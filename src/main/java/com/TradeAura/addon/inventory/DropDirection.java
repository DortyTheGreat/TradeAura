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

package com.TradeAura.addon.inventory;

/**
 * Drop direction, relative to the player (not to the world axes).
 * Dropped items inherit the direction the player is looking at when the throw packet is processed,
 * so the task rotates (server side only) before dropping.
 */
public enum DropDirection {
    Up,
    Down,
    Forward,
    Back,
    Left,
    Right;

    /**
     * @param playerYaw the current yaw of the player
     * @return {yaw, pitch} that has to be sent to the server for the item to fly in this direction
     */
    public float[] getRotation(float playerYaw) {
        return switch (this) {
            case Up -> new float[]{playerYaw, -90f};
            case Down -> new float[]{playerYaw, 90f};
            case Forward -> new float[]{playerYaw, 0f};
            case Back -> new float[]{playerYaw + 180f, 0f};
            case Left -> new float[]{playerYaw - 90f, 0f};
            case Right -> new float[]{playerYaw + 90f, 0f};
        };
    }
}
