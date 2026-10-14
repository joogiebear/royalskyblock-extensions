package com.mystipixel.royalskyblock.ext.ecominions

import com.mystipixel.royalskyblock.RoyalSkyblockPlugin
import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.extensions.Extension
import org.bukkit.Bukkit

/**
 * Adds what EcoMinions doesn't publish to libreforge itself (the `minion_fuel` trigger and the
 * `minion_count_above` condition) for the whole eco suite. EcoMinions' own triggers, such as
 * `minion_collect` and `minion_pickup`, come from EcoMinions.
 *
 * Registration must happen in onEnable: eco enables extensions before the host's enable, where
 * RoyalSkyblock compiles its perk and upgrade effect chains, and a chain referencing `minion_fuel`
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
            logger.warning("EcoMinions is not installed: minion_fuel and minion_count_above not registered.")
            return
        }

        // each wrapped and reported separately: an extension that throws in onEnable disables its host
        try {
            if (MinionTriggers.register(plugin)) {
                logger.info("Registered the minion_fuel trigger.")
            } else {
                logger.warning("EcoMinions is installed but its events could not be read: minion_fuel is off.")
            }
        } catch (failed: Throwable) {
            logger.severe("Could not register minion_fuel: $failed")
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
