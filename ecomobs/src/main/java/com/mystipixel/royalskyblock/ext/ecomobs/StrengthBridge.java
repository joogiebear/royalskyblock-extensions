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
import java.util.List;

/**
 * Scales a spawned EcoMob's health and damage by the level of the island it appeared on.
 *
 * <p>Done in code because EcoMobs checks spawn conditions against the location with no player, so
 * {@code %royalskyblock_island_level%} cannot resolve there. Reflective because every published
 * {@code com.willfp:EcoMobs} artifact is an empty stub; the spawn event is resolved at runtime.
 * Attributes are looked up by registry key because the {@code Attribute} enum names changed.
 */
public final class StrengthBridge implements Listener {

    private static final String EVENT_CLASS = "com.willfp.ecomobs.event.EcoMobSpawnEvent";

    private static final Attribute MAX_HEALTH = attribute("max_health");
    private static final Attribute ATTACK_DAMAGE = attribute("attack_damage");

    private final EcoMobsExtension extension;
    private final Method getMob;
    private final Method getEntity;
    private final Method getReason;              // null if EcoMobs stops exposing it

    // used when strength.spawn-reasons is absent: everything but spawners and eggs
    private static final List<String> DEFAULT_REASONS = List.of("COMMAND", "NATURAL", "TOTEM");

    private StrengthBridge(EcoMobsExtension extension, Method getMob, Method getEntity, Method getReason) {
        this.extension = extension;
        this.getMob = getMob;
        this.getEntity = getEntity;
        this.getReason = getReason;
    }

    // HIGH, not MONITOR: this mutates the mob, and HIGH still runs after EcoMobs has configured it.
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
            // resolve off the declared return type: the runtime class may be a non-public
            // implementation that rejects the call
            Method getEntity = getMob.getReturnType().getMethod("getEntity");
            Method getReason;
            try {
                getReason = eventClass.getMethod("getReason");
            } catch (NoSuchMethodException noReason) {
                getReason = null;                // cannot filter by reason, scale every spawn
            }

            StrengthBridge bridge = new StrengthBridge(extension, getMob, getEntity, getReason);
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

    // runs inside EcoMobs' own callEvent, so nothing thrown here may escape into someone else's spawn
    private void handle(Event event) {
        try {
            scaleSpawn(event);
        } catch (Throwable failed) {
            // degrade to "this mob is not scaled"
        }
    }

    private void scaleSpawn(Event event) throws ReflectiveOperationException {
        FileConfiguration config = extension.config();
        if (config == null || !config.getBoolean("strength.enabled", true)) {
            return;
        }
        if (!reasonAllowed(config, event)) {
            return;
        }
        Object mob = getMob.invoke(event);
        if (mob == null || !(getEntity.invoke(mob) instanceof Mob entity)) {
            return;
        }
        Island island = RoyalSkyblockPlugin.Companion.get().islands().getIslandByWorld(entity.getWorld());
        if (island == null || island.level() <= 0) {
            return;                              // not on an island, or level 0: leave the mob as-is
        }
        double level = island.level();
        scale(entity, MAX_HEALTH, multiplier(config, level, "health-scale-per-level", 0.002), true);
        scale(entity, ATTACK_DAMAGE, multiplier(config, level, "damage-scale-per-level", 0.0015), false);
    }

    private boolean reasonAllowed(FileConfiguration config, Event event) throws ReflectiveOperationException {
        if (getReason == null) {
            return true;
        }
        Object reason = getReason.invoke(event);
        if (!(reason instanceof Enum<?> named)) {
            return true;
        }
        List<String> allowed = config.isList("strength.spawn-reasons")
                ? config.getStringList("strength.spawn-reasons")
                : DEFAULT_REASONS;
        return StrengthMath.reasonAllowed(named.name(), allowed);
    }

    private static Attribute attribute(String key) {
        try {
            return Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
        } catch (Throwable oddApi) {
            return null;                         // the scale() guard handles null
        }
    }

    private double multiplier(FileConfiguration config, double level, String scaleKey, double fallback) {
        return StrengthMath.multiplier(level,
                config.getDouble("strength." + scaleKey, fallback),
                config.getDouble("strength.max-multiplier", 5.0));
    }

    private void scale(Mob entity, Attribute attr, double factor, boolean isHealth) {
        if (attr == null || factor <= 1.0) {
            return;
        }
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst == null) {
            return;                              // passive mob with no attack damage, etc.
        }
        inst.setBaseValue(inst.getBaseValue() * factor);
        if (isHealth) {
            // Spawn at the new full health. getValue(), not the scaled base: attribute modifiers sit
            // on top of the base, and setHealth rejects anything above the modified maximum.
            double max = inst.getValue();
            if (max > 0) {
                entity.setHealth(max);
            }
        }
    }
}
