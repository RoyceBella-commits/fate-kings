package cn.blockforge.fatekings.compat.mixin;

import cn.blockforge.fatekings.compat.Restraint;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Chained by Enkidu: no techniques; Excalibur's wound: no domain. */
@Pseudo
@Mixin(targets = "cn.blockforge.ryomensukuna.m2a542fea.skill.SkillDispatcher", remap = false)
public abstract class SkillCastMixin {
    @Inject(method = "cast", at = @At("HEAD"), cancellable = true, require = 0)
    private static void fatekings$restrain(ServerPlayer player, @Coerce Object skill, float charge, CallbackInfo ci) {
        String name = skill instanceof Enum<?> e ? e.name() : "";
        boolean domain = "DOMAIN".equals(name) || "VOID".equals(name);
        if (Restraint.chained(player) || domain && Restraint.noDomain(player)) ci.cancel();
    }
}
