package cn.blockforge.fatekings.client.render;

import cn.blockforge.fatekings.FateKings;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Geometry helpers for the effects. Glow (additive, untextured) uses the lightning render type,
 * space rifts the end-portal starfield, textured sprites the translucent emissive entity type.
 */
public final class FxDraw {
    public static final int FULL_BRIGHT = 15728880;
    public static final Identifier RIPPLE = FateKings.id("textures/misc/gate_ripple.png");
    public static final Identifier LABYRINTH = FateKings.id("textures/misc/labyrinth.png");
    public static final Identifier CHAIN = FateKings.id("textures/misc/chain_link.png");
    public static final Identifier SHARD = FateKings.id("textures/misc/avalon_shard.png");
    public static final Identifier CRACKS = FateKings.id("textures/misc/golden_cracks.png");
    public static final Identifier VIMANA = FateKings.id("textures/entity/vimana.png");
    public static final Identifier RHO_PETAL = FateKings.id("textures/misc/rho_aias_petal.png");
    public static final Identifier TWIN_TRAIL = FateKings.id("textures/misc/twin_trail.png");
    public static final Identifier UBW_SKY = FateKings.id("textures/misc/ubw_sky.png");
    public static final Identifier UBW_GEAR = FateKings.id("textures/misc/ubw_gear.png");
    public static final Identifier UBW_SUN = FateKings.id("textures/misc/ubw_sun.png");
    public static final Identifier UBW_SUN_GLOW = FateKings.id("textures/misc/ubw_sun_glow.png");
    public static final Identifier UBW_GROUND = FateKings.id("textures/misc/ubw_ground.png");
    public static final Identifier UBW_MIST = FateKings.id("textures/misc/ubw_mist.png");

    private FxDraw() {
    }

    public static RenderType glow() {
        return RenderTypes.lightning();
    }

    public static RenderType rift() {
        return RenderTypes.endPortal();
    }

    public static RenderType tex(Identifier id) {
        return RenderTypes.entityTranslucentEmissive(id);
    }

    /**
     * Opaque, unlit and writing depth (the opaque beacon-beam type): for surfaces that must hide what
     * lies behind them whatever order the frame's translucent layers are drawn in, such as the sky
     * and the ground of a reality marble.
     */
    public static RenderType opaque(Identifier id) {
        return RenderTypes.beaconBeam(id, false);
    }

    /** Translucent and unlit (the beacon's outer glow): light that is not shaded by which way it faces. */
    public static RenderType light(Identifier id) {
        return RenderTypes.beaconBeam(id, true);
    }

    public static int argb(int a, int rgb) {
        return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF);
    }

    public static int alpha(float a, int rgb) {
        return argb(Math.round(a * 255.0f), rgb);
    }

    private static void v(VertexConsumer vc, Matrix4f m, Vec3 p, int argb) {
        vc.addVertex(m, (float)p.x, (float)p.y, (float)p.z).setColor(argb);
    }

    private static void vt(VertexConsumer vc, Matrix4f m, Vec3 p, int argb, float u, float v, Vec3 n) {
        vc.addVertex(m, (float)p.x, (float)p.y, (float)p.z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(FULL_BRIGHT).setNormal((float)n.x, (float)n.y, (float)n.z);
    }

    /** A glowing quad, drawn from both sides. */
    public static void quad(PoseStack pose, PortBuffers b, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, int c0, int c1, int c2, int c3) {
        VertexConsumer vc = b.getBuffer(glow());
        Matrix4f m = pose.last().pose();
        v(vc, m, p0, c0);
        v(vc, m, p1, c1);
        v(vc, m, p2, c2);
        v(vc, m, p3, c3);
        v(vc, m, p3, c3);
        v(vc, m, p2, c2);
        v(vc, m, p1, c1);
        v(vc, m, p0, c0);
    }

    public static void quad(PoseStack pose, PortBuffers b, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, int c) {
        quad(pose, b, p0, p1, p2, p3, c, c, c, c);
    }

    /** A textured, full-bright quad drawn from both sides. */
    public static void texQuad(PoseStack pose, PortBuffers b, Identifier tex, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, int c,
                               float u0, float v0, float u1, float v1) {
        texQuad(pose, b, tex(tex), p0, p1, p2, p3, c, u0, v0, u1, v1);
    }

    /** A textured quad drawn from both sides in the given render type. */
    public static void texQuad(PoseStack pose, PortBuffers b, RenderType type, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, int c,
                               float u0, float v0, float u1, float v1) {
        VertexConsumer vc = b.getBuffer(type);
        Matrix4f m = pose.last().pose();
        Vec3 n = p1.subtract(p0).cross(p3.subtract(p0));
        n = n.lengthSqr() < 1.0E-8 ? new Vec3(0, 1, 0) : n.normalize();
        vt(vc, m, p0, c, u0, v0, n);
        vt(vc, m, p1, c, u1, v0, n);
        vt(vc, m, p2, c, u1, v1, n);
        vt(vc, m, p3, c, u0, v1, n);
        Vec3 r = n.scale(-1.0);
        vt(vc, m, p3, c, u0, v1, r);
        vt(vc, m, p2, c, u1, v1, r);
        vt(vc, m, p1, c, u1, v0, r);
        vt(vc, m, p0, c, u0, v0, r);
    }

    /** Starfield (end portal) quad: space torn open. */
    public static void riftQuad(PoseStack pose, PortBuffers b, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3) {
        VertexConsumer vc = b.getBuffer(rift());
        Matrix4f m = pose.last().pose();
        for (Vec3 p : new Vec3[]{p0, p1, p2, p3, p3, p2, p1, p0}) vc.addVertex(m, (float)p.x, (float)p.y, (float)p.z);
    }

    /** Two unit vectors perpendicular to {@code n} (and to each other). */
    public static Vec3[] basis(Vec3 n) {
        Vec3 d = n.normalize();
        Vec3 ref = Math.abs(d.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 a = d.cross(ref).normalize();
        Vec3 c = d.cross(a).normalize();
        return new Vec3[]{a, c};
    }

    /** A square sprite centred on {@code c} facing along {@code normal}, rotated by {@code spin}. */
    public static void sprite(PoseStack pose, PortBuffers b, Identifier tex, Vec3 c, Vec3 normal, float r, float spin, int color) {
        sprite(pose, b, tex(tex), c, normal, r, spin, color);
    }

    public static void sprite(PoseStack pose, PortBuffers b, RenderType type, Vec3 c, Vec3 normal, float r, float spin, int color) {
        Vec3[] uv = basis(normal);
        double cs = Math.cos(spin), sn = Math.sin(spin);
        Vec3 a = uv[0].scale(cs).add(uv[1].scale(sn)).scale(r);
        Vec3 d = uv[0].scale(-sn).add(uv[1].scale(cs)).scale(r);
        texQuad(pose, b, type, c.subtract(a).subtract(d), c.add(a).subtract(d), c.add(a).add(d), c.subtract(a).add(d), color, 0, 0, 1, 1);
    }

    /** A camera-facing band from {@code a} to {@code b}. */
    public static void ribbon(PoseStack pose, PortBuffers buf, Vec3 a, Vec3 b, Vec3 toCamera, float width, int ca, int cb) {
        Vec3 along = b.subtract(a);
        Vec3 side = along.cross(toCamera);
        if (side.lengthSqr() < 1.0E-8) return;
        side = side.normalize().scale(width * 0.5);
        quad(pose, buf, a.subtract(side), b.subtract(side), b.add(side), a.add(side), ca, cb, cb, ca);
    }

    /** A camera-facing textured band (translucent, so a dark trail is visible too). */
    public static void texRibbon(PoseStack pose, PortBuffers buf, Identifier tex, Vec3 a, Vec3 b, Vec3 toCamera, float width, int color, float v0, float v1) {
        Vec3 along = b.subtract(a);
        Vec3 side = along.cross(toCamera);
        if (side.lengthSqr() < 1.0E-8) return;
        side = side.normalize().scale(width * 0.5);
        texQuad(pose, buf, tex, a.subtract(side), b.subtract(side), b.add(side), a.add(side), color, v0, 0.0f, v1, 1.0f);
    }

    /** A textured latitude / longitude sphere (u around, v from top to bottom), seen from inside and out. */
    public static void texSphere(PoseStack pose, PortBuffers b, Identifier tex, Vec3 c, float r, int lat, int lon, int color) {
        for (int i = 0; i < lat; ++i) {
            double t0 = Math.PI * i / lat, t1 = Math.PI * (i + 1) / lat;
            for (int j = 0; j < lon; ++j) {
                double p0 = Math.PI * 2 * j / lon, p1 = Math.PI * 2 * (j + 1) / lon;
                texQuad(pose, b, tex, sp(c, r, t0, p0), sp(c, r, t0, p1), sp(c, r, t1, p1), sp(c, r, t1, p0), color,
                    j / (float)lon, i / (float)lat, (j + 1) / (float)lon, (i + 1) / (float)lat);
            }
        }
    }

    /**
     * The same sphere cut at a domain seam: quads wholly beyond it are left out and the corners of
     * those that cross it are pulled onto it, so the edge follows the seam.
     */
    public static void texSphere(PoseStack pose, PortBuffers b, Identifier tex, Vec3 c, float r, int lat, int lon, int color, DomainSeam.Clip clip) {
        texSphere(pose, b, tex(tex), c, r, lat, lon, color, clip);
    }

    /** The same in a given render type (see {@link #opaque}). */
    public static void texSphere(PoseStack pose, PortBuffers b, RenderType type, Vec3 c, float r, int lat, int lon, int color, DomainSeam.Clip clip) {
        for (int i = 0; i < lat; ++i) {
            double t0 = Math.PI * i / lat, t1 = Math.PI * (i + 1) / lat;
            for (int j = 0; j < lon; ++j) {
                double p0 = Math.PI * 2 * j / lon, p1 = Math.PI * 2 * (j + 1) / lon;
                Vec3 a = sp(c, r, t0, p0), bb = sp(c, r, t0, p1), cc = sp(c, r, t1, p1), d = sp(c, r, t1, p0);
                if (clip != null) {
                    if (!clip.keeps(a) && !clip.keeps(bb) && !clip.keeps(cc) && !clip.keeps(d)) continue;
                    a = clip.onto(a);
                    bb = clip.onto(bb);
                    cc = clip.onto(cc);
                    d = clip.onto(d);
                }
                texQuad(pose, b, type, a, bb, cc, d, color,
                    j / (float)lon, i / (float)lat, (j + 1) / (float)lon, (i + 1) / (float)lat);
            }
        }
    }

    /** A textured quad facing along {@code normal} and kept upright (its up as near the world's up as it can be). */
    public static void upright(PoseStack pose, PortBuffers b, Identifier tex, Vec3 c, Vec3 normal, float halfW, float halfH, int color,
                               float u0, float v0, float u1, float v1) {
        upright(pose, b, tex(tex), c, normal, halfW, halfH, color, u0, v0, u1, v1);
    }

    public static void upright(PoseStack pose, PortBuffers b, RenderType type, Vec3 c, Vec3 normal, float halfW, float halfH, int color,
                               float u0, float v0, float u1, float v1) {
        Vec3 n = normal.normalize();
        Vec3 right = n.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(n).normalize();
        Vec3 w = right.scale(halfW), h = up.scale(halfH);
        texQuad(pose, b, type, c.subtract(w).add(h), c.add(w).add(h), c.add(w).subtract(h), c.subtract(w).subtract(h), color, u0, v0, u1, v1);
    }

    /** A flat ring between two radii in the plane perpendicular to {@code normal}. */
    public static void ring(PoseStack pose, PortBuffers b, Vec3 c, Vec3 normal, float rIn, float rOut, int segs, int cin, int cout) {
        Vec3[] uv = basis(normal);
        for (int i = 0; i < segs; ++i) {
            double a0 = i * Math.PI * 2 / segs, a1 = (i + 1) * Math.PI * 2 / segs;
            Vec3 d0 = uv[0].scale(Math.cos(a0)).add(uv[1].scale(Math.sin(a0)));
            Vec3 d1 = uv[0].scale(Math.cos(a1)).add(uv[1].scale(Math.sin(a1)));
            quad(pose, b, c.add(d0.scale(rIn)), c.add(d0.scale(rOut)), c.add(d1.scale(rOut)), c.add(d1.scale(rIn)), cin, cout, cout, cin);
        }
    }

    /** A tube from {@code a} to {@code b}; {@code twist} turns the segments to make a spiral of colours. */
    public static void tube(PoseStack pose, PortBuffers b, Vec3 a, Vec3 z, float ra, float rz, int segs, int ca, int cz, float twist) {
        Vec3 axis = z.subtract(a);
        if (axis.lengthSqr() < 1.0E-6) return;
        Vec3[] uv = basis(axis);
        for (int i = 0; i < segs; ++i) {
            double a0 = i * Math.PI * 2 / segs, a1 = (i + 1) * Math.PI * 2 / segs;
            Vec3 d0 = uv[0].scale(Math.cos(a0)).add(uv[1].scale(Math.sin(a0)));
            Vec3 d1 = uv[0].scale(Math.cos(a1)).add(uv[1].scale(Math.sin(a1)));
            Vec3 e0 = uv[0].scale(Math.cos(a0 + twist)).add(uv[1].scale(Math.sin(a0 + twist)));
            Vec3 e1 = uv[0].scale(Math.cos(a1 + twist)).add(uv[1].scale(Math.sin(a1 + twist)));
            quad(pose, b, a.add(d0.scale(ra)), a.add(d1.scale(ra)), z.add(e1.scale(rz)), z.add(e0.scale(rz)), ca, ca, cz, cz);
        }
    }

    /** A latitude / longitude sphere. */
    public static void sphere(PoseStack pose, PortBuffers b, Vec3 c, float r, int lat, int lon, int color) {
        for (int i = 0; i < lat; ++i) {
            double t0 = Math.PI * i / lat, t1 = Math.PI * (i + 1) / lat;
            for (int j = 0; j < lon; ++j) {
                double p0 = Math.PI * 2 * j / lon, p1 = Math.PI * 2 * (j + 1) / lon;
                quad(pose, b, sp(c, r, t0, p0), sp(c, r, t0, p1), sp(c, r, t1, p1), sp(c, r, t1, p0), color);
            }
        }
    }

    public static Vec3 sp(Vec3 c, double r, double theta, double phi) {
        return c.add(Math.sin(theta) * Math.cos(phi) * r, Math.cos(theta) * r, Math.sin(theta) * Math.sin(phi) * r);
    }
}
