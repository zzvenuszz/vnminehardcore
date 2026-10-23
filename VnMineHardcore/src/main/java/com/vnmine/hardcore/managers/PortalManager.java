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
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.scheduler.BukkitTask;

public final class PortalManager implements Listener, CommandExecutor, TabCompleter {
    private static final String ADMIN_PERMISSION = "vnmine.command.portal.admin";
    private static final long MOVE_COOLDOWN_MILLIS = 1_000L;

    private final VnMineHardcore plugin;
    private final File storageFile;
    private final NamespacedKey selectionWandKey;
    private final Map<String, PortalDefinition> portals = new LinkedHashMap<>();
    private final Map<UUID, Selection> selections = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastTravelAt = new ConcurrentHashMap<>();
    private final Map<UUID, String> arrivalPortalSuppression = new ConcurrentHashMap<>();
    private final Set<String> invalidSettingWarnings = ConcurrentHashMap.newKeySet();
    private final BukkitTask particleTask;

    public PortalManager(VnMineHardcore plugin) throws IOException, InvalidConfigurationException {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "portals.yml");
        this.selectionWandKey = new NamespacedKey(plugin, "portal_selection_wand");
        loadPortals();
        particleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::showPortalParticles, 20L, 20L);
    }

    public void stop() {
        particleTask.cancel();
        selections.clear();
        lastTravelAt.clear();
        arrivalPortalSuppression.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSelectionWandUse(PlayerInteractEvent event) {
        if (event.getItem() == null || !isSelectionWand(event.getItem())) return;
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        event.setCancelled(true);
        Selection previous = selections.get(event.getPlayer().getUniqueId());
        boolean firstPosition = event.getAction() == Action.LEFT_CLICK_BLOCK;
        Selection updated = previous == null
            ? (firstPosition
                ? new Selection(event.getClickedBlock().getLocation(), null)
                : new Selection(null, event.getClickedBlock().getLocation()))
            : previous.withPosition(firstPosition, event.getClickedBlock().getLocation());
        selections.put(event.getPlayer().getUniqueId(), updated);
        event.getPlayer().sendMessage("§aĐã chọn pos" + (firstPosition ? "1" : "2") + ": §f"
            + formatBlock(event.getClickedBlock().getLocation()));
        if (updated.complete()) {
            event.getPlayer().sendMessage("§7Vùng chọn có thể dùng với §e/vnportal create <id> <tên>§7.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortalEnter(PlayerMoveEvent event) {
        if (event.getTo() == null || !plugin.getConfig().getBoolean("rpg.portals.enabled", true)) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getWorld() == to.getWorld()
            && from.getBlockX() == to.getBlockX()
            && from.getBlockY() == to.getBlockY()
            && from.getBlockZ() == to.getBlockZ()) return;

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String suppressedPortalId = arrivalPortalSuppression.get(playerId);
        if (suppressedPortalId != null) {
            PortalDefinition arrivalPortal = portals.get(suppressedPortalId);
            if (arrivalPortal != null && arrivalPortal.contains(to.getWorld().getName(),
                    to.getBlockX(), to.getBlockY(), to.getBlockZ())) return;
            arrivalPortalSuppression.remove(playerId);
        }
        long now = System.currentTimeMillis();
        long cooldownMillis = portalTravelCooldownMillis();
        long previousTravel = lastTravelAt.getOrDefault(playerId, 0L);
        if (now - previousTravel < Math.max(MOVE_COOLDOWN_MILLIS, cooldownMillis)) return;

        for (PortalDefinition portal : portals.values()) {
            if (!portal.enabled() || !portal.contains(to.getWorld().getName(),
                    to.getBlockX(), to.getBlockY(), to.getBlockZ())) continue;
            lastTravelAt.put(playerId, now);
            travel(player, portal);
            return;
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        selections.remove(playerId);
        lastTravelAt.remove(playerId);
        arrivalPortalSuppression.remove(playerId);
    }

    private void travel(Player player, PortalDefinition source) {
        if (player.isDead() || plugin.getDeathListener().isPortalTravelBlocked(player)) {
            player.sendMessage("§cKhông thể đi qua portal khi đang chết hoặc chờ hồi sinh.");
            return;
        }
        if (plugin.getConfig().getBoolean("rpg.portal-travel.block-in-combat", true)
            && plugin.getDeathListener().isCombatTagged(player)) {
            player.sendMessage("§cKhông thể đi qua portal khi đang giao chiến.");
            return;
        }

        String targetId = source.linkedPortalId();
        PortalDefinition target = targetId == null ? null : portals.get(targetId);
        if (target == null || !target.enabled() || target.destination() == null) {
            player.sendMessage("§cPortal này chưa được nối đến một điểm đến hợp lệ.");
            return;
        }
        Location destination = resolveDestination(target.destination());
        if (destination == null || !isSafeDestination(destination)) {
            player.sendMessage("§cĐiểm đến của portal chưa sẵn sàng hoặc không an toàn.");
            return;
        }

        Location sourceLocation = player.getLocation();
        if (player.teleport(destination)) {
            if (target.contains(destination.getWorld().getName(), destination.getBlockX(),
                    destination.getBlockY(), destination.getBlockZ())) {
                arrivalPortalSuppression.put(player.getUniqueId(), target.id());
            }
            sourceLocation.getWorld().spawnParticle(Particle.PORTAL, sourceLocation, 24, 0.4, 0.6, 0.4, 0.05);
            destination.getWorld().spawnParticle(Particle.PORTAL, destination, 24, 0.4, 0.6, 0.4, 0.05);
            player.sendMessage("§aBạn đã dịch chuyển từ §f" + source.name() + " §ađến §f"
                + target.name() + "§a.");
        } else {
            player.sendMessage("§cDịch chuyển thất bại; hãy thử lại.");
        }
    }

    private Location resolveDestination(PortalDefinition.Destination destination) {
        World world = Bukkit.getWorld(destination.worldName());
        if (world == null) return null;
        return new Location(world, destination.x(), destination.y(), destination.z(),
            destination.yaw(), destination.pitch());
    }

    private boolean isSafeDestination(Location destination) {
        World world = destination.getWorld();
        if (world == null || destination.getBlockY() <= world.getMinHeight()
            || destination.getBlockY() + 1 >= world.getMaxHeight()
            || !world.getWorldBorder().isInside(destination)) return false;
        int chunkX = destination.getBlockX() >> 4;
        int chunkZ = destination.getBlockZ() >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) return false;
        return destination.getBlock().isPassable()
            && world.getBlockAt(destination.getBlockX(), destination.getBlockY() + 1,
                destination.getBlockZ()).isPassable()
            && world.getBlockAt(destination.getBlockX(), destination.getBlockY() - 1,
                destination.getBlockZ()).getType().isSolid();
    }

    private void showPortalParticles() {
        if (!plugin.getConfig().getBoolean("rpg.portals.enabled", true)
            || !plugin.getConfig().getBoolean("rpg.portals.particle-ring.enabled", true)) return;
        for (PortalDefinition portal : portals.values()) {
            if (!portal.enabled()) continue;
            World world = Bukkit.getWorld(portal.worldName());
            if (world == null) continue;
            int centerX = (int) (portal.minX() + ((long) portal.maxX() - portal.minX()) / 2);
            int centerZ = (int) (portal.minZ() + ((long) portal.maxZ() - portal.minZ()) / 2);
            double centerY = portal.minY() + ((double) portal.maxY() - portal.minY()) / 2.0 + 0.5;
            if (!world.isChunkLoaded(centerX >> 4, centerZ >> 4)) continue;
            for (int point = 0; point < 8; point++) {
                double angle = Math.PI * 2 * point / 8;
                world.spawnParticle(Particle.PORTAL, centerX + 0.5 + Math.cos(angle),
                    centerY, centerZ + 0.5 + Math.sin(angle), 2, 0, 0, 0, 0);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        plugin.getLogger().info("[Portal] Command from " + sender.getName() + ": /"
            + command.getName() + (args.length == 0 ? "" : " " + String.join(" ", args)));
        if (!plugin.getPermissionManager().hasPermission(sender, ADMIN_PERMISSION)) {
            plugin.getLogger().warning("[Portal] Command denied for " + sender.getName()
                + ": missing permission " + ADMIN_PERMISSION + ".");
            sender.sendMessage("§cBạn không có quyền quản lý portal.");
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "wand" -> giveSelectionWand(sender);
            case "pos1", "pos2" -> selectTargetBlock(sender, subcommand.equals("pos1"));
            case "create" -> createPortal(sender, args);
            case "setspawn" -> setSpawn(sender, args);
            case "link" -> link(sender, args);
            case "unlink" -> unlink(sender, args);
            case "enable", "disable" -> setEnabled(sender, args, subcommand.equals("enable"));
            case "remove" -> remove(sender, args);
            case "info" -> info(sender, args);
            case "list" -> list(sender);
            case "validate" -> validate(sender);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void giveSelectionWand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cLệnh này chỉ dùng trong game.");
            return;
        }
        ItemStack wand = new ItemStack(Material.WOODEN_AXE);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName("§6Portal Selection Wand");
        meta.setLore(List.of("§7Chuột trái: chọn pos1", "§7Chuột phải: chọn pos2"));
        meta.getPersistentDataContainer().set(selectionWandKey, PersistentDataType.BYTE, (byte) 1);
        wand.setItemMeta(meta);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(wand);
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.sendMessage("§aĐã cấp rìu chọn vùng portal.");
    }

    private boolean isSelectionWand(ItemStack item) {
        if (item.getType() != Material.WOODEN_AXE || !item.hasItemMeta()) return false;
        Byte marker = item.getItemMeta().getPersistentDataContainer()
            .get(selectionWandKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private void selectTargetBlock(CommandSender sender, boolean firstPosition) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cLệnh này chỉ dùng trong game.");
            return;
        }
        org.bukkit.block.Block target = player.getTargetBlockExact(128);
        if (target == null) {
            player.sendMessage("§cHãy nhìn vào một block trong phạm vi 128 block.");
            return;
        }
        Selection previous = selections.get(player.getUniqueId());
        Selection updated = previous == null
            ? (firstPosition
                ? new Selection(target.getLocation(), null)
                : new Selection(null, target.getLocation()))
            : previous.withPosition(firstPosition, target.getLocation());
        selections.put(player.getUniqueId(), updated);
        player.sendMessage("§aĐã chọn pos" + (firstPosition ? "1" : "2") + ": §f"
            + formatBlock(target.getLocation()));
    }

    private void createPortal(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§eSử dụng: /vnportal create <id> <tên portal>");
            return;
        }
        String id = normalizeId(args[1]);
        if (!PortalDefinition.isValidId(id)) {
            sender.sendMessage("§cID phải gồm 2-32 ký tự a-z, 0-9, _ hoặc -.");
            return;
        }
        String name = String.join(" ", List.of(args).subList(2, args.length)).trim();
        if (name.isEmpty() || name.length() > 64) {
            sender.sendMessage("§cTên portal phải dài 1-64 ký tự.");
            return;
        }
        if (portals.containsKey(id)) {
            sender.sendMessage("§cPortal ID này đã tồn tại.");
            return;
        }
        int maxPortals = configuredInteger("rpg.portals.max-portals", 100, 1, 500);
        if (portals.size() >= maxPortals) {
            sender.sendMessage("§cĐã đạt giới hạn " + maxPortals + " portal.");
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cLệnh này chỉ dùng trong game.");
            return;
        }
        Selection selection = selections.get(player.getUniqueId());
        if (selection == null || !selection.complete()) {
            sender.sendMessage("§cHãy chọn cả pos1 và pos2 bằng /vnportal wand trước.");
            return;
        }
        if (selection.pos1().getWorld() != selection.pos2().getWorld()
            || selection.pos1().getWorld() != player.getWorld()) {
            sender.sendMessage("§cHai điểm chọn phải cùng world hiện tại.");
            return;
        }

        PortalDefinition definition = createDefinition(id, name, selection);
        long maximumVolume = configuredInteger("rpg.portals.max-selection-blocks", 20_000, 1, 250_000);
        if (definition.volume() > maximumVolume) {
            sender.sendMessage("§cVùng chọn quá lớn (" + definition.volume() + " block; tối đa "
                + maximumVolume + ").");
            return;
        }
        Map<String, PortalDefinition> updated = new LinkedHashMap<>(portals);
        updated.put(id, definition);
        if (!savePortals(updated)) {
            sender.sendMessage("§cKhông thể lưu portal; dữ liệu hiện tại chưa được thay đổi.");
            return;
        }
        portals.clear();
        portals.putAll(updated);
        sender.sendMessage("§aĐã tạo portal §f" + definition.name() + " §7(ID: " + id
            + ", vùng: " + definition.volume() + " block). Dùng /vnportal setspawn " + id + ".");
    }

    private PortalDefinition createDefinition(String id, String name, Selection selection) {
        Location first = selection.pos1();
        Location second = selection.pos2();
        return new PortalDefinition(id, name, first.getWorld().getName(),
            Math.min(first.getBlockX(), second.getBlockX()),
            Math.min(first.getBlockY(), second.getBlockY()),
            Math.min(first.getBlockZ(), second.getBlockZ()),
            Math.max(first.getBlockX(), second.getBlockX()),
            Math.max(first.getBlockY(), second.getBlockY()),
            Math.max(first.getBlockZ(), second.getBlockZ()),
            null, null, true);
    }

    private void setSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || args.length != 2) {
            sender.sendMessage("§eSử dụng trong game: /vnportal setspawn <id>");
            return;
        }
        PortalDefinition portal = portals.get(normalizeId(args[1]));
        if (portal == null) {
            sender.sendMessage("§cKhông tìm thấy portal đó.");
            return;
        }
        Location location = player.getLocation();
        PortalDefinition updatedPortal = portal.withDestination(new PortalDefinition.Destination(
            location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
            location.getYaw(), location.getPitch()));
        updatePortal(sender, updatedPortal, "§aĐã đặt điểm đến cho portal §f" + portal.name() + "§a.");
    }

    private void link(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage("§eSử dụng: /vnportal link <portalA> <portalB>");
            return;
        }
        String firstId = normalizeId(args[1]);
        String secondId = normalizeId(args[2]);
        PortalDefinition first = portals.get(firstId);
        PortalDefinition second = portals.get(secondId);
        if (first == null || second == null || firstId.equals(secondId)) {
            sender.sendMessage("§cHai portal ID phải tồn tại và không được trùng nhau.");
            return;
        }
        if (hasOtherLink(first, secondId) || hasOtherLink(second, firstId)) {
            sender.sendMessage("§cHãy unlink liên kết hiện tại trước khi nối portal này.");
            return;
        }
        Map<String, PortalDefinition> updated = new LinkedHashMap<>(portals);
        updated.put(firstId, first.withLink(secondId));
        updated.put(secondId, second.withLink(firstId));
        if (!savePortals(updated)) {
            sender.sendMessage("§cKhông thể lưu liên kết portal.");
            return;
        }
        portals.clear();
        portals.putAll(updated);
        sender.sendMessage("§aĐã nối hai chiều §f" + first.name() + " §7↔ §f" + second.name() + "§a.");
    }

    private boolean hasOtherLink(PortalDefinition portal, String allowedLink) {
        return portal.linkedPortalId() != null && !portal.linkedPortalId().equals(allowedLink);
    }

    private void unlink(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("§eSử dụng: /vnportal unlink <id>");
            return;
        }
        String id = normalizeId(args[1]);
        PortalDefinition portal = portals.get(id);
        if (portal == null) {
            sender.sendMessage("§cKhông tìm thấy portal đó.");
            return;
        }
        Map<String, PortalDefinition> updated = new LinkedHashMap<>(portals);
        updated.put(id, portal.withLink(null));
        if (portal.linkedPortalId() != null && updated.containsKey(portal.linkedPortalId())) {
            PortalDefinition linked = updated.get(portal.linkedPortalId());
            if (id.equals(linked.linkedPortalId())) updated.put(linked.id(), linked.withLink(null));
        }
        saveAndReplace(sender, updated, "§aĐã bỏ liên kết portal §f" + portal.name() + "§a.");
    }

    private void setEnabled(CommandSender sender, String[] args, boolean enabled) {
        if (args.length != 2) {
            sender.sendMessage("§eSử dụng: /vnportal " + (enabled ? "enable" : "disable") + " <id>");
            return;
        }
        PortalDefinition portal = portals.get(normalizeId(args[1]));
        if (portal == null) {
            sender.sendMessage("§cKhông tìm thấy portal đó.");
            return;
        }
        updatePortal(sender, portal.withEnabled(enabled),
            "§aĐã " + (enabled ? "bật" : "tắt") + " portal §f" + portal.name() + "§a.");
    }

    private void remove(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("§eSử dụng: /vnportal remove <id>");
            return;
        }
        String id = normalizeId(args[1]);
        PortalDefinition portal = portals.get(id);
        if (portal == null) {
            sender.sendMessage("§cKhông tìm thấy portal đó.");
            return;
        }
        Map<String, PortalDefinition> updated = new LinkedHashMap<>(portals);
        updated.remove(id);
        if (portal.linkedPortalId() != null && updated.containsKey(portal.linkedPortalId())) {
            PortalDefinition linked = updated.get(portal.linkedPortalId());
            if (id.equals(linked.linkedPortalId())) updated.put(linked.id(), linked.withLink(null));
        }
        saveAndReplace(sender, updated, "§aĐã xóa portal §f" + portal.name() + "§a.");
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("§eSử dụng: /vnportal info <id>");
            return;
        }
        PortalDefinition portal = portals.get(normalizeId(args[1]));
        if (portal == null) {
            sender.sendMessage("§cKhông tìm thấy portal đó.");
            return;
        }
        sender.sendMessage("§6Portal §f" + portal.name() + " §7(ID: " + portal.id() + ")");
        sender.sendMessage("§7Vùng: " + portal.worldName() + " "
            + portal.minX() + "," + portal.minY() + "," + portal.minZ() + " → "
            + portal.maxX() + "," + portal.maxY() + "," + portal.maxZ());
        sender.sendMessage("§7Trạng thái: " + (portal.enabled() ? "§aBật" : "§cTắt")
            + " §7| Liên kết: " + (portal.linkedPortalId() == null ? "chưa có" : portal.linkedPortalId()));
        sender.sendMessage("§7Điểm đến: " + (portal.destination() == null ? "chưa đặt"
            : portal.destination().worldName() + " " + portal.destination().x() + ", "
                + portal.destination().y() + ", " + portal.destination().z()));
    }

    private void list(CommandSender sender) {
        if (portals.isEmpty()) {
            sender.sendMessage("§7Chưa có portal nào.");
            return;
        }
        sender.sendMessage("§6Portal (" + portals.size() + "):");
        portals.values().forEach(portal -> sender.sendMessage("§e" + portal.id() + " §7- "
            + portal.name() + " §8[" + (portal.enabled() ? "bật" : "tắt") + "]"));
    }

    private void validate(CommandSender sender) {
        int issues = 0;
        for (PortalDefinition portal : portals.values()) {
            List<String> problems = new ArrayList<>();
            if (Bukkit.getWorld(portal.worldName()) == null) problems.add("world chưa tải");
            if (portal.destination() == null) problems.add("chưa đặt điểm đến");
            else {
                Location destination = resolveDestination(portal.destination());
                if (destination == null) problems.add("world điểm đến chưa tải");
                else if (!isSafeDestination(destination)) problems.add("điểm đến không an toàn/chunk chưa tải");
            }
            PortalDefinition linked = portal.linkedPortalId() == null
                ? null : portals.get(portal.linkedPortalId());
            if (portal.linkedPortalId() == null) problems.add("chưa liên kết");
            else if (linked == null) problems.add("portal liên kết không tồn tại");
            else if (!portal.id().equals(linked.linkedPortalId())) problems.add("liên kết không hai chiều");
            if (!problems.isEmpty()) {
                issues++;
                sender.sendMessage("§c" + portal.id() + ": §7" + String.join(", ", problems));
            }
            plugin.getLogger().info("[Portal] Validate " + describePortal(portal)
                + (problems.isEmpty() ? " status=valid" : " issues=" + String.join(", ", problems)));
        }
        if (issues == 0) sender.sendMessage("§aTất cả " + portals.size() + " portal đều hợp lệ.");
        else sender.sendMessage("§eKiểm tra hoàn tất: " + issues + " portal cần xử lý.");
        plugin.getLogger().info("[Portal] Validation complete: " + portals.size()
            + " portal(s), " + issues + " with issues.");
    }

    private void updatePortal(CommandSender sender, PortalDefinition definition, String successMessage) {
        Map<String, PortalDefinition> updated = new LinkedHashMap<>(portals);
        updated.put(definition.id(), definition);
        saveAndReplace(sender, updated, successMessage);
    }

    private void saveAndReplace(CommandSender sender, Map<String, PortalDefinition> updated, String successMessage) {
        if (!savePortals(updated)) {
            sender.sendMessage("§cKhông thể lưu thay đổi portal.");
            return;
        }
        portals.clear();
        portals.putAll(updated);
        sender.sendMessage(successMessage);
    }

    private boolean savePortals(Map<String, PortalDefinition> definitions) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (PortalDefinition portal : definitions.values()) {
            String path = "portals." + portal.id() + ".";
            yaml.set(path + "name", portal.name());
            yaml.set(path + "world", portal.worldName());
            yaml.set(path + "bounds.min-x", portal.minX());
            yaml.set(path + "bounds.min-y", portal.minY());
            yaml.set(path + "bounds.min-z", portal.minZ());
            yaml.set(path + "bounds.max-x", portal.maxX());
            yaml.set(path + "bounds.max-y", portal.maxY());
            yaml.set(path + "bounds.max-z", portal.maxZ());
            yaml.set(path + "enabled", portal.enabled());
            yaml.set(path + "link", portal.linkedPortalId());
            PortalDefinition.Destination destination = portal.destination();
            if (destination != null) {
                yaml.set(path + "destination.world", destination.worldName());
                yaml.set(path + "destination.x", destination.x());
                yaml.set(path + "destination.y", destination.y());
                yaml.set(path + "destination.z", destination.z());
                yaml.set(path + "destination.yaw", destination.yaw());
                yaml.set(path + "destination.pitch", destination.pitch());
            }
        }
        File temporaryFile = new File(storageFile.getParentFile(), storageFile.getName() + ".tmp");
        try {
            yaml.save(temporaryFile);
            try {
                Files.move(temporaryFile.toPath(), storageFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile.toPath(), storageFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            StringBuilder state = new StringBuilder();
            for (PortalDefinition portal : definitions.values()) {
                if (!state.isEmpty()) state.append("; ");
                state.append(describePortal(portal));
            }
            plugin.getLogger().info("[Portal] Saved " + definitions.size() + " portal(s) to "
                + storageFile.getAbsolutePath() + ": " + state);
            return true;
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(temporaryFile.toPath());
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            plugin.getLogger().log(Level.SEVERE, "Could not save portal definitions.", exception);
            return false;
        }
    }

    private void loadPortals() throws IOException, InvalidConfigurationException {
        File parent = storageFile.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create plugin data directory for portals.");
        }
        if (!storageFile.exists()) return;
        YamlConfiguration yaml = new YamlConfiguration();
        YamlDuplicateKeyValidator.validateNoDuplicateKeys(storageFile);
        yaml.load(storageFile);
        ConfigurationSection section = yaml.getConfigurationSection("portals");
        if (section == null) return;
        for (String rawId : section.getKeys(false)) {
            if (!rawId.equals(normalizeId(rawId))) {
                plugin.getLogger().warning("[Portal] Ignoring non-lowercase portal ID '" + rawId + "'.");
                continue;
            }
            ConfigurationSection portal = section.getConfigurationSection(rawId);
            if (portal == null) {
                plugin.getLogger().warning("[Portal] Ignoring malformed portal entry '" + rawId + "'.");
                continue;
            }
            try {
                String world = requiredString(portal, "world");
                ConfigurationSection bounds = requiredSection(portal, "bounds");
                ConfigurationSection destinationSection = portal.getConfigurationSection("destination");
                PortalDefinition.Destination destination = destinationSection == null ? null
                    : new PortalDefinition.Destination(requiredString(destinationSection, "world"),
                        requiredDouble(destinationSection, "x"), requiredDouble(destinationSection, "y"),
                        requiredDouble(destinationSection, "z"),
                        (float) requiredDouble(destinationSection, "yaw"),
                        (float) requiredDouble(destinationSection, "pitch"));
                String link = portal.getString("link");
                PortalDefinition definition = new PortalDefinition(normalizeId(rawId),
                    requiredString(portal, "name"), world,
                    requiredInt(bounds, "min-x"), requiredInt(bounds, "min-y"), requiredInt(bounds, "min-z"),
                    requiredInt(bounds, "max-x"), requiredInt(bounds, "max-y"), requiredInt(bounds, "max-z"),
                    destination, link == null || link.isBlank() ? null : normalizeId(link),
                    portal.getBoolean("enabled", true));
                if (portals.putIfAbsent(definition.id(), definition) != null) {
                    plugin.getLogger().warning("[Portal] Ignoring duplicate portal ID '" + definition.id() + "'.");
                }
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().log(Level.WARNING,
                    "[Portal] Ignoring invalid portal entry '" + rawId + "': " + exception.getMessage());
            }
        }
    }

    private String requiredString(ConfigurationSection section, String key) {
        String value = section.getString(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key + ".");
        return value;
    }

    private int requiredInt(ConfigurationSection section, String key) {
        if (!section.isInt(key)) throw new IllegalArgumentException("Missing or invalid " + key + ".");
        return section.getInt(key);
    }

    private double requiredDouble(ConfigurationSection section, String key) {
        Object value = section.get(key);
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new IllegalArgumentException("Missing or invalid " + key + ".");
        }
        return number.doubleValue();
    }

    private ConfigurationSection requiredSection(ConfigurationSection section, String key) {
        ConfigurationSection value = section.getConfigurationSection(key);
        if (value == null) throw new IllegalArgumentException("Missing " + key + " section.");
        return value;
    }

    private int configuredInteger(String path, int defaultValue, int minimum, int maximum) {
        int value = plugin.getConfig().getInt(path, defaultValue);
        if (value < minimum || value > maximum) {
            if (invalidSettingWarnings.add(path)) {
                plugin.getLogger().warning("[Portal] Invalid " + path + "=" + value
                    + "; using default " + defaultValue + ".");
            }
            return defaultValue;
        }
        return value;
    }

    private long portalTravelCooldownMillis() {
        return configuredInteger("rpg.portals.travel-cooldown-seconds", 3, 0, 300) * 1_000L;
    }

    private String normalizeId(String id) {
        return id.toLowerCase(Locale.ROOT);
    }

    private String formatBlock(Location location) {
        return location.getWorld().getName() + " " + location.getBlockX() + ","
            + location.getBlockY() + "," + location.getBlockZ();
    }

    private String describePortal(PortalDefinition portal) {
        PortalDefinition.Destination destination = portal.destination();
        String destinationDescription = destination == null ? "unset"
            : destination.worldName() + " " + destination.x() + "," + destination.y() + "," + destination.z();
        return portal.id() + "{enabled=" + portal.enabled()
            + ", link=" + (portal.linkedPortalId() == null ? "none" : portal.linkedPortalId())
            + ", destination=" + destinationDescription + "}";
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§e/vnportal <wand|pos1|pos2|create|setspawn|link|unlink|enable|disable|remove|info|list|validate>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!plugin.getPermissionManager().hasPermission(sender, ADMIN_PERMISSION)) return List.of();
        if (args.length == 1) {
            return matching(List.of("wand", "pos1", "pos2", "create", "setspawn", "link", "unlink",
                "enable", "disable", "remove", "info", "list", "validate"), args[0]);
        }
        if (args.length == 2 && List.of("setspawn", "unlink", "enable", "disable", "remove", "info")
            .contains(args[0].toLowerCase(Locale.ROOT))) {
            return matching(new ArrayList<>(portals.keySet()), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("link")) {
            return matching(new ArrayList<>(portals.keySet()), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("link")) {
            return matching(new ArrayList<>(portals.keySet()), args[2]);
        }
        return List.of();
    }

    private List<String> matching(List<String> candidates, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return candidates.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    private record Selection(Location pos1, Location pos2) {
        private Selection {
            pos1 = pos1 == null ? null : pos1.clone();
            pos2 = pos2 == null ? null : pos2.clone();
        }

        private boolean complete() {
            return pos1 != null && pos2 != null;
        }

        private Selection withPosition(boolean first, Location location) {
            return first ? new Selection(location, pos2) : new Selection(pos1, location);
        }
    }
}
