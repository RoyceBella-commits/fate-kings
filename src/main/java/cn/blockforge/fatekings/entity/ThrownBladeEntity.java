package cn.blockforge.fatekings.entity;

import cn.blockforge.fatekings.archer.ArcherRules;
import cn.blockforge.fatekings.archer.RhoAias;
import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.combat.Judgement;
import cn.blockforge.fatekings.registry.FateDamage;
import cn.blockforge.fatekings.registry.FateEntities;
import cn.blockforge.fatekings.registry.FateItems;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
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
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Kanshou or Bakuya, thrown. The married blades call to each other: each sweeps out on a curve to
 * one side and closes on the target from there, then turns and comes back to the thrower's hand.
 * 16 damage a pass; Infinity and Rho Aias turn it back. Never saved.
 */
public class ThrownBladeEntity extends Entity {
    private static final EntityDataAccessor<ItemStack> WEAPON = SynchedEntityData.defineId(ThrownBladeEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final int OUT_TICKS = 12;
    private static final int BACK_TICKS = 12;
    private final IntOpenHashSet hit = new IntOpenHashSet();
    private UUID ownerId;
    private LivingEntity target;
    private Vec3 start;
    private Vec3 goal;
    private Vec3 bend;
    private boolean back;
    private int phaseAge;
    private float damage = ArcherRules.THROWN_BLADE_DAMAGE;

    public ThrownBladeEntity(EntityType<? extends ThrownBladeEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /**
     * Throws one blade from {@code hand} at {@code target} (or at {@code aimPoint}), swinging out by
     * {@code curve} blocks to the {@code side} (+1 right, -1 left) and {@code lift} up.
     */
    public static ThrownBladeEntity loose(ServerLevel level, LivingEntity owner, boolean kanshou, Vec3 hand, LivingEntity target, Vec3 aimPoint,
                                          double side, double curve, double lift, float damage) {
        ThrownBladeEntity b = new ThrownBladeEntity(FateEntities.THROWN_BLADE, level);
        b.ownerId = owner.getUUID();
        b.target = target;
        b.start = hand;
        b.goal = target != null ? target.getBoundingBox().getCenter() : aimPoint;
        Vec3 d = b.goal.subtract(hand);
        Vec3 right = d.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
        b.bend = right.scale(side * curve).add(0.0, lift, 0.0);
        b.damage = damage;
        b.entityData.set(WEAPON, new ItemStack(kanshou ? FateItems.KANSHOU : FateItems.BAKUYA));
        b.snapTo(hand.x, hand.y, hand.z, 0.0f, 0.0f);
        level.addFreshEntity(b);
        level.playSound(null, hand.x, hand.y, hand.z, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.9f, 1.3f + (kanshou ? 0.0f : 0.15f));
        return b;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(WEAPON, new ItemStack(FateItems.KANSHOU));
    }

    public ItemStack weapon() {
        return this.entityData.get(WEAPON);
    }

    private LivingEntity owner(ServerLevel level) {
        return this.ownerId != null && level.getEntity(this.ownerId) instanceof LivingEntity l && l.isAlive() ? l : null;
    }

    private static Vec3 bezier(Vec3 p0, Vec3 p1, Vec3 p2, double t) {
        double u = 1.0 - t;
        return p0.scale(u * u).add(p1.scale(2.0 * u * t)).add(p2.scale(t * t));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            if (this.tickCount % 2 == 0) this.level().addParticle(Fx.dust(weapon().is(FateItems.KANSHOU) ? 0x301010 : 0xF0F4FF, 0.8f),
                this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
            return;
        }
        LivingEntity owner = owner(level);
        if (owner == null || this.start == null || this.tickCount > 40) {
            this.discard();
            return;
        }
        Vec3 from = this.position();
        ++this.phaseAge;
        Vec3 to;
        if (!this.back) {
            // The married blades call to each other: the far end follows the foe.
            if (this.target != null && this.target.isAlive()) this.goal = this.target.getBoundingBox().getCenter();
            Vec3 mid = this.start.add(this.goal).scale(0.5).add(this.bend);
            to = bezier(this.start, mid, this.goal, Math.min(1.0, this.phaseAge / (double)OUT_TICKS));
            if (this.phaseAge >= OUT_TICKS) turnBack(to);
        } else {
            Vec3 hand = owner.getEyePosition().add(0.0, -0.4, 0.0);
            Vec3 mid = this.start.add(hand).scale(0.5).add(this.bend.scale(-0.6));
            double t = Math.min(1.0, this.phaseAge / (double)BACK_TICKS);
            to = bezier(this.start, mid, hand, t);
            if (t >= 1.0 || to.distanceToSqr(hand) < 1.0) {
                Fx.particles(level, Fx.dust(Fx.TRACE_CYAN, 0.7f), to, 4, 0.1, 0.0);
                this.discard();
                return;
            }
        }
        if (RhoAias.blocks(level, this, owner, from, to)) {
            turnBack(from);
            return;
        }
        strike(level, owner, from, to);
        this.setPos(to);
    }

    private void turnBack(Vec3 at) {
        if (this.back) return;
        this.back = true;
        this.phaseAge = 0;
        this.start = at;
        this.hit.clear();
    }

    private void strike(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 to) {
        AABB box = new AABB(from, to).inflate(1.2);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != owner && e.isAlive() && !e.isSpectator())) {
            if (e instanceof OwnableEntity pet && pet.getOwner() == owner || this.hit.contains(e.getId())) continue;
            if (owner instanceof cn.blockforge.fatekings.npc.KingNpcEntity npc && !npc.canHarm(e) && npc.getTarget() != e) continue;
            if (!(owner instanceof cn.blockforge.fatekings.npc.KingNpcEntity) && e != this.target && !cn.blockforge.fatekings.combat.Targets.hostileTo(owner, e)) continue;
            Vec3 c = e.getBoundingBox().getCenter();
            Vec3 seg = to.subtract(from);
            double len2 = Math.max(1.0E-6, seg.lengthSqr());
            double t = Math.max(0.0, Math.min(1.0, c.subtract(from).dot(seg) / len2));
            double reach = 1.0 + Math.max(e.getBbWidth(), e.getBbHeight()) * 0.5;
            if (c.distanceToSqr(from.add(seg.scale(t))) > reach * reach) continue;
            this.hit.add(e.getId());
            DamageSource source = FateDamage.source(level, FateDamage.THROWN_BLADE, this, owner);
            Judgement.fresh(e);
            boolean hurt = e.hurtServer(level, source, this.damage);
            Fx.particles(level, ParticleTypes.CRIT, c, 8, 0.3, 0.3);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.8f, 1.4f);
            if (!hurt || e == this.target) turnBack(this.position());
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
    protected void readAdditionalSaveData(ValueInput in) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
    }
}
