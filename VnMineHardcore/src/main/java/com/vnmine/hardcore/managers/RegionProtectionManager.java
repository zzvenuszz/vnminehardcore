package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
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
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/** Selection wand, named region flags, and start-world safety settings. */
public final class RegionProtectionManager implements Listener, CommandExecutor, TabCompleter {
    public static final String ADMIN_PERMISSION = "vnmine.command.worldedit";
    private static final int EDIT_BATCH_SIZE = 512;
    private static final int DEFAULT_MAX_EDIT_BLOCKS = 32_768;
    private static final int DEFAULT_HISTORY_LIMIT = 10;
    private static final int DEFAULT_MAX_EDIT_CHUNKS = 256;
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
    private final File dataFile;
    private final Map<String, ProtectedRegion> regions = new LinkedHashMap<>();
    private final Map<UUID, Selection> selections = new HashMap<>();
    private final Map<UUID, String> pendingNames = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Deque<EditOperation>> undoHistory = new HashMap<>();
    private final Map<UUID, Deque<EditOperation>> redoHistory = new HashMap<>();
    private final Map<UUID, Clipboard> clipboards = new HashMap<>();
    private final Set<UUID> activeEdits = new HashSet<>();

    public RegionProtectionManager(VnMineHardcore plugin) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey(plugin, "worldedit_region_wand");
        this.dataFile = new File(plugin.getDataFolder(), "protected-regions.yml");
        load();
        for (World world : Bukkit.getWorlds()) applyStartWorldSettings(world);
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
        if (args.length == 0) { openAdminMenu(player, 0); return true; }
        String operation = args[0].replaceFirst("^/+", "").toLowerCase(Locale.ROOT);
        if ("wand".equals(operation)) { giveWand(player); return true; }
        executeWorldEditCommand(player, operation, java.util.Arrays.copyOfRange(args, 1, args.length));
        return true;
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("wand", "help", "pos1", "pos2", "hpos1", "hpos2", "chunk", "size",
            "desel", "set", "replace", "walls", "faces", "overlay", "center", "count", "expand", "contract",
            "shift", "move", "stack", "copy", "cut", "paste", "undo", "redo", "clearhistory", "limit");
        if (args.length == 2 && Set.of("set", "walls", "faces", "overlay", "center", "cut").contains(args[0].toLowerCase(Locale.ROOT)))
            return blockSuggestions(args[1]);
        if (args.length == 3 && Set.of("replace", "cut").contains(args[0].toLowerCase(Locale.ROOT)))
            return blockSuggestions(args[2]);
        return List.of();
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
        undoHistory.remove(id); redoHistory.remove(id); clipboards.remove(id); selections.remove(id); pendingNames.remove(id);
    }

    public void openAdminMenu(Player player) { openAdminMenu(player, 0); }

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
            "§7//set <block> • //replace <from> <to>", "§7//walls <block> • //faces <block> • //overlay <block>",
            "§7//copy • //cut • //paste [-a] • //undo • //redo", "§eGõ //help để xem hướng dẫn.")));
        if (page > 0) menu.setItem(45, icon(Material.ARROW, "§eTrang trước", List.of()));
        menu.setItem(47, icon(Material.SUNFLOWER, "§bCài đặt World bắt đầu/reset", List.of(
            "§7Bảo vệ block, tương tác, PvP, quái, thời gian, đói và khát.")));
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
                case "chunk" -> selectChunk(player);
                case "size" -> reportSelectionSize(player);
                case "desel", "deselect" -> { selections.remove(player.getUniqueId()); player.sendMessage("§eĐã bỏ vùng chọn."); }
                case "set" -> editSet(player, args);
                case "replace", "re", "rep" -> editReplace(player, args);
                case "walls" -> editShape(player, args, Shape.WALLS);
                case "faces", "outline" -> editShape(player, args, Shape.FACES);
                case "overlay" -> editOverlay(player, args);
                case "center", "middle" -> editCenter(player, args);
                case "count" -> countBlocks(player, args);
                case "expand" -> adjustSelection(player, args, true);
                case "contract" -> adjustSelection(player, args, false);
                case "shift" -> shiftSelection(player, args);
                case "move" -> moveSelection(player, args);
                case "stack" -> stackSelection(player, args);
                case "copy" -> copySelection(player);
                case "cut" -> cutSelection(player, args);
                case "paste" -> pasteClipboard(player, args);
                case "undo" -> applyHistory(player, false);
                case "redo" -> applyHistory(player, true);
                case "clearhistory" -> { undoHistory.remove(player.getUniqueId()); redoHistory.remove(player.getUniqueId()); player.sendMessage("§eĐã xóa lịch sử undo/redo."); }
                case "limit" -> setEditLimit(player, args);
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
        player.sendMessage("§6WorldEdit tích hợp §7• chọn bằng búa hoặc //pos1 //pos2 //hpos1 //hpos2 //chunk");
        player.sendMessage("§e//set <block> §7• §e//replace <from> <to> §7• §e//walls <block> §7• §e//faces <block>");
        player.sendMessage("§e//overlay <block> §7• §e//center <block> §7• §e//count <block> §7• §e//cut [block]");
        player.sendMessage("§e//copy §7• §e//paste [-a] §7• §e//undo §7• §e//redo §7• §e//size §7• §e//desel");
        player.sendMessage("§7Dùng //clearhistory để xóa lịch sử undo/redo hiện tại.");
        player.sendMessage("§7Điều chỉnh vùng: //expand <n> [up|down|north|south|east|west|vert], //contract <n> [hướng], //shift <n> [hướng].");
        player.sendMessage("§7//move <n> [hướng] [block_để_lại] • //stack <số_bản> [hướng]. Clipboard/undo chỉ lưu trong phiên.");
        player.sendMessage("§7Block nhận ID như §fminecraft:stone§7 hoặc block state §fminecraft:oak_log[axis=x]§7. Giới hạn: §f"
            + editLimit() + " §7block/thao tác. Clipboard và history theo phiên người chơi.");
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
        if (args.length == 0 || command.startsWith("hpos")) {
            point = player.getTargetBlockExact(256) == null ? null : player.getTargetBlockExact(256).getLocation();
        } else {
            int[] xyz = parseCoordinates(args);
            point = new Location(player.getWorld(), xyz[0], xyz[1], xyz[2]);
        }
        if (point == null) { player.sendMessage("§cKhông thấy block đích trong 256 block."); return; }
        Selection selection = selections.computeIfAbsent(player.getUniqueId(), ignored -> new Selection());
        if (command.endsWith("1")) selection.setFirst(point); else selection.setSecond(point);
        player.sendMessage("§aĐiểm " + (command.endsWith("1") ? "1" : "2") + ": §f" + point.getBlockX() + " "
            + point.getBlockY() + " " + point.getBlockZ() + " §7(" + selectionText(selection) + ")");
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
        if (enforceLimit && volume > editLimit()) throw new IllegalArgumentException("Vùng có " + volume + " block; giới hạn là " + editLimit() + ". Dùng //contract hoặc //limit.");
        if (enforceLimit && (selection.minY() < selection.first.getWorld().getMinHeight()
                || selection.maxY() >= selection.first.getWorld().getMaxHeight()
                || !selection.first.getWorld().getWorldBorder().isInside(selection.first)
                || !selection.first.getWorld().getWorldBorder().isInside(selection.second)))
            throw new IllegalArgumentException("Vùng vượt giới hạn chiều cao hoặc world border.");
        if (enforceLimit && !selection.allChunksLoaded()) throw new IllegalArgumentException("Một phần vùng nằm trong chunk chưa nạp; hãy tới gần và nạp toàn bộ vùng trước.");
        return selection;
    }

    private void editSet(Player player, String[] args) {
        if (args.length != 1) throw new IllegalArgumentException("Dùng //set <block>, ví dụ //set minecraft:stone.");
        Selection selection = requireSelection(player, true);
        BlockPattern pattern = parsePattern(args[0]);
        Random random = new Random();
        List<BlockChange> changes = new ArrayList<>();
        for (int x = selection.minX(); x <= selection.maxX(); x++) for (int y = selection.minY(); y <= selection.maxY(); y++)
            for (int z = selection.minZ(); z <= selection.maxZ(); z++) addChange(player, changes,
                selection.first.getWorld().getBlockAt(x, y, z), pattern.next(random));
        scheduleEdit(player, "//set", changes);
    }

    private void editReplace(Player player, String[] args) {
        if (args.length != 2) throw new IllegalArgumentException("Dùng //replace <block_cũ> <block_mới>, ví dụ //replace minecraft:dirt minecraft:stone.");
        Selection selection = requireSelection(player, true);
        BlockMatcher matcher = parseMatcher(args[0]);
        BlockPattern replacement = parsePattern(args[1]);
        Random random = new Random(); List<BlockChange> changes = new ArrayList<>();
        for (int x = selection.minX(); x <= selection.maxX(); x++) for (int y = selection.minY(); y <= selection.maxY(); y++)
            for (int z = selection.minZ(); z <= selection.maxZ(); z++) {
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
                boolean edge = x == selection.minX() || x == selection.maxX() || y == selection.minY() || y == selection.maxY()
                    || z == selection.minZ() || z == selection.maxZ();
                boolean wall = x == selection.minX() || x == selection.maxX() || z == selection.minZ() || z == selection.maxZ();
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
        for (int x : xs) for (int y : ys) for (int z : zs) addChange(player, changes,
            selection.first.getWorld().getBlockAt(x, y, z), pattern.next(random));
        scheduleEdit(player, "//center", changes);
    }

    private int[] centerCoordinates(int min, int max) {
        int low = min + (max - min) / 2;
        return (max - min + 1) % 2 == 0 ? new int[]{low, low + 1} : new int[]{low};
    }

    private void countBlocks(Player player, String[] args) {
        if (args.length != 1) throw new IllegalArgumentException("Dùng //count <block>.");
        Selection selection = requireSelection(player, true); BlockMatcher matcher = parseMatcher(args[0]); long count = 0;
        for (int x = selection.minX(); x <= selection.maxX(); x++) for (int y = selection.minY(); y <= selection.maxY(); y++)
            for (int z = selection.minZ(); z <= selection.maxZ(); z++) if (matcher.matches(selection.first.getWorld().getBlockAt(x, y, z).getBlockData())) count++;
        player.sendMessage("§aTìm thấy §f" + count + "§a block khớp §f" + args[0] + "§a.");
    }

    private void adjustSelection(Player player, String[] args, boolean expand) {
        if (args.length < 1 || args.length > 2) throw new IllegalArgumentException("Dùng //" + (expand ? "expand" : "contract") + " <số> [hướng].");
        int amount = positiveInt(args[0]); Selection selection = requireSelection(player, false);
        if (amount > editLimit()) throw new IllegalArgumentException("Khoảng mở rộng tối đa một lần là giới hạn block hiện tại: " + editLimit() + ".");
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
        if (Math.abs((long) amount) > editLimit()) throw new IllegalArgumentException("Dịch chuyển tối đa một lần là " + editLimit() + " block.");
        int[] vector = directionVector(args[1]); selection.first.add(vector[0] * amount, vector[1] * amount, vector[2] * amount);
        selection.second.add(vector[0] * amount, vector[1] * amount, vector[2] * amount);
        player.sendMessage("§aĐã dịch chuyển vùng chọn " + amount + " block.");
    }

    private void moveSelection(Player player, String[] args) {
        if (args.length < 1 || args.length > 3) throw new IllegalArgumentException("Dùng //move <số> [hướng] [block_để_lại].");
        int amount = positiveInt(args[0]);
        if (amount > editLimit()) throw new IllegalArgumentException("Khoảng move vượt giới hạn block.");
        int[] direction = args.length >= 2 ? directionVector(args[1]) : directionVector(player.getFacing().name());
        BlockData leave;
        try { leave = Bukkit.createBlockData(args.length == 3 ? args[2] : "minecraft:air"); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Block để lại không hợp lệ."); }
        Selection selection = requireSelection(player, true); World world = selection.first.getWorld();
        Map<BlockPosition, String> source = new LinkedHashMap<>();
        for (int x = selection.minX(); x <= selection.maxX(); x++) for (int y = selection.minY(); y <= selection.maxY(); y++)
            for (int z = selection.minZ(); z <= selection.maxZ(); z++) {
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
        List<Map.Entry<BlockPosition, String>> source = new ArrayList<>((int) selection.volume());
        for (int x = selection.minX(); x <= selection.maxX(); x++) for (int y = selection.minY(); y <= selection.maxY(); y++)
            for (int z = selection.minZ(); z <= selection.maxZ(); z++) {
                Block block = world.getBlockAt(x, y, z);
                if (block.getState() instanceof org.bukkit.block.TileState) throw new IllegalArgumentException("Stack vùng có rương/bảng hiệu bị từ chối để tránh mất dữ liệu.");
                source.add(Map.entry(new BlockPosition(x, y, z), block.getBlockData().getAsString()));
            }
        long total = (long) source.size() * copies;
        if (total > editLimit()) throw new IllegalArgumentException("Stack cần " + total + " block; giới hạn hiện tại " + editLimit() + ".");
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
        List<ClipboardBlock> blocks = new ArrayList<>((int) selection.volume());
        for (int x = selection.minX(); x <= selection.maxX(); x++) for (int y = selection.minY(); y <= selection.maxY(); y++)
            for (int z = selection.minZ(); z <= selection.maxZ(); z++) {
                Block block = selection.first.getWorld().getBlockAt(x, y, z);
                if (block.getState() instanceof org.bukkit.block.TileState) throw new IllegalArgumentException("Clipboard hiện không lưu nội dung rương/sign; đã hủy copy an toàn.");
                blocks.add(new ClipboardBlock(x - origin.getBlockX(), y - origin.getBlockY(), z - origin.getBlockZ(), block.getBlockData().getAsString()));
            }
        clipboards.put(player.getUniqueId(), new Clipboard(List.copyOf(blocks), selection.maxX() - selection.minX() + 1,
            selection.maxY() - selection.minY() + 1, selection.maxZ() - selection.minZ() + 1, origin));
        player.sendMessage("§aĐã copy §f" + blocks.size() + "§a block theo vị trí đứng làm gốc.");
        plugin.getLogger().info("[WorldRegion] clipboard-copy player=" + player.getName() + ", blocks=" + blocks.size()
            + ", origin=" + origin.getWorld().getName() + ":" + origin.getBlockX() + "," + origin.getBlockY() + "," + origin.getBlockZ());
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
            addChange(player, changes, world.getBlockAt(x, y, z), data);
        }
        if (changes.size() > editLimit()) throw new IllegalArgumentException("Paste vượt giới hạn " + editLimit() + " block.");
        scheduleEdit(player, "//paste", changes);
    }

    private BlockPattern parsePattern(String raw) {
        List<WeightedBlock> choices = new ArrayList<>(); double total = 0;
        for (String entry : raw.split(",")) {
            String value = entry.trim(); double weight = 1.0;
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^(\\d+(?:\\.\\d+)?)%(.+)$").matcher(value);
            if (matcher.matches()) { weight = Double.parseDouble(matcher.group(1)); value = matcher.group(2); }
            if (!(weight > 0) || !Double.isFinite(weight)) throw new IllegalArgumentException("Trọng số pattern phải lớn hơn 0.");
            BlockData data;
            try { data = Bukkit.createBlockData(value); }
            catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Block ID hoặc block state không hợp lệ: " + value); }
            if (!data.getMaterial().isBlock()) throw new IllegalArgumentException("Đây không phải block: " + value);
            choices.add(new WeightedBlock(data.getAsString(), weight)); total += weight;
        }
        if (choices.isEmpty()) throw new IllegalArgumentException("Pattern block đang trống.");
        return new BlockPattern(List.copyOf(choices), total);
    }

    private BlockMatcher parseMatcher(String raw) {
        BlockData data;
        try { data = Bukkit.createBlockData(raw); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Block ID không hợp lệ: " + raw); }
        boolean exactState = raw.contains("[");
        return candidate -> candidate.getMaterial() == data.getMaterial() && (!exactState || candidate.matches(data));
    }

    private void addChange(Player player, List<BlockChange> changes, Block block, BlockData target) {
        BlockData current = block.getBlockData();
        if (current.getAsString().equals(target.getAsString())) return;
        if (block.getState() instanceof org.bukkit.block.TileState)
            throw new IllegalArgumentException("Vùng chứa block entity (rương, bảng hiệu...) tại " + block.getX() + "," + block.getY() + "," + block.getZ()
                + "; chỉnh sửa bị hủy để tránh mất dữ liệu.");
        changes.add(new BlockChange(block.getLocation(), current.getAsString(), target.getAsString()));
        if (changes.size() > editLimit()) throw new IllegalArgumentException("Thao tác vượt giới hạn " + editLimit() + " block.");
    }

    private void scheduleEdit(Player player, String label, List<BlockChange> changes) {
        if (changes.isEmpty()) { player.sendMessage("§eKhông có block nào cần thay đổi."); return; }
        UUID id = player.getUniqueId();
        List<ChunkTicket> tickets = acquireChunkTickets(changes);
        activeEdits.add(id);
        EditOperation operation = new EditOperation(label, List.copyOf(changes));
        player.sendMessage("§eĐang áp dụng " + changes.size() + " block theo từng đợt. Đừng thoát world cho tới khi xong.");
        plugin.getLogger().info("[WorldRegion] edit-start player=" + player.getName() + ", operation=" + label + ", blocks=" + changes.size());
        final int[] index = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            try {
                int end = Math.min(operation.changes.size(), index[0] + editBatchSize());
                while (index[0] < end) {
                    BlockChange change = operation.changes.get(index[0]++);
                    change.location.getBlock().setBlockData(Bukkit.createBlockData(change.after), false);
                }
                if (index[0] >= operation.changes.size()) {
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
                    change.location.getBlock().setBlockData(Bukkit.createBlockData(change.before), false);
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
            if (change.location.getBlock().getState() instanceof org.bukkit.block.TileState) {
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
                    change.location.getBlock().setBlockData(Bukkit.createBlockData(redo ? change.after : change.before), false);
                }
                if (index[0] >= operation.changes.size()) {
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
                    change.location.getBlock().setBlockData(Bukkit.createBlockData(redo ? change.before : change.after), false);
                }
                source.push(operation); releaseChunkTickets(tickets);
                plugin.getLogger().log(Level.SEVERE, "[WorldRegion] history operation failed and rolled back player=" + player.getName(), exception);
                if (player.isOnline()) player.sendMessage("§cUndo/redo gặp lỗi; phần đã làm được hoàn tác.");
            }
        }, 1L, 1L);
    }

    private void setEditLimit(Player player, String[] args) {
        if (args.length != 1) { player.sendMessage("§7Giới hạn hiện tại: " + editLimit() + ". Dùng //limit <1..250000>."); return; }
        int limit = positiveInt(args[0]);
        if (limit > 250_000) throw new IllegalArgumentException("Giới hạn lớn nhất là 250000 block.");
        plugin.getConfig().set("worldedit.max-blocks", limit); plugin.saveConfig();
        player.sendMessage("§aĐã lưu giới hạn thao tác: §f" + limit + "§a block.");
    }

    private int editLimit() { return Math.max(1, Math.min(250_000, plugin.getConfig().getInt("worldedit.max-blocks", DEFAULT_MAX_EDIT_BLOCKS))); }
    private int historyLimit() { return Math.max(1, Math.min(50, plugin.getConfig().getInt("worldedit.history-size", DEFAULT_HISTORY_LIMIT))); }
    private int editBatchSize() { return Math.max(32, Math.min(4096, plugin.getConfig().getInt("worldedit.blocks-per-tick", EDIT_BATCH_SIZE))); }

    private List<ChunkTicket> acquireChunkTickets(List<BlockChange> changes) {
        Map<String, ChunkTicket> chunks = new LinkedHashMap<>();
        for (BlockChange change : changes) {
            World world = change.location.getWorld();
            int chunkX = change.location.getBlockX() >> 4, chunkZ = change.location.getBlockZ() >> 4;
            chunks.putIfAbsent(world.getUID() + ":" + chunkX + ":" + chunkZ, new ChunkTicket(world, chunkX, chunkZ));
        }
        int maximum = Math.max(1, Math.min(2048, plugin.getConfig().getInt("worldedit.max-chunks-per-operation", DEFAULT_MAX_EDIT_CHUNKS)));
        if (chunks.size() > maximum) throw new IllegalArgumentException("Thao tác đi qua " + chunks.size() + " chunk; giới hạn là " + maximum + ". Chia vùng nhỏ hơn.");
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
            "§7//copy / //paste / //undo / //redo", "§7/vnregion để quản lý vùng và flags."));
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isAdminBypass(event.getPlayer())) return;
        Location location = event.getBlock().getLocation();
        if (isStartWorld(location) && plugin.getConfig().getBoolean(startWorldPath() + ".protection.enabled", true)
                && plugin.getConfig().getBoolean(startWorldPath() + ".protection.block-break", true)) {
            event.setCancelled(true); return;
        }
        if (regionsAt(location).stream().anyMatch(region -> region.flag("deny-block-break"))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isAdminBypass(event.getPlayer())) return;
        Location location = event.getBlockPlaced().getLocation();
        if (isStartWorld(location) && plugin.getConfig().getBoolean(startWorldPath() + ".protection.enabled", true)
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
        if (isStartWorld(location) && plugin.getConfig().getBoolean(startWorldPath() + ".protection.enabled", true)
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
        boolean startWorldPvp = isStartWorld(target.getLocation()) || isStartWorld(attacker.getLocation());
        if (startWorldPvp && !plugin.getConfig().getBoolean(startWorldPath() + ".pvp-enabled", false)) {
            event.setCancelled(true); return;
        }
        if (regionsAt(target.getLocation()).stream().anyMatch(region -> region.flag("deny-pvp"))
                || regionsAt(attacker.getLocation()).stream().anyMatch(region -> region.flag("deny-pvp"))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (isStartWorld(event.getLocation()) && !plugin.getConfig().getBoolean(startWorldPath() + ".mob-spawning-enabled", false)
                && reason != CreatureSpawnEvent.SpawnReason.CUSTOM && reason != CreatureSpawnEvent.SpawnReason.COMMAND) {
            event.setCancelled(true); return;
        }
        if (regionsAt(event.getLocation()).stream().anyMatch(region -> region.flag("deny-mob-spawning"))) event.setCancelled(true);
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
        if (isStartWorld(player.getLocation()) && !plugin.getConfig().getBoolean(startWorldPath() + ".hunger-drain-enabled", false)) return true;
        return regionsAt(player.getLocation()).stream().anyMatch(region -> region.flag("disable-hunger"));
    }

    public boolean isTimeProgressionDisabled(World world) {
        return world != null && world.getName().equalsIgnoreCase(
            plugin.getConfig().getString(startWorldPath() + ".name", "world_start"))
            && !plugin.getConfig().getBoolean(startWorldPath() + ".time-passes", false);
    }

    public boolean isThirstDrainDisabled(Player player) {
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
        private void setFirst(Location value) { first = value.clone(); }
        private void setSecond(Location value) { second = value.clone(); }
        private boolean complete() { return first != null && second != null && first.getWorld().equals(second.getWorld()); }
        private int minX() { return Math.min(first.getBlockX(), second.getBlockX()); }
        private int minY() { return Math.min(first.getBlockY(), second.getBlockY()); }
        private int minZ() { return Math.min(first.getBlockZ(), second.getBlockZ()); }
        private int maxX() { return Math.max(first.getBlockX(), second.getBlockX()); }
        private int maxY() { return Math.max(first.getBlockY(), second.getBlockY()); }
        private int maxZ() { return Math.max(first.getBlockZ(), second.getBlockZ()); }
        private long volume() { return ((long) maxX() - minX() + 1) * ((long) maxY() - minY() + 1) * ((long) maxZ() - minZ() + 1); }
        private boolean allChunksLoaded() {
            World world = first.getWorld();
            for (int x = minX() >> 4; x <= maxX() >> 4; x++) for (int z = minZ() >> 4; z <= maxZ() >> 4; z++)
                if (!world.isChunkLoaded(x, z)) return false;
            return true;
        }
    }

    private enum Shape {
        WALLS("walls"), FACES("faces");
        private final String command;
        Shape(String command) { this.command = command; }
    }

    private record WeightedBlock(String blockData, double weight) { }
    private record BlockPosition(int x, int y, int z) {
        private BlockPosition offset(int dx, int dy, int dz) { return new BlockPosition(x + dx, y + dy, z + dz); }
    }
    private record BlockChange(Location location, String before, String after) { }
    private record ChunkTicket(World world, int x, int z) { }
    private record EditOperation(String label, List<BlockChange> changes) { }
    private record ClipboardBlock(int x, int y, int z, String blockData) { }
    private record Clipboard(List<ClipboardBlock> blocks, int sizeX, int sizeY, int sizeZ, Location origin) {
        private Clipboard(List<ClipboardBlock> blocks, int sizeX, int sizeY, int sizeZ) { this(blocks, sizeX, sizeY, sizeZ, null); }
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
