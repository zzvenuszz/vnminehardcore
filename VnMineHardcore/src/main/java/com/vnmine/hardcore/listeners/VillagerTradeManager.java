package com.vnmine.hardcore.listeners;

import com.vnmine.hardcore.VnMineHardcore;
import com.vnmine.hardcore.managers.ConfigManager;
import com.vnmine.hardcore.managers.VillagerDataManager;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.MerchantRecipe;

import java.util.List;
import java.util.logging.Logger;

public class VillagerTradeManager implements Listener {

    private final VnMineHardcore plugin;
    private final ConfigManager config;
    private final VillagerDataManager villagerData;
    private final Logger logger;

    public VillagerTradeManager(VnMineHardcore plugin, ConfigManager config, VillagerDataManager villagerData) {
        this.plugin = plugin;
        this.config = config;
        this.villagerData = villagerData;
        this.logger = plugin.getLogger();
        logger.info("[VillagerTrade] Initialized: enabled=" + config.villagerTradingEnabled +
            ", disableRandom=" + config.disableRandomVillager);
    }

    /**
     * Khi player di chuyển, kiểm tra biome mới và sinh data nếu cần
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!config.villagerTradingEnabled) return;
        if (event.getTo() == null) return;

        // Chỉ kiểm tra khi đổi block (không spam khi di chuyển trong cùng block)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        Player player = event.getPlayer();
        Location loc = event.getTo();
        String biomeKey = villagerData.getBiomeKey(loc);

        // Kiểm tra player đã vào biome này chưa
        if (!villagerData.hasPlayerVisitedBiome(player.getUniqueId(), biomeKey)) {
            // Đánh dấu đã khám phá
            villagerData.checkPlayerBiome(player.getUniqueId(), biomeKey);

            // Nếu biome chưa có data, sinh data mới
            if (!villagerData.hasBiomeData(biomeKey)) {
                villagerData.generateBiomeData(biomeKey, loc);
                player.sendMessage("§a§l🌍 Khám phá biome mới! §7Dữ liệu giao dịch đã được tạo cho khu vực này.");
                logger.info("[VillagerTrade] " + player.getName() + " discovered new biome region: " + biomeKey);
            }
        }
    }

    /**
     * Khi villager đổi nghề (đặt workstation), áp dụng trade từ data
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVillagerCareerChange(VillagerCareerChangeEvent event) {
        if (!config.villagerTradingEnabled) return;

        Villager villager = event.getEntity();
        Villager.Profession newProfession = event.getProfession();

        // Bỏ qua nếu là NONE hoặc NITWIT
        if (newProfession == Villager.Profession.NONE || newProfession == Villager.Profession.NITWIT) return;

        Location loc = villager.getLocation();
        String biomeKey = villagerData.getBiomeKey(loc);

        // Nếu biome chưa có data, sinh data (trường hợp villager ở biome chưa ai khám phá)
        if (!villagerData.hasBiomeData(biomeKey)) {
            villagerData.generateBiomeData(biomeKey, loc);
        }

        // Lấy danh sách trade cho profession này trong biome này
        List<VillagerDataManager.TradeEntry> trades = villagerData.getTradesForProfession(biomeKey, newProfession.name());

        if (trades.isEmpty()) {
            logger.fine("[VillagerTrade] No trades for " + newProfession + " in " + biomeKey);
            return;
        }

        // Clear trades cũ và set trades mới
        villager.setRecipes(convertToRecipes(trades, villager.getVillagerLevel()));

        logger.fine("[VillagerTrade] " + villager.getUniqueId() + " set " + trades.size() +
            " trades for " + newProfession + " in " + biomeKey);
    }

    /**
     * Khi player tương tác với villager, cập nhật trade theo level hiện tại
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractVillager(PlayerInteractEntityEvent event) {
        if (!config.villagerTradingEnabled) return;
        if (!(event.getRightClicked() instanceof Villager villager)) return;

        Villager.Profession prof = villager.getProfession();
        if (prof == Villager.Profession.NONE || prof == Villager.Profession.NITWIT) return;

        Location loc = villager.getLocation();
        String biomeKey = villagerData.getBiomeKey(loc);

        if (!villagerData.hasBiomeData(biomeKey)) return;

        List<VillagerDataManager.TradeEntry> trades = villagerData.getTradesForProfession(biomeKey, prof.name());
        if (trades.isEmpty()) return;

        // Cập nhật recipes theo level hiện tại của villager
        villager.setRecipes(convertToRecipes(trades, villager.getVillagerLevel()));
    }

    /**
     * Chặn random nghề khi đặt workstation (nếu disableRandomVillager = true)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!config.villagerTradingEnabled) return;
        if (!config.disableRandomVillager) return;

        Block block = event.getBlockPlaced();
        if (!isWorkstation(block.getType())) return;

        Player player = event.getPlayer();

        // Check for nearby unemployed villagers
        block.getWorld().getNearbyEntities(block.getLocation(), 5, 5, 5).stream()
            .filter(e -> e instanceof Villager)
            .map(e -> (Villager) e)
            .filter(v -> v.getProfession() == Villager.Profession.NONE || v.getProfession() == Villager.Profession.NITWIT)
            .forEach(v -> {
                player.sendMessage("§c⚠ Có một dân làng thất nghiệp gần đây! " +
                    "Không thể random nghề khi đặt block công việc!");
            });
    }

    /**
     * Chuyển đổi TradeEntry list thành MerchantRecipe list
     */
    private List<MerchantRecipe> convertToRecipes(List<VillagerDataManager.TradeEntry> entries, int villagerLevel) {
        List<MerchantRecipe> recipes = new java.util.ArrayList<>();
        for (VillagerDataManager.TradeEntry entry : entries) {
            MerchantRecipe recipe = villagerData.createRecipe(entry, villagerLevel);
            recipes.add(recipe);
        }
        return recipes;
    }

    private boolean isWorkstation(org.bukkit.Material material) {
        return switch (material) {
            case BARREL, BLAST_FURNACE, SMOKER, FURNACE, BREWING_STAND,
                 CARTOGRAPHY_TABLE, CAULDRON, WATER_CAULDRON, COMPOSTER,
                 FLETCHING_TABLE, GRINDSTONE, LECTERN, LOOM, SMITHING_TABLE,
                 STONECUTTER -> true;
            default -> false;
        };
    }
}