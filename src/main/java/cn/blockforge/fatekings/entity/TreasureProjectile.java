package cn.blockforge.fatekings.entity;

import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.registry.FateEntities;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
 * One treasure fired from the Gate of Babylon. It is an arrow as far as the world is concerned
 * (arrow damage, stopped by Infinity like arrows, adapted to and reflected by Mahoraga as arrows),
 * but it renders as the weapon it is and never drops: it returns to the treasury as gold light.
 */
public class TreasureProjectile extends AbstractArrow {
    public static final int NONE = 0;
    public static final int FIRE = 1;
    public static final int SLOW = 2;
    public static final int WITHER = 3;
    public static final int PIERCE = 4;
    public static final int LAUNCH = 5;
    private static final EntityDataAccessor<ItemStack> WEAPON = SynchedEntityData.defineId(TreasureProjectile.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> EFFECT = SynchedEntityData.defineId(TreasureProjectile.class, EntityDataSerializers.INT);
    private final IntOpenHashSet hit = new IntOpenHashSet();
    /** Single shots may be dodged by Artoria's Instinct (20%); volleys and rings may not. */
    private boolean single;
    private int groundTicks;

    public TreasureProjectile(EntityType<? extends TreasureProjectile> type, Level level) {
        super(type, level);
        this.pickup = Pickup.DISALLOWED;
        this.setNoGravity(true);
    }

    public static TreasureProjectile create(ServerLevel level, LivingEntity owner, Vec3 pos, ItemStack weapon, int effect, boolean single) {
        TreasureProjectile p = new TreasureProjectile(FateEntities.TREASURE, level);
        p.snapTo(pos.x, pos.y, pos.z, 0.0f, 0.0f);
        p.setOwner(owner);
        p.entityData.set(WEAPON, weapon.copy());
        p.entityData.set(EFFECT, effect);
        p.single = single;
        return p;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(WEAPON, new ItemStack(Items.IRON_SWORD));
        builder.define(EFFECT, NONE);
    }

    public ItemStack weapon() {
        return this.entityData.get(WEAPON);
    }

    public int effect() {
        return this.entityData.get(EFFECT);
    }

    public boolean single() {
        return this.single;
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(Items.ARROW);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            if (!this.isInGround()) {
                this.level().addParticle(Fx.dust(Fx.GOLD, 0.8f), this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
            }
            return;
        }
        if (this.isInGround()) {
            if (++this.groundTicks >= 60) recall();
        }
        if (this.tickCount > 200) recall();
    }

    /** Back into the treasury in a flash of gold. */
    public void recall() {
        if (this.level() instanceof ServerLevel level) {
            Fx.particles(level, Fx.dust(Fx.GOLD, 1.2f), this.position(), 10, 0.2, 0.0);
            Fx.particles(level, ParticleTypes.END_ROD, this.position(), 3, 0.1, 0.02);
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
        if (UbwEntity.shields(this)) {
            // Unlimited Blade Works: a projected blade was there first.
            UbwEntity.parried(level, result.getLocation());
            this.discard();
            return;
        }
        Entity owner = this.getOwner();
        DamageSource source = this.damageSources().arrow(this, owner == null ? this : owner);
        float damage = KingRules.GOB_DAMAGE_MIN + this.random.nextFloat() * (KingRules.GOB_DAMAGE_MAX - KingRules.GOB_DAMAGE_MIN);
        int effect = this.effect();
        if (effect == FIRE) target.igniteForSeconds(5.0f);
        boolean hurt = target.hurtServer(level, source, damage);
        if (hurt && target instanceof LivingEntity living) {
            switch (effect) {
                case SLOW -> living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2), owner);
                case WITHER -> living.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1), owner);
                case LAUNCH -> {
                    Vec3 v = this.getDeltaMovement().normalize();
                    living.push(v.x * 0.9, 0.7, v.z * 0.9);
                    living.needsSync = true;
                }
                default -> {
                }
            }
        }
        Fx.particles(level, ParticleTypes.CRIT, result.getLocation(), 8, 0.2, 0.3);
        Fx.particles(level, Fx.dust(Fx.GOLD, 1.0f), result.getLocation(), 6, 0.2, 0.1);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.6f, 1.2f + this.random.nextFloat() * 0.3f);
        if (effect == PIERCE && this.hit.size() < 4) return;
        if (!hurt && target.isAlive()) {
            // Stopped (Infinity, a shield, a dodge): fall away and go home shortly.
            this.setDeltaMovement(this.getDeltaMovement().scale(-0.05));
            this.groundTicks = 40;
            this.setInGround(false);
            return;
        }
        recall();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        this.setNoGravity(true);
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
}
