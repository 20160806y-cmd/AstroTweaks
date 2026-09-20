package astrotweaks;

import net.minecraft.item.Item;
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.client.model.obj.OBJLoader;
import net.minecraftforge.common.MinecraftForge;
import astrotweaks.block.mirage.MirageBlock;
//import astrotweaks.block.mirage.MirageItemBlock;
import astrotweaks.block.mirage.MirageTEISR;
import astrotweaks.block.mirage.MirageTESR;



/**
 * Клиентский прокси: регистрация TESR/TEISR и прочая client-only логика.
 */
public class ClientProxyAstrotweaksMod implements IProxyAstrotweaksMod {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        OBJLoader.INSTANCE.addDomain("astrotweaks");

    }

    @Override
    public void init(FMLInitializationEvent event) {
        // ─── Блок-мираж: TESR (рендер в мире) ───
        ClientRegistry.bindTileEntitySpecialRenderer( astrotweaks.block.mirage.MirageTileEntity.class, new MirageTESR() );

        // ─── Блок-мираж: TEISR (рендер предмета в руке/инвентаре) ───
        // ВАЖНО 1.12.2: RenderItem для ItemBlock с ENTITYBLOCK_ANIMATED
        // вызывает именно СТАТИЧЕСКИЙ TileEntityItemStackRenderer.instance,
        // а НЕ item.setTileEntityItemStackRenderer().
        // Заменяем instance глобально, но MirageTEISR делегирует в суперкласс
        // для чужих предметов (chest/banner/skull), так что ваниль не сломается.
        TileEntityItemStackRenderer.instance = new MirageTEISR();

        // На всякий случай ставим TEISR и на предмет тоже
        // (часть модов и код Forge ищут его именно через item).
        Item mirageItem = Item.getItemFromBlock(MirageBlock.block);
        if (mirageItem != null) {
            mirageItem.setTileEntityItemStackRenderer(TileEntityItemStackRenderer.instance);
        }

        if (ModVariables.AstroTech_Environment) MinecraftForge.EVENT_BUS.register(new astrotweaks.event.WhenBackToMenu("AstroTech"));
        else if (ModVariables.CUSTOM_GAME_TITLE != null && ModVariables.CUSTOM_GAME_TITLE != "") MinecraftForge.EVENT_BUS.register(new astrotweaks.event.WhenBackToMenu(ModVariables.CUSTOM_GAME_TITLE));
        //else pass
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {}

    @Override
    public void serverLoad(FMLServerStartingEvent event) {}
}
