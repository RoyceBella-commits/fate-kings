package cn.blockforge.fatekings.npc;

/**
 * The two NPC temperaments as pure state rules (design doc 7.3, 12.3, 13.2). Gilgamesh starts
 * arrogant and escalates; Artoria starts serious and releases her sword whenever it is justified.
 */
public final class KingAiRules {
    // ---- Gilgamesh ----
    public static final int ARROGANT = 0;
    public static final int DISPLEASED = 1;
    public static final int SERIOUS = 2;
    /** Real damage that makes him displeased. */
    public static final float DISPLEASED_DAMAGE = 40.0f;
    public static final int INFINITY_BLOCKED_FOR_EA = 200;
    public static final int EA_PER_FIGHT = 2;

    // ---- Artoria ----
    public static final int COURTESY = 0;
    public static final int FULL_POWER = 1;
    public static final int LAST_STAND = 3;
    public static final int STRONG_FOE_FIGHT_TICKS = 160;
    public static final int CROWD = 8;

    private KingAiRules() {
    }

    /**
     * Gilgamesh's tier after taking a hit.
     *
     * @param totalTaken      real damage taken in this fight so far
     * @param attackerUnworthy the hit came from something beneath him (a vanilla mob, a plain player)
     * @param attackerWorthy  the hit came from a worthy foe (who therefore struck first)
     * @param healthFrac      health / max health after the hit
     * @param foeWorthy       whether his current foe is worthy at all
     */
    public static int gilTierAfterHit(int tier, float totalTaken, boolean attackerUnworthy, boolean attackerWorthy,
                                      float healthFrac, boolean foeWorthy, boolean vsSaber) {
        int next = tier;
        if (totalTaken >= DISPLEASED_DAMAGE || attackerUnworthy) next = Math.max(next, DISPLEASED);
        if (attackerWorthy || healthFrac < 0.5f || vsSaber) next = Math.max(next, SERIOUS);
        if (!foeWorthy && !vsSaber) next = Math.min(next, DISPLEASED);
        return next;
    }

    /**
     * Whether Gilgamesh unlocks Ea now. Normally: already serious, a worthy foe, and one of: health
     * below a third / stopped by Infinity for 10 s / Mahoraga adapted to his treasures / an enemy
     * domain open. Against Saber: below half health or struck by her Excalibur once. Always subject
     * to the 180 s cooldown and at most two per fight; never against ordinary mobs.
     */
    public static boolean gilWantsEa(int tier, boolean foeWorthy, float healthFrac, int infinityBlockedTicks,
                                     boolean mahoragaAdapted, boolean enemyDomain, boolean eaReady, int eaUsed,
                                     boolean vsSaber, boolean hitByExcalibur) {
        if (!eaReady || eaUsed >= EA_PER_FIGHT) return false;
        if (vsSaber) return healthFrac < 0.5f || hitByExcalibur;
        if (tier < SERIOUS || !foeWorthy) return false;
        return healthFrac < 1.0f / 3.0f || infinityBlockedTicks >= INFINITY_BLOCKED_FOR_EA || mahoragaAdapted || enemyDomain;
    }

    /** Gates opened per casual shot in each tier (arrogant 1-3, displeased 8-15, serious 30-60). */
    public static int gilGates(int tier, float roll01) {
        return switch (tier) {
            case ARROGANT -> 1 + Math.round(2 * roll01);
            case DISPLEASED -> 8 + Math.round(7 * roll01);
            default -> 30 + Math.round(30 * roll01);
        };
    }

    /** Artoria's tier from her health and foe. */
    public static int saberTier(float healthFrac, boolean strongFoe) {
        if (healthFrac < 0.25f) return LAST_STAND;
        if (strongFoe || healthFrac < 0.7f) return FULL_POWER;
        return COURTESY;
    }

    /**
     * Whether Artoria releases Excalibur now (any one reason suffices), unless the sword is on
     * cooldown or the foe is a neutral animal.
     */
    public static boolean saberWantsExcalibur(boolean ready, boolean neutralAnimal, boolean strongFoe, int fightTicks,
                                              boolean foeUsedUltimate, boolean foeIsBoss, float healthFrac,
                                              int hostilesNear, boolean avalonCounter, boolean lastStand) {
        if (!ready || neutralAnimal) return false;
        if (avalonCounter || lastStand) return true;
        return strongFoe && fightTicks > STRONG_FOE_FIGHT_TICKS || foeUsedUltimate || foeIsBoss || healthFrac < 0.5f
            || hostilesNear >= CROWD;
    }

    // ---- Noble phantasm clash (13.3) ----
    public static final int CLASH_WINDOW = 60;
    public static final int CLASH_TICKS = 60;
    public static final int CLASH_DECIDE = 40;

    public enum ClashResult { AVALON_BLOCKS, EA_OVERPOWERS }

    public static ClashResult clash(boolean avalonReady) {
        return avalonReady ? ClashResult.AVALON_BLOCKS : ClashResult.EA_OVERPOWERS;
    }

    /**
     * Whether two beams fired towards each other meet: launched within 3 s, facing (roughly) opposite
     * ways, and passing closer than their half widths together.
     */
    public static boolean beamsMeet(double[] dirA, double[] dirB, double closest, double halfA, double halfB, long launchGap) {
        double dot = dirA[0] * dirB[0] + dirA[1] * dirB[1] + dirA[2] * dirB[2];
        return launchGap <= CLASH_WINDOW && dot < -0.35 && closest <= halfA + halfB + 1.5;
    }
}
