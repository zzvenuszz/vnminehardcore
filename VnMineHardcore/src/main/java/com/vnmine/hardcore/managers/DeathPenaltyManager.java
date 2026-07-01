package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * Quản lý hình phạt khi chết (Death Penalty).
 * Mỗi lần chết, người chơi bị giảm các chỉ số.
 * Sau khi sống sót đủ thời gian, chỉ số phục hồi.
 */
public class DeathPenaltyManager {

    private final VnMineHardcore plugin;
    private final ConfigManager config;
    private final Logger logger;

    // Lưu số lần chết tích lũy chưa được phục hồi cho mỗi player
    private final Map<UUID, Integer> penaltyStacks = new HashMap<>();
    // Lưu thời điểm bắt đầu hồi phục (khi player hồi sinh)
    private final Map<UUID, Long> recoveryStartTime = new HashMap<>();
    // Lưu trạng thái đã phục hồi chưa
    private final Set<UUID> recovered = new HashSet<>();

    private final File dataFile;
    private final YamlConfiguration data;
    private BukkitRunnable recoveryTask;

    // Attribute modifier names (unique names for Bukkit API)
    private static final String MODIFIER_NAME_MAX_HP = "vnmine_death_penalty_max_hp";
    private static final UUID MODIFIER_UUID_MAX_HP = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");

    public DeathPenaltyManager(VnMineHardcore plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.logger = plugin.getLogger();

        this.dataFile = new File(plugin.getDataFolder(), "death-penalty.yml");
        this.data = YamlConfiguration.loadConfiguration(dataFile);
        loadData();

        if (config.deathPenaltyEnabled) {
            startRecoveryTask();
        }

        logger.info("[DeathPenalty] Initialized: enabled=" + config.deathPenaltyEnabled +
            ", recovery=" + config.deathPenaltyRecoverySeconds + "s" +
            ", maxStack=" + config.deathPenaltyMaxStack);
    }

    /**
     * Gọi khi người chơi chết - tăng penalty stack.
     */
    public void onPlayerDeath(Player player) {
        if (!config.deathPenaltyEnabled) return;

        UUID uuid = player.getUniqueId();
        int current = penaltyStacks.getOrDefault(uuid, 0);
        int maxStack = config.deathPenaltyMaxStack;
        if (current < maxStack) {
            penaltyStacks.put(uuid, current + 1);
            logger.info("[DeathPenalty] " + player.getName() + " died. Penalty stack: " + (current + 1) + "/" + maxStack);
        } else {
            logger.info("[DeathPenalty] " + player.getName() + " died. Penalty already at max: " + maxStack);
        }

        // Reset recovery timer - player will start recovery after respawn
        recoveryStartTime.remove(uuid);
        recovered.remove(uuid);

        saveData();
    }

    /**
     * Gọi khi người chơi hồi sinh - bắt đầu đếm thời gian hồi phục.
     */
    public void onPlayerRespawn(Player player) {
        if (!config.deathPenaltyEnabled) return;

        UUID uuid = player.getUniqueId();
        int stack = penaltyStacks.getOrDefault(uuid, 0);
        if (stack <= 0) return;

        // Bắt đầu đếm thời gian hồi phục
        recoveryStartTime.put(uuid, System.currentTimeMillis());
        recovered.remove(uuid);

        // Áp dụng penalty ngay lập tức
        applyPenalty(player);

        player.sendMessage("§c§l💀 HÌNH PHẠT TỬ THẦN!");
        player.sendMessage("§7Bạn đã chết §c" + stack + " §7lần. Chỉ số bị giảm!");
        player.sendMessage("§eSống sót §6" + config.deathPenaltyRecoverySeconds + "§e giây để phục hồi!");

        logger.info("[DeathPenalty] " + player.getName() + " respawned with stack=" + stack);
    }

    /**
     * Áp dụng penalty lên người chơi.
     */
    public void applyPenalty(Player player) {
        if (!config.deathPenaltyEnabled) return;

        int stack = penaltyStacks.getOrDefault(player.getUniqueId(), 0);
        if (stack <= 0) return;

        // Giảm max HP
        double hpReduction = stack * config.deathPenaltyMaxHpPerDeath;
        AttributeInstance maxHpAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHpAttr != null) {
            // Remove old modifier first
            for (AttributeModifier mod : maxHpAttr.getModifiers()) {
                if (mod.getName().equals(MODIFIER_NAME_MAX_HP)) {
                    maxHpAttr.removeModifier(mod);
                }
            }
            double baseMaxHp = 20.0; // Vanilla max health
            double newMaxHp = baseMaxHp * (1.0 - hpReduction);
            if (newMaxHp < 1.0) newMaxHp = 1.0;
            // Use ADD_NUMBER to set absolute value
            AttributeModifier modifier = new AttributeModifier(
                MODIFIER_UUID_MAX_HP,
                MODIFIER_NAME_MAX_HP,
                newMaxHp - baseMaxHp,
                AttributeModifier.Operation.ADD_NUMBER
            );
            maxHpAttr.addTransientModifier(modifier);

            // Clamp current health
            if (player.getHealth() > maxHpAttr.getValue()) {
                player.setHealth(maxHpAttr.getValue());
            }
        }

        player.sendMessage("§c§l☠ Penalty: §7HP giảm " + String.format("%.0f", hpReduction * 100) + "%");
    }

    /**
     * Gỡ bỏ penalty khỏi người chơi.
     */
    public void removePenalty(Player player) {
        UUID uuid = player.getUniqueId();
        penaltyStacks.remove(uuid);
        recoveryStartTime.remove(uuid);
        recovered.add(uuid);

        // Restore max HP
        AttributeInstance maxHpAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHpAttr != null) {
            for (AttributeModifier mod : maxHpAttr.getModifiers()) {
                if (mod.getName().equals(MODIFIER_NAME_MAX_HP)) {
                    maxHpAttr.removeModifier(mod);
                }
            }
        }

        player.sendMessage("§a§l✅ PHỤC HỒI! §7Chỉ số đã trở lại bình thường!");
        logger.info("[DeathPenalty] " + player.getName() + " recovered from death penalty");

        saveData();
    }

    /**
     * Lấy số stack penalty hiện tại.
     */
    public int getPenaltyStack(Player player) {
        return penaltyStacks.getOrDefault(player.getUniqueId(), 0);
    }

    /**
     * Lấy hệ số nhân sát thương gây ra (0.0 - 1.0).
     * 1.0 = bình thường, 0.8 = giảm 20%.
     */
    public double getDamageDealtMultiplier(Player player) {
        if (!config.deathPenaltyEnabled) return 1.0;
        int stack = penaltyStacks.getOrDefault(player.getUniqueId(), 0);
        if (stack <= 0) return 1.0;
        return Math.max(0.1, 1.0 - stack * config.deathPenaltyDamagePerDeath);
    }

    /**
     * Lấy hệ số nhân sát thương nhận vào (1.0+).
     * 1.0 = bình thường, 1.2 = tăng 20%.
     */
    public double getIncomingDamageMultiplier(Player player) {
        if (!config.deathPenaltyEnabled) return 1.0;
        int stack = penaltyStacks.getOrDefault(player.getUniqueId(), 0);
        if (stack <= 0) return 1.0;
        return 1.0 + stack * config.deathPenaltyIncomingDamagePerDeath;
    }

    /**
     * Lấy hệ số nhân hồi phục (0.0 - 1.0).
     */
    public double getRegenMultiplier(Player player) {
        if (!config.deathPenaltyEnabled) return 1.0;
        int stack = penaltyStacks.getOrDefault(player.getUniqueId(), 0);
        if (stack <= 0) return 1.0;
        return Math.max(0.1, 1.0 - stack * config.deathPenaltyRegenPerDeath);
    }

    /**
     * Lấy hệ số nhân drain thức ăn (1.0+).
     */
    public double getFoodDrainMultiplier(Player player) {
        if (!config.deathPenaltyEnabled) return 1.0;
        int stack = penaltyStacks.getOrDefault(player.getUniqueId(), 0);
        if (stack <= 0) return 1.0;
        return 1.0 + stack * config.deathPenaltyFoodDrainPerDeath;
    }

    /**
     * Lấy hệ số nhân drain khát (1.0+).
     */
    public double getThirstDrainMultiplier(Player player) {
        if (!config.deathPenaltyEnabled) return 1.0;
        int stack = penaltyStacks.getOrDefault(player.getUniqueId(), 0);
        if (stack <= 0) return 1.0;
        return 1.0 + stack * config.deathPenaltyThirstDrainPerDeath;
    }

    /**
     * Lấy max thirst offset (giảm dần theo stack).
     */
    public int getMaxThirstOffset(Player player) {
        if (!config.deathPenaltyEnabled) return 0;
        int stack = penaltyStacks.getOrDefault(player.getUniqueId(), 0);
        if (stack <= 0) return 0;
        double reduction = stack * config.deathPenaltyMaxThirstPerDeath;
        return (int) Math.round(config.maxThirst * reduction);
    }

    /**
     * Task kiểm tra hồi phục mỗi giây.
     */
    private void startRecoveryTask() {
        recoveryTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();
                    if (!penaltyStacks.containsKey(uuid)) continue;
                    if (recovered.contains(uuid)) continue;

                    Long startTime = recoveryStartTime.get(uuid);
                    if (startTime == null) continue;

                    long elapsed = (now - startTime) / 1000;
                    long required = config.deathPenaltyRecoverySeconds;

                    if (elapsed >= required) {
                        removePenalty(player);
                    } else {
                        // Show remaining time occasionally
                        if (elapsed % 30 == 0 && elapsed > 0) {
                            long remaining = required - elapsed;
                            player.sendMessage("§e⏳ Còn §6" + remaining + "§e giây để phục hồi chỉ số...");
                        }
                    }
                }
            }
        };
        recoveryTask.runTaskTimer(plugin, 20L, 20L);
    }

    public void stop() {
        if (recoveryTask != null) {
            recoveryTask.cancel();
            recoveryTask = null;
        }
        saveData();
    }

    public void reload() {
        stop();
        loadData();
        if (config.deathPenaltyEnabled) {
            startRecoveryTask();
        }
    }

    private void saveData() {
        for (Map.Entry<UUID, Integer> entry : penaltyStacks.entrySet()) {
            String path = "players." + entry.getKey().toString();
            data.set(path + ".stack", entry.getValue());
            Long startTime = recoveryStartTime.get(entry.getKey());
            if (startTime != null) {
                data.set(path + ".recoveryStart", startTime);
            }
        }
        try {
            data.save(dataFile);
        } catch (IOException e) {
            logger.warning("[DeathPenalty] Could not save data: " + e.getMessage());
        }
    }

    private void loadData() {
        penaltyStacks.clear();
        recoveryStartTime.clear();
        recovered.clear();

        if (data.contains("players")) {
            for (String uuidStr : data.getConfigurationSection("players").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidStr);
                int stack = data.getInt("players." + uuidStr + ".stack", 0);
                if (stack > 0) {
                    penaltyStacks.put(uuid, stack);
                    if (data.contains("players." + uuidStr + ".recoveryStart")) {
                        recoveryStartTime.put(uuid, data.getLong("players." + uuidStr + ".recoveryStart"));
                    }
                }
            }
            logger.info("[DeathPenalty] Loaded " + penaltyStacks.size() + " player penalties");
        }
    }
}