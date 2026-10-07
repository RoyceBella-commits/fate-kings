package cn.blockforge.fatekings.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Soft compatibility with the Gojo x Sukuna mod ("sukuna"). That mod has no addon API, so it is
 * read through its registry ids, its player attachment and a few public static methods (its class
 * names are not obfuscated). Everything degrades to "not there" when the mod is absent or changes.
 */
public final class JjkCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger("FateKings/JjkCompat");
    public static final String NS = "sukuna";
    private static final String PKG = "cn.blockforge.ryomensukuna.m2a542fea.";
    public static final boolean LOADED = FabricLoader.getInstance().isModLoaded(NS);

    public static final Identifier GOJO = Identifier.fromNamespaceAndPath(NS, "gojo");
    public static final Identifier SUKUNA = Identifier.fromNamespaceAndPath(NS, "sukuna");
    public static final Identifier MAHORAGA = Identifier.fromNamespaceAndPath(NS, "mahoraga");
    public static final Identifier UNLIMITED_VOID = Identifier.fromNamespaceAndPath(NS, "unlimited_void");
    public static final Identifier SHRINE = Identifier.fromNamespaceAndPath(NS, "shrine");
    public static final Identifier MURASAKI = Identifier.fromNamespaceAndPath(NS, "murasaki");
    public static final Identifier CURSE_SLASH = Identifier.fromNamespaceAndPath(NS, "curse_slash");
    public static final Identifier WORLD_CUT = Identifier.fromNamespaceAndPath(NS, "world_cut");
    public static final Identifier SURE_HIT = Identifier.fromNamespaceAndPath(NS, "sure_hit");

    private static boolean resolved;
    private static AttachmentType<Object> curse;
    private static Method route, stage, withGold, setState, sync, goldHpTable, infinityActive, activeSkill, terrain, setTerrain, slashMode;
    private static Field burstHeal;
    private static Method damageTaken;
    private static Method domainRegister, domainUnregister, domainOf, domainActive, domainRival;
    private static Field domainOwner, domainOpenedAt;

    private JjkCompat() {
    }

    @SuppressWarnings("unchecked")
    private static synchronized void resolve() {
        if (resolved) return;
        resolved = true;
        if (!LOADED) return;
        try {
            Class<?> manager = Class.forName(PKG + "skill.CurseManager");
            curse = (AttachmentType<Object>)manager.getField("CURSE").get(null);
            Class<?> state = Class.forName(PKG + "skill.CurseState");
            route = state.getMethod("route");
            stage = state.getMethod("stage");
            withGold = state.getMethod("withGold", float.class);
            setState = manager.getMethod("setState", ServerPlayer.class, state);
            sync = manager.getMethod("sync", ServerPlayer.class);
            goldHpTable = Class.forName(PKG + "progression.StageRules").getMethod("goldHp", int.class);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Gojo x Sukuna player state not readable; routes are ignored", e);
            curse = null;
        }
        try {
            infinityActive = Class.forName(PKG + "gojo.Infinity").getMethod("active", ServerPlayer.class);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Gojo x Sukuna Infinity not readable", e);
        }
        try {
            activeSkill = Class.forName(PKG + "skill.ChannelCasting").getMethod("activeSkill", ServerPlayer.class);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Gojo x Sukuna channel casting not readable", e);
        }
        try {
            Class<?> cfg = Class.forName(PKG + "config.JjkConfig");
            terrain = cfg.getMethod("terrainDestruction");
            setTerrain = cfg.getMethod("setTerrainDestruction", boolean.class);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Gojo x Sukuna terrain switch not readable", e);
        }
        try {
            burstHeal = Class.forName(PKG + "entity.npc.JjkNpcEntity").getDeclaredField("burstHealCooldown");
            burstHeal.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Gojo x Sukuna NPC healing not adjustable", e);
        }
        try {
            Class<?> clash = Class.forName(PKG + "combat.DomainClash");
            Class<?> domain = Class.forName(PKG + "combat.DomainClash$Domain");
            domainRegister = clash.getMethod("register", LivingEntity.class, Entity.class, Vec3.class, double.class, boolean.class);
            domainUnregister = clash.getMethod("unregister", Entity.class);
            domainOf = clash.getMethod("of", Entity.class);
            domainActive = clash.getMethod("active");
            domainRival = domain.getMethod("rival");
            domainOwner = domain.getField("owner");
            try {
                domainOpenedAt = domain.getDeclaredField("openedAt");
                domainOpenedAt.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                domainOpenedAt = null;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Gojo x Sukuna domain clash not reachable; Unlimited Blade Works will not clash with domains", e);
            domainRegister = null;
        }
        try {
            damageTaken = Class.forName(PKG + "combat.DamageTaken").getMethod("apply", LivingEntity.class, DamageSource.class, float.class);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Gojo x Sukuna damage shares not readable", e);
        }
        try {
            slashMode = Class.forName(PKG + "entity.CurseSlashEntity").getMethod("getMode");
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Gojo x Sukuna slash mode not readable", e);
        }
    }

    /** The share of this hit the Gojo x Sukuna mod lets through to {@code target} (1 when it does not scale it). */
    public static float takenScale(LivingEntity target, DamageSource source) {
        if (!LOADED) return 1.0f;
        resolve();
        if (damageTaken == null) return 1.0f;
        try {
            return (Float)damageTaken.invoke(null, target, source, 1.0f);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 1.0f;
        }
    }

    // ---- Domain clash (Unlimited Blade Works counts as a domain) ----

    /** Registers a reality marble with the other mod's domain clash (closed: no blink across its wall). */
    public static void registerDomain(LivingEntity caster, Entity entity, Vec3 centre, double radius, boolean closed) {
        if (!LOADED) return;
        resolve();
        if (domainRegister == null) return;
        try {
            domainRegister.invoke(null, caster, entity, centre, radius, closed);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Could not register a domain", e);
        }
    }

    public static void unregisterDomain(Entity entity) {
        if (!LOADED || domainUnregister == null) return;
        try {
            domainUnregister.invoke(null, entity);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    /** Whether the other mod knows {@code entity} as a domain. */
    public static boolean domainRegistered(Entity entity) {
        return domainHandle(entity) != null;
    }

    private static Object domainHandle(Entity entity) {
        if (!LOADED) return null;
        resolve();
        if (domainOf == null) return null;
        try {
            return domainOf.invoke(null, entity);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** The owner of the domain {@code entity} is clashing with, or null. */
    public static UUID domainRival(Entity entity) {
        Object d = domainHandle(entity);
        if (d == null || domainRival == null) return null;
        try {
            return (UUID)domainRival.invoke(d);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** When the domain of {@code owner} opened (game time), or -1. */
    public static long domainOpenedAt(UUID owner) {
        if (!LOADED || domainActive == null || domainOwner == null || domainOpenedAt == null) return -1L;
        try {
            for (Object d : (java.util.List<?>)domainActive.invoke(null)) {
                if (owner.equals(domainOwner.get(d))) return domainOpenedAt.getLong(d);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return -1L;
    }

    public static Identifier typeId(Entity e) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
    }

    public static boolean is(Entity e, Identifier type) {
        return e != null && type.equals(typeId(e));
    }

    public static boolean fromJjk(Entity e) {
        return e != null && NS.equals(typeId(e).getNamespace());
    }

    private static Object curseState(Player p) {
        resolve();
        if (curse == null || !(p instanceof ServerPlayer)) return null;
        try {
            return p.getAttached(curse);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int intOf(Object state, Method m) {
        if (state == null || m == null) return 0;
        try {
            return (int)m.invoke(state);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0;
        }
    }

    /** 0 none, 1 Gojo, 2 Sukuna (server side only). */
    public static int route(Player p) {
        return intOf(curseState(p), route);
    }

    public static int stage(Player p) {
        return intOf(curseState(p), stage);
    }

    public static boolean awakened(Player p) {
        return route(p) != 0;
    }

    /** Gold hearts (HP) the other mod grants at the player's current stage. */
    public static float goldMax(Player p) {
        Object s = curseState(p);
        if (s == null || goldHpTable == null) return 0.0f;
        int st = intOf(s, stage);
        if (intOf(s, route) == 0) return 0.0f;
        try {
            return (float)goldHpTable.invoke(null, st);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0.0f;
        }
    }

    /** Empties the other mod's gold hearts (Excalibur on Gojo: "金心清零"). */
    public static void clearGold(ServerPlayer p) {
        Object s = curseState(p);
        if (s == null || withGold == null || setState == null) return;
        try {
            setState.invoke(null, p, withGold.invoke(s, 0.0f));
            if (sync != null) sync.invoke(null, p);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.debug("clearGold failed", e);
        }
    }

    /** Whether Infinity is up on this entity right now. */
    public static boolean infinityUp(LivingEntity e) {
        if (!LOADED || e == null || !e.isAlive()) return false;
        if (is(e, GOJO)) return true;
        if (!(e instanceof ServerPlayer p)) return false;
        resolve();
        if (infinityActive == null) return false;
        try {
            return (boolean)infinityActive.invoke(null, p);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    /** The technique a player is charging (enum name), or null. */
    public static String chargingSkill(ServerPlayer p) {
        if (!LOADED) return null;
        resolve();
        if (activeSkill == null) return null;
        try {
            Object skill = activeSkill.invoke(null, p);
            return skill instanceof Enum<?> en ? en.name() : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** The other mod's terrain switch, or null when it is not installed. */
    public static Boolean terrain() {
        if (!LOADED) return null;
        resolve();
        if (terrain == null) return null;
        try {
            return (Boolean)terrain.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    public static void setTerrain(boolean on) {
        if (!LOADED) return;
        resolve();
        if (setTerrain == null) return;
        try {
            setTerrain.invoke(null, on);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.debug("setTerrain failed", e);
        }
    }

    /** Stops a Gojo / Sukuna NPC from burst-healing out of Excalibur's wound for {@code ticks}. */
    public static void holdBurstHeal(Entity npc, int ticks) {
        if (!LOADED || !fromJjk(npc)) return;
        resolve();
        if (burstHeal == null || !burstHeal.getDeclaringClass().isInstance(npc)) return;
        try {
            burstHeal.setInt(npc, Math.max(burstHeal.getInt(npc), ticks));
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.debug("holdBurstHeal failed", e);
        }
    }

    /** World Cut is a curse slash entity in mode 2. */
    public static boolean worldCutSlash(Entity e) {
        if (!is(e, CURSE_SLASH)) return false;
        resolve();
        if (slashMode == null) return false;
        try {
            return (int)slashMode.invoke(e) == 2;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    public static boolean domain(Entity e) {
        return is(e, UNLIMITED_VOID) || is(e, SHRINE);
    }
}
