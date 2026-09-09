package astrotweaks.Multiverse;

import net.minecraft.block.Block;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

import astrotweaks.AstrotweaksMod;

/**
 * Registers the custom nether-portal block and its TileEntity. The block is a
 * pure server/render block (no item, like the vanilla portal), so there is no
 * ItemBlock to register.
 */
@Mod.EventBusSubscriber(modid = AstrotweaksMod.MODID)
public final class NetherPortalReg {

    private NetherPortalReg() {}

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().register(BlockNetherPortal.BLOCK);
        GameRegistry.registerTileEntity(TileNetherPortal.class, "astrotweaks:nether_portal");
    }
}