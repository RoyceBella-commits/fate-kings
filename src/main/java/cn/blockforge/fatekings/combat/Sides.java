package cn.blockforge.fatekings.combat;

import cn.blockforge.fatekings.combat.JudgementRules.Side;
import cn.blockforge.fatekings.compat.JjkCompat;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.Kings;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;

/** Which side an entity stands on, for damage shares, judgement and the NPCs' temperaments. */
public final class Sides {
    private Sides() {
    }

    public static Side side(Entity e) {
        if (e == null) return Side.VANILLA;
        int king = Kings.king(e);
        if (e instanceof Player p) {
            int route = JjkCompat.route(p);
            if (route == 1) return Side.GOJO;
            if (king == KingRules.HERO) return Side.HERO;
            if (king == KingRules.KNIGHT) return Side.KNIGHT;
            if (king == KingRules.ARCHER) return Side.ARCHER;
            if (route == 2) return Side.SUKUNA;
            return Side.PLAYER;
        }
        if (king == KingRules.HERO) return Side.HERO;
        if (king == KingRules.KNIGHT) return Side.KNIGHT;
        if (king == KingRules.ARCHER) return Side.ARCHER;
        if (JjkCompat.is(e, JjkCompat.GOJO)) return Side.GOJO;
        if (JjkCompat.is(e, JjkCompat.SUKUNA)) return Side.SUKUNA;
        if (JjkCompat.is(e, JjkCompat.MAHORAGA)) return Side.MAHORAGA;
        if ("minecraft".equals(JjkCompat.typeId(e).getNamespace())) return boss(e) ? Side.VANILLA_BOSS : Side.VANILLA;
        return Side.OTHER_MOD;
    }

    public static boolean boss(Entity e) {
        return e instanceof EnderDragon || e instanceof WitherBoss || e instanceof Warden || e instanceof ElderGuardian
            || e.getType().builtInRegistryHolder().is(ConventionalEntityTypeTags.BOSSES);
    }

    /** Gojo / Sukuna side: their NPCs, Mahoraga and route players. */
    public static boolean jjkSide(Entity e) {
        Side s = side(e);
        return s == Side.GOJO || s == Side.SUKUNA || s == Side.MAHORAGA
            || e instanceof Player p && JjkCompat.awakened(p);
    }

    public static boolean gojoSide(Entity e) {
        return side(e) == Side.GOJO;
    }

    /** "Other mod characters": the kings and the Gojo / Sukuna side. Their blows count at 10%. */
    public static boolean modCharacter(Entity e) {
        if (e == null) return false;
        return Kings.isKing(e) || jjkSide(e);
    }

    /** Stage V for NPCs; the route stage for players. */
    public static boolean stageFive(Entity e) {
        if (e instanceof Player p) return JjkCompat.stage(p) >= 5;
        return true;
    }

    /** Gilgamesh's worthy / Artoria's strong foe. */
    public static boolean worthy(Entity e) {
        return JudgementRules.worthy(side(e), stageFive(e));
    }

    /**
     * Someone with no means or will to fight, whom Gilgamesh will not stoop to strike even when struck:
     * villagers and traders, animals minding their own business, the young, players in creative or
     * spectator mode, and plain players with nothing to fight with (no weapon in hand, no king's armour,
     * no Gojo / Sukuna route).
     */
    public static boolean noncombatant(LivingEntity e) {
        if (e instanceof Player p) {
            if (p.isCreative() || p.isSpectator()) return true;
            if (Kings.isKing(p) || JjkCompat.awakened(p)) return false;
            return !armed(p.getMainHandItem()) && !armed(p.getOffhandItem());
        }
        if (e instanceof net.minecraft.world.entity.monster.Enemy || Kings.isKing(e) || jjkSide(e)) return false;
        if (e instanceof net.minecraft.world.entity.npc.villager.AbstractVillager) return true;
        if (e.isBaby()) return true;
        if (e instanceof net.minecraft.world.entity.animal.Animal) {
            // A wolf or a bee that has turned on someone is fighting; a grazing cow is not.
            return !(e instanceof net.minecraft.world.entity.Mob m && m.getTarget() != null)
                && !(e instanceof net.minecraft.world.entity.NeutralMob n && n.isAngry());
        }
        return false;
    }

    /** Whether an item is something to fight with. */
    public static boolean armed(net.minecraft.world.item.ItemStack s) {
        if (s.isEmpty()) return false;
        return s.is(net.minecraft.tags.ItemTags.SWORDS) || s.is(net.minecraft.tags.ItemTags.AXES) || s.is(net.minecraft.tags.ItemTags.SPEARS)
            || s.is(net.minecraft.tags.ItemTags.WEAPON_ENCHANTABLE) || s.is(net.minecraft.world.item.Items.BOW)
            || s.is(net.minecraft.world.item.Items.CROSSBOW) || s.is(net.minecraft.world.item.Items.TRIDENT) || s.is(net.minecraft.world.item.Items.MACE)
            || s.is(cn.blockforge.fatekings.registry.FateItems.EXCALIBUR) || s.is(cn.blockforge.fatekings.registry.FateItems.EA)
            || s.is(cn.blockforge.fatekings.registry.FateItems.BAB_ILU) || s.is(cn.blockforge.fatekings.registry.FateItems.GATE_OF_BABYLON)
            || s.is(cn.blockforge.fatekings.registry.FateItems.ENKIDU) || s.is(cn.blockforge.fatekings.registry.FateItems.BLACK_BOW)
            || s.is(cn.blockforge.fatekings.registry.FateItems.KANSHOU) || s.is(cn.blockforge.fatekings.registry.FateItems.BAKUYA)
            || s.is(cn.blockforge.fatekings.registry.FateItems.UNLIMITED_BLADE_WORKS) || s.is(cn.blockforge.fatekings.registry.FateItems.EXCALIBUR_REPLICA)
            || cn.blockforge.fatekings.archer.Projection.isWeapon(s);
    }

    public static String sideKey(Side side) {
        return "fatekings.side." + side.name().toLowerCase(java.util.Locale.ROOT);
    }

    public static boolean alive(Entity e) {
        return e instanceof LivingEntity l && l.isAlive();
    }
}
