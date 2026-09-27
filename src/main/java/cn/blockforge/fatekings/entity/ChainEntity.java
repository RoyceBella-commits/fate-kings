package cn.blockforge.fatekings.entity;

import cn.blockforge.fatekings.registry.FateEntities;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * A golden chain of Enkidu from a ripple (this entity's position) to a target or a point. Purely
 * visual: the hook / bind effects are applied by the skill. Never saved.
 */
public class ChainEntity extends Entity {
    public static final int HOOK = 0;
    public static final int BIND = 1;
    /** Stopped by Infinity: halts before the target and dissolves into gold. */
    public static final int STOPPED = 2;
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(ChainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<org.joml.Vector3fc> END = SynchedEntityData.defineId(ChainEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(ChainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(ChainEntity.class, EntityDataSerializers.INT);

    public ChainEntity(EntityType<? extends ChainEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static ChainEntity spawn(ServerLevel level, Vec3 from, Entity target, Vec3 end, int mode, int life) {
        ChainEntity c = new ChainEntity(FateEntities.CHAIN, level);
        c.snapTo(from.x, from.y, from.z, 0.0f, 0.0f);
        c.entityData.set(TARGET, target == null ? -1 : target.getId());
        c.entityData.set(END, new Vector3f((float)end.x, (float)end.y, (float)end.z));
        c.entityData.set(MODE, mode);
        c.entityData.set(LIFE, life);
        level.addFreshEntity(c);
        return c;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TARGET, -1);
        builder.define(END, new Vector3f());
        builder.define(LIFE, 10);
        builder.define(MODE, HOOK);
    }

    public int mode() {
        return this.entityData.get(MODE);
    }

    public int life() {
        return this.entityData.get(LIFE);
    }

    /** Where the chain ends: the target's centre while it lives, otherwise the stored point. */
    public Vec3 end(float partial) {
        Entity t = this.level().getEntity(this.entityData.get(TARGET));
        if (t != null && t.isAlive() && mode() != STOPPED) {
            return t.getPosition(partial).add(0.0, t.getBbHeight() * 0.55, 0.0);
        }
        var v = this.entityData.get(END);
        return new Vec3(v.x(), v.y(), v.z());
    }

    /** How far the chain has shot out (0..1) and, at the end of its life, pulled back. */
    public float reach(float partial) {
        float t = this.tickCount + partial;
        float out = Math.min(1.0f, t / 4.0f);
        float left = life() - t;
        if (left < 5.0f && mode() != BIND) out *= Math.max(0.0f, left / 5.0f);
        return out;
    }

    public void setLife(int life) {
        this.entityData.set(LIFE, life);
    }

    public void release() {
        this.entityData.set(LIFE, this.tickCount + 5);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide() && this.tickCount >= life()) this.discard();
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
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 128.0 * 128.0;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
    }
}
