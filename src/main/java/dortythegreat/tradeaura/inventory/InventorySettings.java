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

import meteordevelopment.meteorclient.settings.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Every setting of the "Inventory manipulation" tab.
 * <p>
 * Kept out of the module itself so {@code TradeAura} stays readable. The per item limits live in
 * {@link ItemRule} lists that are edited through the module GUI (same style as the buy / sell rules) and
 * serialized by the module.
 */
public class InventorySettings {
    public final SettingGroup group;

    // Not final on purpose: the visibility lambdas of the settings above reference toggles that are only built
    // further down in the constructor, and a blank final field may not be read before it is assigned - not even
    // from a lambda that runs long after the constructor finished.

    // General

    public Setting<Boolean> enabled;
    public Setting<Integer> actionDelay;
    public Setting<Integer> actionTimeout;
    public Setting<Integer> failCooldown;
    public Setting<Integer> maxChain;
    public Setting<Boolean> cancelScreens;
    public Setting<AmountMode> amountMode;
    public Setting<Integer> clicksPerTick;

    // 2.1 Drop excess items

    public Setting<Boolean> dropEnabled;
    public Setting<DropDirection> dropDirection;
    public final List<ItemRule> dropRules = new ArrayList<>();

    // 2.2 Compress emeralds

    public Setting<Boolean> compressEnabled;
    public Setting<Integer> compressTrigger;
    public Setting<Integer> compressLeave;
    public Setting<Double> craftingTableRange;
    public Setting<Integer> maxCrafts;

    // 2.3 Decompress emeralds

    public Setting<Boolean> decompressEnabled;
    public Setting<Integer> decompressTrigger;
    public Setting<Integer> decompressLeave;

    // Glass panes (6 glass -> 16 panes)

    public Setting<Boolean> glassPanesEnabled;
    public Setting<Integer> glassTrigger;
    public Setting<Integer> glassLeave;

    // 2.4 Dump to shulker

    public Setting<Boolean> dumpEnabled;
    public final List<ItemRule> dumpRules = new ArrayList<>();

    // 2.5 Refill from shulker

    public Setting<Boolean> refillEnabled;
    public final List<ItemRule> refillRules = new ArrayList<>();

    // Shulker handling

    public Setting<Boolean> shulkerAutoTool;
    public Setting<Boolean> rotate;
    public Setting<Integer> shulkerPickupTicks;
    public Setting<Integer> transferBatch;
    public Setting<Boolean> walkToDrop;
    public Setting<Boolean> lockMovement;

    public InventorySettings(SettingGroup group, Runnable onVisibilityChanged) {
        this.group = group;

        enabled = group.add(new BoolSetting.Builder()
            .name("inventory-manipulation")
            .description("Master switch of every inventory trigger below. Each trigger interrupts the aura while it runs.")
            .defaultValue(false)
            .onChanged(v -> onVisibilityChanged.run())
            .build()
        );

        actionDelay = group.add(new IntSetting.Builder()
            .name("action-delay")
            .description("Ticks to wait between two inventory operations, gives the server time to confirm the previous one.")
            .defaultValue(2)
            .min(0)
            .sliderMax(20)
            .visible(enabled::get)
            .build()
        );

        actionTimeout = group.add(new IntSetting.Builder()
            .name("action-timeout")
            .description("How many ticks a single step (placing, opening, breaking, ...) may take before the action is aborted.")
            .defaultValue(60)
            .min(10)
            .sliderMax(200)
            .visible(enabled::get)
            .build()
        );

        failCooldown = group.add(new IntSetting.Builder()
            .name("fail-cooldown")
            .description("Ticks a trigger is skipped after it failed, stops the module from retrying a hopeless action forever.")
            .defaultValue(100)
            .min(0)
            .sliderMax(600)
            .visible(enabled::get)
            .build()
        );

        maxChain = group.add(new IntSetting.Builder()
            .name("max-chained-actions")
            .description("Triggers may chain into each other (low emeralds -> craft -> low blocks -> refill). This caps how many actions may run back to back before the module assumes the settings contradict each other.")
            .defaultValue(12)
            .min(1)
            .sliderMax(50)
            .visible(enabled::get)
            .build()
        );

        cancelScreens = group.add(new BoolSetting.Builder()
            .name("cancel-screens")
            .description("Do not render the crafting / shulker screens the module opens.")
            .defaultValue(true)
            .visible(enabled::get)
            .build()
        );

        rotate = group.add(new BoolSetting.Builder()
            .name("rotate")
            .description("Rotate towards the crafting table / shulker box the module interacts with.")
            .defaultValue(true)
            .visible(enabled::get)
            .build()
        );

        amountMode = group.add(new EnumSetting.Builder<AmountMode>()
            .name("amount-mode")
            .description("ToLimit moves everything down/up to the leave value in one go. TriggerMinusLeave moves exactly (trigger - leave) per action, which may need several chained actions.")
            .defaultValue(AmountMode.ToLimit)
            .visible(enabled::get)
            .build()
        );

        clicksPerTick = group.add(new IntSetting.Builder()
            .name("clicks-per-tick")
            .description("How many inventory clicks the crafting triggers may send within one tick. Higher is faster, lower is gentler on anticheats.")
            .defaultValue(64)
            .min(8)
            .sliderRange(8, 256)
            .visible(enabled::get)
            .build()
        );

        // Drop

        dropEnabled = group.add(new BoolSetting.Builder()
            .name("drop-excess-items")
            .description("Throws away everything above the limit configured in the Drop Rules table.")
            .defaultValue(false)
            .visible(enabled::get)
            .onChanged(v -> onVisibilityChanged.run())
            .build()
        );

        dropDirection = group.add(new EnumSetting.Builder<DropDirection>()
            .name("drop-direction")
            .description("Where to throw the items, relative to the player.")
            .defaultValue(DropDirection.Forward)
            .visible(() -> enabled.get() && dropEnabled.get())
            .build()
        );

        // Compress

        compressEnabled = group.add(new BoolSetting.Builder()
            .name("compress-emeralds")
            .description("Crafts excess emeralds into emerald blocks. Needs a crafting table in range, otherwise the trigger does not fire.")
            .defaultValue(false)
            .visible(enabled::get)
            .build()
        );

        compressTrigger = group.add(new IntSetting.Builder()
            .name("compress-trigger")
            .description("Fires when the emerald count is above this value.")
            .defaultValue(128)
            .min(1)
            .sliderMax(2304)
            .visible(() -> enabled.get() && compressEnabled.get())
            .build()
        );

        compressLeave = group.add(new IntSetting.Builder()
            .name("compress-leave")
            .description("How many emeralds stay in the inventory, the rest (trigger - leave) is crafted into blocks.")
            .defaultValue(64)
            .min(0)
            .sliderMax(2304)
            .visible(() -> enabled.get() && compressEnabled.get())
            .build()
        );

        craftingTableRange = group.add(new DoubleSetting.Builder()
            .name("crafting-table-range")
            .description("How far away a crafting table may be for the table recipes to fire.")
            .defaultValue(4.0)
            .min(1)
            .sliderRange(1, 5)
            .visible(() -> enabled.get() && (compressEnabled.get() || glassPanesEnabled.get()))
            .build()
        );

        maxCrafts = group.add(new IntSetting.Builder()
            .name("max-crafts-per-action")
            .description("Upper limit of crafts a single crafting action performs. A grid slot cannot hold more than a stack, so 64 is the maximum.")
            .defaultValue(CraftTask.MAX_CRAFTS)
            .min(1)
            .sliderRange(1, CraftTask.MAX_CRAFTS)
            .visible(() -> enabled.get() && (compressEnabled.get() || decompressEnabled.get() || glassPanesEnabled.get()))
            .build()
        );

        // Decompress

        decompressEnabled = group.add(new BoolSetting.Builder()
            .name("decompress-emeralds")
            .description("Crafts emerald blocks back into emeralds using the 2x2 grid, no crafting table needed. Does not fire without blocks in the inventory.")
            .defaultValue(false)
            .visible(enabled::get)
            .build()
        );

        decompressTrigger = group.add(new IntSetting.Builder()
            .name("decompress-trigger")
            .description("Fires when the emerald count is below this value.")
            .defaultValue(32)
            .min(0)
            .sliderMax(2304)
            .visible(() -> enabled.get() && decompressEnabled.get())
            .build()
        );

        decompressLeave = group.add(new IntSetting.Builder()
            .name("decompress-target")
            .description("Emerald count to restore, the missing amount (target - trigger) is crafted from blocks.")
            .defaultValue(128)
            .min(1)
            .sliderMax(2304)
            .visible(() -> enabled.get() && decompressEnabled.get())
            .build()
        );

        // Glass panes

        glassPanesEnabled = group.add(new BoolSetting.Builder()
            .name("craft-glass-panes")
            .description("Crafts excess glass into glass panes (6 -> 16). Needs a crafting table in range, otherwise the trigger does not fire.")
            .defaultValue(false)
            .visible(enabled::get)
            .build()
        );

        glassTrigger = group.add(new IntSetting.Builder()
            .name("glass-trigger")
            .description("Fires when the glass count is above this value.")
            .defaultValue(64)
            .min(1)
            .sliderMax(2304)
            .visible(() -> enabled.get() && glassPanesEnabled.get())
            .build()
        );

        glassLeave = group.add(new IntSetting.Builder()
            .name("glass-leave")
            .description("How much glass stays in the inventory, the rest is crafted into panes.")
            .defaultValue(0)
            .min(0)
            .sliderMax(2304)
            .visible(() -> enabled.get() && glassPanesEnabled.get())
            .build()
        );

        // Dump

        dumpEnabled = group.add(new BoolSetting.Builder()
            .name("dump-to-shulker")
            .description("Places a shulker box, stores everything above the limit configured in the Dump Rules table, breaks it and picks it back up.")
            .defaultValue(false)
            .visible(enabled::get)
            .onChanged(v -> onVisibilityChanged.run())
            .build()
        );

        // Refill

        refillEnabled = group.add(new BoolSetting.Builder()
            .name("refill-from-shulker")
            .description("Same as Dump, but pulls the missing items out of the shulker box.")
            .defaultValue(false)
            .visible(enabled::get)
            .onChanged(v -> onVisibilityChanged.run())
            .build()
        );

        shulkerAutoTool = group.add(new BoolSetting.Builder()
            .name("shulker-auto-tool")
            .description("Swap to the fastest tool in the hotbar before breaking the shulker box.")
            .defaultValue(true)
            .visible(() -> enabled.get() && (dumpEnabled.get() || refillEnabled.get()))
            .build()
        );

        transferBatch = group.add(new IntSetting.Builder()
            .name("transfers-per-tick")
            .description("How many stacks are moved into / out of the shulker box per tick. Low values make a big dump take forever.")
            .defaultValue(12)
            .min(1)
            .sliderRange(1, 36)
            .visible(() -> enabled.get() && (dumpEnabled.get() || refillEnabled.get()))
            .build()
        );

        lockMovement = group.add(new BoolSetting.Builder()
            .name("lock-movement-while-placed")
            .description("Suppress your own movement input from the moment the shulker box is placed until it is broken again. Only the input is blocked, movement packets keep being sent, so nothing desyncs.")
            .defaultValue(true)
            .visible(() -> enabled.get() && (dumpEnabled.get() || refillEnabled.get()))
            .build()
        );

        walkToDrop = group.add(new BoolSetting.Builder()
            .name("walk-to-dropped-shulker")
            .description("Walk over to the broken shulker box if it landed out of pickup range instead of leaving it behind.")
            .defaultValue(true)
            .visible(() -> enabled.get() && (dumpEnabled.get() || refillEnabled.get()))
            .build()
        );

        shulkerPickupTicks = group.add(new IntSetting.Builder()
            .name("shulker-pickup-ticks")
            .description("How long to wait for (and walk towards) the broken shulker box before giving up on it.")
            .defaultValue(80)
            .min(0)
            .sliderMax(200)
            .visible(() -> enabled.get() && (dumpEnabled.get() || refillEnabled.get()))
            .build()
        );
    }
}
