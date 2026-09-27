package cn.blockforge.fatekings.client.render;

import cn.blockforge.fatekings.entity.VimanaEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

/** Vimana: a golden throne on a golden hull with emerald wings of light. */
public class VimanaRenderer extends FxEntityRenderer<VimanaEntity> {
    private static ModelPart model;

    public VimanaRenderer(EntityRendererProvider.Context context) {
        super(context);
        if (model == null) model = build();
    }

    /** Built in code (units of 1/16 block; y up). Gold on the top half of the texture, emerald below, red for the cushion. */
    private static ModelPart build() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("hull", CubeListBuilder.create().texOffs(0, 0).addBox(-16.0f, 0.0f, -10.0f, 32.0f, 4.0f, 20.0f), PartPose.ZERO);
        root.addOrReplaceChild("keel", CubeListBuilder.create().texOffs(0, 8).addBox(-12.0f, -3.0f, -6.0f, 24.0f, 3.0f, 12.0f), PartPose.ZERO);
        root.addOrReplaceChild("prow", CubeListBuilder.create().texOffs(0, 16).addBox(-4.0f, 1.0f, -16.0f, 8.0f, 4.0f, 6.0f), PartPose.ZERO);
        root.addOrReplaceChild("seat", CubeListBuilder.create().texOffs(0, 50).addBox(-6.0f, 4.0f, -2.0f, 12.0f, 3.0f, 10.0f), PartPose.ZERO);
        root.addOrReplaceChild("back", CubeListBuilder.create().texOffs(0, 0).addBox(-7.0f, 4.0f, 8.0f, 14.0f, 20.0f, 3.0f), PartPose.ZERO);
        root.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(0, 52).addBox(-2.0f, 24.0f, 8.5f, 4.0f, 4.0f, 2.0f), PartPose.ZERO);
        root.addOrReplaceChild("arm_l", CubeListBuilder.create().texOffs(0, 20).addBox(6.0f, 4.0f, -2.0f, 3.0f, 7.0f, 10.0f), PartPose.ZERO);
        root.addOrReplaceChild("arm_r", CubeListBuilder.create().texOffs(0, 20).addBox(-9.0f, 4.0f, -2.0f, 3.0f, 7.0f, 10.0f), PartPose.ZERO);
        root.addOrReplaceChild("wing_l", CubeListBuilder.create().texOffs(0, 34).addBox(0.0f, 0.0f, -6.0f, 26.0f, 1.0f, 12.0f),
            PartPose.offsetAndRotation(16.0f, 2.0f, 2.0f, 0.0f, 0.0f, 0.25f));
        root.addOrReplaceChild("wing_r", CubeListBuilder.create().texOffs(0, 34).addBox(-26.0f, 0.0f, -6.0f, 26.0f, 1.0f, 12.0f),
            PartPose.offsetAndRotation(-16.0f, 2.0f, 2.0f, 0.0f, 0.0f, -0.25f));
        return root.bake(64, 64);
    }

    @Override
    protected void build(VimanaEntity e, State state, float partial, PoseStack pose, PortBuffers b, Vec3 toCamera) {
        pose.pushPose();
        pose.rotateDegrees(Axis.YP, -e.getYRot(partial) + 180.0f);
        pose.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);
        model.render(pose, b.getBuffer(RenderTypes.entityCutout(FxDraw.VIMANA)), FxDraw.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        float t = e.tickCount + partial;
        float glow = 0.5f + 0.25f * (float)Math.sin(t * 0.3);
        Vec3 fwd = Vec3.directionFromRotation(0.0f, e.getYRot(partial));
        Vec3 side = new Vec3(-fwd.z, 0.0, fwd.x);
        for (int s = -1; s <= 1; s += 2) {
            // Emerald light wings.
            Vec3 root = side.scale(s * 1.0).add(0.0, 0.15, 0.0);
            Vec3 tip = side.scale(s * 3.0).add(0.0, 0.7, 0.0).add(fwd.scale(-0.8));
            FxDraw.quad(pose, b, root.add(fwd.scale(0.6)), tip.add(fwd.scale(0.3)), tip.add(fwd.scale(-0.8)), root.add(fwd.scale(-0.8)),
                FxDraw.alpha(glow, 0x7CF0B4), FxDraw.alpha(0.1f, 0x1FB873), FxDraw.alpha(0.05f, 0x1FB873), FxDraw.alpha(glow, 0x7CF0B4));
        }
    }
}
