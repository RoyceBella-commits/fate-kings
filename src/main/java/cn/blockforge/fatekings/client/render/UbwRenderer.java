package cn.blockforge.fatekings.client.render;

import cn.blockforge.fatekings.archer.ArcherRules;
import cn.blockforge.fatekings.client.FateClient;
import cn.blockforge.fatekings.entity.UbwEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Unlimited Blade Works. Inside: an orange sky closes overhead (an opaque dome over the real sky and
 * the land beyond the wall), giant bronze gears turn in it, and swords stand planted across the
 * ground as far as the wall. From outside: a bubble of fiery haze. The ring of fire that unfolds it
 * runs out across the ground first, and each sword appears as the fire passes.
 */
public class UbwRenderer extends FxEntityRenderer<UbwEntity> {
    private static final Vector3f BLADE = new Vector3f(1.0f, 1.0f, 0.0f).normalize();

    public UbwRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(UbwEntity entity, Frustum frustum, double x, double y, double z, float partial) {
        return entity.distanceToSqr(x, y, z) < 256.0 * 256.0;
    }

    @Override
    protected void build(UbwEntity e, State state, float partial, PoseStack pose, PortBuffers b, Vec3 toCamera) {
        boolean low = FateClient.prefs().lowFx();
        float open = e.openness(partial);
        if (open <= 0.0f) return;
        float R = e.radius();
        float spread = Math.min(1.0f, (e.life() + partial) / ArcherRules.UBW_UNFOLD);
        float r = R * spread;
        float t = e.tickCount + partial;
        boolean inside = toCamera.lengthSqr() < (double)r * r;
        // The sky of the marble.
        if (inside) {
            FxDraw.texSphere(pose, b, FxDraw.UBW_SKY, Vec3.ZERO, r, low ? 8 : 12, low ? 16 : 24, FxDraw.argb((int)(255 * open), 0xFFFFFF));
        } else {
            FxDraw.texSphere(pose, b, FxDraw.UBW_SKY, Vec3.ZERO, r, 8, 16, FxDraw.argb((int)(90 * open), 0xFFFFFF));
        }
        // The gears turning overhead.
        int gears = low ? 2 : 5;
        Random rnd = new Random(e.getUUID().getMostSignificantBits());
        for (int i = 0; i < gears; ++i) {
            double az = rnd.nextDouble() * Math.PI * 2.0;
            double el = Math.toRadians(45.0 + rnd.nextDouble() * 25.0);
            float size = 6.0f + rnd.nextFloat() * 4.0f;
            float speed = (rnd.nextBoolean() ? 1.0f : -1.0f) * (0.004f + rnd.nextFloat() * 0.006f);
            Vec3 dir = new Vec3(Math.cos(el) * Math.cos(az), Math.sin(el), Math.cos(el) * Math.sin(az));
            FxDraw.sprite(pose, b, FxDraw.UBW_GEAR, dir.scale(r * 0.85), dir, size * spread, t * speed * 6.0f, FxDraw.argb((int)(230 * open), 0xC88A4A));
        }
        // The ring of fire running out over the ground.
        if (spread < 1.0f || e.life() < ArcherRules.UBW_UNFOLD + 6) {
            float a = spread < 1.0f ? 1.0f : Math.max(0.0f, 1.0f - (e.life() + partial - ArcherRules.UBW_UNFOLD) / 6.0f);
            FxDraw.ring(pose, b, new Vec3(0.0, 0.15, 0.0), new Vec3(0, 1, 0), Math.max(0.0f, r - 1.5f), r + 0.5f, low ? 32 : 64,
                FxDraw.argb((int)(220 * a), 0xFF7A2A), FxDraw.argb(0, 0xFFC060));
            FxDraw.ring(pose, b, new Vec3(0.0, 0.8, 0.0), new Vec3(0, 1, 0), Math.max(0.0f, r - 0.8f), r + 0.2f, low ? 24 : 48,
                FxDraw.argb((int)(120 * a), 0xFFB040), FxDraw.argb(0, 0xFFB040));
        }
        plantedSwords(e, state, r, open, low);
    }

    /** Swords planted across the hill, each where the fire has passed; their places follow from the marble's id. */
    private void plantedSwords(UbwEntity e, State state, float r, float open, boolean low) {
        var level = Minecraft.getInstance().level;
        if (level == null || open < 0.2f) return;
        int n = low ? 40 : 120;
        Random rnd = new Random(e.getUUID().getLeastSignificantBits());
        Vec3 c = e.position();
        float R = e.radius();
        for (int i = 0; i < n; ++i) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            double d = Math.sqrt(rnd.nextDouble()) * R * 0.95;
            float tilt = (float)Math.toRadians(5.0 + rnd.nextDouble() * 20.0);
            float yaw = (float)(rnd.nextDouble() * Math.PI * 2.0);
            int slot = rnd.nextInt(UbwEntity.DISPLAY_SLOTS);
            float scale = 1.6f + rnd.nextFloat() * 0.8f;
            if (d > r) continue;
            double x = c.x + Math.cos(a) * d, z = c.z + Math.sin(a) * d;
            int bx = (int)Math.floor(x), bz = (int)Math.floor(z);
            if (!level.hasChunk(bx >> 4, bz >> 4)) continue;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            if (Math.abs(top - c.y) > 24.0) continue;
            Vec3 offset = new Vec3(x - c.x, top - c.y + 0.35 * scale, z - c.z);
            // Blade down into the ground, leaning a little, turned at random.
            Quaternionf rot = new Quaternionf().rotateY(yaw).rotateX(tilt).mul(new Quaternionf().rotationTo(BLADE, new Vector3f(0.0f, -1.0f, 0.0f)));
            addItem(e, state, e.display(slot), rot, offset, scale);
        }
    }
}
