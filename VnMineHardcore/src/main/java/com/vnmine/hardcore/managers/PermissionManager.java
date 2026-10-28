package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Pattern;

public final class PermissionManager implements Listener, CommandExecutor, TabCompleter {
    private static final String GROUP_PERMISSION = "vnmine.command.groups.manage";
    private static final String USER_PERMISSION = "vnmine.command.users.manage";
    private static final Pattern GROUP_NAME_PATTERN = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final Pattern PERMISSION_PATTERN =
        Pattern.compile("[a-z0-9_-]+(?:\\.[a-z0-9_-]+)*");

    private final VnMineHardcore plugin;
    private final File dataFile;
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();
    private YamlConfiguration data;
    private String defaultGroup;

    public PermissionManager(VnMineHardcore plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "permission.yml");
        load();
        for (Player player : Bukkit.getOnlinePlayers()) applyPermissions(player);
    }

    public boolean reload() {
        try {
            loadConfiguration();
            for (Player player : Bukkit.getOnlinePlayers()) applyPermissions(player);
            return true;
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().log(Level.SEVERE,
                "[Permissions] Could not reload " + dataFile.getName() + ".", exception);
            return false;
        }
    }

    public boolean hasPermission(CommandSender sender, String permission) {
        return sender.isOp() || sender.hasPermission(permission);
    }

    public boolean isProtectionBypass(Player player) {
        return player.isOp() || player.hasPermission("vnmine.spawnprotection.bypass");
    }

    public void applyPermissions(Player player) {
        PermissionAttachment previous = attachments.remove(player.getUniqueId());
        if (previous != null) player.removeAttachment(previous);

        PermissionAttachment attachment = player.addAttachment(plugin);
        attachments.put(player.getUniqueId(), attachment);
        String path = userPath(player.getUniqueId());
        String group = data.getString(path + ".group", defaultGroup);
        addPermissions(attachment, "groups." + group + ".permissions");
        addPermissions(attachment, path + ".permissions");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String path = userPath(player.getUniqueId());
        if (!data.contains(path)) {
            data.set(path + ".name", player.getName());
            data.set(path + ".group", defaultGroup);
            data.set(path + ".permissions", List.of());
            if (!save()) {
                data.set(path, null);
            }
        } else {
            data.set(path + ".name", player.getName());
            save();
        }
        applyPermissions(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        PermissionAttachment attachment = attachments.remove(event.getPlayer().getUniqueId());
        if (attachment != null) event.getPlayer().removeAttachment(attachment);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean groupsCommand = command.getName().equalsIgnoreCase("vngroup");
        if (!hasPermission(sender, groupsCommand ? GROUP_PERMISSION : USER_PERMISSION)) {
            sender.sendMessage("§cBạn không có quyền!");
            return true;
        }
        return groupsCommand ? handleGroupCommand(sender, args) : handleUserCommand(sender, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean groupsCommand = command.getName().equalsIgnoreCase("vngroup");
        if (!hasPermission(sender, groupsCommand ? GROUP_PERMISSION : USER_PERMISSION)) return List.of();
        if (groupsCommand) return completeGroupCommand(args);
        return completeUserCommand(args);
    }

    private boolean handleGroupCommand(CommandSender sender, String[] args) {
        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("list"))) {
            sender.sendMessage("§6Nhóm: §e" + String.join(", ", getGroupNames()));
            sender.sendMessage("§7Nhóm mặc định: §e" + defaultGroup);
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
            String group = normalizeGroup(args[1]);
            if (!hasGroup(group)) {
                sender.sendMessage("§cKhông tìm thấy nhóm §e" + args[1] + "§c.");
                return true;
            }
            List<String> permissions = groupPermissions(group);
            sender.sendMessage("§6Nhóm §e" + group + "§6 có " + permissions.size() + " quyền:");
            sender.sendMessage("§7" + (permissions.isEmpty() ? "(không có)" : String.join(", ", permissions)));
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            String group = normalizeGroup(args[1]);
            if (!GROUP_NAME_PATTERN.matcher(group).matches()) {
                sender.sendMessage("§cTên nhóm chỉ được gồm chữ thường, số, _ hoặc - (1-32 ký tự).");
                return true;
            }
            if (hasGroup(group)) {
                sender.sendMessage("§cNhóm §e" + group + " §cđã tồn tại.");
                return true;
            }
            String before = data.saveToString();
            data.set("groups." + group + ".permissions", List.of());
            if (!persistMutation(sender, before)) return true;
            sender.sendMessage("§aĐã tạo nhóm §e" + group + "§a. Nhóm mới không được cấp quyền mặc định.");
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("delete")) {
            String group = normalizeGroup(args[1]);
            if (!hasGroup(group)) {
                sender.sendMessage("§cKhông tìm thấy nhóm §e" + args[1] + "§c.");
                return true;
            }
            if (group.equals(defaultGroup) || group.equals("user") || group.equals("admin")) {
                sender.sendMessage("§cKhông thể xóa nhóm mặc định hoặc nhóm hệ thống user/admin.");
                return true;
            }
            if (hasUsersInGroup(group)) {
                sender.sendMessage("§cKhông thể xóa nhóm đang có người dùng; hãy chuyển họ sang nhóm khác trước.");
                return true;
            }
            String before = data.saveToString();
            data.set("groups." + group, null);
            if (!persistMutation(sender, before)) return true;
            sender.sendMessage("§aĐã xóa nhóm §e" + group + "§a.");
            return true;
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("permission")
                && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
            String group = normalizeGroup(args[2]);
            String permission = normalizePermission(args[3]);
            if (!hasGroup(group)) {
                sender.sendMessage("§cKhông tìm thấy nhóm §e" + args[2] + "§c.");
                return true;
            }
            if (!isValidPermission(permission)) {
                sender.sendMessage("§cNode quyền không hợp lệ; chỉ dùng chữ thường, số, dấu chấm, _ hoặc -.");
                return true;
            }
            boolean add = args[1].equalsIgnoreCase("add");
            if (add && !canGrant(sender, permission)) {
                sender.sendMessage("§cBạn không thể cấp quyền mà chính mình không có: §e" + permission);
                return true;
            }
            Set<String> permissions = new HashSet<>(groupPermissions(group));
            boolean changed = add ? permissions.add(permission) : permissions.remove(permission);
            if (!changed) {
                sender.sendMessage(add ? "§eNhóm đã có quyền này." : "§eNhóm không có quyền này.");
                return true;
            }
            String before = data.saveToString();
            data.set("groups." + group + ".permissions", permissions.stream().sorted().toList());
            if (!persistMutation(sender, before)) return true;
            refreshOnlinePlayers();
            sender.sendMessage("§aĐã " + (add ? "cấp" : "thu hồi") + " §e" + permission
                + " §a" + (add ? "cho" : "khỏi") + " nhóm §e" + group + "§a.");
            return true;
        }

        sender.sendMessage("§eDùng: /vngroup list | info <group> | create <group> | delete <group> | permission <add|remove> <group> <node>");
        return true;
    }

    private boolean handleUserCommand(CommandSender sender, String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
            UserTarget target = resolveUser(args[1]);
            if (target == null) {
                sender.sendMessage("§cKhông tìm thấy người chơi đang online hoặc đã được lưu trong permission.yml: §e" + args[1]);
                return true;
            }
            String path = userPath(target.uuid());
            String group = data.getString(path + ".group", defaultGroup);
            List<String> permissions = userPermissions(target.uuid());
            sender.sendMessage("§6Người chơi §e" + displayName(target) + "§6 | nhóm: §e" + group);
            sender.sendMessage("§6Quyền cá nhân cộng thêm: §e"
                + (permissions.isEmpty() ? "(không có)" : String.join(", ", permissions)));
            return true;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("group")) {
            UserTarget target = resolveUser(args[1]);
            String group = normalizeGroup(args[2]);
            if (target == null) {
                sender.sendMessage("§cKhông tìm thấy người chơi đang online hoặc đã được lưu: §e" + args[1]);
                return true;
            }
            if (!hasGroup(group)) {
                sender.sendMessage("§cKhông tìm thấy nhóm §e" + args[2] + "§c.");
                return true;
            }
            for (String permission : groupPermissions(group)) {
                if (!canGrant(sender, permission)) {
                    sender.sendMessage("§cBạn không có đủ quyền để gán nhóm §e" + group + "§c.");
                    return true;
                }
            }
            String path = userPath(target.uuid());
            String before = data.saveToString();
            data.set(path + ".name", target.name());
            data.set(path + ".group", group);
            data.set(path + ".permissions", userPermissions(target.uuid()));
            if (!persistMutation(sender, before)) return true;
            refreshOnlinePlayer(target.uuid());
            sender.sendMessage("§aĐã gán người chơi §e" + displayName(target) + " §avào nhóm §e" + group + "§a.");
            return true;
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("permission")
                && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
            UserTarget target = resolveUser(args[2]);
            String permission = normalizePermission(args[3]);
            if (target == null) {
                sender.sendMessage("§cKhông tìm thấy người chơi đang online hoặc đã được lưu: §e" + args[2]);
                return true;
            }
            if (!isValidPermission(permission)) {
                sender.sendMessage("§cNode quyền không hợp lệ; chỉ dùng chữ thường, số, dấu chấm, _ hoặc -.");
                return true;
            }
            boolean add = args[1].equalsIgnoreCase("add");
            if (add && !canGrant(sender, permission)) {
                sender.sendMessage("§cBạn không thể cấp quyền mà chính mình không có: §e" + permission);
                return true;
            }
            Set<String> permissions = new HashSet<>(userPermissions(target.uuid()));
            boolean changed = add ? permissions.add(permission) : permissions.remove(permission);
            if (!changed) {
                sender.sendMessage(add ? "§eNgười chơi đã có quyền cá nhân này." : "§eNgười chơi không có quyền cá nhân này.");
                return true;
            }
            String path = userPath(target.uuid());
            String before = data.saveToString();
            data.set(path + ".name", target.name());
            data.set(path + ".permissions", permissions.stream().sorted().toList());
            if (!persistMutation(sender, before)) return true;
            refreshOnlinePlayer(target.uuid());
            sender.sendMessage("§aĐã " + (add ? "cấp" : "thu hồi") + " quyền cá nhân §e" + permission
                + " §a" + (add ? "cho" : "khỏi") + " §e" + displayName(target) + "§a.");
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            UserTarget target = resolveUser(args[1]);
            if (target == null) {
                sender.sendMessage("§cKhông tìm thấy người chơi đang online hoặc đã được lưu: §e" + args[1]);
                return true;
            }
            String path = userPath(target.uuid());
            String before = data.saveToString();
            data.set(path + ".group", defaultGroup);
            data.set(path + ".permissions", List.of());
            if (!persistMutation(sender, before)) return true;
            refreshOnlinePlayer(target.uuid());
            sender.sendMessage("§aĐã đặt lại quyền của §e" + displayName(target)
                + " §avề nhóm mặc định §e" + defaultGroup + "§a.");
            return true;
        }
        sender.sendMessage("§eDùng: /vnuser info <player> | group <player> <group> | permission <add|remove> <player> <node> | reset <player>");
        return true;
    }

    private List<String> completeGroupCommand(String[] args) {
        if (args.length == 1) return matching(args[0], "list", "info", "create", "delete", "permission");
        if (args.length == 2 && args[0].equalsIgnoreCase("info")) return matching(args[1], getGroupNames().toArray(String[]::new));
        if (args.length == 2 && args[0].equalsIgnoreCase("permission")) return matching(args[1], "add", "remove");
        if (args.length == 3 && args[0].equalsIgnoreCase("permission")) return matching(args[2], getGroupNames().toArray(String[]::new));
        if (args.length == 4 && args[0].equalsIgnoreCase("permission")) return matchingPermissions(args[3]);
        return List.of();
    }

    private List<String> completeUserCommand(String[] args) {
        if (args.length == 1) return matching(args[0], "info", "group", "permission", "reset");
        if (args.length == 2 && args[0].equalsIgnoreCase("group")) return matching(args[1], knownUserNames());
        if (args.length == 3 && args[0].equalsIgnoreCase("group")) return matching(args[2], getGroupNames().toArray(String[]::new));
        if (args.length == 2 && args[0].equalsIgnoreCase("info")) return matching(args[1], knownUserNames());
        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) return matching(args[1], knownUserNames());
        if (args.length == 2 && args[0].equalsIgnoreCase("permission")) return matching(args[1], "add", "remove");
        if (args.length == 3 && args[0].equalsIgnoreCase("permission")) return matching(args[2], knownUserNames());
        if (args.length == 4 && args[0].equalsIgnoreCase("permission")) return matchingPermissions(args[3]);
        return List.of();
    }

    private List<String> matchingPermissions(String partial) {
        String normalized = partial.toLowerCase(Locale.ROOT);
        return Bukkit.getPluginManager().getPermissions().stream()
            .map(Permission::getName)
            .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(normalized))
            .sorted()
            .toList();
    }

    private List<String> matching(String partial, String... options) {
        String normalized = partial.toLowerCase(Locale.ROOT);
        return java.util.Arrays.stream(options)
            .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(normalized))
            .sorted()
            .toList();
    }

    private List<String> matching(String partial, List<String> options) {
        String normalized = partial.toLowerCase(Locale.ROOT);
        return options.stream()
            .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(normalized))
            .sorted()
            .toList();
    }

    private List<String> knownUserNames() {
        Set<String> names = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) names.add(player.getName());
        ConfigurationSection users = data.getConfigurationSection("users");
        if (users != null) {
            for (String uuid : users.getKeys(false)) {
                String name = data.getString("users." + uuid + ".name");
                if (name != null && !name.isBlank()) names.add(name);
            }
        }
        return names.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private UserTarget resolveUser(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return new UserTarget(online.getUniqueId(), online.getName());
        ConfigurationSection users = data.getConfigurationSection("users");
        if (users == null) return null;
        for (String rawUuid : users.getKeys(false)) {
            String storedName = data.getString("users." + rawUuid + ".name");
            if (storedName != null && storedName.equalsIgnoreCase(name)) {
                try {
                    return new UserTarget(UUID.fromString(rawUuid), storedName);
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().warning("[Permissions] Ignoring invalid stored UUID '" + rawUuid + "'.");
                }
            }
        }
        return null;
    }

    private String displayName(UserTarget target) {
        return target.name();
    }

    private record UserTarget(UUID uuid, String name) {}

    private void addPermissions(PermissionAttachment attachment, String path) {
        for (String permission : data.getStringList(path)) {
            if (isValidPermission(permission)) attachment.setPermission(permission, true);
        }
    }

    private boolean hasGroup(String group) {
        return data.isConfigurationSection("groups." + group);
    }

    public List<String> getGroupNames() {
        ConfigurationSection groups = data.getConfigurationSection("groups");
        if (groups == null) return List.of();
        return groups.getKeys(false).stream().sorted().toList();
    }

    private List<String> groupPermissions(String group) {
        return data.getStringList("groups." + group + ".permissions");
    }

    private List<String> userPermissions(UUID uuid) {
        return data.getStringList(userPath(uuid) + ".permissions");
    }

    private boolean hasUsersInGroup(String group) {
        ConfigurationSection users = data.getConfigurationSection("users");
        if (users == null) return false;
        return users.getKeys(false).stream()
            .anyMatch(key -> group.equalsIgnoreCase(data.getString("users." + key + ".group", defaultGroup)));
    }

    private String userPath(UUID uuid) {
        return "users." + uuid;
    }

    private String normalizeGroup(String group) {
        return group.toLowerCase(Locale.ROOT);
    }

    private String normalizePermission(String permission) {
        return permission.toLowerCase(Locale.ROOT);
    }

    private boolean isValidPermission(String permission) {
        return permission != null && PERMISSION_PATTERN.matcher(permission).matches();
    }

    private boolean canGrant(CommandSender sender, String permission) {
        return sender.isOp() || sender.hasPermission(permission);
    }

    private void refreshOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) applyPermissions(player);
    }

    private void refreshOnlinePlayer(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) applyPermissions(player);
    }

    private boolean saveFailed(CommandSender sender) {
        sender.sendMessage("§cKhông thể lưu permission.yml; thay đổi đã bị hủy.");
        return true;
    }

    private boolean persistMutation(CommandSender sender, String before) {
        if (save()) return true;
        try {
            data.loadFromString(before);
        } catch (InvalidConfigurationException exception) {
            plugin.getLogger().log(Level.SEVERE,
                "[Permissions] Could not restore in-memory permission configuration after a failed save.", exception);
        }
        saveFailed(sender);
        return false;
    }

    private void load() {
        try {
            loadConfiguration();
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalStateException("Could not load permission.yml", exception);
        }
    }

    private void loadConfiguration() throws IOException, InvalidConfigurationException {
        if (!dataFile.isFile()) {
            if (!plugin.getDataFolder().isDirectory() && !plugin.getDataFolder().mkdirs()) {
                throw new IOException("Could not create plugin data directory.");
            }
            plugin.saveResource("permission.yml", false);
        }
        YamlConfiguration loaded = new YamlConfiguration();
        loaded.options().parseComments(true);
        loaded.load(dataFile);
        if (loaded.getConfigurationSection("groups") == null) {
            throw new InvalidConfigurationException("Missing 'groups' section.");
        }
        if (!loaded.isConfigurationSection("groups.user")
                || !loaded.isConfigurationSection("groups.admin")) {
            throw new InvalidConfigurationException("The built-in user and admin groups must exist.");
        }
        List<String> adminPermissions = new ArrayList<>(loaded.getStringList("groups.admin.permissions"));
        boolean adminPermissionsChanged = false;
        if (!adminPermissions.contains("vnmine.command.army.admin")) {
            adminPermissions.add("vnmine.command.army.admin");
            adminPermissionsChanged = true;
        }
        if (!adminPermissions.contains("vnmine.command.schemabuild")) {
            adminPermissions.add("vnmine.command.schemabuild");
            adminPermissionsChanged = true;
        }
        if (adminPermissionsChanged) {
            loaded.set("groups.admin.permissions", adminPermissions);
            try {
                loaded.save(dataFile);
            } catch (IOException exception) {
                plugin.getLogger().log(Level.WARNING,
                    "[Permissions] Added built-in admin permissions in memory but could not persist permission.yml.", exception);
            }
        }
        if (loaded.getConfigurationSection("users") == null) loaded.createSection("users");
        String configuredDefault = loaded.getString("default-group", "user");
        if (configuredDefault == null || !loaded.isConfigurationSection("groups." + configuredDefault)) {
            throw new InvalidConfigurationException("default-group must reference an existing group.");
        }
        this.data = loaded;
        this.defaultGroup = configuredDefault;
        plugin.getLogger().info("[Permissions] Loaded " + getGroupNames().size()
            + " group(s), default group '" + defaultGroup + "'.");
    }

    private boolean save() {
        File temporary = new File(dataFile.getParentFile(), dataFile.getName() + ".tmp");
        try {
            if (!dataFile.getParentFile().isDirectory() && !dataFile.getParentFile().mkdirs()) {
                throw new IOException("Could not create plugin data directory.");
            }
            data.save(temporary);
            try {
                Files.move(temporary.toPath(), dataFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "[Permissions] Could not save permission.yml.", exception);
            try {
                Files.deleteIfExists(temporary.toPath());
            } catch (IOException cleanupException) {
                plugin.getLogger().log(Level.WARNING,
                    "[Permissions] Could not remove temporary permission file.", cleanupException);
            }
            return false;
        }
    }
}
