package cn.blockforge.fatekings.king;

import cn.blockforge.fatekings.FateKings;
import cn.blockforge.fatekings.archer.Archer;
import cn.blockforge.fatekings.archer.ArcherPassives;
import cn.blockforge.fatekings.archer.ArcherRules;
import cn.blockforge.fatekings.combat.DamageRules;
import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.compat.JjkCompat;
import cn.blockforge.fatekings.hero.EaItem;
import cn.blockforge.fatekings.hero.HeroPassives;
import cn.blockforge.fatekings.knight.ExcaliburItem;
import cn.blockforge.fatekings.knight.KnightPassives;
import cn.blockforge.fatekings.net.FateNet;
import cn.blockforge.fatekings.npc.KingNpcEntity;
import cn.blockforge.fatekings.registry.FateItems;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Who is a king right now, and everything the full set grants while it is worn. */
public final class Kings {
    public static final AttachmentType<KingState> STATE = AttachmentRegistry.<KingState>builder()
        .persistent(KingState.CODEC).copyOnDeath().initializer(KingState::fresh).buildAndRegister(FateKings.id("king"));

    public static final Identifier MOD_HEALTH = FateKings.id("king_health");
    public static final Identifier MOD_ARMOR = FateKings.id("king_armor");
    public static final Identifier MOD_TOUGHNESS = FateKings.id("king_toughness");
    public static final Identifier MOD_KNOCKBACK = FateKings.id("king_knockback");
    public static final Identifier MOD_SPEED = FateKings.id("king_speed");
    public static final Identifier MOD_UNARMED = FateKings.id("king_unarmed");
    public static final Identifier MOD_EXCALIBUR = FateKings.id("excalibur_power");
    public static final Identifier MOD_REACH = FateKings.id("invisible_air_reach");
    public static final Identifier MOD_DEPLETION = FateKings.id("mana_depletion");
    public static final Identifier MOD_TWIN = FateKings.id("kanshou_bakuya");
    private static final Component[] NO_ARGS = {};

    /** Client hooks (set by the client initializer; the defaults describe a dedicated server). */
    public static volatile IntSupplier clientKing = () -> KingRules.NONE;
    public static volatile Predicate<Entity> clientIsLocal = e -> false;

    private Kings() {
    }

    // ---------------------------------------------------------------------------------------------
    // Queries
    // ---------------------------------------------------------------------------------------------

    public static KingState of(LivingEntity e) {
        if (e instanceof Player p) return p.getAttachedOrCreate(STATE);
        if (e instanceof KingNpcEntity npc) return npc.kingState();
        return null;
    }

    /** King type of an entity on the logical server (and of NPCs on both sides). */
    public static int king(Entity e) {
        if (e instanceof KingNpcEntity npc) return npc.kingType();
        if (e instanceof Player p) {
            if (p.level().isClientSide()) return clientIsLocal.test(p) ? clientKing.getAsInt() : KingRules.NONE;
            KingState s = p.getAttached(STATE);
            return s == null ? KingRules.NONE : s.king;
        }
        return KingRules.NONE;
    }

    public static boolean isKing(Entity e) {
        return king(e) != KingRules.NONE;
    }

    public static boolean isHero(Entity e) {
        return king(e) == KingRules.HERO;
    }

    public static boolean isKnight(Entity e) {
        return king(e) == KingRules.KNIGHT;
    }

    public static boolean isArcher(Entity e) {
        return king(e) == KingRules.ARCHER;
    }

    public static long now(Entity e) {
        return e.level().getGameTime();
    }

    public static int heroPieces(LivingEntity e) {
        int n = 0;
        for (EquipmentSlot slot : ARMOR) if (FateItems.heroPiece(e.getItemBySlot(slot))) ++n;
        return n;
    }

    public static int knightPieces(LivingEntity e) {
        int n = 0;
        for (EquipmentSlot slot : ARMOR) if (FateItems.knightPiece(e.getItemBySlot(slot))) ++n;
        return n;
    }

    public static int archerPieces(LivingEntity e) {
        int n = 0;
        for (EquipmentSlot slot : ARMOR) if (FateItems.archerPiece(e.getItemBySlot(slot))) ++n;
        return n;
    }

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    // ---------------------------------------------------------------------------------------------
    // Player tick
    // ---------------------------------------------------------------------------------------------

    public static void tick(ServerPlayer p) {
        KingState s = of(p);
        long now = now(p);
        int wanted = p.isSpectator() || !p.isAlive() ? KingRules.NONE : KingRules.kingOfSet(heroPieces(p), knightPieces(p), archerPieces(p));
        if (wanted != s.king) {
            if (wanted != KingRules.NONE && !KingRules.mayEnter(wanted, s.lockedFrom, s.lockUntil, now)) {
                if (now % 20 == 0) {
                    FateNet.actionBar(p, Component.translatable("fatekings.hint.swap_lock",
                        String.format(java.util.Locale.ROOT, "%.1f", (s.lockUntil - now) / 20.0f)));
                }
                wanted = KingRules.NONE;
            }
            if (wanted != s.king) transition(p, s, wanted, now);
        }
        if (now % 10 == 0 || s.dirty) applyAttributes(p, s);
        updateHeldModifiers(p, s, now);
        tickGold(p, s, now);
        switch (s.king) {
            case KingRules.HERO -> HeroPassives.tick(p, s, now);
            case KingRules.KNIGHT -> KnightPassives.tick(p, s, now);
            case KingRules.ARCHER -> ArcherPassives.tick(p, s, now);
            default -> {
            }
        }
        // Sha Naqba Imuru for the King of Heroes, Clairvoyance for the Archer.
        if ((s.king == KingRules.HERO || s.king == KingRules.ARCHER) && now % 20 == 0) SideSync.send(p);
        HeroPassives.tickFlight(p, s);
        Archer.sweep(p, now);
        if (s.dirty || now % 10 == 0) KingSync.send(p, s, now);
        if (now % 10 == 0 && p.containerMenu != p.inventoryMenu) {
            EaItem.purgeContainer(p);
            Archer.purgeMenu(p);
        }
    }

    private static void transition(ServerPlayer p, KingState s, int to, long now) {
        int from = s.king;
        ServerLevel level = p.level();
        if (from != KingRules.NONE) {
            // Leaving: charges stop, Ea goes home, the Vimana lands, the other king waits 15 s.
            if (p.isUsingItem()) {
                if (p.getUseItem().is(FateItems.EXCALIBUR)) cn.blockforge.fatekings.knight.ExcaliburSkill.cancel(level, p);
                p.stopUsingItem();
            }
            cn.blockforge.fatekings.hero.GateOfBabylon.cancelVolley(p);
            EaItem.dissipateAll(p);
            if (from == KingRules.ARCHER) Archer.leave(p);
            s.lockedFrom = from;
            s.lockUntil = now + KingRules.SWAP_LOCK;
        }
        s.king = to;
        s.dirty = true;
        if (to == KingRules.NONE) {
            applyAttributes(p, s);
            return;
        }
        if (s.gold < 0.0f) s.gold = goldTarget(p);
        s.gold = Math.min(s.gold, goldTarget(p));
        applyAttributes(p, s);
        if (to == KingRules.ARCHER) {
            // The Red Shroud: a flare of embers and the ring of a hammer on the anvil.
            Fx.event(level, Fx.ARRIVAL_ARCHER, p, p.position(), 40, 1.0f, 64.0);
            Fx.eventTo(p, Fx.TITLE_ARCHER, p, p.position(), 60, 1.0f);
            Fx.ring(level, p.position().add(0.0, 0.3, 0.0), Fx.EMBER, 1.5, 32);
            Fx.particles(level, ParticleTypes.SMALL_FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 24, 0.6, 0.9, 0.6, 0.02);
            Fx.particles(level, Fx.dust(Fx.SHROUD_RED, 1.1f), p.getX(), p.getY() + 1.0, p.getZ(), 24, 0.6, 0.8, 0.6, 0.0);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 1.4f, 0.8f);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8f, 0.7f);
        } else if (to == KingRules.HERO) {
            Fx.event(level, Fx.ARRIVAL_HERO, p, p.position(), 40, 1.0f, 64.0);
            Fx.eventTo(p, Fx.TITLE_HERO, p, p.position(), 60, 1.0f);
            Fx.ring(level, p.position().add(0.0, 1.2, 0.0), Fx.GOLD, 1.6, 36);
            Fx.particles(level, ParticleTypes.END_ROD, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.6, 0.9, 0.6, 0.02);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.6f, 0.55f);
        } else {
            Fx.event(level, Fx.ARRIVAL_KNIGHT, p, p.position(), 40, 1.0f, 64.0);
            Fx.eventTo(p, Fx.TITLE_KNIGHT, p, p.position(), 60, 1.0f);
            Fx.particles(level, ParticleTypes.GUST, p.getX(), p.getY() + 0.2, p.getZ(), 2, 0.4, 0.0, 0.4, 0.0);
            Fx.ring(level, p.position().add(0.0, 0.2, 0.0), Fx.WIND, 1.4, 30);
            Fx.particles(level, Fx.dust(Fx.GOLD, 0.9f), p.getX(), p.getY() + 0.6, p.getZ(), 20, 0.8, 0.5, 0.8, 0.0);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 1.4f, 1.6f);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 1.4f);
        }
    }

    /** Gold hearts this mod grants: 30 hearts, minus whatever the other mod already gives (higher wins). */
    public static float goldTarget(LivingEntity e) {
        if (e instanceof Player p) return DamageRules.kingGoldTarget(JjkCompat.goldMax(p));
        return KingRules.NPC_GOLD_HP;
    }

    private static void tickGold(ServerPlayer p, KingState s, long now) {
        if (s.king == KingRules.NONE) return;
        float max = goldTarget(p);
        if (s.gold > max) {
            s.gold = max;
            s.dirty = true;
        }
        if (s.gold < max && now - s.lastHurt >= KingRules.GOLD_REGEN_DELAY && (now - s.lastHurt) % KingRules.GOLD_REGEN_INTERVAL == 0) {
            s.gold = Math.min(max, s.gold + 2.0f);
            s.dirty = true;
        }
    }

    /** Called with the health a hit is about to remove (after armour); gold takes it first. */
    public static float absorb(LivingEntity e, float loss) {
        KingState s = of(e);
        if (s == null || loss <= 0.0f) return loss;
        s.lastHurt = now(e);
        s.lastCombat = s.lastHurt;
        if (king(e) == KingRules.NONE || s.gold <= 0.0f) return loss;
        float[] split = KingRules.splitDamage(s.gold, loss);
        if (split[0] > 0.0f) {
            s.gold -= split[0];
            s.dirty = true;
            if (e.level() instanceof ServerLevel level) {
                Fx.particles(level, Fx.dust(Fx.GOLD, 0.9f), e.getX(), e.getY() + 1.1, e.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
            }
        }
        return split[1];
    }

    // ---------------------------------------------------------------------------------------------
    // Attributes
    // ---------------------------------------------------------------------------------------------

    public static void applyAttributes(LivingEntity e, KingState s) {
        boolean king = s.king != KingRules.NONE;
        set(e, Attributes.MAX_HEALTH, MOD_HEALTH, king ? KingRules.MAX_HEALTH - 20.0 : 0.0, AttributeModifier.Operation.ADD_VALUE);
        topUp(e, Attributes.ARMOR, MOD_ARMOR, king ? KingRules.ARMOR : 0.0);
        topUp(e, Attributes.ARMOR_TOUGHNESS, MOD_TOUGHNESS, king ? KingRules.TOUGHNESS : 0.0);
        set(e, Attributes.KNOCKBACK_RESISTANCE, MOD_KNOCKBACK, king ? 1.0 : 0.0, AttributeModifier.Operation.ADD_VALUE);
        double speed = KingRules.speed(s.king);
        set(e, Attributes.MOVEMENT_SPEED, MOD_SPEED, speed, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        if (e.getHealth() > e.getMaxHealth()) e.setHealth(e.getMaxHealth());
    }

    /** Modifiers that depend on what is held: bare fists, Excalibur's power and reach, mana depletion. */
    private static void updateHeldModifiers(ServerPlayer p, KingState s, long now) {
        ItemStack main = p.getMainHandItem();
        boolean king = s.king != KingRules.NONE;
        topUp(p, Attributes.ATTACK_DAMAGE, MOD_UNARMED, king && main.isEmpty() ? KingRules.UNARMED : 0.0);
        boolean knightSword = s.king == KingRules.KNIGHT && main.is(FateItems.EXCALIBUR);
        boolean revealed = knightSword && ExcaliburItem.revealed(p, s, now);
        topUp(p, Attributes.ATTACK_DAMAGE, MOD_EXCALIBUR, knightSword ? KingRules.excaliburSwing(revealed, false) : 0.0);
        set(p, Attributes.ENTITY_INTERACTION_RANGE, MOD_REACH, knightSword && !revealed ? 1.5 : 0.0, AttributeModifier.Operation.ADD_VALUE);
        set(p, Attributes.MOVEMENT_SPEED, MOD_DEPLETION, s.king == KingRules.KNIGHT && s.depleted(now) ? -0.4 : 0.0,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        boolean twin = s.king == KingRules.ARCHER && FateItems.twinSword(main);
        topUp(p, Attributes.ATTACK_DAMAGE, MOD_TWIN, twin ? ArcherRules.TWIN_SWING : 0.0);
    }

    /** Adds / updates / removes a transient modifier (0 removes it). */
    public static void set(LivingEntity e, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = e.getAttribute(attribute);
        if (inst == null) return;
        AttributeModifier current = inst.getModifier(id);
        if (amount == 0.0) {
            if (current != null) inst.removeModifier(id);
            return;
        }
        if (current == null || current.amount() != amount || current.operation() != op) {
            inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
        }
    }

    /**
     * Raises an attribute to {@code target} with an additive modifier, counting only what the other
     * sources give (never stacking on top of a higher value from another mod).
     */
    public static void topUp(LivingEntity e, Holder<Attribute> attribute, Identifier id, double target) {
        AttributeInstance inst = e.getAttribute(attribute);
        if (inst == null) return;
        AttributeModifier current = inst.getModifier(id);
        double without = inst.getValue() - (current == null ? 0.0 : current.amount());
        double amount = target <= 0.0 ? 0.0 : DamageRules.topUp(without, target);
        if (Math.abs(amount) < 1.0E-4) {
            if (current != null) inst.removeModifier(id);
            return;
        }
        if (current == null || Math.abs(current.amount() - amount) > 1.0E-4) {
            inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    public static void onRespawn(ServerPlayer p) {
        KingState s = of(p);
        s.king = KingRules.NONE;
        s.gold = -1.0f;
        s.domeUntil = 0L;
        s.guardUntil = 0L;
        s.healUntil = 0L;
        s.dirty = true;
        Archer.leave(p);
    }

    public static void refuse(Player p, String key, Object... args) {
        if (p instanceof ServerPlayer sp) FateNet.actionBar(sp, Component.translatable(key, args));
    }
}
