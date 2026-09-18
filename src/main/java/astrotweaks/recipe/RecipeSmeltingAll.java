package astrotweaks.recipe;

import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;

import astrotweaks.item.*;
import astrotweaks.block.*;

import javax.annotation.Nonnull;

import astrotweaks.ModVariables;



public class RecipeSmeltingAll {
	public RecipeSmeltingAll() {}

	private static final void reg(@Nonnull ItemStack input, @Nonnull ItemStack output, float xp) {
		GameRegistry.addSmelting(input, output, xp);
	}


	
	public static void init() {
		reg(new ItemStack(BlockRubyOre.block, 1), new ItemStack(ATItems.RUBY, 1), 1F);
		reg(new ItemStack(ATItems.CORDAGE_VINE, 1), new ItemStack(ATItems.CORDAGE_FIBER, 1), 0.1F);
		reg(new ItemStack(ATBlocks.COMPRESSED_SAND, 1), new ItemStack(Blocks.QUARTZ_BLOCK, 1, 0), 0.5F);

		reg(new ItemStack(Items.FISH, 1, 2), new ItemStack(ItemCoockedTropicalFish.TROPICAL_FISH, 1), 0.6F);
		reg(new ItemStack(Items.FISH, 1, 3), new ItemStack(ItemCoockedPufferfish.PUFFERFISH, 1), 1.5F);

		reg(new ItemStack(BlockQuartzOreStone.block, 1), new ItemStack(Items.QUARTZ, 1), 1F);
		reg(new ItemStack(BlockQuartzOreGranite.block, 1), new ItemStack(Items.QUARTZ, 1), 1F);
		
		reg(new ItemStack(ATItems.BRASS_DUST, 1), new ItemStack(ATItems.BRASS_INGOT, 1), 0F);

		reg(new ItemStack(ATItems.CLAY_BRICK, 1), new ItemStack(Items.BRICK, 1), 0.5F);
		reg(new ItemStack(ATBlocks.COBBLED_DEEPSLATE, 1), new ItemStack(ATBlocks.DEEPSLATE, 1), 0.5F);
		reg(new ItemStack(ATBlocks.DEEPSLATE, 1), new ItemStack(ATBlocks.DEEPSLATE_TILES, 1), 0.5F);

		reg(new ItemStack(ItemSawIron.IRON_SAW, 1), new ItemStack(Items.IRON_NUGGET, 1), 0.1F);
		reg(new ItemStack(ItemGoldenSaw.GOLDEN_SAW, 1), new ItemStack(Items.GOLD_NUGGET, 1), 0.1F);

		// mossy to simple
		reg(new ItemStack(Blocks.MOSSY_COBBLESTONE, 1), new ItemStack(Blocks.COBBLESTONE, 1), 0F);
		reg(new ItemStack(Blocks.STONEBRICK, 1, 1), new ItemStack(Blocks.STONEBRICK, 1,0), 0F);
		reg(new ItemStack(Blocks.COBBLESTONE_WALL, 1,1), new ItemStack(Blocks.COBBLESTONE_WALL, 1,0), 0F);
		reg(new ItemStack(B_MossyCobblestoneStairs.block, 1), new ItemStack(Blocks.STONE_STAIRS, 1), 0F);
		reg(new ItemStack(B_MossyCobblestoneSlab.block, 1), new ItemStack(Blocks.STONE_SLAB, 1,3), 0F);
		reg(new ItemStack(B_MossyStonebrickStairs.block, 1), new ItemStack(Blocks.STONE_BRICK_STAIRS, 1), 0F);
		reg(new ItemStack(B_MossyStonebrickSlab.block, 1), new ItemStack(Blocks.STONE_SLAB, 1, 5), 0F);

		reg(new ItemStack(ATBlocks.MOSSY_STONE, 1), new ItemStack(Blocks.STONE, 1,0), 0F);
		reg(new ItemStack(ATBlocks.MOSSY_CRACKED_STONEBRICK, 1), new ItemStack(Blocks.STONEBRICK, 1,2), 0F);
		reg(new ItemStack(ATBlocks.MOSSY_CARVED_STONEBRICK, 1), new ItemStack(Blocks.STONEBRICK, 1,3), 0F);



		if (ModVariables.Better_Smelting) {
			reg(new ItemStack(Items.SHEARS, 1), new ItemStack(Items.IRON_NUGGET, 1), 0.1F);
			reg(new ItemStack(Items.BUCKET, 1), new ItemStack(Items.IRON_NUGGET, 1), 0.1F);
			reg(new ItemStack(Item.getItemFromBlock(Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE), 1), new ItemStack(Items.IRON_NUGGET, 1), 0.1F);
			reg(new ItemStack(Items.COMPASS, 1), new ItemStack(Items.IRON_NUGGET, 1), 0.1F);
			reg(new ItemStack(Items.IRON_HORSE_ARMOR, 1), new ItemStack(Items.IRON_NUGGET, 1), 0.1F);
			reg(new ItemStack(Items.MINECART, 1), new ItemStack(Items.IRON_INGOT, 1), 0.1F);
			reg(new ItemStack(Item.getItemFromBlock(Blocks.IRON_BARS), 1), new ItemStack(Items.IRON_NUGGET, 1), 0.1F);
			reg(new ItemStack(Items.CAULDRON, 1), new ItemStack(Items.IRON_NUGGET, (int) (3)), 0.1F);
			reg(new ItemStack(Item.getItemFromBlock(Blocks.HOPPER), 1), new ItemStack(Items.IRON_NUGGET, (int) (3)), 0.1F);
			reg(new ItemStack(Items.IRON_DOOR, 1), new ItemStack(Items.IRON_INGOT, 1), 0.1F);
			reg(new ItemStack(Item.getItemFromBlock(Blocks.IRON_TRAPDOOR), 1), new ItemStack(Items.IRON_INGOT, 1), 0.1F);
			reg(new ItemStack(Item.getItemFromBlock(Blocks.RAIL), 1), new ItemStack(Items.IRON_NUGGET, 1), 0.1F);
			reg(new ItemStack(Item.getItemFromBlock(Blocks.DETECTOR_RAIL), 1), new ItemStack(Items.IRON_NUGGET, 1), 0.1F);
			reg(new ItemStack(Item.getItemFromBlock(Blocks.ACTIVATOR_RAIL), 1), new ItemStack(Items.IRON_NUGGET, 1), 0.1F);
			reg(new ItemStack(Item.getItemFromBlock(Blocks.TRIPWIRE_HOOK), 1), new ItemStack(Items.IRON_NUGGET, 1), 0.1F);

			reg(new ItemStack(Item.getItemFromBlock(Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE), 1), new ItemStack(Items.GOLD_NUGGET, 1), 0.1F);
			reg(new ItemStack(Item.getItemFromBlock(Blocks.GOLDEN_RAIL), 1), new ItemStack(Items.GOLD_NUGGET, 1), 0.1F);
			reg(new ItemStack(Items.GOLDEN_HORSE_ARMOR, 1), new ItemStack(Items.GOLD_NUGGET, 1), 0.1F);
			reg(new ItemStack(Items.CLOCK, 1), new ItemStack(Items.GOLD_NUGGET, 1), 0.1F);

			reg(new ItemStack(ItemGavel.GAVEL, 1), new ItemStack(Items.IRON_NUGGET, 1), 0.1F);


			reg(new ItemStack(Blocks.MONSTER_EGG, 1,0), new ItemStack(Blocks.STONE, 1,0), 0F);
			reg(new ItemStack(Blocks.MONSTER_EGG, 1,1), new ItemStack(Blocks.COBBLESTONE, 1), 0F);
			reg(new ItemStack(Blocks.MONSTER_EGG, 1,2), new ItemStack(Blocks.STONEBRICK, 1,0), 0F);
			reg(new ItemStack(Blocks.MONSTER_EGG, 1,3), new ItemStack(Blocks.STONEBRICK, 1,0), 0F);
			reg(new ItemStack(Blocks.MONSTER_EGG, 1,4), new ItemStack(Blocks.STONEBRICK, 1,2), 0F);
			reg(new ItemStack(Blocks.MONSTER_EGG, 1,5), new ItemStack(Blocks.STONEBRICK, 1,3), 0F);
		}


		if (ModVariables.Money_Can_Smelt) {
			 ItemStack output = ItemStack.EMPTY;
            if (!OreDictionary.getOres("nuggetCopper").isEmpty()) {
                ItemStack copperNuggetStack = OreDictionary.getOres("nuggetCopper").get(0);
                output = new ItemStack(copperNuggetStack.getItem(), 5, copperNuggetStack.getMetadata());
            }
            if (!output.isEmpty()) {
                reg(new ItemStack(ATItems.COPPER_COIN, 1), output, 0F);
            }
		}
	}
}
