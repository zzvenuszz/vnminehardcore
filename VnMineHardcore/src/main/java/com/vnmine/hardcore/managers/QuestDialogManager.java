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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Pattern;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
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

/** Stores quest-specific/public typed dialogs and displays story/quest text as an action-bar typewriter. */
public final class QuestDialogManager implements Listener {
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

    public boolean hasDialog(String id) {
        return id != null && dialogs.containsKey(id);
    }

    public void openSelector(Player player, String selectedId, Consumer<String> callback) {
        player.sendMessage("§cKhông xác định được quest và trạng thái cho quest-dialog này.");
    }

    public void openSelector(Player player, String selectedId, Consumer<String> callback,
                             String questId, String dialogType) {
        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage("§cBạn không có quyền quản lý quest-dialog.");
            return;
        }
        if (!Set.of("AVAILABLE", "IN_PROGRESS", "COMPLETED", "UNAVAILABLE").contains(dialogType)) {
            player.sendMessage("§cLoại quest-dialog không hợp lệ.");
            return;
        }
        openSelector(player, selectedId, new DialogContext(questId, dialogType, 0, selectedId, callback), 0);
    }

    private void openSelector(Player player, String selectedId, DialogContext context, int requestedPage) {
        List<QuestDialog> values = dialogs.values().stream()
            .filter(dialog -> visibleFor(dialog, context))
            .sorted(java.util.Comparator.comparing(QuestDialog::id)).toList();
        int pageCount = Math.max(1, (values.size() + 44) / 45);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        DialogContext pageContext = context.withPage(page);
        DialogHolder holder = new DialogHolder(player.getUniqueId(), Menu.SELECT, null, page, context.callback(), pageContext);
        holder.selectedId = selectedId;
        Inventory menu = Bukkit.createInventory(holder, 54, "Quest-dialog • " + context.dialogType());
        holder.inventory = menu;
        for (int slot = 0; slot < 45 && page * 45 + slot < values.size(); slot++) {
            QuestDialog dialog = values.get(page * 45 + slot);
            menu.setItem(slot, item(Material.WRITABLE_BOOK,
                (dialog.id().equals(selectedId) ? "§a✓ " : "§f") + LegacyText.translate(dialog.title()),
                List.of("§7ID: §f" + dialog.id(), "§7Loại: §f" + dialogTypeLabel(dialog.type()),
                    "§7Phạm vi: " + (dialog.isPublic() ? "§aPublic" : "§eRiêng quest " + dialog.questId()),
                    "§7Số trang: §f" + dialog.pages().size(), "§eChuột trái: chọn • chuột phải: sửa")));
            holder.dialogSlots.put(slot, dialog.id());
        }
        menu.setItem(45, item(Material.BARRIER, "§cKhông dùng hội thoại", List.of("§7Xóa lựa chọn hiện tại.")));
        menu.setItem(46, item(Material.LIME_DYE, "§aTạo dialog cho quest này", List.of(
            "§7Loại dialog: §f" + dialogTypeLabel(context.dialogType()),
            "§7Sau khi nhập nội dung, chọn dialog riêng hoặc public.", "§eNhấn để bắt đầu.")));
        if (page > 0) menu.setItem(48, item(Material.LIME_CARPET, "§eTrang trước", List.of()));
        menu.setItem(49, item(Material.CRAFTING_TABLE, "§eQuay lại quest", List.of()));
        if (page + 1 < pageCount) menu.setItem(50, item(Material.CYAN_CARPET, "§eTrang sau", List.of()));
        menu.setItem(53, item(Material.PAPER, "§fĐang chọn: " + (selectedId == null ? "không có" : selectedId),
            List.of("§7Trang " + (page + 1) + "/" + pageCount)));
        if (values.isEmpty()) menu.setItem(22, item(Material.BOOK, "§7Chưa có dialog phù hợp", List.of(
            "§7Dialog private của quest khác không xuất hiện ở đây.", "§eDùng nút tạo để viết hội thoại cho trạng thái này.")));
        player.openInventory(menu);
    }

    private boolean visibleFor(QuestDialog dialog, DialogContext context) {
        if ("LEGACY".equals(dialog.type())) return dialog.isPublic();
        if (!dialog.type().equals(context.dialogType())) return false;
        return dialog.isPublic() || context.questId().equals(dialog.questId());
    }

    private String dialogTypeLabel(String type) {
        return switch (type) {
            case "AVAILABLE" -> "Có thể nhận";
            case "IN_PROGRESS" -> "Đang làm";
            case "COMPLETED" -> "Hoàn tất/nhận thưởng";
            case "UNAVAILABLE" -> "Chưa đủ điều kiện";
            default -> "Dialog cũ (public)";
        };
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
        openStoryChapter(player, speaker, chapter, null);
    }

    public void openStoryChapter(Player player, String speaker, StoryChapterLoader.StoryChapter chapter,
                                 Runnable onComplete) {
        if (chapter == null) {
            player.sendMessage("§cNPC này chưa được gán chapter truyện. Admin hãy shift+click phải NPC để chỉnh sửa.");
            return;
        }
        openPages(player, speaker, chapter.pages(), onComplete);
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
            String text = LegacyText.translate(page).replace('\n', ' ').trim();
            StringBuilder chunk = new StringBuilder();
            String activeFormat = "";
            int visible = 0;
            for (int offset = 0; offset < text.length();) {
                char current = text.charAt(offset);
                if (current == ChatColor.COLOR_CHAR && offset + 1 < text.length()) {
                    char code = Character.toLowerCase(text.charAt(offset + 1));
                    chunk.append(current).append(code);
                    if ("0123456789abcdef".indexOf(code) >= 0) activeFormat = "§" + code;
                    else if (code == 'r') activeFormat = "";
                    else if ("kl mno".replace(" ", "").indexOf(code) >= 0) activeFormat += "§" + code;
                    offset += 2;
                    continue;
                }
                int cp = text.codePointAt(offset);
                chunk.appendCodePoint(cp);
                visible++;
                offset += Character.charCount(cp);
                if (visible >= 36) {
                    chunks.add(chunk.toString());
                    chunk = new StringBuilder(activeFormat);
                    visible = 0;
                }
            }
            if (!chunk.toString().replaceAll("§[0-9a-fk-or]", "").isBlank()) chunks.add(chunk.toString());
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
        String visible = visiblePrefix(session.text, session.visibleCodePoints);
        Player player = Bukkit.getPlayer(session.viewer);
        if (player != null) {
            player.sendActionBar(LegacyComponentSerializer.legacySection()
                .deserialize("§6[" + actionBarSpeaker(session.speaker) + ":] §f" + visible));
            if (session.visibleCodePoints >= session.codePoints) session.pauseTicks = 8;
        }
    }

    private static String visiblePrefix(String text, int visibleLimit) {
        StringBuilder visible = new StringBuilder();
        int shown = 0;
        for (int offset = 0; offset < text.length();) {
            char current = text.charAt(offset);
            if (current == ChatColor.COLOR_CHAR && offset + 1 < text.length()) {
                visible.append(current).append(text.charAt(offset + 1));
                offset += 2;
                continue;
            }
            if (shown >= visibleLimit) break;
            int cp = text.codePointAt(offset);
            visible.appendCodePoint(cp);
            shown++;
            offset += Character.charCount(cp);
        }
        return visible.toString();
    }

    private static String actionBarSpeaker(String speaker) {
        String formatted = LegacyText.translate(speaker);
        String plain = ChatColor.stripColor(formatted);
        if (plain.length() <= 20) return formatted;
        return LegacyText.translate(plain.substring(0, 20));
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
            } else if (slot == 46) {
                prompt(player, new PendingInput(InputField.CREATE_ID, null, holder.page, null, holder.context));
            } else if (slot == 49) {
                finishSelection(player, holder, holder.selectedId);
            } else if (slot == 48) {
                openSelector(player, holder.selectedId, holder.context, holder.page - 1);
            } else if (slot == 50) {
                openSelector(player, holder.selectedId, holder.context, holder.page + 1);
            } else if (holder.dialogSlots.containsKey(slot)) {
                String id = holder.dialogSlots.get(slot);
                if (event.getClick().isRightClick()) openEditor(player, id, holder.context);
                else finishSelection(player, holder, id);
            }
        } else if (holder.menu == Menu.LIST) {
            if (slot == 45) prompt(player, new PendingInput(InputField.CREATE_ID, null, holder.page));
            else if (slot == 48) openList(player, holder.page - 1);
            else if (slot == 50) openList(player, holder.page + 1);
            else if (slot == 53) player.closeInventory();
            else if (holder.dialogSlots.containsKey(slot)) openEditor(player, holder.dialogSlots.get(slot));
        } else if (holder.menu == Menu.EDIT) {
            QuestDialog dialog = dialogs.get(holder.dialogId);
            if (dialog == null) { openSelector(player, holder.selectedId, holder.context, holder.page); return; }
            if (slot == 10) prompt(player, new PendingInput(InputField.RENAME, dialog.id(), holder.page, null, holder.context));
            else if (slot == 12) prompt(player, new PendingInput(InputField.CONTENT, dialog.id(), holder.page, null, holder.context));
            else if (slot == 14) openDeleteConfirmation(player, dialog.id(), holder.context);
            else if (slot == 16) togglePublic(player, holder);
            else if (slot == 49) openSelector(player, holder.selectedId, holder.context, holder.page);
        } else if (holder.menu == Menu.SCOPE) {
            if (holder.draft == null) { openSelector(player, holder.selectedId, holder.context, holder.page); return; }
            if (slot == 20) saveDraft(player, holder, false);
            else if (slot == 24) saveDraft(player, holder, true);
            else if (slot == 49) openSelector(player, holder.selectedId, holder.context, holder.page);
        } else if (holder.menu == Menu.CONFIRM_DELETE) {
            if (slot == 11) {
                if (plugin.getQuestCatalogManager() != null
                        && plugin.getQuestCatalogManager().isQuestDialogInUse(holder.dialogId)) {
                    player.sendMessage("§cKhông thể xóa: hội thoại đang được gán cho ít nhất một quest.");
                    openEditor(player, holder.dialogId, holder.context);
                    return;
                }
                QuestDialog removed = dialogs.remove(holder.dialogId);
                if (removed != null && !save()) {
                    dialogs.put(removed.id(), removed);
                    player.sendMessage("§cKhông lưu được thao tác xóa; dữ liệu cũ đã được khôi phục.");
                    openEditor(player, removed.id(), holder.context);
                } else {
                    player.sendMessage("§aĐã xóa quest-dialog.");
                    openSelector(player, holder.selectedId, holder.context, holder.page);
                }
            } else if (slot == 15) openEditor(player, holder.dialogId, holder.context);
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
        openEditor(player, id, null);
    }

    private void openEditor(Player player, String id, DialogContext context) {
        QuestDialog dialog = dialogs.get(id);
        if (dialog == null) {
            if (context != null) openSelector(player, context.selectedId(), context, context.page());
            else openList(player);
            return;
        }
        DialogHolder holder = new DialogHolder(player.getUniqueId(), Menu.EDIT, id,
            context == null ? 0 : context.page(), context == null ? null : context.callback(), context);
        holder.selectedId = context == null ? null : context.selectedId();
        Inventory menu = Bukkit.createInventory(holder, 54, "Sửa quest-dialog • " + id);
        holder.inventory = menu;
        menu.setItem(4, item(Material.WRITABLE_BOOK, "§6" + LegacyText.translate(dialog.title()), List.of(
            "§7ID: §f" + id, "§7Loại: §f" + dialogTypeLabel(dialog.type()),
            "§7Phạm vi: " + (dialog.isPublic() ? "§aPublic" : "§eRiêng quest " + dialog.questId()))));
        menu.setItem(10, item(Material.NAME_TAG, "§eĐổi tên", List.of("§7Tên hiện tại: " + LegacyText.translate(dialog.title()))));
        menu.setItem(12, item(Material.PAPER, "§bSửa nội dung", List.of("§7" + dialog.pages().size()
            + " trang.", "§7Nhập các trang, phân tách bằng |.")));
        menu.setItem(14, item(Material.REDSTONE, "§cXóa hội thoại", List.of("§7Cần xác nhận trước khi xóa.")));
        menu.setItem(16, item(dialog.isPublic() ? Material.LIME_DYE : Material.GRAY_DYE,
            dialog.isPublic() ? "§aPublic: bật" : "§ePublic: tắt", List.of(
                "§7Dialog public có thể được chọn bởi quest khác",
                "§7khi cùng loại trạng thái.", "§eNhấn để đổi phạm vi.")));
        menu.setItem(49, item(Material.CRAFTING_TABLE, "§eQuay lại danh sách dialog phù hợp", List.of()));
        player.openInventory(menu);
    }

    private void openDeleteConfirmation(Player player, String id) {
        openDeleteConfirmation(player, id, null);
    }

    private void openDeleteConfirmation(Player player, String id, DialogContext context) {
        DialogHolder holder = new DialogHolder(player.getUniqueId(), Menu.CONFIRM_DELETE, id,
            context == null ? 0 : context.page(), context == null ? null : context.callback(), context);
        holder.selectedId = context == null ? null : context.selectedId();
        Inventory menu = Bukkit.createInventory(holder, 54, "Xóa quest-dialog?");
        holder.inventory = menu;
        menu.setItem(4, item(Material.WRITABLE_BOOK, "§c" + id, List.of("§7Hành động không thể hoàn tác.")));
        menu.setItem(11, item(Material.LIME_DYE, "§aXác nhận xóa", List.of()));
        menu.setItem(15, item(Material.RED_DYE, "§eHủy", List.of()));
        player.openInventory(menu);
    }

    private void openScopeChoice(Player player, QuestDialog draft, DialogContext context) {
        DialogHolder holder = new DialogHolder(player.getUniqueId(), Menu.SCOPE, draft.id(), context.page(),
            context.callback(), context);
        holder.selectedId = context.selectedId();
        holder.draft = draft;
        Inventory menu = Bukkit.createInventory(holder, 54, "Phạm vi quest-dialog • " + draft.type());
        holder.inventory = menu;
        menu.setItem(4, item(Material.WRITABLE_BOOK, "§6" + LegacyText.translate(draft.title()), List.of(
            "§7Loại: §f" + dialogTypeLabel(draft.type()), "§7ID: §f" + draft.id())));
        menu.setItem(20, item(Material.BOOK, "§eChỉ dùng cho quest này", List.of(
            "§7Dialog chỉ xuất hiện trong quest §f" + context.questId(),
            "§7và trạng thái §f" + dialogTypeLabel(context.dialogType()) + "§7.")));
        menu.setItem(24, item(Material.ENCHANTED_BOOK, "§aĐánh dấu Public", List.of(
            "§7Dialog có thể được chọn bởi các quest khác", "§7ở cùng loại trạng thái.")));
        menu.setItem(49, item(Material.CRAFTING_TABLE, "§eHủy và quay lại", List.of()));
        player.openInventory(menu);
    }

    private void saveDraft(Player player, DialogHolder holder, boolean isPublic) {
        QuestDialog draft = holder.draft;
        DialogContext context = holder.context;
        if (draft == null || context == null || dialogs.containsKey(draft.id())) {
            player.sendMessage("§cDialog không hợp lệ hoặc ID đã được dùng.");
            openSelector(player, holder.selectedId, holder.context, holder.page);
            return;
        }
        QuestDialog saved = new QuestDialog(draft.id(), draft.title(), draft.pages(), draft.type(), isPublic,
            isPublic ? null : context.questId());
        dialogs.put(saved.id(), saved);
        if (!save()) {
            dialogs.remove(saved.id());
            player.sendMessage("§cKhông lưu được quest-dialog; chưa gán dialog cho quest.");
            openSelector(player, holder.selectedId, holder.context, holder.page);
            return;
        }
        player.sendMessage("§aĐã tạo dialog " + (isPublic ? "public" : "riêng cho quest này") + ".");
        context.callback().accept(saved.id());
    }

    private void togglePublic(Player player, DialogHolder holder) {
        QuestDialog old = dialogs.get(holder.dialogId);
        if (old == null || holder.context == null) {
            player.sendMessage("§cChỉ sửa được phạm vi dialog từ selector của một quest.");
            return;
        }
        boolean makePublic = !old.isPublic();
        QuestDialog updated = new QuestDialog(old.id(), old.title(), old.pages(),
            makePublic ? old.type() : holder.context.dialogType(), makePublic,
            makePublic ? null : holder.context.questId());
        dialogs.put(old.id(), updated);
        if (!save()) {
            dialogs.put(old.id(), old);
            player.sendMessage("§cKhông lưu được phạm vi dialog.");
        } else player.sendMessage("§aĐã đổi dialog thành " + (makePublic ? "public" : "riêng quest này") + ".");
        openEditor(player, old.id(), holder.context);
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
            case CREATE_TITLE, RENAME -> "Nhập tên hội thoại, ví dụ &6Lời nhắn của thợ rèn (tối đa 64 ký tự). Mã màu/format dùng &.";
            case CONTENT -> "Nhập nội dung tối đa 12 trang, mỗi trang tối đa 256 ký tự; dùng | để tách trang. Ví dụ: &6Chào người lữ khách.|&bHãy giúp ta tìm quặng.";
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
            if (input.context() != null) openSelector(player, input.context().selectedId(), input.context(), input.page());
            else if (input.dialogId() == null) openList(player); else openEditor(player, input.dialogId());
            return;
        }
        if (input.field() == InputField.CREATE_ID) {
            String id = text.toLowerCase(Locale.ROOT);
            if (!ID_PATTERN.matcher(id).matches() || dialogs.containsKey(id) || dialogs.size() >= MAX_DIALOGS) {
                player.sendMessage("§cID không hợp lệ/trùng hoặc đã đủ 200 hội thoại. Ví dụ: §fquest-dialog-hunter");
                prompt(player, input); return;
            }
            prompt(player, new PendingInput(InputField.CREATE_TITLE, id, input.page(), null, input.context()));
        } else if (input.field() == InputField.CREATE_TITLE) {
            if (!validTitle(text)) { player.sendMessage("§cTên cần 1-64 ký tự."); prompt(player, input); return; }
            prompt(player, new PendingInput(InputField.CONTENT, input.dialogId(), input.page(), text, input.context()));
        } else if (input.field() == InputField.RENAME) {
            QuestDialog old = dialogs.get(input.dialogId());
            if (old == null || !validTitle(text)) { player.sendMessage("§cTên không hợp lệ."); prompt(player, input); return; }
            dialogs.put(old.id(), old.withTitle(text));
            persistOrRollback(player, old, input.dialogId(), input.context());
        } else if (input.field() == InputField.CONTENT) {
            List<String> pages = parsePages(text);
            if (pages == null) { player.sendMessage("§cNội dung phải có 1-12 trang, mỗi trang 1-256 ký tự."); prompt(player, input); return; }
            QuestDialog old = dialogs.get(input.dialogId());
            if (old == null && input.context() != null) {
                QuestDialog draft = new QuestDialog(input.dialogId(), input.value(), pages,
                    input.context().dialogType(), false, input.context().questId());
                openScopeChoice(player, draft, input.context());
            } else if (old != null) {
                dialogs.put(old.id(), old.withPages(pages));
                persistOrRollback(player, old, old.id(), input.context());
            }
        }
    }

    private void persistOrRollback(Player player, QuestDialog previous, String id, DialogContext context) {
        if (!save()) {
            if (previous == null) dialogs.remove(id); else dialogs.put(id, previous);
            player.sendMessage("§cKhông lưu được quest-dialog; xem console.");
        } else player.sendMessage("§aĐã lưu quest-dialog §f" + id + "§a.");
        if (dialogs.containsKey(id)) openEditor(player, id, context);
        else if (context != null) openSelector(player, context.selectedId(), context, context.page());
        else openList(player);
    }

    private void load() throws IOException {
        if (!storageFile.isFile()) return;
        YamlConfiguration yaml = new YamlConfiguration();
        try { YamlDuplicateKeyValidator.validateNoDuplicateKeys(storageFile); yaml.load(storageFile); }
        catch (InvalidConfigurationException | YAMLException exception) {
            throw new IOException("Quest-dialog data file is invalid YAML.", exception);
        }
        int schema = yaml.getInt("schema-version", -1);
        if (schema != 1 && schema != 2) throw new IOException("Unsupported quest-dialog schema.");
        var section = yaml.getConfigurationSection("dialogs");
        if (section == null) return;
        if (section.getKeys(false).size() > MAX_DIALOGS) throw new IOException("Quest-dialog limit exceeded.");
        for (String id : section.getKeys(false)) {
            String title = LegacyText.translate(yaml.getString("dialogs." + id + ".title", "").trim());
            List<String> pages = yaml.getStringList("dialogs." + id + ".pages");
            if (!ID_PATTERN.matcher(id).matches() || !validTitle(title) || parsePages(String.join("|", pages)) == null) {
                plugin.getLogger().warning("[QuestDialog] Ignoring invalid definition id='" + id + "'.");
                continue;
            }
            String type = schema == 1 ? "LEGACY" : yaml.getString("dialogs." + id + ".type", "");
            boolean isPublic = schema == 1 || yaml.getBoolean("dialogs." + id + ".public", false);
            String questId = schema == 1 ? null : yaml.getString("dialogs." + id + ".quest-id");
            if (schema == 2 && !Set.of("AVAILABLE", "IN_PROGRESS", "COMPLETED", "UNAVAILABLE").contains(type)) {
                plugin.getLogger().warning("[QuestDialog] Ignoring invalid type for id='" + id + "'.");
                continue;
            }
            if (!isPublic && (questId == null || !ID_PATTERN.matcher(questId).matches())) {
                plugin.getLogger().warning("[QuestDialog] Ignoring private dialog without valid quest id='" + id + "'.");
                continue;
            }
            dialogs.put(id, new QuestDialog(id, title, pages, type, isPublic, questId));
        }
        plugin.getLogger().info("[QuestDialog] Loaded " + dialogs.size() + " typed quest dialogs.");
    }

    private boolean save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 2);
        yaml.setComments("schema-version", List.of("[VI] Phiên bản cấu trúc file quest-dialog.",
            "[EN] Quest-dialog file schema version."));
        for (QuestDialog dialog : dialogs.values()) {
            String base = "dialogs." + dialog.id();
            yaml.set(base + ".title", dialog.title());
            yaml.set(base + ".pages", dialog.pages());
            yaml.set(base + ".type", dialog.type());
            yaml.set(base + ".public", dialog.isPublic());
            if (!dialog.isPublic()) yaml.set(base + ".quest-id", dialog.questId());
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
    private static String stripAndFormat(String text) { return LegacyText.strip(text); }
    private static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(LegacyText.translate(name));
            meta.setLore(lore.stream().map(LegacyText::translate).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private enum Menu { LIST, EDIT, CONFIRM_DELETE, SELECT, SCOPE }
    private enum InputField { CREATE_ID, CREATE_TITLE, RENAME, CONTENT }
    private record PendingInput(InputField field, String dialogId, int page, String value, DialogContext context) {
        private PendingInput(InputField field, String dialogId, int page) {
            this(field, dialogId, page, null, null);
        }
        private PendingInput(InputField field, String dialogId, int page, String value) {
            this(field, dialogId, page, value, null);
        }
    }
    private record DialogContext(String questId, String dialogType, int page, String selectedId,
                                 Consumer<String> callback) {
        private DialogContext withPage(int newPage) {
            return new DialogContext(questId, dialogType, newPage, selectedId, callback);
        }
    }
    private record QuestDialog(String id, String title, List<String> pages, String type,
                               boolean isPublic, String questId) {
        private QuestDialog { pages = List.copyOf(pages); }
        private QuestDialog withTitle(String newTitle) {
            return new QuestDialog(id, newTitle, pages, type, isPublic, questId);
        }
        private QuestDialog withPages(List<String> newPages) {
            return new QuestDialog(id, title, newPages, type, isPublic, questId);
        }
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
        private final DialogContext context;
        private QuestDialog draft;
        private String selectedId;
        private final Map<Integer, String> dialogSlots = new LinkedHashMap<>();
        private Inventory inventory;
        private DialogHolder(UUID viewer, Menu menu, String dialogId, int page, Consumer<String> callback) {
            this(viewer, menu, dialogId, page, callback, null);
        }
        private DialogHolder(UUID viewer, Menu menu, String dialogId, int page, Consumer<String> callback,
                             DialogContext context) {
            this.viewer = viewer; this.menu = menu; this.dialogId = dialogId; this.page = page;
            this.callback = callback; this.context = context;
        }
        @Override public Inventory getInventory() { return inventory; }
    }
}
