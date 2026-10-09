package cn.blockforge.fatekings.client.render;

import cn.blockforge.fatekings.entity.UbwSwordEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A sword of the hill. Rising: a ring of embers at its foot as it comes up blade-down. Hanging: its
 * point turned on the foe, a glint running round it. Loosed: a long white-hot streak edged with
 * fire. Driven into the ground: it stands, then shrinks away to nothing as it crumbles.
 */
public class UbwSwordRenderer extends FxEntityRenderer<UbwSwordEntity> {

    public UbwSwordRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void build(UbwSwordEntity e, State state, float partial, PoseStack pose, PortBuffers b, Vec3 toCamera) {
        Vec3 dir = e.aim();
        int phase = e.phase();
        float age = e.age() + partial;
        float scale = 1.35f;
        if (phase == UbwSwordEntity.STUCK) {
            // It crumbles over its last few ticks.
            float left = UbwSwordEntity.STUCK_TIME - (age - stuckSince(e, age));
            scale *= Math.max(0.0f, Math.min(1.0f, left / 6.0f));
        }
        Quaternionf rot = new Quaternionf().rotationTo(BLADE, new Vector3f((float)dir.x, (float)dir.y, (float)dir.z));
        setItem(e, state, e.weapon(), rot, Vec3.ZERO, scale);
        switch (phase) {
            case UbwSwordEntity.RISING -> {
                // The ring of embers where it is drawn out of the ground.
                float t = Math.min(1.0f, age / UbwSwordEntity.RISE);
                Vec3 foot = e.seenAt().subtract(e.position()).add(0.0, 1.25, 0.0);
                FxDraw.ring(pose, b, foot, new Vec3(0, 1, 0), 0.2f + 0.6f * t, 0.6f + 1.0f * t, 16,
                    FxDraw.argb((int)(200 * (1.0f - t)), 0xFFB060), FxDraw.argb(0, 0xFF6A20));
                FxDraw.ribbon(pose, b, foot, Vec3.ZERO, toCamera, 0.25f, FxDraw.argb(0, 0xFF7A2A), FxDraw.argb((int)(120 * (1.0f - t)), 0xFFB060));
            }
            case UbwSwordEntity.AIMING -> {
                // A glint circling the blade as it takes aim.
                float pulse = 0.5f + 0.5f * (float)Math.sin(age * 1.3f);
                FxDraw.ring(pose, b, dir.scale(-0.2), dir, 0.15f, 0.45f + 0.15f * pulse, 12,
                    FxDraw.argb((int)(150 + 80 * pulse), 0xFFE0B0), FxDraw.argb(0, 0xFF8A2A));
            }
            case UbwSwordEntity.FLYING -> {
                double len = 7.0;
                FxDraw.ribbon(pose, b, dir.scale(-len), Vec3.ZERO, toCamera, 0.55f, FxDraw.argb(0, 0xFF5A1A), FxDraw.argb(170, 0xFF9A40));
                FxDraw.ribbon(pose, b, dir.scale(-len * 0.6), Vec3.ZERO, toCamera, 0.18f, FxDraw.argb(0, 0xFFF0D0), FxDraw.argb(230, 0xFFF6E0));
            }
            default -> {
                float glow = Math.max(0.0f, 1.0f - (age - stuckSince(e, age)) / 10.0f);
                if (glow > 0.0f) {
                    FxDraw.ring(pose, b, dir.scale(0.5), new Vec3(0, 1, 0), 0.1f, 0.9f, 12, FxDraw.argb((int)(180 * glow), 0xFFB060), FxDraw.argb(0, 0xFF6A20));
                }
            }
        }
    }

    /** When it was driven in, as far as this client knows (tracked on the entity's first stuck frame). */
    private static float stuckSince(UbwSwordEntity e, float age) {
        return e.stuckSeen(age);
    }
}
