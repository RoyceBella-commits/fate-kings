package cn.blockforge.fatekings.archer;

import cn.blockforge.fatekings.entity.UbwEntity;
import cn.blockforge.fatekings.king.Kings;
import cn.blockforge.fatekings.net.FateNet;
import cn.blockforge.fatekings.voice.VoicePlayer;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** The few calls the rest of the mod makes into EMIYA's kit (state for the HUD, leaving the set, clean-up). */
public final class Archer {
    private Archer() {
    }

    /** He is no longer the Archer (took the shroud off, died, left): everything of his fades. */
    public static void leave(ServerPlayer p) {
        Projection.dissipateAll(p);
        RhoAias.end(p);
        UbwEntity.collapseOf(p);
        CraneWing.overedge(p, false);
        if (p.isUsingItem() && (p.getUseItem().getItem() instanceof UnlimitedBladeWorksItem || p.getUseItem().getItem() instanceof BlackBowItem)) {
            VoicePlayer.stop(p);
        }
    }

    /** Every tick for every player. */
    public static void sweep(ServerPlayer p, long now) {
        Projection.sweep(p, now);
        if (Kings.isArcher(p)) {
            Arsenal.Data data = Arsenal.of(p);
            if (data.dirty) sendArsenal(p, data);
        }
    }

    public static void purgeMenu(Player p) {
        Projection.purgeMenu(p);
    }

    public static int ubwLeft(ServerPlayer p) {
        return Kings.isArcher(p) ? UbwEntity.leftOf(p) : 0;
    }

    public static int rhoPetals(ServerPlayer p) {
        return RhoAias.petals(p);
    }

    public static int rhoLeft(ServerPlayer p) {
        return RhoAias.left(p);
    }

    public static int projected(ServerPlayer p) {
        return Kings.isArcher(p) ? Projection.count(p) : 0;
    }

    /** The Hill of Swords to its owner's screen: index of the chosen one, then every weapon in full. */
    public static void sendArsenal(ServerPlayer p, Arsenal.Data data) {
        data.dirty = false;
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), p.level().registryAccess());
        buf.writeVarInt(1);
        buf.writeVarInt(data.selected);
        buf.writeVarInt(data.entries.size());
        for (Arsenal.Entry e : data.entries) ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, e.stack());
        if (buf.readableBytes() > 60_000) {
            // Very heavy stacks: names and items only.
            buf.release();
            buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), p.level().registryAccess());
            buf.writeVarInt(1);
            buf.writeVarInt(data.selected);
            buf.writeVarInt(data.entries.size());
            for (Arsenal.Entry e : data.entries) ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, new ItemStack(e.stack().getItem()));
        }
        FateNet.send(p, FateNet.S2C_ARSENAL, buf);
    }

    public static void clear() {
        RhoAias.clear();
        Projection.clear();
        UbwEntity.clearAll();
        CraneWing.clear();
        TwinBlades.clear();
    }
}
