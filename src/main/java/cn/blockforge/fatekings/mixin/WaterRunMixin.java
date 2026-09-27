package cn.blockforge.fatekings.mixin;

import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.Kings;
import cn.blockforge.fatekings.knight.WarhorseItem;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The blessing of the Lady of the Lake: the King of Knights runs on water while she keeps moving (and
 * her warhorse with her); she sinks once she stops or sneaks.
 */
@Mixin(LivingEntity.class)
public abstract class WaterRunMixin {
    @Inject(method = "canStandOnFluid", at = @At("RETURN"), cancellable = true)
    private void fatekings$waterRun(FluidState fluid, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || !fluid.is(FluidTags.WATER)) return;
        LivingEntity self = (LivingEntity)(Object)this;
        if (self.isShiftKeyDown()) return;
        boolean knight = Kings.king(self) == KingRules.KNIGHT;
        // Vanilla stops sprinting as soon as the feet touch water, so moving counts as well as sprinting.
        if (KingRules.waterRun(knight, self.isSprinting(), self.getDeltaMovement().horizontalDistanceSqr(), self instanceof Mob)) {
            cir.setReturnValue(true);
            return;
        }
        if (WarhorseItem.isWarhorse(self) && self.getControllingPassenger() != null
            && Kings.king(self.getControllingPassenger()) == KingRules.KNIGHT) {
            cir.setReturnValue(true);
        }
    }
}
