package cn.blockforge.fatekings.client;

import cn.blockforge.fatekings.archer.ArcherRules;
import cn.blockforge.fatekings.client.render.FxDraw;
import cn.blockforge.fatekings.client.render.PortBuffers;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** EMIYA's light on the world: Rho Aias, the lines of a trace, and the trails of Kanshou and Bakuya. */
public final class ArcherFx {
    private ArcherFx() {
    }

    /**
     * Rho Aias: seven layered flowers of pink-violet light in front of the bearer, the front ones
     * broken first. {@code rel} is the anchor's feet relative to the camera.
     */
    public static void rhoAias(PoseStack pose, PortBuffers b, Vec3 rel, Entity anchor, float age, int duration, int petals, float partial, boolean low) {
        if (!(anchor instanceof LivingEntity bearer) || petals <= 0) return;
        float open = Math.min(1.0f, age / 4.0f);
        float close = age > duration - 6 ? Math.max(0.0f, (duration - age) / 6.0f) : 1.0f;
        float vis = Math.min(open, close);
        if (vis <= 0.0f) return;
        Vec3 view = bearer.getViewVector(partial);
        Vec3 c = rel.add(0.0, bearer.getEyeHeight(), 0.0).add(view.scale(ArcherRules.RHO_AIAS_DIST));
        Vec3[] uv = FxDraw.basis(view);
        int perLayer = low ? 3 : 6;
        for (int layer = 0; layer < petals; ++layer) {
            // The rearmost layer is the last to break: layer 0 at the back.
            Vec3 lc = c.add(view.scale(0.12 * layer));
            float r = (float)(ArcherRules.RHO_AIAS_RADIUS * (0.55 + 0.08 * layer)) * (0.6f + 0.4f * vis);
            for (int i = 0; i < perLayer; ++i) {
                double a = i * Math.PI * 2 / perLayer + layer * 0.45 + age * 0.01;
                Vec3 dir = uv[0].scale(Math.cos(a)).add(uv[1].scale(Math.sin(a)));
                FxDraw.sprite(pose, b, FxDraw.RHO_PETAL, lc.add(dir.scale(r * 0.55)), view, r * 0.55f, (float)a,
                    FxDraw.argb((int)(170 * vis), 0xFFC8F0));
            }
            FxDraw.ring(pose, b, lc, view, r * 0.95f, r * 1.02f, low ? 16 : 32, FxDraw.argb((int)(200 * vis), 0xFFB0E0), FxDraw.argb(0, 0xFFB0E0));
        }
    }

    /** Structural analysis / projection: blue lines of light trace the edges of the anchor's form. */
    public static void trace(PoseStack pose, PortBuffers b, Vec3 rel, Entity anchor, float age, int duration) {
        if (anchor == null) return;
        float vis = Math.max(0.0f, 1.0f - age / Math.max(1.0f, duration));
        if (vis <= 0.0f) return;
        AABB box = anchor.getBoundingBox().inflate(0.15 + 0.1 * (1.0f - vis));
        Vec3 o = rel.subtract(anchor.position());
        double[] xs = {box.minX, box.maxX}, ys = {box.minY, box.maxY}, zs = {box.minZ, box.maxZ};
        int col = FxDraw.argb((int)(220 * vis), 0x7FF0FF);
        float w = 0.04f;
        for (double y : ys) for (double z : zs) bar(pose, b, o.add(xs[0], y, z), o.add(xs[1], y, z), w, col);
        for (double x : xs) for (double z : zs) bar(pose, b, o.add(x, ys[0], z), o.add(x, ys[1], z), w, col);
        for (double x : xs) for (double y : ys) bar(pose, b, o.add(x, y, zs[0]), o.add(x, y, zs[1]), w, col);
        // A scan line rising through the form.
        double sy = box.minY + (box.maxY - box.minY) * Math.min(1.0, age / Math.max(1.0, duration * 0.7));
        FxDraw.quad(pose, b, o.add(xs[0], sy, zs[0]), o.add(xs[1], sy, zs[0]), o.add(xs[1], sy, zs[1]), o.add(xs[0], sy, zs[1]),
            FxDraw.argb((int)(90 * vis), 0x7FF0FF));
    }

    private static void bar(PoseStack pose, PortBuffers b, Vec3 a, Vec3 z, float w, int col) {
        Vec3 d = z.subtract(a);
        Vec3[] uv = FxDraw.basis(d);
        Vec3 s = uv[0].scale(w), t = uv[1].scale(w);
        FxDraw.quad(pose, b, a.add(s), z.add(s), z.subtract(s), a.subtract(s), col);
        FxDraw.quad(pose, b, a.add(t), z.add(t), z.subtract(t), a.subtract(t), col);
    }

    /**
     * Trails of the twin blades: an arc of dark steel edged in red for Kanshou, of white edged in
     * pale blue for Bakuya, following each stroke's figure round the striker.
     */
    public static void twinTrails(PoseStack pose, PortBuffers b, ClientLevel level, Vec3 cam, float partial, boolean low) {
        for (Map.Entry<Integer, ClientSwings.Twin> en : ClientSwings.twins()) {
            if (!(level.getEntity(en.getKey()) instanceof LivingEntity e)) continue;
            ClientSwings.Twin t = en.getValue();
            Vec3 at = e.getPosition(partial).subtract(cam);
            float yaw = (float)Math.toRadians(-(e.yBodyRotO + (e.yBodyRot - e.yBodyRotO) * partial));
            Vec3 fwd = new Vec3(Math.sin(yaw), 0.0, Math.cos(yaw));
            Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x).scale(-1.0);
            Vec3 up = new Vec3(0, 1, 0);
            Vec3 c = at.add(0.0, e.getBbHeight() * 0.62, 0.0).add(fwd.scale(0.35));
            Vec3 toCam = cam.subtract(e.getPosition(partial)).subtract(0.0, e.getBbHeight() * 0.62, 0.0);
            int step = t.step();
            float p = t.progress();
            switch (step) {
                case 0 -> arc(pose, b, c, fwd, right, 1.6, 70, -70, p, toCam, true, low);
                case 1 -> arc(pose, b, c, fwd, right, 1.6, -70, 70, p, toCam, false, low);
                case 2, 6 -> {
                    double r = step == 6 ? 2.6 : 1.7;
                    Vec3 d1 = right.scale(Math.cos(Math.PI / 4)).add(up.scale(Math.sin(Math.PI / 4)));
                    Vec3 d2 = right.scale(-Math.cos(Math.PI / 4)).add(up.scale(Math.sin(Math.PI / 4)));
                    arc(pose, b, c, fwd, d1, r, 80, -80, p, toCam, true, low);
                    arc(pose, b, c, fwd, d2, r, 80, -80, p, toCam, false, low);
                }
                case 3 -> {
                    arc(pose, b, c.subtract(0.0, 0.15, 0.0), fwd, right, 2.0, 180, -180, p, toCam, true, low);
                    arc(pose, b, c.add(0.0, 0.15, 0.0), fwd.scale(-1.0), right.scale(-1.0), 2.0, 180, -180, p, toCam, false, low);
                }
                case 4 -> {
                    arc(pose, b, c.add(right.scale(0.35)), fwd, up, 1.6, 100, -60, p, toCam, true, low);
                    arc(pose, b, c.subtract(right.scale(0.35)), fwd, up, 1.6, 100, -60, p, toCam, false, low);
                }
                default -> {
                    arc(pose, b, c.add(right.scale(0.35)), fwd, up, 1.6, -60, 100, p, toCam, true, low);
                    arc(pose, b, c.subtract(right.scale(0.35)), fwd, up, 1.6, -60, 100, p, toCam, false, low);
                }
            }
        }
    }

    /**
     * An arc in the plane of {@code a0} (0 degrees) and {@code a90} (90 degrees) round {@code c}, swept
     * from {@code from} to {@code to} degrees: the trail covers the last 35% of the stroke so far.
     */
    private static void arc(PoseStack pose, PortBuffers b, Vec3 c, Vec3 a0, Vec3 a90, double r, double from, double to, float p,
                            Vec3 toCam, boolean kanshou, boolean low) {
        int segs = low ? 5 : 10;
        float tail = 0.35f;
        float head = Math.min(1.0f, p * 1.15f);
        float start = Math.max(0.0f, head - tail);
        if (head <= start) return;
        Vec3 prev = null;
        for (int i = 0; i <= segs; ++i) {
            float k = start + (head - start) * i / segs;
            double ang = Math.toRadians(from + (to - from) * k);
            Vec3 q = c.add(a0.scale(Math.cos(ang) * r)).add(a90.scale(Math.sin(ang) * r));
            if (prev != null) {
                float fade = (float)i / segs * (1.0f - Math.max(0.0f, p - 0.8f) / 0.2f);
                if (kanshou) {
                    FxDraw.texRibbon(pose, b, FxDraw.TWIN_TRAIL, prev, q, toCam.subtract(q), 0.45f, FxDraw.argb((int)(200 * fade), 0x141016), 0.0f, 1.0f);
                    FxDraw.ribbon(pose, b, prev, q, toCam.subtract(q), 0.12f, FxDraw.argb(0, 0xE0302A), FxDraw.argb((int)(160 * fade), 0xE0302A));
                } else {
                    FxDraw.ribbon(pose, b, prev, q, toCam.subtract(q), 0.42f, FxDraw.argb(0, 0xF4F8FF), FxDraw.argb((int)(210 * fade), 0xF4F8FF));
                    FxDraw.ribbon(pose, b, prev, q, toCam.subtract(q), 0.6f, FxDraw.argb(0, 0x9FC8FF), FxDraw.argb((int)(90 * fade), 0x9FC8FF));
                }
            }
            prev = q;
        }
    }
}
