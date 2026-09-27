package cn.blockforge.fatekings.gametest;

import cn.blockforge.fatekings.client.ClientKingState;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.registry.FateItems;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
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
            ctx.getInput().pressKey(o -> o.keyJump);
            ctx.waitTicks(25);
            Vec3 after = pos(ctx);
            double leap = Math.hypot(after.x - before.x, after.z - before.z);
            check(leap > 8.0, "knight leap covers distance (" + String.format("%.1f", leap) + " blocks)");
            // Leap with a direction key (back).
            before = pos(ctx);
            ctx.getInput().holdKey(o -> o.keyDown);
            ctx.waitTick();
            ctx.getInput().pressKey(o -> o.keyJump);
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
            check(ClientKingState.king == KingRules.NONE, "swap lock keeps the other king away");
            ctx.waitFor(mc -> ClientKingState.king == KingRules.HERO, 400);
            check(ClientKingState.king == KingRules.HERO, "after 15 s: King of Heroes");
            ctx.waitTicks(15);
            shot(ctx, "10_hero_title");
            camera(ctx, CameraType.THIRD_PERSON_FRONT);
            ctx.waitTicks(10);
            shot(ctx, "11_hero_front");
            camera(ctx, CameraType.THIRD_PERSON_BACK);

            // Flight: double-tap jump.
            ctx.getInput().pressKey(o -> o.keyJump);
            ctx.waitTicks(2);
            ctx.getInput().pressKey(o -> o.keyJump);
            ctx.waitTicks(5);
            check(ctx.computeOnClient(mc -> mc.player.getAbilities().flying), "double jump: flying");
            ctx.getInput().holdKeyFor(o -> o.keyJump, 12);
            ctx.waitTicks(5);
            shot(ctx, "12_hero_flying");
            ctx.getInput().pressKey(o -> o.keyJump);
            ctx.waitTicks(2);
            ctx.getInput().pressKey(o -> o.keyJump);
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
        }
    }
}
