
package astrotweaks.block;


import net.minecraft.world.gen.feature.WorldGenMinable;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.World;
import net.minecraft.world.IBlockAccess;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.NonNullList;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Blocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.SoundType;
import net.minecraft.block.Block;

import java.util.Random;

import astrotweaks.item.ATItems;
import astrotweaks.creativetab.ATCreativeTabs;



public class BlockRubyOre {
	public static final Block block = new BlockCustom().setRegistryName("astrotweaks", "ruby_ore");

	private static final com.google.common.base.Predicate<IBlockState> STONE_MATCH = state -> state != null && state.getBlock() == Blocks.STONE;

	public static void generateWorld(Random random, int chunkX, int chunkZ, World world, int dimID, IChunkGenerator cg, IChunkProvider cp) {
	    if (dimID != 0) return;
	    //if (!ModVariables.OW_Ruby_Gen) return;

	    WorldGenMinable gen = new WorldGenMinable(block.getDefaultState(), 3, STONE_MATCH);
	    for (int i = 0; i < 7; i++) {
	        int x = chunkX + random.nextInt(16);
	        int y = random.nextInt(21) + 3;
	        int z = chunkZ + random.nextInt(16);

	        gen.generate(world, random, new BlockPos(x, y, z));
	    }
	}

	public static class BlockCustom extends Block {
		public BlockCustom() {
			super(Material.ROCK);
			setUnlocalizedName("ruby_ore");
			setSoundType(SoundType.STONE);
			setHarvestLevel("pickaxe", 2);
			setHardness(5F);
			setResistance(15F);
			setCreativeTab(ATCreativeTabs.ASTRO_TWEAKS_CT);
		}
		@Override public MapColor getMapColor(IBlockState state, IBlockAccess blockAccess, BlockPos pos) { return MapColor.STONE; }
		@Override public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
			drops.add(new ItemStack(ATItems.RUBY, (int) (1)));
		}
	}
}
