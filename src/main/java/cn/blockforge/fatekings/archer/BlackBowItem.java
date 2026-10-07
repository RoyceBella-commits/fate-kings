package cn.blockforge.fatekings.archer;

import cn.blockforge.fatekings.hero.KingItem;
import cn.blockforge.fatekings.king.Kings;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** EMIYA's black bow: tap = homing arrow, hold 1.5 s = Caladbolg II, sneak = Rho Aias. */
public class BlackBowItem extends KingItem {
    public BlackBowItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!Kings.isArcher(player)) {
            if (!level.isClientSide()) Kings.refuse(player, "fatekings.hint.archer_only");
            return InteractionResult.FAIL;
        }
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) RhoAias.cast(player);
            return InteractionResult.SUCCESS;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BOW;
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (!level.isClientSide()) ArcherBow.chargeTick(user, held(remaining));
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
        if (level.isClientSide()) return true;
        int held = held(remaining);
        // Not drawn long enough for Caladbolg: a plain shot all the same.
        if (held >= ArcherRules.CALADBOLG_CHARGE && ArcherBow.ready(user, cn.blockforge.fatekings.king.Skills.CALADBOLG, false)) {
            ArcherBow.caladbolg(user, null);
        } else {
            ArcherBow.tap(user);
        }
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.fatekings.black_bow.desc").withStyle(ChatFormatting.RED));
        builder.accept(Component.translatable("item.fatekings.black_bow.use").withStyle(ChatFormatting.GRAY));
    }
}
