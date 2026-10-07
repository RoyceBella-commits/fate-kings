package cn.blockforge.fatekings.client;

import cn.blockforge.fatekings.archer.ArcherRules;
import cn.blockforge.fatekings.net.FateNet;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The Hill of Swords: every weapon EMIYA has analysed, in a 9 x 3 grid. Left click projects it into
 * the hotbar; right click forgets it.
 */
public class ArsenalScreen extends Screen {
    private static final int COLS = 9;
    private static final int CELL = 20;
    private int left;
    private int top;

    public ArsenalScreen() {
        super(Component.translatable("fatekings.screen.arsenal"));
    }

    @Override
    protected void init() {
        super.init();
        int w = COLS * CELL + 12;
        this.left = (this.width - w) / 2 + 6;
        this.top = this.height / 2 - 40;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int cellAt(double mx, double my) {
        int col = (int)Math.floor((mx - this.left) / CELL), row = (int)Math.floor((my - this.top) / CELL);
        if (col < 0 || col >= COLS || row < 0 || mx < this.left || my < this.top) return -1;
        int i = row * COLS + col;
        return i < ClientArsenal.ENTRIES.size() && i < ArcherRules.ARSENAL_CAP ? i : -1;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float partial) {
        super.extractRenderState(ctx, mx, my, partial);
        int rows = (ArcherRules.ARSENAL_CAP + COLS - 1) / COLS;
        int x0 = this.left - 6, y0 = this.top - 22, x1 = this.left + COLS * CELL + 6, y1 = this.top + rows * CELL + 22;
        ctx.fillGradient(x0, y0, x1, y1, 0xE0401808, 0xE0100604);
        ctx.fill(x0, y0, x1, y0 + 1, 0xFFFF6A4A);
        ctx.text(this.font, Component.translatable("fatekings.screen.arsenal.title", ClientArsenal.ENTRIES.size(), ArcherRules.ARSENAL_CAP), x0 + 6, y0 + 6,
            0xFFFFD0A0);
        int hover = cellAt(mx, my);
        for (int i = 0; i < ArcherRules.ARSENAL_CAP; ++i) {
            int cx = this.left + (i % COLS) * CELL, cy = this.top + (i / COLS) * CELL;
            int bg = i == hover ? 0x90FFB060 : i == ClientArsenal.selected ? 0x70FF6A4A : 0x50000000;
            ctx.fill(cx, cy, cx + CELL - 2, cy + CELL - 2, bg);
            if (i < ClientArsenal.ENTRIES.size()) {
                ItemStack s = ClientArsenal.ENTRIES.get(i);
                ctx.item(s, cx + 1, cy + 1);
                ctx.itemDecorations(this.font, s, cx + 1, cy + 1);
            }
        }
        ctx.centeredText(this.font, Component.translatable("fatekings.screen.arsenal.hint"), this.width / 2, y1 - 12, 0xFFB0A090);
        if (hover >= 0) ctx.setTooltipForNextFrame(this.font, ClientArsenal.ENTRIES.get(hover), mx, my);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        int i = cellAt(event.x(), event.y());
        if (i >= 0 && (event.button() == 0 || event.button() == 1)) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeVarInt(i);
            buf.writeByte(event.button() == 0 ? 0 : 1);
            ClientPlayNetworking.send(FateNet.payload(FateNet.C2S_PROJECT, buf));
            if (event.button() == 0) this.onClose();
            return true;
        }
        return super.mouseClicked(event, doubled);
    }
}
