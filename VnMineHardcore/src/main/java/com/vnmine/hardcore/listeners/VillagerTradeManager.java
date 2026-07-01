package com.vnmine.hardcore.listeners;

import com.vnmine.hardcore.VnMineHardcore;
import com.vnmine.hardcore.managers.ConfigManager;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;

import java.util.logging.Logger;

public class VillagerTradeManager implements Listener {

    private final VnMineHardcore plugin;
    private final ConfigManager config;
    private final Logger logger;

    public VillagerTradeManager(VnMineHardcore plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.logger = plugin.getLogger();
        logger.info("[VillagerTrade] Initialized: enabled=" + config.villagerTradingEnabled +
            ", disableRandom=" + config.disableRandomVillager);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVillagerCareerChange(VillagerCareerChangeEvent event) {
        if (!config.villagerTradingEnabled) return;

        Villager villager = event.getEntity();
        Villager.Profession newProfession = event.getProfession();

        logger.fine("[VillagerTrade] " + villager.getUniqueId() + " changed career to " + newProfession +
            " at " + villager.getLocation().getBlockX() + "," +
            villager.getLocation().getBlockY() + "," +
            villager.getLocation().getBlockZ() +
            " biome: " + villager.getLocation().getBlock().getBiome().getKey());
    }

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