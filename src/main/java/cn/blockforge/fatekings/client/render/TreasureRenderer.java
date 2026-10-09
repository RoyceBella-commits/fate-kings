package cn.blockforge.fatekings.client.render;

import cn.blockforge.fatekings.entity.TreasureProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A treasure in flight: the weapon itself, blade first, trailing gold light. */
public class TreasureRenderer extends FxEntityRenderer<TreasureProjectile> {

    public TreasureRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void build(TreasureProjectile e, State state, float partial, PoseStack pose, PortBuffers b, Vec3 toCamera) {
        Vec3 v = e.getDeltaMovement();
        Vec3 dir = v.lengthSqr() < 1.0E-6 ? Vec3.directionFromRotation(e.getXRot(), e.getYRot()).scale(-1.0) : v.normalize();
        Quaternionf rot = new Quaternionf().rotationTo(BLADE, new Vector3f((float)dir.x, (float)dir.y, (float)dir.z));
        setItem(e, state, e.weapon(), rot, Vec3.ZERO, 1.25f);
        if (v.lengthSqr() > 0.05) {
            Vec3 tail = dir.scale(-Math.min(4.0, v.length() * 1.6));
            FxDraw.ribbon(pose, b, tail, Vec3.ZERO, toCamera, 0.28f, FxDraw.argb(0, 0xFFD34A), FxDraw.argb(170, 0xFFE89A));
        }
    }
}
