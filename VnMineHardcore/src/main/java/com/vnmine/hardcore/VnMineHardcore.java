package com.vnmine.hardcore;

import com.vnmine.hardcore.listeners.*;
import com.vnmine.hardcore.managers.*;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class VnMineHardcore extends JavaPlugin {

    private static VnMineHardcore instance;
    private ConfigManager configManager;
    private BanManager banManager;
    private LogManager logManager;
    private ThirstManager thirstManager;
    private DisasterManager disasterManager;
    private DeathRenameManager deathRenameManager;
    private DeathPenaltyManager deathPenaltyManager;
    private BossEventManager bossEventManager;
    private DeathListener deathListener;
    private CombatListener combatListener;
    private EnvironmentListener environmentListener;
    private HungerListener hungerListener;
    private WaterListener waterListener;
    private WorldInteractionListener worldInteractionListener;
    private SpawnerControlListener spawnerControlListener;
    private VillagerTradeManager villagerTradeManager;
    private OreControlListener oreControlListener;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("§a==========================================");
        getLogger().info("§a  VnMineHardcore v1.0.0 - KHỞI ĐỘNG");
        getLogger().info("§a==========================================");

        // Load config first
        this.configManager = new ConfigManager(this);

        // Initialize managers (pass config)
        this.banManager = new BanManager(this, configManager);
        this.logManager = new LogManager(this);
        this.thirstManager = new ThirstManager(this, configManager);
        this.disasterManager = new DisasterManager(this, configManager);
        this.deathRenameManager = new DeathRenameManager(this, configManager);
        this.deathPenaltyManager = new DeathPenaltyManager(this, configManager);
        this.bossEventManager = new BossEventManager(this, configManager);

        // Create listeners (pass config)
        this.deathListener = new DeathListener(this, configManager, deathRenameManager, deathPenaltyManager);
        this.combatListener = new CombatListener(this, deathListener, configManager);
        this.environmentListener = new EnvironmentListener(this, configManager);
        this.hungerListener = new HungerListener(this, configManager);
        this.waterListener = new WaterListener(this);
        this.worldInteractionListener = new WorldInteractionListener(this);
        this.spawnerControlListener = new SpawnerControlListener(this, configManager);
        this.villagerTradeManager = new VillagerTradeManager(this, configManager);
        this.oreControlListener = new OreControlListener(this, configManager);

        // Register listeners
        Bukkit.getPluginManager().registerEvents(deathListener, this);
        Bukkit.getPluginManager().registerEvents(hungerListener, this);
        Bukkit.getPluginManager().registerEvents(combatListener, this);
        Bukkit.getPluginManager().registerEvents(environmentListener, this);
        Bukkit.getPluginManager().registerEvents(waterListener, this);
        Bukkit.getPluginManager().registerEvents(worldInteractionListener, this);
        Bukkit.getPluginManager().registerEvents(spawnerControlListener, this);
        Bukkit.getPluginManager().registerEvents(villagerTradeManager, this);
        Bukkit.getPluginManager().registerEvents(oreControlListener, this);

        // Register commands
        this.getCommand("vnstats").setExecutor(this);
        this.getCommand("vnhardcore").setExecutor(this);
        this.getCommand("vnreload").setExecutor(this);
        this.getCommand("vnhelp").setExecutor(this);
        this.getCommand("vnevent").setExecutor(this);
        this.getCommand("vn").setExecutor(this);

        getLogger().info("§a  VnMineHardcore enabled - Hell difficulty!");
        getLogger().info("§c  Death = Permanent Ban!");
        getLogger().info("§a  Use /vnhelp for command list");
        getLogger().info("§a  Use /vnreload to reload config");
        getLogger().info("§a  Use /vnevent to manually trigger disasters");
        getLogger().info("§a==========================================");
    }

    @Override
    public void onDisable() {
        if (thirstManager != null) thirstManager.stop();
        if (disasterManager != null) disasterManager.stop();
        if (bossEventManager != null) bossEventManager.stop();
        getLogger().info("VnMineHardcore disabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("vnhelp")) {
            sender.sendMessage("§6§m========================================");
            sender.sendMessage("§6§l  VnMineHardcore - Command Help");
            sender.sendMessage("§6§m========================================");
            sender.sendMessage("§e/vnstats §7- Xem thống kê của bạn");
            sender.sendMessage("§e/vnhardcore §7- Xem trạng thái plugin");
            sender.sendMessage("§e/vnhardcore unban <player> §7- Bỏ ban player");
            sender.sendMessage("§e/vn death reset <player> §7- Reset số lần chết về 0");
            sender.sendMessage("§e/vnevent §7- Gọi thiên tai thủ công (xem danh sách)");
            sender.sendMessage("§e/vnevent <id> <time> <duration> §7- Gọi thiên tai cụ thể");
            sender.sendMessage("§e/vnreload §7- Tải lại config.yml");
            sender.sendMessage("§e/vnhelp §7- Hiển thị help này");
            sender.sendMessage("§6§m========================================");
            return true;
        }

        if (command.getName().equalsIgnoreCase("vnreload")) {
            if (!sender.hasPermission("vnmine.hardcore.admin")) {
                sender.sendMessage("§cBạn không có quyền!");
                return true;
            }
            configManager.reload();
            hungerListener.reload();
            environmentListener.reload();
            thirstManager.reload();
            deathListener.reload();
            sender.sendMessage("§a✅ Config + all tasks reloaded!");
            getLogger().info("[Cmd] " + sender.getName() + " reloaded config");
            return true;
        }

        if (command.getName().equalsIgnoreCase("vnevent")) {
            if (!sender.hasPermission("vnmine.hardcore.admin")) {
                sender.sendMessage("§cBạn không có quyền!");
                return true;
            }
            if (args.length == 0) {
                sender.sendMessage("§6§m=======================================");
                sender.sendMessage("§6§l  /vnevent - Gọi thiên tai thủ công");
                sender.sendMessage("§6§m=======================================");
                sender.sendMessage("§eUsage: §7/vnevent <id> <warning(s)> <duration(s)>");
                sender.sendMessage("§e  /vnevent §7- Hiển thị danh sách này");
                sender.sendMessage("");
                sender.sendMessage("§eDanh sách EVENT_ID có thể dùng:");
                for (String id : disasterManager.getDisasterIds()) {
                    String name = disasterManager.getDisasterName(id);
                    sender.sendMessage("§c  " + id + "  §7- " + name);
                }
                sender.sendMessage("");
                sender.sendMessage("§eVí dụ: §7/vnevent bloodmoon 30 60");
                sender.sendMessage("§6§m=======================================");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage("§cSử dụng: /vnevent <id> <warning(s)> <duration(s)>");
                return true;
            }
            String eventId = args[0].toLowerCase();
            int warningTime, duration;
            try {
                warningTime = Integer.parseInt(args[1]);
                duration = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cThời gian phải là số nguyên (giây)!");
                return true;
            }
            if (warningTime < 1 || duration < 1) {
                sender.sendMessage("§cThời gian phải >= 1 giây!");
                return true;
            }
            boolean success = disasterManager.triggerDisaster(eventId, warningTime, duration);
            if (success) {
                sender.sendMessage("§a✅ Đã kích hoạt §e" + disasterManager.getDisasterName(eventId));
            } else {
                if (disasterManager.isDisasterActive()) {
                    sender.sendMessage("§c❌ Đã có thiên tai đang hoạt động: " + disasterManager.getCurrentDisaster());
                } else {
                    sender.sendMessage("§c❌ ID '" + eventId + "' không hợp lệ!");
                }
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("vnboss")) {
            if (!sender.hasPermission("vnmine.hardcore.admin")) {
                sender.sendMessage("§cBạn không có quyền!");
                return true;
            }
            if (args.length == 0) {
                sender.sendMessage("§6§m=======================================");
                sender.sendMessage("§6§l  /vnboss - Gọi boss thủ công");
                sender.sendMessage("§6§m=======================================");
                sender.sendMessage("§eUsage: §7/vnboss <id> <warning(s)> <duration(s)>");
                sender.sendMessage("§e  /vnboss §7- Hiển thị danh sách này");
                sender.sendMessage("");
                sender.sendMessage("§eDanh sách BOSS_ID có thể dùng:");
                for (String id : bossEventManager.getBossIds()) {
                    sender.sendMessage("§c  " + id + "  §7- " + bossEventManager.getBossName(id));
                }
                sender.sendMessage("");
                sender.sendMessage("§eVí dụ: §7/vnboss wither 30 120");
                sender.sendMessage("§6§m=======================================");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage("§cSử dụng: /vnboss <id> <warning(s)> <duration(s)>");
                return true;
            }
            String bossId = args[0].toLowerCase();
            int warningTime, duration;
            try {
                warningTime = Integer.parseInt(args[1]);
                duration = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cThời gian phải là số nguyên (giây)!");
                return true;
            }
            if (warningTime < 1 || duration < 1) {
                sender.sendMessage("§cThời gian phải >= 1 giây!");
                return true;
            }
            boolean success = bossEventManager.triggerBoss(bossId, warningTime, duration);
            if (success) {
                sender.sendMessage("§a✅ Đã kích hoạt boss §e" + bossEventManager.getBossName(bossId));
            } else {
                if (bossEventManager.isBossActive()) {
                    sender.sendMessage("§c❌ Đã có boss đang hoạt động: " + bossEventManager.getCurrentBossName());
                } else {
                    sender.sendMessage("§c❌ ID boss '" + bossId + "' không hợp lệ hoặc đã bị tắt!");
                }
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("vnstats")) {
            if (sender instanceof Player player) {
                sender.sendMessage("§6=== VnMineHardcore Stats ===");
                sender.sendMessage("§eDeaths: §c" + logManager.getDeathCount(player));
                sender.sendMessage("§eSurvival Time: §a" + logManager.getSurvivalTime(player));
                sender.sendMessage("§eMob Kills: §b" + logManager.getMobKills(player));
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("vnhardcore")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("unban")) {
                if (!sender.hasPermission("vnmine.hardcore.admin")) {
                    sender.sendMessage("§cBạn không có quyền!");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§cSử dụng: /vnhardcore unban <player>");
                    return true;
                }
                banManager.unbanPlayer(args[1]);
                sender.sendMessage("§a✅ Đã bỏ ban cho §e" + args[1]);
                return true;
            }
            sender.sendMessage("§6§m========================================");
            sender.sendMessage("§6§l  VnMineHardcore - Admin Status");
            sender.sendMessage("§6§m========================================");
            sender.sendMessage("§c☠ DEATH: Ban=" + (configManager.banOnDeath ? "§aON" : "§cOFF") +
                " | Tag=§aON (" + (configManager.combatTagDurationMs / 1000) + "s)");
            sender.sendMessage("§e🍔 HUNGER: " + (configManager.hungerEnabled ? "§aON" : "§cOFF"));
            sender.sendMessage("§b💧 THIRST: " + (configManager.thirstEnabled ? "§aON" : "§cOFF") +
                " | Source=" + (configManager.drinkFromSource ? "§aON" : "§cOFF"));
            sender.sendMessage("§c⚔ COMBAT: Mob=x" + configManager.mobDamageMultiplier +
                " Fall=x" + configManager.fallDamageMultiplier +
                " Regen=" + (!configManager.disableNaturalRegen ? "§aON" : "§cOFF"));
            sender.sendMessage("§a🌍 ENV: Temp=" + (configManager.temperatureEnabled ? "§aON" : "§cOFF") +
                " Fog=" + (configManager.fogEnabled ? "§aON" : "§cOFF") +
                " Fly=" + (configManager.flightBlock ? "§cBLOCK" : "§aOK"));
            sender.sendMessage("§4🌋 DISASTERS: " + (configManager.disastersEnabled ? "§aON" : "§cOFF") +
                (disasterManager.isDisasterActive() ? " §c⚠ ACTIVE: " + disasterManager.getCurrentDisaster() : ""));
            sender.sendMessage("§c👹 BOSS: " + (configManager.bossEventsEnabled ? "§aON" : "§cOFF") +
                (bossEventManager.isBossActive() ? " §c⚠ ACTIVE: " + bossEventManager.getCurrentBossName() : ""));
            sender.sendMessage("§e🏷 RENAME: " + (configManager.renameEnabled ? "§aON" : "§cOFF"));
            sender.sendMessage("§eCommands: §7/vnhelp | /vnreload | /vnevent | /vnhardcore unban <player> | /vn death reset <player>");
            sender.sendMessage("§6§m========================================");
            return true;
        }

        if (command.getName().equalsIgnoreCase("vn")) {
            if (args.length == 0) {
                sender.sendMessage("§cSử dụng: /vn <subcommand>");
                sender.sendMessage("§cGõ /vnhelp để xem hướng dẫn.");
                return true;
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("death") && args[1].equalsIgnoreCase("reset")) {
                if (!sender.hasPermission("vnmine.hardcore.admin")) {
                    sender.sendMessage("§cBạn không có quyền!");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§cSử dụng: /vn death reset <player>");
                    return true;
                }
                String targetName = args[2];
                Player target = Bukkit.getPlayerExact(targetName);
                if (target != null && target.isOnline()) {
                    logManager.resetDeathCount(target.getUniqueId());
                    deathRenameManager.updateDisplayName(target, 0);
                    sender.sendMessage("§a✅ Đã reset số lần chết của §e" + target.getName());
                } else {
                    @SuppressWarnings("deprecation")
                    org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetName);
                    if (offlinePlayer.hasPlayedBefore() || offlinePlayer.isOnline()) {
                        logManager.resetDeathCount(offlinePlayer.getUniqueId());
                        sender.sendMessage("§a✅ Đã reset số lần chết của §e" + offlinePlayer.getName());
                    } else {
                        sender.sendMessage("§c❌ Không tìm thấy người chơi §e" + targetName);
                    }
                }
                return true;
            }
            sender.sendMessage("§cLệnh phụ không hợp lệ. Gõ /vnhelp để xem hướng dẫn.");
            return true;
        }
        return false;
    }

    public static VnMineHardcore getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public BanManager getBanManager() { return banManager; }
    public LogManager getLogManager() { return logManager; }
    public ThirstManager getThirstManager() { return thirstManager; }
    public DisasterManager getDisasterManager() { return disasterManager; }
    public DeathPenaltyManager getDeathPenaltyManager() { return deathPenaltyManager; }
    public BossEventManager getBossEventManager() { return bossEventManager; }
}