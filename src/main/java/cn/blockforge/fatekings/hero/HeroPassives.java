package cn.blockforge.fatekings.hero;

import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.entity.StrikeAirEntity;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.KingState;
import cn.blockforge.fatekings.king.Skills;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * The King of Heroes' passives: the Autodefender discs and flight (double-tap jump), plus the
 * treasury-reorganisation lock checks shared by his treasures.
 */
public final class HeroPassives {
    public static final double AUTODEFENDER_RANGE = 20.0;
    /** Creative flight is 0.05; the king flies a little faster. */
    public static final float FLIGHT_SPEED = 0.08f;
    private static final Map<UUID, Long> AUTODEFENDER_UNTIL = new ConcurrentHashMap<>();

    private HeroPassives() {
    }

    public static void clear() {
        AUTODEFENDER_UNTIL.clear();
    }

    public static void tick(LivingEntity e, KingState s, long now) {
        if (!(e.level() instanceof ServerLevel level)) return;
        long until = AUTODEFENDER_UNTIL.getOrDefault(e.getUUID(), 0L);
        if (now >= until && e.getHealth() < e.getMaxHealth() * 0.5f && s.ready(Skills.AUTODEFENDER, now) && !s.reorganizing(now)) {
            until = now + KingRules.AUTODEFENDER_TIME;
            AUTODEFENDER_UNTIL.put(e.getUUID(), until);
            s.cooldown(Skills.AUTODEFENDER, now, KingRules.AUTODEFENDER);
            Fx.event(level, Fx.AUTODEFENDER, e, e.position(), KingRules.AUTODEFENDER_TIME, 1.0f, 96.0);
            level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 1.6f);
        }
        if (now < until && !s.reorganizing(now)) shootDown(level, e);
    }

    public static boolean autodefenderActive(LivingEntity e) {
        return e.level().getGameTime() < AUTODEFENDER_UNTIL.getOrDefault(e.getUUID(), 0L);
    }

    /** Thin golden lightning knocks down ordinary projectiles flying at the king (never the wind of Strike Air). */
    private static void shootDown(ServerLevel level, LivingEntity e) {
        Vec3 centre = e.getBoundingBox().getCenter();
        int shots = 0;
        for (Projectile p : level.getEntitiesOfClass(Projectile.class, e.getBoundingBox().inflate(AUTODEFENDER_RANGE),
                p -> p.isAlive() && p.getOwner() != e && !(p instanceof StrikeAirEntity))) {
            Vec3 to = centre.subtract(p.position());
            if (p.getDeltaMovement().lengthSqr() < 1.0E-3 || p.getDeltaMovement().dot(to) <= 0.0) continue;
            Vec3 from = centre.add(0.0, 0.8, 0.0);
            Fx.line(level, ParticleTypes.ELECTRIC_SPARK, from, p.position(), 0.6);
            Fx.particles(level, ParticleTypes.ELECTRIC_SPARK, p.position(), 12, 0.2, 0.2);
            Fx.particles(level, Fx.dust(Fx.GOLD, 1.0f), p.position(), 8, 0.2, 0.0);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.3f, 2.0f);
            p.discard();
            if (++shots >= 6) break;
        }
    }

    /**
     * Flight is a standing ability of the King of Heroes: while the golden set is worn he may always
     * fly (double-tap jump, as in creative mode). Vanilla takes "may fly" away on a game-mode switch,
     * a respawn or a relog, so it is given back every tick it is missing. Only a grant made here is
     * ever taken back, when the set comes off.
     */
    public static void tickFlight(ServerPlayer p, KingState s) {
        var abilities = p.getAbilities();
        boolean hero = s.king == KingRules.HERO && p.isAlive() && !p.isSpectator();
        if (hero) {
            boolean changed = false;
            if (!abilities.mayfly) {
                abilities.mayfly = true;
                s.flightGranted = true;
                changed = true;
            } else if (!p.isCreative()) {
                s.flightGranted = true;
            }
            if (abilities.getFlyingSpeed() != FLIGHT_SPEED) {
                abilities.setFlyingSpeed(FLIGHT_SPEED);
                changed = true;
            }
            if (changed) p.onUpdateAbilities();
        } else if (s.flightGranted) {
            s.flightGranted = false;
            if (!p.isCreative() && !p.isSpectator()) {
                abilities.mayfly = false;
                abilities.flying = false;
            }
            abilities.setFlyingSpeed(0.05f);
            p.onUpdateAbilities();
        }
        if (hero && abilities.flying && p.level().getGameTime() % 4 == 0) {
            Fx.particles(p.level(), Fx.dust(Fx.GOLD, 0.8f), p.getX(), p.getY() - 0.1, p.getZ(), 2, 0.25, 0.05, 0.25, 0.0);
        }
    }

    public static void forget(ServerPlayer p) {
        AUTODEFENDER_UNTIL.remove(p.getUUID());
    }
}
