package cn.blockforge.fatekings.npc;

import cn.blockforge.fatekings.hero.HeroPassives;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.KingState;
import cn.blockforge.fatekings.king.Kings;
import cn.blockforge.fatekings.knight.ExcaliburSkill;
import cn.blockforge.fatekings.knight.KnightPassives;
import cn.blockforge.fatekings.voice.Voice;
import cn.blockforge.fatekings.voice.VoicePlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A king from a spawn egg, at full strength (the same values as a player wearing the full set).
 * Not a monster: a king only fights those who earn it. Cooldowns, temper and the Ea count are
 * saved with the entity.
 */
public abstract class KingNpcEntity extends PathfinderMob {
    protected KingState kingState = KingState.fresh();
    private boolean goldPierced;
    protected int combatTicks;
    protected float takenThisFight;
    protected LivingEntity lastFoe;
    private boolean greeted;

    protected KingNpcEntity(EntityType<? extends KingNpcEntity> type, Level level) {
        super(type, level);
        this.xpReward = 80;
        this.setPersistenceRequired();
        this.kingState.gold = KingRules.NPC_GOLD_HP;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, KingRules.NPC_MAX_HEALTH)
            .add(Attributes.ARMOR, KingRules.ARMOR)
            .add(Attributes.ARMOR_TOUGHNESS, KingRules.TOUGHNESS)
            .add(Attributes.ATTACK_DAMAGE, KingRules.UNARMED)
            .add(Attributes.MOVEMENT_SPEED, 0.34)
            .add(Attributes.FOLLOW_RANGE, 64.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
            .add(Attributes.STEP_HEIGHT, 1.0);
    }

    public abstract int kingType();

    /** Whom this king fights of their own accord. */
    public abstract boolean canHarm(LivingEntity target);

    protected abstract Voice spawnLine();

    protected abstract void kingTick(ServerLevel level, LivingEntity target, boolean fighting);

    public KingState kingState() {
        return this.kingState;
    }

    /** Artoria's last stand; Gilgamesh has none. */
    public boolean lastStand() {
        return false;
    }

    /** A hit landed on this king (after its taken share). */
    public void onHurt(DamageSource source, float taken) {
        this.takenThisFight += taken;
    }

    protected static boolean ignoredPlayer(LivingEntity e) {
        return e instanceof Player p && (p.isCreative() || p.isSpectator());
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.7));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 10.0f));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, true,
            (target, level) -> !ignoredPlayer(target) && this.canHarm(target)));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level) || !this.isAlive()) return;
        long now = level.getGameTime();
        this.kingState.king = kingType();
        var maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null && maxHealth.getBaseValue() < KingRules.NPC_MAX_HEALTH) {
            // An NPC from a 1.0.0 world: raise it to the new 200 health.
            maxHealth.setBaseValue(KingRules.NPC_MAX_HEALTH);
            this.setHealth(this.getMaxHealth());
        }
        if (!this.greeted && this.tickCount > 10) {
            this.greeted = true;
            VoicePlayer.say(this, spawnLine());
        }
        if (this.kingState.gold < KingRules.NPC_GOLD_HP && now - this.kingState.lastHurt >= KingRules.GOLD_REGEN_DELAY
            && (now - this.kingState.lastHurt) % KingRules.GOLD_REGEN_INTERVAL == 0) {
            this.kingState.gold = Math.min(KingRules.NPC_GOLD_HP, this.kingState.gold + 5.0f);
        }
        if (kingType() == KingRules.HERO) HeroPassives.tick(this, this.kingState, now);
        else {
            KnightPassives.tick(this, this.kingState, now);
            ExcaliburSkill.tickDash(level, this);
            cn.blockforge.fatekings.knight.KnightLeap.tick(this);
            if (this.onGround()) this.kingState.airDashUsed = false;
        }
        this.resetFallDistance();
        LivingEntity target = this.getTarget();
        boolean fighting = target != null && target.isAlive() && target.level() == this.level() && !ignoredPlayer(target);
        if (fighting) {
            ++this.combatTicks;
            if (target != this.lastFoe) {
                this.lastFoe = target;
                this.combatTicks = 0;
            }
        } else {
            if (this.combatTicks > 0 && (this.lastFoe == null || !this.lastFoe.isAlive())) endFight();
            this.combatTicks = 0;
            target = null;
        }
        kingTick(level, target, fighting);
    }

    /** A fight is over: the temper cools down. */
    protected void endFight() {
        this.takenThisFight = 0.0f;
    }

    public boolean fighting() {
        return this.combatTicks > 0;
    }

    /** Set around one blow that goes past the gold hearts straight to health (Ea / Excalibur on an NPC). */
    public void pierceGold(boolean on) {
        this.goldPierced = on;
    }

    @Override
    protected void actuallyHurt(ServerLevel level, DamageSource source, float amount) {
        // Gold hearts first (as for players).
        this.kingState.lastHurt = level.getGameTime();
        this.kingState.lastCombat = this.kingState.lastHurt;
        if (this.goldPierced) {
            super.actuallyHurt(level, source, amount);
            return;
        }
        float[] split = KingRules.splitDamage(this.kingState.gold, amount);
        this.kingState.gold -= split[0];
        if (split[1] > 0.0f) super.actuallyHurt(level, source, split[1]);
    }

    public float goldHearts() {
        return this.kingState.gold;
    }

    protected Vec3 aimDir(LivingEntity target) {
        Vec3 d = target.getBoundingBox().getCenter().subtract(this.getEyePosition());
        return d.lengthSqr() < 1.0E-4 ? this.getViewVector(1.0f) : d.normalize();
    }

    /** Faces the target exactly (for noble phantasms fired along the view). */
    protected void faceExactly(Vec3 point) {
        Vec3 d = point.subtract(this.getEyePosition());
        float yaw = (float)Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float)-Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        this.setYRot(yaw);
        this.setYHeadRot(yaw);
        this.yBodyRot = yaw;
        this.setXRot(pitch);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.store("FateKing", KingState.CODEC, this.kingState);
        CompoundTag ai = new CompoundTag();
        writeAi(ai);
        out.store("FateAi", CompoundTag.CODEC, ai);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        in.read("FateKing", KingState.CODEC).ifPresent(s -> this.kingState = s);
        readAi(in.read("FateAi", CompoundTag.CODEC).orElseGet(CompoundTag::new));
    }

    protected void writeAi(CompoundTag tag) {
    }

    protected void readAi(CompoundTag tag) {
    }

    @Override
    protected void dropFromLootTable(ServerLevel level, DamageSource source, boolean causedByPlayer) {
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    /** The winner of a duel between the two kings, if {@code killer} is the other one. */
    protected static boolean rival(Entity killer, int king) {
        return killer instanceof LivingEntity l && Kings.king(l) == king;
    }
}
