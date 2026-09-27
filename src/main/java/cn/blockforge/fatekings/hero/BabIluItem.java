package cn.blockforge.fatekings.hero;

import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.KingState;
import cn.blockforge.fatekings.king.Kings;
import cn.blockforge.fatekings.king.Skills;
import cn.blockforge.fatekings.knight.Instinct;
import cn.blockforge.fatekings.voice.Voice;
import cn.blockforge.fatekings.voice.VoicePlayer;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.phys.Vec3;

/**
 * Bab-ilu, the key sword to the deepest vault. Held for 2 s by the King of Heroes: a red labyrinth
 * spreads over the sky (visible from far away), shrinks into a sphere of light and Ea is drawn from
 * it into the main hand. For anyone else it is a plain golden short sword.
 */
public class BabIluItem extends Item {
    public BabIluItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!Kings.isHero(player)) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            KingState s = Kings.of(player);
            long now = level.getGameTime();
            if (!s.ready(Skills.BAB_ILU, now)) {
                Kings.refuse(player, "fatekings.hint.cooldown", Component.translatable("fatekings.skill." + Skills.BAB_ILU),
                    String.format(java.util.Locale.ROOT, "%.1f", s.cooldownLeft(Skills.BAB_ILU, now) / 20.0f));
                return InteractionResult.FAIL;
            }
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
        return ItemUseAnimation.BOW;
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel server)) return;
        int held = KingItem.held(remaining);
        if (held == 1) beginRitual(server, user);
        ritualTick(server, user, held);
        if (held >= KingRules.BAB_ILU_TICKS && user instanceof Player p) {
            InteractionHand hand = p.getUsedItemHand();
            p.stopUsingItem();
            drawEa(server, p, hand, stack);
        }
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
        return true;
    }

    /** The key turns in the void; the labyrinth spreads over the sky (the others' early warning). */
    public static void beginRitual(ServerLevel level, LivingEntity user) {
        VoicePlayer.say(user, Voice.GIL_UNLOCK);
        Fx.event(level, Fx.LABYRINTH, user, user.position(), KingRules.BAB_ILU_TICKS + 12, 1.0f, 256.0);
        Instinct.announce(level, user, "fatekings.warn.bab_ilu", KingRules.BAB_ILU_TICKS + KingRules.EA_CHARGE + 20);
        level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.IRON_DOOR_OPEN, SoundSource.PLAYERS, 2.0f, 0.4f);
        level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 3.0f, 0.5f);
    }

    public static void ritualTick(ServerLevel level, LivingEntity user, int held) {
        Vec3 hand = user.getEyePosition().add(user.getViewVector(1.0f).scale(1.2)).add(0.0, -0.3, 0.0);
        Fx.particles(level, Fx.dust(0xFF2A2A, 1.0f), hand.x, hand.y, hand.z, 3, 0.15, 0.15, 0.15, 0.0);
        if (held % 10 == 5) level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.5f, 0.5f);
    }

    /** The labyrinth closes into a sphere of red light; Ea comes out of it into the main hand. */
    public static void drawEa(ServerLevel level, Player p, InteractionHand hand, ItemStack key) {
        KingState s = Kings.of(p);
        long now = level.getGameTime();
        s.cooldown(Skills.BAB_ILU, now, KingRules.BAB_ILU);
        ItemStack ea = EaItem.draw(level, key.copy(), now + KingRules.EA_LIFETIME);
        p.setItemInHand(hand, ea);
        if (hand != InteractionHand.MAIN_HAND) {
            // Ea only exists in the main hand: swap it over.
            ItemStack main = p.getMainHandItem();
            p.setItemInHand(InteractionHand.MAIN_HAND, ea);
            p.setItemInHand(hand, main);
        }
        VoicePlayer.say(p, Voice.GIL_EA_DRAWN);
        Vec3 c = p.getEyePosition().add(p.getViewVector(1.0f).scale(1.2));
        Fx.particles(level, Fx.dust(0xFF2A2A, 2.0f), c.x, c.y, c.z, 30, 0.3, 0.3, 0.3, 0.0);
        Fx.particles(level, ParticleTypes.END_ROD, c.x, c.y, c.z, 12, 0.2, 0.2, 0.2, 0.05);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 2.0f, 0.6f);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.fatekings.bab_ilu.desc").withStyle(ChatFormatting.GOLD));
        builder.accept(Component.translatable("item.fatekings.bab_ilu.use").withStyle(ChatFormatting.GRAY));
    }
}
