package cn.blockforge.fatekings.entity;

import cn.blockforge.fatekings.clash.NoblePhantasmClash;
import cn.blockforge.fatekings.combat.JudgementRules.Weapon;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A noble phantasm sweeping forward from its origin (this entity's position) along its facing. The
 * front advances each tick; everything inside the band between the old and new front is judged.
 * A clash with the opposing noble phantasm freezes both fronts where they meet.
 */
public abstract class BeamEntity extends Entity {
    public static final int ADVANCING = 0;
    public static final int CLASHING = 1;
    public static final int ENDING = 2;
    private static final EntityDataAccessor<Float> WIDTH = SynchedEntityData.defineId(BeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FRONT = SynchedEntityData.defineId(BeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(BeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STATE_SINCE = SynchedEntityData.defineId(BeamEntity.class, EntityDataSerializers.INT);
    protected final IntOpenHashSet judged = new IntOpenHashSet();
    protected UUID ownerId;
    protected float multiplierOnHero = 1.0f;
    protected double maxFront;
    /** Where the front stops early (Caladbolg at its target), or -1. */
    protected double stopAt = -1.0;
    protected long launchedAt;

    protected BeamEntity(EntityType<? extends BeamEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    protected void setup(LivingEntity owner, Vec3 origin, Vec3 dir, float width, double range) {
        this.ownerId = owner.getUUID();
        this.snapTo(origin.x, origin.y, origin.z, 0.0f, 0.0f);
        Vec3 d = dir.normalize();
        float yaw = (float)Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float)-Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, d.y))));
        this.setYRot(yaw);
        this.setXRot(pitch);
        this.yRotO = yaw;
        this.xRotO = pitch;
        this.entityData.set(WIDTH, width);
        this.maxFront = range;
        this.launchedAt = this.level().getGameTime();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(WIDTH, 5.0f);
        builder.define(FRONT, 0.0f);
        builder.define(STATE, ADVANCING);
        builder.define(STATE_SINCE, 0);
    }

    public abstract Weapon weapon();

    protected abstract double speed();

    /** Ticks the beam lingers once the front stops (collapse / afterglow). */
    protected abstract int endingTicks();

    protected abstract void onSegment(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 to, float width);

    protected abstract void onEnd(ServerLevel level, LivingEntity owner, Vec3 at);

    public Vec3 dir() {
        return Vec3.directionFromRotation(this.getXRot(), this.getYRot());
    }

    public float width() {
        return this.entityData.get(WIDTH);
    }

    public float front() {
        return this.entityData.get(FRONT);
    }

    public int state() {
        return this.entityData.get(STATE);
    }

    public int stateAge(float partial) {
        return (int)(this.tickCount + partial) - this.entityData.get(STATE_SINCE);
    }

    public Vec3 frontPos() {
        return this.position().add(dir().scale(front()));
    }

    public long launchedAt() {
        return this.launchedAt;
    }

    public LivingEntity owner() {
        if (this.ownerId == null || !(this.level() instanceof ServerLevel level)) return null;
        return level.getEntity(this.ownerId) instanceof LivingEntity l ? l : null;
    }

    public UUID ownerId() {
        return this.ownerId;
    }

    public void setMultiplierOnHero(float m) {
        this.multiplierOnHero = m;
    }

    protected void setState(int state) {
        this.entityData.set(STATE, state);
        this.entityData.set(STATE_SINCE, this.tickCount);
    }

    /** Clash: the front stops at {@code distance} from the origin. */
    public void freezeAt(double distance) {
        this.entityData.set(FRONT, (float)Math.max(0.0, Math.min(distance, this.maxFront)));
        setState(CLASHING);
    }

    /** Ends the beam where its front is now. */
    public void finish() {
        if (state() == ENDING) return;
        setState(ENDING);
        if (this.level() instanceof ServerLevel level) onEnd(level, owner(), frontPos());
    }

    /** Resumes after a won clash (not used by Ea / Excalibur: both end at the clash point). */
    public void cancelSilently() {
        setState(ENDING);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) return;
        LivingEntity owner = owner();
        int state = state();
        if (state == ADVANCING) {
            double from = front();
            double to = Math.min(this.maxFront, from + speed());
            Vec3 d = dir();
            boolean end = to >= this.maxFront;
            // Rho Aias stops whatever it can stop where the front meets it.
            double shield = cn.blockforge.fatekings.archer.RhoAias.interceptBeam(level, this, this.position().add(d.scale(from)), this.position().add(d.scale(to)));
            if (shield >= 0.0) {
                to = from + shield;
                end = true;
            }
            if (this.stopAt >= 0.0 && to >= this.stopAt) {
                to = Math.max(from, this.stopAt);
                end = true;
            }
            onSegment(level, owner, this.position().add(d.scale(from)), this.position().add(d.scale(to)), width());
            this.entityData.set(FRONT, (float)to);
            NoblePhantasmClash.track(this);
            if (end || this.stopAt >= 0.0 && to >= this.stopAt) finish();
        } else if (state == ENDING && stateAge(0.0f) >= endingTicks()) {
            this.discard();
        } else if (state == CLASHING && stateAge(0.0f) > 200) {
            finish();
        }
    }

    /** Entities in the band of this segment (a capsule of radius width / 2). */
    protected java.util.List<Entity> inBand(ServerLevel level, Vec3 from, Vec3 to, float width) {
        double r = width / 2.0;
        AABB box = new AABB(from, to).inflate(r + 1.0);
        Vec3 seg = to.subtract(from);
        double len2 = Math.max(1.0E-6, seg.lengthSqr());
        return level.getEntities(this, box, e -> {
            Vec3 c = e.getBoundingBox().getCenter();
            double t = Math.max(0.0, Math.min(1.0, c.subtract(from).dot(seg) / len2));
            Vec3 closest = from.add(seg.scale(t));
            double reach = r + Math.max(e.getBbWidth(), e.getBbHeight()) * 0.5;
            return c.distanceToSqr(closest) <= reach * reach;
        });
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
        return d < 320.0 * 320.0;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
    }
}
