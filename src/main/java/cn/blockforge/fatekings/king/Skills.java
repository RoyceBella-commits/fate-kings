package cn.blockforge.fatekings.king;

/** Cooldown keys stored in {@link KingState#cooldowns} and shown on the HUD. */
public final class Skills {
    public static final String GOB_TAP = "gob_tap";
    public static final String GOB_VOLLEY = "gob_volley";
    public static final String GOB_RING = "gob_ring";
    public static final String BAB_ILU = "bab_ilu";
    public static final String ENKIDU_HOOK = "enkidu_hook";
    public static final String ENKIDU_BIND = "enkidu_bind";
    public static final String VIMANA = "vimana";
    public static final String ELIXIR = "elixir";
    public static final String AUTODEFENDER = "autodefender";
    public static final String STRIKE_AIR = "strike_air";
    public static final String MANA_BURST = "mana_burst";
    public static final String EXCALIBUR = "excalibur";
    public static final String WARHORSE = "warhorse";
    public static final String AVALON_LETHAL = "avalon_lethal";
    public static final String AVALON_DOME = "avalon_dome";
    public static final String BOW_TAP = "bow_tap";
    public static final String CALADBOLG = "caladbolg";
    public static final String RHO_AIAS = "rho_aias";
    public static final String TWIN_THROW = "twin_throw";
    public static final String CRANE_WING = "crane_wing";
    public static final String TRACE = "trace";
    public static final String UBW = "ubw";
    public static final String EXCALIBUR_REPLICA = "excalibur_replica";

    /** Order of the HUD rows per king. */
    public static final String[] HERO_HUD = {GOB_VOLLEY, GOB_RING, BAB_ILU, ENKIDU_BIND, VIMANA, AUTODEFENDER, ELIXIR};
    public static final String[] KNIGHT_HUD = {STRIKE_AIR, MANA_BURST, EXCALIBUR, AVALON_LETHAL, AVALON_DOME, WARHORSE};
    public static final String[] ARCHER_HUD = {CALADBOLG, RHO_AIAS, TWIN_THROW, CRANE_WING, EXCALIBUR_REPLICA, UBW};

    /** The HUD rows of a spirit. */
    public static String[] hud(int king) {
        return switch (king) {
            case KingRules.HERO -> HERO_HUD;
            case KingRules.KNIGHT -> KNIGHT_HUD;
            case KingRules.ARCHER -> ARCHER_HUD;
            default -> new String[0];
        };
    }

    private Skills() {
    }
}
