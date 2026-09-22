package astrotweaks.block.black_hole.client;

import astrotweaks.block.black_hole.BlackHoleTileEntity;
import astrotweaks.block.black_hole.BlackHoleUtils;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import org.lwjgl.opengl.GL11;

public class BlackHoleTESR extends TileEntitySpecialRenderer<BlackHoleTileEntity> {

    private static BlackHoleShader shader;
    private static boolean shaderAttempted = false;

    private static BlackHoleShader getShader() {
        if (!shaderAttempted) {
            shaderAttempted = true;
            shader = BlackHoleShader.loadOrCreate();
        }
        return shader;
    }

    @Override
    public void render(BlackHoleTileEntity te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        if (te == null || te.getWorld() == null) return;
        double mass = te.getMass();
        double horizon = BlackHoleUtils.getHorizonRadius(mass);
        double gravRange = BlackHoleUtils.getGravityRange(mass);

        // Frustum culling already handled via getRenderBoundingBox ; but distance check
        // Avoid rendering tiny horizon from far away if desired? Keep always.

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5);

        // State setup: minimal, let shader handle depth correctly (TESR must not break GUI)
        GlStateManager.pushAttrib();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableCull();
        GlStateManager.disableLighting();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.color(1, 1, 1, 1);

        BlackHoleShader sh = getShader();
        long worldTime = te.getWorld() != null ? te.getWorld().getTotalWorldTime() : 0;
        float time = (worldTime + partialTicks) * 0.05f;

        // --- Inner black horizon sphere (uMode=0 -> opaque black) ---
        if (sh != null) {
            sh.use();
            sh.setFloat("uTime", time);
            sh.setFloat("uHorizon", (float) horizon);
            sh.setFloat("uGravityRange", (float) gravRange);
            sh.setFloat("uMass", (float) mass);
            sh.setFloat("uMode", 0.0f);
        }

        if (sh != null) {
            BlackHoleRenderHelper.drawSphere(horizon, 0xFFFFFF, 1.0f, 32, 32);
        } else {
            BlackHoleRenderHelper.drawSphere(horizon, 0x000000, 1.0f, 32, 32);
        }

        // --- First halo (uMode=1) + second halo (uMode=2) ---
        // thickness depends on horizon: 1->0.25, 10->1.0
        double thickness = BlackHoleUtils.getHaloThickness(horizon);
        double halo1 = horizon + thickness;
        double halo2 = halo1 + thickness;
        GlStateManager.depthMask(false);
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        if (sh != null) {
            sh.setFloat("uMode", 1.0f);
            BlackHoleRenderHelper.drawSphere(halo1, 0xFFFFFF, 1.0f, 32, 32);
            sh.setFloat("uMode", 2.0f);
            BlackHoleRenderHelper.drawSphere(halo2, 0xFFFFFF, 1.0f, 32, 32);
            BlackHoleShader.stop();
        } else {
            BlackHoleRenderHelper.drawSphere(halo1, 0x000000, 0.32f, 16, 16);
            BlackHoleRenderHelper.drawSphere(halo2, 0x000000, 0.175f, 16, 16);
        }

        // Restore state via popAttrib (covers blend/texture/depth) + manual
        GlStateManager.depthMask(true);
        GlStateManager.popAttrib();
        GlStateManager.enableTexture2D();
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();

        GlStateManager.popMatrix();
    }

    @Override
    public boolean isGlobalRenderer(BlackHoleTileEntity te) {
        return true; // allow rendering outside chunk frustum culling via bounding box
    }
}
