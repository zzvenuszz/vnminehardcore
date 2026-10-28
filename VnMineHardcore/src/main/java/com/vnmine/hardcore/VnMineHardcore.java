package com.vnmine.hardcore;

import com.vnmine.hardcore.listeners.*;
import com.vnmine.hardcore.managers.*;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public final class VnMineHardcore extends JavaPlugin {

    private static VnMineHardcore instance;
    private ConfigManager configManager;
    private PlayerProfileDatabaseManager playerProfileDatabaseManager;
    private EconomyManager economyManager;
    private BukkitTask profileDatabaseBackupTask;
    private final Set<String> registeredCommands = new HashSet<>();
    private CharacterSelectionManager characterSelectionManager;
    private RpgWorldManager rpgWorldManager;
    private TutorialQuestManager tutorialQuestManager;
    private QuestCatalogManager questCatalogManager;
    private QuestDialogManager questDialogManager;
    private Map<String, NpcDefinitionLoader.NpcDefinition> npcDefinitions = Map.of();
    private NpcManager npcManager;
    private NpcMarkerManager npcMarkerManager;
    private TextDisplayManager textDisplayManager;
    private SkillManager skillManager;
    private LogManager logManager;
    private ThirstManager thirstManager;
    private DisasterManager disasterManager;
    private DeathRenameManager deathRenameManager;
    private DeathPenaltyManager deathPenaltyManager;
    private BossEventManager bossEventManager;
    private BossLeaderboardManager bossLeaderboardManager;
    private GraveManager graveManager;
    private DeathHistoryManager deathHistoryManager;
    private SpawnProtectionManager spawnProtectionManager;
    private PermissionManager permissionManager;
    private PortalManager portalManager;
    private MercenaryManager mercenaryManager;
    private PlayerAdminManager playerAdminManager;
    private ServerSetupWizardManager serverSetupWizardManager;
    private TooltipManager tooltipManager;
    private VillagerDataManager villagerDataManager;
    private DeathListener deathListener;
    private CombatListener combatListener;
    private EnvironmentListener environmentListener;
    private HungerListener hungerListener;
    private WaterListener waterListener;
    private WorldInteractionListener worldInteractionListener;
    private SpawnerControlListener spawnerControlListener;
    private VillagerTradeManager villagerTradeManager;
    private OreControlListener oreControlListener;
    private TimeControlManager timeControlManager;
    private GraveListener graveListener;

    @Override
    public void onEnable() {
        var protocolLib = Bukkit.getPluginManager().getPlugin("ProtocolLib");
        getLogger().info("[Compatibility] java=" + System.getProperty("java.version")
            + ", server=" + Bukkit.getVersion() + ", bukkit=" + Bukkit.getBukkitVersion()
            + ", protocolLib=" + (protocolLib == null ? "missing"
                : protocolLib.getDescription().getVersion()));
        if (protocolLib == null) {
            getLogger().severe("ProtocolLib is required for VnMineHardcore.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        String protocolLibVersion = protocolLib.getDescription().getVersion();
        if (!protocolLibVersion.startsWith("5.5.0-SNAPSHOT")) {
            getLogger().severe("Unsupported ProtocolLib version: " + protocolLibVersion
                + ". VnMineHardcore requires ProtocolLib 5.5.0-SNAPSHOT.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        instance = this;
        clearLegacyPluginBans();

        getLogger().info("§a==========================================");
        getLogger().info("§a  VnMineHardcore v" + getDescription().getVersion() + " - KHỞI ĐỘNG");
        getLogger().info("§a==========================================");

        // Load config first
        this.configManager = new ConfigManager(this);
        this.playerProfileDatabaseManager = new PlayerProfileDatabaseManager(getDataFolder(),
            configManager.profileDatabaseBackupRetentionCount,
            configManager.profileDatabaseWriteRetries,
            configManager.profileDatabaseWriteRetryDelayMillis);
        this.playerProfileDatabaseManager.configureGameplayDatabaseDiagnostics(
            getLogger(), () -> configManager.consoleDebug);
        this.economyManager = new EconomyManager(this, playerProfileDatabaseManager);
        configureCharacterProgression();
        try {
            this.playerProfileDatabaseManager.initialize();
            if (this.playerProfileDatabaseManager.getRecoveryMessage() != null) {
                getLogger().warning(this.playerProfileDatabaseManager.getRecoveryMessage());
            }
        } catch (Exception exception) {
            getLogger().log(java.util.logging.Level.SEVERE, "Failed to initialize player profile database.", exception);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        this.characterSelectionManager = new CharacterSelectionManager(this);
        this.skillManager = new SkillManager(this, playerProfileDatabaseManager, configManager);
        try {
            skillManager.initialize(characterSelectionManager.getClassIds());
            characterSelectionManager.setPlayableClassIds(skillManager.getPlayableClassIds());
        } catch (java.io.IOException exception) {
            getLogger().log(java.util.logging.Level.SEVERE, "Failed to initialize RPG skill definitions.", exception);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        this.rpgWorldManager = new RpgWorldManager(this);
        this.tutorialQuestManager = new TutorialQuestManager(this);
        try {
            this.questDialogManager = new QuestDialogManager(this);
        } catch (java.io.IOException exception) {
            getLogger().log(Level.SEVERE, "Failed to initialize quest dialog catalog.", exception);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        try {
            this.questCatalogManager = new QuestCatalogManager(this);
        } catch (java.io.IOException exception) {
            getLogger().log(Level.SEVERE, "Failed to initialize quest catalog.", exception);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        try {
            java.io.File npcDirectory = new java.io.File(getDataFolder(), "npcs");
            if (!npcDirectory.isDirectory() && !npcDirectory.mkdirs()) {
                throw new java.io.IOException("Could not create NPC data directory.");
            }
            java.io.File npcDefinitionsFile = new java.io.File(npcDirectory, "npc-definitions.yml");
            if (!npcDefinitionsFile.isFile()) {
                try {
                    saveResource("npcs/npc-definitions.yml", false);
                } catch (IllegalArgumentException exception) {
                    throw new java.io.IOException("Bundled NPC definitions are missing.", exception);
                }
            }
            this.npcDefinitions = NpcDefinitionLoader.load(npcDefinitionsFile, getLogger()).orElseThrow(
                () -> new java.io.IOException("NPC definitions are invalid; see the log for details."));
        } catch (java.io.IOException exception) {
            getLogger().log(Level.SEVERE, "Failed to initialize NPC definitions.", exception);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        this.timeControlManager = new TimeControlManager(this, configManager);
        this.timeControlManager.reload();

        // Initialize managers (pass config)
        this.logManager = new LogManager(this, configManager);
        this.deathHistoryManager = new DeathHistoryManager(this);
        this.bossLeaderboardManager = new BossLeaderboardManager(this);
        this.deathPenaltyManager = new DeathPenaltyManager(this, configManager);
        this.thirstManager = new ThirstManager(this, configManager);
        this.disasterManager = new DisasterManager(this, configManager);
        this.deathRenameManager = new DeathRenameManager(this, configManager);
        this.bossEventManager = new BossEventManager(this, configManager);
        this.villagerDataManager = new VillagerDataManager(this, configManager);
        this.graveManager = new GraveManager(this, configManager);
        this.spawnProtectionManager = new SpawnProtectionManager(this);
        this.permissionManager = new PermissionManager(this);
        try {
            this.portalManager = new PortalManager(this);
        } catch (java.io.IOException | InvalidConfigurationException | YAMLException exception) {
            getLogger().log(Level.SEVERE, "Failed to load portal definitions.", exception);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        this.mercenaryManager = new MercenaryManager(this);
        this.playerAdminManager = new PlayerAdminManager(this);
        this.serverSetupWizardManager = new ServerSetupWizardManager(this);
        this.tooltipManager = new TooltipManager(this);

        // Create listeners (pass config)
        Bukkit.getPluginManager().registerEvents(characterSelectionManager, this);
        Bukkit.getPluginManager().registerEvents(rpgWorldManager, this);
        Bukkit.getPluginManager().registerEvents(tutorialQuestManager, this);
        Bukkit.getPluginManager().registerEvents(questDialogManager, this);
        Bukkit.getPluginManager().registerEvents(questCatalogManager, this);
        Bukkit.getPluginManager().registerEvents(skillManager, this);
        Bukkit.getPluginManager().registerEvents(economyManager, this);
        Bukkit.getPluginManager().registerEvents(playerAdminManager, this);
        Bukkit.getPluginManager().registerEvents(serverSetupWizardManager, this);
        this.deathListener = new DeathListener(this, configManager, deathRenameManager, deathPenaltyManager);
        this.combatListener = new CombatListener(this, deathListener, configManager);
        this.environmentListener = new EnvironmentListener(this, configManager);
        this.hungerListener = new HungerListener(this, configManager);
        this.waterListener = new WaterListener(this);
        this.worldInteractionListener = new WorldInteractionListener(this);
        this.spawnerControlListener = new SpawnerControlListener(this, configManager);
        this.villagerTradeManager = new VillagerTradeManager(this, configManager, villagerDataManager);
        this.oreControlListener = new OreControlListener(this, configManager);
        this.graveListener = new GraveListener(this, graveManager);
        try {
            this.npcManager = new NpcManager(this, npcDefinitions);
        } catch (java.io.IOException | InvalidConfigurationException | YAMLException exception) {
            getLogger().log(Level.SEVERE, "Failed to initialize NPC runtime data.", exception);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        economyManager.refreshAllGroundItemNames();
        Bukkit.getPluginManager().registerEvents(npcManager, this);
        this.npcMarkerManager = new NpcMarkerManager(this);
        try {
            this.textDisplayManager = new TextDisplayManager(this);
        } catch (java.io.IOException exception) {
            getLogger().log(Level.SEVERE, "Failed to initialize managed text displays.", exception);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        Bukkit.getPluginManager().registerEvents(textDisplayManager, this);

        // Register listeners
        Bukkit.getPluginManager().registerEvents(deathListener, this);
        Bukkit.getPluginManager().registerEvents(deathHistoryManager, this);
        Bukkit.getPluginManager().registerEvents(hungerListener, this);
        Bukkit.getPluginManager().registerEvents(combatListener, this);
        Bukkit.getPluginManager().registerEvents(environmentListener, this);
        Bukkit.getPluginManager().registerEvents(waterListener, this);
        Bukkit.getPluginManager().registerEvents(worldInteractionListener, this);
        Bukkit.getPluginManager().registerEvents(spawnerControlListener, this);
        Bukkit.getPluginManager().registerEvents(villagerTradeManager, this);
        Bukkit.getPluginManager().registerEvents(oreControlListener, this);
        Bukkit.getPluginManager().registerEvents(graveListener, this);
        Bukkit.getPluginManager().registerEvents(spawnProtectionManager, this);
        Bukkit.getPluginManager().registerEvents(permissionManager, this);
        Bukkit.getPluginManager().registerEvents(portalManager, this);

        // Register commands
        for (String commandName : List.of("vnstats", "vnhardcore", "vnreload", "vnhelp",
                "vnevent", "vnboss", "vn", "reward", "vnrespawn", "vnleave", "fly",
                "nodame", "spawn", "gm0", "gm1", "gm2", "gm3", "scoreboard",
                "vntime", "vnday", "vnspawn", "tp")) {
            registerCommand(commandName, this, this);
        }
        registerCommand("vnskills", skillManager, skillManager);
        registerCommand("vnskillbook", skillManager, skillManager);
        registerCommand("vnnpc", npcManager, npcManager);
        registerCommand("vntext", textDisplayManager, textDisplayManager);
        registerCommand("vnquest", questCatalogManager, questCatalogManager);
        registerCommand("vnquestdialog", questDialogManager, questDialogManager);
        registerCommand("vngroup", permissionManager, permissionManager);
        registerCommand("vnuser", permissionManager, permissionManager);
        registerCommand("vnarmy", mercenaryManager, mercenaryManager);
        registerCommand("vnmenu", mercenaryManager, mercenaryManager);
        registerCommand("quatanthu", mercenaryManager, mercenaryManager);
        registerCommand("vndeathhistory", deathHistoryManager, deathHistoryManager);
        registerCommand("vnportal", portalManager, portalManager);
        registerCommand("vncoin", economyManager, economyManager);
        registerCommand("vnplayeradmin", (sender, command, label, args) -> {
            if (sender instanceof Player player) return playerAdminManager.open(player);
            sender.sendMessage("Lệnh này chỉ dùng trong game.");
            return true;
        }, (sender, command, alias, args) -> List.of());
        registerCommand("vninit", serverSetupWizardManager, serverSetupWizardManager);
        registerCommand("start", (sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Lệnh /start chỉ dùng trong game.");
                return true;
            }
            characterSelectionManager.startJourney(player);
            return true;
        }, (sender, command, alias, args) -> List.of());
        startProfileDatabaseBackupTask();

        getLogger().info("§a  VnMineHardcore enabled - Hell difficulty!");
        getLogger().info("§a  Deaths are recorded; no death bans are applied.");
        getLogger().info("§a  Use /vnhelp for command list");
        getLogger().info("§a  Use /vnreload to reload config");
        getLogger().info("§a  Use /vnevent to manually trigger disasters");
        getLogger().info("§a==========================================");
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor,
                                 TabCompleter tabCompleter) {
        String normalizedName = name.toLowerCase(Locale.ROOT);
        if (!registeredCommands.add(normalizedName)) {
            throw new IllegalStateException("Command '/" + normalizedName + "' was registered more than once.");
        }
        PluginCommand pluginCommand = getCommand(name);
        if (pluginCommand == null) {
            throw new IllegalStateException("Command '/" + name + "' is missing from plugin.yml.");
        }
        pluginCommand.setExecutor(executor);
        pluginCommand.setTabCompleter(tabCompleter);
        if (configManager.consoleDebug) {
            getLogger().info("[RPG-DEBUG][CommandRegistry] command=/" + name
                + ", executor=" + executor.getClass().getSimpleName()
                + ", tabCompleter=" + tabCompleter.getClass().getSimpleName());
        }
    }

    private void reloadProfileDatabaseMaintenance() {
        playerProfileDatabaseManager.configureMaintenance(
            configManager.profileDatabaseBackupRetentionCount,
            configManager.profileDatabaseWriteRetries,
            configManager.profileDatabaseWriteRetryDelayMillis
        );
        configureCharacterProgression();
        startProfileDatabaseBackupTask();
    }

    private void configureCharacterProgression() {
        playerProfileDatabaseManager.configureCharacterProgression(
            configManager.characterProgressionMaxLevel,
            configManager.characterProgressionBaseExperience,
            configManager.characterProgressionExperiencePerLevel,
            configManager.characterProgressionSkillPointsPerLevel
        );
    }

    private void startProfileDatabaseBackupTask() {
        if (profileDatabaseBackupTask != null) {
            profileDatabaseBackupTask.cancel();
        }
        long intervalTicks = configManager.profileDatabaseBackupIntervalHours * 60L * 60L * 20L;
        profileDatabaseBackupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                playerProfileDatabaseManager.backupDatabase();
            } catch (java.io.IOException | java.sql.SQLException exception) {
                getLogger().log(Level.SEVERE, "Scheduled player profile database backup failed.", exception);
            }
        }, intervalTicks, intervalTicks);
    }

    @Override
    public void onDisable() {
        if (profileDatabaseBackupTask != null) profileDatabaseBackupTask.cancel();
        if (characterSelectionManager != null) characterSelectionManager.stopOnboardingNavigation();
        if (portalManager != null) portalManager.stop();
        if (thirstManager != null) thirstManager.stop();
        if (deathPenaltyManager != null) deathPenaltyManager.stop();
        if (disasterManager != null) disasterManager.stop();
        if (bossEventManager != null) bossEventManager.stop();
        if (bossLeaderboardManager != null) bossLeaderboardManager.stop();
        if (graveManager != null) graveManager.stop();
        if (mercenaryManager != null) mercenaryManager.stop();
        if (npcMarkerManager != null) npcMarkerManager.close();
        if (tooltipManager != null) tooltipManager.stop();
        if (logManager != null) logManager.stop();
        if (timeControlManager != null) timeControlManager.stop();
        if (economyManager != null) economyManager.close();
        if (playerProfileDatabaseManager != null) {
            playerProfileDatabaseManager.shutdownGameplayDatabaseExecutor();
        }
        getLogger().info("VnMineHardcore disabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (configManager != null && configManager.consoleDebug) {
            getLogger().info("[RPG-DEBUG][Command] command=/" + command.getName() + ", sender="
                + sender.getName() + ", args=" + String.join(" ", args));
        }
        if (command.getName().equalsIgnoreCase("tp")) {
            return handleTeleportToWorldSpawn(sender, args);
        }

        if (command.getName().equalsIgnoreCase("vnspawn")) {
            return handleVnSpawn(sender, args);
        }

        if (command.getName().equalsIgnoreCase("scoreboard")) {
            if (!permissionManager.hasPermission(sender, "vnmine.command.scoreboard.reset")) {
                sender.sendMessage("§cBạn không có quyền!");
                return true;
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("reset")) {
                if (bossLeaderboardManager.resetAllData()) {
                    sender.sendMessage("§aĐã xóa dữ liệu scoreboard và phần thưởng đang chờ nhận.");
                } else {
                    sender.sendMessage("§cKhông thể lưu dữ liệu scoreboard; dữ liệu cũ đã được giữ lại.");
                }
                return true;
            }
            sender.sendMessage("§eSử dụng: /scoreboard reset");
            return true;
        }

        if (command.getName().equalsIgnoreCase("vntime")) {
            if (!permissionManager.hasPermission(sender, "vnmine.command.time.manage")) {
                sender.sendMessage("§cBạn không có quyền!");
                return true;
            }
            if ((args.length != 3 && args.length != 4) || !args[0].equalsIgnoreCase("set")
                    || !(args[1].equalsIgnoreCase("day") || args[1].equalsIgnoreCase("night"))) {
                sender.sendMessage("§eSử dụng: /vntime set <day|night> <phút> [world]");
                return true;
            }

            int minutes;
            try {
                minutes = Integer.parseInt(args[2]);
            } catch (NumberFormatException exception) {
                sender.sendMessage("§cThời lượng phải là số nguyên phút lớn hơn 0.");
                return true;
            }
            if (minutes < 1) {
                sender.sendMessage("§cThời lượng phải ít nhất 1 phút.");
                return true;
            }

            boolean isDay = args[1].equalsIgnoreCase("day");
            String worldName = args.length == 4 ? args[3] : null;
            if (worldName != null && Bukkit.getWorld(worldName) == null) {
                sender.sendMessage("§cKhông tìm thấy world đang tải: §e" + worldName);
                return true;
            }
            boolean saved = worldName == null
                ? (isDay
                    ? configManager.setGlobalDayDurationMinutes(minutes)
                    : configManager.setGlobalNightDurationMinutes(minutes))
                : (isDay
                    ? configManager.setWorldDayDurationMinutes(worldName, minutes)
                    : configManager.setWorldNightDurationMinutes(worldName, minutes));
            if (!saved) {
                sender.sendMessage("§cKhông thể lưu thời lượng vào config.yml; cấu hình hiện tại không đổi.");
                return true;
            }
            String period = isDay ? "ban ngày" : "ban đêm";
            String target = worldName == null ? "mặc định toàn cục" : "của world §e" + worldName;
            sender.sendMessage("§aĐã đặt thời lượng " + period + " " + target + " thành §e" + minutes +
                " phút§a và lưu vào config.yml.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("vnday")) {
            if (!permissionManager.hasPermission(sender, "vnmine.command.day.manage")) {
                sender.sendMessage("§cBạn không có quyền!");
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cLệnh này chỉ dành cho người chơi vì cần xác định world hiện tại.");
                return true;
            }
            if (args.length != 2 || !args[0].equalsIgnoreCase("set")) {
                sender.sendMessage("§eSử dụng: /vnday set <ngày>");
                return true;
            }
            long day;
            try {
                day = Long.parseLong(args[1]);
            } catch (NumberFormatException exception) {
                sender.sendMessage("§cNgày phải là số nguyên không âm.");
                return true;
            }
            if (day < 0 || day > Long.MAX_VALUE / 24_000L) {
                sender.sendMessage("§cNgày phải nằm trong khoảng 0 đến " + Long.MAX_VALUE / 24_000L + ".");
                return true;
            }
            long worldTime = day * 24_000L;
            try {
                player.getWorld().setFullTime(worldTime);
            } catch (IllegalArgumentException exception) {
                getLogger().log(java.util.logging.Level.WARNING,
                    "[TimeControl] Could not set the full time for world '" + player.getWorld().getName() + "'.",
                    exception);
                sender.sendMessage("§cKhông thể đặt ngày trong world hiện tại.");
                return true;
            }
            sender.sendMessage("§aĐã đặt world §e" + player.getWorld().getName() +
                " §avề bình minh ngày thứ §e" + day + "§a.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("spawn")) {
            if (!permissionManager.hasPermission(sender, "vnmine.command.spawn")) {
                sender.sendMessage("§cBạn không có quyền!");
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Lệnh này chỉ dành cho người chơi.");
                return true;
            }
            player.teleport(player.getWorld().getSpawnLocation());
            player.sendMessage("§aĐã dịch chuyển về điểm spawn của thế giới.");
            return true;
        }

        if (command.getName().matches("(?i)gm[0-3]")) {
            if (!permissionManager.hasPermission(sender, "vnmine.command.gamemode")) {
                sender.sendMessage("§cBạn không có quyền!");
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Lệnh này chỉ dành cho người chơi.");
                return true;
            }
            org.bukkit.GameMode gameMode = switch (command.getName().toLowerCase(Locale.ROOT)) {
                case "gm0" -> org.bukkit.GameMode.SURVIVAL;
                case "gm1" -> org.bukkit.GameMode.CREATIVE;
                case "gm2" -> org.bukkit.GameMode.ADVENTURE;
                case "gm3" -> org.bukkit.GameMode.SPECTATOR;
                default -> throw new IllegalStateException("Unexpected game mode command");
            };
            player.setGameMode(gameMode);
            player.sendMessage("§aĐã chuyển sang chế độ §e" + gameMode.name().toLowerCase(Locale.ROOT) + "§a.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("fly")) {
            if (!permissionManager.hasPermission(sender, "vnmine.command.fly")) {
                sender.sendMessage("§cBạn không có quyền!");
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Lệnh này chỉ dành cho người chơi.");
                return true;
            }
            if (player.getGameMode() != org.bukkit.GameMode.SURVIVAL) {
                player.sendMessage("§cLệnh /fly chỉ hoạt động trong chế độ Sinh tồn.");
                return true;
            }
            if (player.getAllowFlight()) {
                player.setFlying(false);
                player.setAllowFlight(false);
                player.sendMessage("§eĐã tắt bay trong chế độ Sinh tồn.");
            } else {
                player.setAllowFlight(true);
                player.sendMessage("§aĐã bật bay trong chế độ Sinh tồn.");
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("nodame")) {
            if (!permissionManager.hasPermission(sender, "vnmine.command.nodame")) {
                sender.sendMessage("§cBạn không có quyền!");
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Lệnh này chỉ dành cho người chơi.");
                return true;
            }
            boolean enabled = deathListener.toggleNoDamageMode(player);
            player.sendMessage(enabled
                ? "§aĐã bật /nodame: quái vẫn có thể chọn bạn làm mục tiêu nhưng bạn không nhận sát thương."
                : "§eĐã tắt /nodame.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("vnrespawn")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Lệnh này chỉ dành cho người chơi.");
                return true;
            }
            if (!deathListener.requestRespawn(player)) {
                player.sendMessage("§cBạn hiện không chờ chọn hồi sinh.");
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("vnleave")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Lệnh này chỉ dành cho người chơi.");
                return true;
            }
            if (!deathListener.isAwaitingRespawnChoice(player)) {
                player.sendMessage("§cBạn chỉ có thể dùng lệnh này sau khi chết.");
                return true;
            }
            player.kick(net.kyori.adventure.text.Component.text("Bạn đã chọn thoát game sau khi chết."));
            return true;
        }

        if (command.getName().equalsIgnoreCase("reward")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Lệnh này chỉ dành cho người chơi.");
                return true;
            }
            return bossLeaderboardManager.claimRewards(player);
        }

        if (command.getName().equalsIgnoreCase("vnhelp")) {
            sender.sendMessage("§6§m========================================");
            sender.sendMessage("§6§l  VnMineHardcore - Danh sách lệnh");
            sender.sendMessage("§6§m========================================");
            sendHelpLine(sender, "vnmine.command.stats",
                "§e/vnstats §7- Xem thống kê cá nhân.");
            sendHelpLine(sender, "vnmine.command.status",
                "§e/vnhardcore §7- Xem trạng thái hệ thống.");
            sendHelpLine(sender, "vnmine.command.death.reset",
                "§e/vn death reset <player> §7- Đặt số lần chết của người chơi về 0.");
            sendHelpLine(sender, "vnmine.command.disasters",
                "§e/vnevent §7- Xem danh sách thiên tai.");
            sendHelpLine(sender, "vnmine.command.disasters",
                "§e/vnevent <id> <warning-giây> <duration-giây> §7- Gọi thiên tai.");
            sendHelpLine(sender, "vnmine.command.disasters",
                "§e/vnevent stop §7- Dừng thiên tai đang cảnh báo/diễn ra.");
            sendHelpLine(sender, "vnmine.command.bosses", "§e/vnboss §7- Xem danh sách boss.");
            sendHelpLine(sender, "vnmine.command.bosses",
                "§e/vnboss <id> <level> <warning-giây> <duration-giây> §7- Gọi boss.");
            sendHelpLine(sender, "vnmine.command.bosses",
                "§e/vnboss kill §7- Kết thúc boss, không phát điểm hoặc loot.");
            sendHelpLine(sender, "vnmine.command.reward",
                "§e/reward §7- Nhận phần thưởng boss đang chờ; cần đủ chỗ trong túi.");
            sendHelpLine(sender, "vnmine.command.scoreboard.reset",
                "§e/scoreboard reset §7- Xóa điểm mùa và phần thưởng đang chờ.");
            sendHelpLine(sender, "vnmine.command.scoreboard.reset",
                "§e/vnscoreboard reset, /sb reset §7- Bí danh của /scoreboard.");
            sendHelpLine(sender, "vnmine.command.time.manage",
                "§e/vntime set <day|night> <phút> [world] §7- Đặt thời lượng ngày/đêm.");
            sendHelpLine(sender, "vnmine.command.day.manage",
                "§e/vnday set <ngày> §7- Đặt world hiện tại về bình minh ngày đó.");
            sendHelpLine(sender, "vnmine.command.teleport.other",
                "§e/tp <user> <world> §7- Đưa người chơi đến điểm spawn của world.");
            sendHelpLine(sender, "vnmine.command.spawn.manage",
                "§e/vnspawn set §7- Đặt spawn tại vị trí hiện tại.");
            sendHelpLine(sender, "vnmine.command.spawn.manage",
                "§e/vnspawn go <world> §7- Dịch chuyển đến spawn của world.");
            sendHelpLine(sender, "vnmine.command.spawn.manage",
                "§e/vnspawn protect <X> §7- Bảo vệ bán kính X quanh vị trí hiện tại.");
            sendHelpLine(sender, "vnmine.command.spawn.manage",
                "§e/vnspawn list §7- Liệt kê các vùng spawn được bảo vệ.");
            sendHelpLine(sender, "vnmine.command.spawn.manage",
                "§e/vnspawn remove <ID> §7- Xóa vùng bảo vệ.");
            sendHelpLine(sender, "vnmine.command.respawn",
                "§e/vnrespawn §7- Chọn hồi sinh khi đang chờ sau khi chết.");
            sendHelpLine(sender, "vnmine.command.leave",
                "§e/vnleave §7- Thoát máy chủ khi đang chờ sau khi chết.");
            sendHelpLine(sender, "vnmine.command.fly",
                "§e/fly §7- Bật/tắt bay trong Survival.");
            sendHelpLine(sender, "vnmine.command.nodame",
                "§e/nodame §7- Bật/tắt miễn sát thương để thử boss.");
            sendHelpLine(sender, "vnmine.command.spawn",
                "§e/spawn §7- Dịch chuyển về spawn của world hiện tại.");
            sendHelpLine(sender, "vnmine.command.gamemode",
                "§e/gm0 /gm1 /gm2 /gm3 §7- Đổi game mode.");
            sendHelpLine(sender, "vnmine.command.reload",
                "§e/vnreload §7- Tải lại cấu hình và các hệ thống.");
            sendHelpLine(sender, "vnmine.command.init",
                "§e/vninit §7- Mở wizard setup đại lục, world, NPC tutorial và onboarding RPG.");
            sendHelpLine(sender, "vnmine.command.users.manage",
                "§e/vnuser info <player> §7- Xem nhóm/quyền người chơi.");
            sendHelpLine(sender, "vnmine.command.users.manage",
                "§e/vnuser group <player> <group> §7- Gán nhóm.");
            sendHelpLine(sender, "vnmine.command.users.manage",
                "§e/vnuser permission <add|remove> <player> <permission> §7- Chỉnh quyền riêng.");
            sendHelpLine(sender, "vnmine.command.users.manage",
                "§e/vnuser reset <player> §7- Đặt lại nhóm mặc định.");
            sendHelpLine(sender, "vnmine.command.army",
                "§e/vnarmy §7- Thuê và quản lý lính đánh thuê.");
            sendHelpLine(sender, "vnmine.command.army",
                "§7  gui | list | hire <loại> <bậc> | recall [ID] | rest [ID]");
            sendHelpLine(sender, "vnmine.command.army",
                "§7  rename <ID> <tên> | gear <ID> | mute <ID> | remove <ID>");
            sendHelpLine(sender, "vnmine.command.army",
                "§7  attack | defend | protect | token give <player> <loại> <bậc>");
            sendHelpLine(sender, "vnmine.command.army.admin",
                "§7  /vnarmy list <user> | remove <user> <ID> | clear <user>");
            sendHelpLine(sender, "vnmine.command.army.admin",
                "§7  /vnarmy give <user> token <bậc> | give <user> army <loại> <bậc>");
            sendHelpLine(sender, "vnmine.command.groups.manage",
                "§e/vngroup list|info <group>|create <group>|delete <group> §7- Quản lý nhóm.");
            sendHelpLine(sender, "vnmine.command.groups.manage",
                "§e/vngroup permission <add|remove> <group> <permission> §7- Chỉnh quyền nhóm.");
            sendHelpLine(sender, "vnmine.command.death.history",
                "§e/vndeathhistory [player] §7- Xem/khôi phục lịch sử inventory lúc chết.");
            sendHelpLine(sender, null,
                "§e/vncoin §7- Xem số dư VNCOIN; §e/vncoin history §7- Xem giao dịch gần đây.");
            sendHelpLine(sender, "vnmine.command.vncoin.history.others",
                "§e/vncoin history <player> §7- Xem lịch sử ví người chơi khác.");
            sendHelpLine(sender, "vnmine.command.vncoin.admin",
                "§e/vncoin give|take <player> <số> [lý do] §7- Quản trị số dư có audit.");
            sendHelpLine(sender, null,
                "§e/quatanthu §7- Nhận túi quà tân thủ nếu đủ điều kiện.");
            sendHelpLine(sender, null,
                "§e/vnmenu §7- Mở menu chính; phím F cũng mở menu này.");
            sendHelpLine(sender, "vnmine.command.utility", "§e/vn help §7- Hướng dẫn lệnh /vn.");
            sendHelpLine(sender, "vnmine.command.help", "§e/vnhelp §7- Hiển thị trợ giúp theo quyền của bạn.");
            sender.sendMessage("§6§m========================================");
            return true;
        }

        if (command.getName().equalsIgnoreCase("vnreload")) {
            if (!permissionManager.hasPermission(sender, "vnmine.command.reload")) {
                sender.sendMessage("§cBạn không có quyền!");
                return true;
            }
            configManager.reload();
            try {
                economyManager.reloadConfiguration();
            } catch (IllegalArgumentException exception) {
                getLogger().log(Level.SEVERE,
                    "[Economy] VNCOIN config is invalid; previous economy settings remain active.", exception);
                sender.sendMessage("§cCấu hình VNCOIN không hợp lệ; thiết lập VNCOIN trước đó vẫn được giữ.");
            }
            if (economyManager.reloadMerchantPrices()) {
                sender.sendMessage("§aĐã tải lại allowlist giá bán system merchant.");
            } else {
                sender.sendMessage("§cKhông tải được merchant-prices.yml; bảng giá hợp lệ trước đó vẫn được giữ.");
            }
            reloadProfileDatabaseMaintenance();
            timeControlManager.reload();
            hungerListener.reload();
            environmentListener.reload();
            thirstManager.reload();
            deathListener.reload();
            deathPenaltyManager.reload();
            graveManager.reload();
            disasterManager.reload();
            bossLeaderboardManager.reload();
            bossEventManager.reload();
            if (!mercenaryManager.reload()) {
                sender.sendMessage("§cCác hệ thống khác đã tải lại nhưng armies.yml lỗi; cấu hình lính cũ vẫn được giữ.");
                return true;
            }
            if (!permissionManager.reload()) {
                sender.sendMessage("§cCác cấu hình khác đã được tải lại nhưng permission.yml không hợp lệ/không đọc được; quyền cũ vẫn được giữ.");
                return true;
            }
            if (!tooltipManager.reload()) {
                sender.sendMessage("§cCác cấu hình khác đã tải lại nhưng tooltips.yml không hợp lệ; tooltip đang dùng cấu hình cũ.");
                return true;
            }
            if (!deathHistoryManager.reload()) {
                sender.sendMessage("§cCác hệ thống khác đã tải lại nhưng giới hạn lịch sử chết vẫn giữ giá trị cũ do không lưu được dữ liệu.");
                return true;
            }
            if (!rpgWorldManager.reload()) {
                sender.sendMessage("§cCác hệ thống khác đã tải lại nhưng định nghĩa world không hợp lệ; cấu hình world cũ vẫn được giữ.");
                return true;
            }
            if (!tutorialQuestManager.reload()) {
                sender.sendMessage("§cCác hệ thống khác đã tải lại nhưng quest tutorial không hợp lệ; cấu hình quest cũ vẫn được giữ.");
                return true;
            }
            sender.sendMessage("§a✅ Config và các hệ thống đã được tải lại!");
            getLogger().info("[Cmd] " + sender.getName() + " reloaded config");
            return true;
        }

        if (command.getName().equalsIgnoreCase("vnevent")) {
            if (!permissionManager.hasPermission(sender, "vnmine.command.disasters")) {
                sender.sendMessage("§cBạn không có quyền!");
                return true;
            }
            if (args.length == 0) {
                sender.sendMessage("§6§m=======================================");
                sender.sendMessage("§6§l  /vnevent - Gọi thiên tai thủ công");
                sender.sendMessage("§6§m=======================================");
                sender.sendMessage("§eCách dùng: §7/vnevent <id> <giây cảnh báo> <giây kéo dài> | /vnevent stop");
                sender.sendMessage("§e  /vnevent §7- Hiển thị danh sách này");
                sender.sendMessage("§e  /vnevent stop §7- Dừng thiên tai đang cảnh báo hoặc hoạt động ngay lập tức");
                sender.sendMessage("");
                sender.sendMessage("§eDanh sách ID thiên tai có thể dùng:");
                for (String id : disasterManager.getDisasterIds()) {
                    String name = disasterManager.getDisasterName(id);
                    sender.sendMessage("§c  " + id + "  §7- " + name);
                }
                sender.sendMessage("");
                sender.sendMessage("§eVí dụ: §7/vnevent blood-moon 30 60");
                sender.sendMessage("§6§m=======================================");
                return true;
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("stop")) {
                String stoppedName = disasterManager.stopCurrentDisaster();
                if (stoppedName == null) {
                    sender.sendMessage("§eHiện không có thiên tai nào đang cảnh báo hoặc hoạt động.");
                } else {
                    sender.sendMessage("§aĐã dừng thiên tai §e" + stoppedName + "§a.");
                }
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage("§cSử dụng: /vnevent <id> <giây cảnh báo> <giây kéo dài> hoặc /vnevent stop");
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
            if (!permissionManager.hasPermission(sender, "vnmine.command.bosses")) {
                sender.sendMessage("§cBạn không có quyền!");
                return true;
            }
            if (args.length == 0) {
                sender.sendMessage("§6§m=======================================");
                sender.sendMessage("§6§l  /vnboss - Quản lý boss");
                sender.sendMessage("§6§m=======================================");
                sender.sendMessage("§eCách dùng: §7/vnboss <id> <level> <giây cảnh báo> <giây kéo dài>");
                sender.sendMessage("§e  /vnboss kill §7- Kết thúc boss đang xuất hiện (không điểm/loot)");
                sender.sendMessage("§e  /vnboss §7- Hiển thị danh sách này");
                sender.sendMessage("");
                sender.sendMessage("§eDanh sách ID boss có thể dùng:");
                for (String id : bossEventManager.getBossIds()) {
                    sender.sendMessage("§c  " + id + "  §7- " + bossEventManager.getBossName(id));
                }
                sender.sendMessage("");
                sender.sendMessage("§eVí dụ: §7/vnboss wither 1 30 120");
                sender.sendMessage("§e  /vnboss wither 3 30 120 §7- Gọi Wither level 3");
                sender.sendMessage("§6§m=======================================");
                return true;
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("kill")) {
                if (bossEventManager.killActiveBoss()) {
                    sender.sendMessage("§aĐã kết thúc boss đang hoạt động; không trao điểm hoặc loot.");
                } else {
                    sender.sendMessage("§cHiện không có boss nào đang xuất hiện.");
                }
                return true;
            }
            if (args.length != 4) {
                sender.sendMessage("§cSử dụng: /vnboss <id> <level> <giây cảnh báo> <giây kéo dài> hoặc /vnboss kill");
                return true;
            }
            String bossId = args[0].toLowerCase(Locale.ROOT);
            int level, warningTime, duration;
            try {
                level = Integer.parseInt(args[1]);
                warningTime = Integer.parseInt(args[2]);
                duration = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cLevel và thời gian phải là số nguyên!");
                return true;
            }
            if (level < 1 || level > 1000 || warningTime < 1 || duration < 1) {
                sender.sendMessage("§cLevel phải từ 1 đến 1000; thời gian phải >= 1 giây!");
                return true;
            }
            boolean success = bossEventManager.triggerBoss(bossId, level, warningTime, duration);
            if (success) {
                sender.sendMessage("§a✅ Đã kích hoạt boss §e" + bossEventManager.getBossName(bossId) +
                    " §7(level " + level + ")");
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
                sender.sendMessage("§6=== Thống kê VnMineHardcore ===");
                sender.sendMessage("§eSố lần chết: §c" + logManager.getDeathCount(player));
                sender.sendMessage("§eThời gian sinh tồn: §a" + logManager.getSurvivalTime(player));
                sender.sendMessage("§eQuái đã giết: §b" + logManager.getMobKills(player));
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("vnhardcore")) {
            sender.sendMessage("§6§m========================================");
            sender.sendMessage("§6§l  VnMineHardcore - Trạng thái quản trị");
            sender.sendMessage("§6§m========================================");
            sender.sendMessage("§c☠ TỬ VONG: §aTheo dõi | Tag=§aBẬT (" +
                (configManager.combatTagDurationMs / 1000) + "s)");
            sender.sendMessage("§e🍔 ĐÓI: " + (configManager.hungerEnabled ? "§aBẬT" : "§cTẮT"));
            sender.sendMessage("§b💧 KHÁT: " + (configManager.thirstEnabled ? "§aBẬT" : "§cTẮT") +
                " | Nguồn=" + (configManager.drinkFromSource ? "§aBẬT" : "§cTẮT"));
            sender.sendMessage("§c⚔ TẤN CÔNG: Quái=x" + configManager.mobDamageMultiplier +
                " Rơi=x" + configManager.fallDamageMultiplier +
                " Hồi phục=" + (!configManager.disableNaturalRegen ? "§aBẬT" : "§cTẮT"));
            sender.sendMessage("§a🌍 MÔI TRƯỜNG: Nhiệt=" + (configManager.temperatureEnabled ? "§aBẬT" : "§cTẮT") +
                " Sương=" + (configManager.fogEnabled ? "§aBẬT" : "§cTẮT") +
                " Bay=" + (configManager.flightBlock ? "§cCHẶN" : "§aOK"));
            sender.sendMessage("§4🌋 THIÊN TAI: " + (configManager.disastersEnabled ? "§aBẬT" : "§cTẮT") +
                (disasterManager.isDisasterActive() ? " §c⚠ ĐANG CHẠY: " + disasterManager.getCurrentDisaster() : ""));
            sender.sendMessage("§c👹 SỰ KIỆN BOSS: " + (configManager.bossEventsEnabled ? "§aBẬT" : "§cTẮT") +
                (bossEventManager.isBossActive() ? " §c⚠ ĐANG CHẠY: " + bossEventManager.getCurrentBossName() : ""));
            sender.sendMessage("§e🏷 ĐỔI TÊN: " + (configManager.renameEnabled ? "§aBẬT" : "§cTẮT"));
            sender.sendMessage("§eLệnh: §7/vnhelp | /vnreload | /vnevent | /vn death reset <player>");
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
                if (!permissionManager.hasPermission(sender, "vnmine.command.death.reset")) {
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
            if (args.length == 1 && args[0].equalsIgnoreCase("help")) {
                sender.sendMessage("§eDùng /vnhelp để xem toàn bộ lệnh.");
                sender.sendMessage("§e/vn death reset <player> §7- Đặt số lần chết của người chơi về 0 (admin).");
                return true;
            }
            sender.sendMessage("§cLệnh phụ không hợp lệ. Gõ /vnhelp để xem hướng dẫn.");
            return true;
        }
        return false;
    }

    private void sendHelpLine(CommandSender sender, String permission, String message) {
        if (permission == null || permissionManager.hasPermission(sender, permission)) {
            sender.sendMessage(message);
        }
    }

    private boolean handleTeleportToWorldSpawn(CommandSender sender, String[] args) {
        if (!permissionManager.hasPermission(sender, "vnmine.command.teleport.other")) {
            sender.sendMessage("§cBạn không có quyền!");
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage("§eCách dùng: /tp <user> <world>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage("§cKhông tìm thấy người chơi đang trực tuyến: §e" + args[0]);
            return true;
        }
        org.bukkit.World world = Bukkit.getWorld(args[1]);
        if (world == null) {
            sender.sendMessage("§cKhông tìm thấy world đang tải: §e" + args[1]);
            return true;
        }
        if (!target.teleport(world.getSpawnLocation())) {
            sender.sendMessage("§cKhông thể dịch chuyển §e" + target.getName() + "§c đến spawn của §e" + world.getName() + "§c.");
            return true;
        }

        target.sendMessage("§aBạn đã được dịch chuyển đến điểm spawn của world §e" + world.getName() + "§a.");
        sender.sendMessage("§aĐã dịch chuyển §e" + target.getName() + " §ađến điểm spawn của world §e" + world.getName() + "§a.");
        return true;
    }

    private boolean handleVnSpawn(CommandSender sender, String[] args) {
        if (!permissionManager.hasPermission(sender, "vnmine.command.spawn.manage")) {
            sender.sendMessage("§cBạn không có quyền!");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("set")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cLệnh này chỉ dành cho người chơi vì cần vị trí hiện tại.");
                return true;
            }
            if (!player.getWorld().setSpawnLocation(player.getLocation())) {
                sender.sendMessage("§cKhông thể đặt điểm spawn cho world này.");
                return true;
            }
            sender.sendMessage("§aĐã đặt điểm spawn của world §e" + player.getWorld().getName()
                + " §atại vị trí hiện tại của bạn.");
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("go")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cLệnh này chỉ dành cho người chơi.");
                return true;
            }
            org.bukkit.World world = Bukkit.getWorld(args[1]);
            if (world == null) {
                sender.sendMessage("§cKhông tìm thấy world đang tải: §e" + args[1]);
                return true;
            }
            if (!player.teleport(world.getSpawnLocation())) {
                sender.sendMessage("§cKhông thể dịch chuyển đến spawn của world §e"
                    + world.getName() + "§c.");
                return true;
            }
            sender.sendMessage("§aĐã dịch chuyển đến điểm spawn của world §e"
                + world.getName() + "§a.");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            List<SpawnProtectionManager.Protection> protections = spawnProtectionManager.getProtections();
            if (protections.isEmpty()) {
                sender.sendMessage("§eChưa có khu vực spawn nào được bảo vệ.");
                return true;
            }
            sender.sendMessage("§6§lDanh sách spawn protection (" + protections.size() + "):");
            for (SpawnProtectionManager.Protection protection : protections) {
                sender.sendMessage("§eID " + protection.id() + " §7| world: §f" + protection.world()
                    + " §7| bán kính: §f" + protection.radius() + " block"
                    + " §7| tâm X/Z: §f" + protection.centerX() + ", " + protection.centerZ());
            }
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("protect")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cLệnh này chỉ dành cho người chơi vì cần vị trí hiện tại.");
                return true;
            }
            int radius;
            try {
                radius = Integer.parseInt(args[1]);
            } catch (NumberFormatException exception) {
                sender.sendMessage("§cBán kính phải là số nguyên dương.");
                return true;
            }
            if (radius < 1) {
                sender.sendMessage("§cBán kính phải lớn hơn 0.");
                return true;
            }
            Integer id = spawnProtectionManager.addProtection(player.getLocation(), radius);
            if (id == null) {
                sender.sendMessage("§cKhông thể lưu khu vực bảo vệ; dữ liệu chưa được thay đổi.");
                return true;
            }
            sender.sendMessage("§aĐã tạo khu vực bảo vệ §e#" + id + " §avới bán kính §e" + radius
                + " block §atrên world §e" + player.getWorld().getName() + "§a.");
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            int id;
            try {
                id = Integer.parseInt(args[1]);
            } catch (NumberFormatException exception) {
                sender.sendMessage("§cID phải là số nguyên dương.");
                return true;
            }
            if (id < 1) {
                sender.sendMessage("§cID phải là số nguyên dương.");
                return true;
            }
            switch (spawnProtectionManager.removeProtection(id)) {
                case REMOVED -> sender.sendMessage("§aĐã xóa khu vực bảo vệ §e#" + id + "§a.");
                case NOT_FOUND -> sender.sendMessage("§cKhông tìm thấy khu vực bảo vệ có ID §e" + id + "§c.");
                case SAVE_FAILED -> sender.sendMessage("§cKhông thể lưu thay đổi; khu vực bảo vệ vẫn được giữ lại.");
            }
            return true;
        }

        sender.sendMessage("§eSử dụng: /vnspawn set | /vnspawn go <world> | /vnspawn protect <X> | /vnspawn list | /vnspawn remove <ID>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("tp")
                && permissionManager.hasPermission(sender, "vnmine.command.teleport.other")) {
            if (args.length == 1) {
                String partial = args[0].toLowerCase(Locale.ROOT);
                return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                    .toList();
            }
            if (args.length == 2) {
                String partial = args[1].toLowerCase(Locale.ROOT);
                return Bukkit.getWorlds().stream()
                    .map(org.bukkit.World::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                    .toList();
            }
        }
        if (command.getName().equalsIgnoreCase("vnspawn")
                && permissionManager.hasPermission(sender, "vnmine.command.spawn.manage")) {
            if (args.length == 1) return matchingSuggestions(args[0], "set", "go", "protect", "list", "remove");
            if (args.length == 2 && args[0].equalsIgnoreCase("go")) {
                String partial = args[1].toLowerCase(Locale.ROOT);
                return Bukkit.getWorlds().stream()
                    .map(org.bukkit.World::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                    .toList();
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("protect")) {
                return matchingSuggestions(args[1], "10", "25", "50", "100");
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
                String partial = args[1].toLowerCase(Locale.ROOT);
                return spawnProtectionManager.getProtectionIds().stream()
                    .map(String::valueOf)
                    .filter(id -> id.startsWith(partial))
                    .toList();
            }
        }
        if (command.getName().equalsIgnoreCase("vnboss") && args.length == 1
                && permissionManager.hasPermission(sender, "vnmine.command.bosses")) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            List<String> matches = new ArrayList<>();
            if ("kill".startsWith(partial)) matches.add("kill");
            for (String id : bossEventManager.getEnabledBossIds()) {
                if (id.toLowerCase(Locale.ROOT).startsWith(partial)) matches.add(id);
            }
            return matches;
        }
        if (command.getName().equalsIgnoreCase("vnevent") && args.length == 1
                && permissionManager.hasPermission(sender, "vnmine.command.disasters")) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            List<String> suggestions = new ArrayList<>();
            if ("stop".startsWith(partial)) suggestions.add("stop");
            suggestions.addAll(disasterManager.getDisasterIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(partial))
                .toList());
            return suggestions;
        }
        if (command.getName().equalsIgnoreCase("vnevent") && args.length == 2
                && permissionManager.hasPermission(sender, "vnmine.command.disasters")) {
            return matchingSuggestions(args[1], "10", "30", "60");
        }
        if (command.getName().equalsIgnoreCase("vnevent") && args.length == 3
                && permissionManager.hasPermission(sender, "vnmine.command.disasters")) {
            return matchingSuggestions(args[2], "30", "60", "120");
        }
        if (command.getName().equalsIgnoreCase("vnboss") && args.length == 2
                && permissionManager.hasPermission(sender, "vnmine.command.bosses")) {
            return matchingSuggestions(args[1], "1", "2", "3", "4", "5");
        }
        if (command.getName().equalsIgnoreCase("vnboss") && args.length == 3
                && permissionManager.hasPermission(sender, "vnmine.command.bosses")) {
            return matchingSuggestions(args[2], "10", "30", "60");
        }
        if (command.getName().equalsIgnoreCase("vnboss") && args.length == 4
                && permissionManager.hasPermission(sender, "vnmine.command.bosses")) {
            return matchingSuggestions(args[3], "60", "120", "300");
        }
        if (command.getName().equalsIgnoreCase("scoreboard") && args.length == 1
                && permissionManager.hasPermission(sender, "vnmine.command.scoreboard.reset")
                && "reset".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            return List.of("reset");
        }
        if (command.getName().equalsIgnoreCase("vntime")
                && permissionManager.hasPermission(sender, "vnmine.command.time.manage")) {
            if (args.length == 1) {
                return matchingSuggestions(args[0], "set");
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
                return matchingSuggestions(args[1], "day", "night");
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("set")
                    && (args[1].equalsIgnoreCase("day") || args[1].equalsIgnoreCase("night"))) {
                return matchingSuggestions(args[2], "5", "10", "15", "20", "30", "60");
            }
            if (args.length == 4 && args[0].equalsIgnoreCase("set")
                    && (args[1].equalsIgnoreCase("day") || args[1].equalsIgnoreCase("night"))) {
                String partial = args[3].toLowerCase(Locale.ROOT);
                return Bukkit.getWorlds().stream()
                    .map(world -> world.getName())
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                    .toList();
            }
        }
        if (command.getName().equalsIgnoreCase("vnday")
                && permissionManager.hasPermission(sender, "vnmine.command.day.manage")) {
            if (args.length == 1) return matchingSuggestions(args[0], "set");
            if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
                return matchingSuggestions(args[1], "0", "1", "10", "50", "100");
            }
        }
        if (command.getName().equalsIgnoreCase("vn")
                && permissionManager.hasPermission(sender, "vnmine.command.utility")) {
            if (args.length == 1) {
                return matchingSuggestions(args[0], "death", "help");
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("death")
                    && permissionManager.hasPermission(sender, "vnmine.command.death.reset")) {
                return matchingSuggestions(args[1], "reset");
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("death")
                    && args[1].equalsIgnoreCase("reset")
                    && permissionManager.hasPermission(sender, "vnmine.command.death.reset")) {
                String partial = args[2].toLowerCase(Locale.ROOT);
                return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                    .toList();
            }
        }
        return List.of();
    }

    private List<String> matchingSuggestions(String partial, String... options) {
        String normalized = partial.toLowerCase(Locale.ROOT);
        return java.util.Arrays.stream(options)
            .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(normalized))
            .toList();
    }

    public static VnMineHardcore getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public PlayerProfileDatabaseManager getPlayerProfileDatabaseManager() { return playerProfileDatabaseManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public Map<String, NpcDefinitionLoader.NpcDefinition> getNpcDefinitions() {
        return npcDefinitions;
    }

    public NpcManager getNpcManager() {
        return npcManager;
    }
    public NpcMarkerManager getNpcMarkerManager() { return npcMarkerManager; }
    public TextDisplayManager getTextDisplayManager() { return textDisplayManager; }
    public SkillManager getSkillManager() { return skillManager; }
    public CharacterSelectionManager getCharacterSelectionManager() { return characterSelectionManager; }
    public RpgWorldManager getRpgWorldManager() { return rpgWorldManager; }
    public TutorialQuestManager getTutorialQuestManager() { return tutorialQuestManager; }

    public QuestCatalogManager getQuestCatalogManager() { return questCatalogManager; }
    public QuestDialogManager getQuestDialogManager() { return questDialogManager; }
    public ServerSetupWizardManager getServerSetupWizardManager() { return serverSetupWizardManager; }
    public LogManager getLogManager() { return logManager; }
    public ThirstManager getThirstManager() { return thirstManager; }
    public DisasterManager getDisasterManager() { return disasterManager; }
    public DeathPenaltyManager getDeathPenaltyManager() { return deathPenaltyManager; }
    public BossEventManager getBossEventManager() { return bossEventManager; }
    public DeathListener getDeathListener() { return deathListener; }
    public BossLeaderboardManager getBossLeaderboardManager() { return bossLeaderboardManager; }
    public GraveManager getGraveManager() { return graveManager; }
    public SpawnProtectionManager getSpawnProtectionManager() { return spawnProtectionManager; }
    public PermissionManager getPermissionManager() { return permissionManager; }
    public MercenaryManager getMercenaryManager() { return mercenaryManager; }
    public PlayerAdminManager getPlayerAdminManager() { return playerAdminManager; }

    private void clearLegacyPluginBans() {
        int cleared = 0;
        for (BanList.Type type : new BanList.Type[] {BanList.Type.NAME, BanList.Type.IP}) {
            BanList banList = Bukkit.getBanList(type);
            java.util.Set<String> legacyTargets = new java.util.HashSet<>();
            for (Object entryObject : banList.getBanEntries()) {
                if (entryObject instanceof org.bukkit.BanEntry entry &&
                    "VnMineHardcore".equals(entry.getSource())) {
                    legacyTargets.add(entry.getTarget());
                }
            }
            for (String target : legacyTargets) {
                banList.pardon(target);
                cleared++;
            }
        }
        if (cleared > 0) {
            getLogger().info("[Death] Removed " + cleared + " legacy plugin bans; deaths no longer ban players.");
        }
    }
}
