package cn.blockforge.fatekings.archer;

/**
 * Pure tables and rules of EMIYA (Archer): the black bow, Caladbolg II, Rho Aias, Kanshou &amp;
 * Bakuya, projection and Unlimited Blade Works. No game classes, so {@code RulesCheck} checks them
 * headlessly. Times are in ticks unless a name says otherwise.
 */
public final class ArcherRules {
    // ---- Black bow ----
    /** Let go before this: a tap. Held longer but short of Caladbolg: still a plain shot. */
    public static final int BOW_TAP_TICKS = 6;
    public static final int BOW_TAP = 4;
    public static final int BOW_TAP_UBW = 2;
    public static final float ARROW_SPEED = 3.2f;
    public static final float ARROW_DAMAGE_MIN = 12.0f;
    public static final float ARROW_DAMAGE_MAX = 16.0f;
    public static final double ARROW_TURN_DEG = 18.0;
    public static final double ARROW_TURN_NEAR_DEG = 30.0;
    public static final double ARROW_NEAR = 6.0;
    /** The arrow leaves the string straight for this long before it starts to hunt. */
    public static final int ARROW_ARM_TICKS = 2;
    public static final int ARROW_LIFETIME = 60;
    public static final int ARROW_STUCK_TICKS = 20;
    public static final double ARROW_ACQUIRE_RANGE = 24.0;
    public static final double ARROW_CONE_COS = 0.64;
    /** At release: the foe in the crosshair, else the nearest foe within 30 degrees. */
    public static final double ARROW_START_CONE_COS = 0.866;
    public static final double ARROW_START_RANGE = 48.0;
    /** The arrow aims a little ahead of a moving foe, never by more than this. */
    public static final double ARROW_LEAD_MAX = 3.0;
    /** Sneak + attack: three arrows in a fan, 8 degrees apart (its own cooldown, not the tap's). */
    public static final int BOW_TRIPLE = 20;
    public static final int BOW_TRIPLE_UBW = 10;
    public static final double TRIPLE_SPREAD_DEG = 8.0;
    public static final int TRIPLE_ARROWS = 3;

    // ---- Regeneration (the Red Shroud's slow mending) ----
    /** Out of combat (5 s unhurt): 1 heart a second. In combat: 1 heart every 4 s. */
    public static final int REGEN_IDLE_AFTER = 100;
    public static final float REGEN_HP = 2.0f;
    public static final int REGEN_IDLE_INTERVAL = 20;
    public static final int REGEN_COMBAT_INTERVAL = 80;

    // ---- Caladbolg II ----
    public static final int CALADBOLG_CHARGE = 30;
    public static final double CALADBOLG_RANGE = 160.0;
    public static final double CALADBOLG_SPEED = 6.0;
    public static final float CALADBOLG_WIDTH = 2.4f;
    public static final double CALADBOLG_TUNNEL = 1.5;
    public static final double CALADBOLG_BLAST = 6.0;
    public static final float CALADBOLG_NPC_LOSS = 120.0f;
    public static final float CALADBOLG_MOB_DAMAGE = 600.0f;
    public static final int CALADBOLG = 900;
    public static final int CALADBOLG_NPC = 1200;

    // ---- Rho Aias ----
    public static final int RHO_AIAS = 1200;
    public static final int RHO_AIAS_TIME = 120;
    public static final int RHO_AIAS_PETALS = 7;
    public static final double RHO_AIAS_RADIUS = 3.0;
    public static final double RHO_AIAS_DIST = 2.2;
    /** Projectiles each petal stops before it breaks (7 petals: 35, most of a full volley). */
    public static final int RHO_AIAS_HITS_PER_PETAL = 5;

    // ---- Kanshou & Bakuya ----
    public static final float TWIN_SWING = 20.0f;
    public static final int TWIN_THROW = 60;
    public static final float THROWN_BLADE_DAMAGE = 16.0f;
    public static final int CRANE_WING = 400;
    public static final float CRANE_FINAL = 40.0f;
    /** Least ticks between two combo steps (a click faster than this is ignored). */
    public static final int TWIN_GAP = 5;
    /** The combo starts over after this long without a stroke. */
    public static final long TWIN_RESET_MS = 1200L;

    // ---- Projection ----
    public static final int TRACE = 10;
    public static final int PROJECTION_LIFETIME = 1200;
    public static final int PROJECTION_MAX = 3;
    public static final int ARSENAL_CAP = 27;
    public static final double ANALYSIS_RANGE = 32.0;

    // ---- Unlimited Blade Works ----
    public static final int UBW_CHANT = 60;
    /** Holding the item this long starts the chant (shorter: analysis / projection). */
    public static final int UBW_CHANT_START = 6;
    public static final int UBW_UNFOLD = 40;
    public static final int UBW_TIME = 600;
    public static final int UBW_CLOSE = 20;
    /** 60 s, player and NPC alike: the same as a domain in the Gojo x Sukuna mod. */
    public static final int UBW = 1200;
    public static final int UBW_NPC = 1200;
    /** 64 blocks, as a domain of the Gojo x Sukuna mod (since its 2.2.6). */
    public static final double UBW_RADIUS = 64.0;
    public static final int UBW_VOLLEY_INTERVAL = 20;
    public static final float UBW_SWORD_DAMAGE = 14.0f;
    public static final int UBW_MAX_SWORDS_PER_VOLLEY = 24;
    public static final int UBW_MAX_TARGETS = 12;
    /** Every treasure inside is met by a blade (at most this many in the air at once; past that it shatters unseen). */
    public static final int UBW_MAX_INTERCEPTORS = 120;
    /** JJK's domain clash: each side keeps at most 20 s, the one that opened first 2 s more. */
    public static final int CLASH_TICKS = 400;
    public static final int CLASH_FIRST_BONUS = 40;

    // ---- Excalibur replica ----
    public static final int REPLICA_CHARGE = 30;
    public static final int REPLICA_FULL = 80;
    public static final double REPLICA_RANGE = 80.0;
    public static final float REPLICA_NPC_LOSS = 95.0f;
    public static final float REPLICA_MOB_DAMAGE = 500.0f;
    public static final int REPLICA = 1800;

    private ArcherRules() {
    }

    /** The bow's tap cooldown: twice as fast inside his own reality marble. */
    public static int bowTapCooldown(boolean insideUbw) {
        return insideUbw ? BOW_TAP_UBW : BOW_TAP;
    }

    public static int bowTripleCooldown(boolean insideUbw) {
        return insideUbw ? BOW_TRIPLE_UBW : BOW_TRIPLE;
    }

    /** Yaw of arrow {@code i} of the fan (0 left, 1 centre, 2 right) from the aim, in degrees (positive to the left). */
    public static double tripleYaw(int i) {
        return (1 - i) * TRIPLE_SPREAD_DEG;
    }

    /**
     * Which foe each arrow of the fan hunts. {@code dots[i][j]}: how well arrow i's direction points
     * at foe j (cosine); foe 0 is the one aimed at, if any ({@code aimed}). The centre arrow takes the
     * aimed foe (else the one best in line with it); then the side arrows take the other foes, the
     * best-placed pair first; an arrow left without a foe follows the centre's. -1: no foe at all
     * (the arrow flies straight).
     */
    public static int[] tripleTargets(double[][] dots, boolean aimed) {
        int arrows = dots.length;
        int[] pick = new int[arrows];
        java.util.Arrays.fill(pick, -1);
        int foes = arrows == 0 ? 0 : dots[0].length;
        if (foes == 0) return pick;
        boolean[] taken = new boolean[foes];
        int centre = arrows / 2;
        if (aimed) {
            pick[centre] = 0;
        } else {
            pick[centre] = 0;
            for (int j = 1; j < foes; ++j) if (dots[centre][j] > dots[centre][pick[centre]]) pick[centre] = j;
        }
        taken[pick[centre]] = true;
        while (true) {
            int bi = -1, bj = -1;
            for (int i = 0; i < arrows; ++i) {
                if (pick[i] >= 0) continue;
                for (int j = 0; j < foes; ++j) {
                    if (!taken[j] && (bi < 0 || dots[i][j] > dots[bi][bj])) {
                        bi = i;
                        bj = j;
                    }
                }
            }
            if (bi < 0) break;
            pick[bi] = bj;
            taken[bj] = true;
        }
        for (int i = 0; i < arrows; ++i) if (pick[i] < 0) pick[i] = pick[centre];
        return pick;
    }

    /** Health mended this tick ({@code now}: game time) by the Red Shroud, before the NPC scale. */
    public static float regen(long now, long ticksSinceHurt) {
        int interval = ticksSinceHurt >= REGEN_IDLE_AFTER ? REGEN_IDLE_INTERVAL : REGEN_COMBAT_INTERVAL;
        return now % interval == 0 ? REGEN_HP : 0.0f;
    }

    public static int caladbolgCooldown(boolean npc) {
        return npc ? CALADBOLG_NPC : CALADBOLG;
    }

    public static int ubwCooldown(boolean npc) {
        return npc ? UBW_NPC : UBW;
    }

    /** Ticks a reality marble has left after meeting another domain (JJK's rule, mirrored). */
    public static int ubwAfterClash(int remaining, boolean openedFirst) {
        return Math.min(remaining, CLASH_TICKS) + (openedFirst ? CLASH_FIRST_BONUS : 0);
    }

    /** Petals a beam tears from Rho Aias (Ea and Excalibur are not stopped at all). */
    public static int beamPetalCost(boolean caladbolg, boolean replica) {
        return caladbolg ? 4 : replica ? 3 : 2;
    }

    /** Petals left after {@code hits} projectiles. */
    public static int petalsAfter(int hits) {
        return Math.max(0, RHO_AIAS_PETALS - hits / RHO_AIAS_HITS_PER_PETAL);
    }

    public static float replicaWidth(int chargeTicks) {
        float t = Math.max(0.0f, Math.min(1.0f, (chargeTicks - REPLICA_CHARGE) / (float)(REPLICA_FULL - REPLICA_CHARGE)));
        return 2.5f + 2.0f * t;
    }

    public static float arrowDamage(float roll01) {
        return ARROW_DAMAGE_MIN + (ARROW_DAMAGE_MAX - ARROW_DAMAGE_MIN) * Math.max(0.0f, Math.min(1.0f, roll01));
    }

    /** The homing arrow's turn limit this tick, in radians. */
    public static double arrowTurn(double distance) {
        return Math.toRadians(distance < ARROW_NEAR ? ARROW_TURN_NEAR_DEG : ARROW_TURN_DEG);
    }

    /**
     * Turns the unit vector {@code cur} toward the unit vector {@code want} by at most {@code maxRad};
     * the result is a unit vector. Opposite directions turn about any perpendicular axis.
     */
    public static double[] turn(double[] cur, double[] want, double maxRad) {
        double[] a = unit(cur), b = unit(want);
        double dot = Math.max(-1.0, Math.min(1.0, a[0] * b[0] + a[1] * b[1] + a[2] * b[2]));
        double angle = Math.acos(dot);
        if (angle <= maxRad) return b;
        // The axis of the turn, and a unit vector perpendicular to a in the plane of the turn.
        double[] perp = {b[0] - a[0] * dot, b[1] - a[1] * dot, b[2] - a[2] * dot};
        double len = Math.sqrt(perp[0] * perp[0] + perp[1] * perp[1] + perp[2] * perp[2]);
        if (len < 1.0E-9) {
            // Anti-parallel: any perpendicular will do.
            perp = Math.abs(a[1]) < 0.9 ? cross(a, new double[]{0, 1, 0}) : cross(a, new double[]{1, 0, 0});
            perp = unit(perp);
        } else {
            perp = new double[]{perp[0] / len, perp[1] / len, perp[2] / len};
        }
        double c = Math.cos(maxRad), s = Math.sin(maxRad);
        return unit(new double[]{a[0] * c + perp[0] * s, a[1] * c + perp[1] * s, a[2] * c + perp[2] * s});
    }

    /** Where to aim at a moving foe: ahead along its pace for the flight time, capped. */
    public static double[] lead(double[] centre, double[] pace, double distance, double speed, double cap) {
        double t = distance / Math.max(0.1, speed);
        double dx = pace[0] * t, dy = pace[1] * t, dz = pace[2] * t;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > cap) {
            dx *= cap / len;
            dy *= cap / len;
            dz *= cap / len;
        }
        return new double[]{centre[0] + dx, centre[1] + dy, centre[2] + dz};
    }

    static double[] unit(double[] v) {
        double len = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return len < 1.0E-12 ? new double[]{0, 0, 1} : new double[]{v[0] / len, v[1] / len, v[2] / len};
    }

    static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    // ---- The twin-blade combo ----

    public static final int STEP_RIGHT = 0;
    public static final int STEP_LEFT = 1;
    public static final int STEP_CROSS = 2;
    public static final int STEP_SPIN = 3;
    public static final int STEP_CHOP = 4;
    public static final int STEP_RISE = 5;
    public static final int STEP_OVEREDGE = 6;
    public static final int COMBO_STEPS = 6;

    /** The stroke after {@code prev}, {@code msSinceLast} later: the six strokes in turn, from the first after a pause. */
    public static int nextStep(int prev, long msSinceLast) {
        if (prev < 0 || prev >= COMBO_STEPS || msSinceLast > TWIN_RESET_MS) return STEP_RIGHT;
        return (prev + 1) % COMBO_STEPS;
    }

    /** How long a stroke's animation runs, in milliseconds. */
    public static long strokeMs(int step) {
        return switch (step) {
            case STEP_CROSS, STEP_SPIN, STEP_OVEREDGE -> 420L;
            case STEP_CHOP, STEP_RISE -> 360L;
            default -> 280L;
        };
    }

    /** Blows the stroke lands on the struck foe (the cross: two). */
    public static int strokeHits(int step) {
        return step == STEP_CROSS || step == STEP_OVEREDGE ? 2 : 1;
    }

    /** Share of the swing dealt by the second blade of a double stroke. */
    public static float secondHit(int step) {
        return step == STEP_CROSS ? 0.6f : step == STEP_OVEREDGE ? 1.0f : 0.0f;
    }

    /** Whether the stroke sweeps everyone round him (the spin) rather than an arc in front. */
    public static boolean strokeAllRound(int step) {
        return step == STEP_SPIN;
    }

    /** Share of the swing dealt to the others caught by the stroke. */
    public static float sweepShare(int step) {
        return switch (step) {
            case STEP_SPIN -> 0.7f;
            case STEP_RIGHT, STEP_LEFT -> 0.5f;
            default -> 0.0f;
        };
    }

    /** Extra share on the struck foe (the downward chop bites deeper). */
    public static float strokeBonus(int step) {
        return step == STEP_CHOP ? 0.4f : 0.0f;
    }

    /** Which hand leads the stroke: 0 main, 1 off, 2 both. */
    public static int strokeHand(int step) {
        return switch (step) {
            case STEP_RIGHT -> 0;
            case STEP_LEFT -> 1;
            default -> 2;
        };
    }
}
