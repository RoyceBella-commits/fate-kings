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
    /** Ea on creatures (1000): the same death message as {@link #ENUMA_ELISH}, but armour applies. */
    public static final ResourceKey<DamageType> ENUMA_ELISH_MOB = key("enuma_elish_mob");
    /** Excalibur on other mods' creatures (1000): the same death message as {@link #EXCALIBUR}, but armour applies. */
    public static final ResourceKey<DamageType> EXCALIBUR_MOB = key("excalibur_mob");
    /** Caladbolg II on characters (pierces Infinity and armour) and on creatures (armour applies). */
    public static final ResourceKey<DamageType> CALADBOLG = key("caladbolg");
    public static final ResourceKey<DamageType> CALADBOLG_MOB = key("caladbolg_mob");
    /** A projected Excalibur: armour-piercing on characters, but stopped by Infinity. */
    public static final ResourceKey<DamageType> EXCALIBUR_REPLICA = key("excalibur_replica");
    public static final ResourceKey<DamageType> EXCALIBUR_REPLICA_MOB = key("excalibur_replica_mob");
    /** The swords of Unlimited Blade Works: the reality marble's sure hit pierces Infinity. */
    public static final ResourceKey<DamageType> UBW_SWORD = key("ubw_sword");
    /** Kanshou and Bakuya thrown. */
    public static final ResourceKey<DamageType> THROWN_BLADE = key("thrown_blade");
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

    /** The damage type of a noble phantasm on characters (NPCs and players). */
    public static ResourceKey<DamageType> characterType(cn.blockforge.fatekings.combat.JudgementRules.Weapon weapon) {
        return switch (weapon) {
            case EA -> ENUMA_ELISH;
            case EXCALIBUR -> EXCALIBUR;
            case CALADBOLG -> CALADBOLG;
            case EXCALIBUR_REPLICA -> EXCALIBUR_REPLICA;
        };
    }

    /** The damage type of a noble phantasm on creatures (armour applies). */
    public static ResourceKey<DamageType> mobType(cn.blockforge.fatekings.combat.JudgementRules.Weapon weapon) {
        return switch (weapon) {
            case EA -> ENUMA_ELISH_MOB;
            case EXCALIBUR -> EXCALIBUR_MOB;
            case CALADBOLG -> CALADBOLG_MOB;
            case EXCALIBUR_REPLICA -> EXCALIBUR_REPLICA_MOB;
        };
    }

    public static boolean is(DamageSource source, ResourceKey<DamageType> type) {
        return source.typeHolder().is(type);
    }
}
