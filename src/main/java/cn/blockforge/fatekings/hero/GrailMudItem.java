package cn.blockforge.fatekings.hero;

import cn.blockforge.fatekings.npc.KingNpcEntity;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Mud of the Holy Grail: an admin item that takes either king NPC down at once (single player or operators). */
public class GrailMudItem extends Item {
    public GrailMudItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        if (!(server.getServer().isSingleplayer() || sp.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))) {
            sp.sendSystemMessage(Component.translatable("fatekings.hint.admin_only"), true);
            return InteractionResult.FAIL;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0f).scale(64.0));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, eye, end, new AABB(eye, end).inflate(1.0),
            e -> e instanceof KingNpcEntity && e.isAlive(), 0.5f);
        if (hit == null || !(hit.getEntity() instanceof KingNpcEntity npc)) {
            sp.sendSystemMessage(Component.translatable("fatekings.hint.no_target"), true);
            return InteractionResult.FAIL;
        }
        server.sendParticles(ParticleTypes.SQUID_INK, npc.getX(), npc.getY() + 1.0, npc.getZ(), 40, 0.4, 0.8, 0.4, 0.05);
        server.playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.HOSTILE, 1.5f, 0.5f);
        npc.kill(server);
        player.getCooldowns().addCooldown(player.getItemInHand(hand), 10);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.fatekings.grail_mud.desc").withStyle(ChatFormatting.DARK_RED));
    }
}
