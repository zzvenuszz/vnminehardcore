package com.vnmine.hardcore.listeners;

import com.vnmine.hardcore.VnMineHardcore;
import com.vnmine.hardcore.managers.ConfigManager;
import com.vnmine.hardcore.managers.ThirstManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class WaterListener implements Listener {

    private final VnMineHardcore plugin;
    private final ThirstManager thirstManager;
    private final ConfigManager config;
    private final Logger logger;
    private final Map<UUID, Long> lastDrinkFromSource = new HashMap<>();
    private final Map<UUID, BukkitRunnable> activeWaterEffects = new HashMap<>();

    public WaterListener(VnMineHardcore plugin) {
        this.plugin = plugin;
        this.thirstManager = plugin.getThirstManager();
        this.config = plugin.getConfigManager();
        this.logger = plugin.getLogger();
        logger.info("[Water] Initialized: drink-from-source, bottle-fill, bucket-drink, natural-water-effects");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        Material blockType = clickedBlock.getType();

        // Check if clicking on a water source
        boolean isWaterSource = blockType == Material.WATER || blockType == Material.BUBBLE_COLUMN;

        // Also check cauldrons with water
        if (blockType == Material.WATER_CAULDRON) {
            isWaterSource = true;
        }

        if (!isWaterSource) return;

        ItemStack item = event.getItem();

        // Case 1: Empty hand -> drink directly from water source
        if ((item == null || item.getType() == Material.AIR)) {
            UUID uuid = player.getUniqueId();
            long now = System.currentTimeMillis();
            Long last = lastDrinkFromSource.get(uuid);

            if (last != null && now - last < config.drinkSourceCooldownMs) {
                long remaining = (config.drinkSourceCooldownMs - (now - last)) / 1000;
                player.sendActionBar("§c⏳ Còn " + remaining + "s mới có thể uống tiếp!");
                return;
            }

            // Check if natural water drinking is enabled
            boolean isNaturalWater = (blockType == Material.WATER || blockType == Material.BUBBLE_COLUMN);

            if (isNaturalWater && !config.naturalWaterEnabled) {
                player.sendActionBar("§c⛔ Không thể uống nước tự nhiên!");
                return;
            }

            // Drink from source
            thirstManager.drinkWater(player);
            lastDrinkFromSource.put(uuid, now);
            player.sendMessage("§b💧 Bạn đã uống nước từ " + getWaterSourceName(blockType) + "! (+" + config.drinkSourceRestore + " khát)");
            logger.fine("[Water] " + player.getName() + " drank from " + blockType);

            // Apply natural water effects if drinking from rivers/oceans (not cauldron)
            if (isNaturalWater) {
                applyNaturalWaterEffects(player);
            }

            event.setCancelled(true);
            return;
        }

        // Don't process further if drinking-from-source is not enabled for hand actions
        if (!config.drinkFromSource) return;

        // Case 2: Glass bottle -> fill with water (replace in hand)
        if (config.bottleFill && item != null && item.getType() == Material.GLASS_BOTTLE) {
            ItemStack waterBottle = new ItemStack(Material.POTION);
            PotionMeta meta = (PotionMeta) waterBottle.getItemMeta();
            if (meta != null) {
                meta.setBasePotionType(PotionType.WATER);
                waterBottle.setItemMeta(meta);
            }

            // Replace the glass bottle(s) in hand with water bottle(s)
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

        // Case 3: Water bucket -> drink from bucket
        if (item != null && item.getType() == Material.WATER_BUCKET) {
            thirstManager.drinkWater(player);
            // Replace bucket with empty bucket
            player.getInventory().setItemInMainHand(new ItemStack(Material.BUCKET));
            player.sendMessage("§b🪣 Bạn đã uống nước từ xô! (+" + config.bucketRestore + " khát)");
            logger.fine("[Water] " + player.getName() + " drank from water bucket");
            event.setCancelled(true);
            return;
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