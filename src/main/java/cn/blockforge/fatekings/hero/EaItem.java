package cn.blockforge.fatekings.hero;

import cn.blockforge.fatekings.combat.Aim;
import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.combat.Terrain;
import cn.blockforge.fatekings.entity.EnumaElishEntity;
import cn.blockforge.fatekings.king.KingRules;
import cn.blockforge.fatekings.king.KingState;
import cn.blockforge.fatekings.king.Kings;
import cn.blockforge.fatekings.knight.Instinct;
import cn.blockforge.fatekings.registry.FateItems;
import cn.blockforge.fatekings.voice.Voice;
import cn.blockforge.fatekings.voice.VoicePlayer;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Ea, the Sword of Rupture. Only drawn through Bab-ilu, kept in the main hand for 30 s, gone the
 * moment it leaves the King of Heroes' main hand. Swings for 40; held for 3 s and released it
 * opens Enuma Elish, then returns to the treasury.
 */
public class EaItem extends Item {
    private static final String EXPIRE = "fatekings_expire";
    private static final String KEY = "fatekings_key";

    public EaItem(Properties properties) {
        super(properties);
    }

    public static ItemAttributeModifiers attributes() {
        return ItemAttributeModifiers.builder()
            .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, 39.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, -3.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
            .build();
    }

    /** A drawn Ea that remembers the key it came from. */
    public static ItemStack draw(ServerLevel level, ItemStack key, long expire) {
        ItemStack ea = new ItemStack(FateItems.EA);
        CompoundTag tag = new CompoundTag();
        tag.putLong(EXPIRE, expire);
        if (!key.isEmpty()) {
            ItemStack.CODEC.encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), key)
                .result().ifPresent(t -> tag.put(KEY, t));
        }
        ea.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return ea;
    }

    private static CompoundTag data(ItemStack stack) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        return d == null ? new CompoundTag() : d.copyTag();
    }

    public static long expireAt(ItemStack stack) {
        return data(stack).getLongOr(EXPIRE, 0L);
    }

    /** The key Ea was drawn with (a fresh key if it was lost). */
    public static ItemStack keyOf(Level level, ItemStack ea) {
        CompoundTag tag = data(ea);
        if (tag.contains(KEY)) {
            var parsed = ItemStack.CODEC.parse(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), tag.get(KEY)).result();
            if (parsed.isPresent()) return parsed.get();
        }
        return new ItemStack(FateItems.BAB_ILU);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (!(entity instanceof LivingEntity holder)) return;
        boolean keep = slot == EquipmentSlot.MAINHAND && Kings.isHero(holder) && level.getGameTime() < expireAt(stack);
        if (!keep) dissipate(holder, stack);
    }

    /** Ea turns to golden light and goes home; the key returns to where Ea was. */
    public static void dissipate(LivingEntity holder, ItemStack ea) {
        if (!(holder.level() instanceof ServerLevel level)) return;
        ItemStack key = holder instanceof Player ? keyOf(level, ea) : ItemStack.EMPTY;
        if (holder instanceof Player p) {
            var inv = p.getInventory();
            boolean replaced = false;
            for (int i = 0; i < inv.getContainerSize(); ++i) {
                if (inv.getItem(i) == ea) {
                    inv.setItem(i, key);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                ea.setCount(0);
                if (!p.getInventory().add(key) && p.level() instanceof ServerLevel sl) p.spawnAtLocation(sl, key);
            }
        } else {
            for (EquipmentSlot s : EquipmentSlot.values()) {
                if (holder.getItemBySlot(s) == ea) holder.setItemSlot(s, ItemStack.EMPTY);
            }
        }
        Fx.particles(level, Fx.dust(Fx.GOLD, 1.2f), holder.getX(), holder.getY() + 1.2, holder.getZ(), 20, 0.4, 0.4, 0.4, 0.0);
        Fx.particles(level, ParticleTypes.END_ROD, holder.getX(), holder.getY() + 1.2, holder.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
        level.playSound(null, holder.getX(), holder.getY(), holder.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.8f);
    }

    /** Ea never stays in a chest or any other container: it returns and the key comes back. */
    public static void purgeContainer(Player p) {
        for (net.minecraft.world.inventory.Slot slot : p.containerMenu.slots) {
            if (slot.container == p.getInventory()) continue;
            ItemStack s = slot.getItem();
            if (!s.is(FateItems.EA)) continue;
            ItemStack key = keyOf(p.level(), s);
            slot.set(ItemStack.EMPTY);
            if (!p.getInventory().add(key) && p.level() instanceof ServerLevel sl) p.spawnAtLocation(sl, key);
        }
        ItemStack carried = p.containerMenu.getCarried();
        if (carried.is(FateItems.EA)) {
            ItemStack key = keyOf(p.level(), carried);
            p.containerMenu.setCarried(ItemStack.EMPTY);
            if (!p.getInventory().add(key) && p.level() instanceof ServerLevel sl) p.spawnAtLocation(sl, key);
        }
    }

    public static void dissipateAll(Player p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); ++i) {
            ItemStack s = inv.getItem(i);
            if (s.is(FateItems.EA)) dissipate(p, s);
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !Kings.isHero(player)) return InteractionResult.PASS;
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return KingItem.USE_DURATION;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BOW;
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (level instanceof ServerLevel server) chargeTick(server, user, KingItem.held(remaining));
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
        if (!(level instanceof ServerLevel)) return true;
        int held = KingItem.held(remaining);
        if (held >= KingRules.EA_CHARGE) {
            release(user, held, null);
            dissipate(user, stack);
        } else {
            VoicePlayer.stop(user);
        }
        return true;
    }

    /** The three cylinders spin faster, the air is dragged in, plants and snow are torn up, the ground cracks. */
    public static void chargeTick(ServerLevel level, LivingEntity user, int held) {
        Vec3 c = user.position();
        if (held == 1) {
            VoicePlayer.say(user, Voice.GIL_EA_CHANT);
            Instinct.announce(level, user, "fatekings.warn.ea", KingRules.EA_CHARGE + 20);
        }
        float t = Math.min(1.0f, held / (float)KingRules.EA_CHARGE);
        int n = 2 + (int)(t * 8);
        for (int i = 0; i < n; ++i) {
            double a = (held * 0.45 + i * Math.PI * 2 / n) % (Math.PI * 2);
            double r = 4.5 - t * 2.5 + level.getRandom().nextDouble();
            Fx.particles(level, Fx.dust(i % 2 == 0 ? 0xB0101A : 0x160808, 1.6f), c.x + Math.cos(a) * r, c.y + 0.4 + level.getRandom().nextDouble() * 2.0,
                c.z + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
        }
        if (held % 10 == 0) {
            level.playSound(null, c.x, c.y, c.z, SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 1.2f, 0.5f + t * 1.3f);
            BlockState below = level.getBlockState(user.blockPosition().below());
            if (!below.isAir()) {
                Fx.particles(level, new BlockParticleOption(ParticleTypes.BLOCK, below), c.x, c.y + 0.1, c.z, 20 + (int)(t * 30), 1.5 + t * 2, 0.05, 1.5 + t * 2, 0.1);
            }
            if (Terrain.enabled()) tearPlants(level, user.blockPosition(), 3 + (int)(t * 4));
        }
    }

    private static void tearPlants(ServerLevel level, BlockPos centre, int r) {
        for (BlockPos p : BlockPos.betweenClosed(centre.offset(-r, -1, -r), centre.offset(r, 2, r))) {
            BlockState s = level.getBlockState(p);
            if (s.is(BlockTags.REPLACEABLE_BY_TREES) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SNOW) && !s.isSolidRender()) {
                if (level.getRandom().nextInt(3) == 0) level.destroyBlock(p, false);
            }
        }
    }

    /** Enuma Elish from {@code caster} (at {@code target} for NPCs, along the view for players). */
    public static void release(LivingEntity caster, int held, LivingEntity target) {
        if (!(caster.level() instanceof ServerLevel level)) return;
        Vec3 origin = caster.getEyePosition().add(caster.getViewVector(1.0f).scale(1.5)).add(0.0, -0.3, 0.0);
        Vec3 dir = target != null ? Aim.centre(target).subtract(origin).normalize() : caster.getViewVector(1.0f);
        EnumaElishEntity.fire(level, caster, origin, dir, KingRules.eaWidth(held), KingRules.EA_RANGE);
        KingState s = Kings.of(caster);
        long now = level.getGameTime();
        s.reorgUntil = now + KingRules.TREASURY_REORG;
        s.dirty = true;
        GateOfBabylon.cancelVolley(caster);
        VoicePlayer.stop(caster);
        VoicePlayer.say(caster, Voice.GIL_EA_RELEASE);
        Fx.event(level, Fx.SHAKE, caster, caster.position(), 50, 1.3f, 160.0);
        Fx.event(level, Fx.FLASH, caster, caster.position(), 8, 0.6f, 96.0);
        Fx.event(level, Fx.SKY_DIM, caster, caster.position(), 60, 0.35f, 160.0);
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        // Wind pressure off the blunt drill.
        Vec3 push = target.position().subtract(attacker.position()).normalize().scale(1.2);
        target.push(push.x, 0.35, push.z);
        target.needsSync = true;
        if (attacker.level() instanceof ServerLevel level) {
            Fx.particles(level, Fx.dust(0xB0101A, 1.4f), target.getX(), target.getY() + 1.0, target.getZ(), 12, 0.5, 0.5, 0.5, 0.0);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.fatekings.ea.desc").withStyle(ChatFormatting.RED));
        builder.accept(Component.translatable("item.fatekings.ea.use").withStyle(ChatFormatting.GRAY));
    }
}
