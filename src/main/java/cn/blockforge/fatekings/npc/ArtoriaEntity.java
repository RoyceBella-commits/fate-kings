package cn.blockforge.fatekings.npc;

import cn.blockforge.fatekings.combat.JudgementRules.Side;
import cn.blockforge.fatekings.combat.Sides;
import cn.blockforge.fatekings.compat.JjkCompat;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.Skills;
import cn.blockforge.fatekings.knight.ExcaliburItem;
import cn.blockforge.fatekings.knight.ExcaliburSkill;
import cn.blockforge.fatekings.knight.KnightLeap;
import cn.blockforge.fatekings.knight.KnightPassives;
import cn.blockforge.fatekings.registry.FateItems;
import cn.blockforge.fatekings.voice.Voice;
import cn.blockforge.fatekings.voice.VoicePlayer;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Artoria. Knightly courtesy first (a salute, then a straight charge; she protects villagers and
 * players from monsters), full power against strong foes or below 70% health, Excalibur whenever it is
 * justified (never on neutral animals, never through villagers or houses), and a last stand below 25%.
 */
public class ArtoriaEntity extends KingNpcEntity {
    private int tier = KingAiRules.COURTESY;
    private UUID saluted;
    private int salute;
    private int skillCooldown = 30;
    private int leapCooldown = 40;
    private boolean foeUltimate;
    private boolean fullPowerSaid;
    private boolean lastStandSaid;
    private UUID spared;
    private long sparedUntil;
    private long lastProvoked;

    public ArtoriaEntity(EntityType<? extends ArtoriaEntity> type, Level level) {
        super(type, level);
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(FateItems.EXCALIBUR));
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData data) {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(FateItems.EXCALIBUR));
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    @Override
    public int kingType() {
        return KingRules.KNIGHT;
    }

    @Override
    protected Voice spawnLine() {
        return Voice.SABER_SPAWN;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.35, true) {
            @Override
            public boolean canUse() {
                return ArtoriaEntity.this.salute <= 0 && !ArtoriaEntity.this.isUsingItem() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return ArtoriaEntity.this.salute <= 0 && !ArtoriaEntity.this.isUsingItem() && super.canContinueToUse();
            }
        });
    }

    @Override
    public boolean canHarm(LivingEntity t) {
        if (t == this || !t.isAlive() || t instanceof EmiyaEntity) return false;
        if (spared(t)) return false;
        if (t instanceof GilgameshEntity) return true;
        Side side = Sides.side(t);
        if (side == Side.SUKUNA || side == Side.MAHORAGA) return !(t instanceof Player);
        // Protects villagers and players: monsters within 16 blocks.
        return t instanceof Enemy && this.distanceToSqr(t) < 16.0 * 16.0;
    }

    @Override
    protected Class<?>[] neverRetaliate() {
        return new Class<?>[]{EmiyaEntity.class};
    }

    private boolean spared(LivingEntity t) {
        return this.spared != null && t.getUUID().equals(this.spared) && this.level().getGameTime() < this.sparedUntil;
    }

    @Override
    public boolean lastStand() {
        return this.tier == KingAiRules.LAST_STAND;
    }

    /** Excalibur left Gojo at one heart: sword lowered, 12 s without striking him. */
    public void onCrippled(LivingEntity gojo) {
        this.spared = gojo.getUUID();
        this.sparedUntil = this.level().getGameTime() + KingRules.WOUND;
        if (this.getTarget() == gojo) this.setTarget(null);
        Vec3 away = this.position().subtract(gojo.position()).multiply(1.0, 0.0, 1.0);
        if (away.lengthSqr() > 1.0E-3) {
            Vec3 to = this.position().add(away.normalize().scale(8.0));
            this.getNavigation().moveTo(to.x, to.y, to.z, 1.0);
        }
    }

    /** Instinct: an ultimate is being prepared near by. Against the King of Heroes' key: counter at once. */
    public void onWarning(Entity source, String key) {
        if (!(source instanceof LivingEntity l) || l == this || spared(l)) return;
        boolean fromFoe = l == this.getTarget() || l instanceof GilgameshEntity || l instanceof Player && l == this.getLastHurtByMob()
            || Sides.side(l) == Side.SUKUNA || JjkCompat.domain(source);
        if (!fromFoe) return;
        this.foeUltimate = true;
        if (l instanceof GilgameshEntity || Sides.side(l) == Side.HERO) {
            this.setTarget(l);
            this.tier = Math.max(this.tier, KingAiRules.FULL_POWER);
        }
    }

    @Override
    public void onHurt(DamageSource source, float taken) {
        super.onHurt(source, taken);
        if (source.getEntity() instanceof LivingEntity l && l != this) {
            this.lastProvoked = this.level().getGameTime();
            if (Sides.gojoSide(l) && !spared(l)) this.setTarget(l);
        }
        if (this.random.nextInt(4) == 0 && taken > 2.0f) VoicePlayer.say(this, Voice.SABER_HURT);
    }

    @Override
    protected void endFight() {
        super.endFight();
        this.foeUltimate = false;
        this.fullPowerSaid = false;
        this.saluted = null;
    }

    @Override
    protected void kingTick(ServerLevel level, LivingEntity target, boolean fighting) {
        long now = level.getGameTime();
        if (!this.getMainHandItem().is(FateItems.EXCALIBUR)) this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(FateItems.EXCALIBUR));
        ExcaliburItem.setState(this.getMainHandItem(), ExcaliburItem.revealed(this, this.kingState, now) ? ExcaliburItem.REVEALED : ExcaliburItem.AIR);
        lookForRival(level);
        if (--this.leapCooldown < 0) this.leapCooldown = 0;
        if (this.isUsingItem()) {
            tickCharge(level, target);
            return;
        }
        if (!fighting) return;
        if (target instanceof Player && now - this.lastProvoked > 600 && this.distanceTo(target) > 16.0) {
            // The player stopped fighting: the sword is sheathed.
            this.setTarget(null);
            return;
        }
        float hp = this.getHealth() / this.getMaxHealth();
        boolean strong = Sides.worthy(target) || target instanceof GilgameshEntity;
        int newTier = KingAiRules.saberTier(hp, strong);
        if (newTier > this.tier || newTier == KingAiRules.COURTESY && this.tier != KingAiRules.LAST_STAND) this.tier = newTier;
        if (this.tier == KingAiRules.LAST_STAND && !this.lastStandSaid) {
            this.lastStandSaid = true;
            VoicePlayer.say(this, Voice.SABER_LAST_STAND);
        } else if (this.tier == KingAiRules.FULL_POWER && !this.fullPowerSaid) {
            this.fullPowerSaid = true;
            VoicePlayer.say(this, target instanceof GilgameshEntity ? Voice.SABER_VS_GIL
                : Sides.side(target) == Side.SUKUNA ? Voice.SABER_VS_SUKUNA : Voice.SABER_FULL_POWER);
        }
        // Courtesy: a salute before the first blow.
        if (this.tier == KingAiRules.COURTESY && !target.getUUID().equals(this.saluted)) {
            this.saluted = target.getUUID();
            this.salute = 10;
            if (!(target instanceof Enemy)) VoicePlayer.say(this, Voice.SABER_SALUTE);
        }
        if (this.salute > 0) {
            --this.salute;
            this.getNavigation().stop();
            this.getLookControl().setLookAt(target, 30.0f, 30.0f);
            return;
        }
        double dist = this.distanceTo(target);
        if (this.kingState.depleted(now)) return; // mana depletion: plain swings only
        boolean ready = this.kingState.ready(Skills.EXCALIBUR, now) || now < this.kingState.counterUntil;
        boolean neutral = target instanceof Animal && !(target instanceof Enemy);
        int hostiles = level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(16.0), e -> e instanceof Enemy && e.isAlive()).size();
        if (KingAiRules.saberWantsExcalibur(ready, neutral, strong, this.combatTicks, this.foeUltimate, Sides.boss(target), hp, hostiles,
                now < this.kingState.counterUntil, this.tier == KingAiRules.LAST_STAND) && this.hasLineOfSight(target)) {
            if (dist < 12.0 && this.leapCooldown <= 0 && this.onGround()) {
                // Needs distance for the holy sword: leap back first.
                faceExactly(target.getBoundingBox().getCenter());
                KnightLeap.leap(this, -1.0f, 0.0f, true);
                this.leapCooldown = 30;
                return;
            }
            if (dist >= 12.0 || this.leapCooldown > 0) {
                this.getNavigation().stop();
                this.startUsingItem(InteractionHand.MAIN_HAND);
                return;
            }
        }
        if (--this.skillCooldown > 0) return;
        this.skillCooldown = this.tier == KingAiRules.COURTESY ? 40 : 25;
        if (dist > 20.0 && this.leapCooldown <= 0 && this.onGround()) {
            faceExactly(target.getBoundingBox().getCenter());
            KnightLeap.leap(this, 0.0f, 0.0f, true);
            this.leapCooldown = 40;
        } else if (dist > 5.0 && dist < 16.0 && !ExcaliburSkill.dashing(this) && this.kingState.ready(Skills.MANA_BURST, now)) {
            faceExactly(target.getBoundingBox().getCenter());
            ExcaliburSkill.manaBurst(this);
        } else if (this.tier >= KingAiRules.FULL_POWER && dist > 6.0 && this.kingState.ready(Skills.STRIKE_AIR, now)) {
            faceExactly(target.getBoundingBox().getCenter());
            ExcaliburSkill.strikeAir(this);
        }
    }

    /** Gathering light: releases after 2-4 s, aimed where no villager, pet or house stands in the way. */
    private void tickCharge(ServerLevel level, LivingEntity target) {
        this.getNavigation().stop();
        int held = this.getTicksUsingItem();
        if (target == null || !target.isAlive()) {
            if (held > 10) {
                this.stopUsingItem();
                ExcaliburSkill.cancel(level, this);
            }
            return;
        }
        faceExactly(target.getBoundingBox().getCenter());
        int want = this.tier == KingAiRules.LAST_STAND ? 40 : 60 + (Sides.worthy(target) ? 20 : 0);
        if (held < want) return;
        Vec3 origin = this.getEyePosition();
        Vec3 dir = target.getBoundingBox().getCenter().subtract(origin).normalize();
        if (!clear(level, origin, dir)) {
            // Try firing slanted upwards (her habit), otherwise give up for Strike Air.
            Vec3 up = dir.add(0.0, 0.45, 0.0).normalize();
            if (clear(level, origin, up)) {
                faceExactly(origin.add(up.scale(10.0)));
            } else {
                this.stopUsingItem();
                ExcaliburSkill.cancel(level, this);
                ExcaliburSkill.strikeAir(this);
                return;
            }
        }
        this.releaseUsingItem();
    }

    /** Whether Excalibur may be released along {@code dir} from {@code origin} (tests). */
    public boolean lineClear(Vec3 origin, Vec3 dir) {
        return this.level() instanceof ServerLevel level && clear(level, origin, dir);
    }

    /** No villager, iron golem, pet or bed / chest / crafting table along the whole path. */
    private boolean clear(ServerLevel level, Vec3 origin, Vec3 dir) {
        return cn.blockforge.fatekings.combat.LineOfFire.clear(level, this, origin, dir, KingRules.EXCALIBUR_RANGE, KingRules.excaliburWidth(60) / 2.0 + 1.0);
    }

    private void lookForRival(ServerLevel level) {
        if (this.tickCount % 20 != 0 || this.getTarget() instanceof GilgameshEntity) return;
        for (GilgameshEntity g : level.getEntitiesOfClass(GilgameshEntity.class, this.getBoundingBox().inflate(48.0), GilgameshEntity::isAlive)) {
            if (this.hasLineOfSight(g) && (this.getTarget() == null || this.getHealth() > this.getMaxHealth() * 0.5f)) {
                this.setTarget(g);
                this.tier = Math.max(this.tier, KingAiRules.FULL_POWER);
                VoicePlayer.say(this, Voice.SABER_REPLY);
                return;
            }
        }
    }

    /** Every stroke has its angle (seen by everyone); with the blade revealed it cuts loose a crescent. */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        float roll = cn.blockforge.fatekings.knight.Slashes.COMBO[this.random.nextInt(cn.blockforge.fatekings.knight.Slashes.COMBO.length)];
        cn.blockforge.fatekings.knight.Slashes.swing(this, roll, target instanceof LivingEntity l ? l : null);
        return hit;
    }

    @Override
    public void die(DamageSource source) {
        VoicePlayer.say(this, Voice.SABER_DEFEAT);
        super.die(source);
    }

    @Override
    public boolean killedEntity(ServerLevel level, LivingEntity victim, DamageSource source) {
        if (Sides.worthy(victim) || victim instanceof Player) VoicePlayer.say(this, Voice.SABER_VICTORY);
        return super.killedEntity(level, victim, source);
    }

    @Override
    protected void writeAi(CompoundTag tag) {
        tag.putInt("Tier", this.tier);
    }

    @Override
    protected void readAi(CompoundTag tag) {
        this.tier = tag.getIntOr("Tier", KingAiRules.COURTESY);
    }

    public boolean chained() {
        return KnightPassives.chained(this);
    }
}
