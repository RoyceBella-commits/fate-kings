package cn.blockforge.fatekings.knight;

import cn.blockforge.fatekings.combat.Sides;
import cn.blockforge.fatekings.compat.JjkCompat;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.Kings;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * Magic Resistance A: the best of any servant. Vanilla magical effects (harming, poison, weakness,
 * slowness, levitation, blindness, darkness, mining fatigue) do not take hold. Jujutsu is not
 * magecraft: effects from the Gojo / Sukuna side, or inside one of their domains, still do.
 */
public final class MagicResistance {
    private static final Set<Holder<MobEffect>> BLOCKED = Set.of(MobEffects.INSTANT_DAMAGE, MobEffects.POISON, MobEffects.WEAKNESS,
        MobEffects.SLOWNESS, MobEffects.LEVITATION, MobEffects.BLINDNESS, MobEffects.DARKNESS, MobEffects.MINING_FATIGUE);

    private MagicResistance() {
    }

    public static boolean blocks(LivingEntity target, MobEffectInstance effect, Entity source) {
        if (target.level().isClientSide() || Kings.king(target) != KingRules.KNIGHT || !BLOCKED.contains(effect.getEffect())) return false;
        if (source != null && (Sides.jjkSide(source) || JjkCompat.fromJjk(source))) return false;
        if (JjkCompat.LOADED && !target.level().getEntities(target, new AABB(target.blockPosition()).inflate(40.0), JjkCompat::domain).isEmpty()) {
            return false;
        }
        return true;
    }
}
