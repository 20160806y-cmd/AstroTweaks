package astrotweaks.block;


import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.block.SoundType;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.Block;



public class B_MossyCobblestoneStairs {
	public static final Block block = new BlockCustom().setRegistryName("astrotweaks", "mossy_cobblestone_stairs");
	public static class BlockCustom extends BlockStairs {
		public BlockCustom() {
			super(new Block(Material.ROCK).getDefaultState());
			setUnlocalizedName("mossy_cobblestone_stairs");
			setSoundType(SoundType.STONE);
			setHarvestLevel("pickaxe", 0);
			setHardness(2F);
			setResistance(6F);
			setLightOpacity(255);
			setCreativeTab(astrotweaks.creativetab.ATCreativeTabs.ASTRO_TWEAKS_CT);
		}
		@Override public MapColor getMapColor(IBlockState state,IBlockAccess blockAccess,BlockPos pos) { return MapColor.FOLIAGE; }
	}
}
