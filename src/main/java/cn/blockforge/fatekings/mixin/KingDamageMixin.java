package cn.blockforge.fatekings.mixin;

import cn.blockforge.fatekings.combat.DamageShare;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A king only takes 1% of a hit (10% from another mod character); /kill and the void are never reduced. */
@Mixin(LivingEntity.class)
public abstract class KingDamageMixin {
    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
    private float fatekings$share(float amount, @Local(argsOnly = true) DamageSource source) {
        return DamageShare.apply((LivingEntity)(Object)this, source, amount);
    }
}
