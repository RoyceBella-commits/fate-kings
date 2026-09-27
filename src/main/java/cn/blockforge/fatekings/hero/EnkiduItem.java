package cn.blockforge.fatekings.hero;

import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.Kings;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

public class EnkiduItem extends KingItem {
    public EnkiduItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!Kings.isHero(player)) {
            if (!level.isClientSide()) Kings.refuse(player, "fatekings.hint.treasury_closed");
            return InteractionResult.FAIL;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (level.isClientSide()) return;
        if (held(remaining) == KingRules.ENKIDU_BIND_TICKS) {
            Enkidu.bind(user);
            user.stopUsingItem();
        }
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
        if (!level.isClientSide() && held(remaining) < KingRules.TAP_TICKS) Enkidu.hook(user);
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.fatekings.enkidu.desc").withStyle(ChatFormatting.GOLD));
        builder.accept(Component.translatable("item.fatekings.enkidu.use").withStyle(ChatFormatting.GRAY));
    }
}
