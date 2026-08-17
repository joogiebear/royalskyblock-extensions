package com.mystipixel.royalskyblock.ext.ecoskills;

import com.mystipixel.royalskyblock.api.ProgressionProvider;
import com.mystipixel.royalskyblock.hooks.CombatLevelSource;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

/**
 * EcoSkills, as a progression backend RoyalSkyblock can ask questions of.
 *
 * <p>Which skill counts as "combat" and which stat as "intimidation" are not decided here — they are
 * admin config in RoyalSkyblock, because they are facts about island mobs rather than about EcoSkills.
 * This backend is only asked for a reader by id and says whether it can supply one, which is why this
 * extension ships no config file of its own.
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
     * A reader for a skill, or null if EcoSkills cannot be reached at all.
     *
     * <p>Null means "no backend", never "no such skill". Whether {@code combat} exists genuinely cannot
     * be answered at startup — EcoSkills registers its skills during its own enable — so answering it
     * here would report healthy configs as broken on every boot.
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
