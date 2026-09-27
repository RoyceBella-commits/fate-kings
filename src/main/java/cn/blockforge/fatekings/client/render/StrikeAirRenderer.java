package cn.blockforge.fatekings.client.render;

import cn.blockforge.fatekings.entity.StrikeAirEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;

/** Strike Air: a column of compressed, twisting air with white streaks round it. */
public class StrikeAirRenderer extends FxEntityRenderer<StrikeAirEntity> {
    public StrikeAirRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void build(StrikeAirEntity e, State state, float partial, PoseStack pose, PortBuffers b, Vec3 toCamera) {
        Vec3 v = e.getDeltaMovement();
        if (v.lengthSqr() < 1.0E-6) return;
        Vec3 dir = v.normalize();
        float t = e.tickCount + partial;
        Vec3 tail = dir.scale(-4.5), head = dir.scale(1.2);
        FxDraw.tube(pose, b, tail, head, 0.4f, 1.0f, 12, FxDraw.argb(0, 0xDDF4FF), FxDraw.argb(60, 0xDDF4FF), t * 0.35f);
        FxDraw.tube(pose, b, tail.scale(0.6), head, 0.2f, 0.55f, 10, FxDraw.argb(0, 0xFFFFFF), FxDraw.argb(45, 0xFFFFFF), -t * 0.5f);
        Vec3[] uv = FxDraw.basis(dir);
        for (int k = 0; k < 3; ++k) {
            // Three helical white streaks.
            Vec3 prev = null;
            for (int i = 0; i <= 12; ++i) {
                double s = -4.5 + i * 0.5;
                double a = s * 1.4 + t * 0.6 + k * Math.PI * 2 / 3;
                double r = 0.9 * (0.4 + 0.6 * (i / 12.0));
                Vec3 p = dir.scale(s).add(uv[0].scale(Math.cos(a) * r)).add(uv[1].scale(Math.sin(a) * r));
                if (prev != null) FxDraw.ribbon(pose, b, prev, p, toCamera, 0.07f, FxDraw.argb(20 + i * 12, 0xFFFFFF), FxDraw.argb(30 + i * 12, 0xFFFFFF));
                prev = p;
            }
        }
    }
}
