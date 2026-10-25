package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
    private final Map<String, RaceDefinition> races = new LinkedHashMap<>();
    private final Map<String, List<ClassDefinition>> classesByRace = new HashMap<>();
    private final Map<UUID, PlayerProfileDatabaseManager.PlayerProfile> loadedProfiles = new HashMap<>();
    private final Set<UUID> profileLookupComplete = new java.util.HashSet<>();
    private final Set<UUID> pendingProfileCreates = new java.util.HashSet<>();
    private Set<String> playableClassIds = Set.of();
    private boolean definitionsAvailable;

    public CharacterSelectionManager(VnMineHardcore plugin) {
        this.plugin = plugin;
        try {
            loadDefinitions();
            definitionsAvailable = true;
        } catch (IllegalStateException exception) {
            races.clear();
            classesByRace.clear();
            plugin.getLogger().log(Level.SEVERE,
                "[Character] Character creation is disabled because its race/class configuration is invalid.",
                exception);
        }
    }

    public void openRaceMenu(Player player) {
        if (!isCreationAvailable(player)) return;
        if (hasExistingProfile(player)) return;
        CharacterMenuHolder holder = new CharacterMenuHolder(player.getUniqueId(), "race", null, null, new HashMap<>());
        Inventory inventory = Bukkit.createInventory(holder, 54, "VnMine • Chọn chủng tộc");
        holder.inventory = inventory;
        int index = 0;
        for (RaceDefinition race : races.values()) {
            if (classesByRace.getOrDefault(race.id(), Collections.emptyList()).stream()
                    .noneMatch(this::isClassSelectable)) {
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

    public void setPlayableClassIds(Set<String> classIds) {
        playableClassIds = Set.copyOf(classIds);
        for (String classId : getClassIds()) {
            ClassDefinition definition = findClassForId(classId);
            if (definition != null && "available".equals(definition.availability())
                    && !playableClassIds.contains(classId)) {
                plugin.getLogger().warning("[Character] Class '" + classId
                    + "' is marked available but hidden because no implemented RPG skill supports it.");
            } else if (definition != null && !"available".equals(definition.availability())) {
                plugin.getLogger().info("[Character] Class '" + classId + "' is " + definition.availability()
                    + " and hidden from character creation.");
            }
        }
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
        Inventory inventory = Bukkit.createInventory(holder, 27, "VnMine • Xác nhận nhân vật");
        holder.inventory = inventory;
        inventory.setItem(11, createActionItem(Material.LIME_CONCRETE, "§aXác nhận tạo nhân vật", List.of(
            "§7Chủng tộc: §6" + race.name(),
            "§7Class: §b" + selectedClass.name(),
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
            } else if (slot == 22 && plugin.getMercenaryManager() != null) {
                plugin.getMercenaryManager().openMainMenu(player);
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
                    return;
                }
                PlayerProfileDatabaseManager.PlayerProfile loadedProfile = profile.get();
                loadedProfiles.put(playerId, loadedProfile);
                if (!races.containsKey(loadedProfile.selectedRace())
                        || findClass(loadedProfile.selectedRace(), loadedProfile.selectedClass()) == null) {
                    plugin.getLogger().warning("[Profile] Stored race/class definition is missing for player " +
                        player.getName() + "; profile data was preserved.");
                }
            }));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        loadedProfiles.remove(playerId);
        profileLookupComplete.remove(playerId);
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
                    openRaceMenu(player);
                }
            }));
    }

    static List<String> buildProfileLore(PlayerProfileDatabaseManager.PlayerProfile profile,
                                         String raceName, String className,
                                         CharacterProgression.Rules progressionRules) {
        String homeWorld = profile.homeWorld() == null || profile.homeWorld().isBlank()
            ? "Chưa thiết lập" : profile.homeWorld();
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
        CharacterMenuHolder holder = new CharacterMenuHolder(
            player.getUniqueId(), "profile", null, null, new HashMap<>());
        Inventory inventory = Bukkit.createInventory(holder, 27, "VnMine • Hồ sơ nhân vật");
        holder.inventory = inventory;
        inventory.setItem(11, createActionItem(Material.SUNFLOWER, "§6Ví VNCOIN: §7Đang tải...",
            List.of("§7Số dư đơn vị tiền tệ của máy chủ.")));
        inventory.setItem(13, createActionItem(Material.PLAYER_HEAD, "§dHồ sơ RPG",
            buildProfileLore(profile, raceName, className,
                plugin.getPlayerProfileDatabaseManager().getCharacterProgressionRules())));
        if (plugin.getConfig().getBoolean("rpg.quest-journal.enabled", true)) {
            inventory.setItem(15, createActionItem(Material.WRITABLE_BOOK, "§bNhật ký nhiệm vụ", List.of(
                "§7Xem checkpoint và trạng thái nhiệm vụ tutorial.")));
        }
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
                } else {
                    loadedProfiles.remove(playerId);
                    debugProfile("refresh-result player=" + player.getName() + ", found=false");
                }
            }));
    }

    public void finalizeSelection(Player player, String raceId, String classId) {
        if (!isCreationAvailable(player)) return;
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
        debugProfile("create-start player=" + playerName + ", race=" + raceId + ", class=" + classId);
        String now = Instant.now().toString();
        plugin.getPlayerProfileDatabaseManager().executeGameplayDatabaseTask(
                "profile.create player=" + playerName, () ->
                plugin.getPlayerProfileDatabaseManager().createPlayerProfile(
                    playerId.toString(), playerName, raceId, classId, 1, 0, null, now))
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
                    playerId.toString(), playerName, raceId, classId, 1, 0, null, now, now, true);
                loadedProfiles.put(playerId, profile);
                profileLookupComplete.add(playerId);
                debugProfile("create-result player=" + playerName + ", created=true");
                player.sendMessage("§aĐã tạo nhân vật: §6" + race.name() + " §7- §b" + selectedClass.name());
                player.closeInventory();
                plugin.getTutorialQuestManager().initializePlayer(player);
            } else {
                debugProfile("create-result player=" + playerName + ", created=false, reason=profile-exists");
                player.sendMessage("§cTài khoản của bạn đã có nhân vật. Không có dữ liệu nào bị ghi đè.");
                player.closeInventory();
            }
        }));
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
        if (loadedProfiles.containsKey(playerId)) {
            player.sendMessage("§cTài khoản của bạn đã có nhân vật. Chức năng nhiều nhân vật chưa được hỗ trợ.");
            return true;
        }
        return false;
    }

    private void loadDefinitions() {
        File raceFile = loadBundledConfig("races.yml");
        File classFile = loadBundledConfig("player-classes.yml");
        YamlConfiguration raceConfig = loadStrictConfig(raceFile);
        YamlConfiguration classConfig = loadStrictConfig(classFile);
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
        }

        races.entrySet().removeIf(entry -> {
            List<ClassDefinition> classList = classesByRace.get(entry.getKey());
            if (classList != null && !classList.isEmpty()) return false;
            plugin.getLogger().warning("[Character] Skipping race '" + entry.getKey() + "' because it has no valid classes.");
            classesByRace.remove(entry.getKey());
            return true;
        });
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

    private File loadBundledConfig(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        return file;
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

    private boolean isClassSelectable(ClassDefinition klass) {
        return klass != null && ("available".equals(klass.availability())
                || "auto".equals(klass.availability()))
            && playableClassIds.contains(klass.id());
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
        private Inventory inventory;

        private CharacterMenuHolder(UUID viewer, String type, String raceId, String classId,
                                    Map<Integer, String> choices) {
            this.viewer = viewer;
            this.type = type;
            this.raceId = raceId;
            this.classId = classId;
            this.choices = choices;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
