package cn.blockforge.fatekings.combat;

import cn.blockforge.fatekings.combat.JudgementRules.Outcome;
import cn.blockforge.fatekings.combat.JudgementRules.Side;
import cn.blockforge.fatekings.combat.JudgementRules.Weapon;
import cn.blockforge.fatekings.compat.JjkCompat;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.KingState;
import cn.blockforge.fatekings.king.Kings;
import cn.blockforge.fatekings.net.FateNet;
import cn.blockforge.fatekings.npc.ArtoriaEntity;
import cn.blockforge.fatekings.npc.KingNpcEntity;
import cn.blockforge.fatekings.registry.FateDamage;
import cn.blockforge.fatekings.registry.FateEffects;
import cn.blockforge.fatekings.registry.FateRules;
import cn.blockforge.fatekings.voice.Voice;
import cn.blockforge.fatekings.voice.VoicePlayer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;

/**
 * Applies Enuma Elish / Excalibur to one target according to {@link JudgementRules}: instant death
 * for vanilla creatures (Excalibur), Mahoraga's wheel that cannot turn, Gojo left at one heart
 * (Excalibur), exactly 190 health off the Gojo / Sukuna / king NPCs, 1000 on other creatures and
 * 90-100 on players.
 */
public final class Judgement {
    /** Gojo crippled by a knight: that knight cannot kill him for 12 s (her chivalry). */
    private record Spared(UUID knight, long until) {
    }
    private static final Map<UUID, Spared> SPARED = new ConcurrentHashMap<>();

    private Judgement() {
    }

    public static void clear() {
        SPARED.clear();
    }

    /** Whether {@code attacker} may not deal the killing blow to {@code target} right now. */
    public static boolean spared(LivingEntity target, Entity attacker) {
        Spared s = SPARED.get(target.getUUID());
        if (s == null) return false;
        if (target.level().getGameTime() >= s.until) {
            SPARED.remove(target.getUUID());
            return false;
        }
        return attacker != null && attacker.getUUID().equals(s.knight);
    }

    /**
     * @param multiplier extra factor on character damage (Avalon's counter: x2 on the King of Heroes;
     *                   a clash lost to Ea: 0.5 of what is left)
     * @return whether the target was affected
     */
    public static boolean strike(ServerLevel level, LivingEntity caster, Entity carrier, LivingEntity target, Weapon weapon, float multiplier) {
        if (target == caster || !target.isAlive() || target.isSpectator()) return false;
        if (target instanceof Player p && p.isCreative()) return false;
        if (caster != null && (target.isPassengerOfSameVehicle(caster) || target.getVehicle() == caster)) return false;
        if (weapon == Weapon.EXCALIBUR && target instanceof OwnableEntity pet && caster != null && pet.getOwner() == caster
            && FateRules.get(level, FateRules.KNIGHT_SPARES_PETS)) {
            return false;
        }
        Side side = Sides.side(target);
        Outcome outcome = JudgementRules.outcome(weapon, side, !(target instanceof Player), FateRules.get(level, FateRules.EXCALIBUR_BOSS_INSTAKILL));
        return switch (outcome) {
            case WHEEL_CANNOT_TURN -> killMahoraga(level, caster, carrier, target, weapon);
            case INSTANT_DEATH -> instantDeath(level, caster, carrier, target);
            case CRIPPLE -> cripple(level, caster, target);
            case HEAVY_DAMAGE -> hurt(level, target, FateDamage.source(level, weapon == Weapon.EA ? FateDamage.ENUMA_ELISH_MOB : FateDamage.EXCALIBUR_MOB, carrier, caster), KingRules.MOB_DAMAGE);
            case NPC_BLOW -> woundAvalon(level, target, weapon, side, npcBlow(level, target, characterSource(level, carrier, caster, weapon), KingRules.NPC_PHANTASM_LOSS * multiplier));
            case CHARACTER_DAMAGE -> {
                float amount = KingRules.ultimateOnCharacter(level.getRandom().nextFloat()) * multiplier;
                yield woundAvalon(level, target, weapon, side, hurt(level, target, characterSource(level, carrier, caster, weapon), amount));
            }
        };
    }

    /** Pierces Infinity and armour; each side's share still applies. */
    private static DamageSource characterSource(ServerLevel level, Entity carrier, LivingEntity caster, Weapon weapon) {
        return FateDamage.source(level, weapon == Weapon.EA ? FateDamage.ENUMA_ELISH : FateDamage.EXCALIBUR, carrier, caster);
    }

    private static boolean hurt(ServerLevel level, LivingEntity target, DamageSource source, float amount) {
        target.setInvulnerableTime(0);
        return target.hurtServer(level, source, amount);
    }

    /**
     * Exactly {@code loss} health off an NPC once every share has been applied (this mod's and the
     * Gojo x Sukuna mod's): the blow is sized by their product. Armour is pierced by the damage type,
     * gold hearts are passed by for this one blow. Avalon, Infinity and the like still have their say.
     */
    private static boolean npcBlow(ServerLevel level, LivingEntity target, DamageSource source, float loss) {
        float scale = DamageShare.apply(target, source, 1.0f) * JjkCompat.takenScale(target, source);
        KingNpcEntity king = target instanceof KingNpcEntity k ? k : null;
        if (king != null) king.pierceGold(true);
        try {
            return hurt(level, target, source, KingRules.rawForLoss(loss, scale));
        } finally {
            if (king != null) king.pierceGold(false);
        }
    }

    /** Avalon does not heal wounds dealt by Excalibur. */
    private static boolean woundAvalon(ServerLevel level, LivingEntity target, Weapon weapon, Side side, boolean hit) {
        if (hit && weapon == Weapon.EXCALIBUR && side == Side.KNIGHT) {
            KingState s = Kings.of(target);
            if (s != null) s.regenPausedUntil = level.getGameTime() + KingRules.AVALON_REGEN_PAUSE;
        }
        return hit;
    }

    private static boolean instantDeath(ServerLevel level, LivingEntity caster, Entity carrier, LivingEntity target) {
        if (caster instanceof Player p) target.setLastHurtByPlayer(p, 100);
        else if (caster != null) target.setLastHurtByMob(caster);
        DamageSource source = FateDamage.source(level, FateDamage.JUDGEMENT, carrier, caster);
        target.setInvulnerableTime(0);
        target.hurtServer(level, source, Float.MAX_VALUE);
        if (target.isAlive() && target.getHealth() > 0.0f) {
            // Anything that shrugged the blow off (phase immunities, damage caps) still falls.
            target.setHealth(0.0f);
            if (!(target instanceof EnderDragon)) target.die(source);
        }
        Fx.particles(level, ParticleTypes.END_ROD, target.getBoundingBox().getCenter(), 8, 0.4, 0.1);
        return true;
    }

    private static boolean killMahoraga(ServerLevel level, LivingEntity caster, Entity carrier, LivingEntity target, Weapon weapon) {
        // Straight to death without going through its damage handling: no wheel, no adaptation, no last stand.
        if (caster instanceof Player p) target.setLastHurtByPlayer(p, 100);
        else if (caster != null) target.setLastHurtByMob(caster);
        DamageSource source = FateDamage.source(level, weapon == Weapon.EA ? FateDamage.ENUMA_ELISH : FateDamage.EXCALIBUR, carrier, caster);
        target.setHealth(0.0f);
        target.die(source);
        Component msg = Component.translatable("fatekings.hint.wheel_cannot_turn").withStyle(ChatFormatting.GOLD);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(target) < 96.0 * 96.0) FateNet.actionBar(p, msg);
        }
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 2.0f, 0.5f);
        if (caster != null) VoicePlayer.say(caster, weapon == Weapon.EA ? Voice.GIL_KILL_MAHORAGA : Voice.SABER_KILL_MAHORAGA);
        return true;
    }

    /**
     * Excalibur through Infinity: Gojo is left at one heart with no gold hearts, "圣剑之创" for 12 s,
     * and this knight cannot finish him in that time.
     */
    private static boolean cripple(ServerLevel level, LivingEntity knight, LivingEntity gojo) {
        gojo.setHealth(DamageRules.crippledHealth(gojo.getHealth()));
        if (gojo instanceof ServerPlayer sp) {
            JjkCompat.clearGold(sp);
            Fx.eventTo(sp, Fx.CRACKS, gojo, gojo.position(), KingRules.WOUND, 1.0f);
        }
        KingState ks = Kings.of(gojo);
        if (ks != null && Kings.isKing(gojo)) {
            ks.gold = 0.0f;
            ks.dirty = true;
        }
        gojo.addEffect(new MobEffectInstance(FateEffects.EXCALIBUR_WOUND, KingRules.WOUND, 0), knight);
        JjkCompat.holdBurstHeal(gojo, KingRules.WOUND);
        if (knight != null) {
            gojo.setLastHurtByMob(knight);
            SPARED.put(gojo.getUUID(), new Spared(knight.getUUID(), level.getGameTime() + KingRules.WOUND));
        }
        infinityTorn(level, knight, gojo, "fatekings.hint.infinity_starlight");
        if (knight instanceof ArtoriaEntity artoria) artoria.onCrippled(gojo);
        if (knight != null) VoicePlayer.say(knight, Voice.SABER_CRIPPLED_GOJO);
        Fx.particles(level, ParticleTypes.END_ROD, gojo.getBoundingBox().getCenter(), 20, 0.4, 0.1);
        level.playSound(null, gojo.getX(), gojo.getY(), gojo.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.5f, 0.6f);
        return true;
    }

    /** "无下限被「乖离剑」撕裂" / "无下限被「星之光」贯穿": shown to both sides. */
    public static void infinityTorn(ServerLevel level, Entity attacker, LivingEntity target, String key) {
        Component msg = Component.translatable(key).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        if (target instanceof ServerPlayer sp) FateNet.actionBar(sp, msg);
        if (attacker instanceof ServerPlayer sp) FateNet.actionBar(sp, msg);
        if (attacker instanceof KingNpcEntity || target instanceof KingNpcEntity) {
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(target) < 48.0 * 48.0 && p != target && p != attacker) FateNet.actionBar(p, msg);
            }
        }
    }
}
