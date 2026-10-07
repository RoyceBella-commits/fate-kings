package cn.blockforge.fatekings.client.render;

import cn.blockforge.fatekings.entity.UbwSwordEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A sword of the hill: blade up as it rises, then blade first, trailing embers. */
public class UbwSwordRenderer extends FxEntityRenderer<UbwSwordEntity> {
    private static final Vector3f BLADE = new Vector3f(1.0f, 1.0f, 0.0f).normalize();

    public UbwSwordRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void build(UbwSwordEntity e, State state, float partial, PoseStack pose, PortBuffers b, Vec3 toCamera) {
        Vec3 v = e.getDeltaMovement();
        Vec3 dir = v.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : v.normalize();
        Quaternionf rot = new Quaternionf().rotationTo(BLADE, new Vector3f((float)dir.x, (float)dir.y, (float)dir.z));
        setItem(e, state, e.weapon(), rot, Vec3.ZERO, 1.3f);
        if (v.lengthSqr() > 0.05) {
            FxDraw.ribbon(pose, b, dir.scale(-Math.min(3.0, v.length() * 1.3)), Vec3.ZERO, toCamera, 0.3f, FxDraw.argb(0, 0xFF8A2A),
                FxDraw.argb(170, 0xFFB060));
        }
    }
}
