package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.yaml.snakeyaml.error.YAMLException;

/** Guided, resumable first-run setup for the RPG onboarding configuration. */
public final class ServerSetupWizardManager implements Listener, CommandExecutor, TabCompleter {
    private static final String PERMISSION = "vnmine.command.init";
    private static final int[] CONTENT_SLOTS = {
        0, 1, 2, 3, 4, 5, 6, 7, 8,
        9, 10, 11, 12, 13, 14, 15, 16, 17
    };
    private static final int[] WORLD_CONTENT_SLOTS = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17,
        18, 19, 20, 21, 22, 23, 24, 25, 26
    };
    private static final int[] DASHBOARD_DIVIDER_SLOTS = {
        18, 19, 20, 21, 22, 23, 24, 25, 26
    };

    private final VnMineHardcore plugin;
    private final java.util.Map<UUID, String> pendingWorldNames = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, RealmDraft> pendingRealmDrafts = new java.util.concurrent.ConcurrentHashMap<>();

    public ServerSetupWizardManager(VnMineHardcore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Lệnh /vninit chỉ dùng trong game.");
            return true;
        }
        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage("§cBạn không có quyền cấu hình khởi tạo server.");
            return true;
        }
        openDashboard(player);
        plugin.getLogger().info("[ServerSetup] Admin " + player.getName() + " opened /vninit.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }

    private void openDashboard(Player player) {
        openDashboard(player, 0);
    }

    private void openDashboard(Player player, int page) {
        List<HumanRealmDefinitionLoader.HumanRealmDefinition> realms = realms();
        int pages = Math.max(1, (realms.size() + CONTENT_SLOTS.length - 1) / CONTENT_SLOTS.length);
        int safePage = Math.max(0, Math.min(page, pages - 1));
        SetupHolder holder = new SetupHolder(player, "dashboard", null, safePage);
        Inventory menu = Bukkit.createInventory(holder, 54, "VnMine • Setup server " + (safePage + 1) + "/" + pages);
        holder.inventory = menu;
        for (int slot : DASHBOARD_DIVIDER_SLOTS) {
            menu.setItem(slot, item(Material.GRAY_STAINED_GLASS_PANE, "§r", List.of()));
        }
        for (int index = safePage * CONTENT_SLOTS.length;
                index < Math.min((safePage + 1) * CONTENT_SLOTS.length, realms.size()); index++) {
            var realm = realms.get(index);
            int slot = CONTENT_SLOTS[index - safePage * CONTENT_SLOTS.length];
            holder.choices.put(slot, realm.id());
            RealmStatus status = status(realm);
            plugin.getLogger().info("[ServerSetup] realm-status admin=" + player.getName()
                + ", realm=" + realm.id() + ", starting=" + status.starting()
                + ", world=" + realm.worldName() + ", worldLoaded=" + status.worldReady()
                + ", spawnReady=" + status.spawnReady() + ", tutorialNpc=" + realm.tutorialNpcId()
                + ", npcReady=" + status.npcReady() + ", ready=" + status.ready());
            menu.setItem(slot, item(status.ready() ? Material.LIME_BANNER : Material.ORANGE_BANNER,
                (status.ready() ? "§a" : "§e") + realm.displayName(), List.of(
                    "§7ID: §f" + realm.id(),
                    status.starting() ? "§aĐang bật làm đại lục khởi đầu" : "§8Chưa bật làm đại lục khởi đầu",
                    status.worldReady() ? "§aWorld: " + realm.worldName() : "§cWorld chưa được nạp: " + realm.worldName(),
                    status.npcReady() ? "§aNPC tutorial đại lục: " + realm.tutorialNpcId() : "§7NPC tutorial đại lục: tùy chọn",
                    status.spawnReady() ? "§aĐiểm bắt đầu hợp lệ" : "§cĐiểm bắt đầu chưa hợp lệ",
                    "", "§eNhấn để cấu hình từng bước.")));
        }
        boolean onboardingEnabled = plugin.getConfig().getBoolean("rpg.onboarding.enabled", true);
        menu.setItem(27, item(Material.MAP, "§aQuản lý đại lục / world", List.of(
            "§7Thêm đại lục với ID, tên hiển thị và tên world do bạn chọn.",
            "§7Sửa tên/world, environment, spawn, NPC và allowlist race/class.",
            "§7Xóa có xác nhận; world riêng sẽ được archive sau restart.",
            "§eNhấn để mở danh sách.")));
        menu.setItem(28, item(Material.VILLAGER_SPAWN_EGG, "§dNPC Tutorial world bắt đầu", List.of(
            "§7Chọn một NPC Tutorial trong đúng world bắt đầu/reset.",
            "§eNhấn để chọn NPC.")));
        menu.setItem(29, item(Material.SHIELD, "§6Bảo vệ World bắt đầu/reset", List.of(
            "§7Thiết lập block, tương tác, PvP, quái, thời gian, đói/khát.",
            "§eNhấn để mở cài đặt chi tiết.")));
        menu.setItem(30, item(Material.NAME_TAG, "§bTên world bắt đầu: §f"
            + plugin.getConfig().getString("rpg.onboarding.start-world.name", "world_start"), List.of(
                "§7Nhập tên world; world được tạo khi khởi động lại server.",
                "§eNhấn để sửa.")));
        menu.setItem(31, item(Material.OBSIDIAN, "§bMôi trường world bắt đầu: §f"
            + plugin.getConfig().getString("rpg.onboarding.start-world.environment", "NORMAL"), List.of(
                "§7Nhấn để đổi NORMAL → NETHER → THE_END.",
                "§cĐổi môi trường/seed sẽ backup world cũ và tạo world mới sau restart.")));
        menu.setItem(32, item(Material.PAPER, "§bSeed world bắt đầu: §f"
            + optionalSeedText(plugin.getConfig().get("rpg.onboarding.start-world.seed")), List.of(
                "§7Tùy chọn; bỏ trống để Minecraft tự sinh seed.",
                "§7Ví dụ: §f123456789", "§eNhấn để nhập, !clear để xóa.")));
        menu.setItem(33, item(Material.ENDER_PEARL, "§bSau khi tạo nhân vật", List.of(
            "§7Chọn điểm đến sau race/class và quest mặc định.", "§eNhấn để cấu hình.")));
        menu.setItem(34, item(Material.TOTEM_OF_UNDYING, "§dQuản lý quest theo chủng tộc", List.of(
            "§7Chọn quest tự giao sau khi người chơi tạo nhân vật.",
            "§7Mỗi chủng tộc có thể gán quest riêng hoặc để trống.",
            "§eNhấn để mở giao diện quản lý race.")));
        int starterBookMappings = starterBookMappingCount();
        menu.setItem(35, item(starterBookMappings > 0 ? Material.ENCHANTED_BOOK : Material.BOOK,
            (starterBookMappings > 0 ? "§d" : "§c") + "Sách kỹ năng khởi đầu", List.of(
            starterBookMappings > 0 ? "§aĐã có " + starterBookMappings + " mapping race/class." : "§cChưa có mapping sách skill khởi đầu.",
            "§7Gán sách theo chủng tộc và class.",
            "§eNhấn để mở quản lý sách.")));
        menu.setItem(36, item(onboardingEnabled ? Material.LIME_DYE : Material.GRAY_DYE,
            "§eOnboarding người mới: " + (onboardingEnabled ? "§aBẬT" : "§cTẮT"), List.of(
                "§7Bật quy trình world start → NPC → đại lục → race/class.",
                "§7Nhấn để đổi; thay đổi được lưu ngay vào config.yml.")));
        menu.setItem(38, item(Material.WRITABLE_BOOK, "§bTên đại lục bắt đầu: §f"
            + startWorldDisplayName(), List.of(
                "§7Tên này được dùng trong nội dung và mục tiêu quest.",
                "§7Không thay đổi tên kỹ thuật của world.",
                "§eNhấn để nhập tên hiển thị.")));
        boolean tutorialQuestEnabled = tutorialQuestEnabled();
        menu.setItem(40, item(tutorialQuestEnabled ? Material.WRITABLE_BOOK : Material.BARRIER,
            (tutorialQuestEnabled ? "§b" : "§c") + "Quest RPG và quest hướng dẫn", List.of(
            tutorialQuestEnabled ? "§afirst-steps.yml đang bật." : "§cfirst-steps.yml chưa có hoặc đang tắt.",
            "§7Mở trình sửa First Step và trạng thái quest RPG.", "§eNhấn để quản lý.")));
        if (safePage > 0) menu.setItem(45, item(Material.ARROW, "§eĐại lục trước", List.of()));
        menu.setItem(49, item(Material.BARRIER, "§cĐóng / tiếp tục sau", List.of(
            "§7Mọi thay đổi đã xác nhận đều được lưu ngay.",
            "§7Thoát menu bất cứ lúc nào; chạy lại /vninit để tiếp tục.")));
        menu.setItem(50, item(Material.PAPER, "§7Trang đại lục " + (safePage + 1) + "/" + pages, List.of(
            "§7Hai dòng đầu là danh sách đại lục.")));
        if (safePage + 1 < pages) menu.setItem(53, item(Material.ARROW, "§eĐại lục tiếp", List.of()));
        if (realms.isEmpty()) {
            menu.setItem(41, item(Material.BARRIER, "§cKhông tìm thấy cấu hình đại lục", List.of(
                "§7Kiểm tra plugins/VnMineHardcore/worlds/*.yml và console.")));
        }
        player.openInventory(menu);
    }

    private void openCharacterCompletionSettings(Player player) {
        SetupHolder holder = new SetupHolder(player, "completion-config", null, 0);
        Inventory menu = Bukkit.createInventory(holder, 54, "Setup • Sau khi tạo nhân vật");
        holder.inventory = menu;
        String mode = plugin.getConfig().getString("rpg.onboarding.character-complete.teleport-mode", "REALM_SPAWN");
        String destination = switch (mode.toUpperCase(java.util.Locale.ROOT)) {
            case "START_WORLD" -> "World bắt đầu";
            case "ADMIN_LOCATION" -> plugin.getConfig().getString("rpg.onboarding.character-complete.location.world", "tọa độ admin");
            case "NONE" -> "Không dịch chuyển";
            default -> "Spawn đại lục đã chọn";
        };
        menu.setItem(13, item(Material.COMPASS, "§bĐiểm đến hiện tại: §f" + destination, List.of(
            "§7Chọn một nút bên dưới để đổi hành vi.")));
        boolean teleportEnabled = plugin.getConfig().getBoolean(
            "rpg.onboarding.character-complete.teleport-enabled", !"NONE".equalsIgnoreCase(mode));
        menu.setItem(16, item(teleportEnabled ? Material.LIME_DYE : Material.GRAY_DYE,
            (teleportEnabled ? "§a" : "§c") + "Dịch chuyển sau khi chọn class: "
                + (teleportEnabled ? "BẬT" : "TẮT"), List.of(
                "§7Tắt để giữ người chơi tại nơi hoàn tất tutorial.",
                "§7Bật lại sẽ dùng điểm đến đang chọn ở trên.", "§eNhấn để bật/tắt.")));
        menu.setItem(10, item(Material.MAP, "§aSpawn đại lục đã chọn", List.of("§7Hành vi mặc định.")));
        menu.setItem(11, item(Material.RECOVERY_COMPASS, "§eSpawn world bắt đầu", List.of(
            "§7Dịch chuyển về world start sau khi tạo nhân vật.")));
        menu.setItem(12, item(Material.ENDER_PEARL, "§dLưu vị trí hiện tại làm đích", List.of(
            "§7Đứng tại vị trí đích rồi bấm nút này.", "§7Lưu world, tọa độ và hướng nhìn.")));
        menu.setItem(14, item(Material.BARRIER, "§cKhông dịch chuyển", List.of("§7Giữ nguyên vị trí.")));
        String questId = plugin.getConfig().getString("rpg.onboarding.character-complete.default-quest-id", "");
        String questTitle = questId.isBlank() || plugin.getQuestCatalogManager() == null ? "Chưa chọn"
            : plugin.getQuestCatalogManager().getQuestTitle(questId);
        menu.setItem(22, item(Material.WRITABLE_BOOK, "§6Quest mặc định: §f" + questTitle, List.of(
            "§7Race vẫn có thể có quest riêng trong Quản lý race.", "§eNhấn để chọn quest đã lưu hoặc bỏ gán.")));
        menu.setItem(49, item(Material.ARROW, "§eQuay lại /vninit", List.of()));
        player.openInventory(menu);
    }

    private void saveCompletionSetting(Player player, String key, String value) {
        plugin.getConfig().set("rpg.onboarding.character-complete." + key, value);
        savePluginConfig(player, "set onboarding completion " + key + "=" + value);
        openCharacterCompletionSettings(player);
    }

    private void openCharacterCompletionQuestChoices(Player player, int requestedPage) {
        var questManager = plugin.getQuestCatalogManager();
        if (questManager == null) return;
        List<String> questIds = questManager.getEnabledQuestIds();
        int pageCount = Math.max(1, (questIds.size() + 44) / 45);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        SetupHolder holder = new SetupHolder(player, "completion-quests", null, page);
        Inventory menu = Bukkit.createInventory(holder, 54, "Quest mặc định sau tạo nhân vật");
        holder.inventory = menu;
        String selected = plugin.getConfig().getString("rpg.onboarding.character-complete.default-quest-id", "");
        for (int index = 0; index < 45; index++) {
            int itemIndex = page * 45 + index;
            if (itemIndex >= questIds.size()) break;
            String id = questIds.get(itemIndex);
            holder.choices.put(index, id);
            menu.setItem(index, item(Material.BOOK, (id.equals(selected) ? "§a✓ " : "§f")
                + questManager.getQuestTitle(id), List.of("§7Quest ID: §f" + id, "§eNhấn để chọn.")));
        }
        menu.setItem(45, item(Material.BARRIER, "§cBỏ quest mặc định", List.of()));
        if (page > 0) menu.setItem(46, item(Material.ARROW, "§eTrang trước", List.of()));
        menu.setItem(48, item(Material.PAPER, "§fTrang " + (page + 1) + "/" + pageCount,
            List.of("§7Quest đang bật: " + questIds.size())));
        menu.setItem(49, item(Material.ARROW, "§eQuay lại cài đặt", List.of()));
        if (page + 1 < pageCount) menu.setItem(50, item(Material.ARROW, "§eTrang sau", List.of()));
        if (questIds.isEmpty()) menu.setItem(22, item(Material.BARRIER, "§cChưa có quest đang bật", List.of()));
        player.openInventory(menu);
    }

    private void openWorldManager(Player player) {
        List<HumanRealmDefinitionLoader.HumanRealmDefinition> configured = realms();
        SetupHolder holder = new SetupHolder(player, "world-manager", null, 0);
        Inventory menu = Bukkit.createInventory(holder, 54, "VnMine • Quản lý đại lục/world");
        holder.inventory = menu;
        for (int index = 0; index < configured.size() && index < CONTENT_SLOTS.length; index++) {
            var realm = configured.get(index);
            int slot = CONTENT_SLOTS[index];
            holder.choices.put(slot, realm.id());
            menu.setItem(slot, item(Material.GRASS_BLOCK, "§b" + realm.displayName(), List.of(
                "§7ID: §f" + realm.id(), "§7World: §f" + realm.worldName(),
                "§7Môi trường: §f" + worldEnvironment(realm.id()),
                "§eNhấn để sửa cài đặt đại lục.")));
        }
        menu.setItem(45, item(Material.LIME_DYE, "§aThêm đại lục/world", List.of(
            "§7Tạo ID, tên hiển thị và tên world theo từng bước.",
            "§7World sẽ được tạo khi khởi động lại server.", "§eNhấn để bắt đầu.")));
        menu.setItem(49, item(Material.ARROW, "§7Quay lại /vninit", List.of()));
        menu.setItem(53, item(Material.BARRIER, "§cĐóng", List.of()));
        player.openInventory(menu);
    }

    private void openEnvironmentConfirmation(Player player, String realmId) {
        var realm = plugin.getRpgWorldManager().getRealmDefinition(realmId);
        if (realm == null) { openWorldManager(player); return; }
        String current = worldEnvironment(realmId).toUpperCase(Locale.ROOT);
        String next = switch (current) {
            case "NORMAL" -> "NETHER";
            case "NETHER" -> "THE_END";
            default -> "NORMAL";
        };
        SetupHolder holder = new SetupHolder(player, "confirm-environment", realmId, 0);
        holder.raceId = next;
        Inventory menu = Bukkit.createInventory(holder, 27, "§cXác nhận đổi môi trường world");
        holder.inventory = menu;
        menu.setItem(13, item(Material.TNT, "§cĐổi " + realm.worldName() + " sang " + next + "?", List.of(
            "§7Khi xác nhận, server sẽ backup folder world cũ", "§7và tạo world mới theo môi trường " + next,
            "§7ở lần khởi động tiếp theo.", "§cDữ liệu trong world mới sẽ bắt đầu lại.",
            "§7Backup cũ được giữ nguyên để phục hồi thủ công.",
            hasOtherRealmUsingWorld(realmId, realm.worldName())
                ? "§cWorld đang được realm khác dùng; hãy đổi realm kia trước." : "§aWorld không bị realm khác dùng.")));
        menu.setItem(11, item(Material.LIME_WOOL, "§aXác nhận đổi world", List.of("§7Lưu yêu cầu và áp dụng sau restart.")));
        menu.setItem(15, item(Material.RED_WOOL, "§cHủy", List.of("§7Không thay đổi cấu hình.")));
        player.openInventory(menu);
    }

    private void openDeleteConfirmation(Player player, String realmId) {
        var realm = plugin.getRpgWorldManager().getRealmDefinition(realmId);
        if (realm == null) { openWorldManager(player); return; }
        SetupHolder holder = new SetupHolder(player, "confirm-delete-realm", realmId, 0);
        Inventory menu = Bukkit.createInventory(holder, 27, "§cXác nhận xóa đại lục");
        holder.inventory = menu;
        menu.setItem(13, item(Material.TNT, "§cXóa " + realm.displayName() + "?", List.of(
            "§7File cấu hình sẽ được đưa vào worlds/deleted/.",
            "§7Nếu world không được dùng chung, dữ liệu sẽ được archive sau restart.",
            "§cKhông thể xóa đại lục cuối cùng.", "§eBackup world không bị xóa vĩnh viễn.")));
        menu.setItem(11, item(Material.LIME_WOOL, "§aXác nhận xóa", List.of("§7Xóa cấu hình đại lục.")));
        menu.setItem(15, item(Material.RED_WOOL, "§cHủy", List.of("§7Quay lại cấu hình đại lục.")));
        player.openInventory(menu);
    }

    private void deleteRealmDefinition(Player player, String realmId) {
        var definitions = plugin.getRpgWorldManager().getDefinitions();
        var definition = definitions.get(realmId);
        if (definition == null || definitions.size() <= 1) {
            player.sendMessage("§cKhông thể xóa đại lục cuối cùng hoặc đại lục không còn tồn tại.");
            openWorldManager(player);
            return;
        }
        String worldName = definition.worldName();
        boolean shared = hasOtherRealmUsingWorld(realmId, worldName);
        File source = findRealmFile(realmId);
        if (source == null) { player.sendMessage("§cKhông tìm thấy file cấu hình đại lục."); openWorldManager(player); return; }
        File archiveDir = new File(plugin.getDataFolder(), "worlds/deleted");
        File archived = new File(archiveDir, source.getName() + ".deleted-" + System.currentTimeMillis());
        try {
            if (!archiveDir.isDirectory() && !archiveDir.mkdirs()) throw new IOException("Cannot create deleted definitions archive");
            try { Files.move(source.toPath(), archived.toPath(), StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(source.toPath(), archived.toPath()); }
            if (!shared && !plugin.getRpgWorldManager().scheduleWorldArchive(worldName)) {
                restoreArchivedDefinition(archived, source);
                player.sendMessage("§cKhông lưu được yêu cầu archive world; cấu hình đã được khôi phục.");
                openRealm(player, realmId);
                return;
            }
            if (!plugin.getRpgWorldManager().reload()) {
                if (!shared) plugin.getRpgWorldManager().cancelWorldArchive(worldName);
                restoreArchivedDefinition(archived, source);
                player.sendMessage("§cKhông nạp được catalog; file cấu hình đã được khôi phục.");
                openRealm(player, realmId);
                return;
            }
            plugin.getLogger().info("[ServerSetup] admin=" + player.getName() + ", action=delete-realm, id="
                + realmId + ", world=" + worldName + ", shared=" + shared + ", configArchive=" + archived);
            player.sendMessage(shared
                ? "§aĐã archive cấu hình. World " + worldName + " vẫn được giữ vì đang dùng chung hoặc là start world."
                : "§aĐã archive cấu hình. Dữ liệu world " + worldName + " sẽ được archive khi server khởi động lại.");
            openWorldManager(player);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "[ServerSetup] Could not archive realm definition " + realmId, exception);
            player.sendMessage("§cKhông thể archive cấu hình; chưa xóa đại lục.");
            openRealm(player, realmId);
        }
    }

    private boolean hasOtherRealmUsingWorld(String realmId, String worldName) {
        boolean sharedRealm = plugin.getRpgWorldManager().getDefinitions().values().stream()
            .anyMatch(other -> !other.id().equals(realmId) && other.worldName().equalsIgnoreCase(worldName));
        String startWorld = plugin.getConfig().getString("rpg.onboarding.start-world.name", "world_start");
        return sharedRealm || worldName.equalsIgnoreCase(startWorld);
    }

    private void restoreArchivedDefinition(File archived, File original) throws IOException {
        try { Files.move(archived.toPath(), original.toPath(), StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ignored) {
            Files.move(archived.toPath(), original.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void promptNewRealm(Player player) {
        pendingWorldNames.put(player.getUniqueId(), "__create_realm_id__");
        player.closeInventory();
        player.sendMessage("§6[Setup đại lục] §eNhập ID mới, ví dụ: §fma-gioi§e. Chỉ dùng chữ thường, số và dấu gạch nối. §c!cancel §eđể hủy.");
    }

    private boolean handleRealmCreationChat(Player player, String token, String input) {
        if (!token.startsWith("__create_realm_")) return false;
        if ("!cancel".equalsIgnoreCase(input)) {
            pendingRealmDrafts.remove(player.getUniqueId());
            player.sendMessage("§eĐã hủy tạo đại lục; chưa có file nào được tạo.");
            openWorldManager(player);
            return true;
        }
        if ("__create_realm_id__".equals(token)) {
            if (!input.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || input.length() > 48
                    || plugin.getRpgWorldManager().getRealmDefinition(input) != null
                    || new File(plugin.getDataFolder(), "worlds/" + input + ".yml").exists()) {
                player.sendMessage("§cID không hợp lệ hoặc đã tồn tại. Ví dụ: §fma-gioi§c. Nhập lại hoặc !cancel.");
                pendingWorldNames.put(player.getUniqueId(), token);
                return true;
            }
            pendingRealmDrafts.put(player.getUniqueId(), new RealmDraft(input, null));
            pendingWorldNames.put(player.getUniqueId(), "__create_realm_display__");
            player.sendMessage("§6[Setup đại lục] §eNhập tên hiển thị, ví dụ: §fMa Giới§e. §c!cancel §eđể hủy.");
            return true;
        }
        if ("__create_realm_display__".equals(token)) {
            RealmDraft draft = pendingRealmDrafts.get(player.getUniqueId());
            if (draft == null) { player.sendMessage("§cPhiên tạo đại lục đã hết hạn; hãy bắt đầu lại."); openWorldManager(player); return true; }
            if (input.isBlank() || input.length() > 48) {
                player.sendMessage("§cTên hiển thị phải có 1–48 ký tự. Ví dụ: §fMa Giới§c. Nhập lại hoặc !cancel.");
                pendingWorldNames.put(player.getUniqueId(), token);
                return true;
            }
            pendingRealmDrafts.put(player.getUniqueId(), new RealmDraft(draft.id(), input));
            pendingWorldNames.put(player.getUniqueId(), "__create_realm_world__");
            player.sendMessage("§6[Setup đại lục] §eNhập tên world, ví dụ: §fworld_magioi§e. Chỉ dùng chữ, số, _ hoặc -. §c!cancel §eđể hủy.");
            return true;
        }
        if ("__create_realm_world__".equals(token)) {
            RealmDraft draft = pendingRealmDrafts.remove(player.getUniqueId());
            if (draft == null) { player.sendMessage("§cPhiên tạo đại lục đã hết hạn; hãy bắt đầu lại."); openWorldManager(player); return true; }
            if (!input.matches("[A-Za-z0-9_-]{1,64}")) {
                pendingRealmDrafts.put(player.getUniqueId(), draft);
                player.sendMessage("§cTên world không hợp lệ. Ví dụ: §fworld_magioi§c. Nhập lại hoặc !cancel.");
                pendingWorldNames.put(player.getUniqueId(), token);
                return true;
            }
            createRealmDefinition(player, draft, input);
            return true;
        }
        return false;
    }

    private void createRealmDefinition(Player player, RealmDraft draft, String worldName) {
        File target = new File(plugin.getDataFolder(), "worlds/" + draft.id() + ".yml");
        if (target.exists()) { player.sendMessage("§cID vừa nhập đã được tạo bởi thao tác khác."); openWorldManager(player); return; }
        try (var resource = plugin.getResource("worlds/human-realm.yml")) {
            if (resource == null) throw new IOException("Bundled realm template missing");
            YamlConfiguration definition = new YamlConfiguration();
            definition.options().parseComments(true);
            definition.loadFromString(new String(resource.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            definition.set("id", draft.id());
            definition.set("display-name", draft.displayName());
            definition.set("world-name", worldName);
            definition.set("world.create-if-missing", true);
            definition.set("world.environment", "NORMAL");
            definition.set("world.revision", 0L);
            definition.set("world.seed", null);
            definition.set("tutorial-npc-id", "tutorial-" + draft.id());
            definition.set("starting-selectable", false);
            File folder = target.getParentFile();
            if (folder != null && !folder.isDirectory() && !folder.mkdirs()) throw new IOException("Could not create worlds folder");
            saveAtomically(definition, target);
            if (!plugin.getRpgWorldManager().reload()) {
                target.delete();
                player.sendMessage("§cKhông nạp được đại lục mới; file vừa tạo đã được gỡ. Kiểm tra console.");
                openWorldManager(player);
                return;
            }
            plugin.getLogger().info("[ServerSetup] admin=" + player.getName() + ", action=create-realm, id="
                + draft.id() + ", name=" + draft.displayName() + ", world=" + worldName);
            player.sendMessage("§aĐã tạo cấu hình đại lục §f" + draft.id() + "§a. Mở lại menu để chỉnh NPC, spawn, race/class và environment; world được tạo sau restart.");
            openRealm(player, draft.id());
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().log(Level.SEVERE, "[ServerSetup] Could not create realm " + draft.id(), exception);
            player.sendMessage("§cKhông tạo được cấu hình đại lục. Xem console.");
            openWorldManager(player);
        }
    }

    private void openRealm(Player player, String realmId) {
        var realm = plugin.getRpgWorldManager().getRealmDefinition(realmId);
        if (realm == null) {
            player.sendMessage("§cKhông tìm thấy đại lục này trong cấu hình đang tải. Hãy tải lại /vninit.");
            openDashboard(player);
            return;
        }
        RealmStatus status = status(realm);
        SetupHolder holder = new SetupHolder(player, "realm", realmId, 0);
        Inventory menu = Bukkit.createInventory(holder, 54, "VnMine • " + realm.displayName());
        holder.inventory = menu;
        menu.setItem(10, item(realm.startingSelectable() ? Material.LIME_DYE : Material.RED_DYE,
            "§eĐại lục khởi đầu: " + (realm.startingSelectable() ? "§aBẬT" : "§cTẮT"), List.of(
                "§7Bật nếu người mới được chọn đại lục này khi đăng nhập.",
                "§7Có thể bật nhiều đại lục cùng lúc.", "§eNhấn để đổi và lưu ngay.")));
        menu.setItem(11, item(Material.NAME_TAG, "§bTên hiển thị: §f" + realm.displayName(), List.of(
            "§7Tên hiển thị tách biệt với ID kỹ thuật.",
            "§eNhấn để nhập tên mới, ví dụ: §fMa Giới§e.")));
        menu.setItem(13, item(Material.NAME_TAG, "§bID đại lục: §f" + realm.id(), List.of(
            "§7ID cũ vẫn được giữ làm bí danh để profile đang làm dở tiếp tục hoạt động.",
            "§eNhấn để đổi ID, ví dụ: §fma-gioi§e.")));
        menu.setItem(12, item(status.worldReady() ? Material.GRASS_BLOCK : Material.OBSIDIAN,
            "§bWorld chính: §f" + realm.worldName(), List.of(
                "§7Có thể chọn world đang nạp hoặc nhập world mới.",
                "§7World mới sẽ được tạo sau khi lưu và khởi động lại.",
                "§eNhấn để chọn world đã có.")));
        menu.setItem(26, item(Material.ANVIL, "§bĐổi/nhập tên world: §f" + realm.worldName(), List.of(
            "§7Nhập world hiện có hoặc tên world mới.",
            "§7Đổi tên world nguồn sẽ tạo world mới sau restart; world cũ được backup.",
            "§eNhấn để nhập tên world.")));
        menu.setItem(14, item(status.npcReady() ? Material.VILLAGER_SPAWN_EGG : Material.NAME_TAG,
            "§dChọn NPC Tutorial cho world này", List.of(
                "§7Đang chọn: §f" + realm.tutorialNpcId(),
                "§7Danh sách chỉ gồm NPC loại tutorial được đặt trong:",
                "§f" + realm.worldName(),
                "§7Tạo NPC trước bằng /vnnpc create <id> tutorial.",
                "§eNhấn để mở danh sách NPC tutorial của world này.")));
        menu.setItem(20, item(Material.WRITABLE_BOOK, "§dDialog và quest tutorial", List.of(
            "§7Chọn hội thoại chạy trong action bar.",
            "§7Chọn quest giao tự động sau khi hội thoại kết thúc.",
            "§7Được chỉnh tại NPC bằng shift + chuột phải.", "§eNhấn để cấu hình.")));
        menu.setItem(16, item(Material.COMPASS,
            "§bMôi trường: §f" + worldEnvironment(realm.id()), List.of(
                "§7Nhấn để đổi NORMAL → NETHER → THE_END.",
                "§cĐổi môi trường/seed sẽ backup world cũ và tạo world mới sau restart.",
                "§eĐây là thiết lập world cho realm này.")));
        menu.setItem(22, item(Material.NAME_TAG, "§bSeed world: §f" + realmSeedText(realm.id()), List.of(
            "§7Tùy chọn; bỏ trống để dùng seed hiện có hoặc seed ngẫu nhiên.",
            "§7Nhập số nguyên 64-bit; ví dụ: §f-987654321", "§eNhấn để nhập, !clear để xóa.")));
        menu.setItem(24, item(Material.TNT, "§cXóa định nghĩa đại lục", List.of(
            "§7Xóa sau bước xác nhận. Không thể xóa đại lục cuối cùng.",
            "§7Nếu world không dùng nơi khác, dữ liệu world sẽ được archive sau restart.",
            "§eNhấn để xem xác nhận.")));
        boolean availabilityLimited = realmAvailabilityEnabled(realm.id());
        menu.setItem(18, item(availabilityLimited ? Material.PLAYER_HEAD : Material.ENDER_EYE,
            "§dRace/class khả dụng: " + (availabilityLimited ? "§aTùy chỉnh" : "§eTất cả"), List.of(
                "§7Chọn chủng tộc và class cho người mới realm này.",
                "§7Mặc định cho phép mọi race/class hợp lệ.", "§eNhấn để cấu hình.")));
        Location current = player.getLocation();
        menu.setItem(28, item(status.spawnReady() ? Material.RESPAWN_ANCHOR : Material.LODESTONE,
            "§6Lưu vị trí hiện tại làm spawn", List.of(
                "§7Ghi world, tọa độ và hướng nhìn hiện tại của bạn.",
                "§7Hãy đứng đúng điểm người mới sẽ bắt đầu.",
                "§7Vị trí hiện tại: §f" + current.getWorld().getName() + " "
                    + current.getBlockX() + ", " + current.getBlockY() + ", " + current.getBlockZ(),
                "§eNhấn để lưu ngay.")));
        menu.setItem(30, item(Material.COMPASS, "§aDùng spawn của world", List.of(
                "§7Xóa tọa độ tùy chỉnh và dùng spawn do Paper/server đặt.",
            "§7World chính cần được nạp để spawn được dùng.", "§eNhấn để lưu ngay.")));
        menu.setItem(32, item(status.ready() ? Material.EMERALD : Material.REDSTONE,
            status.ready() ? "§aĐại lục đã sẵn sàng" : "§cKiểm tra cấu hình đại lục", List.of(
                status.starting() ? "§a✓ Cho phép bắt đầu" : "§c✗ Chưa bật làm đại lục khởi đầu",
                status.worldReady() ? "§a✓ World đang nạp" : "§c✗ World chưa nạp hoặc sai tên",
                status.npcReady() ? "§a✓ NPC tutorial đúng loại và world" : "§c✗ Chọn NPC tutorial ở đúng world",
                status.spawnReady() ? "§a✓ Có spawn hợp lệ" : "§c✗ Cần world spawn hoặc tọa độ hợp lệ",
                "§7Mục chưa đạt sẽ được giữ lại để bạn cấu hình tiếp.")));
        menu.setItem(49, item(Material.ARROW, "§7Quay lại danh sách setup", List.of()));
        menu.setItem(53, item(Material.BARRIER, "§cĐóng / tiếp tục sau", List.of(
            "§7Đã lưu đến bước hiện tại. Chạy /vninit để tiếp tục.")));
        player.openInventory(menu);
    }

    public void openTutorialNpcEditor(Player player, String npcId) {
        if (!player.hasPermission(PERMISSION) && !player.hasPermission("vnmine.command.npc.admin")) {
            player.sendMessage("§cCần quyền quản trị NPC hoặc /vninit để sửa hội thoại NPC tutorial.");
            return;
        }
        String startNpcId = plugin.getConfig().getString("rpg.onboarding.start-world.tutorial-npc-id", "");
        if (!startNpcId.isBlank() && startNpcId.equalsIgnoreCase(npcId)) {
            openStartWorldTutorialNpcEditor(player, npcId);
            return;
        }
        var realm = plugin.getRpgWorldManager().getDefinitions().values().stream()
            .filter(value -> value.tutorialNpcId().equalsIgnoreCase(npcId)).findFirst().orElse(null);
        if (realm == null) {
            player.sendMessage("§cNPC " + npcId + " chưa được gán làm NPC tutorial trong /vninit.");
            return;
        }
        File file = findRealmFile(realm.id());
        YamlConfiguration yaml = file == null ? new YamlConfiguration() : YamlConfiguration.loadConfiguration(file);
        String dialogId = yaml.getString("tutorial.dialog-id");
        String questId = yaml.getString("tutorial.auto-quest-id");
        SetupHolder holder = new SetupHolder(player, "tutorial-config", realm.id(), 0);
        Inventory menu = Bukkit.createInventory(holder, 54, "NPC Tutorial • " + npcId);
        holder.inventory = menu;
        menu.setItem(11, item(Material.WRITABLE_BOOK, "§dDialog: §f" + (dialogId == null ? "Chưa chọn" : dialogId), List.of(
            "§7Chọn quest-dialog dùng khi người chơi gặp NPC.", "§7Tạo/sửa dialog trực tiếp trong bộ chọn quest.",
            "§eNhấn để chọn.")));
        menu.setItem(15, item(Material.ENCHANTED_BOOK, "§bQuest tự giao: §f" + (questId == null ? "Chưa chọn" : questId), List.of(
            "§7Chỉ quest có giver NPC ID đúng là §f" + npcId + " §7mới được liệt kê.",
            "§7Quest sẽ được giao sau khi hội thoại tutorial kết thúc.", "§eNhấn để chọn.")));
        menu.setItem(49, item(Material.ARROW, "§7Quay lại cấu hình đại lục", List.of()));
        player.openInventory(menu);
    }

    private void openStartWorldTutorialNpcEditor(Player player, String npcId) {
        String startWorld = plugin.getConfig().getString("rpg.onboarding.start-world.name", "world_start");
        String npcWorld = plugin.getNpcManager() == null ? null
            : plugin.getNpcManager().getNpcTarget(npcId).map(NpcManager.NpcTarget::world).orElse(null);
        if (npcWorld == null || !npcWorld.equalsIgnoreCase(startWorld)) {
            player.sendMessage("§cNPC tutorial world bắt đầu phải được đặt trong world §f" + startWorld + "§c.");
            plugin.getLogger().warning("[ServerSetup] Start-world tutorial NPC world mismatch: npc=" + npcId
                + ", expected=" + startWorld + ", actual=" + npcWorld);
            return;
        }
        String root = "rpg.onboarding.start-world.tutorial";
        String dialogId = plugin.getConfig().getString(root + ".dialog-id");
        String questId = plugin.getConfig().getString(root + ".auto-quest-id");
        SetupHolder holder = new SetupHolder(player, "start-tutorial-config", "__start__", 0);
        Inventory menu = Bukkit.createInventory(holder, 54, "NPC Tutorial World bắt đầu • " + npcId);
        holder.inventory = menu;
        menu.setItem(11, item(Material.WRITABLE_BOOK, "§dDialog: §f" + (dialogId == null ? "Chưa chọn" : dialogId), List.of(
            "§7Dialog sẽ phát khi người chơi tương tác NPC này.",
            "§7Tạo/sửa dialog trực tiếp trong bộ chọn quest.", "§eNhấn để chọn.")));
        menu.setItem(15, item(Material.ENCHANTED_BOOK, "§bQuest tự giao: §f" + (questId == null ? "Chưa chọn" : questId), List.of(
            "§7Chỉ liệt kê quest có giver NPC ID là §f" + npcId + "§7.",
            "§7Quest được giao sau khi dialog kết thúc.", "§eNhấn để chọn.")));
        menu.setItem(49, item(Material.ARROW, "§7Quay lại /vninit", List.of()));
        player.openInventory(menu);
    }

    private void openTutorialQuestChoices(Player player, String realmId) {
        var realm = plugin.getRpgWorldManager().getRealmDefinition(realmId);
        List<String> quests = realm == null || plugin.getQuestCatalogManager() == null ? List.of()
            : plugin.getQuestCatalogManager().getQuestIdsForNpc(realm.tutorialNpcId());
        SetupHolder holder = new SetupHolder(player, "tutorial-quests", realmId, 0);
        Inventory menu = Bukkit.createInventory(holder, 54, "Quest giao sau tutorial");
        holder.inventory = menu;
        File file = findRealmFile(realmId);
        String current = file == null ? null : YamlConfiguration.loadConfiguration(file).getString("tutorial.auto-quest-id");
        for (int i = 0; i < quests.size() && i < CONTENT_SLOTS.length; i++) {
            String questId = quests.get(i);
            int slot = CONTENT_SLOTS[i];
            holder.choices.put(slot, questId);
            String title = plugin.getQuestCatalogManager().getQuestTitle(questId);
            menu.setItem(slot, item(questId.equals(current) ? Material.ENCHANTED_BOOK : Material.BOOK,
                (questId.equals(current) ? "§a✓ " : "§f") + title, List.of("§7ID: §f" + questId,
                    "§7Giao bởi NPC: §f" + realm.tutorialNpcId(), "§eNhấn để chọn.")));
        }
        if (quests.isEmpty()) menu.setItem(22, item(Material.BARRIER, "§cChưa có quest phù hợp", List.of(
            "§7Tạo quest trong /vnquest admin và đặt giver NPC ID thành:", "§f" + (realm == null ? "?" : realm.tutorialNpcId()))));
        menu.setItem(45, item(Material.BARRIER, "§cBỏ quest tự giao", List.of()));
        menu.setItem(49, item(Material.ARROW, "§7Quay lại", List.of()));
        player.openInventory(menu);
    }

    private void openStartTutorialQuestChoices(Player player) {
        String npcId = plugin.getConfig().getString("rpg.onboarding.start-world.tutorial-npc-id", "");
        List<String> quests = npcId.isBlank() || plugin.getQuestCatalogManager() == null ? List.of()
            : plugin.getQuestCatalogManager().getQuestIdsForNpc(npcId);
        SetupHolder holder = new SetupHolder(player, "start-tutorial-quests", "__start__", 0);
        Inventory menu = Bukkit.createInventory(holder, 54, "Quest giao sau tutorial world bắt đầu");
        holder.inventory = menu;
        String current = plugin.getConfig().getString("rpg.onboarding.start-world.tutorial.auto-quest-id");
        for (int i = 0; i < quests.size() && i < CONTENT_SLOTS.length; i++) {
            String questId = quests.get(i);
            int slot = CONTENT_SLOTS[i];
            holder.choices.put(slot, questId);
            String title = plugin.getQuestCatalogManager().getQuestTitle(questId);
            menu.setItem(slot, item(questId.equals(current) ? Material.ENCHANTED_BOOK : Material.BOOK,
                (questId.equals(current) ? "§a✓ " : "§f") + title, List.of("§7ID: §f" + questId,
                    "§7Giao bởi NPC: §f" + npcId, "§eNhấn để chọn.")));
        }
        if (quests.isEmpty()) menu.setItem(22, item(Material.BARRIER, "§cChưa có quest phù hợp", List.of(
            "§7Tạo quest trong /vnquest admin và đặt giver NPC ID là:",
            "§f" + (npcId.isBlank() ? "chưa chọn NPC" : npcId))));
        menu.setItem(45, item(Material.BARRIER, "§cBỏ quest tự giao", List.of()));
        menu.setItem(49, item(Material.ARROW, "§7Quay lại", List.of()));
        player.openInventory(menu);
    }

    private String worldEnvironment(String realmId) {
        File file = findRealmFile(realmId);
        return file == null ? "NORMAL" : YamlConfiguration.loadConfiguration(file)
            .getString("world.environment", "NORMAL");
    }

    private String realmSeedText(String realmId) {
        File file = findRealmFile(realmId);
        return file == null ? "tự sinh" : optionalSeedText(YamlConfiguration.loadConfiguration(file).get("world.seed"));
    }

    private String optionalSeedText(Object seed) {
        return seed instanceof Number ? seed.toString() : "tự sinh";
    }

    private String startWorldDisplayName() {
        return LegacyText.translate(plugin.getConfig().getString(
            "rpg.onboarding.start-world.display-name", "Đại lục khởi đầu"));
    }

    private boolean realmAvailabilityEnabled(String realmId) {
        File file = findRealmFile(realmId);
        return file != null && YamlConfiguration.loadConfiguration(file)
            .getBoolean("character-availability.enabled", false);
    }

    private void openRealmRaces(Player player, String realmId) {
        var characterManager = plugin.getCharacterSelectionManager();
        var races = characterManager == null ? List.<CharacterSelectionManager.RaceDefinition>of()
            : characterManager.getConfiguredRaceDefinitions();
        File file = findRealmFile(realmId);
        YamlConfiguration yaml = file == null ? new YamlConfiguration() : YamlConfiguration.loadConfiguration(file);
        boolean limited = yaml.getBoolean("character-availability.enabled", false);
        SetupHolder holder = new SetupHolder(player, "availability-races", realmId, 0);
        Inventory menu = Bukkit.createInventory(holder, 54, "Race khả dụng • " + realmId);
        holder.inventory = menu;
        int index = 0;
        for (var race : races) {
            int slot = CONTENT_SLOTS[index++];
            boolean selected = !limited || yaml.getStringList("character-availability.races").contains(race.id());
            holder.choices.put(slot, race.id());
            menu.setItem(slot, item(selected ? race.material() : Material.GRAY_DYE,
                (selected ? "§a✓ " : "§c✗ ") + race.name(), List.of(
                    "§7ID: §f" + race.id(), "§7Trạng thái: " + (selected ? "§aCho phép" : "§cẨn"),
                    "§eChuột trái: cấu hình class.", "§eChuột phải: bật/tắt race.")));
        }
        menu.setItem(4, item(limited ? Material.LIME_DYE : Material.GRAY_DYE,
            "§eGiới hạn race/class: " + (limited ? "§aBẬT" : "§cTẮT"), List.of(
                "§7Tắt: mọi lựa chọn hợp lệ đều được phép.",
                "§7Bật: chỉ race/class được đánh dấu mới xuất hiện.", "§eNhấn để đổi.")));
        menu.setItem(49, item(Material.ARROW, "§7Quay lại realm", List.of()));
        player.openInventory(menu);
    }

    private void openRealmClasses(Player player, String realmId, String raceId) {
        var characterManager = plugin.getCharacterSelectionManager();
        var race = characterManager == null ? null : characterManager.getConfiguredRaceDefinitions().stream()
            .filter(value -> value.id().equals(raceId)).findFirst().orElse(null);
        if (race == null) { openRealmRaces(player, realmId); return; }
        File file = findRealmFile(realmId);
        YamlConfiguration yaml = file == null ? new YamlConfiguration() : YamlConfiguration.loadConfiguration(file);
        boolean limited = yaml.getBoolean("character-availability.enabled", false);
        SetupHolder holder = new SetupHolder(player, "availability-classes", realmId, 0);
        holder.raceId = raceId;
        Inventory menu = Bukkit.createInventory(holder, 54, "Class khả dụng • " + race.name());
        holder.inventory = menu;
        List<CharacterSelectionManager.ClassDefinition> classes = characterManager.getClassDefinitions(raceId);
        for (int i = 0; i < classes.size() && i < CONTENT_SLOTS.length; i++) {
            var klass = classes.get(i);
            int slot = CONTENT_SLOTS[i];
            boolean selected = !limited || yaml.getStringList("character-availability.classes." + raceId)
                .contains(klass.id());
            holder.choices.put(slot, klass.id());
            menu.setItem(slot, item(selected ? klass.material() : Material.GRAY_DYE,
                (selected ? "§a✓ " : "§c✗ ") + klass.name(), List.of("§7ID: §f" + klass.id(),
                    "§7Trạng thái: " + (selected ? "§aCho phép" : "§cẨn"), "§eNhấn để đổi.")));
        }
        menu.setItem(49, item(Material.ARROW, "§7Quay lại danh sách race", List.of()));
        player.openInventory(menu);
    }

    private void toggleAvailability(Player player, String realmId, String path, String id, boolean currentlySelected) {
        updateRealm(player, realmId, config -> {
            if (!config.getBoolean("character-availability.enabled", false)) {
                var characters = plugin.getCharacterSelectionManager();
                if (characters != null) {
                    List<String> raceIds = characters.getConfiguredRaceDefinitions().stream()
                        .map(CharacterSelectionManager.RaceDefinition::id).toList();
                    config.set("character-availability.races", raceIds);
                    for (String raceId : raceIds) config.set("character-availability.classes." + raceId,
                        characters.getClassDefinitions(raceId).stream()
                            .map(CharacterSelectionManager.ClassDefinition::id).toList());
                }
                config.set("character-availability.enabled", true);
            }
            List<String> values = new java.util.ArrayList<>(config.getStringList(path));
            if (currentlySelected) values.remove(id); else if (!values.contains(id)) values.add(id);
            values.sort(String.CASE_INSENSITIVE_ORDER);
            config.set(path, values);
        }, "toggle " + path + " " + id);
    }

    @EventHandler
    public void onWorldNameChat(AsyncPlayerChatEvent event) {
        String realmId = pendingWorldNames.remove(event.getPlayer().getUniqueId());
        if (realmId == null) return;
        event.setCancelled(true);
        String input = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = event.getPlayer();
            if (!player.isOnline()) return;
            if ("!cancel".equalsIgnoreCase(input)) {
                if (realmId.startsWith("__first_steps_")) {
                    player.sendMessage("§eĐã hủy nhập liệu First Step.");
                    openFirstStepsEditor(player);
                    return;
                }
                if (handleRealmCreationChat(player, realmId, input)) return;
                player.sendMessage("§eĐã hủy nhập tên world.");
                if ("__start__".equals(realmId) || "__start_seed__".equals(realmId)
                        || "__start_display_name__".equals(realmId)) openDashboard(player);
                else if (realmId.startsWith("__seed__:")) openRealm(player, realmId.substring("__seed__:".length()));
                else if (realmId.startsWith("__edit_display__:") || realmId.startsWith("__edit_world__:")
                        || realmId.startsWith("__edit_id__:"))
                    openRealm(player, realmId.substring(realmId.indexOf(':') + 1));
                else openWorlds(player, realmId, 0);
                return;
            }
            if (handleRealmCreationChat(player, realmId, input)) return;
            if (realmId.startsWith("__first_steps_")) {
                String field = switch (realmId) {
                    case "__first_steps_title__" -> "title";
                    case "__first_steps_description__" -> "description";
                    case "__first_steps_start_message__" -> "objective.start-message";
                    case "__first_steps_complete_message__" -> "objective.complete-message";
                    default -> null;
                };
                int maximumLength = "title".equals(field) ? 64 : "description".equals(field) ? 300 : 240;
                if (field == null || input.isBlank() || input.length() > maximumLength) {
                    player.sendMessage("§cNội dung không hợp lệ; nhập từ 1 đến " + maximumLength
                        + " ký tự. Nhập lại hoặc !cancel.");
                    pendingWorldNames.put(player.getUniqueId(), realmId);
                    return;
                }
                saveFirstStepsField(player, "quest." + field,
                    LegacyText.translate(input));
                return;
            }
            if ("__start_display_name__".equals(realmId)) {
                if (input.isBlank() || input.length() > 48) {
                    player.sendMessage("§cTên đại lục phải có 1–48 ký tự. Nhập lại hoặc !cancel.");
                    pendingWorldNames.put(player.getUniqueId(), realmId);
                    return;
                }
                String displayName = LegacyText.translate(input);
                plugin.getConfig().set("rpg.onboarding.start-world.display-name", displayName);
                savePluginConfig(player, "rpg.onboarding.start-world.display-name=" + input);
                player.sendMessage("§aĐã lưu tên hiển thị đại lục bắt đầu: §f" + displayName);
                openDashboard(player);
                return;
            }
            if (realmId.startsWith("__edit_display__:")) {
                String targetId = realmId.substring("__edit_display__:".length());
                if (input.isBlank() || input.length() > 48) {
                    player.sendMessage("§cTên hiển thị phải có 1–48 ký tự. Nhập lại hoặc !cancel.");
                    pendingWorldNames.put(player.getUniqueId(), realmId);
                    return;
                }
                updateRealm(player, targetId, config -> config.set("display-name", input), "set display-name=" + input);
                openRealm(player, targetId);
                return;
            }
            if (realmId.startsWith("__edit_id__:")) {
                String targetId = realmId.substring("__edit_id__:".length());
                if (!input.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || input.length() > 48) {
                    player.sendMessage("§cID chỉ gồm chữ thường, số và dấu gạch nối. Ví dụ: §fma-gioi§c. Nhập lại hoặc !cancel.");
                    pendingWorldNames.put(player.getUniqueId(), realmId);
                    return;
                }
                renameRealmId(player, targetId, input);
                return;
            }
            if (realmId.startsWith("__edit_world__:")) {
                String targetId = realmId.substring("__edit_world__:".length());
                if (!input.matches("[A-Za-z0-9_-]{1,64}")) {
                    player.sendMessage("§cTên world không hợp lệ; ví dụ: §fworld_magioi§c. Nhập lại hoặc !cancel.");
                    pendingWorldNames.put(player.getUniqueId(), realmId);
                    return;
                }
                updateRealm(player, targetId, config -> {
                    String previous = config.getString("world-name", "");
                    if (!previous.equalsIgnoreCase(input)) {
                        config.set("tutorial-npc-id", null);
                        config.set("tutorial.dialog-id", null);
                        config.set("tutorial.auto-quest-id", null);
                    }
                    config.set("world-name", input);
                    config.set("world.create-if-missing", true);
                }, "set world-name=" + input);
                openRealm(player, targetId);
                return;
            }
            if ("__start_seed__".equals(realmId) || realmId.startsWith("__seed__:")) {
                boolean startWorld = "__start_seed__".equals(realmId);
                String targetRealmId = startWorld ? null : realmId.substring("__seed__:".length());
                if ("!clear".equalsIgnoreCase(input)) {
                    if (startWorld) {
                        plugin.getConfig().set("rpg.onboarding.start-world.seed", null);
                        savePluginConfig(player, "clear rpg.onboarding.start-world.seed");
                        openDashboard(player);
                    } else {
                        updateRealm(player, targetRealmId, config -> {
                            config.set("world.seed", null);
                            config.set("world.create-if-missing", true);
                        }, "clear world.seed");
                        openRealm(player, targetRealmId);
                    }
                    player.sendMessage("§aĐã bỏ seed tùy chỉnh.");
                    return;
                }
                long seed;
                try { seed = Long.parseLong(input); }
                catch (NumberFormatException exception) {
                    player.sendMessage("§cSeed phải là số nguyên 64-bit, ví dụ: §f123456789§c. Nhập lại hoặc !cancel.");
                    pendingWorldNames.put(player.getUniqueId(), realmId);
                    return;
                }
                if (startWorld) {
                    plugin.getConfig().set("rpg.onboarding.start-world.seed", seed);
                    plugin.getConfig().set("rpg.onboarding.start-world.create-if-missing", true);
                    savePluginConfig(player, "set rpg.onboarding.start-world.seed=" + seed);
                    player.sendMessage("§aĐã lưu seed. Thay đổi world sẽ được áp dụng khi khởi động lại server.");
                    openDashboard(player);
                } else {
                    updateRealm(player, targetRealmId, config -> {
                        config.set("world.seed", seed);
                        config.set("world.create-if-missing", true);
                    }, "set world.seed=" + seed);
                    player.sendMessage("§aĐã lưu seed. Thay đổi world sẽ được áp dụng khi khởi động lại server.");
                    openRealm(player, targetRealmId);
                }
                return;
            }
            if (!input.matches("[A-Za-z0-9_-]{1,64}")) {
                player.sendMessage("§cTên world không hợp lệ. Ví dụ: world_start. Hãy nhập lại hoặc !cancel.");
                pendingWorldNames.put(player.getUniqueId(), realmId);
                return;
            }
            if ("__start__".equals(realmId)) {
                String previousWorld = plugin.getConfig().getString("rpg.onboarding.start-world.name", "world_start");
                if (!previousWorld.equalsIgnoreCase(input)) {
                    plugin.getConfig().set("rpg.onboarding.start-world.tutorial-npc-id", null);
                    plugin.getConfig().set("rpg.onboarding.start-world.tutorial.dialog-id", null);
                    plugin.getConfig().set("rpg.onboarding.start-world.tutorial.auto-quest-id", null);
                    plugin.getLogger().info("[ServerSetup] Cleared start-world tutorial NPC binding after world rename: old="
                        + previousWorld + ", new=" + input);
                }
                plugin.getConfig().set("rpg.onboarding.start-world.name", input);
                plugin.getConfig().set("rpg.onboarding.start-world.create-if-missing", true);
                savePluginConfig(player, "rpg.onboarding.start-world.name=" + input);
                player.sendMessage("§aĐã lưu world bắt đầu §f" + input + "§a. World sẽ được tạo khi khởi động lại server.");
                openDashboard(player);
                return;
            }
            updateRealm(player, realmId, config -> {
                config.set("world-name", input);
                config.set("world.create-if-missing", true);
                config.set("world.environment", config.getString("world.environment", "NORMAL"));
            }, "set/create world=" + input);
            player.sendMessage("§aĐã lưu tên world. World mới sẽ được tạo khi server khởi động lại.");
            openRealm(player, realmId);
        });
    }

    private void openTutorialQuestSetup(Player player) {
        File file = new File(plugin.getDataFolder(), "quests/tutorial/first-steps.yml");
        YamlConfiguration quest = file.isFile() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        boolean enabled = quest.getBoolean("quest.enabled", false);
        String title = quest.getString("quest.title", "First Steps");
        String objectiveType = quest.getString("quest.objective.type", "visit-start-world-tutorial-npc");
        SetupHolder holder = new SetupHolder(player, "tutorial-quest", null, 0);
        Inventory menu = Bukkit.createInventory(holder, 54, "VnMine • Quest onboarding");
        holder.inventory = menu;
        menu.setItem(13, item(enabled ? Material.WRITABLE_BOOK : Material.BARRIER,
            enabled ? "§aFirst Steps đang bật" : "§cFirst Steps đang tắt / thiếu file", List.of(
                "§7Tên hiện tại: §f" + title,
                "§7Mục tiêu: §f" + tutorialObjectiveName(objectiveType),
                "§7Bấm mục First Step bên dưới để sửa tên, mô tả và mục tiêu.")));
        menu.setItem(22, item(Material.ENCHANTED_BOOK, "§eSửa First Step: §f" + title, List.of(
            "§7Mở trình sửa quest hướng dẫn first-steps.yml.",
            "§7Có thể đổi tên và cấu hình objective giống quest RPG.", "§eNhấn để chỉnh sửa.")));
        menu.setItem(29, item(enabled ? Material.REDSTONE_TORCH : Material.LEVER,
            enabled ? "§eTắt quest First Steps" : "§eBật quest First Steps", List.of(
                "§7Lưu trạng thái ngay và nạp lại quest manager.",
                "§7Tắt sẽ dừng tracking đang chạy; bật áp dụng khi quest được khởi tạo lại.",
                "§7Tiến trình đã lưu của người chơi không bị xóa.")));
        menu.setItem(33, item(Material.BOOK, "§bMở quản lý quest giao bởi NPC", List.of(
            "§7Mở /vnquest admin để tạo/sửa các quest RPG thường.",
            "§7Quest onboarding first-steps.yml là luồng riêng.")));
        menu.setItem(49, item(Material.ARROW, "§7Quay lại setup", List.of()));
        menu.setItem(53, item(Material.BARRIER, "§cĐóng / tiếp tục sau", List.of("§7Chạy lại /vninit bất cứ lúc nào.")));
        player.openInventory(menu);
    }

    private void openFirstStepsEditor(Player player) {
        File file = new File(plugin.getDataFolder(), "quests/tutorial/first-steps.yml");
        YamlConfiguration quest = file.isFile() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        String title = quest.getString("quest.title", "First Steps");
        String objectiveType = quest.getString("quest.objective.type", "visit-start-world-tutorial-npc");
        SetupHolder holder = new SetupHolder(player, "tutorial-quest-editor", null, 0);
        Inventory menu = Bukkit.createInventory(holder, 54, "Sửa First Step • " + title);
        holder.inventory = menu;
        menu.setItem(4, item(Material.WRITABLE_BOOK, "§6" + title, List.of(
            "§7ID hệ thống: §ffirst-steps", "§7Mô tả: §f" + quest.getString("quest.description", ""),
            "§7Mục tiêu: §f" + tutorialObjectiveName(objectiveType))));
        menu.setItem(20, item(Material.NAME_TAG, "§eĐổi tên nhiệm vụ", List.of(
            "§7Hiện tại: §f" + title, "§7Tên này hiển thị trong GUI và thông báo quest.",
            "§eNhấn rồi nhập tên mới trong chat.")));
        menu.setItem(22, item(Material.PAPER, "§eSửa mô tả quest", List.of(
            "§7Hiện tại: §f" + quest.getString("quest.description", ""),
            "§eNhấn rồi nhập mô tả mới trong chat.")));
        menu.setItem(24, item(Material.COMPASS, "§bSửa mục tiêu quest", List.of(
            "§7Đang chọn: §f" + tutorialObjectiveName(objectiveType),
            "§7Chọn NPC Tutorial world bắt đầu, NPC Tutorial đại lục, hoặc spawn đại lục.",
            "§eNhấn để chọn loại và đích objective.")));
        menu.setItem(30, item(Material.WRITABLE_BOOK, "§dSửa lời hướng dẫn khi nhận quest", List.of(
            "§7Hiện tại: §f" + quest.getString("quest.objective.start-message", ""),
            "§eNhấn rồi nhập nội dung mới trong chat.")));
        menu.setItem(32, item(Material.FIREWORK_STAR, "§aSửa thông báo hoàn thành", List.of(
            "§7Hiện tại: §f" + quest.getString("quest.objective.complete-message", ""),
            "§eNhấn rồi nhập nội dung mới trong chat.")));
        menu.setItem(49, item(Material.CRAFTING_TABLE, "§7Quay lại quest hướng dẫn", List.of()));
        menu.setItem(53, item(Material.BARRIER, "§cĐóng", List.of()));
        player.openInventory(menu);
    }

    private void openFirstStepsObjectiveMenu(Player player) {
        File file = new File(plugin.getDataFolder(), "quests/tutorial/first-steps.yml");
        YamlConfiguration quest = file.isFile() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        String current = quest.getString("quest.objective.type", "visit-start-world-tutorial-npc");
        SetupHolder holder = new SetupHolder(player, "tutorial-quest-objective", null, 0);
        Inventory menu = Bukkit.createInventory(holder, 54, "First Step • Chọn mục tiêu");
        holder.inventory = menu;
        String[] types = {"visit-start-world-tutorial-npc", "visit-tutorial-npc", "visit-realm-spawn"};
        Material[] icons = {Material.VILLAGER_SPAWN_EGG, Material.PLAYER_HEAD, Material.COMPASS};
        int[] slots = {20, 22, 24};
        for (int index = 0; index < types.length; index++) {
            String type = types[index];
            menu.setItem(slots[index], item(icons[index], (current.equals(type) ? "§a✓ " : "§e")
                + tutorialObjectiveName(type), List.of(
                    "§7" + tutorialObjectiveDescription(type),
                    current.equals(type) ? "§aĐang được chọn" : "§eNhấn để chọn mục tiêu.")));
            holder.choices.put(slots[index], type);
        }
        menu.setItem(49, item(Material.CRAFTING_TABLE, "§7Quay lại sửa First Step", List.of()));
        player.openInventory(menu);
    }

    private String tutorialObjectiveName(String type) {
        return switch (type == null ? "" : type) {
            case "visit-tutorial-npc" -> "Gặp NPC Tutorial của đại lục đã chọn";
            case "visit-realm-spawn" -> "Đến điểm spawn của đại lục đã chọn";
            default -> "Gặp NPC Tutorial tại world bắt đầu";
        };
    }

    private String tutorialObjectiveDescription(String type) {
        return switch (type) {
            case "visit-tutorial-npc" -> "Điểm đến lấy từ NPC Tutorial của đại lục người chơi chọn.";
            case "visit-realm-spawn" -> "Điểm đến lấy từ spawn của đại lục đã chọn.";
            default -> "Điểm đến lấy từ NPC Tutorial được gán cho world bắt đầu trong /vninit.";
        };
    }

    private void saveFirstStepsField(Player player, String path, String value) {
        File file = new File(plugin.getDataFolder(), "quests/tutorial/first-steps.yml");
        if (!file.isFile()) {
            player.sendMessage("§cKhông tìm thấy first-steps.yml; không thay đổi dữ liệu.");
            openFirstStepsEditor(player);
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().parseComments(true);
        try {
            yaml.load(file);
            yaml.set(path, value);
            saveAtomically(yaml, file);
            if (plugin.getTutorialQuestManager() == null || !plugin.getTutorialQuestManager().reloadDefinitionOnly()) {
                player.sendMessage("§cĐã lưu nhưng quest manager không nạp được cấu hình. Kiểm tra console.");
                plugin.getLogger().severe("[ServerSetup] first-steps.yml saved but tutorial quest reload failed: " + path);
                return;
            }
            plugin.getLogger().info("[ServerSetup] admin=" + player.getName() + ", action=edit-first-steps " + path);
            player.sendMessage("§aĐã lưu cấu hình First Step.");
            openFirstStepsEditor(player);
        } catch (IOException | InvalidConfigurationException | YAMLException exception) {
            plugin.getLogger().log(Level.SEVERE, "[ServerSetup] Could not edit first-steps.yml field " + path, exception);
            player.sendMessage("§cKhông thể lưu First Step. Kiểm tra console.");
            openFirstStepsEditor(player);
        }
    }

    private void promptTutorialQuestText(Player player, int slot) {
        String marker = switch (slot) {
            case 20 -> "__first_steps_title__";
            case 22 -> "__first_steps_description__";
            case 30 -> "__first_steps_start_message__";
            default -> "__first_steps_complete_message__";
        };
        String label = switch (slot) {
            case 20 -> "tên nhiệm vụ";
            case 22 -> "mô tả nhiệm vụ";
            case 30 -> "lời hướng dẫn khi bắt đầu";
            default -> "thông báo hoàn thành";
        };
        String example = switch (slot) {
            case 20 -> "ví dụ: §fKhởi đầu hành trình";
            case 22 -> "ví dụ: §fHãy tìm người hướng dẫn để bắt đầu.";
            case 30 -> "ví dụ: §fGặp NPC Tutorial để nhận chỉ dẫn.";
            default -> "ví dụ: §fBạn đã hoàn thành bước hướng dẫn đầu tiên.";
        };
        pendingWorldNames.put(player.getUniqueId(), marker);
        player.closeInventory();
        player.sendMessage("§6[First Step] §eNhập " + label + ": " + example
            + " §7Gõ §c!cancel §7để quay lại trình sửa.");
    }

    private void setFirstStepsObjective(Player player, String type) {
        if (!List.of("visit-start-world-tutorial-npc", "visit-tutorial-npc", "visit-realm-spawn").contains(type)) return;
        saveFirstStepsField(player, "quest.objective.type", type);
        openFirstStepsEditor(player);
    }

    private void openWorlds(Player player, String realmId, int page) {
        List<World> worlds = Bukkit.getWorlds().stream().sorted(Comparator.comparing(World::getName,
            String.CASE_INSENSITIVE_ORDER)).toList();
        int pages = Math.max(1, (worlds.size() + 26) / 27);
        int safePage = Math.max(0, Math.min(page, pages - 1));
        SetupHolder holder = new SetupHolder(player, "worlds", realmId, safePage);
        Inventory menu = Bukkit.createInventory(holder, 54, "VnMine • Chọn world " + (safePage + 1) + "/" + pages);
        holder.inventory = menu;
        for (int index = safePage * 27; index < Math.min((safePage + 1) * 27, worlds.size()); index++) {
            World world = worlds.get(index);
            int slot = WORLD_CONTENT_SLOTS[index - safePage * 27];
            holder.choices.put(slot, world.getName());
            menu.setItem(slot, item(Material.GRASS_BLOCK, "§b" + world.getName(), List.of(
                "§7Environment: §f" + world.getEnvironment().name(), "§eNhấn để gán world cho đại lục.")));
        }
        if (safePage > 0) menu.setItem(45, item(Material.ARROW, "§eTrang trước", List.of()));
        menu.setItem(48, item(Material.ANVIL, "§aNhập tên world / tạo world mới", List.of(
            "§7Tên world phải gồm chữ, số, _ hoặc -.",
            "§7Ví dụ: world_start", "§eNhấn rồi nhập tên trong chat; !cancel để hủy.")));
        menu.setItem(49, item(Material.ARROW, "§7Quay lại đại lục", List.of()));
        if (safePage + 1 < pages) menu.setItem(53, item(Material.ARROW, "§eTrang sau", List.of()));
        player.openInventory(menu);
    }

    private void openTutorialNpcs(Player player, String realmId, int page) {
        NpcManager npcManager = plugin.getNpcManager();
        boolean startWorldSelection = "__start__".equals(realmId);
        var realm = startWorldSelection || plugin.getRpgWorldManager() == null ? null
            : plugin.getRpgWorldManager().getRealmDefinition(realmId);
        String expectedWorld = startWorldSelection
            ? plugin.getConfig().getString("rpg.onboarding.start-world.name", "world_start")
            : realm == null ? "" : realm.worldName();
        List<String> npcs = npcManager == null ? List.of() : npcManager.getNpcIds().stream()
            .filter(id -> npcManager.getNpcType(id).filter("tutorial"::equalsIgnoreCase).isPresent())
            .filter(id -> npcManager.getNpcTarget(id).map(target -> target.world().equalsIgnoreCase(expectedWorld)).orElse(false))
            .sorted(String.CASE_INSENSITIVE_ORDER).toList();
        int pages = Math.max(1, (npcs.size() + 26) / 27);
        int safePage = Math.max(0, Math.min(page, pages - 1));
        SetupHolder holder = new SetupHolder(player, startWorldSelection ? "start-world-npcs" : "npcs", realmId, safePage);
        Inventory menu = Bukkit.createInventory(holder, 54, (startWorldSelection ? "NPC tutorial world bắt đầu " : "VnMine • NPC tutorial ")
            + (safePage + 1) + "/" + pages);
        holder.inventory = menu;
        for (int index = safePage * 27; index < Math.min((safePage + 1) * 27, npcs.size()); index++) {
            String npcId = npcs.get(index);
            int slot = WORLD_CONTENT_SLOTS[index - safePage * 27];
            String npcWorld = npcManager.getNpcTarget(npcId).map(NpcManager.NpcTarget::world).orElse("chưa nạp");
            String displayName = npcManager.getNpcDisplayName(npcId).orElse(npcId);
            String selectedNpcId = startWorldSelection
                ? plugin.getConfig().getString("rpg.onboarding.start-world.tutorial-npc-id", "")
                : realm == null ? "" : realm.tutorialNpcId();
            boolean selected = npcId.equalsIgnoreCase(selectedNpcId);
            holder.choices.put(slot, npcId);
            menu.setItem(slot, item(Material.VILLAGER_SPAWN_EGG,
                (selected ? "§a✓ " : "§d") + displayName, List.of(
                "§7ID: §f" + npcId, "§7Loại: §ftutorial",
                "§7World đang đặt: §f" + npcWorld,
                "§7World yêu cầu: §f" + expectedWorld,
                selected ? "§aĐang được gán" : "§eNhấn để gán làm NPC Tutorial.")));
        }
        if (npcs.isEmpty()) {
            menu.setItem(22, item(Material.NAME_TAG, "§cChưa có NPC loại tutorial", List.of(
                "§7Đóng GUI và tạo NPC bằng lệnh:",
                "§f/vnnpc create <id> tutorial",
                "§7Ví dụ: /vnnpc create tutorial-nhan-gioi tutorial",
                "§7Đặt NPC trong world §f" + expectedWorld + "§7, sau đó mở lại /vninit.")));
        }
        if (safePage > 0) menu.setItem(45, item(Material.ARROW, "§eTrang trước", List.of()));
        if (startWorldSelection) menu.setItem(47, item(Material.BARRIER, "§cBỏ chọn NPC tutorial world bắt đầu", List.of(
            "§7NPC hiện tại: §f" + plugin.getConfig().getString("rpg.onboarding.start-world.tutorial-npc-id", "Chưa chọn"),
            "§eNhấn để xóa NPC đã gán.")));
        else menu.setItem(47, item(Material.BARRIER, "§cBỏ chọn NPC Tutorial của đại lục", List.of(
            "§7Chỉ xóa liên kết khỏi đại lục; không xóa NPC.", "§eNhấn để bỏ gán.")));
        menu.setItem(49, item(Material.ARROW, startWorldSelection ? "§7Quay lại /vninit" : "§7Quay lại đại lục", List.of()));
        if (safePage + 1 < pages) menu.setItem(53, item(Material.ARROW, "§eTrang sau", List.of()));
        player.openInventory(menu);
    }

    @EventHandler
    public void onSetupMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !(event.getView().getTopInventory().getHolder() instanceof SetupHolder holder)) return;
        event.setCancelled(true);
        if (!holder.adminId.equals(player.getUniqueId())
                || (!player.hasPermission(PERMISSION) && !player.hasPermission("vnmine.command.npc.admin"))) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getRawSlot();
        if ("dashboard".equals(holder.type)) {
            if (slot == 45 && holder.page > 0) {
                openDashboard(player, holder.page - 1);
            } else if (slot == 53 && (holder.page + 1) * CONTENT_SLOTS.length < realms().size()) {
                openDashboard(player, holder.page + 1);
            } else if (slot == 36) {
                boolean enabled = plugin.getConfig().getBoolean("rpg.onboarding.enabled", true);
                plugin.getConfig().set("rpg.onboarding.enabled", !enabled);
                savePluginConfig(player, "rpg.onboarding.enabled=" + !enabled);
                openDashboard(player, holder.page);
            } else if (slot == 35) {
                Bukkit.dispatchCommand(player, "vnskillbook config");
            } else if (slot == 30) {
                pendingWorldNames.put(player.getUniqueId(), "__start__");
                player.closeInventory();
                player.sendMessage("§eNhập tên world an toàn cho người mới/reset. Ví dụ: §fworld_start§e. Gõ §f!cancel §eđể hủy.");
            } else if (slot == 28) {
                openTutorialNpcs(player, "__start__", 0);
            } else if (slot == 29) {
                if (plugin.getRegionProtectionManager() != null) plugin.getRegionProtectionManager().openStartWorldSettings(player);
            } else if (slot == 31) {
                String current = plugin.getConfig().getString("rpg.onboarding.start-world.environment", "NORMAL");
                String next = "NORMAL".equalsIgnoreCase(current) ? "NETHER"
                    : "NETHER".equalsIgnoreCase(current) ? "THE_END" : "NORMAL";
                plugin.getConfig().set("rpg.onboarding.start-world.environment", next);
                plugin.getConfig().set("rpg.onboarding.start-world.world-revision",
                    plugin.getConfig().getLong("rpg.onboarding.start-world.world-revision", 0L) + 1L);
                plugin.getConfig().set("rpg.onboarding.start-world.create-if-missing", true);
                savePluginConfig(player, "rpg.onboarding.start-world.environment=" + next);
                openDashboard(player);
            } else if (slot == 32) {
                pendingWorldNames.put(player.getUniqueId(), "__start_seed__");
                player.closeInventory();
                player.sendMessage("§eNhập seed số nguyên 64-bit cho world bắt đầu. Ví dụ: §f123456789§e. Gõ §f!clear §eđể seed tự sinh, §f!cancel §eđể hủy.");
            } else if (slot == 40) {
                openTutorialQuestSetup(player);
            } else if (slot == 33) {
                openCharacterCompletionSettings(player);
            } else if (slot == 34) {
                if (plugin.getCharacterSelectionManager() != null)
                    plugin.getCharacterSelectionManager().openRaceAdminMenu(player);
            } else if (slot == 38) {
                pendingWorldNames.put(player.getUniqueId(), "__start_display_name__");
                player.closeInventory();
                player.sendMessage("§eNhập tên hiển thị đại lục bắt đầu dùng trong quest. Ví dụ: §fNhân Giới§e. Gõ §f!cancel §eđể hủy.");
            } else if (slot == 27) {
                openWorldManager(player);
            } else {
                String realmId = holder.choices.get(slot);
                if (realmId != null) openRealm(player, realmId);
            }
            return;
        }
        if ("completion-config".equals(holder.type)) {
            if (slot == 49) openDashboard(player);
            else if (slot == 16) {
                String currentMode = plugin.getConfig().getString(
                    "rpg.onboarding.character-complete.teleport-mode", "REALM_SPAWN");
                boolean enabled = !plugin.getConfig().getBoolean(
                    "rpg.onboarding.character-complete.teleport-enabled", !"NONE".equalsIgnoreCase(currentMode));
                if (enabled && "NONE".equalsIgnoreCase(plugin.getConfig().getString(
                        "rpg.onboarding.character-complete.teleport-mode", "REALM_SPAWN"))) {
                    plugin.getConfig().set("rpg.onboarding.character-complete.teleport-mode", "REALM_SPAWN");
                }
                plugin.getConfig().set("rpg.onboarding.character-complete.teleport-enabled", enabled);
                savePluginConfig(player, "onboarding character-complete teleport-enabled=" + enabled);
                openCharacterCompletionSettings(player);
            }
            else if (slot == 10) {
                plugin.getConfig().set("rpg.onboarding.character-complete.teleport-enabled", true);
                saveCompletionSetting(player, "teleport-mode", "REALM_SPAWN");
            }
            else if (slot == 11) {
                plugin.getConfig().set("rpg.onboarding.character-complete.teleport-enabled", true);
                saveCompletionSetting(player, "teleport-mode", "START_WORLD");
            }
            else if (slot == 14) {
                plugin.getConfig().set("rpg.onboarding.character-complete.teleport-enabled", false);
                saveCompletionSetting(player, "teleport-mode", "NONE");
            }
            else if (slot == 12) {
                Location location = player.getLocation();
                var config = plugin.getConfig();
                String base = "rpg.onboarding.character-complete.location.";
                config.set(base + "world", location.getWorld().getName());
                config.set(base + "x", location.getX());
                config.set(base + "y", location.getY());
                config.set(base + "z", location.getZ());
                config.set(base + "yaw", location.getYaw());
                config.set(base + "pitch", location.getPitch());
                config.set("rpg.onboarding.character-complete.teleport-mode", "ADMIN_LOCATION");
                config.set("rpg.onboarding.character-complete.teleport-enabled", true);
                savePluginConfig(player, "set onboarding completion destination=" + location);
                openCharacterCompletionSettings(player);
            } else if (slot == 22) openCharacterCompletionQuestChoices(player, 0);
            return;
        }
        if ("completion-quests".equals(holder.type)) {
            if (slot == 49) openCharacterCompletionSettings(player);
            else if (slot == 46 && holder.page > 0)
                openCharacterCompletionQuestChoices(player, holder.page - 1);
            else if (slot == 50) openCharacterCompletionQuestChoices(player, holder.page + 1);
            else if (slot == 45 || holder.choices.containsKey(slot)) {
                String questId = slot == 45 ? null : holder.choices.get(slot);
                if (questId != null && (plugin.getQuestCatalogManager() == null
                        || !plugin.getQuestCatalogManager().isEnabledQuest(questId))) {
                    player.sendMessage("§cQuest đã bị tắt hoặc xóa; hãy chọn lại.");
                    openCharacterCompletionQuestChoices(player, 0);
                    return;
                }
                plugin.getConfig().set("rpg.onboarding.character-complete.default-quest-id", questId);
                savePluginConfig(player, "set onboarding default quest=" + questId);
                openCharacterCompletionSettings(player);
            }
            return;
        }
        if ("world-manager".equals(holder.type)) {
            if (slot == 49) openDashboard(player);
            else if (slot == 45) promptNewRealm(player);
            else {
                String realmId = holder.choices.get(slot);
                if (realmId != null) openRealm(player, realmId);
            }
            return;
        }
        if ("confirm-environment".equals(holder.type)) {
            if (slot == 11) {
                var realm = plugin.getRpgWorldManager().getRealmDefinition(holder.realmId);
                if (realm != null && hasOtherRealmUsingWorld(holder.realmId, realm.worldName())) {
                    player.sendMessage("§cKhông thể đổi environment vì world này còn được start-world hoặc đại lục khác sử dụng. Hãy gán world riêng trước.");
                    openRealm(player, holder.realmId);
                    return;
                }
                String selectedEnvironment = holder.raceId;
                updateRealm(player, holder.realmId, config -> {
                    config.set("world.environment", selectedEnvironment);
                    config.set("world.revision", config.getLong("world.revision", 0L) + 1L);
                    config.set("world.create-if-missing", true);
                }, "confirmed environment=" + selectedEnvironment);
                player.sendMessage("§eĐã xác nhận. World cũ sẽ được archive, world §f" + selectedEnvironment
                    + " §esẽ được tạo ở lần khởi động kế tiếp.");
                openRealm(player, holder.realmId);
            } else if (slot == 15 || slot == 18) openRealm(player, holder.realmId);
            return;
        }
        if ("confirm-delete-realm".equals(holder.type)) {
            if (slot == 11) deleteRealmDefinition(player, holder.realmId);
            else if (slot == 15 || slot == 18) openRealm(player, holder.realmId);
            return;
        }
        if ("tutorial-quest".equals(holder.type)) {
            if (slot == 49) openDashboard(player);
            else if (slot == 53) player.closeInventory();
            else if (slot == 22) openFirstStepsEditor(player);
            else if (slot == 29) toggleTutorialQuest(player);
            else if (slot == 33) {
                Bukkit.dispatchCommand(player, "vnquest admin");
            }
            return;
        }
        if ("tutorial-quest-editor".equals(holder.type)) {
            if (slot == 49) openTutorialQuestSetup(player);
            else if (slot == 53) player.closeInventory();
            else if (slot == 24) openFirstStepsObjectiveMenu(player);
            else if (slot == 20 || slot == 22 || slot == 30 || slot == 32)
                promptTutorialQuestText(player, slot);
            return;
        }
        if ("tutorial-quest-objective".equals(holder.type)) {
            if (slot == 49) openFirstStepsEditor(player);
            else {
                String type = holder.choices.get(slot);
                if (type != null) setFirstStepsObjective(player, type);
            }
            return;
        }
        if ("tutorial-config".equals(holder.type)) {
            if (slot == 49) openRealm(player, holder.realmId);
            else if (slot == 11 && plugin.getQuestDialogManager() != null) {
                File file = findRealmFile(holder.realmId);
                String current = file == null ? null : YamlConfiguration.loadConfiguration(file).getString("tutorial.dialog-id");
                plugin.getQuestDialogManager().openSelector(player, current, selected -> {
                    updateRealm(player, holder.realmId, config -> config.set("tutorial.dialog-id", selected),
                        "set tutorial dialog=" + selected);
                    openTutorialNpcEditor(player, plugin.getRpgWorldManager().getRealmDefinition(holder.realmId).tutorialNpcId());
                });
            } else if (slot == 15) openTutorialQuestChoices(player, holder.realmId);
            return;
        }
        if ("start-tutorial-config".equals(holder.type)) {
            if (slot == 49) openDashboard(player);
            else if (slot == 11 && plugin.getQuestDialogManager() != null) {
                String current = plugin.getConfig().getString("rpg.onboarding.start-world.tutorial.dialog-id");
                plugin.getQuestDialogManager().openSelector(player, current, selected -> {
                    saveStartWorldTutorialSetting(player, "dialog-id", selected);
                    String npcId = plugin.getConfig().getString("rpg.onboarding.start-world.tutorial-npc-id", "");
                    openTutorialNpcEditor(player, npcId);
                });
            } else if (slot == 15) openStartTutorialQuestChoices(player);
            return;
        }
        if ("start-tutorial-quests".equals(holder.type)) {
            if (slot == 49) {
                String npcId = plugin.getConfig().getString("rpg.onboarding.start-world.tutorial-npc-id", "");
                openTutorialNpcEditor(player, npcId);
            } else if (slot == 45 || holder.choices.containsKey(slot)) {
                saveStartWorldTutorialSetting(player, "auto-quest-id", slot == 45 ? null : holder.choices.get(slot));
                String npcId = plugin.getConfig().getString("rpg.onboarding.start-world.tutorial-npc-id", "");
                openTutorialNpcEditor(player, npcId);
            }
            return;
        }
        if ("tutorial-quests".equals(holder.type)) {
            if (slot == 49) {
                var realm = plugin.getRpgWorldManager().getRealmDefinition(holder.realmId);
                if (realm != null) openTutorialNpcEditor(player, realm.tutorialNpcId());
            } else if (slot == 45 || holder.choices.containsKey(slot)) {
                String questId = slot == 45 ? null : holder.choices.get(slot);
                updateRealm(player, holder.realmId, config -> config.set("tutorial.auto-quest-id", questId),
                    "set tutorial auto quest=" + questId);
                var realm = plugin.getRpgWorldManager().getRealmDefinition(holder.realmId);
                if (realm != null) openTutorialNpcEditor(player, realm.tutorialNpcId());
            }
            return;
        }
        if ("availability-races".equals(holder.type)) {
            File file = findRealmFile(holder.realmId);
            YamlConfiguration yaml = file == null ? new YamlConfiguration() : YamlConfiguration.loadConfiguration(file);
            if (slot == 49) { openRealm(player, holder.realmId); return; }
            if (slot == 4) {
                boolean enabled = yaml.getBoolean("character-availability.enabled", false);
                updateRealm(player, holder.realmId, config -> {
                    config.set("character-availability.enabled", !enabled);
                    if (!enabled) {
                        var characters = plugin.getCharacterSelectionManager();
                        List<String> raceIds = characters == null ? List.of() : characters.getConfiguredRaceDefinitions().stream()
                            .map(CharacterSelectionManager.RaceDefinition::id).toList();
                        config.set("character-availability.races", raceIds);
                        if (characters != null) for (String raceId : raceIds) {
                            config.set("character-availability.classes." + raceId,
                                characters.getClassDefinitions(raceId).stream()
                                    .map(CharacterSelectionManager.ClassDefinition::id).toList());
                        }
                    }
                }, "toggle realm character availability");
                openRealmRaces(player, holder.realmId);
                return;
            }
            String raceId = holder.choices.get(slot);
            if (raceId != null) {
                if (event.getClick().isRightClick()) {
                    boolean selected = !yaml.getBoolean("character-availability.enabled", false)
                        || yaml.getStringList("character-availability.races").contains(raceId);
                    toggleAvailability(player, holder.realmId, "character-availability.races", raceId, selected);
                    openRealmRaces(player, holder.realmId);
                } else openRealmClasses(player, holder.realmId, raceId);
            }
            return;
        }
        if ("availability-classes".equals(holder.type)) {
            if (slot == 49) { openRealmRaces(player, holder.realmId); return; }
            String classId = holder.choices.get(slot);
            if (classId != null) {
                String path = "character-availability.classes." + holder.raceId;
                File file = findRealmFile(holder.realmId);
                YamlConfiguration yaml = file == null ? new YamlConfiguration() : YamlConfiguration.loadConfiguration(file);
                boolean selected = !yaml.getBoolean("character-availability.enabled", false)
                    || yaml.getStringList(path).contains(classId);
                toggleAvailability(player, holder.realmId, path, classId, selected);
                openRealmClasses(player, holder.realmId, holder.raceId);
            }
            return;
        }
        if ("realm".equals(holder.type)) {
            switch (slot) {
                case 10 -> {
                    updateRealm(player, holder.realmId, config -> {
                        String path = "starting-selectable";
                        config.set(path, !config.getBoolean(path, false));
                    }, "toggle starting-selectable");
                    openRealm(player, holder.realmId);
                }
                case 11 -> {
                    pendingWorldNames.put(player.getUniqueId(), "__edit_display__:" + holder.realmId);
                    player.closeInventory();
                    player.sendMessage("§eNhập tên hiển thị mới cho §f" + holder.realmId
                        + "§e. Ví dụ: §fMa Giới§e. §c!cancel §eđể hủy.");
                }
                case 13 -> {
                    pendingWorldNames.put(player.getUniqueId(), "__edit_id__:" + holder.realmId);
                    player.closeInventory();
                    player.sendMessage("§eNhập ID mới cho đại lục §f" + holder.realmId
                        + "§e, ví dụ: §fma-gioi§e. ID cũ được lưu làm bí danh. §c!cancel §eđể hủy.");
                }
                case 12 -> openWorlds(player, holder.realmId, 0);
                case 14 -> openTutorialNpcs(player, holder.realmId, 0);
                case 16 -> {
                    openEnvironmentConfirmation(player, holder.realmId);
                }
                case 18 -> openRealmRaces(player, holder.realmId);
                case 20 -> {
                    var realm = plugin.getRpgWorldManager().getRealmDefinition(holder.realmId);
                    if (realm != null) openTutorialNpcEditor(player, realm.tutorialNpcId());
                }
                case 22 -> {
                    pendingWorldNames.put(player.getUniqueId(), "__seed__:" + holder.realmId);
                    player.closeInventory();
                    player.sendMessage("§eNhập seed số nguyên 64-bit cho world đại lục. Ví dụ: §f-987654321§e. Gõ §f!clear §eđể bỏ seed tùy chỉnh, §f!cancel §eđể hủy.");
                }
                case 24 -> openDeleteConfirmation(player, holder.realmId);
                case 26 -> {
                    pendingWorldNames.put(player.getUniqueId(), "__edit_world__:" + holder.realmId);
                    player.closeInventory();
                    player.sendMessage("§eNhập tên world cho đại lục §f" + holder.realmId
                        + "§e, ví dụ: §fworld_magioi§e. World tạo sau restart. §c!cancel §eđể hủy.");
                }
                case 28 -> saveCurrentSpawn(player, holder.realmId);
                case 30 -> {
                    updateRealm(player, holder.realmId, config -> {
                        config.set("spawn.use-world-spawn", true);
                        config.set("spawn.x", null);
                        config.set("spawn.y", null);
                        config.set("spawn.z", null);
                    }, "use world spawn");
                    openRealm(player, holder.realmId);
                }
                case 49 -> openDashboard(player);
                case 53 -> player.closeInventory();
                default -> { }
            }
            return;
        }
        if ("worlds".equals(holder.type)) {
            if (slot == 49) openRealm(player, holder.realmId);
            else if (slot == 45 && holder.page > 0) openWorlds(player, holder.realmId, holder.page - 1);
            else if (slot == 53 && (holder.page + 1) * 27 < Bukkit.getWorlds().size()) openWorlds(player, holder.realmId, holder.page + 1);
            else if (slot == 48) {
                pendingWorldNames.put(player.getUniqueId(), holder.realmId);
                player.closeInventory();
                player.sendMessage("§eNhập tên world để realm sử dụng. Ví dụ: §fworld_start§e. World chưa tồn tại sẽ được tạo sau khi khởi động lại; gõ §f!cancel §eđể hủy.");
            }
            else {
                String world = holder.choices.get(slot);
                if (world != null) {
                    updateRealm(player, holder.realmId, config -> {
                        config.set("world-name", world);
                        config.set("world.create-if-missing", true);
                    }, "set existing world=" + world);
                    openRealm(player, holder.realmId);
                }
            }
            return;
        }
        if ("start-world-npcs".equals(holder.type)) {
            if (slot == 49) openDashboard(player);
            else if (slot == 45 && holder.page > 0) openTutorialNpcs(player, "__start__", holder.page - 1);
            else if (slot == 53) openTutorialNpcs(player, "__start__", holder.page + 1);
            else if (slot == 47) {
                plugin.getConfig().set("rpg.onboarding.start-world.tutorial-npc-id", null);
                plugin.getConfig().set("rpg.onboarding.start-world.tutorial.dialog-id", null);
                plugin.getConfig().set("rpg.onboarding.start-world.tutorial.auto-quest-id", null);
                savePluginConfig(player, "clear start-world tutorial NPC");
                openDashboard(player);
            } else {
                String npcId = holder.choices.get(slot);
                if (npcId != null) {
                    plugin.getConfig().set("rpg.onboarding.start-world.tutorial-npc-id", npcId);
                    savePluginConfig(player, "set start-world tutorial NPC=" + npcId);
                    openDashboard(player);
                }
            }
            return;
        }
        if ("npcs".equals(holder.type)) {
            if (slot == 49) openRealm(player, holder.realmId);
            else if (slot == 45 && holder.page > 0) openTutorialNpcs(player, holder.realmId, holder.page - 1);
            else if (slot == 53) openTutorialNpcs(player, holder.realmId, holder.page + 1);
            else if (slot == 47) {
                updateRealm(player, holder.realmId, config -> config.set("tutorial-npc-id", null),
                    "clear tutorial-npc selection");
                openRealm(player, holder.realmId);
            }
            else {
                String npcId = holder.choices.get(slot);
                if (npcId != null) {
                    updateRealm(player, holder.realmId, config -> config.set("tutorial-npc-id", npcId), "set tutorial-npc=" + npcId);
                    openRealm(player, holder.realmId);
                }
            }
        }
    }

    private void saveCurrentSpawn(Player player, String realmId) {
        Location location = player.getLocation();
        updateRealm(player, realmId, config -> {
            config.set("world-name", location.getWorld().getName());
            config.set("world.create-if-missing", true);
            config.set("spawn.use-world-spawn", false);
            config.set("spawn.x", location.getX());
            config.set("spawn.y", location.getY());
            config.set("spawn.z", location.getZ());
            config.set("spawn.yaw", location.getYaw());
            config.set("spawn.pitch", location.getPitch());
        }, "save spawn world=" + location.getWorld().getName() + " x=" + location.getBlockX()
            + " y=" + location.getBlockY() + " z=" + location.getBlockZ());
        openRealm(player, realmId);
    }

    private void updateRealm(Player player, String realmId, ConfigUpdate update, String action) {
        File file = findRealmFile(realmId);
        if (file == null) {
            player.sendMessage("§cKhông tìm thấy file cấu hình cho đại lục " + realmId + ".");
            plugin.getLogger().warning("[ServerSetup] No world definition file found for realm=" + realmId);
            return;
        }
        YamlConfiguration config = new YamlConfiguration();
        config.options().parseComments(true);
        try {
            config.load(file);
            update.apply(config);
            saveAtomically(config, file);
            if (!plugin.getRpgWorldManager().reload()) {
                player.sendMessage("§cĐã lưu file nhưng nạp lại catalog đại lục thất bại. Kiểm tra console: " + file.getName());
                plugin.getLogger().severe("[ServerSetup] Saved realm config but reload failed: realm=" + realmId + ", file=" + file);
                return;
            }
            plugin.getLogger().info("[ServerSetup] admin=" + player.getName() + ", realm=" + realmId + ", action=" + action);
            player.sendMessage("§aĐã lưu thay đổi cho §e" + realmId + "§a. Cấu hình đã được nạp lại.");
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().log(Level.SEVERE, "[ServerSetup] Could not update realm=" + realmId + ".", exception);
            player.sendMessage("§cKhông thể lưu cấu hình đại lục. Dữ liệu cũ vẫn được giữ nguyên nếu thao tác ghi thất bại.");
        }
    }

    private void renameRealmId(Player player, String oldId, String newId) {
        var worldManager = plugin.getRpgWorldManager();
        if (oldId.equals(newId)) { player.sendMessage("§eID không thay đổi."); openRealm(player, oldId); return; }
        if (worldManager.getRealmDefinition(newId) != null) {
            player.sendMessage("§cID này đang được dùng hoặc là bí danh của một đại lục. Nhập ID khác.");
            pendingWorldNames.put(player.getUniqueId(), "__edit_id__:" + oldId);
            return;
        }
        File file = findRealmFile(oldId);
        if (file == null) { player.sendMessage("§cKhông tìm thấy file cấu hình đại lục."); openWorldManager(player); return; }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().parseComments(true);
        try {
            yaml.load(file);
            String originalId = yaml.getString("id", oldId);
            yaml.set("id", newId);
            saveAtomically(yaml, file);
            if (!worldManager.saveRealmIdAlias(oldId, newId)) {
                yaml.set("id", originalId);
                saveAtomically(yaml, file);
                player.sendMessage("§cKhông lưu được bí danh ID; thay đổi đã được hoàn tác.");
                openRealm(player, oldId);
                return;
            }
            if (!worldManager.reload()) {
                yaml.set("id", originalId);
                saveAtomically(yaml, file);
                worldManager.removeRealmIdAlias(oldId);
                worldManager.reload();
                player.sendMessage("§cKhông nạp được ID mới; thay đổi đã được hoàn tác.");
                openRealm(player, oldId);
                return;
            }
            plugin.getLogger().info("[ServerSetup] admin=" + player.getName() + ", action=rename-realm-id, old="
                + oldId + ", new=" + newId);
            player.sendMessage("§aĐã đổi ID từ §f" + oldId + " §asang §f" + newId
                + "§a. ID cũ được giữ làm bí danh để profile/onboarding đã lưu tiếp tục nhận diện đại lục.");
            openRealm(player, newId);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().log(Level.SEVERE, "[ServerSetup] Could not rename realm ID " + oldId + " -> " + newId, exception);
            player.sendMessage("§cKhông thể đổi ID đại lục. Xem console.");
            openRealm(player, oldId);
        }
    }

    private File findRealmFile(String realmId) {
        realmId = plugin.getRpgWorldManager().resolveRealmId(realmId);
        File folder = new File(plugin.getDataFolder(), "worlds");
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return null;
        for (File file : files) {
            YamlConfiguration config = new YamlConfiguration();
            try {
                config.load(file);
                if (realmId.equals(config.getString("id"))) return file;
            } catch (IOException | InvalidConfigurationException exception) {
                plugin.getLogger().log(Level.WARNING, "[ServerSetup] Ignoring unreadable realm file " + file.getName(), exception);
            }
        }
        return null;
    }

    private void saveAtomically(YamlConfiguration config, File target) throws IOException {
        File temp = new File(target.getParentFile(), target.getName() + ".vninit.tmp");
        config.save(temp);
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void savePluginConfig(Player player, String action) {
        try {
            plugin.saveConfig();
            plugin.getLogger().info("[ServerSetup] admin=" + player.getName() + ", action=" + action);
            player.sendMessage("§aĐã lưu cấu hình onboarding.");
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "[ServerSetup] Could not save config.yml.", exception);
            player.sendMessage("§cKhông thể lưu config.yml. Kiểm tra console.");
        }
    }

    private void saveStartWorldTutorialSetting(Player player, String field, String value) {
        String path = "rpg.onboarding.start-world.tutorial." + field;
        plugin.getConfig().set(path, value);
        savePluginConfig(player, "set " + path + "=" + value);
    }

    private void toggleTutorialQuest(Player player) {
        File file = new File(plugin.getDataFolder(), "quests/tutorial/first-steps.yml");
        if (!file.isFile()) {
            player.sendMessage("§cKhông tìm thấy first-steps.yml; không thay đổi dữ liệu.");
            return;
        }
        YamlConfiguration config = new YamlConfiguration();
        config.options().parseComments(true);
        try {
            config.load(file);
            boolean enabled = !config.getBoolean("quest.enabled", false);
            config.set("quest.enabled", enabled);
            saveAtomically(config, file);
            if (!plugin.getTutorialQuestManager().reloadDefinitionOnly()) {
                player.sendMessage("§cĐã lưu nhưng quest manager không nạp lại được. Kiểm tra console.");
                plugin.getLogger().severe("[ServerSetup] Saved tutorial quest enabled=" + enabled + " but reload failed.");
                return;
            }
            plugin.getLogger().info("[ServerSetup] admin=" + player.getName() + ", action=tutorial-quest-enabled=" + enabled);
            player.sendMessage("§aĐã " + (enabled ? "bật" : "tắt") + " quest First Steps và nạp lại cấu hình.");
            openTutorialQuestSetup(player);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().log(Level.SEVERE, "[ServerSetup] Could not update first-steps.yml.", exception);
            player.sendMessage("§cKhông thể lưu trạng thái quest tutorial. Xem console để biết chi tiết.");
        }
    }

    private List<HumanRealmDefinitionLoader.HumanRealmDefinition> realms() {
        if (plugin.getRpgWorldManager() == null) return List.of();
        return plugin.getRpgWorldManager().getDefinitions().values().stream()
            .sorted(Comparator.comparing(HumanRealmDefinitionLoader.HumanRealmDefinition::displayName,
                String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private RealmStatus status(HumanRealmDefinitionLoader.HumanRealmDefinition realm) {
        boolean worldReady = Bukkit.getWorld(realm.worldName()) != null;
        Optional<RpgWorldManager.ResolvedSpawn> spawn = plugin.getRpgWorldManager().resolveSpawn(realm.id());
        boolean spawnReady = worldReady && spawn.isPresent() && spawn.get().location().getWorld() != null
            && spawn.get().location().getWorld().getName().equals(realm.worldName());
        NpcManager npcManager = plugin.getNpcManager();
        boolean npcReady = npcManager != null
            && npcManager.getNpcType(realm.tutorialNpcId()).filter("tutorial"::equalsIgnoreCase).isPresent()
            && npcManager.getNpcTarget(realm.tutorialNpcId()).map(target -> target.world().equals(realm.worldName())).orElse(false);
        return new RealmStatus(realm.startingSelectable(), worldReady, spawnReady, npcReady,
            realm.startingSelectable() && worldReady && spawnReady);
    }

    private int starterBookMappingCount() {
        File file = new File(plugin.getDataFolder(), "starter-skill-books.yml");
        if (!file.isFile()) return 0;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        var races = config.getConfigurationSection("starter-books");
        if (races == null) return 0;
        return races.getKeys(false).stream().mapToInt(race -> {
            var classes = races.getConfigurationSection(race);
            return classes == null ? 0 : classes.getKeys(false).size();
        }).sum();
    }

    private boolean tutorialQuestEnabled() {
        File file = new File(plugin.getDataFolder(), "quests/tutorial/first-steps.yml");
        if (!file.isFile()) return false;
        return YamlConfiguration.loadConfiguration(file).getBoolean("quest.enabled", false);
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(LegacyText.translate(name));
        meta.setLore(lore.stream().map(LegacyText::translate).toList());
        item.setItemMeta(meta);
        return item;
    }

    private record RealmStatus(boolean starting, boolean worldReady, boolean spawnReady, boolean npcReady, boolean ready) { }
    private record RealmDraft(String id, String displayName) { }

    @FunctionalInterface
    private interface ConfigUpdate {
        void apply(YamlConfiguration config);
    }

    private static final class SetupHolder implements InventoryHolder {
        private final java.util.UUID adminId;
        private final String type;
        private final String realmId;
        private final int page;
        private String raceId;
        private final Map<Integer, String> choices = new HashMap<>();
        private Inventory inventory;

        private SetupHolder(Player admin, String type, String realmId, int page) {
            this.adminId = admin.getUniqueId();
            this.type = type;
            this.realmId = realmId;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
