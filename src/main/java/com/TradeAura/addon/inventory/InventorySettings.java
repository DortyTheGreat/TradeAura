package com.TradeAura.addon.inventory;

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

    // General

    public final Setting<Boolean> enabled;
    public final Setting<Integer> actionDelay;
    public final Setting<Integer> actionTimeout;
    public final Setting<Integer> failCooldown;
    public final Setting<Integer> maxChain;
    public final Setting<Boolean> cancelScreens;
    public final Setting<AmountMode> amountMode;

    // 2.1 Drop excess items

    public final Setting<Boolean> dropEnabled;
    public final Setting<DropDirection> dropDirection;
    public final List<ItemRule> dropRules = new ArrayList<>();

    // 2.2 Compress emeralds

    public final Setting<Boolean> compressEnabled;
    public final Setting<Integer> compressTrigger;
    public final Setting<Integer> compressLeave;
    public final Setting<Double> craftingTableRange;

    // 2.3 Decompress emeralds

    public final Setting<Boolean> decompressEnabled;
    public final Setting<Integer> decompressTrigger;
    public final Setting<Integer> decompressLeave;

    // 2.4 Dump to shulker

    public final Setting<Boolean> dumpEnabled;
    public final List<ItemRule> dumpRules = new ArrayList<>();

    // 2.5 Refill from shulker

    public final Setting<Boolean> refillEnabled;
    public final List<ItemRule> refillRules = new ArrayList<>();

    // Shulker handling

    public final Setting<Boolean> shulkerAutoTool;
    public final Setting<Boolean> rotate;
    public final Setting<Integer> shulkerPickupTicks;

    public InventorySettings(SettingGroup group, Runnable onVisibilityChanged) {
        this.group = group;

        enabled = group.add(new BoolSetting.Builder()
            .name("Inventory-manipulation")
            .description("Master switch of every inventory trigger below. Each trigger interrupts the aura while it runs.")
            .defaultValue(false)
            .onChanged(v -> onVisibilityChanged.run())
            .build()
        );

        actionDelay = group.add(new IntSetting.Builder()
            .name("Action-delay")
            .description("Ticks to wait between two inventory operations, gives the server time to confirm the previous one.")
            .defaultValue(2)
            .min(0)
            .sliderMax(20)
            .visible(enabled::get)
            .build()
        );

        actionTimeout = group.add(new IntSetting.Builder()
            .name("Action-timeout")
            .description("How many ticks a single step (placing, opening, breaking, ...) may take before the action is aborted.")
            .defaultValue(60)
            .min(10)
            .sliderMax(200)
            .visible(enabled::get)
            .build()
        );

        failCooldown = group.add(new IntSetting.Builder()
            .name("Fail-cooldown")
            .description("Ticks a trigger is skipped after it failed, stops the module from retrying a hopeless action forever.")
            .defaultValue(100)
            .min(0)
            .sliderMax(600)
            .visible(enabled::get)
            .build()
        );

        maxChain = group.add(new IntSetting.Builder()
            .name("Max-chained-actions")
            .description("Triggers may chain into each other (low emeralds -> craft -> low blocks -> refill). This caps how many actions may run back to back before the module assumes the settings contradict each other.")
            .defaultValue(12)
            .min(1)
            .sliderMax(50)
            .visible(enabled::get)
            .build()
        );

        cancelScreens = group.add(new BoolSetting.Builder()
            .name("Cancel-screens")
            .description("Do not render the crafting / shulker screens the module opens.")
            .defaultValue(true)
            .visible(enabled::get)
            .build()
        );

        rotate = group.add(new BoolSetting.Builder()
            .name("Rotate")
            .description("Rotate towards the crafting table / shulker box the module interacts with.")
            .defaultValue(true)
            .visible(enabled::get)
            .build()
        );

        amountMode = group.add(new EnumSetting.Builder<AmountMode>()
            .name("Amount-mode")
            .description("ToLimit moves everything down/up to the leave value in one go. TriggerMinusLeave moves exactly (trigger - leave) per action, which may need several chained actions.")
            .defaultValue(AmountMode.ToLimit)
            .visible(enabled::get)
            .build()
        );

        // Drop

        dropEnabled = group.add(new BoolSetting.Builder()
            .name("Drop-excess-items")
            .description("Throws away everything above the limit configured in the Drop Rules table.")
            .defaultValue(false)
            .visible(enabled::get)
            .onChanged(v -> onVisibilityChanged.run())
            .build()
        );

        dropDirection = group.add(new EnumSetting.Builder<DropDirection>()
            .name("Drop-direction")
            .description("Where to throw the items, relative to the player.")
            .defaultValue(DropDirection.Forward)
            .visible(() -> enabled.get() && dropEnabled.get())
            .build()
        );

        // Compress

        compressEnabled = group.add(new BoolSetting.Builder()
            .name("Compress-emeralds")
            .description("Crafts excess emeralds into emerald blocks. Needs a crafting table in range, otherwise the trigger does not fire.")
            .defaultValue(false)
            .visible(enabled::get)
            .build()
        );

        compressTrigger = group.add(new IntSetting.Builder()
            .name("Compress-trigger")
            .description("Fires when the emerald count is above this value.")
            .defaultValue(128)
            .min(1)
            .sliderMax(2304)
            .visible(() -> enabled.get() && compressEnabled.get())
            .build()
        );

        compressLeave = group.add(new IntSetting.Builder()
            .name("Compress-leave")
            .description("How many emeralds stay in the inventory, the rest (trigger - leave) is crafted into blocks.")
            .defaultValue(64)
            .min(0)
            .sliderMax(2304)
            .visible(() -> enabled.get() && compressEnabled.get())
            .build()
        );

        craftingTableRange = group.add(new DoubleSetting.Builder()
            .name("Crafting-table-range")
            .description("How far away a crafting table may be for Compress to fire.")
            .defaultValue(4.0)
            .min(1)
            .sliderRange(1, 5)
            .visible(() -> enabled.get() && compressEnabled.get())
            .build()
        );

        // Decompress

        decompressEnabled = group.add(new BoolSetting.Builder()
            .name("Decompress-emeralds")
            .description("Crafts emerald blocks back into emeralds using the 2x2 grid, no crafting table needed. Does not fire without blocks in the inventory.")
            .defaultValue(false)
            .visible(enabled::get)
            .build()
        );

        decompressTrigger = group.add(new IntSetting.Builder()
            .name("Decompress-trigger")
            .description("Fires when the emerald count is below this value.")
            .defaultValue(32)
            .min(0)
            .sliderMax(2304)
            .visible(() -> enabled.get() && decompressEnabled.get())
            .build()
        );

        decompressLeave = group.add(new IntSetting.Builder()
            .name("Decompress-target")
            .description("Emerald count to restore, the missing amount (target - trigger) is crafted from blocks.")
            .defaultValue(128)
            .min(1)
            .sliderMax(2304)
            .visible(() -> enabled.get() && decompressEnabled.get())
            .build()
        );

        // Dump

        dumpEnabled = group.add(new BoolSetting.Builder()
            .name("Dump-to-shulker")
            .description("Places a shulker box, stores everything above the limit configured in the Dump Rules table, breaks it and picks it back up.")
            .defaultValue(false)
            .visible(enabled::get)
            .onChanged(v -> onVisibilityChanged.run())
            .build()
        );

        // Refill

        refillEnabled = group.add(new BoolSetting.Builder()
            .name("Refill-from-shulker")
            .description("Same as Dump, but pulls the missing items out of the shulker box.")
            .defaultValue(false)
            .visible(enabled::get)
            .onChanged(v -> onVisibilityChanged.run())
            .build()
        );

        shulkerAutoTool = group.add(new BoolSetting.Builder()
            .name("Shulker-auto-tool")
            .description("Swap to the fastest tool in the hotbar before breaking the shulker box.")
            .defaultValue(true)
            .visible(() -> enabled.get() && (dumpEnabled.get() || refillEnabled.get()))
            .build()
        );

        shulkerPickupTicks = group.add(new IntSetting.Builder()
            .name("Shulker-pickup-ticks")
            .description("How long to wait for the broken shulker box to be picked back up.")
            .defaultValue(40)
            .min(0)
            .sliderMax(200)
            .visible(() -> enabled.get() && (dumpEnabled.get() || refillEnabled.get()))
            .build()
        );
    }
}
