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

public final class HumanRealmDefinitionLoader {
    private static final int SUPPORTED_SCHEMA_VERSION = 1;
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    private HumanRealmDefinitionLoader() {
    }

    public static Optional<HumanRealmDefinition> load(File file, Logger logger) {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            YamlDuplicateKeyValidator.validateNoDuplicateKeys(file);
            configuration.load(file);
        } catch (IOException | InvalidConfigurationException | YAMLException exception) {
            logger.log(Level.SEVERE, "[World] Could not load " + file.getName() + ".", exception);
            return Optional.empty();
        }

        boolean valid = true;
        Object rawSchemaVersion = configuration.get("schema-version");
        int schemaVersion = rawSchemaVersion instanceof Number number ? number.intValue() : -1;
        if (!(rawSchemaVersion instanceof Number number)
                || number.doubleValue() != schemaVersion
                || schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            logger.warning("[World] Unsupported or missing schema-version in " + file.getName() +
                "; expected " + SUPPORTED_SCHEMA_VERSION + ".");
            valid = false;
        }

        String id = readRequiredText(configuration, "id", file, logger);
        if (id != null && !ID_PATTERN.matcher(id).matches()) {
            logger.warning("[World] Invalid content ID at " + file.getName() +
                ".id; use lowercase letters, numbers, and single hyphens between segments.");
            valid = false;
        }
        String displayName = readRequiredText(configuration, "display-name", file, logger);
        String worldName = readRequiredText(configuration, "world-name", file, logger);
        String fallbackWorldName = readRequiredText(configuration, "fallback-world-name", file, logger);
        Object rawTutorialNpcId = configuration.get("tutorial-npc-id");
        String tutorialNpcId = rawTutorialNpcId == null
            ? ("human-realm".equals(id) ? "tutorial-guide" : "tutorial-" + id)
            : rawTutorialNpcId instanceof String value ? value.trim() : null;
        if (tutorialNpcId == null || !ID_PATTERN.matcher(tutorialNpcId).matches()) {
            logger.warning("[World] Invalid tutorial NPC ID at " + file.getName()
                + ".tutorial-npc-id; use lowercase letters, numbers, and single hyphens between segments.");
            valid = false;
        }
        Object rawStartingSelectable = configuration.get("starting-selectable");
        boolean startingSelectable = rawStartingSelectable == null
            ? "default".equals(configuration.getString("access.type", "default"))
            : rawStartingSelectable instanceof Boolean value && value;
        if (rawStartingSelectable != null && !(rawStartingSelectable instanceof Boolean)) {
            logger.warning("[World] Invalid boolean at " + file.getName() + ".starting-selectable.");
            valid = false;
        }

        ConfigurationSection accessSection = configuration.getConfigurationSection("access");
        String accessType = accessSection == null ? "default"
            : readRequiredText(accessSection, "type", file, logger);
        String requiredQuestId = null;
        if (accessSection != null && "completed-quest".equals(accessType)) {
            requiredQuestId = readRequiredText(accessSection, "quest-id", file, logger);
            if (requiredQuestId != null && !ID_PATTERN.matcher(requiredQuestId).matches()) {
                logger.warning("[World] Invalid quest ID at " + file.getName() +
                    ".access.quest-id; use lowercase letters, numbers, and single hyphens between segments.");
                valid = false;
            }
        } else if (!"default".equals(accessType)) {
            logger.warning("[World] Unsupported access type at " + file.getName() +
                ".access.type; expected 'default' or 'completed-quest'.");
            valid = false;
        }
        if (accessType == null) valid = false;
        AccessCondition access = accessType == null ? null : new AccessCondition(accessType, requiredQuestId);

        ConfigurationSection spawnSection = configuration.getConfigurationSection("spawn");
        if (spawnSection == null) {
            logger.warning("[World] Missing section at " + file.getName() + ".spawn.");
            return Optional.empty();
        }
        Boolean useWorldSpawn = readRequiredBoolean(spawnSection, "use-world-spawn", file, "spawn", logger);
        Double x = null;
        Double y = null;
        Double z = null;
        if (Boolean.FALSE.equals(useWorldSpawn)) {
            x = readRequiredNumber(spawnSection, "x", file, "spawn", logger);
            y = readRequiredNumber(spawnSection, "y", file, "spawn", logger);
            z = readRequiredNumber(spawnSection, "z", file, "spawn", logger);
        }
        Double yawValue = readOptionalNumber(spawnSection, "yaw", 0.0, file, "spawn", logger);
        Double pitchValue = readOptionalNumber(spawnSection, "pitch", 0.0, file, "spawn", logger);
        if (yawValue == null || yawValue < -360.0 || yawValue > 360.0) {
            logger.warning("[World] spawn.yaw in " + file.getName() + " must be between -360 and 360.");
            valid = false;
        }
        if (pitchValue == null || pitchValue < -90.0 || pitchValue > 90.0) {
            logger.warning("[World] spawn.pitch in " + file.getName() + " must be between -90 and 90.");
            valid = false;
        }

        ConfigurationSection safeZoneSection = configuration.getConfigurationSection("safe-zone");
        if (safeZoneSection == null) {
            logger.warning("[World] Missing section at " + file.getName() + ".safe-zone.");
            return Optional.empty();
        }
        Boolean safeZoneEnabled = readRequiredBoolean(safeZoneSection, "enabled", file, "safe-zone", logger);
        Integer safeZoneRadius = readRequiredInteger(safeZoneSection, "radius", file, "safe-zone", logger);
        if (safeZoneRadius != null && (safeZoneRadius < 1 || safeZoneRadius > 1024)) {
            logger.warning("[World] safe-zone.radius in " + file.getName() + " must be between 1 and 1024.");
            valid = false;
        }

        if (id == null || displayName == null || worldName == null || fallbackWorldName == null
                || access == null
                || useWorldSpawn == null || yawValue == null || pitchValue == null
                || safeZoneEnabled == null || safeZoneRadius == null
                || (!useWorldSpawn && (x == null || y == null || z == null))) {
            valid = false;
        }
        if (!valid) return Optional.empty();

        return Optional.of(new HumanRealmDefinition(
            id, displayName, worldName, fallbackWorldName, tutorialNpcId, startingSelectable,
            access,
            new Spawn(useWorldSpawn, x, y, z, yawValue.floatValue(), pitchValue.floatValue()),
            new SafeZone(safeZoneEnabled, safeZoneRadius)
        ));
    }

    private static String readRequiredText(
            ConfigurationSection section, String key, File file, Logger logger) {
        Object value = section.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            logger.warning("[World] Missing or invalid text at " + file.getName() + "." + key + ".");
            return null;
        }
        return text.trim();
    }

    private static Boolean readRequiredBoolean(
            ConfigurationSection section, String key, File file, String parent, Logger logger) {
        Object value = section.get(key);
        if (!(value instanceof Boolean booleanValue)) {
            logger.warning("[World] Missing or invalid boolean at " + file.getName() + "." + parent + "." + key + ".");
            return null;
        }
        return booleanValue;
    }

    private static Double readRequiredNumber(
            ConfigurationSection section, String key, File file, String parent, Logger logger) {
        Double value = readOptionalNumber(section, key, null, file, parent, logger);
        if (value == null) {
            logger.warning("[World] Missing required coordinate at " + file.getName() + "." + parent + "." + key + ".");
        }
        return value;
    }

    private static Double readOptionalNumber(
            ConfigurationSection section, String key, Double defaultValue,
            File file, String parent, Logger logger) {
        Object rawValue = section.get(key);
        if (rawValue == null) return defaultValue;
        if (!(rawValue instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            logger.warning("[World] Invalid number at " + file.getName() + "." + parent + "." + key + ".");
            return null;
        }
        return number.doubleValue();
    }

    private static Integer readRequiredInteger(
            ConfigurationSection section, String key, File file, String parent, Logger logger) {
        Object value = section.get(key);
        if (!(value instanceof Number number) || number.doubleValue() != number.intValue()) {
            logger.warning("[World] Missing or invalid integer at " + file.getName() + "." + parent + "." + key + ".");
            return null;
        }
        return number.intValue();
    }

    public record HumanRealmDefinition(
        String id,
        String displayName,
        String worldName,
        String fallbackWorldName,
        String tutorialNpcId,
        boolean startingSelectable,
        AccessCondition access,
        Spawn spawn,
        SafeZone safeZone
    ) {
    }

    public record AccessCondition(String type, String questId) {
    }

    public record Spawn(
        boolean useWorldSpawn,
        Double x,
        Double y,
        Double z,
        float yaw,
        float pitch
    ) {
    }

    public record SafeZone(boolean enabled, int radius) {
    }
}
