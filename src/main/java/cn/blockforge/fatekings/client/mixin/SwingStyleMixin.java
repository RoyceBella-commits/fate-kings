package cn.blockforge.fatekings.client.mixin;

import cn.blockforge.fatekings.client.ClientSwings;
import cn.blockforge.fatekings.client.KingPoses;
import cn.blockforge.fatekings.client.render.KingNpcRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Third person: an Excalibur stroke sweeps along its own angle instead of the one vanilla swing;
 * a stroke of Kanshou and Bakuya moves both arms (and the body) through its own figure.
 */
@Mixin(HumanoidModel.class)
public abstract class SwingStyleMixin {
    @Inject(method = "setupAttackAnimation", at = @At("HEAD"), cancellable = true)
    private void fatekings$stroke(HumanoidRenderState state, CallbackInfo ci) {
        int id = state instanceof AvatarRenderState a ? a.id : state instanceof KingNpcRenderer.State k ? k.entityId : -1;
        ClientSwings.Twin twin = id < 0 ? null : ClientSwings.twin(id);
        if (twin != null) {
            HumanoidModel<?> model = (HumanoidModel<?>)(Object)this;
            boolean right = state.mainArm == HumanoidArm.RIGHT;
            KingPoses.twin(right ? model.rightArm : model.leftArm, right ? model.leftArm : model.rightArm, model.body, twin.step(), twin.progress(),
                right ? 1.0f : -1.0f);
            ci.cancel();
            return;
        }
        if (state.swingAnimation <= 0.0f) return;
        Float roll = id < 0 ? null : ClientSwings.roll(id);
        if (roll == null) return;
        HumanoidModel<?> model = (HumanoidModel<?>)(Object)this;
        boolean right = state.mainArm == HumanoidArm.RIGHT;
        ModelPart arm = right ? model.rightArm : model.leftArm;
        KingPoses.stroke(arm, model.body, state.swingAnimation, roll, right ? 1.0f : -1.0f);
        ci.cancel();
    }
}
