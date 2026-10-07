package cn.blockforge.fatekings.entity;

import cn.blockforge.fatekings.archer.ArcherRules;
import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.combat.Judgement;
import cn.blockforge.fatekings.combat.Targets;
import cn.blockforge.fatekings.registry.FateEntities;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * An arrow of the black bow: a projected sword-arrow that hunts its foe. It leaves the string
 * straight, then turns toward its target (18 degrees a tick, 30 when close) aiming a little ahead of
 * where the foe runs; if the target falls or slips out of sight it looks for another hostile thing
 * in front of it. Once something stops it (Infinity, Rho Aias) it no longer hunts. It is an arrow as
 * far as the world is concerned: Infinity pins it, Mahoraga adapts to it, the Autodefender shoots it down.
 */
public class ProjectedArrowEntity extends AbstractArrow {
    private static final EntityDataAccessor<ItemStack> WEAPON = SynchedEntityData.defineId(ProjectedArrowEntity.class, EntityDataSerializers.ITEM_STACK);
    private final IntOpenHashSet hit = new IntOpenHashSet();
    private LivingEntity target;
    private int flight;
    private int stuck;
    private int slow;
    private boolean stalled;

    public ProjectedArrowEntity(EntityType<? extends ProjectedArrowEntity> type, Level level) {
        super(type, level);
        this.pickup = Pickup.DISALLOWED;
        this.setNoGravity(true);
    }

    /** Looses an arrow from {@code origin} along {@code dir}, hunting {@code target} (or whatever it meets). */
    public static ProjectedArrowEntity loose(ServerLevel level, LivingEntity owner, Vec3 origin, Vec3 dir, LivingEntity target, ItemStack look) {
        ProjectedArrowEntity a = new ProjectedArrowEntity(FateEntities.PROJECTED_ARROW, level);
        a.snapTo(origin.x, origin.y, origin.z, 0.0f, 0.0f);
        a.setOwner(owner);
        a.target = target;
        a.entityData.set(WEAPON, look.isEmpty() ? defaultLook() : look.copyWithCount(1));
        Vec3 d = dir.normalize();
        a.shoot(d.x, d.y, d.z, ArcherRules.ARROW_SPEED, 0.0f);
        level.addFreshEntity(a);
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0f, 1.6f + level.getRandom().nextFloat() * 0.2f);
        return a;
    }

    /** A plain projected sword-arrow (the model is set by the item model id, no item needed). */
    public static ItemStack defaultLook() {
        ItemStack s = new ItemStack(Items.ARROW);
        s.set(DataComponents.ITEM_MODEL, cn.blockforge.fatekings.FateKings.id("sword_arrow"));
        return s;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(WEAPON, new ItemStack(Items.ARROW));
    }

    public ItemStack weapon() {
        return this.entityData.get(WEAPON);
    }

    public LivingEntity target() {
        return this.target;
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(Items.ARROW);
    }

    @Override
    public void tick() {
        if (this.level() instanceof ServerLevel level && !this.isInGround()) steer(level);
        super.tick();
        if (this.level().isClientSide()) {
            if (!this.isInGround()) this.level().addParticle(Fx.dust(0xFF5A3A, 0.7f), this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
            return;
        }
        if (this.isInGround()) {
            if (++this.stuck >= ArcherRules.ARROW_STUCK_TICKS) dissolve();
        } else if (++this.flight > ArcherRules.ARROW_LIFETIME) {
            dissolve();
        }
    }

    private void steer(ServerLevel level) {
        Vec3 v = this.getDeltaMovement();
        double speed = v.length();
        if (this.tickCount > ArcherRules.ARROW_ARM_TICKS && speed < ArcherRules.ARROW_SPEED * 0.5) {
            // Pinned by Infinity, caught by a shield: it stops hunting for good.
            if (++this.slow >= 2) this.stalled = true;
        } else {
            this.slow = 0;
        }
        if (this.stalled || this.tickCount < ArcherRules.ARROW_ARM_TICKS || speed < 1.0E-4) return;
        Entity owner = this.getOwner();
        if (this.target != null && (!this.target.isAlive() || this.target.level() != level || this.target.distanceToSqr(this) > 64.0 * 64.0
                || this.hit.contains(this.target.getId()))) {
            this.target = null;
        }
        if (this.target == null && this.tickCount % 4 == 0 && owner instanceof LivingEntity caster) {
            this.target = Targets.nearestInCone(caster, this.position(), v.normalize(), ArcherRules.ARROW_CONE_COS, ArcherRules.ARROW_ACQUIRE_RANGE);
            if (this.target != null && this.target.distanceToSqr(this) > ArcherRules.ARROW_ACQUIRE_RANGE * ArcherRules.ARROW_ACQUIRE_RANGE) this.target = null;
        }
        if (this.target == null) return;
        Vec3 c = this.target.getBoundingBox().getCenter();
        double dist = c.distanceTo(this.position());
        Vec3 pace = new Vec3(this.target.getX() - this.target.xo, this.target.getY() - this.target.yo, this.target.getZ() - this.target.zo);
        double[] aim = ArcherRules.lead(new double[]{c.x, c.y, c.z}, new double[]{pace.x, pace.y, pace.z}, dist, ArcherRules.ARROW_SPEED,
            ArcherRules.ARROW_LEAD_MAX);
        Vec3 want = new Vec3(aim[0], aim[1], aim[2]).subtract(this.position());
        if (want.lengthSqr() < 1.0E-6) return;
        double[] cur = {v.x / speed, v.y / speed, v.z / speed};
        double[] d = ArcherRules.turn(cur, new double[]{want.x, want.y, want.z}, ArcherRules.arrowTurn(dist));
        this.setDeltaMovement(d[0] * ArcherRules.ARROW_SPEED, d[1] * ArcherRules.ARROW_SPEED, d[2] * ArcherRules.ARROW_SPEED);
        this.needsSync = true;
    }

    private void dissolve() {
        if (this.level() instanceof ServerLevel level) {
            Fx.particles(level, Fx.dust(0xFF8A5A, 0.9f), this.position(), 6, 0.15, 0.0);
        }
        this.discard();
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return super.canHitEntity(e) && !this.hit.contains(e.getId()) && e != this.getOwner();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        if (!(this.level() instanceof ServerLevel level) || !this.hit.add(target.getId())) return;
        Entity owner = this.getOwner();
        DamageSource source = this.damageSources().arrow(this, owner == null ? this : owner);
        float damage = ArcherRules.arrowDamage(this.random.nextFloat());
        if (target instanceof LivingEntity living) Judgement.fresh(living);
        else target.setInvulnerableTime(0);
        boolean hurt = target.hurtServer(level, source, damage);
        Fx.particles(level, ParticleTypes.CRIT, result.getLocation(), 6, 0.2, 0.3);
        Fx.particles(level, Fx.dust(0xFF5A3A, 1.0f), result.getLocation(), 6, 0.2, 0.1);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ARROW_HIT, SoundSource.PLAYERS, 0.7f, 1.4f);
        if (!hurt && target.isAlive()) {
            // Stopped (Infinity, a shield, a dodge): it falls away and fades.
            this.stalled = true;
            this.setDeltaMovement(this.getDeltaMovement().scale(-0.05));
            this.stuck = ArcherRules.ARROW_STUCK_TICKS - 10;
            return;
        }
        dissolve();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        this.setNoGravity(true);
        this.stalled = true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Override
    protected float getWaterInertia() {
        return 0.95f;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
