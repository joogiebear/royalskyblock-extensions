package com.mystipixel.royalskyblock.ext.ecomobs;

import java.util.List;
import java.util.Locale;

/**
 * The arithmetic and filtering behind {@link StrengthBridge}, kept free of Bukkit so it can be unit
 * tested without a server.
 */
final class StrengthMath {

    private StrengthMath() {
    }

    /** {@code 1 + level*scale}, clamped to {@code [1, max]} — never weakens a mob, never exceeds the cap. */
    static double multiplier(double level, double scalePerLevel, double max) {
        return Math.max(1.0, Math.min(max, 1.0 + level * scalePerLevel));
    }

    /** Whether {@code reason} appears in {@code allowed}, ignoring case and surrounding spaces. */
    static boolean reasonAllowed(String reason, List<String> allowed) {
        for (String entry : allowed) {
            if (entry != null && entry.trim().toUpperCase(Locale.ROOT).equals(reason)) {
                return true;
            }
        }
        return false;
    }
}
