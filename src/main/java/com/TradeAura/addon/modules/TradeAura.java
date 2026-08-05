package com.TradeAura.addon.modules;

import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
/// ^^^ MODULE BASIC IMPORTS ^^^


import net.minecraft.network.packet.c2s.play.*;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;

import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.orbit.EventHandler;

import java.lang.reflect.Field;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.util.ActionResult;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;

import net.minecraft.client.MinecraftClient;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;

import java.util.List;
import net.minecraft.item.Item;
import java.util.Arrays;

import net.minecraft.client.gui.widget.TextFieldWidget;

import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.entity.passive.MerchantEntity;
import org.apache.commons.lang3.reflect.FieldUtils;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.village.*;
import net.minecraft.village.MerchantInventory;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

import meteordevelopment.meteorclient.utils.player.SlotUtils;
import net.minecraft.screen.ScreenHandler;

import net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket;
import net.minecraft.entity.Entity;

import java.util.ArrayList;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.utils.entity.SortPriority;

import net.minecraft.world.GameMode;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import net.minecraft.entity.passive.VillagerEntity;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Box;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.math.Vec3d;
import meteordevelopment.meteorclient.utils.player.Rotations;

import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.events.render.Render3DEvent;

import net.minecraft.util.Pair;
import meteordevelopment.meteorclient.renderer.ShapeMode;

// todo: delete useless imports
import it.unimi.dsi.fastutil.objects.ObjectIntImmutablePair;
import meteordevelopment.meteorclient.events.entity.player.InteractEntityEvent;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.utils.misc.ISerializable;
import meteordevelopment.meteorclient.utils.misc.Names;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.play.SetTradeOffersS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.VillagerProfession;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;


public class TradeAura extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAura = settings.createGroup("Aura");
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

    /*
     * НОВОЕ: конфигурация по каждому предмету вместо глобальных MaxPrice / MaxSellPrice / items.
     * Формат одной строки: item_id;maxBuyPrice;maxBarterPrice;buyLimit;minSellPrice;maxSellQuantity
     *
     *   item_id        - id предмета (например minecraft:diamond)
     *
     *   -- покупка (вы получаете item_id, платите изумрудами или бартером) --
     *   maxBuyPrice    - максимальная цена в ИЗУМРУДАХ, которую вы готовы заплатить за item_id
     *   maxBarterPrice - максимальное количество НЕ-изумрудных предметов, которое вы готовы отдать
     *                    за item_id (редкий случай бартерных трейдов)
     *   buyLimit       - максимальное количество item_id в инвентаре, после которого покупка
     *                    прекращается. -1 = без лимита.
     *
     *   -- продажа (вы отдаёте item_id жителю, получаете изумруды) --
     *   minSellPrice   - минимальное количество изумрудов, за которое вы готовы продать item_id.
     *                    -1 = никогда не продавать этот предмет.
     *   maxSellQuantity - максимальное количество item_id, которое вы готовы отдать за 1 трейд
     *                    (у жителей меняется именно ЭТО число, а не число изумрудов — они почти
     *                    всегда дают 1 изумруд, но могут просить и 8, и 36 предметов за него).
     *                    -1 = без ограничения по количеству.
     *
     * Пример: minecraft:diamond;40;64;-1;-1;-1        (покупаем алмазы, не продаём)
     *         minecraft:rotten_flesh;0;0;-1;1;16       (продаём гнилую плоть от 1 изумруда,
     *                                                    но не больше 16 штук за трейд)
     */
    private final Setting<List<String>> itemConfigs = sgGeneral.add(new StringListSetting.Builder()
            .name("item-configs")
            .description("Формат: item_id;maxBuyPrice;maxBarterPrice;buyLimit;minSellPrice;maxSellQuantity (-1 = без лимита / не продавать). Пример: minecraft:diamond;40;64;-1;-1;-1")
            .defaultValue(new ArrayList<>())
            .onChanged(list -> parseConfigs())
            .build()
    );


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
            .description("How many entities to load at once at most. (Just a memory stuff, idk it seems like a code smell though...)")
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
            .defaultValue(new SettingColor(0, 0, 0))
            .visible(render::get)
            .build()
    );

    private final Setting<SettingColor> noEmeraldColor = sgRender.add(new ColorSetting.Builder()
            .name("no-emerald-color")
            .description("Color for no emeralds in inventory")
            .defaultValue(new SettingColor(0, 0, 255))
            .visible(render::get)
            .build()
    );
	
	private final Setting<SettingColor> noSellItemsColor = sgRender.add(new ColorSetting.Builder()
            .name("no-sell-item-color")
            .description("Color for no sellable items in inventory")
            .defaultValue(new SettingColor(255, 255, 255))
            .visible(render::get)
            .build()
    );

    private final Setting<SettingColor> noTradesColor = sgRender.add(new ColorSetting.Builder()
            .name("no-trades")
            .description("Color for no villager trades in trade list ")
            .defaultValue(new SettingColor(255, 0, 0))
            .visible(render::get)
            .build()
    );

    private final Setting<SettingColor> disabledTradeColor = sgRender.add(new ColorSetting.Builder()
            .name("disabled-trade-color")
            .description("Color for a trade on a cooldown")
            .defaultValue(new SettingColor(255, 255, 0))
            .visible(render::get)
            .build()
    );

    private final Setting<SettingColor> TooExpensiveColor = sgRender.add(new ColorSetting.Builder()
            .name("too-expensive-color")
            .description("Color for a high priced trade")
            .defaultValue(new SettingColor(255, 0, 255))
            .visible(render::get)
            .build()
    );

    // НОВОЕ: отдельный цвет для случая "лимит по количеству предмета в инвентаре достигнут"
    private final Setting<SettingColor> limitReachedColor = sgRender.add(new ColorSetting.Builder()
            .name("limit-reached-color")
            .description("Color for when the per-item inventory limit has been reached")
            .defaultValue(new SettingColor(255, 165, 0))
            .visible(render::get)
            .build()
    );

    private final Setting<SettingColor> yesPurchase = sgRender.add(new ColorSetting.Builder()
            .name("purchase-color")
            .description("Color for a successfull trade")
            .defaultValue(new SettingColor(0, 255, 0))
            .visible(render::get)
            .build()
    );

    public TradeAura(Category cat) {
        super(cat, "Trade-Aura", "Trades with villagers for you");
    }


    /// Pair<Integer, String> pair = new Pair<>(1, "One");
    private final List<Entity> targets = new ArrayList<>();
    private final Map<Entity, Pair<Integer, Color>> VillagerCooldown = new HashMap<>();

    private int ticker = 0;
    private int ticker_close = 0;
    // НОВОЕ: становится true только после того, как syncing_func реально обработал трейды
    // для текущего окна. Закрытие окна (см. onTick) ждёт этого флага вместо сырого счётчика
    // от момента открытия экрана.
    private boolean pendingClose = false;

    // НОВОЕ: распарсенный конфиг по предметам, item -> настройки
    private final Map<Item, ItemConfig> parsedConfigs = new HashMap<>();

    private static class ItemConfig {
        final int maxBuyPrice;
        final int maxBarterPrice;
        final int buyLimit;        // -1 = без лимита
        final int minSellPrice;    // -1 = не продавать этот предмет
        final int maxSellQuantity; // -1 = без ограничения по количеству отдаваемых предметов

        ItemConfig(int maxBuyPrice, int maxBarterPrice, int buyLimit, int minSellPrice, int maxSellQuantity) {
            this.maxBuyPrice = maxBuyPrice;
            this.maxBarterPrice = maxBarterPrice;
            this.buyLimit = buyLimit;
            this.minSellPrice = minSellPrice;
            this.maxSellQuantity = maxSellQuantity;
        }
    }

    // Парсит настройку item-configs в удобную мапу Item -> ItemConfig.
    // Ошибки парсинга ВСЕГДА выводятся в чат (не только при Debug), так как без этого
    // тихо сломанный конфиг выглядит как "модуль не работает".
    private void parseConfigs() {
        parsedConfigs.clear();

        for (String rawLine : itemConfigs.get()) {
            if (rawLine == null) continue;
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split(";");
            if (parts.length != 6) {
                info("[TradeAura] Неверная строка item-config (нужно 6 полей через ';': item_id;maxBuyPrice;maxBarterPrice;buyLimit;minSellPrice;maxSellQuantity): '" + line + "'");
                continue;
            }

            try {
                String idStr = parts[0].trim();
                Identifier id = Identifier.tryParse(idStr);
                if (id == null) {
                    info("[TradeAura] Не удалось распознать item id: '" + idStr + "'");
                    continue;
                }

                if (!Registries.ITEM.containsId(id)) {
                    info("[TradeAura] Неизвестный предмет: '" + idStr + "'");
                    continue;
                }

                Item item = Registries.ITEM.get(id);

                int maxBuy = Integer.parseInt(parts[1].trim());
                int maxBarter = Integer.parseInt(parts[2].trim());
                int limit = Integer.parseInt(parts[3].trim());
                int minSell = Integer.parseInt(parts[4].trim());
                int maxSellQty = Integer.parseInt(parts[5].trim());

                parsedConfigs.put(item, new ItemConfig(maxBuy, maxBarter, limit, minSell, maxSellQty));
            } catch (NumberFormatException e) {
                info("[TradeAura] Не число в строке item-config: '" + line + "'");
            }
        }

        if (Debug.get()) info("[TradeAura] Загружено конфигов предметов: " + parsedConfigs.size());
    }

    // НОВОЕ: считает суммарное количество предмета в инвентаре игрока (хотбар + основной инвентарь)
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
        parseConfigs();
    }

    private MerchantScreenHandler MSH_g;

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (!(event.screen instanceof MerchantScreen)) return;

		//info("open screen");
        if (CancelEvent.get()) event.cancel();
    }

    // Imaging being forced to find race conditions in multithreaded game? Yeah, it sucks
    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.packet instanceof SetTradeOffersS2CPacket)) return;

        //if (Debug.get()) info("packet1");
        mc.execute(() -> {
            if (mc.player == null) return;
            if (!(mc.player.currentScreenHandler instanceof MerchantScreenHandler MSH)) return;

            //if (Debug.get()) info("packet2");
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
        if (parsedConfigs.isEmpty()) {
            info("[TradeAura] item-configs пуст — нечего покупать/продавать. Добавь строки вида 'minecraft:diamond;40;64;-1;-1;-1' в настройке item-configs.");
        }

        try {
            /// АХТУНГ! https://maven.fabricmc.net/docs/yarn-23w51b+build.4/net/minecraft/screen/MerchantScreenHandler.html#merchant
            if (!(FieldUtils.readField(MSH, "field_7863", true) instanceof Merchant merc)) return;
            FindItemResult resultEm = InvUtils.find(Items.EMERALD);
            if (!resultEm.found()) {
                if (Debug.get()) {
                    info("no emerald");
                }
                if (Close.get()) mc.player.closeHandledScreen();

                updateColor(noEmeraldColor.get());

                return;
            }

            TradeOfferList Offers = MSH.getRecipes();
            int num = -1;
            updateColor(noTradesColor.get());

            boolean tradeHappened = false;
            for (TradeOffer offer : Offers) {
                num++;

                ItemStack sellItem = offer.getSellItem();          // то, что вы ПОЛУЧАЕТЕ от трейда
                ItemStack payItem = offer.getDisplayedFirstBuyItem(); // то, что вы ОТДАЁТЕ за трейд

                // НОВОЕ: villager "sell"-трейды (вы отдаёте предмет, получаете изумруды) выглядят так,
                // что sellItem - это изумруд, а payItem - реальный отдаваемый предмет. Раньше конфиг
                // всегда искался по sellItem.getItem(), из-за чего продажа была в принципе невозможна
                // (изумруд не мог совпасть ни с одним ключом конфига).
                boolean isSellingToVillager = sellItem.isOf(Items.EMERALD) && !payItem.isOf(Items.EMERALD);

                if (isSellingToVillager) {
                    
                    ItemConfig sellConfig = parsedConfigs.get(payItem.getItem());
                    if (sellConfig == null || sellConfig.minSellPrice < 0) continue; // продажа этого предмета не настроена/отключена

                    if (sellItem.getCount() < sellConfig.minSellPrice) {
                        if (Debug.get())
                            info(payItem.getName().getString() + " sell price too low: " + sellItem.getCount() + " < " + sellConfig.minSellPrice);
                        updateColor(TooExpensiveColor.get());
                        continue;
                    }

                    // НОВОЕ: контролируем именно количество ОТДАВАЕМОГО предмета за трейд —
                    // у жителей эмеральдов почти всегда 1, а вот сколько предметов они просят
                    // за него, как раз и меняется (может быть и 8, и 36).
                    if (sellConfig.maxSellQuantity != -1 && payItem.getCount() > sellConfig.maxSellQuantity) {
                        if (Debug.get())
                            info(payItem.getName().getString() + " sell quantity too high: " + payItem.getCount() + " > " + sellConfig.maxSellQuantity);
                        updateColor(TooExpensiveColor.get());
                        continue;
                    }

                    // НОВОЕ: Проверяем, есть ли у игрока в инвентаре достаточно предметов для продажи
                    int availableCount = countItemInInventory(payItem.getItem());
                    if (availableCount < payItem.getCount()) {
                        if (Debug.get())
                            info("Недостаточно " + payItem.getName().getString() + " для продажи (есть " + availableCount + ", нужно " + payItem.getCount() + ")");
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

                // Обычная покупка: вы получаете sellItem, платите payItem (изумруды или бартер)
                ItemConfig buyConfig = parsedConfigs.get(sellItem.getItem());
                if (buyConfig != null) {

                    if (payItem.isOf(Items.EMERALD) && payItem.getCount() > buyConfig.maxBuyPrice) {
                        if (Debug.get())
                            info(offer.getSellItem().toString() + " too expensive " + payItem.getCount());
                        updateColor(TooExpensiveColor.get());
                        continue;
                    }

                    if (!payItem.isOf(Items.EMERALD) && payItem.getCount() > buyConfig.maxBarterPrice) {
                        if (Debug.get()) info(payItem.toString() + " too high to barter " + payItem.getCount());
                        updateColor(TooExpensiveColor.get());
                        continue;
                    }

                    // НОВОЕ: проверка лимита количества предмета в инвентаре перед покупкой
                    if (buyConfig.buyLimit != -1) {
                        int currentCount = countItemInInventory(sellItem.getItem());
                        if (currentCount >= buyConfig.buyLimit) {
                            if (Debug.get())
                                info(sellItem.getItem().toString() + " limit reached: " + currentCount + "/" + buyConfig.buyLimit);
                            updateColor(limitReachedColor.get());
                            continue;
                        }
                    }

                    if (Debug.get()) {
                        info("BUYING " + sellItem.getName());
                    }

                    if (offer.isDisabled()) {
                        updateColor(disabledTradeColor.get());
                        continue;
                    }

                    mc.player.networkHandler.sendPacket(new SelectMerchantTradeC2SPacket(num));
                    InvUtils.shiftClick().slotId(2);
                    tradeHappened = true;

                }
                /// https://maven.fabricmc.net/docs/yarn-20w51a+build.9/net/minecraft/village/TradeOffer.html#depleteBuyItems(net.minecraft.item.ItemStack,net.minecraft.item.ItemStack)


            }

            /// Хороший вопрос на тему того, как стоит раставить приоритеты цветов...
            if (tradeHappened) updateColor(yesPurchase.get());

            // НОВОЕ: трейды обработаны — теперь можно начинать отсчёт до закрытия окна (см. onTick)
            ticker_close = 0;
            pendingClose = true;

        } catch (IllegalAccessException e) {
            info("IAE ex");
            // НОВОЕ: если рефлексия упала, окно иначе никогда не помечается на закрытие
            // и виснет открытым до тех пор, пока игрок не отлетит и не вернётся.
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

        // Add some random noise to prevent anticheat detection
        yaw += (Math.random() - 0.5) * 2;
        pitch += (Math.random() - 0.5) * 2;

        Rotations.rotate(yaw, pitch);
    }

    public void VillagerInteract(Entity villager) {
        
		Vec3d playerPos = mc.player.getEyePos();
		Vec3d villagerPos = villager.getEyePos();
		EntityHitResult entityHitResult = ProjectileUtil.raycast(mc.player, playerPos, villagerPos, villager.getBoundingBox(), Entity::canHit, playerPos.squaredDistanceTo(villagerPos));
		if (entityHitResult == null) {
			// Raycast didn't find villager entity?
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

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!mc.player.isAlive() || PlayerUtils.getGameMode() == GameMode.SPECTATOR) return;

        if (++ticker < ticks_to_wait.get()) return;
        ticker = 0;

        if (mc.player.currentScreenHandler instanceof MerchantScreenHandler) {
            // НОВОЕ: закрытие окна теперь запускается не от момента открытия экрана,
            // а от момента, когда syncing_func реально обработал трейды (см. pendingClose).
            // Раньше окно могло закрыться ДО того, как придёт SetTradeOffersS2CPacket
            // и успеет пройти покупка — отсюда "ничего не происходит" / перепутанные жители.
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