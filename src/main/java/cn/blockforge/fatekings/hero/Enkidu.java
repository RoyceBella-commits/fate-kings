package cn.blockforge.fatekings.hero;

import cn.blockforge.fatekings.combat.Aim;
import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.combat.Sides;
import cn.blockforge.fatekings.compat.JjkCompat;
import cn.blockforge.fatekings.entity.ChainEntity;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.Kings;
import cn.blockforge.fatekings.king.Skills;
import cn.blockforge.fatekings.registry.FateDamage;
import cn.blockforge.fatekings.registry.FateEffects;
import cn.blockforge.fatekings.voice.Voice;
import cn.blockforge.fatekings.voice.VoicePlayer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Enkidu, Chains of Heaven. Tap: a chain from a ripple hooks the target and drags it before the king
 * (8 damage). Hold 1 s: ripples in sky and ground bind it (3 s; 5 s with 6 damage a second for beings
 * of higher standing); it cannot teleport or leap. Infinity stops the chain short.
 */
public final class Enkidu {
    private record Bind(UUID caster, long until, boolean high) {
    }
    private static final Map<UUID, Bind> BINDS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> MAHORAGA_BOUND = new ConcurrentHashMap<>();
    private static final String ADAPTED_TAG = "fatekings.chain_adapted";

    private Enkidu() {
    }

    public static void clear() {
        BINDS.clear();
        MAHORAGA_BOUND.clear();
    }

    /** Beings of higher standing (the "divinity" of the design doc): bosses, Mahoraga, sorcerers, kings. */
    public static boolean highStanding(LivingEntity e) {
        return Sides.worthy(e) || Sides.jjkSide(e) || Kings.isKing(e);
    }

    private static Vec3 rippleNear(LivingEntity caster, Vec3 target) {
        Vec3 look = caster.getViewVector(1.0f);
        return caster.getEyePosition().add(look.scale(1.0)).add(0.0, 0.6, 0.0);
    }

    private static boolean stoppedByInfinity(ServerLevel level, Vec3 from, LivingEntity target) {
        if (!JjkCompat.infinityUp(target)) return false;
        Vec3 c = target.getBoundingBox().getCenter();
        Vec3 stop = c.add(from.subtract(c).normalize().scale(1.6));
        ChainEntity.spawn(level, from, null, stop, ChainEntity.STOPPED, 16);
        Fx.particles(level, Fx.dust(Fx.GOLD, 1.2f), stop, 16, 0.3, 0.0);
        level.playSound(null, stop.x, stop.y, stop.z, SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.0f, 1.4f);
        return true;
    }

    public static boolean hook(LivingEntity caster) {
        if (!GateOfBabylon.treasuryOpen(caster) || !GateOfBabylon.ready(caster, Skills.ENKIDU_HOOK)) return false;
        ServerLevel level = (ServerLevel)caster.level();
        Kings.of(caster).cooldown(Skills.ENKIDU_HOOK, level.getGameTime(), KingRules.ENKIDU_HOOK);
        Aim aim = Aim.of(caster, 32.0);
        Vec3 from = rippleNear(caster, aim.point());
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.2f, 1.3f);
        LivingEntity target = aim.entity();
        if (target == null) {
            ChainEntity.spawn(level, from, null, aim.point(), ChainEntity.HOOK, 12);
            return true;
        }
        if (stoppedByInfinity(level, from, target)) return true;
        ChainEntity.spawn(level, from, target, aim.point(), ChainEntity.HOOK, 12);
        target.setInvulnerableTime(0);
        target.hurtServer(level, FateDamage.source(level, FateDamage.CHAIN, caster, caster), 8.0f);
        if (!BINDS.containsKey(target.getUUID())) {
            Vec3 dest = caster.position().add(caster.getViewVector(1.0f).multiply(1.0, 0.0, 1.0).normalize().scale(2.0));
            Vec3 pull = dest.subtract(target.position());
            Vec3 v = pull.scale(0.28).add(0.0, 0.25 + Math.max(0.0, pull.y) * 0.05, 0.0);
            target.setDeltaMovement(v);
            target.needsSync = true;
            if (target instanceof ServerPlayer sp) sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
        }
        return true;
    }

    public static boolean bind(LivingEntity caster) {
        if (!GateOfBabylon.treasuryOpen(caster) || !GateOfBabylon.ready(caster, Skills.ENKIDU_BIND)) return false;
        ServerLevel level = (ServerLevel)caster.level();
        Aim aim = Aim.of(caster, 48.0);
        LivingEntity target = aim.entity();
        if (target == null) {
            if (caster instanceof Player p) Kings.refuse(p, "fatekings.hint.no_target");
            return false;
        }
        return bindTarget(level, caster, target);
    }

    public static boolean bindTarget(ServerLevel level, LivingEntity caster, LivingEntity target) {
        Kings.of(caster).cooldown(Skills.ENKIDU_BIND, level.getGameTime(), KingRules.ENKIDU_BIND);
        VoicePlayer.say(caster, Voice.GIL_CHAIN);
        Vec3 c = target.getBoundingBox().getCenter();
        if (stoppedByInfinity(level, rippleNear(caster, c), target)) return true;
        if (target.entityTags().contains(ADAPTED_TAG)) {
            // Mahoraga has adapted to the chains: they slide off.
            Fx.particles(level, Fx.dust(Fx.GOLD, 1.2f), c, 20, 0.6, 0.0);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.5f, 0.8f);
            return true;
        }
        boolean high = highStanding(target);
        int duration = high ? KingRules.CHAIN_BIND_HIGH : KingRules.CHAIN_BIND;
        target.addEffect(new MobEffectInstance(FateEffects.HEAVENS_CHAIN, duration, 0, false, true, true), caster);
        BINDS.put(target.getUUID(), new Bind(caster.getUUID(), level.getGameTime() + duration, high));
        int n = 4 + level.getRandom().nextInt(3);
        for (int i = 0; i < n; ++i) {
            double a = i * Math.PI * 2 / n + level.getRandom().nextDouble() * 0.5;
            boolean sky = i % 2 == 0;
            Vec3 from = c.add(Math.cos(a) * 5.0, sky ? 5.5 : -1.0 - target.getBbHeight() * 0.4, Math.sin(a) * 5.0);
            ChainEntity.spawn(level, from, target, c, ChainEntity.BIND, duration);
            Fx.ring(level, from, Fx.GOLD, 0.8, 12);
        }
        target.setDeltaMovement(Vec3.ZERO);
        target.needsSync = true;
        level.playSound(null, c.x, c.y, c.z, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 2.0f, 0.6f);
        return true;
    }

    public static boolean bound(LivingEntity e) {
        Bind b = BINDS.get(e.getUUID());
        return b != null && e.level().getGameTime() < b.until;
    }

    public static void tick(ServerLevel level) {
        long now = level.getGameTime();
        List<UUID> done = new ArrayList<>();
        BINDS.forEach((id, b) -> {
            if (!(level.getEntity(id) instanceof LivingEntity target)) {
                if (now >= b.until) done.add(id);
                return;
            }
            if (now >= b.until || !target.isAlive() || !target.hasEffect(FateEffects.HEAVENS_CHAIN)) {
                done.add(id);
                return;
            }
            target.setDeltaMovement(target.getDeltaMovement().multiply(0.0, target.onGround() ? 0.0 : 1.0, 0.0));
            if (b.high && (b.until - now) % 20 == 0) {
                // The tighter the chain holds a higher being, the brighter it glows.
                LivingEntity caster = level.getEntity(b.caster) instanceof LivingEntity l ? l : null;
                target.setInvulnerableTime(0);
                target.hurtServer(level, FateDamage.source(level, FateDamage.CHAIN, caster, caster), 6.0f);
                Fx.particles(level, Fx.dust(0xFFF1A8, 1.4f), target.getBoundingBox().getCenter(), 14, 0.5, 0.0);
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 1.0f, 0.7f);
            }
            if (JjkCompat.is(target, JjkCompat.MAHORAGA)) {
                int t = MAHORAGA_BOUND.merge(id, 1, Integer::sum);
                if (t >= 60) {
                    // The wheel turns: Mahoraga adapts to the chains in 3 s and breaks free.
                    target.addTag(ADAPTED_TAG);
                    target.removeEffect(FateEffects.HEAVENS_CHAIN);
                    Fx.particles(level, ParticleTypes.END_ROD, target.getBoundingBox().getCenter(), 30, 0.8, 0.1);
                    level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 2.0f, 0.5f);
                    done.add(id);
                }
            }
        });
        done.forEach(BINDS::remove);
    }
}
