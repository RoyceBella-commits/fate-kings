package cn.blockforge.fatekings.config;

import cn.blockforge.fatekings.archer.ArcherBow;
import cn.blockforge.fatekings.archer.ArcherRules;
import cn.blockforge.fatekings.archer.CraneWing;
import cn.blockforge.fatekings.archer.ExcaliburReplicaItem;
import cn.blockforge.fatekings.archer.RhoAias;
import cn.blockforge.fatekings.archer.UnlimitedBladeWorks;
import cn.blockforge.fatekings.combat.Terrain;
import cn.blockforge.fatekings.compat.JjkCompat;
import cn.blockforge.fatekings.hero.EaItem;
import cn.blockforge.fatekings.hero.Enkidu;
import cn.blockforge.fatekings.hero.GateOfBabylon;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.KingState;
import cn.blockforge.fatekings.king.Kings;
import cn.blockforge.fatekings.knight.Avalon;
import cn.blockforge.fatekings.knight.ExcaliburSkill;
import cn.blockforge.fatekings.knight.KnightLeap;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

/**
 * {@code /fate terrain on|off}, {@code /fate cooldowns reset [players]} and, for testing,
 * {@code /fate cast <skill> [ticks]} (operators only).
 */
public final class FateCommands {
    private static final String[] CASTS = {"gob_tap", "gob_volley", "gob_ring", "bab_ilu", "enuma_elish", "enkidu_hook", "enkidu_bind",
        "strike_air", "mana_burst", "excalibur", "avalon", "leap",
        "bow_tap", "bow_triple", "caladbolg", "rho_aias", "twin_throw", "crane_wing", "ubw", "excalibur_replica"};

    private FateCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("fate")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.literal("terrain")
                .then(Commands.literal("on").executes(ctx -> terrain(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> terrain(ctx.getSource(), false)))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.translatable(Terrain.enabled() ? "fatekings.cmd.terrain.on" : "fatekings.cmd.terrain.off"), false);
                    return 1;
                }))
            .then(Commands.literal("cooldowns").then(Commands.literal("reset")
                .executes(ctx -> reset(ctx.getSource(), java.util.List.of(ctx.getSource().getPlayerOrException())))
                .then(Commands.argument("players", EntityArgument.players())
                    .executes(ctx -> reset(ctx.getSource(), EntityArgument.getPlayers(ctx, "players"))))))
            .then(Commands.literal("cast").then(Commands.argument("skill", StringArgumentType.word())
                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(CASTS, b))
                .executes(ctx -> cast(ctx, 0))
                .then(Commands.argument("ticks", IntegerArgumentType.integer(0, 200))
                    .executes(ctx -> cast(ctx, IntegerArgumentType.getInteger(ctx, "ticks")))))));
    }

    private static int terrain(CommandSourceStack source, boolean on) {
        FateConfig.setTerrainDestruction(on);
        JjkCompat.setTerrain(on);
        source.sendSuccess(() -> Component.translatable(on ? "fatekings.cmd.terrain.on" : "fatekings.cmd.terrain.off"), true);
        return 1;
    }

    private static int reset(CommandSourceStack source, Collection<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            KingState s = Kings.of(p);
            s.clearAllCooldowns();
            s.reorgUntil = 0L;
            s.depletionUntil = 0L;
            s.lockUntil = 0L;
        }
        source.sendSuccess(() -> Component.translatable("fatekings.cmd.cooldowns_reset", players.size()), true);
        return players.size();
    }

    private static int cast(CommandContext<CommandSourceStack> ctx, int ticks) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof LivingEntity caster) || !(source.getLevel() instanceof ServerLevel level)) return 0;
        String skill = StringArgumentType.getString(ctx, "skill");
        boolean ok = switch (skill) {
            case "gob_tap" -> GateOfBabylon.tap(caster, 3, true);
            case "gob_volley" -> {
                int held = Math.max(KingRules.GOB_VOLLEY_MIN, ticks == 0 ? KingRules.GOB_VOLLEY_FULL : ticks);
                GateOfBabylon.growVolley(caster, held);
                GateOfBabylon.releaseVolley(caster);
                yield true;
            }
            case "gob_ring" -> GateOfBabylon.ring(caster);
            case "bab_ilu" -> {
                if (caster instanceof ServerPlayer p) {
                    cn.blockforge.fatekings.hero.BabIluItem.beginRitual(level, p);
                    cn.blockforge.fatekings.hero.BabIluItem.drawEa(level, p, InteractionHand.MAIN_HAND, p.getMainHandItem().copy());
                }
                yield true;
            }
            case "enuma_elish" -> {
                EaItem.release(caster, Math.max(KingRules.EA_CHARGE, ticks), null);
                yield true;
            }
            case "enkidu_hook" -> Enkidu.hook(caster);
            case "enkidu_bind" -> Enkidu.bind(caster);
            case "strike_air" -> ExcaliburSkill.strikeAir(caster);
            case "mana_burst" -> ExcaliburSkill.manaBurst(caster);
            case "excalibur" -> {
                if (ExcaliburSkill.mayCharge(caster)) ExcaliburSkill.release(caster, Math.max(KingRules.EXCALIBUR_CHARGE, ticks), null);
                yield true;
            }
            case "avalon" -> {
                KingState s = Kings.of(caster);
                if (s != null) Avalon.unfold(caster, s, null);
                yield s != null;
            }
            case "leap" -> {
                KnightLeap.leap(caster, 0.0f, 0.0f, caster.onGround());
                yield true;
            }
            case "bow_tap" -> ArcherBow.tap(caster);
            case "bow_triple" -> ArcherBow.triple(caster);
            case "caladbolg" -> ArcherBow.caladbolg(caster, null);
            case "rho_aias" -> RhoAias.cast(caster);
            case "twin_throw" -> CraneWing.throwPair(caster);
            case "crane_wing" -> CraneWing.start(caster);
            case "ubw" -> UnlimitedBladeWorks.mayChant(caster) && UnlimitedBladeWorks.unfold(caster);
            case "excalibur_replica" -> ExcaliburReplicaItem.release(caster, Math.max(ArcherRules.REPLICA_CHARGE, ticks), null);
            default -> false;
        };
        if (!ok) {
            source.sendFailure(Component.translatable("fatekings.cmd.cast_failed", skill));
            return 0;
        }
        return 1;
    }
}
