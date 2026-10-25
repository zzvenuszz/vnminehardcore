package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Pattern;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.yaml.snakeyaml.error.YAMLException;

/** Stores reusable quest dialogs and displays story/quest text as an action-bar typewriter. */
public final class QuestDialogManager implements Listener, CommandExecutor, TabCompleter {
    private static final String PERMISSION = "vnmine.command.quest.admin";
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final int MAX_DIALOGS = 200;
    private final VnMineHardcore plugin;
    private final File storageFile;
    private final Map<String, QuestDialog> dialogs = new LinkedHashMap<>();
    private final Map<UUID, PendingInput> pendingInputs = new ConcurrentHashMap<>();
    private final Map<UUID, DialogueSession> sessions = new ConcurrentHashMap<>();

    public QuestDialogManager(VnMineHardcore plugin) throws IOException {
        this.plugin = plugin;
        File directory = new File(plugin.getDataFolder(), "quests");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Could not create quest directory.");
        storageFile = new File(directory, "quest-dialogs.yml");
        load();
        if (!storageFile.isFile() && !save()) throw new IOException("Could not create quest-dialogs.yml.");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickSessions, 1L, 2L);
    }

    public void openAdmin(Player player) {
        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage("§cBạn không có quyền quản lý quest-dialog.");
            return;
        }
        openList(player, 0);
    }

    public boolean hasDialog(String id) {
        return id != null && dialogs.containsKey(id);
    }

    public void openSelector(Player player, String selectedId, Consumer<String> callback) {
        openSelector(player, selectedId, callback, 0);
    }

    private void openSelector(Player player, String selectedId, Consumer<String> callback, int requestedPage) {
        List<QuestDialog> values = dialogs.values().stream()
            .sorted(java.util.Comparator.comparing(QuestDialog::id)).toList();
        int pageCount = Math.max(1, (values.size() + 44) / 45);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        DialogHolder holder = new DialogHolder(player.getUniqueId(), Menu.SELECT, null, page, callback);
        holder.selectedId = selectedId;
        Inventory menu = Bukkit.createInventory(holder, 54, "Chọn quest-dialog");
        holder.inventory = menu;
        for (int slot = 0; slot < 45 && page * 45 + slot < values.size(); slot++) {
            QuestDialog dialog = values.get(page * 45 + slot);
            menu.setItem(slot, item(Material.WRITABLE_BOOK,
                (dialog.id().equals(selectedId) ? "§a✓ " : "§f") + dialog.title(),
                List.of("§7ID: §f" + dialog.id(), "§7Số trang: §f" + dialog.pages().size(),
                    "§eNhấn để chọn")));
            holder.dialogSlots.put(slot, dialog.id());
        }
        menu.setItem(45, item(Material.BARRIER, "§cKhông dùng hội thoại", List.of("§7Xóa lựa chọn hiện tại.")));
        if (page > 0) menu.setItem(48, item(Material.ARROW, "§eTrang trước", List.of()));
        menu.setItem(49, item(Material.ARROW, "§eHủy chọn", List.of()));
        if (page + 1 < pageCount) menu.setItem(50, item(Material.ARROW, "§eTrang sau", List.of()));
        menu.setItem(53, item(Material.PAPER, "§fĐang chọn: " + (selectedId == null ? "không có" : selectedId),
            List.of("§7Trang " + (page + 1) + "/" + pageCount)));
        player.openInventory(menu);
    }

    public void openQuestDialogue(Player player, String dialogId, String speaker, Runnable onContinue) {
        openQuestDialogue(player, dialogId, speaker, "Ta có điều muốn nói với bạn.", onContinue);
    }

    public void openQuestDialogue(Player player, String dialogId, String speaker,
                                  String fallbackText, Runnable onContinue) {
        QuestDialog dialog = dialogId == null ? null : dialogs.get(dialogId);
        if (dialog == null) {
            if (dialogId != null) plugin.getLogger().warning("[QuestDialog] Missing quest-dialog id='" + dialogId + "'.");
            String fallback = dialogId == null
                ? fallbackText
                : "Hội thoại đang được cấu hình lại; hãy thử lại sau.";
            openPages(player, speaker, List.of(fallback), onContinue);
            return;
        }
        openPages(player, speaker, dialog.pages(), onContinue);
    }

    public void openStoryChapter(Player player, String speaker, StoryChapterLoader.StoryChapter chapter) {
        if (chapter == null) {
            player.sendMessage("§cNPC này chưa được gán chapter truyện. Admin hãy shift+click phải NPC để chỉnh sửa.");
            return;
        }
        openPages(player, speaker, chapter.pages(), null);
    }

    private void openPages(Player player, String speaker, List<String> pages, Runnable onContinue) {
        if (pages == null || pages.isEmpty()) {
            player.sendActionBar("§cKhông có nội dung hội thoại để hiển thị.");
            if (onContinue != null) onContinue.run();
            return;
        }
        DialogueSession previous = sessions.remove(player.getUniqueId());
        if (previous != null) previous.cancelled = true;
        List<String> chunks = splitDialoguePages(pages);
        if (chunks.isEmpty()) {
            player.sendActionBar("§cKhông có nội dung hội thoại để hiển thị.");
            if (onContinue != null) onContinue.run();
            return;
        }
        DialogueSession session = new DialogueSession(player.getUniqueId(), speaker, chunks, onContinue);
        sessions.put(player.getUniqueId(), session);
        prepareChunk(session);
        updatePlayback(session);
    }

    private static List<String> splitDialoguePages(List<String> pages) {
        List<String> chunks = new ArrayList<>();
        for (String page : pages) {
            String text = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', page))
                .replace('\n', ' ').trim();
            while (!text.isBlank()) {
                int end = Math.min(36, text.codePointCount(0, text.length()));
                if (end < text.codePointCount(0, text.length())) {
                    int charEnd = text.offsetByCodePoints(0, end);
                    int split = text.lastIndexOf(' ', charEnd);
                    if (split > 12) end = text.codePointCount(0, split);
                }
                int charEnd = text.offsetByCodePoints(0, end);
                chunks.add(text.substring(0, charEnd).trim());
                text = text.substring(charEnd).stripLeading();
            }
        }
        return List.copyOf(chunks);
    }

    private void prepareChunk(DialogueSession session) {
        session.text = session.chunks.get(session.chunkIndex);
        session.codePoints = session.text.codePointCount(0, session.text.length());
        session.visibleCodePoints = 0;
        session.pauseTicks = 0;
    }

    private void tickSessions() {
        for (DialogueSession session : List.copyOf(sessions.values())) {
            Player player = Bukkit.getPlayer(session.viewer);
            if (player == null || !player.isOnline()) {
                sessions.remove(session.viewer, session);
                session.cancelled = true;
                continue;
            }
            if (session.visibleCodePoints < session.codePoints) {
                session.visibleCodePoints = Math.min(session.codePoints, session.visibleCodePoints + 2);
                updatePlayback(session);
            } else if (session.pauseTicks > 0) {
                session.pauseTicks--;
            } else if (++session.chunkIndex < session.chunks.size()) {
                prepareChunk(session);
                updatePlayback(session);
            } else if (sessions.remove(session.viewer, session)) {
                player.sendActionBar(" ");
                Runnable callback = session.onContinue;
                if (callback != null && !session.cancelled) callback.run();
            }
        }
    }

    private void updatePlayback(DialogueSession session) {
        int end = session.text.offsetByCodePoints(0, session.visibleCodePoints);
        String visible = session.text.substring(0, end);
        Player player = Bukkit.getPlayer(session.viewer);
        if (player != null) {
            player.sendActionBar(LegacyComponentSerializer.legacySection()
                .deserialize("§6[" + actionBarSpeaker(session.speaker) + ":] §f" + visible));
            if (session.visibleCodePoints >= session.codePoints) session.pauseTicks = 8;
        }
    }

    private static String actionBarSpeaker(String speaker) {
        String plain = stripAndFormat(speaker);
        return plain.length() <= 20 ? plain : plain.substring(0, 20);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof DialogHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.viewer.equals(player.getUniqueId())
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (!player.hasPermission(PERMISSION)) {
            player.closeInventory();
            return;
        }
        int slot = event.getRawSlot();
        if (holder.menu == Menu.SELECT) {
            if (slot == 45) {
                finishSelection(player, holder, null);
            } else if (slot == 49) {
                finishSelection(player, holder, holder.selectedId);
            } else if (slot == 48) {
                openSelector(player, holder.selectedId, holder.callback, holder.page - 1);
            } else if (slot == 50) {
                openSelector(player, holder.selectedId, holder.callback, holder.page + 1);
            } else if (holder.dialogSlots.containsKey(slot)) {
                finishSelection(player, holder, holder.dialogSlots.get(slot));
            }
        } else if (holder.menu == Menu.LIST) {
            if (slot == 45) prompt(player, new PendingInput(InputField.CREATE_ID, null, holder.page));
            else if (slot == 48) openList(player, holder.page - 1);
            else if (slot == 50) openList(player, holder.page + 1);
            else if (slot == 53) player.closeInventory();
            else if (holder.dialogSlots.containsKey(slot)) openEditor(player, holder.dialogSlots.get(slot));
        } else if (holder.menu == Menu.EDIT) {
            QuestDialog dialog = dialogs.get(holder.dialogId);
            if (dialog == null) { openList(player); return; }
            if (slot == 10) prompt(player, new PendingInput(InputField.RENAME, dialog.id(), 0));
            else if (slot == 12) prompt(player, new PendingInput(InputField.CONTENT, dialog.id(), 0));
            else if (slot == 14) openDeleteConfirmation(player, dialog.id());
            else if (slot == 49) openList(player);
        } else if (holder.menu == Menu.CONFIRM_DELETE) {
            if (slot == 11) {
                if (plugin.getQuestCatalogManager() != null
                        && plugin.getQuestCatalogManager().isQuestDialogInUse(holder.dialogId)) {
                    player.sendMessage("§cKhông thể xóa: hội thoại đang được gán cho ít nhất một quest.");
                    openEditor(player, holder.dialogId);
                    return;
                }
                QuestDialog removed = dialogs.remove(holder.dialogId);
                if (removed != null && !save()) {
                    dialogs.put(removed.id(), removed);
                    player.sendMessage("§cKhông lưu được thao tác xóa; dữ liệu cũ đã được khôi phục.");
                    openEditor(player, removed.id());
                } else {
                    player.sendMessage("§aĐã xóa quest-dialog.");
                    openList(player);
                }
            } else if (slot == 15) openEditor(player, holder.dialogId);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof DialogHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingInputs.remove(event.getPlayer().getUniqueId());
        DialogueSession session = sessions.remove(event.getPlayer().getUniqueId());
        if (session != null) session.cancelled = true;
    }

    private void openList(Player player) {
        openList(player, 0);
    }

    private void openList(Player player, int requestedPage) {
        List<QuestDialog> values = dialogs.values().stream().sorted(java.util.Comparator.comparing(QuestDialog::id)).toList();
        int pageCount = Math.max(1, (values.size() + 44) / 45);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        DialogHolder holder = new DialogHolder(player.getUniqueId(), Menu.LIST, null, page, null);
        Inventory menu = Bukkit.createInventory(holder, 54, "Quản lý quest-dialog • " + (page + 1));
        holder.inventory = menu;
        for (int slot = 0; slot < 45 && page * 45 + slot < values.size(); slot++) {
            QuestDialog dialog = values.get(page * 45 + slot);
            menu.setItem(slot, item(Material.WRITABLE_BOOK, "§e" + dialog.title(), List.of(
                "§7ID: §f" + dialog.id(), "§7Trang hội thoại: §f" + dialog.pages().size(), "§eNhấn để sửa")));
            holder.dialogSlots.put(slot, dialog.id());
        }
        menu.setItem(45, item(Material.LIME_DYE, "§aTạo quest-dialog", List.of(
            "§7Tạo ID, tên và nội dung bằng chat.", "§7Dùng dấu | để tách các trang.")));
        if (page > 0) menu.setItem(48, item(Material.ARROW, "§eTrang trước", List.of()));
        menu.setItem(49, item(Material.PAPER, "§fTrang " + (page + 1) + "/" + pageCount,
            List.of("§7Tổng: " + values.size() + " hội thoại")));
        if (page + 1 < pageCount) menu.setItem(50, item(Material.ARROW, "§eTrang sau", List.of()));
        menu.setItem(53, item(Material.BARRIER, "§cĐóng", List.of()));
        player.openInventory(menu);
    }

    private void openEditor(Player player, String id) {
        QuestDialog dialog = dialogs.get(id);
        if (dialog == null) { openList(player); return; }
        DialogHolder holder = new DialogHolder(player.getUniqueId(), Menu.EDIT, id, 0, null);
        Inventory menu = Bukkit.createInventory(holder, 54, "Sửa quest-dialog • " + id);
        holder.inventory = menu;
        menu.setItem(4, item(Material.WRITABLE_BOOK, "§6" + dialog.title(), List.of("§7ID: §f" + id)));
        menu.setItem(10, item(Material.NAME_TAG, "§eĐổi tên", List.of("§7Tên hiện tại: " + dialog.title())));
        menu.setItem(12, item(Material.PAPER, "§bSửa nội dung", List.of("§7" + dialog.pages().size()
            + " trang.", "§7Nhập các trang, phân tách bằng |.")));
        menu.setItem(14, item(Material.REDSTONE, "§cXóa hội thoại", List.of("§7Cần xác nhận trước khi xóa.")));
        menu.setItem(49, item(Material.ARROW, "§eQuay lại", List.of()));
        player.openInventory(menu);
    }

    private void openDeleteConfirmation(Player player, String id) {
        DialogHolder holder = new DialogHolder(player.getUniqueId(), Menu.CONFIRM_DELETE, id, 0, null);
        Inventory menu = Bukkit.createInventory(holder, 54, "Xóa quest-dialog?");
        holder.inventory = menu;
        menu.setItem(4, item(Material.WRITABLE_BOOK, "§c" + id, List.of("§7Hành động không thể hoàn tác.")));
        menu.setItem(11, item(Material.LIME_DYE, "§aXác nhận xóa", List.of()));
        menu.setItem(15, item(Material.RED_DYE, "§eHủy", List.of()));
        player.openInventory(menu);
    }

    private void finishSelection(Player player, DialogHolder holder, String selected) {
        Consumer<String> callback = holder.callback;
        player.closeInventory();
        if (callback != null) callback.accept(selected);
    }

    private void prompt(Player player, PendingInput input) {
        pendingInputs.put(player.getUniqueId(), input);
        player.closeInventory();
        String instruction = switch (input.field()) {
            case CREATE_ID -> "Nhập ID, ví dụ quest-dialog-hunter (chữ thường, số, dấu gạch nối).";
            case CREATE_TITLE, RENAME -> "Nhập tên hội thoại, ví dụ Lời nhắn của thợ rèn (tối đa 64 ký tự).";
            case CONTENT -> "Nhập nội dung tối đa 12 trang, mỗi trang tối đa 256 ký tự; dùng | để tách trang. Ví dụ: Chào người lữ khách.|Hãy giúp ta tìm quặng.";
        };
        player.sendMessage("§6[Quest-dialog] §e" + instruction);
        player.sendMessage("§7Gõ §c!cancel §7để hủy và quay lại giao diện.");
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        PendingInput input = pendingInputs.remove(event.getPlayer().getUniqueId());
        if (input == null) return;
        event.setCancelled(true);
        String text = event.getMessage().trim();
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> handleInput(player, input, text));
    }

    private void handleInput(Player player, PendingInput input, String text) {
        if (!player.isOnline()) return;
        if (text.equalsIgnoreCase("!cancel")) {
            if (input.dialogId() == null) openList(player); else openEditor(player, input.dialogId());
            return;
        }
        if (input.field() == InputField.CREATE_ID) {
            String id = text.toLowerCase(Locale.ROOT);
            if (!ID_PATTERN.matcher(id).matches() || dialogs.containsKey(id) || dialogs.size() >= MAX_DIALOGS) {
                player.sendMessage("§cID không hợp lệ/trùng hoặc đã đủ 200 hội thoại. Ví dụ: §fquest-dialog-hunter");
                prompt(player, input); return;
            }
            prompt(player, new PendingInput(InputField.CREATE_TITLE, id, 0));
        } else if (input.field() == InputField.CREATE_TITLE) {
            if (!validTitle(text)) { player.sendMessage("§cTên cần 1-64 ký tự."); prompt(player, input); return; }
            prompt(player, new PendingInput(InputField.CONTENT, input.dialogId(), input.page(), text));
        } else if (input.field() == InputField.RENAME) {
            QuestDialog old = dialogs.get(input.dialogId());
            if (old == null || !validTitle(text)) { player.sendMessage("§cTên không hợp lệ."); prompt(player, input); return; }
            dialogs.put(old.id(), new QuestDialog(old.id(), text, old.pages()));
            persistOrRollback(player, old, input.dialogId());
        } else if (input.field() == InputField.CONTENT) {
            List<String> pages = parsePages(text);
            if (pages == null) { player.sendMessage("§cNội dung phải có 1-12 trang, mỗi trang 1-256 ký tự."); prompt(player, input); return; }
            QuestDialog old = dialogs.get(input.dialogId());
            QuestDialog next = new QuestDialog(input.dialogId(), input.value(), pages);
            if (old != null) next = new QuestDialog(old.id(), old.title(), pages);
            dialogs.put(next.id(), next);
            persistOrRollback(player, old, next.id());
        }
    }

    private void persistOrRollback(Player player, QuestDialog previous, String id) {
        if (!save()) {
            if (previous == null) dialogs.remove(id); else dialogs.put(id, previous);
            player.sendMessage("§cKhông lưu được quest-dialog; xem console.");
        } else player.sendMessage("§aĐã lưu quest-dialog §f" + id + "§a.");
        if (dialogs.containsKey(id)) openEditor(player, id); else openList(player);
    }

    private void load() throws IOException {
        if (!storageFile.isFile()) return;
        YamlConfiguration yaml = new YamlConfiguration();
        try { YamlDuplicateKeyValidator.validateNoDuplicateKeys(storageFile); yaml.load(storageFile); }
        catch (InvalidConfigurationException | YAMLException exception) {
            throw new IOException("Quest-dialog data file is invalid YAML.", exception);
        }
        if (yaml.getInt("schema-version", -1) != 1) throw new IOException("Unsupported quest-dialog schema.");
        var section = yaml.getConfigurationSection("dialogs");
        if (section == null) return;
        if (section.getKeys(false).size() > MAX_DIALOGS) throw new IOException("Quest-dialog limit exceeded.");
        for (String id : section.getKeys(false)) {
            String title = yaml.getString("dialogs." + id + ".title", "").trim();
            List<String> pages = yaml.getStringList("dialogs." + id + ".pages");
            if (!ID_PATTERN.matcher(id).matches() || !validTitle(title) || parsePages(String.join("|", pages)) == null) {
                plugin.getLogger().warning("[QuestDialog] Ignoring invalid definition id='" + id + "'.");
                continue;
            }
            dialogs.put(id, new QuestDialog(id, title, pages));
        }
        plugin.getLogger().info("[QuestDialog] Loaded " + dialogs.size() + " reusable dialogs.");
    }

    private boolean save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 1);
        yaml.setComments("schema-version", List.of("[VI] Phiên bản cấu trúc file quest-dialog.",
            "[EN] Quest-dialog file schema version."));
        for (QuestDialog dialog : dialogs.values()) {
            String base = "dialogs." + dialog.id();
            yaml.set(base + ".title", dialog.title());
            yaml.set(base + ".pages", dialog.pages());
            yaml.setComments(base, List.of("[VI] Hội thoại RPG do admin tạo trong GUI.",
                "[EN] RPG dialog created by an admin through the GUI."));
        }
        File temporary = null;
        try {
            temporary = Files.createTempFile(storageFile.getParentFile().toPath(), "quest-dialogs-", ".yml.tmp").toFile();
            Files.writeString(temporary.toPath(), yaml.saveToString(), java.nio.charset.StandardCharsets.UTF_8);
            try { Files.move(temporary.toPath(), storageFile.toPath(), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary.toPath(), storageFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "[QuestDialog] Could not save quest-dialog data.", exception);
            if (temporary != null) temporary.delete();
            return false;
        }
    }

    private static List<String> parsePages(String content) {
        String[] values = content.split("\\|", -1);
        if (values.length < 1 || values.length > 12) return null;
        List<String> result = new ArrayList<>(values.length);
        for (String value : values) {
            String page = value.trim();
            if (page.isBlank() || page.length() > 256) return null;
            result.add(page);
        }
        return List.copyOf(result);
    }

    private static boolean validTitle(String title) { return title != null && !title.isBlank() && title.length() <= 64; }
    private static String stripAndFormat(String text) { return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', text)); }
    private static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); meta.setLore(lore); item.setItemMeta(meta); }
        return item;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Lệnh này chỉ dùng trong game."); return true; }
        if (args.length == 0 || args[0].equalsIgnoreCase("admin")) openAdmin(player);
        else sender.sendMessage("§eDùng: /vnquestdialog admin");
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && "admin".startsWith(args[0].toLowerCase(Locale.ROOT))) return List.of("admin");
        return List.of();
    }

    private enum Menu { LIST, EDIT, CONFIRM_DELETE, SELECT }
    private enum InputField { CREATE_ID, CREATE_TITLE, RENAME, CONTENT }
    private record PendingInput(InputField field, String dialogId, int page, String value) {
        private PendingInput(InputField field, String dialogId, int page) {
            this(field, dialogId, page, null);
        }
    }
    private record QuestDialog(String id, String title, List<String> pages) {
        private QuestDialog { pages = List.copyOf(pages); }
    }

    private static final class DialogueSession {
        private final UUID viewer;
        private final String speaker;
        private final List<String> chunks;
        private final Runnable onContinue;
        private int chunkIndex;
        private int pauseTicks;
        private int visibleCodePoints;
        private int codePoints;
        private String text = "";
        private boolean cancelled;
        private DialogueSession(UUID viewer, String speaker, List<String> chunks, Runnable onContinue) {
            this.viewer = viewer;
            this.speaker = speaker;
            this.chunks = List.copyOf(chunks);
            this.onContinue = onContinue;
        }
    }

    private static final class DialogHolder implements InventoryHolder {
        private final UUID viewer;
        private final Menu menu;
        private final String dialogId;
        private final int page;
        private final Consumer<String> callback;
        private String selectedId;
        private final Map<Integer, String> dialogSlots = new LinkedHashMap<>();
        private Inventory inventory;
        private DialogHolder(UUID viewer, Menu menu, String dialogId, int page, Consumer<String> callback) {
            this.viewer = viewer; this.menu = menu; this.dialogId = dialogId; this.page = page; this.callback = callback;
        }
        @Override public Inventory getInventory() { return inventory; }
    }
}
