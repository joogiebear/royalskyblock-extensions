package com.mystipixel.royalskyblock.ext.mythicmobs;

import com.mystipixel.royalskyblock.RoyalSkyblockPlugin;
import com.willfp.eco.core.EcoPlugin;
import com.willfp.eco.core.extensions.Extension;
import org.bukkit.Bukkit;

/**
 * Teaches RoyalSkyblock to spawn MythicMobs mobs.
 *
 * <p>Install MythicMobs and this jar, set {@code island-mobs.provider: mythicmobs}, and island mobs
 * come from MythicMobs. Install neither and RoyalSkyblock does not know MythicMobs exists — the core
 * carries no MythicMobs code and no check for the plugin's name. This is the adapter pattern's whole
 * point: one registry slot, one jar per mob plugin, the config picks.
 *
 * <p>The provider is registered in {@code onEnable}, because that is what makes it visible: eco
 * enables extensions before the host's own enable, so the provider is in the registry by the time
 * RoyalSkyblock starts island mob spawning and looks for it. Registered even when MythicMobs is
 * absent — the backend reports itself unavailable and nothing selects it — because "I installed the
 * extension and nothing happened" should never be silent.
 */
public final class MythicMobsExtension extends Extension {

    public MythicMobsExtension(EcoPlugin plugin) {
        super(plugin);
    }

    @Override
    protected void onEnable() {
        RoyalSkyblockPlugin host = RoyalSkyblockPlugin.Companion.get();

        if (!host.extensionEnabled(getName())) {
            getLogger().info(getName() + " is listed in extensions.disabled — not registering.");
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
            getLogger().warning("Registered, but MythicMobs is not installed — this extension does "
                    + "nothing until it is.");
            return;
        }
        getLogger().info("MythicMobs backend registered — set island-mobs.provider to 'mythicmobs' to use it.");
    }

    @Override
    protected void onDisable() {
        // Nothing to undo: the registry lives and dies with the host.
    }
}
