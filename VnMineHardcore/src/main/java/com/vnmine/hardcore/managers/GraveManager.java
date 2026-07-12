package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Quản lý ngôi mộ (Graves) cho người chơi khi chết.
 * Mỗi lần chết, một ngôi mộ được xây tại vị trí chết với:
 * - Thân mộ: 3 Stone Brick Slabs
 * - Bia mộ: Stone Brick Wall
 * - Đầu: Player Head (skin của người chết)
 * - Hoa: Wither Rose
 */
public class GraveManager {

    private final VnMineHardcore plugin;
    private final ConfigManager config;
    private final Logger logger;
    private final File dataFile;
    private final File dataBackupFile;
    private final YamlConfiguration data;

    // graveId -> GraveData
    private final Map<String, GraveData> graves = new ConcurrentHashMap<>();
    // UUID -> list of graveIds (để dễ dàng tìm mộ cũ)
    private final Map<UUID, List<String>> playerGraves = new ConcurrentHashMap<>();

    // Task cập nhật display name
    private BukkitRunnable displayTask;

    // ===== CẤU TRÚC MỘ (relative offsets từ vị trí chết) =====
    private static final GraveBlock[] GRAVE_STRUCTURE = new GraveBlock[] {
        // Thân mộ (y=0)
        new GraveBlock(-1, 0, 0, Material.STONE_BRICK_SLAB),
        new GraveBlock(0, 0, 0, Material.STONE_BRICK_SLAB),
        new GraveBlock(1, 0, 0, Material.STONE_BRICK_SLAB),
        // Bia mộ (y=1)
        new GraveBlock(0, 1, 0, Material.STONE_BRICK_WALL),
        // Đầu (y=2)
        new GraveBlock(0, 2, 0, Material.PLAYER_HEAD),
        // Hoa (y=0, z=1)
        new GraveBlock(0, 0, 1, Material.WITHER_ROSE)
    };

    private static class GraveBlock {
        final int dx, dy, dz;
        final Material material;

        GraveBlock(int dx, int dy, int dz, Material material) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.material = material;
        }
    }

    /**
     * Dữ liệu một ngôi mộ
     */
    public static class GraveData {
        String graveId;           // UUID ngẫu nhiên
        UUID owner;               // UUID chủ mộ
        String ownerName;         // Tên chủ mộ
        String worldName;         // Tên world
        int x, y, z;              // Tọa độ mộ (vị trí chân)
        long createdAt;           // Thời gian tạo (millis)
        boolean isEmpty;          // Mộ trống (đã hết hạn)
        int deathCountAtCreation; // Số lần chết khi tạo mộ
        LinkedHashMap<Integer, ItemStack> items; // Slot -> Item (giữ nguyên vị trí)

        GraveData() {
            this.graveId = UUID.randomUUID().toString().substring(0, 8);
            this.items = new LinkedHashMap<>();
        }
    }

    // ===== CONFIG VALUES =====
    public boolean enabled;
    public int protectionMinutes;
    public int publicMinutes;
    public int expireDeaths;
    public String emptyMessage;
    public String displayFormat;

    public GraveManager(VnMineHardcore plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.logger = plugin.getLogger();

        this.dataFile = new File(plugin.getDataFolder(), "graves.yml");
        this.dataBackupFile = new File(plugin.getDataFolder(), "graves.yml.bak");
        this.data = YamlConfiguration.loadConfiguration(dataFile);

        loadConfig();
        loadData();
        startDisplayTask();

        logger.info("[Grave] Initialized: enabled=" + enabled +
            ", protection=" + protectionMinutes + "m" +
            ", public=" + publicMinutes + "m" +
            ", expire-deaths=" + expireDeaths);
    }

    private void loadConfig() {
        // Config values sẽ được load từ config.yml qua plugin
        this.enabled = plugin.getConfig().getBoolean("graves.enabled", true);
        this.protectionMinutes = plugin.getConfig().getInt("graves.protection-minutes", 30);
        this.publicMinutes = plugin.getConfig().getInt("graves.public-minutes", 15);
        this.expireDeaths = plugin.getConfig().getInt("graves.expire-deaths", 3);
        this.emptyMessage = plugin.getConfig().getString("graves.empty-message", "§7Ngôi mộ này chỉ còn xương trắng...");
        this.displayFormat = plugin.getConfig().getString("graves.display-format", "§cR.I.P §4{name} §c- {time}");
    }

    public void reload() {
        loadConfig();
        loadData();
    }

    // ===== CREATE GRAVE =====

    /**
     * Tạo mộ cho người chơi tại vị trí chết
     */
    public void createGrave(Player player, Location deathLocation) {
        if (!enabled) return;

        GraveData grave = new GraveData();
        grave.owner = player.getUniqueId();
        grave.ownerName = player.getName();
        grave.worldName = deathLocation.getWorld().getName();
        grave.x = deathLocation.getBlockX();
        grave.y = deathLocation.getBlockY();
        grave.z = deathLocation.getBlockZ();
        grave.createdAt = System.currentTimeMillis();
        grave.deathCountAtCreation = plugin.getLogManager().getDeathCount(player);
        grave.isEmpty = false;

        // Lưu toàn bộ inventory (36 main + 4 armor + 1 offhand = 41 slots)
        // Slot 0-35: main inventory
        // Slot 36-39: armor (boots, leggings, chestplate, helmet)
        // Slot 40: offhand
        for (int i = 0; i < 36; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                grave.items.put(i, item.clone());
            }
        }
        // Armor slots
        for (int i = 0; i < 4; i++) {
            ItemStack item = player.getInventory().getArmorContents()[i];
            if (item != null && item.getType() != Material.AIR) {
                grave.items.put(36 + i, item.clone());
            }
        }
        // Offhand
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && offhand.getType() != Material.AIR) {
            grave.items.put(40, offhand.clone());
        }

        // Xây cấu trúc mộ
        buildGraveStructure(deathLocation, player.getName());

        // Lưu dữ liệu
        graves.put(grave.graveId, grave);
        playerGraves.computeIfAbsent(grave.owner, k -> new ArrayList<>()).add(grave.graveId);
        saveData();

        logger.info("[Grave] Created grave " + grave.graveId + " for " + player.getName() +
            " at " + grave.x + "," + grave.y + "," + grave.z + " (" + grave.items.size() + " items)");
    }

    /**
     * Xây cấu trúc mộ tại vị trí
     */
    private void buildGraveStructure(Location baseLocation, String playerName) {
        World world = baseLocation.getWorld();
        if (world == null) return;

        // Kiểm tra và dọn dẹp block cũ tại vị trí
        for (GraveBlock block : GRAVE_STRUCTURE) {
            Location loc = baseLocation.clone().add(block.dx, block.dy, block.dz);
            loc.getBlock().setType(Material.AIR);
        }

        // Xây mộ
        for (GraveBlock block : GRAVE_STRUCTURE) {
            Location loc = baseLocation.clone().add(block.dx, block.dy, block.dz);

            if (block.material == Material.PLAYER_HEAD) {
                // Đặt đầu người chơi với skin
                loc.getBlock().setType(Material.PLAYER_HEAD);
                if (loc.getBlock().getState() instanceof org.bukkit.block.Skull skull) {
                    skull.setOwningPlayer(Bukkit.getOfflinePlayer(playerName));
                    skull.update();
                }
            } else {
                loc.getBlock().setType(block.material);
            }
        }
    }

    /**
     * Phá hủy cấu trúc mộ
     */
    public void destroyGraveStructure(Location baseLocation) {
        World world = baseLocation.getWorld();
        if (world == null) return;

        for (GraveBlock block : GRAVE_STRUCTURE) {
            Location loc = baseLocation.clone().add(block.dx, block.dy, block.dz);
            loc.getBlock().setType(Material.AIR);
        }
    }

    // ===== INTERACT WITH GRAVE =====

    /**
     * Xử lý khi người chơi click vào head của mộ
     * @return true nếu xử lý thành công
     */
    public boolean interactGrave(Player player, Location headLocation) {
        if (!enabled) return false;

        // Tìm grave dựa trên vị trí head
        GraveData grave = findGraveByHeadLocation(headLocation);
        if (grave == null) return false;

        // Kiểm tra mộ có trống không
        if (grave.isEmpty) {
            player.sendMessage(emptyMessage);
            return true;
        }

        // Kiểm tra thời gian hết hạn
        long now = System.currentTimeMillis();
        long age = now - grave.createdAt;
        long totalDurationMs = (protectionMinutes + publicMinutes) * 60 * 1000L;

        if (age >= totalDurationMs) {
            // Mộ đã hết hạn
            grave.isEmpty = true;
            saveData();
            player.sendMessage(emptyMessage);
            return true;
        }

        // Kiểm tra death count expiration
        int currentDeaths = grave.deathCountAtCreation; // fallback
        Player ownerPlayer = Bukkit.getPlayer(grave.owner);
        if (ownerPlayer != null && ownerPlayer.isOnline()) {
            currentDeaths = plugin.getLogManager().getDeathCount(ownerPlayer);
        }
        if (currentDeaths - grave.deathCountAtCreation >= expireDeaths) {
            grave.isEmpty = true;
            saveData();
            player.sendMessage(emptyMessage);
            return true;
        }

        // Kiểm tra quyền truy cập
        boolean isOwner = player.getUniqueId().equals(grave.owner);
        boolean inProtection = age < protectionMinutes * 60 * 1000L;
        boolean inPublic = !inProtection && age < (protectionMinutes + publicMinutes) * 60 * 1000L;

        if (inProtection && !isOwner) {
            // Đang trong thời gian bảo vệ, chỉ chủ mộ mới lấy được
            long remaining = (protectionMinutes * 60 * 1000L - age) / 1000;
            player.sendMessage("§c§l⚠ Ngôi mộ này đang được bảo vệ! §7Chỉ §c" + grave.ownerName + " §7mới có thể lấy items.");
            player.sendMessage("§7Thời gian bảo vệ còn: §a§l" + formatTime(remaining));
            return true;
        }

        if (!inProtection && !inPublic) {
            // Hết thời gian
            grave.isEmpty = true;
            saveData();
            player.sendMessage(emptyMessage);
            return true;
        }

        // Cho phép lấy items (inPublic hoặc isOwner)
        retrieveItems(player, grave);
        return true;
    }

    /**
     * Trả items về cho người chơi, giữ nguyên vị trí slot cũ
     */
    private void retrieveItems(Player player, GraveData grave) {
        int restored = 0;
        int dropped = 0;

        for (Map.Entry<Integer, ItemStack> entry : grave.items.entrySet()) {
            int slot = entry.getKey();
            ItemStack item = entry.getValue();

            if (slot < 36) {
                // Main inventory
                if (player.getInventory().getItem(slot) == null || 
                    player.getInventory().getItem(slot).getType() == Material.AIR) {
                    player.getInventory().setItem(slot, item);
                    restored++;
                } else {
                    // Slot đã có item, tìm slot trống
                    HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                    if (!leftover.isEmpty()) {
                        // Inventory full, drop ra đất
                        player.getWorld().dropItemNaturally(player.getLocation(), item);
                        dropped++;
                    } else {
                        restored++;
                    }
                }
            } else if (slot >= 36 && slot < 40) {
                // Armor slots (36=boots, 37=leggings, 38=chestplate, 39=helmet)
                int armorIndex = slot - 36;
                ItemStack[] armor = player.getInventory().getArmorContents();
                if (armor[armorIndex] == null || armor[armorIndex].getType() == Material.AIR) {
                    armor[armorIndex] = item;
                    player.getInventory().setArmorContents(armor);
                    restored++;
                } else {
                    // Armor slot bận, thử đưa vào main inventory
                    HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                    if (!leftover.isEmpty()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), item);
                        dropped++;
                    } else {
                        restored++;
                    }
                }
            } else if (slot == 40) {
                // Offhand
                if (player.getInventory().getItemInOffHand() == null || 
                    player.getInventory().getItemInOffHand().getType() == Material.AIR) {
                    player.getInventory().setItemInOffHand(item);
                    restored++;
                } else {
                    HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                    if (!leftover.isEmpty()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), item);
                        dropped++;
                    } else {
                        restored++;
                    }
                }
            } else {
                // Unknown slot, add to inventory
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                if (!leftover.isEmpty()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                    dropped++;
                } else {
                    restored++;
                }
            }
        }

        // Đánh dấu mộ đã lấy
        grave.isEmpty = true;
        saveData();

        // Phá hủy cấu trúc mộ
        Location graveLocation = new Location(
            Bukkit.getWorld(grave.worldName), grave.x, grave.y, grave.z);
        destroyGraveStructure(graveLocation);

        // Thông báo
        String baseMsg = "§a§l✅ Đã lấy lại items từ mộ của §c" + grave.ownerName + "§a!";
        String detailMsg = "§7Khôi phục: §a" + restored + " §7item" + (dropped > 0 ? " §c(Rơi: " + dropped + ")" : "");
        player.sendMessage(baseMsg);
        player.sendMessage(detailMsg);

        logger.info("[Grave] " + player.getName() + " retrieved grave " + grave.graveId +
            " of " + grave.ownerName + " (" + restored + " restored, " + dropped + " dropped)");
    }

    // ===== FIND GRAVE =====

    /**
     * Tìm grave dựa trên vị trí block head
     */
    public GraveData findGraveByHeadLocation(Location headLocation) {
        // Vị trí head là (base.x, base.y+2, base.z) so với base
        // => base = (head.x, head.y-2, head.z)
        Location baseLoc = headLocation.clone().subtract(0, 2, 0);

        for (GraveData grave : graves.values()) {
            if (grave.isEmpty) continue;
            if (grave.x == baseLoc.getBlockX() &&
                grave.y == baseLoc.getBlockY() &&
                grave.z == baseLoc.getBlockZ() &&
                grave.worldName.equals(baseLoc.getWorld().getName())) {
                return grave;
            }
        }
        return null;
    }

    /**
     * Tìm tất cả mộ của một người chơi
     */
    public List<GraveData> getPlayerGraves(UUID playerUUID) {
        List<String> graveIds = playerGraves.get(playerUUID);
        if (graveIds == null) return new ArrayList<>();

        List<GraveData> result = new ArrayList<>();
        for (String id : graveIds) {
            GraveData grave = graves.get(id);
            if (grave != null) result.add(grave);
        }
        return result;
    }

    // ===== DISPLAY TASK =====

    /**
     * Task cập nhật display name cho block head mỗi giây
     */
    private void startDisplayTask() {
        displayTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!enabled) return;

                long now = System.currentTimeMillis();

                for (GraveData grave : graves.values()) {
                    if (grave.isEmpty) continue;

                    long age = now - grave.createdAt;
                    long totalDurationMs = (protectionMinutes + publicMinutes) * 60 * 1000L;

                    if (age >= totalDurationMs) {
                        // Mộ đã hết hạn, đánh dấu trống
                        grave.isEmpty = true;
                        saveData();
                        continue;
                    }

                    // Tính thời gian còn lại
                    long remainingMs = totalDurationMs - age;
                    long remainingSeconds = remainingMs / 1000;
                    String timeStr = formatTime(remainingSeconds);

                    // Xác định màu sắc dựa trên giai đoạn
                    boolean inProtection = age < protectionMinutes * 60 * 1000L;
                    String timeColor;
                    if (inProtection) {
                        timeColor = "§a§l"; // Xanh lá đậm - protection
                    } else {
                        timeColor = "§b§l"; // Xanh da trời đậm - public
                    }

                    // Tạo display name
                    String displayName = displayFormat
                        .replace("{name}", "§4" + grave.ownerName + "§r")
                        .replace("{time}", timeColor + timeStr);

                    // Cập nhật tên của block head
                    Location headLoc = new Location(
                        Bukkit.getWorld(grave.worldName),
                        grave.x, grave.y + 2, grave.z);

                    if (headLoc.getBlock().getType() == Material.PLAYER_HEAD ||
                        headLoc.getBlock().getType() == Material.PLAYER_WALL_HEAD) {
                        if (headLoc.getBlock().getState() instanceof org.bukkit.block.Skull skull) {
                            if (!displayName.equals(skull.customName())) {
                                skull.customName(net.kyori.adventure.text.Component.text(displayName));
                                skull.update();
                            }
                        }
                    }
                }
            }
        };
        displayTask.runTaskTimer(plugin, 40L, 20L); // Mỗi giây
    }

    /**
     * Format thời gian từ giây sang MM:SS
     */
    private String formatTime(long seconds) {
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    // ===== DATA SAVE/LOAD =====

    private synchronized void saveData() {
        try {
            // Backup
            if (dataFile.exists()) {
                java.nio.file.Files.copy(dataFile.toPath(), dataBackupFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            data.set("_version", 1);
            data.set("_updated", System.currentTimeMillis());

            for (GraveData grave : graves.values()) {
                String path = "graves." + grave.graveId;
                data.set(path + ".owner", grave.owner.toString());
                data.set(path + ".ownerName", grave.ownerName);
                data.set(path + ".world", grave.worldName);
                data.set(path + ".x", grave.x);
                data.set(path + ".y", grave.y);
                data.set(path + ".z", grave.z);
                data.set(path + ".createdAt", grave.createdAt);
                data.set(path + ".isEmpty", grave.isEmpty);
                data.set(path + ".deathCountAtCreation", grave.deathCountAtCreation);

                // Lưu items
                int itemIndex = 0;
                for (Map.Entry<Integer, ItemStack> entry : grave.items.entrySet()) {
                    String itemPath = path + ".items." + itemIndex;
                    data.set(itemPath + ".slot", entry.getKey());
                    data.set(itemPath + ".item", entry.getValue());
                    itemIndex++;
                }
                data.set(path + ".itemCount", grave.items.size());
            }

            // Lưu player -> grave mapping
            data.set("_playerGraves", null);
            for (Map.Entry<UUID, List<String>> entry : playerGraves.entrySet()) {
                data.set("_playerGraves." + entry.getKey().toString(), entry.getValue());
            }

            data.save(dataFile);
        } catch (Exception e) {
            logger.warning("[Grave] Failed to save data: " + e.getMessage());
        }
    }

    private synchronized void loadData() {
        graves.clear();
        playerGraves.clear();

        if (!dataFile.exists()) return;

        try {
            // Load player -> grave mapping
            if (data.contains("_playerGraves")) {
                for (String uuidStr : data.getConfigurationSection("_playerGraves").getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        List<String> graveIds = data.getStringList("_playerGraves." + uuidStr);
                        playerGraves.put(uuid, new ArrayList<>(graveIds));
                    } catch (IllegalArgumentException ignored) {}
                }
            }

            // Load graves
            if (data.contains("graves")) {
                for (String graveId : data.getConfigurationSection("graves").getKeys(false)) {
                    try {
                        GraveData grave = new GraveData();
                        grave.graveId = graveId;
                        grave.owner = UUID.fromString(data.getString("graves." + graveId + ".owner"));
                        grave.ownerName = data.getString("graves." + graveId + ".ownerName", "Unknown");
                        grave.worldName = data.getString("graves." + graveId + ".world", "world");
                        grave.x = data.getInt("graves." + graveId + ".x", 0);
                        grave.y = data.getInt("graves." + graveId + ".y", 0);
                        grave.z = data.getInt("graves." + graveId + ".z", 0);
                        grave.createdAt = data.getLong("graves." + graveId + ".createdAt", System.currentTimeMillis());
                        grave.isEmpty = data.getBoolean("graves." + graveId + ".isEmpty", false);
                        grave.deathCountAtCreation = data.getInt("graves." + graveId + ".deathCountAtCreation", 0);

                        // Parse items
                        int itemCount = data.getInt("graves." + graveId + ".itemCount", 0);
                        for (int i = 0; i < itemCount; i++) {
                            String itemPath = "graves." + graveId + ".items." + i;
                            int slot = data.getInt(itemPath + ".slot", -1);
                            ItemStack item = data.getItemStack(itemPath + ".item");
                            if (slot >= 0 && item != null) {
                                grave.items.put(slot, item);
                            }
                        }

                        graves.put(graveId, grave);
                    } catch (Exception e) {
                        logger.warning("[Grave] Error loading grave " + graveId + ": " + e.getMessage());
                    }
                }
            }

            logger.info("[Grave] Loaded " + graves.size() + " graves for " + playerGraves.size() + " players.");
        } catch (Exception e) {
            logger.warning("[Grave] Failed to load data: " + e.getMessage());
        }
    }

    public void stop() {
        if (displayTask != null) {
            displayTask.cancel();
            displayTask = null;
        }
        saveData();
    }
}