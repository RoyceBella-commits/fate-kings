package cn.blockforge.fatekings.registry;

import cn.blockforge.fatekings.FateKings;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** Data-driven damage types (data/fatekings/damage_type/*.json). */
public final class FateDamage {
    /** Enuma Elish on anything: pierces Infinity, still subject to each side's taken share. */
    public static final ResourceKey<DamageType> ENUMA_ELISH = key("enuma_elish");
    /** Excalibur on characters: pierces Infinity, still subject to each side's taken share. */
    public static final ResourceKey<DamageType> EXCALIBUR = key("excalibur");
    /** Excalibur on vanilla creatures: bypasses invulnerability, armour, resistance and shields. */
    public static final ResourceKey<DamageType> JUDGEMENT = key("excalibur_judgement");
    /** Heaven's Chain tightening around a being of higher standing. */
    public static final ResourceKey<DamageType> CHAIN = key("heavens_chain");
    /** The golden crescent cut loose by the revealed Excalibur. */
    public static final ResourceKey<DamageType> SWORD_QI = key("sword_qi");
    /** Mana Burst charge and Ea's wind pressure. */
    public static final ResourceKey<DamageType> MANA_BURST = key("mana_burst");

    private FateDamage() {
    }

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, FateKings.id(name));
    }

    public static DamageSource source(Level level, ResourceKey<DamageType> type, Entity direct, Entity attacker) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type), direct, attacker);
    }

    public static boolean is(DamageSource source, ResourceKey<DamageType> type) {
        return source.typeHolder().is(type);
    }
}
