package cn.blockforge.fatekings.voice;

/**
 * Voice lines (FGO voice clips from fgo.wiki, the two signature noble-phantasm lines supplied by the
 * user) and a few subtitle-only lines. The Chinese subtitle of each is the lang key
 * {@code fatekings.voice.<id>}; {@code seconds} is the clip length, used to avoid overlaps.
 */
public enum Voice {
    GIL_SPAWN(Speaker.GILGAMESH, "gil_spawn", 6.7f),
    GIL_ARROGANT(Speaker.GILGAMESH, "gil_arrogant", 4.1f),
    GIL_DISPLEASED(Speaker.GILGAMESH, "gil_displeased", 2.2f),
    GIL_SERIOUS(Speaker.GILGAMESH, "gil_serious", 4.7f),
    GIL_VOLLEY(Speaker.GILGAMESH, "gil_volley", 1.6f),
    GIL_CHAIN(Speaker.GILGAMESH, "gil_chain", 1.6f),
    GIL_UNLOCK(Speaker.GILGAMESH, "gil_unlock", 2.5f),
    GIL_EA_DRAWN(Speaker.GILGAMESH, "gil_ea_drawn", 2.6f),
    GIL_EA_CHANT(Speaker.GILGAMESH, "gil_ea_chant", 2.5f),
    GIL_EA_RELEASE(Speaker.GILGAMESH, "gil_ea_release", 2.7f),
    GIL_LAUGH(Speaker.GILGAMESH, "gil_laugh", 1.2f),
    GIL_HURT(Speaker.GILGAMESH, "gil_hurt", 2.6f),
    GIL_DEFEAT(Speaker.GILGAMESH, "gil_defeat", 2.2f),
    GIL_DEFEAT_SABER(Speaker.GILGAMESH, "gil_defeat_saber", 6.2f),
    GIL_VICTORY(Speaker.GILGAMESH, "gil_victory", 3.2f),
    GIL_PROPOSAL(Speaker.GILGAMESH, null, 3.0f),
    GIL_SABER_NAME(Speaker.GILGAMESH, null, 2.0f),
    GIL_SECOND_EA(Speaker.GILGAMESH, null, 3.0f),
    GIL_KILL_MAHORAGA(Speaker.GILGAMESH, null, 3.0f),
    GIL_DISDAIN(Speaker.GILGAMESH, null, 3.0f),

    SABER_SPAWN(Speaker.ARTORIA, "saber_spawn", 3.3f),
    SABER_SALUTE(Speaker.ARTORIA, "saber_salute", 2.0f),
    SABER_FULL_POWER(Speaker.ARTORIA, "saber_full_power", 1.1f),
    SABER_STRIKE_AIR(Speaker.ARTORIA, "saber_strike_air", 1.0f),
    SABER_RELEASE_CALL(Speaker.ARTORIA, "saber_release_call", 1.9f),
    SABER_EXCALIBUR_CHANT(Speaker.ARTORIA, "saber_excalibur_chant", 4.5f),
    SABER_EXCALIBUR_RELEASE(Speaker.ARTORIA, "saber_excalibur_release", 3.3f),
    SABER_VS_GIL(Speaker.ARTORIA, "saber_vs_gil", 2.2f),
    SABER_VS_SUKUNA(Speaker.ARTORIA, "saber_vs_sukuna", 1.1f),
    SABER_AVALON(Speaker.ARTORIA, "saber_avalon", 1.1f),
    SABER_LAST_STAND(Speaker.ARTORIA, "saber_last_stand", 1.5f),
    SABER_HURT(Speaker.ARTORIA, "saber_hurt", 1.1f),
    SABER_DEFEAT(Speaker.ARTORIA, "saber_defeat", 2.0f),
    SABER_VICTORY(Speaker.ARTORIA, "saber_victory", 1.9f),
    SABER_REPLY(Speaker.ARTORIA, null, 3.0f),
    SABER_CRIPPLED_GOJO(Speaker.ARTORIA, null, 3.0f),
    SABER_KILL_MAHORAGA(Speaker.ARTORIA, null, 3.0f);

    public enum Speaker { GILGAMESH, ARTORIA }

    public final Speaker speaker;
    /** Sound file (under sounds/voice/), or null for a subtitle-only line. */
    public final String clip;
    public final float seconds;

    Voice(Speaker speaker, String clip, float seconds) {
        this.speaker = speaker;
        this.clip = clip;
        this.seconds = seconds;
    }

    public String key() {
        return "fatekings.voice." + this.name().toLowerCase(java.util.Locale.ROOT);
    }

    public int ticks() {
        return Math.round(this.seconds * 20.0f);
    }

    /** Big lines (noble phantasms, defeat, rivalry) may cut into whatever the speaker was saying. */
    public boolean priority() {
        return switch (this) {
            case GIL_UNLOCK, GIL_EA_DRAWN, GIL_EA_CHANT, GIL_EA_RELEASE, GIL_DEFEAT, GIL_DEFEAT_SABER, GIL_SABER_NAME,
                 SABER_RELEASE_CALL, SABER_EXCALIBUR_CHANT, SABER_EXCALIBUR_RELEASE, SABER_AVALON, SABER_DEFEAT,
                 SABER_LAST_STAND, GIL_PROPOSAL, SABER_REPLY -> true;
            default -> false;
        };
    }
}
