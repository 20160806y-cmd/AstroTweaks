package astrotweaks.block.black_hole.client;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

import java.util.HashMap;
import java.util.Map;



// Minimal sphere helper — без аллокаций double[] на вершину
public final class BlackHoleRenderHelper {
    private BlackHoleRenderHelper() {}

    // Unitsphere colors used by the BH renderer are branch-selected inline
    // in drawSphere (no per-call float[] allocation on the hot path).

    // Cached sin/cos tables per longitude-segment count (render thread only).
    // phi(i) depends on lonSegments alone, so one table serves all spheres/frames.
    private static final Map<Integer, double[]> COS_TABLE = new HashMap<>();
    private static final Map<Integer, double[]> SIN_TABLE = new HashMap<>();

    private static double[] cosTable(int lonSegments) {
        double[] t = COS_TABLE.get(lonSegments);
        if (t == null) {
            t = new double[lonSegments + 1];
            double step = 2.0 * Math.PI / lonSegments;
            for (int i = 0; i <= lonSegments; i++) t[i] = Math.cos(i * step);
            COS_TABLE.put(lonSegments, t);
        }
        return t;
    }

    private static double[] sinTable(int lonSegments) {
        double[] t = SIN_TABLE.get(lonSegments);
        if (t == null) {
            t = new double[lonSegments + 1];
            double step = 2.0 * Math.PI / lonSegments;
            for (int i = 0; i <= lonSegments; i++) t[i] = Math.sin(i * step);
            SIN_TABLE.put(lonSegments, t);
        }
        return t;
    }

    public static void drawSphere(double radius, int color, float alpha, int latSegments, int lonSegments) {
        if (alpha <= 0.01f) return;
        float r, g, b;
        if (color == 0xFFFFFF) { r = 1.0f; g = 1.0f; b = 1.0f; }
        else if (color == 0x000000) { r = 0.0f; g = 0.0f; b = 0.0f; }
        else {
            float[] rgb = unpackRGB(color);
            r = rgb[0]; g = rgb[1]; b = rgb[2];
        }
        double[] cosPhi = cosTable(lonSegments);
        double[] sinPhi = sinTable(lonSegments);
        Tessellator tes = Tessellator.getInstance();
        BufferBuilder buf = tes.getBuffer();
        buf.begin(4, DefaultVertexFormats.POSITION_COLOR);
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
                double cosPhi1 = cosPhi[lon];
                double sinPhi1 = sinPhi[lon];
                double cosPhi2 = cosPhi[lon + 1];
                double sinPhi2 = sinPhi[lon + 1];
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
