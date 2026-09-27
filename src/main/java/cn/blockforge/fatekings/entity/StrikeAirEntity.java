package cn.blockforge.fatekings.entity;

import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.combat.Terrain;
import cn.blockforge.fatekings.compat.JjkCompat;
import cn.blockforge.fatekings.registry.FateEntities;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Strike Air: the compressed wind round Excalibur fired as a hammer. A projectile (so Mahoraga can
 * adapt to it and bat it back), 40 blocks, 25 damage to everything along the way with a launch of
 * 10+ blocks. Puts out fires, scatters dropped items and, with terrain effects on, strips leaves and
 * tall grass. Never breaks solid blocks.
 */
public class StrikeAirEntity extends Projectile {
    public static final double SPEED = 2.5;
    public static final double RANGE = 40.0;
    private static final double RADIUS = 1.8;
    private final IntOpenHashSet hit = new IntOpenHashSet();
    private Entity lastOwner;
    private double travelled;

    public StrikeAirEntity(EntityType<? extends StrikeAirEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static StrikeAirEntity fire(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 dir) {
        StrikeAirEntity e = new StrikeAirEntity(FateEntities.STRIKE_AIR, level);
        e.setOwner(owner);
        e.snapTo(from.x, from.y, from.z, owner.getYRot(), owner.getXRot());
        e.setDeltaMovement(dir.normalize().scale(SPEED));
        level.addFreshEntity(e);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = this.getDeltaMovement();
        Vec3 from = this.position();
        Vec3 to = from.add(v);
        if (!(this.level() instanceof ServerLevel level)) {
            this.setPos(to);
            this.level().addParticle(ParticleTypes.SMALL_GUST, from.x, from.y, from.z, 0.0, 0.0, 0.0);
            return;
        }
        if (this.getOwner() != this.lastOwner) {
            // Batted back by Mahoraga: everyone can be hit again, including the knight.
            this.lastOwner = this.getOwner();
            this.hit.clear();
        }
        if (!level.noCollision(this, this.getBoundingBox().move(v).deflate(0.2)) && this.tickCount > 1) {
            burst(level, from);
            return;
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(RADIUS), this::canStrike)) {
            if (JjkCompat.infinityUp(e) && e.distanceToSqr(from) < 9.0) {
                // Infinity: the wind stops a couple of blocks short and spreads into a ring.
                Fx.ring(level, e.position().add(0.0, 1.0, 0.0).add(v.normalize().scale(-2.0)), 0xDDF4FF, 2.2, 30);
                level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.BREEZE_DEFLECT, SoundSource.PLAYERS, 1.0f, 0.8f);
                this.discard();
                return;
            }
            if (!this.hit.add(e.getId())) continue;
            strike(level, e, v);
        }
        clearAlong(level, from, to);
        this.setPos(to);
        this.travelled += v.length();
        Fx.particles(level, ParticleTypes.GUST, from.x, from.y, from.z, 1, 0.1, 0.1, 0.1, 0.0);
        if (this.travelled >= RANGE) burst(level, to);
    }

    private boolean canStrike(LivingEntity e) {
        Entity owner = this.getOwner();
        return e.isAlive() && e != owner && !e.isSpectator() && !(owner != null && e.isPassengerOfSameVehicle(owner));
    }

    private void strike(ServerLevel level, LivingEntity e, Vec3 v) {
        Entity owner = this.getOwner();
        DamageSource source = this.damageSources().mobProjectile(this, owner instanceof LivingEntity l ? l : null);
        e.setInvulnerableTime(0);
        e.hurtServer(level, source, 25.0f);
        Vec3 push = v.normalize().scale(2.4).add(0.0, 0.65, 0.0);
        e.setDeltaMovement(push);
        e.needsSync = true;
        if (e instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));
        }
        Fx.particles(level, ParticleTypes.GUST_EMITTER_SMALL, e.getX(), e.getY() + 1.0, e.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
    }

    /** Fires out, items scattered; leaves and tall plants blown away only with terrain effects on. */
    private void clearAlong(ServerLevel level, Vec3 from, Vec3 to) {
        boolean terrain = Terrain.enabled();
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(from, to).inflate(RADIUS + 1.0))) {
            item.setDeltaMovement(this.getDeltaMovement().normalize().scale(0.8).add(0.0, 0.3, 0.0));
        }
        BlockPos.betweenClosed(BlockPos.containing(Math.min(from.x, to.x) - 2, Math.min(from.y, to.y) - 2, Math.min(from.z, to.z) - 2),
                BlockPos.containing(Math.max(from.x, to.x) + 2, Math.max(from.y, to.y) + 2, Math.max(from.z, to.z) + 2)).forEach(pos -> {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) return;
            if (state.getBlock() instanceof BaseFireBlock) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            } else if (terrain && (state.is(BlockTags.LEAVES) || state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(BlockTags.FLOWERS))) {
                level.destroyBlock(pos, false);
            }
        });
    }

    private void burst(ServerLevel level, Vec3 at) {
        Fx.particles(level, ParticleTypes.GUST_EMITTER_LARGE, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.4f, 0.7f);
        this.discard();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
    }
}
