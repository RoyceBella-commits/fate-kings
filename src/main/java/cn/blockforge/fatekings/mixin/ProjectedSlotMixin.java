package cn.blockforge.fatekings.mixin;

import cn.blockforge.fatekings.archer.Projection;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A projected copy put into any slot that is not its owner's inventory (chest, grid, anvil ...) simply fades. */
@Mixin(Slot.class)
public abstract class ProjectedSlotMixin {
    @Inject(method = "setByPlayer(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
    private void fatekings$noCopies(ItemStack stack, ItemStack previous, CallbackInfo ci) {
        Slot self = (Slot)(Object)this;
        if (!(self.container instanceof Inventory) && Projection.projected(stack)) ci.cancel();
    }
}
