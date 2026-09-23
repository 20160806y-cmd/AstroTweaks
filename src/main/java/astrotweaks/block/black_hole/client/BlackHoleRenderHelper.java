package astrotweaks.block.black_hole.client;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;



// Minimal sphere helper ported from AE2's RenderHelper (GL POSITION+COLOR)
public final class BlackHoleRenderHelper {
    private BlackHoleRenderHelper() {}

    public static void drawSphere(double radius, int color, float alpha, int latSegments, int lonSegments) {
        if (alpha <= 0.01f) return;
        float[] rgb = unpackRGB(color);
        Tessellator tes = Tessellator.getInstance();
        BufferBuilder buf = tes.getBuffer();
        buf.begin(4, DefaultVertexFormats.POSITION_COLOR);
        for (int lat = 0; lat < latSegments; lat++) {
            double theta1 = Math.PI * lat / latSegments;
            double theta2 = Math.PI * (lat + 1) / latSegments;
            for (int lon = 0; lon < lonSegments; lon++) {
                double phi1 = 2 * Math.PI * lon / lonSegments;
                double phi2 = 2 * Math.PI * (lon + 1) / lonSegments;
                double[] p1 = sphereVertex(radius, theta1, phi1);
                double[] p2 = sphereVertex(radius, theta1, phi2);
                double[] p3 = sphereVertex(radius, theta2, phi1);
                double[] p4 = sphereVertex(radius, theta2, phi2);
                addTri(buf, p1, p3, p2, rgb, alpha);
                addTri(buf, p2, p3, p4, rgb, alpha);
            }
        }
        tes.draw();
    }

    private static double[] sphereVertex(double r, double theta, double phi) {
        return new double[] {
                r * Math.sin(theta) * Math.cos(phi),
                r * Math.cos(theta),
                r * Math.sin(theta) * Math.sin(phi)
        };
    }

    private static void addTri(BufferBuilder buf, double[] a, double[] b, double[] c, float[] rgb, float alpha) {
        buf.pos(a[0], a[1], a[2]).color(rgb[0], rgb[1], rgb[2], alpha).endVertex();
        buf.pos(b[0], b[1], b[2]).color(rgb[0], rgb[1], rgb[2], alpha).endVertex();
        buf.pos(c[0], c[1], c[2]).color(rgb[0], rgb[1], rgb[2], alpha).endVertex();
    }

    public static float[] unpackRGB(int col) {
        return new float[] {
                ((col >> 16) & 0xFF) / 255f,
                ((col >> 8) & 0xFF) / 255f,
                (col & 0xFF) / 255f
        };
    }
}
