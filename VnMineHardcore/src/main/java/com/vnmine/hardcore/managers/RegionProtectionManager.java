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
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
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
        if (args.length > 0 && "wand".equalsIgnoreCase(args[0])) {
            giveWand(player);
            return true;
        }
        openAdminMenu(player, 0);
        return true;
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("wand");
        return List.of();
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
        meta.setLore(List.of("§7Chuột trái: đặt điểm 1", "§7Chuột phải: đặt điểm 2", "§7Dùng /vnregion để đặt tên vùng và cấu hình flags."));
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
