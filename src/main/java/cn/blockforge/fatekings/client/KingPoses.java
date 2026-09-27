package cn.blockforge.fatekings.client;

import cn.blockforge.fatekings.hero.KingItem;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.registry.FateItems;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Arm poses of the two kings, shared by players and the NPCs. */
public final class KingPoses {
    public static final int NONE = 0;
    /** Excalibur raised above the head in both hands (the true name needs both hands). */
    public static final int SWORD_RAISED = 1;
    /** Bab-ilu turned in the void / Ea held out spinning. */
    public static final int HELD_OUT = 2;

    private KingPoses() {
    }

    public static int of(LivingEntity e) {
        if (!e.isUsingItem()) return NONE;
        ItemStack use = e.getUseItem();
        if (use.is(FateItems.EXCALIBUR) && KingItem.held(e.getUseItemRemainingTicks()) >= KingRules.TAP_TICKS) return SWORD_RAISED;
        if (use.is(FateItems.BAB_ILU) || use.is(FateItems.EA)) return HELD_OUT;
        return NONE;
    }

    /**
     * One Excalibur stroke along {@code rollDeg} (on screen: 0 = left to right, 180 = right to left,
     * -90 = straight down, 90 = rising): the sword arm sweeps from one side of the line to the other
     * and the body turns with it. {@code side} is +1 for the right arm, -1 for the left.
     */
    public static void stroke(ModelPart arm, ModelPart body, float progress, float rollDeg, float side) {
        float p = Math.max(0.0f, Math.min(1.0f, progress));
        float e = p * p * (3.0f - 2.0f * p);
        float s = 2.0f * e - 1.0f;
        double r = Math.toRadians(rollDeg);
        float dx = (float)Math.cos(r), dy = (float)Math.sin(r);
        arm.xRot = -1.45f + dy * s * 1.05f;
        arm.yRot = side * -dx * s * 1.15f;
        arm.zRot = side * 0.12f;
        body.yRot = arm.yRot * 0.3f;
    }

    public static void apply(int pose, ModelPart head, ModelPart left, ModelPart right) {
        switch (pose) {
            case SWORD_RAISED -> {
                left.xRot = right.xRot = (float)-Math.PI + 0.15f;
                left.yRot = 0.35f;
                right.yRot = -0.35f;
                left.zRot = right.zRot = 0.0f;
            }
            case HELD_OUT -> {
                left.xRot = right.xRot = -1.45f + head.xRot;
                left.yRot = 0.3f;
                right.yRot = -0.3f;
            }
            default -> {
            }
        }
    }
}
