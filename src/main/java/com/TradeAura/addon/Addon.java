package com.TradeAura.addon;

import com.TradeAura.addon.modules.TradeAura;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

/**
 * Every constant here comes from {@link BuildConfig}, which is generated out of gradle.properties during the
 * build, so the mod name, the category and the repo are defined in exactly one place.
 */
public class Addon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();

    public static Category CATEGORY = new Category(BuildConfig.CATEGORY_NAME, () -> Items.DRIED_KELP.getDefaultInstance());

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
