package cn.blockforge.fatekings.gametest;

import cn.blockforge.fatekings.archer.ArcherBow;
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
import cn.blockforge.fatekings.entity.ExcaliburWaveEntity;
import cn.blockforge.fatekings.entity.GatePortalEntity;
import cn.blockforge.fatekings.entity.ProjectedArrowEntity;
import cn.blockforge.fatekings.entity.TreasureProjectile;
import cn.blockforge.fatekings.entity.UbwEntity;
import cn.blockforge.fatekings.hero.GateOfBabylon;
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
    // ---- 1.1.1 ----

    @GameTest
    public void tripleShotFansAtThreeFoes(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        EmiyaEntity emiya = still(h.spawn(FateEntities.EMIYA, 1.5f, 2.0f, 4.5f));
        LivingEntity centre = still(h.spawn(EntityTypes.ZOMBIE, 7.5f, 2.0f, 4.5f));
        LivingEntity left = still(h.spawn(EntityTypes.ZOMBIE, 7.0f, 2.0f, 2.0f));
        LivingEntity right = still(h.spawn(EntityTypes.ZOMBIE, 7.0f, 2.0f, 7.0f));
        faceEast(emiya);
        emiya.setTarget(centre);
        h.assertTrue(ArcherBow.triple(emiya), "three arrows loosed");
        h.assertFalse(ArcherBow.triple(emiya), "then a second's cooldown");
        h.assertTrue(ArcherBow.tap(emiya), "which is not the tap's");
        List<ProjectedArrowEntity> arrows = level.getEntitiesOfClass(ProjectedArrowEntity.class, emiya.getBoundingBox().inflate(4.0),
            a -> a.getOwner() == emiya);
        java.util.Set<LivingEntity> hunted = new java.util.HashSet<>();
        for (ProjectedArrowEntity a : arrows) {
            if (a.target() != null) hunted.add(a.target());
            a.discard();
        }
        h.assertTrue(arrows.size() == 4, "three arrows and a tap (" + arrows.size() + ")");
        h.assertTrue(hunted.containsAll(List.of(centre, left, right)), "each foe hunted by an arrow (" + hunted.size() + ")");
        h.succeed();
    }

    @GameTest
    public void tripleShotAllHuntOneFoe(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        EmiyaEntity emiya = still(h.spawn(FateEntities.EMIYA, 1.5f, 2.0f, 4.5f));
        LivingEntity zombie = still(h.spawn(EntityTypes.ZOMBIE, 7.5f, 2.0f, 4.5f));
        faceEast(emiya);
        emiya.setTarget(zombie);
        h.assertTrue(ArcherBow.triple(emiya), "three arrows loosed");
        List<ProjectedArrowEntity> arrows = level.getEntitiesOfClass(ProjectedArrowEntity.class, emiya.getBoundingBox().inflate(4.0),
            a -> a.getOwner() == emiya);
        boolean all = arrows.size() == 3 && arrows.stream().allMatch(a -> a.target() == zombie);
        // Fanned out: the side arrows leave at an angle to the centre one.
        double spread = 0.0;
        for (ProjectedArrowEntity a : arrows) {
            for (ProjectedArrowEntity b : arrows) spread = Math.max(spread, Math.toDegrees(Math.acos(Math.min(1.0,
                a.getDeltaMovement().normalize().dot(b.getDeltaMovement().normalize())))));
            a.discard();
        }
        h.assertTrue(all, "one foe: all three hunt it");
        h.assertTrue(Math.abs(spread - 2 * ArcherRules.TRIPLE_SPREAD_DEG) < 0.5, "a fan 16 degrees wide (" + spread + ")");
        h.succeed();
    }

    @GameTest
    public void theShroudMendsSlowly(GameTestHelper h) {
        EmiyaEntity emiya = still(h.spawn(FateEntities.EMIYA, 1.5f, 2.0f, 4.5f));
        var s = Kings.of(emiya);
        long t0 = (h.getLevel().getGameTime() / 80 + 10) * 80;
        emiya.setHealth(100.0f);
        s.lastCombat = t0 - 200;
        for (long t = t0; t < t0 + 40; ++t) ArcherPassives.tick(emiya, s, t);
        h.assertTrue(Math.abs(emiya.getHealth() - 110.0f) < 0.01f, "out of combat: 2 s mend 2 hearts, x2.5 for the NPC (" + emiya.getHealth() + ")");
        emiya.setHealth(100.0f);
        s.lastCombat = t0 + 40;
        for (long t = t0 + 40; t < t0 + 120; ++t) ArcherPassives.tick(emiya, s, t);
        h.assertTrue(Math.abs(emiya.getHealth() - 105.0f) < 0.01f, "in combat: 1 heart in 4 s (" + emiya.getHealth() + ")");
        h.succeed();
    }

    @GameTest
    public void aHeldVolleySpreadsApartAndTheNpcReaims(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        GilgameshEntity gil = still(h.spawn(FateEntities.GILGAMESH, 1.5f, 2.0f, 1.5f));
        ArtoriaEntity saber = still(h.spawn(FateEntities.ARTORIA, 6.5f, 2.0f, 6.5f));
        GateOfBabylon.npcVolleyStart(gil, saber);
        GateOfBabylon.growVolley(gil, KingRules.GOB_VOLLEY_FULL);
        List<GatePortalEntity> gates = GateOfBabylon.volley(gil);
        Vec3 eye = gil.getEyePosition();
        double spread = gates.stream().mapToDouble(g -> Math.hypot(g.getX() - eye.x, g.getZ() - eye.z)).max().orElse(0.0);
        double high = gates.stream().mapToDouble(g -> g.getY() - eye.y).max().orElse(0.0);
        double least = Double.MAX_VALUE;
        for (int i = 0; i < gates.size(); ++i) {
            for (int j = i + 1; j < gates.size(); ++j) least = Math.min(least, gates.get(i).position().distanceTo(gates.get(j).position()));
        }
        // She moves while the gates open: they turn on where she is at the release.
        Vec3 moved = h.absoluteVec(new Vec3(4.5, 2.0, 7.5));
        saber.setPos(moved);
        saber.xo = saber.getX();
        saber.yo = saber.getY();
        saber.zo = saber.getZ();
        GateOfBabylon.npcReleaseVolley(gil, saber);
        Vec3 first = gates.isEmpty() ? null : gates.get(0).aimPoint();
        gates.forEach(GatePortalEntity::discard);
        h.assertTrue(gates.size() == KingRules.GOB_VOLLEY_MAX_GATES, "100 gates (" + gates.size() + ")");
        h.assertTrue(spread >= 9.0, "spread wide: " + spread + " blocks out");
        h.assertTrue(high >= 6.0, "and high: " + high);
        h.assertTrue(least > 1.8, "no two gates overlap (closest " + least + ")");
        h.assertTrue(first != null && first.distanceTo(saber.getBoundingBox().getCenter()) < 0.5, "re-aimed at her at the release (" + first + ")");
        h.assertFalse(GateOfBabylon.volleyHeld(gil), "the volley is let go");
        h.succeed();
    }

    @GameTest(maxTicks = 100)
    public void gilgameshOpensWithAFullVolleyBeforeAStrongFoe(GameTestHelper h) {
        GilgameshEntity gil = h.spawn(FateEntities.GILGAMESH, 1.5f, 2.0f, 1.5f);
        ArtoriaEntity saber = still(h.spawn(FateEntities.ARTORIA, 6.5f, 2.0f, 6.5f));
        Kings.of(saber).cooldown(Skills.AVALON_DOME, h.getLevel().getGameTime(), KingRules.AVALON_DOME);
        gil.setTarget(saber);
        // He rises first (up to 2 s), then holds still while the gates open for 4 s.
        h.runAfterDelay(80, () -> {
            boolean held = GateOfBabylon.volleyHeld(gil);
            int n = GateOfBabylon.volley(gil).size();
            double up = gil.getY() - h.absoluteVec(new Vec3(0.0, 2.0, 0.0)).y;
            gil.discard();
            h.assertTrue(held && n > 30, "gates still opening (" + held + ", " + n + ")");
            h.assertTrue(up >= 3.0, "opened aloft (" + up + ")");
            h.assertFalse(GateOfBabylon.volleyHeld(gil), "gone with him");
            h.succeed();
        });
    }

    @GameTest
    public void aWholeSetSwappedAtOnceStillWaits(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(FateItems.KNIGHT_RIBBON));
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(FateItems.KNIGHT_BREASTPLATE));
        p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(FateItems.KNIGHT_SKIRT));
        p.setItemSlot(EquipmentSlot.FEET, new ItemStack(FateItems.KNIGHT_BOOTS));
        Kings.tick(p);
        h.assertTrue(Kings.isKnight(p), "the King of Knights");
        // All four pieces at once (one tick): no straight swap past the 15 s lock.
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(FateItems.SHROUD_HEADPIECE));
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(FateItems.SHROUD_COAT));
        p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(FateItems.SHROUD_LEGGINGS));
        p.setItemSlot(EquipmentSlot.FEET, new ItemStack(FateItems.SHROUD_BOOTS));
        Kings.tick(p);
        h.assertTrue(Kings.of(p).king == KingRules.NONE && Kings.of(p).lockedFrom == KingRules.KNIGHT, "swap lock: no Archer yet ("
            + Kings.of(p).king + ")");
        // Back into her own set: no lock on the same king.
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(FateItems.KNIGHT_RIBBON));
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(FateItems.KNIGHT_BREASTPLATE));
        p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(FateItems.KNIGHT_SKIRT));
        p.setItemSlot(EquipmentSlot.FEET, new ItemStack(FateItems.KNIGHT_BOOTS));
        Kings.tick(p);
        h.assertTrue(Kings.isKnight(p), "back to the same king at once");
        h.succeed();
    }

    @GameTest
    public void aProjectionComesToHandOnAFullHotbar(GameTestHelper h) {
        ServerPlayer p = archer(h);
        for (int i = 0; i < 9; ++i) p.getInventory().setItem(i, new ItemStack(i == 0 ? FateItems.UNLIMITED_BLADE_WORKS : Items.DIRT, 1 + i));
        p.getInventory().setSelectedSlot(0);
        h.assertTrue(Projection.give(p, new ItemStack(Items.DIAMOND_SWORD)), "projected");
        ItemStack hand = p.getMainHandItem();
        h.assertTrue(hand.is(Items.DIAMOND_SWORD) && Projection.projected(hand), "the copy in hand (" + hand + ")");
        h.assertTrue(p.getInventory().getItem(0).is(FateItems.UNLIMITED_BLADE_WORKS), "his own phantasm stays where it was");
        int dirt = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); ++i) if (p.getInventory().getItem(i).is(Items.DIRT)) dirt += p.getInventory().getItem(i).getCount();
        h.assertTrue(dirt == 2 + 3 + 4 + 5 + 6 + 7 + 8 + 9, "the item it replaced went into the inventory (" + dirt + ")");
        h.succeed();
    }

    @GameTest
    public void theHillStartsWithHisOwnArms(GameTestHelper h) {
        ServerPlayer p = archer(h);
        Arsenal.Data data = Arsenal.of(p);
        h.assertTrue(data.seeded && data.entries.size() == Arsenal.defaults().size(), "his own arms on the hill (" + data.entries.size() + ")");
        h.assertTrue(data.selectedStack().is(Items.DIAMOND_SWORD), "the diamond sword ready to project");
        Arsenal.forget(data, 0);
        Arsenal.seed(data, 0L);
        h.assertTrue(data.entries.size() == Arsenal.defaults().size() - 1, "seeded once only: what he forgets stays forgotten");
        h.succeed();
    }

    // ---- 1.1.3 ----

    @GameTest(maxTicks = 80)
    public void theMarbleStopsEveryTreasure(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        EmiyaEntity emiya = still(h.spawn(FateEntities.EMIYA, 3.5f, 2.0f, 3.5f));
        GilgameshEntity gil = still(h.spawn(FateEntities.GILGAMESH, 6.5f, 2.0f, 6.5f));
        UbwEntity marble = UbwEntity.open(level, emiya, 6.0);
        h.runAfterDelay(ArcherRules.UBW_UNFOLD + 2, () -> {
            float hp = emiya.getHealth();
            List<TreasureProjectile> fired = new java.util.ArrayList<>();
            for (int i = 0; i < 12; ++i) {
                Vec3 from = h.absoluteVec(new Vec3(6.5, 3.0 + (i % 3) * 0.4, 6.5 - (i % 4) * 0.3));
                TreasureProjectile t = TreasureProjectile.create(level, gil, from, new ItemStack(Items.IRON_SWORD), TreasureProjectile.NONE, true);
                Vec3 to = emiya.getBoundingBox().getCenter().subtract(from);
                t.shoot(to.x, to.y, to.z, 3.0f, 0.0f);
                level.addFreshEntity(t);
                fired.add(t);
            }
            h.runAfterDelay(6, () -> {
                float after = emiya.getHealth();
                long left = fired.stream().filter(t -> !t.isRemoved()).count();
                marble.discard();
                fired.forEach(TreasureProjectile::discard);
                h.assertTrue(after == hp, "not one treasure reached him (" + hp + " -> " + after + ")");
                h.assertTrue(left == 0, "every treasure was struck down (" + left + " left)");
                h.succeed();
            });
        });
    }

    @GameTest(maxTicks = 120)
    public void theHillsSwordsRiseAimAndStrike(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        EmiyaEntity emiya = still(h.spawn(FateEntities.EMIYA, 1.5f, 2.0f, 1.5f));
        LivingEntity zombie = still(h.spawn(EntityTypes.ZOMBIE, 5.5f, 2.0f, 5.5f));
        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024.0);
        zombie.setHealth(1024.0f);
        UbwEntity marble = UbwEntity.open(level, emiya, 7.0);
        java.util.Set<Integer> phases = new java.util.HashSet<>();
        for (int i = 1; i < 100; ++i) {
            h.runAfterDelay(i, () -> level.getEntitiesOfClass(cn.blockforge.fatekings.entity.UbwSwordEntity.class, marble.getBoundingBox().inflate(12.0),
                x -> true).forEach(x -> phases.add(x.phase())));
        }
        h.runAfterDelay(100, () -> {
            float hp = zombie.getHealth();
            marble.discard();
            h.assertTrue(phases.contains(cn.blockforge.fatekings.entity.UbwSwordEntity.RISING) && phases.contains(cn.blockforge.fatekings.entity.UbwSwordEntity.AIMING)
                && phases.contains(cn.blockforge.fatekings.entity.UbwSwordEntity.FLYING), "rise, take aim, fly (" + phases + ")");
            h.assertTrue(hp < 1024.0f, "the swords strike (" + hp + ", phases " + phases + ")");
            h.succeed();
        });
    }

    @GameTest
    public void theArcherLeapsLikeTheKnight(GameTestHelper h) {
        ServerPlayer p = archer(h);
        p.setDeltaMovement(Vec3.ZERO);
        cn.blockforge.fatekings.knight.KnightLeap.handle(p, 0.0f, 0.0f, true);
        h.assertTrue(p.getDeltaMovement().length() > 1.0, "space: the mana-burst leap (" + p.getDeltaMovement() + ")");
        h.succeed();
    }

    @GameTest
    public void excaliburCutsATrench(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos[] cut = {new BlockPos(3, 5, 3), new BlockPos(3, 3, 3), new BlockPos(4, 5, 4), new BlockPos(5, 7, 3)};
        BlockPos[] kept = {new BlockPos(3, 5, 6), new BlockPos(3, 1, 3), new BlockPos(5, 5, 0)};
        for (BlockPos p : cut) h.setBlock(p, Blocks.STONE);
        for (BlockPos p : kept) h.setBlock(p, Blocks.STONE);
        h.assertTrue(Terrain.enabled(), "terrain effects on");
        ExcaliburWaveEntity.cut(level, h.absoluteVec(new Vec3(0.5, 5.5, 3.5)), h.absoluteVec(new Vec3(6.5, 5.5, 3.5)), 5.0f);
        h.runAfterDelay(3, () -> {
            for (BlockPos p : cut) h.assertBlockNotPresent(Blocks.STONE, p);
            for (BlockPos p : kept) h.assertBlockPresent(Blocks.STONE, p);
            h.succeed();
        });
    }

    @SuppressWarnings("unused")
    private static List<Entity> near(LivingEntity e) {
        return e.level().getEntities(e, e.getBoundingBox().inflate(8.0));
    }
}
