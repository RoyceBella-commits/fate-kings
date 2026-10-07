package cn.blockforge.fatekings.client;

import cn.blockforge.fatekings.entity.UbwEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Inside a reality marble: embers drifting on the hot wind, and how strongly the orange light tints the view. */
public final class UbwClient {
    private static float inside;

    private UbwClient() {
    }

    /** 0..1: how much the camera is inside an open marble (for the screen tint). */
    public static float inside() {
        return inside;
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> tick(mc));
    }

    private static void tick(Minecraft mc) {
        inside = 0.0f;
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        UbwEntity marble = null;
        for (UbwEntity u : mc.level.getEntitiesOfClass(UbwEntity.class, new AABB(cam, cam).inflate(48.0), u -> true)) {
            if (cam.distanceToSqr(u.position()) <= u.radius() * u.radius()) {
                marble = u;
                break;
            }
        }
        if (marble == null) return;
        float open = marble.openness(0.0f);
        inside = open;
        if (open < 0.3f) return;
        boolean low = FateClient.prefs().lowFx();
        int n = low ? 1 : 4;
        var r = mc.level.getRandom();
        for (int i = 0; i < n; ++i) {
            double x = cam.x + (r.nextDouble() - 0.5) * 24.0, y = cam.y + (r.nextDouble() - 0.3) * 10.0, z = cam.z + (r.nextDouble() - 0.5) * 24.0;
            mc.level.addParticle(r.nextBoolean() ? ParticleTypes.SMALL_FLAME : ParticleTypes.ASH, x, y, z, 0.02, 0.03, 0.01);
        }
    }
}
