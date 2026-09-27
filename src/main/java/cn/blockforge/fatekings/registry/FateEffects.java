package cn.blockforge.fatekings.registry;

import cn.blockforge.fatekings.FateKings;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class FateEffects {
    /** Enkidu: held fast (no walking, no jumping, no teleport, no techniques). Damage is dealt by the chain itself. */
    public static final Holder<MobEffect> HEAVENS_CHAIN = register("heavens_chain", new Plain(MobEffectCategory.HARMFUL, 0xE8B830)
        .addAttributeModifier(Attributes.MOVEMENT_SPEED, FateKings.id("heavens_chain"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
        .addAttributeModifier(Attributes.JUMP_STRENGTH, FateKings.id("heavens_chain_jump"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    /** Excalibur on Gojo: slower, weaker, half healing, no domain, for 12 s. */
    public static final Holder<MobEffect> EXCALIBUR_WOUND = register("excalibur_wound", new Plain(MobEffectCategory.HARMFUL, 0xFFE38A)
        .addAttributeModifier(Attributes.MOVEMENT_SPEED, FateKings.id("excalibur_wound"), -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
        .addAttributeModifier(Attributes.ATTACK_DAMAGE, FateKings.id("excalibur_wound_attack"), -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    /** Gilgamesh's disbelief after Avalon stops Enuma Elish (1.5 s). */
    public static final Holder<MobEffect> DISBELIEF = register("disbelief", new Plain(MobEffectCategory.HARMFUL, 0xC8141E)
        .addAttributeModifier(Attributes.MOVEMENT_SPEED, FateKings.id("disbelief"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    private FateEffects() {
    }

    private static Holder<MobEffect> register(String name, MobEffect effect) {
        return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, FateKings.id(name), effect);
    }

    public static void init() {
    }

    private static final class Plain extends MobEffect {
        Plain(MobEffectCategory category, int color) {
            super(category, color);
        }
    }
}
