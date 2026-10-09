package cn.blockforge.fatekings.checks;

import cn.blockforge.fatekings.archer.ArcherRules;
import cn.blockforge.fatekings.combat.DamageRules;
import cn.blockforge.fatekings.combat.JudgementRules;
import cn.blockforge.fatekings.combat.JudgementRules.Outcome;
import cn.blockforge.fatekings.combat.JudgementRules.Side;
import cn.blockforge.fatekings.combat.JudgementRules.Weapon;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.KingState;
import cn.blockforge.fatekings.king.KingSync;
import cn.blockforge.fatekings.king.Skills;
import cn.blockforge.fatekings.npc.KingAiRules;
import cn.blockforge.fatekings.voice.Voice;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Headless checks of the pure rules and of resource completeness (run by {@code ./gradlew check}).
 * Argument: the path of src/main/resources.
 */
public final class RulesCheck {
    private static int passed;

    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError("FAILED: " + label);
        ++passed;
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1.0E-4;
    }

    public static void main(String[] args) throws IOException {
        Path res = Path.of(args[0]);
        kings();
        damage();
        judgement();
        archer();
        ai();
        state();
        resources(res);
        System.out.println("PASS: " + passed + " checks");
    }

    private static void kings() {
        check(KingRules.kingOfSet(4, 0) == KingRules.HERO, "full golden set -> hero");
        check(KingRules.kingOfSet(0, 4) == KingRules.KNIGHT, "full knight set -> knight");
        check(KingRules.kingOfSet(3, 1) == KingRules.NONE, "mixed sets -> nobody");
        check(KingRules.kingOfSet(3, 0) == KingRules.NONE, "incomplete golden set -> nobody");
        check(KingRules.kingOfSet(2, 2) == KingRules.NONE, "two and two -> nobody");
        check(KingRules.kingOfSet(0, 0) == KingRules.NONE, "nothing -> nobody");
        check(KingRules.kingOfSet(0, 0, 4) == KingRules.ARCHER, "full Red Shroud -> archer");
        check(KingRules.kingOfSet(4, 0, 1) == KingRules.NONE && KingRules.kingOfSet(0, 1, 3) == KingRules.NONE
            && KingRules.kingOfSet(2, 0, 2) == KingRules.NONE, "a shroud piece spoils any set, any other piece spoils the shroud");
        check(KingRules.kingOfSet(4, 0, 0) == KingRules.HERO && KingRules.kingOfSet(0, 4, 0) == KingRules.KNIGHT, "three-way sets keep the two kings");
        check(!KingRules.mayEnter(KingRules.ARCHER, KingRules.KNIGHT, 1000, 999), "knight -> archer waits for the lock");
        check(near(KingRules.ARCHER_SPEED, 0.25) && near(KingRules.speed(KingRules.ARCHER), 0.25) && KingRules.speed(KingRules.NONE) == 0.0f,
            "archer +25%");
        check("ARCHER".equals(KingRules.roman(KingRules.ARCHER)), "roman name");
        check(!KingRules.mayEnter(KingRules.KNIGHT, KingRules.HERO, 1000, 999), "hero -> knight inside the 15 s lock is refused");
        check(KingRules.mayEnter(KingRules.KNIGHT, KingRules.HERO, 1000, 1000), "hero -> knight once the lock ends");
        check(KingRules.mayEnter(KingRules.HERO, KingRules.HERO, 1000, 10), "returning to the same king is never locked");
        check(KingRules.mayEnter(KingRules.HERO, KingRules.NONE, 0, 0), "first time is free");
        check(KingRules.SWAP_LOCK == 300, "swap lock is 15 s");
        check(KingRules.MAX_HEALTH == 80.0f, "40 red hearts");
        check(KingRules.GOLD_HP == 60.0f, "30 gold hearts");
        check(KingRules.ARMOR == 30.0f && KingRules.TOUGHNESS == 20.0f, "armor 30 / toughness 20");
        check(KingRules.UNARMED == 24.0f, "fists 24");
        check(near(KingRules.HERO_SPEED, 0.2) && near(KingRules.KNIGHT_SPEED, 0.3), "speed +20% / +30%");
        check(KingRules.volleyGates(0) == 0, "no volley on a tap");
        check(KingRules.volleyGates(KingRules.GOB_VOLLEY_MIN) == 5, "a volley starts with 5 gates");
        check(KingRules.volleyGates(KingRules.GOB_VOLLEY_FULL) == 100, "a volley grows to 100 gates");
        check(KingRules.volleyGates(1000) == 100, "never more than 100 gates");
        check(KingRules.volleyGates(40) > 5 && KingRules.volleyGates(40) < 100, "gates grow while held");
        check(KingRules.GOB_VOLLEY == 60, "volley cooldown 3 s (was 8 s)");
        // 1.1.3: the gates hang apart in the air, never one wall.
        double least = Double.MAX_VALUE, wide = 0.0, high = -9.0, low = 9.0, deep = 0.0;
        for (double phase = 0.0; phase < 6.28; phase += 0.37) {
            java.util.List<double[]> spots = new java.util.ArrayList<>();
            for (int i = 0; i < 100; ++i) spots.add(KingRules.gateSpot(i, phase));
            for (int i = 0; i < spots.size(); ++i) {
                double[] a = spots.get(i);
                wide = Math.max(wide, Math.abs(a[0]));
                high = Math.max(high, a[1]);
                low = Math.min(low, a[1]);
                deep = Math.max(deep, a[2]);
                for (int j = i + 1; j < spots.size(); ++j) {
                    double[] b = spots.get(j);
                    least = Math.min(least, Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2])));
                }
            }
        }
        check(least > 2.0 * 0.9 * KingRules.gateSize(1.0), "100 gates never overlap (closest " + String.format(Locale.ROOT, "%.2f", least)
            + ", a gate at most " + String.format(Locale.ROOT, "%.2f", 1.8 * KingRules.gateSize(1.0)) + " across)");
        check(wide >= 12.0 && wide <= 18.0 && high >= 10.0 && high <= 17.0 && low >= 0.0 && deep >= 3.0,
            "spread wide, high and deep behind him (" + String.format(Locale.ROOT, "%.1f / %.1f / %.1f / %.1f", wide, high, low, deep) + ")");
        double[] firstSpot = KingRules.gateSpot(0, 0.0);
        check(Math.hypot(firstSpot[0], firstSpot[1]) < 2.0, "the first gates open close to him");
        check(KingRules.gateSize(0.0) == 0.75f && Math.abs(KingRules.gateSize(1.0) - 0.9f) < 1.0E-6f, "gate sizes 0.75-0.9");
        // 1.1.1: Excalibur's trench and crater.
        check(KingRules.inExcaliburCut(0.0, 0.0, 5.0f) && KingRules.inExcaliburCut(1.9, 0.0, 5.0f) && !KingRules.inExcaliburCut(2.1, 0.0, 5.0f),
            "the trench is 0.4 of the beam's width to each side");
        check(KingRules.inExcaliburCut(0.0, -3.1, 5.0f) && !KingRules.inExcaliburCut(0.0, -3.3, 5.0f), "and 1.6 times that deep below the line");
        check(near(KingRules.excaliburCutDepth(0.0, 9.0f), 5.76) && KingRules.excaliburCutDepth(3.7, 9.0f) == 0.0,
            "a full charge cuts some 4 blocks below the feet");
        check(KingRules.excaliburCutDepth(1.0, 5.0f) < KingRules.excaliburCutDepth(0.0, 5.0f), "deepest in the middle");
        check(KingRules.excaliburBlast(5.0f, false) == 6.0f && KingRules.excaliburBlast(9.0f, false) == 8.0f && KingRules.excaliburBlast(9.0f, true) == 4.0f,
            "crater 6-8, a replica's 4");
        check(KingRules.GOB_DAMAGE_MIN == 20.0f && KingRules.GOB_DAMAGE_MAX == 30.0f, "treasures hit for 20-30 (was 12-18)");
        check(KingRules.gateMark(0, 3) == 0 && KingRules.gateMark(1, 3) == 1 && KingRules.gateMark(2, 3) == 2 && KingRules.gateMark(3, 3) == 0,
            "gates take the foes in turn, the aimed one first");
        check(KingRules.gateMark(5, 1) == 0 && KingRules.gateMark(0, 0) == -1, "one foe takes all; none: no mark");
        int[] perFoe = new int[3];
        for (int g = 0; g < 100; ++g) ++perFoe[KingRules.gateMark(g, 3)];
        check(perFoe[0] == 34 && perFoe[1] == 33 && perFoe[2] == 33, "100 gates over 3 foes: 34 / 33 / 33");
        check(KingRules.GOB_MAX_TARGETS == 10 && Math.abs(KingRules.GOB_CONE_COS - Math.cos(Math.toRadians(40.0))) < 0.001, "up to 10 foes within 40 degrees");
        check(Math.abs(KingRules.GOB_RELEASE_CONE_COS - Math.cos(Math.toRadians(65.0))) < 0.001, "a held volley re-aims within 65 degrees as it fires");
        double[] still = KingRules.npcLead(new double[]{1, 2, 3}, new double[]{0, 0, 0}, 20.0, 8, 3.0);
        check(still[0] == 1 && still[1] == 2 && still[2] == 3, "a foe standing still: aimed at where it is");
        double[] runner = KingRules.npcLead(new double[]{0, 0, 0}, new double[]{0.2, 0.5, 0}, 6.0, 8, 3.0);
        check(near(runner[0], 0.2 * (8 + 2)) && runner[1] == 0.0 && runner[2] == 0.0, "a runner: led once along its way, never up or down");
        double[] fast = KingRules.npcLead(new double[]{0, 0, 0}, new double[]{3, 0, 4}, 30.0, 8, 3.0);
        check(near(Math.hypot(fast[0], fast[2]), KingRules.GOB_NPC_LEAD_MAX), "the lead is capped");
        check(KingRules.DODGE_VANILLA == 0.5f && KingRules.DODGE_TREASURE == 0.35f, "Instinct: 50% / 35% (was 30% / 20%)");
        check(KingRules.REVEALED == 200, "blade revealed 10 s after Strike Air");
        check(KingRules.NPC_MAX_HEALTH == 200.0f && KingRules.MAX_HEALTH == 80.0f, "NPCs 200 health, players unchanged");
        check(KingRules.NPC_GOLD_HP == 150.0f, "NPC gold scaled alike (150)");
        check(near(KingRules.PLAYER_VS_NPC, 2.5), "players hit NPCs x2.5: same fight length as before");
        check(near(KingRules.NPC_GOLD_HP / KingRules.PLAYER_VS_NPC, KingRules.GOLD_HP), "scaled gold matches the players' 30 hearts");
        check(KingRules.KING_VS_JJK_DAMAGE == 2.0f, "king blows (NPC or player) on Gojo / Sukuna NPCs x2");
        check(KingRules.waterRun(true, true, 0.0, false), "sprinting knight runs on water");
        check(KingRules.waterRun(true, false, 0.02, false), "moving knight stays on water even after vanilla stops the sprint");
        check(!KingRules.waterRun(true, false, 0.0, false), "a knight standing still sinks");
        check(!KingRules.waterRun(false, true, 1.0, false), "only the knight");
        check(KingRules.waterRun(true, false, 0.0, true), "the NPC knight too");
        double[] ahead = KingRules.leapVelocity(new double[]{0, 0, 1}, null, true);
        double[] up = KingRules.leapVelocity(new double[]{0, 0.8, 0.6}, null, true);
        double[] back = KingRules.leapVelocity(new double[]{0, 0.8, 0.6}, new double[]{0, 0, -1}, true);
        check(ahead[2] > 1.5 && ahead[1] > 0.5, "space alone: a long arc along the view");
        check(up[1] > ahead[1] && up[2] < ahead[2] && up[1] <= KingRules.LEAP_MAX_LIFT + 1.0E-9, "looking up: a higher, shorter leap (capped)");
        check(back[2] < -1.5 && Math.abs(back[1] - KingRules.LEAP_LIFT) < 1.0E-9, "S + space: backwards along the ground");
        check(KingRules.leapVelocity(new double[]{0, 0, 1}, null, false)[1] < ahead[1], "the mid-air leap is lower");
        check(KingRules.excaliburWidth(KingRules.EXCALIBUR_CHARGE) == 5.0f, "Excalibur 5 wide at 1.5 s");
        check(KingRules.excaliburWidth(KingRules.EXCALIBUR_FULL) == 9.0f, "Excalibur 9 wide at 4 s");
        check(KingRules.excaliburWidth(500) == 9.0f, "Excalibur width capped");
        check(KingRules.eaWidth(KingRules.EA_CHARGE) == 4.0f, "Ea starts 4 wide");
        check(KingRules.eaWidth(1000) == 10.0f, "Ea width capped");
        check(KingRules.excaliburCooldown(false, false) == 1200, "player Excalibur 60 s");
        check(KingRules.excaliburCooldown(true, false) == 1800, "NPC Excalibur 90 s");
        check(KingRules.excaliburCooldown(true, true) == 900, "last stand Excalibur 45 s");
        check(KingRules.BAB_ILU == 1800, "Bab-ilu 90 s");
        check(KingRules.EA_NPC == 3600, "NPC Ea 180 s");
        check(KingRules.excaliburSwing(false, false) == 30.0f, "Invisible Air swing 30");
        check(KingRules.excaliburSwing(true, false) == 39.0f, "revealed swing +30%");
        check(KingRules.excaliburSwing(false, true) == 39.0f, "last stand swing revealed");
        check(KingRules.avalonRegen(0) == 1.0f, "Avalon 0.5 hearts/s in combat");
        check(KingRules.avalonRegen(100) == 4.0f, "Avalon 2 hearts/s after 5 s out of combat");
        check(KingRules.healScale(false) == 1.0f && KingRules.healScale(true) == 2.5f, "NPC heals x2.5 (200-health pool)");
        check(near(KingRules.healScale(true) / KingRules.NPC_MAX_HEALTH, 1.0 / KingRules.MAX_HEALTH), "an NPC recovers the same share per second as a player");
        float[] split = KingRules.splitDamage(10.0f, 4.0f);
        check(split[0] == 4.0f && split[1] == 0.0f, "gold takes a small hit entirely");
        split = KingRules.splitDamage(3.0f, 10.0f);
        check(split[0] == 3.0f && split[1] == 7.0f, "gold takes what it can, red takes the rest");
        split = KingRules.splitDamage(0.0f, 5.0f);
        check(split[0] == 0.0f && split[1] == 5.0f, "no gold: all red");
        check(KingRules.ultimateOnCharacter(0.0f) == 90.0f && KingRules.ultimateOnCharacter(1.0f) == 100.0f, "90-100 on players");
        check(KingRules.MOB_DAMAGE == 1000.0f, "Ea / Excalibur on creatures: 1000");
        check(KingRules.NPC_PHANTASM_LOSS == 190.0f && KingRules.NPC_PHANTASM_LOSS < KingRules.NPC_MAX_HEALTH, "190 off an NPC: one blow leaves 10");
        check(near(KingRules.rawForLoss(190.0f, 0.1f), 1900.0) && near(KingRules.rawForLoss(190.0f, 0.25f), 760.0), "king NPC: 10% (x2.5 from a player)");
        check(near(KingRules.rawForLoss(190.0f, 0.5f), 380.0) && near(KingRules.rawForLoss(190.0f, 0.5f) * 0.5, 190.0), "Gojo / Sukuna NPC: 2 x 2.5 x 10% = 50%");
        check(KingRules.rawForLoss(190.0f, 0.0f) == 190.0f && KingRules.rawForLoss(190.0f, Float.NaN) == 190.0f, "no usable share: the loss as is");
        check(KingRules.TREASURY_REORG == 160 && KingRules.MANA_DEPLETION == 100, "reorg 8 s, depletion 5 s");
        check(KingRules.MANA_DEPLETION < KingRules.TREASURY_REORG, "depletion 3 s shorter than reorg");
        check(KingRules.EXCALIBUR < KingRules.BAB_ILU, "Excalibur cools faster than the key");
    }

    private static void damage() {
        check(near(DamageRules.kingTaken(false), 0.01), "vanilla hits: 1%");
        check(near(DamageRules.kingTaken(true), 0.10), "mod characters: 10%");
        check(near(DamageRules.jjkTaken(5, false), 0.01), "stage V sorcerer: 1%");
        check(near(DamageRules.jjkTaken(5, true), 0.10), "stage V between sorcerers: 10%");
        check(near(DamageRules.jjkTaken(0, false), 1.0), "unawakened: all");
        // A stage V sorcerer wearing the set: the other mod multiplies too; together it must be the lower share.
        check(near(DamageRules.kingTakenWithJjk(false, 5, false) * DamageRules.jjkTaken(5, false), 0.01), "both mods: still 1%");
        check(near(DamageRules.kingTakenWithJjk(true, 5, true) * DamageRules.jjkTaken(5, true), 0.10), "both mods: still 10%");
        check(near(DamageRules.kingTakenWithJjk(false, 2, false) * DamageRules.jjkTaken(2, false), 0.01), "stage II + king: the king's 1% wins");
        check(near(DamageRules.kingTakenWithJjk(true, 1, true) * DamageRules.jjkTaken(1, true), 0.10), "stage I + king vs sorcerer: 10%");
        check(DamageRules.kingTakenWithJjk(false, 5, false) <= 1.0f, "never amplifies");
        check(DamageRules.crippledHealth(80.0f) == 2.0f, "Excalibur leaves Gojo at one heart");
        check(DamageRules.crippledHealth(1.0f) == 1.0f, "below one heart stays unchanged");
        check(DamageRules.crippledHealth(2.0f) == 2.0f, "exactly one heart stays");
        check(DamageRules.crippledHealth(0.5f) > 0.0f, "Excalibur itself never kills Gojo");
        check(DamageRules.kingGoldTarget(60.0f) == 0.0f, "stage V sorcerer gold already 30 hearts: no extra");
        check(DamageRules.kingGoldTarget(30.0f) == 30.0f, "stage II: top up to 30 hearts");
        check(DamageRules.kingGoldTarget(0.0f) == 60.0f, "no other gold: 30 hearts");
        check(DamageRules.topUp(20.0, 30.0) == 10.0, "armor 20 -> +10");
        check(DamageRules.topUp(35.0, 30.0) == 0.0, "never stacks above");
    }

    private static void archer() {
        // Homing: never more than the turn limit, unit length, straight to the target inside it.
        double[] cur = {1, 0, 0};
        double[] side = ArcherRules.turn(cur, new double[]{0, 0, 1}, Math.toRadians(18.0));
        check(Math.abs(Math.toDegrees(Math.acos(side[0])) - 18.0) < 1.0E-6 && near(Math.hypot(Math.hypot(side[0], side[1]), side[2]), 1.0),
            "homing turns 18 degrees a tick at most");
        double[] close = ArcherRules.turn(cur, new double[]{1, 0.1, 0}, Math.toRadians(18.0));
        check(Math.abs(close[1] - 0.1 / Math.hypot(1, 0.1)) < 1.0E-9, "within the limit it points straight at it");
        double[] back = ArcherRules.turn(cur, new double[]{-1, 0, 0}, Math.toRadians(30.0));
        check(near(Math.hypot(Math.hypot(back[0], back[1]), back[2]), 1.0) && back[0] < 0.9 && back[0] > 0.8, "turning about from straight behind");
        check(ArcherRules.arrowTurn(3.0) > ArcherRules.arrowTurn(10.0), "it turns harder when close");
        double[] led = ArcherRules.lead(new double[]{0, 0, 0}, new double[]{1, 0, 0}, 32.0, 3.2, ArcherRules.ARROW_LEAD_MAX);
        check(near(led[0], ArcherRules.ARROW_LEAD_MAX), "the arrow's lead is capped");
        check(ArcherRules.arrowDamage(0.0f) == 12.0f && ArcherRules.arrowDamage(1.0f) == 16.0f, "arrows 12-16");
        check(ArcherRules.bowTapCooldown(false) == 4 && ArcherRules.bowTapCooldown(true) == 2, "a tap every 4 ticks, 2 in his marble");
        // 1.1.1: the triple shot.
        check(ArcherRules.bowTripleCooldown(false) == 20 && ArcherRules.bowTripleCooldown(true) == 10, "triple shot 1 s, 0.5 s in his marble");
        check(ArcherRules.tripleYaw(0) == 8.0 && ArcherRules.tripleYaw(1) == 0.0 && ArcherRules.tripleYaw(2) == -8.0, "a fan 8 degrees apart");
        double[][] three = {{0.9, 0.2, 0.99}, {0.95, 0.5, 0.97}, {0.8, 0.99, 0.6}};
        int[] p3 = ArcherRules.tripleTargets(three, true);
        check(p3[1] == 0 && p3[0] == 2 && p3[2] == 1, "the aimed foe takes the centre, the others each a side arrow");
        int[] p1 = ArcherRules.tripleTargets(new double[][]{{0.9}, {0.99}, {0.9}}, true);
        check(p1[0] == 0 && p1[1] == 0 && p1[2] == 0, "one foe: all three arrows hunt it");
        int[] p2 = ArcherRules.tripleTargets(new double[][]{{0.99, 0.5}, {0.9, 0.8}, {0.7, 0.95}}, false);
        check(p2[1] == 0 && p2[2] == 1 && p2[0] == 0, "nothing aimed: centre takes the best in line, the spare arrow follows it");
        int[] p0 = ArcherRules.tripleTargets(new double[3][0], false);
        check(p0[0] == -1 && p0[1] == -1 && p0[2] == -1, "no foe: three straight arrows");
        check(KingAiRules.emiyaWantsTriple(true, 2, 0.9f) && KingAiRules.emiyaWantsTriple(true, 1, 0.1f)
            && !KingAiRules.emiyaWantsTriple(true, 1, 0.9f) && !KingAiRules.emiyaWantsTriple(false, 3, 0.0f), "EMIYA's triple shot: at two foes, now and then at one");
        // 1.1.1: the Red Shroud mends slowly.
        float idle = 0.0f, fight = 0.0f;
        for (long t = 0; t < 400; ++t) {
            idle += ArcherRules.regen(t, 200);
            fight += ArcherRules.regen(t, 10);
        }
        check(near(idle, 40.0) && near(fight, 10.0), "regen: 1 heart a second out of combat, 1 every 4 s in it (20 s: " + idle + " / " + fight + ")");
        check(ArcherRules.regen(20, 99) == 0.0f && ArcherRules.regen(20, 100) == ArcherRules.REGEN_HP, "out of combat after 5 s unhurt");
        check(ArcherRules.BOW_TAP_TICKS < ArcherRules.CALADBOLG_CHARGE && ArcherRules.CALADBOLG_CHARGE == 30, "tap / Caladbolg at 1.5 s");
        check(ArcherRules.caladbolgCooldown(false) == 900 && ArcherRules.caladbolgCooldown(true) == 1200, "Caladbolg 45 s / NPC 60 s");
        // Rho Aias.
        check(ArcherRules.petalsAfter(0) == 7 && ArcherRules.petalsAfter(4) == 7 && ArcherRules.petalsAfter(5) == 6 && ArcherRules.petalsAfter(35) == 0,
            "seven petals, five projectiles each");
        check(ArcherRules.beamPetalCost(true, false) == 4 && ArcherRules.beamPetalCost(false, true) == 3 && ArcherRules.beamPetalCost(false, false) == 2,
            "beams tear 4 / 3 / 2 petals");
        check(ArcherRules.RHO_AIAS_TIME == 120 && ArcherRules.RHO_AIAS == 1200, "6 s, 60 s cooldown");
        // The marble and the domain clash.
        check(ArcherRules.ubwAfterClash(500, true) == 440 && ArcherRules.ubwAfterClash(300, false) == 300 && ArcherRules.ubwAfterClash(300, true) == 340,
            "clash: at most 20 s, 2 s more for the first");
        check(ArcherRules.UBW_TIME == 600 && ArcherRules.UBW_RADIUS == 64.0 && ArcherRules.ubwCooldown(false) == 1200
            && ArcherRules.ubwCooldown(true) == 1200, "30 s, radius 64 and 60 s like a JJK domain");
        check(ArcherRules.UBW_CHANT_START < ArcherRules.UBW_CHANT && ArcherRules.UBW_CHANT == 60, "the aria takes 3 s");
        // The twin-blade combo.
        int step = -1;
        int[] seen = new int[ArcherRules.COMBO_STEPS];
        for (int i = 0; i < 12; ++i) {
            step = ArcherRules.nextStep(step, 300L);
            ++seen[step];
        }
        boolean all = true;
        for (int n : seen) all &= n == 2;
        check(all, "the six strokes come in turn");
        check(ArcherRules.nextStep(3, ArcherRules.TWIN_RESET_MS + 1) == ArcherRules.STEP_RIGHT, "after a pause the combo starts over");
        check(ArcherRules.strokeHits(ArcherRules.STEP_CROSS) == 2 && ArcherRules.secondHit(ArcherRules.STEP_CROSS) == 0.6f, "the cross strikes twice");
        check(ArcherRules.strokeAllRound(ArcherRules.STEP_SPIN) && ArcherRules.sweepShare(ArcherRules.STEP_SPIN) == 0.7f, "the spin catches all round");
        check(ArcherRules.strokeBonus(ArcherRules.STEP_CHOP) > 0.0f && ArcherRules.strokeHand(ArcherRules.STEP_LEFT) == 1, "chop bites, backhand is the off hand");
        for (int s = 0; s <= ArcherRules.STEP_OVEREDGE; ++s) check(ArcherRules.strokeMs(s) >= 250L && ArcherRules.strokeMs(s) <= 500L, "stroke time " + s);
        // Projection and the replica.
        check(ArcherRules.PROJECTION_LIFETIME == 1200 && ArcherRules.PROJECTION_MAX == 3 && ArcherRules.ARSENAL_CAP == 27, "copies 60 s, 3 at once, 27 kept");
        check(ArcherRules.replicaWidth(ArcherRules.REPLICA_CHARGE) == 2.5f && ArcherRules.replicaWidth(1000) == 4.5f, "replica width 2.5-4.5");
        check(ArcherRules.REPLICA_RANGE < KingRules.EXCALIBUR_RANGE && ArcherRules.REPLICA_NPC_LOSS < KingRules.NPC_PHANTASM_LOSS, "the replica falls short");
    }

    private static void judgement() {
        check(JudgementRules.outcome(Weapon.EXCALIBUR, Side.VANILLA, true, true) == Outcome.INSTANT_DEATH, "Excalibur kills vanilla creatures");
        check(JudgementRules.outcome(Weapon.EXCALIBUR, Side.PLAYER, false, true) == Outcome.INSTANT_DEATH, "Excalibur kills an ordinary player");
        check(JudgementRules.outcome(Weapon.EXCALIBUR, Side.VANILLA_BOSS, true, true) == Outcome.INSTANT_DEATH, "Excalibur kills bosses");
        check(JudgementRules.outcome(Weapon.EXCALIBUR, Side.VANILLA_BOSS, true, false) == Outcome.HEAVY_DAMAGE, "boss rule off: 1000");
        check(JudgementRules.outcome(Weapon.EXCALIBUR, Side.MAHORAGA, true, true) == Outcome.WHEEL_CANNOT_TURN, "Excalibur kills Mahoraga");
        check(JudgementRules.outcome(Weapon.EA, Side.MAHORAGA, true, true) == Outcome.WHEEL_CANNOT_TURN, "Ea kills Mahoraga");
        check(JudgementRules.outcome(Weapon.EXCALIBUR, Side.GOJO, true, true) == Outcome.CRIPPLE, "Excalibur cripples the Gojo NPC");
        check(JudgementRules.outcome(Weapon.EXCALIBUR, Side.GOJO, false, true) == Outcome.CRIPPLE, "... and a Gojo player");
        check(JudgementRules.outcome(Weapon.EA, Side.GOJO, true, true) == Outcome.NPC_BLOW, "Ea on the Gojo NPC: 190 (through Infinity)");
        check(JudgementRules.outcome(Weapon.EA, Side.GOJO, false, true) == Outcome.CHARACTER_DAMAGE, "Ea on a Gojo player: 90-100");
        for (Side s : new Side[]{Side.SUKUNA, Side.HERO, Side.KNIGHT, Side.ARCHER}) {
            for (Weapon w : Weapon.values()) {
                check(JudgementRules.outcome(w, s, true, true) == Outcome.NPC_BLOW, w + " on the " + s + " NPC: 190");
                check(JudgementRules.outcome(w, s, false, true) == Outcome.CHARACTER_DAMAGE, w + " on a " + s + " player: 90-100");
            }
        }
        for (Side s : new Side[]{Side.VANILLA, Side.VANILLA_BOSS, Side.OTHER_MOD}) {
            check(JudgementRules.outcome(Weapon.EA, s, true, true) == Outcome.HEAVY_DAMAGE, "Ea on " + s + ": 1000");
        }
        check(JudgementRules.outcome(Weapon.EA, Side.PLAYER, false, true) == Outcome.CHARACTER_DAMAGE, "Ea on a player: 90-100");
        check(JudgementRules.outcome(Weapon.EXCALIBUR, Side.OTHER_MOD, true, true) == Outcome.HEAVY_DAMAGE, "other mods' creatures: 1000, no instakill");
        // EMIYA's Caladbolg II and a projected Excalibur.
        check(JudgementRules.outcome(Weapon.CALADBOLG, Side.MAHORAGA, true, true) == Outcome.WHEEL_CANNOT_TURN, "Caladbolg kills Mahoraga");
        check(JudgementRules.outcome(Weapon.EXCALIBUR_REPLICA, Side.MAHORAGA, true, true) == Outcome.HEAVY_DAMAGE, "a replica does not");
        check(JudgementRules.outcome(Weapon.CALADBOLG, Side.VANILLA_BOSS, true, true) == Outcome.HEAVY_DAMAGE
            && JudgementRules.outcome(Weapon.EXCALIBUR_REPLICA, Side.VANILLA, true, true) == Outcome.HEAVY_DAMAGE, "no instant death but Excalibur's");
        check(JudgementRules.outcome(Weapon.CALADBOLG, Side.GOJO, true, true) == Outcome.NPC_BLOW
            && JudgementRules.outcome(Weapon.CALADBOLG, Side.PLAYER, false, true) == Outcome.CHARACTER_DAMAGE, "Caladbolg: NPC blow / players");
        check(JudgementRules.npcLoss(Weapon.EA) == 190.0f && JudgementRules.npcLoss(Weapon.CALADBOLG) == 120.0f
            && JudgementRules.npcLoss(Weapon.EXCALIBUR_REPLICA) == 95.0f, "NPC losses 190 / 120 / 95");
        check(JudgementRules.mobDamage(Weapon.EXCALIBUR) == 1000.0f && JudgementRules.mobDamage(Weapon.CALADBOLG) == 600.0f
            && JudgementRules.mobDamage(Weapon.EXCALIBUR_REPLICA) == 500.0f, "creature damage 1000 / 600 / 500");
        check(JudgementRules.characterDamage(Weapon.CALADBOLG, 0.0f) == 60.0f && JudgementRules.characterDamage(Weapon.CALADBOLG, 1.0f) == 70.0f
            && JudgementRules.characterDamage(Weapon.EXCALIBUR_REPLICA, 1.0f) == 50.0f, "on players 60-70 / 45-50");
        check(JudgementRules.piercesInfinity(Weapon.CALADBOLG) && !JudgementRules.piercesInfinity(Weapon.EXCALIBUR_REPLICA), "Caladbolg pierces Infinity, a replica not");
        check(JudgementRules.killsMahoraga(Weapon.EA) && !JudgementRules.killsMahoraga(Weapon.EXCALIBUR_REPLICA), "only a replica is adapted to");
        check(JudgementRules.worthy(Side.ARCHER, false), "the archer is worthy");
        check(JudgementRules.piercesInfinity(Weapon.EA) && JudgementRules.piercesInfinity(Weapon.EXCALIBUR), "both pierce Infinity");
        check(JudgementRules.worthy(Side.HERO, false) && JudgementRules.worthy(Side.KNIGHT, false), "kings are worthy");
        check(JudgementRules.worthy(Side.MAHORAGA, false) && JudgementRules.worthy(Side.VANILLA_BOSS, false), "Mahoraga and bosses are worthy");
        check(JudgementRules.worthy(Side.GOJO, true) && !JudgementRules.worthy(Side.GOJO, false), "sorcerers worthy at stage V only");
        check(!JudgementRules.worthy(Side.VANILLA, true) && !JudgementRules.worthy(Side.PLAYER, true), "mobs and plain players are not worthy");
    }

    private static void ai() {
        int t = KingAiRules.ARROGANT;
        check(KingAiRules.gilTierAfterHit(t, 5, false, false, 0.99f, true, false) == KingAiRules.ARROGANT, "a scratch keeps him arrogant");
        check(KingAiRules.gilTierAfterHit(t, 45, false, false, 0.9f, true, false) == KingAiRules.DISPLEASED, "40 damage: displeased");
        check(KingAiRules.gilTierAfterHit(t, 1, true, false, 0.99f, true, false) == KingAiRules.DISPLEASED, "hit by the unworthy: displeased");
        check(KingAiRules.gilTierAfterHit(t, 1, false, true, 0.99f, true, false) == KingAiRules.SERIOUS, "a worthy foe struck first: serious");
        check(KingAiRules.gilTierAfterHit(t, 1, false, false, 0.4f, true, false) == KingAiRules.SERIOUS, "below half: serious");
        check(KingAiRules.gilTierAfterHit(t, 100, true, false, 0.3f, false, false) == KingAiRules.DISPLEASED, "ordinary mobs: at most displeased");
        check(KingAiRules.gilTierAfterHit(t, 1, false, false, 0.99f, false, true) == KingAiRules.SERIOUS, "Saber: serious at once");
        check(!KingAiRules.gilWantsEa(KingAiRules.ARROGANT, true, 0.2f, 0, false, false, true, 0, false, false), "no Ea while arrogant");
        check(!KingAiRules.gilWantsEa(KingAiRules.SERIOUS, false, 0.1f, 999, true, true, true, 0, false, false), "never Ea on the unworthy");
        check(KingAiRules.gilWantsEa(KingAiRules.SERIOUS, true, 0.3f, 0, false, false, true, 0, false, false), "serious + worthy + below a third: Ea");
        check(!KingAiRules.gilWantsEa(KingAiRules.SERIOUS, true, 0.5f, 0, false, false, true, 0, false, false), "serious + worthy but no reason: no Ea");
        check(KingAiRules.gilWantsEa(KingAiRules.SERIOUS, true, 0.9f, 200, false, false, true, 0, false, false), "stopped by Infinity 10 s: Ea");
        check(!KingAiRules.gilWantsEa(KingAiRules.SERIOUS, true, 0.9f, 199, false, false, true, 0, false, false), "less than 10 s: not yet");
        check(KingAiRules.gilWantsEa(KingAiRules.SERIOUS, true, 0.9f, 0, true, false, true, 0, false, false), "Mahoraga adapted: Ea");
        check(KingAiRules.gilWantsEa(KingAiRules.SERIOUS, true, 0.9f, 0, false, true, true, 0, false, false), "enemy domain: Ea");
        check(!KingAiRules.gilWantsEa(KingAiRules.SERIOUS, true, 0.1f, 0, false, false, false, 0, false, false), "Ea on cooldown");
        check(!KingAiRules.gilWantsEa(KingAiRules.SERIOUS, true, 0.1f, 0, false, false, true, 2, false, false), "at most twice per fight");
        check(KingAiRules.gilWantsEa(KingAiRules.ARROGANT, true, 0.45f, 0, false, false, true, 0, true, false), "vs Saber: below half is enough");
        check(KingAiRules.gilWantsEa(KingAiRules.ARROGANT, true, 0.9f, 0, false, false, true, 0, true, true), "vs Saber: hit by Excalibur is enough");
        check(!KingAiRules.gilWantsEa(KingAiRules.SERIOUS, true, 0.1f, 0, false, false, true, 2, true, true), "vs Saber: still twice at most");
        check(KingAiRules.gilGates(KingAiRules.ARROGANT, 0.0f) == 1 && KingAiRules.gilGates(KingAiRules.ARROGANT, 1.0f) == 3, "arrogant: 1-3 treasures");
        check(KingAiRules.gilGates(KingAiRules.DISPLEASED, 0.0f) == 8 && KingAiRules.gilGates(KingAiRules.DISPLEASED, 1.0f) == 15, "displeased: 8-15");
        check(KingAiRules.gilGates(KingAiRules.SERIOUS, 0.0f) == 30 && KingAiRules.gilGates(KingAiRules.SERIOUS, 1.0f) == 60, "serious: 30-60");
        check(KingAiRules.gilChainCombo(KingAiRules.SERIOUS, true, true) && KingAiRules.gilChainCombo(KingAiRules.DISPLEASED, true, true),
            "a strong foe: chains and treasures together whenever the chains are ready");
        check(!KingAiRules.gilChainCombo(KingAiRules.ARROGANT, true, true), "not while still arrogant");
        check(!KingAiRules.gilChainCombo(KingAiRules.SERIOUS, false, true), "not for ordinary foes");
        check(!KingAiRules.gilChainCombo(KingAiRules.SERIOUS, true, false), "not while the chains cool down");
        check(KingAiRules.COMBO_BIND_COOLDOWN < KingRules.ENKIDU_BIND, "against the strong he reaches for the chains more often");
        check(KingAiRules.gilOpensWithVolley(false, true, true), "a strong foe: he opens with the full volley");
        check(!KingAiRules.gilOpensWithVolley(true, true, true), "once a fight");
        check(!KingAiRules.gilOpensWithVolley(false, false, true), "not for ordinary foes");
        check(!KingAiRules.gilOpensWithVolley(false, true, false), "not while the volley cools down");
        // EMIYA.
        check(KingAiRules.emiyaSwords(5.0, false) && !KingAiRules.emiyaSwords(7.0, false) && KingAiRules.emiyaSwords(8.0, true)
            && !KingAiRules.emiyaSwords(10.0, true), "blades inside 6, the bow beyond 9 (no flicker between)");
        check(KingAiRules.emiyaWantsRhoAias(true, 5, 0, false) && KingAiRules.emiyaWantsRhoAias(true, 0, 3, false)
            && KingAiRules.emiyaWantsRhoAias(true, 0, 0, true) && !KingAiRules.emiyaWantsRhoAias(false, 9, 9, true)
            && !KingAiRules.emiyaWantsRhoAias(true, 2, 1, false), "Rho Aias against volleys, flights and beams");
        check(KingAiRules.emiyaWantsCaladbolg(true, false, false, 0, 0, 1.0f, true), "Caladbolg at a boss at once");
        check(!KingAiRules.emiyaWantsCaladbolg(true, false, true, 100, 0, 1.0f, false) && KingAiRules.emiyaWantsCaladbolg(true, false, true, 201, 0, 1.0f, false),
            "at the strong after 10 s");
        check(!KingAiRules.emiyaWantsCaladbolg(true, true, true, 999, 999, 0.1f, true), "never at a neutral animal");
        check(KingAiRules.emiyaWantsUbw(true, false, true, true, 101, 1.0f, false, 0), "the marble against the King of Heroes");
        check(!KingAiRules.emiyaWantsUbw(true, true, true, true, 999, 0.1f, true, 99), "once a fight");
        check(KingAiRules.emiyaWantsUbw(true, false, false, true, 0, 0.4f, false, 0) && !KingAiRules.emiyaWantsUbw(true, false, false, false, 999, 0.1f, false, 0),
            "against the worthy when hurt, never against the unworthy alone");
        check(KingAiRules.emiyaWantsUbw(true, false, false, false, 0, 1.0f, false, KingAiRules.CROWD + 2), "or against a horde");
        check(KingAiRules.emiyaWantsReplica(true, true, true, 12.0) && !KingAiRules.emiyaWantsReplica(false, true, true, 30.0), "the replica once he has seen Excalibur");
        check(KingAiRules.emiyaTier(1.0f, true) == KingAiRules.EMIYA_SERIOUS && KingAiRules.emiyaTier(0.9f, false) == KingAiRules.EMIYA_CALM, "his temper");
        check(KingAiRules.saberTier(1.0f, false) == KingAiRules.COURTESY, "courtesy by default");
        check(KingAiRules.saberTier(1.0f, true) == KingAiRules.FULL_POWER, "strong foe: full power");
        check(KingAiRules.saberTier(0.6f, false) == KingAiRules.FULL_POWER, "below 70%: full power");
        check(KingAiRules.saberTier(0.2f, true) == KingAiRules.LAST_STAND, "below 25%: last stand");
        check(!KingAiRules.saberWantsExcalibur(true, true, true, 999, true, true, 0.1f, 20, true, true), "never on neutral animals");
        check(!KingAiRules.saberWantsExcalibur(false, false, true, 999, true, true, 0.1f, 20, false, false), "not on cooldown");
        check(KingAiRules.saberWantsExcalibur(true, false, false, 0, false, true, 1.0f, 0, false, false), "bosses: at first sight");
        check(KingAiRules.saberWantsExcalibur(true, false, true, 161, false, false, 1.0f, 0, false, false), "strong foe after 8 s");
        check(!KingAiRules.saberWantsExcalibur(true, false, true, 100, false, false, 1.0f, 0, false, false), "strong foe before 8 s: not yet");
        check(KingAiRules.saberWantsExcalibur(true, false, false, 0, true, false, 1.0f, 0, false, false), "foe's ultimate: answer at once");
        check(KingAiRules.saberWantsExcalibur(true, false, false, 0, false, false, 0.45f, 0, false, false), "below half health");
        check(KingAiRules.saberWantsExcalibur(true, false, false, 0, false, false, 1.0f, 8, false, false), "8 hostiles around");
        check(!KingAiRules.saberWantsExcalibur(true, false, false, 0, false, false, 1.0f, 7, false, false), "7 hostiles: not yet");
        check(KingAiRules.saberWantsExcalibur(true, false, false, 0, false, false, 1.0f, 0, true, false), "Avalon's counter");
        check(KingAiRules.clash(true) == KingAiRules.ClashResult.AVALON_BLOCKS, "Avalon ready: blocks Ea");
        check(KingAiRules.clash(false) == KingAiRules.ClashResult.EA_OVERPOWERS, "Avalon cooling: Ea overpowers");
        double[] east = {1, 0, 0}, west = {-1, 0, 0}, north = {0, 0, -1};
        check(KingAiRules.beamsMeet(east, west, 0.0, 2.0, 2.5, 20), "head-on beams meet");
        check(!KingAiRules.beamsMeet(east, east, 0.0, 2.0, 2.5, 20), "same direction: no clash");
        check(!KingAiRules.beamsMeet(east, west, 10.0, 2.0, 2.5, 20), "passing far apart: no clash");
        check(!KingAiRules.beamsMeet(east, west, 0.0, 2.0, 2.5, 61), "more than 3 s apart: no clash");
        check(!KingAiRules.beamsMeet(east, north, 0.0, 2.0, 2.5, 0), "at right angles: no clash");
    }

    private static void state() {
        KingState s = KingState.fresh();
        s.king = KingRules.KNIGHT;
        s.lockedFrom = KingRules.HERO;
        s.lockUntil = 1234L;
        s.gold = 42.0f;
        s.cooldown(Skills.EXCALIBUR, 100L, 1200);
        s.revealedUntil = 77L;
        s.flightGranted = true;
        var json = KingState.CODEC.encodeStart(JsonOps.INSTANCE, s).getOrThrow();
        KingState back = KingState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        check(back.king == KingRules.KNIGHT && back.lockedFrom == KingRules.HERO && back.lockUntil == 1234L, "state round trip: king and lock");
        check(back.gold == 42.0f && back.cooldownEnd(Skills.EXCALIBUR) == 1300L, "state round trip: gold and cooldowns");
        check(back.revealedUntil == 77L && back.flightGranted, "state round trip: windows and flight");
        KingState old = KingState.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
        check(old.king == KingRules.NONE && old.gold < 0.0f, "empty save loads as a fresh state");
        check(back.ready(Skills.EXCALIBUR, 1300L) && !back.ready(Skills.EXCALIBUR, 1299L), "cooldown boundaries");
        check(back.cooldownLeft(Skills.EXCALIBUR, 1000L) == 300, "cooldown left");
        check(back.cooldownLeft(Skills.EXCALIBUR, 5000L) == 0, "cooldown never negative");
        Set<String> keys = new HashSet<>(Set.of(KingSync.KEYS));
        for (String k : Skills.HERO_HUD) check(keys.contains(k), "HUD key synced: " + k);
        for (String k : Skills.KNIGHT_HUD) check(keys.contains(k), "HUD key synced: " + k);
        for (String k : Skills.ARCHER_HUD) check(keys.contains(k), "HUD key synced: " + k);
        check(Skills.hud(KingRules.ARCHER) == Skills.ARCHER_HUD && Skills.hud(KingRules.NONE).length == 0, "HUD rows per spirit");
    }

    private static JsonObject json(Path p) throws IOException {
        return JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static void resources(Path res) throws IOException {
        Path a = res.resolve("assets/fatekings");
        JsonObject zh = json(a.resolve("lang/zh_cn.json"));
        JsonObject en = json(a.resolve("lang/en_us.json"));
        check(zh.keySet().equals(en.keySet()), "zh_cn and en_us have the same keys");
        String[] items = {"golden_crown", "golden_chestplate", "golden_greaves", "golden_sabatons", "gate_of_babylon", "bab_ilu", "ea",
            "enkidu", "vimana", "treasury_elixir", "knight_ribbon", "knight_breastplate", "knight_skirt", "knight_boots", "excalibur",
            "warhorse", "knight_barding", "gilgamesh_spawn_egg", "artoria_spawn_egg", "grail_mud", "shroud_headpiece", "shroud_coat",
            "shroud_leggings", "shroud_boots", "black_bow", "kanshou", "bakuya", "unlimited_blade_works", "excalibur_replica", "emiya_spawn_egg"};
        for (String id : items) {
            check(Files.exists(a.resolve("items/" + id + ".json")), "item model definition: " + id);
            check(zh.has("item.fatekings." + id), "item name: " + id);
        }
        for (String tex : new String[]{"excalibur_air", "excalibur_revealed", "excalibur_release", "ea", "ea_charging"}) {
            check(Files.exists(a.resolve("textures/item/" + tex + ".png")), "texture: " + tex);
            check(Files.exists(a.resolve("models/item/" + tex + ".json")), "model: " + tex);
        }
        check(Files.exists(a.resolve("textures/item/excalibur_air.png.mcmeta")), "Invisible Air is animated");
        check(Files.exists(a.resolve("textures/item/excalibur_release.png.mcmeta")), "the true name glow is animated");
        String excal = Files.readString(a.resolve("items/excalibur.json"));
        check(excal.contains("using_item") && excal.contains("\"air\"") && excal.contains("excalibur_revealed"), "Excalibur switches three states");
        for (String tex : new String[]{"black_bow", "black_bow_pulling_0", "black_bow_pulling_1", "black_bow_caladbolg", "kanshou", "bakuya",
                "sword_arrow", "caladbolg_arrow", "unlimited_blade_works"}) {
            check(Files.exists(a.resolve("textures/item/" + tex + ".png")), "texture: " + tex);
            check(Files.exists(a.resolve("models/item/" + tex + ".json")), "model: " + tex);
        }
        for (String m : new String[]{"kanshou_overedge", "bakuya_overedge"}) check(Files.exists(a.resolve("models/item/" + m + ".json")), "Overedge model: " + m);
        for (String id : new String[]{"sword_arrow", "caladbolg_arrow"}) check(Files.exists(a.resolve("items/" + id + ".json")), "look of " + id);
        String bow = Files.readString(a.resolve("items/black_bow.json"));
        check(bow.contains("use_duration") && bow.contains("black_bow_caladbolg"), "the bow draws and nocks the spiral sword");
        check(Files.readString(a.resolve("items/kanshou.json")).contains("overedge"), "Kanshou grows for Crane Wing");
        check(Files.readString(a.resolve("items/excalibur_replica.json")).contains("tints"), "the replica is tinted");
        for (String tex : new String[]{"rho_aias_petal", "twin_trail", "ubw_sky", "ubw_gear"}) {
            check(Files.exists(a.resolve("textures/misc/" + tex + ".png")), "effect texture: " + tex);
        }
        String unprojectable = Files.readString(res.resolve("data/fatekings/tags/item/unprojectable.json"));
        for (String id : new String[]{"ea", "bab_ilu", "gate_of_babylon", "enkidu", "vimana", "unlimited_blade_works", "black_bow", "excalibur_replica"}) {
            check(unprojectable.contains("\"fatekings:" + id + "\""), "cannot be projected: " + id);
        }
        check(!unprojectable.contains("excalibur\"") && !unprojectable.contains("kanshou"), "Excalibur (as a replica) and the twin blades can");
        for (String eq : new String[]{"golden_regalia", "knight_regalia", "red_shroud"}) {
            check(Files.exists(a.resolve("equipment/" + eq + ".json")), "equipment asset: " + eq);
            check(Files.exists(a.resolve("textures/entity/equipment/humanoid/" + eq + ".png")), "armor layer: " + eq);
            check(Files.exists(a.resolve("textures/entity/equipment/humanoid_leggings/" + eq + ".png")), "leggings layer: " + eq);
        }
        check(Files.exists(a.resolve("textures/entity/equipment/horse_body/knight_barding.png")), "horse barding layer");
        check(Files.exists(a.resolve("textures/entity/gilgamesh.png")) && Files.exists(a.resolve("textures/entity/artoria.png"))
            && Files.exists(a.resolve("textures/entity/emiya.png")), "NPC skins");
        JsonObject sounds = json(a.resolve("sounds.json"));
        for (Voice v : Voice.values()) {
            check(zh.has(v.key()) && en.has(v.key()), "subtitle for " + v);
            if (v.clip == null) continue;
            check(sounds.has("voice." + v.clip), "sounds.json entry for " + v.clip);
            check(Files.exists(a.resolve("sounds/voice/" + v.clip + ".ogg")), "voice file " + v.clip + ".ogg");
            check(v.seconds > 0.5f && v.seconds < 8.0f, "sane duration " + v.clip);
        }
        for (String dmg : new String[]{"enuma_elish_mob", "excalibur_mob"}) {
            JsonObject type = json(res.resolve("data/fatekings/damage_type/" + dmg + ".json"));
            String base = dmg.substring(0, dmg.length() - "_mob".length());
            check(("fatekings." + base).equals(type.get("message_id").getAsString()), dmg + " shares the death messages of " + base);
        }
        String armour = Files.readString(res.resolve("data/minecraft/tags/damage_type/bypasses_armor.json"));
        check(armour.contains("\"fatekings:enuma_elish\"") && armour.contains("\"fatekings:excalibur\"") && !armour.contains("_mob"),
            "armour stops the 1000 on creatures, not the blows on characters");
        for (String dmg : new String[]{"caladbolg_mob", "excalibur_replica_mob"}) {
            JsonObject type = json(res.resolve("data/fatekings/damage_type/" + dmg + ".json"));
            check(("fatekings." + dmg.substring(0, dmg.length() - 4)).equals(type.get("message_id").getAsString()), dmg + " shares its death messages");
        }
        check(armour.contains("\"fatekings:caladbolg\"") && armour.contains("\"fatekings:excalibur_replica\"") && !armour.contains("ubw_sword"),
            "Caladbolg and the replica pierce armour on characters; the marble's swords do not");
        for (String dmg : new String[]{"enuma_elish", "excalibur", "excalibur_judgement", "heavens_chain", "mana_burst", "sword_qi", "caladbolg",
                "excalibur_replica", "ubw_sword", "thrown_blade"}) {
            check(Files.exists(res.resolve("data/fatekings/damage_type/" + dmg + ".json")), "damage type " + dmg);
            check(zh.has("death.attack.fatekings." + dmg) && zh.has("death.attack.fatekings." + dmg + ".player"), "death messages " + dmg);
        }
        String inv = Files.readString(res.resolve("data/minecraft/tags/damage_type/bypasses_invulnerability.json"));
        check(inv.contains("excalibur_judgement") && !inv.contains("\"fatekings:excalibur\"") && !inv.contains("enuma_elish") && !inv.contains("_mob"),
            "only the judgement type ignores invulnerability (the character types keep each side's share)");
        for (String k : Skills.HERO_HUD) check(zh.has("fatekings.skill." + k), "skill name " + k);
        for (String k : Skills.KNIGHT_HUD) check(zh.has("fatekings.skill." + k), "skill name " + k);
        for (String k : Skills.ARCHER_HUD) check(zh.has("fatekings.skill." + k), "skill name " + k);
        for (Voice.Speaker s : Voice.Speaker.values()) check(zh.has("fatekings.speaker." + s.name().toLowerCase(Locale.ROOT)), "speaker name " + s);
        for (String k : new String[]{"fatekings.title.archer", "fatekings.hud.archer", "entity.fatekings.emiya", "fatekings.screen.arsenal"}) {
            check(zh.has(k), "lang: " + k);
        }
        for (JudgementRules.Side s : JudgementRules.Side.values()) check(zh.has("fatekings.side." + s.name().toLowerCase(Locale.ROOT)), "side name " + s);
        JsonObject mod = json(res.resolve("fabric.mod.json"));
        check("fatekings".equals(mod.get("id").getAsString()), "mod id");
        check(mod.get("name").getAsString().contains("英灵") && zh.get("itemGroup.fatekings.main").getAsString().contains("英灵"), "Fate · 英灵");
        check(mod.getAsJsonObject("suggests").has("sukuna"), "the Gojo x Sukuna mod is optional");
        check(!mod.getAsJsonObject("depends").has("sukuna"), "no hard dependency on it");
        JsonObject compat = json(res.resolve("fatekings.compat.mixins.json"));
        check(!compat.get("required").getAsBoolean() && compat.has("plugin"), "compat mixins are optional and gated");
    }
}
