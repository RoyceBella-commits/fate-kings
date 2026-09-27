package cn.blockforge.fatekings.client.render;

import cn.blockforge.fatekings.entity.ChainEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;

/** Enkidu: golden links from a ripple to the target, snaking out and pulled taut; glowing brighter when binding. */
public class ChainRenderer extends FxEntityRenderer<ChainEntity> {
    public ChainRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void build(ChainEntity e, State state, float partial, PoseStack pose, PortBuffers b, Vec3 toCamera) {
        Vec3 end = e.end(partial).subtract(e.getPosition(partial));
        float reach = e.reach(partial);
        double len = end.length() * reach;
        if (len < 0.2) return;
        Vec3 dir = end.normalize();
        Vec3[] uv = FxDraw.basis(dir);
        float t = e.tickCount + partial;
        boolean bind = e.mode() == ChainEntity.BIND;
        int links = (int)(len / 0.42);
        float glow = bind ? 0.6f + 0.4f * (float)Math.sin(t * 0.4) : 0.3f;
        FxDraw.sprite(pose, b, FxDraw.RIPPLE, Vec3.ZERO, dir, 0.7f, t * 0.1f, FxDraw.argb(200, 0xFFFFFF));
        for (int i = 0; i < links; ++i) {
            double s = i * 0.42;
            // A little snake-like sway while the chain is shooting out, straight once it holds.
            double sway = reach < 1.0f || e.mode() == ChainEntity.HOOK ? Math.sin(s * 1.3 - t * 0.9) * 0.18 * (1.0 - s / Math.max(len, 1.0)) : 0.0;
            Vec3 c = dir.scale(s).add(uv[0].scale(sway));
            Vec3 across = i % 2 == 0 ? uv[0] : uv[1];
            Vec3 a = dir.scale(0.26), w = across.scale(0.13);
            FxDraw.texQuad(pose, b, FxDraw.CHAIN, c.subtract(a).subtract(w), c.add(a).subtract(w), c.add(a).add(w), c.subtract(a).add(w),
                0xFFFFFFFF, 0, 0, 1, 1);
        }
        if (glow > 0.35f) {
            FxDraw.ribbon(pose, b, Vec3.ZERO, dir.scale(len), toCamera, 0.35f, FxDraw.alpha(glow * 0.35f, 0xFFE58A), FxDraw.alpha(glow * 0.35f, 0xFFE58A));
        }
        if (e.mode() == ChainEntity.STOPPED) {
            float f = Math.min(1.0f, t / 16.0f);
            FxDraw.sphere(pose, b, dir.scale(len), 0.35f + f * 0.5f, 5, 8, FxDraw.alpha(0.5f * (1.0f - f), 0xFFD34A));
        }
    }
}
