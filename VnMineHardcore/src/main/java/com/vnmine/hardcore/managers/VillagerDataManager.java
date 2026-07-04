package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Quản lý villager-data.yml
 * Mỗi biome instance (biome@regionX_regionZ_world) được random data riêng khi lần đầu player bước vào.
 */
public class VillagerDataManager {

    private final VnMineHardcore plugin;
    private final ConfigManager config;
    private final Logger logger;
    private final Random random = ThreadLocalRandom.current();

    private File dataFile;
    private FileConfiguration data;

    // Cache: biomeKey -> Map<professionName, List<TradeEntry>>
    private final Map<String, Map<String, List<TradeEntry>>> biomeTradeCache = new HashMap<>();

    // Cache: player tracked biomes
    private final Map<UUID, Set<String>> playerTrackedBiomes = new HashMap<>();

    public VillagerDataManager(VnMineHardcore plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.logger = plugin.getLogger();
        loadData();
        logger.info("[VillagerData] Initialized");
    }

    /**
     * Tải dữ liệu từ villager-data.yml
     */
    public void loadData() {
        dataFile = new File(plugin.getDataFolder(), "villager-data.yml");
        if (!dataFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                logger.warning("[VillagerData] Không thể tạo villager-data.yml!");
                return;
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        biomeTradeCache.clear();
        loadCacheFromData();
    }

    /**
     * Lưu dữ liệu xuống villager-data.yml
     */
    public void saveData() {
        if (data == null) return;
        try {
            data.save(dataFile);
        } catch (IOException e) {
            logger.warning("[VillagerData] Không thể lưu villager-data.yml!");
        }
    }

    /**
     * Load cache từ dữ liệu YAML
     */
    private void loadCacheFromData() {
        ConfigurationSection biomesSection = data.getConfigurationSection("biomes");
        if (biomesSection == null) return;

        for (String biomeKey : biomesSection.getKeys(false)) {
            ConfigurationSection professionsSection = biomesSection.getConfigurationSection(biomeKey + ".professions");
            if (professionsSection == null) continue;

            Map<String, List<TradeEntry>> professionMap = new HashMap<>();
            for (String professionName : professionsSection.getKeys(false)) {
                List<TradeEntry> trades = new ArrayList<>();
                ConfigurationSection tradesSection = professionsSection.getConfigurationSection(professionName + ".trades");
                if (tradesSection == null) continue;

                for (String tradeKey : tradesSection.getKeys(false)) {
                    ConfigurationSection tradeSection = tradesSection.getConfigurationSection(tradeKey);
                    if (tradeSection == null) continue;
                    TradeEntry entry = new TradeEntry();
                    entry.itemMaterial = tradeSection.getString("item", "EMERALD");
                    entry.baseLevel = tradeSection.getInt("base-level", 1);
                    entry.maxLevel = tradeSection.getInt("max-level", 5);
                    entry.enchantment = tradeSection.getString("enchantment", "");
                    entry.upgradePath = new HashMap<>();
                    ConfigurationSection upgradeSection = tradeSection.getConfigurationSection("upgrade");
                    if (upgradeSection != null) {
                        for (String levelKey : upgradeSection.getKeys(false)) {
                            entry.upgradePath.put(levelKey, upgradeSection.getString(levelKey, "EMERALD"));
                        }
                    }
                    trades.add(entry);
                }
                professionMap.put(professionName, trades);
            }
            biomeTradeCache.put(biomeKey, professionMap);
        }
    }

    /**
     * Lấy key cho biome instance: biome@regionX_regionZ_world
     */
    public String getBiomeKey(Location loc) {
        String biomeName = loc.getBlock().getBiome().getKey().getKey().toLowerCase();
        int regionSize = config.villagerRegionSize;
        int regionX = (int) Math.floor((double) loc.getBlockX() / regionSize);
        int regionZ = (int) Math.floor((double) loc.getBlockZ() / regionSize);
        String worldName = loc.getWorld().getName();
        return biomeName + "@" + regionX + "_" + regionZ + "_" + worldName;
    }

    /**
     * Kiểm tra biển đã có dữ liệu chưa
     */
    public boolean hasBiomeData(String biomeKey) {
        return biomeTradeCache.containsKey(biomeKey);
    }

    /**
     * Random sinh dữ liệu trade cho biome key này
     * Gồm tất cả các nghề có thể có, mỗi nghề 1-2 item trade random
     */
    public void generateBiomeData(String biomeKey, Location loc) {
        if (hasBiomeData(biomeKey)) return;

        String biomeName = loc.getBlock().getBiome().getKey().getKey().toLowerCase();
        Map<String, List<TradeEntry>> professionMap = new LinkedHashMap<>();

        // Xác định danh sách nghề phù hợp với biome
        List<Villager.Profession> suitableProfessions = getProfessionsForBiome(biomeName);
        if (suitableProfessions.isEmpty()) {
            // Fallback: tất cả nghề
            suitableProfessions.addAll(Arrays.asList(Villager.Profession.values()));
            suitableProfessions.remove(Villager.Profession.NONE);
            suitableProfessions.remove(Villager.Profession.NITWIT);
        }

        for (Villager.Profession profession : suitableProfessions) {
            String profName = profession.name();
            List<TradeEntry> trades = new ArrayList<>();

            // Random 1-2 loại item cho nghề này
            int tradeCount = 1 + random.nextInt(2); // 1-2 trades
            List<String> possibleItems = getItemsForProfession(profName, biomeName);

            if (possibleItems.isEmpty()) continue;

            // Chọn ngẫu nhiên items không trùng
            List<String> selectedItems = new ArrayList<>();
            List<String> shuffled = new ArrayList<>(possibleItems);
            Collections.shuffle(shuffled, random);
            for (int i = 0; i < Math.min(tradeCount, shuffled.size()); i++) {
                selectedItems.add(shuffled.get(i));
            }

            for (String item : selectedItems) {
                TradeEntry entry = new TradeEntry();
                entry.itemMaterial = item;
                entry.baseLevel = 1;
                entry.maxLevel = 3 + random.nextInt(3); // 3-5 levels
                entry.enchantment = getEnchantmentForItem(item);
                entry.upgradePath = generateUpgradePath(item, entry.maxLevel);
                trades.add(entry);
            }

            if (!trades.isEmpty()) {
                professionMap.put(profName, trades);
            }
        }

        // Lưu vào cache
        biomeTradeCache.put(biomeKey, professionMap);

        // Lưu vào file
        String basePath = "biomes." + biomeKey;
        data.set(basePath + ".biome", biomeName);
        for (Map.Entry<String, List<TradeEntry>> profEntry : professionMap.entrySet()) {
            String profPath = basePath + ".professions." + profEntry.getKey();
            List<TradeEntry> tradeList = profEntry.getValue();
            for (int i = 0; i < tradeList.size(); i++) {
                TradeEntry te = tradeList.get(i);
                String tradePath = profPath + ".trades.trade-" + i;
                data.set(tradePath + ".item", te.itemMaterial);
                data.set(tradePath + ".base-level", te.baseLevel);
                data.set(tradePath + ".max-level", te.maxLevel);
                if (!te.enchantment.isEmpty()) {
                    data.set(tradePath + ".enchantment", te.enchantment);
                }
                for (Map.Entry<String, String> upgrade : te.upgradePath.entrySet()) {
                    data.set(tradePath + ".upgrade." + upgrade.getKey(), upgrade.getValue());
                }
            }
        }
        saveData();
        logger.info("[VillagerData] Generated data for " + biomeKey);
    }

    /**
     * Lấy danh sách trade cho biome+profession
     */
    public List<TradeEntry> getTradesForProfession(String biomeKey, String professionName) {
        Map<String, List<TradeEntry>> professionMap = biomeTradeCache.get(biomeKey);
        if (professionMap == null) return Collections.emptyList();
        return professionMap.getOrDefault(professionName, Collections.emptyList());
    }

    /**
     * Tạo MerchantRecipe từ TradeEntry theo level hiện tại của villager
     */
    public MerchantRecipe createRecipe(TradeEntry entry, int villagerLevel) {
        int currentLevel = Math.min(villagerLevel, entry.maxLevel);
        String levelKey = "level-" + currentLevel;
        String itemName = entry.upgradePath.getOrDefault(levelKey, entry.itemMaterial);

        Material material;
        try {
            material = Material.valueOf(itemName.toUpperCase());
        } catch (IllegalArgumentException e) {
            material = Material.EMERALD;
        }

        ItemStack result = new ItemStack(material, 1);

        // Nếu có enchantment, thêm vào
        if (!entry.enchantment.isEmpty()) {
            Enchantment ench = Enchantment.getByName(entry.enchantment.toUpperCase());
            if (ench != null) {
                int enchLevel = Math.min(currentLevel, ench.getMaxLevel());
                result.addUnsafeEnchantment(ench, enchLevel);
            }
        }

        // Cost: emerald + maybe extra item
        int baseCost = 1 + currentLevel;
        MerchantRecipe recipe = new MerchantRecipe(result, 0, entry.maxLevel, true, 0, 0);
        recipe.addIngredient(new ItemStack(Material.EMERALD, baseCost));

        // Thêm ingredient thứ 2 random nếu level cao
        if (currentLevel >= 3) {
            Material extraMat = getExtraCostMaterial(material);
            recipe.addIngredient(new ItemStack(extraMat, 1 + random.nextInt(2)));
        }

        return recipe;
    }

    // ============ BIOME-PROFESSION MAPPING ============

    /**
     * Lấy danh sách nghề phù hợp với biome
     */
    private List<Villager.Profession> getProfessionsForBiome(String biomeName) {
        List<Villager.Profession> result = new ArrayList<>();

        if (biomeName.contains("desert") || biomeName.contains("badlands") || biomeName.contains("savanna")) {
            // Biome nóng: dân làng liên quan đến sa mạc
            result.add(Villager.Profession.CARTOGRAPHER); // Bản đồ
            result.add(Villager.Profession.CLERIC); // Thuốc
            result.add(Villager.Profession.FARMER); // Nông dân
            result.add(Villager.Profession.WEAPONSMITH); // Rèn vũ khí
        } else if (biomeName.contains("snowy") || biomeName.contains("frozen") || biomeName.contains("ice")) {
            // Biome lạnh
            result.add(Villager.Profession.FLETCHER); // Cung tên
            result.add(Villager.Profession.BUTCHER); // Đồ ăn
            result.add(Villager.Profession.ARMORER); // Rèn giáp
            result.add(Villager.Profession.TOOLSMITH); // Rèn công cụ
        } else if (biomeName.contains("taiga") || biomeName.contains("forest")) {
            // Rừng
            result.add(Villager.Profession.FLETCHER);
            result.add(Villager.Profession.FARMER);
            result.add(Villager.Profession.SHEPHERD); // Chăn cừu
            result.add(Villager.Profession.LEATHERWORKER); // Da
        } else if (biomeName.contains("ocean") || biomeName.contains("river") || biomeName.contains("beach")) {
            // Nước
            result.add(Villager.Profession.FISHERMAN); // Câu cá
            result.add(Villager.Profession.CLERIC);
            result.add(Villager.Profession.CARTOGRAPHER);
        } else if (biomeName.contains("jungle")) {
            // Rừng rậm
            result.add(Villager.Profession.CLERIC);
            result.add(Villager.Profession.FARMER);
            result.add(Villager.Profession.MASON); // Thợ xây
        } else if (biomeName.contains("plains") || biomeName.contains("meadow") || biomeName.contains("sunflower")) {
            // Đồng bằng
            result.add(Villager.Profession.FARMER);
            result.add(Villager.Profession.SHEPHERD);
            result.add(Villager.Profession.BUTCHER);
            result.add(Villager.Profession.ARMORER);
        } else if (biomeName.contains("swamp") || biomeName.contains("mangrove")) {
            // Đầm lầy
            result.add(Villager.Profession.CLERIC);
            result.add(Villager.Profession.FISHERMAN);
            result.add(Villager.Profession.MASON);
        } else {
            // Default: tất cả nghề
            result.addAll(Arrays.asList(
                Villager.Profession.ARMORER, Villager.Profession.BUTCHER,
                Villager.Profession.CARTOGRAPHER, Villager.Profession.CLERIC,
                Villager.Profession.FARMER, Villager.Profession.FISHERMAN,
                Villager.Profession.FLETCHER, Villager.Profession.LEATHERWORKER,
                Villager.Profession.LIBRARIAN, Villager.Profession.MASON,
                Villager.Profession.SHEPHERD, Villager.Profession.TOOLSMITH,
                Villager.Profession.WEAPONSMITH
            ));
        }

        return result;
    }

    /**
     * Lấy danh sách item có thể trade cho nghề + biome
     */
    private List<String> getItemsForProfession(String profession, String biomeName) {
        List<String> items = new ArrayList<>();
        boolean isHot = biomeName.contains("desert") || biomeName.contains("badlands") ||
                       biomeName.contains("savanna") || biomeName.contains("nether");
        boolean isCold = biomeName.contains("snowy") || biomeName.contains("frozen") ||
                        biomeName.contains("ice");
        boolean isWater = biomeName.contains("ocean") || biomeName.contains("river") ||
                         biomeName.contains("beach") || biomeName.contains("swamp");

        switch (profession) {
            case "ARMORER":
                items.add("IRON_HELMET"); items.add("IRON_CHESTPLATE");
                items.add("IRON_LEGGINGS"); items.add("IRON_BOOTS");
                items.add("CHAINMAIL_HELMET"); items.add("CHAINMAIL_CHESTPLATE");
                items.add("CHAINMAIL_LEGGINGS"); items.add("CHAINMAIL_BOOTS");
                items.add("DIAMOND_HELMET"); items.add("DIAMOND_CHESTPLATE");
                if (!isCold) items.add("SHIELD");
                if (isHot) items.add("LEATHER_HELMET"); // Mũ da mát hơn
                break;
            case "WEAPONSMITH":
                items.add("IRON_SWORD"); items.add("IRON_AXE");
                items.add("DIAMOND_SWORD"); items.add("DIAMOND_AXE");
                items.add("STONE_SWORD"); items.add("STONE_AXE");
                if (isCold) items.add("BOW"); // Lạnh dùng cung săn
                if (isHot) items.add("GOLDEN_SWORD"); // Vàng trong sa mạc
                break;
            case "TOOLSMITH":
                items.add("IRON_PICKAXE"); items.add("IRON_SHOVEL");
                items.add("IRON_HOE"); items.add("DIAMOND_PICKAXE");
                items.add("STONE_PICKAXE"); items.add("STONE_SHOVEL");
                items.add("WOODEN_PICKAXE"); items.add("WOODEN_SHOVEL");
                if (isWater) items.add("IRON_AXE"); // Rìa để đốn gỗ dưới nước
                break;
            case "FARMER":
                items.add("WHEAT"); items.add("CARROT"); items.add("POTATO");
                items.add("BEETROOT"); items.add("MELON"); items.add("PUMPKIN");
                items.add("APPLE"); items.add("GOLDEN_CARROT");
                items.add("ENCHANTED_GOLDEN_APPLE");
                items.add("BREAD"); items.add("CAKE");
                if (isHot) items.add("MELON"); // Dưa hấu mát
                if (isCold) items.add("PUMPKIN"); // Bí ngô chịu lạnh
                break;
            case "FISHERMAN":
                items.add("COD"); items.add("SALMON"); items.add("TROPICAL_FISH");
                items.add("PUFFERFISH"); items.add("FISHING_ROD");
                items.add("INK_SAC"); items.add("LILY_PAD");
                if (isCold) items.add("SALMON"); // Cá hồi nước lạnh
                if (isHot) items.add("TROPICAL_FISH"); // Cá nhiệt đới
                break;
            case "FLETCHER":
                items.add("ARROW"); items.add("BOW"); items.add("CROSSBOW");
                items.add("FLINT"); items.add("FEATHER");
                items.add("FLINT_AND_STEEL"); items.add("TRIDENT");
                if (isCold) items.add("ARROW"); // Nhiều tên hơn
                if (isWater) items.add("TRIDENT"); // Lao móc dưới nước
                break;
            case "SHEPHERD":
                items.add("WHITE_WOOL"); items.add("BLACK_WOOL");
                items.add("BROWN_WOOL"); items.add("SHEARS");
                items.add("BED"); items.add("WHITE_BED");
                items.add("PAINTING"); items.add("ITEM_FRAME");
                if (isCold) items.add("WHITE_WOOL"); // Len ấm
                break;
            case "LIBRARIAN":
                items.add("BOOK"); items.add("BOOKSHELF"); items.add("ENCHANTED_BOOK");
                items.add("WRITABLE_BOOK"); items.add("COMPASS");
                items.add("CLOCK"); items.add("NAME_TAG");
                items.add("MAP"); items.add("EXPERIENCE_BOTTLE");
                break;
            case "CARTOGRAPHER":
                items.add("MAP"); items.add("EMPTY_MAP");
                items.add("COMPASS"); items.add("RECOVERY_COMPASS");
                items.add("FILLED_MAP"); items.add("GLOBE_BANNER_PATTERN");
                items.add("NAME_TAG");
                break;
            case "CLERIC":
                items.add("EXPERIENCE_BOTTLE"); items.add("GLOWSTONE");
                items.add("REDSTONE"); items.add("LAPIS_LAZULI");
                items.add("ENDER_PEARL"); items.add("BLAZE_ROD");
                items.add("NETHER_WART"); items.add("GHAST_TEAR");
                items.add("PHANTOM_MEMBRANE");
                if (isHot || biomeName.contains("nether")) items.add("BLAZE_ROD");
                break;
            case "LEATHERWORKER":
                items.add("LEATHER_HELMET"); items.add("LEATHER_CHESTPLATE");
                items.add("LEATHER_LEGGINGS"); items.add("LEATHER_BOOTS");
                items.add("LEATHER"); items.add("RABBIT_HIDE");
                items.add("SADDLE");
                if (isCold) items.add("LEATHER_CHESTPLATE"); // Áo da ấm
                break;
            case "BUTCHER":
                items.add("BEEF"); items.add("PORKCHOP"); items.add("CHICKEN");
                items.add("MUTTON"); items.add("RABBIT");
                items.add("COOKED_BEEF"); items.add("COOKED_PORKCHOP");
                items.add("COOKED_CHICKEN"); items.add("COOKED_MUTTON");
                items.add("SWEET_BERRIES");
                if (isCold) items.add("COOKED_BEEF"); // Thịt nướng giữ nhiệt
                break;
            case "MASON":
                items.add("STONE"); items.add("COBBLESTONE"); items.add("STONE_BRICKS");
                items.add("BRICK"); items.add("CLAY");
                items.add("TERRACOTTA"); items.add("QUARTZ_BLOCK");
                items.add("CHISELED_STONE_BRICKS");
                if (isHot) items.add("TERRACOTTA"); // Gốm chịu nhiệt
                if (isWater) items.add("PRISMARINE"); // Đá biển
                break;
            default:
                items.add("EMERALD"); items.add("GOLD_INGOT");
                items.add("IRON_INGOT"); items.add("DIAMOND");
                break;
        }

        return items;
    }

    /**
     * Lấy enchantment phù hợp cho item
     */
    private String getEnchantmentForItem(String itemName) {
        Material mat = Material.getMaterial(itemName.toUpperCase());
        if (mat == null) return "";

        // Sword
        if (mat.name().contains("SWORD")) return "SHARPNESS";
        // Axe
        if (mat.name().contains("AXE")) return "SHARPNESS";
        // Bow
        if (mat.name().contains("BOW")) return "POWER";
        // Crossbow
        if (mat.name().contains("CROSSBOW")) return "PIERCING";
        // Trident
        if (mat.name().contains("TRIDENT")) return "IMPALING";
        // Helmet
        if (mat.name().contains("HELMET")) return "PROTECTION";
        // Chestplate
        if (mat.name().contains("CHESTPLATE")) return "PROTECTION";
        // Leggings
        if (mat.name().contains("LEGGINGS")) return "PROTECTION";
        // Boots
        if (mat.name().contains("BOOTS")) return "PROTECTION";
        // Pickaxe
        if (mat.name().contains("PICKAXE")) return "EFFICIENCY";
        // Shovel
        if (mat.name().contains("SHOVEL")) return "EFFICIENCY";
        // Hoe
        if (mat.name().contains("HOE")) return "EFFICIENCY";
        // Shield
        if (mat.name().contains("SHIELD")) return "";
        // Fishing Rod
        if (mat.name().contains("FISHING_ROD")) return "LUCK_OF_THE_SEA";
        // Enchanted Book
        if (mat == Material.ENCHANTED_BOOK) {
            Enchantment[] enchants = Enchantment.values();
            return enchants[random.nextInt(enchants.length)].getName();
        }

        return "";
    }

    /**
     * Sinh upgrade path cho item: level-1, level-2, ..., level-N
     * Level càng cao, item càng hiếm
     */
    private Map<String, String> generateUpgradePath(String baseItem, int maxLevel) {
        Map<String, String> path = new LinkedHashMap<>();
        path.put("level-1", baseItem);

        String current = baseItem;
        for (int level = 2; level <= maxLevel; level++) {
            String upgraded = getUpgradedItem(current, level);
            path.put("level-" + level, upgraded);
            current = upgraded;
        }

        return path;
    }

    /**
     * Lấy item nâng cấp dựa trên item hiện tại và level
     */
    private String getUpgradedItem(String currentItem, int level) {
        Material mat = Material.getMaterial(currentItem.toUpperCase());
        if (mat == null) return currentItem;

        String name = mat.name();

        // Upgrade tool/weapon materials
        if (name.contains("WOODEN_")) return name.replace("WOODEN_", "STONE_");
        if (name.contains("STONE_")) {
            if (level >= 4) return name.replace("STONE_", "DIAMOND_");
            return name.replace("STONE_", "IRON_");
        }
        if (name.contains("IRON_")) {
            if (level >= 4) return name.replace("IRON_", "DIAMOND_");
            return "ENCHANTED_BOOK";
        }
        if (name.contains("GOLDEN_")) return name.replace("GOLDEN_", "IRON_");
        if (name.contains("DIAMOND_")) {
            if (level >= 5) return name.replace("DIAMOND_", "NETHERITE_");
            return "ENCHANTED_GOLDEN_APPLE";
        }
        if (name.contains("CHAINMAIL_")) return name.replace("CHAINMAIL_", "IRON_");
        if (name.contains("LEATHER_")) return name.replace("LEATHER_", "CHAINMAIL_");

        // Food upgrade
        if (name.contains("BEEF") || name.contains("PORKCHOP") || name.contains("CHICKEN") || name.contains("MUTTON") || name.contains("RABBIT")) {
            return "COOKED_" + name;
        }
        if (name.contains("COOKED_")) {
            if (level >= 4) return "ENCHANTED_GOLDEN_APPLE";
            return "GOLDEN_CARROT";
        }
        if (name.equals("WHEAT")) { return "BREAD"; }
        if (name.equals("BREAD")) { return "CAKE"; }
        if (name.equals("CARROT")) { return "GOLDEN_CARROT"; }
        if (name.equals("POTATO")) { return "BAKED_POTATO"; }
        if (name.equals("MELON")) { return "MELON_SLICE"; }
        if (name.equals("PUMPKIN")) { return "PUMPKIN_PIE"; }

        // Material upgrade
        if (name.equals("IRON_INGOT")) { return "DIAMOND"; }
        if (name.equals("GOLD_INGOT")) { return "NETHERITE_SCRAP"; }
        if (name.equals("DIAMOND")) { return "NETHERITE_INGOT"; }
        if (name.equals("COAL")) { return "DIAMOND"; }
        if (name.equals("FLINT")) { return "IRON_INGOT"; }
        if (name.equals("FEATHER")) { return "ARROW"; }
        if (name.equals("ARROW")) { return "SPECTRAL_ARROW"; }
        if (name.equals("LEATHER")) { return "RABBIT_HIDE"; }

        // Wool
        if (name.contains("WOOL")) { return "BED"; }

        // Default: emerald based
        if (level >= 5) return "NETHERITE_INGOT";
        if (level >= 4) return "DIAMOND";
        if (level >= 3) return "GOLD_INGOT";
        return currentItem;
    }

    /**
     * Lấy material extra cost dựa trên item kết quả
     */
    private Material getExtraCostMaterial(Material resultMaterial) {
        String name = resultMaterial.name();
        if (name.contains("NETHERITE")) return Material.DIAMOND;
        if (name.contains("DIAMOND")) return Material.GOLD_INGOT;
        if (name.contains("GOLDEN") || name.contains("GOLD_")) return Material.IRON_INGOT;
        if (name.contains("IRON")) return Material.COAL;
        return Material.EMERALD;
    }

    // ============ PLAYER TRACKING ============

    /**
     * Theo dõi biome player đã khám phá
     */
    public boolean checkPlayerBiome(UUID playerUUID, String biomeKey) {
        Set<String> tracked = playerTrackedBiomes.computeIfAbsent(playerUUID, k -> new HashSet<>());
        if (tracked.contains(biomeKey)) return false;
        tracked.add(biomeKey);
        return true;
    }

    /**
     * Kiểm tra player đã từng vào biome này chưa
     */
    public boolean hasPlayerVisitedBiome(UUID playerUUID, String biomeKey) {
        Set<String> tracked = playerTrackedBiomes.get(playerUUID);
        return tracked != null && tracked.contains(biomeKey);
    }

    // ============ TRADE ENTRY CLASS ============

    public static class TradeEntry {
        public String itemMaterial = "EMERALD";
        public int baseLevel = 1;
        public int maxLevel = 5;
        public String enchantment = "";
        public Map<String, String> upgradePath = new HashMap<>();

        public TradeEntry() {}

        public TradeEntry(String itemMaterial, int baseLevel, int maxLevel) {
            this.itemMaterial = itemMaterial;
            this.baseLevel = baseLevel;
            this.maxLevel = maxLevel;
        }
    }
}