package astrotweaks.block.black_hole.client;

import astrotweaks.block.black_hole.BlackHoleTileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Рендерит чёрные дыры на любой дистанции прорисовки, обходя отсечение
 * TileEntity по дистанции трансляции (64 блока) и frustum-culling.
 * isGlobalRenderer=true не спасает от отсечения по maxRenderDistance,
 * поэтому рендерим через RenderWorldLastEvent напрямую относительно камеры.
 */
@Mod.EventBusSubscriber(modid = "astrotweaks", value = Side.CLIENT)
public final class BlackHoleWorldRenderer {

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        World w = mc.world; if (w == null) return;
        java.util.Set<BlackHoleTileEntity> active = BlackHoleTileEntity.getActiveHoles();
        if (active.isEmpty()) return;
        Entity view = mc.getRenderViewEntity(); if (view == null) return;
        double px = view.lastTickPosX + (view.posX - view.lastTickPosX) * e.getPartialTicks();
        double py = view.lastTickPosY + (view.posY - view.lastTickPosY) * e.getPartialTicks();
        double pz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * e.getPartialTicks();

        // Квадрат дистанции прогруженных чанков для этого игрока (renderDistance в чанках)
        int rd = mc.gameSettings.renderDistanceChunks;
        // +2 чанка запаса, чтобы не резать на границе прогрузки
        double loadedRange = (rd + 2) * 16.0;
        double loadedRangeSq = loadedRange * loadedRange;

        for (BlackHoleTileEntity bh : new java.util.ArrayList<>(active)) {
            if (bh.isInvalid() || bh.getWorld() != w) continue;
            BlockPos p = bh.getPos();
            // Только в прогруженных чанках для этого игрока
            if (!w.isBlockLoaded(p)) continue;
            // Доп. проверка по дистанции прогрузки — isBlockLoaded может держать чанк чуть дольше
            double dx = (p.getX() + 0.5) - px;
            double dy = (p.getY() + 0.5) - py;
            double dz = (p.getZ() + 0.5) - pz;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > loadedRangeSq) continue;

            // Рендерим на любой дистанции прогрузки единым путём (без фантомного хенд-оффа 64)
            double x = p.getX() - px;
            double y = p.getY() - py;
            double z = p.getZ() - pz;
            BlackHoleTESR.renderStatic(bh, x, y, z, e.getPartialTicks());
        }
    }
}
