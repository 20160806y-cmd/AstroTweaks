package astrotweaks.event;

import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.Display;



public class WhenBackToMenu {

    private final String TITLE;

    public WhenBackToMenu(String windowTitle) {
        this.TITLE = windowTitle;
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (event.getGui() instanceof GuiMainMenu) {
            setTitle(TITLE);
        }
    }
    private void setTitle(String title) {
        Display.setTitle(title);
    }
}
