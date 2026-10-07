package cn.blockforge.fatekings.archer;

import cn.blockforge.fatekings.combat.Aim;
import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.combat.Judgement;
import cn.blockforge.fatekings.entity.ThrownBladeEntity;
import cn.blockforge.fatekings.king.KingState;
import cn.blockforge.fatekings.king.Kings;
import cn.blockforge.fatekings.king.Skills;
import cn.blockforge.fatekings.npc.KingNpcEntity;
import cn.blockforge.fatekings.registry.FateItems;
import cn.blockforge.fatekings.voice.Voice;
import cn.blockforge.fatekings.voice.VoicePlayer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Kanshou and Bakuya: thrown (the married blades close on the foe from both sides and come home),
 * and Crane Wing Three Strikes: a first pair from the sides, a second from above, then he leaps in
 * with both blades grown into wings of steel (Overedge) and cuts the foe twice in an X.
 */
public final class CraneWing {
    private record Run(UUID target, long start) {
    }

    private static final Map<UUID, Run> RUNS = new ConcurrentHashMap<>();

    private CraneWing() {
    }

    public static void clear() {
        RUNS.clear();
    }

    private static Vec3 hand(LivingEntity e) {
        return e.getEyePosition().add(e.getViewVector(1.0f).scale(0.8)).add(0.0, -0.35, 0.0);
    }

    /** Throws the pair (Kanshou left, Bakuya right) at whatever he aims at. */
    public static boolean throwPair(LivingEntity caster) {
        if (!Kings.isArcher(caster) || !(caster.level() instanceof ServerLevel level)) return false;
        if (!ArcherBow.ready(caster, Skills.TWIN_THROW, true)) return false;
        KingState s = Kings.of(caster);
        s.cooldown(Skills.TWIN_THROW, level.getGameTime(), ArcherRules.TWIN_THROW);
        s.dirty = true;
        Aim aim = Aim.of(caster, 32.0);
        pair(level, caster, aim.entity(), aim.point(), 5.0, 0.6, ArcherRules.THROWN_BLADE_DAMAGE);
        if (!VoicePlayer.talking(caster)) VoicePlayer.say(caster, Voice.EMIYA_TWIN);
        return true;
    }

    private static void pair(ServerLevel level, LivingEntity caster, LivingEntity target, Vec3 point, double curve, double lift, float damage) {
        Vec3 hand = hand(caster);
        ThrownBladeEntity.loose(level, caster, true, hand, target, point, -1.0, curve, lift, damage);
        ThrownBladeEntity.loose(level, caster, false, hand, target, point, 1.0, curve, lift, damage);
        Projection.traced(level, caster);
    }

    /** Crane Wing Three Strikes. */
    public static boolean start(LivingEntity caster) {
        if (!Kings.isArcher(caster) || !(caster.level() instanceof ServerLevel level)) return false;
        LivingEntity target = Aim.of(caster, 32.0).entity();
        if (target == null) {
            if (caster instanceof Player p) Kings.refuse(p, "fatekings.hint.no_target");
            return false;
        }
        if (!ArcherBow.ready(caster, Skills.CRANE_WING, true)) return false;
        KingState s = Kings.of(caster);
        s.cooldown(Skills.CRANE_WING, level.getGameTime(), ArcherRules.CRANE_WING);
        s.dirty = true;
        RUNS.put(caster.getUUID(), new Run(target.getUUID(), level.getGameTime()));
        VoicePlayer.say(caster, Voice.EMIYA_CRANE_WING);
        return true;
    }

    public static boolean running(LivingEntity e) {
        return RUNS.containsKey(e.getUUID());
    }

    public static void tick(MinecraftServer server) {
        if (RUNS.isEmpty()) return;
        for (ServerLevel level : server.getAllLevels()) {
            for (Map.Entry<UUID, Run> en : RUNS.entrySet()) {
                if (!(level.getEntity(en.getKey()) instanceof LivingEntity caster)) continue;
                Run run = en.getValue();
                LivingEntity target = level.getEntity(run.target) instanceof LivingEntity l && l.isAlive() ? l : null;
                long t = level.getGameTime() - run.start;
                if (!caster.isAlive() || !Kings.isArcher(caster) || t > 40) {
                    end(caster);
                    continue;
                }
                Vec3 point = target != null ? target.getBoundingBox().getCenter() : caster.getEyePosition().add(caster.getViewVector(1.0f).scale(10.0));
                if (t == 0) pair(level, caster, target, point, 5.0, 0.4, ArcherRules.THROWN_BLADE_DAMAGE);
                if (t == 5) pair(level, caster, target, point, 8.0, 4.0, ArcherRules.THROWN_BLADE_DAMAGE);
                if (t == 10) overedge(caster, true);
                if (t >= 10 && t < 15 && target != null) {
                    // The leap in.
                    Vec3 dir = target.position().subtract(caster.position());
                    if (dir.lengthSqr() > 9.0) {
                        caster.setDeltaMovement(dir.normalize().scale(1.8).add(0.0, 0.15, 0.0));
                        caster.needsSync = true;
                        caster.resetFallDistance();
                        if (caster instanceof ServerPlayer sp) sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
                    }
                }
                if (t == 10) TwinBlades.broadcast(level, caster, TwinBlades.STYLE_TWIN, 0.0f, ArcherRules.STEP_OVEREDGE, false);
                boolean arrived = target != null && caster.distanceToSqr(target) < 3.5 * 3.5;
                if (t >= 10 && t <= 16 && (arrived || t == 16) && !en.getValue().equals(DONE.get(en.getKey()))) {
                    DONE.put(en.getKey(), run);
                    finisher(level, caster, target);
                }
                if (t == 40) end(caster);
            }
        }
    }

    private static final Map<UUID, Run> DONE = new ConcurrentHashMap<>();

    /** The X of the overgrown blades: twice on the foe, and on everyone hostile within 3.5 blocks. */
    private static void finisher(ServerLevel level, LivingEntity caster, LivingEntity target) {
        Vec3 c = caster.position();
        DamageSource source = caster instanceof Player p ? caster.damageSources().playerAttack(p) : caster.damageSources().mobAttack(caster);
        List<LivingEntity> foes = level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(3.5),
            e -> e != caster && e.isAlive() && (e == target || hostile(caster, e)));
        for (LivingEntity e : foes) {
            float amount = e == target ? ArcherRules.CRANE_FINAL : ArcherRules.CRANE_FINAL * 0.5f;
            Judgement.fresh(e);
            e.hurtServer(level, source, amount);
            if (e == target) {
                Judgement.fresh(e);
                e.hurtServer(level, source, amount);
            }
        }
        Fx.particles(level, ParticleTypes.SWEEP_ATTACK, c.x, c.y + 1.0, c.z, 4, 1.2, 0.3, 1.2, 0.0);
        Fx.particles(level, Fx.dust(0xF0F4FF, 1.4f), c.x, c.y + 1.0, c.z, 30, 1.5, 0.6, 1.5, 0.0);
        Fx.particles(level, Fx.dust(0x301010, 1.4f), c.x, c.y + 1.0, c.z, 30, 1.5, 0.6, 1.5, 0.0);
        Fx.event(level, Fx.SHAKE, caster, c, 10, 0.5f, 32.0);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.4f, 0.8f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6f, 1.6f);
        VoicePlayer.say(caster, Voice.EMIYA_GOT_YOU);
    }

    private static boolean hostile(LivingEntity caster, LivingEntity e) {
        if (caster instanceof KingNpcEntity npc) return npc.canHarm(e) || npc.getTarget() == e;
        return cn.blockforge.fatekings.combat.Targets.hostileTo(caster, e);
    }

    private static void end(LivingEntity caster) {
        RUNS.remove(caster.getUUID());
        DONE.remove(caster.getUUID());
        overedge(caster, false);
    }

    /** The blades grow into wings of steel (or back): a model state on the held blades. */
    public static void overedge(LivingEntity e, boolean on) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
            ItemStack s = e.getItemBySlot(slot);
            if (!FateItems.twinSword(s)) continue;
            if (on) s.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of("overedge"), List.of()));
            else s.remove(DataComponents.CUSTOM_MODEL_DATA);
        }
    }
}
