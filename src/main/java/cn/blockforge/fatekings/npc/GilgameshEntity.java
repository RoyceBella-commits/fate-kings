package cn.blockforge.fatekings.npc;

import cn.blockforge.fatekings.combat.JudgementRules.Side;
import cn.blockforge.fatekings.combat.Sides;
import cn.blockforge.fatekings.compat.JjkCompat;
import cn.blockforge.fatekings.hero.BabIluItem;
import cn.blockforge.fatekings.hero.EaItem;
import cn.blockforge.fatekings.hero.Enkidu;
import cn.blockforge.fatekings.hero.GateOfBabylon;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.Skills;
import cn.blockforge.fatekings.knight.KnightPassives;
import cn.blockforge.fatekings.registry.FateDamage;
import cn.blockforge.fatekings.registry.FateItems;
import cn.blockforge.fatekings.voice.Voice;
import cn.blockforge.fatekings.voice.VoicePlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Gilgamesh, a hostile being (Unlimited Void holds him, Gojo goes after him). He fights floating in
 * the air, looking down on everything. Arrogant (a stray treasure now and then, no dodging) ->
 * displeased (more gates, Enkidu) -> serious (volleys, rings, binding chains, keeps his distance) ->
 * Ea, but only against the worthy and only when it is truly needed (180 s cooldown, twice per fight
 * at most). Against a strong foe he pairs Enkidu with his treasures: chains whenever they are ready,
 * treasures while the foe is held. His gates take every foe near him in turn. He does not stoop to
 * strike those who cannot fight. Against Saber: chains first, serious at her first blow, Ea as soon
 * as he is hurt enough.
 */
public class GilgameshEntity extends KingNpcEntity implements Enemy {
    private static final String EA_COOLDOWN = "npc_ea";
    private int tier = KingAiRules.ARROGANT;
    private int shotCooldown = 40;
    private int eaUsed;
    private int infinityBlocked;
    private boolean hitByExcalibur;
    private boolean firstHit;
    private boolean proposed;
    private int ritual = -1;
    private int charge = -1;
    /** Ticks into the opening volley (-1: none); whether this fight has had it. */
    private int opening = -1;
    private boolean openingDone;
    private int ambientLine = 200;
    private int disdainCooldown;
    /** Last tick he was fighting (or drawing Ea): he stays aloft for 5 s after that. */
    private int lastBattleTick = -1000;
    private double orbit;

    public GilgameshEntity(EntityType<? extends GilgameshEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public int kingType() {
        return KingRules.HERO;
    }

    @Override
    protected Voice spawnLine() {
        return Voice.GIL_SPAWN;
    }

    @Override
    public boolean canHarm(LivingEntity t) {
        if (t == this || !t.isAlive() || Sides.noncombatant(t)) return false;
        if (t instanceof ArtoriaEntity || t instanceof EmiyaEntity) return true;
        Side side = Sides.side(t);
        if (side == Side.GOJO || side == Side.SUKUNA || side == Side.MAHORAGA) return !(t instanceof Player);
        // "Mongrel, who allowed you to look upon the king?" Hostile monsters within 16 blocks.
        return t instanceof Enemy && !(t instanceof GilgameshEntity) && this.distanceToSqr(t) < 16.0 * 16.0;
    }

    private boolean vsSaber(LivingEntity t) {
        return t != null && Sides.side(t) == Side.KNIGHT;
    }

    @Override
    public void onHurt(DamageSource source, float taken) {
        super.onHurt(source, taken);
        Entity attacker = source.getEntity();
        if (FateDamage.is(source, FateDamage.EXCALIBUR)) this.hitByExcalibur = true;
        if (attacker instanceof LivingEntity l && l != this) {
            if (!this.firstHit) {
                this.firstHit = true;
                VoicePlayer.say(this, Voice.GIL_HURT);
            }
            boolean unworthy = Sides.side(l) == Side.VANILLA || Sides.side(l) == Side.PLAYER;
            int before = this.tier;
            LivingEntity foe = this.getTarget() != null ? this.getTarget() : l;
            this.tier = KingAiRules.gilTierAfterHit(this.tier, this.takenThisFight, unworthy, Sides.worthy(l),
                this.getHealth() / this.getMaxHealth(), Sides.worthy(foe), vsSaber(l) || vsSaber(foe));
            if (this.tier != before) {
                VoicePlayer.say(this, this.tier == KingAiRules.SERIOUS ? Voice.GIL_SERIOUS : Voice.GIL_DISPLEASED);
            }
        }
    }

    @Override
    protected void endFight() {
        super.endFight();
        this.tier = KingAiRules.ARROGANT;
        this.eaUsed = 0;
        this.hitByExcalibur = false;
        this.firstHit = false;
        this.proposed = false;
        this.infinityBlocked = 0;
        this.openingDone = false;
        if (this.opening >= 0) {
            this.opening = -1;
            GateOfBabylon.cancelVolley(this);
        }
    }

    @Override
    protected void kingTick(ServerLevel level, LivingEntity target, boolean fighting) {
        long now = level.getGameTime();
        if (this.disdainCooldown > 0) --this.disdainCooldown;
        if (target != null && Sides.noncombatant(target)) {
            // Beneath him: a sneer, no blow.
            this.setTarget(null);
            if (this.disdainCooldown <= 0) {
                this.disdainCooldown = 200;
                VoicePlayer.say(this, Voice.GIL_DISDAIN);
            }
            target = null;
            fighting = false;
        }
        if (fighting || this.ritual >= 0 || this.charge >= 0) this.lastBattleTick = this.tickCount;
        hover(level, target);
        // The ritual and the charge run to the end regardless of what else happens.
        if (this.ritual >= 0) {
            tickRitual(level, target);
            return;
        }
        if (this.charge >= 0) {
            tickCharge(level, target);
            return;
        }
        if (this.opening >= 0 && (!fighting || target == null)) {
            // The foe is gone: the gates close unfired.
            this.opening = -1;
            GateOfBabylon.cancelVolley(this);
        }
        lookForSaber(level);
        if (!fighting) {
            if (--this.ambientLine <= 0) this.ambientLine = 400 + this.random.nextInt(400);
            return;
        }
        this.getLookControl().setLookAt(target, 30.0f, 30.0f);
        double dist = this.distanceTo(target);
        this.infinityBlocked = JjkCompat.infinityUp(target) ? this.infinityBlocked + 1 : 0;
        boolean saber = vsSaber(target);
        if (saber && !this.proposed) {
            this.proposed = true;
            VoicePlayer.say(this, Voice.GIL_PROPOSAL);
            if (this.kingState.ready(Skills.ENKIDU_BIND, now)) Enkidu.bindTarget(level, this, target);
        }
        if (this.tier == KingAiRules.ARROGANT && --this.ambientLine <= 0) {
            this.ambientLine = 300 + this.random.nextInt(300);
            VoicePlayer.say(this, Voice.GIL_ARROGANT);
        }
        move(level, target, dist);
        if (this.kingState.reorganizing(now)) return; // treasury reorganisation: fists only
        if (this.opening >= 0) {
            tickOpening(target);
            return;
        }
        boolean domain = JjkCompat.LOADED && !level.getEntities(this, this.getBoundingBox().inflate(40.0), JjkCompat::domain).isEmpty()
            || cn.blockforge.fatekings.entity.UbwEntity.foreignNear(this, 40.0);
        boolean mahoragaAdapted = JjkCompat.is(target, JjkCompat.MAHORAGA) && this.combatTicks > 320;
        boolean eaReady = this.kingState.ready(EA_COOLDOWN, now);
        if (KingAiRules.gilWantsEa(this.tier, Sides.worthy(target), this.getHealth() / this.getMaxHealth(), this.infinityBlocked,
                mahoragaAdapted, domain, eaReady, this.eaUsed, saber, this.hitByExcalibur) && this.hasLineOfSight(target)) {
            startRitual(level, target);
            return;
        }
        boolean strongFoe = Sides.worthy(target) || saber;
        if (KingAiRules.gilOpensWithVolley(this.openingDone, strongFoe, this.kingState.ready(Skills.GOB_VOLLEY, now)) && this.hasLineOfSight(target)
                && aloft(level)) {
            // "Rejoice: the treasury of the king is open to you." Once past his arrogance he holds
            // the foe in Enkidu while the gates open.
            this.openingDone = true;
            this.opening = 0;
            if (KingAiRules.gilChainCombo(this.tier, true, this.kingState.ready(Skills.ENKIDU_BIND, now))) {
                Enkidu.bindTarget(level, this, target);
                this.kingState.cooldown(Skills.ENKIDU_BIND, now, KingAiRules.COMBO_BIND_COOLDOWN);
            }
            GateOfBabylon.npcVolleyStart(this, target);
            return;
        }
        if (--this.shotCooldown > 0 || !this.hasLineOfSight(target)) return;
        if (target instanceof net.minecraft.world.entity.Mob mob && JjkCompat.fromJjk(target) && mob.getTarget() != this) {
            // "An insolent greeting": one treasure, and the sorcerer turns on him.
            mob.setTarget(this);
        }
        boolean strong = Sides.worthy(target) || saber;
        if (KingAiRules.gilChainCombo(this.tier, strong, this.kingState.ready(Skills.ENKIDU_BIND, now))) {
            // "Enkidu!" and the gates open on the one held.
            Enkidu.bindTarget(level, this, target);
            this.kingState.cooldown(Skills.ENKIDU_BIND, now, KingAiRules.COMBO_BIND_COOLDOWN);
            GateOfBabylon.npcShots(this, target, KingAiRules.gilGates(this.tier, this.random.nextFloat()));
            this.shotCooldown = 30 + this.random.nextInt(15);
            return;
        }
        if (strong && this.tier != KingAiRules.ARROGANT && Enkidu.bound(target)) {
            // Held by the chains: nothing but treasures until they let go.
            GateOfBabylon.npcShots(this, target, KingAiRules.gilGates(this.tier, this.random.nextFloat()));
            this.shotCooldown = 35 + this.random.nextInt(15);
            return;
        }
        switch (this.tier) {
            case KingAiRules.ARROGANT -> {
                GateOfBabylon.npcShots(this, target, KingAiRules.gilGates(this.tier, this.random.nextFloat()));
                this.shotCooldown = 40 + this.random.nextInt(20);
            }
            case KingAiRules.DISPLEASED -> {
                if (dist > 10.0 && this.kingState.ready(Skills.ENKIDU_HOOK, now) && this.random.nextInt(3) == 0) {
                    this.kingState.cooldown(Skills.ENKIDU_HOOK, now, KingRules.ENKIDU_HOOK * 3);
                    Enkidu.bindTarget(level, this, target);
                } else {
                    GateOfBabylon.npcShots(this, target, KingAiRules.gilGates(this.tier, this.random.nextFloat()));
                }
                this.shotCooldown = 30 + this.random.nextInt(15);
            }
            default -> {
                int roll = this.random.nextInt(6);
                if (roll == 0 && this.kingState.ready(Skills.ENKIDU_BIND, now)) {
                    Enkidu.bindTarget(level, this, target);
                } else if (roll == 1 && this.kingState.ready(Skills.GOB_RING, now)) {
                    GateOfBabylon.ring(this);
                } else {
                    GateOfBabylon.npcShots(this, target, KingAiRules.gilGates(this.tier, this.random.nextFloat()));
                    if (this.random.nextInt(3) == 0) VoicePlayer.say(this, Voice.GIL_VOLLEY);
                }
                this.shotCooldown = 50 + this.random.nextInt(30);
            }
        }
    }

    /**
     * Risen into the air (the opening volley waits for it, so its wall opens behind him up there):
     * 3 blocks up, or 2 s into the fight wherever he is (under a low roof he never gets that high).
     */
    private boolean aloft(ServerLevel level) {
        if (this.combatTicks >= 40) return true;
        return this.isNoGravity() && this.getY() - level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, this.getBlockX(), this.getBlockZ()) >= 3.0;
    }

    /** The opening volley: gates open behind him for 4 s (5 growing to 100), then all fire at once. */
    private void tickOpening(LivingEntity target) {
        ++this.opening;
        GateOfBabylon.growVolley(this, this.opening);
        if (this.opening >= KingRules.GOB_VOLLEY_MIN && !GateOfBabylon.volleyHeld(this)) {
            this.opening = -1; // the treasury refused (cooldown, reorganisation)
            return;
        }
        if (this.opening >= KingRules.GOB_VOLLEY_FULL) {
            GateOfBabylon.npcReleaseVolley(this, target);
            this.opening = -1;
            this.shotCooldown = 60;
        }
    }

    /** On the ground a fist is all the unworthy get; the treasures come from above. */
    private void move(ServerLevel level, LivingEntity target, double dist) {
        if (dist < 3.0 && this.tickCount % 20 == 0) this.doHurtTarget(level, target);
    }

    /**
     * He fights floating in the air, looking down on everyone: 6+ blocks up, above his foe, at a
     * distance that grows with his temper. After 5 s without a fight he sinks slowly back down.
     */
    private void hover(ServerLevel level, LivingEntity target) {
        if (KnightPassives.chained(this)) {
            this.setNoGravity(false);
            return;
        }
        boolean aloft = this.tickCount - this.lastBattleTick < 100;
        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, this.getBlockX(), this.getBlockZ());
        if (!aloft) {
            if (this.isNoGravity()) {
                if (this.onGround() || this.getY() - groundY < 0.6) {
                    this.setNoGravity(false);
                } else {
                    this.setDeltaMovement(this.getDeltaMovement().multiply(0.5, 0.0, 0.5).add(0.0, -0.15, 0.0));
                }
            }
            return;
        }
        this.setNoGravity(true);
        this.getNavigation().stop();
        Vec3 goal;
        if (this.opening >= 0) {
            // The gates open behind him where he hangs: he stays put until they fire.
            goal = this.position();
            if (target != null) this.getLookControl().setLookAt(target, 30.0f, 60.0f);
        } else if (target == null || !target.isAlive() || this.ritual >= 0 || this.charge >= 0) {
            goal = new Vec3(this.getX(), Math.max(this.getY(), groundY + 6.0), this.getZ());
        } else {
            Vec3 flat = this.position().subtract(target.position()).multiply(1.0, 0.0, 1.0);
            double dist = flat.length();
            Vec3 away = dist < 1.0E-3 ? new Vec3(1, 0, 0) : flat.scale(1.0 / dist);
            double near = this.tier == KingAiRules.SERIOUS ? 12.0 : this.tier == KingAiRules.DISPLEASED ? 8.0 : 5.0;
            double far = this.tier == KingAiRules.SERIOUS ? 26.0 : this.tier == KingAiRules.DISPLEASED ? 18.0 : Math.max(dist, 6.0);
            double r = Math.max(near, Math.min(far, dist));
            // A slow drift around the foe, so he never hangs quite still.
            this.orbit += this.tier == KingAiRules.ARROGANT ? 0.003 : 0.008;
            double c = Math.cos(this.orbit * 0.3), s = Math.sin(this.orbit * 0.3);
            Vec3 dir = new Vec3(away.x * c - away.z * s, 0.0, away.x * s + away.z * c);
            int targetGround = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target.getBlockX(), target.getBlockZ());
            double y = Math.max(targetGround + 6.0, target.getY() + (this.tier == KingAiRules.SERIOUS ? 7.0 : 5.0));
            goal = new Vec3(target.getX() + dir.x * r, y, target.getZ() + dir.z * r);
            this.getLookControl().setLookAt(target, 30.0f, 60.0f);
        }
        Vec3 d = goal.subtract(this.position());
        double len = d.length();
        Vec3 v = len < 0.05 ? Vec3.ZERO : d.scale(Math.min(0.4, len * 0.12) / len);
        if (!level.noCollision(this, this.getBoundingBox().move(v))) v = new Vec3(0.0, 0.25, 0.0);
        this.setDeltaMovement(this.getDeltaMovement().scale(0.4).add(v.scale(0.6)));
        this.resetFallDistance();
        if (this.tickCount % 4 == 0) {
            level.sendParticles(cn.blockforge.fatekings.combat.Fx.dust(0xFFD34A, 0.9f), this.getX(), this.getY() - 0.1, this.getZ(), 3, 0.3, 0.05, 0.3, 0.0);
        }
    }

    /** Saber first; failing her, the faker. */
    private void lookForSaber(ServerLevel level) {
        if (this.getTarget() instanceof ArtoriaEntity || this.tickCount % 20 != 0) return;
        for (ArtoriaEntity a : level.getEntitiesOfClass(ArtoriaEntity.class, this.getBoundingBox().inflate(48.0), ArtoriaEntity::isAlive)) {
            if (this.hasLineOfSight(a) && (this.getTarget() == null || this.getHealth() > this.getMaxHealth() * 0.5f)) {
                this.setTarget(a);
                this.tier = Math.max(this.tier, KingAiRules.ARROGANT);
                return;
            }
        }
        if (this.getTarget() instanceof EmiyaEntity) return;
        for (EmiyaEntity e : level.getEntitiesOfClass(EmiyaEntity.class, this.getBoundingBox().inflate(48.0), EmiyaEntity::isAlive)) {
            if (this.hasLineOfSight(e) && this.getTarget() == null) {
                this.setTarget(e);
                VoicePlayer.say(this, Voice.GIL_FAKER);
                return;
            }
        }
    }

    private void startRitual(ServerLevel level, LivingEntity target) {
        this.ritual = 0;
        this.getNavigation().stop();
        if (this.eaUsed >= 1) VoicePlayer.say(this, Voice.GIL_SECOND_EA);
        BabIluItem.beginRitual(level, this);
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(FateItems.BAB_ILU));
    }

    private void tickRitual(ServerLevel level, LivingEntity target) {
        ++this.ritual;
        this.getNavigation().stop();
        if (target != null) this.getLookControl().setLookAt(target, 60.0f, 60.0f);
        BabIluItem.ritualTick(level, this, this.ritual);
        if (this.ritual >= KingRules.BAB_ILU_TICKS) {
            this.ritual = -1;
            long now = level.getGameTime();
            this.setItemSlot(EquipmentSlot.MAINHAND, EaItem.draw(level, ItemStack.EMPTY, now + KingRules.EA_LIFETIME));
            VoicePlayer.say(this, Voice.GIL_EA_DRAWN);
            this.charge = 0;
            this.startUsingItem(InteractionHand.MAIN_HAND);
        }
    }

    private void tickCharge(ServerLevel level, LivingEntity target) {
        ++this.charge;
        this.getNavigation().stop();
        if (target != null && target.isAlive()) {
            faceExactly(target.getBoundingBox().getCenter());
            this.getLookControl().setLookAt(target, 90.0f, 90.0f);
        }
        if (!this.isUsingItem() && this.getMainHandItem().is(FateItems.EA)) this.startUsingItem(InteractionHand.MAIN_HAND);
        if (this.charge >= KingRules.EA_CHARGE + 10) {
            this.charge = -1;
            long now = level.getGameTime();
            if (target != null && target.isAlive()) faceExactly(target.getBoundingBox().getCenter());
            if (this.isUsingItem()) {
                this.releaseUsingItem();
            } else {
                EaItem.release(this, KingRules.EA_CHARGE + 10, target);
            }
            this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            ++this.eaUsed;
            this.kingState.cooldown(EA_COOLDOWN, now, KingRules.EA_NPC);
        }
    }

    @Override
    public void die(DamageSource source) {
        VoicePlayer.say(this, rival(source.getEntity(), KingRules.KNIGHT) ? Voice.GIL_DEFEAT_SABER : Voice.GIL_DEFEAT);
        GateOfBabylon.cancelVolley(this);
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        // Server side only: the client's copy of him shares the UUID (and, in single player, the JVM).
        if (!this.level().isClientSide()) GateOfBabylon.cancelVolley(this);
        super.remove(reason);
    }

    @Override
    public boolean killedEntity(ServerLevel level, LivingEntity victim, DamageSource source) {
        if (Sides.worthy(victim) || victim instanceof Player) VoicePlayer.say(this, Voice.GIL_VICTORY);
        return super.killedEntity(level, victim, source);
    }

    @Override
    protected void writeAi(CompoundTag tag) {
        tag.putInt("Tier", this.tier);
        tag.putInt("EaUsed", this.eaUsed);
    }

    @Override
    protected void readAi(CompoundTag tag) {
        this.tier = tag.getIntOr("Tier", KingAiRules.ARROGANT);
        this.eaUsed = tag.getIntOr("EaUsed", 0);
    }

    public int tier() {
        return this.tier;
    }

    public boolean unlocking() {
        return this.ritual >= 0;
    }

    public boolean chargingEa() {
        return this.charge >= 0;
    }

    public AABB searchBox(double r) {
        return this.getBoundingBox().inflate(r);
    }
}
