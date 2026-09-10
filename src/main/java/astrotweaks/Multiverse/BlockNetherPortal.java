package astrotweaks.Multiverse;

import net.minecraft.block.BlockPortal;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * The custom nether portal block used inside multiverse-level dimensions.
 *
 * <p>Visually and mechanically it behaves like the vanilla portal (thin slab
 * bounding box, animated texture, pigman rumbling, ambient particles), but its
 * travel is driven by {@link NetherPortalLink} which teleports the entity to the
 * linked portal's center (or builds a fresh exit) instead of the vanilla generic
 * nether mapping.
 *
 * <p>Every portal block carries a {@link TileNetherPortal} beacon used for the
 * two-way portal link bookkeeping.
 */
public class BlockNetherPortal extends BlockPortal {
    public static final BlockNetherPortal BLOCK = new BlockNetherPortal();

    private BlockNetherPortal() {
        super();
        setRegistryName("astrotweaks", "nether_portal");
        setUnlocalizedName("nether_portal");
        setLightLevel(0.75F);
        setResistance(0.0F);
        setSoundType(SoundType.GLASS);
        setBlockUnbreakable();
    }
    @Override
    public void onEntityCollidedWithBlock(World worldIn, BlockPos pos, IBlockState state, Entity entityIn) {
        if (!entityIn.isRiding() && !entityIn.isBeingRidden() && entityIn.isNonBoss()) {
            NetherPortalLink.handleCollision(worldIn, pos, entityIn);
        }
    }
    @Override
    public MapColor getMapColor(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return MapColor.PURPLE;
    }
    @Override public boolean hasTileEntity(IBlockState state) {
        return true;
    }
    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileNetherPortal();
    }
    @Override
    public void neighborChanged(IBlockState state, World worldIn, BlockPos pos, net.minecraft.block.Block blockIn, BlockPos fromPos) {
        if (worldIn.isRemote) return;
        if (NetherPortalGeometry.findInterior(worldIn, pos) == null) {
            worldIn.setBlockToAir(pos);
        }
    }
}
