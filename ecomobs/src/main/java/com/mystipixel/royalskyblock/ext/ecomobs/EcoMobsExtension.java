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
 * Lets RoyalSkyblock spawn EcoMobs mobs and scale them to island level.
 *
 * <p>The mob provider registers in {@code onEnable} because eco enables extensions before the host's
 * enable; any later and island mob spawning never finds it. The strength bridge waits for
 * {@code onAfterLoad} because it reads host services.
 */
public final class EcoMobsExtension extends Extension {

    // null until enabled, when disabled, or when listed in extensions.disabled
    private volatile FileConfiguration config;

    public EcoMobsExtension(EcoPlugin plugin) {
        super(plugin);
    }

    /** This extension's settings: how island level scales a mob, not EcoMobs itself. */
    public FileConfiguration config() {
        return config;
    }

    @Override
    protected void onEnable() {
        RoyalSkyblockPlugin host = RoyalSkyblockPlugin.Companion.get();

        if (!host.extensionEnabled(getName())) {
            getLogger().info(getName() + " is listed in extensions.disabled, not registering.");
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
            getLogger().warning("Registered, but EcoMobs is not installed: this extension does nothing "
                    + "until it is.");
            return;
        }
        getLogger().info("EcoMobs backend registered. Set island-mobs.provider to 'ecomobs' to use it.");
    }

    @Override
    protected void onAfterLoad() {
        if (config == null) {
            return;                              // disabled, or enable bailed out
        }
        // Registered even with strength.enabled false: the bridge checks it per spawn, so a reload
        // can switch scaling on without a restart.
        try {
            if (StrengthBridge.register(this, getPlugin())) {
                getLogger().info(config.getBoolean("strength.enabled", true)
                        ? "Island-level mob strength scaling active."
                        : "Island-level mob strength scaling ready, but strength.enabled is false.");
            } else if (Bukkit.getPluginManager().getPlugin("EcoMobs") != null) {
                getLogger().warning("EcoMobs is installed but its spawn event could not be resolved: "
                        + "mob strength scaling is off.");
            }
        } catch (Throwable failed) {
            getLogger().severe("Mob strength scaling could not start: " + failed);
        }
    }

    // eco calls this on every host reload (and twice during startup)
    @Override
    protected void onReload() {
        if (config == null) {
            return;                              // disabled: stay disabled until a restart
        }
        this.config = loadConfig();
    }

    @Override
    protected void onDisable() {
        this.config = null;
    }

    // Reads extensions/<name>/config.yml, not getDataFolder() (that is the host's folder). The default
    // is copied out of this jar by hand: getResourceAsStream("/config.yml") resolves parent-first and
    // returns the host's config.yml.
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
                    + "), running on defaults.");
            return new YamlConfiguration();
        }
    }

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
