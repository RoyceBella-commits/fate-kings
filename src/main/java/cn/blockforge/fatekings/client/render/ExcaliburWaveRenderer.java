package cn.blockforge.fatekings.client.render;

import cn.blockforge.fatekings.client.FateClient;
import cn.blockforge.fatekings.entity.BeamEntity;
import cn.blockforge.fatekings.entity.ExcaliburWaveEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;

/** Excalibur: a golden-white band of light sweeping forward with a crescent edge, then an afterglow. */
public class ExcaliburWaveRenderer extends FxEntityRenderer<ExcaliburWaveEntity> {
    public ExcaliburWaveRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void build(ExcaliburWaveEntity e, State state, float partial, PoseStack pose, PortBuffers b, Vec3 toCamera) {
        boolean low = FateClient.prefs().lowFx();
        Vec3 dir = e.dir();
        float w = e.width();
        float front = e.front() + (e.state() == BeamEntity.ADVANCING ? 10.0f * partial : 0.0f);
        float fade = e.state() == BeamEntity.ENDING ? Math.max(0.0f, 1.0f - e.stateAge(partial) / 30.0f) : 1.0f;
        if (fade <= 0.0f) return;
        float t = e.tickCount + partial;
        Vec3 end = dir.scale(front);
        // The band: vertical plane through the path, bright core fading to the edges.
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 side = dir.cross(up);
        Vec3 plane = side.lengthSqr() < 1.0E-4 ? FxDraw.basis(dir)[0] : side.normalize().cross(dir).normalize();
        Vec3 h = plane.scale(w * 0.5);
        // A replica's light is paler, greyer: a copy of the star's light.
        int hot = e.replica() ? 0xE8ECF4 : 0xFFF7DA, warm = e.replica() ? 0xB8C0D0 : 0xFFE38A;
        int core = FxDraw.alpha(0.95f * fade, hot), edge = FxDraw.alpha(0.0f, warm), mid = FxDraw.alpha(0.6f * fade, warm);
        FxDraw.quad(pose, b, Vec3.ZERO, end, end.add(h), h, core, core, edge, edge);
        FxDraw.quad(pose, b, Vec3.ZERO, end, end.subtract(h), h.scale(-1.0), core, core, edge, edge);
        FxDraw.tube(pose, b, Vec3.ZERO, end, w * 0.12f, w * 0.18f, 12, core, mid, 0.0f);
        if (!low) {
            // A wider, fainter light sheet horizontally as well.
            Vec3 s2 = side.lengthSqr() < 1.0E-4 ? FxDraw.basis(dir)[1].scale(w * 0.6) : side.normalize().scale(w * 0.6);
            FxDraw.quad(pose, b, Vec3.ZERO, end, end.add(s2), s2, mid, mid, edge, edge);
            FxDraw.quad(pose, b, Vec3.ZERO, end, end.subtract(s2), s2.scale(-1.0), mid, mid, edge, edge);
        }
        if (e.state() == BeamEntity.ADVANCING || e.state() == BeamEntity.CLASHING) {
            // The crescent edge of the slash.
            Vec3[] uv = FxDraw.basis(dir);
            for (int i = 0; i < 12; ++i) {
                double a0 = -Math.PI / 2 + i * Math.PI / 12, a1 = -Math.PI / 2 + (i + 1) * Math.PI / 12;
                Vec3 p0 = end.add(plane.scale(Math.sin(a0) * w * 0.6)).add(dir.scale(Math.cos(a0) * 1.5));
                Vec3 p1 = end.add(plane.scale(Math.sin(a1) * w * 0.6)).add(dir.scale(Math.cos(a1) * 1.5));
                FxDraw.ribbon(pose, b, p0, p1, toCamera.subtract(end), 0.9f, FxDraw.alpha(0.9f, 0xFFFFFF), FxDraw.alpha(0.9f, 0xFFFFFF));
            }
            FxDraw.sphere(pose, b, end, w * 0.3f + (float)Math.sin(t) * 0.2f, 6, 10, FxDraw.alpha(0.5f, 0xFFF7DA));
        } else {
            FxDraw.sphere(pose, b, end, w * (1.0f + (1.0f - fade) * 2.0f), 8, 12, FxDraw.alpha(0.5f * fade, 0xFFE38A));
        }
    }
}
