package com.mystipixel.royalskyblock.ext.ecominions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class PickupAllowanceTest {

    private val owner = UUID.randomUUID()

    // place then pick up the same minion, as the overseer exploit does; returns how many pickups fired
    private fun cycle(allowance: PickupAllowance, times: Int, placed: Int): Int {
        var fired = 0
        repeat(times) {
            allowance.observe(owner, placed)
            if (allowance.tryUse(owner, placed - 1)) fired++
        }
        return fired
    }

    @Test
    fun placeAndPickupLoopStopsAtTheMinionCount() {
        val allowance = PickupAllowance(null)
        assertEquals(12, cycle(allowance, 100, placed = 12))
        assertEquals(0, cycle(allowance, 100, placed = 12))
    }

    @Test
    fun aNewMinionEarnsOneMorePickup() {
        val allowance = PickupAllowance(null)
        cycle(allowance, 100, placed = 12)
        assertEquals(1, cycle(allowance, 100, placed = 13))
    }

    @Test
    fun usedPickupsSurviveARestart(@TempDir dir: Path) {
        val file = dir.resolve("pickups.txt")
        val first = PickupAllowance(file)
        assertEquals(12, cycle(first, 100, placed = 12))
        first.close()

        val second = PickupAllowance(file)
        assertEquals(0, cycle(second, 100, placed = 12))
        second.close()
    }

    @Test
    fun noCountMeansNoPickup() {
        val allowance = PickupAllowance(null)
        allowance.observe(owner, 12)
        assertFalse(allowance.tryUse(owner, null))
        assertTrue(allowance.tryUse(owner, 12))
    }

    @Test
    fun ownersAreCountedSeparately() {
        val allowance = PickupAllowance(null)
        cycle(allowance, 100, placed = 12)
        val other = UUID.randomUUID()
        allowance.observe(other, 3)
        var fired = 0
        repeat(10) { if (allowance.tryUse(other, 3)) fired++ }
        assertEquals(3, fired)
    }
}
