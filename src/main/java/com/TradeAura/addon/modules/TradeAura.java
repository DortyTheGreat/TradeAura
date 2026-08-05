package com.TradeAura.addon.modules;

import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.renderer.ShapeMode;
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
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
import net.minecraft.world.GameMode;
import org.apache.commons.lang3.reflect.FieldUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * NEW: per-item configuration instead of global MaxPrice / MaxSellPrice / items.
     * Format of one string: item_id;maxBuyPrice;maxBarterPrice;buyLimit;minSellPrice;maxSellQuantity
     *
     *   item_id        - item id (e.g. minecraft:diamond)
     *
     *   -- buying (you receive item_id, pay with emeralds or barter) --
     *   maxBuyPrice    - maximum price in EMERALDS you are willing to pay for item_id
     *   maxBarterPrice - maximum amount of NON-emerald items you are willing to give
     *                    for item_id (rare barter trade case)
     *   buyLimit       - maximum amount of item_id in inventory before buying stops. -1 = no limit.
     *
     *   -- selling (you give item_id to the villager, get emeralds) --
     *   minSellPrice   - minimum amount of emeralds for which you are willing to sell item_id.
     *                    -1 = never sell this item.
     *   maxSellQuantity - maximum amount of item_id you are willing to give per 1 trade
     *                    (villagers change THIS number, not the number of emeralds — they almost
     *                    always give 1 emerald, but can ask for 8 or 36 items for it).
     *                    -1 = no limit on the quantity.
     *
     * Example: minecraft:diamond;40;64;-1;-1;-1        (buy diamonds, don't sell)
     *          minecraft:rotten_flesh;0;0;-1;1;16       (sell rotten flesh for 1 emerald,
     *                                                    but no more than 16 items per trade)
     */
    private final Setting<List<String>> itemConfigs = sgGeneral.add(new StringListSetting.Builder()
            .name("item-configs")
            .description("Format: item_id;maxBuyPrice;maxBarterPrice;buyLimit;minSellPrice;maxSellQuantity (-1 = no limit / do not sell). Example: minecraft:diamond;40;64;-1;-1;-1")
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

    // NEW: separate color for the "item inventory limit reached" case
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

    private final List<Entity> targets = new ArrayList<>();
    private final Map<Entity, Pair<Integer, Color>> VillagerCooldown = new HashMap<>();

    private int ticker = 0;
    private int ticker_close = 0;
    // NEW: becomes true only after syncing_func has actually processed the trades
    // for the current window. Window closing (see onTick) waits for this flag instead of a raw counter
    // from the moment the screen was opened.
    private boolean pendingClose = false;

    // NEW: parsed item config, item -> settings
    private final Map<Item, ItemConfig> parsedConfigs = new HashMap<>();

    private static class ItemConfig {
        final int maxBuyPrice;
        final int maxBarterPrice;
        final int buyLimit;        // -1 = no limit
        final int minSellPrice;    // -1 = do not sell this item
        final int maxSellQuantity; // -1 = no limit on the quantity of given items

        ItemConfig(int maxBuyPrice, int maxBarterPrice, int buyLimit, int minSellPrice, int maxSellQuantity) {
            this.maxBuyPrice = maxBuyPrice;
            this.maxBarterPrice = maxBarterPrice;
            this.buyLimit = buyLimit;
            this.minSellPrice = minSellPrice;
            this.maxSellQuantity = maxSellQuantity;
        }
    }

    // Parses the item-configs setting into a convenient Item -> ItemConfig map.
    // Parsing errors are ALWAYS printed to chat (not just in Debug), because without this
    // a silently broken config looks like "the module doesn't work".
    private void parseConfigs() {
        parsedConfigs.clear();

        for (String rawLine : itemConfigs.get()) {
            if (rawLine == null) continue;
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split(";");
            if (parts.length != 6) {
                info("[TradeAura] Invalid item-config string (requires 6 fields separated by ';': item_id;maxBuyPrice;maxBarterPrice;buyLimit;minSellPrice;maxSellQuantity): '" + line + "'");
                continue;
            }

            try {
                String idStr = parts[0].trim();
                Identifier id = Identifier.tryParse(idStr);
                if (id == null) {
                    info("[TradeAura] Failed to parse item id: '" + idStr + "'");
                    continue;
                }

                if (!Registries.ITEM.containsId(id)) {
                    info("[TradeAura] Unknown item: '" + idStr + "'");
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
                info("[TradeAura] Not a number in item-config string: '" + line + "'");
            }
        }

        if (Debug.get()) info("[TradeAura] Loaded item configs: " + parsedConfigs.size());
    }

    // NEW: counts the total amount of an item in the player's inventory (hotbar + main inventory)
    // Uses Meteor's InvUtils to avoid accessing private Minecraft fields (fixes IllegalAccessError)
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

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (!(event.screen instanceof MerchantScreen)) return;

        if (CancelEvent.get()) event.cancel();
    }

    // Imaging being forced to find race conditions in multithreaded game? Yeah, it sucks
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
        if (parsedConfigs.isEmpty()) {
            info("[TradeAura] item-configs is empty — nothing to buy/sell. Add lines like 'minecraft:diamond;40;64;-1;-1;-1' to the item-configs setting.");
        }

        try {
            /// WARNING! https://maven.fabricmc.net/docs/yarn-23w51b+build.4/net/minecraft/screen/MerchantScreenHandler.html#merchant
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

                ItemStack sellItem = offer.getSellItem();          // what you RECEIVE from the trade
                ItemStack payItem = offer.getDisplayedFirstBuyItem(); // what you PAY for the trade

                // NEW: villager "sell"-trades (you give an item, get emeralds) look like this:
                // sellItem is an emerald, and payItem is the actual given item. Previously the config
                // was always searched by sellItem.getItem(), which made selling impossible in principle
                // (emerald could not match any config key).
                boolean isSellingToVillager = sellItem.isOf(Items.EMERALD) && !payItem.isOf(Items.EMERALD);

                if (isSellingToVillager) {
                    
                    ItemConfig sellConfig = parsedConfigs.get(payItem.getItem());
                    if (sellConfig == null || sellConfig.minSellPrice < 0) continue; // selling this item is not configured/disabled

                    if (sellItem.getCount() < sellConfig.minSellPrice) {
                        if (Debug.get())
                            info(payItem.getName().getString() + " sell price too low: " + sellItem.getCount() + " < " + sellConfig.minSellPrice);
                        updateColor(TooExpensiveColor.get());
                        continue;
                    }

                    // NEW: we control exactly the quantity of the GIVEN item per trade —
                    // villagers almost always give 1 emerald, but how many items they ask for it
                    // is what changes (can be 8, or 36).
                    if (sellConfig.maxSellQuantity != -1 && payItem.getCount() > sellConfig.maxSellQuantity) {
                        if (Debug.get())
                            info(payItem.getName().getString() + " sell quantity too high: " + payItem.getCount() + " > " + sellConfig.maxSellQuantity);
                        updateColor(TooExpensiveColor.get());
                        continue;
                    }

                    // NEW: Check if the player has enough items in inventory to sell
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

                // Normal purchase: you get sellItem, pay payItem (emeralds or barter)
                ItemConfig buyConfig = parsedConfigs.get(sellItem.getItem());
                if (buyConfig != null) {

                    if (payItem.isOf(Items.EMERALD) && payItem.getCount() > buyConfig.maxBuyPrice) {
                        if (Debug.get())
                            info(offer.getSellItem().getName().getString() + " too expensive " + payItem.getCount());
                        updateColor(TooExpensiveColor.get());
                        continue;
                    }

                    if (!payItem.isOf(Items.EMERALD) && payItem.getCount() > buyConfig.maxBarterPrice) {
                        if (Debug.get()) info(payItem.getName().getString() + " too high to barter " + payItem.getCount());
                        updateColor(TooExpensiveColor.get());
                        continue;
                    }

                    // NEW: check item inventory limit before buying
                    if (buyConfig.buyLimit != -1) {
                        int currentCount = countItemInInventory(sellItem.getItem());
                        if (currentCount >= buyConfig.buyLimit) {
                            if (Debug.get())
                                info(sellItem.getName().getString() + " limit reached: " + currentCount + "/" + buyConfig.buyLimit);
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
                /// https://maven.fabricmc.net/docs/yarn-20w51a+build.9/net/minecraft/village/TradeOffer.html#depleteBuyItems(net.minecraft.item.ItemStack,net.minecraft.item.ItemStack)

            }

            /// A good question about how to arrange color priorities...
            if (tradeHappened) updateColor(yesPurchase.get());

            // NEW: trades processed — now we can start the countdown to close the window (see onTick)
            ticker_close = 0;
            pendingClose = true;

        } catch (IllegalAccessException e) {
            info("IAE ex");
            // NEW: if reflection failed, the window would otherwise never be marked for closing
            // and would hang open until the player flies away and returns.
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
            // NEW: window closing is now triggered not from the moment the screen opens,
            // but from the moment syncing_func actually processed the trades (see pendingClose).
            // Previously, the window could close BEFORE the SetTradeOffersS2CPacket arrived
            // and the purchase had time to go through — hence "nothing happens" / mixed up villagers.
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