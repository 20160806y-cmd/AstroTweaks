package astrotweaks.block.black_hole;

import astrotweaks.AstrotweaksMod;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;



@Mod.EventBusSubscriber(modid = AstrotweaksMod.MODID)
public class BlackHoleRegistry {

    @SubscribeEvent
    public static void onRegisterBlocks(RegistryEvent.Register<Block> e) {
        e.getRegistry().register(BlackHoleBlock.INSTANCE);
        GameRegistry.registerTileEntity(BlackHoleTileEntity.class, new ResourceLocation("astrotweaks", "te_black_hole"));
    }
    @SubscribeEvent
    public static void onRegisterItems(RegistryEvent.Register<Item> e) {
        e.getRegistry().register(new BlackHoleItemBlock(BlackHoleBlock.INSTANCE).setRegistryName(BlackHoleBlock.INSTANCE.getRegistryName()));
    }

    @Mod.EventBusSubscriber(modid = AstrotweaksMod.MODID, value = Side.CLIENT)
    public static class Client {
        @SubscribeEvent
        public static void onModelRegister(ModelRegistryEvent e) {
            Item item = Item.getItemFromBlock(BlackHoleBlock.INSTANCE);
            ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(BlackHoleBlock.INSTANCE.getRegistryName(), "inventory"));
        }
    }
}
