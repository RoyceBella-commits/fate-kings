package cn.blockforge.fatekings.gametest;

import cn.blockforge.fatekings.archer.Projection;
import cn.blockforge.fatekings.client.ArsenalScreen;
import cn.blockforge.fatekings.client.ClientArsenal;
import cn.blockforge.fatekings.client.ClientKingState;
import cn.blockforge.fatekings.client.UbwClient;
import cn.blockforge.fatekings.entity.UbwEntity;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.registry.FateItems;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * A scripted session in a real client: both armour sets, the swap lock, flight, the knight's leap,
 * every noble phantasm, the NPCs and a clash. Screenshots go to the run folder for review.
 */
public class FateClientTests implements FabricClientGameTest {
    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError("FAILED: " + what);
        System.out.println("[FateClientTests] OK " + what);
    }

    private static void camera(ClientGameTestContext ctx, CameraType type) {
        ctx.runOnClient(mc -> mc.options.setCameraType(type));
    }

    private static void equip(TestServerContext server, String head, String chest, String legs, String feet) {
        server.runCommand("item replace entity @p armor.head with " + head);
        server.runCommand("item replace entity @p armor.chest with " + chest);
        server.runCommand("item replace entity @p armor.legs with " + legs);
        server.runCommand("item replace entity @p armor.feet with " + feet);
    }

    private static void hand(TestServerContext server, String item) {
        server.runCommand("item replace entity @p weapon.mainhand with " + item);
    }

    private static Vec3 pos(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> mc.player.position());
    }

    private static void shot(ClientGameTestContext ctx, String name) {
        ctx.takeScreenshot("fate_" + name);
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("weather clear");
            server.runCommand("gamemode survival @p");
            server.runCommand("difficulty peaceful");
            server.runCommand("fate terrain off");
            ctx.getInput().lookAt(0.0f, 10.0f);
            ctx.waitTicks(20);

            // --- The holy sword outside a knight's hand: revealed ---
            hand(server, "fatekings:excalibur");
            server.runCommand("item replace entity @p hotbar.1 with fatekings:gate_of_babylon");
            server.runCommand("item replace entity @p hotbar.2 with fatekings:bab_ilu");
            server.runCommand("item replace entity @p hotbar.3 with fatekings:enkidu");
            server.runCommand("item replace entity @p hotbar.4 with fatekings:vimana");
            server.runCommand("item replace entity @p hotbar.5 with fatekings:treasury_elixir");
            server.runCommand("item replace entity @p hotbar.6 with fatekings:warhorse");
            server.runCommand("item replace entity @p hotbar.7 with fatekings:gilgamesh_spawn_egg");
            server.runCommand("item replace entity @p hotbar.8 with fatekings:artoria_spawn_egg");
            ctx.waitTicks(10);
            shot(ctx, "01_hotbar_plain");

            // --- King of Knights ---
            equip(server, "fatekings:knight_ribbon", "fatekings:knight_breastplate", "fatekings:knight_skirt", "fatekings:knight_boots");
            ctx.waitFor(mc -> ClientKingState.king == KingRules.KNIGHT, 100);
            check(ClientKingState.king == KingRules.KNIGHT, "full knight set -> King of Knights");
            ctx.waitTicks(15);
            shot(ctx, "02_knight_title_hud");
            ctx.waitTicks(40);
            check(ctx.computeOnClient(mc -> mc.player.getMaxHealth()) >= 79.0f, "40 hearts");
            check(ctx.computeOnClient(mc -> mc.player.getArmorValue()) >= 30, "armor 30");
            shot(ctx, "03_knight_hotbar_air");
            camera(ctx, CameraType.THIRD_PERSON_FRONT);
            ctx.waitTicks(10);
            shot(ctx, "04_knight_front");
            camera(ctx, CameraType.THIRD_PERSON_BACK);

            // Leap: jump alone goes far along the view.
            Vec3 before = pos(ctx);
            ctx.getInput().holdKeyFor(o -> o.keyJump, 3);
            ctx.waitTicks(25);
            Vec3 after = pos(ctx);
            double leap = Math.hypot(after.x - before.x, after.z - before.z);
            check(leap > 8.0, "knight leap covers distance (" + String.format("%.1f", leap) + " blocks)");
            // Leap with a direction key (back).
            before = pos(ctx);
            ctx.getInput().holdKey(o -> o.keyDown);
            ctx.waitTick();
            ctx.getInput().holdKeyFor(o -> o.keyJump, 3);
            ctx.waitTicks(3);
            ctx.getInput().releaseKey(o -> o.keyDown);
            ctx.waitTicks(22);
            after = pos(ctx);
            Vec3 look = ctx.computeOnClient(mc -> mc.player.getViewVector(1.0f));
            double along = (after.x - before.x) * look.x + (after.z - before.z) * look.z;
            check(along < -5.0, "S + jump leaps backwards (" + String.format("%.1f", along) + ")");

            // Strike Air (tap) and the true name (hold, release).
            ctx.getInput().pressKey(o -> o.keyUse);
            ctx.waitTicks(3);
            shot(ctx, "05_strike_air");
            ctx.waitTicks(10);
            server.runCommand("fate cooldowns reset @p");
            ctx.getInput().holdKey(o -> o.keyUse);
            ctx.waitTicks(40);
            shot(ctx, "06_excalibur_gathering_light");
            ctx.getInput().releaseKey(o -> o.keyUse);
            ctx.waitTicks(5);
            shot(ctx, "07_excalibur_wave");
            ctx.waitTicks(12);
            shot(ctx, "08_excalibur_trench");
            check(ClientKingState.left(ClientKingState.depletionLeft) > 0 || ClientKingState.cooldown("excalibur") > 0, "Excalibur released (cooldown / depletion)");
            ctx.waitTicks(20);
            server.runCommand("execute as @p at @s run fate cast avalon");
            ctx.waitTicks(10);
            shot(ctx, "09_avalon_dome");
            ctx.waitTicks(70);

            // --- Swap lock: straight into the golden set is refused for 15 s ---
            equip(server, "fatekings:golden_crown", "fatekings:golden_chestplate", "fatekings:golden_greaves", "fatekings:golden_sabatons");
            ctx.waitTicks(20);
            check(ClientKingState.king == KingRules.NONE, "swap lock keeps the other king away (king " + ClientKingState.king + ", lock "
                + ClientKingState.lockLeft + ", from " + ClientKingState.lockedFrom + ")");
            ctx.waitFor(mc -> ClientKingState.king == KingRules.HERO, 400);
            check(ClientKingState.king == KingRules.HERO, "after 15 s: King of Heroes");
            ctx.waitTicks(15);
            shot(ctx, "10_hero_title");
            camera(ctx, CameraType.THIRD_PERSON_FRONT);
            ctx.waitTicks(10);
            shot(ctx, "11_hero_front");
            camera(ctx, CameraType.THIRD_PERSON_BACK);

            // Flight: double-tap jump (each tap held over a tick so the movement input sees it).
            ctx.getInput().holdKeyFor(o -> o.keyJump, 2);
            ctx.waitTicks(2);
            ctx.getInput().holdKeyFor(o -> o.keyJump, 2);
            ctx.waitTicks(5);
            check(ctx.computeOnClient(mc -> mc.player.getAbilities().flying), "double jump: flying");
            ctx.getInput().holdKeyFor(o -> o.keyJump, 12);
            ctx.waitTicks(5);
            shot(ctx, "12_hero_flying");
            ctx.getInput().holdKeyFor(o -> o.keyJump, 2);
            ctx.waitTicks(2);
            ctx.getInput().holdKeyFor(o -> o.keyJump, 2);
            ctx.waitTicks(40);

            // Gate of Babylon: a volley.
            ctx.getInput().pressKey(o -> o.keyHotbarSlots[1]);
            ctx.waitTicks(5);
            ctx.getInput().holdKey(o -> o.keyUse);
            ctx.waitTicks(65);
            shot(ctx, "13_gob_wall_of_gates");
            ctx.getInput().releaseKey(o -> o.keyUse);
            ctx.waitTicks(7);
            shot(ctx, "14_gob_volley");
            ctx.waitTicks(30);

            // Bab-ilu -> Ea in the main hand; switching away sends it home and the key comes back.
            ctx.getInput().pressKey(o -> o.keyHotbarSlots[2]);
            ctx.waitTicks(5);
            ctx.getInput().lookAt(0.0f, -35.0f);
            ctx.getInput().holdKey(o -> o.keyUse);
            ctx.waitTicks(20);
            shot(ctx, "15_bab_ilu_labyrinth");
            ctx.waitTicks(30);
            ctx.getInput().releaseKey(o -> o.keyUse);
            ctx.getInput().lookAt(0.0f, 5.0f);
            ctx.waitTicks(5);
            check(ctx.computeOnClient(mc -> mc.player.getMainHandItem().is(FateItems.EA)), "Ea drawn into the main hand");
            shot(ctx, "16_ea_in_hand");
            ctx.getInput().pressKey(o -> o.keyHotbarSlots[3]);
            ctx.waitTicks(5);
            ctx.getInput().pressKey(o -> o.keyHotbarSlots[2]);
            ctx.waitTicks(5);
            check(ctx.computeOnClient(mc -> mc.player.getMainHandItem().is(FateItems.BAB_ILU)), "Ea left the hand: the key is back");
            // Draw again (cooldowns reset) and release Enuma Elish.
            server.runCommand("fate cooldowns reset @p");
            ctx.getInput().holdKey(o -> o.keyUse);
            ctx.waitTicks(45);
            ctx.getInput().releaseKey(o -> o.keyUse);
            ctx.waitTicks(3);
            ctx.getInput().holdKey(o -> o.keyUse);
            ctx.waitTicks(65);
            shot(ctx, "17_ea_charging");
            ctx.getInput().releaseKey(o -> o.keyUse);
            ctx.waitTicks(6);
            shot(ctx, "18_enuma_elish");
            ctx.waitTicks(14);
            shot(ctx, "19_enuma_elish_rift");
            check(ClientKingState.left(ClientKingState.reorgLeft) > 0, "treasury reorganising after Ea");
            ctx.waitTicks(60);

            // Enkidu on a zombie in front.
            server.runCommand("difficulty normal");
            server.runCommand("execute as @p at @s run summon minecraft:zombie ^ ^ ^6 {NoAI:1b,PersistenceRequired:1b}");
            ctx.waitTicks(10);
            server.runCommand("fate cooldowns reset @p");
            server.runCommand("execute as @p at @s anchored eyes facing entity @e[type=minecraft:zombie,limit=1,sort=nearest] feet run tp @s ~ ~ ~ ~ ~");
            ctx.waitTicks(5);
            server.runCommand("execute as @p at @s run fate cast enkidu_bind");
            ctx.waitTicks(8);
            shot(ctx, "20_enkidu_bind");
            server.runCommand("kill @e[type=minecraft:zombie]");

            // Vimana.
            ctx.getInput().lookAt(0.0f, 10.0f);
            ctx.getInput().pressKey(o -> o.keyHotbarSlots[4]);
            ctx.waitTicks(5);
            ctx.getInput().pressKey(o -> o.keyUse);
            ctx.waitTicks(10);
            check(ctx.computeOnClient(mc -> mc.player.isPassenger()), "riding the Vimana");
            ctx.getInput().holdKeyFor(o -> o.keyUp, 20);
            ctx.waitTicks(5);
            shot(ctx, "21_vimana");
            ctx.getInput().pressKey(o -> o.keyShift);
            ctx.waitTicks(40);

            // The two NPCs, then their clash.
            server.runCommand("difficulty peaceful");
            camera(ctx, CameraType.FIRST_PERSON);
            server.runCommand("execute as @p at @s run summon fatekings:gilgamesh ^-2 ^ ^6 {NoAI:1b}");
            server.runCommand("execute as @p at @s run summon fatekings:artoria ^2 ^ ^6 {NoAI:1b}");
            ctx.waitTicks(20);
            shot(ctx, "22_npcs");
            server.runCommand("kill @e[type=fatekings:gilgamesh]");
            server.runCommand("kill @e[type=fatekings:artoria]");
            server.runCommand("execute as @p at @s run summon fatekings:gilgamesh ^-40 ^ ^30 {NoAI:1b}");
            server.runCommand("execute as @p at @s run summon fatekings:artoria ^40 ^ ^30 {NoAI:1b}");
            ctx.waitTicks(10);
            server.runCommand("execute as @e[type=fatekings:gilgamesh] at @s run tp @s ~ ~ ~ facing entity @e[type=fatekings:artoria,limit=1] eyes");
            server.runCommand("execute as @e[type=fatekings:artoria] at @s run tp @s ~ ~ ~ facing entity @e[type=fatekings:gilgamesh,limit=1] eyes");
            ctx.waitTicks(5);
            server.runCommand("execute as @e[type=fatekings:gilgamesh] at @s run fate cast enuma_elish 90");
            server.runCommand("execute as @e[type=fatekings:artoria] at @s run fate cast excalibur 60");
            ctx.getInput().lookAt(0.0f, 0.0f);
            ctx.waitTicks(15);
            shot(ctx, "23_clash");
            ctx.waitTicks(30);
            shot(ctx, "24_clash_decided");
            ctx.waitTicks(40);

            archer(ctx, server);
        }
    }

    /** A still husk with 500 health: struck, it lives on to show the hit. */
    private static void summonHusk(TestServerContext server, String at, String extra) {
        server.runCommand("execute as @p at @s run summon minecraft:husk " + at
            + " {NoAI:1b,PersistenceRequired:1b,Health:500.0f,attributes:[{id:\"minecraft:max_health\",base:500.0d}]" + extra + "}");
    }

    private static void faceNearestHusk(TestServerContext server) {
        server.runCommand("execute as @p at @s anchored eyes facing entity @e[type=minecraft:husk,limit=1,sort=nearest] eyes run tp @s ~ ~ ~ ~ ~");
    }

    private static int hurtHusks(TestServerContext server) {
        return server.computeOnServer(s -> s.overworld().getEntities(EntityTypes.HUSK, e -> e.getHealth() < e.getMaxHealth()).size());
    }

    /**
     * 1.1.x, the Red Archer: the shroud, the bow (tap, triple shot, Caladbolg II), Rho Aias, the twin
     * blades (combo, throw, Crane Wing), tracing and the Hill of Swords, the reality marble, the
     * replica and the EMIYA NPC. Every renderer, mixin and screen of his runs here at least once.
     */
    private static void archer(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("kill @e[type=fatekings:gilgamesh]");
        server.runCommand("kill @e[type=fatekings:artoria]");
        camera(ctx, CameraType.FIRST_PERSON);
        equip(server, "fatekings:shroud_headpiece", "fatekings:shroud_coat", "fatekings:shroud_leggings", "fatekings:shroud_boots");
        ctx.waitTicks(5);
        // Out of the golden set; the swap lock is lifted by the reset (as an operator may).
        server.runCommand("fate cooldowns reset @p");
        ctx.waitFor(mc -> ClientKingState.king == KingRules.ARCHER, 100);
        check(ClientKingState.king == KingRules.ARCHER, "full Red Shroud -> Red Archer");
        ctx.waitTicks(15);
        shot(ctx, "30_archer_title_hud");
        ctx.waitTicks(40);
        check(ctx.computeOnClient(mc -> mc.player.getMaxHealth()) >= 79.0f, "Archer: 40 hearts");
        server.runCommand("item replace entity @p hotbar.0 with fatekings:black_bow");
        server.runCommand("item replace entity @p hotbar.1 with fatekings:kanshou");
        server.runCommand("item replace entity @p hotbar.2 with fatekings:unlimited_blade_works");
        server.runCommand("item replace entity @p hotbar.3 with fatekings:emiya_spawn_egg");
        server.runCommand("item replace entity @p weapon.offhand with minecraft:air");
        ctx.getInput().pressKey(o -> o.keyHotbarSlots[0]);
        server.runCommand("difficulty normal");

        // The bow: a tap.
        summonHusk(server, "^ ^ ^12", "");
        ctx.waitTicks(10);
        faceNearestHusk(server);
        ctx.waitTicks(5);
        shot(ctx, "31_archer_bow_hand");
        ctx.getInput().pressKey(o -> o.keyUse);
        ctx.waitTicks(2);
        shot(ctx, "32_bow_tap");
        ctx.waitTicks(15);
        check(hurtHusks(server) == 1, "a tap of the bow: the homing arrow hits");

        // Sneak + attack: the triple shot, one arrow at each of three.
        server.runCommand("kill @e[type=minecraft:husk]");
        summonHusk(server, "^ ^ ^12", "");
        summonHusk(server, "^5 ^ ^12", "");
        summonHusk(server, "^-5 ^ ^12", "");
        ctx.waitTicks(10);
        faceNearestHusk(server);
        ctx.waitTicks(5);
        ctx.getInput().holdKey(o -> o.keyShift);
        ctx.waitTicks(3);
        ctx.getInput().pressKey(o -> o.keyAttack);
        ctx.waitTicks(2);
        shot(ctx, "33_bow_triple");
        ctx.getInput().releaseKey(o -> o.keyShift);
        ctx.waitTicks(3);
        check(ClientKingState.cooldown("bow_triple") > 0, "sneak + attack with the bow: the triple shot (on cooldown)");
        ctx.waitTicks(15);
        check(hurtHusks(server) == 3, "the triple shot hits all three foes (" + hurtHusks(server) + ")");

        // Held: Caladbolg II (the renderer that once failed at resource load).
        server.runCommand("kill @e[type=minecraft:husk]");
        summonHusk(server, "^ ^ ^20", "");
        ctx.waitTicks(10);
        faceNearestHusk(server);
        ctx.getInput().holdKey(o -> o.keyUse);
        ctx.waitTicks(36);
        shot(ctx, "34_caladbolg_drawn");
        ctx.getInput().releaseKey(o -> o.keyUse);
        ctx.waitTicks(2);
        shot(ctx, "35_caladbolg");
        ctx.waitTicks(6);
        shot(ctx, "36_broken_phantasm");
        check(ClientKingState.cooldown("caladbolg") > 0, "Caladbolg II released");
        ctx.waitTicks(30);

        // Rho Aias.
        ctx.getInput().holdKey(o -> o.keyShift);
        ctx.waitTicks(2);
        ctx.getInput().pressKey(o -> o.keyUse);
        ctx.waitTicks(2);
        ctx.getInput().releaseKey(o -> o.keyShift);
        ctx.waitTicks(10);
        check(ClientKingState.rhoPetals == 7, "Rho Aias: seven petals (" + ClientKingState.rhoPetals + ")");
        shot(ctx, "37_rho_aias");
        camera(ctx, CameraType.THIRD_PERSON_FRONT);
        ctx.waitTicks(5);
        shot(ctx, "38_rho_aias_front");
        camera(ctx, CameraType.FIRST_PERSON);
        ctx.waitTicks(120);

        // Kanshou & Bakuya: the married blade in the off hand, the combo, the throw, Crane Wing.
        server.runCommand("kill @e[type=minecraft:husk]");
        ctx.getInput().pressKey(o -> o.keyHotbarSlots[1]);
        ctx.waitTicks(10);
        check(ctx.computeOnClient(mc -> mc.player.getOffhandItem().is(FateItems.BAKUYA)), "Kanshou in hand: Bakuya in the other");
        summonHusk(server, "^ ^ ^2.5", "");
        ctx.waitTicks(10);
        faceNearestHusk(server);
        camera(ctx, CameraType.THIRD_PERSON_BACK);
        for (int i = 0; i < 6; ++i) {
            ctx.getInput().pressKey(o -> o.keyAttack);
            ctx.waitTicks(3);
            shot(ctx, "39_twin_stroke_" + (i + 1));
            ctx.waitTicks(4);
        }
        camera(ctx, CameraType.FIRST_PERSON);
        ctx.waitTicks(30);
        for (int i = 0; i < 3; ++i) {
            ctx.getInput().pressKey(o -> o.keyAttack);
            ctx.waitTicks(2);
            shot(ctx, "40_twin_first_person_" + (i + 1));
            ctx.waitTicks(5);
        }
        check(hurtHusks(server) == 1, "the twin blades strike");
        server.runCommand("kill @e[type=minecraft:husk]");
        summonHusk(server, "^ ^ ^8", "");
        ctx.waitTicks(10);
        faceNearestHusk(server);
        ctx.getInput().pressKey(o -> o.keyUse);
        ctx.waitTicks(6);
        shot(ctx, "41_twin_throw");
        ctx.waitTicks(30);
        check(ClientKingState.cooldown("twin_throw") > 0 || hurtHusks(server) == 1, "the pair thrown");
        server.runCommand("fate cooldowns reset @p");
        faceNearestHusk(server);
        ctx.getInput().holdKey(o -> o.keyShift);
        ctx.waitTicks(2);
        ctx.getInput().pressKey(o -> o.keyUse);
        ctx.getInput().releaseKey(o -> o.keyShift);
        ctx.waitTicks(11);
        shot(ctx, "42_crane_wing");
        check(ClientKingState.cooldown("crane_wing") > 0, "Crane Wing");
        ctx.waitTicks(40);

        // Tracing: a husk with a golden sword is analysed; the Hill of Swords; a projection.
        server.runCommand("kill @e[type=minecraft:husk]");
        ctx.getInput().pressKey(o -> o.keyHotbarSlots[2]);
        summonHusk(server, "^ ^ ^6", ",equipment:{mainhand:{id:\"minecraft:golden_sword\",count:1}}");
        ctx.waitTicks(10);
        faceNearestHusk(server);
        ctx.waitTicks(3);
        ctx.getInput().pressKey(o -> o.keyUse);
        ctx.waitTicks(5);
        shot(ctx, "43_structural_analysis");
        ctx.waitFor(mc -> ClientArsenal.ENTRIES.stream().anyMatch(st -> st.is(Items.GOLDEN_SWORD)), 40);
        check(ClientArsenal.ENTRIES.stream().anyMatch(st -> st.is(Items.GOLDEN_SWORD)), "the golden sword is on the Hill of Swords");
        ctx.getInput().holdKey(o -> o.keyShift);
        ctx.waitTicks(2);
        ctx.getInput().pressKey(o -> o.keyUse);
        ctx.getInput().releaseKey(o -> o.keyShift);
        ctx.waitFor(mc -> mc.gui.screen() instanceof ArsenalScreen, 40);
        check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof ArsenalScreen), "sneak + use: the Hill of Swords screen");
        ctx.waitTicks(5);
        shot(ctx, "44_hill_of_swords");
        ctx.runOnClient(mc -> mc.gui.setScreen(null));
        ctx.waitTicks(5);
        server.runCommand("kill @e[type=minecraft:husk]");
        ctx.getInput().lookAt(0.0f, -80.0f);
        ctx.waitTicks(15);
        ctx.getInput().pressKey(o -> o.keyUse);
        ctx.waitTicks(10);
        shot(ctx, "45a_tap_at_nothing");
        check(ctx.computeOnClient(mc -> mc.player.getMainHandItem().is(Items.GOLDEN_SWORD) && Projection.projected(mc.player.getMainHandItem())),
            "a tap at nothing: the golden sword projected into the hand (" + ctx.computeOnClient(mc -> mc.player.getMainHandItem() + " slot "
                + mc.player.getInventory().getSelectedSlot() + " screen " + mc.gui.screen() + " selected " + ClientArsenal.selected
                + " projected " + ClientKingState.projected + " trace " + ClientKingState.cooldown("trace")) + ")");
        ctx.getInput().lookAt(0.0f, 5.0f);
        ctx.waitTicks(5);
        shot(ctx, "45_projected_sword");

        // Unlimited Blade Works: the aria, the ring of fire, the hill; swords rise at foes inside.
        server.runCommand("fate cooldowns reset @p");
        ctx.getInput().pressKey(o -> o.keyHotbarSlots[2]);
        ctx.waitTicks(5);
        ctx.getInput().holdKey(o -> o.keyUse);
        ctx.waitTicks(30);
        shot(ctx, "46_ubw_aria");
        ctx.waitTicks(36);
        ctx.getInput().releaseKey(o -> o.keyUse);
        ctx.waitTicks(15);
        shot(ctx, "47_ubw_unfolding");
        ctx.waitTicks(40);
        check(ClientKingState.ubwLeft > 0, "the reality marble is open (" + ClientKingState.ubwLeft + ")");
        shot(ctx, "48_ubw_inside");
        check(ctx.computeOnClient(mc -> UbwClient.hidesTerrain()), "inside the open marble the world's own ground is not drawn");
        lookAtSun(ctx);
        ctx.waitTicks(3);
        shot(ctx, "48a_ubw_sun");
        ctx.getInput().lookAt(0.0f, 45.0f);
        ctx.waitTicks(3);
        shot(ctx, "48b_ubw_scorched_ground");
        ctx.getInput().lookAt(0.0f, 0.0f);
        ctx.waitTicks(3);
        summonHusk(server, "^ ^ ^10", "");
        summonHusk(server, "^4 ^ ^12", "");
        for (int i = 0; i < 6; ++i) {
            ctx.waitTicks(4);
            shot(ctx, "49_ubw_sword_rain_" + (i + 1));
        }
        camera(ctx, CameraType.THIRD_PERSON_BACK);
        ctx.getInput().lookAt(0.0f, -10.0f);
        ctx.waitTicks(10);
        shot(ctx, "50_ubw_third_person");
        camera(ctx, CameraType.FIRST_PERSON);
        // A volley every second; each sword rises, takes aim a moment and is loosed (some 1-1.5 s in all).
        for (int i = 0; i < 6 && hurtHusks(server) < 1; ++i) ctx.waitTicks(10);
        check(hurtHusks(server) >= 1, "the swords of the hill strike the foes inside (" + server.computeOnServer(s2 -> {
            var p = s2.getPlayerList().getPlayers().getFirst();
            StringBuilder sb = new StringBuilder();
            for (var hk : s2.overworld().getEntities(EntityTypes.HUSK, e -> true)) {
                sb.append("husk ").append(hk.position().subtract(p.position())).append(" hp ").append(hk.getHealth()).append("/").append(hk.getMaxHealth()).append("; ");
            }
            for (var m : s2.overworld().getEntities(cn.blockforge.fatekings.registry.FateEntities.UBW, e -> true)) {
                sb.append("marble ").append(m.position().subtract(p.position())).append(" active ").append(m.active()).append("; ");
            }
            sb.append("swords ").append(s2.overworld().getEntities(cn.blockforge.fatekings.registry.FateEntities.UBW_SWORD, e -> true).size());
            return sb.toString();
        }) + ")");
        server.runCommand("kill @e[type=minecraft:husk]");

        // A replica of Excalibur, released (and shattered).
        server.runCommand("fate cooldowns reset @p");
        server.runCommand("item replace entity @p hotbar.4 with fatekings:excalibur_replica");
        ctx.getInput().pressKey(o -> o.keyHotbarSlots[4]);
        ctx.getInput().lookAt(0.0f, 0.0f);
        ctx.waitTicks(5);
        ctx.getInput().holdKey(o -> o.keyUse);
        ctx.waitTicks(36);
        ctx.getInput().releaseKey(o -> o.keyUse);
        ctx.waitTicks(5);
        shot(ctx, "51_excalibur_replica");
        check(ctx.computeOnClient(mc -> mc.player.getMainHandItem().isEmpty()), "the replica shattered");
        ctx.waitTicks(40);

        // EMIYA, from his egg.
        server.runCommand("execute as @p at @s run summon fatekings:emiya ^ ^ ^5 {NoAI:1b}");
        ctx.waitTicks(20);
        shot(ctx, "52_emiya_npc");
        server.runCommand("kill @e[type=fatekings:emiya]");
        ctx.waitTicks(20);
        marbleWorld(ctx, server);
    }

    private static void lookAtSun(ClientGameTestContext ctx) {
        float[] look = ctx.computeOnClient(mc -> {
            UbwEntity u = mc.level.getEntitiesOfClass(UbwEntity.class, mc.player.getBoundingBox().inflate(80.0), x -> true).stream().findFirst().orElse(null);
            if (u == null) return new float[]{0.0f, -30.0f};
            Vec3 sun = u.position().add(cn.blockforge.fatekings.client.render.UbwRenderer.sunDirection(u).scale(u.radius() * 0.88));
            Vec3 d = sun.subtract(mc.player.getEyePosition()).normalize();
            return new float[]{(float)Math.toDegrees(Math.atan2(-d.x, d.z)), (float)-Math.toDegrees(Math.asin(d.y))};
        });
        ctx.getInput().lookAt(look[0], look[1]);
    }

    private static void tp(TestServerContext server, Vec3 at, float yaw, float pitch) {
        server.runCommand(String.format(java.util.Locale.ROOT, "tp @p %.2f %.2f %.2f %.1f %.1f", at.x, at.y, at.z, yaw, pitch));
    }

    /**
     * 1.1.4: the marble as a world of its own, the size of a JJK domain: its plain of scorched earth
     * (terrain hidden), the sun, the haze; the burning sphere from outside; and, with the Gojo x
     * Sukuna mod, the split world of a clash with Malevolent Shrine and with Unlimited Void.
     */
    private static void marbleWorld(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("kill @e[type=fatekings:unlimited_blade_works]");
        server.runCommand("kill @e[type=minecraft:husk]");
        server.runCommand("gamemode creative @p");
        server.runCommand("difficulty peaceful");
        server.runOnServer(s -> {
            s.getPlayerList().setViewDistance(14);
            s.getPlayerList().setSimulationDistance(10);
        });
        ctx.runOnClient(mc -> {
            mc.options.renderDistance().set(14);
            mc.options.broadcastOptions();
        });
        camera(ctx, CameraType.FIRST_PERSON);
        ctx.waitTicks(40);
        Vec3 home = ctx.computeOnClient(mc -> mc.player.position());
        Vec3 base = server.computeOnServer(s -> new Vec3(home.x, s.overworld().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
            (int)Math.floor(home.x), (int)Math.floor(home.z)), home.z));
        // EMIYA opens his marble here (so the player may leave it and look back).
        server.runCommand(String.format(java.util.Locale.ROOT, "summon fatekings:emiya %.2f %.2f %.2f {NoAI:1b,Rotation:[90f,0f]}", base.x, base.y, base.z));
        ctx.waitTicks(10);
        server.runCommand("execute as @e[type=fatekings:emiya,limit=1] at @s run fate cast ubw");
        tp(server, base.add(4.0, 0.0, 0.0), 0.0f, 0.0f);
        ctx.waitTicks(70);
        check(ctx.computeOnClient(mc -> UbwClient.hidesTerrain()), "EMIYA's marble: the plain replaces the ground");
        shot(ctx, "60_world_horizon");
        lookAtSun(ctx);
        ctx.waitTicks(3);
        shot(ctx, "61_world_sun");
        ctx.getInput().lookAt(120.0f, 35.0f);
        ctx.waitTicks(3);
        shot(ctx, "62_world_ground");
        ctx.getInput().lookAt(30.0f, -70.0f);
        ctx.waitTicks(3);
        shot(ctx, "63_world_sky");
        // From outside: a burning sphere.
        tp(server, base.add(0.0, 24.0, -130.0), 0.0f, 8.0f);
        ctx.waitTicks(60);
        check(!ctx.computeOnClient(mc -> UbwClient.hidesTerrain()), "outside it the ground is there again");
        shot(ctx, "64_world_outside");
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sukuna")) {
            server.runCommand("kill @e[type=fatekings:emiya]");
            return;
        }
        // Against Malevolent Shrine: a Sukuna NPC 96 blocks east, facing the marble.
        server.runCommand(String.format(java.util.Locale.ROOT, "summon sukuna:sukuna %.2f %.2f %.2f {NoAI:1b,Rotation:[90f,0f]}", base.x + 96.0, base.y, base.z));
        ctx.waitTicks(10);
        server.runOnServer(s -> {
            var npc = s.overworld().getEntities(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("sukuna", "sukuna")), e -> true).stream().findFirst().orElseThrow();
            try {
                Class.forName("cn.blockforge.ryomensukuna.m2a542fea.skill.domain.DomainSkill")
                    .getMethod("castFor", net.minecraft.world.entity.LivingEntity.class, int.class, int.class).invoke(null, npc, 4000, -1);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Malevolent Shrine: " + e);
            }
        });
        tp(server, base.add(40.0, 2.0, 0.0), -90.0f, 0.0f);
        ctx.waitTicks(150);
        shot(ctx, "65_clash_shrine_seam");
        tp(server, base.add(40.0, 2.0, 0.0), 90.0f, 0.0f);
        ctx.waitTicks(5);
        shot(ctx, "66_clash_shrine_marble_side");
        tp(server, base.add(40.0, 2.0, 0.0), 0.0f, 0.0f);
        ctx.waitTicks(5);
        shot(ctx, "67_clash_shrine_along_seam");
        tp(server, base.add(48.0, 30.0, -150.0), 0.0f, 8.0f);
        ctx.waitTicks(40);
        shot(ctx, "68_clash_shrine_outside");
        server.runCommand("kill @e[type=sukuna:sukuna]");
        server.runCommand("kill @e[type=sukuna:shrine]");
        ctx.waitTicks(20);
        // Against Unlimited Void: a Gojo NPC in the same place.
        server.runCommand(String.format(java.util.Locale.ROOT, "summon sukuna:gojo %.2f %.2f %.2f {NoAI:1b,Rotation:[90f,0f]}", base.x + 96.0, base.y, base.z));
        ctx.waitTicks(10);
        server.runOnServer(s -> {
            var npc = s.overworld().getEntities(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("sukuna", "gojo")), e -> true).stream().findFirst().orElseThrow();
            try {
                Class.forName("cn.blockforge.ryomensukuna.m2a542fea.gojo.GojoSkills")
                    .getMethod("expandVoid", net.minecraft.world.entity.LivingEntity.class, int.class, int.class).invoke(null, npc, -1, 4000);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Unlimited Void: " + e);
            }
        });
        tp(server, base.add(40.0, 2.0, 0.0), -90.0f, 0.0f);
        ctx.waitTicks(80);
        shot(ctx, "69_clash_void_seam");
        tp(server, base.add(40.0, 2.0, 0.0), 0.0f, 0.0f);
        ctx.waitTicks(5);
        shot(ctx, "70_clash_void_along_seam");
        tp(server, base.add(48.0, 30.0, -150.0), 0.0f, 8.0f);
        ctx.waitTicks(40);
        shot(ctx, "71_clash_void_outside");
        server.runCommand("kill @e[type=sukuna:gojo]");
        server.runCommand("kill @e[type=sukuna:unlimited_void]");
        server.runCommand("kill @e[type=fatekings:emiya]");
        ctx.waitTicks(20);
    }
}
