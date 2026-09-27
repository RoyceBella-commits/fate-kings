package cn.blockforge.fatekings.client;

import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.knight.Slashes;
import cn.blockforge.fatekings.net.FateNet;
import cn.blockforge.fatekings.registry.FateItems;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * The angle of a knight's Excalibur stroke. It follows the mouse: a sweep to the right cuts left to
 * right, pulling down chops down, a diagonal drag cuts diagonally (8 directions). With the mouse
 * still, strokes follow the combo: sweep, backhand, down-right, rising, straight down.
 */
public final class SlashInput {
    private static final int HISTORY = 4;
    private static final float[] DYAW = new float[HISTORY];
    private static final float[] DPITCH = new float[HISTORY];
    private static int cursor;
    private static float lastYaw = Float.NaN;
    private static float lastPitch;
    private static int combo;
    private static long lastComboAt;

    private SlashInput() {
    }

    /** Called every client tick: remembers how the view moved lately. */
    public static void tick(Minecraft mc) {
        var p = mc.player;
        if (p == null) {
            lastYaw = Float.NaN;
            return;
        }
        float yaw = p.getYRot(), pitch = p.getXRot();
        if (!Float.isNaN(lastYaw)) {
            DYAW[cursor] = yaw - lastYaw;
            DPITCH[cursor] = pitch - lastPitch;
            cursor = (cursor + 1) % HISTORY;
        }
        lastYaw = yaw;
        lastPitch = pitch;
    }

    /** Called when the attack key starts an attack. */
    public static void onAttack(Minecraft mc) {
        var p = mc.player;
        if (p == null || ClientKingState.king != KingRules.KNIGHT || !p.getMainHandItem().is(FateItems.EXCALIBUR) || p.isUsingItem()) return;
        float dx = 0.0f, dy = 0.0f;
        for (int i = 0; i < HISTORY; ++i) {
            dx += DYAW[i];
            dy -= DPITCH[i]; // looking down (pitch up) moves the blade down the screen
        }
        float roll;
        long now = System.currentTimeMillis();
        if (dx * dx + dy * dy > 16.0f) {
            // Quantised to 8 directions (every 45 degrees).
            roll = Math.round((float)Math.toDegrees(Math.atan2(dy, dx)) / 45.0f) * 45.0f;
        } else {
            if (now - lastComboAt > 1500L) combo = 0;
            roll = Slashes.COMBO[combo % Slashes.COMBO.length];
            combo = (combo + 1) % Slashes.COMBO.length;
        }
        lastComboAt = now;
        ClientSwings.put(p.getId(), roll);
        if (!ClientPlayNetworking.canSend(FateNet.C2S_SLASH)) return;
        var buf = FateNet.buffer();
        buf.writeFloat(roll);
        buf.writeByte(combo);
        ClientPlayNetworking.send(FateNet.payload(FateNet.C2S_SLASH, buf));
    }
}
