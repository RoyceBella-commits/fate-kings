package cn.blockforge.fatekings.client.render;

import cn.blockforge.fatekings.entity.SwordQiEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;

/** The golden crescent: a bright edge along the stroke's angle, a softer glow and a fading trail. */
public class SwordQiRenderer extends FxEntityRenderer<SwordQiEntity> {
    public SwordQiRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void build(SwordQiEntity e, State state, float partial, PoseStack pose, PortBuffers b, Vec3 toCamera) {
        Vec3 d = e.dir();
        Vec3 edge = e.edgeAxis();
        double h = SwordQiEntity.HALF_LENGTH;
        float fade = Math.min(1.0f, (e.tickCount + partial) / 2.0f);
        for (int layer = 0; layer < 3; ++layer) {
            double back = layer * 0.7;
            float width = layer == 0 ? 0.45f : 0.3f;
            int core = FxDraw.alpha(fade * (layer == 0 ? 0.95f : 0.45f / layer), layer == 0 ? 0xFFF7DA : 0xFFE38A);
            Vec3 prev = null;
            for (int i = 0; i <= 12; ++i) {
                double s = -h + 2.0 * h * i / 12.0;
                double lead = 0.6 * (1.0 - (s / h) * (s / h));
                Vec3 p = edge.scale(s).add(d.scale(lead - back));
                if (prev != null) {
                    float taper = (float)(1.0 - Math.abs(s) / h * 0.7);
                    FxDraw.ribbon(pose, b, prev, p, toCamera, width * taper, core, core);
                }
                prev = p;
            }
        }
        FxDraw.ribbon(pose, b, edge.scale(-h * 0.8), edge.scale(h * 0.8), toCamera, 1.2f,
            FxDraw.alpha(0.15f * fade, 0xFFE38A), FxDraw.alpha(0.15f * fade, 0xFFE38A));
    }
}
