package com.TradeAura.addon.modules;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class TradeData {
    public static final Map<Item, Set<String>> VILLAGER_BUYS = createBuyMap();
    public static final Map<Item, Set<String>> VILLAGER_SELLS = createSellMap();

    private TradeData() {}

    private static Map<Item, Set<String>> createBuyMap() {
        Map<Item, Set<String>> map = new HashMap<>();

        map.put(Items.WHEAT, Set.of("farmer"));
        map.put(Items.POTATO, Set.of("farmer"));
        map.put(Items.CARROT, Set.of("farmer"));
        map.put(Items.BEETROOT, Set.of("farmer"));
        map.put(Items.PUMPKIN, Set.of("farmer"));
        map.put(Items.MELON_SLICE, Set.of("farmer"));

        map.put(Items.PAPER, Set.of("cartographer", "librarian"));
        map.put(Items.GLASS_PANE, Set.of("cartographer"));
        map.put(Items.COMPASS, Set.of("cartographer"));
        map.put(Items.BOOK, Set.of("librarian"));
        map.put(Items.INK_SAC, Set.of("librarian"));

        map.put(Items.STRING, Set.of("fletcher", "fisherman"));
        map.put(Items.FEATHER, Set.of("fletcher"));
        map.put(Items.STICK, Set.of("fletcher"));
        map.put(Items.FLINT, Set.of("fletcher", "toolsmith", "weaponsmith"));
        map.put(Items.COD, Set.of("fisherman"));
        map.put(Items.SALMON, Set.of("fisherman"));

        map.put(Items.RAW_IRON, Set.of("armorer", "weaponsmith", "toolsmith"));
        map.put(Items.IRON_INGOT, Set.of("armorer", "weaponsmith", "toolsmith"));
        map.put(Items.RAW_GOLD, Set.of("armorer"));
        map.put(Items.GOLD_INGOT, Set.of("cleric"));
        map.put(Items.RAW_COPPER, Set.of("armorer"));
        map.put(Items.COAL, Set.of("armorer", "weaponsmith", "toolsmith", "butcher", "fisherman"));
        map.put(Items.DIAMOND, Set.of("armorer", "weaponsmith", "toolsmith"));
        map.put(Items.LAPIS_LAZULI, Set.of("cleric", "armorer"));
        map.put(Items.REDSTONE, Set.of("cleric"));

        map.put(Items.ROTTEN_FLESH, Set.of("cleric"));
        map.put(Items.RABBIT_FOOT, Set.of("cleric"));
        map.put(Items.GLASS_BOTTLE, Set.of("cleric"));
        map.put(Items.NETHER_WART, Set.of("cleric"));

        map.put(Items.LEATHER, Set.of("leatherworker"));
        map.put(Items.RABBIT_HIDE, Set.of("leatherworker"));
        map.put(Items.TURTLE_SCUTE, Set.of("leatherworker"));

        map.put(Items.MUTTON, Set.of("butcher"));
        map.put(Items.PORKCHOP, Set.of("butcher"));
        map.put(Items.CHICKEN, Set.of("butcher"));
        map.put(Items.BEEF, Set.of("butcher"));

        map.put(Items.CLAY_BALL, Set.of("mason"));
        map.put(Items.STONE, Set.of("mason"));
        map.put(Items.GRANITE, Set.of("mason"));
        map.put(Items.ANDESITE, Set.of("mason"));
        map.put(Items.DIORITE, Set.of("mason"));
        map.put(Items.NETHERRACK, Set.of("mason"));
        map.put(Items.BLACKSTONE, Set.of("mason"));
        map.put(Items.END_STONE, Set.of("mason"));
        map.put(Items.TERRACOTTA, Set.of("mason"));

        map.put(Items.WHITE_WOOL, Set.of("shepherd"));
        map.put(Items.BLACK_WOOL, Set.of("shepherd"));
        map.put(Items.GRAY_WOOL, Set.of("shepherd"));
        map.put(Items.BROWN_WOOL, Set.of("shepherd"));
        map.put(Items.WHITE_DYE, Set.of("shepherd"));
        map.put(Items.BLACK_DYE, Set.of("shepherd"));
        map.put(Items.BROWN_DYE, Set.of("shepherd"));
        map.put(Items.BLUE_DYE, Set.of("shepherd"));

        return Map.copyOf(map);
    }

    private static Map<Item, Set<String>> createSellMap() {
        Map<Item, Set<String>> map = new HashMap<>();

        map.put(Items.BREAD, Set.of("farmer"));
        map.put(Items.APPLE, Set.of("farmer"));
        map.put(Items.PUMPKIN_PIE, Set.of("farmer"));
        map.put(Items.COOKIE, Set.of("farmer"));
        map.put(Items.CAKE, Set.of("farmer"));
        map.put(Items.GOLDEN_CARROT, Set.of("farmer"));
        map.put(Items.GLISTERING_MELON_SLICE, Set.of("farmer"));
        map.put(Items.SUSPICIOUS_STEW, Set.of("farmer"));

        map.put(Items.GLASS, Set.of("librarian"));
        map.put(Items.ENCHANTED_BOOK, Set.of("librarian"));
        map.put(Items.BOOKSHELF, Set.of("librarian", "cleric"));
        map.put(Items.NAME_TAG, Set.of("librarian"));
        map.put(Items.CLOCK, Set.of("librarian"));
        map.put(Items.LANTERN, Set.of("librarian"));
        map.put(Items.COMPASS, Set.of("librarian"));

        map.put(Items.MAP, Set.of("cartographer"));
        map.put(Items.FILLED_MAP, Set.of("cartographer"));
        map.put(Items.ITEM_FRAME, Set.of("cartographer"));
        map.put(Items.CARTOGRAPHY_TABLE, Set.of("cartographer"));
        map.put(Items.WHITE_BANNER, Set.of("cartographer", "shepherd"));
        map.put(Items.RED_BANNER, Set.of("cartographer", "shepherd"));
        map.put(Items.BLUE_BANNER, Set.of("cartographer", "shepherd"));

        map.put(Items.ARROW, Set.of("fletcher"));
        map.put(Items.BOW, Set.of("fletcher"));
        map.put(Items.CROSSBOW, Set.of("fletcher"));
        map.put(Items.FLINT_AND_STEEL, Set.of("fletcher"));
        map.put(Items.TIPPED_ARROW, Set.of("fletcher"));

        map.put(Items.COOKED_COD, Set.of("fisherman"));
        map.put(Items.COOKED_SALMON, Set.of("fisherman"));
        map.put(Items.FISHING_ROD, Set.of("fisherman"));
        map.put(Items.COD_BUCKET, Set.of("fisherman"));
        map.put(Items.CAMPFIRE, Set.of("fisherman"));

        map.put(Items.IRON_HELMET, Set.of("armorer"));
        map.put(Items.IRON_CHESTPLATE, Set.of("armorer"));
        map.put(Items.IRON_LEGGINGS, Set.of("armorer"));
        map.put(Items.IRON_BOOTS, Set.of("armorer"));
        map.put(Items.SHIELD, Set.of("armorer"));
        map.put(Items.CHAINMAIL_HELMET, Set.of("armorer"));
        map.put(Items.CHAINMAIL_CHESTPLATE, Set.of("armorer"));
        map.put(Items.CHAINMAIL_LEGGINGS, Set.of("armorer"));
        map.put(Items.CHAINMAIL_BOOTS, Set.of("armorer"));
        map.put(Items.DIAMOND_HELMET, Set.of("armorer"));
        map.put(Items.DIAMOND_CHESTPLATE, Set.of("armorer"));
        map.put(Items.DIAMOND_LEGGINGS, Set.of("armorer"));
        map.put(Items.DIAMOND_BOOTS, Set.of("armorer"));
        map.put(Items.BELL, Set.of("armorer", "weaponsmith", "toolsmith"));

        map.put(Items.STONE_AXE, Set.of("weaponsmith", "toolsmith"));
        map.put(Items.STONE_SWORD, Set.of("weaponsmith"));
        map.put(Items.IRON_SWORD, Set.of("weaponsmith"));
        map.put(Items.IRON_AXE, Set.of("weaponsmith", "toolsmith"));
        map.put(Items.DIAMOND_SWORD, Set.of("weaponsmith"));
        map.put(Items.DIAMOND_AXE, Set.of("weaponsmith", "toolsmith"));

        map.put(Items.STONE_PICKAXE, Set.of("toolsmith"));
        map.put(Items.STONE_SHOVEL, Set.of("toolsmith"));
        map.put(Items.STONE_HOE, Set.of("toolsmith"));
        map.put(Items.IRON_PICKAXE, Set.of("toolsmith"));
        map.put(Items.IRON_SHOVEL, Set.of("toolsmith"));
        map.put(Items.IRON_HOE, Set.of("toolsmith"));
        map.put(Items.DIAMOND_PICKAXE, Set.of("toolsmith"));
        map.put(Items.DIAMOND_SHOVEL, Set.of("toolsmith"));
        map.put(Items.DIAMOND_HOE, Set.of("toolsmith"));

        map.put(Items.LEATHER_HELMET, Set.of("leatherworker"));
        map.put(Items.LEATHER_CHESTPLATE, Set.of("leatherworker"));
        map.put(Items.LEATHER_LEGGINGS, Set.of("leatherworker"));
        map.put(Items.LEATHER_BOOTS, Set.of("leatherworker"));
        map.put(Items.SADDLE, Set.of("leatherworker"));
        map.put(Items.LEATHER_HORSE_ARMOR, Set.of("leatherworker"));
        map.put(Items.WOLF_ARMOR, Set.of("leatherworker"));

        map.put(Items.ENDER_PEARL, Set.of("cleric"));
        map.put(Items.GLOWSTONE, Set.of("cleric"));
        map.put(Items.EXPERIENCE_BOTTLE, Set.of("cleric"));
        map.put(Items.REDSTONE, Set.of("cleric"));
        map.put(Items.LAPIS_LAZULI, Set.of("cleric"));
        map.put(Items.ENDER_EYE, Set.of("cleric"));

        map.put(Items.COOKED_MUTTON, Set.of("butcher"));
        map.put(Items.COOKED_PORKCHOP, Set.of("butcher"));
        map.put(Items.COOKED_CHICKEN, Set.of("butcher"));
        map.put(Items.COOKED_BEEF, Set.of("butcher"));
        map.put(Items.RABBIT_STEW, Set.of("butcher"));

        map.put(Items.BRICK, Set.of("mason"));
        map.put(Items.QUARTZ, Set.of("mason"));
        map.put(Items.DRIPSTONE_BLOCK, Set.of("mason"));
        map.put(Items.CHISELED_STONE_BRICKS, Set.of("mason"));
        map.put(Items.POLISHED_GRANITE, Set.of("mason"));
        map.put(Items.POLISHED_ANDESITE, Set.of("mason"));
        map.put(Items.POLISHED_DIORITE, Set.of("mason"));
        map.put(Items.QUARTZ_BLOCK, Set.of("mason"));
        map.put(Items.QUARTZ_PILLAR, Set.of("mason"));

        map.put(Items.SHEARS, Set.of("shepherd"));
        map.put(Items.WHITE_BED, Set.of("shepherd"));
        map.put(Items.BLACK_BED, Set.of("shepherd"));
        map.put(Items.RED_BED, Set.of("shepherd"));
        map.put(Items.BLUE_BED, Set.of("shepherd"));
        map.put(Items.WHITE_CARPET, Set.of("shepherd"));
        map.put(Items.BLACK_CARPET, Set.of("shepherd"));
        map.put(Items.PAINTING, Set.of("shepherd"));

        return Map.copyOf(map);
    }
}
