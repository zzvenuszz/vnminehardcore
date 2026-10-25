package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.yaml.snakeyaml.error.YAMLException;

public class ConfigManager {
   private static final Random RANDOM = ThreadLocalRandom.current();
   private final VnMineHardcore plugin;
   private FileConfiguration config;
   public int profileDatabaseBackupIntervalHours = 6;
   public int profileDatabaseBackupRetentionCount = 12;
   public int profileDatabaseWriteRetries = 2;
   public int profileDatabaseWriteRetryDelayMillis = 50;
   public int characterProgressionMaxLevel = CharacterProgression.DEFAULT_MAX_LEVEL;
   public int characterProgressionBaseExperience = CharacterProgression.DEFAULT_BASE_EXPERIENCE;
   public int characterProgressionExperiencePerLevel = CharacterProgression.DEFAULT_EXPERIENCE_PER_LEVEL;
   public int characterProgressionSkillPointsPerLevel = CharacterProgression.DEFAULT_SKILL_POINTS_PER_LEVEL;
   public int rpgMaxMana = 100;
   public int rpgManaRegenerationPerSecond = 5;
   public double rpgBattleFocusDamageBonusPerRank = 0.03;
   public double rpgFortifyDamageReductionPerStackPerRank = 0.02;
   public int rpgFortifyMaxStacks = 3;
   public int rpgFortifyStackDurationSeconds = 5;
   public int rpgManaFlowManaPerChainCastPerRank = 1;
   public int rpgManaFlowChainWindowSeconds = 5;
   // Death
   public boolean broadcastDeath;
   public boolean playSound;
   public boolean respawnProtectionEnabled;
   public int respawnProtectionSeconds;
   public int respawnMovementRadiusBlocks;
   public int respawnDamageImmunitySeconds;
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
   public double waterDamage;
   public double acidRainDamage;
   public Set<String> hotBiomeKeys = Set.of();
   public Set<String> coldBiomeKeys = Set.of();
   public Set<String> acidRainBiomeKeys = Set.of();
   public long waterDamageIntervalMs;
   public long acidRainIntervalMs;
   public boolean rainControlEnabled;
   public int rainChancePercent;
   public int maxRainDurationMinutes;
   public boolean rainRandomDurationEnabled;
   public int minRainDurationMinutes;
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
   public int disasterDefaultSuccessProbability;
   public List<ConfigManager.DisasterSuccessThreshold> disasterSuccessThresholds = new ArrayList<>();
   // Safe Zone settings (loaded from disasters/_settings.yml)
   public boolean safeZoneEnabled;
   public int safeZoneCheckRadius;
   public int safeZoneRoofHeight;
   public boolean safeZoneCheckWalls;
   public int safeZoneMinWalls;
   // Disaster config map - each disaster is a complete object
   public Map<String, ConfigManager.DisasterConfig> disasterConfigs = new HashMap<>();
   // Disaster messages (shared across all disasters, loaded from disasters/_settings.yml)
   public Map<String, String> disasterMessages = new HashMap<>();
   // Boss Events - General settings (loaded from bosses/_settings.yml)
   public boolean bossEventsEnabled;
   public boolean bossSkillCastLogsEnabled;
   public boolean bossSkillCastOpBroadcastEnabled;
   public boolean bossSkillCastPlayerBroadcastEnabled;
   public double bossRewardDamageThresholdPercent;
   public double bossMaxPlayerHitDamage = 6.0;
   public boolean bossRandomHealthEnabled = true;
   public int bossHealthLevelOneMinimum = 2000;
   public int bossHealthLevelOneMaximum = 4000;
   public int bossHealthLevelTenMinimum = 45000;
   public int bossHealthLevelTenMaximum = 50000;
   public int bossDefaultDurationSeconds = 1800;
   public boolean spiderBossDamageDebugEnabled = true;
   public boolean bossSizeEnabled = true;
   public double bossSizeMultiplier = 2.0;
   public Set<String> bossSizeEntityTypes = Set.of();
   public boolean bossFlightHeightEnabled = true;
   public int bossMaxFlightHeightAboveGround = 30;
   public int bossFlightTargetRange = 30;
   public int bossFlightPreferredCombatDistance = 12;
   public Set<String> bossFlightEntityTypes = Set.of();
   public boolean bossAiEnabled = true;
   public double bossAiDamageWeight = 60.0;
   public double bossAiProximityWeight = 25.0;
   public double bossAiLowHealthWeight = 15.0;
   public double bossAiRetargetScoreMargin = 8.0;
   public double bossAiGroundPathfindingSpeed = 1.1;
   public boolean bossAiProjectileDodgeEnabled = true;
   public int bossAiProjectileDodgeRange = 10;
   public int bossAiProjectileDodgeCooldownTicks = 20;
   public boolean bossAiPhasesEnabled = true;
   public double bossAiPhaseTwoHealthPercent = 60.0;
   public double bossAiPhaseThreeHealthPercent = 30.0;
   public boolean bossAiMinionCoordinationEnabled = true;
   public boolean bossArmyTokenDropEnabled = true;
   public double bossArmyTokenDropChance = 0.3;
   public int bossExperienceLevelOne = 50;
   public int bossExperienceLevelTen = 150;
   public String bossEventMinIntervalRaw;
   public int bossEventSpawnRadius;
   public boolean bossLevelProgressionEnabled;
   public List<ConfigManager.BossLevelThreshold> bossLevelThresholds = new ArrayList<>();
   public boolean bossSpawnSuccessProgressionEnabled;
   public double bossSpawnSuccessDefaultPercent = 100.0;
   public List<ConfigManager.BossSpawnSuccessThreshold> bossSpawnSuccessThresholds = new ArrayList<>();
   public double bossArmorPieceDropChance = 0.3;
   public int bossArmorNaturalLevelGap = 2;
   public double bossArmorLevelsPerEnchant = 0.8;
   public int bossArmorMaximumEnchantLevel = 5;
   public boolean bossArmorAllowUnsafeEnchants = true;
   private ConfigManager.BossMercenaryGearDropConfig bossMercenaryGearDropDefaults;
   // Boss config map - each boss is a complete object (loaded from bosses/*.yml)
   public Map<String, ConfigManager.BossConfig> bossConfigs = new HashMap<>();
   private final Map<String, List<String>> bossItemStories = new HashMap<>();
   private final Map<String, ConfigManager.BossArmorPieceStory> bossArmorPieceStories = new HashMap<>();
   private final Map<String, String> bossItemStoryBosses = new HashMap<>();
   private Map<String, StoryChapterLoader.StoryChapter> storyChapters = Map.of();
   // Ore Control
   public boolean oreControlEnabled;
   public Map<String, Map<String, Double>> oreControlWorlds = new HashMap<>();
   // Time Control
   public boolean timeControlEnabled;
   public int dayDurationMinutes;
   public int nightDurationMinutes;
   public Map<String, ConfigManager.WorldTimeConfig> worldTimeConfigs = new HashMap<>();
   // Block drop, tool break, and explosion rules
   public boolean blockDropSettingsEnabled;
   public boolean dropReplaceEnabled;
   public boolean toolBreakEnabled;
   public boolean oreExplodeEnabled;
   public Map<Material, Map<Material, ConfigManager.BlockMiningRule>> blockMiningRules = new HashMap<>();
   // Rename
   public boolean renameEnabled;
   public String nameStructure;
   // Logging
   public volatile boolean consoleDebug;
   public boolean logDeaths;
   public boolean logDisasters;

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

    private int readBoundedInt(String path, int defaultValue, int minimum, int maximum) {
       int value = this.config.getInt(path, defaultValue);
       if (value >= minimum && value <= maximum) return value;
       this.plugin.getLogger().warning("[Config] " + path + " must be between " + minimum + " and " + maximum
           + "; using " + defaultValue + ".");
       return defaultValue;
    }

    public ConfigManager(VnMineHardcore plugin) {
      this.plugin = plugin;
      this.reload();
   }

   public void reload() {
      this.plugin.saveDefaultConfig();
      this.plugin.reloadConfig();
      this.config = this.plugin.getConfig();
      this.migrateDeathPenaltyDefaults();
      this.mergeDefaultResource("config.yml");
      this.plugin.reloadConfig();
      this.config = this.plugin.getConfig();
      // ===== Save default external configs (bosses/, disasters/) =====
      this.saveDefaultExternalConfigs();
      this.profileDatabaseBackupIntervalHours =
          readBoundedInt("rpg.profile-database.backup-interval-hours", 6, 1, 168);
      this.profileDatabaseBackupRetentionCount =
          readBoundedInt("rpg.profile-database.backup-retention-count", 12, 1, 100);
      this.profileDatabaseWriteRetries =
          readBoundedInt("rpg.profile-database.write-retries", 2, 0, 3);
      this.profileDatabaseWriteRetryDelayMillis =
          readBoundedInt("rpg.profile-database.write-retry-delay-ms", 50, 10, 250);
      this.characterProgressionMaxLevel =
          readBoundedInt("rpg.progression.max-level", CharacterProgression.DEFAULT_MAX_LEVEL, 2, 100);
      this.characterProgressionBaseExperience =
          readBoundedInt("rpg.progression.base-experience", CharacterProgression.DEFAULT_BASE_EXPERIENCE,
              1, 1_000_000);
      this.characterProgressionExperiencePerLevel =
          readBoundedInt("rpg.progression.experience-per-level",
              CharacterProgression.DEFAULT_EXPERIENCE_PER_LEVEL, 0, 1_000_000);
      this.characterProgressionSkillPointsPerLevel =
          readBoundedInt("rpg.progression.skill-points-per-level",
              CharacterProgression.DEFAULT_SKILL_POINTS_PER_LEVEL, 0, 10);
      this.rpgMaxMana = readBoundedInt("rpg.skills.mana.max-mana", 100, 1, 1000);
      this.rpgManaRegenerationPerSecond =
          readBoundedInt("rpg.skills.mana.regeneration-per-second", 1, 0, 100);
      this.rpgBattleFocusDamageBonusPerRank =
          readBoundedDouble(this.config, "rpg.skills.passives.battle-focus.damage-bonus-per-rank",
              0.03, 0.0, 0.10);
      this.rpgFortifyDamageReductionPerStackPerRank =
          readBoundedDouble(this.config, "rpg.skills.passives.fortify.damage-reduction-per-stack-per-rank",
              0.02, 0.0, 0.05);
      this.rpgFortifyMaxStacks =
          readBoundedInt("rpg.skills.passives.fortify.max-stacks", 3, 1, 3);
      this.rpgFortifyStackDurationSeconds =
          readBoundedInt("rpg.skills.passives.fortify.stack-duration-seconds", 5, 1, 30);
      this.rpgManaFlowManaPerChainCastPerRank =
          readBoundedInt("rpg.skills.passives.mana-flow.mana-per-chain-cast-per-rank", 1, 0, 10);
      this.rpgManaFlowChainWindowSeconds =
          readBoundedInt("rpg.skills.passives.mana-flow.chain-window-seconds", 5, 1, 30);
      this.broadcastDeath = this.config.getBoolean("death.broadcast-death", true);
      this.playSound = this.config.getBoolean("death.play-sound", true);
      this.respawnProtectionEnabled = this.config.getBoolean("death.respawn-protection.enabled", true);
      this.respawnProtectionSeconds = this.config.getInt("death.respawn-protection.delay-seconds", 10);
      this.respawnMovementRadiusBlocks = this.config.getInt("death.respawn-protection.movement-radius-blocks", 10);
      this.respawnDamageImmunitySeconds = this.config.getInt("death.respawn-protection.damage-immunity-seconds", 10);
      if (this.respawnProtectionSeconds < 0) {
         this.plugin.getLogger().warning("[Config] death.respawn-protection.delay-seconds cannot be negative; using 0.");
         this.respawnProtectionSeconds = 0;
      }

      if (this.respawnMovementRadiusBlocks < 0) {
         this.plugin.getLogger().warning("[Config] death.respawn-protection.movement-radius-blocks cannot be negative; using 0.");
         this.respawnMovementRadiusBlocks = 0;
      }

      if (this.respawnDamageImmunitySeconds < 0) {
         this.plugin.getLogger().warning("[Config] death.respawn-protection.damage-immunity-seconds cannot be negative; using 0.");
         this.respawnDamageImmunitySeconds = 0;
      }

      this.deathPenaltyEnabled = this.config.getBoolean("death-penalty.enabled", true);
      this.deathPenaltyRecoverySeconds = this.config.getInt("death-penalty.recovery-seconds", 300);
      this.deathPenaltyMaxStack = this.config.getInt("death-penalty.max-penalty-stack", 5);
      this.deathPenaltyMaxHpPerDeath = this.config.getDouble("death-penalty.stats.max-hp-per-death", 0.1);
      this.deathPenaltyMaxThirstPerDeath = this.config.getDouble("death-penalty.stats.max-thirst-per-death", 0.1);
      this.deathPenaltyDamagePerDeath = this.config.getDouble("death-penalty.stats.damage-per-death", 0.1);
      this.deathPenaltyIncomingDamagePerDeath = this.config.getDouble("death-penalty.stats.incoming-damage-per-death", 0.1);
      this.deathPenaltyRegenPerDeath = this.config.getDouble("death-penalty.stats.regen-per-death", 0.1);
      this.deathPenaltyFoodDrainPerDeath = this.config.getDouble("death-penalty.stats.food-drain-per-death", 0.1);
      this.deathPenaltyThirstDrainPerDeath = this.config.getDouble("death-penalty.stats.thirst-drain-per-death", 0.1);
      this.mobDamageMultiplier = this.config.getDouble("combat.mob-damage-multiplier", 2.0);
      this.fallDamageMultiplier = this.config.getDouble("combat.fall-damage-multiplier", 2.0);
      this.fireDamageMultiplier = this.config.getDouble("combat.fire-damage-multiplier", 2.0);
      this.drowningDamageMultiplier = this.config.getDouble("combat.drowning-damage-multiplier", 2.0);
      this.explosionDamageMultiplier = this.config.getDouble("combat.explosion-damage-multiplier", 1.5);
      this.pvpDamageMultiplier = this.config.getDouble("combat.pvp-damage-multiplier", 1.25);
      this.mobExtraHpMultiplier = this.config.getDouble("combat.mob-extra-hp-multiplier", 1.5);
      this.disableNaturalRegen = this.config.getBoolean("combat.disable-natural-regen", false);
      this.regenMultiplier = this.config.getDouble("combat.regen-multiplier", 0.5);
      this.combatTagDurationMs = this.config.getLong("combat.combat-tag-duration-seconds", 30L) * 1000L;
      this.enderPearlDamage = this.config.getDouble("combat.ender-pearl-damage", 2.0);
      this.hungerEnabled = this.config.getBoolean("hunger.enabled", true);
      this.drainIntervalSeconds = this.config.getInt("hunger.drain-interval-seconds", 180);
      this.foodRestoreMultiplier = this.config.getDouble("hunger.food-restore-multiplier", 0.5);
      this.starvationDamage = this.config.getDouble("hunger.starvation-damage", 1.0);
      this.sprintMinFood = this.config.getInt("hunger.sprint-min-food", 6);
      this.rawFoodPoison = this.config.getBoolean("hunger.raw-food-poison", true);
      this.chokeEnabled = this.config.getBoolean("hunger.choke-enabled", true);
      this.chokeMaxEats = this.config.getInt("hunger.choke-max-eats", 3);
      this.chokeWindowSeconds = this.config.getInt("hunger.choke-window-seconds", 10);
      this.chokeDamage = this.config.getDouble("hunger.choke-damage", 1.0);
      this.notchAppleExplode = this.config.getBoolean("hunger.notch-apple-explode", true);
      this.notchAppleDamage = this.config.getDouble("hunger.notch-apple-damage", 2.0);
      this.thirstEnabled = this.config.getBoolean("thirst.enabled", true);
      this.thirstDrainIntervalSeconds = this.config.getInt("thirst.drain-interval-seconds", 120);
      this.thirstDamage = this.config.getDouble("thirst.thirst-damage", 1.0);
      this.maxThirst = this.config.getInt("thirst.max-thirst", 20);
      this.drinkFromSource = this.config.getBoolean("thirst.drink-from-source", true);
      this.drinkSourceRestore = this.config.getInt("thirst.drink-source-restore", 4);
      this.drinkSourceCooldownSeconds = this.config.getInt("thirst.drink-source-cooldown-seconds", 3);
      this.bucketDrinkEnabled = this.config.getBoolean("thirst.bucket-drink.enabled", true);
      this.bucketRestore = this.config.getInt("thirst.bucket-drink.restore", 10);
      this.bottleDrinkEnabled = this.config.getBoolean("thirst.bottle-drink.enabled", true);
      this.bottleRestore = this.config.getInt("thirst.bottle-drink.restore", 6);
      this.naturalWaterEnabled = this.config.getBoolean("thirst.natural-water.enabled", true);
      this.naturalWaterDamagePerSecond = this.config.getDouble("thirst.natural-water.damage-per-second", 0.5);
      this.naturalWaterDurationSeconds = this.config.getInt("thirst.natural-water.duration-seconds", 5);
      this.naturalWaterNauseaAmplifier = this.config.getInt("thirst.natural-water.nausea-amplifier", 0);
      this.bottleFill = this.config.getBoolean("thirst.bottle-fill", true);
      this.temperatureEnabled = this.config.getBoolean("environment.temperature.enabled", true);
      this.tempCheckIntervalSeconds = this.config.getInt("environment.temperature.check-interval-seconds", 30);
      this.heatDamage = this.config.getDouble("environment.temperature.heat-damage", 1.0);
      this.heatAmplifier = this.config.getInt("environment.temperature.heat-amplifier", 0);
      this.helmetReducePercent = this.config.getDouble("environment.temperature.helmet-reduce-percent", 0.5);
      this.helmetDamageReducePercent = this.config.getDouble("environment.temperature.helmet-damage-reduce-percent", 0.5);
      this.coldDamage = this.config.getDouble("environment.temperature.cold-damage", 1.0);
      this.coldAmplifier = this.config.getInt("environment.temperature.cold-amplifier", 0);
      this.armorReducePercent = this.config.getDouble("environment.temperature.armor-reduce-percent", 0.25);
      this.armorDamageReducePercent = this.config.getDouble("environment.temperature.armor-damage-reduce-percent", 0.2);
      this.heatExposureDelaySeconds = this.config.getInt("environment.temperature.heat-exposure-delay-seconds", 10);
      this.coldExposureDelaySeconds = this.config.getInt("environment.temperature.cold-exposure-delay-seconds", 10);
      this.waterDamage = this.config.getDouble("environment.temperature.water-damage", 1.0);
      this.acidRainDamage = this.config.getDouble("environment.temperature.acid-rain-damage", 1.0);
      this.hotBiomeKeys = this.loadBiomeKeys(
         "environment.temperature.biomes.hot",
         List.of("desert", "badlands", "wooded_badlands", "eroded_badlands", "savanna", "savanna_plateau", "windswept_savanna")
      );
      this.coldBiomeKeys = this.loadBiomeKeys(
         "environment.temperature.biomes.cold",
         List.of(
            "snowy_plains",
            "snowy_taiga",
            "snowy_slopes",
            "snowy_beach",
            "frozen_ocean",
            "frozen_river",
            "ice_spikes",
            "frozen_peaks",
            "jagged_peaks",
            "grove",
            "taiga",
            "old_growth_taiga",
            "old_growth_pine_taiga",
            "old_growth_spruce_taiga",
            "cold_ocean",
            "deep_cold_ocean"
         )
      );
      this.acidRainBiomeKeys = this.loadBiomeKeys(
         "environment.temperature.biomes.acid-rain",
         List.of(
            "forest",
            "flower_forest",
            "birch_forest",
            "old_growth_birch_forest",
            "dark_forest",
            "jungle",
            "sparse_jungle",
            "bamboo_jungle",
            "taiga",
            "old_growth_pine_taiga",
            "old_growth_spruce_taiga"
         )
      );
      this.waterDamageIntervalMs = this.config
         .getLong("environment.temperature.water-damage-interval-ms", this.config.getLong("environment.temperature.water-damage-interval-seconds", 10L) * 1000L);
      this.acidRainIntervalMs = this.config
         .getLong("environment.temperature.acid-rain-interval-ms", this.config.getLong("environment.temperature.acid-rain-interval-seconds", 5L) * 1000L);
      this.rainControlEnabled = this.config.getBoolean("environment.rain-control.enabled", true);
      int configuredRainChance = this.config.getInt("environment.rain-control.chance-percent", 40);
      if (configuredRainChance < 0 || configuredRainChance > 100) {
         this.plugin.getLogger().warning("[Config] environment.rain-control.chance-percent must be 0-100; clamping.");
      }

      this.rainChancePercent = Math.max(0, Math.min(100, configuredRainChance));
      int configuredMaxRainMinutes = this.config.getInt("environment.rain-control.max-duration-minutes", 5);
      if (configuredMaxRainMinutes < 1 || configuredMaxRainMinutes > 120) {
         this.plugin.getLogger().warning("[Config] environment.rain-control.max-duration-minutes must be 1-120; clamping.");
      }

      this.maxRainDurationMinutes = Math.max(1, Math.min(120, configuredMaxRainMinutes));
      this.rainRandomDurationEnabled = this.config.getBoolean("environment.rain-control.random-duration.enabled", true);
      int configuredMinRainMinutes = this.config.getInt("environment.rain-control.random-duration.minimum-minutes", 1);
      if (configuredMinRainMinutes < 1 || configuredMinRainMinutes > this.maxRainDurationMinutes) {
         this.plugin
            .getLogger()
            .warning("[Config] environment.rain-control.random-duration.minimum-minutes must be between 1 and max-duration-minutes; clamping.");
      }

      this.minRainDurationMinutes = Math.max(1, Math.min(this.maxRainDurationMinutes, configuredMinRainMinutes));
      this.fogEnabled = this.config.getBoolean("environment.fog.enabled", true);
      this.fogIntervalSeconds = this.config.getInt("environment.fog.interval-seconds", 60);
      this.fogEffectDurationSeconds = this.config.getInt("environment.fog.effect-duration-seconds", 30);
      this.fogEffectAmplifier = this.config.getInt("environment.fog.effect-amplifier", 0);
      this.sleepBlock = this.config.getBoolean("environment.sleep.block", true);
      this.sleepDamage = this.config.getDouble("environment.sleep.damage", 1.0);
      this.cropSlowEnabled = this.config.getBoolean("environment.crop-slow.enabled", true);
      this.cropSlowMultiplier = this.config.getDouble("environment.crop-slow.multiplier", 0.75);
      this.toolWearEnabled = this.config.getBoolean("environment.tool-wear.enabled", true);
      this.toolWearMultiplier = this.config.getDouble("environment.tool-wear.multiplier", 1.5);
      this.flightBlock = this.config.getBoolean("environment.flight.block", false);
      this.claustrophobiaEnabled = this.config.getBoolean("environment.claustrophobia.enabled", true);
      this.claustrophobiaYLevel = this.config.getInt("environment.claustrophobia.y-level", 30);
      this.claustrophobiaChance = this.config.getInt("environment.claustrophobia.chance", 5);
      this.claustrophobiaAmplifier = this.config.getInt("environment.claustrophobia.amplifier", 0);
      this.claustrophobiaTorchReducePercent = this.config.getDouble("environment.claustrophobia.torch-reduce-percent", 0.5);
      this.vertigoEnabled = this.config.getBoolean("environment.vertigo.enabled", true);
      this.vertigoYLevel = this.config.getInt("environment.vertigo.y-level", 100);
      this.vertigoChance = this.config.getInt("environment.vertigo.chance", 10);
      this.vertigoAmplifier = this.config.getInt("environment.vertigo.amplifier", 0);
      this.vertigoElytraReducePercent = this.config.getDouble("environment.vertigo.elytra-reduce-percent", 0.5);
      this.villagerTradingEnabled = this.config.getBoolean("villager-trading.enabled", true);
      this.disableRandomVillager = this.config.getBoolean("villager-trading.disable-random-villager", true);
      this.villagerRegionSize = this.config.getInt("villager-trading.region-size", 500);
      this.spawnerControlEnabled = this.config.getBoolean("spawner-control.enabled", true);
      this.spawnerSpawnRateReduction = this.config.getDouble("spawner-control.spawn-rate-reduction", 0.3);
      this.spawnerHpMultiplier = this.config.getDouble("spawner-control.hp-multiplier", 3.0);
      this.spawnerDamageMultiplier = this.config.getDouble("spawner-control.damage-multiplier", 2.0);
      // ===== LOAD FROM EXTERNAL FILES =====
      this.loadDisasterSettings();
      this.loadDisasterConfigs();
      this.loadDisasterMessages();
      this.loadBossSettings();
      this.loadBossItemStories();
      this.loadBossConfigs();
      this.validateBossItemStoryBosses();
      this.storyChapters = StoryChapterLoader.load(
          new File(plugin.getDataFolder(), "story/chapters.yml"), plugin.getLogger());
      this.oreControlEnabled = this.config.getBoolean("ore-control.enabled", false);
      this.oreControlWorlds.clear();
      ConfigurationSection oreSection = this.config.getConfigurationSection("ore-control.worlds");
      if (oreSection != null) {
         for (String worldName : oreSection.getKeys(false)) {
            ConfigurationSection worldSection = oreSection.getConfigurationSection(worldName);
            Map<String, Double> oreRates = new HashMap<>();
            if (worldSection != null) {
               for (String oreKey : worldSection.getKeys(false)) {
                  oreRates.put(oreKey, worldSection.getDouble(oreKey, 0.0));
               }
            }

            this.oreControlWorlds.put(worldName, oreRates);
         }
      }

      this.timeControlEnabled = this.config.getBoolean("time-control.enabled", true);
      String globalTimePath = this.config.isConfigurationSection("time-control.global") ? "time-control.global." : "time-control.";
      this.dayDurationMinutes = this.getPositiveMinutes(
         globalTimePath + "day-duration-minutes", this.config.getInt("time-control.day-duration-minutes", 10), 10
      );
      this.nightDurationMinutes = this.getPositiveMinutes(
         globalTimePath + "night-duration-minutes", this.config.getInt("time-control.night-duration-minutes", 10), 10
      );
      this.worldTimeConfigs.clear();
      ConfigurationSection worldTimeSection = this.config.getConfigurationSection("time-control.worlds");
      if (worldTimeSection != null) {
         for (String worldName : worldTimeSection.getKeys(false)) {
            ConfigurationSection worldSection = worldTimeSection.getConfigurationSection(worldName);
            if (worldSection == null) {
               this.plugin.getLogger().warning("[Config] time-control.worlds." + worldName + " must be a configuration section; using global time settings.");
            } else {
               ConfigManager.WorldTimeConfig worldTimeConfig = new ConfigManager.WorldTimeConfig();
               String worldPath = "time-control.worlds." + worldName + ".";
               if (worldSection.contains("day-duration-minutes")) {
                  worldTimeConfig.dayDurationMinutes = this.getPositiveMinutes(
                     worldPath + "day-duration-minutes", worldSection.getInt("day-duration-minutes"), this.dayDurationMinutes
                  );
               }

               if (worldSection.contains("night-duration-minutes")) {
                  worldTimeConfig.nightDurationMinutes = this.getPositiveMinutes(
                     worldPath + "night-duration-minutes", worldSection.getInt("night-duration-minutes"), this.nightDurationMinutes
                  );
               }

               this.worldTimeConfigs.put(worldName, worldTimeConfig);
            }
         }
      }

      this.loadBlockMiningRules();
      this.renameEnabled = this.config.getBoolean("rename.enabled", true);
      this.nameStructure = this.config.getString("rename.name-structure", "<name> ☠ 000");
      this.consoleDebug = this.config.getBoolean("logging.console-debug", false);
      this.logDeaths = this.config.getBoolean("logging.log-deaths", true);
      this.logDisasters = this.config.getBoolean("logging.log-disasters", true);
      this.plugin.getLogger().info("[Config] Loaded configuration with " + this.config.getKeys(true).size() + " keys");
      this.plugin.getLogger().info("[Config] Loaded " + this.bossConfigs.size() + " bosses from bosses/");
      this.plugin.getLogger().info("[Config] Loaded " + this.disasterConfigs.size() + " disasters from disasters/");
   }

    private int getPositiveMinutes(String path, int minutes, int defaultValue) {
        if (minutes > 0) return minutes;

        plugin.getLogger().warning("[Config] " + path + " must be greater than 0; using " + defaultValue + ".");
        return defaultValue;
    }

    public int getDayDurationMinutes(String worldName) {
        WorldTimeConfig worldConfig = worldTimeConfigs.get(worldName);
        return worldConfig != null && worldConfig.dayDurationMinutes != null
            ? worldConfig.dayDurationMinutes
            : dayDurationMinutes;
    }

    public int getNightDurationMinutes(String worldName) {
        WorldTimeConfig worldConfig = worldTimeConfigs.get(worldName);
        return worldConfig != null && worldConfig.nightDurationMinutes != null
            ? worldConfig.nightDurationMinutes
            : nightDurationMinutes;
    }

    public boolean setGlobalDayDurationMinutes(int minutes) {
        if (minutes < 1) return false;
        return setGlobalTimeDuration("day-duration-minutes", minutes, true);
    }

    public boolean setGlobalNightDurationMinutes(int minutes) {
        if (minutes < 1) return false;
        return setGlobalTimeDuration("night-duration-minutes", minutes, false);
    }

    public boolean setWorldDayDurationMinutes(String worldName, int minutes) {
        if (worldName == null || worldName.isBlank() || minutes < 1) return false;
        return setWorldTimeDuration(worldName, "day-duration-minutes", minutes, true);
    }

    public boolean setWorldNightDurationMinutes(String worldName, int minutes) {
        if (worldName == null || worldName.isBlank() || minutes < 1) return false;
        return setWorldTimeDuration(worldName, "night-duration-minutes", minutes, false);
    }

   private boolean setGlobalTimeDuration(String setting, int minutes, boolean day) {
      String path = this.config.isConfigurationSection("time-control.global") ? "time-control.global." + setting : "time-control." + setting;
      Object previousValue = this.config.get(path);
      this.config.set(path, minutes);

      try {
         this.config.save(new File(this.plugin.getDataFolder(), "config.yml"));
      } catch (IOException exception) {
         this.config.set(path, previousValue);
         this.plugin.getLogger().log(Level.SEVERE, "[Config] Could not save " + path + ".", exception);
         return false;
      }

      if (day) {
         this.dayDurationMinutes = minutes;
      } else {
         this.nightDurationMinutes = minutes;
      }

      return true;
   }

   private boolean setWorldTimeDuration(String worldName, String setting, int minutes, boolean day) {
      String path = "time-control.worlds." + worldName + "." + setting;
      Object previousValue = this.config.get(path);
      this.config.set(path, minutes);

      try {
         this.config.save(new File(this.plugin.getDataFolder(), "config.yml"));
      } catch (IOException exception) {
         this.config.set(path, previousValue);
         this.plugin.getLogger().log(Level.SEVERE, "[Config] Could not save " + path + ".", exception);
         return false;
      }

      ConfigManager.WorldTimeConfig worldConfig = this.worldTimeConfigs.computeIfAbsent(worldName, ignored -> new ConfigManager.WorldTimeConfig());
      if (day) {
         worldConfig.dayDurationMinutes = minutes;
      } else {
         worldConfig.nightDurationMinutes = minutes;
      }

      return true;
   }

    private Set<String> loadBiomeKeys(String path, List<String> fallbackBiomeKeys) {
        List<String> configuredBiomeKeys = config.isList(path)
            ? config.getStringList(path) : fallbackBiomeKeys;
        Set<String> normalizedKeys = new HashSet<>();
        for (String configuredKey : configuredBiomeKeys) {
            if (configuredKey == null || configuredKey.isBlank()) continue;
            String key = configuredKey.trim().toLowerCase(Locale.ROOT);
            normalizedKeys.add(key.contains(":") ? key : "minecraft:" + key);
        }
        return Set.copyOf(normalizedKeys);
    }

    private void loadBlockMiningRules() {
        blockDropSettingsEnabled = config.getBoolean("block-drop-settings.enabled", false);
        dropReplaceEnabled = config.getBoolean("block-drop-settings.replace-enabled", true);
        toolBreakEnabled = config.getBoolean("block-drop-settings.break-enabled", true);
        oreExplodeEnabled = config.getBoolean("block-drop-settings.explode-enabled", true);
        blockMiningRules.clear();

        List<Map<?, ?>> rules = config.getMapList("block-drop-settings.rules");
        for (int index = 0; index < rules.size(); index++) {
            Map<?, ?> ruleMap = rules.get(index);
            Material blockMaterial = parseMaterial(ruleMap.get("block"),
                "block-drop-settings.rules[" + index + "].block");
            if (blockMaterial == null || !blockMaterial.isBlock()) continue;

            for (Map.Entry<?, ?> entry : ruleMap.entrySet()) {
                if (!(entry.getKey() instanceof String toolName) || "block".equalsIgnoreCase(toolName)) continue;
                Material toolMaterial = parseMaterial(toolName,
                    "block-drop-settings.rules[" + index + "]." + toolName);
                if (toolMaterial == null || !(entry.getValue() instanceof Map<?, ?> toolMap)) continue;

                BlockMiningRule miningRule = new BlockMiningRule();
                Map<?, ?> replaceMap = nestedMap(toolMap.get("drop-replace"));
                if (replaceMap != null) {
                    miningRule.replaceEnabled = readBoolean(replaceMap, "enabled", false);
                    miningRule.replaceMaterial = parseMaterial(replaceMap.get("replace-block"),
                        "block-drop-settings.rules[" + index + "]." + toolName + ".drop-replace.replace-block");
                    miningRule.replaceChance = readChance(replaceMap, "chance",
                        "block-drop-settings.rules[" + index + "]." + toolName + ".drop-replace.chance");
                    if (miningRule.replaceMaterial != null &&
                        (!miningRule.replaceMaterial.isItem() || miningRule.replaceMaterial.isAir())) {
                        plugin.getLogger().warning("[Config] Replacement material " +
                            miningRule.replaceMaterial + " is not an item; drop replacement disabled.");
                        miningRule.replaceEnabled = false;
                    }
                }

                Map<?, ?> breakMap = nestedMap(toolMap.get("tool-break"));
                if (breakMap != null) {
                    miningRule.toolBreakEnabled = readBoolean(breakMap, "enabled", false);
                    miningRule.toolBreakChance = readChance(breakMap, "chance",
                        "block-drop-settings.rules[" + index + "]." + toolName + ".tool-break.chance");
                    miningRule.toolBreakMessage = readString(breakMap, "message", "");
                }

                Map<?, ?> explodeMap = nestedMap(toolMap.get("explode"));
                if (explodeMap != null) {
                    miningRule.explodeEnabled = readBoolean(explodeMap, "enabled", false);
                    miningRule.explosionPower = (float) readNonNegativeNumber(explodeMap, "power",
                        "block-drop-settings.rules[" + index + "]." + toolName + ".explode.power", 0.0);
                    miningRule.explosionBreakBlocks = readBoolean(explodeMap, "break-blocks", false);
                    miningRule.explosionSetFire = readBoolean(explodeMap, "set-fire", false);
                    miningRule.explosionDamagePlayer = readBoolean(explodeMap, "damage-player", true);
                    miningRule.explosionChance = readChance(explodeMap, "chance",
                        "block-drop-settings.rules[" + index + "]." + toolName + ".explode.chance");
                    miningRule.explosionMessage = readString(explodeMap, "message", "");
                    if (miningRule.explosionPower <= 0.0f) miningRule.explodeEnabled = false;
                }

                blockMiningRules.computeIfAbsent(blockMaterial, ignored -> new HashMap<>())
                    .put(toolMaterial, miningRule);
            }
        }
    }

    private Material parseMaterial(Object value, String path) {
        if (!(value instanceof String name) || name.isBlank()) {
            plugin.getLogger().warning("[Config] Missing or invalid material at " + path + ".");
            return null;
        }
        Material material = Material.matchMaterial(name.trim());
        if (material == null) plugin.getLogger().warning("[Config] Unknown material '" + name + "' at " + path + ".");
        return material;
    }

    private Map<?, ?> nestedMap(Object value) {
        return value instanceof Map<?, ?> map ? map : null;
    }

    private boolean readBoolean(Map<?, ?> values, String key, boolean defaultValue) {
        Object value = values.get(key);
        return value instanceof Boolean bool ? bool : defaultValue;
    }

    private String readString(Map<?, ?> values, String key, String defaultValue) {
        Object value = values.get(key);
        return value instanceof String string ? string : defaultValue;
    }

    private double readChance(Map<?, ?> values, String key, String path) {
        double chance = readNonNegativeNumber(values, key, path, 0.0);
        if (chance <= 100.0) return chance;

        plugin.getLogger().warning("[Config] " + path + " must be between 0 and 100; using 0.");
        return 0.0;
    }

    private double readNonNegativeNumber(Map<?, ?> values, String key, String path, double defaultValue) {
        Object value = values.get(key);
        if (!(value instanceof Number number)) return defaultValue;
        double result = number.doubleValue();
        if (Double.isFinite(result) && result >= 0.0) return result;

        plugin.getLogger().warning("[Config] " + path + " must be a finite non-negative number; using " +
            defaultValue + ".");
        return defaultValue;
    }

    private void migrateDeathPenaltyDefaults() {
        int version = config.getInt("config-version", 0);
        if (version >= 2) return;

        migrateDouble("death-penalty.stats.max-hp-per-death", 1.0, 0.1);
        migrateDouble("death-penalty.stats.max-thirst-per-death", 1.0, 0.1);
        migrateDouble("death-penalty.stats.damage-per-death", 0.5, 0.1);
        migrateDouble("death-penalty.stats.incoming-damage-per-death", 0.5, 0.1);
        migrateDouble("death-penalty.stats.regen-per-death", 0.5, 0.1);
        migrateDouble("death-penalty.stats.food-drain-per-death", 0.5, 0.1);
        migrateDouble("death-penalty.stats.thirst-drain-per-death", 0.5, 0.1);
        config.set("config-version", 2);
        plugin.saveConfig();
        plugin.getLogger().info("[Config] Migrated bundled death-penalty defaults to 10% per death; custom values were preserved.");
    }

    private void migrateDouble(String path, double oldDefault, double newDefault) {
        if (config.contains(path) && Double.compare(config.getDouble(path), oldDefault) == 0) {
            config.set(path, newDefault);
        }
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

        // Merge bundled samples into old disaster files without overwriting admin values.
        mergeDefaultResource("disasters/_settings.yml");
        mergeDefaultResource("disasters/blood-moon.yml");
        mergeDefaultResource("disasters/meteor.yml");
        mergeDefaultResource("disasters/mega-storm.yml");
        mergeDefaultResource("disasters/solar-flare.yml");
        mergeDefaultResource("disasters/plague.yml");
        mergeDefaultResource("disasters/tornado.yml");
        mergeDefaultResource("disasters/eclipse.yml");
        mergeDefaultResource("disasters/earthquake.yml");
        mergeDefaultResource("disasters/inferno-storm.yml");
        mergeDefaultResource("disasters/soul-eruption.yml");
        mergeDefaultResource("disasters/lava-geyser.yml");
        mergeDefaultResource("disasters/end-surge.yml");
        mergeDefaultResource("disasters/void-storm.yml");
        mergeDefaultResource("disasters/chorus-explosion.yml");

        saveResourceIfNotExists("scoreboard.yml");
        mergeDefaultResource("scoreboard.yml");
        mergeDefaultResource("story.yml");
        saveResourceIfNotExists("story/chapters.yml");
        mergeDefaultResource("story/chapters.yml");

        // Merge new boss settings into existing files without overwriting admin values.
        mergeDefaultResource("bosses/_settings.yml");
        mergeDefaultResource("bosses/wither.yml");
        mergeDefaultResource("bosses/ender_dragon.yml");
        mergeDefaultResource("bosses/ghast.yml");
        mergeDefaultResource("bosses/zombie_boss.yml");
        mergeDefaultResource("bosses/skeleton_boss.yml");
        mergeDefaultResource("bosses/spider_boss.yml");
        mergeDefaultResource("bosses/creeper_boss.yml");
        mergeDefaultResource("bosses/enderman_boss.yml");
        mergeDefaultResource("bosses/witch_boss.yml");
        mergeDefaultResource("bosses/ravager_boss.yml");
        mergeDefaultResource("bosses/vindicator_boss.yml");
        mergeDefaultResource("bosses/phantom_boss.yml");
        mergeDefaultResource("bosses/giant_boss.yml");
    }

    private void mergeDefaultResource(String resourcePath) {
        File targetFile = new File(plugin.getDataFolder(), resourcePath);
        if (!targetFile.exists()) {
            saveResourceIfNotExists(resourcePath);
            return;
        }

        try (InputStream resource = plugin.getResource(resourcePath)) {
            if (resource == null) {
                plugin.getLogger().warning("[Config] Missing bundled default resource: " + resourcePath);
                return;
            }

            YamlConfiguration target = new YamlConfiguration();
            try {
                target.load(targetFile);
            } catch (IOException | InvalidConfigurationException exception) {
                plugin.getLogger().severe("[Config] Cannot merge defaults into invalid " + resourcePath +
                    ": " + exception.getMessage());
                return;
            }

            YamlConfiguration defaults;
            try (InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
                defaults = YamlConfiguration.loadConfiguration(reader);
            }

            if (YamlConfigMerger.mergeMissingKeys(target, defaults, plugin.getLogger())) {
                target.save(targetFile);
                plugin.getLogger().info("[Config] Added missing defaults to " + resourcePath +
                    " (existing values were preserved)");
            }
        } catch (IOException exception) {
            plugin.getLogger().severe("[Config] Cannot update " + resourcePath + ": " + exception.getMessage());
        }
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
            disasterDefaultSuccessProbability = 100;
            disasterSuccessThresholds.clear();
            safeZoneEnabled = false;
            return;
        }

        FileConfiguration ds = YamlConfiguration.loadConfiguration(settingsFile);

        disastersEnabled = ds.getBoolean("enabled", true);
        disasterMinIntervalRaw = getStringFromConfig(ds.getConfigurationSection(""), "min-interval-seconds", "2400-4800");
        disasterWarningSeconds = ds.getInt("warning-seconds", 120);
        disasterDefaultSuccessProbability = clampPercent(
            ds.getInt("success-probability.default-percent", 100));
        disasterSuccessThresholds.clear();
        for (Map<?, ?> row : ds.getMapList("success-probability.day-thresholds")) {
            Object minDayValue = row.get("min-day");
            Object probabilityValue = row.get("percent");
            if (!(minDayValue instanceof Number minDayNumber)
                || !(probabilityValue instanceof Number probabilityNumber)) {
                continue;
            }
            int minDay = minDayNumber.intValue();
            if (minDay < 0) continue;
            disasterSuccessThresholds.add(new DisasterSuccessThreshold(
                minDay, clampPercent(probabilityNumber.intValue())));
        }
        disasterSuccessThresholds.sort((first, second) -> Integer.compare(first.minDay, second.minDay));

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

    private int clampPercent(int percent) {
        return Math.max(0, Math.min(100, percent));
    }

    private double readBoundedDouble(ConfigurationSection section, String path,
                                     double fallback, double minimum, double maximum) {
        double value = section.getDouble(path, fallback);
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            plugin.getLogger().warning("[Boss] " + path + " must be between " + minimum +
                " and " + maximum + "; using a clamped value.");
            return Double.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : fallback;
        }
        return value;
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
            dc.name = EconomyManager.sanitizeVisibleName(section.getString("name", disasterId));
            dc.worlds = section.getStringList("conditions.worlds");
            dc.chatDescription = section.getString("chat-description", "");
            dc.effectIntervalSeconds = section.getInt("effect-interval-seconds", 5);
            dc.effectDurationSeconds = section.getInt("effect-duration-seconds", 5);
            dc.durationSeconds = section.getInt("duration-seconds", 600);

            // Custom title/subtitle for disaster start
            dc.startTitle = EconomyManager.sanitizeVisibleName(section.getString("start-title", ""));
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
      File settingsFile = new File(this.plugin.getDataFolder(), "bosses/_settings.yml");
      if (!settingsFile.exists()) {
         File settingsDirectory = settingsFile.getParentFile();
         if (settingsDirectory != null && !settingsDirectory.exists() && !settingsDirectory.mkdirs()) {
            this.plugin.getLogger().warning("[Boss] Could not create bosses directory; boss settings will use defaults.");
         }

         this.plugin.saveResource("bosses/_settings.yml", false);
      }

      FileConfiguration bs = YamlConfiguration.loadConfiguration(settingsFile);
      this.bossMercenaryGearDropDefaults = this.readBossMercenaryGearDropConfig(
         bs.getConfigurationSection("mercenary-gear-drops"), this.defaultBossMercenaryGearDropConfig(), "bosses/_settings.yml"
      );
      boolean migrateBossSettings = false;
      if (Math.abs(bs.getDouble("army-token-drop.chance", 0.3) - 0.35) < 1.0E-6) {
         bs.set("army-token-drop.chance", 0.3);
         bs.setComments(
            "army-token-drop.chance",
            List.of(
               "[VI] Tỉ lệ rơi 1 token khi boss bị người chơi gây sát thương; mặc định 30%, hợp lệ 0-1.",
               "[EN] Chance to drop 1 token when a player damages the boss; default 30%, valid range 0-1."
            )
         );
         migrateBossSettings = true;
      }

      if (!bs.contains("experience-pool.level-1")) {
         bs.set("experience-pool.level-1", 50);
         bs.setComments(
            "experience-pool.level-1",
            List.of(
               "[VI] Tổng EXP chia cho người chơi gây sát thương khi boss level 1; mặc định 50, hợp lệ 1-1000000.",
               "[EN] Total XP pool shared among damage contributors for a level-1 boss; default 50, valid range 1-1000000."
            )
         );
         migrateBossSettings = true;
      }

      if (!bs.contains("experience-pool.level-10")) {
         bs.set("experience-pool.level-10", 150);
         bs.setComments(
            "experience-pool.level-10",
            List.of(
               "[VI] Tổng EXP chia cho người chơi gây sát thương khi boss level 10; mặc định 150, hợp lệ 1-1000000.",
               "[EN] Total XP pool shared among damage contributors for a level-10 boss; default 150, valid range 1-1000000."
            )
         );
         migrateBossSettings = true;
      }

      if (!bs.contains("default-duration-seconds")) {
         bs.set("default-duration-seconds", 1800);
         bs.setComments(
            "default-duration-seconds",
            List.of(
               "[VI] Thời gian tồn tại mặc định của boss (giây); mặc định 1800 (30 phút), hợp lệ 60-86400.",
               "[EN] Default boss lifetime in seconds; default 1800 (30 minutes), valid range 60-86400."
            )
         );
         migrateBossSettings = true;
      }

      if (bs.contains("experience-per-level-multiplier")) {
         bs.setComments(
            "experience-per-level-multiplier",
            List.of(
               "[VI] Cấu hình cũ, không còn được dùng; EXP hiện lấy từ experience-pool và chia theo sát thương.",
               "[EN] Legacy setting, no longer used; XP now comes from experience-pool and is shared by damage."
            )
         );
      }

      this.bossDefaultDurationSeconds = bs.getInt("default-duration-seconds", 1800);
      if (this.bossDefaultDurationSeconds < 60 || this.bossDefaultDurationSeconds > 86400) {
         this.plugin.getLogger().warning("[Boss] default-duration-seconds must be 60-86400; using 1800.");
         this.bossDefaultDurationSeconds = 1800;
      }

      if (!bs.getBoolean("migrations.boss-duration-30-minutes", false) && this.migrateLegacyBossDurations(this.bossDefaultDurationSeconds)) {
         bs.set("migrations.boss-duration-30-minutes", true);
         bs.setComments(
            "migrations.boss-duration-30-minutes",
            List.of(
               "[VI] Cờ nội bộ xác nhận đã chuyển thời lượng mặc định boss từ 1200 sang 1800 giây.",
               "[EN] Internal marker confirming the default boss duration was migrated from 1200 to 1800 seconds."
            )
         );
         migrateBossSettings = true;
      }

      if (migrateBossSettings) {
         try {
            bs.save(settingsFile);
         } catch (IOException exception) {
            this.plugin.getLogger().log(Level.SEVERE, "[Boss] Could not migrate bosses/_settings.yml.", exception);
         }
      }

      this.bossEventsEnabled = bs.getBoolean("enabled", true);
      this.bossSkillCastLogsEnabled = bs.getBoolean("skill-cast-feedback.console-log", true);
      this.bossSkillCastOpBroadcastEnabled = bs.getBoolean("skill-cast-feedback.broadcast-to-ops", true);
      this.bossSkillCastPlayerBroadcastEnabled = bs.getBoolean("skill-cast-feedback.broadcast-to-players", true);
      double rewardDamageThreshold = bs.getDouble("reward-damage-threshold-percent", 50.0);
      if (!Double.isFinite(rewardDamageThreshold) || rewardDamageThreshold < 0.0 || rewardDamageThreshold > 100.0) {
         this.plugin.getLogger().warning("[Boss] reward-damage-threshold-percent must be 0-100; clamping.");
      }

      this.bossRewardDamageThresholdPercent = Double.isFinite(rewardDamageThreshold) ? Math.max(0.0, Math.min(100.0, rewardDamageThreshold)) : 50.0;
      this.bossMaxPlayerHitDamage = this.readBoundedDouble(bs, "max-player-hit-damage", 6.0, 0.0, 100.0);
      this.bossRandomHealthEnabled = bs.getBoolean("boss-health.enabled", true);
      this.bossHealthLevelOneMinimum = bs.getInt("boss-health.level-1-minimum", 2000);
      this.bossHealthLevelOneMaximum = bs.getInt("boss-health.level-1-maximum", 4000);
      this.bossHealthLevelTenMinimum = bs.getInt("boss-health.level-10-minimum", 45000);
      this.bossHealthLevelTenMaximum = bs.getInt("boss-health.level-10-maximum", 50000);
      if (this.bossHealthLevelOneMinimum < 1
         || this.bossHealthLevelOneMaximum < this.bossHealthLevelOneMinimum
         || this.bossHealthLevelTenMinimum < this.bossHealthLevelOneMaximum
         || this.bossHealthLevelTenMaximum < this.bossHealthLevelTenMinimum
         || this.bossHealthLevelTenMaximum > 1000000) {
         this.plugin.getLogger().warning("[Boss] Invalid boss-health ranges; using defaults 2000-4000 and 45000-50000.");
         this.bossHealthLevelOneMinimum = 2000;
         this.bossHealthLevelOneMaximum = 4000;
         this.bossHealthLevelTenMinimum = 45000;
         this.bossHealthLevelTenMaximum = 50000;
      }

      this.spiderBossDamageDebugEnabled = bs.getBoolean("debug.spider-boss-damage", true);
      this.bossSizeEnabled = bs.getBoolean("boss-size.enabled", true);
      this.bossSizeMultiplier = this.readBoundedDouble(bs, "boss-size.multiplier", 2.0, 1.0, 4.0);
      this.bossSizeEntityTypes = this.readBossEntityTypes(
         bs, "boss-size.entity-types", List.of("ZOMBIE", "SKELETON", "SPIDER", "CREEPER", "ENDERMAN", "WITCH", "VINDICATOR", "PHANTOM")
      );
      this.bossFlightHeightEnabled = bs.getBoolean("boss-flight-height.enabled", true);
      int flightHeight = bs.getInt("boss-flight-height.max-height-above-ground", 30);
      if (flightHeight < 1 || flightHeight > 256) {
         this.plugin.getLogger().warning("[Boss] boss-flight-height.max-height-above-ground must be 1-256; clamping.");
      }

      this.bossMaxFlightHeightAboveGround = Math.max(1, Math.min(256, flightHeight));
      int flightTargetRange = bs.getInt("boss-flight-height.target-range-horizontal", 30);
      if (flightTargetRange < 1 || flightTargetRange > 128) {
         this.plugin.getLogger().warning("[Boss] boss-flight-height.target-range-horizontal must be 1-128; clamping.");
      }

      this.bossFlightTargetRange = Math.max(1, Math.min(128, flightTargetRange));
      int preferredCombatDistance = bs.getInt("boss-flight-height.preferred-combat-distance", 12);
      if (preferredCombatDistance < 4 || preferredCombatDistance > 30) {
         this.plugin.getLogger().warning("[Boss] boss-flight-height.preferred-combat-distance must be 4-30; clamping.");
      }

      this.bossFlightPreferredCombatDistance = Math.max(4, Math.min(30, preferredCombatDistance));
      this.bossFlightEntityTypes = this.readBossEntityTypes(bs, "boss-flight-height.entity-types", List.of("WITHER", "ENDER_DRAGON", "GHAST", "PHANTOM"));
      this.bossAiEnabled = bs.getBoolean("boss-ai.enabled", true);
      this.bossAiDamageWeight = this.readBoundedDouble(bs, "boss-ai.targeting.damage-weight", 60.0, 0.0, 100.0);
      this.bossAiProximityWeight = this.readBoundedDouble(bs, "boss-ai.targeting.proximity-weight", 25.0, 0.0, 100.0);
      this.bossAiLowHealthWeight = this.readBoundedDouble(bs, "boss-ai.targeting.low-health-weight", 15.0, 0.0, 100.0);
      this.bossAiRetargetScoreMargin = this.readBoundedDouble(bs, "boss-ai.targeting.retarget-score-margin", 8.0, 0.0, 100.0);
      this.bossAiGroundPathfindingSpeed = this.readBoundedDouble(bs, "boss-ai.movement.ground-pathfinding-speed", 1.1, 0.5, 2.0);
      this.bossAiProjectileDodgeEnabled = bs.getBoolean("boss-ai.dodge-projectiles.enabled", true);
      int dodgeRange = bs.getInt("boss-ai.dodge-projectiles.range", 10);
      if (dodgeRange < 4 || dodgeRange > 20) {
         this.plugin.getLogger().warning("[Boss] boss-ai.dodge-projectiles.range must be 4-20; clamping.");
      }

      this.bossAiProjectileDodgeRange = Math.max(4, Math.min(20, dodgeRange));
      int dodgeCooldown = bs.getInt("boss-ai.dodge-projectiles.cooldown-ticks", 20);
      if (dodgeCooldown < 5 || dodgeCooldown > 200) {
         this.plugin.getLogger().warning("[Boss] boss-ai.dodge-projectiles.cooldown-ticks must be 5-200; clamping.");
      }

      this.bossAiProjectileDodgeCooldownTicks = Math.max(5, Math.min(200, dodgeCooldown));
      this.bossAiPhasesEnabled = bs.getBoolean("boss-ai.phases.enabled", true);
      this.bossAiPhaseTwoHealthPercent = this.readBoundedDouble(bs, "boss-ai.phases.phase-two-health-percent", 60.0, 1.0, 99.0);
      this.bossAiPhaseThreeHealthPercent = this.readBoundedDouble(bs, "boss-ai.phases.phase-three-health-percent", 30.0, 0.0, 98.0);
      if (this.bossAiPhaseThreeHealthPercent >= this.bossAiPhaseTwoHealthPercent) {
         this.plugin.getLogger().warning("[Boss] Phase three threshold must be below phase two; adjusting it.");
         this.bossAiPhaseThreeHealthPercent = Math.min(30.0, this.bossAiPhaseTwoHealthPercent - 1.0);
      }

      this.bossAiMinionCoordinationEnabled = bs.getBoolean("boss-ai.minion-coordination.enabled", true);
      this.bossArmyTokenDropEnabled = bs.getBoolean("army-token-drop.enabled", true);
      this.bossArmyTokenDropChance = this.readBoundedDouble(bs, "army-token-drop.chance", 0.3, 0.0, 1.0);
      int levelOneExperience = bs.getInt("experience-pool.level-1", 50);
      int levelTenExperience = bs.getInt("experience-pool.level-10", 150);
      if (levelOneExperience >= 1 && levelOneExperience <= 1000000 && levelTenExperience >= levelOneExperience && levelTenExperience <= 1000000) {
         this.bossExperienceLevelOne = levelOneExperience;
         this.bossExperienceLevelTen = levelTenExperience;
      } else {
         this.plugin.getLogger().warning("[Boss] Invalid experience-pool range; using defaults 50-150.");
         this.bossExperienceLevelOne = 50;
         this.bossExperienceLevelTen = 150;
      }

      this.bossArmorPieceDropChance = this.readBoundedDouble(bs, "reward-items.armor-piece-chance", 0.3, 0.0, 1.0);
      int armorLevelGap = bs.getInt("reward-items.armor-natural-level-gap", 2);
      if (armorLevelGap < 0 || armorLevelGap > 5) {
         this.plugin.getLogger().warning("[Boss] reward-items.armor-natural-level-gap must be 0-5; clamping.");
      }

      this.bossArmorNaturalLevelGap = Math.max(0, Math.min(5, armorLevelGap));
      this.bossArmorLevelsPerEnchant = this.readBoundedDouble(bs, "reward-items.enchant-levels-per-boss-level", 0.8, 0.0, 5.0);
      int maximumArmorEnchantLevel = bs.getInt("reward-items.maximum-enchant-level", 5);
      if (maximumArmorEnchantLevel < 1 || maximumArmorEnchantLevel > 255) {
         this.plugin.getLogger().warning("[Boss] reward-items.maximum-enchant-level must be 1-255; clamping.");
      }

      this.bossArmorMaximumEnchantLevel = Math.max(1, Math.min(255, maximumArmorEnchantLevel));
      this.bossArmorAllowUnsafeEnchants = bs.getBoolean("reward-items.allow-over-vanilla-enchants", true);
      this.bossEventMinIntervalRaw = getStringFromConfig(bs.getConfigurationSection(""), "min-interval-seconds", "1200-2400");
      this.bossEventSpawnRadius = bs.getInt("spawn-radius", 50);
      this.bossLevelProgressionEnabled = bs.getBoolean("level-progression.enabled", true);
      this.bossLevelThresholds.clear();

      for (Map<?, ?> thresholdMap : bs.getMapList("level-progression.thresholds")) {
         Object dayValue = thresholdMap.get("min-day");
         Object minLevelValue = thresholdMap.get("min-level");
         Object maxLevelValue = thresholdMap.get("max-level");
         if (dayValue instanceof Number dayNumber && minLevelValue instanceof Number minNumber && maxLevelValue instanceof Number maxNumber) {
            ConfigManager.BossLevelThreshold threshold = new ConfigManager.BossLevelThreshold();
            threshold.minDay = Math.max(0L, dayNumber.longValue());
            threshold.minLevel = Math.max(1, minNumber.intValue());
            threshold.maxLevel = Math.max(threshold.minLevel, maxNumber.intValue());
            this.bossLevelThresholds.add(threshold);
         } else {
            this.plugin.getLogger().warning("[Boss] Skipping malformed level-progression threshold in bosses/_settings.yml.");
         }
      }

      this.bossLevelThresholds.sort(Comparator.comparingLong(thresholdx -> thresholdx.minDay));
      this.bossSpawnSuccessProgressionEnabled = bs.getBoolean("spawn-success-progression.enabled", true);
      double defaultChance = bs.getDouble("spawn-success-progression.default-percent", 100.0);
      if (!Double.isFinite(defaultChance)) {
         this.plugin.getLogger().warning("[Boss] Invalid spawn-success-progression.default-percent; using 100%.");
         defaultChance = 100.0;
      }

      this.bossSpawnSuccessDefaultPercent = Math.max(0.0, Math.min(100.0, defaultChance));
      if (this.bossSpawnSuccessDefaultPercent != defaultChance) {
         this.plugin.getLogger().warning("[Boss] Clamped default boss spawn success chance to " + this.bossSpawnSuccessDefaultPercent + "%.");
      }

      this.bossSpawnSuccessThresholds.clear();

      for (Map<?, ?> thresholdMap : bs.getMapList("spawn-success-progression.thresholds")) {
         Object dayValue = thresholdMap.get("min-day");
         Object chanceValue = thresholdMap.get("chance-percent");
         if (dayValue instanceof Number dayNumber && chanceValue instanceof Number chanceNumber) {
            double chance = chanceNumber.doubleValue();
            if (!Double.isFinite(chance)) {
               this.plugin.getLogger().warning("[Boss] Skipping non-finite spawn success chance in bosses/_settings.yml.");
            } else {
               ConfigManager.BossSpawnSuccessThreshold threshold = new ConfigManager.BossSpawnSuccessThreshold();
               threshold.minDay = Math.max(0L, dayNumber.longValue());
               threshold.chancePercent = Math.max(0.0, Math.min(100.0, chance));
               if (threshold.chancePercent != chance) {
                  this.plugin.getLogger().warning("[Boss] Clamped spawn success chance at day " + threshold.minDay + " to " + threshold.chancePercent + "%.");
               }

               this.bossSpawnSuccessThresholds.add(threshold);
            }
         } else {
            this.plugin.getLogger().warning("[Boss] Skipping malformed spawn-success-progression threshold in bosses/_settings.yml.");
         }
      }

      this.bossSpawnSuccessThresholds.sort(Comparator.comparingLong(thresholdx -> thresholdx.minDay));
      this.plugin
         .getLogger()
         .info(
            "[Boss] Loaded "
               + this.bossLevelThresholds.size()
               + " daily level threshold(s) and "
               + this.bossSpawnSuccessThresholds.size()
               + " daily spawn-success threshold(s) from bosses/_settings.yml."
         );
   }

   private Set<String> readBossEntityTypes(ConfigurationSection section, String path, List<String> defaults) {
      List<String> configured = section.contains(path) ? section.getStringList(path) : defaults;
      Set<String> entityTypes = new HashSet<>();

      for (String value : configured) {
         String normalized = value.trim().toUpperCase(Locale.ROOT);

         try {
            EntityType.valueOf(normalized);
            entityTypes.add(normalized);
         } catch (IllegalArgumentException exception) {
            this.plugin.getLogger().warning("[Boss] Invalid entity type '" + value + "' in " + path + "; ignoring.");
         }
      }

      return Set.copyOf(entityTypes);
   }

   private boolean migrateLegacyBossDurations(int newDefaultDuration) {
      File bossesDirectory = new File(this.plugin.getDataFolder(), "bosses");
      if (!bossesDirectory.isDirectory()) {
         return false;
      }

      File[] bossFiles = bossesDirectory.listFiles((directory, name) -> name.endsWith(".yml") && !name.equals("_settings.yml"));
      if (bossFiles == null) {
         this.plugin.getLogger().warning("[Boss] Could not list boss configs for duration migration; will retry.");
         return false;
      }

      boolean successful = true;

      for (File bossFile : bossFiles) {
         FileConfiguration bossConfig = YamlConfiguration.loadConfiguration(bossFile);
         if (bossConfig.contains("duration-seconds") && bossConfig.getInt("duration-seconds") == 1200) {
            bossConfig.set("duration-seconds", newDefaultDuration);
            bossConfig.setComments(
               "duration-seconds",
               List.of(
                  "[VI] Thời gian boss tồn tại mặc định sau khi xuất hiện (giây); mặc định 1800 = 30 phút.",
                  "[EN] Default boss lifetime after spawning (seconds); default 1800 = 30 minutes."
               )
            );

            try {
               bossConfig.save(bossFile);
            } catch (IOException exception) {
               successful = false;
               this.plugin.getLogger().log(Level.SEVERE, "[Boss] Could not migrate duration in " + bossFile.getName() + ".", exception);
            }
         }
      }

      return successful;
   }

   private ConfigManager.BossMercenaryGearDropConfig defaultBossMercenaryGearDropConfig() {
      ConfigManager.BossMercenaryGearDropConfig defaults = new ConfigManager.BossMercenaryGearDropConfig();
      defaults.levelTiers.put(1, new ConfigManager.BossMercenaryGearTier("leather", 1));
      defaults.levelTiers.put(2, new ConfigManager.BossMercenaryGearTier("copper", 1));
      defaults.levelTiers.put(3, new ConfigManager.BossMercenaryGearTier("iron", 1));
      defaults.levelTiers.put(4, new ConfigManager.BossMercenaryGearTier("iron", 2));
      defaults.levelTiers.put(5, new ConfigManager.BossMercenaryGearTier("gold", 2));
      defaults.levelTiers.put(6, new ConfigManager.BossMercenaryGearTier("diamond", 2));
      defaults.levelTiers.put(7, new ConfigManager.BossMercenaryGearTier("diamond", 3));
      defaults.levelTiers.put(8, new ConfigManager.BossMercenaryGearTier("netherite", 3));
      defaults.levelTiers.put(9, new ConfigManager.BossMercenaryGearTier("netherite", 4));
      defaults.levelTiers.put(10, new ConfigManager.BossMercenaryGearTier("netherite", 5));
      return defaults;
   }

   private ConfigManager.BossMercenaryGearDropConfig copyBossMercenaryGearDropConfig(ConfigManager.BossMercenaryGearDropConfig source) {
      ConfigManager.BossMercenaryGearDropConfig copy = new ConfigManager.BossMercenaryGearDropConfig();
      copy.enabled = source.enabled;
      copy.leatherOneStarChancePercent = source.leatherOneStarChancePercent;
      copy.netheriteFiveStarChancePercent = source.netheriteFiveStarChancePercent;
      source.levelTiers.forEach((level, tier) -> copy.levelTiers.put(level, new ConfigManager.BossMercenaryGearTier(tier.material, tier.stars)));
      return copy;
   }

   private ConfigManager.BossMercenaryGearDropConfig readBossMercenaryGearDropConfig(
      ConfigurationSection section, ConfigManager.BossMercenaryGearDropConfig inherited, String sourceName
   ) {
      ConfigManager.BossMercenaryGearDropConfig result = this.copyBossMercenaryGearDropConfig(inherited);
      if (section == null) {
         return result;
      }

      result.enabled = section.getBoolean("enabled", result.enabled);
      result.leatherOneStarChancePercent = this.readBoundedDouble(section, "chance.leather-1-star-percent", result.leatherOneStarChancePercent, 0.0, 100.0);
      result.netheriteFiveStarChancePercent = this.readBoundedDouble(
         section, "chance.netherite-5-star-percent", result.netheriteFiveStarChancePercent, 0.0, 100.0
      );
      if (result.leatherOneStarChancePercent < result.netheriteFiveStarChancePercent) {
         this.plugin
            .getLogger()
            .warning("[Boss] In " + sourceName + ", leather 1-star chance must be at least the netherite 5-star chance; using inherited values.");
         result.leatherOneStarChancePercent = inherited.leatherOneStarChancePercent;
         result.netheriteFiveStarChancePercent = inherited.netheriteFiveStarChancePercent;
      }

      ConfigurationSection tiers = section.getConfigurationSection("level-tiers");
      if (tiers != null) {
         Iterator<String> tierLevelKeys = tiers.getKeys(false).iterator();

         while (true) {
            String levelKey;
            int level;
            while (true) {
               if (!tierLevelKeys.hasNext()) {
                  return result;
               }

               levelKey = tierLevelKeys.next();

               try {
                  level = Integer.parseInt(levelKey);
                  break;
               } catch (NumberFormatException exception) {
                  this.plugin.getLogger().warning("[Boss] Ignoring invalid mercenary gear level '" + levelKey + "' in " + sourceName + ".");
               }
            }

            ConfigurationSection tier = tiers.getConfigurationSection(levelKey);
            if (level >= 1 && level <= 10 && tier != null) {
               String material = tier.getString("material", "").trim().toLowerCase(Locale.ROOT);
               int stars = tier.getInt("stars", 0);
               if (ConfigManager.BossMercenaryGearDropConfig.MATERIAL_ORDER.contains(material) && stars >= 1 && stars <= 5) {
                  result.levelTiers.put(level, new ConfigManager.BossMercenaryGearTier(material, stars));
               } else {
                  this.plugin.getLogger().warning("[Boss] Ignoring invalid mercenary gear material/stars at level " + levelKey + " in " + sourceName + ".");
               }
            } else {
               this.plugin.getLogger().warning("[Boss] Ignoring invalid mercenary gear tier at level " + levelKey + " in " + sourceName + ".");
            }
         }
      } else {
         return result;
      }
   }

   /**
    * Load all boss configurations from bosses/*.yml files
    * Dynamically discovers all .yml files in the bosses/ directory
    */
   private void loadBossConfigs() {
      this.bossConfigs.clear();
      File bossesDir = new File(this.plugin.getDataFolder(), "bosses");
      if (bossesDir.exists() && bossesDir.isDirectory()) {
         File[] bossFiles = bossesDir.listFiles((dir, name) -> name.endsWith(".yml") && !name.equals("_settings.yml"));
         if (bossFiles != null) {
            for (File file : bossFiles) {
               String fileName = file.getName();
               String bossId = fileName.substring(0, fileName.length() - 4);
               FileConfiguration section = YamlConfiguration.loadConfiguration(file);
               if (section != null) {
                  ConfigManager.BossConfig bc = new ConfigManager.BossConfig();
                  bc.enabled = section.getBoolean("enabled", true);
                  bc.entityType = section.getString("entity-type", "WITHER");
                  bc.displayName = EconomyManager.sanitizeVisibleName(
                     section.getString("display-name", "§c§lBoss"));
                  bc.armyTokenClass = section.getString("army-token-class", "guardian");
                  if (bc.armyTokenClass == null || !bc.armyTokenClass.matches("[a-z0-9_]+")) {
                     this.plugin.getLogger().warning("[Boss] Invalid army-token-class in " + fileName + "; using guardian.");
                     bc.armyTokenClass = "guardian";
                  }

                  bc.mercenaryGearDrops = this.readBossMercenaryGearDropConfig(
                     section.getConfigurationSection("mercenary-gear-drops"),
                     this.bossMercenaryGearDropDefaults == null ? this.defaultBossMercenaryGearDropConfig() : this.bossMercenaryGearDropDefaults,
                     fileName
                  );
                  bc.hp = section.getDouble("hp", 100.0);
                  bc.damageMultiplier = section.getDouble("damage-multiplier", 1.0);
                  bc.maxPlayerHitDamage = section.getDouble("max-player-hit-damage", 10.0);
                  if (!Double.isFinite(bc.maxPlayerHitDamage) || bc.maxPlayerHitDamage < 0.0 || bc.maxPlayerHitDamage > 100.0) {
                     this.plugin.getLogger().warning("[Boss] Invalid max-player-hit-damage in " + fileName + "; clamping to 0-100.");
                     bc.maxPlayerHitDamage = Double.isFinite(bc.maxPlayerHitDamage) ? Math.max(0.0, Math.min(100.0, bc.maxPlayerHitDamage)) : 10.0;
                  }

                  bc.chance = section.getInt("chance", 10);
                  bc.experience = section.getInt("experience", defaultBossExperience(bossId));
                  if (bc.experience < 1 || bc.experience > 1000000) {
                     this.plugin.getLogger().warning("[Boss] Invalid experience in " + fileName + "; using default 100.");
                     bc.experience = 100;
                  }

                  bc.durationSeconds = section.getInt("duration-seconds", this.bossDefaultDurationSeconds);
                  bc.warningSeconds = section.getInt("warning-seconds", 60);
                  bc.activityRadius = section.getInt("activity-radius", 100);
                  if (bc.activityRadius < 1 || bc.activityRadius > 10000) {
                     this.plugin.getLogger().warning("[Boss] Invalid activity-radius in " + fileName + "; using default 100 blocks.");
                     bc.activityRadius = 100;
                  }

                  ConfigurationSection hpByLevelSection = section.getConfigurationSection("hp-by-level");
                  if (hpByLevelSection != null) {
                     for (String levelKey : hpByLevelSection.getKeys(false)) {
                        try {
                           int level = Integer.parseInt(levelKey);
                           double hp = hpByLevelSection.getDouble(levelKey);
                           if (level > 0 && Double.isFinite(hp) && hp > 0.0) {
                              bc.hpByLevel.put(level, hp);
                           } else {
                              this.plugin.getLogger().warning("[Boss] Ignoring invalid hp-by-level." + levelKey + " in " + fileName);
                           }
                        } catch (NumberFormatException exception) {
                           this.plugin.getLogger().warning("[Boss] Ignoring non-numeric hp-by-level key '" + levelKey + "' in " + fileName);
                        }
                     }
                  }

                  ConfigurationSection skillsSection = section.getConfigurationSection("skills");
                  if (skillsSection != null) {
                     for (String skillId : skillsSection.getKeys(false)) {
                        ConfigurationSection skillSection = skillsSection.getConfigurationSection(skillId);
                        if (skillSection != null) {
                           ConfigManager.BossSkillConfig skill = new ConfigManager.BossSkillConfig();
                           skill.enabled = skillSection.getBoolean("enabled", false);
                           skill.damage = Math.max(0.0, skillSection.getDouble("damage", 4.0));
                           skill.multiply = Math.max(1, skillSection.getInt("multiply", 1));
                           skill.cooldownSeconds = Math.max(1, skillSection.getInt("cooldown-seconds", 8));
                           skill.radius = Math.max(0.0, skillSection.getDouble("radius", 4.0));
                           skill.velocity = Math.max(0.1, skillSection.getDouble("velocity", 1.0));
                           skill.spread = Math.max(0.0, skillSection.getDouble("spread", 1.0));
                           skill.durationSeconds = Math.max(1, Math.min(600, skillSection.getInt("duration-seconds", 5)));
                           skill.explosionPower = Math.max(1.0, Math.min(64.0, skillSection.getDouble("explosion-power", 3.0)));
                           skill.chargedExplosionPower = Math.max(skill.explosionPower, Math.min(64.0, skillSection.getDouble("charged-explosion-power", 6.0)));
                           skill.explosionCooldownSeconds = Math.max(1, Math.min(120, skillSection.getInt("explosion-cooldown-seconds", 4)));
                           skill.options.putAll(skillSection.getValues(false));
                           bc.skills.put(skillId.toLowerCase(Locale.ROOT), skill);
                        }
                     }
                  }

                  ConfigurationSection traitsSection = section.getConfigurationSection("traits");
                  if (traitsSection != null) {
                     for (String traitId : traitsSection.getKeys(false)) {
                        ConfigurationSection trait = traitsSection.getConfigurationSection(traitId);
                        if (trait != null) {
                           bc.traits.put(traitId, trait.getValues(false));
                        }
                     }
                  }

                  // Load immunities
                  ConfigurationSection immunitiesSection = section.getConfigurationSection("immunities");
                  if (immunitiesSection != null) {
                     // Boolean immunities
                     for (String key : immunitiesSection.getKeys(false)) {
                        if (!key.equals("potion-effects")) {
                           bc.immunities.put(key, immunitiesSection.getBoolean(key, false));
                        }
                     }

                     // Potion effect list immunity
                     bc.immunityPotionEffects = immunitiesSection.getStringList("potion-effects");
                  }

                  // Load drops
                  ConfigurationSection dropsSection = section.getConfigurationSection("drops");
                  if (dropsSection != null) {
                     for (String dropKey : dropsSection.getKeys(false)) {
                        ConfigurationSection dropSection = dropsSection.getConfigurationSection(dropKey);
                        if (dropSection != null) {
                           ConfigManager.DropConfig dc = new ConfigManager.DropConfig();
                           dc.minAmount = dropSection.getInt("min-amount", 1);
                           dc.maxAmount = dropSection.getInt("max-amount", 1);
                           dc.chance = dropSection.getDouble("chance", 0.5);
                           dc.name = EconomyManager.sanitizeVisibleName(dropSection.getString("name"));
                           dc.lore = dropSection.getStringList("lore");
                           ConfigurationSection bookEnchants = dropSection.getConfigurationSection("book-enchantments");
                           if (bookEnchants != null) {
                              for (String enchantment : bookEnchants.getKeys(false)) {
                                 int enchantLevel = bookEnchants.getInt(enchantment, 0);
                                 if (enchantLevel > 0 && enchantLevel <= 255) {
                                    dc.bookEnchantments.put(enchantment, enchantLevel);
                                 } else {
                                    this.plugin
                                       .getLogger()
                                       .warning("[Boss] Ignoring invalid book enchantment '" + enchantment + "' for " + dropKey + " in " + fileName + ".");
                                 }
                              }
                           }

                           bc.drops.put(dropKey, dc);
                        }
                     }
                  }

                  ConfigurationSection armorSetSection = section.getConfigurationSection("armor-set");
                  if (armorSetSection != null) {
                     ConfigManager.BossArmorSetConfig armorSet = new ConfigManager.BossArmorSetConfig();
                     armorSet.enabled = armorSetSection.getBoolean("enabled", true);
                     armorSet.name = EconomyManager.sanitizeVisibleName(
                        armorSetSection.getString("name", bc.displayName + " Di Vật"));
                     armorSet.storyId = armorSetSection.getString("story-id");
                     double dropChance = armorSetSection.getDouble("drop-chance", this.bossArmorPieceDropChance);
                     if (!Double.isFinite(dropChance) || dropChance < 0.0 || dropChance > 1.0) {
                        this.plugin.getLogger().warning("[Boss] Invalid armor-set.drop-chance in " + fileName + "; using global default.");
                        dropChance = this.bossArmorPieceDropChance;
                     }

                     armorSet.dropChance = dropChance;
                     ConfigurationSection pieceNames = armorSetSection.getConfigurationSection("piece-names");
                     if (pieceNames != null) {
                        for (String slot : List.of("helmet", "chestplate", "leggings", "boots")) {
                           String pieceName = pieceNames.getString(slot);
                           if (pieceName != null && !pieceName.isBlank()) {
                              armorSet.pieceNames.put(slot.toUpperCase(Locale.ROOT), pieceName);
                           }
                        }
                     }

                     ConfigurationSection enchantments = armorSetSection.getConfigurationSection("enchantments");
                     if (enchantments != null) {
                        for (String slot : List.of("helmet", "chestplate", "leggings", "boots")) {
                           List<String> configuredEnchants = enchantments.getStringList(slot);
                           if (!configuredEnchants.isEmpty()) {
                              armorSet.enchantmentsBySlot
                                 .put(slot.toUpperCase(Locale.ROOT), configuredEnchants.stream().map(value -> value.toLowerCase(Locale.ROOT)).toList());
                           }
                        }
                     }

                     ConfigurationSection bonuses = armorSetSection.getConfigurationSection("bonuses");
                     if (bonuses != null) {
                        for (String piecesKey : bonuses.getKeys(false)) {
                           int pieces;
                           try {
                              pieces = Integer.parseInt(piecesKey);
                           } catch (NumberFormatException exception) {
                              this.plugin.getLogger().warning("[Boss] Ignoring invalid armor bonus threshold '" + piecesKey + "' in " + fileName + ".");
                              continue;
                           }

                           if (pieces >= 2 && pieces <= 4) {
                              ConfigurationSection bonusSection = bonuses.getConfigurationSection(piecesKey);
                              if (bonusSection != null) {
                                 ConfigManager.BossArmorBonusConfig bonus = new ConfigManager.BossArmorBonusConfig();
                                 bonus.damageReductionPercent = this.readBoundedDouble(bonusSection, "damage-reduction-percent", 0.0, 0.0, 1.0);
                                 bonus.damageCauses = bonusSection.getStringList("damage-causes")
                                    .stream()
                                    .map(value -> value.toUpperCase(Locale.ROOT))
                                    .toList();
                                 bonus.knockbackReductionPercent = this.readBoundedDouble(bonusSection, "knockback-reduction-percent", 0.0, 0.0, 1.0);
                                 bonus.blockingDamageReductionPercent = this.readBoundedDouble(bonusSection, "blocking-damage-reduction-percent", 0.0, 0.0, 1.0);
                                 bonus.effectDurationReductionPercent = this.readBoundedDouble(bonusSection, "effect-duration-reduction-percent", 0.0, 0.0, 1.0);
                                 bonus.healOnUndeadKill = this.readBoundedDouble(bonusSection, "heal-on-undead-kill", 0.0, 0.0, 20.0);
                                 bonus.healCooldownSeconds = Math.max(1, Math.min(3600, bonusSection.getInt("heal-cooldown-seconds", 10)));
                                 armorSet.bonuses.put(pieces, bonus);
                              }
                           }
                        }
                     }

                     ConfigurationSection materialTiers = armorSetSection.getConfigurationSection("material-tiers");
                     if (materialTiers != null) {
                        for (String levelKey : materialTiers.getKeys(false)) {
                           try {
                              int level = Integer.parseInt(levelKey);
                              String material = materialTiers.getString(levelKey, "").trim().toUpperCase(Locale.ROOT);
                              if (level >= 1 && level <= 1000 && !material.isBlank()) {
                                 if (Material.matchMaterial(material + "_HELMET") == null) {
                                    this.plugin.getLogger().warning("[Boss] Invalid armor material tier '" + material + "' in " + fileName + ".");
                                 } else {
                                    armorSet.materialTiers.put(level, material);
                                 }
                              }
                           } catch (NumberFormatException exception) {
                              this.plugin.getLogger().warning("[Boss] Ignoring invalid armor material tier '" + levelKey + "' in " + fileName + ".");
                           }
                        }
                     }

                     if (!armorSet.enabled || armorSet.storyId != null && !armorSet.storyId.isBlank()) {
                        if (armorSet.enabled
                           && !this.bossItemStories.containsKey(armorSet.storyId)
                           && List.of("helmet", "chestplate", "leggings", "boots")
                              .stream()
                              .noneMatch(slot -> this.bossArmorPieceStories.containsKey(armorSet.storyId + ":" + slot))) {
                           this.plugin
                              .getLogger()
                              .warning("[Boss] Missing story-id '" + armorSet.storyId + "' in story.yml for armor set in " + fileName + ".");
                           armorSet.enabled = false;
                        }
                     } else {
                        this.plugin
                           .getLogger()
                           .warning("[Boss] armor-set.story-id is missing in " + fileName + "; armor rewards will be skipped until a story ID is configured.");
                        armorSet.enabled = false;
                     }

                     if (armorSet.enabled && armorSet.materialTiers.isEmpty()) {
                        this.plugin.getLogger().warning("[Boss] armor-set has no valid material tiers in " + fileName + "; armor rewards are disabled.");
                        armorSet.enabled = false;
                     }

                     bc.armorSet = armorSet;
                  }

                  ConfigurationSection levelDropsSection = section.getConfigurationSection("level-drops");
                  if (levelDropsSection != null) {
                     Iterator<String> levelDropKeys = levelDropsSection.getKeys(false).iterator();

                     label276:
                     while (true) {
                        String levelKey;
                        int level;
                        while (true) {
                           if (!levelDropKeys.hasNext()) {
                              break label276;
                           }

                           levelKey = levelDropKeys.next();

                           try {
                              level = Integer.parseInt(levelKey);
                              break;
                           } catch (NumberFormatException exception) {
                              this.plugin.getLogger().warning("[Boss] Ignoring invalid level-drops key '" + levelKey + "' in " + fileName + ".");
                           }
                        }

                        if (level >= 1 && level <= 1000) {
                           List<ConfigManager.ConfiguredItem> items = new ArrayList<>();

                           for (Map<?, ?> itemMap : section.getMapList("level-drops." + levelKey)) {
                              ConfigManager.ConfiguredItem item = new ConfigManager.ConfiguredItem();
                              if (itemMap.get("material") instanceof String material) {
                                 item.material = material;
                              }

                              if (itemMap.get("name") instanceof String name) {
                                 item.name = name;
                              }

                              if (itemMap.get("story-id") instanceof String storyId) {
                                 List<String> story = this.bossItemStories.get(storyId);
                                 if (story == null) {
                                    this.plugin
                                       .getLogger()
                                       .warning("[Boss] Missing story-id '" + storyId + "' in story.yml for " + fileName + " level " + level + ".");
                                 } else {
                                    for (String line : story) {
                                       item.lore.add(line.replace("{level}", Integer.toString(level)));
                                    }
                                 }
                              }

                              Object loreValue = itemMap.get("lore");
                              if (item.lore.isEmpty() && loreValue instanceof List) {
                                 for (Object line : (List)loreValue) {
                                    if (line instanceof String text) {
                                       item.lore.add(text);
                                    }
                                 }
                              }

                              if (itemMap.get("min-amount") instanceof Number number) {
                                 item.minAmount = number.intValue();
                              }

                              if (itemMap.get("max-amount") instanceof Number number) {
                                 item.maxAmount = number.intValue();
                              }

                              if (itemMap.get("chance") instanceof Number number) {
                                 item.chance = number.doubleValue();
                              }

                              this.readEnchantments(itemMap.get("enchantments"), item.enchantments);
                              this.readEnchantments(itemMap.get("book-enchantments"), item.bookEnchantments);
                              if (item.minAmount >= 1
                                 && item.maxAmount >= item.minAmount
                                 && item.maxAmount <= 64
                                 && Double.isFinite(item.chance)
                                 && !(item.chance < 0.0)
                                 && !(item.chance > 1.0)) {
                                 items.add(item);
                              } else {
                                 this.plugin.getLogger().warning("[Boss] Ignoring invalid level drop at " + fileName + " level " + level + ".");
                              }
                           }

                           bc.levelDrops.put(level, items);
                        } else {
                           this.plugin.getLogger().warning("[Boss] Ignoring out-of-range level-drops key '" + levelKey + "' in " + fileName + ".");
                        }
                     }
                  }

                  this.bossConfigs.put(bossId, bc);
               }
            }
         }
      }
   }

    private static int defaultBossExperience(String bossId) {
        return switch (bossId) {
            case "zombie_boss" -> 200;
            case "skeleton_boss", "spider_boss" -> 250;
            case "enderman_boss", "phantom_boss" -> 300;
            case "creeper_boss", "witch_boss" -> 350;
            case "ghast" -> 400;
            case "vindicator_boss" -> 450;
            case "ravager_boss" -> 600;
            case "giant_boss" -> 1000;
            case "wither" -> 1500;
            case "ender_dragon" -> 2000;
            default -> 100;
        };
    }

    private void loadBossItemStories() {
        bossItemStories.clear();
        bossArmorPieceStories.clear();
        bossItemStoryBosses.clear();
        File storyFile = new File(plugin.getDataFolder(), "story.yml");
        YamlConfiguration storyConfig = new YamlConfiguration();
        try {
            YamlDuplicateKeyValidator.validateNoDuplicateKeys(storyFile);
            storyConfig.load(storyFile);
        } catch (IOException | InvalidConfigurationException | YAMLException exception) {
            plugin.getLogger().log(Level.SEVERE,
                "[Story] Cannot load story.yml because it is invalid or contains duplicate YAML keys.", exception);
            return;
        }
        ConfigurationSection artifacts = storyConfig.getConfigurationSection("artifacts");
        if (artifacts == null) {
            plugin.getLogger().warning("[Boss] story.yml has no 'artifacts' section; item lore will use boss files.");
            return;
        }

        for (String storyId : artifacts.getKeys(false)) {
            ConfigurationSection artifact = artifacts.getConfigurationSection(storyId);
            if (artifact == null) {
                plugin.getLogger().warning("[Story] Expected a section at story.yml:artifacts." + storyId + ".");
                continue;
            }
            StoryArtifactValidator.validateArtifact(storyId, artifact.getValues(false))
                .forEach(issue -> plugin.getLogger().warning("[Story] " + issue));
            Object bossId = artifact.get("boss");
            if (bossId instanceof String configuredBossId && !configuredBossId.isBlank()) {
                bossItemStoryBosses.put(storyId, configuredBossId);
            }
            List<String> lore = artifact.getStringList("lore");
            if (!lore.isEmpty()) bossItemStories.put(storyId, List.copyOf(lore));
            ConfigurationSection pieces = artifact.getConfigurationSection("pieces");
            if (pieces != null) {
                for (String slot : List.of("helmet", "chestplate", "leggings", "boots")) {
                    ConfigurationSection piece = pieces.getConfigurationSection(slot);
                    if (piece == null) continue;
                    BossArmorPieceStory pieceStory = new BossArmorPieceStory();
                    pieceStory.itemName = piece.getString("item");
                    pieceStory.lore = List.copyOf(piece.getStringList("lore"));
                    if (pieceStory.itemName != null || !pieceStory.lore.isEmpty()) {
                        bossArmorPieceStories.put(storyId + ":" + slot, pieceStory);
                    }
                }
            }
        }
        plugin.getLogger().info("[Boss] Loaded " + bossItemStories.size() +
            " item stories and " + bossArmorPieceStories.size() + " armor-piece stories from story.yml.");
    }

    private void validateBossItemStoryBosses() {
        for (Map.Entry<String, String> entry : bossItemStoryBosses.entrySet()) {
            StoryArtifactValidator.validateBossReference(entry.getKey(), entry.getValue(), bossConfigs.keySet())
                .ifPresent(issue -> plugin.getLogger().warning("[Story] " + issue));
        }
    }

    public List<String> getBossItemStory(String storyId, int level) {
        List<String> lore = bossItemStories.get(storyId);
        if (lore == null) return List.of();
        return lore.stream().map(line -> line.replace("{level}", Integer.toString(level))).toList();
    }

    public Map<String, StoryChapterLoader.StoryChapter> getStoryChapters() {
        return storyChapters;
    }

    public BossArmorPieceStory getBossArmorPieceStory(String storyId, String slot, int level) {
        BossArmorPieceStory story = bossArmorPieceStories.get(storyId + ":" + slot.toLowerCase(Locale.ROOT));
        if (story == null) return null;
        BossArmorPieceStory resolved = new BossArmorPieceStory();
        resolved.itemName = story.itemName == null ? null
            : story.itemName.replace("{level}", Integer.toString(level));
        resolved.lore = story.lore.stream()
            .map(line -> line.replace("{level}", Integer.toString(level))).toList();
        return resolved;
    }

    private void readEnchantments(Object source, Map<String, Integer> destination) {
        if (!(source instanceof Map<?, ?> enchantments)) return;
        for (Map.Entry<?, ?> entry : enchantments.entrySet()) {
            if (!(entry.getKey() instanceof String name) || !(entry.getValue() instanceof Number level)) continue;
            if (name.isBlank() || level.intValue() < 1 || level.intValue() > 255) {
                plugin.getLogger().warning("[Boss] Ignoring invalid enchantment '" + name + "'.");
                continue;
            }
            destination.put(name, level.intValue());
        }
    }

   public static class BlockMiningRule {
      public boolean replaceEnabled;
      public Material replaceMaterial;
      public double replaceChance;
      public boolean toolBreakEnabled;
      public double toolBreakChance;
      public String toolBreakMessage;
      public boolean explodeEnabled;
      public float explosionPower;
      public boolean explosionBreakBlocks;
      public boolean explosionSetFire;
      public boolean explosionDamagePlayer;
      public double explosionChance;
      public String explosionMessage;
   }

   public static class BossArmorBonusConfig {
      public double damageReductionPercent;
      public List<String> damageCauses = new ArrayList<>();
      public double knockbackReductionPercent;
      public double blockingDamageReductionPercent;
      public double effectDurationReductionPercent;
      public double healOnUndeadKill;
      public int healCooldownSeconds = 10;
   }

   public static class BossArmorPieceStory {
      public String itemName;
      public List<String> lore = List.of();
   }

   public static class BossArmorSetConfig {
      public boolean enabled = true;
      public String name = "Di Vật";
      public String storyId;
      public double dropChance = 0.30;
      public Map<Integer, String> materialTiers = new HashMap<>();
      public Map<String, String> pieceNames = new HashMap<>();
      public Map<String, List<String>> enchantmentsBySlot = new HashMap<>();
      public Map<Integer, ConfigManager.BossArmorBonusConfig> bonuses = new HashMap<>();
   }

   // BossConfig class - holds all config for a single boss
   public static class BossConfig {
      public boolean enabled = true;
      public String entityType = "WITHER";
      public String displayName = "§c§lBoss";
      public String armyTokenClass = "guardian";
      public double hp = 100.0;
      public double damageMultiplier = 1.0;
      public double maxPlayerHitDamage = 10.0;
      public int chance = 10;
      public int experience = 100;
      public int durationSeconds = 1800;
      public int warningSeconds = 60;
      public int activityRadius = 100;
      public Map<String, Boolean> immunities = new HashMap<>();
      public List<String> immunityPotionEffects = new ArrayList<>();
      public Map<String, ConfigManager.DropConfig> drops = new HashMap<>();
      public Map<Integer, List<ConfigManager.ConfiguredItem>> levelDrops = new HashMap<>();
      public Map<Integer, Double> hpByLevel = new HashMap<>();
      public Map<String, ConfigManager.BossSkillConfig> skills = new HashMap<>();
      public Map<String, Object> traits = new HashMap<>();
      public ConfigManager.BossArmorSetConfig armorSet;
      public ConfigManager.BossMercenaryGearDropConfig mercenaryGearDrops;
   }

   public static class BossLevelThreshold {
      public long minDay;
      public int minLevel;
      public int maxLevel;
   }

   public static class BossMercenaryGearDropConfig {
      private static final List<String> MATERIAL_ORDER = List.of("leather", "copper", "iron", "gold", "diamond", "netherite");
      public boolean enabled = true;
      public double leatherOneStarChancePercent = 30.0;
      public double netheriteFiveStarChancePercent = 30.0;
      public Map<Integer, ConfigManager.BossMercenaryGearTier> levelTiers = new HashMap<>();

      public double chanceFor(String material, int stars) {
         int materialIndex = MATERIAL_ORDER.indexOf(material.toLowerCase(Locale.ROOT));
         if (materialIndex >= 0 && stars >= 1 && stars <= 5) {
            int qualityIndex = materialIndex * 5 + stars - 1;
            int highestQualityIndex = MATERIAL_ORDER.size() * 5 - 1;
            return (
                  this.leatherOneStarChancePercent
                     + (this.netheriteFiveStarChancePercent - this.leatherOneStarChancePercent) * qualityIndex / highestQualityIndex
               )
               / 100.0;
         } else {
            throw new IllegalArgumentException("Unsupported mercenary gear quality.");
         }
      }
   }

   public static class BossMercenaryGearTier {
      public String material;
      public int stars;

      public BossMercenaryGearTier(String material, int stars) {
         this.material = material;
         this.stars = stars;
      }
   }

   public static class BossSkillConfig {
      public boolean enabled;
      public double damage;
      public int multiply;
      public int cooldownSeconds;
      public double radius;
      public double velocity;
      public double spread;
      public int durationSeconds;
      public double explosionPower;
      public double chargedExplosionPower;
      public int explosionCooldownSeconds;
      public Map<String, Object> options = new HashMap<>();
   }

   public static class BossSpawnSuccessThreshold {
      public long minDay;
      public double chancePercent;
   }

   public static class ConfiguredItem {
      public String material = "DIAMOND";
      public String name;
      public List<String> lore = new ArrayList<>();
      public int minAmount = 1;
      public int maxAmount = 1;
      public double chance;
      public Map<String, Integer> enchantments = new HashMap<>();
      public Map<String, Integer> bookEnchantments = new HashMap<>();
   }

   // DisasterConfig class - holds all config for a single disaster
   public static class DisasterConfig {
      public boolean enabled = true;
      public int chance = 5;
      public String name = "Unknown Disaster";
      public List<String> worlds = new ArrayList<>();
      public String chatDescription = "";
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

   public static class DisasterSuccessThreshold {
      public final int minDay;
      public final int probabilityPercent;

      public DisasterSuccessThreshold(int minDay, int probabilityPercent) {
         this.minDay = minDay;
         this.probabilityPercent = probabilityPercent;
      }
   }

   // DropConfig class - holds drop configuration
   public static class DropConfig {
      public int minAmount = 1;
      public int maxAmount = 1;
      public double chance = 0.5;
      public String name;
      public List<String> lore = new ArrayList<>();
      public Map<String, Integer> bookEnchantments = new HashMap<>();
   }

   public static class WorldTimeConfig {
      public Integer dayDurationMinutes;
      public Integer nightDurationMinutes;
   }
}
