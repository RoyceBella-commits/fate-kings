package cn.blockforge.fatekings.archer;

import cn.blockforge.fatekings.combat.Aim;
import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.combat.Judgement;
import cn.blockforge.fatekings.king.Kings;
import cn.blockforge.fatekings.net.FateNet;
import cn.blockforge.fatekings.npc.KingNpcEntity;
import cn.blockforge.fatekings.registry.FateItems;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Kanshou and Bakuya in the hand: a six-stroke combo (right slash, left backhand, cross, spinning
 * sweep, downward chop, rising cut) the client picks and everyone sees. Each stroke has its own
 * bite: the side strokes and the spin catch whoever stands round him, the cross strikes twice, the
 * chop bites deeper and slows, the rising cut lifts.
 */
public final class TwinBlades {
    /** Swing styles sent with each stroke. */
    public static final int STYLE_EXCALIBUR = 0;
    public static final int STYLE_TWIN = 1;
    public static final int STYLE_BOW = 2;

    private record Last(int step, long tick, float strength) {
    }

    private record Pending(UUID attacker, int target, long due, float amount) {
    }

    private static final Map<UUID, Last> LAST = new ConcurrentHashMap<>();
    private static final List<Pending> PENDING = new ArrayList<>();

    private TwinBlades() {
    }

    public static synchronized void clear() {
        LAST.clear();
        PENDING.clear();
    }

    /** From an Archer's client: the stroke he is about to make. */
    public static void handle(ServerPlayer p, int step) {
        if (!Kings.isArcher(p) || !FateItems.twinSword(p.getMainHandItem()) || p.isUsingItem()) return;
        if (step < 0 || step >= ArcherRules.COMBO_STEPS) return;
        long now = p.level().getGameTime();
        Last last = LAST.get(p.getUUID());
        if (last != null && now - last.tick < ArcherRules.TWIN_GAP) return;
        // Read the charge now: vanilla resets it when the blow itself lands right after.
        float strength = p.getAttackStrengthScale(0.5f);
        LAST.put(p.getUUID(), new Last(step, now, strength));
        LivingEntity aimed = Aim.of(p, 4.5).entity();
        sweep(p.level(), p, step, aimed, strength);
        broadcast(p.level(), p, STYLE_TWIN, 0.0f, step, true);
        strokeFx(p.level(), p, step);
    }

    /** The struck foe of the stroke he just made (from the blade's post-hit). */
    public static void onHit(LivingEntity attacker, LivingEntity target) {
        Last last = LAST.get(attacker.getUUID());
        if (last == null || attacker.level().getGameTime() - last.tick > 10) return;
        landed(attacker, target, last.step, last.strength);
    }

    /** An NPC's stroke: the whole of it at once. */
    public static void npcStroke(KingNpcEntity npc, LivingEntity target, int step) {
        if (!(npc.level() instanceof ServerLevel level)) return;
        LAST.put(npc.getUUID(), new Last(step, level.getGameTime(), 1.0f));
        sweep(level, npc, step, target, 1.0f);
        broadcast(level, npc, STYLE_TWIN, 0.0f, step, false);
        strokeFx(level, npc, step);
        landed(npc, target, step, 1.0f);
    }

    private static void landed(LivingEntity attacker, LivingEntity target, int step, float strength) {
        if (!(attacker.level() instanceof ServerLevel level) || !target.isAlive()) return;
        float swing = ArcherRules.TWIN_SWING * Math.max(0.2f, strength);
        float bonus = ArcherRules.strokeBonus(step);
        if (bonus > 0.0f) hurt(level, attacker, target, swing * bonus);
        if (step == ArcherRules.STEP_CHOP) target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 1), attacker);
        if (step == ArcherRules.STEP_RISE && !cn.blockforge.fatekings.combat.Sides.boss(target)) {
            target.push(0.0, 0.7, 0.0);
            target.needsSync = true;
        }
        float second = ArcherRules.secondHit(step);
        if (second > 0.0f) {
            synchronized (TwinBlades.class) {
                PENDING.add(new Pending(attacker.getUUID(), target.getId(), level.getGameTime() + 3, swing * second));
            }
        }
    }

    /** Second blades, a few ticks after the first. */
    public static void tick(MinecraftServer server) {
        List<Pending> due = new ArrayList<>();
        synchronized (TwinBlades.class) {
            if (PENDING.isEmpty()) return;
            long now = server.overworld().getGameTime();
            for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
                Pending p = it.next();
                if (now >= p.due) {
                    due.add(p);
                    it.remove();
                }
            }
        }
        for (Pending p : due) {
            for (ServerLevel level : server.getAllLevels()) {
                if (!(level.getEntity(p.attacker) instanceof LivingEntity attacker)) continue;
                if (level.getEntity(p.target) instanceof LivingEntity target && target.isAlive() && target.distanceToSqr(attacker) < 36.0) {
                    hurt(level, attacker, target, p.amount);
                    Vec3 c = target.getBoundingBox().getCenter();
                    Fx.particles(level, ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0.0, 0.0, 0.0, 0.0);
                    level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8f, 1.3f);
                }
                break;
            }
        }
    }

    private static void sweep(ServerLevel level, LivingEntity attacker, int step, LivingEntity spared, float strength) {
        float share = ArcherRules.sweepShare(step);
        if (share <= 0.0f) return;
        boolean round = ArcherRules.strokeAllRound(step);
        double r = round ? 3.2 : 3.0;
        Vec3 c = attacker.position();
        Vec3 look = attacker.getViewVector(1.0f).multiply(1.0, 0.0, 1.0);
        look = look.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : look.normalize();
        float amount = ArcherRules.TWIN_SWING * share * Math.max(0.2f, strength);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r + 1.0), e -> e != attacker && e != spared && e.isAlive())) {
            if (!caught(attacker, e)) continue;
            Vec3 to = e.position().subtract(c).multiply(1.0, 0.0, 1.0);
            if (to.lengthSqr() > r * r) continue;
            if (!round && to.lengthSqr() > 1.0E-4 && look.dot(to.normalize()) < Math.cos(Math.toRadians(50.0))) continue;
            hurt(level, attacker, e, amount);
        }
    }

    /** The sweep never catches allies, pets, villagers or bystanders; only foes. */
    private static boolean caught(LivingEntity attacker, LivingEntity e) {
        if (e instanceof OwnableEntity pet && pet.getOwner() == attacker) return false;
        if (attacker instanceof Player) return cn.blockforge.fatekings.combat.Targets.hostileTo(attacker, e);
        return attacker instanceof KingNpcEntity npc && (npc.canHarm(e) || npc.getTarget() == e);
    }

    private static void hurt(ServerLevel level, LivingEntity attacker, LivingEntity target, float amount) {
        DamageSource source = attacker instanceof Player p ? attacker.damageSources().playerAttack(p) : attacker.damageSources().mobAttack(attacker);
        Judgement.fresh(target);
        target.hurtServer(level, source, amount);
    }

    /** Sparks and a whistle of steel, black and white. */
    private static void strokeFx(ServerLevel level, LivingEntity e, int step) {
        Vec3 c = e.getEyePosition().add(e.getViewVector(1.0f).scale(1.4)).add(0.0, -0.4, 0.0);
        Fx.particles(level, ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, step == ArcherRules.STEP_SPIN ? 3 : 1, 0.6, 0.1, 0.6, 0.0);
        level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 1.2f + step * 0.05f);
    }

    /** A stroke for everyone near ({@code skipSelf}: the striker's own client animates already). */
    public static void broadcast(ServerLevel level, LivingEntity e, int style, float roll, int step, boolean skipSelf) {
        for (ServerPlayer p : level.players()) {
            if (skipSelf && p == e || p.distanceToSqr(e) > 64.0 * 64.0) continue;
            var buf = FateNet.buffer();
            buf.writeVarInt(e.getId());
            buf.writeFloat(roll);
            buf.writeByte(style);
            buf.writeByte(step);
            FateNet.send(p, FateNet.S2C_SWING, buf);
        }
    }

    /** A bow shot: the others see the draw for a moment. */
    public static void broadcastShot(ServerLevel level, LivingEntity e) {
        broadcast(level, e, STYLE_BOW, 0.0f, 0, e instanceof Player);
    }
}
