package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {

    private final VnMineHardcore plugin;
    private FileConfiguration config;

    // Death
    public boolean banOnDeath;
    public boolean banIp;
    public boolean broadcastDeath;
    public boolean playSound;

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
    public int drainIntervalTicks;
    public double foodRestoreMultiplier;
    public double starvationDamage;
    public int sprintMinFood;
    public boolean rawFoodPoison;
    public boolean chokeEnabled;
    public int chokeMaxEats;
    public long chokeWindowMs;
    public double chokeDamage;
    public boolean notchAppleExplode;
    public double notchAppleDamage;

    // Thirst
    public boolean thirstEnabled;
    public long thirstDrainIntervalMs;
    public double thirstDamage;
    public int maxThirst;
    public boolean drinkFromSource;
    public int drinkSourceRestore;
    public long drinkSourceCooldownMs;
    public boolean naturalWaterEnabled;
    public double naturalWaterDamagePerSecond;
    public int naturalWaterDurationSeconds;
    public int naturalWaterNauseaAmplifier;
    public boolean bottleFill;
    public int bottleRestore;
    public int bucketRestore;

    // Environment
    public boolean temperatureEnabled;
    public long tempCheckIntervalMs;
    public double heatDamage;
    public double coldDamage;
    public long waterDamageIntervalMs;
    public long acidRainIntervalMs;
    public boolean fogEnabled;
    public int fogIntervalTicks;
    public int fogEffectDurationTicks;
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
    public boolean vertigoEnabled;
    public int vertigoYLevel;
    public int vertigoChance;

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

        // Combat
        mobDamageMultiplier = config.getDouble("combat.mob-damage-multiplier", 2.0);
        fallDamageMultiplier = config.getDouble("combat.fall-damage-multiplier", 2.0);
        fireDamageMultiplier = config.getDouble("combat.fire-damage-multiplier", 2.0);
        drowningDamageMultiplier = config.getDouble("combat.drowning-damage-multiplier", 2.0);
        explosionDamageMultiplier = config.getDouble("combat.explosion-damage-multiplier", 1.5);
        pvpDamageMultiplier = config.getDouble("combat.pvp-damage-multiplier", 1.25);
        mobExtraHpMultiplier = config.getDouble("combat.mob-extra-hp-multiplier", 1.5);
        disableNaturalRegen = config.getBoolean("combat.disable-natural-regen", true);
        regenMultiplier = config.getDouble("combat.regen-multiplier", 1.0);
        combatTagDurationMs = config.getLong("combat.combat-tag-duration-seconds", 30) * 1000L;
        enderPearlDamage = config.getDouble("combat.ender-pearl-damage", 10.0);

        // Hunger
        hungerEnabled = config.getBoolean("hunger.enabled", true);
        drainIntervalTicks = config.getInt("hunger.drain-interval-ticks", 80);
        foodRestoreMultiplier = config.getDouble("hunger.food-restore-multiplier", 0.5);
        starvationDamage = config.getDouble("hunger.starvation-damage", 2.0);
        sprintMinFood = config.getInt("hunger.sprint-min-food", 4);
        rawFoodPoison = config.getBoolean("hunger.raw-food-poison", true);
        chokeEnabled = config.getBoolean("hunger.choke-enabled", true);
        chokeMaxEats = config.getInt("hunger.choke-max-eats", 3);
        chokeWindowMs = config.getLong("hunger.choke-window-ms", 10000);
        chokeDamage = config.getDouble("hunger.choke-damage", 4.0);
        notchAppleExplode = config.getBoolean("hunger.notch-apple-explode", true);
        notchAppleDamage = config.getDouble("hunger.notch-apple-damage", 20.0);

        // Thirst
        thirstEnabled = config.getBoolean("thirst.enabled", true);
        thirstDrainIntervalMs = config.getLong("thirst.drain-interval-ms", 8000);
        thirstDamage = config.getDouble("thirst.thirst-damage", 1.0);
        maxThirst = config.getInt("thirst.max-thirst", 20);
        drinkFromSource = config.getBoolean("thirst.drink-from-source", true);
        drinkSourceRestore = config.getInt("thirst.drink-source-restore", 4);
        drinkSourceCooldownMs = config.getLong("thirst.drink-source-cooldown-ms", 3000);
        naturalWaterEnabled = config.getBoolean("thirst.natural-water.enabled", true);
        naturalWaterDamagePerSecond = config.getDouble("thirst.natural-water.damage-per-second", 2.0);
        naturalWaterDurationSeconds = config.getInt("thirst.natural-water.duration-seconds", 10);
        naturalWaterNauseaAmplifier = config.getInt("thirst.natural-water.nausea-amplifier", 0);
        bottleFill = config.getBoolean("thirst.bottle-fill", true);
        bottleRestore = config.getInt("thirst.bottle-restore", 6);
        bucketRestore = config.getInt("thirst.bucket-restore", 8);

        // Environment
        temperatureEnabled = config.getBoolean("environment.temperature.enabled", true);
        tempCheckIntervalMs = config.getLong("environment.temperature.check-interval-ms", 30000);
        heatDamage = config.getDouble("environment.temperature.heat-damage", 1.0);
        coldDamage = config.getDouble("environment.temperature.cold-damage", 1.0);
        waterDamageIntervalMs = config.getLong("environment.temperature.water-damage-interval-ms", 10000);
        acidRainIntervalMs = config.getLong("environment.temperature.acid-rain-interval-ms", 5000);
        fogEnabled = config.getBoolean("environment.fog.enabled", true);
        fogIntervalTicks = config.getInt("environment.fog.interval-ticks", 80);
        fogEffectDurationTicks = config.getInt("environment.fog.effect-duration-ticks", 100);
        fogEffectAmplifier = config.getInt("environment.fog.effect-amplifier", 0);
        sleepBlock = config.getBoolean("environment.sleep.block", true);
        sleepDamage = config.getDouble("environment.sleep.damage", 4.0);
        cropSlowEnabled = config.getBoolean("environment.crop-slow.enabled", true);
        cropSlowMultiplier = config.getDouble("environment.crop-slow.multiplier", 0.75);
        toolWearEnabled = config.getBoolean("environment.tool-wear.enabled", true);
        toolWearMultiplier = config.getDouble("environment.tool-wear.multiplier", 1.5);
        flightBlock = config.getBoolean("environment.flight.block", true);
        claustrophobiaEnabled = config.getBoolean("environment.claustrophobia.enabled", true);
        claustrophobiaYLevel = config.getInt("environment.claustrophobia.y-level", 30);
        claustrophobiaChance = config.getInt("environment.claustrophobia.chance", 5);
        vertigoEnabled = config.getBoolean("environment.vertigo.enabled", true);
        vertigoYLevel = config.getInt("environment.vertigo.y-level", 200);
        vertigoChance = config.getInt("environment.vertigo.chance", 10);

        // Disasters
        disastersEnabled = config.getBoolean("disasters.enabled", true);
        disasterMinIntervalSeconds = config.getInt("disasters.min-interval-seconds", 120);
        disasterWarningSeconds = config.getInt("disasters.warning-seconds", 300);
        bloodMoonChance = config.getInt("disasters.blood-moon-chance", 30);
        meteorChance = config.getInt("disasters.meteor-chance", 5);
        megaStormChance = config.getInt("disasters.mega-storm-chance", 5);
        solarFlareChance = config.getInt("disasters.solar-flare-chance", 3);
        plagueChance = config.getInt("disasters.plague-chance", 2);
        tornadoChance = config.getInt("disasters.tornado-chance", 2);
        eclipseChance = config.getInt("disasters.eclipse-chance", 1);

        // Logging
        consoleDebug = config.getBoolean("logging.console-debug", false);
        logDeaths = config.getBoolean("logging.log-deaths", true);
        logBans = config.getBoolean("logging.log-bans", true);
        logDisasters = config.getBoolean("logging.log-disasters", true);

        plugin.getLogger().info("[Config] Loaded configuration with " + config.getKeys(true).size() + " keys");
    }
}