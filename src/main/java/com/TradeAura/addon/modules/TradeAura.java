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
import meteordevelopment.meteorclient.utils.player.*;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerInventory;
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
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.Merchant;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.world.GameMode;
import org.apache.commons.lang3.reflect.FieldUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TradeAura extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAura = settings.createGroup("Aura");
    private final SettingGroup sgInventory = settings.createGroup("Inventory manipulation");
    private final SettingGroup sgRender = settings.createGroup("Render");

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

    private final Setting<Boolean> rotateToVillager = sgAura.add(new BoolSetting.Builder()
        .name("Rotate-to-villager")
        .description("Look at the villager before interacting")
        .defaultValue(true)
        .visible(aura::get)
        .build()
    );

    private final Setting<Boolean> cancelMovement = sgAura.add(new BoolSetting.Builder()
        .name("Cancel-Movement")
        .description("Cancel player movement input when the next aura interaction is close")
        .defaultValue(false)
        .visible(aura::get)
        .build()
    );

    private final Setting<Integer> ticks_to_cancel_movement = sgAura.add(new IntSetting.Builder()
        .name("Ticks-to-cancel-movement")
        .description("Cancel movement when fewer than this many ticks remain until the next aura interaction")
        .defaultValue(2)
        .min(0)
        .sliderMax(20)
        .visible(() -> aura.get() && cancelMovement.get())
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

    private final Setting<Boolean> refreshDefaultColorCooldown = sgAura.add(new BoolSetting.Builder()
        .name("Refresh-stale-default-color")
        .description("Force the cooldown to refresh when a villager is still using the default render color")
        .defaultValue(true)
        .visible(aura::get)
        .build()
    );

    private final Setting<Boolean> inventoryManipulationEnabled = sgInventory.add(new BoolSetting.Builder()
        .name("Enabled")
        .description("Enable inventory manipulation triggers")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> dropExcessItemsEnabled = sgInventory.add(new BoolSetting.Builder()
        .name("Drop Excess Items")
        .description("Drop inventory items that exceed the configured limit")
        .defaultValue(false)
        .visible(inventoryManipulationEnabled::get)
        .build()
    );

    private final Setting<List<Item>> dropExcessItems = sgInventory.add(new ItemListSetting.Builder()
        .name("Drop Items")
        .description("Specific items to consider for dropping")
        .defaultValue(new ArrayList<>())
        .visible(() -> inventoryManipulationEnabled.get() && dropExcessItemsEnabled.get())
        .build()
    );

    private final Setting<Integer> dropExcessItemsTrigger = sgInventory.add(new IntSetting.Builder()
        .name("Drop Trigger")
        .description("Drop items when their count exceeds this value")
        .defaultValue(64)
        .min(1)
        .sliderMax(256)
        .visible(() -> inventoryManipulationEnabled.get() && dropExcessItemsEnabled.get())
        .build()
    );

    private final Setting<Integer> dropExcessItemsLeave = sgInventory.add(new IntSetting.Builder()
        .name("Drop Leave")
        .description("Keep this many items in inventory when dropping excess")
        .defaultValue(32)
        .min(0)
        .sliderMax(256)
        .visible(() -> inventoryManipulationEnabled.get() && dropExcessItemsEnabled.get())
        .build()
    );

    private final Setting<Direction> dropExcessItemsDirection = sgInventory.add(new EnumSetting.Builder<Direction>()
        .name("Drop Direction")
        .description("Direction to spawn dropped items in")
        .defaultValue(Direction.UP)
        .visible(() -> inventoryManipulationEnabled.get() && dropExcessItemsEnabled.get())
        .build()
    );

    private final Setting<Boolean> compressEmeraldsEnabled = sgInventory.add(new BoolSetting.Builder()
        .name("Compress Emeralds")
        .description("Craft emeralds into emerald blocks when above the configured threshold")
        .defaultValue(false)
        .visible(inventoryManipulationEnabled::get)
        .build()
    );

    private final Setting<Integer> compressEmeraldsTrigger = sgInventory.add(new IntSetting.Builder()
        .name("Compress Trigger")
        .description("Craft emeralds into blocks when emerald count exceeds this value")
        .defaultValue(64)
        .min(1)
        .sliderMax(256)
        .visible(() -> inventoryManipulationEnabled.get() && compressEmeraldsEnabled.get())
        .build()
    );

    private final Setting<Integer> compressEmeraldsLeave = sgInventory.add(new IntSetting.Builder()
        .name("Compress Leave")
        .description("Leave this many emeralds in inventory when compressing")
        .defaultValue(32)
        .min(0)
        .sliderMax(256)
        .visible(() -> inventoryManipulationEnabled.get() && compressEmeraldsEnabled.get())
        .build()
    );

    private final Setting<Boolean> decompressEmeraldsEnabled = sgInventory.add(new BoolSetting.Builder()
        .name("Decompress Emeralds")
        .description("Craft emerald blocks back into emeralds when below the configured threshold")
        .defaultValue(false)
        .visible(inventoryManipulationEnabled::get)
        .build()
    );

    private final Setting<Integer> decompressEmeraldsTrigger = sgInventory.add(new IntSetting.Builder()
        .name("Decompress Trigger")
        .description("Decompress emerald blocks when emerald count is below this value")
        .defaultValue(32)
        .min(1)
        .sliderMax(256)
        .visible(() -> inventoryManipulationEnabled.get() && decompressEmeraldsEnabled.get())
        .build()
    );

    private final Setting<Integer> decompressEmeraldsLeave = sgInventory.add(new IntSetting.Builder()
        .name("Decompress Leave")
        .description("Keep this many emeralds in inventory when decompressing")
        .defaultValue(64)
        .min(0)
        .sliderMax(256)
        .visible(() -> inventoryManipulationEnabled.get() && decompressEmeraldsEnabled.get())
        .build()
    );

    private final Setting<Boolean> dumpToShulkerEnabled = sgInventory.add(new BoolSetting.Builder()
        .name("Dump To Shulker")
        .description("Move excess items into a shulker box item")
        .defaultValue(false)
        .visible(inventoryManipulationEnabled::get)
        .build()
    );

    private final Setting<Integer> dumpToShulkerTrigger = sgInventory.add(new IntSetting.Builder()
        .name("Dump Trigger")
        .description("Move items into a shulker box when their count exceeds this value")
        .defaultValue(64)
        .min(1)
        .sliderMax(256)
        .visible(() -> inventoryManipulationEnabled.get() && dumpToShulkerEnabled.get())
        .build()
    );

    private final Setting<Integer> dumpToShulkerLeave = sgInventory.add(new IntSetting.Builder()
        .name("Dump Leave")
        .description("Leave this many items in inventory before dumping them")
        .defaultValue(32)
        .min(0)
        .sliderMax(256)
        .visible(() -> inventoryManipulationEnabled.get() && dumpToShulkerEnabled.get())
        .build()
    );

    private final Setting<Boolean> refillFromShulkerEnabled = sgInventory.add(new BoolSetting.Builder()
        .name("Refill From Shulker")
        .description("Move stored items back into the inventory from a shulker box item")
        .defaultValue(false)
        .visible(inventoryManipulationEnabled::get)
        .build()
    );

    private final Setting<Integer> refillFromShulkerTrigger = sgInventory.add(new IntSetting.Builder()
        .name("Refill Trigger")
        .description("Refill only when the tracked item count is below this threshold")
        .defaultValue(32)
        .min(1)
        .sliderMax(256)
        .visible(() -> inventoryManipulationEnabled.get() && refillFromShulkerEnabled.get())
        .build()
    );

    private final Setting<Integer> refillFromShulkerLeave = sgInventory.add(new IntSetting.Builder()
        .name("Refill Leave")
        .description("Keep this many items in inventory when refilling")
        .defaultValue(64)
        .min(0)
        .sliderMax(256)
        .visible(() -> inventoryManipulationEnabled.get() && refillFromShulkerEnabled.get())
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("Render")
        .description("Renders villagers that you've clicked")
        .defaultValue(false)
        .visible(aura::get)
        .build()
    );

    public final Setting<Double> fillOpacity = sgRender.add(new DoubleSetting.Builder()
        .name("fill-opacity")
        .description("The opacity of the shape fill.")
        .visible(() -> aura.get() && render.get())
        .defaultValue(0.3)
        .range(0, 1)
        .sliderMax(1)
        .build()
    );

    private final Setting<SettingColor> defaultColor = sgRender.add(new ColorSetting.Builder()
        .name("default-color")
        .description("Color for unsynced actions")
        .defaultValue(new SettingColor(160, 160, 160))
        .visible(() -> aura.get() && render.get())
        .build()
    );

    private final Setting<SettingColor> noEmeraldColor = sgRender.add(new ColorSetting.Builder()
        .name("no-emerald-color")
        .description("Color for no emeralds in inventory")
        .defaultValue(new SettingColor(0, 170, 255))
        .visible(() -> aura.get() && render.get())
        .build()
    );

    private final Setting<SettingColor> noSellItemsColor = sgRender.add(new ColorSetting.Builder()
        .name("no-sell-item-color")
        .description("Color for no sellable items in inventory")
        .defaultValue(new SettingColor(200, 200, 200))
        .visible(() -> aura.get() && render.get())
        .build()
    );

    private final Setting<SettingColor> noTradesColor = sgRender.add(new ColorSetting.Builder()
        .name("no-trades")
        .description("Color for no villager trades in trade list ")
        .defaultValue(new SettingColor(255, 45, 45))
        .visible(() -> aura.get() && render.get())
        .build()
    );

    private final Setting<SettingColor> disabledTradeColor = sgRender.add(new ColorSetting.Builder()
        .name("disabled-trade-color")
        .description("Color for a trade on a cooldown")
        .defaultValue(new SettingColor(255, 210, 0))
        .visible(() -> aura.get() && render.get())
        .build()
    );

    private final Setting<SettingColor> TooExpensiveColor = sgRender.add(new ColorSetting.Builder()
        .name("too-expensive-color")
        .description("Color for a high priced trade")
        .defaultValue(new SettingColor(255, 0, 150))
        .visible(() -> aura.get() && render.get())
        .build()
    );

    private final Setting<SettingColor> limitReachedColor = sgRender.add(new ColorSetting.Builder()
        .name("limit-reached-color")
        .description("Color for when the per-item inventory limit has been reached")
        .defaultValue(new SettingColor(255, 140, 0))
        .visible(() -> aura.get() && render.get())
        .build()
    );

    private final Setting<SettingColor> yesPurchase = sgRender.add(new ColorSetting.Builder()
        .name("purchase-color")
        .description("Color for a successfull trade")
        .defaultValue(new SettingColor(45, 255, 45))
        .visible(() -> aura.get() && render.get())
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

    @Override
    public void onDeactivate() {
        if (mc.player != null && mc.player.currentScreenHandler != null) {
            mc.player.closeHandledScreen();
            mc.player.getInventory().updateItems();
        }

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
            Pair<Integer, Color> newPair = VillagerCooldown.get(remember_entity);
            newPair.setRight(clr);
            VillagerCooldown.replace(remember_entity, newPair);
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

    private boolean runInventoryManipulation() {
        if (!inventoryManipulationEnabled.get() || mc.player == null || mc.world == null) return false;

        boolean didAnyAction = false;
        boolean performedThisPass;

        do {
            performedThisPass = false;

            if (dropExcessItemsEnabled.get() && tryDropExcessItems()) {
                didAnyAction = true;
                performedThisPass = true;
            } else if (compressEmeraldsEnabled.get() && tryCompressEmeralds()) {
                didAnyAction = true;
                performedThisPass = true;
            } else if (decompressEmeraldsEnabled.get() && tryDecompressEmeralds()) {
                didAnyAction = true;
                performedThisPass = true;
            } else if (dumpToShulkerEnabled.get() && tryDumpToShulker()) {
                didAnyAction = true;
                performedThisPass = true;
            } else if (refillFromShulkerEnabled.get() && tryRefillFromShulker()) {
                didAnyAction = true;
                performedThisPass = true;
            }
        } while (performedThisPass);

        return didAnyAction;
    }

    private boolean tryDropExcessItems() {
        if (!dropExcessItemsEnabled.get()) return false;

        PlayerInventory inventory = mc.player.getInventory();
        List<Item> selectedItems = dropExcessItems.get();
        if (selectedItems.isEmpty()) return false;

        int totalMatching = 0;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty()) continue;
            if (selectedItems.contains(stack.getItem())) totalMatching += stack.getCount();
        }

        int trigger = Math.max(1, dropExcessItemsTrigger.get());
        int leave = Math.max(0, dropExcessItemsLeave.get());
        if (totalMatching <= trigger) return false;

        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty() || !selectedItems.contains(stack.getItem())) continue;
            if (stack.getCount() <= leave) continue;

            int slotId = SlotUtils.indexToId(i);
            if (slotId < 0) continue;

            InvUtils.drop().slotId(slotId);
            return true;
        }

        return false;
    }

    private boolean tryCompressEmeralds() {
        if (!compressEmeraldsEnabled.get()) return false;

        PlayerInventory inventory = mc.player.getInventory();
        int emeraldCount = countItemsInInventory(inventory, Items.EMERALD);
        int trigger = Math.max(1, compressEmeraldsTrigger.get());
        int leave = Math.max(0, compressEmeraldsLeave.get());
        if (emeraldCount <= trigger) return false;

        int toConvert = emeraldCount - leave;
        int blocksToCraft = toConvert / 9;
        if (blocksToCraft <= 0) return false;

        if (!ensureCraftingTableOpen()) return false;
        if (!(mc.player.currentScreenHandler instanceof CraftingScreenHandler craftingHandler)) return false;

        int sourceSlot = findInventorySlotWithItem(inventory, Items.EMERALD);
        if (sourceSlot < 0) return false;

        int sourceSlotId = SlotUtils.indexToId(sourceSlot);
        if (sourceSlotId < 0) return false;

        for (int gridSlot = 1; gridSlot <= 9; gridSlot++) {
            if (countItemsInInventory(inventory, Items.EMERALD) <= 0) break;
            mc.interactionManager.clickSlot(craftingHandler.syncId, sourceSlotId, 1, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(craftingHandler.syncId, gridSlot, 0, SlotActionType.PICKUP, mc.player);
        }

        mc.interactionManager.clickSlot(craftingHandler.syncId, 0, 0, SlotActionType.PICKUP, mc.player);
        return true;
    }

    private boolean tryDecompressEmeralds() {
        if (!decompressEmeraldsEnabled.get()) return false;

        PlayerInventory inventory = mc.player.getInventory();
        int emeraldCount = countItemsInInventory(inventory, Items.EMERALD);
        int trigger = Math.max(1, decompressEmeraldsTrigger.get());
        int leave = Math.max(0, decompressEmeraldsLeave.get());
        if (emeraldCount >= leave) return false;

        int toConvert = leave - Math.max(emeraldCount, trigger);
        if (toConvert <= 0) return false;

        if (!ensureCraftingTableOpen()) return false;
        if (!(mc.player.currentScreenHandler instanceof CraftingScreenHandler craftingHandler)) return false;

        int sourceSlot = findInventorySlotWithItem(inventory, Items.EMERALD_BLOCK);
        if (sourceSlot < 0) return false;

        int sourceSlotId = SlotUtils.indexToId(sourceSlot);
        if (sourceSlotId < 0) return false;

        int blocksToConvert = Math.min(countItemsInInventory(inventory, Items.EMERALD_BLOCK), toConvert / 9);
        if (blocksToConvert <= 0) return false;

        //for (int gridSlot = 1; gridSlot <= 1; gridSlot++) {
        if (countItemsInInventory(inventory, Items.EMERALD_BLOCK) <= 0) return false;
        if (mc.interactionManager == null) return false;

        mc.interactionManager.clickSlot(craftingHandler.syncId, sourceSlotId, 0, SlotActionType.QUICK_MOVE, mc.player);
        //mc.interactionManager.clickSlot(craftingHandler.syncId, 1, 0, SlotActionType.PICKUP, mc.player);
        //}

        mc.interactionManager.clickSlot(craftingHandler.syncId, 0, 0, SlotActionType.QUICK_MOVE, mc.player);
        return true;
    }

    private boolean tryDumpToShulker() {
        if (!dumpToShulkerEnabled.get()) return false;

        PlayerInventory inventory = mc.player.getInventory();
        ItemStack shulkerStack = findShulkerBoxInInventory(inventory);
        if (shulkerStack == null) {
            shulkerStack = new ItemStack(Items.SHULKER_BOX);
            if (!insertStackIntoInventory(inventory, shulkerStack)) return false;
        }

        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty() || stack.isOf(Items.SHULKER_BOX)) continue;

            int trigger = Math.max(1, dumpToShulkerTrigger.get());
            int leave = Math.max(0, dumpToShulkerLeave.get());
            if (stack.getCount() <= trigger) continue;

            int toStore = stack.getCount() - leave;
            if (toStore <= 0) continue;

            ItemStack storedStack = stack.copyWithCount(Math.min(toStore, stack.getCount()));
            stack.decrement(storedStack.getCount());
            if (stack.isEmpty()) inventory.setStack(i, ItemStack.EMPTY);
            return true;
        }

        return false;
    }

    private boolean tryRefillFromShulker() {
        if (!refillFromShulkerEnabled.get()) return false;
        return false;
    }

    private ItemStack findShulkerBoxInInventory(PlayerInventory inventory) {
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && stack.isOf(Items.SHULKER_BOX)) return stack;
        }
        return null;
    }

    private int findInventorySlotWithItem(PlayerInventory inventory, Item item) {
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && stack.isOf(item)) return i;
        }
        return -1;
    }

    private boolean ensureCraftingTableOpen() {
        if (mc.player == null || mc.world == null) return false;
        if (mc.player.currentScreenHandler instanceof CraftingScreenHandler) return true;

        BlockPos nearestTable = findNearestCraftingTable();
        if (nearestTable == null) return false;

        Vec3d hitVec = Vec3d.ofCenter(nearestTable);
        BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, nearestTable, false);
        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
        return result.isAccepted();
    }

    private BlockPos findNearestCraftingTable() {
        if (mc.player == null || mc.world == null) return null;
        BlockPos center = mc.player.getBlockPos();
        for (int radius = 0; radius <= 4; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        BlockPos pos = center.add(x, y, z);
                        if (mc.world.getBlockState(pos).isOf(Blocks.CRAFTING_TABLE)) return pos;
                    }
                }
            }
        }
        return null;
    }

    private int countItemsInInventory(PlayerInventory inventory, Item item) {
        int count = 0;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && stack.isOf(item)) count += stack.getCount();
        }
        return count;
    }

    private void removeItemsFromInventory(PlayerInventory inventory, Item item, int amount) {
        int remaining = amount;
        for (int i = 0; i < inventory.size() && remaining > 0; i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty() || !stack.isOf(item)) continue;
            int taken = Math.min(stack.getCount(), remaining);
            stack.decrement(taken);
            remaining -= taken;
            if (stack.isEmpty()) inventory.setStack(i, ItemStack.EMPTY);
        }
    }

    private boolean insertStackIntoInventory(PlayerInventory inventory, ItemStack stack) {
        if (stack.isEmpty()) return false;
        boolean inserted = inventory.insertStack(stack);
        if (!inserted && !stack.isEmpty()) {
            ItemEntity entity = new ItemEntity(mc.world, mc.player.getX(), mc.player.getY() + 1.0, mc.player.getZ(), stack);
            mc.world.spawnEntity(entity);
            return true;
        }
        return inserted;
    }

    private boolean hasCraftingTableNearby() {
        if (mc.player == null || mc.world == null) return false;
        BlockPos center = mc.player.getBlockPos();
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    if (mc.world.getBlockState(center.add(x, y, z)).isOf(Blocks.CRAFTING_TABLE)) return true;
                }
            }
        }
        return false;
    }

    private Vec3d getDropDirectionVector() {
        return switch (dropExcessItemsDirection.get()) {
            case DOWN -> new Vec3d(0, -1, 0);
            case NORTH -> new Vec3d(0, 0, -1);
            case SOUTH -> new Vec3d(0, 0, 1);
            case WEST -> new Vec3d(-1, 0, 0);
            case EAST -> new Vec3d(1, 0, 0);
            default -> new Vec3d(0, 1, 0);
        };
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
            if (rotateToVillager.get()) {
                lookAtVillager(playerPos, villagerPos);
            }
            ActionResult actionResult = mc.interactionManager.interactEntityAtLocation(mc.player, villager, entityHitResult, Hand.MAIN_HAND);
            if (!actionResult.isAccepted()) {
                ActionResult actionResultDirect = mc.interactionManager.interactEntity(mc.player, villager, Hand.MAIN_HAND);
                if (Debug.get()) info("Action wasn't accepted");
            }
        }
    }

    private void cancelPlayerMovementControl() {
        if (mc.player == null || mc.player.input == null) return;

        int ticksRemaining = ticks_to_wait.get() - ticker;
        if (ticksRemaining < 0 || ticksRemaining > ticks_to_cancel_movement.get()) return;

        if (mc.player != null) {
            mc.player.setSprinting(false);
            mc.player.setSneaking(false);
            mc.player.input.jump();
        }

        if (mc.options != null) {
            mc.options.forwardKey.setPressed(false);
            mc.options.backKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
            mc.options.jumpKey.setPressed(false);
            mc.options.sneakKey.setPressed(false);
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
                if (!TradeData.VILLAGER_BUYS.containsKey(item)) {
                    anyValidTrade = true; // Unhardcoded item, allow click to check
                    break;
                }
                boolean correctProfession = TradeData.VILLAGER_BUYS.get(item).contains(professionName) || professionName.equals("any");

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
                if (!TradeData.VILLAGER_SELLS.containsKey(item)) {
                    anyValidTrade = true;
                    break;
                }
                boolean correctProfession = TradeData.VILLAGER_SELLS.get(item).contains(professionName) || professionName.equals("any");
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

        if (aura.get() && inventoryManipulationEnabled.get() && runInventoryManipulation()) {
            return;
        }

        if (++ticker < ticks_to_wait.get()) {
            if (aura.get() && cancelMovement.get()) {
                cancelPlayerMovementControl();
            }
            return;
        }
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
            if (VillagerCooldown.containsKey(targett)) {
                if (refreshDefaultColorCooldown.get()) {
                    Pair<Integer, Color> existing = VillagerCooldown.get(targett);
                    if (existing != null && existing.getRight().equals(defaultColor.get())) {
                        VillagerCooldown.put(targett, new Pair<>(forget.get(), defaultColor.get()));
                    }
                }
                continue;
            }

            if (shouldSkipVillager(targett)) {
                if (Debug.get()) info("Skipped villager (pre-check: no valid trades possible).");
                VillagerCooldown.put(targett, new Pair<>(0, limitReachedColor.get()));
                continue;
            }

            remember_entity = targett;
            VillagerCooldown.put(targett, new Pair<>(0, defaultColor.get()));
            if (rotateToVillager.get()) {
                VillagerInteract(targett);
            } else {
                if (Debug.get()) info("Rotation disabled, interacting without rotating.");
                ActionResult actionResult = mc.interactionManager.interactEntity(mc.player, targett, Hand.MAIN_HAND);
                if (!actionResult.isAccepted() && Debug.get()) {
                    info("Aura interaction was not accepted.");
                }
            }
            break;
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
