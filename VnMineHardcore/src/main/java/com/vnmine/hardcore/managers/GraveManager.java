package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
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
 * Mỗi lần chết, một ngôi mộ được xây tại vị trí chết theo cấu trúc
 * từ file graves-structure.yml.
 * 
 * Khi tạo mộ, các block cũ được lưu lại. Khi phá mộ (lấy đồ hoặc hết hạn),
 * các block cũ được khôi phục.
 * Tên người chết và thời gian hiển thị trên ArmorStand phía trên bia mộ.
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
    // UUID -> list of graveIds
    private final Map<UUID, List<String>> playerGraves = new ConcurrentHashMap<>();

    // Cấu trúc mộ load từ file
    private final List<GraveBlock> graveStructure = new ArrayList<>();

    // Task cập nhật display name
    private BukkitRunnable displayTask;

    private static class GraveBlock {
        final int dx, dy, dz;
        final Material material;
        final String data;

        GraveBlock(int dx, int dy, int dz, Material material, String data) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.material = material;
            this.data = data;
        }
    }

    /**
     * Dữ liệu một ngôi mộ
     */
    public static class GraveData {
        String graveId;
        UUID owner;
        String ownerName;
        String worldName;
        int x, y, z;
        long createdAt;
        boolean isEmpty;
        int deathCountAtCreation;
        LinkedHashMap<Integer, ItemStack> items;
        // Lưu block cũ: key = "dx_dy_dz", value = "MATERIAL;BLOCKDATA"
        Map<String, String> originalBlocks = new HashMap<>();
        // ArmorStand UUID để hiển thị tên trên mộ
        UUID armorStandUuid;

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
        loadStructure();
        loadData();
        startDisplayTask();

        logger.info("[Grave] Initialized: enabled=" + enabled +
            ", protection=" + protectionMinutes + "m" +
            ", public=" + publicMinutes + "m" +
            ", expire-deaths=" + expireDeaths +
            ", structure-blocks=" + graveStructure.size());
    }

    private void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("graves.enabled", true);
        this.protectionMinutes = plugin.getConfig().getInt("graves.protection-minutes", 30);
        this.publicMinutes = plugin.getConfig().getInt("graves.public-minutes", 15);
        this.expireDeaths = plugin.getConfig().getInt("graves.expire-deaths", 3);
        this.emptyMessage = plugin.getConfig().getString("graves.empty-message", "§7Ngôi mộ này chỉ còn xương trắng...");
        this.displayFormat = plugin.getConfig().getString("graves.display-format", "§cR.I.P §4{name} §c- {time}");
    }

    private void loadStructure() {
        graveStructure.clear();
        plugin.saveResource("graves-structure.yml", false);

        File structureFile = new File(plugin.getDataFolder(), "graves-structure.yml");
        YamlConfiguration structureConfig = YamlConfiguration.loadConfiguration(structureFile);

        if (!structureConfig.contains("blocks")) {
            logger.warning("[Grave] No blocks found in graves-structure.yml, using fallback structure");
            addFallbackStructure();
            return;
        }

        for (Object blockObj : structureConfig.getList("blocks")) {
            if (blockObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> blockMap = (Map<String, Object>) blockObj;
                int dx = getIntFromMap(blockMap, "dx", 0);
                int dy = getIntFromMap(blockMap, "dy", 0);
                int dz = getIntFromMap(blockMap, "dz", 0);
                String materialName = (String) blockMap.getOrDefault("material", "AIR");
                String blockData = (String) blockMap.getOrDefault("data", "");

                Material material = Material.getMaterial(materialName.toUpperCase());
                if (material == null || material == Material.AIR) {
                    logger.warning("[Grave] Invalid material '" + materialName + "' at dx=" + dx + ",dy=" + dy + ",dz=" + dz);
                    continue;
                }
                graveStructure.add(new GraveBlock(dx, dy, dz, material, blockData != null ? blockData : ""));
            }
        }

        if (graveStructure.isEmpty()) {
            logger.warning("[Grave] No valid blocks, using fallback structure");
            addFallbackStructure();
        }
    }

    private int getIntFromMap(Map<String, Object> map, String key, int defaultValue) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        return defaultValue;
    }

    private void addFallbackStructure() {
        graveStructure.add(new GraveBlock(0, 0, 0, Material.COBBLESTONE, ""));
        graveStructure.add(new GraveBlock(0, 1, 0, Material.COBBLESTONE, ""));
        graveStructure.add(new GraveBlock(0, 1, 1, Material.PLAYER_HEAD, "player-head"));
        graveStructure.add(new GraveBlock(0, 2, 0, Material.COBBLESTONE, ""));
    }

    public void reload() {
        loadConfig();
        loadStructure();
        loadData();
    }

    // ===== CREATE GRAVE =====

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

        for (int i = 0; i < 36; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                grave.items.put(i, item.clone());
            }
        }
        for (int i = 0; i < 4; i++) {
            ItemStack item = player.getInventory().getArmorContents()[i];
            if (item != null && item.getType() != Material.AIR) {
                grave.items.put(36 + i, item.clone());
            }
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && offhand.getType() != Material.AIR) {
            grave.items.put(40, offhand.clone());
        }

        buildGraveStructure(deathLocation, player.getName(), grave);

        graves.put(grave.graveId, grave);
        playerGraves.computeIfAbsent(grave.owner, k -> new ArrayList<>()).add(grave.graveId);
        saveData();

        logger.info("[Grave] Created grave " + grave.graveId + " for " + player.getName() +
            " at " + grave.x + "," + grave.y + "," + grave.z + " (" + grave.items.size() + " items)");
    }

    /**
     * Xây cấu trúc mộ tại vị trí.
     * Lưu block cũ, đặt block mộ, spawn ArmorStand hiển thị tên.
     */
    private void buildGraveStructure(Location baseLocation, String playerName, GraveData grave) {
        World world = baseLocation.getWorld();
        if (world == null) return;

        // 1. Lưu block cũ
        for (GraveBlock block : graveStructure) {
            Location loc = baseLocation.clone().add(block.dx, block.dy, block.dz);
            Block currentBlock = loc.getBlock();
            String key = block.dx + "_" + block.dy + "_" + block.dz;
            if (currentBlock.getType() != Material.AIR) {
                grave.originalBlocks.put(key,
                    currentBlock.getType().name() + ";" + currentBlock.getBlockData().getAsString());
            }
        }

        // 2. Xây mộ
        for (GraveBlock block : graveStructure) {
            Location loc = baseLocation.clone().add(block.dx, block.dy, block.dz);
            Block currentBlock = loc.getBlock();

            if (block.material == Material.PLAYER_HEAD || "player-head".equals(block.data)) {
                currentBlock.setType(Material.PLAYER_HEAD);
                if (currentBlock.getState() instanceof org.bukkit.block.Skull skull) {
                    skull.setOwningPlayer(Bukkit.getOfflinePlayer(playerName));
                    skull.update();
                }
            } else {
                currentBlock.setType(block.material);
            }
        }

        // 3. Spawn ArmorStand hiển thị tên trên đỉnh bia
        spawnGraveNameDisplay(baseLocation, grave);
    }

    /**
     * Spawn ArmorStand vô hình hiển thị tên trên đỉnh bia mộ
     */
    private void spawnGraveNameDisplay(Location baseLocation, GraveData grave) {
        World world = baseLocation.getWorld();
        if (world == null) return;

        int highestY = Integer.MIN_VALUE;
        Integer centerDx = null, centerDz = null;
        for (GraveBlock block : graveStructure) {
            if (block.dy > highestY) {
                highestY = block.dy;
                centerDx = block.dx;
                centerDz = block.dz;
            }
        }
        if (centerDx == null) return;

        double standX = baseLocation.getBlockX() + centerDx + 0.5;
        double standY = baseLocation.getBlockY() + highestY + 1.5;
        double standZ = baseLocation.getBlockZ() + centerDz + 0.5;

        Location standLoc = new Location(world, standX, standY, standZ);
        ArmorStand stand = (ArmorStand) world.spawnEntity(standLoc, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setSmall(true);
        stand.setMarker(true);
        stand.setGravity(false);
        stand.setCanPickupItems(false);
        stand.setInvulnerable(true);
        stand.setCollidable(false);
        stand.customName(net.kyori.adventure.text.Component.text(""));
        stand.setCustomNameVisible(true);

        grave.armorStandUuid = stand.getUniqueId();
    }

    /**
     * Xóa ArmorStand hiển thị tên
     */
    private void removeGraveNameDisplay(GraveData grave) {
        if (grave.armorStandUuid == null) return;
        World world = Bukkit.getWorld(grave.worldName);
        if (world == null) return;

        for (org.bukkit.entity.Entity entity : world.getEntities()) {
            if (entity.getUniqueId().equals(grave.armorStandUuid)) {
                entity.remove();
                break;
            }
        }
    }

    /**
     * Khôi phục block cũ khi phá hủy mộ
     */
    public void destroyGraveStructure(Location baseLocation, Map<String, String> originalBlocks) {
        World world = baseLocation.getWorld();
        if (world == null) return;

        for (GraveBlock block : graveStructure) {
            Location loc = baseLocation.clone().add(block.dx, block.dy, block.dz);
            Block currentBlock = loc.getBlock();

            String key = block.dx + "_" + block.dy + "_" + block.dz;
            String oldBlockData = originalBlocks != null ? originalBlocks.get(key) : null;

            if (oldBlockData != null) {
                String[] parts = oldBlockData.split(";", 2);
                Material oldMaterial = Material.getMaterial(parts[0]);
                if (oldMaterial != null && oldMaterial != Material.AIR) {
                    currentBlock.setType(oldMaterial);
                    if (parts.length > 1) {
                        try {
                            BlockData bd = Bukkit.createBlockData(parts[1]);
                            currentBlock.setBlockData(bd);
                        } catch (Exception ignored) {}
                    }
                } else {
                    currentBlock.setType(Material.AIR);
                }
            } else {
                currentBlock.setType(Material.AIR);
            }
        }
    }

    // ===== INTERACT WITH GRAVE =====

    /**
     * Xử lý khi người chơi click vào head của mộ
     */
    public boolean interactGrave(Player player, Location headLocation) {
        if (!enabled) return false;

        GraveData grave = findGraveByHeadLocation(headLocation);
        if (grave == null) return false;

        if (grave.isEmpty) {
            player.sendMessage(emptyMessage);
            return true;
        }

        long now = System.currentTimeMillis();
        long age = now - grave.createdAt;
        long totalDurationMs = (protectionMinutes + publicMinutes) * 60 * 1000L;

        if (age >= totalDurationMs) {
            grave.isEmpty = true;
            removeGraveNameDisplay(grave);
            saveData();
            Location graveLocation = new Location(Bukkit.getWorld(grave.worldName), grave.x, grave.y, grave.z);
            destroyGraveStructure(graveLocation, grave.originalBlocks);
            player.sendMessage(emptyMessage);
            return true;
        }

        int currentDeaths = grave.deathCountAtCreation;
        Player ownerPlayer = Bukkit.getPlayer(grave.owner);
        if (ownerPlayer != null && ownerPlayer.isOnline()) {
            currentDeaths = plugin.getLogManager().getDeathCount(ownerPlayer);
        }
        if (currentDeaths - grave.deathCountAtCreation >= expireDeaths) {
            grave.isEmpty = true;
            removeGraveNameDisplay(grave);
            saveData();
            Location graveLocation = new Location(Bukkit.getWorld(grave.worldName), grave.x, grave.y, grave.z);
            destroyGraveStructure(graveLocation, grave.originalBlocks);
            player.sendMessage(emptyMessage);
            return true;
        }

        boolean isOwner = player.getUniqueId().equals(grave.owner);
        boolean inProtection = age < protectionMinutes * 60 * 1000L;
        boolean inPublic = !inProtection && age < (protectionMinutes + publicMinutes) * 60 * 1000L;

        if (inProtection && !isOwner) {
            long remaining = (protectionMinutes * 60 * 1000L - age) / 1000;
            player.sendMessage("§c§l⚠ Ngôi mộ này đang được bảo vệ! §7Chỉ §c" + grave.ownerName + " §7mới có thể lấy items.");
            player.sendMessage("§7Thời gian bảo vệ còn: §a§l" + formatTime(remaining));
            return true;
        }

        if (!inProtection && !inPublic) {
            grave.isEmpty = true;
            removeGraveNameDisplay(grave);
            saveData();
            Location graveLocation = new Location(Bukkit.getWorld(grave.worldName), grave.x, grave.y, grave.z);
            destroyGraveStructure(graveLocation, grave.originalBlocks);
            player.sendMessage(emptyMessage);
            return true;
        }

        retrieveItems(player, grave);
        return true;
    }

    /**
     * Trả items về cho người chơi
     */
    private void retrieveItems(Player player, GraveData grave) {
        int restored = 0;
        int dropped = 0;

        for (Map.Entry<Integer, ItemStack> entry : grave.items.entrySet()) {
            int slot = entry.getKey();
            ItemStack item = entry.getValue();

            if (slot < 36) {
                if (player.getInventory().getItem(slot) == null || 
                    player.getInventory().getItem(slot).getType() == Material.AIR) {
                    player.getInventory().setItem(slot, item);
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
            } else if (slot >= 36 && slot < 40) {
                int armorIndex = slot - 36;
                ItemStack[] armor = player.getInventory().getArmorContents();
                if (armor[armorIndex] == null || armor[armorIndex].getType() == Material.AIR) {
                    armor[armorIndex] = item;
                    player.getInventory().setArmorContents(armor);
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
            } else if (slot == 40) {
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
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                if (!leftover.isEmpty()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                    dropped++;
                } else {
                    restored++;
                }
            }
        }

        grave.isEmpty = true;
        removeGraveNameDisplay(grave);

        Location graveLocation = new Location(Bukkit.getWorld(grave.worldName), grave.x, grave.y, grave.z);
        destroyGraveStructure(graveLocation, grave.originalBlocks);
        saveData();

        String baseMsg = "§a§l✅ Đã lấy lại items từ mộ của §c" + grave.ownerName + "§a!";
        String detailMsg = "§7Khôi phục: §a" + restored + " §7item" + (dropped > 0 ? " §c(Rơi: " + dropped + ")" : "");
        player.sendMessage(baseMsg);
        player.sendMessage(detailMsg);

        logger.info("[Grave] " + player.getName() + " retrieved grave " + grave.graveId +
            " of " + grave.ownerName + " (" + restored + " restored, " + dropped + " dropped)");
    }

    // ===== FIND GRAVE =====

    /**
     * Tìm grave dựa trên vị trí block PLAYER_HEAD
     */
    public GraveData findGraveByHeadLocation(Location headLocation) {
        Integer headDx = null, headDy = null, headDz = null;
        for (GraveBlock block : graveStructure) {
            if (block.material == Material.PLAYER_HEAD || "player-head".equals(block.data)) {
                headDx = block.dx;
                headDy = block.dy;
                headDz = block.dz;
                break;
            }
        }
        if (headDx == null) return null;

        Location baseLoc = headLocation.clone().subtract(headDx, headDy, headDz);

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
     * Task cập nhật tên ArmorStand mỗi giây
     * Hiển thị R.I.P {name} - {time} với màu sắc tương ứng
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
                        grave.isEmpty = true;
                        removeGraveNameDisplay(grave);
                        Location graveLocation = new Location(
                            Bukkit.getWorld(grave.worldName), grave.x, grave.y, grave.z);
                        destroyGraveStructure(graveLocation, grave.originalBlocks);
                        saveData();
                        continue;
                    }

                    long remainingMs = totalDurationMs - age;
                    long remainingSeconds = remainingMs / 1000;
                    String timeStr = formatTime(remainingSeconds);

                    boolean inProtection = age < protectionMinutes * 60 * 1000L;
                    String timeColor = inProtection ? "§a§l" : "§b§l";

                    String displayName = displayFormat
                        .replace("{name}", "§4" + grave.ownerName + "§r")
                        .replace("{time}", timeColor + timeStr);

                    // Cập nhật tên ArmorStand
                    if (grave.armorStandUuid != null) {
                        World world = Bukkit.getWorld(grave.worldName);
                        if (world == null) continue;
                        for (org.bukkit.entity.Entity entity : world.getEntities()) {
                            if (entity.getUniqueId().equals(grave.armorStandUuid)) {
                                entity.customName(net.kyori.adventure.text.Component.text(displayName));
                                break;
                            }
                        }
                    }
                }
            }
        };
        displayTask.runTaskTimer(plugin, 40L, 20L);
    }

    private String formatTime(long seconds) {
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    // ===== DATA SAVE/LOAD =====

    private synchronized void saveData() {
        try {
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

                int itemIndex = 0;
                for (Map.Entry<Integer, ItemStack> entry : grave.items.entrySet()) {
                    String itemPath = path + ".items." + itemIndex;
                    data.set(itemPath + ".slot", entry.getKey());
                    data.set(itemPath + ".item", entry.getValue());
                    itemIndex++;
                }
                data.set(path + ".itemCount", grave.items.size());

                if (!grave.originalBlocks.isEmpty()) {
                    for (Map.Entry<String, String> blockEntry : grave.originalBlocks.entrySet()) {
                        data.set(path + ".originalBlocks." + blockEntry.getKey(), blockEntry.getValue());
                    }
                }

                if (grave.armorStandUuid != null) {
                    data.set(path + ".armorStandUuid", grave.armorStandUuid.toString());
                }
            }

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
            if (data.contains("_playerGraves")) {
                for (String uuidStr : data.getConfigurationSection("_playerGraves").getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        List<String> graveIds = data.getStringList("_playerGraves." + uuidStr);
                        playerGraves.put(uuid, new ArrayList<>(graveIds));
                    } catch (IllegalArgumentException ignored) {}
                }
            }

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

                        int itemCount = data.getInt("graves." + graveId + ".itemCount", 0);
                        for (int i = 0; i < itemCount; i++) {
                            String itemPath = "graves." + graveId + ".items." + i;
                            int slot = data.getInt(itemPath + ".slot", -1);
                            ItemStack item = data.getItemStack(itemPath + ".item");
                            if (slot >= 0 && item != null) {
                                grave.items.put(slot, item);
                            }
                        }

                        String blockPath = "graves." + graveId + ".originalBlocks";
                        if (data.contains(blockPath)) {
                            for (String key : data.getConfigurationSection(blockPath).getKeys(false)) {
                                String value = data.getString(blockPath + "." + key);
                                if (value != null) {
                                    grave.originalBlocks.put(key, value);
                                }
                            }
                        }

                        String armorUuid = data.getString("graves." + graveId + ".armorStandUuid");
                        if (armorUuid != null && !armorUuid.isEmpty()) {
                            grave.armorStandUuid = UUID.fromString(armorUuid);
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
        // Xóa tất cả ArmorStand khi tắt plugin
        for (GraveData grave : graves.values()) {
            removeGraveNameDisplay(grave);
        }
        saveData();
    }
}