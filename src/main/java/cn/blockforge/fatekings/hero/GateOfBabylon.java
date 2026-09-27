package cn.blockforge.fatekings.hero;

import cn.blockforge.fatekings.combat.Aim;
import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.entity.GatePortalEntity;
import cn.blockforge.fatekings.entity.TreasureProjectile;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.KingState;
import cn.blockforge.fatekings.king.Kings;
import cn.blockforge.fatekings.king.Skills;
import cn.blockforge.fatekings.voice.Voice;
import cn.blockforge.fatekings.voice.VoicePlayer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Gate of Babylon: golden ripples open behind the king and treasures fly out at what he looks at.
 * Tap: 3 treasures. Hold: a wall growing from 5 to 40 gates, all fired on release. Sneak: gates all
 * round the target.
 */
public final class GateOfBabylon {
    private static final Item[] TREASURES = {Items.IRON_SWORD, Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD,
        Items.IRON_AXE, Items.GOLDEN_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE, Items.TRIDENT, Items.MACE, Items.DIAMOND_SPEAR,
        Items.GOLDEN_SPEAR, Items.NETHERITE_SPEAR};
    private static final Map<UUID, List<GatePortalEntity>> VOLLEYS = new ConcurrentHashMap<>();

    private GateOfBabylon() {
    }

    public static void clear() {
        VOLLEYS.clear();
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

    /**
     * A point on the wall of gates behind the caster. Ring k (from 0) holds 6 + 6k gates, so even
     * 100 gates spread only some 6 blocks round the king; rings alternate a little in depth.
     */
    private static Vec3 wallSpot(LivingEntity caster, int index, RandomSource random) {
        Vec3 look = caster.getViewVector(1.0f);
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 side = new Vec3(-flat.z, 0.0, flat.x);
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
        return caster.getEyePosition().add(flat.scale(-depth)).add(side.scale(x)).add(0.0, y - 0.8, 0.0);
    }

    // ---- Tap ----

    public static boolean tap(LivingEntity caster, int count, boolean single) {
        if (!treasuryOpen(caster) || !ready(caster, Skills.GOB_TAP)) return false;
        ServerLevel level = (ServerLevel)caster.level();
        KingState s = Kings.of(caster);
        s.cooldown(Skills.GOB_TAP, level.getGameTime(), KingRules.GOB_TAP);
        Aim aim = Aim.of(caster, 64.0);
        for (int i = 0; i < count; ++i) {
            Vec3 spot = wallSpot(caster, i * 3 + level.getRandom().nextInt(3), level.getRandom());
            GatePortalEntity g = GatePortalEntity.open(level, caster, spot, aim.point().subtract(spot), randomTreasure(level.getRandom()), randomEffect(level.getRandom()), single);
            g.release(aim.entity(), aim.point(), 4 + i * 2);
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
        Aim aim = Aim.of(caster, 96.0);
        while (gates.size() < wanted) {
            Vec3 spot = wallSpot(caster, gates.size(), level.getRandom());
            gates.add(GatePortalEntity.open(level, caster, spot, aim.point().subtract(spot), randomTreasure(level.getRandom()), randomEffect(level.getRandom()), false));
        }
        for (GatePortalEntity g : gates) g.face(aim.point().subtract(g.position()));
        if (gates.size() >= 15 && held % 20 == 0) {
            Fx.event(level, Fx.SKY_DIM, caster, caster.position(), 40, 0.2f, 96.0);
        }
    }

    public static boolean volleyHeld(LivingEntity caster) {
        return VOLLEYS.containsKey(caster.getUUID());
    }

    public static void releaseVolley(LivingEntity caster) {
        List<GatePortalEntity> gates = VOLLEYS.remove(caster.getUUID());
        if (gates == null || !(caster.level() instanceof ServerLevel level)) return;
        gates.removeIf(g -> g.isRemoved());
        if (gates.isEmpty()) return;
        Aim aim = Aim.of(caster, 128.0);
        for (int i = 0; i < gates.size(); ++i) gates.get(i).release(aim.entity(), aim.point(), 4 + i % 8);
        Kings.of(caster).cooldown(Skills.GOB_VOLLEY, level.getGameTime(), KingRules.GOB_VOLLEY);
        if (gates.size() >= 15) {
            VoicePlayer.say(caster, Voice.GIL_VOLLEY);
            Fx.event(level, Fx.SKY_DIM, caster, caster.position(), 50, 0.2f, 96.0);
        }
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.5f, 1.4f);
    }

    public static void cancelVolley(LivingEntity caster) {
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
        Vec3 c = target.getBoundingBox().getCenter();
        int n = 24;
        for (int i = 0; i < n; ++i) {
            // Golden-angle spiral over the upper hemisphere plus a low belt.
            double y = 1.0 - (i + 0.5) / n * 1.3;
            double r = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double a = i * 2.39996;
            Vec3 spot = c.add(Math.cos(a) * r * 7.0, y * 7.0, Math.sin(a) * r * 7.0);
            GatePortalEntity g = GatePortalEntity.open(level, caster, spot, c.subtract(spot), randomTreasure(level.getRandom()), randomEffect(level.getRandom()), false);
            g.release(target, c, 12 + i % 6);
        }
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 2.0f, 1.2f);
        return true;
    }

    /** NPC casual shots: a few gates, fired straight away. */
    public static void npcShots(LivingEntity caster, LivingEntity target, int gates) {
        if (!(caster.level() instanceof ServerLevel level) || Kings.of(caster).reorganizing(level.getGameTime())) return;
        Vec3 c = target.getBoundingBox().getCenter();
        for (int i = 0; i < gates; ++i) {
            Vec3 spot = wallSpot(caster, i, level.getRandom());
            GatePortalEntity g = GatePortalEntity.open(level, caster, spot, c.subtract(spot), randomTreasure(level.getRandom()), randomEffect(level.getRandom()), gates <= 3);
            g.release(target, c, 5 + i % 6);
        }
    }

    public static int effectNone() {
        return TreasureProjectile.NONE;
    }
}
