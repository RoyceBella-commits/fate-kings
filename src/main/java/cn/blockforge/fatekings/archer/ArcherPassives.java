package cn.blockforge.fatekings.archer;

import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.KingState;
import cn.blockforge.fatekings.npc.KingNpcEntity;
import cn.blockforge.fatekings.registry.FateItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** What the Red Shroud keeps doing: the married blade answers its twin in the other hand, and wounds slowly close. */
public final class ArcherPassives {
    private ArcherPassives() {
    }

    public static void tick(LivingEntity e, KingState s, long now) {
        if (e instanceof ServerPlayer p) partnerBlade(p);
        // 1 heart a second after 5 s unhurt, 1 every 4 s in a fight (an NPC's in proportion to its health).
        if (e.getHealth() < e.getMaxHealth()) {
            float heal = ArcherRules.regen(now, now - s.lastCombat);
            if (heal > 0.0f) e.heal(heal * KingRules.healScale(e instanceof KingNpcEntity));
        }
        // The NPC's traced copies fade like anyone's.
        ItemStack main = e.getMainHandItem();
        if (!(e instanceof ServerPlayer) && Projection.projected(main) && now >= Projection.expireAt(main)) {
            e.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(FateItems.KANSHOU));
        }
    }

    /**
     * Kanshou in the main hand calls Bakuya into the empty off hand (and the other way round), as a
     * projection that lasts while its twin is held.
     */
    public static void partnerBlade(ServerPlayer p) {
        ItemStack main = p.getMainHandItem();
        ItemStack off = p.getOffhandItem();
        boolean twin = FateItems.twinSword(main) && !p.isSpectator();
        if (!twin) {
            if (Projection.partner(off)) p.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            return;
        }
        ItemStack want = new ItemStack(main.is(FateItems.KANSHOU) ? FateItems.BAKUYA : FateItems.KANSHOU);
        if (off.isEmpty() || Projection.partner(off) && !off.is(want.getItem())) {
            p.setItemSlot(EquipmentSlot.OFFHAND, Projection.copy(want, p.getUUID(), Long.MAX_VALUE, true));
        }
    }
}
