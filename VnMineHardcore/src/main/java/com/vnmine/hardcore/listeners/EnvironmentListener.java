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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
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
    private long tempCheckIntervalMs;
    private BukkitRunnable temperatureTask;
    private BukkitRunnable fogTask;
    private int fogIntervalTicks;
    private int fogEffectDurationTicks;

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

    // Các loại đuốc giúp giảm sợ hầm
    private static final Set<Material> TORCH_ITEMS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        Material.TORCH, Material.SOUL_TORCH, Material.LANTERN, Material.SOUL_LANTERN,
        Material.REDSTONE_TORCH, Material.GLOWSTONE, Material.SHROOMLIGHT,
        Material.JACK_O_LANTERN, Material.SEA_LANTERN, Material.END_ROD,
        Material.CAMPFIRE, Material.SOUL_CAMPFIRE
    )));

    public EnvironmentListener(VnMineHardcore plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.logger = plugin.getLogger();
        this.fogIntervalTicks = config.fogIntervalSeconds * 20;
        this.fogEffectDurationTicks = config.fogEffectDurationSeconds * 20;
        this.tempCheckIntervalMs = config.tempCheckIntervalSeconds * 1000L;
        logger.info("[Environment] Initialized: temperature=" + config.temperatureEnabled +
            ", fog=" + config.fogEnabled + " (interval=" + config.fogIntervalSeconds +
            "s, duration=" + config.fogEffectDurationSeconds + "s, amplifier=" + config.fogEffectAmplifier + ")" +
            ", sleep-block=" + config.sleepBlock +
            ", crop-slow=" + config.cropSlowEnabled + ", tool-wear=" + config.toolWearEnabled);

        startTasks();
    }

    public void reload() {
        fogIntervalTicks = config.fogIntervalSeconds * 20;
        fogEffectDurationTicks = config.fogEffectDurationSeconds * 20;
        tempCheckIntervalMs = config.tempCheckIntervalSeconds * 1000L;
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

                    if (lastCheck != null && now - lastCheck < tempCheckIntervalMs) continue;
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

                        // Heat effect with amplifier and helmet reduction
                        int heatAmp = config.heatAmplifier;
                        if (hasHelmet(player)) {
                            heatAmp = (int) Math.round(heatAmp * (1.0 - config.helmetReducePercent));
                        }
                        if (heatAmp >= 0) {
                            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, Math.max(0, heatAmp)));
                            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, Math.max(0, heatAmp)));
                        }

                        player.sendActionBar("§c§l🔥 QUÁ NÓNG! §7Tìm bóng râm ngay!");
                        logger.fine("[Environment] " + player.getName() + " heat damage in " + biome);
                    }

                    if (inColdBiome && exposedToSky) {
                        player.damage(config.coldDamage);

                        // Cold effect with amplifier and armor reduction
                        int coldAmp = config.coldAmplifier;
                        coldAmp = reduceByArmor(coldAmp, player);

                        if (coldAmp >= 0) {
                            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, Math.min(4, coldAmp + 1)));
                            player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 100, Math.max(0, coldAmp)));
                        }

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
                        fogEffectDurationTicks,
                        config.fogEffectAmplifier,
                        false, false, false
                    ));
                    count++;
                }
                if (count > 0) {
                    logger.fine("[Environment] Fog: " + count + " players (interval=" + fogIntervalTicks + "t)");
                }
            }
        };
        fogTask.runTaskTimer(plugin, 100L, fogIntervalTicks);
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
                int claustroAmp = config.claustrophobiaAmplifier;

                // Giảm amplifier nếu cầm đuốc
                if (isHoldingTorch(player)) {
                    claustroAmp = (int) Math.round(claustroAmp * (1.0 - config.claustrophobiaTorchReducePercent));
                }

                player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 100, Math.max(0, claustroAmp + 1)));
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, Math.max(0, claustroAmp)));
                player.sendActionBar("§0§l🕳 SỢ HẦM! §7Ngột ngạt dưới lòng đất...");
            }
        }

        // Vertigo
        if (config.vertigoEnabled && event.getTo().getY() > config.vertigoYLevel) {
            if (random.nextInt(100) < config.vertigoChance) {
                int vertigoAmp = config.vertigoAmplifier;

                // Giảm amplifier nếu đeo elytra
                if (hasElytra(player)) {
                    vertigoAmp = (int) Math.round(vertigoAmp * (1.0 - config.vertigoElytraReducePercent));
                }

                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, Math.max(0, vertigoAmp)));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, Math.max(0, vertigoAmp)));
                player.sendActionBar("§d§l🌀 CHOÁNG VÁNG! §7Ở quá cao!");
            }
        }
    }

    /**
     * Kiểm tra player có đội mũ bảo hiểm không.
     */
    private boolean hasHelmet(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        return helmet != null && helmet.getType() != Material.AIR;
    }

    /**
     * Kiểm tra player có đeo elytra không.
     */
    private boolean hasElytra(Player player) {
        ItemStack chestplate = player.getInventory().getChestplate();
        return chestplate != null && chestplate.getType() == Material.ELYTRA;
    }

    /**
     * Kiểm tra player có cầm đuốc ở tay chính hoặc tay phụ không.
     */
    private boolean isHoldingTorch(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack mainHand = inv.getItemInMainHand();
        ItemStack offHand = inv.getItemInOffHand();
        return TORCH_ITEMS.contains(mainHand.getType()) || TORCH_ITEMS.contains(offHand.getType());
    }

    /**
     * Giảm amplifier dựa trên số món giáp đang mặc.
     */
    private int reduceByArmor(int amplifier, Player player) {
        int armorPieces = 0;
        if (player.getInventory().getHelmet() != null && player.getInventory().getHelmet().getType() != Material.AIR) armorPieces++;
        if (player.getInventory().getChestplate() != null && player.getInventory().getChestplate().getType() != Material.AIR) armorPieces++;
        if (player.getInventory().getLeggings() != null && player.getInventory().getLeggings().getType() != Material.AIR) armorPieces++;
        if (player.getInventory().getBoots() != null && player.getInventory().getBoots().getType() != Material.AIR) armorPieces++;

        double reduction = armorPieces * config.armorReducePercent;
        if (reduction > 1.0) reduction = 1.0;
        return (int) Math.round(amplifier * (1.0 - reduction));
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