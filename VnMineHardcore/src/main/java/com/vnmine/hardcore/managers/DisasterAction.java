package com.vnmine.hardcore.managers;

import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

/**
 * Action Engine for disasters.
 * Parses actions from YAML and stores them as data objects.
 */
public class DisasterAction {

    // ===== ENUMS =====
    public enum ActionType {
        DAMAGE,
        POTION_EFFECT,
        SPAWN_MOBS,
        LIGHTNING_STRIKE,
        EXPLOSION,
        SET_FIRE,
        PLACE_BLOCK,
        FALLING_BLOCK,
        VELOCITY,
        SET_TIME,
        SET_WEATHER,
        CLEAR_WEATHER,
        BROADCAST,
        ACTION_BAR,
        TELEPORT_RANDOM,
        PLAY_SOUND
    }

    // ===== FIELDS =====
    public ActionType type;
    public ActionCondition condition;
    public List<ActionTarget> targets;
    public Map<String, Object> params = new HashMap<>();

    // ===== CONSTRUCTOR =====
    public DisasterAction(ActionType type) {
        this.type = type;
        this.condition = new ActionCondition();
        this.targets = new ArrayList<>();
    }

    // ===== SUBCLASSES =====

    /**
     * Điều kiện áp dụng action
     */
    public static class ActionCondition {
        public boolean requireOutdoor = false;
        public boolean ignoreSafeZone = false;

        public static ActionCondition fromSection(ConfigurationSection section) {
            ActionCondition c = new ActionCondition();
            if (section == null) return c;
            c.requireOutdoor = section.getBoolean("require-outdoor", false);
            c.ignoreSafeZone = section.getBoolean("ignore-safe-zone", false);
            return c;
        }
    }

    /**
     * Target với weighted random
     */
    public static class ActionTarget {
        public String entityType; // player, all_players, ground, trees, friendly_mob, hostile_mob
        public int weight = 100;
        public List<String> worlds = new ArrayList<>();

        public static ActionTarget fromSection(String key, ConfigurationSection section) {
            ActionTarget t = new ActionTarget();
            t.entityType = key;
            t.weight = section.getInt("weight", 100);
            t.worlds = section.getStringList("worlds");
            if (t.worlds.isEmpty()) {
                t.worlds.add("all");
            }
            return t;
        }
    }

    /**
     * Dữ liệu hiệu ứng potion
     */
    public static class PotionEffectData {
        public String type = "SPEED";
        public int durationTicks = 100;
        public int amplifier = 0;

        public static PotionEffectData fromSection(ConfigurationSection section) {
            PotionEffectData p = new PotionEffectData();
            if (section == null) return p;
            p.type = section.getString("type", "SPEED");
            p.durationTicks = section.getInt("duration-ticks", 100);
            p.amplifier = section.getInt("amplifier", 0);
            return p;
        }
    }

    /**
     * Dữ liệu spawn mob
     */
    public static class MobSpawnData {
        public String entityType = "ZOMBIE";
        public int weight = 100;
        public List<PotionEffectData> effects = new ArrayList<>();

        public static MobSpawnData fromSection(String key, ConfigurationSection section) {
            MobSpawnData m = new MobSpawnData();
            m.entityType = key;
            m.weight = section.getInt("weight", 100);
            // Load effects
            ConfigurationSection effectsSection = section.getConfigurationSection("effects");
            if (effectsSection != null) {
                for (String effectKey : effectsSection.getKeys(false)) {
                    ConfigurationSection es = effectsSection.getConfigurationSection(effectKey);
                    if (es != null) {
                        m.effects.add(PotionEffectData.fromSection(es));
                    }
                }
            }
            return m;
        }
    }

    // ===== FACTORY: Parse actions from YAML =====

    /**
     * Parse danh sách actions từ List (dạng list of maps trong YAML)
     */
    @SuppressWarnings("unchecked")
    public static List<DisasterAction> parseActionList(List<Map<?, ?>> actionList) {
        List<DisasterAction> actions = new ArrayList<>();
        if (actionList == null) return actions;

        for (Map<?, ?> actionMap : actionList) {
            String typeStr = (String) actionMap.get("type");
            if (typeStr == null) continue;

            ActionType type;
            try {
                type = ActionType.valueOf(typeStr.toUpperCase().replace("-", "_"));
            } catch (IllegalArgumentException e) {
                continue;
            }

            DisasterAction action = new DisasterAction(type);

            // Parse condition
            if (actionMap.containsKey("condition")) {
                Map<?, ?> condMap = (Map<?, ?>) actionMap.get("condition");
                action.condition.requireOutdoor = toBool(condMap.get("require-outdoor"), false);
                action.condition.ignoreSafeZone = toBool(condMap.get("ignore-safe-zone"), false);
            }

            // Parse targets
            if (actionMap.containsKey("targets")) {
                Map<?, ?> targetsMap = (Map<?, ?>) actionMap.get("targets");
                for (Map.Entry<?, ?> entry : targetsMap.entrySet()) {
                    String targetKey = entry.getKey() != null ? entry.getKey().toString() : "";
                    Map<?, ?> tMap = (Map<?, ?>) entry.getValue();
                    ActionTarget target = new ActionTarget();
                    target.entityType = targetKey;
                    target.weight = toInt(tMap.get("weight"), 100);
                    if (tMap.containsKey("worlds")) {
                        List<?> worldsRaw = (List<?>) tMap.get("worlds");
                        target.worlds = new ArrayList<>();
                        for (Object w : worldsRaw) {
                            target.worlds.add(w.toString());
                        }
                    }
                    if (target.worlds == null || target.worlds.isEmpty()) {
                        target.worlds.add("all");
                    }
                    action.targets.add(target);
                }
            }

            // Parse all other params
            for (Map.Entry<?, ?> entry : actionMap.entrySet()) {
                String key = entry.getKey() != null ? entry.getKey().toString() : "";
                if (key.equals("type") || key.equals("condition") || key.equals("targets")) continue;
                action.params.put(key, entry.getValue());
            }

            actions.add(action);
        }

        return actions;
    }

    // ===== UTILITY =====
    private static boolean toBool(Object value, boolean defaultValue) {
        if (value instanceof Boolean) return (Boolean) value;
        return defaultValue;
    }

    private static int toInt(Object value, int defaultValue) {
        if (value instanceof Number) return ((Number) value).intValue();
        try { return Integer.parseInt(value.toString()); } catch (Exception e) { return defaultValue; }
    }

    private static double toDouble(Object value, double defaultValue) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        try { return Double.parseDouble(value.toString()); } catch (Exception e) { return defaultValue; }
    }
}