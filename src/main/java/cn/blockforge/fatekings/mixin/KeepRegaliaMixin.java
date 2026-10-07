package cn.blockforge.fatekings.mixin;

import cn.blockforge.fatekings.king.Regalia;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The regalia stay on the body at death (game rule fatekings:keep_regalia): taken aside before drops. */
@Mixin(Player.class)
public abstract class KeepRegaliaMixin {
    @Inject(method = "dropEquipment", at = @At("HEAD"))
    private void fatekings$keep(ServerLevel level, CallbackInfo ci) {
        if ((Object)this instanceof ServerPlayer p) {
            // Projected copies never drop: they fade with their maker.
            cn.blockforge.fatekings.archer.Projection.dissipateAll(p);
            Regalia.stash(level, p);
        }
    }
}
