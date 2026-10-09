package cn.blockforge.fatekings.entity;

import cn.blockforge.fatekings.archer.ArcherRules;
import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.combat.Judgement;
import cn.blockforge.fatekings.combat.JudgementRules.Weapon;
import cn.blockforge.fatekings.combat.Terrain;
import cn.blockforge.fatekings.registry.FateEntities;
import cn.blockforge.fatekings.voice.Voice;
import cn.blockforge.fatekings.voice.VoicePlayer;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Caladbolg II, the fake spiral sword: a drill arrow that twists the space it passes through. It
 * flies 160 blocks at 6 a tick, judges everything in its 2.4-wide path, bores a tunnel (terrain
 * effects on), and at its target or the end of its flight is set off as a Broken Phantasm: everyone
 * within 6 blocks is judged too and the ground is blasted.
 */
public class CaladbolgEntity extends BeamEntity {
    private UUID lockedId;

    public CaladbolgEntity(EntityType<? extends CaladbolgEntity> type, Level level) {
        super(type, level);
    }

    public static CaladbolgEntity fire(ServerLevel level, LivingEntity owner, Vec3 origin, Vec3 dir, LivingEntity locked) {
        CaladbolgEntity e = new CaladbolgEntity(FateEntities.CALADBOLG, level);
        e.setup(owner, origin, dir, ArcherRules.CALADBOLG_WIDTH, ArcherRules.CALADBOLG_RANGE);
        e.lockedId = locked == null ? null : locked.getUUID();
        level.addFreshEntity(e);
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.PLAYERS, 3.0f, 0.7f);
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.6f, 1.6f);
        return e;
    }

    @Override
    public Weapon weapon() {
        return Weapon.CALADBOLG;
    }

    @Override
    protected double speed() {
        return ArcherRules.CALADBOLG_SPEED;
    }

    @Override
    protected int endingTicks() {
        return 24;
    }

    @Override
    protected void onSegment(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 to, float width) {
        Vec3 d = dir();
        for (Entity e : inBand(level, from, to, width)) {
            if (e == owner || !(e instanceof LivingEntity living) || !this.judged.add(e.getId())) continue;
            Judgement.strike(level, owner, this, living, Weapon.CALADBOLG, 1.0f);
            if (e.getUUID().equals(this.lockedId)) {
                // It bursts in the one it was loosed at.
                this.stopAt = Math.max(0.0, e.getBoundingBox().getCenter().subtract(this.position()).dot(d));
            }
        }
        Vec3 seg = to.subtract(from);
        double len = seg.length();
        Vec3 side = d.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(d).normalize();
        for (double t = 0; t < len; t += 0.75) {
            double a = (this.front() + t) * 1.6;
            Vec3 p = from.add(d.scale(t));
            Vec3 o = side.scale(Math.cos(a) * 0.9).add(up.scale(Math.sin(a) * 0.9));
            Fx.particles(level, Fx.dust(0xE8302A, 1.3f), p.x + o.x, p.y + o.y, p.z + o.z, 1, 0.02, 0.02, 0.02, 0.0);
            Fx.particles(level, Fx.dust(0xFFF0E8, 1.0f), p.x - o.x, p.y - o.y, p.z - o.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
        if (Terrain.enabled()) carve(level, from, to, ArcherRules.CALADBOLG_TUNNEL);
    }

    private static void carve(ServerLevel level, Vec3 from, Vec3 to, double r) {
        Vec3 seg = to.subtract(from);
        double len2 = Math.max(1.0E-6, seg.lengthSqr());
        BlockPos min = BlockPos.containing(Math.min(from.x, to.x) - r, Math.min(from.y, to.y) - r, Math.min(from.z, to.z) - r);
        BlockPos max = BlockPos.containing(Math.max(from.x, to.x) + r, Math.max(from.y, to.y) + r, Math.max(from.z, to.z) + r);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            Vec3 c = Vec3.atCenterOf(pos);
            double t = Math.max(0.0, Math.min(1.0, c.subtract(from).dot(seg) / len2));
            if (c.distanceToSqr(from.add(seg.scale(t))) <= r * r) Terrain.carve(level, pos);
        }
    }

    /** Broken Phantasm. */
    @Override
    protected void onEnd(ServerLevel level, LivingEntity owner, Vec3 at) {
        double r = ArcherRules.CALADBOLG_BLAST;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r),
                e -> e != owner && e.isAlive() && e.getBoundingBox().getCenter().distanceToSqr(at) <= r * r)) {
            if (this.judged.add(e.getId())) Judgement.strike(level, owner, this, e, Weapon.CALADBOLG, 1.0f);
        }
        Terrain.blast(level, owner, at, 5.0f);
        Fx.particles(level, ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 2, 1.0, 1.0, 1.0, 0.0);
        Fx.particles(level, Fx.dust(0xE8302A, 2.4f), at.x, at.y, at.z, 80, 3.0, 3.0, 3.0, 0.0);
        Fx.particles(level, ParticleTypes.END_ROD, at.x, at.y, at.z, 60, 2.5, 2.5, 2.5, 0.2);
        Fx.event(level, Fx.SHAKE, this, at, 30, 1.0f, 96.0);
        Fx.event(level, Fx.FLASH, this, at, 6, 0.4f, 64.0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 4.0f, 0.6f);
        if (owner != null) VoicePlayer.say(owner, Voice.EMIYA_BROKEN_PHANTASM);
    }
}
