package cn.blockforge.fatekings.combat;

import cn.blockforge.fatekings.net.FateNet;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Server-side visual helpers: particles, sounds and client visual events ({@link FateNet#S2C_FX}). */
public final class Fx {
    // Client visual event kinds.
    public static final int LABYRINTH = 0;
    public static final int PILLAR = 1;
    public static final int DOME = 2;
    public static final int AUTODEFENDER = 3;
    public static final int CLASH = 4;
    public static final int SHAKE = 5;
    public static final int FLASH = 6;
    public static final int CRACKS = 7;
    public static final int SKY_DIM = 8;
    public static final int SKY_SPLIT = 9;
    public static final int ARMOR_SCATTER = 10;
    public static final int ARRIVAL_HERO = 11;
    public static final int ARRIVAL_KNIGHT = 12;
    public static final int TITLE_HERO = 13;
    public static final int TITLE_KNIGHT = 14;
    public static final int GOLD_FLASH = 15;

    public static final int GOLD = 0xFFD34A;
    public static final int RED = 0xC8141E;
    public static final int WIND = 0x9FD3EF;
    public static final int KNIGHT_BLUE = 0x2C57C8;
    public static final int MANA = 0xBFE4FF;

    private Fx() {
    }

    /** Sends a visual event to every player within {@code radius}. */
    public static void event(ServerLevel level, int kind, Entity anchor, Vec3 pos, int duration, float strength, double radius) {
        FateNet.sendNear(level, pos, radius, FateNet.S2C_FX, encode(kind, anchor, pos, duration, strength));
    }

    /** Sends a visual event to one player only (screen effects). */
    public static void eventTo(ServerPlayer player, int kind, Entity anchor, Vec3 pos, int duration, float strength) {
        FateNet.send(player, FateNet.S2C_FX, encode(kind, anchor, pos, duration, strength));
    }

    private static net.minecraft.network.FriendlyByteBuf encode(int kind, Entity anchor, Vec3 pos, int duration, float strength) {
        var buf = FateNet.buffer();
        buf.writeByte(kind);
        buf.writeVarInt(anchor == null ? 0 : anchor.getId() + 1);
        buf.writeDouble(pos.x);
        buf.writeDouble(pos.y);
        buf.writeDouble(pos.z);
        buf.writeVarInt(duration);
        buf.writeFloat(strength);
        return buf;
    }

    public static DustParticleOptions dust(int rgb, float scale) {
        return new DustParticleOptions(rgb, scale);
    }

    public static void particles(ServerLevel level, ParticleOptions p, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        level.sendParticles(p, false, true, x, y, z, count, dx, dy, dz, speed);
    }

    public static void particles(ServerLevel level, ParticleOptions p, Vec3 at, int count, double spread, double speed) {
        particles(level, p, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    /** A flat ring of dust around {@code c}. */
    public static void ring(ServerLevel level, Vec3 c, int rgb, double r, int points) {
        DustParticleOptions dust = dust(rgb, 1.0f);
        for (int i = 0; i < points; ++i) {
            double a = i * Math.PI * 2.0 / points;
            particles(level, dust, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** A line of particles between two points (lightning arcs, chains). */
    public static void line(ServerLevel level, ParticleOptions p, Vec3 a, Vec3 b, double step) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1.0E-3) return;
        Vec3 u = d.scale(1.0 / len);
        for (double t = 0; t <= len; t += step) {
            Vec3 q = a.add(u.scale(t));
            particles(level, p, q.x, q.y, q.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    public static void sound(ServerLevel level, Vec3 at, SoundEvent sound, SoundSource source, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, source, volume, pitch);
    }
}
