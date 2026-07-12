package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LogManager {

    private final VnMineHardcore plugin;
    private final File dataFolder;
    private final Map<UUID, PlayerStats> playerStats = new ConcurrentHashMap<>();
    private final File statsFile;
    private final File statsBackupFile;
    private boolean dirty = false; // Đánh dấu cần save
    private BukkitRunnable saveTask;

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
        this.statsFile = new File(dataFolder, "stats.yml");
        this.statsBackupFile = new File(dataFolder, "stats.yml.bak");
        loadStats();
        startSaveTask();
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
        markDirty();
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
        markDirty();
    }

    public void logDisaster(String disasterName) {
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String entry = String.format("[%s] DISASTER: %s occurred!%n", time, disasterName);
        appendToFile("disasters.log", entry);
    }

    public int getDeathCount(Player player) {
        return playerStats.getOrDefault(player.getUniqueId(), new PlayerStats()).deathCount;
    }

    /**
     * Reset số lần chết của một người chơi về 0.
     * @param uuid UUID của người chơi
     */
    public void resetDeathCount(UUID uuid) {
        PlayerStats stats = playerStats.get(uuid);
        if (stats != null) {
            stats.deathCount = 0;
            stats.lastDeath = 0;
            markDirty();
            plugin.getLogger().info("[LogManager] Reset death count for UUID: " + uuid);
        } else {
            plugin.getLogger().warning("[LogManager] Cannot reset death count: no stats found for UUID: " + uuid);
        }
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
        markDirty();
    }

    /**
     * Lưu ngay lập tức (gọi khi plugin disable)
     */
    public void saveImmediately() {
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

    /**
     * Đánh dấu cần save, task định kỳ sẽ tự động lưu
     */
    private void markDirty() {
        dirty = true;
    }

    /**
     * Task lưu stats định kỳ mỗi 30 giây (chỉ lưu khi có thay đổi)
     */
    private void startSaveTask() {
        saveTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (dirty) {
                    saveStats();
                    dirty = false;
                }
            }
        };
        saveTask.runTaskTimer(plugin, 600L, 600L); // 30 giây
    }

    public void stop() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        // Lưu ngay khi stop
        if (dirty) {
            saveStats();
            dirty = false;
        }
    }

    /**
     * Lưu stats dùng YamlConfiguration (atomic write)
     * Tự động tạo backup trước khi ghi
     */
    private synchronized void saveStats() {
        try {
            // Backup file cũ nếu tồn tại
            if (statsFile.exists()) {
                Files.copy(statsFile.toPath(), statsBackupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            YamlConfiguration yaml = new YamlConfiguration();
            for (Map.Entry<UUID, PlayerStats> entry : playerStats.entrySet()) {
                String path = entry.getKey().toString();
                PlayerStats stats = entry.getValue();
                yaml.set(path + ".deaths", stats.deathCount);
                yaml.set(path + ".firstJoin", stats.firstJoin);
                yaml.set(path + ".lastDeath", stats.lastDeath);
                yaml.set(path + ".mobKills", stats.mobKills);
            }
            yaml.save(statsFile);
        } catch (Exception e) {
            plugin.getLogger().warning("[LogManager] Failed to save stats: " + e.getMessage());
            // Thử khôi phục từ backup
            if (statsBackupFile.exists()) {
                try {
                    Files.copy(statsBackupFile.toPath(), statsFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().info("[LogManager] Restored stats from backup file.");
                } catch (IOException ex) {
                    plugin.getLogger().warning("[LogManager] Could not restore from backup: " + ex.getMessage());
                }
            }
        }
    }

    /**
     * Load stats từ file YAML
     * Tự động thử load từ backup nếu file chính bị lỗi
     */
    private synchronized void loadStats() {
        playerStats.clear();

        // Thử load từ file chính trước
        if (statsFile.exists()) {
            if (!loadFromFile(statsFile)) {
                // Nếu file chính lỗi, thử load từ backup
                plugin.getLogger().warning("[LogManager] Main stats file corrupted, trying backup...");
                if (statsBackupFile.exists()) {
                    if (loadFromFile(statsBackupFile)) {
                        plugin.getLogger().info("[LogManager] Successfully loaded stats from backup.");
                    } else {
                        plugin.getLogger().warning("[LogManager] Backup file also corrupted. Starting fresh.");
                    }
                } else {
                    plugin.getLogger().warning("[LogManager] No backup found. Starting fresh.");
                }
            }
        }

        plugin.getLogger().info("[LogManager] Loaded stats for " + playerStats.size() + " players.");
    }

    /**
     * Load stats từ một file cụ thể
     */
    private boolean loadFromFile(File file) {
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            if (yaml.getKeys(false).isEmpty()) {
                return false;
            }
            for (String uuidStr : yaml.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String path = uuidStr;
                    PlayerStats stats = new PlayerStats();
                    stats.deathCount = yaml.getInt(path + ".deaths", 0);
                    stats.firstJoin = yaml.getLong(path + ".firstJoin", System.currentTimeMillis());
                    stats.lastDeath = yaml.getLong(path + ".lastDeath", 0);
                    stats.mobKills = yaml.getInt(path + ".mobKills", 0);
                    playerStats.put(uuid, stats);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("[LogManager] Invalid UUID in stats file: " + uuidStr);
                }
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[LogManager] Error loading stats from " + file.getName() + ": " + e.getMessage());
            return false;
        }
    }
}