package com.mystipixel.royalskyblock.ext.ecominions

import com.mystipixel.royalskyblock.RoyalSkyblockPlugin
import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.extensions.Extension
import org.bukkit.Bukkit

/**
 * Publishes EcoMinions activity to libreforge (four triggers and a condition) for the whole eco suite.
 *
 * Registration must happen in onEnable: eco enables extensions before the host's enable, where
 * RoyalSkyblock compiles its perk and upgrade effect chains, and a chain referencing `minion_pickup`
 * fails with an unknown-id violation if the trigger isn't registered yet.
 */
class EcoMinionsExtension(plugin: EcoPlugin) : Extension(plugin) {

    override fun onEnable() {
        val host = RoyalSkyblockPlugin.get()

        if (!host.extensionEnabled(name)) {
            logger.info("$name is listed in extensions.disabled, not registering.")
            return
        }

        if (Bukkit.getPluginManager().getPlugin("EcoMinions") == null) {
            logger.warning("EcoMinions is not installed: no minion triggers or conditions registered.")
            return
        }

        // each wrapped and reported separately: an extension that throws in onEnable disables its host
        try {
            if (MinionTriggers.register(plugin)) {
                logger.info("Registered minion triggers (minion_pickup, minion_place, minion_upgrade, minion_fuel).")
            } else {
                logger.warning("EcoMinions is installed but its events could not be read: minion triggers are off.")
            }
        } catch (failed: Throwable) {
            logger.severe("Could not register minion triggers: $failed")
        }

        try {
            if (ConditionMinionCount.register()) {
                logger.info("Registered the minion_count_above condition.")
            } else {
                logger.warning("EcoMinions is installed but its API could not be read: minion_count_above is off.")
            }
        } catch (failed: Throwable) {
            logger.severe("Could not register minion_count_above: $failed")
        }
    }

    override fun onDisable() {
        // libreforge has no unregister; the registries are rebuilt with the host on reload
    }
}
