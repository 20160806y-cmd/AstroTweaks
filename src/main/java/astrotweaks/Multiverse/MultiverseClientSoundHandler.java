package astrotweaks.Multiverse;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;




@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(Side.CLIENT)
public final class MultiverseClientSoundHandler {

    /** Id'ы измерений, которые клиент знает как MV (уровни + global + proxy). */
    private static final Set<Integer> MV_DIMS = ConcurrentHashMap.newKeySet();

    /**
     * Окно (в тиках), в течение которого портальные звуки глушатся.
     * Ставится из {@link #noteMultiverseRegistration} на каждом MessageMultiverse;
     * с запасом покрывает login + teleport.
     */
    private static int suppressTicks = 400; // тиков

    private MultiverseClientSoundHandler() {}

    /** Вызывается из MessageMultiverse.ClientHandler.onMessage. */
    public static void noteMultiverseRegistration(int baseDimId, boolean global) {
        if (global) {
            // GLOBAL_DIM / PROXY_DIM — конкретные id, приходят как sentinel.
            // Просто доверяем серверу и не пытаемся различать их тут.
            MV_DIMS.add(baseDimId);
        } else {
            MV_DIMS.add(baseDimId);
            MV_DIMS.add(baseDimId + 1);
            MV_DIMS.add(baseDimId + 2);
            MV_DIMS.add(baseDimId + 3);
        }
        MV_DIMS.add(-1_000_000);

        suppressTicks = 400;
    }

    /** Сброс при отключении от сервера, чтобы не тащить id между сессиями. */
    public static void reset() {
        MV_DIMS.clear();
        suppressTicks = 0;
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        if (suppressTicks <= 0) return;

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc == null || mc.player == null) return;

        // Ключевая проверка: если игрок оказался в обычном мире — не трогаем.
        // (Например, чей-то мод позвал MessageMultiverse, но телепорт не состоялся.)
        if (!MV_DIMS.contains(mc.player.dimension)) return;

        String name = event.getName();
        if (name == null) return;

        if ("block.portal.trigger".equals(name) || "block.portal.travel".equals(name)) {
            event.setResultSound(null);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (suppressTicks > 0) suppressTicks--;
    }
}
