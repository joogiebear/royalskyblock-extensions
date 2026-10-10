package com.mystipixel.royalskyblock.ext.ecoskills;

import com.mystipixel.royalskyblock.RoyalSkyblockPlugin;
import com.willfp.eco.core.EcoPlugin;
import com.willfp.eco.core.extensions.Extension;
import org.bukkit.Bukkit;

/**
 * Lets RoyalSkyblock read EcoSkills skills and stats.
 *
 * <p>Registers in {@code onEnable}, which eco runs before the host's enable, so the backend is in place
 * before island mob spawning starts. Host services don't exist yet, so nothing here may touch them.
 * Wrapped because an extension that throws in {@code onEnable} takes its host down.
 */
public final class EcoSkillsExtension extends Extension {

    public EcoSkillsExtension(EcoPlugin plugin) {
        super(plugin);
    }

    @Override
    protected void onEnable() {
        RoyalSkyblockPlugin host = RoyalSkyblockPlugin.Companion.get();

        if (!host.extensionEnabled(getName())) {
            getLogger().info(getName() + " is listed in extensions.disabled, not registering.");
            return;
        }

        try {
            host.integrations().registerProgressionProvider(new EcoSkillsProgression());
        } catch (Throwable failed) {
            getLogger().severe("Could not register the EcoSkills backend: " + failed);
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("EcoSkills") == null) {
            // registered anyway: the backend reports itself unavailable and nothing selects it
            getLogger().warning("Registered, but EcoSkills is not installed: this extension does nothing "
                    + "until it is.");
            return;
        }
        getLogger().info("EcoSkills backend registered: skills and stats are readable.");
    }

    @Override
    protected void onDisable() {
        // nothing to undo: the registry lives and dies with the host
    }
}
