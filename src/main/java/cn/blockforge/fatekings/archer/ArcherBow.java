package cn.blockforge.fatekings.archer;

import cn.blockforge.fatekings.combat.Aim;
import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.combat.Targets;
import cn.blockforge.fatekings.entity.CaladbolgEntity;
import cn.blockforge.fatekings.entity.ProjectedArrowEntity;
import cn.blockforge.fatekings.king.KingState;
import cn.blockforge.fatekings.king.Kings;
import cn.blockforge.fatekings.king.Skills;
import cn.blockforge.fatekings.knight.Instinct;
import cn.blockforge.fatekings.npc.KingNpcEntity;
import cn.blockforge.fatekings.voice.Voice;
import cn.blockforge.fatekings.voice.VoicePlayer;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The black bow. A tap looses a homing sword-arrow at once (no draw; one every 4 ticks, 2 inside his
 * own reality marble). Held 1.5 s and let go: Caladbolg II.
 */
public final class ArcherBow {
    private ArcherBow() {
    }

    public static boolean ready(LivingEntity caster, String skill, boolean say) {
        KingState s = Kings.of(caster);
        long now = Kings.now(caster);
        if (s.ready(skill, now)) return true;
        if (say && caster instanceof Player p) Kings.refuse(p, "fatekings.hint.cooldown", Component.translatable("fatekings.skill." + skill),
            String.format(java.util.Locale.ROOT, "%.1f", s.cooldownLeft(skill, now) / 20.0f));
        return false;
    }

    /** One homing arrow. */
    public static boolean tap(LivingEntity caster) {
        if (!Kings.isArcher(caster) || !(caster.level() instanceof ServerLevel level)) return false;
        KingState s = Kings.of(caster);
        long now = level.getGameTime();
        if (!s.ready(Skills.BOW_TAP, now)) return false;
        s.cooldown(Skills.BOW_TAP, now, ArcherRules.bowTapCooldown(UnlimitedBladeWorks.inside(caster)));
        LivingEntity target = Aim.of(caster, 96.0).entity();
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getViewVector(1.0f);
        if (target == null || !Targets.hostileTo(caster, target) && caster instanceof KingNpcEntity) {
            target = Targets.nearestInCone(caster, eye, look, ArcherRules.ARROW_START_CONE_COS, ArcherRules.ARROW_START_RANGE);
        }
        Vec3 dir = caster instanceof KingNpcEntity && target != null ? target.getBoundingBox().getCenter().subtract(eye) : look;
        Vec3 origin = eye.add(look.scale(0.7)).add(0.0, -0.15, 0.0);
        ItemStack lookOf = UnlimitedBladeWorks.inside(caster) ? randomArsenal(caster) : ItemStack.EMPTY;
        ProjectedArrowEntity.loose(level, caster, origin, dir, target, lookOf);
        if (caster instanceof KingNpcEntity && caster.getRandom().nextInt(12) == 0 && !VoicePlayer.talking(caster)) VoicePlayer.say(caster, Voice.EMIYA_THERE);
        TwinBlades.broadcastShot(level, caster);
        return true;
    }

    private static ItemStack randomArsenal(LivingEntity caster) {
        List<ItemStack> all = Arsenal.display(caster, 12);
        return all.isEmpty() ? ItemStack.EMPTY : all.get(caster.getRandom().nextInt(all.size()));
    }

    /** While the bow is held: from 0.3 s the spiral sword is nocked, the air twists round it. */
    public static void chargeTick(LivingEntity caster, int held) {
        if (!(caster.level() instanceof ServerLevel level) || !Kings.isArcher(caster)) return;
        if (held == ArcherRules.BOW_TAP_TICKS) {
            if (!ready(caster, Skills.CALADBOLG, true)) return;
            Instinct.announce(level, caster, "fatekings.warn.caladbolg", 40);
            VoicePlayer.say(caster, Voice.EMIYA_FINISH_IT);
            level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), SoundSource.PLAYERS, 1.0f, 0.6f);
        }
        if (held < ArcherRules.BOW_TAP_TICKS || !Kings.of(caster).ready(Skills.CALADBOLG, level.getGameTime())) return;
        Vec3 look = caster.getViewVector(1.0f);
        Vec3 tip = caster.getEyePosition().add(look.scale(1.2));
        double a = held * 0.7;
        Vec3 side = look.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(look).normalize();
        Vec3 o = side.scale(Math.cos(a) * 0.5).add(up.scale(Math.sin(a) * 0.5));
        Fx.particles(level, Fx.dust(0xE8302A, 0.9f), tip.x + o.x, tip.y + o.y, tip.z + o.z, 1, 0.0, 0.0, 0.0, 0.0);
        if (held == ArcherRules.CALADBOLG_CHARGE) {
            level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 1.2f, 0.5f);
            Fx.particles(level, ParticleTypes.END_ROD, tip.x, tip.y, tip.z, 10, 0.2, 0.2, 0.2, 0.05);
        }
    }

    /** Caladbolg II, at whatever the caster aims at. */
    public static boolean caladbolg(LivingEntity caster, LivingEntity aimed) {
        if (!Kings.isArcher(caster) || !(caster.level() instanceof ServerLevel level)) return false;
        if (!ready(caster, Skills.CALADBOLG, true)) return false;
        KingState s = Kings.of(caster);
        long now = level.getGameTime();
        s.cooldown(Skills.CALADBOLG, now, ArcherRules.caladbolgCooldown(caster instanceof KingNpcEntity));
        s.dirty = true;
        Aim aim = Aim.of(caster, ArcherRules.CALADBOLG_RANGE);
        LivingEntity locked = aimed != null ? aimed : aim.entity();
        Vec3 eye = caster.getEyePosition();
        Vec3 dir = caster instanceof KingNpcEntity && locked != null ? locked.getBoundingBox().getCenter().subtract(eye).normalize() : caster.getViewVector(1.0f);
        CaladbolgEntity.fire(level, caster, eye.add(dir.scale(1.2)), dir, locked);
        VoicePlayer.say(caster, Voice.EMIYA_CALADBOLG);
        Fx.event(level, Fx.SHAKE, caster, caster.position(), 14, 0.6f, 48.0);
        TwinBlades.broadcastShot(level, caster);
        return true;
    }
}
