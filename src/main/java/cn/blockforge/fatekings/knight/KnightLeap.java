package cn.blockforge.fatekings.knight;

import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.KingState;
import cn.blockforge.fatekings.king.Kings;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Artoria's jump is a Mana Burst leap with the feel of Winston's jump pack: a high arc along the view
 * (space alone, or W + space; look up to leap up onto things), or along the ground towards S / A / D.
 * The fall is softened on the client, and a long leap lands with a heavy thud (dust, a burst of
 * wind, a low boom and a small shake). It does no damage. One more leap is allowed in the air;
 * landing restores it. Sneak + jump is an ordinary jump.
 */
public final class KnightLeap {
    private static final int MIN_GAP = 6;
    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();
    private static final Set<UUID> AIR_USED = ConcurrentHashMap.newKeySet();
    /** Leaps in flight: when they began (game time). */
    private static final Map<UUID, Long> IN_FLIGHT = new ConcurrentHashMap<>();

    private KnightLeap() {
    }

    public static void clear() {
        LAST.clear();
        AIR_USED.clear();
        IN_FLIGHT.clear();
    }

    /** From the client: jump was pressed with these movement inputs. */
    public static void handle(ServerPlayer p, float forward, float strafe, boolean clientOnGround) {
        if (!Kings.isKnight(p) || !p.isAlive() || p.isSpectator() || p.isPassenger() || p.isFallFlying() || p.getAbilities().flying) return;
        if (KnightPassives.chained(p)) {
            Kings.refuse(p, "fatekings.hint.chained");
            return;
        }
        KingState s = Kings.of(p);
        long now = p.level().getGameTime();
        if (now - LAST.getOrDefault(p.getUUID(), -100L) < MIN_GAP || s.depleted(now)) return;
        boolean ground = clientOnGround || p.onGround();
        if (!ground) {
            if (!AIR_USED.add(p.getUUID())) return;
        }
        LAST.put(p.getUUID(), now);
        leap(p, forward, strafe, ground);
    }

    /** Per-tick landing check for players and NPCs. */
    public static void tick(LivingEntity e) {
        if (e.onGround()) AIR_USED.remove(e.getUUID());
        Long start = IN_FLIGHT.get(e.getUUID());
        if (start == null) return;
        long airtime = e.level().getGameTime() - start;
        if (airtime > 200 || !e.isAlive()) {
            IN_FLIGHT.remove(e.getUUID());
        } else if (airtime > 2 && (e.onGround() || e.isInWater() && airtime > 4)) {
            IN_FLIGHT.remove(e.getUUID());
            if (airtime >= KingRules.LEAP_SLAM_AIRTIME && e.level() instanceof ServerLevel level) land(level, e);
        }
    }

    public static boolean hasInput(float forward, float strafe) {
        return Math.abs(forward) > 0.05f || Math.abs(strafe) > 0.05f;
    }

    /** Horizontal direction of the movement keys relative to the facing (strafe > 0 is left). */
    public static Vec3 direction(float yaw, float forward, float strafe) {
        Vec3 f = Vec3.directionFromRotation(0.0f, yaw);
        Vec3 left = new Vec3(f.z, 0.0, -f.x);
        Vec3 d = f.scale(forward).add(left.scale(strafe));
        return d.lengthSqr() < 0.01 ? f : d.normalize();
    }

    /** W (or no key): along the view in 3D; S / A / D: along the ground that way. */
    public static void leap(LivingEntity e, float forward, float strafe, boolean ground) {
        Vec3 look = e.getViewVector(1.0f);
        double[] keyDir = null;
        if (hasInput(forward, strafe) && forward <= 0.05f) {
            Vec3 d = direction(e.getYRot(), forward, strafe);
            keyDir = new double[]{d.x, 0.0, d.z};
        }
        double[] v = KingRules.leapVelocity(new double[]{look.x, look.y, look.z}, keyDir, ground);
        Vec3 velocity = new Vec3(v[0], v[1], v[2]);
        launch(e, velocity);
        IN_FLIGHT.put(e.getUUID(), e.level().getGameTime());
        if (e.level() instanceof ServerLevel level) {
            Vec3 feet = e.position();
            Vec3 dir = velocity.normalize();
            Vec3 back = feet.subtract(dir.scale(0.4)).add(0.0, 0.8, 0.0);
            Fx.particles(level, Fx.dust(Fx.MANA, 1.2f), back.x, back.y, back.z, 14, 0.25, 0.4, 0.25, 0.0);
            Fx.particles(level, ParticleTypes.SOUL_FIRE_FLAME, back.x, back.y, back.z, 8, 0.2, 0.3, 0.2, 0.02);
            Fx.particles(level, ParticleTypes.CLOUD, feet.x, feet.y + 0.1, feet.z, 14, 0.5, 0.05, 0.5, 0.06);
            Fx.ring(level, feet.add(0.0, 0.1, 0.0), Fx.WIND, 1.2, 20);
            level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 1.0f, 0.7f);
            level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.5f, 1.5f);
        }
    }

    /** The heavy landing: purely a show of force, no damage and no knockback. */
    private static void land(ServerLevel level, LivingEntity e) {
        Vec3 feet = e.position();
        BlockState below = level.getBlockState(BlockPos.containing(feet.x, feet.y - 0.2, feet.z));
        if (!below.isAir()) {
            BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, below);
            for (int i = 0; i < 24; ++i) {
                double a = i * Math.PI * 2 / 24;
                Fx.particles(level, dust, feet.x + Math.cos(a) * 1.8, feet.y + 0.1, feet.z + Math.sin(a) * 1.8, 3, 0.2, 0.05, 0.2, 0.15);
            }
        }
        Fx.particles(level, ParticleTypes.CLOUD, feet.x, feet.y + 0.2, feet.z, 20, 1.2, 0.05, 1.2, 0.05);
        Fx.particles(level, ParticleTypes.GUST_EMITTER_SMALL, feet.x, feet.y + 0.3, feet.z, 1, 0.0, 0.0, 0.0, 0.0);
        Fx.ring(level, feet.add(0.0, 0.15, 0.0), Fx.MANA, 2.2, 32);
        level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.2f, 0.7f);
        level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.GENERIC_BIG_FALL, SoundSource.PLAYERS, 0.8f, 0.6f);
        Fx.event(level, Fx.SHAKE, e, feet, 10, 0.45f, 12.0);
    }

    public static void launch(LivingEntity e, Vec3 velocity) {
        e.setDeltaMovement(velocity);
        e.needsSync = true;
        e.resetFallDistance();
        if (e instanceof ServerPlayer sp) sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
    }
}
