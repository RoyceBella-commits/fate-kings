package cn.blockforge.fatekings.compat.mixin;

import cn.blockforge.fatekings.client.render.DomainSeam;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Malevolent Shrine meeting Unlimited Blade Works: when no Unlimited Void is against it, the marble
 * takes the void's place in the shrine's shader, which then leaves the marble its side of the seam.
 */
@Pseudo
@Mixin(targets = "cn.blockforge.ryomensukuna.m2a542fea.client.render.DomainRenderer", remap = false)
public abstract class ShrineSeamMixin {
    @ModifyVariable(method = "extract", at = @At(value = "NEW", target = "cn/blockforge/ryomensukuna/m2a542fea/client/render/DomainRenderer$Frame"),
        name = "voidCenter", require = 0)
    private static Vec3 fatekings$marbleCentre(Vec3 voidCenter) {
        if (!Vec3.ZERO.equals(voidCenter)) return voidCenter;
        DomainSeam.Sphere m = DomainSeam.marbleAgainstShrine();
        return m == null ? voidCenter : m.centre();
    }

    @ModifyVariable(method = "extract", at = @At(value = "NEW", target = "cn/blockforge/ryomensukuna/m2a542fea/client/render/DomainRenderer$Frame"),
        name = "voidRadius", require = 0)
    private static float fatekings$marbleRadius(float voidRadius) {
        if (voidRadius > 0.0f) return voidRadius;
        DomainSeam.Sphere m = DomainSeam.marbleAgainstShrine();
        return m == null ? voidRadius : (float)m.radius();
    }
}
