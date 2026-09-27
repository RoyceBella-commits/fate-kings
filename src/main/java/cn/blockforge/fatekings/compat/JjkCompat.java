package cn.blockforge.fatekings.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
            slashMode = Class.forName(PKG + "entity.CurseSlashEntity").getMethod("getMode");
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Gojo x Sukuna slash mode not readable", e);
        }
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
