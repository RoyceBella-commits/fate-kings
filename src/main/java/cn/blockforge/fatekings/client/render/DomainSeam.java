package cn.blockforge.fatekings.client.render;

import cn.blockforge.fatekings.entity.UbwEntity;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Where Unlimited Blade Works meets another domain (the Gojo x Sukuna mod's Unlimited Void or
 * Malevolent Shrine, or another marble): as in that mod, the sky splits along the radical plane of
 * the two spheres and each keeps its own side. Its domains are read by reflection.
 */
public final class DomainSeam {
    private static final Identifier VOID = Identifier.fromNamespaceAndPath("sukuna", "unlimited_void");
    private static final Identifier SHRINE = Identifier.fromNamespaceAndPath("sukuna", "shrine");
    private static final Map<String, Object> REFLECT = new ConcurrentHashMap<>();
    private static final Object MISSING = new Object();

    private DomainSeam() {
    }

    /** Points p with {@code (p - origin) . normal <= offset} are kept (this side of the seam). */
    public record Clip(Vec3 origin, Vec3 normal, double offset) {
        public double side(Vec3 p) {
            return p.subtract(this.origin).dot(this.normal) - this.offset;
        }

        public boolean keeps(Vec3 p) {
            return side(p) <= 0.0;
        }

        /** The point itself if kept, else its foot on the seam. */
        public Vec3 onto(Vec3 p) {
            double s = side(p);
            return s <= 0.0 ? p : p.subtract(this.normal.scale(s));
        }
    }

    /** A domain as a sphere in the world: centre and the radius it is drawn at now; whether it lays its own floor (the shrine's water) there. */
    public record Sphere(Vec3 centre, double radius, boolean floor) {
        public Sphere(Vec3 centre, double radius) {
            this(centre, radius, false);
        }
    }

    /** The marble's meeting with another domain: its seam (relative to the marble) and the other sphere (in the world). */
    public record Meeting(Clip clip, Sphere rival) {
    }

    /** The seam that cuts a sphere (centre c, radius a) against another (centre d, radius b), relative to c; null if they do not meet. */
    public static Clip between(Vec3 c, double a, Vec3 d, double b) {
        Vec3 s = d.subtract(c);
        double dist = s.length();
        if (a < 0.3 || b < 0.3 || dist < 1.0E-3 || dist >= a + b) return null;
        return new Clip(Vec3.ZERO, s.scale(1.0 / dist), (dist * dist + a * a - b * b) / (2.0 * dist));
    }

    /** The marble's side of its seam against the nearest domain it overlaps (relative to the marble), or null. */
    public static Clip forMarble(UbwEntity marble, float partial) {
        Meeting m = meetingOf(marble, partial);
        return m == null ? null : m.clip();
    }

    /** The nearest domain the marble overlaps, with the seam between them; null if none. */
    public static Meeting meetingOf(UbwEntity marble, float partial) {
        double a = marble.visualRadius(partial);
        if (a < 0.3) return null;
        Vec3 c = marble.position();
        Meeting best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : marble.level().getEntities(marble, marble.getBoundingBox().inflate(a + 72.0), e -> !e.isRemoved())) {
            Sphere s = sphereOf(e, partial);
            if (s == null) continue;
            double dist = s.centre().distanceTo(c);
            if (dist >= bestDist) continue;
            Clip clip = between(c, a, s.centre(), s.radius());
            if (clip == null) continue;
            best = new Meeting(clip, s);
            bestDist = dist;
        }
        return best;
    }

    /** The nearest open marble meeting a domain sphere (centre, radius), for that domain's side of the seam; null if none. */
    public static Sphere marbleMeeting(Level level, Vec3 centre, double radius, float partial) {
        Sphere best = null;
        double bestDist = Double.MAX_VALUE;
        for (UbwEntity u : level.getEntitiesOfClass(UbwEntity.class, new AABB(centre, centre).inflate(radius + 72.0), u -> !u.isRemoved())) {
            double b = u.visualRadius(partial);
            double dist = u.position().distanceTo(centre);
            if (b < 0.3 || dist >= radius + b || dist >= bestDist) continue;
            best = new Sphere(u.position(), b);
            bestDist = dist;
        }
        return best;
    }

    // ---- The Gojo x Sukuna mod's side of the seam (called from its renderers by compat mixins) ----

    /**
     * For an Unlimited Void (centre {@code c} relative to the entity, radius) with no Malevolent Shrine
     * against it: its clip ({@code SlashShader.Clip}, made by reflection) against a marble it meets, or null.
     */
    public static Object voidClip(Entity voidDomain, Vec3 c, float radius, float delta) {
        Vec3 pos = voidDomain.getPosition(delta);
        Sphere m = marbleMeeting(voidDomain.level(), pos.add(c), radius, delta);
        if (m == null) return null;
        Vec3 s = m.centre().subtract(pos).subtract(c);
        double dist = s.length();
        if (dist < 1.0E-3) return null;
        try {
            Object ctor = REFLECT.computeIfAbsent("SlashShader$Clip#<init>", k -> {
                try {
                    return Class.forName("cn.blockforge.ryomensukuna.m2a542fea.client.render.SlashShader$Clip")
                        .getConstructor(Vec3.class, Vec3.class, double.class);
                } catch (ReflectiveOperationException ex) {
                    return MISSING;
                }
            });
            if (ctor == MISSING) return null;
            return ((java.lang.reflect.Constructor<?>)ctor).newInstance(c, s.scale(1.0 / dist), (dist * dist + radius * radius - m.radius() * m.radius()) / (2.0 * dist));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    /**
     * For the Malevolent Shrine the camera is in (the nearest, as that mod picks it): a marble it meets,
     * centre relative to the shrine's origin, or null. Its shader then gives the marble its side of the seam.
     */
    public static Sphere marbleAgainstShrine() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return null;
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Entity shrine = null;
        double best = Double.MAX_VALUE;
        for (Entity e : mc.level.getEntities((Entity)null, new AABB(cam, cam).inflate(256.0), e -> SHRINE.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType())))) {
            double d = e.distanceToSqr(cam);
            if (d < best) {
                best = d;
                shrine = e;
            }
        }
        if (shrine == null) return null;
        Sphere s = sphereOf(shrine, partial);
        if (s == null) return null;
        Sphere m = marbleMeeting(mc.level, s.centre(), s.radius(), partial);
        if (m == null) return null;
        // Inside the marble but outside the shrine, the shrine's shader sees only the near face of its
        // sphere, which lies on the marble's side: it would leave a hole. Let it draw its whole sphere then.
        if (cam.distanceToSqr(m.centre()) < m.radius() * m.radius() && cam.distanceToSqr(s.centre()) >= s.radius() * s.radius()) return null;
        return new Sphere(m.centre().subtract(s.centre()), m.radius());
    }

    /** Another domain's sphere: a marble, an Unlimited Void (while it stands) or a Malevolent Shrine; null for anything else. */
    public static Sphere sphereOf(Entity e, float partial) {
        if (e instanceof UbwEntity u) {
            double r = u.visualRadius(partial);
            return r < 0.3 ? null : new Sphere(u.position(), r);
        }
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        try {
            if (VOID.equals(id)) {
                if (((Number)method(e, "closeAt").invoke(e)).intValue() >= 0) return null;
                double open = ((Number)staticMethod(e, "openFraction", float.class).invoke(null, ((Number)method(e, "age").invoke(e)).floatValue() + partial)).doubleValue();
                double r = staticField(e, "RADIUS").getDouble(null) * open;
                return r < 0.3 ? null : new Sphere(e.position().add(0.0, 0.5, 0.0), r);
            }
            if (SHRINE.equals(id)) {
                float life = ((Number)method(e, "getVisualLife", float.class).invoke(e, partial)).floatValue();
                double x = Math.max(0.0, Math.min(1.0, (life - 35.0) / 65.0));
                double r = field(e, "radius").getDouble(e) * x * x * (3.0 - 2.0 * x);
                return r < 0.3 ? null : new Sphere(e.position().add(0.0, 0.035, 0.0), r, true);
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
        return null;
    }

    private static Method method(Entity e, String name, Class<?>... args) throws NoSuchMethodException {
        Object m = REFLECT.computeIfAbsent(e.getClass().getName() + "#" + name, k -> {
            try {
                return e.getClass().getMethod(name, args);
            } catch (NoSuchMethodException ex) {
                return MISSING;
            }
        });
        if (m == MISSING) throw new NoSuchMethodException(name);
        return (Method)m;
    }

    private static Method staticMethod(Entity e, String name, Class<?>... args) throws NoSuchMethodException {
        return method(e, name, args);
    }

    private static Field field(Entity e, String name) throws NoSuchFieldException {
        Object f = REFLECT.computeIfAbsent(e.getClass().getName() + "." + name, k -> {
            try {
                return e.getClass().getField(name);
            } catch (NoSuchFieldException ex) {
                return MISSING;
            }
        });
        if (f == MISSING) throw new NoSuchFieldException(name);
        return (Field)f;
    }

    private static Field staticField(Entity e, String name) throws NoSuchFieldException {
        return field(e, name);
    }
}
