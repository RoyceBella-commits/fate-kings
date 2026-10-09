package cn.blockforge.fatekings.combat;

import cn.blockforge.fatekings.compat.JjkCompat;
import cn.blockforge.fatekings.config.FateConfig;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.PriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Terrain changes of the noble phantasms, spread over ticks. One switch for everything: this mod's
 * config and, when the Gojo x Sukuna mod is installed, its switch too (both must allow it).
 */
public final class Terrain {
    private static final int BUDGET = 700;
    private record Op(ResourceKey<Level> dim, BlockPos pos, BlockState state) {
    }
    private record Later(long at, ResourceKey<Level> dim, BlockPos pos, BlockState expect, BlockState state) {
    }
    private static final Deque<Op> QUEUE = new ArrayDeque<>();
    private static final PriorityQueue<Later> LATER = new PriorityQueue<>((a, b) -> Long.compare(a.at, b.at));

    /** A noble phantasm's blast only breaks blocks: the judgement has already dealt with the living. */
    private static final ExplosionDamageCalculator BLOCKS_ONLY = new ExplosionDamageCalculator() {
        @Override
        public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
            return false;
        }

        @Override
        public float getKnockbackMultiplier(Entity entity) {
            return 0.6f;
        }
    };

    private Terrain() {
    }

    /**
     * An explosion of {@code power} at {@code at} that breaks blocks (when terrain effects are on,
     * and as mob griefing allows) and hurts no one.
     */
    public static void blast(ServerLevel level, Entity source, Vec3 at, float power) {
        level.explode(source, null, BLOCKS_ONLY, at.x, at.y, at.z, power, false,
            enabled() ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE);
    }

    public static boolean enabled() {
        Boolean jjk = JjkCompat.terrain();
        return FateConfig.terrainDestruction() && (jjk == null || jjk);
    }

    public static void clear() {
        QUEUE.clear();
        LATER.clear();
    }

    /** Whether a block may be removed at all (not bedrock, barriers, portals or air). */
    public static boolean breakable(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.isAir() && state.getDestroySpeed(level, pos) >= 0.0f && state.getBlock().getExplosionResistance() < 1200.0f;
    }

    public static void set(ServerLevel level, BlockPos pos, BlockState state) {
        if (QUEUE.size() < 400_000) QUEUE.add(new Op(level.dimension(), pos.immutable(), state));
    }

    public static void carve(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (breakable(level, pos, s)) set(level, pos, Blocks.AIR.defaultBlockState());
    }

    /** Replaces {@code pos} with {@code state} after {@code delay} ticks if it still holds {@code expect}. */
    public static void later(ServerLevel level, BlockPos pos, BlockState expect, BlockState state, int delay) {
        if (LATER.size() < 200_000) LATER.add(new Later(level.getGameTime() + delay, level.dimension(), pos.immutable(), expect, state));
    }

    public static void tick(MinecraftServer server) {
        int budget = BUDGET;
        while (budget-- > 0 && !QUEUE.isEmpty()) {
            Op op = QUEUE.poll();
            ServerLevel level = server.getLevel(op.dim());
            if (level == null || !level.isLoaded(op.pos())) continue;
            BlockState cur = level.getBlockState(op.pos());
            if (!op.state().isAir() || breakable(level, op.pos(), cur)) level.setBlock(op.pos(), op.state(), 3);
        }
        long now = server.overworld().getGameTime();
        int cool = BUDGET;
        while (cool-- > 0 && !LATER.isEmpty() && LATER.peek().at() <= now) {
            Later l = LATER.poll();
            ServerLevel level = server.getLevel(l.dim());
            if (level == null || !level.isLoaded(l.pos())) continue;
            if (level.getBlockState(l.pos()).is(l.expect().getBlock())) level.setBlock(l.pos(), l.state(), 3);
        }
    }
}
