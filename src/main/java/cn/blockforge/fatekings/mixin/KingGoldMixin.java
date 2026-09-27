package cn.blockforge.fatekings.mixin;

import cn.blockforge.fatekings.king.Kings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Gold hearts: after armour and absorption, the king's gold takes the loss before red health. */
@Mixin(Player.class)
public abstract class KingGoldMixin {
    @ModifyArg(method = "actuallyHurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setHealth(F)V"), index = 0)
    private float fatekings$gold(float newHealth) {
        Player self = (Player)(Object)this;
        if (!(self instanceof ServerPlayer player)) return newHealth;
        float loss = player.getHealth() - newHealth;
        if (loss <= 0.0f) return newHealth;
        return player.getHealth() - Kings.absorb(player, loss);
    }
}
