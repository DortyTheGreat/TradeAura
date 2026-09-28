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

import meteordevelopment.meteorclient.utils.misc.input.Input;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Client side movement control.
 * <p>
 * Everything here works on the <b>input</b> level (key bindings), never on packets: the movement packets keep
 * being sent exactly as usual, so the server always sees where the player really is and nothing desyncs. What
 * is suppressed is only the movement the client would produce out of the player's own key presses.
 * <p>
 * That is also why walking is done by pressing the movement keys instead of writing to the velocity directly:
 * velocity set before the player tick is immediately reworked by friction and by the (empty) movement input, so
 * the player barely moves. Pressing the keys makes the player walk for real, including sprinting rules, step
 * assist and collision handling.
 */
public final class MovementControl {
    /** Set while this class holds any key down, so the keys are only released when they were pressed by us. */
    private static boolean holding;

    private MovementControl() {
    }

    /**
     * Suppresses the player's own movement input for this tick. Has to be called every tick to stay in effect,
     * because a key binding is re-read by the client on every tick.
     */
    public static void freeze() {
        holding = true;
        releaseKeys();

        if (mc.player != null) {
            mc.player.setSprinting(false);
            mc.player.setShiftKeyDown(false);
        }
    }

    /**
     * Walks towards a position by pressing the movement keys that fit best, relative to where the player is
     * currently looking. The camera is never turned, so this also works while rotations are disabled.
     */
    public static void walkTowards(double targetX, double targetZ) {
        if (mc.player == null || mc.options == null) return;

        double dx = targetX - mc.player.getX();
        double dz = targetZ - mc.player.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);

        if (length < 1.0E-4) {
            stop();
            return;
        }

        dx /= length;
        dz /= length;

        double yaw = Math.toRadians(mc.player.getYRot());
        // Where "W" and "A" would take the player with the current yaw.
        double forwardX = -Math.sin(yaw), forwardZ = Math.cos(yaw);
        double leftX = Math.cos(yaw), leftZ = Math.sin(yaw);

        double forward = dx * forwardX + dz * forwardZ;
        double left = dx * leftX + dz * leftZ;

        releaseKeys();
        holding = true;

        // 0.35 is roughly the point where a diagonal is closer to the target than a single direction.
        if (forward > 0.35) mc.options.keyUp.setDown(true);
        else if (forward < -0.35) mc.options.keyDown.setDown(true);

        if (left > 0.35) mc.options.keyLeft.setDown(true);
        else if (left < -0.35) mc.options.keyRight.setDown(true);

        // Simple obstacle handling: bump into something -> hop over it.
        if (mc.player.horizontalCollision && mc.player.onGround()) mc.options.keyJump.setDown(true);
    }

    /**
     * Hands control back to the player. Safe to call at any time.
     * <p>
     * The key bindings are restored to what the keyboard actually says instead of just being cleared: a key
     * binding that is forced to "up" while the key is physically held stays up until the next key event, which
     * is why the player had to let go and press again after the module released them.
     */
    public static void stop() {
        if (!holding) return;
        holding = false;

        if (mc.options == null) return;

        restore(mc.options.keyUp);
        restore(mc.options.keyDown);
        restore(mc.options.keyLeft);
        restore(mc.options.keyRight);
        restore(mc.options.keyJump);
        restore(mc.options.keyShift);
    }

    private static void restore(net.minecraft.client.KeyMapping key) {
        key.setDown(Input.isPressed(key));
    }

    private static void releaseKeys() {
        if (mc.options == null) return;

        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keyJump.setDown(false);
        mc.options.keyShift.setDown(false);
    }
}
