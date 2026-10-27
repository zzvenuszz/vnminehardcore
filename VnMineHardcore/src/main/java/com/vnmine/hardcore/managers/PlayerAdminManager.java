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
            if (slot == 49) {
                if ("rpg-root".equals(holder.action)) openTarget(admin, holder.target);
                else if ("player-root".equals(holder.action)) openRpgItemPicker(admin, holder.target);
                else if ("player-categories".equals(holder.action)) openPlayerRpgCategories(admin, holder.target);
                else if ("root".equals(holder.action)) openRpgItemPicker(admin, holder.target);
                else if ("gear-class".equals(holder.action)) openMercenaryClasses(admin, holder.target);
                else if ("gear-material".equals(holder.action)) openMercenaryMaterials(admin, holder.target, holder.gearClass);
                else if ("gear-star".equals(holder.action)) openMercenaryStars(admin, holder.target, holder.gearClass, holder.gearMaterial);
                else if ("gear-set".equals(holder.action)) openMercenarySets(admin, holder.target, holder.gearClass, holder.gearMaterial, holder.gearStars);
                else openTarget(admin, holder.target);
            }
            else if (slot == 45 && holder.page > 0)
                openRpgItemPicker(admin, holder.target, holder.rpgItemList, holder.page - 1);
            else if (slot == 53)
                openRpgItemPicker(admin, holder.target, holder.rpgItemList, holder.page + 1);
            else if (holder.rpgChoices.containsKey(slot)) {
                AdminItemChoice choice = holder.rpgChoices.get(slot);
                if (choice.id().startsWith("menu:")) {
                    switch (choice.id()) {
                        case "menu:player-items" -> openPlayerRpgCategories(admin, holder.target);
                        case "menu:mercenary-gear" -> openMercenaryClasses(admin, holder.target);
                        case "menu:mercenary-token" -> openRpgItemPicker(admin, holder.target,
                            getAdminRpgItems(admin).stream().filter(item -> item.id().startsWith("army-token/")).toList(),
                            0, "root", "Lệnh triệu hồi lính", null, null, 0, null);
                        case "menu:category" -> openRpgItemPicker(admin, holder.target,
                            getAdminRpgItems(admin).stream().filter(item -> item.category().equals(choice.category())
                                && !item.id().startsWith("army-")).toList(), 0);
                        default -> { }
                    }
                } else if (choice.id().startsWith("gear-class:")) {
                    openMercenaryMaterials(admin, holder.target, choice.id().substring("gear-class:".length()));
                } else if (choice.id().startsWith("gear-material:")) {
                    openMercenaryStars(admin, holder.target, holder.gearClass,
                        choice.id().substring("gear-material:".length()));
                } else if (choice.id().startsWith("gear-star:")) {
                    openMercenarySets(admin, holder.target, holder.gearClass, holder.gearMaterial,
                        Integer.parseInt(choice.id().substring("gear-star:".length())));
                } else if (choice.id().startsWith("gear-set:")) {
                    openMercenarySlots(admin, holder.target, holder.gearClass, holder.gearMaterial,
                        holder.gearStars, choice.id().substring("gear-set:".length()));
                } else if ("gear-give-set".equals(choice.id())) {
                    List<AdminItemChoice> setItems = gearItems(getAdminRpgItems(admin), holder.gearClass,
                        holder.gearMaterial, holder.gearStars, holder.gearSet);
                    giveAdminRpgItems(admin, holder.target, setItems,
                        "Bộ " + mercenaryClassName(holder.gearClass) + " " + holder.gearMaterial + " ★" + holder.gearStars);
                } else giveAdminRpgItem(admin, holder.target, choice);
            }
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
        List<AdminItemChoice> root = List.of(
            new AdminItemChoice("menu:player-items", "§aVật phẩm người chơi", "Item RPG / boss / quest / skill / shop", Material.CHEST, new ItemStack(Material.CHEST)),
            new AdminItemChoice("menu:mercenary-gear", "§bTrang bị lính", "Class → vật liệu → sao → set → món", Material.IRON_CHESTPLATE, new ItemStack(Material.IRON_CHESTPLATE)),
            new AdminItemChoice("menu:mercenary-token", "§eLệnh triệu hồi lính", "Token lính theo class và bậc", Material.PAPER, new ItemStack(Material.PAPER))
        );
        openRpgItemPicker(admin, targetId, root, 0, "rpg-root", "Give RPG • Phân loại", null, null, 0, null);
    }

    private void openRpgItemPicker(Player admin, UUID targetId, int requestedPage) {
        openPlayerRpgCategories(admin, targetId);
    }

    private void openRpgItemPicker(Player admin, UUID targetId, List<AdminItemChoice> items, int requestedPage) {
        openRpgItemPicker(admin, targetId, items, requestedPage, "player-categories", "Give item RPG", null, null, 0, null);
    }

    private void openRpgItemPicker(Player admin, UUID targetId, List<AdminItemChoice> items, int requestedPage,
            String back, String title, String gearClass, String gearMaterial, int gearStars, String gearSet) {
        int pageCount = Math.max(1, (items.size() + 44) / 45);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        AdminHolder holder = new AdminHolder(admin.getUniqueId(), "rpg-items", page);
        holder.target = targetId;
        holder.rpgItemList = items;
        holder.action = back;
        holder.gearClass = gearClass;
        holder.gearMaterial = gearMaterial;
        holder.gearStars = gearStars;
        holder.gearSet = gearSet;
        Inventory menu = Bukkit.createInventory(holder, 54, title + (items.size() > 45 ? " • " + (page + 1) + "/" + pageCount : ""));
        holder.inventory = menu;
        for (int slot = 0; slot < Math.min(45, items.size() - page * 45); slot++) {
            AdminItemChoice choice = items.get(page * 45 + slot);
            holder.rpgChoices.put(slot, choice);
            ItemStack icon = choice.item().clone();
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName(choice.name().startsWith("§") ? choice.name() : "§6" + choice.name());
            List<String> lore = new ArrayList<>();
            lore.add("§dLoại: §f" + choice.category());
            lore.add("§7ID: §f" + choice.id());
            lore.add(choice.id().startsWith("menu:") || choice.id().startsWith("gear-")
                ? "§eNhấn để mở danh mục." : "§eNhấn để cấp một vật phẩm.");
            meta.setLore(lore);
            icon.setItemMeta(meta);
            icon.setAmount(1);
            menu.setItem(slot, icon);
        }
        if (items.isEmpty()) menu.setItem(22, item(Material.BARRIER, "§cChưa có item RPG trong registry", List.of()));
        if (page > 0) menu.setItem(45, item(Material.ARROW, "§eTrang trước", List.of()));
        menu.setItem(49, item(Material.ARROW, "§eQuay lại", List.of("§7Về danh mục trước.")));
        if (page + 1 < pageCount) menu.setItem(53, item(Material.ARROW, "§eTrang sau", List.of()));
        admin.openInventory(menu);
    }

    private void openPlayerRpgCategories(Player admin, UUID targetId) {
        Map<String, AdminItemChoice> categories = new LinkedHashMap<>();
        for (AdminItemChoice choice : getAdminRpgItems(admin)) {
            if (choice.id().startsWith("army-")) continue;
            categories.putIfAbsent(choice.category(), new AdminItemChoice("menu:category", choice.category(),
                choice.category(), choice.material(), choice.item()));
        }
        List<AdminItemChoice> choices = new ArrayList<>(categories.values());
        choices.sort(Comparator.comparing(AdminItemChoice::category, String.CASE_INSENSITIVE_ORDER));
        if (choices.isEmpty()) choices = List.of(new AdminItemChoice("empty", "Chưa có vật phẩm", "", Material.BARRIER, new ItemStack(Material.BARRIER)));
        openRpgItemPicker(admin, targetId, choices, 0, "player-root", "Vật phẩm người chơi", null, null, 0, null);
    }

    private void openMercenaryClasses(Player admin, UUID targetId) {
        List<AdminItemChoice> all = getAdminRpgItems(admin).stream().filter(choice -> choice.id().startsWith("army-gear/")).toList();
        Map<String, AdminItemChoice> classes = new LinkedHashMap<>();
        for (AdminItemChoice choice : all) {
            String[] parts = choice.id().split("/");
            if (parts.length < 6) continue;
            classes.putIfAbsent(parts[1], new AdminItemChoice("gear-class:" + parts[1], mercenaryClassName(parts[1]),
                "Class lính", Material.IRON_SWORD, new ItemStack(Material.IRON_SWORD)));
        }
        openRpgItemPicker(admin, targetId, new ArrayList<>(classes.values()), 0, "root", "Trang bị lính • Chọn loại lính", null, null, 0, null);
    }

    private void openMercenaryMaterials(Player admin, UUID targetId, String classId) {
        List<AdminItemChoice> all = gearItems(getAdminRpgItems(admin), classId, null, 0, null);
        Map<String, AdminItemChoice> materials = new LinkedHashMap<>();
        for (AdminItemChoice choice : all) {
            String[] parts = choice.id().split("/");
            if (parts.length < 6) continue;
            materials.putIfAbsent(parts[3], new AdminItemChoice("gear-material:" + parts[3], gearMaterialName(parts[3]),
                "Vật liệu", Material.IRON_INGOT, new ItemStack(Material.IRON_INGOT)));
        }
        openRpgItemPicker(admin, targetId, new ArrayList<>(materials.values()), 0, "gear-class", "Trang bị lính • Vật liệu",
            classId, null, 0, null);
    }

    private void openMercenaryStars(Player admin, UUID targetId, String classId, String material) {
        List<AdminItemChoice> all = gearItems(getAdminRpgItems(admin), classId, material, 0, null);
        Map<Integer, AdminItemChoice> stars = new LinkedHashMap<>();
        for (AdminItemChoice choice : all) {
            String[] parts = choice.id().split("/");
            if (parts.length < 6) continue;
            int tier = Integer.parseInt(parts[4]);
            stars.putIfAbsent(tier, new AdminItemChoice("gear-star:" + tier, "§e" + "★".repeat(tier),
                "Bậc sao", Material.NETHER_STAR, new ItemStack(Material.NETHER_STAR)));
        }
        openRpgItemPicker(admin, targetId, new ArrayList<>(stars.values()), 0, "gear-material", "Trang bị lính • Bậc sao",
            classId, material, 0, null);
    }

    private void openMercenarySets(Player admin, UUID targetId, String classId, String material, int stars) {
        List<AdminItemChoice> all = gearItems(getAdminRpgItems(admin), classId, material, stars, null);
        Map<String, AdminItemChoice> sets = new LinkedHashMap<>();
        for (AdminItemChoice choice : all) {
            String[] parts = choice.id().split("/");
            if (parts.length < 6) continue;
            sets.putIfAbsent(parts[2], new AdminItemChoice("gear-set:" + parts[2], parts[2], "Set trang bị",
                Material.CHEST, new ItemStack(Material.CHEST)));
        }
        openRpgItemPicker(admin, targetId, new ArrayList<>(sets.values()), 0, "gear-star", "Trang bị lính • Set",
            classId, material, stars, null);
    }

    private void openMercenarySlots(Player admin, UUID targetId, String classId, String material, int stars, String setId) {
        List<AdminItemChoice> items = gearItems(getAdminRpgItems(admin), classId, material, stars, setId);
        items = new ArrayList<>(items);
        items.add(new AdminItemChoice("gear-give-set", "§aCấp nguyên set", "Gồm " + items.size() + " món hợp lệ",
            Material.CHEST, new ItemStack(Material.CHEST)));
        openRpgItemPicker(admin, targetId, items, 0, "gear-set", "Trang bị lính • Chọn món",
            classId, material, stars, setId);
    }

    private List<AdminItemChoice> gearItems(List<AdminItemChoice> all, String classId, String material, int stars, String setId) {
        return all.stream().filter(choice -> choice.id().startsWith("army-gear/"))
            .filter(choice -> { String[] p = choice.id().split("/"); return p.length >= 6 && (classId == null || p[1].equals(classId)); })
            .filter(choice -> { String[] p = choice.id().split("/"); return material == null || p[3].equals(material); })
            .filter(choice -> { String[] p = choice.id().split("/"); return stars <= 0 || Integer.parseInt(p[4]) == stars; })
            .filter(choice -> { String[] p = choice.id().split("/"); return setId == null || p[2].equals(setId); })
            .toList();
    }

    private String mercenaryClassName(String id) {
        return switch (id) {
            case "archer" -> "Xạ Thủ"; case "guardian" -> "Vệ Binh"; case "medic" -> "Y Sư";
            case "berserker" -> "Chiến binh"; case "ranger" -> "Trinh Sát"; case "paladin" -> "Kỵ sĩ";
            case "pyromancer" -> "Pháp sư lửa"; case "druid" -> "Tu sĩ rừng"; case "assassin" -> "Sát thủ";
            case "frost_mage" -> "Pháp sư băng"; default -> id;
        };
    }

    private String gearMaterialName(String id) {
        return switch (id) { case "leather" -> "Da"; case "copper" -> "Đồng"; case "iron" -> "Sắt";
            case "gold" -> "Vàng"; case "diamond" -> "Kim cương"; case "netherite" -> "Netherite"; default -> id; };
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
        giveAdminRpgItems(admin, targetId, List.of(choice), choice.name());
    }

    private void giveAdminRpgItems(Player admin, UUID targetId, List<AdminItemChoice> choices, String description) {
        if (choices.isEmpty()) {
            admin.sendMessage("§cSet này chưa có món hợp lệ để cấp.");
            return;
        }
        PlayerProfileDatabaseManager database = plugin.getPlayerProfileDatabaseManager();
        String transaction = "admin-rpg-item:" + UUID.randomUUID();
        List<ItemStack> items = choices.stream().map(choice -> {
            ItemStack item = choice.item().clone();
            item.setAmount(1);
            return item;
        }).toList();
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            database.executeGameplayDatabaseTask("player-admin.give-rpg-item actor=" + admin.getName()
                    + " target=" + targetId + " item=" + description, () -> {
                database.enqueueMerchantDeliveries(targetId.toString(), transaction,
                    items.stream().map(ItemStack::serializeAsBytes).toList(), java.time.Instant.now().toString());
                return true;
            }).whenComplete((ignored, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (failure != null) {
                    plugin.getLogger().log(Level.SEVERE, "[PlayerAdmin] Could not queue RPG item delivery.", failure);
                    admin.sendMessage("§cKhông thể xếp item vào kho nhận.");
                } else {
                    audit(admin, Bukkit.getOfflinePlayer(targetId).getName(), "give-rpg-item:" + description);
                    admin.sendMessage("§aĐã xếp §f" + description + "§a (" + items.size() + " món) vào kho nhận offline.");
                }
            }));
            return;
        }
        List<ItemStack> overflowItems = new ArrayList<>();
        for (ItemStack item : items) overflowItems.addAll(target.getInventory().addItem(item).values());
        if (overflowItems.isEmpty()) {
            target.saveData();
            audit(admin, target.getName(), "give-rpg-item:" + description);
            admin.sendMessage("§aĐã cấp §f" + description + "§a (" + items.size() + " món) cho " + target.getName() + ".");
            return;
        }
        List<byte[]> overflow = overflowItems.stream().map(ItemStack::serializeAsBytes).toList();
        database.executeGameplayDatabaseTask("player-admin.give-rpg-overflow actor=" + admin.getName()
                + " target=" + targetId + " item=" + description, () -> {
            database.enqueueMerchantDeliveries(targetId.toString(), transaction, overflow,
                java.time.Instant.now().toString());
            return true;
        }).whenComplete((ignored, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (failure != null) {
                plugin.getLogger().log(Level.SEVERE, "[PlayerAdmin] Could not queue RPG item overflow.", failure);
                admin.sendMessage("§cKhông thể lưu phần item tràn vào kho nhận; hãy kiểm tra console.");
            } else {
                if (target.isOnline()) target.saveData();
                audit(admin, target.getName(), "give-rpg-item:" + description + ":queued");
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
        private String gearClass;
        private String gearMaterial;
        private String gearSet;
        private int gearStars;
        private Inventory inventory;
        private AdminHolder(UUID viewer, String screen, int page) {
            this.viewer = viewer; this.screen = screen; this.page = page;
        }
        @Override public Inventory getInventory() { return inventory; }
    }

    private record AdminItemChoice(String id, String name, String category, Material material, ItemStack item) { }
    private record PendingInput(String type, UUID target) { }
}
