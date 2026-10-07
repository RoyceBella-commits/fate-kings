package cn.blockforge.fatekings.gametest;

import cn.blockforge.fatekings.archer.ArcherPassives;
import cn.blockforge.fatekings.archer.ArcherRules;
import cn.blockforge.fatekings.archer.Arsenal;
import cn.blockforge.fatekings.archer.Projection;
import cn.blockforge.fatekings.archer.RhoAias;
import cn.blockforge.fatekings.combat.DamageShare;
import cn.blockforge.fatekings.combat.Judgement;
import cn.blockforge.fatekings.combat.JudgementRules.Weapon;
import cn.blockforge.fatekings.combat.Terrain;
import cn.blockforge.fatekings.compat.JjkCompat;
import cn.blockforge.fatekings.config.FateConfig;
import cn.blockforge.fatekings.entity.CaladbolgEntity;
import cn.blockforge.fatekings.entity.EnumaElishEntity;
import cn.blockforge.fatekings.entity.ProjectedArrowEntity;
import cn.blockforge.fatekings.entity.TreasureProjectile;
import cn.blockforge.fatekings.entity.UbwEntity;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.Kings;
import cn.blockforge.fatekings.king.Skills;
import cn.blockforge.fatekings.npc.ArtoriaEntity;
import cn.blockforge.fatekings.npc.EmiyaEntity;
import cn.blockforge.fatekings.npc.GilgameshEntity;
import cn.blockforge.fatekings.registry.FateDamage;
import cn.blockforge.fatekings.registry.FateEntities;
import cn.blockforge.fatekings.registry.FateItems;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Server game tests of EMIYA: the Red Shroud, the bow, Caladbolg II, Rho Aias, projection, the marble, the NPC. */
public class ArcherServerTests {
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

    /** A mock player wearing the full Red Shroud (and made the Archer at once). */
    private static ServerPlayer archer(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(FateItems.SHROUD_HEADPIECE));
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(FateItems.SHROUD_COAT));
        p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(FateItems.SHROUD_LEGGINGS));
        p.setItemSlot(EquipmentSlot.FEET, new ItemStack(FateItems.SHROUD_BOOTS));
        Kings.tick(p);
        return p;
    }

    /** Faces an NPC exactly along +x (the view follows the head). */
    private static void faceEast(LivingEntity e) {
        e.setYRot(-90.0f);
        e.setYHeadRot(-90.0f);
        e.setYBodyRot(-90.0f);
        e.setXRot(0.0f);
        e.yRotO = -90.0f;
        e.yHeadRotO = -90.0f;
    }

    private static void assertLoss(GameTestHelper h, LivingEntity t, float before, float want, String what) {
        float loss = before - t.getHealth();
        float expect = Math.min(want, before);
        h.assertTrue(Math.abs(loss - expect) < 0.01f, what + ": " + expect + " health off (" + loss + ")");
    }

    @GameTest
    public void archerFromTheFullRedShroud(GameTestHelper h) {
        ServerPlayer p = archer(h);
        h.assertTrue(Kings.of(p).king == KingRules.ARCHER && Kings.isArcher(p), "the Red Shroud makes the Archer");
        h.assertTrue(Math.abs(p.getMaxHealth() - KingRules.MAX_HEALTH) < 0.01f, "40 red hearts (" + p.getMaxHealth() + ")");
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(FateItems.GOLDEN_CROWN));
        Kings.tick(p);
        h.assertTrue(Kings.of(p).king == KingRules.NONE, "a golden crown spoils the set");
        h.succeed();
    }

    @GameTest
    public void archerTakesOneOrTenPercent(GameTestHelper h) {
        EmiyaEntity emiya = still(h.spawn(FateEntities.EMIYA, 1.5f, 2.0f, 1.5f));
        GilgameshEntity gil = still(h.spawn(FateEntities.GILGAMESH, 5.5f, 2.0f, 5.5f));
        LivingEntity zombie = still(h.spawn(EntityTypes.ZOMBIE, 3.5f, 2.0f, 1.5f));
        h.assertTrue(emiya.getMaxHealth() == KingRules.NPC_MAX_HEALTH && Kings.of(emiya).gold == KingRules.NPC_GOLD_HP, "NPC 200 + 150");
        h.assertTrue(Math.abs(DamageShare.apply(emiya, zombie.damageSources().mobAttack(zombie), 100.0f) - 1.0f) < 1.0E-3, "vanilla hits: 1%");
        h.assertTrue(Math.abs(DamageShare.apply(emiya, gil.damageSources().mobAttack(gil), 100.0f) - 10.0f) < 1.0E-3, "another spirit: 10%");
        float hp = emiya.getHealth();
        Judgement.strike(h.getLevel(), gil, null, emiya, Weapon.EA, 1.0f);
        assertLoss(h, emiya, hp, KingRules.NPC_PHANTASM_LOSS, "Ea on EMIYA");
        h.succeed();
    }

    @GameTest
    public void homingArrowBendsTowardItsFoe(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        EmiyaEntity emiya = still(h.spawn(FateEntities.EMIYA, 0.5f, 2.0f, 0.5f));
        LivingEntity zombie = still(h.spawn(EntityTypes.ZOMBIE, 4.5f, 2.0f, 7.5f));
        Vec3 from = h.absoluteVec(new Vec3(0.5, 3.0, 1.0));
        Vec3 east = h.absoluteVec(new Vec3(1.5, 3.0, 1.0)).subtract(from);
        Vec3 south = h.absoluteVec(new Vec3(0.5, 3.0, 2.0)).subtract(from);
        ProjectedArrowEntity arrow = ProjectedArrowEntity.loose(level, emiya, from, east, zombie, ItemStack.EMPTY);
        Vec3 v = flyInPlace(arrow, from);
        h.assertTrue(v.normalize().dot(south) > 0.2, "the arrow turned toward the zombie (" + v + ")");
        h.assertTrue(Math.abs(v.length() - ArcherRules.ARROW_SPEED) < 0.05, "at full speed (" + v.length() + ")");
        h.succeed();
    }

    /**
     * Four flight ticks without leaving the spot (the test area is too small for an arrow at full
     * speed): two to arm, two of steering, and no fresh search for a foe yet. The level counts the
     * ticks, so this does too.
     */
    private static Vec3 flyInPlace(ProjectedArrowEntity arrow, Vec3 from) {
        for (int i = 0; i < 4; ++i) {
            arrow.setPos(from);
            ++arrow.tickCount;
            arrow.tick();
        }
        Vec3 v = arrow.getDeltaMovement();
        arrow.discard();
        return v;
    }

    @GameTest
    public void homingArrowFliesStraightWithNoFoe(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        EmiyaEntity emiya = still(h.spawn(FateEntities.EMIYA, 0.5f, 2.0f, 0.5f));
        Vec3 from = h.absoluteVec(new Vec3(0.5, 3.0, 1.0));
        Vec3 east = h.absoluteVec(new Vec3(1.5, 3.0, 1.0)).subtract(from);
        ProjectedArrowEntity arrow = ProjectedArrowEntity.loose(level, emiya, from, east, null, ItemStack.EMPTY);
        Vec3 v = flyInPlace(arrow, from);
        h.assertTrue(v.normalize().dot(east) > 0.999, "no foe: straight on (" + v + ")");
        h.succeed();
    }

    @GameTest
    public void caladbolgTakes120FromNpcs(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        EmiyaEntity emiya = still(h.spawn(FateEntities.EMIYA, 1.5f, 2.0f, 1.5f));
        GilgameshEntity gil = still(h.spawn(FateEntities.GILGAMESH, 6.5f, 2.0f, 1.5f));
        ArtoriaEntity saber = still(h.spawn(FateEntities.ARTORIA, 1.5f, 2.0f, 6.5f));
        EmiyaEntity other = still(h.spawn(FateEntities.EMIYA, 6.5f, 2.0f, 6.5f));
        GilgameshEntity gil2 = still(h.spawn(FateEntities.GILGAMESH, 4.0f, 2.0f, 4.0f));
        Kings.of(saber).cooldown(Skills.AVALON_DOME, level.getGameTime(), KingRules.AVALON_DOME);
        for (LivingEntity t : new LivingEntity[]{gil, saber, other}) {
            float hp = t.getHealth();
            Judgement.strike(level, emiya, null, t, Weapon.CALADBOLG, 1.0f);
            assertLoss(h, t, hp, ArcherRules.CALADBOLG_NPC_LOSS, "Caladbolg on " + t.getType());
            h.assertTrue(Kings.of(t).gold == KingRules.NPC_GOLD_HP, "gold untouched (" + t.getType() + ")");
        }
        ServerPlayer p = archer(h);
        float hp = gil2.getHealth();
        Judgement.strike(level, p, null, gil2, Weapon.CALADBOLG, 1.0f);
        assertLoss(h, gil2, hp, ArcherRules.CALADBOLG_NPC_LOSS, "an archer player's Caladbolg");
        h.succeed();
    }

    @GameTest
    public void caladbolgOnCreaturesAndArmour(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        EmiyaEntity emiya = still(h.spawn(FateEntities.EMIYA, 1.5f, 2.0f, 1.5f));
        LivingEntity bare = sturdy(h, 5.5f, 1.5f, 0.0);
        LivingEntity armoured = sturdy(h, 5.5f, 5.5f, 20.0);
        Judgement.strike(level, emiya, null, bare, Weapon.CALADBOLG, 1.0f);
        Judgement.strike(level, emiya, null, armoured, Weapon.CALADBOLG, 1.0f);
        float bareLoss = 1024.0f - bare.getHealth(), armouredLoss = 1024.0f - armoured.getHealth();
        h.assertTrue(Math.abs(bareLoss - ArcherRules.CALADBOLG_MOB_DAMAGE) < 0.01f, "600 on a creature (" + bareLoss + ")");
        h.assertTrue(armouredLoss < bareLoss - 20.0f, "armour takes its part (" + armouredLoss + ")");
        h.succeed();
    }

    private static LivingEntity sturdy(GameTestHelper h, float x, float z, double armour) {
        LivingEntity golem = still(h.spawn(EntityTypes.IRON_GOLEM, x, 2.0f, z));
        golem.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024.0);
        golem.getAttribute(Attributes.ARMOR).setBaseValue(armour);
        golem.setHealth(1024.0f);
        return golem;
    }

    @GameTest
    public void caladbolgBoresOnlyWhenAllowed(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        EmiyaEntity emiya = still(h.spawn(FateEntities.EMIYA, 6.5f, 2.0f, 6.5f));
        BlockPos a = new BlockPos(3, 2, 1), b = new BlockPos(4, 2, 1);
        h.setBlock(a, Blocks.STONE);
        h.setBlock(b, Blocks.STONE);
        Vec3 origin = h.absoluteVec(new Vec3(0.5, 2.5, 1.5));
        Vec3 dir = h.absoluteVec(new Vec3(1.5, 2.5, 1.5)).subtract(origin);
        boolean before = FateConfig.terrainDestruction();
        try {
            FateConfig.setTerrainDestruction(false);
            // One step of flight by hand (6 blocks), then gone: nothing leaves the test area.
            CaladbolgEntity off = CaladbolgEntity.fire(level, emiya, origin, dir, null);
            off.tick();
            off.discard();
        } finally {
            FateConfig.setTerrainDestruction(true);
        }
        h.runAfterDelay(3, () -> {
            h.assertBlockPresent(Blocks.STONE, a);
            h.assertBlockPresent(Blocks.STONE, b);
            h.assertTrue(Terrain.enabled(), "terrain effects on");
            CaladbolgEntity on = CaladbolgEntity.fire(level, emiya, origin, dir, null);
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
    public void caladbolgKillsMahoragaAndPiercesInfinity(GameTestHelper h) {
        if (!JjkCompat.LOADED) {
            h.succeed();
            return;
        }
        ServerLevel level = h.getLevel();
        EmiyaEntity emiya = still(h.spawn(FateEntities.EMIYA, 1.5f, 2.0f, 1.5f));
        LivingEntity mahoraga = spawnJjk(h, JjkCompat.MAHORAGA, 5.5, 2.0, 2.5);
        LivingEntity gojo = spawnJjk(h, JjkCompat.GOJO, 5.5, 2.0, 6.0);
        Judgement.strike(level, emiya, null, mahoraga, Weapon.CALADBOLG, 1.0f);
        h.assertTrue(mahoraga.isDeadOrDying(), "Caladbolg kills Mahoraga");
        float hp = gojo.getHealth();
        gojo.hurtServer(level, FateDamage.source(level, FateDamage.THROWN_BLADE, emiya, emiya), 60.0f);
        Judgement.fresh(gojo);
        gojo.hurtServer(level, emiya.damageSources().mobAttack(emiya), 60.0f);
        Judgement.fresh(gojo);
        h.assertTrue(gojo.getHealth() == hp, "thrown blades and blows stop at Infinity");
        gojo.hurtServer(level, FateDamage.source(level, FateDamage.UBW_SWORD, emiya, emiya), 60.0f);
        h.assertTrue(gojo.getHealth() < hp, "the swords of the marble go through Infinity");
        float hp2 = gojo.getHealth();
        Judgement.fresh(gojo);
        gojo.hurtServer(level, FateDamage.source(level, FateDamage.CALADBOLG, emiya, emiya), 60.0f);
        h.assertTrue(gojo.getHealth() < hp2, "Caladbolg II goes through Infinity: " + hp + " -> " + hp2 + " -> " + gojo.getHealth()
            + " max " + gojo.getMaxHealth());
        h.succeed();
    }

    @GameTest
    public void uniqueTreasuresCannotBeProjected(GameTestHelper h) {
        h.assertTrue(Projection.check(new ItemStack(FateItems.EA)) == Projection.Check.UNIQUE, "Ea is one of a kind");
        h.assertTrue(Projection.check(new ItemStack(FateItems.GATE_OF_BABYLON)) == Projection.Check.UNIQUE, "so is the treasury");
        h.assertTrue(Projection.check(new ItemStack(Items.DIAMOND_SWORD)) == Projection.Check.OK, "a sword");
        h.assertTrue(Projection.check(new ItemStack(Items.BOW)) == Projection.Check.OK, "a bow");
        h.assertTrue(Projection.check(new ItemStack(Items.MACE)) == Projection.Check.OK, "a mace");
        h.assertTrue(Projection.check(new ItemStack(Items.DIAMOND_PICKAXE)) == Projection.Check.NOT_WEAPON, "a pickaxe is a tool");
        h.assertTrue(Projection.check(new ItemStack(Items.BREAD)) == Projection.Check.NOT_WEAPON, "bread");
        ItemStack replica = Projection.copy(new ItemStack(FateItems.EXCALIBUR), java.util.UUID.randomUUID(), 100L, false);
        h.assertTrue(replica.is(FateItems.EXCALIBUR_REPLICA) && Projection.projected(replica), "Excalibur traces into a replica");
        h.succeed();
    }

    @GameTest
    public void projectionsFadeOutsideTheirMaker(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = archer(h);
        h.assertTrue(Projection.give(p, new ItemStack(Items.DIAMOND_SWORD)), "a sword projected");
        ItemStack copy = p.getMainHandItem();
        h.assertTrue(Projection.projected(copy) && copy.is(Items.DIAMOND_SWORD), "the copy is in his hand (" + copy + ")");
        // A chest: the copy fades; a real sword stays.
        ChestMenu chest = ChestMenu.threeRows(77, p.getInventory(), new SimpleContainer(27));
        chest.getSlot(0).setByPlayer(copy.copy(), ItemStack.EMPTY);
        h.assertTrue(chest.getSlot(0).getItem().isEmpty(), "no copy in a chest");
        chest.getSlot(1).setByPlayer(new ItemStack(Items.DIAMOND_SWORD), ItemStack.EMPTY);
        h.assertFalse(chest.getSlot(1).getItem().isEmpty(), "a real sword goes in");
        // A bundle refuses it.
        BundleContents.Mutable bundle = new BundleContents.Mutable();
        h.assertTrue(bundle.tryInsert(copy.copy()) == 0, "no copy in a bundle");
        // Dropped on the ground: gone.
        ItemEntity dropped = new ItemEntity(level, p.getX(), p.getY() + 1.0, p.getZ(), copy.copy());
        level.addFreshEntity(dropped);
        // Run out: gone at the next sweep.
        ItemStack short_ = Projection.copy(new ItemStack(Items.IRON_SWORD), p.getUUID(), level.getGameTime() + 1, false);
        p.getInventory().setItem(20, short_);
        h.runAfterDelay(3, () -> {
            h.assertTrue(dropped.isRemoved(), "a dropped copy fades");
            h.assertTrue(p.getInventory().getItem(20).isEmpty(), "a copy that ran out fades");
            Projection.dissipateAll(p);
            h.succeed();
        });
    }

    @GameTest
    public void weaponsThatHitTheArcherAreRecorded(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        // The NPC (the mock player is in creative mode, where blows do not land).
        EmiyaEntity emiya = still(h.spawn(FateEntities.EMIYA, 1.5f, 2.0f, 1.5f));
        LivingEntity zombie = still(h.spawn(EntityTypes.ZOMBIE, 3.5f, 2.0f, 1.5f));
        zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_AXE));
        emiya.hurtServer(level, zombie.damageSources().mobAttack(zombie), 5.0f);
        boolean seen = Arsenal.of(emiya).entries.stream().anyMatch(e -> e.stack().is(Items.DIAMOND_AXE));
        h.assertTrue(seen, "the axe that struck him is on the Hill of Swords");
        h.succeed();
    }

    @GameTest
    public void twinBladeAnswersInTheOffHand(GameTestHelper h) {
        ServerPlayer p = archer(h);
        p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(FateItems.KANSHOU));
        ArcherPassives.partnerBlade(p);
        ItemStack off = p.getOffhandItem();
        h.assertTrue(off.is(FateItems.BAKUYA) && Projection.partner(off), "Bakuya is projected into the off hand");
        p.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        ArcherPassives.partnerBlade(p);
        h.assertTrue(p.getOffhandItem().isEmpty(), "and fades when Kanshou is put away");
        h.succeed();
    }

    @GameTest
    public void rhoAiasStopsATreasure(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        EmiyaEntity emiya = still(h.spawn(FateEntities.EMIYA, 1.5f, 2.0f, 4.5f));
        GilgameshEntity gil = still(h.spawn(FateEntities.GILGAMESH, 7.5f, 2.0f, 0.5f));
        faceEast(emiya);
        h.assertTrue(RhoAias.cast(emiya), "the seven rings");
        float hp = emiya.getHealth();
        Vec3 from = h.absoluteVec(new Vec3(7.5, 3.6, 4.5));
        TreasureProjectile t = TreasureProjectile.create(level, gil, from, new ItemStack(Items.IRON_SWORD), TreasureProjectile.NONE, true);
        Vec3 west = h.absoluteVec(new Vec3(6.5, 3.6, 4.5)).subtract(from);
        t.shoot(west.x, west.y, west.z, 3.0f, 0.0f);
        level.addFreshEntity(t);
        h.runAfterDelay(4, () -> {
            h.assertTrue(t.isRemoved(), "the treasure was stopped");
            h.assertTrue(emiya.getHealth() == hp, "he was not hurt");
            h.assertTrue(RhoAias.petals(emiya) == ArcherRules.RHO_AIAS_PETALS, "one hit costs no petal yet (five a petal)");
            RhoAias.end(emiya);
            h.succeed();
        });
    }

    @GameTest
    public void rhoAiasBeamsTearPetalsAndEaShattersIt(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        EmiyaEntity emiya = still(h.spawn(FateEntities.EMIYA, 1.5f, 2.0f, 4.5f));
        faceEast(emiya);
        RhoAias.cast(emiya);
        Vec3 from = h.absoluteVec(new Vec3(7.5, 3.6, 4.5));
        Vec3 to = h.absoluteVec(new Vec3(1.0, 3.6, 4.5));
        CaladbolgEntity drill = new CaladbolgEntity(FateEntities.CALADBOLG, level);
        double stop = RhoAias.interceptBeam(level, drill, from, to);
        h.assertTrue(stop > 0.0 && RhoAias.petals(emiya) == 3, "Caladbolg stopped at the shield, four petals torn (" + stop + ")");
        EnumaElishEntity ea = new EnumaElishEntity(FateEntities.ENUMA_ELISH, level);
        h.assertTrue(RhoAias.interceptBeam(level, ea, from, to) < 0.0, "Ea is never stopped");
        h.assertTrue(RhoAias.petals(emiya) == 0, "and the shield is shattered");
        h.succeed();
    }

    @GameTest(maxTicks = 100)
    public void theMarbleHoldsItsEnemiesIn(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        EmiyaEntity emiya = still(h.spawn(FateEntities.EMIYA, 3.5f, 2.0f, 3.5f));
        LivingEntity zombie = still(h.spawn(EntityTypes.ZOMBIE, 3.5f, 2.0f, 5.5f));
        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024.0);
        zombie.setHealth(1024.0f);
        LivingEntity villager = still(h.spawn(EntityTypes.VILLAGER, 5.5f, 2.0f, 3.5f));
        UbwEntity marble = UbwEntity.open(level, emiya, 4.0);
        Vec3 c = marble.position();
        h.runAfterDelay(ArcherRules.UBW_UNFOLD + 2, () -> {
            h.assertTrue(marble.active(), "the marble is open");
            Vec3 out = c.add(0.0, 0.0, 6.0);
            zombie.teleportTo(out.x, out.y, out.z);
            Vec3 out2 = c.add(6.0, 0.0, 0.0);
            villager.teleportTo(out2.x, out2.y, out2.z);
            h.runAfterDelay(2, () -> {
                double dz = zombie.position().distanceTo(c), dv = villager.position().distanceTo(c);
                marble.discard();
                h.assertTrue(dz < 3.5, "the enemy is put back inside the wall (" + dz + ")");
                h.assertTrue(dv > 5.5, "the bystander walks where he likes (" + dv + ")");
                h.succeed();
            });
        });
    }

    @GameTest(maxTicks = 60)
    public void theMarbleClashesWithADomain(GameTestHelper h) {
        if (!JjkCompat.LOADED) {
            h.succeed();
            return;
        }
        ServerLevel level = h.getLevel();
        EmiyaEntity emiya = still(h.spawn(FateEntities.EMIYA, 3.5f, 2.0f, 3.5f));
        LivingEntity gojo = spawnJjk(h, JjkCompat.GOJO, 6.5, 2.0, 6.5);
        Entity stand = h.spawn(EntityTypes.ARMOR_STAND, new Vec3(6.5, 2.0, 1.5));
        UbwEntity marble = UbwEntity.open(level, emiya, 4.0);
        h.runAfterDelay(3, () -> {
            h.assertTrue(JjkCompat.domainRegistered(marble), "the other mod knows the marble as a domain");
            // A rival domain (a stand-in for a Void) overlapping it.
            JjkCompat.registerDomain(gojo, stand, stand.position(), 10.0, true);
            h.runAfterDelay(12, () -> {
                int left = marble.closeAt() - marble.life();
                JjkCompat.unregisterDomain(stand);
                marble.discard();
                stand.discard();
                h.assertTrue(gojo.getUUID().equals(JjkCompat.domainRival(marble)) || left <= 440, "paired with the domain");
                h.assertTrue(left <= ArcherRules.ubwAfterClash(10_000, true) && left > 400, "cut to 20 s, 2 s more for opening first (" + left + ")");
                h.succeed();
            });
        });
    }

    @GameTest(maxTicks = 60)
    public void emiyaFightsGilgameshNeverArtoria(GameTestHelper h) {
        EmiyaEntity emiya = still(h.spawn(FateEntities.EMIYA, 1.5f, 2.0f, 1.5f));
        GilgameshEntity gil = still(h.spawn(FateEntities.GILGAMESH, 6.5f, 2.0f, 6.5f));
        ArtoriaEntity saber = still(h.spawn(FateEntities.ARTORIA, 1.5f, 2.0f, 6.5f));
        h.assertTrue(emiya.canHarm(gil) && gil.canHarm(emiya), "Archer and the King of Heroes are enemies");
        h.assertFalse(emiya.canHarm(saber) || saber.canHarm(emiya), "Archer and Saber never fight");
        h.succeedWhen(() -> {
            h.assertTrue(emiya.getTarget() == gil, "he seeks out the King of Heroes");
            emiya.discard();
            gil.discard();
            saber.discard();
        });
    }

    @GameTest
    public void emiyaDefendsVillagers(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        EmiyaEntity emiya = still(h.spawn(FateEntities.EMIYA, 1.5f, 2.0f, 1.5f));
        LivingEntity villager = still(h.spawn(EntityTypes.VILLAGER, 5.5f, 2.0f, 5.5f));
        LivingEntity zombie = still(h.spawn(EntityTypes.ZOMBIE, 6.5f, 2.0f, 5.5f));
        villager.hurtServer(level, zombie.damageSources().mobAttack(zombie), 1.0f);
        h.assertTrue(emiya.getTarget() == zombie, "he turns on whatever struck a villager");
        emiya.discard();
        h.succeed();
    }

    /** Gates near {@code e} (other tests' far away). */
    @SuppressWarnings("unused")
    private static List<Entity> near(LivingEntity e) {
        return e.level().getEntities(e, e.getBoundingBox().inflate(8.0));
    }
}
