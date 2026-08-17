package com.mystipixel.royalskyblock.ext.ecomobs;

import com.mystipixel.royalskyblock.RoyalSkyblockPlugin;
import com.willfp.eco.core.EcoPlugin;
import com.willfp.eco.core.extensions.Extension;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Teaches RoyalSkyblock to spawn EcoMobs mobs, and to scale them to island level.
 *
 * <p>Install EcoMobs and this jar and island mobs work. Install EcoMobs without this and RoyalSkyblock
 * does not know it exists — the core carries no EcoMobs code and no check for the plugin's name.
 *
 * <h2>Two registrations, at two different times</h2>
 *
 * <p>The <b>mob provider</b> is registered in {@code onEnable}, because that is what makes it visible:
 * eco enables extensions before the host's own enable, so the provider is in the registry by the time
 * RoyalSkyblock starts island mob spawning and looks for it. Register it any later and the feature
 * silently never starts.
 *
 * <p>The <b>strength bridge</b> waits for {@code onAfterLoad}. It has no ordering requirement, and it
 * reads {@code islands()} when a mob spawns — so it belongs on the far side of the host's enable, with
 * everything else that touches host services.
 *
 * <p>Both are wrapped. An extension that throws in {@code onEnable} takes its host down, and this is
 * reflective glue against a plugin that updates on its own schedule.
 */
public final class EcoMobsExtension extends Extension {

    private FileConfiguration config;

    public EcoMobsExtension(EcoPlugin plugin) {
        super(plugin);
    }

    /** This extension's settings — how island level scales a mob, and nothing about EcoMobs itself. */
    public FileConfiguration config() {
        return config;
    }

    @Override
    protected void onEnable() {
        RoyalSkyblockPlugin host = RoyalSkyblockPlugin.Companion.get();

        if (!host.extensionEnabled(getName())) {
            getLogger().info(getName() + " is listed in extensions.disabled — not registering.");
            return;
        }

        this.config = loadConfig();

        try {
            host.integrations().registerMobProvider(new EcoMobsProvider());
        } catch (Throwable failed) {
            getLogger().severe("Could not register the EcoMobs backend: " + failed);
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("EcoMobs") == null) {
            getLogger().warning("Registered, but EcoMobs is not installed — this extension does nothing "
                    + "until it is.");
            return;
        }
        getLogger().info("EcoMobs backend registered — set island-mobs.provider to 'ecomobs' to use it.");
    }

    @Override
    protected void onAfterLoad() {
        if (config == null) {
            return;                              // disabled, or enable bailed out
        }
        if (!config.getBoolean("strength.enabled", true)) {
            return;
        }
        try {
            if (StrengthBridge.register(this, getPlugin())) {
                getLogger().info("Island-level mob strength scaling active.");
            } else if (Bukkit.getPluginManager().getPlugin("EcoMobs") != null) {
                getLogger().warning("EcoMobs is installed but its spawn event could not be resolved — "
                        + "mob strength scaling is off.");
            }
        } catch (Throwable failed) {
            getLogger().severe("Mob strength scaling could not start: " + failed);
        }
    }

    @Override
    protected void onDisable() {
        this.config = null;
    }

    /**
     * Read {@code config.yml} from this extension's own folder, writing the bundled default the first
     * time.
     *
     * <p>Its own folder, not {@link #getDataFolder()} — that returns the <em>host's</em> directory, so
     * every extension using it writes into {@code plugins/RoyalSkyblock/} directly, mixed in with the
     * host's own files and with no way to tell which belong to what.
     *
     * <p><b>And the default is read out of this jar by hand, not through the classloader.</b>
     * {@code getResourceAsStream("/config.yml")} resolves parent-first, so it returns
     * <em>RoyalSkyblock's</em> config.yml — an extension asking for a resource by a common name gets
     * the host's copy, silently and with no error. Opening our own jar is the only lookup that can
     * only ever find our own file, and it stays correct however eco arranges extension classloaders.
     *
     * <p>A failure here returns empty config rather than throwing: every read has a sensible default,
     * so the extension runs on defaults and says why, instead of taking the server down over a file.
     */
    private FileConfiguration loadConfig() {
        File dir = new File(new File(getPlugin().getDataFolder(), "extensions"), getName());
        File file = new File(dir, "config.yml");
        try {
            if (!file.exists()) {
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new IOException("could not create " + dir);
                }
                writeBundledDefault(file);
                getLogger().info("Wrote default config to " + file.getPath() + ".");
            }
            return YamlConfiguration.loadConfiguration(file);
        } catch (IOException problem) {
            getLogger().warning("Could not read " + file + " (" + problem.getMessage()
                    + ") — running on defaults.");
            return new YamlConfiguration();
        }
    }

    /** Copy {@code config.yml} out of this extension's jar, by opening the jar itself. */
    private void writeBundledDefault(File target) throws IOException {
        try (JarFile jar = new JarFile(getFile())) {
            JarEntry entry = jar.getJarEntry("config.yml");
            if (entry == null) {
                throw new IOException("config.yml is missing from " + getFile().getName());
            }
            try (InputStream bundled = jar.getInputStream(entry)) {
                Files.copy(bundled, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
