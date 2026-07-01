package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

public class BossEventManager {

    private final VnMineHardcore plugin;
    private final ConfigManager config;
    private final LogManager logManager;
    private final Logger logger;
    private final Random random = ThreadLocalRandom.current();

    private boolean bossActive = false;
    private String currentBossName = null;
    private LivingEntity currentBoss = null;
    private BossBar bossBar;
    private BossBar warningBossBar;
    private int timeSinceLastBoss = 0;
    private int warningTimeLeft = 0;
    private BossConfig pendingBoss = null;

    // Cấu trúc lưu config boss
    private static class BossConfig {
        boolean enabled;
        EntityType entityType;
        String displayName;
        double hp;
        double damageMultiplier;
        int chance;
        int durationSeconds;
        int warningSeconds;
        Map<String, DropConfig> drops = new HashMap<>();
    }

    private static class DropConfig {
        int minAmount;
        int maxAmount;
        double chance;
    }

    private final Map<String, BossConfig> bossConfigs = new LinkedHashMap<>();

    public BossEventManager(VnMineHardcore plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.logManager = plugin.getLogManager();
        this.logger = plugin.getLogger();

        this.bossBar = BossBar.bossBar(
            Component.text(""),
            1.0f,
            BossBar.Color.PURPLE,
            BossBar.Overlay.PROGRESS
        );

        this.warningBossBar = BossBar.bossBar(
            Component.text(""),
            1.0f,
            BossBar.Color.YELLOW,
            BossBar.Overlay.PROGRESS
        );

        loadBossConfigs();

        if (config.bossEventsEnabled) start();

        logger.info("[BossEvent] Initialized: enabled=" + config.bossEventsEnabled +
            ", interval=" + config.bossEventMinIntervalSeconds + "s" +
            ", bosses=" + bossConfigs.size());
    }

    private void loadBossConfigs() {
        bossConfigs.clear();
        ConfigurationSection bossesSection = plugin.getConfig().getConfigurationSection("boss-events.bosses");
        if (bossesSection == null) return;

        for (String bossId : bossesSection.getKeys(false)) {
            ConfigurationSection section = bossesSection.getConfigurationSection(bossId);
            if (section == null) continue;

            BossConfig bc = new BossConfig();
            bc.enabled = section.getBoolean("enabled", false);
            if (!bc.enabled) continue;

            String entityTypeName = section.getString("entity-type", "WITHER");
            try {
                bc.entityType = EntityType.valueOf(entityTypeName.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warning("[BossEvent] Invalid entity-type '" + entityTypeName + "' for boss '" + bossId + "'");
                continue;
            }

            bc.displayName = section.getString("display-name", "§c§lBoss");
            bc.hp = section.getDouble("hp", 100);
            bc.damageMultiplier = section.getDouble("damage-multiplier", 1.0);
            bc.chance = section.getInt("chance", 10);
            bc.durationSeconds = section.getInt("duration-seconds", 120);
            bc.warningSeconds = section.getInt("warning-seconds", 60);

            // Load drops
            ConfigurationSection dropsSection = section.getConfigurationSection("drops");
            if (dropsSection != null) {
                for (String dropKey : dropsSection.getKeys(false)) {
                    ConfigurationSection dropSection = dropsSection.getConfigurationSection(dropKey);
                    if (dropSection == null) continue;
                    DropConfig dc = new DropConfig();
                    dc.minAmount = dropSection.getInt("min-amount", 1);
                    dc.maxAmount = dropSection.getInt("max-amount", 1);
                    dc.chance = dropSection.getDouble("chance", 0.5);
                    bc.drops.put(dropKey, dc);
                }
            }

            bossConfigs.put(bossId, bc);
            logger.info("[BossEvent] Loaded boss: " + bossId + " (" + bc.entityType + ")");
        }
    }

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (Bukkit.getWorlds().isEmpty()) return;
                timeSinceLastBoss++;

                if (!bossActive && timeSinceLastBoss >= config.bossEventMinIntervalSeconds) {
                    trySpawnBoss();
                }

                // Update warning bar
                if (warningTimeLeft > 0 && pendingBoss != null) {
                    warningTimeLeft--;
                    updateWarningBar();
                    if (warningTimeLeft <= 0) {
                        // Warning ended, spawn boss now
                        spawnBoss(pendingBoss);
                        pendingBoss = null;
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Every second
    }

    private void trySpawnBoss() {
        if (Bukkit.getOnlinePlayers().isEmpty()) return;

        // Random boss selection based on chance
        int roll = random.nextInt(100);
        for (Map.Entry<String, BossConfig> entry : bossConfigs.entrySet()) {
            BossConfig bc = entry.getValue();
            if (roll < bc.chance) {
                startWarning(bc);
                return;
            }
            roll -= bc.chance;
        }
    }

    private void startWarning(BossConfig bc) {
        if (bossActive) return;
        
        pendingBoss = bc;
        warningTimeLeft = bc.warningSeconds;
        
        // Show warning bar
        warningBossBar.name(Component.text("§e§l⚠ BOSS SẮP XUẤT HIỆN ⚠"));
        warningBossBar.progress(1.0f);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(warningBossBar);
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 0.5f);
        }
        
        Bukkit.broadcastMessage("§4§l⚠ CẢNH BÁO: §c" + bc.displayName + " §4§lsẽ xuất hiện trong §e§l" + bc.warningSeconds + " giây§4§l!");
    }

    private void updateWarningBar() {
        if (warningTimeLeft > 0 && pendingBoss != null) {
            warningBossBar.name(Component.text("§e§l⚠ " + pendingBoss.displayName + " §7- §c§l" + warningTimeLeft + "s"));
            warningBossBar.progress((float) warningTimeLeft / pendingBoss.warningSeconds);
            
            if (warningTimeLeft <= 10 || warningTimeLeft == 30) {
                Bukkit.broadcastMessage("§c§l⚠ " + pendingBoss.displayName + " §csẽ xuất hiện trong §4§l" + warningTimeLeft + "§c giây!");
            }
        }
    }

    private void spawnBoss(BossConfig bc) {
        if (bossActive) return;
        
        // Hide warning bar
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.hideBossBar(warningBossBar);
        }

        // Chọn player ngẫu nhiên
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        Player targetPlayer = onlinePlayers.get(random.nextInt(onlinePlayers.size()));

        // Tính vị trí spawn ngẫu nhiên quanh player
        int radius = config.bossEventSpawnRadius;
        int x = targetPlayer.getLocation().getBlockX() + random.nextInt(radius * 2) - radius;
        int z = targetPlayer.getLocation().getBlockZ() + random.nextInt(radius * 2) - radius;
        World world = targetPlayer.getWorld();
        int y = world.getHighestBlockYAt(x, z) + 1;
        Location spawnLoc = new Location(world, x, y, z);

        // Spawn boss
        currentBoss = (LivingEntity) world.spawnEntity(spawnLoc, bc.entityType);
        currentBoss.setCustomName(bc.displayName);
        currentBoss.setCustomNameVisible(true);
        currentBoss.setMaxHealth(bc.hp);
        currentBoss.setHealth(bc.hp);

        bossActive = true;
        currentBossName = bc.displayName;
        timeSinceLastBoss = 0;

        // Thông báo cho tất cả player
        String msg = "§4§l⚠ BOSS: §c" + bc.displayName + " §4§lđã xuất hiện tại §e" +
            x + ", " + y + ", " + z + " §4§l(" + world.getName() + ")";
        Bukkit.broadcastMessage("§4§m========================================");
        Bukkit.broadcastMessage(msg);
        Bukkit.broadcastMessage("§e§lHP: §c" + (int) bc.hp + " §e| Sát thương: x" + bc.damageMultiplier);
        Bukkit.broadcastMessage("§eThời gian tồn tại: " + bc.durationSeconds + " giây");
        Bukkit.broadcastMessage("§4§m========================================");

        // Boss bar
        bossBar.name(Component.text("§4§l⚠ " + bc.displayName + " §7- §c" + (int) bc.hp + " HP"));
        bossBar.progress(1.0f);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(bossBar);
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);
        }

        logManager.logDisaster("BOSS: " + bc.displayName + " spawned at " + x + "," + y + "," + z);

        // Task theo dõi boss
        new BukkitRunnable() {
            int elapsed = 0;
            final int maxDuration = bc.durationSeconds * 20; // ticks

            @Override
            public void run() {
                if (currentBoss == null || currentBoss.isDead()) {
                    // Boss đã chết
                    if (currentBoss != null && currentBoss.getKiller() != null) {
                        onBossKilled(currentBoss.getKiller(), bc);
                    }
                    cleanup();
                    this.cancel();
                    return;
                }

                elapsed += 20; // 1 second

                // Update boss bar
                float progress = (float) (currentBoss.getHealth() / currentBoss.getMaxHealth());
                bossBar.progress(Math.max(0, progress));
                bossBar.name(Component.text("§4§l⚠ " + bc.displayName +
                    " §7- §c" + String.format("%.0f", currentBoss.getHealth()) + "/" +
                    String.format("%.0f", currentBoss.getMaxHealth()) + " HP"));

                if (elapsed >= maxDuration) {
                    // Hết thời gian - boss biến mất
                    Bukkit.broadcastMessage("§c§l⏰ " + bc.displayName + " đã biến mất!");
                    cleanup();
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void onBossKilled(Player killer, BossConfig bc) {
        Bukkit.broadcastMessage("§a§l🎉 " + killer.getName() + " đã tiêu diệt " + bc.displayName + "!");

        // Drop items
        for (Map.Entry<String, DropConfig> dropEntry : bc.drops.entrySet()) {
            DropConfig dc = dropEntry.getValue();
            if (random.nextDouble() < dc.chance) {
                String materialName = dropEntry.getKey();
                try {
                    Material material = Material.valueOf(materialName.toUpperCase());
                    int amount = random.nextInt(dc.maxAmount - dc.minAmount + 1) + dc.minAmount;
                    ItemStack dropItem = new ItemStack(material, amount);
                    if (currentBoss != null) {
                        currentBoss.getWorld().dropItemNaturally(currentBoss.getLocation(), dropItem);
                    }
                } catch (IllegalArgumentException e) {
                    logger.warning("[BossEvent] Invalid drop material: " + materialName);
                }
            }
        }

        logManager.logDisaster("BOSS: " + bc.displayName + " killed by " + killer.getName());
    }

    private void cleanup() {
        if (currentBoss != null && !currentBoss.isDead()) {
            currentBoss.remove();
        }
        bossActive = false;
        currentBoss = null;
        currentBossName = null;
        pendingBoss = null;
        warningTimeLeft = 0;
        bossBar.name(Component.text(""));
        bossBar.progress(0);
        warningBossBar.name(Component.text(""));
        warningBossBar.progress(0);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.hideBossBar(bossBar);
            p.hideBossBar(warningBossBar);
        }
    }

    public boolean isBossActive() {
        return bossActive;
    }

    public String getCurrentBossName() {
        return currentBossName;
    }

    public Set<String> getBossIds() {
        return bossConfigs.keySet();
    }

    public String getBossName(String id) {
        BossConfig bc = bossConfigs.get(id.toLowerCase());
        if (bc == null) return "Unknown Boss";
        return bc.displayName;
    }

    public boolean triggerBoss(String bossId, int warningTimeSeconds, int durationSeconds) {
        if (bossActive) return false;
        
        BossConfig bc = bossConfigs.get(bossId.toLowerCase());
        if (bc == null) return false;

        // Override warning time and duration
        bc.warningSeconds = warningTimeSeconds;
        bc.durationSeconds = durationSeconds;
        
        startWarning(bc);
        return true;
    }

    public void stop() {
        cleanup();
    }
}
