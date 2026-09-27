package cn.blockforge.fatekings.mixin;

import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.Kings;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Riding A: the King of Knights steers any horse, donkey, mule or camel, saddle or not. */
@Mixin(AbstractHorse.class)
public abstract class KnightHorseMixin {
    @Inject(method = "getControllingPassenger", at = @At("RETURN"), cancellable = true)
    private void fatekings$ride(CallbackInfoReturnable<LivingEntity> cir) {
        if (cir.getReturnValue() != null) return;
        AbstractHorse self = (AbstractHorse)(Object)this;
        if (self.getFirstPassenger() instanceof Player p && Kings.king(p) == KingRules.KNIGHT) cir.setReturnValue(p);
    }
}
