package cn.blockforge.fatekings.client;

import cn.blockforge.fatekings.archer.ArcherRules;
import cn.blockforge.fatekings.archer.TwinBlades;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Each entity's latest stroke, for about a second: an Excalibur stroke (its angle on screen), a
 * stroke of Kanshou and Bakuya (its place in the combo), or a shot of the black bow.
 */
public final class ClientSwings {
    private record Stroke(int style, float roll, int step, long at) {
    }

    /** A twin-blade stroke in progress. */
    public record Twin(int step, float progress) {
    }

    private static final Map<Integer, Stroke> STROKES = new HashMap<>();

    private ClientSwings() {
    }

    public static synchronized void put(int entityId, float roll) {
        put(entityId, TwinBlades.STYLE_EXCALIBUR, roll, 0);
    }

    public static synchronized void put(int entityId, int style, float roll, int step) {
        STROKES.put(entityId, new Stroke(style, roll, step, System.currentTimeMillis()));
    }

    private static Stroke recent(int entityId, int style, long within) {
        Stroke s = STROKES.get(entityId);
        if (s == null || s.style != style) return null;
        if (System.currentTimeMillis() - s.at > within) return null;
        return s;
    }

    /** The stroke angle, or null when there is no recent Excalibur stroke for this entity. */
    public static synchronized Float roll(int entityId) {
        Stroke s = recent(entityId, TwinBlades.STYLE_EXCALIBUR, 1000L);
        return s == null ? null : s.roll;
    }

    /** The twin-blade stroke under way, or null once it is over. */
    public static synchronized Twin twin(int entityId) {
        Stroke s = STROKES.get(entityId);
        if (s == null || s.style != TwinBlades.STYLE_TWIN) return null;
        long ms = ArcherRules.strokeMs(s.step);
        float p = (System.currentTimeMillis() - s.at) / (float)ms;
        return p >= 1.0f ? null : new Twin(s.step, Math.max(0.0f, p));
    }

    /** Whether the entity loosed an arrow in the last quarter second (to hold the draw pose). */
    public static synchronized boolean bowShot(int entityId) {
        return recent(entityId, TwinBlades.STYLE_BOW, 250L) != null;
    }

    /** Every twin stroke under way: entity id and stroke (for the trails of light). */
    public static synchronized List<Map.Entry<Integer, Twin>> twins() {
        List<Map.Entry<Integer, Twin>> out = new ArrayList<>();
        for (Integer id : STROKES.keySet()) {
            Twin t = twin(id);
            if (t != null) out.add(Map.entry(id, t));
        }
        return out;
    }

    public static synchronized void clear() {
        STROKES.clear();
    }
}
