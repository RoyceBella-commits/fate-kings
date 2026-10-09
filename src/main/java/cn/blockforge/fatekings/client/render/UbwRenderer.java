package cn.blockforge.fatekings.client.render;

import cn.blockforge.fatekings.archer.ArcherRules;
import cn.blockforge.fatekings.client.FateClient;
import cn.blockforge.fatekings.entity.UbwEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Random;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Unlimited Blade Works, drawn as a world of its own, as the domains of the Gojo x Sukuna mod are.
 * Inside: a plain of scorched earth (the real ground is not drawn while the marble is open, see
 * {@code UbwClient}) with swords planted across it as far as the eye can see, a haze where it meets
 * the sky, an amber sky of drifting cloud with vast bronze gears turning in it, and a great sun hanging
 * in the west. From outside: a burning amber sphere whose rim glows brighter. The ring of fire that
 * unfolds it runs out across the ground first. Where it meets another domain the world splits along
 * the seam ({@link DomainSeam}), each keeping its side, and the seam burns white-hot on the marble's side.
 */
public class UbwRenderer extends FxEntityRenderer<UbwEntity> {
    /** The ground lies just above the marble's foot (and just above another domain's floor in a clash). */
    private static final float GROUND = 0.03f;
    private static final int MIST = 0xC8743A;

    public UbwRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(UbwEntity entity, Frustum frustum, double x, double y, double z, float partial) {
        return entity.distanceToSqr(x, y, z) < 256.0 * 256.0;
    }

    @Override
    protected void build(UbwEntity e, State state, float partial, PoseStack pose, PortBuffers b, Vec3 toCamera) {
        boolean low = FateClient.prefs().lowFx();
        float open = e.openness(partial);
        if (open <= 0.0f) return;
        float R = e.radius();
        float spread = Math.min(1.0f, (e.life() + partial) / ArcherRules.UBW_UNFOLD);
        float r = R * spread;
        float t = e.tickCount + partial;
        DomainSeam.Meeting meeting = DomainSeam.meetingOf(e, partial);
        DomainSeam.Clip clip = meeting == null ? null : meeting.clip();
        float gy = GROUND;
        if (meeting != null && meeting.rival().floor()) {
            // Just above the other domain's water, so its floor never paints over this half's ground.
            gy = Math.max(GROUND, (float)(meeting.rival().centre().y - e.getY()) + 0.06f);
        }
        // Inside the sphere, even across a seam, the marble's half is the world beyond it (as the other domain's half is).
        boolean inside = toCamera.lengthSqr() < (double)r * r;
        int a = (int)(255 * open);
        if (inside) {
            int lat = clip != null ? (low ? 16 : 28) : low ? 10 : 14;
            int lon = clip != null ? (low ? 32 : 56) : low ? 20 : 28;
            // Sky and ground are opaque: drawn before every translucent layer, they never cover what stands in front of them.
            FxDraw.texSphere(pose, b, FxDraw.opaque(FxDraw.UBW_SKY), Vec3.ZERO, r, lat, lon, FxDraw.argb(255, 0xFFFFFF), clip);
            sun(e, pose, b, r, open, toCamera, clip);
            gears(e, pose, b, r, spread, open, t, clip, low);
            ground(pose, b, r, gy, open, clip, low);
            mist(pose, b, r, gy, open, clip, low);
            plantedSwords(e, state, r, gy, open, low, clip);
        } else {
            outside(pose, b, r, open, toCamera, clip, low);
        }
        if (clip != null) seamGlow(pose, b, clip, r, gy, toCamera, inside, low);
        // The ring of fire running out over the ground.
        if (spread < 1.0f || e.life() < ArcherRules.UBW_UNFOLD + 6) {
            float f = spread < 1.0f ? 1.0f : Math.max(0.0f, 1.0f - (e.life() + partial - ArcherRules.UBW_UNFOLD) / 6.0f);
            fireRing(pose, b, gy + 0.15, Math.max(0.0f, r - 2.0f), r + 0.6f, low ? 32 : 72, FxDraw.argb((int)(220 * f), 0xFF7A2A), FxDraw.argb(0, 0xFFC060), clip);
            fireRing(pose, b, gy + 1.0, Math.max(0.0f, r - 1.0f), r + 0.3f, low ? 24 : 56, FxDraw.argb((int)(120 * f), 0xFFB040), FxDraw.argb(0, 0xFFB040), clip);
        }
    }

    // ---- Inside ----

    /** The scorched plain: a tile of charred earth every 6 blocks, fading into the haze towards the wall. */
    private static void ground(PoseStack pose, PortBuffers b, float r, float gy, float open, DomainSeam.Clip clip, boolean low) {
        float tile = low ? 8.0f : 6.0f;
        int n = (int)Math.ceil(r / tile);
        for (int i = -n; i < n; ++i) {
            for (int j = -n; j < n; ++j) {
                double x0 = i * tile, z0 = j * tile, x1 = x0 + tile, z1 = z0 + tile;
                double dc = Math.hypot(x0 + tile * 0.5, z0 + tile * 0.5);
                if (dc > r) continue;
                Vec3 p0 = new Vec3(x0, gy, z0), p1 = new Vec3(x1, gy, z0), p2 = new Vec3(x1, gy, z1), p3 = new Vec3(x0, gy, z1);
                if (clip != null) {
                    if (!clip.keeps(p0) && !clip.keeps(p1) && !clip.keeps(p2) && !clip.keeps(p3)) continue;
                    p0 = clip.onto(p0);
                    p1 = clip.onto(p1);
                    p2 = clip.onto(p2);
                    p3 = clip.onto(p3);
                }
                float fade = smooth((float)((dc - r * 0.5) / (r * 0.45)));
                int c = lerp(0xF0D8C8, MIST, fade * 0.8f);
                FxDraw.texQuad(pose, b, FxDraw.opaque(FxDraw.UBW_GROUND), p0, p1, p2, p3, FxDraw.argb(255, c), 0.0f, 0.0f, 1.0f, 1.0f);
            }
        }
    }

    /** The haze where the plain meets the sky, all round. */
    private static void mist(PoseStack pose, PortBuffers b, float r, float gy, float open, DomainSeam.Clip clip, boolean low) {
        int segs = low ? 32 : 64;
        float rm = r * 0.93f;
        float h = r * 0.22f;
        int c = FxDraw.argb((int)(235 * open), 0xFFFFFF);
        for (int k = 0; k < segs; ++k) {
            double a0 = k * Math.PI * 2.0 / segs, a1 = (k + 1) * Math.PI * 2.0 / segs;
            Vec3 b0 = new Vec3(Math.cos(a0) * rm, gy - 0.5, Math.sin(a0) * rm), b1 = new Vec3(Math.cos(a1) * rm, gy - 0.5, Math.sin(a1) * rm);
            if (clip != null && !clip.keeps(b0) && !clip.keeps(b1)) continue;
            FxDraw.texQuad(pose, b, FxDraw.light(FxDraw.UBW_MIST), b0.add(0.0, h, 0.0), b1.add(0.0, h, 0.0), b1, b0, c, 0.0f, 0.0f, 1.0f, 1.0f);
        }
    }

    /**
     * The sun: a great disc high in the west of the marble's sky (its bearing follows from the
     * marble's id), a close halo and a wide glow of light round it.
     */
    private static void sun(UbwEntity e, PoseStack pose, PortBuffers b, float r, float open, Vec3 toCamera, DomainSeam.Clip clip) {
        if (open < 0.15f) return;
        Vec3 dir = sunDirection(e);
        float far = r * 0.88f;
        Vec3 at = dir.scale(far);
        if (clip != null && !clip.keeps(at)) return;
        Vec3 face = toCamera.subtract(at);
        Vec3 n = face.lengthSqr() < 1.0E-6 ? dir.scale(-1.0) : face.normalize();
        float disc = (float)Math.tan(Math.toRadians(5.0)) * far;
        int a = (int)(255 * open);
        // Unlit light: a sun facing the camera is not shaded like the side of an entity.
        FxDraw.upright(pose, b, FxDraw.light(FxDraw.UBW_SUN_GLOW), at.add(n.scale(0.2)), n, disc * 9.0f, disc * 8.0f, FxDraw.argb((int)(a * 0.45), 0xFFFFFF), 0, 0, 1, 1);
        FxDraw.upright(pose, b, FxDraw.light(FxDraw.UBW_SUN_GLOW), at.add(n.scale(0.4)), n, disc * 3.4f, disc * 3.4f, FxDraw.argb((int)(a * 0.85), 0xFFFFFF), 0, 0, 1, 1);
        FxDraw.upright(pose, b, FxDraw.light(FxDraw.UBW_SUN), at.add(n.scale(0.6)), n, disc / 0.46f, disc / 0.46f, FxDraw.argb(a, 0xFFFFFF), 0, 0, 1, 1);
        // Added light over it: a white-gold core and a bloom spilling out round the disc, as a bright sun dazzles.
        FxDraw.ring(pose, b, at.add(n.scale(0.8)), n, 0.0f, disc * 0.85f, 32, FxDraw.argb((int)(a * 0.55), 0xFFF2D8), FxDraw.argb(0, 0xFFC880));
        FxDraw.ring(pose, b, at.add(n.scale(0.7)), n, disc * 0.8f, disc * 2.6f, 32, FxDraw.argb((int)(a * 0.35), 0xFFB060), FxDraw.argb(0, 0xFF7A30));
    }

    /** Which way the marble's sun hangs from its centre (unit): 26 degrees up, its bearing from the marble's id. */
    public static Vec3 sunDirection(UbwEntity e) {
        Random rnd = new Random(e.getUUID().getLeastSignificantBits() ^ 0x5EEDL);
        double az = rnd.nextDouble() * Math.PI * 2.0;
        double el = Math.toRadians(26.0);
        return new Vec3(Math.cos(el) * Math.cos(az), Math.sin(el), Math.cos(el) * Math.sin(az));
    }

    /** The gears turning overhead. */
    private static void gears(UbwEntity e, PoseStack pose, PortBuffers b, float r, float spread, float open, float t, DomainSeam.Clip clip, boolean low) {
        int gears = low ? 2 : 5;
        Random rnd = new Random(e.getUUID().getMostSignificantBits());
        float big = r / 34.0f;
        for (int i = 0; i < gears; ++i) {
            double az = rnd.nextDouble() * Math.PI * 2.0;
            double el = Math.toRadians(42.0 + rnd.nextDouble() * 28.0);
            float size = (6.0f + rnd.nextFloat() * 4.0f) * big;
            float speed = (rnd.nextBoolean() ? 1.0f : -1.0f) * (0.004f + rnd.nextFloat() * 0.006f);
            Vec3 dir = new Vec3(Math.cos(el) * Math.cos(az), Math.sin(el), Math.cos(el) * Math.sin(az));
            Vec3 at = dir.scale(r * 0.82);
            if (clip != null && !clip.keeps(at)) continue;
            FxDraw.sprite(pose, b, FxDraw.light(FxDraw.UBW_GEAR), at, dir, size * spread, t * speed * 6.0f, FxDraw.argb((int)(235 * open), 0xA8743E));
        }
    }

    /** Swords planted across the plain; their places follow from the marble's id. */
    private void plantedSwords(UbwEntity e, State state, float r, float gy, float open, boolean low, DomainSeam.Clip clip) {
        if (open < 0.2f) return;
        int n = low ? 80 : 220;
        Random rnd = new Random(e.getUUID().getLeastSignificantBits());
        for (int i = 0; i < n; ++i) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            double d = Math.sqrt(rnd.nextDouble()) * e.radius() * 0.9;
            float tilt = (float)Math.toRadians(5.0 + rnd.nextDouble() * 22.0);
            float yaw = (float)(rnd.nextDouble() * Math.PI * 2.0);
            int slot = rnd.nextInt(UbwEntity.DISPLAY_SLOTS);
            float scale = 1.9f + rnd.nextFloat() * 1.0f;
            if (d > r) continue;
            Vec3 offset = new Vec3(Math.cos(a) * d, gy + 0.35 * scale, Math.sin(a) * d);
            if (clip != null && !clip.keeps(offset)) continue;
            // Blade down into the ground, leaning a little, turned at random.
            Quaternionf rot = new Quaternionf().rotateY(yaw).rotateX(tilt).mul(new Quaternionf().rotationTo(BLADE, new Vector3f(0.0f, -1.0f, 0.0f)));
            addItem(e, state, e.display(slot), rot, offset, scale);
        }
    }

    // ---- Outside ----

    /** Seen from outside: a burning amber sphere, its rim brighter (the far side of the seam left to the other domain). */
    private static void outside(PoseStack pose, PortBuffers b, float r, float open, Vec3 toCamera, DomainSeam.Clip clip, boolean low) {
        int lat = clip != null ? (low ? 16 : 24) : low ? 10 : 16;
        int lon = clip != null ? (low ? 32 : 48) : low ? 20 : 32;
        for (int i = 0; i < lat; ++i) {
            double t0 = Math.PI * i / lat, t1 = Math.PI * (i + 1) / lat;
            for (int j = 0; j < lon; ++j) {
                double p0 = Math.PI * 2 * j / lon, p1 = Math.PI * 2 * (j + 1) / lon;
                Vec3 q0 = FxDraw.sp(Vec3.ZERO, r, t0, p0), q1 = FxDraw.sp(Vec3.ZERO, r, t0, p1), q2 = FxDraw.sp(Vec3.ZERO, r, t1, p1), q3 = FxDraw.sp(Vec3.ZERO, r, t1, p0);
                if (clip != null) {
                    if (!clip.keeps(q0) && !clip.keeps(q1) && !clip.keeps(q2) && !clip.keeps(q3)) continue;
                    q0 = clip.onto(q0);
                    q1 = clip.onto(q1);
                    q2 = clip.onto(q2);
                    q3 = clip.onto(q3);
                }
                Vec3 mid = q0.add(q1).add(q2).add(q3).scale(0.25);
                Vec3 view = mid.subtract(toCamera);
                double rim = view.lengthSqr() < 1.0E-6 ? 0.0 : 1.0 - Math.abs(mid.normalize().dot(view.normalize()));
                int color = lerp(0xFFD8B0, 0xFFF4D0, (float)(rim * rim));
                int alpha = (int)(255 * open * (0.93 + 0.07 * rim));
                FxDraw.texQuad(pose, b, FxDraw.opaque(FxDraw.UBW_SKY), q0, q1, q2, q3, FxDraw.argb(alpha, color),
                    j / (float)lon, i / (float)lat * 0.45f + 0.02f, (j + 1) / (float)lon, (i + 1) / (float)lat * 0.45f + 0.02f);
            }
        }
    }

    // ---- The seam ----

    /**
     * Where the marble meets another domain: a white-hot line along the seam on the marble's side,
     * fading to ember, wider the further away it is (the other domain lights its own side).
     */
    private static void seamGlow(PoseStack pose, PortBuffers b, DomainSeam.Clip clip, float r, float gy, Vec3 toCamera, boolean inside, boolean low) {
        Vec3 nrm = clip.normal();
        double off = clip.offset();
        if (Math.abs(off) >= r) return;
        Vec3[] basis = FxDraw.basis(nrm);
        int segs = low ? 48 : 96;
        double rho = Math.sqrt(r * r - off * off);
        for (int k = 0; k < segs; ++k) {
            double a0 = k * Math.PI * 2.0 / segs, a1 = (k + 1) * Math.PI * 2.0 / segs;
            Vec3 e0 = basis[0].scale(Math.cos(a0)).add(basis[1].scale(Math.sin(a0)));
            Vec3 e1 = basis[0].scale(Math.cos(a1)).add(basis[1].scale(Math.sin(a1)));
            Vec3 p0 = nrm.scale(off).add(e0.scale(rho)), p1 = nrm.scale(off).add(e1.scale(rho));
            if (inside && p0.y < gy - 0.5 && p1.y < gy - 0.5) continue;
            double w = 0.9 + 0.035 * p0.add(p1).scale(0.5).distanceTo(toCamera);
            double back = Math.min(r - 0.01, off - w);
            double rho2 = Math.sqrt(Math.max(0.0, r * r - (off - w) * (off - w)));
            Vec3 q0 = nrm.scale(off - w).add(e0.scale(rho2)), q1 = nrm.scale(off - w).add(e1.scale(rho2));
            if (back <= -r) continue;
            FxDraw.quad(pose, b, p0, p1, q1, q0, FxDraw.argb(235, 0xFFF4E4), FxDraw.argb(235, 0xFFF4E4), FxDraw.argb(0, 0xFF7A2A), FxDraw.argb(0, 0xFF7A2A));
        }
        if (!inside) return;
        // Across the ground: the line where the seam cuts the plain.
        Vec3 nh = new Vec3(nrm.x, 0.0, nrm.z);
        double h2 = nh.lengthSqr();
        if (h2 < 1.0E-4) return;
        Vec3 x0 = nh.scale((off - nrm.y * gy) / h2);
        double half = r * r - x0.lengthSqr();
        if (half <= 0.0) return;
        half = Math.sqrt(half);
        Vec3 along = new Vec3(-nrm.z, 0.0, nrm.x).normalize();
        Vec3 inward = nh.normalize().scale(-1.0);
        int pieces = Math.max(4, (int)(half / 3.0));
        for (int k = 0; k < pieces; ++k) {
            double s0 = -half + 2.0 * half * k / pieces, s1 = -half + 2.0 * half * (k + 1) / pieces;
            Vec3 a = x0.add(along.scale(s0)).add(0.0, gy + 0.05, 0.0), c = x0.add(along.scale(s1)).add(0.0, gy + 0.05, 0.0);
            double w = 0.9 + 0.035 * a.add(c).scale(0.5).distanceTo(toCamera);
            FxDraw.quad(pose, b, a, c, c.add(inward.scale(w)), a.add(inward.scale(w)),
                FxDraw.argb(235, 0xFFF4E4), FxDraw.argb(235, 0xFFF4E4), FxDraw.argb(0, 0xFF7A2A), FxDraw.argb(0, 0xFF7A2A));
        }
    }

    /** The ring of fire on the ground (the part beyond a seam left out). */
    private static void fireRing(PoseStack pose, PortBuffers b, double y, float rIn, float rOut, int segs, int cin, int cout, DomainSeam.Clip clip) {
        for (int i = 0; i < segs; ++i) {
            double a0 = i * Math.PI * 2 / segs, a1 = (i + 1) * Math.PI * 2 / segs;
            Vec3 d0 = new Vec3(Math.cos(a0), 0.0, Math.sin(a0)), d1 = new Vec3(Math.cos(a1), 0.0, Math.sin(a1));
            Vec3 o0 = d0.scale(rOut).add(0.0, y, 0.0), o1 = d1.scale(rOut).add(0.0, y, 0.0);
            if (clip != null && !clip.keeps(o0) && !clip.keeps(o1)) continue;
            FxDraw.quad(pose, b, d0.scale(rIn).add(0.0, y, 0.0), o0, o1, d1.scale(rIn).add(0.0, y, 0.0), cin, cout, cout, cin);
        }
    }

    private static float smooth(float x) {
        float v = Math.max(0.0f, Math.min(1.0f, x));
        return v * v * (3.0f - 2.0f * v);
    }

    private static int lerp(int from, int to, float t) {
        int r = (int)(((from >> 16) & 255) + (((to >> 16) & 255) - ((from >> 16) & 255)) * t);
        int g = (int)(((from >> 8) & 255) + (((to >> 8) & 255) - ((from >> 8) & 255)) * t);
        int bl = (int)((from & 255) + ((to & 255) - (from & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
