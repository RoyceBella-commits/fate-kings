package cn.blockforge.fatekings.client.mixin;

import cn.blockforge.fatekings.client.ClientKingState;
import cn.blockforge.fatekings.client.ClientSwings;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.registry.FateItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** First person: the held Excalibur cuts along the stroke's angle. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonSwingMixin {
    @Inject(method = "applyItemArmAttackTransform", at = @At("HEAD"), cancellable = true)
    private void fatekings$stroke(PoseStack pose, HumanoidArm arm, float progress, CallbackInfo ci) {
        var p = Minecraft.getInstance().player;
        if (p == null || ClientKingState.king != KingRules.KNIGHT || !p.getMainHandItem().is(FateItems.EXCALIBUR)) return;
        Float roll = ClientSwings.roll(p.getId());
        if (roll == null) return;
        float side = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
        float e = progress * progress * (3.0f - 2.0f * progress);
        float s = 2.0f * e - 1.0f;
        float arc = (float)Math.sin(progress * Math.PI);
        double r = Math.toRadians(roll);
        pose.translate(side * (float)Math.cos(r) * s * 0.4f, (float)Math.sin(r) * s * 0.3f, -arc * 0.2f);
        pose.rotate(Axis.YP, (float)Math.toRadians(side * 45.0f));
        pose.rotate(Axis.ZP, (float)Math.toRadians(side * -(roll + 90.0f) * 0.35f));
        pose.rotate(Axis.XP, (float)Math.toRadians(-85.0f * arc));
        pose.rotate(Axis.YP, (float)Math.toRadians(side * -45.0f));
        ci.cancel();
    }
}
