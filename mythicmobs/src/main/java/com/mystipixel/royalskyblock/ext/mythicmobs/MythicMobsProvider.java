package com.mystipixel.royalskyblock.ext.mythicmobs;

import com.mystipixel.royalskyblock.hooks.IslandMobProvider;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * Spawns MythicMobs mobs on behalf of RoyalSkyblock.
 *
 * <p>Compiled against MythicMobs' API. The types only link when {@link #spawn} first runs and
 * {@link #available} touches none, so this loads safely without MythicMobs. Spawns at level 1;
 * difficulty scaling is the server's config, not this adapter's.
 */
public final class MythicMobsProvider implements IslandMobProvider {

    static final String ID = "mythicmobs";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("MythicMobs");
    }

    @Override
    public Entity spawn(String mobId, Location location) {
        try {
            var mob = MythicBukkit.inst().getMobManager().getMythicMob(mobId);
            if (mob.isEmpty()) {
                return null;
            }
            ActiveMob active = mob.get().spawn(BukkitAdapter.adapt(location), 1);
            if (active == null) {
                return null;
            }
            return BukkitAdapter.adapt(active.getEntity());
        } catch (Throwable notMythic) {
            // absent plugin (NoClassDefFoundError) or a reshaped API: fail the spawn rather than throw
            // inside the island spawn service
            return null;
        }
    }
}
