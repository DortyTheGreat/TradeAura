package com.TradeAura.addon.inventory;

import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * 2.2 Compress Emeralds and 2.3 Decompress Emeralds.
 * <p>
 * Both directions place an <b>exact</b> amount of ingredients into the grid and then shift click the result
 * exactly once. Because every grid slot holds exactly {@code crafts} ingredients, one shift click produces
 * exactly {@code crafts} results - no over crafting, which was the main problem of the shift click only version.
 * <p>
 * Compressing needs a crafting table (3x3), decompressing uses the 2x2 grid of the player screen handler and
 * therefore works anywhere. The screen is always closed again when the task finishes.
 */
public class CraftTask extends InvTask {
    public enum Mode {
        Compress,
        Decompress
    }

    private enum State {
        Init,
        OpenTable,
        WaitScreen,
        Fill,
        Take,
        Verify,
        Done
    }

    private static final int MAX_CRAFTS_PER_TASK = 8;

    private final Mode mode;
    private final int crafts;

    private State state = State.Init;
    private BlockPos tablePos;
    private int gridIndex;
    private int delay;

    public CraftTask(InventoryManager mgr, Mode mode, int crafts) {
        super(mgr);
        this.mode = mode;
        this.crafts = Math.min(crafts, MAX_CRAFTS_PER_TASK);
    }

    @Override
    public String id() {
        return mode == Mode.Compress ? "compress" : "decompress";
    }

    /** Maximum number of crafts a single task will do, the rest is picked up by the next evaluation. */
    public static int maxCraftsPerTask() {
        return MAX_CRAFTS_PER_TASK;
    }

    private Item ingredient() {
        return mode == Mode.Compress ? Items.EMERALD : Items.EMERALD_BLOCK;
    }

    private int ingredientsPerCraft() {
        return mode == Mode.Compress ? 9 : 1;
    }

    private int gridSlots() {
        return mode == Mode.Compress ? 9 : 4;
    }

    /** Grid slot ids: crafting table 1..9, player 2x2 grid 1..4, result is slot 0 in both. */
    private int gridSlotId(int i) {
        return 1 + i;
    }

    private int usedGridSlots() {
        return mode == Mode.Compress ? 9 : 1;
    }

    @Override
    protected Status run() {
        if (mc.player == null || mc.world == null) return fail("no player");
        if (crafts <= 0) return fail("nothing to craft");

        if (delay > 0) {
            delay--;
            return Status.RUNNING;
        }

        return switch (state) {
            case Init -> init();
            case OpenTable -> openTable();
            case WaitScreen -> waitScreen();
            case Fill -> fill();
            case Take -> take();
            case Verify -> verify();
            case Done -> Status.DONE;
        };
    }

    private Status init() {
        if (mode == Mode.Decompress) {
            // 2x2 grid, no table needed - but no other screen may be open.
            if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
                mc.player.closeHandledScreen();
                return timedOut() ? fail("could not close the open screen") : Status.RUNNING;
            }

            setState(State.Fill);
            return Status.RUNNING;
        }

        if (mc.player.currentScreenHandler instanceof CraftingScreenHandler) {
            setState(State.Fill);
            return Status.RUNNING;
        }

        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
            mc.player.closeHandledScreen();
            return timedOut() ? fail("could not close the open screen") : Status.RUNNING;
        }

        tablePos = findCraftingTable(s.craftingTableRange.get());
        if (tablePos == null) return fail("no crafting table in range");

        setState(State.OpenTable);
        return Status.RUNNING;
    }

    private Status openTable() {
        if (tablePos == null) return fail("no crafting table in range");
        if (!mc.world.getBlockState(tablePos).isOf(Blocks.CRAFTING_TABLE)) return fail("crafting table vanished");

        Vec3d hitPos = Vec3d.ofCenter(tablePos);
        BlockHitResult hitResult = new BlockHitResult(hitPos, Direction.UP, tablePos, false);

        Runnable interact = () -> {
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
            if (result.isAccepted()) mc.player.swingHand(Hand.MAIN_HAND);
        };

        if (s.rotate.get()) Rotations.rotate(Rotations.getYaw(hitPos), Rotations.getPitch(hitPos), 100, interact);
        else interact.run();

        setState(State.WaitScreen);
        return Status.RUNNING;
    }

    private Status waitScreen() {
        if (mc.player.currentScreenHandler instanceof CraftingScreenHandler) {
            setState(State.Fill);
            return Status.RUNNING;
        }

        return timedOut() ? fail("crafting table did not open") : Status.RUNNING;
    }

    private Status fill() {
        if (!handlerValid()) return fail("screen closed while filling the grid");

        if (gridIndex >= usedGridSlots()) {
            setState(State.Take);
            return Status.RUNNING;
        }

        int slotId = gridSlotId(gridIndex);
        if (!fillGridSlot(slotId, ingredient(), crafts)) {
            return fail("ran out of " + ingredient().getName().getString());
        }

        gridIndex++;
        delay = s.actionDelay.get();
        return Status.RUNNING;
    }

    private Status take() {
        if (!handlerValid()) return fail("screen closed before taking the result");

        // Every grid slot holds exactly `crafts` ingredients, so one shift click crafts exactly `crafts` times.
        InvHelper.click(0, 0, SlotActionType.QUICK_MOVE);

        setState(State.Verify);
        delay = Math.max(1, s.actionDelay.get());
        return Status.RUNNING;
    }

    private Status verify() {
        if (!handlerValid()) return fail("screen closed before the grid was cleared");

        // Pull back whatever is left in the grid (happens when the inventory filled up mid craft).
        boolean leftovers = false;
        for (int i = 0; i < gridSlots(); i++) {
            int slotId = gridSlotId(i);
            if (InvHelper.stackInSlotId(slotId).isEmpty()) continue;

            InvHelper.click(slotId, 0, SlotActionType.QUICK_MOVE);
            leftovers = true;
        }

        if (leftovers && !timedOut()) {
            delay = Math.max(1, s.actionDelay.get());
            return Status.RUNNING;
        }

        debug((mode == Mode.Compress ? "Compressed " : "Decompressed ") + crafts + "x");
        setState(State.Done);
        return Status.DONE;
    }

    /**
     * Puts exactly {@code need} items into a grid slot, pulling from as many inventory stacks as necessary.
     */
    private boolean fillGridSlot(int slotId, Item item, int need) {
        int have = InvHelper.stackInSlotId(slotId).getCount();
        int guard = 0;

        while (have < need && guard++ < 16) {
            int index = InvHelper.findIndex(item);
            if (index == -1) break;

            int fromId = SlotUtils.indexToId(index);
            if (fromId == -1) break;

            int moved = InvHelper.moveExact(fromId, slotId, need - have);
            if (moved <= 0) break;

            have += moved;
        }

        InvHelper.returnCursor(-1);
        return have >= need;
    }

    private boolean handlerValid() {
        if (mode == Mode.Compress) return mc.player.currentScreenHandler instanceof CraftingScreenHandler;
        return mc.player.currentScreenHandler == mc.player.playerScreenHandler;
    }

    /** Nearest crafting table within range, {@code null} when there is none - the trigger then does not fire. */
    public static BlockPos findCraftingTable(double range) {
        if (mc.player == null || mc.world == null) return null;

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        int r = (int) Math.ceil(range) + 1;
        BlockPos center = mc.player.getBlockPos();

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = center.add(x, y, z);
                    if (!mc.world.getBlockState(pos).isOf(Blocks.CRAFTING_TABLE)) continue;

                    double distance = PlayerUtils.distanceTo(pos);
                    if (distance > range || distance >= bestDistance) continue;

                    best = pos;
                    bestDistance = distance;
                }
            }
        }

        return best;
    }

    private void setState(State state) {
        this.state = state;
        resetStateTimer();
    }

    @Override
    public void cleanup() {
        if (mc.player == null) return;

        InvHelper.returnCursor(-1);

        // Never leave ingredients behind in the grid, the 2x2 grid in particular would keep them until the
        // player opens their inventory manually.
        if (handlerValid()) {
            for (int i = 0; i < gridSlots(); i++) {
                int slotId = gridSlotId(i);
                if (!InvHelper.stackInSlotId(slotId).isEmpty()) InvHelper.click(slotId, 0, SlotActionType.QUICK_MOVE);
            }
        }

        // The old implementation left the crafting screen open, this one always closes it again.
        if (mc.player.currentScreenHandler instanceof CraftingScreenHandler) mc.player.closeHandledScreen();
    }
}
