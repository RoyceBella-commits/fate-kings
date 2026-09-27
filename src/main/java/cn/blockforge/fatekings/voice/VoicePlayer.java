package cn.blockforge.fatekings.voice;

import cn.blockforge.fatekings.net.FateNet;
import cn.blockforge.fatekings.registry.FateSounds;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Plays a voice line from a speaker (heard ~48 blocks away) and shows its Chinese line on the
 * action bar of players within 32 blocks. One line at a time per speaker; big lines cut in.
 */
public final class VoicePlayer {
    private static final double SUBTITLE_RANGE = 32.0;
    private static final float VOLUME = 3.0f;
    private static final Map<UUID, Long> BUSY = new ConcurrentHashMap<>();
    private static final Map<UUID, Voice> LAST = new ConcurrentHashMap<>();

    private VoicePlayer() {
    }

    public static void clear() {
        BUSY.clear();
        LAST.clear();
    }

    /** Plays {@code line}; returns false if the speaker was still talking and the line is not a priority one. */
    public static boolean say(LivingEntity speaker, Voice line) {
        if (!(speaker.level() instanceof ServerLevel level)) return false;
        long now = level.getGameTime();
        long busy = BUSY.getOrDefault(speaker.getUUID(), 0L);
        if (now < busy && !line.priority()) return false;
        if (now < busy) stop(speaker);
        SoundEvent sound = FateSounds.voice(line);
        if (sound != null) {
            level.playSound(null, speaker.getX(), speaker.getEyeY(), speaker.getZ(), sound, SoundSource.VOICE, VOLUME, 1.0f);
        }
        BUSY.put(speaker.getUUID(), now + line.ticks());
        LAST.put(speaker.getUUID(), line);
        subtitle(level, speaker, line);
        return true;
    }

    /** Stops whatever the speaker is saying (the chant is cut when the sword is released early). */
    public static void stop(LivingEntity speaker) {
        Voice last = LAST.remove(speaker.getUUID());
        BUSY.remove(speaker.getUUID());
        if (last == null || last.clip == null || !(speaker.level() instanceof ServerLevel level)) return;
        SoundEvent sound = FateSounds.voice(last);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(speaker) < 96.0 * 96.0) {
                p.connection.send(new ClientboundStopSoundPacket(sound.location(), SoundSource.VOICE));
            }
        }
    }

    public static boolean talking(LivingEntity speaker) {
        return speaker.level().getGameTime() < BUSY.getOrDefault(speaker.getUUID(), 0L);
    }

    private static void subtitle(ServerLevel level, LivingEntity speaker, Voice line) {
        boolean gil = line.speaker == Voice.Speaker.GILGAMESH;
        Component name = Component.translatable(gil ? "fatekings.speaker.gilgamesh" : "fatekings.speaker.artoria")
            .withStyle(gil ? ChatFormatting.GOLD : ChatFormatting.AQUA);
        Component text = Component.empty().append(name).append(Component.literal("：「").withStyle(ChatFormatting.GRAY))
            .append(Component.translatable(line.key()).withStyle(ChatFormatting.WHITE))
            .append(Component.literal("」").withStyle(ChatFormatting.GRAY));
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(speaker) <= SUBTITLE_RANGE * SUBTITLE_RANGE) FateNet.actionBar(p, text);
        }
    }
}
