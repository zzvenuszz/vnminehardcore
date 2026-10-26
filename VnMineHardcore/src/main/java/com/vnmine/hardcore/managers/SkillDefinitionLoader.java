package com.vnmine.hardcore.managers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.error.YAMLException;

public final class SkillDefinitionLoader {
    private static final int SUPPORTED_SCHEMA_VERSION = 1;
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    private SkillDefinitionLoader() {
    }

    public static Optional<SkillRegistry> load(File skillsFile, File booksFile, Logger logger) {
        return load(skillsFile, booksFile, null, logger);
    }

    public static Optional<SkillRegistry> load(File skillsFile, File booksFile,
                                               Set<String> validClassIds, Logger logger) {
        YamlConfiguration skillsConfig = loadYaml(skillsFile, logger);
        YamlConfiguration booksConfig = loadYaml(booksFile, logger);
        if (skillsConfig == null || booksConfig == null) return Optional.empty();

        boolean valid = validateVersion(skillsConfig, skillsFile, logger)
            & validateVersion(booksConfig, booksFile, logger);
        ConfigurationSection skillsSection = skillsConfig.getConfigurationSection("skills");
        ConfigurationSection booksSection = booksConfig.getConfigurationSection("books");
        if (skillsSection == null) {
            logger.warning("[Skills] Missing section at " + skillsFile.getName() + ".skills.");
            valid = false;
        }
        if (booksSection == null) {
            logger.warning("[Skills] Missing section at " + booksFile.getName() + ".books.");
            valid = false;
        }
        if (!valid) return Optional.empty();

        Map<String, SkillDefinition> skills = new LinkedHashMap<>();
        for (String id : skillsSection.getKeys(false)) {
            ConfigurationSection section = skillsSection.getConfigurationSection(id);
            SkillDefinition definition = section == null ? null :
                readSkill(id, section, skillsFile, logger);
            if (definition == null || skills.putIfAbsent(id, definition) != null) {
                valid = false;
            }
        }

        Map<String, SkillBookDefinition> books = new LinkedHashMap<>();
        for (String id : booksSection.getKeys(false)) {
            ConfigurationSection section = booksSection.getConfigurationSection(id);
            SkillBookDefinition definition = section == null ? null :
                readBook(id, section, booksFile, logger);
            if (definition == null || books.putIfAbsent(id, definition) != null) {
                valid = false;
            }
        }

        Map<String, String> bookBySkill = new LinkedHashMap<>();
        for (SkillBookDefinition book : books.values()) {
            if (!skills.containsKey(book.skillId())) {
                logger.warning("[Skills] Book '" + book.id() + "' references unknown skill '" +
                    book.skillId() + "'.");
                valid = false;
                continue;
            }
            String previous = bookBySkill.putIfAbsent(book.skillId(), book.id());
            if (previous != null) {
                logger.warning("[Skills] Multiple books reference skill '" + book.skillId() +
                    "'; each skill must have exactly one learning book.");
                valid = false;
            }
        }
        for (SkillDefinition skill : skills.values()) {
            if (validClassIds != null && !validClassIds.contains(skill.classId())) {
                logger.warning("[Skills] Skill '" + skill.id() + "' references unknown class '" +
                    skill.classId() + "'.");
                valid = false;
            }
            if (!books.containsKey(skill.bookId()) ||
                    !skill.id().equals(books.get(skill.bookId()).skillId())) {
                logger.warning("[Skills] Skill '" + skill.id() + "' has a missing or mismatched book reference.");
                valid = false;
            }
        }
        if (skills.isEmpty() || books.isEmpty()) {
            logger.warning("[Skills] Skill and skill-book definitions must not be empty.");
            valid = false;
        }
        if (!valid) return Optional.empty();
        return Optional.of(new SkillRegistry(skills, books, bookBySkill));
    }

    private static YamlConfiguration loadYaml(File file, Logger logger) {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            YamlDuplicateKeyValidator.validateNoDuplicateKeys(file);
            configuration.load(file);
            return configuration;
        } catch (IOException | InvalidConfigurationException | YAMLException exception) {
            logger.log(Level.SEVERE, "[Skills] Could not load " + file.getName() + ".", exception);
            return null;
        }
    }

    private static boolean validateVersion(YamlConfiguration configuration, File file, Logger logger) {
        Object rawVersion = configuration.get("schema-version");
        if (rawVersion instanceof Number number
                && number.doubleValue() == number.intValue()
                && number.intValue() == SUPPORTED_SCHEMA_VERSION) {
            return true;
        }
        logger.warning("[Skills] Unsupported or missing schema-version in " + file.getName() +
            "; expected " + SUPPORTED_SCHEMA_VERSION + ".");
        return false;
    }

    private static SkillDefinition readSkill(String id, ConfigurationSection section, File file, Logger logger) {
        boolean valid = true;
        if (!isValidId(id)) {
            logger.warning("[Skills] Invalid skill ID '" + id + "' in " + file.getName() + ".");
            valid = false;
        }
        String classId = readText(section, "class-id", "skills", id, file, logger);
        String displayName = readText(section, "display-name", "skills", id, file, logger);
        String description = readText(section, "description", "skills", id, file, logger);
        String bookId = readText(section, "book-id", "skills", id, file, logger);
        String iconName = readText(section, "icon", "skills", id, file, logger);
        Integer requiredLevel = readInteger(section, "required-level", "skills", id, file, logger);
        Integer maxRank = readInteger(section, "max-rank", "skills", id, file, logger);
        Object rawCastType = section.get("cast-type");
        String castType = rawCastType == null ? defaultCastType(id)
            : rawCastType instanceof String text ? text.trim().toLowerCase(java.util.Locale.ROOT) : "";
        Object rawCooldown = section.get("cooldown-seconds");
        int cooldownSeconds = rawCooldown == null ? defaultCooldownSeconds(id)
            : rawCooldown instanceof Number number && number.doubleValue() == number.intValue()
                ? number.intValue() : -1;
        Object rawManaCost = section.get("mana-cost");
        int manaCost = rawManaCost == null ? defaultManaCost(id)
            : rawManaCost instanceof Number number && number.doubleValue() == number.intValue()
                ? number.intValue() : -1;
        List<String> lore = readTextList(section, "lore", "skills", id, file, logger);
        Material icon = iconName == null ? null : Material.matchMaterial(iconName);
        if (classId != null && !isValidId(classId)) {
            logger.warning("[Skills] Invalid class-id for skill '" + id + "' in " + file.getName() + ".");
            valid = false;
        }
        if (requiredLevel != null && (requiredLevel < 1 || requiredLevel > 30)) {
            logger.warning("[Skills] required-level for skill '" + id + "' must be between 1 and 30.");
            valid = false;
        }
        if (maxRank != null && (maxRank < 1 || maxRank > 5)) {
            logger.warning("[Skills] max-rank for skill '" + id + "' must be between 1 and 5.");
            valid = false;
        }
        if (!castType.equals("active") && !castType.equals("passive")) {
            logger.warning("[Skills] cast-type for skill '" + id + "' must be active or passive.");
            valid = false;
        }
        if (cooldownSeconds < 0 || cooldownSeconds > 120
                || (castType.equals("passive") && cooldownSeconds != 0)
                || (castType.equals("active") && cooldownSeconds == 0)) {
            logger.warning("[Skills] Invalid cooldown-seconds for skill '" + id + "'.");
            valid = false;
        }
        if (manaCost < 0 || manaCost > 1000
                || (castType.equals("passive") && manaCost != 0)
                || (castType.equals("active") && manaCost == 0)) {
            logger.warning("[Skills] Invalid mana-cost for skill '" + id + "'.");
            valid = false;
        }
        if (bookId != null && !isValidId(bookId)) {
            logger.warning("[Skills] Invalid book-id for skill '" + id + "' in " + file.getName() + ".");
            valid = false;
        }
        if (!isItemMaterial(icon)) {
            logger.warning("[Skills] Invalid item icon for skill '" + id + "' in " + file.getName() + ".");
            valid = false;
        }
        if (classId == null || displayName == null || description == null || bookId == null
                || requiredLevel == null || maxRank == null || lore == null) {
            valid = false;
        }
        if (!valid) return null;
        return new SkillDefinition(id, classId, displayName, description, lore, requiredLevel,
            maxRank, icon, bookId, castType.equals("passive"), cooldownSeconds, manaCost);
    }

    private static String defaultCastType(String skillId) {
        return switch (skillId) {
            case "battle-focus", "fortify", "mana-flow" -> "passive";
            default -> "active";
        };
    }

    private static int defaultCooldownSeconds(String skillId) {
        return switch (skillId) {
            case "blade-flare" -> 6;
            case "guard-break" -> 8;
            case "shield-wall" -> 10;
            case "taunt" -> 12;
            case "fire-bolt" -> 5;
            case "arcane-field" -> 11;
            case "battle-focus", "fortify", "mana-flow" -> 0;
            default -> 6;
        };
    }

    private static int defaultManaCost(String skillId) {
        return switch (skillId) {
            case "blade-flare" -> 12;
            case "guard-break" -> 14;
            case "shield-wall" -> 15;
            case "taunt" -> 10;
            case "fire-bolt" -> 12;
            case "arcane-field" -> 18;
            case "battle-focus", "fortify", "mana-flow" -> 0;
            default -> 10;
        };
    }

    private static SkillBookDefinition readBook(String id, ConfigurationSection section, File file, Logger logger) {
        boolean valid = true;
        if (!isValidId(id)) {
            logger.warning("[Skills] Invalid book ID '" + id + "' in " + file.getName() + ".");
            valid = false;
        }
        String skillId = readText(section, "skill-id", "books", id, file, logger);
        String displayName = readText(section, "display-name", "books", id, file, logger);
        String materialName = readText(section, "material", "books", id, file, logger);
        String rarity = readText(section, "rarity", "books", id, file, logger);
        List<String> lore = readTextList(section, "lore", "books", id, file, logger);
        Object rawSellValue = section.get("sell-value");
        long sellValue = rawSellValue == null ? 0L
            : rawSellValue instanceof Number number && number.doubleValue() == number.longValue()
                ? number.longValue() : -1L;
        Material material = materialName == null ? null : Material.matchMaterial(materialName);
        if (sellValue < 0 || sellValue > 1_000_000L) {
            logger.warning("[Skills] sell-value for book '" + id + "' must be an integer between 0 and 1000000.");
            valid = false;
        }
        if (skillId != null && !isValidId(skillId)) {
            logger.warning("[Skills] Invalid skill-id for book '" + id + "' in " + file.getName() + ".");
            valid = false;
        }
        if (rarity != null && !List.of("common", "uncommon", "rare", "epic", "legendary")
                .contains(rarity.toLowerCase(java.util.Locale.ROOT))) {
            logger.warning("[Skills] Invalid rarity for book '" + id + "' in " + file.getName() + ".");
            valid = false;
        }
        if (!isItemMaterial(material)) {
            logger.warning("[Skills] Invalid item material for book '" + id + "' in " + file.getName() + ".");
            valid = false;
        }
        if (skillId == null || displayName == null || rarity == null || lore == null) {
            valid = false;
        }
        if (!valid) return null;
        return new SkillBookDefinition(id, skillId, displayName, rarity, material, lore, sellValue);
    }

    private static String readText(ConfigurationSection section, String key, String root,
                                   String id, File file, Logger logger) {
        Object value = section.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            logger.warning("[Skills] Missing or invalid text at " + file.getName() +
                "." + root + "." + id + "." + key + ".");
            return null;
        }
        return text.trim();
    }

    private static Integer readInteger(ConfigurationSection section, String key, String root,
                                       String id, File file, Logger logger) {
        Object value = section.get(key);
        if (!(value instanceof Number number) || number.doubleValue() != number.intValue()) {
            logger.warning("[Skills] Missing or invalid integer at " + file.getName() +
                "." + root + "." + id + "." + key + ".");
            return null;
        }
        return number.intValue();
    }

    private static List<String> readTextList(ConfigurationSection section, String key, String root,
                                              String id, File file, Logger logger) {
        List<?> raw = section.getList(key);
        if (raw == null || raw.isEmpty() || raw.size() > 20
                || raw.stream().anyMatch(value -> !(value instanceof String text) || text.isBlank())) {
            logger.warning("[Skills] Missing or invalid lore at " + file.getName() +
                "." + root + "." + id + "." + key + "; expected 1 to 20 non-empty lines.");
            return null;
        }
        List<String> values = new ArrayList<>(raw.size());
        for (Object value : raw) values.add(((String) value).trim());
        return List.copyOf(values);
    }

    private static boolean isValidId(String id) {
        return ID_PATTERN.matcher(id).matches();
    }

    private static boolean isItemMaterial(Material material) {
        if (material == null || material.name().equals("AIR")) return false;
        return Bukkit.getServer() == null || material.isItem();
    }

    public record SkillRegistry(Map<String, SkillDefinition> skills,
                                Map<String, SkillBookDefinition> books,
                                Map<String, String> bookBySkill) {
        public SkillRegistry {
            skills = Map.copyOf(skills);
            books = Map.copyOf(books);
            bookBySkill = Map.copyOf(bookBySkill);
        }
    }

    public record SkillDefinition(String id, String classId, String displayName, String description,
                                  List<String> lore, int requiredLevel, int maxRank,
                                  Material icon, String bookId, boolean passive, int cooldownSeconds,
                                  int manaCost) {
        public SkillDefinition {
            lore = List.copyOf(lore);
        }
    }

    public record SkillBookDefinition(String id, String skillId, String displayName, String rarity,
                                       Material material, List<String> lore, long sellValue) {
        public SkillBookDefinition {
            lore = List.copyOf(lore);
        }
    }
}
