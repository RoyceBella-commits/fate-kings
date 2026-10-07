package cn.blockforge.fatekings.archer;

import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.compat.Restraint;
import cn.blockforge.fatekings.entity.UbwEntity;
import cn.blockforge.fatekings.king.KingState;
import cn.blockforge.fatekings.king.Kings;
import cn.blockforge.fatekings.king.Skills;
import cn.blockforge.fatekings.knight.Instinct;
import cn.blockforge.fatekings.net.FateNet;
import cn.blockforge.fatekings.npc.KingNpcEntity;
import cn.blockforge.fatekings.voice.Voice;
import cn.blockforge.fatekings.voice.VoicePlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The item "Unlimited Blade Works": the source of EMIYA's projection. A tap analyses the weapon he
 * looks at (or projects the one he chose last); held, it is the aria of the reality marble.
 */
public final class UnlimitedBladeWorks {
    private UnlimitedBladeWorks() {
    }

    public static boolean inside(LivingEntity e) {
        return UbwEntity.inside(e);
    }

    /** Whether the chant may begin now (refusing with a reason for a player). */
    public static boolean mayChant(LivingEntity caster) {
        if (!Kings.isArcher(caster)) return false;
        if (Restraint.noDomain(caster)) {
            if (caster instanceof Player p) Kings.refuse(p, "fatekings.hint.chained");
            return false;
        }
        if (UbwEntity.activeFor(caster)) {
            if (caster instanceof Player p) Kings.refuse(p, "fatekings.hint.ubw_active");
            return false;
        }
        return ArcherBow.ready(caster, Skills.UBW, true);
    }

    /** "I am the bone of my sword." Embers circle his feet while the aria goes on. */
    public static void chantTick(LivingEntity caster, int held) {
        if (!(caster.level() instanceof ServerLevel level)) return;
        if (held == ArcherRules.UBW_CHANT_START) {
            VoicePlayer.say(caster, Voice.EMIYA_UBW_CHANT);
            Instinct.announce(level, caster, "fatekings.warn.ubw", 60);
        }
        double a = held * 0.35;
        double r = 1.2 + held / (double)ArcherRules.UBW_CHANT;
        Fx.particles(level, ParticleTypes.FLAME, caster.getX() + Math.cos(a) * r, caster.getY() + 0.1, caster.getZ() + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
        Fx.particles(level, Fx.dust(Fx.EMBER, 0.8f), caster.getX() + Math.cos(a + Math.PI) * r, caster.getY() + 0.2, caster.getZ() + Math.sin(a + Math.PI) * r,
            1, 0.0, 0.1, 0.0, 0.0);
    }

    /** "Unlimited Blade Works." */
    public static boolean unfold(LivingEntity caster) {
        if (!(caster.level() instanceof ServerLevel level) || !Kings.isArcher(caster)) return false;
        KingState s = Kings.of(caster);
        s.cooldown(Skills.UBW, level.getGameTime(), ArcherRules.ubwCooldown(caster instanceof KingNpcEntity));
        s.dirty = true;
        UbwEntity.open(level, caster, ArcherRules.UBW_RADIUS);
        return true;
    }

    // ---- Structural analysis ----

    /** What he looks at within 32 blocks: a creature holding a weapon, a weapon on the ground or in a frame. */
    public static ItemStack weaponInSight(LivingEntity caster) {
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getViewVector(1.0f).scale(ArcherRules.ANALYSIS_RANGE));
        BlockHitResult block = caster.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        Vec3 limit = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(caster.level(), caster, eye, limit, new AABB(eye, limit).inflate(1.5),
            e -> e != caster && !e.isSpectator() && (e instanceof LivingEntity || e instanceof ItemEntity || e instanceof ItemFrame), 0.5f);
        if (hit == null) return null;
        Entity e = hit.getEntity();
        if (e instanceof ItemEntity item) return item.getItem();
        if (e instanceof ItemFrame frame) return frame.getItem();
        if (e instanceof LivingEntity l) {
            ItemStack main = l.getMainHandItem();
            if (Projection.check(main) != Projection.Check.NOT_WEAPON) return main;
            ItemStack off = l.getOffhandItem();
            return Projection.check(off) != Projection.Check.NOT_WEAPON ? off : ItemStack.EMPTY;
        }
        return null;
    }

    /** A tap: analyse what he looks at, or (nothing there) project the weapon chosen last. */
    public static void tap(ServerPlayer p) {
        if (!Kings.isArcher(p)) return;
        KingState s = Kings.of(p);
        long now = p.level().getGameTime();
        if (!s.ready(Skills.TRACE, now)) return;
        s.cooldown(Skills.TRACE, now, ArcherRules.TRACE);
        Arsenal.Data data = Arsenal.of(p);
        ItemStack seen = weaponInSight(p);
        if (seen != null) {
            switch (Projection.check(seen)) {
                case OK -> {
                    boolean forgot = Arsenal.record(data, seen, now);
                    FateNet.actionBar(p, Component.translatable(forgot ? "fatekings.hint.arsenal_forgot" : "fatekings.hint.analysed", seen.getHoverName()));
                    Fx.event(p.level(), Fx.TRACE, p, p.position(), 16, 1.0f, 48.0);
                    if (!VoicePlayer.talking(p)) VoicePlayer.say(p, Voice.EMIYA_ANALYSIS);
                    p.level().playSound(null, p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE,
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.8f);
                }
                case UNIQUE -> Kings.refuse(p, "fatekings.hint.unprojectable", seen.getHoverName());
                case NOT_WEAPON -> Kings.refuse(p, "fatekings.hint.not_a_weapon");
            }
            return;
        }
        ItemStack chosen = data.selectedStack();
        if (chosen.isEmpty()) {
            Kings.refuse(p, "fatekings.hint.arsenal_empty");
            return;
        }
        Arsenal.touch(data, data.selected, now);
        Projection.give(p, chosen);
    }

    /** From the Hill of Swords screen: project (0), forget (1) or select (2) entry {@code index}. */
    public static void fromScreen(ServerPlayer p, int index, int action) {
        if (!Kings.isArcher(p)) return;
        Arsenal.Data data = Arsenal.of(p);
        if (index < 0 || index >= data.entries.size()) return;
        long now = p.level().getGameTime();
        switch (action) {
            case 0 -> {
                KingState s = Kings.of(p);
                if (!s.ready(Skills.TRACE, now)) return;
                s.cooldown(Skills.TRACE, now, ArcherRules.TRACE);
                Arsenal.touch(data, index, now);
                Projection.give(p, data.entries.get(index).stack());
            }
            case 1 -> Arsenal.forget(data, index);
            case 2 -> Arsenal.touch(data, index, now);
            default -> {
            }
        }
    }
}
