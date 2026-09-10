package astrotweaks.recipe;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;



public final class GavelRecipe {
    public final Block inputBlock;
    public final int inputMeta; // -1 = sny meta
    public final ItemStack[] outputs; // array ItemStack (each element is copied upon spawn)
    public final float[] chances; // same size as outputs, values?0..1

    public GavelRecipe(Block inputBlock, int inputMeta, ItemStack[] outputs, float[] chances) {
        this.inputBlock = inputBlock;
        this.inputMeta = inputMeta;
        this.outputs = outputs;
        this.chances = chances;
    }

    public boolean matches(net.minecraft.block.state.IBlockState state) {
        if (state == null) return false;
        Block b = state.getBlock();
        if (b != inputBlock) return false;
        if (inputMeta == -1) return true;
        int meta = b.getMetaFromState(state);

        return meta == inputMeta;
    }
    // Convenient factory method for storing meta-items as ItemStack[] and chances in percent (or 0..1)
    public static GavelRecipe of(Block inBlock, int inMeta, ItemStack[] outputs, float[] chances) {
        return new GavelRecipe(inBlock, inMeta, outputs, chances);
    }
}
