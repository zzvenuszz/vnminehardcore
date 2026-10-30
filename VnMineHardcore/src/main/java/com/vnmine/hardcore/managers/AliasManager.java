package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import io.papermc.paper.chat.ChatRenderer;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import io.papermc.paper.event.player.AsyncChatEvent;

/** Player display aliases with per-code formatting permissions. */
public final class AliasManager implements Listener, CommandExecutor, TabCompleter {
    private static final String SET_PERMISSION = "vnmine.alias.set";
    private static final String ADMIN_PERMISSION = "vnmine.alias.admin";
    private static final Pattern CODE = Pattern.compile("&([0-9a-fk-or])", Pattern.CASE_INSENSITIVE);
    private final VnMineHardcore plugin;
    private final File file;
    private final Map<UUID, Alias> aliases = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> formatPermissions = new java.util.concurrent.ConcurrentHashMap<>();

    public AliasManager(VnMineHardcore plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "aliases.yml");
        load();
        for (Player player : Bukkit.getOnlinePlayers()) apply(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String plainMessage = PlainTextComponentSerializer.plainText().serialize(event.message());
        String formattedMessage = renderText(player, plainMessage, false);
        if (!formattedMessage.equals(plainMessage)) {
            event.message(LegacyComponentSerializer.legacySection().deserialize(formattedMessage));
        }
        Alias alias = aliases.get(event.getPlayer().getUniqueId());
        if (alias == null) return;
        String formatted = renderAlias(player, alias);
        Component name = LegacyComponentSerializer.legacySection().deserialize(formatted);
        event.renderer(ChatRenderer.viewerUnaware((source, displayName, message) ->
            name.append(Component.text(": ")).append(message)));
    }

    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        apply(event.getPlayer());
    }

    public String displayName(Player player) {
        Alias alias = aliases.get(player.getUniqueId());
        return alias == null ? player.getName() : renderAlias(player, alias);
    }

    private String renderAlias(Player player, Alias alias) {
        return renderText(player, alias.value(), alias.adminSet());
    }

    private String renderText(Player player, String input, boolean bypassFormattingPermissions) {
        if (input == null || input.isEmpty()) return input == null ? "" : input;
        if (bypassFormattingPermissions) return LegacyText.translate(input.replace('§', '&'));
        String value = input.replace('§', '&');
        Set<String> allowed = formatPermissions.getOrDefault(player.getUniqueId(), Set.of());
        StringBuilder safe = new StringBuilder(value.length());
        for (int i = 0; i < value.length();) {
            int rainbowLength = rainbowTokenLength(value, i);
            if (rainbowLength > 0) {
                if (allowed.contains("vnmine.alias.rainbow")) safe.append(value, i, i + rainbowLength);
                i += rainbowLength;
                continue;
            }
            if (value.charAt(i) == '&' && i + 1 < value.length()) {
                char code = Character.toLowerCase(value.charAt(i + 1));
                String permission = formatPermission(code);
                if (permission != null) {
                    if (allowed.contains(permission)) safe.append('&').append(code);
                    i += 2;
                    continue;
                }
            }
            safe.append(value.charAt(i++));
        }
        return LegacyText.translate(safe.toString());
    }

    private String formatPermission(char code) {
        if ("0123456789abcdef".indexOf(code) >= 0) return "vnmine.alias.color." + code;
        if ("klmnor".indexOf(code) >= 0) return "vnmine.alias.format." + code;
        return null;
    }

    private void apply(Player player) {
        refreshPermissionCache(player);
        Alias alias = aliases.get(player.getUniqueId());
        String display = alias == null ? player.getName() : renderAlias(player, alias);
        player.setDisplayName(display);
        player.setPlayerListName(display);
    }

    public void refreshPermissionCache(Player player) {
        if (player == null) return;
        Set<String> allowed = new java.util.HashSet<>();
        for (char code : "0123456789abcdef".toCharArray()) {
            String permission = formatPermission(code);
            if (player.isOp() || player.hasPermission(permission)) allowed.add(permission);
        }
        for (char code : "klmnor".toCharArray()) {
            String permission = formatPermission(code);
            if (player.isOp() || player.hasPermission(permission)) allowed.add(permission);
        }
        if (player.isOp() || player.hasPermission("vnmine.alias.rainbow"))
            allowed.add("vnmine.alias.rainbow");
        formatPermissions.put(player.getUniqueId(), Set.copyOf(allowed));
    }

    private static int rainbowTokenLength(String value, int offset) {
        if (offset + 2 >= value.length() || value.charAt(offset) != '&'
                || Character.toLowerCase(value.charAt(offset + 1)) != 'r') return 0;
        char mode = Character.toLowerCase(value.charAt(offset + 2));
        if (mode == 'l') return offset + 3 < value.length() && value.charAt(offset + 3) == ':' ? 4 : 0;
        return mode == '1' || mode == '2' || mode == '3' ? 3 : 0;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Lệnh này chỉ dùng trong game.");
            return true;
        }
        if (args.length == 0) {
            if (!player.hasPermission(SET_PERMISSION)) {
                player.sendMessage("§cBạn cần quyền vnmine.alias.set để quản lý alias.");
                return true;
            }
            clear(player, player.getUniqueId(), player.getName());
            return true;
        }
        if (args[0].equalsIgnoreCase("set") && args.length >= 2) {
            if (!player.hasPermission(SET_PERMISSION)) {
                player.sendMessage("§cBạn cần quyền vnmine.alias.set để đặt alias.");
                return true;
            }
            set(player, player.getUniqueId(), player.getName(), String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)), false);
            return true;
        }
        if (args[0].equalsIgnoreCase("clear") && args.length == 1) {
            if (!player.hasPermission(SET_PERMISSION)) {
                player.sendMessage("§cBạn cần quyền vnmine.alias.set để xóa alias.");
                return true;
            }
            clear(player, player.getUniqueId(), player.getName());
            return true;
        }
        if (args[0].equalsIgnoreCase("set") && args.length >= 3) {
            if (!player.hasPermission(ADMIN_PERMISSION)) return noAdmin(player);
            OfflinePlayer target = findTarget(args[1]);
            if (target == null) { player.sendMessage("§cKhông tìm thấy người chơi đã từng vào server đó."); return true; }
            set(player, target.getUniqueId(), target.getName() == null ? args[1] : target.getName(),
                String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)), true);
            return true;
        }
        if (args[0].equalsIgnoreCase("clear") && args.length == 2) {
            if (!player.hasPermission(ADMIN_PERMISSION)) return noAdmin(player);
            OfflinePlayer target = findTarget(args[1]);
            if (target == null) { player.sendMessage("§cKhông tìm thấy người chơi đã từng vào server đó."); return true; }
            clear(player, target.getUniqueId(), target.getName() == null ? args[1] : target.getName());
            return true;
        }
        player.sendMessage("§eDùng: /alias §7(xóa alias), §e/alias set <tên> §7hoặc §e/alias clear");
        if (player.hasPermission(ADMIN_PERMISSION))
            player.sendMessage("§eAdmin: /alias set <người_chơi> <tên> §7| §e/alias clear <người_chơi>");
        return true;
    }

    private boolean noAdmin(Player player) {
        player.sendMessage("§cBạn cần quyền vnmine.alias.admin.");
        return true;
    }

    private OfflinePlayer findTarget(String name) {
        Player online = Bukkit.getPlayerExact(name);
        OfflinePlayer target = online == null ? Bukkit.getOfflinePlayerIfCached(name) : online;
        if (target == null || (!target.isOnline() && !target.hasPlayedBefore())) return null;
        return target;
    }

    private void set(Player actor, UUID targetId, String targetName, String raw, boolean adminSet) {
        String value = raw.replace('§', '&').trim();
        String plain = LegacyText.strip(value).replaceAll("(?i)&r[l123]", "").trim();
        if (value.isBlank() || plain.isBlank() || plain.codePointCount(0, plain.length()) > 32
                || value.length() > 96 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            actor.sendMessage("§cAlias phải có 1–32 ký tự hiển thị; mã màu không tính vào giới hạn.");
            return;
        }
        if (!adminSet && !validatePermissions(actor, value)) return;
        Alias previous = aliases.put(targetId, new Alias(value, adminSet, targetName));
        if (!save()) {
            if (previous == null) aliases.remove(targetId); else aliases.put(targetId, previous);
            actor.sendMessage("§cKhông lưu được aliases.yml; alias cũ vẫn được giữ.");
            return;
        }
        Player target = Bukkit.getPlayer(targetId);
        if (target != null) apply(target);
        if (plugin.getBossLeaderboardManager() != null)
            plugin.getBossLeaderboardManager().updateAliasName(targetId,
                target == null ? LegacyText.translate(value) : displayName(target));
        actor.sendMessage("§aĐã đặt alias cho §f" + targetName + "§a: " + LegacyText.translate(value));
        plugin.getLogger().info("[Alias] actor=" + actor.getName() + ", target=" + targetName
            + ", adminSet=" + adminSet + ", value=" + ChatColor.stripColor(LegacyText.translate(value)));
    }

    private boolean validatePermissions(Player player, String alias) {
        Matcher matcher = CODE.matcher(alias);
        while (matcher.find()) {
            if (matcher.start() + 2 < alias.length() && Character.toLowerCase(matcher.group(1).charAt(0)) == 'r'
                    && "l123".indexOf(Character.toLowerCase(alias.charAt(matcher.start() + 2))) >= 0) continue;
            char code = Character.toLowerCase(matcher.group(1).charAt(0));
            String permission = formatPermission(code);
            if (permission != null && !player.hasPermission(permission)) {
                player.sendMessage("§cBạn thiếu quyền định dạng §f&" + code + "§c: §f" + permission);
                return false;
            }
        }
        for (String rainbow : List.of("&rl", "&r1", "&r2", "&r3")) {
            if (alias.toLowerCase(Locale.ROOT).contains(rainbow)
                    && !player.hasPermission("vnmine.alias.rainbow")) {
                player.sendMessage("§cBạn thiếu quyền hiệu ứng rainbow: §fvnmine.alias.rainbow");
                return false;
            }
        }
        return true;
    }

    private void clear(Player actor, UUID targetId, String targetName) {
        Alias previous = aliases.remove(targetId);
        if (previous == null) {
            actor.sendMessage("§e" + targetName + " chưa có alias.");
            return;
        }
        if (!save()) {
            aliases.put(targetId, previous);
            actor.sendMessage("§cKhông lưu được aliases.yml; alias cũ vẫn được giữ.");
            return;
        }
        Player target = Bukkit.getPlayer(targetId);
        if (target != null) apply(target);
        if (plugin.getBossLeaderboardManager() != null)
            plugin.getBossLeaderboardManager().updateAliasName(targetId,
                target == null ? targetName : target.getName());
        actor.sendMessage("§aĐã xóa alias của §f" + targetName + "§a.");
        plugin.getLogger().info("[Alias] actor=" + actor.getName() + ", target=" + targetName + ", action=clear");
    }

    private void load() {
        if (!file.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var section = yaml.getConfigurationSection("aliases");
        if (section == null) return;
        for (String rawId : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(rawId);
                String value = yaml.getString("aliases." + rawId + ".value", "");
                if (!value.isBlank() && value.length() <= 96) aliases.put(id, new Alias(value,
                    yaml.getBoolean("aliases." + rawId + ".admin-set", false),
                    yaml.getString("aliases." + rawId + ".last-name", "player")));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("[Alias] Ignoring invalid player UUID '" + rawId + "'.");
            }
        }
        plugin.getLogger().info("[Alias] Loaded " + aliases.size() + " player aliases.");
    }

    private boolean save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (var entry : aliases.entrySet()) {
            String path = "aliases." + entry.getKey();
            yaml.set(path + ".value", entry.getValue().value());
            yaml.set(path + ".admin-set", entry.getValue().adminSet());
            yaml.set(path + ".last-name", entry.getValue().lastName());
        }
        File temp = null;
        try {
            temp = Files.createTempFile(file.getParentFile().toPath(), "aliases-", ".yml.tmp").toFile();
            Files.writeString(temp.toPath(), yaml.saveToString(), java.nio.charset.StandardCharsets.UTF_8);
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "[Alias] Could not save aliases.yml.", exception);
            if (temp != null) temp.delete();
            return false;
        }
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("set", "clear").stream()
            .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && sender.hasPermission(ADMIN_PERMISSION)
                && (args[0].equalsIgnoreCase("clear") || args[0].equalsIgnoreCase("set"))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }

    private record Alias(String value, boolean adminSet, String lastName) { }
}
