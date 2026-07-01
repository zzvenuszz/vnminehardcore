package com.vnmine.hardcore.listeners;

import com.vnmine.hardcore.VnMineHardcore;
import com.vnmine.hardcore.managers.ConfigManager;
import com.vnmine.hardcore.managers.ThirstManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.logging.Logger;

public class HungerListener implements Listener {

    private final VnMineHardcore plugin;
    private final ThirstManager thirstManager;
    private final ConfigManager config;
    private final Logger logger;
    private final Map<UUID, List<Long>> eatTimestamps = new HashMap<>();
    private BukkitRunnable hungerTask;
    private int drainIntervalTicks;

    private static final Set<Material> RAW_FOODS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        Material.BEEF, Material.CHICKEN, Material.PORKCHOP, Material.MUTTON,
        Material.RABBIT, Material.COD, Material.SALMON, Material.TROPICAL_FISH,
        Material.PUFFERFISH, Material.ROTTEN_FLESH, Material.SPIDER_EYE,
        Material.POISONOUS_POTATO
    )));

    public HungerListener(VnMineHardcore plugin, ConfigManager config) {
        this.plugin = plugin;
        this.thirstManager = plugin.getThirstManager();
        this.config = config;
        this.logger = plugin.getLogger();
        this.drainIntervalTicks = config.drainIntervalSeconds * 20;
        logger.info("[Hunger] Initialized: drain=" + config.drainIntervalSeconds + "s, food=" + (int)(config.foodRestoreMultiplier * 100) + "%");
        startHungerTask();
    }

    public void startHungerTask() {
        // Cancel old task if exists
        if (hungerTask != null) {
            hungerTask.cancel();
        }

        if (!config.hungerEnabled) {
            logger.info("[Hunger] Disabled via config");
            return;
        }

        hungerTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    int foodLevel = player.getFoodLevel();

                    if (foodLevel > 0) {
                        player.setFoodLevel(Math.max(0, foodLevel - 1));
                        player.setSaturation(0);
                    }

                    if (foodLevel <= 0) {
                        if (player.getHealth() > 0) {
                            player.damage(config.starvationDamage);
                            player.sendActionBar("§c§l☠ ĐÓI! §7Bạn đang chết đói!");
                        }
                    }

                    if (foodLevel < config.sprintMinFood && player.isSprinting()) {
                        player.setSprinting(false);
                        player.sendActionBar("§c§l⚠ Quá đói để chạy!");
                    }
                }
            }
        };
        hungerTask.runTaskTimer(plugin, 20L, drainIntervalTicks);
    }

    // Cancel vanilla food changes completely - plugin controls food
    @EventHandler(priority = EventPriority.LOWEST)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        Material itemType = event.getItem().getType();
        UUID uuid = player.getUniqueId();

        long now = System.currentTimeMillis();
        List<Long> timestamps = eatTimestamps.computeIfAbsent(uuid, k -> new ArrayList<>());
        timestamps.add(now);
        timestamps.removeIf(t -> now - t > config.chokeWindowSeconds * 1000L);

        if (config.chokeEnabled && timestamps.size() > config.chokeMaxEats) {
            player.damage(config.chokeDamage);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
            player.sendMessage("§c§l⚠ Bạn ăn quá nhanh! Bị nghẹn! (-" + (config.chokeDamage / 2) + " HP)");
            event.setCancelled(true);
            logger.info("[Hunger] " + player.getName() + " choked from eating too fast!");
            return;
        }

        // Restore food manually since we cancelled vanilla food change
        if (itemType.isEdible()) {
            int restore = getFoodRestore(itemType);
            int reduced = (int) Math.max(1, Math.ceil(restore * config.foodRestoreMultiplier));
            int newFood = Math.min(20, player.getFoodLevel() + reduced);
            player.setFoodLevel(newFood);
            player.setSaturation(0);
            logger.fine("[Hunger] " + player.getName() + " ate " + itemType + ": +" + reduced + " food");
        }

        if (config.rawFoodPoison && RAW_FOODS.contains(itemType)) {
            switch (itemType) {
                case ROTTEN_FLESH:
                    player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 300, 1));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 200, 0));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0));
                    player.sendMessage("§2§l☠ Thịt thối! §aBạn bị ngộ độc!");
                    break;
                case SPIDER_EYE: case POISONOUS_POTATO:
                    player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 200, 0));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 200, 0));
                    player.sendMessage("§a§l☠ Độc tố! §2Bạn bị trúng độc!");
                    break;
                case PUFFERFISH:
                    player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 400, 1));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 400, 2));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 400, 1));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 400, 0));
                    player.sendMessage("§4§l☠ CÁ NÓC! §cNgộ độc nặng!");
                    break;
                default:
                    player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 200, 0));
                    player.sendMessage("§c§l⚠ Thịt sống! §7Bạn bị ngộ độc nhẹ!");
                    break;
            }
        }

        if (ThirstManager.canDrink(event.getItem())) {
            thirstManager.drinkWater(player);
            player.sendMessage("§b§l💧 Bạn đã uống nước! §7(Khát: " + thirstManager.getThirstBar(player) + ")");
        }

        if (itemType == Material.SUSPICIOUS_STEW) {
            PotionEffectType[] badEffects = {
                PotionEffectType.POISON, PotionEffectType.HUNGER, PotionEffectType.WEAKNESS,
                PotionEffectType.SLOWNESS, PotionEffectType.BLINDNESS, PotionEffectType.NAUSEA
            };
            PotionEffectType randomEffect = badEffects[new Random().nextInt(badEffects.length)];
            player.addPotionEffect(new PotionEffect(randomEffect, 200, 0));
            player.sendMessage("§5§l❓ Súp bí ẩn! §dHiệu ứng: " + randomEffect.getName());
        }

        if (config.notchAppleExplode && itemType == Material.ENCHANTED_GOLDEN_APPLE) {
            event.setCancelled(true);
            player.getWorld().createExplosion(player.getLocation(), 4.0f, false, true);
            player.damage(config.notchAppleDamage);
            player.sendMessage("§4§l💥 Táo vàng phát nổ!");
            logger.info("[Hunger] " + player.getName() + " tried enchanted golden apple - exploded");
        }
    }

    private int getFoodRestore(Material material) {
        // Vanilla food restoration values
        return switch (material) {
            case APPLE, MUSHROOM_STEW, BEETROOT_SOUP, RABBIT_STEW, DRIED_KELP -> 6;
            case BAKED_POTATO, COOKED_COD, COOKED_SALMON, COOKED_MUTTON, COOKED_CHICKEN, COOKED_PORKCHOP, COOKED_BEEF, COOKED_RABBIT -> 6;
            case BREAD, COOKIE, PUMPKIN_PIE -> 5;
            case GOLDEN_APPLE, ENCHANTED_GOLDEN_APPLE -> 4;
            case GOLDEN_CARROT -> 6;
            case CARROT, POTATO, BEETROOT -> 3;
            case MELON_SLICE, SWEET_BERRIES, GLOW_BERRIES -> 2;
            case CHORUS_FRUIT -> 4;
            case BEEF, CHICKEN, PORKCHOP, MUTTON, RABBIT, COD, SALMON -> 3;
            case TROPICAL_FISH -> 1;
            case PUFFERFISH -> 1;
            case ROTTEN_FLESH -> 4;
            case SPIDER_EYE -> 2;
            case POISONOUS_POTATO -> 2;
            case SUSPICIOUS_STEW -> 6;
            default -> 0;
        };
    }

    public void reload() {
        drainIntervalTicks = config.drainIntervalSeconds * 20;
        startHungerTask();
    }
}