package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class DisasterManager {

    private final VnMineHardcore plugin;
    private final LogManager logManager;
    private final Random random = ThreadLocalRandom.current();
    private final ConfigManager config;

    private boolean disasterActive = false;
    private String currentDisaster = null;
    private BossBar warningBar;
    private int warningTimeLeft = 0;
    private int timeSinceLastDisaster = 0;

    private final Map<String, Runnable> disasterMap = new LinkedHashMap<>();

    public DisasterManager(VnMineHardcore plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.logManager = plugin.getLogManager();
        this.warningBar = BossBar.bossBar(
            Component.text(""), 1.0f, BossBar.Color.RED, BossBar.Overlay.PROGRESS
        );
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
        disasterMap.put("earthquake", this::startEarthquake);
        disasterMap.put("inferno", this::startInfernoStorm);
        disasterMap.put("souleruption", this::startSoulEruption);
        disasterMap.put("lavageyser", this::startLavaGeyser);
        disasterMap.put("endsurge", this::startEndSurge);
        disasterMap.put("voidstorm", this::startVoidStorm);
        disasterMap.put("chorusexplosion", this::startChorusExplosion);
    }

    public Set<String> getDisasterIds() { return disasterMap.keySet(); }

    public String getDisasterName(String id) {
        return switch (id.toLowerCase()) {
            case "bloodmoon" -> "🌕 Blood Moon";
            case "meteor" -> "☄️ Meteor Shower";
            case "storm" -> "🌊 Mega Storm";
            case "solarflare" -> "🔥 Solar Flare";
            case "plague" -> "🦠 Plague";
            case "tornado" -> "🌪️ Tornado";
            case "eclipse" -> "📉 Solar Eclipse";
            case "earthquake" -> "🌍 Earthquake";
            case "inferno" -> "🔥 Inferno Storm";
            case "souleruption" -> "💀 Soul Eruption";
            case "lavageyser" -> "🌋 Lava Geyser";
            case "endsurge" -> "👁️ End Surge";
            case "voidstorm" -> "🌌 Void Storm";
            case "chorusexplosion" -> "🌀 Chorus Explosion";
            default -> id;
        };
    }

    public boolean triggerDisaster(String disasterId, int warningTimeSeconds, int durationSeconds) {
        if (disasterActive) return false;
        Runnable task = disasterMap.get(disasterId.toLowerCase());
        if (task == null) return false;

        String name = getDisasterName(disasterId);
        currentDisaster = name;
        warningTimeLeft = warningTimeSeconds;

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(warningBar);
            p.sendTitle("§4§l⚠ CẢNH BÁO THIÊN TAI ⚠",
                "§c" + name + "\n§e§lSẽ xảy ra trong " + warningTimeSeconds + " giây!", 10, 70, 20);
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 0.5f);
            p.sendMessage("§4§l⚠ " + name + " §r§cđã được kích hoạt thủ công!");
        }
        logManager.logDisaster(name + " (MANUAL - Warning " + warningTimeSeconds + "s)");

        new BukkitRunnable() {
            int cd = warningTimeSeconds;
            @Override
            public void run() {
                cd--;
                if (cd <= 0) { this.cancel(); task.run(); return; }
                if (cd <= 5 || cd == 10 || cd == 30) {
                    broadcast("§4§l⚠ " + name + " §csẽ xảy ra trong §4§l" + cd + "§c giây!");
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
        return true;
    }

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (Bukkit.getWorlds().isEmpty()) return;
                timeSinceLastDisaster++;
                if (!disasterActive && timeSinceLastDisaster >= 1200) tryScheduleDisaster();
                if (warningTimeLeft > 0) {
                    warningTimeLeft--;
                    updateWarningBar();
                    if (warningTimeLeft <= 0) {
                        warningBar.name(Component.text(""));
                        warningBar.progress(0);
                        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(warningBar);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void stop() {
        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(warningBar);
    }

    private void tryScheduleDisaster() {
        int roll = random.nextInt(100);
        boolean isNight = Bukkit.getWorlds().get(0).getTime() > 13000;

        // Xác định dimension có player
        boolean hasNether = false, hasEnd = false, hasOverworld = false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            String env = p.getWorld().getEnvironment().name();
            if ("NETHER".equals(env)) hasNether = true;
            else if ("THE_END".equals(env)) hasEnd = true;
            else hasOverworld = true;
        }

        if (timeSinceLastDisaster < 1800) return;

        // Overworld events
        if (hasOverworld) {
            if (isNight && roll < 30) { timeSinceLastDisaster = 0; scheduleDisaster("🌕 Blood Moon", this::startBloodMoon); return; }
            if (roll < 5) { timeSinceLastDisaster = 0; scheduleDisaster("☄️ Meteor Shower", this::startMeteorShower); return; }
            if (roll < 10) { timeSinceLastDisaster = 0; scheduleDisaster("🌊 Mega Storm", this::startMegaStorm); return; }
            if (roll < 13) { timeSinceLastDisaster = 0; scheduleDisaster("🔥 Solar Flare", this::startSolarFlare); return; }
            if (roll < 15) { timeSinceLastDisaster = 0; scheduleDisaster("🦠 Plague", this::startPlague); return; }
            if (roll < 17) { timeSinceLastDisaster = 0; scheduleDisaster("🌪️ Tornado", this::startTornado); return; }
            if (roll < 18 && !isNight) { timeSinceLastDisaster = 0; scheduleDisaster("📉 Solar Eclipse", this::startSolarEclipse); return; }
            if (roll < 20) { timeSinceLastDisaster = 0; scheduleDisaster("🌍 Earthquake", this::startEarthquake); return; }
        }

        // Nether events
        if (hasNether) {
            if (roll < 23) { timeSinceLastDisaster = 0; scheduleDisaster("🔥 Inferno Storm", this::startInfernoStorm); return; }
            if (roll < 25) { timeSinceLastDisaster = 0; scheduleDisaster("💀 Soul Eruption", this::startSoulEruption); return; }
            if (roll < 27) { timeSinceLastDisaster = 0; scheduleDisaster("🌋 Lava Geyser", this::startLavaGeyser); return; }
        }

        // End events
        if (hasEnd) {
            if (roll < 29) { timeSinceLastDisaster = 0; scheduleDisaster("👁️ End Surge", this::startEndSurge); return; }
            if (roll < 31) { timeSinceLastDisaster = 0; scheduleDisaster("🌌 Void Storm", this::startVoidStorm); return; }
            if (roll < 32) { timeSinceLastDisaster = 0; scheduleDisaster("🌀 Chorus Explosion", this::startChorusExplosion); return; }
        }
    }

    private void scheduleDisaster(String name, Runnable disasterTask) {
        currentDisaster = name;
        warningTimeLeft = config.disasterWarningSeconds;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(warningBar);
            p.sendTitle("§4§l⚠ CẢNH BÁO THIÊN TAI ⚠",
                "§c" + name + "\n§e§lSẽ xảy ra trong " + (config.disasterWarningSeconds / 60) + " phút!", 10, 70, 20);
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 0.5f);
            p.sendMessage("§4§l⚠ " + name + " §r§cđang đến gần!");
        }
        logManager.logDisaster(name + " (WARNING)");

        new BukkitRunnable() {
            int cd = config.disasterWarningSeconds;
            @Override
            public void run() {
                cd--;
                if (cd <= 0) { this.cancel(); disasterTask.run(); return; }
                if (cd == 60 || cd == 30 || cd == 10 || cd <= 5) {
                    broadcast("§4§l⚠ " + currentDisaster + " §csẽ xảy ra trong §4§l" + cd + "§c giây!");
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void startDisaster(String name, String startMsg, int durationTicks, Runnable duringTask, Runnable endTask) {
        disasterActive = true;
        currentDisaster = name;
        warningBar.name(Component.text("§4§l⚠ " + name + " ĐANG HOẠT ĐỘNG ⚠"));
        warningBar.progress(1.0f);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(warningBar);
            p.sendTitle("§4§l" + name, "§c" + startMsg, 10, 60, 20);
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);
        }
        logManager.logDisaster(name + " (ACTIVE)");
        broadcast("§4§l" + name + " - " + startMsg + " (§e" + (durationTicks / 20) + "s§4)");

        // Lấy interval riêng cho disaster này
        String configKey = name.contains("Blood") ? "blood-moon" :
            name.contains("Meteor") ? "meteor" :
            name.contains("Mega") ? "mega-storm" :
            name.contains("Solar") ? "solar-flare" :
            name.contains("Plague") ? "plague" :
            name.contains("Tornado") ? "tornado" :
            name.contains("Eclipse") ? "eclipse" :
            name.contains("Earthquake") ? "earthquake" :
            name.contains("Inferno") ? "inferno-storm" :
            name.contains("Soul") ? "soul-eruption" :
            name.contains("Lava") ? "lava-geyser" :
            name.contains("End Surge") ? "end-surge" :
            name.contains("Void") ? "void-storm" :
            name.contains("Chorus") ? "chorus-explosion" : "blood-moon";

        int effectIntervalTicks = config.disasterEffectInterval.getOrDefault(configKey, 5) * 20;
        int effectDurationTicks = config.disasterEffectDuration.getOrDefault(configKey, 5) * 20;

        new BukkitRunnable() {
            int elapsed = 0;
            int tickCounter = 0;
            @Override
            public void run() {
                if (elapsed >= durationTicks) { this.cancel(); return; }
                warningBar.progress(1.0f - (float) elapsed / durationTicks);
                tickCounter++;
                if (tickCounter >= effectIntervalTicks / 20) {
                    tickCounter = 0;
                    duringTask.run();
                }
                elapsed += 20;
            }
        }.runTaskTimer(plugin, 0L, 20L);

        new BukkitRunnable() {
            @Override
            public void run() {
                disasterActive = false;
                currentDisaster = null;
                warningBar.name(Component.text(""));
                warningBar.progress(0);
                for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(warningBar);
                if (endTask != null) endTask.run();
                broadcast("§a§l✅ " + name + " đã kết thúc!");
            }
        }.runTaskLater(plugin, durationTicks);
    }

    private void updateWarningBar() {
        if (warningTimeLeft > 0) {
            warningBar.name(Component.text("§4§l⚠ " + currentDisaster + " §7- §e§l" + (warningTimeLeft / 60) + ":" + String.format("%02d", warningTimeLeft % 60)));
            warningBar.progress((float) warningTimeLeft / 300f);
        }
    }

    // ============ EXISTING DISASTERS ============

    private void startBloodMoon() {
        startDisaster("🌕 Blood Moon", "Máu trăng lên!", 600,
            () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (random.nextInt(10) < 3) {
                        Location loc = p.getLocation();
                        World w = p.getWorld();
                        for (int i = 0; i < 3; i++) {
                            Location sl = loc.clone().add(random.nextInt(20) - 10, 0, random.nextInt(20) - 10);
                            sl.setY(w.getHighestBlockYAt(sl) + 1);
                            EntityType[] mobs = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER};
                            Entity e = w.spawnEntity(sl, mobs[random.nextInt(mobs.length)]);
                            if (e instanceof Mob m) {
                                m.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 600, 1));
                                m.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 0));
                            }
                        }
                    }
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false));
                }
            }, () -> broadcast("§6Mặt trăng đã trở lại bình thường."));
    }

    private void startMeteorShower() {
        startDisaster("☄️ Meteor Shower", "Mưa sao băng!", 400,
            () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (random.nextInt(5) < 2) {
                        Location pl = p.getLocation();
                        World w = p.getWorld();
                        for (int i = 0; i < 2 + random.nextInt(3); i++) {
                            int x = pl.getBlockX() + random.nextInt(30) - 15;
                            int z = pl.getBlockZ() + random.nextInt(30) - 15;
                            Location il = new Location(w, x, w.getHighestBlockYAt(x, z), z);
                            w.strikeLightningEffect(new Location(w, x, pl.getBlockY() + 30, z));
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    w.createExplosion(il, 3.0f, false, true);
                                    for (Player pl2 : Bukkit.getOnlinePlayers()) {
                                        if (pl2.getLocation().distance(il) < 8) {
                                            double dmg = 8.0 * (1.0 - pl2.getLocation().distance(il) / 8.0);
                                            pl2.damage(dmg);
                                        }
                                    }
                                }
                            }.runTaskLater(plugin, 30L);
                        }
                    }
                }
            }, () -> broadcast("§aBầu trời đã trở lại bình thường."));
    }

    private void startMegaStorm() {
        for (World w : Bukkit.getWorlds()) { w.setStorm(true); w.setThundering(true); w.setWeatherDuration(200000); }
        startDisaster("🌊 Mega Storm", "Siêu bão đổ bộ!", 600,
            () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    Location l = p.getLocation();
                    if (random.nextInt(5) < 2) {
                        Location sl = l.clone().add(random.nextInt(10) - 5, 0, random.nextInt(10) - 5);
                        sl.setY(l.getWorld().getHighestBlockYAt(sl));
                        l.getWorld().strikeLightning(sl);
                        if (p.getLocation().distance(sl) < 4) p.damage(4.0);
                    }
                    if (l.getBlock().getLightFromSky() > 10 && random.nextInt(5) < 2) p.damage(1.0);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1));
                }
            }, () -> {
                for (World w : Bukkit.getWorlds()) { w.setStorm(false); w.setThundering(false); }
                broadcast("§aBão đã tan.");
            });
    }

    private void startSolarFlare() {
        startDisaster("🔥 Solar Flare", "Bão mặt trời!", 400,
            () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getLocation().getBlock().getLightFromSky() > 10) {
                        p.damage(2.0); p.setFireTicks(40);
                        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 2));
                    }
                }
            }, () -> broadcast("§aBão mặt trời đã qua."));
    }

    private void startPlague() {
        startDisaster("🦠 Plague", "Dịch bệnh lan rộng!", 400,
            () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 2));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 100, 3));
                }
            }, () -> broadcast("§aDịch bệnh đã được kiểm soát."));
    }

    private void startTornado() {
        startDisaster("🌪️ Tornado", "Lốc xoáy!", 300,
            () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getLocation().getBlock().getLightFromSky() > 5) {
                        Vector v = p.getVelocity();
                        v.setY(v.getY() + 1.5);
                        v.setX(v.getX() + (random.nextDouble() - 0.5) * 2);
                        v.setZ(v.getZ() + (random.nextDouble() - 0.5) * 2);
                        p.setVelocity(v);
                        p.setFallDistance(0);
                    }
                }
            }, () -> broadcast("§aLốc xoáy đã tan."));
    }

    private void startSolarEclipse() {
        World w = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        long ot = w != null ? w.getTime() : 0;
        startDisaster("📉 Solar Eclipse", "Nhật thực toàn phần!", 600,
            () -> {
                if (w != null && w.getTime() < 13000) w.setTime(13000);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 2));
                    if (random.nextInt(10) < 3) {
                        Location sl = p.getLocation().clone().add(random.nextInt(15) - 7, 0, random.nextInt(15) - 7);
                        sl.setY(p.getWorld().getHighestBlockYAt(sl) + 1);
                        EntityType[] mobs = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, EntityType.SPIDER};
                        p.getWorld().spawnEntity(sl, mobs[random.nextInt(mobs.length)]);
                    }
                }
            }, () -> { if (w != null) w.setTime(Math.max(ot, 1000)); broadcast("§aNhật thực kết thúc!"); });
    }

    private void startEarthquake() {
        startDisaster("🌍 Earthquake", "Động đất dữ dội!", 400,
            () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    Location l = p.getLocation();
                    p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 30, 0));
                    int radius = config.earthquakeRadius;
                    int minY = config.earthquakeMinY;
                    int chance = config.earthquakeBlockFallChance;
                    double resistanceFactor = config.earthquakeBlastResistanceFactor;

                    for (int i = 0; i < 5; i++) {
                        int bx = l.getBlockX() + random.nextInt(radius * 2) - radius;
                        int bz = l.getBlockZ() + random.nextInt(radius * 2) - radius;
                        int by = minY + random.nextInt(60);
                        Block block = new Location(p.getWorld(), bx, by, bz).getBlock();
                        Material bt = block.getType();
                        if (bt == Material.AIR || bt == Material.WATER || bt == Material.LAVA || bt == Material.BEDROCK) continue;

                        // Block có blast resistance càng cao càng khó rơi
                        double blastRes = bt.getBlastResistance();
                        double fallProb = chance / (1.0 + blastRes * resistanceFactor);
                        if (random.nextDouble() * 100 < fallProb) {
                            FallingBlock fb = p.getWorld().spawnFallingBlock(block.getLocation().add(0.5, 0, 0.5), block.getBlockData());
                            fb.setDropItem(true);
                            fb.setHurtEntities(true);
                            block.setType(Material.AIR);
                        }
                    }
                }
            }, () -> broadcast("§aĐộng đất đã kết thúc."));
    }

    // ============ NETHER DISASTERS ============

    private void startInfernoStorm() {
        startDisaster("🔥 Inferno Storm", "Lửa địa ngục trút xuống!", 400,
            () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.getWorld().getEnvironment().equals(World.Environment.NETHER)) continue;
                    Location l = p.getLocation();
                    p.damage(config.infernoStormDamage);
                    p.setFireTicks(config.infernoStormFireTicks);

                    if (random.nextInt(10) < 3) {
                        p.getWorld().spawnEntity(l, random.nextBoolean() ? EntityType.GHAST : EntityType.MAGMA_CUBE);
                    }

                    // Lửa rơi từ trần nether
                    for (int i = 0; i < 3; i++) {
                        int fx = l.getBlockX() + random.nextInt(20) - 10;
                        int fz = l.getBlockZ() + random.nextInt(20) - 10;
                        int fy = Math.min(l.getBlockY() + 20, p.getWorld().getMaxHeight() - 1);
                        Location fl = new Location(p.getWorld(), fx, fy, fz);
                        if (fl.getBlock().getType() == Material.AIR) {
                            fl.getBlock().setType(Material.FIRE);
                        }
                    }
                    p.sendActionBar("§4§l🔥 INFERNO! §cLửa địa ngục!");
                }
            }, () -> broadcast("§aInferno Storm đã kết thúc."));
    }

    private void startSoulEruption() {
        startDisaster("💀 Soul Eruption", "Soul sand phát nổ!", 400,
            () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.getWorld().getEnvironment().equals(World.Environment.NETHER)) continue;
                    p.damage(config.soulEruptionDamage);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, config.soulEruptionWitherAmplifier));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));

                    if (random.nextInt(10) < 3) {
                        Location sl = p.getLocation().clone().add(random.nextInt(10) - 5, 0, random.nextInt(10) - 5);
                        p.getWorld().spawnEntity(sl, EntityType.WITHER_SKELETON);
                    }

                    // Nổ soul sand gần player
                    if (random.nextInt(10) < 2) {
                        int ex = p.getLocation().getBlockX() + random.nextInt(8) - 4;
                        int ez = p.getLocation().getBlockZ() + random.nextInt(8) - 4;
                        Location el = new Location(p.getWorld(), ex, p.getLocation().getBlockY(), ez);
                        p.getWorld().createExplosion(el, 2.0f, false, true);
                    }
                    p.sendActionBar("§2§l💀 SOUL ERUPTION! §aLinh hồn đang gào thét!");
                }
            }, () -> broadcast("§aSoul Eruption đã kết thúc."));
    }

    private void startLavaGeyser() {
        startDisaster("🌋 Lava Geyser", "Lava phun trào!", 300,
            () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.getWorld().getEnvironment().equals(World.Environment.NETHER)) continue;
                    p.damage(config.lavaGeyserDamage);
                    p.setFireTicks(60);

                    // Cột lava từ dưới đất
                    if (random.nextInt(10) < 3) {
                        int gx = p.getLocation().getBlockX() + random.nextInt(10) - 5;
                        int gz = p.getLocation().getBlockZ() + random.nextInt(10) - 5;
                        int gy = p.getWorld().getHighestBlockYAt(gx, gz) - 5;
                        for (int dy = 0; dy < 5; dy++) {
                            Location gl = new Location(p.getWorld(), gx, gy + dy, gz);
                            if (gl.getBlock().getType() == Material.AIR) {
                                gl.getBlock().setType(Material.LAVA);
                            }
                        }
                    }
                    p.sendActionBar("§6§l🌋 LAVA GEYSER! §eTránh xa!");
                }
            }, () -> broadcast("§aLava Geyser đã kết thúc."));
    }

    // ============ THE END DISASTERS ============

    private void startEndSurge() {
        startDisaster("👁️ End Surge", "Sinh vật End trỗi dậy!", 400,
            () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.getWorld().getEnvironment().equals(World.Environment.THE_END)) continue;

                    if (random.nextInt(10) < 4) {
                        EntityType type = random.nextInt(100) < config.endSurgeShulkerChance ? EntityType.SHULKER : EntityType.ENDERMITE;
                        Location sl = p.getLocation().clone().add(random.nextInt(15) - 7, 0, random.nextInt(15) - 7);
                        p.getWorld().spawnEntity(sl, type);
                    }

                    if (random.nextInt(10) < 3) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 40, 1));
                    }
                    p.sendActionBar("§5§l👁️ END SURGE! §dSinh vật trỗi dậy!");
                }
            }, () -> broadcast("§aEnd Surge đã kết thúc."));
    }

    private void startVoidStorm() {
        startDisaster("🌌 Void Storm", "Bão hư vô!", 400,
            () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.getWorld().getEnvironment().equals(World.Environment.THE_END)) continue;

                    p.damage(config.voidStormDamage);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 1));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 2));
                    p.sendActionBar("§0§l🌌 VOID STORM! §8Hư vô đang nuốt chửng!");
                }
            }, () -> broadcast("§aVoid Storm đã kết thúc."));
    }

    private void startChorusExplosion() {
        startDisaster("🌀 Chorus Explosion", "Chorus phát nổ!", 300,
            () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.getWorld().getEnvironment().equals(World.Environment.THE_END)) continue;

                    p.damage(config.chorusExplosionDamage);

                    // Teleport ngẫu nhiên
                    if (random.nextInt(10) < 3) {
                        World w = p.getWorld();
                        int tx = p.getLocation().getBlockX() + random.nextInt(30) - 15;
                        int tz = p.getLocation().getBlockZ() + random.nextInt(30) - 15;
                        int ty = w.getHighestBlockYAt(tx, tz) + 1;
                        p.teleport(new Location(w, tx, ty, tz));
                        p.sendMessage("§d🌀 Bạn bị teleport!");
                    }

                    p.sendActionBar("§d§l🌀 CHORUS EXPLOSION!");
                }
            }, () -> broadcast("§aChorus Explosion đã kết thúc."));
    }

    private void broadcast(String msg) { Bukkit.broadcastMessage(msg); }
    public boolean isDisasterActive() { return disasterActive; }
    public String getCurrentDisaster() { return currentDisaster; }
}