package astrotweaks.Multiverse;

import net.minecraft.block.Block;
import net.minecraft.block.BlockPortal;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.block.material.Material;

import javax.annotation.Nullable;

/**
 * Nether-portal geometry helpers for the custom {@link BlockNetherPortal}.
 *
 * <p>{@link #findFrame(World, BlockPos)} is a faithful port of vanilla
 * {@code BlockPortal.Size}: given any position inside/under a completed obsidian
 * frame it returns the portal rectangle (axis, interior origin, width, height).
 * It is used both for the "light with flint &amp; steel" replacement hook and for
 * validating that a portal is still whole (frame breaking cascades the removal).
 *
 * <p>{@link #placeExitPortal(World, int, int, EnumFacing.Axis)} builds a fresh
 * 2x3 (4x5x1) obsidian + portal frame at the caller-provided column without ever
 * touching blocks outside the ring/interior footprint.
 */
public final class NetherPortalGeometry {

    private NetherPortalGeometry() {}

    /** Portal rectangle: interior origin (bottom-left interior cell). */
    public static final class Geometry {
        public final BlockPos interiorMin;
        public final int width;
        public final int height;
        public final EnumFacing.Axis axis;

        public Geometry(BlockPos interiorMin, int width, int height, EnumFacing.Axis axis) {
            this.interiorMin = interiorMin;
            this.width = width;
            this.height = height;
            this.axis = axis;
        }

        public EnumFacing rightDir() {
            return axis == EnumFacing.Axis.X ? EnumFacing.WEST : EnumFacing.SOUTH;
        }

        public EnumFacing leftDir() {
            return axis == EnumFacing.Axis.X ? EnumFacing.EAST : EnumFacing.NORTH;
        }

        /** Where an incoming entity stands when arriving through this portal. */
        public BlockPos standPos() {
            return new BlockPos(interiorMin.getX() + 0.5D, interiorMin.getY(), interiorMin.getZ() + 0.5D);
        }
    }

    public static boolean isPortalBlock(IBlockState state) {
        return state.getBlock() == BlockNetherPortal.BLOCK;
    }

    private static boolean isEmptyBlock(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        Material material = state.getMaterial();
        if (material == Material.AIR) {
            return true;
        }
        Block block = state.getBlock();
        return block == Blocks.FIRE || block == Blocks.PORTAL || block == BlockNetherPortal.BLOCK;
    }

    /** Tries axis X then axis Z, like vanilla {@code BlockPortal.trySpawnPortal}. */
    @Nullable
    public static Geometry findFrame(World world, BlockPos pos) {
        Geometry geometry = compute(world, pos, EnumFacing.Axis.X);
        if (geometry != null) {
            return geometry;
        }
        return compute(world, pos, EnumFacing.Axis.Z);
    }

    /** Like {@link #findFrame} but additionally requires every interior cell to be a portal block. */
    @Nullable
    public static Geometry findInterior(World world, BlockPos pos) {
        Geometry geometry = findFrame(world, pos);
        if (geometry == null) {
            return null;
        }
        int count = 0;
        for (int i = 0; i < geometry.width; i++) {
            for (int j = 0; j < geometry.height; j++) {
                BlockPos cell = geometry.interiorMin.offset(geometry.rightDir(), i).up(j);
                if (world.getBlockState(cell).getBlock() == BlockNetherPortal.BLOCK) {
                    count++;
                }
            }
        }
        return count == geometry.width * geometry.height ? geometry : null;
    }

    private static Geometry compute(World world, BlockPos p, EnumFacing.Axis axis) {
        EnumFacing leftDir = axis == EnumFacing.Axis.X ? EnumFacing.EAST : EnumFacing.NORTH;
        EnumFacing rightDir = axis == EnumFacing.Axis.X ? EnumFacing.WEST : EnumFacing.SOUTH;

        BlockPos pos = p;
        for (BlockPos stop = pos; pos.getY() > stop.getY() - 21 && pos.getY() > 0 && isEmptyBlock(world, pos.down()); pos = pos.down()) {
            ; // walk down onto the obsidian base
        }

        int i = distanceToEdge(world, pos, leftDir) - 1;
        BlockPos bottomLeft = null;
        int width = 0;
        if (i >= 0) {
            bottomLeft = pos.offset(leftDir, i);
            width = distanceToEdge(world, bottomLeft, rightDir);
            if (width < 2 || width > 21) {
                bottomLeft = null;
                width = 0;
            }
        }
        if (bottomLeft != null) {
            int height = calculatePortalHeight(world, bottomLeft, rightDir, leftDir, width);
            if (height >= 3 && height <= 21) {
                return new Geometry(bottomLeft, width, height, axis);
            }
        }
        return null;
    }

    private static int distanceToEdge(World world, BlockPos p, EnumFacing dir) {
        int i;
        for (i = 0; i < 22; ++i) {
            BlockPos offset = p.offset(dir, i);
            if (!isEmptyBlock(world, offset) || world.getBlockState(offset.down()).getBlock() != Blocks.OBSIDIAN) {
                break;
            }
        }
        Block block = world.getBlockState(p.offset(dir, i)).getBlock();
        return block == Blocks.OBSIDIAN ? i : 0;
    }

    private static int calculatePortalHeight(World world, BlockPos bottomLeft, EnumFacing rightDir, EnumFacing leftDir, int width) {
        int height;
        outer:
        for (height = 0; height < 21; ++height) {
            for (int i = 0; i < width; ++i) {
                BlockPos blockpos = bottomLeft.offset(rightDir, i).up(height);
                if (!isEmptyBlock(world, blockpos)) {
                    break outer;
                }
                Block block;
                if (i == 0) {
                    block = world.getBlockState(blockpos.offset(leftDir)).getBlock();
                    if (block != Blocks.OBSIDIAN) {
                        break outer;
                    }
                } else if (i == width - 1) {
                    block = world.getBlockState(blockpos.offset(rightDir)).getBlock();
                    if (block != Blocks.OBSIDIAN) {
                        break outer;
                    }
                }
            }
        }

        for (int j = 0; j < width; ++j) {
            if (world.getBlockState(bottomLeft.offset(rightDir, j).up(height)).getBlock() != Blocks.OBSIDIAN) {
                return 0;
            }
        }

        if (height <= 21 && height >= 3) {
            return height;
        }
        return 0;
    }

    /** Fills a completed frame with our portal blocks (used by the light-up hook). */
    public static void placePortal(World world, Geometry geometry) {
        for (int i = 0; i < geometry.width; i++) {
            for (int j = 0; j < geometry.height; j++) {
                world.setBlockState(
                    geometry.interiorMin.offset(geometry.rightDir(), i).up(j),
                    BlockNetherPortal.BLOCK.getDefaultState().withProperty(BlockPortal.AXIS, geometry.axis), 2);
            }
        }
    }

    /**
     * Vanilla-style search for an existing custom portal: scans 16-block square
     * steps within a 128-block radius of the expected arrival point (like vanilla
     * {@code net.minecraft.world.Teleporter}), vertically through the whole height
     * of each candidate column. Returns the first complete portal rectangle found,
     * or null so the caller builds a fresh one.
     */
    @Nullable
    public static Geometry findNearbyPortal(World world, int centerX, int centerY, int centerZ) {
        int maxY = world.getHeight() - 10;
        for (int dx = -128; dx <= 128; dx += 16) {
            for (int dz = -128; dz <= 128; dz += 16) {
                int x = centerX + dx;
                int z = centerZ + dz;
                for (int y = 1; y <= maxY; y++) {
                    if (world.getBlockState(new BlockPos(x, y, z)).getBlock() == BlockNetherPortal.BLOCK) {
                        Geometry geo = findInterior(world, new BlockPos(x, y, z));
                        if (geo != null) {
                            return geo;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Builds a 2-wide x 3-tall nether portal at the given column: obsidian ring
     * (4x5x1) + portal interior. Only the ring/interior footprint is ever written -
     * no blocks outside of it are removed or replaced. {@code seedY} is the scaled
     * arrival height used to find the ground below (mirrors MultiverseTeleporter).
     */
    public static Geometry placeExitPortal(World world, int x, int seedY, int z, EnumFacing.Axis axis) {
        int groundY = findTopSolidBelow(world, x, z, seedY);
        if (groundY < 0) {
            groundY = findTopSolidBelow(world, x, z, world.getActualHeight() - 1);
        }
        if (groundY < 0) {
            groundY = 64;
        }
        int baseY = clampPortalBase(Math.max(groundY, 0) + 1, world);

        BlockPos interiorMin = axis == EnumFacing.Axis.X
                ? new BlockPos(x - 1, baseY, z)
                : new BlockPos(x, baseY, z - 1);
        int width = 2;
        int height = 3;

        EnumFacing rightDir = axis == EnumFacing.Axis.X ? EnumFacing.WEST : EnumFacing.SOUTH;
        EnumFacing leftDir = axis == EnumFacing.Axis.X ? EnumFacing.EAST : EnumFacing.NORTH;

        // side columns (from base-1 up to base+height)
        for (int k = -1; k <= height; k++) {
            world.setBlockState(interiorMin.offset(leftDir, 1).up(k), Blocks.OBSIDIAN.getDefaultState(), 2);
            world.setBlockState(interiorMin.offset(rightDir, width).up(k), Blocks.OBSIDIAN.getDefaultState(), 2);
        }
        // floor & cap rows across the interior
        for (int i = 0; i < width; i++) {
            world.setBlockState(interiorMin.offset(rightDir, i).down(), Blocks.OBSIDIAN.getDefaultState(), 2);
            world.setBlockState(interiorMin.offset(rightDir, i).up(height), Blocks.OBSIDIAN.getDefaultState(), 2);
        }
        Geometry geometry = new Geometry(interiorMin, width, height, axis);
        placePortal(world, geometry);
        return geometry;
    }

    private static int clampPortalBase(int y, World world) {
        int maxBase = world.getActualHeight() - 6;
        if (y > maxBase) {
            return maxBase;
        }
        return y < 1 ? 1 : y;
    }
    private static int findTopSolidBelow(World world, int x, int z, int startY) {
        int top = Math.min(startY, world.getActualHeight() - 1);
        for (int yy = top; yy >= 0; yy--) {
            IBlockState state = world.getBlockState(new BlockPos(x, yy, z));
            if (state.isTopSolid()) {
                return yy;
            }
        }
        return -1;
    }
}
