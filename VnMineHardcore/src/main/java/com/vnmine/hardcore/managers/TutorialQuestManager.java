package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

public final class TutorialQuestManager implements Listener {
    private final VnMineHardcore plugin;
    private final File definitionFile;
    private final NamespacedKey trackingEnabledKey;
    private final Map<UUID, Boolean> activeQuestSessions = new HashMap<>();
    private final Map<UUID, Long> lastWaypointAtMillis = new HashMap<>();
    private final java.util.Set<UUID> pendingQuestCompletions = new java.util.HashSet<>();
    private TutorialQuestDefinitionLoader.TutorialQuestDefinition definition;
    private BukkitTask waypointTicker;

    public TutorialQuestManager(VnMineHardcore plugin) {
        this.plugin = plugin;
        this.trackingEnabledKey = new NamespacedKey(plugin, "quest_tracking_enabled");
        File questsFolder = new File(plugin.getDataFolder(), "quests/tutorial");
        if (!questsFolder.isDirectory() && !questsFolder.mkdirs()) {
            plugin.getLogger().severe("[Quest] Could not create the tutorial quest configuration directory.");
            definitionFile = new File(questsFolder, "first-steps.yml");
            return;
        }

        definitionFile = new File(questsFolder, "first-steps.yml");
        if (!definitionFile.isFile()) {
            try {
                plugin.saveResource("quests/tutorial/first-steps.yml", false);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().log(Level.SEVERE, "[Quest] Bundled first-steps.yml resource is missing.", exception);
                return;
            }
        }
        reload();
    }

    public boolean reload() {
        Optional<TutorialQuestDefinitionLoader.TutorialQuestDefinition> loaded =
            TutorialQuestDefinitionLoader.load(definitionFile, plugin.getLogger());
        if (loaded.isEmpty()) return false;

        definition = loaded.get();
        startWaypointTicker();
        for (Player player : Bukkit.getOnlinePlayers()) {
            initializePlayer(player, false);
        }
        return true;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        initializePlayer(event.getPlayer(), true);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        QuestWaypointRenderer.forget(playerId);
        activeQuestSessions.remove(playerId);
        lastWaypointAtMillis.remove(playerId);
    }

    public List<String> getQuestSidebarLines(Player player) {
        CharacterSelectionManager characters = plugin.getCharacterSelectionManager();
        if (characters != null) {
            List<String> onboardingLines = characters.getOnboardingSidebarLines(player);
            if (!onboardingLines.isEmpty()) return onboardingLines;
        }
        if (definition == null || !definition.enabled()
                || !activeQuestSessions.containsKey(player.getUniqueId())) return List.of();
        return List.of("§d§lNHIỆM VỤ", "§fTới: Nhân giới", "§7Mục tiêu: Đi tới spawn");
    }

    private void refreshQuestSidebar(Player player) {
        BossLeaderboardManager leaderboard = plugin.getBossLeaderboardManager();
        if (leaderboard != null) leaderboard.refreshPlayerBoard(player);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) initializePlayer(player, false);
        }, 1L);
    }

    public void openJournal(Player player) {
        if (!plugin.getConfig().getBoolean("rpg.quest-journal.enabled", true)) {
            player.sendMessage("§cNhật ký nhiệm vụ hiện đang tắt.");
            return;
        }
        if (definition == null) {
            player.sendMessage("§cNhật ký nhiệm vụ hiện chưa khả dụng. Hãy báo quản trị viên.");
            return;
        }

        PlayerProfileDatabaseManager database = plugin.getPlayerProfileDatabaseManager();
        if (database == null) {
            plugin.getLogger().severe("[Quest] Cannot open journal because the profile database is unavailable.");
            player.sendMessage("§cKhông thể tải nhật ký nhiệm vụ. Dữ liệu của bạn vẫn được giữ.");
            return;
        }
        UUID playerId = player.getUniqueId();
        debugQuest("journal-open-start player=" + player.getName() + ", quest=" + definition.id());
        TutorialQuestDefinitionLoader.TutorialQuestDefinition quest = definition;
        database.executeGameplayDatabaseTask("quest.journal player=" + player.getName(), () -> {
            Optional<PlayerProfileDatabaseManager.PlayerProfile> profile =
                database.getPlayerProfile(playerId.toString());
            boolean hasActiveProfile = profile.isPresent() && profile.get().active();
            Optional<PlayerProfileDatabaseManager.QuestProgress> progress = hasActiveProfile
                ? database.getQuestProgress(playerId.toString(), quest.id())
                : Optional.empty();
            return new QuestJournalData(hasActiveProfile, progress);
        }).whenComplete((data, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (failure != null) {
                plugin.getLogger().log(Level.SEVERE, "[Quest] Failed to open quest journal for " +
                    player.getName(), failure);
                player.sendMessage("§cKhông thể tải nhật ký nhiệm vụ. Dữ liệu của bạn vẫn được giữ.");
                return;
            }
            debugQuest("journal-open-result player=" + player.getName() + ", activeProfile="
                + data.hasActiveProfile() + ", status="
                + data.progress().map(PlayerProfileDatabaseManager.QuestProgress::status).orElse("none"));
            openJournalInventory(player, data.hasActiveProfile(), data.progress());
        }));
    }

    private record QuestJournalData(boolean hasActiveProfile,
                                    Optional<PlayerProfileDatabaseManager.QuestProgress> progress) {
    }

    @EventHandler
    public void onQuestJournalClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof QuestJournalHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.viewer.equals(player.getUniqueId())
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        switch (event.getRawSlot()) {
            case 9 -> {
                if (holder.hasActiveProfile && plugin.getQuestCatalogManager() != null) {
                    plugin.getQuestCatalogManager().openPlayerJournal(player);
                }
            }
            case 11 -> {
                if (!holder.hasActiveProfile && plugin.getCharacterSelectionManager() != null) {
                    plugin.getCharacterSelectionManager().openRaceMenu(player);
                } else if (holder.progress.isPresent()
                        && "active".equals(holder.progress.get().status())) {
                    boolean enabled = !isTrackingEnabled(player);
                    player.getPersistentDataContainer().set(trackingEnabledKey,
                        PersistentDataType.BYTE, enabled ? (byte) 1 : (byte) 0);
                    lastWaypointAtMillis.remove(player.getUniqueId());
                    if (!enabled) player.sendActionBar(" ");
                    player.sendMessage(enabled
                        ? "§aĐã bật particle dẫn đường nhiệm vụ."
                        : "§eĐã tắt particle dẫn đường nhiệm vụ.");
                    openJournalInventory(player, true, holder.progress);
                }
            }
            case 15 -> {
                initializePlayer(player);
                openJournal(player);
            }
            case 17 -> {
                if (holder.progress.isPresent()
                        && "active".equals(holder.progress.get().status())) {
                    player.getPersistentDataContainer().set(trackingEnabledKey,
                        PersistentDataType.BYTE, (byte) 1);
                    lastWaypointAtMillis.remove(player.getUniqueId());
                    showWaypoint(player, player.getLocation(), true);
                    player.sendMessage("§aĐã bật tracking tới mục tiêu nhiệm vụ.");
                    openJournalInventory(player, true, holder.progress);
                }
            }
            case 22 -> {
                if (plugin.getMercenaryManager() != null) {
                    plugin.getMercenaryManager().openMainMenu(player);
                }
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onQuestJournalDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof QuestJournalHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || isSameBlock(from, to)) return;

        UUID playerId = event.getPlayer().getUniqueId();
        Boolean wasInside = activeQuestSessions.get(playerId);
        if (wasInside == null) return;

        boolean isInside = isAtObjective(to);
        activeQuestSessions.put(playerId, isInside);
        if (isInside && !wasInside) {
            completeObjective(event.getPlayer());
        }
    }

    private void startWaypointTicker() {
        if (waypointTicker != null) {
            waypointTicker.cancel();
            waypointTicker = null;
        }
        if (definition == null || !definition.enabled() || !definition.objective().waypointEnabled()) return;
        long interval = definition.objective().waypointUpdateIntervalTicks();
        waypointTicker = Bukkit.getScheduler().runTaskTimer(plugin, this::updateTrackedWaypoints, 1L, interval);
    }

    private void updateTrackedWaypoints() {
        if (definition == null || !definition.enabled() || !definition.objective().waypointEnabled()) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            Boolean insideObjective = activeQuestSessions.get(player.getUniqueId());
            if (insideObjective != null && !insideObjective && isTrackingEnabled(player)) {
                showWaypoint(player, player.getLocation(), false);
            }
        }
    }

    private boolean isTrackingEnabled(Player player) {
        Byte stored = player.getPersistentDataContainer().get(trackingEnabledKey, PersistentDataType.BYTE);
        return stored == null ? definition.objective().trackingEnabledByDefault() : stored != 0;
    }

    public void initializePlayer(Player player) {
        initializePlayer(player, true);
    }

    private void initializePlayer(Player player, boolean showReminder) {
        CharacterSelectionManager characters = plugin.getCharacterSelectionManager();
        if (characters != null && characters.isStarterTutorialPending(player)) {
            debugQuest("initialize-deferred player=" + player.getName()
                + ", reason=starter-skill-book-delivery-pending");
            return;
        }
        if (definition == null) return;
        TutorialQuestDefinitionLoader.TutorialQuestDefinition quest = definition;
        if (!quest.enabled()) {
            UUID playerId = player.getUniqueId();
            activeQuestSessions.remove(playerId);
            lastWaypointAtMillis.remove(playerId);
            refreshQuestSidebar(player);
            return;
        }
        UUID playerId = player.getUniqueId();
        debugQuest("initialize-start player=" + player.getName() + ", quest=" + quest.id()
            + ", reminder=" + showReminder);
        activeQuestSessions.remove(playerId);
        lastWaypointAtMillis.remove(playerId);
        refreshQuestSidebar(player);
        PlayerProfileDatabaseManager database = plugin.getPlayerProfileDatabaseManager();
        if (database == null) {
            plugin.getLogger().severe("[Quest] Cannot initialize tutorial progress because the profile database is unavailable.");
            player.sendMessage("§cKhông thể tải nhiệm vụ hướng dẫn lúc này. Tiến trình của bạn vẫn được giữ.");
            return;
        }
        String tutorialTargetWorld = plugin.getRpgWorldManager() == null ? null
            : plugin.getRpgWorldManager().getDefinitions().containsKey(quest.objective().realmId())
                ? plugin.getRpgWorldManager().getDefinitions().get(quest.objective().realmId()).worldName() : null;
        String playerName = player.getName();
        boolean debugEnabled = plugin.getConfig().getBoolean("logging.console-debug", false);
        database.executeGameplayDatabaseTask("quest.initialize player=" + player.getName(), () -> {
            Optional<PlayerProfileDatabaseManager.PlayerProfile> profile =
                database.getPlayerProfile(playerId.toString());
            if (profile.isEmpty() || !profile.get().active()) return null;
            if (tutorialTargetWorld != null && profile.get().homeWorld() != null
                    && !profile.get().homeWorld().isBlank()
                    && !tutorialTargetWorld.equals(profile.get().homeWorld())) {
                if (debugEnabled) plugin.getLogger().info("[RPG-DEBUG][Quest] initialize-skip player=" + playerName
                    + ", quest=" + quest.id() + ", reason=starting-world-mismatch, homeWorld="
                    + profile.get().homeWorld() + ", questWorld=" + tutorialTargetWorld);
                return null;
            }

            Optional<PlayerProfileDatabaseManager.QuestProgress> progress =
                database.getQuestProgress(playerId.toString(), quest.id());
            boolean startedNow = false;
            if (progress.isEmpty()) {
                String startedAt = Instant.now().toString();
                if (database.startQuestProgress(playerId.toString(), quest.id(),
                        quest.objective().id(), startedAt)) {
                    startedNow = true;
                    progress = Optional.of(new PlayerProfileDatabaseManager.QuestProgress(
                        "active", quest.objective().id(), startedAt, null, false));
                } else {
                    progress = database.getQuestProgress(playerId.toString(), quest.id());
                }
            }

            boolean rewardClaimedNow = false;
            PlayerProfileDatabaseManager.PlayerProfile updatedProfile = null;
            if (progress.isPresent() && "completed".equals(progress.get().status())
                    && !progress.get().rewardClaimed()) {
                rewardClaimedNow = "vncoin".equals(quest.reward().type())
                    ? database.claimCompletedQuestWalletReward(playerId.toString(), quest.id(),
                        quest.reward().amount(), Instant.now().toString())
                    : database.claimCompletedQuestExperienceReward(playerId.toString(), quest.id(),
                        quest.reward().amount(), Instant.now().toString());
                if (rewardClaimedNow && "character-exp".equals(quest.reward().type())) {
                    updatedProfile = database.getPlayerProfile(playerId.toString()).orElse(null);
                }
            }
            return new QuestInitData(profile.get(), progress, startedNow, rewardClaimedNow, updatedProfile);
        }).whenComplete((data, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (failure != null) {
                plugin.getLogger().log(Level.SEVERE, "[Quest] Failed to initialize tutorial progress for " +
                    player.getName(), failure);
                player.sendMessage("§cKhông thể lưu/tải tiến trình nhiệm vụ. Dữ liệu của bạn vẫn được giữ.");
                return;
            }
            if (data == null || data.progress().isEmpty()) {
                debugQuest("initialize-result player=" + player.getName()
                    + ", activeProfile=" + (data != null) + ", progress=none");
                return;
            }
            debugQuest("initialize-result player=" + player.getName() + ", status="
                + data.progress().get().status() + ", startedNow=" + data.startedNow()
                + ", rewardClaimedNow=" + data.rewardClaimedNow());
            if (data.rewardClaimedNow()) {
                if ("character-exp".equals(quest.reward().type())) {
                    CharacterSelectionManager characters2 = plugin.getCharacterSelectionManager();
                    if (characters2 != null && data.updatedProfile() != null) {
                        characters2.updateLoadedProfile(data.updatedProfile());
                    }
                    sendLevelUpFeedback(player, data.profile());
                    player.sendMessage("§aPhần thưởng nhiệm vụ đã được lưu: §e+"
                        + quest.reward().amount() + " EXP§a.");
                } else {
                    player.sendMessage("§aPhần thưởng nhiệm vụ đã được lưu: §6+"
                        + quest.reward().amount() + " VNCOIN§a.");
                }
                return;
            }
            PlayerProfileDatabaseManager.QuestProgress progress = data.progress().get();
            if (!"active".equals(progress.status())) return;
            if (!quest.objective().id().equals(progress.currentStep())) {
                plugin.getLogger().warning("[Quest] Active tutorial progress has an unknown objective ID for " +
                    player.getName() + "; saved progress was preserved.");
                return;
            }
            if (data.startedNow() || showReminder) {
                player.sendMessage("§6§l" + quest.title());
                player.sendMessage("§7" + quest.description());
                player.sendMessage("§e" + quest.objective().startMessage());
            }

            boolean alreadyAtTarget = isAtObjective(player.getLocation());
            activeQuestSessions.put(playerId, alreadyAtTarget);
            refreshQuestSidebar(player);
            if (alreadyAtTarget) {
                completeObjective(player);
            } else {
                showWaypoint(player, player.getLocation(), true);
            }
        }));
    }

    private record QuestInitData(PlayerProfileDatabaseManager.PlayerProfile profile,
                                 Optional<PlayerProfileDatabaseManager.QuestProgress> progress,
                                 boolean startedNow, boolean rewardClaimedNow,
                                 PlayerProfileDatabaseManager.PlayerProfile updatedProfile) {
    }

    static boolean isWithinObjectiveRadius(double centerX, double centerZ, double x, double z, int radius) {
        double dx = x - centerX;
        double dz = z - centerZ;
        return dx * dx + dz * dz <= (double) radius * radius;
    }

    static List<String> buildJournalLore(
            TutorialQuestDefinitionLoader.TutorialQuestDefinition quest,
            Optional<PlayerProfileDatabaseManager.QuestProgress> progress) {
        List<String> lore = new ArrayList<>();
        lore.add("§7" + quest.description());
        lore.add("");
        if (progress.isEmpty()) {
            lore.add("§eTrạng thái: §7Chưa bắt đầu");
        } else {
            PlayerProfileDatabaseManager.QuestProgress questProgress = progress.get();
            if ("completed".equals(questProgress.status())) {
                lore.add("§eTrạng thái: §aĐã hoàn thành");
                lore.add("§ePhần thưởng: " + (questProgress.rewardClaimed()
                    ? "§aĐã nhận" : "§cChưa nhận"));
                if (questProgress.completedAt() != null) {
                    lore.add("§eHoàn thành lúc: §f" + questProgress.completedAt());
                }
            } else if ("active".equals(questProgress.status())) {
                lore.add("§eTrạng thái: §6Đang thực hiện");
                lore.add("§eCheckpoint hiện tại: §f" + questProgress.currentStep());
            } else {
                lore.add("§eTrạng thái: §cKhông xác định");
            }
        }
        lore.add("");
        lore.add("§eChỉ dẫn: §f" + quest.objective().startMessage());
        lore.add("§eĐiểm đến: §f" + quest.objective().realmId());
        lore.add("§ePhạm vi: §f" + quest.objective().radiusBlocks() + " block");
        lore.add("§ePhần thưởng dự kiến: " + ("vncoin".equals(quest.reward().type())
            ? "§6+" + quest.reward().amount() + " VNCOIN"
            : "§b+" + quest.reward().amount() + " EXP nhân vật"));
        return List.copyOf(lore);
    }

    private void openJournalInventory(
            Player player,
            boolean hasActiveProfile,
            Optional<PlayerProfileDatabaseManager.QuestProgress> progress) {
        QuestJournalHolder holder = new QuestJournalHolder(player.getUniqueId(), hasActiveProfile, progress);
        Inventory inventory = Bukkit.createInventory(holder, 54, "VnMine • Nhật ký nhiệm vụ");
        holder.inventory = inventory;
        if (hasActiveProfile) {
            inventory.setItem(9, createJournalItem(Material.BOOK, "§bQuest từ NPC",
                List.of("§7Xem quest đang làm, tiến độ và phần thưởng.", "§eNhấn để mở danh sách quest.")));
        }

        if (!hasActiveProfile) {
            inventory.setItem(13, createJournalItem(Material.PAPER, "§eChưa có nhân vật RPG", List.of(
                "§7Tạo nhân vật trước để bắt đầu nhiệm vụ tutorial.")));
            inventory.setItem(11, createJournalItem(Material.ENDER_EYE, "§dTạo nhân vật", List.of(
                "§7Mở giao diện chọn chủng tộc và class.")));
        } else if (!definition.enabled()) {
            inventory.setItem(13, createJournalItem(Material.BARRIER, "§cQuest tutorial đang tắt", List.of(
                "§7Quản trị viên có thể bật lại trong first-steps.yml.")));
        } else {
            boolean activeQuest = progress.isPresent() && "active".equals(progress.get().status());
            boolean trackingEnabled = isTrackingEnabled(player);
            inventory.setItem(11, createJournalItem(activeQuest && trackingEnabled
                    ? Material.LIME_DYE : Material.GRAY_DYE,
                activeQuest && trackingEnabled ? "§aTracking: Đang bật" : "§7Tracking: Đang tắt",
                List.of(activeQuest
                    ? "§eNhấn để " + (trackingEnabled ? "tắt" : "bật") + " particle dẫn đường."
                    : "§7Chỉ bật tracking khi có nhiệm vụ đang thực hiện.")));
            inventory.setItem(13, createJournalItem(Material.WRITABLE_BOOK, "§6" + definition.title(),
                buildJournalLore(definition, progress)));
            if (activeQuest) {
                inventory.setItem(17, createJournalItem(Material.COMPASS, "§bĐiểm đến: Nhân giới",
                    List.of("§7Mục tiêu: Đi tới spawn của Nhân giới.",
                        "§eBấm để bật particle tracking tới điểm này.")));
            }
            boolean rewardClaimed = progress.isPresent()
                && "completed".equals(progress.get().status())
                && progress.get().rewardClaimed();
            if (rewardClaimed) {
                inventory.setItem(15, createJournalItem(Material.ENCHANTED_BOOK, "§aNhiệm vụ đã hoàn thành",
                    List.of("§7Trạng thái và phần thưởng đã được lưu.")));
            } else {
                inventory.setItem(15, createJournalItem(Material.CLOCK, "§eLàm mới tiến trình", List.of(
                    "§7Tải lại checkpoint và trạng thái đã lưu.",
                    "§7Quest đang hoạt động cũng sẽ được đồng bộ.")));
            }
        }

        inventory.setItem(22, createJournalItem(Material.ARROW, "§7Về menu chính", List.of(
            "§7Quay lại hub RPG.")));
        player.openInventory(inventory);
    }

    private ItemStack createJournalItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isAtObjective(Location location) {
        if (definition == null || location.getWorld() == null
                || !definition.objective().realmId().equals("human-realm")) {
            return false;
        }
        Optional<RpgWorldManager.ResolvedSpawn> target =
            plugin.getRpgWorldManager().resolveHumanRealmSpawn();
        if (target.isEmpty()) return false;

        Location center = target.get().location();
        return location.getWorld() == center.getWorld()
            && isWithinObjectiveRadius(center.getX(), center.getZ(), location.getX(), location.getZ(),
                definition.objective().radiusBlocks());
    }

    private void showWaypoint(Player player, Location playerLocation, boolean force) {
        if (definition == null || !definition.objective().waypointEnabled() || !isTrackingEnabled(player)) return;
        long now = System.currentTimeMillis();
        long updateIntervalMillis = definition.objective().waypointUpdateIntervalTicks() * 50L;
        Long lastUpdate = lastWaypointAtMillis.get(player.getUniqueId());
        if (!force && lastUpdate != null && now - lastUpdate < updateIntervalMillis) return;
        lastWaypointAtMillis.put(player.getUniqueId(), now);

        Optional<RpgWorldManager.ResolvedSpawn> resolvedTarget = plugin.getRpgWorldManager().resolveSpawn(
            definition.objective().realmId());
        if (resolvedTarget.isEmpty()) return;
        Location target = resolvedTarget.get().location();
        String targetPosition = "§fX " + target.getBlockX() + " Y " + target.getBlockY()
            + " Z " + target.getBlockZ();
        if (playerLocation.getWorld() != target.getWorld()) {
            player.sendActionBar("§eĐi tới spawn " + target.getWorld().getName() + " §7(" + targetPosition + "§7)");
            return;
        }

        double dx = target.getX() - playerLocation.getX();
        double dy = target.getY() - playerLocation.getY();
        double dz = target.getZ() - playerLocation.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        player.sendActionBar("§eĐi tới spawn Nhân giới §7(" + targetPosition + "§7) §8• §f"
            + Math.round(Math.sqrt(dx * dx + dz * dz)) + " block");
        if (distance > definition.objective().waypointParticleRangeBlocks()) return;

        QuestWaypointRenderer.render(player, target,
            definition.objective().waypointParticleRangeBlocks(), definition.objective().targetMarkerEnabled());
    }

    private void completeObjective(Player player) {
        if (definition == null) return;
        UUID playerId = player.getUniqueId();
        TutorialQuestDefinitionLoader.TutorialQuestDefinition quest = definition;
        if (!pendingQuestCompletions.add(playerId)) {
            debugQuest("complete-skipped player=" + player.getName() + ", quest=" + quest.id()
                + ", reason=already-pending");
            return;
        }
        debugQuest("complete-start player=" + player.getName() + ", quest=" + quest.id()
            + ", rewardType=" + quest.reward().type() + ", rewardAmount=" + quest.reward().amount());
        Optional<PlayerProfileDatabaseManager.PlayerProfile> previousProfile =
            plugin.getCharacterSelectionManager().getLoadedProfile(playerId);
        plugin.getPlayerProfileDatabaseManager().executeGameplayDatabaseTask(
            "quest.complete player=" + player.getName() + " quest=" + quest.id(), () -> {
            boolean completed = "vncoin".equals(quest.reward().type())
                ? plugin.getPlayerProfileDatabaseManager().completeQuestAndAwardWallet(playerId.toString(),
                    quest.id(), "completed", quest.reward().amount(), Instant.now().toString())
                : plugin.getPlayerProfileDatabaseManager().completeQuestAndAwardExperience(playerId.toString(),
                    quest.id(), "completed", quest.reward().amount(), Instant.now().toString());
            if (completed) {
                PlayerProfileDatabaseManager.PlayerProfile updated = "character-exp".equals(quest.reward().type())
                    ? plugin.getPlayerProfileDatabaseManager().getPlayerProfile(playerId.toString()).orElse(null)
                    : null;
                return new QuestCompletionData(true, Optional.empty(), updated);
            }
            Optional<PlayerProfileDatabaseManager.QuestProgress> progress =
                plugin.getPlayerProfileDatabaseManager().getQuestProgress(playerId.toString(), quest.id());
            return new QuestCompletionData(false, progress, null);
        }).whenComplete((result, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            pendingQuestCompletions.remove(playerId);
            if (!player.isOnline()) return;
            if (failure != null) {
                plugin.getLogger().log(Level.SEVERE, "[Quest] Failed to complete tutorial objective for " +
                    player.getName(), failure);
                activeQuestSessions.put(playerId, true);
                player.sendMessage("§cChưa thể lưu tiến trình nhiệm vụ. Hãy rời khu vực rồi quay lại để thử lại.");
                return;
            }
            if (result.completed()) {
                debugQuest("complete-result player=" + player.getName() + ", quest=" + quest.id()
                    + ", completed=true, rewardType=" + quest.reward().type()
                    + ", rewardAmount=" + quest.reward().amount());
                activeQuestSessions.remove(playerId);
                lastWaypointAtMillis.remove(playerId);
                refreshQuestSidebar(player);
                CharacterSelectionManager characters = plugin.getCharacterSelectionManager();
                if (characters != null && result.updatedProfile() != null) {
                    characters.updateLoadedProfile(result.updatedProfile());
                }
                if ("character-exp".equals(quest.reward().type())) {
                    previousProfile.ifPresent(profile -> sendLevelUpFeedback(player, profile));
                }
                String rewardUnit = "vncoin".equals(quest.reward().type()) ? " VNCOIN" : " EXP";
                player.sendTitle("§6§lNHIỆM VỤ HOÀN THÀNH",
                    "§e" + quest.title() + " §8• §6+" + quest.reward().amount() + rewardUnit,
                    10, 70, 20);
                player.sendMessage("§a§lNhiệm vụ hoàn thành: §f" + quest.title());
                player.sendMessage("§7" + quest.objective().completeMessage());
                player.sendMessage("§ePhần thưởng: +" + quest.reward().amount()
                    + ("vncoin".equals(quest.reward().type()) ? " VNCOIN." : " EXP nhân vật."));
                return;
            }
            if (result.progress().isPresent() && "completed".equals(result.progress().get().status())) {
                debugQuest("complete-result player=" + player.getName() + ", quest=" + quest.id()
                    + ", completed=false, persistedStatus=completed");
                activeQuestSessions.remove(playerId);
                lastWaypointAtMillis.remove(playerId);
                return;
            }
            plugin.getLogger().warning("[Quest] Could not complete tutorial objective for " +
                player.getName() + "; the active progress remains available.");
            debugQuest("complete-result player=" + player.getName() + ", quest=" + quest.id()
                + ", completed=false, status="
                + result.progress().map(PlayerProfileDatabaseManager.QuestProgress::status).orElse("missing"));
            activeQuestSessions.put(playerId, true);
            player.sendMessage("§cChưa thể lưu tiến trình nhiệm vụ. Hãy rời khu vực rồi quay lại để thử lại.");
        }));
    }

    private record QuestCompletionData(boolean completed,
                                       Optional<PlayerProfileDatabaseManager.QuestProgress> progress,
                                       PlayerProfileDatabaseManager.PlayerProfile updatedProfile) {
    }

    private void debugQuest(String detail) {
        if (plugin.getConfig().getBoolean("logging.console-debug", false)) {
            plugin.getLogger().info("[RPG-DEBUG][Quest] " + detail);
        }
    }

    private void sendLevelUpFeedback(Player player,
                                     PlayerProfileDatabaseManager.PlayerProfile previousProfile) {
        Optional<PlayerProfileDatabaseManager.PlayerProfile> updatedProfile =
            plugin.getCharacterSelectionManager().getLoadedProfile(player.getUniqueId());
        if (updatedProfile.isEmpty()) return;

        PlayerProfileDatabaseManager.PlayerProfile currentProfile = updatedProfile.get();
        int levelsGained = currentProfile.level() - previousProfile.level();
        if (levelsGained <= 0) return;

        player.sendMessage("§b§lBạn đã lên cấp " + currentProfile.level() + "!");
        int pointsGained = currentProfile.unspentSkillPoints() - previousProfile.unspentSkillPoints();
        if (pointsGained > 0) {
            player.sendMessage("§d+" + pointsGained + " skill point §7(đang có " +
                currentProfile.unspentSkillPoints() + ").");
        }
    }

    private boolean isSameBlock(Location first, Location second) {
        return first.getWorld() == second.getWorld()
            && first.getBlockX() == second.getBlockX()
            && first.getBlockY() == second.getBlockY()
            && first.getBlockZ() == second.getBlockZ();
    }

    private static final class QuestJournalHolder implements InventoryHolder {
        private final UUID viewer;
        private final boolean hasActiveProfile;
        private final Optional<PlayerProfileDatabaseManager.QuestProgress> progress;
        private Inventory inventory;

        private QuestJournalHolder(UUID viewer, boolean hasActiveProfile,
                                   Optional<PlayerProfileDatabaseManager.QuestProgress> progress) {
            this.viewer = viewer;
            this.hasActiveProfile = hasActiveProfile;
            this.progress = progress;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
