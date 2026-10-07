package cn.blockforge.fatekings.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/** The local Archer's Hill of Swords, as last sent by the server. */
public final class ClientArsenal {
    public static final List<ItemStack> ENTRIES = new ArrayList<>();
    public static int selected = -1;

    private ClientArsenal() {
    }

    public static void read(RegistryFriendlyByteBuf buf) {
        int version = buf.readVarInt();
        if (version != 1) return;
        selected = buf.readVarInt();
        int n = buf.readVarInt();
        ENTRIES.clear();
        for (int i = 0; i < n; ++i) ENTRIES.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
    }

    public static void reset() {
        ENTRIES.clear();
        selected = -1;
    }
}
