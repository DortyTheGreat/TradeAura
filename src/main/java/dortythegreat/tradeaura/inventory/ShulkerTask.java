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

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * 2.4 Dump To Shulker and 2.5 Refill From Shulker.
 * <p>
 * Full round trip: pick a suitable shulker box item (2.4.1 - it must have free slots for a dump, or contain the
 * wanted items for a refill), move it into the hotbar, place it next to the player, open it, move the exact
 * amounts, close it, break it and wait until it has been picked back up. Everything is server driven, unlike the
 * first draft which only edited the client side inventory and therefore did nothing at all.
 */
public class ShulkerTask extends InvTask {
    public enum Mode {
        Dump,
        Refill
    }

    private enum State {
        Prepare,
        Place,
        WaitPlaced,
        Open,
        WaitScreen,
        Transfer,
        Close,
        Break,
        Collect,
        Restore,
        Done
    }

    /** A rule together with the amount that still has to be moved for it. */
    public static class Entry {
        public final ItemRule rule;
        public int remaining;

        public Entry(ItemRule rule, int remaining) {
            this.rule = rule;
            this.remaining = remaining;
        }
    }

    private static final int CONTAINER_SLOTS = InvHelper.SHULKER_SLOTS;

    private final Mode mode;
    private final List<Entry> entries;

    private State state = State.Prepare;
    private int delay;

    private int shulkerIndex = -1;
    private int originalIndex = -1;
    private int hotbarIndex = -1;
    private boolean swappedIntoHotbar;

    private BlockPos pos;
    private int shulkersBefore;
    private int stalls;
    private boolean toolSwapped;
    private String pendingFail;

    public ShulkerTask(InventoryManager mgr, Mode mode, List<Entry> entries) {
        super(mgr);
        this.mode = mode;
        this.entries = entries;
    }

    @Override
    public String id() {
        return mode == Mode.Dump ? "dump" : "refill";
    }

    @Override
    protected Status run() {
        if (mc.player == null || mc.level == null) return fail("no player");

        // Has to happen before the delay check, a key binding has to be re-suppressed on every single tick.
        applyMovementControl();

        if (delay > 0) {
            delay--;
            return Status.RUNNING;
        }

        return switch (state) {
            case Prepare -> prepare();
            case Place -> place();
            case WaitPlaced -> waitPlaced();
            case Open -> open();
            case WaitScreen -> waitScreen();
            case Transfer -> transfer();
            case Close -> close();
            case Break -> breakBox();
            case Collect -> collect();
            case Restore -> restore();
            case Done -> finish();
        };
    }

    /**
     * While the box is standing in the world the player must not wander off, or the box ends up out of reach
     * with all the items still inside. Only the input is blocked - movement packets are untouched, so the
     * server keeps seeing the player normally.
     * <p>
     * The one exception is walking to the drop: there the module wants the player to move, so the lock is
     * lifted and {@link MovementControl#walkTowards(double, double)} takes over.
     */
    private void applyMovementControl() {
        boolean boxIsDown = switch (state) {
            case Place, WaitPlaced, Open, WaitScreen, Transfer, Close, Break -> true;
            default -> false;
        };

        if (boxIsDown && s.lockMovement.get()) MovementControl.freeze();
        else if (state != State.Collect) MovementControl.stop();
    }

    // States

    private Status prepare() {
        if (mc.player.containerMenu != mc.player.inventoryMenu) {
            InvHelper.closeScreen();
            return timedOut() ? fail("could not close the open screen") : Status.RUNNING;
        }

        originalIndex = findShulker();
        if (originalIndex == -1) return fail(mode == Mode.Dump ? "no shulker box with free slots" : "no shulker box with the wanted items");

        shulkerIndex = originalIndex;

        // The box has to be in the hotbar to be placed.
        if (!SlotUtils.isHotbar(shulkerIndex)) {
            hotbarIndex = InvHelper.findEmptyHotbarIndex();
            if (hotbarIndex == -1) hotbarIndex = mc.player.getInventory().getSelectedSlot();

            InvUtils.quickSwap().fromId(hotbarIndex).to(originalIndex);
            swappedIntoHotbar = true;
            shulkerIndex = hotbarIndex;

            delay = Math.max(1, s.actionDelay.get());
        }

        pos = findPlacePos();
        if (pos == null) return fail("no free spot to place the shulker box");

        shulkersBefore = countShulkers();

        setState(State.Place);
        return Status.RUNNING;
    }

    private Status place() {
        ItemStack stack = mc.player.getInventory().getItem(shulkerIndex);
        if (!InvHelper.isShulker(stack)) return fail("the shulker box moved out of its slot");

        InvUtils.swap(shulkerIndex, true);

        BlockPos support = pos.below();
        Vec3 hitPos = Vec3.atCenterOf(support).add(0, 0.5, 0);
        BlockHitResult hitResult = new BlockHitResult(hitPos, Direction.UP, support, false);

        interact(hitResult, hitPos);

        setState(State.WaitPlaced);
        return Status.RUNNING;
    }

    private Status waitPlaced() {
        if (mc.level.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock) {
            setState(State.Open);
            return Status.RUNNING;
        }

        if (timedOut()) return fail("the shulker box could not be placed");

        // Retry the placement every few ticks, the first attempt can be eaten by a lag spike.
        if (stateTicks % 10 == 0) {
            BlockPos support = pos.below();
            Vec3 hitPos = Vec3.atCenterOf(support).add(0, 0.5, 0);
            interact(new BlockHitResult(hitPos, Direction.UP, support, false), hitPos);
        }

        return Status.RUNNING;
    }

    private Status open() {
        Vec3 hitPos = Vec3.atCenterOf(pos);
        interact(new BlockHitResult(hitPos, Direction.UP, pos, false), hitPos);

        setState(State.WaitScreen);
        return Status.RUNNING;
    }

    private Status waitScreen() {
        if (mc.player.containerMenu instanceof ShulkerBoxMenu) {
            setState(State.Transfer);
            return Status.RUNNING;
        }

        if (timedOut()) return failLater("the shulker box did not open");

        if (stateTicks % 10 == 0) {
            Vec3 hitPos = Vec3.atCenterOf(pos);
            interact(new BlockHitResult(hitPos, Direction.UP, pos, false), hitPos);
        }

        return Status.RUNNING;
    }

    private Status transfer() {
        if (!(mc.player.containerMenu instanceof ShulkerBoxMenu)) {
            return failLater("the shulker screen closed unexpectedly");
        }

        // Several stacks per tick, otherwise a big dump runs into the action timeout, gets cut short and the
        // trigger immediately places the very same box again.
        int batch = Math.max(1, s.transferBatch.get());
        int moves = 0;

        while (moves < batch) {
            int moved = mode == Mode.Dump ? transferDump() : transferRefill();
            if (moved <= 0) break;
            moves++;
        }

        if (moves > 0) {
            stalls = 0;
            // As long as items keep moving this is not a stall, so the timeout must not run out on us.
            resetStateTimer();
            delay = s.actionDelay.get();
            return Status.RUNNING;
        }

        if (timedOut()) {
            setState(State.Close);
            return Status.RUNNING;
        }

        // Nothing could be moved: either everything is done or the container / inventory is full.
        if (hasWork() && ++stalls < 3) {
            delay = Math.max(1, s.actionDelay.get());
            return Status.RUNNING;
        }

        setState(State.Close);
        return Status.RUNNING;
    }

    private int transferDump() {
        for (Entry entry : entries) {
            if (entry.remaining <= 0) continue;

            int index = InvHelper.findIndex(entry.rule.items);
            if (index == -1) {
                entry.remaining = 0;
                continue;
            }

            ItemStack stack = mc.player.getInventory().getItem(index);
            int fromId = SlotUtils.indexToId(index);
            if (fromId == -1) continue;

            int wanted = Math.min(entry.remaining, stack.getCount());
            int moved;

            if (wanted >= stack.getCount()) {
                // Whole stack: one shift click instead of a pickup / place pair.
                int before = stack.getCount();
                InvHelper.click(fromId, 0, ContainerInput.QUICK_MOVE);
                moved = Math.max(0, before - mc.player.getInventory().getItem(index).getCount());
            }
            else {
                int toId = InvHelper.findContainerTargetSlot(mc.player.containerMenu, CONTAINER_SLOTS, stack.getItem());
                if (toId == -1) continue;

                moved = InvHelper.moveExact(fromId, toId, wanted);
            }

            entry.remaining -= moved;
            if (moved > 0) return moved;
        }

        return 0;
    }

    private int transferRefill() {
        for (Entry entry : entries) {
            if (entry.remaining <= 0) continue;

            int fromId = InvHelper.findContainerSlotWith(mc.player.containerMenu, CONTAINER_SLOTS, entry.rule.items);
            if (fromId == -1) {
                entry.remaining = 0;
                continue;
            }

            ItemStack stack = InvHelper.stackInSlotId(fromId);
            int wanted = Math.min(entry.remaining, stack.getCount());
            int moved;

            if (wanted >= stack.getCount()) {
                int before = stack.getCount();
                InvHelper.click(fromId, 0, ContainerInput.QUICK_MOVE);
                moved = Math.max(0, before - InvHelper.stackInSlotId(fromId).getCount());
            }
            else {
                int targetIndex = InvHelper.findTargetIndex(stack.getItem());
                if (targetIndex == -1) continue;

                int toId = SlotUtils.indexToId(targetIndex);
                if (toId == -1) continue;

                moved = InvHelper.moveExact(fromId, toId, wanted);
            }

            entry.remaining -= moved;
            if (moved > 0) return moved;
        }

        return 0;
    }

    private boolean hasWork() {
        for (Entry entry : entries) {
            if (entry.remaining > 0) return true;
        }
        return false;
    }

    private Status close() {
        InvHelper.returnCursor(-1);
        InvHelper.closeScreen();

        setState(State.Break);
        delay = Math.max(1, s.actionDelay.get());
        return Status.RUNNING;
    }

    private Status breakBox() {
        if (!(mc.level.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock)) {
            setState(State.Collect);
            return Status.RUNNING;
        }

        if (timedOut()) return failLater("could not break the shulker box");

        if (s.shulkerAutoTool.get() && !toolSwapped) {
            toolSwapped = true;

            FindItemResult tool = InvUtils.findFastestTool(mc.level.getBlockState(pos));
            if (tool.found() && tool.isHotbar()) InvUtils.swap(tool.slot(), true);
        }

        if (s.rotate.get()) {
            Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), 100, () -> BlockUtils.breakBlock(pos, true));
        }
        else {
            BlockUtils.breakBlock(pos, true);
        }

        return Status.RUNNING;
    }

    private Status collect() {
        if (countShulkers() >= shulkersBefore) {
            MovementControl.stop();
            setState(State.Restore);
            return Status.RUNNING;
        }

        if (stateTicks >= s.shulkerPickupTicks.get()) {
            MovementControl.stop();
            mgr.warn("The broken shulker box was not picked up, it is lying at " + pos.toShortString() + ".");
            setState(State.Restore);
            return Status.RUNNING;
        }

        // Items are only picked up on collision, and the box is placed up to a few blocks away, so without
        // this the shulker (and everything in it) would simply be left on the ground.
        if (s.walkToDrop.get()) walkToDrop();

        return Status.RUNNING;
    }

    /** Nearest dropped shulker box around the spot the box was broken at. */
    private ItemEntity findDrop() {
        AABB box = new AABB(
            pos.getX() - 6, pos.getY() - 4, pos.getZ() - 6,
            pos.getX() + 7, pos.getY() + 5, pos.getZ() + 7
        );

        ItemEntity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Entity entity : mc.level.getEntities(mc.player, box, e -> e instanceof ItemEntity item && InvHelper.isShulker(item.getItem()))) {
            if (!(entity instanceof ItemEntity item)) continue;

            double distance = mc.player.distanceToSqr(entity);
            if (distance >= bestDistance) continue;

            best = item;
            bestDistance = distance;
        }

        return best;
    }

    private void walkToDrop() {
        ItemEntity drop = findDrop();

        if (drop == null) {
            MovementControl.stop();
            return;
        }

        double dx = drop.getX() - mc.player.getX();
        double dz = drop.getZ() - mc.player.getZ();

        if (Math.sqrt(dx * dx + dz * dz) < 0.35) {
            MovementControl.stop();
            return;
        }

        // Real walking through the movement keys instead of a velocity injection: setting the velocity before
        // the player tick fights with friction and with the empty movement input, which is why the player
        // barely moved before.
        MovementControl.walkTowards(drop.getX(), drop.getZ());
    }

    private Status restore() {
        if (swappedIntoHotbar && originalIndex != -1 && hotbarIndex != -1) {
            InvUtils.quickSwap().fromId(hotbarIndex).to(originalIndex);
            swappedIntoHotbar = false;
        }

        InvUtils.swapBack();

        setState(State.Done);
        return Status.RUNNING;
    }

    private Status finish() {
        if (pendingFail != null) return fail(pendingFail);

        debug((mode == Mode.Dump ? "Dumped items into " : "Refilled items from ") + "the shulker box");
        return Status.DONE;
    }

    // Helpers

    private Status failLater(String reason) {
        if (pendingFail == null) pendingFail = reason;

        // Always try to leave the world in the state we found it in.
        if (mc.player.containerMenu instanceof ShulkerBoxMenu) InvHelper.closeScreen();

        setState(mc.level.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock ? State.Break : State.Restore);
        return Status.RUNNING;
    }

    private void interact(BlockHitResult hitResult, Vec3 hitPos) {
        Runnable action = () -> {
            boolean sneaking = mc.player.isShiftKeyDown();
            mc.player.setShiftKeyDown(false);

            InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
            if (result.consumesAction()) mc.player.swing(InteractionHand.MAIN_HAND);

            mc.player.setShiftKeyDown(sneaking);
        };

        if (s.rotate.get()) Rotations.rotate(Rotations.getYaw(hitPos), Rotations.getPitch(hitPos), 100, action);
        else action.run();
    }

    /** Total number of shulker box items in the inventory, used to detect that the broken box was picked up. */
    private int countShulkers() {
        int count = 0;
        for (int i = SlotUtils.HOTBAR_START; i <= SlotUtils.MAIN_END; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (InvHelper.isShulker(stack)) count += stack.getCount();
        }
        return count;
    }

    /**
     * 2.4.1: only a box that can actually take the items (dump) or that holds them (refill) is used.
     */
    private int findShulker() {
        for (int i = SlotUtils.HOTBAR_START; i <= SlotUtils.MAIN_END; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!InvHelper.isShulker(stack)) continue;
            if (isSuitable(stack)) return i;
        }

        return -1;
    }

    private boolean isSuitable(ItemStack shulker) {
        if (mode == Mode.Dump) {
            if (InvHelper.containerFreeSlots(shulker) <= 0) {
                // No empty slot left, but a partially filled stack of the same item may still take something.
                for (Entry entry : entries) {
                    for (var item : entry.rule.items) {
                        if (InvHelper.containerSpaceFor(shulker, item) > 0) return true;
                    }
                }
                return false;
            }
            return true;
        }

        for (Entry entry : entries) {
            if (InvHelper.containerCount(shulker, entry.rule.items) > 0) return true;
        }

        return false;
    }

    /** Static version used by the manager to check whether the trigger can fire at all. */
    public static boolean hasUsableShulker(Mode mode, List<Entry> entries) {
        if (mc.player == null) return false;

        for (int i = SlotUtils.HOTBAR_START; i <= SlotUtils.MAIN_END; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!InvHelper.isShulker(stack)) continue;

            if (mode == Mode.Dump) {
                if (InvHelper.containerFreeSlots(stack) > 0) return true;

                for (Entry entry : entries) {
                    for (var item : entry.rule.items) {
                        if (InvHelper.containerSpaceFor(stack, item) > 0) return true;
                    }
                }
            }
            else {
                for (Entry entry : entries) {
                    if (InvHelper.containerCount(stack, entry.rule.items) > 0) return true;
                }
            }
        }

        return false;
    }

    private BlockPos findPlacePos() {
        BlockPos feet = mc.player.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int dy = 0; dy >= -1; dy--) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos candidate = feet.offset(dx, dy, dz);

                    if (candidate.equals(feet) || candidate.equals(feet.above())) continue;
                    if (!isPlaceable(candidate)) continue;

                    double distance = PlayerUtils.distanceTo(candidate);
                    if (distance >= bestDistance) continue;

                    best = candidate;
                    bestDistance = distance;
                }
            }
        }

        return best;
    }

    private boolean isPlaceable(BlockPos candidate) {
        // The box is placed on top of the block below, so it faces up and needs free space above to open.
        if (!BlockUtils.canPlaceBlock(candidate, true, Blocks.SHULKER_BOX)) return false;
        if (!mc.level.getBlockState(candidate.above()).canBeReplaced()) return false;

        BlockPos support = candidate.below();
        var supportState = mc.level.getBlockState(support);

        if (supportState.canBeReplaced()) return false;
        if (!supportState.getFluidState().isEmpty()) return false;
        if (BlockUtils.isClickable(supportState.getBlock())) return false;

        return PlayerUtils.isWithinReach(candidate) && PlayerUtils.isWithinReach(support);
    }

    private void setState(State state) {
        this.state = state;
        resetStateTimer();
    }

    @Override
    public void cleanup() {
        MovementControl.stop();

        if (mc.player == null) return;

        InvHelper.returnCursor(-1);

        if (mc.player.containerMenu instanceof ShulkerBoxMenu) InvHelper.closeScreen();

        if (swappedIntoHotbar && originalIndex != -1 && hotbarIndex != -1) {
            InvUtils.quickSwap().fromId(hotbarIndex).to(originalIndex);
            swappedIntoHotbar = false;
        }

        InvUtils.swapBack();
    }
}
