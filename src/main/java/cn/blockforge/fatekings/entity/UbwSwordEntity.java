package cn.blockforge.fatekings.entity;

import cn.blockforge.fatekings.archer.ArcherRules;
import cn.blockforge.fatekings.archer.RhoAias;
import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.combat.Judgement;
import cn.blockforge.fatekings.registry.FateDamage;
import cn.blockforge.fatekings.registry.FateEntities;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * A sword of the reality marble. Pulled up out of the hill (blade down, embers round its foot), it
 * hangs in the air a moment turning its point on its foe, then is loosed: straight and fast, a long
 * streak behind it, correcting only a little (the marble's sure hit; its damage pierces Infinity).
 * Past its foe it drives into the ground and stands there a moment before it crumbles to embers. An
 * interceptor flies at a treasure of the Gate of Babylon instead and both shatter. Not a projectile:
 * no shield of Infinity catches it. Never saved.
 */
public class UbwSwordEntity extends Entity {
    private static final EntityDataAccessor<ItemStack> WEAPON = SynchedEntityData.defineId(UbwSwordEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(UbwSwordEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(UbwSwordEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3fc> AIM = SynchedEntityData.defineId(UbwSwordEntity.class, EntityDataSerializers.VECTOR3);
    public static final int RISING = 0;
    public static final int AIMING = 1;
    public static final int FLYING = 2;
    public static final int STUCK = 3;
    /** Ticks to rise out of the ground; ticks it stands in the ground before it crumbles. */
    public static final int RISE = 8;
    public static final int STUCK_TIME = 18;
    private static final double SPEED = 3.6;
    private static final double INTERCEPT_SPEED = 4.0;
    private UUID ownerId;
    private Entity target;
    private boolean interceptor;
    private boolean struck;
    private Vec3 base;
    private double riseHeight = 4.0;
    private int hold = 8;
    private int flightStart;
    private int stuckAt;
    /** Client: where it was first seen (the foot of its rise). */
    private Vec3 seenAt;

    public UbwSwordEntity(EntityType<? extends UbwSwordEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** A sword of a volley: it rises out of the ground at {@code ground}, then goes for {@code foe}. */
    public static UbwSwordEntity rise(ServerLevel level, LivingEntity owner, Vec3 ground, LivingEntity foe, ItemStack weapon) {
        UbwSwordEntity s = create(level, owner, ground.add(0.0, -1.2, 0.0), foe, weapon);
        s.base = ground;
        s.riseHeight = 3.0 + level.getRandom().nextDouble() * 3.5;
        // Each hangs a different while before it is loosed, so a volley falls as a rain, not a single blow.
        s.hold = 5 + level.getRandom().nextInt(10);
        s.aim(new Vec3(0.0, -1.0, 0.0));
        level.addFreshEntity(s);
        Fx.particles(level, Fx.dust(Fx.EMBER, 1.2f), ground.x, ground.y + 0.15, ground.z, 10, 0.35, 0.05, 0.35, 0.0);
        Fx.particles(level, ParticleTypes.SMALL_FLAME, ground.x, ground.y + 0.1, ground.z, 4, 0.25, 0.05, 0.25, 0.02);
        level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.35f, 1.8f);
        return s;
    }

    public static UbwSwordEntity intercept(ServerLevel level, LivingEntity owner, Vec3 from, Entity treasure, ItemStack weapon) {
        UbwSwordEntity s = create(level, owner, from, treasure, weapon);
        s.interceptor = true;
        Vec3 dir = treasure.position().subtract(from).normalize();
        s.entityData.set(PHASE, FLYING);
        s.aim(dir);
        s.setDeltaMovement(dir.scale(INTERCEPT_SPEED));
        level.addFreshEntity(s);
        return s;
    }

    private static UbwSwordEntity create(ServerLevel level, LivingEntity owner, Vec3 pos, Entity target, ItemStack weapon) {
        UbwSwordEntity s = new UbwSwordEntity(FateEntities.UBW_SWORD, level);
        s.ownerId = owner.getUUID();
        s.target = target;
        s.entityData.set(WEAPON, weapon.copyWithCount(1));
        s.snapTo(pos.x, pos.y, pos.z, 0.0f, 0.0f);
        return s;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(WEAPON, new ItemStack(Items.IRON_SWORD));
        builder.define(PHASE, RISING);
        builder.define(AGE, 0);
        builder.define(AIM, new Vector3f(0.0f, -1.0f, 0.0f));
    }

    public ItemStack weapon() {
        return this.entityData.get(WEAPON);
    }

    public boolean intercepting() {
        return this.interceptor;
    }

    public int phase() {
        return this.entityData.get(PHASE);
    }

    /** Ticks since it was made (as the server counts them). */
    public int age() {
        return this.entityData.get(AGE);
    }

    /** Where its point is turned (unit). */
    public Vec3 aim() {
        Vector3fc a = this.entityData.get(AIM);
        return new Vec3(a.x(), a.y(), a.z());
    }

    /** Client: the age at which it was first seen driven in (so it can crumble from then). */
    private float stuckSeen = -1.0f;

    public float stuckSeen(float age) {
        if (this.stuckSeen < 0.0f) this.stuckSeen = age;
        return this.stuckSeen;
    }

    /** Client: the foot of its rise (where it was first seen). */
    public Vec3 seenAt() {
        return this.seenAt == null ? this.position() : this.seenAt;
    }

    private void aim(Vec3 dir) {
        if (dir.lengthSqr() < 1.0E-8) return;
        Vec3 d = dir.normalize();
        this.entityData.set(AIM, new Vector3f((float)d.x, (float)d.y, (float)d.z));
    }

    private void setPhase(int phase) {
        this.entityData.set(PHASE, phase);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            if (this.seenAt == null) this.seenAt = this.position();
            return;
        }
        this.entityData.set(AGE, this.tickCount);
        LivingEntity owner = this.ownerId != null && level.getEntity(this.ownerId) instanceof LivingEntity l ? l : null;
        if (owner == null || this.tickCount > 80) {
            crumble(level);
            return;
        }
        if (this.interceptor) {
            interceptTick(level, owner);
            return;
        }
        boolean foeGone = this.target == null || !this.target.isAlive();
        switch (phase()) {
            case RISING -> {
                if (foeGone) {
                    crumble(level);
                    return;
                }
                // Pulled up out of the hill, slowing as it comes clear; its point swings round to the foe.
                double t = Math.min(1.0, this.tickCount / (double)RISE);
                double up = -1.2 + (this.riseHeight + 1.2) * (1.0 - Math.pow(1.0 - t, 3.0));
                Vec3 base = this.base == null ? this.position() : this.base;
                this.setPos(base.x, base.y + up, base.z);
                Vec3 want = foeCentre().subtract(this.position()).normalize();
                double k = t * t;
                aim(new Vec3(0.0, -1.0, 0.0).scale(1.0 - k).add(want.scale(k)));
                if (this.tickCount >= RISE) setPhase(AIMING);
            }
            case AIMING -> {
                if (foeGone) {
                    crumble(level);
                    return;
                }
                Vec3 base = this.base == null ? this.position() : this.base;
                double bob = Math.sin(this.tickCount * 0.7) * 0.06;
                this.setPos(base.x, base.y + this.riseHeight + bob, base.z);
                aim(lead().subtract(this.position()));
                if (this.tickCount >= RISE + this.hold) {
                    setPhase(FLYING);
                    this.flightStart = this.tickCount;
                    this.setDeltaMovement(aim().scale(SPEED));
                    level.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.55f, 1.5f + this.random.nextFloat() * 0.3f);
                    Fx.particles(level, Fx.dust(Fx.EMBER, 1.0f), this.position(), 5, 0.15, 0.0);
                }
            }
            case FLYING -> fly(level, owner, foeGone);
            default -> {
                if (this.tickCount - this.stuckAt >= STUCK_TIME) crumble(level);
            }
        }
    }

    private Vec3 foeCentre() {
        return this.target.getBoundingBox().getCenter();
    }

    /** Where to aim at a moving foe: a little ahead of it, for the flight it takes to get there. */
    private Vec3 lead() {
        Vec3 c = foeCentre();
        double ticks = c.distanceTo(this.position()) / SPEED;
        Vec3 pace = new Vec3(this.target.getX() - this.target.xo, 0.0, this.target.getZ() - this.target.zo);
        return c.add(pace.scale(Math.min(ticks, 8.0)));
    }

    /** Loosed: straight and fast, turning a little towards its foe until it strikes; then on into the ground. */
    private void fly(ServerLevel level, LivingEntity owner, boolean foeGone) {
        Vec3 from = this.position();
        Vec3 dir = aim();
        if (!this.struck && !foeGone) {
            Vec3 want = foeCentre().subtract(from);
            double[] d = ArcherRules.turn(new double[]{dir.x, dir.y, dir.z}, new double[]{want.x, want.y, want.z}, Math.toRadians(10.0));
            dir = new Vec3(d[0], d[1], d[2]);
            aim(dir);
        }
        Vec3 v = dir.scale(SPEED);
        this.setDeltaMovement(v);
        Vec3 to = from.add(v);
        if (RhoAias.blocks(level, this, owner, from, to)) {
            crumble(level);
            return;
        }
        if (!this.struck && !foeGone) {
            double reach = 0.6 + Math.max(this.target.getBbWidth(), this.target.getBbHeight()) * 0.5;
            if (closest(from, to, foeCentre()) <= reach && this.target instanceof LivingEntity foe) {
                this.struck = true;
                DamageSource source = FateDamage.source(level, FateDamage.UBW_SWORD, this, owner);
                Judgement.fresh(foe);
                foe.hurtServer(level, source, ArcherRules.UBW_SWORD_DAMAGE);
                Vec3 at = foe.getBoundingBox().getCenter();
                Fx.particles(level, ParticleTypes.CRIT, at, 10, 0.3, 0.4);
                Fx.particles(level, Fx.dust(Fx.EMBER, 1.3f), at, 10, 0.3, 0.05);
                Fx.particles(level, ParticleTypes.END_ROD, at, 4, 0.15, 0.08);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.7f, 1.1f + this.random.nextFloat() * 0.2f);
            }
        }
        // Into the ground: it stands where it drove in.
        BlockHitResult ground = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (ground.getType() == HitResult.Type.BLOCK) {
            Vec3 in = ground.getLocation().add(dir.scale(0.45));
            this.setPos(in);
            this.setDeltaMovement(Vec3.ZERO);
            setPhase(STUCK);
            this.stuckAt = this.tickCount;
            Fx.particles(level, Fx.dust(Fx.EMBER, 1.1f), ground.getLocation(), 8, 0.25, 0.05);
            Fx.particles(level, ParticleTypes.SMOKE, ground.getLocation(), 4, 0.2, 0.02);
            level.playSound(null, in.x, in.y, in.z, SoundEvents.TRIDENT_HIT_GROUND, SoundSource.PLAYERS, 0.5f, 0.9f + this.random.nextFloat() * 0.2f);
            return;
        }
        if (this.tickCount - this.flightStart > 24) {
            crumble(level);
            return;
        }
        this.setPos(to);
    }

    /** An interceptor: flat out at its treasure, turning hard; both shatter. */
    private void interceptTick(ServerLevel level, LivingEntity owner) {
        if (this.target == null || !this.target.isAlive()) {
            crumble(level);
            return;
        }
        Vec3 from = this.position();
        Vec3 aim = this.target.getBoundingBox().getCenter().subtract(from);
        double dist = aim.length();
        Vec3 cur = aim();
        double[] d = ArcherRules.turn(new double[]{cur.x, cur.y, cur.z}, new double[]{aim.x, aim.y, aim.z}, Math.toRadians(60.0));
        Vec3 dir = new Vec3(d[0], d[1], d[2]);
        aim(dir);
        Vec3 v = dir.scale(Math.min(INTERCEPT_SPEED, Math.max(0.5, dist)));
        this.setDeltaMovement(v);
        Vec3 to = from.add(v);
        double reach = 0.6 + Math.max(this.target.getBbWidth(), this.target.getBbHeight()) * 0.5;
        if (closest(from, to, this.target.getBoundingBox().getCenter()) <= reach) {
            Vec3 at = this.target.position();
            this.target.discard();
            UbwEntity.parried(level, at);
            this.discard();
            return;
        }
        this.setPos(to);
    }

    /** It crumbles into embers, as every projection does in the end. */
    private void crumble(ServerLevel level) {
        Fx.particles(level, Fx.dust(Fx.EMBER, 0.9f), this.position(), 6, 0.15, 0.02);
        this.discard();
    }

    private static double closest(Vec3 from, Vec3 to, Vec3 c) {
        Vec3 seg = to.subtract(from);
        double len2 = Math.max(1.0E-6, seg.lengthSqr());
        double t = Math.max(0.0, Math.min(1.0, c.subtract(from).dot(seg) / len2));
        return c.distanceTo(from.add(seg.scale(t)));
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
