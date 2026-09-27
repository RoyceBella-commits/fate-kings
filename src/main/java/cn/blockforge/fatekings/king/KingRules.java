package cn.blockforge.fatekings.king;

/**
 * Pure tables and rules of the two kings (no game classes, so they are checked headlessly by
 * {@code RulesCheck}). Times are in ticks unless a name says otherwise.
 */
public final class KingRules {
    public static final int NONE = 0;
    public static final int HERO = 1;
    public static final int KNIGHT = 2;

    /** Leaving one king's state locks the other one for 15 s (design doc chapter 3). */
    public static final int SWAP_LOCK = 300;

    // ---- Base values while the full set is worn (chapters 5 and 9) ----
    public static final float MAX_HEALTH = 80.0f;          // 40 red hearts
    public static final float GOLD_HP = 60.0f;             // 30 gold hearts
    /** The NPCs are tougher than players (200 health, gold scaled alike); players hit them harder to match. */
    public static final float NPC_MAX_HEALTH = 200.0f;
    public static final float NPC_GOLD_HP = GOLD_HP * NPC_MAX_HEALTH / MAX_HEALTH;
    /** A player's blows on a king NPC: the fight lasts as long as against an 80-health king. */
    public static final float PLAYER_VS_NPC = NPC_MAX_HEALTH / MAX_HEALTH;
    /** An NPC's heals scale with its pool, so it recovers at the pace of an 80-health king. */
    public static final float NPC_HEAL_SCALE = NPC_MAX_HEALTH / MAX_HEALTH;
    /** A king's blows (NPC or player) on a Gojo / Sukuna NPC: their techniques ignore armour, ours do not. */
    public static final float KING_VS_JJK_DAMAGE = 2.0f;
    public static final float ARMOR = 30.0f;
    public static final float TOUGHNESS = 20.0f;
    public static final float UNARMED = 24.0f;
    public static final float HERO_SPEED = 0.20f;
    public static final float KNIGHT_SPEED = 0.30f;
    /** Share of a hit a king still takes: 1% from anything vanilla, 10% from another mod character. */
    public static final float TAKEN_VANILLA = 0.01f;
    public static final float TAKEN_MOD = 0.10f;
    /** Gold hearts refill after 10 s without damage, 1 heart every 2 s. */
    public static final int GOLD_REGEN_DELAY = 200;
    public static final int GOLD_REGEN_INTERVAL = 40;

    // ---- Cooldowns ----
    public static final int GOB_TAP = 10;
    public static final int GOB_VOLLEY = 60;
    public static final int GOB_RING = 400;
    public static final int BAB_ILU = 1800;
    public static final int ENKIDU_HOOK = 60;
    public static final int ENKIDU_BIND = 400;
    public static final int VIMANA = 300;
    public static final int ELIXIR = 1200;
    public static final int AUTODEFENDER = 900;
    public static final int STRIKE_AIR = 120;
    public static final int MANA_BURST = 60;
    public static final int EXCALIBUR = 1200;
    public static final int EXCALIBUR_NPC = 1800;
    public static final int EXCALIBUR_LAST_STAND = 900;
    public static final int WARHORSE = 600;
    public static final int AVALON_LETHAL = 4800;
    public static final int AVALON_DOME = 6000;
    public static final int EA_NPC = 3600;

    // ---- Windows ----
    public static final int TREASURY_REORG = 160;     // after Enuma Elish
    public static final int MANA_DEPLETION = 100;     // after Excalibur
    public static final int REVEALED = 200;           // blade shown after Strike Air (10 s)
    public static final int AVALON_COUNTER = 100;     // free Excalibur after the dome
    public static final int DOME = 60;
    public static final int LETHAL_GUARD = 60;
    public static final int LETHAL_HEAL = 100;
    public static final int AVALON_REGEN_PAUSE = 300;
    public static final int EA_LIFETIME = 600;
    public static final int WOUND = 240;              // "圣剑之创" on Gojo
    public static final int CHAIN_BIND = 60;
    public static final int CHAIN_BIND_HIGH = 100;
    public static final int AUTODEFENDER_TIME = 200;

    // ---- Charge thresholds ----
    public static final int TAP_TICKS = 5;
    public static final int GOB_VOLLEY_MIN = 8;
    public static final int GOB_VOLLEY_FULL = 80;
    public static final int GOB_VOLLEY_MIN_GATES = 5;
    public static final int GOB_VOLLEY_MAX_GATES = 100;
    public static final int BAB_ILU_TICKS = 40;
    public static final int ENKIDU_BIND_TICKS = 20;
    public static final int EA_CHARGE = 60;
    public static final int EXCALIBUR_CHARGE = 30;
    public static final int EXCALIBUR_FULL = 80;
    public static final int EXCALIBUR_AUTO_RELEASE = 120;

    // ---- Gate of Babylon, Instinct, sword qi, leap ----
    public static final float GOB_DAMAGE_MIN = 20.0f;
    public static final float GOB_DAMAGE_MAX = 30.0f;
    public static final float DODGE_VANILLA = 0.50f;
    public static final float DODGE_TREASURE = 0.35f;
    public static final float SWORD_QI_DAMAGE = 24.0f;
    public static final double SWORD_QI_RANGE = 28.0;
    public static final int SLASH_GAP = 6;
    public static final double LEAP_HORIZONTAL = 1.7;
    public static final double LEAP_LIFT = 0.7;
    public static final double LEAP_LIFT_PER_UP = 0.9;
    public static final double LEAP_MAX_LIFT = 1.6;
    /** Landing with a thud once the leap lasted this long. */
    public static final int LEAP_SLAM_AIRTIME = 8;

    // ---- Noble phantasms ----
    public static final double EA_RANGE = 200.0;
    public static final double EXCALIBUR_RANGE = 160.0;
    /** Ea / Excalibur on a creature (Ea: every creature; Excalibur: other mods' and spared bosses). Armour applies. */
    public static final float MOB_DAMAGE = 1000.0f;
    /**
     * Health a Gojo / Sukuna / king NPC loses to Ea or Excalibur once every share of both mods has
     * been applied; armour and gold hearts do not soak it (200 health: one blow leaves 10).
     */
    public static final float NPC_PHANTASM_LOSS = 190.0f;

    private KingRules() {
    }

    /**
     * The blow to deal so that {@code loss} is what remains once the target's shares (a product
     * {@code scale}, e.g. 10% x 2.5) have been applied. A missing or broken scale deals the loss as is.
     */
    public static float rawForLoss(float loss, float scale) {
        return scale > 1.0E-6f && Float.isFinite(scale) ? loss / scale : loss;
    }

    /** Which king a set of worn pieces makes: 4 of one set and nothing of the other. */
    public static int kingOfSet(int heroPieces, int knightPieces) {
        if (heroPieces == 4 && knightPieces == 0) return HERO;
        if (knightPieces == 4 && heroPieces == 0) return KNIGHT;
        return NONE;
    }

    /**
     * Whether a player may become {@code wanted} now. Returning to the king one just left is always
     * allowed; switching to the other king waits for the swap lock.
     */
    public static boolean mayEnter(int wanted, int lockedFrom, long lockUntil, long now) {
        if (wanted == NONE) return true;
        if (now >= lockUntil || lockedFrom == NONE) return true;
        return lockedFrom == wanted;
    }

    /** Ticks per gate while a volley is held: 5 gates at the start growing to 40 after 3 s. */
    public static int volleyGates(int heldTicks) {
        if (heldTicks < GOB_VOLLEY_MIN) return 0;
        float t = Math.min(1.0f, (heldTicks - GOB_VOLLEY_MIN) / (float)(GOB_VOLLEY_FULL - GOB_VOLLEY_MIN));
        return Math.round(GOB_VOLLEY_MIN_GATES + (GOB_VOLLEY_MAX_GATES - GOB_VOLLEY_MIN_GATES) * t);
    }

    /** Enuma Elish width grows with the charge beyond the 3 s minimum (4 .. 10 blocks). */
    public static float eaWidth(int chargeTicks) {
        float t = Math.max(0.0f, Math.min(1.0f, (chargeTicks - EA_CHARGE) / 60.0f));
        return 4.0f + 6.0f * t;
    }

    /** Excalibur width: 5 blocks at 1.5 s, 9 blocks at the 4 s maximum. */
    public static float excaliburWidth(int chargeTicks) {
        float t = Math.max(0.0f, Math.min(1.0f, (chargeTicks - EXCALIBUR_CHARGE) / (float)(EXCALIBUR_FULL - EXCALIBUR_CHARGE)));
        return 5.0f + 4.0f * t;
    }

    /** Damage of a noble phantasm on a player or another mod character before its taken share: 90-100. */
    public static float ultimateOnCharacter(float roll01) {
        return 90.0f + 10.0f * Math.max(0.0f, Math.min(1.0f, roll01));
    }

    /** Base Excalibur cooldown of a caster: players 60 s, NPCs 90 s, an NPC at its last stand 45 s. */
    public static int excaliburCooldown(boolean npc, boolean lastStand) {
        if (!npc) return EXCALIBUR;
        return lastStand ? EXCALIBUR_LAST_STAND : EXCALIBUR_NPC;
    }

    /** Swing damage of Excalibur in a knight's hand: 30 under Invisible Air, +30% while revealed. */
    public static float excaliburSwing(boolean revealed, boolean lastStand) {
        return revealed || lastStand ? 39.0f : 30.0f;
    }

    /** Avalon's regeneration per second: 4 HP once 5 s out of combat, 1 HP while fighting. */
    public static float healScale(boolean npc) {
        return npc ? NPC_HEAL_SCALE : 1.0f;
    }

    public static float avalonRegen(long ticksSinceCombat) {
        return ticksSinceCombat >= 100 ? 4.0f : 1.0f;
    }

    /**
     * The knight's leap velocity (Winston-like arc). {@code look} is the view vector (x, y, z). With no
     * key or W held the leap follows the view in 3D (up is allowed); a flat {@code keyDir} (S / A / D)
     * leaps that way along the ground. Returns {vx, vy, vz}.
     */
    public static double[] leapVelocity(double[] look, double[] keyDir, boolean ground) {
        double lookUp = Math.max(0.0, look[1]);
        double lift = Math.min(LEAP_MAX_LIFT, LEAP_LIFT + LEAP_LIFT_PER_UP * lookUp);
        double hx, hz;
        if (keyDir != null) {
            hx = keyDir[0];
            hz = keyDir[2];
            lift = LEAP_LIFT;
        } else {
            double flat = Math.sqrt(look[0] * look[0] + look[2] * look[2]);
            hx = flat < 1.0E-4 ? 0.0 : look[0] / flat;
            hz = flat < 1.0E-4 ? 0.0 : look[2] / flat;
        }
        double speed = LEAP_HORIZONTAL * (keyDir != null ? 1.0 : 1.0 - 0.6 * lookUp);
        if (!ground) lift *= 0.8;
        return new double[]{hx * speed, lift, hz * speed};
    }

    /** Walking on water: a knight that sprints or keeps moving (over 0.05 blocks a tick) stays on the surface. */
    public static boolean waterRun(boolean knight, boolean sprinting, double horizontalSpeedSqr, boolean mob) {
        return knight && (sprinting || mob || horizontalSpeedSqr > 0.0025);
    }

    /** Splits a health loss between gold hearts and red health. Returns {gold used, red left}. */
    public static float[] splitDamage(float gold, float loss) {
        if (loss <= 0.0f) return new float[]{0.0f, 0.0f};
        float used = Math.max(0.0f, Math.min(gold, loss));
        return new float[]{used, loss - used};
    }

    public static String roman(int king) {
        return king == HERO ? "HERO" : king == KNIGHT ? "KNIGHT" : "NONE";
    }
}
