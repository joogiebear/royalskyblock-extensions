package com.mystipixel.royalskyblock.ext.ecominions

import com.mystipixel.royalskyblock.libreforge.RoyalTrigger
import com.willfp.libreforge.triggers.Triggers
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.lang.reflect.Method
import java.util.UUID

/**
 * Adds the minion triggers EcoMinions doesn't provide to players, usable from any eco plugin's config.
 *
 * EcoMinions registers its own `minion_pickup`, `minion_place`, `minion_upgrade`, `minion_collect` and
 * more. This adapter used to register the first three too, which put two triggers under one id; it now
 * leaves them to EcoMinions. EcoMinions dispatches its mining triggers (`minion_collect`,
 * `minion_mine_block`) to the minion, so only the minion's own type and upgrades see them, never a
 * player's perks or items. `minion_owner_mine` carries the same moment to the owner.
 *
 * Reflective because EcoMinions is not a compile dependency. Each trigger carries `player`, `location`
 * (the minion's) and `text` (the minion type id, so a config can filter by type).
 */
object MinionTriggers {

    private const val API_CLASS = "com.exanthiax.ecominions.api.EcoMinionsApi"
    private const val EVENT_PACKAGE = "com.exanthiax.ecominions.api.event."

    /** Fires when a player fuels a minion. `value` is the minion's level. */
    val FUEL = RoyalTrigger("minion_fuel", "Fires when a player fuels a minion.", "minions")

    /**
     * Fires for a minion's owner, while they are online, each time one of their minions mines a block.
     * `value` is how many items the block dropped.
     */
    val OWNER_MINE = RoyalTrigger(
        "minion_owner_mine",
        "Fires for a minion's owner, while online, each time one of their minions mines a block.",
        "minions"
    )

    /**
     * Returns false when EcoMinions is absent or reshaped. Registers only when the triggers can fire,
     * so a config referencing them gets an unknown-id error rather than silence, and resolves both
     * events first so it is all or nothing.
     */
    fun register(owner: Plugin): Boolean {
        val ecoMinions = Bukkit.getPluginManager().getPlugin("EcoMinions") ?: return false
        val loader = ecoMinions.javaClass.classLoader
        val bindings = try {
            // the API type's presence is the compatibility check
            Class.forName(API_CLASS, false, loader)
            listOf(fuel(loader), ownerMine(loader))
        } catch (notEcoMinions: Throwable) {
            return false
        }
        bindings.forEach { it.bind(owner) }
        return true
    }

    // the player who fuelled it; value is the minion's level
    private fun fuel(loader: ClassLoader): Binding {
        val minion = MinionAccess.resolve(loader, "MinionFuelEvent")
        val getPlayer = minion.eventClass.getMethod("getPlayer")
        return Binding(minion, FUEL) { event, it ->
            val player = getPlayer.invoke(event) as? Player ?: return@Binding null
            player to ((minion.getLevel.invoke(it) as? Int)?.toDouble() ?: 0.0)
        }
    }

    // the owner, if online; value is the number of items dropped
    private fun ownerMine(loader: ClassLoader): Binding {
        val minion = MinionAccess.resolve(loader, "MinionMineEvent")
        val getOwner = minion.minionType.getMethod("getOwner")
        val getDrops = minion.eventClass.getMethod("getDrops")
        return Binding(minion, OWNER_MINE) { event, it ->
            if ((event as? Cancellable)?.isCancelled == true) {
                return@Binding null
            }
            val ownerId = getOwner.invoke(it) as? UUID ?: return@Binding null
            val player = Bukkit.getPlayer(ownerId) ?: return@Binding null
            val drops = (getDrops.invoke(event) as? List<*>)?.sumOf { (it as? ItemStack)?.amount ?: 0 } ?: 0
            player to drops.toDouble()
        }
    }

    /** The methods every minion event shares, resolved off the declared types. */
    private class MinionAccess(
        val eventClass: Class<out Event>,
        val minionType: Class<*>,
        val getMinion: Method,
        val getLocation: Method,
        val getLevel: Method,
        val getType: Method,
        val getTypeId: Method
    ) {
        companion object {
            fun resolve(loader: ClassLoader, simpleName: String): MinionAccess {
                @Suppress("UNCHECKED_CAST")
                val eventClass = Class.forName(EVENT_PACKAGE + simpleName, false, loader) as Class<out Event>
                // resolve off the declared Minion interface: the runtime class may be non-public and
                // reject the call
                val getMinion = eventClass.getMethod("getMinion")
                val minionType = getMinion.returnType
                val getType = minionType.getMethod("getType")
                return MinionAccess(
                    eventClass = eventClass,
                    minionType = minionType,
                    getMinion = getMinion,
                    getLocation = minionType.getMethod("getLocation"),
                    getLevel = minionType.getMethod("getLevel"),
                    getType = getType,
                    getTypeId = getType.returnType.getMethod("getId")
                )
            }
        }
    }

    /** One event bound to one trigger; [target] picks the player and value, or null to skip the event. */
    private class Binding(
        val access: MinionAccess,
        val trigger: RoyalTrigger,
        val target: (Event, Any) -> Pair<Player, Double>?
    ) {
        fun bind(owner: Plugin) {
            Triggers.register(trigger)

            Bukkit.getPluginManager().registerEvent(
                access.eventClass,
                EmptyListener,
                EventPriority.MONITOR, // observing only: never alter what EcoMinions is doing
                { _, event -> if (access.eventClass.isInstance(event)) dispatch(event) },
                owner
            )
        }

        private fun dispatch(event: Event) {
            try {
                val minion = access.getMinion.invoke(event) ?: return
                val (player, value) = target(event, minion) ?: return
                trigger.fire(
                    player,
                    access.getLocation.invoke(minion) as? Location,
                    access.getTypeId.invoke(access.getType.invoke(minion)) as? String,
                    value
                )
            } catch (failed: Throwable) {
                // EcoMinions changed shape: drop this dispatch rather than throw inside another
                // plugin's event
            }
        }
    }

    // the executor does the work; Bukkit just needs a listener to own the registration
    private object EmptyListener : Listener
}
