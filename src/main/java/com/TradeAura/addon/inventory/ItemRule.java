/*
 * TradeAura - Meteor Client addon for automated villager trading.
 * Copyright (C) 2026 DortyTheGreat
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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
