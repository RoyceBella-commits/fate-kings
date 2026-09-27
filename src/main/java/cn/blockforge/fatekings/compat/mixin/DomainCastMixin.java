package cn.blockforge.fatekings.compat.mixin;

import cn.blockforge.fatekings.compat.Restraint;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No Malevolent Shrine while chained or wounded by Excalibur. */
@Pseudo
@Mixin(targets = "cn.blockforge.ryomensukuna.m2a542fea.skill.domain.DomainSkill", remap = false)
public abstract class DomainCastMixin {
    @Inject(method = "castFor", at = @At("HEAD"), cancellable = true, require = 0)
    private static void fatekings$noDomain(LivingEntity caster, int ticks, int castId, CallbackInfoReturnable<Boolean> cir) {
        if (Restraint.noDomain(caster)) cir.setReturnValue(false);
    }
}
