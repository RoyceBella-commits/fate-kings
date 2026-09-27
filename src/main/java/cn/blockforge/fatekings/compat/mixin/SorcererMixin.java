package cn.blockforge.fatekings.compat.mixin;

import cn.blockforge.fatekings.king.Kings;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The two kings count as "mod characters" for the Gojo x Sukuna damage share (10%, not 1%). */
@Pseudo
@Mixin(targets = "cn.blockforge.ryomensukuna.m2a542fea.skill.CurseManager", remap = false)
public abstract class SorcererMixin {
    @Inject(method = "isSorcerer", at = @At("RETURN"), cancellable = true, require = 0)
    private static void fatekings$kings(Entity e, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && e != null && Kings.isKing(e)) cir.setReturnValue(true);
    }
}
