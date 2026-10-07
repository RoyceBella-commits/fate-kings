package cn.blockforge.fatekings.combat;

import cn.blockforge.fatekings.npc.KingNpcEntity;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Whom a spirit's self-aiming attacks may pick on their own: the Gate of Babylon, homing arrows, the reality marble. */
public final class Targets {
    private Targets() {
    }

    /** NPC: whatever it would fight. Player: monsters and whatever is after him, never players or his own pets. */
    public static boolean hostileTo(LivingEntity caster, LivingEntity e) {
        if (e == caster || !e.isAlive() || e.isSpectator() || caster.isPassengerOfSameVehicle(e)) return false;
        if (caster instanceof KingNpcEntity npc) return npc.canHarm(e) || npc.getTarget() == e;
        if (e instanceof Player || Sides.noncombatant(e)) return false;
        if (e instanceof OwnableEntity pet && pet.getOwner() == caster) return false;
        return e instanceof Enemy || e instanceof Mob m && m.getTarget() == caster;
    }

    /** The foe nearest the line from {@code eye} along {@code dir} within the cone ({@code cos}) and range, in sight. */
    public static LivingEntity nearestInCone(LivingEntity caster, Vec3 eye, Vec3 dir, double cos, double range) {
        List<LivingEntity> found = caster.level().getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(range),
            e -> hostileTo(caster, e) && e.distanceToSqr(caster) <= range * range
                && dir.dot(e.getBoundingBox().getCenter().subtract(eye).normalize()) >= cos && caster.hasLineOfSight(e));
        return found.stream().min(Comparator.comparingDouble(e -> -dir.dot(e.getBoundingBox().getCenter().subtract(eye).normalize())
            + e.distanceTo(caster) / (range * 4.0))).orElse(null);
    }
}
