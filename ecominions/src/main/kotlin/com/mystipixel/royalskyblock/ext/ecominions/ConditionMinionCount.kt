package com.mystipixel.royalskyblock.ext.ecominions

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.Dispatcher
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.ProvidedHolder
import com.willfp.libreforge.arguments
import com.willfp.libreforge.conditions.Condition
import com.willfp.libreforge.conditions.Conditions
import com.willfp.libreforge.get
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import java.lang.reflect.Method

/**
 * `minion_count_above`: passes when the player owns more than `count` minions.
 *
 * ```yaml
 * conditions:
 *   - id: minion_count_above
 *     args:
 *       count: 10
 * ```
 *
 * `count` is a libreforge expression, so placeholders and maths work
 * (`count: "%royalskyblock_island_level% / 10"`).
 */
object ConditionMinionCount : Condition<NoCompileData>("minion_count_above") {

    override val description = "Passes when the player owns more than the specified number of minions."

    override val categories = setOf("minions")

    override val arguments = arguments {
        // Four-argument overload on purpose: the two-argument form links against a synthetic
        // require$default bridge, which throws NoSuchMethodError when libreforge changes its parameters.
        require<Any?>("count", "You must specify the minion count!", { key: String -> this.get(key) }, { value: Any? -> value != null })
    }

    // resolved once in register()
    private var apiGetOrNull: Method? = null

    private var minions: Method? = null

    private var countOwnedBy: Method? = null

    /** Binds to EcoMinions and registers; false when EcoMinions is absent or reshaped. */
    fun register(): Boolean {
        val ecoMinions = Bukkit.getPluginManager().getPlugin("EcoMinions") ?: return false
        return try {
            val apiClass = Class.forName(
                "com.exanthiax.ecominions.api.EcoMinionsApi", false, ecoMinions.javaClass.classLoader
            )
            // getOrNull() rather than get(): get() throws when EcoMinions has not finished starting,
            // and a condition must never blow up mid-evaluation inside someone else's effect chain.
            apiGetOrNull = apiClass.getMethod("getOrNull")
            val minionsMethod = apiClass.getMethod("minions")
            minions = minionsMethod
            // Resolve off the declared MinionsManager interface, not the runtime implementation, which
            // may be non-public and reject the call.
            countOwnedBy = minionsMethod.returnType.getMethod("countOwnedBy", OfflinePlayer::class.java)

            Conditions.register(this)
            true
        } catch (notEcoMinions: Throwable) {
            false
        }
    }

    override fun isMet(
        dispatcher: Dispatcher<*>,
        config: Config,
        holder: ProvidedHolder,
        compileData: NoCompileData
    ): Boolean {
        val player = dispatcher.get<Player>() ?: return false
        val owned = countFor(player) ?: return false
        return owned > config.getIntFromExpression("count", player)
    }

    // null if EcoMinions can't answer right now
    private fun countFor(player: Player): Int? = try {
        val api = apiGetOrNull?.invoke(null)
        if (api == null) {
            null
        } else {
            countOwnedBy?.invoke(minions?.invoke(api), player) as? Int
        }
    } catch (failed: Throwable) {
        null // EcoMinions changed shape: fail the condition rather than the whole chain
    }
}
