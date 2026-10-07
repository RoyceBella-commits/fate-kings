package cn.blockforge.fatekings.entity;

import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.combat.Judgement;
import cn.blockforge.fatekings.combat.Terrain;
import cn.blockforge.fatekings.compat.JjkCompat;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.registry.FateDamage;
import cn.blockforge.fatekings.registry.FateEntities;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A crescent of golden light cut loose by the revealed Excalibur. It flies straight (28 blocks), its
 * edge tilted by the angle of the swing, hits each creature once and, with terrain effects on, cuts
 * through every breakable block its edge passes. Never saved.
 */
public class SwordQiEntity extends Entity {
    public static final double SPEED = 2.4;
    public static final double HALF_LENGTH = 2.2;
    private static final EntityDataAccessor<Float> ROLL = SynchedEntityData.defineId(SwordQiEntity.class, EntityDataSerializers.FLOAT);
    private final IntOpenHashSet hit = new IntOpenHashSet();
    private UUID ownerId;
    private double travelled;

    public SwordQiEntity(EntityType<? extends SwordQiEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** {@code roll}: the swing's angle on screen in degrees (0: left to right, -90: straight down ...). */
    public static SwordQiEntity fire(ServerLevel level, LivingEntity owner, Vec3 origin, Vec3 dir, float roll) {
        SwordQiEntity q = new SwordQiEntity(FateEntities.SWORD_QI, level);
        q.ownerId = owner.getUUID();
        Vec3 d = dir.normalize();
        float yaw = (float)Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float)-Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, d.y))));
        q.snapTo(origin.x, origin.y, origin.z, yaw, pitch);
        q.entityData.set(ROLL, roll);
        q.setDeltaMovement(d.scale(SPEED));
        level.addFreshEntity(q);
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2f, 0.8f);
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.PLAYERS, 0.8f, 1.6f);
        return q;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ROLL, 0.0f);
    }

    public float roll() {
        return this.entityData.get(ROLL);
    }

    public Vec3 dir() {
        return Vec3.directionFromRotation(this.getXRot(), this.getYRot());
    }

    /** The unit vector along the crescent's edge (the swing direction projected on the view plane). */
    public Vec3 edgeAxis() {
        Vec3 d = dir();
        Vec3 right = d.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(d).normalize();
        double r = Math.toRadians(roll());
        return right.scale(Math.cos(r)).add(up.scale(Math.sin(r))).normalize();
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = this.getDeltaMovement();
        if (v.lengthSqr() < 1.0E-6) v = dir().scale(SPEED);
        Vec3 from = this.position();
        Vec3 to = from.add(v);
        if (!(this.level() instanceof ServerLevel level)) {
            this.setPos(to);
            return;
        }
        LivingEntity owner = this.ownerId != null && level.getEntity(this.ownerId) instanceof LivingEntity l ? l : null;
        Vec3 d = v.normalize();
        Vec3 edge = edgeAxis();
        Vec3 depth = d.cross(edge).normalize();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(HALF_LENGTH + 1.0),
                e -> e.isAlive() && e != owner && !e.isSpectator() && !(e instanceof Player p && p.isCreative()))) {
            Vec3 c = e.getBoundingBox().getCenter().subtract(from);
            double along = c.dot(d);
            double across = Math.abs(c.dot(edge));
            double thick = Math.abs(c.dot(depth));
            if (along < -0.5 || along > v.length() + 0.8 || across > HALF_LENGTH + e.getBbWidth() * 0.5 || thick > 0.8 + e.getBbHeight() * 0.5) continue;
            if (JjkCompat.infinityUp(e) && owner != null) {
                // Infinity stops the light a step before him.
                Fx.particles(level, Fx.dust(0xFFE38A, 1.4f), e.getBoundingBox().getCenter().subtract(d.scale(1.5)), 20, 0.6, 0.0);
                this.discard();
                return;
            }
            if (!this.hit.add(e.getId())) continue;
            Judgement.fresh(e);
            DamageSource source = FateDamage.source(level, FateDamage.SWORD_QI, this, owner);
            e.hurtServer(level, source, KingRules.SWORD_QI_DAMAGE);
            Fx.particles(level, ParticleTypes.END_ROD, e.getBoundingBox().getCenter(), 6, 0.2, 0.05);
        }
        if (Terrain.enabled()) cut(level, from, edge, d);
        this.setPos(to);
        this.travelled += v.length();
        if (this.tickCount % 2 == 0) Fx.particles(level, Fx.dust(0xFFE38A, 1.0f), from.x, from.y, from.z, 4, 0.6, 0.6, 0.6, 0.0);
        if (this.travelled >= KingRules.SWORD_QI_RANGE || this.tickCount > 40) {
            Fx.particles(level, ParticleTypes.END_ROD, to, 12, 0.8, 0.05);
            this.discard();
        }
    }

    /** Every breakable block under the crescent's edge, at both half-steps of this tick. */
    private void cut(ServerLevel level, Vec3 from, Vec3 edge, Vec3 d) {
        for (double step = 0.0; step <= SPEED; step += SPEED / 2.0) {
            Vec3 centre = from.add(d.scale(step));
            for (double s = -HALF_LENGTH; s <= HALF_LENGTH; s += 0.4) {
                // A crescent: the middle leads, the tips trail.
                double lead = 0.6 * (1.0 - (s / HALF_LENGTH) * (s / HALF_LENGTH));
                Vec3 p = centre.add(edge.scale(s)).add(d.scale(lead));
                Terrain.carve(level, BlockPos.containing(p));
            }
        }
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

    public Entity ownerEntity() {
        return this.ownerId != null && this.level() instanceof ServerLevel level ? level.getEntity(this.ownerId) : null;
    }
}
