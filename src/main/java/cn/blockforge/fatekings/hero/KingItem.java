package cn.blockforge.fatekings.hero;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

/** Right-click noble phantasm: tap / hold-and-release on the vanilla item-use mechanics. */
public abstract class KingItem extends Item {
    public static final int USE_DURATION = 72000;

    protected KingItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return USE_DURATION;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }

    /** Ticks the item has been held so far, given the remaining ticks the game reports. */
    public static int held(int remaining) {
        return USE_DURATION - remaining;
    }
}
