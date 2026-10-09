package cn.blockforge.fatekings.entity;

import cn.blockforge.fatekings.archer.ArcherRules;
import cn.blockforge.fatekings.archer.Arsenal;
import cn.blockforge.fatekings.combat.Fx;
import cn.blockforge.fatekings.combat.Sides;
import cn.blockforge.fatekings.combat.Targets;
import cn.blockforge.fatekings.compat.JjkCompat;
import cn.blockforge.fatekings.king.Kings;
import cn.blockforge.fatekings.net.FateNet;
import cn.blockforge.fatekings.npc.KingNpcEntity;
import cn.blockforge.fatekings.registry.FateEntities;
import cn.blockforge.fatekings.voice.Voice;
import cn.blockforge.fatekings.voice.VoicePlayer;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Unlimited Blade Works, the reality marble: from where EMIYA stood a ring of fire spreads 34 blocks
 * and inside it the world is a wasteland under an orange sky, giant gears turning overhead, swords
 * planted as far as the eye can see (all of it drawn, no block is touched). For 30 s every enemy in
 * it is hunted by swords rising from the ground (they pierce Infinity), cannot walk out through its
 * wall, and the Gate of Babylon's treasures are met in the air by projected blades. The Gojo x
 * Sukuna mod counts it as a domain: when it meets one, each is cut to 20 s (the first 2 s more) and
 * neither touches the other's caster. Enuma Elish tears it apart.
 */
public class UbwEntity extends Entity {
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(UbwEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(UbwEntity.class, EntityDataSerializers.INT);
    /** Life tick at which the close begins. */
    private static final EntityDataAccessor<Integer> CLOSE_AT = SynchedEntityData.defineId(UbwEntity.class, EntityDataSerializers.INT);
    private static final List<EntityDataAccessor<ItemStack>> DISPLAY = new ArrayList<>();
    public static final int DISPLAY_SLOTS = 6;

    static {
        for (int i = 0; i < DISPLAY_SLOTS; ++i) DISPLAY.add(SynchedEntityData.defineId(UbwEntity.class, EntityDataSerializers.ITEM_STACK));
    }

    private static final Map<UUID, UbwEntity> ACTIVE = new ConcurrentHashMap<>();

    private UUID ownerId;
    private final Set<UUID> trapped = new HashSet<>();
    private final IntOpenHashSet rolled = new IntOpenHashSet();
    private List<ItemStack> weapons = List.of();
    private int weaponTurn;
    private long openedAt;
    private boolean clashed;
    private boolean finale;

    public UbwEntity(EntityType<? extends UbwEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** Unfolds the marble around the caster ({@code radius}: 34; game tests use smaller ones). */
    public static UbwEntity open(ServerLevel level, LivingEntity owner, double radius) {
        UbwEntity old = ACTIVE.get(owner.getUUID());
        if (old != null && !old.isRemoved()) old.collapse(null);
        UbwEntity u = new UbwEntity(FateEntities.UBW, level);
        u.ownerId = owner.getUUID();
        u.snapTo(owner.getX(), owner.getY(), owner.getZ(), 0.0f, 0.0f);
        u.entityData.set(RADIUS, (float)radius);
        u.entityData.set(CLOSE_AT, ArcherRules.UBW_UNFOLD + ArcherRules.UBW_TIME);
        u.weapons = Arsenal.display(owner, 24);
        for (int i = 0; i < DISPLAY_SLOTS && i < u.weapons.size(); ++i) u.entityData.set(DISPLAY.get(i), u.weapons.get(i).copyWithCount(1));
        u.openedAt = level.getGameTime();
        level.addFreshEntity(u);
        ACTIVE.put(owner.getUUID(), u);
        VoicePlayer.say(owner, Voice.EMIYA_UBW_RELEASE);
        level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 3.0f, 0.4f);
        level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 3.0f, 0.5f);
        Fx.event(level, Fx.SHAKE, owner, owner.position(), 30, 0.7f, radius + 16.0);
        return u;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(RADIUS, (float)ArcherRules.UBW_RADIUS);
        builder.define(LIFE, 0);
        builder.define(CLOSE_AT, ArcherRules.UBW_UNFOLD + ArcherRules.UBW_TIME);
        for (EntityDataAccessor<ItemStack> d : DISPLAY) builder.define(d, new ItemStack(Items.IRON_SWORD));
    }

    public float radius() {
        return this.entityData.get(RADIUS);
    }

    public int life() {
        return this.entityData.get(LIFE);
    }

    public int closeAt() {
        return this.entityData.get(CLOSE_AT);
    }

    public ItemStack display(int i) {
        return this.entityData.get(DISPLAY.get(i));
    }

    /** 0..1 as the ring of fire spreads, then 1, then 1..0 as the marble closes. */
    /** How far the marble reaches as drawn (the fire running out to its wall); 0 once it has faded. */
    public float visualRadius(float partial) {
        if (openness(partial) <= 0.05f) return 0.0f;
        return radius() * Math.min(1.0f, (life() + partial) / ArcherRules.UBW_UNFOLD);
    }

    public float openness(float partial) {
        float t = life() + partial;
        float open = Math.min(1.0f, t / ArcherRules.UBW_UNFOLD);
        float close = (t - closeAt()) / ArcherRules.UBW_CLOSE;
        return close > 0.0f ? Math.max(0.0f, open * (1.0f - close)) : open;
    }

    public boolean active() {
        int life = life();
        return life >= ArcherRules.UBW_UNFOLD && life < closeAt();
    }

    public UUID ownerId() {
        return this.ownerId;
    }

    public boolean contains(Vec3 p) {
        double r = radius();
        return p.distanceToSqr(this.position()) <= r * r;
    }

    // ---- Queries for the rest of the mod ----

    /** Whether {@code e} stands inside his own active reality marble. */
    public static boolean inside(LivingEntity e) {
        UbwEntity u = ACTIVE.get(e.getUUID());
        return u != null && !u.isRemoved() && u.active() && u.contains(e.position());
    }

    /** Ticks his marble has left, 0 if none. */
    public static int leftOf(LivingEntity e) {
        UbwEntity u = ACTIVE.get(e.getUUID());
        if (u == null || u.isRemoved()) return 0;
        return Math.max(0, u.closeAt() - u.life());
    }

    public static boolean activeFor(LivingEntity e) {
        UbwEntity u = ACTIVE.get(e.getUUID());
        return u != null && !u.isRemoved();
    }

    public static void collapseOf(LivingEntity e) {
        UbwEntity u = ACTIVE.get(e.getUUID());
        if (u != null && !u.isRemoved()) u.collapse(null);
    }

    /** Another's marble within {@code range} of {@code e} (an enemy domain, as far as Gilgamesh cares). */
    public static boolean foreignNear(LivingEntity e, double range) {
        for (UbwEntity u : ACTIVE.values()) {
            if (u.isRemoved() || u.level() != e.level() || e.getUUID().equals(u.ownerId)) continue;
            if (u.position().distanceTo(e.position()) <= u.radius() + range) return true;
        }
        return false;
    }

    /** Enuma Elish crossing a marble's wall tears it apart. */
    public static void tearIfCrossed(ServerLevel level, Vec3 from, Vec3 to, LivingEntity caster) {
        for (UbwEntity u : ACTIVE.values()) {
            if (u.isRemoved() || u.level() != level || caster != null && caster.getUUID().equals(u.ownerId)) continue;
            double r = u.radius();
            Vec3 c = u.position();
            Vec3 seg = to.subtract(from);
            double len2 = Math.max(1.0E-6, seg.lengthSqr());
            double t = Math.max(0.0, Math.min(1.0, c.subtract(from).dot(seg) / len2));
            if (c.distanceToSqr(from.add(seg.scale(t))) <= r * r) u.collapse("fatekings.hint.ubw_torn");
        }
    }

    public static void clearAll() {
        ACTIVE.clear();
    }

    // ---- The marble ----

    private LivingEntity owner(ServerLevel level) {
        return this.ownerId != null && level.getEntity(this.ownerId) instanceof LivingEntity l && l.isAlive() ? l : null;
    }

    /** Begins the close (with a message to everyone inside, if a reason is given). */
    public void collapse(String reasonKey) {
        if (!(this.level() instanceof ServerLevel level)) return;
        int life = life();
        if (closeAt() > life) this.entityData.set(CLOSE_AT, life);
        JjkCompat.unregisterDomain(this);
        if (reasonKey != null) {
            Component msg = Component.translatable(reasonKey).withStyle(ChatFormatting.GOLD);
            for (ServerPlayer p : level.players()) if (contains(p.position())) FateNet.actionBar(p, msg);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 3.0f, 0.5f);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) return;
        LivingEntity owner = owner(level);
        int life = life() + 1;
        this.entityData.set(LIFE, life);
        if (owner == null || !Kings.isArcher(owner) || owner.position().distanceTo(this.position()) > radius() + 4.0) {
            if (closeAt() > life) collapse(null);
        }
        if (life >= closeAt() + ArcherRules.UBW_CLOSE) {
            this.discard();
            return;
        }
        if (owner == null) return;
        if (life == 1) JjkCompat.registerDomain(owner, this, this.position(), radius(), true);
        if (!this.clashed && life % 5 == 0) clash(level, owner, life);
        if (life == ArcherRules.UBW_UNFOLD) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 2.0f, 0.5f);
        }
        if (life % 10 == 0 || life == ArcherRules.UBW_UNFOLD) refreshTrapped(level, owner);
        if (!active()) return;
        holdTheWall(level);
        int sinceOpen = life - ArcherRules.UBW_UNFOLD;
        if (sinceOpen % ArcherRules.UBW_VOLLEY_INTERVAL == 0) volley(level, owner, 1);
        int left = closeAt() - life;
        if (!this.finale && left == 30) {
            this.finale = true;
            VoicePlayer.say(owner, Voice.EMIYA_FULL_OPEN);
            volley(level, owner, 3);
        }
        interceptTreasures(level, owner);
    }

    /** The other mod's domain clash: when it pairs us with a domain, we keep the same rule on our side. */
    private void clash(ServerLevel level, LivingEntity owner, int life) {
        UUID rival = JjkCompat.domainRival(this);
        if (rival == null) return;
        this.clashed = true;
        long rivalOpened = JjkCompat.domainOpenedAt(rival);
        boolean first = rivalOpened >= 0L && this.openedAt < rivalOpened;
        int remaining = Math.max(0, closeAt() - life);
        this.entityData.set(CLOSE_AT, life + ArcherRules.ubwAfterClash(remaining, first));
        String key = first ? "fatekings.hint.ubw_clash_first" : "fatekings.hint.ubw_clash";
        if (owner instanceof ServerPlayer sp) FateNet.actionBar(sp, Component.translatable(key).withStyle(ChatFormatting.GOLD));
    }

    private boolean enemy(LivingEntity owner, LivingEntity e) {
        if (e == owner || !e.isAlive() || e.isSpectator() || e instanceof Player p && p.isCreative()) return false;
        if (owner instanceof KingNpcEntity npc) return npc.canHarm(e) || npc.getTarget() == e || e instanceof net.minecraft.world.entity.Mob m && m.getTarget() == owner;
        if (Targets.hostileTo(owner, e)) return true;
        // Players: whoever fights him (struck him or was struck by him lately).
        return e instanceof Player && (owner.getLastHurtByMob() == e || owner.getLastHurtMob() == e);
    }

    private void refreshTrapped(ServerLevel level, LivingEntity owner) {
        double r = radius();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r), e -> contains(e.position()))) {
            if (enemy(owner, e)) this.trapped.add(e.getUUID());
        }
    }

    /** Enemies inside cannot walk out: pushed back at the wall, put back if they got through. */
    private void holdTheWall(ServerLevel level) {
        double r = radius();
        Vec3 c = this.position();
        for (UUID id : this.trapped) {
            if (!(level.getEntity(id) instanceof LivingEntity e) || !e.isAlive() || e.isSpectator()) continue;
            Vec3 off = e.position().subtract(c);
            double d = off.length();
            if (d < r - 1.5 || d < 1.0E-3) continue;
            Vec3 out = off.scale(1.0 / d);
            if (d > r - 0.5) {
                Vec3 back = c.add(out.scale(r - 2.0));
                e.teleportTo(back.x, Math.max(back.y, e.getY() - 1.0), back.z);
                if (e instanceof ServerPlayer sp) FateNet.actionBar(sp, Component.translatable("fatekings.hint.ubw_wall").withStyle(ChatFormatting.RED));
            }
            Vec3 v = e.getDeltaMovement();
            double outward = v.dot(out);
            if (outward > 0.0) v = v.subtract(out.scale(outward));
            e.setDeltaMovement(v.subtract(out.scale(0.3)));
            e.needsSync = true;
            if (e instanceof ServerPlayer sp) sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));
        }
    }

    /** Swords rise from the hill and fly at every enemy inside: {@code each} per foe (two at the worthy). */
    private void volley(ServerLevel level, LivingEntity owner, int each) {
        UUID rival = JjkCompat.domainRival(this);
        List<LivingEntity> foes = new ArrayList<>();
        for (UUID id : this.trapped) {
            if (level.getEntity(id) instanceof LivingEntity e && e.isAlive() && contains(e.position()) && !e.getUUID().equals(rival)) foes.add(e);
        }
        foes.sort(Comparator.comparingDouble(e -> e.distanceToSqr(owner)));
        int swords = 0;
        for (int i = 0; i < foes.size() && i < ArcherRules.UBW_MAX_TARGETS; ++i) {
            LivingEntity foe = foes.get(i);
            int n = each * (Sides.worthy(foe) ? 2 : 1);
            for (int k = 0; k < n && swords < ArcherRules.UBW_MAX_SWORDS_PER_VOLLEY * each; ++k, ++swords) {
                UbwSwordEntity.rise(level, owner, groundNear(level, foe), foe, nextWeapon());
            }
        }
        if (swords > 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.PLAYERS, 2.0f, 1.4f);
    }

    private ItemStack nextWeapon() {
        if (this.weapons.isEmpty()) return new ItemStack(Items.IRON_SWORD);
        return this.weapons.get(this.weaponTurn++ % this.weapons.size());
    }

    private Vec3 groundNear(ServerLevel level, LivingEntity foe) {
        double a = this.random.nextDouble() * Math.PI * 2.0;
        double d = 5.0 + this.random.nextDouble() * 6.0;
        double x = foe.getX() + Math.cos(a) * d, z = foe.getZ() + Math.sin(a) * d;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int)Math.floor(x), (int)Math.floor(z));
        double y = Math.abs(top - foe.getY()) > 12.0 ? foe.getY() : top;
        return new Vec3(x, y, z);
    }

    /**
     * Whether a treasure of the Gate of Babylon where it is now flies inside an open marble (not its
     * own caster's): there it never lands, the hill's blades meet every one.
     */
    public static boolean shields(Entity treasure) {
        if (!(treasure.level() instanceof ServerLevel)) return false;
        Entity caster = treasure instanceof net.minecraft.world.entity.projectile.Projectile p ? p.getOwner() : null;
        for (UbwEntity u : ACTIVE.values()) {
            if (u.isRemoved() || u.level() != treasure.level() || !u.active()) continue;
            if (caster != null && caster.getUUID().equals(u.ownerId)) continue;
            if (u.contains(treasure.position())) return true;
        }
        return false;
    }

    /** A treasure struck from the air: a clang, sparks and embers where it broke. */
    public static void parried(ServerLevel level, Vec3 at) {
        Fx.particles(level, ParticleTypes.CRIT, at, 10, 0.25, 0.4);
        Fx.particles(level, Fx.dust(Fx.EMBER, 1.1f), at, 8, 0.25, 0.05);
        Fx.particles(level, Fx.dust(Fx.GOLD, 1.0f), at, 6, 0.2, 0.05);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5f, 1.6f + level.getRandom().nextFloat() * 0.3f);
    }

    /** "The treasury cannot keep up with the forge": treasures fired inside are met by blades. */
    private void interceptTreasures(ServerLevel level, LivingEntity owner) {
        double r = radius();
        int flying = 0;
        for (UbwSwordEntity s : level.getEntitiesOfClass(UbwSwordEntity.class, getBoundingBox().inflate(r), UbwSwordEntity::intercepting)) ++flying;
        for (TreasureProjectile t : level.getEntitiesOfClass(TreasureProjectile.class, getBoundingBox().inflate(r), e -> e.isAlive() && contains(e.position()))) {
            if (t.getOwner() == owner || !this.rolled.add(t.getId())) continue;
            // Every one is met; the blade in the air is the sight of it (a treasure that gets to
            // anyone first shatters on its own, see shields).
            if (flying >= ArcherRules.UBW_MAX_INTERCEPTORS) continue;
            Vec3 from = t.position().add(t.getDeltaMovement().normalize().scale(4.0)).add(0.0, -2.0, 0.0);
            UbwSwordEntity.intercept(level, owner, from, t, nextWeapon());
            ++flying;
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        JjkCompat.unregisterDomain(this);
        if (this.ownerId != null) ACTIVE.remove(this.ownerId, this);
        super.remove(reason);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 256.0 * 256.0;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
    }
}
