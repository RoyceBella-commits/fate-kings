package cn.blockforge.fatekings.combat;

import cn.blockforge.fatekings.archer.ArcherRules;
import cn.blockforge.fatekings.king.KingRules;

/**
 * What the two anti-world noble phantasms do to each side (design doc 11.4 / 6.3 / 15.1).
 * Pure: the caller maps entities to a {@link Side}.
 */
public final class JudgementRules {
    public enum Side {
        /** Vanilla mob: animals, monsters, villagers. */
        VANILLA,
        /** Ender dragon, wither, warden, elder guardian. */
        VANILLA_BOSS,
        /** A player with no Gojo / Sukuna route and no king's armour ("普通玩家也属于原版"). */
        PLAYER,
        /** Gojo NPC or a Gojo-route player. */
        GOJO,
        /** Sukuna NPC or a Sukuna-route player. */
        SUKUNA,
        MAHORAGA,
        HERO,
        KNIGHT,
        ARCHER,
        /** A creature from another mod. */
        OTHER_MOD
    }

    /** Ea and Excalibur (anti-world), EMIYA's Caladbolg II and a projected replica of Excalibur. */
    public enum Weapon { EA, EXCALIBUR, CALADBOLG, EXCALIBUR_REPLICA }

    public enum Outcome {
        /** Dies outright: ignores health, armour, resistance and invulnerability frames. */
        INSTANT_DEATH,
        /** Mahoraga: dies without its wheel turning (no adaptation, no last stand). */
        WHEEL_CANNOT_TURN,
        /** Gojo: Infinity pierced, left at one heart, never killed by the blast itself. */
        CRIPPLE,
        /** 1000 damage to a creature; armour applies. */
        HEAVY_DAMAGE,
        /** A Gojo / Sukuna / king NPC: 190 health off after every share; armour and gold hearts ignored. */
        NPC_BLOW,
        /** A player: 90-100, then the target's own taken share (10% for a king or a stage V sorcerer). */
        CHARACTER_DAMAGE
    }

    private JudgementRules() {
    }

    /** @param npc whether the target is not a player (for the character sides: one of the four NPCs) */
    public static Outcome outcome(Weapon weapon, Side side, boolean npc, boolean bossInstakill) {
        if (side == Side.MAHORAGA) return killsMahoraga(weapon) ? Outcome.WHEEL_CANNOT_TURN : Outcome.HEAVY_DAMAGE;
        Outcome character = npc ? Outcome.NPC_BLOW : Outcome.CHARACTER_DAMAGE;
        if (weapon == Weapon.EXCALIBUR) {
            return switch (side) {
                case VANILLA, PLAYER -> Outcome.INSTANT_DEATH;
                case VANILLA_BOSS -> bossInstakill ? Outcome.INSTANT_DEATH : Outcome.HEAVY_DAMAGE;
                case GOJO -> Outcome.CRIPPLE;
                case OTHER_MOD -> Outcome.HEAVY_DAMAGE;
                default -> character;
            };
        }
        return switch (side) {
            case VANILLA, VANILLA_BOSS, OTHER_MOD -> Outcome.HEAVY_DAMAGE;
            case PLAYER -> Outcome.CHARACTER_DAMAGE;
            default -> character;
        };
    }

    /** Whether the attack goes straight through Infinity: everything here but a replica. */
    public static boolean piercesInfinity(Weapon weapon) {
        return weapon != Weapon.EXCALIBUR_REPLICA;
    }

    /** Mahoraga dies outright, its wheel unturned: Ea, Excalibur, Caladbolg II (a replica is adapted to). */
    public static boolean killsMahoraga(Weapon weapon) {
        return weapon != Weapon.EXCALIBUR_REPLICA;
    }

    /** Health a Gojo / Sukuna / spirit NPC loses to one blow after every share. */
    public static float npcLoss(Weapon weapon) {
        return switch (weapon) {
            case EA, EXCALIBUR -> KingRules.NPC_PHANTASM_LOSS;
            case CALADBOLG -> ArcherRules.CALADBOLG_NPC_LOSS;
            case EXCALIBUR_REPLICA -> ArcherRules.REPLICA_NPC_LOSS;
        };
    }

    /** Damage on a creature (armour applies). */
    public static float mobDamage(Weapon weapon) {
        return switch (weapon) {
            case EA, EXCALIBUR -> KingRules.MOB_DAMAGE;
            case CALADBOLG -> ArcherRules.CALADBOLG_MOB_DAMAGE;
            case EXCALIBUR_REPLICA -> ArcherRules.REPLICA_MOB_DAMAGE;
        };
    }

    /** Damage on a player before his taken share: 90-100, Caladbolg 60-70, a replica 45-50. */
    public static float characterDamage(Weapon weapon, float roll01) {
        float r = Math.max(0.0f, Math.min(1.0f, roll01));
        return switch (weapon) {
            case EA, EXCALIBUR -> KingRules.ultimateOnCharacter(r);
            case CALADBOLG -> 60.0f + 10.0f * r;
            case EXCALIBUR_REPLICA -> 45.0f + 5.0f * r;
        };
    }

    /** Sides that count as "worthy" for Gilgamesh and as "strong foes" for Artoria. */
    public static boolean worthy(Side side, boolean stageFive) {
        return switch (side) {
            case VANILLA_BOSS, MAHORAGA, HERO, KNIGHT, ARCHER -> true;
            case GOJO, SUKUNA -> stageFive;
            default -> false;
        };
    }
}
