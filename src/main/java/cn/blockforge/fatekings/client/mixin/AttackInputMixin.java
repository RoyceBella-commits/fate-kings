package cn.blockforge.fatekings.client.mixin;

import cn.blockforge.fatekings.client.SlashInput;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every press of the attack key: the knight's stroke gets its angle, EMIYA's blades their next
 * stroke; sneaking with the black bow it is the triple shot instead of a blow.
 */
@Mixin(Minecraft.class)
public abstract class AttackInputMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void fatekings$slash(CallbackInfoReturnable<Boolean> cir) {
        if (cn.blockforge.fatekings.client.TwinInput.onBowAttack((Minecraft)(Object)this)) {
            cir.setReturnValue(false);
            return;
        }
        SlashInput.onAttack((Minecraft)(Object)this);
        cn.blockforge.fatekings.client.TwinInput.onAttack((Minecraft)(Object)this);
    }

    /** Holding the attack key in the triple-shot stance does not mine the block in front either. */
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void fatekings$noMining(boolean down, CallbackInfo ci) {
        if (down && cn.blockforge.fatekings.client.TwinInput.bowStance((Minecraft)(Object)this)) ci.cancel();
    }
}
