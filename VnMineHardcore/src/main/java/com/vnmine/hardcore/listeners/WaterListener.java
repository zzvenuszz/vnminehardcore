package com.vnmine.hardcore.listeners;

import com.vnmine.hardcore.VnMineHardcore;
import com.vnmine.hardcore.managers.ConfigManager;
import com.vnmine.hardcore.managers.ThirstManager;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.logging.Logger;

public class WaterListener implements Listener {

    private final VnMineHardcore plugin;
    private final ThirstManager thirstManager;
    private final ConfigManager config;
    private final Logger logger;
    private final Map<UUID, Long> lastDrinkFromSource = new HashMap<>();
    private final Map<UUID, BukkitRunnable> activeWaterEffects = new HashMap<>();

    // Các block không thể tương tác (non-interactive) - được dùng làm "bề mặt" để uống nước từ xô
    private static final Set<Material> NON_INTERACTIVE_BLOCKS = new HashSet<>(Arrays.asList(
        Material.AIR, Material.WATER, Material.LAVA, Material.GRASS_BLOCK,
        Material.DIRT, Material.STONE, Material.SAND, Material.GRAVEL,
        Material.COBBLESTONE, Material.OAK_LOG, Material.SPRUCE_LOG,
        Material.BIRCH_LOG, Material.JUNGLE_LOG, Material.ACACIA_LOG,
        Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG,
        Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES,
        Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS,
        Material.GRASS_BLOCK, Material.SHORT_GRASS, Material.TALL_GRASS, Material.VINE,
        Material.COBBLED_DEEPSLATE, Material.DEEPSLATE, Material.TUFF,
        Material.ANDESITE, Material.DIORITE, Material.GRANITE,
        Material.OAK_SLAB, Material.SPRUCE_SLAB, Material.BIRCH_SLAB,
        Material.OAK_STAIRS, Material.SPRUCE_STAIRS, Material.BIRCH_STAIRS,
        Material.WHITE_WOOL, Material.ORANGE_WOOL, Material.MAGENTA_WOOL,
        Material.LIGHT_BLUE_WOOL, Material.YELLOW_WOOL, Material.LIME_WOOL,
        Material.PINK_WOOL, Material.GRAY_WOOL, Material.LIGHT_GRAY_WOOL,
        Material.CYAN_WOOL, Material.PURPLE_WOOL, Material.BLUE_WOOL,
        Material.BROWN_WOOL, Material.GREEN_WOOL, Material.RED_WOOL,
        Material.BLACK_WOOL, Material.SNOW_BLOCK, Material.SNOW,
        Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE,
        Material.NETHERRACK, Material.SOUL_SAND, Material.SOUL_SOIL,
        Material.BASALT, Material.POLISHED_BASALT, Material.SMOOTH_BASALT,
        Material.END_STONE, Material.OBSIDIAN, Material.CRYING_OBSIDIAN,
        Material.GLOWSTONE, Material.SEA_LANTERN, Material.PRISMARINE,
        Material.DARK_PRISMARINE, Material.PRISMARINE_BRICKS,
        Material.CLAY, Material.MUD, Material.PACKED_MUD,
        Material.MUD_BRICKS, Material.COARSE_DIRT, Material.ROOTED_DIRT,
        Material.PODZOL, Material.MYCELIUM, Material.WARPED_NYLIUM,
        Material.CRIMSON_NYLIUM, Material.NETHER_QUARTZ_ORE,
        Material.NETHER_GOLD_ORE, Material.ANCIENT_DEBRIS,
        Material.COAL_ORE, Material.IRON_ORE, Material.GOLD_ORE,
        Material.DIAMOND_ORE, Material.EMERALD_ORE, Material.LAPIS_ORE,
        Material.REDSTONE_ORE, Material.COPPER_ORE,
        Material.DEEPSLATE_COAL_ORE, Material.DEEPSLATE_IRON_ORE,
        Material.DEEPSLATE_GOLD_ORE, Material.DEEPSLATE_DIAMOND_ORE,
        Material.DEEPSLATE_EMERALD_ORE, Material.DEEPSLATE_LAPIS_ORE,
        Material.DEEPSLATE_REDSTONE_ORE, Material.DEEPSLATE_COPPER_ORE,
        Material.RAW_IRON_BLOCK, Material.RAW_GOLD_BLOCK, Material.RAW_COPPER_BLOCK,
        Material.AMETHYST_BLOCK, Material.BUDDING_AMETHYST,
        Material.CALCITE, Material.DRIPSTONE_BLOCK, Material.POINTED_DRIPSTONE,
        Material.SMOOTH_STONE, Material.SMOOTH_SANDSTONE, Material.SMOOTH_RED_SANDSTONE,
        Material.CUT_SANDSTONE, Material.CUT_RED_SANDSTONE,
        Material.CHISELED_SANDSTONE, Material.CHISELED_RED_SANDSTONE,
        Material.SANDSTONE, Material.RED_SANDSTONE, Material.BRICKS,
        Material.STONE_BRICKS, Material.CRACKED_STONE_BRICKS,
        Material.CHISELED_STONE_BRICKS, Material.MOSSY_STONE_BRICKS,
        Material.MOSSY_COBBLESTONE, Material.MOSS_BLOCK,
        Material.COBBLESTONE_SLAB, Material.STONE_SLAB, Material.SANDSTONE_SLAB,
        Material.COBBLESTONE_STAIRS, Material.STONE_STAIRS, Material.SANDSTONE_STAIRS,
        Material.WHITE_CONCRETE, Material.ORANGE_CONCRETE, Material.MAGENTA_CONCRETE,
        Material.LIGHT_BLUE_CONCRETE, Material.YELLOW_CONCRETE, Material.LIME_CONCRETE,
        Material.PINK_CONCRETE, Material.GRAY_CONCRETE, Material.LIGHT_GRAY_CONCRETE,
        Material.CYAN_CONCRETE, Material.PURPLE_CONCRETE, Material.BLUE_CONCRETE,
        Material.BROWN_CONCRETE, Material.GREEN_CONCRETE, Material.RED_CONCRETE,
        Material.BLACK_CONCRETE, Material.WHITE_CONCRETE_POWDER,
        Material.ORANGE_CONCRETE_POWDER, Material.MAGENTA_CONCRETE_POWDER,
        Material.LIGHT_BLUE_CONCRETE_POWDER, Material.YELLOW_CONCRETE_POWDER,
        Material.LIME_CONCRETE_POWDER, Material.PINK_CONCRETE_POWDER,
        Material.GRAY_CONCRETE_POWDER, Material.LIGHT_GRAY_CONCRETE_POWDER,
        Material.CYAN_CONCRETE_POWDER, Material.PURPLE_CONCRETE_POWDER,
        Material.BLUE_CONCRETE_POWDER, Material.BROWN_CONCRETE_POWDER,
        Material.GREEN_CONCRETE_POWDER, Material.RED_CONCRETE_POWDER,
        Material.BLACK_CONCRETE_POWDER, Material.TERRACOTTA,
        Material.WHITE_TERRACOTTA, Material.ORANGE_TERRACOTTA,
        Material.MAGENTA_TERRACOTTA, Material.LIGHT_BLUE_TERRACOTTA,
        Material.YELLOW_TERRACOTTA, Material.LIME_TERRACOTTA,
        Material.PINK_TERRACOTTA, Material.GRAY_TERRACOTTA,
        Material.LIGHT_GRAY_TERRACOTTA, Material.CYAN_TERRACOTTA,
        Material.PURPLE_TERRACOTTA, Material.BLUE_TERRACOTTA,
        Material.BROWN_TERRACOTTA, Material.GREEN_TERRACOTTA,
        Material.RED_TERRACOTTA, Material.BLACK_TERRACOTTA,
        Material.GLASS, Material.TINTED_GLASS, Material.WHITE_STAINED_GLASS,
        Material.ORANGE_STAINED_GLASS, Material.MAGENTA_STAINED_GLASS,
        Material.LIGHT_BLUE_STAINED_GLASS, Material.YELLOW_STAINED_GLASS,
        Material.LIME_STAINED_GLASS, Material.PINK_STAINED_GLASS,
        Material.GRAY_STAINED_GLASS, Material.LIGHT_GRAY_STAINED_GLASS,
        Material.CYAN_STAINED_GLASS, Material.PURPLE_STAINED_GLASS,
        Material.BLUE_STAINED_GLASS, Material.BROWN_STAINED_GLASS,
        Material.GREEN_STAINED_GLASS, Material.RED_STAINED_GLASS,
        Material.BLACK_STAINED_GLASS, Material.GLASS_PANE,
        Material.IRON_BLOCK, Material.GOLD_BLOCK, Material.DIAMOND_BLOCK,
        Material.EMERALD_BLOCK, Material.LAPIS_BLOCK, Material.REDSTONE_BLOCK,
        Material.NETHERITE_BLOCK, Material.COAL_BLOCK,
        Material.BONE_BLOCK, Material.HAY_BLOCK, Material.DRIED_KELP_BLOCK,
        Material.SPONGE, Material.WET_SPONGE,
        Material.MAGMA_BLOCK, Material.SHROOMLIGHT,
        Material.SCULK, Material.SCULK_VEIN, Material.SCULK_CATALYST,
        Material.SCULK_SHRIEKER, Material.SCULK_SENSOR,
        Material.OCHRE_FROGLIGHT, Material.VERDANT_FROGLIGHT, Material.PEARLESCENT_FROGLIGHT,
        Material.COPPER_BLOCK, Material.WAXED_COPPER_BLOCK,
        Material.EXPOSED_COPPER, Material.WAXED_EXPOSED_COPPER,
        Material.WEATHERED_COPPER, Material.WAXED_WEATHERED_COPPER,
        Material.OXIDIZED_COPPER, Material.WAXED_OXIDIZED_COPPER
    ));

    public WaterListener(VnMineHardcore plugin) {
        this.plugin = plugin;
        this.thirstManager = plugin.getThirstManager();
        this.config = plugin.getConfigManager();
        this.logger = plugin.getLogger();
        logger.info("[Water] Initialized: drink-from-source, bottle-fill, bucket-drink, natural-water-effects");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractBlock(PlayerInteractEvent event) {
        // Only handle RIGHT_CLICK_BLOCK
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        // Only process main hand
        if (event.getHand() != EquipmentSlot.HAND) return;

        Material blockType = clickedBlock.getType();
        ItemStack item = event.getItem();

        // ============================================================
        // TRƯỜNG HỢP 1: Click vào nước (WATER, BUBBLE_COLUMN, WATER_CAULDRON)
        // ============================================================
        boolean isWaterSource = (blockType == Material.WATER || 
                                 blockType == Material.BUBBLE_COLUMN || 
                                 blockType == Material.WATER_CAULDRON);

        if (isWaterSource) {
            handleWaterSourceClick(event, player, clickedBlock, blockType, item);
            return;
        }

        // ============================================================
        // TRƯỜNG HỢP 2: Click vào block KHÔNG phải nước, cầm WATER_BUCKET
        // Chỉ uống nước từ xô nếu bấm Shift + block không phải interactive
        // ============================================================
        if (item != null && item.getType() == Material.WATER_BUCKET && config.bucketDrinkEnabled) {
            // Phải bấm Shift mới được uống nước từ xô
            if (!player.isSneaking()) return;

            // Kiểm tra nếu block là interactive (có thể tương tác: chest, furnace, crafting table...)
            if (isInteractiveBlock(blockType)) {
                // Block có thể tương tác - không cancel, để Minecraft xử lý đặt xô
                return;
            }

            // Block không tương tác + đang sneak - uống nước từ xô
            drinkFromBucket(player, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractAir(PlayerInteractEvent event) {
        // Handle RIGHT_CLICK_AIR for water bucket drinking
        if (event.getAction() != Action.RIGHT_CLICK_AIR) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // Phải bấm Shift mới được uống nước từ xô
        if (item != null && item.getType() == Material.WATER_BUCKET && config.bucketDrinkEnabled) {
            if (!player.isSneaking()) return;
            drinkFromBucket(player, event);
        }
    }

    /**
     * Xử lý click vào nguồn nước.
     */
    private void handleWaterSourceClick(PlayerInteractEvent event, Player player, 
                                          Block clickedBlock, Material blockType, ItemStack item) {
        boolean isNaturalWater = (blockType == Material.WATER || blockType == Material.BUBBLE_COLUMN);

        // Trường hợp: Tay không -> uống từ nguồn (phải bấm Shift)
        if (item == null || item.getType() == Material.AIR) {
            if (!player.isSneaking()) return;
            UUID uuid = player.getUniqueId();
            long now = System.currentTimeMillis();
            Long last = lastDrinkFromSource.get(uuid);

            if (last != null && now - last < config.drinkSourceCooldownSeconds * 1000L) {
                long remaining = (config.drinkSourceCooldownSeconds * 1000L - (now - last)) / 1000;
                player.sendActionBar("§c⏳ Còn " + remaining + "s mới có thể uống tiếp!");
                return;
            }

            if (isNaturalWater && !config.naturalWaterEnabled) {
                player.sendActionBar("§c⛔ Không thể uống nước tự nhiên!");
                return;
            }

            // Uống nước từ nguồn
            thirstManager.drinkWater(player, config.drinkSourceRestore);
            lastDrinkFromSource.put(uuid, now);
            player.sendMessage("§b💧 Bạn đã uống nước từ " + getWaterSourceName(blockType) + "! (+" + config.drinkSourceRestore + " khát)");
            logger.fine("[Water] " + player.getName() + " drank from " + blockType);

            // Hiệu ứng nước bẩn nếu uống từ sông/biển
            if (isNaturalWater) {
                applyNaturalWaterEffects(player);
            }

            event.setCancelled(true);
            return;
        }

        // Trường hợp: Glass bottle -> lấy nước vào chai
        if (config.bottleFill && item.getType() == Material.GLASS_BOTTLE) {
            ItemStack waterBottle = new ItemStack(Material.POTION);
            // Sử dụng editMeta API của Paper 1.21 để tránh thêm component không mong muốn
            waterBottle.editMeta(meta -> {
                if (meta instanceof PotionMeta potionMeta) {
                    potionMeta.setBasePotionType(PotionType.WATER);
                }
            });

            if (item.getAmount() == 1) {
                player.getInventory().setItemInMainHand(waterBottle);
            } else {
                item.setAmount(item.getAmount() - 1);
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(waterBottle);
                if (!leftover.isEmpty()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover.get(0));
                }
            }

            player.sendMessage("§b🧴 Bạn đã lấy nước vào chai thủy tinh!");
            logger.fine("[Water] " + player.getName() + " filled glass bottle with water");
            event.setCancelled(true);
            return;
        }

        // Trường hợp: WATER_BUCKET -> uống từ xô (phải bấm Shift)
        if (item.getType() == Material.WATER_BUCKET && config.bucketDrinkEnabled) {
            if (!player.isSneaking()) return;
            drinkFromBucket(player, event);
        }
    }

    /**
     * Uống nước từ xô.
     */
    private void drinkFromBucket(Player player, PlayerInteractEvent event) {
        if (!config.bucketDrinkEnabled) {
            player.sendActionBar("§c⛔ Không thể uống nước từ xô!");
            return;
        }

        thirstManager.drinkWater(player, config.bucketRestore);
        // Thay xô nước thành xô rỗng
        player.getInventory().setItemInMainHand(new ItemStack(Material.BUCKET));
        player.sendMessage("§b🪣 Bạn đã uống nước từ xô! (+" + config.bucketRestore + " khát)");
        logger.fine("[Water] " + player.getName() + " drank from water bucket");
        event.setCancelled(true);
    }

    /**
     * Kiểm tra xem block có phải là interactive (có thể tương tác) không.
     * Nếu là interactive, không uống nước từ xô để tránh xung đột.
     */
    private boolean isInteractiveBlock(Material material) {
        // Sử dụng Bukkit Tag để kiểm tra các block interactive phổ biến
        if (Tag.DOORS.isTagged(material)) return true;
        if (Tag.TRAPDOORS.isTagged(material)) return true;
        if (Tag.BUTTONS.isTagged(material)) return true;
        if (Tag.FENCE_GATES.isTagged(material)) return true;
        if (Tag.SIGNS.isTagged(material)) return true;

        // Các block interactive đặc biệt
        switch (material) {
            // Container blocks
            case CHEST: case TRAPPED_CHEST: case BARREL: case SHULKER_BOX:
            case WHITE_SHULKER_BOX: case ORANGE_SHULKER_BOX: case MAGENTA_SHULKER_BOX:
            case LIGHT_BLUE_SHULKER_BOX: case YELLOW_SHULKER_BOX: case LIME_SHULKER_BOX:
            case PINK_SHULKER_BOX: case GRAY_SHULKER_BOX: case LIGHT_GRAY_SHULKER_BOX:
            case CYAN_SHULKER_BOX: case PURPLE_SHULKER_BOX: case BLUE_SHULKER_BOX:
            case BROWN_SHULKER_BOX: case GREEN_SHULKER_BOX: case RED_SHULKER_BOX:
            case BLACK_SHULKER_BOX:
            case FURNACE: case BLAST_FURNACE: case SMOKER:
            case CRAFTING_TABLE: case ENCHANTING_TABLE:
            case ANVIL: case CHIPPED_ANVIL: case DAMAGED_ANVIL:
            case GRINDSTONE: case CARTOGRAPHY_TABLE: case FLETCHING_TABLE:
            case SMITHING_TABLE: case STONECUTTER: case LOOM:
            case BREWING_STAND: case CAULDRON: case WATER_CAULDRON:
            case LAVA_CAULDRON: case COMPOSTER:
            case JUKEBOX: case NOTE_BLOCK:
            case LEVER: case REPEATER: case COMPARATOR:
            case DAYLIGHT_DETECTOR: case BELL:
            case RESPAWN_ANCHOR: case BEACON: case CONDUIT:
            case ENDER_CHEST: case HOPPER: case DROPPER: case DISPENSER:
            case LECTERN: case DECORATED_POT:
            case CANDLE: case WHITE_CANDLE: case ORANGE_CANDLE:
            case MAGENTA_CANDLE: case LIGHT_BLUE_CANDLE: case YELLOW_CANDLE:
            case LIME_CANDLE: case PINK_CANDLE: case GRAY_CANDLE:
            case LIGHT_GRAY_CANDLE: case CYAN_CANDLE: case PURPLE_CANDLE:
            case BLUE_CANDLE: case BROWN_CANDLE: case GREEN_CANDLE:
            case RED_CANDLE: case BLACK_CANDLE:
            case CAKE:
            case SWEET_BERRY_BUSH: case CAVE_VINES: case CAVE_VINES_PLANT:
            case FLOWERING_AZALEA: case AZALEA:
            case DRAGON_EGG: case FLOWER_POT:
                return true;
            default:
                return false;
        }
    }

    /**
     * Apply negative effects when drinking from natural water sources (rivers, oceans).
     * Gây damage mỗi giây + Nausea effect trong thời gian cấu hình.
     */
    private void applyNaturalWaterEffects(Player player) {
        UUID uuid = player.getUniqueId();

        // Cancel any existing active effect task for this player
        BukkitRunnable existingTask = activeWaterEffects.get(uuid);
        if (existingTask != null) {
            existingTask.cancel();
        }

        player.sendMessage("§c§l⚠ Nước bẩn! §7Bạn đang bị nhiễm độc!");

        BukkitRunnable task = new BukkitRunnable() {
            int elapsed = 0;
            final int totalDuration = config.naturalWaterDurationSeconds;

            @Override
            public void run() {
                if (elapsed >= totalDuration) {
                    // Effect ended
                    player.sendActionBar("§a§oHiệu ứng nước bẩn đã hết.");
                    activeWaterEffects.remove(uuid);
                    this.cancel();
                    return;
                }

                // Apply damage per second
                if (player.isOnline() && !player.isDead()) {
                    player.damage(config.naturalWaterDamagePerSecond);
                }

                // Apply Nausea effect
                player.addPotionEffect(new PotionEffect(
                    PotionEffectType.NAUSEA,
                    40, // 2 seconds (refresh every second)
                    config.naturalWaterNauseaAmplifier,
                    false, true, true
                ));

                // Apply Hunger effect (from dirty water)
                player.addPotionEffect(new PotionEffect(
                    PotionEffectType.HUNGER,
                    40,
                    0, // level I
                    false, true, true
                ));

                player.sendActionBar("§2§l☠ Nước bẩn! §a-" + (totalDuration - elapsed) + "s còn lại");

                elapsed++;
            }
        };

        activeWaterEffects.put(uuid, task);
        task.runTaskTimer(plugin, 0L, 20L); // every second
    }

    private String getWaterSourceName(Material type) {
        return switch (type) {
            case WATER, BUBBLE_COLUMN -> "sông/suối";
            case WATER_CAULDRON -> "vạc nước";
            default -> "nguồn nước";
        };
    }
}