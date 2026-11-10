package primevisuals;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Thin white zig-zag lightning that strikes the place where you killed someone. Client-side, drawn as lines. */
public class Bolts {
    private static final int LIFE = 14;
    private static final List<Bolt> ACTIVE = new ArrayList<>();

    private static class Bolt {
        final List<Vec3d> main = new ArrayList<>();
        final List<List<Vec3d>> branches = new ArrayList<>();
        int age;
        int rgb;
    }

    public static int colorOf(String id) {
        return switch (id) {
            case "blue" -> 0x6AA9FF;
            case "purple" -> 0xB066FF;
            case "gold" -> 0xFFD84A;
            case "red" -> 0xFF4040;
            default -> 0xFFFFFF;
        };
    }

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(Bolts::render);
    }

    public static void spawn(double x, double y, double z, String color) {
        if (color == null || "off".equals(color)) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Bolt b = new Bolt();
        b.rgb = colorOf(color);
        double h = 30 + r.nextDouble() * 8;
        double sx = x + (r.nextDouble() - 0.5) * 8;
        double sz = z + (r.nextDouble() - 0.5) * 8;
        int n = 16;
        for (int i = 0; i <= n; i++) {
            double f = i / (double) n;
            double jx = (i == 0 || i == n) ? 0 : (r.nextDouble() - 0.5) * 2.2;
            double jz = (i == 0 || i == n) ? 0 : (r.nextDouble() - 0.5) * 2.2;
            b.main.add(new Vec3d(sx + (x - sx) * f + jx, y + 0.05 + h * (1 - f), sz + (z - sz) * f + jz));
        }
        for (int k = 0; k < 3; k++) {
            int idx = 3 + r.nextInt(n - 5);
            Vec3d p = b.main.get(idx);
            double dx = (r.nextDouble() - 0.5) * 2;
            double dz = (r.nextDouble() - 0.5) * 2;
            List<Vec3d> br = new ArrayList<>();
            br.add(p);
            for (int s = 0; s < 4; s++) {
                p = p.add(dx * 1.1 + (r.nextDouble() - 0.5) * 1.2, -1.6 - r.nextDouble(), dz * 1.1 + (r.nextDouble() - 0.5) * 1.2);
                br.add(p);
            }
            b.branches.add(br);
        }
        ACTIVE.add(b);
    }

    public static void tick() {
        for (Iterator<Bolt> it = ACTIVE.iterator(); it.hasNext(); ) {
            Bolt b = it.next();
            if (++b.age > LIFE) it.remove();
        }
    }

    private static void render(WorldRenderContext ctx) {
        if (ACTIVE.isEmpty()) return;
        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        if (ms == null || vcp == null) return;
        Vec3d cam = ctx.camera().getPos();
        ms.push();
        ms.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = ms.peek().getPositionMatrix();
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getLines());
        for (Bolt b : ACTIVE) {
            float fade = b.age < 9 ? 1.0f : Math.max(0f, (LIFE - b.age) / 5.0f);
            if (b.age >= 2 && b.age <= 5 && b.age % 2 == 1) fade *= 0.55f; // flicker
            line(vc, mat, b.main, b.rgb, fade);
            for (List<Vec3d> br : b.branches) line(vc, mat, br, b.rgb, fade * 0.55f);
        }
        ms.pop();
    }

    private static void line(VertexConsumer vc, Matrix4f mat, List<Vec3d> pts, int rgb, float alpha) {
        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int bl = rgb & 255;
        int a = (int) (255 * Math.max(0f, Math.min(1f, alpha)));
        int glow = (int) (a * 0.35f);
        for (int i = 0; i + 1 < pts.size(); i++) {
            Vec3d p = pts.get(i);
            Vec3d q = pts.get(i + 1);
            Vec3d d = q.subtract(p).normalize();
            float nx = (float) d.x;
            float ny = (float) d.y;
            float nz = (float) d.z;
            // core (3 slightly offset lines = thicker) + dim outer glow
            double[][] offs = {{0, 0, 255}, {0.03, 0, 255}, {0, 0.03, 255}, {0.07, 0.07, 0}, {-0.07, -0.07, 0}};
            for (double[] o : offs) {
                int al = o[2] > 0 ? a : glow;
                vc.vertex(mat, (float) (p.x + o[0]), (float) p.y, (float) (p.z + o[1])).color(r, g, bl, al).normal(nx, ny, nz);
                vc.vertex(mat, (float) (q.x + o[0]), (float) q.y, (float) (q.z + o[1])).color(r, g, bl, al).normal(nx, ny, nz);
            }
        }
    }
}
