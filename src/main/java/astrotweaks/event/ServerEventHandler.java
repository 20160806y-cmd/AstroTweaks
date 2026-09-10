package astrotweaks.event;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.world.BlockEvent;



@Mod.EventBusSubscriber(modid = "astrotweaks",value=net.minecraftforge.fml.relauncher.Side.SERVER)
public class ServerEventHandler {
	@SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getState().getBlock() == astrotweaks.block.BlockQmBlock.block) {
            event.setCanceled(true);
        }
    }
}
