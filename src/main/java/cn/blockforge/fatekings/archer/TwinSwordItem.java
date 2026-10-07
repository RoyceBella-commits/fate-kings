package cn.blockforge.fatekings.archer;

import cn.blockforge.fatekings.king.Kings;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * Kanshou (black) and Bakuya (white), the married blades. In an Archer's main hand the other blade
 * is projected into his off hand; strokes run the six-step combo; right-click throws the pair;
 * sneak + right-click is Crane Wing Three Strikes. In anyone else's hand, a fine sword.
 */
public class TwinSwordItem extends Item {
    private final boolean kanshou;

    public TwinSwordItem(Properties properties, boolean kanshou) {
        super(properties);
        this.kanshou = kanshou;
    }

    public boolean kanshou() {
        return this.kanshou;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !Kings.isArcher(player)) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            if (player.isShiftKeyDown()) CraneWing.start(player);
            else CraneWing.throwPair(player);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        super.postHurtEnemy(stack, target, attacker);
        if (!attacker.level().isClientSide() && Kings.isArcher(attacker)) TwinBlades.onHit(attacker, target);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.fatekings." + (this.kanshou ? "kanshou" : "bakuya") + ".desc").withStyle(ChatFormatting.RED));
        builder.accept(Component.translatable("item.fatekings.kanshou.use").withStyle(ChatFormatting.GRAY));
    }
}
