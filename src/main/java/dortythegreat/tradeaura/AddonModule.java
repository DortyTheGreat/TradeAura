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

package dortythegreat.tradeaura;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * Base class for every module of this addon.
 *
 * Comes from dorty-addon-template and is the same in every Dorty addon apart from the package.
 * It is not on the template's sync list (the package differs per addon), so a change here has
 * to be copied to the other addons by hand.
 */
public abstract class AddonModule extends Module {
    protected AddonModule(Category category, String name, String description) {
        super(category, name, description);
    }

    /**
     * Meteor runs every chat message through String.format, so a '%' in an item name, a player
     * name or a path would throw halfway through a tick. Plain messages go through "%s" instead
     * and are shown verbatim. Calls with format arguments still reach Meteor's varargs versions.
     */
    public void info(String message) {
        super.info("%s", message);
    }

    /** See {@link #info(String)}. */
    public void warning(String message) {
        super.warning("%s", message);
    }

    /** See {@link #info(String)}. */
    public void error(String message) {
        super.error("%s", message);
    }

    /**
     * Meteor looks settings up by their exact name when loading a config, so renaming a setting
     * - even only its capitalisation - silently resets it to the default. Before Meteor reads the
     * config, stored names that match a current setting ignoring case are rewritten to the current
     * spelling. A subclass that overrides fromTag must call super.fromTag.
     */
    @Override
    public Module fromTag(CompoundTag tag) {
        migrateSettingNames(tag);
        return super.fromTag(tag);
    }

    private void migrateSettingNames(CompoundTag moduleTag) {
        if (!(moduleTag.get("settings") instanceof CompoundTag settingsTag)) return;

        for (Tag groupElement : settingsTag.getListOrEmpty("groups")) {
            if (!(groupElement instanceof CompoundTag groupTag)) continue;

            SettingGroup group = settings.getGroup(groupTag.getStringOr("name", ""));
            if (group == null) continue;

            for (Tag settingElement : groupTag.getListOrEmpty("settings")) {
                if (!(settingElement instanceof CompoundTag settingTag)) continue;

                String stored = settingTag.getStringOr("name", "");
                if (group.get(stored) != null) continue;

                for (Setting<?> setting : group) {
                    if (setting.name.equalsIgnoreCase(stored)) {
                        settingTag.putString("name", setting.name);
                        break;
                    }
                }
            }
        }
    }
}
