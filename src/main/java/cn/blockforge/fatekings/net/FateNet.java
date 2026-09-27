package cn.blockforge.fatekings.net;

import cn.blockforge.fatekings.FateKings;
import io.netty.buffer.Unpooled;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Bounded byte payloads, in the style of the Gojo x Sukuna mod: one payload type per channel, each
 * with a hard size limit so no channel accepts unbounded data.
 */
public final class FateNet {
    /** King state of the receiving player (HUD, gold hearts, Excalibur model, leap / flight). */
    public static final Identifier S2C_STATE = FateKings.id("state");
    /** A visual event: sky labyrinth, light pillar, Avalon dome, clash, shake, flash, cracks ... */
    public static final Identifier S2C_FX = FateKings.id("fx");
    /** Sides of nearby players for the Sha Naqba Imuru target panel. */
    public static final Identifier S2C_SIDES = FateKings.id("sides");
    /** Instinct: an ultimate is being prepared within 64 blocks. */
    public static final Identifier S2C_WARN = FateKings.id("warn");
    /** Artoria's Mana Burst leap on jump (forward, strafe, was on ground). */
    public static final Identifier C2S_LEAP = FateKings.id("leap");

    /** The knight swung Excalibur: the swing's angle on screen (degrees) and its place in the combo. */
    public static final Identifier C2S_SLASH = FateKings.id("slash");
    /** Someone swung Excalibur at this angle (so everyone sees the same stroke). */
    public static final Identifier S2C_SWING = FateKings.id("swing");

    private static final Map<Identifier, Integer> LIMITS = new LinkedHashMap<>();
    private static final Map<Identifier, CustomPacketPayload.Type<Payload>> TYPES = new LinkedHashMap<>();

    public record Payload(CustomPacketPayload.Type<Payload> type, byte[] data) implements CustomPacketPayload {
    }

    private FateNet() {
    }

    public static void registerTypes() {
        register(S2C_STATE, 512, false);
        register(S2C_FX, 128, false);
        register(S2C_SIDES, 8192, false);
        register(S2C_WARN, 96, false);
        register(C2S_LEAP, 9, true);
        register(C2S_SLASH, 5, true);
        register(S2C_SWING, 12, false);
    }

    private static void register(Identifier id, int limit, boolean c2s) {
        var type = new CustomPacketPayload.Type<Payload>(id);
        TYPES.put(id, type);
        LIMITS.put(id, limit);
        StreamCodec<RegistryFriendlyByteBuf, Payload> codec = StreamCodec.of((buf, payload) -> {
            if (payload.data.length > limit) throw new IllegalArgumentException("Payload too large: " + id);
            buf.writeVarInt(payload.data.length);
            buf.writeBytes(payload.data);
        }, buf -> {
            int n = buf.readVarInt();
            if (n < 0 || n > limit || n > buf.readableBytes()) throw new IllegalArgumentException("Invalid payload size: " + id);
            byte[] data = new byte[n];
            buf.readBytes(data);
            return new Payload(type, data);
        });
        if (c2s) PayloadTypeRegistry.serverboundPlay().register(type, codec);
        else PayloadTypeRegistry.clientboundPlay().register(type, codec);
    }

    public static CustomPacketPayload.Type<Payload> type(Identifier id) {
        return TYPES.get(id);
    }

    public static FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    public static Payload payload(Identifier id, FriendlyByteBuf buffer) {
        try {
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            if (bytes.length > LIMITS.get(id)) throw new IllegalArgumentException("Payload too large: " + id);
            return new Payload(type(id), bytes);
        } finally {
            buffer.release();
        }
    }

    public interface Receiver {
        void receive(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buffer);
    }

    public static void receive(Identifier id, Receiver receiver) {
        ServerPlayNetworking.registerGlobalReceiver(type(id), (payload, context) -> {
            byte[] data = payload.data();
            context.server().execute(() -> {
                var buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
                try {
                    receiver.receive(context.server(), context.player(), buf);
                } finally {
                    buf.release();
                }
            });
        });
    }

    public static void send(ServerPlayer player, Identifier id, FriendlyByteBuf buffer) {
        if (!ServerPlayNetworking.canSend(player, id)) {
            buffer.release();
            return;
        }
        ServerPlayNetworking.send(player, payload(id, buffer));
    }

    /** Sends a copy of the same data to every player of the level within {@code radius} of {@code at}. */
    public static void sendNear(ServerLevel level, Vec3 at, double radius, Identifier id, FriendlyByteBuf buffer) {
        try {
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), bytes);
            for (ServerPlayer p : level.players()) {
                if (p.position().distanceToSqr(at) > radius * radius || !ServerPlayNetworking.canSend(p, id)) continue;
                ServerPlayNetworking.send(p, new Payload(type(id), bytes));
            }
        } finally {
            buffer.release();
        }
    }

    public static void actionBar(ServerPlayer player, Component text) {
        player.connection.send(new ClientboundSystemChatPacket(text, true));
    }
}
