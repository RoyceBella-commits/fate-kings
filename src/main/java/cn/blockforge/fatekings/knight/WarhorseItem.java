package cn.blockforge.fatekings.knight;

import cn.blockforge.fatekings.FateKings;
import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.hero.GateOfBabylon;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.Kings;
import cn.blockforge.fatekings.king.Skills;
import cn.blockforge.fatekings.registry.FateItems;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * The King of Knights' warhorse: a white horse in silver-blue barding, half as fast again as the
 * fastest horse, running on water with her. It vanishes into light after 60 damage, when she leaves
 * it for 30 s, or when she is no longer the King of Knights.
 */
public class WarhorseItem extends Item {
    public static final String TAG = "fatekings.warhorse";
    public static final Identifier MOUNT_SPEED = FateKings.id("knight_mount");
    private static final Map<UUID, UUID> HORSE_OWNER = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_RIDDEN = new ConcurrentHashMap<>();

    public WarhorseItem(Properties properties) {
        super(properties);
    }

    public static void clear() {
        HORSE_OWNER.clear();
        LAST_RIDDEN.clear();
        MOUNTED.clear();
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!Kings.isKnight(player)) {
            if (!level.isClientSide()) Kings.refuse(player, "fatekings.hint.sword_answers_king");
            return InteractionResult.FAIL;
        }
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        for (var e : HORSE_OWNER.entrySet()) {
            if (e.getValue().equals(player.getUUID()) && server.getEntity(e.getKey()) instanceof Horse old) {
                vanish(server, old);
                return InteractionResult.SUCCESS_SERVER;
            }
        }
        if (!GateOfBabylon.ready(player, Skills.WARHORSE)) return InteractionResult.FAIL;
        Horse horse = EntityTypes.HORSE.create(server, EntitySpawnReason.MOB_SUMMONED);
        if (horse == null) return InteractionResult.FAIL;
        horse.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0f);
        horse.tameWithName(player);
        horse.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
        horse.setItemSlot(EquipmentSlot.BODY, new ItemStack(FateItems.KNIGHT_BARDING));
        horse.setDropChance(EquipmentSlot.SADDLE, 0.0f);
        horse.setDropChance(EquipmentSlot.BODY, 0.0f);
        horse.getAttribute(Attributes.MAX_HEALTH).setBaseValue(60.0);
        horse.setHealth(60.0f);
        horse.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3375 * 1.5);
        horse.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(1.0);
        horse.addTag(TAG);
        horse.setPersistenceRequired();
        server.addFreshEntity(horse);
        HORSE_OWNER.put(horse.getUUID(), player.getUUID());
        LAST_RIDDEN.put(horse.getUUID(), server.getGameTime());
        player.startRiding(horse);
        Kings.of(player).cooldown(Skills.WARHORSE, server.getGameTime(), KingRules.WARHORSE);
        Fx.particles(server, Fx.dust(0xDDF4FF, 1.4f), horse.getX(), horse.getY() + 1.0, horse.getZ(), 40, 0.8, 0.8, 0.8, 0.0);
        Fx.particles(server, ParticleTypes.END_ROD, horse.getX(), horse.getY() + 1.0, horse.getZ(), 12, 0.6, 0.6, 0.6, 0.03);
        server.playSound(null, horse.getX(), horse.getY(), horse.getZ(), SoundEvents.HORSE_AMBIENT, SoundSource.NEUTRAL, 1.2f, 1.1f);
        return InteractionResult.SUCCESS_SERVER;
    }

    public static boolean isWarhorse(Entity e) {
        return e instanceof AbstractHorse && e.entityTags().contains(TAG);
    }

    public static void vanish(ServerLevel level, LivingEntity horse) {
        HORSE_OWNER.remove(horse.getUUID());
        LAST_RIDDEN.remove(horse.getUUID());
        horse.ejectPassengers();
        Fx.particles(level, Fx.dust(0xDDF4FF, 1.4f), horse.getX(), horse.getY() + 1.0, horse.getZ(), 40, 0.8, 0.8, 0.8, 0.0);
        Fx.particles(level, ParticleTypes.END_ROD, horse.getX(), horse.getY() + 1.0, horse.getZ(), 16, 0.6, 0.6, 0.6, 0.05);
        level.playSound(null, horse.getX(), horse.getY(), horse.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.2f, 1.2f);
        horse.discard();
    }

    /** Per-horse upkeep (every second). A warhorse from a previous session has no owner: it vanishes. */
    public static void tickHorse(ServerLevel level, AbstractHorse horse) {
        UUID owner = HORSE_OWNER.get(horse.getUUID());
        if (owner == null) {
            vanish(level, horse);
            return;
        }
        Entity rider = horse.getFirstPassenger();
        long now = level.getGameTime();
        if (rider != null && rider.getUUID().equals(owner)) LAST_RIDDEN.put(horse.getUUID(), now);
        Entity o = level.getEntity(owner);
        boolean ownerKnight = o instanceof ServerPlayer p && Kings.isKnight(p);
        if (!ownerKnight || now - LAST_RIDDEN.getOrDefault(horse.getUUID(), now) > 600) vanish(level, horse);
    }

    /** Riding: +50% speed for any mount of the King of Knights, removed when she gets off. */
    public static void tickRider(ServerPlayer p) {
        Entity v = p.getVehicle();
        UUID last = MOUNTED.get(p.getUUID());
        if (last != null && (v == null || !v.getUUID().equals(last))) {
            MOUNTED.remove(p.getUUID());
            if (p.level().getEntity(last) instanceof AbstractHorse old) {
                Kings.set(old, Attributes.MOVEMENT_SPEED, MOUNT_SPEED, 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            }
        }
        if (v instanceof AbstractHorse horse) {
            boolean knight = Kings.isKnight(p);
            Kings.set(horse, Attributes.MOVEMENT_SPEED, MOUNT_SPEED, knight ? 0.5 : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            if (knight) MOUNTED.put(p.getUUID(), horse.getUUID());
        }
    }

    private static final Map<UUID, UUID> MOUNTED = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.fatekings.warhorse.desc").withStyle(ChatFormatting.AQUA));
    }
}
