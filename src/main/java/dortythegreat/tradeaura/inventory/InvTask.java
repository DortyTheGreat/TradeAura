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
