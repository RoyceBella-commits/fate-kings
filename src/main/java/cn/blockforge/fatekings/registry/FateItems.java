package cn.blockforge.fatekings.registry;

import cn.blockforge.fatekings.FateKings;
import cn.blockforge.fatekings.hero.BabIluItem;
import cn.blockforge.fatekings.hero.EaItem;
import cn.blockforge.fatekings.hero.ElixirItem;
import cn.blockforge.fatekings.hero.EnkiduItem;
import cn.blockforge.fatekings.hero.GateOfBabylonItem;
import cn.blockforge.fatekings.hero.GrailMudItem;
import cn.blockforge.fatekings.hero.VimanaItem;
import cn.blockforge.fatekings.knight.ExcaliburItem;
import cn.blockforge.fatekings.knight.WarhorseItem;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Unit;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;

public final class FateItems {
    /** Nothing repairs the regalia (they never wear out anyway). */
    public static final TagKey<Item> NO_REPAIR = TagKey.create(Registries.ITEM, FateKings.id("no_repair"));
    public static final ResourceKey<EquipmentAsset> GOLDEN_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, FateKings.id("golden_regalia"));
    public static final ResourceKey<EquipmentAsset> KNIGHT_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, FateKings.id("knight_regalia"));
    public static final ResourceKey<EquipmentAsset> BARDING_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, FateKings.id("knight_barding"));
    private static final Map<ArmorType, Integer> DIAMOND_DEFENSE = Map.of(ArmorType.BOOTS, 3, ArmorType.LEGGINGS, 6, ArmorType.CHESTPLATE, 8, ArmorType.HELMET, 3, ArmorType.BODY, 11);
    public static final ArmorMaterial GOLDEN = new ArmorMaterial(37, DIAMOND_DEFENSE, 0, SoundEvents.ARMOR_EQUIP_GOLD, 2.0f, 0.0f, NO_REPAIR, GOLDEN_ASSET);
    public static final ArmorMaterial KNIGHT = new ArmorMaterial(37, DIAMOND_DEFENSE, 0, SoundEvents.ARMOR_EQUIP_IRON, 2.0f, 0.0f, NO_REPAIR, KNIGHT_ASSET);
    public static final ArmorMaterial BARDING = new ArmorMaterial(37, DIAMOND_DEFENSE, 0, SoundEvents.ARMOR_EQUIP_IRON, 2.0f, 0.0f, NO_REPAIR, BARDING_ASSET);

    // ---- King of Heroes ----
    public static final Item GOLDEN_CROWN = armor("golden_crown", GOLDEN, ArmorType.HELMET);
    public static final Item GOLDEN_CHESTPLATE = armor("golden_chestplate", GOLDEN, ArmorType.CHESTPLATE);
    public static final Item GOLDEN_GREAVES = armor("golden_greaves", GOLDEN, ArmorType.LEGGINGS);
    public static final Item GOLDEN_SABATONS = armor("golden_sabatons", GOLDEN, ArmorType.BOOTS);
    public static final Item GATE_OF_BABYLON = register("gate_of_babylon", GateOfBabylonItem::new, p -> p.stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    public static final Item BAB_ILU = register("bab_ilu", BabIluItem::new, p -> p.stacksTo(1).rarity(Rarity.EPIC).fireResistant()
        .sword(net.minecraft.world.item.ToolMaterial.IRON, 3.0f, -2.4f).component(DataComponents.UNBREAKABLE, Unit.INSTANCE));
    public static final Item EA = register("ea", EaItem::new, p -> p.stacksTo(1).rarity(Rarity.EPIC).fireResistant()
        .attributes(EaItem.attributes()).component(DataComponents.UNBREAKABLE, Unit.INSTANCE));
    public static final Item ENKIDU = register("enkidu", EnkiduItem::new, p -> p.stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    public static final Item VIMANA = register("vimana", VimanaItem::new, p -> p.stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    public static final Item TREASURY_ELIXIR = register("treasury_elixir", ElixirItem::new, p -> p.stacksTo(1).rarity(Rarity.RARE));

    // ---- King of Knights ----
    public static final Item KNIGHT_RIBBON = armor("knight_ribbon", KNIGHT, ArmorType.HELMET);
    public static final Item KNIGHT_BREASTPLATE = armor("knight_breastplate", KNIGHT, ArmorType.CHESTPLATE);
    public static final Item KNIGHT_SKIRT = armor("knight_skirt", KNIGHT, ArmorType.LEGGINGS);
    public static final Item KNIGHT_BOOTS = armor("knight_boots", KNIGHT, ArmorType.BOOTS);
    public static final Item EXCALIBUR = register("excalibur", ExcaliburItem::new, p -> p.stacksTo(1).rarity(Rarity.EPIC).fireResistant()
        .sword(net.minecraft.world.item.ToolMaterial.DIAMOND, 3.0f, -2.4f).component(DataComponents.UNBREAKABLE, Unit.INSTANCE)
        .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false));
    public static final Item WARHORSE = register("warhorse", WarhorseItem::new, p -> p.stacksTo(1).rarity(Rarity.RARE));
    public static final Item KNIGHT_BARDING = register("knight_barding", Item::new, p -> p.stacksTo(1).rarity(Rarity.RARE)
        .horseArmor(BARDING).component(DataComponents.UNBREAKABLE, Unit.INSTANCE));

    // ---- NPCs and the admin item ----
    public static final Item GILGAMESH_SPAWN_EGG = register("gilgamesh_spawn_egg", SpawnEggItem::new, p -> p.spawnEgg(FateEntities.GILGAMESH));
    public static final Item ARTORIA_SPAWN_EGG = register("artoria_spawn_egg", SpawnEggItem::new, p -> p.spawnEgg(FateEntities.ARTORIA));
    public static final Item GRAIL_MUD = register("grail_mud", GrailMudItem::new, p -> p.stacksTo(1).rarity(Rarity.EPIC));

    public static final List<Item> HERO_SET = List.of(GOLDEN_CROWN, GOLDEN_CHESTPLATE, GOLDEN_GREAVES, GOLDEN_SABATONS);
    public static final List<Item> KNIGHT_SET = List.of(KNIGHT_RIBBON, KNIGHT_BREASTPLATE, KNIGHT_SKIRT, KNIGHT_BOOTS);

    public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, FateKings.id("main"),
        FabricCreativeModeTab.builder()
            .title(Component.translatable("itemGroup.fatekings.main"))
            .icon(() -> new ItemStack(EXCALIBUR))
            .displayItems((ctx, entries) -> {
                for (Item item : List.of(GOLDEN_CROWN, GOLDEN_CHESTPLATE, GOLDEN_GREAVES, GOLDEN_SABATONS, GATE_OF_BABYLON, BAB_ILU,
                        ENKIDU, VIMANA, TREASURY_ELIXIR, KNIGHT_RIBBON, KNIGHT_BREASTPLATE, KNIGHT_SKIRT, KNIGHT_BOOTS, EXCALIBUR,
                        WARHORSE, KNIGHT_BARDING, GILGAMESH_SPAWN_EGG, ARTORIA_SPAWN_EGG, GRAIL_MUD)) {
                    entries.accept(new ItemStack(item));
                }
            }).build());

    private FateItems() {
    }

    private static Item armor(String name, ArmorMaterial material, ArmorType type) {
        return register(name, Item::new, p -> p.stacksTo(1).rarity(Rarity.EPIC).fireResistant()
            .attributes(material.createAttributes(type))
            .component(DataComponents.EQUIPPABLE, Equippable.builder(type.getSlot()).setEquipSound(material.equipSound()).setAsset(material.assetId()).build())
            .component(DataComponents.UNBREAKABLE, Unit.INSTANCE));
    }

    private static Item register(String name, Function<Item.Properties, Item> factory, Function<Item.Properties, Item.Properties> props) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, FateKings.id(name));
        Item item = factory.apply(props.apply(new Item.Properties().setId(key)));
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    public static boolean heroPiece(ItemStack stack) {
        return HERO_SET.contains(stack.getItem());
    }

    public static boolean knightPiece(ItemStack stack) {
        return KNIGHT_SET.contains(stack.getItem());
    }

    public static void init() {
    }
}
