package cn.blockforge.fatekings.mixin;

import cn.blockforge.fatekings.archer.Projection;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A bundle will not hold a projected copy. */
@Mixin(BundleContents.Mutable.class)
public abstract class ProjectedBundleMixin {
    @Inject(method = "tryInsert", at = @At("HEAD"), cancellable = true)
    private void fatekings$noCopies(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (Projection.projected(stack)) cir.setReturnValue(0);
    }
}
