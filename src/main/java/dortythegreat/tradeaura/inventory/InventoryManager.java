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

import dortythegreat.tradeaura.modules.TradeAura;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Runs the "Inventory manipulation" triggers.
 * <p>
 * Only one action runs at a time and every action takes as many ticks as it needs; the aura is interrupted for
 * the whole duration ({@link #tick()} returns {@code true} while busy). After an action finished the triggers are
 * evaluated again, which is what makes them chain into each other - the "few emeralds -> craft emeralds from
 * blocks -> few blocks -> refill blocks from the shulker" case from the spec falls out of that automatically.
 * <p>
 * Because a chain can also be an infinite loop when two triggers undo each other, the number of actions that run
 * back to back is capped by {@code max-chained-actions}; hitting the cap pauses the whole tab for a while and
 * prints what the conflict most likely is.
 */
public class InventoryManager {
    private final TradeAura module;
    private final InventorySettings s;

    private InvTask task;
    private int delay;
    private int chain;
    private int pausedTicks;

    private final Map<String, Integer> cooldowns = new HashMap<>();

    public InventoryManager(TradeAura module, InventorySettings settings) {
        this.module = module;
        this.s = settings;
    }

    public InventorySettings settings() {
        return s;
    }

    public boolean isBusy() {
        return task != null;
    }

    public void debug(String message) {
        if (module.isDebug()) module.info("[Inventory] " + message);
    }

    public void warn(String message) {
        module.warning("[Inventory] " + message);
    }

    /**
     * @return true when an inventory action is running and the aura has to stand down this tick
     */
    public boolean tick() {
        if (!s.enabled.get() || mc.player == null || mc.level == null) {
            if (task != null) reset();
            return false;
        }

        tickCooldowns();

        if (pausedTicks > 0) {
            pausedTicks--;
            return false;
        }

        // Running action
        if (task != null) {
            // Only the shulker task manages the movement lock, everything else must not inherit one.
            if (!(task instanceof ShulkerTask)) MovementControl.stop();

            InvTask.Status status = task.tick();
            if (status == InvTask.Status.RUNNING) return true;

            if (status == InvTask.Status.FAILED) {
                warn("'" + task.id() + "' aborted: " + task.failReason());
                cooldowns.put(task.id(), s.failCooldown.get());
            }
            else {
                debug("'" + task.id() + "' finished");
            }

            task.cleanup();
            task = null;
            delay = s.actionDelay.get();
            return true;
        }

        if (delay > 0) {
            delay--;
            return true;
        }

        // Pick the next action
        InvTask next = evaluate();
        if (next == null) {
            chain = 0;
            return false;
        }

        // Nothing is allowed to start while another screen (e.g. the trading screen) is still open.
        // Checked before the chain counter so waiting for the screen does not eat the chain budget.
        if (mc.player.containerMenu != mc.player.inventoryMenu) {
            InvHelper.closeScreen();
            return true;
        }

        if (++chain > s.maxChain.get()) {
            chain = 0;
            pausedTicks = 200;
            warn("Too many chained actions, the triggers seem to undo each other. Paused for 10s.");
            reportConflicts();
            return false;
        }

        task = next;
        debug("Starting '" + task.id() + "'");
        return true;
    }

    public void reset() {
        if (task != null) {
            task.cleanup();
            task = null;
        }

        // Never leave a movement key held down because the module got switched off mid action.
        MovementControl.stop();

        cooldowns.clear();
        delay = 0;
        chain = 0;
        pausedTicks = 0;
    }

    // Trigger evaluation, in the order of the spec

    private InvTask evaluate() {
        InvTask task;

        if ((task = evaluateDrop()) != null) return task;
        if ((task = evaluateCompress()) != null) return task;
        if ((task = evaluateGlassPanes()) != null) return task;
        if ((task = evaluateDecompress()) != null) return task;
        if ((task = evaluateDump()) != null) return task;
        if ((task = evaluateRefill()) != null) return task;

        return null;
    }

    private InvTask evaluateDrop() {
        if (!s.dropEnabled.get() || onCooldown("drop")) return null;

        for (ItemRule rule : s.dropRules) {
            if (!rule.isValid()) continue;

            int total = InvHelper.count(rule.items);
            if (total <= rule.trigger) continue;

            int amount = amountAbove(rule, total);
            if (amount <= 0) continue;

            return new DropTask(this, rule, amount);
        }

        return null;
    }

    private InvTask evaluateCompress() {
        if (!s.compressEnabled.get() || onCooldown("compress")) return null;

        return evaluateSurplusCraft(CraftRecipe.CompressEmeralds, s.compressTrigger.get(), s.compressLeave.get());
    }

    private InvTask evaluateGlassPanes() {
        if (!s.glassPanesEnabled.get() || onCooldown(CraftRecipe.GlassPanes.id())) return null;

        return evaluateSurplusCraft(CraftRecipe.GlassPanes, s.glassTrigger.get(), s.glassLeave.get());
    }

    /**
     * Shared logic of every "too many of X -> craft them into Y" trigger (emerald compression, glass panes).
     */
    private InvTask evaluateSurplusCraft(CraftRecipe recipe, int trigger, int leave) {
        int available = InvHelper.count(recipe.input());
        if (available <= trigger) return null;

        int excess = s.amountMode.get() == AmountMode.ToLimit ? available - leave : trigger - leave;

        int crafts = Math.min(excess / recipe.inputPerCraft(), maxCrafts());
        if (crafts <= 0) return null;

        // The results have to fit somewhere. The ingredients free up slots while crafting, so this is a
        // conservative estimate - whatever does not fit is pulled back out of the grid afterwards.
        crafts = Math.min(crafts, InvHelper.freeSpaceFor(recipe.output()) / recipe.outputPerCraft());
        if (crafts <= 0) {
            debug(recipe.id() + " skipped: no room for the result");
            return null;
        }

        // A crafting table is mandatory for anything bigger than 2x2, without one the trigger does not fire.
        if (recipe.needsTable() && CraftTask.findCraftingTable(s.craftingTableRange.get()) == null) {
            debug(recipe.id() + " skipped: no crafting table in range");
            return null;
        }

        return new CraftTask(this, recipe, crafts);
    }

    private InvTask evaluateDecompress() {
        if (!s.decompressEnabled.get() || onCooldown("decompress")) return null;

        int emeralds = InvHelper.count(Items.EMERALD);
        if (emeralds >= s.decompressTrigger.get()) return null;

        int missing = s.amountMode.get() == AmountMode.ToLimit
            ? s.decompressLeave.get() - emeralds
            : s.decompressLeave.get() - s.decompressTrigger.get();
        if (missing <= 0) return null;

        int crafts = Math.min((missing + 8) / 9, maxCrafts());

        // No blocks in the inventory -> the trigger does not fire.
        int blocks = InvHelper.count(Items.EMERALD_BLOCK);
        crafts = Math.min(crafts, blocks);
        if (crafts <= 0) return null;

        crafts = Math.min(crafts, InvHelper.freeSpaceFor(Items.EMERALD) / 9);
        if (crafts <= 0) {
            debug("Decompress skipped: no room for the emeralds");
            return null;
        }

        return new CraftTask(this, CraftRecipe.DecompressEmeralds, crafts);
    }

    private InvTask evaluateDump() {
        if (!s.dumpEnabled.get() || onCooldown("dump")) return null;

        List<ShulkerTask.Entry> entries = new ArrayList<>();

        for (ItemRule rule : s.dumpRules) {
            if (!rule.isValid()) continue;

            int total = InvHelper.count(rule.items);
            if (total <= rule.trigger) continue;

            int amount = amountAbove(rule, total);
            if (amount > 0) entries.add(new ShulkerTask.Entry(rule, amount));
        }

        if (entries.isEmpty()) return null;

        if (!ShulkerTask.hasUsableShulker(ShulkerTask.Mode.Dump, entries)) {
            debug("Dump skipped: no shulker box with free slots");
            return null;
        }

        return new ShulkerTask(this, ShulkerTask.Mode.Dump, entries);
    }

    private InvTask evaluateRefill() {
        if (!s.refillEnabled.get() || onCooldown("refill")) return null;

        List<ShulkerTask.Entry> entries = new ArrayList<>();

        for (ItemRule rule : s.refillRules) {
            if (!rule.isValid()) continue;

            int total = InvHelper.count(rule.items);
            if (total >= rule.trigger) continue;

            int amount = s.amountMode.get() == AmountMode.ToLimit
                ? rule.leave - total
                : rule.leave - rule.trigger;

            amount = Math.min(amount, spaceFor(rule.items));
            if (amount > 0) entries.add(new ShulkerTask.Entry(rule, amount));
        }

        if (entries.isEmpty()) return null;

        if (!ShulkerTask.hasUsableShulker(ShulkerTask.Mode.Refill, entries)) {
            debug("Refill skipped: no shulker box holding the wanted items");
            return null;
        }

        return new ShulkerTask(this, ShulkerTask.Mode.Refill, entries);
    }

    // Helpers

    private int maxCrafts() {
        return Math.min(s.maxCrafts.get(), CraftTask.MAX_CRAFTS);
    }

    private int amountAbove(ItemRule rule, int total) {
        int amount = s.amountMode.get() == AmountMode.ToLimit ? total - rule.leave : rule.trigger - rule.leave;
        return Math.max(0, Math.min(amount, total));
    }

    private int spaceFor(List<Item> items) {
        int space = 0;
        for (Item item : items) space = Math.max(space, InvHelper.freeSpaceFor(item));
        return space;
    }

    private boolean onCooldown(String id) {
        return cooldowns.containsKey(id);
    }

    private void tickCooldowns() {
        Iterator<Map.Entry<String, Integer>> it = cooldowns.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<String, Integer> entry = it.next();
            if (entry.getValue() <= 1) it.remove();
            else entry.setValue(entry.getValue() - 1);
        }
    }

    /**
     * Points at the settings that most likely make two triggers fight each other. Called when the chain limit is
     * hit and once when the module is enabled.
     */
    public void reportConflicts() {
        if (!s.enabled.get()) return;

        if (s.compressEnabled.get() && s.decompressEnabled.get() && s.compressLeave.get() < s.decompressTrigger.get()) {
            warn("compress-leave (" + s.compressLeave.get() + ") is below decompress-trigger (" + s.decompressTrigger.get() + "), those two will undo each other.");
        }

        if (s.dumpEnabled.get() && s.refillEnabled.get()) {
            for (ItemRule dump : s.dumpRules) {
                for (ItemRule refill : s.refillRules) {
                    if (!sharesItem(dump, refill)) continue;
                    if (dump.leave >= refill.leave) continue;

                    warn("A Dump rule keeps fewer items than a Refill rule wants for the same item, those two will undo each other.");
                    return;
                }
            }
        }

        if (s.dropEnabled.get() && s.refillEnabled.get()) {
            for (ItemRule drop : s.dropRules) {
                for (ItemRule refill : s.refillRules) {
                    if (!sharesItem(drop, refill)) continue;

                    warn("The same item is configured for Drop and Refill, it will be pulled out of the shulker just to be thrown away.");
                    return;
                }
            }
        }
    }

    private boolean sharesItem(ItemRule a, ItemRule b) {
        for (Item item : a.items) {
            if (b.items.contains(item)) return true;
        }
        return false;
    }
}
