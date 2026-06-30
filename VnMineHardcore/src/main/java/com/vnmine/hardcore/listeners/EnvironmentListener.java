package com.vnmine.hardcore.listeners;

import com.vnmine.hardcore.VnMineHardcore;
import com.vnmine.hardcore.managers.ConfigManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.logging.Logger;

public class EnvironmentListener implements Listener {

    private final VnMineHardcore plugin;
    private final ConfigManager config;
    private final Logger logger;
    private final Map<UUID, Long> lastTemperatureCheck = new HashMap<>();
    private final Map<UUID, Long> lastWaterDamage = new HashMap<>();
    private final Map<UUID, Long> lastAcidRainDamage = new HashMap<>();
    private final Random random = new Random();
    private BukkitRunnable temperatureTask;
    private BukkitRunnable fogTask;

    // Biomes for temperature system
    private static final Set<String> HOT_BIOMES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "desert", "badlands", "wooded_badlands", "eroded_badlands",
        "nether_wastes", "soul_sand_valley", "crimson_forest", "warped_forest",
        "basalt_deltas", "savanna", "savanna_plateau", "windswept_savanna"
    )));

    private static final Set<String> COLD_BIOMES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "snowy_plains", "snowy_taiga", "snowy_slopes", "snowy_beach",
        "frozen_ocean", "frozen_river", "ice_spikes", "frozen_peaks",
        "jagged_peaks", "grove", "taiga", "old_growth_taiga",
        "cold_ocean", "deep_cold_ocean"
    )));

    public EnvironmentListener(VnMineHardcore plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.logger = plugin.getLogger();
        logger.info("[Environment] Initialized: temperature=" + config.temperatureEnabled +
            ", fog=" + config.fogEnabled + " (interval=" + config.fogIntervalTicks +
            "t, duration=" + config.fogEffectDurationTicks + "t, amplifier=" + config.fogEffectAmplifier + ")" +
            ", sleep-block=" + config.sleepBlock +
            ", crop-slow=" + config.cropSlowEnabled + ", tool-wear=" + config.toolWearEnabled);

        startTasks();
    }

    public void reload() {
        stopTasks();
        startTasks();
    }

    private void stopTasks() {
        if (temperatureTask != null) {
            temperatureTask.cancel();
            temperatureTask = null;
        }
        if (fogTask != null) {
            fogTask.cancel();
            fogTask = null;
        }
    }

    private void startTasks() {
        if (config.temperatureEnabled) startTemperatureTask();
        if (config.fogEnabled) startFogTask();
    }

    private void startTemperatureTask() {
        temperatureTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                int playersChecked = 0;

                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();
                    Long lastCheck = lastTemperatureCheck.get(uuid);

                    if (lastCheck != null && now - lastCheck < config.tempCheckIntervalMs) continue;
                    lastTemperatureCheck.put(uuid, now);
                    playersChecked++;

                    Location loc = player.getLocation();
                    String biome = loc.getBlock().getBiome().getKey().toString().toLowerCase();

                    boolean inHotBiome = HOT_BIOMES.stream().anyMatch(b -> biome.contains(b));
                    boolean inColdBiome = COLD_BIOMES.stream().anyMatch(b -> biome.contains(b));
                    boolean exposedToSky = loc.getBlock().getLightFromSky() > 5;

                    if (inHotBiome && exposedToSky) {
                        player.damage(config.heatDamage);
                        player.setFireTicks(20);
                        player.sendActionBar("§c§l🔥 QUÁ NÓNG! §7Tìm bóng râm ngay!");
                        logger.fine("[Environment] " + player.getName() + " heat damage in " + biome);
                    }

                    if (inColdBiome && exposedToSky) {
                        player.damage(config.coldDamage);
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
                        player.sendActionBar("§b§l❄ QUÁ LẠNH! §7Tìm nơi ấm áp ngay!");
                        logger.fine("[Environment] " + player.getName() + " freeze damage in " + biome);
                    }

                    if (loc.getBlock().getType() == Material.WATER ||
                        loc.getBlock().getType() == Material.BUBBLE_COLUMN) {
                        Long lastWater = lastWaterDamage.get(uuid);
                        if (lastWater == null || now - lastWater > config.waterDamageIntervalMs) {
                            player.damage(1.0);
                            player.sendActionBar("§b§l🌊 Nước lạnh! §7Hạ thân nhiệt!");
                            lastWaterDamage.put(uuid, now);
                        }
                    }

                    if (exposedToSky && player.getWorld().hasStorm()) {
                        Long lastAcid = lastAcidRainDamage.get(uuid);
                        if (lastAcid == null || now - lastAcid > config.acidRainIntervalMs) {
                            player.damage(1.0);
                            player.sendActionBar("§c§l☔ Mưa axit! §7Vào nhà ngay!");
                            lastAcidRainDamage.put(uuid, now);
                        }
                    }
                }

                if (playersChecked > 0) {
                    logger.fine("[Environment] Temp check: " + playersChecked + " players");
                }
            }
        };
        temperatureTask.runTaskTimer(plugin, 100L, 20L);
    }

    private void startFogTask() {
        fogTask = new BukkitRunnable() {
            @Override
            public void run() {
                int count = 0;
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    player.addPotionEffect(new PotionEffect(
                        PotionEffectType.DARKNESS,
                        config.fogEffectDurationTicks,
                        config.fogEffectAmplifier,
                        false, false, false
                    ));
                    count++;
                }
                if (count > 0) {
                    logger.fine("[Environment] Fog: " + count + " players (interval=" + config.fogIntervalTicks + "t)");
                }
            }
        };
        fogTask.runTaskTimer(plugin, 100L, config.fogIntervalTicks);
    }

    @EventHandler
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        if (!config.sleepBlock) return;
        Player player = event.getPlayer();
        event.setCancelled(true);
        player.damage(config.sleepDamage);
        player.sendMessage("§c§l⚠ Không thể ngủ! §7Quái vật không để bạn yên!");
        player.sendTitle("§4§lKHÔNG THỂ NGỦ!", "§cThức suốt đêm!", 10, 40, 10);
        logger.info("[Environment] " + player.getName() + " tried to sleep - damage " + config.sleepDamage);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!config.toolWearEnabled) return;
        Player player = event.getPlayer();
        if (!player.getGameMode().toString().equals("CREATIVE")) {
            var item = player.getInventory().getItemInMainHand();
            if (item != null && item.getType() != Material.AIR) {
                short durability = item.getDurability();
                item.setDurability((short) (durability + (int) config.toolWearMultiplier));
            }
        }
    }

    @EventHandler
    public void onBlockGrow(BlockGrowEvent event) {
        if (!config.cropSlowEnabled) return;
        if (random.nextDouble() > config.cropSlowMultiplier) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        if (!config.flightBlock) return;
        if (event.isFlying()) {
            Player player = event.getPlayer();
            if (!player.hasPermission("vnmine.hardcore.bypass")) {
                event.setCancelled(true);
                player.setAllowFlight(false);
                player.setFlying(false);
                player.sendMessage("§c§l🚫 Không thể bay!");
                logger.info("[Environment] " + player.getName() + " flight blocked");
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (event.getTo() == null) return;

        // Claustrophobia
        if (config.claustrophobiaEnabled && event.getTo().getY() < config.claustrophobiaYLevel) {
            if (random.nextInt(100) < config.claustrophobiaChance) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 100, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0));
                player.sendActionBar("§0§l🕳 SỢ HẦM! §7Ngột ngạt dưới lòng đất...");
            }
        }

        // Vertigo
        if (config.vertigoEnabled && event.getTo().getY() > config.vertigoYLevel) {
            if (random.nextInt(100) < config.vertigoChance) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0));
                player.sendActionBar("§d§l🌀 CHOÁNG VÁNG! §7Ở quá cao!");
            }
        }
    }

    public boolean isTemperatureEnabled() { return config.temperatureEnabled; }
    public boolean isFogEnabled() { return config.fogEnabled; }
    public boolean isSleepBlocked() { return config.sleepBlock; }
    public boolean isCropSlowEnabled() { return config.cropSlowEnabled; }
    public boolean isToolWearEnabled() { return config.toolWearEnabled; }
    public boolean isFlightBlocked() { return config.flightBlock; }
    public boolean isClaustrophobiaEnabled() { return config.claustrophobiaEnabled; }
    public boolean isVertigoEnabled() { return config.vertigoEnabled; }
}