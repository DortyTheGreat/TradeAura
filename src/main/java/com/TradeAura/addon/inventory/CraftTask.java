package com.TradeAura.addon.inventory;

import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

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
        if (mc.player == null || mc.level == null) return fail("no player");
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
            if (mc.player.containerMenu != mc.player.inventoryMenu) {
                InvHelper.closeScreen();
                return timedOut() ? fail("could not close the open screen") : Status.RUNNING;
            }

            setState(State.Fill);
            return Status.RUNNING;
        }

        if (mc.player.containerMenu instanceof CraftingMenu) {
            setState(State.Fill);
            return Status.RUNNING;
        }

        if (mc.player.containerMenu != mc.player.inventoryMenu) {
            InvHelper.closeScreen();
            return timedOut() ? fail("could not close the open screen") : Status.RUNNING;
        }

        tablePos = findCraftingTable(s.craftingTableRange.get());
        if (tablePos == null) return fail("no crafting table in range");

        setState(State.OpenTable);
        return Status.RUNNING;
    }

    private Status openTable() {
        if (tablePos == null) return fail("no crafting table in range");
        if (!mc.level.getBlockState(tablePos).is(Blocks.CRAFTING_TABLE)) return fail("crafting table vanished");

        Vec3 hitPos = Vec3.atCenterOf(tablePos);
        BlockHitResult hitResult = new BlockHitResult(hitPos, Direction.UP, tablePos, false);

        Runnable interact = () -> {
            InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
            if (result.consumesAction()) mc.player.swing(InteractionHand.MAIN_HAND);
        };

        if (s.rotate.get()) Rotations.rotate(Rotations.getYaw(hitPos), Rotations.getPitch(hitPos), 100, interact);
        else interact.run();

        setState(State.WaitScreen);
        return Status.RUNNING;
    }

    private Status waitScreen() {
        if (mc.player.containerMenu instanceof CraftingMenu) {
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
            if (clicks < 0) return fail("ran out of " + name(recipe.input()));

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
        InvHelper.click(0, 0, ContainerInput.QUICK_MOVE);

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

        debug(recipe.id() + ": crafted " + crafts + "x " + name(recipe.output()));
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
            int available = mc.player.getInventory().getItem(index).getCount();

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

            InvHelper.click(i, 0, ContainerInput.QUICK_MOVE);
            leftovers = true;
        }

        return leftovers;
    }

    private boolean handlerValid() {
        if (recipe.needsTable()) return mc.player.containerMenu instanceof CraftingMenu;
        return mc.player.containerMenu == mc.player.inventoryMenu;
    }

    /** Nearest crafting table within range, {@code null} when there is none - the trigger then does not fire. */
    public static BlockPos findCraftingTable(double range) {
        if (mc.player == null || mc.level == null) return null;

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        int r = (int) Math.ceil(range) + 1;
        BlockPos center = mc.player.blockPosition();

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (!mc.level.getBlockState(pos).is(Blocks.CRAFTING_TABLE)) continue;

                    double distance = PlayerUtils.distanceTo(pos);
                    if (distance > range || distance >= bestDistance) continue;

                    best = pos;
                    bestDistance = distance;
                }
            }
        }

        return best;
    }

    private static String name(Item item) {
        return item.getDefaultInstance().getHoverName().getString();
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
        if (mc.player.containerMenu instanceof CraftingMenu) InvHelper.closeScreen();
    }
}
