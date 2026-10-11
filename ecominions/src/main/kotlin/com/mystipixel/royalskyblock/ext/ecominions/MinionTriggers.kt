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
 * Exposes EcoMinions activity to libreforge as triggers, usable from any eco plugin's config.
 *
 * Reflective because EcoMinions is not a compile dependency. Each trigger carries `player`, `location`
 * (the minion's), `text` (the minion type id, so a config can filter by type) and `value` (the
 * minion's level, or the tier reached for an upgrade).
 */
object MinionTriggers {

    private const val API_CLASS = "com.exanthiax.ecominions.api.EcoMinionsApi"
    private const val EVENT_PACKAGE = "com.exanthiax.ecominions.api.event."

    /**
     * Fires when a player picks a minion up out of the world, at most once per minion they have had
     * placed at once (see [PickupAllowance]). `value` is the minion's level.
     */
    val PICKUP = RoyalTrigger("minion_pickup", "Fires when a player picks up a minion, once per minion they own.", "minions")

    /** Fires when a player places a minion. `value` is the minion's level. */
    val PLACE = RoyalTrigger("minion_place", "Fires when a player places a minion.", "minions")

    /** Fires when a player upgrades a minion. `value` is the tier reached. */
    val UPGRADE = RoyalTrigger("minion_upgrade", "Fires when a player upgrades a minion.", "minions")

    /** Fires when a player fuels a minion. `value` is the minion's level. */
    val FUEL = RoyalTrigger("minion_fuel", "Fires when a player fuels a minion.", "minions")

    /**
     * Returns false when EcoMinions is absent or reshaped. Registers only when the triggers can fire,
     * so a config referencing them gets an unknown-id error rather than silence, and resolves all four
     * events first so it is all or nothing.
     */
    internal fun register(owner: Plugin, pickups: PickupAllowance): Boolean {
        val ecoMinions = Bukkit.getPluginManager().getPlugin("EcoMinions") ?: return false
        val loader = ecoMinions.javaClass.classLoader
        val bindings = try {
            // the API type's presence is the compatibility check
            Class.forName(API_CLASS, false, loader)

            listOf(
                resolve(loader, "MinionPickupEvent", PICKUP, MinionValue.LEVEL) { player ->
                    pickups.tryUse(player.uniqueId, ConditionMinionCount.countFor(player))
                },
                resolve(loader, "MinionPlaceEvent", PLACE, MinionValue.LEVEL) { player ->
                    pickups.observe(player.uniqueId, ConditionMinionCount.countFor(player))
                    true
                },
                resolve(loader, "MinionUpgradeEvent", UPGRADE, MinionValue.TIER),
                resolve(loader, "MinionFuelEvent", FUEL, MinionValue.LEVEL)
            )
        } catch (notEcoMinions: Throwable) {
            return false
        }
        bindings.forEach { it.bind(owner) }
        return true
    }

    private enum class MinionValue { LEVEL, TIER }

    private class Binding(
        val eventClass: Class<out Event>,
        val trigger: RoyalTrigger,
        val getMinion: Method,
        val getPlayer: Method,
        val getLocation: Method,
        val getLevel: Method,
        val getType: Method,
        val getTypeId: Method,
        val getTier: Method?,
        val allow: (Player) -> Boolean
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
                if (minion != null && player != null && allow(player)) {
                    trigger.fire(
                        player,
                        getLocation.invoke(minion) as? Location,
                        getTypeId.invoke(getType.invoke(minion)) as? String,
                        ((getTier?.invoke(event) ?: getLevel.invoke(minion)) as? Int)?.toDouble() ?: 0.0
                    )
                }
            } catch (failed: Throwable) {
                // EcoMinions changed shape: drop this dispatch rather than throw inside another
                // plugin's event
            }
        }
    }

    private fun resolve(
        loader: ClassLoader,
        simpleName: String,
        trigger: RoyalTrigger,
        value: MinionValue,
        allow: (Player) -> Boolean = { true }
    ): Binding {
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
            getTypeId = getType.returnType.getMethod("getId"),
            getTier = if (value == MinionValue.TIER) eventClass.getMethod("getTier") else null,
            allow = allow
        )
    }

    // the executor does the work; Bukkit just needs a listener to own the registration
    private object EmptyListener : Listener
}
