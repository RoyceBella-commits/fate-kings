package cn.blockforge.fatekings.client;

import cn.blockforge.fatekings.client.render.FxDraw;
import cn.blockforge.fatekings.client.render.PortBuffers;
import cn.blockforge.fatekings.combat.Fx;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Visual events sent by the server ({@link Fx}): drawn in the world (labyrinth in the sky, pillar of
 * light, Avalon's dome, Autodefender discs, the clash orb) or on the screen (shake, flashes, sky
 * tints, Gojo's golden cracks, the arrival titles).
 */
public final class WorldFx {
    public record Event(int kind, int entityId, Vec3 pos, long start, int duration, float strength) {
        public float age(ClientLevel level, float partial) {
            return level.getGameTime() - this.start + partial;
        }

        public float progress(ClientLevel level, float partial) {
            return this.duration <= 0 ? 1.0f : Mth.clamp(age(level, partial) / this.duration, 0.0f, 1.0f);
        }
    }

    private static final List<Event> EVENTS = new ArrayList<>();
    private static List<PortBuffers.Batch> extracted = List.of();

    private WorldFx() {
    }

    public static synchronized void receive(FriendlyByteBuf buf) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        int kind = buf.readByte();
        int id = buf.readVarInt() - 1;
        Vec3 pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        int duration = buf.readVarInt();
        float strength = buf.readFloat();
        if (kind == Fx.PILLAR && duration == 0) {
            // The light goes out: end this caster's pillar.
            EVENTS.removeIf(e -> e.kind == Fx.PILLAR && e.entityId == id);
            return;
        }
        if (kind == Fx.PILLAR) EVENTS.removeIf(e -> e.kind == Fx.PILLAR && e.entityId == id);
        EVENTS.add(new Event(kind, id, pos, mc.level.getGameTime(), duration, strength));
        switch (kind) {
            case Fx.TITLE_HERO -> FateHud.title("fatekings.title.hero", 0xFFFFD34A, duration);
            case Fx.TITLE_KNIGHT -> FateHud.title("fatekings.title.knight", 0xFFBFE4FF, duration);
            default -> {
            }
        }
    }

    public static synchronized List<Event> active(int kind) {
        List<Event> out = new ArrayList<>();
        for (Event e : EVENTS) if (e.kind == kind) out.add(e);
        return out;
    }

    private static synchronized void prune(ClientLevel level) {
        long now = level.getGameTime();
        EVENTS.removeIf(e -> now - e.start > e.duration + 40);
    }

    public static void init() {
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> {
            synchronized (WorldFx.class) {
                EVENTS.clear();
            }
            extracted = List.of();
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> PortBuffers.submit(extracted, ctx.poseStack(), ctx.submitNodeCollector()));
        LevelExtractionEvents.END_EXTRACTION.register(ctx -> {
            ClientLevel level = ctx.level();
            prune(level);
            float partial = ctx.deltaTracker().getGameTimeDeltaPartialTick(false);
            Vec3 cam = ctx.camera().position();
            PortBuffers b = new PortBuffers();
            PoseStack pose = new PoseStack();
            List<Event> copy;
            synchronized (WorldFx.class) {
                copy = List.copyOf(EVENTS);
            }
            boolean low = FateClient.prefs().lowFx();
            for (Event e : copy) {
                Entity anchor = e.entityId >= 0 ? level.getEntity(e.entityId) : null;
                Vec3 at = anchor != null ? anchor.getPosition(partial) : e.pos;
                float age = e.age(level, partial);
                if (age > e.duration + 12) continue;
                Vec3 rel = at.subtract(cam);
                switch (e.kind) {
                    case Fx.LABYRINTH -> labyrinth(pose, b, rel, age, e.duration, low);
                    case Fx.PILLAR -> pillar(pose, b, rel, anchor, age, cam.subtract(at), low);
                    case Fx.DOME -> dome(pose, b, rel, age, e.duration);
                    case Fx.AUTODEFENDER -> discs(pose, b, rel, age, e.duration);
                    case Fx.CLASH -> clashOrb(pose, b, rel, age, e.duration, cam.subtract(at));
                    case Fx.ARRIVAL_HERO -> arrival(pose, b, rel, age, 0xFFD34A);
                    case Fx.ARRIVAL_KNIGHT -> arrival(pose, b, rel, age, 0xBFE4FF);
                    case Fx.ARMOR_SCATTER -> scatter(pose, b, rel, age);
                    default -> {
                    }
                }
            }
            extracted = b.snapshot();
        });
    }

    /** Bab-ilu: a vast red labyrinth spreads over the sky (radius 64), then shrinks into a sphere of light. */
    private static void labyrinth(PoseStack pose, PortBuffers b, Vec3 at, float age, int duration, boolean low) {
        float spreadEnd = duration - 12;
        float r;
        float y;
        if (age < 8) {
            r = 64.0f * (age / 8.0f);
            y = 30.0f;
        } else if (age < spreadEnd) {
            r = 64.0f;
            y = 30.0f;
        } else {
            float k = Math.min(1.0f, (age - spreadEnd) / 10.0f);
            r = 64.0f * (1.0f - k) + 0.6f * k;
            y = 30.0f * (1.0f - k) + 1.8f * k;
        }
        Vec3 c = at.add(0.0, y, 0.0);
        float spin = age * 0.01f;
        int alpha = low ? 150 : 230;
        FxDraw.sprite(pose, b, FxDraw.LABYRINTH, c, new Vec3(0, 1, 0), r, spin, FxDraw.argb(alpha, 0xFFFFFF));
        if (!low) FxDraw.sprite(pose, b, FxDraw.LABYRINTH, c.add(0.0, -0.4, 0.0), new Vec3(0, 1, 0), r * 0.7f, -spin * 1.6f, FxDraw.argb(140, 0xFFFFFF));
        if (age >= spreadEnd - 2) FxDraw.sphere(pose, b, c, 0.6f, 6, 10, FxDraw.argb(200, 0xFF2A2A));
    }

    /** Excalibur gathering light: a pillar of gold from the sword to the clouds (and a cut in them). */
    private static void pillar(PoseStack pose, PortBuffers b, Vec3 at, Entity anchor, float age, Vec3 toCamera, boolean low) {
        float grow = Math.min(1.0f, age / 20.0f);
        Vec3 base = at.add(0.0, anchor != null ? anchor.getBbHeight() + 1.2 : 3.0, 0.0);
        float height = 40.0f + 200.0f * grow;
        float pulse = 0.85f + 0.15f * (float)Math.sin(age * 0.5);
        FxDraw.tube(pose, b, base, base.add(0.0, height, 0.0), 0.35f * pulse, 0.9f, 10, FxDraw.argb(230, 0xFFF7DA), FxDraw.argb(0, 0xFFE38A), 0.0f);
        FxDraw.tube(pose, b, base, base.add(0.0, height * 0.7, 0.0), 0.9f * pulse, 1.6f, 10, FxDraw.argb(90, 0xFFE38A), FxDraw.argb(0, 0xFFE38A), 0.0f);
        if (!low && grow >= 1.0f) {
            // The cloud layer cut open: a thin bright slit at cloud height.
            Vec3 cloud = new Vec3(base.x, 192.0 - Minecraft.getInstance().gameRenderer.mainCamera().position().y, base.z);
            FxDraw.quad(pose, b, cloud.add(-60, 0, -1.2), cloud.add(60, 0, -1.2), cloud.add(60, 0, 1.2), cloud.add(-60, 0, 1.2),
                FxDraw.argb(0, 0xFFF7DA), FxDraw.argb(0, 0xFFF7DA), FxDraw.argb(160, 0xFFF7DA), FxDraw.argb(160, 0xFFF7DA));
        }
    }

    /** Avalon unfolds: petals of gold-blue light close into a translucent dome, then scatter. */
    private static void dome(PoseStack pose, PortBuffers b, Vec3 at, float age, int duration) {
        float open = Math.min(1.0f, age / 6.0f);
        float close = age > duration - 8 ? Math.max(0.0f, (duration - age) / 8.0f) : 1.0f;
        float vis = Math.min(open, close);
        Vec3 c = at.add(0.0, 1.0, 0.0);
        float r = 1.8f + 0.2f * vis;
        int petals = 64;
        for (int i = 0; i < petals; ++i) {
            double y = 1.0 - (i + 0.5) / petals * 1.6;
            double rr = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double a = i * 2.39996 + age * 0.03;
            Vec3 dir = new Vec3(Math.cos(a) * rr, y, Math.sin(a) * rr);
            float dist = r * (vis + (1.0f - vis) * 2.5f);
            FxDraw.sprite(pose, b, FxDraw.SHARD, c.add(dir.scale(dist)), dir, 0.45f, (float)a, FxDraw.argb((int)(200 * vis), 0xFFFFFF));
        }
        FxDraw.sphere(pose, b, c, r, 10, 16, FxDraw.argb((int)(40 * vis), 0xBFD8FF));
    }

    /** Three golden discs circling the King of Heroes. */
    private static void discs(PoseStack pose, PortBuffers b, Vec3 at, float age, int duration) {
        float vis = Math.min(1.0f, Math.min(age / 6.0f, Math.max(0.0f, (duration - age) / 10.0f)));
        for (int i = 0; i < 3; ++i) {
            double a = age * 0.12 + i * Math.PI * 2 / 3;
            Vec3 c = at.add(Math.cos(a) * 1.6, 1.3 + Math.sin(age * 0.2 + i) * 0.3, Math.sin(a) * 1.6);
            Vec3 n = new Vec3(Math.cos(a), 0.4, Math.sin(a));
            FxDraw.sprite(pose, b, FxDraw.RIPPLE, c, n, 0.45f, age * 0.3f, FxDraw.argb((int)(230 * vis), 0xFFFFFF));
            FxDraw.ring(pose, b, c, n, 0.4f, 0.5f, 16, FxDraw.argb((int)(200 * vis), 0xFFD34A), FxDraw.argb(0, 0xFFD34A));
        }
    }

    /** The clash point: a white ball of energy between the red and the gold. */
    private static void clashOrb(PoseStack pose, PortBuffers b, Vec3 at, float age, int duration, Vec3 toCamera) {
        float grow = Math.min(1.0f, age / 10.0f);
        float r = 5.0f * grow + (float)Math.sin(age * 0.9) * 0.4f;
        FxDraw.sphere(pose, b, at, r, 10, 16, FxDraw.argb(170, 0xFFFFFF));
        FxDraw.sphere(pose, b, at, r * 1.4f, 10, 16, FxDraw.argb(60, 0xFFF0C0));
        FxDraw.ring(pose, b, at, toCamera, r * 1.2f, r * 2.2f, 32, FxDraw.argb(120, 0xFFE38A), FxDraw.argb(0, 0xFF3A2A));
    }

    /** Putting on the full set: a ring of gold ripples (hero) or a pale-blue whirlwind (knight). */
    private static void arrival(PoseStack pose, PortBuffers b, Vec3 at, float age, int rgb) {
        if (age > 30) return;
        float k = age / 30.0f;
        FxDraw.ring(pose, b, at.add(0.0, 0.1 + k * 1.5, 0.0), new Vec3(0, 1, 0), 0.4f + k * 2.5f, 0.6f + k * 2.8f, 32,
            FxDraw.argb((int)(200 * (1 - k)), rgb), FxDraw.argb(0, rgb));
    }

    /** Avalon's counter: the armour scatters into motes of light for a moment (visual only). */
    private static void scatter(PoseStack pose, PortBuffers b, Vec3 at, float age) {
        if (age > 20) return;
        float k = age / 20.0f;
        for (int i = 0; i < 24; ++i) {
            double a = i * 2.39996;
            double y = (i % 6) * 0.3;
            Vec3 p = at.add(Math.cos(a) * (0.4 + k * 1.2), 0.3 + y + k * 0.8, Math.sin(a) * (0.4 + k * 1.2));
            FxDraw.sprite(pose, b, FxDraw.SHARD, p, new Vec3(Math.cos(a), 0, Math.sin(a)), 0.18f, (float)a, FxDraw.argb((int)(230 * (1 - k)), 0xFFFFFF));
        }
    }

    // ---- Screen effects read by the HUD and the camera ----

    /** Camera shake strength now (0 = none). */
    public static float shake(ClientLevel level, Vec3 eye, float partial) {
        float s = 0.0f;
        for (Event e : active(Fx.SHAKE)) {
            float p = e.progress(level, partial);
            if (p >= 1.0f) continue;
            double d = eye.distanceTo(e.pos);
            s = Math.max(s, e.strength * (1.0f - p) * (float)Math.max(0.0, 1.0 - d / 160.0));
        }
        for (Event e : active(Fx.CLASH)) {
            if (e.progress(level, partial) < 1.0f) s = Math.max(s, 0.8f * (float)Math.max(0.0, 1.0 - eye.distanceTo(e.pos) / 200.0));
        }
        return s;
    }

    /** White (or gold) flash opacity now. */
    public static float flash(ClientLevel level, float partial, int kind) {
        float f = 0.0f;
        for (Event e : active(kind)) {
            float p = e.progress(level, partial);
            if (p < 1.0f) f = Math.max(f, e.strength * (1.0f - p));
        }
        return f;
    }

    /** Sky darkening (Gate of Babylon volleys, Ea) now. */
    public static float skyDim(ClientLevel level, float partial) {
        float d = 0.0f;
        for (Event e : active(Fx.SKY_DIM)) {
            float p = e.progress(level, partial);
            if (p < 1.0f) d = Math.max(d, e.strength * Math.min(1.0f, (1.0f - p) * 3.0f));
        }
        return d;
    }

    public static Event latest(int kind) {
        List<Event> l = active(kind);
        return l.isEmpty() ? null : l.get(l.size() - 1);
    }

    public static synchronized void cleanup() {
        Iterator<Event> it = EVENTS.iterator();
        while (it.hasNext()) if (it.next().duration < 0) it.remove();
    }
}
