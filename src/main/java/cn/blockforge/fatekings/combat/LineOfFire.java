package cn.blockforge.fatekings.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** What a guardian spirit checks before a noble phantasm: no villager, golem, pet or home in the path (or under it). */
public final class LineOfFire {
    private LineOfFire() {
    }

    /** No villager, iron golem, pet or bed / chest / crafting table within {@code halfWidth} of the path. */
    public static boolean clear(ServerLevel level, LivingEntity self, Vec3 origin, Vec3 dir, double range, double halfWidth) {
        Vec3 end = origin.add(dir.scale(range));
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(origin, end).inflate(halfWidth), e -> e != self)) {
            boolean protectedOne = e instanceof AbstractVillager || e instanceof IronGolem || e instanceof OwnableEntity o && o.getOwner() != null;
            if (!protectedOne) continue;
            Vec3 c = e.getBoundingBox().getCenter();
            double t = c.subtract(origin).dot(dir);
            if (t > 0 && c.distanceTo(origin.add(dir.scale(t))) < halfWidth) return false;
        }
        // With terrain effects on, Excalibur's trench reaches 4 blocks aside and 6 below the path.
        boolean terrain = Terrain.enabled();
        int aside = terrain ? 4 : 2, below = terrain ? 6 : 2;
        for (double t = 4.0; t < range; t += 2.0) {
            Vec3 p = origin.add(dir.scale(t));
            BlockPos pos = BlockPos.containing(p);
            if (!level.isLoaded(pos)) break;
            for (BlockPos q : BlockPos.betweenClosed(pos.offset(-aside, -below, -aside), pos.offset(aside, 2, aside))) {
                BlockState s = level.getBlockState(q);
                if (s.getBlock() instanceof BedBlock || s.getBlock() instanceof ChestBlock || s.getBlock() instanceof CraftingTableBlock) return false;
            }
        }
        return true;
    }
}
