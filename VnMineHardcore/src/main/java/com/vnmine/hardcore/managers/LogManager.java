package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import org.bukkit.entity.Player;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LogManager {

    private final VnMineHardcore plugin;
    private final File dataFolder;
    private final Map<UUID, PlayerStats> playerStats = new ConcurrentHashMap<>();

    private static class PlayerStats {
        int deathCount = 0;
        long firstJoin = System.currentTimeMillis();
        long lastDeath = 0;
        int mobKills = 0;
    }

    public LogManager(VnMineHardcore plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "logs");
        this.dataFolder.mkdirs();
        loadStats();
    }

    public void logDeath(Player player, String cause) {
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String entry = String.format("[%s] %s (UUID: %s) died at [%d, %d, %d] in %s | Cause: %s%n",
            time, player.getName(), player.getUniqueId(),
            player.getLocation().getBlockX(),
            player.getLocation().getBlockY(),
            player.getLocation().getBlockZ(),
            player.getWorld().getName(), cause);

        appendToFile("deaths.log", entry);

        // Update stats
        PlayerStats stats = playerStats.computeIfAbsent(player.getUniqueId(), k -> new PlayerStats());
        stats.deathCount++;
        stats.lastDeath = System.currentTimeMillis();
        saveStats();
    }

    public void logBan(String playerName, String uuid, String ip, String reason) {
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String entry = String.format("[%s] BANNED: %s (UUID: %s) IP: %s | Reason: %s%n",
            time, playerName, uuid, ip != null ? ip : "unknown", reason);
        appendToFile("bans.log", entry);
    }

    public void logMobKill(Player player) {
        PlayerStats stats = playerStats.computeIfAbsent(player.getUniqueId(), k -> new PlayerStats());
        stats.mobKills++;
        saveStats();
    }

    public void logDisaster(String disasterName) {
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String entry = String.format("[%s] DISASTER: %s occurred!%n", time, disasterName);
        appendToFile("disasters.log", entry);
    }

    public int getDeathCount(Player player) {
        return playerStats.getOrDefault(player.getUniqueId(), new PlayerStats()).deathCount;
    }

    public String getSurvivalTime(Player player) {
        PlayerStats stats = playerStats.get(player.getUniqueId());
        if (stats == null) return "N/A";

        long currentTime = stats.lastDeath > 0 ? stats.lastDeath : System.currentTimeMillis();
        long diff = currentTime - stats.firstJoin;

        long hours = diff / 3600000;
        long minutes = (diff % 3600000) / 60000;
        long seconds = (diff % 60000) / 1000;

        return String.format("%dh %dm %ds", hours, minutes, seconds);
    }

    public int getMobKills(Player player) {
        return playerStats.getOrDefault(player.getUniqueId(), new PlayerStats()).mobKills;
    }

    public void trackFirstJoin(Player player) {
        playerStats.computeIfAbsent(player.getUniqueId(), k -> {
            PlayerStats stats = new PlayerStats();
            stats.firstJoin = System.currentTimeMillis();
            return stats;
        });
        saveStats();
    }

    private void appendToFile(String fileName, String entry) {
        try {
            Files.write(
                new File(dataFolder, fileName).toPath(),
                entry.getBytes(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write to " + fileName + ": " + e.getMessage());
        }
    }

    private void saveStats() {
        File statsFile = new File(dataFolder, "stats.yml");
        try (PrintWriter writer = new PrintWriter(new FileWriter(statsFile))) {
            for (Map.Entry<UUID, PlayerStats> entry : playerStats.entrySet()) {
                PlayerStats stats = entry.getValue();
                writer.printf("%s: deaths=%d, firstJoin=%d, lastDeath=%d, mobKills=%d%n",
                    entry.getKey().toString(),
                    stats.deathCount,
                    stats.firstJoin,
                    stats.lastDeath,
                    stats.mobKills);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save stats: " + e.getMessage());
        }
    }

    private void loadStats() {
        File statsFile = new File(dataFolder, "stats.yml");
        if (!statsFile.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(statsFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split(": ");
                if (parts.length < 2) continue;

                UUID uuid = UUID.fromString(parts[0]);
                String[] values = parts[1].split(", ");
                PlayerStats stats = new PlayerStats();

                for (String val : values) {
                    String[] kv = val.split("=");
                    if (kv.length == 2) {
                        switch (kv[0]) {
                            case "deaths" -> stats.deathCount = Integer.parseInt(kv[1]);
                            case "firstJoin" -> stats.firstJoin = Long.parseLong(kv[1]);
                            case "lastDeath" -> stats.lastDeath = Long.parseLong(kv[1]);
                            case "mobKills" -> stats.mobKills = Integer.parseInt(kv[1]);
                        }
                    }
                }
                playerStats.put(uuid, stats);
            }
        } catch (IOException | NumberFormatException e) {
            plugin.getLogger().warning("Failed to load stats: " + e.getMessage());
        }
    }
}