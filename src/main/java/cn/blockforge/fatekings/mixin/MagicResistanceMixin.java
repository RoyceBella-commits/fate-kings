package cn.blockforge.fatekings.mixin;

import cn.blockforge.fatekings.knight.MagicResistance;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Magic Resistance A: vanilla magical effects do not take hold on the King of Knights. */
@Mixin(LivingEntity.class)
public abstract class MagicResistanceMixin {
    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void fatekings$resist(MobEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> cir) {
        if (MagicResistance.blocks((LivingEntity)(Object)this, effect, source)) cir.setReturnValue(false);
    }
}
