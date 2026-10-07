package cn.blockforge.fatekings.archer;

import cn.blockforge.fatekings.FateKings;
import cn.blockforge.fatekings.npc.EmiyaEntity;
import cn.blockforge.fatekings.registry.FateItems;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Hill of Swords: every weapon EMIYA has analysed (by looking at it, or by being struck with it),
 * at most 27, the least used forgotten first. Kept across death; the NPC keeps his own in his save.
 */
public final class Arsenal {
    public record Entry(ItemStack stack, long used) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.CODEC.fieldOf("stack").forGetter(Entry::stack),
            Codec.LONG.optionalFieldOf("used", 0L).forGetter(Entry::used)
        ).apply(i, Entry::new));
    }

    public static final class Data {
        public static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
            Entry.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(d -> d.entries),
            Codec.INT.optionalFieldOf("selected", -1).forGetter(d -> d.selected)
        ).apply(i, Data::new));

        public final List<Entry> entries;
        public int selected;
        /** Changed since the owner's screen was last sent. */
        public transient boolean dirty = true;

        public Data() {
            this(List.of(), -1);
        }

        private Data(List<Entry> entries, int selected) {
            this.entries = new ArrayList<>(entries);
            this.selected = selected;
        }

        public ItemStack selectedStack() {
            return this.selected >= 0 && this.selected < this.entries.size() ? this.entries.get(this.selected).stack() : ItemStack.EMPTY;
        }
    }

    public static final AttachmentType<Data> ARSENAL = AttachmentRegistry.<Data>builder()
        .persistent(Data.CODEC).copyOnDeath().initializer(Data::new).buildAndRegister(FateKings.id("arsenal"));

    private Arsenal() {
    }

    public static Data of(LivingEntity e) {
        if (e instanceof Player p) return p.getAttachedOrCreate(ARSENAL);
        if (e instanceof EmiyaEntity emiya) return emiya.arsenal();
        return null;
    }

    /**
     * Records a weapon (already checked as copyable) and selects it. Returns whether something was
     * forgotten to make room.
     */
    public static boolean record(Data data, ItemStack weapon, long now) {
        ItemStack clean = Projection.sanitize(weapon);
        if (clean.isEmpty()) return false;
        for (int i = 0; i < data.entries.size(); ++i) {
            if (ItemStack.isSameItemSameComponents(data.entries.get(i).stack(), clean)) {
                data.entries.set(i, new Entry(data.entries.get(i).stack(), now));
                data.selected = i;
                data.dirty = true;
                return false;
            }
        }
        boolean forgot = false;
        if (data.entries.size() >= ArcherRules.ARSENAL_CAP) {
            int oldest = 0;
            for (int i = 1; i < data.entries.size(); ++i) {
                if (data.entries.get(i).used() < data.entries.get(oldest).used()) oldest = i;
            }
            data.entries.remove(oldest);
            if (data.selected >= oldest) data.selected = Math.max(-1, data.selected - 1);
            forgot = true;
        }
        data.entries.add(new Entry(clean, now));
        data.selected = data.entries.size() - 1;
        data.dirty = true;
        return forgot;
    }

    public static void touch(Data data, int index, long now) {
        if (index < 0 || index >= data.entries.size()) return;
        data.entries.set(index, new Entry(data.entries.get(index).stack(), now));
        data.selected = index;
        data.dirty = true;
    }

    public static void forget(Data data, int index) {
        if (index < 0 || index >= data.entries.size()) return;
        data.entries.remove(index);
        if (data.selected == index) data.selected = -1;
        else if (data.selected > index) --data.selected;
        data.dirty = true;
    }

    /** What every EMIYA knows by heart: his twin blades and the plain arms of the world. */
    public static List<ItemStack> defaults() {
        return List.of(new ItemStack(FateItems.KANSHOU), new ItemStack(FateItems.BAKUYA), new ItemStack(Items.IRON_SWORD),
            new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.NETHERITE_SWORD), new ItemStack(Items.NETHERITE_AXE),
            new ItemStack(Items.TRIDENT));
    }

    /** The swords shown and fired in his reality marble: what he has seen, else the defaults. */
    public static List<ItemStack> display(LivingEntity e, int n) {
        Data data = of(e);
        List<ItemStack> out = new ArrayList<>();
        if (data != null) {
            for (int i = data.entries.size() - 1; i >= 0 && out.size() < n; --i) out.add(data.entries.get(i).stack());
        }
        for (ItemStack s : defaults()) {
            if (out.size() >= n) break;
            out.add(s);
        }
        return out;
    }
}
