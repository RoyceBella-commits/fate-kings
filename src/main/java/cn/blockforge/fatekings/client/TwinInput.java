package cn.blockforge.fatekings.client;

import cn.blockforge.fatekings.archer.ArcherRules;
import cn.blockforge.fatekings.archer.TwinBlades;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.net.FateNet;
import cn.blockforge.fatekings.registry.FateItems;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

/** Each attack press with Kanshou in hand: the next stroke of the six (from the first after a pause). */
public final class TwinInput {
    private static int step = -1;
    private static long lastAt;

    private TwinInput() {
    }

    public static void onAttack(Minecraft mc) {
        var p = mc.player;
        if (p == null || ClientKingState.king != KingRules.ARCHER || !FateItems.twinSword(p.getMainHandItem()) || p.isUsingItem()) return;
        long now = System.currentTimeMillis();
        if (now - lastAt < ArcherRules.TWIN_GAP * 50L) return;
        step = ArcherRules.nextStep(step, now - lastAt);
        lastAt = now;
        ClientSwings.put(p.getId(), TwinBlades.STYLE_TWIN, 0.0f, step);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeByte(step);
        ClientPlayNetworking.send(FateNet.payload(FateNet.C2S_TWIN, buf));
    }
}
