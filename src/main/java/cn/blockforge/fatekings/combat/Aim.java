package cn.blockforge.fatekings.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Where a caster is aiming: the crosshair for players, the current target for NPCs. */
public record Aim(Vec3 point, LivingEntity entity) {
    public static Aim of(LivingEntity caster, double range) {
        if (caster instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive()) {
            LivingEntity t = mob.getTarget();
            return new Aim(t.getBoundingBox().getCenter(), t);
        }
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult block = caster.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        Vec3 limit = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(caster.level(), caster, eye, limit, new AABB(eye, limit).inflate(1.5),
            e -> e instanceof LivingEntity l && l.isAlive() && !e.isSpectator() && e != caster && !caster.isPassengerOfSameVehicle(e), 0.6f);
        if (hit != null && hit.getEntity() instanceof LivingEntity l) {
            return new Aim(l.getBoundingBox().getCenter(), l);
        }
        return new Aim(limit, null);
    }

    public Vec3 dirFrom(Vec3 from) {
        Vec3 d = this.point.subtract(from);
        return d.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : d.normalize();
    }

    /** A target point on {@code e} (its centre). */
    public static Vec3 centre(Entity e) {
        return e.getBoundingBox().getCenter();
    }
}
