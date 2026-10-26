package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
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
public class GraveManager implements Listener {

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
        final String facing;

        GraveBlock(int dx, int dy, int dz, Material material, String data, String facing) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.material = material;
            this.data = data;
            this.facing = facing;
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
        Set<String> structureKeys = new LinkedHashSet<>();
        String headBlockKey;
        String temporarySupportKey;
        // Lưu block cũ: key = "dx_dy_dz", value = "MATERIAL;BLOCKDATA"
        Map<String, String> originalBlocks = new HashMap<>();
        // Block grave đã đặt tại mỗi offset, dùng để tránh khôi phục đè lên thay đổi sau đó.
        Map<String, String> placedBlocks = new HashMap<>();
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
    public boolean waterSurfacePlacementEnabled;
    public Material waterSupportMaterial;

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
        Bukkit.getPluginManager().registerEvents(this, plugin);

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
        this.waterSurfacePlacementEnabled =
            plugin.getConfig().getBoolean("graves.water-surface-placement.enabled", true);
        String supportMaterial = plugin.getConfig().getString(
            "graves.water-surface-placement.support-material", "COBBLESTONE");
        Material configuredSupport = Material.matchMaterial(supportMaterial == null ? "" : supportMaterial);
        if (configuredSupport == null || !configuredSupport.isSolid()) {
            logger.warning("[Grave] Invalid water support material '" + supportMaterial
                + "'; using COBBLESTONE.");
            configuredSupport = Material.COBBLESTONE;
        }
        this.waterSupportMaterial = configuredSupport;
    }

    private void loadStructure() {
        graveStructure.clear();
        File structureFile = new File(plugin.getDataFolder(), "graves-structure.yml");
        if (!structureFile.exists()) {
            plugin.saveResource("graves-structure.yml", false);
        }
        YamlConfiguration structureConfig = YamlConfiguration.loadConfiguration(structureFile);
        mergeStructureDefaults(structureConfig, structureFile);

        boolean miniGraves = structureConfig.getBoolean("mini-graves", true);
        String selectedVariant = miniGraves ? "mini" : "full";
        org.bukkit.configuration.ConfigurationSection selectedStructure =
            structureConfig.getConfigurationSection(selectedVariant);
        List<Map<?, ?>> blocks;

        if (selectedStructure != null) {
            blocks = selectedStructure.getMapList("blocks");
        } else if (structureConfig.isList("blocks")) {
            logger.warning("[Grave] Missing '" + selectedVariant +
                "' structure in graves-structure.yml; loading legacy 'blocks' structure");
            blocks = structureConfig.getMapList("blocks");
        } else {
            logger.warning("[Grave] No '" + selectedVariant +
                "' structure found in graves-structure.yml, using fallback structure");
            addFallbackStructure();
            return;
        }

        for (Map<?, ?> blockMap : blocks) {
            int dx = getIntFromMap(blockMap, "dx", 0);
            int dy = getIntFromMap(blockMap, "dy", 0);
            int dz = getIntFromMap(blockMap, "dz", 0);
            Object materialValue = blockMap.get("material");
            String materialName = materialValue instanceof String ? (String) materialValue : "AIR";
            Object blockDataValue = blockMap.get("data");
            String blockData = blockDataValue instanceof String ? (String) blockDataValue : "";
            Object facingValue = blockMap.get("facing");
            String facing = facingValue instanceof String ? (String) facingValue : "";

            Material material = Material.getMaterial(materialName.toUpperCase(Locale.ROOT));
            if (material == null || material == Material.AIR) {
                logger.warning("[Grave] Invalid material '" + materialName + "' at dx=" + dx + ",dy=" + dy + ",dz=" + dz);
                continue;
            }
            graveStructure.add(new GraveBlock(dx, dy, dz, material, blockData, facing));
        }

        if (graveStructure.isEmpty()) {
            logger.warning("[Grave] No valid blocks in '" + selectedVariant +
                "' structure, using fallback structure");
            addFallbackStructure();
        }
        logger.info("[Grave] Using '" + selectedVariant + "' structure from graves-structure.yml (" +
            graveStructure.size() + " blocks)");
    }

    private void mergeStructureDefaults(YamlConfiguration target, File targetFile) {
        InputStream resource = plugin.getResource("graves-structure.yml");
        if (resource == null) {
            logger.warning("[Grave] Bundled graves-structure.yml is missing; cannot migrate structure config.");
            return;
        }

        YamlConfiguration defaults;
        try (InputStream input = resource;
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            defaults = YamlConfiguration.loadConfiguration(reader);
        } catch (IOException exception) {
            logger.warning("[Grave] Cannot read bundled grave structure defaults: " + exception.getMessage());
            return;
        }

        boolean changed = false;
        for (String key : List.of("mini-graves", "full", "mini")) {
            org.bukkit.configuration.ConfigurationSection defaultChild = defaults.getConfigurationSection(key);
            if (defaultChild != null) {
                org.bukkit.configuration.ConfigurationSection targetChild = target.getConfigurationSection(key);
                if (targetChild == null) {
                    if (target.contains(key)) continue;
                    targetChild = target.createSection(key);
                    changed = true;
                }
                changed |= mergeStructureKeys(targetChild, defaultChild);
            } else if (!target.contains(key)) {
                target.set(key, defaults.get(key));
                changed = true;
            }
        }
        changed |= fixFullMarkerHead(target);
        if (!changed) return;
        try {
            target.save(targetFile);
            logger.info("[Grave] Added missing full/mini grave structure settings; existing values were preserved.");
        } catch (IOException exception) {
            logger.severe("[Grave] Cannot save migrated graves-structure.yml: " + exception.getMessage());
        }
    }

    private boolean mergeStructureKeys(org.bukkit.configuration.ConfigurationSection target,
            org.bukkit.configuration.ConfigurationSection defaults) {
        boolean changed = false;
        for (String key : defaults.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection defaultChild = defaults.getConfigurationSection(key);
            if (defaultChild != null) {
                org.bukkit.configuration.ConfigurationSection targetChild = target.getConfigurationSection(key);
                if (targetChild == null) {
                    if (target.contains(key)) continue;
                    targetChild = target.createSection(key);
                    changed = true;
                }
                changed |= mergeStructureKeys(targetChild, defaultChild);
            } else if (!target.contains(key)) {
                target.set(key, defaults.get(key));
                changed = true;
            }
        }
        return changed;
    }

    private boolean fixFullMarkerHead(YamlConfiguration structureConfig) {
        List<Map<?, ?>> configuredBlocks = structureConfig.getMapList("full.blocks");
        if (configuredBlocks.isEmpty()) return false;

        List<Map<String, Object>> fixedBlocks = new ArrayList<>(configuredBlocks.size());
        boolean changed = false;
        for (Map<?, ?> configuredBlock : configuredBlocks) {
            Map<String, Object> block = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : configuredBlock.entrySet()) {
                if (entry.getKey() instanceof String key) block.put(key, entry.getValue());
            }

            Object material = block.get("material");
            Object marker = block.get("data");
            Object yValue = block.get("dy");
            Object zValue = block.get("dz");
            if (material instanceof String materialName && materialName.equalsIgnoreCase("PLAYER_HEAD") &&
                    "player-head".equals(marker) && yValue instanceof Number y && y.intValue() > 0 &&
                    zValue instanceof Number z && z.intValue() > 0) {
                block.put("material", "PLAYER_WALL_HEAD");
                block.put("facing", "NORTH");
                changed = true;
            }
            fixedBlocks.add(block);
        }

        for (Map<String, Object> block : new ArrayList<>(fixedBlocks)) {
            if (!"PLAYER_WALL_HEAD".equals(block.get("material")) ||
                    !"NORTH".equalsIgnoreCase(String.valueOf(block.get("facing")))) continue;
            Object dxValue = block.get("dx");
            Object dyValue = block.get("dy");
            Object dzValue = block.get("dz");
            if (!(dxValue instanceof Number dx) || !(dyValue instanceof Number dy) ||
                    !(dzValue instanceof Number dz)) continue;
            int supportZ = dz.intValue() + 1;
            boolean supportExists = fixedBlocks.stream().anyMatch(other ->
                other.get("dx") instanceof Number otherDx && otherDx.intValue() == dx.intValue() &&
                other.get("dy") instanceof Number otherDy && otherDy.intValue() == dy.intValue() &&
                other.get("dz") instanceof Number otherDz && otherDz.intValue() == supportZ);
            if (!supportExists) {
                Map<String, Object> support = new LinkedHashMap<>();
                support.put("dx", dx.intValue());
                support.put("dy", dy.intValue());
                support.put("dz", supportZ);
                support.put("material", "STONE_BRICKS");
                fixedBlocks.add(support);
                changed = true;
            }
        }

        if (changed) {
            structureConfig.set("full.blocks", fixedBlocks);
            logger.info("[Grave] Reoriented the full grave marker to an upright wall head facing the grave.");
        }
        return changed;
    }

    private int getIntFromMap(Map<?, ?> map, String key, int defaultValue) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        return defaultValue;
    }

    private void addFallbackStructure() {
        graveStructure.add(new GraveBlock(0, 0, 0, Material.COBBLESTONE, "", ""));
        graveStructure.add(new GraveBlock(0, 1, 0, Material.COBBLESTONE, "", ""));
        graveStructure.add(new GraveBlock(0, 1, 1, Material.PLAYER_HEAD, "player-head", ""));
        graveStructure.add(new GraveBlock(0, 2, 0, Material.COBBLESTONE, "", ""));
    }

    public void reload() {
        saveData();
        loadConfig();
        loadStructure();
        loadData();
    }

    // ===== CREATE GRAVE =====

    public boolean createGrave(Player player, Location deathLocation) {
        if (!enabled || deathLocation == null || deathLocation.getWorld() == null) return false;
        Location graveLocation = findGravePlacement(deathLocation);
        if (graveLocation == null) {
            logger.warning("[Grave] Cannot find a clear grave placement near " + deathLocation + "; allowing vanilla item drops.");
            return false;
        }

        GraveData grave = new GraveData();
        grave.owner = player.getUniqueId();
        grave.ownerName = player.getName();
        grave.worldName = graveLocation.getWorld().getName();
        grave.x = graveLocation.getBlockX();
        grave.y = graveLocation.getBlockY();
        grave.z = graveLocation.getBlockZ();
        grave.createdAt = System.currentTimeMillis();
        grave.deathCountAtCreation = plugin.getLogManager().getDeathCount(player);
        grave.isEmpty = false;
        if (waterSurfacePlacementEnabled && deathLocation.getBlock().isLiquid()) {
            int surfaceY = findWaterSurfaceY(deathLocation);
            if (surfaceY >= 0 && grave.y == surfaceY + 2) {
                Block support = graveLocation.clone().add(0, -2, 0).getBlock();
                if (!support.getType().isSolid()) grave.temporarySupportKey = "0_-2_0";
            }
        }

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

        buildGraveStructure(graveLocation, player.getName(), grave);

        graves.put(grave.graveId, grave);
        List<String> ownerGraves = playerGraves.computeIfAbsent(grave.owner, k -> new ArrayList<>());
        ownerGraves.add(grave.graveId);
        if (!saveData()) {
            graves.remove(grave.graveId);
            ownerGraves.remove(grave.graveId);
            data.set("graves." + grave.graveId, null);
            String ownerPath = "_playerGraves." + grave.owner;
            if (ownerGraves.isEmpty()) {
                playerGraves.remove(grave.owner);
                data.set(ownerPath, null);
            } else {
                data.set(ownerPath, ownerGraves);
            }
            removeGraveNameDisplay(grave);
            destroyGraveStructure(graveLocation, grave);
            saveData();
            logger.warning("[Grave] Could not persist grave for " + player.getName() + "; allowing vanilla item drops.");
            return false;
        }

        logger.info("[Grave] Created grave " + grave.graveId + " for " + player.getName() +
            " at " + grave.x + "," + grave.y + "," + grave.z + " (" + grave.items.size() + " items)");
        return true;
    }

    /**
     * Xây cấu trúc mộ tại vị trí.
     * Lưu block cũ, đặt block mộ, spawn ArmorStand hiển thị tên.
     */
    private void buildGraveStructure(Location baseLocation, String playerName, GraveData grave) {
        World world = baseLocation.getWorld();
        if (world == null) return;

        if (grave.temporarySupportKey != null) {
            int[] offset = parseOffset(grave.temporarySupportKey);
            if (offset != null) {
                Block support = baseLocation.clone().add(offset[0], offset[1], offset[2]).getBlock();
                grave.originalBlocks.put(grave.temporarySupportKey, support.getType() == Material.AIR ? "" :
                    support.getType().name() + ";" + support.getBlockData().getAsString());
                support.setType(waterSupportMaterial);
                grave.placedBlocks.put(grave.temporarySupportKey, serializeBlock(support));
            }
        }

        // 1. Lưu block cũ
        for (GraveBlock block : graveStructure) {
            Location loc = baseLocation.clone().add(block.dx, block.dy, block.dz);
            Block currentBlock = loc.getBlock();
            String key = block.dx + "_" + block.dy + "_" + block.dz;
            grave.structureKeys.add(key);
            if (isHeadBlock(block)) grave.headBlockKey = key;
            grave.originalBlocks.put(key, currentBlock.getType() == Material.AIR ? "" :
                currentBlock.getType().name() + ";" + currentBlock.getBlockData().getAsString());
        }

        // 2. Xây mộ
        List<GraveBlock> blocksToPlace = new ArrayList<>(graveStructure);
        boolean waterMiniGrave = grave.temporarySupportKey != null
            && graveStructure.stream().anyMatch(block -> block.material == Material.PLAYER_HEAD);
        if (waterMiniGrave) {
            blocksToPlace.sort(Comparator.comparing(block -> !isHeadBlock(block)));
        } else {
            blocksToPlace.sort(Comparator.comparing(block -> block.material == Material.PLAYER_WALL_HEAD));
        }
        for (GraveBlock block : blocksToPlace) {
            Location loc = baseLocation.clone().add(block.dx, block.dy, block.dz);
            Block currentBlock = loc.getBlock();
            String key = block.dx + "_" + block.dy + "_" + block.dz;

            if (isHeadBlock(block)) {
                Material headMaterial = block.material == Material.PLAYER_WALL_HEAD
                    ? Material.PLAYER_WALL_HEAD : Material.PLAYER_HEAD;
                currentBlock.setType(headMaterial);
                if (currentBlock.getState() instanceof org.bukkit.block.Skull skull) {
                    skull.setOwningPlayer(Bukkit.getOfflinePlayer(playerName));
                    skull.update();
                }
            } else {
                currentBlock.setType(block.material);
            }
            if (!block.facing.isBlank()) {
                try {
                    BlockData placedData = currentBlock.getBlockData();
                    if (placedData instanceof Directional directional) {
                        directional.setFacing(BlockFace.valueOf(block.facing.toUpperCase(Locale.ROOT)));
                        currentBlock.setBlockData(placedData);
                    }
                } catch (IllegalArgumentException exception) {
                    logger.warning("[Grave] Invalid facing '" + block.facing + "' for " + block.material);
                }
            }
            grave.placedBlocks.put(key, serializeBlock(currentBlock));
        }
        if (grave.temporarySupportKey != null) grave.structureKeys.add(grave.temporarySupportKey);

        // 3. Spawn ArmorStand hiển thị tên trên đỉnh bia
        spawnGraveNameDisplay(baseLocation, grave);
    }

    private static boolean isHeadBlock(GraveBlock block) {
        return block.material == Material.PLAYER_HEAD || block.material == Material.PLAYER_WALL_HEAD ||
            "player-head".equals(block.data);
    }

    private Location findGravePlacement(Location deathLocation) {
        World world = deathLocation.getWorld();
        if (world == null) return null;

        int baseX = deathLocation.getBlockX();
        int baseY = deathLocation.getBlockY();
        int baseZ = deathLocation.getBlockZ();
        Set<String> occupied = getOccupiedGraveBlocks(world.getName());
        if (waterSurfacePlacementEnabled && deathLocation.getBlock().isLiquid()) {
            int surfaceY = findWaterSurfaceY(deathLocation);
            int waterBaseY = surfaceY + 2;
            if (surfaceY >= 0 && waterBaseY < world.getMaxHeight()) {
                baseY = waterBaseY;
                Location waterCandidate = new Location(world, baseX, waterBaseY, baseZ);
                String supportPosition = baseX + ":" + surfaceY + ":" + baseZ;
                Block support = world.getBlockAt(baseX, surfaceY, baseZ);
                if (!occupied.contains(supportPosition) && !support.getType().isSolid()
                        && !wouldOverlapGrave(waterCandidate, occupied)) return waterCandidate;
            }
        }
        if (!wouldOverlapGrave(new Location(world, baseX, baseY, baseZ), occupied)) {
            return new Location(world, baseX, baseY, baseZ);
        }

        int[] verticalOffsets = {0, 1, -1, 2, -2, 3, -3};
        for (int radius = 1; radius <= 16; radius++) {
            List<int[]> offsets = new ArrayList<>();
            offsets.add(new int[] {radius, 0});
            offsets.add(new int[] {-radius, 0});
            offsets.add(new int[] {0, radius});
            offsets.add(new int[] {0, -radius});
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    if ((Math.abs(dx) == radius && dz == 0) ||
                        (Math.abs(dz) == radius && dx == 0)) {
                        continue;
                    }
                    offsets.add(new int[] {dx, dz});
                }
            }
            for (int[] offset : offsets) {
                int x = baseX + offset[0];
                int z = baseZ + offset[1];
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                for (int verticalOffset : verticalOffsets) {
                    int y = baseY + verticalOffset;
                    if (y <= world.getMinHeight() || y >= world.getMaxHeight()) continue;
                    Location candidate = new Location(world, x, y, z);
                    if (waterSurfacePlacementEnabled && deathLocation.getBlock().isLiquid() && y == baseY
                            && occupied.contains(x + ":" + (y - 2) + ":" + z)) continue;
                    if (!wouldOverlapGrave(candidate, occupied)) return candidate;
                }
            }
        }
        return null;
    }

    private int findWaterSurfaceY(Location deathLocation) {
        World world = deathLocation.getWorld();
        if (world == null || !deathLocation.getBlock().isLiquid()) return -1;
        int x = deathLocation.getBlockX();
        int y = deathLocation.getBlockY();
        int z = deathLocation.getBlockZ();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return -1;
        while (y < world.getMaxHeight() - 1) {
            Block block = world.getBlockAt(x, y, z);
            if (!block.isLiquid()) return -1;
            Block above = world.getBlockAt(x, y + 1, z);
            if (!above.isLiquid()) {
                return above.getType().isAir() || above.isReplaceable() ? y : -1;
            }
            y++;
        }
        return -1;
    }

    private Set<String> getOccupiedGraveBlocks(String worldName) {
        Set<String> occupied = new HashSet<>();
        for (GraveData grave : graves.values()) {
            if (grave.isEmpty || !grave.worldName.equals(worldName)) continue;
            for (String key : getStructureKeys(grave)) {
                int[] offset = parseOffset(key);
                if (offset != null) {
                    occupied.add((grave.x + offset[0]) + ":" + (grave.y + offset[1]) + ":" + (grave.z + offset[2]));
                }
            }
        }
        return occupied;
    }

    private boolean wouldOverlapGrave(Location baseLocation, Set<String> occupied) {
        World world = baseLocation.getWorld();
        for (GraveBlock block : graveStructure) {
            int x = baseLocation.getBlockX() + block.dx;
            int y = baseLocation.getBlockY() + block.dy;
            int z = baseLocation.getBlockZ() + block.dz;
            if (occupied.contains(x + ":" + y + ":" + z)) return true;
            Block existingBlock = world.getBlockAt(x, y, z);
            if (existingBlock.getState() instanceof org.bukkit.block.TileState) return true;
            if ((block.dy > 0 || !block.material.isSolid()) &&
                !existingBlock.getType().isAir() && !existingBlock.isReplaceable()) return true;
        }
        return false;
    }

    /**
     * Spawn ArmorStand vô hình hiển thị tên trên đỉnh bia mộ
     */
    private void spawnGraveNameDisplay(Location baseLocation, GraveData grave) {
        World world = baseLocation.getWorld();
        if (world == null) return;

        int[] displayOffset = getDisplayOffset(grave);
        if (displayOffset == null) return;

        double standX = baseLocation.getBlockX() + displayOffset[0] + 0.5;
        double standY = baseLocation.getBlockY() + displayOffset[1] + 1.5;
        double standZ = baseLocation.getBlockZ() + displayOffset[2] + 0.5;

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
        int[] displayOffset = getDisplayOffset(grave);
        if (world != null && displayOffset != null) {
            int chunkX = (grave.x + displayOffset[0]) >> 4;
            int chunkZ = (grave.z + displayOffset[2]) >> 4;
            for (org.bukkit.entity.Entity entity : world.getChunkAt(chunkX, chunkZ).getEntities()) {
                if (entity.getUniqueId().equals(grave.armorStandUuid)) {
                    entity.remove();
                    break;
                }
            }
        }
        grave.armorStandUuid = null;
        data.set("graves." + grave.graveId + ".armorStandUuid", null);
    }

    private int[] getDisplayOffset(GraveData grave) {
        String highestKey = null;
        int highestY = Integer.MIN_VALUE;
        for (String key : getStructureKeys(grave)) {
            int[] offset = parseOffset(key);
            if (offset != null && offset[1] > highestY) {
                highestY = offset[1];
                highestKey = key;
            }
        }
        return highestKey == null ? null : parseOffset(highestKey);
    }

    private Set<String> getStructureKeys(GraveData grave) {
        if (!grave.structureKeys.isEmpty()) return grave.structureKeys;
        Set<String> keys = new LinkedHashSet<>(grave.originalBlocks.keySet());
        for (GraveBlock block : graveStructure) {
            keys.add(block.dx + "_" + block.dy + "_" + block.dz);
        }
        return keys;
    }

    private int[] parseOffset(String key) {
        try {
            String[] parts = key.split("_", 3);
            if (parts.length != 3) return null;
            return new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Khôi phục block cũ khi phá hủy mộ
     */
    private void destroyGraveStructure(Location baseLocation, GraveData grave) {
        World world = baseLocation.getWorld();
        if (world == null) return;

        for (String key : getStructureKeys(grave)) {
            int[] offset = parseOffset(key);
            if (offset == null) continue;
            int x = baseLocation.getBlockX() + offset[0];
            int y = baseLocation.getBlockY() + offset[1];
            int z = baseLocation.getBlockZ() + offset[2];
            if (isOccupiedByAnotherGrave(grave, x, y, z)) continue;
            Block currentBlock = world.getBlockAt(x, y, z);
            if (!isStillGraveBlock(grave, key, currentBlock)) continue;
            String oldBlockData = grave.originalBlocks.get(key);

            if (oldBlockData != null && !oldBlockData.isEmpty()) {
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

    private boolean isOccupiedByAnotherGrave(GraveData restoringGrave, int x, int y, int z) {
        for (GraveData other : graves.values()) {
            if (other.graveId.equals(restoringGrave.graveId) || other.isEmpty ||
                !other.worldName.equals(restoringGrave.worldName)) continue;
            for (String key : getStructureKeys(other)) {
                int[] offset = parseOffset(key);
                if (offset != null && other.x + offset[0] == x &&
                    other.y + offset[1] == y && other.z + offset[2] == z) return true;
            }
        }
        return false;
    }

    private boolean isStillGraveBlock(GraveData grave, String key, Block block) {
        String expected = grave.placedBlocks.get(key);
        if (expected != null) {
            String[] parts = expected.split(";", 2);
            if (!block.getType().name().equals(parts[0])) return false;
            return parts.length < 2 || block.getBlockData().getAsString().equals(parts[1]);
        }

        // Older grave saves do not have placedBlocks; use the configured shape when possible.
        int[] offset = parseOffset(key);
        if (offset != null) {
            for (GraveBlock structureBlock : graveStructure) {
                if (structureBlock.dx == offset[0] && structureBlock.dy == offset[1] &&
                    structureBlock.dz == offset[2]) {
                    if (block.getType() == structureBlock.material) return true;
                }
            }
        }
        if (key.equals(grave.headBlockKey)) {
            return block.getType() == Material.PLAYER_HEAD || block.getType() == Material.PLAYER_WALL_HEAD;
        }
        // Keep legacy mini graves restorable after their configured footprint changes.
        return switch (block.getType()) {
            case DIRT, OXEYE_DAISY, TORCH, STONE_BRICK_WALL, STONE_BRICK_SLAB, COBBLESTONE -> true;
            default -> false;
        };
    }

    private String serializeBlock(Block block) {
        return block.getType().name() + ";" + block.getBlockData().getAsString();
    }

    private void expireGrave(GraveData grave, World world) {
        grave.isEmpty = true;
        grave.items.clear();
        removeGraveNameDisplay(grave);
        if (world != null) {
            destroyGraveStructure(new Location(world, grave.x, grave.y, grave.z), grave);
        }
        graves.remove(grave.graveId);
        List<String> ownerGraves = playerGraves.get(grave.owner);
        if (ownerGraves != null) {
            ownerGraves.remove(grave.graveId);
            if (ownerGraves.isEmpty()) playerGraves.remove(grave.owner);
        }
        data.set("graves." + grave.graveId, null);
        saveData();
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
            expireGrave(grave, headLocation.getWorld());
            player.sendMessage(emptyMessage);
            return true;
        }

        if (hasReachedDeathLimit(grave)) {
            expireGrave(grave, headLocation.getWorld());
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
            expireGrave(grave, headLocation.getWorld());
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
                        for (ItemStack leftoverItem : leftover.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), leftoverItem);
                        }
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
                        for (ItemStack leftoverItem : leftover.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), leftoverItem);
                        }
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
                        for (ItemStack leftoverItem : leftover.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), leftoverItem);
                        }
                        dropped++;
                    } else {
                        restored++;
                    }
                }
            } else {
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                if (!leftover.isEmpty()) {
                    for (ItemStack leftoverItem : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), leftoverItem);
                    }
                    dropped++;
                } else {
                    restored++;
                }
            }
        }

        World graveWorld = Bukkit.getWorld(grave.worldName);
        expireGrave(grave, graveWorld);

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
        for (GraveData grave : graves.values()) {
            int[] headOffset = parseOffset(grave.headBlockKey);
            if (headOffset == null || headLocation.getWorld() == null) continue;
            Location baseLoc = headLocation.clone().subtract(headOffset[0], headOffset[1], headOffset[2]);
            if (grave.x == baseLoc.getBlockX() && grave.y == baseLoc.getBlockY() &&
                grave.z == baseLoc.getBlockZ() && grave.worldName.equals(baseLoc.getWorld().getName())) {
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

    public List<GraveData> getRecentPlayerGraves(UUID playerUUID, int limit) {
        return getPlayerGraves(playerUUID).stream().filter(grave -> !grave.isEmpty && !grave.items.isEmpty())
            .sorted(java.util.Comparator.comparingLong((GraveData grave) -> grave.createdAt).reversed())
            .limit(Math.max(0, Math.min(10, limit))).toList();
    }

    /** Queue a selected death inventory once, then remove its grave. Delivery IDs are stable across retries. */
    public void queueAdminRestore(UUID ownerId, String graveId, java.util.function.Consumer<Boolean> completion) {
        GraveData grave = getPlayerGraves(ownerId).stream()
            .filter(candidate -> candidate.graveId.equals(graveId)).findFirst().orElse(null);
        if (grave == null || grave.isEmpty || grave.items.isEmpty()) {
            completion.accept(false);
            return;
        }
        List<byte[]> items = grave.items.values().stream().map(ItemStack::serializeAsBytes).toList();
        PlayerProfileDatabaseManager database = plugin.getPlayerProfileDatabaseManager();
        database.executeGameplayDatabaseTask("grave.admin-restore owner=" + ownerId + " grave=" + graveId, () -> {
            database.enqueueMerchantDeliveries(ownerId.toString(), "admin-grave-restore:" + graveId,
                items, java.time.Instant.now().toString());
            return true;
        }).whenComplete((queued, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (failure != null) {
                logger.log(Level.SEVERE, "[Grave][Admin] Could not queue death inventory restore for grave "
                    + graveId + ".", failure);
                completion.accept(false);
                return;
            }
            World world = Bukkit.getWorld(grave.worldName);
            expireGrave(grave, world);
            logger.info("[Grave][Admin] Queued restore owner=" + ownerId + ", grave=" + graveId
                + ", items=" + items.size());
            completion.accept(true);
        }));
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
                long now = System.currentTimeMillis();

                for (GraveData grave : new ArrayList<>(graves.values())) {
                    World graveWorld = Bukkit.getWorld(grave.worldName);
                    if (graveWorld == null) continue;

                    if (grave.isEmpty) {
                        expireGrave(grave, graveWorld);
                        continue;
                    }

                    if (hasReachedDeathLimit(grave)) {
                        expireGrave(grave, graveWorld);
                        continue;
                    }

                    long age = now - grave.createdAt;
                    long totalDurationMs = (protectionMinutes + publicMinutes) * 60 * 1000L;

                    if (age >= totalDurationMs) {
                        expireGrave(grave, graveWorld);
                        continue;
                    }
                    if (!enabled) continue;

                    long remainingMs = totalDurationMs - age;
                    long remainingSeconds = remainingMs / 1000;
                    String timeStr = formatTime(remainingSeconds);

                    boolean inProtection = age < protectionMinutes * 60 * 1000L;
                    String timeColor = inProtection ? "§a§l" : "§b§l";

                    String displayName = displayFormat
                        .replace("{name}", "§4" + grave.ownerName + "§r")
                        .replace("{time}", timeColor + timeStr);

                    int[] displayOffset = getDisplayOffset(grave);
                    if (displayOffset == null) continue;
                    int displayChunkX = (grave.x + displayOffset[0]) >> 4;
                    int displayChunkZ = (grave.z + displayOffset[2]) >> 4;
                    if (!graveWorld.isChunkLoaded(displayChunkX, displayChunkZ)) continue;

                    org.bukkit.entity.Entity display = grave.armorStandUuid == null
                        ? null : Bukkit.getEntity(grave.armorStandUuid);
                    if (display instanceof ArmorStand && display.getWorld().equals(graveWorld)) {
                        display.customName(net.kyori.adventure.text.Component.text(displayName));
                    } else {
                        grave.armorStandUuid = null;
                        spawnGraveNameDisplay(new Location(graveWorld, grave.x, grave.y, grave.z), grave);
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

    private boolean hasReachedDeathLimit(GraveData grave) {
        return expireDeaths > 0 &&
            plugin.getLogManager().getDeathCount(grave.owner) - grave.deathCountAtCreation >= expireDeaths;
    }

    private boolean isExpired(GraveData grave, long now) {
        long totalDurationMs = (protectionMinutes + publicMinutes) * 60 * 1000L;
        return grave.isEmpty || hasReachedDeathLimit(grave) || now - grave.createdAt >= totalDurationMs;
    }

    private void cleanupExpiredGraves(World world) {
        long now = System.currentTimeMillis();
        for (GraveData grave : new ArrayList<>(graves.values())) {
            if (grave.worldName.equals(world.getName()) && isExpired(grave, now)) {
                expireGrave(grave, world);
            }
        }
    }

    @EventHandler
    public void onWorldLoad(org.bukkit.event.world.WorldLoadEvent event) {
        cleanupExpiredGraves(event.getWorld());
    }

    // ===== DATA SAVE/LOAD =====

    private synchronized boolean saveData() {
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
                data.set(path + ".structureKeys", new ArrayList<>(grave.structureKeys));
                data.set(path + ".headBlockKey", grave.headBlockKey);

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
                if (!grave.placedBlocks.isEmpty()) {
                    for (Map.Entry<String, String> blockEntry : grave.placedBlocks.entrySet()) {
                        data.set(path + ".placedBlocks." + blockEntry.getKey(), blockEntry.getValue());
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
            return true;
        } catch (Exception e) {
            logger.warning("[Grave] Failed to save data: " + e.getMessage());
            return false;
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

                        String placedBlockPath = "graves." + graveId + ".placedBlocks";
                        if (data.contains(placedBlockPath)) {
                            for (String key : data.getConfigurationSection(placedBlockPath).getKeys(false)) {
                                String value = data.getString(placedBlockPath + "." + key);
                                if (value != null) {
                                    grave.placedBlocks.put(key, value);
                                }
                            }
                        }

                        grave.structureKeys.addAll(data.getStringList("graves." + graveId + ".structureKeys"));
                        if (grave.structureKeys.isEmpty()) {
                            grave.structureKeys.addAll(grave.originalBlocks.keySet());
                            for (GraveBlock block : graveStructure) {
                                grave.structureKeys.add(block.dx + "_" + block.dy + "_" + block.dz);
                            }
                        }
                        grave.headBlockKey = data.getString("graves." + graveId + ".headBlockKey");
                        if (grave.headBlockKey == null) {
                            for (GraveBlock block : graveStructure) {
                                if (block.material == Material.PLAYER_HEAD ||
                                        block.material == Material.PLAYER_WALL_HEAD ||
                                        "player-head".equals(block.data)) {
                                    grave.headBlockKey = block.dx + "_" + block.dy + "_" + block.dz;
                                    break;
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
        saveData();
    }
}
