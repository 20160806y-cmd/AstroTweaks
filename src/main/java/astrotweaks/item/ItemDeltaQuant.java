package astrotweaks.item;

import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;



public final class ItemDeltaQuant {
    public static final Item DELTA_QUANT = new Item() {
        @Override
        public void onUpdate(ItemStack itemstack, World world, Entity entity, int slot, boolean par5) {
            super.onUpdate(itemstack, world, entity, slot, par5);
            astrotweaks.procedure.P_SQInvTick.exect(entity, itemstack);
        }
    }.setCreativeTab(astrotweaks.creativetab.ATCreativeTabs.AT_INTEGRATION_CT).setRegistryName("astrotweaks", "delta_quant").setUnlocalizedName("delta_quant");

    private ItemDeltaQuant() {}
}
