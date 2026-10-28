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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rotatable;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.bukkit.configuration.file.YamlConfiguration;

/** Admin GUI for placing small-to-medium YAML .schema structures with a private BlockDisplay preview. */
public final class SchemaBuildManager implements Listener {
    private static final String PERMISSION = "vnmine.command.schemabuild";
    private static final int PAGE_SIZE = 45;
    private static final int DEFAULT_MAX_BLOCKS = 5000;
    private static final int DEFAULT_PREVIEW_BLOCKS = 1200;
    private static final int DEFAULT_HISTORY_SIZE = 10;
    private final VnMineHardcore plugin;
    private final Path schemasRoot;
    private final Map<UUID, BuildSession> sessions = new HashMap<>();
    private final Map<UUID, Deque<BuildOperation>> undoHistory = new HashMap<>();
    private final Map<UUID, Deque<BuildOperation>> redoHistory = new HashMap<>();
    private final BukkitTask previewTask;

    public SchemaBuildManager(VnMineHardcore plugin) throws IOException {
        this.plugin = plugin;
        this.schemasRoot = new File(plugin.getDataFolder(), "schemas").toPath().toAbsolutePath().normalize();
        Files.createDirectories(schemasRoot);
        File example = schemasRoot.resolve("examples").resolve("small-platform.schema").toFile();
        if (!example.isFile()) {
            File parent = example.getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Could not create schema example directory.");
            try {
                plugin.saveResource("schemas/examples/small-platform.schema", false);
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
                "§eNhấn để xem bóng mờ và đặt.")));
        }
        if (files.isEmpty()) menu.setItem(22, icon(Material.BARRIER, "§cChưa có schema hợp lệ", List.of(
            "§7Đặt file .schema theo định dạng YAML vào:", "§fplugins/VnMineHardcore/schemas/",
            "§7Có thể tạo thư mục con để nhóm công trình.", "§eVí dụ: schemas/examples/small-platform.schema")));
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
                    .filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".schema"))
                    .toList()) {
                if (!path.toRealPath().startsWith(rootReal)) continue;
                String relative = schemasRoot.relativize(path).toString().replace(File.separatorChar, '/');
                try {
                    files.add(new SchemaFile(relative, loadSchema(path)));
                } catch (IllegalArgumentException | IOException exception) {
                    plugin.getLogger().log(Level.WARNING, "[SchemaBuild] Ignoring invalid schema " + relative, exception);
                    player.sendMessage("§cBỏ qua schema lỗi: §f" + relative + " §7(xem console).");
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
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        int schemaVersion = yaml.getInt("schema-version", -1);
        if (schemaVersion != 1) throw new IllegalArgumentException("schema-version must be 1");
        String name = yaml.getString("name", file.getFileName().toString().replaceFirst("(?i)\\.schema$", "")).trim();
        List<Map<?, ?>> entries = yaml.getMapList("blocks");
        int maxBlocks = Math.max(1, Math.min(100_000,
            plugin.getConfig().getInt("schema-build.max-blocks", DEFAULT_MAX_BLOCKS)));
        if (entries.isEmpty() || entries.size() > maxBlocks)
            throw new IllegalArgumentException("blocks must contain 1.." + maxBlocks + " entries");
        List<SchemaBlock> blocks = new ArrayList<>(entries.size());
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        java.util.Set<String> positions = new HashSet<>();
        for (Map<?, ?> entry : entries) {
            int x = integer(entry.get("x"), "x");
            int y = integer(entry.get("y"), "y");
            int z = integer(entry.get("z"), "z");
            if (Math.abs((long) x) > 512 || Math.abs((long) y) > 512 || Math.abs((long) z) > 512)
                throw new IllegalArgumentException("block coordinates must be within -512..512");
            if (!positions.add(x + "," + y + "," + z))
                throw new IllegalArgumentException("duplicate block position " + x + "," + y + "," + z);
            Object materialValue = entry.get("material");
            if (!(materialValue instanceof String materialText)) throw new IllegalArgumentException("material is required");
            Material material = Material.matchMaterial(materialText);
            if (material == null || !material.isBlock() || material.isAir())
                throw new IllegalArgumentException("unknown/non-placeable block material: " + materialText);
            Object dataValue = entry.get("block-data");
            BlockData data;
            try {
                data = dataValue instanceof String text && !text.isBlank()
                    ? Bukkit.createBlockData(text) : material.createBlockData();
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("invalid block-data for " + materialText, exception);
            }
            if (data.getMaterial() != material) throw new IllegalArgumentException("block-data/material mismatch for " + materialText);
            blocks.add(new SchemaBlock(x, y, z, data.getAsString()));
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
        }
        return new SchemaDefinition(name, List.copyOf(blocks), maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
    }

    private int integer(Object value, String field) {
        if (!(value instanceof Number number) || number.doubleValue() != number.intValue())
            throw new IllegalArgumentException(field + " must be an integer");
        return number.intValue();
    }

    private void openBuilder(Player player, SchemaFile schema) {
        BuildSession previous = sessions.remove(player.getUniqueId());
        if (previous != null) removePreview(previous);
        BuildSession session = new BuildSession(schema);
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
            "§7Điểm neo bám theo mặt block bạn đang ngắm.")));
        menu.setItem(10, icon(Material.IRON_NUGGET, "§eXoay trục X −90°", List.of()));
        menu.setItem(16, icon(Material.GOLD_NUGGET, "§eXoay trục X +90°", List.of()));
        menu.setItem(19, icon(Material.IRON_INGOT, "§eXoay trục Y −90°", List.of()));
        menu.setItem(25, icon(Material.GOLD_INGOT, "§eXoay trục Y +90°", List.of()));
        menu.setItem(28, icon(Material.IRON_BLOCK, "§eXoay trục Z −90°", List.of()));
        menu.setItem(34, icon(Material.GOLD_BLOCK, "§eXoay trục Z +90°", List.of()));
        menu.setItem(37, icon(Material.SPECTRAL_ARROW, "§bUndo build gần nhất", List.of(
            "§7Chỉ lùi thay đổi do Schema Build thực hiện.", "§7Tối đa: §f" + historyLimit() + " bản.")));
        menu.setItem(40, icon(Material.LIME_CONCRETE, "§aXÁC NHẬN DỰNG", List.of(
            "§7Đặt §f" + session.schema.schema().blocks().size() + "§7 block tại vị trí bóng mờ.",
            "§cVật thể dạng block entity sẽ được đặt rỗng; không lưu inventory/sign data.",
            "§eNhấn để dựng.")));
        menu.setItem(43, icon(Material.TIPPED_ARROW, "§dRedo build vừa Undo", List.of()));
        menu.setItem(45, icon(Material.ARROW, "§eChọn schema khác", List.of()));
        menu.setItem(49, icon(Material.BARRIER, "§cHủy xem trước", List.of("§7Bóng mờ sẽ bị xóa.")));
        menu.setItem(53, icon(Material.COMPASS, "§bCách đặt", List.of(
            "§7Di chuyển/ngắm mặt block để chuyển bóng mờ.",
            "§7Các nút xoay thay đổi góc trên từng trục.",
            "§7Schema giới hạn block và chỉ nhận file YAML version 1.")));
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
            case 19 -> rotate(player, session, 'y', -1);
            case 25 -> rotate(player, session, 'y', 1);
            case 28 -> rotate(player, session, 'z', -1);
            case 34 -> rotate(player, session, 'z', 1);
            case 37 -> undo(player);
            case 40 -> place(player, session);
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

    private int wrap(int degrees) { return Math.floorMod(degrees, 360); }

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
        Location newAnchor = findAnchor(player);
        if (session.anchor != null && sameBlock(session.anchor, newAnchor) && !force) return;
        session.anchor = newAnchor;
        List<PlacedBlock> blocks = transform(session.schema.schema(), session.rotationX, session.rotationY, session.rotationZ);
        int displayLimit = Math.max(1, Math.min(5000,
            plugin.getConfig().getInt("schema-build.preview-blocks", DEFAULT_PREVIEW_BLOCKS)));
        int count = Math.min(blocks.size(), displayLimit);
        while (session.preview.size() > count) session.preview.remove(session.preview.size() - 1).remove();
        for (int index = 0; index < count; index++) {
            PlacedBlock placed = blocks.get(index);
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
        session.previewTruncated = blocks.size() > displayLimit;
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof SchemaHolder holder
                && holder.viewer.equals(player.getUniqueId()) && holder.view == View.BUILDER) {
            holder.inventory.setItem(4, icon(Material.STRUCTURE_BLOCK, "§b" + session.schema.schema().name(), List.of(
                "§7Khối neo: §f" + locationText(session.anchor),
                "§7Xoay X/Y/Z: §f" + session.rotationX + "° / " + session.rotationY + "° / " + session.rotationZ + "°",
                session.previewTruncated ? "§cPreview rút gọn theo giới hạn cấu hình." : "§aPreview hiển thị đầy đủ.")));
        }
        player.sendActionBar(net.kyori.adventure.text.Component.text(
            "Schema: " + session.schema.relativePath() + " | " + locationText(session.anchor)
                + " | X/Y/Z " + session.rotationX + "/" + session.rotationY + "/" + session.rotationZ));
    }

    private Location findAnchor(Player player) {
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

    private List<PlacedBlock> transform(SchemaDefinition schema, int rotateX, int rotateY, int rotateZ) {
        List<RotatedBlock> rotated = new ArrayList<>(schema.blocks().size());
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (SchemaBlock block : schema.blocks()) {
            Vector position = rotate(new Vector(block.x(), block.y(), block.z()), rotateX, rotateY, rotateZ);
            int x = position.getBlockX(), y = position.getBlockY(), z = position.getBlockZ();
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            BlockData data = Bukkit.createBlockData(block.blockData());
            rotateBlockData(data, rotateX, rotateY, rotateZ);
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

    private void rotateBlockData(BlockData data, int rotateX, int rotateY, int rotateZ) {
        if (data instanceof Directional directional) {
            Vector facing = rotate(faceVector(directional.getFacing()), rotateX, rotateY, rotateZ);
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
        if (data instanceof Rotatable rotatable && rotateY != 0) {
            BlockFace face = rotatable.getRotation();
            for (int turn = 0; turn < rotateY / 90; turn++) face = rotateHorizontal(face);
            try { rotatable.setRotation(face); } catch (IllegalArgumentException ignored) { }
        }
    }

    private Vector faceVector(BlockFace face) { return new Vector(face.getModX(), face.getModY(), face.getModZ()); }

    private BlockFace vectorFace(Vector vector) {
        for (BlockFace face : BlockFace.values()) {
            if (face.getModX() == vector.getBlockX() && face.getModY() == vector.getBlockY()
                    && face.getModZ() == vector.getBlockZ()) return face;
        }
        return BlockFace.SELF;
    }

    private BlockFace rotateHorizontal(BlockFace face) {
        Vector turned = rotate(faceVector(face), 0, 90, 0).normalize();
        return java.util.Arrays.stream(BlockFace.values())
            .filter(candidate -> candidate.getModY() == 0 && candidate != BlockFace.SELF)
            .max(Comparator.comparingDouble(candidate -> faceVector(candidate).normalize().dot(turned)))
            .orElse(face);
    }

    private void place(Player player, BuildSession session) {
        List<PlacedBlock> blocks = transform(session.schema.schema(), session.rotationX, session.rotationY, session.rotationZ);
        if (session.anchor == null || session.anchor.getWorld() == null) {
            player.sendMessage("§cChưa xác định được vị trí đặt.");
            return;
        }
        var border = session.anchor.getWorld().getWorldBorder();
        List<BlockSnapshot> snapshots = new ArrayList<>(blocks.size());
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
            snapshots.add(new BlockSnapshot(location, block.getBlockData().getAsString(), after.getAsString()));
        }
        int applied = 0;
        for (BlockSnapshot snapshot : snapshots) {
            Block block = snapshot.location().getBlock();
            BlockData after = Bukkit.createBlockData(snapshot.afterData());
            BlockPlaceEvent placeEvent = new BlockPlaceEvent(block, block.getState(), block.getRelative(BlockFace.DOWN),
                new ItemStack(after.getMaterial()), player, true, EquipmentSlot.HAND);
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
        Deque<BuildOperation> undo = undoHistory.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>());
        undo.push(operation);
        while (undo.size() > historyLimit()) undo.removeLast();
        redoHistory.remove(player.getUniqueId());
        player.sendMessage("§aĐã dựng schema §f" + session.schema.schema().name() + "§a: " + snapshots.size() + " block.");
        plugin.getLogger().info("[SchemaBuild] build-complete player=" + player.getName() + ", schema="
            + session.schema.relativePath() + ", blocks=" + snapshots.size() + ", world="
            + session.anchor.getWorld().getName() + ", anchor=" + locationText(session.anchor)
            + ", rotation=" + session.rotationX + "/" + session.rotationY + "/" + session.rotationZ);
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
    private record BlockSnapshot(Location location, String beforeData, String afterData) { }
    private record BuildOperation(String schema, List<BlockSnapshot> blocks) { }
    private enum View { LIST, BUILDER }

    private static final class BuildSession {
        private final SchemaFile schema;
        private final List<BlockDisplay> preview = new ArrayList<>();
        private Location anchor;
        private int rotationX;
        private int rotationY;
        private int rotationZ;
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
