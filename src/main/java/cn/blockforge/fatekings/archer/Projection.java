package cn.blockforge.fatekings.archer;

import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.king.Kings;
import cn.blockforge.fatekings.net.FateNet;
import cn.blockforge.fatekings.registry.FateItems;
import cn.blockforge.fatekings.voice.Voice;
import cn.blockforge.fatekings.voice.VoicePlayer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * Projection (Trace On): copies of weapons EMIYA has analysed. A copy carries a marker (owner, the
 * game time it fades, whether it is the partner blade); it lasts 60 s, at most three at once, and
 * vanishes the moment it leaves his own inventory: dropped, put into any container, crafting grid,
 * anvil, grindstone, bundle or frame, or on his death. Any weapon of the game or of another mod can
 * be copied; Excalibur becomes a replica; the one-of-a-kind noble phantasms cannot be copied.
 */
public final class Projection {
    public static final String MARK = "fatekings_projected";
    private static final Map<UUID, Long> RECORDED_AT = new ConcurrentHashMap<>();

    public enum Check { OK, NOT_WEAPON, UNIQUE }

    private Projection() {
    }

    public static void clear() {
        RECORDED_AT.clear();
    }

    // ---- What can be copied ----

    public static Check check(ItemStack s) {
        if (s.isEmpty()) return Check.NOT_WEAPON;
        if (s.is(FateItems.UNPROJECTABLE) || unique(s)) return Check.UNIQUE;
        if (s.is(FateItems.EXCALIBUR)) return Check.OK;
        if (s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || s.is(ItemTags.SPEARS) || s.is(ItemTags.WEAPON_ENCHANTABLE)
            || s.is(ConventionalItemTags.MELEE_WEAPON_TOOLS) || s.is(ConventionalItemTags.RANGED_WEAPON_TOOLS)
            || s.getItem() instanceof ProjectileWeaponItem || s.is(Items.TRIDENT) || s.is(Items.MACE)) {
            return Check.OK;
        }
        if (s.is(ItemTags.PICKAXES) || s.is(ItemTags.SHOVELS) || s.is(ItemTags.HOES)) return Check.NOT_WEAPON;
        // Another mod's weapon that carries no tag: anything that adds real damage in the main hand.
        return attackBonus(s) >= 2.0 ? Check.OK : Check.NOT_WEAPON;
    }

    public static boolean isWeapon(ItemStack s) {
        return check(s) == Check.OK;
    }

    /** One of a kind: Ea, its key, the treasury, the chains, the Vimana, and EMIYA's own bow and marble. */
    private static boolean unique(ItemStack s) {
        return s.is(FateItems.EA) || s.is(FateItems.BAB_ILU) || s.is(FateItems.GATE_OF_BABYLON) || s.is(FateItems.ENKIDU)
            || s.is(FateItems.VIMANA) || s.is(FateItems.UNLIMITED_BLADE_WORKS) || s.is(FateItems.BLACK_BOW) || s.is(FateItems.EXCALIBUR_REPLICA);
    }

    private static double attackBonus(ItemStack s) {
        double[] sum = {0.0};
        s.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).forEach(EquipmentSlot.MAINHAND, (attr, mod) -> {
            if (attr.value() == Attributes.ATTACK_DAMAGE.value() && mod.operation() == AttributeModifier.Operation.ADD_VALUE) sum[0] += mod.amount();
        });
        return sum[0];
    }

    /** A clean single copy: no damage, no contents, no model state, no marker. Enchantments and attributes stay. */
    public static ItemStack sanitize(ItemStack s) {
        if (s.isEmpty()) return ItemStack.EMPTY;
        ItemStack c = s.copyWithCount(1);
        c.remove(DataComponents.DAMAGE);
        c.remove(DataComponents.CONTAINER);
        c.remove(DataComponents.BUNDLE_CONTENTS);
        c.remove(DataComponents.CHARGED_PROJECTILES);
        c.remove(DataComponents.CONTAINER_LOOT);
        c.remove(DataComponents.CUSTOM_MODEL_DATA);
        CustomData d = c.get(DataComponents.CUSTOM_DATA);
        if (d != null) {
            CompoundTag t = d.copyTag();
            t.remove(MARK);
            if (t.isEmpty()) c.remove(DataComponents.CUSTOM_DATA);
            else c.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
        }
        return c;
    }

    // ---- The marker ----

    /** A projected copy of {@code original} (Excalibur: the replica). */
    public static ItemStack copy(ItemStack original, UUID owner, long expire, boolean partner) {
        ItemStack base = original.is(FateItems.EXCALIBUR) ? new ItemStack(FateItems.EXCALIBUR_REPLICA) : sanitize(original);
        CompoundTag mark = new CompoundTag();
        mark.putIntArray("owner", UUIDUtil.uuidToIntArray(owner));
        mark.putLong("expire", expire);
        mark.putBoolean("partner", partner);
        CustomData.update(DataComponents.CUSTOM_DATA, base, t -> t.put(MARK, mark));
        return base;
    }

    private static CompoundTag mark(ItemStack s) {
        if (s.isEmpty()) return null;
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d == null ? null : d.copyTag().getCompound(MARK).orElse(null);
    }

    public static boolean projected(ItemStack s) {
        return mark(s) != null;
    }

    public static boolean partner(ItemStack s) {
        CompoundTag m = mark(s);
        return m != null && m.getBooleanOr("partner", false);
    }

    public static long expireAt(ItemStack s) {
        CompoundTag m = mark(s);
        return m == null ? 0L : m.getLongOr("expire", 0L);
    }

    private static UUID owner(CompoundTag m) {
        return m.getIntArray("owner").filter(a -> a.length == 4).map(UUIDUtil::uuidFromIntArray).orElse(null);
    }

    // ---- Giving and taking ----

    /** Projects a copy of {@code original} into the player's hotbar (selected) and says so. */
    public static boolean give(ServerPlayer p, ItemStack original) {
        ServerLevel level = p.level();
        long now = level.getGameTime();
        Inventory inv = p.getInventory();
        // At most three: the oldest copy makes way.
        List<Integer> copies = new ArrayList<>();
        for (int i = 0; i < inv.getContainerSize(); ++i) {
            ItemStack s = inv.getItem(i);
            if (projected(s) && !partner(s)) copies.add(i);
        }
        if (copies.size() >= ArcherRules.PROJECTION_MAX) {
            copies.sort((a, b) -> Long.compare(expireAt(inv.getItem(a)), expireAt(inv.getItem(b))));
            for (int k = 0; k <= copies.size() - ArcherRules.PROJECTION_MAX; ++k) inv.setItem(copies.get(k), ItemStack.EMPTY);
        }
        ItemStack copy = copy(original, p.getUUID(), now + ArcherRules.PROJECTION_LIFETIME, false);
        int slot = -1;
        for (int i = 0; i < Inventory.SELECTION_SIZE; ++i) {
            if (inv.getItem(i).isEmpty()) {
                slot = i;
                break;
            }
        }
        if (slot >= 0) {
            inv.setItem(slot, copy);
            inv.setSelectedSlot(slot);
            p.connection.send(new ClientboundSetHeldSlotPacket(slot));
        } else {
            int free = inv.getFreeSlot();
            if (free < 0) {
                Kings.refuse(p, "fatekings.hint.inventory_full");
                return false;
            }
            inv.setItem(free, copy);
        }
        traced(level, p);
        FateNet.actionBar(p, Component.translatable("fatekings.hint.projected", copy.getHoverName()));
        return true;
    }

    /** "Trace on": blue lines of light round the hand, the hum of a forge. */
    public static void traced(ServerLevel level, LivingEntity caster) {
        Fx.event(level, Fx.TRACE, caster, caster.position(), 16, 1.0f, 48.0);
        Fx.particles(level, Fx.dust(Fx.TRACE_CYAN, 0.8f), caster.getX(), caster.getY() + 1.2, caster.getZ(), 16, 0.4, 0.4, 0.4, 0.0);
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 1.4f);
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 0.6f, 1.6f);
        if (!VoicePlayer.talking(caster)) VoicePlayer.say(caster, Voice.EMIYA_TRACE_ON);
    }

    /** A copy breaks into motes of light. */
    public static void fade(ServerLevel level, double x, double y, double z) {
        Fx.particles(level, Fx.dust(Fx.TRACE_CYAN, 0.9f), x, y, z, 12, 0.25, 0.25, 0.25, 0.0);
        Fx.particles(level, ParticleTypes.END_ROD, x, y, z, 4, 0.2, 0.2, 0.2, 0.02);
        level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 0.6f, 1.6f);
    }

    /**
     * Every tick for every player: copies that ran out, that belong to someone else, that outlived
     * their maker's Archer state, or a partner blade away from the off hand, vanish.
     */
    public static void sweep(ServerPlayer p, long now) {
        boolean archer = Kings.isArcher(p) && p.isAlive();
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); ++i) {
            ItemStack s = inv.getItem(i);
            CompoundTag m = mark(s);
            if (m == null) continue;
            boolean partner = m.getBooleanOr("partner", false);
            boolean keep = archer && p.getUUID().equals(owner(m)) && (partner ? i == Inventory.SLOT_OFFHAND : now < m.getLongOr("expire", 0L));
            if (!keep) {
                inv.setItem(i, ItemStack.EMPTY);
                if (!partner) fade(p.level(), p.getX(), p.getY() + 1.0, p.getZ());
            }
        }
        ItemStack carried = p.containerMenu.getCarried();
        CompoundTag m = mark(carried);
        if (m != null && (!archer || now >= m.getLongOr("expire", 0L) || m.getBooleanOr("partner", false))) {
            p.containerMenu.setCarried(ItemStack.EMPTY);
        }
    }

    /** Copies never stay in a container: anything projected in a foreign slot of the open menu fades. */
    public static void purgeMenu(Player p) {
        purgeMenu(p.containerMenu, p);
    }

    public static void purgeMenu(net.minecraft.world.inventory.AbstractContainerMenu menu, Player p) {
        for (Slot slot : menu.slots) {
            if (slot.container == p.getInventory()) continue;
            if (projected(slot.getItem())) {
                slot.set(ItemStack.EMPTY);
                if (p.level() instanceof ServerLevel level) fade(level, p.getX(), p.getY() + 1.0, p.getZ());
            }
        }
    }

    /** All his copies go (he stopped being the Archer, left, or died). */
    public static void dissipateAll(Player p) {
        Inventory inv = p.getInventory();
        boolean any = false;
        for (int i = 0; i < inv.getContainerSize(); ++i) {
            if (projected(inv.getItem(i))) {
                inv.setItem(i, ItemStack.EMPTY);
                any = true;
            }
        }
        if (projected(p.containerMenu.getCarried())) p.containerMenu.setCarried(ItemStack.EMPTY);
        if (any && p.level() instanceof ServerLevel level) fade(level, p.getX(), p.getY() + 1.0, p.getZ());
    }

    /** His copies in hand (the partner blade not counted). */
    public static int count(Player p) {
        Inventory inv = p.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); ++i) {
            ItemStack s = inv.getItem(i);
            if (projected(s) && !partner(s)) ++n;
        }
        return n;
    }

    // ---- Learning from blows ----

    /** The weapon behind a blow: the attacker's blade, a thrown trident, a treasure of the Gate, a bow's arrow. */
    public static ItemStack weaponOf(DamageSource source) {
        ItemStack w = source.getWeaponItem();
        if (w != null && !w.isEmpty()) return w;
        Entity direct = source.getDirectEntity();
        if (direct instanceof cn.blockforge.fatekings.entity.TreasureProjectile tp) return tp.weapon();
        if (direct instanceof AbstractArrow arrow && arrow.getWeaponItem() != null) return arrow.getWeaponItem();
        return ItemStack.EMPTY;
    }

    /** EMIYA struck by a weapon: he has seen it now, and it goes to the Hill of Swords. */
    public static void learnFrom(LivingEntity archer, DamageSource source) {
        if (!Kings.isArcher(archer)) return;
        ItemStack weapon = weaponOf(source);
        if (check(weapon) != Check.OK || projected(weapon) && source.getEntity() == archer) return;
        long now = archer.level().getGameTime();
        Long last = RECORDED_AT.get(archer.getUUID());
        if (last != null && now - last < 20) return;
        RECORDED_AT.put(archer.getUUID(), now);
        Arsenal.Data data = Arsenal.of(archer);
        if (data == null) return;
        for (Arsenal.Entry e : data.entries) {
            if (ItemStack.isSameItemSameComponents(e.stack(), sanitize(weapon))) return;
        }
        Arsenal.record(data, weapon, now);
        if (archer instanceof ServerPlayer sp) {
            FateNet.actionBar(sp, Component.translatable("fatekings.hint.analysed", weapon.getHoverName()));
        }
    }
}
