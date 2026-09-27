package cn.blockforge.fatekings.knight;

import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.entity.SwordQiEntity;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.KingState;
import cn.blockforge.fatekings.king.Kings;
import cn.blockforge.fatekings.net.FateNet;
import cn.blockforge.fatekings.npc.KingNpcEntity;
import cn.blockforge.fatekings.registry.FateItems;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Excalibur's swings: every stroke has an angle (from the mouse movement or the combo), everyone sees
 * the same stroke, and while the blade is revealed after Strike Air (or at Artoria's last stand) each
 * stroke cuts loose a golden crescent at that angle.
 */
public final class Slashes {
    /** The combo when the mouse hardly moves: sweep, backhand, down-right, rising, straight down. */
    public static final float[] COMBO = {0.0f, 180.0f, -45.0f, 90.0f, -90.0f};
    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();

    private Slashes() {
    }

    public static void clear() {
        LAST.clear();
    }

    /** From a knight's client: a swing at {@code roll} degrees. */
    public static void handle(ServerPlayer p, float roll, int combo) {
        if (!Kings.isKnight(p) || !p.getMainHandItem().is(FateItems.EXCALIBUR) || p.isUsingItem() || !Float.isFinite(roll)) return;
        long now = p.level().getGameTime();
        if (now - LAST.getOrDefault(p.getUUID(), -100L) < KingRules.SLASH_GAP) return;
        LAST.put(p.getUUID(), now);
        swing(p, roll, null);
    }

    /** A swing by a player or an NPC: shown to others, and a crescent while the blade is revealed. */
    public static void swing(LivingEntity e, float roll, LivingEntity target) {
        if (!(e.level() instanceof ServerLevel level)) return;
        broadcast(level, e, roll);
        if (revealedForQi(e)) {
            Vec3 origin = e.getEyePosition().add(e.getViewVector(1.0f).scale(1.0)).add(0.0, -0.25, 0.0);
            Vec3 dir = target != null ? target.getBoundingBox().getCenter().subtract(origin).normalize() : e.getViewVector(1.0f);
            SwordQiEntity.fire(level, e, origin, dir, roll);
            Fx.particles(level, Fx.dust(0xFFE38A, 1.2f), origin, 10, 0.4, 0.0);
        }
    }

    /** Revealed by Strike Air (not by mana depletion, which is the enemy's window), or Artoria's last stand. */
    public static boolean revealedForQi(LivingEntity e) {
        KingState s = Kings.of(e);
        if (s == null || !Kings.isKnight(e)) return false;
        long now = Kings.now(e);
        if (s.depleted(now)) return false;
        return now < s.revealedUntil || e instanceof KingNpcEntity npc && npc.lastStand();
    }

    private static void broadcast(ServerLevel level, LivingEntity e, float roll) {
        for (ServerPlayer p : level.players()) {
            if (p == e || p.distanceToSqr(e) > 64.0 * 64.0) continue;
            var buf = FateNet.buffer();
            buf.writeVarInt(e.getId());
            buf.writeFloat(roll);
            FateNet.send(p, FateNet.S2C_SWING, buf);
        }
    }
}
