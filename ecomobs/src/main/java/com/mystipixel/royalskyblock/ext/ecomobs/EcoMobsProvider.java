package com.mystipixel.royalskyblock.ext.ecomobs;

import com.mystipixel.royalskyblock.hooks.IslandMobProvider;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Spawns EcoMobs custom mobs on behalf of RoyalSkyblock.
 *
 * <p>Reflective rather than compiled: the registry lookup ({@code EcoMobs.getByID}) is inherited from
 * libreforge's {@code RegistrableCategory}, which is not resolvable from EcoMobs' published
 * artifacts — and reflection is what keeps this jar loadable on a server with no EcoMobs
 * installed at all.
 *
 * <p>Classes are resolved from EcoMobs' own classloader, not this jar's: the extension classloader
 * delegates to the host's, which only sees EcoMobs if the host happens to declare it. The handles
 * are resolved once, on the first spawn, and reused.
 *
 * <p>Spawns with EcoMobs' {@code COMMAND} reason, because RoyalSkyblock is the deliberate spawner
 * here; natural monster spawning is expected to be off on islands via the island gamerules.
 */
public final class EcoMobsProvider implements IslandMobProvider {

    static final String ID = "ecomobs";

    /** Resolved on first use; stays null while EcoMobs is absent or unreadable, so the next spawn retries. */
    private volatile Handles handles;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("EcoMobs");
    }

    @Override
    public Entity spawn(String mobId, Location location) {
        try {
            Handles h = handles();
            if (h == null) {
                return null;
            }
            Object mob = h.getByID.invoke(h.registry, mobId);
            if (mob == null) {
                return null;                     // no such mob configured in EcoMobs
            }
            Object living = h.spawn.invoke(mob, location, h.command);
            if (living == null) {
                return null;
            }
            return h.getEntity.invoke(living) instanceof Entity resolved ? resolved : null;
        } catch (Throwable notEcoMobs) {
            return null;
        }
    }

    private Handles handles() {
        Handles h = handles;
        if (h == null) {
            h = Handles.resolve();
            handles = h;
        }
        return h;
    }

    /** Everything {@link #spawn} needs from EcoMobs, looked up once. */
    private record Handles(Object registry, Method getByID, Method spawn, Object command, Method getEntity) {

        @SuppressWarnings({"unchecked", "rawtypes"})
        static Handles resolve() {
            Plugin ecoMobs = Bukkit.getPluginManager().getPlugin("EcoMobs");
            if (ecoMobs == null) {
                return null;
            }
            try {
                ClassLoader loader = ecoMobs.getClass().getClassLoader();
                Class<?> registryType = Class.forName("com.willfp.ecomobs.mob.EcoMobs", true, loader);
                Class<?> spawnReason = Class.forName("com.willfp.ecomobs.mob.SpawnReason", true, loader);
                Class<?> ecoMob = Class.forName("com.willfp.ecomobs.mob.EcoMob", true, loader);
                Class<?> livingMob = Class.forName("com.willfp.ecomobs.mob.LivingMob", true, loader);
                return new Handles(
                        registryType.getField("INSTANCE").get(null),
                        registryType.getMethod("getByID", String.class),
                        ecoMob.getMethod("spawn", Location.class, spawnReason),
                        Enum.valueOf((Class) spawnReason, "COMMAND"),
                        livingMob.getMethod("getEntity"));
            } catch (Throwable reshaped) {
                return null;
            }
        }
    }
}
