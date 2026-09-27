package cn.blockforge.fatekings.combat;

import cn.blockforge.fatekings.king.KingRules;

/** Pure damage-share rules (checked headlessly). */
public final class DamageRules {
    /** Floors of the Gojo x Sukuna mod: 1% in general, 10% from one of its sorcerers. */
    public static final float JJK_FLOOR = 0.01f;
    public static final float JJK_FLOOR_FROM_SORCERER = 0.10f;
    public static final int JJK_MAX_STAGE = 5;

    private DamageRules() {
    }

    /** Share a king takes of one hit. */
    public static float kingTaken(boolean fromModCharacter) {
        return fromModCharacter ? KingRules.TAKEN_MOD : KingRules.TAKEN_VANILLA;
    }

    /** Share the Gojo x Sukuna mod applies on its own to an awakened sorcerer at {@code stage}. */
    public static float jjkTaken(int stage, boolean fromSorcerer) {
        int s = Math.max(0, Math.min(JJK_MAX_STAGE, stage));
        float floor = fromSorcerer ? JJK_FLOOR_FROM_SORCERER : JJK_FLOOR;
        return 1.0f - (1.0f - floor) * s / (float)JJK_MAX_STAGE;
    }

    /**
     * Multiplier this mod applies to a king who is also an awakened sorcerer. The other mod applies
     * its own share as well, so the product of both must be the lower of the two ("取较高值不相加",
     * the more favourable share wins).
     */
    public static float kingTakenWithJjk(boolean fromModCharacter, int jjkStage, boolean fromSorcerer) {
        float ours = kingTaken(fromModCharacter);
        float theirs = jjkTaken(jjkStage, fromSorcerer);
        if (theirs <= 0.0f) return ours;
        return Math.min(ours, theirs) / theirs;
    }

    /** Health after Excalibur cripples Gojo: straight down to one heart; below that it stays as it is. */
    public static float crippledHealth(float health) {
        return Math.min(health, 2.0f);
    }

    /** Extra top-up so that gold hearts from both mods give the higher total, not the sum. */
    public static float kingGoldTarget(float otherModGoldMax) {
        return Math.max(0.0f, KingRules.GOLD_HP - Math.max(0.0f, otherModGoldMax));
    }

    /** Extra modifier so that a value reaches {@code target} without stacking on higher sources. */
    public static double topUp(double current, double target) {
        return Math.max(0.0, target - current);
    }
}
