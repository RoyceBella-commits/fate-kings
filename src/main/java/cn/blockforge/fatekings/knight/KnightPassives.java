package cn.blockforge.fatekings.knight;

import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.KingState;
import cn.blockforge.fatekings.registry.FateEffects;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The King of Knights' passives: Avalon's healing (layer one) and the lethal-wound regeneration
 * (layer two's afterglow), the cleansing of ailments, the halted hunger and the dome's immunity.
 */
public final class KnightPassives {
    /** Ailments Avalon washes away every 3 s. */
    private static final Set<Holder<MobEffect>> CLEANSED = Set.of(MobEffects.POISON, MobEffects.WITHER, MobEffects.WEAKNESS,
        MobEffects.SLOWNESS, MobEffects.HUNGER, MobEffects.MINING_FATIGUE, MobEffects.NAUSEA, MobEffects.BLINDNESS, MobEffects.DARKNESS);

    private KnightPassives() {
    }

    public static void tick(LivingEntity e, KingState s, long now) {
        if (!(e.level() instanceof ServerLevel level) || !e.isAlive()) return;
        // Layer one: constant regeneration (paused by mana depletion and by another knight's Excalibur).
        if (now % 20 == 0 && now >= s.regenPausedUntil && !s.depleted(now) && e.getHealth() < e.getMaxHealth()) {
            e.heal(KingRules.avalonRegen(now - s.lastCombat));
            if (now % 40 == 0) {
                Fx.particles(level, Fx.dust(0xFFE38A, 0.7f), e.getX(), e.getY() + 1.0, e.getZ(), 3, 0.4, 0.5, 0.4, 0.0);
                Fx.particles(level, Fx.dust(0x7FB2FF, 0.7f), e.getX(), e.getY() + 1.0, e.getZ(), 2, 0.4, 0.5, 0.4, 0.0);
            }
        }
        // Layer two's afterglow: 20 hearts over 5 s after a lethal wound was refused.
        if (now < s.healUntil) {
            e.heal(40.0f / KingRules.LETHAL_HEAL);
        }
        if (now % 60 == 0) cleanse(e, false);
        if (s.domeActive(now)) {
            cleanse(e, true);
            e.setDeltaMovement(e.getDeltaMovement().multiply(0.0, 1.0, 0.0));
        }
        if (e instanceof Player p && p.getFoodData().getSaturationLevel() < 5.0f) {
            // "停止衰老": hunger never goes down.
            p.getFoodData().setSaturation(5.0f);
        }
    }

    /** Removes Avalon-cleansed ailments; {@code all} (inside the dome) removes every harmful effect. */
    public static void cleanse(LivingEntity e, boolean all) {
        List<Holder<MobEffect>> remove = new ArrayList<>();
        for (MobEffectInstance inst : e.getActiveEffects()) {
            Holder<MobEffect> effect = inst.getEffect();
            boolean harmful = effect.value().getCategory() == MobEffectCategory.HARMFUL;
            if (all ? harmful : CLEANSED.contains(effect)) remove.add(effect);
        }
        for (Holder<MobEffect> h : remove) e.removeEffect(h);
        if (!remove.isEmpty() && e.level() instanceof ServerLevel level) {
            Fx.particles(level, ParticleTypes.WAX_OFF, e.getX(), e.getY() + 1.0, e.getZ(), 6, 0.3, 0.5, 0.3, 0.0);
        }
    }

    public static boolean chained(LivingEntity e) {
        return e.hasEffect(FateEffects.HEAVENS_CHAIN);
    }
}
