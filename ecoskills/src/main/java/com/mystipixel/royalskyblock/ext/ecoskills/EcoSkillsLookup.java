package com.mystipixel.royalskyblock.ext.ecoskills;

import com.mystipixel.royalskyblock.hooks.CombatLevelSource;
import org.bukkit.OfflinePlayer;

import java.lang.reflect.Method;

/**
 * Reads one EcoSkills skill or stat for a player, by reflection.
 *
 * <p>Reflective rather than compiled: EcoSkills' POM pulls unresolvable NMS submodules, so there is
 * nothing to compile against. It is also what keeps the hook soft — this class loads fine on a server
 * with no EcoSkills, reports itself unusable, and nothing else has to care.
 *
 * <p>Skills and stats are the same lookup three names apart — a registry singleton with
 * {@code getByID}, an element type, and an API method taking {@code (OfflinePlayer, element)}. They
 * were two near-identical classes in the core; parameterising the three names is what let them become
 * one, and is why adding "read a third kind of thing" is a static factory rather than a third copy.
 */
final class EcoSkillsLookup implements CombatLevelSource {

    private final String elementId;
    private final int fallback;

    private Object registry;       // Skills.INSTANCE / Stats.INSTANCE
    private Method getByID;        // registry.getByID(String) -> element?
    private Method levelMethod;    // static EcoSkillsAPI.getXLevel(OfflinePlayer, element) -> int
    private Object element;        // resolved lazily
    private final boolean wired;

    /** A reader for a skill — the level a player has trained. */
    static EcoSkillsLookup skill(String skillId, int fallback) {
        return new EcoSkillsLookup("com.willfp.ecoskills.skills.Skills",
                "com.willfp.ecoskills.skills.Skill", "getSkillLevel", skillId, fallback);
    }

    /**
     * A reader for a stat.
     *
     * <p>Uses {@code getStatLevel}, which includes bonuses from modifiers — so a stat granted by a
     * talisman (Talismans' {@code add_stat}) counts, which is the entire point of reading it.
     */
    static EcoSkillsLookup stat(String statId, int fallback) {
        return new EcoSkillsLookup("com.willfp.ecoskills.stats.Stats",
                "com.willfp.ecoskills.stats.Stat", "getStatLevel", statId, fallback);
    }

    private EcoSkillsLookup(String registryClass, String elementClass, String apiMethod,
                            String elementId, int fallback) {
        this.elementId = elementId;
        this.fallback = fallback;

        boolean ok;
        try {
            Class<?> registryType = Class.forName(registryClass);
            this.registry = registryType.getField("INSTANCE").get(null);
            this.getByID = registryType.getMethod("getByID", String.class);
            this.levelMethod = Class.forName("com.willfp.ecoskills.api.EcoSkillsAPI")
                    .getMethod(apiMethod, OfflinePlayer.class, Class.forName(elementClass));
            ok = true;
        } catch (Throwable notPresent) {
            ok = false;
        }
        this.wired = ok;
    }

    /** Whether the EcoSkills API was reachable. The element itself resolves later — see {@link #levelOf}. */
    boolean wired() {
        return wired;
    }

    /**
     * The player's level, or the fallback.
     *
     * <p>The element is resolved on first read, not in the constructor: EcoSkills registers its skills
     * and stats during its own enable, which can happen after this is built, so asking too early would
     * report a perfectly good id as missing. By the time anything reads a level, the registry is
     * populated.
     */
    @Override
    public int levelOf(OfflinePlayer player) {
        if (!wired || player == null) {
            return fallback;
        }
        try {
            if (element == null) {
                element = getByID.invoke(registry, elementId);
            }
            if (element == null) {
                return fallback;
            }
            return levelMethod.invoke(null, player, element) instanceof Integer level ? level : fallback;
        } catch (Throwable failed) {
            return fallback;
        }
    }
}
