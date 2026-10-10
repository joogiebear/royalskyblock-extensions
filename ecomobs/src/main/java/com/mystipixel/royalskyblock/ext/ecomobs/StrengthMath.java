package com.mystipixel.royalskyblock.ext.ecomobs;

import java.util.List;
import java.util.Locale;

// The arithmetic behind StrengthBridge, kept free of Bukkit so it can be unit tested.
final class StrengthMath {

    private StrengthMath() {
    }

    // clamped to [1, max]: never weakens a mob, never exceeds the cap
    static double multiplier(double level, double scalePerLevel, double max) {
        return Math.max(1.0, Math.min(max, 1.0 + level * scalePerLevel));
    }

    static boolean reasonAllowed(String reason, List<String> allowed) {
        for (String entry : allowed) {
            if (entry != null && entry.trim().toUpperCase(Locale.ROOT).equals(reason)) {
                return true;
            }
        }
        return false;
    }
}
