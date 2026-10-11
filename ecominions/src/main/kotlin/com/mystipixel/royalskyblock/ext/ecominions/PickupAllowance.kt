package com.mystipixel.royalskyblock.ext.ecominions

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

// Lets minion_pickup fire at most once per minion a player has had placed at once, so picking up and
// re-placing the same minion can't repeat a payout. Stored as one "uuid peak used" line per player.
internal class PickupAllowance(private val file: Path?, private val logger: Logger? = null) {

    private class Entry(var peak: Int, var used: Int)

    private val entries = HashMap<UUID, Entry>()

    // one thread, so saves land in the order they were taken
    private val writer: ExecutorService? = file?.let {
        Executors.newSingleThreadExecutor { task -> Thread(task, "RoyalSkyblock-EcoMinions-pickups").apply { isDaemon = true } }
    }

    init {
        if (file != null && Files.exists(file)) {
            Files.readAllLines(file).forEach { line ->
                val parts = line.trim().split(' ')
                val owner = parts.getOrNull(0)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                val peak = parts.getOrNull(1)?.toIntOrNull()
                val used = parts.getOrNull(2)?.toIntOrNull()
                if (owner != null && peak != null && used != null) {
                    entries[owner] = Entry(peak, used)
                }
            }
        }
    }

    // owned is null when EcoMinions can't give a count
    fun observe(owner: UUID, owned: Int?) {
        if (owned == null) return
        val entry = entries.getOrPut(owner) { Entry(0, 0) }
        if (owned > entry.peak) {
            entry.peak = owned
            save()
        }
    }

    fun tryUse(owner: UUID, owned: Int?): Boolean {
        // without a count the payout can't be bounded, so the trigger stays quiet
        if (owned == null) return false
        observe(owner, owned)
        val entry = entries.getValue(owner)
        if (entry.used >= entry.peak) return false
        entry.used++
        save()
        return true
    }

    fun close() {
        writer?.shutdown()
        writer?.awaitTermination(5, TimeUnit.SECONDS)
    }

    private fun save() {
        val target = file ?: return
        val snapshot = entries.entries.joinToString("\n", postfix = "\n") { (owner, e) -> "$owner ${e.peak} ${e.used}" }
        writer?.execute {
            try {
                Files.createDirectories(target.parent)
                val temp = target.resolveSibling(target.fileName.toString() + ".tmp")
                Files.writeString(temp, snapshot)
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (failed: IOException) {
                logger?.log(Level.WARNING, "Could not save $target", failed)
            }
        }
    }
}
