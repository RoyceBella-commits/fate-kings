package cn.blockforge.fatekings.registry;

import cn.blockforge.fatekings.FateKings;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

/** World game rules of the mod. */
public final class FateRules {
    /** Excalibur kills vanilla bosses outright (design doc hard rule); off: they only take heavy damage. */
    public static final GameRule<Boolean> EXCALIBUR_BOSS_INSTAKILL = GameRuleBuilder.forBoolean(true)
        .category(GameRuleCategory.MOBS).buildAndRegister(FateKings.id("excalibur_boss_instakill"));
    /** "A knight does not strike her own side": the caster's tamed pets are spared by Excalibur. */
    public static final GameRule<Boolean> KNIGHT_SPARES_PETS = GameRuleBuilder.forBoolean(true)
        .category(GameRuleCategory.MOBS).buildAndRegister(FateKings.id("knight_spares_pets"));
    /** The two regalia stay on the body at death. */
    public static final GameRule<Boolean> KEEP_REGALIA = GameRuleBuilder.forBoolean(true)
        .category(GameRuleCategory.PLAYER).buildAndRegister(FateKings.id("keep_regalia"));

    private FateRules() {
    }

    public static boolean get(ServerLevel level, GameRule<Boolean> rule) {
        return level.getGameRules().get(rule);
    }

    public static void init() {
    }
}
