package com.mystipixel.royalskyblock.ext.ecominions

import com.mystipixel.royalskyblock.libreforge.RoyalTrigger
import com.willfp.libreforge.triggers.Triggers
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin
import java.lang.reflect.Method

/**
 * Adds the minion trigger EcoMinions doesn't register itself, usable from any eco plugin's config.
 *
 * EcoMinions registers its own `minion_pickup`, `minion_place`, `minion_upgrade`, `minion_collect` and
 * more. This adapter used to register the first three too, which put two triggers under one id; it now
 * leaves them to EcoMinions and only adds `minion_fuel`.
 *
 * Reflective because EcoMinions is not a compile dependency. The trigger carries `player`, `location`
 * (the minion's), `text` (the minion type id, so a config can filter by type) and `value` (the
 * minion's level).
 */
object MinionTriggers {

    private const val API_CLASS = "com.exanthiax.ecominions.api.EcoMinionsApi"
    private const val EVENT_PACKAGE = "com.exanthiax.ecominions.api.event."

    /** Fires when a player fuels a minion. `value` is the minion's level. */
    val FUEL = RoyalTrigger("minion_fuel", "Fires when a player fuels a minion.", "minions")

    /**
     * Returns false when EcoMinions is absent or reshaped. Registers only when the trigger can fire,
     * so a config referencing it gets an unknown-id error rather than silence.
     */
    fun register(owner: Plugin): Boolean {
        val ecoMinions = Bukkit.getPluginManager().getPlugin("EcoMinions") ?: return false
        val loader = ecoMinions.javaClass.classLoader
        val binding = try {
            // the API type's presence is the compatibility check
            Class.forName(API_CLASS, false, loader)
            resolve(loader, "MinionFuelEvent", FUEL)
        } catch (notEcoMinions: Throwable) {
            return false
        }
        binding.bind(owner)
        return true
    }

    private class Binding(
        val eventClass: Class<out Event>,
        val trigger: RoyalTrigger,
        val getMinion: Method,
        val getPlayer: Method,
        val getLocation: Method,
        val getLevel: Method,
        val getType: Method,
        val getTypeId: Method
    ) {
        fun bind(owner: Plugin) {
            Triggers.register(trigger)

            Bukkit.getPluginManager().registerEvent(
                eventClass,
                EmptyListener,
                EventPriority.MONITOR, // observing only: never alter what EcoMinions is doing
                { _, event -> if (eventClass.isInstance(event)) dispatch(event) },
                owner
            )
        }

        private fun dispatch(event: Event) {
            try {
                val minion = getMinion.invoke(event)
                val player = getPlayer.invoke(event) as? Player
                if (minion != null && player != null) {
                    trigger.fire(
                        player,
                        getLocation.invoke(minion) as? Location,
                        getTypeId.invoke(getType.invoke(minion)) as? String,
                        (getLevel.invoke(minion) as? Int)?.toDouble() ?: 0.0
                    )
                }
            } catch (failed: Throwable) {
                // EcoMinions changed shape: drop this dispatch rather than throw inside another
                // plugin's event
            }
        }
    }

    private fun resolve(loader: ClassLoader, simpleName: String, trigger: RoyalTrigger): Binding {
        @Suppress("UNCHECKED_CAST")
        val eventClass = Class.forName(EVENT_PACKAGE + simpleName, false, loader) as Class<out Event>

        // resolve off the declared Minion interface: the runtime class may be non-public and
        // reject the call
        val getMinion = eventClass.getMethod("getMinion")
        val minionType = getMinion.returnType
        val getType = minionType.getMethod("getType")
        return Binding(
            eventClass = eventClass,
            trigger = trigger,
            getMinion = getMinion,
            getPlayer = eventClass.getMethod("getPlayer"),
            getLocation = minionType.getMethod("getLocation"),
            getLevel = minionType.getMethod("getLevel"),
            getType = getType,
            getTypeId = getType.returnType.getMethod("getId")
        )
    }

    // the executor does the work; Bukkit just needs a listener to own the registration
    private object EmptyListener : Listener
}
