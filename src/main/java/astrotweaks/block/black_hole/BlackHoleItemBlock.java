package astrotweaks.block.black_hole;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

public class BlackHoleItemBlock extends ItemBlock {
    public BlackHoleItemBlock(Block block) {
        super(block);
        setMaxStackSize(1);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        double mass = BlackHoleUtils.DEFAULT_MASS;
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey(BlackHoleTileEntity.TAG_MASS)) {
            mass = stack.getTagCompound().getDouble(BlackHoleTileEntity.TAG_MASS);
        }
        tooltip.add(TextFormatting.DARK_GRAY + "Mass: " + String.format("%.1f", mass));
        tooltip.add(TextFormatting.GRAY + "Horizon: " + String.format("%.2f", BlackHoleUtils.getHorizonRadius(mass)) + " | Gravity: " + String.format("%.1f", BlackHoleUtils.getGravityRange(mass)));
        tooltip.add(TextFormatting.DARK_PURPLE + "Sneak+RClick on block for debug");
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey(BlackHoleTileEntity.TAG_MASS)) {
            double m = stack.getTagCompound().getDouble(BlackHoleTileEntity.TAG_MASS);
            return super.getItemStackDisplayName(stack) + " [" + (int)m + "]";
        }
        return super.getItemStackDisplayName(stack);
    }
}
