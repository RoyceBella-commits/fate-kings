package cn.blockforge.fatekings.registry;

import cn.blockforge.fatekings.FateKings;
import cn.blockforge.fatekings.voice.Voice;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

/** One sound event per voice clip ({@code fatekings:voice.<clip>}); all other sounds are vanilla. */
public final class FateSounds {
    private static final Map<Voice, SoundEvent> VOICES = new EnumMap<>(Voice.class);

    static {
        for (Voice v : Voice.values()) {
            if (v.clip == null) continue;
            var id = FateKings.id("voice." + v.clip);
            VOICES.put(v, Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id)));
        }
    }

    private FateSounds() {
    }

    public static SoundEvent voice(Voice v) {
        return VOICES.get(v);
    }

    public static void init() {
    }
}
