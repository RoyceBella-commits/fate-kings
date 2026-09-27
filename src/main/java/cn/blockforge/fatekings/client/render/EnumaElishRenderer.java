package cn.blockforge.fatekings.client.render;

import cn.blockforge.fatekings.client.FateClient;
import cn.blockforge.fatekings.entity.BeamEntity;
import cn.blockforge.fatekings.entity.EnumaElishEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Random;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;

/**
 * Enuma Elish: a red torrent wound with black spirals; space cracks like glass along its path and
 * the starry void shows through; at the end a black hole collapses. Fewer effects: only the red
 * beam and the outline of the cracks.
 */
public class EnumaElishRenderer extends FxEntityRenderer<EnumaElishEntity> {
    public EnumaElishRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void build(EnumaElishEntity e, State state, float partial, PoseStack pose, PortBuffers b, Vec3 toCamera) {
        boolean low = FateClient.prefs().lowFx();
        Vec3 dir = e.dir();
        float w = e.width();
        float front = e.front() + (e.state() == BeamEntity.ADVANCING ? 8.0f * partial : 0.0f);
        float t = e.tickCount + partial;
        float fade = e.state() == BeamEntity.ENDING ? Math.max(0.0f, 1.0f - e.stateAge(partial) / 20.0f) : 1.0f;
        if (fade <= 0.0f && e.state() == BeamEntity.ENDING && e.stateAge(partial) > 34) return;
        Vec3 end = dir.scale(front);
        if (fade > 0.0f) {
            FxDraw.tube(pose, b, Vec3.ZERO, end, w * 0.18f, w * 0.3f, 14, FxDraw.alpha(0.9f * fade, 0xE0202A), FxDraw.alpha(0.8f * fade, 0xB0101A), t * 0.3f);
            FxDraw.tube(pose, b, Vec3.ZERO, end, w * 0.35f, w * 0.5f, 14, FxDraw.alpha(0.35f * fade, 0x8A0010), FxDraw.alpha(0.25f * fade, 0x5A0008), -t * 0.2f);
            Vec3[] uv = FxDraw.basis(dir);
            // Spiral bands of wind round the torrent.
            for (int k = 0; k < 3; ++k) {
                Vec3 prev = null;
                for (double s = 0; s <= front; s += 2.0) {
                    double a = s * 0.35 - t * 0.9 + k * Math.PI * 2 / 3;
                    double r = w * 0.45;
                    Vec3 p = dir.scale(s).add(uv[0].scale(Math.cos(a) * r)).add(uv[1].scale(Math.sin(a) * r));
                    if (prev != null) FxDraw.ribbon(pose, b, prev, p, toCamera, w * 0.12f, FxDraw.alpha(0.6f * fade, k == 1 ? 0x2A0A0E : 0xFF3A2A),
                        FxDraw.alpha(0.6f * fade, k == 1 ? 0x2A0A0E : 0xFF3A2A));
                    prev = p;
                }
            }
            cracks(e, pose, b, dir, uv, front, w, fade, low);
        }
        if (e.state() == BeamEntity.ENDING && !low) {
            // The black hole at the end: the void opens, then collapses.
            float age = e.stateAge(partial);
            float r = age < 18 ? w * 0.9f * (age / 18.0f) : w * 0.9f * Math.max(0.0f, 1.0f - (age - 18) / 10.0f);
            if (r > 0.05f) {
                Vec3 c = end;
                Vec3[] uv = FxDraw.basis(toCamera.subtract(c));
                for (int i = 0; i < 16; ++i) {
                    double a0 = i * Math.PI * 2 / 16, a1 = (i + 1) * Math.PI * 2 / 16;
                    Vec3 p0 = c.add(uv[0].scale(Math.cos(a0) * r)).add(uv[1].scale(Math.sin(a0) * r));
                    Vec3 p1 = c.add(uv[0].scale(Math.cos(a1) * r)).add(uv[1].scale(Math.sin(a1) * r));
                    FxDraw.riftQuad(pose, b, c, p0, p1, p1);
                }
                FxDraw.ring(pose, b, c, toCamera.subtract(c), r, r * 1.35f, 24, FxDraw.argb(220, 0xFF3A2A), FxDraw.argb(0, 0x8A0010));
            }
        }
    }

    /** Glass-like shards of torn space along the path (starfield inside, red rims). */
    private void cracks(EnumaElishEntity e, PoseStack pose, PortBuffers b, Vec3 dir, Vec3[] uv, float front, float w, float fade, boolean low) {
        Random rnd = new Random(e.getId() * 31L);
        for (double s = 2.0; s < front; s += 3.0) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double r0 = w * (0.45 + rnd.nextDouble() * 0.25), r1 = r0 + w * (0.3 + rnd.nextDouble() * 0.5);
            Vec3 radial = uv[0].scale(Math.cos(a)).add(uv[1].scale(Math.sin(a)));
            Vec3 c0 = dir.scale(s).add(radial.scale(r0));
            Vec3 c1 = dir.scale(s + 1.0 + rnd.nextDouble() * 2.0).add(radial.scale(r1));
            Vec3 side = dir.cross(radial).normalize().scale(0.4 + rnd.nextDouble() * 0.8);
            Vec3 p0 = c0.subtract(side), p1 = c0.add(side), p2 = c1.add(side.scale(0.3)), p3 = c1.subtract(side.scale(0.3));
            if (!low && fade > 0.3f) FxDraw.riftQuad(pose, b, p0, p1, p2, p3);
            int rim = FxDraw.alpha(0.8f * fade, 0xFF4A3A);
            FxDraw.quad(pose, b, p0, p1, p1.add(radial.scale(0.08)), p0.add(radial.scale(0.08)), rim);
            FxDraw.quad(pose, b, p1, p2, p2.add(radial.scale(0.08)), p1.add(radial.scale(0.08)), rim);
        }
    }
}
