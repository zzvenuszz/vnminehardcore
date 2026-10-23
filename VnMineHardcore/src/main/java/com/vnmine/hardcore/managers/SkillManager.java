package com.vnmine.hardcore.managers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.EventPriority;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class SkillManager implements Listener, CommandExecutor, TabCompleter {
    private static final String ADMIN_PERMISSION = "vnmine.command.skillbook.admin";
    private static final Set<String> CAST_HANDLERS = Set.of(
        "blade-flare", "guard-break", "shield-wall", "taunt", "fire-bolt", "arcane-field");
    private static final Set<String> IMPLEMENTED_PASSIVES = Set.of("battle-focus", "fortify", "mana-flow");

    private final JavaPlugin plugin;
    private final PlayerProfileDatabaseManager database;
    private final ConfigManager config;
    private final NamespacedKey bookIdKey;
    private final NamespacedKey skillIdKey;
    private SkillDefinitionLoader.SkillRegistry registry;
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Integer> battleFocusRanks = new HashMap<>();
    private final Map<UUID, Integer> fortifyRanks = new HashMap<>();
    private final Map<UUID, FortifyState> fortifyStates = new HashMap<>();
    private final Map<UUID, Integer> manaFlowRanks = new HashMap<>();
    private final Map<UUID, Long> manaFlowLastCastAt = new HashMap<>();
    private final Set<UUID> mouseDropInteractions = ConcurrentHashMap.newKeySet();

    public SkillManager(JavaPlugin plugin, PlayerProfileDatabaseManager database, ConfigManager config) {
        this.plugin = plugin;
        this.database = database;
        this.config = config;
        this.bookIdKey = new NamespacedKey(plugin, "skill_book_id");
        this.skillIdKey = new NamespacedKey(plugin, "rpg_skill_id");
    }

    private String formatPercent(double fraction) {
        return String.format(java.util.Locale.ROOT, "%.0f%%", fraction * 100);
    }

    @EventHandler(ignoreCancelled = true)
    public void onQuickCastKey(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (mouseDropInteractions.remove(player.getUniqueId()) || !isQuickCastKeyEnabled()) return;
        Inventory topInventory = player.getOpenInventory().getTopInventory();
        if (!(topInventory.getHolder() instanceof QuickCastHolder)
                && player.getOpenInventory().getType() != InventoryType.CRAFTING) {
            return;
        }
        if (!hasActiveRpgProfile(player)) return;
        event.setCancelled(true);
        scheduleQuickCastToggle(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onQuickCastInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (event.getClickedInventory() == null
                && (event.getAction() == InventoryAction.DROP_ALL_CURSOR
                    || event.getAction() == InventoryAction.DROP_ONE_CURSOR)) {
            UUID playerId = player.getUniqueId();
            mouseDropInteractions.add(playerId);
            Bukkit.getScheduler().runTask(plugin, () -> mouseDropInteractions.remove(playerId));
        }

        if (isQuickCastKeyClick(event.getClick())) {
            if (!isQuickCastKeyEnabled()) return;
            if (!hasActiveRpgProfile(player)) return;
            event.setCancelled(true);
            scheduleQuickCastToggle(player);
            return;
        }

        if (!(event.getView().getTopInventory().getHolder() instanceof QuickCastHolder holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        if (event.getClick() == ClickType.NUMBER_KEY) {
            if (holder.screen != QuickCastScreen.QUICK_BAR) return;
            int hotbarButton = event.getHotbarButton();
            if (hotbarButton >= 0 && hotbarButton < 9) {
                castQuickSlot(player, hotbarButton + 1);
            }
            return;
        }

        if (holder.screen == QuickCastScreen.QUICK_BAR) {
            int selectedSlot = quickBarSlot(event.getSlot());
            if (selectedSlot < 1) return;
            String assignedSkill = getQuickSlots(player).get(selectedSlot);
            if (assignedSkill == null) {
                openSkillPicker(player, selectedSlot);
            } else if (event.isRightClick()) {
                clearQuickSlot(player, selectedSlot);
            } else if (event.isLeftClick()) {
                castSkill(player, assignedSkill);
            }
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String skillId = clicked.getItemMeta().getPersistentDataContainer().get(
            skillIdKey, PersistentDataType.STRING);
        if (holder.screen == QuickCastScreen.SKILL_PICKER && skillId != null) {
            assignQuickSlot(player, holder.selectedSlot, skillId);
        } else if (holder.screen == QuickCastScreen.SKILL_TREE && skillId != null) {
            openSkillDetails(player, skillId);
        } else if (holder.screen == QuickCastScreen.SKILL_DETAILS) {
            if (event.getSlot() == 22) {
                openSkillTree(player);
            } else if (event.getSlot() == 13 && event.isLeftClick()) {
                upgradeSkill(player, holder.selectedSkillId);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        cooldowns.remove(playerId);
        battleFocusRanks.remove(playerId);
        fortifyRanks.remove(playerId);
        fortifyStates.remove(playerId);
        mouseDropInteractions.remove(playerId);
        manaFlowRanks.remove(event.getPlayer().getUniqueId());
        manaFlowLastCastAt.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        refreshPassiveRanks(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBattleFocusDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)
                || !(event.getEntity() instanceof Monster)
                || (event.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK
                    && event.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)) {
            return;
        }
        int rank = battleFocusRanks.getOrDefault(player.getUniqueId(), 0);
        if (rank > 0) {
            event.setDamage(calculateBattleFocusDamage(
                event.getDamage(), rank, config.rpgBattleFocusDamageBonusPerRank));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFortifyDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player) || !isHostileMobAttack(event.getDamager())) {
            return;
        }
        int rank = fortifyRanks.getOrDefault(player.getUniqueId(), 0);
        double damage = event.getDamage();
        if (rank <= 0 || !Double.isFinite(damage) || damage <= 0) {
            return;
        }

        long now = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();
        FortifyState currentState = fortifyStates.get(playerId);
        int activeStacks = currentState != null && currentState.expiresAtMillis() > now
            ? currentState.stacks() : 0;
        if (activeStacks > 0) {
            event.setDamage(calculateFortifyDamage(
                damage, rank, activeStacks, config.rpgFortifyDamageReductionPerStackPerRank));
        }
        int nextStacks = Math.min(config.rpgFortifyMaxStacks, activeStacks + 1);
        long expiresAt = now + config.rpgFortifyStackDurationSeconds * 1000L;
        fortifyStates.put(playerId, new FortifyState(nextStacks, expiresAt));
    }

    private boolean isHostileMobAttack(Entity damager) {
        return damager instanceof Monster
            || damager instanceof Projectile projectile && projectile.getShooter() instanceof Monster;
    }

    private void refreshPassiveRanks(Player player) {
        UUID playerId = player.getUniqueId();
        try {
            Optional<PlayerProfileDatabaseManager.PlayerProfile> profile =
                database.getPlayerProfile(playerId.toString());
            if (profile.isEmpty() || !profile.get().active()) {
                battleFocusRanks.remove(playerId);
                fortifyRanks.remove(playerId);
                fortifyStates.remove(playerId);
                manaFlowRanks.remove(playerId);
                manaFlowLastCastAt.remove(playerId);
                return;
            }
            List<PlayerProfileDatabaseManager.SkillProgress> learned =
                database.getLearnedSkills(playerId.toString());
            setCachedPassiveRank(playerId, "battle-focus", "swordsman", profile.get().selectedClass(), learned);
            setCachedPassiveRank(playerId, "fortify", "guardian", profile.get().selectedClass(), learned);
            setCachedPassiveRank(playerId, "mana-flow", "mage", profile.get().selectedClass(), learned);
        } catch (SQLException exception) {
            battleFocusRanks.remove(playerId);
            fortifyRanks.remove(playerId);
            fortifyStates.remove(playerId);
            manaFlowRanks.remove(playerId);
            manaFlowLastCastAt.remove(playerId);
            plugin.getLogger().log(Level.SEVERE, "Could not load RPG passive ranks for "
                + player.getName() + ".", exception);
            player.sendMessage("§cKhông thể tải nội tại RPG. Hãy thử vào lại sau.");
        }
    }

    private void setCachedPassiveRank(UUID playerId, String skillId, String classId,
                                      String selectedClass,
                                      List<PlayerProfileDatabaseManager.SkillProgress> learned) {
        Map<UUID, Integer> cache = switch (skillId) {
            case "battle-focus" -> battleFocusRanks;
            case "fortify" -> fortifyRanks;
            default -> manaFlowRanks;
        };
        if (!classId.equals(selectedClass)) {
            cache.remove(playerId);
            if (skillId.equals("fortify")) fortifyStates.remove(playerId);
            if (skillId.equals("mana-flow")) manaFlowLastCastAt.remove(playerId);
            return;
        }
        int rank = learned.stream().filter(progress -> progress.skillId().equals(skillId))
            .mapToInt(PlayerProfileDatabaseManager.SkillProgress::rank).findFirst().orElse(0);
        if (rank > 0) cache.put(playerId, rank);
        else {
            cache.remove(playerId);
            if (skillId.equals("fortify")) fortifyStates.remove(playerId);
            if (skillId.equals("mana-flow")) manaFlowLastCastAt.remove(playerId);
        }
    }

    static double calculateBattleFocusDamage(double baseDamage, int rank, double bonusPerRank) {
        if (!Double.isFinite(baseDamage) || baseDamage <= 0
                || rank <= 0 || !Double.isFinite(bonusPerRank) || bonusPerRank <= 0) {
            return baseDamage;
        }
        return baseDamage * (1.0 + rank * bonusPerRank);
    }

    static double calculateFortifyDamage(double baseDamage, int rank, int stacks,
                                         double reductionPerStackPerRank) {
        if (!Double.isFinite(baseDamage) || baseDamage <= 0 || rank <= 0 || stacks <= 0
                || !Double.isFinite(reductionPerStackPerRank) || reductionPerStackPerRank <= 0) {
            return baseDamage;
        }
        double reduction = Math.min(0.9, rank * stacks * reductionPerStackPerRank);
        return baseDamage * (1.0 - reduction);
    }

    static int calculateManaFlowRestore(int rank, int manaPerRank) {
        if (rank <= 0 || manaPerRank <= 0) return 0;
        return (int) Math.min(1000L, (long) rank * manaPerRank);
    }

    static boolean isManaFlowChain(long previousCastAt, long currentCastAt, int windowSeconds) {
        return previousCastAt > 0 && currentCastAt >= previousCastAt
            && windowSeconds > 0 && currentCastAt - previousCastAt <= windowSeconds * 1000L;
    }

    private boolean isQuickCastKeyEnabled() {
        return plugin.getConfig().getBoolean("rpg.skills.quick-cast.enabled", true)
            && plugin.getConfig().getBoolean("rpg.skills.quick-cast.intercept-drop-key", true);
    }

    static boolean isQuickCastKeyClick(ClickType click) {
        return click == ClickType.DROP || click == ClickType.CONTROL_DROP;
    }

    private boolean hasActiveRpgProfile(Player player) {
        try {
            Optional<PlayerProfileDatabaseManager.PlayerProfile> profile =
                database.getPlayerProfile(player.getUniqueId().toString());
            return profile.isPresent() && profile.get().active();
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not check RPG profile before opening quick cast for "
                + player.getName() + ".", exception);
            player.sendMessage("§cKhông thể tải hồ sơ RPG; thao tác thả vật phẩm vẫn hoạt động bình thường.");
            return false;
        }
    }

    private void scheduleQuickCastToggle(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof QuickCastHolder) {
                player.closeInventory();
            } else {
                openQuickCast(player);
            }
        });
    }

    private void openQuickCast(Player player) {
        try {
            Optional<PlayerProfileDatabaseManager.PlayerProfile> profile =
                database.getPlayerProfile(player.getUniqueId().toString());
            if (profile.isEmpty() || !profile.get().active()) {
                player.sendMessage("§cBạn cần tạo nhân vật RPG trước khi dùng Quick Cast.");
                return;
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not load RPG profile for Quick Cast: "
                + player.getName() + ".", exception);
            player.sendMessage("§cKhông thể tải hồ sơ RPG. Hãy thử lại sau.");
            return;
        }
        Map<Integer, String> assignedSlots = getQuickSlots(player);
        QuickCastHolder holder = new QuickCastHolder(QuickCastScreen.QUICK_BAR, 0);
        Inventory inventory = Bukkit.createInventory(holder, 27, "§6Quick Cast");
        holder.inventory = inventory;
        Optional<PlayerProfileDatabaseManager.ManaState> loadedMana = getManaState(player);
        if (loadedMana.isEmpty()) return;
        PlayerProfileDatabaseManager.ManaState mana = loadedMana.get();
        PlayerProfileDatabaseManager.PlayerProfile profile;
        try {
            profile = database.getPlayerProfile(player.getUniqueId().toString()).orElseThrow();
        } catch (SQLException | java.util.NoSuchElementException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not load RPG profile for Quick Cast: "
                + player.getName() + ".", exception);
            player.sendMessage("§cKhông thể tải hồ sơ RPG. Hãy thử lại sau.");
            return;
        }
        ItemStack status = new ItemStack(Material.LAPIS_LAZULI);
        ItemMeta statusMeta = status.getItemMeta();
        statusMeta.setDisplayName("§bMana " + mana.currentMana() + "/" + mana.maxMana());
        statusMeta.setLore(List.of("§7Cấp nhân vật: " + profile.level(),
            "§7Skill point: " + profile.unspentSkillPoints(),
            "§eDùng /vnskills tree để nâng kỹ năng."));
        status.setItemMeta(statusMeta);
        inventory.setItem(4, status);
        int[] cells = {10, 11, 12, 13, 14, 15, 16, 19, 20};
        for (int slot = 1; slot <= 9; slot++) {
            String skillId = assignedSlots.get(slot);
            SkillDefinitionLoader.SkillDefinition skill = skillId == null ? null : registry.skills().get(skillId);
            ItemStack icon;
            if (skill == null) {
                icon = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta meta = icon.getItemMeta();
                meta.setDisplayName("§7Slot " + slot + " §8- Trống");
                meta.setLore(List.of("§eNhấp để gán kỹ năng."));
                icon.setItemMeta(meta);
            } else {
                icon = createSkillIcon(skill, "§eSlot " + slot + " §8| §f" + skill.displayName(),
                    List.of("§7Rank " + findSkillRank(player, skill.id()), "§7Hồi chiêu: "
                        + cooldownRemainingSeconds(player, skill.id()) + " giây",
                        "§bMana: " + skill.manaCost(),
                        "§aNhấp trái để cast", "§cNhấp phải để bỏ gán"));
            }
            inventory.setItem(cells[slot - 1], icon);
        }
        player.openInventory(inventory);
    }

    private void openSkillTree(Player player) {
        PlayerProfileDatabaseManager.PlayerProfile profile;
        List<PlayerProfileDatabaseManager.SkillProgress> learned;
        try {
            Optional<PlayerProfileDatabaseManager.PlayerProfile> loaded =
                database.getPlayerProfile(player.getUniqueId().toString());
            if (loaded.isEmpty() || !loaded.get().active()) {
                player.sendMessage("§cBạn cần tạo nhân vật RPG trước khi mở cây kỹ năng.");
                return;
            }
            profile = loaded.get();
            learned = database.getLearnedSkills(player.getUniqueId().toString());
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not load skill tree for "
                + player.getName() + ".", exception);
            player.sendMessage("§cKhông thể tải cây kỹ năng. Hãy thử lại sau.");
            return;
        }
        Map<String, Integer> ranks = new HashMap<>();
        learned.forEach(skill -> ranks.put(skill.skillId(), skill.rank()));
        List<SkillDefinitionLoader.SkillDefinition> classSkills = registry.skills().values().stream()
            .filter(skill -> skill.classId().equals(profile.selectedClass()))
            .sorted(Comparator.comparingInt(SkillDefinitionLoader.SkillDefinition::requiredLevel)
                .thenComparing(SkillDefinitionLoader.SkillDefinition::id))
            .toList();
        QuickCastHolder holder = new QuickCastHolder(QuickCastScreen.SKILL_TREE, 0, null);
        Inventory inventory = Bukkit.createInventory(holder, 54,
            "§6Cây kỹ năng §8| §e" + profile.selectedClass());
        holder.inventory = inventory;
        ItemStack points = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta pointsMeta = points.getItemMeta();
        pointsMeta.setDisplayName("§eSkill point: " + profile.unspentSkillPoints());
        pointsMeta.setLore(List.of("§7Nhấp kỹ năng để xem điều kiện và nâng rank.",
            "§7Skill chưa học cần sách tương ứng."));
        points.setItemMeta(pointsMeta);
        inventory.setItem(49, points);
        for (int index = 0; index < classSkills.size() && index < 45; index++) {
            SkillDefinitionLoader.SkillDefinition skill = classSkills.get(index);
            int rank = ranks.getOrDefault(skill.id(), 0);
            Material iconMaterial = rank == 0 ? Material.GRAY_DYE
                : skill.passive() ? Material.PURPLE_DYE : Material.LIME_DYE;
            ItemStack icon = new ItemStack(iconMaterial);
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName((rank == 0 ? "§7" : "§a") + skill.displayName());
            List<String> lore = new java.util.ArrayList<>();
            lore.add("§7" + skill.description());
            lore.add("§7Rank: " + rank + "/" + skill.maxRank());
            lore.add("§7Cấp yêu cầu học: " + skill.requiredLevel());
            lore.add(skill.id().equals("battle-focus")
                ? "§dNội tại: +" + formatPercent(config.rpgBattleFocusDamageBonusPerRank)
                    + " sát thương đánh thường/rank lên quái"
                : skill.id().equals("fortify")
                    ? "§dNội tại: -" + formatPercent(config.rpgFortifyDamageReductionPerStackPerRank)
                        + " sát thương/stack/rank; tối đa " + config.rpgFortifyMaxStacks + " stack"
                : skill.id().equals("mana-flow")
                    ? "§dNội tại: +" + config.rpgManaFlowManaPerChainCastPerRank
                        + " mana/rank mỗi cast nối chuỗi trong " + config.rpgManaFlowChainWindowSeconds + "s"
                : skill.passive() ? "§dNội tại - hiệu ứng chưa triển khai"
                : "§bMana: " + skill.manaCost() + " §8| §eHồi: " + skill.cooldownSeconds() + "s");
            if (rank == 0) lore.add("§8Sách: " + skill.bookId());
            else if (rank < skill.maxRank()) lore.add("§eNhấp để xem nâng cấp");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(skillIdKey, PersistentDataType.STRING, skill.id());
            icon.setItemMeta(meta);
            inventory.setItem(index, icon);
        }
        player.openInventory(inventory);
    }

    public void openSkillTreeFromNpc(Player player) {
        openSkillTree(player);
    }

    public void giveSkillBookFromNpc(Player player, String bookId) {
        SkillDefinitionLoader.SkillBookDefinition book = registry.books().get(bookId);
        if (book == null) {
            plugin.getLogger().warning("[NPC] Configured skill book '" + bookId + "' does not exist.");
            player.sendMessage("§cSách kỹ năng này hiện không khả dụng.");
            return;
        }
        try {
            boolean learned = database.getLearnedSkills(player.getUniqueId().toString()).stream()
                .anyMatch(progress -> progress.skillId().equals(book.skillId()));
            if (learned) {
                player.sendMessage("§eBạn đã học kỹ năng này rồi.");
                return;
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not check learned skills before NPC book grant for "
                + player.getName() + ".", exception);
            player.sendMessage("§cKhông thể kiểm tra kỹ năng đã học. Hãy thử lại sau.");
            return;
        }
        for (ItemStack item : player.getInventory().getContents()) {
            if (bookId.equals(getBookId(item))) {
                player.sendMessage("§eBạn đã có sách kỹ năng này trong túi đồ.");
                return;
            }
        }
        if (!player.getInventory().addItem(createSkillBook(book)).isEmpty()) {
            player.sendMessage("§cTúi đồ đã đầy; không thể nhận sách kỹ năng.");
            return;
        }
        player.sendMessage("§aBạn nhận được sách kỹ năng §e" + book.displayName() + "§a.");
    }

    private void openSkillDetails(Player player, String skillId) {
        SkillDefinitionLoader.SkillDefinition skill = registry.skills().get(skillId);
        if (skill == null) return;
        PlayerProfileDatabaseManager.PlayerProfile profile;
        int rank;
        try {
            Optional<PlayerProfileDatabaseManager.PlayerProfile> loaded =
                database.getPlayerProfile(player.getUniqueId().toString());
            if (loaded.isEmpty() || !loaded.get().active()) {
                player.sendMessage("§cBạn cần tạo nhân vật RPG trước.");
                return;
            }
            profile = loaded.get();
            rank = database.getLearnedSkills(player.getUniqueId().toString()).stream()
                .filter(progress -> progress.skillId().equals(skillId))
                .mapToInt(PlayerProfileDatabaseManager.SkillProgress::rank).findFirst().orElse(0);
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not load skill details for " + skillId
                + " and " + player.getName() + ".", exception);
            player.sendMessage("§cKhông thể tải chi tiết kỹ năng. Hãy thử lại sau.");
            return;
        }
        QuickCastHolder holder = new QuickCastHolder(QuickCastScreen.SKILL_DETAILS, 0, skillId);
        Inventory inventory = Bukkit.createInventory(holder, 27, "§6Chi tiết: " + skill.displayName());
        holder.inventory = inventory;
        List<String> details = new java.util.ArrayList<>(List.of(
            "§7" + skill.description(),
            "§7Rank hiện tại: " + rank + "/" + skill.maxRank(),
            "§7Skill point còn: " + profile.unspentSkillPoints(),
            "§7Cấp yêu cầu học: " + skill.requiredLevel()));
        if (rank > 0 && rank < skill.maxRank()) {
            details.add("§7Cấp yêu cầu rank kế: " + (skill.requiredLevel() + rank));
        }
        details.add(skill.id().equals("battle-focus")
            ? "§dNội tại: +" + formatPercent(config.rpgBattleFocusDamageBonusPerRank)
                + " sát thương đánh thường/rank lên quái."
            : skill.id().equals("fortify")
                ? "§dNội tại: giảm " + formatPercent(config.rpgFortifyDamageReductionPerStackPerRank)
                    + "/stack/rank; tối đa " + config.rpgFortifyMaxStacks + " stack, reset sau "
                    + config.rpgFortifyStackDurationSeconds + "s không bị quái đánh."
            : skill.id().equals("mana-flow")
                ? "§dNội tại: +" + config.rpgManaFlowManaPerChainCastPerRank
                    + " mana/rank mỗi cast nối chuỗi trong " + config.rpgManaFlowChainWindowSeconds + "s."
            : skill.passive() ? "§dHiệu ứng nội tại chưa được triển khai."
            : "§bMana: " + skill.manaCost() + " §8| §eHồi chiêu: " + skill.cooldownSeconds() + "s");
        ItemStack skillItem = createSkillIcon(skill, "§e" + skill.displayName(), details);
        inventory.setItem(4, skillItem);
        boolean upgradeSupported = !skill.passive() || IMPLEMENTED_PASSIVES.contains(skill.id());
        boolean canUpgrade = rank > 0 && rank < skill.maxRank()
            && profile.unspentSkillPoints() > 0
            && profile.level() >= skill.requiredLevel() + rank && upgradeSupported;
        ItemStack upgrade = new ItemStack(canUpgrade ? Material.EMERALD : Material.BARRIER);
        ItemMeta upgradeMeta = upgrade.getItemMeta();
        upgradeMeta.setDisplayName(canUpgrade ? "§aNâng 1 rank (1 skill point)" : "§cChưa thể nâng");
        if (!canUpgrade) {
            String unavailableReason = rank == 0 ? "§7Học skill bằng sách trước."
                : rank >= skill.maxRank() ? "§7Đã đạt rank tối đa."
                : profile.level() < skill.requiredLevel() + rank
                    ? "§7Cần đạt cấp " + (skill.requiredLevel() + rank) + " để nâng."
                : profile.unspentSkillPoints() < 1 ? "§7Bạn chưa có skill point."
                : "§7Nội tại này chưa hỗ trợ nâng rank.";
            upgradeMeta.setLore(List.of(unavailableReason));
        } else {
            upgradeMeta.setLore(List.of("§7Rank kế tiếp: " + (rank + 1) + "/" + skill.maxRank(),
                skill.id().equals("battle-focus")
                    ? "§7Thêm +" + formatPercent(config.rpgBattleFocusDamageBonusPerRank)
                        + " sát thương đánh thường lên quái."
                    : skill.id().equals("fortify")
                        ? "§7Thêm -" + formatPercent(config.rpgFortifyDamageReductionPerStackPerRank)
                            + " sát thương mỗi stack."
                    : skill.id().equals("mana-flow")
                        ? "§7Thêm +" + config.rpgManaFlowManaPerChainCastPerRank
                            + " mana mỗi cast nối chuỗi."
                    : "§7Cấp nhân vật yêu cầu: " + (skill.requiredLevel() + rank),
                "§eNhấp để tiêu 1 skill point."));
        }
        upgrade.setItemMeta(upgradeMeta);
        inventory.setItem(13, upgrade);
        inventory.setItem(22, new ItemStack(Material.ARROW));
        player.openInventory(inventory);
    }

    private void upgradeSkill(Player player, String skillId) {
        SkillDefinitionLoader.SkillDefinition skill = registry.skills().get(skillId);
        if (skill == null || (skill.passive() && !IMPLEMENTED_PASSIVES.contains(skill.id()))) return;
        try {
            PlayerProfileDatabaseManager.SkillUpgradeResult result = database.upgradeSkill(
                player.getUniqueId().toString(), skillId, skill.classId(),
                skill.requiredLevel(), skill.maxRank());
            switch (result) {
                case UPGRADED -> {
                    if (skill.passive()) {
                        refreshPassiveRanks(player);
                    }
                    player.sendMessage("§aĐã nâng cấp §e" + skill.displayName() + "§a.");
                    openSkillDetails(player, skillId);
                }
                case NO_SKILL_POINTS -> player.sendMessage("§cBạn không còn skill point.");
                case MAX_RANK -> player.sendMessage("§eKỹ năng đã đạt rank tối đa.");
                case SKILL_NOT_LEARNED -> player.sendMessage("§cHãy học skill bằng sách trước.");
                case REQUIREMENTS_NOT_MET -> player.sendMessage("§cChưa đạt cấp yêu cầu cho rank kế tiếp.");
                case PROFILE_NOT_FOUND -> player.sendMessage("§cKhông tìm thấy hồ sơ nhân vật đang hoạt động.");
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not upgrade skill " + skillId
                + " for " + player.getName() + ".", exception);
            player.sendMessage("§cKhông thể nâng kỹ năng. Skill point chưa bị trừ.");
        }
    }

    private Optional<PlayerProfileDatabaseManager.ManaState> getManaState(Player player) {
        try {
            return database.getManaState(player.getUniqueId().toString(), config.rpgMaxMana,
                config.rpgManaRegenerationPerSecond, System.currentTimeMillis());
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not load mana for " + player.getName() + ".", exception);
            player.sendMessage("§cKhông thể tải mana. Hãy thử lại sau.");
            return Optional.empty();
        }
    }

    private void openSkillPicker(Player player, int selectedSlot) {
        List<PlayerProfileDatabaseManager.SkillProgress> learned;
        try {
            learned = database.getLearnedSkills(player.getUniqueId().toString());
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not load learned skills for quick-slot assignment: "
                + player.getName() + ".", exception);
            player.sendMessage("§cKhông thể tải kỹ năng. Hãy thử lại sau.");
            return;
        }
        List<SkillDefinitionLoader.SkillDefinition> available = learned.stream()
            .map(progress -> registry.skills().get(progress.skillId()))
            .filter(skill -> skill != null && !skill.passive() && CAST_HANDLERS.contains(skill.id()))
            .toList();
        if (available.isEmpty()) {
            player.sendMessage("§7Bạn chưa học kỹ năng chủ động nào để gán.");
            openQuickCast(player);
            return;
        }

        QuickCastHolder holder = new QuickCastHolder(QuickCastScreen.SKILL_PICKER, selectedSlot);
        Inventory inventory = Bukkit.createInventory(holder, 54, "§6Gán skill vào slot " + selectedSlot);
        holder.inventory = inventory;
        int index = 0;
        for (SkillDefinitionLoader.SkillDefinition skill : available) {
            ItemStack icon = createSkillIcon(skill, "§e" + skill.displayName(),
                List.of("§7" + skill.description(), "§7Cooldown: " + skill.cooldownSeconds() + " giây",
                    "§bMana: " + skill.manaCost(),
                    "§aNhấp để gán vào slot " + selectedSlot));
            ItemMeta meta = icon.getItemMeta();
            meta.getPersistentDataContainer().set(skillIdKey, PersistentDataType.STRING, skill.id());
            icon.setItemMeta(meta);
            inventory.setItem(index++, icon);
        }
        player.openInventory(inventory);
    }

    private ItemStack createSkillIcon(SkillDefinitionLoader.SkillDefinition skill, String title,
                                      List<String> details) {
        ItemStack icon = new ItemStack(skill.icon());
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(title);
        meta.setLore(details);
        icon.setItemMeta(meta);
        return icon;
    }

    private static int quickBarSlot(int inventorySlot) {
        return switch (inventorySlot) {
            case 10 -> 1;
            case 11 -> 2;
            case 12 -> 3;
            case 13 -> 4;
            case 14 -> 5;
            case 15 -> 6;
            case 16 -> 7;
            case 19 -> 8;
            case 20 -> 9;
            default -> -1;
        };
    }

    private Map<Integer, String> getQuickSlots(Player player) {
        try {
            return database.getQuickSkillSlots(player.getUniqueId().toString());
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not load quick skill slots for "
                + player.getName() + ".", exception);
            player.sendMessage("§cKhông thể tải quick slots. Hãy thử lại sau.");
            return Map.of();
        }
    }

    private void assignQuickSlot(Player player, int slot, String skillId) {
        SkillDefinitionLoader.SkillDefinition skill = registry.skills().get(skillId);
        if (skill == null || slot < 1 || slot > 9) return;
        if (skill.passive() || !CAST_HANDLERS.contains(skillId)) {
            player.sendMessage("§cKỹ năng này chưa hỗ trợ quick cast.");
            return;
        }
        try {
            PlayerProfileDatabaseManager.QuickSlotResult result = database.assignQuickSkill(
                player.getUniqueId().toString(), slot, skill.id(), skill.classId());
            switch (result) {
                case ASSIGNED -> {
                    player.sendMessage("§aĐã gán §e" + skill.displayName() + "§a vào slot " + slot + ".");
                    openQuickCast(player);
                }
                case ALREADY_ASSIGNED ->
                    player.sendMessage("§eKỹ năng này đã được gán vào một slot khác.");
                case SKILL_NOT_LEARNED ->
                    player.sendMessage("§cBạn chưa học kỹ năng này.");
                case REQUIREMENTS_NOT_MET ->
                    player.sendMessage("§cKỹ năng không dành cho class hiện tại.");
                case PROFILE_NOT_FOUND ->
                    player.sendMessage("§cBạn cần tạo nhân vật RPG trước khi gán kỹ năng.");
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not assign skill " + skillId + " to slot "
                + slot + " for " + player.getName() + ".", exception);
            player.sendMessage("§cKhông thể lưu quick slot. Hãy thử lại sau.");
        }
    }

    private void clearQuickSlot(Player player, int slot) {
        try {
            if (database.clearQuickSkillSlot(player.getUniqueId().toString(), slot)) {
                player.sendMessage("§aĐã bỏ gán kỹ năng ở slot " + slot + ".");
            }
            openQuickCast(player);
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not clear quick slot " + slot
                + " for " + player.getName() + ".", exception);
            player.sendMessage("§cKhông thể lưu thay đổi quick slot.");
        }
    }

    private void castQuickSlot(Player player, int slot) {
        String skillId = getQuickSlots(player).get(slot);
        if (skillId == null) {
            player.sendMessage("§7Slot " + slot + " chưa được gán kỹ năng.");
            return;
        }
        castSkill(player, skillId);
    }

    private void castSkill(Player player, String skillId) {
        SkillDefinitionLoader.SkillDefinition skill = registry.skills().get(skillId);
        if (skill == null || skill.passive() || !CAST_HANDLERS.contains(skillId)) {
            player.sendMessage("§cKỹ năng này không thể cast.");
            return;
        }
        Optional<PlayerProfileDatabaseManager.PlayerProfile> profile;
        List<PlayerProfileDatabaseManager.SkillProgress> learned;
        try {
            profile = database.getPlayerProfile(player.getUniqueId().toString());
            learned = database.getLearnedSkills(player.getUniqueId().toString());
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not validate skill cast " + skillId
                + " for " + player.getName() + ".", exception);
            player.sendMessage("§cKhông thể xác minh kỹ năng. Hãy thử lại sau.");
            return;
        }
        if (profile.isEmpty() || !profile.get().active()
                || !skill.classId().equals(profile.get().selectedClass())) {
            player.sendMessage("§cKỹ năng không dành cho class hiện tại.");
            return;
        }
        int rank = learned.stream().filter(progress -> progress.skillId().equals(skillId))
            .mapToInt(PlayerProfileDatabaseManager.SkillProgress::rank).findFirst().orElse(0);
        if (rank < 1) {
            player.sendMessage("§cBạn chưa học kỹ năng này.");
            return;
        }
        long remaining = cooldownRemainingMillis(player, skillId);
        if (remaining > 0) {
            player.sendMessage("§e" + skill.displayName() + " hồi chiêu còn "
                + ((remaining + 999) / 1000) + " giây.");
            return;
        }
        if (!hasValidCastTarget(player, skill)) {
            player.sendMessage("§eKhông có mục tiêu hợp lệ trong phạm vi kỹ năng.");
            return;
        }
        try {
            PlayerProfileDatabaseManager.ManaSpendResult manaResult = database.spendMana(
                player.getUniqueId().toString(), skill.manaCost(), config.rpgMaxMana,
                config.rpgManaRegenerationPerSecond, System.currentTimeMillis());
            if (manaResult == PlayerProfileDatabaseManager.ManaSpendResult.PROFILE_NOT_FOUND) {
                player.sendMessage("§cKhông tìm thấy hồ sơ nhân vật đang hoạt động.");
                return;
            }
            if (manaResult == PlayerProfileDatabaseManager.ManaSpendResult.NOT_ENOUGH_MANA) {
                Optional<PlayerProfileDatabaseManager.ManaState> current = getManaState(player);
                player.sendMessage("§cKhông đủ mana"
                    + (current.isEmpty() ? "." : " (" + current.get().currentMana()
                        + "/" + current.get().maxMana() + ")."));
                return;
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not spend mana for skill " + skillId
                + " cast by " + player.getName() + ".", exception);
            player.sendMessage("§cKhông thể dùng mana. Kỹ năng chưa được cast.");
            return;
        }
        boolean castSucceeded = applySkillEffect(player, skill, rank);
        long castAt = System.currentTimeMillis();
        int manaFlowRank = manaFlowRanks.getOrDefault(player.getUniqueId(), 0);
        long previousManaFlowCast = manaFlowLastCastAt.getOrDefault(player.getUniqueId(), 0L);
        boolean manaFlowChain = castSucceeded && manaFlowRank > 0
            && isManaFlowChain(previousManaFlowCast, castAt, config.rpgManaFlowChainWindowSeconds);
        if (castSucceeded) {
            manaFlowLastCastAt.put(player.getUniqueId(), castAt);
        }
        if (manaFlowChain) {
            restoreManaFlow(player, manaFlowRank);
        }
        cooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
            .put(skillId, castAt + skill.cooldownSeconds() * 1000L);
        player.sendMessage("§aĐã cast §e" + skill.displayName() + "§a §8| §b-" + skill.manaCost() + " mana");
    }

    private void restoreManaFlow(Player player, int rank) {
        int restoreAmount = calculateManaFlowRestore(rank, config.rpgManaFlowManaPerChainCastPerRank);
        if (restoreAmount == 0) return;
        try {
            Optional<PlayerProfileDatabaseManager.ManaRestoreResult> result = database.restoreMana(
                player.getUniqueId().toString(), restoreAmount, config.rpgMaxMana,
                config.rpgManaRegenerationPerSecond, System.currentTimeMillis());
            if (result.isEmpty()) {
                plugin.getLogger().warning("Mana Flow could not restore mana because the active profile "
                    + "was not found for " + player.getName() + ".");
                player.sendMessage("§cCast thành công nhưng Mạch Pháp không tìm thấy hồ sơ để hồi mana.");
                return;
            }
            int restored = result.get().restoredAmount();
            if (restored > 0) {
                player.sendMessage("§dMạch Pháp: +" + restored + " mana.");
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not restore Mana Flow mana for "
                + player.getName() + ".", exception);
            player.sendMessage("§cCast thành công nhưng Mạch Pháp không thể hồi mana lần này.");
        }
    }

    private boolean hasValidCastTarget(Player player, SkillDefinitionLoader.SkillDefinition skill) {
        return switch (skill.id()) {
            case "blade-flare" -> findRayTarget(player, 5.0, 0.8) != null;
            case "guard-break" -> findRayTarget(player, 4.0, 0.6) != null;
            case "shield-wall" -> true;
            case "taunt" -> player.getNearbyEntities(8, 5, 8).stream().anyMatch(Monster.class::isInstance);
            case "fire-bolt" -> findRayTarget(player, 20.0, 0.4) != null;
            case "arcane-field" -> !getArcaneFieldTargets(player).isEmpty();
            default -> false;
        };
    }

    private boolean applySkillEffect(Player player, SkillDefinitionLoader.SkillDefinition skill, int rank) {
        return switch (skill.id()) {
            case "blade-flare" -> damageRayTarget(player, 5.0, 3.0 + rank, 0.8, false);
            case "guard-break" -> damageRayTarget(player, 4.0, 4.0 + rank,
                0.6, true);
            case "shield-wall" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                    80 + rank * 10, 0, true, true, true));
                player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0, 1, 0),
                    24, 0.7, 0.8, 0.7, 0.6);
                player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.8f, 1.2f);
                yield true;
            }
            case "taunt" -> {
                List<Monster> targets = player.getNearbyEntities(8, 5, 8).stream()
                    .filter(Monster.class::isInstance).map(Monster.class::cast).toList();
                targets.forEach(target -> target.setTarget(player));
                if (!targets.isEmpty()) {
                    player.getWorld().spawnParticle(Particle.ANGRY_VILLAGER,
                        player.getLocation().add(0, 1, 0), 12, 1.2, 0.8, 1.2, 0);
                    player.playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_HURT, 0.7f, 1.4f);
                }
                yield !targets.isEmpty();
            }
            case "fire-bolt" -> damageRayTarget(player, 20.0, 4.0 + rank,
                0.4, false, 40);
            case "arcane-field" -> castArcaneField(player, rank);
            default -> false;
        };
    }

    private boolean damageRayTarget(Player player, double range, double damage, double raySize,
                                   boolean weaken) {
        return damageRayTarget(player, range, damage, raySize, weaken, 0);
    }

    private boolean damageRayTarget(Player player, double range, double damage, double raySize,
                                    boolean weaken, int fireTicks) {
        Monster target = findRayTarget(player, range, raySize);
        if (target == null) return false;
        target.damage(damage, player);
        if (weaken) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0, true, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 1, true, true, true));
        }
        if (fireTicks > 0) target.setFireTicks(Math.max(target.getFireTicks(), fireTicks));
        player.getWorld().spawnParticle(fireTicks > 0 ? Particle.FLAME : Particle.SWEEP_ATTACK,
            target.getLocation().add(0, 1, 0), 12, 0.3, 0.4, 0.3, 0.02);
        player.playSound(player.getLocation(), fireTicks > 0
            ? Sound.ITEM_FIRECHARGE_USE : Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.1f);
        return true;
    }

    private Monster findRayTarget(Player player, double range, double raySize) {
        org.bukkit.Location eyeLocation = player.getEyeLocation();
        Vector direction = eyeLocation.getDirection();
        RayTraceResult entityHit = player.getWorld().rayTraceEntities(eyeLocation, direction,
            range, raySize, Monster.class::isInstance);
        if (entityHit == null || !(entityHit.getHitEntity() instanceof Monster target)) return null;
        RayTraceResult blockHit = player.getWorld().rayTraceBlocks(eyeLocation, direction, range);
        if (blockHit != null && blockHit.getHitPosition().distanceSquared(eyeLocation.toVector())
                < entityHit.getHitPosition().distanceSquared(eyeLocation.toVector())) {
            return null;
        }
        return target;
    }

    private boolean castArcaneField(Player player, int rank) {
        org.bukkit.Location center = getArcaneFieldCenter(player);
        List<LivingEntity> targets = getArcaneFieldTargets(player);
        if (targets.isEmpty()) return false;
        for (LivingEntity target : targets) {
            target.damage(1.5 + rank, player);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, true, true, true));
        }
        player.getWorld().spawnParticle(Particle.PORTAL, center, 50, 1.5, 0.5, 1.5, 0.2);
        player.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.6f);
        return true;
    }

    private List<LivingEntity> getArcaneFieldTargets(Player player) {
        return player.getWorld().getNearbyEntities(getArcaneFieldCenter(player), 3.0, 2.0, 3.0).stream()
            .filter(Monster.class::isInstance).map(LivingEntity.class::cast).toList();
    }

    private org.bukkit.Location getArcaneFieldCenter(Player player) {
        org.bukkit.Location eyeLocation = player.getEyeLocation();
        Vector direction = eyeLocation.getDirection();
        RayTraceResult blockHit = player.getWorld().rayTraceBlocks(eyeLocation, direction, 12.0);
        return blockHit == null ? eyeLocation.add(direction.multiply(12))
            : blockHit.getHitPosition().toLocation(player.getWorld());
    }

    private int findSkillRank(Player player, String skillId) {
        try {
            return database.getLearnedSkills(player.getUniqueId().toString()).stream()
                .filter(progress -> progress.skillId().equals(skillId))
                .mapToInt(PlayerProfileDatabaseManager.SkillProgress::rank).findFirst().orElse(0);
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not read rank for skill " + skillId
                + " for " + player.getName() + ".", exception);
            return 0;
        }
    }

    private long cooldownRemainingMillis(Player player, String skillId) {
        long expiresAt = cooldowns.getOrDefault(player.getUniqueId(), Map.of())
            .getOrDefault(skillId, 0L);
        return Math.max(0L, expiresAt - System.currentTimeMillis());
    }

    private int cooldownRemainingSeconds(Player player, String skillId) {
        long remaining = cooldownRemainingMillis(player, skillId);
        return (int) ((remaining + 999) / 1000);
    }

    public void initialize(Set<String> validClassIds) throws IOException {
        File skillDirectory = new File(plugin.getDataFolder(), "skills");
        Files.createDirectories(skillDirectory.toPath());
        saveBundledIfMissing("skills/skill-definitions.yml");
        saveBundledIfMissing("skills/skill-books.yml");

        Optional<SkillDefinitionLoader.SkillRegistry> loaded = SkillDefinitionLoader.load(
            new File(skillDirectory, "skill-definitions.yml"),
            new File(skillDirectory, "skill-books.yml"),
            validClassIds,
            plugin.getLogger());
        if (loaded.isEmpty()) {
            throw new IOException("Skill definitions or skill books are invalid; see the log for details.");
        }
        registry = loaded.get();
        Bukkit.getOnlinePlayers().forEach(this::refreshPassiveRanks);
    }

    private void saveBundledIfMissing(String resourcePath) throws IOException {
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.isFile()) {
            try {
                plugin.saveResource(resourcePath, false);
            } catch (IllegalArgumentException exception) {
                throw new IOException("Bundled skill resource is missing: " + resourcePath, exception);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSkillBookUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        ItemStack item = event.getItem();
        String bookId = getBookId(item);
        if (bookId == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        SkillDefinitionLoader.SkillBookDefinition book = registry.books().get(bookId);
        if (book == null) {
            player.sendMessage("§cSách kỹ năng này không còn được máy chủ hỗ trợ.");
            return;
        }
        SkillDefinitionLoader.SkillDefinition skill = registry.skills().get(book.skillId());
        Optional<PlayerProfileDatabaseManager.PlayerProfile> profile;
        try {
            profile = database.getPlayerProfile(player.getUniqueId().toString());
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not load RPG profile while learning skill book "
                + bookId + " for " + player.getName() + ".", exception);
            player.sendMessage("§cKhông thể đọc hồ sơ nhân vật. Sách chưa bị tiêu thụ; hãy thử lại sau.");
            return;
        }
        if (profile.isEmpty() || !profile.get().active()) {
            player.sendMessage("§cBạn cần tạo nhân vật RPG trước khi học kỹ năng.");
            return;
        }
        if (!skill.classId().equals(profile.get().selectedClass())) {
            player.sendMessage("§cKỹ năng này chỉ dành cho class §e" + skill.classId() + "§c. Sách chưa bị tiêu thụ.");
            return;
        }
        if (profile.get().level() < skill.requiredLevel()) {
            player.sendMessage("§cCần đạt cấp " + skill.requiredLevel() + " để học kỹ năng này. Sách chưa bị tiêu thụ.");
            return;
        }

        try {
            PlayerProfileDatabaseManager.SkillLearnResult result = database.learnSkill(
                player.getUniqueId().toString(), skill.id(), skill.classId(), skill.requiredLevel(),
                Instant.now().toString());
            switch (result) {
                case LEARNED -> {
                    consumeOneFromMainHand(player);
                    if (skill.passive()) {
                        if (skill.id().equals("battle-focus")) {
                            battleFocusRanks.put(player.getUniqueId(), 1);
                        } else if (skill.id().equals("fortify")) {
                            fortifyRanks.put(player.getUniqueId(), 1);
                        } else if (skill.id().equals("mana-flow")) {
                            manaFlowRanks.put(player.getUniqueId(), 1);
                        }
                    }
                    player.sendMessage("§aBạn đã học kỹ năng §e" + skill.displayName() + "§a (Rank 1).");
                }
                case ALREADY_LEARNED ->
                    player.sendMessage("§eBạn đã học kỹ năng này rồi. Sách chưa bị tiêu thụ.");
                case REQUIREMENTS_NOT_MET ->
                    player.sendMessage("§cClass hoặc cấp của nhân vật không đáp ứng điều kiện. Sách chưa bị tiêu thụ.");
                case PROFILE_NOT_FOUND ->
                    player.sendMessage("§cKhông tìm thấy hồ sơ nhân vật đang hoạt động. Sách chưa bị tiêu thụ.");
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save learned skill " + skill.id()
                + " for " + player.getName() + ".", exception);
            player.sendMessage("§cKhông thể lưu kỹ năng. Sách chưa bị tiêu thụ; hãy thử lại sau.");
        }
    }

    private String getBookId(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(bookIdKey, PersistentDataType.STRING);
    }

    private void consumeOneFromMainHand(Player player) {
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        } else {
            heldItem.setAmount(heldItem.getAmount() - 1);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("vnskills")) {
            return handleSkillsCommand(sender, args);
        }
        if (command.getName().equalsIgnoreCase("vnskillbook")) {
            return handleSkillBookCommand(sender, args);
        }
        return false;
    }

    private boolean handleSkillsCommand(CommandSender sender, String[] args) {
        if (args.length > 1 || (args.length == 1
                && !args[0].equalsIgnoreCase("list") && !args[0].equalsIgnoreCase("tree"))) {
            sender.sendMessage("§eDùng: /vnskills [list|tree]");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Lệnh này chỉ dành cho người chơi.");
            return true;
        }
        if (args.length == 0) {
            openQuickCast(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("tree")) {
            openSkillTree(player);
            return true;
        }
        try {
            Optional<PlayerProfileDatabaseManager.PlayerProfile> profile =
                database.getPlayerProfile(player.getUniqueId().toString());
            if (profile.isEmpty() || !profile.get().active()) {
                player.sendMessage("§cBạn cần tạo nhân vật RPG trước.");
                return true;
            }
            List<PlayerProfileDatabaseManager.SkillProgress> learned =
                database.getLearnedSkills(player.getUniqueId().toString());
            player.sendMessage("§6§lKỹ năng nhân vật §7| §e" + profile.get().selectedClass()
                + " §7| Cấp " + profile.get().level());
            if (learned.isEmpty()) {
                player.sendMessage("§7Chưa học kỹ năng nào. Dùng sách kỹ năng phù hợp với class của bạn.");
                return true;
            }
            for (PlayerProfileDatabaseManager.SkillProgress progress : learned) {
                SkillDefinitionLoader.SkillDefinition skill = registry.skills().get(progress.skillId());
                String name = skill == null ? progress.skillId() : skill.displayName();
                player.sendMessage("§a• §f" + name + " §7- Rank " + progress.rank());
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not list learned skills for "
                + player.getName() + ".", exception);
            player.sendMessage("§cKhông thể tải danh sách kỹ năng. Hãy thử lại sau.");
        }
        return true;
    }

    private boolean handleSkillBookCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cBạn không có quyền dùng lệnh này.");
            return true;
        }
        if (args.length != 3 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage("§eDùng: /vnskillbook give <người_chơi> <book-id>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§cNgười chơi phải đang trực tuyến.");
            return true;
        }
        SkillDefinitionLoader.SkillBookDefinition book = registry.books().get(args[2]);
        if (book == null) {
            sender.sendMessage("§cKhông tìm thấy book-id: " + args[2]);
            return true;
        }
        ItemStack item = createSkillBook(book);
        Map<Integer, ItemStack> overflow = target.getInventory().addItem(item);
        if (!overflow.isEmpty()) {
            sender.sendMessage("§cTúi đồ người chơi đã đầy; không có sách nào được cấp.");
            return true;
        }
        sender.sendMessage("§aĐã cấp sách §e" + book.id() + "§a cho §f" + target.getName() + "§a.");
        target.sendMessage("§aBạn nhận được sách kỹ năng §e" + book.displayName() + "§a.");
        return true;
    }

    private ItemStack createSkillBook(SkillDefinitionLoader.SkillBookDefinition book) {
        SkillDefinitionLoader.SkillDefinition skill = registry.skills().get(book.skillId());
        ItemStack item = new ItemStack(book.material());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6" + book.displayName());
        List<String> lore = book.lore().stream().map(line -> "§7" + line).toList();
        lore = new java.util.ArrayList<>(lore);
        lore.add("§8Độ hiếm: §d" + book.rarity());
        lore.add("§8Kỹ năng: §e" + skill.displayName());
        lore.add("§8Class: §b" + skill.classId() + " §7| Cấp yêu cầu: §e" + skill.requiredLevel());
        lore.add("§8Nhấp chuột phải để học");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(bookIdKey, PersistentDataType.STRING, book.id());
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("vnskills")) {
            if (args.length != 1) return List.of();
            String prefix = args[0].toLowerCase(java.util.Locale.ROOT);
            return List.of("list", "tree").stream().filter(value -> value.startsWith(prefix)).toList();
        }
        if (!sender.hasPermission(ADMIN_PERMISSION)) return List.of();
        if (args.length == 1) return List.of("give");
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            String prefix = args[1].toLowerCase(java.util.Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(name -> name.toLowerCase(java.util.Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            String prefix = args[2].toLowerCase(java.util.Locale.ROOT);
            return registry.books().keySet().stream()
                .filter(id -> id.startsWith(prefix)).sorted(Comparator.naturalOrder()).toList();
        }
        return List.of();
    }

    private record FortifyState(int stacks, long expiresAtMillis) {
    }

    private enum QuickCastScreen {
        QUICK_BAR,
        SKILL_PICKER,
        SKILL_TREE,
        SKILL_DETAILS
    }

    private static final class QuickCastHolder implements InventoryHolder {
        private final QuickCastScreen screen;
        private final int selectedSlot;
        private final String selectedSkillId;
        private Inventory inventory;

        private QuickCastHolder(QuickCastScreen screen, int selectedSlot) {
            this(screen, selectedSlot, null);
        }

        private QuickCastHolder(QuickCastScreen screen, int selectedSlot, String selectedSkillId) {
            this.screen = screen;
            this.selectedSlot = selectedSlot;
            this.selectedSkillId = selectedSkillId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
