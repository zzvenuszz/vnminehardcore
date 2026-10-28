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

/** Guided, resumable first-run setup for the RPG onboarding configuration. */
public final class ServerSetupWizardManager implements Listener, CommandExecutor, TabCompleter {
    private static final String PERMISSION = "vnmine.command.init";
    private static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43
    };

    private final VnMineHardcore plugin;
    private final java.util.Map<UUID, String> pendingWorldNames = new java.util.concurrent.ConcurrentHashMap<>();

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
                    status.npcReady() ? "§aNPC tutorial: " + realm.tutorialNpcId() : "§cNPC tutorial chưa hợp lệ/đúng world",
                    status.spawnReady() ? "§aĐiểm bắt đầu hợp lệ" : "§cĐiểm bắt đầu chưa hợp lệ",
                    "", "§eNhấn để cấu hình từng bước.")));
        }
        boolean onboardingEnabled = plugin.getConfig().getBoolean("rpg.onboarding.enabled", true);
        if (safePage > 0) menu.setItem(45, item(Material.ARROW, "§eĐại lục trước", List.of()));
        menu.setItem(46, item(onboardingEnabled ? Material.LIME_DYE : Material.GRAY_DYE,
            "§eOnboarding người mới: " + (onboardingEnabled ? "§aBẬT" : "§cTẮT"), List.of(
                "§7Bật quy trình chọn đại lục → gặp NPC → chọn race/class.",
                "§7Nhấn để đổi; thay đổi được lưu ngay vào config.yml.")));
        int starterBookMappings = starterBookMappingCount();
        menu.setItem(47, item(starterBookMappings > 0 ? Material.ENCHANTED_BOOK : Material.BOOK,
            (starterBookMappings > 0 ? "§d" : "§c") + "Sách kỹ năng khởi đầu", List.of(
            starterBookMappings > 0 ? "§aĐã có " + starterBookMappings + " mapping race/class." : "§cChưa có mapping sách skill khởi đầu.",
            "§7Gán sách theo chủng tộc và class.",
            "§7Dùng /vnskillbook config để mở GUI hiện có.",
            "§eThiết lập này quyết định sách được tặng sau khi chọn class.")));
        menu.setItem(48, item(Material.OBSIDIAN, "§bWorld bắt đầu/reset", List.of(
            "§7Hiện tại: §f" + plugin.getConfig().getString("rpg.onboarding.start-world.name", "world_start"),
            "§7World này được tạo tự động nếu chưa tồn tại.", "§eNhấn để nhập tên world.")));
        menu.setItem(50, item(Material.COMPASS, "§bMôi trường world bắt đầu: §f"
            + plugin.getConfig().getString("rpg.onboarding.start-world.environment", "NORMAL"), List.of(
                "§7Nhấn để đổi NORMAL → NETHER → THE_END.", "§7Áp dụng khi world được tạo mới.")));
        boolean tutorialQuestEnabled = tutorialQuestEnabled();
        menu.setItem(51, item(tutorialQuestEnabled ? Material.WRITABLE_BOOK : Material.BARRIER,
            (tutorialQuestEnabled ? "§b" : "§c") + "Quest RPG và quest hướng dẫn", List.of(
            tutorialQuestEnabled ? "§afirst-steps.yml đang bật." : "§cfirst-steps.yml chưa có hoặc đang tắt.",
            "§7Quest hướng dẫn first-steps.yml tự bắt đầu sau khi giao sách skill.",
            "§7Quest hướng dẫn tự chọn đại lục khớp homeWorld của người chơi.",
            "§7Dùng /vnquest admin để quản lý các quest RPG do NPC giao.",
            "§7File first-steps.yml: plugins/VnMineHardcore/quests/tutorial/.")));
        menu.setItem(49, item(Material.BARRIER, "§cĐóng / tiếp tục sau", List.of(
            "§7Mọi thay đổi đã xác nhận đều được lưu ngay.",
            "§7Thoát menu bất cứ lúc nào; chạy lại /vninit để tiếp tục.")));
        if (safePage + 1 < pages) menu.setItem(53, item(Material.ARROW, "§eĐại lục tiếp", List.of()));
        if (realms.isEmpty()) {
            menu.setItem(22, item(Material.BARRIER, "§cKhông tìm thấy cấu hình đại lục", List.of(
                "§7Kiểm tra plugins/VnMineHardcore/worlds/*.yml và console.")));
        }
        player.openInventory(menu);
    }

    private void openRealm(Player player, String realmId) {
        var realm = plugin.getRpgWorldManager().getDefinitions().get(realmId);
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
        menu.setItem(12, item(status.worldReady() ? Material.GRASS_BLOCK : Material.OBSIDIAN,
            "§bWorld chính: §f" + realm.worldName(), List.of(
                "§7Có thể chọn world đang nạp hoặc nhập world mới.",
                "§7World mới sẽ được tạo sau khi lưu và khởi động lại.",
                "§eNhấn để chọn world đã có.")));
        menu.setItem(14, item(status.npcReady() ? Material.VILLAGER_SPAWN_EGG : Material.NAME_TAG,
            "§dNPC hướng dẫn: §f" + realm.tutorialNpcId(), List.of(
                "§7Chọn NPC runtime có loại tutorial trong world chính.",
                "§7NPC phải được tạo và đặt trước khi chọn.",
                "§7Ví dụ tạo: /vnnpc create tutorial-nhan-gioi tutorial",
                "§eNhấn để chọn NPC tutorial.")));
        menu.setItem(20, item(Material.WRITABLE_BOOK, "§dDialog và quest tutorial", List.of(
            "§7Chọn hội thoại chạy trong action bar.",
            "§7Chọn quest giao tự động sau khi hội thoại kết thúc.",
            "§7Được chỉnh tại NPC bằng shift + chuột phải.", "§eNhấn để cấu hình.")));
        menu.setItem(16, item(Material.COMPASS,
            "§bMôi trường: §f" + worldEnvironment(realm.id()), List.of(
                "§7Nhấn để đổi NORMAL → NETHER → THE_END.",
                "§7Áp dụng khi world được plugin tạo mới.",
                "§eĐây là thiết lập world cho realm này.")));
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
            "§7Chọn quest-dialog dùng khi người chơi gặp NPC.", "§7Có thể tạo/sửa nội dung bằng /vnquestdialog admin.",
            "§eNhấn để chọn.")));
        menu.setItem(15, item(Material.ENCHANTED_BOOK, "§bQuest tự giao: §f" + (questId == null ? "Chưa chọn" : questId), List.of(
            "§7Chỉ quest có giver NPC ID đúng là §f" + npcId + " §7mới được liệt kê.",
            "§7Quest sẽ được giao sau khi hội thoại tutorial kết thúc.", "§eNhấn để chọn.")));
        menu.setItem(49, item(Material.ARROW, "§7Quay lại cấu hình đại lục", List.of()));
        player.openInventory(menu);
    }

    private void openTutorialQuestChoices(Player player, String realmId) {
        var realm = plugin.getRpgWorldManager().getDefinitions().get(realmId);
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

    private String worldEnvironment(String realmId) {
        File file = findRealmFile(realmId);
        return file == null ? "NORMAL" : YamlConfiguration.loadConfiguration(file)
            .getString("world.environment", "NORMAL");
    }

    private boolean realmAvailabilityEnabled(String realmId) {
        File file = findRealmFile(realmId);
        return file != null && YamlConfiguration.loadConfiguration(file)
            .getBoolean("character-availability.enabled", false);
    }

    private void openRealmRaces(Player player, String realmId) {
        var characterManager = plugin.getCharacterSelectionManager();
        var races = characterManager == null ? List.<CharacterSelectionManager.RaceDefinition>of()
            : characterManager.getRaceDefinitions();
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
        var race = characterManager == null ? null : characterManager.getRaceDefinitions().stream()
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
                    List<String> raceIds = characters.getRaceDefinitions().stream()
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
                player.sendMessage("§eĐã hủy nhập tên world.");
                if ("__start__".equals(realmId)) openDashboard(player);
                else openWorlds(player, realmId, 0);
                return;
            }
            if (!input.matches("[A-Za-z0-9_-]{1,64}")) {
                player.sendMessage("§cTên world không hợp lệ. Ví dụ: world_start. Hãy nhập lại hoặc !cancel.");
                pendingWorldNames.put(player.getUniqueId(), realmId);
                return;
            }
            if ("__start__".equals(realmId)) {
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
        SetupHolder holder = new SetupHolder(player, "tutorial-quest", null, 0);
        Inventory menu = Bukkit.createInventory(holder, 54, "VnMine • Quest onboarding");
        holder.inventory = menu;
        menu.setItem(13, item(enabled ? Material.WRITABLE_BOOK : Material.BARRIER,
            enabled ? "§aFirst Steps đang bật" : "§cFirst Steps đang tắt / thiếu file", List.of(
                "§7Người chơi dùng /start; quest dẫn tới NPC Tutorial của đại lục.",
                "§7Hồ sơ cũ: bắt đầu sau khi chọn đại lục; người mới: sau khi chọn class và nhận sách.",
                "§7Chi tiết phần thưởng, nội dung và tracking ở:",
                "§fplugins/VnMineHardcore/quests/tutorial/first-steps.yml")));
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
            int slot = CONTENT_SLOTS[index - safePage * 27];
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
        List<String> npcs = npcManager == null ? List.of() : npcManager.getNpcIds().stream()
            .filter(id -> npcManager.getNpcType(id).filter("tutorial"::equalsIgnoreCase).isPresent())
            .sorted(String.CASE_INSENSITIVE_ORDER).toList();
        int pages = Math.max(1, (npcs.size() + 26) / 27);
        int safePage = Math.max(0, Math.min(page, pages - 1));
        SetupHolder holder = new SetupHolder(player, "npcs", realmId, safePage);
        Inventory menu = Bukkit.createInventory(holder, 54, "VnMine • NPC tutorial " + (safePage + 1) + "/" + pages);
        holder.inventory = menu;
        for (int index = safePage * 27; index < Math.min((safePage + 1) * 27, npcs.size()); index++) {
            String npcId = npcs.get(index);
            int slot = CONTENT_SLOTS[index - safePage * 27];
            String npcWorld = npcManager.getNpcTarget(npcId).map(NpcManager.NpcTarget::world).orElse("chưa nạp");
            holder.choices.put(slot, npcId);
            menu.setItem(slot, item(Material.VILLAGER_SPAWN_EGG, "§d" + npcId, List.of(
                "§7World đang đặt: §f" + npcWorld,
                "§7Chỉ hợp lệ khi world khớp đại lục.", "§eNhấn để gán làm NPC hướng dẫn.")));
        }
        if (npcs.isEmpty()) {
            menu.setItem(22, item(Material.NAME_TAG, "§cChưa có NPC loại tutorial", List.of(
                "§7Đóng GUI và tạo NPC bằng lệnh:",
                "§f/vnnpc create <id> tutorial",
                "§7Ví dụ: /vnnpc create tutorial-nhan-gioi tutorial",
                "§7Đặt NPC trong đúng world, sau đó chạy lại /vninit.")));
        }
        if (safePage > 0) menu.setItem(45, item(Material.ARROW, "§eTrang trước", List.of()));
        menu.setItem(49, item(Material.ARROW, "§7Quay lại đại lục", List.of()));
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
            } else if (slot == 46) {
                boolean enabled = plugin.getConfig().getBoolean("rpg.onboarding.enabled", true);
                plugin.getConfig().set("rpg.onboarding.enabled", !enabled);
                savePluginConfig(player, "rpg.onboarding.enabled=" + !enabled);
                openDashboard(player, holder.page);
            } else if (slot == 47) {
                player.closeInventory();
                Bukkit.dispatchCommand(player, "vnskillbook config");
            } else if (slot == 48) {
                pendingWorldNames.put(player.getUniqueId(), "__start__");
                player.closeInventory();
                player.sendMessage("§eNhập tên world an toàn cho người mới/reset. Ví dụ: §fworld_start§e. Gõ §f!cancel §eđể hủy.");
            } else if (slot == 50) {
                String current = plugin.getConfig().getString("rpg.onboarding.start-world.environment", "NORMAL");
                String next = "NORMAL".equalsIgnoreCase(current) ? "NETHER"
                    : "NETHER".equalsIgnoreCase(current) ? "THE_END" : "NORMAL";
                plugin.getConfig().set("rpg.onboarding.start-world.environment", next);
                plugin.getConfig().set("rpg.onboarding.start-world.create-if-missing", true);
                savePluginConfig(player, "rpg.onboarding.start-world.environment=" + next);
                openDashboard(player);
            } else if (slot == 51) {
                openTutorialQuestSetup(player);
            } else {
                String realmId = holder.choices.get(slot);
                if (realmId != null) openRealm(player, realmId);
            }
            return;
        }
        if ("tutorial-quest".equals(holder.type)) {
            if (slot == 49) openDashboard(player);
            else if (slot == 53) player.closeInventory();
            else if (slot == 29) toggleTutorialQuest(player);
            else if (slot == 33) {
                player.closeInventory();
                Bukkit.dispatchCommand(player, "vnquest admin");
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
                    openTutorialNpcEditor(player, plugin.getRpgWorldManager().getDefinitions().get(holder.realmId).tutorialNpcId());
                });
            } else if (slot == 15) openTutorialQuestChoices(player, holder.realmId);
            return;
        }
        if ("tutorial-quests".equals(holder.type)) {
            if (slot == 49) {
                var realm = plugin.getRpgWorldManager().getDefinitions().get(holder.realmId);
                if (realm != null) openTutorialNpcEditor(player, realm.tutorialNpcId());
            } else if (slot == 45 || holder.choices.containsKey(slot)) {
                String questId = slot == 45 ? null : holder.choices.get(slot);
                updateRealm(player, holder.realmId, config -> config.set("tutorial.auto-quest-id", questId),
                    "set tutorial auto quest=" + questId);
                var realm = plugin.getRpgWorldManager().getDefinitions().get(holder.realmId);
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
                        List<String> raceIds = characters == null ? List.of() : characters.getRaceDefinitions().stream()
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
                case 12 -> openWorlds(player, holder.realmId, 0);
                case 14 -> openTutorialNpcs(player, holder.realmId, 0);
                case 16 -> {
                    updateRealm(player, holder.realmId, config -> {
                        String current = config.getString("world.environment", "NORMAL");
                        String next = "NORMAL".equalsIgnoreCase(current) ? "NETHER"
                            : "NETHER".equalsIgnoreCase(current) ? "THE_END" : "NORMAL";
                        config.set("world.environment", next);
                    }, "cycle world environment");
                    openRealm(player, holder.realmId);
                }
                case 18 -> openRealmRaces(player, holder.realmId);
                case 20 -> {
                    var realm = plugin.getRpgWorldManager().getDefinitions().get(holder.realmId);
                    if (realm != null) openTutorialNpcEditor(player, realm.tutorialNpcId());
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
                        config.set("world.create-if-missing", false);
                    }, "set existing world=" + world);
                    openRealm(player, holder.realmId);
                }
            }
            return;
        }
        if ("npcs".equals(holder.type)) {
            if (slot == 49) openRealm(player, holder.realmId);
            else if (slot == 45 && holder.page > 0) openTutorialNpcs(player, holder.realmId, holder.page - 1);
            else if (slot == 53) openTutorialNpcs(player, holder.realmId, holder.page + 1);
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

    private File findRealmFile(String realmId) {
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
            realm.startingSelectable() && worldReady && spawnReady && npcReady);
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
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private record RealmStatus(boolean starting, boolean worldReady, boolean spawnReady, boolean npcReady, boolean ready) { }

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
