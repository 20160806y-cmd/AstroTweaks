package astrotweaks.block.black_hole;

import net.minecraft.block.BlockBush;
import net.minecraft.block.BlockVine;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.*;

public class BlackHoleRegionManager {

    /** Total block reads allowed per tick for this black hole. */
    public static final int BUDGET_PER_TICK = 1024;
    /** Legacy threshold - now unused, wake checked every tick vs wakeMass. Kept for compat. */
    public static final double MASS_WAKE_THRESHOLD = 0.0;
    /**
     * Safety full-rescan delay after a mass change (5 minutes = 6000 ticks).
     * Catches blocks/liquids missed by the first pass. At most one pending
     * request per black hole; the earliest wins, later changes are ignored
     * until it fires.
     */
    public static final long RESCAN_DELAY_TICKS = 6000L;

    private final BlackHoleTileEntity te;
    private final Map<Long, BlackHoleRegion> allRegions = new HashMap<>();
    private final List<BlackHoleRegion> active = new ArrayList<>();
    private final PriorityQueue<BlackHoleRegion> waiting =
            new PriorityQueue<>(Comparator.comparingDouble(r -> r.wakeMass));

    private double massAtLastWake = 0;
    private boolean seeded = false;

    // --- Delayed safety rescan state (transient: fresh seed scan covers reloads) ---
    private long rescanDueTick = -1L; // -1 = none scheduled
    private double lastSeenMass = Double.NaN;

    public BlackHoleRegionManager(BlackHoleTileEntity te) {
        this.te = te;
        this.massAtLastWake = te.getMass();
    }

    // =================================================================
    // Main tick — called from BlackHoleTileEntity.update() every tick.
    // =================================================================
    public void tick() {
        World world = te.getWorld();
        if (world == null || world.isRemote) return;

        if (!seeded) { seeded = true; seed(); }

        double mass = te.getMass();
        long now = world.getTotalWorldTime();

        // --- Delayed safety rescan: any mass change schedules one full
        // recheck in RESCAN_DELAY_TICKS, but only if none is pending
        // (earliest request wins). If the rescan eats new blocks, the mass
        // change it causes schedules the next one; if mass stays flat
        // (everything edible already eaten), nothing is scheduled and the
        // hole stays idle. O(1) per tick.
        if (Double.isNaN(lastSeenMass)) {
            lastSeenMass = mass; // first tick after load: no spurious schedule
        } else if (Double.compare(mass, lastSeenMass) != 0) {
            lastSeenMass = mass;
            if (rescanDueTick < 0) rescanDueTick = now + RESCAN_DELAY_TICKS;
        }
        if (rescanDueTick >= 0 && now >= rescanDueTick) {
            rescanDueTick = -1L;
            fullRescan();
        }

        // Wake deferred regions as soon as mass reaches their wakeMass.
        // No delta-threshold: guarantees EMPTY=only air/unbreakable and liquids
        // are rechecked immediately when BH grows enough to pull them (accel>0.1).
        // PriorityQueue peek is O(1), so per-tick cost is negligible.
        while (!waiting.isEmpty() && waiting.peek().wakeMass <= mass) {
            BlackHoleRegion r = waiting.poll();
            if (r.state != BlackHoleRegion.STATE_WAITING) continue; // lazy removal via onBlockPlaced
            r.state = BlackHoleRegion.STATE_RECHECKING;
            r.recheckCursor = 0;
            if (!active.contains(r)) active.add(r);
            expandFrontier(r);
        }
        // Frontier expansion for still-waiting regions when mass grew
        // (covers case where new mass makes a neighbour's nearDist <= MAX)
        if (mass > massAtLastWake) {
            massAtLastWake = mass;
            for (BlackHoleRegion r : new ArrayList<>(waiting)) {
                expandFrontier(r);
            }
        }

        if (active.isEmpty()) return;

        double cx = te.getPos().getX() + 0.5;
        double cy = te.getPos().getY() + 0.5;
        double cz = te.getPos().getZ() + 0.5;

        double totalWeight = 0;
        for (BlackHoleRegion r : active) {
            r.weight = 1.0 / Math.max(r.distSq, 1.0);
            totalWeight += r.weight;
        }

        List<BlackHoleRegion> snapshot = new ArrayList<>(active);
        int budget = BUDGET_PER_TICK;
        for (BlackHoleRegion r : snapshot) {
            if (budget <= 0) break;
            if (!active.contains(r)) continue;
            int share = (int) Math.round(BUDGET_PER_TICK * r.weight / totalWeight);
            if (share < 1) share = 1;
            if (share > budget) share = budget;
            budget -= processRegion(r, share, cx, cy, cz);
        }
    }

    // =================================================================
    // Region processing
    // =================================================================
    private int processRegion(BlackHoleRegion r, int budget, double cx, double cy, double cz) {
        switch (r.state) {
            case BlackHoleRegion.STATE_SCANNING:
                return processScan(r, budget, cx, cy, cz);
            case BlackHoleRegion.STATE_RECHECKING:
                return processRecheck(r, budget, cx, cy, cz);
            default:
                return 0;
        }
    }

    private int processScan(BlackHoleRegion r, int budget, double cx, double cy, double cz) {
        World world = te.getWorld();
        if (r.sortedOrder == null) r.buildSortedOrder(cx, cy, cz);
        int used = 0;
        double massDelta = 0.0D;
        // Local mass cache: avoids te.getMass() + Math.pow(horizon) per block.
        // Updated on every eat, flushed once at the end (single markDirty/sync).
        double curMass = te.getMass();
        double horizonPlus = BlackHoleUtils.getHorizonRadius(curMass) + 0.5D;

        while (used < budget && r.scanCursor < BlackHoleRegion.VOLUME) {
            int idx = r.sortedOrder[r.scanCursor++];
            used++;
            BlockPos bp = r.blockPos(idx);
            if (!world.isBlockLoaded(bp)) continue;

            IBlockState st = world.getBlockState(bp);
            if (st.getBlock() == Blocks.AIR || st.getBlock() instanceof BlackHoleBlock) continue;

            Material mat = st.getMaterial();
            // --- Liquids & vegetation: eat if accel > 0.1 or inside horizon, else defer (never EMPTY) ---
            if (mat.isLiquid() || isVegetation(st, mat)) {
                double bdist = dist(bp, cx, cy, cz);
                boolean insideHorizon = bdist <= horizonPlus;
                double accel = BlackHoleUtils.getAcceleration(curMass, bdist);
                if (insideHorizon || accel > 0.1) {
                    eat(world, bp, true);
                    massDelta += BlackHoleUtils.MASS_PER_LIQUID;
                    curMass += BlackHoleUtils.MASS_PER_LIQUID;
                    horizonPlus = BlackHoleUtils.getHorizonRadius(curMass) + 0.5D;
                } else {
                    r.addDeferred(idx); // keep region WAITING, not EMPTY
                }
                continue;
            }

            float hardness;
            try { hardness = st.getBlockHardness(world, bp); }
            catch (Exception e) { continue; }
            if (hardness < 0) { r.addDeferred(idx); continue; }

            // Fake hardness for the check, real hardness for the mass gain.
            double check = BlackHoleUtils.effectiveHardnessForCheck(mat, hardness);
            double bdist = dist(bp, cx, cy, cz);

            if (canEat(bdist, check, curMass, horizonPlus)) {
                eat(world, bp, false);
                double gain = BlackHoleUtils.massGainForHardness(hardness);
                massDelta += gain;
                curMass += gain;
                horizonPlus = BlackHoleUtils.getHorizonRadius(curMass) + 0.5D;
            } else {
                r.addDeferred(idx);
            }
        }

        if (r.scanCursor >= BlackHoleRegion.VOLUME) finishScan(r);
        if (massDelta != 0.0D) te.addMass(massDelta);
        return used;
    }

    /**
     * Safety full recheck of all settled regions at current mass.
     * EMPTY/WAITING regions go back to SCANNING so missed blocks and liquids
     * are picked up in distance order within the normal per-tick budget.
     * Regions already in flight (SCANNING/RECHECKING) are left alone.
     * Stale `waiting` queue entries are skipped lazily in tick().
     */
    private void fullRescan() {
        for (BlackHoleRegion r : allRegions.values()) {
            if (r.state == BlackHoleRegion.STATE_EMPTY
                    || r.state == BlackHoleRegion.STATE_WAITING) {
                r.resetToScanning();
                if (!active.contains(r)) active.add(r);
            }
        }
    }

    private void finishScan(BlackHoleRegion r) {
        r.freeSortedOrder();
        // Always expand the frontier — hole keeps eating farther regions even
        // if it had to defer some blocks here.
        expandFrontier(r);
        if (r.deferredCount == 0) {
            r.state = BlackHoleRegion.STATE_EMPTY;
            active.remove(r);
        } else {
            r.state = BlackHoleRegion.STATE_WAITING;
            r.wakeMass = computeWakeMass(r);
            active.remove(r);
            waiting.add(r);
        }
    }

    private int processRecheck(BlackHoleRegion r, int budget, double cx, double cy, double cz) {
        World world = te.getWorld();
        int used = 0;
        double massDelta = 0.0D;
        double curMass = te.getMass();
        double horizonPlus = BlackHoleUtils.getHorizonRadius(curMass) + 0.5D;

        while (r.recheckCursor < r.deferredCount && used < budget) {
            short idx = r.deferred[r.recheckCursor];
            used++;
            if (idx < 0) { r.recheckCursor++; continue; }

            BlockPos bp = r.blockPos(idx);
            if (!world.isBlockLoaded(bp)) { r.recheckCursor++; continue; }

            IBlockState st = world.getBlockState(bp);
            if (st.getBlock() == Blocks.AIR) {
                r.markDeferredRemoved(r.recheckCursor++);
                continue;
            }

            Material mat = st.getMaterial();
            // --- Liquids & vegetation: eat if accel > 0.1 or inside horizon, else keep deferred ---
            if (mat.isLiquid() || isVegetation(st, mat)) {
                double bdist = dist(bp, cx, cy, cz);
                boolean insideHorizon = bdist <= horizonPlus;
                double accel = BlackHoleUtils.getAcceleration(curMass, bdist);
                if (insideHorizon || accel > 0.1) {
                    eat(world, bp, true);
                    massDelta += BlackHoleUtils.MASS_PER_LIQUID;
                    curMass += BlackHoleUtils.MASS_PER_LIQUID;
                    horizonPlus = BlackHoleUtils.getHorizonRadius(curMass) + 0.5D;
                    r.markDeferredRemoved(r.recheckCursor);
                }
                // else: keep in deferred -> remains WAITING until mass grows
                r.recheckCursor++;
                continue;
            }

            float hardness;
            try { hardness = st.getBlockHardness(world, bp); }
            catch (Exception e) { r.recheckCursor++; continue; }
            if (hardness < 0) { r.recheckCursor++; continue; }

            // Fake hardness for the check, real hardness for the mass gain.
            double check = BlackHoleUtils.effectiveHardnessForCheck(mat, hardness);
            double bdist = dist(bp, cx, cy, cz);

            if (canEat(bdist, check, curMass, horizonPlus)) {
                eat(world, bp, false);
                double gain = BlackHoleUtils.massGainForHardness(hardness);
                massDelta += gain;
                curMass += gain;
                horizonPlus = BlackHoleUtils.getHorizonRadius(curMass) + 0.5D;
                r.markDeferredRemoved(r.recheckCursor);
            }
            r.recheckCursor++;
        }

        if (r.recheckCursor >= r.deferredCount) {
            r.compactDeferred();
            if (r.deferredCount == 0) {
                r.state = BlackHoleRegion.STATE_EMPTY;
                active.remove(r);
            } else {
                r.state = BlackHoleRegion.STATE_WAITING;
                r.wakeMass = computeWakeMass(r);
                active.remove(r);
                waiting.add(r);
            }
        }
        if (massDelta != 0.0D) te.addMass(massDelta);
        return used;
    }

    // =================================================================
    // Frontier expansion
    // =================================================================
    private void expandFrontier(BlackHoleRegion r) {
        int s = BlackHoleRegion.SIZE;
        int[][] dirs = {{s,0,0},{-s,0,0},{0,s,0},{0,-s,0},{0,0,s},{0,0,-s}};
        for (int[] d : dirs) {
            BlackHoleRegion nr = getOrCreateRegion(r.originX + d[0], r.originY + d[1], r.originZ + d[2]);
            if (nr != null
                    && nr.state == BlackHoleRegion.STATE_SCANNING
                    && !active.contains(nr)) {
                active.add(nr);
            }
        }
    }

    private BlackHoleRegion getOrCreateRegion(int ox, int oy, int oz) {
        int s = BlackHoleRegion.SIZE;
        if (oy < 0 || oy >= 256) return null;

        long key = packKey(ox, oy, oz);
        BlackHoleRegion r = allRegions.get(key);
        if (r != null) return r;

        double cx = te.getPos().getX() + 0.5;
        double cy = te.getPos().getY() + 0.5;
        double cz = te.getPos().getZ() + 0.5;

        // Smoothing: use closest point of region cube instead of center.
        // This removes 8x8 cube steps at the MAX boundary.
        // Region creation is gated only by MAX_BLOCK_CAPTURE_RANGE (hard cap),
        // not by current reachSoft - per-block canEat inside the region gives
        // the true spherical front (radial sortedOrder) without quantization.
        double nearX = Math.max(ox, Math.min(cx, ox + s));
        double nearY = Math.max(oy, Math.min(cy, oy + s));
        double nearZ = Math.max(oz, Math.min(cz, oz + s));
        double ndx = nearX - cx, ndy = nearY - cy, ndz = nearZ - cz;
        double nearDist = Math.sqrt(ndx * ndx + ndy * ndy + ndz * ndz);

        if (nearDist > BlackHoleUtils.MAX_BLOCK_CAPTURE_RANGE) return null;

        r = new BlackHoleRegion(ox, oy, oz, cx, cy, cz);
        allRegions.put(key, r);
        return r;
    }

    /** Seed the region containing the black hole itself. Called once on first tick. */
    public void seed() {
        int s = BlackHoleRegion.SIZE;
        int ox = Math.floorDiv(te.getPos().getX(), s) * s;
        int oy = Math.floorDiv(te.getPos().getY(), s) * s;
        int oz = Math.floorDiv(te.getPos().getZ(), s) * s;
        BlackHoleRegion r = getOrCreateRegion(ox, oy, oz);
        if (r != null && !active.contains(r)) active.add(r);
    }

    // =================================================================
    // Event hooks
    // =================================================================
    /** Called when a block is placed / liquid appears in the world. */
    public void onBlockPlaced(BlockPos bp) {
        long key = packKeyForBlock(bp);
        BlackHoleRegion r = allRegions.get(key);
        if (r == null) {
            // Region not yet known (frontier hasn't reached it). Try to create it
            // if it's within MAX range - otherwise frontier will create it later.
            int s = BlackHoleRegion.SIZE;
            int ox = Math.floorDiv(bp.getX(), s) * s;
            int oy = Math.floorDiv(bp.getY(), s) * s;
            int oz = Math.floorDiv(bp.getZ(), s) * s;
            r = getOrCreateRegion(ox, oy, oz);
            if (r == null) return;
            if (!active.contains(r)) active.add(r);
            // New region will scan and pick the block up in sorted order
            return;
        }

        if (r.state == BlackHoleRegion.STATE_EMPTY) {
            r.resetToScanning();
            if (!active.contains(r)) active.add(r);
        } else if (r.state == BlackHoleRegion.STATE_WAITING) {
            int lx = bp.getX() - r.originX;
            int ly = bp.getY() - r.originY;
            int lz = bp.getZ() - r.originZ;
            if (lx < 0 || lx >= BlackHoleRegion.SIZE || ly < 0 || ly >= BlackHoleRegion.SIZE || lz < 0 || lz >= BlackHoleRegion.SIZE) return;
            int idx = (lz << 6) | (ly << 3) | lx;
            // Avoid duplicate deferral, but ensure recheck
            if (r.isDeferred != null && idx >= 0 && idx < r.isDeferred.length && r.isDeferred[idx]) {
                // already deferred
            } else {
                r.addDeferred(idx);
            }
            // Recompute wakeMass for the new block - old wakeMass may be huge
            // (hard block) and the new dirt should wake earlier.
            double bdist = dist(bp, te.getPos().getX() + 0.5, te.getPos().getY() + 0.5, te.getPos().getZ() + 0.5);
            World world = te.getWorld();
            if (world != null && world.isBlockLoaded(bp)) {
                try {
                    IBlockState st = world.getBlockState(bp);
                    Material mat = st.getMaterial();
                    boolean veg = isVegetation(st, mat);
                    if (!mat.isLiquid() && !veg) {
                        float h = st.getBlockHardness(world, bp);
                        if (h >= 0) {
                            // Same fake hardness as the eat check (ore 3.0 -> 1.5 etc.)
                            double eff = BlackHoleUtils.effectiveHardnessForCheck(mat, h);
                            double mAccel = eff * bdist * bdist / BlackHoleUtils.G;
                            double mHorizon = BlackHoleUtils.massForHorizon(bdist - 0.5);
                            double need = Math.min(mAccel, mHorizon);
                            if (need < r.wakeMass) r.wakeMass = need;
                        }
                    } else {
                        // Liquids/vegetation never stay long in deferred, wake with 0.1
                        double eff = 0.1;
                        double mAccel = eff * bdist * bdist / BlackHoleUtils.G;
                        double mHorizon = BlackHoleUtils.massForHorizon(bdist - 0.5);
                        double need = Math.min(mAccel, mHorizon);
                        if (need < r.wakeMass) r.wakeMass = need;
                    }
                } catch (Exception ignored) {}
            }
            r.state = BlackHoleRegion.STATE_RECHECKING;
            r.recheckCursor = 0;
            if (!active.contains(r)) active.add(r);
            // Not removed from `waiting` — lazy removal in tick()
        } else {
            // SCANNING или RECHECKING: sortedOrder мог уже пройти эту позицию,
            // либо его вовсе нет. Явно кладём блок в deferred.
            int lx = bp.getX() - r.originX;
            int ly = bp.getY() - r.originY;
            int lz = bp.getZ() - r.originZ;
            if (lx < 0 || lx >= BlackHoleRegion.SIZE || ly < 0 || ly >= BlackHoleRegion.SIZE || lz < 0 || lz >= BlackHoleRegion.SIZE) return;
            int idx = (lz << 6) | (ly << 3) | lx;
            r.addDeferred(idx);
            // Для SCANNING: finishScan посчитает wakeMass уже с учётом этого блока.
            // Для RECHECKING: запись встанет в конец deferred, и recheckCursor её дойдёт
            // (recheckCursor <= deferredCount всегда, потому что мы только что увеличили count).
        }
    }

    /** Called when a chunk finishes loading. Resets any regions in that chunk. */
    public void onChunkLoaded(Chunk chunk) {
        // Chunk covers 16x16 -> 2x2 region columns, y range 0..255 -> 32 rows.
        for (int dx = 0; dx < 16; dx += BlackHoleRegion.SIZE) {
            for (int dz = 0; dz < 16; dz += BlackHoleRegion.SIZE) {
                int ox = (chunk.x << 4) + dx;
                int oz = (chunk.z << 4) + dz;
                for (int oy = 0; oy < 256; oy += BlackHoleRegion.SIZE) {
                    BlackHoleRegion r = allRegions.get(packKey(ox, oy, oz));
                    if (r == null) continue;
                    // While chunk was unloaded, BH mass and world blocks may have
                    // changed arbitrarily. Need to handle both EMPTY and WAITING:
                    // - EMPTY: full rescan (new blocks may have appeared)
                    // - WAITING: deferred list is stale (new blocks missed, old
                    //   blocks may be air/hard). Move to RECHECKING and recompute
                    //   wakeMass instead of full rescan to keep budget low.
                    if (r.state == BlackHoleRegion.STATE_EMPTY) {
                        r.resetToScanning();
                        if (!active.contains(r)) active.add(r);
                    } else if (r.state == BlackHoleRegion.STATE_WAITING) {
                        // Stale deferred - force recheck; if chunk was modified
                        // heavily, compact will drop air entries and recompute
                        // wakeMass will be updated after the recheck pass.
                        r.state = BlackHoleRegion.STATE_RECHECKING;
                        r.recheckCursor = 0;
                        r.wakeMass = computeWakeMass(r);
                        if (!active.contains(r)) active.add(r);
                        // lazy removal from waiting
                    }
                }
            }
        }
    }

    // =================================================================
    // Helpers
    // =================================================================
    private boolean canEat(double bdist, double checkHardness, double curMass, double horizonPlus) {
        if (bdist <= horizonPlus) return true;
        return BlackHoleUtils.getAcceleration(curMass, bdist) >= checkHardness;
    }

    private void eat(World world, BlockPos bp, boolean liquid) {
        if (liquid) world.setBlockState(bp, Blocks.AIR.getDefaultState(), 2);
        else world.setBlockToAir(bp);
    }

    private double computeWakeMass(BlackHoleRegion r) {
        World world = te.getWorld();
        double cx = te.getPos().getX() + 0.5;
        double cy = te.getPos().getY() + 0.5;
        double cz = te.getPos().getZ() + 0.5;
        double minMass = Double.MAX_VALUE;
        for (int i = 0; i < r.deferredCount; i++) {
            short idx = r.deferred[i];
            if (idx < 0) continue;
            BlockPos bp = r.blockPos(idx);
            IBlockState st = world.getBlockState(bp);
            if (st.getBlock() == Blocks.AIR) continue;
            float hardness;
            try { hardness = st.getBlockHardness(world, bp); } catch (Exception e) { continue; }
            if (hardness < 0) continue;
            Material mat = st.getMaterial();
            boolean liquid = mat.isLiquid() || isVegetation(st, mat);
            // Wake threshold must use the same fake hardness as the eat check,
            // otherwise e.g. an ore (3.0 -> 1.5) would wake the region too late.
            double effective = liquid ? 0.1 : BlackHoleUtils.effectiveHardnessForCheck(mat, hardness);
            double bdist = dist(bp, cx, cy, cz);
            // Either accel becomes enough, or the horizon grows to swallow it.
            double mAccel = effective * bdist * bdist / BlackHoleUtils.G;
            double mHorizon = BlackHoleUtils.massForHorizon(bdist - 0.5);
            double m = Math.min(mAccel, mHorizon);
            if (m < minMass) minMass = m;
        }
        return minMass;
    }

    /** Vegetation that should be absorbed like liquids (hardness ~0.1): tallgrass, flowers, bushes, vine etc. */
    public static boolean isVegetation(IBlockState st) {
        if (st == null) return false;
        return isVegetation(st, st.getMaterial());
    }

    /** Same as above, but reuses an already-fetched Material (hot-path overload). */
    public static boolean isVegetation(IBlockState st, Material m) {
        if (st == null || m == null) return false;
        if (m == Material.PLANTS || m == Material.VINE) return true;
        net.minecraft.block.Block b = st.getBlock();
        if (b instanceof BlockBush) return true;
        if (b instanceof BlockVine) return true;
        return false;
    }

    private static double dist(BlockPos bp, double cx, double cy, double cz) {
        double dx = bp.getX() + 0.5 - cx;
        double dy = bp.getY() + 0.5 - cy;
        double dz = bp.getZ() + 0.5 - cz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static long packKey(int ox, int oy, int oz) {
        int rx = Math.floorDiv(ox, BlackHoleRegion.SIZE);
        int ry = Math.floorDiv(oy, BlackHoleRegion.SIZE);
        int rz = Math.floorDiv(oz, BlackHoleRegion.SIZE);
        // 24 | 6 | 24 = 54 bits — covers full world range with margin.
        return ((long)(rx & 0xFFFFFF) << 30)
              | ((long)(ry & 0x3F) << 24)
              | (long)(rz & 0xFFFFFF);
    }

    private static long packKeyForBlock(BlockPos bp) {
        int s = BlackHoleRegion.SIZE;
        int ox = Math.floorDiv(bp.getX(), s) * s;
        int oy = Math.floorDiv(bp.getY(), s) * s;
        int oz = Math.floorDiv(bp.getZ(), s) * s;
        return packKey(ox, oy, oz);
    }
}
