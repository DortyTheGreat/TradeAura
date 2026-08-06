package com.TradeAura.addon.inventory;

/**
 * How the amount a trigger has to move is calculated.
 * <ul>
 *     <li>{@link #ToLimit} - move {@code |current - leave|}, i.e. bring the inventory to exactly the "leave" value
 *     with a single action. Recommended, because the trigger is satisfied immediately and cannot chain into itself.</li>
 *     <li>{@link #TriggerMinusLeave} - move exactly {@code |trigger - leave|}, the literal formula from the spec.
 *     A big overflow is worked off in several chained actions.</li>
 * </ul>
 */
public enum AmountMode {
    ToLimit,
    TriggerMinusLeave
}
