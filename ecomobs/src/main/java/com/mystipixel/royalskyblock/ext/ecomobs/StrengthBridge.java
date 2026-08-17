package com.mystipixel.royalskyblock.ext.ecomobs;

import com.mystipixel.royalskyblock.RoyalSkyblockPlugin;
import com.mystipixel.royalskyblock.island.Island;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Mob;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Scales a spawned EcoMob's health and damage by the level of the island it appeared on.
 *
 * <p><b>Why code rather than EcoMobs config.</b> EcoMobs evaluates its spawn conditions against the
 * spawn <em>location</em> ({@code conditions.areMet(location.toDispatcher(), ...)}), with no player in
 * context. libreforge's placeholder conditions resolve via {@code dispatcher.get<Player>()}, so
 * {@code %royalskyblock_island_level%} cannot resolve at an EcoMobs spawn — the config route is a dead
 * end. Reading the island level from the mob's world after it has spawned needs no player at all.
 *
 * <p><b>Why reflection rather than a compile dependency.</b> Every published {@code com.willfp:EcoMobs}
 * artifact is an empty two-entry stub containing no classes — verified across 2026.27, .29, .30, .31
 * and .32 — so there is nothing to compile against. A build can appear to work when a real jar has
 * been hand-installed into a developer's local Maven repository, but that is invisible to CI and to
 * anyone else cloning the project. The shipped plugin jar does contain
 * {@code com.willfp.ecomobs.event.EcoMobSpawnEvent}, so the event is resolved and registered at
 * runtime instead.
 *
 * <p><b>Version independence.</b> Attributes are looked up by their stable registry key
 * ({@code minecraft:max_health} / {@code minecraft:attack_damage}) rather than the {@code Attribute}
 * enum constant, whose Java name churned across Bukkit versions (the {@code GENERIC_} prefix).
 */
public final class StrengthBridge implements Listener {

    private static final String EVENT_CLASS = "com.willfp.ecomobs.event.EcoMobSpawnEvent";

    private static final Attribute MAX_HEALTH = attribute("max_health");
    private static final Attribute ATTACK_DAMAGE = attribute("attack_damage");

    private final EcoMobsExtension extension;
    private final Method getMob;
    private final Method getEntity;

    private StrengthBridge(EcoMobsExtension extension, Method getMob, Method getEntity) {
        this.extension = extension;
        this.getMob = getMob;
        this.getEntity = getEntity;
    }

    /**
     * Resolve EcoMobs' spawn event and register against it. Returns false when EcoMobs is absent or
     * its event is not shaped the way this expects; the caller logs and carries on, and island mobs
     * simply are not scaled.
     *
     * <p>Registered at HIGH rather than MONITOR: MONITOR is for observers and this mutates the mob.
     * HIGH still runs after EcoMobs has finished configuring the entity.
     */
    @SuppressWarnings("unchecked")
    static boolean register(EcoMobsExtension extension, Plugin owner) {
        try {
            Plugin ecoMobs = Bukkit.getPluginManager().getPlugin("EcoMobs");
            if (ecoMobs == null) {
                return false;
            }
            Class<?> eventClass = Class.forName(EVENT_CLASS, false, ecoMobs.getClass().getClassLoader());
            if (!Event.class.isAssignableFrom(eventClass)) {
                return false;
            }
            Method getMob = eventClass.getMethod("getMob");
            // Resolve getEntity() off the DECLARED return type (EcoMobs' public mob interface) rather
            // than the runtime object's class, which may be a non-public implementation that would
            // reject the call.
            Method getEntity = getMob.getReturnType().getMethod("getEntity");

            StrengthBridge bridge = new StrengthBridge(extension, getMob, getEntity);
            Bukkit.getPluginManager().registerEvent(
                    (Class<? extends Event>) eventClass,
                    bridge,
                    EventPriority.HIGH,
                    (listener, event) -> bridge.handle(event),
                    owner);
            return true;
        } catch (Throwable notEcoMobs) {
            return false;
        }
    }

    private void handle(Event event) {
        FileConfiguration config = extension.config();
        if (!config.getBoolean("strength.enabled", true)) {
            return;
        }
        Mob entity;
        try {
            Object mob = getMob.invoke(event);
            if (mob == null) {
                return;
            }
            if (!(getEntity.invoke(mob) instanceof Mob resolved)) {
                return;
            }
            entity = resolved;
        } catch (Throwable failed) {
            return;                              // EcoMobs changed shape — degrade to no scaling
        }
        Island island = RoyalSkyblockPlugin.Companion.get().islands().getIslandByWorld(entity.getWorld());
        if (island == null || island.level() <= 0) {
            return;                              // not on an island, or level 0 — leave the mob as-is
        }
        double level = island.level();
        scale(entity, MAX_HEALTH, multiplier(config, level, "health-scale-per-level", 0.002), true);
        scale(entity, ATTACK_DAMAGE, multiplier(config, level, "damage-scale-per-level", 0.0015), false);
    }

    private static Attribute attribute(String key) {
        try {
            return Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
        } catch (Throwable oddApi) {
            return null;                         // the scale() guard handles null
        }
    }

    /** {@code 1 + level*scale}, clamped to {@code [1, max-multiplier]}. */
    private double multiplier(FileConfiguration config, double level, String scaleKey, double fallback) {
        double scale = config.getDouble("strength." + scaleKey, fallback);
        double max = config.getDouble("strength.max-multiplier", 5.0);
        return Math.max(1.0, Math.min(max, 1.0 + level * scale));
    }

    private void scale(Mob entity, Attribute attr, double factor, boolean isHealth) {
        if (attr == null || factor <= 1.0) {
            return;
        }
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst == null) {
            return;                              // passive mob with no attack damage, etc.
        }
        double scaled = inst.getBaseValue() * factor;
        inst.setBaseValue(scaled);
        if (isHealth) {
            entity.setHealth(scaled);            // spawn at the new full health, not the old value
        }
    }
}
