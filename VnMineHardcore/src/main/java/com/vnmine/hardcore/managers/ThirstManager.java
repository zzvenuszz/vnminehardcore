package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

public class ThirstManager {

    private final VnMineHardcore plugin;
    private final ConfigManager config;
    private final Logger logger;
    private final Map<UUID, Integer> thirstLevel = new HashMap<>();
    private final Map<UUID, Long> lastDrink = new HashMap<>();
    private final Set<UUID> damagedByThirst = new HashSet<>();
    private final File dataFile;
    private final YamlConfiguration data;

    private BukkitRunnable task;
    private boolean running = false;

    public ThirstManager(VnMineHardcore plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.logger = plugin.getLogger();

        // Load saved data
        this.dataFile = new File(plugin.getDataFolder(), "thirst-data.yml");
        this.data = YamlConfiguration.loadConfiguration(dataFile);
        loadData();

        logger.info("[Thirst] Initialized: drain=" + config.thirstDrainIntervalMs / 1000 + "s, max=" + config.maxThirst);
        if (config.thirstEnabled) start();
    }

    public void reload() {
        stop();
        loadData();
        if (config.thirstEnabled) start();
    }

    private void loadData() {
        thirstLevel.clear();
        lastDrink.clear();
        damagedByThirst.clear();

        if (data.contains("players")) {
            for (String uuidStr : data.getConfigurationSection("players").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidStr);
                int thirst = data.getInt("players." + uuidStr + ".thirst", config.maxThirst);
                long last = data.getLong("players." + uuidStr + ".lastDrink", System.currentTimeMillis());
                thirstLevel.put(uuid, thirst);
                lastDrink.put(uuid, last);
            }
            logger.info("[Thirst] Loaded " + thirstLevel.size() + " player thirst levels from file");
        }
    }

    public void saveData() {
        for (Map.Entry<UUID, Integer> entry : thirstLevel.entrySet()) {
            String path = "players." + entry.getKey().toString();
            data.set(path + ".thirst", entry.getValue());
            data.set(path + ".lastDrink", lastDrink.getOrDefault(entry.getKey(), System.currentTimeMillis()));
        }
        try {
            data.save(dataFile);
        } catch (IOException e) {
            logger.warning("[Thirst] Could not save data: " + e.getMessage());
        }
    }

    public void start() {
        if (running) return;
        running = true;

        task = new BukkitRunnable() {
            @Override
            public void run() {
                int playersAffected = 0;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();

                    thirstLevel.putIfAbsent(uuid, config.maxThirst);
                    lastDrink.putIfAbsent(uuid, System.currentTimeMillis());

                    long now = System.currentTimeMillis();
                    long last = lastDrink.get(uuid);

                    if (now - last > config.thirstDrainIntervalMs) {
                        int current = thirstLevel.get(uuid);
                        current = Math.max(0, current - 1);
                        thirstLevel.put(uuid, current);
                        playersAffected++;

                        sendThirstBar(player, current);

                        if (current <= 0) {
                            if (!damagedByThirst.contains(uuid) || now - last > config.thirstDrainIntervalMs) {
                                player.damage(config.thirstDamage);
                                player.sendActionBar("§c§lKHÁT! §7Bạn đang mất máu vì khát!");
                                damagedByThirst.add(uuid);
                                logger.fine("[Thirst] " + player.getName() + " took thirst damage");
                            }
                        } else {
                            damagedByThirst.remove(uuid);
                        }

                        lastDrink.put(uuid, now);
                    }
                }
                if (playersAffected > 0) {
                    logger.fine("[Thirst] Drained " + playersAffected + " players");
                }
            }
        };
        task.runTaskTimer(plugin, 20L, 20L);
    }

    public void stop() {
        running = false;
        if (task != null) {
            task.cancel();
            task = null;
        }
        saveData(); // Save on stop
    }

    public void drinkWater(Player player) {
        UUID uuid = player.getUniqueId();
        int current = thirstLevel.getOrDefault(uuid, config.maxThirst);
        int newThirst = Math.min(config.maxThirst, current + 8);
        thirstLevel.put(uuid, newThirst);
        lastDrink.put(uuid, System.currentTimeMillis());
        damagedByThirst.remove(uuid);

        sendThirstBar(player, newThirst);
        logger.fine("[Thirst] " + player.getName() + " drank: " + current + " -> " + newThirst);
        saveData();
    }

    public boolean isPlayerThirsty(Player player) {
        return thirstLevel.getOrDefault(player.getUniqueId(), config.maxThirst) <= 4;
    }

    public int getThirstLevel(Player player) {
        return thirstLevel.getOrDefault(player.getUniqueId(), config.maxThirst);
    }

    public String getThirstBar(Player player) {
        int thirst = getThirstLevel(player);
        int filled = thirst / 2;
        int empty = 10 - filled;

        StringBuilder bar = new StringBuilder("§b");
        for (int i = 0; i < filled; i++) bar.append("█");
        bar.append("§7");
        for (int i = 0; i < empty; i++) bar.append("█");

        return bar.toString();
    }

    private void sendThirstBar(Player player, int thirst) {
        String bar = getThirstBar(player);

        String status;
        if (thirst >= 15) status = "§a§lKhỏe";
        else if (thirst >= 10) status = "§e§lHơi khát";
        else if (thirst >= 5) status = "§6§lKhát";
        else if (thirst >= 1) status = "§c§lRất khát";
        else status = "§4§lSẮP CHẾT KHÁT!";

        player.sendActionBar("§7Khát: " + bar + " §7| " + status);
    }

    public static boolean canDrink(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        return type == Material.WATER_BUCKET || type == Material.POTION;
    }

    public int getMaxThirst() { return config.maxThirst; }
}