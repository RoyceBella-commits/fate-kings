package cn.blockforge.fatekings.hero;

import cn.blockforge.fatekings.combat.Aim;
import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.combat.Sides;
import cn.blockforge.fatekings.entity.GatePortalEntity;
import cn.blockforge.fatekings.entity.TreasureProjectile;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.KingState;
import cn.blockforge.fatekings.king.Kings;
import cn.blockforge.fatekings.king.Skills;
import cn.blockforge.fatekings.npc.KingNpcEntity;
import cn.blockforge.fatekings.voice.Voice;
import cn.blockforge.fatekings.voice.VoicePlayer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Gate of Babylon: golden ripples open behind the king and treasures fly out at his foes. Tap: 3
 * treasures. Hold: a wall growing from 5 to 100 gates, all fired on release. Sneak: gates all round
 * the target. Everything is aimed the moment the cast begins (for a player: when right-click is
 * pressed): where the gates open, which way they face and the spots they fire at. Facing several
 * foes, the gates take them in turn; the treasures fly straight and never follow anyone.
 */
public final class GateOfBabylon {
    private static final Item[] TREASURES = {Items.IRON_SWORD, Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD,
        Items.IRON_AXE, Items.GOLDEN_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE, Items.TRIDENT, Items.MACE, Items.DIAMOND_SPEAR,
        Items.GOLDEN_SPEAR, Items.NETHERITE_SPEAR};
    /** Treasure speed (blocks per tick), for an NPC's lead. */
    private static final double SPEED = 3.0;
    private static final Map<UUID, List<GatePortalEntity>> VOLLEYS = new ConcurrentHashMap<>();
    /** What a player aimed at when he pressed right-click, kept until he lets go. */
    private static final Map<UUID, Salvo> PRESSED = new ConcurrentHashMap<>();

    /**
     * One cast, aimed once: the king's eye and facing at that moment (the gates open behind that
     * pose) and the spots the treasures fly at, the foe aimed at first.
     */
    public record Salvo(Vec3 eye, Vec3 forward, Vec3 side, List<Vec3> marks) {
        public Vec3 mark(int gate) {
            return this.marks.get(KingRules.gateMark(gate, this.marks.size()));
        }
    }

    private GateOfBabylon() {
    }

    public static void clear() {
        VOLLEYS.clear();
        PRESSED.clear();
    }

    /** Refusal reasons shared by the treasures; returns true if the treasury is open to this caster now. */
    public static boolean treasuryOpen(LivingEntity caster) {
        KingState s = Kings.of(caster);
        if (s == null || !Kings.isHero(caster)) {
            if (caster instanceof Player p) Kings.refuse(p, "fatekings.hint.treasury_closed");
            return false;
        }
        if (s.reorganizing(Kings.now(caster))) {
            if (caster instanceof Player p) Kings.refuse(p, "fatekings.hint.treasury_reorg",
                String.format(java.util.Locale.ROOT, "%.1f", (s.reorgUntil - Kings.now(caster)) / 20.0f));
            return false;
        }
        return true;
    }

    public static boolean ready(LivingEntity caster, String skill) {
        KingState s = Kings.of(caster);
        long now = Kings.now(caster);
        if (s.ready(skill, now)) return true;
        if (caster instanceof Player p) Kings.refuse(p, "fatekings.hint.cooldown", net.minecraft.network.chat.Component.translatable("fatekings.skill." + skill),
            String.format(java.util.Locale.ROOT, "%.1f", s.cooldownLeft(skill, now) / 20.0f));
        return false;
    }

    public static ItemStack randomTreasure(RandomSource random) {
        return new ItemStack(TREASURES[random.nextInt(TREASURES.length)]);
    }

    private static int randomEffect(RandomSource random) {
        return random.nextInt(6);
    }

    // ---- Aiming ----

    /** Right-click pressed: the cast is aimed now, whatever the king does while he holds it. */
    public static void press(LivingEntity caster) {
        cancelVolley(caster);
        PRESSED.put(caster.getUUID(), aim(caster, null, 96.0));
    }

    /** The cast aimed at the press, if one is held. */
    public static Salvo pressed(LivingEntity caster) {
        return PRESSED.get(caster.getUUID());
    }

    /** The press came to nothing (refused, or let go too late for a tap without a volley). */
    public static void forget(LivingEntity caster) {
        PRESSED.remove(caster.getUUID());
    }

    private static Salvo pressedOrNow(LivingEntity caster, double range) {
        return PRESSED.computeIfAbsent(caster.getUUID(), id -> aim(caster, null, range));
    }

    /**
     * Aims a cast now. The foe in the crosshair (an NPC: its target, or {@code primary}) comes first,
     * then up to five more: for a player, hostile things within 40 degrees of his view; for an NPC,
     * whatever it would fight. Each must be in sight. With no foe at all, the crosshair spot.
     */
    public static Salvo aim(LivingEntity caster, LivingEntity primary, double range) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getViewVector(1.0f);
        Vec3 forward = new Vec3(look.x, 0.0, look.z);
        forward = forward.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : forward.normalize();
        Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
        Aim aim = Aim.of(caster, range);
        LivingEntity first = primary != null ? primary : aim.entity();
        List<Vec3> marks = new ArrayList<>();
        for (LivingEntity foe : foes(caster, first, eye, look, range)) marks.add(mark(caster, foe));
        if (marks.isEmpty()) marks.add(aim.point());
        return new Salvo(eye, forward, side, List.copyOf(marks));
    }

    private static List<LivingEntity> foes(LivingEntity caster, LivingEntity first, Vec3 eye, Vec3 look, double range) {
        List<LivingEntity> foes = new ArrayList<>();
        if (first != null && first.isAlive()) foes.add(first);
        boolean npc = caster instanceof KingNpcEntity;
        double reach = npc ? Math.min(range, 32.0) : range;
        List<LivingEntity> more = caster.level().getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(reach),
            e -> e != first && hostileTo(caster, e) && e.distanceToSqr(caster) <= reach * reach
                && (npc || look.dot(e.getBoundingBox().getCenter().subtract(eye).normalize()) >= KingRules.GOB_CONE_COS)
                && caster.hasLineOfSight(e));
        // A player's nearest-to-the-crosshair first; an NPC's nearest first.
        more.sort(Comparator.comparingDouble(e -> npc ? e.distanceToSqr(caster) : -look.dot(e.getBoundingBox().getCenter().subtract(eye).normalize())));
        for (LivingEntity e : more) {
            if (foes.size() >= KingRules.GOB_MAX_TARGETS) break;
            foes.add(e);
        }
        return foes;
    }

    /** NPC: whatever it would fight. Player: monsters and whatever is after him, never players or his own pets. */
    private static boolean hostileTo(LivingEntity caster, LivingEntity e) {
        if (e == caster || !e.isAlive() || e.isSpectator() || caster.isPassengerOfSameVehicle(e)) return false;
        if (caster instanceof KingNpcEntity npc) return npc.canHarm(e);
        if (e instanceof Player || Sides.noncombatant(e)) return false;
        if (e instanceof OwnableEntity pet && pet.getOwner() == caster) return false;
        return e instanceof Enemy || e instanceof Mob m && m.getTarget() == caster;
    }

    /** Where to aim at a foe: where it is; an NPC leads a moving foe once, when it casts. */
    private static Vec3 mark(LivingEntity caster, LivingEntity foe) {
        Vec3 c = foe.getBoundingBox().getCenter();
        if (!(caster instanceof KingNpcEntity)) return c;
        double[] p = KingRules.npcLead(new double[]{c.x, c.y, c.z}, new double[]{foe.getX() - foe.xo, 0.0, foe.getZ() - foe.zo},
            caster.getEyePosition().distanceTo(c), 8, SPEED);
        return new Vec3(p[0], p[1], p[2]);
    }

    /**
     * A point on the wall of gates behind the pose a cast was aimed from. Ring k (from 0) holds 6 + 6k
     * gates, so even 100 gates spread only some 6 blocks round the king; rings alternate a little in depth.
     */
    private static Vec3 wallSpot(Salvo s, int index, RandomSource random) {
        int ring = 0;
        int left = index;
        while (left >= 6 + 6 * ring) {
            left -= 6 + 6 * ring;
            ++ring;
        }
        int inRing = 6 + 6 * ring;
        double angle = left / (double)inRing * Math.PI * 2.0 + ring * 0.37 + random.nextDouble() * 0.12;
        double radius = 1.5 + ring * 0.85 + random.nextDouble() * 0.25;
        double x = Math.cos(angle) * radius;
        // Mostly above the shoulders: the lower half of each ring is folded upwards.
        double y = Math.abs(Math.sin(angle)) * radius * 0.8 + 0.4;
        double depth = 1.2 + (ring % 2) * 0.5 + random.nextDouble() * 0.3;
        return s.eye().add(s.forward().scale(-depth)).add(s.side().scale(x)).add(0.0, y - 0.8, 0.0);
    }

    // ---- Tap ----

    public static boolean tap(LivingEntity caster, int count, boolean single) {
        Salvo s = pressedOrNow(caster, 64.0);
        PRESSED.remove(caster.getUUID());
        if (!treasuryOpen(caster) || !ready(caster, Skills.GOB_TAP)) return false;
        ServerLevel level = (ServerLevel)caster.level();
        Kings.of(caster).cooldown(Skills.GOB_TAP, level.getGameTime(), KingRules.GOB_TAP);
        for (int i = 0; i < count; ++i) {
            Vec3 spot = wallSpot(s, i * 3 + level.getRandom().nextInt(3), level.getRandom());
            GatePortalEntity.open(level, caster, spot, s.mark(i), randomTreasure(level.getRandom()), randomEffect(level.getRandom()), single)
                .release(4 + i * 2);
        }
        return true;
    }

    // ---- Volley ----

    public static void growVolley(LivingEntity caster, int held) {
        if (!(caster.level() instanceof ServerLevel level)) return;
        int wanted = KingRules.volleyGates(held);
        if (wanted <= 0) return;
        List<GatePortalEntity> gates = VOLLEYS.get(caster.getUUID());
        if (gates == null) {
            if (!treasuryOpen(caster) || !ready(caster, Skills.GOB_VOLLEY)) return;
            gates = new ArrayList<>();
            VOLLEYS.put(caster.getUUID(), gates);
        }
        gates.removeIf(g -> g.isRemoved());
        Salvo s = pressedOrNow(caster, 96.0);
        while (gates.size() < wanted) {
            int i = gates.size();
            gates.add(GatePortalEntity.open(level, caster, wallSpot(s, i, level.getRandom()), s.mark(i), randomTreasure(level.getRandom()),
                randomEffect(level.getRandom()), false));
        }
        if (gates.size() >= 15 && held % 20 == 0) {
            Fx.event(level, Fx.SKY_DIM, caster, caster.position(), 40, 0.2f, 96.0);
        }
    }

    public static boolean volleyHeld(LivingEntity caster) {
        return VOLLEYS.containsKey(caster.getUUID());
    }

    /** The gates of the volley being held (empty if none). */
    public static List<GatePortalEntity> volley(LivingEntity caster) {
        List<GatePortalEntity> gates = VOLLEYS.get(caster.getUUID());
        return gates == null ? List.of() : List.copyOf(gates);
    }

    public static void releaseVolley(LivingEntity caster) {
        PRESSED.remove(caster.getUUID());
        List<GatePortalEntity> gates = VOLLEYS.remove(caster.getUUID());
        if (gates == null || !(caster.level() instanceof ServerLevel level)) return;
        gates.removeIf(g -> g.isRemoved());
        if (gates.isEmpty()) return;
        for (int i = 0; i < gates.size(); ++i) gates.get(i).release(4 + i % 8);
        Kings.of(caster).cooldown(Skills.GOB_VOLLEY, level.getGameTime(), KingRules.GOB_VOLLEY);
        if (gates.size() >= 15) {
            VoicePlayer.say(caster, Voice.GIL_VOLLEY);
            Fx.event(level, Fx.SKY_DIM, caster, caster.position(), 50, 0.2f, 96.0);
        }
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.5f, 1.4f);
    }

    public static void cancelVolley(LivingEntity caster) {
        PRESSED.remove(caster.getUUID());
        List<GatePortalEntity> gates = VOLLEYS.remove(caster.getUUID());
        if (gates != null) gates.forEach(GatePortalEntity::discard);
    }

    // ---- Ring ----

    public static boolean ring(LivingEntity caster) {
        if (!treasuryOpen(caster) || !ready(caster, Skills.GOB_RING)) return false;
        ServerLevel level = (ServerLevel)caster.level();
        Aim aim = Aim.of(caster, 64.0);
        LivingEntity target = aim.entity();
        if (target == null) {
            if (caster instanceof Player p) Kings.refuse(p, "fatekings.hint.no_target");
            return false;
        }
        Kings.of(caster).cooldown(Skills.GOB_RING, level.getGameTime(), KingRules.GOB_RING);
        // Encircles where the foe is now; the gates do not turn after it.
        Vec3 c = mark(caster, target);
        int n = 24;
        for (int i = 0; i < n; ++i) {
            // Golden-angle spiral over the upper hemisphere plus a low belt.
            double y = 1.0 - (i + 0.5) / n * 1.3;
            double r = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double a = i * 2.39996;
            Vec3 spot = c.add(Math.cos(a) * r * 7.0, y * 7.0, Math.sin(a) * r * 7.0);
            GatePortalEntity.open(level, caster, spot, c, randomTreasure(level.getRandom()), randomEffect(level.getRandom()), false)
                .release(12 + i % 6);
        }
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 2.0f, 1.2f);
        return true;
    }

    /** NPC shots: a few gates, fired straight away, at its foe first and the others near it in turn. */
    public static void npcShots(LivingEntity caster, LivingEntity target, int gates) {
        if (!(caster.level() instanceof ServerLevel level) || Kings.of(caster).reorganizing(level.getGameTime())) return;
        Salvo s = aim(caster, target, 32.0);
        for (int i = 0; i < gates; ++i) {
            GatePortalEntity.open(level, caster, wallSpot(s, i, level.getRandom()), s.mark(i), randomTreasure(level.getRandom()),
                randomEffect(level.getRandom()), gates <= 3).release(5 + i % 6);
        }
    }

    public static int effectNone() {
        return TreasureProjectile.NONE;
    }
}
