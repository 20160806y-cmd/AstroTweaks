package astrotweaks.block.black_hole;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.*;

public class BlackHoleRegionManager {

    /** Total block reads allowed per tick for this black hole. */
    public static final int BUDGET_PER_TICK = 1500;
    /** Only process wake-ups once mass grew by this much since last check. */
    public static final double MASS_WAKE_THRESHOLD = 100.0;

    private final BlackHoleTileEntity te;
    private final Map<Long, BlackHoleRegion> allRegions = new HashMap<>();
    private final List<BlackHoleRegion> active = new ArrayList<>();
    private final PriorityQueue<BlackHoleRegion> waiting =
            new PriorityQueue<>(Comparator.comparingDouble(r -> r.wakeMass));

    private double massAtLastWake = 0;
    private boolean seeded = false;

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
        if (mass - massAtLastWake > MASS_WAKE_THRESHOLD) {
            massAtLastWake = mass;
            while (!waiting.isEmpty() && waiting.peek().wakeMass <= mass) {
                BlackHoleRegion r = waiting.poll();
                if (r.state != BlackHoleRegion.STATE_WAITING) continue; // already woken via event (lazy removal)
                r.state = BlackHoleRegion.STATE_RECHECKING;
                r.recheckCursor = 0;
                if (!active.contains(r)) active.add(r);
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
        boolean massChanged = false;

        while (used < budget && r.scanCursor < BlackHoleRegion.VOLUME) {
            int idx = r.sortedOrder[r.scanCursor++];
            used++;
            BlockPos bp = r.blockPos(idx);
            if (!world.isBlockLoaded(bp)) continue;

            IBlockState st = world.getBlockState(bp);
            if (st.getBlock() == Blocks.AIR || st.getBlock() instanceof BlackHoleBlock) continue;

            // --- Liquids: always eat. They have no structural resistance. ---
            if (st.getMaterial().isLiquid()) {
                eat(world, bp, true);
                te.addMass(BlackHoleUtils.MASS_PER_LIQUID);
                massChanged = true;
                continue;
            }

            float hardness;
            try { hardness = st.getBlock().getBlockHardness(st, world, bp); }
            catch (Exception e) { continue; }
            if (hardness < 0) { r.addDeferred(idx); continue; }

            double effective = (hardness == 0) ? 0.1 : hardness;
            double bdist = dist(bp, cx, cy, cz);

            if (canEat(bdist, effective)) {
                eat(world, bp, false);
                te.addMass(effective);
                massChanged = true;
            } else {
                r.addDeferred(idx);
            }
        }

        if (r.scanCursor >= BlackHoleRegion.VOLUME) finishScan(r);
        if (massChanged) te.markDirty();
        return used;
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
        boolean massChanged = false;

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

            // --- Liquids: always eat, and drop from the deferred list. ---
            if (st.getMaterial().isLiquid()) {
                eat(world, bp, true);
                te.addMass(BlackHoleUtils.MASS_PER_LIQUID);
                massChanged = true;
                r.markDeferredRemoved(r.recheckCursor);
                r.recheckCursor++;
                continue;
            }

            float hardness;
            try { hardness = st.getBlock().getBlockHardness(st, world, bp); }
            catch (Exception e) { r.recheckCursor++; continue; }
            if (hardness < 0) { r.recheckCursor++; continue; }

            double effective = (hardness == 0) ? 0.1 : hardness;
            double bdist = dist(bp, cx, cy, cz);

            if (canEat(bdist, effective)) {
                eat(world, bp, false);
                te.addMass(effective);
                massChanged = true;
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
        if (massChanged) te.markDirty();
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
        if (oy < 0 || oy >= 256) return null;
        long key = packKey(ox, oy, oz);
        BlackHoleRegion r = allRegions.get(key);
        if (r != null) return r;

        double cx = te.getPos().getX() + 0.5;
        double cy = te.getPos().getY() + 0.5;
        double cz = te.getPos().getZ() + 0.5;
        double rx = ox + 4, ry = oy + 4, rz = oz + 4;
        double dx = rx - cx, dy = ry - cy, dz = rz - cz;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d > BlackHoleUtils.MAX_BLOCK_CAPTURE_RANGE + BlackHoleRegion.SIZE) return null;

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
        BlackHoleRegion r = allRegions.get(packKeyForBlock(bp));
        if (r == null) return; // will be picked up when the frontier reaches it

        if (r.state == BlackHoleRegion.STATE_EMPTY) {
            r.resetToScanning();
            if (!active.contains(r)) active.add(r);
        } else if (r.state == BlackHoleRegion.STATE_WAITING) {
            int lx = bp.getX() - r.originX;
            int ly = bp.getY() - r.originY;
            int lz = bp.getZ() - r.originZ;
            int idx = (lz << 6) | (ly << 3) | lx;
            r.addDeferred(idx);
            r.state = BlackHoleRegion.STATE_RECHECKING;
            r.recheckCursor = 0;
            if (!active.contains(r)) active.add(r);
            // Not removed from `waiting` — the polling loop in tick() will skip it (lazy removal).
        }
        // SCANNING / RECHECKING: it'll be picked up on its own.
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
                    // Blocks in this chunk may have changed while unloaded. Rescan.
                    if (r.state == BlackHoleRegion.STATE_EMPTY) {
                        r.resetToScanning();
                        if (!active.contains(r)) active.add(r);
                    }
                }
            }
        }
    }

    // =================================================================
    // Helpers
    // =================================================================
    private boolean canEat(double bdist, double effective) {
        double mass = te.getMass();
        if (bdist <= BlackHoleUtils.getHorizonRadius(mass) + 0.5) return true;
        return BlackHoleUtils.getAcceleration(mass, bdist) >= effective;
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
            boolean liquid = st.getMaterial().isLiquid();
            double effective = liquid ? 0.5 : (hardness == 0 ? 0.1 : hardness);
            double bdist = dist(bp, cx, cy, cz);
            // Either accel becomes enough, or the horizon grows to swallow it.
            double mAccel = effective * bdist * bdist / BlackHoleUtils.G;
            double mHorizon = BlackHoleUtils.massForHorizon(bdist - 0.5);
            double m = Math.min(mAccel, mHorizon);
            if (m < minMass) minMass = m;
        }
        return minMass;
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
