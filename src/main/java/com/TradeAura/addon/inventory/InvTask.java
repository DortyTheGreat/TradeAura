package com.TradeAura.addon.inventory;

/**
 * Base class of every inventory action.
 * <p>
 * Everything an inventory trigger does (crafting, placing shulkers, breaking them, ...) needs several ticks and
 * several server round trips, so every action is a small state machine that gets ticked once per client tick and
 * reports back whether it is still running. Doing this inside a single tick, like the first draft did, either
 * dead locks the client or desyncs the inventory.
 */
public abstract class InvTask {
    public enum Status {
        RUNNING,
        DONE,
        FAILED
    }

    protected final InventoryManager mgr;
    protected final InventorySettings s;

    /** Ticks since the task was created. */
    protected int ticks;
    /** Ticks since the last state change, used for per state timeouts. */
    protected int stateTicks;

    private String failReason = "";

    protected InvTask(InventoryManager mgr) {
        this.mgr = mgr;
        this.s = mgr.settings();
    }

    public final Status tick() {
        ticks++;
        stateTicks++;
        return run();
    }

    protected abstract Status run();

    /** Stable id of the trigger this task belongs to, used for failure cooldowns. */
    public abstract String id();

    /** Always called once the task is finished, no matter whether it succeeded or failed. */
    public void cleanup() {
    }

    public String failReason() {
        return failReason;
    }

    protected Status fail(String reason) {
        failReason = reason;
        return Status.FAILED;
    }

    protected boolean timedOut() {
        return stateTicks > s.actionTimeout.get();
    }

    protected void resetStateTimer() {
        stateTicks = 0;
    }

    protected void debug(String message) {
        mgr.debug(message);
    }
}
