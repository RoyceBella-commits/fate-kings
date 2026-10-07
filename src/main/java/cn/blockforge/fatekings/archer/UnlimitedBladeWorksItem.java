package cn.blockforge.fatekings.archer;

import cn.blockforge.fatekings.hero.KingItem;
import cn.blockforge.fatekings.king.Kings;
import cn.blockforge.fatekings.voice.VoicePlayer;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * Unlimited Blade Works. Tap: analyse the weapon in sight / project the one chosen last. Sneak:
 * the Hill of Swords (every weapon he has seen, to project with a click). Hold 3 s: the aria, and
 * the reality marble unfolds.
 */
public class UnlimitedBladeWorksItem extends KingItem {
    /** Client hook: opens the Hill of Swords screen (set by the client initializer). */
    public static volatile Runnable clientOpenArsenal = () -> {
    };

    public UnlimitedBladeWorksItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!Kings.isArcher(player)) {
            if (!level.isClientSide()) Kings.refuse(player, "fatekings.hint.archer_only");
            return InteractionResult.FAIL;
        }
        if (player.isShiftKeyDown()) {
            if (level.isClientSide()) clientOpenArsenal.run();
            return InteractionResult.SUCCESS;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (level.isClientSide()) return;
        int held = held(remaining);
        if (held == ArcherRules.UBW_CHANT_START && !UnlimitedBladeWorks.mayChant(user)) {
            user.stopUsingItem();
            return;
        }
        if (held >= ArcherRules.UBW_CHANT_START) UnlimitedBladeWorks.chantTick(user, held);
        if (held >= ArcherRules.UBW_CHANT) {
            user.stopUsingItem();
            UnlimitedBladeWorks.unfold(user);
        }
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
        if (level.isClientSide()) return true;
        int held = held(remaining);
        if (held < ArcherRules.UBW_CHANT_START) {
            if (user instanceof ServerPlayer p) UnlimitedBladeWorks.tap(p);
        } else {
            // The aria broken off.
            VoicePlayer.stop(user);
        }
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.fatekings.unlimited_blade_works.desc").withStyle(ChatFormatting.RED));
        builder.accept(Component.translatable("item.fatekings.unlimited_blade_works.use").withStyle(ChatFormatting.GRAY));
    }
}
