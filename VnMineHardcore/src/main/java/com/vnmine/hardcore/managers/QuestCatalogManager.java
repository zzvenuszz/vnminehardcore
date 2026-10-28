package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.yaml.snakeyaml.error.YAMLException;

/** Data-driven entry point for NPC-given combat quests. */
public final class QuestCatalogManager implements Listener, CommandExecutor, TabCompleter {
    private static final String ADMIN_PERMISSION = "vnmine.command.quest.admin";
    private static final double TRACKING_PARTICLE_RANGE = 36.0;
    private final VnMineHardcore plugin;
    private final File definitionsFile;
    private final NamespacedKey questItemKey;
    private final NamespacedKey rpgSellValueKey;
    private final NamespacedKey waypointParticlesKey;
    private final Map<String, QuestDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, QuestDialogRefs> questDialogs = new LinkedHashMap<>();
    private final Map<String, Long> questItemSellValues = new LinkedHashMap<>();
    private final Map<String, List<Long>> questRewardSellValues = new LinkedHashMap<>();
    private final Map<String, Boolean> repeatableQuests = new LinkedHashMap<>();
    private final Map<String, Integer> repeatDailyLimits = new LinkedHashMap<>();
    private final Map<UUID, Map<String, PlayerProfileDatabaseManager.QuestProgress>> activeByPlayer =
        new ConcurrentHashMap<>();
    private final Map<UUID, java.util.Set<String>> completedByPlayer = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> questStateLoaded = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PendingTextInput> pendingTextInputs = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack> pendingRequirementItems = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack> pendingObjectiveItems = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCrossWorldHint = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> creditedBossEncounters = ConcurrentHashMap.newKeySet();
    private final java.util.Set<UUID> processingPendingSuccessors = ConcurrentHashMap.newKeySet();

    public QuestCatalogManager(VnMineHardcore plugin) throws IOException {
        this.plugin = plugin;
        this.questItemKey = new NamespacedKey(plugin, "quest_item_id");
        this.rpgSellValueKey = new NamespacedKey(plugin, "rpg_sell_value");
        this.waypointParticlesKey = new NamespacedKey(plugin, "quest_waypoint_particles");
        File directory = new File(plugin.getDataFolder(), "quests");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Could not create quest configuration directory.");
        }
        definitionsFile = new File(directory, "quest-definitions.yml");
        if (!definitionsFile.isFile()) {
            try {
                plugin.saveResource("quests/quest-definitions.yml", false);
            } catch (IllegalArgumentException exception) {
                throw new IOException("Bundled quest-definitions.yml is missing.", exception);
            }
        }
        loadDefinitions();
        int updateTicks = Math.max(5, Math.min(40,
            plugin.getConfig().getInt("rpg.quests.tracking.update-interval-ticks", 10)));
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateTrackedQuestWaypoints,
            updateTicks, updateTicks);
    }

    public void openAdmin(Player player) {
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            player.sendMessage("§cBạn không có quyền quản lý quest.");
            return;
        }
        openAdminList(player, 0);
    }

    public List<String> getQuestIdsForNpc(String npcId) {
        if (npcId == null) return List.of();
        return definitions.values().stream().filter(quest -> quest.enabled() && quest.giverNpcId().equals(npcId))
            .map(QuestDefinition::id).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public String getQuestTitle(String questId) {
        QuestDefinition quest = definitions.get(questId);
        return quest == null ? questId : quest.title();
    }

    public void openAdminCompletedQuests(Player admin, UUID targetId) {
        openAdminCompletedQuests(admin, targetId, 0);
    }

    private void openAdminCompletedQuests(Player admin, UUID targetId, int requestedPage) {
        if (!admin.hasPermission(ADMIN_PERMISSION)
                && !admin.hasPermission("vnmine.command.playeradmin")) return;
        PlayerProfileDatabaseManager database = plugin.getPlayerProfileDatabaseManager();
        database.executeGameplayDatabaseTask("quest.admin-history actor=" + admin.getName()
                + " target=" + targetId, () -> database.getCompletedQuestIds(targetId.toString()))
            .whenComplete((completed, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (!admin.isOnline()) return;
                if (failure != null) {
                    plugin.getLogger().log(Level.SEVERE, "[Quest][Admin] Could not load completed quests for "
                        + targetId, failure);
                    admin.sendMessage("§cKhông thể tải quest đã hoàn thành.");
                    return;
                }
                List<QuestDefinition> quests = completed.stream().map(definitions::get)
                    .filter(java.util.Objects::nonNull)
                    .sorted(java.util.Comparator.comparing(QuestDefinition::title,
                        String.CASE_INSENSITIVE_ORDER)).toList();
                int pageCount = Math.max(1, (quests.size() + 44) / 45);
                int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
                QuestMenuHolder holder = new QuestMenuHolder(admin.getUniqueId(),
                    QuestMenu.ADMIN_PLAYER_HISTORY, null, page);
                holder.npcId = targetId.toString();
                Inventory menu = Bukkit.createInventory(holder, 54, "Admin • Quest hoàn thành");
                holder.inventory = menu;
                for (int slot = 0; slot < Math.min(45, quests.size() - page * 45); slot++) {
                    QuestDefinition quest = quests.get(page * 45 + slot);
                    holder.questSlots.put(slot, quest.id());
                    menu.setItem(slot, adminItem(Material.ENCHANTED_BOOK, "§a" + quest.title(), List.of(
                        "§7ID: " + quest.id(), "§aĐã hoàn thành", "§eNhấn để đánh dấu chưa hoàn thành.")));
                }
                if (quests.isEmpty()) menu.setItem(22, adminItem(Material.BOOK, "§7Không có quest hoàn thành", List.of()));
                if (page > 0) menu.setItem(45, adminItem(Material.ARROW, "§eTrang trước", List.of()));
                if (page + 1 < pageCount) menu.setItem(53, adminItem(Material.ARROW, "§eTrang sau", List.of()));
                menu.setItem(49, adminItem(Material.ARROW, "§eQuay lại hồ sơ", List.of()));
                admin.openInventory(menu);
                debugQuest("admin-history actor=" + admin.getName() + ", target=" + targetId
                    + ", completed=" + quests.size());
            }));
    }

    public List<AdminRpgItem> getAdminRpgItems() {
        Map<String, AdminRpgItem> items = new LinkedHashMap<>();
        definitions.values().stream().sorted(java.util.Comparator.comparing(QuestDefinition::id)).forEach(quest -> {
            for (QuestObjective objective : quest.objectives()) {
                if (objective.type() != ObjectiveType.COLLECT_RPG_ITEM || objective.itemId() == null
                        || objective.itemId().isBlank() || objective.itemMaterial() == null
                        || !objective.itemMaterial().isItem()) continue;
                ItemStack item = createQuestItem(objective.itemId(), objective.itemMaterial(), objective.itemName(),
                    questItemSellValues.getOrDefault(quest.id(), 0L));
                items.putIfAbsent(objective.itemId(), new AdminRpgItem(objective.itemId(),
                    objective.itemName(), objective.itemMaterial(), item));
            }
        });
        return List.copyOf(items.values());
    }

    public boolean giveAdminRpgItem(Player admin, UUID targetId, String questItemId) {
        if (admin == null || !admin.hasPermission("vnmine.command.playeradmin")) return false;
        QuestDefinition quest = definitions.values().stream()
            .filter(value -> value.objectiveType() == ObjectiveType.COLLECT_RPG_ITEM
                && value.itemId().equals(questItemId)).findFirst().orElse(null);
        if (quest == null) return false;
        ItemStack item = createQuestItem(quest);
        List<byte[]> delivery = List.of(item.serializeAsBytes());
        Player target = Bukkit.getPlayer(targetId);
        PlayerProfileDatabaseManager database = plugin.getPlayerProfileDatabaseManager();
        String transaction = "admin-rpg-item:" + UUID.randomUUID();
        if (target == null) {
            database.executeGameplayDatabaseTask("player-admin.give-rpg-item actor=" + admin.getName()
                    + " target=" + targetId + " item=" + questItemId,
                () -> {
                    database.enqueueMerchantDeliveries(targetId.toString(), transaction, delivery,
                        Instant.now().toString());
                    return true;
                })
                .whenComplete((ignored, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (failure != null) {
                        plugin.getLogger().log(Level.SEVERE, "[PlayerAdmin] Could not queue RPG item delivery.", failure);
                        admin.sendMessage("§cKhông thể xếp item vào kho nhận.");
                    } else {
                        plugin.getLogger().info("[PlayerAdmin] actor=" + admin.getName() + ", target=" + targetId
                            + ", action=give-rpg-item, item=" + questItemId + ", amount=1, delivery=queued");
                        admin.sendMessage("§aĐã xếp item RPG vào kho nhận của người chơi offline.");
                    }
                }));
            return true;
        }
        Map<Integer, ItemStack> leftover = target.getInventory().addItem(item);
        if (leftover.isEmpty()) {
            target.saveData();
            plugin.getLogger().info("[PlayerAdmin] actor=" + admin.getName() + ", target=" + target.getName()
                + ", action=give-rpg-item, item=" + questItemId + ", amount=1, delivery=inventory");
            admin.sendMessage("§aĐã cấp item RPG cho " + target.getName() + ".");
            return true;
        }
        List<byte[]> overflow = leftover.values().stream().map(ItemStack::serializeAsBytes).toList();
        database.executeGameplayDatabaseTask("player-admin.give-rpg-overflow actor=" + admin.getName()
                + " target=" + targetId + " item=" + questItemId,
            () -> {
                database.enqueueMerchantDeliveries(targetId.toString(), transaction, overflow, Instant.now().toString());
                return true;
            })
            .whenComplete((ignored, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (failure != null) {
                    plugin.getLogger().log(Level.SEVERE, "[PlayerAdmin] Could not queue RPG item overflow.", failure);
                    admin.sendMessage("§cInventory gần đầy; phần item còn lại chưa được giao, hãy kiểm tra console.");
                } else {
                    target.saveData();
                    plugin.getLogger().info("[PlayerAdmin] actor=" + admin.getName() + ", target=" + target.getName()
                        + ", action=give-rpg-item, item=" + questItemId + ", amount=1, delivery=partial+queue");
                    admin.sendMessage("§aĐã cấp item; phần tràn được xếp vào kho nhận.");
                }
            }));
        return true;
    }

    public record AdminRpgItem(String id, String name, Material material, ItemStack item) { }

    private void confirmAdminQuestReset(Player admin, UUID targetId, String questId) {
        QuestDefinition quest = definitions.get(questId);
        if (quest == null) return;
        QuestMenuHolder holder = new QuestMenuHolder(admin.getUniqueId(), QuestMenu.ADMIN_RESET_CONFIRM,
            questId, getOpenAdminHistoryPage(admin, targetId));
        holder.npcId = targetId.toString();
        Inventory menu = Bukkit.createInventory(holder, 27, "Xác nhận reset quest");
        holder.inventory = menu;
        menu.setItem(13, adminItem(Material.REDSTONE, "§cĐánh dấu quest chưa hoàn thành?", List.of(
            "§7Người chơi: §f" + Bukkit.getOfflinePlayer(targetId).getName(),
            "§7Quest: §f" + quest.title(),
            "§7Có thể nhận lại và phần thưởng được xử lý như lần chạy mới.")));
        menu.setItem(11, adminItem(Material.LIME_CONCRETE, "§aXác nhận", List.of()));
        menu.setItem(15, adminItem(Material.RED_CONCRETE, "§cHủy", List.of()));
        admin.openInventory(menu);
    }

    private void resetAdminQuest(Player admin, UUID targetId, String questId, int page) {
        PlayerProfileDatabaseManager database = plugin.getPlayerProfileDatabaseManager();
        database.executeGameplayDatabaseTask("quest.admin-reset actor=" + admin.getName()
                + " target=" + targetId + " quest=" + questId,
                () -> database.resetCompletedQuest(targetId.toString(), questId))
            .whenComplete((reset, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (!admin.isOnline()) return;
                if (failure != null) {
                    plugin.getLogger().log(Level.SEVERE, "[Quest][Admin] Could not reset quest " + questId
                        + " for " + targetId, failure);
                    admin.sendMessage("§cKhông thể reset quest; dữ liệu không được xác nhận.");
                    return;
                }
                if (reset) {
                    Player target = Bukkit.getPlayer(targetId);
                    if (target != null) reloadQuestCache(target);
                    plugin.getLogger().info("[Quest][Admin] actor=" + admin.getName() + ", target=" + targetId
                        + ", action=reset-completed, quest=" + questId);
                    admin.sendMessage("§aĐã cho phép nhận lại quest " + questId + ".");
                } else admin.sendMessage("§eQuest không còn ở trạng thái hoàn thành.");
                openAdminCompletedQuests(admin, targetId, page);
            }));
    }

    private int getOpenAdminHistoryPage(Player admin, UUID targetId) {
        if (admin.getOpenInventory().getTopInventory().getHolder() instanceof QuestMenuHolder holder
                && holder.menu == QuestMenu.ADMIN_PLAYER_HISTORY
                && targetId.toString().equals(holder.npcId)) return holder.page;
        return 0;
    }

    public void reloadQuestCache(Player player) {
        UUID id = player.getUniqueId();
        PlayerProfileDatabaseManager database = plugin.getPlayerProfileDatabaseManager();
        database.executeGameplayDatabaseTask("quest.admin-refresh player=" + player.getName(),
                () -> new QuestJoinData(database.getActiveQuestProgress(id.toString()),
                    database.getCompletedQuestIds(id.toString())))
            .whenComplete((data, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (failure != null) plugin.getLogger().log(Level.SEVERE,
                    "[Quest][Admin] Could not refresh cache for " + player.getName(), failure);
                else if (player.isOnline()) {
                    activeByPlayer.put(id, data.active());
                    completedByPlayer.put(id, data.completed());
                    refreshBoard(player);
                }
            }));
    }

    public boolean isQuestDialogInUse(String dialogId) {
        if (dialogId == null) return false;
        return questDialogs.values().stream().anyMatch(refs -> dialogId.equals(refs.available())
            || dialogId.equals(refs.inProgress()) || dialogId.equals(refs.completed())
            || dialogId.equals(refs.unavailable()));
    }

    public void openPlayerJournal(Player player) {
        openPlayerJournal(player, 0);
    }

    private void openPlayerJournal(Player player, int requestedPage) {
        processPendingQuestSuccessors(player);
        Map<String, PlayerProfileDatabaseManager.QuestProgress> active =
            activeByPlayer.getOrDefault(player.getUniqueId(), Map.of());
        List<Map.Entry<String, PlayerProfileDatabaseManager.QuestProgress>> quests = active.entrySet().stream()
            .filter(entry -> definitions.containsKey(entry.getKey()))
            .sorted(java.util.Comparator.<Map.Entry<String, PlayerProfileDatabaseManager.QuestProgress>, String>
                comparing(entry -> {
                    QuestDefinition definition = definitions.get(entry.getKey());
                    return definition == null ? entry.getKey() : definition.title();
                }, String.CASE_INSENSITIVE_ORDER))
            .toList();
        int pageCount = Math.max(1, (quests.size() + 21) / 22);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        QuestMenuHolder holder = new QuestMenuHolder(player.getUniqueId(), QuestMenu.PLAYER_JOURNAL, null, page);
        Inventory menu = Bukkit.createInventory(holder, 54, "Nhiệm vụ đang nhận • " + (page + 1));
        holder.inventory = menu;
        if (quests.isEmpty()) {
            menu.setItem(22, adminItem(Material.BOOK, "§7Chưa có quest NPC đang làm",
                List.of("§7Nhận nhiệm vụ từ NPC để theo dõi tiến độ tại đây.")));
        } else {
            for (int displayIndex = 0; displayIndex < 22; displayIndex++) {
                int questIndex = page * 22 + displayIndex;
                if (questIndex >= quests.size()) break;
                Map.Entry<String, PlayerProfileDatabaseManager.QuestProgress> entry = quests.get(questIndex);
                QuestDefinition quest = definitions.get(entry.getKey());
                if (quest == null) continue;
                int slot = displayIndex * 2;
                int step = currentObjectiveIndex(entry.getValue(), quest);
                QuestObjective objective = quest.objectives().get(step);
                List<String> lore = new java.util.ArrayList<>();
                lore.add("§7ID: §f" + quest.id());
                lore.add("§7NPC giao: §f" + quest.giverNpcId());
                lore.add("§7Mục tiêu " + (step + 1) + "/" + quest.objectives().size() + ": §f"
                    + objectiveDescription(objective));
                lore.add("§7Tiến độ: §f" + entry.getValue().objectiveProgress() + "/" + objective.amount());
                lore.add("§7Thưởng: §e" + quest.vncoinReward() + " VNCOIN §7| §b"
                    + quest.experienceReward() + " EXP");
                for (ItemStack reward : quest.itemRewards()) {
                    lore.add("§7Vật phẩm: §f" + (reward.hasItemMeta() && reward.getItemMeta().hasDisplayName()
                        ? reward.getItemMeta().getDisplayName() : reward.getType().name()) + " × " + reward.getAmount());
                }
                lore.add(entry.getValue().tracked() ? "§aĐang theo dõi — nhấn để bỏ theo dõi"
                    : "§eNhấn để theo dõi trên scoreboard");
                menu.setItem(slot, adminItem(entry.getValue().tracked() ? Material.COMPASS : Material.WRITABLE_BOOK,
                    (entry.getValue().tracked() ? "§b" : "§f") + quest.title(), lore));
                holder.questSlots.put(slot, quest.id());
                menu.setItem(slot + 1, adminItem(entry.getValue().tracked() ? Material.LIME_DYE : Material.GRAY_DYE,
                    entry.getValue().tracked() ? "§aTheo dõi: BẬT" : "§7Theo dõi: TẮT",
                    List.of("§7Bật/tắt quest trên scoreboard.", "§eNhấn để đổi trạng thái.")));
                holder.trackingSlots.put(slot + 1, quest.id());
            }
        }
        if (page > 0) menu.setItem(45, adminItem(Material.ARROW, "§eTrang trước", List.of()));
        menu.setItem(46, adminItem(Material.WRITABLE_BOOK, "§dNhật ký nhiệm vụ", List.of(
            "§7Xem các nhiệm vụ đã hoàn thành.")));
        menu.setItem(47, adminItem(Material.PAPER, "§fTrang " + (page + 1) + "/" + pageCount,
            List.of("§7Tổng quest đang làm: " + quests.size())));
        if (page + 1 < pageCount) menu.setItem(48, adminItem(Material.ARROW, "§eTrang sau", List.of()));
        menu.setItem(49, adminItem(Material.ARROW, "§eQuay lại nhật ký tutorial", List.of()));
        boolean particlesEnabled = areQuestWaypointParticlesEnabled(player);
        menu.setItem(50, adminItem(particlesEnabled ? Material.LIME_DYE : Material.GRAY_DYE,
            particlesEnabled ? "§aParticle dẫn đường: BẬT" : "§7Particle dẫn đường: TẮT", List.of(
                "§7Tùy chọn riêng của bạn, áp dụng cho quest đang theo dõi.",
                "§eNhấn để " + (particlesEnabled ? "tắt" : "bật") + " mũi tên dẫn đường.")));
        menu.setItem(53, adminItem(Material.BARRIER, "§cĐóng", List.of()));
        player.openInventory(menu);
    }

    private void openQuestHistory(Player player, int requestedPage) {
        java.util.Set<String> completed = completedByPlayer.getOrDefault(player.getUniqueId(), java.util.Set.of());
        List<QuestDefinition> quests = completed.stream().map(definitions::get).filter(java.util.Objects::nonNull)
            .sorted(java.util.Comparator.comparing(QuestDefinition::title, String.CASE_INSENSITIVE_ORDER)).toList();
        int pageCount = Math.max(1, (quests.size() + 44) / 45);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        QuestMenuHolder holder = new QuestMenuHolder(player.getUniqueId(), QuestMenu.PLAYER_HISTORY, null, page);
        Inventory menu = Bukkit.createInventory(holder, 54, "Nhật ký nhiệm vụ • " + (page + 1));
        holder.inventory = menu;
        if (quests.isEmpty()) menu.setItem(22, adminItem(Material.BOOK, "§7Chưa hoàn thành nhiệm vụ nào", List.of()));
        for (int slot = 0; slot < 45 && page * 45 + slot < quests.size(); slot++) {
            QuestDefinition quest = quests.get(page * 45 + slot);
            menu.setItem(slot, adminItem(Material.ENCHANTED_BOOK, "§a" + quest.title(), List.of(
                "§7NPC giao: §f" + quest.giverNpcId(), "§aĐã hoàn thành", "§eNhấn để xem chi tiết và phần thưởng.")));
            holder.questSlots.put(slot, quest.id());
        }
        if (page > 0) menu.setItem(45, adminItem(Material.ARROW, "§eTrang trước", List.of()));
        menu.setItem(46, adminItem(Material.BOOK, "§bNhiệm vụ đang nhận", List.of()));
        menu.setItem(47, adminItem(Material.PAPER, "§fTrang " + (page + 1) + "/" + pageCount,
            List.of("§7Tổng quest hoàn thành: " + quests.size())));
        if (page + 1 < pageCount) menu.setItem(48, adminItem(Material.ARROW, "§eTrang sau", List.of()));
        menu.setItem(49, adminItem(Material.ARROW, "§eQuay lại nhật ký tutorial", List.of()));
        menu.setItem(53, adminItem(Material.BARRIER, "§cĐóng", List.of()));
        player.openInventory(menu);
    }

    private void openQuestDetails(Player player, String questId, boolean completed) {
        QuestDefinition quest = definitions.get(questId);
        if (quest == null) {
            openPlayerJournal(player);
            return;
        }
        PlayerProfileDatabaseManager.QuestProgress progress = activeByPlayer
            .getOrDefault(player.getUniqueId(), Map.of()).get(questId);
        QuestMenuHolder holder = new QuestMenuHolder(player.getUniqueId(), QuestMenu.PLAYER_DETAILS, questId, completed ? 1 : 0);
        Inventory menu = Bukkit.createInventory(holder, 54, "Quest • " + quest.title());
        holder.inventory = menu;
        menu.setItem(4, adminItem(completed ? Material.ENCHANTED_BOOK : Material.WRITABLE_BOOK,
            (completed ? "§a" : "§b") + quest.title(), List.of("§7NPC giao: §f" + quest.giverNpcId(),
                completed ? "§aTrạng thái: Hoàn thành" : "§eTrạng thái: Đang thực hiện")));
        List<QuestObjective> objectives = quest.objectives();
        int current = progress == null ? 0 : currentObjectiveIndex(progress, quest);
        List<String> objectiveLore = new java.util.ArrayList<>();
        for (int index = 0; index < objectives.size(); index++) {
            QuestObjective objective = objectives.get(index);
            String state = completed || index < current ? "§a✓ Hoàn thành"
                : index == current ? "§eĐang làm: " + (progress == null ? 0 : progress.objectiveProgress())
                    + "/" + objective.amount() : "§7Chưa mở khóa";
            objectiveLore.add("§f" + (index + 1) + ". " + objectiveDescription(objective));
            objectiveLore.add("  " + state);
        }
        menu.setItem(13, adminItem(Material.PAPER, "§bMục tiêu nhiệm vụ", objectiveLore));
        menu.setItem(40, adminItem(Material.SUNFLOWER, "§6Phần thưởng", rewardLore(quest)));
        if (!completed && progress != null) {
            menu.setItem(30, adminItem(progress.tracked() ? Material.COMPASS : Material.MAP,
                progress.tracked() ? "§bĐang theo dõi" : "§eTheo dõi quest", List.of(
                    "§7Theo dõi tới NPC giao nhiệm vụ.", "§eNhấn để đổi trạng thái.")));
            boolean particlesEnabled = areQuestWaypointParticlesEnabled(player);
            menu.setItem(31, adminItem(particlesEnabled ? Material.LIME_DYE : Material.GRAY_DYE,
                particlesEnabled ? "§aParticle dẫn đường: Bật" : "§7Particle dẫn đường: Tắt", List.of(
                    "§7Tùy chọn riêng cho bạn, lưu qua lần đăng nhập.",
                    "§7Quản trị viên vẫn có thể tắt particle toàn server.",
                    "§eNhấn để " + (particlesEnabled ? "tắt" : "bật") + " mũi tên dẫn đường.")));
            menu.setItem(32, adminItem(Material.REDSTONE, "§cHủy nhiệm vụ", List.of(
                "§7Tiến độ nhiệm vụ này sẽ bị bỏ.", "§eNhấn để xác nhận hủy.")));
        }
        menu.setItem(49, adminItem(Material.ARROW, "§eQuay lại", List.of()));
        player.openInventory(menu);
    }

    private List<String> rewardLore(QuestDefinition quest) {
        List<String> lore = new java.util.ArrayList<>();
        lore.add("§e" + quest.vncoinReward() + " VNCOIN");
        lore.add("§b" + quest.experienceReward() + " EXP");
        List<Long> sellValues = questRewardSellValues.getOrDefault(quest.id(),
            zeroSellValues(quest.itemRewards().size()));
        for (int index = 0; index < quest.itemRewards().size(); index++) {
            ItemStack reward = quest.itemRewards().get(index);
            lore.add("§f" + (reward.hasItemMeta() && reward.getItemMeta().hasDisplayName()
                ? reward.getItemMeta().getDisplayName() : reward.getType().name()) + " × " + reward.getAmount()
                + " §8• §6Giá bán: " + sellValues.get(index) + " VNCOIN/item");
        }
        return lore;
    }

    private void openAbandonConfirmation(Player player, String questId) {
        QuestDefinition quest = definitions.get(questId);
        if (quest == null) return;
        QuestMenuHolder holder = new QuestMenuHolder(player.getUniqueId(), QuestMenu.CONFIRM_ABANDON, questId, 0);
        Inventory menu = Bukkit.createInventory(holder, 54, "Xác nhận hủy nhiệm vụ");
        holder.inventory = menu;
        menu.setItem(13, adminItem(Material.BOOK, "§cHủy quest: §f" + quest.title(), List.of(
            "§7Tiến độ đã làm sẽ không được giữ.")));
        menu.setItem(11, adminItem(Material.LIME_DYE, "§aCó, hủy nhiệm vụ", List.of()));
        menu.setItem(15, adminItem(Material.RED_DYE, "§cKhông, quay lại", List.of()));
        player.openInventory(menu);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cLệnh quest cần được mở trong game.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("journal")) {
            openPlayerJournal(player);
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("detail")) {
            String questId = args[1].toLowerCase(Locale.ROOT);
            boolean active = activeByPlayer.getOrDefault(player.getUniqueId(), Map.of()).containsKey(questId);
            boolean completed = completedByPlayer.getOrDefault(player.getUniqueId(), java.util.Set.of())
                .contains(questId);
            if (active || completed) openQuestDetails(player, questId, completed && !active);
            else player.sendMessage("§cBạn chưa nhận hoặc hoàn thành nhiệm vụ này.");
            return true;
        }
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            player.sendMessage("§cBạn không có quyền quản lý quest.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("admin")) {
            openAdminList(player, 0);
            return true;
        }
        player.sendMessage("§eDùng: /vnquest <journal|detail <id>|admin>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("detail") && sender instanceof Player player) {
            java.util.Set<String> known = new java.util.HashSet<>(
                activeByPlayer.getOrDefault(player.getUniqueId(), Map.of()).keySet());
            known.addAll(completedByPlayer.getOrDefault(player.getUniqueId(), java.util.Set.of()));
            return known.stream().filter(id -> id.startsWith(args[1].toLowerCase(Locale.ROOT))).sorted().toList();
        }
        if (args.length != 1) return List.of();
        List<String> options = new java.util.ArrayList<>();
        if ("journal".startsWith(args[0].toLowerCase(Locale.ROOT))) options.add("journal");
        if ("detail".startsWith(args[0].toLowerCase(Locale.ROOT))) options.add("detail");
        if (sender.hasPermission(ADMIN_PERMISSION)
                && "admin".startsWith(args[0].toLowerCase(Locale.ROOT))) options.add("admin");
        return options;
    }

    private void openAdminList(Player player, int page) {
        List<QuestDefinition> quests = definitions.values().stream()
            .sorted(java.util.Comparator.comparing(QuestDefinition::id)).toList();
        int safePage = Math.max(0, Math.min(page, Math.max(0, (quests.size() - 1) / 45)));
        QuestMenuHolder holder = new QuestMenuHolder(player.getUniqueId(), QuestMenu.LIST, null, safePage);
        Inventory menu = Bukkit.createInventory(holder, 54, "Quản lý nhiệm vụ • " + (safePage + 1));
        holder.inventory = menu;
        for (int index = 0; index < 45; index++) {
            int questIndex = safePage * 45 + index;
            if (questIndex >= quests.size()) break;
            QuestDefinition quest = quests.get(questIndex);
            menu.setItem(index, adminItem(quest.enabled() ? Material.ENCHANTED_BOOK : Material.GRAY_DYE,
                (quest.enabled() ? "§a" : "§c") + quest.title(), List.of(
                    "§7ID: §f" + quest.id(), "§7NPC nhận: §f" + quest.giverNpcId(),
                    "§7Mục tiêu: §f" + quest.targetEntity() + " × " + quest.target(),
                    "§7Thưởng: §e" + quest.vncoinReward() + " VNCOIN",
                    quest.enabled() ? "§aĐang bật" : "§cĐang tắt", "§eNhấn để chỉnh sửa")));
            holder.questSlots.put(index, quest.id());
        }
        menu.setItem(45, adminItem(Material.LIME_DYE, "§aTạo quest", List.of("§7Tạo quest mới bằng ID trong chat.")));
        if (safePage > 0) menu.setItem(48, adminItem(Material.ARROW, "§eTrang trước", List.of()));
        menu.setItem(49, adminItem(Material.BOOK, "§f" + (safePage + 1) + "/"
            + Math.max(1, (quests.size() + 44) / 45), List.of("§7" + quests.size() + " quest")));
        if ((safePage + 1) * 45 < quests.size()) menu.setItem(50,
            adminItem(Material.ARROW, "§eTrang sau", List.of()));
        menu.setItem(53, adminItem(Material.BARRIER, "§cĐóng", List.of()));
        menu.setItem(51, adminItem(Material.WRITABLE_BOOK, "§dQuản lý quest-dialog", List.of(
            "§7Tạo, sửa và xóa các hội thoại dùng lại.", "§eNhấn để mở trình quản lý.")));
        player.openInventory(menu);
    }

    private void openQuestEditor(Player player, String questId) {
        QuestDefinition quest = definitions.get(questId);
        if (quest == null) {
            openAdminList(player, 0);
            return;
        }
        QuestMenuHolder holder = new QuestMenuHolder(player.getUniqueId(), QuestMenu.EDIT, questId, 0);
        Inventory menu = Bukkit.createInventory(holder, 54, "Sửa quest • " + quest.id());
        holder.inventory = menu;
        menu.setItem(4, adminItem(quest.enabled() ? Material.ENCHANTED_BOOK : Material.GRAY_DYE,
            (quest.enabled() ? "§a" : "§c") + quest.title(), List.of("§7ID: §f" + quest.id())));
        menu.setItem(10, adminItem(Material.NAME_TAG, "§eĐổi tên hiển thị", List.of("§7Hiện tại: §f" + quest.title(),
            "§eNhấn rồi nhập tên vào chat.")));
        menu.setItem(12, adminItem(Material.VILLAGER_SPAWN_EGG, "§eĐổi NPC giao quest", List.of(
            "§7ID hiện tại: §f" + quest.giverNpcId(), "§eNhấn rồi nhập NPC ID vào chat.")));
        menu.setItem(14, adminItem(quest.enabled() ? Material.LIME_DYE : Material.GRAY_DYE,
            quest.enabled() ? "§aTắt quest" : "§eBật lại quest", List.of("§7Quest đang "
                + (quest.enabled() ? "bật" : "tắt") + " trong hệ thống.")));
        menu.setItem(16, adminItem(Material.REPEATER, "§dĐiều kiện nhận quest", List.of(
            "§7Số điều kiện: §f" + quest.requirements().size(), "§7Tất cả điều kiện cùng phải đạt.",
            "§eNhấn để quản lý điều kiện.")));
        menu.setItem(19, adminItem(Material.IRON_SWORD, "§bMục tiêu quest", List.of(
            "§7Số bước: §f" + quest.objectives().size(), "§7Bước đầu: " + objectiveDescription(quest.objectives().getFirst()),
            quest.objectiveType() == ObjectiveType.COLLECT_RPG_ITEM
                ? "§7Giá item nhiệm vụ: §6" + questItemSellValues.getOrDefault(quest.id(), 0L) + " VNCOIN/item"
                : "§7Item RPG không có giá sẽ mặc định không bán được.",
            "§8Có thể chỉnh sell-value trong quests/quest-definitions.yml; restart để áp dụng.",
            "§eNhấn để quản lý chuỗi mục tiêu.")));
        menu.setItem(21, adminItem(Material.CHEST, "§6Item thưởng", List.of(
            "§7Số stack: §f" + quest.itemRewards().size(),
            "§7Giá từng stack: " + questRewardSellValues.getOrDefault(quest.id(),
                zeroSellValues(quest.itemRewards().size())),
            "§8Chỉnh reward.item-sell-values trong quests/quest-definitions.yml.",
            "§eNhấn để mở rương cấu hình.")));
        menu.setItem(23, adminItem(Material.REDSTONE, "§cXóa quest", List.of("§7Xóa định nghĩa và gỡ khỏi danh sách.")));
        QuestDefinition successor = quest.successorQuestId() == null ? null : definitions.get(quest.successorQuestId());
        menu.setItem(25, adminItem(Material.IRON_CHAIN,
            "§bQuest tự giao tiếp theo",
            List.of("§7Hiện tại: §f" + (successor == null ? "Không có"
                : successor.title() + " (" + successor.id() + ")"),
                "§7Giao sau khi quest này hoàn thành và nhận thưởng.",
                "§eNhấn để cấu hình.")));
        QuestDialogRefs refs = dialogRefs(quest.id());
        menu.setItem(28, dialogChoiceItem("Hội thoại: có thể nhận", refs.available()));
        menu.setItem(29, dialogChoiceItem("Hội thoại: đang làm", refs.inProgress()));
        menu.setItem(30, dialogChoiceItem("Hội thoại: hoàn tất", refs.completed()));
        menu.setItem(31, dialogChoiceItem("Hội thoại: chưa đủ điều kiện", refs.unavailable()));
        boolean repeatable = repeatableQuests.getOrDefault(quest.id(), false);
        int dailyLimit = repeatDailyLimits.getOrDefault(quest.id(), 0);
        menu.setItem(33, adminItem(repeatable ? Material.LIME_DYE : Material.GRAY_DYE,
            repeatable ? "§aLặp lại: BẬT" : "§7Lặp lại: TẮT", List.of(
                "§7Bật để có thể nhận lại quest sau khi hoàn thành.",
                "§7" + (dailyLimit == 0 ? "Không giới hạn lượt/ngày." : "Tối đa " + dailyLimit + " lượt/ngày."),
                "§eNhấn để bật/tắt.")));
        menu.setItem(34, adminItem(Material.REPEATER, "§bSố lượt/ngày: " + dailyLimit,
            List.of("§7Giới hạn: 0–10; 0 nghĩa là không giới hạn.",
                repeatable ? "§eDùng nút -1 / +1 hai bên để chỉnh." : "§8Bật lặp lại trước khi chỉnh.")));
        menu.setItem(32, adminItem(repeatable ? Material.RED_DYE : Material.GRAY_DYE,
            repeatable ? "§c-1 lượt/ngày" : "§8-1 lượt/ngày (hãy bật lặp lại)", List.of()));
        menu.setItem(35, adminItem(repeatable ? Material.LIME_DYE : Material.GRAY_DYE,
            repeatable ? "§a+1 lượt/ngày" : "§8+1 lượt/ngày (hãy bật lặp lại)", List.of()));
        menu.setItem(40, adminItem(Material.EXPERIENCE_BOTTLE,
            "§bEXP thưởng: " + quest.experienceReward(), List.of("§7Giới hạn: 0–100000 EXP.")));
        int[] expSlots = {37, 38, 39, 41, 42, 43};
        int[] expDeltas = {-100, -10, -1, 1, 10, 100};
        for (int index = 0; index < expSlots.length; index++) {
            int delta = expDeltas[index];
            menu.setItem(expSlots[index], adminItem(delta > 0 ? Material.LIME_DYE : Material.RED_DYE,
                "§fEXP " + (delta > 0 ? "§a+" : "§c") + delta, List.of()));
        }
        menu.setItem(45, adminItem(Material.ARROW, "§eQuay lại danh sách", List.of()));
        int[] coinSlots = {46, 47, 48, 50, 51, 52};
        int[] coinDeltas = {-100, -10, -1, 1, 10, 100};
        for (int index = 0; index < coinSlots.length; index++) {
            int delta = coinDeltas[index];
            menu.setItem(coinSlots[index], adminItem(delta > 0 ? Material.LIME_DYE : Material.RED_DYE,
                "§fVNCOIN " + (delta > 0 ? "§a+" : "§c") + delta, List.of()));
        }
        menu.setItem(49, adminItem(Material.GOLD_INGOT, "§6VNCOIN thưởng: " + quest.vncoinReward(),
            List.of("§7Giới hạn: 0–1000000 VNCOIN.")));
        player.openInventory(menu);
    }

    @EventHandler
    public void onQuestAdminClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof QuestMenuHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.viewer.equals(player.getUniqueId())
                ) {
            event.setCancelled(true);
            return;
        }
        int slot = event.getRawSlot();
        if (holder.menu == QuestMenu.ADMIN_PLAYER_HISTORY) {
            event.setCancelled(true);
            if (!player.hasPermission(ADMIN_PERMISSION)
                    && !player.hasPermission("vnmine.command.playeradmin")) {
                player.closeInventory();
                return;
            }
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;
            UUID targetId;
            try { targetId = UUID.fromString(holder.npcId); }
            catch (IllegalArgumentException exception) { return; }
            if (slot == 49) {
                if (plugin.getPlayerAdminManager() != null)
                    plugin.getPlayerAdminManager().openTargetQuestMenuForAdmin(player, targetId);
            } else if (slot == 45 && holder.page > 0) {
                openAdminCompletedQuests(player, targetId, holder.page - 1);
            } else if (slot == 53) {
                openAdminCompletedQuests(player, targetId, holder.page + 1);
            } else if (holder.questSlots.containsKey(slot)) {
                confirmAdminQuestReset(player, targetId, holder.questSlots.get(slot));
            }
            return;
        }
        if (holder.menu == QuestMenu.ADMIN_RESET_CONFIRM) {
            event.setCancelled(true);
            if (!player.hasPermission(ADMIN_PERMISSION)
                    && !player.hasPermission("vnmine.command.playeradmin")) {
                player.closeInventory();
                return;
            }
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;
            UUID targetId;
            try { targetId = UUID.fromString(holder.npcId); }
            catch (IllegalArgumentException exception) { return; }
            if (slot == 11) resetAdminQuest(player, targetId, holder.questId, holder.page);
            else if (slot == 15) openAdminCompletedQuests(player, targetId, holder.page);
            return;
        }
        if (holder.menu == QuestMenu.NPC_ACCEPT || holder.menu == QuestMenu.NPC_SUBMIT) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;
            if (slot == 15 || slot == 49 || slot == 53) {
                player.closeInventory();
                return;
            }
            if (slot != 11) return;
            QuestDefinition quest = definitions.get(holder.questId);
            if (quest == null || !quest.enabled() || !quest.giverNpcId().equals(holder.npcId)) {
                player.closeInventory();
                player.sendMessage("§cNhiệm vụ không còn khả dụng tại NPC này.");
                return;
            }
            player.closeInventory();
            if (holder.menu == QuestMenu.NPC_ACCEPT) {
                if (!activeByPlayer.getOrDefault(player.getUniqueId(), Map.of()).containsKey(quest.id())
                        && (isRepeatable(quest.id()) || !completedByPlayer.getOrDefault(player.getUniqueId(), java.util.Set.of()).contains(quest.id()))
                        && canDisplayQuest(player, quest)
                        && activeByPlayer.getOrDefault(player.getUniqueId(), Map.of()).size() < 3) {
                    acceptQuest(player, holder.npcId, quest.id());
                } else {
                    player.sendMessage("§cĐiều kiện nhận quest đã thay đổi; hãy kiểm tra lại tại NPC.");
                }
            } else {
                submitQuestItems(player, holder.npcId, quest.id());
            }
            return;
        }
        if (holder.menu == QuestMenu.PLAYER_JOURNAL || holder.menu == QuestMenu.PLAYER_HISTORY) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;
            if (slot == 45 && holder.page > 0) {
                if (holder.menu == QuestMenu.PLAYER_JOURNAL) openPlayerJournal(player, holder.page - 1);
                else openQuestHistory(player, holder.page - 1);
            } else if (slot == 48) {
                if (holder.menu == QuestMenu.PLAYER_JOURNAL) openPlayerJournal(player, holder.page + 1);
                else openQuestHistory(player, holder.page + 1);
            } else if (slot == 46) {
                if (holder.menu == QuestMenu.PLAYER_JOURNAL) openQuestHistory(player, 0);
                else openPlayerJournal(player, 0);
            } else if (slot == 49) {
                plugin.getTutorialQuestManager().openJournal(player);
            } else if (slot == 50) {
                toggleWaypointParticles(player, () -> openPlayerJournal(player, holder.page));
            } else if (slot == 53) {
                player.closeInventory();
            } else if (holder.questSlots.containsKey(slot)) {
                String questId = holder.questSlots.get(slot);
                openQuestDetails(player, questId, holder.menu == QuestMenu.PLAYER_HISTORY);
            } else if (holder.trackingSlots.containsKey(slot)) {
                toggleQuestTracking(player, holder.trackingSlots.get(slot), () -> openPlayerJournal(player, holder.page));
            }
            return;
        }
        if (holder.menu == QuestMenu.PLAYER_DETAILS) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;
            boolean completed = holder.page == 1;
            String questId = holder.questId;
            if (slot == 49) {
                if (completed) openQuestHistory(player, 0);
                else openPlayerJournal(player, 0);
            } else if (!completed && slot == 32) {
                openAbandonConfirmation(player, questId);
            } else if (!completed && slot == 30) {
                toggleQuestTracking(player, questId, () -> openQuestDetails(player, questId, false));
            } else if (!completed && slot == 31) {
                toggleWaypointParticles(player, () -> openQuestDetails(player, questId, false));
            }
            return;
        }
        if (holder.menu == QuestMenu.CONFIRM_ABANDON) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;
            if (slot == 15) {
                openQuestDetails(player, holder.questId, false);
            } else if (slot == 11) {
                UUID playerId = player.getUniqueId();
                String questId = holder.questId;
                PlayerProfileDatabaseManager database = plugin.getPlayerProfileDatabaseManager();
                database.executeGameplayDatabaseTask("quest.abandon player=" + player.getName()
                        + " quest=" + questId,
                        () -> database.abandonQuestProgress(playerId.toString(), questId, Instant.now().toString()))
                    .whenComplete((abandoned, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (failure != null) {
                            plugin.getLogger().log(Level.SEVERE, "[Quest] Could not abandon quest " + questId
                                + " for " + player.getName(), failure);
                            if (player.isOnline()) player.sendMessage("§cKhông thể hủy nhiệm vụ lúc này.");
                        } else if (player.isOnline() && abandoned) {
                            Map<String, PlayerProfileDatabaseManager.QuestProgress> updated = new LinkedHashMap<>(
                                activeByPlayer.getOrDefault(playerId, Map.of()));
                            updated.remove(questId);
                            activeByPlayer.put(playerId, Map.copyOf(updated));
                            player.sendMessage("§eĐã hủy nhiệm vụ §f" + questId + "§e.");
                            refreshBoard(player);
                            processPendingQuestSuccessors(player);
                        }
                        if (player.isOnline()) openPlayerJournal(player);
                    }));
            }
            return;
        }
        if (holder.menu == QuestMenu.REWARD_ITEMS) {
            if (event.getClickedInventory() == event.getView().getTopInventory() && slot == 49) {
                event.setCancelled(true);
                saveRewardItems(player, holder);
            } else if (event.getClickedInventory() == event.getView().getTopInventory() && slot == 53) {
                event.setCancelled(true);
                holder.navigating = true;
                openQuestEditor(player, holder.questId);
            } else if (event.isShiftClick() && event.getClickedInventory() != event.getView().getTopInventory()) {
                event.setCancelled(true);
            } else if (event.getClickedInventory() == event.getView().getTopInventory() && slot >= 45) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (holder.menu == QuestMenu.LIST) {
            if (slot == 45) {
                prompt(player, new PendingTextInput(TextField.CREATE_ID, null, 0));
            } else if (slot == 48) {
                openAdminList(player, holder.page - 1);
            } else if (slot == 50) {
                openAdminList(player, holder.page + 1);
            } else if (slot == 51) {
                plugin.getQuestDialogManager().openAdmin(player);
            } else if (slot == 53) {
                player.closeInventory();
            } else if (holder.questSlots.containsKey(slot)) {
                openQuestEditor(player, holder.questSlots.get(slot));
            }
            return;
        }
        if (holder.menu == QuestMenu.CONFIRM_DELETE) {
            if (slot == 11) {
                QuestDefinition deleting = definitions.get(holder.questId);
                if (deleting != null && hasActiveQuest(deleting.id())) {
                    player.sendMessage("§cKhông thể xóa quest khi người chơi đang làm quest này.");
                    openQuestEditor(player, deleting.id());
                    return;
                }
                if (deleting != null && hasSuccessorReferences(deleting.id())) {
                    player.sendMessage("§cKhông thể xóa quest này vì quest khác đang liên kết tới nó.");
                    openQuestEditor(player, deleting.id());
                    return;
                }
                if (deleting != null) deleteDefinition(deleting);
                openAdminList(player, holder.page);
            } else if (slot == 15) {
                openQuestEditor(player, holder.questId);
            }
            return;
        }
        if (holder.menu == QuestMenu.REQUIREMENTS || holder.menu == QuestMenu.PREREQUISITE_SELECT) {
            handleRequirementMenuClick(player, holder, slot);
            return;
        }
        if (holder.menu == QuestMenu.SUCCESSOR_SELECT) {
            handleSuccessorSelectClick(player, holder, slot);
            return;
        }
        if (holder.menu == QuestMenu.OBJECTIVES) {
            handleObjectiveMenuClick(player, holder, slot);
            return;
        }
        QuestDefinition quest = definitions.get(holder.questId);
        if (quest == null) {
            openAdminList(player, 0);
            return;
        }
        if (slot == 45) {
            openAdminList(player, holder.page);
        } else if (slot == 10) {
            prompt(player, new PendingTextInput(TextField.RENAME, quest.id(), holder.page));
        } else if (slot == 12) {
            prompt(player, new PendingTextInput(TextField.GIVER, quest.id(), holder.page));
        } else if (slot == 14) {
            replaceDefinition(quest, quest.withEnabled(!quest.enabled()));
            openQuestEditor(player, quest.id());
        } else if (slot == 16) {
            openRequirementsMenu(player, quest.id());
        } else if (slot >= 28 && slot <= 31) {
            QuestDialogStage stage = QuestDialogStage.values()[slot - 28];
            String selected = dialogRefs(quest.id()).forStage(stage);
            plugin.getQuestDialogManager().openSelector(player, selected, dialogId ->
                updateQuestDialog(player, quest.id(), stage, dialogId));
        } else if (slot == 19) {
            openObjectivesMenu(player, quest.id());
        } else if (slot == 21) {
            openRewardItems(player, quest);
        } else if (slot == 23) {
            openDeleteConfirmation(player, quest.id(), holder.page);
        } else if (slot == 25) {
            openSuccessorSelect(player, quest.id());
        } else if (slot == 33) {
            repeatableQuests.put(quest.id(), !repeatableQuests.getOrDefault(quest.id(), false));
            if (!saveDefinitions()) repeatableQuests.put(quest.id(), !repeatableQuests.get(quest.id()));
            debugQuest("repeatable-config actor=" + player.getName() + ", quest=" + quest.id()
                + ", enabled=" + repeatableQuests.getOrDefault(quest.id(), false)
                + ", dailyLimit=" + repeatDailyLimits.getOrDefault(quest.id(), 0));
            openQuestEditor(player, quest.id());
        } else if (slot == 32 || slot == 35) {
            if (!repeatableQuests.getOrDefault(quest.id(), false)) {
                player.sendMessage("§eBật “Lặp lại” trước khi chỉnh số lượt mỗi ngày.");
                openQuestEditor(player, quest.id());
                return;
            }
            int current = repeatDailyLimits.getOrDefault(quest.id(), 0);
            repeatDailyLimits.put(quest.id(), Math.max(0, Math.min(10, current + (slot == 35 ? 1 : -1))));
            if (!saveDefinitions()) repeatDailyLimits.put(quest.id(), current);
            debugQuest("repeat-limit-config actor=" + player.getName() + ", quest=" + quest.id()
                + ", dailyLimit=" + repeatDailyLimits.getOrDefault(quest.id(), 0));
            openQuestEditor(player, quest.id());
        } else if (slot >= 37 && slot <= 43 && slot != 40) {
            int delta = switch (slot) {
                case 37 -> -100;
                case 38 -> -10;
                case 39 -> -1;
                case 41 -> 1;
                case 42 -> 10;
                default -> 100;
            };
            replaceDefinition(quest, quest.withExperienceReward(
                Math.max(0, Math.min(100_000, quest.experienceReward() + delta))));
            openQuestEditor(player, quest.id());
        } else if (slot == 46 || slot == 47 || slot == 48 || slot == 50 || slot == 51 || slot == 52) {
            int delta = switch (slot) {
                case 46 -> -100;
                case 47 -> -10;
                case 48 -> -1;
                case 50 -> 1;
                case 51 -> 10;
                default -> 100;
            };
            replaceDefinition(quest, quest.withWalletReward(
                Math.max(0, Math.min(1_000_000, quest.vncoinReward() + delta))));
            openQuestEditor(player, quest.id());
        }
    }

    private void openObjectivesMenu(Player player, String questId) {
        QuestDefinition quest = definitions.get(questId);
        if (quest == null) return;
        QuestMenuHolder holder = new QuestMenuHolder(player.getUniqueId(), QuestMenu.OBJECTIVES, questId, 0);
        Inventory menu = Bukkit.createInventory(holder, 54, "Mục tiêu • " + quest.title());
        holder.inventory = menu;
        List<QuestObjective> objectives = quest.objectives();
        for (int index = 0; index < Math.min(45, objectives.size()); index++) {
            QuestObjective objective = objectives.get(index);
            menu.setItem(index, adminItem(Material.PAPER, "§eBước " + (index + 1) + ": " + objectiveDescription(objective),
                List.of(index == 0 ? "§7Mục tiêu gốc không thể xóa." : "§cNhấn để xóa bước này.")));
            holder.questSlots.put(index, Integer.toString(index));
        }
        menu.setItem(45, adminItem(Material.ZOMBIE_SPAWN_EGG, "§aThêm mục tiêu hạ mob", List.of(
            "§7Nhập EntityType (ví dụ ZOMBIE) vào chat.", "§7Sau đó nhập số lượng cần hạ.")));
        menu.setItem(46, adminItem(Material.ANVIL, "§eSửa mục tiêu đầu", List.of(
            "§7Hiện tại: " + objectiveDescription(objectives.getFirst()),
            "§7Áp dụng cho mục tiêu hạ mob.")));
        menu.setItem(47, adminItem(Material.HOPPER, "§aThêm mục tiêu thu thập item", List.of(
            "§7Cầm item cần thu thập ở tay chính.", "§7Giữ nguyên Material, tên và PDC item RPG.")));
        menu.setItem(48, adminItem(Material.CHEST, "§6Thêm mục tiêu nộp item", List.of(
            "§7Cầm item cần nộp ở tay chính.", "§7Người chơi nộp cho NPC giao quest.")));
        menu.setItem(49, adminItem(Material.ARROW, "§eQuay lại quest", List.of()));
        menu.setItem(50, adminItem(Material.WITHER_SKELETON_SKULL, "§cThêm mục tiêu hạ boss", List.of(
            "§7Yêu cầu boss hệ thống với ID hợp lệ.", "§7Ví dụ: winter-tyrant")));
        menu.setItem(51, adminItem(Material.VILLAGER_SPAWN_EGG, "§bThêm mục tiêu gặp NPC", List.of(
            "§7Nhập ID NPC cần gặp, ví dụ: hunter.", "§7Mỗi lần tương tác hợp lệ tăng tiến độ 1.")));
        menu.setItem(52, adminItem(Material.NAME_TAG, "§dĐặt bước đầu: gặp NPC", List.of(
            "§7Thay bước đầu hiện tại bằng mục tiêu gặp NPC.", "§7Nhập NPC ID, ví dụ: hunter.")));
        player.openInventory(menu);
    }

    private void handleObjectiveMenuClick(Player player, QuestMenuHolder holder, int slot) {
        QuestDefinition quest = definitions.get(holder.questId);
        if (quest == null) return;
        if ((slot == 45 || slot == 46 || slot == 47 || slot == 48 || slot == 50 || slot == 51 || slot == 52
                || holder.questSlots.containsKey(slot))
                && hasActiveQuest(quest.id())) {
            player.sendMessage("§cKhông thể sửa mục tiêu khi người chơi đang làm quest này.");
            return;
        }
        if (slot == 49) {
            openQuestEditor(player, quest.id());
        } else if (slot == 45) {
            prompt(player, new PendingTextInput(TextField.ADD_KILL_ENTITY, quest.id(), 0));
        } else if (slot == 46 && quest.objectiveType() == ObjectiveType.KILL_MOB) {
            prompt(player, new PendingTextInput(TextField.EDIT_KILL_ENTITY, quest.id(), 0));
        } else if (slot == 47) {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held.getType().isAir()) {
                player.sendMessage("§cCầm item cần thu thập ở tay chính trước.");
                return;
            }
            ItemStack required = held.clone();
            required.setAmount(1);
            pendingObjectiveItems.put(player.getUniqueId(), required);
            prompt(player, new PendingTextInput(TextField.ADD_OBJECTIVE_ITEM_AMOUNT, quest.id(), 0));
        } else if (slot == 48) {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held.getType().isAir()) {
                player.sendMessage("§cCầm item cần nộp ở tay chính trước.");
                return;
            }
            ItemStack required = held.clone();
            required.setAmount(1);
            pendingObjectiveItems.put(player.getUniqueId(), required);
            prompt(player, new PendingTextInput(TextField.ADD_SUBMIT_ITEM_AMOUNT, quest.id(), 0));
        } else if (slot == 50) {
            prompt(player, new PendingTextInput(TextField.ADD_BOSS_ID, quest.id(), 0));
        } else if (slot == 51) {
            prompt(player, new PendingTextInput(TextField.ADD_OBJECTIVE_NPC_ID, quest.id(), 0));
        } else if (slot == 52) {
            prompt(player, new PendingTextInput(TextField.SET_PRIMARY_NPC_ID, quest.id(), 0));
        } else if (holder.questSlots.containsKey(slot)) {
            int index = Integer.parseInt(holder.questSlots.get(slot));
            if (index == 0) {
                player.sendMessage("§cBước mục tiêu đầu tiên không thể xóa.");
                return;
            }
            List<QuestObjective> objectives = new java.util.ArrayList<>(quest.objectives());
            objectives.remove(index);
            replaceDefinition(quest, quest.withObjectives(objectives));
            openObjectivesMenu(player, quest.id());
        }
    }

    private String objectiveDescription(QuestObjective objective) {
        return switch (objective.type()) {
            case KILL_MOB -> "Hạ " + objective.entity() + " × " + objective.amount();
            case KILL_BOSS -> "Hạ boss " + objective.itemId() + " × " + objective.amount();
            case COLLECT_RPG_ITEM -> "Thu thập " + objective.itemName() + " × " + objective.amount();
            case COLLECT_EXACT_ITEM -> "Thu thập " + objective.requiredItem().getType()
                + (objective.itemName().isBlank() ? "" : " (" + objective.itemName() + ")")
                + " × " + objective.amount();
            case SUBMIT_EXACT_ITEM -> "Nộp " + objective.requiredItem().getType()
                + (objective.itemName().isBlank() ? "" : " (" + objective.itemName() + ")")
                + " × " + objective.amount();
            case VISIT_NPC -> "Gặp NPC " + objective.itemId() + " × " + objective.amount();
        };
    }

    private void openDeleteConfirmation(Player player, String questId, int page) {
        QuestMenuHolder holder = new QuestMenuHolder(player.getUniqueId(), QuestMenu.CONFIRM_DELETE, questId, page);
        Inventory menu = Bukkit.createInventory(holder, 54, "Xác nhận xóa quest");
        holder.inventory = menu;
        QuestDefinition quest = definitions.get(questId);
        menu.setItem(4, adminItem(Material.BOOK, "§cXóa " + (quest == null ? questId : quest.title()),
            List.of("§7Xóa quest khỏi danh sách cấu hình.")));
        menu.setItem(11, adminItem(Material.LIME_DYE, "§aXác nhận xóa", List.of()));
        menu.setItem(15, adminItem(Material.RED_DYE, "§eHủy", List.of()));
        player.openInventory(menu);
    }

    private void openRequirementsMenu(Player player, String questId) {
        QuestDefinition quest = definitions.get(questId);
        if (quest == null) return;
        QuestMenuHolder holder = new QuestMenuHolder(player.getUniqueId(), QuestMenu.REQUIREMENTS, questId, 0);
        Inventory menu = Bukkit.createInventory(holder, 54, "Điều kiện • " + quest.title());
        holder.inventory = menu;
        for (int index = 0; index < Math.min(45, quest.requirements().size()); index++) {
            QuestRequirement requirement = quest.requirements().get(index);
            menu.setItem(index, adminItem(Material.PAPER, "§e" + requirement.description(),
                List.of("§7Nhấn để xóa điều kiện.")));
        }
        menu.setItem(45, adminItem(Material.EXPERIENCE_BOTTLE, "§aThêm yêu cầu level RPG", List.of(
            "§7Nhập level tối thiểu vào chat.")));
        menu.setItem(46, adminItem(Material.WRITABLE_BOOK, "§aThêm quest prerequisite", List.of(
            "§7Chọn quest phải hoàn thành trước.")));
        menu.setItem(47, adminItem(Material.HOPPER, "§aYêu cầu item đang cầm", List.of(
            "§7Cầm đúng item (kể cả item RPG) ở tay chính.", "§7Người chơi cần có ít nhất 1 item đó.")));
        menu.setItem(49, adminItem(Material.ARROW, "§eQuay lại quest", List.of()));
        player.openInventory(menu);
    }

    private void openRewardItems(Player player, QuestDefinition quest) {
        QuestMenuHolder holder = new QuestMenuHolder(player.getUniqueId(), QuestMenu.REWARD_ITEMS, quest.id(), 0);
        Inventory menu = Bukkit.createInventory(holder, 54, "Item thưởng • " + quest.title());
        holder.inventory = menu;
        for (int slot = 0; slot < Math.min(45, quest.itemRewards().size()); slot++) {
            menu.setItem(slot, quest.itemRewards().get(slot).clone());
        }
        menu.setItem(49, adminItem(Material.LIME_DYE, "§aLưu item thưởng", List.of("§7Tối đa 45 stack.")));
        menu.setItem(53, adminItem(Material.BARRIER, "§cHủy", List.of("§7Bỏ thay đổi trong phiên này.")));
        player.openInventory(menu);
    }

    private void saveRewardItems(Player player, QuestMenuHolder holder) {
        QuestDefinition quest = definitions.get(holder.questId);
        if (quest == null) {
            holder.navigating = true;
            openAdminList(player, 0);
            return;
        }
        List<ItemStack> items = new java.util.ArrayList<>();
        for (int slot = 0; slot < 45; slot++) {
            ItemStack item = holder.inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) items.add(item.clone());
        }
        if (items.size() > 45) return;
        replaceDefinition(quest, quest.withItemRewards(items));
        holder.navigating = true;
        player.sendMessage("§aĐã lưu " + items.size() + " stack item thưởng cho quest.");
        openQuestEditor(player, quest.id());
    }

    private void openPrerequisiteQuestList(Player player, String questId) {
        QuestMenuHolder holder = new QuestMenuHolder(player.getUniqueId(), QuestMenu.PREREQUISITE_SELECT, questId, 0);
        Inventory menu = Bukkit.createInventory(holder, 54, "Chọn quest prerequisite");
        holder.inventory = menu;
        List<QuestDefinition> choices = definitions.values().stream()
            .filter(candidate -> !candidate.id().equals(questId))
            .sorted(java.util.Comparator.comparing(QuestDefinition::id)).toList();
        for (int index = 0; index < Math.min(45, choices.size()); index++) {
            QuestDefinition choice = choices.get(index);
            menu.setItem(index, adminItem(Material.BOOK, "§e" + choice.title(),
                List.of("§7ID: §f" + choice.id(), "§eNhấn để yêu cầu hoàn thành.")));
            holder.questSlots.put(index, choice.id());
        }
        menu.setItem(49, adminItem(Material.ARROW, "§eQuay lại", List.of()));
        player.openInventory(menu);
    }

    private void openSuccessorSelect(Player player, String questId) {
        openSuccessorSelect(player, questId, 0);
    }

    private void openSuccessorSelect(Player player, String questId, int requestedPage) {
        QuestDefinition source = definitions.get(questId);
        if (source == null) return;
        List<QuestDefinition> choices = definitions.values().stream()
            .filter(candidate -> !candidate.id().equals(questId))
            .sorted(java.util.Comparator.comparing(QuestDefinition::id)).toList();
        int pageCount = Math.max(1, (choices.size() + 44) / 45);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        QuestMenuHolder holder = new QuestMenuHolder(player.getUniqueId(), QuestMenu.SUCCESSOR_SELECT, questId, page);
        Inventory menu = Bukkit.createInventory(holder, 54, "Quest tiếp theo • " + source.title());
        holder.inventory = menu;
        for (int index = 0; index < 45; index++) {
            int choiceIndex = page * 45 + index;
            if (choiceIndex >= choices.size()) break;
            QuestDefinition choice = choices.get(choiceIndex);
            boolean createsCycle = wouldCreateSuccessorCycle(questId, choice.id());
            menu.setItem(index, adminItem(createsCycle ? Material.BARRIER : Material.BOOK,
                (createsCycle ? "§c" : "§e") + choice.title(), List.of(
                    "§7ID: §f" + choice.id(),
                    createsCycle ? "§cLựa chọn này tạo vòng lặp." : "§eNhấn để chọn làm quest tiếp theo.")));
            if (!createsCycle) holder.questSlots.put(index, choice.id());
        }
        menu.setItem(45, adminItem(Material.BARRIER, "§cXóa liên kết", List.of(
            "§7Quest này sẽ không tự giao quest tiếp theo.")));
        menu.setItem(49, adminItem(Material.ARROW, "§eQuay lại quest", List.of()));
        if (page > 0) menu.setItem(46, adminItem(Material.ARROW, "§eTrang trước", List.of()));
        menu.setItem(48, adminItem(Material.PAPER, "§fTrang " + (page + 1) + "/" + pageCount,
            List.of("§7Tổng quest: " + choices.size())));
        if (page + 1 < pageCount) menu.setItem(50, adminItem(Material.ARROW, "§eTrang sau", List.of()));
        player.openInventory(menu);
    }

    private boolean wouldCreateSuccessorCycle(String sourceQuestId, String candidateId) {
        String current = candidateId;
        Set<String> visited = new java.util.HashSet<>();
        while (current != null && visited.add(current)) {
            if (current.equals(sourceQuestId)) return true;
            QuestDefinition definition = definitions.get(current);
            current = definition == null ? null : definition.successorQuestId();
        }
        return current != null;
    }

    private void handleSuccessorSelectClick(Player player, QuestMenuHolder holder, int slot) {
        QuestDefinition source = definitions.get(holder.questId);
        if (source == null) return;
        if (slot == 49) {
            openQuestEditor(player, source.id());
            return;
        }
        if (slot == 46 || slot == 50) {
            openSuccessorSelect(player, source.id(), holder.page + (slot == 50 ? 1 : -1));
            return;
        }
        if (slot == 45) {
            replaceDefinition(source, source.withSuccessorQuestId(null));
            debugQuest("successor-configured admin=" + player.getName() + ", quest=" + source.id()
                + ", successor=cleared");
            openQuestEditor(player, source.id());
            return;
        }
        String successorId = holder.questSlots.get(slot);
        if (successorId == null || wouldCreateSuccessorCycle(source.id(), successorId)) return;
        replaceDefinition(source, source.withSuccessorQuestId(successorId));
        debugQuest("successor-configured admin=" + player.getName() + ", quest=" + source.id()
            + ", successor=" + successorId);
        openQuestEditor(player, source.id());
    }

    private void handleRequirementMenuClick(Player player, QuestMenuHolder holder, int slot) {
        if (holder.menu == QuestMenu.PREREQUISITE_SELECT) {
            if (slot == 49) openRequirementsMenu(player, holder.questId);
            else if (holder.questSlots.containsKey(slot)) {
                addRequirement(player, holder.questId, new QuestRequirement(RequirementType.COMPLETED_QUEST,
                    holder.questSlots.get(slot), 1, null));
                openRequirementsMenu(player, holder.questId);
            }
            return;
        }
        QuestDefinition quest = definitions.get(holder.questId);
        if (quest == null) return;
        if (slot == 49) {
            openQuestEditor(player, quest.id());
        } else if (slot == 45) {
            prompt(player, new PendingTextInput(TextField.ADD_LEVEL_REQUIREMENT, quest.id(), 0));
        } else if (slot == 46) {
            openPrerequisiteQuestList(player, quest.id());
        } else if (slot == 47) {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held.getType().isAir()) {
                player.sendMessage("§cCầm item muốn kiểm tra ở tay chính trước.");
                return;
            }
            ItemStack exact = held.clone();
            exact.setAmount(1);
            pendingRequirementItems.put(player.getUniqueId(), exact);
            prompt(player, new PendingTextInput(TextField.ADD_ITEM_AMOUNT, quest.id(), 0));
        } else if (slot >= 0 && slot < quest.requirements().size()) {
            List<QuestRequirement> requirements = new java.util.ArrayList<>(quest.requirements());
            requirements.remove(slot);
            replaceDefinition(quest, quest.withRequirements(requirements));
            openRequirementsMenu(player, quest.id());
        }
    }

    private void addRequirement(Player player, String questId, QuestRequirement requirement) {
        QuestDefinition quest = definitions.get(questId);
        if (quest == null) return;
        if (quest.requirements().size() >= 32) {
            player.sendMessage("§cĐã đạt tối đa 32 điều kiện cho quest này.");
            return;
        }
        List<QuestRequirement> requirements = new java.util.ArrayList<>(quest.requirements());
        requirements.add(requirement);
        replaceDefinition(quest, quest.withRequirements(requirements));
    }

    @EventHandler
    public void onQuestAdminDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof QuestMenuHolder holder)) return;
        if (holder.menu != QuestMenu.REWARD_ITEMS) {
            event.setCancelled(true);
            return;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= holder.inventory.getSize() || rawSlot >= 45) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onRewardInventoryClose(InventoryCloseEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof QuestMenuHolder holder)
                || holder.menu != QuestMenu.REWARD_ITEMS || holder.navigating
                || !(event.getPlayer() instanceof Player player)
                || !holder.viewer.equals(player.getUniqueId())) return;
        saveRewardItems(player, holder);
        player.sendMessage("§eĐã tự lưu item thưởng trước khi đóng giao diện.");
    }

    @EventHandler
    public void onQuestAdminChat(AsyncPlayerChatEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        PendingTextInput pending = pendingTextInputs.remove(playerId);
        if (pending == null) return;
        event.setCancelled(true);
        String text = event.getMessage().trim();
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> handleTextInput(player, pending, text));
    }

    @EventHandler
    public void onQuestEditorQuit(PlayerQuitEvent event) {
        pendingTextInputs.remove(event.getPlayer().getUniqueId());
        pendingRequirementItems.remove(event.getPlayer().getUniqueId());
        pendingObjectiveItems.remove(event.getPlayer().getUniqueId());
    }

    private void prompt(Player player, PendingTextInput input) {
        pendingTextInputs.put(player.getUniqueId(), input);
        String instruction = switch (input.field()) {
            case CREATE_ID -> "§eHãy nhập §fID quest§e (chữ thường, số, dấu gạch nối), ví dụ: §fquest-test";
            case RENAME -> "§eHãy nhập §ftên quest§e (1-64 ký tự), ví dụ: §fTuần tra bìa rừng";
            case GIVER -> "§eHãy nhập §fID NPC giao quest§e (chữ thường, số, dấu gạch nối), ví dụ: §fhunter";
            case ADD_LEVEL_REQUIREMENT -> "§eHãy nhập §flevel RPG tối thiểu§e, số nguyên 1-1000, ví dụ: §f10";
            case ADD_ITEM_AMOUNT -> "§eHãy nhập §fsố lượng item yêu cầu§e, số nguyên 1-1000, ví dụ: §f3";
            case ADD_KILL_ENTITY, EDIT_KILL_ENTITY -> "§eHãy nhập §fEntityType cần hạ§e, ví dụ: §fZOMBIE";
            case ADD_KILL_AMOUNT, EDIT_KILL_AMOUNT -> "§eHãy nhập §fsố mob cần hạ§e, số nguyên 1-100000, ví dụ: §f5";
            case ADD_OBJECTIVE_ITEM_AMOUNT -> "§eHãy nhập §fsố item cần thu thập§e, số nguyên 1-100000, ví dụ: §f3";
            case ADD_SUBMIT_ITEM_AMOUNT -> "§eHãy nhập §fsố item cần nộp cho NPC§e, số nguyên 1-100000, ví dụ: §f10";
            case ADD_BOSS_ID -> "§eHãy nhập §fID boss hệ thống§e, ví dụ: §fwinter-tyrant";
            case ADD_BOSS_AMOUNT -> "§eHãy nhập §fsố lần cần hạ boss§e, số nguyên 1-100, ví dụ: §f1";
            case ADD_OBJECTIVE_NPC_ID -> "§eHãy nhập §fID NPC cần gặp§e, ví dụ: §fhunter";
            case SET_PRIMARY_NPC_ID -> "§eNhập §fID NPC cho bước đầu§e, ví dụ: §fhunter";
        };
        player.closeInventory();
        player.sendMessage("§6[Quản lý quest] " + instruction);
        player.sendMessage("§7Gõ §c!cancel §7để hủy và quay lại giao diện quest.");
    }

    private void handleTextInput(Player player, PendingTextInput input, String text) {
        if (!player.isOnline()) return;
        if (text.equalsIgnoreCase("!cancel")) {
            if (input.field() == TextField.ADD_ITEM_AMOUNT) pendingRequirementItems.remove(player.getUniqueId());
            if (input.field() == TextField.ADD_OBJECTIVE_ITEM_AMOUNT
                    || input.field() == TextField.ADD_SUBMIT_ITEM_AMOUNT)
                pendingObjectiveItems.remove(player.getUniqueId());
            reopenEditor(player, input);
            return;
        }
        try {
            switch (input.field()) {
                case CREATE_ID -> {
                    if (!text.matches("[a-z0-9]+(?:-[a-z0-9]+){0,7}") || text.length() > 48
                            || definitions.containsKey(text)) {
                        player.sendMessage("§cID không hợp lệ hoặc đã tồn tại; chỉ dùng chữ thường, số, dấu gạch nối.");
                        prompt(player, input);
                        return;
                    }
                    QuestDefinition created = QuestDefinition.create(text);
                    definitions.put(text, created);
                    if (!saveDefinitions()) {
                        definitions.remove(text);
                        player.sendMessage("§cKhông lưu được quest mới. Hãy xem console.");
                        openAdminList(player, 0);
                        return;
                    }
                    openQuestEditor(player, text);
                }
                case RENAME -> {
                    QuestDefinition old = definitions.get(input.questId());
                    if (old == null || text.isBlank() || text.length() > 64) {
                        player.sendMessage("§cTên quest cần có 1-64 ký tự.");
                        prompt(player, input);
                        return;
                    }
                    replaceDefinition(old, old.withTitle(text));
                    openQuestEditor(player, old.id());
                }
                case GIVER -> {
                    QuestDefinition old = definitions.get(input.questId());
                    if (old == null || !text.matches("[a-z0-9]+(?:-[a-z0-9]+){0,7}") || text.length() > 48) {
                        player.sendMessage("§cNPC ID không hợp lệ.");
                        prompt(player, input);
                        return;
                    }
                    replaceDefinition(old, old.withGiverNpcId(text));
                    openQuestEditor(player, old.id());
                }
                case ADD_LEVEL_REQUIREMENT -> {
                    QuestDefinition quest = definitions.get(input.questId());
                    int level;
                    try {
                        level = Integer.parseInt(text);
                    } catch (NumberFormatException exception) {
                        level = -1;
                    }
                    if (quest == null || level < 1 || level > 1000) {
                        player.sendMessage("§cLevel phải là số nguyên từ 1 đến 1000.");
                        prompt(player, input);
                        return;
                    }
                    addRequirement(player, quest.id(), new QuestRequirement(
                        RequirementType.LEVEL, Integer.toString(level), 1, null));
                    openRequirementsMenu(player, quest.id());
                }
                case ADD_ITEM_AMOUNT -> {
                    QuestDefinition quest = definitions.get(input.questId());
                    ItemStack item = pendingRequirementItems.remove(player.getUniqueId());
                    int amount;
                    try {
                        amount = Integer.parseInt(text);
                    } catch (NumberFormatException exception) {
                        amount = -1;
                    }
                    if (quest == null || item == null || amount < 1 || amount > 1000) {
                        player.sendMessage("§cSố lượng item phải từ 1 đến 1000.");
                        if (item != null) pendingRequirementItems.put(player.getUniqueId(), item);
                        prompt(player, input);
                        return;
                    }
                    addRequirement(player, quest.id(), new QuestRequirement(
                        RequirementType.EXACT_ITEM, "", amount, item));
                    openRequirementsMenu(player, quest.id());
                }
                case ADD_KILL_ENTITY -> {
                    QuestDefinition quest = definitions.get(input.questId());
                    EntityType entity;
                    try {
                        entity = EntityType.valueOf(text.trim().toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException exception) {
                        entity = null;
                    }
                    if (quest == null || entity == null || !entity.isAlive() || entity.getEntityClass() == null
                            || !org.bukkit.entity.LivingEntity.class.isAssignableFrom(entity.getEntityClass())) {
                        player.sendMessage("§cEntityType không hợp lệ hoặc không phải mob sống.");
                        prompt(player, input);
                        return;
                    }
                    prompt(player, new PendingTextInput(TextField.ADD_KILL_AMOUNT, quest.id(), 0,
                        entity.name()));
                }
                case EDIT_KILL_ENTITY -> {
                    QuestDefinition quest = definitions.get(input.questId());
                    EntityType entity;
                    try {
                        entity = EntityType.valueOf(text.trim().toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException exception) {
                        entity = null;
                    }
                    if (quest == null || entity == null || !entity.isAlive() || entity.getEntityClass() == null
                            || !org.bukkit.entity.LivingEntity.class.isAssignableFrom(entity.getEntityClass())) {
                        player.sendMessage("§cEntityType không hợp lệ hoặc không phải mob sống.");
                        prompt(player, input);
                        return;
                    }
                    prompt(player, new PendingTextInput(TextField.EDIT_KILL_AMOUNT, quest.id(), 0,
                        entity.name()));
                }
                case ADD_KILL_AMOUNT -> {
                    QuestDefinition quest = definitions.get(input.questId());
                    int amount;
                    try {
                        amount = Integer.parseInt(text.trim());
                    } catch (NumberFormatException exception) {
                        amount = -1;
                    }
                    if (quest == null || amount < 1 || amount > 100_000) {
                        player.sendMessage("§cSố lượng phải là số nguyên từ 1 đến 100000.");
                        prompt(player, input);
                        return;
                    }
                    EntityType entity = EntityType.valueOf(input.value());
                    List<QuestObjective> objectives = new java.util.ArrayList<>(quest.objectives());
                    if (objectives.size() >= 45) {
                        player.sendMessage("§cQuest đã đạt tối đa 45 mục tiêu.");
                    } else {
                        objectives.add(new QuestObjective(ObjectiveType.KILL_MOB, entity, amount,
                            "", null, "", 0));
                        replaceDefinition(quest, quest.withObjectives(objectives));
                    }
                    openObjectivesMenu(player, quest.id());
                }
                case EDIT_KILL_AMOUNT -> {
                    QuestDefinition quest = definitions.get(input.questId());
                    int amount;
                    try {
                        amount = Integer.parseInt(text.trim());
                    } catch (NumberFormatException exception) {
                        amount = -1;
                    }
                    if (quest == null || amount < 1 || amount > 100_000) {
                        player.sendMessage("§cSố lượng phải là số nguyên từ 1 đến 100000.");
                        prompt(player, input);
                        return;
                    }
                    EntityType entity = EntityType.valueOf(input.value());
                    QuestObjective first = new QuestObjective(ObjectiveType.KILL_MOB, entity, amount,
                        "", null, "", 0);
                    List<QuestObjective> objectives = new java.util.ArrayList<>(quest.objectives());
                    objectives.set(0, first);
                    replaceDefinition(quest, quest.withObjectives(objectives));
                    openObjectivesMenu(player, quest.id());
                }
                case ADD_OBJECTIVE_ITEM_AMOUNT -> {
                    QuestDefinition quest = definitions.get(input.questId());
                    ItemStack item = pendingObjectiveItems.remove(player.getUniqueId());
                    int amount;
                    try {
                        amount = Integer.parseInt(text.trim());
                    } catch (NumberFormatException exception) {
                        amount = -1;
                    }
                    if (quest == null || item == null || amount < 1 || amount > 100_000) {
                        player.sendMessage("§cSố lượng cần thu thập phải từ 1 đến 100000.");
                        if (item != null) pendingObjectiveItems.put(player.getUniqueId(), item);
                        prompt(player, input);
                        return;
                    }
                    List<QuestObjective> objectives = new java.util.ArrayList<>(quest.objectives());
                    if (objectives.size() >= 45) {
                        player.sendMessage("§cQuest đã đạt tối đa 45 mục tiêu.");
                    } else {
                        objectives.add(new QuestObjective(ObjectiveType.COLLECT_EXACT_ITEM, EntityType.ZOMBIE,
                            amount, "", item.getType(), item.getItemMeta() == null ? "" :
                                item.getItemMeta().getDisplayName(), 0, item));
                        replaceDefinition(quest, quest.withObjectives(objectives));
                    }
                    openObjectivesMenu(player, quest.id());
                }
                case ADD_SUBMIT_ITEM_AMOUNT -> {
                    QuestDefinition quest = definitions.get(input.questId());
                    ItemStack item = pendingObjectiveItems.remove(player.getUniqueId());
                    int amount;
                    try {
                        amount = Integer.parseInt(text.trim());
                    } catch (NumberFormatException exception) {
                        amount = -1;
                    }
                    if (quest == null || item == null || amount < 1 || amount > 100_000) {
                        player.sendMessage("§cSố lượng cần nộp phải từ 1 đến 100000.");
                        if (item != null) pendingObjectiveItems.put(player.getUniqueId(), item);
                        prompt(player, input);
                        return;
                    }
                    List<QuestObjective> objectives = new java.util.ArrayList<>(quest.objectives());
                    if (objectives.size() >= 45) player.sendMessage("§cQuest đã đạt tối đa 45 mục tiêu.");
                    else {
                        objectives.add(new QuestObjective(ObjectiveType.SUBMIT_EXACT_ITEM, EntityType.ZOMBIE,
                            amount, "", item.getType(), item.getItemMeta() == null ? ""
                                : item.getItemMeta().getDisplayName(), 0, item));
                        replaceDefinition(quest, quest.withObjectives(objectives));
                    }
                    openObjectivesMenu(player, quest.id());
                }
                case ADD_BOSS_ID -> {
                    QuestDefinition quest = definitions.get(input.questId());
                    String bossId = text.toLowerCase(Locale.ROOT);
                    if (quest == null || !bossId.matches("[a-z0-9]+(?:-[a-z0-9]+)*")
                            || !plugin.getConfigManager().bossConfigs.containsKey(bossId)) {
                        player.sendMessage("§cKhông tìm thấy ID boss hệ thống hợp lệ.");
                        prompt(player, input);
                        return;
                    }
                    prompt(player, new PendingTextInput(TextField.ADD_BOSS_AMOUNT, quest.id(), 0, bossId));
                }
                case ADD_BOSS_AMOUNT -> {
                    QuestDefinition quest = definitions.get(input.questId());
                    int amount;
                    try {
                        amount = Integer.parseInt(text.trim());
                    } catch (NumberFormatException exception) {
                        amount = -1;
                    }
                    if (quest == null || amount < 1 || amount > 100) {
                        player.sendMessage("§cSố lần cần hạ boss phải từ 1 đến 100.");
                        prompt(player, input);
                        return;
                    }
                    List<QuestObjective> objectives = new java.util.ArrayList<>(quest.objectives());
                    if (objectives.size() >= 45) player.sendMessage("§cQuest đã đạt tối đa 45 mục tiêu.");
                    else {
                        objectives.add(new QuestObjective(ObjectiveType.KILL_BOSS, EntityType.ZOMBIE,
                            amount, input.value(), null, "", 0));
                        replaceDefinition(quest, quest.withObjectives(objectives));
                    }
                    openObjectivesMenu(player, quest.id());
                }
                case ADD_OBJECTIVE_NPC_ID -> {
                    QuestDefinition quest = definitions.get(input.questId());
                    String npcId = text.trim().toLowerCase(Locale.ROOT);
                    if (quest == null || !npcId.matches("[a-z0-9]+(?:-[a-z0-9]+)*")
                            || plugin.getNpcManager() == null || !plugin.getNpcManager().hasNpc(npcId)) {
                        player.sendMessage("§cKhông tìm thấy NPC theo ID đó. Ví dụ hợp lệ: §fhunter");
                        prompt(player, input);
                        return;
                    }
                    List<QuestObjective> objectives = new java.util.ArrayList<>(quest.objectives());
                    if (objectives.size() >= 45) player.sendMessage("§cQuest đã đạt tối đa 45 mục tiêu.");
                    else {
                        objectives.add(new QuestObjective(ObjectiveType.VISIT_NPC, EntityType.ZOMBIE,
                            1, npcId, null, "", 0));
                        replaceDefinition(quest, quest.withObjectives(objectives));
                    }
                    openObjectivesMenu(player, quest.id());
                }
                case SET_PRIMARY_NPC_ID -> {
                    QuestDefinition quest = definitions.get(input.questId());
                    String npcId = text.trim().toLowerCase(Locale.ROOT);
                    if (quest == null || !npcId.matches("[a-z0-9]+(?:-[a-z0-9]+)*")
                            || plugin.getNpcManager() == null || !plugin.getNpcManager().hasNpc(npcId)) {
                        player.sendMessage("§cKhông tìm thấy NPC theo ID đó. Ví dụ hợp lệ: §fhunter");
                        prompt(player, input);
                        return;
                    }
                    List<QuestObjective> objectives = new java.util.ArrayList<>(quest.objectives());
                    objectives.set(0, new QuestObjective(ObjectiveType.VISIT_NPC, EntityType.ZOMBIE,
                        1, npcId, null, "", 0));
                    replaceDefinition(quest, quest.withObjectives(objectives));
                    openObjectivesMenu(player, quest.id());
                }
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "[QuestAdmin] Failed processing chat input for "
                + player.getName(), exception);
            player.sendMessage("§cKhông cập nhật được quest; hãy xem console.");
            reopenEditor(player, input);
        }
    }

    private void reopenEditor(Player player, PendingTextInput input) {
        if (input.field() == TextField.ADD_LEVEL_REQUIREMENT || input.field() == TextField.ADD_ITEM_AMOUNT)
            openRequirementsMenu(player, input.questId());
        else if (input.field() == TextField.ADD_OBJECTIVE_ITEM_AMOUNT
                || input.field() == TextField.ADD_SUBMIT_ITEM_AMOUNT
                || input.field() == TextField.ADD_BOSS_ID || input.field() == TextField.ADD_BOSS_AMOUNT
                || input.field() == TextField.ADD_OBJECTIVE_NPC_ID || input.field() == TextField.SET_PRIMARY_NPC_ID)
            openObjectivesMenu(player, input.questId());
        else if (input.field() == TextField.ADD_KILL_ENTITY || input.field() == TextField.ADD_KILL_AMOUNT
                || input.field() == TextField.EDIT_KILL_ENTITY || input.field() == TextField.EDIT_KILL_AMOUNT)
            openObjectivesMenu(player, input.questId());
        else if (input.questId() == null) openAdminList(player, input.page());
        else openQuestEditor(player, input.questId());
    }

    private void replaceDefinition(QuestDefinition old, QuestDefinition updated) {
        definitions.put(old.id(), updated);
        List<Long> oldSellValues = questRewardSellValues.get(old.id());
        List<Long> updatedSellValues = remapRewardSellValues(old.itemRewards(),
            oldSellValues == null ? defaultRewardSellValues(old.itemRewards()) : oldSellValues,
            updated.itemRewards());
        questRewardSellValues.put(old.id(), updatedSellValues);
        if (!saveDefinitions()) {
            definitions.put(old.id(), old);
            if (oldSellValues == null) questRewardSellValues.remove(old.id());
            else questRewardSellValues.put(old.id(), oldSellValues);
        }
    }

    private void deleteDefinition(QuestDefinition quest) {
        if (hasActiveQuest(quest.id()) || hasSuccessorReferences(quest.id())) return;
        definitions.remove(quest.id());
        QuestDialogRefs oldRefs = questDialogs.remove(quest.id());
        Long oldItemSellValue = questItemSellValues.remove(quest.id());
        List<Long> oldRewardSellValues = questRewardSellValues.remove(quest.id());
        if (!saveDefinitions()) {
            definitions.put(quest.id(), quest);
            if (oldRefs != null) questDialogs.put(quest.id(), oldRefs);
            if (oldItemSellValue != null) questItemSellValues.put(quest.id(), oldItemSellValue);
            if (oldRewardSellValues != null) questRewardSellValues.put(quest.id(), oldRewardSellValues);
        }
    }

    private List<Long> remapRewardSellValues(List<ItemStack> oldItems, List<Long> oldValues,
            List<ItemStack> newItems) {
        boolean[] claimed = new boolean[oldItems.size()];
        List<Long> mapped = new java.util.ArrayList<>(newItems.size());
        for (ItemStack next : newItems) {
            long value = defaultRewardSellValues(List.of(next)).getFirst();
            for (int index = 0; index < oldItems.size(); index++) {
                if (!claimed[index] && oldItems.get(index).isSimilar(next)) {
                    claimed[index] = true;
                    if (index < oldValues.size()) value = oldValues.get(index);
                    break;
                }
            }
            mapped.add(value);
        }
        return List.copyOf(mapped);
    }

    private static List<Long> zeroSellValues(int size) {
        return java.util.Collections.nCopies(size, 0L);
    }

    private List<Long> defaultRewardSellValues(List<ItemStack> items) {
        List<Long> values = new java.util.ArrayList<>(items.size());
        for (ItemStack item : items) {
            ItemMeta meta = item.getItemMeta();
            Long configured = meta == null ? null
                : meta.getPersistentDataContainer().get(rpgSellValueKey, PersistentDataType.LONG);
            values.add(configured != null && configured >= 0 && configured <= 1_000_000L ? configured : 0L);
        }
        return List.copyOf(values);
    }

    private boolean hasActiveQuest(String questId) {
        return activeByPlayer.values().stream().anyMatch(active -> active.containsKey(questId));
    }

    private boolean hasSuccessorReferences(String questId) {
        return definitions.values().stream().anyMatch(quest -> questId.equals(quest.successorQuestId()));
    }

    private boolean saveDefinitions() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().parseComments(true);
        try {
            yaml.load(definitionsFile);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().log(Level.SEVERE, "[QuestAdmin] Could not reload quest file before saving.", exception);
            return false;
        }
        for (QuestDefinition quest : definitions.values()) {
            String base = "quests." + quest.id();
            yaml.set(base + ".enabled", quest.enabled());
            yaml.set(base + ".title", quest.title());
            yaml.set(base + ".giver-npc-id", quest.giverNpcId());
            yaml.set(base + ".successor-quest-id", quest.successorQuestId());
            yaml.setComments(base + ".successor-quest-id", List.of(
                "[VI] ID quest tự giao sau khi quest này hoàn thành; tùy chọn, không tự tạo vòng lặp.",
                "[EN] Quest ID to auto-start after this quest completes; optional and cycle-checked."));
            yaml.set(base + ".repeatable.enabled", repeatableQuests.getOrDefault(quest.id(), false));
            yaml.set(base + ".repeatable.daily-limit", repeatDailyLimits.getOrDefault(quest.id(), 0));
            yaml.setComments(base + ".repeatable", List.of(
                "[VI] Cho phép người chơi hoàn thành và nhận lại quest; daily-limit 0 là không giới hạn, 1-10 là số lượt tối đa mỗi ngày.",
                "[EN] Allows players to repeat and receive rewards again; daily-limit 0 is unlimited, 1-10 is the daily maximum."));
            QuestDialogRefs refs = dialogRefs(quest.id());
            yaml.set(base + ".dialogs.available", refs.available());
            yaml.set(base + ".dialogs.in-progress", refs.inProgress());
            yaml.set(base + ".dialogs.completed", refs.completed());
            yaml.set(base + ".dialogs.unavailable", refs.unavailable());
            yaml.setComments(base + ".dialogs", List.of(
                "[VI] ID hội thoại tùy chọn cho từng trạng thái quest; tạo trong GUI Quản lý quest-dialog.",
                "[EN] Optional dialog ID for each quest state; create these in the Quest Dialog Manager GUI."));
            yaml.set(base + ".objective.type", switch (quest.objectiveType()) {
                case KILL_MOB -> "kill-mob";
                case KILL_BOSS -> "kill-boss";
                case COLLECT_RPG_ITEM -> "collect-rpg-item";
                case COLLECT_EXACT_ITEM -> "collect-exact-item";
                case SUBMIT_EXACT_ITEM -> "submit-exact-item";
                case VISIT_NPC -> "visit-npc";
            });
            yaml.set(base + ".objective.entity", quest.targetEntity().name());
            yaml.set(base + ".objective.amount", quest.target());
            if (quest.objectiveType() == ObjectiveType.COLLECT_RPG_ITEM) {
                yaml.set(base + ".objective.sell-value", questItemSellValues.getOrDefault(quest.id(), 0L));
                yaml.setComments(base + ".objective.sell-value", List.of(
                    "[VI] Giá system merchant trả cho mỗi item RPG nhiệm vụ; mặc định 0 (không mua), hợp lệ 0-1000000 VNCOIN.",
                    "[EN] System-merchant value per generated quest RPG item; default 0 (not purchasable), valid 0-1000000 VNCOIN."));
            }
            if (quest.objectiveType() == ObjectiveType.KILL_BOSS) {
                yaml.set(base + ".objective.boss-id", quest.itemId());
            } else if (quest.objectiveType() == ObjectiveType.COLLECT_RPG_ITEM) {
                yaml.set(base + ".objective.item-id", quest.itemId());
                yaml.set(base + ".objective.item-material", quest.itemMaterial().name());
                yaml.set(base + ".objective.item-name", quest.itemName());
                yaml.set(base + ".objective.drop-chance-percent", quest.dropChancePercent());
            } else if (quest.objectiveType() == ObjectiveType.VISIT_NPC) {
                yaml.set(base + ".objective.npc-id", quest.itemId());
            }
            List<Map<String, Object>> extraObjectives = new java.util.ArrayList<>();
            for (QuestObjective objective : quest.additionalObjectives()) {
                Map<String, Object> saved = new LinkedHashMap<>();
                saved.put("type", switch (objective.type()) {
                    case KILL_MOB -> "kill-mob";
                    case KILL_BOSS -> "kill-boss";
                    case COLLECT_RPG_ITEM -> "collect-rpg-item";
                    case COLLECT_EXACT_ITEM -> "collect-exact-item";
                    case SUBMIT_EXACT_ITEM -> "submit-exact-item";
                    case VISIT_NPC -> "visit-npc";
                });
                if (objective.type() == ObjectiveType.KILL_MOB) saved.put("entity", objective.entity().name());
                else if (objective.type() == ObjectiveType.KILL_BOSS) saved.put("boss-id", objective.itemId());
                else if (objective.type() == ObjectiveType.VISIT_NPC) saved.put("npc-id", objective.itemId());
                else saved.put("item", objective.requiredItem().serialize());
                saved.put("amount", objective.amount());
                extraObjectives.add(saved);
            }
            yaml.set(base + ".additional-objectives", extraObjectives);
            yaml.setComments(base + ".additional-objectives", List.of(
                "[VI] Mục tiêu tuần tự bổ sung; hỗ trợ kill-mob/kill-boss/collect-exact-item/submit-exact-item/visit-npc, tối đa 44 bước bổ sung.",
                "[EN] Additional sequential objectives; supports kill-mob/kill-boss/collect-exact-item/submit-exact-item/visit-npc, maximum 44 extra steps."));
            yaml.set(base + ".reward.vncoin", quest.vncoinReward());
            yaml.set(base + ".reward.character-exp", quest.experienceReward());
            yaml.set(base + ".reward.items", quest.itemRewards().stream().map(ItemStack::serialize).toList());
            yaml.set(base + ".reward.item-sell-values", questRewardSellValues.getOrDefault(quest.id(),
                zeroSellValues(quest.itemRewards().size())));
            yaml.setComments(base + ".reward.item-sell-values", List.of(
                "[VI] Giá VNCOIN cho từng stack item thưởng, cùng thứ tự với reward.items; 0 nghĩa là system merchant không mua; hợp lệ 0-1000000.",
                "[EN] VNCOIN sell value per reward stack, in the same order as reward.items; 0 disables system-merchant purchases; valid 0-1000000."));
            yaml.setComments(base + ".reward.character-exp", List.of("[VI] EXP nhân vật thưởng; hợp lệ 0-100000.",
                "[EN] Character EXP reward; valid range 0-100000."));
            yaml.setComments(base + ".reward.items", List.of("[VI] ItemStack thưởng đã cấu hình trong giao diện; tối đa 45 stack.",
                "[EN] ItemStack rewards configured through the GUI; maximum 45 stacks."));
            List<Map<String, Object>> requirementData = new java.util.ArrayList<>();
            for (QuestRequirement requirement : quest.requirements()) {
                Map<String, Object> saved = new LinkedHashMap<>();
                switch (requirement.type()) {
                    case LEVEL -> {
                        saved.put("type", "level");
                        saved.put("value", Integer.parseInt(requirement.value()));
                    }
                    case COMPLETED_QUEST -> {
                        saved.put("type", "completed-quest");
                        saved.put("quest-id", requirement.value());
                    }
                    case EXACT_ITEM -> {
                        saved.put("type", "exact-item");
                        saved.put("item", requirement.item().serialize());
                        saved.put("amount", requirement.amount());
                    }
                }
                requirementData.add(saved);
            }
            yaml.set(base + ".requirements", requirementData);
            yaml.setComments(base + ".requirements", List.of("[VI] Tất cả điều kiện trong danh sách phải đạt; có thể để trống.",
                "[EN] Every listed requirement must pass; this list may be empty."));
            yaml.setComments(base + ".enabled", List.of("[VI] Bật/tắt quest; mặc định true.",
                "[EN] Enable/disable this quest; default true."));
            yaml.setComments(base + ".title", List.of("[VI] Tên hiển thị quest; tối đa 64 ký tự.",
                "[EN] Quest display name; maximum 64 characters."));
            yaml.setComments(base + ".giver-npc-id", List.of("[VI] ID NPC giao quest.",
                "[EN] NPC ID that offers this quest."));
            yaml.setComments(base + ".objective.type", List.of(
                "[VI] Loại mục tiêu gốc: kill-mob, collect-rpg-item hoặc kill-boss.",
                "[EN] Base objective type: kill-mob, collect-rpg-item, or kill-boss."));
            yaml.setComments(base + ".objective.entity", List.of("[VI] Bukkit EntityType liên quan tới mục tiêu.",
                "[EN] Bukkit EntityType associated with this objective."));
            yaml.setComments(base + ".objective.amount", List.of("[VI] Số lượng mục tiêu; hợp lệ 1-100000.",
                "[EN] Objective amount; valid range 1-100000."));
            yaml.setComments(base + ".reward.vncoin", List.of("[VI] VNCOIN thưởng; hợp lệ 1-1000000.",
                "[EN] VNCOIN reward; valid range 1-1000000."));
            if (quest.objectiveType() == ObjectiveType.COLLECT_RPG_ITEM) {
                yaml.setComments(base + ".objective.item-id", List.of("[VI] ID PDC item RPG.",
                    "[EN] RPG item PDC ID."));
                yaml.setComments(base + ".objective.item-material", List.of("[VI] Bukkit Material của item.",
                    "[EN] Bukkit Material for the item."));
                yaml.setComments(base + ".objective.item-name", List.of("[VI] Tên item; tối đa 64 ký tự.",
                    "[EN] Item name; maximum 64 characters."));
                yaml.setComments(base + ".objective.drop-chance-percent", List.of("[VI] Tỉ lệ rơi; hợp lệ 1-100%.",
                    "[EN] Drop chance; valid range 1-100%."));
            }
        }
        ConfigurationSection section = yaml.getConfigurationSection("quests");
        if (section != null) {
            for (String id : List.copyOf(section.getKeys(false))) {
                if (!definitions.containsKey(id)) yaml.set("quests." + id, null);
            }
        }
        File temporary = null;
        try {
            File parent = definitionsFile.getAbsoluteFile().getParentFile();
            temporary = Files.createTempFile(parent.toPath(), "quests-", ".yml.tmp").toFile();
            Files.writeString(temporary.toPath(), yaml.saveToString(), java.nio.charset.StandardCharsets.UTF_8);
            try {
                Files.move(temporary.toPath(), definitionsFile.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary.toPath(), definitionsFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "[QuestAdmin] Could not atomically save quest definitions.", exception);
            if (temporary != null) temporary.delete();
            return false;
        }
    }

    private ItemStack adminItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack dialogChoiceItem(String label, String dialogId) {
        QuestDialogManager manager = plugin.getQuestDialogManager();
        boolean available = dialogId != null && manager != null && manager.hasDialog(dialogId);
        return adminItem(available ? Material.WRITABLE_BOOK : Material.BOOK,
            (available ? "§d" : "§7") + label,
            List.of("§7Đang chọn: §f" + (dialogId == null ? "(chưa chọn)" : dialogId),
                available ? "§eNhấn để đổi hội thoại." : "§cHội thoại chưa chọn hoặc không tồn tại."));
    }

    private QuestDialogRefs dialogRefs(String questId) {
        return questDialogs.getOrDefault(questId, QuestDialogRefs.EMPTY);
    }

    private void updateQuestDialog(Player player, String questId, QuestDialogStage stage, String dialogId) {
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            player.sendMessage("§cBạn không còn quyền quản lý quest.");
            return;
        }
        QuestDefinition quest = definitions.get(questId);
        if (quest == null) {
            openAdminList(player, 0);
            return;
        }
        QuestDialogRefs old = dialogRefs(questId);
        QuestDialogRefs updated = old.with(stage, validDialogId(dialogId));
        questDialogs.put(questId, updated);
        if (!saveDefinitions()) {
            if (old.equals(QuestDialogRefs.EMPTY)) questDialogs.remove(questId);
            else questDialogs.put(questId, old);
            player.sendMessage("§cKhông lưu được lựa chọn quest-dialog; xem console.");
        } else {
            player.sendMessage("§aĐã cập nhật hội thoại cho trạng thái §f" + stage.displayName + "§a.");
        }
        openQuestEditor(player, questId);
    }

    private static String validDialogId(String id) {
        return id != null && id.matches("[a-z0-9]+(?:-[a-z0-9]+)*") ? id : null;
    }

    private void loadDefinitions() throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            YamlDuplicateKeyValidator.validateNoDuplicateKeys(definitionsFile);
            yaml.load(definitionsFile);
        } catch (InvalidConfigurationException | YAMLException exception) {
            throw new IOException("Quest definitions are invalid YAML.", exception);
        }
        if (yaml.getInt("schema-version", -1) != 1) {
            throw new IOException("Unsupported quest schema-version; expected 1.");
        }
        ConfigurationSection section = yaml.getConfigurationSection("quests");
        if (section == null || section.getKeys(false).size() > 100) {
            throw new IOException("Quest definitions may contain 0-100 entries.");
        }
        boolean needsSellValueDefaults = false;
        boolean needsRepeatableDefaults = false;
        for (String id : section.getKeys(false)) {
            ConfigurationSection quest = section.getConfigurationSection(id);
            if (quest == null) throw new IOException("Quest '" + id + "' must be a configuration section.");
            if (!quest.contains("repeatable.enabled") || !quest.contains("repeatable.daily-limit"))
                needsRepeatableDefaults = true;
            String giver = quest.getString("giver-npc-id", "").trim();
            String title = quest.getString("title", "").trim();
            String successorQuestId = quest.getString("successor-quest-id", "").trim();
            if (successorQuestId.isEmpty()) successorQuestId = null;
            String type = quest.getString("objective.type", "").trim().toLowerCase(Locale.ROOT);
            if ("collect-rpg-item".equals(type) && !quest.contains("objective.sell-value"))
                needsSellValueDefaults = true;
            if (!quest.contains("reward.item-sell-values")) needsSellValueDefaults = true;
            long objectiveSellValue = parseSellValue(quest.get("objective.sell-value"),
                "quests." + id + ".objective.sell-value");
            String entityText = quest.getString("objective.entity", "ZOMBIE").trim().toUpperCase(Locale.ROOT);
            int target = quest.getInt("objective.amount", 0);
            int reward = quest.getInt("reward.vncoin", 0);
            int experienceReward = quest.getInt("reward.character-exp", 0);
            boolean repeatable = quest.getBoolean("repeatable.enabled", false);
            int repeatDailyLimit = quest.getInt("repeatable.daily-limit", 0);
            List<ItemStack> itemRewards = parseItemRewards(quest.getList("reward.items", List.of()), id);
            Object rawRewardSellValues = quest.get("reward.item-sell-values");
            if (rawRewardSellValues != null && !(rawRewardSellValues instanceof List<?>))
                throw new IOException("Quest '" + id + "' reward.item-sell-values must be a list.");
            List<Long> rewardSellValues = parseRewardSellValues(
                rawRewardSellValues instanceof List<?> values ? values : List.of(), itemRewards, id);
            EntityType entityType;
            try {
                entityType = EntityType.valueOf(entityText);
            } catch (IllegalArgumentException exception) {
                throw new IOException("Quest '" + id + "' has an invalid objective.entity.", exception);
            }
            ObjectiveType objectiveType = switch (type) {
                case "kill-mob" -> ObjectiveType.KILL_MOB;
                case "collect-rpg-item" -> ObjectiveType.COLLECT_RPG_ITEM;
                case "kill-boss" -> ObjectiveType.KILL_BOSS;
                case "visit-npc" -> ObjectiveType.VISIT_NPC;
                default -> null;
            };
            String itemId = objectiveType == ObjectiveType.VISIT_NPC
                ? quest.getString("objective.npc-id", "").trim()
                : quest.getString("objective.item-id", "").trim();
            String itemMaterialText = quest.getString("objective.item-material", "").trim().toUpperCase(Locale.ROOT);
            String itemName = quest.getString("objective.item-name", "").trim();
            int dropChance = quest.getInt("objective.drop-chance-percent", 0);
            List<QuestRequirement> requirements = parseRequirements(quest.getMapList("requirements"), id);
            Material itemMaterial = null;
            if (objectiveType == ObjectiveType.COLLECT_RPG_ITEM) {
                itemMaterial = Material.matchMaterial(itemMaterialText);
            }
            List<QuestObjective> additionalObjectives = parseAdditionalObjectives(
                quest.getMapList("additional-objectives"), id);
            if (!id.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || id.length() > 64
                    || !giver.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || giver.length() > 64
                    || title.isBlank() || title.length() > 64 || objectiveType == null
                    || (objectiveType != ObjectiveType.KILL_BOSS && objectiveType != ObjectiveType.VISIT_NPC
                        && !entityType.isAlive())
                    || target < 1 || target > 100_000 || reward < 0 || reward > 1_000_000
                    || experienceReward < 0 || experienceReward > 100_000
                    || repeatDailyLimit < 0 || repeatDailyLimit > 10
                    || objectiveType == ObjectiveType.COLLECT_RPG_ITEM
                        && (objectiveSellValue < 0 || objectiveSellValue > 1_000_000L)
                    || (objectiveType == ObjectiveType.KILL_BOSS
                        && (!quest.getString("objective.boss-id", "").matches("[a-z0-9]+(?:-[a-z0-9]+)*")
                            || !plugin.getConfigManager().bossConfigs.containsKey(
                                quest.getString("objective.boss-id", ""))))
                    || (objectiveType == ObjectiveType.COLLECT_RPG_ITEM
                        && (!itemId.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || itemMaterial == null
                            || itemMaterial.isAir() || itemName.isBlank() || itemName.length() > 64
                            || dropChance < 1 || dropChance > 100))
                    || (objectiveType == ObjectiveType.VISIT_NPC
                        && !itemId.matches("[a-z0-9]+(?:-[a-z0-9]+)*"))) {
                throw new IOException("Quest '" + id + "' has invalid fields or unsupported bounds.");
            }
            definitions.put(id, new QuestDefinition(id, giver, title, objectiveType, entityType,
                target, reward, objectiveType == ObjectiveType.KILL_BOSS
                    ? quest.getString("objective.boss-id", "") : itemId, itemMaterial, itemName, dropChance,
                quest.getBoolean("enabled", true), requirements, experienceReward, itemRewards,
                additionalObjectives, successorQuestId));
            repeatableQuests.put(id, repeatable);
            repeatDailyLimits.put(id, repeatDailyLimit);
            questRewardSellValues.put(id, rewardSellValues);
            if (objectiveType == ObjectiveType.COLLECT_RPG_ITEM)
                questItemSellValues.put(id, objectiveSellValue);
            debugQuest("merchant-values-loaded quest=" + id
                + ", objective=" + questItemSellValues.getOrDefault(id, 0L)
                + ", reward-items=" + rewardSellValues.size() + ", reward-values=" + rewardSellValues);
            ConfigurationSection dialogs = quest.getConfigurationSection("dialogs");
            if (dialogs != null) {
                questDialogs.put(id, new QuestDialogRefs(
                    validDialogId(dialogs.getString("available")),
                    validDialogId(dialogs.getString("in-progress")),
                    validDialogId(dialogs.getString("completed")),
                    validDialogId(dialogs.getString("unavailable"))));
            }
        }
        validateSuccessorGraph();
        if (needsSellValueDefaults || needsRepeatableDefaults) {
            if (saveDefinitions()) {
                plugin.getLogger().info("[Quest][Config] Added missing safe defaults to quest definitions; "
                    + "RPG item values default to 0 VNCOIN and quests default to non-repeatable.");
            } else {
                plugin.getLogger().warning("[Quest][Config] Could not persist missing defaults; "
                    + "in-memory defaults remain non-repeatable/0 VNCOIN.");
            }
        }
        plugin.getLogger().info("[Quest] Loaded " + definitions.size() + " NPC quest definitions.");
    }

    private List<Long> parseRewardSellValues(List<?> rawValues, List<ItemStack> rewardItems, String questId)
            throws IOException {
        if (rawValues.isEmpty()) return defaultRewardSellValues(rewardItems);
        if (rawValues.size() != rewardItems.size())
            throw new IOException("Quest '" + questId + "' reward.item-sell-values must match reward.items length.");
        List<Long> values = new java.util.ArrayList<>(rawValues.size());
        for (Object raw : rawValues) {
            if (!(raw instanceof Number number))
                throw new IOException("Quest '" + questId + "' has a non-numeric reward item sell value.");
            values.add(parseSellValue(number, "quests." + questId + ".reward.item-sell-values"));
        }
        return List.copyOf(values);
    }

    private long parseSellValue(Object raw, String path) throws IOException {
        if (raw == null) return 0L;
        if (!(raw instanceof Number number))
            throw new IOException(path + " must be an integer from 0 to 1000000.");
        long value = number.longValue();
        if (number.doubleValue() != (double)value || value < 0 || value > 1_000_000L)
            throw new IOException(path + " must be an integer from 0 to 1000000.");
        return value;
    }

    private void validateSuccessorGraph() throws IOException {
        for (QuestDefinition quest : definitions.values()) {
            String successorId = quest.successorQuestId();
            if (successorId == null) continue;
            if (!successorId.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || successorId.length() > 64
                    || !definitions.containsKey(successorId)
                    || wouldCreateSuccessorCycle(quest.id(), successorId)) {
                throw new IOException("Quest '" + quest.id() + "' has an invalid successor quest link.");
            }
        }
    }

    private List<QuestObjective> parseAdditionalObjectives(List<Map<?, ?>> rawObjectives, String questId)
            throws IOException {
        if (rawObjectives.size() > 44) throw new IOException("Quest '" + questId + "' has over 44 extra objectives.");
        List<QuestObjective> objectives = new java.util.ArrayList<>();
        for (Map<?, ?> raw : rawObjectives) {
            Object type = raw.get("type");
            Object entityValue = raw.get("entity");
            Object amountValue = raw.get("amount");
            if (!(amountValue instanceof Number amountNumber)) {
                throw new IOException("Quest '" + questId + "' has an invalid additional objective.");
            }
            int amount = amountNumber.intValue();
            if (amount < 1 || amount > 100_000) {
                throw new IOException("Quest '" + questId + "' has invalid additional objective values.");
            }
            if ("kill-mob".equals(type) && entityValue instanceof String entityName) {
                EntityType entity;
                try {
                    entity = EntityType.valueOf(entityName.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    throw new IOException("Quest '" + questId + "' has an invalid additional objective entity.", exception);
                }
                if (!entity.isAlive() || entity.getEntityClass() == null
                        || !org.bukkit.entity.LivingEntity.class.isAssignableFrom(entity.getEntityClass())) {
                    throw new IOException("Quest '" + questId + "' has invalid additional objective values.");
                }
                objectives.add(new QuestObjective(ObjectiveType.KILL_MOB, entity, amount, "", null, "", 0));
            } else if (("collect-exact-item".equals(type) || "submit-exact-item".equals(type))
                    && raw.get("item") instanceof Map<?, ?> itemMap) {
                ItemStack item;
                try {
                    item = ItemStack.deserialize(castStringObjectMap(itemMap));
                } catch (RuntimeException exception) {
                    throw new IOException("Quest '" + questId + "' has an invalid collect item.", exception);
                }
                if (item.getType().isAir()) throw new IOException("Quest '" + questId + "' has an empty collect item.");
                String itemName = item.getItemMeta() == null ? "" : item.getItemMeta().getDisplayName();
                ObjectiveType objectiveType = "submit-exact-item".equals(type)
                    ? ObjectiveType.SUBMIT_EXACT_ITEM : ObjectiveType.COLLECT_EXACT_ITEM;
                objectives.add(new QuestObjective(objectiveType, EntityType.ZOMBIE,
                    amount, "", item.getType(), itemName, 0, item));
            } else if ("kill-boss".equals(type) && raw.get("boss-id") instanceof String bossId
                    && bossId.matches("[a-z0-9]+(?:-[a-z0-9]+)*")
                    && plugin.getConfigManager().bossConfigs.containsKey(bossId)) {
                objectives.add(new QuestObjective(ObjectiveType.KILL_BOSS, EntityType.ZOMBIE,
                    amount, bossId, null, "", 0));
            } else if ("visit-npc".equals(type) && raw.get("npc-id") instanceof String npcId
                    && npcId.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
                objectives.add(new QuestObjective(ObjectiveType.VISIT_NPC, EntityType.ZOMBIE,
                    amount, npcId, null, "", 0));
            } else {
                throw new IOException("Quest '" + questId + "' has an unsupported additional objective.");
            }
        }
        return List.copyOf(objectives);
    }

    private List<ItemStack> parseItemRewards(List<?> rawItems, String questId) throws IOException {
        if (rawItems.size() > 45) throw new IOException("Quest '" + questId + "' has over 45 item rewards.");
        List<ItemStack> parsed = new java.util.ArrayList<>();
        for (Object raw : rawItems) {
            ItemStack item = raw instanceof ItemStack stack ? stack.clone()
                : raw instanceof Map<?, ?> map ? ItemStack.deserialize(castStringObjectMap(map)) : null;
            if (item == null || item.getType().isAir()) {
                throw new IOException("Quest '" + questId + "' has an invalid item reward.");
            }
            parsed.add(item);
        }
        return List.copyOf(parsed);
    }

    private List<QuestRequirement> parseRequirements(List<Map<?, ?>> rawRequirements, String questId)
            throws IOException {
        if (rawRequirements.size() > 32) throw new IOException("Quest '" + questId + "' has over 32 requirements.");
        List<QuestRequirement> requirements = new java.util.ArrayList<>();
        for (Map<?, ?> raw : rawRequirements) {
            Object rawType = raw.get("type");
            if (!(rawType instanceof String type)) throw new IOException("Quest '" + questId + "' has invalid requirement.");
            switch (type) {
                case "level" -> {
                    int level = raw.get("value") instanceof Number number ? number.intValue() : 0;
                    if (level < 1 || level > 1000) throw new IOException("Quest '" + questId + "' has invalid level requirement.");
                    requirements.add(new QuestRequirement(RequirementType.LEVEL, Integer.toString(level), 1, null));
                }
                case "completed-quest" -> {
                    Object value = raw.get("quest-id");
                    if (!(value instanceof String quest) || !quest.matches("[a-z0-9]+(?:-[a-z0-9]+){0,7}"))
                        throw new IOException("Quest '" + questId + "' has invalid completed-quest requirement.");
                    requirements.add(new QuestRequirement(RequirementType.COMPLETED_QUEST, quest, 1, null));
                }
                case "exact-item" -> {
                    Object rawItem = raw.get("item");
                    int amount = raw.get("amount") instanceof Number number ? number.intValue() : 0;
                    ItemStack item = rawItem instanceof Map<?, ?> itemMap
                        ? ItemStack.deserialize(castStringObjectMap(itemMap)) : null;
                    if (item == null || item.getType().isAir() || amount < 1 || amount > 1000)
                        throw new IOException("Quest '" + questId + "' has invalid exact-item requirement.");
                    item.setAmount(1);
                    requirements.add(new QuestRequirement(RequirementType.EXACT_ITEM, "", amount, item));
                }
                default -> throw new IOException("Quest '" + questId + "' has unsupported requirement '" + type + "'.");
            }
        }
        return List.copyOf(requirements);
    }

    private static Map<String, Object> castStringObjectMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> {
            if (key instanceof String string) result.put(string, value);
        });
        return result;
    }

    public void acceptQuest(Player player, String npcId, String questId) {
        acceptQuest(player, npcId, questId, null);
    }

    private void acceptQuest(Player player, String npcId, String questId, Consumer<AcceptStatus> completion) {
        QuestDefinition quest = definitions.get(questId);
        if (quest == null || !quest.enabled() || !quest.giverNpcId().equals(npcId)) {
            player.sendMessage("§cNhiệm vụ này không khả dụng tại NPC hiện tại.");
            plugin.getLogger().warning("[Quest] Rejected accept action: npc=" + npcId + ", quest=" + questId);
            return;
        }
        UUID playerId = player.getUniqueId();
        debugQuest("accept-start player=" + player.getName() + ", npc=" + npcId + ", quest=" + questId);
        if (activeByPlayer.getOrDefault(playerId, Map.of()).containsKey(questId)
                || !isRepeatable(questId)
                    && completedByPlayer.getOrDefault(playerId, java.util.Set.of()).contains(questId)) {
            if (completion == null) player.sendMessage("§eBạn đã nhận nhiệm vụ này hoặc đã hoàn thành nó.");
            else completion.accept(AcceptStatus.ALREADY_STARTED);
            debugQuest("accept-cache-hit player=" + player.getName() + ", quest=" + questId
                + ", status=already-started");
            return;
        }
        for (QuestRequirement requirement : quest.requirements()) {
            if (requirement.type() == RequirementType.EXACT_ITEM
                    && countExactItems(player, requirement.item()) < requirement.amount()) {
                player.sendMessage("§cThiếu vật phẩm yêu cầu: §f" + requirement.description());
                if (completion != null) completion.accept(AcceptStatus.REQUIREMENTS_NOT_MET);
                return;
            }
        }
        PlayerProfileDatabaseManager database = plugin.getPlayerProfileDatabaseManager();
        database.executeGameplayDatabaseTask("quest.accept player=" + player.getName() + " quest=" + questId, () -> {
            var profile = database.getPlayerProfile(playerId.toString());
            if (profile.isEmpty() || !profile.get().active()) return AcceptResult.NO_PROFILE;
            for (QuestRequirement requirement : quest.requirements()) {
                if (requirement.type() == RequirementType.LEVEL
                        && profile.get().level() < Integer.parseInt(requirement.value())) {
                    return AcceptResult.REQUIREMENTS_NOT_MET;
                }
                if (requirement.type() == RequirementType.COMPLETED_QUEST) {
                    var previous = database.getQuestProgress(playerId.toString(), requirement.value());
                    if (previous.isEmpty() || !"completed".equals(previous.get().status())) {
                        return AcceptResult.REQUIREMENTS_NOT_MET;
                    }
                }
            }
            String startedAt = Instant.now().toString();
            String resetDate = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
            PlayerProfileDatabaseManager.QuestStartResult start = database.startQuestProgressWithLimit(
                playerId.toString(), questId, "0", startedAt, 3, isRepeatable(questId),
                repeatDailyLimits.getOrDefault(questId, 0), resetDate);
            if (start == PlayerProfileDatabaseManager.QuestStartResult.ALREADY_EXISTS)
                return AcceptResult.ALREADY_STARTED;
            if (start == PlayerProfileDatabaseManager.QuestStartResult.ACTIVE_LIMIT_REACHED)
                return AcceptResult.ACTIVE_LIMIT_REACHED;
            if (start == PlayerProfileDatabaseManager.QuestStartResult.DAILY_LIMIT_REACHED)
                return AcceptResult.DAILY_LIMIT_REACHED;
            database.setQuestTracked(playerId.toString(), questId);
            var progress = database.getActiveQuestProgress(playerId.toString());
            return new AcceptResult(AcceptStatus.ACCEPTED, progress);
        }).whenComplete((result, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (failure != null) {
                plugin.getLogger().log(Level.SEVERE, "[Quest] Failed to accept " + questId
                    + " for " + player.getName(), failure);
                if (player.isOnline()) player.sendMessage("§cKhông thể nhận nhiệm vụ lúc này.");
                if (completion != null) completion.accept(AcceptStatus.TRANSIENT_FAILURE);
                return;
            }
            if (!player.isOnline()) {
                if (completion != null) completion.accept(AcceptStatus.TRANSIENT_FAILURE);
                return;
            }
            if (result.status() == AcceptStatus.NO_PROFILE) {
                player.sendMessage("§cHãy tạo nhân vật trước khi nhận nhiệm vụ.");
            } else if (result.status() == AcceptStatus.ALREADY_STARTED) {
                if (completion == null) player.sendMessage("§eBạn đã nhận nhiệm vụ này hoặc đã hoàn thành nó.");
            } else if (result.status() == AcceptStatus.ACTIVE_LIMIT_REACHED) {
                player.sendMessage("§cBạn chỉ có thể nhận tối đa 3 nhiệm vụ cùng lúc. Hãy hoàn thành hoặc hủy một nhiệm vụ trước.");
            } else if (result.status() == AcceptStatus.REQUIREMENTS_NOT_MET) {
                player.sendMessage("§cBạn chưa đáp ứng điều kiện để nhận quest này.");
            } else if (result.status() == AcceptStatus.DAILY_LIMIT_REACHED) {
                player.sendMessage("§cBạn đã đạt giới hạn số lần lặp quest hôm nay.");
            } else {
                activeByPlayer.put(playerId, result.progress());
                completedByPlayer.computeIfPresent(playerId, (ignored, completed) -> {
                    java.util.Set<String> updatedCompleted = ConcurrentHashMap.newKeySet();
                    updatedCompleted.addAll(completed);
                    updatedCompleted.remove(questId);
                    return updatedCompleted;
                });
                debugQuest("accept-result player=" + player.getName() + ", quest=" + questId
                    + ", status=accepted, activeCount=" + result.progress().size());
                player.sendMessage("§aĐã nhận nhiệm vụ: §f" + quest.title() + " §7(0/" + quest.target() + ")");
                player.sendMessage("§7Phần thưởng: §e" + quest.vncoinReward() + " VNCOIN");
                refreshBoard(player);
            }
            if (completion != null) completion.accept(result.status());
        }));
    }

    private int countExactItems(Player player, ItemStack template) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && !item.getType().isAir() && item.isSimilar(template)) {
                count = Math.min(100_000, count + item.getAmount());
            }
        }
        return count;
    }

    public List<NpcDefinitionLoader.NpcAction> getNpcQuestActions(Player player, String npcId) {
        Map<String, PlayerProfileDatabaseManager.QuestProgress> active = activeByPlayer
            .getOrDefault(player.getUniqueId(), Map.of());
        java.util.Set<String> completed = completedByPlayer.getOrDefault(player.getUniqueId(), java.util.Set.of());
        return definitions.values().stream()
            .filter(quest -> quest.enabled() && quest.giverNpcId().equals(npcId))
            .sorted(java.util.Comparator.comparing(QuestDefinition::id))
            .map(quest -> {
                String state = active.containsKey(quest.id()) ? "§e[Đang làm] "
                    : completed.contains(quest.id()) && !isRepeatable(quest.id()) ? "§a[Đã xong] "
                    : completed.contains(quest.id()) ? "§d[Lặp lại] "
                    : canDisplayQuest(player, quest) && active.size() < 3 ? "§a[Có thể nhận] " : "§c[Chưa đủ điều kiện] ";
                String title = quest.title().length() > 26 ? quest.title().substring(0, 26) : quest.title();
                return new NpcDefinitionLoader.NpcAction("quest-interaction", state + title, null, quest.id());
            }).toList();
    }

    public void handleNpcQuestInteraction(Player player, String npcId, String questId) {
        QuestDefinition quest = definitions.get(questId);
        if (quest == null || !quest.enabled() || !quest.giverNpcId().equals(npcId)) {
            player.sendMessage("§cNhiệm vụ này không khả dụng tại NPC hiện tại.");
            return;
        }
        UUID playerId = player.getUniqueId();
        Map<String, PlayerProfileDatabaseManager.QuestProgress> active = activeByPlayer
            .getOrDefault(playerId, Map.of());
        java.util.Set<String> completed = completedByPlayer.getOrDefault(playerId, java.util.Set.of());
        QuestDialogRefs refs = dialogRefs(quest.id());
        String speaker = plugin.getNpcManager() == null ? quest.title()
            : plugin.getNpcManager().getNpcDisplayName(npcId).orElse(quest.title());
        if (completed.contains(questId) && !isRepeatable(questId)) {
            plugin.getQuestDialogManager().openQuestDialogue(player, refs.completed(), speaker,
                "Nhiệm vụ này đã hoàn thành. Phần thưởng đã được trao cho bạn.", null);
            return;
        }
        if (active.containsKey(questId)) {
            PlayerProfileDatabaseManager.QuestProgress progress = active.get(questId);
            int step = currentObjectiveIndex(progress, quest);
            Runnable followUp = quest.objectives().get(step).type() == ObjectiveType.SUBMIT_EXACT_ITEM
                ? () -> openNpcQuestSubmit(player, npcId, questId) : null;
            plugin.getQuestDialogManager().openQuestDialogue(player, refs.inProgress(), speaker,
                followUp == null ? "Hãy tiếp tục mục tiêu của nhiệm vụ."
                    : "Nếu đã có vật phẩm yêu cầu, ta có thể nhận chúng.", followUp);
            return;
        }
        if (active.size() < 3 && canDisplayQuest(player, quest)) {
            plugin.getQuestDialogManager().openQuestDialogue(player, refs.available(), speaker,
                "Ta có một nhiệm vụ cần bạn giúp.",
                () -> openNpcQuestOffer(player, npcId, questId));
        } else {
            plugin.getQuestDialogManager().openQuestDialogue(player, refs.unavailable(), speaker,
                "Bạn chưa đáp ứng điều kiện nhận nhiệm vụ này hoặc đã nhận đủ số nhiệm vụ đang làm.", null);
        }
    }

    public void recordNpcVisit(Player player, String npcId) {
        if (npcId == null || npcId.isBlank()) return;
        Map<String, PlayerProfileDatabaseManager.QuestProgress> active = activeByPlayer
            .getOrDefault(player.getUniqueId(), Map.of());
        for (String questId : List.copyOf(active.keySet())) {
            QuestDefinition quest = definitions.get(questId);
            if (quest == null) continue;
            int step = currentObjectiveIndex(active.get(questId), quest);
            QuestObjective objective = quest.objectives().get(step);
            if (objective.type() == ObjectiveType.VISIT_NPC && objective.itemId().equalsIgnoreCase(npcId)) {
                debugQuest("npc-visit-matched player=" + player.getName() + ", quest=" + questId
                    + ", npc=" + npcId);
                increment(player, quest, step, 1);
            }
        }
    }

    private void openNpcQuestOffer(Player player, String npcId, String questId) {
        QuestDefinition quest = definitions.get(questId);
        Map<String, PlayerProfileDatabaseManager.QuestProgress> active =
            activeByPlayer.getOrDefault(player.getUniqueId(), Map.of());
        if (quest == null || !quest.enabled() || !quest.giverNpcId().equals(npcId)
                || active.containsKey(questId)
                || !isRepeatable(questId)
                    && completedByPlayer.getOrDefault(player.getUniqueId(), java.util.Set.of()).contains(questId)
                || active.size() >= 3 || !canDisplayQuest(player, quest)) {
            player.sendMessage("§cQuest không còn đủ điều kiện nhận; hãy nói chuyện lại với NPC.");
            return;
        }
        QuestMenuHolder holder = new QuestMenuHolder(player.getUniqueId(), QuestMenu.NPC_ACCEPT, questId, 0);
        holder.npcId = npcId;
        Inventory menu = Bukkit.createInventory(holder, 54, "Nhận nhiệm vụ • " + trimTitle(quest.title()));
        holder.inventory = menu;
        for (int slot = 0; slot < menu.getSize(); slot++)
            menu.setItem(slot, adminItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        List<String> details = new java.util.ArrayList<>();
        details.add("§7NPC giao: §f" + npcId);
        details.add("§7Mục tiêu:");
        for (int index = 0; index < quest.objectives().size(); index++) {
            QuestObjective objective = quest.objectives().get(index);
            details.add("§f" + (index + 1) + ". " + objectiveDescription(objective)
                + " × " + objective.amount());
        }
        details.add("§7Thưởng: §e" + quest.vncoinReward() + " VNCOIN §7| §b" + quest.experienceReward() + " EXP");
        details.addAll(rewardLore(quest));
        menu.setItem(13, adminItem(Material.WRITTEN_BOOK, "§6" + quest.title(), details));
        menu.setItem(11, adminItem(Material.LIME_DYE, "§aNhận nhiệm vụ", List.of(
            "§7Nhiệm vụ sẽ được thêm vào nhật ký của bạn.")));
        menu.setItem(15, adminItem(Material.RED_DYE, "§cĐể sau", List.of("§7Chưa nhận nhiệm vụ này.")));
        player.openInventory(menu);
    }

    private void openNpcQuestSubmit(Player player, String npcId, String questId) {
        QuestDefinition quest = definitions.get(questId);
        PlayerProfileDatabaseManager.QuestProgress progress = activeByPlayer
            .getOrDefault(player.getUniqueId(), Map.of()).get(questId);
        if (quest == null || progress == null || !quest.giverNpcId().equals(npcId)) return;
        int step = currentObjectiveIndex(progress, quest);
        QuestObjective objective = quest.objectives().get(step);
        QuestMenuHolder holder = new QuestMenuHolder(player.getUniqueId(), QuestMenu.NPC_SUBMIT, questId, 0);
        holder.npcId = npcId;
        Inventory menu = Bukkit.createInventory(holder, 54, "Nộp vật phẩm nhiệm vụ");
        holder.inventory = menu;
        for (int slot = 0; slot < menu.getSize(); slot++)
            menu.setItem(slot, adminItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        menu.setItem(13, adminItem(Material.PAPER, "§e" + objectiveDescription(objective), List.of(
            "§7Cần nộp: §f" + Math.max(0, objective.amount() - progress.objectiveProgress()),
            "§7Vật phẩm sẽ bị lấy khỏi túi khi xác nhận.")));
        menu.setItem(11, adminItem(Material.LIME_DYE, "§aNộp vật phẩm", List.of()));
        menu.setItem(15, adminItem(Material.RED_DYE, "§cĐể sau", List.of()));
        player.openInventory(menu);
    }

    private static String trimTitle(String title) {
        return title.length() <= 24 ? title : title.substring(0, 24);
    }

    public void submitQuestItems(Player player, String npcId, String questId) {
        QuestDefinition quest = definitions.get(questId);
        PlayerProfileDatabaseManager.QuestProgress progress = activeByPlayer
            .getOrDefault(player.getUniqueId(), Map.of()).get(questId);
        if (quest == null || progress == null || !quest.giverNpcId().equals(npcId)) {
            player.sendMessage("§cNhiệm vụ nộp vật phẩm không còn khả dụng tại NPC này.");
            return;
        }
        int step = currentObjectiveIndex(progress, quest);
        QuestObjective objective = quest.objectives().get(step);
        if (objective.type() != ObjectiveType.SUBMIT_EXACT_ITEM) {
            player.sendMessage("§cNhiệm vụ hiện tại không yêu cầu nộp vật phẩm.");
            return;
        }
        int remaining = objective.amount() - progress.objectiveProgress();
        int available = countExactItems(player, objective.requiredItem());
        int required = Math.min(remaining, available);
        if (required <= 0) {
            player.sendMessage("§cBạn chưa có vật phẩm cần nộp: §f" + objectiveDescription(objective));
            return;
        }
        if (!consumeExactItems(player, objective.requiredItem(), required)) {
            player.sendMessage("§cKhông thể lấy đủ vật phẩm khỏi túi đồ; chưa có vật phẩm nào được nộp.");
            return;
        }
        player.sendMessage("§aĐã nộp " + required + " vật phẩm cho nhiệm vụ.");
        increment(player, quest, step, required, objective.requiredItem(), required);
    }

    public void onSystemBossDefeated(String bossId, UUID encounterId, java.util.Set<UUID> contributorIds) {
        if (bossId == null || encounterId == null || contributorIds == null || contributorIds.isEmpty()
                || !creditedBossEncounters.add(encounterId)) return;
        if (creditedBossEncounters.size() > 4096) {
            UUID oldest = creditedBossEncounters.stream().findFirst().orElse(null);
            if (oldest != null && !oldest.equals(encounterId)) creditedBossEncounters.remove(oldest);
        }
        for (UUID contributorId : contributorIds) {
            Player player = Bukkit.getPlayer(contributorId);
            if (player == null || !player.isOnline()) continue;
            Map<String, PlayerProfileDatabaseManager.QuestProgress> active = activeByPlayer
                .getOrDefault(contributorId, Map.of());
            for (String questId : List.copyOf(active.keySet())) {
                QuestDefinition quest = definitions.get(questId);
                if (quest == null) continue;
                int step = currentObjectiveIndex(active.get(questId), quest);
                QuestObjective objective = quest.objectives().get(step);
                if (objective.type() == ObjectiveType.KILL_BOSS && objective.itemId().equals(bossId)) {
                    debugQuest("boss-kill-matched player=" + player.getName() + ", quest=" + quest.id()
                        + ", boss=" + bossId + ", contribution=true");
                    increment(player, quest, step, 1);
                }
            }
        }
    }

    private boolean consumeExactItems(Player player, ItemStack template, int amount) {
        if (amount < 1 || countExactItems(player, template) < amount) return false;
        int remaining = amount;
        for (int slot = 0; slot < player.getInventory().getStorageContents().length && remaining > 0; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || item.getType().isAir() || !item.isSimilar(template)) continue;
            int consumed = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - consumed);
            if (item.getAmount() <= 0) player.getInventory().setItem(slot, null);
            remaining -= consumed;
        }
        return remaining == 0;
    }

    private void restoreExactItems(Player player, ItemStack template, int amount) {
        if (template == null || amount < 1) return;
        int remaining = amount;
        int stackSize = Math.max(1, template.getMaxStackSize());
        if (!player.isOnline()) {
            List<byte[]> serialized = new java.util.ArrayList<>();
            while (remaining > 0) {
                ItemStack stack = template.clone();
                int give = Math.min(remaining, stackSize);
                stack.setAmount(give);
                serialized.add(stack.serializeAsBytes());
                remaining -= give;
            }
            String prefix = "quest-submit-refund:" + player.getUniqueId() + ":" + UUID.randomUUID();
            PlayerProfileDatabaseManager database = plugin.getPlayerProfileDatabaseManager();
            database.executeGameplayDatabaseTask("quest.submit-refund player=" + player.getName(),
                    () -> {
                        database.enqueueMerchantDeliveries(player.getUniqueId().toString(), prefix,
                            serialized, Instant.now().toString());
                        return null;
                    })
                .whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        plugin.getLogger().log(Level.SEVERE,
                            "[Quest] Could not persist an offline item refund for " + player.getName(), failure);
                    } else if (plugin.getEconomyManager() != null) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (player.isOnline()) plugin.getEconomyManager().reconcileMerchantDeliveries(player);
                        });
                    }
                });
            return;
        }
        while (remaining > 0) {
            ItemStack stack = template.clone();
            int give = Math.min(remaining, stackSize);
            stack.setAmount(give);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            remaining -= give;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        PlayerProfileDatabaseManager database = plugin.getPlayerProfileDatabaseManager();
        database.executeGameplayDatabaseTask("quest.load-active player=" + player.getName(),
                () -> new QuestJoinData(database.getActiveQuestProgress(playerId.toString()),
                    database.getCompletedQuestIds(playerId.toString())))
            .whenComplete((data, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (failure != null) {
                    plugin.getLogger().log(Level.SEVERE, "[Quest] Failed loading active quests for "
                        + player.getName(), failure);
                } else if (player.isOnline()) {
                    activeByPlayer.put(playerId, data.active());
                    completedByPlayer.put(playerId, data.completed());
                    questStateLoaded.add(playerId);
                    debugQuest("load-active player=" + player.getName() + ", count=" + data.active().size()
                        + ", completed=" + data.completed().size());
                    refreshBoard(player);
                    processPendingQuestSuccessors(player);
                }
            }));
    }

    private void processPendingQuestSuccessors(Player player) {
        UUID playerId = player.getUniqueId();
        if (!processingPendingSuccessors.add(playerId)) return;
        PlayerProfileDatabaseManager database = plugin.getPlayerProfileDatabaseManager();
        database.executeGameplayDatabaseTask("quest.successor-load player=" + player.getName(),
                () -> database.getPendingQuestSuccessors(playerId.toString()))
            .whenComplete((pending, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (failure != null) {
                    processingPendingSuccessors.remove(playerId);
                    plugin.getLogger().log(Level.SEVERE, "[Quest][Successor] Failed loading pending successors for "
                        + player.getName(), failure);
                    return;
                }
                if (!player.isOnline()) {
                    processingPendingSuccessors.remove(playerId);
                    return;
                }
                debugQuest("successor-pending-loaded player=" + player.getName() + ", count=" + pending.size());
                processPendingQuestSuccessorAt(player, pending, 0);
            }));
    }

    private void processPendingQuestSuccessorAt(Player player,
            List<PlayerProfileDatabaseManager.PendingQuestSuccessor> pending, int index) {
        UUID playerId = player.getUniqueId();
        if (!player.isOnline() || index >= pending.size()) {
            processingPendingSuccessors.remove(playerId);
            return;
        }
        PlayerProfileDatabaseManager.PendingQuestSuccessor link = pending.get(index);
        QuestDefinition successor = definitions.get(link.successorQuestId());
        if (successor == null || !successor.enabled()) {
            plugin.getLogger().warning("[Quest][Successor] Pending quest remains unresolved: player="
                + player.getName() + ", source=" + link.sourceQuestId() + ", successor="
                + link.successorQuestId() + ", reason=" + (successor == null ? "definition-missing" : "disabled"));
            debugQuest("successor-held player=" + player.getName() + ", source=" + link.sourceQuestId()
                + ", successor=" + link.successorQuestId() + ", reason=missing-or-disabled");
            processPendingQuestSuccessorAt(player, pending, index + 1);
            return;
        }
        debugQuest("successor-start-attempt player=" + player.getName() + ", source=" + link.sourceQuestId()
            + ", successor=" + successor.id());
        acceptQuest(player, successor.giverNpcId(), successor.id(), status -> {
            if (status != AcceptStatus.ACCEPTED && status != AcceptStatus.ALREADY_STARTED) {
                debugQuest("successor-retained player=" + player.getName() + ", source="
                    + link.sourceQuestId() + ", successor=" + successor.id() + ", reason=" + status);
                processPendingQuestSuccessorAt(player, pending, index + 1);
                return;
            }
            PlayerProfileDatabaseManager database = plugin.getPlayerProfileDatabaseManager();
            database.executeGameplayDatabaseTask("quest.successor-clear player=" + player.getName()
                    + " quest=" + successor.id(), () -> database.removePendingQuestSuccessor(
                        playerId.toString(), link.sourceQuestId(), successor.id()))
                .whenComplete((removed, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (failure != null || !Boolean.TRUE.equals(removed)) {
                        plugin.getLogger().log(Level.SEVERE, "[Quest][Successor] Could not clear pending link: "
                            + "player=" + player.getName() + ", source=" + link.sourceQuestId()
                            + ", successor=" + successor.id(), failure);
                    } else {
                        debugQuest("successor-cleared player=" + player.getName() + ", source="
                            + link.sourceQuestId() + ", successor=" + successor.id() + ", status=" + status);
                    }
                    processPendingQuestSuccessorAt(player, pending, index + 1);
                }));
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        QuestWaypointRenderer.forget(event.getPlayer().getUniqueId());
        activeByPlayer.remove(event.getPlayer().getUniqueId());
        completedByPlayer.remove(event.getPlayer().getUniqueId());
        questStateLoaded.remove(event.getPlayer().getUniqueId());
        pendingTextInputs.remove(event.getPlayer().getUniqueId());
        pendingRequirementItems.remove(event.getPlayer().getUniqueId());
        pendingObjectiveItems.remove(event.getPlayer().getUniqueId());
        lastCrossWorldHint.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player player = event.getEntity().getKiller();
        if (player == null) return;
        Map<String, PlayerProfileDatabaseManager.QuestProgress> active = activeByPlayer.get(player.getUniqueId());
        for (QuestDefinition quest : definitions.values()) {
            if (active == null || !active.containsKey(quest.id())) continue;
            int objectiveIndex = currentObjectiveIndex(active.get(quest.id()), quest);
            QuestObjective objective = quest.objectives().get(objectiveIndex);
            if (objective.type() == ObjectiveType.KILL_MOB && objective.entity() == event.getEntityType()) {
                debugQuest("kill-matched player=" + player.getName() + ", quest=" + quest.id()
                    + ", entity=" + event.getEntityType() + ", entityId=" + event.getEntity().getUniqueId());
                increment(player, quest, objectiveIndex, 1);
            } else if (objective.type() == ObjectiveType.COLLECT_RPG_ITEM
                    && objective.entity() == event.getEntityType()
                    && ThreadLocalRandom.current().nextInt(100) < objective.dropChancePercent()) {
                dropQuestItem(event.getEntity().getLocation(), quest);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuestItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack stack = event.getItem().getItemStack();
        String itemId = stack.hasItemMeta() ? stack.getItemMeta().getPersistentDataContainer()
            .get(questItemKey, PersistentDataType.STRING) : null;
        int pickedAmount = Math.max(0, stack.getAmount() - event.getRemaining());
        if (pickedAmount == 0) return;
        ItemStack pickedOne = stack.clone();
        pickedOne.setAmount(1);
        Map<String, PlayerProfileDatabaseManager.QuestProgress> active = activeByPlayer.get(player.getUniqueId());
        if (active == null) return;
        for (String questId : List.copyOf(active.keySet())) {
            QuestDefinition quest = definitions.get(questId);
            if (quest != null) {
                int objectiveIndex = currentObjectiveIndex(active.get(questId), quest);
                QuestObjective objective = quest.objectives().get(objectiveIndex);
                if (objective.type() == ObjectiveType.COLLECT_RPG_ITEM && objective.itemId().equals(itemId)
                        || objective.type() == ObjectiveType.COLLECT_EXACT_ITEM
                            && objective.requiredItem().isSimilar(pickedOne)) {
                    increment(player, quest, objectiveIndex, pickedAmount);
                }
            }
        }
    }

    private int currentObjectiveIndex(PlayerProfileDatabaseManager.QuestProgress progress, QuestDefinition quest) {
        if (progress == null) return 0;
        try {
            int index = Integer.parseInt(progress.currentStep());
            return Math.max(0, Math.min(index, quest.objectives().size() - 1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void dropQuestItem(Location location, QuestDefinition quest) {
        ItemStack stack = createQuestItem(quest);
        if (stack == null) return;
        var dropped = location.getWorld().dropItemNaturally(location, stack);
        dropped.setCustomName("§6" + quest.itemName());
        dropped.setCustomNameVisible(true);
        debugQuest("item-drop quest=" + quest.id() + ", item=" + quest.itemId() + ", sell-value="
            + questItemSellValues.getOrDefault(quest.id(), 0L)
            + ", entity=" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
    }

    private ItemStack createQuestItem(QuestDefinition quest) {
        return createQuestItem(quest.itemId(), quest.itemMaterial(), quest.itemName(),
            questItemSellValues.getOrDefault(quest.id(), 0L));
    }

    private ItemStack createQuestItem(String itemId, Material material, String itemName, long sellValue) {
        if (itemId == null || material == null || material.isAir()) return null;
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return null;
        meta.setDisplayName("§6" + itemName);
        meta.setLore(List.of("§7Nguyên liệu nhiệm vụ RPG", "§8Mã: " + itemId));
        meta.getPersistentDataContainer().set(questItemKey, PersistentDataType.STRING, itemId);
        meta.getPersistentDataContainer().set(rpgSellValueKey, PersistentDataType.LONG, sellValue);
        stack.setItemMeta(meta);
        return stack;
    }

    private void increment(Player player, QuestDefinition quest, int objectiveIndex, int amount) {
        increment(player, quest, objectiveIndex, amount, null, 0);
    }

    private void increment(Player player, QuestDefinition quest, int objectiveIndex, int amount,
            ItemStack consumedItem, int consumedAmount) {
        UUID playerId = player.getUniqueId();
        QuestObjective objective = quest.objectives().get(objectiveIndex);
        String expectedStep = Integer.toString(objectiveIndex);
        PlayerProfileDatabaseManager database = plugin.getPlayerProfileDatabaseManager();
        database.executeGameplayDatabaseTask("quest.kill-progress player=" + player.getName()
                + " quest=" + quest.id(), () -> database.incrementQuestObjectiveProgress(
                    playerId.toString(), quest.id(), expectedStep, Math.min(1000, amount), objective.amount(),
                    Instant.now().toString()))
            .thenCompose(result -> {
                if (!result.active() || result.progress() < objective.amount())
                    return java.util.concurrent.CompletableFuture.completedFuture(new ProgressResult(result, false, false));
                if (objectiveIndex + 1 < quest.objectives().size()) {
                    String nextStep = Integer.toString(objectiveIndex + 1);
                    return database.executeGameplayDatabaseTask("quest.advance player=" + player.getName()
                            + " quest=" + quest.id(), () -> database.advanceQuestObjective(playerId.toString(),
                                quest.id(), expectedStep, nextStep, Instant.now().toString()))
                        .thenApply(advanced -> new ProgressResult(result, false, advanced));
                }
                List<Long> rewardSellValues = questRewardSellValues.getOrDefault(quest.id(),
                    zeroSellValues(quest.itemRewards().size()));
                List<byte[]> serializedItems = new java.util.ArrayList<>(quest.itemRewards().size());
                for (int rewardIndex = 0; rewardIndex < quest.itemRewards().size(); rewardIndex++) {
                    ItemStack reward = quest.itemRewards().get(rewardIndex).clone();
                    ItemMeta rewardMeta = reward.getItemMeta();
                    if (rewardMeta != null) {
                        rewardMeta.getPersistentDataContainer().set(rpgSellValueKey, PersistentDataType.LONG,
                            rewardSellValues.get(rewardIndex));
                        reward.setItemMeta(rewardMeta);
                    }
                    serializedItems.add(reward.serializeAsBytes());
                }
                debugQuest("reward-item-prices quest=" + quest.id() + ", stacks=" + serializedItems.size()
                    + ", values=" + rewardSellValues);
                return database.executeGameplayDatabaseTask("quest.complete player=" + player.getName()
                        + " quest=" + quest.id(), () -> database.completeQuestAndAwardRewards(
                            playerId.toString(), quest.id(), "complete", quest.experienceReward(),
                            quest.vncoinReward(), serializedItems, quest.successorQuestId(), Instant.now().toString(),
                            isRepeatable(quest.id()), repeatDailyLimits.getOrDefault(quest.id(), 0),
                            java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString(),
                            activeByPlayer.getOrDefault(playerId, Map.of()).get(quest.id()) == null
                                ? null : activeByPlayer.get(playerId).get(quest.id()).startedAt()))
                    .thenApply(completed -> new ProgressResult(result, completed, false));
            }).whenComplete((result, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (failure != null) {
                    plugin.getLogger().log(Level.SEVERE, "[Quest] Failed updating progress for " + quest.id(), failure);
                    if (consumedItem != null) restoreExactItems(player, consumedItem, consumedAmount);
                    return;
                }
                if (!player.isOnline()) return;
                if (!result.progress().active()) {
                    if (consumedItem != null) restoreExactItems(player, consumedItem, consumedAmount);
                    return;
                }
                debugQuest("progress-result player=" + player.getName() + ", quest=" + quest.id()
                    + ", step=" + result.progress().currentStep() + ", progress=" + result.progress().progress()
                    + "/" + objective.amount() + ", completed=" + result.completed()
                    + ", advanced=" + result.advanced() + ", tracked=" + result.progress().tracked());
                Map<String, PlayerProfileDatabaseManager.QuestProgress> updated =
                    new LinkedHashMap<>(activeByPlayer.getOrDefault(playerId, Map.of()));
                boolean finalObjectiveReached = objectiveIndex == quest.objectives().size() - 1
                    && result.progress().progress() >= objective.amount();
                if (consumedItem != null && finalObjectiveReached && !result.completed()) {
                    restoreExactItems(player, consumedItem, consumedAmount);
                    return;
                }
                if (result.completed() || finalObjectiveReached) {
                    updated.remove(quest.id());
                    completedByPlayer.computeIfAbsent(playerId, ignored -> ConcurrentHashMap.newKeySet())
                        .add(quest.id());
                    if (result.completed()) {
                        player.sendTitle("§6§lNHIỆM VỤ HOÀN THÀNH",
                            "§e" + quest.title() + " §8• §b+" + quest.experienceReward()
                                + " EXP §8• §6+" + quest.vncoinReward() + " VNCOIN",
                            10, 70, 20);
                        player.sendMessage("§6Hoàn thành nhiệm vụ: §f" + quest.title()
                            + " §7(thưởng " + quest.vncoinReward() + " VNCOIN, "
                            + quest.experienceReward() + " EXP)");
                        if (!quest.itemRewards().isEmpty())
                            plugin.getEconomyManager().reconcileMerchantDeliveries(player);
                        String npcName = plugin.getNpcManager() == null ? quest.giverNpcId()
                            : plugin.getNpcManager().getNpcDisplayName(quest.giverNpcId()).orElse(quest.giverNpcId());
                        if (consumedItem != null) {
                            plugin.getQuestDialogManager().openQuestDialogue(player,
                                dialogRefs(quest.id()).completed(), npcName,
                                "Nhiệm vụ đã hoàn thành. Phần thưởng đã được trao cho bạn.", null);
                        }
                    }
                } else if (result.advanced()) {
                    int nextIndex = objectiveIndex + 1;
                    updated.put(quest.id(), new PlayerProfileDatabaseManager.QuestProgress(
                        "active", Integer.toString(nextIndex), "", "", false, 0, result.progress().tracked()));
                    player.sendMessage("§aHoàn thành bước " + (objectiveIndex + 1) + "/" + quest.objectives().size()
                        + ": " + objectiveDescription(objective));
                } else {
                    updated.put(quest.id(), new PlayerProfileDatabaseManager.QuestProgress(
                        "active", expectedStep, "", "", false, result.progress().progress(), result.progress().tracked()));
                    player.sendMessage("§aTiến độ " + quest.title() + ": §f" + result.progress().progress()
                        + "/" + objective.amount());
                }
                activeByPlayer.put(playerId, Map.copyOf(updated));
                refreshBoard(player);
                if (result.completed()) {
                    if (quest.successorQuestId() != null) {
                        debugQuest("successor-enqueued player=" + player.getName() + ", source=" + quest.id()
                            + ", successor=" + quest.successorQuestId());
                    }
                    processPendingQuestSuccessors(player);
                }
            }));
    }

    public List<String> getQuestSidebarLines(Player player) {
        Map<String, PlayerProfileDatabaseManager.QuestProgress> active = activeByPlayer.get(player.getUniqueId());
        if (active == null || active.isEmpty()) return List.of();
        List<Map.Entry<String, PlayerProfileDatabaseManager.QuestProgress>> quests = active.entrySet().stream()
            .filter(entry -> definitions.containsKey(entry.getKey()))
            .sorted(java.util.Comparator.comparing(entry -> !entry.getValue().tracked()))
            .limit(3).toList();
        if (quests.isEmpty()) return List.of();
        List<String> lines = new java.util.ArrayList<>();
        lines.add("§b§l    NHIỆM VỤ");
        for (Map.Entry<String, PlayerProfileDatabaseManager.QuestProgress> entry : quests) {
            QuestDefinition quest = definitions.get(entry.getKey());
            int step = currentObjectiveIndex(entry.getValue(), quest);
            QuestObjective objective = quest.objectives().get(step);
            // Legacy scoreboard rows are strings and cannot carry clickable component events.
            lines.add("§f§n" + shortSidebarText(quest.title(), 22) + "§r§f: "
                + (step + 1) + "/" + quest.objectives().size());
            lines.add("§7" + shortSidebarText(objectiveDescription(objective), 28) + ": "
                + entry.getValue().objectiveProgress() + "/" + objective.amount());
        }
        return List.copyOf(lines);
    }

    private String shortSidebarText(String value, int maxLength) {
        String plain = org.bukkit.ChatColor.stripColor(value);
        if (plain == null || plain.length() <= maxLength) return value;
        return plain.substring(0, Math.max(1, maxLength - 1)) + "…";
    }

    private void refreshBoard(Player player) {
        BossLeaderboardManager board = plugin.getBossLeaderboardManager();
        if (board != null) board.refreshPlayerBoard(player);
    }

    private boolean canDisplayQuest(Player player, QuestDefinition quest) {
        var profile = plugin.getCharacterSelectionManager().getLoadedProfile(player.getUniqueId());
        java.util.Set<String> completed = completedByPlayer.getOrDefault(player.getUniqueId(), java.util.Set.of());
        for (QuestRequirement requirement : quest.requirements()) {
            switch (requirement.type()) {
                case LEVEL -> {
                    if (profile.isEmpty() || profile.get().level() < Integer.parseInt(requirement.value())) return false;
                }
                case COMPLETED_QUEST -> {
                    if (!completed.contains(requirement.value())) return false;
                }
                case EXACT_ITEM -> {
                    if (countExactItems(player, requirement.item()) < requirement.amount()) return false;
                }
            }
        }
        return true;
    }

    private boolean isRepeatable(String questId) {
        return repeatableQuests.getOrDefault(questId, false);
    }

    public boolean hasAvailableQuestForMarker(Player player, String npcId) {
        if (player == null || npcId == null || plugin.getCharacterSelectionManager() == null
                || plugin.getCharacterSelectionManager().getLoadedProfile(player.getUniqueId()).isEmpty()
                || !questStateLoaded.contains(player.getUniqueId())) return false;
        Map<String, PlayerProfileDatabaseManager.QuestProgress> active = activeByPlayer
            .getOrDefault(player.getUniqueId(), Map.of());
        if (active.size() >= 3) return false;
        java.util.Set<String> completed = completedByPlayer.getOrDefault(player.getUniqueId(), java.util.Set.of());
        return definitions.values().stream().anyMatch(quest -> quest.enabled()
            && quest.giverNpcId().equalsIgnoreCase(npcId)
            && !active.containsKey(quest.id()) && (isRepeatable(quest.id()) || !completed.contains(quest.id()))
            && canDisplayQuest(player, quest));
    }

    private void debugQuest(String detail) {
        if (plugin.getConfig().getBoolean("logging.console-debug", false)) {
            plugin.getLogger().info("[RPG-DEBUG][QuestCatalog] " + detail);
        }
    }

    private void updateTrackedQuestWaypoints() {
        boolean particlesEnabled = plugin.getConfig().getBoolean("rpg.quests.tracking.particle-enabled", true);
        for (Player player : Bukkit.getOnlinePlayers()) {
            Map<String, PlayerProfileDatabaseManager.QuestProgress> active =
                activeByPlayer.getOrDefault(player.getUniqueId(), Map.of());
            Map.Entry<String, PlayerProfileDatabaseManager.QuestProgress> tracked = active.entrySet().stream()
                .filter(entry -> entry.getValue().tracked() && definitions.containsKey(entry.getKey()))
                .findFirst().orElse(null);
            if (tracked == null) {
                clearWaypointHint(player);
                continue;
            }
            QuestDefinition quest = definitions.get(tracked.getKey());
            int objectiveIndex = currentObjectiveIndex(tracked.getValue(), quest);
            QuestObjective objective = quest.objectives().get(objectiveIndex);
            String targetNpcId = switch (objective.type()) {
                case VISIT_NPC -> objective.itemId();
                case SUBMIT_EXACT_ITEM -> quest.giverNpcId();
                default -> null;
            };
            if (targetNpcId == null || plugin.getNpcManager() == null) {
                QuestWaypointRenderer.forget(player.getUniqueId());
                clearWaypointHint(player);
                continue;
            }
            var target = plugin.getNpcManager().getNpcTarget(targetNpcId);
            if (target.isEmpty()) {
                QuestWaypointRenderer.forget(player.getUniqueId());
                clearWaypointHint(player);
                continue;
            }
            Location destination = target.get().location();
            if (destination == null || destination.getWorld() != player.getWorld()) {
                sendTrackingHint(player, "§eĐi tới thế giới §f" + target.get().world()
                    + " §eđể gặp NPC mục tiêu.");
                continue;
            }
            if (!particlesEnabled || !areQuestWaypointParticlesEnabled(player)) {
                sendTrackingHint(player, "§eNPC nhiệm vụ tại §fX " + destination.getBlockX() + " Y "
                    + destination.getBlockY() + " Z " + destination.getBlockZ());
                continue;
            }
            if (!QuestWaypointRenderer.render(player, destination, TRACKING_PARTICLE_RANGE)) {
                sendTrackingHint(player, "§eKhông tìm được đường đi bộ tới NPC; vị trí: §fX "
                    + destination.getBlockX() + " Y " + destination.getBlockY() + " Z "
                    + destination.getBlockZ());
            }
        }
    }

    private boolean areQuestWaypointParticlesEnabled(Player player) {
        Byte stored = player.getPersistentDataContainer().get(waypointParticlesKey, PersistentDataType.BYTE);
        return stored == null || stored != 0;
    }

    private void toggleWaypointParticles(Player player, Runnable reopen) {
        boolean enabled = !areQuestWaypointParticlesEnabled(player);
        player.getPersistentDataContainer().set(waypointParticlesKey, PersistentDataType.BYTE,
            enabled ? (byte) 1 : (byte) 0);
        player.saveData();
        if (!enabled) {
            QuestWaypointRenderer.forget(player.getUniqueId());
            player.sendActionBar(" ");
        }
        player.sendMessage(enabled ? "§aĐã bật mũi tên particle dẫn đường quest."
            : "§eĐã tắt mũi tên particle dẫn đường quest.");
        reopen.run();
    }

    private void toggleQuestTracking(Player player, String questId, Runnable reopen) {
        UUID playerId = player.getUniqueId();
        PlayerProfileDatabaseManager.QuestProgress progress = activeByPlayer
            .getOrDefault(playerId, Map.of()).get(questId);
        if (progress == null) {
            reopen.run();
            return;
        }
        boolean wasTracked = progress.tracked();
        PlayerProfileDatabaseManager database = plugin.getPlayerProfileDatabaseManager();
        database.executeGameplayDatabaseTask("quest.journal-track player=" + player.getName()
                + " quest=" + questId + " tracked=" + !wasTracked,
                () -> wasTracked ? database.clearQuestTracking(playerId.toString(), questId)
                    : database.setQuestTracked(playerId.toString(), questId))
            .whenComplete((changed, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (failure != null) {
                    plugin.getLogger().log(Level.SEVERE, "[Quest] Could not update tracked quest "
                        + questId + " for " + player.getName(), failure);
                    if (player.isOnline()) player.sendMessage("§cKhông thể cập nhật quest đang theo dõi.");
                    return;
                }
                if (!player.isOnline()) return;
                if (changed) {
                    Map<String, PlayerProfileDatabaseManager.QuestProgress> updated = new LinkedHashMap<>(
                        activeByPlayer.getOrDefault(playerId, Map.of()));
                    updated.replaceAll((id, value) -> new PlayerProfileDatabaseManager.QuestProgress(
                        value.status(), value.currentStep(), value.startedAt(), value.completedAt(),
                        value.rewardClaimed(), value.objectiveProgress(), !wasTracked && id.equals(questId)));
                    activeByPlayer.put(playerId, Map.copyOf(updated));
                    if (wasTracked) QuestWaypointRenderer.forget(playerId);
                    player.sendMessage(wasTracked ? "§eĐã bỏ theo dõi quest."
                        : "§aĐang theo dõi quest trên scoreboard.");
                    refreshBoard(player);
                }
                reopen.run();
            }));
    }

    private void sendTrackingHint(Player player, String message) {
        long now = System.currentTimeMillis();
        long last = lastCrossWorldHint.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 2_000) return;
        player.sendActionBar(message);
        lastCrossWorldHint.put(player.getUniqueId(), now);
    }

    private void clearWaypointHint(Player player) {
        if (lastCrossWorldHint.remove(player.getUniqueId()) != null) player.sendActionBar(" ");
    }

    private enum ObjectiveType { KILL_MOB, KILL_BOSS, COLLECT_RPG_ITEM, COLLECT_EXACT_ITEM, SUBMIT_EXACT_ITEM, VISIT_NPC }
    private record QuestObjective(ObjectiveType type, EntityType entity, int amount,
                                  String itemId, Material itemMaterial, String itemName,
                                  int dropChancePercent, ItemStack requiredItem) {
        private QuestObjective {
            requiredItem = requiredItem == null ? null : requiredItem.clone();
        }
        @Override public ItemStack requiredItem() {
            return requiredItem == null ? null : requiredItem.clone();
        }
        private QuestObjective(ObjectiveType type, EntityType entity, int amount, String itemId,
                Material itemMaterial, String itemName, int dropChancePercent) {
            this(type, entity, amount, itemId, itemMaterial, itemName, dropChancePercent, null);
        }
    }
    private enum RequirementType { LEVEL, COMPLETED_QUEST, EXACT_ITEM }
    private record QuestRequirement(RequirementType type, String value, int amount, ItemStack item) {
        private QuestRequirement {
            item = item == null ? null : item.clone();
        }

        private String description() {
            return switch (type) {
                case LEVEL -> "Level RPG tối thiểu " + value;
                case COMPLETED_QUEST -> "Đã hoàn thành quest " + value;
                case EXACT_ITEM -> "Có " + amount + " × " + item.getItemMeta().getDisplayName();
            };
        }
    }
    private record QuestDefinition(String id, String giverNpcId, String title, ObjectiveType objectiveType,
                                   EntityType targetEntity, int target, int vncoinReward, String itemId,
                                   Material itemMaterial, String itemName, int dropChancePercent, boolean enabled,
                                   List<QuestRequirement> requirements, int experienceReward,
                                   List<ItemStack> itemRewards, List<QuestObjective> additionalObjectives,
                                   String successorQuestId) {
        private QuestDefinition {
            requirements = List.copyOf(requirements);
            itemRewards = itemRewards.stream().map(ItemStack::clone).toList();
            additionalObjectives = List.copyOf(additionalObjectives);
        }

        private static QuestDefinition create(String id) {
            return new QuestDefinition(id, "hunter", "Quest mới", ObjectiveType.KILL_MOB,
                EntityType.ZOMBIE, 1, 1, "", null, "", 0, false, List.of(), 0, List.of(), List.of(), null);
        }

        private List<QuestObjective> objectives() {
            List<QuestObjective> result = new java.util.ArrayList<>();
            result.add(new QuestObjective(objectiveType, targetEntity, target, itemId, itemMaterial,
                itemName, dropChancePercent, null));
            result.addAll(additionalObjectives);
            return List.copyOf(result);
        }

        private QuestDefinition withTitle(String value) {
            return new QuestDefinition(id, giverNpcId, value, objectiveType, targetEntity, target, vncoinReward,
                itemId, itemMaterial, itemName, dropChancePercent, enabled, requirements, experienceReward, itemRewards,
                additionalObjectives, successorQuestId);
        }

        private QuestDefinition withGiverNpcId(String value) {
            return new QuestDefinition(id, value, title, objectiveType, targetEntity, target, vncoinReward,
                itemId, itemMaterial, itemName, dropChancePercent, enabled, requirements, experienceReward, itemRewards,
                additionalObjectives, successorQuestId);
        }

        private QuestDefinition withEnabled(boolean value) {
            return new QuestDefinition(id, giverNpcId, title, objectiveType, targetEntity, target, vncoinReward,
                itemId, itemMaterial, itemName, dropChancePercent, value, requirements, experienceReward, itemRewards,
                additionalObjectives, successorQuestId);
        }

        private QuestDefinition withRequirements(List<QuestRequirement> value) {
            return new QuestDefinition(id, giverNpcId, title, objectiveType, targetEntity, target, vncoinReward,
                itemId, itemMaterial, itemName, dropChancePercent, enabled, value, experienceReward, itemRewards,
                additionalObjectives, successorQuestId);
        }

        private QuestDefinition withExperienceReward(int value) {
            return new QuestDefinition(id, giverNpcId, title, objectiveType, targetEntity, target, vncoinReward,
                itemId, itemMaterial, itemName, dropChancePercent, enabled, requirements, value, itemRewards,
                additionalObjectives, successorQuestId);
        }

        private QuestDefinition withItemRewards(List<ItemStack> value) {
            return new QuestDefinition(id, giverNpcId, title, objectiveType, targetEntity, target, vncoinReward,
                itemId, itemMaterial, itemName, dropChancePercent, enabled, requirements, experienceReward, value,
                additionalObjectives, successorQuestId);
        }

        private QuestDefinition withWalletReward(int value) {
            return new QuestDefinition(id, giverNpcId, title, objectiveType, targetEntity, target, value,
                itemId, itemMaterial, itemName, dropChancePercent, enabled, requirements, experienceReward, itemRewards,
                additionalObjectives, successorQuestId);
        }

        private QuestDefinition withObjectives(List<QuestObjective> value) {
            QuestObjective first = value.getFirst();
            return new QuestDefinition(id, giverNpcId, title, first.type(), first.entity(), first.amount(),
                vncoinReward, first.itemId(), first.itemMaterial(), first.itemName(), first.dropChancePercent(),
                enabled, requirements, experienceReward, itemRewards, value.subList(1, value.size()), successorQuestId);
        }

        private QuestDefinition withSuccessorQuestId(String value) {
            return new QuestDefinition(id, giverNpcId, title, objectiveType, targetEntity, target, vncoinReward,
                itemId, itemMaterial, itemName, dropChancePercent, enabled, requirements, experienceReward,
                itemRewards, additionalObjectives, value);
        }
    }
    private record ProgressResult(PlayerProfileDatabaseManager.QuestObjectiveProgressResult progress,
                                  boolean completed, boolean advanced) { }
    private record QuestJoinData(Map<String, PlayerProfileDatabaseManager.QuestProgress> active,
                                 java.util.Set<String> completed) { }
    private enum AcceptStatus {
        ACCEPTED, ALREADY_STARTED, ACTIVE_LIMIT_REACHED, DAILY_LIMIT_REACHED, NO_PROFILE,
        REQUIREMENTS_NOT_MET, TRANSIENT_FAILURE
    }
    private record AcceptResult(AcceptStatus status,
                                Map<String, PlayerProfileDatabaseManager.QuestProgress> progress) {
        private static final AcceptResult NO_PROFILE = new AcceptResult(AcceptStatus.NO_PROFILE, Map.of());
        private static final AcceptResult ALREADY_STARTED =
            new AcceptResult(AcceptStatus.ALREADY_STARTED, Map.of());
        private static final AcceptResult ACTIVE_LIMIT_REACHED =
            new AcceptResult(AcceptStatus.ACTIVE_LIMIT_REACHED, Map.of());
        private static final AcceptResult DAILY_LIMIT_REACHED =
            new AcceptResult(AcceptStatus.DAILY_LIMIT_REACHED, Map.of());
        private static final AcceptResult REQUIREMENTS_NOT_MET =
            new AcceptResult(AcceptStatus.REQUIREMENTS_NOT_MET, Map.of());
        private AcceptResult {
            progress = Map.copyOf(progress);
        }
    }

    private enum QuestMenu { LIST, EDIT, CONFIRM_DELETE, CONFIRM_ABANDON, REQUIREMENTS,
        PREREQUISITE_SELECT, SUCCESSOR_SELECT, OBJECTIVES, REWARD_ITEMS, PLAYER_JOURNAL, PLAYER_HISTORY, PLAYER_DETAILS,
        NPC_ACCEPT, NPC_SUBMIT, ADMIN_PLAYER_HISTORY, ADMIN_RESET_CONFIRM }
    private enum QuestDialogStage {
        AVAILABLE("Có thể nhận"), IN_PROGRESS("Đang thực hiện"), COMPLETED("Hoàn tất/nhận thưởng"),
        UNAVAILABLE("Không đủ điều kiện/đầy giới hạn");
        private final String displayName;
        QuestDialogStage(String displayName) { this.displayName = displayName; }
    }
    private record QuestDialogRefs(String available, String inProgress, String completed, String unavailable) {
        private static final QuestDialogRefs EMPTY = new QuestDialogRefs(null, null, null, null);
        private String forStage(QuestDialogStage stage) {
            return switch (stage) {
                case AVAILABLE -> available;
                case IN_PROGRESS -> inProgress;
                case COMPLETED -> completed;
                case UNAVAILABLE -> unavailable;
            };
        }
        private QuestDialogRefs with(QuestDialogStage stage, String value) {
            return switch (stage) {
                case AVAILABLE -> new QuestDialogRefs(value, inProgress, completed, unavailable);
                case IN_PROGRESS -> new QuestDialogRefs(available, value, completed, unavailable);
                case COMPLETED -> new QuestDialogRefs(available, inProgress, value, unavailable);
                case UNAVAILABLE -> new QuestDialogRefs(available, inProgress, completed, value);
            };
        }
    }
    private enum TextField { CREATE_ID, RENAME, GIVER, ADD_LEVEL_REQUIREMENT, ADD_ITEM_AMOUNT,
        ADD_KILL_ENTITY, ADD_KILL_AMOUNT, EDIT_KILL_ENTITY, EDIT_KILL_AMOUNT, ADD_OBJECTIVE_ITEM_AMOUNT,
        ADD_SUBMIT_ITEM_AMOUNT, ADD_BOSS_ID, ADD_BOSS_AMOUNT, ADD_OBJECTIVE_NPC_ID, SET_PRIMARY_NPC_ID }
    private record PendingTextInput(TextField field, String questId, int page, String value) {
        private PendingTextInput(TextField field, String questId, int page) { this(field, questId, page, ""); }
    }

    private static final class QuestMenuHolder implements InventoryHolder {
        private final UUID viewer;
        private final QuestMenu menu;
        private final String questId;
        private final int page;
        private String npcId;
        private final Map<Integer, String> questSlots = new LinkedHashMap<>();
        private final Map<Integer, String> trackingSlots = new LinkedHashMap<>();
        private Inventory inventory;
        private boolean navigating;

        private QuestMenuHolder(UUID viewer, QuestMenu menu, String questId, int page) {
            this.viewer = viewer;
            this.menu = menu;
            this.questId = questId;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
