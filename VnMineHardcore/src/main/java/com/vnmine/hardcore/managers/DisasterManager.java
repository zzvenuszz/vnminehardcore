package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

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

    // Danh sách block được coi là "cây cối" (trees)
    private static final Set<Material> TREE_MATERIALS = new HashSet<>(Arrays.asList(
        Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG,
        Material.JUNGLE_LOG, Material.ACACIA_LOG, Material.DARK_OAK_LOG,
        Material.MANGROVE_LOG, Material.CHERRY_LOG,
        Material.OAK_WOOD, Material.SPRUCE_WOOD, Material.BIRCH_WOOD,
        Material.JUNGLE_WOOD, Material.ACACIA_WOOD, Material.DARK_OAK_WOOD,
        Material.MANGROVE_WOOD, Material.CHERRY_WOOD,
        Material.STRIPPED_OAK_LOG, Material.STRIPPED_SPRUCE_LOG,
        Material.STRIPPED_BIRCH_LOG, Material.STRIPPED_JUNGLE_LOG,
        Material.STRIPPED_ACACIA_LOG, Material.STRIPPED_DARK_OAK_LOG,
        Material.STRIPPED_MANGROVE_LOG, Material.STRIPPED_CHERRY_LOG,
        Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES,
        Material.JUNGLE_LEAVES, Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES,
        Material.MANGROVE_LEAVES, Material.CHERRY_LEAVES,
        Material.AZALEA_LEAVES, Material.FLOWERING_AZALEA_LEAVES,
        Material.MANGROVE_ROOTS, Material.MUDDY_MANGROVE_ROOTS
    ));

    // Danh sách friendly mobs
    private static final Set<Class<? extends Entity>> FRIENDLY_MOB_CLASSES = new HashSet<>(Arrays.asList(
        Cow.class, Sheep.class, Pig.class, Chicken.class, Rabbit.class,
        Horse.class, Donkey.class, Mule.class, Llama.class, TraderLlama.class,
        Wolf.class, Cat.class, Ocelot.class, Parrot.class,
        Villager.class, WanderingTrader.class, IronGolem.class, Snowman.class,
        Bee.class, Fox.class, Panda.class, PolarBear.class,
        Turtle.class, Dolphin.class, Squid.class, GlowSquid.class,
        Allay.class, Axolotl.class, Camel.class, Frog.class, Goat.class, Sniffer.class,
        Armadillo.class, Bogged.class
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

    /**
     * Thực thi một action và chain-actions của nó
     */
    private void executeSingleAction(DisasterAction action, ConfigManager.DisasterConfig dc) {
        // Lấy danh sách targets dựa trên action targets
        List<Object> targets = getAffectedTargets(action);

        if (targets.isEmpty()) return;

        // Thực thi action chính trên mỗi target
        for (Object target : targets) {
            executeActionOnTarget(action, target);
        }

        // Thực thi chain-actions nếu có
        if (!action.chainActions.isEmpty()) {
            for (Object target : targets) {
                for (DisasterAction chainAction : action.chainActions) {
                    // Chain action thực thi trên vị trí của target chính
                    Location chainLocation = getTargetLocation(target);
                    if (chainLocation != null) {
                        executeChainActionAt(chainAction, chainLocation, target);
                    }
                }
            }
        }
    }

    /**
     * Thực thi action trên một target cụ thể
     */
    @SuppressWarnings("unchecked")
    private void executeActionOnTarget(DisasterAction action, Object target) {
        Location targetLoc = getTargetLocation(target);
        if (targetLoc == null) return;

        switch (action.type) {
            // ===== CORE ACTIONS =====
            case DAMAGE: {
                double damage = getParamDouble(action.params, "damage", 2.0);
                boolean ignoreArmor = getParamBool(action.params, "ignore-armor", false);
                if (target instanceof LivingEntity le) {
                    le.damage(damage);
                }
                break;
            }

            case POTION_EFFECT: {
                List<Map<?, ?>> effectsList = (List<Map<?, ?>>) action.params.get("effects");
                if (effectsList == null) break;
                if (target instanceof LivingEntity le) {
                    for (Map<?, ?> effectMap : effectsList) {
                        String effectType = (String) effectMap.get("type");
                        int duration = toInt(effectMap.get("duration-ticks"), 100);
                        int amplifier = toInt(effectMap.get("amplifier"), 0);
                        if (effectType == null) continue;
                        try {
                            PotionEffectType pet = PotionEffectType.getByName(effectType.toUpperCase());
                            if (pet != null) {
                                le.addPotionEffect(new PotionEffect(pet, duration, amplifier, false, false));
                            }
                        } catch (Exception ignored) {}
                    }
                }
                break;
            }

            case SPAWN_MOBS: {
                Map<?, ?> mobsMap = (Map<?, ?>) action.params.get("mobs");
                if (mobsMap == null) break;
                int countPerTarget = getParamInt(action.params, "count-per-target", 1);
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

                for (int i = 0; i < countPerTarget; i++) {
                    if (totalWeight <= 0) break;
                    int roll = random.nextInt(totalWeight);
                    int cum = 0;
                    for (Map.Entry<String, Map<?, ?>> entry : mobEntries.entrySet()) {
                        cum += toInt(entry.getValue().get("weight"), 100);
                        if (roll < cum) {
                            spawnMobAt(targetLoc, entry.getKey(), radius, entry.getValue());
                            break;
                        }
                    }
                }
                break;
            }

            case LIGHTNING_STRIKE: {
                int count = getParamInt(action.params, "count-per-target", 1);
                int radius = getParamInt(action.params, "radius", 15);
                float explosionPower = (float) getParamDouble(action.params, "explosion-power", 3.0f);
                boolean fire = getParamBool(action.params, "explosion-fire", false);
                boolean breakBlocks = getParamBool(action.params, "explosion-break-blocks", true);
                int delayTicks = getParamInt(action.params, "delay-ticks", 0);
                double damageMultiplier = getParamDouble(action.params, "damage-multiplier", 1.0);

                for (int i = 0; i < count; i++) {
                    Location strikeLoc = getRandomLocationAround(targetLoc, radius);
                    if (delayTicks > 0) {
                        Location finalTarget = strikeLoc;
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                strikeLightningAt(finalTarget, explosionPower, fire, breakBlocks, damageMultiplier);
                            }
                        }.runTaskLater(plugin, delayTicks);
                    } else {
                        strikeLightningAt(strikeLoc, explosionPower, fire, breakBlocks, damageMultiplier);
                    }
                }
                break;
            }

            case EXPLOSION: {
                int count = getParamInt(action.params, "count-per-target", 1);
                int radius = getParamInt(action.params, "radius", 15);
                float power = (float) getParamDouble(action.params, "explosion-power", 3.0f);
                boolean fire = getParamBool(action.params, "explosion-fire", false);
                boolean breakBlocks = getParamBool(action.params, "explosion-break-blocks", true);

                for (int i = 0; i < count; i++) {
                    Location explLoc = getRandomLocationAround(targetLoc, radius);
                    targetLoc.getWorld().createExplosion(explLoc, power, fire, breakBlocks);
                }
                break;
            }

            case SET_FIRE: {
                int fireTicks = getParamInt(action.params, "fire-ticks", 100);
                if (target instanceof LivingEntity le) {
                    le.setFireTicks(fireTicks);
                } else if (target instanceof Block block) {
                    // Đốt block (nếu là block cháy được)
                    if (block.getType() == Material.AIR) {
                        block.setType(Material.FIRE);
                    }
                }
                break;
            }

            case PLACE_BLOCK: {
                String blockType = (String) action.params.get("block-type");
                int count = getParamInt(action.params, "count-per-target", 3);
                int radius = getParamInt(action.params, "radius", 10);
                int placeHeight = getParamInt(action.params, "place-height", 1);
                if (blockType == null) break;
                Material material;
                try { material = Material.valueOf(blockType.toUpperCase()); } catch (Exception e) { break; }

                for (int i = 0; i < count; i++) {
                    Location loc = getRandomLocationAround(targetLoc, radius);
                    for (int dy = 0; dy < placeHeight; dy++) {
                        Location blockLoc = loc.clone().add(0, dy, 0);
                        if (blockLoc.getBlock().getType() == Material.AIR) {
                            blockLoc.getBlock().setType(material);
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
                int blocksPerTarget = getParamInt(action.params, "blocks-per-target", 5);

                for (int i = 0; i < blocksPerTarget; i++) {
                    int bx = targetLoc.getBlockX() + random.nextInt(radius * 2) - radius;
                    int bz = targetLoc.getBlockZ() + random.nextInt(radius * 2) - radius;
                    int by = minY + random.nextInt(60);
                    Block block = new Location(targetLoc.getWorld(), bx, by, bz).getBlock();
                    Material bt = block.getType();
                    if (bt == Material.AIR || bt == Material.WATER || bt == Material.LAVA || bt == Material.BEDROCK) continue;

                    double blastRes = bt.getBlastResistance();
                    double fallProb = blockFallChance / (1.0 + blastRes * resistanceFactor);
                    if (random.nextDouble() * 100 < fallProb) {
                        FallingBlock fb = targetLoc.getWorld().spawnFallingBlock(block.getLocation().add(0.5, 0, 0.5), block.getBlockData());
                        fb.setDropItem(true);
                        fb.setHurtEntities(true);
                        block.setType(Material.AIR);
                    }
                }
                break;
            }

            case VELOCITY: {
                double velY = getParamDouble(action.params, "velocity-y", 1.5);
                String xRange = (String) action.params.get("velocity-x-range");
                String zRange = (String) action.params.get("velocity-z-range");

                if (target instanceof LivingEntity le) {
                    Vector v = le.getVelocity();
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
                    le.setVelocity(v);
                    le.setFallDistance(0);
                }
                break;
            }

            case SET_TIME:
            case WORLD_TIME: {
                int time = getParamInt(action.params, "time", 1000);
                for (World w : Bukkit.getWorlds()) {
                    w.setTime(time);
                }
                break;
            }

            case SET_WEATHER:
            case WORLD_WEATHER: {
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
                if (target instanceof Player p) {
                    Location randomLoc = getRandomLocationAround(p.getLocation(), radius);
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

            // ===== NEW BLOCK ACTIONS =====
            case BLOCK_VELOCITY: {
                double velY = getParamDouble(action.params, "velocity-y", 2.0);
                boolean damageOnLand = getParamBool(action.params, "damage-on-land", true);
                double damageAmount = getParamDouble(action.params, "damage-amount", 4.0);
                boolean replaceWithAir = getParamBool(action.params, "replace-with-air", true);

                if (target instanceof Block block) {
                    Material bt = block.getType();
                    if (bt == Material.AIR || bt == Material.WATER || bt == Material.LAVA || bt == Material.BEDROCK) break;
                    
                    FallingBlock fb = targetLoc.getWorld().spawnFallingBlock(
                        block.getLocation().add(0.5, 0, 0.5), block.getBlockData());
                    fb.setDropItem(true);
                    fb.setHurtEntities(damageOnLand);
                    if (replaceWithAir) {
                        block.setType(Material.AIR);
                    }
                    // Đẩy block lên
                    fb.setVelocity(new Vector(
                        (random.nextDouble() - 0.5) * 2,
                        velY,
                        (random.nextDouble() - 0.5) * 2
                    ));
                }
                break;
            }

            case BLOCK_EXPLOSION: {
                int radius = getParamInt(action.params, "radius", 5);
                float power = (float) getParamDouble(action.params, "power", 2.0f);
                boolean dropItems = getParamBool(action.params, "drop-items", true);

                if (target instanceof Block block) {
                    // Phá hủy block dạng nổ
                    block.getWorld().createExplosion(block.getLocation(), power, false, dropItems);
                } else {
                    // Nổ tại vị trí target
                    targetLoc.getWorld().createExplosion(targetLoc, power, false, dropItems);
                }
                break;
            }

            case BLOCK_REPLACE: {
                String fromMaterial = (String) action.params.get("from-material");
                String toMaterial = (String) action.params.get("to-material");
                int replaceRadius = getParamInt(action.params, "radius", 3);
                if (fromMaterial == null || toMaterial == null) break;
                
                Material fromMat;
                Material toMat;
                try {
                    fromMat = Material.valueOf(fromMaterial.toUpperCase());
                    toMat = Material.valueOf(toMaterial.toUpperCase());
                } catch (Exception e) { break; }

                for (int dx = -replaceRadius; dx <= replaceRadius; dx++) {
                    for (int dy = -replaceRadius; dy <= replaceRadius; dy++) {
                        for (int dz = -replaceRadius; dz <= replaceRadius; dz++) {
                            Block b = targetLoc.getBlock().getRelative(dx, dy, dz);
                            if (b.getType() == fromMat) {
                                b.setType(toMat);
                            }
                        }
                    }
                }
                break;
            }

            case BLOCK_IGNITE: {
                int igniteRadius = getParamInt(action.params, "radius", 3);
                int fireTicks = getParamInt(action.params, "fire-ticks", 100);

                for (int dx = -igniteRadius; dx <= igniteRadius; dx++) {
                    for (int dy = -igniteRadius; dy <= igniteRadius; dy++) {
                        for (int dz = -igniteRadius; dz <= igniteRadius; dz++) {
                            Block b = targetLoc.getBlock().getRelative(dx, dy, dz);
                            if (b.getType() == Material.AIR && b.getRelative(0, -1, 0).getType().isSolid()) {
                                b.setType(Material.FIRE);
                            }
                        }
                    }
                }
                break;
            }

            case BLOCK_LIQUID: {
                String liquidType = (String) action.params.get("liquid-type");
                int liquidRadius = getParamInt(action.params, "radius", 3);
                if (liquidType == null) break;
                
                Material liquidMat;
                try {
                    liquidMat = Material.valueOf(liquidType.toUpperCase());
                } catch (Exception e) { break; }
                
                if (liquidMat != Material.WATER && liquidMat != Material.LAVA) break;

                for (int dx = -liquidRadius; dx <= liquidRadius; dx++) {
                    for (int dy = -liquidRadius; dy <= liquidRadius; dy++) {
                        for (int dz = -liquidRadius; dz <= liquidRadius; dz++) {
                            Block b = targetLoc.getBlock().getRelative(dx, dy, dz);
                            if (b.getType() == Material.AIR) {
                                b.setType(liquidMat);
                            }
                        }
                    }
                }
                break;
            }

            case BLOCK_FERTILIZE: {
                int fertilizeRadius = getParamInt(action.params, "radius", 5);
                double bonemealChance = getParamDouble(action.params, "bonemeal-chance", 0.3);

                for (int dx = -fertilizeRadius; dx <= fertilizeRadius; dx++) {
                    for (int dz = -fertilizeRadius; dz <= fertilizeRadius; dz++) {
                        Block b = targetLoc.getBlock().getRelative(dx, 0, dz);
                        if (random.nextDouble() < bonemealChance) {
                            b.applyBoneMeal(org.bukkit.block.BlockFace.UP);
                        }
                    }
                }
                break;
            }

            // ===== NEW ENTITY ACTIONS =====
            case ENTITY_PULL: {
                double pullStrength = getParamDouble(action.params, "pull-strength", 1.0);
                int pullRadius = getParamInt(action.params, "radius", 10);

                for (Entity e : targetLoc.getWorld().getNearbyEntities(targetLoc, pullRadius, pullRadius, pullRadius)) {
                    if (e instanceof LivingEntity le && !(e instanceof Player)) {
                        Vector pull = targetLoc.toVector().subtract(le.getLocation().toVector()).normalize().multiply(pullStrength);
                        le.setVelocity(le.getVelocity().add(pull));
                    }
                }
                break;
            }

            case ENTITY_PUSH: {
                double pushStrength = getParamDouble(action.params, "push-strength", 1.0);
                int pushRadius = getParamInt(action.params, "radius", 10);

                for (Entity e : targetLoc.getWorld().getNearbyEntities(targetLoc, pushRadius, pushRadius, pushRadius)) {
                    if (e instanceof LivingEntity le && !(e instanceof Player)) {
                        Vector push = le.getLocation().toVector().subtract(targetLoc.toVector()).normalize().multiply(pushStrength);
                        le.setVelocity(le.getVelocity().add(push));
                    }
                }
                break;
            }

            case ENTITY_FREEZE: {
                int freezeDuration = getParamInt(action.params, "duration-ticks", 100);
                int freezeRadius = getParamInt(action.params, "radius", 5);

                for (Entity e : targetLoc.getWorld().getNearbyEntities(targetLoc, freezeRadius, freezeRadius, freezeRadius)) {
                    if (e instanceof LivingEntity le) {
                        le.setFreezeTicks(freezeDuration);
                    }
                }
                break;
            }

            case ENTITY_DISMOUNT: {
                int dismountRadius = getParamInt(action.params, "radius", 10);
                for (Entity e : targetLoc.getWorld().getNearbyEntities(targetLoc, dismountRadius, dismountRadius, dismountRadius)) {
                    if (e instanceof LivingEntity le) {
                        le.eject();
                        le.leaveVehicle();
                    }
                }
                break;
            }

            case ENTITY_MOUNT: {
                String mountType = (String) action.params.get("mount-type");
                int mountRadius = getParamInt(action.params, "radius", 10);
                if (mountType == null) break;
                
                try {
                    EntityType mountEntityType = EntityType.valueOf(mountType.toUpperCase());
                    for (Entity e : targetLoc.getWorld().getNearbyEntities(targetLoc, mountRadius, mountRadius, mountRadius)) {
                        if (e instanceof LivingEntity le && !(e instanceof Player)) {
                            Entity mount = targetLoc.getWorld().spawnEntity(le.getLocation(), mountEntityType);
                            if (mount instanceof LivingEntity) {
                                le.addPassenger(mount);
                            }
                        }
                    }
                } catch (Exception ignored) {}
                break;
            }

            // ===== NEW ITEM ACTIONS =====
            case ITEM_DROP: {
                String itemMaterial = (String) action.params.get("material");
                int itemAmount = getParamInt(action.params, "amount", 1);
                int itemRadius = getParamInt(action.params, "radius", 3);
                if (itemMaterial == null) break;
                
                try {
                    Material mat = Material.valueOf(itemMaterial.toUpperCase());
                    for (int i = 0; i < itemAmount; i++) {
                        Location dropLoc = getRandomLocationAround(targetLoc, itemRadius);
                        targetLoc.getWorld().dropItemNaturally(dropLoc, new ItemStack(mat, 1));
                    }
                } catch (Exception ignored) {}
                break;
            }
        }
    }

    /**
     * Thực thi chain action tại một vị trí cụ thể
     */
    private void executeChainActionAt(DisasterAction chainAction, Location location, Object originalTarget) {
        // Tạo một action tạm thời với target là vị trí hiện tại
        // Chain action luôn thực thi tại vị trí của target chính
        switch (chainAction.type) {
            case EXPLOSION: {
                float power = (float) getParamDouble(chainAction.params, "power", 2.0f);
                boolean fire = getParamBool(chainAction.params, "fire", false);
                boolean breakBlocks = getParamBool(chainAction.params, "break-blocks", true);
                location.getWorld().createExplosion(location, power, fire, breakBlocks);
                break;
            }

            case DAMAGE: {
                double damage = getParamDouble(chainAction.params, "damage", 2.0);
                int damageRadius = getParamInt(chainAction.params, "radius", 5);
                boolean ignoreArmor = getParamBool(chainAction.params, "ignore-armor", false);
                for (Entity e : location.getWorld().getNearbyEntities(location, damageRadius, damageRadius, damageRadius)) {
                    if (e instanceof LivingEntity le) {
                        le.damage(damage);
                    }
                }
                break;
            }

            case SET_FIRE: {
                int fireTicks = getParamInt(chainAction.params, "fire-ticks", 100);
                int fireRadius = getParamInt(chainAction.params, "radius", 3);
                for (int dx = -fireRadius; dx <= fireRadius; dx++) {
                    for (int dy = -fireRadius; dy <= fireRadius; dy++) {
                        for (int dz = -fireRadius; dz <= fireRadius; dz++) {
                            Block b = location.getBlock().getRelative(dx, dy, dz);
                            if (b.getType() == Material.AIR && b.getRelative(0, -1, 0).getType().isSolid()) {
                                b.setType(Material.FIRE);
                            }
                        }
                    }
                }
                // Đốt entity trong bán kính
                for (Entity e : location.getWorld().getNearbyEntities(location, fireRadius, fireRadius, fireRadius)) {
                    if (e instanceof LivingEntity le) {
                        le.setFireTicks(fireTicks);
                    }
                }
                break;
            }

            case POTION_EFFECT: {
                @SuppressWarnings("unchecked")
                List<Map<?, ?>> effectsList = (List<Map<?, ?>>) chainAction.params.get("effects");
                if (effectsList == null) break;
                int effectRadius = getParamInt(chainAction.params, "radius", 5);
                for (Entity e : location.getWorld().getNearbyEntities(location, effectRadius, effectRadius, effectRadius)) {
                    if (e instanceof LivingEntity le) {
                        for (Map<?, ?> effectMap : effectsList) {
                            String effectType = (String) effectMap.get("type");
                            int duration = toInt(effectMap.get("duration-ticks"), 100);
                            int amplifier = toInt(effectMap.get("amplifier"), 0);
                            if (effectType == null) continue;
                            try {
                                PotionEffectType pet = PotionEffectType.getByName(effectType.toUpperCase());
                                if (pet != null) {
                                    le.addPotionEffect(new PotionEffect(pet, duration, amplifier, false, false));
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
                break;
            }

            case LIGHTNING_STRIKE: {
                float explosionPower = (float) getParamDouble(chainAction.params, "explosion-power", 0f);
                boolean fire = getParamBool(chainAction.params, "fire", false);
                boolean breakBlocks = getParamBool(chainAction.params, "break-blocks", false);
                double damageMultiplier = getParamDouble(chainAction.params, "damage-multiplier", 1.0);
                strikeLightningAt(location, explosionPower, fire, breakBlocks, damageMultiplier);
                break;
            }

            case FALLING_BLOCK: {
                boolean damageOnLand = getParamBool(chainAction.params, "damage-on-land", true);
                double damageAmount = getParamDouble(chainAction.params, "damage-amount", 4.0);
                boolean replaceWithAir = getParamBool(chainAction.params, "replace-with-air", true);
                
                // Tạo falling block tại vị trí
                Block block = location.getBlock();
                if (block.getType() != Material.AIR && block.getType() != Material.WATER && block.getType() != Material.LAVA) {
                    FallingBlock fb = location.getWorld().spawnFallingBlock(
                        location.add(0.5, 2, 0.5), block.getBlockData());
                    fb.setDropItem(true);
                    fb.setHurtEntities(damageOnLand);
                    if (replaceWithAir) {
                        block.setType(Material.AIR);
                    }
                }
                break;
            }

            case VELOCITY: {
                double velY = getParamDouble(chainAction.params, "velocity-y", 1.5);
                int velRadius = getParamInt(chainAction.params, "radius", 5);
                for (Entity e : location.getWorld().getNearbyEntities(location, velRadius, velRadius, velRadius)) {
                    if (e instanceof LivingEntity le) {
                        Vector v = le.getVelocity();
                        v.setY(v.getY() + velY);
                        v.setX(v.getX() + (random.nextDouble() - 0.5) * 2);
                        v.setZ(v.getZ() + (random.nextDouble() - 0.5) * 2);
                        le.setVelocity(v);
                    }
                }
                break;
            }

            case BLOCK_VELOCITY: {
                double velY = getParamDouble(chainAction.params, "velocity-y", 2.0);
                boolean damageOnLand = getParamBool(chainAction.params, "damage-on-land", true);
                boolean replaceWithAir = getParamBool(chainAction.params, "replace-with-air", true);
                int bvRadius = getParamInt(chainAction.params, "radius", 3);

                for (int dx = -bvRadius; dx <= bvRadius; dx++) {
                    for (int dz = -bvRadius; dz <= bvRadius; dz++) {
                        Block b = location.getBlock().getRelative(dx, 0, dz);
                        Material bt = b.getType();
                        if (bt == Material.AIR || bt == Material.WATER || bt == Material.LAVA || bt == Material.BEDROCK) continue;
                        if (random.nextInt(3) != 0) continue; // Không nhấc tất cả
                        
                        FallingBlock fb = location.getWorld().spawnFallingBlock(
                            b.getLocation().add(0.5, 0, 0.5), b.getBlockData());
                        fb.setDropItem(true);
                        fb.setHurtEntities(damageOnLand);
                        if (replaceWithAir) {
                            b.setType(Material.AIR);
                        }
                        fb.setVelocity(new Vector(
                            (random.nextDouble() - 0.5) * 2,
                            velY,
                            (random.nextDouble() - 0.5) * 2
                        ));
                    }
                }
                break;
            }

            case ITEM_DROP: {
                String itemMaterial = (String) chainAction.params.get("material");
                int itemAmount = getParamInt(chainAction.params, "amount", 1);
                if (itemMaterial == null) break;
                try {
                    Material mat = Material.valueOf(itemMaterial.toUpperCase());
                    for (int i = 0; i < itemAmount; i++) {
                        location.getWorld().dropItemNaturally(location, new ItemStack(mat, 1));
                    }
                } catch (Exception ignored) {}
                break;
            }

            case PLAY_SOUND: {
                String soundStr = (String) chainAction.params.get("sound");
                if (soundStr == null) break;
                try {
                    Sound sound = Sound.valueOf(soundStr.toUpperCase());
                    float volume = (float) getParamDouble(chainAction.params, "volume", 1.0);
                    float pitch = (float) getParamDouble(chainAction.params, "pitch", 1.0);
                    location.getWorld().playSound(location, sound, volume, pitch);
                } catch (Exception ignored) {}
                break;
            }

            default:
                break;
        }
    }

    // ===== TARGET SYSTEM =====

    /**
     * Lấy danh sách các đối tượng target dựa trên action targets
     * Trả về List<Object> có thể chứa Player, LivingEntity, Block, Location
     */
    private List<Object> getAffectedTargets(DisasterAction action) {
        List<Object> result = new ArrayList<>();
        boolean requireOutdoor = action.condition.requireOutdoor;
        boolean ignoreSafeZone = action.condition.ignoreSafeZone;

        // Nếu không có targets, mặc định là tất cả player
        if (action.targets.isEmpty()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (requireOutdoor && !ignoreSafeZone && isPlayerSafe(p)) continue;
                result.add(p);
            }
            return result;
        }

        // Xử lý từng target type
        for (DisasterAction.ActionTarget target : action.targets) {
            String entityType = target.entityType;
            int weight = target.weight;
            int radius = target.radius;

            // Parse target type
            if (entityType.equals("player") || entityType.equals("all_players")) {
                // Player targets
                List<Player> players = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    // Check condition
                    if (requireOutdoor && !ignoreSafeZone && isPlayerSafe(p)) continue;
                    if (requireOutdoor && ignoreSafeZone) {
                        if (p.getLocation().getBlock().getLightFromSky() <= 5) continue;
                    }
                    // Check world filter
                    if (!target.worlds.contains("all") && !target.worlds.contains(p.getWorld().getEnvironment().name().toLowerCase())) continue;
                    players.add(p);
                }

                if (entityType.equals("all_players")) {
                    result.addAll(players);
                } else {
                    // player: weighted random chọn 1
                    if (!players.isEmpty()) {
                        result.add(players.get(random.nextInt(players.size())));
                    }
                }
            } else if (entityType.equals("friendly_mobs") || entityType.equals("hostile_mobs") || entityType.equals("all_mobs")) {
                // Mob targets
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (requireOutdoor && !ignoreSafeZone && isPlayerSafe(p)) continue;
                    
                    for (Entity e : p.getWorld().getNearbyEntities(p.getLocation(), radius, radius, radius)) {
                        if (e instanceof LivingEntity le && !(e instanceof Player)) {
                            boolean isFriendly = isFriendlyMob(le);
                            boolean isHostile = !isFriendly && !(le instanceof Boss);

                            if (entityType.equals("friendly_mobs") && isFriendly) {
                                result.add(le);
                            } else if (entityType.equals("hostile_mobs") && isHostile) {
                                result.add(le);
                            } else if (entityType.equals("all_mobs")) {
                                result.add(le);
                            }
                        }
                    }
                }
            } else if (entityType.equals("ground")) {
                // Ground blocks - block dưới chân mỗi player
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (requireOutdoor && !ignoreSafeZone && isPlayerSafe(p)) continue;
                    Block ground = p.getLocation().getBlock().getRelative(0, -1, 0);
                    if (ground.getType() != Material.AIR && ground.getType() != Material.WATER && ground.getType() != Material.LAVA) {
                        result.add(ground);
                    }
                }
            } else if (entityType.equals("trees")) {
                // Tree blocks
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (requireOutdoor && !ignoreSafeZone && isPlayerSafe(p)) continue;
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dy = -radius; dy <= radius; dy++) {
                            for (int dz = -radius; dz <= radius; dz++) {
                                Block b = p.getLocation().getBlock().getRelative(dx, dy, dz);
                                if (TREE_MATERIALS.contains(b.getType())) {
                                    result.add(b);
                                }
                            }
                        }
                    }
                }
            } else if (entityType.equals("blocks")) {
                // All solid blocks
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (requireOutdoor && !ignoreSafeZone && isPlayerSafe(p)) continue;
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dy = -radius; dy <= radius; dy++) {
                            for (int dz = -radius; dz <= radius; dz++) {
                                Block b = p.getLocation().getBlock().getRelative(dx, dy, dz);
                                if (!TRANSPARENT_BLOCKS.contains(b.getType()) && b.getType().isSolid()) {
                                    result.add(b);
                                }
                            }
                        }
                    }
                }
            } else if (entityType.equals("surface_blocks")) {
                // Surface blocks
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (requireOutdoor && !ignoreSafeZone && isPlayerSafe(p)) continue;
                    Location loc = p.getLocation();
                    int highestY = loc.getWorld().getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ());
                    Block surface = loc.getWorld().getBlockAt(loc.getBlockX(), highestY, loc.getBlockZ());
                    if (!TRANSPARENT_BLOCKS.contains(surface.getType())) {
                        result.add(surface);
                    }
                }
            } else if (entityType.startsWith("random_blocks:")) {
                // Random blocks from material list
                String materialsStr = entityType.substring("random_blocks:".length());
                String[] materialNames = materialsStr.split(",");
                List<Material> targetMaterials = new ArrayList<>();
                for (String matName : materialNames) {
                    try {
                        targetMaterials.add(Material.valueOf(matName.trim().toUpperCase()));
                    } catch (Exception ignored) {}
                }
                if (!targetMaterials.isEmpty()) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (requireOutdoor && !ignoreSafeZone && isPlayerSafe(p)) continue;
                        for (int dx = -radius; dx <= radius; dx++) {
                            for (int dy = -radius; dy <= radius; dy++) {
                                for (int dz = -radius; dz <= radius; dz++) {
                                    Block b = p.getLocation().getBlock().getRelative(dx, dy, dz);
                                    if (targetMaterials.contains(b.getType())) {
                                        result.add(b);
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (entityType.startsWith("random:")) {
                // Random từ array target types
                String targetsStr = entityType.substring("random:".length());
                String[] targetTypes = targetsStr.split(",");
                List<String> targetList = Arrays.asList(targetTypes);
                String selectedType = targetList.get(random.nextInt(targetList.size()));
                
                // Tạo action target tạm thời và đệ quy
                DisasterAction.ActionTarget tempTarget = new DisasterAction.ActionTarget();
                tempTarget.entityType = selectedType.trim();
                tempTarget.weight = weight;
                tempTarget.radius = radius;
                tempTarget.worlds = target.worlds;
                tempTarget.blockMaterials = target.blockMaterials;
                
                DisasterAction tempAction = new DisasterAction(action.type);
                tempAction.targets.add(tempTarget);
                tempAction.condition = action.condition;
                result.addAll(getAffectedTargets(tempAction));
            }
        }

        // Giới hạn số lượng target để tránh lag
        int maxTargets = getParamInt(action.params, "max-targets", 50);
        if (result.size() > maxTargets) {
            // Random subset
            Collections.shuffle(result, random);
            result = result.subList(0, maxTargets);
        }

        return result;
    }

    /**
     * Lấy vị trí của target
     */
    private Location getTargetLocation(Object target) {
        if (target instanceof Player p) return p.getLocation();
        if (target instanceof LivingEntity le) return le.getLocation();
        if (target instanceof Block b) return b.getLocation();
        if (target instanceof Location loc) return loc;
        if (target instanceof Entity e) return e.getLocation();
        return null;
    }

    /**
     * Kiểm tra entity có phải friendly mob không
     */
    private boolean isFriendlyMob(LivingEntity entity) {
        for (Class<? extends Entity> clazz : FRIENDLY_MOB_CLASSES) {
            if (clazz.isInstance(entity)) return true;
        }
        return false;
    }

    // ===== HELPERS =====

    private Location getRandomLocationAround(Location center, int radius) {
        int x = center.getBlockX() + random.nextInt(radius * 2) - radius;
        int z = center.getBlockZ() + random.nextInt(radius * 2) - radius;
        int y = center.getWorld().getHighestBlockYAt(x, z) + 1;
        return new Location(center.getWorld(), x, y, z);
    }

    private void spawnMobAt(Location location, String entityTypeStr, int radius, Map<?, ?> mobData) {
        try {
            EntityType entityType = EntityType.valueOf(entityTypeStr.toUpperCase());
            Location spawnLoc = getRandomLocationAround(location, radius);
            Entity entity = location.getWorld().spawnEntity(spawnLoc, entityType);
            
            // Apply potion effects from mob config
            @SuppressWarnings("unchecked")
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

    private void strikeLightningAt(Location loc, float explosionPower, boolean fire, boolean breakBlocks, double damageMultiplier) {
        loc.getWorld().strikeLightningEffect(new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY() + 30, loc.getBlockZ()));
        if (explosionPower > 0) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    loc.getWorld().createExplosion(loc, explosionPower, fire, breakBlocks);
                    // Damage entities in radius
                    for (Entity e : loc.getWorld().getNearbyEntities(loc, 8, 8, 8)) {
                        if (e instanceof LivingEntity le) {
                            double dmg = 8.0 * damageMultiplier * (1.0 - le.getLocation().distance(loc) / 8.0);
                            if (dmg > 0) {
                                le.damage(dmg);
                            }
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