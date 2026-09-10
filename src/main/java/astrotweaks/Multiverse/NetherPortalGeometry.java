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
 *
 * <p>All position math reuses a single {@link BlockPos.MutableBlockPos} per scan so
 * the hot methods ({@link #findNearbyPortal(World, int, int, int)} in particular,
 * which walks up to ~74k cells) allocate no per-cell {@code BlockPos} objects.
 */
public final class NetherPortalGeometry {

    private NetherPortalGeometry() {}

    private static final Block OBSIDIAN = Blocks.OBSIDIAN;
    private static final IBlockState CPORTAL = BlockNetherPortal.BLOCK.getDefaultState();

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
        if (material == Material.AIR) return true;

        Block block = state.getBlock();
        return block == Blocks.FIRE || block == Blocks.PORTAL || block == BlockNetherPortal.BLOCK;
    }

    /** X component of a horizontal {@link EnumFacing} step (N/S/E/W only). */
    static int stepX(EnumFacing dir) {
        switch (dir) {
            case EAST:  return 1;
            case WEST:  return -1;
            default:    return 0;
        }
    }
    /** Z component of a horizontal {@link EnumFacing} step (N/S/E/W only). */
    static int stepZ(EnumFacing dir) {
        switch (dir) {
            case SOUTH: return 1;
            case NORTH: return -1;
            default:    return 0;
        }
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
        BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
        int bx = geometry.interiorMin.getX();
        int by = geometry.interiorMin.getY();
        int bz = geometry.interiorMin.getZ();
        EnumFacing rightDir = geometry.rightDir();
        int rx = stepX(rightDir);
        int rz = stepZ(rightDir);
        for (int i = 0; i < geometry.width; i++) {
            for (int j = 0; j < geometry.height; j++) {
                cell.setPos(bx + rx * i, by + j, bz + rz * i);
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

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(p.getX(), p.getY(), p.getZ());
        int startY = pos.getY();
        // walk down onto the obsidian base; "pos" ends at the highest empty cell above a solid one
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
        while (pos.getY() > startY - 21 && pos.getY() > 0) {
            below.setPos(pos.getX(), pos.getY() - 1, pos.getZ());
            if (!isEmptyBlock(world, below)) {
                break;
            }
            pos.setPos(below.getX(), below.getY(), below.getZ());
        }

        int i = distanceToEdge(world, pos.getX(), pos.getY(), pos.getZ(), leftDir) - 1;
        int width = 0;
        if (i >= 0) {
            pos.setPos(pos.getX() + stepX(leftDir) * i, pos.getY(), pos.getZ() + stepZ(leftDir) * i);
            width = distanceToEdge(world, pos.getX(), pos.getY(), pos.getZ(), rightDir);
            if (width < 2 || width > 21) {
                width = 0;
            }
        }
        if (width >= 2 && width <= 21) {
            int height = calculatePortalHeight(world, pos.getX(), pos.getY(), pos.getZ(), rightDir, leftDir, width);
            if (height >= 3 && height <= 21) {
                return new Geometry(pos.toImmutable(), width, height, axis);
            }
        }
        return null;
    }

    private static int distanceToEdge(World world, int x, int y, int z, EnumFacing dir) {
        BlockPos.MutableBlockPos offset = new BlockPos.MutableBlockPos();
        int dx = stepX(dir);
        int dz = stepZ(dir);
        int i;
        for (i = 0; i < 22; ++i) {
            offset.setPos(x + dx * i, y, z + dz * i);
            if (!isEmptyBlock(world, offset) || world.getBlockState(offset.setPos(offset.getX(), offset.getY() - 1, offset.getZ())).getBlock() != OBSIDIAN) {
                break;
            }
        }
        offset.setPos(x + dx * i, y, z + dz * i);
        return world.getBlockState(offset).getBlock() == OBSIDIAN ? i : 0;
    }

    private static int calculatePortalHeight(World world, int bx, int by, int bz, EnumFacing rightDir, EnumFacing leftDir, int width) {
        BlockPos.MutableBlockPos mc = new BlockPos.MutableBlockPos();
        int rdx = stepX(rightDir);
        int rdz = stepZ(rightDir);
        int ldx = stepX(leftDir);
        int ldz = stepZ(leftDir);
        int height;
        outer:
        for (height = 0; height < 21; ++height) {
            for (int i = 0; i < width; ++i) {
                mc.setPos(bx + rdx * i, by + height, bz + rdz * i);
                if (!isEmptyBlock(world, mc)) {
                    break outer;
                }
                if (i == 0) {
                    if (world.getBlockState(mc.setPos(mc.getX() + ldx, mc.getY(), mc.getZ() + ldz)).getBlock() != OBSIDIAN) {
                        break outer;
                    }
                } else if (i == width - 1) {
                    if (world.getBlockState(mc.setPos(mc.getX() + rdx, mc.getY(), mc.getZ() + rdz)).getBlock() != OBSIDIAN) {
                        break outer;
                    }
                }
            }
        }
        for (int j = 0; j < width; ++j) {
            if (world.getBlockState(mc.setPos(bx + rdx * j, by + height, bz + rdz * j)).getBlock() != OBSIDIAN) {
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
        BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
        int bx = geometry.interiorMin.getX();
        int by = geometry.interiorMin.getY();
        int bz = geometry.interiorMin.getZ();
        EnumFacing rightDir = geometry.rightDir();
        int rx = stepX(rightDir);
        int rz = stepZ(rightDir);
        for (int i = 0; i < geometry.width; i++) {
            for (int j = 0; j < geometry.height; j++) {
                cell.setPos(bx + rx * i, by + j, bz + rz * i);
                world.setBlockState(
                    cell, CPORTAL.withProperty(BlockPortal.AXIS, geometry.axis), 2);
            }
        }
    }

    /**
     * Vanilla-style search for an existing custom portal: scans square rings of
     * 16-block steps expanding outward from the expected arrival column, within a
     * 128-block radius (like vanilla {@code net.minecraft.world.Teleporter} but
     * starting from the center instead of the negative corner), vertically through
     * the whole height of each candidate column. Returns the first complete portal
     * rectangle found, or null so the caller builds a fresh one.
     *
     * <p>The scan reuses one {@link BlockPos.MutableBlockPos} for every candidate
     * block, so the full ~74k-cell walk allocates no {@code BlockPos} objects.
     */
    @Nullable
    public static Geometry findNearbyPortal(World world, int centerX, int centerY, int centerZ) {
        int maxY = world.getHeight() - 10;
        BlockPos.MutableBlockPos candidate = new BlockPos.MutableBlockPos();

        Geometry geo = scanColumn(world, candidate, centerX, centerZ, maxY);
        if (geo != null) { return geo; }

        for (int extent = 16; extent <= 128; extent += 16) {
            // right column (dx = +extent), then left column (dx = -extent)
            int x = centerX + extent;
            for (int dz = -extent; dz <= extent; dz += 16) {
                geo = scanColumn(world, candidate, x, centerZ + dz, maxY);
                if (geo != null) {
                    return geo;
                }
            }
            x = centerX - extent;
            for (int dz = -extent; dz <= extent; dz += 16) {
                geo = scanColumn(world, candidate, x, centerZ + dz, maxY);
                if (geo != null) {
                    return geo;
                }
            }
            // top row (dz = +extent) and bottom row (dz = -extent), skipping the corners
            int z = centerZ + extent;
            for (int dx = -extent + 16; dx <= extent - 16; dx += 16) {
                geo = scanColumn(world, candidate, centerX + dx, z, maxY);
                if (geo != null) {
                    return geo;
                }
            }
            z = centerZ - extent;
            for (int dx = -extent + 16; dx <= extent - 16; dx += 16) {
                geo = scanColumn(world, candidate, centerX + dx, z, maxY);
                if (geo != null) {
                    return geo;
                }
            }
        }
        return null;
    }

    @Nullable
    private static Geometry scanColumn(World world, BlockPos.MutableBlockPos candidate, int x, int z, int maxY) {
        for (int y = 1; y <= maxY; y++) {
            candidate.setPos(x, y, z);
            if (world.getBlockState(candidate).getBlock() == BlockNetherPortal.BLOCK) {
                Geometry geo = findInterior(world, candidate);
                if (geo != null) {
                    return geo;
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

        int interiorMinX = axis == EnumFacing.Axis.X ? x - 1 : x;
        int interiorMinZ = axis == EnumFacing.Axis.X ? z : z - 1;
        int width = 2;
        int height = 3;

        EnumFacing rightDir = axis == EnumFacing.Axis.X ? EnumFacing.WEST : EnumFacing.SOUTH;
        EnumFacing leftDir = axis == EnumFacing.Axis.X ? EnumFacing.EAST : EnumFacing.NORTH;
        int rdx = stepX(rightDir);
        int rdz = stepZ(rightDir);
        int ldx = stepX(leftDir);
        int ldz = stepZ(leftDir);

        BlockPos.MutableBlockPos mc = new BlockPos.MutableBlockPos();
        // side columns (from base-1 up to base+height)
        for (int k = -1; k <= height; k++) {
            mc.setPos(interiorMinX + ldx, baseY + k, interiorMinZ + ldz);
            world.setBlockState(mc, OBSIDIAN.getDefaultState(), 2);
            mc.setPos(interiorMinX + rdx * width, baseY + k, interiorMinZ + rdz * width);
            world.setBlockState(mc, OBSIDIAN.getDefaultState(), 2);
        }
        // floor & cap rows across the interior
        for (int i = 0; i < width; i++) {
            mc.setPos(interiorMinX + rdx * i, baseY - 1, interiorMinZ + rdz * i);
            world.setBlockState(mc, OBSIDIAN.getDefaultState(), 2);
            mc.setPos(interiorMinX + rdx * i, baseY + height, interiorMinZ + rdz * i);
            world.setBlockState(mc, OBSIDIAN.getDefaultState(), 2);
        }
        Geometry geometry = new Geometry(new BlockPos(interiorMinX, baseY, interiorMinZ), width, height, axis);
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
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        for (int yy = top; yy >= 0; yy--) {
            if (world.getBlockState(mpos.setPos(x, yy, z)).isTopSolid()) {
                return yy;
            }
        }
        return -1;
    }
}
