package cn.blockforge.fatekings.mixin;

import cn.blockforge.fatekings.registry.FateEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** "圣剑之创": all healing is halved while Excalibur's wound lasts. */
@Mixin(LivingEntity.class)
public abstract class WoundHealMixin {
    @ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
    private float fatekings$halve(float amount) {
        LivingEntity self = (LivingEntity)(Object)this;
        return self.hasEffect(FateEffects.EXCALIBUR_WOUND) ? amount * 0.5f : amount;
    }
}
