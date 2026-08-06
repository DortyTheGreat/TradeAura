package com.TradeAura.addon.inventory;

import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

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

    public static NbtList listToTag(List<ItemRule> rules) {
        NbtList list = new NbtList();

        for (ItemRule rule : rules) {
            NbtCompound ruleTag = new NbtCompound();
            NbtList itemsList = new NbtList();

            for (Item item : rule.items) {
                Identifier id = Registries.ITEM.getId(item);
                if (id != null) itemsList.add(NbtString.of(id.toString()));
            }

            ruleTag.put("items", itemsList);
            ruleTag.putInt("trigger", rule.trigger);
            ruleTag.putInt("leave", rule.leave);
            list.add(ruleTag);
        }

        return list;
    }

    public static void listFromTag(NbtList list, List<ItemRule> rules) {
        rules.clear();

        for (NbtElement element : list) {
            if (!(element instanceof NbtCompound ruleTag)) continue;

            ItemRule rule = new ItemRule();

            if (ruleTag.get("items") instanceof NbtList itemsList) {
                for (NbtElement itemElement : itemsList) {
                    if (!(itemElement instanceof NbtString itemString)) continue;

                    itemString.asString().ifPresent(idStr -> {
                        Identifier id = Identifier.tryParse(idStr);
                        if (id != null && Registries.ITEM.containsId(id)) rule.items.add(Registries.ITEM.get(id));
                    });
                }
            }

            rule.trigger = ruleTag.getInt("trigger").orElse(64);
            rule.leave = ruleTag.getInt("leave").orElse(32);
            rules.add(rule);
        }
    }
}
