package astrotweaks.oredict;


import net.minecraftforge.oredict.OreDictionary;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;

import astrotweaks.item.*;

import javax.annotation.Nonnull;

import astrotweaks.block.*;

//import net.minecraftforge.oredict.OreIngredient;



public class UOredictRegistrar {
	public UOredictRegistrar() {}

	public static void init() {
		new UOredictRegistrar();

		OreItems();
		OreBlocks();
		OreGems();
		OreDusts();
		OrePlates();
		OreNuggets();
		OreIngots();
		OreOres();
		OreRods();
		OreTools();
	}

	// Немного сократить размер кода
	private static void reg(String name, @Nonnull ItemStack ore) {
		OreDictionary.registerOre(name, ore);
	}
	private static void reg(String name, @Nonnull Block ore) {
		OreDictionary.registerOre(name, ore);
	}
	private static void reg(String name, @Nonnull Item ore) {
		OreDictionary.registerOre(name, ore);
	}




	private static void OreItems() {
		reg("bonemeal", new ItemStack(Items.DYE, 1, 15));
		
		reg("rock", new ItemStack(ATItems.ROCK, 1));
		reg("rock", new ItemStack(ATItems.ROCK_FLAT, 1));
		reg("rockFlat", new ItemStack(ATItems.ROCK_FLAT, 1));
		reg("brickStone", new ItemStack(ATItems.STONE_BRICK, 1));
		reg("ingotStone", new ItemStack(ATItems.STONE_BRICK, 1));
		reg("ingotBrickStone", new ItemStack(ATItems.STONE_BRICK, 1));
		reg("brickClay", new ItemStack(ATItems.CLAY_BRICK, 1));
		reg("ingotClay", new ItemStack(ATItems.CLAY_BRICK, 1));
		reg("ingotBrickClay", new ItemStack(ATItems.CLAY_BRICK, 1));
		reg("discSilicon", new ItemStack(ATItems.SILICON_DISC, 1));
        reg("twine", new ItemStack(ATItems.CORDAGE_FIBER, 1));
        
        reg("singularity", new ItemStack(ATItems.NEUTRONIUM_SINGULARITY, 1));
        reg("singularityNeutronium", new ItemStack(ATItems.NEUTRONIUM_SINGULARITY, 1));
        
        reg("singularity", new ItemStack(ATItems.INFINITY_SINGULARITY, 1));
        reg("singularityInfinity", new ItemStack(ATItems.INFINITY_SINGULARITY, 1));


        reg("shardFlint", new ItemStack(ATItems.FLINT_SHARD, 1));
        reg("shard", new ItemStack(ATItems.FLINT_SHARD, 1));
        reg("shardBone", new ItemStack(ATItems.BONE_SHARD, 1));
        reg("shard", new ItemStack(ATItems.BONE_SHARD, 1));
        reg("shardDiamond", new ItemStack(ATItems.DIAMOND_SHARD, 1));
        reg("shard", new ItemStack(ATItems.DIAMOND_SHARD, 1));

		reg("wireGold", new ItemStack(ATItems.COPPER_COIL, 1));
		reg("wireCopper", new ItemStack(ATItems.GOLDEN_COIL, 1));

		reg("listAllmeatraw", new ItemStack(Items.PORKCHOP, 1));
		reg("listAllmeatraw", new ItemStack(Items.BEEF, 1));
		reg("listAllmeatraw", new ItemStack(Items.RABBIT, 1));
		reg("listAllmeatraw", new ItemStack(Items.MUTTON, 1));
		reg("listAllmeatraw", new ItemStack(Items.CHICKEN, 1));

		reg("listAllfishraw", new ItemStack(Items.FISH, 1));
		reg("listAllfishraw", new ItemStack(Items.FISH, 1, 1));
		reg("listAllfishraw", new ItemStack(Items.FISH, 1, 2));

		reg("listAllfishcooked", new ItemStack(Items.COOKED_FISH, 1));
		reg("listAllfishcooked", new ItemStack(ItemCoockedTropicalFish.TROPICAL_FISH, 1));
		reg("listAllfishcooked", new ItemStack(ItemCoockedTropicalFish.TROPICAL_FISH, 1, 1));
		reg("itemCookedFish", new ItemStack(ItemCoockedTropicalFish.TROPICAL_FISH, 1));

		reg("listAllmilk", new ItemStack(ItemMilkBottle.MILK_BOTTLE, 1));

		reg("banner", new ItemStack(Items.BANNER, 1));
		reg("banner", new ItemStack(Items.BANNER, 1, 1));
		reg("banner", new ItemStack(Items.BANNER, 1, 2));
		reg("banner", new ItemStack(Items.BANNER, 1, 3));
		reg("banner", new ItemStack(Items.BANNER, 1, 4));
		reg("banner", new ItemStack(Items.BANNER, 1, 5));
		reg("banner", new ItemStack(Items.BANNER, 1, 6));
		reg("banner", new ItemStack(Items.BANNER, 1, 7));
		reg("banner", new ItemStack(Items.BANNER, 1, 8));
		reg("banner", new ItemStack(Items.BANNER, 1, 9));
		reg("banner", new ItemStack(Items.BANNER, 1, 10));
		reg("banner", new ItemStack(Items.BANNER, 1, 11));
		reg("banner", new ItemStack(Items.BANNER, 1, 12));
		reg("banner", new ItemStack(Items.BANNER, 1, 13));
		reg("banner", new ItemStack(Items.BANNER, 1, 14));
		reg("banner", new ItemStack(Items.BANNER, 1, 15));
	}
	private static void OreBlocks() {
		reg("blockRuby", new ItemStack(ATBlocks.RUBY_BLOCK, 1));
		reg("blockBrass", new ItemStack(ATBlocks.BRASS_BLOCK, 1));
		reg("blockMineralSteel", new ItemStack(ATBlocks.MINERAL_STEEL, 1));

		reg("stone", new ItemStack(ATBlocks.DEEPSLATE, 1));
		reg("cobblestone", new ItemStack(ATBlocks.COBBLED_DEEPSLATE, 1));
		reg("dirt", new ItemStack(ATBlocks.DIRT_BRICKS, 1));
		reg("blockNetherStar", new ItemStack(ATBlocks.NETHERSTAR_BLOCK, 1));

		reg("hardenedClay", new ItemStack(Blocks.HARDENED_CLAY, 1));
		reg("hardenedClay", new ItemStack(Blocks.STAINED_HARDENED_CLAY, 1));
		reg("stainedHardenedClay", new ItemStack(Blocks.STAINED_HARDENED_CLAY, 1));
		reg("hardenedClay", new ItemStack(Blocks.STAINED_HARDENED_CLAY, 1, 1));
		reg("stainedHardenedClay", new ItemStack(Blocks.STAINED_HARDENED_CLAY, 1, 1));
		reg("hardenedClay", new ItemStack(Blocks.STAINED_HARDENED_CLAY, 1, 2));
		reg("stainedHardenedClay", new ItemStack(Blocks.STAINED_HARDENED_CLAY, 1, 2));
		reg("hardenedClay", new ItemStack(Blocks.STAINED_HARDENED_CLAY, 1, 3));
		reg("stainedHardenedClay", new ItemStack(Blocks.STAINED_HARDENED_CLAY, 1, 3));
		reg("hardenedClay", new ItemStack(Blocks.STAINED_HARDENED_CLAY, 1, 4));
		reg("stainedHardenedClay", new ItemStack(Blocks.STAINED_HARDENED_CLAY, 1, 4));
		reg("hardenedClay", new ItemStack(Blocks.STAINED_HARDENED_CLAY, 1, 5));
		reg("stainedHardenedClay", new ItemStack(Blocks.STAINED_HARDENED_CLAY, 1, 5));
		reg("hardenedClay", new ItemStack(Blocks.STAINED_HARDENED_CLAY, 1, 6));
		reg("stainedHardenedClay", new ItemStack(Blocks.STAINED_HARDENED_CLAY, 1, 6));
		reg("hardenedClay", new ItemStack(Blocks.STAINED_HARDENED_CLAY, 1, 7));
		reg("stainedHardenedClay", new ItemStack(Blocks.STAINED_HARDENED_CLAY, 1, 7));
		reg("hardenedClay", new ItemStack(Blocks.STAINED_HARDENED_CLAY, 1, 8));
		reg("stainedHardenedClay", new ItemStack(Blocks.STAINED_HARDENED_CLAY, 1, 8));
		reg("hardenedClay", new ItemStack(Blocks.STAINED_HARDENED_CLAY, 1, 9));
		reg("stainedHardenedClay", new ItemStack(Blocks.STAINED_HARDENED_CLAY, 1, 9));
		reg("hardenedClay", new ItemStack(Blocks.STAINED_HARDENED_CLAY, 1, 10));
		reg("stainedHardenedClay", new ItemStack(Blocks.STAINED_HARDENED_CLAY, 1, 10));
		reg("hardenedClay", new ItemStack(Blocks.STAINED_HARDENED_CLAY, 1, 11));
		reg("stainedHardenedClay", new ItemStack(Blocks.STAINED_HARDENED_CLAY, 1, 11));
		reg("hardenedClay", new ItemStack(Blocks.STAINED_HARDENED_CLAY, 1, 12));
		reg("stainedHardenedClay", new ItemStack(Blocks.STAINED_HARDENED_CLAY, 1, 12));
		reg("hardenedClay", new ItemStack(Blocks.STAINED_HARDENED_CLAY, 1, 13));
		reg("stainedHardenedClay", new ItemStack(Blocks.STAINED_HARDENED_CLAY, 1, 13));
		reg("hardenedClay", new ItemStack(Blocks.STAINED_HARDENED_CLAY, 1, 14));
		reg("stainedHardenedClay", new ItemStack(Blocks.STAINED_HARDENED_CLAY, 1, 14));
		reg("hardenedClay", new ItemStack(Blocks.STAINED_HARDENED_CLAY, 1, 15));
		reg("stainedHardenedClay", new ItemStack(Blocks.STAINED_HARDENED_CLAY, 1, 15));

		reg("shulkerBox", new ItemStack(Blocks.WHITE_SHULKER_BOX, 1));
		reg("shulkerBox", new ItemStack(Blocks.ORANGE_SHULKER_BOX, 1));
		reg("shulkerBox", new ItemStack(Blocks.MAGENTA_SHULKER_BOX, 1));
		reg("shulkerBox", new ItemStack(Blocks.LIGHT_BLUE_SHULKER_BOX, 1));
		reg("shulkerBox", new ItemStack(Blocks.YELLOW_SHULKER_BOX, 1));
		reg("shulkerBox", new ItemStack(Blocks.LIME_SHULKER_BOX, 1));
		reg("shulkerBox", new ItemStack(Blocks.PINK_SHULKER_BOX, 1));
		reg("shulkerBox", new ItemStack(Blocks.GRAY_SHULKER_BOX, 1));
		reg("shulkerBox", new ItemStack(Blocks.SILVER_SHULKER_BOX, 1));
		reg("shulkerBox", new ItemStack(Blocks.CYAN_SHULKER_BOX, 1));
		reg("shulkerBox", new ItemStack(Blocks.PURPLE_SHULKER_BOX, 1));
		reg("shulkerBox", new ItemStack(Blocks.BLUE_SHULKER_BOX, 1));
		reg("shulkerBox", new ItemStack(Blocks.BROWN_SHULKER_BOX, 1));
		reg("shulkerBox", new ItemStack(Blocks.GREEN_SHULKER_BOX, 1));
		reg("shulkerBox", new ItemStack(Blocks.RED_SHULKER_BOX, 1));
		reg("shulkerBox", new ItemStack(Blocks.BLACK_SHULKER_BOX, 1));

		/* Чета форджу не нравятся кровати с OreDict тегами -.-
		reg("bed", new ItemStack(Blocks.BED, 1));
		reg("bed", new ItemStack(Blocks.BED, 1, 1));
		reg("bed", new ItemStack(Blocks.BED, 1, 2));
		reg("bed", new ItemStack(Blocks.BED, 1, 3));
		reg("bed", new ItemStack(Blocks.BED, 1, 4));
		reg("bed", new ItemStack(Blocks.BED, 1, 5));
		reg("bed", new ItemStack(Blocks.BED, 1, 6));
		reg("bed", new ItemStack(Blocks.BED, 1, 7));
		reg("bed", new ItemStack(Blocks.BED, 1, 8));
		reg("bed", new ItemStack(Blocks.BED, 1, 9));
		reg("bed", new ItemStack(Blocks.BED, 1, 10));
		reg("bed", new ItemStack(Blocks.BED, 1, 11));
		reg("bed", new ItemStack(Blocks.BED, 1, 12));
		reg("bed", new ItemStack(Blocks.BED, 1, 13));
		reg("bed", new ItemStack(Blocks.BED, 1, 14));
		reg("bed", new ItemStack(Blocks.BED, 1, 15));
		*/

		reg("carpet", new ItemStack(Blocks.CARPET, 1));
		reg("carpet", new ItemStack(Blocks.CARPET, 1, 1));
		reg("carpet", new ItemStack(Blocks.CARPET, 1, 2));
		reg("carpet", new ItemStack(Blocks.CARPET, 1, 3));
		reg("carpet", new ItemStack(Blocks.CARPET, 1, 4));
		reg("carpet", new ItemStack(Blocks.CARPET, 1, 5));
		reg("carpet", new ItemStack(Blocks.CARPET, 1, 6));
		reg("carpet", new ItemStack(Blocks.CARPET, 1, 7));
		reg("carpet", new ItemStack(Blocks.CARPET, 1, 8));
		reg("carpet", new ItemStack(Blocks.CARPET, 1, 9));
		reg("carpet", new ItemStack(Blocks.CARPET, 1, 10));
		reg("carpet", new ItemStack(Blocks.CARPET, 1, 11));
		reg("carpet", new ItemStack(Blocks.CARPET, 1, 12));
		reg("carpet", new ItemStack(Blocks.CARPET, 1, 13));
		reg("carpet", new ItemStack(Blocks.CARPET, 1, 14));
		reg("carpet", new ItemStack(Blocks.CARPET, 1, 15));

		reg("blockConcrete", new ItemStack(Blocks.CONCRETE, 1));
		reg("blockConcrete", new ItemStack(Blocks.CONCRETE, 1, 1));
		reg("blockConcrete", new ItemStack(Blocks.CONCRETE, 1, 2));
		reg("blockConcrete", new ItemStack(Blocks.CONCRETE, 1, 3));
		reg("blockConcrete", new ItemStack(Blocks.CONCRETE, 1, 4));
		reg("blockConcrete", new ItemStack(Blocks.CONCRETE, 1, 5));
		reg("blockConcrete", new ItemStack(Blocks.CONCRETE, 1, 6));
		reg("blockConcrete", new ItemStack(Blocks.CONCRETE, 1, 7));
		reg("blockConcrete", new ItemStack(Blocks.CONCRETE, 1, 8));
		reg("blockConcrete", new ItemStack(Blocks.CONCRETE, 1, 9));
		reg("blockConcrete", new ItemStack(Blocks.CONCRETE, 1, 10));
		reg("blockConcrete", new ItemStack(Blocks.CONCRETE, 1, 11));
		reg("blockConcrete", new ItemStack(Blocks.CONCRETE, 1, 12));
		reg("blockConcrete", new ItemStack(Blocks.CONCRETE, 1, 13));
		reg("blockConcrete", new ItemStack(Blocks.CONCRETE, 1, 14));
		reg("blockConcrete", new ItemStack(Blocks.CONCRETE, 1, 15));
		reg("blockConcretePowder", new ItemStack(Blocks.CONCRETE_POWDER, 1));
		reg("blockConcretePowder", new ItemStack(Blocks.CONCRETE_POWDER, 1, 1));
		reg("blockConcretePowder", new ItemStack(Blocks.CONCRETE_POWDER, 1, 2));
		reg("blockConcretePowder", new ItemStack(Blocks.CONCRETE_POWDER, 1, 3));
		reg("blockConcretePowder", new ItemStack(Blocks.CONCRETE_POWDER, 1, 4));
		reg("blockConcretePowder", new ItemStack(Blocks.CONCRETE_POWDER, 1, 5));
		reg("blockConcretePowder", new ItemStack(Blocks.CONCRETE_POWDER, 1, 6));
		reg("blockConcretePowder", new ItemStack(Blocks.CONCRETE_POWDER, 1, 7));
		reg("blockConcretePowder", new ItemStack(Blocks.CONCRETE_POWDER, 1, 8));
		reg("blockConcretePowder", new ItemStack(Blocks.CONCRETE_POWDER, 1, 9));
		reg("blockConcretePowder", new ItemStack(Blocks.CONCRETE_POWDER, 1, 10));
		reg("blockConcretePowder", new ItemStack(Blocks.CONCRETE_POWDER, 1, 11));
		reg("blockConcretePowder", new ItemStack(Blocks.CONCRETE_POWDER, 1, 12));
		reg("blockConcretePowder", new ItemStack(Blocks.CONCRETE_POWDER, 1, 13));
		reg("blockConcretePowder", new ItemStack(Blocks.CONCRETE_POWDER, 1, 14));
		reg("blockConcretePowder", new ItemStack(Blocks.CONCRETE_POWDER, 1, 15));
		reg("blockGlazedTerracota", new ItemStack(Blocks.WHITE_GLAZED_TERRACOTTA, 1));
		reg("blockGlazedTerracota", new ItemStack(Blocks.ORANGE_GLAZED_TERRACOTTA, 1));
		reg("blockGlazedTerracota", new ItemStack(Blocks.MAGENTA_GLAZED_TERRACOTTA, 1));
		reg("blockGlazedTerracota", new ItemStack(Blocks.LIGHT_BLUE_GLAZED_TERRACOTTA, 1));
		reg("blockGlazedTerracota", new ItemStack(Blocks.YELLOW_GLAZED_TERRACOTTA, 1));
		reg("blockGlazedTerracota", new ItemStack(Blocks.LIME_GLAZED_TERRACOTTA, 1));
		reg("blockGlazedTerracota", new ItemStack(Blocks.PINK_GLAZED_TERRACOTTA, 1));
		reg("blockGlazedTerracota", new ItemStack(Blocks.GRAY_GLAZED_TERRACOTTA, 1));
		reg("blockGlazedTerracota", new ItemStack(Blocks.SILVER_GLAZED_TERRACOTTA, 1));
		reg("blockGlazedTerracota", new ItemStack(Blocks.CYAN_GLAZED_TERRACOTTA, 1));
		reg("blockGlazedTerracota", new ItemStack(Blocks.PURPLE_GLAZED_TERRACOTTA, 1));
		reg("blockGlazedTerracota", new ItemStack(Blocks.BLUE_GLAZED_TERRACOTTA, 1));
		reg("blockGlazedTerracota", new ItemStack(Blocks.BROWN_GLAZED_TERRACOTTA, 1));
		reg("blockGlazedTerracota", new ItemStack(Blocks.GREEN_GLAZED_TERRACOTTA, 1));
		reg("blockGlazedTerracota", new ItemStack(Blocks.RED_GLAZED_TERRACOTTA, 1));
		reg("blockGlazedTerracota", new ItemStack(Blocks.BLACK_GLAZED_TERRACOTTA, 1));
		reg("blockMossy", new ItemStack(Blocks.MOSSY_COBBLESTONE, 1));

		reg("blockDirt", new ItemStack(Blocks.DIRT, 1, 1));

		reg("blockGrass", new ItemStack(Blocks.DIRT, 1, 2));


		//reg("cobblestoneSlab", new ItemStack(BlockCobbledDeepslateSlab.block, 1));
	}
	private static void OreGems() {
        reg("gemRuby", new ItemStack(ATItems.RUBY, 1));
	}
	private static void OreDusts() {
        reg("dustRuby", new ItemStack(ATItems.RUBY_DUST, 1));
        reg("dustCement", new ItemStack(ATItems.CEMENT_DUST, 1));
        reg("dustBrass", new ItemStack(ATItems.BRASS_DUST, 1));
        reg("dustMineralSteel", new ItemStack(ATItems.MINERAL_STEEL_DUST, 1));
	}
	private static void OrePlates() {

	}
    private static void OreNuggets() {
        reg("nuggetBrass", new ItemStack(ATItems.BRASS_NUGGET, 1));
    }
    private static void OreIngots() {
        reg("ingotBrass", new ItemStack(ATItems.BRASS_INGOT, 1));
        reg("ingotMineralSteel", new ItemStack(ATItems.MINERAL_STEEL_INGOT, 1));
	}
	private static void OreOres() {
		reg("oreRuby", new ItemStack(BlockRubyOre.block, 1));
		reg("oreQuartz", new ItemStack(BlockQuartzOreStone.block, 1));
		reg("oreQuartz", new ItemStack(BlockQuartzOreGranite.block, 1));
	}
	///
	private static void OreRods() {
		reg("rodIron", new ItemStack(ATItems.IRON_STICK, 1));
        reg("rodGold", new ItemStack(ATItems.GOLD_STICK, 1));
        reg("rodCopper", new ItemStack(ATItems.COPPER_STICK, 1));
        reg("rodTin", new ItemStack(ATItems.TIN_STICK, 1));
        reg("rodBronze", new ItemStack(ATItems.BRONZE_STICK, 1));
        reg("rodDiamond", new ItemStack(ATItems.DIAMOND_STICK, 1));
        reg("rodAluminium", new ItemStack(ATItems.ALUMINIUM_STICK, 1));
        reg("rodTitanium", new ItemStack(ATItems.TITANIUM_STICK, 1));
        reg("rodNickel", new ItemStack(ATItems.NICKEL_STICK, 1));
        reg("rodCobalt", new ItemStack(ATItems.COBALT_STICK, 1));
        reg("rodMeteoricIron", new ItemStack(ATItems.METEORIC_STICK, 1));
        reg("rodElectrum", new ItemStack(ATItems.ELECTRUM_STICK, 1));
        reg("rodEmerald", new ItemStack(ATItems.EMERALD_STICK, 1));
        reg("rodRuby", new ItemStack(ATItems.RUBY_STICK, 1));
        reg("rodSteel", new ItemStack(ATItems.STEEL_STICK, 1));
        reg("rodIridium", new ItemStack(ATItems.IRIDIUM_STICK, 1));
        reg("rodSilver", new ItemStack(ATItems.SILVER_STICK, 1));
        reg("rodUranium", new ItemStack(ATItems.URANIUM_STICK, 1));
        reg("rodBrass", new ItemStack(ATItems.BRASS_STICK, 1));
        reg("rodCarbon", new ItemStack(ATItems.CARBON_STICK, 1));

	}

	private static void OreTools() {
		reg("toolSaw", new ItemStack(ItemSawIron.IRON_SAW, 1));
		reg("toolSaw", new ItemStack(ItemSawDiamond.DIAMOND_SAW, 1));
		reg("toolSaw", new ItemStack(ItemGoldenSaw.GOLDEN_SAW, 1));
		reg("toolSaw", new ItemStack(ItemCopperSaw.COPPER_SAW, 1));
		reg("toolSaw", new ItemStack(ItemBronzeSaw.BRONZE_SAW, 1));
		reg("toolSaw", new ItemStack(ItemTinSaw.TIN_SAW, 1));
		reg("toolSaw", new ItemStack(ItemSteelSaw.STEEL_SAW, 1));

		reg("toolHoe", new ItemStack(Items.DIAMOND_HOE, 1));
		reg("toolHoe", new ItemStack(Items.IRON_HOE, 1));
		reg("toolHoe", new ItemStack(Items.GOLDEN_HOE, 1));
		reg("toolHoe", new ItemStack(Items.STONE_HOE, 1));
		reg("toolHoe", new ItemStack(ItemRubyHoe.HOE, 1));
		reg("toolHoe", new ItemStack(ItemEmeraldHoe.HOE, 1));
		reg("toolHoe", new ItemStack(ItemCrystalHoe.HOE, 1));
		reg("toolHoe", new ItemStack(ItemNeutroniumHoe.HOE, 1));

		reg("toolAxe", new ItemStack(ItemRubyAxe.AXE, 1));
		reg("toolAxe", new ItemStack(ItemEmeraldAxe.AXE, 1));
		reg("toolAxe", new ItemStack(ItemCrystalAxe.AXE, 1));
		reg("toolAxe", new ItemStack(ItemNeutroniumAxe.AXE, 1));
		
		reg("toolPickaxe", new ItemStack(ItemRubyPickaxe.PICKAXE, 1));
		reg("toolPickaxe", new ItemStack(ItemEmeraldPickaxe.PICKAXE, 1));
		reg("toolPickaxe", new ItemStack(ItemCrystalPickaxe.PICKAXE, 1));
		reg("toolPickaxe", new ItemStack(ItemNeutroniumPickaxe.PICKAXE, 1));


	}
}
