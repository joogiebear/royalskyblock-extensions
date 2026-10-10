package com.mystipixel.royalskyblock.ext.ecoskills;

import com.mystipixel.royalskyblock.hooks.CombatLevelSource;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

// Reads one EcoSkills skill or stat for a player. Reflective because EcoSkills' POM pulls
// unresolvable NMS submodules; classes come from EcoSkills' own classloader.
final class EcoSkillsLookup implements CombatLevelSource {

    private final String elementId;
    private final int fallback;

    private Object registry;       // Skills.INSTANCE / Stats.INSTANCE
    private Method getByID;        // registry.getByID(String) -> element?
    private Method levelMethod;    // static EcoSkillsAPI.getXLevel(OfflinePlayer, element) -> int
    private Object element;        // resolved lazily
    private final boolean wired;

    static EcoSkillsLookup skill(String skillId, int fallback) {
        return new EcoSkillsLookup("com.willfp.ecoskills.skills.Skills",
                "com.willfp.ecoskills.skills.Skill", "getSkillLevel", skillId, fallback);
    }

    // getStatLevel includes modifier bonuses, so a stat granted by a talisman counts
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
            Plugin ecoSkills = Bukkit.getPluginManager().getPlugin("EcoSkills");
            if (ecoSkills == null) {
                throw new ClassNotFoundException("EcoSkills is not installed");
            }
            ClassLoader loader = ecoSkills.getClass().getClassLoader();
            Class<?> registryType = Class.forName(registryClass, true, loader);
            this.registry = registryType.getField("INSTANCE").get(null);
            this.getByID = registryType.getMethod("getByID", String.class);
            this.levelMethod = Class.forName("com.willfp.ecoskills.api.EcoSkillsAPI", true, loader)
                    .getMethod(apiMethod, OfflinePlayer.class, Class.forName(elementClass, true, loader));
            ok = true;
        } catch (Throwable notPresent) {
            ok = false;
        }
        this.wired = ok;
    }

    // the element itself resolves later, in levelOf
    boolean wired() {
        return wired;
    }

    // The element is resolved on first read: EcoSkills registers its skills and stats during its own
    // enable, which can come after this is built.
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
