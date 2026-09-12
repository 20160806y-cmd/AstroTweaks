package astrotweaks.block;


import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.NonNullList;
import net.minecraft.item.ItemStack;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.SoundType;
import net.minecraft.block.Block;

import astrotweaks.util.DropHandler;
import astrotweaks.util.DropHandler.DropEntry;



public class BlockDeepRichMinerals {
    public static final Block block = new BlockCustom().setRegistryName("astrotweaks", "deep_rich_minerals");

    public static class BlockCustom extends Block {
        public BlockCustom() {
            super(Material.IRON);
            setUnlocalizedName("deep_rich_minerals");
            setSoundType(SoundType.STONE);
            setHarvestLevel("pickaxe", 3);
            setHardness(60F);
            setResistance(50F);
            setCreativeTab(astrotweaks.creativetab.ATCreativeTabs.ASTRO_TWEAKS_CT);
        }
        @Override public EnumPushReaction getMobilityFlag(IBlockState state) {return EnumPushReaction.IGNORE;}


        private static final astrotweaks.util.DropHandler.DropEntry[] DE_TABLE = new astrotweaks.util.DropHandler.DropEntry[] {
            new DropEntry("oreDiamond", 	4, 20.0),
            new DropEntry("oreEmerald", 	4,	9.0),
            new DropEntry("oreGold",   		4, 20.0),
			new DropEntry("oreIron", 		4, 11.0),
			new DropEntry("oreLapis", 		4,	9.0),
			new DropEntry("oreUranium", 	4, 15.0),
			new DropEntry("oreThorium", 	4, 15.0),
			new DropEntry("oreTitanium",	4, 14.0),
			new DropEntry("oreNiobium",		4, 12.0),
			new DropEntry("dustTungsten",	4, 12.0),
        };

		private static final DropHandler DE_DROP_TABLE = new DropHandler(DE_TABLE);
		@Override public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
		    World w = (world instanceof World) ? (World) world : null;

		    int rolls = (w != null) ? w.rand.nextInt(4) + 2 /* 2..5 */: new java.util.Random().nextInt(4) + 2;
		    DE_DROP_TABLE.generateDrops(drops, w, rolls);
		}
    }
}
