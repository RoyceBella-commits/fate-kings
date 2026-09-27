package cn.blockforge.fatekings.client;

import cn.blockforge.fatekings.FateKings;
import cn.blockforge.fatekings.client.render.FxDraw;
import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.combat.JudgementRules.Side;
import cn.blockforge.fatekings.compat.JjkCompat;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.Skills;
import cn.blockforge.fatekings.npc.ArtoriaEntity;
import cn.blockforge.fatekings.npc.GilgameshEntity;
import cn.blockforge.fatekings.registry.FateEffects;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** The kings' HUD: status panel, gold hearts, Sha Naqba Imuru, Instinct warnings and screen effects. */
public final class FateHud {
    private static final Identifier HEART_CONTAINER = Identifier.withDefaultNamespace("hud/heart/container");
    private static final Identifier GOLD_FULL = Identifier.withDefaultNamespace("hud/heart/absorbing_full");
    private static final Identifier GOLD_HALF = Identifier.withDefaultNamespace("hud/heart/absorbing_half");
    private static final Identifier GOLD_FULL_BLINK = Identifier.withDefaultNamespace("hud/heart/absorbing_full_blinking");
    private static final Identifier GOLD_HALF_BLINK = Identifier.withDefaultNamespace("hud/heart/absorbing_half_blinking");
    private static final Map<Integer, Side> PLAYER_SIDES = new HashMap<>();
    private static final List<Warning> WARNINGS = new ArrayList<>();
    private static String titleKey;
    private static int titleColor;
    private static long titleUntil;
    private static long titleStart;

    private record Warning(int entityId, Vec3 pos, String key, long until) {
    }

    private FateHud() {
    }

    public static void init() {
        HudElementRegistry.addLast(FateKings.id("hud"), (ctx, tracker) -> render(ctx, tracker.getGameTimeDeltaPartialTick(false)));
        HudElementRegistry.attachElementAfter(VanillaHudElements.HEALTH_BAR, FateKings.id("king_gold"), (ctx, tracker) -> renderGold(ctx));
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> {
            PLAYER_SIDES.clear();
            WARNINGS.clear();
            ClientKingState.reset();
        });
    }

    public static void title(String key, int color, int ticks) {
        titleKey = key;
        titleColor = color;
        titleStart = System.currentTimeMillis();
        titleUntil = titleStart + ticks * 50L;
    }

    public static void readSides(FriendlyByteBuf buf) {
        PLAYER_SIDES.clear();
        int n = buf.readVarInt();
        for (int i = 0; i < n; ++i) {
            int id = buf.readVarInt();
            int side = buf.readByte();
            if (side >= 0 && side < Side.values().length) PLAYER_SIDES.put(id, Side.values()[side]);
        }
    }

    public static void readWarning(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        Vec3 pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        String key = buf.readUtf(48);
        int duration = buf.readVarInt();
        WARNINGS.removeIf(w -> w.entityId == id && w.key.equals(key));
        WARNINGS.add(new Warning(id, pos, key, System.currentTimeMillis() + duration * 50L));
    }

    // ---- Gold hearts ----

    private static void renderGold(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.gui.hud.isHidden() || ClientKingState.king == KingRules.NONE || ClientKingState.goldMax <= 0.0f
            || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) {
            return;
        }
        int xLeft = ctx.guiWidth() / 2 - 91;
        int yLineBase = ctx.guiHeight() - 39;
        float maxHealth = Math.max((float)player.getAttributeValue(Attributes.MAX_HEALTH), player.getHealth());
        int absorption = Mth.ceil(player.getAbsorptionAmount());
        int healthRows = Mth.ceil((maxHealth + absorption) / 2.0f / 10.0f);
        int healthRowHeight = Math.max(10 - (healthRows - 2), 3);
        int top = yLineBase - (healthRows - 1) * healthRowHeight - 10;
        if (player.getArmorValue() > 0) top -= 10;
        top -= 10 * extraRowsFromOtherMods();
        int hearts = Mth.ceil(ClientKingState.goldMax / 2.0f);
        int rows = Mth.ceil(hearts / 10.0f);
        int rowStep = rows <= 1 ? 10 : Math.max(4, 10 - (rows - 1) * 3);
        int gold = Mth.ceil(ClientKingState.gold);
        long since = (System.nanoTime() - ClientKingState.goldChangedAt) / 1_000_000L;
        boolean blink = since < 400L && (since / 100L) % 2L == 0L;
        for (int i = hearts - 1; i >= 0; --i) {
            int row = i / 10;
            int x = xLeft + (i % 10) * 8;
            int y = top - row * rowStep;
            ctx.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_CONTAINER, x, y, 9, 9);
            if (i * 2 + 1 < gold) ctx.blitSprite(RenderPipelines.GUI_TEXTURED, blink ? GOLD_FULL_BLINK : GOLD_FULL, x, y, 9, 9);
            else if (i * 2 + 1 == gold) ctx.blitSprite(RenderPipelines.GUI_TEXTURED, blink ? GOLD_HALF_BLINK : GOLD_HALF, x, y, 9, 9);
        }
        ctx.text(mc.font, gold + "/" + (int)ClientKingState.goldMax, xLeft + 83, top - (rows - 1) * rowStep + 1, 0xFFFFD34A);
    }

    /** The Gojo x Sukuna mod draws its own gold row in the same place; ours goes above it. */
    private static int extraRowsFromOtherMods() {
        return JjkCompat.LOADED && ClientKingState.goldMax < KingRules.GOLD_HP - 0.5f ? 1 : 0;
    }

    // ---- Everything else ----

    private static void render(GuiGraphicsExtractor ctx, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        int sw = ctx.guiWidth(), sh = ctx.guiHeight();
        screenEffects(ctx, mc, sw, sh, partial);
        if (mc.gui.hud.isHidden()) return;
        renderTitle(ctx, mc, sw, sh);
        if (ClientKingState.king == KingRules.HERO) targetPanel(ctx, mc, sw, sh);
        if (ClientKingState.king == KingRules.KNIGHT) warnings(ctx, mc, sw, sh, partial);
        if (ClientKingState.king != KingRules.NONE && FateClient.prefs().hudVisible() && mc.gui.screen() == null) panel(ctx, mc, sw, sh);
        else if (ClientKingState.lockLeft > 0 && ClientKingState.left(ClientKingState.lockLeft) > 0) {
            ctx.centeredText(mc.font, Component.translatable("fatekings.hud.swap_lock",
                String.format(Locale.ROOT, "%.1f", ClientKingState.left(ClientKingState.lockLeft) / 20.0f)), sw / 2, sh - 70, 0xFFFFB0B0);
        }
    }

    private static void screenEffects(GuiGraphicsExtractor ctx, Minecraft mc, int sw, int sh, float partial) {
        ClientPrefs prefs = FateClient.prefs();
        float dim = prefs.lowFx() ? 0.0f : WorldFx.skyDim(mc.level, partial);
        if (dim > 0.01f) ctx.fill(0, 0, sw, sh, FxDraw.alpha(dim * 0.55f, 0x05030A));
        WorldFx.Event split = WorldFx.latest(Fx.SKY_SPLIT);
        if (split != null && !prefs.lowFx() && split.progress(mc.level, partial) < 1.0f) {
            float a = 0.22f * Math.min(1.0f, (1.0f - split.progress(mc.level, partial)) * 4.0f);
            ctx.fillGradient(0, 0, sw / 2, sh, FxDraw.alpha(a, 0x7A0A12), FxDraw.alpha(a * 0.6f, 0x1A0A0E));
            ctx.fillGradient(sw / 2, 0, sw, sh, FxDraw.alpha(a, 0xFFD34A), FxDraw.alpha(a * 0.6f, 0xFFF7DA));
        }
        if (!prefs.noFlash()) {
            float red = WorldFx.flash(mc.level, partial, Fx.FLASH);
            if (red > 0.01f) ctx.fill(0, 0, sw, sh, FxDraw.alpha(red * 0.6f, 0xFFE0E0));
            float gold = WorldFx.flash(mc.level, partial, Fx.GOLD_FLASH);
            if (gold > 0.01f) ctx.fill(0, 0, sw, sh, FxDraw.alpha(gold * 0.6f, 0xFFF7DA));
        }
        WorldFx.Event cracks = WorldFx.latest(Fx.CRACKS);
        if (cracks != null && mc.player.hasEffect(FateEffects.EXCALIBUR_WOUND)) {
            float p = cracks.progress(mc.level, partial);
            float a = p < 0.85f ? 1.0f : (1.0f - p) / 0.15f;
            ctx.blit(RenderPipelines.GUI_TEXTURED, FxDraw.CRACKS, 0, 0, 0.0f, 0.0f, sw, sh, 256, 256, 256, 256, FxDraw.alpha(a * 0.9f, 0xFFFFFF));
        }
    }

    private static void renderTitle(GuiGraphicsExtractor ctx, Minecraft mc, int sw, int sh) {
        long now = System.currentTimeMillis();
        if (titleKey == null || now > titleUntil) return;
        float t = (now - titleStart) / (float)(titleUntil - titleStart);
        float a = t < 0.15f ? t / 0.15f : t > 0.75f ? (1.0f - t) / 0.25f : 1.0f;
        var pose = ctx.pose();
        pose.pushMatrix();
        pose.translate(sw / 2.0f, sh * 0.22f);
        pose.scale(2.2f, 2.2f);
        ctx.centeredText(mc.font, Component.translatable(titleKey), 0, 0, FxDraw.alpha(Math.max(0.05f, a), titleColor & 0xFFFFFF));
        pose.popMatrix();
    }

    private static void panel(GuiGraphicsExtractor ctx, Minecraft mc, int sw, int sh) {
        boolean hero = ClientKingState.king == KingRules.HERO;
        int accent = hero ? 0xFFFFD34A : 0xFF7FB2FF;
        String[] rows = hero ? Skills.HERO_HUD : Skills.KNIGHT_HUD;
        List<Component> extra = new ArrayList<>();
        if (hero && ClientKingState.left(ClientKingState.reorgLeft) > 0) extra.add(window("fatekings.hud.reorg", ClientKingState.reorgLeft));
        if (!hero && ClientKingState.left(ClientKingState.depletionLeft) > 0) extra.add(window("fatekings.hud.depletion", ClientKingState.depletionLeft));
        if (!hero && ClientKingState.left(ClientKingState.counterLeft) > 0) extra.add(window("fatekings.hud.counter", ClientKingState.counterLeft));
        if (!hero && ClientKingState.left(ClientKingState.domeLeft) > 0) extra.add(window("fatekings.hud.dome", ClientKingState.domeLeft));
        if (!hero && ClientKingState.left(ClientKingState.regenPausedLeft) > 0) extra.add(window("fatekings.hud.regen_paused", ClientKingState.regenPausedLeft));
        int width = 150;
        int height = 18 + rows.length * 10 + extra.size() * 10 + 4;
        int x = sw - width - 6;
        int y = Math.max(6, sh / 2 - height / 2 - 20);
        ctx.fill(x, y, x + width, y + height, 0xC80E0B15);
        ctx.fill(x, y, x + width, y + 1, accent);
        ctx.fill(x, y + 1, x + 2, y + height - 1, accent & 0x90FFFFFF);
        ctx.text(mc.font, Component.translatable(hero ? "fatekings.hud.hero" : "fatekings.hud.knight"), x + 7, y + 5, accent);
        int ry = y + 18;
        for (String key : rows) {
            float left = ClientKingState.cooldown(key);
            String name = Component.translatable("fatekings.skill." + key).getString();
            ctx.text(mc.font, mc.font.plainSubstrByWidth(name, 90), x + 7, ry, 0xFFE8E4F0);
            String status = left > 1.0f ? String.format(Locale.ROOT, "%.1fs", left / 20.0f) : Component.translatable("fatekings.hud.ready").getString();
            ctx.text(mc.font, status, x + width - 6 - mc.font.width(status), ry, left > 1.0f ? 0xFFB0A8C0 : 0xFF8CFFB0);
            ry += 10;
        }
        for (Component c : extra) {
            ctx.text(mc.font, c, x + 7, ry, 0xFFFFC080);
            ry += 10;
        }
    }

    private static Component window(String key, int ticks) {
        return Component.translatable(key, String.format(Locale.ROOT, "%.1f", ClientKingState.left(ticks) / 20.0f));
    }

    // ---- Sha Naqba Imuru ----

    private static void targetPanel(GuiGraphicsExtractor ctx, Minecraft mc, int sw, int sh) {
        Entity camera = mc.getCameraEntity();
        if (camera == null) return;
        Vec3 eye = camera.getEyePosition(1.0f);
        Vec3 look = camera.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(64.0));
        BlockHitResult block = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, camera));
        Vec3 limit = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(mc.level, camera, eye, limit, new AABB(eye, limit).inflate(1.0),
            e -> e instanceof LivingEntity l && l.isAlive() && !e.isSpectator() && e != camera, 0.3f);
        if (hit == null || !(hit.getEntity() instanceof LivingEntity target)) return;
        Side side = sideOf(target);
        int x = sw / 2 + 12, y = sh / 2 + 10;
        String hp = String.format(Locale.ROOT, "%.0f / %.0f", target.getHealth(), target.getMaxHealth());
        Component name = target.getDisplayName();
        Component line2 = Component.translatable("fatekings.hud.target", hp, target.getArmorValue());
        Component line3 = Component.translatable("fatekings.side." + side.name().toLowerCase(Locale.ROOT));
        int w = Math.max(mc.font.width(name), Math.max(mc.font.width(line2), mc.font.width(line3))) + 10;
        ctx.fill(x, y, x + w, y + 34, 0xB0100808);
        ctx.fill(x, y, x + 1, y + 34, 0xFFFFD34A);
        ctx.text(mc.font, name, x + 5, y + 3, 0xFFFFE8A0);
        ctx.text(mc.font, line2, x + 5, y + 13, 0xFFE0D8C8);
        ctx.text(mc.font, line3, x + 5, y + 23, sideColor(side));
    }

    /** Client view of a side (players from the server's list, everyone else from the type). */
    private static Side sideOf(LivingEntity e) {
        if (e instanceof Player) return PLAYER_SIDES.getOrDefault(e.getId(), Side.PLAYER);
        if (e instanceof GilgameshEntity) return Side.HERO;
        if (e instanceof ArtoriaEntity) return Side.KNIGHT;
        if (JjkCompat.is(e, JjkCompat.GOJO)) return Side.GOJO;
        if (JjkCompat.is(e, JjkCompat.SUKUNA)) return Side.SUKUNA;
        if (JjkCompat.is(e, JjkCompat.MAHORAGA)) return Side.MAHORAGA;
        if ("minecraft".equals(JjkCompat.typeId(e).getNamespace())) {
            boolean boss = e instanceof EnderDragon || e instanceof WitherBoss || e instanceof Warden || e instanceof ElderGuardian
                || e.getType().builtInRegistryHolder().is(ConventionalEntityTypeTags.BOSSES);
            return boss ? Side.VANILLA_BOSS : Side.VANILLA;
        }
        return Side.OTHER_MOD;
    }

    private static int sideColor(Side s) {
        return switch (s) {
            case GOJO -> 0xFF7FD8FF;
            case SUKUNA, MAHORAGA -> 0xFFE0303F;
            case HERO -> 0xFFFFD34A;
            case KNIGHT -> 0xFF7FB2FF;
            case VANILLA_BOSS -> 0xFFD080FF;
            default -> 0xFFB8B8B8;
        };
    }

    // ---- Instinct ----

    private static void warnings(GuiGraphicsExtractor ctx, Minecraft mc, int sw, int sh, float partial) {
        long now = System.currentTimeMillis();
        WARNINGS.removeIf(w -> now > w.until);
        if (WARNINGS.isEmpty()) return;
        Entity camera = mc.getCameraEntity();
        if (camera == null) return;
        int row = 0;
        for (Warning w : WARNINGS) {
            Entity src = mc.level.getEntity(w.entityId);
            Vec3 at = src != null ? src.getPosition(partial).add(0.0, src.getBbHeight() * 0.5, 0.0) : w.pos;
            Vec3 d = at.subtract(camera.getEyePosition(partial));
            double yaw = Math.toDegrees(Math.atan2(-d.x, d.z)) - camera.getYRot(partial);
            double rel = Math.toRadians(Mth.wrapDegrees(yaw));
            int cx = sw / 2, cy = sh / 2;
            int ax = cx + (int)(Math.sin(-rel) * (sw * 0.42));
            int ay = cy - (int)(Math.cos(rel) * (sh * 0.40));
            boolean blink = (now / 250) % 2 == 0;
            int col = blink ? 0xFFFFD34A : 0xFFFFF0B0;
            // A small arrow head pointing out to the source.
            for (int i = 0; i < 6; ++i) {
                int ox = (int)(Math.sin(-rel) * i), oy = -(int)(Math.cos(rel) * i);
                ctx.fill(ax + ox - (6 - i) / 2, ay + oy - (6 - i) / 2, ax + ox + (6 - i) / 2 + 1, ay + oy + (6 - i) / 2 + 1, col);
            }
            Component label = Component.translatable(w.key);
            ctx.centeredText(mc.font, label, cx, 40 + row * 11, col);
            ++row;
        }
    }
}
