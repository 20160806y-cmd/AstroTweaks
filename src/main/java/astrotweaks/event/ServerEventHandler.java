package astrotweaks.event;

import net.minecraftforge.fml.common.Mod;
import astrotweaks.ModVariables;
import net.minecraftforge.event.world.BlockEvent;



@Mod.EventBusSubscriber(modid = "astrotweaks",value=net.minecraftforge.fml.relauncher.Side.SERVER)
public class ServerEventHandler {
	@net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getState().getBlock() == astrotweaks.block.B_QmBlock.block && ModVariables.QM_is_fully_unbreakable) {
            event.setCanceled(true);
        }
    }
}
