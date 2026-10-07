package cn.blockforge.fatekings.entity;

import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.combat.Judgement;
import cn.blockforge.fatekings.combat.JudgementRules.Side;
import cn.blockforge.fatekings.combat.JudgementRules.Weapon;
import cn.blockforge.fatekings.combat.Sides;
import cn.blockforge.fatekings.combat.Terrain;
import cn.blockforge.fatekings.registry.FateEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Enuma Elish, Star of Creation that Split Heaven and Earth: a red-black spiral of wind that splits
 * space along its path (200 blocks), swallows what it passes and collapses into a black hole at the
 * end before bursting.
 */
public class EnumaElishEntity extends BeamEntity {
    public EnumaElishEntity(EntityType<? extends EnumaElishEntity> type, Level level) {
        super(type, level);
    }

    public static EnumaElishEntity fire(ServerLevel level, LivingEntity owner, Vec3 origin, Vec3 dir, float width, double range) {
        EnumaElishEntity e = new EnumaElishEntity(FateEntities.ENUMA_ELISH, level);
        e.setup(owner, origin, dir, width, range);
        level.addFreshEntity(e);
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 3.0f, 0.5f);
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0f, 0.6f);
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 3.0f, 0.5f);
        return e;
    }

    @Override
    public Weapon weapon() {
        return Weapon.EA;
    }

    @Override
    protected double speed() {
        return 8.0;
    }

    @Override
    protected int endingTicks() {
        return 34;
    }

    @Override
    protected void onSegment(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 to, float width) {
        for (Entity e : inBand(level, from, to, width)) {
            if (e == owner || !this.judged.add(e.getId())) continue;
            if (e instanceof LivingEntity living) {
                float m = Sides.side(living) == Side.HERO ? this.multiplierOnHero : 1.0f;
                Judgement.strike(level, owner, this, living, Weapon.EA, m);
            } else if (!(e instanceof Player) && !(e instanceof BeamEntity) && e.getVehicle() == null && !e.isVehicle()) {
                // The rift swallows whatever lies in its path.
                e.discard();
            }
        }
        Vec3 seg = to.subtract(from);
        double len = seg.length();
        Vec3 d = seg.scale(1.0 / Math.max(1.0E-6, len));
        for (double t = 0; t < len; t += 1.5) {
            Vec3 p = from.add(d.scale(t));
            Fx.particles(level, Fx.dust(level.getRandom().nextBoolean() ? 0xB0101A : 0x1A0A0E, 2.0f), p.x, p.y, p.z, 3, width * 0.25, width * 0.25, width * 0.25, 0.0);
        }
        if (Terrain.enabled()) carve(level, from, to, width * 0.45);
        // Ea tears a reality marble apart.
        UbwEntity.tearIfCrossed(level, from, to, owner);
    }

    private void carve(ServerLevel level, Vec3 from, Vec3 to, double r) {
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

    @Override
    protected void onEnd(ServerLevel level, LivingEntity owner, Vec3 at) {
        level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 4.0f, 0.5f);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel level && state() == ENDING && stateAge(0.0f) == 28) {
            Vec3 at = frontPos();
            // The black hole collapses with a low boom.
            level.explode(owner(), at.x, at.y, at.z, 5.0f, Terrain.enabled() ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 4.0f, 0.4f);
            Fx.particles(level, ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 2, 1.0, 1.0, 1.0, 0.0);
            Fx.particles(level, ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 80, 2.5, 2.5, 2.5, 0.4);
        }
    }
}
