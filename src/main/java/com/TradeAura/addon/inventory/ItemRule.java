package com.TradeAura.addon.inventory;

import net.minecraft.world.item.Item;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * A single user configured rule for the "Inventory manipulation" tab.
 * <p>
 * {@link #trigger} and {@link #leave} are interpreted differently depending on the trigger that owns the rule:
 * <ul>
 *     <li>Drop / Dump: fires when the total amount of {@link #items} in the inventory is <b>above</b> {@code trigger},
 *     the amount that gets removed is {@code total - leave}.</li>
 *     <li>Refill: fires when the total amount is <b>below</b> {@code trigger},
 *     the amount that gets pulled in is {@code leave - total}.</li>
 * </ul>
 */
public class ItemRule {
    public final List<Item> items = new ArrayList<>();
    public int trigger;
    public int leave;

    public ItemRule() {
        this(64, 32);
    }

    public ItemRule(int trigger, int leave) {
        this.trigger = trigger;
        this.leave = leave;
    }

    public boolean isValid() {
        return !items.isEmpty();
    }

    // NBT

    public static ListTag listToTag(List<ItemRule> rules) {
        ListTag list = new ListTag();

        for (ItemRule rule : rules) {
            CompoundTag ruleTag = new CompoundTag();
            ListTag itemsList = new ListTag();

            for (Item item : rule.items) {
                Identifier id = BuiltInRegistries.ITEM.getKey(item);
                if (id != null) itemsList.add(StringTag.valueOf(id.toString()));
            }

            ruleTag.put("items", itemsList);
            ruleTag.putInt("trigger", rule.trigger);
            ruleTag.putInt("leave", rule.leave);
            list.add(ruleTag);
        }

        return list;
    }

    public static void listFromTag(ListTag list, List<ItemRule> rules) {
        rules.clear();

        for (Tag element : list) {
            if (!(element instanceof CompoundTag ruleTag)) continue;

            ItemRule rule = new ItemRule();

            if (ruleTag.get("items") instanceof ListTag itemsList) {
                for (Tag itemElement : itemsList) {
                    if (!(itemElement instanceof StringTag itemString)) continue;

                    itemString.asString().ifPresent(idStr -> {
                        Identifier id = Identifier.tryParse(idStr);
                        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) rule.items.add(BuiltInRegistries.ITEM.getValue(id));
                    });
                }
            }

            rule.trigger = ruleTag.getIntOr("trigger", 64);
            rule.leave = ruleTag.getIntOr("leave", 32);
            rules.add(rule);
        }
    }
}
