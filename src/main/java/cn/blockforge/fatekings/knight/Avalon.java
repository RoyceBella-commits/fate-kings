package cn.blockforge.fatekings.knight;

import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.compat.JjkCompat;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.KingState;
import cn.blockforge.fatekings.king.Kings;
import cn.blockforge.fatekings.king.Skills;
import cn.blockforge.fatekings.registry.FateDamage;
import cn.blockforge.fatekings.voice.Voice;
import cn.blockforge.fatekings.voice.VoicePlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * Avalon, the Everdistant Utopia, in three layers: constant healing (see {@link KnightPassives}),
 * refusing one lethal wound every 240 s, and the full unfolding against a single ultimate every 300 s
 * (never for domains), which also gives the counter-attack: Excalibur ready again within 5 s.
 */
public final class Avalon {
    private static final ResourceKey<DamageType> WORLD_CUT = ResourceKey.create(Registries.DAMAGE_TYPE, JjkCompat.WORLD_CUT);
    private static final ResourceKey<DamageType> SURE_HIT = ResourceKey.create(Registries.DAMAGE_TYPE, JjkCompat.SURE_HIT);

    private Avalon() {
    }

    /** Enuma Elish, World Cut, Hollow Purple, another knight's Excalibur, or any hit taking half her life. */
    public static boolean ultimate(LivingEntity target, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.typeHolder().is(SURE_HIT)) return false;
        if (FateDamage.is(source, FateDamage.ENUMA_ELISH) || FateDamage.is(source, FateDamage.CALADBOLG) || source.typeHolder().is(WORLD_CUT)) return true;
        if (FateDamage.is(source, FateDamage.EXCALIBUR) && source.getEntity() != target) return true;
        if (purple(target, source)) return true;
        KingState s = Kings.of(target);
        float life = target.getHealth() + (s == null ? 0.0f : Math.max(0.0f, s.gold));
        return amount >= life * 0.5f;
    }

    /** Hollow Purple deals plain magic damage; it is recognised by the Purple entity next to the target. */
    private static boolean purple(LivingEntity target, DamageSource source) {
        if (!JjkCompat.LOADED) return false;
        Entity direct = source.getDirectEntity();
        if (direct != null && JjkCompat.is(direct, JjkCompat.MURASAKI)) return true;
        if (!source.is(DamageTypes.MAGIC)) return false;
        return !target.level().getEntities(target, new AABB(target.blockPosition()).inflate(14.0), e -> JjkCompat.is(e, JjkCompat.MURASAKI)).isEmpty();
    }

    public static boolean domeReady(LivingEntity knight) {
        KingState s = Kings.of(knight);
        return s != null && s.ready(Skills.AVALON_DOME, Kings.now(knight));
    }

    /** Unfolds the utopia: 3 s of immunity to everything, Excalibur ready again for 5 s. */
    public static void unfold(LivingEntity knight, KingState s, Entity against) {
        if (!(knight.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        s.domeUntil = now + KingRules.DOME;
        s.cooldown(Skills.AVALON_DOME, now, KingRules.AVALON_DOME);
        s.clearCooldown(Skills.EXCALIBUR);
        s.counterUntil = now + KingRules.AVALON_COUNTER;
        s.dirty = true;
        KnightPassives.cleanse(knight, true);
        Fx.event(level, Fx.DOME, knight, knight.position(), KingRules.DOME, 1.0f, 160.0);
        Fx.particles(level, Fx.dust(0xFFE38A, 1.5f), knight.getX(), knight.getY() + 1.0, knight.getZ(), 60, 1.2, 1.2, 1.2, 0.0);
        Fx.particles(level, Fx.dust(0x7FB2FF, 1.5f), knight.getX(), knight.getY() + 1.0, knight.getZ(), 40, 1.2, 1.2, 1.2, 0.0);
        level.playSound(null, knight.getX(), knight.getY(), knight.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 3.0f, 1.4f);
        level.playSound(null, knight.getX(), knight.getY(), knight.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 2.0f, 1.2f);
        VoicePlayer.say(knight, Voice.SABER_AVALON);
        if (against instanceof LivingEntity hero && Kings.isHero(hero)) {
            // "……Saber——！" He can only call her name.
            VoicePlayer.say(hero, Voice.GIL_SABER_NAME);
        }
    }

    /** Layer two: the scabbard that never lets its bearer bleed. Returns true when death is refused. */
    public static boolean refuseDeath(LivingEntity knight, DamageSource source) {
        KingState s = Kings.of(knight);
        if (s == null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        long now = Kings.now(knight);
        if (!s.ready(Skills.AVALON_LETHAL, now)) return false;
        s.cooldown(Skills.AVALON_LETHAL, now, KingRules.AVALON_LETHAL);
        s.guardUntil = now + KingRules.LETHAL_GUARD;
        s.healUntil = now + KingRules.LETHAL_GUARD + KingRules.LETHAL_HEAL;
        s.dirty = true;
        knight.setHealth(2.0f);
        if (knight.level() instanceof ServerLevel level) {
            Fx.particles(level, ParticleTypes.TOTEM_OF_UNDYING, knight.getX(), knight.getY() + 1.0, knight.getZ(), 40, 0.5, 0.8, 0.5, 0.3);
            Fx.particles(level, Fx.dust(0xFFE38A, 1.2f), knight.getX(), knight.getY() + 1.0, knight.getZ(), 30, 0.6, 0.8, 0.6, 0.0);
            level.playSound(null, knight.getX(), knight.getY(), knight.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2.0f, 1.2f);
        }
        return true;
    }
}
