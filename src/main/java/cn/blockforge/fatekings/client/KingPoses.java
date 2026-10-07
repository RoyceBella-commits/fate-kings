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
    /** The aria of Unlimited Blade Works: the right hand held out, palm open over the hill. */
    public static final int ARIA = 3;

    private KingPoses() {
    }

    public static int of(LivingEntity e) {
        if (!e.isUsingItem()) return NONE;
        ItemStack use = e.getUseItem();
        if ((use.is(FateItems.EXCALIBUR) || use.is(FateItems.EXCALIBUR_REPLICA)) && KingItem.held(e.getUseItemRemainingTicks()) >= KingRules.TAP_TICKS) return SWORD_RAISED;
        if (use.is(FateItems.UNLIMITED_BLADE_WORKS) && KingItem.held(e.getUseItemRemainingTicks()) >= 6) return ARIA;
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

    private static float smooth(float p) {
        float c = Math.max(0.0f, Math.min(1.0f, p));
        return c * c * (3.0f - 2.0f * c);
    }

    /**
     * A stroke of Kanshou and Bakuya in third person: {@code lead} is the main-hand arm, {@code off}
     * the other, {@code side} +1 when the main hand is the right. Six figures: a right sweep, a
     * left backhand, a cross from high to low, a spin with both blades held out, a double chop, a
     * rising cut; and the Overedge cross.
     */
    public static void twin(ModelPart lead, ModelPart off, ModelPart body, int step, float progress, float side) {
        float e = smooth(progress);
        float arc = (float)Math.sin(progress * Math.PI);
        float bodyYaw;
        switch (step) {
            case 0 -> { // right sweep: the lead blade from its side across the body
                lead.xRot = -1.45f + arc * 0.25f;
                lead.yRot = side * (1.25f - 2.5f * e);
                lead.zRot = side * 0.1f;
                off.xRot = -0.5f;
                off.yRot = side * 0.4f;
                off.zRot = side * -0.2f;
                bodyYaw = side * (0.55f - 1.1f * e);
            }
            case 1 -> { // left backhand: the off blade the other way
                off.xRot = -1.45f + arc * 0.25f;
                off.yRot = side * (-1.25f + 2.5f * e);
                off.zRot = side * -0.1f;
                lead.xRot = -0.5f;
                lead.yRot = side * -0.4f;
                lead.zRot = side * 0.2f;
                bodyYaw = side * (-0.55f + 1.1f * e);
            }
            case 2, 6 -> { // cross: both blades from high outside to low inside
                float k = step == 6 ? 1.25f : 1.0f;
                lead.xRot = -2.7f * k + 2.3f * e;
                lead.yRot = side * (0.7f - 1.3f * e) * k;
                lead.zRot = side * -0.3f;
                off.xRot = -2.7f * k + 2.3f * e;
                off.yRot = side * (-0.7f + 1.3f * e) * k;
                off.zRot = side * 0.3f;
                bodyYaw = 0.0f;
            }
            case 3 -> { // spin: arms held out, the body wound and loosed
                lead.xRot = -1.57f;
                off.xRot = -1.57f;
                lead.zRot = side * -1.2f * arc;
                off.zRot = side * 1.2f * arc;
                lead.yRot = side * 0.4f;
                off.yRot = side * -0.4f;
                bodyYaw = side * (float)Math.sin(e * Math.PI * 2.0) * 1.1f;
            }
            case 4 -> { // double chop: both blades high over the head, then down
                lead.xRot = -3.0f + 2.7f * e;
                off.xRot = -3.0f + 2.7f * e;
                lead.yRot = side * -0.15f;
                off.yRot = side * 0.15f;
                lead.zRot = side * 0.15f * arc;
                off.zRot = side * -0.15f * arc;
                bodyYaw = 0.0f;
            }
            default -> { // rising cut: from low beside the legs up over the head
                lead.xRot = -0.1f - 2.8f * e;
                off.xRot = -0.1f - 2.8f * e;
                lead.yRot = side * 0.25f;
                off.yRot = side * -0.25f;
                lead.zRot = side * 0.3f * (1.0f - e);
                off.zRot = side * -0.3f * (1.0f - e);
                bodyYaw = side * 0.2f * arc;
            }
        }
        // The body turns, the shoulders follow (as vanilla does for its own swing).
        body.yRot = bodyYaw;
        float s = (float)Math.sin(bodyYaw), c = (float)Math.cos(bodyYaw);
        ModelPart right = side > 0 ? lead : off, left = side > 0 ? off : lead;
        right.z = s * 5.0f;
        right.x = -c * 5.0f;
        left.z = -s * 5.0f;
        left.x = c * 5.0f;
        right.yRot += bodyYaw;
        left.yRot += bodyYaw;
    }

    /**
     * The same stroke in first person: each hand moves on its own ({@code side} +1 right hand,
     * {@code main} whether it is the main hand).
     */
    public static void twinFirstPerson(com.mojang.blaze3d.vertex.PoseStack pose, float side, boolean main, int step, float progress) {
        float e = smooth(progress);
        float arc = (float)Math.sin(progress * Math.PI);
        boolean leads = step == 0 ? main : step == 1 ? !main : true;
        if (!leads) {
            pose.translate(side * 0.06f * arc, -0.06f * arc, 0.04f * arc);
            return;
        }
        switch (step) {
            case 0, 1 -> {
                pose.translate(side * (0.35f - 0.7f * e), 0.12f * arc, -0.25f * arc);
                pose.rotate(com.mojang.math.Axis.YP, (float)Math.toRadians(side * (70.0f - 140.0f * e)));
                pose.rotate(com.mojang.math.Axis.ZP, (float)Math.toRadians(side * -25.0f * arc));
                pose.rotate(com.mojang.math.Axis.XP, (float)Math.toRadians(-40.0f * arc));
            }
            case 2, 6 -> {
                float k = step == 6 ? 1.3f : 1.0f;
                pose.translate(side * (0.15f - 0.35f * e) * k, (0.35f - 0.6f * e) * k, -0.25f * arc);
                pose.rotate(com.mojang.math.Axis.ZP, (float)Math.toRadians(side * (-50.0f + 100.0f * e)));
                pose.rotate(com.mojang.math.Axis.XP, (float)Math.toRadians(-100.0f + 130.0f * e));
            }
            case 3 -> {
                pose.translate(side * 0.25f * arc, 0.05f * arc, -0.35f * arc);
                pose.rotate(com.mojang.math.Axis.YP, (float)Math.toRadians(side * -110.0f * arc));
                pose.rotate(com.mojang.math.Axis.ZP, (float)Math.toRadians(side * -60.0f * arc));
            }
            case 4 -> {
                pose.translate(0.0f, 0.35f * (1.0f - e) - 0.1f * arc, -0.3f * arc);
                pose.rotate(com.mojang.math.Axis.XP, (float)Math.toRadians(-120.0f + 160.0f * e));
                pose.rotate(com.mojang.math.Axis.ZP, (float)Math.toRadians(side * 10.0f * arc));
            }
            default -> {
                pose.translate(0.0f, -0.25f + 0.55f * e, -0.3f * arc);
                pose.rotate(com.mojang.math.Axis.XP, (float)Math.toRadians(50.0f - 170.0f * e));
                pose.rotate(com.mojang.math.Axis.ZP, (float)Math.toRadians(side * -15.0f * arc));
            }
        }
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
            case ARIA -> {
                right.xRot = -1.35f + head.xRot * 0.5f;
                right.yRot = -0.2f;
                right.zRot = 0.0f;
                left.xRot = 0.1f;
                left.zRot = -0.15f;
            }
            default -> {
            }
        }
    }
}
