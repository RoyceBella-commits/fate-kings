package cn.blockforge.fatekings.knight;

import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.hero.KingItem;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.KingState;
import cn.blockforge.fatekings.king.Kings;
import cn.blockforge.fatekings.npc.KingNpcEntity;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Excalibur, Sword of Promised Victory. For the King of Knights: swing (Invisible Air, +1.5 reach),
 * tap = Strike Air, sneak = Mana Burst charge, hold 1.5 s and release = the true name. Model:
 * "air" (wind only) in a knight's hand, "revealed" otherwise, "release" while gathering light.
 * For anyone else it is a revealed, diamond-grade long sword.
 */
public class ExcaliburItem extends Item {
    public static final String AIR = "air";
    public static final String REVEALED = "revealed";

    public ExcaliburItem(Properties properties) {
        super(properties);
    }

    public static boolean revealed(LivingEntity holder, KingState s, long now) {
        if (s == null || !Kings.isKnight(holder)) return true;
        if (holder instanceof KingNpcEntity npc && npc.lastStand()) return true;
        return now < s.revealedUntil || s.depleted(now);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!Kings.isKnight(player)) {
            if (!level.isClientSide()) Kings.refuse(player, "fatekings.hint.sword_answers_king");
            return InteractionResult.FAIL;
        }
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) ExcaliburSkill.manaBurst(player);
            return InteractionResult.SUCCESS;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return KingItem.USE_DURATION;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.TRIDENT;
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel server)) return;
        int held = KingItem.held(remaining);
        if (held == KingRules.TAP_TICKS) {
            // Past a tap: this is the true name. Riding needs a hand on the reins; both hands are needed.
            if (user.isPassenger() || !ExcaliburSkill.mayCharge(user)) {
                if (user.isPassenger() && user instanceof Player p) Kings.refuse(p, "fatekings.hint.two_hands");
                user.stopUsingItem();
                return;
            }
        }
        if (held >= KingRules.TAP_TICKS) ExcaliburSkill.chargeTick(server, user, held);
        if (held >= KingRules.EXCALIBUR_AUTO_RELEASE) {
            user.stopUsingItem();
            ExcaliburSkill.release(user, held, null);
        }
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
        if (!(level instanceof ServerLevel server)) return true;
        int held = KingItem.held(remaining);
        if (held < KingRules.TAP_TICKS) {
            ExcaliburSkill.strikeAir(user);
        } else if (held >= KingRules.EXCALIBUR_CHARGE) {
            ExcaliburSkill.release(user, held, null);
        } else {
            ExcaliburSkill.cancel(server, user);
        }
        return true;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (!(entity instanceof LivingEntity holder)) return;
        boolean inHand = slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND;
        String state = inHand && !revealed(holder, Kings.of(holder), level.getGameTime()) ? AIR : REVEALED;
        setState(stack, state);
    }

    public static void setState(ItemStack stack, String state) {
        CustomModelData cur = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        String now = cur == null ? null : cur.getString(0);
        if (!state.equals(now)) {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(state), List.of()));
        }
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!Kings.isKnight(attacker) || !(attacker.level() instanceof ServerLevel level)) return;
        KingState s = Kings.of(attacker);
        boolean air = !revealed(attacker, s, level.getGameTime());
        // A blade of wind sweeps the 3-block arc in front (Invisible Air), plus Mana Burst sparks.
        Vec3 look = attacker.getViewVector(1.0f).multiply(1.0, 0.0, 1.0).normalize();
        float swing = KingRules.excaliburSwing(!air, attacker instanceof KingNpcEntity npc && npc.lastStand()) * 0.6f;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, attacker.getBoundingBox().inflate(3.5, 1.0, 3.5),
                e -> e != attacker && e != target && e.isAlive() && !e.isSpectator() && !(e instanceof Player p && p.isCreative()))) {
            Vec3 to = e.position().subtract(attacker.position()).multiply(1.0, 0.0, 1.0);
            if (to.length() > 4.5 || to.normalize().dot(look) < 0.3) continue;
            e.hurtServer(level, attacker.damageSources().mobAttack(attacker), swing);
        }
        Vec3 c = attacker.getEyePosition().add(look.scale(1.8));
        Fx.particles(level, ParticleTypes.SWEEP_ATTACK, c.x, c.y - 0.4, c.z, 1, 0.0, 0.0, 0.0, 0.0);
        Fx.particles(level, air ? Fx.dust(0xDDF4FF, 1.2f) : Fx.dust(0xFFE38A, 1.0f), c.x, c.y - 0.4, c.z, 10, 1.2, 0.2, 1.2, 0.0);
        Fx.particles(level, ParticleTypes.ELECTRIC_SPARK, target.getBoundingBox().getCenter(), 8, 0.3, 0.2);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.fatekings.excalibur.desc").withStyle(ChatFormatting.GOLD));
        builder.accept(Component.translatable("item.fatekings.excalibur.use").withStyle(ChatFormatting.GRAY));
    }
}
