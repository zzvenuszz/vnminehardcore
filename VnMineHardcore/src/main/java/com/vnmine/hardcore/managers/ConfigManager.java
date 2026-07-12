package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class ConfigManager {

    private static final Random RANDOM = ThreadLocalRandom.current();

    // Utility: parse range string like "500-1200" and return random value in that range
    public static int parseRangeOrInt(String value, int defaultValue) {
        if (value == null) return defaultValue;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return defaultValue;
        if (trimmed.contains("-")) {
            String[] parts = trimmed.split("-");
            try {
                int min = Integer.parseInt(parts[0].trim());
                int max = Integer.parseInt(parts[1].trim());
                if (min > max) { int temp = min; min = max; max = temp; }
                return min + RANDOM.nextInt(max - min + 1);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // Utility: get raw string from config (supports both string and int)
    public static String getStringFromConfig(ConfigurationSection section, String path, String defaultValue) {
        if (section == null) return defaultValue;
        if (section.isString(path)) return section.getString(path, defaultValue);
        if (section.isInt(path)) return String.valueOf(section.getInt(path));
        return defaultValue;
    }

    private final VnMineHardcore plugin;
    private FileConfiguration config;

    // Death
    public boolean banOnDeath;
    public boolean banIp;
    public boolean broadcastDeath;
    public boolean playSound;

    // Death Penalty
    public boolean deathPenaltyEnabled;
    public int deathPenaltyRecoverySeconds;
    public int deathPenaltyMaxStack;
    public double deathPenaltyMaxHpPerDeath;
    public double deathPenaltyMaxThirstPerDeath;
    public double deathPenaltyDamagePerDeath;
    public double deathPenaltyIncomingDamagePerDeath;
    public double deathPenaltyRegenPerDeath;
    public double deathPenaltyFoodDrainPerDeath;
    public double deathPenaltyThirstDrainPerDeath;

    // Combat
    public double mobDamageMultiplier;
    public double fallDamageMultiplier;
    public double fireDamageMultiplier;
    public double drowningDamageMultiplier;
    public double explosionDamageMultiplier;
    public double pvpDamageMultiplier;
    public double mobExtraHpMultiplier;
    public boolean disableNaturalRegen;
    public double regenMultiplier;
    public long combatTagDurationMs;
    public double enderPearlDamage;

    // Hunger
    public boolean hungerEnabled;
    public int drainIntervalSeconds;
    public double foodRestoreMultiplier;
    public double starvationDamage;
    public int sprintMinFood;
    public boolean rawFoodPoison;
    public boolean chokeEnabled;
    public int chokeMaxEats;
    public int chokeWindowSeconds;
    public double chokeDamage;
    public boolean notchAppleExplode;
    public double notchAppleDamage;

    // Thirst
    public boolean thirstEnabled;
    public int thirstDrainIntervalSeconds;
    public double thirstDamage;
    public int maxThirst;
    public boolean drinkFromSource;
    public int drinkSourceRestore;
    public int drinkSourceCooldownSeconds;
    public boolean bucketDrinkEnabled;
    public int bucketRestore;
    public boolean bottleDrinkEnabled;
    public int bottleRestore;
    public boolean naturalWaterEnabled;
    public double naturalWaterDamagePerSecond;
    public int naturalWaterDurationSeconds;
    public int naturalWaterNauseaAmplifier;
    public boolean bottleFill;

    // Environment
    public boolean temperatureEnabled;
    public int tempCheckIntervalSeconds;
    public double heatDamage;
    public int heatAmplifier;
    public double helmetReducePercent;
    public double helmetDamageReducePercent;
    public double coldDamage;
    public int coldAmplifier;
    public double armorReducePercent;
    public double armorDamageReducePercent;
    public int heatExposureDelaySeconds;
    public int coldExposureDelaySeconds;
    public long waterDamageIntervalMs;
    public long acidRainIntervalMs;
    public boolean fogEnabled;
    public int fogIntervalSeconds;
    public int fogEffectDurationSeconds;
    public int fogEffectAmplifier;
    public boolean sleepBlock;
    public double sleepDamage;
    public boolean cropSlowEnabled;
    public double cropSlowMultiplier;
    public boolean toolWearEnabled;
    public double toolWearMultiplier;
    public boolean flightBlock;
    public boolean claustrophobiaEnabled;
    public int claustrophobiaYLevel;
    public int claustrophobiaChance;
    public int claustrophobiaAmplifier;
    public double claustrophobiaTorchReducePercent;
    public boolean vertigoEnabled;
    public int vertigoYLevel;
    public int vertigoChance;
    public int vertigoAmplifier;
    public double vertigoElytraReducePercent;

    // Villager Trading
    public boolean villagerTradingEnabled;
    public boolean disableRandomVillager;
    public int villagerRegionSize;

    // Spawner Control
    public boolean spawnerControlEnabled;
    public double spawnerSpawnRateReduction;
    public double spawnerHpMultiplier;
    public double spawnerDamageMultiplier;

    // Disasters - General settings (loaded from disasters/_settings.yml)
    public boolean disastersEnabled;
    public String disasterMinIntervalRaw;
    public int disasterWarningSeconds;

    // Safe Zone settings (loaded from disasters/_settings.yml)
    public boolean safeZoneEnabled;
    public int safeZoneCheckRadius;
    public int safeZoneRoofHeight;
    public boolean safeZoneCheckWalls;
    public int safeZoneMinWalls;

    // Disaster config map - each disaster is a complete object
    public Map<String, DisasterConfig> disasterConfigs = new HashMap<>();

    // Disaster messages (shared across all disasters, loaded from disasters/_settings.yml)
    public Map<String, String> disasterMessages = new HashMap<>();

    // Boss Events - General settings (loaded from bosses/_settings.yml)
    public boolean bossEventsEnabled;
    public String bossEventMinIntervalRaw;
    public int bossEventSpawnRadius;

    // Boss config map - each boss is a complete object (loaded from bosses/*.yml)
    public Map<String, BossConfig> bossConfigs = new HashMap<>();

    // Ore Control
    public boolean oreControlEnabled;
    public Map<String, Map<String, Double>> oreControlWorlds = new HashMap<>();

    // Rename
    public boolean renameEnabled;
    public String nameStructure;

    // Logging
    public boolean consoleDebug;
    public boolean logDeaths;
    public boolean logBans;
    public boolean logDisasters;

    // DisasterConfig class - holds all config for a single disaster
    public static class DisasterConfig {
        public boolean enabled = true;
        public int chance = 5;
        public String name = "Unknown Disaster";
        public int effectIntervalSeconds = 5;
        public int effectDurationSeconds = 5;
        public int durationSeconds = 600;
        public String startTitle = "";
        public String startSubtitle = "";
        public int blockFallChance = 15;
        public int radius = 15;
        public int minY = 30;
        public double blastResistanceFactor = 0.1;
        public double damage = 2.0;
        public int fireTicks = 100;
        public int witherAmplifier = 1;
        public int shulkerChance = 20;
        
        // Action engine - parsed from YAML actions: list
        public List<DisasterAction> actions = new ArrayList<>();
        public List<DisasterAction> onEnd = new ArrayList<>();
    }

    // BossConfig class - holds all config for a single boss
    public static class BossConfig {
        public boolean enabled = true;
        public String entityType = "WITHER";
        public String displayName = "§c§lBoss";
        public double hp = 100.0;
        public double damageMultiplier = 1.0;
        public int chance = 10;
        public int durationSeconds = 120;
        public int warningSeconds = 60;
        public Map<String, Boolean> immunities = new HashMap<>();
        public java.util.List<String> immunityPotionEffects = new java.util.ArrayList<>();
        public Map<String, DropConfig> drops = new HashMap<>();
    }

    // DropConfig class - holds drop configuration
    public static class DropConfig {
        public int minAmount = 1;
        public int maxAmount = 1;
        public double chance = 0.5;
    }

    public ConfigManager(VnMineHardcore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        // ===== Save default external configs (bosses/, disasters/) =====
        saveDefaultExternalConfigs();

        // Death
        banOnDeath = config.getBoolean("death.ban-on-death", true);
        banIp = config.getBoolean("death.ban-ip", true);
        broadcastDeath = config.getBoolean("death.broadcast-death", true);
        playSound = config.getBoolean("death.play-sound", true);

        // Death Penalty
        deathPenaltyEnabled = config.getBoolean("death-penalty.enabled", true);
        deathPenaltyRecoverySeconds = config.getInt("death-penalty.recovery-seconds", 300);
        deathPenaltyMaxStack = config.getInt("death-penalty.max-penalty-stack", 5);
        deathPenaltyMaxHpPerDeath = config.getDouble("death-penalty.stats.max-hp-per-death", 0.1);
        deathPenaltyMaxThirstPerDeath = config.getDouble("death-penalty.stats.max-thirst-per-death", 0.1);
        deathPenaltyDamagePerDeath = config.getDouble("death-penalty.stats.damage-per-death", 0.1);
        deathPenaltyIncomingDamagePerDeath = config.getDouble("death-penalty.stats.incoming-damage-per-death", 0.1);
        deathPenaltyRegenPerDeath = config.getDouble("death-penalty.stats.regen-per-death", 0.1);
        deathPenaltyFoodDrainPerDeath = config.getDouble("death-penalty.stats.food-drain-per-death", 0.1);
        deathPenaltyThirstDrainPerDeath = config.getDouble("death-penalty.stats.thirst-drain-per-death", 0.1);

        // Combat
        mobDamageMultiplier = config.getDouble("combat.mob-damage-multiplier", 2.0);
        fallDamageMultiplier = config.getDouble("combat.fall-damage-multiplier", 2.0);
        fireDamageMultiplier = config.getDouble("combat.fire-damage-multiplier", 2.0);
        drowningDamageMultiplier = config.getDouble("combat.drowning-damage-multiplier", 2.0);
        explosionDamageMultiplier = config.getDouble("combat.explosion-damage-multiplier", 1.5);
        pvpDamageMultiplier = config.getDouble("combat.pvp-damage-multiplier", 1.25);
        mobExtraHpMultiplier = config.getDouble("combat.mob-extra-hp-multiplier", 1.5);
        disableNaturalRegen = config.getBoolean("combat.disable-natural-regen", false);
        regenMultiplier = config.getDouble("combat.regen-multiplier", 0.5);
        combatTagDurationMs = config.getLong("combat.combat-tag-duration-seconds", 30) * 1000L;
        enderPearlDamage = config.getDouble("combat.ender-pearl-damage", 2.0);

        // Hunger
        hungerEnabled = config.getBoolean("hunger.enabled", true);
        drainIntervalSeconds = config.getInt("hunger.drain-interval-seconds", 180);
        foodRestoreMultiplier = config.getDouble("hunger.food-restore-multiplier", 0.5);
        starvationDamage = config.getDouble("hunger.starvation-damage", 1.0);
        sprintMinFood = config.getInt("hunger.sprint-min-food", 6);
        rawFoodPoison = config.getBoolean("hunger.raw-food-poison", true);
        chokeEnabled = config.getBoolean("hunger.choke-enabled", true);
        chokeMaxEats = config.getInt("hunger.choke-max-eats", 3);
        chokeWindowSeconds = config.getInt("hunger.choke-window-seconds", 10);
        chokeDamage = config.getDouble("hunger.choke-damage", 1.0);
        notchAppleExplode = config.getBoolean("hunger.notch-apple-explode", true);
        notchAppleDamage = config.getDouble("hunger.notch-apple-damage", 2.0);

        // Thirst
        thirstEnabled = config.getBoolean("thirst.enabled", true);
        thirstDrainIntervalSeconds = config.getInt("thirst.drain-interval-seconds", 120);
        thirstDamage = config.getDouble("thirst.thirst-damage", 1.0);
        maxThirst = config.getInt("thirst.max-thirst", 20);
        drinkFromSource = config.getBoolean("thirst.drink-from-source", true);
        drinkSourceRestore = config.getInt("thirst.drink-source-restore", 4);
        drinkSourceCooldownSeconds = config.getInt("thirst.drink-source-cooldown-seconds", 3);
        bucketDrinkEnabled = config.getBoolean("thirst.bucket-drink.enabled", true);
        bucketRestore = config.getInt("thirst.bucket-drink.restore", 10);
        bottleDrinkEnabled = config.getBoolean("thirst.bottle-drink.enabled", true);
        bottleRestore = config.getInt("thirst.bottle-drink.restore", 6);
        naturalWaterEnabled = config.getBoolean("thirst.natural-water.enabled", true);
        naturalWaterDamagePerSecond = config.getDouble("thirst.natural-water.damage-per-second", 0.5);
        naturalWaterDurationSeconds = config.getInt("thirst.natural-water.duration-seconds", 5);
        naturalWaterNauseaAmplifier = config.getInt("thirst.natural-water.nausea-amplifier", 0);
        bottleFill = config.getBoolean("thirst.bottle-fill", true);

        // Environment
        temperatureEnabled = config.getBoolean("environment.temperature.enabled", true);
        tempCheckIntervalSeconds = config.getInt("environment.temperature.check-interval-seconds", 30);
        heatDamage = config.getDouble("environment.temperature.heat-damage", 1.0);
        heatAmplifier = config.getInt("environment.temperature.heat-amplifier", 0);
        helmetReducePercent = config.getDouble("environment.temperature.helmet-reduce-percent", 0.5);
        helmetDamageReducePercent = config.getDouble("environment.temperature.helmet-damage-reduce-percent", 0.5);
        coldDamage = config.getDouble("environment.temperature.cold-damage", 1.0);
        coldAmplifier = config.getInt("environment.temperature.cold-amplifier", 0);
        armorReducePercent = config.getDouble("environment.temperature.armor-reduce-percent", 0.25);
        armorDamageReducePercent = config.getDouble("environment.temperature.armor-damage-reduce-percent", 0.20);
        heatExposureDelaySeconds = config.getInt("environment.temperature.heat-exposure-delay-seconds", 10);
        coldExposureDelaySeconds = config.getInt("environment.temperature.cold-exposure-delay-seconds", 10);
        waterDamageIntervalMs = config.getLong("environment.temperature.water-damage-interval-ms", 10000);
        acidRainIntervalMs = config.getLong("environment.temperature.acid-rain-interval-ms", 5000);
        fogEnabled = config.getBoolean("environment.fog.enabled", true);
        fogIntervalSeconds = config.getInt("environment.fog.interval-seconds", 60);
        fogEffectDurationSeconds = config.getInt("environment.fog.effect-duration-seconds", 30);
        fogEffectAmplifier = config.getInt("environment.fog.effect-amplifier", 0);
        sleepBlock = config.getBoolean("environment.sleep.block", true);
        sleepDamage = config.getDouble("environment.sleep.damage", 1.0);
        cropSlowEnabled = config.getBoolean("environment.crop-slow.enabled", true);
        cropSlowMultiplier = config.getDouble("environment.crop-slow.multiplier", 0.75);
        toolWearEnabled = config.getBoolean("environment.tool-wear.enabled", true);
        toolWearMultiplier = config.getDouble("environment.tool-wear.multiplier", 1.5);
        flightBlock = config.getBoolean("environment.flight.block", false);
        claustrophobiaEnabled = config.getBoolean("environment.claustrophobia.enabled", true);
        claustrophobiaYLevel = config.getInt("environment.claustrophobia.y-level", 30);
        claustrophobiaChance = config.getInt("environment.claustrophobia.chance", 5);
        claustrophobiaAmplifier = config.getInt("environment.claustrophobia.amplifier", 0);
        claustrophobiaTorchReducePercent = config.getDouble("environment.claustrophobia.torch-reduce-percent", 0.5);
        vertigoEnabled = config.getBoolean("environment.vertigo.enabled", true);
        vertigoYLevel = config.getInt("environment.vertigo.y-level", 100);
        vertigoChance = config.getInt("environment.vertigo.chance", 10);
        vertigoAmplifier = config.getInt("environment.vertigo.amplifier", 0);
        vertigoElytraReducePercent = config.getDouble("environment.vertigo.elytra-reduce-percent", 0.5);

        // Villager Trading
        villagerTradingEnabled = config.getBoolean("villager-trading.enabled", true);
        disableRandomVillager = config.getBoolean("villager-trading.disable-random-villager", true);
        villagerRegionSize = config.getInt("villager-trading.region-size", 500);

        // Spawner Control
        spawnerControlEnabled = config.getBoolean("spawner-control.enabled", true);
        spawnerSpawnRateReduction = config.getDouble("spawner-control.spawn-rate-reduction", 0.3);
        spawnerHpMultiplier = config.getDouble("spawner-control.hp-multiplier", 3.0);
        spawnerDamageMultiplier = config.getDouble("spawner-control.damage-multiplier", 2.0);

        // ===== LOAD FROM EXTERNAL FILES =====
        loadDisasterSettings();
        loadDisasterConfigs();
        loadDisasterMessages();
        loadBossSettings();
        loadBossConfigs();

        // Ore Control
        oreControlEnabled = config.getBoolean("ore-control.enabled", false);
        oreControlWorlds.clear();
        ConfigurationSection oreSection = config.getConfigurationSection("ore-control.worlds");
        if (oreSection != null) {
            for (String worldName : oreSection.getKeys(false)) {
                ConfigurationSection worldSection = oreSection.getConfigurationSection(worldName);
                Map<String, Double> oreRates = new HashMap<>();
                if (worldSection != null) {
                    for (String oreKey : worldSection.getKeys(false)) {
                        oreRates.put(oreKey, worldSection.getDouble(oreKey, 0.0));
                    }
                }
                oreControlWorlds.put(worldName, oreRates);
            }
        }

        // Rename
        renameEnabled = config.getBoolean("rename.enabled", true);
        nameStructure = config.getString("rename.name-structure", "<name> ☠ 000");

        // Logging
        consoleDebug = config.getBoolean("logging.console-debug", false);
        logDeaths = config.getBoolean("logging.log-deaths", true);
        logBans = config.getBoolean("logging.log-bans", true);
        logDisasters = config.getBoolean("logging.log-disasters", true);

        plugin.getLogger().info("[Config] Loaded configuration with " + config.getKeys(true).size() + " keys");
        plugin.getLogger().info("[Config] Loaded " + bossConfigs.size() + " bosses from bosses/");
        plugin.getLogger().info("[Config] Loaded " + disasterConfigs.size() + " disasters from disasters/");
    }

    /**
     * Save default external config files (bosses/*.yml, disasters/*.yml)
     */
    private void saveDefaultExternalConfigs() {
        // Save disasters/_settings.yml
        saveResourceIfNotExists("disasters/_settings.yml");
        // Save disaster files
        saveResourceIfNotExists("disasters/blood-moon.yml");
        saveResourceIfNotExists("disasters/meteor.yml");
        saveResourceIfNotExists("disasters/mega-storm.yml");
        saveResourceIfNotExists("disasters/solar-flare.yml");
        saveResourceIfNotExists("disasters/plague.yml");
        saveResourceIfNotExists("disasters/tornado.yml");
        saveResourceIfNotExists("disasters/eclipse.yml");
        saveResourceIfNotExists("disasters/earthquake.yml");
        saveResourceIfNotExists("disasters/inferno-storm.yml");
        saveResourceIfNotExists("disasters/soul-eruption.yml");
        saveResourceIfNotExists("disasters/lava-geyser.yml");
        saveResourceIfNotExists("disasters/end-surge.yml");
        saveResourceIfNotExists("disasters/void-storm.yml");
        saveResourceIfNotExists("disasters/chorus-explosion.yml");

        // Save bosses/_settings.yml
        saveResourceIfNotExists("bosses/_settings.yml");
        // Save boss files
        saveResourceIfNotExists("bosses/wither.yml");
        saveResourceIfNotExists("bosses/ender_dragon.yml");
        saveResourceIfNotExists("bosses/ghast.yml");
        saveResourceIfNotExists("bosses/zombie_boss.yml");
        saveResourceIfNotExists("bosses/skeleton_boss.yml");
        saveResourceIfNotExists("bosses/spider_boss.yml");
        saveResourceIfNotExists("bosses/creeper_boss.yml");
        saveResourceIfNotExists("bosses/enderman_boss.yml");
        saveResourceIfNotExists("bosses/witch_boss.yml");
        saveResourceIfNotExists("bosses/ravager_boss.yml");
        saveResourceIfNotExists("bosses/vindicator_boss.yml");
        saveResourceIfNotExists("bosses/phantom_boss.yml");
    }

    /**
     * Save a resource from the JAR to the plugin's data folder if it doesn't exist already
     */
    private void saveResourceIfNotExists(String resourcePath) {
        File outFile = new File(plugin.getDataFolder(), resourcePath);
        if (!outFile.exists()) {
            // Create parent directories
            outFile.getParentFile().mkdirs();
            // Save the resource
            plugin.saveResource(resourcePath, false);
            plugin.getLogger().info("[Config] Created default: " + resourcePath);
        }
    }

    /**
     * Load disaster general settings from disasters/_settings.yml
     */
    private void loadDisasterSettings() {
        File settingsFile = new File(plugin.getDataFolder(), "disasters/_settings.yml");
        if (!settingsFile.exists()) {
            disastersEnabled = false;
            disasterMinIntervalRaw = "2400";
            disasterWarningSeconds = 120;
            safeZoneEnabled = false;
            return;
        }

        FileConfiguration ds = YamlConfiguration.loadConfiguration(settingsFile);

        disastersEnabled = ds.getBoolean("enabled", true);
        disasterMinIntervalRaw = getStringFromConfig(ds.getConfigurationSection(""), "min-interval-seconds", "2400-4800");
        disasterWarningSeconds = ds.getInt("warning-seconds", 120);

        // Safe Zone
        ConfigurationSection sz = ds.getConfigurationSection("safe-zone");
        if (sz != null) {
            safeZoneEnabled = sz.getBoolean("enabled", true);
            safeZoneCheckRadius = sz.getInt("check-radius", 2);
            safeZoneRoofHeight = sz.getInt("roof-height", 5);
            safeZoneCheckWalls = sz.getBoolean("check-walls", false);
            safeZoneMinWalls = sz.getInt("min-walls", 2);
        } else {
            safeZoneEnabled = false;
        }
    }

    /**
     * Load all disaster configurations from disasters/*.yml files
     * Now supports dynamic disaster IDs by scanning all .yml files
     */
    private void loadDisasterConfigs() {
        disasterConfigs.clear();

        File disastersDir = new File(plugin.getDataFolder(), "disasters");
        if (!disastersDir.exists() || !disastersDir.isDirectory()) return;

        File[] disasterFiles = disastersDir.listFiles((dir, name) -> name.endsWith(".yml") && !name.equals("_settings.yml"));
        if (disasterFiles == null) return;

        for (File file : disasterFiles) {
            String fileName = file.getName();
            String disasterId = fileName.substring(0, fileName.length() - 4); // Remove .yml

            FileConfiguration section = YamlConfiguration.loadConfiguration(file);
            if (section == null) continue;

            DisasterConfig dc = new DisasterConfig();
            dc.enabled = section.getBoolean("enabled", true);
            dc.chance = section.getInt("chance", 5);
            dc.name = section.getString("name", disasterId);
            dc.effectIntervalSeconds = section.getInt("effect-interval-seconds", 5);
            dc.effectDurationSeconds = section.getInt("effect-duration-seconds", 5);
            dc.durationSeconds = section.getInt("duration-seconds", 600);

            // Custom title/subtitle for disaster start
            dc.startTitle = section.getString("start-title", "");
            dc.startSubtitle = section.getString("start-subtitle", "");

            // Earthquake specific
            dc.blockFallChance = section.getInt("block-fall-chance", 15);
            dc.radius = section.getInt("radius", 15);
            dc.minY = section.getInt("min-y", 30);
            dc.blastResistanceFactor = section.getDouble("blast-resistance-factor", 0.1);

            // Damage/Fire/Wither
            dc.damage = section.getDouble("damage", 2.0);
            dc.fireTicks = section.getInt("fire-ticks", 100);
            dc.witherAmplifier = section.getInt("wither-amplifier", 1);

            // End Surge specific
            dc.shulkerChance = section.getInt("shulker-chance", 20);

            // Parse actions from YAML list
            List<Map<?, ?>> actionsList = section.getMapList("actions");
            if (!actionsList.isEmpty()) {
                dc.actions = DisasterAction.parseActionList(actionsList);
            }
            List<Map<?, ?>> onEndList = section.getMapList("on-end");
            if (!onEndList.isEmpty()) {
                dc.onEnd = DisasterAction.parseActionList(onEndList);
            }

            disasterConfigs.put(disasterId, dc);
        }
    }

    /**
     * Load disaster messages from disasters/_settings.yml
     */
    private void loadDisasterMessages() {
        disasterMessages.clear();

        File settingsFile = new File(plugin.getDataFolder(), "disasters/_settings.yml");
        if (!settingsFile.exists()) return;

        FileConfiguration ds = YamlConfiguration.loadConfiguration(settingsFile);
        ConfigurationSection messagesSection = ds.getConfigurationSection("messages");
        if (messagesSection == null) return;

        disasterMessages.put("warning-title", messagesSection.getString("warning-title", "§4§l⚠ CẢNH BÁO THIÊN TAI ⚠"));
        disasterMessages.put("warning-subtitle", messagesSection.getString("warning-subtitle", "§c{name}\n§e§lSẽ xảy ra trong {time} giây!"));
        disasterMessages.put("warning-broadcast", messagesSection.getString("warning-broadcast", "§4§l⚠ {name} §r§cđang đến gần!"));
        disasterMessages.put("countdown-broadcast", messagesSection.getString("countdown-broadcast", "§4§l⚠ {name} §csẽ xảy ra trong §4§l{time}§c giây!"));
        disasterMessages.put("active-broadcast", messagesSection.getString("active-broadcast", "§4§l{name} - {message} (§e{duration}s§4)"));
        disasterMessages.put("end-broadcast", messagesSection.getString("end-broadcast", "§a§l✅ {name} đã kết thúc!"));
    }

    /**
     * Load boss general settings from bosses/_settings.yml
     */
    private void loadBossSettings() {
        File settingsFile = new File(plugin.getDataFolder(), "bosses/_settings.yml");
        if (!settingsFile.exists()) {
            bossEventsEnabled = false;
            bossEventMinIntervalRaw = "1200";
            bossEventSpawnRadius = 50;
            return;
        }

        FileConfiguration bs = YamlConfiguration.loadConfiguration(settingsFile);

        bossEventsEnabled = bs.getBoolean("enabled", true);
        bossEventMinIntervalRaw = getStringFromConfig(bs.getConfigurationSection(""), "min-interval-seconds", "1200-2400");
        bossEventSpawnRadius = bs.getInt("spawn-radius", 50);
    }

    /**
     * Load all boss configurations from bosses/*.yml files
     * Dynamically discovers all .yml files in the bosses/ directory
     */
    private void loadBossConfigs() {
        bossConfigs.clear();

        File bossesDir = new File(plugin.getDataFolder(), "bosses");
        if (!bossesDir.exists() || !bossesDir.isDirectory()) return;

        File[] bossFiles = bossesDir.listFiles((dir, name) -> name.endsWith(".yml") && !name.equals("_settings.yml"));
        if (bossFiles == null) return;

        for (File file : bossFiles) {
            String fileName = file.getName();
            String bossId = fileName.substring(0, fileName.length() - 4); // Remove .yml

            FileConfiguration section = YamlConfiguration.loadConfiguration(file);
            if (section == null) continue;

            BossConfig bc = new BossConfig();
            bc.enabled = section.getBoolean("enabled", true);
            bc.entityType = section.getString("entity-type", "WITHER");
            bc.displayName = section.getString("display-name", "§c§lBoss");
            bc.hp = section.getDouble("hp", 100.0);
            bc.damageMultiplier = section.getDouble("damage-multiplier", 1.0);
            bc.chance = section.getInt("chance", 10);
            bc.durationSeconds = section.getInt("duration-seconds", 120);
            bc.warningSeconds = section.getInt("warning-seconds", 60);

            // Load immunities
            ConfigurationSection immunitiesSection = section.getConfigurationSection("immunities");
            if (immunitiesSection != null) {
                // Boolean immunities
                for (String key : immunitiesSection.getKeys(false)) {
                    if (key.equals("potion-effects")) continue;
                    bc.immunities.put(key, immunitiesSection.getBoolean(key, false));
                }
                // Potion effect list immunity
                bc.immunityPotionEffects = immunitiesSection.getStringList("potion-effects");
            }

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
        }
    }
}