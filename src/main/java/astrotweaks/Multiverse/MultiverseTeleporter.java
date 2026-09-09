package astrotweaks.Multiverse;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ITeleporter;

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
 */
public class MultiverseTeleporter implements ITeleporter {
    private final BlockPos pos;
    private final boolean createExitPortal;

    public MultiverseTeleporter(BlockPos pos) {
        this(pos, false);
    }
    public MultiverseTeleporter(BlockPos pos, boolean createExitPortal) {
        this.pos = pos;
        this.createExitPortal = createExitPortal;
    }

    @Override
    public void placeEntity(World world, Entity entity, float yaw) {
        int x = pos.getX();
        int z = pos.getZ();

        if (createExitPortal && world.provider.getDimension() != MultiverseDims.GLOBAL_DIM) {
            int groundY = findTopSolidBelow(world, x, z, pos.getY());
            if (groundY < 0) {
                groundY = findTopSolidBelow(world, x, z, world.getHeight() - 1);
            }
            int portalBase = clampPortalBase(Math.max(groundY, 0) + 1, world);
            buildReturnPortal(world, new BlockPos(x, portalBase, z));
            entity.setPositionAndUpdate(x + 0.5, portalBase + 1.0, z + 0.5);
        } else {
            int groundY = findTopSolidBelow(world, x, z, pos.getY());
            if (groundY < 0) {
                groundY = findTopSolidBelow(world, x, z, world.getHeight() - 1);
            }
            int safeY = Math.max(pos.getY(), Math.max(groundY, 0) + 1);
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
     * Highest solid (non-liquid, standable) block at or below {@code startY} in the
     * column, or -1 if the whole column below is open. In the nether this returns the
     * FLOOR (bedrock/ground), never the bedrock ceiling at the top of the world.
     */
    private static int findTopSolidBelow(World world, int x, int z, int startY) {
        int top = Math.min(startY, world.getHeight() - 1);
        for (int yy = top; yy >= 0; yy--) {
            IBlockState state = world.getBlockState(new BlockPos(x, yy, z));
            if (state.isTopSolid()) {
                return yy;
            }
        }
        return -1;
    }

    /**
     * Builds a 2-wide x 3-tall nether portal frame (obsidian columns/caps + nether portal
     * blocks in the interior) with its floor at {@code base - 1}, then clears a small 3D
     * footprint around it (from the floor up to the top cap) so the player does not spawn
     * inside walls. Blocks below the floor are never modified.
     */
    private static void buildReturnPortal(World world, BlockPos basePos) {
        int x = basePos.getX();
        int y = basePos.getY();
        int z = basePos.getZ();

        for (int dx = -1; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                clearColumn(world, new BlockPos(x + dx, y - 1, z + dz), y + 4);
            }
        }
        for (int dx = -1; dx <= 2; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.setBlockState(new BlockPos(x + dx, y - 1, z + dz), Blocks.OBSIDIAN.getDefaultState());
                world.setBlockState(new BlockPos(x + dx, y + 3, z + dz), Blocks.OBSIDIAN.getDefaultState());
            }
        }
        for (int dh = 0; dh <= 4; dh++) {
            world.setBlockState(new BlockPos(x - 1, y + dh, z), Blocks.OBSIDIAN.getDefaultState());
            world.setBlockState(new BlockPos(x + 2, y + dh, z), Blocks.OBSIDIAN.getDefaultState());
        }

        world.setBlockState(new BlockPos(x + 1, y + 1, z), Blocks.FIRE.getDefaultState());

    }

    private static void clearColumn(World world, BlockPos from, int upToY) {
        for (int yy = from.getY(); yy <= upToY; yy++) {
            Block block = world.getBlockState(new BlockPos(from.getX(), yy, from.getZ())).getBlock();
            if (block != Blocks.AIR && block != Blocks.PORTAL && block != Blocks.END_PORTAL) {
                world.setBlockState(new BlockPos(from.getX(), yy, from.getZ()), Blocks.AIR.getDefaultState());
            }
        }
    }
    @Override
    public boolean isVanilla() {
        return false;
    }
}