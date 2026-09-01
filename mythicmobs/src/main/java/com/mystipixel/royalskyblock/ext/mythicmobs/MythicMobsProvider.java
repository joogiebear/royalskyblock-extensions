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
 * <p>Compiled against MythicMobs' published API rather than reflective — unlike the eco plugins,
 * MythicMobs ships a real artifact, so this adapter gets actual types. The JVM only links them when
 * {@link #spawn} first runs, and {@link #available} touches none, so the provider registers safely
 * on a server without MythicMobs and simply reports itself unavailable.
 *
 * <p>Spawned at level 1: island difficulty scaling belongs to whatever the server configures
 * (RoyalSkyblock's own island-level hooks, or MythicMobs' level modifiers), not to the glue.
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
                return null;                     // no such mob configured in MythicMobs
            }
            ActiveMob active = mob.get().spawn(BukkitAdapter.adapt(location), 1);
            if (active == null) {
                return null;
            }
            return BukkitAdapter.adapt(active.getEntity());
        } catch (Throwable notMythic) {
            // Absent plugin (NoClassDefFoundError) or a reshaped API — degrade to "spawn failed"
            // rather than throwing inside the island spawn service.
            return null;
        }
    }
}
