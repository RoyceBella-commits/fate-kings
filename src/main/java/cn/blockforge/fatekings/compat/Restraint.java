package cn.blockforge.fatekings.compat;

import cn.blockforge.fatekings.combat.Judgement;
import cn.blockforge.fatekings.hero.Enkidu;
import cn.blockforge.fatekings.registry.FateDamage;
import cn.blockforge.fatekings.registry.FateEffects;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** What the compat mixins ask: which attacks pierce Infinity, who may not cast or teleport. */
public final class Restraint {
    private Restraint() {
    }

    /** Last pierce message per target (the swords of the reality marble would flood the action bar). */
    private static final Map<UUID, Long> ANNOUNCED = new ConcurrentHashMap<>();

    /**
     * What pierces Infinity: Enuma Elish, Excalibur's true name, Caladbolg II (a drill that twists
     * space itself) and the swords of Unlimited Blade Works (the reality marble's sure hit).
     */
    public static boolean piercesInfinity(DamageSource source) {
        return FateDamage.is(source, FateDamage.ENUMA_ELISH) || FateDamage.is(source, FateDamage.EXCALIBUR)
            || FateDamage.is(source, FateDamage.CALADBOLG) || FateDamage.is(source, FateDamage.UBW_SWORD);
    }

    public static void announcePierce(LivingEntity target, DamageSource source) {
        if (!(target.level() instanceof ServerLevel level) || !JjkCompat.infinityUp(target)) return;
        long now = level.getGameTime();
        if (now < ANNOUNCED.getOrDefault(target.getUUID(), Long.MIN_VALUE)) return;
        ANNOUNCED.put(target.getUUID(), now + 60);
        String key = FateDamage.is(source, FateDamage.ENUMA_ELISH) ? "fatekings.hint.infinity_ea"
            : FateDamage.is(source, FateDamage.CALADBOLG) ? "fatekings.hint.infinity_caladbolg"
            : FateDamage.is(source, FateDamage.UBW_SWORD) ? "fatekings.hint.infinity_ubw" : "fatekings.hint.infinity_starlight";
        Judgement.infinityTorn(level, source.getEntity(), target, key);
    }

    public static void clear() {
        ANNOUNCED.clear();
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
