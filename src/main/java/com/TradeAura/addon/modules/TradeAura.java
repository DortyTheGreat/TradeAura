package com.TradeAura.addon.modules;

import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
/// ^^^ MODULE BASIC IMPORTS ^^^


import static com.example.addon.Utils.*; 

import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;

import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;



import java.lang.reflect.Field;
import meteordevelopment.meteorclient.MeteorClient;

import meteordevelopment.meteorclient.settings.*;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;

import net.minecraft.client.MinecraftClient;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;


import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;


import java.util.List;
import meteordevelopment.meteorclient.settings.ItemListSetting;
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

import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.events.render.Render3DEvent;

import net.minecraft.util.Pair;
import meteordevelopment.meteorclient.renderer.ShapeMode;

import static com.example.addon.Utils.*; 

public class TradeAura extends Module {

	private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAura= settings.createGroup("Aura");
	private final SettingGroup sgRender= settings.createGroup("Render");
	
	
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
        .name("ticks_to_close")
        .description("time before closing villager window in ticks")
        .defaultValue(2)
        .min(0)
        .sliderMax(100)
		.visible(Close::get)
        .build()
    );
	
	
	
	private final Setting<Integer> MaxPrice = sgGeneral.add(new IntSetting.Builder()
        .name("Max-Price")
        .description("Max Price (in emeralds) for a deal")
        .defaultValue(65)
        .min(0)
        .sliderMax(65)
        .build()
    );
	
	private final Setting<List<Item>> items = sgGeneral.add(new ItemListSetting.Builder()
        .name("items")
        .description("Items you want to buy.")
        .defaultValue(Arrays.asList())
        .build()
    );
	
	private final Setting<Boolean> aura = sgAura.add(new BoolSetting.Builder()
            .name("Villager-Aura")
            .description("Clicks on Villagers in range of your vision")
            .defaultValue(false)
            .build()
    );
	
	private final Setting<Integer> ticks_to_wait = sgAura.add(new IntSetting.Builder()
        .name("ticks_to_wait")
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
	private final Map<Entity, Pair<Integer, Color> > VillagerCooldown = new HashMap<>();
	
	private int ticker = 0;
	private int ticker_close = 0;
	
	@Override
    public void onActivate() {
        targets.clear();
		VillagerCooldown.clear();
		ticker = 0;
		ticker_close = 0;
    }
	
	private MerchantScreenHandler MSH_g;
	
	@EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
		if (!(event.screen instanceof MerchantScreen)) return;
		if (!(mc.player.currentScreenHandler instanceof MerchantScreenHandler MSH)) return;
		MinecraftClient.getInstance().executeSync(() -> syncing_func(MSH));
		
		if (CancelEvent.get()) event.cancel();
	}
	
	
	
	private Entity remember_entity; 
	
	private void updateColor(Color clr){
		if (VillagerCooldown.containsKey(remember_entity)){
			Pair new_pair = VillagerCooldown.get(remember_entity);
			new_pair.setRight(clr);
			VillagerCooldown.replace(remember_entity, new_pair);
		}
	}
	
	private void syncing_func(MerchantScreenHandler MSH){
		try{
			/// АХТУНГ! https://maven.fabricmc.net/docs/yarn-23w51b+build.4/net/minecraft/screen/MerchantScreenHandler.html#merchant
			if ( !(FieldUtils.readField(MSH, "field_7863", true) instanceof Merchant merc) ) return;
			FindItemResult resultEm = InvUtils.find(Items.EMERALD); 
			if (!resultEm.found()){
				if (Debug.get()){info("no emerald");}
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
				//if (Debug.get()){info(String.format("Offer: %s", offer.getSellItem().toString()));}
				
				
				
				
				ItemStack sellItem = offer.getSellItem();
				if (items.get().contains(sellItem.getItem())){
					
					ItemStack emeralds = offer.getDisplayedFirstBuyItem(); /// на самом деле нужно сделать проверку на то, что второй итем - изумруд и что этот изумруд и т.д., но мне лень
				
					if (emeralds.isOf(Items.EMERALD) && emeralds.getCount() > MaxPrice.get()){
						if (Debug.get()) info(offer.getSellItem().toString() + " too expensive " + emeralds.getCount());
						updateColor(TooExpensiveColor.get());
						continue;
					}
					
					if (Debug.get()){info("BUYING " + sellItem.getName());}
					
					if (offer.isDisabled()){
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
			
			
			///ItemStack emeraldIS = mc.player.getInventory().getStack(resultEm.slot());
			//if (Close.get()) {setTimeout(() -> {mc.player.closeHandledScreen(); mc.player.getInventory().updateItems(); },time_before_close.get());}
			// По неким причинам закрытие окна в другой треде создаёт десинхронизацию между мышкой и игрой. -_- Dorty 15.04.2024
			
			
		}catch(IllegalAccessException e){
			info("IAE ex");
		}
		
	}
	
	
	private boolean entityCheck(Entity entity) {
        if (entity.equals(mc.player) || entity.equals(mc.cameraEntity)) return false;
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
	
	@EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!mc.player.isAlive() || PlayerUtils.getGameMode() == GameMode.SPECTATOR) return;
		
		if (++ticker < ticks_to_wait.get() ) return;
		ticker = 0;
		
		if (mc.player.currentScreenHandler instanceof MerchantScreenHandler) {
			if (!Close.get()) return;
			if (++ticker_close < ticks_to_close.get() ) return;
			ticker_close = 0;
			mc.player.closeHandledScreen(); mc.player.getInventory().updateItems();
			return;
		}
		if (!aura.get()) return;
        
		targets.clear();
		TargetUtils.getList(targets, this::entityCheck, priority.get(), maxTargets.get());
        
		
		
		for (Entity targett : targets){
			if (!VillagerCooldown.containsKey(targett)){
				remember_entity = targett;
				VillagerCooldown.put(targett, new Pair<>(0, defaultColor.get()) );
				mc.interactionManager.interactEntity(mc.player, targett, Hand.MAIN_HAND);
				
				break;
			}
				
		}
			
		for (Map.Entry<Entity, Pair<Integer, Color> > e : new HashMap<>(VillagerCooldown).entrySet()) {
			int time = e.getValue().getLeft();
			Color clr = e.getValue().getRight();
			if (time > forget.get()) {
				VillagerCooldown.remove(e.getKey());
			}else {
				VillagerCooldown.replace(e.getKey(), new Pair<>(time + 1, clr));
			}
		}
			
			
		
	}
	
	@EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!render.get()) return;
		
		for (Map.Entry<Entity, Pair<Integer, Color> > e : new HashMap<>(VillagerCooldown).entrySet()) {
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


