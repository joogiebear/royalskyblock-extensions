package com.mystipixel.royalskyblock.ext.mythicmobs;

import com.mystipixel.royalskyblock.RoyalSkyblockPlugin;
import com.willfp.eco.core.EcoPlugin;
import com.willfp.eco.core.extensions.Extension;
import org.bukkit.Bukkit;

/**
 * Lets RoyalSkyblock spawn MythicMobs mobs ({@code island-mobs.provider: mythicmobs}).
 *
 * <p>The provider registers in {@code onEnable} because eco enables extensions before the host's
 * enable, so it is in the registry before island mob spawning looks for it. It registers even without
 * MythicMobs installed and reports itself unavailable.
 */
public final class MythicMobsExtension extends Extension {

    public MythicMobsExtension(EcoPlugin plugin) {
        super(plugin);
    }

    @Override
    protected void onEnable() {
        RoyalSkyblockPlugin host = RoyalSkyblockPlugin.Companion.get();

        if (!host.extensionEnabled(getName())) {
            getLogger().info(getName() + " is listed in extensions.disabled, not registering.");
            return;
        }

        try {
            host.integrations().registerMobProvider(new MythicMobsProvider());
        } catch (Throwable failed) {
            // An extension that throws in onEnable takes its host down with it; a broken integration
            // should cost one jar, not the server.
            getLogger().severe("Could not register the MythicMobs backend: " + failed);
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
            getLogger().warning("Registered, but MythicMobs is not installed: this extension does "
                    + "nothing until it is.");
            return;
        }
        getLogger().info("MythicMobs backend registered. Set island-mobs.provider to 'mythicmobs' to use it.");
    }

    @Override
    protected void onDisable() {
        // Nothing to undo: the registry lives and dies with the host.
    }
}
