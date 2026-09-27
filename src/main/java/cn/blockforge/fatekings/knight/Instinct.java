package cn.blockforge.fatekings.knight;

import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.compat.JjkCompat;
import cn.blockforge.fatekings.entity.TreasureProjectile;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.Kings;
import cn.blockforge.fatekings.net.FateNet;
import cn.blockforge.fatekings.npc.ArtoriaEntity;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Instinct A: sees ballistics (50% of vanilla projectiles, 35% of single Gate of Babylon shots are
 * side-stepped) and senses ultimates being prepared within 64 blocks (an arrow at the screen edge;
 * a warning only, never an immunity).
 */
public final class Instinct {
    public static final double WARN_RANGE = 64.0;
    private static final Map<UUID, Long> ANNOUNCED = new ConcurrentHashMap<>();

    private Instinct() {
    }

    public static void clear() {
        ANNOUNCED.clear();
    }

    /** An ultimate is being prepared by {@code source}: every knight within 64 blocks is warned. */
    public static void announce(ServerLevel level, Entity source, String key, int duration) {
        for (ServerPlayer p : level.players()) {
            if (p == source || !Kings.isKnight(p) || p.distanceToSqr(source) > WARN_RANGE * WARN_RANGE) continue;
            var buf = FateNet.buffer();
            buf.writeVarInt(source.getId());
            buf.writeDouble(source.getX());
            buf.writeDouble(source.getY() + source.getBbHeight() * 0.5);
            buf.writeDouble(source.getZ());
            buf.writeUtf(key, 48);
            buf.writeVarInt(duration);
            FateNet.send(p, FateNet.S2C_WARN, buf);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.5f, 2.0f);
        }
        for (ArtoriaEntity a : level.getEntitiesOfClass(ArtoriaEntity.class, new AABB(source.blockPosition()).inflate(WARN_RANGE))) {
            if (a != source) a.onWarning(source, key);
        }
    }

    /** Gojo x Sukuna ultimates: charging players (polled) and freshly spawned domains / Purple / World Cut. */
    public static void tickJjk(MinecraftServer server) {
        if (!JjkCompat.LOADED || server.getTickCount() % 10 != 0) return;
        long now = server.overworld().getGameTime();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String skill = JjkCompat.chargingSkill(p);
            String key = skill == null ? null : switch (skill) {
                case "WORLD_CUT" -> "fatekings.warn.world_cut";
                case "MURASAKI" -> "fatekings.warn.murasaki";
                case "DOMAIN" -> "fatekings.warn.shrine";
                case "VOID" -> "fatekings.warn.void";
                default -> null;
            };
            if (key == null || now < ANNOUNCED.getOrDefault(p.getUUID(), 0L)) continue;
            ANNOUNCED.put(p.getUUID(), now + 60);
            announce(p.level(), p, key, 60);
        }
    }

    public static void onEntityLoad(Entity e, ServerLevel level) {
        if (!JjkCompat.LOADED || !JjkCompat.fromJjk(e)) return;
        String key = JjkCompat.is(e, JjkCompat.UNLIMITED_VOID) ? "fatekings.warn.void"
            : JjkCompat.is(e, JjkCompat.SHRINE) ? "fatekings.warn.shrine"
            : JjkCompat.is(e, JjkCompat.MURASAKI) ? "fatekings.warn.murasaki" : null;
        if (key != null) announce(level, e, key, 40);
    }

    /** Whether a knight side-steps this hit. */
    public static boolean dodge(LivingEntity target, DamageSource source) {
        Entity direct = source.getDirectEntity();
        if (!(direct instanceof Projectile)) return false;
        float chance;
        if (direct instanceof TreasureProjectile tp) chance = tp.single() ? KingRules.DODGE_TREASURE : 0.0f;
        else chance = "minecraft".equals(JjkCompat.typeId(direct).getNamespace()) ? KingRules.DODGE_VANILLA : 0.0f;
        if (chance <= 0.0f || target.getRandom().nextFloat() >= chance) return false;
        if (!(target.level() instanceof ServerLevel level)) return false;
        Vec3 v = direct.getDeltaMovement();
        Vec3 side = new Vec3(-v.z, 0.0, v.x);
        side = side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
        if (target.getRandom().nextBoolean()) side = side.scale(-1.0);
        Vec3 before = target.position();
        for (double d = 1.2; d >= 0.4; d -= 0.4) {
            Vec3 off = side.scale(d);
            if (level.noCollision(target, target.getBoundingBox().move(off))) {
                target.teleportTo(before.x + off.x, before.y, before.z + off.z);
                break;
            }
        }
        // A pale-cyan afterimage where she stood.
        Fx.particles(level, Fx.dust(0xDDF4FF, 1.4f), before.x, before.y + 1.0, before.z, 24, 0.25, 0.7, 0.25, 0.0);
        Fx.particles(level, ParticleTypes.CLOUD, before.x, before.y + 1.0, before.z, 6, 0.2, 0.5, 0.2, 0.01);
        level.playSound(null, before.x, before.y, before.z, SoundEvents.BREEZE_DEFLECT, SoundSource.PLAYERS, 0.8f, 1.5f);
        return true;
    }
}
