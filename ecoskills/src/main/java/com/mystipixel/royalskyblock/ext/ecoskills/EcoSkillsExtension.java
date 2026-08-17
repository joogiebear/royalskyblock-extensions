package com.mystipixel.royalskyblock.ext.ecoskills;

import com.mystipixel.royalskyblock.RoyalSkyblockPlugin;
import com.willfp.eco.core.EcoPlugin;
import com.willfp.eco.core.extensions.Extension;
import org.bukkit.Bukkit;

/**
 * Teaches RoyalSkyblock to read EcoSkills.
 *
 * <p>Install EcoSkills and this jar and island mobs scale to a player's combat skill and respect the
 * intimidation stat. Install EcoSkills without this and RoyalSkyblock does not know it exists — that
 * is the point of the split, not an oversight. The core carries no EcoSkills code and no check for
 * the plugin's name.
 *
 * <h2>Registration happens in onEnable, and must not throw</h2>
 *
 * <p>eco enables extensions <b>before</b> the host's own enable, which is what makes registering here
 * work: the backend is in the registry by the time RoyalSkyblock starts island mob spawning. It also
 * means the host's services do not exist yet, so nothing here may touch them.
 *
 * <p>And an extension that throws in {@code onEnable} takes its host down with it. This is reflective
 * glue against a plugin that updates on its own schedule, so the whole registration is wrapped: if
 * EcoSkills changes underneath it, this logs and registers nothing, and the skyblock server still
 * boots. A broken integration should cost one jar, not the server.
 */
public final class EcoSkillsExtension extends Extension {

    public EcoSkillsExtension(EcoPlugin plugin) {
        super(plugin);
    }

    @Override
    protected void onEnable() {
        RoyalSkyblockPlugin host = RoyalSkyblockPlugin.Companion.get();

        if (!host.extensionEnabled(getName())) {
            getLogger().info(getName() + " is listed in extensions.disabled — not registering.");
            return;
        }

        try {
            host.integrations().registerProgressionProvider(new EcoSkillsProgression());
        } catch (Throwable failed) {
            getLogger().severe("Could not register the EcoSkills backend: " + failed);
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("EcoSkills") == null) {
            // Registered anyway: the backend reports itself unavailable and nothing selects it. Said
            // out loud because "I installed the extension and nothing happened" is otherwise silent.
            getLogger().warning("Registered, but EcoSkills is not installed — this extension does nothing "
                    + "until it is.");
            return;
        }
        getLogger().info("EcoSkills backend registered — skills and stats are readable.");
    }

    @Override
    protected void onDisable() {
        // Nothing to undo. The registry lives and dies with the host, and a backend left in it while
        // the host is shutting down is never read again.
    }
}
