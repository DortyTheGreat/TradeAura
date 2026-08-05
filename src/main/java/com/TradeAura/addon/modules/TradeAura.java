package com.TradeAura.addon.modules;

import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket;
import net.minecraft.network.packet.s2c.play.SetTradeOffersS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.Merchant;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.GameMode;
import org.apache.commons.lang3.reflect.FieldUtils;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.village.VillagerData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TradeAura extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAura = settings.createGroup("Aura");
    private final SettingGroup sgRender = settings.createGroup("Render");

    // --- VILLAGER TRADES HARDCODE ---
    private static final Map<Item, Set<String>> VILLAGER_BUYS = new HashMap<>();
    private static final Map<Item, Set<String>> VILLAGER_SELLS = new HashMap<>();

    static {
        // ==================Selling (villager buys from us)==================
        // Farmer
        VILLAGER_BUYS.put(Items.WHEAT, Set.of("farmer"));
        VILLAGER_BUYS.put(Items.POTATO, Set.of("farmer"));
        VILLAGER_BUYS.put(Items.CARROT, Set.of("farmer"));
        VILLAGER_BUYS.put(Items.BEETROOT, Set.of("farmer"));
        VILLAGER_BUYS.put(Items.PUMPKIN, Set.of("farmer"));
        VILLAGER_BUYS.put(Items.MELON_SLICE, Set.of("farmer"));
        
        // Librarian & Cartographer
        VILLAGER_BUYS.put(Items.PAPER, Set.of("cartographer", "librarian"));
        VILLAGER_BUYS.put(Items.GLASS_PANE, Set.of("cartographer"));
        VILLAGER_BUYS.put(Items.COMPASS, Set.of("cartographer"));
        VILLAGER_BUYS.put(Items.BOOK, Set.of("librarian"));
        VILLAGER_BUYS.put(Items.INK_SAC, Set.of("librarian"));
        
        // Fletcher & Fisherman
        VILLAGER_BUYS.put(Items.STRING, Set.of("fletcher", "fisherman"));
        VILLAGER_BUYS.put(Items.FEATHER, Set.of("fletcher"));
        VILLAGER_BUYS.put(Items.STICK, Set.of("fletcher"));
        VILLAGER_BUYS.put(Items.FLINT, Set.of("fletcher", "toolsmith", "weaponsmith"));
        VILLAGER_BUYS.put(Items.COD, Set.of("fisherman"));
        VILLAGER_BUYS.put(Items.SALMON, Set.of("fisherman"));
        
        // Armorer, Weaponsmith, Toolsmith
        VILLAGER_BUYS.put(Items.RAW_IRON, Set.of("armorer", "weaponsmith", "toolsmith"));
        VILLAGER_BUYS.put(Items.IRON_INGOT, Set.of("armorer", "weaponsmith", "toolsmith"));
        VILLAGER_BUYS.put(Items.RAW_GOLD, Set.of("armorer"));
        VILLAGER_BUYS.put(Items.GOLD_INGOT, Set.of("cleric"));
        VILLAGER_BUYS.put(Items.RAW_COPPER, Set.of("armorer"));
        VILLAGER_BUYS.put(Items.COAL, Set.of("armorer", "weaponsmith", "toolsmith", "butcher", "fisherman"));
        VILLAGER_BUYS.put(Items.DIAMOND, Set.of("armorer", "weaponsmith", "toolsmith"));
        VILLAGER_BUYS.put(Items.LAPIS_LAZULI, Set.of("cleric", "armorer"));
        VILLAGER_BUYS.put(Items.REDSTONE, Set.of("cleric"));
        
        // Cleric
        VILLAGER_BUYS.put(Items.ROTTEN_FLESH, Set.of("cleric"));
        VILLAGER_BUYS.put(Items.RABBIT_FOOT, Set.of("cleric"));
        VILLAGER_BUYS.put(Items.GLASS_BOTTLE, Set.of("cleric"));
        VILLAGER_BUYS.put(Items.NETHER_WART, Set.of("cleric"));
        
        // Leatherworker
        VILLAGER_BUYS.put(Items.LEATHER, Set.of("leatherworker"));
        VILLAGER_BUYS.put(Items.RABBIT_HIDE, Set.of("leatherworker"));
        VILLAGER_BUYS.put(Items.TURTLE_SCUTE, Set.of("leatherworker"));
        
        // Butcher
        VILLAGER_BUYS.put(Items.MUTTON, Set.of("butcher"));
        VILLAGER_BUYS.put(Items.PORKCHOP, Set.of("butcher"));
        VILLAGER_BUYS.put(Items.CHICKEN, Set.of("butcher"));
        VILLAGER_BUYS.put(Items.BEEF, Set.of("butcher"));
        
        // Mason (Stone Mason)
        VILLAGER_BUYS.put(Items.CLAY_BALL, Set.of("mason"));
        VILLAGER_BUYS.put(Items.STONE, Set.of("mason"));
        VILLAGER_BUYS.put(Items.GRANITE, Set.of("mason"));
        VILLAGER_BUYS.put(Items.ANDESITE, Set.of("mason"));
        VILLAGER_BUYS.put(Items.DIORITE, Set.of("mason"));
        VILLAGER_BUYS.put(Items.NETHERRACK, Set.of("mason"));
        VILLAGER_BUYS.put(Items.BLACKSTONE, Set.of("mason"));
        VILLAGER_BUYS.put(Items.END_STONE, Set.of("mason"));
        VILLAGER_BUYS.put(Items.TERRACOTTA, Set.of("mason"));

        // Shepherd
        VILLAGER_BUYS.put(Items.WHITE_WOOL, Set.of("shepherd"));
        VILLAGER_BUYS.put(Items.BLACK_WOOL, Set.of("shepherd"));
        VILLAGER_BUYS.put(Items.GRAY_WOOL, Set.of("shepherd"));
        VILLAGER_BUYS.put(Items.BROWN_WOOL, Set.of("shepherd"));
        VILLAGER_BUYS.put(Items.WHITE_DYE, Set.of("shepherd"));
        VILLAGER_BUYS.put(Items.BLACK_DYE, Set.of("shepherd"));
        VILLAGER_BUYS.put(Items.BROWN_DYE, Set.of("shepherd"));
        VILLAGER_BUYS.put(Items.BLUE_DYE, Set.of("shepherd"));

        // ==================Buying (villager sells to us)==================
        // Farmer
        VILLAGER_SELLS.put(Items.BREAD, Set.of("farmer"));
        VILLAGER_SELLS.put(Items.APPLE, Set.of("farmer"));
        VILLAGER_SELLS.put(Items.PUMPKIN_PIE, Set.of("farmer"));
        VILLAGER_SELLS.put(Items.COOKIE, Set.of("farmer"));
        VILLAGER_SELLS.put(Items.CAKE, Set.of("farmer"));
        VILLAGER_SELLS.put(Items.GOLDEN_CARROT, Set.of("farmer"));
        VILLAGER_SELLS.put(Items.GLISTERING_MELON_SLICE, Set.of("farmer"));
        VILLAGER_SELLS.put(Items.SUSPICIOUS_STEW, Set.of("farmer"));
        
        // Librarian & Cartographer
        VILLAGER_SELLS.put(Items.GLASS, Set.of("librarian"));
        VILLAGER_SELLS.put(Items.ENCHANTED_BOOK, Set.of("librarian"));
        VILLAGER_SELLS.put(Items.BOOKSHELF, Set.of("librarian", "cleric"));
        VILLAGER_SELLS.put(Items.NAME_TAG, Set.of("librarian"));
        VILLAGER_SELLS.put(Items.CLOCK, Set.of("librarian"));
        VILLAGER_SELLS.put(Items.LANTERN, Set.of("librarian"));
        VILLAGER_SELLS.put(Items.COMPASS, Set.of("librarian"));
        
        VILLAGER_SELLS.put(Items.MAP, Set.of("cartographer"));
        VILLAGER_SELLS.put(Items.FILLED_MAP, Set.of("cartographer"));
        VILLAGER_SELLS.put(Items.ITEM_FRAME, Set.of("cartographer"));
        VILLAGER_SELLS.put(Items.CARTOGRAPHY_TABLE, Set.of("cartographer"));
        VILLAGER_SELLS.put(Items.WHITE_BANNER, Set.of("cartographer", "shepherd"));
        VILLAGER_SELLS.put(Items.RED_BANNER, Set.of("cartographer", "shepherd"));
        VILLAGER_SELLS.put(Items.BLUE_BANNER, Set.of("cartographer", "shepherd"));
        
        // Fletcher & Fisherman
        VILLAGER_SELLS.put(Items.ARROW, Set.of("fletcher"));
        VILLAGER_SELLS.put(Items.BOW, Set.of("fletcher"));
        VILLAGER_SELLS.put(Items.CROSSBOW, Set.of("fletcher"));
        VILLAGER_SELLS.put(Items.FLINT_AND_STEEL, Set.of("fletcher"));
        VILLAGER_SELLS.put(Items.TIPPED_ARROW, Set.of("fletcher"));
        
        VILLAGER_SELLS.put(Items.COOKED_COD, Set.of("fisherman"));
        VILLAGER_SELLS.put(Items.COOKED_SALMON, Set.of("fisherman"));
        VILLAGER_SELLS.put(Items.FISHING_ROD, Set.of("fisherman"));
        VILLAGER_SELLS.put(Items.COD_BUCKET, Set.of("fisherman"));
        VILLAGER_SELLS.put(Items.CAMPFIRE, Set.of("fisherman"));
        
        // Armorer
        VILLAGER_SELLS.put(Items.IRON_HELMET, Set.of("armorer"));
        VILLAGER_SELLS.put(Items.IRON_CHESTPLATE, Set.of("armorer"));
        VILLAGER_SELLS.put(Items.IRON_LEGGINGS, Set.of("armorer"));
        VILLAGER_SELLS.put(Items.IRON_BOOTS, Set.of("armorer"));
        VILLAGER_SELLS.put(Items.SHIELD, Set.of("armorer"));
        VILLAGER_SELLS.put(Items.CHAINMAIL_HELMET, Set.of("armorer"));
        VILLAGER_SELLS.put(Items.CHAINMAIL_CHESTPLATE, Set.of("armorer"));
        VILLAGER_SELLS.put(Items.CHAINMAIL_LEGGINGS, Set.of("armorer"));
        VILLAGER_SELLS.put(Items.CHAINMAIL_BOOTS, Set.of("armorer"));
        VILLAGER_SELLS.put(Items.DIAMOND_HELMET, Set.of("armorer"));
        VILLAGER_SELLS.put(Items.DIAMOND_CHESTPLATE, Set.of("armorer"));
        VILLAGER_SELLS.put(Items.DIAMOND_LEGGINGS, Set.of("armorer"));
        VILLAGER_SELLS.put(Items.DIAMOND_BOOTS, Set.of("armorer"));
        VILLAGER_SELLS.put(Items.BELL, Set.of("armorer", "weaponsmith", "toolsmith"));
        
        // Weaponsmith
        VILLAGER_SELLS.put(Items.STONE_AXE, Set.of("weaponsmith", "toolsmith"));
        VILLAGER_SELLS.put(Items.STONE_SWORD, Set.of("weaponsmith"));
        VILLAGER_SELLS.put(Items.IRON_SWORD, Set.of("weaponsmith"));
        VILLAGER_SELLS.put(Items.IRON_AXE, Set.of("weaponsmith", "toolsmith"));
        VILLAGER_SELLS.put(Items.DIAMOND_SWORD, Set.of("weaponsmith"));
        VILLAGER_SELLS.put(Items.DIAMOND_AXE, Set.of("weaponsmith", "toolsmith"));
        
        // Toolsmith
        VILLAGER_SELLS.put(Items.STONE_PICKAXE, Set.of("toolsmith"));
        VILLAGER_SELLS.put(Items.STONE_SHOVEL, Set.of("toolsmith"));
        VILLAGER_SELLS.put(Items.STONE_HOE, Set.of("toolsmith"));
        VILLAGER_SELLS.put(Items.IRON_PICKAXE, Set.of("toolsmith"));
        VILLAGER_SELLS.put(Items.IRON_SHOVEL, Set.of("toolsmith"));
        VILLAGER_SELLS.put(Items.IRON_HOE, Set.of("toolsmith"));
        VILLAGER_SELLS.put(Items.DIAMOND_PICKAXE, Set.of("toolsmith"));
        VILLAGER_SELLS.put(Items.DIAMOND_SHOVEL, Set.of("toolsmith"));
        VILLAGER_SELLS.put(Items.DIAMOND_HOE, Set.of("toolsmith"));
        
        // Leatherworker
        VILLAGER_SELLS.put(Items.LEATHER_HELMET, Set.of("leatherworker"));
        VILLAGER_SELLS.put(Items.LEATHER_CHESTPLATE, Set.of("leatherworker"));
        VILLAGER_SELLS.put(Items.LEATHER_LEGGINGS, Set.of("leatherworker"));
        VILLAGER_SELLS.put(Items.LEATHER_BOOTS, Set.of("leatherworker"));
        VILLAGER_SELLS.put(Items.SADDLE, Set.of("leatherworker"));
        VILLAGER_SELLS.put(Items.LEATHER_HORSE_ARMOR, Set.of("leatherworker"));
        VILLAGER_SELLS.put(Items.WOLF_ARMOR, Set.of("leatherworker")); // Новинка из 1.20.5+
        
        // Cleric
        VILLAGER_SELLS.put(Items.ENDER_PEARL, Set.of("cleric"));
        VILLAGER_SELLS.put(Items.GLOWSTONE, Set.of("cleric"));
        VILLAGER_SELLS.put(Items.EXPERIENCE_BOTTLE, Set.of("cleric"));
        VILLAGER_SELLS.put(Items.REDSTONE, Set.of("cleric"));
        VILLAGER_SELLS.put(Items.LAPIS_LAZULI, Set.of("cleric"));
        VILLAGER_SELLS.put(Items.ENDER_EYE, Set.of("cleric"));
        
        // Butcher
        VILLAGER_SELLS.put(Items.COOKED_MUTTON, Set.of("butcher"));
        VILLAGER_SELLS.put(Items.COOKED_PORKCHOP, Set.of("butcher"));
        VILLAGER_SELLS.put(Items.COOKED_CHICKEN, Set.of("butcher"));
        VILLAGER_SELLS.put(Items.COOKED_BEEF, Set.of("butcher"));
        VILLAGER_SELLS.put(Items.RABBIT_STEW, Set.of("butcher"));
        
        // Mason
        VILLAGER_SELLS.put(Items.BRICK, Set.of("mason"));
        VILLAGER_SELLS.put(Items.QUARTZ, Set.of("mason"));
        VILLAGER_SELLS.put(Items.DRIPSTONE_BLOCK, Set.of("mason"));
        VILLAGER_SELLS.put(Items.CHISELED_STONE_BRICKS, Set.of("mason"));
        VILLAGER_SELLS.put(Items.POLISHED_GRANITE, Set.of("mason"));
        VILLAGER_SELLS.put(Items.POLISHED_ANDESITE, Set.of("mason"));
        VILLAGER_SELLS.put(Items.POLISHED_DIORITE, Set.of("mason"));
        VILLAGER_SELLS.put(Items.QUARTZ_BLOCK, Set.of("mason"));
        VILLAGER_SELLS.put(Items.QUARTZ_PILLAR, Set.of("mason"));
        
        // Shepherd
        VILLAGER_SELLS.put(Items.SHEARS, Set.of("shepherd"));
        VILLAGER_SELLS.put(Items.WHITE_BED, Set.of("shepherd"));
        VILLAGER_SELLS.put(Items.BLACK_BED, Set.of("shepherd"));
        VILLAGER_SELLS.put(Items.RED_BED, Set.of("shepherd"));
        VILLAGER_SELLS.put(Items.BLUE_BED, Set.of("shepherd"));
        VILLAGER_SELLS.put(Items.WHITE_CARPET, Set.of("shepherd"));
        VILLAGER_SELLS.put(Items.BLACK_CARPET, Set.of("shepherd"));
        VILLAGER_SELLS.put(Items.PAINTING, Set.of("shepherd"));
    }

    private final Setting<Boolean> Debug = sgGeneral.add(new BoolSetting.Builder()
            .name("Debug")
            .description("notify with a message to debug module")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> Close = sgGeneral.add(new BoolSetting.Builder()
            .name("Close")
            .description("Close trading screen after trade")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> CancelEvent = sgGeneral.add(new BoolSetting.Builder()
            .name("Cancel-Event")
            .description("Prevents your eyes from bleeding")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> ticks_to_close = sgGeneral.add(new IntSetting.Builder()
            .name("Ticks-to-close")
            .description("time before closing villager window in ticks")
            .defaultValue(2)
            .min(0)
            .sliderMax(100)
            .visible(Close::get)
            .build()
    );

    public static class TradeRule {
        public List<Item> items = new ArrayList<>();
        public int value1; // Buy: maxBuyPrice | Sell: maxSellQuantity
        public int value2; // Buy: buyLimit       | Sell: emeraldSellLimit
    }

    private final List<TradeRule> buyRules = new ArrayList<>();
    private final List<TradeRule> sellRules = new ArrayList<>();

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        rebuildGui(theme, list);
        return list;
    }

    private void rebuildGui(GuiTheme theme, WVerticalList rootList) {
        rootList.clear();

        WSection buySection = rootList.add(theme.section("Buy Rules", true)).expandX().widget();
        WTable buyTable = buySection.add(theme.table()).expandX().widget();
        
        buyTable.add(theme.label("Items")).expandX();
        buyTable.add(theme.label("Max Price")).minWidth(70);
        buyTable.add(theme.label("Buy Limit")).minWidth(70);
        buyTable.add(theme.label(""));
        buyTable.row();

        for (TradeRule rule : buyRules) {
            addRuleRow(theme, buyTable, rule, false, rootList);
        }

        rootList.add(theme.button("Add Buy Rule")).expandX().widget().action = () -> {
            TradeRule rule = new TradeRule();
            rule.value1 = 1;
            rule.value2 = -1;
            buyRules.add(rule);
            rebuildGui(theme, rootList);
        };

        rootList.add(theme.horizontalSeparator()).expandX();

        WSection sellSection = rootList.add(theme.section("Sell Rules", true)).expandX().widget();
        WTable sellTable = sellSection.add(theme.table()).expandX().widget();
        
        sellTable.add(theme.label("Items")).expandX();
        sellTable.add(theme.label("Max Sell Qty")).minWidth(70);
        sellTable.add(theme.label("Emerald Limit")).minWidth(70);
        sellTable.add(theme.label(""));
        sellTable.row();

        for (TradeRule rule : sellRules) {
            addRuleRow(theme, sellTable, rule, true, rootList);
        }

        rootList.add(theme.button("Add Sell Rule")).expandX().widget().action = () -> {
            TradeRule rule = new TradeRule();
            rule.value1 = -1;
            rule.value2 = -1;
            sellRules.add(rule);
            rebuildGui(theme, rootList);
        };
    }

    private void addRuleRow(GuiTheme theme, WTable table, TradeRule rule, boolean isSell, WVerticalList rootList) {
        Setting<List<Item>> itemSetting = new ItemListSetting.Builder()
            .name("items")
            .description("Items for this rule")
            .defaultValue(new ArrayList<>(rule.items))
            .onChanged(items -> {
                rule.items.clear();
                rule.items.addAll(items);
            })
            .build();

        Settings dummySettings = new Settings();
        SettingGroup hiddenGroup = dummySettings.createGroup("");
        hiddenGroup.sectionExpanded = (true);
        hiddenGroup.add(itemSetting);
        
        table.add(theme.settings(dummySettings)).expandX().top();

        WIntEdit val1Edit = table.add(theme.intEdit(rule.value1, -1, 10000, false)).minWidth(70).top().widget();
        val1Edit.action = () -> rule.value1 = val1Edit.get();

        WIntEdit val2Edit = table.add(theme.intEdit(rule.value2, -1, 10000, false)).minWidth(70).top().widget();
        val2Edit.action = () -> rule.value2 = val2Edit.get();

        WMinus removeBtn = table.add(theme.minus()).top().widget();
        removeBtn.action = () -> {
            if (isSell) sellRules.remove(rule);
            else buyRules.remove(rule);
            rebuildGui(theme, rootList);
        };

        table.row();
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = super.toTag();
        tag.put("buyRules", rulesToTag(buyRules));
        tag.put("sellRules", rulesToTag(sellRules));
        return tag;
    }

    @Override
    public Module fromTag(NbtCompound tag) {
        super.fromTag(tag);
        buyRules.clear();
        sellRules.clear();
        
        if (tag.get("buyRules") instanceof NbtList buyList) {
            rulesFromTag(buyList, buyRules);
        }
        if (tag.get("sellRules") instanceof NbtList sellList) {
            rulesFromTag(sellList, sellRules);
        }
        
        return this;
    }

    private NbtList rulesToTag(List<TradeRule> rules) {
        NbtList list = new NbtList();
        for (TradeRule rule : rules) {
            NbtCompound ruleTag = new NbtCompound();
            NbtList itemsList = new NbtList();
            for (Item item : rule.items) {
                Identifier id = Registries.ITEM.getId(item);
                if (id != null) itemsList.add(NbtString.of(id.toString()));
            }
            ruleTag.put("items", itemsList);
            ruleTag.putInt("value1", rule.value1);
            ruleTag.putInt("value2", rule.value2);
            list.add(ruleTag);
        }
        return list;
    }

    private void rulesFromTag(NbtList list, List<TradeRule> rules) {
        for (NbtElement element : list) {
            if (element instanceof NbtCompound ruleTag) {
                TradeRule rule = new TradeRule();
                
                if (ruleTag.get("items") instanceof NbtList itemsList) {
                    for (NbtElement itemElement : itemsList) {
                        if (itemElement instanceof NbtString itemString) {
                            itemString.asString().ifPresent(idStr -> {
                                Identifier id = Identifier.tryParse(idStr);
                                if (id != null && Registries.ITEM.containsId(id)) {
                                    rule.items.add(Registries.ITEM.get(id));
                                }
                            });
                        }
                    }
                }
                
                rule.value1 = ruleTag.getInt("value1").orElse(-1);
                rule.value2 = ruleTag.getInt("value2").orElse(-1);
                rules.add(rule);
            }
        }
    }

    private final Setting<Boolean> aura = sgAura.add(new BoolSetting.Builder()
            .name("Villager-Aura")
            .description("Clicks on Villagers in range of your vision")
            .defaultValue(false)
            .build()
    );

    private final Setting<Integer> ticks_to_wait = sgAura.add(new IntSetting.Builder()
            .name("Ticks-to-wait")
            .description("time before clicking another villager window in ticks")
            .defaultValue(1)
            .min(0)
            .sliderMax(100)
            .visible(aura::get)
            .build()
    );

    private final Setting<SortPriority> priority = sgAura.add(new EnumSetting.Builder<SortPriority>()
            .name("priority")
            .description("How to filter villagers within range.")
            .defaultValue(SortPriority.ClosestAngle)
            .visible(aura::get)
            .build()
    );

    private final Setting<Double> range = sgAura.add(new DoubleSetting.Builder()
            .name("range")
            .description("The maximum range for the villager to be clicked")
            .defaultValue(4.5)
            .min(0)
            .sliderMax(6)
            .visible(aura::get)
            .build()
    );

    private final Setting<Integer> forget = sgAura.add(new IntSetting.Builder()
            .name("forget-after")
            .description("How many ticks to wait before forgetting which villager to interact with")
            .defaultValue(40)
            .min(20)
            .sliderMax(1000)
            .visible(aura::get)
            .build()
    );

    private final Setting<Integer> maxTargets = sgAura.add(new IntSetting.Builder()
            .name("max-targets")
            .description("How many entities to load at once at most.")
            .defaultValue(1000)
            .min(1)
            .sliderRange(1, 1000)
            .visible(aura::get)
            .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
            .name("Render")
            .description("Renders villagers that you've clicked")
            .defaultValue(false)
            .build()
    );

    public final Setting<Double> fillOpacity = sgRender.add(new DoubleSetting.Builder()
            .name("fill-opacity")
            .description("The opacity of the shape fill.")
            .visible(render::get)
            .defaultValue(0.3)
            .range(0, 1)
            .sliderMax(1)
            .build()
    );

    private final Setting<SettingColor> defaultColor = sgRender.add(new ColorSetting.Builder()
            .name("default-color")
            .description("Color for unsynced actions")
            .defaultValue(new SettingColor(160, 160, 160))
            .visible(render::get)
            .build()
    );

    private final Setting<SettingColor> noEmeraldColor = sgRender.add(new ColorSetting.Builder()
            .name("no-emerald-color")
            .description("Color for no emeralds in inventory")
            .defaultValue(new SettingColor(0, 170, 255))
            .visible(render::get)
            .build()
    );

    private final Setting<SettingColor> noSellItemsColor = sgRender.add(new ColorSetting.Builder()
            .name("no-sell-item-color")
            .description("Color for no sellable items in inventory")
            .defaultValue(new SettingColor(200, 200, 200))
            .visible(render::get)
            .build()
    );

    private final Setting<SettingColor> noTradesColor = sgRender.add(new ColorSetting.Builder()
            .name("no-trades")
            .description("Color for no villager trades in trade list ")
            .defaultValue(new SettingColor(255, 45, 45))
            .visible(render::get)
            .build()
    );

    private final Setting<SettingColor> disabledTradeColor = sgRender.add(new ColorSetting.Builder()
            .name("disabled-trade-color")
            .description("Color for a trade on a cooldown")
            .defaultValue(new SettingColor(255, 210, 0))
            .visible(render::get)
            .build()
    );

    private final Setting<SettingColor> TooExpensiveColor = sgRender.add(new ColorSetting.Builder()
            .name("too-expensive-color")
            .description("Color for a high priced trade")
            .defaultValue(new SettingColor(255, 0, 150))
            .visible(render::get)
            .build()
    );

    private final Setting<SettingColor> limitReachedColor = sgRender.add(new ColorSetting.Builder()
            .name("limit-reached-color")
            .description("Color for when the per-item inventory limit has been reached")
            .defaultValue(new SettingColor(255, 140, 0))
            .visible(render::get)
            .build()
    );

    private final Setting<SettingColor> yesPurchase = sgRender.add(new ColorSetting.Builder()
            .name("purchase-color")
            .description("Color for a successfull trade")
            .defaultValue(new SettingColor(45, 255, 45))
            .visible(render::get)
            .build()
    );

    public TradeAura(Category cat) {
        super(cat, "Trade-Aura", "Trades with villagers for you");
    }

    private final List<Entity> targets = new ArrayList<>();
    private final Map<Entity, Pair<Integer, Color>> VillagerCooldown = new HashMap<>();

    private int ticker = 0;
    private int ticker_close = 0;
    private boolean pendingClose = false;

    private int countItemInInventory(Item item) {
        FindItemResult result = InvUtils.find(item);
        return result.count();
    }

    @Override
    public void onActivate() {
        targets.clear();
        VillagerCooldown.clear();
        ticker = 0;
        ticker_close = 0;
        pendingClose = false;
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (!(event.screen instanceof MerchantScreen)) return;
        if (CancelEvent.get()) event.cancel();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.packet instanceof SetTradeOffersS2CPacket)) return;

        mc.execute(() -> {
            if (mc.player == null) return;
            if (!(mc.player.currentScreenHandler instanceof MerchantScreenHandler MSH)) return;

            syncing_func(MSH);
        });
    }

    private Entity remember_entity;

    private void updateColor(Color clr) {
        if (VillagerCooldown.containsKey(remember_entity)) {
            Pair new_pair = VillagerCooldown.get(remember_entity);
            new_pair.setRight(clr);
            VillagerCooldown.replace(remember_entity, new_pair);
        }
    }

    private void syncing_func(MerchantScreenHandler MSH) {
        if (buyRules.isEmpty() && sellRules.isEmpty()) {
            info("[TradeAura] Rules are empty — nothing to buy/sell. Configure them in the module GUI.");
        }

        try {
            if (!(FieldUtils.readField(MSH, "field_7863", true) instanceof Merchant merc)) return;
            

            TradeOfferList Offers = MSH.getRecipes();
            int num = -1;
            updateColor(noTradesColor.get());

            boolean tradeHappened = false;
            for (TradeOffer offer : Offers) {
                num++;

                ItemStack sellItem = offer.getSellItem();
                ItemStack payItem = offer.getDisplayedFirstBuyItem();

                boolean isSellingToVillager = sellItem.isOf(Items.EMERALD) && !payItem.isOf(Items.EMERALD);

                if (isSellingToVillager) {
                    TradeRule sellRule = null;
                    for (TradeRule rule : sellRules) {
                        if (rule.items.contains(payItem.getItem())) {
                            sellRule = rule;
                            break;
                        }
                    }

                    if (sellRule == null) continue;

                    if (sellRule.value1 != -1 && payItem.getCount() > sellRule.value1) {
                        if (Debug.get())
                            info(payItem.getName().getString() + " sell quantity too high: " + payItem.getCount() + " > " + sellRule.value1);
                        updateColor(TooExpensiveColor.get());
                        continue;
                    }

                    if (sellRule.value2 != -1) {
                        int emeraldCount = countItemInInventory(Items.EMERALD);
                        if (emeraldCount >= sellRule.value2) {
                            if (Debug.get())
                                info("Emerald limit reached for " + payItem.getName().getString() + ": " + emeraldCount + "/" + sellRule.value2);
                            updateColor(limitReachedColor.get());
                            continue;
                        }
                    }

                    int availableCount = countItemInInventory(payItem.getItem());
                    if (availableCount < payItem.getCount()) {
                        if (Debug.get())
                            info("Not enough " + payItem.getName().getString() + " to sell (have " + availableCount + ", need " + payItem.getCount() + ")");
                        updateColor(noSellItemsColor.get()); 
                        continue;
                    }

                    if (offer.isDisabled()) {
                        updateColor(disabledTradeColor.get());
                        continue;
                    }

                    if (Debug.get()) info("SELLING " + payItem.getName().getString());

                    mc.player.networkHandler.sendPacket(new SelectMerchantTradeC2SPacket(num));
                    InvUtils.shiftClick().slotId(2);
                    tradeHappened = true;
                    continue;
                }

                TradeRule buyRule = null;
                for (TradeRule rule : buyRules) {
                    if (rule.items.contains(sellItem.getItem())) {
                        buyRule = rule;
                        break;
                    }
                }

                if (buyRule != null) {
                    
					FindItemResult resultEm = InvUtils.find(Items.EMERALD);
					if (!resultEm.found()) {
						if (Debug.get()) info("no emeralds");
						updateColor(noEmeraldColor.get());
						continue;
					}
					
					if (payItem.isOf(Items.EMERALD) && payItem.getCount() > buyRule.value1) {
                        if (Debug.get())
                            info(offer.getSellItem().getName().getString() + " too expensive " + payItem.getCount());
                        updateColor(TooExpensiveColor.get());
                        continue;
                    }

                    if (buyRule.value2 != -1) {
                        int currentCount = countItemInInventory(sellItem.getItem());
                        if (currentCount >= buyRule.value2) {
                            if (Debug.get())
                                info(sellItem.getName().getString() + " limit reached: " + currentCount + "/" + buyRule.value2);
                            updateColor(limitReachedColor.get());
                            continue;
                        }
                    }

                    if (Debug.get()) {
                        info("BUYING " + sellItem.getName().getString());
                    }

                    if (offer.isDisabled()) {
                        updateColor(disabledTradeColor.get());
                        continue;
                    }

                    mc.player.networkHandler.sendPacket(new SelectMerchantTradeC2SPacket(num));
                    InvUtils.shiftClick().slotId(2);
                    tradeHappened = true;
                }
            }

            if (tradeHappened) updateColor(yesPurchase.get());

            ticker_close = 0;
            pendingClose = true;

        } catch (IllegalAccessException e) {
            info("IAE ex");
            ticker_close = 0;
            pendingClose = true;
        }
    }

    private boolean entityCheck(Entity entity) {
        if (entity.equals(mc.player) || entity.equals(mc.getCameraEntity())) return false;
        if ((entity instanceof LivingEntity livingEntity && livingEntity.isDead()) || !entity.isAlive()) return false;

        Box hitbox = entity.getBoundingBox();
        if (!PlayerUtils.isWithin(
                MathHelper.clamp(mc.player.getX(), hitbox.minX, hitbox.maxX),
                MathHelper.clamp(mc.player.getY(), hitbox.minY, hitbox.maxY),
                MathHelper.clamp(mc.player.getZ(), hitbox.minZ, hitbox.maxZ),
                range.get()
        )) return false;

        return entity instanceof VillagerEntity;
    }

    public void lookAtVillager(Vec3d playerPos, Vec3d villagerPos) {
        Vec3d direction = villagerPos.subtract(playerPos).normalize();
        double yaw = Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90;
        double pitch = Math.toDegrees(-Math.atan2(direction.y, Math.sqrt(direction.x * direction.x + direction.z * direction.z)));

        yaw += (Math.random() - 0.5) * 2;
        pitch += (Math.random() - 0.5) * 2;

        Rotations.rotate(yaw, pitch);
    }

    public void VillagerInteract(Entity villager) {
        Vec3d playerPos = mc.player.getEyePos();
        Vec3d villagerPos = villager.getEyePos();
        EntityHitResult entityHitResult = ProjectileUtil.raycast(mc.player, playerPos, villagerPos, villager.getBoundingBox(), Entity::canHit, playerPos.squaredDistanceTo(villagerPos));
        
        if (entityHitResult == null) {
            ActionResult actionResultDirect = mc.interactionManager.interactEntity(mc.player, villager, Hand.MAIN_HAND);
            if (Debug.get()) info("Raycast didn't find a target");
        } else {
            lookAtVillager(playerPos, villagerPos);
            ActionResult actionResult = mc.interactionManager.interactEntityAtLocation(mc.player, villager, entityHitResult, Hand.MAIN_HAND);
            if (!actionResult.isAccepted()) {
                ActionResult actionResultDirect = mc.interactionManager.interactEntity(mc.player, villager, Hand.MAIN_HAND);
                if (Debug.get()) info("Action wasn't accepted");
            }
        }
    }

    // NEW METHOD: Pre-check whether to click the villager
    private boolean shouldSkipVillager(Entity target) {
        if (!(target instanceof VillagerEntity villager)) return false;

        String professionName = "any";
		
		/**
		idk how to get the proffession, reflection doesn't seem to work. (btw, WHY is that still a thing?..)
		*/
		
        boolean anyValidTrade = false;

        // 1. Check SELL rules (we sell to the villager)
        for (TradeRule rule : sellRules) {
            boolean sellLimitOk = rule.value2 == -1 || countItemInInventory(Items.EMERALD) < rule.value2;
            
            for (Item item : rule.items) {
                if (!VILLAGER_BUYS.containsKey(item)) {
                    anyValidTrade = true; // Unhardcoded item, allow click to check
                    break;
                }
                boolean correctProfession = VILLAGER_BUYS.get(item).contains(professionName) || professionName == "any";
                
                if (correctProfession && sellLimitOk) {
                    anyValidTrade = true; // Found a valid trade!
                    break;
                }
            }
            if (anyValidTrade) break;
        }

        if (anyValidTrade) return false; // If we found a valid sell trade, do not skip

        // 2. Check BUY rules (we buy from the villager)
        for (TradeRule rule : buyRules) {
            for (Item item : rule.items) {
                if (!VILLAGER_SELLS.containsKey(item)) {
                    anyValidTrade = true;
                    break;
                }
                boolean correctProfession = VILLAGER_SELLS.get(item).contains(professionName) || professionName == "any";
                boolean buyLimitOk = rule.value2 == -1 || countItemInInventory(item) < rule.value2;
                
                if (correctProfession && buyLimitOk) {
                    anyValidTrade = true; // Found a valid trade!
                    break;
                }
            }
            if (anyValidTrade) break;
        }

        return !anyValidTrade; 
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!mc.player.isAlive() || PlayerUtils.getGameMode() == GameMode.SPECTATOR) return;

        if (++ticker < ticks_to_wait.get()) return;
        ticker = 0;

        if (mc.player.currentScreenHandler instanceof MerchantScreenHandler) {
            if (!Close.get()) return;
            if (!pendingClose) return;

            if (++ticker_close < ticks_to_close.get()) return;
            ticker_close = 0;
            pendingClose = false;
            mc.player.closeHandledScreen();
            mc.player.getInventory().updateItems();
            return;
        }
        if (!aura.get()) return;

        targets.clear();
        TargetUtils.getList(targets, this::entityCheck, priority.get(), maxTargets.get());

        for (Entity targett : targets) {
            if (!VillagerCooldown.containsKey(targett)) {
                if (shouldSkipVillager(targett)) {
                    if (Debug.get()) info("Skipped villager (pre-check: no valid trades possible).");
                    VillagerCooldown.put(targett, new Pair<>(0, limitReachedColor.get()));
                    continue;
                }

                remember_entity = targett;
                VillagerCooldown.put(targett, new Pair<>(0, defaultColor.get()));
                VillagerInteract(targett);
                break;
            }
        }

        for (Map.Entry<Entity, Pair<Integer, Color>> e : new HashMap<>(VillagerCooldown).entrySet()) {
            int time = e.getValue().getLeft();
            Color clr = e.getValue().getRight();
            if (time > forget.get()) {
                VillagerCooldown.remove(e.getKey());
            } else {
                VillagerCooldown.replace(e.getKey(), new Pair<>(time + 1, clr));
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!render.get()) return;

        for (Map.Entry<Entity, Pair<Integer, Color>> e : new HashMap<>(VillagerCooldown).entrySet()) {
            Entity entity = e.getKey();
            drawBoundingBox(event, entity, e.getValue().getRight());
        }
    }

    private void drawBoundingBox(Render3DEvent event, Entity entity, Color color) {
        Color lineColor = new Color();
        Color sideColor = new Color();

        lineColor.set(color);
        sideColor.set(color).a((int) (sideColor.a * fillOpacity.get()));

        double x = MathHelper.lerp(event.tickDelta, entity.lastRenderX, entity.getX()) - entity.getX();
        double y = MathHelper.lerp(event.tickDelta, entity.lastRenderY, entity.getY()) - entity.getY();
        double z = MathHelper.lerp(event.tickDelta, entity.lastRenderZ, entity.getZ()) - entity.getZ();

        Box box = entity.getBoundingBox();
        event.renderer.box(x + box.minX, y + box.minY, z + box.minZ, x + box.maxX, y + box.maxY, z + box.maxZ, sideColor, lineColor, ShapeMode.Both, 0);
    }
}