package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/** Admin GUI for placing Sponge .schem structures with a private BlockDisplay preview. */
public final class SchemaBuildManager implements Listener, CommandExecutor, TabCompleter {
    private static final String PERMISSION = "vnmine.command.schemabuild";
    private static final int PAGE_SIZE = 45;
    private static final int DEFAULT_MAX_BLOCKS = 400_000;
    private static final int DEFAULT_PREVIEW_BLOCKS = 1200;
    private static final int DEFAULT_HISTORY_SIZE = 10;
    private final VnMineHardcore plugin;
    private final Path schemasRoot;
    private final File buildsFile;
    private final Map<String, BuildRecord> builds = new LinkedHashMap<>();
    private final Map<String, String> invalidSchemaWarnings = new HashMap<>();
    private final Map<UUID, String> selectedBuilds = new HashMap<>();
    private final Map<UUID, BuildSession> sessions = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Deque<BuildOperation>> undoHistory = new HashMap<>();
    private final Map<UUID, Deque<BuildOperation>> redoHistory = new HashMap<>();
    private final BukkitTask previewTask;

    public SchemaBuildManager(VnMineHardcore plugin) throws IOException {
        this.plugin = plugin;
        this.schemasRoot = new File(plugin.getDataFolder(), "schemas").toPath().toAbsolutePath().normalize();
        this.buildsFile = new File(plugin.getDataFolder(), "builds.yml");
        Files.createDirectories(schemasRoot);
        loadBuilds();
        File example = schemasRoot.resolve("examples").resolve("small-platform.schem").toFile();
        if (!example.isFile()) {
            File parent = example.getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Could not create schema example directory.");
            try {
                plugin.saveResource("schemas/examples/small-platform.schem", false);
            } catch (IllegalArgumentException missingBundledExample) {
                plugin.getLogger().warning("[SchemaBuild] Bundled example schema is missing; schema folder remains usable.");
            }
        }
        previewTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickPreviews, 1L, 5L);
    }

    public void open(Player player) {
        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage("§cBạn không có quyền sử dụng Schema Build.");
            return;
        }
        openList(player, 0);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Lệnh này chỉ dùng trong game."); return true; }
        if (!player.hasPermission(PERMISSION)) { player.sendMessage("§cBạn không có quyền Schema Build."); return true; }
        if (args.length == 1 && "select".equalsIgnoreCase(args[0])) {
            BuildRecord nearest = builds.values().stream()
                .filter(record -> record.world.equals(player.getWorld().getName()))
                .min(Comparator.comparingDouble(record -> distanceSquared(record.anchor, player.getLocation())))
                .orElse(null);
            if (nearest == null) { player.sendMessage("§eKhông tìm thấy công trình đã đăng ký trong world hiện tại."); return true; }
            nearest = resolveWorld(nearest, player.getWorld());
            builds.put(nearest.id, nearest);
            selectedBuilds.put(player.getUniqueId(), nearest.id);
            player.sendMessage("§aĐã chọn công trình §f" + nearest.id + "§a. Gõ §f!edit§a, §f!copy §ahoặc §f!remove§a trong chat.");
            plugin.getLogger().info("[SchemaBuild] selected-build player=" + player.getName() + ", id=" + nearest.id);
            return true;
        }
        player.sendMessage("§eDùng /vnbuild select để chọn công trình gần nhất trong world hiện tại.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? List.of("select") : List.of();
    }

    private void openList(Player player, int requestedPage) {
        List<SchemaFile> files = findSchemaFiles(player);
        int pages = Math.max(1, (files.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        SchemaHolder holder = new SchemaHolder(player.getUniqueId(), View.LIST, page);
        Inventory menu = Bukkit.createInventory(holder, 54, "Schema Build • " + (page + 1) + "/" + pages);
        holder.inventory = menu;
        int end = Math.min(files.size(), (page + 1) * PAGE_SIZE);
        for (int index = page * PAGE_SIZE; index < end; index++) {
            SchemaFile file = files.get(index);
            int slot = index - page * PAGE_SIZE;
            holder.choices.put(slot, file);
            menu.setItem(slot, icon(Material.STRUCTURE_BLOCK, "§b" + file.relativePath(), List.of(
                "§7Tên: §f" + file.schema().name(),
                "§7Số block: §f" + file.schema().blocks().size(),
                "§7Kích thước: §f" + file.schema().sizeX() + "×" + file.schema().sizeY() + "×" + file.schema().sizeZ(),
                "§eNhấn để xoay/chỉnh preview.")));
        }
        if (files.isEmpty()) menu.setItem(22, icon(Material.BARRIER, "§cChưa có schema hợp lệ", List.of(
            "§7Đặt file Sponge Schematic .schem vào:", "§fplugins/VnMineHardcore/schemas/",
            "§7Có thể tạo thư mục con để nhóm công trình.", "§eVí dụ: schemas/examples/small-platform.schem")));
        if (page > 0) menu.setItem(45, icon(Material.ARROW, "§eTrang trước", List.of()));
        menu.setItem(46, icon(Material.SPECTRAL_ARROW, "§bUndo build gần nhất", List.of("§7Chỉ lùi thay đổi Schema Build.")));
        menu.setItem(47, icon(Material.TIPPED_ARROW, "§dRedo build vừa Undo", List.of()));
        menu.setItem(48, icon(Material.SUNFLOWER, "§eQuét lại thư mục", List.of("§7Đọc schema mới/thay đổi ngay.")));
        menu.setItem(49, icon(Material.ARROW, "§7Về menu Admin", List.of()));
        if ((page + 1) * PAGE_SIZE < files.size()) menu.setItem(53, icon(Material.ARROW, "§eTrang sau", List.of()));
        player.openInventory(menu);
    }

    private List<SchemaFile> findSchemaFiles(Player player) {
        List<SchemaFile> files = new ArrayList<>();
        try (var paths = Files.walk(schemasRoot)) {
            Path rootReal = schemasRoot.toRealPath();
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".schem"))
                    .toList()) {
                if (!path.toRealPath().startsWith(rootReal)) continue;
                String relative = schemasRoot.relativize(path).toString().replace(File.separatorChar, '/');
                try {
                    files.add(new SchemaFile(relative, loadSchema(path)));
                    invalidSchemaWarnings.remove(relative);
                } catch (IllegalArgumentException | IOException exception) {
                    String fingerprint = invalidSchemaFingerprint(path);
                    if (!fingerprint.equals(invalidSchemaWarnings.put(relative, fingerprint))) {
                        plugin.getLogger().warning("[SchemaBuild] Ignoring schema " + relative + ": " + exception.getMessage());
                        player.sendMessage("§cBỏ qua schema §f" + relative + "§c: §7" + exception.getMessage());
                    }
                }
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "[SchemaBuild] Could not scan schema directory.", exception);
            player.sendMessage("§cKhông thể đọc thư mục schemas. Hãy xem console.");
        }
        files.sort(Comparator.comparing(SchemaFile::relativePath, String.CASE_INSENSITIVE_ORDER));
        return files;
    }

    private SchemaDefinition loadSchema(Path file) throws IOException {
        int maxBlocks = Math.max(1, Math.min(500_000,
            plugin.getConfig().getInt("schema-build.max-blocks", DEFAULT_MAX_BLOCKS)));
        SpongeSchematicReader.Schematic schematic = SpongeSchematicReader.read(file, maxBlocks);
        List<SchemaBlock> blocks = new ArrayList<>(schematic.blockStates().size());
        java.util.Set<String> normalizedLegacyStates = new java.util.HashSet<>();
        int index = 0;
        for (int y = 0; y < schematic.height(); y++) {
            for (int z = 0; z < schematic.length(); z++) {
                for (int x = 0; x < schematic.width(); x++) {
                    String state = schematic.blockStates().get(index++);
                    BlockData data;
                    try { data = Bukkit.createBlockData(state); }
                    catch (IllegalArgumentException exception) {
                        String compatible = stripUnsupportedFalseWaterlogged(state);
                        if (compatible == null) {
                            throw new IOException("unsupported block state in schematic: " + state, exception);
                        }
                        try {
                            data = Bukkit.createBlockData(compatible);
                            if (normalizedLegacyStates.add(state)) {
                                plugin.getLogger().info("[SchemaBuild] Normalized unsupported waterlogged=false "
                                    + "property while loading " + file.getFileName() + ": " + state);
                            }
                        } catch (IllegalArgumentException retryFailure) {
                            retryFailure.addSuppressed(exception);
                            throw new IOException("unsupported block state in schematic: " + state, retryFailure);
                        }
                    }
                    if (!data.getMaterial().isBlock()) throw new IOException("non-block palette entry: " + state);
                    blocks.add(new SchemaBlock(x, y, z, data.getAsString()));
                }
            }
        }
        return new SchemaDefinition(schematic.name(), List.copyOf(blocks), schematic.width(),
            schematic.height(), schematic.length());
    }

    private String stripUnsupportedFalseWaterlogged(String state) {
        int open = state.indexOf('[');
        int close = state.lastIndexOf(']');
        if (open < 1 || close <= open || !state.substring(open + 1, close).contains("waterlogged=false")) {
            return null;
        }
        String[] properties = state.substring(open + 1, close).split(",");
        List<String> retained = new ArrayList<>();
        boolean removed = false;
        for (String property : properties) {
            if (property.trim().equals("waterlogged=false")) removed = true;
            else retained.add(property.trim());
        }
        if (!removed) return null;
        return retained.isEmpty() ? state.substring(0, open)
            : state.substring(0, open) + "[" + String.join(",", retained) + "]";
    }

    private String invalidSchemaFingerprint(Path path) {
        try {
            return Files.size(path) + ":" + Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return "unavailable";
        }
    }

    private void openBuilder(Player player, SchemaFile schema) {
        openBuilder(player, schema, null);
    }

    private void openBuilder(Player player, SchemaFile schema, BuildRecord existing) {
        BuildSession previous = sessions.remove(player.getUniqueId());
        if (previous != null) removePreview(previous);
        BuildSession session = new BuildSession(schema);
        if (existing != null) {
            session.buildId = existing.id;
            session.protect = existing.protect;
            session.anchor = existing.anchor.clone();
            session.rotationX = existing.rotationX;
            session.rotationY = existing.rotationY;
            session.rotationZ = existing.rotationZ;
            session.flipX = existing.flipX;
            session.flipY = existing.flipY;
            session.baseline = existing.blocks;
        }
        sessions.put(player.getUniqueId(), session);
        updatePreview(player, session, true);
        openBuilderMenu(player, session);
        plugin.getLogger().info("[SchemaBuild] preview-start player=" + player.getName() + ", schema="
            + schema.relativePath() + ", blocks=" + schema.schema().blocks().size());
    }

    private void openBuilderMenu(Player player, BuildSession session) {
        SchemaHolder holder = new SchemaHolder(player.getUniqueId(), View.BUILDER, 0, session);
        Inventory menu = Bukkit.createInventory(holder, 54, "Đặt schema • " + session.schema.relativePath());
        holder.inventory = menu;
        menu.setItem(4, icon(Material.STRUCTURE_BLOCK, "§b" + session.schema.schema().name(), List.of(
            "§7Khối neo: §f" + locationText(session.anchor),
            "§7Xoay X/Y/Z: §f" + session.rotationX + "° / " + session.rotationY + "° / " + session.rotationZ + "°",
            "§7Đối xứng X/Y/Z: §f" + session.flipX + " / " + session.flipY + " / " + session.flipZ,
            "§7Preview ban đầu hiện phía trước bạn.")));
        menu.setItem(10, icon(Material.IRON_NUGGET, "§eXoay trục X −90°", List.of()));
        menu.setItem(16, icon(Material.GOLD_NUGGET, "§eXoay trục X +90°", List.of()));
        menu.setItem(19, icon(Material.IRON_INGOT, "§eXoay trục Y −90°", List.of()));
        menu.setItem(25, icon(Material.GOLD_INGOT, "§eXoay trục Y +90°", List.of()));
        menu.setItem(28, icon(Material.IRON_BLOCK, "§eXoay trục Z −90°", List.of()));
        menu.setItem(31, icon(session.protect ? Material.LIME_DYE : Material.GRAY_DYE,
            "§6Bảo vệ sau khi đặt: " + (session.protect ? "§aON" : "§cOFF"),
            List.of("§7ON chặn người thường đặt/phá block trong cấu trúc.", "§eNhấn để đổi.")));
        menu.setItem(34, icon(Material.GOLD_BLOCK, "§eXoay trục Z +90°", List.of()));
        menu.setItem(11, icon(session.flipX ? Material.LIME_DYE : Material.GRAY_DYE,
            "§eLật trục X: " + (session.flipX ? "§aBẬT" : "§cTẮT"), List.of("§7Nhấn để đổi đối xứng.")));
        menu.setItem(15, icon(session.flipY ? Material.LIME_DYE : Material.GRAY_DYE,
            "§eLật trục Y: " + (session.flipY ? "§aBẬT" : "§cTẮT"), List.of("§7Nhấn để đổi đối xứng.")));
        menu.setItem(20, icon(session.flipZ ? Material.LIME_DYE : Material.GRAY_DYE,
            "§eLật trục Z: " + (session.flipZ ? "§aBẬT" : "§cTẮT"), List.of("§7Nhấn để đổi đối xứng.")));
        menu.setItem(37, icon(Material.SPECTRAL_ARROW, "§bUndo build gần nhất", List.of(
            "§7Chỉ lùi thay đổi do Schema Build thực hiện.", "§7Tối đa: §f" + historyLimit() + " bản.")));
        menu.setItem(40, icon(Material.LIME_CONCRETE, "§aPreview ngoài world", List.of(
            "§7Đóng menu và giữ bóng mờ trong world.",
            "§7Di chuyển tự do để chọn vị trí đặt.",
            "§eGõ !put để đặt hoặc !back để chỉnh lại.")));
        menu.setItem(43, icon(Material.TIPPED_ARROW, "§dRedo build vừa Undo", List.of()));
        menu.setItem(45, icon(Material.ARROW, "§eChọn schema khác", List.of()));
        menu.setItem(49, icon(Material.BARRIER, "§cHủy xem trước", List.of("§7Bóng mờ sẽ bị xóa.")));
        menu.setItem(53, icon(Material.COMPASS, "§bCách đặt", List.of(
            "§7Xoay và lật từng trục, rồi bật preview ngoài world.",
            "§7Trong preview, di chuyển/ngắm mặt block để chọn vị trí.",
            "§e!put đặt công trình; !back mở lại menu xoay/lật.")));
        player.openInventory(menu);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SchemaHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.viewer.equals(player.getUniqueId()) || event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getRawSlot();
        if (!player.hasPermission(PERMISSION)) { player.closeInventory(); return; }
        if (holder.view == View.LIST) {
            if (slot == 49) {
                if (plugin.getMercenaryManager() != null) plugin.getMercenaryManager().openAdminMenu(player);
            } else if (slot == 45 && holder.page > 0) openList(player, holder.page - 1);
            else if (slot == 53) openList(player, holder.page + 1);
            else if (slot == 46) { undo(player); openList(player, holder.page); }
            else if (slot == 47) { redo(player); openList(player, holder.page); }
            else if (slot == 48) openList(player, holder.page);
            else if (holder.choices.containsKey(slot)) openBuilder(player, holder.choices.get(slot));
            return;
        }
        BuildSession session = sessions.get(player.getUniqueId());
        if (session == null) { player.closeInventory(); return; }
        switch (slot) {
            case 10 -> rotate(player, session, 'x', -1);
            case 16 -> rotate(player, session, 'x', 1);
            case 11 -> toggleFlip(player, session, 'x');
            case 15 -> toggleFlip(player, session, 'y');
            case 20 -> toggleFlip(player, session, 'z');
            case 19 -> rotate(player, session, 'y', -1);
            case 25 -> rotate(player, session, 'y', 1);
            case 28 -> rotate(player, session, 'z', -1);
            case 34 -> rotate(player, session, 'z', 1);
            case 31 -> { session.protect = !session.protect; openBuilderMenu(player, session); }
            case 37 -> undo(player);
            case 40 -> enterPlacementPreview(player, session);
            case 43 -> redo(player);
            case 45 -> {
                sessions.remove(player.getUniqueId());
                removePreview(session);
                openList(player, 0);
            }
            case 49 -> {
                sessions.remove(player.getUniqueId());
                removePreview(session);
                player.closeInventory();
                player.sendMessage("§eĐã hủy Schema Build.");
            }
            default -> { }
        }
    }

    @EventHandler
    public void onMenuClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)
                || !(event.getInventory().getHolder() instanceof SchemaHolder holder)
                || holder.view != View.BUILDER || holder.session == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            BuildSession active = sessions.get(player.getUniqueId());
            if (active != holder.session) return;
            if (active.placementPreview) return;
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof SchemaHolder next
                    && next.session == holder.session) return;
            sessions.remove(player.getUniqueId());
            removePreview(holder.session);
            if (player.isOnline()) player.sendMessage("§eĐã đóng Schema Build; preview đã được dọn.");
        });
    }

    private void rotate(Player player, BuildSession session, char axis, int amount) {
        switch (axis) {
            case 'x' -> session.rotationX = wrap(session.rotationX + amount * 90);
            case 'y' -> session.rotationY = wrap(session.rotationY + amount * 90);
            case 'z' -> session.rotationZ = wrap(session.rotationZ + amount * 90);
            default -> { }
        }
        updatePreview(player, session, true);
        openBuilderMenu(player, session);
    }

    private void toggleFlip(Player player, BuildSession session, char axis) {
        switch (axis) {
            case 'x' -> session.flipX = !session.flipX;
            case 'y' -> session.flipY = !session.flipY;
            case 'z' -> session.flipZ = !session.flipZ;
            default -> { }
        }
        updatePreview(player, session, true);
        openBuilderMenu(player, session);
    }

    private void enterPlacementPreview(Player player, BuildSession session) {
        session.placementPreview = true;
        updatePreview(player, session, true);
        player.closeInventory();
        player.sendMessage("§bPreview schema đang bật. Di chuyển và ngắm để chọn vị trí; gõ §f!put §bđể đặt hoặc §f!back §bđể chỉnh xoay/lật.");
        plugin.getLogger().info("[SchemaBuild] placement-preview player=" + player.getName() + ", schema="
            + session.schema.relativePath() + ", anchor=" + locationText(session.anchor));
    }

    @EventHandler
    public void onPlacementPreviewChat(AsyncPlayerChatEvent event) {
        String input = event.getMessage().trim();
        if (!"!put".equalsIgnoreCase(input) && !"!back".equalsIgnoreCase(input)) return;
        UUID playerId = event.getPlayer().getUniqueId();
        BuildSession session = sessions.get(playerId);
        if (session == null || !session.placementPreview) return;
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline() || sessions.get(playerId) != session) return;
            if (!player.hasPermission(PERMISSION)) {
                sessions.remove(playerId);
                removePreview(session);
                player.sendMessage("§cBạn không còn quyền Schema Build; preview đã bị hủy.");
                return;
            }
            if ("!put".equalsIgnoreCase(input)) {
                place(player, session);
            } else {
                session.placementPreview = false;
                updatePreview(player, session, true);
                openBuilderMenu(player, session);
                player.sendMessage("§eĐã quay lại menu xoay/lật schema.");
            }
        });
    }

    @EventHandler
    public void onBuildManagementChat(AsyncPlayerChatEvent event) {
        String input = event.getMessage().trim().toLowerCase(Locale.ROOT);
        if (!List.of("!edit", "!copy", "!remove").contains(input)) return;
        UUID playerId = event.getPlayer().getUniqueId();
        String selectedId = selectedBuilds.get(playerId);
        if (selectedId == null) return;
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            BuildRecord record = builds.get(selectedId);
            if (player == null || !player.isOnline() || record == null) return;
            if (!player.hasPermission(PERMISSION)) { player.sendMessage("§cBạn không còn quyền Schema Build."); return; }
            switch (input) {
                case "!edit" -> {
                    SchemaFile schema = loadRecordedSchema(player, record);
                    if (schema != null) openBuilder(player, schema, record);
                }
                case "!copy" -> {
                    SchemaFile schema = loadRecordedSchema(player, record);
                    if (schema == null) return;
                    BuildSession copy = new BuildSession(schema);
                    copy.anchor = record.anchor.clone(); copy.rotationX = record.rotationX;
                    copy.rotationY = record.rotationY; copy.rotationZ = record.rotationZ;
                    copy.flipX = record.flipX; copy.flipY = record.flipY; copy.flipZ = record.flipZ;
                    copy.protect = record.protect;
                    sessions.put(playerId, copy);
                    updatePreview(player, copy, true);
                    openBuilderMenu(player, copy);
                    player.sendMessage("§eBản sao sẽ nhận ID mới sau khi !put.");
                }
                case "!remove" -> removeBuild(player, record);
                default -> { }
            }
        });
    }

    private SchemaFile loadRecordedSchema(Player player, BuildRecord record) {
        try {
            Path path = schemasRoot.resolve(record.schemaPath).normalize();
            if (!path.startsWith(schemasRoot) || !Files.isRegularFile(path)) throw new IOException("schema file is missing");
            return new SchemaFile(record.schemaPath, loadSchema(path));
        } catch (IOException | IllegalArgumentException exception) {
            player.sendMessage("§cKhông thể mở schema của công trình. Kiểm tra file và console.");
            plugin.getLogger().log(Level.WARNING, "[SchemaBuild] Could not load stored build id=" + record.id, exception);
            return null;
        }
    }

    private void removeBuild(Player player, BuildRecord record) {
        if (record.blocks.stream().anyMatch(snapshot -> snapshot.location().getWorld() == null
                || !snapshot.location().getWorld().isChunkLoaded(snapshot.location().getBlockX() >> 4,
                    snapshot.location().getBlockZ() >> 4))) {
            player.sendMessage("§cHãy đến gần công trình để nạp toàn bộ chunk trước khi xóa."); return;
        }
        if (record.blocks.stream().anyMatch(snapshot -> !snapshot.location().getBlock().getBlockData().getAsString().equals(snapshot.afterData()))) {
            player.sendMessage("§cKhông xóa được an toàn: có block công trình đã bị thay đổi. Khôi phục thủ công hoặc dùng !edit."); return;
        }
        if (record.blocks.stream().anyMatch(snapshot -> snapshot.location().getBlock().getState() instanceof org.bukkit.block.TileState)) {
            player.sendMessage("§cKhông xóa được an toàn: công trình có block entity như rương/sign."); return;
        }
        BuildRecord removed = builds.remove(record.id);
        if (!saveBuilds()) { builds.put(record.id, removed); player.sendMessage("§cKhông lưu được danh sách công trình."); return; }
        if (plugin.getRegionProtectionManager() != null
                && !plugin.getRegionProtectionManager().removeRegion(record.id)
                && plugin.getRegionProtectionManager().getRegion(record.id) != null) {
            builds.put(record.id, removed); saveBuilds();
            player.sendMessage("§cKhông thể gỡ bảo vệ công trình; không xóa block nào."); return;
        }
        restoreSnapshots(record.blocks, false);
        selectedBuilds.remove(player.getUniqueId());
        player.sendMessage("§aĐã xóa công trình §f" + record.id + "§a và khôi phục block nền.");
        plugin.getLogger().info("[SchemaBuild] build-removed player=" + player.getName() + ", id=" + record.id);
    }

    private int wrap(int degrees) { return Math.floorMod(degrees, 360); }

    private String nextBuildId(String world, String schemaName) {
        String worldPart = sanitizeIdPart(world, 18);
        String schemaPart = sanitizeIdPart(schemaName, 18);
        String prefix = worldPart + "_" + schemaPart + "_";
        int next = builds.keySet().stream().filter(id -> id.startsWith(prefix))
            .map(id -> id.substring(prefix.length())).filter(value -> value.matches("\\d+"))
            .mapToInt(value -> { try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { return 0; } })
            .max().orElse(0) + 1;
        return prefix + next;
    }

    private String sanitizeIdPart(String value, int maxLength) {
        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "_").replaceAll("_+", "_");
        if (normalized.isBlank()) normalized = "build";
        return normalized.substring(0, Math.min(maxLength, normalized.length()));
    }

    private String locationKey(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private double distanceSquared(Location first, Location second) {
        double dx = first.getX() - second.getX(), dy = first.getY() - second.getY(), dz = first.getZ() - second.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private BuildRecord resolveWorld(BuildRecord record, World world) {
        Location anchor = new Location(world, record.anchor.getX(), record.anchor.getY(), record.anchor.getZ(),
            record.anchor.getYaw(), record.anchor.getPitch());
        List<BlockSnapshot> blocks = record.blocks.stream().map(snapshot -> new BlockSnapshot(
            new Location(world, snapshot.location().getBlockX(), snapshot.location().getBlockY(), snapshot.location().getBlockZ()),
            snapshot.beforeData(), snapshot.afterData(), snapshot.baselineBeforeData())).toList();
        return new BuildRecord(record.id, record.schemaPath, record.world, anchor, record.rotationX, record.rotationY,
            record.rotationZ, record.flipX, record.flipY, record.flipZ, record.protect, blocks);
    }

    private void loadBuilds() {
        if (!buildsFile.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(buildsFile);
        ConfigurationSection entries = yaml.getConfigurationSection("builds");
        if (entries == null) return;
        for (String id : entries.getKeys(false)) {
            String path = "builds." + id;
            String worldName = yaml.getString(path + ".world", "");
            World world = Bukkit.getWorld(worldName);
            Location anchor = new Location(world, yaml.getDouble(path + ".anchor.x"), yaml.getDouble(path + ".anchor.y"), yaml.getDouble(path + ".anchor.z"));
            List<BlockSnapshot> snapshots = new ArrayList<>();
            for (Map<?, ?> raw : yaml.getMapList(path + ".blocks")) {
                try {
                    int x = ((Number) raw.get("x")).intValue(), y = ((Number) raw.get("y")).intValue(), z = ((Number) raw.get("z")).intValue();
                    String before = String.valueOf(raw.get("before")), after = String.valueOf(raw.get("after"));
                    snapshots.add(new BlockSnapshot(new Location(world, x, y, z), before, after, before));
                } catch (RuntimeException exception) { plugin.getLogger().warning("[SchemaBuild] Invalid block record for " + id); }
            }
            BuildRecord record = new BuildRecord(id, yaml.getString(path + ".schema", ""), worldName, anchor,
                yaml.getInt(path + ".rotation.x"), yaml.getInt(path + ".rotation.y"), yaml.getInt(path + ".rotation.z"),
                yaml.getBoolean(path + ".flip.x"), yaml.getBoolean(path + ".flip.y"), yaml.getBoolean(path + ".flip.z"),
                yaml.getBoolean(path + ".protect"), List.copyOf(snapshots));
            if (!record.schemaPath.isBlank() && !record.blocks.isEmpty()) builds.put(id.toLowerCase(Locale.ROOT), record);
        }
        plugin.getLogger().info("[SchemaBuild] Loaded " + builds.size() + " persistent build record(s).");
    }

    private boolean saveBuilds() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (BuildRecord record : builds.values()) {
            String path = "builds." + record.id;
            yaml.set(path + ".schema", record.schemaPath); yaml.set(path + ".world", record.world);
            yaml.set(path + ".anchor.x", record.anchor.getX()); yaml.set(path + ".anchor.y", record.anchor.getY()); yaml.set(path + ".anchor.z", record.anchor.getZ());
            yaml.set(path + ".rotation.x", record.rotationX); yaml.set(path + ".rotation.y", record.rotationY); yaml.set(path + ".rotation.z", record.rotationZ);
            yaml.set(path + ".flip.x", record.flipX); yaml.set(path + ".flip.y", record.flipY); yaml.set(path + ".flip.z", record.flipZ);
            yaml.set(path + ".protect", record.protect);
            List<Map<String, Object>> blocks = new ArrayList<>();
            for (BlockSnapshot snapshot : record.blocks) {
                Map<String, Object> block = new LinkedHashMap<>();
                block.put("x", snapshot.location().getBlockX()); block.put("y", snapshot.location().getBlockY()); block.put("z", snapshot.location().getBlockZ());
                block.put("before", snapshot.baselineBeforeData()); block.put("after", snapshot.afterData()); blocks.add(block);
            }
            yaml.set(path + ".blocks", blocks);
        }
        try { yaml.save(buildsFile); return true; }
        catch (IOException exception) { plugin.getLogger().log(Level.SEVERE, "[SchemaBuild] Could not save builds.yml", exception); return false; }
    }

    private void tickPreviews() {
        for (Map.Entry<UUID, BuildSession> entry : List.copyOf(sessions.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                removePreview(entry.getValue());
                sessions.remove(entry.getKey());
                continue;
            }
            updatePreview(player, entry.getValue(), false);
        }
    }

    private void updatePreview(Player player, BuildSession session, boolean force) {
        Location newAnchor = findAnchor(player, session.placementPreview);
        if (session.anchor != null && sameBlock(session.anchor, newAnchor) && !force) return;
        session.anchor = newAnchor;
        List<PlacedBlock> blocks = transform(session.schema.schema(), session.rotationX, session.rotationY, session.rotationZ,
            session.flipX, session.flipY, session.flipZ);
        int displayLimit = Math.max(1, Math.min(5000,
            plugin.getConfig().getInt("schema-build.preview-blocks", DEFAULT_PREVIEW_BLOCKS)));
        List<PlacedBlock> visibleBlocks = blocks.stream()
            .filter(placed -> !Bukkit.createBlockData(placed.blockData()).getMaterial().isAir()).toList();
        int count = Math.min(visibleBlocks.size(), displayLimit);
        while (session.preview.size() > count) session.preview.remove(session.preview.size() - 1).remove();
        for (int index = 0; index < count; index++) {
            PlacedBlock placed = visibleBlocks.get(index);
            Location location = session.anchor.clone().add(placed.x(), placed.y(), placed.z());
            BlockDisplay display;
            if (index >= session.preview.size()) {
                display = session.anchor.getWorld().spawn(location, BlockDisplay.class, entity -> {
                    entity.setBlock(Bukkit.createBlockData(placed.blockData()));
                    entity.setGlowing(true);
                    entity.setVisibleByDefault(false);
                    entity.setPersistent(false);
                });
                player.showEntity(plugin, display);
                session.preview.add(display);
            } else {
                display = session.preview.get(index);
                display.setBlock(Bukkit.createBlockData(placed.blockData()));
                if (!display.getWorld().equals(location.getWorld())) display.teleport(location);
                else if (display.getLocation().distanceSquared(location) > 0.001) display.teleport(location);
                player.showEntity(plugin, display);
            }
        }
        session.previewTruncated = visibleBlocks.size() > displayLimit;
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof SchemaHolder holder
                && holder.viewer.equals(player.getUniqueId()) && holder.view == View.BUILDER) {
            holder.inventory.setItem(4, icon(Material.STRUCTURE_BLOCK, "§b" + session.schema.schema().name(), List.of(
                "§7Khối neo: §f" + locationText(session.anchor),
                "§7Xoay X/Y/Z: §f" + session.rotationX + "° / " + session.rotationY + "° / " + session.rotationZ + "°",
                "§7Lật X/Y/Z: §f" + session.flipX + " / " + session.flipY + " / " + session.flipZ,
                session.previewTruncated ? "§cPreview rút gọn theo giới hạn cấu hình." : "§aPreview hiển thị đầy đủ.")));
        }
        if (session.placementPreview) {
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                "Schema: " + session.schema.relativePath() + " | " + locationText(session.anchor)
                    + " | X/Y/Z " + session.rotationX + "/" + session.rotationY + "/" + session.rotationZ
                    + " | !put đặt / !back chỉnh"));
        }
    }

    private Location findAnchor(Player player, boolean placementPreview) {
        if (!placementPreview) {
            Location ahead = player.getEyeLocation().add(player.getLocation().getDirection().normalize().multiply(4.0));
            return new Location(player.getWorld(), ahead.getBlockX(), ahead.getBlockY(), ahead.getBlockZ());
        }
        RayTraceResult ray = player.rayTraceBlocks(48.0);
        if (ray != null && ray.getHitBlock() != null) {
            Block block = ray.getHitBlock();
            BlockFace face = ray.getHitBlockFace() == null ? BlockFace.UP : ray.getHitBlockFace();
            return block.getRelative(face).getLocation();
        }
        Vector direction = player.getLocation().getDirection().normalize().multiply(4.0);
        Location ahead = player.getEyeLocation().add(direction);
        return new Location(player.getWorld(), ahead.getBlockX(), ahead.getBlockY(), ahead.getBlockZ());
    }

    private boolean sameBlock(Location a, Location b) {
        return a.getWorld().equals(b.getWorld()) && a.getBlockX() == b.getBlockX()
            && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }

    private List<PlacedBlock> transform(SchemaDefinition schema, int rotateX, int rotateY, int rotateZ,
            boolean flipX, boolean flipY, boolean flipZ) {
        List<RotatedBlock> rotated = new ArrayList<>(schema.blocks().size());
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (SchemaBlock block : schema.blocks()) {
            Vector position = mirror(new Vector(block.x(), block.y(), block.z()), flipX, flipY, flipZ);
            position = rotate(position, rotateX, rotateY, rotateZ);
            int x = position.getBlockX(), y = position.getBlockY(), z = position.getBlockZ();
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            BlockData data = Bukkit.createBlockData(block.blockData());
            transformBlockData(data, rotateX, rotateY, rotateZ, flipX, flipY, flipZ);
            rotated.add(new RotatedBlock(x, y, z, data.getAsString()));
        }
        int originX = minX, originY = minY, originZ = minZ;
        return rotated.stream().map(block -> new PlacedBlock(block.x() - originX, block.y() - originY,
                block.z() - originZ, block.blockData()))
            .sorted(Comparator.comparingInt(PlacedBlock::y).thenComparingInt(PlacedBlock::x).thenComparingInt(PlacedBlock::z))
            .toList();
    }

    private Vector rotate(Vector original, int rotateX, int rotateY, int rotateZ) {
        Vector value = original.clone();
        for (int turn = 0; turn < rotateX / 90; turn++) value = new Vector(value.getX(), -value.getZ(), value.getY());
        for (int turn = 0; turn < rotateY / 90; turn++) value = new Vector(value.getZ(), value.getY(), -value.getX());
        for (int turn = 0; turn < rotateZ / 90; turn++) value = new Vector(-value.getY(), value.getX(), value.getZ());
        return value;
    }

    private void transformBlockData(BlockData data, int rotateX, int rotateY, int rotateZ,
            boolean flipX, boolean flipY, boolean flipZ) {
        if (data instanceof Directional directional) {
            Vector facing = mirror(faceVector(directional.getFacing()), flipX, flipY, flipZ);
            facing = rotate(facing, rotateX, rotateY, rotateZ);
            BlockFace mapped = vectorFace(facing);
            try { directional.setFacing(mapped); } catch (IllegalArgumentException ignored) { }
        }
        if (data instanceof Orientable orientable) {
            Vector axis = switch (orientable.getAxis()) {
                case X -> new Vector(1, 0, 0);
                case Y -> new Vector(0, 1, 0);
                case Z -> new Vector(0, 0, 1);
            };
            Vector rotated = rotate(axis, rotateX, rotateY, rotateZ);
            org.bukkit.Axis newAxis = Math.abs(rotated.getX()) > 0.5 ? org.bukkit.Axis.X
                : Math.abs(rotated.getY()) > 0.5 ? org.bukkit.Axis.Y : org.bukkit.Axis.Z;
            if (orientable.getAxes().contains(newAxis)) orientable.setAxis(newAxis);
        }
        if (data instanceof MultipleFacing multipleFacing) {
            List<BlockFace> originalFaces = List.copyOf(multipleFacing.getFaces());
            for (BlockFace face : multipleFacing.getAllowedFaces()) multipleFacing.setFace(face, false);
            for (BlockFace face : originalFaces) {
                Vector facing = mirror(faceVector(face), flipX, flipY, flipZ);
                facing = rotate(facing, rotateX, rotateY, rotateZ);
                BlockFace mapped = vectorFace(facing);
                if (multipleFacing.getAllowedFaces().contains(mapped)) multipleFacing.setFace(mapped, true);
            }
        }
        if (data instanceof Bisected bisected && flipY) {
            bisected.setHalf(bisected.getHalf() == Bisected.Half.TOP ? Bisected.Half.BOTTOM : Bisected.Half.TOP);
        }
        if (data instanceof Stairs stairs && (flipX ^ flipZ)) {
            stairs.setShape(switch (stairs.getShape()) {
                case INNER_LEFT -> Stairs.Shape.INNER_RIGHT;
                case INNER_RIGHT -> Stairs.Shape.INNER_LEFT;
                case OUTER_LEFT -> Stairs.Shape.OUTER_RIGHT;
                case OUTER_RIGHT -> Stairs.Shape.OUTER_LEFT;
                default -> stairs.getShape();
            });
        }
        if (data instanceof Rotatable rotatable) {
            Vector facing = mirror(faceVector(rotatable.getRotation()), flipX, flipY, flipZ);
            facing = rotate(facing, rotateX, rotateY, rotateZ);
            try { rotatable.setRotation(vectorFace(facing)); } catch (IllegalArgumentException ignored) { }
        }
    }

    private Vector mirror(Vector vector, boolean flipX, boolean flipY, boolean flipZ) {
        return new Vector(flipX ? -vector.getX() : vector.getX(),
            flipY ? -vector.getY() : vector.getY(), flipZ ? -vector.getZ() : vector.getZ());
    }

    private Vector faceVector(BlockFace face) { return new Vector(face.getModX(), face.getModY(), face.getModZ()); }

    private BlockFace vectorFace(Vector vector) {
        for (BlockFace face : BlockFace.values()) {
            if (face.getModX() == vector.getBlockX() && face.getModY() == vector.getBlockY()
                    && face.getModZ() == vector.getBlockZ()) return face;
        }
        return BlockFace.SELF;
    }

    private void place(Player player, BuildSession session) {
        List<PlacedBlock> blocks = transform(session.schema.schema(), session.rotationX, session.rotationY, session.rotationZ,
            session.flipX, session.flipY, session.flipZ);
        if (session.anchor == null || session.anchor.getWorld() == null) {
            player.sendMessage("§cChưa xác định được vị trí đặt.");
            return;
        }
        var border = session.anchor.getWorld().getWorldBorder();
        List<BlockSnapshot> snapshots = new ArrayList<>(blocks.size());
        Map<String, BlockSnapshot> baselineByLocation = new HashMap<>();
        if (session.baseline != null) for (BlockSnapshot saved : session.baseline) baselineByLocation.put(locationKey(saved.location()), saved);
        java.util.Set<String> newLocations = new java.util.HashSet<>();
        for (PlacedBlock placed : blocks) {
            int x = session.anchor.getBlockX() + placed.x();
            int y = session.anchor.getBlockY() + placed.y();
            int z = session.anchor.getBlockZ() + placed.z();
            if (y < session.anchor.getWorld().getMinHeight() || y >= session.anchor.getWorld().getMaxHeight()) {
                player.sendMessage("§cSchema nằm ngoài giới hạn chiều cao world; chưa có block nào được đổi.");
                return;
            }
            Location location = new Location(session.anchor.getWorld(), x, y, z);
            if (!border.isInside(location) || !session.anchor.getWorld().isChunkLoaded(x >> 4, z >> 4)) {
                player.sendMessage("§cSchema vượt world border hoặc đi vào chunk chưa được nạp; chưa đặt gì cả.");
                return;
            }
            Block block = location.getBlock();
            if (block.getState() instanceof org.bukkit.block.TileState && !block.getType().isAir()) {
                player.sendMessage("§cSchema không ghi dữ liệu inventory/sign. Không thể ghi đè block entity tại "
                    + x + ", " + y + ", " + z + ".");
                return;
            }
            BlockData after = Bukkit.createBlockData(placed.blockData());
            String key = locationKey(location);
            newLocations.add(key);
            BlockSnapshot baseline = baselineByLocation.get(key);
            snapshots.add(new BlockSnapshot(location, block.getBlockData().getAsString(), after.getAsString(),
                baseline == null ? block.getBlockData().getAsString() : baseline.beforeData()));
        }
        if (session.baseline != null) {
            for (BlockSnapshot old : session.baseline) {
                if (newLocations.contains(locationKey(old.location()))) continue;
                Location location = old.location();
                if (location.getWorld() == null || !location.getWorld().equals(session.anchor.getWorld())
                        || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                    player.sendMessage("§cKhông thể cập nhật công trình: vùng cũ không còn được nạp."); return;
                }
                Block block = location.getBlock();
                snapshots.add(new BlockSnapshot(location, block.getBlockData().getAsString(), old.beforeData(), old.beforeData()));
            }
        }
        int applied = 0;
        for (BlockSnapshot snapshot : snapshots) {
            Block block = snapshot.location().getBlock();
            BlockData after = Bukkit.createBlockData(snapshot.afterData());
            ItemStack placedItem = new ItemStack(after.getMaterial().isAir() ? Material.BARRIER : after.getMaterial());
            BlockPlaceEvent placeEvent = new BlockPlaceEvent(block, block.getState(), block.getRelative(BlockFace.DOWN),
                placedItem, player, true, EquipmentSlot.HAND);
            Bukkit.getPluginManager().callEvent(placeEvent);
            if (placeEvent.isCancelled()) {
                restoreSnapshots(snapshots.subList(0, applied), false);
                player.sendMessage("§cMột plugin bảo vệ đã từ chối đặt tại " + block.getX() + ", "
                    + block.getY() + ", " + block.getZ() + ". Đã hoàn tác phần đã đặt.");
                plugin.getLogger().warning("[SchemaBuild] placement-cancelled player=" + player.getName()
                    + ", schema=" + session.schema.relativePath() + ", applied-before-cancel=" + applied);
                return;
            }
            block.setBlockData(after, false);
            applied++;
        }
        BuildOperation operation = new BuildOperation(session.schema.relativePath(), List.copyOf(snapshots));
        List<BlockSnapshot> persistedSnapshots = snapshots.stream().map(snapshot -> new BlockSnapshot(snapshot.location(),
            snapshot.baselineBeforeData(), snapshot.afterData(), snapshot.baselineBeforeData())).toList();
        String buildId = session.buildId == null ? nextBuildId(session.anchor.getWorld().getName(), session.schema.schema().name()) : session.buildId;
        BuildRecord previousRecord = builds.get(buildId);
        BuildRecord record = new BuildRecord(buildId, session.schema.relativePath(), session.anchor.clone(),
            session.rotationX, session.rotationY, session.rotationZ, session.flipX, session.flipY, session.flipZ,
            session.protect, List.copyOf(persistedSnapshots));
        builds.put(buildId, record);
        if (!saveBuilds()) {
            if (previousRecord == null) builds.remove(buildId); else builds.put(buildId, previousRecord);
            restoreSnapshots(snapshots, false);
            player.sendMessage("§cKhông thể lưu ID công trình; block đã được hoàn tác."); return;
        }
        if (plugin.getRegionProtectionManager() != null) {
            Location min = new Location(session.anchor.getWorld(),
                blocks.stream().mapToInt(block -> session.anchor.getBlockX() + block.x()).min().orElse(session.anchor.getBlockX()),
                blocks.stream().mapToInt(block -> session.anchor.getBlockY() + block.y()).min().orElse(session.anchor.getBlockY()),
                blocks.stream().mapToInt(block -> session.anchor.getBlockZ() + block.z()).min().orElse(session.anchor.getBlockZ()));
            Location max = new Location(session.anchor.getWorld(),
                blocks.stream().mapToInt(block -> session.anchor.getBlockX() + block.x()).max().orElse(session.anchor.getBlockX()),
                blocks.stream().mapToInt(block -> session.anchor.getBlockY() + block.y()).max().orElse(session.anchor.getBlockY()),
                blocks.stream().mapToInt(block -> session.anchor.getBlockZ() + block.z()).max().orElse(session.anchor.getBlockZ()));
            if (!plugin.getRegionProtectionManager().upsertBuildRegion(buildId, min, max, session.protect)) {
                if (previousRecord == null) builds.remove(buildId); else builds.put(buildId, previousRecord);
                saveBuilds();
                restoreSnapshots(snapshots, false);
                player.sendMessage("§cKhông thể lưu bảo vệ công trình; thao tác dựng đã được hoàn tác.");
                plugin.getLogger().warning("[SchemaBuild] Protection save failed; rolled back build id=" + buildId);
                return;
            }
        }
        selectedBuilds.put(player.getUniqueId(), buildId);
        Deque<BuildOperation> undo = undoHistory.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>());
        undo.push(operation);
        while (undo.size() > historyLimit()) undo.removeLast();
        redoHistory.remove(player.getUniqueId());
        player.sendMessage("§aĐã dựng schema §f" + session.schema.schema().name() + "§a: " + snapshots.size() + " block. ID: §f" + buildId);
        plugin.getLogger().info("[SchemaBuild] build-complete player=" + player.getName() + ", schema="
            + session.schema.relativePath() + ", blocks=" + snapshots.size() + ", world="
            + session.anchor.getWorld().getName() + ", anchor=" + locationText(session.anchor)
            + ", rotation=" + session.rotationX + "/" + session.rotationY + "/" + session.rotationZ + ", id=" + buildId
            + ", protect=" + session.protect);
        sessions.remove(player.getUniqueId());
        removePreview(session);
        openList(player, 0);
    }

    private void undo(Player player) {
        Deque<BuildOperation> undo = undoHistory.get(player.getUniqueId());
        if (undo == null || undo.isEmpty()) { player.sendMessage("§eKhông có schema build để Undo."); return; }
        BuildOperation operation = undo.peek();
        if (containsTileEntity(operation)) {
            player.sendMessage("§cKhông thể Undo an toàn: công trình có block entity (rương/sign...) mà schema không lưu dữ liệu bên trong.");
            return;
        }
        if (!matches(operation, true)) { player.sendMessage("§cKhông thể Undo: một hay nhiều block đã bị thay đổi sau khi dựng."); return; }
        undo.pop();
        restoreSnapshots(operation.blocks(), false);
        redoHistory.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>()).push(operation);
        player.sendMessage("§aĐã Undo schema " + operation.schema() + ".");
        plugin.getLogger().info("[SchemaBuild] undo player=" + player.getName() + ", schema=" + operation.schema());
    }

    private void redo(Player player) {
        Deque<BuildOperation> redo = redoHistory.get(player.getUniqueId());
        if (redo == null || redo.isEmpty()) { player.sendMessage("§eKhông có Schema Build nào để Redo."); return; }
        BuildOperation operation = redo.peek();
        if (containsTileEntity(operation)) {
            player.sendMessage("§cKhông thể Redo an toàn: công trình có block entity mà schema không lưu dữ liệu bên trong.");
            return;
        }
        if (!matches(operation, false)) { player.sendMessage("§cKhông thể Redo: vị trí đã bị thay đổi sau Undo."); return; }
        redo.pop();
        restoreSnapshots(operation.blocks(), true);
        undoHistory.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>()).push(operation);
        player.sendMessage("§aĐã Redo schema " + operation.schema() + ".");
        plugin.getLogger().info("[SchemaBuild] redo player=" + player.getName() + ", schema=" + operation.schema());
    }

    private boolean matches(BuildOperation operation, boolean expectAfter) {
        for (BlockSnapshot snapshot : operation.blocks()) {
            if (!snapshot.location().getWorld().isChunkLoaded(snapshot.location().getBlockX() >> 4,
                    snapshot.location().getBlockZ() >> 4)) return false;
            String expected = expectAfter ? snapshot.afterData() : snapshot.beforeData();
            if (!snapshot.location().getBlock().getBlockData().getAsString().equals(expected)) return false;
        }
        return true;
    }

    private boolean containsTileEntity(BuildOperation operation) {
        for (BlockSnapshot snapshot : operation.blocks()) {
            Location location = snapshot.location();
            if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) return false;
            if (location.getBlock().getState() instanceof org.bukkit.block.TileState) return true;
        }
        return false;
    }

    private void restoreSnapshots(List<BlockSnapshot> snapshots, boolean useAfter) {
        for (BlockSnapshot snapshot : snapshots) {
            Block block = snapshot.location().getBlock();
            block.setBlockData(Bukkit.createBlockData(useAfter ? snapshot.afterData() : snapshot.beforeData()), false);
        }
    }

    private int historyLimit() {
        return Math.max(1, Math.min(50, plugin.getConfig().getInt("schema-build.history-size", DEFAULT_HISTORY_SIZE)));
    }

    private String locationText(Location location) {
        return location == null || location.getWorld() == null ? "?"
            : location.getWorld().getName() + " " + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();
    }

    private ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void removePreview(BuildSession session) {
        session.preview.forEach(BlockDisplay::remove);
        session.preview.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        BuildSession session = sessions.remove(event.getPlayer().getUniqueId());
        if (session != null) removePreview(session);
    }

    public void shutdown() {
        previewTask.cancel();
        sessions.values().forEach(this::removePreview);
        sessions.clear();
    }

    private record SchemaFile(String relativePath, SchemaDefinition schema) { }
    private record SchemaDefinition(String name, List<SchemaBlock> blocks, int sizeX, int sizeY, int sizeZ) { }
    private record SchemaBlock(int x, int y, int z, String blockData) { }
    private record RotatedBlock(int x, int y, int z, String blockData) { }
    private record PlacedBlock(int x, int y, int z, String blockData) { }
    private record BlockSnapshot(Location location, String beforeData, String afterData, String baselineBeforeData) { }
    private record BuildOperation(String schema, List<BlockSnapshot> blocks) { }
    private enum View { LIST, BUILDER }

    private static final class BuildRecord {
        private final String id, schemaPath, world;
        private final Location anchor;
        private final int rotationX, rotationY, rotationZ;
        private final boolean flipX, flipY, flipZ, protect;
        private final List<BlockSnapshot> blocks;
        private BuildRecord(String id, String schemaPath, Location anchor, int rotationX, int rotationY, int rotationZ,
                            boolean flipX, boolean flipY, boolean flipZ, boolean protect, List<BlockSnapshot> blocks) {
            this(id, schemaPath, anchor.getWorld().getName(), anchor, rotationX, rotationY, rotationZ,
                flipX, flipY, flipZ, protect, blocks);
        }
        private BuildRecord(String id, String schemaPath, String world, Location anchor, int rotationX, int rotationY, int rotationZ,
                            boolean flipX, boolean flipY, boolean flipZ, boolean protect, List<BlockSnapshot> blocks) {
            this.id = id; this.schemaPath = schemaPath; this.anchor = anchor.clone();
            this.world = world; this.rotationX = rotationX; this.rotationY = rotationY; this.rotationZ = rotationZ;
            this.flipX = flipX; this.flipY = flipY; this.flipZ = flipZ; this.protect = protect; this.blocks = blocks;
        }
    }

    private static final class BuildSession {
        private final SchemaFile schema;
        private final List<BlockDisplay> preview = new ArrayList<>();
        private Location anchor;
        private int rotationX;
        private int rotationY;
        private int rotationZ;
        private boolean flipX;
        private boolean flipY;
        private boolean flipZ;
        private boolean protect;
        private String buildId;
        private List<BlockSnapshot> baseline;
        private volatile boolean placementPreview;
        private boolean previewTruncated;
        private BuildSession(SchemaFile schema) { this.schema = schema; }
    }

    private static final class SchemaHolder implements InventoryHolder {
        private final UUID viewer;
        private final View view;
        private final int page;
        private final Map<Integer, SchemaFile> choices = new HashMap<>();
        private Inventory inventory;
        private final BuildSession session;
        private SchemaHolder(UUID viewer, View view, int page) { this(viewer, view, page, null); }
        private SchemaHolder(UUID viewer, View view, int page, BuildSession session) {
            this.viewer = viewer; this.view = view; this.page = page; this.session = session;
        }
        @Override public Inventory getInventory() { return inventory; }
    }
}
