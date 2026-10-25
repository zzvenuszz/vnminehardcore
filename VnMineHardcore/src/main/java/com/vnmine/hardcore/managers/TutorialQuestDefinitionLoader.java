package com.vnmine.hardcore.managers;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.error.YAMLException;

public final class TutorialQuestDefinitionLoader {
    private static final int SUPPORTED_SCHEMA_VERSION = 1;
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final String SUPPORTED_OBJECTIVE_TYPE = "visit-realm-spawn";
    private static final String SUPPORTED_REALM_ID = "human-realm";

    private TutorialQuestDefinitionLoader() {
    }

    public static Optional<TutorialQuestDefinition> load(File file, Logger logger) {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            YamlDuplicateKeyValidator.validateNoDuplicateKeys(file);
            configuration.load(file);
        } catch (IOException | InvalidConfigurationException | YAMLException exception) {
            logger.log(Level.SEVERE, "[Quest] Could not load " + file.getName() + ".", exception);
            return Optional.empty();
        }

        boolean valid = true;
        Object rawSchemaVersion = configuration.get("schema-version");
        int schemaVersion = rawSchemaVersion instanceof Number number ? number.intValue() : -1;
        if (!(rawSchemaVersion instanceof Number number)
                || number.doubleValue() != schemaVersion
                || schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            logger.warning("[Quest] Unsupported or missing schema-version in " + file.getName() +
                "; expected " + SUPPORTED_SCHEMA_VERSION + ".");
            valid = false;
        }

        ConfigurationSection quest = configuration.getConfigurationSection("quest");
        if (quest == null) {
            logger.warning("[Quest] Missing section at " + file.getName() + ".quest.");
            return Optional.empty();
        }

        Boolean enabled = readRequiredBoolean(quest, "enabled", file, "quest", logger);
        String id = readRequiredText(quest, "id", file, "quest", logger);
        if (id != null && !ID_PATTERN.matcher(id).matches()) {
            logger.warning("[Quest] Invalid quest ID at " + file.getName() +
                ".quest.id; use lowercase letters, numbers, and single hyphens between segments.");
            valid = false;
        }
        String title = readRequiredText(quest, "title", file, "quest", logger);
        String description = readRequiredText(quest, "description", file, "quest", logger);

        ConfigurationSection rewardSection = quest.getConfigurationSection("reward");
        if (rewardSection == null) {
            logger.warning("[Quest] Missing section at " + file.getName() + ".quest.reward.");
            return Optional.empty();
        }
        String rewardType = readRequiredText(rewardSection, "type", file, "quest.reward", logger);
        if (rewardType != null && !"character-exp".equals(rewardType) && !"vncoin".equals(rewardType)) {
            logger.warning("[Quest] Unsupported reward type '" + rewardType + "' at " +
                file.getName() + ".quest.reward.type; supported types are 'character-exp' and 'vncoin'.");
            valid = false;
        }
        Integer rewardAmount = readRequiredInteger(rewardSection, "amount", file, "quest.reward", logger);
        if (rewardAmount != null) {
            if ("character-exp".equals(rewardType) && (rewardAmount < 0 || rewardAmount > 100_000)) {
                logger.warning("[Quest] character-exp reward amount in " + file.getName()
                    + " must be between 0 and 100000.");
                valid = false;
            } else if ("vncoin".equals(rewardType) && (rewardAmount < 1 || rewardAmount > 1_000_000)) {
                logger.warning("[Quest] VNCOIN reward amount in " + file.getName()
                    + " must be between 1 and 1000000.");
                valid = false;
            }
        }

        ConfigurationSection objective = quest.getConfigurationSection("objective");
        if (objective == null) {
            logger.warning("[Quest] Missing section at " + file.getName() + ".quest.objective.");
            return Optional.empty();
        }
        String objectiveId = readRequiredText(objective, "id", file, "quest.objective", logger);
        if (objectiveId != null && !ID_PATTERN.matcher(objectiveId).matches()) {
            logger.warning("[Quest] Invalid objective ID at " + file.getName() +
                ".quest.objective.id; use lowercase letters, numbers, and single hyphens between segments.");
            valid = false;
        }
        String objectiveType = readRequiredText(objective, "type", file, "quest.objective", logger);
        if (objectiveType != null && !SUPPORTED_OBJECTIVE_TYPE.equals(objectiveType)) {
            logger.warning("[Quest] Unsupported objective type '" + objectiveType + "' at " +
                file.getName() + ".quest.objective.type; only '" + SUPPORTED_OBJECTIVE_TYPE + "' is supported.");
            valid = false;
        }
        String realmId = readRequiredText(objective, "realm-id", file, "quest.objective", logger);
        if (realmId != null && !SUPPORTED_REALM_ID.equals(realmId)) {
            logger.warning("[Quest] Unsupported realm ID at " + file.getName() +
                ".quest.objective.realm-id; expected '" + SUPPORTED_REALM_ID + "'.");
            valid = false;
        }
        Integer radiusBlocks = readRequiredInteger(objective, "radius-blocks", file, "quest.objective", logger);
        if (radiusBlocks != null && (radiusBlocks < 1 || radiusBlocks > 64)) {
            logger.warning("[Quest] radius-blocks in " + file.getName() + " must be between 1 and 64.");
            valid = false;
        }
        Boolean waypointEnabled = readOptionalBoolean(objective, "waypoint-enabled", true,
            file, "quest.objective", logger);
        Integer waypointUpdateIntervalTicks = readOptionalInteger(objective, "waypoint-update-interval-ticks", 20,
            file, "quest.objective", logger);
        if (waypointUpdateIntervalTicks != null
                && (waypointUpdateIntervalTicks < 5 || waypointUpdateIntervalTicks > 200)) {
            logger.warning("[Quest] waypoint-update-interval-ticks in " + file.getName()
                + " must be between 5 and 200.");
            valid = false;
        }
        Integer waypointParticleRangeBlocks = readOptionalInteger(objective, "waypoint-particle-range-blocks", 48,
            file, "quest.objective", logger);
        if (waypointParticleRangeBlocks != null
                && (waypointParticleRangeBlocks < 8 || waypointParticleRangeBlocks > 64)) {
            logger.warning("[Quest] waypoint-particle-range-blocks in " + file.getName()
                + " must be between 8 and 64.");
            valid = false;
        }
        String startMessage = readRequiredText(objective, "start-message", file, "quest.objective", logger);
        String completeMessage = readRequiredText(objective, "complete-message", file, "quest.objective", logger);

        if (enabled == null || id == null || title == null || description == null
                || rewardType == null || rewardAmount == null || objectiveId == null
                || objectiveType == null || realmId == null || radiusBlocks == null
                || waypointEnabled == null || waypointUpdateIntervalTicks == null
                || waypointParticleRangeBlocks == null
                || startMessage == null || completeMessage == null) {
            valid = false;
        }
        if (!valid) return Optional.empty();

        return Optional.of(new TutorialQuestDefinition(
            enabled, id, title, description, new Reward(rewardType, rewardAmount),
            new Objective(objectiveId, objectiveType, realmId, radiusBlocks, waypointEnabled,
                waypointUpdateIntervalTicks, waypointParticleRangeBlocks, startMessage, completeMessage)
        ));
    }

    private static String readRequiredText(
            ConfigurationSection section, String key, File file, String parent, Logger logger) {
        Object value = section.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            logger.warning("[Quest] Missing or invalid text at " + file.getName() + "." + parent + "." + key + ".");
            return null;
        }
        return text.trim();
    }

    private static Integer readRequiredInteger(
            ConfigurationSection section, String key, File file, String parent, Logger logger) {
        Object value = section.get(key);
        if (!(value instanceof Number number) || number.doubleValue() != number.intValue()) {
            logger.warning("[Quest] Missing or invalid integer at " + file.getName() + "." + parent + "." + key + ".");
            return null;
        }
        return number.intValue();
    }

    private static Integer readOptionalInteger(
            ConfigurationSection section, String key, int defaultValue, File file, String parent, Logger logger) {
        return section.contains(key) ? readRequiredInteger(section, key, file, parent, logger) : defaultValue;
    }

    private static Boolean readRequiredBoolean(
            ConfigurationSection section, String key, File file, String parent, Logger logger) {
        Object value = section.get(key);
        if (!(value instanceof Boolean booleanValue)) {
            logger.warning("[Quest] Missing or invalid boolean at " + file.getName() + "." + parent + "." + key + ".");
            return null;
        }
        return booleanValue;
    }

    private static Boolean readOptionalBoolean(
            ConfigurationSection section, String key, boolean defaultValue, File file, String parent, Logger logger) {
        return section.contains(key) ? readRequiredBoolean(section, key, file, parent, logger) : defaultValue;
    }

    public record TutorialQuestDefinition(boolean enabled, String id, String title, String description,
                                          Reward reward, Objective objective) {
    }

    public record Reward(String type, int amount) {
    }

    public record Objective(
        String id,
        String type,
        String realmId,
        int radiusBlocks,
        boolean waypointEnabled,
        int waypointUpdateIntervalTicks,
        int waypointParticleRangeBlocks,
        String startMessage,
        String completeMessage
    ) {
    }
}
