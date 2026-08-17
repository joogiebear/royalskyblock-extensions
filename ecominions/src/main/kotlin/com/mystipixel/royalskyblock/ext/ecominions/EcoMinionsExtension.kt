package com.mystipixel.royalskyblock.ext.ecominions

import com.mystipixel.royalskyblock.RoyalSkyblockPlugin
import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.extensions.Extension
import org.bukkit.Bukkit

/**
 * Publishes EcoMinions activity to libreforge, for the whole eco suite.
 *
 * Unlike the other adapters this one gives RoyalSkyblock nothing in particular. It registers four
 * triggers and a condition, and libreforge elements registered by any plugin work in all of them — so
 * a talisman, a quest, a booster or an island perk can all react to minions from the moment this jar
 * is present. RoyalSkyblock is where it lives, not who it is for.
 *
 * ## Why registration must happen in onEnable
 *
 * eco enables extensions **before** the host's own enable, and RoyalSkyblock compiles every perk and
 * upgrade effect chain during that enable. A chain referencing `minion_pickup` fails to compile with
 * an unknown-id violation if the trigger is not registered yet — which is exactly what happened to the
 * Minion Overseer perk when this lived in the core and ran a few lines too late.
 *
 * Being an extension is what makes this safe rather than delicate: onEnable is unconditionally earlier
 * than anything the host does, instead of one ordering constraint among many inside a single method.
 */
class EcoMinionsExtension(plugin: EcoPlugin) : Extension(plugin) {

    override fun onEnable() {
        val host = RoyalSkyblockPlugin.get()

        if (!host.extensionEnabled(name)) {
            logger.info("$name is listed in extensions.disabled — not registering.")
            return
        }

        if (Bukkit.getPluginManager().getPlugin("EcoMinions") == null) {
            logger.warning("EcoMinions is not installed — no minion triggers or conditions registered.")
            return
        }

        // Wrapped: an extension that throws in onEnable disables its host, and every call below is
        // reflective against a plugin that updates on its own schedule.
        try {
            val triggers = MinionTriggers.register(plugin)
            val condition = ConditionMinionCount.register()
            if (triggers && condition) {
                logger.info(
                    "Registered minion triggers (minion_pickup, minion_place, minion_upgrade, "
                        + "minion_fuel) and the minion_count_above condition."
                )
            } else {
                logger.warning(
                    "EcoMinions is installed but its API could not be read — minion elements are off."
                )
            }
        } catch (failed: Throwable) {
            logger.severe("Could not register minion elements: $failed")
        }
    }

    override fun onDisable() {
        // libreforge has no unregister for elements, and there is nothing to undo: on a reload the
        // registries are rebuilt with the host, and a registered element with no events bound to it
        // simply never fires.
    }
}
