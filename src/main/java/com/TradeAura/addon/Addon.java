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

package com.TradeAura.addon;

import com.TradeAura.addon.modules.TradeAura;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.DisplayItemUtils;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

/**
 * Every constant here comes from {@link BuildConfig}, which is generated out of gradle.properties during the
 * build, so the mod name, the category and the repo are defined in exactly one place.
 */
public class Addon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();

    /**
     * DisplayItemUtils.toStack, NOT Items.DRIED_KELP.getDefaultInstance().
     *
     * getDefaultInstance() builds a real ItemStack, which reads the item's
     * data components - and those are only bound once the registries have
     * finished loading. Opening the Meteor GUI then throws "Components not
     * bound yet" and takes the whole client down. The Supplier defers the
     * call but not far enough to guarantee it.
     *
     * This is the helper Meteor's own Categories class uses, for exactly
     * this reason.
     */
    public static Category CATEGORY = new Category(
        BuildConfig.CATEGORY_NAME, () -> DisplayItemUtils.toStack(Items.DRIED_KELP));

    @Override
    public void onInitialize() {
        LOG.info("Initializing {}", BuildConfig.MOD_NAME);

        // Reuse the category instance Meteor already registered, if there is one.
        for (Category category : Modules.loopCategories()) {
            if (BuildConfig.CATEGORY_NAME.equals(category.name)) {
                CATEGORY = category;
                break;
            }
        }

        // Modules
        Modules.get().add(new TradeAura(CATEGORY));
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return BuildConfig.MOD_PACKAGE;
    }

    @Override
    public String getWebsite() {
        return BuildConfig.GITHUB_URL;
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo(BuildConfig.GITHUB_OWNER, BuildConfig.GITHUB_REPO);
    }
}
