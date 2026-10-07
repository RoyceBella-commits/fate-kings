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
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Excalibur, Sword of Promised Victory: a golden slash of light that cuts everything in the region it
 * sweeps (160 blocks, 5-9 wide), leaves a glowing trench that slowly cools, and bursts into falling
 * motes at its end.
 */
public class ExcaliburWaveEntity extends BeamEntity {
    /** A projected replica's beam: weaker, paler, and Infinity stops it. */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> REPLICA =
        net.minecraft.network.syncher.SynchedEntityData.defineId(ExcaliburWaveEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    public ExcaliburWaveEntity(EntityType<? extends ExcaliburWaveEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(REPLICA, false);
    }

    public boolean replica() {
        return this.entityData.get(REPLICA);
    }

    /** EMIYA's projected Excalibur: half the width, half the reach, a fraction of the power. */
    public static ExcaliburWaveEntity fireReplica(ServerLevel level, LivingEntity owner, Vec3 origin, Vec3 dir, float width, double range) {
        ExcaliburWaveEntity e = new ExcaliburWaveEntity(FateEntities.EXCALIBUR_WAVE, level);
        e.setup(owner, origin, dir, width, range);
        e.entityData.set(REPLICA, true);
        level.addFreshEntity(e);
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 2.0f, 1.5f);
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2.0f, 0.8f);
        return e;
    }

    public static ExcaliburWaveEntity fire(ServerLevel level, LivingEntity owner, Vec3 origin, Vec3 dir, float width, double range) {
        ExcaliburWaveEntity e = new ExcaliburWaveEntity(FateEntities.EXCALIBUR_WAVE, level);
        e.setup(owner, origin, dir, width, range);
        level.addFreshEntity(e);
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 3.0f, 1.3f);
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 3.0f, 0.6f);
        return e;
    }

    @Override
    public Weapon weapon() {
        return replica() ? Weapon.EXCALIBUR_REPLICA : Weapon.EXCALIBUR;
    }

    @Override
    protected double speed() {
        return 10.0;
    }

    @Override
    protected int endingTicks() {
        return 30;
    }

    @Override
    protected void onSegment(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 to, float width) {
        for (Entity e : inBand(level, from, to, width)) {
            if (e == owner || !(e instanceof LivingEntity living) || !this.judged.add(e.getId())) continue;
            float m = Sides.side(living) == Side.HERO ? this.multiplierOnHero : 1.0f;
            Judgement.strike(level, owner, this, living, weapon(), m);
        }
        Vec3 seg = to.subtract(from);
        double len = seg.length();
        Vec3 d = seg.scale(1.0 / Math.max(1.0E-6, len));
        for (double t = 0; t < len; t += 2.0) {
            Vec3 p = from.add(d.scale(t));
            Fx.particles(level, Fx.dust(0xFFE38A, 2.2f), p.x, p.y, p.z, 3, width * 0.2, width * 0.2, width * 0.2, 0.0);
            Fx.particles(level, ParticleTypes.END_ROD, p.x, p.y, p.z, 1, width * 0.2, width * 0.2, width * 0.2, 0.05);
        }
        scorch(level, from, to, width);
    }

    /** The glowing trench: surface blocks under the path turn to magma and cool to blackstone, plants burn. */
    private void scorch(ServerLevel level, Vec3 from, Vec3 to, float width) {
        boolean terrain = Terrain.enabled();
        Vec3 seg = to.subtract(from);
        double len = seg.length();
        if (len < 1.0E-3) return;
        Vec3 d = seg.scale(1.0 / len);
        Vec3 side = new Vec3(-d.z, 0.0, d.x);
        if (side.lengthSqr() < 1.0E-4) return; // straight up or down: no trench
        side = side.normalize();
        double half = width * (replica() ? 0.18 : 0.35);
        for (double t = 0; t < len; t += 1.0) {
            Vec3 c = from.add(d.scale(t));
            for (double o = -half; o <= half; o += 1.0) {
                Vec3 q = c.add(side.scale(o));
                BlockPos pos = BlockPos.containing(q);
                for (int dy = 0; dy < 10; ++dy) {
                    BlockPos p = pos.below(dy);
                    BlockState s = level.getBlockState(p);
                    if (s.isAir()) continue;
                    if (s.is(BlockTags.REPLACEABLE_BY_TREES) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.LEAVES) || s.is(BlockTags.SAPLINGS)) {
                        if (terrain) Terrain.set(level, p, Blocks.AIR.defaultBlockState());
                        continue;
                    }
                    if (terrain && Terrain.breakable(level, p, s) && !s.hasBlockEntity() && s.isSolidRender()) {
                        Terrain.set(level, p, Blocks.MAGMA_BLOCK.defaultBlockState());
                        Terrain.later(level, p, Blocks.MAGMA_BLOCK.defaultBlockState(),
                            (level.getRandom().nextInt(3) == 0 ? Blocks.COARSE_DIRT : Blocks.BLACKSTONE).defaultBlockState(), 500 + level.getRandom().nextInt(200));
                    }
                    if (level.getRandom().nextInt(6) == 0) {
                        Fx.particles(level, ParticleTypes.FLAME, p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 1, 0.3, 0.1, 0.3, 0.01);
                    }
                    break;
                }
            }
        }
    }

    @Override
    protected void onEnd(ServerLevel level, LivingEntity owner, Vec3 at) {
        Fx.particles(level, ParticleTypes.FLAME, at.x, at.y, at.z, 120, 3.0, 3.0, 3.0, 0.2);
        Fx.particles(level, ParticleTypes.END_ROD, at.x, at.y, at.z, 160, 5.0, 5.0, 5.0, 0.15);
        Fx.particles(level, Fx.dust(0xFFF7DA, 3.0f), at.x, at.y, at.z, 80, 4.0, 4.0, 4.0, 0.0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 4.0f, 0.5f);
    }
}
