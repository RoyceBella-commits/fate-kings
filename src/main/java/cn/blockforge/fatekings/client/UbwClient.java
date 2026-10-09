package cn.blockforge.fatekings.client;

import cn.blockforge.fatekings.archer.ArcherRules;
import cn.blockforge.fatekings.entity.UbwEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Inside a reality marble: embers drifting on the hot wind, how strongly the orange light tints the
 * view, and whether the world's own terrain gives way to the marble's plain (once it is fully open,
 * with a brief flare of firelight as it does).
 */
public final class UbwClient {
    private static float inside;
    private static boolean hide;
    /** When the terrain last gave way (nanoTime), for the flare; 0 = never. */
    private static long enteredAt;
    private static final float VEIL_SECONDS = 0.7f;

    private UbwClient() {
    }

    /** 0..1: how much the camera is inside an open marble (for the screen tint). */
    public static float inside() {
        return inside;
    }

    /** Whether the terrain is hidden (the camera is inside a fully open marble, on its side of any seam). */
    public static boolean hidesTerrain() {
        return hide;
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> tick(mc));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> {
            hide = false;
            enteredAt = 0L;
        });
        // The firelight flaring as the ground gives way, so the world does not simply blink out.
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addFirst(cn.blockforge.fatekings.FateKings.id("ubw_veil"), (ctx, tracker) -> {
            float alpha = veil();
            if (alpha > 0.004f) ctx.fill(0, 0, ctx.guiWidth(), ctx.guiHeight(), (int)(alpha * 255.0f) << 24 | 0xFFB060);
        });
    }

    private static float veil() {
        if (enteredAt == 0L) return 0.0f;
        float t = (System.nanoTime() - enteredAt) / 1.0E9f / VEIL_SECONDS;
        if (t >= 1.0f) return 0.0f;
        return 0.8f * (1.0f - t) * (1.0f - t);
    }

    private static void setHide(boolean now) {
        if (now && !hide) enteredAt = System.nanoTime();
        hide = now;
    }

    private static void tick(Minecraft mc) {
        inside = 0.0f;
        if (mc.level == null || mc.player == null) {
            setHide(false);
            return;
        }
        if (mc.isPaused()) return;
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        UbwEntity marble = null;
        for (UbwEntity u : mc.level.getEntitiesOfClass(UbwEntity.class, new AABB(cam, cam).inflate(80.0), u -> true)) {
            if (cam.distanceToSqr(u.position()) > u.radius() * u.radius()) continue;
            // On the other domain's side of a seam the marble is not overhead.
            var seam = cn.blockforge.fatekings.client.render.DomainSeam.forMarble(u, 0.0f);
            if (seam != null && !seam.keeps(cam.subtract(u.position()))) continue;
            marble = u;
            break;
        }
        // The ground goes once the fire has run out to the wall, and comes back as the marble closes.
        setHide(marble != null && marble.openness(0.0f) >= 0.9f && marble.life() >= ArcherRules.UBW_UNFOLD
            && cam.distanceToSqr(marble.position()) < marble.visualRadius(0.0f) * marble.visualRadius(0.0f));
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
