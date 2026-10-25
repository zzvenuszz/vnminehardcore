package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class BossLeaderboardManager implements Listener {
    private static final int MAX_RANKED_PLAYERS = 10;
    private static final int MAX_DAMAGE_DISPLAY = 5;
    private static final Map<String, String> LEGACY_ENCHANTMENTS = Map.ofEntries(
        Map.entry("DAMAGE_ALL", "sharpness"),
        Map.entry("DAMAGE_UNDEAD", "smite"),
        Map.entry("DAMAGE_ARTHROPODS", "bane_of_arthropods"),
        Map.entry("PROTECTION_ENVIRONMENTAL", "protection"),
        Map.entry("PROTECTION_FIRE", "fire_protection"),
        Map.entry("PROTECTION_FALL", "feather_falling"),
        Map.entry("PROTECTION_EXPLOSIONS", "blast_protection"),
        Map.entry("PROTECTION_PROJECTILE", "projectile_protection"),
        Map.entry("OXYGEN", "respiration"),
        Map.entry("WATER_WORKER", "aqua_affinity"),
        Map.entry("DURABILITY", "unbreaking"),
        Map.entry("LOOT_BONUS_MOBS", "looting"),
        Map.entry("ARROW_DAMAGE", "power"),
        Map.entry("ARROW_KNOCKBACK", "punch"),
        Map.entry("ARROW_FIRE", "flame"),
        Map.entry("ARROW_INFINITE", "infinity")
    );
    private final VnMineHardcore plugin;
    private final File dataFile;
    private final Map<UUID, Double> seasonPoints = new HashMap<>();
    private final Map<UUID, String> playerNames = new HashMap<>();
    private final Map<UUID, Map<String, List<ItemStack>>> pendingRewards = new HashMap<>();
    private final Map<UUID, Double> bossDamage = new HashMap<>();
    private final Map<UUID, Scoreboard> playerBoards = new HashMap<>();
    private final Map<UUID, Objective> playerObjectives = new HashMap<>();
    private final Map<UUID, Scoreboard> previousBoards = new HashMap<>();

    private YamlConfiguration settings;
    private long lastResetGameDay;
    private long seasonNumber;
    private int resetEveryGameDays = 100;
    private int winnerTitleSeconds = 10;
    private int reminderIntervalMinutes = 60;
    private boolean enabled = true;
    private boolean scoreboardEnabled = true;
    private String scoreboardTitle = "§6§lBOSS RANKING";
    private Entity activeBoss;
    private BukkitRunnable ticker;
    private int minutesSinceReminder;

    public BossLeaderboardManager(VnMineHardcore plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "scoreboard-data.yml");
        reload();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startTicker();
    }

    public void reload() {
        File settingsFile = new File(plugin.getDataFolder(), "scoreboard.yml");
        settings = YamlConfiguration.loadConfiguration(settingsFile);
        if (settings.getInt("reset.every-game-days", 30) == 30
                && !settings.getBoolean("_migrations.reset-every-game-days-100", false)) {
            settings.set("reset.every-game-days", 100);
            settings.set("_migrations.reset-every-game-days-100", true);
            try {
                settings.save(settingsFile);
            } catch (IOException exception) {
                plugin.getLogger().log(Level.SEVERE,
                    "[Scoreboard] Could not migrate reset.every-game-days to 100.", exception);
            }
        }
        enabled = settings.getBoolean("enabled", true);
        scoreboardEnabled = enabled && settings.getBoolean("scoreboard.enabled", true);
        scoreboardTitle = color(settings.getString("scoreboard.title", "§6§lBOSS RANKING"));
        resetEveryGameDays = boundedInt("reset.every-game-days", 100, 1, 100000);
        winnerTitleSeconds = boundedInt("reset.winner-title-seconds", 10, 1, 60);
        reminderIntervalMinutes = boundedInt("rewards.reminder-interval-minutes", 60, 1, 10080);
        loadData();
        if (enabled && lastResetGameDay == Long.MIN_VALUE) {
            lastResetGameDay = currentGameDay();
            saveData();
        }
        refreshAllBoards();
        if (ticker != null) startTicker();
    }

    private int boundedInt(String path, int fallback, int minimum, int maximum) {
        int value = settings.getInt(path, fallback);
        if (value < minimum || value > maximum) {
            plugin.getLogger().warning("[Scoreboard] Invalid " + path + "; using " + fallback + ".");
            return fallback;
        }
        return value;
    }

    private void loadData() {
        seasonPoints.clear();
        playerNames.clear();
        pendingRewards.clear();
        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        lastResetGameDay = data.contains("last-reset-game-day")
            ? Math.max(0L, data.getLong("last-reset-game-day")) : Long.MIN_VALUE;
        seasonNumber = Math.max(0L, data.getLong("season-number", 0L));

        ConfigurationSection scores = data.getConfigurationSection("scores");
        if (scores != null) {
            for (String id : scores.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(id);
                    double points = scores.getDouble(id + ".points");
                    if (Double.isFinite(points) && points >= 0) seasonPoints.put(uuid, points);
                    String name = scores.getString(id + ".name");
                    if (name != null && !name.isBlank()) playerNames.put(uuid, name);
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().warning("[Scoreboard] Ignoring invalid player UUID in scoreboard-data.yml: " + id);
                }
            }
        }

        ConfigurationSection rewards = data.getConfigurationSection("pending");
        if (rewards != null) {
            for (String playerId : rewards.getKeys(false)) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(playerId);
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().warning("[Scoreboard] Ignoring invalid reward UUID in scoreboard-data.yml: " + playerId);
                    continue;
                }
                ConfigurationSection playerRewards = rewards.getConfigurationSection(playerId);
                if (playerRewards == null) continue;
                Map<String, List<ItemStack>> seasons = new LinkedHashMap<>();
                for (String season : playerRewards.getKeys(false)) {
                    List<ItemStack> items = new ArrayList<>();
                    for (Object item : playerRewards.getList(season + ".items", List.of())) {
                        if (item instanceof ItemStack stack && stack.getType() != Material.AIR) {
                            items.add(stack.clone());
                        }
                    }
                    if (!items.isEmpty()) seasons.put(season, items);
                }
                if (!seasons.isEmpty()) pendingRewards.put(uuid, seasons);
            }
        }
    }

    private boolean saveData() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("last-reset-game-day", lastResetGameDay);
        data.set("season-number", seasonNumber);
        for (Map.Entry<UUID, Double> entry : seasonPoints.entrySet()) {
            String path = "scores." + entry.getKey();
            data.set(path + ".points", entry.getValue());
            String name = playerNames.get(entry.getKey());
            if (name != null) data.set(path + ".name", name);
        }
        for (Map.Entry<UUID, Map<String, List<ItemStack>>> player : pendingRewards.entrySet()) {
            for (Map.Entry<String, List<ItemStack>> season : player.getValue().entrySet()) {
                data.set("pending." + player.getKey() + "." + season.getKey() + ".items", season.getValue());
            }
        }
        File temporaryFile = new File(dataFile.getParentFile(), dataFile.getName() + ".tmp");
        try {
            data.save(temporaryFile);
            try {
                Files.move(temporaryFile.toPath(), dataFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveException) {
                Files.move(temporaryFile.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "[Scoreboard] Could not save scoreboard-data.yml.", exception);
            if (temporaryFile.exists() && !temporaryFile.delete()) {
                plugin.getLogger().warning("[Scoreboard] Could not remove incomplete scoreboard-data.yml.tmp.");
            }
            return false;
        }
    }

    private void startTicker() {
        if (ticker != null) ticker.cancel();
        ticker = new BukkitRunnable() {
            @Override
            public void run() {
                if (!enabled) return;
                if (currentGameDay() - lastResetGameDay >= resetEveryGameDays) resetSeason();
                refreshAllBoards();
                minutesSinceReminder++;
                if (minutesSinceReminder >= reminderIntervalMinutes) {
                    minutesSinceReminder = 0;
                    remindOnlinePlayers();
                }
            }
        };
        ticker.runTaskTimer(plugin, 1200L, 1200L);
    }

    private long currentGameDay() {
        if (Bukkit.getWorlds().isEmpty()) return 0L;
        World world = Bukkit.getWorlds().get(0);
        return Math.max(0L, world.getFullTime() / 24000L);
    }

    private void resetSeason() {
        List<Map.Entry<UUID, Double>> ranking = orderedScores();
        Map<UUID, Double> previousPoints = new HashMap<>(seasonPoints);
        Map<UUID, Map<String, List<ItemStack>>> previousRewards = copyPendingRewards();
        long previousSeasonNumber = seasonNumber;
        long previousResetDay = lastResetGameDay;
        seasonNumber++;
        String seasonId = Long.toString(seasonNumber);
        for (int index = 0; index < Math.min(MAX_RANKED_PLAYERS, ranking.size()); index++) {
            Map.Entry<UUID, Double> entry = ranking.get(index);
            List<ItemStack> configuredItems = configuredRewardItems(index + 1);
            if (configuredItems.isEmpty()) continue;
            ItemStack selectedReward = configuredItems.get(
                java.util.concurrent.ThreadLocalRandom.current().nextInt(configuredItems.size()));
            pendingRewards.computeIfAbsent(entry.getKey(), ignored -> new LinkedHashMap<>())
                .put(seasonId, List.of(selectedReward));
        }

        String winnerName = ranking.isEmpty() ? null : nameOf(ranking.get(0).getKey());
        seasonPoints.clear();
        lastResetGameDay = currentGameDay();
        if (!saveData()) {
            seasonPoints.putAll(previousPoints);
            pendingRewards.clear();
            pendingRewards.putAll(previousRewards);
            seasonNumber = previousSeasonNumber;
            lastResetGameDay = previousResetDay;
            return;
        }

        Bukkit.broadcastMessage("§6§lBảng xếp hạng boss đã được reset! Phần thưởng đã phát, dùng §e/reward§6 để nhận.");
        if (winnerName != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendTitle("§6§lChúc mừng " + winnerName + "!",
                    "§eĐứng đầu mùa xếp hạng boss", 10, winnerTitleSeconds * 20, 20);
            }
        }
        refreshAllBoards();
    }

    public boolean resetAllData() {
        Map<UUID, Double> previousPoints = new HashMap<>(seasonPoints);
        Map<UUID, String> previousNames = new HashMap<>(playerNames);
        Map<UUID, Map<String, List<ItemStack>>> previousRewards = copyPendingRewards();
        Map<UUID, Double> previousBossDamage = new HashMap<>(bossDamage);
        long previousSeasonNumber = seasonNumber;
        long previousResetDay = lastResetGameDay;

        seasonPoints.clear();
        playerNames.clear();
        pendingRewards.clear();
        bossDamage.clear();
        seasonNumber = 0L;
        lastResetGameDay = currentGameDay();
        if (!saveData()) {
            seasonPoints.putAll(previousPoints);
            playerNames.putAll(previousNames);
            pendingRewards.putAll(previousRewards);
            bossDamage.putAll(previousBossDamage);
            seasonNumber = previousSeasonNumber;
            lastResetGameDay = previousResetDay;
            refreshAllBoards();
            return false;
        }

        refreshAllBoards();
        return true;
    }

    private Map<UUID, Map<String, List<ItemStack>>> copyPendingRewards() {
        Map<UUID, Map<String, List<ItemStack>>> copy = new HashMap<>();
        for (Map.Entry<UUID, Map<String, List<ItemStack>>> player : pendingRewards.entrySet()) {
            Map<String, List<ItemStack>> seasons = new LinkedHashMap<>();
            for (Map.Entry<String, List<ItemStack>> season : player.getValue().entrySet()) {
                seasons.put(season.getKey(), season.getValue().stream().map(ItemStack::clone).toList());
            }
            copy.put(player.getKey(), seasons);
        }
        return copy;
    }

    private List<ItemStack> configuredRewardItems(int rank) {
        List<ItemStack> items = new ArrayList<>();
        ConfigurationSection rewardSection = settings.getConfigurationSection("rewards.top-" + rank);
        if (rewardSection != null) {
            for (String id : rewardSection.getKeys(false)) {
                ConfigurationSection section = rewardSection.getConfigurationSection(id);
                if (section == null || !section.getBoolean("enabled", false)) continue;
                ItemStack item = createConfiguredItem(section);
                if (item != null) items.add(item);
            }
        }
        addRewardPoolItems(rank, items);
        return items;
    }

    private void addRewardPoolItems(int rank, List<ItemStack> items) {
        List<Map<?, ?>> configuredPool = settings.getMapList("rewards.top-" + rank + ".pool");
        for (int index = 0; index < configuredPool.size(); index++) {
            Map<?, ?> entry = configuredPool.get(index);
            if (Boolean.FALSE.equals(entry.get("enabled"))) continue;
            Object materialValue = entry.get("material");
            if (!(materialValue instanceof String materialName)) {
                plugin.getLogger().warning("[Scoreboard] Missing material in rewards.top-" + rank
                    + ".pool[" + index + "].");
                continue;
            }
            Material material = Material.matchMaterial(materialName);
            if (material == null || material.isAir()) {
                plugin.getLogger().warning("[Scoreboard] Invalid reward material '" + materialName
                    + "' in rewards.top-" + rank + ".pool[" + index + "].");
                continue;
            }
            Object amountValue = entry.get("amount");
            int amount = amountValue instanceof Number number ? number.intValue() : 1;
            if (amount < 1 || amount > material.getMaxStackSize()) {
                plugin.getLogger().warning("[Scoreboard] Invalid amount in rewards.top-" + rank
                    + ".pool[" + index + "].");
                continue;
            }
            String name = entry.get("name") instanceof String value ? value : null;
            List<String> lore = entry.get("lore") instanceof List<?> values
                ? values.stream().filter(String.class::isInstance).map(String.class::cast).toList()
                : List.of();
            ItemStack item = new ItemStack(material, amount);
            applyMetadata(item, name, lore, null, null);
            items.add(item);
        }
    }

    private ItemStack createConfiguredItem(ConfigurationSection section) {
        Material material = Material.matchMaterial(section.getString("material", ""));
        if (material == null || material.isAir()) {
            plugin.getLogger().warning("[Scoreboard] Invalid reward material in " + section.getCurrentPath() + ".");
            return null;
        }
        int amount = section.getInt("amount", 1);
        if (amount < 1 || amount > material.getMaxStackSize()) {
            plugin.getLogger().warning("[Scoreboard] Invalid reward amount in " + section.getCurrentPath() + ".");
            return null;
        }
        ItemStack item = new ItemStack(material, amount);
        validateEnchantments(section.getConfigurationSection("enchantments"), section.getCurrentPath());
        validateEnchantments(section.getConfigurationSection("book-enchantments"), section.getCurrentPath());
        applyMetadata(item, section.getString("name"), section.getStringList("lore"),
            section.getConfigurationSection("enchantments"),
            section.getConfigurationSection("book-enchantments"));
        return item;
    }

    private void validateEnchantments(ConfigurationSection enchants, String itemPath) {
        if (enchants == null) return;
        for (String name : enchants.getKeys(false)) {
            int level = enchants.getInt(name, 0);
            if (resolveEnchantment(name) == null || level < 1 || level > 255) {
                plugin.getLogger().warning("[Scoreboard] Ignoring invalid enchantment '" + name +
                    "' in " + itemPath + ".");
            }
        }
    }

    public void startBoss(Entity boss) {
        activeBoss = boss;
        bossDamage.clear();
        refreshAllBoards();
    }

    public void recordBossDamage(Player attacker, double damage) {
        if (activeBoss == null || attacker == null || !Double.isFinite(damage) || damage <= 0.0) return;
        UUID id = attacker.getUniqueId();
        bossDamage.merge(id, damage, Double::sum);
        playerNames.put(id, attacker.getName());
        refreshAllBoards();
    }

    public void finishBoss(int level, boolean killed) {
        finishBoss(level, killed ? bossDamage.keySet() : java.util.Set.of());
    }

    public void finishBoss(int level, java.util.Set<UUID> contributors) {
        finishBoss(level, bossDamage, contributors);
    }

    public void finishBoss(int level, Map<UUID, Double> contributionDamage) {
        finishBoss(level, contributionDamage, contributionDamage.keySet());
    }

    private void finishBoss(int level, Map<UUID, Double> contributionDamage, java.util.Set<UUID> contributors) {
        if (enabled && !contributionDamage.isEmpty() && !contributors.isEmpty()) {
            List<Map.Entry<UUID, Double>> contributionEntries = contributionDamage.entrySet().stream()
                .filter(entry -> contributors.contains(entry.getKey()))
                .filter(entry -> Double.isFinite(entry.getValue()) && entry.getValue() > 0.0)
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .toList();
            double totalDamage = contributionEntries.stream().mapToDouble(Map.Entry::getValue).sum();
            double pointsToShare = 10.0 * Math.max(1, level);
            if (totalDamage > 0.0) {
                for (Map.Entry<UUID, Double> contributor : contributionEntries) {
                    UUID uuid = contributor.getKey();
                    seasonPoints.merge(uuid, pointsToShare * contributor.getValue() / totalDamage, Double::sum);
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null) playerNames.put(uuid, player.getName());
                }
                saveData();
            }
        }
        activeBoss = null;
        bossDamage.clear();
        refreshAllBoards();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        refreshBoard(event.getPlayer());
        remindPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        restorePreviousBoard(event.getPlayer());
        UUID playerId = event.getPlayer().getUniqueId();
        playerBoards.remove(playerId);
        playerObjectives.remove(playerId);
        previousBoards.remove(playerId);
    }

    public boolean claimRewards(Player player) {
        Map<String, List<ItemStack>> rewards = pendingRewards.get(player.getUniqueId());
        if (rewards == null || rewards.isEmpty()) {
            player.sendMessage("§cBạn hiện không có phần thưởng boss nào đang chờ nhận.");
            return true;
        }

        List<ItemStack> items = rewards.values().stream().flatMap(List::stream)
            .map(ItemStack::clone).toList();
        Inventory preview = Bukkit.createInventory(null, 36);
        preview.setStorageContents(cloneContents(player.getInventory().getStorageContents()));
        Map<Integer, ItemStack> leftovers = preview.addItem(items.stream().map(ItemStack::clone).toArray(ItemStack[]::new));
        if (!leftovers.isEmpty()) {
            player.sendMessage("§cTúi đồ của bạn đã đầy. Hãy dọn dẹp inventory rồi thử lại bằng /reward.");
            return true;
        }

        ItemStack[] originalContents = cloneContents(player.getInventory().getStorageContents());
        Map<String, List<ItemStack>> claimedRewards = pendingRewards.remove(player.getUniqueId());
        player.getInventory().addItem(items.toArray(ItemStack[]::new));
        if (!saveData()) {
            pendingRewards.put(player.getUniqueId(), claimedRewards);
            player.getInventory().setStorageContents(originalContents);
            player.sendMessage("§cKhông thể lưu trạng thái nhận thưởng. Vui lòng thử lại sau.");
            return true;
        }
        player.sendMessage("§aĐã nhận phần thưởng xếp hạng boss của bạn!");
        return true;
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copies = new ItemStack[contents.length];
        for (int index = 0; index < contents.length; index++) {
            copies[index] = contents[index] == null ? null : contents[index].clone();
        }
        return copies;
    }

    private void remindOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) remindPlayer(player);
    }

    private void remindPlayer(Player player) {
        Map<String, List<ItemStack>> rewards = pendingRewards.get(player.getUniqueId());
        if (rewards != null && !rewards.isEmpty()) {
            player.sendMessage("§eBạn còn phần thưởng boss chưa nhận. Dùng §6/reward§e để nhận.");
        }
    }

    public void refreshAllBoards() {
        if (!scoreboardEnabled) {
            restorePreviousBoards();
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) refreshBoard(player);
    }

    private void refreshBoard(Player player) {
        if (!scoreboardEnabled) {
            restorePreviousBoard(player);
            return;
        }
        Scoreboard board = playerBoards.get(player.getUniqueId());
        Objective objective = playerObjectives.get(player.getUniqueId());
        if (board == null || objective == null) {
            previousBoards.put(player.getUniqueId(), player.getScoreboard());
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            objective = board.registerNewObjective("vnmineboss", "dummy", scoreboardTitle);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            playerBoards.put(player.getUniqueId(), board);
            playerObjectives.put(player.getUniqueId(), objective);
            player.setScoreboard(board);
        } else {
            objective.setDisplayName(scoreboardTitle);
            if (player.getScoreboard() != board) player.setScoreboard(board);
        }

        for (String entry : new ArrayList<>(board.getEntries())) board.resetScores(entry);
        List<String> lines = new ArrayList<>();
        if (activeBoss != null && activeBoss.isValid() && !activeBoss.isDead()) {
            lines.add("§e§lSát thương boss");
            List<Map.Entry<UUID, Double>> ranking = bossDamage.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(MAX_DAMAGE_DISPLAY).toList();
            if (ranking.isEmpty()) lines.add("§7Chưa có sát thương");
            else {
                for (int index = 0; index < ranking.size(); index++) {
                    Map.Entry<UUID, Double> entry = ranking.get(index);
                    lines.add("§f" + (index + 1) + ". " + shortName(nameOf(entry.getKey())) +
                        " §c" + format(entry.getValue()));
                }
            }
        } else {
            lines.add("§e§lĐiểm mùa này");
            List<Map.Entry<UUID, Double>> ranking = orderedScores();
            if (ranking.isEmpty()) lines.add("§7Chưa có điểm");
            else {
                for (int index = 0; index < Math.min(MAX_RANKED_PLAYERS, ranking.size()); index++) {
                    Map.Entry<UUID, Double> entry = ranking.get(index);
                    lines.add("§f" + (index + 1) + ". " + shortName(nameOf(entry.getKey())) +
                        " §6" + format(entry.getValue()));
                }
            }
        }
        lines.add("§8Ngày " + currentGameDay() + " • /reward");
        for (int index = 0; index < lines.size(); index++) {
            String entry = uniqueLine(lines.get(index), index);
            objective.getScore(entry).setScore(lines.size() - index);
        }
        player.setScoreboard(board);
    }

    private String uniqueLine(String line, int index) {
        String suffix = switch (index % 5) {
            case 0 -> "§0";
            case 1 -> "§1";
            case 2 -> "§2";
            case 3 -> "§3";
            default -> "§4";
        };
        return line + suffix;
    }

    private void restorePreviousBoard(Player player) {
        Scoreboard board = playerBoards.get(player.getUniqueId());
        if (board != null && player.getScoreboard() == board) {
            Scoreboard previous = previousBoards.get(player.getUniqueId());
            player.setScoreboard(previous == null ? Bukkit.getScoreboardManager().getMainScoreboard() : previous);
        }
    }

    private void restorePreviousBoards() {
        for (Player player : Bukkit.getOnlinePlayers()) restorePreviousBoard(player);
    }

    private List<Map.Entry<UUID, Double>> orderedScores() {
        return seasonPoints.entrySet().stream()
            .filter(entry -> entry.getValue() > 0.0 && Double.isFinite(entry.getValue()))
            .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
            .toList();
    }

    private String nameOf(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        return playerNames.getOrDefault(uuid, uuid.toString().substring(0, 8));
    }

    private String shortName(String name) {
        return name.length() <= 10 ? name : name.substring(0, 10);
    }

    private String format(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    public static void applyMetadata(ItemStack item, String name, List<String> lore,
                                     ConfigurationSection enchantments,
                                     ConfigurationSection bookEnchantments) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        if (name != null && !name.isBlank()) {
            meta.setDisplayName(EconomyManager.sanitizeVisibleName(
                ChatColor.translateAlternateColorCodes('&', name)));
        }
        if (lore != null && !lore.isEmpty()) {
            meta.setLore(lore.stream().map(line -> ChatColor.translateAlternateColorCodes('&', line)).toList());
        }
        applyEnchantments(meta, enchantments);
        if (meta instanceof EnchantmentStorageMeta stored && bookEnchantments != null) {
            for (String key : bookEnchantments.getKeys(false)) {
                Enchantment enchantment = resolveEnchantment(key);
                int level = bookEnchantments.getInt(key, 1);
                if (enchantment != null && level >= 1 && level <= 255) stored.addStoredEnchant(enchantment, level, true);
            }
        }
        item.setItemMeta(meta);
    }

    private static void applyEnchantments(ItemMeta meta, ConfigurationSection section) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            Enchantment enchantment = resolveEnchantment(key);
            int level = section.getInt(key, 1);
            if (enchantment == null || level < 1 || level > 255) continue;
            if (meta instanceof EnchantmentStorageMeta stored) stored.addStoredEnchant(enchantment, level, true);
            else meta.addEnchant(enchantment, level, true);
        }
    }

    private static Enchantment resolveEnchantment(String name) {
        if (name == null || name.isBlank()) return null;
        String upper = name.toUpperCase(Locale.ROOT);
        String key = LEGACY_ENCHANTMENTS.getOrDefault(upper, name.toLowerCase(Locale.ROOT));
        try {
            return Enchantment.getByKey(NamespacedKey.minecraft(key));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public void stop() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        saveData();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Scoreboard previous = previousBoards.get(player.getUniqueId());
            if (playerBoards.containsKey(player.getUniqueId()) &&
                    player.getScoreboard() == playerBoards.get(player.getUniqueId())) {
                player.setScoreboard(previous == null ? Bukkit.getScoreboardManager().getMainScoreboard() : previous);
            }
        }
        playerBoards.clear();
        playerObjectives.clear();
        previousBoards.clear();
    }
}
