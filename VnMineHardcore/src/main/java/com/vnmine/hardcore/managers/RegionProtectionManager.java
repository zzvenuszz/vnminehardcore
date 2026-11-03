package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import org.bukkit.Color;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Rail;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Animals;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowman;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/** Selection wand, named region flags, and start-world safety settings. */
public final class RegionProtectionManager implements Listener, CommandExecutor, TabCompleter {
    public static final String ADMIN_PERMISSION = "vnmine.command.worldedit";
    private static final int EDIT_BATCH_SIZE = 512;
    private static final long DEFAULT_MAX_EDIT_BLOCKS = 32_768L;
    private static final int DEFAULT_HISTORY_LIMIT = 50;
    private static final int DEFAULT_MAX_EDIT_CHUNKS = 256;
    private static final int DEFAULT_BRUSH_PREVIEW_BLOCKS = -1;
    private static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43
    };
    private static final String[] FLAG_KEYS = {
        "deny-block-break", "deny-block-place", "deny-interact", "deny-pvp",
        "deny-mob-spawning", "disable-hunger", "disable-thirst"
    };
    private static final String[] FLAG_NAMES = {
        "Chặn phá khối", "Chặn đặt khối", "Chặn tương tác khối", "Chặn PvP",
        "Chặn sinh quái", "Tắt tiêu hao đói", "Tắt tiêu hao khát"
    };

    private final VnMineHardcore plugin;
    private final NamespacedKey wandKey;
    private final NamespacedKey brushKey;
    private final NamespacedKey clipboardPasteToolKey;
    private final NamespacedKey deltreeToolKey;
    private final File dataFile;
    private final Map<String, ProtectedRegion> regions = new LinkedHashMap<>();
    private final Map<UUID, Selection> selections = new HashMap<>();
    private final Map<UUID, String> pendingNames = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Clipboard> pendingSchemaSaves = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Deque<EditOperation>> undoHistory = new HashMap<>();
    private final Map<UUID, Deque<EditOperation>> redoHistory = new HashMap<>();
    private final Map<UUID, Clipboard> clipboards = new HashMap<>();
    private final Map<UUID, BlockMatcher> playerMasks = new HashMap<>();
    private BlockMatcher globalMask;
    private final Map<UUID, BrushConfig> brushConfigs = new HashMap<>();
    private final Map<UUID, BrushPreview> brushPreviews = new HashMap<>();
    private final Map<UUID, PastePreview> pastePreviews = new HashMap<>();
    private final Set<UUID> activeEdits = new HashSet<>();
    private final BukkitTask selectionParticleTask;
    private final BukkitTask brushPreviewTask;

    public RegionProtectionManager(VnMineHardcore plugin) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey(plugin, "worldedit_region_wand");
        this.brushKey = new NamespacedKey(plugin, "worldedit_clipboard_brush");
        this.clipboardPasteToolKey = new NamespacedKey(plugin, "worldedit_clipboard_paste_tool");
        this.deltreeToolKey = new NamespacedKey(plugin, "worldedit_deltree_tool");
        this.dataFile = new File(plugin.getDataFolder(), "protected-regions.yml");
        load();
        for (World world : Bukkit.getWorlds()) applyStartWorldSettings(world);
        long interval = Math.max(5L, Math.min(40L,
            plugin.getConfig().getLong("worldedit.selection-particles.interval-ticks", 10L)));
        selectionParticleTask = Bukkit.getScheduler().runTaskTimer(plugin,
            this::renderSelectionParticles, interval, interval);
        brushPreviewTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickBrushPreviews, 1L, 1L);
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Lệnh này chỉ dùng trong game.");
            return true;
        }
        if (!canManage(player)) {
            player.sendMessage("§cBạn không có quyền quản lý vùng.");
            return true;
        }
        if ("deltree".equalsIgnoreCase(command.getName())) { giveDeltreeTool(player); return true; }
        if (args.length == 0) { openAdminMenu(player, 0); return true; }
        String operation = args[0].replaceFirst("^/+", "").toLowerCase(Locale.ROOT);
        if ("wand".equals(operation)) { giveWand(player); return true; }
        executeWorldEditCommand(player, operation, java.util.Arrays.copyOfRange(args, 1, args.length));
        return true;
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(List.of("wand", "help", "pos1", "pos2", "hpos1", "hpos2", "sel", "chunk", "size",
            "desel", "set", "clear", "replace", "walls", "faces", "overlay", "center", "smooth", "mask", "gmask", "br", "square", "circle", "round", "count", "expand", "contract",
            "shift", "move", "stack", "copy", "cut", "paste", "schem", "undo", "redo", "clearhistory", "limit",
            "sphere", "hsphere", "cyl", "hcyl", "pyramid", "hpyramid", "forest", "br", "mask", "gmask", "smooth", "deltree", "thru", "unstuck", "jumpto", "rotate", "flip"), args[0]);
        if (args.length == 2 && Set.of("set", "walls", "faces", "overlay", "center", "cut").contains(args[0].toLowerCase(Locale.ROOT)))
            return blockSuggestions(args[1]);
        if (args.length == 2 && "sel".equalsIgnoreCase(args[0]))
            return filter(List.of("cuboid", "sphere", "ellipsoid"), args[1]);
        if (args.length == 2 && "br".equalsIgnoreCase(args[0]))
            return filter(List.of("sphere", "smooth", "none"), args[1]);
        if (args.length == 2 && "rotate".equalsIgnoreCase(args[0])) return filter(List.of("90", "180", "270"), args[1]);
        if (args.length == 2 && "flip".equalsIgnoreCase(args[0])) return filter(List.of("x", "z", "north", "south", "east", "west"), args[1]);
        if (args.length == 2 && "schem".equalsIgnoreCase(args[0]))
            return filter(List.of("save", "load"), args[1]);
        if (args.length == 3 && Set.of("replace", "cut").contains(args[0].toLowerCase(Locale.ROOT)))
            return blockSuggestions(args[2]);
        if (args.length == 3 && Set.of("square", "circle", "round").contains(args[0].toLowerCase(Locale.ROOT)))
            return blockSuggestions(args[2]);
        return List.of();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldEditTabComplete(TabCompleteEvent event) {
        if (!(event.getSender() instanceof Player player) || !canManage(player)) return;
        String buffer = event.getBuffer();
        if (buffer == null) return;
        String input;
        if (buffer.startsWith("//")) input = buffer.substring(2);
        else if (buffer.startsWith("/")) input = buffer.substring(1);
        else if (event.isCommand()) input = buffer;
        else return;
        String[] tokens = input.split("\\s+", -1);
        List<String> commands = List.of("wand", "help", "pos1", "pos2", "hpos1", "hpos2", "sel", "chunk", "size",
            "desel", "set", "clear", "replace", "walls", "faces", "overlay", "center", "smooth", "mask", "gmask", "br", "square", "circle", "round", "count",
            "expand", "contract", "shift", "move", "stack", "copy", "cut", "paste", "schem", "undo", "redo",
            "clearhistory", "limit", "sphere", "hsphere", "cyl", "hcyl", "pyramid", "hpyramid", "forest", "br", "mask", "gmask", "smooth", "deltree", "thru", "unstuck", "jumpto", "rotate", "flip");
        if (tokens.length == 1) {
            if (!tokens[0].isEmpty() && commands.stream().noneMatch(value -> value.startsWith(tokens[0]))) return;
            event.setCompletions(filter(commands, tokens[0]));
            return;
        }
        String command = tokens[0].toLowerCase(Locale.ROOT);
        if (tokens.length == 2 && Set.of("set", "walls", "faces", "overlay", "center", "cut", "replace")
                .contains(command)) {
            event.setCompletions(blockSuggestions(tokens[1]));
        } else if (tokens.length == 2 && "sel".equals(command)) {
            event.setCompletions(filter(List.of("cuboid", "sphere", "ellipsoid"), tokens[1]));
        } else if (tokens.length == 2 && "br".equals(command)) {
            event.setCompletions(filter(List.of("sphere", "smooth", "none"), tokens[1]));
        } else if (tokens.length == 2 && "rotate".equals(command)) {
            event.setCompletions(filter(List.of("90", "180", "270"), tokens[1]));
        } else if (tokens.length == 2 && "flip".equals(command)) {
            event.setCompletions(filter(List.of("x", "z", "north", "south", "east", "west"), tokens[1]));
        } else if (tokens.length == 2 && "schem".equals(command)) {
            event.setCompletions(filter(List.of("save", "load"), tokens[1]));
        } else if (tokens.length == 3 && Set.of("replace", "cut").contains(command)) {
            event.setCompletions(blockSuggestions(tokens[2]));
        } else if (tokens.length == 3 && Set.of("square", "circle", "round").contains(command)) {
            event.setCompletions(blockSuggestions(tokens[2]));
        } else if (tokens.length == 2 && "limit".equals(command)) {
            event.setCompletions(filter(List.of("unlimited"), tokens[1]));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldEditCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage().trim();
        if (!message.startsWith("//")) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!canManage(player)) { player.sendMessage("§cBạn không có quyền dùng WorldEdit tích hợp."); return; }
        String commandLine = message.substring(2).trim();
        if (commandLine.isEmpty()) { sendWorldEditHelp(player); return; }
        String[] parts = commandLine.split("\\s+");
        executeWorldEditCommand(player, parts[0], java.util.Arrays.copyOfRange(parts, 1, parts.length));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        undoHistory.remove(id); redoHistory.remove(id); clipboards.remove(id); selections.remove(id); pendingNames.remove(id); pendingSchemaSaves.remove(id);
        playerMasks.remove(id); brushConfigs.remove(id);
        removeBrushPreview(id);
        removePastePreview(id);
    }

    public void openAdminMenu(Player player) { openAdminMenu(player, 0); }

    public void shutdown() {
        selectionParticleTask.cancel();
        brushPreviewTask.cancel();
        for (UUID playerId : new HashSet<>(brushPreviews.keySet())) removeBrushPreview(playerId);
        for (UUID playerId : new HashSet<>(pastePreviews.keySet())) removePastePreview(playerId);
    }

    private void openAdminMenu(Player player, int requestedPage) {
        List<ProtectedRegion> ordered = regions.values().stream()
            .sorted(Comparator.comparing(region -> region.id.toLowerCase(Locale.ROOT))).toList();
        int pages = Math.max(1, (ordered.size() + CONTENT_SLOTS.length - 1) / CONTENT_SLOTS.length);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        RegionHolder holder = new RegionHolder(player.getUniqueId(), "list", null, page);
        Inventory menu = Bukkit.createInventory(holder, 54, "WorldEdit / WorldGuard • " + (page + 1) + "/" + pages);
        holder.inventory = menu;
        for (int i = page * CONTENT_SLOTS.length; i < Math.min((page + 1) * CONTENT_SLOTS.length, ordered.size()); i++) {
            ProtectedRegion region = ordered.get(i);
            int slot = CONTENT_SLOTS[i - page * CONTENT_SLOTS.length];
            holder.choices.put(slot, region.id);
            menu.setItem(slot, icon(Material.IRON_BARS, "§b" + region.id, List.of(
                "§7World: §f" + region.world, "§7Vùng: §f" + region.minX + " " + region.minY + " " + region.minZ
                    + " → " + region.maxX + " " + region.maxY + " " + region.maxZ,
                region.flag("deny-block-break") || region.flag("deny-block-place")
                    ? "§cCó bảo vệ block" : "§aKhông chặn build", "§eNhấn để quản lý flags.")));
        }
        Selection selection = selections.get(player.getUniqueId());
        menu.setItem(4, icon(Material.WOODEN_AXE, "§eBúa chọn vùng WorldEdit", List.of(
            "§7Chuột trái: điểm 1 • Chuột phải: điểm 2",
            "§7Vùng chọn hiện tại: " + selectionText(selection), "§eNhấn để nhận búa.")));
        menu.setItem(8, icon(Material.NAME_TAG, "§aTạo vùng từ lựa chọn", List.of(
            selection != null && selection.complete() ? "§aĐã chọn đủ hai góc." : "§cCần chọn đủ hai góc cùng world.",
            "§eNhấn để đặt tên vùng bằng chat.")));
        menu.setItem(46, icon(Material.WRITABLE_BOOK, "§dLệnh WorldEdit tích hợp", List.of(
            "§7//set <pattern> • //replace <from> <to>", "§7//walls <pattern> • //faces <pattern> • //overlay <pattern>",
            "§7//copy • //cut • //paste [-a] • //undo • //redo", "§7Nhận stick brush để vẽ hoặc cuốc chim để preview/xoay/đặt clipboard.",
            "§eGõ //help để xem hướng dẫn.")));
        if (page > 0) menu.setItem(45, icon(Material.ARROW, "§eTrang trước", List.of()));
        menu.setItem(47, icon(Material.SUNFLOWER, "§bCài đặt World bắt đầu/reset", List.of(
            "§7Bảo vệ block, tương tác, PvP, quái, thời gian, đói và khát.")));
        menu.setItem(50, icon(Material.STICK, "§aLấy Clipboard Brush", List.of(
            "§7Dùng pattern đã //copy; preview bám mặt block đang ngắm.", "§7Chuột phải để xác nhận đóng dấu; air được bỏ qua.",
            "§7Mỗi lần vẽ là một bước //undo.", "§eCần //copy một vùng trước.")));
        menu.setItem(51, icon(Material.WOODEN_PICKAXE, "§bLấy Clipboard Paste Tool", List.of(
            "§7Preview đầy đủ nội dung đã //copy trên mặt đất.", "§7Chuột trái xoay 90°; chuột phải đặt.",
            "§7Block air trong clipboard cũng được đặt.", "§7Mỗi lần đặt lưu thành một bước //undo.")));
        menu.setItem(49, icon(Material.ARROW, "§7Quay lại menu Admin", List.of()));
        if ((page + 1) * CONTENT_SLOTS.length < ordered.size()) menu.setItem(53, icon(Material.ARROW, "§eTrang sau", List.of()));
        player.openInventory(menu);
    }

    public void openStartWorldSettings(Player player) {
        String root = startWorldPath();
        boolean guard = plugin.getConfig().getBoolean(root + ".protection.enabled", true);
        RegionHolder holder = new RegionHolder(player.getUniqueId(), "start-world", null, 0);
        Inventory menu = Bukkit.createInventory(holder, 54, "World bắt đầu • Bảo vệ");
        holder.inventory = menu;
        menu.setItem(10, toggle(Material.SHIELD, "Bảo vệ block/tương tác", root + ".protection.enabled", guard,
            "Bật để áp dụng các tùy chọn chặn block và tương tác."));
        menu.setItem(12, toggle(Material.IRON_PICKAXE, "Chặn phá block", root + ".protection.block-break", true,
            "Khi bảo vệ chính bật, người chơi thường không phá được block."));
        menu.setItem(14, toggle(Material.BRICKS, "Chặn đặt block", root + ".protection.block-place", true,
            "Khi bảo vệ chính bật, người chơi thường không đặt được block."));
        menu.setItem(16, toggle(Material.CHEST, "Chặn tương tác", root + ".protection.interactions", true,
            "Chặn mở rương, cửa, nút và tương tác block."));
        menu.setItem(20, toggle(Material.IRON_SWORD, "Cho phép PvP", root + ".pvp-enabled", false,
            "Bật cho phép người chơi gây sát thương lẫn nhau."));
        menu.setItem(22, toggle(Material.ZOMBIE_SPAWN_EGG, "Cho phép sinh quái", root + ".mob-spawning-enabled", false,
            "Bật sinh quái tự nhiên và từ spawner."));
        menu.setItem(24, toggle(Material.CLOCK, "Thời gian trôi", root + ".time-passes", false,
            "Tắt để dừng chu kỳ ngày/đêm trong world bắt đầu."));
        menu.setItem(30, toggle(Material.COOKED_BEEF, "Tiêu hao đói", root + ".hunger-drain-enabled", false,
            "Tắt để không mất food trong world bắt đầu."));
        menu.setItem(32, toggle(Material.WATER_BUCKET, "Tiêu hao khát", root + ".thirst-drain-enabled", false,
            "Tắt để không mất thirst trong world bắt đầu."));
        menu.setItem(49, icon(Material.ARROW, "§7Quay lại", List.of()));
        menu.setItem(53, icon(Material.BARRIER, "§cĐóng", List.of()));
        player.openInventory(menu);
    }

    private void executeWorldEditCommand(Player player, String rawCommand, String[] args) {
        String command = rawCommand.toLowerCase(Locale.ROOT).replaceFirst("^/+", "");
        if (command.equals("help") || command.equals("we")) { sendWorldEditHelp(player); return; }
        if (activeEdits.contains(player.getUniqueId())) { player.sendMessage("§eThao tác vùng trước vẫn đang chạy."); return; }
        try {
            switch (command) {
                case "wand" -> giveWand(player);
                case "pos1", "pos2", "hpos1", "hpos2" -> selectPosition(player, command, args);
                case "sel" -> setSelectionShape(player, args);
                case "chunk" -> selectChunk(player);
                case "size" -> reportSelectionSize(player);
                case "desel", "deselect" -> { selections.remove(player.getUniqueId()); player.sendMessage("§eĐã bỏ vùng chọn."); }
                case "set" -> editSet(player, args);
                case "clear" -> editClear(player, args);
                case "replace", "re", "rep" -> editReplace(player, args);
                case "walls" -> editShape(player, args, Shape.WALLS);
                case "faces", "outline" -> editShape(player, args, Shape.FACES);
                case "overlay" -> editOverlay(player, args);
                case "center", "middle" -> editCenter(player, args);
                case "smooth" -> editSmooth(player, args);
                case "mask" -> setMask(player, args, false);
                case "gmask" -> setMask(player, args, true);
                case "br" -> setBrush(player, args);
                case "square", "circle", "round" -> editPlane(player, command, args);
                case "sphere", "hsphere", "cyl", "hcyl", "pyramid", "hpyramid" -> generateShape(player, command, args);
                case "forest" -> generateForest(player, args);
                case "count" -> countBlocks(player, args);
                case "expand" -> adjustSelection(player, args, true);
                case "contract" -> adjustSelection(player, args, false);
                case "shift" -> shiftSelection(player, args);
                case "move" -> moveSelection(player, args);
                case "stack" -> stackSelection(player, args);
                case "copy" -> copySelection(player);
                case "cut" -> cutSelection(player, args);
                case "paste" -> pasteClipboard(player, args);
                case "rotate" -> rotateClipboardCommand(player, args);
                case "flip" -> flipClipboardCommand(player, args);
                case "schem" -> schematicCommand(player, args);
                case "undo" -> applyHistory(player, false);
                case "redo" -> applyHistory(player, true);
                case "clearhistory" -> { undoHistory.remove(player.getUniqueId()); redoHistory.remove(player.getUniqueId()); player.sendMessage("§eĐã xóa lịch sử undo/redo."); }
                case "limit" -> setEditLimit(player, args);
                case "thru" -> teleportThrough(player);
                case "unstuck" -> teleportUnstuck(player);
                case "jumpto" -> jumpToTarget(player);
                case "deltree" -> giveDeltreeTool(player);
                default -> player.sendMessage("§cLệnh chưa hỗ trợ: §f//" + command + "§c. Gõ §f//help§c.");
            }
        } catch (IllegalArgumentException exception) {
            player.sendMessage("§c" + exception.getMessage());
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "[WorldRegion] command failed player=" + player.getName()
                + ", command=//" + command, exception);
            player.sendMessage("§cLệnh lỗi. Không có thay đổi nào được xác nhận; xem console.");
        }
    }

    private void sendWorldEditHelp(Player player) {
        player.sendMessage("§6WorldEdit tích hợp §7• chọn bằng búa hoặc //pos1 //pos2 //hpos1 //hpos2 //chunk; //sel cuboid|sphere|ellipsoid");
        player.sendMessage("§e//set <pattern> §7• §e//clear §7• §e//replace <from> <to> §7• §e//walls <pattern> §7• §e//faces <pattern>");
        player.sendMessage("§7Pattern: stone, air, 0, ID cũ (35:14), block state hoặc 50%stone,50%glass.");
        player.sendMessage("§e//overlay <block> §7• §e//center <block> §7• §e//square <bán_kính> <block>");
        player.sendMessage("§e//smooth §7• §e//mask [block] §7• §e//gmask [block] §7• §e//br sphere <block> <bán_kính> | //br smooth <bán_kính>");
        player.sendMessage("§e//circle <bán_kính> <block> (đĩa đặc) §7• §e//round <bán_kính> <block> (vòng rỗng)");
        player.sendMessage("§e//sphere|hsphere <block> <bán_kính> §7• §e//cyl|hcyl <block> <bán_kính> <chiều_cao> §7• §e//pyramid|hpyramid <block> <kích_thước>");
        player.sendMessage("§7Di chuyển: //unstuck • //thru • //jumpto. Poly selection chưa được hỗ trợ.");
        player.sendMessage("§e//count <block> §7• §e//cut [block]");
        player.sendMessage("§e//copy §7• §e//paste [-a] §7• §e//undo §7• §e//redo §7• §e//size §7• §e//desel");
        player.sendMessage("§7Clipboard: //rotate <90|180|270> • //flip <x|z>");
        player.sendMessage("§7Dùng //clearhistory để xóa lịch sử undo/redo hiện tại.");
        player.sendMessage("§7Điều chỉnh vùng: //expand <n> [up|down|north|south|east|west|vert], //contract <n> [hướng], //shift <n> [hướng].");
        player.sendMessage("§7//move <n> [hướng] [block_để_lại] • //stack <số_bản> [hướng]. Clipboard/undo chỉ lưu trong phiên.");
        player.sendMessage("§7Copy vùng bằng búa + //copy; stick brush để vẽ, cuốc chim preview clipboard, chuột trái xoay/phải đặt; //undo hoàn tác.");
        player.sendMessage("§7Block nhận ID như §fminecraft:stone§7 hoặc block state §fminecraft:oak_log[axis=x]§7. Giới hạn hiện tại: §f"
            + limitLabel() + " §7block/thao tác. Dùng //limit <số|unlimited> để đổi; thao tác không giới hạn vẫn phụ thuộc RAM và chunk đã nạp.");
    }

    private List<String> blockSuggestions(String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT).replace("minecraft:", "");
        return java.util.Arrays.stream(Material.values()).filter(Material::isBlock)
            .map(material -> "minecraft:" + material.getKey().getKey())
            .filter(name -> name.substring("minecraft:".length()).startsWith(normalized))
            .limit(40).toList();
    }

    private void selectPosition(Player player, String command, String[] args) {
        Location point;
        if (command.startsWith("hpos")) {
            Block target = player.getTargetBlockExact(256);
            point = target == null ? null : target.getLocation();
        } else {
            if (args.length == 0) {
                point = player.getLocation().getBlock().getLocation();
            } else {
                int[] xyz = parseCoordinates(args);
                point = new Location(player.getWorld(), xyz[0], xyz[1], xyz[2]);
            }
        }
        if (point == null) { player.sendMessage("§cKhông thấy block đang ngắm trong 256 block."); return; }
        Selection selection = selections.computeIfAbsent(player.getUniqueId(), ignored -> new Selection());
        if (command.endsWith("1")) selection.setFirst(point); else selection.setSecond(point);
        player.sendMessage("§aĐiểm " + (command.endsWith("1") ? "1" : "2") + ": §f" + point.getWorld().getName()
            + " " + point.getBlockX() + " " + point.getBlockY() + " " + point.getBlockZ()
            + " §7(" + selectionText(selection) + ")");
    }

    private void setSelectionShape(Player player, String[] args) {
        if (args.length != 1) throw new IllegalArgumentException("Dùng //sel <cuboid|sphere|ellipsoid>.");
        RegionShape shape = switch (args[0].toLowerCase(Locale.ROOT)) {
            case "cuboid" -> RegionShape.CUBOID;
            case "sphere" -> RegionShape.SPHERE;
            case "ellipsoid" -> RegionShape.ELLIPSOID;
            default -> throw new IllegalArgumentException("Kiểu chọn hiện hỗ trợ cuboid, sphere và ellipsoid.");
        };
        Selection selection = selections.computeIfAbsent(player.getUniqueId(), ignored -> new Selection());
        selection.shape = shape;
        player.sendMessage("§aKiểu vùng chọn: §f" + shape.name().toLowerCase(Locale.ROOT) + "§a. Hai điểm đã chọn được giữ nguyên.");
    }

    private int[] parseCoordinates(String[] args) {
        String[] values = args.length == 1 ? args[0].split(",") : args;
        if (values.length != 3) throw new IllegalArgumentException("Dùng //pos1 x,y,z hoặc //pos2 x y z.");
        try { return new int[]{Integer.parseInt(values[0]), Integer.parseInt(values[1]), Integer.parseInt(values[2])}; }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("Tọa độ phải là số nguyên x,y,z."); }
    }

    private void selectChunk(Player player) {
        int minX = player.getLocation().getBlockX() >> 4 << 4, minZ = player.getLocation().getBlockZ() >> 4 << 4;
        Selection selection = selections.computeIfAbsent(player.getUniqueId(), ignored -> new Selection());
        selection.setFirst(new Location(player.getWorld(), minX, player.getWorld().getMinHeight(), minZ));
        selection.setSecond(new Location(player.getWorld(), minX + 15, player.getWorld().getMaxHeight() - 1, minZ + 15));
        player.sendMessage("§aĐã chọn chunk §f" + (minX >> 4) + ", " + (minZ >> 4) + "§a. §7Kiểm tra giới hạn block trước khi sửa.");
    }

    private void reportSelectionSize(Player player) {
        Selection selection = requireSelection(player, false);
        long x = (long) selection.maxX() - selection.minX() + 1, y = (long) selection.maxY() - selection.minY() + 1;
        long z = (long) selection.maxZ() - selection.minZ() + 1;
        player.sendMessage("§aVùng: §f" + x + " × " + y + " × " + z + " = " + (x * y * z) + " block §7(world "
            + selection.first.getWorld().getName() + ")");
    }

    private Selection requireSelection(Player player, boolean enforceLimit) {
        Selection selection = selections.get(player.getUniqueId());
        if (selection == null || !selection.complete()) throw new IllegalArgumentException("Chọn hai góc bằng búa hoặc //pos1 và //pos2 trước.");
        if (!selection.first.getWorld().equals(player.getWorld())) throw new IllegalArgumentException("Bạn phải ở cùng world với vùng đã chọn.");
        long volume = selection.volume();
        if (enforceLimit && volume > editLimit()) throw new IllegalArgumentException("Vùng có " + volume + " block; giới hạn là " + limitLabel() + ". Dùng //contract hoặc //limit.");
        if (enforceLimit && (selection.minY() < selection.first.getWorld().getMinHeight()
                || selection.maxY() >= selection.first.getWorld().getMaxHeight()
                || !selection.first.getWorld().getWorldBorder().isInside(selection.first)
                || !selection.first.getWorld().getWorldBorder().isInside(selection.second)))
            throw new IllegalArgumentException("Vùng vượt giới hạn chiều cao hoặc world border.");
        if (enforceLimit && !selection.allChunksLoaded()) throw new IllegalArgumentException("Một phần vùng nằm trong chunk chưa nạp; hãy tới gần và nạp toàn bộ vùng trước.");
        return selection;
    }

    private void editSet(Player player, String[] args) {
        editSet(player, args, "//set");
    }

    private void editClear(Player player, String[] args) {
        if (args.length != 0) throw new IllegalArgumentException("Dùng //clear để xóa block trong vùng chọn.");
        editSet(player, new String[]{"air"}, "//clear");
    }

    private void editSet(Player player, String[] args, String operationLabel) {
        if (args.length != 1) throw new IllegalArgumentException(
            "Dùng //set <pattern>, ví dụ //set stone, //set air, //set 0 hoặc //set 35:14.");
        Selection selection = requireSelection(player, true);
        BlockPattern pattern = parsePattern(args[0]);
        Random random = new Random();
        List<BlockChange> changes = new ArrayList<>();
        for (int x = selection.minX(); x <= selection.maxX(); x++) for (int y = selection.minY(); y <= selection.maxY(); y++)
            for (int z = selection.minZ(); z <= selection.maxZ(); z++) {
                if (!selection.contains(x, y, z)) continue;
                Block block = selection.first.getWorld().getBlockAt(x, y, z);
                addChange(player, changes, block, pattern.next(random), true);
            }
        scheduleEdit(player, operationLabel, changes);
    }

    private void editReplace(Player player, String[] args) {
        if (args.length != 2) throw new IllegalArgumentException("Dùng //replace <block_cũ> <block_mới>, ví dụ //replace minecraft:dirt minecraft:stone.");
        Selection selection = requireSelection(player, true);
        BlockMatcher matcher = parseMatcher(args[0]);
        BlockPattern replacement = parsePattern(args[1]);
        Random random = new Random(); List<BlockChange> changes = new ArrayList<>();
        for (int x = selection.minX(); x <= selection.maxX(); x++) for (int y = selection.minY(); y <= selection.maxY(); y++)
            for (int z = selection.minZ(); z <= selection.maxZ(); z++) {
                if (!selection.contains(x, y, z)) continue;
                Block block = selection.first.getWorld().getBlockAt(x, y, z);
                if (matcher.matches(block.getBlockData())) addChange(player, changes, block, replacement.next(random));
            }
        scheduleEdit(player, "//replace", changes);
    }

    private void editShape(Player player, String[] args, Shape shape) {
        if (args.length != 1) throw new IllegalArgumentException("Dùng //" + shape.command + " <block>, ví dụ //" + shape.command + " minecraft:stone.");
        Selection selection = requireSelection(player, true); BlockPattern pattern = parsePattern(args[0]); Random random = new Random();
        List<BlockChange> changes = new ArrayList<>();
        for (int x = selection.minX(); x <= selection.maxX(); x++) for (int y = selection.minY(); y <= selection.maxY(); y++)
            for (int z = selection.minZ(); z <= selection.maxZ(); z++) {
                if (!selection.contains(x, y, z)) continue;
                boolean wall = !selection.contains(x - 1, y, z) || !selection.contains(x + 1, y, z)
                    || !selection.contains(x, y, z - 1) || !selection.contains(x, y, z + 1);
                boolean edge = wall || !selection.contains(x, y - 1, z) || !selection.contains(x, y + 1, z);
                if (shape == Shape.FACES ? edge : wall) addChange(player, changes,
                    selection.first.getWorld().getBlockAt(x, y, z), pattern.next(random));
            }
        scheduleEdit(player, "//" + shape.command, changes);
    }

    private void editOverlay(Player player, String[] args) {
        if (args.length != 1) throw new IllegalArgumentException("Dùng //overlay <block>.");
        Selection selection = requireSelection(player, true); BlockPattern pattern = parsePattern(args[0]); Random random = new Random();
        List<BlockChange> changes = new ArrayList<>();
        for (int x = selection.minX(); x <= selection.maxX(); x++) for (int z = selection.minZ(); z <= selection.maxZ(); z++)
            for (int y = selection.maxY() - 1; y >= selection.minY(); y--) {
                if (!selection.contains(x, y, z)) continue;
                Block floor = selection.first.getWorld().getBlockAt(x, y, z);
                Block above = floor.getRelative(0, 1, 0);
                if (!floor.getType().isAir() && above.getType().isAir()) { addChange(player, changes, above, pattern.next(random)); break; }
            }
        scheduleEdit(player, "//overlay", changes);
    }

    private void editCenter(Player player, String[] args) {
        if (args.length != 1) throw new IllegalArgumentException("Dùng //center <block>.");
        Selection selection = requireSelection(player, true); BlockPattern pattern = parsePattern(args[0]); Random random = new Random();
        int[] xs = centerCoordinates(selection.minX(), selection.maxX()), ys = centerCoordinates(selection.minY(), selection.maxY()), zs = centerCoordinates(selection.minZ(), selection.maxZ());
        List<BlockChange> changes = new ArrayList<>();
        for (int x : xs) for (int y : ys) for (int z : zs) if (selection.contains(x, y, z))
            addChange(player, changes, selection.first.getWorld().getBlockAt(x, y, z), pattern.next(random));
        scheduleEdit(player, "//center", changes);
    }

    private void editSmooth(Player player, String[] args) {
        if (args.length > 1) throw new IllegalArgumentException("Dùng //smooth [số_lần_làm_mịn].");
        Selection selection = requireSelection(player, true);
        int passes = args.length == 0 ? 1 : Math.max(1, Math.min(5, positiveInt(args[0])));
        World world = selection.first.getWorld();
        Map<BlockPosition, BlockData> current = new HashMap<>();
        for (int x = selection.minX(); x <= selection.maxX(); x++) for (int y = selection.minY(); y <= selection.maxY(); y++)
            for (int z = selection.minZ(); z <= selection.maxZ(); z++) if (selection.contains(x, y, z)) {
                Block block = world.getBlockAt(x, y, z);
                if (!(block.getState() instanceof org.bukkit.block.TileState)) current.put(new BlockPosition(x, y, z), block.getBlockData().clone());
            }
        for (int pass = 0; pass < passes; pass++) {
            Map<BlockPosition, BlockData> next = new HashMap<>(current);
            for (Map.Entry<BlockPosition, BlockData> entry : current.entrySet()) {
                BlockPosition point = entry.getKey();
                Map<Material, Integer> counts = new HashMap<>();
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                    BlockPosition neighbor = new BlockPosition(point.x() + dx, point.y() + dy, point.z() + dz);
                    BlockData data = current.get(neighbor);
                    if (data == null && world.isChunkLoaded(neighbor.x() >> 4, neighbor.z() >> 4))
                        data = world.getBlockAt(neighbor.x(), neighbor.y(), neighbor.z()).getBlockData();
                    if (data != null) counts.merge(data.getMaterial(), 1, Integer::sum);
                }
                Material dominant = counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(entry.getValue().getMaterial());
                if (dominant != entry.getValue().getMaterial()) next.put(point, Bukkit.createBlockData(dominant));
            }
            current = next;
        }
        List<BlockChange> changes = new ArrayList<>();
        for (Map.Entry<BlockPosition, BlockData> entry : current.entrySet()) addChange(player, changes,
            world.getBlockAt(entry.getKey().x(), entry.getKey().y(), entry.getKey().z()), entry.getValue());
        scheduleEdit(player, "//smooth", changes, true);
    }

    private void setMask(Player player, String[] args, boolean global) {
        if (args.length > 1) throw new IllegalArgumentException("Dùng //" + (global ? "gmask" : "mask") + " [block].");
        BlockMatcher mask = args.length == 0 || "none".equalsIgnoreCase(args[0]) ? null : parseMatcher(args[0]);
        if (global) globalMask = mask;
        else if (mask == null) playerMasks.remove(player.getUniqueId());
        else playerMasks.put(player.getUniqueId(), mask);
        player.sendMessage(mask == null ? "§eĐã xóa " + (global ? "gmask" : "mask") + "."
            : "§aĐã đặt " + (global ? "gmask" : "mask") + " thành §f" + args[0] + "§a.");
    }

    private void setBrush(Player player, String[] args) {
        if (args.length == 0 || "none".equalsIgnoreCase(args[0])) {
            brushConfigs.remove(player.getUniqueId());
            player.sendMessage("§eĐã tắt brush kiểu hình cầu/làm mịn.");
            return;
        }
        String kind = args[0].toLowerCase(Locale.ROOT);
        if ("sphere".equals(kind)) {
            if (args.length != 3) throw new IllegalArgumentException("Dùng //br sphere <block> <bán_kính>.");
            BlockData data = parseBlockData(args[1]);
            if (!data.getMaterial().isBlock()) throw new IllegalArgumentException("Đây không phải block: " + args[1]);
            int radius = positiveInt(args[2]);
            if (radius > 32) throw new IllegalArgumentException("Bán kính brush tối đa là 32.");
            brushConfigs.put(player.getUniqueId(), new BrushConfig(kind, radius, data.getAsString()));
        } else if ("smooth".equals(kind)) {
            if (args.length != 2) throw new IllegalArgumentException("Dùng //br smooth <bán_kính>.");
            int radius = positiveInt(args[1]);
            if (radius > 16) throw new IllegalArgumentException("Bán kính brush làm mịn tối đa là 16.");
            brushConfigs.put(player.getUniqueId(), new BrushConfig(kind, radius, null));
        } else throw new IllegalArgumentException("Brush hỗ trợ sphere, smooth hoặc none.");
        player.sendMessage("§aĐã đặt brush " + kind + ". Cầm stick và chuột phải lên bề mặt để áp dụng; //undo hoàn tác mỗi lần.");
        giveBrush(player);
    }

    private void generateShape(Player player, String command, String[] args) {
        boolean pyramid = command.contains("pyramid"), cylinder = command.endsWith("cyl");
        if (args.length != (cylinder ? 3 : 2)) throw new IllegalArgumentException(cylinder
            ? "Dùng //" + command + " <block> <bán_kính> <chiều_cao>."
            : "Dùng //" + command + " <block> <bán_kính|kích_thước>.");
        BlockPattern pattern = parsePattern(args[0]); Random random = new Random();
        int radius = positiveInt(args[1]), height = cylinder ? positiveInt(args[2]) : pyramid ? radius : radius * 2 + 1;
        if (radius > 64 || height > 128) throw new IllegalArgumentException("Kích thước hình tối đa là bán kính 64 và chiều cao 128.");
        boolean hollow = command.startsWith("h");
        Location base = player.getLocation().getBlock().getLocation(); World world = player.getWorld();
        int cx = base.getBlockX(), cy = base.getBlockY(), cz = base.getBlockZ();
        Map<BlockPosition, BlockData> desired = new LinkedHashMap<>();
        for (int dy = 0; dy < height; dy++) {
            int layerRadius = pyramid ? Math.max(0, radius - 1 - dy) : radius;
            for (int dx = -layerRadius; dx <= layerRadius; dx++) for (int dz = -layerRadius; dz <= layerRadius; dz++) {
                boolean inside = pyramid ? Math.abs(dx) <= layerRadius && Math.abs(dz) <= layerRadius
                    : cylinder ? dx * dx + dz * dz <= radius * radius
                    : dx * dx + dy * dy + dz * dz <= radius * radius;
                if (!inside) continue;
                boolean edge = pyramid ? Math.abs(dx) == layerRadius || Math.abs(dz) == layerRadius
                    : cylinder ? dx * dx + dz * dz >= (radius - 0.5) * (radius - 0.5)
                        || dy == 0 || dy == height - 1
                    : dx * dx + dy * dy + dz * dz >= (radius - 0.5) * (radius - 0.5);
                if (hollow && !edge) continue;
                int x = cx + dx, y = cy + dy, z = cz + dz;
                if (y < world.getMinHeight() || y >= world.getMaxHeight() || !world.isChunkLoaded(x >> 4, z >> 4))
                    throw new IllegalArgumentException("Hình đi qua chunk chưa nạp hoặc vượt chiều cao world.");
                if (!world.getWorldBorder().isInside(new Location(world, x, y, z))) throw new IllegalArgumentException("Hình vượt world border.");
                desired.put(new BlockPosition(x, y, z), pattern.next(random));
                if (desired.size() > editLimit()) throw new IllegalArgumentException("Hình vượt giới hạn " + limitLabel() + " block.");
            }
        }
        List<BlockChange> changes = new ArrayList<>();
        for (Map.Entry<BlockPosition, BlockData> entry : desired.entrySet()) {
            BlockPosition point = entry.getKey(); addChange(player, changes, world.getBlockAt(point.x(), point.y(), point.z()), entry.getValue());
        }
        scheduleEdit(player, "//" + command, changes, true);
    }

    private void teleportUnstuck(Player player) {
        Location origin = player.getLocation();
        for (int y = origin.getBlockY(); y < player.getWorld().getMaxHeight() - 1; y++) {
            Block feet = player.getWorld().getBlockAt(origin.getBlockX(), y, origin.getBlockZ());
            if (feet.isPassable() && feet.getRelative(BlockFace.UP).isPassable()) {
                player.teleport(new Location(player.getWorld(), origin.getX(), y, origin.getZ(), origin.getYaw(), origin.getPitch()));
                return;
            }
        }
        player.sendMessage("§cKhông tìm được vị trí trống phía trên.");
    }

    private void jumpToTarget(Player player) {
        Block target = player.getTargetBlockExact(256);
        if (target == null) { player.sendMessage("§cKhông thấy block để nhảy tới trong 256 block."); return; }
        Location destination = target.getLocation().add(0.5, 1, 0.5);
        while (destination.getBlockY() < player.getWorld().getMaxHeight() - 1
                && (!destination.getBlock().isPassable() || !destination.clone().add(0, 1, 0).getBlock().isPassable())) destination.add(0, 1, 0);
        if (destination.getBlockY() >= player.getWorld().getMaxHeight() - 1) { player.sendMessage("§cKhông tìm được chỗ đứng trên mục tiêu."); return; }
        destination.setYaw(player.getLocation().getYaw()); destination.setPitch(player.getLocation().getPitch());
        player.teleport(destination);
    }

    private void teleportThrough(Player player) {
        World world = player.getWorld(); Location origin = player.getEyeLocation();
        org.bukkit.util.Vector direction = origin.getDirection().normalize();
        for (double distance = 0.5; distance <= 6.0; distance += 0.25) {
            Location candidate = origin.clone().add(direction.clone().multiply(distance));
            Block feet = world.getBlockAt(candidate.getBlockX(), candidate.getBlockY(), candidate.getBlockZ());
            Block head = feet.getRelative(BlockFace.UP);
            if (!feet.isPassable() || !head.isPassable()) continue;
            if (!world.isChunkLoaded(feet.getX() >> 4, feet.getZ() >> 4)) break;
            candidate.setY(feet.getY()); candidate.setX(feet.getX() + 0.5); candidate.setZ(feet.getZ() + 0.5);
            player.teleport(candidate); return;
        }
        player.sendMessage("§cKhông tìm được lối xuyên tường trong 6 block phía trước.");
    }

    private void generateForest(Player player, String[] args) {
        if (args.length != 2) throw new IllegalArgumentException("Dùng //forest <oak|spruce|birch|jungle|acacia|dark_oak|swamp> <mật_độ 1-100>.");
        Selection selection = requireSelection(player, true);
        String treeName = args[0].toLowerCase(Locale.ROOT);
        org.bukkit.TreeType type = switch (treeName) {
            case "oak" -> org.bukkit.TreeType.TREE;
            case "spruce" -> org.bukkit.TreeType.REDWOOD;
            case "birch" -> org.bukkit.TreeType.BIRCH;
            case "jungle" -> org.bukkit.TreeType.SMALL_JUNGLE;
            case "acacia" -> org.bukkit.TreeType.ACACIA;
            case "dark_oak" -> org.bukkit.TreeType.DARK_OAK;
            case "swamp" -> org.bukkit.TreeType.SWAMP;
            default -> throw new IllegalArgumentException("Loại cây hỗ trợ: oak, spruce, birch, jungle, acacia, dark_oak, swamp.");
        };
        int density = positiveInt(args[1]);
        if (density > 100) throw new IllegalArgumentException("Mật độ phải nằm trong khoảng 1-100.");
        World world = selection.first.getWorld(); Random random = new Random(); List<BlockPosition> roots = new ArrayList<>();
        for (int x = selection.minX(); x <= selection.maxX(); x++) for (int z = selection.minZ(); z <= selection.maxZ(); z++) {
            if (random.nextInt(100) >= density) continue;
            for (int y = selection.maxY(); y >= selection.minY(); y--) {
                Block ground = world.getBlockAt(x, y, z);
                if (ground.getType().isAir() || !ground.getType().isSolid()) continue;
                if (y + 1 < world.getMaxHeight() && world.getBlockAt(x, y + 1, z).isPassable()) roots.add(new BlockPosition(x, y + 1, z));
                break;
            }
            if (roots.size() >= 128) break;
        }
        if (roots.isEmpty()) throw new IllegalArgumentException("Không tìm thấy mặt đất phù hợp trong vùng chọn.");
        Map<BlockPosition, BeforeTreeBlock> before = new LinkedHashMap<>();
        for (BlockPosition root : roots) for (int x = root.x() - 4; x <= root.x() + 4; x++)
            for (int y = root.y(); y < Math.min(world.getMaxHeight(), root.y() + 20); y++)
                for (int z = root.z() - 4; z <= root.z() + 4; z++) {
                    validateEditTarget(world, x, y, z);
                    Block block = world.getBlockAt(x, y, z); BlockState state = block.getState();
                    before.putIfAbsent(new BlockPosition(x, y, z), new BeforeTreeBlock(block.getBlockData().getAsString(),
                        state instanceof org.bukkit.block.TileState ? state.copy() : null));
                    if (before.size() > editLimit()) throw new IllegalArgumentException("Vùng đệm cây vượt giới hạn " + limitLabel() + " block.");
                }
        try {
            for (BlockPosition root : roots) world.generateTree(new Location(world, root.x(), root.y(), root.z()), type);
            List<BlockChange> changes = new ArrayList<>();
            for (Map.Entry<BlockPosition, BeforeTreeBlock> entry : before.entrySet()) {
                BlockPosition p = entry.getKey(); Block block = world.getBlockAt(p.x(), p.y(), p.z());
                String after = block.getBlockData().getAsString();
                if (after.equals(entry.getValue().data())) continue;
                BlockState afterState = block.getState() instanceof org.bukkit.block.TileState ? block.getState().copy() : null;
                Map<String, Object> beforeNbt = entry.getValue().state() == null ? Map.of() : NativeBlockEntityNbt.capture(entry.getValue().state());
                Map<String, Object> afterNbt = afterState == null ? Map.of() : NativeBlockEntityNbt.capture(afterState);
                changes.add(new BlockChange(block.getLocation(), entry.getValue().data(), after,
                    entry.getValue().state(), afterState, afterNbt));
            }
            if (changes.size() > editLimit()) throw new IllegalArgumentException("Rừng tạo quá nhiều block; dùng mật độ thấp hơn.");
            if (changes.isEmpty()) { player.sendMessage("§eKhông cây nào mọc được tại vùng này."); return; }
            EditOperation operation = new EditOperation("//forest", changes, true);
            refreshLinkedConnections(changes); captureActualAfterStates(operation);
            Deque<EditOperation> history = undoHistory.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>());
            history.push(operation); while (history.size() > historyLimit()) history.removeLast(); redoHistory.remove(player.getUniqueId());
            player.sendMessage("§aĐã tạo " + roots.size() + " cây; có thể hoàn tác bằng //undo.");
        } catch (RuntimeException exception) {
            for (Map.Entry<BlockPosition, BeforeTreeBlock> entry : before.entrySet()) {
                BlockPosition p = entry.getKey(); Block block = world.getBlockAt(p.x(), p.y(), p.z());
                block.setBlockData(Bukkit.createBlockData(entry.getValue().data()), false);
                if (entry.getValue().state() != null) entry.getValue().state().update(true, false);
            }
            throw new IllegalArgumentException("Không thể tạo rừng an toàn; các block trong vùng đệm đã được phục hồi: " + exception.getMessage());
        }
    }

    private void editPlane(Player player, String command, String[] args) {
        if (args.length != 2)
            throw new IllegalArgumentException("Dùng //" + command + " <bán_kính> <block>, ví dụ //"
                + command + " 8 minecraft:stone.");
        Selection selection = selections.get(player.getUniqueId());
        if (selection == null || selection.first == null)
            throw new IllegalArgumentException("Chọn tâm bằng pos1 trước; có thể dùng búa hoặc //pos1.");
        Location center = selection.first;
        World world = center.getWorld();
        if (!world.equals(player.getWorld()))
            throw new IllegalArgumentException("Bạn phải ở cùng world với điểm pos1.");
        int radius = positiveInt(args[0]);
        long side = 2L * radius + 1L;
        long upperBound;
        try {
            upperBound = "square".equals(command) ? Math.multiplyExact(side, side) : 0L;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Bán kính vượt mức có thể xử lý an toàn.");
        }
        if (upperBound > editLimit())
            throw new IllegalArgumentException("Mặt phẳng cần tối đa " + upperBound
                + " block; giới hạn hiện tại là " + limitLabel() + ". Dùng //limit <số|unlimited>.");
        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();
        long minXLong = (long) centerX - radius;
        long maxXLong = (long) centerX + radius;
        long minZLong = (long) centerZ - radius;
        long maxZLong = (long) centerZ + radius;
        if (centerY < world.getMinHeight() || centerY >= world.getMaxHeight()
                || minXLong < Integer.MIN_VALUE || maxXLong > Integer.MAX_VALUE
                || minZLong < Integer.MIN_VALUE || maxZLong > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Mặt phẳng vượt giới hạn tọa độ của world.");
        int minX = (int) minXLong, maxX = (int) maxXLong;
        int minZ = (int) minZLong, maxZ = (int) maxZLong;
        Location cornerA = new Location(world, minX, centerY, minZ);
        Location cornerB = new Location(world, maxX, centerY, maxZ);
        if (!world.getWorldBorder().isInside(cornerA) || !world.getWorldBorder().isInside(cornerB))
            throw new IllegalArgumentException("Mặt phẳng vượt world border.");
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                if (!world.isChunkLoaded(chunkX, chunkZ))
                    throw new IllegalArgumentException("Một phần mặt phẳng nằm trong chunk chưa nạp; hãy nạp toàn bộ vùng trước.");
            }
        }
        BlockPattern pattern = parsePattern(args[1]);
        Random random = new Random();
        List<BlockChange> changes = new ArrayList<>();
        if ("round".equals(command)) {
            Set<BlockPosition> circumference = rasterizeCircle(centerX, centerY, centerZ, radius);
            if (circumference.size() > editLimit())
                throw new IllegalArgumentException("Vòng tròn cần " + circumference.size()
                    + " block; giới hạn hiện tại là " + limitLabel() + ". Dùng //limit <số|unlimited>.");
            for (BlockPosition point : circumference) {
                addChange(player, changes, world.getBlockAt(point.x(), point.y(), point.z()), pattern.next(random));
            }
        } else {
            long radiusSquared = (long) radius * radius;
            for (long xLong = minX; xLong <= maxX; xLong++) {
                int x = (int) xLong;
                for (long zLong = minZ; zLong <= maxZ; zLong++) {
                    int z = (int) zLong;
                    long dx = (long) x - centerX;
                    long dz = (long) z - centerZ;
                    if ("circle".equals(command) && dx * dx + dz * dz > radiusSquared) continue;
                    addChange(player, changes, world.getBlockAt(x, centerY, z), pattern.next(random));
                }
            }
        }
        scheduleEdit(player, "//" + command, changes);
    }

    private Set<BlockPosition> rasterizeCircle(int centerX, int centerY, int centerZ, int radius) {
        Set<BlockPosition> points = new HashSet<>();
        long x = radius;
        long z = 0;
        long error = 1L - radius;
        while (x >= z) {
            addCircleOctants(points, centerX, centerY, centerZ, x, z);
            z++;
            if (error < 0) {
                error += 2L * z + 1L;
            } else {
                x--;
                error += 2L * (z - x) + 1L;
            }
        }
        return points;
    }

    private void addCircleOctants(Set<BlockPosition> points, int centerX, int centerY, int centerZ, long x, long z) {
        points.add(new BlockPosition(centerX + (int) x, centerY, centerZ + (int) z));
        points.add(new BlockPosition(centerX + (int) z, centerY, centerZ + (int) x));
        points.add(new BlockPosition(centerX - (int) z, centerY, centerZ + (int) x));
        points.add(new BlockPosition(centerX - (int) x, centerY, centerZ + (int) z));
        points.add(new BlockPosition(centerX - (int) x, centerY, centerZ - (int) z));
        points.add(new BlockPosition(centerX - (int) z, centerY, centerZ - (int) x));
        points.add(new BlockPosition(centerX + (int) z, centerY, centerZ - (int) x));
        points.add(new BlockPosition(centerX + (int) x, centerY, centerZ - (int) z));
    }

    private int[] centerCoordinates(int min, int max) {
        int low = min + (max - min) / 2;
        return (max - min + 1) % 2 == 0 ? new int[]{low, low + 1} : new int[]{low};
    }

    private void countBlocks(Player player, String[] args) {
        if (args.length != 1) throw new IllegalArgumentException("Dùng //count <block>.");
        Selection selection = requireSelection(player, true); BlockMatcher matcher = parseMatcher(args[0]); long count = 0;
        for (int x = selection.minX(); x <= selection.maxX(); x++) for (int y = selection.minY(); y <= selection.maxY(); y++)
            for (int z = selection.minZ(); z <= selection.maxZ(); z++) if (selection.contains(x, y, z)
                    && matcher.matches(selection.first.getWorld().getBlockAt(x, y, z).getBlockData())) count++;
        player.sendMessage("§aTìm thấy §f" + count + "§a block khớp §f" + args[0] + "§a.");
    }

    private void adjustSelection(Player player, String[] args, boolean expand) {
        if (args.length < 1 || args.length > 2) throw new IllegalArgumentException("Dùng //" + (expand ? "expand" : "contract") + " <số> [hướng].");
        int amount = positiveInt(args[0]); Selection selection = requireSelection(player, false);
        if (amount > editLimit()) throw new IllegalArgumentException("Khoảng mở rộng tối đa một lần là giới hạn block hiện tại: " + limitLabel() + ".");
        String direction = args.length == 2 ? args[1].toLowerCase(Locale.ROOT) : player.getFacing().name().toLowerCase(Locale.ROOT);
        if ("vert".equals(direction) && expand) {
            selection.first.setY(selection.first.getWorld().getMinHeight());
            selection.second.setY(selection.first.getWorld().getMaxHeight() - 1);
        } else {
            int dx = 0, dy = 0, dz = 0;
            switch (direction) {
                case "up" -> dy = 1; case "down" -> dy = -1;
                case "north" -> dz = -1; case "south" -> dz = 1;
                case "east" -> dx = 1; case "west" -> dx = -1;
                default -> throw new IllegalArgumentException("Hướng phải là up/down/north/south/east/west/vert.");
            }
            translateBoundary(selection, expand, amount, dx, dy, dz);
        }
        player.sendMessage("§aVùng đã " + (expand ? "mở rộng" : "thu hẹp") + ". §7Dùng //size để xem kích thước.");
    }

    private void translateBoundary(Selection selection, boolean expand, int amount, int dx, int dy, int dz) {
        int ax = selection.first.getBlockX(), ay = selection.first.getBlockY(), az = selection.first.getBlockZ();
        int bx = selection.second.getBlockX(), by = selection.second.getBlockY(), bz = selection.second.getBlockZ();
        int min = dx != 0 ? selection.minX() : dy != 0 ? selection.minY() : selection.minZ();
        int max = dx != 0 ? selection.maxX() : dy != 0 ? selection.maxY() : selection.maxZ();
        int direction = dx + dy + dz;
        int step = direction * amount * (expand ? 1 : -1);
        int boundary = direction > 0 ? max : min;
        if (!expand && (direction > 0 ? boundary + step < min : boundary + step > max))
            throw new IllegalArgumentException("Thu hẹp quá mức sẽ làm hai góc vượt nhau.");
        if (dx != 0) {
            boolean positive = dx > 0;
            Location target = (positive ? ax > bx : ax < bx) ? selection.first : selection.second;
            target.add(dx * amount * (expand ? 1 : -1), 0, 0);
        } else if (dy != 0) {
            boolean positive = dy > 0;
            Location target = (positive ? ay > by : ay < by) ? selection.first : selection.second;
            target.add(0, dy * amount * (expand ? 1 : -1), 0);
        } else {
            boolean positive = dz > 0;
            Location target = (positive ? az > bz : az < bz) ? selection.first : selection.second;
            target.add(0, 0, dz * amount * (expand ? 1 : -1));
        }
    }

    private void shiftSelection(Player player, String[] args) {
        if (args.length != 2) throw new IllegalArgumentException("Dùng //shift <số> <up|down|north|south|east|west>.");
        int amount = Integer.parseInt(args[0]); Selection selection = requireSelection(player, false);
        if (Math.abs((long) amount) > editLimit()) throw new IllegalArgumentException("Dịch chuyển tối đa một lần là " + limitLabel() + " block.");
        int[] vector = directionVector(args[1]); selection.first.add(vector[0] * amount, vector[1] * amount, vector[2] * amount);
        selection.second.add(vector[0] * amount, vector[1] * amount, vector[2] * amount);
        player.sendMessage("§aĐã dịch chuyển vùng chọn " + amount + " block.");
    }

    private void moveSelection(Player player, String[] args) {
        if (args.length < 1 || args.length > 3) throw new IllegalArgumentException("Dùng //move <số> [hướng] [block_để_lại].");
        int amount = positiveInt(args[0]);
        if (amount > editLimit()) throw new IllegalArgumentException("Khoảng move vượt giới hạn block hiện tại: " + limitLabel() + ".");
        int[] direction = args.length >= 2 ? directionVector(args[1]) : directionVector(player.getFacing().name());
        BlockData leave;
        try { leave = Bukkit.createBlockData(args.length == 3 ? args[2] : "minecraft:air"); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Block để lại không hợp lệ."); }
        Selection selection = requireSelection(player, true); World world = selection.first.getWorld();
        Map<BlockPosition, String> source = new LinkedHashMap<>();
        for (int x = selection.minX(); x <= selection.maxX(); x++) for (int y = selection.minY(); y <= selection.maxY(); y++)
            for (int z = selection.minZ(); z <= selection.maxZ(); z++) {
                if (!selection.contains(x, y, z)) continue;
                Block block = world.getBlockAt(x, y, z);
                if (block.getState() instanceof org.bukkit.block.TileState) throw new IllegalArgumentException("Move vùng có rương/bảng hiệu bị từ chối để tránh mất dữ liệu.");
                source.put(new BlockPosition(x, y, z), block.getBlockData().getAsString());
            }
        Map<BlockPosition, String> desired = new LinkedHashMap<>();
        source.keySet().forEach(position -> desired.put(position, leave.getAsString()));
        for (Map.Entry<BlockPosition, String> entry : source.entrySet()) {
            BlockPosition target = entry.getKey().offset(direction[0] * amount, direction[1] * amount, direction[2] * amount);
            validateEditTarget(world, target.x, target.y, target.z);
            if (world.getBlockAt(target.x, target.y, target.z).getState() instanceof org.bukkit.block.TileState)
                throw new IllegalArgumentException("Điểm đến có rương/bảng hiệu; không sửa để tránh mất dữ liệu.");
            desired.put(target, entry.getValue());
        }
        List<BlockChange> changes = new ArrayList<>();
        for (Map.Entry<BlockPosition, String> entry : desired.entrySet()) addChange(player, changes,
            world.getBlockAt(entry.getKey().x, entry.getKey().y, entry.getKey().z), Bukkit.createBlockData(entry.getValue()));
        scheduleEdit(player, "//move", changes);
    }

    private void stackSelection(Player player, String[] args) {
        if (args.length < 1 || args.length > 2) throw new IllegalArgumentException("Dùng //stack <số_bản> [hướng].");
        int copies = positiveInt(args[0]); if (copies > 1000) throw new IllegalArgumentException("//stack hỗ trợ tối đa 1000 bản mỗi lần.");
        Selection selection = requireSelection(player, true); World world = selection.first.getWorld();
        int[] vector = args.length == 2 ? directionVector(args[1]) : directionVector(player.getFacing().name());
        int depth = vector[0] != 0 ? selection.maxX() - selection.minX() + 1
            : vector[1] != 0 ? selection.maxY() - selection.minY() + 1 : selection.maxZ() - selection.minZ() + 1;
        List<Map.Entry<BlockPosition, String>> source = new ArrayList<>();
        for (int x = selection.minX(); x <= selection.maxX(); x++) for (int y = selection.minY(); y <= selection.maxY(); y++)
            for (int z = selection.minZ(); z <= selection.maxZ(); z++) {
                if (!selection.contains(x, y, z)) continue;
                Block block = world.getBlockAt(x, y, z);
                if (block.getState() instanceof org.bukkit.block.TileState) throw new IllegalArgumentException("Stack vùng có rương/bảng hiệu bị từ chối để tránh mất dữ liệu.");
                source.add(Map.entry(new BlockPosition(x, y, z), block.getBlockData().getAsString()));
            }
        long total = (long) source.size() * copies;
        if (total > editLimit()) throw new IllegalArgumentException("Stack cần " + total + " block; giới hạn hiện tại " + limitLabel() + ".");
        Map<BlockPosition, String> desired = new LinkedHashMap<>();
        for (int copy = 1; copy <= copies; copy++) for (Map.Entry<BlockPosition, String> entry : source) {
            BlockPosition target = entry.getKey().offset(vector[0] * depth * copy, vector[1] * depth * copy, vector[2] * depth * copy);
            validateEditTarget(world, target.x, target.y, target.z);
            if (world.getBlockAt(target.x, target.y, target.z).getState() instanceof org.bukkit.block.TileState)
                throw new IllegalArgumentException("Điểm stack có rương/bảng hiệu; không sửa để tránh mất dữ liệu.");
            desired.put(target, entry.getValue());
        }
        List<BlockChange> changes = new ArrayList<>();
        for (Map.Entry<BlockPosition, String> entry : desired.entrySet()) addChange(player, changes,
            world.getBlockAt(entry.getKey().x, entry.getKey().y, entry.getKey().z), Bukkit.createBlockData(entry.getValue()));
        scheduleEdit(player, "//stack", changes);
    }

    private void validateEditTarget(World world, int x, int y, int z) {
        Location target = new Location(world, x, y, z);
        if (y < world.getMinHeight() || y >= world.getMaxHeight() || !world.getWorldBorder().isInside(target)
                || !world.isChunkLoaded(x >> 4, z >> 4))
            throw new IllegalArgumentException("Vị trí đích vượt chiều cao/world border hoặc chunk chưa nạp; không đổi block nào.");
    }

    private int positiveInt(String value) {
        try { int parsed = Integer.parseInt(value); if (parsed < 1) throw new NumberFormatException(); return parsed; }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("Số block phải là số nguyên dương."); }
    }

    private long positiveLong(String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 1) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Giới hạn phải là số nguyên dương hoặc unlimited.");
        }
    }

    private static List<String> filter(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }

    private int[] directionVector(String direction) {
        return switch (direction.toLowerCase(Locale.ROOT)) {
            case "up" -> new int[]{0, 1, 0}; case "down" -> new int[]{0, -1, 0};
            case "north" -> new int[]{0, 0, -1}; case "south" -> new int[]{0, 0, 1};
            case "east" -> new int[]{1, 0, 0}; case "west" -> new int[]{-1, 0, 0};
            default -> throw new IllegalArgumentException("Hướng phải là up/down/north/south/east/west.");
        };
    }

    private void copySelection(Player player) {
        Selection selection = requireSelection(player, true);
        Location origin = player.getLocation().getBlock().getLocation();
        List<ClipboardBlock> blocks = new ArrayList<>();
        for (int x = selection.minX(); x <= selection.maxX(); x++) for (int y = selection.minY(); y <= selection.maxY(); y++)
            for (int z = selection.minZ(); z <= selection.maxZ(); z++) {
                if (!selection.contains(x, y, z)) continue;
                Block block = selection.first.getWorld().getBlockAt(x, y, z);
                BlockState tileSnapshot = block.getState() instanceof org.bukkit.block.TileState ? block.getState().copy() : null;
                Map<String, Object> tileData = tileSnapshot == null ? Map.of() : NativeBlockEntityNbt.capture(tileSnapshot);
                blocks.add(new ClipboardBlock(x - origin.getBlockX(), y - origin.getBlockY(), z - origin.getBlockZ(),
                    block.getBlockData().getAsString(), tileSnapshot, tileData));
            }
        clipboards.put(player.getUniqueId(), new Clipboard(List.copyOf(blocks), selection.maxX() - selection.minX() + 1,
            selection.maxY() - selection.minY() + 1, selection.maxZ() - selection.minZ() + 1, origin,
            selection.minX() - origin.getBlockX(), selection.minY() - origin.getBlockY(), selection.minZ() - origin.getBlockZ()));
        player.sendMessage("§aĐã copy §f" + blocks.size() + "§a block theo vị trí đứng làm gốc.");
        plugin.getLogger().info("[WorldRegion] clipboard-copy player=" + player.getName() + ", blocks=" + blocks.size()
            + ", origin=" + origin.getWorld().getName() + ":" + origin.getBlockX() + "," + origin.getBlockY() + "," + origin.getBlockZ());
    }

    private void schematicCommand(Player player, String[] args) {
        if (args.length < 1) throw new IllegalArgumentException("Dùng //schem save hoặc //schem load <tên>.");
        if ("save".equalsIgnoreCase(args[0])) {
            if (args.length != 1) throw new IllegalArgumentException("Dùng //schem save rồi nhập tên/đường dẫn trong chat.");
            copySelection(player);
            pendingSchemaSaves.put(player.getUniqueId(), clipboards.get(player.getUniqueId()));
            player.sendMessage("§eNhập tên file hoặc đường dẫn trong thư mục custom-schemas, ví dụ §fhouse/medieval.schem§e. §7Gõ !cancel để hủy.");
            return;
        }
        if ("load".equalsIgnoreCase(args[0])) {
            if (args.length != 2) throw new IllegalArgumentException("Dùng //schem load <tên/đường_dẫn>.");
            loadCustomSchematic(player, args[1]);
            return;
        }
        throw new IllegalArgumentException("Dùng //schem save hoặc //schem load <tên>.");
    }

    private Path customSchemaRoot() {
        return new File(plugin.getDataFolder(), "schemas/custom-schemas").toPath().toAbsolutePath().normalize();
    }

    private Path customSchemaPath(String rawName) {
        String name = rawName.trim().replace('\\', '/');
        if (name.isBlank() || name.startsWith("/") || name.matches("^[A-Za-z]:.*"))
            throw new IllegalArgumentException("Tên schematic không được là đường dẫn tuyệt đối.");
        String extension = name.toLowerCase(Locale.ROOT);
        if (!extension.endsWith(".schem") && !extension.endsWith(".litematic")) name += ".schem";
        String[] segments = name.split("/", -1);
        for (String segment : segments) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")
                    || !segment.matches("[\\p{L}\\p{N} _.-]{1,80}"))
                throw new IllegalArgumentException("Đường dẫn chỉ nhận thư mục/tên file hợp lệ; không dùng .. hoặc ký tự đặc biệt.");
        }
        Path root = customSchemaRoot();
        Path target = root.resolve(name).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("Đường dẫn schematic nằm ngoài custom-schemas.");
        return target;
    }

    private void saveClipboardSchema(Player player, Clipboard clipboard, String rawName) {
        Path target = customSchemaPath(rawName);
        if (!target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".schem"))
            throw new IllegalArgumentException("Chỉ lưu schema mới với phần mở rộng .schem.");
        if (Files.exists(target)) {
            throw new IllegalArgumentException("File đã tồn tại; chọn tên khác để tránh ghi đè.");
        }
        int minX = clipboard.minOffsetX(), minY = clipboard.minOffsetY(), minZ = clipboard.minOffsetZ();
        Map<BlockPosition, ClipboardBlock> selected = new HashMap<>();
        for (ClipboardBlock block : clipboard.blocks()) selected.put(new BlockPosition(block.x(), block.y(), block.z()), block);
        List<SpongeSchematicWriter.Block> blocks = new ArrayList<>(clipboard.sizeX() * clipboard.sizeY() * clipboard.sizeZ());
        for (int y = 0; y < clipboard.sizeY(); y++) for (int z = 0; z < clipboard.sizeZ(); z++)
            for (int x = 0; x < clipboard.sizeX(); x++) {
                ClipboardBlock source = selected.get(new BlockPosition(minX + x, minY + y, minZ + z));
                blocks.add(new SpongeSchematicWriter.Block(x, y, z,
                    source == null ? "minecraft:air" : source.blockData(), source == null ? Map.of() : source.blockEntityData()));
            }
        try {
            Files.createDirectories(target.getParent());
            SpongeSchematicWriter.write(target, target.getFileName().toString(), clipboard.sizeX(), clipboard.sizeY(),
                clipboard.sizeZ(), blocks);
            player.sendMessage("§aĐã lưu schematic vào §f" + customSchemaRoot().relativize(target).toString().replace(File.separatorChar, '/') + "§a.");
            plugin.getLogger().info("[WorldRegion] schematic-save player=" + player.getName() + ", file=" + target);
        } catch (IOException | RuntimeException exception) {
            player.sendMessage("§cKhông thể lưu schematic: " + exception.getMessage());
            plugin.getLogger().log(Level.WARNING, "[WorldRegion] Could not save schematic " + target, exception);
        }
    }

    private void loadCustomSchematic(Player player, String rawName) {
        Path path = customSchemaPath(rawName);
        Path root = customSchemaRoot();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) throw new IllegalArgumentException("Không tìm thấy schematic trong schemas/custom-schemas.");
        try {
            List<ClipboardBlock> blocks = new ArrayList<>();
            int width, height, length;
            String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
            int maximum = (int) Math.min(Integer.MAX_VALUE, editLimit());
            if (fileName.endsWith(".litematic")) {
                LitematicReader.Schematic schematic = LitematicReader.read(path, maximum);
                width = schematic.width(); height = schematic.height(); length = schematic.length();
                Map<BlockPosition, String> nonAir = new HashMap<>();
                Map<BlockPosition, Map<String, Object>> entities = new HashMap<>();
                for (LitematicReader.SchematicBlock block : schematic.blocks())
                    nonAir.put(new BlockPosition(block.x(), block.y(), block.z()), block.blockData());
                for (LitematicReader.BlockEntity entity : schematic.blockEntities())
                    entities.put(new BlockPosition(entity.x(), entity.y(), entity.z()), entity.data());
                for (int y = 0; y < height; y++) for (int z = 0; z < length; z++) for (int x = 0; x < width; x++)
                    blocks.add(new ClipboardBlock(x, y, z, nonAir.getOrDefault(new BlockPosition(x, y, z), "minecraft:air"), null,
                        entities.getOrDefault(new BlockPosition(x, y, z), Map.of())));
            } else {
                SpongeSchematicReader.Schematic schematic = SpongeSchematicReader.read(path, maximum);
                width = schematic.width(); height = schematic.height(); length = schematic.length();
                Map<BlockPosition, Map<String, Object>> entities = new HashMap<>();
                for (SpongeSchematicReader.BlockEntity entity : schematic.blockEntities())
                    entities.put(new BlockPosition(entity.x(), entity.y(), entity.z()), entity.data());
                int stateIndex = 0;
                for (int y = 0; y < height; y++) for (int z = 0; z < length; z++) for (int x = 0; x < width; x++) {
                    String data = Bukkit.createBlockData(schematic.blockStates().get(stateIndex++)).getAsString();
                    Map<String, Object> nbt = entities.getOrDefault(new BlockPosition(x, y, z), Map.of());
                    blocks.add(new ClipboardBlock(x, y, z, data, null, nbt));
                }
            }
            if ((long) width * height * length > editLimit()) throw new IllegalArgumentException("Schematic vượt giới hạn " + limitLabel() + " block.");
            Location origin = player.getLocation().getBlock().getLocation();
            clipboards.put(player.getUniqueId(), new Clipboard(List.copyOf(blocks), width, height, length, origin, 0, 0, 0));
            pastePreviews.remove(player.getUniqueId());
            player.sendMessage("§aĐã tải schematic §f" + root.relativize(path).toString().replace(File.separatorChar, '/')
                + "§a vào clipboard (" + blocks.size() + " block, tính cả air).");
            plugin.getLogger().info("[WorldRegion] schematic-load player=" + player.getName() + ", file=" + path + ", blocks=" + blocks.size());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Không thể đọc schematic: " + exception.getMessage());
        }
    }

    private void cutSelection(Player player, String[] args) {
        if (args.length > 1) throw new IllegalArgumentException("Dùng //cut [block_để_thay], mặc định là air.");
        copySelection(player);
        editSet(player, new String[]{args.length == 0 ? "minecraft:air" : args[0]});
    }

    private void pasteClipboard(Player player, String[] args) {
        Clipboard clipboard = clipboards.get(player.getUniqueId());
        if (clipboard == null) throw new IllegalArgumentException("Clipboard trống. Chọn vùng rồi dùng //copy hoặc //cut.");
        boolean ignoreAir = false, original = false;
        for (String arg : args) {
            if ("-a".equalsIgnoreCase(arg)) ignoreAir = true;
            else if ("-o".equalsIgnoreCase(arg)) original = true;
            else throw new IllegalArgumentException("Tùy chọn paste hỗ trợ -a (bỏ air) và -o (tọa độ gốc).");
        }
        Location anchor = original ? clipboard.origin : player.getLocation().getBlock().getLocation();
        if (anchor.getWorld() != null && !anchor.getWorld().equals(player.getWorld())) anchor = player.getLocation().getBlock().getLocation();
        List<BlockChange> changes = new ArrayList<>();
        for (ClipboardBlock saved : clipboard.blocks) {
            BlockData data = Bukkit.createBlockData(saved.blockData);
            if (ignoreAir && data.getMaterial().isAir()) continue;
            int x = anchor.getBlockX() + saved.x, y = anchor.getBlockY() + saved.y, z = anchor.getBlockZ() + saved.z;
            World world = player.getWorld();
            if (y < world.getMinHeight() || y >= world.getMaxHeight() || !world.getWorldBorder().isInside(new Location(world, x, y, z))
                    || !world.isChunkLoaded(x >> 4, z >> 4)) throw new IllegalArgumentException("Paste vượt chiều cao/world border hoặc vào chunk chưa nạp; không đổi block nào.");
            Block target = world.getBlockAt(x, y, z);
            BlockChange change = createClipboardChange(player, target, data, saved.blockState(), saved.blockEntityData());
            if (change != null) changes.add(change);
        }
        if (changes.size() > editLimit()) throw new IllegalArgumentException("Paste vượt giới hạn " + limitLabel() + " block.");
        scheduleEdit(player, "//paste", changes);
    }

    private void applyClipboardBrush(Player player, Clipboard clipboard, Block target) {
        if (activeEdits.contains(player.getUniqueId())) {
            player.sendMessage("§eThao tác vùng trước vẫn đang chạy.");
            return;
        }
        World world = player.getWorld();
        Location anchor = target.getLocation();
        List<ClipboardBlock> pattern = clipboard.blocks().stream()
            .filter(block -> !Bukkit.createBlockData(block.blockData()).getMaterial().isAir()).toList();
        int surfaceLayer = pattern.stream().mapToInt(ClipboardBlock::y).min().orElse(0);
        List<BlockChange> changes = new ArrayList<>();
        try {
            for (ClipboardBlock saved : pattern) {
                BlockData data = Bukkit.createBlockData(saved.blockData());
                long x = (long) anchor.getBlockX() + saved.x();
                long y = (long) anchor.getBlockY() + saved.y() - surfaceLayer;
                long z = (long) anchor.getBlockZ() + saved.z();
                if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE
                        || y < world.getMinHeight() || y >= world.getMaxHeight())
                    throw new IllegalArgumentException("Brush vượt giới hạn tọa độ/chiều cao của world.");
                int blockX = (int) x, blockY = (int) y, blockZ = (int) z;
                Location location = new Location(world, blockX, blockY, blockZ);
                if (!world.getWorldBorder().isInside(location))
                    throw new IllegalArgumentException("Brush vượt world border; không đổi block nào.");
                if (!world.isChunkLoaded(blockX >> 4, blockZ >> 4))
                    throw new IllegalArgumentException("Brush đi vào chunk chưa nạp; không đổi block nào.");
                Block targetBlock = world.getBlockAt(blockX, blockY, blockZ);
                BlockChange change = createClipboardChange(player, targetBlock, data, saved.blockState(), saved.blockEntityData());
                if (change != null) changes.add(change);
            }
            if (changes.isEmpty()) {
                player.sendMessage("§eBrush không có block nào cần thay đổi (air được bỏ qua).");
                return;
            }
            scheduleEdit(player, "//brush", changes);
        } catch (IllegalArgumentException exception) {
            player.sendMessage("§c" + exception.getMessage());
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "[WorldRegion] brush failed player=" + player.getName(), exception);
            player.sendMessage("§cKhông thể áp dụng brush; không có thay đổi nào được xác nhận.");
        }
    }

    private BlockPattern parsePattern(String raw) {
        List<WeightedBlock> choices = new ArrayList<>(); double total = 0;
        for (String entry : raw.split(",")) {
            String value = entry.trim(); double weight = 1.0;
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^(\\d+(?:\\.\\d+)?)%(.+)$").matcher(value);
            if (matcher.matches()) { weight = Double.parseDouble(matcher.group(1)); value = matcher.group(2); }
            if (!(weight > 0) || !Double.isFinite(weight)) throw new IllegalArgumentException("Trọng số pattern phải lớn hơn 0.");
            BlockData data = parseBlockData(value);
            if (!data.getMaterial().isBlock()) throw new IllegalArgumentException("Đây không phải block: " + value);
            choices.add(new WeightedBlock(data.getAsString(), weight)); total += weight;
        }
        if (choices.isEmpty()) throw new IllegalArgumentException("Pattern block đang trống.");
        return new BlockPattern(List.copyOf(choices), total);
    }

    private BlockMatcher parseMatcher(String raw) {
        BlockData data = parseBlockData(raw);
        boolean exactState = raw.contains("[") || raw.matches("^\\d+:\\d+$");
        return candidate -> candidate.getMaterial() == data.getMaterial() && (!exactState || candidate.matches(data));
    }

    private BlockData parseBlockData(String raw) {
        String value = raw.trim();
        java.util.regex.Matcher legacyId = java.util.regex.Pattern.compile("^(\\d+)(?::(\\d+))?$").matcher(value);
        if (legacyId.matches()) {
            int id;
            int dataValue;
            try {
                id = Integer.parseInt(legacyId.group(1));
                dataValue = legacyId.group(2) == null ? 0 : Integer.parseInt(legacyId.group(2));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("ID block số không hợp lệ: " + value);
            }
            if (dataValue > 15) throw new IllegalArgumentException("Data ID block cũ phải nằm trong khoảng 0-15: " + value);
            if (id == 0 && dataValue == 0) return Bukkit.createBlockData(Material.AIR);
            if (dataValue == 0) {
                Material commonNumericBlock = switch (id) {
                    case 1 -> Material.STONE;
                    case 2 -> Material.GRASS_BLOCK;
                    default -> null;
                };
                if (commonNumericBlock != null) return Bukkit.createBlockData(commonNumericBlock);
            }
            Material legacyMaterial = java.util.Arrays.stream(Material.values())
                .filter(Material::isLegacy)
                .filter(material -> material.getId() == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ID block cũ: " + value));
            try {
                BlockData data = Bukkit.getUnsafe().fromLegacy(legacyMaterial, (byte) dataValue);
                if (data == null) throw new IllegalArgumentException("ID block cũ không hợp lệ: " + value);
                return data;
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("ID block cũ không hợp lệ: " + value);
            }
        }
        try { return Bukkit.createBlockData(value); }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Material, ID block hoặc block state không hợp lệ: " + value);
        }
    }

    private void addChange(Player player, List<BlockChange> changes, Block block, BlockData target) {
        addChange(player, changes, block, target, false);
    }

    private void addChange(Player player, List<BlockChange> changes, Block block, BlockData target, boolean allowBlockEntities) {
        if (!matchesMasks(player, block)) return;
        BlockChange change = createChange(player, block, target, allowBlockEntities);
        if (change != null) changes.add(change);
        if (changes.size() > editLimit()) throw new IllegalArgumentException("Thao tác vượt giới hạn " + limitLabel() + " block.");
    }

    private BlockChange createChange(Player player, Block block, BlockData target, boolean allowBlockEntities) {
        BlockData current = block.getBlockData();
        if (current.getAsString().equals(target.getAsString())) return null;
        BlockState beforeState = block.getState();
        return new BlockChange(block.getLocation(), current.getAsString(), target.getAsString(),
            beforeState instanceof org.bukkit.block.TileState ? beforeState.copy() : null, null, Map.of());
    }

    private BlockChange createClipboardChange(Player player, Block target, BlockData data, BlockState copiedState,
            Map<String, Object> copiedNbt) {
        if (!matchesMasks(player, target)) return null;
        BlockChange change = createChange(player, target, data, true);
        if (change == null && copiedState == null && (copiedNbt == null || copiedNbt.isEmpty())) return null;
        if (change == null) {
            BlockState oldState = target.getState();
            change = new BlockChange(target.getLocation(), target.getBlockData().getAsString(), data.getAsString(),
                oldState instanceof org.bukkit.block.TileState ? oldState.copy() : null, null, Map.of());
        }
        BlockState relocated = copiedState == null ? target.getState() : copiedState.copy(target.getLocation());
        relocated.setBlockData(data);
        if (copiedNbt != null && !copiedNbt.isEmpty() && relocated instanceof org.bukkit.block.TileState)
            NativeBlockEntityNbt.apply(relocated, copiedNbt, target.getX(), target.getY(), target.getZ());
        BlockState afterState = copiedState != null || copiedNbt != null && !copiedNbt.isEmpty()
                && relocated instanceof org.bukkit.block.TileState ? relocated : null;
        return new BlockChange(change.location(), change.before(), change.after(), change.beforeState(),
            afterState, copiedNbt == null ? Map.of() : copiedNbt);
    }

    private boolean matchesMasks(Player player, Block block) {
        BlockMatcher mask = playerMasks.get(player.getUniqueId());
        return (mask == null || mask.matches(block.getBlockData()))
            && (globalMask == null || globalMask.matches(block.getBlockData()));
    }

    private void scheduleEdit(Player player, String label, List<BlockChange> changes) {
        scheduleEdit(player, label, changes, false);
    }

    private void scheduleEdit(Player player, String label, List<BlockChange> changes, boolean refreshConnections) {
        if (changes.isEmpty()) { player.sendMessage("§eKhông có block nào cần thay đổi."); return; }
        UUID id = player.getUniqueId();
        List<ChunkTicket> tickets = acquireChunkTickets(changes);
        activeEdits.add(id);
        EditOperation operation = new EditOperation(label, new ArrayList<>(changes), refreshConnections);
        player.sendMessage("§eĐang áp dụng " + changes.size() + " block theo từng đợt. Đừng thoát world cho tới khi xong.");
        plugin.getLogger().info("[WorldRegion] edit-start player=" + player.getName() + ", operation=" + label + ", blocks=" + changes.size());
        final int[] index = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            try {
                int end = Math.min(operation.changes.size(), index[0] + editBatchSize());
                while (index[0] < end) {
                    BlockChange change = operation.changes.get(index[0]++);
                    restoreBlockChange(change, true);
                }
                if (index[0] >= operation.changes.size()) {
                    if (operation.refreshConnections) {
                        refreshLinkedConnections(operation.changes);
                        captureActualAfterStates(operation);
                    }
                    task.cancel(); activeEdits.remove(id); releaseChunkTickets(tickets);
                    Player online = Bukkit.getPlayer(id);
                    if (online != null) {
                        Deque<EditOperation> history = undoHistory.computeIfAbsent(id, ignored -> new ArrayDeque<>());
                        history.push(operation); while (history.size() > historyLimit()) history.removeLast();
                        redoHistory.remove(id);
                        online.sendMessage("§aHoàn tất " + label + ": " + changes.size() + " block.");
                    }
                    plugin.getLogger().info("[WorldRegion] edit-complete player=" + player.getName() + ", operation=" + label + ", blocks=" + changes.size());
                }
            } catch (RuntimeException exception) {
                task.cancel(); activeEdits.remove(id);
                for (int rollback = index[0] - 1; rollback >= 0; rollback--) {
                    BlockChange change = operation.changes.get(rollback);
                    restoreBlockChange(change, false);
                }
                releaseChunkTickets(tickets);
                plugin.getLogger().log(Level.SEVERE, "[WorldRegion] edit failed and rolled back player=" + player.getName() + ", operation=" + label, exception);
                Player online = Bukkit.getPlayer(id); if (online != null) online.sendMessage("§cLỗi khi sửa vùng; phần đã làm được hoàn tác.");
            }
        }, 1L, 1L);
    }

    private void applyHistory(Player player, boolean redo) {
        UUID id = player.getUniqueId();
        Deque<EditOperation> source = redo ? redoHistory.get(id) : undoHistory.get(id);
        if (source == null || source.isEmpty()) { player.sendMessage("§eKhông có thao tác để " + (redo ? "redo" : "undo") + "."); return; }
        EditOperation operation = source.peek();
        for (BlockChange change : operation.changes) {
            if (!change.location.getWorld().isChunkLoaded(change.location.getBlockX() >> 4, change.location.getBlockZ() >> 4)) {
                player.sendMessage("§cHãy nạp toàn bộ chunk của vùng trước khi undo/redo."); return;
            }
            String expected = redo ? change.before : change.after;
            if (!change.location.getBlock().getBlockData().getAsString().equals(expected)) {
                player.sendMessage("§cKhông thể " + (redo ? "redo" : "undo") + ": block đã bị thay đổi sau thao tác."); return;
            }
            boolean entityAwareSet = operation.label.equals("//set") || operation.label.equals("//clear");
            if (change.location.getBlock().getState() instanceof org.bukkit.block.TileState && !entityAwareSet
                    && change.beforeState == null && change.afterState == null && change.afterNbt.isEmpty()) {
                player.sendMessage("§cKhông thể undo/redo an toàn vì có block entity trong vùng."); return;
            }
        }
        List<ChunkTicket> tickets = acquireChunkTickets(operation.changes);
        activeEdits.add(id); source.pop();
        plugin.getLogger().info("[WorldRegion] history-start player=" + player.getName() + ", action=" + (redo ? "redo" : "undo")
            + ", operation=" + operation.label + ", blocks=" + operation.changes.size());
        final int[] index = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            try {
                int end = Math.min(operation.changes.size(), index[0] + editBatchSize());
                while (index[0] < end) {
                    BlockChange change = operation.changes.get(index[0]++);
                    restoreBlockChange(change, redo);
                }
                if (index[0] >= operation.changes.size()) {
                    if (operation.refreshConnections) {
                        refreshLinkedConnections(operation.changes);
                        if (redo) captureActualAfterStates(operation);
                    }
                    task.cancel(); activeEdits.remove(id); releaseChunkTickets(tickets);
                    if (player.isOnline()) {
                        (redo ? undoHistory : redoHistory).computeIfAbsent(id, ignored -> new ArrayDeque<>()).push(operation);
                        player.sendMessage("§aĐã " + (redo ? "redo " : "undo ") + operation.label + " (" + operation.changes.size() + " block).");
                    }
                }
            } catch (RuntimeException exception) {
                task.cancel(); activeEdits.remove(id);
                for (int rollback = index[0] - 1; rollback >= 0; rollback--) {
                    BlockChange change = operation.changes.get(rollback);
                    restoreBlockChange(change, !redo);
                }
                if (operation.refreshConnections) refreshLinkedConnections(operation.changes.subList(0, index[0]));
                source.push(operation); releaseChunkTickets(tickets);
                plugin.getLogger().log(Level.SEVERE, "[WorldRegion] history operation failed and rolled back player=" + player.getName(), exception);
                if (player.isOnline()) player.sendMessage("§cUndo/redo gặp lỗi; phần đã làm được hoàn tác.");
            }
        }, 1L, 1L);
    }

    private void setEditLimit(Player player, String[] args) {
        if (args.length != 1) {
            player.sendMessage("§7Giới hạn hiện tại: " + limitLabel() + ". Dùng //limit <số|unlimited>.");
            return;
        }
        boolean unlimited = "unlimited".equalsIgnoreCase(args[0]);
        long limit = unlimited ? -1L : positiveLong(args[0]);
        plugin.getConfig().set("worldedit.max-blocks", limit);
        if (unlimited) plugin.getConfig().set("worldedit.max-chunks-per-operation", -1);
        plugin.saveConfig();
        player.sendMessage(unlimited
            ? "§cĐã bỏ giới hạn block và chunk cho thao tác WorldEdit. Chỉ dùng trên vùng đã nạp và cân nhắc RAM/server load."
            : "§aĐã đặt giới hạn thao tác thành §f" + limit + "§a block; giới hạn chunk hiện tại: §f"
                + chunkLimitLabel() + "§a.");
    }

    private long editLimit() {
        long configured = plugin.getConfig().getLong("worldedit.max-blocks", DEFAULT_MAX_EDIT_BLOCKS);
        return configured <= 0 ? Long.MAX_VALUE : configured;
    }
    private String limitLabel() { return editLimit() == Long.MAX_VALUE ? "không giới hạn" : Long.toString(editLimit()); }
    private int chunkLimit() { return plugin.getConfig().getInt("worldedit.max-chunks-per-operation", DEFAULT_MAX_EDIT_CHUNKS); }
    private String chunkLimitLabel() { return chunkLimit() <= 0 ? "không giới hạn" : Integer.toString(chunkLimit()); }
    private int historyLimit() { return Math.max(1, Math.min(100, plugin.getConfig().getInt("worldedit.history-size", DEFAULT_HISTORY_LIMIT))); }
    private int editBatchSize() { return Math.max(32, Math.min(4096, plugin.getConfig().getInt("worldedit.blocks-per-tick", EDIT_BATCH_SIZE))); }

    private void restoreBlockChange(BlockChange change, boolean useAfter) {
        BlockState snapshot = useAfter ? change.afterState : change.beforeState;
        if (snapshot != null) {
            if (!snapshot.update(true, false)) {
                throw new IllegalStateException("Could not restore previous block entity at " + change.location);
            }
            return;
        }
        Block block = change.location.getBlock();
        block.setBlockData(Bukkit.createBlockData(useAfter ? change.after : change.before), false);
        if (useAfter && !change.afterNbt.isEmpty()) {
            BlockState state = block.getState();
            NativeBlockEntityNbt.apply(state, change.afterNbt, block.getX(), block.getY(), block.getZ());
            if (!state.update(true, false)) throw new IllegalStateException("Could not apply block entity at " + change.location);
        }
    }

    private void refreshLinkedConnections(List<BlockChange> changes) {
        Set<String> refreshed = new HashSet<>();
        for (BlockChange change : changes) {
            Block block = change.location.getBlock();
            BlockData data = block.getBlockData();
            if (!(data instanceof MultipleFacing) && !(data instanceof Rail)) continue;
            String key = block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
            if (refreshed.add(key)) block.setBlockData(data, true);
        }
    }

    private void captureActualAfterStates(EditOperation operation) {
        List<BlockChange> resolved = new ArrayList<>(operation.changes.size());
        for (BlockChange change : operation.changes) {
            resolved.add(new BlockChange(change.location, change.before,
                change.location.getBlock().getBlockData().getAsString(), change.beforeState, change.afterState, change.afterNbt));
        }
        operation.changes = List.copyOf(resolved);
    }

    private List<ChunkTicket> acquireChunkTickets(List<BlockChange> changes) {
        Map<String, ChunkTicket> chunks = new LinkedHashMap<>();
        for (BlockChange change : changes) {
            World world = change.location.getWorld();
            int chunkX = change.location.getBlockX() >> 4, chunkZ = change.location.getBlockZ() >> 4;
            chunks.putIfAbsent(world.getUID() + ":" + chunkX + ":" + chunkZ, new ChunkTicket(world, chunkX, chunkZ));
        }
        int maximum = chunkLimit();
        if (maximum > 0 && chunks.size() > maximum)
            throw new IllegalArgumentException("Thao tác đi qua " + chunks.size() + " chunk; giới hạn là " + maximum
                + ". Dùng //limit unlimited để bỏ giới hạn hoặc tăng worldedit.max-chunks-per-operation.");
        List<ChunkTicket> acquired = new ArrayList<>();
        try {
            for (ChunkTicket ticket : chunks.values()) {
                if (!ticket.world.isChunkLoaded(ticket.x, ticket.z)) throw new IllegalArgumentException("Chunk bị unload trước khi bắt đầu thao tác.");
                if (!ticket.world.addPluginChunkTicket(ticket.x, ticket.z, plugin)) throw new IllegalArgumentException("Không thể giữ chunk đang chỉnh sửa.");
                acquired.add(ticket);
            }
        } catch (RuntimeException exception) {
            releaseChunkTickets(acquired);
            throw exception;
        }
        return List.copyOf(acquired);
    }

    private void releaseChunkTickets(List<ChunkTicket> tickets) {
        for (ChunkTicket ticket : tickets) ticket.world.removePluginChunkTicket(ticket.x, ticket.z, plugin);
    }

    private ItemStack toggle(Material material, String title, String path, boolean defaultValue, String help) {
        boolean value = plugin.getConfig().getBoolean(path, defaultValue);
        return icon(value ? Material.LIME_DYE : Material.GRAY_DYE,
            (value ? "§aBẬT • " : "§cTẮT • ") + title,
            List.of("§7" + help, "§eNhấn để đổi và lưu ngay."));
    }

    public void giveWand(Player player) {
        if (!canManage(player)) return;
        ItemStack wand = new ItemStack(Material.WOODEN_AXE);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName("§6WorldEdit • Búa chọn vùng");
        meta.setLore(List.of("§7Chuột trái: điểm 1 • Chuột phải: điểm 2", "§7//set / //replace / //walls / //faces",
            "§7//copy / //paste / //undo / //redo", "§7Copy rồi lấy stick brush để vẽ hoặc cuốc chim để preview/xoay/đặt.",
            "§7/vnregion để quản lý vùng và flags."));
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        wand.setItemMeta(meta);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(wand);
        if (!leftovers.isEmpty()) player.getWorld().dropItemNaturally(player.getLocation(), wand);
        player.sendMessage("§aĐã cấp búa gỗ chọn vùng. §eChuột trái chọn góc 1, chuột phải chọn góc 2.");
        plugin.getLogger().info("[WorldRegion] wand-issued admin=" + player.getName());
    }

    private boolean isWand(ItemStack item) {
        return item != null && item.getType() == Material.WOODEN_AXE && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    private boolean isBrush(ItemStack item) {
        return item != null && item.getType() == Material.STICK && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(brushKey, PersistentDataType.BYTE);
    }

    private boolean isClipboardPasteTool(ItemStack item) {
        return item != null && item.getType() == Material.WOODEN_PICKAXE && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(clipboardPasteToolKey, PersistentDataType.BYTE);
    }

    private void giveDeltreeTool(Player player) {
        ItemStack tool = new ItemStack(Material.SHEARS); ItemMeta meta = tool.getItemMeta();
        meta.setDisplayName("§aWorldEdit • Tree Remover"); meta.setLore(List.of("§7Chuột trái vào thân cây để xóa thân và lá.", "§7Mỗi lần dùng có thể //undo."));
        meta.getPersistentDataContainer().set(deltreeToolKey, PersistentDataType.BYTE, (byte) 1); tool.setItemMeta(meta);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(tool);
        if (!leftovers.isEmpty()) player.getWorld().dropItemNaturally(player.getLocation(), tool);
        player.sendMessage("§aĐã cấp Tree Remover. Chuột trái vào thân cây; //undo để phục hồi.");
    }

    private boolean isDeltreeTool(ItemStack item) {
        return item != null && item.getType() == Material.SHEARS && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(deltreeToolKey, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDeltreeToolUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isDeltreeTool(event.getItem())
                || event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        event.setCancelled(true); Player player = event.getPlayer();
        if (!canManage(player)) { player.sendMessage("§cBạn không có quyền dùng công cụ xóa cây."); return; }
        Block clicked = event.getClickedBlock(); if (clicked == null || !isLog(clicked.getType())) {
            player.sendMessage("§eHãy đánh dấu một block thân cây."); return;
        }
        World world = clicked.getWorld(); Set<BlockPosition> logs = new LinkedHashSet<>(); Deque<BlockPosition> queue = new ArrayDeque<>();
        BlockPosition start = new BlockPosition(clicked.getX(), clicked.getY(), clicked.getZ()); queue.add(start); logs.add(start);
        while (!queue.isEmpty() && logs.size() <= 4096) {
            BlockPosition point = queue.removeFirst();
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dy == 0 && dz == 0) continue;
                int x = point.x() + dx, y = point.y() + dy, z = point.z() + dz;
                if (y < world.getMinHeight() || y >= world.getMaxHeight() || !world.isChunkLoaded(x >> 4, z >> 4)) continue;
                BlockPosition adjacent = new BlockPosition(x, y, z);
                if (logs.contains(adjacent)) continue;
                if (isLog(world.getBlockAt(x, y, z).getType())) { logs.add(adjacent); queue.addLast(adjacent); }
            }
        }
        if (logs.size() > 4096) { player.sendMessage("§cCây vượt giới hạn 4096 block thân; không thay đổi gì."); return; }
        Set<BlockPosition> tree = new LinkedHashSet<>(logs);
        for (BlockPosition log : logs) for (int dx = -6; dx <= 6; dx++) for (int dy = -6; dy <= 6; dy++) for (int dz = -6; dz <= 6; dz++) {
            int x = log.x() + dx, y = log.y() + dy, z = log.z() + dz;
            if (y < world.getMinHeight() || y >= world.getMaxHeight() || !world.isChunkLoaded(x >> 4, z >> 4)) continue;
            if (isLeaves(world.getBlockAt(x, y, z).getType())) tree.add(new BlockPosition(x, y, z));
        }
        if (tree.size() > editLimit()) { player.sendMessage("§cCây vượt giới hạn thao tác " + limitLabel() + " block."); return; }
        List<BlockChange> changes = new ArrayList<>();
        for (BlockPosition point : tree) addChange(player, changes, world.getBlockAt(point.x(), point.y(), point.z()), Bukkit.createBlockData(Material.AIR));
        scheduleEdit(player, "//deltree", changes, true);
    }

    private boolean isLog(Material material) {
        String name = material.name();
        return name.endsWith("_LOG") || name.endsWith("_WOOD") || name.endsWith("_STEM") || "MUSHROOM_STEM".equals(name);
    }

    private boolean isLeaves(Material material) { return material.name().endsWith("_LEAVES"); }

    private void giveBrush(Player player) {
        Clipboard clipboard = clipboards.get(player.getUniqueId());
        BrushConfig config = brushConfigs.get(player.getUniqueId());
        if (clipboard == null && config == null) {
            player.sendMessage("§cChọn vùng bằng búa rồi dùng //copy trước khi lấy brush.");
            return;
        }
        ItemStack brush = new ItemStack(Material.STICK);
        ItemMeta meta = brush.getItemMeta();
        meta.setDisplayName("§aWorldEdit • Brush");
        meta.setLore(config == null ? List.of("§7Clipboard pattern: §f" + clipboard.blocks().size() + " block",
            "§7Preview bám bề mặt đang ngắm; chuột phải để xác nhận.",
            "§7Block air trong clipboard sẽ bị bỏ qua.", "§eMỗi lần dùng brush lưu riêng vào //undo.")
            : List.of("§7Brush: §f" + config.kind() + " §7• bán kính §f" + config.radius(),
                "§7Nhắm bề mặt và chuột phải để áp dụng.", "§eMỗi lần dùng brush lưu riêng vào //undo."));
        meta.getPersistentDataContainer().set(brushKey, PersistentDataType.BYTE, (byte) 1);
        brush.setItemMeta(meta);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(brush);
        if (!leftovers.isEmpty()) player.getWorld().dropItemNaturally(player.getLocation(), brush);
        player.sendMessage("§aĐã cấp stick brush. §eCầm stick và chuột phải lên bề mặt để áp dụng; //undo hoàn tác từng lần.");
        plugin.getLogger().info("[WorldRegion] brush-issued player=" + player.getName()
            + ", mode=" + (config == null ? "clipboard" : config.kind()));
    }

    private void giveClipboardPasteTool(Player player) {
        Clipboard clipboard = clipboards.get(player.getUniqueId());
        ItemStack tool = new ItemStack(Material.WOODEN_PICKAXE);
        ItemMeta meta = tool.getItemMeta();
        meta.setDisplayName("§bWorldEdit • Clipboard Paste Tool");
        meta.setLore(List.of("§7Clipboard: §f" + (clipboard == null ? "chưa //copy" : clipboard.blocks().size() + " block"),
            "§7Có thể lấy tool trước khi copy; preview tự hiện sau //copy.",
            "§7Cầm để xem preview bám mặt đất đang ngắm.",
            "§7Chuột trái xoay 90°; chuột phải đặt.", "§7Có thể //undo sau khi đặt."));
        meta.getPersistentDataContainer().set(clipboardPasteToolKey, PersistentDataType.BYTE, (byte) 1);
        tool.setItemMeta(meta);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(tool);
        if (!leftovers.isEmpty()) player.getWorld().dropItemNaturally(player.getLocation(), tool);
        player.sendMessage(clipboard == null
            ? "§aĐã cấp Clipboard Paste Tool. Hãy //copy một vùng; preview sẽ tự hiện khi bạn cầm cuốc."
            : "§aĐã cấp cuốc chim Clipboard Paste Tool. Cầm cuốc để xem preview; chuột trái xoay, chuột phải đặt và có thể //undo.");
        plugin.getLogger().info("[WorldRegion] paste-tool-issued player=" + player.getName()
            + ", blocks=" + (clipboard == null ? "not-copied" : clipboard.blocks().size()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClipboardBrushUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isBrush(event.getItem())) return;
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK
                && action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        event.setCancelled(true);
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!canManage(player)) {
            player.sendMessage("§cBạn không có quyền dùng WorldEdit brush.");
            return;
        }
        if (activeEdits.contains(player.getUniqueId())) {
            player.sendMessage("§eThao tác vùng trước vẫn đang chạy.");
            return;
        }
        Clipboard clipboard = clipboards.get(player.getUniqueId());
        BrushConfig config = brushConfigs.get(player.getUniqueId());
        if (config != null) {
            Block target = brushSurfaceAnchor(player, event.getClickedBlock());
            if (target == null) { player.sendMessage("§eHãy nhắm vào một block để dùng brush."); return; }
            try { applyGenericBrush(player, config, target); }
            catch (IllegalArgumentException exception) { player.sendMessage("§c" + exception.getMessage()); }
            return;
        }
        if (clipboard == null) {
            player.sendMessage("§cClipboard trống. Chọn vùng bằng búa rồi dùng //copy trước.");
            return;
        }
        Block target = brushSurfaceAnchor(player, event.getClickedBlock());
        if (target == null) {
            player.sendMessage("§eHãy nhắm vào một block để vẽ pattern.");
            return;
        }
        applyClipboardBrush(player, clipboard, target);
    }

    private void applyGenericBrush(Player player, BrushConfig config, Block clicked) {
        World world = clicked.getWorld();
        int radius = config.radius();
        int centerX = clicked.getX(), centerY = clicked.getY() + 1, centerZ = clicked.getZ();
        List<BlockChange> changes = new ArrayList<>();
        if ("sphere".equals(config.kind())) {
            BlockData pattern = Bukkit.createBlockData(config.blockData());
            long bound = (2L * radius + 1) * (2L * radius + 1) * (2L * radius + 1);
            if (bound > editLimit()) throw new IllegalArgumentException("Brush vượt giới hạn " + limitLabel() + " block.");
            for (int x = centerX - radius; x <= centerX + radius; x++) for (int y = centerY - radius; y <= centerY + radius; y++)
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    if (distanceSquared(x - centerX, y - centerY, z - centerZ) > radius * radius) continue;
                    if (y < world.getMinHeight() || y >= world.getMaxHeight() || !world.isChunkLoaded(x >> 4, z >> 4))
                        throw new IllegalArgumentException("Brush đi qua chunk chưa nạp hoặc vượt chiều cao.");
                    addChange(player, changes, world.getBlockAt(x, y, z), pattern);
                }
        } else {
            Map<BlockPosition, Material> original = new HashMap<>();
            for (int x = centerX - radius; x <= centerX + radius; x++) for (int y = centerY - radius; y <= centerY + radius; y++)
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    if (distanceSquared(x - centerX, y - centerY, z - centerZ) > radius * radius) continue;
                    if (y < world.getMinHeight() || y >= world.getMaxHeight() || !world.isChunkLoaded(x >> 4, z >> 4))
                        throw new IllegalArgumentException("Brush đi qua chunk chưa nạp hoặc vượt chiều cao.");
                    original.put(new BlockPosition(x, y, z), world.getBlockAt(x, y, z).getType());
                }
            for (Map.Entry<BlockPosition, Material> entry : original.entrySet()) {
                BlockPosition point = entry.getKey(); Map<Material, Integer> counts = new HashMap<>();
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                    Material material = original.get(new BlockPosition(point.x() + dx, point.y() + dy, point.z() + dz));
                    if (material != null) counts.merge(material, 1, Integer::sum);
                }
                Material smooth = counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(entry.getValue());
                if (smooth.isBlock() && !smooth.isAir()) addChange(player, changes, world.getBlockAt(point.x(), point.y(), point.z()), Bukkit.createBlockData(smooth));
            }
        }
        scheduleEdit(player, "//br " + config.kind(), changes, true);
    }

    private int distanceSquared(int x, int y, int z) { return x * x + y * y + z * z; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClipboardPasteToolUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isClipboardPasteTool(event.getItem())) return;
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK
                && action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!canManage(player)) {
            player.sendMessage("§cBạn không có quyền dùng WorldEdit clipboard tool.");
            return;
        }
        if (activeEdits.contains(player.getUniqueId())) {
            player.sendMessage("§eThao tác vùng trước vẫn đang chạy.");
            return;
        }
        Clipboard clipboard = clipboards.get(player.getUniqueId());
        if (clipboard == null) {
            removePastePreview(player.getUniqueId());
            player.sendMessage("§cClipboard trống. Chọn vùng bằng búa rồi dùng //copy trước.");
            return;
        }
        PastePreview preview = pastePreviews.get(player.getUniqueId());
        if (preview == null || preview.clipboard != clipboard) {
            removePastePreview(player.getUniqueId());
            List<ClipboardBlock> transformed = rotateClipboard(clipboard, 0);
            preview = new PastePreview(clipboard, transformed, lowestClipboardY(transformed));
            pastePreviews.put(player.getUniqueId(), preview);
        }
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            preview.rotation = (preview.rotation + 90) % 360;
            preview.transformedBlocks = rotateClipboard(clipboard, preview.rotation);
            preview.surfaceLayer = lowestClipboardY(preview.transformedBlocks);
            preview.anchor = null;
            Block anchor = pasteSurfaceAnchor(player, null, null);
            if (anchor != null) updatePastePreview(player, preview, anchor);
            player.sendActionBar("§bClipboard xoay §f" + preview.rotation + "°§b. Chuột phải để đặt.");
            return;
        }
        Block anchor = pasteSurfaceAnchor(player, event.getClickedBlock(), event.getBlockFace());
        if (anchor == null) {
            player.sendMessage("§eHãy ngắm vào mặt đất hoặc block để xem vị trí đặt.");
            return;
        }
        placeClipboardTool(player, preview, anchor);
    }

    private void placeClipboardTool(Player player, PastePreview preview, Block anchor) {
        World world = player.getWorld();
        List<BlockChange> changes = new ArrayList<>();
        try {
            for (ClipboardBlock saved : preview.transformedBlocks) {
                long x = (long) anchor.getX() + saved.x();
                long y = (long) anchor.getY() + saved.y() - preview.surfaceLayer;
                long z = (long) anchor.getZ() + saved.z();
                if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE
                        || y < world.getMinHeight() || y >= world.getMaxHeight()) {
                    throw new IllegalArgumentException("Clipboard vượt giới hạn tọa độ/chiều cao của world.");
                }
                int blockX = (int) x, blockY = (int) y, blockZ = (int) z;
                Location location = new Location(world, blockX, blockY, blockZ);
                if (!world.getWorldBorder().isInside(location))
                    throw new IllegalArgumentException("Clipboard vượt world border; không đổi block nào.");
                if (!world.isChunkLoaded(blockX >> 4, blockZ >> 4))
                    throw new IllegalArgumentException("Clipboard đi vào chunk chưa nạp; không đổi block nào.");
                addChange(player, changes, world.getBlockAt(blockX, blockY, blockZ),
                    Bukkit.createBlockData(saved.blockData()));
            }
            if (changes.size() > editLimit())
                throw new IllegalArgumentException("Clipboard cần " + changes.size() + " block; giới hạn hiện tại là " + limitLabel() + ".");
            scheduleEdit(player, "//paste-tool", changes, true);
            plugin.getLogger().info("[WorldRegion] paste-tool-place player=" + player.getName() + ", rotation="
                + preview.rotation + ", blocks=" + changes.size() + ", anchor=" + world.getName() + ":"
                + anchor.getX() + "," + anchor.getY() + "," + anchor.getZ());
        } catch (IllegalArgumentException exception) {
            player.sendMessage("§c" + exception.getMessage());
        }
    }

    private void tickBrushPreviews() {
        Set<UUID> active = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            ItemStack held = player.getInventory().getItemInMainHand();
            Clipboard clipboard = clipboards.get(playerId);
            if (!canManage(player) || !isBrush(held) || clipboard == null) {
                removeBrushPreview(playerId);
                continue;
            }
            Block anchor = brushSurfaceAnchor(player, null);
            if (anchor == null) {
                removeBrushPreview(playerId);
                continue;
            }
            active.add(playerId);
            updateBrushPreview(player, clipboard, anchor);
        }
        for (UUID playerId : new HashSet<>(brushPreviews.keySet())) {
            if (!active.contains(playerId)) removeBrushPreview(playerId);
        }
        tickClipboardPastePreviews();
    }

    private void tickClipboardPastePreviews() {
        Set<UUID> active = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            ItemStack held = player.getInventory().getItemInMainHand();
            Clipboard clipboard = clipboards.get(playerId);
            if (!canManage(player) || !isClipboardPasteTool(held) || clipboard == null) {
                removePastePreview(playerId);
                continue;
            }
            Block anchor = pasteSurfaceAnchor(player, null, null);
            if (anchor == null) {
                removePastePreview(playerId);
                continue;
            }
            PastePreview preview = pastePreviews.get(playerId);
            if (preview == null || preview.clipboard != clipboard) {
                removePastePreview(playerId);
                List<ClipboardBlock> transformed = rotateClipboard(clipboard, 0);
                preview = new PastePreview(clipboard, transformed, lowestClipboardY(transformed));
                pastePreviews.put(playerId, preview);
            }
            active.add(playerId);
            updatePastePreview(player, preview, anchor);
        }
        for (UUID playerId : new HashSet<>(pastePreviews.keySet())) {
            if (!active.contains(playerId)) removePastePreview(playerId);
        }
    }

    private Block pasteSurfaceAnchor(Player player, Block clicked, BlockFace face) {
        if (clicked == null) {
            org.bukkit.util.RayTraceResult hit = player.rayTraceBlocks(64.0);
            if (hit == null || hit.getHitBlock() == null) return null;
            clicked = hit.getHitBlock();
            face = hit.getHitBlockFace();
        }
        if (face == null || face == BlockFace.SELF) face = BlockFace.UP;
        return clicked.getRelative(face);
    }

    private List<ClipboardBlock> rotateClipboard(Clipboard clipboard, int degrees) {
        List<ClipboardBlock> result = new ArrayList<>(clipboard.blocks().size());
        int turns = Math.floorMod(degrees, 360) / 90;
        for (ClipboardBlock saved : clipboard.blocks()) {
            int x = saved.x(), z = saved.z();
            BlockData data = Bukkit.createBlockData(saved.blockData());
            List<BlockFace> connectedFaces = data instanceof MultipleFacing facing
                ? facing.getFaces().stream().toList() : List.of();
            for (int turn = 0; turn < turns; turn++) {
                int oldX = x;
                x = z;
                z = -oldX;
                data.rotate(StructureRotation.CLOCKWISE_90);
            }
            if (turns > 0 && data instanceof MultipleFacing facing) {
                for (BlockFace face : facing.getAllowedFaces()) facing.setFace(face, false);
                for (BlockFace face : connectedFaces) {
                    BlockFace rotated = rotateHorizontalFace(face, turns);
                    if (facing.getAllowedFaces().contains(rotated)) facing.setFace(rotated, true);
                }
            }
            BlockState transformedState = saved.blockState() == null ? null : saved.blockState().copy();
            if (transformedState != null) transformedState.setBlockData(data);
            result.add(new ClipboardBlock(x, saved.y(), z, data.getAsString(), transformedState, saved.blockEntityData()));
        }
        return List.copyOf(result);
    }

    private void rotateClipboardCommand(Player player, String[] args) {
        if (args.length != 1 || !Set.of(90, 180, 270).contains(parseInt(args[0])))
            throw new IllegalArgumentException("Dùng //rotate <90|180|270>.");
        Clipboard clipboard = requireClipboard(player);
        int degrees = Integer.parseInt(args[0]);
        List<ClipboardBlock> blocks = rotateClipboard(clipboard, degrees);
        boolean swap = degrees == 90 || degrees == 270;
        clipboards.put(player.getUniqueId(), new Clipboard(blocks, swap ? clipboard.sizeZ() : clipboard.sizeX(),
            clipboard.sizeY(), swap ? clipboard.sizeX() : clipboard.sizeZ(), clipboard.origin(),
            clipboard.minOffsetX(), clipboard.minOffsetY(), clipboard.minOffsetZ()));
        removePastePreview(player.getUniqueId()); removeBrushPreview(player.getUniqueId());
        player.sendMessage("§aClipboard đã xoay " + degrees + "°. Dùng //paste để đặt.");
    }

    private void flipClipboardCommand(Player player, String[] args) {
        if (args.length != 1) throw new IllegalArgumentException("Dùng //flip <x|z|north|south|east|west>.");
        String direction = args[0].toLowerCase(Locale.ROOT);
        boolean flipX = Set.of("x", "east", "west").contains(direction);
        boolean flipZ = Set.of("z", "north", "south").contains(direction);
        if (!flipX && !flipZ) throw new IllegalArgumentException("Hướng lật phải là x/z hoặc east/west/north/south.");
        Clipboard clipboard = requireClipboard(player); List<ClipboardBlock> blocks = new ArrayList<>(clipboard.blocks().size());
        for (ClipboardBlock saved : clipboard.blocks()) {
            BlockData data = Bukkit.createBlockData(saved.blockData());
            data.mirror(flipX ? org.bukkit.block.structure.Mirror.FRONT_BACK : org.bukkit.block.structure.Mirror.LEFT_RIGHT);
            BlockState state = saved.blockState() == null ? null : saved.blockState().copy();
            if (state != null) state.setBlockData(data);
            blocks.add(new ClipboardBlock(flipX ? -saved.x() : saved.x(), saved.y(), flipZ ? -saved.z() : saved.z(),
                data.getAsString(), state, saved.blockEntityData()));
        }
        clipboards.put(player.getUniqueId(), new Clipboard(List.copyOf(blocks), clipboard.sizeX(), clipboard.sizeY(), clipboard.sizeZ(),
            clipboard.origin(), clipboard.minOffsetX(), clipboard.minOffsetY(), clipboard.minOffsetZ()));
        removePastePreview(player.getUniqueId()); removeBrushPreview(player.getUniqueId());
        player.sendMessage("§aClipboard đã lật theo trục " + (flipX ? "X" : "Z") + ". Dùng //paste để đặt.");
    }

    private Clipboard requireClipboard(Player player) {
        Clipboard clipboard = clipboards.get(player.getUniqueId());
        if (clipboard == null) throw new IllegalArgumentException("Clipboard trống; hãy chọn vùng và dùng //copy trước.");
        return clipboard;
    }

    private int parseInt(String value) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException exception) { return -1; }
    }

    private BlockFace rotateHorizontalFace(BlockFace face, int turns) {
        BlockFace result = face;
        for (int turn = 0; turn < turns; turn++) {
            result = switch (result) {
                case NORTH -> BlockFace.WEST;
                case WEST -> BlockFace.SOUTH;
                case SOUTH -> BlockFace.EAST;
                case EAST -> BlockFace.NORTH;
                default -> result;
            };
        }
        return result;
    }

    private int lowestClipboardY(List<ClipboardBlock> blocks) {
        return blocks.stream().mapToInt(ClipboardBlock::y).min().orElse(0);
    }

    private void updatePastePreview(Player player, PastePreview preview, Block anchorBlock) {
        Location anchor = anchorBlock.getLocation();
        if (preview.anchor != null && preview.anchor.getWorld().equals(anchor.getWorld())
                && preview.anchor.getBlockX() == anchor.getBlockX()
                && preview.anchor.getBlockY() == anchor.getBlockY()
                && preview.anchor.getBlockZ() == anchor.getBlockZ()) return;
        preview.anchor = anchor;
        List<ClipboardBlock> visible = preview.transformedBlocks.stream()
            .filter(block -> !Bukkit.createBlockData(block.blockData()).getMaterial().isAir()).toList();
        int configuredLimit = plugin.getConfig().getInt("worldedit.brush-preview-blocks", DEFAULT_BRUSH_PREVIEW_BLOCKS);
        int displayLimit = configuredLimit == -1 ? Integer.MAX_VALUE : Math.max(1, Math.min(32_768, configuredLimit));
        int count = Math.min(visible.size(), displayLimit);
        while (preview.displays.size() > count) preview.displays.remove(preview.displays.size() - 1).remove();
        for (int index = 0; index < count; index++) {
            ClipboardBlock saved = visible.get(index);
            Location location = anchor.clone().add(saved.x(), saved.y() - preview.surfaceLayer, saved.z());
            BlockDisplay display;
            if (index >= preview.displays.size()) {
                display = anchor.getWorld().spawn(location, BlockDisplay.class, entity -> {
                    entity.setBlock(Bukkit.createBlockData(saved.blockData()));
                    entity.setGlowing(true);
                    entity.setVisibleByDefault(false);
                    entity.setPersistent(false);
                });
                preview.displays.add(display);
            } else {
                display = preview.displays.get(index);
                if (!display.getWorld().equals(location.getWorld())) {
                    display.remove();
                    display = anchor.getWorld().spawn(location, BlockDisplay.class, entity -> {
                        entity.setBlock(Bukkit.createBlockData(saved.blockData()));
                        entity.setGlowing(true);
                        entity.setVisibleByDefault(false);
                        entity.setPersistent(false);
                    });
                    preview.displays.set(index, display);
                } else {
                    display.teleport(location);
                    display.setBlock(Bukkit.createBlockData(saved.blockData()));
                }
            }
            player.showEntity(plugin, display);
        }
        String truncated = visible.size() > displayLimit ? " §e(preview giới hạn)" : "";
        player.sendActionBar("§bClipboard preview §f" + preview.rotation + "°§b tại §f" + anchorBlock.getX()
            + " " + anchorBlock.getY() + " " + anchorBlock.getZ() + " §7• chuột trái xoay, chuột phải đặt" + truncated);
    }

    private void removePastePreview(UUID playerId) {
        PastePreview preview = pastePreviews.remove(playerId);
        if (preview != null) for (BlockDisplay display : preview.displays) display.remove();
    }

    private Block brushSurfaceAnchor(Player player, Block clicked) {
        if (clicked != null) return clicked;
        org.bukkit.util.RayTraceResult hit = player.rayTraceBlocks(64.0);
        if (hit == null || hit.getHitBlock() == null) return null;
        return hit.getHitBlock();
    }

    private void updateBrushPreview(Player player, Clipboard clipboard, Block anchorBlock) {
        UUID playerId = player.getUniqueId();
        BrushPreview preview = brushPreviews.get(playerId);
        if (preview == null || preview.clipboard != clipboard) {
            removeBrushPreview(playerId);
            List<ClipboardBlock> pattern = clipboard.blocks().stream()
                .filter(block -> !Bukkit.createBlockData(block.blockData()).getMaterial().isAir()).toList();
            int surfaceLayer = pattern.stream().mapToInt(ClipboardBlock::y).min().orElse(0);
            preview = new BrushPreview(clipboard, pattern, surfaceLayer);
            brushPreviews.put(playerId, preview);
        }
        Location anchor = anchorBlock.getLocation();
        if (preview.anchor != null && preview.anchor.getWorld().equals(anchor.getWorld())
                && preview.anchor.getBlockX() == anchor.getBlockX()
                && preview.anchor.getBlockY() == anchor.getBlockY()
                && preview.anchor.getBlockZ() == anchor.getBlockZ()) return;
        preview.anchor = anchor;
        int configuredLimit = plugin.getConfig().getInt("worldedit.brush-preview-blocks", DEFAULT_BRUSH_PREVIEW_BLOCKS);
        int displayLimit = configuredLimit == -1 ? Integer.MAX_VALUE : Math.max(1, Math.min(32_768, configuredLimit));
        int count = Math.min(preview.pattern.size(), displayLimit);
        while (preview.displays.size() > count) preview.displays.remove(preview.displays.size() - 1).remove();
        for (int index = 0; index < count; index++) {
            ClipboardBlock saved = preview.pattern.get(index);
            Location location = anchor.clone().add(saved.x(), saved.y() - preview.surfaceLayer, saved.z());
            BlockDisplay display;
            if (index >= preview.displays.size()) {
                display = anchor.getWorld().spawn(location, BlockDisplay.class, entity -> {
                    entity.setBlock(Bukkit.createBlockData(saved.blockData()));
                    entity.setGlowing(true);
                    entity.setVisibleByDefault(false);
                    entity.setPersistent(false);
                });
                player.showEntity(plugin, display);
                preview.displays.add(display);
            } else {
                display = preview.displays.get(index);
                if (!display.getWorld().equals(location.getWorld())) {
                    display.remove();
                    display = anchor.getWorld().spawn(location, BlockDisplay.class, entity -> {
                        entity.setBlock(Bukkit.createBlockData(saved.blockData()));
                        entity.setGlowing(true);
                        entity.setVisibleByDefault(false);
                        entity.setPersistent(false);
                    });
                    preview.displays.set(index, display);
                } else {
                    display.teleport(location);
                    display.setBlock(Bukkit.createBlockData(saved.blockData()));
                }
                player.showEntity(plugin, display);
            }
        }
        String truncated = preview.pattern.size() > displayLimit ? " §e(preview giới hạn)" : "";
        player.sendActionBar("§aBrush preview tại §f" + anchorBlock.getX() + " " + anchorBlock.getY() + " "
            + anchorBlock.getZ() + " §7• chuột phải xác nhận • air bỏ qua" + truncated);
    }

    private void removeBrushPreview(UUID playerId) {
        BrushPreview preview = brushPreviews.remove(playerId);
        if (preview != null) for (BlockDisplay display : preview.displays) display.remove();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSelectionClick(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || !isWand(event.getItem()) || !canManage(event.getPlayer())) return;
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        Selection selection = selections.computeIfAbsent(player.getUniqueId(), ignored -> new Selection());
        Block block = event.getClickedBlock();
        if (action == Action.LEFT_CLICK_BLOCK) selection.setFirst(block.getLocation());
        else selection.setSecond(block.getLocation());
        player.sendMessage("§eĐiểm " + (action == Action.LEFT_CLICK_BLOCK ? "1" : "2") + " đã chọn: §f"
            + block.getWorld().getName() + " " + block.getX() + " " + block.getY() + " " + block.getZ());
        player.sendActionBar("§eLựa chọn WorldEdit: " + selectionText(selection));
    }

    private void renderSelectionParticles() {
        if (!plugin.getConfig().getBoolean("worldedit.selection-particles.enabled", true)) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            Selection selection = selections.get(player.getUniqueId());
            if (selection == null || selection.first == null || !selection.first.getWorld().equals(player.getWorld())) continue;
            if (!selection.complete()) {
                Location point = selection.first.clone().add(0.5, 0.5, 0.5);
                player.spawnParticle(org.bukkit.Particle.DUST, point, 1, 0, 0, 0,
                    new org.bukkit.Particle.DustOptions(Color.RED, 1.4f));
                continue;
            }
            double viewDistance = Math.max(16.0,
                plugin.getConfig().getDouble("worldedit.selection-particles.view-distance-blocks", 96.0));
            Location playerLocation = player.getLocation();
            double closestX = Math.max(selection.minX(), Math.min(selection.maxX(), playerLocation.getX()));
            double closestY = Math.max(selection.minY(), Math.min(selection.maxY(), playerLocation.getY()));
            double closestZ = Math.max(selection.minZ(), Math.min(selection.maxZ(), playerLocation.getZ()));
            double dx = closestX - playerLocation.getX(), dy = closestY - playerLocation.getY();
            double dz = closestZ - playerLocation.getZ();
            if (dx * dx + dy * dy + dz * dz > viewDistance * viewDistance) continue;
            drawSelectionParticles(player, selection);
        }
    }

    private void drawSelectionParticles(Player player, Selection selection) {
        World world = selection.first.getWorld();
        int minX = selection.minX(), maxX = selection.maxX();
        int minY = selection.minY(), maxY = selection.maxY();
        int minZ = selection.minZ(), maxZ = selection.maxZ();
        drawParticleEdge(player, world, minX, minY, minZ, maxX, minY, minZ);
        drawParticleEdge(player, world, minX, minY, maxZ, maxX, minY, maxZ);
        drawParticleEdge(player, world, minX, maxY, minZ, maxX, maxY, minZ);
        drawParticleEdge(player, world, minX, maxY, maxZ, maxX, maxY, maxZ);
        drawParticleEdge(player, world, minX, minY, minZ, minX, minY, maxZ);
        drawParticleEdge(player, world, maxX, minY, minZ, maxX, minY, maxZ);
        drawParticleEdge(player, world, minX, maxY, minZ, minX, maxY, maxZ);
        drawParticleEdge(player, world, maxX, maxY, minZ, maxX, maxY, maxZ);
        drawParticleEdge(player, world, minX, minY, minZ, minX, maxY, minZ);
        drawParticleEdge(player, world, maxX, minY, minZ, maxX, maxY, minZ);
        drawParticleEdge(player, world, minX, minY, maxZ, minX, maxY, maxZ);
        drawParticleEdge(player, world, maxX, minY, maxZ, maxX, maxY, maxZ);
    }

    private void drawParticleEdge(Player player, World world, int x1, int y1, int z1, int x2, int y2, int z2) {
        double distance = Math.sqrt((double) (x2 - x1) * (x2 - x1)
            + (double) (y2 - y1) * (y2 - y1) + (double) (z2 - z1) * (z2 - z1));
        double spacing = Math.max(0.25, Math.min(2.0,
            plugin.getConfig().getDouble("worldedit.selection-particles.spacing-blocks", 0.5)));
        int steps = Math.max(1, Math.min(512, (int) Math.ceil(distance / spacing)));
        for (int index = 0; index <= steps; index++) {
            double progress = (double) index / steps;
            Location point = new Location(world, x1 + (x2 - x1) * progress + 0.5,
                y1 + (y2 - y1) * progress + 0.5, z1 + (z2 - z1) * progress + 0.5);
            player.spawnParticle(org.bukkit.Particle.DUST, point, 1, 0, 0, 0,
                new org.bukkit.Particle.DustOptions(Color.RED, 1.2f));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isAdminBypass(event.getPlayer())) return;
        Location location = event.getBlock().getLocation();
        WorldSettingsManager worldSettings = plugin.getWorldSettingsManager();
        if (worldSettings != null && worldSettings.hasOverride(location.getWorld(), WorldSettingsManager.Rule.BLOCK_BREAK)) {
            if (!worldSettings.isEnabled(location.getWorld(), WorldSettingsManager.Rule.BLOCK_BREAK)) event.setCancelled(true);
            if (event.isCancelled()) return;
        }
        if ((worldSettings == null || !worldSettings.hasOverride(location.getWorld(), WorldSettingsManager.Rule.BLOCK_BREAK))
                && isStartWorld(location) && plugin.getConfig().getBoolean(startWorldPath() + ".protection.enabled", true)
                && plugin.getConfig().getBoolean(startWorldPath() + ".protection.block-break", true)) {
            event.setCancelled(true); return;
        }
        if (regionsAt(location).stream().anyMatch(region -> region.flag("deny-block-break"))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isAdminBypass(event.getPlayer())) return;
        Location location = event.getBlockPlaced().getLocation();
        WorldSettingsManager worldSettings = plugin.getWorldSettingsManager();
        if (worldSettings != null && worldSettings.hasOverride(location.getWorld(), WorldSettingsManager.Rule.BLOCK_PLACE)) {
            if (!worldSettings.isEnabled(location.getWorld(), WorldSettingsManager.Rule.BLOCK_PLACE)) event.setCancelled(true);
            if (event.isCancelled()) return;
        }
        if ((worldSettings == null || !worldSettings.hasOverride(location.getWorld(), WorldSettingsManager.Rule.BLOCK_PLACE))
                && isStartWorld(location) && plugin.getConfig().getBoolean(startWorldPath() + ".protection.enabled", true)
                && plugin.getConfig().getBoolean(startWorldPath() + ".protection.block-place", true)) {
            event.setCancelled(true); return;
        }
        if (regionsAt(location).stream().anyMatch(region -> region.flag("deny-block-place"))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getClickedBlock() == null || isAdminBypass(player)) return;
        Location location = event.getClickedBlock().getLocation();
        WorldSettingsManager worldSettings = plugin.getWorldSettingsManager();
        if (worldSettings != null && worldSettings.hasOverride(location.getWorld(), WorldSettingsManager.Rule.INTERACTIONS)) {
            if (!worldSettings.isEnabled(location.getWorld(), WorldSettingsManager.Rule.INTERACTIONS)) event.setCancelled(true);
            if (event.isCancelled()) return;
        }
        if ((worldSettings == null || !worldSettings.hasOverride(location.getWorld(), WorldSettingsManager.Rule.INTERACTIONS))
                && isStartWorld(location) && plugin.getConfig().getBoolean(startWorldPath() + ".protection.enabled", true)
                && plugin.getConfig().getBoolean(startWorldPath() + ".protection.interactions", true)) {
            event.setCancelled(true); return;
        }
        if (regionsAt(location).stream().anyMatch(region -> region.flag("deny-interact"))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) return;
        Player attacker = event.getDamager() instanceof Player player ? player
            : event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player ? player : null;
        if (attacker == null || isAdminBypass(attacker) || isAdminBypass(target)) return;
        WorldSettingsManager worldSettings = plugin.getWorldSettingsManager();
        boolean targetOverride = worldSettings != null
            && worldSettings.hasOverride(target.getWorld(), WorldSettingsManager.Rule.PVP);
        boolean attackerOverride = worldSettings != null
            && worldSettings.hasOverride(attacker.getWorld(), WorldSettingsManager.Rule.PVP);
        if ((targetOverride && !worldSettings.isEnabled(target.getWorld(), WorldSettingsManager.Rule.PVP))
                || (attackerOverride && !worldSettings.isEnabled(attacker.getWorld(), WorldSettingsManager.Rule.PVP))) {
            event.setCancelled(true);
            return;
        }
        boolean startWorldPvp = isStartWorld(target.getLocation()) || isStartWorld(attacker.getLocation());
        boolean hasStartOverride = (targetOverride && isStartWorld(target.getLocation()))
            || (attackerOverride && isStartWorld(attacker.getLocation()));
        if (startWorldPvp && !hasStartOverride
                && !plugin.getConfig().getBoolean(startWorldPath() + ".pvp-enabled", false)) {
            event.setCancelled(true); return;
        }
        if (regionsAt(target.getLocation()).stream().anyMatch(region -> region.flag("deny-pvp"))
                || regionsAt(attacker.getLocation()).stream().anyMatch(region -> region.flag("deny-pvp"))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        boolean pluginSpawn = reason == CreatureSpawnEvent.SpawnReason.CUSTOM
            || reason == CreatureSpawnEvent.SpawnReason.COMMAND;
        WorldSettingsManager worldSettings = plugin.getWorldSettingsManager();
        boolean mobOverride = worldSettings != null
            && worldSettings.hasOverride(event.getLocation().getWorld(), WorldSettingsManager.Rule.MOB_SPAWNING);
        if (mobOverride && !worldSettings.isEnabled(event.getLocation().getWorld(), WorldSettingsManager.Rule.MOB_SPAWNING)
                && !pluginSpawn) {
            event.setCancelled(true); return;
        }
        if (!mobOverride && isStartWorld(event.getLocation()) && !plugin.getConfig().getBoolean(startWorldPath() + ".mob-spawning-enabled", false)
                && !pluginSpawn) {
            event.setCancelled(true); return;
        }
        if (!pluginSpawn && worldSettings != null
                && isFriendlyMob(event.getEntity())
                && !worldSettings.isEnabled(event.getLocation().getWorld(),
                    WorldSettingsManager.Rule.FRIENDLY_MOB_SPAWNING)) {
            event.setCancelled(true);
            return;
        }
        if (regionsAt(event.getLocation()).stream().anyMatch(region -> region.flag("deny-mob-spawning"))) event.setCancelled(true);
    }

    private static boolean isFriendlyMob(Entity entity) {
        return entity instanceof Animals || entity instanceof AbstractVillager
            || entity instanceof IronGolem || entity instanceof Snowman || entity instanceof Allay;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) { applyStartWorldSettings(event.getWorld()); }

    public void applyStartWorldSettings() {
        World world = Bukkit.getWorld(plugin.getConfig().getString(startWorldPath() + ".name", "world_start"));
        if (world != null) applyStartWorldSettings(world);
    }

    private void applyStartWorldSettings(World world) {
        if (!world.getName().equalsIgnoreCase(plugin.getConfig().getString(startWorldPath() + ".name", "world_start"))) return;
        Boolean value = plugin.getConfig().getBoolean(startWorldPath() + ".time-passes", false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, value);
    }

    public boolean isHungerDrainDisabled(Player player) {
        WorldSettingsManager settings = plugin.getWorldSettingsManager();
        if (settings != null && settings.hasOverride(player.getWorld(), WorldSettingsManager.Rule.HUNGER))
            return !settings.isEnabled(player.getWorld(), WorldSettingsManager.Rule.HUNGER);
        if (isStartWorld(player.getLocation()) && !plugin.getConfig().getBoolean(startWorldPath() + ".hunger-drain-enabled", false)) return true;
        return regionsAt(player.getLocation()).stream().anyMatch(region -> region.flag("disable-hunger"));
    }

    public boolean isTimeProgressionDisabled(World world) {
        WorldSettingsManager settings = plugin.getWorldSettingsManager();
        if (settings != null && settings.hasOverride(world, WorldSettingsManager.Rule.TIME))
            return !settings.isEnabled(world, WorldSettingsManager.Rule.TIME);
        return world != null && world.getName().equalsIgnoreCase(
            plugin.getConfig().getString(startWorldPath() + ".name", "world_start"))
            && !plugin.getConfig().getBoolean(startWorldPath() + ".time-passes", false);
    }

    public boolean isThirstDrainDisabled(Player player) {
        WorldSettingsManager settings = plugin.getWorldSettingsManager();
        if (settings != null && settings.hasOverride(player.getWorld(), WorldSettingsManager.Rule.THIRST))
            return !settings.isEnabled(player.getWorld(), WorldSettingsManager.Rule.THIRST);
        if (isStartWorld(player.getLocation()) && !plugin.getConfig().getBoolean(startWorldPath() + ".thirst-drain-enabled", false)) return true;
        return regionsAt(player.getLocation()).stream().anyMatch(region -> region.flag("disable-thirst"));
    }

    public ProtectedRegion getRegion(String id) { return regions.get(normalizeId(id)); }

    public boolean upsertRegion(ProtectedRegion region) {
        String id = normalizeId(region.id);
        if (id == null || id.isBlank()) return false;
        ProtectedRegion previous = regions.put(id, region.withId(id));
        if (!save()) { if (previous == null) regions.remove(id); else regions.put(id, previous); return false; }
        return true;
    }

    public boolean upsertBuildRegion(String id, Location first, Location second, boolean protect) {
        if (!protect) return removeRegion(id) || getRegion(id) == null;
        if (first == null || second == null || first.getWorld() == null || !first.getWorld().equals(second.getWorld())) return false;
        ProtectedRegion region = new ProtectedRegion(id, first.getWorld().getName(),
            first.getBlockX(), first.getBlockY(), first.getBlockZ(),
            second.getBlockX(), second.getBlockY(), second.getBlockZ());
        region.setFlag("deny-block-break", true);
        region.setFlag("deny-block-place", true);
        return upsertRegion(region);
    }

    public boolean removeRegion(String id) {
        String key = normalizeId(id);
        ProtectedRegion previous = regions.remove(key);
        if (previous == null) return false;
        if (!save()) { regions.put(key, previous); return false; }
        return true;
    }

    public List<ProtectedRegion> allRegions() { return List.copyOf(regions.values()); }

    public void openRegionEditor(Player player, String id) {
        ProtectedRegion region = regions.get(normalizeId(id));
        if (region == null) { player.sendMessage("§cKhông tìm thấy vùng §f" + id + "§c."); return; }
        RegionHolder holder = new RegionHolder(player.getUniqueId(), "editor", region.id, 0);
        Inventory menu = Bukkit.createInventory(holder, 54, "WorldGuard • " + region.id);
        holder.inventory = menu;
        menu.setItem(4, icon(Material.IRON_BARS, "§b" + region.id, List.of("§7World: §f" + region.world,
            "§7Min: §f" + region.minX + " " + region.minY + " " + region.minZ,
            "§7Max: §f" + region.maxX + " " + region.maxY + " " + region.maxZ)));
        int[] slots = {10, 12, 14, 16, 20, 22, 24};
        for (int i = 0; i < FLAG_KEYS.length; i++) {
            boolean enabled = region.flag(FLAG_KEYS[i]);
            menu.setItem(slots[i], icon(enabled ? Material.RED_DYE : Material.LIME_DYE,
                (enabled ? "§cBẬT • " : "§aTẮT • ") + FLAG_NAMES[i], List.of("§7Nhấn để đổi flag vùng.")));
        }
        menu.setItem(45, icon(Material.TNT, "§cXóa vùng", List.of("§7Chỉ xóa cấu hình vùng, không sửa block.")));
        menu.setItem(49, icon(Material.ARROW, "§7Quay lại danh sách vùng", List.of()));
        player.openInventory(menu);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !(event.getView().getTopInventory().getHolder() instanceof RegionHolder holder)) return;
        event.setCancelled(true);
        if (!holder.viewer.equals(player.getUniqueId()) || event.getClickedInventory() != event.getView().getTopInventory()
                || !canManage(player)) return;
        int slot = event.getRawSlot();
        if ("list".equals(holder.view)) {
            if (slot == 4) giveWand(player);
            else if (slot == 8) beginCreateRegion(player);
            else if (slot == 46) sendWorldEditHelp(player);
            else if (slot == 47) openStartWorldSettings(player);
            else if (slot == 50) giveBrush(player);
            else if (slot == 51) giveClipboardPasteTool(player);
            else if (slot == 49) plugin.getMercenaryManager().openAdminMenu(player);
            else if (slot == 45 && holder.page > 0) openAdminMenu(player, holder.page - 1);
            else if (slot == 53) openAdminMenu(player, holder.page + 1);
            else if (holder.choices.containsKey(slot)) openRegionEditor(player, holder.choices.get(slot));
            return;
        }
        if ("start-world".equals(holder.view)) {
            if (slot == 49) openAdminMenu(player);
            else if (slot == 53) player.closeInventory();
            else {
                String path = switch (slot) {
                    case 10 -> startWorldPath() + ".protection.enabled";
                    case 12 -> startWorldPath() + ".protection.block-break";
                    case 14 -> startWorldPath() + ".protection.block-place";
                    case 16 -> startWorldPath() + ".protection.interactions";
                    case 20 -> startWorldPath() + ".pvp-enabled";
                    case 22 -> startWorldPath() + ".mob-spawning-enabled";
                    case 24 -> startWorldPath() + ".time-passes";
                    case 30 -> startWorldPath() + ".hunger-drain-enabled";
                    case 32 -> startWorldPath() + ".thirst-drain-enabled";
                    default -> null;
                };
                if (path != null) {
                    boolean value = !plugin.getConfig().getBoolean(path, startWorldDefault(slot));
                    plugin.getConfig().set(path, value);
                    plugin.saveConfig();
                    WorldSettingsManager settings = plugin.getWorldSettingsManager();
                    World startWorld = Bukkit.getWorld(plugin.getConfig().getString(startWorldPath() + ".name", "world_start"));
                    if (settings != null && startWorld != null) {
                        if (slot == 10) {
                            settings.clearOverride(startWorld, WorldSettingsManager.Rule.BLOCK_BREAK);
                            settings.clearOverride(startWorld, WorldSettingsManager.Rule.BLOCK_PLACE);
                            settings.clearOverride(startWorld, WorldSettingsManager.Rule.INTERACTIONS);
                        } else {
                            WorldSettingsManager.Rule rule = switch (slot) {
                                case 12 -> WorldSettingsManager.Rule.BLOCK_BREAK;
                                case 14 -> WorldSettingsManager.Rule.BLOCK_PLACE;
                                case 16 -> WorldSettingsManager.Rule.INTERACTIONS;
                                case 20 -> WorldSettingsManager.Rule.PVP;
                                case 22 -> WorldSettingsManager.Rule.MOB_SPAWNING;
                                case 24 -> WorldSettingsManager.Rule.TIME;
                                case 30 -> WorldSettingsManager.Rule.HUNGER;
                                case 32 -> WorldSettingsManager.Rule.THIRST;
                                default -> null;
                            };
                            if (rule != null) settings.clearOverride(startWorld, rule);
                        }
                    }
                    applyStartWorldSettings();
                    plugin.getLogger().info("[WorldRegion] admin=" + player.getName() + ", action=" + path + ", value=" + value);
                    openStartWorldSettings(player);
                }
            }
            return;
        }
        if ("editor".equals(holder.view)) {
            ProtectedRegion region = regions.get(holder.regionId);
            if (region == null) { openAdminMenu(player); return; }
            if (slot == 49) openAdminMenu(player);
            else if (slot == 45) {
                removeRegion(region.id);
                player.sendMessage("§eĐã xóa vùng §f" + region.id + "§e; block trong vùng không bị thay đổi.");
                openAdminMenu(player);
            } else {
                int[] slots = {10, 12, 14, 16, 20, 22, 24};
                for (int i = 0; i < slots.length; i++) if (slots[i] == slot) {
                    region.setFlag(FLAG_KEYS[i], !region.flag(FLAG_KEYS[i]));
                    upsertRegion(region);
                    openRegionEditor(player, region.id);
                    break;
                }
            }
        }
    }

    @EventHandler
    public void onNameInput(AsyncPlayerChatEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Clipboard pendingClipboard = pendingSchemaSaves.remove(playerId);
        if (pendingClipboard != null) {
            event.setCancelled(true);
            String input = event.getMessage().trim();
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) return;
                if (!canManage(player)) { player.sendMessage("§cBạn không còn quyền WorldEdit."); return; }
                if ("!cancel".equalsIgnoreCase(input)) { player.sendMessage("§eĐã hủy lưu schematic."); return; }
                try { saveClipboardSchema(player, pendingClipboard, input); }
                catch (IllegalArgumentException exception) {
                    pendingSchemaSaves.put(playerId, pendingClipboard);
                    player.sendMessage("§c" + exception.getMessage() + " Nhập lại đường dẫn hoặc !cancel.");
                }
            });
            return;
        }
        String mode = pendingNames.remove(event.getPlayer().getUniqueId());
        if (mode == null) return;
        event.setCancelled(true);
        String name = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = event.getPlayer();
            if (!player.isOnline()) return;
            if ("!cancel".equalsIgnoreCase(name)) { openAdminMenu(player); return; }
            if (!name.matches("[A-Za-z0-9_-]{1,48}")) {
                player.sendMessage("§cTên vùng chỉ gồm chữ, số, _ hoặc -, tối đa 48 ký tự. Nhập lại hoặc !cancel.");
                pendingNames.put(player.getUniqueId(), "create"); return;
            }
            Selection selection = selections.get(player.getUniqueId());
            if (selection == null || !selection.complete()) { player.sendMessage("§cLựa chọn vùng chưa đủ hai góc."); openAdminMenu(player); return; }
            ProtectedRegion region = new ProtectedRegion(name.toLowerCase(Locale.ROOT), selection.first.getWorld().getName(),
                selection.minX(), selection.minY(), selection.minZ(), selection.maxX(), selection.maxY(), selection.maxZ());
            if (regions.containsKey(region.id)) { player.sendMessage("§cID vùng này đã tồn tại."); openRegionEditor(player, region.id); return; }
            if (!upsertRegion(region)) { player.sendMessage("§cKhông thể lưu vùng; dữ liệu trước đó được giữ lại."); return; }
            plugin.getLogger().info("[WorldRegion] region-created admin=" + player.getName() + ", id=" + region.id
                + ", world=" + region.world + ", bounds=" + region.minX + "," + region.minY + "," + region.minZ
                + ".." + region.maxX + "," + region.maxY + "," + region.maxZ);
            openRegionEditor(player, region.id);
        });
    }

    private void beginCreateRegion(Player player) {
        Selection selection = selections.get(player.getUniqueId());
        if (selection == null || !selection.complete()) {
            player.sendMessage("§cChọn hai góc cùng world bằng búa gỗ trước."); return;
        }
        pendingNames.put(player.getUniqueId(), "create");
        player.closeInventory();
        player.sendMessage("§eNhập ID vùng, ví dụ §fspawn_safe§e. Chỉ chữ/số/_/-, tối đa 48 ký tự; §f!cancel §ehủy.");
    }

    public void toggleStartWorldSetting(Player player, String path) {
        boolean value = !plugin.getConfig().getBoolean(path, defaultStartWorldValue(path));
        plugin.getConfig().set(path, value);
        plugin.saveConfig();
        applyStartWorldSettings();
        plugin.getLogger().info("[WorldRegion] admin=" + player.getName() + ", action=" + path + ", value=" + value);
    }

    private List<ProtectedRegion> regionsAt(Location location) {
        if (location == null || location.getWorld() == null) return List.of();
        String world = location.getWorld().getName();
        return regions.values().stream().filter(region -> region.contains(world, location.getBlockX(),
            location.getBlockY(), location.getBlockZ())).toList();
    }

    private boolean isStartWorld(Location location) {
        return location != null && location.getWorld() != null
            && location.getWorld().getName().equalsIgnoreCase(plugin.getConfig().getString(startWorldPath() + ".name", "world_start"));
    }

    private String startWorldPath() { return "rpg.onboarding.start-world"; }
    private boolean startWorldDefault(int slot) {
        return switch (slot) {
            case 10, 12, 14, 16 -> true;
            default -> false;
        };
    }
    private boolean defaultStartWorldValue(String path) {
        return path.endsWith(".protection.enabled") || path.endsWith(".protection.block-break")
            || path.endsWith(".protection.block-place") || path.endsWith(".protection.interactions");
    }
    private boolean canManage(Player player) { return player.isOp() || player.hasPermission(ADMIN_PERMISSION); }
    private boolean isAdminBypass(Player player) { return canManage(player); }

    private String selectionText(Selection selection) {
        if (selection == null) return "§cchưa có";
        return (selection.first == null ? "§c?" : "§a1") + " / "
            + (selection.second == null ? "§c?" : "§a2");
    }

    private ItemStack icon(Material material, String title, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(title); meta.setLore(lore); item.setItemMeta(meta);
        return item;
    }

    private String normalizeId(String id) {
        if (id == null) return null;
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        return normalized.matches("[a-z0-9_-]{1,48}") ? normalized : null;
    }

    private void load() {
        if (!dataFile.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection entries = yaml.getConfigurationSection("regions");
        if (entries == null) return;
        for (String id : entries.getKeys(false)) {
            String path = "regions." + id;
            String world = yaml.getString(path + ".world", "");
            int minX = yaml.getInt(path + ".min-x"), minY = yaml.getInt(path + ".min-y"), minZ = yaml.getInt(path + ".min-z");
            int maxX = yaml.getInt(path + ".max-x"), maxY = yaml.getInt(path + ".max-y"), maxZ = yaml.getInt(path + ".max-z");
            if (normalizeId(id) == null || world.isBlank() || minX > maxX || minY > maxY || minZ > maxZ) {
                plugin.getLogger().warning("[WorldRegion] Skipping invalid region " + id); continue;
            }
            ProtectedRegion region = new ProtectedRegion(id, world, minX, minY, minZ, maxX, maxY, maxZ);
            for (String flag : FLAG_KEYS) region.setFlag(flag, yaml.getBoolean(path + ".flags." + flag, false));
            regions.put(region.id, region);
        }
        plugin.getLogger().info("[WorldRegion] Loaded " + regions.size() + " protected region(s).");
    }

    private boolean save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (ProtectedRegion region : regions.values()) {
            String path = "regions." + region.id;
            yaml.set(path + ".world", region.world);
            yaml.set(path + ".min-x", region.minX); yaml.set(path + ".min-y", region.minY); yaml.set(path + ".min-z", region.minZ);
            yaml.set(path + ".max-x", region.maxX); yaml.set(path + ".max-y", region.maxY); yaml.set(path + ".max-z", region.maxZ);
            for (String flag : FLAG_KEYS) yaml.set(path + ".flags." + flag, region.flag(flag));
        }
        try { yaml.save(dataFile); return true; }
        catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "[WorldRegion] Could not save " + dataFile.getName(), exception);
            return false;
        }
    }

    public static final class ProtectedRegion {
        private final String id;
        private final String world;
        private final int minX, minY, minZ, maxX, maxY, maxZ;
        private final Map<String, Boolean> flags = new HashMap<>();
        public ProtectedRegion(String id, String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.id = id; this.world = world;
            this.minX = Math.min(minX, maxX); this.minY = Math.min(minY, maxY); this.minZ = Math.min(minZ, maxZ);
            this.maxX = Math.max(minX, maxX); this.maxY = Math.max(minY, maxY); this.maxZ = Math.max(minZ, maxZ);
        }
        private ProtectedRegion withId(String newId) {
            ProtectedRegion copy = new ProtectedRegion(newId, world, minX, minY, minZ, maxX, maxY, maxZ);
            copy.flags.putAll(flags); return copy;
        }
        public String id() { return id; }
        public String world() { return world; }
        public boolean flag(String name) { return flags.getOrDefault(name, false); }
        public void setFlag(String name, boolean value) { flags.put(name, value); }
        public int minX() { return minX; } public int minY() { return minY; } public int minZ() { return minZ; }
        public int maxX() { return maxX; } public int maxY() { return maxY; } public int maxZ() { return maxZ; }
        private boolean contains(String worldName, int x, int y, int z) {
            return world.equalsIgnoreCase(worldName) && x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
    }

    private static final class Selection {
        private Location first, second;
        private RegionShape shape = RegionShape.CUBOID;
        private void setFirst(Location value) { first = value.clone(); }
        private void setSecond(Location value) { second = value.clone(); }
        private boolean complete() { return first != null && second != null && first.getWorld().equals(second.getWorld()); }
        private int minX() { return Math.min(first.getBlockX(), second.getBlockX()); }
        private int minY() { return Math.min(first.getBlockY(), second.getBlockY()); }
        private int minZ() { return Math.min(first.getBlockZ(), second.getBlockZ()); }
        private int maxX() { return Math.max(first.getBlockX(), second.getBlockX()); }
        private int maxY() { return Math.max(first.getBlockY(), second.getBlockY()); }
        private int maxZ() { return Math.max(first.getBlockZ(), second.getBlockZ()); }
        private boolean contains(int x, int y, int z) {
            if (x < minX() || x > maxX() || y < minY() || y > maxY() || z < minZ() || z > maxZ()) return false;
            if (shape == RegionShape.CUBOID) return true;
            double rx = (maxX() - minX() + 1) / 2.0;
            double ry = (maxY() - minY() + 1) / 2.0;
            double rz = (maxZ() - minZ() + 1) / 2.0;
            double radius = shape == RegionShape.SPHERE ? Math.min(rx, Math.min(ry, rz)) : 0.0;
            if (shape == RegionShape.SPHERE) rx = ry = rz = radius;
            double cx = (minX() + maxX() + 1) / 2.0;
            double cy = (minY() + maxY() + 1) / 2.0;
            double cz = (minZ() + maxZ() + 1) / 2.0;
            double dx = (x + 0.5 - cx) / rx;
            double dy = (y + 0.5 - cy) / ry;
            double dz = (z + 0.5 - cz) / rz;
            return dx * dx + dy * dy + dz * dz <= 1.0;
        }
        private long volume() { return ((long) maxX() - minX() + 1) * ((long) maxY() - minY() + 1) * ((long) maxZ() - minZ() + 1); }
        private boolean allChunksLoaded() {
            World world = first.getWorld();
            for (int x = minX() >> 4; x <= maxX() >> 4; x++) for (int z = minZ() >> 4; z <= maxZ() >> 4; z++)
                if (!world.isChunkLoaded(x, z)) return false;
            return true;
        }
    }

    private enum RegionShape { CUBOID, SPHERE, ELLIPSOID }

    private enum Shape {
        WALLS("walls"), FACES("faces");
        private final String command;
        Shape(String command) { this.command = command; }
    }

    private record WeightedBlock(String blockData, double weight) { }
    private record BrushConfig(String kind, int radius, String blockData) { }
    private record BeforeTreeBlock(String data, BlockState state) { }
    private record BlockPosition(int x, int y, int z) {
        private BlockPosition offset(int dx, int dy, int dz) { return new BlockPosition(x + dx, y + dy, z + dz); }
    }
    private record BlockChange(Location location, String before, String after, BlockState beforeState,
                               BlockState afterState, Map<String, Object> afterNbt) { }
    private record ChunkTicket(World world, int x, int z) { }
    private static final class EditOperation {
        private final String label;
        private List<BlockChange> changes;
        private final boolean refreshConnections;
        private EditOperation(String label, List<BlockChange> changes, boolean refreshConnections) {
            this.label = label;
            this.changes = changes;
            this.refreshConnections = refreshConnections;
        }
    }
    private record ClipboardBlock(int x, int y, int z, String blockData, BlockState blockState,
                                  Map<String, Object> blockEntityData) { }
    private record Clipboard(List<ClipboardBlock> blocks, int sizeX, int sizeY, int sizeZ, Location origin,
                             int minOffsetX, int minOffsetY, int minOffsetZ) {
        private Clipboard(List<ClipboardBlock> blocks, int sizeX, int sizeY, int sizeZ) {
            this(blocks, sizeX, sizeY, sizeZ, null, 0, 0, 0);
        }
    }
    private static final class BrushPreview {
        private final Clipboard clipboard;
        private final List<ClipboardBlock> pattern;
        private final int surfaceLayer;
        private final List<BlockDisplay> displays = new ArrayList<>();
        private Location anchor;
        private BrushPreview(Clipboard clipboard, List<ClipboardBlock> pattern, int surfaceLayer) {
            this.clipboard = clipboard;
            this.pattern = pattern;
            this.surfaceLayer = surfaceLayer;
        }
    }
    private static final class PastePreview {
        private final Clipboard clipboard;
        private final List<BlockDisplay> displays = new ArrayList<>();
        private List<ClipboardBlock> transformedBlocks;
        private int surfaceLayer;
        private int rotation;
        private Location anchor;
        private PastePreview(Clipboard clipboard, List<ClipboardBlock> transformedBlocks, int surfaceLayer) {
            this.clipboard = clipboard;
            this.transformedBlocks = transformedBlocks;
            this.surfaceLayer = surfaceLayer;
        }
    }
    @FunctionalInterface private interface BlockMatcher { boolean matches(BlockData data); }
    private record BlockPattern(List<WeightedBlock> choices, double totalWeight) {
        private BlockData next(Random random) {
            double selected = random.nextDouble(totalWeight);
            for (WeightedBlock choice : choices) {
                selected -= choice.weight();
                if (selected < 0) return Bukkit.createBlockData(choice.blockData());
            }
            return Bukkit.createBlockData(choices.get(choices.size() - 1).blockData());
        }
    }

    private static final class RegionHolder implements InventoryHolder {
        private final UUID viewer; private final String view; private final String regionId; private final int page;
        private final Map<Integer, String> choices = new HashMap<>(); private Inventory inventory;
        private RegionHolder(UUID viewer, String view, String regionId, int page) {
            this.viewer = viewer; this.view = view; this.regionId = regionId; this.page = page;
        }
        @Override public Inventory getInventory() { return inventory; }
    }
}
