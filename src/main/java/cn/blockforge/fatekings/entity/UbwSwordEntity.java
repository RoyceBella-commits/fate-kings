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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A sword of the reality marble: it rises blade-up out of the hill, then flies at its foe, turning
 * hard to follow (the marble's sure hit; its damage pierces Infinity). An interceptor flies at a
 * treasure of the Gate of Babylon instead and both shatter. Not a projectile: no shield of Infinity
 * catches it. Never saved.
 */
public class UbwSwordEntity extends Entity {
    private static final EntityDataAccessor<ItemStack> WEAPON = SynchedEntityData.defineId(UbwSwordEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final int RISE = 6;
    private UUID ownerId;
    private Entity target;
    private boolean interceptor;

    public UbwSwordEntity(EntityType<? extends UbwSwordEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static UbwSwordEntity rise(ServerLevel level, LivingEntity owner, Vec3 ground, LivingEntity foe, ItemStack weapon) {
        UbwSwordEntity s = create(level, owner, ground.add(0.0, -0.6, 0.0), foe, weapon);
        s.setDeltaMovement(0.0, 0.35, 0.0);
        level.addFreshEntity(s);
        Fx.particles(level, Fx.dust(Fx.EMBER, 1.0f), ground.x, ground.y + 0.2, ground.z, 6, 0.3, 0.1, 0.3, 0.0);
        return s;
    }

    public static UbwSwordEntity intercept(ServerLevel level, LivingEntity owner, Vec3 from, Entity treasure, ItemStack weapon) {
        UbwSwordEntity s = create(level, owner, from, treasure, weapon);
        s.interceptor = true;
        s.tickCount = RISE;
        s.setDeltaMovement(treasure.position().subtract(from).normalize().scale(4.0));
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
    }

    public ItemStack weapon() {
        return this.entityData.get(WEAPON);
    }

    public boolean intercepting() {
        return this.interceptor;
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = this.getDeltaMovement();
        if (!(this.level() instanceof ServerLevel level)) {
            this.setPos(this.position().add(v));
            return;
        }
        LivingEntity owner = this.ownerId != null && level.getEntity(this.ownerId) instanceof LivingEntity l ? l : null;
        if (owner == null || this.tickCount > 40 || this.target == null || !this.target.isAlive()) {
            Fx.particles(level, Fx.dust(Fx.EMBER, 0.9f), this.position(), 4, 0.1, 0.0);
            this.discard();
            return;
        }
        Vec3 from = this.position();
        if (this.tickCount >= RISE) {
            double speed = this.interceptor ? 4.0 : 2.6;
            Vec3 aim = this.target.getBoundingBox().getCenter().subtract(from);
            double dist = aim.length();
            Vec3 cur = v.lengthSqr() < 1.0E-6 ? aim.normalize() : v.normalize();
            double[] d = ArcherRules.turn(new double[]{cur.x, cur.y, cur.z}, new double[]{aim.x, aim.y, aim.z},
                Math.toRadians(this.interceptor ? 60.0 : 35.0));
            v = new Vec3(d[0], d[1], d[2]).scale(Math.min(speed, Math.max(0.5, dist)));
            this.setDeltaMovement(v);
            if (this.tickCount == RISE) level.playSound(null, from.x, from.y, from.z, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.5f, 1.6f);
        }
        Vec3 to = from.add(v);
        if (RhoAias.blocks(level, this, owner, from, to)) {
            this.discard();
            return;
        }
        double reach = 0.6 + Math.max(this.target.getBbWidth(), this.target.getBbHeight()) * 0.5;
        if (this.tickCount >= RISE && closest(from, to, this.target.getBoundingBox().getCenter()) <= reach) {
            if (this.interceptor) {
                Vec3 at = this.target.position();
                this.target.discard();
                Fx.particles(level, ParticleTypes.CRIT, at, 10, 0.3, 0.4);
                Fx.particles(level, Fx.dust(Fx.GOLD, 1.0f), at, 6, 0.2, 0.0);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.5f, 1.8f);
            } else if (this.target instanceof LivingEntity foe) {
                DamageSource source = FateDamage.source(level, FateDamage.UBW_SWORD, this, owner);
                Judgement.fresh(foe);
                foe.hurtServer(level, source, ArcherRules.UBW_SWORD_DAMAGE);
                Fx.particles(level, ParticleTypes.CRIT, foe.getBoundingBox().getCenter(), 6, 0.3, 0.3);
                level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.6f, 1.2f);
            }
            this.discard();
            return;
        }
        this.setPos(to);
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
