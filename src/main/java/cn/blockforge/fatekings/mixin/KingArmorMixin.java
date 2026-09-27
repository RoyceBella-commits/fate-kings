package cn.blockforge.fatekings.mixin;

import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.Kings;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A king's armour reaches the vanilla cap of 30 even when another mod caps sorcerers lower (the higher
 * value wins, never the sum). Runs after other mods' caps (higher priority).
 */
@Mixin(value = LivingEntity.class, priority = 1100)
public abstract class KingArmorMixin {
    @Inject(method = "getArmorValue", at = @At("RETURN"), cancellable = true)
    private void fatekings$armor(CallbackInfoReturnable<Integer> cir) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (!Kings.isKing(self)) return;
        int own = (int)Math.min(KingRules.ARMOR, Math.floor(self.getAttributeValue(Attributes.ARMOR)));
        if (own > cir.getReturnValueI()) cir.setReturnValue(own);
    }
}
