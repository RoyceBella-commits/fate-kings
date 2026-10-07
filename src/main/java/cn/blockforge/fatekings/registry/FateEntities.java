package cn.blockforge.fatekings.registry;

import cn.blockforge.fatekings.FateKings;
import cn.blockforge.fatekings.entity.CaladbolgEntity;
import cn.blockforge.fatekings.entity.ChainEntity;
import cn.blockforge.fatekings.entity.EnumaElishEntity;
import cn.blockforge.fatekings.entity.ExcaliburWaveEntity;
import cn.blockforge.fatekings.entity.GatePortalEntity;
import cn.blockforge.fatekings.entity.ProjectedArrowEntity;
import cn.blockforge.fatekings.entity.StrikeAirEntity;
import cn.blockforge.fatekings.entity.SwordQiEntity;
import cn.blockforge.fatekings.entity.ThrownBladeEntity;
import cn.blockforge.fatekings.entity.TreasureProjectile;
import cn.blockforge.fatekings.entity.UbwEntity;
import cn.blockforge.fatekings.entity.UbwSwordEntity;
import cn.blockforge.fatekings.entity.VimanaEntity;
import cn.blockforge.fatekings.npc.ArtoriaEntity;
import cn.blockforge.fatekings.npc.EmiyaEntity;
import cn.blockforge.fatekings.npc.GilgameshEntity;
import cn.blockforge.fatekings.npc.KingNpcEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class FateEntities {
    public static final EntityType<GilgameshEntity> GILGAMESH = register("gilgamesh",
        EntityType.Builder.<GilgameshEntity>of(GilgameshEntity::new, MobCategory.MONSTER).sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10));
    public static final EntityType<ArtoriaEntity> ARTORIA = register("artoria",
        EntityType.Builder.<ArtoriaEntity>of(ArtoriaEntity::new, MobCategory.CREATURE).sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10));
    public static final EntityType<EmiyaEntity> EMIYA = register("emiya",
        EntityType.Builder.<EmiyaEntity>of(EmiyaEntity::new, MobCategory.CREATURE).sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10));
    public static final EntityType<TreasureProjectile> TREASURE = register("treasure",
        EntityType.Builder.<TreasureProjectile>of(TreasureProjectile::new, MobCategory.MISC).sized(0.5f, 0.5f).clientTrackingRange(8).updateInterval(2).fireImmune().noSave());
    public static final EntityType<GatePortalEntity> GATE_PORTAL = register("gate_portal",
        EntityType.Builder.<GatePortalEntity>of(GatePortalEntity::new, MobCategory.MISC).sized(0.5f, 0.5f).clientTrackingRange(8).updateInterval(5).fireImmune().noSave().noSummon());
    public static final EntityType<ChainEntity> CHAIN = register("chain",
        EntityType.Builder.<ChainEntity>of(ChainEntity::new, MobCategory.MISC).sized(0.3f, 0.3f).clientTrackingRange(8).updateInterval(2).fireImmune().noSave().noSummon());
    public static final EntityType<VimanaEntity> VIMANA = register("vimana",
        EntityType.Builder.<VimanaEntity>of(VimanaEntity::new, MobCategory.MISC).sized(2.2f, 1.1f).clientTrackingRange(10).updateInterval(1).fireImmune().noSummon());
    public static final EntityType<EnumaElishEntity> ENUMA_ELISH = register("enuma_elish",
        EntityType.Builder.<EnumaElishEntity>of(EnumaElishEntity::new, MobCategory.MISC).sized(1.0f, 1.0f).clientTrackingRange(16).updateInterval(1).fireImmune().noSave().noSummon());
    public static final EntityType<ExcaliburWaveEntity> EXCALIBUR_WAVE = register("excalibur_wave",
        EntityType.Builder.<ExcaliburWaveEntity>of(ExcaliburWaveEntity::new, MobCategory.MISC).sized(1.0f, 1.0f).clientTrackingRange(16).updateInterval(1).fireImmune().noSave().noSummon());
    public static final EntityType<SwordQiEntity> SWORD_QI = register("sword_qi",
        EntityType.Builder.<SwordQiEntity>of(SwordQiEntity::new, MobCategory.MISC).sized(1.0f, 1.0f).clientTrackingRange(8).updateInterval(1).fireImmune().noSave().noSummon());
    public static final EntityType<StrikeAirEntity> STRIKE_AIR = register("strike_air",
        EntityType.Builder.<StrikeAirEntity>of(StrikeAirEntity::new, MobCategory.MISC).sized(1.2f, 1.2f).clientTrackingRange(8).updateInterval(1).fireImmune().noSave().noSummon());

    public static final EntityType<ProjectedArrowEntity> PROJECTED_ARROW = register("projected_arrow",
        EntityType.Builder.<ProjectedArrowEntity>of(ProjectedArrowEntity::new, MobCategory.MISC).sized(0.5f, 0.5f).clientTrackingRange(8).updateInterval(1).fireImmune().noSave());
    public static final EntityType<CaladbolgEntity> CALADBOLG = register("caladbolg",
        EntityType.Builder.<CaladbolgEntity>of(CaladbolgEntity::new, MobCategory.MISC).sized(1.0f, 1.0f).clientTrackingRange(16).updateInterval(1).fireImmune().noSave().noSummon());
    public static final EntityType<ThrownBladeEntity> THROWN_BLADE = register("thrown_blade",
        EntityType.Builder.<ThrownBladeEntity>of(ThrownBladeEntity::new, MobCategory.MISC).sized(0.6f, 0.6f).clientTrackingRange(8).updateInterval(1).fireImmune().noSave().noSummon());
    public static final EntityType<UbwEntity> UBW = register("unlimited_blade_works",
        EntityType.Builder.<UbwEntity>of(UbwEntity::new, MobCategory.MISC).sized(1.0f, 1.0f).clientTrackingRange(16).updateInterval(5).fireImmune().noSave().noSummon());
    public static final EntityType<UbwSwordEntity> UBW_SWORD = register("ubw_sword",
        EntityType.Builder.<UbwSwordEntity>of(UbwSwordEntity::new, MobCategory.MISC).sized(0.5f, 0.5f).clientTrackingRange(8).updateInterval(1).fireImmune().noSave().noSummon());

    private FateEntities() {
    }

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, FateKings.id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(GILGAMESH, KingNpcEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ARTORIA, KingNpcEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(EMIYA, KingNpcEntity.createAttributes());
    }
}
