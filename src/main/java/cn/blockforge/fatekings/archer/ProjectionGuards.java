package cn.blockforge.fatekings.archer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.ShelfBlock;

/** The ways a copy could leave its maker's hands that are not slots: frames, stands, shelves, pots, the ground. */
public final class ProjectionGuards {
    private ProjectionGuards() {
    }

    public static void register() {
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!Projection.projected(player.getItemInHand(hand))) return InteractionResult.PASS;
            return entity instanceof ItemFrame || entity instanceof ArmorStand || entity instanceof Allay ? InteractionResult.FAIL : InteractionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!Projection.projected(player.getItemInHand(hand))) return InteractionResult.PASS;
            var block = level.getBlockState(hit.getBlockPos()).getBlock();
            return block instanceof ShelfBlock || block instanceof DecoratedPotBlock ? InteractionResult.FAIL : InteractionResult.PASS;
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof ItemEntity item && Projection.projected(item.getItem())) {
                Projection.fade(level, item.getX(), item.getY() + 0.3, item.getZ());
                item.discard();
            } else if (entity instanceof AbstractArrow arrow && Projection.projected(arrow.getPickupItemStackOrigin())) {
                // A thrown projected trident cannot be picked up again.
                arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            }
        });
    }
}
