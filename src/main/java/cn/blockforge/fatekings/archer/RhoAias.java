package cn.blockforge.fatekings.archer;

import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.combat.JudgementRules.Weapon;
import cn.blockforge.fatekings.entity.BeamEntity;
import cn.blockforge.fatekings.king.KingState;
import cn.blockforge.fatekings.king.Kings;
import cn.blockforge.fatekings.king.Skills;
import cn.blockforge.fatekings.voice.Voice;
import cn.blockforge.fatekings.voice.VoicePlayer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Rho Aias, the seven rings that cover the heavens: seven petals of light 2.2 blocks before EMIYA,
 * 3 blocks across, facing where he looks. Each petal stops five projectiles; a beam tears two to
 * four at once; Ea and Excalibur go straight through and shatter it. Six seconds, 60 s cooldown.
 */
public final class RhoAias {
    private static final class Shield {
        final UUID owner;
        final long until;
        int hits;
        int torn;

        Shield(UUID owner, long until) {
            this.owner = owner;
            this.until = until;
        }

        int petals() {
            return Math.max(0, ArcherRules.petalsAfter(this.hits) - this.torn);
        }
    }

    private static final Map<UUID, Shield> SHIELDS = new ConcurrentHashMap<>();

    private RhoAias() {
    }

    public static void clear() {
        SHIELDS.clear();
    }

    public static boolean cast(LivingEntity caster) {
        if (!Kings.isArcher(caster) || !(caster.level() instanceof ServerLevel level)) return false;
        KingState s = Kings.of(caster);
        long now = level.getGameTime();
        if (!s.ready(Skills.RHO_AIAS, now)) {
            if (caster instanceof Player p) Kings.refuse(p, "fatekings.hint.cooldown", net.minecraft.network.chat.Component.translatable("fatekings.skill.rho_aias"),
                String.format(java.util.Locale.ROOT, "%.1f", s.cooldownLeft(Skills.RHO_AIAS, now) / 20.0f));
            return false;
        }
        s.cooldown(Skills.RHO_AIAS, now, ArcherRules.RHO_AIAS);
        s.dirty = true;
        SHIELDS.put(caster.getUUID(), new Shield(caster.getUUID(), now + ArcherRules.RHO_AIAS_TIME));
        Fx.event(level, Fx.RHO_AIAS, caster, caster.position(), ArcherRules.RHO_AIAS_TIME, ArcherRules.RHO_AIAS_PETALS, 96.0);
        VoicePlayer.say(caster, Voice.EMIYA_RHO_AIAS);
        Vec3 c = centre(caster);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.6f, 1.4f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.6f, 0.8f);
        return true;
    }

    public static boolean active(LivingEntity e) {
        Shield s = SHIELDS.get(e.getUUID());
        return s != null && e.level().getGameTime() < s.until && s.petals() > 0;
    }

    public static int petals(LivingEntity e) {
        return active(e) ? SHIELDS.get(e.getUUID()).petals() : 0;
    }

    public static int left(LivingEntity e) {
        Shield s = SHIELDS.get(e.getUUID());
        return s == null || !active(e) ? 0 : (int)Math.max(0L, s.until - e.level().getGameTime());
    }

    public static void end(LivingEntity e) {
        if (SHIELDS.remove(e.getUUID()) != null && e.level() instanceof ServerLevel level) {
            Fx.event(level, Fx.RHO_AIAS, e, e.position(), 0, 0.0f, 96.0);
        }
    }

    private static Vec3 centre(LivingEntity owner) {
        return owner.getEyePosition().add(owner.getViewVector(1.0f).scale(ArcherRules.RHO_AIAS_DIST));
    }

    private static LivingEntity owner(ServerLevel level, Shield s) {
        return level.getEntity(s.owner) instanceof LivingEntity l && l.isAlive() ? l : null;
    }

    /** Each tick: projectiles that would cross a shield's face from the front are stopped there. */
    public static void tick(ServerLevel level) {
        if (SHIELDS.isEmpty()) return;
        long now = level.getGameTime();
        for (Shield s : SHIELDS.values()) {
            LivingEntity owner = owner(level, s);
            if (owner == null) {
                if (level.getEntity(s.owner) == null && now >= s.until) SHIELDS.remove(s.owner);
                continue;
            }
            if (now >= s.until || s.petals() <= 0) {
                end(owner);
                continue;
            }
            Vec3 c = centre(owner);
            Vec3 n = owner.getViewVector(1.0f);
            double r = ArcherRules.RHO_AIAS_RADIUS;
            for (Projectile p : level.getEntitiesOfClass(Projectile.class, new AABB(c, c).inflate(r + 6.0), Entity::isAlive)) {
                if (p.getOwner() == owner) continue;
                if (crossing(p.position(), p.getDeltaMovement(), c, n, r)) {
                    stop(level, s, owner, p.position());
                    p.discard();
                    if (s.petals() <= 0) break;
                }
            }
        }
    }

    /** Whether a mover at {@code pos} with velocity {@code vel} crosses the disc (centre c, normal n, radius r) this tick from the front. */
    static boolean crossing(Vec3 pos, Vec3 vel, Vec3 c, Vec3 n, double r) {
        double d0 = pos.subtract(c).dot(n);
        double d1 = pos.add(vel).subtract(c).dot(n);
        if (vel.dot(n) >= 0.0) return false;
        if (!(d0 >= -0.6 && d1 <= 0.3 && d0 <= Math.max(0.3, vel.length() + 0.3))) return false;
        double t = Math.abs(d0 - d1) < 1.0E-6 ? 0.0 : Math.max(0.0, Math.min(1.0, d0 / (d0 - d1)));
        Vec3 q = pos.add(vel.scale(t));
        Vec3 off = q.subtract(c);
        Vec3 inPlane = off.subtract(n.scale(off.dot(n)));
        return inPlane.lengthSqr() <= r * r;
    }

    private static void stop(ServerLevel level, Shield s, LivingEntity owner, Vec3 at) {
        int before = s.petals();
        ++s.hits;
        Fx.particles(level, Fx.dust(0xFFB0E0, 1.2f), at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.0);
        Fx.particles(level, ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.1);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.2f, 1.3f);
        petalLost(level, s, owner, before);
    }

    private static void petalLost(ServerLevel level, Shield s, LivingEntity owner, int before) {
        int after = s.petals();
        if (after == before) return;
        Vec3 c = centre(owner);
        Fx.particles(level, Fx.dust(0xFFB0E0, 1.6f), c.x, c.y, c.z, 30, 1.2, 1.2, 1.2, 0.0);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.4f, 0.8f + 0.1f * after);
        if (after <= 0) {
            end(owner);
        } else {
            Fx.event(level, Fx.RHO_AIAS, owner, owner.position(), (int)Math.max(1L, s.until - level.getGameTime()), after, 96.0);
        }
    }

    /**
     * A beam front moving from {@code from} to {@code to}: returns how far along it a shield stops it
     * (petals torn), or -1. Ea and Excalibur are never stopped; they shatter whatever shield they cross.
     */
    public static double interceptBeam(ServerLevel level, BeamEntity beam, Vec3 from, Vec3 to) {
        if (SHIELDS.isEmpty()) return -1.0;
        Vec3 seg = to.subtract(from);
        double len = seg.length();
        if (len < 1.0E-6) return -1.0;
        Vec3 d = seg.scale(1.0 / len);
        double best = -1.0;
        for (Shield s : SHIELDS.values()) {
            LivingEntity owner = owner(level, s);
            if (owner == null || owner.getUUID().equals(beam.ownerId()) || s.petals() <= 0 || level.getGameTime() >= s.until) continue;
            Vec3 c = centre(owner);
            Vec3 n = owner.getViewVector(1.0f);
            double dn = d.dot(n);
            if (dn >= -1.0E-3) continue;
            double dist = c.subtract(from).dot(n) / dn;
            if (dist < 0.0 || dist > len) continue;
            Vec3 q = from.add(d.scale(dist));
            if (q.distanceTo(c) > ArcherRules.RHO_AIAS_RADIUS + beam.width() * 0.5) continue;
            Weapon w = beam.weapon();
            if (w == Weapon.EA || w == Weapon.EXCALIBUR) {
                shatter(level, s, owner);
                continue;
            }
            int cost = ArcherRules.beamPetalCost(w == Weapon.CALADBOLG, w == Weapon.EXCALIBUR_REPLICA);
            int before = s.petals();
            if (before >= cost) {
                s.torn += cost;
                petalLost(level, s, owner, before);
                best = best < 0.0 ? dist : Math.min(best, dist);
            } else {
                shatter(level, s, owner);
            }
        }
        return best;
    }

    private static void shatter(ServerLevel level, Shield s, LivingEntity owner) {
        int before = s.petals();
        s.torn = ArcherRules.RHO_AIAS_PETALS;
        petalLost(level, s, owner, before);
    }

    /** For movers that are not projectiles (sword light, thrown blades): one hit. Returns whether it was stopped. */
    public static boolean blocks(ServerLevel level, Entity mover, LivingEntity moverOwner, Vec3 from, Vec3 to) {
        if (SHIELDS.isEmpty()) return false;
        for (Shield s : SHIELDS.values()) {
            LivingEntity owner = owner(level, s);
            if (owner == null || owner == moverOwner || s.petals() <= 0 || level.getGameTime() >= s.until) continue;
            if (crossing(from, to.subtract(from), centre(owner), owner.getViewVector(1.0f), ArcherRules.RHO_AIAS_RADIUS)) {
                stop(level, s, owner, from);
                return true;
            }
        }
        return false;
    }
}
