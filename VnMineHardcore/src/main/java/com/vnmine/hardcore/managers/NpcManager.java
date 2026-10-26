package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.yaml.snakeyaml.error.YAMLException;

public final class NpcManager implements Listener, CommandExecutor, TabCompleter {
    private static final String ADMIN_PERMISSION = "vnmine.command.npc.admin";
    private static final int HARD_MAX_NPCS = 100;
    private static final int SCHEMA_VERSION = 1;
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    private final VnMineHardcore plugin;
    private final File storageFile;
    private final NamespacedKey npcIdKey;
    private final Map<String, NpcDefinitionLoader.NpcDefinition> definitions;
    private final Map<String, NpcRecord> npcs = new LinkedHashMap<>();
    private final Map<String, LinkedHashMap<Integer, MerchantListing>> merchantListings = new LinkedHashMap<>();
    private final Map<String, LinkedHashMap<Integer, MerchantBuyOrder>> merchantBuyOrders = new LinkedHashMap<>();
    private final Map<UUID, PendingNpcInput> pendingNpcInputs = new ConcurrentHashMap<>();
    private final int maxNpcs;

    public NpcManager(VnMineHardcore plugin,
                      Map<String, NpcDefinitionLoader.NpcDefinition> definitions)
            throws IOException, InvalidConfigurationException {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "npcs.yml");
        this.npcIdKey = new NamespacedKey(plugin, "npc_id");
        this.definitions = definitions;
        this.maxNpcs = configuredNpcLimit();
        loadNpcs();
        if (!storageFile.isFile()) saveNpcs();
        reconcileLoadedNpcs();
    }

    public boolean isManagedNpc(Entity entity) {
        return entity.getPersistentDataContainer().has(npcIdKey, PersistentDataType.STRING);
    }

    public java.util.Optional<NpcTarget> getNpcTarget(String npcId) {
        if (npcId == null) return java.util.Optional.empty();
        NpcRecord npc = npcs.get(npcId.toLowerCase(Locale.ROOT));
        if (npc == null) return java.util.Optional.empty();
        World world = Bukkit.getWorld(npc.world());
        Location location = world == null ? null : toLocation(npc, world);
        return java.util.Optional.of(new NpcTarget(npc.world(), location));
    }

    public java.util.Optional<String> getNpcDisplayName(String npcId) {
        NpcRecord npc = npcId == null ? null : npcs.get(npcId.toLowerCase(Locale.ROOT));
        return npc == null ? java.util.Optional.empty() : java.util.Optional.of(npc.displayName());
    }

    public java.util.Optional<String> getNpcType(String npcId) {
        NpcRecord npc = npcId == null ? null : npcs.get(npcId.toLowerCase(Locale.ROOT));
        return npc == null ? java.util.Optional.empty() : java.util.Optional.of(npc.type());
    }

    public boolean hasNpc(String npcId) {
        return npcId != null && npcs.containsKey(npcId.toLowerCase(Locale.ROOT));
    }

    public java.util.Optional<MerchantBuyQuote> getBestMerchantBuyQuote(ItemStack item) {
        if (item == null || item.getType().isAir()) return java.util.Optional.empty();
        MerchantBuyQuote best = null;
        for (Map.Entry<String, LinkedHashMap<Integer, MerchantBuyOrder>> merchant : merchantBuyOrders.entrySet()) {
            for (MerchantBuyOrder order : merchant.getValue().values()) {
                if (!order.item().isSimilar(item)) continue;
                MerchantBuyQuote quote = new MerchantBuyQuote(order.amount(), order.price(), merchant.getKey());
                if (best == null || (double) quote.price() / quote.amount() > (double) best.price() / best.amount())
                    best = quote;
            }
        }
        return java.util.Optional.ofNullable(best);
    }

    public void openAdminMenu(Player player) {
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            player.sendMessage("§cBạn không có quyền quản lý NPC.");
            return;
        }
        openNpcList(player, 0);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cBạn không có quyền quản lý NPC.");
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "create" -> createNpc(sender, args);
                case "edit" -> editNpc(sender, args);
                case "rename" -> renameNpc(sender, args);
                case "move" -> moveNpc(sender, args);
                case "remove" -> removeNpc(sender, args);
                case "list" -> listNpcs(sender);
                case "info" -> showNpcInfo(sender, args);
                default -> sendUsage(sender);
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "[NPC] Could not save NPC data.", exception);
            sender.sendMessage("§cKhông thể lưu dữ liệu NPC. Kiểm tra console để biết chi tiết.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("create", "edit", "rename", "move", "remove", "list", "info"), args[0]);
        }
        if (args.length == 2 && List.of("edit", "rename", "move", "remove", "info")
                .contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(new ArrayList<>(npcs.keySet()), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            return filter(NpcDefinitionLoader.supportedTypes().stream().sorted().toList(), args[2]);
        }
        return List.of();
    }

    private void openNpcList(Player player, int page) {
        List<NpcRecord> records = npcs.values().stream().sorted(Comparator.comparing(NpcRecord::id)).toList();
        int safePage = Math.max(0, Math.min(page, Math.max(0, (records.size() - 1) / 45)));
        NpcMenuHolder holder = new NpcMenuHolder(player.getUniqueId(), NpcMenu.LIST, null, safePage);
        Inventory menu = Bukkit.createInventory(holder, 54, "Quản lý NPC • " + (safePage + 1));
        holder.inventory = menu;
        for (int index = 0; index < 45; index++) {
            int recordIndex = safePage * 45 + index;
            if (recordIndex >= records.size()) break;
            NpcRecord npc = records.get(recordIndex);
            menu.setItem(index, createDialogueItem(Material.VILLAGER_SPAWN_EGG, "§e" + npc.displayName(), List.of(
                "§7ID: §f" + npc.id(), "§7Loại: §b" + npc.type(),
                "§7Vị trí: §f" + npc.world() + " " + formatCoordinate(npc.x()) + ", "
                    + formatCoordinate(npc.y()) + ", " + formatCoordinate(npc.z()),
                "§aNhấn để chỉnh sửa")));
            holder.npcSlots.put(index, npc.id());
        }
        menu.setItem(45, createDialogueItem(Material.LIME_DYE, "§aTạo NPC", List.of(
            "§7Tạo tại vị trí hiện tại của bạn.", "§eNhấn để nhập ID, loại và tên.")));
        if (safePage > 0) menu.setItem(48, createDialogueItem(Material.ARROW, "§eTrang trước", List.of()));
        menu.setItem(49, createDialogueItem(Material.BARRIER, "§cVề menu admin", List.of()));
        if ((safePage + 1) * 45 < records.size())
            menu.setItem(50, createDialogueItem(Material.ARROW, "§eTrang sau", List.of()));
        player.openInventory(menu);
    }

    private void openNpcEditor(Player player, String npcId, int page) {
        NpcRecord npc = npcs.get(npcId);
        if (npc == null) {
            openNpcList(player, page);
            return;
        }
        NpcMenuHolder holder = new NpcMenuHolder(player.getUniqueId(), NpcMenu.EDIT, npcId, page);
        Inventory menu = Bukkit.createInventory(holder, 54, "Sửa NPC • " + npc.id());
        holder.inventory = menu;
        menu.setItem(4, createDialogueItem(Material.VILLAGER_SPAWN_EGG, "§e" + npc.displayName(), List.of(
            "§7ID cố định: §f" + npc.id(), "§7Loại: §b" + npc.type())));
        menu.setItem(10, createDialogueItem(Material.NAME_TAG, "§eĐổi tên hiển thị", List.of(
            "§7Hiện tại: §f" + npc.displayName(), "§7Tối đa 64 ký tự; hỗ trợ mã màu &.")));
        menu.setItem(12, createDialogueItem(Material.WRITABLE_BOOK, "§eĐổi loại NPC", List.of(
            "§7Hiện tại: §f" + npc.type(), "§7Nhập một loại được liệt kê trong prompt chat.")));
        menu.setItem(14, createDialogueItem(Material.COMPASS, "§bDi chuyển tới vị trí hiện tại", List.of(
            "§7Thế giới: §f" + npc.world(), "§7" + formatCoordinate(npc.x()) + ", "
                + formatCoordinate(npc.y()) + ", " + formatCoordinate(npc.z()), "§eNhấn để di chuyển NPC.")));
        menu.setItem(16, createDialogueItem(Material.REDSTONE, "§cXóa NPC", List.of(
            "§7Sẽ xóa thực thể NPC và dữ liệu shop liên quan.", "§eNhấn để xác nhận.")));
        if (npc.type().equals("story")) {
            String chapterId = npc.storyChapterId() == null ? "(chưa chọn)" : npc.storyChapterId();
            menu.setItem(18, createDialogueItem(Material.WRITTEN_BOOK, "§dChọn chapter cốt truyện", List.of(
                "§7Chapter ID: §f" + chapterId, "§eNhấn để chọn nội dung NPC sẽ kể.")));
        }
        menu.setItem(49, createDialogueItem(Material.ARROW, "§eQuay lại danh sách", List.of()));
        player.openInventory(menu);
    }

    private void openNpcDeleteConfirmation(Player player, String npcId, int page) {
        NpcMenuHolder holder = new NpcMenuHolder(player.getUniqueId(), NpcMenu.CONFIRM_DELETE, npcId, page);
        Inventory menu = Bukkit.createInventory(holder, 54, "Xác nhận xóa NPC");
        holder.inventory = menu;
        menu.setItem(4, createDialogueItem(Material.VILLAGER_SPAWN_EGG, "§cXóa NPC §f" + npcId,
            List.of("§7Hành động này không thể hoàn tác.")));
        menu.setItem(11, createDialogueItem(Material.LIME_DYE, "§aXác nhận xóa", List.of()));
        menu.setItem(15, createDialogueItem(Material.RED_DYE, "§eHủy", List.of()));
        player.openInventory(menu);
    }

    private void openStoryChapterSelector(Player player, String npcId, int requestedPage) {
        NpcRecord npc = npcs.get(npcId);
        if (npc == null) { openNpcList(player, 0); return; }
        List<StoryChapterLoader.StoryChapter> chapters = List.copyOf(
            plugin.getConfigManager().getStoryChapters().values());
        int pageCount = Math.max(1, (chapters.size() + 44) / 45);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        NpcMenuHolder holder = new NpcMenuHolder(player.getUniqueId(), NpcMenu.STORY_CHAPTERS, npcId, page);
        Inventory menu = Bukkit.createInventory(holder, 54, "Chọn chapter • " + npc.id());
        holder.inventory = menu;
        for (int slot = 0; slot < 45 && page * 45 + slot < chapters.size(); slot++) {
            StoryChapterLoader.StoryChapter chapter = chapters.get(page * 45 + slot);
            boolean selected = chapter.id().equals(npc.storyChapterId());
            menu.setItem(slot, createDialogueItem(selected ? Material.ENCHANTED_BOOK : Material.WRITTEN_BOOK,
                (selected ? "§a✓ " : "§f") + chapter.order() + ". " + chapter.title(), List.of(
                    "§7ID: §f" + chapter.id(), "§7" + chapter.summary(), "§eNhấn để gán cho NPC.")));
            holder.npcSlots.put(slot, chapter.id());
        }
        if (page > 0) menu.setItem(45, createDialogueItem(Material.ARROW, "§eTrang trước", List.of()));
        menu.setItem(46, createDialogueItem(Material.BARRIER, "§cBỏ chọn chapter", List.of()));
        menu.setItem(49, createDialogueItem(Material.ARROW, "§eQuay lại chỉnh NPC", List.of()));
        if (page + 1 < pageCount) menu.setItem(50, createDialogueItem(Material.ARROW, "§eTrang sau", List.of()));
        menu.setItem(53, createDialogueItem(Material.PAPER, "§fTrang " + (page + 1) + "/" + pageCount,
            List.of("§7Tổng chapter: " + chapters.size())));
        if (chapters.isEmpty()) menu.setItem(22, createDialogueItem(Material.BARRIER, "§cChưa có chapter truyện", List.of()));
        player.openInventory(menu);
    }

    private void saveStoryChapterSelection(Player player, String npcId, String chapterId, int page) {
        NpcRecord old = npcs.get(npcId);
        if (old == null) { openNpcList(player, 0); return; }
        if (chapterId != null && !plugin.getConfigManager().getStoryChapters().containsKey(chapterId)) {
            player.sendMessage("§cChapter không tồn tại trong cấu hình truyện.");
            openStoryChapterSelector(player, npcId, page);
            return;
        }
        npcs.put(npcId, old.withStoryChapterId(chapterId));
        try {
            saveNpcs();
            player.sendMessage("§aĐã " + (chapterId == null ? "bỏ chọn chapter" : "gán chapter §f" + chapterId)
                + " §acho NPC §f" + npcId + "§a.");
        } catch (IOException exception) {
            npcs.put(npcId, old);
            reportNpcGuiError(player, exception);
        }
        openNpcEditor(player, npcId, page);
    }

    @EventHandler
    public void onNpcAdminClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof NpcMenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.viewer.equals(player.getUniqueId())
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            player.closeInventory();
            player.sendMessage("§cBạn không còn quyền quản lý NPC.");
            return;
        }
        int slot = event.getRawSlot();
        if (holder.menu == NpcMenu.LIST) {
            if (slot == 45) promptNpc(player, new PendingNpcInput(NpcTextField.CREATE_ID, null, holder.page, ""));
            else if (slot == 48) openNpcList(player, holder.page - 1);
            else if (slot == 50) openNpcList(player, holder.page + 1);
            else if (slot == 49) plugin.getMercenaryManager().openAdminMenu(player);
            else if (holder.npcSlots.containsKey(slot)) openNpcEditor(player, holder.npcSlots.get(slot), holder.page);
            return;
        }
        if (holder.menu == NpcMenu.CONFIRM_DELETE) {
            if (slot == 11) {
                try {
                    removeNpc(player, new String[]{"remove", holder.npcId});
                } catch (IOException exception) {
                    reportNpcGuiError(player, exception);
                }
                openNpcList(player, holder.page);
            } else if (slot == 15) openNpcEditor(player, holder.npcId, holder.page);
            return;
        }
        if (holder.menu == NpcMenu.STORY_CHAPTERS) {
            if (slot == 45 && holder.page > 0) openStoryChapterSelector(player, holder.npcId, holder.page - 1);
            else if (slot == 46) saveStoryChapterSelection(player, holder.npcId, null, holder.page);
            else if (slot == 49) openNpcEditor(player, holder.npcId, holder.page);
            else if (slot == 50) openStoryChapterSelector(player, holder.npcId, holder.page + 1);
            else if (holder.npcSlots.containsKey(slot))
                saveStoryChapterSelection(player, holder.npcId, holder.npcSlots.get(slot), holder.page);
            return;
        }
        if (slot == 49) openNpcList(player, holder.page);
        else if (slot == 10) promptNpc(player, new PendingNpcInput(NpcTextField.RENAME, holder.npcId, holder.page, ""));
        else if (slot == 12) promptNpc(player, new PendingNpcInput(NpcTextField.CHANGE_TYPE, holder.npcId, holder.page, ""));
        else if (slot == 14) {
            try {
                moveNpc(player, new String[]{"move", holder.npcId});
            } catch (IOException exception) {
                reportNpcGuiError(player, exception);
            }
            openNpcEditor(player, holder.npcId, holder.page);
        } else if (slot == 16) openNpcDeleteConfirmation(player, holder.npcId, holder.page);
        else if (slot == 18) {
            NpcRecord npc = npcs.get(holder.npcId);
            if (npc != null && "story".equals(npc.type())) openStoryChapterSelector(player, holder.npcId, 0);
        }
    }

    @EventHandler
    public void onNpcAdminDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof NpcMenuHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onNpcAdminChat(AsyncPlayerChatEvent event) {
        PendingNpcInput pending = pendingNpcInputs.remove(event.getPlayer().getUniqueId());
        if (pending == null) return;
        event.setCancelled(true);
        String text = event.getMessage().trim();
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> handleNpcTextInput(player, pending, text));
    }

    @EventHandler
    public void onNpcAdminQuit(PlayerQuitEvent event) {
        pendingNpcInputs.remove(event.getPlayer().getUniqueId());
    }

    private void promptNpc(Player player, PendingNpcInput input) {
        pendingNpcInputs.put(player.getUniqueId(), input);
        String instruction = switch (input.field()) {
            case CREATE_ID -> "§eHãy nhập §fID của NPC§e (chữ thường, số, dấu gạch nối), ví dụ: §fnpc-test";
            case CREATE_TYPE, CHANGE_TYPE -> "§eHãy nhập §floại NPC§e. Ví dụ: §fquest-giver§e. Hợp lệ: §f"
                + String.join(", ", NpcDefinitionLoader.supportedTypes().stream().sorted().toList());
            case CREATE_NAME, RENAME -> "§eHãy nhập §ftên NPC§e (1-64 ký tự, hỗ trợ màu &), ví dụ: §fThợ săn &6Cổ đại";
        };
        player.closeInventory();
        player.sendMessage("§6[Quản lý NPC] " + instruction);
        player.sendMessage("§7Gõ §c!cancel §7để hủy và quay lại giao diện NPC.");
    }

    private void handleNpcTextInput(Player player, PendingNpcInput input, String text) {
        if (!player.isOnline()) return;
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            player.sendMessage("§cBạn không còn quyền quản lý NPC.");
            return;
        }
        if (text.equalsIgnoreCase("!cancel")) {
            reopenNpcEditor(player, input);
            return;
        }
        try {
            switch (input.field()) {
                case CREATE_ID -> {
                    String id = text.toLowerCase(Locale.ROOT);
                    if (!isValidId(id) || npcs.containsKey(id)) {
                        player.sendMessage("§cID NPC không hợp lệ hoặc đã tồn tại; ví dụ hợp lệ: §fnpc-test");
                        promptNpc(player, input);
                        return;
                    }
                    promptNpc(player, new PendingNpcInput(NpcTextField.CREATE_TYPE, id, input.page(), ""));
                }
                case CREATE_TYPE -> {
                    String type = text.toLowerCase(Locale.ROOT);
                    if (!NpcDefinitionLoader.supportedTypes().contains(type)) {
                        player.sendMessage("§cLoại NPC không hợp lệ. Ví dụ: §fquest-giver§c.");
                        promptNpc(player, input);
                        return;
                    }
                    promptNpc(player, new PendingNpcInput(NpcTextField.CREATE_NAME, input.npcId(),
                        input.page(), type));
                }
                case CREATE_NAME -> {
                    if (!isValidDisplayName(text)) {
                        player.sendMessage("§cTên cần 1-64 ký tự. Ví dụ: §fThợ săn &6Cổ đại");
                        promptNpc(player, input);
                        return;
                    }
                    if (npcs.size() >= maxNpcs) {
                        player.sendMessage("§cĐã đạt giới hạn " + maxNpcs + " NPC.");
                        openNpcList(player, input.page());
                        return;
                    }
                    createNpc(player, new String[]{"create", input.npcId(), input.value(), text});
                    if (npcs.containsKey(input.npcId())) openNpcEditor(player, input.npcId(), input.page());
                    else openNpcList(player, input.page());
                }
                case RENAME -> {
                    if (!isValidDisplayName(text)) {
                        player.sendMessage("§cTên cần 1-64 ký tự. Ví dụ: §fThợ săn &6Cổ đại");
                        promptNpc(player, input);
                        return;
                    }
                    renameNpc(player, new String[]{"rename", input.npcId(), text});
                    openNpcEditor(player, input.npcId(), input.page());
                }
                case CHANGE_TYPE -> {
                    String type = text.toLowerCase(Locale.ROOT);
                    if (!NpcDefinitionLoader.supportedTypes().contains(type)) {
                        player.sendMessage("§cLoại NPC không hợp lệ. Ví dụ: §fquest-giver§c.");
                        promptNpc(player, input);
                        return;
                    }
                    NpcRecord old = npcs.get(input.npcId());
                    if (old == null) {
                        openNpcList(player, input.page());
                        return;
                    }
                    if (!old.type().equals("merchant") && type.equals("merchant")
                            && !plugin.getConfig().getBoolean("economy.merchant.custom.enabled", true)) {
                        player.sendMessage("§cCửa hàng NPC tùy chỉnh đang bị tắt.");
                        openNpcEditor(player, input.npcId(), input.page());
                        return;
                    }
                    if (!type.equals("merchant") && (!merchantListings.getOrDefault(old.id(), new LinkedHashMap<>()).isEmpty()
                            || !merchantBuyOrders.getOrDefault(old.id(), new LinkedHashMap<>()).isEmpty())) {
                        player.sendMessage("§cHãy xóa hết hàng mua/bán của merchant trước khi đổi loại NPC.");
                        openNpcEditor(player, input.npcId(), input.page());
                        return;
                    }
                    npcs.put(old.id(), old.withType(type));
                    try {
                        saveNpcs();
                        player.sendMessage("§aĐã đổi loại NPC §f" + old.id() + " §asang §f" + type + "§a.");
                    } catch (IOException exception) {
                        npcs.put(old.id(), old);
                        throw exception;
                    }
                    openNpcEditor(player, input.npcId(), input.page());
                }
            }
        } catch (IOException | RuntimeException exception) {
            reportNpcGuiError(player, exception);
            reopenNpcEditor(player, input);
        }
    }

    private void reopenNpcEditor(Player player, PendingNpcInput input) {
        if (input.field() == NpcTextField.CREATE_ID || input.field() == NpcTextField.CREATE_TYPE
                || input.field() == NpcTextField.CREATE_NAME) openNpcList(player, input.page());
        else openNpcEditor(player, input.npcId(), input.page());
    }

    private void reportNpcGuiError(Player player, Exception exception) {
        plugin.getLogger().log(Level.SEVERE, "[NPC] GUI operation failed for " + player.getName(), exception);
        player.sendMessage("§cKhông thể cập nhật NPC. Xem console để biết chi tiết.");
    }

    private static String formatCoordinate(double coordinate) {
        return String.format(Locale.ROOT, "%.1f", coordinate);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        reconcileChunk(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        for (NpcRecord npc : List.copyOf(npcs.values())) {
            if (!npc.world().equals(event.getWorld().getName())) continue;
            int chunkX = chunkCoordinate(npc.x());
            int chunkZ = chunkCoordinate(npc.z());
            if (event.getWorld().isChunkLoaded(chunkX, chunkZ)) {
                reconcileChunk(event.getWorld().getChunkAt(chunkX, chunkZ));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNpcInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isManagedNpc(event.getRightClicked())) return;
        event.setCancelled(true);
        String id = event.getRightClicked().getPersistentDataContainer()
            .get(npcIdKey, PersistentDataType.STRING);
        NpcRecord npc = id == null ? null : npcs.get(id);
        if (npc == null) {
            plugin.getLogger().warning("[NPC] Managed entity " + event.getRightClicked().getUniqueId()
                + " has no matching runtime record.");
            event.getPlayer().sendMessage("§cKhông tìm thấy dữ liệu của NPC này.");
            return;
        }
        Player player = event.getPlayer();
        if (player.isSneaking() && player.hasPermission(ADMIN_PERMISSION)
                && ("story".equals(npc.type()) || "quest-giver".equals(npc.type()))) {
            openNpcEditor(player, npc.id(), 0);
            return;
        }
        if (plugin.getCharacterSelectionManager() != null
                && plugin.getCharacterSelectionManager().recordTutorialNpcVisit(player, npc.id())) {
            return;
        }
        if (plugin.getQuestCatalogManager() != null) {
            plugin.getQuestCatalogManager().recordNpcVisit(player, npc.id());
        }
        if ("system-merchant".equals(npc.type())) {
            plugin.getEconomyManager().openMerchantMenu(player);
            return;
        }
        if ("merchant".equals(npc.type())) {
            if (player.isSneaking() && player.hasPermission(ADMIN_PERMISSION)) {
                openMerchantEditor(player, npc.id(), copyListings(merchantListings.get(npc.id())));
                return;
            }
            List<EconomyManager.CustomMerchantOffer> offers = merchantListings
                .getOrDefault(npc.id(), new LinkedHashMap<>()).values().stream()
                .map(listing -> new EconomyManager.CustomMerchantOffer(listing.item().clone(), listing.price()))
                .toList();
            List<EconomyManager.CustomMerchantBuyOrder> buyOrders = merchantBuyOrders
                .getOrDefault(npc.id(), new LinkedHashMap<>()).values().stream()
                .map(order -> new EconomyManager.CustomMerchantBuyOrder(order.item().clone(),
                    order.amount(), order.price())).toList();
            plugin.getEconomyManager().openCustomMerchantMenu(player, npc.id(),
                dialogueTitle(npc.displayName()), offers, buyOrders);
            return;
        }
        if ("story".equals(npc.type())) {
            StoryChapterLoader.StoryChapter chapter = npc.storyChapterId() == null ? null
                : plugin.getConfigManager().getStoryChapters().get(npc.storyChapterId());
            if (chapter == null && npc.storyChapterId() != null) {
                plugin.getLogger().warning("[NPC] Story NPC '" + npc.id() + "' references missing chapter '"
                    + npc.storyChapterId() + "'.");
            }
            plugin.getQuestDialogManager().openStoryChapter(player,
                ChatColor.stripColor(formatDisplayName(npc.displayName())), chapter);
            return;
        }
        openNpcDialogue(player, npc, 0);
    }

    private void openNpcDialogue(Player player, NpcRecord npc, int actionPage) {
        NpcDefinitionLoader.NpcDefinition definition = findDialogueDefinition(npc.id(), npc.type(), definitions);
        if (definition == null) {
            definition = createFallbackDialogueDefinition(npc);
        }
        openDialogue(player, npc, definition, actionPage);
    }

    @EventHandler
    public void onDialogueClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof DialogueHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.viewer.equals(player.getUniqueId())
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (event.getRawSlot() == 49) {
            player.closeInventory();
            return;
        }
        if (event.getRawSlot() == 46 || event.getRawSlot() == 48) {
            NpcRecord npc = npcs.get(holder.npcId);
            if (npc != null) openNpcDialogue(player, npc, holder.page + (event.getRawSlot() == 46 ? -1 : 1));
            return;
        }
        NpcDefinitionLoader.NpcAction action = holder.actions.get(event.getRawSlot());
        if (action == null) return;
        player.closeInventory();
        switch (action.type()) {
            case "open-skill-tree" -> plugin.getSkillManager().openSkillTreeFromNpc(player);
            case "open-quest-journal" -> plugin.getTutorialQuestManager().openJournal(player);
            case "open-story" -> plugin.getMercenaryManager().openStoryJournal(player);
            case "open-merchant" -> plugin.getEconomyManager().openMerchantMenu(player);
            case "give-skill-book" -> plugin.getSkillManager().giveSkillBookFromNpc(player, action.bookId());
            case "accept-quest", "quest-interaction" -> plugin.getQuestCatalogManager()
                .handleNpcQuestInteraction(player, holder.npcId, action.questId());
            case "submit-quest" -> plugin.getQuestCatalogManager().submitQuestItems(player, holder.npcId,
                action.questId());
            default -> plugin.getLogger().warning("[NPC] Unsupported runtime action '" + action.type() + "'.");
        }
    }

    @EventHandler
    public void onDialogueDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof DialogueHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMerchantEditorClick(InventoryClickEvent event) {
        InventoryHolder rawHolder = event.getView().getTopInventory().getHolder();
        if (!(rawHolder instanceof MerchantEditorHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.viewer.equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
                ItemStack source = event.getCurrentItem();
                if (source != null && isSystemCreatedItem(source)) {
                    int slot = firstEmptyShopSlot(holder.draft);
                    if (slot < 0) {
                        player.sendMessage("§cCửa hàng đã đủ " + configuredMerchantMaxOffers() + " mặt hàng.");
                        return;
                    }
                    holder.draft.put(slot, new MerchantListing(source.clone(), 1L));
                    event.setCurrentItem(null);
                    openMerchantEditor(player, holder.merchantId, holder.draft);
                } else {
                    player.sendMessage("§cChỉ có thể thêm vật phẩm RPG do hệ thống tạo.");
                }
            }
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == 45) {
            saveMerchantDraft(player, holder.merchantId, holder.draft);
            return;
        }
        if (slot == 47) {
            openMerchantBuyEditor(player, holder.merchantId,
                copyBuyOrders(merchantBuyOrders.get(holder.merchantId)));
            return;
        }
        if (slot == 53) {
            player.closeInventory();
            return;
        }
        if (slot < 0 || slot >= 45) return;
        ItemStack cursor = event.getCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            if (!isSystemCreatedItem(cursor)) {
                player.sendMessage("§cChỉ có thể đặt vật phẩm RPG do hệ thống tạo.");
                return;
            }
            long price = holder.draft.containsKey(slot) ? holder.draft.get(slot).price() : 1L;
            holder.draft.put(slot, new MerchantListing(cursor.clone(), price));
            event.setCursor(null);
            openMerchantEditor(player, holder.merchantId, holder.draft);
            return;
        }
        MerchantListing listing = holder.draft.get(slot);
        if (listing == null) return;
        if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
            holder.draft.remove(slot);
            openMerchantEditor(player, holder.merchantId, holder.draft);
            return;
        }
        openMerchantPriceEditor(player, holder.merchantId, holder.draft, slot);
    }

    @EventHandler
    public void onMerchantPriceClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MerchantPriceHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.viewer.equals(player.getUniqueId())
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        MerchantListing listing = holder.draft.get(holder.slot);
        if (listing == null) {
            openMerchantEditor(player, holder.merchantId, holder.draft);
            return;
        }
        long delta = switch (event.getRawSlot()) {
            case 10 -> -configuredMerchantPriceStep(2);
            case 11 -> -configuredMerchantPriceStep(1);
            case 12 -> -configuredMerchantPriceStep(0);
            case 14 -> configuredMerchantPriceStep(0);
            case 15 -> configuredMerchantPriceStep(1);
            case 16 -> configuredMerchantPriceStep(2);
            default -> 0L;
        };
        if (delta != 0) {
            long maxPrice = configuredMerchantMaxPrice();
            long updated = Math.max(1L, Math.min(maxPrice, listing.price() + delta));
            holder.draft.put(holder.slot, new MerchantListing(listing.item(), updated));
            openMerchantPriceEditor(player, holder.merchantId, holder.draft, holder.slot);
            return;
        }
        if (event.getRawSlot() == 22) {
            openMerchantEditor(player, holder.merchantId, holder.draft);
        } else if (event.getRawSlot() == 18) {
            openMerchantEditor(player, holder.merchantId, holder.draft);
        }
    }

    @EventHandler
    public void onMerchantEditorDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof MerchantEditorHolder) && !(holder instanceof MerchantPriceHolder)) return;
        if (event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize())) {
            event.setCancelled(true);
        }
    }

    private void openMerchantEditor(Player player, String merchantId,
                                    LinkedHashMap<Integer, MerchantListing> draft) {
        MerchantEditorHolder holder = new MerchantEditorHolder(player.getUniqueId(), merchantId, draft);
        Inventory inventory = Bukkit.createInventory(holder, 54, "VnMine • Sửa shop " + merchantId);
        holder.inventory = inventory;
        for (int slot = 45; slot < 54; slot++) {
            inventory.setItem(slot, createDialogueItem(Material.GRAY_STAINED_GLASS_PANE, "§r", List.of()));
        }
        for (Map.Entry<Integer, MerchantListing> entry : draft.entrySet()) {
            ItemStack display = entry.getValue().item().clone();
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() && meta.getLore() != null
                    ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add("§7Giá hiện tại: §6" + entry.getValue().price() + " VNCOIN");
                lore.add("§eChuột trái: sửa giá | Chuột phải: gỡ món");
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            inventory.setItem(entry.getKey(), display);
        }
        inventory.setItem(45, createDialogueItem(Material.EMERALD, "§aLưu cửa hàng",
            List.of("§7Lưu danh sách và giá vào npcs.yml.")));
        inventory.setItem(47, createDialogueItem(Material.HOPPER, "§bCấu hình giá mua vào",
            List.of("§7Đặt vật phẩm người chơi có thể bán cho shop.",
                "§7Cấu hình số lượng item đổi lấy VNCOIN.")));
        inventory.setItem(49, createDialogueItem(Material.BOOK, "§eChỉnh sửa shop",
            List.of("§7Shift-click item RPG trong túi để thêm.",
                "§7Chuột phải món trong shop để gỡ.",
                "§7Chuột trái món trong shop để chỉnh giá.",
                "§7Giá: " + configuredMerchantPriceStep(0) + ", "
                    + configuredMerchantPriceStep(1) + ", " + configuredMerchantPriceStep(2) + " VNCOIN.")));
        inventory.setItem(53, createDialogueItem(Material.BARRIER, "§cĐóng", List.of()));
        player.openInventory(inventory);
    }

    private void openMerchantBuyEditor(Player player, String merchantId,
            LinkedHashMap<Integer, MerchantBuyOrder> draft) {
        MerchantBuyEditorHolder holder = new MerchantBuyEditorHolder(player.getUniqueId(), merchantId, draft);
        Inventory inventory = Bukkit.createInventory(holder, 54, "VnMine • Giá mua vào " + merchantId);
        holder.inventory = inventory;
        for (int slot = 45; slot < 54; slot++)
            inventory.setItem(slot, createDialogueItem(Material.GRAY_STAINED_GLASS_PANE, "§r", List.of()));
        for (Map.Entry<Integer, MerchantBuyOrder> entry : draft.entrySet()) {
            MerchantBuyOrder order = entry.getValue();
            ItemStack display = order.item().clone();
            display.setAmount(Math.min(order.amount(), display.getMaxStackSize()));
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() && meta.getLore() != null
                    ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add("§7Cần giao: §f" + order.amount() + " item");
                lore.add("§7Nhận: §6" + order.price() + " VNCOIN");
                lore.add("§eChuột trái: sửa | Chuột phải: gỡ");
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            inventory.setItem(entry.getKey(), display);
        }
        inventory.setItem(45, createDialogueItem(Material.EMERALD, "§aLưu giá mua vào", List.of(
            "§7Lưu các mặt hàng và lượng VNCOIN sẽ trả.")));
        inventory.setItem(49, createDialogueItem(Material.BOOK, "§eThêm vật phẩm RPG", List.of(
            "§7Shift-click vật phẩm hệ thống trong túi để thêm.",
            "§7Chuột trái sửa số lượng và VNCOIN; phải-click gỡ.")));
        inventory.setItem(53, createDialogueItem(Material.ARROW, "§eQuay lại shop bán ra", List.of()));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onMerchantBuyEditorClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MerchantBuyEditorHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player) || !holder.viewer.equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
                ItemStack source = event.getCurrentItem();
                if (source != null && isSystemCreatedItem(source)) {
                    int target = firstEmptyBuyOrderSlot(holder.draft);
                    if (target < 0) {
                        player.sendMessage("§cDanh sách giá mua vào đã đủ " + configuredMerchantMaxOffers() + " món.");
                        return;
                    }
                    ItemStack template = source.clone();
                    template.setAmount(1);
                    holder.draft.put(target, new MerchantBuyOrder(template, 1, 1));
                    event.setCurrentItem(null);
                    openMerchantBuyEditor(player, holder.merchantId, holder.draft);
                } else if (source != null) {
                    player.sendMessage("§cChỉ có thể cấu hình vật phẩm RPG do hệ thống tạo.");
                }
            }
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == 45) {
            saveMerchantBuyOrders(player, holder.merchantId, holder.draft);
            return;
        }
        if (slot == 53) {
            openMerchantEditor(player, holder.merchantId,
                copyListings(merchantListings.get(holder.merchantId)));
            return;
        }
        if (slot < 0 || slot >= 45) return;
        ItemStack cursor = event.getCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            if (!isSystemCreatedItem(cursor)) {
                player.sendMessage("§cChỉ có thể đặt vật phẩm RPG do hệ thống tạo.");
                return;
            }
            ItemStack template = cursor.clone();
            template.setAmount(1);
            MerchantBuyOrder old = holder.draft.get(slot);
            holder.draft.put(slot, new MerchantBuyOrder(template,
                old == null ? 1 : old.amount(), old == null ? 1 : old.price()));
            event.setCursor(null);
            openMerchantBuyEditor(player, holder.merchantId, holder.draft);
            return;
        }
        if (!holder.draft.containsKey(slot)) return;
        if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
            holder.draft.remove(slot);
            openMerchantBuyEditor(player, holder.merchantId, holder.draft);
        } else {
            openMerchantBuyOrderEditor(player, holder.merchantId, holder.draft, slot);
        }
    }

    @EventHandler
    public void onMerchantBuyOrderClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MerchantBuyOrderHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.viewer.equals(player.getUniqueId())
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        MerchantBuyOrder order = holder.draft.get(holder.slot);
        if (order == null) {
            openMerchantBuyEditor(player, holder.merchantId, holder.draft);
            return;
        }
        int amountDelta = switch (event.getRawSlot()) {
            case 10 -> -100; case 11 -> -10; case 12 -> -1;
            case 14 -> 1; case 15 -> 10; case 16 -> 100; default -> 0;
        };
        long priceDelta = switch (event.getRawSlot()) {
            case 28 -> -100; case 29 -> -10; case 30 -> -1;
            case 32 -> 1; case 33 -> 10; case 34 -> 100; default -> 0L;
        };
        if (amountDelta != 0 || priceDelta != 0) {
            int amount = Math.max(1, Math.min(100_000, order.amount() + amountDelta));
            long price = Math.max(1L, Math.min(configuredMerchantMaxPrice(), order.price() + priceDelta));
            holder.draft.put(holder.slot, new MerchantBuyOrder(order.item(), amount, price));
            openMerchantBuyOrderEditor(player, holder.merchantId, holder.draft, holder.slot);
        } else if (event.getRawSlot() == 49 || event.getRawSlot() == 53) {
            openMerchantBuyEditor(player, holder.merchantId, holder.draft);
        }
    }

    @EventHandler
    public void onMerchantBuyEditorDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if ((holder instanceof MerchantBuyEditorHolder || holder instanceof MerchantBuyOrderHolder)
                && event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize())) {
            event.setCancelled(true);
        }
    }

    private void openMerchantBuyOrderEditor(Player player, String merchantId,
            LinkedHashMap<Integer, MerchantBuyOrder> draft, int slot) {
        MerchantBuyOrder order = draft.get(slot);
        if (order == null) {
            openMerchantBuyEditor(player, merchantId, draft);
            return;
        }
        MerchantBuyOrderHolder holder = new MerchantBuyOrderHolder(player.getUniqueId(), merchantId, draft, slot);
        Inventory inventory = Bukkit.createInventory(holder, 54, "VnMine • Số lượng và giá đổi");
        holder.inventory = inventory;
        for (int fill = 0; fill < inventory.getSize(); fill++)
            inventory.setItem(fill, createDialogueItem(Material.GRAY_STAINED_GLASS_PANE, "§r", List.of()));
        ItemStack item = order.item().clone();
        item.setAmount(Math.min(order.amount(), item.getMaxStackSize()));
        inventory.setItem(13, item);
        inventory.setItem(10, priceButton(Material.RED_DYE, "-100 item"));
        inventory.setItem(11, priceButton(Material.RED_DYE, "-10 item"));
        inventory.setItem(12, priceButton(Material.RED_DYE, "-1 item"));
        inventory.setItem(14, priceButton(Material.LIME_DYE, "+1 item"));
        inventory.setItem(15, priceButton(Material.LIME_DYE, "+10 item"));
        inventory.setItem(16, priceButton(Material.LIME_DYE, "+100 item"));
        inventory.setItem(22, createDialogueItem(Material.PAPER, "§eSố lượng: §f" + order.amount(),
            List.of("§7Số item cần giao để đổi lấy giá VNCOIN bên dưới.")));
        inventory.setItem(28, priceButton(Material.RED_DYE, "-100 VNCOIN"));
        inventory.setItem(29, priceButton(Material.RED_DYE, "-10 VNCOIN"));
        inventory.setItem(30, priceButton(Material.RED_DYE, "-1 VNCOIN"));
        inventory.setItem(32, priceButton(Material.LIME_DYE, "+1 VNCOIN"));
        inventory.setItem(33, priceButton(Material.LIME_DYE, "+10 VNCOIN"));
        inventory.setItem(34, priceButton(Material.LIME_DYE, "+100 VNCOIN"));
        inventory.setItem(31, createDialogueItem(Material.GOLD_INGOT, "§6Giá: §e" + order.price() + " VNCOIN",
            List.of("§7Nhận khoản tiền này khi giao đủ item.")));
        inventory.setItem(49, createDialogueItem(Material.ARROW, "§eQuay lại", List.of()));
        inventory.setItem(53, createDialogueItem(Material.LIME_DYE, "§aXong", List.of()));
        player.openInventory(inventory);
    }

    private void saveMerchantBuyOrders(Player player, String merchantId,
            LinkedHashMap<Integer, MerchantBuyOrder> draft) {
        LinkedHashMap<Integer, MerchantBuyOrder> previous = merchantBuyOrders.get(merchantId);
        if (draft.isEmpty()) merchantBuyOrders.remove(merchantId);
        else merchantBuyOrders.put(merchantId, copyBuyOrders(draft));
        try {
            saveNpcs();
            player.sendMessage("§aĐã lưu " + draft.size() + " giá mua vào cho shop " + merchantId + ".");
            plugin.getLogger().info("[NPC][Merchant] Saved buy orders: npc=" + merchantId + ", count=" + draft.size());
            if (plugin.getEconomyManager() != null) plugin.getEconomyManager().refreshAllGroundItemNames();
            openMerchantEditor(player, merchantId, copyListings(merchantListings.get(merchantId)));
        } catch (IOException exception) {
            if (previous == null) merchantBuyOrders.remove(merchantId);
            else merchantBuyOrders.put(merchantId, previous);
            plugin.getLogger().log(Level.SEVERE, "[NPC][Merchant] Could not save buy orders for " + merchantId, exception);
            player.sendMessage("§cKhông lưu được giá mua vào; dữ liệu cũ được khôi phục.");
            openMerchantBuyEditor(player, merchantId, draft);
        }
    }

    private int firstEmptyBuyOrderSlot(Map<Integer, MerchantBuyOrder> draft) {
        for (int slot = 0; slot < Math.min(45, configuredMerchantMaxOffers()); slot++)
            if (!draft.containsKey(slot)) return slot;
        return -1;
    }

    private LinkedHashMap<Integer, MerchantBuyOrder> copyBuyOrders(Map<Integer, MerchantBuyOrder> source) {
        LinkedHashMap<Integer, MerchantBuyOrder> copy = new LinkedHashMap<>();
        if (source != null) source.forEach((slot, order) -> copy.put(slot,
            new MerchantBuyOrder(order.item(), order.amount(), order.price())));
        return copy;
    }

    private void openMerchantPriceEditor(Player player, String merchantId,
                                         LinkedHashMap<Integer, MerchantListing> draft, int slot) {
        MerchantListing listing = draft.get(slot);
        if (listing == null) {
            openMerchantEditor(player, merchantId, draft);
            return;
        }
        MerchantPriceHolder holder = new MerchantPriceHolder(player.getUniqueId(), merchantId, draft, slot);
        Inventory inventory = Bukkit.createInventory(holder, 54, "VnMine • Giá VNCOIN");
        holder.inventory = inventory;
        ItemStack item = listing.item().clone();
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta != null) {
            List<String> lore = itemMeta.hasLore() && itemMeta.getLore() != null
                ? new ArrayList<>(itemMeta.getLore()) : new ArrayList<>();
            lore.add("§7Giá: §6" + listing.price() + " VNCOIN");
            itemMeta.setLore(lore);
            item.setItemMeta(itemMeta);
        }
        inventory.setItem(13, item);
        inventory.setItem(10, priceButton(Material.RED_DYE, "-" + configuredMerchantPriceStep(2)));
        inventory.setItem(11, priceButton(Material.RED_DYE, "-" + configuredMerchantPriceStep(1)));
        inventory.setItem(12, priceButton(Material.RED_DYE, "-" + configuredMerchantPriceStep(0)));
        inventory.setItem(14, priceButton(Material.LIME_DYE, "+" + configuredMerchantPriceStep(0)));
        inventory.setItem(15, priceButton(Material.LIME_DYE, "+" + configuredMerchantPriceStep(1)));
        inventory.setItem(16, priceButton(Material.LIME_DYE, "+" + configuredMerchantPriceStep(2)));
        inventory.setItem(18, createDialogueItem(Material.ARROW, "§7Quay lại", List.of()));
        inventory.setItem(22, createDialogueItem(Material.EMERALD, "§aXong, quay lại lưu shop",
            List.of("§7Giá chỉ được ghi khi bấm Lưu cửa hàng.")));
        player.openInventory(inventory);
    }

    private ItemStack priceButton(Material material, String increment) {
        return createDialogueItem(material, (increment.startsWith("+") ? "§a" : "§c") + increment + " VNCOIN",
            List.of("§7Giá được giới hạn trong khoảng 1-" + configuredMerchantMaxPrice() + "."));
    }

    private void saveMerchantDraft(Player player, String merchantId,
                                   LinkedHashMap<Integer, MerchantListing> draft) {
        LinkedHashMap<Integer, MerchantListing> previous = merchantListings.get(merchantId);
        if (draft.isEmpty()) merchantListings.remove(merchantId);
        else merchantListings.put(merchantId, copyListings(draft));
        try {
            saveNpcs();
            player.sendMessage("§aĐã lưu cửa hàng của NPC §f" + merchantId + "§a ("
                + draft.size() + " mặt hàng).");
            player.closeInventory();
            plugin.getLogger().info("[NPC][Merchant] Saved shop: npc=" + merchantId
                + ", editor=" + player.getName() + ", offers=" + draft.size());
        } catch (IOException exception) {
            if (previous == null) merchantListings.remove(merchantId);
            else merchantListings.put(merchantId, previous);
            plugin.getLogger().log(Level.SEVERE, "[NPC][Merchant] Could not save shop " + merchantId, exception);
            player.sendMessage("§cKhông thể lưu cửa hàng; dữ liệu cũ vẫn được giữ.");
        }
    }

    private LinkedHashMap<Integer, MerchantListing> copyListings(Map<Integer, MerchantListing> source) {
        LinkedHashMap<Integer, MerchantListing> copy = new LinkedHashMap<>();
        if (source != null) source.forEach((slot, listing) ->
            copy.put(slot, new MerchantListing(listing.item().clone(), listing.price())));
        return copy;
    }

    private int firstEmptyShopSlot(Map<Integer, MerchantListing> draft) {
        int max = configuredMerchantMaxOffers();
        for (int slot = 0; slot < max; slot++) if (!draft.containsKey(slot)) return slot;
        return -1;
    }

    private boolean isSystemCreatedItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().getKeys().stream()
            .anyMatch(key -> key.getNamespace().equalsIgnoreCase(plugin.getName().toLowerCase(Locale.ROOT))
                && !key.getKey().startsWith("vncoin_") && !key.getKey().equals("merchant_offer_id"));
    }

    private int configuredMerchantMaxOffers() {
        int configured = plugin.getConfig().getInt("economy.merchant.custom.max-offers", 45);
        return configured >= 1 && configured <= 45 ? configured : 45;
    }

    private long configuredMerchantMaxPrice() {
        long configured = plugin.getConfig().getLong("economy.merchant.custom.max-price", 1_000_000L);
        return configured >= 1 && configured <= PlayerProfileDatabaseManager.MAX_WALLET_BALANCE
            ? configured : 1_000_000L;
    }

    private int configuredMerchantPriceStep(int index) {
        List<Integer> configured = plugin.getConfig().getIntegerList("economy.merchant.custom.price-steps");
        int fallback = switch (index) { case 0 -> 1; case 1 -> 10; default -> 100; };
        if (configured.size() != 3) return fallback;
        int step = configured.get(index);
        return step >= 1 && step <= 1_000_000 ? step : fallback;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNpcDamage(EntityDamageEvent event) {
        if (isManagedNpc(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNpcCombust(EntityCombustEvent event) {
        if (isManagedNpc(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNpcTransform(EntityTransformEvent event) {
        if (isManagedNpc(event.getEntity())) event.setCancelled(true);
    }

    static boolean isValidId(String id) {
        return id != null && ID_PATTERN.matcher(id).matches();
    }

    static NpcDefinitionLoader.NpcDefinition findDialogueDefinition(
            String npcId, String type, Map<String, NpcDefinitionLoader.NpcDefinition> definitions) {
        NpcDefinitionLoader.NpcDefinition exact = definitions.get(npcId);
        // An ID-specific definition is authoritative even when its configured role differs.
        if (exact != null) return exact;
        return definitions.values().stream()
            .filter(definition -> definition.type().equals(type))
            .findFirst()
            .orElse(null);
    }

    private static NpcDefinitionLoader.NpcDefinition createFallbackDialogueDefinition(NpcRecord npc) {
        String line = switch (npc.type()) {
            case "quest-giver", "hunter" -> "Ta có một vài nhiệm vụ cần người giúp sức. Hãy xem danh sách bên dưới.";
            case "blacksmith" -> "Ta đang cần người tìm nguyên liệu cho lò rèn.";
            case "alchemist" -> "Ta đang nghiên cứu những nguyên liệu quý hiếm.";
            case "trainer" -> "Hãy chuẩn bị kỹ năng trước khi bước vào vùng nguy hiểm.";
            case "story" -> "Những ký ức của thế giới này vẫn đang chờ được khám phá.";
            case "gatekeeper" -> "Hãy kiểm tra điều kiện trước khi bước qua cánh cổng.";
            case "healer" -> "Hãy nghỉ ngơi và hồi phục trước chuyến đi tiếp theo.";
            default -> "Xin chào. Hãy xem các chức năng khả dụng bên dưới.";
        };
        return new NpcDefinitionLoader.NpcDefinition(npc.id(), npc.type(), npc.displayName(),
            List.of(line), List.of());
    }

    private void openDialogue(Player player, NpcRecord npc, NpcDefinitionLoader.NpcDefinition definition,
                              int requestedActionPage) {
        DialogueHolder holder = new DialogueHolder(player.getUniqueId(), npc.id(), requestedActionPage);
        Inventory inventory = Bukkit.createInventory(holder, 54, dialogueTitle(npc.displayName()));
        holder.inventory = inventory;

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot,
                createDialogueItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        }

        inventory.setItem(4, createDialogueItem(Material.WRITABLE_BOOK,
            formatDisplayName(npc.displayName()), List.of("§7" + titleCase(npc.type()))));
        int[] dialogueSlots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
        };
        for (int index = 0; index < definition.dialogue().size(); index++) {
            String line = definition.dialogue().get(index);
            inventory.setItem(dialogueSlots[index], createDialogueItem(
                Material.PAPER, "§eLời thoại " + (index + 1),
                List.of(formatDisplayName(line), "§8" + (index + 1) + "/" + definition.dialogue().size())));
        }
        List<NpcDefinitionLoader.NpcAction> dialogueActions = new ArrayList<>(definition.actions());
        java.util.Set<String> configuredQuestIds = definition.actions().stream()
            .map(NpcDefinitionLoader.NpcAction::questId).filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
        for (NpcDefinitionLoader.NpcAction action : plugin.getQuestCatalogManager().getNpcQuestActions(player, npc.id())) {
            if (!configuredQuestIds.contains(action.questId())) dialogueActions.add(action);
        }
        int pageCount = Math.max(1, (dialogueActions.size() + 8) / 9);
        holder.page = Math.max(0, Math.min(requestedActionPage, pageCount - 1));
        int[] actionSlots = {37, 38, 39, 40, 41, 42, 43, 44, 45};
        for (int index = 0; index < actionSlots.length && holder.page * 9 + index < dialogueActions.size(); index++) {
            NpcDefinitionLoader.NpcAction action = dialogueActions.get(holder.page * 9 + index);
            int slot = actionSlots[index];
            Material material = switch (action.type()) {
                case "give-skill-book" -> Material.ENCHANTED_BOOK;
                case "accept-quest" -> Material.FILLED_MAP;
                case "quest-interaction" -> Material.FILLED_MAP;
                case "open-skill-tree" -> Material.IRON_SWORD;
                case "open-merchant" -> Material.EMERALD;
                default -> Material.WRITABLE_BOOK;
            };
            inventory.setItem(slot, createDialogueItem(material, "§a" + formatDisplayName(action.label()),
                List.of("§7Nhấn để thực hiện.")));
            holder.actions.put(slot, action);
        }
        if (holder.page > 0) inventory.setItem(46, createDialogueItem(Material.ARROW, "§eQuest trước", List.of()));
        if (dialogueActions.size() > 9) inventory.setItem(47, createDialogueItem(Material.PAPER,
            "§fTrang quest " + (holder.page + 1) + "/" + pageCount,
            List.of("§7Tổng hành động: " + dialogueActions.size())));
        if (holder.page + 1 < pageCount) inventory.setItem(48,
            createDialogueItem(Material.ARROW, "§eQuest tiếp", List.of()));
        inventory.setItem(49, createDialogueItem(Material.BARRIER, "§cĐóng", List.of("§7Đóng hội thoại.")));
        player.openInventory(inventory);
    }

    private static ItemStack createDialogueItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (!lore.isEmpty()) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String dialogueTitle(String displayName) {
        String plainName = ChatColor.stripColor(formatDisplayName(displayName));
        if (plainName.length() > 32) plainName = plainName.substring(0, 32);
        return "§6" + plainName;
    }

    private void createNpc(CommandSender sender, String[] args) throws IOException {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cLệnh này chỉ dùng được trong game.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("§eDùng: /vnnpc create <id> <type> [tên hiển thị]");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        String type = args[2].toLowerCase(Locale.ROOT);
        if (!isValidId(id)) {
            sender.sendMessage("§cID chỉ gồm chữ thường, số và dấu gạch nối đơn.");
            return;
        }
        if (npcs.containsKey(id)) {
            sender.sendMessage("§cĐã tồn tại NPC có ID " + id + ".");
            return;
        }
        if (!NpcDefinitionLoader.supportedTypes().contains(type)) {
            sender.sendMessage("§cLoại NPC không hợp lệ. Loại hỗ trợ: "
                + String.join(", ", NpcDefinitionLoader.supportedTypes()) + ".");
            return;
        }
        if (npcs.size() >= maxNpcs) {
            sender.sendMessage("§cĐã đạt giới hạn " + maxNpcs + " NPC trong cấu hình.");
            return;
        }
        String displayName = args.length >= 4 ? joinArguments(args, 3) : defaultName(type);
        if (!isValidDisplayName(displayName)) {
            sender.sendMessage("§cTên hiển thị phải có từ 1 đến 64 ký tự.");
            return;
        }

        Location location = player.getLocation();
        location.getWorld().getChunkAt(location.getBlockX() >> 4, location.getBlockZ() >> 4);
        Villager entity = spawnNpc(id, displayName, location);
        NpcRecord created = new NpcRecord(id, type, displayName, location, entity.getUniqueId());
        if ("story".equals(type)) created = created.withStoryChapterId(defaultStoryChapterId());
        npcs.put(id, created);
        try {
            saveNpcs();
        } catch (IOException exception) {
            npcs.remove(id);
            entity.remove();
            throw exception;
        }
        sender.sendMessage("§aĐã tạo NPC §f" + id + " §atại vị trí hiện tại.");
    }

    private void editNpc(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cLệnh này chỉ dùng được trong game.");
            return;
        }
        if (args.length != 2) {
            sender.sendMessage("§eDùng: /vnnpc edit <id>");
            return;
        }
        NpcRecord npc = npcs.get(args[1].toLowerCase(Locale.ROOT));
        if (npc == null) {
            sender.sendMessage("§cKhông tìm thấy NPC " + args[1] + ".");
            return;
        }
        if (!"merchant".equals(npc.type())) {
            sender.sendMessage("§cChỉ NPC merchant tùy chỉnh mới có thể chỉnh danh sách hàng hóa.");
            return;
        }
        if (!plugin.getConfig().getBoolean("economy.merchant.custom.enabled", true)) {
            sender.sendMessage("§cCửa hàng NPC tùy chỉnh hiện đang tắt trong cấu hình.");
            return;
        }
        openMerchantEditor(player, npc.id(), copyListings(merchantListings.get(npc.id())));
    }

    private void renameNpc(CommandSender sender, String[] args) throws IOException {
        if (args.length < 3) {
            sender.sendMessage("§eDùng: /vnnpc rename <id> <tên hiển thị>");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        NpcRecord current = npcs.get(id);
        if (current == null) {
            sender.sendMessage("§cKhông tìm thấy NPC " + id + ".");
            return;
        }
        String displayName = joinArguments(args, 2);
        if (!isValidDisplayName(displayName)) {
            sender.sendMessage("§cTên hiển thị phải có từ 1 đến 64 ký tự.");
            return;
        }
        Entity entity = findNpcEntity(current);
        NpcRecord renamed = current.withDisplayName(displayName);
        npcs.put(id, renamed);
        if (entity != null) applyNpcState(entity, renamed);
        try {
            saveNpcs();
        } catch (IOException exception) {
            npcs.put(id, current);
            if (entity != null) applyNpcState(entity, current);
            throw exception;
        }
        sender.sendMessage("§aĐã đổi tên NPC §f" + id + " §athành " + displayName + ".");
    }

    private void moveNpc(CommandSender sender, String[] args) throws IOException {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cLệnh này chỉ dùng được trong game.");
            return;
        }
        if (args.length != 2) {
            sender.sendMessage("§eDùng: /vnnpc move <id>");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        NpcRecord current = npcs.get(id);
        if (current == null) {
            sender.sendMessage("§cKhông tìm thấy NPC " + id + ".");
            return;
        }
        Entity entity = findNpcEntity(current);
        Location destination = player.getLocation();
        destination.getWorld().getChunkAt(destination.getBlockX() >> 4, destination.getBlockZ() >> 4);
        if (entity == null) {
            Villager replacement = spawnNpc(id, current.displayName(), destination);
            npcs.put(id, current.withLocation(destination, replacement.getUniqueId()));
            try {
                saveNpcs();
            } catch (IOException exception) {
                npcs.put(id, current);
                replacement.remove();
                throw exception;
            }
            sender.sendMessage("§aĐã chuyển NPC §f" + id + " §ađến vị trí hiện tại.");
            return;
        }
        Location previousLocation = entity.getLocation();
        NpcRecord moved = current.withLocation(destination, entity.getUniqueId());
        npcs.put(id, moved);
        if (!entity.teleport(destination)) {
            npcs.put(id, current);
            sender.sendMessage("§cKhông thể di chuyển NPC tới vị trí mới.");
            return;
        }
        try {
            saveNpcs();
        } catch (IOException exception) {
            npcs.put(id, current);
            entity.teleport(previousLocation);
            throw exception;
        }
        sender.sendMessage("§aĐã chuyển NPC §f" + id + " §ađến vị trí hiện tại.");
    }

    private void removeNpc(CommandSender sender, String[] args) throws IOException {
        if (args.length != 2) {
            sender.sendMessage("§eDùng: /vnnpc remove <id>");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        NpcRecord current = npcs.get(id);
        if (current == null) {
            sender.sendMessage("§cKhông tìm thấy NPC " + id + ".");
            return;
        }
        Entity entity = findNpcEntity(current);
        npcs.remove(id);
        LinkedHashMap<Integer, MerchantListing> oldListings = merchantListings.remove(id);
        LinkedHashMap<Integer, MerchantBuyOrder> oldBuyOrders = merchantBuyOrders.remove(id);
        try {
            saveNpcs();
        } catch (IOException exception) {
            npcs.put(id, current);
            if (oldListings != null) merchantListings.put(id, oldListings);
            if (oldBuyOrders != null) merchantBuyOrders.put(id, oldBuyOrders);
            throw exception;
        }
        if (entity != null) entity.remove();
        sender.sendMessage("§aĐã xóa NPC §f" + id + ".");
    }

    private void listNpcs(CommandSender sender) {
        if (npcs.isEmpty()) {
            sender.sendMessage("§7Chưa có NPC nào được tạo.");
            return;
        }
        sender.sendMessage("§6NPC đã tạo (" + npcs.size() + "/" + maxNpcs + "):");
        npcs.values().stream().sorted(Comparator.comparing(NpcRecord::id))
            .forEach(npc -> sender.sendMessage("§e" + npc.id() + " §7(" + npc.type() + "): §f"
                + ChatColor.stripColor(formatDisplayName(npc.displayName()))));
    }

    private void showNpcInfo(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("§eDùng: /vnnpc info <id>");
            return;
        }
        NpcRecord npc = npcs.get(args[1].toLowerCase(Locale.ROOT));
        if (npc == null) {
            sender.sendMessage("§cKhông tìm thấy NPC " + args[1] + ".");
            return;
        }
        sender.sendMessage("§6NPC §e" + npc.id() + " §7(" + npc.type() + ")");
        sender.sendMessage("§7Tên: §f" + formatDisplayName(npc.displayName()));
        sender.sendMessage("§7Vị trí: §f" + npc.world() + " " + npc.x() + ", "
            + npc.y() + ", " + npc.z());
    }

    private void loadNpcs() throws IOException, InvalidConfigurationException {
        if (!storageFile.isFile()) return;
        YamlDuplicateKeyValidator.validateNoDuplicateKeys(storageFile);
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(storageFile);
        } catch (YAMLException exception) {
            throw new InvalidConfigurationException("Invalid NPC data YAML.", exception);
        }
        Object rawVersion = configuration.get("schema-version");
        if (!(rawVersion instanceof Number version) || version.intValue() != SCHEMA_VERSION
                || version.doubleValue() != version.intValue()) {
            throw new InvalidConfigurationException("Unsupported or missing NPC data schema-version.");
        }
        ConfigurationSection section = configuration.getConfigurationSection("npcs");
        if (section == null) {
            throw new InvalidConfigurationException("Missing 'npcs' section in " + storageFile.getName() + ".");
        }
        if (section.getKeys(false).size() > HARD_MAX_NPCS) {
            throw new InvalidConfigurationException("NPC data exceeds the hard limit of " + HARD_MAX_NPCS + ".");
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (!isValidId(id) || entry == null) {
                throw new InvalidConfigurationException("Invalid NPC entry '" + id + "'.");
            }
            String type = requiredString(entry, "type", id);
            String displayName = requiredString(entry, "display-name", id);
            String world = requiredString(entry, "world", id);
            if (!NpcDefinitionLoader.supportedTypes().contains(type) || !isValidDisplayName(displayName)) {
                throw new InvalidConfigurationException("Invalid NPC type or display-name for '" + id + "'.");
            }
            double x = finiteNumber(entry, "x", id);
            double y = finiteNumber(entry, "y", id);
            double z = finiteNumber(entry, "z", id);
            float yaw = (float) finiteNumber(entry, "yaw", id);
            float pitch = (float) finiteNumber(entry, "pitch", id);
            UUID entityId = null;
            Object rawEntityId = entry.get("entity-uuid");
            if (rawEntityId != null) {
                if (!(rawEntityId instanceof String entityIdText) || entityIdText.isBlank()) {
                    throw new InvalidConfigurationException("Invalid entity-uuid for NPC '" + id + "'.");
                }
                try {
                    entityId = UUID.fromString(entityIdText);
                } catch (IllegalArgumentException exception) {
                    throw new InvalidConfigurationException("Invalid entity-uuid for NPC '" + id + "'.", exception);
                }
            }
            String storyChapterId = entry.getString("story-chapter-id", "").trim();
            if (storyChapterId.isBlank() && "story".equals(type)) storyChapterId = defaultStoryChapterId();
            npcs.put(id, new NpcRecord(id, type, displayName,
                new Location(null, x, y, z, yaw, pitch), entityId, world,
                storyChapterId.isBlank() ? null : storyChapterId));
            ConfigurationSection savedListings = entry.getConfigurationSection("merchant-offers");
            if (savedListings != null) {
                if (!"merchant".equals(type)) {
                    throw new InvalidConfigurationException("Only custom merchant NPC '" + id
                        + "' may contain merchant-offers.");
                }
                LinkedHashMap<Integer, MerchantListing> listings = new LinkedHashMap<>();
                for (String rawSlot : savedListings.getKeys(false)) {
                    int slot;
                    try {
                        slot = Integer.parseInt(rawSlot);
                    } catch (NumberFormatException exception) {
                        throw new InvalidConfigurationException("Invalid merchant slot '" + rawSlot
                            + "' for NPC '" + id + "'.", exception);
                    }
                    ConfigurationSection offer = savedListings.getConfigurationSection(rawSlot);
                    ItemStack item = offer == null ? null : offer.getItemStack("item");
                    long price = offer == null ? 0L : offer.getLong("price", 0L);
                    if (slot < 0 || slot >= 45 || !isSystemCreatedItem(item)
                            || price < 1 || price > PlayerProfileDatabaseManager.MAX_WALLET_BALANCE) {
                        throw new InvalidConfigurationException("Invalid merchant offer at NPC '" + id
                            + "', slot " + rawSlot + ".");
                    }
                    listings.put(slot, new MerchantListing(item.clone(), price));
                }
                merchantListings.put(id, listings);
            }
            ConfigurationSection savedBuyOrders = entry.getConfigurationSection("merchant-buy-orders");
            if (savedBuyOrders != null) {
                if (!"merchant".equals(type)) {
                    throw new InvalidConfigurationException("Only custom merchant NPC '" + id
                        + "' may contain merchant-buy-orders.");
                }
                LinkedHashMap<Integer, MerchantBuyOrder> orders = new LinkedHashMap<>();
                for (String rawSlot : savedBuyOrders.getKeys(false)) {
                    int slot;
                    try {
                        slot = Integer.parseInt(rawSlot);
                    } catch (NumberFormatException exception) {
                        throw new InvalidConfigurationException("Invalid merchant buy-order slot '" + rawSlot
                            + "' for NPC '" + id + "'.", exception);
                    }
                    ConfigurationSection order = savedBuyOrders.getConfigurationSection(rawSlot);
                    ItemStack item = order == null ? null : order.getItemStack("item");
                    int amount = order == null ? 0 : order.getInt("amount", 0);
                    long price = order == null ? 0L : order.getLong("price", 0L);
                    if (slot < 0 || slot >= 45 || !isSystemCreatedItem(item) || amount < 1
                            || amount > 100_000 || price < 1
                            || price > PlayerProfileDatabaseManager.MAX_WALLET_BALANCE) {
                        throw new InvalidConfigurationException("Invalid merchant buy order at NPC '" + id
                            + "', slot " + rawSlot + ".");
                    }
                    item.setAmount(1);
                    orders.put(slot, new MerchantBuyOrder(item, amount, price));
                }
                merchantBuyOrders.put(id, orders);
            }
        }
    }

    private void saveNpcs() throws IOException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("schema-version", SCHEMA_VERSION);
        configuration.createSection("npcs");
        for (NpcRecord npc : npcs.values()) {
            String path = "npcs." + npc.id();
            configuration.set(path + ".type", npc.type());
            configuration.set(path + ".display-name", npc.displayName());
            configuration.set(path + ".world", npc.world());
            configuration.set(path + ".x", npc.x());
            configuration.set(path + ".y", npc.y());
            configuration.set(path + ".z", npc.z());
            configuration.set(path + ".yaw", npc.yaw());
            configuration.set(path + ".pitch", npc.pitch());
            configuration.set(path + ".entity-uuid", npc.entityId() == null ? null : npc.entityId().toString());
            configuration.set(path + ".story-chapter-id", npc.storyChapterId());
            Map<Integer, MerchantListing> listings = merchantListings.get(npc.id());
            if (listings != null) {
                for (Map.Entry<Integer, MerchantListing> offer : listings.entrySet()) {
                    String offerPath = path + ".merchant-offers." + offer.getKey();
                    configuration.set(offerPath + ".item", offer.getValue().item().clone());
                    configuration.set(offerPath + ".price", offer.getValue().price());
                }
            }
            Map<Integer, MerchantBuyOrder> buyOrders = merchantBuyOrders.get(npc.id());
            if (buyOrders != null) {
                for (Map.Entry<Integer, MerchantBuyOrder> order : buyOrders.entrySet()) {
                    String orderPath = path + ".merchant-buy-orders." + order.getKey();
                    configuration.set(orderPath + ".item", order.getValue().item().clone());
                    configuration.set(orderPath + ".amount", order.getValue().amount());
                    configuration.set(orderPath + ".price", order.getValue().price());
                    configuration.setComments(orderPath + ".item", List.of(
                        "[VI] Item RPG chính xác người chơi cần giao; được lưu từ GUI merchant.",
                        "[EN] Exact RPG item players must hand in; saved through the merchant GUI."));
                    configuration.setComments(orderPath + ".amount", List.of(
                        "[VI] Số item cần giao để đổi một lần; hợp lệ 1-100000.",
                        "[EN] Item count required for one exchange; valid range 1-100000."));
                    configuration.setComments(orderPath + ".price", List.of(
                        "[VI] VNCOIN trả cho đủ số lượng item; hợp lệ 1 đến giới hạn max-price.",
                        "[EN] VNCOIN paid for the required item count; valid range 1 through max-price."));
                }
            }
        }

        File parent = storageFile.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create NPC data directory.");
        }
        File temporary = Files.createTempFile(parent.toPath(), "npcs-", ".yml.tmp").toFile();
        try {
            Files.writeString(temporary.toPath(), configuration.saveToString());
            try {
                Files.move(temporary.toPath(), storageFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary.toPath(), storageFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary.toPath());
        }
    }

    private String defaultStoryChapterId() {
        return plugin.getConfigManager().getStoryChapters().values().stream()
            .min(Comparator.comparingInt(StoryChapterLoader.StoryChapter::order))
            .map(StoryChapterLoader.StoryChapter::id).orElse(null);
    }

    private void reconcileLoadedNpcs() {
        for (NpcRecord npc : List.copyOf(npcs.values())) {
            World world = Bukkit.getWorld(npc.world());
            if (world == null) {
                plugin.getLogger().warning("[NPC] World '" + npc.world()
                    + "' for NPC '" + npc.id() + "' is not loaded; it will spawn when the world loads.");
                continue;
            }
            int chunkX = chunkCoordinate(npc.x());
            int chunkZ = chunkCoordinate(npc.z());
            if (world.isChunkLoaded(chunkX, chunkZ)) {
                reconcileChunk(world.getChunkAt(chunkX, chunkZ));
            }
        }
    }

    private void reconcileChunk(Chunk chunk) {
        Map<String, Entity> found = new LinkedHashMap<>();
        for (Entity entity : chunk.getEntities()) {
            String id = entity.getPersistentDataContainer().get(npcIdKey, PersistentDataType.STRING);
            if (id == null) continue;
            NpcRecord record = npcs.get(id);
            if (!(entity instanceof Villager) || record == null || !isInChunk(record, chunk)
                    || (record.entityId() != null && !record.entityId().equals(entity.getUniqueId()))
                    || found.containsKey(id)) {
                entity.remove();
                continue;
            }
            found.put(id, entity);
        }

        boolean changed = false;
        for (NpcRecord record : List.copyOf(npcs.values())) {
            if (!isInChunk(record, chunk)) continue;
            Entity entity = found.get(record.id());
            if (entity == null) {
                World world = chunk.getWorld();
                Location location = toLocation(record, world);
                Villager spawned = spawnNpc(record.id(), record.displayName(), location);
                entity = spawned;
            } else {
                applyNpcState(entity, record);
            }
            if (!entity.getUniqueId().equals(record.entityId())) {
                npcs.put(record.id(), record.withEntityId(entity.getUniqueId()));
                changed = true;
            }
        }
        if (changed) {
            try {
                saveNpcs();
            } catch (IOException exception) {
                plugin.getLogger().log(Level.SEVERE, "[NPC] Could not save reconciled NPC data.", exception);
            }
        }
    }

    private Entity findNpcEntity(NpcRecord record) {
        World world = Bukkit.getWorld(record.world());
        if (world == null) return null;
        Chunk chunk = world.getChunkAt(chunkCoordinate(record.x()), chunkCoordinate(record.z()));
        reconcileChunk(chunk);
        for (Entity entity : chunk.getEntities()) {
            if (entity.getUniqueId().equals(npcs.get(record.id()).entityId())) return entity;
        }
        return null;
    }

    private Villager spawnNpc(String id, String displayName, Location location) {
        Villager villager = (Villager) location.getWorld().spawnEntity(location, EntityType.VILLAGER);
        NpcRecord temporary = new NpcRecord(id, "tutorial", displayName, location, villager.getUniqueId());
        applyNpcState(villager, temporary);
        return villager;
    }

    private void applyNpcState(Entity entity, NpcRecord npc) {
        entity.getPersistentDataContainer().set(npcIdKey, PersistentDataType.STRING, npc.id());
        entity.setCustomName(formatDisplayName(npc.displayName()));
        entity.setCustomNameVisible(true);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setPersistent(true);
        if (entity instanceof Villager villager) {
            villager.setAI(false);
            villager.setAware(false);
            villager.setCanPickupItems(false);
            villager.setCollidable(false);
            villager.setAdult();
            villager.setProfession(Villager.Profession.NONE);
        }
    }

    private String defaultName(String type) {
        return definitions.values().stream()
            .filter(definition -> definition.type().equals(type))
            .map(NpcDefinitionLoader.NpcDefinition::displayName)
            .findFirst()
            .orElseGet(() -> titleCase(type));
    }

    private int configuredNpcLimit() {
        int configured = plugin.getConfig().getInt("rpg.npc.max-runtime-npcs", HARD_MAX_NPCS);
        if (configured < 1 || configured > HARD_MAX_NPCS) {
            plugin.getLogger().warning("[NPC] rpg.npc.max-runtime-npcs must be 1-"
                + HARD_MAX_NPCS + "; using " + HARD_MAX_NPCS + ".");
            return HARD_MAX_NPCS;
        }
        return configured;
    }

    private static String requiredString(ConfigurationSection section, String key, String id)
            throws InvalidConfigurationException {
        String value = section.getString(key);
        if (value == null || value.isBlank()) {
            throw new InvalidConfigurationException("Missing or invalid '" + key + "' for NPC '" + id + "'.");
        }
        return value.trim();
    }

    private static double finiteNumber(ConfigurationSection section, String key, String id)
            throws InvalidConfigurationException {
        Object value = section.get(key);
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new InvalidConfigurationException("Missing or invalid '" + key + "' for NPC '" + id + "'.");
        }
        return number.doubleValue();
    }

    private static boolean isValidDisplayName(String displayName) {
        return displayName != null && !displayName.isBlank() && displayName.length() <= 64;
    }

    private static int chunkCoordinate(double coordinate) {
        return ((int) Math.floor(coordinate)) >> 4;
    }

    private static boolean isInChunk(NpcRecord record, Chunk chunk) {
        return record.world().equals(chunk.getWorld().getName())
            && chunkCoordinate(record.x()) == chunk.getX()
            && chunkCoordinate(record.z()) == chunk.getZ();
    }

    private static Location toLocation(NpcRecord record, World world) {
        return new Location(world, record.x(), record.y(), record.z(), record.yaw(), record.pitch());
    }

    private static String formatDisplayName(String displayName) {
        return EconomyManager.sanitizeVisibleName(ChatColor.translateAlternateColorCodes('&', displayName));
    }

    private static String titleCase(String value) {
        String[] segments = value.split("-");
        for (int index = 0; index < segments.length; index++) {
            String segment = segments[index];
            if (!segment.isEmpty()) {
                segments[index] = segment.substring(0, 1).toUpperCase(Locale.ROOT) + segment.substring(1);
            }
        }
        return String.join(" ", segments);
    }

    private static String joinArguments(String[] args, int fromIndex) {
        return String.join(" ", java.util.Arrays.copyOfRange(args, fromIndex, args.length));
    }

    private static List<String> filter(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }

    private static void sendUsage(CommandSender sender) {
        sender.sendMessage("§e/vnnpc create <id> <type> [tên hiển thị] §7(tạo tại vị trí hiện tại)");
        sender.sendMessage("§e/vnnpc edit <id> §7(chỉnh cửa hàng của merchant tùy chỉnh)");
        sender.sendMessage("§e/vnnpc rename <id> <tên> §7| §e/vnnpc move <id>");
        sender.sendMessage("§e/vnnpc info <id> §7| §e/vnnpc list §7| §e/vnnpc remove <id>");
    }

    public record NpcTarget(String world, Location location) {
        public NpcTarget {
            location = location == null ? null : location.clone();
        }

        @Override public Location location() { return location == null ? null : location.clone(); }
    }

    private record NpcRecord(String id, String type, String displayName, Location location,
                             UUID entityId, String world, String storyChapterId) {
        private NpcRecord(String id, String type, String displayName, Location location, UUID entityId) {
            this(id, type, displayName, location.clone(), entityId, location.getWorld().getName(), null);
        }

        private NpcRecord(String id, String type, String displayName, Location location,
                          UUID entityId, String world) {
            this(id, type, displayName, location, entityId, world, null);
        }

        private NpcRecord(String id, String type, String displayName, Location location,
                          UUID entityId, String world, String storyChapterId) {
            this.id = id;
            this.type = type;
            this.displayName = displayName;
            this.location = location.clone();
            this.entityId = entityId;
            this.world = world;
            this.storyChapterId = storyChapterId;
        }

        private double x() {
            return location.getX();
        }

        private double y() {
            return location.getY();
        }

        private double z() {
            return location.getZ();
        }

        private float yaw() {
            return location.getYaw();
        }

        private float pitch() {
            return location.getPitch();
        }

        private NpcRecord withDisplayName(String name) {
            return new NpcRecord(id, type, name, location, entityId, world, storyChapterId);
        }

        private NpcRecord withType(String value) {
            return new NpcRecord(id, value, displayName, location, entityId, world, storyChapterId);
        }

        private NpcRecord withLocation(Location newLocation, UUID newEntityId) {
            return new NpcRecord(id, type, displayName, newLocation, newEntityId,
                newLocation.getWorld().getName(), storyChapterId);
        }

        private NpcRecord withEntityId(UUID newEntityId) {
            return new NpcRecord(id, type, displayName, location, newEntityId, world, storyChapterId);
        }

        private NpcRecord withStoryChapterId(String value) {
            return new NpcRecord(id, type, displayName, location, entityId, world, value);
        }
    }

    private record MerchantListing(ItemStack item, long price) {
        private MerchantListing {
            item = item.clone();
        }
    }

    private record MerchantBuyOrder(ItemStack item, int amount, long price) {
        private MerchantBuyOrder {
            item = item.clone();
            item.setAmount(1);
        }
    }

    public record MerchantBuyQuote(int amount, long price, String merchantId) { }

    private enum NpcMenu { LIST, EDIT, CONFIRM_DELETE, STORY_CHAPTERS }
    private enum NpcTextField { CREATE_ID, CREATE_TYPE, CREATE_NAME, RENAME, CHANGE_TYPE }
    private record PendingNpcInput(NpcTextField field, String npcId, int page, String value) { }

    private static final class NpcMenuHolder implements InventoryHolder {
        private final UUID viewer;
        private final NpcMenu menu;
        private final String npcId;
        private final int page;
        private final Map<Integer, String> npcSlots = new LinkedHashMap<>();
        private Inventory inventory;

        private NpcMenuHolder(UUID viewer, NpcMenu menu, String npcId, int page) {
            this.viewer = viewer;
            this.menu = menu;
            this.npcId = npcId;
            this.page = page;
        }

        @Override public Inventory getInventory() { return inventory; }
    }

    private static final class DialogueHolder implements InventoryHolder {
        private final UUID viewer;
        private final String npcId;
        private int page;
        private final Map<Integer, NpcDefinitionLoader.NpcAction> actions = new LinkedHashMap<>();
        private Inventory inventory;

        private DialogueHolder(UUID viewer, String npcId, int page) {
            this.viewer = viewer;
            this.npcId = npcId;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class MerchantEditorHolder implements InventoryHolder {
        private final UUID viewer;
        private final String merchantId;
        private final LinkedHashMap<Integer, MerchantListing> draft;
        private Inventory inventory;

        private MerchantEditorHolder(UUID viewer, String merchantId,
                                     LinkedHashMap<Integer, MerchantListing> draft) {
            this.viewer = viewer;
            this.merchantId = merchantId;
            this.draft = draft;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class MerchantPriceHolder implements InventoryHolder {
        private final UUID viewer;
        private final String merchantId;
        private final LinkedHashMap<Integer, MerchantListing> draft;
        private final int slot;
        private Inventory inventory;

        private MerchantPriceHolder(UUID viewer, String merchantId,
                                    LinkedHashMap<Integer, MerchantListing> draft, int slot) {
            this.viewer = viewer;
            this.merchantId = merchantId;
            this.draft = draft;
            this.slot = slot;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class MerchantBuyEditorHolder implements InventoryHolder {
        private final UUID viewer;
        private final String merchantId;
        private final LinkedHashMap<Integer, MerchantBuyOrder> draft;
        private Inventory inventory;

        private MerchantBuyEditorHolder(UUID viewer, String merchantId,
                LinkedHashMap<Integer, MerchantBuyOrder> draft) {
            this.viewer = viewer;
            this.merchantId = merchantId;
            this.draft = draft;
        }

        @Override public Inventory getInventory() { return inventory; }
    }

    private static final class MerchantBuyOrderHolder implements InventoryHolder {
        private final UUID viewer;
        private final String merchantId;
        private final LinkedHashMap<Integer, MerchantBuyOrder> draft;
        private final int slot;
        private Inventory inventory;

        private MerchantBuyOrderHolder(UUID viewer, String merchantId,
                LinkedHashMap<Integer, MerchantBuyOrder> draft, int slot) {
            this.viewer = viewer;
            this.merchantId = merchantId;
            this.draft = draft;
            this.slot = slot;
        }

        @Override public Inventory getInventory() { return inventory; }
    }
}
