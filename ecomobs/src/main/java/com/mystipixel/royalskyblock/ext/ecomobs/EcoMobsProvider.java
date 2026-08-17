package com.mystipixel.royalskyblock.ext.ecomobs;

import com.mystipixel.royalskyblock.hooks.IslandMobProvider;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * Spawns EcoMobs custom mobs on behalf of RoyalSkyblock.
 *
 * <p>Reflective rather than compiled: the registry lookup ({@code EcoMobs.getByID}) is inherited from
 * libreforge's {@code RegistrableCategory}, which is not resolvable from EcoMobs' published
 * artifacts — and reflection is what keeps this jar loadable on a server with no EcoSkills or EcoMobs
 * installed at all.
 *
 * <p>Spawns with EcoMobs' {@code COMMAND} reason, because RoyalSkyblock is the deliberate spawner
 * here; natural monster spawning is expected to be off on islands via the island gamerules.
 */
public final class EcoMobsProvider implements IslandMobProvider {

    static final String ID = "ecomobs";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("EcoMobs");
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Entity spawn(String mobId, Location location) {
        try {
            Class<?> ecoMobs = Class.forName("com.willfp.ecomobs.mob.EcoMobs");
            Object registry = ecoMobs.getField("INSTANCE").get(null);
            Object mob = ecoMobs.getMethod("getByID", String.class).invoke(registry, mobId);
            if (mob == null) {
                return null;                     // no such mob configured in EcoMobs
            }
            Class<?> spawnReason = Class.forName("com.willfp.ecomobs.mob.SpawnReason");
            Object command = Enum.valueOf((Class) spawnReason, "COMMAND");
            Class<?> ecoMob = Class.forName("com.willfp.ecomobs.mob.EcoMob");
            Object living = ecoMob.getMethod("spawn", Location.class, spawnReason)
                    .invoke(mob, location, command);
            if (living == null) {
                return null;
            }
            Class<?> livingMob = Class.forName("com.willfp.ecomobs.mob.LivingMob");
            Object entity = livingMob.getMethod("getEntity").invoke(living);
            return entity instanceof Entity resolved ? resolved : null;
        } catch (Throwable notEcoMobs) {
            return null;
        }
    }
}
