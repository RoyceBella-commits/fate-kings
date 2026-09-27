package cn.blockforge.fatekings.combat;

import cn.blockforge.fatekings.compat.JjkCompat;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.Kings;
import cn.blockforge.fatekings.npc.KingNpcEntity;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Applies {@link DamageRules} to a hit, plus the NPC-specific scalings. */
public final class DamageShare {
    private DamageShare() {
    }

    public static float apply(LivingEntity target, DamageSource source, float amount) {
        if (amount <= 0.0f || target.level().isClientSide() || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return amount;
        Entity attacker = source.getEntity();
        Entity direct = source.getDirectEntity();
        float out = amount;
        // A king (NPC or player) against a Gojo / Sukuna NPC: their techniques ignore armour, ours need a push to keep up.
        if (Kings.isKing(attacker) && !(target instanceof Player) && JjkCompat.fromJjk(target)) {
            out *= KingRules.KING_VS_JJK_DAMAGE;
        }
        if (!Kings.isKing(target)) return out;
        // Except logic: 1% from anything, except the Gojo x Sukuna side and the other king (10%).
        boolean tenPercent = tenPercentSource(target, attacker) || tenPercentSource(target, direct);
        if (target instanceof Player p && JjkCompat.awakened(p)) {
            // The other mod applies its own share too: together they must give the lower one.
            out *= DamageRules.kingTakenWithJjk(tenPercent, JjkCompat.stage(p), tenPercent);
        } else {
            out *= DamageRules.kingTaken(tenPercent);
        }
        // Players hit the 200-health NPCs harder, so a fight lasts as long as against an 80-health king.
        if (target instanceof KingNpcEntity && attacker instanceof Player) out *= KingRules.PLAYER_VS_NPC;
        return out;
    }

    /** The exceptions to the 1% rule: anything of the Gojo x Sukuna mod, route players, and another king. */
    public static boolean tenPercentSource(LivingEntity target, Entity e) {
        if (e == null || e == target) return false;
        if (JjkCompat.fromJjk(e)) return true;
        if (e instanceof Player p && JjkCompat.awakened(p)) return true;
        return Kings.isKing(e);
    }
}
