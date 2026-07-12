package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class DisasterManager {

    private final VnMineHardcore plugin;
    private final LogManager logManager;
    private final Random random = ThreadLocalRandom.current();
    private final ConfigManager config;

    private boolean disasterActive = false;
    private String currentDisaster = null;
    private String currentDisasterId = null;
    private BossBar warningBar;
    private int warningTimeLeft = 0;
    private int timeSinceLastDisaster = 0;

    // Block materials considered as "transparent" - không được coi là mái che an toàn
    private static final Set<Material> TRANSPARENT_BLOCKS = new HashSet<>(Arrays.asList(
        Material.AIR, Material.WATER, Material.LAVA, Material.BUBBLE_COLUMN,
        Material.GLASS, Material.WHITE_STAINED_GLASS, Material.ORANGE_STAINED_GLASS,
        Material.MAGENTA_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS,
        Material.YELLOW_STAINED_GLASS, Material.LIME_STAINED_GLASS,
        Material.PINK_STAINED_GLASS, Material.GRAY_STAINED_GLASS,
        Material.LIGHT_GRAY_STAINED_GLASS, Material.CYAN_STAINED_GLASS,
        Material.PURPLE_STAINED_GLASS, Material.BLUE_STAINED_GLASS,
        Material.BROWN_STAINED_GLASS, Material.GREEN_STAINED_GLASS,
        Material.RED_STAINED_GLASS, Material.BLACK_STAINED_GLASS,
        Material.TINTED_GLASS, Material.GLASS_PANE,
        Material.WHITE_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE,
        Material.MAGENTA_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE,
        Material.YELLOW_STAINED_GLASS_PANE, Material.LIME_STAINED_GLASS_PANE,
        Material.PINK_STAINED_GLASS_PANE, Material.GRAY_STAINED_GLASS_PANE,
        Material.LIGHT_GRAY_STAINED_GLASS_PANE, Material.CYAN_STAINED_GLASS_PANE,
        Material.PURPLE_STAINED_GLASS_PANE, Material.BLUE_STAINED_GLASS_PANE,
        Material.BROWN_STAINED_GLASS_PANE, Material.GREEN_STAINED_GLASS_PANE,
        Material.RED_STAINED_GLASS_PANE, Material.BLACK_STAINED_GLASS_PANE,
        Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES,
        Material.JUNGLE_LEAVES, Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES,
        Material.MANGROVE_LEAVES, Material.CHERRY_LEAVES,
        Material.AZALEA_LEAVES, Material.FLOWERING_AZALEA_LEAVES,
        Material.OAK_SLAB, Material.SPRUCE_SLAB, Material.BIRCH_SLAB,
        Material.JUNGLE_SLAB, Material.ACACIA_SLAB, Material.DARK_OAK_SLAB,
        Material.MANGROVE_SLAB, Material.CHERRY_SLAB,
        Material.OAK_STAIRS, Material.SPRUCE_STAIRS, Material.BIRCH_STAIRS,
        Material.JUNGLE_STAIRS, Material.ACACIA_STAIRS, Material.DARK_OAK_STAIRS,
        Material.MANGROVE_STAIRS, Material.CHERRY_STAIRS,
        Material.OAK_FENCE, Material.SPRUCE_FENCE, Material.BIRCH_FENCE,
        Material.JUNGLE_FENCE, Material.ACACIA_FENCE, Material.DARK_OAK_FENCE,
        Material.MANGROVE_FENCE, Material.CHERRY_FENCE,
        Material.OAK_FENCE_GATE, Material.SPRUCE_FENCE_GATE, Material.BIRCH_FENCE_GATE,
        Material.JUNGLE_FENCE_GATE, Material.ACACIA_FENCE_GATE, Material.DARK_OAK_FENCE_GATE,
        Material.MANGROVE_FENCE_GATE, Material.CHERRY_FENCE_GATE,
        Material.OAK_TRAPDOOR, Material.SPRUCE_TRAPDOOR, Material.BIRCH_TRAPDOOR,
        Material.JUNGLE_TRAPDOOR, Material.ACACIA_TRAPDOOR, Material.DARK_OAK_TRAPDOOR,
        Material.MANGROVE_TRAPDOOR, Material.CHERRY_TRAPDOOR,
        Material.VINE, Material.LADDER, Material.SCAFFOLDING,
        Material.IRON_BARS, Material.COBWEB, Material.TORCH, Material.SOUL_TORCH,
        Material.REDSTONE_TORCH, Material.LANTERN, Material.SOUL_LANTERN,
        Material.END_ROD, Material.SHORT_GRASS, Material.TALL_GRASS,
        Material.SNOW, Material.CHORUS_PLANT, Material.CHORUS_FLOWER,
        Material.COBWEB, Material.BAMBOO, Material.BAMBOO_SAPLING,
        Material.SUGAR_CANE, Material.CACTUS, Material.KELP, Material.KELP_PLANT,
        Material.SEAGRASS, Material.TALL_SEAGRASS, Material.LILY_PAD,
        Material.BIG_DRIPLEAF, Material.SMALL_DRIPLEAF, Material.HANGING_ROOTS,
        Material.ROOTED_DIRT, Material.MOSS_CARPET, Material.AZALEA,
        Material.FLOWERING_AZALEA, Material.BROWN_MUSHROOM, Material.RED_MUSHROOM,
        Material.CRIMSON_FUNGUS, Material.WARPED_FUNGUS, Material.CRIMSON_ROOTS,
        Material.WARPED_ROOTS, Material.NETHER_SPROUTS, Material.TWISTING_VINES,
        Material.TWISTING_VINES_PLANT, Material.WEEPING_VINES, Material.WEEPING_VINES_PLANT,
        Material.DEAD_BUSH, Material.FERN, Material.LARGE_FERN,
        Material.DANDELION, Material.POPPY, Material.BLUE_ORCHID, Material.ALLIUM,
        Material.AZURE_BLUET, Material.OXEYE_DAISY, Material.CORNFLOWER,
        Material.LILY_OF_THE_VALLEY, Material.WITHER_ROSE, Material.SUNFLOWER,
        Material.LILAC, Material.PEONY, Material.ROSE_BUSH, Material.PITCHER_PLANT,
        Material.TORCHFLOWER, Material.SPORE_BLOSSOM,
        Material.BELL, Material.CHAIN, Material.LIGHTNING_ROD,
        Material.DAYLIGHT_DETECTOR, Material.COMPARATOR, Material.REPEATER,
        Material.REDSTONE_WIRE, Material.REDSTONE_TORCH, Material.LEVER,
        Material.TRIPWIRE, Material.TRIPWIRE_HOOK, Material.PAINTING,
        Material.ITEM_FRAME, Material.GLOW_ITEM_FRAME, Material.FLOWER_POT,
        Material.DECORATED_POT, Material.BREWING_STAND, Material.CAULDRON,
        Material.WATER_CAULDRON, Material.LAVA_CAULDRON, Material.POWDER_SNOW_CAULDRON,
        Material.END_PORTAL, Material.END_GATEWAY, Material.NETHER_PORTAL,
        Material.LECTERN, Material.COMPOSTER, Material.HOPPER,
        Material.SNIFFER_EGG, Material.TURTLE_EGG, Material.FROGSPAWN,
        Material.CANDLE, Material.WHITE_CANDLE, Material.ORANGE_CANDLE,
        Material.MAGENTA_CANDLE, Material.LIGHT_BLUE_CANDLE, Material.YELLOW_CANDLE,
        Material.LIME_CANDLE, Material.PINK_CANDLE, Material.GRAY_CANDLE,
        Material.LIGHT_GRAY_CANDLE, Material.CYAN_CANDLE, Material.PURPLE_CANDLE,
        Material.BLUE_CANDLE, Material.BROWN_CANDLE, Material.GREEN_CANDLE,
        Material.RED_CANDLE, Material.BLACK_CANDLE, Material.CAKE,
        Material.AMETHYST_CLUSTER, Material.LARGE_AMETHYST_BUD,
        Material.MEDIUM_AMETHYST_BUD, Material.SMALL_AMETHYST_BUD,
        Material.POINTED_DRIPSTONE, Material.BIG_DRIPLEAF_STEM,
        Material.POWDER_SNOW, Material.BUBBLE_COLUMN
    ));

    public DisasterManager(VnMineHardcore plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.logManager = plugin.getLogManager();
        this.warningBar = BossBar.bossBar(
            Component.text(""), 1.0f, BossBar.Color.RED, BossBar.Overlay.PROGRESS
        );
        if (config.disastersEnabled) start();
    }

    public Set<String> getDisasterIds() {
        return config.disasterConfigs.keySet();
    }

    public String getDisasterName(String id) {
        ConfigManager.DisasterConfig dc = config.disasterConfigs.get(id.toLowerCase());
        if (dc != null && dc.name != null && !dc.name.isEmpty()) {
            return dc.name;
        }
        return id;
    }

    // ===== SAFE ZONE =====

    public boolean isPlayerSafe(Player player) {
        if (!config.safeZoneEnabled) return false;
        
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return false;
        
        int playerX = loc.getBlockX();
        int playerY = loc.getBlockY();
        int playerZ = loc.getBlockZ();
        
        int checkRadius = config.safeZoneCheckRadius;
        
        // Kiểm tra block ngay trên đầu player (từ đầu đến trần)
        boolean hasSolidRoof = false;
        for (int y = playerY + 1; y <= playerY + config.safeZoneRoofHeight; y++) {
            Block blockAbove = world.getBlockAt(playerX, y, playerZ);
            if (!TRANSPARENT_BLOCKS.contains(blockAbove.getType())) {
                hasSolidRoof = true;
                break;
            }
        }
        
        if (!hasSolidRoof) return false;
        
        // Nếu bật kiểm tra xung quanh, kiểm tra có ít nhất 2 block rắn xung quanh
        if (config.safeZoneCheckWalls) {
            int solidWalls = 0;
            for (int dx = -checkRadius; dx <= checkRadius; dx++) {
                for (int dz = -checkRadius; dz <= checkRadius; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    Block sideBlock = world.getBlockAt(playerX + dx, playerY, playerZ + dz);
                    if (!TRANSPARENT_BLOCKS.contains(sideBlock.getType())) {
                        solidWalls++;
                    }
                }
            }
            return solidWalls >= config.safeZoneMinWalls;
        }
        
        return true;
    }

    private void sendSafeZoneMessage(Player player) {
        if (!config.safeZoneEnabled) return;
        if (isPlayerSafe(player)) {
            player.sendActionBar("§a§l🏠 Bạn đang an toàn trong nhà!");
        } else if (disasterActive) {
            player.sendActionBar("§c§l⚠ Bạn đang ở ngoài trời! Vào nhà ngay!");
        }
    }

    // ===== TRIGGER (MANUAL) =====

    public boolean triggerDisaster(String disasterId, int warningTimeSeconds, int durationSeconds) {
        if (disasterActive) return false;
        if (currentDisaster != null) return false;

        ConfigManager.DisasterConfig dc = config.disasterConfigs.get(disasterId.toLowerCase());
        if (dc == null) return false;

        String name = dc.name != null && !dc.name.isEmpty() ? dc.name : disasterId;
        currentDisaster = name;
        currentDisasterId = disasterId;
        warningTimeLeft = warningTimeSeconds;

        broadcastWarning(name, warningTimeSeconds);

        new BukkitRunnable() {
            int cd = warningTimeSeconds;
            @Override
            public void run() {
                cd--;
                if (cd <= 0) {
                    this.cancel();
                    startDisasterById(disasterId, durationSeconds);
                    return;
                }
                if (cd <= 5 || cd == 10 || cd == 30) {
                    broadcastCountdown(name, cd);
                }
                if (config.safeZoneEnabled && cd % 5 == 0) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        sendSafeZoneMessage(p);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
        return true;
    }

    // ===== SCHEDULER =====

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (Bukkit.getWorlds().isEmpty()) return;
                timeSinceLastDisaster++;
                int currentInterval = ConfigManager.parseRangeOrInt(config.disasterMinIntervalRaw, 1200);
                if (!disasterActive && timeSinceLastDisaster >= currentInterval) {
                    tryScheduleDisaster();
                }
                if (warningTimeLeft > 0) {
                    warningTimeLeft--;
                    updateWarningBar();
                    if (warningTimeLeft <= 0) {
                        warningBar.name(Component.text(""));
                        warningBar.progress(0);
                        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(warningBar);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void stop() {
        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(warningBar);
    }

    public boolean isDisasterActive() {
        return disasterActive;
    }

    public String getCurrentDisaster() {
        return currentDisaster;
    }

    private void tryScheduleDisaster() {
        int currentInterval = ConfigManager.parseRangeOrInt(config.disasterMinIntervalRaw, 1200);
        if (timeSinceLastDisaster < currentInterval) return;
        if (currentDisaster != null) return;

        // Collect enabled disasters from config (dynamic!)
        List<Map.Entry<String, ConfigManager.DisasterConfig>> enabledDisasters = new ArrayList<>();
        for (Map.Entry<String, ConfigManager.DisasterConfig> entry : config.disasterConfigs.entrySet()) {
            ConfigManager.DisasterConfig dc = entry.getValue();
            if (!dc.enabled) continue;
            if (dc.actions.isEmpty()) continue; // Bỏ qua disaster không có actions
            enabledDisasters.add(entry);
        }

        if (enabledDisasters.isEmpty()) return;

        // Tính tổng chance
        int totalChance = 0;
        for (Map.Entry<String, ConfigManager.DisasterConfig> entry : enabledDisasters) {
            totalChance += entry.getValue().chance;
        }
        if (totalChance <= 0) return;

        // Weighted random
        int roll = random.nextInt(totalChance);
        int cumulative = 0;
        for (Map.Entry<String, ConfigManager.DisasterConfig> entry : enabledDisasters) {
            String disasterId = entry.getKey();
            ConfigManager.DisasterConfig dc = entry.getValue();
            cumulative += dc.chance;
            if (roll < cumulative) {
                // Kiểm tra conditions (world, time)
                if (!canDisasterHappen(disasterId, dc)) continue;
                
                timeSinceLastDisaster = 0;
                scheduleDisaster(disasterId, dc);
                return;
            }
        }
    }

    /**
     * Kiểm tra điều kiện disaster có thể xảy ra không
     */
    private boolean canDisasterHappen(String disasterId, ConfigManager.DisasterConfig dc) {
        // Kiểm tra world conditions từ file YAML
        // Đọc file disaster để lấy conditions (tạm thời dùng logic cũ)
        // Conditions: only-night, only-day, worlds
        boolean isNight = Bukkit.getWorlds().get(0).getTime() > 13000;
        
        // Kiểm tra có player trong dimension phù hợp không
        boolean hasNether = false, hasEnd = false, hasOverworld = false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            String env = p.getWorld().getEnvironment().name();
            if ("NETHER".equals(env)) hasNether = true;
            else if ("THE_END".equals(env)) hasEnd = true;
            else hasOverworld = true;
        }

        // Xác định disaster thuộc dimension nào dựa trên tên
        String id = disasterId.toLowerCase();
        boolean isNether = id.contains("inferno") || id.contains("soul") || id.contains("lava");
        boolean isEnd = id.contains("end-surge") || id.contains("void") || id.contains("chorus");
        
        if (isNether && !hasNether) return false;
        if (isEnd && !hasEnd) return false;
        if (!isNether && !isEnd && !hasOverworld) return false;

        // Time conditions (approximate based on old behavior)
        if (id.equals("blood-moon") && !isNight) return false;
        if (id.equals("solar-flare") && isNight) return false;
        if (id.equals("eclipse") && isNight) return false;

        return true;
    }

    // ===== SCHEDULE WITH WARNING =====

    private void scheduleDisaster(String disasterId, ConfigManager.DisasterConfig dc) {
        String name = dc.name != null && !dc.name.isEmpty() ? dc.name : disasterId;
        currentDisaster = name;
        currentDisasterId = disasterId;
        warningTimeLeft = config.disasterWarningSeconds;

        broadcastWarning(name, config.disasterWarningSeconds);
        logManager.logDisaster(name + " (WARNING)");

        new BukkitRunnable() {
            int cd = config.disasterWarningSeconds;
            @Override
            public void run() {
                cd--;
                if (cd <= 0) {
                    this.cancel();
                    startDisasterById(disasterId, dc.durationSeconds);
                    return;
                }
                if (cd == 60 || cd == 30 || cd == 10 || cd <= 5) {
                    broadcastCountdown(name, cd);
                }
                if (config.safeZoneEnabled && cd % 5 == 0) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        sendSafeZoneMessage(p);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // ===== START DISASTER (ACTION ENGINE) =====

    private void startDisasterById(String disasterId, int durationSecondsOverride) {
        ConfigManager.DisasterConfig dc = config.disasterConfigs.get(disasterId);
        if (dc == null) return;

        disasterActive = true;
        String name = dc.name != null && !dc.name.isEmpty() ? dc.name : disasterId;

        int totalDurationTicks = (durationSecondsOverride > 0 ? durationSecondsOverride : dc.durationSeconds) * 20;

        // Show start title
        String startTitle = (dc.startTitle != null && !dc.startTitle.isEmpty()) ? dc.startTitle : "§4§l⚠ " + name;
        String startSubtitle = (dc.startSubtitle != null && !dc.startSubtitle.isEmpty()) ? dc.startSubtitle : "§cĐã bắt đầu!";

        warningBar.name(Component.text("§4§l⚠ " + name + " ĐANG HOẠT ĐỘNG ⚠"));
        warningBar.progress(1.0f);
        
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(warningBar);
            p.sendTitle(startTitle, startSubtitle, 10, 60, 20);
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);
        }
        logManager.logDisaster(name + " (ACTIVE)");

        String activeBroadcast = config.disasterMessages.getOrDefault("active-broadcast",
            "§4§l{name} - {message} (§e{duration}s§4)");
        broadcast(activeBroadcast.replace("{name}", name)
            .replace("{message}", startSubtitle)
            .replace("{duration}", String.valueOf(totalDurationTicks / 20)));

        int effectIntervalTicks = dc.effectIntervalSeconds * 20;

        // Execute on-end actions when disaster finishes
        new BukkitRunnable() {
            int elapsed = 0;
            int tickCounter = 0;
            boolean started = false;

            @Override
            public void run() {
                if (!started) {
                    started = true;
                    // Execute first wave immediately
                    executeActions(dc.actions, dc);
                }
                elapsed += 20;
                if (elapsed >= totalDurationTicks) {
                    this.cancel();
                    disasterActive = false;
                    currentDisaster = null;
                    currentDisasterId = null;
                    warningBar.name(Component.text(""));
                    warningBar.progress(0);
                    for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(warningBar);
                    
                    // Execute on-end actions
                    executeActions(dc.onEnd, dc);
                    
                    String endBroadcast = config.disasterMessages.getOrDefault("end-broadcast",
                        "§a§l✅ {name} đã kết thúc!");
                    broadcast(endBroadcast.replace("{name}", name));
                    return;
                }
                warningBar.progress(1.0f - (float) elapsed / totalDurationTicks);
                tickCounter += 20;
                if (tickCounter >= effectIntervalTicks) {
                    tickCounter = 0;
                    executeActions(dc.actions, dc);
                }
                if (config.safeZoneEnabled && tickCounter % 100 == 0) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        sendSafeZoneMessage(p);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // ===== ACTION ENGINE =====

    /**
     * Thực thi danh sách actions từ config
     */
    @SuppressWarnings("unchecked")
    private void executeActions(List<DisasterAction> actions, ConfigManager.DisasterConfig dc) {
        if (actions == null || actions.isEmpty()) return;

        for (DisasterAction action : actions) {
            try {
                executeSingleAction(action, dc);
            } catch (Exception e) {
                plugin.getLogger().warning("[Disaster] Error executing action " + action.type + ": " + e.getMessage());
            }
        }
    }

    private void executeSingleAction(DisasterAction action, ConfigManager.DisasterConfig dc) {
        // Determine which players are affected based on conditions and targets
        List<Player> affectedPlayers = getAffectedPlayers(action);

        switch (action.type) {
            case DAMAGE: {
                double damage = getParamDouble(action.params, "damage", 2.0);
                boolean ignoreArmor = getParamBool(action.params, "ignore-armor", false);
                for (Player p : affectedPlayers) {
                    if (ignoreArmor) {
                        p.damage(damage);
                    } else {
                        p.damage(damage);
                    }
                }
                break;
            }

            case POTION_EFFECT: {
                // Parse effects list from params
                List<Map<?, ?>> effectsList = (List<Map<?, ?>>) action.params.get("effects");
                if (effectsList == null) break;
                for (Player p : affectedPlayers) {
                    for (Map<?, ?> effectMap : effectsList) {
                        String effectType = (String) effectMap.get("type");
                        int duration = toInt(effectMap.get("duration-ticks"), 100);
                        int amplifier = toInt(effectMap.get("amplifier"), 0);
                        if (effectType == null) continue;
                        try {
                            PotionEffectType pet = PotionEffectType.getByName(effectType.toUpperCase());
                            if (pet != null) {
                                p.addPotionEffect(new PotionEffect(pet, duration, amplifier, false, false));
                            }
                        } catch (Exception ignored) {}
                    }
                }
                break;
            }

            case SPAWN_MOBS: {
                Map<?, ?> mobsMap = (Map<?, ?>) action.params.get("mobs");
                if (mobsMap == null) break;
                int countPerPlayer = getParamInt(action.params, "count-per-player", 1);
                int radius = getParamInt(action.params, "radius", 10);

                // Tính tổng weight
                Map<String, Map<?, ?>> mobEntries = new HashMap<>();
                int totalWeight = 0;
                for (Map.Entry<?, ?> entry : mobsMap.entrySet()) {
                    String entityType = entry.getKey().toString();
                    Map<?, ?> mobData = (Map<?, ?>) entry.getValue();
                    mobEntries.put(entityType, mobData);
                    totalWeight += toInt(mobData.get("weight"), 100);
                }

                for (Player p : affectedPlayers) {
                    for (int i = 0; i < countPerPlayer; i++) {
                        if (totalWeight <= 0) break;
                        // Weighted random chọn mob
                        int roll = random.nextInt(totalWeight);
                        int cum = 0;
                        for (Map.Entry<String, Map<?, ?>> entry : mobEntries.entrySet()) {
                            cum += toInt(entry.getValue().get("weight"), 100);
                            if (roll < cum) {
                                spawnMobNear(p, entry.getKey(), radius, entry.getValue());
                                break;
                            }
                        }
                    }
                }
                break;
            }

            case LIGHTNING_STRIKE: {
                int count = getParamInt(action.params, "count-per-player", 1);
                int radius = getParamInt(action.params, "radius", 15);
                float explosionPower = (float) getParamDouble(action.params, "explosion-power", 3.0);
                boolean fire = getParamBool(action.params, "explosion-fire", false);
                boolean breakBlocks = getParamBool(action.params, "explosion-break-blocks", true);
                int delayTicks = getParamInt(action.params, "delay-ticks", 0);

                for (Player p : affectedPlayers) {
                    for (int i = 0; i < count; i++) {
                        Location targetLoc = getRandomLocationAround(p, radius);
                        if (delayTicks > 0) {
                            Location finalTarget = targetLoc;
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    strikeLightningAt(finalTarget, explosionPower, fire, breakBlocks, affectedPlayers);
                                }
                            }.runTaskLater(plugin, delayTicks);
                        } else {
                            strikeLightningAt(targetLoc, explosionPower, fire, breakBlocks, affectedPlayers);
                        }
                    }
                }
                break;
            }

            case EXPLOSION: {
                int count = getParamInt(action.params, "count-per-player", 1);
                int radius = getParamInt(action.params, "radius", 15);
                float power = (float) getParamDouble(action.params, "explosion-power", 3.0);
                boolean fire = getParamBool(action.params, "explosion-fire", false);
                boolean breakBlocks = getParamBool(action.params, "explosion-break-blocks", true);

                for (Player p : affectedPlayers) {
                    for (int i = 0; i < count; i++) {
                        Location targetLoc = getRandomLocationAround(p, radius);
                        p.getWorld().createExplosion(targetLoc, power, fire, breakBlocks);
                    }
                }
                break;
            }

            case SET_FIRE: {
                int fireTicks = getParamInt(action.params, "fire-ticks", 100);
                for (Player p : affectedPlayers) {
                    p.setFireTicks(fireTicks);
                }
                break;
            }

            case PLACE_BLOCK: {
                String blockType = (String) action.params.get("block-type");
                int count = getParamInt(action.params, "count-per-player", 3);
                int radius = getParamInt(action.params, "radius", 10);
                int placeHeight = getParamInt(action.params, "place-height", 1);
                if (blockType == null) break;
                Material material;
                try { material = Material.valueOf(blockType.toUpperCase()); } catch (Exception e) { break; }

                for (Player p : affectedPlayers) {
                    for (int i = 0; i < count; i++) {
                        Location loc = getRandomLocationAround(p, radius);
                        for (int dy = 0; dy < placeHeight; dy++) {
                            Location blockLoc = loc.clone().add(0, dy, 0);
                            if (blockLoc.getBlock().getType() == Material.AIR) {
                                blockLoc.getBlock().setType(material);
                            }
                        }
                    }
                }
                break;
            }

            case FALLING_BLOCK: {
                int radius = getParamInt(action.params, "radius", 15);
                int minY = getParamInt(action.params, "min-y", 30);
                int blockFallChance = getParamInt(action.params, "block-fall-chance", 30);
                double resistanceFactor = getParamDouble(action.params, "blast-resistance-factor", 0.1);
                int blocksPerPlayer = getParamInt(action.params, "blocks-per-player", 5);

                for (Player p : affectedPlayers) {
                    Location l = p.getLocation();
                    for (int i = 0; i < blocksPerPlayer; i++) {
                        int bx = l.getBlockX() + random.nextInt(radius * 2) - radius;
                        int bz = l.getBlockZ() + random.nextInt(radius * 2) - radius;
                        int by = minY + random.nextInt(60);
                        Block block = new Location(p.getWorld(), bx, by, bz).getBlock();
                        Material bt = block.getType();
                        if (bt == Material.AIR || bt == Material.WATER || bt == Material.LAVA || bt == Material.BEDROCK) continue;

                        double blastRes = bt.getBlastResistance();
                        double fallProb = blockFallChance / (1.0 + blastRes * resistanceFactor);
                        if (random.nextDouble() * 100 < fallProb) {
                            FallingBlock fb = p.getWorld().spawnFallingBlock(block.getLocation().add(0.5, 0, 0.5), block.getBlockData());
                            fb.setDropItem(true);
                            fb.setHurtEntities(true);
                            block.setType(Material.AIR);
                        }
                    }
                }
                break;
            }

            case VELOCITY: {
                double velY = getParamDouble(action.params, "velocity-y", 1.5);
                String xRange = (String) action.params.get("velocity-x-range");
                String zRange = (String) action.params.get("velocity-z-range");

                for (Player p : affectedPlayers) {
                    Vector v = p.getVelocity();
                    v.setY(v.getY() + velY);
                    if (xRange != null) {
                        String[] parts = xRange.split("-");
                        double min = Double.parseDouble(parts[0]);
                        double max = Double.parseDouble(parts[1]);
                        v.setX(v.getX() + min + random.nextDouble() * (max - min));
                    } else {
                        v.setX(v.getX() + (random.nextDouble() - 0.5) * 2);
                    }
                    if (zRange != null) {
                        String[] parts = zRange.split("-");
                        double min = Double.parseDouble(parts[0]);
                        double max = Double.parseDouble(parts[1]);
                        v.setZ(v.getZ() + min + random.nextDouble() * (max - min));
                    } else {
                        v.setZ(v.getZ() + (random.nextDouble() - 0.5) * 2);
                    }
                    p.setVelocity(v);
                    p.setFallDistance(0);
                }
                break;
            }

            case SET_TIME: {
                int time = getParamInt(action.params, "time", 1000);
                for (World w : Bukkit.getWorlds()) {
                    w.setTime(time);
                }
                break;
            }

            case SET_WEATHER: {
                boolean storm = getParamBool(action.params, "storm", false);
                boolean thunder = getParamBool(action.params, "thunder", false);
                int durationTicks = getParamInt(action.params, "duration-ticks", 200000);
                for (World w : Bukkit.getWorlds()) {
                    w.setStorm(storm);
                    w.setThundering(thunder);
                    w.setWeatherDuration(durationTicks);
                }
                break;
            }

            case CLEAR_WEATHER: {
                for (World w : Bukkit.getWorlds()) {
                    w.setStorm(false);
                    w.setThundering(false);
                }
                break;
            }

            case BROADCAST: {
                String message = (String) action.params.get("message");
                if (message != null) {
                    broadcast(message.replace("{name}", currentDisaster != null ? currentDisaster : ""));
                }
                break;
            }

            case ACTION_BAR: {
                String message = (String) action.params.get("message");
                if (message != null) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendActionBar(message.replace("{name}", currentDisaster != null ? currentDisaster : ""));
                    }
                }
                break;
            }

            case TELEPORT_RANDOM: {
                int radius = getParamInt(action.params, "radius", 30);
                for (Player p : affectedPlayers) {
                    Location randomLoc = getRandomLocationAround(p, radius);
                    randomLoc.setY(p.getWorld().getHighestBlockYAt(randomLoc) + 1);
                    p.teleport(randomLoc);
                }
                break;
            }

            case PLAY_SOUND: {
                String soundStr = (String) action.params.get("sound");
                if (soundStr == null) break;
                try {
                    Sound sound = Sound.valueOf(soundStr.toUpperCase());
                    float volume = (float) getParamDouble(action.params, "volume", 1.0);
                    float pitch = (float) getParamDouble(action.params, "pitch", 1.0);
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), sound, volume, pitch);
                    }
                } catch (Exception ignored) {}
                break;
            }
        }
    }

    // ===== HELPERS =====

    /**
     * Lấy danh sách player bị ảnh hưởng dựa trên action conditions và targets
     */
    private List<Player> getAffectedPlayers(DisasterAction action) {
        List<Player> result = new ArrayList<>();
        boolean requireOutdoor = action.condition.requireOutdoor;
        boolean ignoreSafeZone = action.condition.ignoreSafeZone;

        for (Player p : Bukkit.getOnlinePlayers()) {
            // Check condition
            if (requireOutdoor && !ignoreSafeZone && isPlayerSafe(p)) continue;
            if (requireOutdoor && ignoreSafeZone) {
                // Even if ignore safe zone, still require outdoor (light from sky)
                if (p.getLocation().getBlock().getLightFromSky() <= 5) continue;
            }
            
            // Check targets: if no targets specified, affect all players
            if (action.targets.isEmpty()) {
                result.add(p);
                continue;
            }

            // Weighted target selection
            List<DisasterAction.ActionTarget> validTargets = new ArrayList<>();
            int totalWeight = 0;
            for (DisasterAction.ActionTarget t : action.targets) {
                if (t.entityType.equals("all_players") || t.entityType.equals("player")) {
                    // Check world filter
                    if (!t.worlds.contains("all") && !t.worlds.contains(p.getWorld().getEnvironment().name().toLowerCase())) continue;
                    validTargets.add(t);
                    totalWeight += t.weight;
                }
            }
            if (totalWeight > 0) {
                // All matched targets include this player
                result.add(p);
            }
        }
        return result;
    }

    private Location getRandomLocationAround(Player player, int radius) {
        int x = player.getLocation().getBlockX() + random.nextInt(radius * 2) - radius;
        int z = player.getLocation().getBlockZ() + random.nextInt(radius * 2) - radius;
        int y = player.getWorld().getHighestBlockYAt(x, z) + 1;
        return new Location(player.getWorld(), x, y, z);
    }

    private void spawnMobNear(Player player, String entityTypeStr, int radius, Map<?, ?> mobData) {
        try {
            EntityType entityType = EntityType.valueOf(entityTypeStr.toUpperCase());
            Location spawnLoc = getRandomLocationAround(player, radius);
            Entity entity = player.getWorld().spawnEntity(spawnLoc, entityType);
            
            // Apply potion effects from mob config
            List<Map<?, ?>> effectsList = (List<Map<?, ?>>) mobData.get("effects");
            if (effectsList != null && entity instanceof LivingEntity le) {
                for (Map<?, ?> effectMap : effectsList) {
                    String effectType = (String) effectMap.get("type");
                    int duration = toInt(effectMap.get("duration-ticks"), 600);
                    int amplifier = toInt(effectMap.get("amplifier"), 0);
                    if (effectType == null) continue;
                    try {
                        PotionEffectType pet = PotionEffectType.getByName(effectType.toUpperCase());
                        if (pet != null) {
                            le.addPotionEffect(new PotionEffect(pet, duration, amplifier, true, false));
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[Disaster] Invalid entity type: " + entityTypeStr);
        }
    }

    private void strikeLightningAt(Location loc, float explosionPower, boolean fire, boolean breakBlocks, List<Player> affectedPlayers) {
        loc.getWorld().strikeLightningEffect(new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY() + 30, loc.getBlockZ()));
        if (explosionPower > 0) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    loc.getWorld().createExplosion(loc, explosionPower, fire, breakBlocks);
                    // Damage only affected players
                    for (Player p : affectedPlayers) {
                        if (p.getLocation().distance(loc) < 8) {
                            double dmg = 8.0 * (1.0 - p.getLocation().distance(loc) / 8.0);
                            p.damage(dmg);
                        }
                    }
                }
            }.runTaskLater(plugin, 30L);
        }
    }

    private void broadcast(String message) {
        Bukkit.broadcastMessage(message);
    }

    private void broadcastWarning(String name, int seconds) {
        String warningTitle = config.disasterMessages.getOrDefault("warning-title", "§4§l⚠ CẢNH BÁO THIÊN TAI ⚠");
        String warningSubtitle = config.disasterMessages.getOrDefault("warning-subtitle", "§c{name}\n§e§lSẽ xảy ra trong {time} giây!");
        String warningBroadcast = config.disasterMessages.getOrDefault("warning-broadcast", "§4§l⚠ {name} §r§cđang đến gần!");

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(warningBar);
            p.sendTitle(warningTitle,
                warningSubtitle.replace("{name}", name).replace("{time}", String.valueOf(seconds)), 10, 70, 20);
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 0.5f);
            p.sendMessage(warningBroadcast.replace("{name}", name));
        }
        logManager.logDisaster(name + " (WARNING - " + seconds + "s)");
    }

    private void broadcastCountdown(String name, int seconds) {
        String countdownMsg = config.disasterMessages.getOrDefault("countdown-broadcast",
            "§4§l⚠ {name} §csẽ xảy ra trong §4§l{time}§c giây!");
        broadcast(countdownMsg.replace("{name}", name).replace("{time}", String.valueOf(seconds)));
    }

    private void updateWarningBar() {
        if (warningTimeLeft > 0 && currentDisaster != null) {
            warningBar.name(Component.text("§4§l⚠ " + currentDisaster + " §7- §e§l" +
                (warningTimeLeft / 60) + ":" + String.format("%02d", warningTimeLeft % 60)));
            warningBar.progress((float) warningTimeLeft / 300f);
        }
    }

    // ===== PARAM HELPERS =====

    private int getParamInt(Map<String, Object> params, String key, int defaultValue) {
        Object val = params.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) try { return Integer.parseInt((String) val); } catch (Exception e) { return defaultValue; }
        return defaultValue;
    }

    private double getParamDouble(Map<String, Object> params, String key, double defaultValue) {
        Object val = params.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) try { return Double.parseDouble((String) val); } catch (Exception e) { return defaultValue; }
        return defaultValue;
    }

    private boolean getParamBool(Map<String, Object> params, String key, boolean defaultValue) {
        Object val = params.get(key);
        if (val instanceof Boolean) return (Boolean) val;
        if (val instanceof String) return Boolean.parseBoolean((String) val);
        return defaultValue;
    }

    private int toInt(Object value, int defaultValue) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) try { return Integer.parseInt((String) value); } catch (Exception e) { return defaultValue; }
        return defaultValue;
    }
}