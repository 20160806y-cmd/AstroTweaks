package astrotweaks.block.black_hole.client;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;



// Minimal sphere helper — без аллокаций double[] на вершину
public final class BlackHoleRenderHelper {
    private BlackHoleRenderHelper() {}

    public static void drawSphere(double radius, int color, float alpha, int latSegments, int lonSegments) {
        if (alpha <= 0.01f) return;
        float[] rgb = unpackRGB(color);
        float r = rgb[0], g = rgb[1], b = rgb[2];
        Tessellator tes = Tessellator.getInstance();
        BufferBuilder buf = tes.getBuffer();
        buf.begin(4, DefaultVertexFormats.POSITION_COLOR);
        // Предвычисляем 2*PI / lonSegments
        final double lonStep = 2.0 * Math.PI / lonSegments;
        final double latStep = Math.PI / latSegments;
        for (int lat = 0; lat < latSegments; lat++) {
            double theta1 = lat * latStep;
            double theta2 = theta1 + latStep;
            double sinTheta1 = Math.sin(theta1);
            double cosTheta1 = Math.cos(theta1);
            double sinTheta2 = Math.sin(theta2);
            double cosTheta2 = Math.cos(theta2);
            double y1 = radius * cosTheta1;
            double y2 = radius * cosTheta2;
            double rSin1 = radius * sinTheta1;
            double rSin2 = radius * sinTheta2;
            for (int lon = 0; lon < lonSegments; lon++) {
                double phi1 = lon * lonStep;
                double phi2 = phi1 + lonStep;
                double cosPhi1 = Math.cos(phi1);
                double sinPhi1 = Math.sin(phi1);
                double cosPhi2 = Math.cos(phi2);
                double sinPhi2 = Math.sin(phi2);
                double x1 = rSin1 * cosPhi1;
                double z1 = rSin1 * sinPhi1;
                double x2 = rSin1 * cosPhi2;
                double z2 = rSin1 * sinPhi2;
                double x3 = rSin2 * cosPhi1;
                double z3 = rSin2 * sinPhi1;
                double x4 = rSin2 * cosPhi2;
                double z4 = rSin2 * sinPhi2;
                // tri 1: p1, p3, p2
                buf.pos(x1, y1, z1).color(r, g, b, alpha).endVertex();
                buf.pos(x3, y2, z3).color(r, g, b, alpha).endVertex();
                buf.pos(x2, y1, z2).color(r, g, b, alpha).endVertex();
                // tri 2: p2, p3, p4
                buf.pos(x2, y1, z2).color(r, g, b, alpha).endVertex();
                buf.pos(x3, y2, z3).color(r, g, b, alpha).endVertex();
                buf.pos(x4, y2, z4).color(r, g, b, alpha).endVertex();
            }
        }
        tes.draw();
    }

    public static float[] unpackRGB(int col) {
        return new float[] {
                ((col >> 16) & 0xFF) / 255f,
                ((col >> 8) & 0xFF) / 255f,
                (col & 0xFF) / 255f
        };
    }
}
