package cn.blockforge.fatekings.compat.mixin;

import cn.blockforge.fatekings.compat.Restraint;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Enuma Elish and Excalibur go straight through Infinity (and nothing else of the two kings). */
@Pseudo
@Mixin(targets = "cn.blockforge.ryomensukuna.m2a542fea.gojo.Infinity", remap = false)
public abstract class InfinityMixin {
    @Inject(method = "allowDamage", at = @At("HEAD"), cancellable = true, require = 0)
    private static void fatekings$pierce(LivingEntity target, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (Restraint.piercesInfinity(source)) {
            Restraint.announcePierce(target, source);
            cir.setReturnValue(true);
        }
    }
}
