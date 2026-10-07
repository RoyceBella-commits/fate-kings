package cn.blockforge.fatekings.gametest;

import cn.blockforge.fatekings.combat.DamageShare;
import cn.blockforge.fatekings.combat.Judgement;
import cn.blockforge.fatekings.combat.JudgementRules.Weapon;
import cn.blockforge.fatekings.combat.Sides;
import cn.blockforge.fatekings.combat.Terrain;
import cn.blockforge.fatekings.compat.JjkCompat;
import cn.blockforge.fatekings.config.FateConfig;
import cn.blockforge.fatekings.entity.GatePortalEntity;
import cn.blockforge.fatekings.entity.SwordQiEntity;
import cn.blockforge.fatekings.hero.Enkidu;
import cn.blockforge.fatekings.hero.GateOfBabylon;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.KingState;
import cn.blockforge.fatekings.king.Kings;
import cn.blockforge.fatekings.king.Skills;
import cn.blockforge.fatekings.npc.ArtoriaEntity;
import cn.blockforge.fatekings.npc.GilgameshEntity;
import cn.blockforge.fatekings.npc.KingAiRules;
import cn.blockforge.fatekings.registry.FateDamage;
import cn.blockforge.fatekings.registry.FateEffects;
import cn.blockforge.fatekings.registry.FateEntities;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Server game tests of the hard rules. The Gojo x Sukuna ones run only when that mod is loaded
 * (it is, in this project's development runs).
 */
public class FateServerTests {
    private static <T extends Mob> T still(T mob) {
        mob.setNoAi(true);
        mob.setNoGravity(true);
        return mob;
    }

    @SuppressWarnings("unchecked")
    private static LivingEntity spawnJjk(GameTestHelper h, Identifier id, double x, double y, double z) {
        EntityType<Entity> type = (EntityType<Entity>)BuiltInRegistries.ENTITY_TYPE.getValue(id);
        Entity e = h.spawn(type, new Vec3(x, y, z));
        if (e instanceof Mob m) still(m);
        return (LivingEntity)e;
    }

    @GameTest
    public void excaliburKillsVanillaCreatures(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ArtoriaEntity saber = still(h.spawn(FateEntities.ARTORIA, 1.5f, 2.0f, 1.5f));
        LivingEntity zombie = still(h.spawn(EntityTypes.ZOMBIE, 3.5f, 2.0f, 3.5f));
        LivingEntity villager = still(h.spawn(EntityTypes.VILLAGER, 5.5f, 2.0f, 3.5f));
        LivingEntity warden = still(h.spawn(EntityTypes.WARDEN, 3.5f, 2.0f, 6.0f));
        WitherBoss wither = still(h.spawn(EntityTypes.WITHER, 6.0f, 3.0f, 6.0f));
        wither.makeInvulnerable();
        for (LivingEntity t : new LivingEntity[]{zombie, villager, warden, wither}) {
            Judgement.strike(level, saber, null, t, Weapon.EXCALIBUR, 1.0f);
            h.assertTrue(t.isDeadOrDying(), "Excalibur must kill " + t.getType());
        }
        h.succeed();
    }

    @GameTest
    public void excaliburKillsTheDragon(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ArtoriaEntity saber = still(h.spawn(FateEntities.ARTORIA, 1.5f, 2.0f, 1.5f));
        LivingEntity dragon = still(h.spawn(EntityTypes.ENDER_DRAGON, 4.0f, 4.0f, 4.0f));
        Judgement.strike(level, saber, null, dragon, Weapon.EXCALIBUR, 1.0f);
        h.assertTrue(dragon.isDeadOrDying() || dragon.getHealth() <= 0.0f, "Excalibur must kill the ender dragon");
        dragon.discard();
        h.succeed();
    }

    @GameTest
    public void eaFlattensCreatures(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        GilgameshEntity gil = still(h.spawn(FateEntities.GILGAMESH, 1.5f, 2.0f, 1.5f));
        LivingEntity golem = still(h.spawn(EntityTypes.IRON_GOLEM, 4.5f, 2.0f, 4.5f));
        Judgement.strike(level, gil, null, golem, Weapon.EA, 1.0f);
        h.assertTrue(golem.isDeadOrDying(), "1000 damage must kill an iron golem");
        h.succeed();
    }

    /** A creature with the most health vanilla allows, so the blow can be measured. */
    private static LivingEntity sturdy(GameTestHelper h, float x, float z, double armour) {
        LivingEntity golem = still(h.spawn(EntityTypes.IRON_GOLEM, x, 2.0f, z));
        golem.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024.0);
        golem.getAttribute(Attributes.ARMOR).setBaseValue(armour);
        golem.setHealth(1024.0f);
        return golem;
    }

    @GameTest
    public void creaturesTakeAThousandAndArmourCounts(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        GilgameshEntity gil = still(h.spawn(FateEntities.GILGAMESH, 1.5f, 2.0f, 1.5f));
        LivingEntity bare = sturdy(h, 5.5f, 1.5f, 0.0);
        LivingEntity armoured = sturdy(h, 5.5f, 5.5f, 20.0);
        Judgement.strike(level, gil, null, bare, Weapon.EA, 1.0f);
        Judgement.strike(level, gil, null, armoured, Weapon.EA, 1.0f);
        float bareLoss = 1024.0f - bare.getHealth(), armouredLoss = 1024.0f - armoured.getHealth();
        h.assertTrue(Math.abs(bareLoss - 1000.0f) < 0.01f, "Ea on a creature: 1000 (" + bareLoss + ")");
        h.assertTrue(armoured.isAlive() && armouredLoss < bareLoss - 50.0f && armouredLoss > 500.0f, "armour takes its part (" + armouredLoss + ")");
        h.succeed();
    }

    private static void assertLoss(GameTestHelper h, LivingEntity t, float before, String what) {
        float loss = before - t.getHealth();
        float want = Math.min(KingRules.NPC_PHANTASM_LOSS, before);
        h.assertTrue(Math.abs(loss - want) < 0.01f, what + ": " + want + " health off (" + loss + ")");
    }

    @GameTest
    public void phantasmsTake190FromKingNpcs(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        GilgameshEntity gil = still(h.spawn(FateEntities.GILGAMESH, 1.5f, 2.0f, 1.5f));
        ArtoriaEntity saber = still(h.spawn(FateEntities.ARTORIA, 6.5f, 2.0f, 1.5f));
        GilgameshEntity gil2 = still(h.spawn(FateEntities.GILGAMESH, 1.5f, 2.0f, 6.5f));
        Kings.of(saber).cooldown(Skills.AVALON_DOME, level.getGameTime(), KingRules.AVALON_DOME); // no dome for this one
        Judgement.strike(level, gil, null, saber, Weapon.EA, 1.0f);
        assertLoss(h, saber, 200.0f, "Ea on Artoria");
        Judgement.strike(level, saber, null, gil, Weapon.EXCALIBUR, 1.0f);
        assertLoss(h, gil, 200.0f, "Excalibur on Gilgamesh");
        for (LivingEntity k : new LivingEntity[]{saber, gil}) {
            h.assertTrue(Kings.of(k).gold == KingRules.NPC_GOLD_HP, "gold hearts do not soak it (" + k.getType() + ")");
        }
        // A player king: the x2.5 on NPCs is folded in the same way.
        Player knight = h.makeMockPlayer(GameType.SURVIVAL);
        Kings.of(knight).king = KingRules.KNIGHT;
        Judgement.strike(level, knight, null, gil2, Weapon.EXCALIBUR, 1.0f);
        assertLoss(h, gil2, 200.0f, "a knight player's Excalibur on Gilgamesh");
        h.succeed();
    }

    @GameTest
    public void kingsTakeOneOrTenPercent(GameTestHelper h) {
        GilgameshEntity gil = still(h.spawn(FateEntities.GILGAMESH, 1.5f, 2.0f, 1.5f));
        ArtoriaEntity saber = still(h.spawn(FateEntities.ARTORIA, 5.5f, 2.0f, 5.5f));
        LivingEntity zombie = still(h.spawn(EntityTypes.ZOMBIE, 3.5f, 2.0f, 1.5f));
        DamageSource fromZombie = zombie.damageSources().mobAttack(zombie);
        DamageSource fromKing = saber.damageSources().mobAttack(saber);
        h.assertTrue(Math.abs(DamageShare.apply(gil, fromZombie, 100.0f) - 1.0f) < 1.0E-3, "vanilla hits: 1%");
        h.assertTrue(Math.abs(DamageShare.apply(gil, fromKing, 100.0f) - 10.0f) < 1.0E-3, "another king: 10%");
        h.assertTrue(Math.abs(DamageShare.apply(saber, gil.damageSources().mobAttack(gil), 100.0f) - 10.0f) < 1.0E-3, "king on king: 10%");
        h.assertTrue(DamageShare.apply(zombie, fromKing, 100.0f) == 100.0f, "no share for ordinary mobs");
        // Everything else is 1%: another mod's entity (here an ownerless sword light), the world, no source.
        SwordQiEntity stray = new SwordQiEntity(FateEntities.SWORD_QI, h.getLevel());
        h.assertTrue(Math.abs(DamageShare.apply(gil, gil.damageSources().indirectMagic(stray, null), 100.0f) - 1.0f) < 1.0E-3, "other mods: 1%");
        h.assertTrue(Math.abs(DamageShare.apply(gil, gil.damageSources().magic(), 100.0f) - 1.0f) < 1.0E-3, "no source: 1%");
        h.succeed();
    }

    @GameTest
    public void npcsHaveTwoHundredHealth(GameTestHelper h) {
        GilgameshEntity gil = still(h.spawn(FateEntities.GILGAMESH, 1.5f, 2.0f, 1.5f));
        ArtoriaEntity saber = still(h.spawn(FateEntities.ARTORIA, 5.5f, 2.0f, 5.5f));
        for (LivingEntity k : new LivingEntity[]{gil, saber}) {
            h.assertTrue(k.getMaxHealth() == 200.0f && k.getHealth() == 200.0f, "NPC health 200 (" + k.getType() + ")");
            h.assertTrue(Kings.of(k).gold == KingRules.NPC_GOLD_HP, "NPC gold 150 (" + k.getType() + ")");
        }
        h.succeed();
    }

    @GameTest
    public void playersHitNpcsTwoAndAHalfTimes(GameTestHelper h) {
        GilgameshEntity gil = still(h.spawn(FateEntities.GILGAMESH, 1.5f, 2.0f, 1.5f));
        ArtoriaEntity saber = still(h.spawn(FateEntities.ARTORIA, 5.5f, 2.0f, 5.5f));
        LivingEntity zombie = still(h.spawn(EntityTypes.ZOMBIE, 3.5f, 2.0f, 1.5f));
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        for (LivingEntity k : new LivingEntity[]{gil, saber}) {
            float fromPlayer = DamageShare.apply(k, k.damageSources().playerAttack(player), 100.0f);
            float fromZombie = DamageShare.apply(k, k.damageSources().mobAttack(zombie), 100.0f);
            h.assertTrue(Math.abs(fromPlayer - 2.5f) < 1.0E-3 && Math.abs(fromPlayer / fromZombie - 2.5f) < 1.0E-3,
                "a player's blow on an NPC king counts x2.5 (" + fromPlayer + " vs " + fromZombie + ")");
        }
        h.assertTrue(DamageShare.apply(zombie, zombie.damageSources().playerAttack(player), 100.0f) == 100.0f, "ordinary mobs unchanged");
        h.succeed();
    }

    @GameTest
    public void gilgameshIsHostileButSparesTheHarmless(GameTestHelper h) {
        GilgameshEntity gil = still(h.spawn(FateEntities.GILGAMESH, 1.5f, 2.0f, 1.5f));
        LivingEntity villager = still(h.spawn(EntityTypes.VILLAGER, 5.5f, 2.0f, 1.5f));
        LivingEntity cow = still(h.spawn(EntityTypes.COW, 5.5f, 2.0f, 5.5f));
        LivingEntity zombie = still(h.spawn(EntityTypes.ZOMBIE, 1.5f, 2.0f, 5.5f));
        h.assertTrue(gil instanceof Enemy && FateEntities.GILGAMESH.getCategory() == MobCategory.MONSTER, "Gilgamesh is a hostile creature");
        Player unarmed = h.makeMockPlayer(GameType.SURVIVAL);
        Player armed = h.makeMockPlayer(GameType.SURVIVAL);
        armed.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        Player creative = h.makeMockPlayer(GameType.CREATIVE);
        h.assertTrue(Sides.noncombatant(villager) && Sides.noncombatant(cow), "villagers and grazing animals cannot fight");
        h.assertTrue(Sides.noncombatant(unarmed) && Sides.noncombatant(creative), "empty hands / creative: no fight");
        h.assertFalse(Sides.noncombatant(armed), "a sword in hand is a fighter");
        h.assertFalse(Sides.noncombatant(zombie), "monsters always fight");
        h.assertFalse(gil.canHarm(villager) || gil.canHarm(cow) || gil.canHarm(unarmed), "he does not strike the harmless");
        h.assertTrue(gil.canHarm(zombie), "monsters are struck down");
        // Hit by one of them (a revenge target): a sneer, and the target is dropped.
        gil.setTarget(villager);
        h.runAfterDelay(3, () -> {
            h.assertTrue(gil.getTarget() == null, "the harmless are left alone even as targets");
            h.succeed();
        });
    }

    @GameTest
    public void swordLightCutsTerrainOnlyWhenAllowed(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ArtoriaEntity saber = still(h.spawn(FateEntities.ARTORIA, 6.5f, 2.0f, 0.5f));
        BlockPos a = new BlockPos(2, 2, 3), b = new BlockPos(2, 2, 4);
        h.setBlock(a, Blocks.STONE);
        h.setBlock(b, Blocks.STONE);
        Vec3 origin = h.absoluteVec(new Vec3(0.5, 2.5, 3.5));
        Vec3 dir = h.absoluteVec(new Vec3(1.5, 2.5, 3.5)).subtract(origin);
        boolean before = FateConfig.terrainDestruction();
        try {
            FateConfig.setTerrainDestruction(false);
            // One step of flight by hand, then gone: nothing leaves the test area.
            SwordQiEntity off = SwordQiEntity.fire(level, saber, origin, dir, 0.0f);
            off.tick();
            off.discard();
        } finally {
            FateConfig.setTerrainDestruction(true);
        }
        h.runAfterDelay(3, () -> {
            h.assertBlockPresent(Blocks.STONE, a);
            h.assertBlockPresent(Blocks.STONE, b);
            h.assertTrue(Terrain.enabled(), "terrain effects on");
            SwordQiEntity on = SwordQiEntity.fire(level, saber, origin, dir, 0.0f);
            on.tick();
            on.discard();
            h.runAfterDelay(3, () -> {
                if (!before) FateConfig.setTerrainDestruction(false);
                h.assertBlockNotPresent(Blocks.STONE, a);
                h.assertBlockNotPresent(Blocks.STONE, b);
                h.succeed();
            });
        });
    }

    @GameTest
    public void avalonBlocksEnumaElish(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        GilgameshEntity gil = still(h.spawn(FateEntities.GILGAMESH, 1.5f, 2.0f, 1.5f));
        ArtoriaEntity saber = still(h.spawn(FateEntities.ARTORIA, 5.5f, 2.0f, 5.5f));
        KingState s = Kings.of(saber);
        s.cooldown(Skills.EXCALIBUR, level.getGameTime(), KingRules.EXCALIBUR);
        float hp = saber.getHealth();
        float gold = s.gold;
        Judgement.strike(level, gil, null, saber, Weapon.EA, 1.0f);
        h.assertTrue(saber.getHealth() == hp && s.gold == gold, "the utopia takes Ea entirely");
        h.assertTrue(s.domeActive(level.getGameTime()), "the dome is up");
        h.assertTrue(s.ready(Skills.EXCALIBUR, level.getGameTime()), "Excalibur is ready again (counter)");
        h.assertTrue(level.getGameTime() < s.counterUntil, "counter window open");
        h.assertFalse(Kings.of(saber).ready(Skills.AVALON_DOME, level.getGameTime()), "the unfolding is on cooldown");
        h.succeed();
    }

    @GameTest
    public void avalonRefusesOneLethalWound(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ArtoriaEntity saber = still(h.spawn(FateEntities.ARTORIA, 5.5f, 2.0f, 5.5f));
        LivingEntity zombie = still(h.spawn(EntityTypes.ZOMBIE, 2.5f, 2.0f, 2.5f));
        KingState s = Kings.of(saber);
        s.cooldown(Skills.AVALON_DOME, level.getGameTime(), KingRules.AVALON_DOME); // no dome for this one
        saber.hurtServer(level, zombie.damageSources().mobAttack(zombie), 1.0E7f);
        h.assertTrue(saber.isAlive() && saber.getHealth() > 0.0f, "Avalon refuses the lethal wound");
        h.assertFalse(s.ready(Skills.AVALON_LETHAL, level.getGameTime()), "lethal protection on cooldown");
        h.succeed();
    }

    @GameTest(maxTicks = 60)
    public void artoriaNpcHealsAtAPlayersPace(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ArtoriaEntity saber = still(h.spawn(FateEntities.ARTORIA, 5.5f, 2.0f, 5.5f));
        saber.setHealth(100.0f);
        Kings.of(saber).lastCombat = level.getGameTime() - 200L; // long out of combat
        h.runAfterDelay(21, () -> {
            float gain = saber.getHealth() - 100.0f;
            // Out of combat Avalon gives a player 4 per second (5% of 80); the NPC gets 10 (5% of 200).
            h.assertTrue(gain >= 9.99f && Math.abs(gain - 10.0f * Math.round(gain / 10.0f)) < 0.01f, "Avalon heals the NPC 10 a second (" + gain + ")");
            h.succeed();
        });
    }

    /** Gates opened round {@code caster} (other tests' gates are far away). */
    private static List<GatePortalEntity> gatesNear(LivingEntity caster) {
        return caster.level().getEntitiesOfClass(GatePortalEntity.class, caster.getBoundingBox().inflate(8.0), g -> !g.isRemoved());
    }

    private static boolean same(Vec3 a, Vec3 b) {
        return a != null && b != null && a.distanceToSqr(b) < 1.0E-4;
    }

    @GameTest
    public void npcGatesTakeFoesInTurnAndStayPut(GameTestHelper h) {
        GilgameshEntity gil = still(h.spawn(FateEntities.GILGAMESH, 1.5f, 2.0f, 1.5f));
        LivingEntity[] foes = {still(h.spawn(EntityTypes.ZOMBIE, 5.5f, 2.0f, 1.5f)), still(h.spawn(EntityTypes.ZOMBIE, 5.5f, 2.0f, 5.5f)),
            still(h.spawn(EntityTypes.ZOMBIE, 1.5f, 2.0f, 5.5f))};
        // One tick so the foes have stood still for a tick (nothing to lead).
        h.runAfterDelay(1, () -> {
            Vec3[] centres = new Vec3[foes.length];
            for (int i = 0; i < foes.length; ++i) centres[i] = foes[i].getBoundingBox().getCenter();
            GateOfBabylon.npcShots(gil, foes[0], 6);
            List<GatePortalEntity> gates = gatesNear(gil);
            h.assertTrue(gates.size() == 6, "6 gates (" + gates.size() + ")");
            for (Vec3 c : centres) {
                h.assertTrue(gates.stream().filter(g -> same(g.aimPoint(), c)).count() == 2, "each of the three foes gets two gates");
            }
            Vec3[] aimed = gates.stream().map(GatePortalEntity::aimPoint).toArray(Vec3[]::new);
            Vec3[] facing = gates.stream().map(GatePortalEntity::facing).toArray(Vec3[]::new);
            // The foe steps away before the treasures fly: the gates do not follow it.
            foes[0].teleportTo(foes[0].getX(), foes[0].getY(), foes[0].getZ() + 2.0);
            h.runAfterDelay(2, () -> {
                for (int i = 0; i < gates.size(); ++i) {
                    GatePortalEntity g = gates.get(i);
                    h.assertTrue(!g.fired() && same(g.aimPoint(), aimed[i]) && g.facing().dot(facing[i]) > 0.9999, "gate " + i + " keeps its aim");
                }
                gates.forEach(GatePortalEntity::discard);
                h.succeed();
            });
        });
    }

    /** Puts a player there, looking that way (the view follows the head). */
    private static void turn(ServerPlayer p, Vec3 at, float yaw, float pitch) {
        p.snapTo(at.x, at.y, at.z, yaw, pitch);
        p.setYHeadRot(yaw);
    }

    @GameTest
    public void playerVolleyIsAimedWhenPressed(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        Kings.of(p).king = KingRules.HERO;
        Vec3 at = h.absoluteVec(new Vec3(1.5, 2.0, 1.5));
        turn(p, at, -90.0f, 0.0f); // facing +x
        LivingEntity ahead = still(h.spawn(EntityTypes.ZOMBIE, 6.5f, 2.0f, 1.5f));
        LivingEntity aside = still(h.spawn(EntityTypes.ZOMBIE, 6.5f, 2.0f, 3.5f));
        still(h.spawn(EntityTypes.ZOMBIE, 1.5f, 2.0f, 6.5f)); // right beside him: outside the 40 degrees
        Vec3 ca = ahead.getBoundingBox().getCenter(), cb = aside.getBoundingBox().getCenter();
        GateOfBabylon.press(p);
        GateOfBabylon.Salvo s = GateOfBabylon.pressed(p);
        h.assertTrue(s != null && s.marks().size() == 2 && same(s.marks().get(0), ca) && same(s.marks().get(1), cb),
            "the foe in the crosshair first, then the one in view; not the one beside him: " + (s == null ? null : s.marks()));
        Vec3 eye = s.eye();
        // He turns round and steps aside while holding: the cast stays as it was when he pressed.
        Vec3 moved = h.absoluteVec(new Vec3(1.5, 2.0, 5.5));
        turn(p, moved, 90.0f, 30.0f);
        GateOfBabylon.growVolley(p, 30);
        List<GatePortalEntity> gates = GateOfBabylon.volley(p);
        h.assertTrue(gates.size() == KingRules.volleyGates(30), "the volley grows (" + gates.size() + ")");
        int onAhead = 0, onAside = 0;
        for (GatePortalEntity g : gates) {
            h.assertTrue(g.getX() < eye.x - 1.0 && Math.abs(g.getZ() - eye.z) < 4.0, "gates open behind where he stood when he pressed");
            if (same(g.aimPoint(), ca)) ++onAhead;
            else if (same(g.aimPoint(), cb)) ++onAside;
            else h.fail("a gate aimed elsewhere: " + g.aimPoint());
        }
        h.assertTrue(onAhead > 0 && onAside > 0 && Math.abs(onAhead - onAside) <= 1, "the two foes share the volley (" + onAhead + " / " + onAside + ")");
        GateOfBabylon.cancelVolley(p);
        h.assertTrue(GateOfBabylon.pressed(p) == null, "nothing left over");
        h.succeed();
    }

    @GameTest(maxTicks = 120)
    public void gilgameshChainsAStrongFoeAndFires(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        GilgameshEntity gil = still(h.spawn(FateEntities.GILGAMESH, 1.5f, 2.0f, 1.5f));
        LivingEntity warden = still(h.spawn(EntityTypes.WARDEN, 6.0f, 2.0f, 6.0f));
        // A worthy foe strikes first: he turns serious.
        gil.hurtServer(level, warden.damageSources().mobAttack(warden), 5.0f);
        h.assertTrue(gil.tier() == KingAiRules.SERIOUS, "serious after a worthy foe's blow");
        gil.setTarget(warden);
        h.succeedWhen(() -> {
            List<GatePortalEntity> gates = gatesNear(gil);
            h.assertTrue(warden.hasEffect(FateEffects.HEAVENS_CHAIN), "Enkidu first");
            h.assertFalse(gates.isEmpty(), "and the treasures with it");
            gates.forEach(GatePortalEntity::discard);
        });
    }

    @GameTest
    public void knightSparesTheLineOfFire(GameTestHelper h) {
        ArtoriaEntity saber = still(h.spawn(FateEntities.ARTORIA, 1.5f, 2.0f, 4.5f));
        still(h.spawn(EntityTypes.VILLAGER, 5.5f, 2.0f, 4.5f));
        Vec3 eye = saber.getEyePosition();
        h.assertFalse(saber.lineClear(eye, new Vec3(1, 0, 0)), "a villager in the way: no Excalibur");
        h.assertTrue(saber.lineClear(eye, new Vec3(-1, 0, 0)), "the other way is clear");
        h.succeed();
    }

    @GameTest
    public void chainsBind(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        GilgameshEntity gil = still(h.spawn(FateEntities.GILGAMESH, 1.5f, 2.0f, 1.5f));
        LivingEntity zombie = still(h.spawn(EntityTypes.ZOMBIE, 5.5f, 2.0f, 5.5f));
        Enkidu.bindTarget(level, gil, zombie);
        h.assertTrue(zombie.hasEffect(FateEffects.HEAVENS_CHAIN), "Enkidu binds");
        h.assertTrue(Enkidu.bound(zombie), "binding is tracked");
        h.succeed();
    }

    // ---- With the Gojo x Sukuna mod ----

    @GameTest
    public void excaliburCripplesGojoButNeverKills(GameTestHelper h) {
        if (!JjkCompat.LOADED) {
            h.succeed();
            return;
        }
        ServerLevel level = h.getLevel();
        ArtoriaEntity saber = still(h.spawn(FateEntities.ARTORIA, 1.5f, 2.0f, 1.5f));
        LivingEntity gojo = spawnJjk(h, JjkCompat.GOJO, 5.5, 2.0, 5.5);
        for (int i = 0; i < 4; ++i) {
            Judgement.strike(level, saber, null, gojo, Weapon.EXCALIBUR, 1.0f);
            h.assertTrue(gojo.isAlive() && gojo.getHealth() > 0.0f && gojo.getHealth() <= 2.0f, "Gojo left at one heart, alive (" + i + ")");
        }
        h.assertTrue(gojo.hasEffect(FateEffects.EXCALIBUR_WOUND), "wound of the holy sword");
        // The same knight cannot finish him while he is spared.
        gojo.hurtServer(level, saber.damageSources().mobAttack(saber), 1.0E6f);
        h.assertTrue(gojo.isAlive(), "her chivalry: she does not kill the helpless");
        h.succeed();
    }

    @GameTest
    public void eaAndExcaliburKillMahoraga(GameTestHelper h) {
        if (!JjkCompat.LOADED) {
            h.succeed();
            return;
        }
        ServerLevel level = h.getLevel();
        GilgameshEntity gil = still(h.spawn(FateEntities.GILGAMESH, 1.5f, 2.0f, 1.5f));
        ArtoriaEntity saber = still(h.spawn(FateEntities.ARTORIA, 1.5f, 2.0f, 6.5f));
        LivingEntity m1 = spawnJjk(h, JjkCompat.MAHORAGA, 5.5, 2.0, 2.5);
        LivingEntity m2 = spawnJjk(h, JjkCompat.MAHORAGA, 5.5, 2.0, 6.0);
        Judgement.strike(level, gil, null, m1, Weapon.EA, 1.0f);
        Judgement.strike(level, saber, null, m2, Weapon.EXCALIBUR, 1.0f);
        h.assertTrue(m1.isDeadOrDying(), "Ea kills Mahoraga");
        h.assertTrue(m2.isDeadOrDying(), "Excalibur kills Mahoraga");
        h.succeed();
    }

    @GameTest
    public void onlyEaPiercesInfinity(GameTestHelper h) {
        if (!JjkCompat.LOADED) {
            h.succeed();
            return;
        }
        ServerLevel level = h.getLevel();
        GilgameshEntity gil = still(h.spawn(FateEntities.GILGAMESH, 1.5f, 2.0f, 1.5f));
        LivingEntity gojo = spawnJjk(h, JjkCompat.GOJO, 5.5, 2.0, 5.5);
        float hp = gojo.getHealth();
        gojo.hurtServer(level, gil.damageSources().mobAttack(gil), 60.0f);
        Judgement.fresh(gojo);
        gojo.hurtServer(level, FateDamage.source(level, FateDamage.CHAIN, gil, gil), 60.0f);
        Judgement.fresh(gojo);
        h.assertTrue(gojo.getHealth() == hp, "fists and chains stop at Infinity");
        gojo.hurtServer(level, FateDamage.source(level, FateDamage.ENUMA_ELISH, gil, gil), 95.0f);
        h.assertTrue(gojo.getHealth() < hp, "Enuma Elish goes through Infinity");
        h.succeed();
    }

    @GameTest
    public void sorcerersTakeTenPercentFromKings(GameTestHelper h) {
        if (!JjkCompat.LOADED) {
            h.succeed();
            return;
        }
        ServerLevel level = h.getLevel();
        GilgameshEntity gil = still(h.spawn(FateEntities.GILGAMESH, 1.5f, 2.0f, 1.5f));
        LivingEntity zombie = still(h.spawn(EntityTypes.ZOMBIE, 1.5f, 2.0f, 6.5f));
        LivingEntity a = spawnJjk(h, JjkCompat.SUKUNA, 5.5, 2.0, 2.5);
        LivingEntity b = spawnJjk(h, JjkCompat.SUKUNA, 5.5, 2.0, 6.0);
        float ha = a.getHealth(), hb = b.getHealth();
        a.hurtServer(level, zombie.damageSources().mobAttack(zombie), 100.0f);
        b.hurtServer(level, gil.damageSources().mobAttack(gil), 100.0f);
        float fromZombie = ha - a.getHealth(), fromKing = hb - b.getHealth();
        h.assertTrue(fromKing > fromZombie * 3.0f && fromKing > 1.0f, "a king counts as a mod character (" + fromZombie + " vs " + fromKing + ")");
        h.succeed();
    }

    @GameTest
    public void phantasmsTake190FromJjkNpcs(GameTestHelper h) {
        if (!JjkCompat.LOADED) {
            h.succeed();
            return;
        }
        ServerLevel level = h.getLevel();
        GilgameshEntity gil = still(h.spawn(FateEntities.GILGAMESH, 1.5f, 2.0f, 1.5f));
        ArtoriaEntity saber = still(h.spawn(FateEntities.ARTORIA, 1.5f, 2.0f, 6.5f));
        LivingEntity gojo = spawnJjk(h, JjkCompat.GOJO, 6.5, 2.0, 1.5);
        LivingEntity s1 = spawnJjk(h, JjkCompat.SUKUNA, 6.5, 2.0, 4.0);
        LivingEntity s2 = spawnJjk(h, JjkCompat.SUKUNA, 6.5, 2.0, 6.5);
        LivingEntity s3 = spawnJjk(h, JjkCompat.SUKUNA, 4.0, 2.0, 6.5);
        Player hero = h.makeMockPlayer(GameType.SURVIVAL);
        Kings.of(hero).king = KingRules.HERO;
        float g = gojo.getHealth(), a = s1.getHealth(), b = s2.getHealth(), c = s3.getHealth();
        Judgement.strike(level, gil, null, gojo, Weapon.EA, 1.0f);
        assertLoss(h, gojo, g, "Ea through Infinity on the Gojo NPC");
        Judgement.strike(level, gil, null, s1, Weapon.EA, 1.0f);
        assertLoss(h, s1, a, "Gilgamesh's Ea on the Sukuna NPC");
        Judgement.strike(level, saber, null, s2, Weapon.EXCALIBUR, 1.0f);
        assertLoss(h, s2, b, "Artoria's Excalibur on the Sukuna NPC");
        Judgement.strike(level, hero, null, s3, Weapon.EA, 1.0f);
        assertLoss(h, s3, c, "a hero player's Ea on the Sukuna NPC");
        h.succeed();
    }

    @GameTest
    public void gojoDealsTenPercentToBothKings(GameTestHelper h) {
        if (!JjkCompat.LOADED) {
            h.succeed();
            return;
        }
        ServerLevel level = h.getLevel();
        GilgameshEntity gil = still(h.spawn(FateEntities.GILGAMESH, 1.5f, 2.0f, 1.5f));
        ArtoriaEntity saber = still(h.spawn(FateEntities.ARTORIA, 1.5f, 2.0f, 6.5f));
        LivingEntity gojo = spawnJjk(h, JjkCompat.GOJO, 5.5, 2.0, 2.5);
        LivingEntity sukuna = spawnJjk(h, JjkCompat.SUKUNA, 5.5, 2.0, 6.0);
        // A technique on its own (Hollow Purple with nobody behind it) counts as well.
        Entity purple = BuiltInRegistries.ENTITY_TYPE.getValue(JjkCompat.MURASAKI).create(level, EntitySpawnReason.COMMAND);
        for (LivingEntity k : new LivingEntity[]{gil, saber}) {
            h.assertTrue(Math.abs(DamageShare.apply(k, gojo.damageSources().mobAttack(gojo), 100.0f) - 10.0f) < 1.0E-3, "Gojo on " + k.getType() + ": 10%");
            h.assertTrue(Math.abs(DamageShare.apply(k, sukuna.damageSources().mobAttack(sukuna), 100.0f) - 10.0f) < 1.0E-3, "Sukuna on " + k.getType() + ": 10%");
            if (purple != null) {
                h.assertTrue(Math.abs(DamageShare.apply(k, level.damageSources().indirectMagic(purple, null), 100.0f) - 10.0f) < 1.0E-3, "Hollow Purple on " + k.getType() + ": 10%");
            }
        }
        // And the kings strike back twice as hard (their blows meet armour; the techniques do not): NPCs ...
        h.assertTrue(DamageShare.apply(gojo, gil.damageSources().mobAttack(gil), 100.0f) == 200.0f, "Gilgamesh on Gojo x2");
        h.assertTrue(DamageShare.apply(sukuna, saber.damageSources().mobAttack(saber), 100.0f) == 200.0f, "Artoria on Sukuna x2");
        // ... and players in either regalia alike, their treasures included; an ordinary player does not.
        Player hero = h.makeMockPlayer(GameType.SURVIVAL);
        Player knight = h.makeMockPlayer(GameType.SURVIVAL);
        Player plain = h.makeMockPlayer(GameType.SURVIVAL);
        Kings.of(hero).king = KingRules.HERO;
        Kings.of(knight).king = KingRules.KNIGHT;
        SwordQiEntity light = new SwordQiEntity(FateEntities.SWORD_QI, level);
        h.assertTrue(DamageShare.apply(gojo, gojo.damageSources().playerAttack(hero), 100.0f) == 200.0f, "hero player on Gojo x2");
        h.assertTrue(DamageShare.apply(sukuna, level.damageSources().indirectMagic(light, knight), 100.0f) == 200.0f, "knight player's sword light on Sukuna x2");
        h.assertTrue(DamageShare.apply(sukuna, sukuna.damageSources().playerAttack(plain), 100.0f) == 100.0f, "no bonus without the regalia");
        h.assertTrue(gil.canHarm(gojo) && gil.canHarm(sukuna), "sorcerers are worth his treasures");
        // Gojo's NPC goes after hostile creatures: Gilgamesh now is one, Artoria is not.
        try {
            var canHarm = gojo.getClass().getMethod("canHarm", LivingEntity.class);
            h.assertTrue((Boolean)canHarm.invoke(gojo, gil), "Gojo attacks Gilgamesh of his own accord");
            h.assertFalse((Boolean)canHarm.invoke(gojo, saber), "but leaves Artoria be");
        } catch (ReflectiveOperationException e) {
            h.fail("Gojo NPC canHarm: " + e);
        }
        h.succeed();
    }

    @GameTest
    public void avalonIgnoresDomains(GameTestHelper h) {
        if (!JjkCompat.LOADED) {
            h.succeed();
            return;
        }
        ServerLevel level = h.getLevel();
        ArtoriaEntity saber = still(h.spawn(FateEntities.ARTORIA, 5.5f, 2.0f, 5.5f));
        LivingEntity sukuna = spawnJjk(h, JjkCompat.SUKUNA, 1.5, 2.0, 1.5);
        ResourceKey<DamageType> sureHit = ResourceKey.create(Registries.DAMAGE_TYPE, JjkCompat.SURE_HIT);
        var holder = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).get(sureHit);
        if (holder.isEmpty()) {
            h.succeed();
            return;
        }
        saber.hurtServer(level, new DamageSource(holder.get(), sukuna, sukuna), 5000.0f);
        h.assertFalse(Kings.of(saber).domeActive(level.getGameTime()), "no unfolding for the sure hit of a domain");
        h.succeed();
    }
}
