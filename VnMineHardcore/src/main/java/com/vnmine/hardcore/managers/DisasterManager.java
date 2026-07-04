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
    private BossBar warningBar;
    private int warningTimeLeft = 0;
    private int timeSinceLastDisaster = 0;

    private final Map<String, Runnable> disasterMap = new LinkedHashMap<>();

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
        registerDisasters();
        if (config.disastersEnabled) start();
    }

    private void registerDisasters() {
        disasterMap.put("blood-moon", this::startBloodMoon);
        disasterMap.put("meteor", this::startMeteorShower);
        disasterMap.put("mega-storm", this::startMegaStorm);
        disasterMap.put("solar-flare", this::startSolarFlare);
        disasterMap.put("plague", this::startPlague);
        disasterMap.put("tornado", this::startTornado);
        disasterMap.put("eclipse", this::startSolarEclipse);
        disasterMap.put("earthquake", this::startEarthquake);
        disasterMap.put("inferno-storm", this::startInfernoStorm);
        disasterMap.put("soul-eruption", this::startSoulEruption);
        disasterMap.put("lava-geyser", this::startLavaGeyser);
        disasterMap.put("end-surge", this::startEndSurge);
        disasterMap.put("void-storm", this::startVoidStorm);
        disasterMap.put("chorus-explosion", this::startChorusExplosion);
    }

    public Set<String> getDisasterIds() { return disasterMap.keySet(); }

    public String getDisasterName(String id) {
        ConfigManager.DisasterConfig dc = config.disasterConfigs.get(id.toLowerCase());
        if (dc != null && dc.name != null && !dc.name.isEmpty()) {
            return dc.name;
        }
        // Fallback to hardcoded names
        return switch (id.toLowerCase()) {
            case "blood-moon" -> "🌕 Blood Moon";
            case "meteor" -> "☄️ Meteor Shower";
            case "mega-storm" -> "🌊 Mega Storm";
            case "solar-flare" -> "🔥 Solar Flare";
            case "plague" -> "🦠 Plague";
            case "tornado" -> "🌪️ Tornado";
            case "eclipse" -> "📉 Solar Eclipse";
            case "earthquake" -> "🌍 Earthquake";
            case "inferno-storm" -> "🔥 Inferno Storm";
            case "soul-eruption" -> "💀 Soul Eruption";
            case "lava-geyser" -> "🌋 Lava Geyser";
            case "end-surge" -> "👁️ End Surge";
            case "void-storm" -> "🌌 Void Storm";
            case "chorus-explosion" -> "🌀 Chorus Explosion";
            default -> id;
        };
    }

    /**
     * Kiểm tra xem player có đang ở nơi an toàn (có mái che) không.
     * Kiểm tra block ngay trên đầu player và các block xung quanh.
     */
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

    /**
     * Gửi thông báo an toàn/không an toàn cho player dựa vào trạng thái hiện tại.
     */
    private void sendSafeZoneMessage(Player player) {
        if (!config.safeZoneEnabled) return;
        
        if (isPlayerSafe(player)) {
            player.sendActionBar("§a§l🏠 Bạn đang an toàn trong nhà!");
        } else {
            if (disasterActive) {
                player.sendActionBar("§c§l⚠ Bạn đang ở ngoài trời! Vào nhà ngay!");
            }
        }
    }

    public boolean triggerDisaster(String disasterId, int warningTimeSeconds, int durationSeconds) {
        if (disasterActive) return false;
        Runnable task = disasterMap.get(disasterId.toLowerCase());
        if (task == null) return false;

        String name = getDisasterName(disasterId);
        currentDisaster = name;
        warningTimeLeft = warningTimeSeconds;

        String warningTitle = config.disasterMessages.getOrDefault("warning-title", "§4§l⚠ CẢNH BÁO THIÊN TAI ⚠");
        String warningSubtitle = config.disasterMessages.getOrDefault("warning-subtitle", "§c{name}\n§e§lSẽ xảy ra trong {time} giây!");
        String warningBroadcast = config.disasterMessages.getOrDefault("warning-broadcast", "§4§l⚠ {name} §r§cđang đến gần!");

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(warningBar);
            p.sendTitle(warningTitle,
                warningSubtitle.replace("{name}", name).replace("{time}", String.valueOf(warningTimeSeconds)), 10, 70, 20);
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 0.5f);
            p.sendMessage(warningBroadcast.replace("{name}", name));
        }
        logManager.logDisaster(name + " (MANUAL - Warning " + warningTimeSeconds + "s)");

        new BukkitRunnable() {
            int cd = warningTimeSeconds;
            @Override
            public void run() {
                cd--;
                if (cd <= 0) { this.cancel(); task.run(); return; }
                if (cd <= 5 || cd == 10 || cd == 30) {
                    String countdownMsg = config.disasterMessages.getOrDefault("countdown-broadcast", 
                        "§4§l⚠ {name} §csẽ xảy ra trong §4§l{time}§c giây!");
                    broadcast(countdownMsg.replace("{name}", name).replace("{time}", String.valueOf(cd)));
                }
                // Gửi thông báo an toàn trong thời gian đếm ngược
                if (config.safeZoneEnabled && cd % 5 == 0) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        sendSafeZoneMessage(p);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
        return true;
    }

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (Bukkit.getWorlds().isEmpty()) return;
                timeSinceLastDisaster++;
                // Use random interval from config (supports range format like "500-1200")
                int currentInterval = ConfigManager.parseRangeOrInt(config.disasterMinIntervalRaw, 1200);
                if (!disasterActive && timeSinceLastDisaster >= currentInterval) tryScheduleDisaster();
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

    private void tryScheduleDisaster() {
        int currentInterval = ConfigManager.parseRangeOrInt(config.disasterMinIntervalRaw, 1200);
        if (timeSinceLastDisaster < currentInterval) return;
        if (currentDisaster != null) return; // Already has a disaster/warning in progress

        int roll = random.nextInt(100);
        boolean isNight = Bukkit.getWorlds().get(0).getTime() > 13000;

        // Xác định dimension có player
        boolean hasNether = false, hasEnd = false, hasOverworld = false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            String env = p.getWorld().getEnvironment().name();
            if ("NETHER".equals(env)) hasNether = true;
            else if ("THE_END".equals(env)) hasEnd = true;
            else hasOverworld = true;
        }

        // Overworld events
        if (hasOverworld) {
            // Blood Moon - chỉ ban đêm
            ConfigManager.DisasterConfig bloodMoonConfig = config.disasterConfigs.get("blood-moon");
            int bloodMoonChance = bloodMoonConfig != null ? bloodMoonConfig.chance : 10;
            boolean bloodMoonEnabled = bloodMoonConfig != null && bloodMoonConfig.enabled;

            if (roll < bloodMoonChance) {
                if (isNight && bloodMoonEnabled) {
                    timeSinceLastDisaster = 0;
                    scheduleDisaster(getDisasterName("blood-moon"), this::startBloodMoon);
                    return;
                } else {
                    // Ban ngày → thay thế bằng Solar Flare hoặc Eclipse ngẫu nhiên
                    timeSinceLastDisaster = 0;
                    ConfigManager.DisasterConfig solarFlareConfig = config.disasterConfigs.get("solar-flare");
                    ConfigManager.DisasterConfig eclipseConfig = config.disasterConfigs.get("eclipse");
                    boolean solarFlareEnabled = solarFlareConfig != null && solarFlareConfig.enabled;
                    boolean eclipseEnabled = eclipseConfig != null && eclipseConfig.enabled;

                    if (random.nextBoolean() && solarFlareEnabled) {
                        scheduleDisaster(getDisasterName("solar-flare"), this::startSolarFlare);
                    } else if (eclipseEnabled) {
                        scheduleDisaster(getDisasterName("eclipse"), this::startSolarEclipse);
                    }
                    return;
                }
            }
            roll -= bloodMoonChance;

            // Meteor Shower
            ConfigManager.DisasterConfig meteorConfig = config.disasterConfigs.get("meteor");
            int meteorChance = meteorConfig != null ? meteorConfig.chance : 5;
            boolean meteorEnabled = meteorConfig != null && meteorConfig.enabled;
            if (roll < meteorChance && meteorEnabled) { 
                timeSinceLastDisaster = 0; 
                scheduleDisaster(getDisasterName("meteor"), this::startMeteorShower); 
                return; 
            }
            roll -= meteorChance;

            // Mega Storm
            ConfigManager.DisasterConfig megaStormConfig = config.disasterConfigs.get("mega-storm");
            int megaStormChance = megaStormConfig != null ? megaStormConfig.chance : 5;
            boolean megaStormEnabled = megaStormConfig != null && megaStormConfig.enabled;
            if (roll < megaStormChance && megaStormEnabled) { 
                timeSinceLastDisaster = 0; 
                scheduleDisaster(getDisasterName("mega-storm"), this::startMegaStorm); 
                return; 
            }
            roll -= megaStormChance;

            // Solar Flare - chỉ ban ngày
            ConfigManager.DisasterConfig solarFlareConfig2 = config.disasterConfigs.get("solar-flare");
            int solarFlareChance = solarFlareConfig2 != null ? solarFlareConfig2.chance : 3;
            boolean solarFlareEnabled2 = solarFlareConfig2 != null && solarFlareConfig2.enabled;
            if (roll < solarFlareChance) {
                if (!isNight && solarFlareEnabled2) {
                    timeSinceLastDisaster = 0;
                    scheduleDisaster(getDisasterName("solar-flare"), this::startSolarFlare);
                    return;
                } else {
                    // Ban đêm → thay thế bằng event ngẫu nhiên khác (trừ bloodmoon)
                    timeSinceLastDisaster = 0;
                    scheduleRandomOverworldDisasterExcluding("🔥 Solar Flare", "📉 Solar Eclipse");
                    return;
                }
            }
            roll -= solarFlareChance;

            // Plague
            ConfigManager.DisasterConfig plagueConfig = config.disasterConfigs.get("plague");
            int plagueChance = plagueConfig != null ? plagueConfig.chance : 2;
            boolean plagueEnabled = plagueConfig != null && plagueConfig.enabled;
            if (roll < plagueChance && plagueEnabled) { 
                timeSinceLastDisaster = 0; 
                scheduleDisaster(getDisasterName("plague"), this::startPlague); 
                return; 
            }
            roll -= plagueChance;

            // Tornado
            ConfigManager.DisasterConfig tornadoConfig = config.disasterConfigs.get("tornado");
            int tornadoChance = tornadoConfig != null ? tornadoConfig.chance : 2;
            boolean tornadoEnabled = tornadoConfig != null && tornadoConfig.enabled;
            if (roll < tornadoChance && tornadoEnabled) { 
                timeSinceLastDisaster = 0; 
                scheduleDisaster(getDisasterName("tornado"), this::startTornado); 
                return; 
            }
            roll -= tornadoChance;

            // Solar Eclipse - chỉ ban ngày
            ConfigManager.DisasterConfig eclipseConfig2 = config.disasterConfigs.get("eclipse");
            int eclipseChance = eclipseConfig2 != null ? eclipseConfig2.chance : 1;
            boolean eclipseEnabled2 = eclipseConfig2 != null && eclipseConfig2.enabled;
            if (roll < eclipseChance) {
                if (!isNight && eclipseEnabled2) {
                    timeSinceLastDisaster = 0;
                    scheduleDisaster(getDisasterName("eclipse"), this::startSolarEclipse);
                    return;
                } else {
                    // Ban đêm → thay thế bằng event ngẫu nhiên khác
                    timeSinceLastDisaster = 0;
                    scheduleRandomOverworldDisasterExcluding("📉 Solar Eclipse", "🔥 Solar Flare");
                    return;
                }
            }
            roll -= eclipseChance;

            // Earthquake
            ConfigManager.DisasterConfig earthquakeConfig = config.disasterConfigs.get("earthquake");
            int earthquakeChance = earthquakeConfig != null ? earthquakeConfig.chance : 2;
            boolean earthquakeEnabled = earthquakeConfig != null && earthquakeConfig.enabled;
            if (roll < earthquakeChance && earthquakeEnabled) { 
                timeSinceLastDisaster = 0; 
                scheduleDisaster(getDisasterName("earthquake"), this::startEarthquake); 
                return; 
            }
            roll -= earthquakeChance;
        }

        // Nether events
        if (hasNether) {
            ConfigManager.DisasterConfig infernoConfig = config.disasterConfigs.get("inferno-storm");
            int infernoChance = infernoConfig != null ? infernoConfig.chance : 3;
            boolean infernoEnabled = infernoConfig != null && infernoConfig.enabled;
            if (roll < infernoChance && infernoEnabled) { 
                timeSinceLastDisaster = 0; 
                scheduleDisaster(getDisasterName("inferno-storm"), this::startInfernoStorm); 
                return; 
            }
            roll -= infernoChance;

            ConfigManager.DisasterConfig soulConfig = config.disasterConfigs.get("soul-eruption");
            int soulChance = soulConfig != null ? soulConfig.chance : 2;
            boolean soulEnabled = soulConfig != null && soulConfig.enabled;
            if (roll < soulChance && soulEnabled) { 
                timeSinceLastDisaster = 0; 
                scheduleDisaster(getDisasterName("soul-eruption"), this::startSoulEruption); 
                return; 
            }
            roll -= soulChance;

            ConfigManager.DisasterConfig lavaConfig = config.disasterConfigs.get("lava-geyser");
            int lavaChance = lavaConfig != null ? lavaConfig.chance : 2;
            boolean lavaEnabled = lavaConfig != null && lavaConfig.enabled;
            if (roll < lavaChance && lavaEnabled) { 
                timeSinceLastDisaster = 0; 
                scheduleDisaster(getDisasterName("lava-geyser"), this::startLavaGeyser); 
                return; 
            }
            roll -= lavaChance;
        }

        // End events
        if (hasEnd) {
            ConfigManager.DisasterConfig endSurgeConfig = config.disasterConfigs.get("end-surge");
            int endSurgeChance = endSurgeConfig != null ? endSurgeConfig.chance : 2;
            boolean endSurgeEnabled = endSurgeConfig != null && endSurgeConfig.enabled;
            if (roll < endSurgeChance && endSurgeEnabled) { 
                timeSinceLastDisaster = 0; 
                scheduleDisaster(getDisasterName("end-surge"), this::startEndSurge); 
                return; 
            }
            roll -= endSurgeChance;

            ConfigManager.DisasterConfig voidConfig = config.disasterConfigs.get("void-storm");
            int voidChance = voidConfig != null ? voidConfig.chance : 2;
            boolean voidEnabled = voidConfig != null && voidConfig.enabled;
            if (roll < voidChance && voidEnabled) { 
                timeSinceLastDisaster = 0; 
                scheduleDisaster(getDisasterName("void-storm"), this::startVoidStorm); 
                return; 
            }
            roll -= voidChance;

            ConfigManager.DisasterConfig chorusConfig = config.disasterConfigs.get("chorus-explosion");
            int chorusChance = chorusConfig != null ? chorusConfig.chance : 1;
            boolean chorusEnabled = chorusConfig != null && chorusConfig.enabled;
            if (roll < chorusChance && chorusEnabled) { 
                timeSinceLastDisaster = 0; 
                scheduleDisaster(getDisasterName("chorus-explosion"), this::startChorusExplosion); 
                return; 
            }
            roll -= chorusChance;
        }
    }

    /**
     * Chọn ngẫu nhiên một overworld disaster khác, loại trừ các disaster được chỉ định
     */
    private void scheduleRandomOverworldDisasterExcluding(String... excludeNames) {
        List<Runnable> available = new ArrayList<>();
        List<String> availableNames = new ArrayList<>();

        // Danh sách tất cả overworld disasters
        java.util.Map<String, Runnable> overworldDisasters = new java.util.LinkedHashMap<>();
        overworldDisasters.put("☄️ Meteor Shower", this::startMeteorShower);
        overworldDisasters.put("🌊 Mega Storm", this::startMegaStorm);
        overworldDisasters.put("🔥 Solar Flare", this::startSolarFlare);
        overworldDisasters.put("🦠 Plague", this::startPlague);
        overworldDisasters.put("🌪️ Tornado", this::startTornado);
        overworldDisasters.put("📉 Solar Eclipse", this::startSolarEclipse);
        overworldDisasters.put("🌍 Earthquake", this::startEarthquake);

        for (java.util.Map.Entry<String, Runnable> entry : overworldDisasters.entrySet()) {
            boolean excluded = false;
            for (String ex : excludeNames) {
                if (entry.getKey().equals(ex)) {
                    excluded = true;
                    break;
                }
            }
            if (!excluded) {
                available.add(entry.getValue());
                availableNames.add(entry.getKey());
            }
        }

        if (!available.isEmpty()) {
            int idx = random.nextInt(available.size());
            scheduleDisaster(availableNames.get(idx), available.get(idx));
        } else {
            // Fallback: meteor shower
            scheduleDisaster("☄️ Meteor Shower", this::startMeteorShower);
        }
    }

    private void scheduleDisaster(String name, Runnable disasterTask) {
        currentDisaster = name;
        warningTimeLeft = config.disasterWarningSeconds;
        
        String warningTitle = config.disasterMessages.getOrDefault("warning-title", "§4§l⚠ CẢNH BÁO THIÊN TAI ⚠");
        String warningBroadcast = config.disasterMessages.getOrDefault("warning-broadcast", "§4§l⚠ {name} §r§cđang đến gần!");
        
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(warningBar);
            p.sendTitle(warningTitle,
                "§c" + name + "\n§e§lSẽ xảy ra trong " + (config.disasterWarningSeconds / 60) + " phút!", 10, 70, 20);
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 0.5f);
            p.sendMessage(warningBroadcast.replace("{name}", name));
        }
        logManager.logDisaster(name + " (WARNING)");

        new BukkitRunnable() {
            int cd = config.disasterWarningSeconds;
            @Override
            public void run() {
                cd--;
                if (cd <= 0) { this.cancel(); disasterTask.run(); return; }
                if (cd == 60 || cd == 30 || cd == 10 || cd <= 5) {
                    String countdownMsg = config.disasterMessages.getOrDefault("countdown-broadcast",
                        "§4§l⚠ {name} §csẽ xảy ra trong §4§l{time}§c giây!");
                    broadcast(countdownMsg.replace("{name}", currentDisaster).replace("{time}", String.valueOf(cd)));
                }
                // Gửi thông báo an toàn trong thời gian đếm ngược (mỗi 5 giây)
                if (config.safeZoneEnabled && cd % 5 == 0) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        sendSafeZoneMessage(p);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void startDisaster(String name, String startMsg, int durationTicks, Runnable duringTask, Runnable endTask) {
        disasterActive = true;
        currentDisaster = name;
        warningBar.name(Component.text("§4§l⚠ " + name + " ĐANG HOẠT ĐỘNG ⚠"));
        warningBar.progress(1.0f);
        
        // Lấy custom title/subtitle từ config nếu có
        String configKey = getConfigKeyFromName(name);
        ConfigManager.DisasterConfig dc = config.disasterConfigs.get(configKey);
        String startTitle = (dc != null && !dc.startTitle.isEmpty()) ? dc.startTitle : "§4§l" + name;
        String startSubtitle = (dc != null && !dc.startSubtitle.isEmpty()) ? dc.startSubtitle : "§c" + startMsg;
        
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(warningBar);
            p.sendTitle(startTitle, startSubtitle, 10, 60, 20);
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);
        }
        logManager.logDisaster(name + " (ACTIVE)");
        
        String activeBroadcast = config.disasterMessages.getOrDefault("active-broadcast", 
            "§4§l{name} - {message} (§e{duration}s§4)");
        String endBroadcast = config.disasterMessages.getOrDefault("end-broadcast", 
            "§a§l✅ {name} đã kết thúc!");
        
        broadcast(activeBroadcast.replace("{name}", name).replace("{message}", startMsg).replace("{duration}", String.valueOf(durationTicks / 20)));

        int effectIntervalTicks = (dc != null ? dc.effectIntervalSeconds : 5) * 20;
        int effectDurationTicks = (dc != null ? dc.effectDurationSeconds : 5) * 20;

        new BukkitRunnable() {
            int elapsed = 0;
            int tickCounter = 0;
            @Override
            public void run() {
                if (elapsed >= durationTicks) { this.cancel(); return; }
                warningBar.progress(1.0f - (float) elapsed / durationTicks);
                tickCounter++;
                if (tickCounter >= effectIntervalTicks / 20) {
                    tickCounter = 0;
                    duringTask.run();
                }
                // Gửi thông báo an toàn mỗi 5 giây trong khi thiên tai đang hoạt động
                if (config.safeZoneEnabled && tickCounter % 5 == 0) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        sendSafeZoneMessage(p);
                    }
                }
                elapsed += 20;
            }
        }.runTaskTimer(plugin, 0L, 20L);

        new BukkitRunnable() {
            @Override
            public void run() {
                disasterActive = false;
                currentDisaster = null;
                warningBar.name(Component.text(""));
                warningBar.progress(0);
                for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(warningBar);
                if (endTask != null) endTask.run();
                broadcast(endBroadcast.replace("{name}", name));
            }
        }.runTaskLater(plugin, durationTicks);
    }

    /**
     * Get config key from disaster display name
     */
    private String getConfigKeyFromName(String name) {
        if (name.contains("Blood")) return "blood-moon";
        if (name.contains("Meteor")) return "meteor";
        if (name.contains("Mega")) return "mega-storm";
        if (name.contains("Solar") && !name.contains("Eclipse")) return "solar-flare";
        if (name.contains("Plague")) return "plague";
        if (name.contains("Tornado")) return "tornado";
        if (name.contains("Eclipse")) return "eclipse";
        if (name.contains("Earthquake")) return "earthquake";
        if (name.contains("Inferno")) return "inferno-storm";
        if (name.contains("Soul")) return "soul-eruption";
        if (name.contains("Lava")) return "lava-geyser";
        if (name.contains("End Surge")) return "end-surge";
        if (name.contains("Void")) return "void-storm";
        if (name.contains("Chorus")) return "chorus-explosion";
        return "blood-moon";
    }

    private void updateWarningBar() {
        if (warningTimeLeft > 0) {
            warningBar.name(Component.text("§4§l⚠ " + currentDisaster + " §7- §e§l" + (warningTimeLeft / 60) + ":" + String.format("%02d", warningTimeLeft % 60)));
            warningBar.progress((float) warningTimeLeft / 300f);
        }
    }

    // ============ EXISTING DISASTERS ============

    private void startBloodMoon() {
        ConfigManager.DisasterConfig dc = config.disasterConfigs.get("blood-moon");
        int duration = (dc != null ? dc.durationSeconds : 600) * 20;

        startDisaster("🌕 Blood Moon", "Máu trăng lên!", duration,
            () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    // Bỏ qua player an toàn trong nhà
                    if (isPlayerSafe(p)) continue;
                    
                    if (random.nextInt(10) < 3) {
                        Location loc = p.getLocation();
                        World w = p.getWorld();
                        for (int i = 0; i < 3; i++) {
                            Location sl = loc.clone().add(random.nextInt(20) - 10, 0, random.nextInt(20) - 10);
                            sl.setY(w.getHighestBlockYAt(sl) + 1);
                            EntityType[] mobs = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER};
                            Entity e = w.spawnEntity(sl, mobs[random.nextInt(mobs.length)]);
                            if (e instanceof Mob m) {
                                m.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 600, 1));
                                m.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 0));
                            }
                        }
                    }
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false));
                }
            }, () -> broadcast("§6Mặt trăng đã trở lại bình thường."));
    }

    private void startMeteorShower() {
        ConfigManager.DisasterConfig dc = config.disasterConfigs.get("meteor");
        int duration = (dc != null ? dc.durationSeconds : 400) * 20;

        startDisaster("☄️ Meteor Shower", "Mưa sao băng!", duration,
            () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    // Bỏ qua player an toàn trong nhà
                    if (isPlayerSafe(p)) continue;
                    
                    if (random.nextInt(5) < 2) {
                        Location pl = p.getLocation();
                        World w = p.getWorld();
                        for (int i = 0; i < 2 + random.nextInt(3); i++) {
                            int x = pl.getBlockX() + random.nextInt(30) - 15;
                            int z = pl.getBlockZ() + random.nextInt(30) - 15;
                            Location il = new Location(w, x, w.getHighestBlockYAt(x, z), z);
                            w.strikeLightningEffect(new Location(w, x, pl.getBlockY() + 30, z));
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    w.createExplosion(il, 3.0f, false, true);
                                    for (Player pl2 : Bukkit.getOnlinePlayers()) {
                                        if (pl2.getLocation().distance(il) < 8) {
                                            double dmg = 8.0 * (1.0 - pl2.getLocation().distance(il) / 8.0);
                                            pl2.damage(dmg);
                                        }
                                    }
                                }
                            }.runTaskLater(plugin, 30L);
                        }
                    }
                }
            }, () -> broadcast("§aBầu trời đã trở lại bình thường."));
    }

    private void startMegaStorm() {
        for (World w : Bukkit.getWorlds()) { w.setStorm(true); w.setThundering(true); w.setWeatherDuration(200000); }
        ConfigManager.DisasterConfig dc = config.disasterConfigs.get("mega-storm");
        int duration = (dc != null ? dc.durationSeconds : 600) * 20;

        startDisaster("🌊 Mega Storm", "Siêu bão đổ bộ!", duration,
            () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    Location l = p.getLocation();
                    
                    // Bỏ qua player an toàn trong nhà
                    if (isPlayerSafe(p)) continue;
                    
                    if (random.nextInt(5) < 2) {
                        Location sl = l.clone().add(random.nextInt(10) - 5, 0, random.nextInt(10) - 5);
                        sl.setY(l.getWorld().getHighestBlockYAt(sl));
                        l.getWorld().strikeLightning(sl);
                        if (p.getLocation().distance(sl) < 4) p.damage(4.0);
                    }
                    if (l.getBlock().getLightFromSky() > 10 && random.nextInt(5) < 2) p.damage(1.0);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1));
                }
            }, () -> {
                for (World w : Bukkit.getWorlds()) { w.setStorm(false); w.setThundering(false); }
                broadcast("§aBão đã tan.");
            });
    }

    private void startSolarFlare() {
        ConfigManager.DisasterConfig dc = config.disasterConfigs.get("solar-flare");
        int duration = (dc != null ? dc.durationSeconds : 400) * 20;

        startDisaster("🔥 Solar Flare", "Bão mặt trời!", duration,
            () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    // Bỏ qua player an toàn trong nhà
                    if (isPlayerSafe(p)) continue;
                    
                    if (p.getLocation().getBlock().getLightFromSky() > 10) {
                        p.damage(2.0); p.setFireTicks(40);
                        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 2));
                    }
                }
            }, () -> broadcast("§aBão mặt trời đã qua."));
    }

    private void startPlague() {
        ConfigManager.DisasterConfig dc = config.disasterConfigs.get("plague");
        int duration = (dc != null ? dc.durationSeconds : 400) * 20;

        startDisaster("🦠 Plague", "Dịch bệnh lan rộng!", duration,
            () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    // Bỏ qua player an toàn trong nhà
                    if (isPlayerSafe(p)) continue;
                    
                    p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 2));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 100, 3));
                }
            }, () -> broadcast("§aDịch bệnh đã được kiểm soát."));
    }

    private void startTornado() {
        ConfigManager.DisasterConfig dc = config.disasterConfigs.get("tornado");
        int duration = (dc != null ? dc.durationSeconds : 300) * 20;

        startDisaster("🌪️ Tornado", "Lốc xoáy!", duration,
            () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    // Bỏ qua player an toàn trong nhà
                    if (isPlayerSafe(p)) continue;
                    
                    if (p.getLocation().getBlock().getLightFromSky() > 5) {
                        Vector v = p.getVelocity();
                        v.setY(v.getY() + 1.5);
                        v.setX(v.getX() + (random.nextDouble() - 0.5) * 2);
                        v.setZ(v.getZ() + (random.nextDouble() - 0.5) * 2);
                        p.setVelocity(v);
                        p.setFallDistance(0);
                    }
                }
            }, () -> broadcast("§aLốc xoáy đã tan."));
    }

    private void startSolarEclipse() {
        World w = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        long ot = w != null ? w.getTime() : 0;
        ConfigManager.DisasterConfig dc = config.disasterConfigs.get("eclipse");
        int duration = (dc != null ? dc.durationSeconds : 600) * 20;

        startDisaster("📉 Solar Eclipse", "Nhật thực toàn phần!", duration,
            () -> {
                if (w != null && w.getTime() < 13000) w.setTime(13000);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 2));
                    
                    // Bỏ qua player an toàn trong nhà cho việc spawn mob
                    if (isPlayerSafe(p)) continue;
                    
                    if (random.nextInt(10) < 3) {
                        Location sl = p.getLocation().clone().add(random.nextInt(15) - 7, 0, random.nextInt(15) - 7);
                        sl.setY(p.getWorld().getHighestBlockYAt(sl) + 1);
                        EntityType[] mobs = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, EntityType.SPIDER};
                        p.getWorld().spawnEntity(sl, mobs[random.nextInt(mobs.length)]);
                    }
                }
            }, () -> { if (w != null) w.setTime(Math.max(ot, 1000)); broadcast("§aNhật thực kết thúc!"); });
    }

    private void startEarthquake() {
        ConfigManager.DisasterConfig dc = config.disasterConfigs.get("earthquake");
        int duration = (dc != null ? dc.durationSeconds : 400) * 20;

        startDisaster("🌍 Earthquake", "Động đất dữ dội!", duration,
            () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    Location l = p.getLocation();
                    
                    // Bỏ qua player an toàn trong nhà
                    if (isPlayerSafe(p)) continue;
                    
                    // Hiệu ứng rung chuyển (shake)
                    p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 30, 0));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1));
                    p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);
                    
                    int radius = (dc != null ? dc.radius : 15);
                    int minY = (dc != null ? dc.minY : 30);
                    int chance = (dc != null ? dc.blockFallChance : 15);
                    double resistanceFactor = (dc != null ? dc.blastResistanceFactor : 0.1);

                    for (int i = 0; i < 5; i++) {
                        int bx = l.getBlockX() + random.nextInt(radius * 2) - radius;
                        int bz = l.getBlockZ() + random.nextInt(radius * 2) - radius;
                        int by = minY + random.nextInt(60);
                        Block block = new Location(p.getWorld(), bx, by, bz).getBlock();
                        Material bt = block.getType();
                        if (bt == Material.AIR || bt == Material.WATER || bt == Material.LAVA || bt == Material.BEDROCK) continue;

                        // Block có blast resistance càng cao càng khó rơi
                        double blastRes = bt.getBlastResistance();
                        double fallProb = chance / (1.0 + blastRes * resistanceFactor);
                        if (random.nextDouble() * 100 < fallProb) {
                            FallingBlock fb = p.getWorld().spawnFallingBlock(block.getLocation().add(0.5, 0, 0.5), block.getBlockData());
                            fb.setDropItem(true);
                            fb.setHurtEntities(true);
                            block.setType(Material.AIR);
                        }
                    }
                }
            }, () -> broadcast("§aĐộng đất đã kết thúc."));
    }

    // ============ NETHER DISASTERS ============

    private void startInfernoStorm() {
        ConfigManager.DisasterConfig dc = config.disasterConfigs.get("inferno-storm");
        int duration = (dc != null ? dc.durationSeconds : 400) * 20;
        double damage = (dc != null ? dc.damage : 2.0);
        int fireTicks = (dc != null ? dc.fireTicks : 100);

        startDisaster("🔥 Inferno Storm", "Lửa địa ngục trút xuống!", duration,
            () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.getWorld().getEnvironment().equals(World.Environment.NETHER)) continue;
                    // Bỏ qua player an toàn trong nhà
                    if (isPlayerSafe(p)) continue;
                    
                    Location l = p.getLocation();
                    p.damage(damage);
                    p.setFireTicks(fireTicks);

                    if (random.nextInt(10) < 3) {
                        p.getWorld().spawnEntity(l, random.nextBoolean() ? EntityType.GHAST : EntityType.MAGMA_CUBE);
                    }

                    // Lửa rơi từ trần nether
                    for (int i = 0; i < 3; i++) {
                        int fx = l.getBlockX() + random.nextInt(20) - 10;
                        int fz = l.getBlockZ() + random.nextInt(20) - 10;
                        int fy = Math.min(l.getBlockY() + 20, p.getWorld().getMaxHeight() - 1);
                        Location fl = new Location(p.getWorld(), fx, fy, fz);
                        if (fl.getBlock().getType() == Material.AIR) {
                            fl.getBlock().setType(Material.FIRE);
                        }
                    }
                    p.sendActionBar("§4§l🔥 INFERNO! §cLửa địa ngục!");
                }
            }, () -> broadcast("§aInferno Storm đã kết thúc."));
    }

    private void startSoulEruption() {
        ConfigManager.DisasterConfig dc = config.disasterConfigs.get("soul-eruption");
        int duration = (dc != null ? dc.durationSeconds : 400) * 20;
        double damage = (dc != null ? dc.damage : 2.0);
        int witherAmplifier = (dc != null ? dc.witherAmplifier : 1);

        startDisaster("💀 Soul Eruption", "Soul sand phát nổ!", duration,
            () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.getWorld().getEnvironment().equals(World.Environment.NETHER)) continue;
                    // Bỏ qua player an toàn trong nhà
                    if (isPlayerSafe(p)) continue;
                    
                    p.damage(damage);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, witherAmplifier));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));

                    if (random.nextInt(10) < 3) {
                        Location sl = p.getLocation().clone().add(random.nextInt(10) - 5, 0, random.nextInt(10) - 5);
                        p.getWorld().spawnEntity(sl, EntityType.WITHER_SKELETON);
                    }

                    // Nổ soul sand gần player
                    if (random.nextInt(10) < 2) {
                        int ex = p.getLocation().getBlockX() + random.nextInt(8) - 4;
                        int ez = p.getLocation().getBlockZ() + random.nextInt(8) - 4;
                        Location el = new Location(p.getWorld(), ex, p.getLocation().getBlockY(), ez);
                        p.getWorld().createExplosion(el, 2.0f, false, true);
                    }
                    p.sendActionBar("§2§l💀 SOUL ERUPTION! §aLinh hồn đang gào thét!");
                }
            }, () -> broadcast("§aSoul Eruption đã kết thúc."));
    }

    private void startLavaGeyser() {
        ConfigManager.DisasterConfig dc = config.disasterConfigs.get("lava-geyser");
        int duration = (dc != null ? dc.durationSeconds : 300) * 20;
        double damage = (dc != null ? dc.damage : 3.0);

        startDisaster("🌋 Lava Geyser", "Lava phun trào!", duration,
            () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.getWorld().getEnvironment().equals(World.Environment.NETHER)) continue;
                    // Bỏ qua player an toàn trong nhà
                    if (isPlayerSafe(p)) continue;
                    
                    p.damage(damage);
                    p.setFireTicks(60);

                    // Cột lava từ dưới đất
                    if (random.nextInt(10) < 3) {
                        int gx = p.getLocation().getBlockX() + random.nextInt(10) - 5;
                        int gz = p.getLocation().getBlockZ() + random.nextInt(10) - 5;
                        int gy = p.getWorld().getHighestBlockYAt(gx, gz) - 5;
                        for (int dy = 0; dy < 5; dy++) {
                            Location gl = new Location(p.getWorld(), gx, gy + dy, gz);
                            if (gl.getBlock().getType() == Material.AIR) {
                                gl.getBlock().setType(Material.LAVA);
                            }
                        }
                    }
                    p.sendActionBar("§6§l🌋 LAVA GEYSER! §eTránh xa!");
                }
            }, () -> broadcast("§aLava Geyser đã kết thúc."));
    }

    // ============ THE END DISASTERS ============

    private void startEndSurge() {
        ConfigManager.DisasterConfig dc = config.disasterConfigs.get("end-surge");
        int duration = (dc != null ? dc.durationSeconds : 400) * 20;
        int shulkerChance = (dc != null ? dc.shulkerChance : 20);

        startDisaster("👁️ End Surge", "Sinh vật End trỗi dậy!", duration,
            () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.getWorld().getEnvironment().equals(World.Environment.THE_END)) continue;
                    // Bỏ qua player an toàn trong nhà
                    if (isPlayerSafe(p)) continue;

                    if (random.nextInt(10) < 4) {
                        EntityType type = random.nextInt(100) < shulkerChance ? EntityType.SHULKER : EntityType.ENDERMITE;
                        Location sl = p.getLocation().clone().add(random.nextInt(15) - 7, 0, random.nextInt(15) - 7);
                        p.getWorld().spawnEntity(sl, type);
                    }

                    if (random.nextInt(10) < 3) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 40, 1));
                    }
                    p.sendActionBar("§5§l👁️ END SURGE! §dSinh vật trỗi dậy!");
                }
            }, () -> broadcast("§aEnd Surge đã kết thúc."));
    }

    private void startVoidStorm() {
        ConfigManager.DisasterConfig dc = config.disasterConfigs.get("void-storm");
        int duration = (dc != null ? dc.durationSeconds : 400) * 20;
        double damage = (dc != null ? dc.damage : 2.0);

        startDisaster("🌌 Void Storm", "Bão hư vô!", duration,
            () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.getWorld().getEnvironment().equals(World.Environment.THE_END)) continue;
                    // Bỏ qua player an toàn trong nhà
                    if (isPlayerSafe(p)) continue;

                    p.damage(damage);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 1));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 2));
                    p.sendActionBar("§0§l🌌 VOID STORM! §8Hư vô đang nuốt chửng!");
                }
            }, () -> broadcast("§aVoid Storm đã kết thúc."));
    }

    private void startChorusExplosion() {
        ConfigManager.DisasterConfig dc = config.disasterConfigs.get("chorus-explosion");
        int duration = (dc != null ? dc.durationSeconds : 300) * 20;
        double damage = (dc != null ? dc.damage : 1.0);

        startDisaster("🌀 Chorus Explosion", "Chorus phát nổ!", duration,
            () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.getWorld().getEnvironment().equals(World.Environment.THE_END)) continue;
                    // Bỏ qua player an toàn trong nhà
                    if (isPlayerSafe(p)) continue;

                    p.damage(damage);

                    // Teleport ngẫu nhiên
                    if (random.nextInt(10) < 3) {
                        World w = p.getWorld();
                        int tx = p.getLocation().getBlockX() + random.nextInt(30) - 15;
                        int tz = p.getLocation().getBlockZ() + random.nextInt(30) - 15;
                        int ty = w.getHighestBlockYAt(tx, tz) + 1;
                        p.teleport(new Location(w, tx, ty, tz));
                        p.sendMessage("§d🌀 Bạn bị teleport!");
                    }

                    p.sendActionBar("§d§l🌀 CHORUS EXPLOSION!");
                }
            }, () -> broadcast("§aChorus Explosion đã kết thúc."));
    }

    private void broadcast(String msg) { Bukkit.broadcastMessage(msg); }
    public boolean isDisasterActive() { return disasterActive; }
    public String getCurrentDisaster() { return currentDisaster; }
}