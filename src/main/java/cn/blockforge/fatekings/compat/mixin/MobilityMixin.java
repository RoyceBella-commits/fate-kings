package cn.blockforge.fatekings.compat.mixin;

import cn.blockforge.fatekings.compat.Restraint;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Enkidu holds against spatial blinks and cursed leaps too. */
@Pseudo
@Mixin(targets = "cn.blockforge.ryomensukuna.m2a542fea.mobility.Mobility", remap = false)
public abstract class MobilityMixin {
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, require = 0)
    private static void fatekings$chained(ServerPlayer player, int kind, float forward, float strafe, CallbackInfo ci) {
        if (Restraint.chained(player)) ci.cancel();
    }
}
