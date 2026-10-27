package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Admin player picker and moderation controls. */
public final class PlayerAdminManager implements Listener {
    private static final String PERMISSION = "vnmine.command.playeradmin";
    private final VnMineHardcore plugin;
    private final File muteFile;
    private final Map<UUID, Long> mutedUntil = new ConcurrentHashMap<>();
    private final Map<UUID, PendingInput> awaitingInput = new ConcurrentHashMap<>();

    public PlayerAdminManager(VnMineHardcore plugin) {
        this.plugin = plugin;
        muteFile = new File(plugin.getDataFolder(), "player-mutes.yml");
        loadMutes();
    }

    public boolean open(Player admin) {
        if (!plugin.getConfig().getBoolean("player-admin.enabled", true)) {
            admin.sendMessage("§cChức năng quản trị người chơi hiện đang tắt.");
            return true;
        }
        if (!admin.hasPermission(PERMISSION)) {
            admin.sendMessage("§cBạn không có quyền quản trị người chơi.");
            return true;
        }
        openOnlinePlayers(admin, 0);
        return true;
    }

    private void openOnlinePlayers(Player admin, int requestedPage) {
        var online = Bukkit.getOnlinePlayers().stream()
            .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER)).toList();
        int pageCount = Math.max(1, (online.size() + 44) / 45);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        AdminHolder holder = new AdminHolder(admin.getUniqueId(), "players", page);
        Inventory menu = Bukkit.createInventory(holder, 54, "Quản trị người chơi • " + (page + 1));
        holder.inventory = menu;
        for (int index = page * 45; index < Math.min((page + 1) * 45, online.size()); index++) {
            Player target = online.get(index);
            int slot = index - page * 45;
            holder.targets.put(slot, target.getUniqueId());
            menu.setItem(slot, item(Material.PLAYER_HEAD, "§a" + target.getName(),
                List.of("§7Đang trực tuyến", "§eNhấn để quản trị.")));
        }
        if (page > 0) menu.setItem(45, item(Material.ARROW, "§eTrang trước", List.of()));
        menu.setItem(49, item(Material.ARROW, "§eMenu quản trị", List.of("§7Quay về menu quản trị chính.")));
        if (page + 1 < pageCount) menu.setItem(51, item(Material.ARROW, "§eTrang sau", List.of()));
        menu.setItem(53, item(Material.ENDER_EYE, "§dTìm người chơi ngoại tuyến",
            List.of("§7Icon này dành riêng cho tìm người chơi offline.", "§eNhấn rồi nhập tên vào chat.")));
        admin.openInventory(menu);
    }

    private void promptOfflineName(Player admin) {
        awaitingInput.put(admin.getUniqueId(), new PendingInput("offline-name", null));
        admin.closeInventory();
        admin.sendMessage("§d[QUẢN TRỊ] §fNhập tên người chơi offline vào chat.");
        admin.sendMessage("§7Ví dụ: §fSteve123 §8| §eGõ !cancel để hủy.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAdminChat(AsyncPlayerChatEvent event) {
        UUID adminId = event.getPlayer().getUniqueId();
        PendingInput pending = awaitingInput.get(adminId);
        if (pending == null) return;
        event.setCancelled(true);
        String name = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player admin = Bukkit.getPlayer(adminId);
            if (admin != null && !admin.hasPermission(PERMISSION)) {
                awaitingInput.remove(adminId);
                admin.sendMessage("§cQuyền quản trị người chơi đã bị thu hồi.");
                return;
            }
            awaitingInput.remove(adminId);
            if (admin == null || !admin.isOnline()) return;
            if (name.equalsIgnoreCase("!cancel")) {
                admin.sendMessage("§eĐã hủy chọn người chơi offline.");
                if ("mute-seconds".equals(pending.type()) && pending.target() != null)
                    openTarget(admin, pending.target());
                else openOnlinePlayers(admin, 0);
                return;
            }
            if ("mute-seconds".equals(pending.type())) {
                try {
                    long seconds = Long.parseLong(name);
                    long maximum = Math.max(1, Math.min(31_536_000,
                        plugin.getConfig().getLong("player-admin.mute.max-seconds", 31_536_000)));
                    if (seconds < 1 || seconds > maximum) throw new NumberFormatException();
                    long until = Math.addExact(System.currentTimeMillis(), Math.multiplyExact(seconds, 1000));
                    mutedUntil.put(pending.target(), until);
                    saveMutes();
                    audit(admin, Bukkit.getOfflinePlayer(pending.target()).getName(), "mute:" + seconds + "s");
                    admin.sendMessage("§aĐã mute trong " + seconds + " giây.");
                    Player target = Bukkit.getPlayer(pending.target());
                    if (target != null) target.sendMessage("§cBạn đã bị mute trong " + seconds + " giây.");
                    openTarget(admin, pending.target());
                } catch (ArithmeticException | NumberFormatException exception) {
                    long maximum = Math.max(1, Math.min(31_536_000,
                        plugin.getConfig().getLong("player-admin.mute.max-seconds", 31_536_000)));
                    admin.sendMessage("§cSố giây hợp lệ: 1–" + maximum + ". Ví dụ: 300");
                    awaitingInput.put(adminId, pending);
                }
                return;
            }
            if (!name.matches("[A-Za-z0-9_]{3,16}")) {
                admin.sendMessage("§cTên không hợp lệ. Ví dụ: Steve123");
                promptOfflineName(admin);
                return;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(name);
            if (target == null || target.getName() == null) {
                admin.sendMessage("§cKhông tìm thấy người chơi đã từng vào server: §f" + name);
                openOnlinePlayers(admin, 0);
                return;
            }
            openTarget(admin, target.getUniqueId());
        });
    }

    private void openTarget(Player admin, UUID targetId) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(targetId);
        String name = offline.getName() == null ? targetId.toString() : offline.getName();
        Player target = Bukkit.getPlayer(targetId);
        boolean online = target != null;
        AdminHolder holder = new AdminHolder(admin.getUniqueId(), "target", 0);
        holder.target = targetId;
        Inventory menu = Bukkit.createInventory(holder, 54, "Quản trị • " + name);
        holder.inventory = menu;
        menu.setItem(4, item(online ? Material.PLAYER_HEAD : Material.SKELETON_SKULL,
            (online ? "§a" : "§7") + name, List.of(online ? "§aĐang online" : "§7Đang offline",
                "§8UUID: " + targetId)));
        boolean banned = Bukkit.getBanList(BanList.Type.NAME).isBanned(name);
        menu.setItem(10, item(banned ? Material.LIME_DYE : Material.BARRIER,
            banned ? "§aUnban" : "§cBan", List.of("§7Trạng thái hiện tại: " + (banned ? "bị ban" : "không bị ban"))));
        menu.setItem(11, item(Material.IRON_DOOR, "§cKick", List.of(online ? "§eNhấn để kick." : "§7Chỉ dùng được khi online.")));
        long muted = mutedUntil.getOrDefault(targetId, 0L);
        menu.setItem(12, item(muted > System.currentTimeMillis() ? Material.LIME_DYE : Material.GRAY_DYE,
            muted > System.currentTimeMillis() ? "§aUnmute" : "§eMute", List.of(
                muted > System.currentTimeMillis() ? "§7Mute còn đến: " + new Date(muted) : "§7Nhấn để mute vô thời hạn.")));
        menu.setItem(13, item(Material.CLOCK, "§eMute N giây", List.of("§7Nhấn rồi nhập số giây vào chat.")));
        menu.setItem(14, item(Material.CHEST, "§cXóa inventory", List.of(online ? "§eCó xác nhận ở bước tiếp theo." : "§7Chỉ dùng được khi online.")));
        menu.setItem(15, item(Material.TOTEM_OF_UNDYING, "§6Khôi phục inventory tử vong",
            List.of("§7Chọn một trong tối đa 10 mộ gần nhất.",
                "§7Vật phẩm được gửi an toàn kể cả khi offline.")));
        menu.setItem(16, item(Material.WRITABLE_BOOK, "§bQuản lý nhiệm vụ người chơi", List.of(
            "§7Mở menu riêng để quản lý tiến trình quest.",
            "§7Xem quest hoàn thành, cho phép làm lại hoặc reset.")));
        menu.setItem(20, item(Material.ENCHANTED_BOOK, "§cXóa skill đã học", List.of("§7Xóa skill progress và quick slot.")));
        menu.setItem(21, item(Material.PLAYER_HEAD, "§cReset race/class", List.of("§7Đưa lựa chọn về trạng thái chưa tạo nhân vật.")));
        menu.setItem(22, item(Material.TNT, "§4Reset toàn bộ hồ sơ RPG", List.of("§7Xóa tiến trình RPG để tạo lại nhân vật.")));
        menu.setItem(23, item(Material.EXPERIENCE_BOTTLE, "§aCấp level / EXP", List.of("§7Dùng công cụ quản trị tiến trình hiện có.")));
        menu.setItem(24, item(Material.ENCHANTED_BOOK, "§aGive item RPG", List.of("§7Chọn vật phẩm trong registry hệ thống.")));
        menu.setItem(45, item(Material.ARROW, "§eQuay lại danh sách người chơi", List.of()));
        menu.setItem(49, item(Material.ARROW, "§eMenu quản trị", List.of("§7Quay về menu quản trị chính.")));
        admin.openInventory(menu);
    }

    public void openTargetForAdmin(Player admin, UUID targetId) {
        if (admin.hasPermission(PERMISSION)) openTarget(admin, targetId);
    }

    public void openTargetQuestMenuForAdmin(Player admin, UUID targetId) {
        if (admin == null || !admin.hasPermission(PERMISSION)) return;
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
        String name = target.getName() == null ? targetId.toString() : target.getName();
        AdminHolder holder = new AdminHolder(admin.getUniqueId(), "target-quests", 0);
        holder.target = targetId;
        Inventory menu = Bukkit.createInventory(holder, 27, "Quản lý quest • " + name);
        holder.inventory = menu;
        menu.setItem(4, item(Material.PLAYER_HEAD, "§b" + name,
            List.of("§7Quản lý tiến trình nhiệm vụ của người chơi này.")));
        menu.setItem(11, item(Material.WRITABLE_BOOK, "§aQuest đã hoàn thành",
            List.of("§7Xem danh sách quest đã hoàn thành.", "§eChọn quest để cho phép làm lại.")));
        menu.setItem(15, item(Material.REDSTONE, "§cReset toàn bộ quest",
            List.of("§7Xóa trạng thái quest đang làm và đã hoàn thành.", "§eCó xác nhận trước khi thực hiện.")));
        menu.setItem(22, item(Material.ARROW, "§eQuay lại hồ sơ người chơi", List.of()));
        admin.openInventory(menu);
    }

    private ItemStack item(Material material, String title, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(title);
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player admin)
                || !(event.getView().getTopInventory().getHolder() instanceof AdminHolder holder)) return;
        event.setCancelled(true);
        if (!admin.hasPermission(PERMISSION)) {
            admin.closeInventory();
            admin.sendMessage("§cQuyền quản trị người chơi đã bị thu hồi.");
            return;
        }
        if (!holder.viewer.equals(admin.getUniqueId()) || event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getRawSlot();
        if ("players".equals(holder.screen)) {
            if (slot == 49) {
                if (plugin.getMercenaryManager() != null) plugin.getMercenaryManager().openAdminMenu(admin);
            }
            else if (slot == 53) promptOfflineName(admin);
            else if (slot == 45 && holder.page > 0) openOnlinePlayers(admin, holder.page - 1);
            else if (slot == 51 && holder.page + 1 < (Bukkit.getOnlinePlayers().size() + 44) / 45)
                openOnlinePlayers(admin, holder.page + 1);
            else if (holder.targets.containsKey(slot)) openTarget(admin, holder.targets.get(slot));
            return;
        }
        if ("rpg-items".equals(holder.screen)) {
            if (slot == 49) openTarget(admin, holder.target);
            else if (slot == 45 && holder.page > 0)
                openRpgItemPicker(admin, holder.target, holder.rpgItemList, holder.page - 1);
            else if (slot == 53)
                openRpgItemPicker(admin, holder.target, holder.rpgItemList, holder.page + 1);
            else if (holder.rpgChoices.containsKey(slot)) giveAdminRpgItem(admin, holder.target, holder.rpgChoices.get(slot));
            return;
        }
        if ("death-snapshots".equals(holder.screen)) {
            if (slot == 49) openTarget(admin, holder.target);
            else if (holder.choices.containsKey(slot)) confirm(admin, holder.target,
                "restore-grave:" + holder.choices.get(slot));
            return;
        }
        if ("confirm".equals(holder.screen)) {
            if (slot == 11) applyConfirmedAction(admin, holder.target, holder.action);
            else if (slot == 15) {
                if ("reset-quests".equals(holder.action)) openTargetQuestMenuForAdmin(admin, holder.target);
                else openTarget(admin, holder.target);
            }
            return;
        }
        if ("target".equals(holder.screen)) {
            if (slot == 45) openOnlinePlayers(admin, 0);
            else if (slot == 49 && plugin.getMercenaryManager() != null)
                plugin.getMercenaryManager().openAdminMenu(admin);
            else handleTargetAction(admin, holder.target, slot);
            return;
        }
        if ("target-quests".equals(holder.screen)) {
            if (slot == 22) openTarget(admin, holder.target);
            else if (slot == 11 && plugin.getQuestCatalogManager() != null)
                plugin.getQuestCatalogManager().openAdminCompletedQuests(admin, holder.target);
            else if (slot == 15) confirm(admin, holder.target, "reset-quests");
        }
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof AdminHolder) event.setCancelled(true);
    }

    private void handleTargetAction(Player admin, UUID targetId, int slot) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(targetId);
        String name = offline.getName();
        if (name == null) return;
        Player online = Bukkit.getPlayer(targetId);
        switch (slot) {
            case 10 -> {
                confirm(admin, targetId, "ban-toggle");
            }
            case 11 -> {
                if (online == null) admin.sendMessage("§cNgười chơi đang offline.");
                else confirm(admin, targetId, "kick");
            }
            case 12 -> {
                confirm(admin, targetId, "mute-toggle");
            }
            case 13 -> promptMuteDuration(admin, targetId);
            case 14 -> confirm(admin, targetId, "clear-inventory");
            case 15 -> openDeathSnapshots(admin, targetId);
            case 16 -> {
                openTargetQuestMenuForAdmin(admin, targetId);
            }
            case 20 -> confirm(admin, targetId, "clear-skills");
            case 21 -> confirm(admin, targetId, "reset-class");
            case 22 -> confirm(admin, targetId, "reset-all");
            case 23 -> {
                if (!admin.hasPermission("vnmine.command.progression.admin"))
                    admin.sendMessage("§cBạn thiếu permission vnmine.command.progression.admin.");
                else if (plugin.getMercenaryManager() != null)
                    plugin.getMercenaryManager().openAdminProgressionForPlayer(admin, targetId);
            }
            case 24 -> openRpgItemPicker(admin, targetId);
        }
    }

    private void openRpgItemPicker(Player admin, UUID targetId) {
        openRpgItemPicker(admin, targetId, 0);
    }

    private void openRpgItemPicker(Player admin, UUID targetId, int requestedPage) {
        openRpgItemPicker(admin, targetId, getAdminRpgItems(admin), requestedPage);
    }

    private void openRpgItemPicker(Player admin, UUID targetId, List<AdminItemChoice> items, int requestedPage) {
        int pageCount = Math.max(1, (items.size() + 44) / 45);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        AdminHolder holder = new AdminHolder(admin.getUniqueId(), "rpg-items", page);
        holder.target = targetId;
        holder.rpgItemList = items;
        Inventory menu = Bukkit.createInventory(holder, 54, "Give item RPG • " + (page + 1) + "/" + pageCount);
        holder.inventory = menu;
        for (int slot = 0; slot < Math.min(45, items.size() - page * 45); slot++) {
            AdminItemChoice choice = items.get(page * 45 + slot);
            holder.rpgChoices.put(slot, choice);
            ItemStack icon = choice.item().clone();
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName("§6" + choice.name());
            List<String> lore = new ArrayList<>();
            lore.add("§dLoại: §f" + choice.category());
            lore.add("§7ID: §f" + choice.id());
            lore.add("§eNhấn để cấp một vật phẩm.");
            meta.setLore(lore);
            icon.setItemMeta(meta);
            icon.setAmount(1);
            menu.setItem(slot, icon);
        }
        if (items.isEmpty()) menu.setItem(22, item(Material.BARRIER, "§cChưa có item RPG trong registry", List.of()));
        if (page > 0) menu.setItem(45, item(Material.ARROW, "§eTrang trước", List.of()));
        menu.setItem(49, item(Material.ARROW, "§eQuay lại hồ sơ", List.of()));
        if (page + 1 < pageCount) menu.setItem(53, item(Material.ARROW, "§eTrang sau", List.of()));
        admin.openInventory(menu);
    }

    private List<AdminItemChoice> getAdminRpgItems(Player admin) {
        List<AdminItemChoice> result = new ArrayList<>();
        if (plugin.getQuestCatalogManager() != null) {
            plugin.getQuestCatalogManager().getAdminRpgItems().forEach(entry -> result.add(
                new AdminItemChoice("quest/" + entry.id(), "Nguyên liệu quest • " + entry.name(),
                    "Nguyên liệu quest", entry.material(), entry.item().clone())));
        }
        if (plugin.getSkillManager() != null) {
            plugin.getSkillManager().getAdminSkillBookItems().forEach(entry -> result.add(
                new AdminItemChoice("skillbook/" + entry.id(), "Sách kỹ năng • " + entry.name(),
                    "Sách kỹ năng", entry.material(), entry.item().clone())));
        }
        if (plugin.getBossEventManager() != null) {
            try {
                plugin.getBossEventManager().getAdminRpgItems().forEach(entry -> result.add(
                    new AdminItemChoice(entry.id(), "Boss • " + entry.name(), "Vật phẩm boss",
                        entry.material(), entry.item().clone())));
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE,
                    "[PlayerAdmin] Could not load boss RPG item catalog; continuing with other item sources.", exception);
                admin.sendMessage("§eDanh mục vật phẩm boss lỗi; picker vẫn mở với các loại item khác.");
            }
        }
        if (plugin.getMercenaryManager() != null) {
            plugin.getMercenaryManager().getAdminRpgItems().forEach(entry -> result.add(
                new AdminItemChoice(entry.id(), entry.name(), entry.category(),
                    entry.material(), entry.item().clone())));
        }
        if (plugin.getNpcManager() != null) {
            plugin.getNpcManager().getAdminRpgItems().forEach(entry -> result.add(
                new AdminItemChoice(entry.id(), "Shop NPC • " + entry.name(), "Item trong shop NPC",
                    entry.material(), entry.item().clone())));
        }
        result.sort(Comparator.comparing(AdminItemChoice::category, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(AdminItemChoice::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(AdminItemChoice::id));
        return List.copyOf(result);
    }

    private void giveAdminRpgItem(Player admin, UUID targetId, AdminItemChoice choice) {
        PlayerProfileDatabaseManager database = plugin.getPlayerProfileDatabaseManager();
        String transaction = "admin-rpg-item:" + UUID.randomUUID();
        ItemStack item = choice.item().clone();
        item.setAmount(1);
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            database.executeGameplayDatabaseTask("player-admin.give-rpg-item actor=" + admin.getName()
                    + " target=" + targetId + " item=" + choice.id(), () -> {
                database.enqueueMerchantDeliveries(targetId.toString(), transaction,
                    List.of(item.serializeAsBytes()), java.time.Instant.now().toString());
                return true;
            }).whenComplete((ignored, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (failure != null) {
                    plugin.getLogger().log(Level.SEVERE, "[PlayerAdmin] Could not queue RPG item delivery.", failure);
                    admin.sendMessage("§cKhông thể xếp item vào kho nhận.");
                } else {
                    audit(admin, Bukkit.getOfflinePlayer(targetId).getName(), "give-rpg-item:" + choice.id());
                    admin.sendMessage("§aĐã xếp §f" + choice.name() + "§a vào kho nhận offline.");
                }
            }));
            return;
        }
        Map<Integer, ItemStack> leftover = target.getInventory().addItem(item);
        if (leftover.isEmpty()) {
            target.saveData();
            audit(admin, target.getName(), "give-rpg-item:" + choice.id());
            admin.sendMessage("§aĐã cấp §f" + choice.name() + "§a cho " + target.getName() + ".");
            return;
        }
        List<byte[]> overflow = leftover.values().stream().map(ItemStack::serializeAsBytes).toList();
        database.executeGameplayDatabaseTask("player-admin.give-rpg-overflow actor=" + admin.getName()
                + " target=" + targetId + " item=" + choice.id(), () -> {
            database.enqueueMerchantDeliveries(targetId.toString(), transaction, overflow,
                java.time.Instant.now().toString());
            return true;
        }).whenComplete((ignored, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (failure != null) {
                plugin.getLogger().log(Level.SEVERE, "[PlayerAdmin] Could not queue RPG item overflow.", failure);
                admin.sendMessage("§cKhông thể lưu phần item tràn vào kho nhận; hãy kiểm tra console.");
            } else {
                if (target.isOnline()) target.saveData();
                audit(admin, target.getName(), "give-rpg-item:" + choice.id() + ":queued");
                admin.sendMessage("§aĐã cấp item; phần tồn kho được xếp vào hàng đợi bền vững.");
                if (target.isOnline() && plugin.getEconomyManager() != null)
                    plugin.getEconomyManager().reconcileMerchantDeliveries(target);
            }
        }));
    }

    private void promptMuteDuration(Player admin, UUID target) {
        awaitingInput.put(admin.getUniqueId(), new PendingInput("mute-seconds", target));
        admin.closeInventory();
        admin.sendMessage("§d[QUẢN TRỊ] §fNhập số giây mute, ví dụ: 300. Gõ !cancel để hủy.");
    }

    private void confirm(Player admin, UUID target, String action) {
        AdminHolder holder = new AdminHolder(admin.getUniqueId(), "confirm", 0);
        holder.target = target;
        holder.action = action;
        Inventory menu = Bukkit.createInventory(holder, 27, "Xác nhận • " + action);
        holder.inventory = menu;
        menu.setItem(13, item(Material.TNT, "§cXác nhận " + action, List.of(
            "§7Người chơi: §f" + Bukkit.getOfflinePlayer(target).getName(),
            "§cThao tác không thể hoàn tác tự động.")));
        menu.setItem(11, item(Material.LIME_CONCRETE, "§aXác nhận", List.of("§7Thực hiện thao tác.")));
        menu.setItem(15, item(Material.RED_CONCRETE, "§cHủy", List.of("§7Quay lại "
            + ("reset-quests".equals(action) ? "quản lý quest." : "hồ sơ người chơi."))));
        admin.openInventory(menu);
    }

    private void applyConfirmedAction(Player admin, UUID targetId, String action) {
        Player target = Bukkit.getPlayer(targetId);
        String name = Bukkit.getOfflinePlayer(targetId).getName();
        if ("ban-toggle".equals(action)) {
            var bans = Bukkit.getBanList(BanList.Type.NAME);
            boolean wasBanned = bans.isBanned(name);
            if (wasBanned) bans.pardon(name);
            else {
                bans.addBan(name, "Bị cấm bởi quản trị viên " + admin.getName(), null, admin.getName());
                if (target != null) target.kickPlayer("§cBạn đã bị ban khỏi server.");
            }
            audit(admin, name, wasBanned ? "unban" : "ban");
            admin.sendMessage(wasBanned ? "§aĐã unban " + name + "." : "§cĐã ban " + name + ".");
        } else if ("kick".equals(action)) {
            if (target == null) admin.sendMessage("§cNgười chơi đã offline; không thể kick.");
            else {
                audit(admin, target.getName(), "kick");
                target.kickPlayer("§cBạn đã bị kick bởi quản trị viên.");
            }
        } else if ("mute-toggle".equals(action)) {
            boolean wasMuted = mutedUntil.remove(targetId) != null;
            if (!wasMuted) mutedUntil.put(targetId, Long.MAX_VALUE);
            saveMutes();
            audit(admin, name, "mute-toggle");
            if (target != null) target.sendMessage(wasMuted
                ? "§aQuản trị viên đã gỡ mute cho bạn."
                : "§cBạn đã bị mute bởi quản trị viên.");
            admin.sendMessage(wasMuted ? "§aĐã unmute " + name + "." : "§eĐã mute " + name + ".");
        } else if (action.startsWith("restore-grave:")) {
            String graveId = action.substring("restore-grave:".length());
            if (plugin.getGraveManager() == null) admin.sendMessage("§cDữ liệu mộ chưa sẵn sàng.");
            else plugin.getGraveManager().queueAdminRestore(targetId, graveId, success -> {
                if (!success) {
                    admin.sendMessage("§cKhông thể xếp inventory tử vong vào kho nhận.");
                    return;
                }
                audit(admin, name, "restore-grave:" + graveId);
                admin.sendMessage("§aĐã khôi phục inventory tử vong " + graveId
                    + " vào kho nhận bền vững của người chơi.");
                Player onlineTarget = Bukkit.getPlayer(targetId);
                if (onlineTarget != null && plugin.getEconomyManager() != null)
                    plugin.getEconomyManager().reconcileMerchantDeliveries(onlineTarget);
                openTarget(admin, targetId);
            });
        }
        else if ("clear-inventory".equals(action)) {
            if (target == null) admin.sendMessage("§cNgười chơi đã offline; inventory không bị thay đổi.");
            else {
                target.getInventory().clear();
                target.getInventory().setArmorContents(new ItemStack[4]);
                target.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
                target.saveData();
                audit(admin, target.getName(), action);
                admin.sendMessage("§aĐã xóa inventory của " + target.getName() + ".");
            }
        } else if ("reset-quests".equals(action)) {
            PlayerProfileDatabaseManager database = plugin.getPlayerProfileDatabaseManager();
            database.executeGameplayDatabaseTask("player-admin.reset-quests actor=" + admin.getName()
                    + " target=" + targetId, () -> database.resetAllQuestProgress(targetId.toString()))
                .whenComplete((count, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (failure != null) {
                        plugin.getLogger().log(Level.SEVERE, "[PlayerAdmin] Quest reset failed for " + targetId, failure);
                        admin.sendMessage("§cKhông thể reset quest; kiểm tra console.");
                    } else {
                        if (target != null && plugin.getQuestCatalogManager() != null)
                            plugin.getQuestCatalogManager().reloadQuestCache(target);
                        if (target != null && plugin.getTutorialQuestManager() != null)
                            plugin.getTutorialQuestManager().refreshPlayerAfterAdminQuestReset(target);
                        audit(admin, name, "reset-quests:" + count);
                        admin.sendMessage("§aĐã xóa " + count + " trạng thái quest.");
                    }
                    openTargetQuestMenuForAdmin(admin, targetId);
                }));
            return;
        } else if ("clear-skills".equals(action)) {
            PlayerProfileDatabaseManager database = plugin.getPlayerProfileDatabaseManager();
            database.executeGameplayDatabaseTask("player-admin.clear-skills actor=" + admin.getName()
                    + " target=" + targetId, () -> database.clearLearnedSkills(targetId.toString()))
                .whenComplete((count, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (failure != null) {
                        plugin.getLogger().log(Level.SEVERE, "[PlayerAdmin] Skill reset failed for " + targetId, failure);
                        admin.sendMessage("§cKhông thể xóa skill đã học.");
                    } else {
                        audit(admin, name, "clear-skills:" + count);
                        admin.sendMessage("§aĐã xóa " + count + " skill đã học và quick slot.");
                    }
                    openTarget(admin, targetId);
                }));
            return;
        } else if ("reset-class".equals(action)) {
            PlayerProfileDatabaseManager database = plugin.getPlayerProfileDatabaseManager();
            database.executeGameplayDatabaseTask("player-admin.reset-class actor=" + admin.getName()
                    + " target=" + targetId, () -> database.resetRaceAndClass(targetId.toString()))
                .whenComplete((success, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (failure != null) plugin.getLogger().log(Level.SEVERE,
                        "[PlayerAdmin] Class reset failed for " + targetId, failure);
                    else if (Boolean.TRUE.equals(success)) {
                        if (target != null && plugin.getCharacterSelectionManager() != null)
                            plugin.getCharacterSelectionManager().refreshLoadedProfile(target);
                        audit(admin, name, "reset-race-class");
                        admin.sendMessage("§aĐã reset race, class, level và skill.");
                    }
                    openTarget(admin, targetId);
                }));
            return;
        } else if ("reset-all".equals(action)) {
            PlayerProfileDatabaseManager database = plugin.getPlayerProfileDatabaseManager();
            database.executeGameplayDatabaseTask("player-admin.reset-all actor=" + admin.getName()
                    + " target=" + targetId, () -> database.deleteRpgProfile(targetId.toString()))
                .whenComplete((success, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (failure != null) plugin.getLogger().log(Level.SEVERE,
                        "[PlayerAdmin] Full RPG reset failed for " + targetId, failure);
                    else if (Boolean.TRUE.equals(success)) {
                        if (target != null && plugin.getCharacterSelectionManager() != null)
                            plugin.getCharacterSelectionManager().refreshLoadedProfile(target);
                        if (target != null && plugin.getQuestCatalogManager() != null)
                            plugin.getQuestCatalogManager().reloadQuestCache(target);
                        if (target != null && plugin.getTutorialQuestManager() != null)
                            plugin.getTutorialQuestManager().refreshPlayerAfterAdminQuestReset(target);
                        audit(admin, name, "reset-rpg-profile");
                        admin.sendMessage("§aĐã xóa hồ sơ RPG; ví VNCOIN vẫn được giữ.");
                    }
                    openTarget(admin, targetId);
                }));
            return;
        } else {
            admin.sendMessage("§eThao tác này chưa khả dụng với hồ sơ này.");
        }
        openTarget(admin, targetId);
    }

    private void openDeathSnapshots(Player admin, UUID targetId) {
        if (plugin.getGraveManager() == null) {
            admin.sendMessage("§cKho mộ chưa sẵn sàng.");
            return;
        }
        int snapshotLimit = Math.max(1, Math.min(10,
            plugin.getConfig().getInt("player-admin.death-snapshot-limit", 10)));
        List<GraveManager.GraveData> graves = plugin.getGraveManager().getRecentPlayerGraves(targetId, snapshotLimit);
        AdminHolder holder = new AdminHolder(admin.getUniqueId(), "death-snapshots", 0);
        holder.target = targetId;
        Inventory menu = Bukkit.createInventory(holder, 54, "Inventory tử vong gần nhất");
        holder.inventory = menu;
        for (int index = 0; index < graves.size(); index++) {
            GraveManager.GraveData grave = graves.get(index);
            holder.choices.put(index, grave.graveId);
            menu.setItem(index, item(Material.CHEST, "§6Lần chết " + (index + 1), List.of(
                "§7ID: §f" + grave.graveId,
                "§7Thời gian: §f" + new Date(grave.createdAt),
                "§7Vật phẩm snapshot: §e" + grave.items.size(),
                "§eNhấn để xem xác nhận khôi phục.")));
        }
        if (graves.isEmpty()) menu.setItem(22, item(Material.BARRIER, "§7Không có inventory tử vong để khôi phục", List.of()));
        menu.setItem(49, item(Material.ARROW, "§eQuay lại hồ sơ", List.of()));
        admin.openInventory(menu);
    }

    private void audit(Player admin, String target, String action) {
        plugin.getLogger().info("[PlayerAdmin] actor=" + admin.getName() + ", target=" + target + ", action=" + action);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMutedChat(AsyncPlayerChatEvent event) {
        Long until = mutedUntil.get(event.getPlayer().getUniqueId());
        if (until == null) return;
        if (until != Long.MAX_VALUE && until <= System.currentTimeMillis()) {
            mutedUntil.remove(event.getPlayer().getUniqueId());
            saveMutes();
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage("§cBạn đang bị mute và không thể nhắn chat.");
    }

    private void loadMutes() {
        if (!muteFile.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(muteFile);
        for (String key : yaml.getKeys(false)) {
            try {
                long until = yaml.getLong(key);
                if (until > System.currentTimeMillis()) mutedUntil.put(UUID.fromString(key), until);
            } catch (IllegalArgumentException ignored) { }
        }
    }

    private void saveMutes() {
        YamlConfiguration yaml = new YamlConfiguration();
        mutedUntil.forEach((uuid, until) -> yaml.set(uuid.toString(), until));
        try {
            File parent = muteFile.getAbsoluteFile().getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create plugin data directory.");
            yaml.save(muteFile);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "[PlayerAdmin] Could not save mute state.", exception);
        }
    }

    private static final class AdminHolder implements InventoryHolder {
        private final UUID viewer;
        private final String screen;
        private final int page;
        private final Map<Integer, UUID> targets = new LinkedHashMap<>();
        private final Map<Integer, String> choices = new LinkedHashMap<>();
        private final Map<Integer, AdminItemChoice> rpgChoices = new LinkedHashMap<>();
        private List<AdminItemChoice> rpgItemList = List.of();
        private UUID target;
        private String action;
        private Inventory inventory;
        private AdminHolder(UUID viewer, String screen, int page) {
            this.viewer = viewer; this.screen = screen; this.page = page;
        }
        @Override public Inventory getInventory() { return inventory; }
    }

    private record AdminItemChoice(String id, String name, String category, Material material, ItemStack item) { }
    private record PendingInput(String type, UUID target) { }
}
