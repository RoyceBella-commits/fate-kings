package cn.blockforge.fatekings.mixin;

import cn.blockforge.fatekings.archer.Projection;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A menu closing: projected copies left in its own slots (a grid, an anvil) fade before they would be handed back. */
@Mixin(AbstractContainerMenu.class)
public abstract class ProjectedMenuMixin {
    @Inject(method = "removed", at = @At("HEAD"))
    private void fatekings$purge(Player player, CallbackInfo ci) {
        if (!player.level().isClientSide()) Projection.purgeMenu((AbstractContainerMenu)(Object)this, player);
    }
}
