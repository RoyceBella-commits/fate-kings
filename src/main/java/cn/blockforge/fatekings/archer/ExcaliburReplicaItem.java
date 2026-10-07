package cn.blockforge.fatekings.archer;

import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.entity.ExcaliburWaveEntity;
import cn.blockforge.fatekings.hero.KingItem;
import cn.blockforge.fatekings.king.KingState;
import cn.blockforge.fatekings.king.Kings;
import cn.blockforge.fatekings.king.Skills;
import cn.blockforge.fatekings.knight.Instinct;
import cn.blockforge.fatekings.npc.KingNpcEntity;
import cn.blockforge.fatekings.voice.Voice;
import cn.blockforge.fatekings.voice.VoicePlayer;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
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
 * Excalibur as EMIYA can trace it: a replica. Held 1.5 s and let go it releases a weaker true name
 * (half the reach and width, far less power, Infinity stops it), and the replica breaks.
 */
public class ExcaliburReplicaItem extends Item {
    public ExcaliburReplicaItem(Properties properties) {
        super(properties);
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
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!Kings.isArcher(player)) {
            if (!level.isClientSide()) Kings.refuse(player, "fatekings.hint.archer_only");
            return InteractionResult.FAIL;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel server)) return;
        int held = KingItem.held(remaining);
        if (held == 5) {
            if (!ArcherBow.ready(user, Skills.EXCALIBUR_REPLICA, true)) {
                user.stopUsingItem();
                return;
            }
            VoicePlayer.say(user, Voice.EMIYA_FORGE);
            Instinct.announce(server, user, "fatekings.warn.excalibur_replica", 40);
        }
        if (held >= 5) chargeFx(server, user, held);
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
        if (level.isClientSide()) return true;
        int held = KingItem.held(remaining);
        if (held >= ArcherRules.REPLICA_CHARGE) release(user, held, null);
        return true;
    }

    static void chargeFx(ServerLevel level, LivingEntity user, int held) {
        Vec3 c = user.getEyePosition().add(0.0, 0.6, 0.0);
        Fx.particles(level, Fx.dust(0xD8D8C8, 1.0f), c.x, c.y, c.z, 2, 0.3, 0.5, 0.3, 0.0);
        if (held % 15 == 0) level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.2f, 0.8f + held * 0.01f);
    }

    /** The replica's true name; the copy shatters. */
    public static boolean release(LivingEntity user, int held, LivingEntity target) {
        if (!(user.level() instanceof ServerLevel level) || !Kings.isArcher(user)) return false;
        if (!ArcherBow.ready(user, Skills.EXCALIBUR_REPLICA, true)) return false;
        KingState s = Kings.of(user);
        s.cooldown(Skills.EXCALIBUR_REPLICA, level.getGameTime(), ArcherRules.REPLICA);
        s.dirty = true;
        Vec3 eye = user.getEyePosition();
        Vec3 dir = user instanceof KingNpcEntity && target != null ? target.getBoundingBox().getCenter().subtract(eye).normalize() : user.getViewVector(1.0f);
        ExcaliburWaveEntity.fireReplica(level, user, eye.add(dir.scale(1.5)), dir, ArcherRules.replicaWidth(held), ArcherRules.REPLICA_RANGE);
        VoicePlayer.say(user, Voice.EMIYA_REPLICA);
        Fx.event(level, Fx.SHAKE, user, user.position(), 24, 0.7f, 96.0);
        // The copy could not bear it.
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
            if (user.getItemBySlot(slot).getItem() instanceof ExcaliburReplicaItem) user.setItemSlot(slot, ItemStack.EMPTY);
        }
        Fx.particles(level, ParticleTypes.END_ROD, user.getX(), user.getY() + 1.2, user.getZ(), 16, 0.4, 0.4, 0.4, 0.05);
        level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2f, 0.9f);
        if (user instanceof ServerPlayer p) Kings.refuse(p, "fatekings.hint.replica_shattered");
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.fatekings.excalibur_replica.desc").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.fatekings.excalibur_replica.use").withStyle(ChatFormatting.GRAY));
    }
}
