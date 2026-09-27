package cn.blockforge.fatekings.client.mixin;

import cn.blockforge.fatekings.client.FateClient;
import cn.blockforge.fatekings.client.WorldFx;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Noble phantasms shake the camera (reduced to a quarter with the reduceShake option). */
@Mixin(Camera.class)
public abstract class CameraShakeMixin {
    @Shadow private Entity entity;
    @Shadow private float xRot;
    @Shadow private float yRot;

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void fatekings$shake(DeltaTracker tracker, CallbackInfo ci) {
        if (this.entity == null || !(this.entity.level() instanceof net.minecraft.client.multiplayer.ClientLevel level)) return;
        float delta = tracker.getGameTimeDeltaPartialTick(false);
        float strength = WorldFx.shake(level, this.entity.getEyePosition(delta), delta);
        if (FateClient.prefs().reduceShake()) strength *= 0.25f;
        if (strength <= 0.01f) return;
        double time = (this.entity.tickCount + delta) * 2.9;
        setRotation(this.yRot + (float)Math.sin(time * 1.37) * strength, this.xRot + (float)Math.cos(time) * strength * 0.65f);
    }
}
