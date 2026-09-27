package cn.blockforge.fatekings.combat;

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
        /** A creature from another mod. */
        OTHER_MOD
    }

    public enum Weapon { EA, EXCALIBUR }

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
        if (side == Side.MAHORAGA) return Outcome.WHEEL_CANNOT_TURN;
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

    /** Whether the attack goes straight through Infinity (only these two, and nothing else of either king). */
    public static boolean piercesInfinity(Weapon weapon) {
        return true;
    }

    /** Sides that count as "worthy" for Gilgamesh and as "strong foes" for Artoria. */
    public static boolean worthy(Side side, boolean stageFive) {
        return switch (side) {
            case VANILLA_BOSS, MAHORAGA, HERO, KNIGHT -> true;
            case GOJO, SUKUNA -> stageFive;
            default -> false;
        };
    }
}
