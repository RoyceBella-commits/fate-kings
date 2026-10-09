package cn.blockforge.fatekings.king;

import cn.blockforge.fatekings.net.FateNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * The king state a client needs: HUD panel, gold hearts, leap / flight input. Written here and
 * read by {@code ClientKingState} in the same field order.
 */
public final class KingSync {
    /** Every cooldown key the HUD can show, by index. */
    public static final String[] KEYS = {Skills.GOB_TAP, Skills.GOB_VOLLEY, Skills.GOB_RING, Skills.BAB_ILU, Skills.ENKIDU_HOOK,
        Skills.ENKIDU_BIND, Skills.VIMANA, Skills.ELIXIR, Skills.AUTODEFENDER, Skills.STRIKE_AIR, Skills.MANA_BURST,
        Skills.EXCALIBUR, Skills.WARHORSE, Skills.AVALON_LETHAL, Skills.AVALON_DOME, Skills.BOW_TAP, Skills.CALADBOLG, Skills.RHO_AIAS,
        Skills.TWIN_THROW, Skills.CRANE_WING, Skills.TRACE, Skills.UBW, Skills.EXCALIBUR_REPLICA, Skills.BOW_TRIPLE};

    private KingSync() {
    }

    public static void send(ServerPlayer p, KingState s, long now) {
        s.dirty = false;
        FriendlyByteBuf buf = FateNet.buffer();
        buf.writeByte(s.king);
        buf.writeVarInt((int)Math.max(0L, s.lockUntil - now));
        buf.writeByte(s.lockedFrom);
        buf.writeFloat(Math.max(0.0f, s.gold));
        buf.writeFloat(Kings.goldTarget(p));
        buf.writeVarInt(left(s.reorgUntil, now));
        buf.writeVarInt(left(s.depletionUntil, now));
        buf.writeVarInt(left(s.revealedUntil, now));
        buf.writeVarInt(left(s.counterUntil, now));
        buf.writeVarInt(left(s.domeUntil, now));
        buf.writeVarInt(left(s.regenPausedUntil, now));
        buf.writeVarInt(KEYS.length);
        for (String key : KEYS) buf.writeVarInt(s.cooldownLeft(key, now));
        // The Archer's windows (appended; ClientKingState reads them in this order).
        buf.writeVarInt(cn.blockforge.fatekings.archer.Archer.ubwLeft(p));
        buf.writeByte(cn.blockforge.fatekings.archer.Archer.rhoPetals(p));
        buf.writeVarInt(cn.blockforge.fatekings.archer.Archer.rhoLeft(p));
        buf.writeByte(cn.blockforge.fatekings.archer.Archer.projected(p));
        FateNet.send(p, FateNet.S2C_STATE, buf);
    }

    private static int left(long until, long now) {
        return (int)Math.max(0L, Math.min(Integer.MAX_VALUE, until - now));
    }
}
