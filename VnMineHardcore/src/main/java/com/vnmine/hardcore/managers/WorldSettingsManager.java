package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Per-world RPG/gameplay switches managed from the admin GUI. */
public final class WorldSettingsManager implements Listener {
    public static final String ADMIN_PERMISSION = "vnmine.command.world.manage";

    public enum Rule {
        BLOCK_BREAK("block-break-enabled", "Cho phép phá khối", Material.IRON_PICKAXE),
        BLOCK_PLACE("block-place-enabled", "Cho phép đặt khối", Material.BRICKS),
        INTERACTIONS("interactions-enabled", "Cho phép tương tác block", Material.CHEST),
        PVP("pvp-enabled", "Cho phép PvP", Material.IRON_SWORD),
        MOB_SPAWNING("mob-spawning-enabled", "Cho phép sinh quái", Material.ZOMBIE_SPAWN_EGG),
        FRIENDLY_MOB_SPAWNING("friendly-mob-spawning-enabled", "Sinh mob thân thiện", Material.COW_SPAWN_EGG),
        BOSS_SPAWNING("boss-spawning-enabled", "Cho phép boss hệ thống", Material.WITHER_SKELETON_SKULL),
        DISASTERS("disasters-enabled", "Cho phép thiên tai", Material.LIGHTNING_ROD),
        TIME("time-passes", "Thời gian trôi", Material.CLOCK),
        HUNGER("hunger-drain-enabled", "Tiêu hao đói", Material.COOKED_BEEF),
        THIRST("thirst-drain-enabled", "Tiêu hao khát", Material.WATER_BUCKET);

        private final String key;
        private final String label;
        private final Material icon;

        Rule(String key, String label, Material icon) {
            this.key = key;
            this.label = label;
            this.icon = icon;
        }
    }

    private final VnMineHardcore plugin;
    private final File file;
    private final YamlConfiguration data = new YamlConfiguration();

    public WorldSettingsManager(VnMineHardcore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "world-settings.yml");
        data.set("schema-version", 1);
        load();
        for (World world : Bukkit.getWorlds()) applyTimeRule(world);
    }

    public boolean isEnabled(World world, Rule rule) {
        if (world == null || rule == null) return true;
        String path = path(world, rule);
        if (data.contains(path)) return data.getBoolean(path);
        String start = "rpg.onboarding.start-world";
        if (world.getName().equalsIgnoreCase(plugin.getConfig().getString(start + ".name", "world_start"))) {
            return switch (rule) {
                case BLOCK_BREAK -> !plugin.getConfig().getBoolean(start + ".protection.enabled", true)
                    || !plugin.getConfig().getBoolean(start + ".protection.block-break", true);
                case BLOCK_PLACE -> !plugin.getConfig().getBoolean(start + ".protection.enabled", true)
                    || !plugin.getConfig().getBoolean(start + ".protection.block-place", true);
                case INTERACTIONS -> !plugin.getConfig().getBoolean(start + ".protection.enabled", true)
                    || !plugin.getConfig().getBoolean(start + ".protection.interactions", true);
                case PVP -> plugin.getConfig().getBoolean(start + ".pvp-enabled", false);
                case MOB_SPAWNING -> plugin.getConfig().getBoolean(start + ".mob-spawning-enabled", false);
                case FRIENDLY_MOB_SPAWNING -> plugin.getConfig().getBoolean(start + ".friendly-mob-spawning-enabled", true);
                case TIME -> plugin.getConfig().getBoolean(start + ".time-passes", false);
                case HUNGER -> plugin.getConfig().getBoolean(start + ".hunger-drain-enabled", false);
                case THIRST -> plugin.getConfig().getBoolean(start + ".thirst-drain-enabled", false);
                case BOSS_SPAWNING, DISASTERS -> true;
            };
        }
        return defaultValue(rule);
    }

    public boolean hasOverride(World world, Rule rule) {
        return world != null && data.contains(path(world, rule));
    }

    public void clearOverride(World world, Rule rule) {
        if (world == null || !hasOverride(world, rule)) return;
        String key = path(world, rule);
        Object old = data.get(key);
        data.set(key, null);
        if (!save()) data.set(key, old);
    }

    public boolean isDisasterEnabled(World world) { return isEnabled(world, Rule.DISASTERS); }
    public boolean isBossSpawningEnabled(World world) { return isEnabled(world, Rule.BOSS_SPAWNING); }

    public void open(Player player) {
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            player.sendMessage("§cBạn không có quyền quản lý cài đặt world.");
            return;
        }
        openWorlds(player, 0);
    }

    private void openWorlds(Player player, int requestedPage) {
        List<World> worlds = Bukkit.getWorlds().stream()
            .sorted(Comparator.comparing(World::getName, String.CASE_INSENSITIVE_ORDER)).toList();
        int pages = Math.max(1, (worlds.size() + 44) / 45);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        WorldMenuHolder holder = new WorldMenuHolder(player.getUniqueId(), "worlds", null, page);
        Inventory menu = Bukkit.createInventory(holder, 54, "Admin • Quản lý world • " + (page + 1));
        holder.inventory = menu;
        for (int slot = 0; slot < 45 && page * 45 + slot < worlds.size(); slot++) {
            World world = worlds.get(page * 45 + slot);
            holder.worldSlots.put(slot, world.getName());
            menu.setItem(slot, item(Material.GRASS_BLOCK, "§b" + world.getName(), List.of(
                "§7Environment: §f" + world.getEnvironment(),
                "§7Players: §f" + world.getPlayers().size(), "§eNhấn để chỉnh cài đặt world.")));
        }
        if (page > 0) menu.setItem(45, item(Material.ARROW, "§eTrang trước", List.of()));
        menu.setItem(49, item(Material.ARROW, "§7Quay lại Admin", List.of()));
        if (page + 1 < pages) menu.setItem(53, item(Material.ARROW, "§eTrang sau", List.of()));
        player.openInventory(menu);
    }

    private void openSettings(Player player, String worldName, int page) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage("§cWorld này không còn được nạp.");
            openWorlds(player, page);
            return;
        }
        WorldMenuHolder holder = new WorldMenuHolder(player.getUniqueId(), "settings", worldName, page);
        Inventory menu = Bukkit.createInventory(holder, 54, "Cài đặt world • " + worldName);
        holder.inventory = menu;
        menu.setItem(4, item(Material.COMPASS, "§b" + worldName, List.of(
            "§7Environment: §f" + world.getEnvironment(), "§7Múi giờ: §f" + world.getTime())));
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 28, 29, 30, 32};
        Rule[] rules = Rule.values();
        for (int index = 0; index < rules.length; index++) {
            Rule rule = rules[index];
            boolean enabled = isEnabled(world, rule);
            holder.ruleSlots.put(slots[index], rule);
            List<String> lore = new java.util.ArrayList<>(List.of(
                "§7World: §f" + worldName, "§eNhấn để đổi trạng thái."));
            if (rule == Rule.FRIENDLY_MOB_SPAWNING) {
                lore.add("§7Mob thân thiện cần bật cả mục sinh quái chung.");
            }
            menu.setItem(slots[index], item(enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                (enabled ? "§aBẬT • " : "§cTẮT • ") + rule.label, lore));
        }
        menu.setItem(49, item(Material.ARROW, "§eQuay lại danh sách world", List.of()));
        menu.setItem(53, item(Material.BARRIER, "§cĐóng", List.of()));
        player.openInventory(menu);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof WorldMenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.viewer.equals(player.getUniqueId())
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            player.closeInventory();
            player.sendMessage("§cBạn không còn quyền quản lý world.");
            return;
        }
        int slot = event.getRawSlot();
        if ("worlds".equals(holder.type)) {
            if (slot == 49) plugin.getMercenaryManager().openAdminMenu(player);
            else if (slot == 45 && holder.page > 0) openWorlds(player, holder.page - 1);
            else if (slot == 53) openWorlds(player, holder.page + 1);
            else {
                String worldName = holder.worldSlots.get(slot);
                if (worldName != null) openSettings(player, worldName, holder.page);
            }
            return;
        }
        if (slot == 49) {
            openWorlds(player, holder.page);
            return;
        }
        if (slot == 53) return;
        Rule rule = holder.ruleSlots.get(slot);
        if (rule == null) return;
        String path = path(holder.worldName, rule);
        boolean next = !isEnabled(Bukkit.getWorld(holder.worldName), rule);
        data.set(path, next);
        if (!save()) {
            player.sendMessage("§cKhông thể lưu cài đặt world. Giá trị cũ được giữ.");
            data.set(path, !next);
            return;
        }
        World world = Bukkit.getWorld(holder.worldName);
        if (rule == Rule.TIME && world != null) applyTimeRule(world);
        plugin.getLogger().info("[WorldSettings] admin=" + player.getName() + ", world=" + holder.worldName
            + ", rule=" + rule.key + ", enabled=" + next);
        openSettings(player, holder.worldName, holder.page);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof WorldMenuHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) { applyTimeRule(event.getWorld()); }

    private void applyTimeRule(World world) {
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, isEnabled(world, Rule.TIME));
    }

    private void load() {
        if (!file.isFile()) return;
        try {
            data.load(file);
            if (data.getInt("schema-version", -1) != 1) {
                plugin.getLogger().severe("[WorldSettings] Unsupported world-settings.yml schema-version; "
                    + "expected 1. Existing file was preserved and defaults will be used.");
                data.getKeys(false).stream().filter(key -> !"schema-version".equals(key)).toList()
                    .forEach(key -> data.set(key, null));
                data.set("schema-version", 1);
            }
        }
        catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "[WorldSettings] Could not load world-settings.yml.", exception);
        }
    }

    private boolean save() {
        File temporary = new File(file.getParentFile(), "world-settings.yml.tmp");
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("mkdir failed");
            String header = "# [VI] Quy tắc gameplay theo từng world; chỉnh bằng Menu chính > Admin > Quản lý world.\n"
                + "# [EN] Per-world gameplay rules; edit through Main Menu > Admin > Manage Worlds.\n"
                + "# [VI] Chỉ các khóa được admin đổi mới lưu; quy tắc chưa lưu dùng giá trị mặc định/plugin config.\n"
                + "# [EN] Only rules changed by an admin are stored; unset rules use plugin defaults/config.\n";
            Files.writeString(temporary.toPath(), header + data.saveToString(), java.nio.charset.StandardCharsets.UTF_8);
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "[WorldSettings] Could not save world-settings.yml.", exception);
            return false;
        } finally {
            try { Files.deleteIfExists(temporary.toPath()); }
            catch (IOException exception) {
                plugin.getLogger().log(Level.WARNING, "[WorldSettings] Could not clean temporary settings file.", exception);
            }
        }
    }

    private static String path(String worldName, Rule rule) {
        return "worlds." + worldName.toLowerCase(Locale.ROOT) + "." + rule.key;
    }

    private static String path(World world, Rule rule) { return path(world.getName(), rule); }

    private static boolean defaultValue(Rule rule) {
        return switch (rule) {
            case PVP -> true;
            case BLOCK_BREAK, BLOCK_PLACE, INTERACTIONS -> true;
            case MOB_SPAWNING, FRIENDLY_MOB_SPAWNING, BOSS_SPAWNING, DISASTERS, TIME, HUNGER, THIRST -> true;
        };
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack result = new ItemStack(material);
        ItemMeta meta = result.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        result.setItemMeta(meta);
        return result;
    }

    private static final class WorldMenuHolder implements InventoryHolder {
        private final UUID viewer;
        private final String type;
        private final String worldName;
        private final int page;
        private final Map<Integer, String> worldSlots = new HashMap<>();
        private final Map<Integer, Rule> ruleSlots = new HashMap<>();
        private Inventory inventory;

        private WorldMenuHolder(UUID viewer, String type, String worldName, int page) {
            this.viewer = viewer;
            this.type = type;
            this.worldName = worldName;
            this.page = page;
        }

        @Override public Inventory getInventory() { return inventory; }
    }
}
