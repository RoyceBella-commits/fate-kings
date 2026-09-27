package cn.blockforge.fatekings.client;

import java.util.HashMap;
import java.util.Map;

/** The angle of each entity's latest Excalibur stroke (degrees on screen), for about a second. */
public final class ClientSwings {
    private record Stroke(float roll, long at) {
    }

    private static final Map<Integer, Stroke> STROKES = new HashMap<>();

    private ClientSwings() {
    }

    public static synchronized void put(int entityId, float roll) {
        STROKES.put(entityId, new Stroke(roll, System.currentTimeMillis()));
    }

    /** The stroke angle, or null when there is no recent Excalibur stroke for this entity. */
    public static synchronized Float roll(int entityId) {
        Stroke s = STROKES.get(entityId);
        if (s == null) return null;
        if (System.currentTimeMillis() - s.at > 1000L) {
            STROKES.remove(entityId);
            return null;
        }
        return s.roll;
    }

    public static synchronized void clear() {
        STROKES.clear();
    }
}
