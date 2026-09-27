package cn.blockforge.fatekings.king;

import cn.blockforge.fatekings.registry.FateItems;
import cn.blockforge.fatekings.registry.FateRules;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * The regalia are made of magical energy and hard to break: they take no wear (unbreakable), cannot
 * be enchanted and, with {@code fatekings:keep_regalia} on, stay on the body at death.
 */
public final class Regalia {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final Map<UUID, Map<EquipmentSlot, ItemStack>> STASH = new ConcurrentHashMap<>();

    private Regalia() {
    }

    public static void clear() {
        STASH.clear();
    }

    /** Before death drops: set the pieces aside. */
    public static void stash(ServerLevel level, ServerPlayer p) {
        if (!FateRules.get(level, FateRules.KEEP_REGALIA) || level.getGameRules().get(GameRules.KEEP_INVENTORY)) return;
        Map<EquipmentSlot, ItemStack> kept = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : SLOTS) {
            ItemStack s = p.getItemBySlot(slot);
            if (FateItems.heroPiece(s) || FateItems.knightPiece(s)) {
                kept.put(slot, s.copy());
                p.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        if (!kept.isEmpty()) STASH.put(p.getUUID(), kept);
    }

    /** On respawn: put them back on. */
    public static void restore(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        Map<EquipmentSlot, ItemStack> kept = STASH.remove(oldPlayer.getUUID());
        if (kept == null || alive) return;
        kept.forEach((slot, stack) -> {
            if (newPlayer.getItemBySlot(slot).isEmpty()) newPlayer.setItemSlot(slot, stack);
            else if (!newPlayer.getInventory().add(stack)) newPlayer.spawnAtLocation(newPlayer.level(), stack);
        });
    }
}
