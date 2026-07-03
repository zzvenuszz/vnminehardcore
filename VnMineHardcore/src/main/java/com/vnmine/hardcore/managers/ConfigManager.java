package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

public class ConfigManager {

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
    public double coldDamage;
    public int coldAmplifier;
    public double armorReducePercent;
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

    // Spawner Control
    public boolean spawnerControlEnabled;
    public double spawnerSpawnRateReduction;
    public double spawnerHpMultiplier;
    public double spawnerDamageMultiplier;

    // Disasters - General settings
    public boolean disastersEnabled;
    public int disasterMinIntervalSeconds;
    public int disasterWarningSeconds;

    // Disaster config map - each disaster is a complete object
    public Map<String, DisasterConfig> disasterConfigs = new HashMap<>();

    // Disaster messages (shared across all disasters)
    public Map<String, String> disasterMessages = new HashMap<>();

    // Boss Events - General settings
    public boolean bossEventsEnabled;
    public int bossEventMinIntervalSeconds;
    public int bossEventSpawnRadius;

    // Boss config map - each boss is a complete object
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
        // [VI] Bật/tắt thiên tai này
        // [EN] Enable/disable this disaster
        public boolean enabled = true;

        // [VI] Tỷ lệ xuất hiện (phần trăm)
        // [EN] Spawn chance (percent)
        public int chance = 5;

        // [VI] Tên hiển thị (hỗ trợ mã màu §)
        // [EN] Display name (supports color codes §)
        public String name = "Unknown Disaster";

        // [VI] Thời gian giữa các đợt áp dụng hiệu ứng (giây)
        // [EN] Interval between effect applications (seconds)
        public int effectIntervalSeconds = 5;

        // [VI] Thời gian kéo dài của mỗi đợt hiệu ứng (giây)
        // [EN] Duration of each effect wave (seconds)
        public int effectDurationSeconds = 5;

        // [VI] Thời gian kéo dài tổng thể của thiên tai (giây)
        // [EN] Total duration of disaster (seconds)
        public int durationSeconds = 600;

        // Earthquake specific
        public int blockFallChance = 15;
        public int radius = 15;
        public int minY = 30;
        public double blastResistanceFactor = 0.1;

        // Inferno Storm specific
        public double damage = 2.0;
        public int fireTicks = 100;

        // Soul Eruption specific
        public int witherAmplifier = 1;

        // End Surge specific
        public int shulkerChance = 20;

        // Void Storm, Lava Geyser, Chorus Explosion specific
        // (damage is already defined above)
    }

    // BossConfig class - holds all config for a single boss
    public static class BossConfig {
        // [VI] Bật/tắt boss này
        // [EN] Enable/disable this boss
        public boolean enabled = true;

        // [VI] Loại entity (WITHER, ENDER_DRAGON, GHAST, ZOMBIE, SKELETON, SPIDER, CREEPER, ENDERMAN, WITCH, RAVAGER, VINDICATOR, PHANTOM)
        // [EN] Entity type (WITHER, ENDER_DRAGON, GHAST, ZOMBIE, SKELETON, SPIDER, CREEPER, ENDERMAN, WITCH, RAVAGER, VINDICATOR, PHANTOM)
        public String entityType = "WITHER";

        // [VI] Tên hiển thị của boss (hỗ trợ mã màu §)
        // [EN] Display name of boss (supports color codes §)
        public String displayName = "§c§lBoss";

        // [VI] HP tối đa của boss
        // [EN] Maximum HP of boss
        public double hp = 100.0;

        // [VI] Hệ số nhân sát thương (1.0 = bình thường, 2.0 = gấp đôi)
        // [EN] Damage multiplier (1.0 = normal, 2.0 = double)
        public double damageMultiplier = 1.0;

        // [VI] Tỷ lệ xuất hiện (%) - tổng tất cả boss không được vượt quá 100
        // [EN] Spawn chance (%) - total of all bosses should not exceed 100
        public int chance = 10;

        // [VI] Thời gian tồn tại tối đa của boss (giây)
        // [EN] Maximum boss duration (seconds)
        public int durationSeconds = 120;

        // [VI] Thời gian cảnh báo trước khi boss xuất hiện (giây)
        // [EN] Warning time before boss spawns (seconds)
        public int warningSeconds = 60;

        // [VI] Danh sách item rơi ra khi boss bị tiêu diệt
        // [EN] List of items dropped when boss is killed
        public Map<String, DropConfig> drops = new HashMap<>();
    }

    // DropConfig class - holds drop configuration
    public static class DropConfig {
        // [VI] Số lượng tối thiểu rơi ra
        // [EN] Minimum amount dropped
        public int minAmount = 1;

        // [VI] Số lượng tối đa rơi ra
        // [EN] Maximum amount dropped
        public int maxAmount = 1;

        // [VI] Tỷ lệ rơi (0.0 = 0%, 1.0 = 100%)
        // [EN] Drop chance (0.0 = 0%, 1.0 = 100%)
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
        coldDamage = config.getDouble("environment.temperature.cold-damage", 1.0);
        coldAmplifier = config.getInt("environment.temperature.cold-amplifier", 0);
        armorReducePercent = config.getDouble("environment.temperature.armor-reduce-percent", 0.25);
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

        // Spawner Control
        spawnerControlEnabled = config.getBoolean("spawner-control.enabled", true);
        spawnerSpawnRateReduction = config.getDouble("spawner-control.spawn-rate-reduction", 0.3);
        spawnerHpMultiplier = config.getDouble("spawner-control.hp-multiplier", 3.0);
        spawnerDamageMultiplier = config.getDouble("spawner-control.damage-multiplier", 2.0);

        // Disasters - General settings
        disastersEnabled = config.getBoolean("disasters.enabled", true);
        disasterMinIntervalSeconds = config.getInt("disasters.min-interval-seconds", 1200);
        disasterWarningSeconds = config.getInt("disasters.warning-seconds", 60);

        // Load all disaster configs
        loadDisasterConfigs();

        // Load disaster messages
        loadDisasterMessages();

        // Boss Events - General settings
        bossEventsEnabled = config.getBoolean("boss-events.enabled", true);
        bossEventMinIntervalSeconds = config.getInt("boss-events.min-interval-seconds", 1200);
        bossEventSpawnRadius = config.getInt("boss-events.spawn-radius", 50);

        // Load all boss configs
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
    }

    /**
     * Load all disaster configurations from config
     * Each disaster is a complete object with all its settings
     */
    private void loadDisasterConfigs() {
        disasterConfigs.clear();
        ConfigurationSection disastersSection = config.getConfigurationSection("disasters");
        if (disastersSection == null) return;

        // List of known disaster IDs
        String[] disasterIds = {
            "blood-moon", "meteor", "mega-storm", "solar-flare", "plague",
            "tornado", "eclipse", "earthquake", "inferno-storm", "soul-eruption",
            "lava-geyser", "end-surge", "void-storm", "chorus-explosion"
        };

        for (String disasterId : disasterIds) {
            ConfigurationSection section = disastersSection.getConfigurationSection(disasterId);
            if (section == null) continue;

            DisasterConfig dc = new DisasterConfig();
            dc.enabled = section.getBoolean("enabled", true);
            dc.chance = section.getInt("chance", 5);
            dc.name = section.getString("name", disasterId);
            dc.effectIntervalSeconds = section.getInt("effect-interval-seconds", 5);
            dc.effectDurationSeconds = section.getInt("effect-duration-seconds", 5);
            dc.durationSeconds = section.getInt("duration-seconds", 600);

            // Earthquake specific
            dc.blockFallChance = section.getInt("block-fall-chance", 15);
            dc.radius = section.getInt("radius", 15);
            dc.minY = section.getInt("min-y", 30);
            dc.blastResistanceFactor = section.getDouble("blast-resistance-factor", 0.1);

            // Inferno Storm, Soul Eruption, Lava Geyser, Void Storm, Chorus Explosion specific
            dc.damage = section.getDouble("damage", 2.0);
            dc.fireTicks = section.getInt("fire-ticks", 100);
            dc.witherAmplifier = section.getInt("wither-amplifier", 1);

            // End Surge specific
            dc.shulkerChance = section.getInt("shulker-chance", 20);

            disasterConfigs.put(disasterId, dc);
        }
    }

    /**
     * Load disaster messages
     */
    private void loadDisasterMessages() {
        disasterMessages.clear();
        ConfigurationSection messagesSection = config.getConfigurationSection("disasters.messages");
        if (messagesSection == null) return;

        disasterMessages.put("warning-title", messagesSection.getString("warning-title", "§4§l⚠ CẢNH BÁO THIÊN TAI ⚠"));
        disasterMessages.put("warning-subtitle", messagesSection.getString("warning-subtitle", "§c{name}\n§e§lSẽ xảy ra trong {time} giây!"));
        disasterMessages.put("warning-broadcast", messagesSection.getString("warning-broadcast", "§4§l⚠ {name} §r§cđang đến gần!"));
        disasterMessages.put("countdown-broadcast", messagesSection.getString("countdown-broadcast", "§4§l⚠ {name} §csẽ xảy ra trong §4§l{time}§c giây!"));
        disasterMessages.put("active-broadcast", messagesSection.getString("active-broadcast", "§4§l{name} - {message} (§e{duration}s§4)"));
        disasterMessages.put("end-broadcast", messagesSection.getString("end-broadcast", "§a§l✅ {name} đã kết thúc!"));
    }

    /**
     * Load all boss configurations from config
     * Each boss is a complete object with all its settings
     */
    private void loadBossConfigs() {
        bossConfigs.clear();
        ConfigurationSection bossesSection = config.getConfigurationSection("boss-events.bosses");
        if (bossesSection == null) return;

        for (String bossId : bossesSection.getKeys(false)) {
            ConfigurationSection section = bossesSection.getConfigurationSection(bossId);
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