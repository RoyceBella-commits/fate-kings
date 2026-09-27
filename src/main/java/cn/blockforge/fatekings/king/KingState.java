package cn.blockforge.fatekings.king;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-player (attachment) or per-NPC (saved with the entity) king data. All windows and cooldowns
 * are absolute game times, so they keep running while the armour is off and are never reset by
 * changing sets (design doc chapter 3, rule 3).
 */
public final class KingState {
    public static final Codec<KingState> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.INT.optionalFieldOf("king", KingRules.NONE).forGetter(s -> s.king),
        Codec.INT.optionalFieldOf("lockedFrom", KingRules.NONE).forGetter(s -> s.lockedFrom),
        Codec.LONG.optionalFieldOf("lockUntil", 0L).forGetter(s -> s.lockUntil),
        Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("cooldowns", Map.of()).forGetter(s -> s.cooldowns),
        Codec.FLOAT.optionalFieldOf("gold", -1.0f).forGetter(s -> s.gold),
        Codec.LONG.optionalFieldOf("reorgUntil", 0L).forGetter(s -> s.reorgUntil),
        Codec.LONG.optionalFieldOf("depletionUntil", 0L).forGetter(s -> s.depletionUntil),
        Codec.LONG.optionalFieldOf("revealedUntil", 0L).forGetter(s -> s.revealedUntil),
        Codec.LONG.optionalFieldOf("counterUntil", 0L).forGetter(s -> s.counterUntil),
        Codec.LONG.optionalFieldOf("regenPausedUntil", 0L).forGetter(s -> s.regenPausedUntil),
        Codec.LONG.optionalFieldOf("domeUntil", 0L).forGetter(s -> s.domeUntil),
        Codec.LONG.optionalFieldOf("guardUntil", 0L).forGetter(s -> s.guardUntil),
        Codec.LONG.optionalFieldOf("healUntil", 0L).forGetter(s -> s.healUntil),
        Codec.BOOL.optionalFieldOf("flightGranted", false).forGetter(s -> s.flightGranted)
    ).apply(i, KingState::new));

    public int king;
    public int lockedFrom;
    public long lockUntil;
    public final Map<String, Long> cooldowns;
    /** Gold hearts in HP; -1 means "not yet granted" (filled when the set is first completed). */
    public float gold;
    public long reorgUntil;
    public long depletionUntil;
    public long revealedUntil;
    public long counterUntil;
    public long regenPausedUntil;
    public long domeUntil;
    public long guardUntil;
    public long healUntil;
    /** "May fly" was granted by this mod (so only that grant is ever taken back). */
    public boolean flightGranted;

    // ---- Not saved ----
    public long lastHurt;
    public long lastCombat;
    public long chainedUntil;
    public boolean counterDouble;
    public boolean airDashUsed;
    public int interruptHits;
    public long interruptWindow;
    public boolean dirty = true;

    public KingState(int king, int lockedFrom, long lockUntil, Map<String, Long> cooldowns, float gold, long reorgUntil,
                     long depletionUntil, long revealedUntil, long counterUntil, long regenPausedUntil, long domeUntil,
                     long guardUntil, long healUntil, boolean flightGranted) {
        this.king = king;
        this.lockedFrom = lockedFrom;
        this.lockUntil = lockUntil;
        this.cooldowns = new HashMap<>(cooldowns);
        this.gold = gold;
        this.reorgUntil = reorgUntil;
        this.depletionUntil = depletionUntil;
        this.revealedUntil = revealedUntil;
        this.counterUntil = counterUntil;
        this.regenPausedUntil = regenPausedUntil;
        this.domeUntil = domeUntil;
        this.guardUntil = guardUntil;
        this.healUntil = healUntil;
        this.flightGranted = flightGranted;
    }

    public static KingState fresh() {
        return new KingState(KingRules.NONE, KingRules.NONE, 0L, Map.of(), -1.0f, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, false);
    }

    public long cooldownEnd(String skill) {
        return this.cooldowns.getOrDefault(skill, 0L);
    }

    public boolean ready(String skill, long now) {
        return now >= cooldownEnd(skill);
    }

    public int cooldownLeft(String skill, long now) {
        return (int)Math.max(0L, cooldownEnd(skill) - now);
    }

    public void cooldown(String skill, long now, int ticks) {
        this.cooldowns.put(skill, now + Math.max(0, ticks));
        this.dirty = true;
    }

    public void clearCooldown(String skill) {
        this.cooldowns.remove(skill);
        this.dirty = true;
    }

    public void clearAllCooldowns() {
        this.cooldowns.clear();
        this.dirty = true;
    }

    public boolean reorganizing(long now) {
        return now < this.reorgUntil;
    }

    public boolean depleted(long now) {
        return now < this.depletionUntil;
    }

    public boolean domeActive(long now) {
        return now < this.domeUntil;
    }
}
