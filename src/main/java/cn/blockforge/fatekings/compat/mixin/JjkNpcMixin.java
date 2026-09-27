package cn.blockforge.fatekings.compat.mixin;

import cn.blockforge.fatekings.compat.Restraint;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gojo / Sukuna NPCs held by Enkidu cast nothing and cannot close in; wounded by Excalibur, no domain. */
@Pseudo
@Mixin(targets = {"cn.blockforge.ryomensukuna.m2a542fea.entity.npc.GojoNpcEntity",
    "cn.blockforge.ryomensukuna.m2a542fea.entity.npc.SukunaNpcEntity"}, remap = false)
public abstract class JjkNpcMixin {
    @Inject(method = "useSkill", at = @At("HEAD"), cancellable = true, require = 0)
    private void fatekings$skill(ServerLevel level, LivingEntity target, double distance, CallbackInfoReturnable<Boolean> cir) {
        if (Restraint.chained(this)) cir.setReturnValue(false);
    }

    @Inject(method = "tryFinisher", at = @At("HEAD"), cancellable = true, require = 0)
    private void fatekings$finisher(ServerLevel level, LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (Restraint.chained(this)) cir.setReturnValue(false);
    }

    @Inject(method = "castDomain", at = @At("HEAD"), cancellable = true, require = 0)
    private void fatekings$domain(ServerLevel level, CallbackInfoReturnable<Boolean> cir) {
        if (Restraint.noDomain(this)) cir.setReturnValue(false);
    }

    @Inject(method = "gapClose", at = @At("HEAD"), cancellable = true, require = 0)
    private void fatekings$gap(ServerLevel level, LivingEntity target, CallbackInfo ci) {
        if (Restraint.chained(this)) ci.cancel();
    }

    @Inject(method = "counterFinisher", at = @At("HEAD"), cancellable = true, require = 0)
    private void fatekings$counter(ServerLevel level, LivingEntity target, CallbackInfo ci) {
        if (Restraint.chained(this)) ci.cancel();
    }
}
