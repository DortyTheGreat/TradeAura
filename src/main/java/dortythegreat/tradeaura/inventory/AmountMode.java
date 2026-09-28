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
