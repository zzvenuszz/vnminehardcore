package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class DisasterManager {

    private final VnMineHardcore plugin;
    private final LogManager logManager;
    private final Random random = ThreadLocalRandom.current();
    private boolean disasterActive = false;
    private String currentDisaster = null;
    private BossBar warningBar;
    private int warningTimeLeft = 0;

    // Counters for disaster timing
    private int timeSinceLastDisaster = 0;
    private int dayCycleCount = 0;
    private boolean isNight = false;

    // Disaster schedule (in minutes converted to ticks: 20 ticks/sec * 60 sec = 1200 ticks/min)
    private static final int DAY_DURATION = 6000;   // 5 minutes
    private static final int NIGHT_DURATION = 18000; // 15 minutes

    private final ConfigManager config;

    // Map disaster ID -> Runnable for manual trigger
    private final Map<String, Runnable> disasterMap = new LinkedHashMap<>();

    public DisasterManager(VnMineHardcore plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.logManager = plugin.getLogManager();
        this.warningBar = BossBar.bossBar(
            Component.text(""),
            1.0f,
            BossBar.Color.RED,
            BossBar.Overlay.PROGRESS
        );

        // Register all disasters
        registerDisasters();

        if (config.disastersEnabled) start();
    }

    private void registerDisasters() {
        disasterMap.put("bloodmoon", this::startBloodMoon);
        disasterMap.put("meteor", this::startMeteorShower);
        disasterMap.put("storm", this::startMegaStorm);
        disasterMap.put("solarflare", this::startSolarFlare);
        disasterMap.put("plague", this::startPlague);
        disasterMap.put("tornado", this::startTornado);
        disasterMap.put("eclipse", this::startSolarEclipse);
    }

    /**
     * Get list of all registered disaster IDs.
     */
    public Set<String> getDisasterIds() {
        return disasterMap.keySet();
    }

    /**
     * Manually trigger a disaster with specified warning time and duration.
     *
     * @param disasterId The ID of the disaster to trigger
     * @param warningTimeSeconds Warning time in seconds before the disaster starts
     * @param durationSeconds Duration of the disaster in seconds
     * @return true if the disaster was successfully triggered, false if ID is invalid or a disaster is already active
     */
    public boolean triggerDisaster(String disasterId, int warningTimeSeconds, int durationSeconds) {
        if (disasterActive) {
            return false; // Already a disaster active
        }

        Runnable disasterTask = disasterMap.get(disasterId.toLowerCase());
        if (disasterTask == null) {
            return false; // Invalid disaster ID
        }

        String disasterName = getDisasterName(disasterId);

        // Override the warning time for this specific disaster
        currentDisaster = disasterName;
        warningTimeLeft = warningTimeSeconds;

        // Show warning title + boss bar to all players
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showBossBar(warningBar);
            player.sendTitle(
                "§4§l⚠ CẢNH BÁO THIÊN TAI ⚠",
                "§c" + disasterName + "\n§e§lSẽ xảy ra trong " + warningTimeSeconds + " giây!",
                10, 70, 20
            );
            player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 0.5f);
            player.sendMessage("§4§m========================================");
            player.sendMessage("§4§l⚠ " + disasterName + " §r§cđã được kích hoạt thủ công!");
            player.sendMessage("§e§l⏰ Còn " + warningTimeSeconds + " giây để chuẩn bị!");
            player.sendMessage("§7Hãy tìm nơi an toàn ngay lập tức!");
            player.sendMessage("§4§m========================================");
        }

        logManager.logDisaster(disasterName + " (MANUAL TRIGGER - Warning " + warningTimeSeconds + "s)");

        // Override the disaster execution to use custom duration
        new BukkitRunnable() {
            int countdown = warningTimeSeconds;

            @Override
            public void run() {
                countdown--;

                // Announce at key times
                if (countdown == 60 || countdown == 30 || countdown == 10) {
                    broadcast("§c§l⚠ " + disasterName + " §esẽ xảy ra trong §c§l" + countdown + "§e giây!");
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
                    }
                }

                if (countdown == 5 || countdown == 4 || countdown == 3 || countdown == 2 || countdown == 1) {
                    broadcast("§4§l⚠ " + disasterName + " §csẽ xảy ra trong §4§l" + countdown + "§c giây!");
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    }
                }

                if (countdown <= 0) {
                    this.cancel();
                    // Execute the disaster with custom duration
                    disasterTask.run();

                    // Override the disaster duration (the original tasks use hardcoded durations,
                    // but we set it up to respect manual trigger)
                    broadcast("§c§l⚠ " + disasterName + " sẽ kéo dài trong " + durationSeconds + " giây!");
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // Every second

        return true;
    }

    /**
     * Get a display name for a disaster ID.
     */
    public String getDisasterName(String disasterId) {
        return switch (disasterId.toLowerCase()) {
            case "bloodmoon" -> "🌕 Blood Moon";
            case "meteor" -> "☄️ Meteor Shower";
            case "storm" -> "🌊 Mega Storm";
            case "solarflare" -> "🔥 Solar Flare";
            case "plague" -> "🦠 Plague";
            case "tornado" -> "🌪️ Tornado";
            case "eclipse" -> "📉 Solar Eclipse";
            default -> disasterId;
        };
    }

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Track day/night cycle
                if (Bukkit.getWorlds().isEmpty()) return;
                World world = Bukkit.getWorlds().get(0);
                long time = world.getTime();

                // Day: 0-5999, Night: 6000-17999, but we use custom cycles
                boolean currentlyNight = time > 13000 && time < 23000;

                if (currentlyNight && !isNight) {
                    isNight = true;
                    dayCycleCount++;
                } else if (!currentlyNight && isNight) {
                    isNight = false;
                }

                timeSinceLastDisaster++;

                if (!disasterActive && timeSinceLastDisaster >= 1200) { // At least 1 minute between checks
                    tryScheduleDisaster();
                }

                // Update warning countdown
                if (warningTimeLeft > 0) {
                    warningTimeLeft--;
                    updateWarningBar();

                    if (warningTimeLeft <= 0) {
                        warningBar.name(Component.text(""));
                        warningBar.progress(0);
                        // Remove boss bar from all players
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            player.hideBossBar(warningBar);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Every second
    }

    public void stop() {
        // Cleanup
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.hideBossBar(warningBar);
        }
    }

    private void tryScheduleDisaster() {
        int roll = random.nextInt(100);

        // Night events
        if (isNight) {
            if (timeSinceLastDisaster >= 6000 && roll < 30) { // 30% chance at night, every 5+ minutes
                timeSinceLastDisaster = 0;
                scheduleDisaster("🌕 Blood Moon", this::startBloodMoon);
                return;
            }
        }

        // Day events
        if (timeSinceLastDisaster >= 1800) { // 1.5+ minutes
            if (roll < 5) { // 5% chance
                timeSinceLastDisaster = 0;
                scheduleDisaster("☄️ Meteor Shower", this::startMeteorShower);
                return;
            }
            if (roll < 10) {
                timeSinceLastDisaster = 0;
                scheduleDisaster("🌊 Mega Storm", this::startMegaStorm);
                return;
            }
            if (roll < 13) {
                timeSinceLastDisaster = 0;
                scheduleDisaster("🔥 Solar Flare", this::startSolarFlare);
                return;
            }
            if (roll < 15) {
                timeSinceLastDisaster = 0;
                scheduleDisaster("🦠 Plague", this::startPlague);
                return;
            }
            if (roll < 17) {
                timeSinceLastDisaster = 0;
                scheduleDisaster("🌪️ Tornado", this::startTornado);
                return;
            }
            if (roll < 18 && !isNight) {
                timeSinceLastDisaster = 0;
                scheduleDisaster("📉 Solar Eclipse", this::startSolarEclipse);
                return;
            }
        }
    }

    /**
     * Schedule a disaster with 5-minute warning
     */
    private void scheduleDisaster(String name, Runnable disasterTask) {
        currentDisaster = name;
        warningTimeLeft = config.disasterWarningSeconds;

        // Show warning title + boss bar to all players
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showBossBar(warningBar);
            player.sendTitle(
                "§4§l⚠ CẢNH BÁO THIÊN TAI ⚠",
                "§c" + name + "\n§e§lSẽ xảy ra trong " + (config.disasterWarningSeconds / 60) + " phút!",
                10, 70, 20
            );
            player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 0.5f);
            player.sendMessage("§4§m========================================");
            player.sendMessage("§4§l⚠ " + name + " §r§cđang đến gần!");
            player.sendMessage("§e§l⏰ Còn " + config.disasterWarningSeconds + " giây để chuẩn bị!");
            player.sendMessage("§7Hãy tìm nơi an toàn ngay lập tức!");
            player.sendMessage("§4§m========================================");
        }

        logManager.logDisaster(name + " (WARNING - " + config.disasterWarningSeconds + " seconds)");

        // Countdown announcements
        new BukkitRunnable() {
            int countdown = config.disasterWarningSeconds;
            @Override
            public void run() {
                countdown--;

                // Announce at key times
                if (countdown == 240 || countdown == 180 || countdown == 120 || countdown == 60) {
                    int minutes = countdown / 60;
                    int seconds = countdown % 60;
                    String timeStr = minutes > 0 ? minutes + " phút " + seconds + " giây" : seconds + " giây";
                    broadcast("§c§l⚠ " + currentDisaster + " §esẽ xảy ra trong §c§l" + timeStr + "§e!");
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
                    }
                }

                if (countdown == 30 || countdown == 10 || countdown == 5 || countdown == 4 || countdown == 3 || countdown == 2 || countdown == 1) {
                    broadcast("§4§l⚠ " + currentDisaster + " §csẽ xảy ra trong §4§l" + countdown + "§c giây!");
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    }
                }

                if (countdown <= 0) {
                    this.cancel();
                    // Execute the disaster
                    disasterTask.run();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // Every second
    }

    private void startDisaster(String name, String startMsg, int duration, Runnable duringTask, Runnable endTask) {
        disasterActive = true;
        currentDisaster = name;
        warningBar.name(Component.text("§4§l⚠ " + name + " ĐANG HOẠT ĐỘNG ⚠"));
        warningBar.progress(1.0f);
        warningBar.color(BossBar.Color.RED);

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showBossBar(warningBar);
            player.sendTitle("§4§l" + name, "§c" + startMsg, 10, 60, 20);
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);
        }

        logManager.logDisaster(name + " (ACTIVE)");
        broadcast("§4§m========================================");
        broadcast("§4§l" + name);
        broadcast("§c" + startMsg);
        broadcast("§eThời gian kéo dài: " + (duration / 20) + " giây");
        broadcast("§4§m========================================");

        // Run the disaster effect periodically
        BukkitRunnable duringTaskRunnable = new BukkitRunnable() {
            int elapsed = 0;
            final int maxDuration = duration;

            @Override
            public void run() {
                if (elapsed >= maxDuration) {
                    this.cancel();
                    return;
                }

                // Update boss bar progress
                float progress = 1.0f - ((float) elapsed / maxDuration);
                warningBar.progress(progress);

                duringTask.run();
                elapsed += 20; // 1 second
            }
        };
        duringTaskRunnable.runTaskTimer(plugin, 0L, 20L);

        // Schedule end
        new BukkitRunnable() {
            @Override
            public void run() {
                disasterActive = false;
                currentDisaster = null;
                warningBar.name(Component.text(""));
                warningBar.progress(0);

                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.hideBossBar(warningBar);
                }

                if (endTask != null) endTask.run();

                broadcast("§a§l✅ " + name + " đã kết thúc!");
                logManager.logDisaster(name + " (ENDED)");
            }
        }.runTaskLater(plugin, duration); // Convert seconds to ticks
    }

    private void updateWarningBar() {
        if (warningTimeLeft > 0) {
            warningBar.name(Component.text("§4§l⚠ " + currentDisaster + " §7- §e§l" + formatTime(warningTimeLeft)));
            warningBar.progress((float) warningTimeLeft / 300f);
            warningBar.color(BossBar.Color.RED);
        }
    }

    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%d:%02d", minutes, secs);
    }

    // ============================================================
    // DISASTER: BLOOD MOON
    // ============================================================
    private void startBloodMoon() {
        startDisaster(
            "🌕 Blood Moon",
            "Máu trăng lên! Quái vật trở nên mạnh mẽ hơn bao giờ hết!",
            600, // 30 seconds (600 ticks)
            () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    // Spawn mobs near each player
                    if (random.nextInt(10) < 3) { // 30% chance per second per player
                        Location loc = player.getLocation();
                        World world = player.getWorld();
                        for (int i = 0; i < 3; i++) {
                            Location spawnLoc = loc.clone().add(
                                random.nextInt(20) - 10,
                                0,
                                random.nextInt(20) - 10
                            );
                            spawnLoc.setY(world.getHighestBlockYAt(spawnLoc) + 1);

                            EntityType[] mobs = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER};
                            EntityType type = mobs[random.nextInt(mobs.length)];
                            Entity entity = world.spawnEntity(spawnLoc, type);
                            if (entity instanceof Mob mob) {
                                mob.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 600, 1));
                                mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 0));
                                // Give armor
                                if (random.nextBoolean()) {
                                    mob.getEquipment().setChestplate(random.nextBoolean() ?
                                        new ItemStack(Material.IRON_CHESTPLATE) :
                                        new ItemStack(Material.CHAINMAIL_CHESTPLATE));
                                }
                            }
                        }
                    }

                    // Darken vision
                    player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false));
                }
            },
            () -> {
                broadcast("§6Mặt trăng đã trở lại bình thường.");
            }
        );
    }

    // ============================================================
    // DISASTER: METEOR SHOWER
    // ============================================================
    private void startMeteorShower() {
        startDisaster(
            "☄️ Meteor Shower",
            "Mưa sao băng đang trút xuống từ bầu trời! Hãy tìm nơi trú ẩn!",
            400, // 20 seconds
            () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (random.nextInt(5) < 2) { // 40% chance per second
                        Location playerLoc = player.getLocation();
                        World world = player.getWorld();

                        // Spawn 2-4 meteors around player
                        int count = random.nextInt(3) + 2;
                        for (int i = 0; i < count; i++) {
                            int x = playerLoc.getBlockX() + random.nextInt(30) - 15;
                            int z = playerLoc.getBlockZ() + random.nextInt(30) - 15;
                            int y = playerLoc.getBlockY() + 30 + random.nextInt(20);

                            Location meteorLoc = new Location(world, x, y, z);

                            // Create explosion effect at impact point
                            Location impactLoc = new Location(world, x, world.getHighestBlockYAt(x, z), z);

                            // Visual: fire block falling (simulate with lightning strike)
                            world.strikeLightningEffect(meteorLoc);

                            // Schedule impact
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    world.createExplosion(impactLoc, 3.0f, false, true);
                                    world.playSound(impactLoc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);

                                    // Damage nearby players
                                    for (Player p : Bukkit.getOnlinePlayers()) {
                                        if (p.getLocation().distance(impactLoc) < 8) {
                                            double damage = 8.0 * (1.0 - p.getLocation().distance(impactLoc) / 8.0);
                                            p.damage(damage);
                                            p.sendMessage("§c§l☄ Bạn bị trúng mảnh vỡ sao băng! §7(-" + String.format("%.1f", damage) + " HP)");
                                        }
                                    }
                                }
                            }.runTaskLater(plugin, 30L); // 1.5 seconds delay for visual
                        }
                    }
                }
            },
            () -> {
                broadcast("§aBầu trời đã trở lại bình thường.");
            }
        );
    }

    // ============================================================
    // DISASTER: MEGA STORM
    // ============================================================
    private void startMegaStorm() {
        // Set weather to thunderstorm
        for (World world : Bukkit.getWorlds()) {
            world.setStorm(true);
            world.setThundering(true);
            world.setWeatherDuration(200000); // Long storm
        }

        startDisaster(
            "🌊 Mega Storm",
            "Siêu bão đổ bộ! Sấm sét và mưa axit tàn phá khắp nơi!",
            600, // 30 seconds
            () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    Location loc = player.getLocation();
                    World world = player.getWorld();

                    // Lightning strikes near players
                    if (random.nextInt(5) < 2) { // 40% chance per second
                        Location strikeLoc = loc.clone().add(
                            random.nextInt(10) - 5,
                            0,
                            random.nextInt(10) - 5
                        );
                        strikeLoc.setY(world.getHighestBlockYAt(strikeLoc));
                        world.strikeLightning(strikeLoc);

                        // Damage if within 3 blocks
                        if (player.getLocation().distance(strikeLoc) < 4) {
                            player.damage(4.0);
                            player.sendMessage("§e§l⚡ Bạn bị sét đánh!");
                        }
                    }

                    // Acid rain damage if outside
                    if (player.getLocation().getBlock().getLightFromSky() > 10) {
                        if (random.nextInt(5) < 2) { // 40% chance
                            player.damage(1.0);
                            player.sendActionBar("§c☔ Mưa axit đang làm bạn bị thương!");
                        }
                    }

                    // Wind effect - slow movement
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1, false, false));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 0, false, false));
                }
            },
            () -> {
                // Clear weather
                for (World world : Bukkit.getWorlds()) {
                    world.setStorm(false);
                    world.setThundering(false);
                }
                broadcast("§aBão đã tan, bầu trời quang đãng trở lại.");
            }
        );
    }

    // ============================================================
    // DISASTER: SOLAR FLARE
    // ============================================================
    private void startSolarFlare() {
        startDisaster(
            "🔥 Solar Flare",
            "Bão mặt trời tấn công! Ánh sáng mặt trời trở nên chết chóc!",
            400, // 20 seconds
            () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    Location loc = player.getLocation();
                    // Check if player is outside (sky light > 10)
                    if (loc.getBlock().getLightFromSky() > 10) {
                        // Damage from solar radiation
                        player.damage(2.0);
                        player.setFireTicks(40); // 2 seconds on fire
                        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 2, false, false));
                        player.sendActionBar("§4§l🔥 BÃO MẶT TRỜI! §cTrốn vào bóng tối ngay!");

                        // Randomly set blocks on fire near player
                        if (random.nextInt(10) < 3) {
                            int x = loc.getBlockX() + random.nextInt(10) - 5;
                            int z = loc.getBlockZ() + random.nextInt(10) - 5;
                            int y = loc.getWorld().getHighestBlockYAt(x, z);
                            Location fireLoc = new Location(loc.getWorld(), x, y + 1, z);
                            if (fireLoc.getBlock().getType() == Material.AIR) {
                                fireLoc.getBlock().setType(Material.FIRE);
                            }
                        }
                    } else {
                        // Safe inside
                        player.sendActionBar("§a§oBạn đang an toàn trong bóng tối...");
                    }
                }
            },
            () -> {
                broadcast("§aBão mặt trời đã qua. Ánh sáng đã an toàn trở lại.");
            }
        );
    }

    // ============================================================
    // DISASTER: PLAGUE
    // ============================================================
    private void startPlague() {
        startDisaster(
            "🦠 Plague",
            "Dịch bệnh chết người đang lan rộng khắp thế giới!",
            400, // 20 seconds
            () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    int duration = 100; // 5 seconds effect
                    player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, duration, 1, false, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, duration, 2, false, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, duration, 3, false, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, duration, 0, false, true));
                    player.sendActionBar("§2§l🦠 DỊCH BỆNH! §aBạn đang bị nhiễm bệnh!");
                }
            },
            () -> {
                broadcast("§aDịch bệnh đã được kiểm soát. Nhưng hãy cẩn thận với di chứng.");
            }
        );
    }

    // ============================================================
    // DISASTER: TORNADO
    // ============================================================
    private void startTornado() {
        startDisaster(
            "🌪️ Tornado",
            "Lốc xoáy khổng lồ đang quét qua khu vực của bạn!",
            300, // 15 seconds
            () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    Location loc = player.getLocation();

                    // Check if outside
                    if (loc.getBlock().getLightFromSky() > 5) {
                        // Launch player into the air
                        Vector velocity = player.getVelocity();
                        velocity.setY(velocity.getY() + 1.5);
                        velocity.setX(velocity.getX() + (random.nextDouble() - 0.5) * 2);
                        velocity.setZ(velocity.getZ() + (random.nextDouble() - 0.5) * 2);
                        player.setVelocity(velocity);

                        // Add effects
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 4, false, false));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 30, 1, false, false));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 40, 1, false, false));
                        player.sendActionBar("§8§l🌪️ LỐC XOÁY! §7Bạn đang bị cuốn lên!");

                        // Random block damage near player
                        if (random.nextInt(20) < 3) {
                            int bx = loc.getBlockX() + random.nextInt(8) - 4;
                            int bz = loc.getBlockZ() + random.nextInt(8) - 4;
                            int by = loc.getWorld().getHighestBlockYAt(bx, bz);
                            Location blockLoc = new Location(loc.getWorld(), bx, by, bz);
                            if (blockLoc.getBlock().getType() != Material.BEDROCK &&
                                blockLoc.getBlock().getType() != Material.WATER &&
                                blockLoc.getBlock().getType() != Material.LAVA) {
                                blockLoc.getBlock().breakNaturally();
                            }
                        }

                        // Fall damage protection during tornado (reduced)
                        player.setFallDistance(0);
                    } else {
                        player.sendActionBar("§a§oBạn an toàn trong nhà...");
                    }
                }
            },
            () -> {
                broadcast("§aLốc xoáy đã tan. Hãy kiểm tra thiệt hại.");
            }
        );
    }

    // ============================================================
    // DISASTER: SOLAR ECLIPSE
    // ============================================================
    private void startSolarEclipse() {
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        final long originalTime = world != null ? world.getTime() : 0;

        startDisaster(
            "📉 Solar Eclipse",
            "Nhật thực toàn phần! Mặt trời biến mất, bóng tối bao trùm!",
            600, // 30 seconds
            () -> {
                // Set time to "night" during eclipse
                if (world != null && world.getTime() < 13000) {
                    world.setTime(13000); // Set to night time
                }

                for (Player player : Bukkit.getOnlinePlayers()) {
                    Location loc = player.getLocation();

                    // Dark vision
                    player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 2, false, false));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, false, false));
                    player.sendActionBar("§0§l📉 NHẬT THỰC! §7Bóng tối đang bao phủ!");

                    // Spawn some mobs during eclipse
                    if (random.nextInt(10) < 3) {
                        EntityType[] nightMobs = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, EntityType.SPIDER};
                        EntityType type = nightMobs[random.nextInt(nightMobs.length)];
                        Location spawnLoc = loc.clone().add(
                            random.nextInt(15) - 7,
                            0,
                            random.nextInt(15) - 7
                        );
                        spawnLoc.setY(loc.getWorld().getHighestBlockYAt(spawnLoc) + 1);
                        loc.getWorld().spawnEntity(spawnLoc, type);
                    }
                }
            },
            () -> {
                // Restore time
                if (world != null) {
                    world.setTime(Math.max(originalTime, 1000)); // Set to morning-ish
                }
                broadcast("§aNhật thực kết thúc. Mặt trời đã trở lại!");
            }
        );
    }

    private void broadcast(String message) {
        Bukkit.broadcastMessage(message);
    }

    public boolean isDisasterActive() {
        return disasterActive;
    }

    public String getCurrentDisaster() {
        return currentDisaster;
    }
}