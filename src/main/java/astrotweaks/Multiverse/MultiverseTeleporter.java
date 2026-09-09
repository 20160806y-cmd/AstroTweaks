package astrotweaks.Multiverse;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ITeleporter;

/**
 * Places the entity at a fixed position, skipping the nether-portal math of Teleporter.
 *
 * <p>When {@code createExitPortal} is set, the landing spot is also prepared like a
 * vanilla portal arrival: the player is moved to a safe in-air height above the ground
 * and a small obsidian + portal frame is built around them, so stepping forward leaves
 * through a real portal. The reverse trip then goes through the ordinary portal path,
 * which MultiverseEvents re-maps back into the same level.</p>
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
        int y = pos.getY();

        if (createExitPortal && world.provider.getDimension() != MultiverseDims.GLOBAL_DIM) {
            // Find the ground below the requested spot so the portal never ends up buried.
            int groundY = world.getTopSolidOrLiquidBlock(new BlockPos(x, 0, z)).getY();
            int portalBase = Math.max(y - 1, groundY);
            if (portalBase > world.getHeight() - 6) {
                portalBase = world.getHeight() - 6;
            }
            buildReturnPortal(world, new BlockPos(x, portalBase, z));
            // Stand inside the freshly built portal so existing (and just created)
            // portal logic can carry the player back out.
            entity.setPositionAndUpdate(x + 0.5, portalBase + 2.2, z + 0.5);
        } else {
            int safeY = Math.max(y, world.getTopSolidOrLiquidBlock(new BlockPos(x, 0, z)).getY() + 1);
            entity.setPositionAndUpdate(x + 0.5, safeY, z + 0.5);
        }
        entity.motionX = 0.0;
        entity.motionY = 0.0;
        entity.motionZ = 0.0;
        entity.fallDistance = 0.0F;
    }

    /**
     * Builds a 2-wide x 3-tall nether portal frame (obsidian columns/caps + nether portal
     * blocks in the interior) with its floor at {@code portalBase}, then clears a 3x4 area
     * around it so the player does not spawn inside walls.
     */
    private static void buildReturnPortal(World world, BlockPos base) {
        int x = base.getX();
        int y = base.getY();
        int z = base.getZ();

        for (int dx = -1; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                clearColumn(world, new BlockPos(x + dx, 0, z + dz), y + 4);
            }
        }
        for (int dx = -1; dx <= 2; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.setBlockState(new BlockPos(x + dx, y - 1, z + dz), Blocks.OBSIDIAN.getDefaultState());
                world.setBlockState(new BlockPos(x + dx, y + 4, z + dz), Blocks.OBSIDIAN.getDefaultState());
            }
        }
        for (int dh = 0; dh <= 4; dh++) {
            world.setBlockState(new BlockPos(x - 1, y + dh, z), Blocks.OBSIDIAN.getDefaultState());
            world.setBlockState(new BlockPos(x + 2, y + dh, z), Blocks.OBSIDIAN.getDefaultState());
        }
        for (int dx = 0; dx <= 1; dx++) {
            for (int dh = 0; dh <= 3; dh++) {
                world.setBlockState(new BlockPos(x + dx, y + dh, z), Blocks.PORTAL.getDefaultState());
            }
        }
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
