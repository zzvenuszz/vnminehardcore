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

    // Disasters
    public boolean disastersEnabled;
    public int disasterMinIntervalSeconds;
    public int disasterWarningSeconds;
    public int bloodMoonChance;
    public int meteorChance;
    public int megaStormChance;
    public int solarFlareChance;
    public int plagueChance;
    public int tornadoChance;
    public int eclipseChance;
    public int earthquakeChance;
    public int infernoStormChance;
    public int soulEruptionChance;
    public int lavaGeyserChance;
    public int endSurgeChance;
    public int voidStormChance;
    public int chorusExplosionChance;

    // Per-disaster configs (stored as Map for flexible access)
    public Map<String, Integer> disasterEffectInterval = new HashMap<>();
    public Map<String, Integer> disasterEffectDuration = new HashMap<>();

    // Earthquake specific
    public int earthquakeBlockFallChance;
    public int earthquakeRadius;
    public int earthquakeMinY;
    public double earthquakeBlastResistanceFactor;

    // Inferno Storm
    public double infernoStormDamage;
    public int infernoStormFireTicks;

    // Soul Eruption
    public double soulEruptionDamage;
    public int soulEruptionWitherAmplifier;

    // Lava Geyser
    public double lavaGeyserDamage;

    // End Surge
    public int endSurgeShulkerChance;

    // Void Storm
    public double voidStormDamage;

    // Chorus Explosion
    public double chorusExplosionDamage;

    // Boss Events
    public boolean bossEventsEnabled;
    public int bossEventMinIntervalSeconds;
    public int bossEventSpawnRadius;
    // Boss configs will be loaded dynamically

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

        // Disasters
        disastersEnabled = config.getBoolean("disasters.enabled", true);
        disasterMinIntervalSeconds = config.getInt("disasters.min-interval-seconds", 600);
        disasterWarningSeconds = config.getInt("disasters.warning-seconds", 300);
        bloodMoonChance = config.getInt("disasters.blood-moon-chance", 30);
        meteorChance = config.getInt("disasters.meteor-chance", 5);
        megaStormChance = config.getInt("disasters.mega-storm-chance", 5);
        solarFlareChance = config.getInt("disasters.solar-flare-chance", 3);
        plagueChance = config.getInt("disasters.plague-chance", 2);
        tornadoChance = config.getInt("disasters.tornado-chance", 2);
        eclipseChance = config.getInt("disasters.eclipse-chance", 1);
        earthquakeChance = config.getInt("disasters.earthquake-chance", 2);
        infernoStormChance = config.getInt("disasters.inferno-storm-chance", 3);
        soulEruptionChance = config.getInt("disasters.soul-eruption-chance", 2);
        lavaGeyserChance = config.getInt("disasters.lava-geyser-chance", 2);
        endSurgeChance = config.getInt("disasters.end-surge-chance", 2);
        voidStormChance = config.getInt("disasters.void-storm-chance", 2);
        chorusExplosionChance = config.getInt("disasters.chorus-explosion-chance", 1);

        // Per-disaster configs
        loadDisasterConfig("blood-moon");
        loadDisasterConfig("meteor");
        loadDisasterConfig("mega-storm");
        loadDisasterConfig("solar-flare");
        loadDisasterConfig("plague");
        loadDisasterConfig("tornado");
        loadDisasterConfig("eclipse");
        loadDisasterConfig("earthquake");
        loadDisasterConfig("inferno-storm");
        loadDisasterConfig("soul-eruption");
        loadDisasterConfig("lava-geyser");
        loadDisasterConfig("end-surge");
        loadDisasterConfig("void-storm");
        loadDisasterConfig("chorus-explosion");

        // Earthquake specific
        earthquakeBlockFallChance = config.getInt("disasters.earthquake.block-fall-chance", 15);
        earthquakeRadius = config.getInt("disasters.earthquake.radius", 15);
        earthquakeMinY = config.getInt("disasters.earthquake.min-y", 30);
        earthquakeBlastResistanceFactor = config.getDouble("disasters.earthquake.blast-resistance-factor", 0.1);

        // Inferno Storm
        infernoStormDamage = config.getDouble("disasters.inferno-storm.damage", 2.0);
        infernoStormFireTicks = config.getInt("disasters.inferno-storm.fire-ticks", 100);

        // Soul Eruption
        soulEruptionDamage = config.getDouble("disasters.soul-eruption.damage", 2.0);
        soulEruptionWitherAmplifier = config.getInt("disasters.soul-eruption.wither-amplifier", 1);

        // Lava Geyser
        lavaGeyserDamage = config.getDouble("disasters.lava-geyser.damage", 3.0);

        // End Surge
        endSurgeShulkerChance = config.getInt("disasters.end-surge.shulker-chance", 20);

        // Void Storm
        voidStormDamage = config.getDouble("disasters.void-storm.damage", 2.0);

        // Chorus Explosion
        chorusExplosionDamage = config.getDouble("disasters.chorus-explosion.damage", 1.0);

        // Boss Events
        bossEventsEnabled = config.getBoolean("boss-events.enabled", true);
        bossEventMinIntervalSeconds = config.getInt("boss-events.min-interval-seconds", 1200);
        bossEventSpawnRadius = config.getInt("boss-events.spawn-radius", 50);

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

    private void loadDisasterConfig(String disasterId) {
        String path = "disasters." + disasterId;
        disasterEffectInterval.put(disasterId, config.getInt(path + ".effect-interval-seconds", 5));
        disasterEffectDuration.put(disasterId, config.getInt(path + ".effect-duration-seconds", 5));
    }
}