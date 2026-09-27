package cn.blockforge.fatekings.client;

import cn.blockforge.fatekings.client.render.ChainRenderer;
import cn.blockforge.fatekings.client.render.EnumaElishRenderer;
import cn.blockforge.fatekings.client.render.ExcaliburWaveRenderer;
import cn.blockforge.fatekings.client.render.GatePortalRenderer;
import cn.blockforge.fatekings.client.render.KingNpcRenderer;
import cn.blockforge.fatekings.client.render.StrikeAirRenderer;
import cn.blockforge.fatekings.client.render.TreasureRenderer;
import cn.blockforge.fatekings.client.render.VimanaRenderer;
import cn.blockforge.fatekings.FateKings;
import cn.blockforge.fatekings.entity.VimanaEntity;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.Kings;
import cn.blockforge.fatekings.net.FateNet;
import cn.blockforge.fatekings.registry.FateEntities;
import com.mojang.brigadier.arguments.BoolArgumentType;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Client entry: renderers, packets, HUD, world effects, the knight's leap and the Vimana controls. */
public final class FateClient {
    private static ClientPrefs prefs;
    private static boolean jumpWasDown;
    /** Ticks since the local knight's last leap (-1: not leaping). */
    private static int leapTicks = -1;

    private FateClient() {
    }

    public static ClientPrefs prefs() {
        if (prefs == null) prefs = new ClientPrefs(FabricLoader.getInstance().getConfigDir().resolve("fatekings-client.json"));
        return prefs;
    }

    public static void init() {
        prefs();
        Kings.clientKing = () -> ClientKingState.king;
        Kings.clientIsLocal = e -> e == Minecraft.getInstance().player;
        VimanaEntity.clientInput = () -> {
            var p = Minecraft.getInstance().player;
            if (p == null) return new float[4];
            var keys = p.input.keyPresses;
            float fwd = (keys.forward() ? 1.0f : 0.0f) - (keys.backward() ? 1.0f : 0.0f);
            float strafe = (keys.left() ? 1.0f : 0.0f) - (keys.right() ? 1.0f : 0.0f);
            return new float[]{fwd, strafe, keys.jump() ? 1.0f : 0.0f, 0.0f};
        };

        EntityRendererRegistry.register(FateEntities.TREASURE, TreasureRenderer::new);
        EntityRendererRegistry.register(FateEntities.GATE_PORTAL, GatePortalRenderer::new);
        EntityRendererRegistry.register(FateEntities.CHAIN, ChainRenderer::new);
        EntityRendererRegistry.register(FateEntities.STRIKE_AIR, StrikeAirRenderer::new);
        EntityRendererRegistry.register(FateEntities.ENUMA_ELISH, EnumaElishRenderer::new);
        EntityRendererRegistry.register(FateEntities.EXCALIBUR_WAVE, ExcaliburWaveRenderer::new);
        EntityRendererRegistry.register(FateEntities.VIMANA, VimanaRenderer::new);
        EntityRendererRegistry.register(FateEntities.SWORD_QI, cn.blockforge.fatekings.client.render.SwordQiRenderer::new);
        EntityRendererRegistry.register(FateEntities.GILGAMESH, ctx -> new KingNpcRenderer<>(ctx, ModelLayers.PLAYER, FateKings.id("textures/entity/gilgamesh.png")));
        EntityRendererRegistry.register(FateEntities.ARTORIA, ctx -> new KingNpcRenderer<>(ctx, ModelLayers.PLAYER_SLIM, FateKings.id("textures/entity/artoria.png")));

        receive(FateNet.S2C_STATE, ClientKingState::read);
        receive(FateNet.S2C_FX, WorldFx::receive);
        receive(FateNet.S2C_SIDES, FateHud::readSides);
        receive(FateNet.S2C_WARN, FateHud::readWarning);
        receive(FateNet.S2C_SWING, buf -> ClientSwings.put(buf.readVarInt(), buf.readFloat()));

        FateHud.init();
        WorldFx.init();
        ClientTickEvents.START_CLIENT_TICK.register(FateClient::leapInput);
        ClientTickEvents.END_CLIENT_TICK.register(FateClient::leapGlide);
        ClientTickEvents.END_CLIENT_TICK.register(SlashInput::tick);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> ClientSwings.clear());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(ClientCommands.literal("fateclient")
            .then(option("hud", "hudVisible"))
            .then(option("lowfx", "lowFx"))
            .then(option("reduceshake", "reduceShake"))
            .then(option("noflash", "noFlash"))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> option(String name, String key) {
        return ClientCommands.literal(name).then(ClientCommands.argument("on", BoolArgumentType.bool()).executes(ctx -> {
            boolean on = BoolArgumentType.getBool(ctx, "on");
            prefs().set(key, on);
            ctx.getSource().sendFeedback(Component.translatable("fatekings.cmd.client_option", name, String.valueOf(on)));
            return 1;
        }));
    }

    private interface Reader {
        void read(FriendlyByteBuf buf);
    }

    private static void receive(Identifier id, Reader reader) {
        ClientPlayNetworking.registerGlobalReceiver(FateNet.type(id), (payload, context) -> {
            byte[] data = payload.data();
            context.client().execute(() -> {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
                try {
                    reader.read(buf);
                } finally {
                    buf.release();
                }
            });
        });
    }

    /**
     * The King of Knights leaps on jump (Mana Burst), like Sukuna's far bound: straight ahead along the
     * view, or towards the held movement keys. Sneak + jump stays an ordinary jump.
     */
    private static void leapInput(Minecraft mc) {
        var p = mc.player;
        boolean down = mc.options.keyJump.isDown();
        boolean edge = down && !jumpWasDown;
        jumpWasDown = down;
        if (!edge || p == null || ClientKingState.king != KingRules.KNIGHT || mc.gui.screen() != null) return;
        // Standing on the water surface counts as ground (the Lady of the Lake's blessing).
        boolean swimming = (p.isInWater() || p.isInLava()) && !p.onGround();
        if (p.isShiftKeyDown() || p.isPassenger() || p.getAbilities().flying || swimming || p.onClimbable()
            || p.isFallFlying() || p.isUsingItem() || p.isSpectator()) {
            return;
        }
        if (!ClientPlayNetworking.canSend(FateNet.C2S_LEAP)) return;
        var move = p.input.moveVector;
        FriendlyByteBuf buf = FateNet.buffer();
        buf.writeFloat(move.y);
        buf.writeFloat(move.x);
        buf.writeBoolean(p.onGround());
        ClientPlayNetworking.send(FateNet.payload(FateNet.C2S_LEAP, buf));
        leapTicks = 0;
    }

    /** Winston-like hang time: while a leap is in the air its fall is softened (gravity 0.08 -> ~0.05). */
    private static void leapGlide(Minecraft mc) {
        var p = mc.player;
        if (leapTicks < 0 || p == null) return;
        ++leapTicks;
        if (leapTicks > 3 && (p.onGround() || p.isInWater()) || leapTicks > 200 || ClientKingState.king != KingRules.KNIGHT) {
            leapTicks = -1;
            return;
        }
        var v = p.getDeltaMovement();
        if (leapTicks > 3 && v.y < 0.15) p.setDeltaMovement(v.x, v.y + 0.03, v.z);
    }
}
