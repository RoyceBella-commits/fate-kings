package cn.blockforge.fatekings.client.mixin;

import cn.blockforge.fatekings.client.SlashInput;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every press of the attack key: the knight's stroke gets its angle. */
@Mixin(Minecraft.class)
public abstract class AttackInputMixin {
    @Inject(method = "startAttack", at = @At("HEAD"))
    private void fatekings$slash(CallbackInfoReturnable<Boolean> cir) {
        SlashInput.onAttack((Minecraft)(Object)this);
    }
}
