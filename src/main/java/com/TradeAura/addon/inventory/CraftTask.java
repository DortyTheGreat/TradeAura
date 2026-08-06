package com.TradeAura.addon.inventory;

import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
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
 * 2.2 Compress Emeralds, 2.3 Decompress Emeralds and the glass pane recipe.
 * <p>
 * Every used grid slot gets exactly {@code crafts} ingredients and the result is shift clicked exactly once,
 * which produces exactly {@code crafts} results - no over crafting.
 * <p>
 * Filling the grid is limited by a click budget per tick instead of one slot per tick, and a whole stack is
 * moved with a single pickup/place pair, so a full 64 craft operation is a handful of ticks instead of dozens.
 * Recipes that fit into the 2x2 grid never open a crafting table.
 */
public class CraftTask extends InvTask {
    private enum State {
        Init,
        OpenTable,
        WaitScreen,
        Fill,
        Take,
        Verify,
        Done
    }

    /** A grid slot can never hold more than a stack, so this is the hard ceiling for one operation. */
    public static final int MAX_CRAFTS = 64;

    private final CraftRecipe recipe;
    private final int crafts;

    private State state = State.Init;
    private BlockPos tablePos;
    private int gridIndex;
    private int delay;

    public CraftTask(InventoryManager mgr, CraftRecipe recipe, int crafts) {
        super(mgr);
        this.recipe = recipe;
        this.crafts = Math.min(crafts, MAX_CRAFTS);
    }

    @Override
    public String id() {
        return recipe.id();
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
        if (!recipe.needsTable()) {
            // 2x2 grid of the player screen handler, no table needed - but no other screen may be open.
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

        int budget = Math.max(2, s.clicksPerTick.get());
        int used = 0;
        int[] slots = recipe.gridSlots();

        while (gridIndex < slots.length) {
            int slotId = slots[gridIndex];

            if (InvHelper.stackInSlotId(slotId).getCount() >= crafts) {
                gridIndex++;
                continue;
            }

            // Always do at least one slot per tick, then stop once the budget for this tick is used up.
            if (used > 0 && used >= budget) break;

            int clicks = fillGridSlot(slotId, recipe.input(), crafts);
            if (clicks < 0) return fail("ran out of " + recipe.input().getName().getString());

            used += clicks;
            gridIndex++;
        }

        if (gridIndex >= slots.length) {
            setState(State.Take);
            delay = s.actionDelay.get();
        }

        return Status.RUNNING;
    }

    private Status take() {
        if (!handlerValid()) return fail("screen closed before taking the result");

        // Every used grid slot holds exactly `crafts` ingredients, so one shift click crafts exactly `crafts` times.
        InvHelper.click(0, 0, SlotActionType.QUICK_MOVE);

        setState(State.Verify);
        delay = Math.max(1, s.actionDelay.get());
        return Status.RUNNING;
    }

    private Status verify() {
        if (!handlerValid()) return fail("screen closed before the grid was cleared");

        // Pull back whatever is left in the grid (happens when the inventory filled up mid craft).
        boolean leftovers = clearGrid();

        if (leftovers && !timedOut()) {
            delay = Math.max(1, s.actionDelay.get());
            return Status.RUNNING;
        }

        debug(recipe.id() + ": crafted " + crafts + "x " + recipe.output().getName().getString());
        setState(State.Done);
        return Status.DONE;
    }

    /**
     * Puts exactly {@code need} items into a grid slot, pulling from as many inventory stacks as necessary.
     *
     * @return the number of clicks that were sent, or -1 when there were not enough ingredients
     */
    private int fillGridSlot(int slotId, Item item, int need) {
        int have = InvHelper.stackInSlotId(slotId).getCount();
        int clicks = 0;
        int guard = 0;

        while (have < need && guard++ < 16) {
            int index = InvHelper.findIndex(item);
            if (index == -1) break;

            int fromId = SlotUtils.indexToId(index);
            if (fromId == -1) break;

            int wanted = need - have;
            int available = mc.player.getInventory().getStack(index).getCount();

            int moved = InvHelper.moveExact(fromId, slotId, wanted);
            if (moved <= 0) break;

            // A whole stack is two clicks, a partial amount is one click per item plus pickup and put back.
            clicks += moved >= available ? 2 : moved + 2;
            have += moved;
        }

        InvHelper.returnCursor(-1);
        return have >= need ? clicks : -1;
    }

    private boolean clearGrid() {
        boolean leftovers = false;

        for (int i = 1; i <= recipe.gridSize(); i++) {
            if (InvHelper.stackInSlotId(i).isEmpty()) continue;

            InvHelper.click(i, 0, SlotActionType.QUICK_MOVE);
            leftovers = true;
        }

        return leftovers;
    }

    private boolean handlerValid() {
        if (recipe.needsTable()) return mc.player.currentScreenHandler instanceof CraftingScreenHandler;
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
        if (handlerValid()) clearGrid();

        // The old implementation left the crafting screen open, this one always closes it again.
        if (mc.player.currentScreenHandler instanceof CraftingScreenHandler) mc.player.closeHandledScreen();
    }
}
