package com.mystipixel.royalskyblock.ext.ecoskills;

import com.mystipixel.royalskyblock.api.ProgressionProvider;
import com.mystipixel.royalskyblock.hooks.CombatLevelSource;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

/**
 * EcoSkills as a RoyalSkyblock progression backend. Which skill counts as "combat" and which stat as
 * "intimidation" is RoyalSkyblock config, so this extension ships no config of its own.
 */
public final class EcoSkillsProgression implements ProgressionProvider {

    static final String ID = "ecoskills";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("EcoSkills");
    }

    /**
     * Null means EcoSkills can't be reached, never "no such skill": skills register during EcoSkills'
     * own enable, so checking the id here would flag healthy configs on every boot.
     */
    @Override
    public @Nullable CombatLevelSource skill(String skillId, int fallback) {
        EcoSkillsLookup lookup = EcoSkillsLookup.skill(skillId, fallback);
        return lookup.wired() ? lookup : null;
    }

    @Override
    public @Nullable CombatLevelSource stat(String statId, int fallback) {
        EcoSkillsLookup lookup = EcoSkillsLookup.stat(statId, fallback);
        return lookup.wired() ? lookup : null;
    }
}
