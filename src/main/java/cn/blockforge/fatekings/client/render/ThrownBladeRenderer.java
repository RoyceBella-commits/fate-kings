package cn.blockforge.fatekings.client.render;

import cn.blockforge.fatekings.entity.ThrownBladeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Kanshou / Bakuya spinning through the air, a faint arc of its colour behind it. */
public class ThrownBladeRenderer extends FxEntityRenderer<ThrownBladeEntity> {
    public ThrownBladeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void build(ThrownBladeEntity e, State state, float partial, PoseStack pose, PortBuffers b, Vec3 toCamera) {
        float t = e.tickCount + partial;
        Quaternionf rot = new Quaternionf().rotationX((float)Math.toRadians(90.0)).rotateZ(t * 1.05f);
        setItem(e, state, e.weapon(), rot, Vec3.ZERO, 1.2f);
        Vec3 v = new Vec3(e.getX() - e.xo, e.getY() - e.yo, e.getZ() - e.zo);
        if (v.lengthSqr() > 0.01) {
            boolean black = e.weapon().is(cn.blockforge.fatekings.registry.FateItems.KANSHOU);
            int c = black ? 0xE0302A : 0xF4F8FF;
            FxDraw.ribbon(pose, b, v.scale(-2.5), Vec3.ZERO, toCamera, 0.5f, FxDraw.argb(0, c), FxDraw.argb(120, c));
        }
    }
}
