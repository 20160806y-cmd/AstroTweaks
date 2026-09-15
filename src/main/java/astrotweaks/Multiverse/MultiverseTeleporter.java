package astrotweaks.Multiverse;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ITeleporter;

import java.util.HashSet;

/**
 * Places the entity at a fixed position, skipping the nether-portal math of Teleporter.
 *
 * <p>When {@code createExitPortal} is set, the landing spot is also prepared like a
 * vanilla portal arrival: the player is moved into a freshly built obsidian + portal
 * frame so stepping forward leaves through a real portal. The reverse trip then goes
 * through the ordinary portal path, which MultiverseEvents re-maps back into the same
 * level.</p>
 *
 * <p>Portal placement finds the ACTUAL surface under the target column instead of
 * {@code World.getTopSolidOrLiquidBlock}: that method starts from the top filled segment
 * and in the nether resolves to the bedrock CEILING (~Y=128), which previously made
 * portals appear at the top of the world and wipe the entire column below them. Here the
 * portal is built with its floor directly ON the ground and only its own footprint
 * (floor .. top cap) is cleared - nothing below ever gets touched.</p>
 *
 * <p>The block scan and portal build reuse one {@link BlockPos.MutableBlockPos}, so a
 * teleport allocates almost no per-block positions.</p>
 */
public class MultiverseTeleporter implements ITeleporter {
    private final BlockPos pos;
    private final boolean createExitPortal;

    private static final IBlockState OBSIDIAN = Blocks.OBSIDIAN.getDefaultState();
    private static final int CHUNK = 16 * 2;

    private boolean precomputedFeet;



    public MultiverseTeleporter(BlockPos pos) {
        this(pos, false, false);
    }
    public MultiverseTeleporter(BlockPos pos, boolean createExitPortal) {
        this(pos, createExitPortal, false); 
    }
    public MultiverseTeleporter(BlockPos pos, boolean createExitPortal, boolean precomputedFeet) {
        this.pos = pos;
        this.createExitPortal = createExitPortal;
        this.precomputedFeet = precomputedFeet;
    }

    @Override
    public void placeEntity(World world, Entity entity, float yaw) {
        int x = pos.getX();
        int z = pos.getZ();
        int scanStart = pos.getY();
        if (world.provider.getDimensionType() == net.minecraft.world.DimensionType.NETHER) {
            scanStart = 100;
        }
        int safeY = precomputedFeet ? pos.getY() : findSafeSpawnY(world, x, z, scanStart);
        if (safeY < 0) {
            safeY = Math.max(pos.getY(), 4);
        }
        if (createExitPortal && world.provider.getDimension() != MultiverseDims.GLOBAL_DIM) {
            int portalBase = clampPortalBase(safeY, world);
            buildReturnPortal(world, x, portalBase, z);
            entity.setPositionAndUpdate(x + 0.5, portalBase + 1.0, z + 0.5);
        } else {
            entity.setPositionAndUpdate(x + 0.5, safeY, z + 0.5);
        }
        entity.motionX = 0.0;
        entity.motionY = 0.0;
        entity.motionZ = 0.0;
        entity.fallDistance = 0.0F;
    }

    private static int clampPortalBase(int y, World world) {
        int maxBase = world.getHeight() - 6;
        if (y > maxBase) return maxBase;
        return y < 1 ? 1 : y;
    }

    /**
     * Chunk-grid search for a safe spawn position.
     * Checks the initial column, then expands outward in 16-block (1 chunk) steps,
     * tracking visited chunks to avoid re-checks. A column is valid when:
     * solid non-liquid block at Y, and Y+1 / Y+2 are non-liquid and passable.
     * Returns the feet Y, or -1 if nothing found within 32 rings (~512 blocks).
     */
    public static int findSafeSpawnY(World world, int x, int z, int startY) {
        int result = findSafeYInColumn(world, x, z, startY);
        if (result >= 0)  return result;

        //HashSet<Long> checked = new HashSet<>();
        //checked.add(chunkKey(x, z));

        for (int ring = 1; ring <= 32; ring++) {
            int range = ring * CHUNK;
            for (int dx = -range; dx <= range; dx += CHUNK) {
                for (int dz = -range; dz <= range; dz += CHUNK) {
                    if (Math.abs(dx) < range && Math.abs(dz) < range) continue;
                    result = findSafeYInColumn(world, x + dx, z + dz, startY);
                    if (result >= 0) return result;
                }
            }
        }
        return -1;
    }

    //private static long chunkKey(int x, int z) {
    //    return ((long) (x >> 4) << 32) | ((z >> 4) & 0xFFFFFFFFL);
    //}

    /**
     * Scans a single column from {@code startY} downward. A position Y is safe when:
     * block at Y is solid non-liquid, blocks at Y+1 and Y+2 are passable non-liquid.
     * Returns feet Y (Y+1) or -1.
     */
    private static int findSafeYInColumn(World world, int x, int z, int startY) {
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        int top = Math.min(startY, world.getHeight() - 3);
        for (int yy = top; yy >= 1; yy--) {
            IBlockState below = world.getBlockState(mpos.setPos(x, yy, z));
            boolean belowSolid  = below.getCollisionBoundingBox(world, mpos) != null;
            boolean belowLiquid = below.getMaterial().isLiquid();
            // ВАЖНО: вода теперь валидная опора — не пропускаем её.
            if (!belowSolid && !belowLiquid) continue;

            IBlockState feet = world.getBlockState(mpos.setPos(x, yy + 1, z));
            if (feet.getMaterial().isLiquid()) continue;
            if (feet.getCollisionBoundingBox(world, mpos) != null) continue;

            IBlockState head = world.getBlockState(mpos.setPos(x, yy + 2, z));
            if (head.getMaterial().isLiquid()) continue;
            if (head.getCollisionBoundingBox(world, mpos) != null) continue;

            return yy + 1;
        }
        return -1;
    }

    /**
     * Builds a 2-wide x 3-tall nether portal frame (obsidian columns/caps + nether portal
     * blocks in the interior) with its floor at {@code base - 1}, then clears a small 3D
     * footprint around it (from the floor up to the top cap) so the player does not spawn
     * inside walls. Blocks below the floor are never modified.
     */
    private static void buildReturnPortal(World world, int x, int y, int z) {
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                clearColumn(world, mpos, x + dx, y - 1, z + dz, y + 4);
            }
        }
        for (int dx = -1; dx <= 2; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                mpos.setPos(x + dx, y - 1, z + dz);
                world.setBlockState(mpos, OBSIDIAN);
                mpos.setPos(x + dx, y + 3, z + dz);
                world.setBlockState(mpos, OBSIDIAN);
            }
        }
        for (int dh = 0; dh <= 4; dh++) {
            mpos.setPos(x - 1, y + dh, z);
            world.setBlockState(mpos, OBSIDIAN);
            mpos.setPos(x + 2, y + dh, z);
            world.setBlockState(mpos, OBSIDIAN);
        }
        mpos.setPos(x + 1, y + 1, z);
        world.setBlockState(mpos, Blocks.FIRE.getDefaultState());
    }

    private static void clearColumn(World world, BlockPos.MutableBlockPos mpos, int x, int fromY, int z, int upToY) {
        for (int yy = fromY; yy <= upToY; yy++) {
            mpos.setPos(x, yy, z);
            Block block = world.getBlockState(mpos).getBlock();
            if (block != Blocks.AIR && block != Blocks.PORTAL && block != Blocks.END_PORTAL) {
                world.setBlockState(mpos, Blocks.AIR.getDefaultState());
            }
        }
    }
    @Override public boolean isVanilla() { return false; }
}
