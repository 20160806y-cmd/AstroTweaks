package astrotweaks.block;

import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraft.world.IBlockAccess;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.NonNullList;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.item.ItemStack;

import net.minecraft.init.Blocks;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.material.Material;
import net.minecraft.block.SoundType;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockFaceShape;

import java.util.Set;
import java.util.Random;

import astrotweaks.item.ATItems;
import astrotweaks.ModVariables;



public class BlockGroundRock1 {
	public static final Block block = new BlockCustom().setRegistryName("astrotweaks", "ground_rock_1");

	private static final Set<Biome> rgb = ModVariables.Rock_Gen_Biomes_Cached;
	private static double rga;

	public static void generateWorld(Random random, int chunkX, int chunkZ, World world, int dimID, IChunkGenerator cg, IChunkProvider cp) {
	    if (world.provider.getDimensionType() != DimensionType.OVERWORLD) return;
			rga = ModVariables.Rock_Gen_Attempts;
		    astrotweaks.world.SurfaceWorldGenerator.generateSurface(random, chunkX, chunkZ, world, block, rgb, rga /*Double attempts*/, ModVariables.Rock_Gen_Min_Y /*min Y*/, ModVariables.Rock_Gen_Max_Y /*max Y*/);
	}
	public static class BlockCustom extends Block {
		public BlockCustom() {
			super(Material.CLOTH);
			setUnlocalizedName("ground_rock_1");
			setSoundType(SoundType.STONE);
			setHardness(0F);
			setResistance(0F);
			setLightOpacity(0);
		}
		@SideOnly(Side.CLIENT)
		@Override public BlockRenderLayer getBlockLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }
		@javax.annotation.Nullable
		@Override public AxisAlignedBB getCollisionBoundingBox(IBlockState blockState, IBlockAccess worldIn, BlockPos pos) { return NULL_AABB; }
		@Override public boolean isPassable(IBlockAccess worldIn, BlockPos pos) { return true; }
		@Override public boolean isFullCube(IBlockState state) { return false; }
		@Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
			return new AxisAlignedBB(0.3125, 0.0, 0.3125, 0.6875, 0.250, 0.6875);
		}
		@Override public boolean isOpaqueCube(IBlockState state) { return false; }
		@Override public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) { return BlockFaceShape.UNDEFINED; }
		@Override public boolean isReplaceable(IBlockAccess blockAccess, BlockPos pos) { return true; }
		@Override public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
		    drops.add(new ItemStack(ATItems.ROCK, 1));
		}
		@Override public boolean canPlaceBlockAt(World worldIn, BlockPos pos) {
		    BlockPos below = pos.down();
		    IBlockState stateBelow = worldIn.getBlockState(below);
		    return stateBelow.isSideSolid(worldIn, below, EnumFacing.UP);
		}
		@Override
		public void neighborChanged(IBlockState state, World world, BlockPos pos, Block neighborBlock, BlockPos fromPos) {
			super.neighborChanged(state, world, pos, neighborBlock, fromPos);
			if (world.isRemote) return;
			int x = pos.getX();
			int y = pos.getY();
			int z = pos.getZ();

			if ((((world.getBlockState(new BlockPos(x, y, z))).getBlock() == BlockGroundRock1.block.getDefaultState().getBlock())
					&& ((world.getBlockState(new BlockPos(x, y - 1, z))).getBlock() == Blocks.AIR.getDefaultState().getBlock()))) {
				world.setBlockToAir(new BlockPos(x, y, z));

				EntityItem entityToSpawn = new EntityItem(world, (x + 0.5), (y + 0.5), (z + 0.5), new ItemStack(ATItems.ROCK, 1));
				entityToSpawn.setPickupDelay(10);
				world.spawnEntity(entityToSpawn);
			}
		    if (isAdjacentToWater(world, pos) || world.getBlockState(pos).getMaterial() == Material.WATER) {
		        world.destroyBlock(pos, true);
		    }
		}
		@Override
		public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer entity, EnumHand hand, EnumFacing direction, float hitX, float hitY, float hitZ) {
			super.onBlockActivated(world, pos, state, entity, hand, direction, hitX, hitY, hitZ);
			int x = pos.getX();
			int y = pos.getY();
			int z = pos.getZ();
			world.setBlockToAir(new BlockPos(x, y, z));
			if (!world.isRemote) {
				EntityItem entityToSpawn = new EntityItem(world, (x + 0.5), (y + 0.5), (z + 0.5), new ItemStack(ATItems.ROCK, 1));
				entityToSpawn.setPickupDelay(10);
				world.spawnEntity(entityToSpawn);
			}
			return true;
		}
		private boolean isAdjacentToWater(World world, BlockPos pos) {
		    for (EnumFacing f : EnumFacing.values()) {
		        IBlockState s = world.getBlockState(pos.offset(f));
		        if (s.getMaterial() == Material.WATER) return true;
		    }
		    return false;
		}
	}
}
