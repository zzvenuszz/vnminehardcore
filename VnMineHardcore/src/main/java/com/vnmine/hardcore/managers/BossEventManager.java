package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

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
    private int timeSinceLastBoss = 0;
    private int warningTimeLeft = 0;
    private BossConfig pendingBoss = null;

    // AI tracking
    private Location lastBossLocation = null;
    private int stuckTicks = 0;
    private BukkitRunnable aiTask = null;

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
            // Mặc định enabled = true nếu không cấu hình
            bc.enabled = section.getBoolean("enabled", true);
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
        if (pendingBoss != null) return; // Already has a warning in progress

        // Lọc các boss enabled
        List<Map.Entry<String, BossConfig>> enabledBosses = new ArrayList<>();
        for (Map.Entry<String, BossConfig> entry : bossConfigs.entrySet()) {
            if (entry.getValue().enabled) {
                enabledBosses.add(entry);
            }
        }

        if (enabledBosses.isEmpty()) return;

        // Tính tổng chance
        int totalChance = 0;
        for (Map.Entry<String, BossConfig> entry : enabledBosses) {
            totalChance += entry.getValue().chance;
        }

        if (totalChance <= 0) {
            // Nếu tổng chance = 0, chọn boss đầu tiên
            startWarning(enabledBosses.get(0).getValue());
            return;
        }

        // Weighted random: roll trong [0, totalChance)
        int roll = random.nextInt(totalChance);
        int cumulative = 0;
        for (Map.Entry<String, BossConfig> entry : enabledBosses) {
            BossConfig bc = entry.getValue();
            cumulative += bc.chance;
            if (roll < cumulative) {
                startWarning(bc);
                return;
            }
        }

        // Fallback: không bao giờ chạy tới đây nếu totalChance > 0
        startWarning(enabledBosses.get(enabledBosses.size() - 1).getValue());
    }

    private void startWarning(BossConfig bc) {
        if (bossActive) return;
        
        pendingBoss = bc;
        warningTimeLeft = bc.warningSeconds;
        
        // Show warning bar
        bossBar.name(Component.text("§e§l⚠ BOSS SẮP XUẤT HIỆN ⚠"));
        bossBar.progress(1.0f);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(bossBar);
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 0.5f);
        }
        
        Bukkit.broadcastMessage("§4§l⚠ CẢNH BÁO: §c" + bc.displayName + " §4§lsẽ xuất hiện trong §e§l" + bc.warningSeconds + " giây§4§l!");
    }

    private void updateWarningBar() {
        if (warningTimeLeft > 0 && pendingBoss != null) {
            bossBar.name(Component.text("§e§l⚠ " + pendingBoss.displayName + " §7- §c§l" + warningTimeLeft + "s"));
            bossBar.progress((float) warningTimeLeft / pendingBoss.warningSeconds);
            
            if (warningTimeLeft <= 10 || warningTimeLeft == 30) {
                Bukkit.broadcastMessage("§c§l⚠ " + pendingBoss.displayName + " §csẽ xuất hiện trong §4§l" + warningTimeLeft + "§c giây!");
            }
        }
    }

    private void spawnBoss(BossConfig bc) {
        if (bossActive) return;
        
        // Hide warning bar
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.hideBossBar(bossBar);
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

        // Remove default AI để dùng AI custom
        if (currentBoss instanceof Mob mob) {
            mob.setAI(true); // Vẫn giữ AI mặc định, nhưng chúng ta sẽ điều khiển thêm
        }

        bossActive = true;
        currentBossName = bc.displayName;
        timeSinceLastBoss = 0;
        lastBossLocation = spawnLoc.clone();
        stuckTicks = 0;

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

        // Khởi động AI task cho boss
        startBossAI(bc);

        // Task theo dõi boss (cập nhật boss bar, kiểm tra chết/hết thời gian)
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

    /**
     * AI cho boss: chạy mỗi tick (20 lần/giây) để boss có hành vi thông minh
     */
    private void startBossAI(BossConfig bc) {
        if (aiTask != null) {
            aiTask.cancel();
        }

        aiTask = new BukkitRunnable() {
            int tickCounter = 0;

            @Override
            public void run() {
                if (currentBoss == null || currentBoss.isDead() || !bossActive) {
                    this.cancel();
                    return;
                }

                tickCounter++;

                // Mỗi 10 ticks (0.5 giây) thực hiện AI
                if (tickCounter % 10 != 0) return;

                Location bossLoc = currentBoss.getLocation();
                World bossWorld = bossLoc.getWorld();
                if (bossWorld == null) return;

                // === KIỂM TRA KẸT ===
                if (lastBossLocation != null) {
                    double dist = bossLoc.distanceSquared(lastBossLocation);
                    if (dist < 0.5) {
                        stuckTicks++;
                    } else {
                        stuckTicks = 0;
                    }
                }
                lastBossLocation = bossLoc.clone();

                // Nếu bị kẹt quá 5 giây (10 lần kiểm tra * 0.5s = 5s)
                if (stuckTicks >= 10) {
                    // Teleport lên cao hơn để thoát kẹt
                    Location newLoc = bossLoc.clone().add(0, 5, 0);
                    if (newLoc.getBlock().getType() == Material.AIR) {
                        currentBoss.teleport(newLoc);
                    } else {
                        // Nếu phía trên có block, teleport ngẫu nhiên xung quanh
                        int tryX = bossLoc.getBlockX() + random.nextInt(10) - 5;
                        int tryZ = bossLoc.getBlockZ() + random.nextInt(10) - 5;
                        int tryY = bossWorld.getHighestBlockYAt(tryX, tryZ) + 1;
                        currentBoss.teleport(new Location(bossWorld, tryX + 0.5, tryY, tryZ + 0.5));
                    }
                    stuckTicks = 0;
                    return;
                }

                // === TÌM MỤC TIÊU ===
                // Bán kính tìm kiếm: 20 blocks cho player, 15 cho mob
                double searchRadius = 20.0;

                // Ưu tiên 1: Tấn công người chơi
                Player nearestPlayer = null;
                double nearestPlayerDist = Double.MAX_VALUE;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.getWorld().equals(bossWorld)) continue;
                    if (p.isDead() || !p.isOnline()) continue;
                    double d = p.getLocation().distanceSquared(bossLoc);
                    if (d < nearestPlayerDist && d < searchRadius * searchRadius) {
                        nearestPlayerDist = d;
                        nearestPlayer = p;
                    }
                }

                if (nearestPlayer != null) {
                    // Tấn công người chơi
                    attackTarget(nearestPlayer, bc);
                    return;
                }

                // Ưu tiên 2: Tấn công friendly mobs (Villager, Iron Golem, Snowman, etc.)
                LivingEntity nearestFriendly = null;
                double nearestFriendlyDist = Double.MAX_VALUE;
                for (Entity e : bossWorld.getNearbyEntities(bossLoc, 15, 10, 15)) {
                    if (e.equals(currentBoss)) continue;
                    if (e instanceof LivingEntity le && !(e instanceof Player)) {
                        if (isFriendlyMob(le)) {
                            double d = le.getLocation().distanceSquared(bossLoc);
                            if (d < nearestFriendlyDist) {
                                nearestFriendlyDist = d;
                                nearestFriendly = le;
                            }
                        }
                    }
                }

                if (nearestFriendly != null) {
                    attackTarget(nearestFriendly, bc);
                    return;
                }

                // Ưu tiên 3: Tấn công các mobs khác
                LivingEntity nearestMob = null;
                double nearestMobDist = Double.MAX_VALUE;
                for (Entity e : bossWorld.getNearbyEntities(bossLoc, 15, 10, 15)) {
                    if (e.equals(currentBoss)) continue;
                    if (e instanceof LivingEntity le && !(e instanceof Player)) {
                        if (!isFriendlyMob(le) && !(le instanceof Boss)) {
                            double d = le.getLocation().distanceSquared(bossLoc);
                            if (d < nearestMobDist) {
                                nearestMobDist = d;
                                nearestMob = le;
                            }
                        }
                    }
                }

                if (nearestMob != null) {
                    attackTarget(nearestMob, bc);
                    return;
                }

                // Ưu tiên 4: Di chuyển ngẫu nhiên và phá hủy địa hình
                wanderAndDestroy(bossLoc, bossWorld, bc);
            }
        };

        aiTask.runTaskTimer(plugin, 0L, 1L); // Mỗi tick
    }

    /**
     * Tấn công mục tiêu: di chuyển đến và gây sát thương
     */
    private void attackTarget(LivingEntity target, BossConfig bc) {
        if (currentBoss == null || currentBoss.isDead()) return;

        Location bossLoc = currentBoss.getLocation();
        Location targetLoc = target.getLocation();
        World world = bossLoc.getWorld();
        if (world == null) return;
        double distance = bossLoc.distance(targetLoc);

        if (currentBoss instanceof EnderDragon) {
            // Ender Dragon: bay vòng quanh mục tiêu và tấn công từ xa
            if (distance > 6.0) {
                // Bay đến gần mục tiêu hơn
                Vector dir = targetLoc.toVector().subtract(bossLoc.toVector()).normalize();
                // Thêm chút độ cao để bay vòng
                dir.setY(0.3 + Math.sin(bossLoc.getX() * 0.1) * 0.2);
                currentBoss.setVelocity(dir.multiply(0.6));
            } else if (distance > 3.0) {
                // Bay vòng quanh mục tiêu
                double angle = Math.atan2(bossLoc.getZ() - targetLoc.getZ(), bossLoc.getX() - targetLoc.getX());
                angle += 0.3; // Xoay vòng
                double circleX = targetLoc.getX() + Math.cos(angle) * 4.0;
                double circleZ = targetLoc.getZ() + Math.sin(angle) * 4.0;
                Vector circleDir = new Vector(circleX - bossLoc.getX(), 0.2, circleZ - bossLoc.getZ()).normalize();
                currentBoss.setVelocity(circleDir.multiply(0.5));
            } else {
                // Ở rất gần - tấn công
                target.damage(10.0 * bc.damageMultiplier, currentBoss);
                // Đẩy lùi mục tiêu
                Vector knockback = target.getLocation().toVector().subtract(bossLoc.toVector()).normalize();
                knockback.setY(0.5);
                target.setVelocity(knockback.multiply(1.5));
            }
        } else if (currentBoss instanceof Giant) {
            // Giant: di chuyển nhanh, phá hủy mạnh
            if (distance > 3.0) {
                // Di chuyển về phía mục tiêu với tốc độ cao
                Vector dir = targetLoc.toVector().subtract(bossLoc.toVector()).normalize();
                dir.setY(0);
                currentBoss.setVelocity(dir.multiply(0.5));
                // Phá hủy block trên đường đi
                destroyBlocksAround(bossLoc, world, 3);
            } else {
                // Ở gần - tấn công mạnh
                target.damage(12.0 * bc.damageMultiplier, currentBoss);
                // Đẩy lùi mục tiêu
                Vector knockback = target.getLocation().toVector().subtract(bossLoc.toVector()).normalize();
                knockback.setY(0.3);
                target.setVelocity(knockback.multiply(2.0));
                // Phá hủy block xung quanh khi tấn công
                destroyBlocksAround(bossLoc, world, 4);
            }
        } else if (currentBoss instanceof Mob mob) {
            // Nếu là Mob (zombie, skeleton, v.v.) dùng pathfinder
            if (distance > 2.0) {
                mob.lookAt(targetLoc);
                Vector dir = targetLoc.toVector().subtract(bossLoc.toVector()).normalize();
                dir.setY(0);
                if (distance > 1.0) {
                    currentBoss.setVelocity(dir.multiply(0.3));
                }
            }
        } else if (currentBoss instanceof Flying flying) {
            // Nếu là flying entity (Ghast)
            if (distance > 4.0) {
                Vector dir = targetLoc.toVector().subtract(bossLoc.toVector()).normalize();
                currentBoss.setVelocity(dir.multiply(0.4));
            }
        } else {
            // Các entity khác
            if (distance > 2.0) {
                Vector dir = targetLoc.toVector().subtract(bossLoc.toVector()).normalize();
                dir.setY(0);
                currentBoss.setVelocity(dir.multiply(0.3));
            }
        }

        // Tấn công nếu ở gần (cho các entity không phải dragon/giant)
        if (!(currentBoss instanceof EnderDragon) && !(currentBoss instanceof Giant) && distance <= 4.0) {
            target.damage(8.0 * bc.damageMultiplier, currentBoss);
        }
    }

    /**
     * Phá hủy block xung quanh vị trí (dùng cho Giant)
     */
    private void destroyBlocksAround(Location center, World world, int radius) {
        for (int bx = -radius; bx <= radius; bx++) {
            for (int by = -1; by <= 1; by++) {
                for (int bz = -radius; bz <= radius; bz++) {
                    if (random.nextInt(3) != 0) continue; // Không phá hủy tất cả
                    Block block = world.getBlockAt(
                        center.getBlockX() + bx,
                        center.getBlockY() + by,
                        center.getBlockZ() + bz
                    );
                    Material type = block.getType();
                    if (isDestructibleBlock(type)) {
                        world.playSound(block.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 0.8f);
                        world.dropItemNaturally(block.getLocation(), new ItemStack(type, 1));
                        block.setType(Material.AIR);
                    }
                }
            }
        }
    }

    /**
     * Di chuyển ngẫu nhiên và phá hủy block xung quanh
     */
    private void wanderAndDestroy(Location bossLoc, World world, BossConfig bc) {
        if (currentBoss == null || currentBoss.isDead()) return;

        if (currentBoss instanceof EnderDragon) {
            // Ender Dragon: bay vòng tròn ngẫu nhiên trên cao
            double angle = random.nextDouble() * 2 * Math.PI;
            double radius = 5.0 + random.nextDouble() * 10.0;
            double targetX = bossLoc.getX() + Math.cos(angle) * radius;
            double targetZ = bossLoc.getZ() + Math.sin(angle) * radius;
            double targetY = bossLoc.getY() + (random.nextDouble() - 0.5) * 10.0;
            // Giới hạn độ cao
            targetY = Math.max(20, Math.min(world.getMaxHeight() - 10, targetY));
            
            Vector dir = new Vector(targetX - bossLoc.getX(), targetY - bossLoc.getY(), targetZ - bossLoc.getZ()).normalize();
            currentBoss.setVelocity(dir.multiply(0.5));
            
            // Phá hủy block trên đường bay
            if (random.nextInt(3) == 0) {
                destroyBlocksAround(bossLoc, world, 2);
            }
        } else if (currentBoss instanceof Giant) {
            // Giant: di chuyển ngẫu nhiên và phá hủy mạnh
            if (random.nextInt(3) == 0) {
                double angle = random.nextDouble() * 2 * Math.PI;
                double dx = Math.cos(angle) * 3.0;
                double dz = Math.sin(angle) * 3.0;
                Location targetLoc = bossLoc.clone().add(dx, 0, dz);
                
                if (targetLoc.getBlock().getType() == Material.AIR) {
                    Vector dir = targetLoc.toVector().subtract(bossLoc.toVector()).normalize();
                    dir.setY(0);
                    currentBoss.setVelocity(dir.multiply(0.4));
                }
            }

            // Phá hủy block xung quanh liên tục
            if (random.nextInt(2) == 0) {
                destroyBlocksAround(bossLoc, world, 3);
            }
        } else {
            // Các boss khác: di chuyển ngẫu nhiên
            if (random.nextInt(5) == 0) {
                double angle = random.nextDouble() * 2 * Math.PI;
                double dx = Math.cos(angle) * 2.0;
                double dz = Math.sin(angle) * 2.0;
                Location targetLoc = bossLoc.clone().add(dx, 0, dz);
                
                if (targetLoc.getBlock().getType() == Material.AIR) {
                    Vector dir = targetLoc.toVector().subtract(bossLoc.toVector()).normalize();
                    dir.setY(0);
                    currentBoss.setVelocity(dir.multiply(0.2));
                }
            }

            // Phá hủy block xung quanh
            if (random.nextInt(4) == 0) {
                int bx = bossLoc.getBlockX() + random.nextInt(5) - 2;
                int by = bossLoc.getBlockY() + random.nextInt(3) - 1;
                int bz = bossLoc.getBlockZ() + random.nextInt(5) - 2;
                Block block = world.getBlockAt(bx, by, bz);
                Material type = block.getType();

                if (isDestructibleBlock(type)) {
                    world.playSound(block.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 0.8f);
                    world.dropItemNaturally(block.getLocation(), new ItemStack(type, 1));
                    block.setType(Material.AIR);
                }
            }
        }
    }

    /**
     * Kiểm tra block có thể bị phá hủy bởi boss không
     */
    private boolean isDestructibleBlock(Material type) {
        return switch (type) {
            case OAK_LEAVES, SPRUCE_LEAVES, BIRCH_LEAVES, JUNGLE_LEAVES,
                 ACACIA_LEAVES, DARK_OAK_LEAVES, AZALEA_LEAVES, FLOWERING_AZALEA_LEAVES,
                 OAK_LOG, SPRUCE_LOG, BIRCH_LOG, JUNGLE_LOG, ACACIA_LOG, DARK_OAK_LOG,
                 OAK_WOOD, SPRUCE_WOOD, BIRCH_WOOD, JUNGLE_WOOD, ACACIA_WOOD, DARK_OAK_WOOD,
                 STRIPPED_OAK_LOG, STRIPPED_SPRUCE_LOG, STRIPPED_BIRCH_LOG,
                 STRIPPED_JUNGLE_LOG, STRIPPED_ACACIA_LOG, STRIPPED_DARK_OAK_LOG,
                 DIRT, GRASS_BLOCK, PODZOL, MYCELIUM, COARSE_DIRT, ROOTED_DIRT,
                 STONE, COBBLESTONE, ANDESITE, DIORITE, GRANITE, TUFF, DEEPSLATE,
                 SHORT_GRASS, TALL_GRASS, FERN, LARGE_FERN, DEAD_BUSH, VINE,
                 DANDELION, POPPY, BLUE_ORCHID, ALLIUM, AZURE_BLUET, OXEYE_DAISY,
                 CORNFLOWER, LILY_OF_THE_VALLEY, WITHER_ROSE, SUNFLOWER, LILAC, PEONY, ROSE_BUSH,
                 SAND, RED_SAND, GRAVEL, CLAY, SNOW_BLOCK, SNOW,
                 COBBLESTONE_STAIRS, STONE_STAIRS, ANDESITE_STAIRS, DIORITE_STAIRS, GRANITE_STAIRS,
                 COBBLESTONE_SLAB, STONE_SLAB, ANDESITE_SLAB, DIORITE_SLAB, GRANITE_SLAB,
                 COBBLESTONE_WALL, ANDESITE_WALL, DIORITE_WALL, GRANITE_WALL,
                 MOSSY_COBBLESTONE, MOSSY_STONE_BRICKS, MOSS_BLOCK, MOSS_CARPET,
                 COCOA, SWEET_BERRY_BUSH, SUGAR_CANE, CACTUS, BAMBOO,
                 MUD, MUDDY_MANGROVE_ROOTS, PACKED_MUD -> true;
            default -> false;
        };
    }

    /**
     * Kiểm tra entity có phải friendly mob không
     */
    private boolean isFriendlyMob(LivingEntity entity) {
        return entity instanceof Villager || 
               entity instanceof IronGolem || 
               entity instanceof Snowman ||
               entity instanceof Cat ||
               entity instanceof Wolf wolf && !wolf.isAngry() ||
               entity instanceof Parrot ||
               entity instanceof Bee ||
               entity instanceof Dolphin ||
               entity instanceof Fox ||
               entity instanceof Panda ||
               entity instanceof Turtle ||
               entity instanceof Axolotl ||
               entity instanceof Goat ||
               entity instanceof Allay ||
               entity instanceof Camel ||
               entity instanceof Sniffer ||
               entity instanceof Armadillo;
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
        // Hủy AI task
        if (aiTask != null) {
            aiTask.cancel();
            aiTask = null;
        }

        if (currentBoss != null && !currentBoss.isDead()) {
            currentBoss.remove();
        }
        bossActive = false;
        currentBoss = null;
        currentBossName = null;
        pendingBoss = null;
        warningTimeLeft = 0;
        lastBossLocation = null;
        stuckTicks = 0;
        bossBar.name(Component.text(""));
        bossBar.progress(0);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.hideBossBar(bossBar);
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