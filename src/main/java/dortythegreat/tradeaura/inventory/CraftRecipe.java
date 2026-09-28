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

package dortythegreat.tradeaura.inventory;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The recipes {@link CraftTask} can execute.
 * <p>
 * A recipe is described by the slots of the grid it uses and how many ingredients go into <b>each</b> of them.
 * The task always puts the same amount ({@code crafts}) into every used slot and then shift clicks the result
 * exactly once, which produces exactly {@code crafts} results.
 * <p>
 * Recipes that use more than the 2x2 grid need a crafting table, the rest works anywhere.
 */
public enum CraftRecipe {
    /** 9 emeralds -> 1 emerald block. */
    CompressEmeralds(Items.EMERALD, Items.EMERALD_BLOCK, 1, true, new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9}, "compress"),

    /** 1 emerald block -> 9 emeralds, fits into the 2x2 grid. */
    DecompressEmeralds(Items.EMERALD_BLOCK, Items.EMERALD, 9, false, new int[]{1}, "decompress"),

    /** 6 glass -> 16 glass panes, shaped as two full rows. */
    GlassPanes(Items.GLASS, Items.GLASS_PANE, 16, true, new int[]{1, 2, 3, 4, 5, 6}, "glass-panes");

    private final Item input;
    private final Item output;
    private final int outputPerCraft;
    private final boolean needsTable;
    private final int[] gridSlots;
    private final String id;

    CraftRecipe(Item input, Item output, int outputPerCraft, boolean needsTable, int[] gridSlots, String id) {
        this.input = input;
        this.output = output;
        this.outputPerCraft = outputPerCraft;
        this.needsTable = needsTable;
        this.gridSlots = gridSlots;
        this.id = id;
    }

    public Item input() {
        return input;
    }

    public Item output() {
        return output;
    }

    /** How many items of {@link #output()} a single craft produces. */
    public int outputPerCraft() {
        return outputPerCraft;
    }

    /** How many ingredients a single craft consumes in total. */
    public int inputPerCraft() {
        return gridSlots.length;
    }

    public boolean needsTable() {
        return needsTable;
    }

    /** Slot ids of the grid slots this recipe fills, result is always slot 0. */
    public int[] gridSlots() {
        return gridSlots;
    }

    /** Total number of grid slots of the screen this recipe runs in, used when clearing the grid. */
    public int gridSize() {
        return needsTable ? 9 : 4;
    }

    public String id() {
        return id;
    }
}
