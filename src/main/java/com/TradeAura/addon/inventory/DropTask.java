package com.TradeAura.addon.inventory;

import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import net.minecraft.item.ItemStack;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * 2.1 Drop Excess Items.
 * <p>
 * Drops {@code total - leave} items of a single rule. The direction is relative to the player, so the task
 * rotates first and throws inside the rotation callback: meteor sends the rotation with the movement packet of
 * the very same tick, which means the server already knows where we look when the throw arrives.
 */
public class DropTask extends InvTask {
    private final ItemRule rule;
    private final int amount;

    private boolean rotationRequested;
    private boolean dropped;

    public DropTask(InventoryManager mgr, ItemRule rule, int amount) {
        super(mgr);
        this.rule = rule;
        this.amount = amount;
    }

    @Override
    public String id() {
        return "drop";
    }

    @Override
    protected Status run() {
        if (mc.player == null) return fail("no player");

        // Never drop with a container open, the throw would be routed through that screen handler.
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
            mc.player.closeHandledScreen();
            return timedOut() ? fail("could not close the open screen") : Status.RUNNING;
        }

        if (!rotationRequested) {
            rotationRequested = true;

            float[] rotation = s.dropDirection.get().getRotation(mc.player.getYaw());
            Rotations.rotate(rotation[0], rotation[1], 100, this::dropItems);

            return Status.RUNNING;
        }

        if (dropped) return Status.DONE;
        return timedOut() ? fail("drop was never executed") : Status.RUNNING;
    }

    private void dropItems() {
        dropped = true;
        if (mc.player == null) return;

        int remaining = amount;
        int clicks = 0;

        while (remaining > 0 && clicks < 128) {
            int index = InvHelper.findIndex(rule.items);
            if (index == -1) break;

            int slotId = SlotUtils.indexToId(index);
            if (slotId == -1) break;

            ItemStack stack = mc.player.getInventory().getStack(index);
            int count = stack.getCount();

            if (count <= remaining) {
                // Whole stack in one packet.
                InvUtils.drop().slotId(slotId);
                remaining -= count;
                clicks++;
            }
            else {
                // Exact remainder, one item per packet.
                for (int i = 0; i < remaining && clicks < 128; i++) {
                    InvUtils.dropOne().slotId(slotId);
                    clicks++;
                }
                remaining = 0;
            }
        }

        debug("Dropped " + (amount - remaining) + " item(s) " + s.dropDirection.get().name().toLowerCase());
    }
}
