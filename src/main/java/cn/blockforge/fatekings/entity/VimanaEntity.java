package cn.blockforge.fatekings.entity;

import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.KingState;
import cn.blockforge.fatekings.king.Kings;
import cn.blockforge.fatekings.king.Skills;
import cn.blockforge.fatekings.registry.FateEntities;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Vimana, the golden and emerald flying throne. Steered by the rider's view: forward speeds up (up
 * to ~1.5x an elytra dive), letting go hovers, jump climbs. The Gate of Babylon still fires while
 * riding. 200 damage forces it down; getting off (or losing the set) lands it gently and it returns
 * to the treasury.
 */
public class VimanaEntity extends Entity {
    /** Client movement input of the local rider: forward, strafe (left > 0), jump, unused. */
    public static volatile Supplier<float[]> clientInput = () -> new float[4];
    public static final double MAX_SPEED = 2.4;
    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(VimanaEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> LANDING = SynchedEntityData.defineId(VimanaEntity.class, EntityDataSerializers.BOOLEAN);
    private UUID ownerId;
    private int riderless;

    public VimanaEntity(EntityType<? extends VimanaEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public static VimanaEntity summon(ServerLevel level, LivingEntity owner) {
        VimanaEntity v = new VimanaEntity(FateEntities.VIMANA, level);
        v.ownerId = owner.getUUID();
        v.snapTo(owner.getX(), owner.getY() + 0.2, owner.getZ(), owner.getYRot(), 0.0f);
        level.addFreshEntity(v);
        owner.startRiding(v, true, true);
        Fx.ring(level, owner.position().add(0.0, 0.1, 0.0), Fx.GOLD, 1.8, 40);
        Fx.particles(level, Fx.dust(0x1FB873, 1.2f), owner.getX(), owner.getY() + 0.3, owner.getZ(), 30, 1.2, 0.2, 1.2, 0.0);
        level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 0.7f);
        return v;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DAMAGE, 0.0f);
        builder.define(LANDING, false);
    }

    @Override
    protected InterpolationHandler createInterpolationHandler() {
        return LinearInterpolationHandler.create(this, 3);
    }

    @Override
    public LivingEntity getControllingPassenger() {
        return !this.entityData.get(LANDING) && this.getFirstPassenger() instanceof Player p ? p : null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty() && passenger instanceof LivingEntity;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (player.isSecondaryUseActive() || !this.getPassengers().isEmpty()) return InteractionResult.PASS;
        if (!this.level().isClientSide()) {
            if (!player.getUUID().equals(this.ownerId)) return InteractionResult.PASS;
            player.startRiding(this);
        }
        return InteractionResult.SUCCESS;
    }

    public boolean landing() {
        return this.entityData.get(LANDING);
    }

    /** Lands gently with whoever is aboard, then returns to the treasury. */
    public void land() {
        this.entityData.set(LANDING, true);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            if (this.isLocalInstanceAuthoritative() && getControllingPassenger() instanceof Player rider) {
                steer(rider);
                this.move(MoverType.SELF, this.getDeltaMovement());
            }
            if (this.tickCount % 2 == 0) {
                Vec3 back = this.position().add(Vec3.directionFromRotation(0.0f, this.getYRot()).scale(-1.3)).add(0.0, 0.4, 0.0);
                this.level().addParticle(Fx.dust(this.random.nextBoolean() ? 0x1FB873 : Fx.GOLD, 1.0f), back.x, back.y, back.z, 0.0, 0.0, 0.0);
            }
            return;
        }
        ServerLevel level = (ServerLevel)this.level();
        LivingEntity rider = this.getFirstPassenger() instanceof LivingEntity l ? l : null;
        if (rider != null) {
            rider.resetFallDistance();
            if (!Kings.isHero(rider) && !landing()) land();
        }
        if (rider == null || landing()) {
            // Gentle descent, then back into the treasury.
            ++this.riderless;
            Vec3 v = this.getDeltaMovement();
            this.setDeltaMovement(v.x * 0.8, Math.max(-0.2, v.y - 0.02), v.z * 0.8);
            this.move(MoverType.SELF, this.getDeltaMovement());
            if (this.onGround() || this.riderless > 200 || rider == null && this.riderless > 40) {
                recall(level, false);
            }
        } else {
            this.riderless = 0;
        }
        if (this.tickCount % 20 == 0) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.6f,
                0.6f + (float)Math.min(0.8, this.getDeltaMovement().length() / MAX_SPEED));
        }
    }

    private void steer(Player rider) {
        float[] in = clientInput.get();
        Vec3 look = rider.getViewVector(1.0f);
        Vec3 side = new Vec3(look.z, 0.0, -look.x).normalize();
        Vec3 target = Vec3.ZERO;
        if (in[0] > 0.05f) target = look.scale(MAX_SPEED * in[0]);
        else if (in[0] < -0.05f) target = look.scale(0.5 * in[0]);
        target = target.add(side.scale(0.6 * in[1]));
        if (in[2] > 0.5f) target = target.add(0.0, 0.6, 0.0);
        Vec3 v = this.getDeltaMovement();
        double k = target.lengthSqr() > v.lengthSqr() ? 0.08 : 0.15;
        this.setDeltaMovement(v.add(target.subtract(v).scale(k)));
        this.setYRot(rider.getYRot());
        this.setXRot(0.0f);
    }

    public void recall(ServerLevel level, boolean crashed) {
        this.ejectPassengers();
        Fx.particles(level, Fx.dust(Fx.GOLD, 1.3f), this.position().add(0.0, 0.5, 0.0), 40, 1.0, 0.0);
        Fx.particles(level, ParticleTypes.END_ROD, this.position().add(0.0, 0.5, 0.0), 12, 0.8, 0.02);
        if (crashed) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0f, 1.4f);
            if (this.ownerId != null && level.getEntity(this.ownerId) instanceof LivingEntity owner) {
                KingState s = Kings.of(owner);
                if (s != null) s.cooldown(Skills.VIMANA, level.getGameTime(), KingRules.VIMANA * 2);
            }
        }
        this.discard();
    }

    public float damageTaken() {
        return this.entityData.get(DAMAGE);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (this.isInvulnerableToBase(source) || source.getEntity() == this.getFirstPassenger()) return false;
        float total = damageTaken() + amount;
        this.entityData.set(DAMAGE, total);
        Fx.particles(level, ParticleTypes.CRIT, this.position().add(0.0, 0.6, 0.0), 6, 0.6, 0.2);
        if (total >= 200.0f) {
            // Forced landing: the rider is put down safely, the cooldown doubles.
            LivingEntity rider = this.getFirstPassenger() instanceof LivingEntity l ? l : null;
            if (rider != null) rider.resetFallDistance();
            recall(level, true);
        }
        return true;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean canBeCollidedWith(Entity other) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        this.ownerId = in.read("Owner", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        if (this.ownerId != null) out.store("Owner", net.minecraft.core.UUIDUtil.CODEC, this.ownerId);
    }
}
