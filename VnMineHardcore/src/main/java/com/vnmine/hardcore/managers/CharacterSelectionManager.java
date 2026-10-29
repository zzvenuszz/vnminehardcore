package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.yaml.snakeyaml.error.YAMLException;

public final class CharacterSelectionManager implements Listener {
    private static final Pattern DEFINITION_ID_PATTERN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final int[] MENU_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    private final VnMineHardcore plugin;
    private final NamespacedKey onboardingStageKey;
    private final NamespacedKey onboardingContinentKey;
    private final NamespacedKey starterBookPendingKey;
    private final NamespacedKey starterTutorialPendingKey;
    private final NamespacedKey startTutorialVisitedKey;
    private final File starterBookMappingsFile;
    private final File pendingJourneyResetsFile;
    private final Set<UUID> pendingJourneyResets = new java.util.HashSet<>();
    private final Map<String, RaceDefinition> races = new LinkedHashMap<>();
    private final Map<String, List<ClassDefinition>> classesByRace = new HashMap<>();
    private final Map<String, String> parentClassById = new LinkedHashMap<>();
    private final Map<String, Integer> promotionLevelByClass = new LinkedHashMap<>();
    private final Map<String, String> promotionQuestByClass = new LinkedHashMap<>();
    private final Map<String, String> promotionNpcByClass = new LinkedHashMap<>();
    private final Map<String, Material> promotionItemByClass = new LinkedHashMap<>();
    private final Map<String, Integer> promotionItemAmountByClass = new LinkedHashMap<>();
    private final Map<String, String> promotionRpgItemByClass = new LinkedHashMap<>();
    private final Map<String, Integer> promotionRpgItemAmountByClass = new LinkedHashMap<>();
    private final Map<String, String> starterBookMappings = new LinkedHashMap<>();
    private final Map<String, String> raceOnboardingQuestIds = new LinkedHashMap<>();
    private final Map<UUID, PlayerProfileDatabaseManager.PlayerProfile> loadedProfiles = new HashMap<>();
    private final Set<UUID> profileLookupComplete = new java.util.HashSet<>();
    private final Set<UUID> pendingProfileCreates = new java.util.HashSet<>();
    private final Set<UUID> pendingStartNpcInteractions = new java.util.HashSet<>();
    private Set<String> playableClassIds = Set.of();
    private File raceDefinitionsFile;
    private boolean definitionsAvailable;
    private BukkitTask onboardingNavigationTask;
    private long onboardingTick;
    private long lastNavigationRunTick;
    private final Map<UUID, Long> lastCrossWorldHintTick = new HashMap<>();

    public CharacterSelectionManager(VnMineHardcore plugin) {
        this.plugin = plugin;
        this.onboardingStageKey = new NamespacedKey(plugin, "onboarding_stage");
        this.onboardingContinentKey = new NamespacedKey(plugin, "onboarding_continent");
        this.starterBookPendingKey = new NamespacedKey(plugin, "starter_book_pending");
        this.starterTutorialPendingKey = new NamespacedKey(plugin, "starter_tutorial_pending");
        this.startTutorialVisitedKey = new NamespacedKey(plugin, "start_tutorial_visited");
        this.starterBookMappingsFile = new File(plugin.getDataFolder(), "starter-skill-books.yml");
        this.pendingJourneyResetsFile = new File(plugin.getDataFolder(), "pending-onboarding-resets.yml");
        try {
            loadDefinitions();
            loadStarterBookMappings();
            loadPendingJourneyResets();
            definitionsAvailable = true;
        } catch (IllegalStateException exception) {
            races.clear();
            classesByRace.clear();
            plugin.getLogger().log(Level.SEVERE,
                "[Character] Character creation is disabled because its race/class configuration is invalid.",
                exception);
        }
        onboardingNavigationTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickOnboardingNavigation, 20L, 10L);
    }

    public void openRaceMenu(Player player) {
        if (!isCreationAvailable(player)) return;
        if (hasExistingProfile(player)) return;
        if (isOnboardingEnabled() && !"ready-for-character".equals(onboardingStage(player))) {
            continueOnboarding(player);
            return;
        }
        CharacterMenuHolder holder = new CharacterMenuHolder(player.getUniqueId(), "race", null, null, new HashMap<>());
        Inventory inventory = Bukkit.createInventory(holder, 54, "VnMine • Chọn chủng tộc");
        holder.inventory = inventory;
        int index = 0;
        for (RaceDefinition race : races.values()) {
            String realmId = onboardingContinent(player);
            if (realmId != null && !isRaceEnabledForRealm(realmId, race.id())) continue;
            if (classesByRace.getOrDefault(race.id(), Collections.emptyList()).stream()
                    .noneMatch(klass -> isClassSelectable(klass)
                        && (realmId == null || isClassEnabledForRealm(realmId, race.id(), klass.id())))) {
                continue;
            }
            if (index >= MENU_SLOTS.length) break;
            int slot = MENU_SLOTS[index++];
            holder.choices.put(slot, race.id());
            inventory.setItem(slot, createRaceItem(race));
        }
        inventory.setItem(49, createActionItem(Material.BARRIER, "§cĐóng", List.of("§7Chưa có thay đổi nào được lưu.")));
        player.openInventory(inventory);
    }

    public Set<String> getClassIds() {
        return classesByRace.values().stream()
            .flatMap(List::stream)
            .map(ClassDefinition::id)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public List<RaceDefinition> getRaceDefinitions() {
        return races.values().stream()
            .filter(race -> classesByRace.getOrDefault(race.id(), List.of()).stream().anyMatch(this::isClassSelectable))
            .sorted(Comparator.comparing(RaceDefinition::name, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    /** All valid race definitions, including races whose classes are not selectable yet. */
    public List<RaceDefinition> getConfiguredRaceDefinitions() {
        return races.values().stream()
            .sorted(Comparator.comparing(RaceDefinition::name, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    public List<ClassDefinition> getClassDefinitions(String raceId) {
        return classesByRace.getOrDefault(raceId, List.of()).stream()
            .sorted(Comparator.comparing(ClassDefinition::name, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    /** Returns this class and configured ancestors, nearest parent first. */
    public Set<String> getInheritedClassIds(String classId) {
        if (classId == null || classId.isBlank()) return Set.of();
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        String current = classId;
        while (current != null && result.add(current)) current = parentClassById.get(current);
        return Set.copyOf(result);
    }

    /** Returns classes that may use skills originally defined by the supplied class. */
    public Set<String> getClassesInheritingFrom(String classId) {
        if (classId == null || classId.isBlank()) return Set.of();
        return getClassIds().stream().filter(candidate -> getInheritedClassIds(candidate).contains(classId))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public List<ClassDefinition> getPromotionCandidates(String currentClassId) {
        if (currentClassId == null || currentClassId.isBlank()) return List.of();
        return classesByRace.values().stream().flatMap(List::stream)
            .filter(candidate -> currentClassId.equals(parentClassById.get(candidate.id())))
            .filter(candidate -> ("available".equals(candidate.availability())
                || "auto".equals(candidate.availability())) && hasPlayableSkillInLineage(candidate.id()))
            .sorted(java.util.Comparator.comparing(ClassDefinition::name, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    public int getPromotionRequiredLevel(String classId) {
        return promotionLevelByClass.getOrDefault(classId, 1);
    }

    public String getPromotionRequiredQuest(String classId) {
        return promotionQuestByClass.get(classId);
    }

    public String getPromotionRequiredNpc(String classId) {
        return promotionNpcByClass.get(classId);
    }

    public Material getPromotionRequiredItem(String classId) {
        return promotionItemByClass.get(classId);
    }

    public int getPromotionRequiredItemAmount(String classId) {
        return promotionItemAmountByClass.getOrDefault(classId, 0);
    }

    public String getPromotionRequiredRpgItem(String classId) {
        return promotionRpgItemByClass.get(classId);
    }

    public int getPromotionRequiredRpgItemAmount(String classId) {
        return promotionRpgItemAmountByClass.getOrDefault(classId, 0);
    }

    public void setPlayableClassIds(Set<String> classIds) {
        playableClassIds = Set.copyOf(classIds);
        validateStarterBookMappings();
        for (String classId : getClassIds()) {
            ClassDefinition definition = findClassForId(classId);
            if (definition != null && "available".equals(definition.availability())
                    && !hasPlayableSkillInLineage(classId)) {
                plugin.getLogger().warning("[Character] Class '" + classId
                    + "' is marked available but hidden because no implemented RPG skill supports it.");
            } else if (definition != null && !"available".equals(definition.availability())) {
                plugin.getLogger().info("[Character] Class '" + classId + "' is " + definition.availability()
                    + " and hidden from character creation.");
            }
        }
    }

    private boolean hasPlayableSkillInLineage(String classId) {
        return getInheritedClassIds(classId).stream().anyMatch(playableClassIds::contains);
    }

    public void openClassMenu(Player player, String raceId) {
        if (!isCreationAvailable(player)) return;
        if (hasExistingProfile(player)) return;
        RaceDefinition race = races.get(raceId);
        if (race == null) {
            player.sendMessage("§cChủng tộc không hợp lệ.");
            return;
        }

        CharacterMenuHolder holder = new CharacterMenuHolder(
            player.getUniqueId(), "class", raceId, null, new HashMap<>());
        Inventory inventory = Bukkit.createInventory(holder, 54, "VnMine • Chọn class cho " + race.name());
        holder.inventory = inventory;
        List<ClassDefinition> classList = classesByRace.getOrDefault(raceId, Collections.emptyList()).stream()
            .filter(this::isClassSelectable)
            .filter(klass -> onboardingContinent(player) == null
                || isClassEnabledForRealm(onboardingContinent(player), raceId, klass.id()))
            .toList();
        if (classList.isEmpty()) {
            player.sendMessage("§eChủng tộc này chưa có class RPG khả dụng. Hãy chọn chủng tộc khác.");
            return;
        }
        for (int index = 0; index < classList.size() && index < MENU_SLOTS.length; index++) {
            ClassDefinition klass = classList.get(index);
            int slot = MENU_SLOTS[index];
            holder.choices.put(slot, klass.id());
            inventory.setItem(slot, createClassItem(race, klass));
        }
        inventory.setItem(49, createActionItem(Material.ARROW, "§7Chọn lại chủng tộc",
            List.of("§7Quay lại màn hình chủng tộc.")));
        player.openInventory(inventory);
    }

    private boolean isRaceEnabledForRealm(String realmId, String raceId) {
        var yaml = loadRealmYaml(realmId);
        return !yaml.getBoolean("character-availability.enabled", false)
            || yaml.getStringList("character-availability.races").contains(raceId);
    }

    private boolean isClassEnabledForRealm(String realmId, String raceId, String classId) {
        var yaml = loadRealmYaml(realmId);
        return !yaml.getBoolean("character-availability.enabled", false)
            || yaml.getStringList("character-availability.classes." + raceId).contains(classId);
    }

    private org.bukkit.configuration.file.YamlConfiguration loadRealmYaml(String realmId) {
        realmId = plugin.getRpgWorldManager().resolveRealmId(realmId);
        File folder = new File(plugin.getDataFolder(), "worlds");
        File[] files = folder.listFiles((directory, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".yml"));
        if (files != null) for (File file : files) {
            var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
            if (realmId.equals(yaml.getString("id"))) return yaml;
        }
        return new org.bukkit.configuration.file.YamlConfiguration();
    }

    private void openConfirmationMenu(Player player, String raceId, String classId) {
        if (!isCreationAvailable(player)) return;
        RaceDefinition race = races.get(raceId);
        ClassDefinition selectedClass = findClass(raceId, classId);
        if (race == null || !isClassSelectable(selectedClass)) {
            player.sendMessage("§cKhông tìm thấy lựa chọn race/class hợp lệ.");
            return;
        }
        CharacterMenuHolder holder = new CharacterMenuHolder(
            player.getUniqueId(), "confirm", raceId, classId, new HashMap<>());
        Inventory inventory = Bukkit.createInventory(holder, 54, "VnMine • Xác nhận nhân vật");
        holder.inventory = inventory;
        String starterBookId = starterBookMappings.get(starterBookKey(raceId, classId));
        inventory.setItem(11, createActionItem(Material.LIME_CONCRETE, "§aXác nhận tạo nhân vật", List.of(
            "§7Chủng tộc: §6" + race.name(),
            "§7Class: §b" + selectedClass.name(),
            "§7Sách skill đầu tiên: §e" + (starterBookId == null ? "Chưa được cấu hình" : starterBookId),
            "§eMỗi tài khoản chỉ tạo được một nhân vật.")));
        inventory.setItem(13, createActionItem(Material.BOOK, "§fXem lại lựa chọn", List.of(
            "§7" + race.description(),
            "§7" + selectedClass.description())));
        inventory.setItem(15, createActionItem(Material.RED_CONCRETE, "§cHủy", List.of(
            "§7Quay lại chọn class.")));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onCharacterMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof CharacterMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!holder.viewer.equals(player.getUniqueId())) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }

        if ("profile".equals(holder.type)) {
            if (slot == 11 && plugin.getEconomyManager() != null) {
                plugin.getEconomyManager().openWalletMenu(player);
            } else if (slot == 15 && plugin.getTutorialQuestManager() != null) {
                plugin.getTutorialQuestManager().openJournal(player);
            } else if (slot == 17 && plugin.getQuestCatalogManager() != null) {
                plugin.getQuestCatalogManager().openPlayerJournal(player);
            } else if (slot == 22 && plugin.getMercenaryManager() != null) {
                plugin.getMercenaryManager().openMainMenu(player);
            }
            return;
        }

        if ("continent".equals(holder.type)) {
            String continentId = holder.choices.get(slot);
            if (continentId != null) selectStartingContinent(player, continentId);
            else if (slot == 49) player.closeInventory();
            return;
        }

        if (holder.type.startsWith("starter-")) {
            if (!player.hasPermission("vnmine.command.skillbook.admin")) {
                player.sendMessage("§cBạn không có quyền quản lý sách skill khởi đầu.");
                return;
            }
            handleStarterBookMenuClick(player, holder, slot);
            return;
        }

        if ("admin-races".equals(holder.type)) {
            if (!hasRaceAdminPermission(player)) {
                player.sendMessage("§cBạn không có quyền quản lý race.");
                return;
            }
            if (slot == 49) {
                if (plugin.getMercenaryManager() != null) plugin.getMercenaryManager().openAdminMenu(player);
            } else {
                String raceId = holder.choices.get(slot);
                if (raceId != null) openRaceQuestChoices(player, raceId, 0);
            }
            return;
        }

        if ("race-quest-choices".equals(holder.type)) {
            if (!hasRaceAdminPermission(player)) {
                player.sendMessage("§cBạn không có quyền quản lý race.");
                return;
            }
            if (slot == 49) openRaceAdminMenu(player);
            else if (slot == 45) saveRaceQuestSelection(player, holder.raceId, null);
            else if (slot == 46 && holder.page > 0)
                openRaceQuestChoices(player, holder.raceId, holder.page - 1);
            else if (slot == 50) openRaceQuestChoices(player, holder.raceId, holder.page + 1);
            else {
                String questId = holder.choices.get(slot);
                if (questId != null) saveRaceQuestSelection(player, holder.raceId, questId);
            }
            return;
        }

        if ("confirm".equals(holder.type)) {
            if (slot == 11) {
                finalizeSelection(player, holder.raceId, holder.classId);
            } else if (slot == 15) {
                openClassMenu(player, holder.raceId);
            }
            return;
        }

        if (slot == 49) {
            if ("class".equals(holder.type)) {
                openRaceMenu(player);
            } else {
                player.closeInventory();
            }
            return;
        }

        String choice = holder.choices.get(slot);
        if (choice == null) {
            return;
        }

        if ("race".equals(holder.type)) {
            openClassMenu(player, choice);
            return;
        }

        if ("class".equals(holder.type)) {
            openConfirmationMenu(player, holder.raceId, choice);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        debugProfile("load-start player=" + player.getName() + ", source=join");
        String playerName = player.getName();
        plugin.getPlayerProfileDatabaseManager()
            .executeGameplayDatabaseTask("profile.load.join player=" + playerName, () ->
                plugin.getPlayerProfileDatabaseManager().getPlayerProfile(playerId.toString()))
            .whenComplete((profile, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (pendingJourneyResets.remove(playerId)) {
                    savePendingJourneyResets();
                    resetQuestJourney(player);
                }
                if (failure != null) {
                    plugin.getLogger().log(Level.SEVERE, "[Profile] Failed to load character profile for " +
                        player.getName(), failure);
                    if (player.isOnline()) {
                        player.sendMessage("§cKhông thể tải hồ sơ nhân vật. Dữ liệu của bạn vẫn được giữ nguyên.");
                    }
                    return;
                }
                profileLookupComplete.add(playerId);
                debugProfile("load-result player=" + playerName + ", source=join, found=" + profile.isPresent());
                if (profile.isEmpty()) {
                    loadedProfiles.remove(playerId);
                    if (onboardingStage(player) == null || "needs-continent".equals(onboardingStage(player))
                            || "awaiting-tutorial".equals(onboardingStage(player))) {
                        setOnboarding(player, "awaiting-start-tutorial", null);
                        if (pendingStartNpcInteractions.remove(playerId)
                                && player.getWorld().getName().equalsIgnoreCase(plugin.getConfig()
                                    .getString("rpg.onboarding.start-world.name", "world_start"))) {
                            recordStartWorldTutorialNpcVisit(player,
                                plugin.getConfig().getString("rpg.onboarding.start-world.tutorial-npc-id", ""));
                        } else teleportToStartWorld(player, true);
                    } else continueOnboarding(player);
                    return;
                }
                PlayerProfileDatabaseManager.PlayerProfile loadedProfile = profile.get();
                loadedProfiles.put(playerId, loadedProfile);
                if (pendingStartNpcInteractions.remove(playerId)) {
                    player.sendMessage("§eHồ sơ RPG đã tồn tại nên NPC Tutorial không mở luồng tạo nhân vật mới.");
                }
                if ("needs-continent".equals(onboardingStage(player))) {
                    player.sendMessage("§eTiến trình quest đã được đặt lại. Dùng §f/start §eđể quay lại hành trình hướng dẫn.");
                } else if (!"complete".equals(onboardingStage(player))) setOnboarding(player, "complete", null);
                if (player.getPersistentDataContainer().getOrDefault(starterBookPendingKey,
                        PersistentDataType.BYTE, (byte) 0) == 1) {
                    queueStarterBookGrant(player, loadedProfile.selectedRace(), loadedProfile.selectedClass());
                } else if (isStarterTutorialPending(player)) {
                    reconcileStarterBookDelivery(player);
                }
                if (loadedProfile.selectedRace() == null || loadedProfile.selectedClass() == null) {
                    debugProfile("incomplete-profile player=" + player.getName() + ", reopening character flow");
                } else if (!races.containsKey(loadedProfile.selectedRace())
                        || findClass(loadedProfile.selectedRace(), loadedProfile.selectedClass()) == null) {
                    plugin.getLogger().warning("[Profile] Stored race/class definition is missing for player " +
                        player.getName() + "; profile data was preserved.");
                }
                if (loadedProfile.selectedRace() == null || loadedProfile.selectedClass() == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) openRaceMenu(player);
                    });
                }
            }));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        loadedProfiles.remove(playerId);
        profileLookupComplete.remove(playerId);
        lastCrossWorldHintTick.remove(playerId);
        pendingStartNpcInteractions.remove(playerId);
    }

    public Optional<PlayerProfileDatabaseManager.PlayerProfile> getLoadedProfile(UUID playerId) {
        return Optional.ofNullable(loadedProfiles.get(playerId));
    }

    public void updateLoadedProfile(PlayerProfileDatabaseManager.PlayerProfile profile) {
        if (profile != null) {
            loadedProfiles.put(UUID.fromString(profile.uuid()), profile);
        }
    }

    public void openCharacterMenu(Player player) {
        if (!plugin.getConfig().getBoolean("rpg.character-creation.enabled", true)) {
            player.sendMessage("§cTính năng nhân vật RPG hiện đang tắt.");
            return;
        }
        PlayerProfileDatabaseManager database = plugin.getPlayerProfileDatabaseManager();
        if (database == null) {
            player.sendMessage("§cHệ thống hồ sơ chưa sẵn sàng.");
            return;
        }
        UUID playerId = player.getUniqueId();
        debugProfile("menu-open-request player=" + player.getName());
        database.executeGameplayDatabaseTask("profile.load.menu player=" + player.getName(),
                () -> database.getPlayerProfile(playerId.toString()))
            .whenComplete((profile, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (failure != null) {
                    plugin.getLogger().log(Level.SEVERE, "[Profile] Failed to open character menu for " +
                        player.getName(), failure);
                    player.sendMessage("§cKhông thể tải hồ sơ nhân vật. Dữ liệu của bạn vẫn được giữ nguyên.");
                    return;
                }
                profileLookupComplete.add(playerId);
                debugProfile("menu-open-result player=" + player.getName() + ", found=" + profile.isPresent());
                if (profile.isPresent()) {
                    loadedProfiles.put(playerId, profile.get());
                    openProfileMenu(player, profile.get());
                } else {
                    loadedProfiles.remove(playerId);
                    continueOnboarding(player);
                }
            }));
    }

    private boolean isOnboardingEnabled() {
        return plugin.getConfig().getBoolean("rpg.onboarding.enabled", true);
    }

    private String onboardingStage(Player player) {
        return player.getPersistentDataContainer().get(onboardingStageKey, PersistentDataType.STRING);
    }

    private String onboardingContinent(Player player) {
        return player.getPersistentDataContainer().get(onboardingContinentKey, PersistentDataType.STRING);
    }

    public String getOnboardingRealm(Player player) {
        return onboardingContinent(player);
    }

    public boolean isWaitingForJourneyStart(Player player) {
        return pendingJourneyResets.contains(player.getUniqueId())
            || "needs-continent".equals(onboardingStage(player))
            || "awaiting-start-tutorial".equals(onboardingStage(player))
            || "choosing-continent".equals(onboardingStage(player));
    }

    public boolean isStarterTutorialPending(Player player) {
        return player.getPersistentDataContainer().getOrDefault(starterTutorialPendingKey,
            PersistentDataType.BYTE, (byte) 0) == 1;
    }

    public boolean hasVisitedStartWorldTutorial(Player player) {
        return player.getPersistentDataContainer().getOrDefault(startTutorialVisitedKey,
            PersistentDataType.BYTE, (byte) 0) != 0;
    }

    public boolean shouldShowTutorialMarker(Player player, String npcId) {
        if (!isOnboardingEnabled() || !"awaiting-tutorial".equals(onboardingStage(player))) return false;
        String continentId = onboardingContinent(player);
        HumanRealmDefinitionLoader.HumanRealmDefinition realm = continentId == null
            || plugin.getRpgWorldManager() == null ? null
            : plugin.getRpgWorldManager().getRealmDefinition(continentId);
        return realm != null && realm.tutorialNpcId().equalsIgnoreCase(npcId);
    }

    private void setOnboarding(Player player, String stage, String continentId) {
        if (continentId == null || continentId.isBlank()) {
            player.getPersistentDataContainer().remove(onboardingContinentKey);
        } else {
            player.getPersistentDataContainer().set(onboardingContinentKey, PersistentDataType.STRING, continentId);
        }
        player.getPersistentDataContainer().set(onboardingStageKey, PersistentDataType.STRING, stage);
        player.saveData();
        BossLeaderboardManager leaderboard = plugin.getBossLeaderboardManager();
        if (leaderboard != null) leaderboard.refreshPlayerBoard(player);
        debugProfile("onboarding-update player=" + player.getName() + ", stage=" + stage
            + ", continent=" + (continentId == null ? "none" : continentId));
    }

    public List<String> getOnboardingSidebarLines(Player player) {
        if (!isOnboardingEnabled() || "complete".equals(onboardingStage(player))) return List.of();
        String stage = onboardingStage(player);
        if ("awaiting-start-tutorial".equals(stage)) {
            String npcId = plugin.getConfig().getString("rpg.onboarding.start-world.tutorial-npc-id", "");
            String world = plugin.getConfig().getString("rpg.onboarding.start-world.name", "world_start");
            return List.of("§d§lNHIỆM VỤ", "§fBước chân đầu tiên: 0/1",
                "§7Gặp NPC Tutorial: 0/1", "§8" + (npcId.isBlank() ? "NPC chưa cấu hình" : npcId) + " • " + world);
        }
        if ("choosing-continent".equals(stage)) {
            return List.of("§d§lNHIỆM VỤ", "§fBước chân đầu tiên: 1/1", "§7Chọn đại lục khởi đầu");
        }
        if ("awaiting-tutorial".equals(stage)) {
            String continentId = onboardingContinent(player);
            HumanRealmDefinitionLoader.HumanRealmDefinition realm = continentId == null
                || plugin.getRpgWorldManager() == null ? null
                : plugin.getRpgWorldManager().getRealmDefinition(continentId);
            if (realm != null) return List.of("§d§lNHIỆM VỤ", "§fBước chân đầu tiên: 0/1",
                "§7Gặp NPC Tutorial: 0/1", "§8" + realm.tutorialNpcId() + " • " + realm.worldName());
        }
        if ("ready-for-character".equals(stage)) {
            return List.of("§d§lNHIỆM VỤ", "§fBước chân đầu tiên: 1/1", "§7Chọn race và class: 0/1");
        }
        return List.of("§d§lNHIỆM VỤ", "§fBước chân đầu tiên: 0/1", "§7Gặp NPC Tutorial tại world bắt đầu");
    }

    private void continueOnboarding(Player player) {
        if (!isOnboardingEnabled()) {
            openRaceMenu(player);
            return;
        }
        String stage = onboardingStage(player);
        if ("complete".equals(stage)) {
            // Recover if the profile database was restored without this player's profile.
            setOnboarding(player, "needs-continent", null);
            stage = "needs-continent";
        }
        if ("needs-continent".equals(stage) || stage == null) {
            setOnboarding(player, "awaiting-start-tutorial", null);
            stage = "awaiting-start-tutorial";
        }
        if ("ready-for-character".equals(stage)) {
            openRaceMenu(player);
            return;
        }
        if ("choosing-continent".equals(stage)) {
            openContinentMenu(player);
            return;
        }
        if ("awaiting-start-tutorial".equals(stage)) {
            teleportToStartWorld(player, false);
            player.sendMessage("§eHãy gặp NPC Tutorial tại world bắt đầu để chọn đại lục và chủng tộc.");
            return;
        }
        if ("awaiting-tutorial".equals(stage) && onboardingContinent(player) != null) {
            player.sendMessage("§eHãy tìm NPC hướng dẫn của đại lục bạn đã chọn.");
            return;
        }
        player.sendMessage("§eHãy gặp NPC Tutorial ở world bắt đầu để tiếp tục hành trình RPG.");
    }

    private void teleportToStartWorld(Player player, boolean welcome) {
        String worldName = plugin.getConfig().getString("rpg.onboarding.start-world.name", "world_start");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage("§cWorld bắt đầu §f" + worldName + " §cchưa được nạp. Hãy báo quản trị viên.");
            plugin.getLogger().warning("[Onboarding] Start world unavailable: player=" + player.getName()
                + ", world=" + worldName);
            return;
        }
        player.teleportAsync(world.getSpawnLocation()).whenComplete((success, failure) ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (failure != null || !Boolean.TRUE.equals(success)) {
                    plugin.getLogger().warning("[Onboarding] Could not send player=" + player.getName()
                        + " to start-world=" + worldName + (failure == null ? "" : ": " + failure.getMessage()));
                    player.sendMessage("§cKhông thể dịch chuyển tới world bắt đầu. Hãy báo quản trị viên.");
                    return;
                }
                if (welcome) player.sendMessage("§eChào mừng! Hãy tìm NPC Tutorial để bắt đầu hành trình RPG.");
                debugProfile("start-world-arrival player=" + player.getName() + ", world=" + worldName);
            }));
    }

    /** Starts or resumes the explicit realm-selection flow. */
    public void startJourney(Player player) {
        if (!isOnboardingEnabled()) {
            openCharacterMenu(player);
            return;
        }
        if (!profileLookupComplete.contains(player.getUniqueId())) {
            player.sendMessage("§eĐang tải hồ sơ người chơi. Hãy thử lại sau vài giây.");
            return;
        }
        if (loadedProfiles.containsKey(player.getUniqueId())
                && !"needs-continent".equals(onboardingStage(player))
                && !"awaiting-tutorial".equals(onboardingStage(player))) {
            player.sendMessage("§eBạn đã hoàn tất phần khởi đầu RPG rồi.");
            return;
        }
        String stage = onboardingStage(player);
        if ("awaiting-start-tutorial".equals(stage) || "awaiting-tutorial".equals(stage)) {
            continueOnboarding(player);
            return;
        }
        if ("choosing-continent".equals(stage)) {
            openContinentMenu(player);
            return;
        }
        if ("ready-for-character".equals(stage)) {
            openRaceMenu(player);
            return;
        }
        setOnboarding(player, "awaiting-start-tutorial", null);
        teleportToStartWorld(player, false);
    }

    /** Used by the admin reset flow; this intentionally never initializes or rewards a quest. */
    public void resetQuestJourney(Player player) {
        if (player == null || !player.isOnline()) return;
        if (plugin.getTutorialQuestManager() != null)
            plugin.getTutorialQuestManager().clearSessionAfterAdminQuestReset(player);
        setOnboarding(player, "awaiting-start-tutorial", null);
        player.getPersistentDataContainer().remove(startTutorialVisitedKey);
        player.getPersistentDataContainer().remove(starterBookPendingKey);
        player.getPersistentDataContainer().remove(starterTutorialPendingKey);
        player.saveData();
        String lobbyName = plugin.getConfig().getString("rpg.onboarding.start-world.name", "world_start");
        World lobby = Bukkit.getWorld(lobbyName);
        if (lobby == null) {
            player.sendMessage("§cQuest đã được reset. World §f" + lobbyName
                + " §cchưa được tạo/nạp; hãy khởi động lại server sau khi cấu hình /vninit.");
            plugin.getLogger().warning("[Onboarding] Reset target world is unavailable: player="
                + player.getName() + ", world=" + lobbyName);
            return;
        }
        player.teleportAsync(lobby.getSpawnLocation()).whenComplete((success, failure) ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (failure != null || !Boolean.TRUE.equals(success)) {
                    plugin.getLogger().warning("[Onboarding] Could not teleport reset player " + player.getName()
                        + " to " + lobbyName + (failure == null ? "" : ": " + failure.getMessage()));
                    return;
                }
                player.sendMessage("§aTiến trình quest đã reset. Hãy gặp NPC Tutorial trong world bắt đầu.");
                debugProfile("admin-quest-reset-return player=" + player.getName() + ", world=" + lobbyName);
            }));
    }

    public void queueQuestJourneyReset(UUID playerId) {
        if (playerId == null) return;
        pendingJourneyResets.add(playerId);
        savePendingJourneyResets();
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            pendingJourneyResets.remove(playerId);
            savePendingJourneyResets();
            resetQuestJourney(online);
        }
    }

    private void loadPendingJourneyResets() {
        YamlConfiguration file = YamlConfiguration.loadConfiguration(pendingJourneyResetsFile);
        for (String raw : file.getStringList("players")) {
            try { pendingJourneyResets.add(UUID.fromString(raw)); }
            catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("[Onboarding] Ignoring invalid UUID in pending-onboarding-resets.yml: " + raw);
            }
        }
    }

    private void savePendingJourneyResets() {
        YamlConfiguration file = new YamlConfiguration();
        file.set("players", pendingJourneyResets.stream().map(UUID::toString).sorted().toList());
        try {
            File parent = pendingJourneyResetsFile.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs())
                throw new IOException("Could not create plugin data directory");
            file.save(pendingJourneyResetsFile);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "[Onboarding] Could not persist pending player resets.", exception);
        }
    }

    private void openContinentMenu(Player player) {
        if (plugin.getRpgWorldManager() == null || plugin.getNpcManager() == null) {
            player.sendMessage("§cDanh sách đại lục chưa sẵn sàng. Hãy báo quản trị viên.");
            return;
        }
        CharacterMenuHolder holder = new CharacterMenuHolder(player.getUniqueId(),
            "continent", null, null, new HashMap<>());
        Inventory inventory = Bukkit.createInventory(holder, 54, "VnMine • Chọn đại lục khởi đầu");
        holder.inventory = inventory;
        int index = 0;
        for (HumanRealmDefinitionLoader.HumanRealmDefinition realm
                : plugin.getRpgWorldManager().getDefinitions().values()) {
            if (!isStartingRealmAvailable(realm)) continue;
            if (index >= MENU_SLOTS.length) break;
            int slot = MENU_SLOTS[index++];
            holder.choices.put(slot, realm.id());
            inventory.setItem(slot, createActionItem(Material.END_PORTAL_FRAME, "§6" + realm.displayName(), List.of(
                "§7World: §f" + realm.worldName(),
                "§7Sẽ lọc race/class theo cấu hình đại lục.",
                "", "§aNhấn để chọn đại lục này.")));
        }
        inventory.setItem(49, createActionItem(Material.BARRIER, "§cĐóng", List.of(
            "§7Bạn cần chọn đại lục để bắt đầu.")));
        if (holder.choices.isEmpty()) {
            player.sendMessage("§cChưa có đại lục khởi đầu sẵn sàng. Hãy báo quản trị viên kiểm tra world và NPC tutorial.");
            plugin.getLogger().warning("[Onboarding] No valid starting realm is available for " + player.getName() + ".");
            return;
        }
        player.openInventory(inventory);
    }

    private boolean isStartingRealmAvailable(HumanRealmDefinitionLoader.HumanRealmDefinition realm) {
        if (realm == null || !realm.startingSelectable()) return false;
        var spawn = plugin.getRpgWorldManager().resolveSpawn(realm.id());
        boolean available = spawn.isPresent() && spawn.get().location().getWorld() != null
            && spawn.get().location().getWorld().getName().equals(realm.worldName())
            && races.values().stream().anyMatch(race -> isRaceEnabledForRealm(realm.id(), race.id())
                && classesByRace.getOrDefault(race.id(), List.of()).stream().anyMatch(klass ->
                    isClassSelectable(klass) && isClassEnabledForRealm(realm.id(), race.id(), klass.id())));
        if (!available) {
            plugin.getLogger().warning("[Onboarding] Realm '" + realm.id()
                + "' is unavailable: require loaded world '" + realm.worldName()
                + "' and a valid configured spawn.");
        }
        return available;
    }

    private void selectStartingContinent(Player player, String continentId) {
        if (!profileLookupComplete.contains(player.getUniqueId())
                || (onboardingStage(player) != null && !"choosing-continent".equals(onboardingStage(player))
                    && !"needs-continent".equals(onboardingStage(player)))) {
            player.sendMessage("§eTrạng thái onboarding đã thay đổi; hãy mở lại menu nhân vật nếu cần.");
            return;
        }
        HumanRealmDefinitionLoader.HumanRealmDefinition realm = plugin.getRpgWorldManager() == null ? null
            : plugin.getRpgWorldManager().getRealmDefinition(continentId);
        if (!isStartingRealmAvailable(realm)) {
            player.sendMessage("§cĐại lục này chưa sẵn sàng. Không có dữ liệu nào bị thay đổi.");
            openContinentMenu(player);
            return;
        }
        setOnboarding(player, "ready-for-character", realm.id());
        player.closeInventory();
        player.sendMessage("§aĐã chọn đại lục §6" + realm.displayName() + "§a. Bây giờ hãy chọn chủng tộc và class.");
        openRaceMenu(player);
    }

    /** Called by the runtime NPC manager before normal dialogue handling. */
    public boolean recordTutorialNpcVisit(Player player, String npcId) {
        if (!isOnboardingEnabled() || !"awaiting-tutorial".equals(onboardingStage(player))) return false;
        String continentId = onboardingContinent(player);
        HumanRealmDefinitionLoader.HumanRealmDefinition realm = continentId == null || plugin.getRpgWorldManager() == null
            ? null : plugin.getRpgWorldManager().getRealmDefinition(continentId);
        if (realm == null || !realm.tutorialNpcId().equalsIgnoreCase(npcId)) return false;
        var target = plugin.getNpcManager().getNpcTarget(npcId);
        if (target.isEmpty() || target.get().location() == null
                || !target.get().world().equals(realm.worldName())
                || !player.getWorld().getName().equals(target.get().world())) {
            player.sendMessage("§cNPC hướng dẫn chưa được cấu hình đúng world. Hãy báo quản trị viên.");
            plugin.getLogger().warning("[Onboarding] Tutorial NPC validation failed: player=" + player.getName()
                + ", npc=" + npcId + ", realm=" + realm.id() + ", expectedWorld=" + realm.worldName());
            return true;
        }
        String dialogId = realmTutorialValue(realm.id(), "tutorial.dialog-id");
        Runnable afterDialogue = () -> finishTutorialNpcVisit(player, realm.id(), npcId);
        if (dialogId != null && plugin.getQuestDialogManager() != null
                && plugin.getQuestDialogManager().hasDialog(dialogId)) {
            String speaker = plugin.getNpcManager().getNpcDisplayName(npcId).orElse(npcId);
            plugin.getQuestDialogManager().openQuestDialogue(player, dialogId, speaker, afterDialogue);
        } else {
            if (dialogId != null) plugin.getLogger().warning("[Onboarding] Configured dialog is missing: realm="
                + realm.id() + ", dialog=" + dialogId);
            afterDialogue.run();
        }
        return true;
    }

    /** Handles the separately configured tutorial NPC in the onboarding start/reset world. */
    public boolean recordStartWorldTutorialNpcVisit(Player player, String npcId) {
        String stage = onboardingStage(player);
        if (!isOnboardingEnabled()) return false;
        String configuredNpc = plugin.getConfig().getString("rpg.onboarding.start-world.tutorial-npc-id", "");
        if (configuredNpc.isBlank() || !configuredNpc.equalsIgnoreCase(npcId)) return false;
        if (plugin.getNpcManager() == null
                || plugin.getNpcManager().getNpcType(npcId).filter("tutorial"::equalsIgnoreCase).isEmpty()) return false;
        if ("complete".equals(stage) && loadedProfiles.containsKey(player.getUniqueId())) {
            player.sendMessage("§bNPC Tutorial: §fBạn đã hoàn thành lựa chọn nhân vật. Nhiệm vụ khởi đầu theo race của bạn đã được thiết lập.");
            debugProfile("start-tutorial-repeat-interaction player=" + player.getName() + ", npc=" + npcId
                + ", result=already-complete");
            return true;
        }
        if (stage == null && !profileLookupComplete.contains(player.getUniqueId())) {
            if (player.getWorld().getName().equalsIgnoreCase(plugin.getConfig()
                    .getString("rpg.onboarding.start-world.name", "world_start"))) {
                pendingStartNpcInteractions.add(player.getUniqueId());
                player.sendMessage("§eĐang tải hồ sơ tân thủ; cuộc trò chuyện với NPC đã được ghi nhận.");
                return true;
            }
            return false;
        }
        if (!("awaiting-start-tutorial".equals(stage)
                || "needs-continent".equals(stage) || "choosing-continent".equals(stage))) return false;
        String startWorld = plugin.getConfig().getString("rpg.onboarding.start-world.name", "world_start");
        var target = plugin.getNpcManager() == null ? java.util.Optional.<NpcManager.NpcTarget>empty()
            : plugin.getNpcManager().getNpcTarget(npcId);
        if (target.isEmpty() || !startWorld.equalsIgnoreCase(target.get().world())
                || !player.getWorld().getName().equalsIgnoreCase(startWorld)) {
            player.sendMessage("§cNPC hướng dẫn khu vực bắt đầu chưa được cấu hình đúng world.");
            plugin.getLogger().warning("[Onboarding] Start-world tutorial NPC validation failed: player="
                + player.getName() + ", npc=" + npcId + ", expectedWorld=" + startWorld
                + ", actualNpcWorld=" + target.map(NpcManager.NpcTarget::world).orElse("missing")
                + ", playerWorld=" + player.getWorld().getName());
            return true;
        }
        player.getPersistentDataContainer().set(startTutorialVisitedKey, PersistentDataType.BYTE, (byte) 1);
        setOnboarding(player, "choosing-continent", null);
        debugProfile("start-tutorial-visited player=" + player.getName() + ", npc=" + npcId
            + ", world=" + startWorld);
        String dialogId = plugin.getConfig().getString("rpg.onboarding.start-world.tutorial.dialog-id");
        Runnable afterDialogue = () -> {
            if (!player.isOnline()) return;
            player.sendMessage("§aĐã gặp người hướng dẫn. Hãy chọn đại lục khởi đầu.");
            openContinentMenu(player);
        };
        if (dialogId != null && !dialogId.isBlank() && plugin.getQuestDialogManager() != null
                && plugin.getQuestDialogManager().hasDialog(dialogId)) {
            String speaker = plugin.getNpcManager().getNpcDisplayName(npcId).orElse(npcId);
            plugin.getQuestDialogManager().openQuestDialogue(player, dialogId, speaker, afterDialogue);
        } else {
            if (dialogId != null && !dialogId.isBlank()) {
                plugin.getLogger().warning("[Onboarding] Start-world tutorial dialog is missing: npc="
                    + npcId + ", dialog=" + dialogId);
            }
            afterDialogue.run();
        }
        return true;
    }

    private void finishTutorialNpcVisit(Player player, String realmId, String npcId) {
        if (!player.isOnline()) return;
        lastCrossWorldHintTick.remove(player.getUniqueId());
        if (loadedProfiles.containsKey(player.getUniqueId())) {
            setOnboarding(player, "complete", realmId);
            player.sendMessage("§aBạn đã hoàn tất bước gặp NPC hướng dẫn.");
            acceptConfiguredTutorialQuest(player, realmId, npcId);
        } else {
            setOnboarding(player, "ready-for-character", realmId);
            player.sendMessage("§aBạn đã gặp người hướng dẫn. Bây giờ hãy chọn chủng tộc và class.");
            openRaceMenu(player);
        }
    }

    private String realmTutorialValue(String realmId, String path) {
        String value = loadRealmYaml(realmId).getString(path);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void acceptConfiguredTutorialQuest(Player player, String realmId, String npcId) {
        String questId = realmTutorialValue(realmId, "tutorial.auto-quest-id");
        if (questId == null || questId.isBlank() || plugin.getQuestCatalogManager() == null) return;
        plugin.getLogger().info("[Onboarding] Auto-quest after tutorial: player=" + player.getName()
            + ", realm=" + realmId + ", npc=" + npcId + ", quest=" + questId);
        plugin.getQuestCatalogManager().acceptQuest(player, npcId, questId);
    }

    private void tickOnboardingNavigation() {
        onboardingTick += 10L;
        int interval = Math.max(10, Math.min(100,
            plugin.getConfig().getInt("rpg.onboarding.navigation-interval-ticks", 20)));
        if (onboardingTick - lastNavigationRunTick < interval || !isOnboardingEnabled()
                || !plugin.getConfig().getBoolean("rpg.onboarding.navigation-enabled", true)) return;
        lastNavigationRunTick = onboardingTick;
        for (Player player : Bukkit.getOnlinePlayers()) {
            String stage = onboardingStage(player);
            if ("awaiting-start-tutorial".equals(stage)) {
                String npcId = plugin.getConfig().getString("rpg.onboarding.start-world.tutorial-npc-id", "");
                var target = npcId.isBlank() || plugin.getNpcManager() == null
                    ? java.util.Optional.<NpcManager.NpcTarget>empty()
                    : plugin.getNpcManager().getNpcTarget(npcId);
                if (target.isPresent()) renderOnboardingTarget(player, target.get());
                continue;
            }
            if (!"awaiting-tutorial".equals(stage)) continue;
            String continentId = onboardingContinent(player);
            HumanRealmDefinitionLoader.HumanRealmDefinition realm = continentId == null || plugin.getRpgWorldManager() == null
                ? null : plugin.getRpgWorldManager().getRealmDefinition(continentId);
            var target = realm == null || plugin.getNpcManager() == null
                ? java.util.Optional.<NpcManager.NpcTarget>empty()
                : plugin.getNpcManager().getNpcTarget(realm.tutorialNpcId());
            if (target.isEmpty()) continue;
            renderOnboardingTarget(player, target.get());
        }
    }

    private void renderOnboardingTarget(Player player, NpcManager.NpcTarget target) {
        if (!player.getWorld().getName().equalsIgnoreCase(target.world())) {
            long lastHint = lastCrossWorldHintTick.getOrDefault(player.getUniqueId(), 0L);
            if (onboardingTick - lastHint >= 100L) {
                player.sendActionBar("§eĐi tới world §f" + target.world()
                    + " §eđể bắt đầu navigation tới NPC hướng dẫn.");
                lastCrossWorldHintTick.put(player.getUniqueId(), onboardingTick);
            }
        } else if (target.location() != null) {
            double range = Math.max(8.0, Math.min(64.0,
                plugin.getConfig().getDouble("rpg.onboarding.navigation-range-blocks", 48.0)));
            QuestWaypointRenderer.render(player, target.location(), range, true);
        }
    }

    public void stopOnboardingNavigation() {
        if (onboardingNavigationTask != null) onboardingNavigationTask.cancel();
        onboardingNavigationTask = null;
    }

    public void openStarterBookAdminMenu(Player player) {
        if (!player.hasPermission("vnmine.command.skillbook.admin")) {
            player.sendMessage("§cBạn không có quyền quản lý sách skill khởi đầu.");
            return;
        }
        CharacterMenuHolder holder = new CharacterMenuHolder(player.getUniqueId(),
            "starter-races", null, null, new HashMap<>());
        Inventory inventory = Bukkit.createInventory(holder, 54, "Quản lý sách skill khởi đầu • Race");
        holder.inventory = inventory;
        int index = 0;
        for (RaceDefinition race : races.values()) {
            if (index >= MENU_SLOTS.length) break;
            int slot = MENU_SLOTS[index++];
            holder.choices.put(slot, race.id());
            long mapped = starterBookMappings.keySet().stream()
                .filter(key -> key.startsWith(race.id() + ".")).count();
            inventory.setItem(slot, createActionItem(race.material(), "§6" + race.name(), List.of(
                "§7Mapping sách đã đặt: §f" + mapped,
                "§eNhấn để chọn class của race này.")));
        }
        inventory.setItem(49, createActionItem(Material.BARRIER, "§cĐóng", List.of()));
        player.openInventory(inventory);
        debugProfile("starter-book-admin-open player=" + player.getName() + ", mappings="
            + starterBookMappings.size());
    }

    public void openRaceAdminMenu(Player player) {
        if (!player.hasPermission("vnmine.command.race.admin")
                && !player.hasPermission("vnmine.command.init")) {
            player.sendMessage("§cBạn không có quyền quản lý race.");
            return;
        }
        CharacterMenuHolder holder = new CharacterMenuHolder(player.getUniqueId(),
            "admin-races", null, null, new HashMap<>());
        Inventory inventory = Bukkit.createInventory(holder, 54, "Admin • Nhiệm vụ theo chủng tộc");
        holder.inventory = inventory;
        int index = 0;
        for (RaceDefinition race : getConfiguredRaceDefinitions()) {
            if (index >= MENU_SLOTS.length) break;
            int slot = MENU_SLOTS[index++];
            String questId = raceOnboardingQuestIds.get(race.id());
            String questTitle = questId == null ? "Chưa gán" : plugin.getQuestCatalogManager() == null
                ? questId : java.util.Objects.toString(plugin.getQuestCatalogManager().getQuestTitle(questId), questId);
            holder.choices.put(slot, race.id());
            inventory.setItem(slot, createActionItem(race.material(), "§d" + race.name(), List.of(
                "§7Race ID: §f" + race.id(), "§7Quest sau khi tạo nhân vật: §e" + questTitle,
                "§7Quest ID: §f" + (questId == null ? "(chưa gán)" : questId),
                "§eNhấn để chọn quest đã lưu.")));
        }
        inventory.setItem(49, createActionItem(Material.CRAFTING_TABLE, "§7Quay lại menu trước", List.of(
            "§7Trở về nơi đã mở chức năng quản lý chủng tộc.")));
        player.openInventory(inventory);
    }

    private boolean hasRaceAdminPermission(Player player) {
        return player.hasPermission("vnmine.command.race.admin") || player.hasPermission("vnmine.command.init");
    }

    private void openRaceQuestChoices(Player player, String raceId, int requestedPage) {
        RaceDefinition race = races.get(raceId);
        if (race == null || plugin.getQuestCatalogManager() == null) return;
        List<String> quests = plugin.getQuestCatalogManager().getEnabledQuestIds();
        int pageCount = Math.max(1, (quests.size() + 44) / 45);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        CharacterMenuHolder holder = new CharacterMenuHolder(player.getUniqueId(),
            "race-quest-choices", raceId, null, new HashMap<>(), page);
        Inventory inventory = Bukkit.createInventory(holder, 54, "Quest tự giao • " + race.name());
        holder.inventory = inventory;
        String current = raceOnboardingQuestIds.get(raceId);
        for (int index = 0; index < 45; index++) {
            int choiceIndex = page * 45 + index;
            if (choiceIndex >= quests.size()) break;
            String questId = quests.get(choiceIndex);
            String title = plugin.getQuestCatalogManager().getQuestTitle(questId);
            holder.choices.put(index, questId);
            inventory.setItem(index, createActionItem(Material.BOOK,
                (questId.equals(current) ? "§a✓ " : "§f") + title,
                List.of("§7Quest ID: §f" + questId,
                    questId.equals(current) ? "§aĐang được gán" : "§eNhấn để chọn.")));
        }
        inventory.setItem(45, createActionItem(Material.BARRIER, "§cBỏ quest tự giao", List.of()));
        if (page > 0) inventory.setItem(46, createActionItem(Material.ARROW, "§eTrang trước", List.of()));
        inventory.setItem(48, createActionItem(Material.PAPER, "§fTrang " + (page + 1) + "/" + pageCount,
            List.of("§7Quest đang bật: " + quests.size())));
        inventory.setItem(49, createActionItem(Material.ARROW, "§eQuay lại danh sách race", List.of()));
        if (page + 1 < pageCount) inventory.setItem(50, createActionItem(Material.ARROW, "§eTrang sau", List.of()));
        if (quests.isEmpty()) inventory.setItem(22, createActionItem(Material.BARRIER,
            "§cKhông có quest đang bật", List.of("§7Bật hoặc tạo quest trước trong /vnquest admin.")));
        player.openInventory(inventory);
    }

    private void saveRaceQuestSelection(Player player, String raceId, String questId) {
        if (!player.isOnline()) return;
        if (!hasRaceAdminPermission(player)) {
            player.sendMessage("§cBạn không còn quyền quản lý race.");
            return;
        }
        if (questId != null && (plugin.getQuestCatalogManager() == null
                || !plugin.getQuestCatalogManager().isEnabledQuest(questId))) {
            player.sendMessage("§cQuest không còn tồn tại hoặc đã bị tắt. Hãy chọn lại.");
            openRaceQuestChoices(player, raceId, 0);
            return;
        }
        String previous = raceOnboardingQuestIds.get(raceId);
        if (questId == null) raceOnboardingQuestIds.remove(raceId);
        else raceOnboardingQuestIds.put(raceId, questId);
        if (!saveRaceDefinitions(player)) {
            if (previous == null) raceOnboardingQuestIds.remove(raceId);
            else raceOnboardingQuestIds.put(raceId, previous);
            return;
        }
        RaceDefinition race = races.get(raceId);
        plugin.getLogger().info("[RaceSetup] admin=" + player.getName() + ", race=" + raceId
            + ", autoQuest=" + (questId == null ? "none" : questId));
        player.sendMessage("§aĐã lưu quest tự giao cho §d" + (race == null ? raceId : race.name()) + "§a.");
        openRaceAdminMenu(player);
    }

    private boolean saveRaceDefinitions(Player player) {
        if (raceDefinitionsFile == null) return false;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().parseComments(true);
        try {
            yaml.load(raceDefinitionsFile);
            for (RaceDefinition race : races.values()) {
                String questId = raceOnboardingQuestIds.get(race.id());
                yaml.set("races." + race.id() + ".onboarding.auto-quest-id", questId);
            }
            File temporary = new File(raceDefinitionsFile.getParentFile(), "races.yml.tmp");
            yaml.save(temporary);
            try {
                Files.move(temporary.toPath(), raceDefinitionsFile.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary.toPath(), raceDefinitionsFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "[RaceSetup] Could not save races.yml.", exception);
            player.sendMessage("§cKhông thể lưu races.yml. Cấu hình trước đó vẫn được giữ.");
            return false;
        }
    }

    private void openStarterClassMenu(Player player, String raceId) {
        RaceDefinition race = races.get(raceId);
        if (race == null) return;
        CharacterMenuHolder holder = new CharacterMenuHolder(player.getUniqueId(),
            "starter-classes", raceId, null, new HashMap<>());
        Inventory inventory = Bukkit.createInventory(holder, 54, "Sách khởi đầu • " + race.name());
        holder.inventory = inventory;
        List<ClassDefinition> classList = classesByRace.getOrDefault(raceId, List.of());
        for (int index = 0; index < classList.size() && index < MENU_SLOTS.length; index++) {
            ClassDefinition klass = classList.get(index);
            int slot = MENU_SLOTS[index];
            holder.choices.put(slot, klass.id());
            String bookId = starterBookMappings.get(starterBookKey(raceId, klass.id()));
            inventory.setItem(slot, createActionItem(klass.material(), "§b" + klass.name(), List.of(
                "§7Trạng thái: §f" + klass.availability(),
                "§7Sách hiện tại: §e" + (bookId == null ? "Chưa đặt" : bookId),
                "§eNhấn để chọn sách.")));
        }
        inventory.setItem(45, createActionItem(Material.ARROW, "§eQuay lại race", List.of()));
        inventory.setItem(49, createActionItem(Material.BARRIER, "§cĐóng", List.of()));
        player.openInventory(inventory);
    }

    private void openStarterBookMenu(Player player, String raceId, String classId) {
        ClassDefinition klass = findClass(raceId, classId);
        if (klass == null) return;
        CharacterMenuHolder holder = new CharacterMenuHolder(player.getUniqueId(),
            "starter-books", raceId, classId, new HashMap<>());
        Inventory inventory = Bukkit.createInventory(holder, 54, "Chọn sách • " + klass.name());
        holder.inventory = inventory;
        SkillManager skills = plugin.getSkillManager();
        List<SkillDefinitionLoader.SkillBookDefinition> books = skills == null
            ? List.of() : skills.getSkillBooksForClass(classId);
        for (int index = 0; index < books.size() && index < 45; index++) {
            SkillDefinitionLoader.SkillBookDefinition book = books.get(index);
            int slot = index;
            holder.choices.put(slot, book.id());
            String current = starterBookMappings.get(starterBookKey(raceId, classId));
            List<String> lore = new java.util.ArrayList<>();
            lore.add("§7Book ID: §f" + book.id());
            lore.add("§7Skill: §b" + book.skillId());
            lore.add("§7Độ hiếm: §d" + book.rarity());
            lore.add(Objects.equals(current, book.id()) ? "§aĐang được chọn" : "§eNhấn để gán cho class này.");
            inventory.setItem(slot, createActionItem(book.material(), "§6" + book.displayName(), lore));
        }
        inventory.setItem(46, createActionItem(Material.BARRIER, "§cXóa mapping", List.of(
            "§7Class này sẽ không tự nhận sách.")));
        inventory.setItem(49, createActionItem(Material.ARROW, "§eQuay lại class", List.of()));
        inventory.setItem(53, createActionItem(Material.BARRIER, "§cĐóng", List.of()));
        player.openInventory(inventory);
    }

    private void handleStarterBookMenuClick(Player player, CharacterMenuHolder holder, int slot) {
        switch (holder.type) {
            case "starter-races" -> {
                if (slot == 49) player.closeInventory();
                else {
                    String raceId = holder.choices.get(slot);
                    if (raceId != null) openStarterClassMenu(player, raceId);
                }
            }
            case "starter-classes" -> {
                if (slot == 45) openStarterBookAdminMenu(player);
                else if (slot == 49) player.closeInventory();
                else {
                    String classId = holder.choices.get(slot);
                    if (classId != null) openStarterBookMenu(player, holder.raceId, classId);
                }
            }
            case "starter-books" -> {
                if (slot == 49) openStarterClassMenu(player, holder.raceId);
                else if (slot == 53) player.closeInventory();
                else if (slot == 46) {
                    String key = starterBookKey(holder.raceId, holder.classId);
                    Map<String, String> previous = new LinkedHashMap<>(starterBookMappings);
                    String previousBook = starterBookMappings.remove(key);
                    boolean saved = saveStarterBookMappings(player);
                    if (!saved) {
                        starterBookMappings.clear();
                        starterBookMappings.putAll(previous);
                    }
                    debugProfile("starter-book-mapping-clear player=" + player.getName() + ", pair=" + key
                        + ", previous=" + previousBook + ", saved=" + saved);
                    openStarterClassMenu(player, holder.raceId);
                } else {
                    String bookId = holder.choices.get(slot);
                    if (bookId != null) {
                        String key = starterBookKey(holder.raceId, holder.classId);
                        Map<String, String> previous = new LinkedHashMap<>(starterBookMappings);
                        starterBookMappings.put(key, bookId);
                        boolean saved = saveStarterBookMappings(player);
                        if (!saved) {
                            starterBookMappings.clear();
                            starterBookMappings.putAll(previous);
                        }
                        debugProfile("starter-book-mapping-set player=" + player.getName() + ", pair=" + key
                            + ", book=" + bookId + ", saved=" + saved);
                        openStarterClassMenu(player, holder.raceId);
                    }
                }
            }
            default -> { }
        }
    }

    private String starterBookKey(String raceId, String classId) {
        return raceId + "." + classId;
    }

    private void loadStarterBookMappings() {
        if (!starterBookMappingsFile.isFile()) {
            try {
                plugin.saveResource("starter-skill-books.yml", false);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().log(Level.SEVERE, "[Skills] Bundled starter-skill-books.yml is missing.", exception);
                return;
            }
        }
        YamlConfiguration yaml = loadStrictConfig(starterBookMappingsFile);
        if (yaml.getInt("schema-version", -1) != 1) {
            plugin.getLogger().severe("[Skills] Unsupported starter-skill-books.yml schema-version; expected 1.");
            return;
        }
        ConfigurationSection roots = yaml.getConfigurationSection("starter-books");
        if (roots == null) return;
        for (String raceId : roots.getKeys(false)) {
            ConfigurationSection classMappings = roots.getConfigurationSection(raceId);
            if (classMappings == null) continue;
            for (String classId : classMappings.getKeys(false)) {
                String bookId = classMappings.getString(classId, "").trim();
                if (!bookId.isEmpty()) starterBookMappings.put(starterBookKey(raceId, classId), bookId);
            }
        }
        debugProfile("starter-book-config-loaded mappings=" + starterBookMappings.size());
    }

    private void validateStarterBookMappings() {
        SkillManager skills = plugin.getSkillManager();
        starterBookMappings.entrySet().removeIf(entry -> {
            String[] identity = entry.getKey().split("\\.", 2);
            ClassDefinition klass = identity.length == 2 ? findClass(identity[0], identity[1]) : null;
            boolean valid = klass != null && skills != null
                && skills.getSkillBooksForClass(klass.id()).stream()
                    .anyMatch(book -> book.id().equals(entry.getValue()));
            debugProfile("starter-book-mapping-check pair=" + entry.getKey() + ", book=" + entry.getValue()
                + ", valid=" + valid);
            if (!valid) plugin.getLogger().warning("[Skills] Ignoring invalid starter-book mapping '"
                + entry.getKey() + "' -> '" + entry.getValue() + "'.");
            return !valid;
        });
    }

    private boolean saveStarterBookMappings(Player player) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 1);
        for (Map.Entry<String, String> entry : starterBookMappings.entrySet()) {
            String[] identity = entry.getKey().split("\\.", 2);
            if (identity.length == 2) yaml.set("starter-books." + identity[0] + "." + identity[1], entry.getValue());
        }
        File temporary = new File(starterBookMappingsFile.getParentFile(), "starter-skill-books.yml.tmp");
        String header = "# [VI] Ánh xạ race/class sang sách skill được cấp sau onboarding; xóa khóa để không cấp sách.\n"
            + "# [EN] Maps each race/class to its onboarding skill book; remove a key to grant no book.\n"
            + "# [VI] Chỉ chọn book có skill cùng class; ID phải có trong skills/skill-books.yml.\n"
            + "# [EN] Select a book whose skill belongs to the same class; IDs must exist in skills/skill-books.yml.\n";
        try {
            Files.writeString(temporary.toPath(), header + yaml.saveToString(), java.nio.charset.StandardCharsets.UTF_8);
            try {
                Files.move(temporary.toPath(), starterBookMappingsFile.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary.toPath(), starterBookMappingsFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            player.sendMessage("§aĐã lưu mapping sách skill khởi đầu.");
            debugProfile("starter-book-config-saved player=" + player.getName()
                + ", mappings=" + starterBookMappings.size());
            return true;
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "[Skills] Could not save starter-skill-books.yml.", exception);
            player.sendMessage("§cKhông thể lưu cấu hình sách. Cấu hình trước đó vẫn được giữ.");
            return false;
        } finally {
            try { Files.deleteIfExists(temporary.toPath()); }
            catch (IOException exception) {
                plugin.getLogger().log(Level.WARNING, "[Skills] Could not remove temporary starter-book config.", exception);
            }
        }
    }

    private void queueStarterBookGrant(Player player, String raceId, String classId) {
        if (player.getPersistentDataContainer().getOrDefault(starterBookPendingKey,
                PersistentDataType.BYTE, (byte) 0) != 1) return;
        String bookId = starterBookMappings.get(starterBookKey(raceId, classId));
        SkillManager skillManager = plugin.getSkillManager();
        Optional<byte[]> serialized = skillManager == null || bookId == null
            ? Optional.empty() : skillManager.serializeStarterSkillBook(bookId);
        if (serialized.isEmpty()) {
            plugin.getLogger().severe("[Onboarding][SkillBook] No valid starter book mapping: player="
                + player.getName() + ", race=" + raceId + ", class=" + classId + ", book=" + bookId);
            player.sendMessage("§cSách skill khởi đầu chưa được cấu hình đúng. Tiến trình được giữ để quản trị viên sửa.");
            return;
        }
        UUID playerId = player.getUniqueId();
        String transactionPrefix = starterBookTransactionPrefix(playerId);
        byte[] itemData = serialized.get();
        debugProfile("starter-book-queue-start player=" + player.getName() + ", race=" + raceId
            + ", class=" + classId + ", book=" + bookId + ", transaction=" + transactionPrefix + ":0");
        plugin.getPlayerProfileDatabaseManager().executeGameplayDatabaseTask(
                "onboarding.starter-book.queue player=" + player.getName(), () -> {
                    plugin.getPlayerProfileDatabaseManager().enqueueMerchantDeliveries(playerId.toString(),
                        transactionPrefix, List.of(itemData), Instant.now().toString());
                    return true;
                })
            .whenComplete((queued, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (failure != null || !Boolean.TRUE.equals(queued)) {
                    plugin.getLogger().log(Level.SEVERE, "[Onboarding][SkillBook] Could not queue starter book: "
                        + "player=" + player.getName() + ", race=" + raceId + ", class=" + classId
                        + ", book=" + bookId, failure);
                    if (player.isOnline()) player.sendMessage("§cChưa thể giao sách skill. Hệ thống sẽ thử lại khi bạn vào lại.");
                    return;
                }
                if (!player.isOnline()) return;
                player.getPersistentDataContainer().remove(starterBookPendingKey);
                player.saveData();
                debugProfile("starter-book-queued player=" + player.getName() + ", race=" + raceId
                    + ", class=" + classId + ", book=" + bookId + ", transaction=" + transactionPrefix + ":0");
                player.sendMessage("§aĐã chuẩn bị sách kỹ năng khởi đầu §e" + bookId
                    + "§a. Nếu túi đầy, sách sẽ chờ đến khi còn chỗ.");
                if (plugin.getEconomyManager() != null) {
                    Bukkit.getScheduler().runTaskLater(plugin,
                        () -> { if (player.isOnline()) reconcileStarterBookDelivery(player); }, 20L);
                }
            }));
    }

    private void reconcileStarterBookDelivery(Player player) {
        if (plugin.getEconomyManager() == null || !isStarterTutorialPending(player)) return;
        String transactionId = starterBookTransactionPrefix(player.getUniqueId()) + ":0";
        plugin.getEconomyManager().reconcileMerchantDeliveries(player, transactionId, () -> {
            if (!player.isOnline() || !isStarterTutorialPending(player)) return;
            player.getPersistentDataContainer().remove(starterTutorialPendingKey);
            player.saveData();
            debugProfile("starter-book-delivered player=" + player.getName() + ", transaction="
                + transactionId + ", next=tutorial-quest-start");
            if (plugin.getTutorialQuestManager() != null) plugin.getTutorialQuestManager().initializePlayer(player);
        });
    }

    private String starterBookTransactionPrefix(UUID playerId) {
        return "starter-skill-book:" + playerId;
    }

    static List<String> buildProfileLore(PlayerProfileDatabaseManager.PlayerProfile profile,
                                         String raceName, String className,
                                         CharacterProgression.Rules progressionRules) {
        return buildProfileLore(profile, raceName, className, progressionRules, profile.homeWorld());
    }

    static List<String> buildProfileLore(PlayerProfileDatabaseManager.PlayerProfile profile,
                                         String raceName, String className,
                                         CharacterProgression.Rules progressionRules,
                                         String startingContinent) {
        String homeWorld = startingContinent == null || startingContinent.isBlank()
            ? "Chưa thiết lập" : startingContinent;
        int experienceRequired = progressionRules.experienceRequiredForNextLevel(profile.level());
        String experienceProgress = experienceRequired == 0
            ? profile.exp() + " (đã đạt cấp tối đa)"
            : profile.exp() + " / " + experienceRequired;
        return List.of(
            "§7Người chơi: §f" + profile.playerName(),
            "§7Chủng tộc: §6" + raceName,
            "§7Class: §b" + className,
            "§7Cấp nhân vật: §e" + profile.level(),
            "§7EXP tới cấp tiếp theo: §e" + experienceProgress,
            "§7Skill point chưa dùng: §e" + profile.unspentSkillPoints(),
            "§7Đại lục khởi đầu: §f" + homeWorld,
            "",
            profile.active() ? "§aHồ sơ đang hoạt động" : "§cHồ sơ đang tạm ngưng"
        );
    }

    private void openProfileMenu(Player player, PlayerProfileDatabaseManager.PlayerProfile profile) {
        RaceDefinition race = races.get(profile.selectedRace());
        ClassDefinition selectedClass = findClass(profile.selectedRace(), profile.selectedClass());
        String raceName = race == null ? String.valueOf(profile.selectedRace()) : race.name();
        String className = selectedClass == null ? String.valueOf(profile.selectedClass()) : selectedClass.name();
        String selectedContinentId = onboardingContinent(player);
        HumanRealmDefinitionLoader.HumanRealmDefinition startRealm = selectedContinentId == null
            || plugin.getRpgWorldManager() == null ? null
            : plugin.getRpgWorldManager().getRealmDefinition(selectedContinentId);
        String startingContinent = startRealm == null ? profile.homeWorld()
            : startRealm.displayName() + " (§7" + startRealm.worldName() + "§f)";
        CharacterMenuHolder holder = new CharacterMenuHolder(
            player.getUniqueId(), "profile", null, null, new HashMap<>());
        Inventory inventory = Bukkit.createInventory(holder, 54, "VnMine • Hồ sơ nhân vật");
        holder.inventory = inventory;
        inventory.setItem(11, createActionItem(Material.SUNFLOWER, "§6Ví VNCOIN: §7Đang tải...",
            List.of("§7Số dư đơn vị tiền tệ của máy chủ.")));
        inventory.setItem(13, createActionItem(Material.PLAYER_HEAD, "§dHồ sơ RPG",
            buildProfileLore(profile, raceName, className,
                plugin.getPlayerProfileDatabaseManager().getCharacterProgressionRules(), startingContinent)));
        if (plugin.getConfig().getBoolean("rpg.quest-journal.enabled", true)) {
            inventory.setItem(15, createActionItem(Material.WRITABLE_BOOK, "§bNhật ký nhiệm vụ", List.of(
                "§7Theo dõi nhiệm vụ tutorial.")));
        }
        inventory.setItem(17, createActionItem(Material.BOOK, "§eNhiệm vụ RPG", List.of(
            "§7Xem nhiệm vụ đang nhận và nhật ký hoàn thành.")));
        inventory.setItem(22, createActionItem(Material.ARROW, "§7Về menu chính", List.of(
            "§7Quay lại hub RPG.")));
        player.openInventory(inventory);
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        plugin.getPlayerProfileDatabaseManager()
            .executeGameplayDatabaseTask("wallet.profile-view player=" + playerName,
                () -> plugin.getPlayerProfileDatabaseManager().getWalletBalance(playerId.toString()))
            .whenComplete((balance, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inventory) return;
                if (failure != null) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "[Economy] Could not load profile wallet for " + playerName + ".", failure);
                    inventory.setItem(11, createActionItem(Material.SUNFLOWER, "§6Ví VNCOIN: §cLỗi tải",
                        List.of("§7Mở lại hồ sơ để thử lại.")));
                    return;
                }
                inventory.setItem(11, createActionItem(Material.SUNFLOWER, "§6Ví VNCOIN: §e" + balance,
                    List.of("§7Số dư tiền tệ hiện tại.", "§eNhấp để xem lịch sử giao dịch.")));
            }));
    }

    public void refreshLoadedProfile(Player player) {
        UUID playerId = player.getUniqueId();
        debugProfile("refresh-start player=" + player.getName());
        plugin.getPlayerProfileDatabaseManager()
            .executeGameplayDatabaseTask("profile.refresh player=" + player.getName(), () ->
                plugin.getPlayerProfileDatabaseManager().getPlayerProfile(playerId.toString()))
            .whenComplete((profile, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (failure != null) {
                    plugin.getLogger().log(Level.SEVERE, "[Profile] Failed to refresh the in-memory profile for " +
                        player.getName(), failure);
                } else if (profile.isPresent()) {
                    loadedProfiles.put(playerId, profile.get());
                    debugProfile("refresh-result player=" + player.getName() + ", found=true");
                    if (profile.get().selectedRace() == null || profile.get().selectedClass() == null)
                        openRaceMenu(player);
                } else {
                    loadedProfiles.remove(playerId);
                    debugProfile("refresh-result player=" + player.getName() + ", found=false");
                    continueOnboarding(player);
                }
            }));
    }

    public void finalizeSelection(Player player, String raceId, String classId) {
        if (!isCreationAvailable(player)) return;
        if (isOnboardingEnabled() && !"ready-for-character".equals(onboardingStage(player))) {
            player.sendMessage("§cBạn cần chọn đại lục và gặp NPC hướng dẫn trước khi tạo nhân vật.");
            continueOnboarding(player);
            return;
        }
        RaceDefinition race = races.get(raceId);
        ClassDefinition selectedClass = findClass(raceId, classId);
        if (race == null || !isClassSelectable(selectedClass)) {
            player.sendMessage("§cClass này chưa khả dụng để tạo nhân vật.");
            return;
        }
        if (plugin.getPlayerProfileDatabaseManager() == null) {
            player.sendMessage("§cHệ thống hồ sơ chưa sẵn sàng.");
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!profileLookupComplete.contains(playerId)) {
            player.sendMessage("§eHồ sơ đang được kiểm tra. Hãy thử lại sau giây lát.");
            return;
        }
        if (!pendingProfileCreates.add(playerId)) return;
        String playerName = player.getName();
        String continentId = onboardingContinent(player);
        HumanRealmDefinitionLoader.HumanRealmDefinition startingRealm = continentId == null
            || plugin.getRpgWorldManager() == null ? null
            : plugin.getRpgWorldManager().getRealmDefinition(continentId);
        String homeWorld = startingRealm == null ? null : startingRealm.worldName();
        if (isOnboardingEnabled()) {
            player.getPersistentDataContainer().set(starterBookPendingKey, PersistentDataType.BYTE, (byte) 1);
            player.getPersistentDataContainer().set(starterTutorialPendingKey, PersistentDataType.BYTE, (byte) 1);
            player.saveData();
            debugProfile("starter-book-grant-armed player=" + playerName + ", race=" + raceId
                + ", class=" + classId);
        }
        debugProfile("create-start player=" + playerName + ", race=" + raceId + ", class=" + classId);
        String now = Instant.now().toString();
        PlayerProfileDatabaseManager.PlayerProfile existingProfile = loadedProfiles.get(playerId);
        boolean completingResetProfile = existingProfile != null
            && existingProfile.selectedRace() == null && existingProfile.selectedClass() == null;
        plugin.getPlayerProfileDatabaseManager().executeGameplayDatabaseTask(
                "profile.create player=" + playerName, () ->
                completingResetProfile
                    ? plugin.getPlayerProfileDatabaseManager().updateResetPlayerSelection(
                        playerId.toString(), playerName, raceId, classId, homeWorld, now)
                    : plugin.getPlayerProfileDatabaseManager().createPlayerProfile(
                        playerId.toString(), playerName, raceId, classId, 1, 0, homeWorld, now))
            .whenComplete((created, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            pendingProfileCreates.remove(playerId);
            if (!player.isOnline()) return;
            if (failure != null) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "[Profile] Failed to save character selection for " + playerName, failure);
                player.sendMessage("§cKhông thể lưu hồ sơ nhân vật. Vui lòng thử lại sau.");
                return;
            }
            if (created) {
                PlayerProfileDatabaseManager.PlayerProfile profile = new PlayerProfileDatabaseManager.PlayerProfile(
                    playerId.toString(), playerName, raceId, classId, 1, 0, homeWorld, now, now, true);
                loadedProfiles.put(playerId, profile);
                profileLookupComplete.add(playerId);
                if (isOnboardingEnabled()) setOnboarding(player, "complete", continentId);
                if (isOnboardingEnabled()) {
                    queueStarterBookGrant(player, raceId, classId);
                    String defaultQuestId = plugin.getConfig().getString(
                        "rpg.onboarding.character-complete.default-quest-id", "").trim();
                    acceptOnboardingQuest(player, defaultQuestId, "default");
                    String raceQuestId = raceOnboardingQuestIds.get(raceId);
                    if (raceQuestId != null && raceQuestId.equalsIgnoreCase(defaultQuestId)) {
                        debugProfile("onboarding-quest-duplicate-skipped player=" + playerName + ", quest="
                            + raceQuestId + ", source=race");
                    } else {
                        acceptOnboardingQuest(player, raceQuestId, "race=" + raceId);
                    }
                    resolveCharacterCompletionDestination(startingRealm).ifPresent(destination ->
                        player.teleportAsync(destination).whenComplete((teleported, teleportFailure) -> {
                            if (teleportFailure != null || !Boolean.TRUE.equals(teleported)) {
                                plugin.getLogger().warning("[Onboarding] Could not teleport after character creation: player="
                                    + playerName + ", world=" + destination.getWorld().getName()
                                    + (teleportFailure == null ? "" : ": " + teleportFailure.getMessage()));
                            } else {
                                debugProfile("character-complete-teleport player=" + playerName + ", world="
                                    + destination.getWorld().getName());
                            }
                        }));
                }
                debugProfile("create-result player=" + playerName + ", created=true, resetProfile="
                    + completingResetProfile);
                player.sendMessage("§aĐã tạo nhân vật: §6" + race.name() + " §7- §b" + selectedClass.name());
                player.closeInventory();
            } else {
                debugProfile("create-result player=" + playerName + ", created=false, reason=profile-exists");
                player.sendMessage("§cTài khoản của bạn đã có nhân vật. Không có dữ liệu nào bị ghi đè.");
                player.closeInventory();
            }
        }));
    }

    private void acceptOnboardingQuest(Player player, String questId, String source) {
        if (questId == null || questId.isBlank() || plugin.getQuestCatalogManager() == null) return;
        if (!plugin.getQuestCatalogManager().isEnabledQuest(questId)) {
            plugin.getLogger().warning("[Onboarding] Ignoring unavailable " + source + " quest: player="
                + player.getName() + ", quest=" + questId);
            return;
        }
        if (!plugin.getQuestCatalogManager().acceptConfiguredQuest(player, questId)) {
            plugin.getLogger().warning("[Onboarding] Could not accept " + source + " quest: player="
                + player.getName() + ", quest=" + questId);
        } else {
            plugin.getLogger().info("[Onboarding] Auto-quest accepted: player=" + player.getName()
                + ", source=" + source + ", quest=" + questId);
        }
    }

    private java.util.Optional<Location> resolveCharacterCompletionDestination(
            HumanRealmDefinitionLoader.HumanRealmDefinition startingRealm) {
        if (!plugin.getConfig().getBoolean("rpg.onboarding.character-complete.teleport-enabled", true)) {
            debugProfile("character-complete-teleport disabled-by-config");
            return java.util.Optional.empty();
        }
        String mode = plugin.getConfig().getString("rpg.onboarding.character-complete.teleport-mode", "REALM_SPAWN");
        if ("NONE".equalsIgnoreCase(mode)) return java.util.Optional.empty();
        if ("START_WORLD".equalsIgnoreCase(mode)) {
            World world = Bukkit.getWorld(plugin.getConfig().getString("rpg.onboarding.start-world.name", "world_start"));
            return world == null ? java.util.Optional.empty() : java.util.Optional.of(world.getSpawnLocation());
        }
        if ("ADMIN_LOCATION".equalsIgnoreCase(mode)) {
            var config = plugin.getConfig();
            World world = Bukkit.getWorld(config.getString("rpg.onboarding.character-complete.location.world", ""));
            String base = "rpg.onboarding.character-complete.location.";
            if (world == null || !config.contains(base + "x") || !config.contains(base + "y")
                    || !config.contains(base + "z")) return java.util.Optional.empty();
            return java.util.Optional.of(new Location(world, config.getDouble(base + "x"),
                config.getDouble(base + "y"), config.getDouble(base + "z"),
                (float)config.getDouble(base + "yaw"), (float)config.getDouble(base + "pitch")));
        }
        if (startingRealm == null || plugin.getRpgWorldManager() == null) return java.util.Optional.empty();
        return plugin.getRpgWorldManager().resolveSpawn(startingRealm.id()).map(RpgWorldManager.ResolvedSpawn::location);
    }

    private void debugProfile(String detail) {
        if (plugin.getConfig().getBoolean("logging.console-debug", false)) {
            plugin.getLogger().info("[RPG-DEBUG][Profile] " + detail);
        }
    }

    private boolean isCreationAvailable(Player player) {
        if (!plugin.getConfig().getBoolean("rpg.character-creation.enabled", true)) {
            player.sendMessage("§cTính năng tạo nhân vật RPG hiện đang tắt.");
            return false;
        }
        if (!definitionsAvailable) {
            player.sendMessage("§cTính năng tạo nhân vật tạm thời không khả dụng. Hãy báo quản trị viên.");
            return false;
        }
        return true;
    }

    private boolean hasExistingProfile(Player player) {
        UUID playerId = player.getUniqueId();
        if (!profileLookupComplete.contains(playerId)) {
            player.sendMessage("§eĐang tải hồ sơ nhân vật. Hãy thử lại sau giây lát.");
            return true;
        }
        PlayerProfileDatabaseManager.PlayerProfile profile = loadedProfiles.get(playerId);
        if (profile != null && profile.selectedRace() != null && profile.selectedClass() != null) {
            player.sendMessage("§cTài khoản của bạn đã có nhân vật. Chức năng nhiều nhân vật chưa được hỗ trợ.");
            return true;
        }
        return false;
    }

    private void loadDefinitions() {
        File raceFile = loadBundledConfig("races.yml");
        raceDefinitionsFile = raceFile;
        File classFile = loadBundledConfig("player-classes.yml");
        YamlConfiguration raceConfig = loadStrictConfig(raceFile);
        YamlConfiguration classConfig = loadStrictConfig(classFile);
        int addedRaces = mergeMissingDefinitions(raceConfig, "races", "races.yml");
        int addedClasses = mergeMissingDefinitions(classConfig, "classes", "player-classes.yml");
        if (addedRaces > 0 || addedClasses > 0) {
            plugin.getLogger().info("[Character] Added missing bundled definitions in memory without changing server files: races="
                + addedRaces + ", classes=" + addedClasses + ".");
        }
        ConfigurationSection raceSection = raceConfig.getConfigurationSection("races");
        ConfigurationSection classSection = classConfig.getConfigurationSection("classes");
        if (raceSection == null || classSection == null) {
            throw new IllegalStateException("Both races.yml:races and player-classes.yml:classes are required.");
        }

        for (String id : raceSection.getKeys(false)) {
            ConfigurationSection section = raceSection.getConfigurationSection(id);
            if (section == null || !DEFINITION_ID_PATTERN.matcher(id).matches()) {
                plugin.getLogger().warning("[Character] Invalid race definition at races.yml:races." + id + ".");
                continue;
            }
            String name = readText(section, "name", "races.yml:races." + id);
            String description = readText(section, "description", "races.yml:races." + id);
            Material icon = readMaterial(section, "icon", "races.yml:races." + id);
            if (name != null && description != null && icon != null) {
                races.put(id, new RaceDefinition(id, name, icon, description));
                classesByRace.put(id, new java.util.ArrayList<>());
                String onboardingQuest = section.getString("onboarding.auto-quest-id", "").trim();
                if (!onboardingQuest.isEmpty()) {
                    if (DEFINITION_ID_PATTERN.matcher(onboardingQuest).matches()) {
                        raceOnboardingQuestIds.put(id, onboardingQuest);
                    } else {
                        plugin.getLogger().warning("[RaceSetup] Ignoring invalid onboarding quest ID '"
                            + onboardingQuest + "' for race=" + id + ".");
                    }
                }
            }
        }

        for (String id : classSection.getKeys(false)) {
            ConfigurationSection section = classSection.getConfigurationSection(id);
            if (section == null || !DEFINITION_ID_PATTERN.matcher(id).matches()) {
                plugin.getLogger().warning("[Character] Invalid class definition at player-classes.yml:classes." + id + ".");
                continue;
            }
            String path = "player-classes.yml:classes." + id;
            String raceId = readText(section, "race", path);
            String name = readText(section, "name", path);
            String description = readText(section, "description", path);
            Material icon = readMaterial(section, "icon", path);
            String availability = section.contains("availability")
                ? readText(section, "availability", path) : "auto";
            String parentClassId = section.contains("parent-class-id")
                ? readText(section, "parent-class-id", path) : null;
            int promotionLevel = 1;
            if (section.contains("required-level")) {
                Object rawLevel = section.get("required-level");
                if (!(rawLevel instanceof Number number) || number.doubleValue() != number.intValue()
                        || number.intValue() < 1 || number.intValue() > 10000) {
                    plugin.getLogger().warning("[ClassProgression] Invalid required-level at " + path
                        + ".required-level; expected an integer from 1 to 10000. Class skipped.");
                    continue;
                }
                promotionLevel = number.intValue();
            }
            String promotionQuest = null;
            if (section.contains("required-quest-id")) {
                promotionQuest = readText(section, "required-quest-id", path);
                if (promotionQuest == null || !DEFINITION_ID_PATTERN.matcher(promotionQuest).matches()) {
                    plugin.getLogger().warning("[ClassProgression] Invalid required-quest-id at " + path
                        + ".required-quest-id; expected a lowercase quest ID. Class skipped.");
                    continue;
                }
            }
            String promotionNpc = null;
            if (section.contains("required-npc-id")) {
                promotionNpc = readText(section, "required-npc-id", path);
                if (promotionNpc == null || !DEFINITION_ID_PATTERN.matcher(promotionNpc).matches()) {
                    plugin.getLogger().warning("[ClassProgression] Invalid required-npc-id at " + path
                        + ".required-npc-id; expected a lowercase NPC ID. Class skipped.");
                    continue;
                }
            }
            Material promotionItem = null;
            int promotionItemAmount = 0;
            if (section.contains("required-item") || section.contains("required-item-amount")) {
                if (!section.contains("required-item") || !section.contains("required-item-amount")) {
                    plugin.getLogger().warning("[ClassProgression] required-item and required-item-amount must "
                        + "both be set at " + path + ". Class skipped.");
                    continue;
                }
                promotionItem = readMaterial(section, "required-item", path);
                Object rawAmount = section.get("required-item-amount");
                if (promotionItem == null || !(rawAmount instanceof Number amount)
                        || amount.doubleValue() != amount.intValue() || amount.intValue() < 1
                        || amount.intValue() > 2304) {
                    plugin.getLogger().warning("[ClassProgression] Invalid required item at " + path
                        + "; amount must be an integer from 1 to 2304. Class skipped.");
                    continue;
                }
                promotionItemAmount = amount.intValue();
            }
            String promotionRpgItem = null;
            int promotionRpgItemAmount = 0;
            if (section.contains("required-rpg-item-id") || section.contains("required-rpg-item-amount")) {
                if (!section.contains("required-rpg-item-id") || !section.contains("required-rpg-item-amount")) {
                    plugin.getLogger().warning("[ClassProgression] required-rpg-item-id and "
                        + "required-rpg-item-amount must both be set at " + path + ". Class skipped.");
                    continue;
                }
                promotionRpgItem = readText(section, "required-rpg-item-id", path);
                Object rawAmount = section.get("required-rpg-item-amount");
                if (promotionRpgItem == null || !DEFINITION_ID_PATTERN.matcher(promotionRpgItem).matches()
                        || !(rawAmount instanceof Number amount) || amount.doubleValue() != amount.intValue()
                        || amount.intValue() < 1 || amount.intValue() > 2304) {
                    plugin.getLogger().warning("[ClassProgression] Invalid RPG item requirement at " + path
                        + "; expected valid RPG item ID and integer amount from 1 to 2304. Class skipped.");
                    continue;
                }
                promotionRpgItemAmount = amount.intValue();
            }
            if (raceId == null || name == null || description == null || icon == null || availability == null) continue;
            if (!Set.of("auto", "available", "locked", "planned").contains(availability)) {
                plugin.getLogger().warning("[Character] Invalid availability '" + availability
                    + "' at " + path + ".availability; expected auto, available, locked or planned.");
                continue;
            }
            List<ClassDefinition> classList = classesByRace.get(raceId);
            if (classList == null) {
                plugin.getLogger().warning("[Character] Unknown race '" + raceId + "' at " + path + ".race.");
                continue;
            }
            classList.add(new ClassDefinition(raceId, id, name, icon, description, availability));
            if (parentClassId != null) parentClassById.put(id, parentClassId);
            if (parentClassId != null) promotionLevelByClass.put(id, promotionLevel);
            if (parentClassId != null && promotionQuest != null) promotionQuestByClass.put(id, promotionQuest);
            if (parentClassId != null && promotionNpc != null) promotionNpcByClass.put(id, promotionNpc);
            if (parentClassId != null && promotionItem != null) {
                promotionItemByClass.put(id, promotionItem);
                promotionItemAmountByClass.put(id, promotionItemAmount);
            }
            if (parentClassId != null && promotionRpgItem != null) {
                promotionRpgItemByClass.put(id, promotionRpgItem);
                promotionRpgItemAmountByClass.put(id, promotionRpgItemAmount);
            }
        }

        validateClassInheritance();

        if (races.isEmpty()) {
            throw new IllegalStateException("No valid race/class definitions were loaded.");
        }
        for (Map.Entry<String, List<ClassDefinition>> entry : classesByRace.entrySet()) {
            if (entry.getValue().size() > MENU_SLOTS.length) {
                throw new IllegalStateException("Too many classes for race '" + entry.getKey() +
                    "'; maximum supported in the selection menu is " + MENU_SLOTS.length + ".");
            }
        }
        if (races.size() > MENU_SLOTS.length) {
            throw new IllegalStateException("Too many race definitions; maximum supported in the selection menu is " +
                MENU_SLOTS.length + ".");
        }
    }

    public void validateConfiguredRaceQuests() {
        raceOnboardingQuestIds.entrySet().removeIf(entry -> {
            boolean valid = plugin.getQuestCatalogManager() != null
                && plugin.getQuestCatalogManager().isEnabledQuest(entry.getValue());
            if (!valid) plugin.getLogger().warning("[RaceSetup] Race quest is missing or disabled; ignored mapping: race="
                + entry.getKey() + ", quest=" + entry.getValue());
            return !valid;
        });
    }

    private void validateClassInheritance() {
        Map<String, ClassDefinition> definitions = new HashMap<>();
        classesByRace.values().forEach(list -> list.forEach(definition -> definitions.put(definition.id(), definition)));
        for (Map.Entry<String, String> entry : parentClassById.entrySet()) {
            ClassDefinition child = definitions.get(entry.getKey());
            ClassDefinition parent = definitions.get(entry.getValue());
            if (parent == null || child == null || !child.raceId().equals(parent.raceId())) {
                throw new IllegalStateException("Invalid class parent '" + entry.getValue() + "' for '"
                    + entry.getKey() + "'; parent must exist and belong to the same race.");
            }
            java.util.HashSet<String> visited = new java.util.HashSet<>();
            String cursor = entry.getKey();
            while (cursor != null) {
                if (!visited.add(cursor)) {
                    throw new IllegalStateException("Class inheritance cycle detected at '" + cursor + "'.");
                }
                cursor = parentClassById.get(cursor);
            }
        }
        for (Map.Entry<String, String> entry : parentClassById.entrySet()) {
            plugin.getLogger().info("[ClassProgression] inheritance-loaded child=" + entry.getKey()
                + ", parent=" + entry.getValue() + ", inherited=" + getInheritedClassIds(entry.getKey()));
        }
    }

    private File loadBundledConfig(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        return file;
    }

    private int mergeMissingDefinitions(YamlConfiguration target, String sectionName, String resourceName) {
        YamlConfiguration defaults;
        try (java.io.InputStream stream = plugin.getResource(resourceName)) {
            if (stream == null) return 0;
            defaults = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(stream,
                java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException exception) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                "[Character] Could not read bundled defaults from " + resourceName + ".", exception);
            return 0;
        }
        ConfigurationSection defaultSection = defaults.getConfigurationSection(sectionName);
        ConfigurationSection targetSection = target.getConfigurationSection(sectionName);
        if (defaultSection == null) return 0;
        int added = 0;
        for (String id : defaultSection.getKeys(false)) {
            if (targetSection != null && targetSection.contains(id)) continue;
            ConfigurationSection definition = defaultSection.getConfigurationSection(id);
            if (definition == null) continue;
            copyConfigurationSection(definition, target, sectionName + "." + id);
            added++;
        }
        return added;
    }

    private void copyConfigurationSection(ConfigurationSection source, YamlConfiguration target, String targetPath) {
        for (String key : source.getKeys(false)) {
            ConfigurationSection nested = source.getConfigurationSection(key);
            if (nested == null) target.set(targetPath + "." + key, source.get(key));
            else copyConfigurationSection(nested, target, targetPath + "." + key);
        }
    }

    private YamlConfiguration loadStrictConfig(File file) {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            YamlDuplicateKeyValidator.validateNoDuplicateKeys(file);
            configuration.load(file);
            return configuration;
        } catch (IOException | InvalidConfigurationException | YAMLException exception) {
            throw new IllegalStateException("Cannot load character definitions from " + file.getName() + ".", exception);
        }
    }

    private String readText(ConfigurationSection section, String key, String path) {
        Object value = section.get(key);
        if (value instanceof String text && !text.isBlank()) return text;
        plugin.getLogger().warning("[Character] Missing or invalid text at " + path + "." + key + ".");
        return null;
    }

    private Material readMaterial(ConfigurationSection section, String key, String path) {
        String value = readText(section, key, path);
        Material material = value == null ? null : Material.matchMaterial(value);
        if (material == null || !material.isItem()) {
            plugin.getLogger().warning("[Character] Missing or invalid Material at " + path + "." + key + ".");
            return null;
        }
        return material;
    }

    private ClassDefinition findClass(String raceId, String classId) {
        return classesByRace.getOrDefault(raceId, Collections.emptyList()).stream()
            .filter(klass -> klass.id().equals(classId))
            .findFirst()
            .orElse(null);
    }

    private ClassDefinition findClassForId(String classId) {
        return classesByRace.values().stream().flatMap(List::stream)
            .filter(klass -> klass.id().equals(classId)).findFirst().orElse(null);
    }

    public ClassDefinition getClassDefinition(String classId) {
        return classId == null ? null : findClassForId(classId);
    }

    private boolean isClassSelectable(ClassDefinition klass) {
        return klass != null && ("available".equals(klass.availability())
                || "auto".equals(klass.availability()))
            && playableClassIds.contains(klass.id()) && !parentClassById.containsKey(klass.id());
    }

    private ItemStack createRaceItem(RaceDefinition race) {
        return createSelectionItem(race.material(), "§6" + race.name(), List.of(
            "§7" + race.description(),
            "§8ID: " + race.id(),
            "§aNhấn để chọn chủng tộc này."));
    }

    private ItemStack createClassItem(RaceDefinition race, ClassDefinition klass) {
        return createSelectionItem(klass.material(), "§b" + klass.name(), List.of(
            "§7" + klass.description(),
            "§8" + race.name() + " • " + klass.id(),
            "§aNhấn để xem trước và xác nhận."));
    }

    private ItemStack createActionItem(Material material, String title, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(title);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSelectionItem(Material material, String title, List<String> lore) {
        return createActionItem(material, title, lore);
    }

    public record RaceDefinition(String id, String name, Material material, String description) {
    }

    public record ClassDefinition(String raceId, String id, String name, Material material, String description,
                                  String availability) {
    }

    private static final class CharacterMenuHolder implements InventoryHolder {
        private final UUID viewer;
        private final String type;
        private final String raceId;
        private final String classId;
        private final Map<Integer, String> choices;
        private final int page;
        private Inventory inventory;

        private CharacterMenuHolder(UUID viewer, String type, String raceId, String classId,
                                    Map<Integer, String> choices) {
            this(viewer, type, raceId, classId, choices, 0);
        }

        private CharacterMenuHolder(UUID viewer, String type, String raceId, String classId,
                                    Map<Integer, String> choices, int page) {
            this.viewer = viewer;
            this.type = type;
            this.raceId = raceId;
            this.classId = classId;
            this.choices = choices;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
