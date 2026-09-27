package cn.blockforge.fatekings.compat;

import cn.blockforge.fatekings.combat.Judgement;
import cn.blockforge.fatekings.hero.Enkidu;
import cn.blockforge.fatekings.registry.FateDamage;
import cn.blockforge.fatekings.registry.FateEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** What the compat mixins ask: which attacks pierce Infinity, who may not cast or teleport. */
public final class Restraint {
    private Restraint() {
    }

    /** Only these two pierce Infinity: Enuma Elish and Excalibur's true name. */
    public static boolean piercesInfinity(DamageSource source) {
        return FateDamage.is(source, FateDamage.ENUMA_ELISH) || FateDamage.is(source, FateDamage.EXCALIBUR);
    }

    public static void announcePierce(LivingEntity target, DamageSource source) {
        if (!(target.level() instanceof ServerLevel level) || !JjkCompat.infinityUp(target)) return;
        String key = FateDamage.is(source, FateDamage.ENUMA_ELISH) ? "fatekings.hint.infinity_ea" : "fatekings.hint.infinity_starlight";
        Judgement.infinityTorn(level, source.getEntity(), target, key);
    }

    /** Held by Enkidu: no techniques, no teleport, no leaps. */
    public static boolean chained(Object e) {
        return e instanceof LivingEntity l && (l.hasEffect(FateEffects.HEAVENS_CHAIN) || Enkidu.bound(l));
    }

    /** No domain while chained or bearing Excalibur's wound. */
    public static boolean noDomain(Object e) {
        return chained(e) || e instanceof LivingEntity l && l.hasEffect(FateEffects.EXCALIBUR_WOUND);
    }
}
