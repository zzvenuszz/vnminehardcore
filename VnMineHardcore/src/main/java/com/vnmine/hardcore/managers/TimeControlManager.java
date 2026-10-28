package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class TimeControlManager {

    private final VnMineHardcore plugin;
    private final ConfigManager config;
    private final Map<UUID, Boolean> originalDaylightCycle = new HashMap<>();
    private final Map<UUID, Double> fractionalTicks = new HashMap<>();
    private final Set<UUID> worldsWithoutClock = new HashSet<>();
    private BukkitTask task;
    private long lastUpdateNanos;

    public TimeControlManager(VnMineHardcore plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void reload() {
        if (!config.timeControlEnabled) {
            stop();
            return;
        }
        if (task == null || task.isCancelled()) {
            lastUpdateNanos = System.nanoTime();
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::advanceWorldTime, 20L, 20L);
        } else {
            lastUpdateNanos = System.nanoTime();
        }
        Bukkit.getWorlds().stream()
            .filter(world -> world.getEnvironment() != World.Environment.NETHER)
            .forEach(this::disableVanillaDaylightCycle);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NETHER) continue;
            Boolean originalValue = originalDaylightCycle.get(world.getUID());
            if (originalValue != null) world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, originalValue);
        }
        originalDaylightCycle.clear();
        fractionalTicks.clear();
        worldsWithoutClock.clear();
    }

    private void advanceWorldTime() {
        long now = System.nanoTime();
        double elapsedSeconds = (now - lastUpdateNanos) / 1_000_000_000.0;
        lastUpdateNanos = now;

        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NETHER) continue;
            if (plugin.getRegionProtectionManager() != null
                    && plugin.getRegionProtectionManager().isTimeProgressionDisabled(world)) {
                disableVanillaDaylightCycle(world);
                continue;
            }
            if (worldsWithoutClock.contains(world.getUID())) continue;
            disableVanillaDaylightCycle(world);

            long worldTime = world.getTime();
            boolean isDay = Math.floorMod(worldTime, 24_000L) < 12_000L;
            int durationMinutes = isDay
                ? config.getDayDurationMinutes(world.getName())
                : config.getNightDurationMinutes(world.getName());
            UUID worldId = world.getUID();
            double accruedTicks = fractionalTicks.getOrDefault(worldId, 0.0) +
                12_000.0 * elapsedSeconds / (durationMinutes * 60.0);
            long wholeTicks = (long) accruedTicks;
            fractionalTicks.put(worldId, accruedTicks - wholeTicks);
            if (wholeTicks > 0) {
                try {
                    world.setTime(worldTime + wholeTicks);
                } catch (IllegalArgumentException exception) {
                    worldsWithoutClock.add(world.getUID());
                    Boolean originalValue = originalDaylightCycle.remove(world.getUID());
                    if (originalValue != null) world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, originalValue);
                    plugin.getLogger().log(Level.WARNING,
                        "[TimeControl] World '" + world.getName() +
                            "' does not support a world clock; custom time control is skipped for this world.",
                        exception);
                }
            }
        }
    }

    private void disableVanillaDaylightCycle(World world) {
        UUID worldId = world.getUID();
        originalDaylightCycle.putIfAbsent(worldId, world.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE));
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
    }
}
