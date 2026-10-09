package cn.blockforge.fatekings.npc;

import cn.blockforge.fatekings.archer.ArcherBow;
import cn.blockforge.fatekings.archer.ArcherRules;
import cn.blockforge.fatekings.archer.Arsenal;
import cn.blockforge.fatekings.archer.CraneWing;
import cn.blockforge.fatekings.archer.Projection;
import cn.blockforge.fatekings.archer.RhoAias;
import cn.blockforge.fatekings.archer.TwinBlades;
import cn.blockforge.fatekings.archer.UnlimitedBladeWorks;
import cn.blockforge.fatekings.combat.JudgementRules.Side;
import cn.blockforge.fatekings.combat.LineOfFire;
import cn.blockforge.fatekings.combat.Sides;
import cn.blockforge.fatekings.combat.Targets;
import cn.blockforge.fatekings.compat.JjkCompat;
import cn.blockforge.fatekings.entity.BeamEntity;
import cn.blockforge.fatekings.entity.GatePortalEntity;
import cn.blockforge.fatekings.entity.UbwEntity;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.Skills;
import cn.blockforge.fatekings.registry.FateItems;
import cn.blockforge.fatekings.voice.Voice;
import cn.blockforge.fatekings.voice.VoicePlayer;
import java.util.EnumSet;
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
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * EMIYA, Archer. Not a monster: a guardian who protects villagers and players and puts down hostile
 * things. He fights at range with the black bow (homing arrows, strafing at 10-20 blocks), draws
 * Kanshou and Bakuya when a foe closes in, throws them and finishes with Crane Wing; raises Rho Aias
 * against volleys and beams; looses Caladbolg II at the strong; and against the worthy (and always
 * against the King of Heroes, his natural enemy) unfolds Unlimited Blade Works. He never fights
 * Artoria. He remembers every weapon he has seen, Excalibur included, and may trace his foe's own.
 */
public class EmiyaEntity extends KingNpcEntity {
    private Arsenal.Data arsenal = new Arsenal.Data();
    private int tier = KingAiRules.EMIYA_CALM;
    private boolean swords;
    private int shotCooldown = 20;
    private int skillCooldown = 40;
    private int infinityBlocked;
    private boolean ubwUsed;
    private boolean seriousSaid;
    private boolean rivalSaid;
    private boolean startSaid;
    private int traceCooldown = 300;
    private long lastProvoked;

    public EmiyaEntity(EntityType<? extends EmiyaEntity> type, Level level) {
        super(type, level);
        equipBow();
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
        this.setDropChance(EquipmentSlot.OFFHAND, 0.0f);
        long now = level.getGameTime();
        for (ItemStack s : Arsenal.defaults()) Arsenal.record(this.arsenal, s, now);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData data) {
        equipBow();
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    private void equipBow() {
        this.swords = false;
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(FateItems.BLACK_BOW));
        this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
    }

    private void equipSwords() {
        this.swords = true;
        ItemStack main = this.getMainHandItem();
        if (!(FateItems.twinSword(main) || Projection.projected(main) && !main.is(FateItems.EXCALIBUR_REPLICA))) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(FateItems.KANSHOU));
        }
        if (FateItems.twinSword(this.getMainHandItem())) this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(FateItems.BAKUYA));
    }

    public Arsenal.Data arsenal() {
        return this.arsenal;
    }

    @Override
    public int kingType() {
        return KingRules.ARCHER;
    }

    @Override
    protected Voice spawnLine() {
        return Voice.EMIYA_SPAWN;
    }

    @Override
    public boolean canHarm(LivingEntity t) {
        if (t == this || !t.isAlive() || t instanceof ArtoriaEntity) return false;
        if (t instanceof GilgameshEntity) return true;
        Side side = Sides.side(t);
        if (side == Side.SUKUNA || side == Side.MAHORAGA) return !(t instanceof Player);
        // A guardian: hostile things within 24 blocks.
        return t instanceof Enemy && this.distanceToSqr(t) < 24.0 * 24.0;
    }

    @Override
    protected Class<?>[] neverRetaliate() {
        return new Class<?>[]{ArtoriaEntity.class};
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(2, new BattleGoal());
    }

    @Override
    public void onHurt(DamageSource source, float taken) {
        super.onHurt(source, taken);
        if (source.getEntity() instanceof LivingEntity l && l != this) this.lastProvoked = this.level().getGameTime();
        if (this.random.nextInt(4) == 0 && taken > 2.0f) VoicePlayer.say(this, Voice.EMIYA_HURT);
    }

    /** Instinct of a kind: he sees Excalibur raised and remembers it. */
    public void onWarning(Entity source, String key) {
        if (key.equals("fatekings.warn.excalibur") && Arsenal.of(this) != null) {
            Arsenal.record(this.arsenal, new ItemStack(FateItems.EXCALIBUR), this.level().getGameTime());
        }
    }

    private boolean seenExcalibur() {
        for (Arsenal.Entry e : this.arsenal.entries) if (e.stack().is(FateItems.EXCALIBUR)) return true;
        return false;
    }

    @Override
    protected void endFight() {
        super.endFight();
        this.ubwUsed = false;
        this.seriousSaid = false;
        this.rivalSaid = false;
        this.startSaid = false;
        this.tier = KingAiRules.EMIYA_CALM;
    }

    @Override
    protected void kingTick(ServerLevel level, LivingEntity target, boolean fighting) {
        long now = level.getGameTime();
        lookForRival(level);
        if (this.isUsingItem()) {
            tickCharge(level, target);
            return;
        }
        if (!fighting) {
            if (this.swords || !this.getMainHandItem().is(FateItems.BLACK_BOW)) equipBow();
            return;
        }
        if (target instanceof Player && now - this.lastProvoked > 600 && this.distanceTo(target) > 16.0) {
            this.setTarget(null);
            return;
        }
        if (!this.startSaid) {
            this.startSaid = true;
            if (!(target instanceof GilgameshEntity)) VoicePlayer.say(this, Voice.EMIYA_START);
        }
        float hp = this.getHealth() / this.getMaxHealth();
        boolean worthy = Sides.worthy(target);
        boolean strong = worthy || target instanceof GilgameshEntity;
        this.tier = Math.max(this.tier, KingAiRules.emiyaTier(hp, strong));
        if (this.tier == KingAiRules.EMIYA_SERIOUS && !this.seriousSaid) {
            this.seriousSaid = true;
            VoicePlayer.say(this, target instanceof GilgameshEntity ? Voice.EMIYA_VS_GIL : Voice.EMIYA_FULL_POWER);
        }
        double dist = this.distanceTo(target);
        this.infinityBlocked = JjkCompat.infinityUp(target) ? this.infinityBlocked + 1 : 0;

        // Defence first: the seven rings.
        if (KingAiRules.emiyaWantsRhoAias(this.kingState.ready(Skills.RHO_AIAS, now), gatesAimedAtMe(level), projectilesInbound(level), beamInbound(level))) {
            faceExactly(target.getBoundingBox().getCenter());
            RhoAias.cast(this);
        }
        boolean enemyDomain = JjkCompat.LOADED && !level.getEntities(this, this.getBoundingBox().inflate(40.0), JjkCompat::domain).isEmpty()
            || UbwEntity.foreignNear(this, 40.0);
        int hostiles = level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(24.0), e -> e instanceof Enemy && e.isAlive()).size();
        if (KingAiRules.emiyaWantsUbw(this.kingState.ready(Skills.UBW, now) && !UbwEntity.activeFor(this), this.ubwUsed,
                target instanceof GilgameshEntity, worthy, this.combatTicks, hp, enemyDomain, hostiles) && UnlimitedBladeWorks.mayChant(this)) {
            this.ubwUsed = true;
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(FateItems.UNLIMITED_BLADE_WORKS));
            this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            this.getNavigation().stop();
            this.startUsingItem(InteractionHand.MAIN_HAND);
            return;
        }
        if (KingAiRules.emiyaWantsReplica(seenExcalibur(), this.kingState.ready(Skills.EXCALIBUR_REPLICA, now), worthy, dist) && this.hasLineOfSight(target)) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(FateItems.EXCALIBUR_REPLICA));
            this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            this.getNavigation().stop();
            this.startUsingItem(InteractionHand.MAIN_HAND);
            return;
        }
        boolean neutral = target instanceof Animal && !(target instanceof Enemy);
        if (KingAiRules.emiyaWantsCaladbolg(this.kingState.ready(Skills.CALADBOLG, now), neutral, strong, this.combatTicks, this.infinityBlocked,
                hp, Sides.boss(target)) && dist > 8.0 && this.hasLineOfSight(target)) {
            equipBow();
            this.getNavigation().stop();
            this.startUsingItem(InteractionHand.MAIN_HAND);
            return;
        }
        // Bow or blades, by distance (and back to them after the aria or a replica).
        boolean wantSwords = KingAiRules.emiyaSwords(dist, this.swords);
        if (wantSwords) {
            ItemStack main = this.getMainHandItem();
            if (!this.swords && this.random.nextInt(3) == 0 && !VoicePlayer.talking(this)) VoicePlayer.say(this, Voice.EMIYA_HEAD_ON);
            if (!this.swords || !(FateItems.twinSword(main) || Projection.projected(main) && !main.is(FateItems.EXCALIBUR_REPLICA))) equipSwords();
        } else if (this.swords || !this.getMainHandItem().is(FateItems.BLACK_BOW)) {
            equipBow();
        }
        if (--this.skillCooldown <= 0) {
            this.skillCooldown = this.tier == KingAiRules.EMIYA_SERIOUS ? 30 : 50;
            if (KingAiRules.emiyaWantsCraneWing(this.kingState.ready(Skills.CRANE_WING, now), strong, hp, dist) && this.hasLineOfSight(target)) {
                equipSwords();
                faceExactly(target.getBoundingBox().getCenter());
                CraneWing.start(this);
            } else if (this.swords && dist > 4.0 && dist < 12.0 && this.kingState.ready(Skills.TWIN_THROW, now)) {
                faceExactly(target.getBoundingBox().getCenter());
                CraneWing.throwPair(this);
            }
        }
        if (!this.swords && --this.shotCooldown <= 0 && this.hasLineOfSight(target)) {
            this.shotCooldown = UbwEntity.inside(this) ? 6 : this.tier == KingAiRules.EMIYA_SERIOUS ? 8 : 12;
            faceExactly(target.getBoundingBox().getCenter());
            int ahead = Targets.inCone(this, this.getEyePosition(), target.getBoundingBox().getCenter().subtract(this.getEyePosition()).normalize(),
                ArcherRules.ARROW_START_CONE_COS, ArcherRules.ARROW_START_RANGE).size();
            if (KingAiRules.emiyaWantsTriple(this.kingState.ready(Skills.BOW_TRIPLE, now), ahead, this.random.nextFloat())) ArcherBow.triple(this);
            else ArcherBow.tap(this);
        }
        traceFoe(level, target);
    }

    /** Now and then he traces his foe's own weapon and fights with the copy. */
    private void traceFoe(ServerLevel level, LivingEntity target) {
        if (--this.traceCooldown > 0) return;
        this.traceCooldown = 600;
        if (this.random.nextInt(4) != 0) return;
        ItemStack weapon = target.getMainHandItem();
        if (Projection.check(weapon) != Projection.Check.OK || weapon.is(FateItems.EXCALIBUR)) return;
        Arsenal.record(this.arsenal, weapon, level.getGameTime());
        Projection.traced(level, this);
        if (this.swords) {
            this.setItemSlot(EquipmentSlot.MAINHAND, Projection.copy(weapon, this.getUUID(), level.getGameTime() + ArcherRules.PROJECTION_LIFETIME / 3, false));
            this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }
    }

    private int gatesAimedAtMe(ServerLevel level) {
        Vec3 me = this.getBoundingBox().getCenter();
        return level.getEntitiesOfClass(GatePortalEntity.class, this.getBoundingBox().inflate(48.0),
            g -> !g.fired() && g.aimPoint() != null && g.aimPoint().distanceToSqr(me) < 9.0).size();
    }

    private int projectilesInbound(ServerLevel level) {
        Vec3 me = this.getBoundingBox().getCenter();
        return level.getEntitiesOfClass(Projectile.class, this.getBoundingBox().inflate(16.0), p -> {
            if (p.getOwner() == this || p.getDeltaMovement().lengthSqr() < 0.25) return false;
            Vec3 to = me.subtract(p.position());
            return to.normalize().dot(p.getDeltaMovement().normalize()) > 0.9;
        }).size();
    }

    private boolean beamInbound(ServerLevel level) {
        Vec3 me = this.getBoundingBox().getCenter();
        for (BeamEntity b : level.getEntitiesOfClass(BeamEntity.class, this.getBoundingBox().inflate(160.0), b -> b.state() == BeamEntity.ADVANCING)) {
            if (this.getUUID().equals(b.ownerId())) continue;
            Vec3 front = b.frontPos();
            Vec3 to = me.subtract(front);
            if (to.length() < 30.0 && to.normalize().dot(b.dir()) > 0.8) return true;
        }
        return false;
    }

    private void lookForRival(ServerLevel level) {
        if (this.tickCount % 20 != 0 || this.getTarget() instanceof GilgameshEntity) return;
        for (GilgameshEntity g : level.getEntitiesOfClass(GilgameshEntity.class, this.getBoundingBox().inflate(48.0), GilgameshEntity::isAlive)) {
            if (this.hasLineOfSight(g)) {
                this.setTarget(g);
                if (!this.rivalSaid) {
                    this.rivalSaid = true;
                    VoicePlayer.say(this, Voice.EMIYA_VS_GIL);
                }
                return;
            }
        }
    }

    /** Charging Caladbolg, the replica, or chanting the aria: standing still, facing the foe. */
    private void tickCharge(ServerLevel level, LivingEntity target) {
        this.getNavigation().stop();
        int held = this.getTicksUsingItem();
        ItemStack using = this.getUseItem();
        if (using.is(FateItems.UNLIMITED_BLADE_WORKS)) {
            if (target != null) this.getLookControl().setLookAt(target, 30.0f, 30.0f);
            return;
        }
        if (target == null || !target.isAlive()) {
            if (held > 10) this.stopUsingItem();
            return;
        }
        faceExactly(target.getBoundingBox().getCenter());
        int want = using.is(FateItems.EXCALIBUR_REPLICA) ? ArcherRules.REPLICA_CHARGE + 10 : ArcherRules.CALADBOLG_CHARGE + 6;
        if (held < want) return;
        Vec3 origin = this.getEyePosition();
        Vec3 dir = target.getBoundingBox().getCenter().subtract(origin).normalize();
        double range = using.is(FateItems.EXCALIBUR_REPLICA) ? ArcherRules.REPLICA_RANGE : ArcherRules.CALADBOLG_RANGE;
        if (!LineOfFire.clear(level, this, origin, dir, range, 3.0)) {
            Vec3 up = dir.add(0.0, 0.45, 0.0).normalize();
            if (LineOfFire.clear(level, this, origin, up, range, 3.0)) {
                faceExactly(origin.add(up.scale(10.0)));
            } else {
                this.stopUsingItem();
                equipBow();
                return;
            }
        }
        this.releaseUsingItem();
        if (this.getMainHandItem().isEmpty() || !this.getMainHandItem().is(FateItems.BLACK_BOW)) equipBow();
    }

    /** With the twin blades: one of the six strokes. */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof LivingEntity l && FateItems.twinSword(this.getMainHandItem())) {
            TwinBlades.npcStroke(this, l, this.random.nextInt(ArcherRules.COMBO_STEPS));
        }
        return hit;
    }

    @Override
    public void die(DamageSource source) {
        VoicePlayer.say(this, Voice.EMIYA_DEFEAT);
        super.die(source);
    }

    @Override
    public boolean killedEntity(ServerLevel level, LivingEntity victim, DamageSource source) {
        if (Sides.worthy(victim) || victim instanceof Player) VoicePlayer.say(this, Voice.EMIYA_VICTORY);
        return super.killedEntity(level, victim, source);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.store("FateArsenal", Arsenal.Data.CODEC, this.arsenal);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        in.read("FateArsenal", Arsenal.Data.CODEC).ifPresent(d -> this.arsenal = d);
    }

    @Override
    protected void writeAi(CompoundTag tag) {
        tag.putInt("Tier", this.tier);
        tag.putBoolean("UbwUsed", this.ubwUsed);
    }

    @Override
    protected void readAi(CompoundTag tag) {
        this.tier = tag.getIntOr("Tier", KingAiRules.EMIYA_CALM);
        this.ubwUsed = tag.getBooleanOr("UbwUsed", false);
    }

    /**
     * Movement in a fight: with the bow, keep 10-20 blocks away and strafe; with the blades, close in
     * and strike every half second.
     */
    private final class BattleGoal extends Goal {
        private int strafeDir = 1;
        private int strafeTime;
        private int attackCooldown;

        BattleGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = EmiyaEntity.this.getTarget();
            return t != null && t.isAlive() && !EmiyaEntity.this.isUsingItem() && !CraneWing.running(EmiyaEntity.this);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            EmiyaEntity.this.getMoveControl().strafe(0.0f, 0.0f);
        }

        @Override
        public void tick() {
            EmiyaEntity self = EmiyaEntity.this;
            LivingEntity t = self.getTarget();
            if (t == null) return;
            self.getLookControl().setLookAt(t, 30.0f, 30.0f);
            double dist = self.distanceTo(t);
            if (--this.attackCooldown < 0) this.attackCooldown = 0;
            if (self.swords) {
                self.getNavigation().moveTo(t, 1.35);
                if (dist < 2.8 && this.attackCooldown <= 0 && self.level() instanceof ServerLevel level) {
                    this.attackCooldown = 10;
                    self.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT);
                    self.doHurtTarget(level, t);
                }
                return;
            }
            if (dist > 20.0) {
                self.getNavigation().moveTo(t, 1.1);
                return;
            }
            self.getNavigation().stop();
            if (++this.strafeTime > 40 || self.random.nextInt(60) == 0) {
                this.strafeTime = 0;
                this.strafeDir = -this.strafeDir;
            }
            float back = dist < 10.0 ? -0.5f : dist > 16.0 ? 0.4f : 0.0f;
            self.getMoveControl().strafe(back, 0.5f * this.strafeDir);
        }
    }
}
