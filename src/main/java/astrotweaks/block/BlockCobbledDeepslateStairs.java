
package astrotweaks.block;


import net.minecraft.block.material.Material;
import net.minecraft.block.SoundType;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.Block;



public class BlockCobbledDeepslateStairs {
	public static final Block block = new BlockCustom().setRegistryName("astrotweaks", "cobbled_deepslate_stairs");
	public static class BlockCustom extends BlockStairs {
		public BlockCustom() {
			super(new Block(Material.ROCK).getDefaultState());
			setUnlocalizedName("cobbled_deepslate_stairs");
			setSoundType(SoundType.STONE);
			setHarvestLevel("pickaxe", 0);
			setHardness(3.5F);
			setResistance(12F);
			setLightOpacity(255);
			setCreativeTab(astrotweaks.creativetab.ATCreativeTabs.ASTRO_TWEAKS_CT);
		}
	}
}
