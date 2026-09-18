package astrotweaks.block;

import net.minecraft.world.World;
import net.minecraft.world.IBlockAccess;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.EnumFacing;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.SoundType;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.Block;

import java.util.Random;



public class B_MossyStonebrickSlab {
	public static final Block block = new BlockCustom().setRegistryName("astrotweaks", "mossy_stonebrick_slab");
	public static final Block block_slab_double = new BlockCustom.Double().setRegistryName("astrotweaks", "mossy_stonebrick_slab_double");
	public static class BlockCustom extends BlockSlab {
		public BlockCustom() {
			super(Material.ROCK);
			setUnlocalizedName("mossy_stonebrick_slab");
			setSoundType(SoundType.STONE);
			setHarvestLevel("pickaxe", 0);
			setHardness(2F);
			setResistance(6F);
			setLightOpacity(255);
			setCreativeTab(astrotweaks.creativetab.ATCreativeTabs.ASTRO_TWEAKS_CT);
			IBlockState state = this.blockState.getBaseState().withProperty(VARIANT, BlockCustom.Variant.DEFAULT);
			if (!this.isDouble())
				state = state.withProperty(BlockSlab.HALF, EnumBlockHalf.BOTTOM);
			this.setDefaultState(state);
			this.useNeighborBrightness = !this.isDouble();
		}
		public static final PropertyEnum<BlockCustom.Variant> VARIANT = PropertyEnum.<BlockCustom.Variant>create("variant", BlockCustom.Variant.class);
		@Override public MapColor getMapColor(IBlockState state,IBlockAccess blockAccess,BlockPos pos) { return MapColor.FOLIAGE; }
		@Override
		public Item getItemDropped(IBlockState state, Random rand, int fortune) {
			return Item.getItemFromBlock(block);
		}
		@Override
		public ItemStack getItem(World worldIn, BlockPos pos, IBlockState state) {
			return new ItemStack(block);
		}
		@Override
		protected net.minecraft.block.state.BlockStateContainer createBlockState() {
			return this.isDouble()
					? new net.minecraft.block.state.BlockStateContainer(this, new IProperty[]{VARIANT})
					: new net.minecraft.block.state.BlockStateContainer(this, new IProperty[]{HALF, VARIANT});
		}
		@Override
		public IBlockState getStateFromMeta(int meta) {
			if (this.isDouble()) {
				return this.getDefaultState();
			} else {
				return this.getDefaultState().withProperty(HALF, BlockSlab.EnumBlockHalf.values()[meta % 2]);
			}
		}
		@Override
		public int getMetaFromState(IBlockState state) {
			if (this.isDouble()) {
				return 0;
			} else {
				return state.getValue(HALF).ordinal();
			}
		}
		@Override public String getUnlocalizedName(int meta) { return super.getUnlocalizedName(); }
		@Override public IProperty<?> getVariantProperty() { return VARIANT; }
		@Override public Comparable<?> getTypeForItem(ItemStack stack) { return BlockCustom.Variant.DEFAULT; }
		@Override public boolean isDouble() { return false; }
		@Override public boolean doesSideBlockRendering(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing face) {
			if (isDouble()) return true;
			return super.doesSideBlockRendering(state, world, pos, face);
		}
		public enum Variant implements IStringSerializable {
			DEFAULT;
			public String getName() {
				return "default";
			}
		}
		public static class Double extends BlockCustom {
			@Override public boolean isDouble() { return true; }
		}
	}
}
