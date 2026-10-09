package cn.blockforge.fatekings.client.render;

import cn.blockforge.fatekings.entity.ProjectedArrowEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A projected sword-arrow in flight: blade first, trailing red light. */
public class ProjectedArrowRenderer extends FxEntityRenderer<ProjectedArrowEntity> {

    public ProjectedArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void build(ProjectedArrowEntity e, State state, float partial, PoseStack pose, PortBuffers b, Vec3 toCamera) {
        Vec3 v = e.getDeltaMovement();
        Vec3 dir = v.lengthSqr() < 1.0E-6 ? Vec3.directionFromRotation(e.getXRot(), e.getYRot()).scale(-1.0) : v.normalize();
        Quaternionf rot = new Quaternionf().rotationTo(BLADE, new Vector3f((float)dir.x, (float)dir.y, (float)dir.z));
        setItem(e, state, e.weapon(), rot, Vec3.ZERO, 0.9f);
        if (v.lengthSqr() > 0.05) {
            Vec3 tail = dir.scale(-Math.min(3.5, v.length() * 1.2));
            FxDraw.ribbon(pose, b, tail, Vec3.ZERO, toCamera, 0.22f, FxDraw.argb(0, 0xE0302A), FxDraw.argb(190, 0xFF7A5A));
        }
    }
}
