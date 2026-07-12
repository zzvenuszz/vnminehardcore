package com.vnmine.hardcore.managers;

import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

/**
 * Action Engine for disasters.
 * Parses actions from YAML and stores them as data objects.
 * Hỗ trợ multi-target và chain-actions.
 */
public class DisasterAction {

    // ===== ENUMS =====
    public enum ActionType {
        // === CORE ACTIONS ===
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
        PLAY_SOUND,

        // === NEW BLOCK ACTIONS ===
        BLOCK_VELOCITY,      // Nhấc block lên và thả rơi
        BLOCK_EXPLOSION,     // Phá hủy block dạng nổ
        BLOCK_REPLACE,       // Thay thế block này bằng block khác
        BLOCK_IGNITE,        // Đốt cháy block
        BLOCK_LIQUID,        // Đặt chất lỏng (lava/water)
        BLOCK_FERTILIZE,     // Thúc đẩy cây trồng

        // === NEW ENTITY ACTIONS ===
        ENTITY_PULL,         // Kéo entity về phía trung tâm
        ENTITY_PUSH,         // Đẩy entity ra xa
        ENTITY_FREEZE,       // Đóng băng entity
        ENTITY_DISMOUNT,     // Hất entity khỏi thú cưỡi
        ENTITY_MOUNT,        // Bắt entity lên thú cưỡi

        // === NEW ITEM ACTIONS ===
        ITEM_DROP,           // Rơi item tại vị trí target

        // === NEW WORLD ACTIONS ===
        WORLD_TIME,          // Đặt thời gian
        WORLD_WEATHER,       // Đặt thời tiết

        // === FIREBALL ACTIONS ===
        FIREBALL_RAIN        // Mưa fireball từ trên trời
    }

    // ===== TARGET TYPES =====
    public enum TargetType {
        PLAYER,              // Người chơi (weighted)
        ALL_PLAYERS,         // Tất cả người chơi
        FRIENDLY_MOBS,       // Sinh vật thân thiện
        HOSTILE_MOBS,        // Sinh vật thù địch
        ALL_MOBS,            // Tất cả sinh vật
        GROUND,              // Block mặt đất
        TREES,               // Block gỗ, lá cây
        BLOCKS,              // Block rắn bất kỳ
        SURFACE_BLOCKS,      // Block trên bề mặt
        METAL_BLOCKS,        // Block kim loại (iron, gold, copper, netherite, lightning_rod)
        RANDOM_BLOCKS,       // Block ngẫu nhiên từ danh sách (format: random_blocks:MATERIAL1,MATERIAL2)
        RANDOM               // Random từ array target (format: random:target1,target2)
    }

    // ===== FIELDS =====
    public ActionType type;
    public ActionCondition condition;
    public List<ActionTarget> targets;
    public Map<String, Object> params = new HashMap<>();
    public List<DisasterAction> chainActions = new ArrayList<>(); // Chain actions thực thi sau action chính

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
        public String entityType; // player, all_players, ground, trees, friendly_mobs, hostile_mobs, all_mobs, blocks, surface_blocks, random_blocks:..., random:...
        public int weight = 100;
        public List<String> worlds = new ArrayList<>();
        public int radius = 10; // Bán kính tìm kiếm target
        public List<String> blockMaterials = new ArrayList<>(); // Danh sách material cho random_blocks

        public static ActionTarget fromSection(String key, ConfigurationSection section) {
            ActionTarget t = new ActionTarget();
            t.entityType = key;
            t.weight = section.getInt("weight", 100);
            t.radius = section.getInt("radius", 10);
            t.worlds = section.getStringList("worlds");
            if (t.worlds.isEmpty()) {
                t.worlds.add("all");
            }
            // Parse block materials nếu có
            if (section.contains("block-materials")) {
                t.blockMaterials = section.getStringList("block-materials");
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
                    target.radius = toInt(tMap.get("radius"), 10);
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
                    // Parse block-materials
                    if (tMap.containsKey("block-materials")) {
                        List<?> materialsRaw = (List<?>) tMap.get("block-materials");
                        target.blockMaterials = new ArrayList<>();
                        for (Object m : materialsRaw) {
                            target.blockMaterials.add(m.toString());
                        }
                    }
                    action.targets.add(target);
                }
            }

            // Parse chain-actions (đệ quy)
            if (actionMap.containsKey("chain-actions")) {
                List<Map<?, ?>> chainList = (List<Map<?, ?>>) actionMap.get("chain-actions");
                action.chainActions = parseActionList(chainList);
            }

            // Parse all other params
            for (Map.Entry<?, ?> entry : actionMap.entrySet()) {
                String key = entry.getKey() != null ? entry.getKey().toString() : "";
                if (key.equals("type") || key.equals("condition") || key.equals("targets") || key.equals("chain-actions")) continue;
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