package cn.blockforge.fatekings.compat.mixin;

import cn.blockforge.fatekings.compat.Restraint;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No Unlimited Void while chained or wounded by Excalibur. */
@Pseudo
@Mixin(targets = "cn.blockforge.ryomensukuna.m2a542fea.gojo.GojoSkills", remap = false)
public abstract class VoidCastMixin {
    @Inject(method = "expandVoid(Lnet/minecraft/world/entity/LivingEntity;II)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void fatekings$noVoid(LivingEntity caster, int castId, int duration, CallbackInfo ci) {
        if (Restraint.noDomain(caster)) ci.cancel();
    }
}
