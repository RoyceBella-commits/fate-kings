package cn.blockforge.fatekings.king;

import cn.blockforge.fatekings.combat.Sides;
import cn.blockforge.fatekings.net.FateNet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;

/** Sides of the players near a King of Heroes, for Sha Naqba Imuru (routes are only known on the server). */
public final class SideSync {
    private SideSync() {
    }

    public static void send(ServerPlayer hero) {
        List<ServerPlayer> near = new ArrayList<>();
        for (ServerPlayer p : hero.level().players()) {
            if (p != hero && p.distanceToSqr(hero) < 128.0 * 128.0 && near.size() < 200) near.add(p);
        }
        var buf = FateNet.buffer();
        buf.writeVarInt(near.size());
        for (ServerPlayer p : near) {
            buf.writeVarInt(p.getId());
            buf.writeByte(Sides.side(p).ordinal());
        }
        FateNet.send(hero, FateNet.S2C_SIDES, buf);
    }
}
