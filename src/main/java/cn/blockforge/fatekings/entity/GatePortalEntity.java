package cn.blockforge.fatekings.entity;

import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.registry.FateEntities;
import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A golden ripple of the Gate of Babylon. The treasure's hilt shows first; a moment before it is
 * fired it turns and bares its blade (design doc 6.1). Never saved.
 */
public class GatePortalEntity extends Entity {
    /** Tick (of this entity) at which the treasure flies; -1 while a volley is still being held. */
    private static final EntityDataAccessor<Integer> FIRE_AT = SynchedEntityData.defineId(GatePortalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> WEAPON = SynchedEntityData.defineId(GatePortalEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(GatePortalEntity.class, EntityDataSerializers.FLOAT);
    private static final int FADE = 10;
    private UUID ownerId;
    private LivingEntity target;
    private Vec3 aimPoint;
    private int effect;
    private boolean single;
    private boolean fired;
    private int firedAt;
    private double speed = 3.0;

    public GatePortalEntity(EntityType<? extends GatePortalEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static GatePortalEntity open(ServerLevel level, LivingEntity owner, Vec3 pos, Vec3 facing, ItemStack weapon, int effect, boolean single) {
        GatePortalEntity g = new GatePortalEntity(FateEntities.GATE_PORTAL, level);
        g.ownerId = owner.getUUID();
        g.effect = effect;
        g.single = single;
        g.entityData.set(WEAPON, weapon.copy());
        g.entityData.set(FIRE_AT, -1);
        g.snapTo(pos.x, pos.y, pos.z, 0.0f, 0.0f);
        g.face(facing);
        level.addFreshEntity(g);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 1.6f + level.getRandom().nextFloat() * 0.4f);
        return g;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(FIRE_AT, -1);
        builder.define(WEAPON, new ItemStack(Items.GOLDEN_SWORD));
        builder.define(SIZE, 1.0f);
    }

    public void face(Vec3 dir) {
        if (dir.lengthSqr() < 1.0E-6) return;
        Vec3 d = dir.normalize();
        float yaw = (float)(Math.toDegrees(Math.atan2(-d.x, d.z)));
        float pitch = (float)(-Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, d.y)))));
        this.setYRot(yaw);
        this.setXRot(pitch);
        this.yRotO = yaw;
        this.xRotO = pitch;
    }

    public Vec3 facing() {
        return Vec3.directionFromRotation(this.getXRot(), this.getYRot());
    }

    public void setSize(float size) {
        this.entityData.set(SIZE, size);
    }

    public float size() {
        return this.entityData.get(SIZE);
    }

    public ItemStack weapon() {
        return this.entityData.get(WEAPON);
    }

    public int fireAt() {
        return this.entityData.get(FIRE_AT);
    }

    /** Fires {@code delay} ticks from now at the target (or the point). */
    public void release(LivingEntity target, Vec3 point, int delay) {
        this.target = target;
        this.aimPoint = point;
        this.entityData.set(FIRE_AT, this.tickCount + Math.max(4, delay));
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public boolean fired() {
        return this.fired;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) return;
        Entity owner = this.ownerId == null ? null : level.getEntity(this.ownerId);
        if (owner == null || !owner.isAlive() || this.tickCount > 400) {
            this.discard();
            return;
        }
        int at = this.fireAt();
        if (!this.fired && at >= 0) {
            Vec3 aim = this.target != null && this.target.isAlive() ? this.target.getBoundingBox().getCenter() : this.aimPoint;
            if (aim != null) face(aim.subtract(this.position()));
            if (this.tickCount >= at) fire(level, (LivingEntity)owner, aim);
        }
        if (this.fired && this.tickCount - this.firedAt > FADE) this.discard();
    }

    private void fire(ServerLevel level, LivingEntity owner, Vec3 aim) {
        this.fired = true;
        this.firedAt = this.tickCount;
        Vec3 dir = aim == null ? facing() : aim.subtract(this.position()).normalize();
        // A little scatter so a volley reads as a rain, not a single line.
        dir = dir.add((this.random.nextDouble() - 0.5) * 0.03, (this.random.nextDouble() - 0.5) * 0.03, (this.random.nextDouble() - 0.5) * 0.03).normalize();
        TreasureProjectile p = TreasureProjectile.create(level, owner, this.position().add(dir.scale(0.6)), weapon(), this.effect, this.single);
        p.shoot(dir.x, dir.y, dir.z, (float)this.speed, 0.0f);
        level.addFreshEntity(p);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.7f, 1.3f + this.random.nextFloat() * 0.4f);
        Fx.particles(level, Fx.dust(Fx.GOLD, 1.0f), this.position(), 6, 0.3, 0.0);
    }

    /** Client: how far the treasure has come out (0 hilt hidden .. 1 fully out). */
    public float emergence(float partial) {
        return Math.min(1.0f, (this.tickCount + partial) / 8.0f);
    }

    /** Client: 0 = hilt towards the foe; 1 = turned, blade towards the foe (just before firing). */
    public float turn(float partial) {
        int at = this.fireAt();
        if (at < 0) return 0.0f;
        float t = (this.tickCount + partial - (at - 4)) / 4.0f;
        return Math.max(0.0f, Math.min(1.0f, t));
    }

    /** Client: 1 while open, fading to 0 after the shot. */
    public float openness(float partial) {
        float open = Math.min(1.0f, (this.tickCount + partial) / 5.0f);
        int at = this.fireAt();
        if (at >= 0 && this.tickCount + partial > at) {
            open *= Math.max(0.0f, 1.0f - (this.tickCount + partial - at) / FADE);
        }
        return open;
    }

    public boolean weaponOut(float partial) {
        int at = this.fireAt();
        return at < 0 || this.tickCount + partial < at;
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
