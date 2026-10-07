package cn.blockforge.fatekings.client;

import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.KingSync;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;

/** The local player's king state as last sent by the server ({@link KingSync}). */
public final class ClientKingState {
    public static int king = KingRules.NONE;
    public static int lockLeft;
    public static int lockedFrom;
    public static float gold;
    public static float goldMax;
    public static int reorgLeft;
    public static int depletionLeft;
    public static int revealedLeft;
    public static int counterLeft;
    public static int domeLeft;
    public static int regenPausedLeft;
    public static int ubwLeft;
    public static int rhoPetals;
    public static int rhoLeft;
    public static int projected;
    public static final Map<String, Integer> COOLDOWNS = new HashMap<>();
    public static long receivedAt;
    public static long goldChangedAt;

    private ClientKingState() {
    }

    public static void read(FriendlyByteBuf buf) {
        king = buf.readByte();
        lockLeft = buf.readVarInt();
        lockedFrom = buf.readByte();
        float g = buf.readFloat();
        if (g < gold - 0.01f) goldChangedAt = System.nanoTime();
        gold = g;
        goldMax = buf.readFloat();
        reorgLeft = buf.readVarInt();
        depletionLeft = buf.readVarInt();
        revealedLeft = buf.readVarInt();
        counterLeft = buf.readVarInt();
        domeLeft = buf.readVarInt();
        regenPausedLeft = buf.readVarInt();
        int n = buf.readVarInt();
        COOLDOWNS.clear();
        for (int i = 0; i < n; ++i) {
            int left = buf.readVarInt();
            if (i < KingSync.KEYS.length) COOLDOWNS.put(KingSync.KEYS[i], left);
        }
        ubwLeft = buf.readVarInt();
        rhoPetals = buf.readByte();
        rhoLeft = buf.readVarInt();
        projected = buf.readByte();
        receivedAt = System.nanoTime();
    }

    /** Ticks elapsed since the last update (the panel counts down smoothly between packets). */
    public static float elapsedTicks() {
        return (System.nanoTime() - receivedAt) / 50_000_000.0f;
    }

    public static float left(int ticks) {
        return Math.max(0.0f, ticks - elapsedTicks());
    }

    public static float cooldown(String key) {
        return left(COOLDOWNS.getOrDefault(key, 0));
    }

    public static void reset() {
        king = KingRules.NONE;
        gold = 0.0f;
        goldMax = 0.0f;
        COOLDOWNS.clear();
    }
}
