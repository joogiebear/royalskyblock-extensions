package com.mystipixel.royalskyblock.ext.ecomobs;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrengthMathTest {

    @Test
    void scalesLinearlyWithLevel() {
        assertEquals(2.0, StrengthMath.multiplier(500, 0.002, 5.0), 1e-9);  // the documented example
    }

    @Test
    void capsAtMaxMultiplier() {
        assertEquals(5.0, StrengthMath.multiplier(1_000_000, 0.002, 5.0), 1e-9);
    }

    @Test
    void neverWeakensAMob() {
        assertEquals(1.0, StrengthMath.multiplier(500, -0.01, 5.0), 1e-9);  // negative scale
        assertEquals(1.0, StrengthMath.multiplier(500, 0.002, 0.5), 1e-9);  // cap below 1
        assertEquals(1.0, StrengthMath.multiplier(0, 0.002, 5.0), 1e-9);
    }

    @Test
    void matchesReasonsIgnoringCaseAndSpaces() {
        List<String> allowed = List.of("command", " Natural ");
        assertTrue(StrengthMath.reasonAllowed("COMMAND", allowed));
        assertTrue(StrengthMath.reasonAllowed("NATURAL", allowed));
        assertFalse(StrengthMath.reasonAllowed("SPAWNER", allowed));
    }

    @Test
    void emptyListAllowsNothing() {
        assertFalse(StrengthMath.reasonAllowed("COMMAND", List.of()));
    }
}
