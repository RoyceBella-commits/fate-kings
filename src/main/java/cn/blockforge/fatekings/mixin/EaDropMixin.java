package cn.blockforge.fatekings.mixin;

import cn.blockforge.fatekings.hero.EaItem;
import cn.blockforge.fatekings.registry.FateItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ea thrown out of the hand dissolves at once; its key goes back to the one who threw it. A projected
 * copy on the ground fades the same tick.
 */
@Mixin(ItemEntity.class)
public abstract class EaDropMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void fatekings$ea(CallbackInfo ci) {
        ItemEntity self = (ItemEntity)(Object)this;
        ItemStack stack = self.getItem();
        if (cn.blockforge.fatekings.archer.Projection.projected(stack) && self.level() instanceof ServerLevel copyLevel) {
            cn.blockforge.fatekings.archer.Projection.fade(copyLevel, self.getX(), self.getY() + 0.3, self.getZ());
            self.discard();
            ci.cancel();
            return;
        }
        if (!stack.is(FateItems.EA) || !(self.level() instanceof ServerLevel level)) return;
        ItemStack key = EaItem.keyOf(level, stack);
        if (self.getOwner() instanceof Player p && p.isAlive()) {
            if (!p.getInventory().add(key) && p.level() instanceof ServerLevel sl) p.spawnAtLocation(sl, key);
        } else {
            self.setItem(key);
            return;
        }
        level.sendParticles(cn.blockforge.fatekings.combat.Fx.dust(0xFFD34A, 1.2f), self.getX(), self.getY() + 0.3, self.getZ(), 12, 0.2, 0.2, 0.2, 0.0);
        self.discard();
        ci.cancel();
    }
}
