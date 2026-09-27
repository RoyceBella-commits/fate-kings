package cn.blockforge.fatekings.hero;

import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.Kings;
import cn.blockforge.fatekings.king.Skills;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** The treasury's elixir: 20 hearts over 3 s while drinking (slowed), 60 s cooldown. Never used up. */
public class ElixirItem extends Item {
    private static final int DRINK = 60;

    public ElixirItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!Kings.isHero(player)) {
            if (!level.isClientSide()) Kings.refuse(player, "fatekings.hint.treasury_closed");
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            if (!GateOfBabylon.ready(player, Skills.ELIXIR)) return InteractionResult.FAIL;
            Kings.of(player).cooldown(Skills.ELIXIR, level.getGameTime(), KingRules.ELIXIR);
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return DRINK;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.DRINK;
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel server)) return;
        user.heal(40.0f / DRINK);
        if (remaining % 10 == 0) Fx.particles(server, Fx.dust(Fx.GOLD, 0.8f), user.getX(), user.getY() + 1.2, user.getZ(), 4, 0.3, 0.3, 0.3, 0.0);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        if (level instanceof ServerLevel server) {
            server.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.6f);
        }
        return stack;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.fatekings.treasury_elixir.desc").withStyle(ChatFormatting.GOLD));
    }
}
