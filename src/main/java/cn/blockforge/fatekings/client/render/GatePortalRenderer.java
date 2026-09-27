package cn.blockforge.fatekings.client.render;

import cn.blockforge.fatekings.entity.GatePortalEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A golden ripple in the air; the treasure's hilt pokes out, then it turns and bares the blade. */
public class GatePortalRenderer extends FxEntityRenderer<GatePortalEntity> {
    private static final Vector3f BLADE = new Vector3f(1.0f, 1.0f, 0.0f).normalize();

    public GatePortalRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void build(GatePortalEntity e, State state, float partial, PoseStack pose, PortBuffers b, Vec3 toCamera) {
        float open = e.openness(partial);
        if (open <= 0.01f) return;
        Vec3 facing = e.facing();
        float t = (e.tickCount + partial) * 0.08f;
        float r = 0.9f * e.size() * open;
        FxDraw.sprite(pose, b, FxDraw.RIPPLE, Vec3.ZERO, facing, r, t, FxDraw.argb((int)(235 * open), 0xFFFFFF));
        FxDraw.ring(pose, b, facing.scale(0.02), facing, r * 0.92f, r * 1.02f, 24, FxDraw.argb((int)(160 * open), 0xFFE58A), FxDraw.argb(0, 0xFFD34A));
        if (!e.weaponOut(partial)) return;
        float out = e.emergence(partial);
        float turn = e.turn(partial);
        // Hilt towards the foe while waiting (blade inside the ripple), then a half turn.
        Vec3 point = facing.scale(-1.0 + 2.0 * turn);
        Quaternionf rot = new Quaternionf().rotationTo(BLADE, new Vector3f((float)point.x, (float)point.y, (float)point.z));
        Vec3 offset = facing.scale(-0.35 + 0.55 * out);
        setItem(e, state, e.weapon(), rot, offset, 1.1f);
    }
}
