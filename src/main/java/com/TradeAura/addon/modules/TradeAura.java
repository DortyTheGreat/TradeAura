package com.TradeAura.addon.modules;

import com.TradeAura.addon.inventory.InvHelper;
import com.TradeAura.addon.inventory.MovementControl;
import com.TradeAura.addon.inventory.InventoryManager;
import com.TradeAura.addon.inventory.InventorySettings;
import com.TradeAura.addon.inventory.ItemRule;

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

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Tuple;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TradeAura extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAura = settings.createGroup("Aura");
    private final SettingGroup sgInventory = settings.createGroup("Inventory manipulation");
    private final SettingGroup sgRender = settings.createGroup("Render");

    /** Every setting of the "Inventory manipulation" tab. */
    public final InventorySettings invSettings = new InventorySettings(sgInventory, this::rebuildGuiIfPossible);
    /** Runs the inventory triggers, interrupts the aura while an action is in progress. */
    private final InventoryManager invManager = new InventoryManager(this, invSettings);

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

    private GuiTheme lastTheme;
    private WVerticalList lastList;

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        lastTheme = theme;
        lastList = list;
        rebuildGui(theme, list);
        return list;
    }

    /** Called when an "Inventory manipulation" toggle changes so the rule tables appear / disappear with it. */
    private void rebuildGuiIfPossible() {
        if (lastTheme != null && lastList != null) rebuildGui(lastTheme, lastList);
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

        if (!invSettings.enabled.get()) return;

        if (invSettings.dropEnabled.get()) {
            addItemRuleSection(theme, rootList, "Drop Rules", invSettings.dropRules,
                "Drop above", "Keep", "Add Drop Rule", 64, 32);
        }

        if (invSettings.dumpEnabled.get()) {
            addItemRuleSection(theme, rootList, "Dump Rules", invSettings.dumpRules,
                "Dump above", "Keep", "Add Dump Rule", 64, 32);
        }

        if (invSettings.refillEnabled.get()) {
            addItemRuleSection(theme, rootList, "Refill Rules", invSettings.refillRules,
                "Refill below", "Fill to", "Add Refill Rule", 32, 64);
        }
    }

    /** One table of {@link ItemRule}s, built in the same style as the buy / sell rules above. */
    private void addItemRuleSection(GuiTheme theme, WVerticalList rootList, String title, List<ItemRule> rules,
                                    String triggerLabel, String leaveLabel, String addLabel,
                                    int defaultTrigger, int defaultLeave) {
        rootList.add(theme.horizontalSeparator()).expandX();

        WSection section = rootList.add(theme.section(title, true)).expandX().widget();
        WTable table = section.add(theme.table()).expandX().widget();

        table.add(theme.label("Items")).expandX();
        table.add(theme.label(triggerLabel)).minWidth(70);
        table.add(theme.label(leaveLabel)).minWidth(70);
        table.add(theme.label(""));
        table.row();

        for (ItemRule rule : rules) {
            addItemRuleRow(theme, table, rules, rule, rootList);
        }

        rootList.add(theme.button(addLabel)).expandX().widget().action = () -> {
            rules.add(new ItemRule(defaultTrigger, defaultLeave));
            rebuildGui(theme, rootList);
        };
    }

    private void addItemRuleRow(GuiTheme theme, WTable table, List<ItemRule> rules, ItemRule rule, WVerticalList rootList) {
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
        hiddenGroup.sectionExpanded = true;
        hiddenGroup.add(itemSetting);

        table.add(theme.settings(dummySettings)).expandX().top();

        WIntEdit triggerEdit = table.add(theme.intEdit(rule.trigger, 0, 10000, false)).minWidth(70).top().widget();
        triggerEdit.action = () -> rule.trigger = triggerEdit.get();

        WIntEdit leaveEdit = table.add(theme.intEdit(rule.leave, 0, 10000, false)).minWidth(70).top().widget();
        leaveEdit.action = () -> rule.leave = leaveEdit.get();

        WMinus removeBtn = table.add(theme.minus()).top().widget();
        removeBtn.action = () -> {
            rules.remove(rule);
            rebuildGui(theme, rootList);
        };

        table.row();
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
    public CompoundTag toTag() {
        CompoundTag tag = super.toTag();
        tag.put("buyRules", rulesToTag(buyRules));
        tag.put("sellRules", rulesToTag(sellRules));
        tag.put("dropRules", ItemRule.listToTag(invSettings.dropRules));
        tag.put("dumpRules", ItemRule.listToTag(invSettings.dumpRules));
        tag.put("refillRules", ItemRule.listToTag(invSettings.refillRules));
        return tag;
    }

    @Override
    public Module fromTag(CompoundTag tag) {
        super.fromTag(tag);
        buyRules.clear();
        sellRules.clear();
        
        if (tag.get("buyRules") instanceof ListTag buyList) {
            rulesFromTag(buyList, buyRules);
        }
        if (tag.get("sellRules") instanceof ListTag sellList) {
            rulesFromTag(sellList, sellRules);
        }
        if (tag.get("dropRules") instanceof ListTag dropList) {
            ItemRule.listFromTag(dropList, invSettings.dropRules);
        }
        if (tag.get("dumpRules") instanceof ListTag dumpList) {
            ItemRule.listFromTag(dumpList, invSettings.dumpRules);
        }
        if (tag.get("refillRules") instanceof ListTag refillList) {
            ItemRule.listFromTag(refillList, invSettings.refillRules);
        }
        
        return this;
    }

    private ListTag rulesToTag(List<TradeRule> rules) {
        ListTag list = new ListTag();
        for (TradeRule rule : rules) {
            CompoundTag ruleTag = new CompoundTag();
            ListTag itemsList = new ListTag();
            for (Item item : rule.items) {
                Identifier id = BuiltInRegistries.ITEM.getKey(item);
                if (id != null) itemsList.add(StringTag.valueOf(id.toString()));
            }
            ruleTag.put("items", itemsList);
            ruleTag.putInt("value1", rule.value1);
            ruleTag.putInt("value2", rule.value2);
            list.add(ruleTag);
        }
        return list;
    }

    private void rulesFromTag(ListTag list, List<TradeRule> rules) {
        for (Tag element : list) {
            if (element instanceof CompoundTag ruleTag) {
                TradeRule rule = new TradeRule();
                
                if (ruleTag.get("items") instanceof ListTag itemsList) {
                    for (Tag itemElement : itemsList) {
                        if (itemElement instanceof StringTag itemString) {
                            itemString.asString().ifPresent(idStr -> {
                                Identifier id = Identifier.tryParse(idStr);
                                if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                                    rule.items.add(BuiltInRegistries.ITEM.getValue(id));
                                }
                            });
                        }
                    }
                }
                
                rule.value1 = ruleTag.getIntOr("value1", -1);
                rule.value2 = ruleTag.getIntOr("value2", -1);
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
            .description("Block your movement input while a villager the aura can click is in range. Never triggers when there is nothing to trade with.")
            .defaultValue(false)
            .visible(aura::get)
            .build()
    );

    private final Setting<Integer> ticks_to_cancel_movement = sgAura.add(new IntSetting.Builder()
            .name("Ticks-to-cancel-movement")
            .description("Block movement when fewer than this many ticks remain until the next interaction. If this is not clearly below Ticks-to-wait, every tick falls into the window and movement is blocked the whole time a villager is in range.")
            .defaultValue(2)
            .min(0)
            .sliderMax(20)
            .visible(() -> aura.get() && cancelMovement.get())
            .build()
    );

    private final Setting<Boolean> cancelMovementNearVillager = sgAura.add(new BoolSetting.Builder()
            .name("Cancel-movement-near-villager")
            .description("Block movement the entire time a villager is in range instead of only inside the countdown window above. Keeps you from walking out of range mid trading.")
            .defaultValue(false)
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

    private final Setting<Boolean> refreshUnsynced = sgAura.add(new BoolSetting.Builder()
            .name("Refresh-unsynced-cooldown")
            .description("If a villager never synced properly (it is still rendered with the default color), force its cooldown to refresh so the aura retries the interaction.")
            .defaultValue(true)
            .visible(aura::get)
            .build()
    );

    private final Setting<Integer> refreshDelay = sgAura.add(new IntSetting.Builder()
            .name("Refresh-delay")
            .description("How many aura cycles (Ticks-to-wait each) to give the server to answer before the cooldown of an unsynced villager is refreshed.")
            .defaultValue(6)
            .min(1)
            .sliderMax(60)
            .visible(() -> aura.get() && refreshUnsynced.get())
            .build()
    );

    private final Setting<Integer> maxRefreshes = sgAura.add(new IntSetting.Builder()
            .name("Max-refreshes")
            .description("How often the same villager may be retried in a row before it is left alone until 'forget-after' expires. Stops the aura from spamming a villager that never answers.")
            .defaultValue(3)
            .min(1)
            .sliderMax(20)
            .visible(() -> aura.get() && refreshUnsynced.get())
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
    private final Map<Entity, Tuple<Integer, Color>> VillagerCooldown = new HashMap<>();
    /** How often an unsynced villager has been retried in a row, see 'Refresh-unsynced-cooldown'. */
    private final Map<Entity, Integer> refreshCount = new HashMap<>();
    /** Scratch list for the movement check, so no list is allocated every tick. */
    private final List<Entity> movementCheckTargets = new ArrayList<>();

    private int ticker = 0;
    private int ticker_close = 0;
    private boolean pendingClose = false;

    /** Used by the inventory manager for its debug output. */
    public boolean isDebug() {
        return Debug.get();
    }

    private int countItemInInventory(Item item) {
        FindItemResult result = InvUtils.find(item);
        return result.count();
    }

    @Override
    public void onActivate() {
        targets.clear();
        VillagerCooldown.clear();
        refreshCount.clear();
        ticker = 0;
        ticker_close = 0;
        pendingClose = false;

        invManager.reset();
        invManager.reportConflicts();
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null && mc.player.containerMenu != null) {
            InvHelper.closeScreen();
            mc.player.getInventory().tick();
        }

        targets.clear();
        VillagerCooldown.clear();
        refreshCount.clear();
        ticker = 0;
        ticker_close = 0;
        pendingClose = false;

        invManager.reset();
        MovementControl.stop();
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (event.screen instanceof MerchantScreen) {
            if (CancelEvent.get()) event.cancel();
            return;
        }

        // Screens the inventory tasks open themselves, the handler is set either way.
        if (!invSettings.cancelScreens.get() || !invManager.isBusy()) return;
        if (!(event.screen instanceof AbstractContainerScreen<?> screen)) return;

        if (screen.getMenu() instanceof ShulkerBoxMenu || screen.getMenu() instanceof CraftingMenu) event.cancel();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.packet instanceof ClientboundMerchantOffersPacket)) return;

        mc.execute(() -> {
            if (mc.player == null) return;
            if (!(mc.player.containerMenu instanceof MerchantMenu MSH)) return;

            syncing_func(MSH);
        });
    }

    private Entity remember_entity;

    private void updateColor(Color clr) {
        if (VillagerCooldown.containsKey(remember_entity)) {
            Tuple<Integer, Color> newPair = VillagerCooldown.get(remember_entity);
            newPair.setB(clr);
            VillagerCooldown.replace(remember_entity, newPair);

            // The villager answered, so it is not "unsynced" anymore.
            if (!clr.equals(defaultColor.get())) refreshCount.remove(remember_entity);
        }
    }

    private void syncing_func(MerchantMenu MSH) {
        if (buyRules.isEmpty() && sellRules.isEmpty()) {
            info("[TradeAura] Rules are empty — nothing to buy/sell. Configure them in the module GUI.");
        }

        {
            // 1.21.11 read the private merchant field by its intermediary name ("field_7863"). 26.1 is
            // unobfuscated, and the value was never used for anything but a type check, so it is gone.
            var Offers = MSH.getOffers();
            int num = -1;
            updateColor(noTradesColor.get());

            boolean tradeHappened = false;
            for (MerchantOffer offer : Offers) {
                num++;

                ItemStack sellItem = offer.getResult();
                ItemStack payItem = offer.getCostA();

                boolean isSellingToVillager = sellItem.is(Items.EMERALD) && !payItem.is(Items.EMERALD);

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
                            info(payItem.getHoverName().getString() + " sell quantity too high: " + payItem.getCount() + " > " + sellRule.value1);
                        updateColor(TooExpensiveColor.get());
                        continue;
                    }

                    if (sellRule.value2 != -1) {
                        int emeraldCount = countItemInInventory(Items.EMERALD);
                        if (emeraldCount >= sellRule.value2) {
                            if (Debug.get())
                                info("Emerald limit reached for " + payItem.getHoverName().getString() + ": " + emeraldCount + "/" + sellRule.value2);
                            updateColor(limitReachedColor.get());
                            continue;
                        }
                    }

                    int availableCount = countItemInInventory(payItem.getItem());
                    if (availableCount < payItem.getCount()) {
                        if (Debug.get())
                            info("Not enough " + payItem.getHoverName().getString() + " to sell (have " + availableCount + ", need " + payItem.getCount() + ")");
                        updateColor(noSellItemsColor.get()); 
                        continue;
                    }

                    if (offer.isOutOfStock()) {
                        updateColor(disabledTradeColor.get());
                        continue;
                    }

                    if (Debug.get()) info("SELLING " + payItem.getHoverName().getString());

                    mc.player.connection.send(new ServerboundSelectTradePacket(num));
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
					
					if (payItem.is(Items.EMERALD) && payItem.getCount() > buyRule.value1) {
                        if (Debug.get())
                            info(offer.getResult().getHoverName().getString() + " too expensive " + payItem.getCount());
                        updateColor(TooExpensiveColor.get());
                        continue;
                    }

                    if (buyRule.value2 != -1) {
                        int currentCount = countItemInInventory(sellItem.getItem());
                        if (currentCount >= buyRule.value2) {
                            if (Debug.get())
                                info(sellItem.getHoverName().getString() + " limit reached: " + currentCount + "/" + buyRule.value2);
                            updateColor(limitReachedColor.get());
                            continue;
                        }
                    }

                    if (Debug.get()) {
                        info("BUYING " + sellItem.getHoverName().getString());
                    }

                    if (offer.isOutOfStock()) {
                        updateColor(disabledTradeColor.get());
                        continue;
                    }

                    mc.player.connection.send(new ServerboundSelectTradePacket(num));
                    InvUtils.shiftClick().slotId(2);
                    tradeHappened = true;
                }
            }

            if (tradeHappened) updateColor(yesPurchase.get());

            ticker_close = 0;
            pendingClose = true;

        }
    }

    private boolean entityCheck(Entity entity) {
        if (entity.equals(mc.player) || entity.equals(mc.getCameraEntity())) return false;
        if ((entity instanceof LivingEntity livingEntity && livingEntity.isDeadOrDying()) || !entity.isAlive()) return false;

        AABB hitbox = entity.getBoundingBox();
        if (!PlayerUtils.isWithin(
                Mth.clamp(mc.player.getX(), hitbox.minX, hitbox.maxX),
                Mth.clamp(mc.player.getY(), hitbox.minY, hitbox.maxY),
                Mth.clamp(mc.player.getZ(), hitbox.minZ, hitbox.maxZ),
                range.get()
        )) return false;

        return entity instanceof Villager;
    }

    public void lookAtVillager(Vec3 playerPos, Vec3 villagerPos) {
        Vec3 direction = villagerPos.subtract(playerPos).normalize();
        double yaw = Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90;
        double pitch = Math.toDegrees(-Math.atan2(direction.y, Math.sqrt(direction.x * direction.x + direction.z * direction.z)));

        yaw += (Math.random() - 0.5) * 2;
        pitch += (Math.random() - 0.5) * 2;

        Rotations.rotate(yaw, pitch);
    }

    public void VillagerInteract(Entity villager) {
        Vec3 playerPos = mc.player.getEyePosition();
        Vec3 villagerPos = villager.getEyePosition();
        EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(mc.player, playerPos, villagerPos, villager.getBoundingBox(), Entity::isPickable, playerPos.distanceToSqr(villagerPos));

        if (entityHitResult == null) {
            if (Debug.get()) info("Raycast didn't find a target");
            interactWith(villager, hitResultFor(villager));
            return;
        }

        if (rotateToVillager.get()) lookAtVillager(playerPos, villagerPos);

        InteractionResult actionResult = interactWith(villager, entityHitResult);
        if (!actionResult.consumesAction()) {
            if (Debug.get()) info("Action wasn't accepted");
            interactWith(villager, hitResultFor(villager));
        }
    }

    private EntityHitResult hitResultFor(Entity entity) {
        return new EntityHitResult(entity, entity.getBoundingBox().getCenter());
    }

    /**
     * 26.1 no longer has separate interactEntity / interactEntityAtLocation methods, everything goes through
     * one interact call that always takes the hit location.
     */
    private InteractionResult interactWith(Entity entity, EntityHitResult hitResult) {
        return mc.gameMode.interact(mc.player, entity, hitResult, InteractionHand.MAIN_HAND);
    }

    /** Is there a villager in range that the aura would interact with right now? */
    private boolean hasClickableVillager() {
        movementCheckTargets.clear();
        TargetUtils.getList(movementCheckTargets, this::entityCheck, priority.get(), maxTargets.get());

        for (Entity target : movementCheckTargets) {
            if (VillagerCooldown.containsKey(target)) continue;
            if (shouldSkipVillager(target)) continue;

            return true;
        }

        return false;
    }

    /**
     * Decides every tick whether the player is allowed to move.
     * <p>
     * The lock only ever engages while there is a villager the aura would actually click - freezing the player
     * in an empty field is never useful. On top of that either the countdown window
     * ({@code Ticks-to-cancel-movement}) or {@code Cancel-movement-near-villager} has to ask for it.
     */
    private void updateMovementLock() {
        if (mc.player == null || !aura.get() || !cancelMovement.get()) {
            MovementControl.stop();
            return;
        }

        if (!hasClickableVillager()) {
            MovementControl.stop();
            return;
        }

        int ticksRemaining = ticks_to_wait.get() - ticker;
        boolean interactionClose = ticksRemaining >= 0 && ticksRemaining <= ticks_to_cancel_movement.get();

        if (cancelMovementNearVillager.get() || interactionClose) MovementControl.freeze();
        else MovementControl.stop();
    }

    // NEW METHOD: Pre-check whether to click the villager
    private boolean shouldSkipVillager(Entity target) {
        if (!(target instanceof Villager villager)) return false;

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

    /**
     * An entity that is still rendered with the default color never made it past
     * {@link #syncing_func(MerchantMenu)}, which means the interaction was lost. Dropping it from the
     * cooldown map makes the aura interact with it again on this very tick.
     */
    private boolean shouldRefreshCooldown(Entity target) {
        if (!refreshUnsynced.get()) return false;

        Tuple<Integer, Color> entry = VillagerCooldown.get(target);
        if (entry == null) return false;
        if (!entry.getB().equals(defaultColor.get())) return false;
        if (entry.getA() < refreshDelay.get()) return false;

        int tries = refreshCount.getOrDefault(target, 0);
        if (tries >= maxRefreshes.get()) return false;

        refreshCount.put(target, tries + 1);
        if (Debug.get()) info("Villager never synced, forcing a cooldown refresh (try " + (tries + 1) + "/" + maxRefreshes.get() + ")");
        return true;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;
        if (!mc.player.isAlive() || PlayerUtils.getGameMode() == GameType.SPECTATOR) return;

        // Inventory manipulation always wins: while an action runs the aura stands down completely.
        if (aura.get() && invManager.tick()) {
            ticker = 0;
            ticker_close = 0;
            pendingClose = false;
            return;
        }

        ticker++;

        // Called on every tick: the lock has to be re-applied (and released) continuously.
        updateMovementLock();

        if (ticker < ticks_to_wait.get()) return;
        ticker = 0;

        if (mc.player.containerMenu instanceof MerchantMenu) {
            if (!Close.get()) return;
            if (!pendingClose) return;

            if (++ticker_close < ticks_to_close.get()) return;
            ticker_close = 0;
            pendingClose = false;
            InvHelper.closeScreen();
            mc.player.getInventory().tick();
            return;
        }
        if (!aura.get()) return;

        targets.clear();
        TargetUtils.getList(targets, this::entityCheck, priority.get(), maxTargets.get());

        for (Entity targett : targets) {
            if (VillagerCooldown.containsKey(targett)) {
                if (!shouldRefreshCooldown(targett)) continue;
                VillagerCooldown.remove(targett);
            }

            if (shouldSkipVillager(targett)) {
                if (Debug.get()) info("Skipped villager (pre-check: no valid trades possible).");
                VillagerCooldown.put(targett, new Tuple<>(0, limitReachedColor.get()));
                continue;
            }

            remember_entity = targett;
            VillagerCooldown.put(targett, new Tuple<>(0, defaultColor.get()));
            if (rotateToVillager.get()) {
                VillagerInteract(targett);
            } else {
                if (Debug.get()) info("Rotation disabled, interacting without rotating.");
                InteractionResult actionResult = interactWith(targett, hitResultFor(targett));
                if (!actionResult.consumesAction() && Debug.get()) {
                    info("Aura interaction was not accepted.");
                }
            }
            break;
        }

        for (Map.Entry<Entity, Tuple<Integer, Color>> e : new HashMap<>(VillagerCooldown).entrySet()) {
            int time = e.getValue().getA();
            Color clr = e.getValue().getB();
            if (time > forget.get()) {
                VillagerCooldown.remove(e.getKey());
                refreshCount.remove(e.getKey());
            } else {
                VillagerCooldown.replace(e.getKey(), new Tuple<>(time + 1, clr));
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!render.get()) return;

        for (Map.Entry<Entity, Tuple<Integer, Color>> e : new HashMap<>(VillagerCooldown).entrySet()) {
            Entity entity = e.getKey();
            drawBoundingBox(event, entity, e.getValue().getB());
        }
    }

    private void drawBoundingBox(Render3DEvent event, Entity entity, Color color) {
        Color lineColor = new Color();
        Color sideColor = new Color();

        lineColor.set(color);
        sideColor.set(color).a((int) (sideColor.a * fillOpacity.get()));

        double x = Mth.lerp(event.tickDelta, entity.xOld, entity.getX()) - entity.getX();
        double y = Mth.lerp(event.tickDelta, entity.yOld, entity.getY()) - entity.getY();
        double z = Mth.lerp(event.tickDelta, entity.zOld, entity.getZ()) - entity.getZ();

        AABB box = entity.getBoundingBox();
        event.renderer.box(x + box.minX, y + box.minY, z + box.minZ, x + box.maxX, y + box.maxY, z + box.maxZ, sideColor, lineColor, ShapeMode.Both, 0);
    }
}