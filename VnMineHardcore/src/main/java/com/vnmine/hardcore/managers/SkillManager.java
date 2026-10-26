package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.bukkit.scheduler.BukkitRunnable;

public final class SkillManager implements Listener, CommandExecutor, TabCompleter {
    private static final String ADMIN_PERMISSION = "vnmine.command.skillbook.admin";
    private static final Set<String> CAST_HANDLERS = Set.of(
        "blade-flare", "guard-break", "shield-wall", "taunt", "fire-bolt", "arcane-field");
    private static final Set<String> IMPLEMENTED_PASSIVES = Set.of("battle-focus", "fortify", "mana-flow");

    private final VnMineHardcore plugin;
    private final PlayerProfileDatabaseManager database;
    private final ConfigManager config;
    private final NamespacedKey bookIdKey;
    private final NamespacedKey skillIdKey;
    private final NamespacedKey rpgSellValueKey;
    private final NamespacedKey classVisitedNpcsKey;
    private final NamespacedKey questItemIdKey;
    private SkillDefinitionLoader.SkillRegistry registry;
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Integer> battleFocusRanks = new HashMap<>();
    private final Map<UUID, Integer> fortifyRanks = new HashMap<>();
    private final Map<UUID, FortifyState> fortifyStates = new HashMap<>();
    private final Map<UUID, Integer> manaFlowRanks = new HashMap<>();
    private final Map<UUID, Long> manaFlowLastCastAt = new HashMap<>();
    private final Set<UUID> mouseDropInteractions = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingSkillCasts = new java.util.HashSet<>();
    private final Set<UUID> pendingBookUses = new java.util.HashSet<>();
    private final Set<UUID> pendingSkillUpgrades = new java.util.HashSet<>();

    public SkillManager(VnMineHardcore plugin, PlayerProfileDatabaseManager database, ConfigManager config) {
        this.plugin = plugin;
        this.database = database;
        this.config = config;
        this.bookIdKey = new NamespacedKey(plugin, "skill_book_id");
        this.skillIdKey = new NamespacedKey(plugin, "rpg_skill_id");
        this.rpgSellValueKey = new NamespacedKey(plugin, "rpg_sell_value");
        this.classVisitedNpcsKey = new NamespacedKey(plugin, "class_visited_npcs");
        this.questItemIdKey = new NamespacedKey(plugin, "quest_item_id");
    }

    private String formatPercent(double fraction) {
        return String.format(java.util.Locale.ROOT, "%.0f%%", fraction * 100);
    }

    @EventHandler(ignoreCancelled = true)
    public void onQuickCastKey(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (pendingBookUses.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuickCastInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (pendingBookUses.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

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

        if (event.getView().getTopInventory().getHolder() instanceof PromotionHolder promotionHolder) {
            event.setCancelled(true);
            if (!promotionHolder.viewer.equals(player.getUniqueId())
                    || event.getClickedInventory() != event.getView().getTopInventory()) return;
            if (promotionHolder.history) {
                if (event.getSlot() == 53) openClassPromotionMenu(player);
            } else if (promotionHolder.confirmation) {
                if (event.getSlot() == 11) confirmClassPromotion(player, promotionHolder.sourceClassId,
                    promotionHolder.targetClassId);
                else if (event.getSlot() == 15) openClassPromotionMenu(player);
            } else {
                String targetId = promotionHolder.choices.get(event.getSlot());
                if (targetId != null) openClassPromotionConfirmation(player,
                    promotionHolder.sourceClassId, targetId);
                else if (event.getSlot() == 49) openClassHistory(player);
                else if (event.getSlot() == 53) openSkillTree(player);
            }
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof QuickCastHolder holder)) return;
        event.setCancelled(true);

        if (event.getClick() == ClickType.NUMBER_KEY) {
            if (holder.screen != QuickCastScreen.QUICK_BAR) return;
            int hotbarButton = event.getHotbarButton();
            try {
                if (hotbarButton >= 0 && hotbarButton < 9) {
                    castQuickSlot(player, hotbarButton + 1);
                }
            } finally {
                player.closeInventory();
            }
            return;
        }

        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        if (holder.screen == QuickCastScreen.SKILL_TREE && event.getSlot() == 53) {
            VnMineHardcore instance = VnMineHardcore.getInstance();
            if (instance != null && instance.getMercenaryManager() != null) {
                instance.getMercenaryManager().openMainMenu(player);
            }
            return;
        }
        if (holder.screen == QuickCastScreen.SKILL_TREE && event.getSlot() == 45) {
            openClassPromotionMenu(player);
            return;
        }

        if (holder.screen == QuickCastScreen.QUICK_BAR) {
            int selectedSlot = quickBarSlot(event.getSlot());
            if (selectedSlot < 1) return;
            String assignedSkill = holder.assignedSlots.get(selectedSlot);
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
        database.executeGameplayDatabaseTask("skill.passive-ranks player=" + player.getName(), () -> {
            Optional<PlayerProfileDatabaseManager.PlayerProfile> profile =
                database.getPlayerProfile(playerId.toString());
            if (profile.isEmpty() || !profile.get().active()) return null;
            return new PassiveRankSnapshot(profile.get().selectedClass(),
                database.getLearnedSkills(playerId.toString()));
        }).whenComplete((snapshot, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            battleFocusRanks.remove(playerId);
            fortifyRanks.remove(playerId);
            fortifyStates.remove(playerId);
            manaFlowRanks.remove(playerId);
            manaFlowLastCastAt.remove(playerId);
            if (failure != null) {
                plugin.getLogger().log(Level.SEVERE, "Could not load RPG passive ranks for "
                    + player.getName() + ".", failure);
                if (player.isOnline()) player.sendMessage("§cKhông thể tải nội tại RPG. Hãy thử vào lại sau.");
                return;
            }
            if (snapshot == null) return;
            setCachedPassiveRank(playerId, "battle-focus", "swordsman", snapshot.selectedClass(), snapshot.learned());
            setCachedPassiveRank(playerId, "fortify", "guardian", snapshot.selectedClass(), snapshot.learned());
            setCachedPassiveRank(playerId, "mana-flow", "mage", snapshot.selectedClass(), snapshot.learned());
        }));
    }

    private record PassiveRankSnapshot(String selectedClass,
                                       List<PlayerProfileDatabaseManager.SkillProgress> learned) {
    }

    private void setCachedPassiveRank(UUID playerId, String skillId, String classId,
                                      String selectedClass,
                                      List<PlayerProfileDatabaseManager.SkillProgress> learned) {
        Map<UUID, Integer> cache = switch (skillId) {
            case "battle-focus" -> battleFocusRanks;
            case "fortify" -> fortifyRanks;
            default -> manaFlowRanks;
        };
        if (!inheritedClasses(selectedClass).contains(classId)) {
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
        return plugin.getCharacterSelectionManager() != null
            && plugin.getCharacterSelectionManager().getLoadedProfile(player.getUniqueId())
                .filter(PlayerProfileDatabaseManager.PlayerProfile::active).isPresent();
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
        UUID playerId = player.getUniqueId();
        debugFeature("[RPG-DEBUG][SkillUI] quick-cast-open player=" + player.getName());
        database.executeGameplayDatabaseTask("skill.quick-cast-menu player=" + player.getName(), () -> {
            Optional<PlayerProfileDatabaseManager.PlayerProfile> profile =
                database.getPlayerProfile(playerId.toString());
            if (profile.isEmpty() || !profile.get().active()) return null;
            Map<Integer, String> slots = database.getQuickSkillSlots(playerId.toString());
            Map<String, Integer> ranks = new HashMap<>();
            for (PlayerProfileDatabaseManager.SkillProgress progress
                    : database.getLearnedSkills(playerId.toString())) {
                ranks.put(progress.skillId(), progress.rank());
            }
            return new QuickCastData(slots, ranks);
        }).whenComplete((data, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (failure != null) {
                plugin.getLogger().log(Level.SEVERE, "Could not load RPG data for Quick Cast: "
                    + player.getName() + ".", failure);
                player.sendMessage("§cKhông thể tải hồ sơ RPG. Hãy thử lại sau.");
                return;
            }
            debugFeature("[RPG-DEBUG][SkillUI] quick-cast-data player=" + player.getName()
                + ", loaded=" + (data != null)
                + (data == null ? "" : ", assignedSlots=" + data.slots().size()
                    + ", learnedSkills=" + data.ranks().size()));
            if (data == null) {
                player.sendMessage("§cBạn cần tạo nhân vật RPG trước khi dùng Quick Cast.");
                return;
            }
            renderQuickCast(player, data);
        }));
    }

    private record QuickCastData(Map<Integer, String> slots, Map<String, Integer> ranks) {
    }

    private void renderQuickCast(Player player, QuickCastData data) {
        QuickCastHolder holder = new QuickCastHolder(QuickCastScreen.QUICK_BAR, 0);
        holder.assignedSlots = data.slots();
        Inventory inventory = Bukkit.createInventory(holder, 9, "§6Quick Cast");
        holder.inventory = inventory;
        for (int slot = 1; slot <= 9; slot++) {
            String skillId = data.slots().get(slot);
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
                    List.of("§7Rank " + data.ranks().getOrDefault(skill.id(), 0), "§7Hồi chiêu: "
                        + cooldownRemainingSeconds(player, skill.id()) + " giây",
                        "§bMana: " + skill.manaCost(),
                        "§aNhấp trái để cast", "§cNhấp phải để bỏ gán"));
            }
            inventory.setItem(slot - 1, icon);
        }
        player.openInventory(inventory);
    }

    private void openSkillTree(Player player) {
        UUID playerId = player.getUniqueId();
        debugFeature("[RPG-DEBUG][SkillUI] tree-open player=" + player.getName());
        database.executeGameplayDatabaseTask("skill.tree-menu player=" + player.getName(), () -> {
            Optional<PlayerProfileDatabaseManager.PlayerProfile> loaded =
                database.getPlayerProfile(playerId.toString());
            if (loaded.isEmpty() || !loaded.get().active()) return null;
            return new ProfileSkills(loaded.get(), database.getLearnedSkills(playerId.toString()));
        }).whenComplete((data, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (failure != null) {
                plugin.getLogger().log(Level.SEVERE, "Could not load skill tree for "
                    + player.getName() + ".", failure);
                player.sendMessage("§cKhông thể tải cây kỹ năng. Hãy thử lại sau.");
            } else if (data == null) {
                debugFeature("[RPG-DEBUG][SkillUI] tree-data player=" + player.getName() + ", loaded=false");
                player.sendMessage("§cBạn cần tạo nhân vật RPG trước khi mở cây kỹ năng.");
            } else {
                debugFeature("[RPG-DEBUG][SkillUI] tree-data player=" + player.getName()
                    + ", loaded=true, class=" + data.profile().selectedClass()
                    + ", learnedSkills=" + data.learned().size());
                renderSkillTree(player, data.profile(), data.learned());
            }
        }));
    }

    private record ProfileSkills(PlayerProfileDatabaseManager.PlayerProfile profile,
                                 List<PlayerProfileDatabaseManager.SkillProgress> learned) {
    }

    private void renderSkillTree(Player player, PlayerProfileDatabaseManager.PlayerProfile profile,
                                 List<PlayerProfileDatabaseManager.SkillProgress> learned) {
        Map<String, Integer> ranks = new HashMap<>();
        learned.forEach(skill -> ranks.put(skill.skillId(), skill.rank()));
        List<SkillDefinitionLoader.SkillDefinition> classSkills = registry.skills().values().stream()
            .filter(skill -> inheritedClasses(profile.selectedClass()).contains(skill.classId()))
            .filter(this::isSkillSupported)
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
        inventory.setItem(45, createSkillIcon(Material.NETHER_STAR, "§dTiến chức class",
            List.of("§7Xem các class nâng cao có thể chọn.",
                "§7Yêu cầu và thay đổi class được xác nhận trước khi lưu.")));
        inventory.setItem(53, createSkillIcon(Material.ARROW, "§eQuay lại menu chính",
            List.of("§7Trở về menu chính VnMineHardcore.")));
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

    private void openClassPromotionMenu(Player player) {
        UUID playerId = player.getUniqueId();
        debugFeature("[RPG-DEBUG][ClassProgression] menu-open player=" + player.getName());
        database.executeGameplayDatabaseTask("class-promotion.menu player=" + player.getName(),
            () -> database.getPlayerProfile(playerId.toString()))
            .whenComplete((profile, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (failure != null) {
                    plugin.getLogger().log(Level.SEVERE, "Could not load class promotion menu for "
                        + player.getName() + ".", failure);
                    player.sendMessage("§cKhông thể tải danh sách tiến chức.");
                    return;
                }
                if (profile.isEmpty() || !profile.get().active()) {
                    player.sendMessage("§cBạn cần có hồ sơ RPG đang hoạt động.");
                    return;
                }
                CharacterSelectionManager classes = plugin.getCharacterSelectionManager();
                List<CharacterSelectionManager.ClassDefinition> candidates = classes == null ? List.of()
                    : classes.getPromotionCandidates(profile.get().selectedClass());
                PromotionHolder holder = new PromotionHolder(playerId, profile.get().selectedClass(), false, null);
                Inventory inventory = Bukkit.createInventory(holder, 54, "§dTiến chức class");
                holder.inventory = inventory;
                if (candidates.isEmpty()) {
                    inventory.setItem(13, createSkillIcon(Material.BARRIER, "§cChưa có nhánh tiến chức",
                        List.of("§7Class hiện tại chưa có class con đã triển khai.")));
                } else {
                    int[] slots = java.util.stream.IntStream.range(0, 45).toArray();
                    for (int index = 0; index < candidates.size() && index < slots.length; index++) {
                        CharacterSelectionManager.ClassDefinition candidate = candidates.get(index);
                        int requiredLevel = classes.getPromotionRequiredLevel(candidate.id());
                        holder.choices.put(slots[index], candidate.id());
                        List<String> requirements = new java.util.ArrayList<>(List.of(
                            "§7" + candidate.description(), "§8ID: " + candidate.id(),
                            "§7Cấp yêu cầu: §e" + requiredLevel));
                        String requiredQuest = classes.getPromotionRequiredQuest(candidate.id());
                        if (requiredQuest != null) requirements.add("§7Quest yêu cầu: §b" + requiredQuest);
                        appendPromotionNpcRequirement(requirements, classes, candidate.id());
                        appendPromotionItemRequirement(requirements, classes, candidate.id());
                        appendPromotionRpgItemRequirement(requirements, classes, candidate.id());
                        requirements.add("§aNhấn để xem trước.");
                        inventory.setItem(slots[index], createSkillIcon(candidate.material(), "§d" + candidate.name(),
                            requirements));
                    }
                }
                inventory.setItem(53, createSkillIcon(Material.ARROW,
                    "§eQuay lại cây kỹ năng", List.of("§7Quay lại cây kỹ năng.")));
                inventory.setItem(49, createSkillIcon(Material.WRITABLE_BOOK, "§bLịch sử class",
                    List.of("§7Xem class gốc và các lần tiến chức.")));
                player.openInventory(inventory);
                debugFeature("[RPG-DEBUG][ClassProgression] candidates player=" + player.getName()
                    + ", current=" + profile.get().selectedClass() + ", count=" + candidates.size());
            }));
    }

    private void openClassPromotionConfirmation(Player player, String sourceClassId, String targetClassId) {
        CharacterSelectionManager manager = plugin.getCharacterSelectionManager();
        CharacterSelectionManager.ClassDefinition target = manager == null ? null
            : manager.getPromotionCandidates(sourceClassId).stream()
                .filter(candidate -> candidate.id().equals(targetClassId)).findFirst().orElse(null);
        if (target == null) {
            player.sendMessage("§cClass tiến chức không còn hợp lệ; hãy mở lại danh sách.");
            openClassPromotionMenu(player);
            return;
        }
        int requiredLevel = manager.getPromotionRequiredLevel(target.id());
        String requiredQuest = manager.getPromotionRequiredQuest(target.id());
        List<String> requirements = new java.util.ArrayList<>(List.of(
            "§7Class hiện tại: §f" + sourceClassId,
            "§7Class mới: §f" + target.id(),
            "§7Cấp yêu cầu: §e" + requiredLevel));
        if (requiredQuest != null) requirements.add("§7Quest yêu cầu: §b" + requiredQuest);
        appendPromotionNpcRequirement(requirements, manager, target.id());
        appendPromotionItemRequirement(requirements, manager, target.id());
        appendPromotionRpgItemRequirement(requirements, manager, target.id());
        requirements.add("§7Skill đã học sẽ được giữ nguyên.");
        requirements.add("§eNhấn xác nhận để đổi class.");
        PromotionHolder holder = new PromotionHolder(player.getUniqueId(), sourceClassId, true, targetClassId);
        Inventory inventory = Bukkit.createInventory(holder, 27, "§dXác nhận tiến chức");
        holder.inventory = inventory;
        inventory.setItem(13, createSkillIcon(target.material(), "§d" + target.name(), requirements));
        inventory.setItem(11, createSkillIcon(Material.LIME_CONCRETE, "§aXác nhận tiến chức", List.of(
            "§7Hồ sơ sẽ chuyển sang class mới.")));
        inventory.setItem(15, createSkillIcon(Material.RED_CONCRETE, "§cQuay lại", List.of("§7Không thay đổi hồ sơ.")));
        player.openInventory(inventory);
    }

    private void openClassHistory(Player player) {
        UUID playerId = player.getUniqueId();
        database.executeGameplayDatabaseTask("class-promotion.history player=" + player.getName(), () -> {
            Optional<PlayerProfileDatabaseManager.PlayerProfile> profile = database.getPlayerProfile(playerId.toString());
            if (profile.isEmpty() || !profile.get().active()) return null;
            return new ClassHistoryView(profile.get(), database.getClassHistory(playerId.toString()));
        }).whenComplete((data, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (failure != null) {
                plugin.getLogger().log(Level.SEVERE, "Could not load class history for " + player.getName() + ".", failure);
                player.sendMessage("§cKhông thể tải lịch sử class.");
                return;
            }
            if (data == null) {
                player.sendMessage("§cKhông tìm thấy hồ sơ RPG đang hoạt động.");
                return;
            }
            String baseClassId = data.history().isEmpty()
                ? data.profile().selectedClass() : data.history().get(0).fromClass();
            debugFeature("[RPG-DEBUG][ClassProgression] history-view player=" + player.getName()
                + ", base=" + baseClassId + ", current=" + data.profile().selectedClass()
                + ", transitions=" + data.history().size());
            CharacterSelectionManager manager = plugin.getCharacterSelectionManager();
            CharacterSelectionManager.ClassDefinition base = manager == null ? null
                : manager.getClassDefinition(baseClassId);
            CharacterSelectionManager.ClassDefinition current = manager == null ? null
                : manager.getClassDefinition(data.profile().selectedClass());
            PromotionHolder holder = new PromotionHolder(playerId, data.profile().selectedClass(), false, null, true);
            Inventory inventory = Bukkit.createInventory(holder, 54, "§bLịch sử class");
            holder.inventory = inventory;
            inventory.setItem(4, createSkillIcon(Material.PLAYER_HEAD, "§dClass hiện tại: §f"
                + (current == null ? data.profile().selectedClass() : current.name()), List.of(
                "§7Class gốc: §e" + (base == null ? baseClassId : base.name()),
                "§7Số lần tiến chức: §e" + data.history().size())));
            if (data.history().isEmpty()) {
                inventory.setItem(22, createSkillIcon(Material.PAPER, "§7Chưa có lần tiến chức",
                    List.of("§7Đây là class ban đầu của nhân vật.")));
            } else {
                for (int index = 0; index < data.history().size() && index < 35; index++) {
                    PlayerProfileDatabaseManager.ClassHistoryEntry entry = data.history().get(index);
                    CharacterSelectionManager.ClassDefinition from = manager == null ? null
                        : manager.getClassDefinition(entry.fromClass());
                    CharacterSelectionManager.ClassDefinition to = manager == null ? null
                        : manager.getClassDefinition(entry.toClass());
                    String fromName = from == null ? entry.fromClass() : from.name();
                    String toName = to == null ? entry.toClass() : to.name();
                    inventory.setItem(10 + index, createSkillIcon(Material.EXPERIENCE_BOTTLE,
                        "§eLần " + (index + 1) + ": §f" + toName,
                        List.of("§7Từ: §f" + fromName, "§7Đến: §f" + toName,
                            "§8Thời gian: " + entry.changedAt())));
                }
            }
            inventory.setItem(53, createSkillIcon(Material.ARROW, "§eQuay lại tiến chức", List.of("§7Trở về danh sách class.")));
            player.openInventory(inventory);
        }));
    }

    private record ClassHistoryView(PlayerProfileDatabaseManager.PlayerProfile profile,
                                    List<PlayerProfileDatabaseManager.ClassHistoryEntry> history) { }

    private void confirmClassPromotion(Player player, String sourceClassId, String targetClassId) {
        CharacterSelectionManager manager = plugin.getCharacterSelectionManager();
        CharacterSelectionManager.ClassDefinition target = manager == null ? null
            : manager.getPromotionCandidates(sourceClassId).stream()
                .filter(candidate -> candidate.id().equals(targetClassId)).findFirst().orElse(null);
        if (target == null) {
            player.sendMessage("§cClass tiến chức không còn hợp lệ.");
            openClassPromotionMenu(player);
            return;
        }
        UUID playerId = player.getUniqueId();
        int requiredLevel = manager.getPromotionRequiredLevel(target.id());
        String requiredQuest = manager.getPromotionRequiredQuest(target.id());
        String changedAt = Instant.now().toString();
        Material requiredItem = manager.getPromotionRequiredItem(target.id());
        int requiredItemAmount = manager.getPromotionRequiredItemAmount(target.id());
        if (requiredItem != null && countInventoryMaterial(player, requiredItem) < requiredItemAmount) {
            debugFeature("[RPG-DEBUG][ClassProgression] promotion-rejected player=" + player.getName()
                + ", target=" + targetClassId + ", reason=required-item-missing, item=" + requiredItem
                + ", required=" + requiredItemAmount + ", found=" + countInventoryMaterial(player, requiredItem));
            player.sendMessage("§cBạn cần mang theo " + requiredItemAmount + " " + requiredItem + " để tiến chức.");
            return;
        }
        String requiredNpcId = manager.getPromotionRequiredNpc(target.id());
        if (requiredNpcId != null && !hasVisitedNpc(player, requiredNpcId)) {
            debugFeature("[RPG-DEBUG][ClassProgression] promotion-rejected player=" + player.getName()
                + ", target=" + targetClassId + ", reason=required-npc-not-visited, npc=" + requiredNpcId);
            player.sendMessage("§cBạn cần gặp NPC §e" + requiredNpcId + "§c trước khi tiến chức.");
            return;
        }
        String requiredRpgItemId = manager.getPromotionRequiredRpgItem(target.id());
        int requiredRpgItemAmount = manager.getPromotionRequiredRpgItemAmount(target.id());
        int carriedRpgItemAmount = requiredRpgItemId == null ? 0
            : countInventoryQuestItem(player, requiredRpgItemId);
        if (requiredRpgItemId != null && carriedRpgItemAmount < requiredRpgItemAmount) {
            debugFeature("[RPG-DEBUG][ClassProgression] promotion-rejected player=" + player.getName()
                + ", target=" + targetClassId + ", reason=required-rpg-item-missing, itemId=" + requiredRpgItemId
                + ", required=" + requiredRpgItemAmount + ", found=" + carriedRpgItemAmount);
            player.sendMessage("§cBạn cần mang theo " + requiredRpgItemAmount + " item RPG §e"
                + requiredRpgItemId + "§c để tiến chức.");
            return;
        }
        debugFeature("[RPG-DEBUG][ClassProgression] promotion-request player=" + player.getName()
            + ", from=" + sourceClassId + ", to=" + targetClassId + ", requiredLevel=" + requiredLevel
            + ", requiredQuest=" + (requiredQuest == null ? "none" : requiredQuest)
            + ", requiredNpc=" + (requiredNpcId == null ? "none" : requiredNpcId)
            + ", requiredItem=" + (requiredItem == null ? "none" : requiredItem + "x" + requiredItemAmount)
            + ", requiredRpgItem=" + (requiredRpgItemId == null ? "none"
                : requiredRpgItemId + "x" + requiredRpgItemAmount));
        database.executeGameplayDatabaseTask("class-promotion.commit player=" + player.getName(),
            () -> database.promoteClass(playerId.toString(), sourceClassId, targetClassId, requiredLevel,
                requiredQuest, changedAt))
            .whenComplete((result, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (failure != null) {
                    plugin.getLogger().log(Level.SEVERE, "Could not promote class for " + player.getName() + ".", failure);
                    player.sendMessage("§cKhông thể lưu tiến chức. Class cũ vẫn được giữ.");
                    return;
                }
                debugFeature("[RPG-DEBUG][ClassProgression] promotion-result player=" + player.getName()
                    + ", from=" + sourceClassId + ", to=" + targetClassId + ", result=" + result);
                switch (result) {
                    case PROMOTED -> {
                        player.closeInventory();
                        player.sendTitle("§d§lTIẾN CHỨC THÀNH CÔNG", "§fClass mới: §e" + target.name(),
                            5, 50, 10);
                        if (manager != null) manager.refreshLoadedProfile(player);
                        refreshPassiveRanks(player);
                    }
                    case REQUIREMENTS_NOT_MET -> player.sendMessage("§cBạn chưa đáp ứng đủ cấp độ hoặc quest yêu cầu.");
                    case CLASS_CHANGED -> player.sendMessage("§cClass đã thay đổi hoặc bạn đã tiến chức rồi.");
                    case PROFILE_NOT_FOUND -> player.sendMessage("§cKhông tìm thấy hồ sơ RPG.");
                }
            }));
    }

    private void appendPromotionItemRequirement(List<String> lore, CharacterSelectionManager manager, String classId) {
        Material item = manager.getPromotionRequiredItem(classId);
        int amount = manager.getPromotionRequiredItemAmount(classId);
        if (item != null && amount > 0) lore.add("§7Cần mang theo: §e" + amount + " " + item);
    }

    private void appendPromotionNpcRequirement(List<String> lore, CharacterSelectionManager manager, String classId) {
        String npcId = manager.getPromotionRequiredNpc(classId);
        if (npcId != null) lore.add("§7Cần gặp NPC: §e" + npcId);
    }

    private void appendPromotionRpgItemRequirement(List<String> lore, CharacterSelectionManager manager,
                                                   String classId) {
        String itemId = manager.getPromotionRequiredRpgItem(classId);
        int amount = manager.getPromotionRequiredRpgItemAmount(classId);
        if (itemId != null && amount > 0) lore.add("§7Cần mang theo RPG item: §e" + amount + "x " + itemId);
    }

    private int countInventoryQuestItem(Player player, String itemId) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            String actualId = item.getItemMeta().getPersistentDataContainer()
                .get(questItemIdKey, PersistentDataType.STRING);
            if (itemId.equals(actualId)) count += item.getAmount();
        }
        return count;
    }

    private boolean hasVisitedNpc(Player player, String npcId) {
        String visited = player.getPersistentDataContainer().get(classVisitedNpcsKey, PersistentDataType.STRING);
        if (visited == null || visited.isBlank()) return false;
        for (String entry : visited.split(",")) if (npcId.equals(entry)) return true;
        return false;
    }

    private int countInventoryMaterial(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() != material) continue;
            count += item.getAmount();
        }
        return count;
    }

    private Set<String> inheritedClasses(String classId) {
        if (classId == null || classId.isBlank()) return Set.of();
        CharacterSelectionManager manager = plugin.getCharacterSelectionManager();
        return manager == null ? Set.of(classId) : manager.getInheritedClassIds(classId);
    }

    private Set<String> classesEligibleForSkill(String skillClassId) {
        if (skillClassId == null || skillClassId.isBlank()) return Set.of();
        CharacterSelectionManager manager = plugin.getCharacterSelectionManager();
        return manager == null ? Set.of(skillClassId) : manager.getClassesInheritingFrom(skillClassId);
    }

    public void openSkillTreeFromNpc(Player player) {
        openSkillTree(player);
    }

    public void openSkillTreeFromMainMenu(Player player) {
        openSkillTree(player);
    }

    public void giveSkillBookFromNpc(Player player, String bookId) {
        SkillDefinitionLoader.SkillBookDefinition book = registry.books().get(bookId);
        if (book == null) {
            plugin.getLogger().warning("[NPC] Configured skill book '" + bookId + "' does not exist.");
            player.sendMessage("§cSách kỹ năng này hiện không khả dụng.");
            return;
        }
        UUID playerId = player.getUniqueId();
        database.executeGameplayDatabaseTask("skill.npc-book-check player=" + player.getName(),
                () -> database.getLearnedSkills(playerId.toString()).stream()
                .anyMatch(progress -> progress.skillId().equals(book.skillId())))
            .whenComplete((learned, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (failure != null) {
                plugin.getLogger().log(Level.SEVERE, "Could not check learned skills before NPC book grant for "
                    + player.getName() + ".", failure);
                player.sendMessage("§cKhông thể kiểm tra kỹ năng đã học. Hãy thử lại sau.");
                return;
            }
            if (learned) {
                player.sendMessage("§eBạn đã học kỹ năng này rồi.");
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
        }));
    }

    public List<SkillDefinitionLoader.SkillBookDefinition> getSkillBooks() {
        return registry.books().values().stream()
            .filter(book -> isSkillSupported(registry.skills().get(book.skillId())))
            .sorted(Comparator.comparing(SkillDefinitionLoader.SkillBookDefinition::id)).toList();
    }

    public List<AdminSkillBookItem> getAdminSkillBookItems() {
        return getSkillBooks().stream().map(book -> new AdminSkillBookItem(
            book.id(), book.displayName(), book.material(), createSkillBook(book))).toList();
    }

    public record AdminSkillBookItem(String id, String name, Material material, ItemStack item) { }

    public List<SkillDefinitionLoader.SkillBookDefinition> getSkillBooksForClass(String classId) {
        if (classId == null || classId.isBlank()) return List.of();
        Set<String> inherited = inheritedClasses(classId);
        return getSkillBooks().stream().filter(book -> {
            SkillDefinitionLoader.SkillDefinition skill = registry.skills().get(book.skillId());
            return skill != null && inherited.contains(skill.classId());
        }).toList();
    }

    public Optional<byte[]> serializeStarterSkillBook(String bookId) {
        SkillDefinitionLoader.SkillBookDefinition book = registry.books().get(bookId);
        if (book == null || !isSkillSupported(registry.skills().get(book.skillId()))) return Optional.empty();
        return Optional.of(createSkillBook(book).serializeAsBytes());
    }

    public Set<String> getPlayableClassIds() {
        return registry.skills().values().stream()
            .filter(this::isSkillSupported)
            .map(SkillDefinitionLoader.SkillDefinition::classId)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private boolean isSkillSupported(SkillDefinitionLoader.SkillDefinition skill) {
        return skill != null && (skill.passive()
            ? IMPLEMENTED_PASSIVES.contains(skill.id())
            : CAST_HANDLERS.contains(skill.id()));
    }

    public boolean giveSkillBookTo(Player target, String bookId) {
        SkillDefinitionLoader.SkillBookDefinition book = registry.books().get(bookId);
        if (book == null || !isSkillSupported(registry.skills().get(book.skillId()))) return false;
        if (!target.getInventory().addItem(createSkillBook(book)).isEmpty()) return false;
        target.sendMessage("§aBạn nhận được sách kỹ năng §e" + book.displayName() + "§a.");
        return true;
    }

    private void openSkillDetails(Player player, String skillId) {
        SkillDefinitionLoader.SkillDefinition skill = registry.skills().get(skillId);
        if (!isSkillSupported(skill)) return;
        UUID playerId = player.getUniqueId();
        database.executeGameplayDatabaseTask("skill.details-menu player=" + player.getName()
                + " skill=" + skillId, () -> {
            Optional<PlayerProfileDatabaseManager.PlayerProfile> loaded =
                database.getPlayerProfile(playerId.toString());
            if (loaded.isEmpty() || !loaded.get().active()) return null;
            int rank = database.getLearnedSkills(playerId.toString()).stream()
                .filter(progress -> progress.skillId().equals(skillId))
                .mapToInt(PlayerProfileDatabaseManager.SkillProgress::rank).findFirst().orElse(0);
            return new SkillDetailsData(loaded.get(), rank);
        }).whenComplete((data, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (failure != null) {
                plugin.getLogger().log(Level.SEVERE, "Could not load skill details for " + skillId
                    + " and " + player.getName() + ".", failure);
                player.sendMessage("§cKhông thể tải chi tiết kỹ năng. Hãy thử lại sau.");
            } else if (data == null) {
                debugFeature("[RPG-DEBUG][SkillUI] details-data player=" + player.getName()
                    + ", skill=" + skillId + ", loaded=false");
                player.sendMessage("§cBạn cần tạo nhân vật RPG trước.");
            } else {
                debugFeature("[RPG-DEBUG][SkillUI] details-data player=" + player.getName()
                    + ", skill=" + skillId + ", rank=" + data.rank()
                    + ", class=" + data.profile().selectedClass());
                renderSkillDetails(player, skill, skillId, data.profile(), data.rank());
            }
        }));
    }

    private record SkillDetailsData(PlayerProfileDatabaseManager.PlayerProfile profile, int rank) {
    }

    private void renderSkillDetails(Player player, SkillDefinitionLoader.SkillDefinition skill, String skillId,
                                    PlayerProfileDatabaseManager.PlayerProfile profile, int rank) {
        QuickCastHolder holder = new QuickCastHolder(QuickCastScreen.SKILL_DETAILS, 0, skillId);
        Inventory inventory = Bukkit.createInventory(holder, 54, "§6Chi tiết: " + skill.displayName());
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
        inventory.setItem(22, createSkillIcon(Material.ARROW, "§eQuay lại cây kỹ năng",
            List.of("§7Trở về danh sách kỹ năng của class.")));
        player.openInventory(inventory);
    }

    private void upgradeSkill(Player player, String skillId) {
        SkillDefinitionLoader.SkillDefinition skill = registry.skills().get(skillId);
        if (skill == null || (skill.passive() && !IMPLEMENTED_PASSIVES.contains(skill.id()))) return;
        UUID playerId = player.getUniqueId();
        if (!pendingSkillUpgrades.add(playerId)) {
            player.sendMessage("§eĐang xử lý nâng cấp kỹ năng. Vui lòng chờ một chút.");
            return;
        }
        debugFeature("[RPG-DEBUG][ClassProgression] skill-upgrade player=" + player.getName()
            + ", skill=" + skillId + ", sourceClass=" + skill.classId()
            + ", eligibleClasses=" + classesEligibleForSkill(skill.classId()));
        database.executeGameplayDatabaseTask("skill.upgrade player=" + player.getName()
                + " skill=" + skillId, () -> database.upgradeSkill(
                playerId.toString(), skillId, classesEligibleForSkill(skill.classId()),
                skill.requiredLevel(), skill.maxRank()))
            .whenComplete((result, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            pendingSkillUpgrades.remove(playerId);
            if (!player.isOnline()) return;
            if (failure != null) {
                plugin.getLogger().log(Level.SEVERE, "Could not upgrade skill " + skillId
                    + " for " + player.getName() + ".", failure);
                player.sendMessage("§cKhông thể nâng kỹ năng. Skill point chưa bị trừ.");
                return;
            }
            debugFeature("[RPG-DEBUG][SkillUpgrade] player=" + player.getName()
                + ", skill=" + skillId + ", result=" + result);
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
        }));
    }

    private void openSkillPicker(Player player, int selectedSlot) {
        UUID playerId = player.getUniqueId();
        database.executeGameplayDatabaseTask("skill.quick-slot-picker player=" + player.getName(),
                () -> database.getLearnedSkills(playerId.toString()))
            .whenComplete((learned, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (failure != null) {
                    plugin.getLogger().log(Level.SEVERE,
                        "Could not load learned skills for quick-slot assignment: " + player.getName() + ".",
                        failure);
                    player.sendMessage("§cKhông thể tải kỹ năng. Hãy thử lại sau.");
                    return;
                }
                renderSkillPicker(player, selectedSlot, learned);
            }));
    }

    private void renderSkillPicker(Player player, int selectedSlot,
                                   List<PlayerProfileDatabaseManager.SkillProgress> learned) {
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
        return createSkillIcon(icon, title, details);
    }

    private ItemStack createSkillIcon(Material material, String title, List<String> details) {
        return createSkillIcon(new ItemStack(material), title, details);
    }

    private ItemStack createSkillIcon(ItemStack icon, String title, List<String> details) {
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(title);
        meta.setLore(details);
        icon.setItemMeta(meta);
        return icon;
    }

    static int quickBarSlot(int inventorySlot) {
        return inventorySlot >= 0 && inventorySlot < 9 ? inventorySlot + 1 : -1;
    }

    private void assignQuickSlot(Player player, int slot, String skillId) {
        SkillDefinitionLoader.SkillDefinition skill = registry.skills().get(skillId);
        if (skill == null || slot < 1 || slot > 9) return;
        if (skill.passive() || !CAST_HANDLERS.contains(skillId)) {
            player.sendMessage("§cKỹ năng này chưa hỗ trợ quick cast.");
            return;
        }
        UUID playerId = player.getUniqueId();
        database.executeGameplayDatabaseTask("skill.quick-slot-assign player=" + player.getName()
                + " slot=" + slot + " skill=" + skillId, () -> database.assignQuickSkill(
                playerId.toString(), slot, skill.id(), classesEligibleForSkill(skill.classId())))
            .whenComplete((result, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (failure != null) {
                plugin.getLogger().log(Level.SEVERE, "Could not assign skill " + skillId + " to slot "
                    + slot + " for " + player.getName() + ".", failure);
                player.sendMessage("§cKhông thể lưu quick slot. Hãy thử lại sau.");
                return;
            }
            debugFeature("[RPG-DEBUG][QuickSlot] assign player=" + player.getName()
                + ", slot=" + slot + ", skill=" + skillId + ", result=" + result);
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
        }));
    }

    private void clearQuickSlot(Player player, int slot) {
        UUID playerId = player.getUniqueId();
        database.executeGameplayDatabaseTask("skill.quick-slot-clear player=" + player.getName()
                + " slot=" + slot, () -> database.clearQuickSkillSlot(playerId.toString(), slot))
            .whenComplete((cleared, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (failure != null) {
                plugin.getLogger().log(Level.SEVERE, "Could not clear quick slot " + slot
                    + " for " + player.getName() + ".", failure);
                player.sendMessage("§cKhông thể lưu thay đổi quick slot.");
                return;
            }
            debugFeature("[RPG-DEBUG][QuickSlot] clear player=" + player.getName()
                + ", slot=" + slot + ", changed=" + cleared);
            if (cleared) {
                player.sendMessage("§aĐã bỏ gán kỹ năng ở slot " + slot + ".");
            }
            openQuickCast(player);
        }));
    }

    private void castQuickSlot(Player player, int slot) {
        UUID playerId = player.getUniqueId();
        debugFeature("[RPG-DEBUG][SkillCast] quick-slot-read player=" + player.getName()
            + ", slot=" + slot);
        database.executeGameplayDatabaseTask("skill.quick-slot-cast player=" + player.getName(),
                () -> database.getQuickSkillSlots(playerId.toString()))
            .whenComplete((quickSlots, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (failure != null) {
                    plugin.getLogger().log(Level.SEVERE, "Could not load quick skill slots before cast for "
                        + player.getName() + ".", failure);
                    player.sendMessage("§cKhông thể tải quick slots. Hãy thử lại sau.");
                    return;
                }
                String skillId = quickSlots.get(slot);
                debugFeature("[RPG-DEBUG][SkillCast] quick-slot-result player=" + player.getName()
                    + ", slot=" + slot + ", skill=" + (skillId == null ? "empty" : skillId));
                if (skillId == null) {
                    player.sendMessage("§7Slot " + slot + " chưa được gán kỹ năng.");
                    return;
                }
                castSkill(player, skillId);
            }));
    }

    private void castSkill(Player player, String skillId) {
        long castStartedAt = System.nanoTime();
        debugCast(player, skillId, "started");
        SkillDefinitionLoader.SkillDefinition skill = registry.skills().get(skillId);
        if (!isSkillSupported(skill) || skill.passive()) {
            debugCast(player, skillId, "rejected: unsupported or passive skill");
            player.sendMessage("§cKỹ năng này không thể cast.");
            return;
        }
        debugCast(player, skillId, "definition class=" + skill.classId() + ", manaCost="
            + skill.manaCost() + ", cooldownSeconds=" + skill.cooldownSeconds());
        long remaining = cooldownRemainingMillis(player, skillId);
        if (remaining > 0) {
            debugCast(player, skillId, "rejected: cooldown remaining " + remaining + "ms");
            player.sendMessage("§e" + skill.displayName() + " hồi chiêu còn "
                + ((remaining + 999) / 1000) + " giây.");
            return;
        }
        long targetCheckStartedAt = System.nanoTime();
        if (!hasValidCastTarget(player, skill)) {
            debugCast(player, skillId, "rejected: no valid target");
            player.sendMessage("§eKhông có mục tiêu hợp lệ trong phạm vi kỹ năng.");
            return;
        }
        debugCast(player, skillId, "valid target, checkMs=" + elapsedMillis(targetCheckStartedAt));
        UUID playerId = player.getUniqueId();
        if (!pendingSkillCasts.add(playerId)) {
            debugCast(player, skillId, "rejected: another skill cast is waiting for the database");
            return;
        }
        long databaseSubmittedAt = System.nanoTime();
        database.executeGameplayDatabaseTask("skill.cast-spend player=" + player.getName() + " skill=" + skillId,
                () -> database.spendManaForSkillCast(
                playerId.toString(), skill.id(), classesEligibleForSkill(skill.classId()),
                skill.manaCost(), config.rpgMaxMana,
                config.rpgManaRegenerationPerSecond, System.currentTimeMillis()))
            .whenComplete((castState, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (failure != null) {
                    pendingSkillCasts.remove(playerId);
                    plugin.getLogger().log(Level.SEVERE, "Could not validate/spend mana for skill " + skillId
                        + " cast by " + player.getName() + ".", failure);
                    if (player.isOnline()) player.sendMessage("§cKhông thể dùng mana. Kỹ năng chưa được cast.");
                    return;
                }
                logDatabaseCallbackLatency(player, skillId, castState.status(), databaseSubmittedAt);
                debugCast(player, skillId, "database-result=" + castState.status()
                    + (castState.manaState() == null ? "" : ", mana=" + castState.manaState().currentMana()
                        + "/" + castState.manaState().maxMana())
                    + ", rank=" + castState.rank());
                if (!player.isOnline()) {
                    pendingSkillCasts.remove(playerId);
                    if (castState.status() == PlayerProfileDatabaseManager.SkillCastStatus.SPENT) {
                        refundManaAsync(playerId, skill.manaCost(), null, skillId, player.getName());
                    }
                    return;
                }
                finishSkillCast(player, skill, castState, castStartedAt);
            }));
    }

    private void finishSkillCast(Player player, SkillDefinitionLoader.SkillDefinition skill,
                                 PlayerProfileDatabaseManager.SkillCastResult castState,
                                 long castStartedAt) {
        UUID playerId = player.getUniqueId();
        switch (castState.status()) {
            case PROFILE_NOT_FOUND -> {
                pendingSkillCasts.remove(playerId);
                player.sendMessage("§cKhông tìm thấy hồ sơ nhân vật đang hoạt động.");
                return;
            }
            case CLASS_MISMATCH -> {
                pendingSkillCasts.remove(playerId);
                player.sendMessage("§cKỹ năng không dành cho class hiện tại.");
                return;
            }
            case SKILL_NOT_LEARNED -> {
                pendingSkillCasts.remove(playerId);
                player.sendMessage("§cBạn chưa học kỹ năng này.");
                return;
            }
            case NOT_ENOUGH_MANA -> {
                pendingSkillCasts.remove(playerId);
                PlayerProfileDatabaseManager.ManaState state = castState.manaState();
                if (state != null) showManaActionBar(player, state);
                player.sendMessage("§cKhông đủ mana" + (state == null ? "."
                    : " (" + state.currentMana() + "/" + state.maxMana() + ")."));
                return;
            }
            case SPENT -> { }
        }

        if (!hasValidCastTarget(player, skill)) {
            debugCast(player, skill.id(), "target-recheck=failed, manaRefund=" + skill.manaCost());
            refundManaAsync(playerId, skill.manaCost(), player, skill.id(), player.getName());
            return;
        }
        boolean castSucceeded;
        try {
            castSucceeded = applySkillEffect(player, skill, castState.rank());
        } catch (RuntimeException exception) {
            castSucceeded = false;
            plugin.getLogger().log(Level.SEVERE, "Skill effect threw an exception after mana was spent: skill="
                + skill.id() + ", player=" + player.getName() + ". Mana refund will be attempted.", exception);
        }
        debugCast(player, skill.id(), "effect=" + (castSucceeded ? "applied" : "failed")
            + ", elapsedMs=" + elapsedMillis(castStartedAt));
        if (!castSucceeded) {
            refundManaAsync(playerId, skill.manaCost(), player, skill.id(), player.getName());
            return;
        }

        pendingSkillCasts.remove(playerId);
        long castAt = System.currentTimeMillis();
        int manaFlowRank = manaFlowRanks.getOrDefault(playerId, 0);
        long previousManaFlowCast = manaFlowLastCastAt.getOrDefault(playerId, 0L);
        boolean manaFlowChain = manaFlowRank > 0
            && isManaFlowChain(previousManaFlowCast, castAt, config.rpgManaFlowChainWindowSeconds);
        manaFlowLastCastAt.put(playerId, castAt);
        cooldowns.computeIfAbsent(playerId, ignored -> new HashMap<>())
            .put(skill.id(), castAt + skill.cooldownSeconds() * 1000L);
        player.sendMessage("§aĐã cast §e" + skill.displayName() + "§a §8| §b-" + skill.manaCost() + " mana");
        debugCast(player, skill.id(), "completed, elapsedMs=" + elapsedMillis(castStartedAt));
        if (manaFlowChain) restoreManaFlowAsync(player, manaFlowRank, castState.manaState());
        else if (castState.manaState() != null) showManaActionBar(player, castState.manaState());
    }

    private void refundManaAsync(UUID playerId, int amount, Player player, String skillId, String playerName) {
        database.executeGameplayDatabaseTask("skill.refund player=" + playerName + " skill=" + skillId,
                () -> database.restoreMana(
                playerId.toString(), amount, config.rpgMaxMana,
                config.rpgManaRegenerationPerSecond, System.currentTimeMillis()))
            .whenComplete((refund, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                pendingSkillCasts.remove(playerId);
                if (failure != null) {
                    plugin.getLogger().log(Level.SEVERE, "Could not refund mana after failed skill cast "
                        + skillId + " for " + playerName + ".", failure);
                    if (player != null && player.isOnline()) {
                        player.sendMessage("§cKỹ năng không kích hoạt được; lỗi hoàn mana đã được ghi nhận. Hãy báo quản trị viên.");
                    }
                    return;
                }
                if (refund.isEmpty()) {
                    plugin.getLogger().severe("Could not refund mana after failed skill cast " + skillId
                        + " for " + playerName + ": active profile was not found.");
                    if (player != null && player.isOnline()) {
                        player.sendMessage("§cKỹ năng không kích hoạt được và chưa thể hoàn mana. Hãy báo quản trị viên.");
                    }
                    return;
                }
                debugFeature("[RPG-DEBUG][SkillCast] refund-result player=" + playerName
                    + ", skill=" + skillId + ", mana=" + refund.get().state().currentMana()
                    + "/" + refund.get().state().maxMana());
                if (player != null && player.isOnline()) {
                    showManaActionBar(player, refund.get().state());
                    player.sendMessage("§eKỹ năng không kích hoạt được. Mana đã được hoàn lại; hồi chiêu không bắt đầu.");
                }
            }));
    }

    private void restoreManaFlowAsync(Player player, int rank,
                                      PlayerProfileDatabaseManager.ManaState currentState) {
        int restoreAmount = calculateManaFlowRestore(rank, config.rpgManaFlowManaPerChainCastPerRank);
        if (restoreAmount == 0) {
            if (currentState != null) showManaActionBar(player, currentState);
            return;
        }
        UUID playerId = player.getUniqueId();
        database.executeGameplayDatabaseTask("skill.mana-flow player=" + player.getName(),
                () -> database.restoreMana(
                playerId.toString(), restoreAmount, config.rpgMaxMana,
                config.rpgManaRegenerationPerSecond, System.currentTimeMillis()))
            .whenComplete((result, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (failure != null) {
                    plugin.getLogger().log(Level.SEVERE, "Could not restore Mana Flow mana for "
                        + player.getName() + ".", failure);
                    player.sendMessage("§cCast thành công nhưng Mạch Pháp không thể hồi mana lần này.");
                    if (currentState != null) showManaActionBar(player, currentState);
                    return;
                }
                if (result.isEmpty()) {
                    plugin.getLogger().warning("Mana Flow could not restore mana because the active profile "
                        + "was not found for " + player.getName() + ".");
                    player.sendMessage("§cCast thành công nhưng Mạch Pháp không tìm thấy hồ sơ để hồi mana.");
                    if (currentState != null) showManaActionBar(player, currentState);
                    return;
                }
                debugFeature("[RPG-DEBUG][ManaFlow] player=" + player.getName()
                    + ", requested=" + restoreAmount + ", restored=" + result.get().restoredAmount()
                    + ", mana=" + result.get().state().currentMana() + "/" + result.get().state().maxMana());
                showManaActionBar(player, result.get().state());
                if (result.get().restoredAmount() > 0) {
                    player.sendMessage("§dMạch Pháp: +" + result.get().restoredAmount() + " mana.");
                }
            }));
    }

    private void debugCast(Player player, String skillId, String detail) {
        if (config.consoleDebug) {
            plugin.getLogger().info("[RPG-DEBUG][SkillCast] player=" + player.getName()
                + ", skill=" + skillId + ", " + detail);
        }
    }

    private void debugFeature(String message) {
        if (config.consoleDebug) plugin.getLogger().info(message);
    }

    private long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }

    private void showManaActionBar(Player player, PlayerProfileDatabaseManager.ManaState state) {
        int segments = 10;
        int filled = Math.max(0, Math.min(segments,
            (int) Math.round((double) state.currentMana() / state.maxMana() * segments)));
        String bar = "§b" + "▰".repeat(filled) + "§8" + "▱".repeat(segments - filled);
        player.sendActionBar("§3✦ Mana §f" + state.currentMana() + "/" + state.maxMana() + " " + bar);
        debugCast(player, "mana", "actionbar-shown=" + state.currentMana() + "/" + state.maxMana());
    }

    private void logDatabaseCallbackLatency(Player player, String skillId,
                                            PlayerProfileDatabaseManager.SkillCastStatus status,
                                            long submittedAtNanos) {
        long durationMillis = elapsedMillis(submittedAtNanos);
        debugCast(player, skillId, "transaction=" + status + ", taskToMainCallbackMs=" + durationMillis
            + " (includes queue, DB and scheduler handoff)");
        if (durationMillis >= 250) {
            plugin.getLogger().warning("[RPG-PERF][SkillCast] responseLatencyMs=" + durationMillis
                + ", includes queue/DB/main-thread handoff, player=" + player.getName()
                + ", skill=" + skillId + ", status=" + status);
        }
    }

    private boolean hasValidCastTarget(Player player, SkillDefinitionLoader.SkillDefinition skill) {
        return switch (skill.id()) {
            case "blade-flare" -> findRayTarget(player, 5.0, 0.8) != null;
            case "guard-break" -> findRayTarget(player, 4.0, 0.6) != null;
            case "shield-wall" -> true;
            case "taunt" -> player.getNearbyEntities(8, 5, 8).stream().anyMatch(Monster.class::isInstance);
            case "fire-bolt" -> findRayTarget(player, 20.0, 0.4) != null;
            case "arcane-field" -> !getMeteorTargets(getArcaneFieldCenter(player)).isEmpty();
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
        Location center = getArcaneFieldCenter(player);
        List<Monster> targets = getMeteorTargets(center);
        debugCast(player, "arcane-field", "center=" + center.getBlockX() + "," + center.getBlockY()
            + "," + center.getBlockZ() + ", initialTargets=" + targets.size() + ", rank=" + rank);
        if (targets.isEmpty()) return false;
        startMeteorShower(player, center, rank);
        return true;
    }

    private List<Monster> getMeteorTargets(Location center) {
        return center.getWorld().getNearbyEntities(center, 5.0, 4.0, 5.0).stream()
            .filter(Monster.class::isInstance).map(Monster.class::cast).toList();
    }

    private void startMeteorShower(Player caster, Location center, int rank) {
        org.bukkit.World world = center.getWorld();
        Location strikeCenter = center.clone();
        Set<java.util.UUID> damagedTargets = new HashSet<>();
        int meteorCount = 5 + rank;
        double damage = 8.0 + rank * 2.0;
        debugCast(caster, "arcane-field", "warning=started, meteorCount=" + meteorCount
            + ", damagePerTarget=" + damage + ", center=" + strikeCenter.getBlockX() + ","
            + strikeCenter.getBlockY() + "," + strikeCenter.getBlockZ() + ", terrainChanges=false");

        new BukkitRunnable() {
            private int elapsedTicks;

            @Override
            public void run() {
                if (!world.isChunkLoaded(strikeCenter.getBlockX() >> 4, strikeCenter.getBlockZ() >> 4)) {
                    if (caster.isOnline()) debugCast(caster, "arcane-field", "cancelled=chunk-unloaded");
                    cancel();
                    return;
                }
                if (elapsedTicks < 20 && elapsedTicks % 2 == 0) {
                    spawnMeteorWarning(world, strikeCenter);
                }
                if (elapsedTicks == 0) {
                    caster.playSound(strikeCenter, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 0.7f);
                }
                if (elapsedTicks == 20) {
                    for (int index = 0; index < meteorCount; index++) {
                        Location impact = getMeteorImpact(strikeCenter, index, meteorCount);
                        if (caster.isOnline()) debugCast(caster, "arcane-field", "meteor-launch="
                            + (index + 1) + "/" + meteorCount + ", impact=" + impact.getBlockX() + ","
                            + impact.getBlockY() + "," + impact.getBlockZ());
                        launchMeteor(caster.getUniqueId(), world, impact, index * 5L, damage, damagedTargets);
                    }
                    cancel();
                }
                elapsedTicks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void spawnMeteorWarning(org.bukkit.World world, Location center) {
        double radius = 4.5;
        for (int point = 0; point < 20; point++) {
            double angle = Math.PI * 2.0 * point / 20.0;
            Location marker = center.clone().add(Math.cos(angle) * radius, 0.15,
                Math.sin(angle) * radius);
            world.spawnParticle(Particle.END_ROD, marker, 1, 0, 0, 0, 0);
        }
        world.spawnParticle(Particle.PORTAL, center.clone().add(0, 0.2, 0), 10,
            1.4, 0.05, 1.4, 0.02);
    }

    private Location getMeteorImpact(Location center, int index, int count) {
        if (index == 0) return center.clone();
        double angle = index * 2.399963229728653;
        double radius = 4.0 * Math.sqrt(index / (double) (count - 1));
        return center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
    }

    private void launchMeteor(java.util.UUID casterId, org.bukkit.World world, Location impact,
                              long delayTicks, double damage, Set<java.util.UUID> damagedTargets) {
        double driftAngle = (impact.getX() * 0.17 + impact.getZ() * 0.11) % (Math.PI * 2.0);
        Vector drift = new Vector(Math.cos(driftAngle) * 5.0, 10.0,
            Math.sin(driftAngle) * 5.0);
        Location start = impact.clone().add(drift);
        Vector travel = impact.toVector().subtract(start.toVector());
        double distance = travel.length();
        Vector direction = travel.normalize();
        int flightTicks = Math.max(1, (int) Math.ceil(distance / 1.5));

        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (tick > flightTicks) {
                    landMeteor(casterId, world, impact, damage, damagedTargets);
                    cancel();
                    return;
                }
                Location point = start.clone().add(direction.clone().multiply(distance * tick / flightTicks));
                world.spawnParticle(Particle.FLAME, point, 5, 0.12, 0.12, 0.12, 0.01);
                world.spawnParticle(Particle.SMOKE, point, 2, 0.08, 0.08, 0.08, 0.01);
                if (tick == 0) world.playSound(start, Sound.ENTITY_BLAZE_SHOOT, 0.5f, 0.8f);
                tick++;
            }
        }.runTaskTimer(plugin, delayTicks, 1L);
    }

    private void landMeteor(java.util.UUID casterId, org.bukkit.World world, Location impact,
                            double damage, Set<java.util.UUID> damagedTargets) {
        if (!world.isChunkLoaded(impact.getBlockX() >> 4, impact.getBlockZ() >> 4)) return;
        world.spawnParticle(Particle.EXPLOSION, impact, 1);
        world.spawnParticle(Particle.FLAME, impact.clone().add(0, 0.2, 0), 28, 1.1, 0.25, 1.1, 0.03);
        world.spawnParticle(Particle.SMOKE, impact.clone().add(0, 0.2, 0), 16, 0.9, 0.3, 0.9, 0.02);
        world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 0.65f, 1.2f);

        Player caster = Bukkit.getPlayer(casterId);
        int affectedCount = 0;
        for (Entity entity : world.getNearbyEntities(impact, 2.6, 2.2, 2.6)) {
            if (!(entity instanceof Monster target) || !damagedTargets.add(target.getUniqueId())) continue;
            if (caster != null && caster.isOnline()) target.damage(damage, caster);
            else target.damage(damage);
            affectedCount++;
            if (caster != null && caster.isOnline()) {
                debugCast(caster, "arcane-field", "target-hit=" + target.getType()
                    + ", entityId=" + target.getUniqueId() + ", damage=" + damage
                    + ", healthAfter=" + Math.max(0.0, target.getHealth()));
            }
            target.setFireTicks(Math.max(target.getFireTicks(), 60));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, true, true, true));
        }
        if (caster != null && caster.isOnline()) {
            debugCast(caster, "arcane-field", "meteor-landed=" + impact.getBlockX() + ","
                + impact.getBlockY() + "," + impact.getBlockZ() + ", affected=" + affectedCount);
        }
    }

    private org.bukkit.Location getArcaneFieldCenter(Player player) {
        org.bukkit.Location eyeLocation = player.getEyeLocation();
        Vector direction = eyeLocation.getDirection();
        RayTraceResult blockHit = player.getWorld().rayTraceBlocks(eyeLocation, direction, 12.0);
        return blockHit == null ? eyeLocation.add(direction.multiply(12))
            : blockHit.getHitPosition().toLocation(player.getWorld());
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
        List<String> unsupportedActiveSkills = registry.skills().values().stream()
            .filter(skill -> !skill.passive() && !CAST_HANDLERS.contains(skill.id()))
            .map(SkillDefinitionLoader.SkillDefinition::id).toList();
        if (!unsupportedActiveSkills.isEmpty()) {
            throw new IOException("Active skills have no runtime cast handler: "
                + String.join(", ", unsupportedActiveSkills) + ". Remove or implement them before enabling.");
        }
        registry.skills().values().stream()
            .filter(skill -> skill.passive() && !IMPLEMENTED_PASSIVES.contains(skill.id()))
            .forEach(skill -> plugin.getLogger().warning("[Skills] Passive skill '" + skill.id()
                + "' has no runtime implementation; it remains unavailable and cannot be upgraded."));
        plugin.getLogger().info("[Skills] Registry ready: skills=" + registry.skills().size()
            + ", books=" + registry.books().size() + ", playableClasses=" + getPlayableClassIds().size()
            + ", castHandlers=" + CAST_HANDLERS.size() + ", passiveHandlers=" + IMPLEMENTED_PASSIVES.size());
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSkillBookUse(PlayerInteractEvent event) {
        if (!isSkillBookUseAction(event.getHand(), event.getAction())) return;
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
        if (!isSkillSupported(skill)) {
            player.sendMessage("§cKỹ năng trong sách hiện chưa được triển khai; sách chưa bị tiêu thụ.");
            return;
        }
        UUID playerId = player.getUniqueId();
        debugFeature("[RPG-DEBUG][ClassProgression] skill-learn player=" + player.getName()
            + ", skill=" + skill.id() + ", sourceClass=" + skill.classId()
            + ", eligibleClasses=" + classesEligibleForSkill(skill.classId()));
        if (!pendingBookUses.add(playerId)) {
            debugFeature("[RPG-DEBUG][SkillBook] use-skipped player=" + player.getName()
                + ", skill=" + skill.id() + ", reason=already-pending");
            return;
        }
        debugFeature("[RPG-DEBUG][SkillBook] learn-start player=" + player.getName()
            + ", skill=" + skill.id() + ", book=" + book.id());
        database.executeGameplayDatabaseTask("skill.book-learn player=" + player.getName()
                + " skill=" + skill.id(), () -> database.learnSkill(
                playerId.toString(), skill.id(), classesEligibleForSkill(skill.classId()),
                skill.requiredLevel(), Instant.now().toString()))
            .whenComplete((result, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            pendingBookUses.remove(playerId);
            if (failure != null) {
                plugin.getLogger().log(Level.SEVERE, "Could not save learned skill " + skill.id()
                    + " for " + player.getName() + ".", failure);
                if (player.isOnline()) {
                    player.sendMessage("§cKhông thể lưu kỹ năng. Sách chưa bị tiêu thụ; hãy thử lại sau.");
                }
                return;
            }
            debugFeature("[RPG-DEBUG][SkillBook] learn-result player=" + player.getName()
                + ", skill=" + skill.id() + ", result=" + result);
            if (result == PlayerProfileDatabaseManager.SkillLearnResult.LEARNED && !player.isOnline()) {
                database.executeGameplayDatabaseTask("skill.book-rollback player=" + player.getName()
                        + " skill=" + skill.id(), () -> database.rollbackNewSkillLearn(
                    playerId.toString(), skill.id())).whenComplete((rolledBack, rollbackFailure) -> {
                        if (rollbackFailure != null || !Boolean.TRUE.equals(rolledBack)) {
                            plugin.getLogger().log(Level.SEVERE, "Could not roll back a skill book learn "
                                + "after player " + player.getName() + " disconnected.", rollbackFailure);
                        }
                    });
                return;
            }
            if (!player.isOnline()) return;
            switch (result) {
                case LEARNED -> {
                    if (!bookId.equals(getBookId(player.getInventory().getItemInMainHand()))) {
                        plugin.getLogger().severe("Skill book disappeared during async learn for "
                            + player.getName() + "; learned skill will be rolled back.");
                        database.executeGameplayDatabaseTask("skill.book-rollback player=" + player.getName()
                                + " skill=" + skill.id(), () -> database.rollbackNewSkillLearn(
                            playerId.toString(), skill.id()));
                        player.sendMessage("§cKhông thể xác nhận sách trong tay. Kỹ năng đã được hoàn tác.");
                        return;
                    }
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
        }));
    }

    static boolean isSkillBookUseAction(EquipmentSlot hand, Action action) {
        return hand == EquipmentSlot.HAND
            && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK);
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
        debugFeature("[RPG-DEBUG][Command] command=/" + command.getName() + ", sender="
            + sender.getName() + ", args=" + String.join(" ", args));
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
        UUID playerId = player.getUniqueId();
        database.executeGameplayDatabaseTask("skill.command-list player=" + player.getName(), () -> {
            Optional<PlayerProfileDatabaseManager.PlayerProfile> profile =
                database.getPlayerProfile(playerId.toString());
            if (profile.isEmpty() || !profile.get().active()) return null;
            return new ProfileSkills(profile.get(), database.getLearnedSkills(playerId.toString()));
        }).whenComplete((data, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (failure != null) {
                plugin.getLogger().log(Level.SEVERE, "Could not list learned skills for "
                    + player.getName() + ".", failure);
                player.sendMessage("§cKhông thể tải danh sách kỹ năng. Hãy thử lại sau.");
                return;
            }
            if (data == null) {
                player.sendMessage("§cBạn cần tạo nhân vật RPG trước.");
                return;
            }
            player.sendMessage("§6§lKỹ năng nhân vật §7| §e" + data.profile().selectedClass()
                + " §7| Cấp " + data.profile().level());
            if (data.learned().isEmpty()) {
                player.sendMessage("§7Chưa học kỹ năng nào. Dùng sách kỹ năng phù hợp với class của bạn.");
                return;
            }
            for (PlayerProfileDatabaseManager.SkillProgress progress : data.learned()) {
                SkillDefinitionLoader.SkillDefinition skill = registry.skills().get(progress.skillId());
                String name = skill == null ? progress.skillId() : skill.displayName();
                player.sendMessage("§a• §f" + name + " §7- Rank " + progress.rank());
            }
        }));
        return true;
    }

    private boolean handleSkillBookCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cBạn không có quyền dùng lệnh này.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("config")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cMở giao diện cấu hình sách cần thực hiện trong game.");
                return true;
            }
            plugin.getCharacterSelectionManager().openStarterBookAdminMenu(player);
            return true;
        }
        if (args.length != 3 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage("§eDùng: /vnskillbook give <người_chơi> <book-id> hoặc /vnskillbook config");
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
        if (!isSkillSupported(registry.skills().get(book.skillId()))) {
            sender.sendMessage("§cSkill này chưa được triển khai nên chưa thể cấp sách.");
            return true;
        }
        if (!giveSkillBookTo(target, book.id())) {
            sender.sendMessage("§cTúi đồ người chơi đã đầy; không có sách nào được cấp.");
            return true;
        }
        sender.sendMessage("§aĐã cấp sách §e" + book.id() + "§a cho §f" + target.getName() + "§a.");
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
        meta.getPersistentDataContainer().set(rpgSellValueKey, PersistentDataType.LONG, book.sellValue());
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
        if (args.length == 1) return List.of("give", "config").stream()
            .filter(value -> value.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            String prefix = args[1].toLowerCase(java.util.Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(name -> name.toLowerCase(java.util.Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            String prefix = args[2].toLowerCase(java.util.Locale.ROOT);
            return getSkillBooks().stream().map(SkillDefinitionLoader.SkillBookDefinition::id)
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

    private static final class PromotionHolder implements InventoryHolder {
        private final UUID viewer;
        private final String sourceClassId;
        private final boolean confirmation;
        private final String targetClassId;
        private final boolean history;
        private final Map<Integer, String> choices = new HashMap<>();
        private Inventory inventory;

        private PromotionHolder(UUID viewer, String sourceClassId, boolean confirmation, String targetClassId) {
            this(viewer, sourceClassId, confirmation, targetClassId, false);
        }

        private PromotionHolder(UUID viewer, String sourceClassId, boolean confirmation, String targetClassId,
                                boolean history) {
            this.viewer = viewer;
            this.sourceClassId = sourceClassId;
            this.confirmation = confirmation;
            this.targetClassId = targetClassId;
            this.history = history;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class QuickCastHolder implements InventoryHolder {
        private final QuickCastScreen screen;
        private final int selectedSlot;
        private final String selectedSkillId;
        private Inventory inventory;
        private Map<Integer, String> assignedSlots = Map.of();

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
