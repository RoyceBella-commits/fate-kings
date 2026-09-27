package cn.blockforge.fatekings.client.mixin;

import cn.blockforge.fatekings.client.KingPoses;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Excalibur is raised in both hands while gathering light; the key and Ea are held out in front. */
@Mixin(PlayerModel.class)
public abstract class PlayerPoseMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void fatekings$pose(AvatarRenderState state, CallbackInfo ci) {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null || !(level.getEntity(state.id) instanceof Player player)) return;
        PlayerModel model = (PlayerModel)(Object)this;
        KingPoses.apply(KingPoses.of(player), model.head, model.leftArm, model.rightArm);
    }
}
