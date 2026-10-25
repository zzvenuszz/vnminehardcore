package com.vnmine.hardcore.managers;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.error.YAMLException;

public final class StoryChapterLoader {
    private static final int SUPPORTED_SCHEMA_VERSION = 1;
    private static final Pattern CHAPTER_ID_PATTERN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    private StoryChapterLoader() {
    }

    public static Map<String, StoryChapter> load(File file, Logger logger) {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            YamlDuplicateKeyValidator.validateNoDuplicateKeys(file);
            configuration.load(file);
        } catch (IOException | InvalidConfigurationException | YAMLException exception) {
            logger.log(Level.SEVERE, "[Story] Could not load " + file.getName() + ".", exception);
            return Map.of();
        }

        int schemaVersion = configuration.getInt("schema-version", -1);
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            logger.warning("[Story] Unsupported or missing schema-version in " + file.getName() +
                "; expected " + SUPPORTED_SCHEMA_VERSION + ".");
            return Map.of();
        }

        ConfigurationSection chapters = configuration.getConfigurationSection("chapters");
        if (chapters == null) {
            logger.warning("[Story] Missing 'chapters' section in " + file.getName() + ".");
            return Map.of();
        }

        TreeMap<Integer, StoryChapter> chaptersByOrder = new TreeMap<>();
        for (String chapterId : chapters.getKeys(false)) {
            String path = file.getName() + ":chapters." + chapterId;
            ConfigurationSection chapter = chapters.getConfigurationSection(chapterId);
            if (chapter == null) {
                logger.warning("[Story] Expected a section at " + path + ".");
                continue;
            }

            boolean valid = true;
            if (!CHAPTER_ID_PATTERN.matcher(chapterId).matches()) {
                logger.warning("[Story] Invalid chapter ID at " + path +
                    "; use lowercase letters, numbers, and single hyphens between segments.");
                valid = false;
            }

            Object rawOrder = chapter.get("order");
            int order = rawOrder instanceof Number number ? number.intValue() : -1;
            if (!(rawOrder instanceof Number number) || number.doubleValue() != order || order < 1) {
                logger.warning("[Story] Invalid or missing positive integer at " + path + ".order.");
                valid = false;
            }

            String title = readRequiredText(chapter, "title", path, logger);
            String summary = readRequiredText(chapter, "summary", path, logger);
            List<String> pages = summary == null ? null : readPages(chapter, summary, path, logger);
            if (title == null || summary == null || pages == null) valid = false;
            if (!valid) continue;

            StoryChapter previous = chaptersByOrder.putIfAbsent(
                order, new StoryChapter(chapterId, order, title, summary, pages));
            if (previous != null) {
                logger.warning("[Story] Duplicate chapter order " + order + " at " + path +
                    "; already used by chapter '" + previous.id() + "'.");
            }
        }

        Map<String, StoryChapter> result = new LinkedHashMap<>();
        chaptersByOrder.values().forEach(chapter -> result.put(chapter.id(), chapter));
        logger.info("[Story] Loaded " + result.size() + " chapters from " + file.getName() + ".");
        return Collections.unmodifiableMap(result);
    }

    private static String readRequiredText(
            ConfigurationSection section, String key, String path, Logger logger) {
        Object value = section.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            logger.warning("[Story] Missing or invalid required text at " + path + "." + key + ".");
            return null;
        }
        return text;
    }

    private static List<String> readPages(ConfigurationSection section, String summary,
                                          String path, Logger logger) {
        Object value = section.get("pages");
        if (value == null) {
            return List.of(summary.length() <= 256 ? summary : summary.substring(0, 256));
        }
        if (!(value instanceof List<?> rawPages) || rawPages.isEmpty() || rawPages.size() > 12) {
            logger.warning("[Story] pages at " + path + " must contain 1 to 12 book pages.");
            return null;
        }
        List<String> pages = new ArrayList<>(rawPages.size());
        for (int index = 0; index < rawPages.size(); index++) {
            Object rawPage = rawPages.get(index);
            if (!(rawPage instanceof String text) || text.isBlank() || text.length() > 256) {
                logger.warning("[Story] Invalid page " + (index + 1) + " at " + path
                    + "; each page must contain 1 to 256 characters.");
                return null;
            }
            pages.add(text.trim());
        }
        return List.copyOf(pages);
    }

    public record StoryChapter(String id, int order, String title, String summary, List<String> pages) {
        public StoryChapter {
            pages = List.copyOf(pages);
        }
    }
}
