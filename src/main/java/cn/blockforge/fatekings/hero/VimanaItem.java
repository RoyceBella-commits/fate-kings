package cn.blockforge.fatekings.hero;

import cn.blockforge.fatekings.entity.VimanaEntity;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.Kings;
import cn.blockforge.fatekings.king.Skills;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

public class VimanaItem extends Item {
    public VimanaItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!Kings.isHero(player)) {
            if (!level.isClientSide()) Kings.refuse(player, "fatekings.hint.treasury_closed");
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel server) {
            if (player.isPassenger()) return InteractionResult.FAIL;
            if (!GateOfBabylon.treasuryOpen(player) || !GateOfBabylon.ready(player, Skills.VIMANA)) return InteractionResult.FAIL;
            Kings.of(player).cooldown(Skills.VIMANA, server.getGameTime(), KingRules.VIMANA);
            VimanaEntity.summon(server, player);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.fatekings.vimana.desc").withStyle(ChatFormatting.GREEN));
        builder.accept(Component.translatable("item.fatekings.vimana.use").withStyle(ChatFormatting.GRAY));
    }
}
