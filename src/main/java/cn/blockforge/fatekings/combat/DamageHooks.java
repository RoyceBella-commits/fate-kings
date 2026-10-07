package cn.blockforge.fatekings.combat;

import cn.blockforge.fatekings.compat.JjkCompat;
import cn.blockforge.fatekings.hero.KingItem;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.KingState;
import cn.blockforge.fatekings.king.Kings;
import cn.blockforge.fatekings.knight.Avalon;
import cn.blockforge.fatekings.knight.ExcaliburSkill;
import cn.blockforge.fatekings.knight.Instinct;
import cn.blockforge.fatekings.knight.WarhorseItem;
import cn.blockforge.fatekings.npc.KingNpcEntity;
import cn.blockforge.fatekings.registry.FateItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Damage and death rules of the two kings, registered on the Fabric living-entity events. */
public final class DamageHooks {
    private DamageHooks() {
    }

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(DamageHooks::allowDamage);
        ServerLivingEntityEvents.AFTER_DAMAGE.register(DamageHooks::afterDamage);
        ServerLivingEntityEvents.ALLOW_DEATH.register(DamageHooks::allowDeath);
        ServerLivingEntityEvents.AFTER_DEATH.register(DamageHooks::afterDeath);
    }

    private static boolean allowDamage(LivingEntity target, DamageSource source, float amount) {
        if (!Kings.isKing(target)) return true;
        KingState s = Kings.of(target);
        long now = Kings.now(target);
        boolean bypass = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        if (source.is(DamageTypeTags.IS_FALL)) return false;
        if (!bypass && (s.domeActive(now) || now < s.guardUntil)) {
            if (s.domeActive(now) && target.level() instanceof ServerLevel level) {
                // Blows on the dome of light only raise a ripple.
                Fx.particles(level, Fx.dust(0xFFE38A, 1.2f), target.getBoundingBox().getCenter(), 10, 1.0, 0.0);
            }
            return false;
        }
        s.lastCombat = now;
        if (Kings.isKnight(target) && !bypass) {
            if (Instinct.dodge(target, source)) return false;
            if (magicBlocked(source)) return false;
            if (Avalon.domeReady(target) && Avalon.ultimate(target, source, amount)) {
                Avalon.unfold(target, s, source.getEntity());
                return false;
            }
        }
        return true;
    }

    /** Magic Resistance A: vanilla magic (fangs, harming potions) cannot touch her. Jujutsu is not magecraft. */
    private static boolean magicBlocked(DamageSource source) {
        if (!(source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC))) return false;
        Entity attacker = source.getEntity();
        return attacker == null || !(Sides.jjkSide(attacker) || JjkCompat.fromJjk(source.getDirectEntity()));
    }

    private static void afterDamage(LivingEntity entity, DamageSource source, float base, float taken, boolean blocked) {
        Entity attacker = source.getEntity();
        // Three close blows while Excalibur gathers light break the charge.
        if (Kings.isKnight(entity) && entity.isUsingItem() && entity.getUseItem().is(FateItems.EXCALIBUR)
            && KingItem.held(entity.getUseItemRemainingTicks()) >= KingRules.TAP_TICKS
            && attacker != null && attacker == source.getDirectEntity() && attacker.distanceToSqr(entity) < 25.0) {
            KingState s = Kings.of(entity);
            long now = Kings.now(entity);
            if (now > s.interruptWindow) s.interruptHits = 0;
            s.interruptWindow = now + 60;
            if (++s.interruptHits >= 3) {
                s.interruptHits = 0;
                ExcaliburSkill.interrupt(entity);
            }
        }
        if (entity instanceof KingNpcEntity npc) npc.onHurt(source, taken);
        // EMIYA has seen the weapon that struck him: it goes to the Hill of Swords.
        if (Kings.isArcher(entity)) cn.blockforge.fatekings.archer.Projection.learnFrom(entity, source);
        // Mana Burst: every close blow of the knight crackles and throws 20% further.
        if (attacker instanceof LivingEntity knight && Kings.isKnight(knight) && attacker == source.getDirectEntity()
            && entity.level() instanceof ServerLevel level && taken > 0.0f) {
            Vec3 away = entity.position().subtract(knight.position()).multiply(1.0, 0.0, 1.0);
            if (away.lengthSqr() > 1.0E-4) {
                away = away.normalize().scale(0.25);
                entity.push(away.x, 0.05, away.z);
            }
            Fx.particles(level, ParticleTypes.ELECTRIC_SPARK, entity.getBoundingBox().getCenter(), 6, 0.3, 0.2);
            Fx.particles(level, Fx.dust(Fx.MANA, 0.9f), entity.getBoundingBox().getCenter(), 4, 0.3, 0.0);
        }
    }

    private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
        if (Judgement.spared(entity, source.getEntity())) {
            entity.setHealth(Math.max(entity.getHealth(), 2.0f));
            return false;
        }
        if (Kings.isKnight(entity) && Avalon.refuseDeath(entity, source)) return false;
        if (WarhorseItem.isWarhorse(entity) && entity.level() instanceof ServerLevel level) {
            WarhorseItem.vanish(level, entity);
            return false;
        }
        return true;
    }

    /** Golden Rule: whatever the King of Heroes kills leaves a little gold behind. */
    private static void afterDeath(LivingEntity entity, DamageSource source) {
        Entity killer = source.getEntity();
        if (!(killer instanceof LivingEntity hero) || !Kings.isHero(hero) || entity instanceof Player
            || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        int nuggets = 1 + level.getRandom().nextInt(3);
        drop(level, entity, new ItemStack(Items.GOLD_NUGGET, nuggets));
        if (level.getRandom().nextFloat() < 0.15f) drop(level, entity, new ItemStack(Items.GOLD_INGOT));
    }

    private static void drop(ServerLevel level, LivingEntity at, ItemStack stack) {
        ItemEntity item = new ItemEntity(level, at.getX(), at.getY() + 0.5, at.getZ(), stack);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }
}
