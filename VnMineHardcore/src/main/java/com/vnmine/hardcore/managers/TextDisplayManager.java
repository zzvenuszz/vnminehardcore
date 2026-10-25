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
import java.util.logging.Level;
import java.util.regex.Pattern;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.yaml.snakeyaml.error.YAMLException;

/** Persistent, configurable text display entities managed through /vntext. */
public final class TextDisplayManager implements Listener, CommandExecutor, TabCompleter {
    private static final String ADMIN_PERMISSION = "vnmine.command.text.manage";
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+){0,7}");
    private static final Map<String, TextColor> COLORS = createColors();

    private final VnMineHardcore plugin;
    private final File storageFile;
    private final NamespacedKey textIdKey;
    private final Map<String, TextDefinition> definitions = new LinkedHashMap<>();
    private final int maximumDisplays;

    public TextDisplayManager(VnMineHardcore plugin) throws IOException {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "text-displays.yml");
        this.textIdKey = new NamespacedKey(plugin, "managed_text_display_id");
        this.maximumDisplays = Math.max(1, Math.min(500,
            plugin.getConfig().getInt("rpg.text-display.max-active", 100)));
        load();
        if (!storageFile.isFile() && !save()) {
            throw new IOException("Could not create text-displays.yml.");
        }
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) reconcileChunk(chunk);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cBạn không có quyền quản lý text display.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            definitions.values().stream().sorted(Comparator.comparing(TextDefinition::id))
                .forEach(definition -> sender.sendMessage("§6" + definition.id() + " §7• "
                    + definition.world() + " §f" + coordinates(definition.x(), definition.y(), definition.z())
                    + " §7• facing " + definition.facing() + " • rotation " + definition.rotationDegrees()
                    + "° • " + definition.color() + " • scale "
                    + definition.scale()));
            if (definitions.isEmpty()) sender.sendMessage("§7Chưa có text display nào.");
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
            TextDefinition definition = definitions.get(normalizeId(args[1]));
            if (definition == null) sender.sendMessage("§cKhông tìm thấy text display.");
            else sender.sendMessage("§6" + definition.id() + " §7• " + definition.world() + " §f"
                + coordinates(definition.x(), definition.y(), definition.z()) + " §7• facing="
                + definition.facing() + " rotation=" + definition.rotationDegrees() + "° color="
                + definition.color() + " scale=" + definition.scale()
                + " text=§f" + definition.text());
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            remove(sender, normalizeId(args[1]));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cLệnh này cần người chơi tại chỗ để tạo, sửa hoặc di chuyển text.");
            sendUsage(sender);
            return true;
        }
        if ((args.length >= 6) && (args[0].equalsIgnoreCase("create") || args[0].equalsIgnoreCase("edit"))) {
            createOrEdit(player, args, args[0].equalsIgnoreCase("edit"));
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("move")) {
            move(player, normalizeId(args[1]));
            return true;
        }
        sendUsage(sender);
        return true;
    }

    private void createOrEdit(Player player, String[] args, boolean editing) {
        String id = normalizeId(args[1]);
        if (!ID_PATTERN.matcher(id).matches()) {
            player.sendMessage("§cID không hợp lệ; dùng chữ thường, số và dấu gạch nối, ví dụ: §fnpc-label");
            return;
        }
        TextDefinition old = definitions.get(id);
        if (editing && old == null) {
            player.sendMessage("§cKhông tìm thấy text display §f" + id + "§c.");
            return;
        }
        if (!editing && old != null) {
            player.sendMessage("§cID này đã tồn tại. Dùng §f/vntext edit " + id + " ...§c.");
            return;
        }
        if (!editing && definitions.size() >= maximumDisplays) {
            player.sendMessage("§cĐã đạt giới hạn " + maximumDisplays + " text display.");
            return;
        }
        Facing facing = Facing.parse(args[2]);
        boolean hasRotation = args.length >= 7 && parseRotation(args[3]) != null;
        int colorIndex = hasRotation ? 4 : 3;
        int scaleIndex = hasRotation ? 5 : 4;
        int textStart = hasRotation ? 6 : 5;
        Float rotation = hasRotation ? parseRotation(args[3]) : 0.0f;
        TextColor color = parseColor(args[colorIndex]);
        float scale;
        try {
            scale = Float.parseFloat(args[scaleIndex]);
        } catch (NumberFormatException exception) {
            scale = -1.0f;
        }
        String text = String.join(" ", java.util.Arrays.copyOfRange(args, textStart, args.length));
        if (facing == null || rotation == null || rotation < 0.0f || rotation > 360.0f || color == null
                || !Float.isFinite(scale) || scale < 0.1f || scale > 32.0f
                || text.isBlank() || text.length() > 256) {
            player.sendMessage("§cFacing, rotation, màu, kích thước hoặc nội dung không hợp lệ. Rotation: 0-360; scale: 0.1-32; text tối đa 256 ký tự.");
            sendUsage(player);
            return;
        }
        Location location;
        String worldName;
        if (editing) {
            World world = Bukkit.getWorld(old.world());
            if (world == null) {
                player.sendMessage("§cThế giới đang chứa text chưa được tải; hãy tới world đó trước khi chỉnh sửa.");
                return;
            }
            worldName = old.world();
            location = new Location(world, old.x(), old.y(), old.z());
        } else {
            location = player.getLocation().clone().add(0.0, 0.02, 0.0);
            worldName = player.getWorld().getName();
        }
        TextDefinition next = new TextDefinition(id, worldName,
            location.getX(), location.getY(), location.getZ(), facing, rotation,
            canonicalColor(args[colorIndex]), scale, text, old == null ? null : old.entityId());
        definitions.put(id, next);
        TextDisplay entity = null;
        try {
            entity = findDisplay(next);
            if (entity == null) entity = spawn(next, location);
            else apply(entity, next, location);
            next = next.withEntityId(entity.getUniqueId());
            definitions.put(id, next);
            if (!save()) throw new IOException("Atomic save failed.");
            plugin.getLogger().info("[TextDisplay] " + (editing ? "Updated" : "Created") + " id=" + id
                + ", world=" + next.world() + ", facing=" + facing + ", rotation=" + rotation
                + ", color=" + next.color()
                + ", scale=" + scale);
            player.sendMessage("§aĐã " + (editing ? "cập nhật" : "tạo") + " text display §f" + id
                + " §atại §f" + next.world() + " " + coordinates(next.x(), next.y(), next.z())
                + "§a. Facing §f" + facing + "§a, xoay §f" + rotation + "°§a, màu §f"
                + next.color() + "§a, scale §f" + scale + "§a.");
        } catch (RuntimeException | IOException exception) {
            if (old == null) {
                definitions.remove(id);
                if (entity != null) entity.remove();
            } else {
                definitions.put(id, old);
                World oldWorld = Bukkit.getWorld(old.world());
                if (entity != null && oldWorld != null) {
                    apply(entity, old, new Location(oldWorld, old.x(), old.y(), old.z()));
                }
            }
            plugin.getLogger().log(Level.SEVERE, "[TextDisplay] Could not save display '" + id + "'.", exception);
            player.sendMessage("§cKhông thể lưu text display; hãy xem console.");
        }
    }

    private void move(Player player, String id) {
        TextDefinition old = definitions.get(id);
        if (old == null) {
            player.sendMessage("§cKhông tìm thấy text display §f" + id + "§c.");
            return;
        }
        Location location = player.getLocation().clone().add(0.0, 0.02, 0.0);
        TextDefinition moved = old.withLocation(player.getWorld().getName(), location);
        definitions.put(id, moved);
        try {
            TextDisplay entity = findDisplay(old);
            if (entity == null) entity = spawn(moved, location);
            else apply(entity, moved, location);
            definitions.put(id, moved.withEntityId(entity.getUniqueId()));
            if (!save()) throw new IOException("Atomic save failed.");
            plugin.getLogger().info("[TextDisplay] Moved id=" + id + ", world=" + moved.world());
            player.sendMessage("§aĐã di chuyển text §f" + id + " §atới vị trí hiện tại.");
        } catch (RuntimeException | IOException exception) {
            definitions.put(id, old);
            World world = Bukkit.getWorld(old.world());
            if (world != null) {
                TextDisplay display = findDisplay(old);
                if (display != null) apply(display, old, new Location(world, old.x(), old.y(), old.z()));
            }
            plugin.getLogger().log(Level.SEVERE, "[TextDisplay] Could not move display '" + id + "'.", exception);
            player.sendMessage("§cKhông thể di chuyển text; hãy xem console.");
        }
    }

    private void remove(CommandSender sender, String id) {
        TextDefinition old = definitions.remove(id);
        if (old == null) {
            sender.sendMessage("§cKhông tìm thấy text display §f" + id + "§c.");
            return;
        }
        Entity entity = old.entityId() == null ? null : Bukkit.getEntity(old.entityId());
        if (entity != null) entity.remove();
        try {
            if (!save()) throw new IOException("Atomic save failed.");
            plugin.getLogger().info("[TextDisplay] Removed id=" + id);
            sender.sendMessage("§aĐã xóa text display §f" + id + "§a.");
        } catch (IOException exception) {
            definitions.put(id, old);
            World world = Bukkit.getWorld(old.world());
            if (world != null && world.isChunkLoaded(blockToChunk(old.x()), blockToChunk(old.z()))) {
                reconcileChunk(world.getChunkAt(blockToChunk(old.x()), blockToChunk(old.z())));
            }
            plugin.getLogger().log(Level.SEVERE, "[TextDisplay] Could not persist removal of '" + id + "'.", exception);
            sender.sendMessage("§cKhông lưu được thao tác xóa; hãy xem console.");
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        reconcileChunk(event.getChunk());
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        for (Chunk chunk : event.getWorld().getLoadedChunks()) reconcileChunk(chunk);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDisplayDamage(EntityDamageEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(textIdKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    private void reconcileChunk(Chunk chunk) {
        Map<String, List<TextDisplay>> loaded = new LinkedHashMap<>();
        for (Entity entity : chunk.getEntities()) {
            String id = entity.getPersistentDataContainer().get(textIdKey, PersistentDataType.STRING);
            if (id == null) continue;
            if (!(entity instanceof TextDisplay display) || !definitions.containsKey(id)) {
                entity.remove();
                continue;
            }
            loaded.computeIfAbsent(id, ignored -> new ArrayList<>()).add(display);
        }
        for (TextDefinition definition : definitions.values()) {
            if (!definition.world().equals(chunk.getWorld().getName())
                    || (blockToChunk(definition.x()) != chunk.getX())
                    || (blockToChunk(definition.z()) != chunk.getZ())) continue;
            List<TextDisplay> matches = loaded.getOrDefault(definition.id(), List.of());
            Entity recordedEntity = definition.entityId() == null ? null : Bukkit.getEntity(definition.entityId());
            Location recordedLocation = recordedEntity instanceof TextDisplay recordedDisplay
                && recordedDisplay.isValid() ? recordedDisplay.getLocation() : null;
            boolean recordedElsewhere = recordedLocation != null
                && (!recordedLocation.getWorld().equals(chunk.getWorld())
                    || blockToChunk(recordedLocation.getX()) != chunk.getX()
                    || blockToChunk(recordedLocation.getZ()) != chunk.getZ());
            if (recordedElsewhere) {
                if (recordedEntity instanceof TextDisplay recordedDisplay
                        && definition.id().equals(recordedDisplay.getPersistentDataContainer()
                            .get(textIdKey, PersistentDataType.STRING))) {
                    recordedDisplay.remove();
                }
                for (TextDisplay stale : matches) stale.remove();
                matches = List.of();
            }
            TextDisplay display = matches.stream()
                .filter(candidate -> definition.entityId() != null && candidate.getUniqueId().equals(definition.entityId()))
                .findFirst().orElse(matches.isEmpty() ? null : matches.getFirst());
            for (TextDisplay duplicate : matches) if (duplicate != display) duplicate.remove();
            Location location = new Location(chunk.getWorld(), definition.x(), definition.y(), definition.z());
            if (display == null) display = spawn(definition, location);
            else apply(display, definition, location);
            if (!display.getUniqueId().equals(definition.entityId())) {
                definitions.put(definition.id(), definition.withEntityId(display.getUniqueId()));
                save();
            }
        }
    }

    private TextDisplay findDisplay(TextDefinition definition) {
        Entity entity = definition.entityId() == null ? null : Bukkit.getEntity(definition.entityId());
        if (entity instanceof TextDisplay display && display.isValid()) return display;
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (Entity candidate : chunk.getEntities()) {
                    if (candidate instanceof TextDisplay display
                            && definition.id().equals(display.getPersistentDataContainer()
                                .get(textIdKey, PersistentDataType.STRING))) return display;
                }
            }
        }
        return null;
    }

    private TextDisplay spawn(TextDefinition definition, Location location) {
        World world = location.getWorld();
        if (world == null) throw new IllegalStateException("Cannot spawn a text display without a world.");
        Entity entity = world.spawnEntity(location, EntityType.TEXT_DISPLAY);
        if (!(entity instanceof TextDisplay display)) {
            entity.remove();
            throw new IllegalStateException("The server did not create a text_display entity.");
        }
        try {
            apply(display, definition, location);
            return display;
        } catch (RuntimeException exception) {
            display.remove();
            throw exception;
        }
    }

    private void apply(TextDisplay display, TextDefinition definition, Location location) {
        display.getPersistentDataContainer().set(textIdKey, PersistentDataType.STRING, definition.id());
        display.setText(legacyColor(definition.color()) + org.bukkit.ChatColor.BOLD + definition.text());
        display.setBillboard(Display.Billboard.FIXED);
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setDefaultBackground(false);
        display.setShadowed(true);
        display.setSeeThrough(false);
        display.setGravity(false);
        display.setPersistent(true);
        display.setInvulnerable(true);
        Vector3f scale = new Vector3f(definition.scale(), definition.scale(), definition.scale());
        Quaternionf roll = new Quaternionf().rotationZ((float) Math.toRadians(definition.rotationDegrees()));
        display.setTransformation(new org.bukkit.util.Transformation(
            new Vector3f(), roll, scale, new Quaternionf()));
        display.teleport(location);
        Facing facing = definition.facing();
        display.setRotation(facing.yaw(), facing.pitch());
    }

    private void load() throws IOException {
        if (!storageFile.isFile()) return;
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            YamlDuplicateKeyValidator.validateNoDuplicateKeys(storageFile);
            yaml.load(storageFile);
        } catch (InvalidConfigurationException | YAMLException exception) {
            throw new IOException("Text display data file is invalid YAML.", exception);
        }
        if (yaml.getInt("schema-version", -1) != 1) {
            throw new IOException("Unsupported text display schema-version; expected 1.");
        }
        if (yaml.getConfigurationSection("displays") == null) return;
        if (yaml.getConfigurationSection("displays").getKeys(false).size() > maximumDisplays) {
            throw new IOException("Text display count exceeds configured maximum.");
        }
        for (String rawId : yaml.getConfigurationSection("displays").getKeys(false)) {
            String id = normalizeId(rawId);
            String path = "displays." + rawId;
            String worldName = yaml.getString(path + ".world", "");
            String facingValue = yaml.getString(path + ".facing", "NORTH");
            String colorValue = yaml.getString(path + ".color", "gold");
            String text = yaml.getString(path + ".text", "");
            double x = yaml.getDouble(path + ".x", Double.NaN);
            double y = yaml.getDouble(path + ".y", Double.NaN);
            double z = yaml.getDouble(path + ".z", Double.NaN);
            float scale = (float) yaml.getDouble(path + ".scale", 1.0);
            float rotation = (float) yaml.getDouble(path + ".rotation-degrees", 0.0);
            Facing facing = Facing.parse(facingValue);
            UUID entityId;
            try {
                String entityText = yaml.getString(path + ".entity-uuid", "");
                entityId = entityText.isBlank() ? null : UUID.fromString(entityText);
            } catch (IllegalArgumentException exception) {
                entityId = null;
            }
            if (!ID_PATTERN.matcher(id).matches() || worldName.isBlank() || facing == null
                    || !Float.isFinite(rotation) || rotation < 0.0f || rotation > 360.0f
                    || parseColor(colorValue) == null || !Double.isFinite(x) || !Double.isFinite(y)
                    || !Double.isFinite(z) || !Float.isFinite(scale) || scale < 0.1f || scale > 32.0f
                    || text.isBlank() || text.length() > 256) {
                plugin.getLogger().warning("[TextDisplay] Ignoring invalid definition '" + rawId + "'.");
                continue;
            }
            definitions.put(id, new TextDefinition(id, worldName, x, y, z,
                facing, rotation, canonicalColor(colorValue), scale, text, entityId));
        }
        plugin.getLogger().info("[TextDisplay] Loaded " + definitions.size() + " managed text displays.");
    }

    private boolean save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 1);
        yaml.setComments("schema-version", List.of(
            "[VI] Phiên bản cấu trúc file text display; không chỉnh sửa thủ công.",
            "[EN] Text display data schema version; do not edit manually."));
        for (TextDefinition definition : definitions.values()) {
            String path = "displays." + definition.id();
            yaml.setComments(path, List.of(
                "[VI] Text display cố định do /vntext quản lý.",
                "[EN] Fixed text display managed through /vntext."));
            yaml.set(path + ".world", definition.world());
            yaml.set(path + ".x", definition.x());
            yaml.set(path + ".y", definition.y());
            yaml.set(path + ".z", definition.z());
            yaml.set(path + ".facing", definition.facing().name());
            yaml.set(path + ".rotation-degrees", definition.rotationDegrees());
            yaml.set(path + ".color", definition.color());
            yaml.set(path + ".scale", definition.scale());
            yaml.set(path + ".text", definition.text());
            yaml.set(path + ".entity-uuid", definition.entityId() == null ? "" : definition.entityId().toString());
            yaml.setComments(path + ".world", List.of(
                "[VI] Tên world lưu vị trí text.", "[EN] World name containing this text display."));
            for (String axis : List.of("x", "y", "z")) {
                yaml.setComments(path + "." + axis, List.of(
                    "[VI] Tọa độ " + axis.toUpperCase(Locale.ROOT) + " chính xác của text.",
                    "[EN] Exact text display " + axis.toUpperCase(Locale.ROOT) + " coordinate."));
            }
            yaml.setComments(path + ".facing", List.of(
                "[VI] Hướng mặt phẳng text: UP, DOWN, NORTH, SOUTH, EAST hoặc WEST.",
                "[EN] Text plane facing: UP, DOWN, NORTH, SOUTH, EAST, or WEST."));
            yaml.setComments(path + ".rotation-degrees", List.of(
                "[VI] Góc xoay quanh pháp tuyến mặt chữ, độc lập với facing; mặc định 0, hợp lệ 0-360 độ.",
                "[EN] Roll around the text-plane normal, independent of facing; defaults to 0, valid range 0-360 degrees."));
            yaml.setComments(path + ".color", List.of(
                "[VI] Màu tên hoặc mã hex #RRGGBB; ví dụ gold, orange, aqua, #FFAA00.",
                "[EN] Named color or #RRGGBB hex color; for example gold, orange, aqua, #FFAA00."));
            yaml.setComments(path + ".scale", List.of(
                "[VI] Kích thước hiển thị; phạm vi 0.1-32.",
                "[EN] Display scale; valid range 0.1-32."));
            yaml.setComments(path + ".text", List.of(
                "[VI] Nội dung hiển thị; tối đa 256 ký tự.",
                "[EN] Display text; maximum length is 256 characters."));
            yaml.setComments(path + ".entity-uuid", List.of(
                "[VI] UUID thực thể runtime; do plugin tự đồng bộ.",
                "[EN] Runtime entity UUID; synchronized by the plugin."));
        }
        File temporary = null;
        try {
            File parent = storageFile.getAbsoluteFile().getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs()) return false;
            temporary = Files.createTempFile(parent.toPath(), "text-displays-", ".yml.tmp").toFile();
            Files.writeString(temporary.toPath(), yaml.saveToString(), java.nio.charset.StandardCharsets.UTF_8);
            try {
                Files.move(temporary.toPath(), storageFile.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary.toPath(), storageFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "[TextDisplay] Could not save text display definitions.", exception);
            if (temporary != null) temporary.delete();
            return false;
        }
    }

    private static Map<String, TextColor> createColors() {
        Map<String, TextColor> colors = new LinkedHashMap<>();
        colors.put("black", NamedTextColor.BLACK);
        colors.put("dark_blue", NamedTextColor.DARK_BLUE);
        colors.put("dark_green", NamedTextColor.DARK_GREEN);
        colors.put("dark_aqua", NamedTextColor.DARK_AQUA);
        colors.put("dark_red", NamedTextColor.DARK_RED);
        colors.put("dark_purple", NamedTextColor.DARK_PURPLE);
        colors.put("gold", NamedTextColor.GOLD);
        colors.put("gray", NamedTextColor.GRAY);
        colors.put("dark_gray", NamedTextColor.DARK_GRAY);
        colors.put("blue", NamedTextColor.BLUE);
        colors.put("green", NamedTextColor.GREEN);
        colors.put("aqua", NamedTextColor.AQUA);
        colors.put("red", NamedTextColor.RED);
        colors.put("light_purple", NamedTextColor.LIGHT_PURPLE);
        colors.put("yellow", NamedTextColor.YELLOW);
        colors.put("white", NamedTextColor.WHITE);
        colors.put("orange", TextColor.color(0xFFAA00));
        colors.put("pink", TextColor.color(0xFF55FF));
        colors.put("cyan", TextColor.color(0x55FFFF));
        return Map.copyOf(colors);
    }

    private static TextColor parseColor(String raw) {
        if (raw == null) return null;
        String value = raw.toLowerCase(Locale.ROOT);
        TextColor named = COLORS.get(value);
        if (named != null) return named;
        if (value.matches("#[0-9a-f]{6}")) return TextColor.fromHexString(value);
        return null;
    }

    private static String canonicalColor(String raw) {
        return raw.startsWith("#") ? raw.toUpperCase(Locale.ROOT) : raw.toLowerCase(Locale.ROOT);
    }

    private static String legacyColor(String raw) {
        if (raw.startsWith("#")) return net.md_5.bungee.api.ChatColor.of(raw).toString();
        String normalized = switch (raw.toLowerCase(Locale.ROOT)) {
            case "orange" -> "GOLD";
            case "pink" -> "LIGHT_PURPLE";
            case "cyan" -> "AQUA";
            default -> raw.toUpperCase(Locale.ROOT);
        };
        return org.bukkit.ChatColor.valueOf(normalized).toString();
    }

    private static String normalizeId(String raw) {
        return raw == null ? "" : raw.toLowerCase(Locale.ROOT);
    }

    private static Float parseRotation(String raw) {
        try {
            float value = Float.parseFloat(raw);
            return Float.isFinite(value) ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static int blockToChunk(double coordinate) {
        return ((int) Math.floor(coordinate)) >> 4;
    }

    private static String coordinates(double x, double y, double z) {
        return String.format(Locale.ROOT, "%.2f, %.2f, %.2f", x, y, z);
    }

    private static void sendUsage(CommandSender sender) {
        sender.sendMessage("§e/vntext create <id> <facing> <rotation 0-360> <color> <scale> <text...>");
        sender.sendMessage("§e/vntext edit <id> <facing> <rotation 0-360> <color> <scale> <text...>");
        sender.sendMessage("§7Cú pháp cũ không có rotation vẫn được hỗ trợ (mặc định 0°).");
        sender.sendMessage("§e/vntext move <id> §7| §e/vntext remove <id> §7| §e/vntext info <id> §7| §e/vntext list");
        sender.sendMessage("§7Facing: UP/DOWN/NORTH/SOUTH/EAST/WEST • rotation quanh mặt chữ: 0-360° • màu: gold/orange/white/#RRGGBB • scale: 0.1-32.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) return List.of();
        if (args.length == 1) {
            return List.of("create", "edit", "move", "remove", "info", "list").stream()
                .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && List.of("edit", "move", "remove", "info").contains(args[0].toLowerCase(Locale.ROOT))) {
            return definitions.keySet().stream().filter(id -> id.startsWith(args[1].toLowerCase(Locale.ROOT))).sorted().toList();
        }
        if (args.length == 3 && List.of("create", "edit").contains(args[0].toLowerCase(Locale.ROOT))) {
            return List.of("UP", "DOWN", "NORTH", "SOUTH", "EAST", "WEST").stream()
                .filter(value -> value.startsWith(args[2].toUpperCase(Locale.ROOT))).toList();
        }
        if (args.length == 4 && List.of("create", "edit").contains(args[0].toLowerCase(Locale.ROOT))) {
            if (args[3].isBlank()) {
                List<String> suggestions = new ArrayList<>(COLORS.keySet());
                suggestions.addAll(List.of("0", "45", "90", "180", "270"));
                return suggestions.stream().sorted().toList();
            }
            if (parseRotation(args[3]) != null) return List.of("0", "45", "90", "180", "270");
            return COLORS.keySet().stream().filter(value -> value.startsWith(args[3].toLowerCase(Locale.ROOT)))
                .sorted().toList();
        }
        if (args.length == 5 && List.of("create", "edit").contains(args[0].toLowerCase(Locale.ROOT))
                && parseRotation(args[3]) != null) {
            return COLORS.keySet().stream().filter(value -> value.startsWith(args[4].toLowerCase(Locale.ROOT)))
                .sorted().toList();
        }
        if (args.length == 6 && List.of("create", "edit").contains(args[0].toLowerCase(Locale.ROOT))
                && parseRotation(args[3]) != null) {
            return List.of("0.5", "1", "2", "4", "9", "16").stream()
                .filter(value -> value.startsWith(args[5])).toList();
        }
        return List.of();
    }

    private enum Facing {
        UP(0.0f, -90.0f), DOWN(0.0f, 90.0f), NORTH(180.0f, 0.0f), SOUTH(0.0f, 0.0f),
        EAST(-90.0f, 0.0f), WEST(90.0f, 0.0f);

        private final float yaw;
        private final float pitch;

        Facing(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }

        private float yaw() { return yaw; }
        private float pitch() { return pitch; }

        private static Facing parse(String value) {
            try {
                return Facing.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
    }

    private record TextDefinition(String id, String world, double x, double y, double z,
                                  Facing facing, float rotationDegrees, String color, float scale,
                                  String text, UUID entityId) {
        private TextDefinition withEntityId(UUID value) {
            return new TextDefinition(id, world, x, y, z, facing, rotationDegrees, color, scale, text, value);
        }

        private TextDefinition withLocation(String worldName, Location location) {
            return new TextDefinition(id, worldName, location.getX(), location.getY(), location.getZ(),
                facing, rotationDegrees, color, scale, text, entityId);
        }
    }
}
