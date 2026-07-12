package com.vnmine.hardcore.listeners;

import com.vnmine.hardcore.VnMineHardcore;
import com.vnmine.hardcore.managers.GraveManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.logging.Logger;

/**
 * Xử lý sự kiện liên quan đến ngôi mộ (Graves).
 * - Khi player chết: xây mộ và lưu items
 * - Khi player click vào head: trả items
 */
public class GraveListener implements Listener {

    private final VnMineHardcore plugin;
    private final GraveManager graveManager;
    private final Logger logger;

    public GraveListener(VnMineHardcore plugin, GraveManager graveManager) {
        this.plugin = plugin;
        this.graveManager = graveManager;
        this.logger = plugin.getLogger();
        logger.info("[GraveListener] Initialized");
    }

    /**
     * Khi người chơi chết, xây mộ tại vị trí chết
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        Location deathLocation = player.getLocation();

        // Tạo mộ tại vị trí chết
        graveManager.createGrave(player, deathLocation);

        logger.fine("[GraveListener] Grave created for " + player.getName() +
            " at " + deathLocation.getBlockX() + "," +
            deathLocation.getBlockY() + "," +
            deathLocation.getBlockZ());
    }

    /**
     * Khi người chơi click vào block head của mộ
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Material blockType = event.getClickedBlock().getType();
        if (blockType != Material.PLAYER_HEAD && blockType != Material.PLAYER_WALL_HEAD) return;

        Player player = event.getPlayer();
        Location headLocation = event.getClickedBlock().getLocation();

        // Xử lý tương tác với mộ
        boolean handled = graveManager.interactGrave(player, headLocation);
        if (handled) {
            event.setCancelled(true);
        }
    }
}