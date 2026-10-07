package cn.blockforge.fatekings.knight;

import cn.blockforge.fatekings.combat.Aim;
import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.combat.Judgement;
import cn.blockforge.fatekings.combat.Terrain;
import cn.blockforge.fatekings.entity.ExcaliburWaveEntity;
import cn.blockforge.fatekings.entity.StrikeAirEntity;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.KingState;
import cn.blockforge.fatekings.king.Kings;
import cn.blockforge.fatekings.king.Skills;
import cn.blockforge.fatekings.npc.KingNpcEntity;
import cn.blockforge.fatekings.registry.FateDamage;
import cn.blockforge.fatekings.voice.Voice;
import cn.blockforge.fatekings.voice.VoicePlayer;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Everything Excalibur does besides being swung: Strike Air, the Mana Burst charge and the true name. */
public final class ExcaliburSkill {
    private record Dash(Vec3 dir, int ticksLeft, IntOpenHashSet hit) {
    }
    private static final Map<UUID, Dash> DASHES = new ConcurrentHashMap<>();

    private ExcaliburSkill() {
    }

    public static void clear() {
        DASHES.clear();
    }

    private static boolean check(LivingEntity caster, String skill, boolean ignoreCooldown) {
        KingState s = Kings.of(caster);
        if (s == null || !Kings.isKnight(caster)) {
            if (caster instanceof Player p) Kings.refuse(p, "fatekings.hint.sword_answers_king");
            return false;
        }
        long now = Kings.now(caster);
        if (s.depleted(now)) {
            if (caster instanceof Player p) Kings.refuse(p, "fatekings.hint.depleted", String.format(java.util.Locale.ROOT, "%.1f", (s.depletionUntil - now) / 20.0f));
            return false;
        }
        if (!ignoreCooldown && !s.ready(skill, now)) {
            if (caster instanceof Player p) Kings.refuse(p, "fatekings.hint.cooldown", Component.translatable("fatekings.skill." + skill),
                String.format(java.util.Locale.ROOT, "%.1f", s.cooldownLeft(skill, now) / 20.0f));
            return false;
        }
        return true;
    }

    // ---- Strike Air ----

    public static boolean strikeAir(LivingEntity caster) {
        if (!check(caster, Skills.STRIKE_AIR, false) || !(caster.level() instanceof ServerLevel level)) return false;
        KingState s = Kings.of(caster);
        long now = level.getGameTime();
        s.cooldown(Skills.STRIKE_AIR, now, KingRules.STRIKE_AIR);
        s.revealedUntil = now + KingRules.REVEALED;
        s.dirty = true;
        Vec3 origin = caster.getEyePosition().add(caster.getViewVector(1.0f).scale(1.2)).add(0.0, -0.3, 0.0);
        Vec3 dir = caster instanceof KingNpcEntity npc && npc.getTarget() != null ? Aim.centre(npc.getTarget()).subtract(origin) : caster.getViewVector(1.0f);
        StrikeAirEntity.fire(level, caster, origin, dir);
        VoicePlayer.say(caster, Voice.SABER_STRIKE_AIR);
        // The wind that hid the blade bursts out in all directions.
        Fx.particles(level, ParticleTypes.GUST_EMITTER_SMALL, caster.getX(), caster.getY() + 1.0, caster.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.6f, 0.6f);
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.6f, 0.7f);
        return true;
    }

    // ---- Mana Burst charge ----

    public static boolean manaBurst(LivingEntity caster) {
        if (!check(caster, Skills.MANA_BURST, false) || !(caster.level() instanceof ServerLevel level)) return false;
        if (KnightPassives.chained(caster)) {
            if (caster instanceof Player p) Kings.refuse(p, "fatekings.hint.chained");
            return false;
        }
        KingState s = Kings.of(caster);
        if (!caster.onGround()) {
            if (s.airDashUsed) {
                if (caster instanceof Player p) Kings.refuse(p, "fatekings.hint.air_used");
                return false;
            }
            s.airDashUsed = true;
        }
        s.cooldown(Skills.MANA_BURST, level.getGameTime(), KingRules.MANA_BURST);
        Vec3 dir = caster instanceof KingNpcEntity npc && npc.getTarget() != null
            ? Aim.centre(npc.getTarget()).subtract(caster.getEyePosition()).normalize() : caster.getViewVector(1.0f);
        DASHES.put(caster.getUUID(), new Dash(dir, 6, new IntOpenHashSet()));
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.2f, 1.4f);
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 1.2f, 0.6f);
        return true;
    }

    public static boolean dashing(LivingEntity e) {
        return DASHES.containsKey(e.getUUID());
    }

    public static void tickDash(ServerLevel level, LivingEntity e) {
        Dash d = DASHES.get(e.getUUID());
        if (d == null) return;
        if (d.ticksLeft <= 0 || !e.isAlive() || !Kings.isKnight(e) || KnightPassives.chained(e)) {
            DASHES.remove(e.getUUID());
            e.setDeltaMovement(e.getDeltaMovement().scale(0.3));
            e.needsSync = true;
            return;
        }
        DASHES.put(e.getUUID(), new Dash(d.dir, d.ticksLeft - 1, d.hit));
        Vec3 v = d.dir.scale(2.35).add(0.0, 0.04, 0.0);
        e.setDeltaMovement(v);
        e.needsSync = true;
        e.resetFallDistance();
        if (e instanceof ServerPlayer sp) sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
        Vec3 back = e.position().add(0.0, 1.0, 0.0).subtract(d.dir.scale(0.6));
        Fx.particles(level, Fx.dust(Fx.MANA, 1.4f), back.x, back.y, back.z, 10, 0.25, 0.4, 0.25, 0.0);
        Fx.particles(level, ParticleTypes.SOUL_FIRE_FLAME, back.x, back.y, back.z, 6, 0.2, 0.4, 0.2, 0.02);
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().expandTowards(v).inflate(1.0),
                t -> t != e && t.isAlive() && !t.isSpectator() && !e.isPassengerOfSameVehicle(t))) {
            if (!d.hit.add(t.getId())) continue;
            Judgement.fresh(t);
            t.hurtServer(level, FateDamage.source(level, FateDamage.MANA_BURST, e, e), 20.0f);
            Vec3 side = new Vec3(-d.dir.z, 0.0, d.dir.x).normalize().scale(t.getRandom().nextBoolean() ? 1.0 : -1.0);
            t.push(side.x * 1.2 + d.dir.x * 0.6, 0.5, side.z * 1.2 + d.dir.z * 0.6);
            t.needsSync = true;
            Fx.particles(level, ParticleTypes.ELECTRIC_SPARK, t.getBoundingBox().getCenter(), 10, 0.3, 0.2);
        }
    }

    // ---- The true name ----

    /** Whether the sword may start gathering light now (Avalon's counter ignores the cooldown). */
    public static boolean mayCharge(LivingEntity caster) {
        KingState s = Kings.of(caster);
        boolean counter = s != null && Kings.now(caster) < s.counterUntil;
        return check(caster, Skills.EXCALIBUR, counter);
    }

    /** The wind bursts free, light gathers, a pillar of gold rises (a warning seen 128 blocks away). */
    public static void chargeTick(ServerLevel level, LivingEntity caster, int held) {
        Vec3 c = caster.position();
        if (held == KingRules.TAP_TICKS) {
            VoicePlayer.say(caster, Voice.SABER_EXCALIBUR_CHANT);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(4.0), e -> e != caster)) {
                Vec3 away = e.position().subtract(c).multiply(1.0, 0.0, 1.0);
                away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
                e.push(away.x * 1.1, 0.35, away.z * 1.1);
                e.needsSync = true;
            }
            Fx.particles(level, ParticleTypes.GUST_EMITTER_LARGE, c.x, c.y + 0.5, c.z, 1, 0.0, 0.0, 0.0, 0.0);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 2.0f, 0.5f);
            if (Terrain.enabled()) blowPlants(level, caster.blockPosition(), 5);
        }
        if (held > KingRules.TAP_TICKS) {
            // Motes of starlight rise from the ground and spiral into the sword.
            for (int i = 0; i < 4; ++i) {
                double a = level.getRandom().nextDouble() * Math.PI * 2;
                double r = 2.0 + level.getRandom().nextDouble() * 4.0;
                Fx.particles(level, Fx.dust(level.getRandom().nextBoolean() ? 0xFFE38A : 0xFFF7DA, 0.9f), c.x + Math.cos(a) * r, c.y + 0.2,
                    c.z + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
            }
            Vec3 tip = c.add(0.0, 2.8, 0.0);
            Fx.particles(level, ParticleTypes.END_ROD, tip.x, tip.y, tip.z, 2, 0.2, 0.2, 0.2, 0.02);
        }
        if (held == 20) {
            Fx.event(level, Fx.PILLAR, caster, caster.position(), 20 * 30, 1.0f, 160.0);
            Instinct.announce(level, caster, "fatekings.warn.excalibur", 60);
        }
        if (held % 15 == 0) {
            level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.5f, 0.5f + Math.min(1.5f, held / 60.0f));
        }
    }

    private static void blowPlants(ServerLevel level, BlockPos centre, int r) {
        for (BlockPos p : BlockPos.betweenClosed(centre.offset(-r, -1, -r), centre.offset(r, 2, r))) {
            BlockState s = level.getBlockState(p);
            if ((s.is(BlockTags.REPLACEABLE_BY_TREES) || s.is(BlockTags.FLOWERS)) && level.getRandom().nextInt(2) == 0) level.destroyBlock(p, false);
        }
    }

    /** Stops the gathered light without releasing it (the pillar goes out). */
    public static void cancel(ServerLevel level, LivingEntity caster) {
        VoicePlayer.stop(caster);
        Fx.event(level, Fx.PILLAR, caster, caster.position(), 0, 0.0f, 160.0);
    }

    /** "Ex——calibur!": the slash of light, then 5 s of mana depletion. */
    public static void release(LivingEntity caster, int held, LivingEntity target) {
        if (!(caster.level() instanceof ServerLevel level)) return;
        KingState s = Kings.of(caster);
        long now = level.getGameTime();
        boolean counter = now < s.counterUntil;
        boolean npc = caster instanceof KingNpcEntity;
        boolean lastStand = caster instanceof KingNpcEntity k && k.lastStand();
        Vec3 origin = caster.getEyePosition().add(caster.getViewVector(1.0f).scale(1.5));
        Vec3 dir = target != null ? Aim.centre(target).subtract(origin).normalize() : caster.getViewVector(1.0f);
        ExcaliburWaveEntity wave = ExcaliburWaveEntity.fire(level, caster, origin, dir, KingRules.excaliburWidth(held), KingRules.EXCALIBUR_RANGE);
        if (counter && s.counterDouble) wave.setMultiplierOnHero(2.0f);
        s.counterUntil = 0L;
        s.counterDouble = false;
        s.cooldown(Skills.EXCALIBUR, now, KingRules.excaliburCooldown(npc, lastStand));
        s.depletionUntil = now + KingRules.MANA_DEPLETION;
        s.revealedUntil = s.depletionUntil;
        s.dirty = true;
        VoicePlayer.stop(caster);
        VoicePlayer.say(caster, Voice.SABER_EXCALIBUR_RELEASE);
        Fx.event(level, Fx.PILLAR, caster, caster.position(), 0, 0.0f, 160.0);
        Fx.event(level, Fx.SHAKE, caster, caster.position(), 40, 1.0f, 160.0);
        Fx.event(level, Fx.GOLD_FLASH, caster, caster.position(), 10, 0.7f, 96.0);
    }

    /** Three close blows while gathering light break the charge; half the cooldown is spent. */
    public static void interrupt(LivingEntity caster) {
        if (!(caster.level() instanceof ServerLevel level)) return;
        KingState s = Kings.of(caster);
        caster.stopUsingItem();
        cancel(level, caster);
        s.cooldown(Skills.EXCALIBUR, level.getGameTime(), KingRules.EXCALIBUR / 2);
        if (caster instanceof Player p) Kings.refuse(p, "fatekings.hint.interrupted");
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.SHIELD_BREAK.value(), SoundSource.PLAYERS, 1.0f, 1.2f);
    }
}
