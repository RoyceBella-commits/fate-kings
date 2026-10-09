package cn.blockforge.fatekings.client.mixin;

import cn.blockforge.fatekings.client.UbwClient;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Inside Unlimited Blade Works the world's own ground is not drawn: only the marble's plain of swords. */
@Mixin(ChunkSectionsToRender.class)
public abstract class UbwTerrainMixin {
    @Inject(method = "renderGroup", at = @At("HEAD"), cancellable = true)
    private void fatekings$marbleHidesTerrain(CallbackInfo ci) {
        if (UbwClient.hidesTerrain()) ci.cancel();
    }

    @Inject(method = "renderOit", at = @At("HEAD"), cancellable = true)
    private void fatekings$marbleHidesTranslucentTerrain(CallbackInfo ci) {
        if (UbwClient.hidesTerrain()) ci.cancel();
    }
}
