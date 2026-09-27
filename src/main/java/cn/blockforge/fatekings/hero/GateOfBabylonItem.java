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

public class GateOfBabylonItem extends KingItem {
    public GateOfBabylonItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!Kings.isHero(player)) {
            if (!level.isClientSide()) Kings.refuse(player, "fatekings.hint.treasury_closed");
            return InteractionResult.FAIL;
        }
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) GateOfBabylon.ring(player);
            return InteractionResult.SUCCESS;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (level.isClientSide()) return;
        int held = held(remaining);
        if (held >= KingRules.GOB_VOLLEY_MIN) GateOfBabylon.growVolley(user, held);
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
        if (level.isClientSide()) return true;
        int held = held(remaining);
        if (GateOfBabylon.volleyHeld(user)) {
            GateOfBabylon.releaseVolley(user);
        } else if (held < KingRules.GOB_VOLLEY_MIN) {
            GateOfBabylon.tap(user, 3, true);
        }
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.fatekings.gate_of_babylon.desc").withStyle(ChatFormatting.GOLD));
        builder.accept(Component.translatable("item.fatekings.gate_of_babylon.use").withStyle(ChatFormatting.GRAY));
    }
}
