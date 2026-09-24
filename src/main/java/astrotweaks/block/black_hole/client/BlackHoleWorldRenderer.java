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

    /** Квадрат дистанции, где TESR ещё отрабатывает (64 блока). Ближе — не дублируем. */
    private static final double TESR_RANGE_SQ = 64 * 64;

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

        for (BlackHoleTileEntity bh : new java.util.ArrayList<>(active)) {
            if (bh.isInvalid() || bh.getWorld() != w) continue;
            BlockPos p = bh.getPos();
            // Рендерим только в прогруженных чанках для этого игрока (иначе BH висит на границе экрана после выгрузки)
            if (!w.isBlockLoaded(p)) continue;

            // Дедупликация: TESR уже отрисует дыры в радиусе 64 блоков.
            double dx = (p.getX() + 0.5) - px;
            double dy = (p.getY() + 0.5) - py;
            double dz = (p.getZ() + 0.5) - pz;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < TESR_RANGE_SQ) continue;

            double x = p.getX() - px;
            double y = p.getY() - py;
            double z = p.getZ() - pz;
            BlackHoleTESR.renderStatic(bh, x, y, z, e.getPartialTicks());
        }
    }
}
