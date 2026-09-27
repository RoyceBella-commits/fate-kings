package cn.blockforge.fatekings.clash;

import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.combat.Judgement;
import cn.blockforge.fatekings.combat.JudgementRules.Weapon;
import cn.blockforge.fatekings.entity.BeamEntity;
import cn.blockforge.fatekings.entity.EnumaElishEntity;
import cn.blockforge.fatekings.entity.ExcaliburWaveEntity;
import cn.blockforge.fatekings.king.KingState;
import cn.blockforge.fatekings.king.Kings;
import cn.blockforge.fatekings.knight.Avalon;
import cn.blockforge.fatekings.npc.KingAiRules;
import cn.blockforge.fatekings.registry.FateEffects;
import cn.blockforge.fatekings.voice.Voice;
import cn.blockforge.fatekings.voice.VoicePlayer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Ea against Excalibur (design doc 13.3): fired at each other within 3 s on crossing paths, the two
 * lock where they meet for 3 s. At 2 s it is decided: Avalon ready means the utopia unfolds, blocks
 * all of Ea and the knight's next Excalibur within 5 s does double on the King of Heroes, who stands
 * frozen for 1.5 s; otherwise Ea overpowers the holy sword and she takes half of what is left.
 */
public final class NoblePhantasmClash {
    private static final List<BeamEntity> ACTIVE = new ArrayList<>();
    private static final List<Clash> CLASHES = new ArrayList<>();

    private static final class Clash {
        final EnumaElishEntity ea;
        final ExcaliburWaveEntity sword;
        final Vec3 point;
        int ticks;

        Clash(EnumaElishEntity ea, ExcaliburWaveEntity sword, Vec3 point) {
            this.ea = ea;
            this.sword = sword;
            this.point = point;
        }
    }

    private NoblePhantasmClash() {
    }

    public static synchronized void clear() {
        ACTIVE.clear();
        CLASHES.clear();
    }

    /** Called by a beam each tick while it advances. */
    public static synchronized void track(BeamEntity beam) {
        if (!ACTIVE.contains(beam)) ACTIVE.add(beam);
    }

    public static synchronized void tick(ServerLevel level) {
        ACTIVE.removeIf(b -> b.isRemoved() || b.state() != BeamEntity.ADVANCING);
        for (int i = 0; i < ACTIVE.size(); ++i) {
            for (int j = 0; j < ACTIVE.size(); ++j) {
                if (!(ACTIVE.get(i) instanceof EnumaElishEntity ea) || !(ACTIVE.get(j) instanceof ExcaliburWaveEntity sword)) continue;
                if (ea.level() != level || sword.level() != level || ea.state() != BeamEntity.ADVANCING || sword.state() != BeamEntity.ADVANCING) continue;
                if (ea.ownerId() != null && ea.ownerId().equals(sword.ownerId())) continue;
                Vec3 point = meeting(ea, sword);
                if (point != null) start(level, ea, sword, point);
            }
        }
        CLASHES.removeIf(c -> step(level, c));
    }

    /** The point where the two fronts meet, if their paths cross and both have reached it. */
    private static Vec3 meeting(BeamEntity a, BeamEntity b) {
        Vec3 p1 = a.position(), d1 = a.dir(), p2 = b.position(), d2 = b.dir();
        Vec3 w = p1.subtract(p2);
        double aa = d1.dot(d1), bb = d1.dot(d2), cc = d2.dot(d2), dd = d1.dot(w), ee = d2.dot(w);
        double den = aa * cc - bb * bb;
        if (Math.abs(den) < 1.0E-6) return null;
        double s = (bb * ee - cc * dd) / den;
        double t = (aa * ee - bb * dd) / den;
        if (s < 0 || t < 0) return null;
        Vec3 q1 = p1.add(d1.scale(s)), q2 = p2.add(d2.scale(t));
        double gap = q1.distanceTo(q2);
        long launchGap = Math.abs(a.launchedAt() - b.launchedAt());
        if (!KingAiRules.beamsMeet(new double[]{d1.x, d1.y, d1.z}, new double[]{d2.x, d2.y, d2.z}, gap, a.width() / 2.0, b.width() / 2.0, launchGap)) {
            return null;
        }
        if (a.front() + a.width() < s || b.front() + b.width() < t) return null;
        return q1.add(q2).scale(0.5);
    }

    private static void start(ServerLevel level, EnumaElishEntity ea, ExcaliburWaveEntity sword, Vec3 point) {
        ea.freezeAt(point.distanceTo(ea.position()));
        sword.freezeAt(point.distanceTo(sword.position()));
        CLASHES.add(new Clash(ea, sword, point));
        Fx.event(level, Fx.CLASH, null, point, KingAiRules.CLASH_TICKS, 1.0f, 320.0);
        Fx.event(level, Fx.SKY_SPLIT, null, point, KingAiRules.CLASH_TICKS, 1.0f, 320.0);
        Fx.event(level, Fx.SHAKE, null, point, KingAiRules.CLASH_TICKS, 1.4f, 200.0);
        level.playSound(null, point.x, point.y, point.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 5.0f, 0.5f);
        level.playSound(null, point.x, point.y, point.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 5.0f, 0.5f);
    }

    /** Returns true when the clash is over. */
    private static boolean step(ServerLevel level, Clash c) {
        ++c.ticks;
        Fx.particles(level, ParticleTypes.END_ROD, c.point, 6, 2.0, 0.2);
        Fx.particles(level, Fx.dust(c.ticks % 2 == 0 ? 0xB0101A : 0xFFE38A, 3.0f), c.point, 8, 2.5, 0.0);
        if (c.ticks == KingAiRules.CLASH_DECIDE) decide(level, c);
        if (c.ticks >= KingAiRules.CLASH_TICKS) {
            c.ea.finish();
            c.sword.finish();
            return true;
        }
        return false;
    }

    private static void decide(ServerLevel level, Clash c) {
        LivingEntity hero = c.ea.owner();
        LivingEntity knight = c.sword.owner();
        boolean avalon = knight != null && knight.isAlive() && Avalon.domeReady(knight);
        if (KingAiRules.clash(avalon) == KingAiRules.ClashResult.AVALON_BLOCKS) {
            KingState s = Kings.of(knight);
            Avalon.unfold(knight, s, hero);
            s.counterDouble = true;
            if (hero != null) hero.addEffect(new MobEffectInstance(FateEffects.DISBELIEF, 30, 0, false, false, true));
            level.playSound(null, c.point.x, c.point.y, c.point.z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 5.0f, 1.2f);
            Fx.event(level, Fx.ARMOR_SCATTER, knight, knight.position(), 20, 1.0f, 128.0);
        } else {
            if (hero != null) VoicePlayer.say(hero, Voice.GIL_LAUGH);
            if (knight != null && knight.isAlive()) {
                Judgement.strike(level, hero, c.ea, knight, Weapon.EA, 0.5f);
            }
            level.playSound(null, c.point.x, c.point.y, c.point.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 5.0f, 0.5f);
        }
    }
}
