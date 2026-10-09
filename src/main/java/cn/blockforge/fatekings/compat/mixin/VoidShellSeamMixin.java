package cn.blockforge.fatekings.compat.mixin;

import cn.blockforge.fatekings.client.render.DomainSeam;
import cn.blockforge.ryomensukuna.m2a542fea.entity.VoidDomainEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Unlimited Void meeting Unlimited Blade Works: the void keeps its side of the seam, as it does
 * against a Malevolent Shrine (its own renderer only looks for the shrine).
 */
@Pseudo
@Mixin(targets = "cn.blockforge.ryomensukuna.m2a542fea.client.render.GojoRenderers$VoidShell", remap = false)
public abstract class VoidShellSeamMixin {
    @Inject(method = "clashClip", at = @At("RETURN"), cancellable = true, require = 0)
    private static void fatekings$marbleSeam(VoidDomainEntity e, Vec3 c, float radius, float delta, CallbackInfoReturnable<Object> cir) {
        if (cir.getReturnValue() != null) return;
        Object clip = DomainSeam.voidClip((Entity)(Object)e, c, radius, delta);
        if (clip != null) cir.setReturnValue(clip);
    }
}
