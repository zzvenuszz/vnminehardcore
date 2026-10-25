package com.vnmine.hardcore.managers;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.vnmine.hardcore.VnMineHardcore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class EconomyManager implements Listener, CommandExecutor, TabCompleter, AutoCloseable {
    private static final String ADMIN_PERMISSION = "vnmine.command.vncoin.admin";
    private static final String HISTORY_OTHERS_PERMISSION = "vnmine.command.vncoin.history.others";
    private static final long MAX_ADMIN_TRANSACTION = 1_000_000L;
    private static final List<String> TIERS = List.of("I", "II", "III", "IV");
    private static final Set<String> RARE_MATERIALS = Set.of("NETHER_STAR", "ELYTRA", "TOTEM_OF_UNDYING",
        "TRIDENT", "DRAGON_EGG", "ENCHANTED_GOLDEN_APPLE", "HEART_OF_THE_SEA", "BEACON", "ANCIENT_DEBRIS");

    private final VnMineHardcore plugin;
    private final PlayerProfileDatabaseManager database;
    private final NamespacedKey coinDropIdKey;
    private final NamespacedKey coinAmountKey;
    private final NamespacedKey coinBossDropKey;
    private final NamespacedKey coinCriticalKey;
    private final NamespacedKey itemNameModeKey;
    private final NamespacedKey spawnEligibleKey;
    private final NamespacedKey armyUnitKey;
    private final NamespacedKey armySummonKey;
    private final NamespacedKey merchantOfferKey;
    private final NamespacedKey merchantDeliveryIdKey;
    private final ConcurrentMap<UUID, ItemNameMode> itemNameModes = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, ItemNameCategory> itemNameCategories = new ConcurrentHashMap<>();
    private final Set<UUID> pendingClaims = new HashSet<>();
    private final Map<UUID, Long> nextBossDropClaimAt = new HashMap<>();
    private final Set<CreatureSpawnEvent.SpawnReason> allowedSpawnReasons = new HashSet<>();
    private final Set<UUID> pendingMerchantPurchases = ConcurrentHashMap.newKeySet();
    private final Set<UUID> reconcilingMerchantDeliveries = ConcurrentHashMap.newKeySet();
    private final Set<String> lockedMerchantDeliveries = ConcurrentHashMap.newKeySet();
    private final Map<String, MobTier> tiersByEntity = new HashMap<>();
    private final PacketAdapter itemNamePacketListener;
    private boolean economyEnabled;
    private boolean mobDropsEnabled;
    private double dropChance;
    private double criticalDropChance;
    private int criticalMinimum;
    private int criticalMaximum;
    private boolean bossDropsEnabled;
    private int bossTotalCoins;
    private int bossCoinsPerDrop;
    private double bossScatterRadius;
    private int inflationWindowDays;

    public EconomyManager(VnMineHardcore plugin, PlayerProfileDatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
        coinDropIdKey = new NamespacedKey(plugin, "vncoin_drop_id");
        coinAmountKey = new NamespacedKey(plugin, "vncoin_amount");
        coinBossDropKey = new NamespacedKey(plugin, "vncoin_boss_drop");
        coinCriticalKey = new NamespacedKey(plugin, "vncoin_critical_drop");
        itemNameModeKey = new NamespacedKey(plugin, "ground_item_name_mode");
        spawnEligibleKey = new NamespacedKey(plugin, "vncoin_spawn_eligible");
        armyUnitKey = new NamespacedKey(plugin, "army_unit");
        armySummonKey = new NamespacedKey(plugin, "army_summoned_minion");
        merchantOfferKey = new NamespacedKey(plugin, "merchant_offer_id");
        merchantDeliveryIdKey = new NamespacedKey(plugin, "merchant_delivery_id");
        itemNamePacketListener = new PacketAdapter(plugin, ListenerPriority.NORMAL,
                PacketType.Play.Server.ENTITY_METADATA) {
            @Override
            public void onPacketSending(PacketEvent event) {
                filterItemNameForViewer(event);
            }
        };
        ProtocolLibrary.getProtocolManager().addPacketListener(itemNamePacketListener);
        reloadConfiguration();
        for (Player player : Bukkit.getOnlinePlayers()) loadItemNameMode(player);
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item item) trackGroundItem(item);
            }
        }
        plugin.getLogger().info("[Economy] Initialized: enabled=" + economyEnabled
            + ", mobDrops=" + mobDropsEnabled + ", chance=" + dropChance
            + ", tiers=" + tiersByEntity.size() + ", icon=" + Material.SUNFLOWER.getKey());
    }

    public void reloadConfiguration() {
        var config = plugin.getConfig();
        boolean newEconomyEnabled = config.getBoolean("economy.enabled", true);
        boolean newMobDropsEnabled = config.getBoolean("economy.mob-drops.enabled", true);
        double newDropChance = config.getDouble("economy.mob-drops.chance", 0.30);
        if (!Double.isFinite(newDropChance) || newDropChance < 0.0 || newDropChance > 1.0) {
            throw new IllegalArgumentException("economy.mob-drops.chance must be finite and between 0 and 1.");
        }
        double newCriticalDropChance = config.getDouble("economy.mob-drops.critical-drop-chance", 0.05);
        int newCriticalMinimum = config.getInt("economy.mob-drops.critical-min", 5);
        int newCriticalMaximum = config.getInt("economy.mob-drops.critical-max", 7);
        if (!Double.isFinite(newCriticalDropChance) || newCriticalDropChance < 0.0
                || newCriticalDropChance > 1.0 || newCriticalMinimum < 1
                || newCriticalMaximum > 10 || newCriticalMinimum > newCriticalMaximum) {
            throw new IllegalArgumentException("Critical VNCOIN drop settings are outside supported bounds.");
        }
        boolean newBossDropsEnabled = config.getBoolean("economy.boss-drops.enabled", true);
        int newBossTotalCoins = config.getInt("economy.boss-drops.total-coins", 100);
        int newBossCoinsPerDrop = config.getInt("economy.boss-drops.coins-per-drop", 5);
        double newBossScatterRadius = config.getDouble("economy.boss-drops.scatter-radius", 5.0);
        int newInflationWindowDays = config.getInt("economy.inflation.window-days", 30);
        if (newBossTotalCoins < 1 || newBossTotalCoins > 10_000 || newBossCoinsPerDrop < 1
                || newBossCoinsPerDrop > 10 || !Double.isFinite(newBossScatterRadius)
                || newBossScatterRadius < 0.0 || newBossScatterRadius > 16.0) {
            throw new IllegalArgumentException("Boss VNCOIN drop settings are outside supported bounds.");
        }
        if (newInflationWindowDays < 1 || newInflationWindowDays > 365) {
            throw new IllegalArgumentException("economy.inflation.window-days must be between 1 and 365.");
        }

        Set<CreatureSpawnEvent.SpawnReason> newAllowedSpawnReasons = new HashSet<>();
        ConfigurationSection drops = config.getConfigurationSection("economy.mob-drops");
        List<String> spawnReasons = drops == null ? List.of("NATURAL", "CHUNK_GEN")
            : drops.getStringList("eligible-spawn-reasons");
        for (String rawReason : spawnReasons) {
            try {
                newAllowedSpawnReasons.add(CreatureSpawnEvent.SpawnReason.valueOf(
                    rawReason.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("[Economy] Ignoring unknown spawn reason '" + rawReason + "'.");
            }
        }
        if (newAllowedSpawnReasons.isEmpty()) {
            throw new IllegalArgumentException("economy.mob-drops.eligible-spawn-reasons must contain a valid reason.");
        }

        Map<String, MobTier> newTiersByEntity = new HashMap<>();
        Set<String> duplicateGuard = new HashSet<>();
        for (String tier : TIERS) {
            String basePath = "economy.mob-drops.tiers." + tier;
            int minimum = config.getInt(basePath + ".min", -1);
            int maximum = config.getInt(basePath + ".max", -1);
            if (minimum < 1 || maximum > 10 || minimum > maximum) {
                throw new IllegalArgumentException(basePath + " amount range must satisfy 1 <= min <= max <= 10.");
            }
            List<String> entityNames = config.getStringList(basePath + ".entities");
            if (entityNames.isEmpty()) {
                throw new IllegalArgumentException(basePath + ".entities must not be empty.");
            }
            MobTier mobTier = new MobTier(tier, minimum, maximum);
            for (String rawEntity : entityNames) {
                String entityName = rawEntity.toUpperCase(Locale.ROOT);
                try {
                    org.bukkit.entity.EntityType.valueOf(entityName);
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException(basePath + " contains unknown entity type '" + rawEntity + "'.",
                        exception);
                }
                if (!duplicateGuard.add(entityName)) {
                    throw new IllegalArgumentException("Mob drop entity '" + entityName
                        + "' appears in more than one tier.");
                }
                newTiersByEntity.put(entityName, mobTier);
            }
        }
        economyEnabled = newEconomyEnabled;
        mobDropsEnabled = newMobDropsEnabled;
        dropChance = newDropChance;
        criticalDropChance = newCriticalDropChance;
        criticalMinimum = newCriticalMinimum;
        criticalMaximum = newCriticalMaximum;
        bossDropsEnabled = newBossDropsEnabled;
        bossTotalCoins = newBossTotalCoins;
        bossCoinsPerDrop = newBossCoinsPerDrop;
        bossScatterRadius = newBossScatterRadius;
        inflationWindowDays = newInflationWindowDays;
        allowedSpawnReasons.clear();
        allowedSpawnReasons.addAll(newAllowedSpawnReasons);
        tiersByEntity.clear();
        tiersByEntity.putAll(newTiersByEntity);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        boolean eligible = allowedSpawnReasons.contains(event.getSpawnReason());
        event.getEntity().getPersistentDataContainer().set(spawnEligibleKey,
            PersistentDataType.BYTE, eligible ? (byte) 1 : (byte) 0);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!economyEnabled || !mobDropsEnabled || event.getEntity() instanceof Player) return;
        LivingEntity mob = event.getEntity();
        Player killer = mob.getKiller();
        MobTier tier = tiersByEntity.get(mob.getType().name());
        if (tier == null) return;
        if (killer == null || !isEligibleSpawn(mob) || isSpecialMob(mob)) {
            debugDrop("[RPG-DEBUG][VNCOIN] drop-skipped mob=" + mob.getType() + ", killer="
                + (killer == null ? "none" : killer.getName()) + ", reason=" + dropSkipReason(mob, killer, tier));
            return;
        }

        double roll = ThreadLocalRandom.current().nextDouble();
        if (roll >= dropChance) {
            debugDrop("[RPG-DEBUG][VNCOIN] roll-failed player=" + killer.getName() + ", mob="
                + mob.getType() + ", tier=" + tier.id() + ", roll=" + roll + ", chance=" + dropChance);
            return;
        }
        boolean critical = ThreadLocalRandom.current().nextDouble() < criticalDropChance;
        int amount = critical
            ? ThreadLocalRandom.current().nextInt(criticalMinimum, criticalMaximum + 1)
            : ThreadLocalRandom.current().nextInt(tier.minimum(), tier.maximum() + 1);
        ItemStack coin = createCoinDrop(amount, critical, false);
        Item item = mob.getWorld().dropItemNaturally(mob.getLocation(), coin);
        item.setPickupDelay(10);
        item.setCustomName(coin.getItemMeta().getDisplayName());
        item.setCustomNameVisible(true);
        item.setGlowing(critical);
        debugDrop("[RPG-DEBUG][VNCOIN] drop-created player=" + killer.getName() + ", mob="
            + mob.getType() + ", tier=" + tier.id() + ", critical=" + critical + ", amount=" + amount
            + ", dropId=" + item.getItemStack().getItemMeta().getPersistentDataContainer()
                .get(coinDropIdKey, PersistentDataType.STRING));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCoinPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!hasCoinTag(event.getItem().getItemStack())) {
            Item pickedItem = event.getItem();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!pickedItem.isValid()) itemNameCategories.remove(pickedItem.getEntityId());
            });
            return;
        }
        event.setCancelled(true);
        beginCoinClaim(player, event.getItem());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCoinAttemptPickup(PlayerAttemptPickupItemEvent event) {
        Item item = event.getItem();
        if (!hasCoinTag(item.getItemStack())) return;
        event.setCancelled(true);
        beginCoinClaim(event.getPlayer(), item);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        nextBossDropClaimAt.remove(event.getPlayer().getUniqueId());
        itemNameModes.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        loadItemNameMode(event.getPlayer());
        reconcileMerchantDeliveries(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item itemEntity = event.getEntity();
        ItemStack stack = itemEntity.getItemStack();
        if (stack.getType().isAir()) return;
        trackGroundItem(itemEntity);
        ItemMeta meta = stack.getItemMeta();
        String visibleName;
        if (meta != null && meta.hasDisplayName()) {
            visibleName = sanitizeVisibleName(meta.getDisplayName());
            if (!visibleName.equals(meta.getDisplayName())) {
                meta.setDisplayName(visibleName);
                stack.setItemMeta(meta);
                itemEntity.setItemStack(stack);
            }
        } else {
            visibleName = "§e" + stack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
            if (meta != null) {
                meta.setDisplayName(visibleName);
                stack.setItemMeta(meta);
                itemEntity.setItemStack(stack);
            }
        }
        itemEntity.setCustomName(visibleName);
        itemEntity.setCustomNameVisible(true);
    }

    @EventHandler
    public void onItemEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof Item item) trackGroundItem(item);
        }
    }

    @EventHandler
    public void onItemEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof Item item) itemNameCategories.remove(item.getEntityId());
        }
    }

    private void trackGroundItem(Item item) {
        ItemStack stack = item.getItemStack();
        ItemMeta meta = stack.getItemMeta();
        boolean pluginTagged = meta != null && meta.getPersistentDataContainer().getKeys().stream()
            .anyMatch(key -> key.getNamespace().equalsIgnoreCase(plugin.getName()));
        boolean hasPluginCoinTag = hasCoinTag(stack);
        boolean customName = meta != null && meta.hasDisplayName()
            && !isGenericGroundItemLabel(meta.getDisplayName(), stack.getType());
        boolean hasLore = meta != null && meta.hasLore() && meta.getLore() != null && !meta.getLore().isEmpty();
        boolean enchanted = meta != null && meta.hasEnchants();
        boolean rpg = pluginTagged || customName || hasLore || enchanted;
        boolean rare = hasPluginCoinTag && (meta.getPersistentDataContainer().has(coinCriticalKey, PersistentDataType.BYTE)
                || meta.getPersistentDataContainer().has(coinBossDropKey, PersistentDataType.BYTE))
            || enchanted || hasLore || customName || isRareMaterial(stack.getType());
        itemNameCategories.put(item.getEntityId(), new ItemNameCategory(rpg, rare));
    }

    private boolean isGenericGroundItemLabel(String displayName, Material material) {
        String plain = org.bukkit.ChatColor.stripColor(displayName);
        if (plain == null) return false;
        String expected = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return plain.trim().equalsIgnoreCase(expected);
    }

    private boolean isRareMaterial(Material material) {
        String name = material.name();
        return name.contains("NETHERITE") || name.contains("DIAMOND") || name.contains("EMERALD")
            || RARE_MATERIALS.contains(name);
    }

    private void filterItemNameForViewer(PacketEvent event) {
        int entityId = event.getPacket().getIntegers().read(0);
        ItemNameCategory category = itemNameCategories.get(entityId);
        if (category == null) return;
        ItemNameMode mode = itemNameModes.getOrDefault(event.getPlayer().getUniqueId(), ItemNameMode.ALL);
        boolean show = switch (mode) {
            case OFF -> false;
            case ALL -> true;
            case RPG_ONLY -> category.rpg();
            case RARE_ONLY -> category.rare();
        };
        if (show) return;

        List<WrappedDataValue> dataValues = new ArrayList<>(
            event.getPacket().getDataValueCollectionModifier().read(0));
        boolean nameFound = false;
        boolean visibilityFound = false;
        for (WrappedDataValue dataValue : dataValues) {
            if (dataValue.getIndex() == 2) {
                dataValue.setValue(Optional.empty());
                nameFound = true;
            } else if (dataValue.getIndex() == 3) {
                dataValue.setValue(false);
                visibilityFound = true;
            }
        }
        if (!nameFound) {
            dataValues.add(new WrappedDataValue(2,
                WrappedDataWatcher.Registry.getChatComponentSerializer(true), Optional.empty()));
        }
        if (!visibilityFound) {
            dataValues.add(new WrappedDataValue(3, WrappedDataWatcher.Registry.get(Boolean.class), false));
        }
        event.getPacket().getDataValueCollectionModifier().write(0, dataValues);
    }

    private ItemNameMode loadItemNameMode(Player player) {
        String saved = player.getPersistentDataContainer().get(itemNameModeKey, PersistentDataType.STRING);
        ItemNameMode mode;
        try {
            mode = saved == null ? ItemNameMode.ALL : ItemNameMode.valueOf(saved);
        } catch (IllegalArgumentException exception) {
            mode = ItemNameMode.ALL;
        }
        itemNameModes.put(player.getUniqueId(), mode);
        return mode;
    }

    public ItemNameMode getItemNameMode(Player player) {
        ItemNameMode mode = itemNameModes.get(player.getUniqueId());
        return mode == null ? loadItemNameMode(player) : mode;
    }

    public void setItemNameMode(Player player, ItemNameMode mode) {
        player.getPersistentDataContainer().set(itemNameModeKey, PersistentDataType.STRING, mode.name());
        itemNameModes.put(player.getUniqueId(), mode);
        refreshItemNames(player);
    }

    private void refreshItemNames(Player player) {
        int sent = 0;
        for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), 40, 32, 40)) {
            if (!(entity instanceof Item item) || !item.isValid()) continue;
            trackGroundItem(item);
            ProtocolLibrary.getProtocolManager().updateEntity(item, List.of(player));
            if (++sent >= 256) break;
        }
    }

    public String getItemNameModeLabel(ItemNameMode mode) {
        return switch (mode) {
            case OFF -> "§cTắt toàn bộ";
            case ALL -> "§aHiển thị toàn bộ";
            case RPG_ONLY -> "§dChỉ hiển thị item RPG";
            case RARE_ONLY -> "§6Chỉ hiển thị item quý hiếm";
        };
    }

    public static String sanitizeVisibleName(String name) {
        if (name == null) return "";
        return name.replace("§0", "§8").replace("&0", "§8")
            .replaceAll("(?i)<black>", "<dark_gray>")
            .replaceAll("(?i)<#000000>", "<dark_gray>");
    }

    public void dropBossCoins(org.bukkit.Location bossLocation, String bossName, UUID encounterId) {
        if (!economyEnabled || !bossDropsEnabled || bossLocation == null || bossLocation.getWorld() == null) return;
        if (encounterId == null) throw new IllegalArgumentException("Boss encounter id cannot be null.");
        int remaining = bossTotalCoins;
        int drops = 0;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        while (remaining > 0) {
            int amount = Math.min(remaining, bossCoinsPerDrop);
            double angle = random.nextDouble(0.0, Math.PI * 2.0);
            double distance = bossScatterRadius <= 0.0 ? 0.0
                : random.nextDouble(0.5, bossScatterRadius + 0.5);
            org.bukkit.Location spawn = bossLocation.clone().add(
                Math.cos(angle) * distance, 0.4 + random.nextDouble(0.5), Math.sin(angle) * distance);
            UUID dropId = UUID.nameUUIDFromBytes((encounterId + ":vncoin:" + drops)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Item drop = bossLocation.getWorld().dropItem(spawn, createCoinDrop(amount, false, true, dropId));
            double speed = random.nextDouble(0.28, 0.62);
            drop.setVelocity(new org.bukkit.util.Vector(Math.cos(angle) * speed,
                random.nextDouble(0.28, 0.55), Math.sin(angle) * speed));
            drop.setPickupDelay(random.nextInt(12, 31));
            remaining -= amount;
            drops++;
        }
        plugin.getLogger().info("[Economy] Boss VNCOIN reward: boss=" + sanitizeVisibleName(bossName)
            + ", total=" + bossTotalCoins + ", drops=" + drops + ", perDrop=" + bossCoinsPerDrop
            + ", scatterRadius=" + bossScatterRadius + ".");
    }

    private void beginCoinClaim(Player player, Item itemEntity) {
        ItemStack stack = itemEntity.getItemStack();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(coinDropIdKey, PersistentDataType.STRING)) return;

        String rawId = meta.getPersistentDataContainer().get(coinDropIdKey, PersistentDataType.STRING);
        Integer amount = meta.getPersistentDataContainer().get(coinAmountKey, PersistentDataType.INTEGER);
        UUID dropId;
        try {
            dropId = UUID.fromString(rawId);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("[Economy] Removing malformed VNCOIN item id='" + rawId + "'.");
            itemEntity.remove();
            return;
        }
        if (stack.getType() != Material.SUNFLOWER || amount == null || amount < 1 || amount > 10) {
            plugin.getLogger().warning("[Economy] Removing malformed VNCOIN item dropId=" + dropId
                + ", material=" + stack.getType() + ", amount=" + amount + ".");
            itemEntity.remove();
            return;
        }
        boolean bossDrop = meta.getPersistentDataContainer().has(coinBossDropKey, PersistentDataType.BYTE);
        if (bossDrop) {
            long now = System.currentTimeMillis();
            long nextAllowedAt = nextBossDropClaimAt.getOrDefault(player.getUniqueId(), 0L);
            if (now < nextAllowedAt) return;
            nextBossDropClaimAt.put(player.getUniqueId(), now + 750L);
        }
        if (!pendingClaims.add(dropId)) return;

        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        String transactionSource = bossDrop ? "boss-drop" : "mob-drop";
        LocationSnapshot origin = new LocationSnapshot(itemEntity.getLocation().clone(), stack.clone());
        debug("[RPG-DEBUG][VNCOIN] pickup-start player=" + playerName + ", amount=" + amount
            + ", dropId=" + dropId);
        database.executeGameplayDatabaseTask("wallet.mob-drop-claim player=" + playerName,
                () -> database.applyWalletTransaction(playerId.toString(), amount,
                    transactionSource + ":" + dropId, transactionSource, playerId.toString(),
                    transactionSource + " " + dropId, Instant.now().toString()))
            .whenComplete((result, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                pendingClaims.remove(dropId);
                if (failure != null) {
                    plugin.getLogger().log(Level.SEVERE, "[Economy] Could not claim VNCOIN drop "
                        + dropId + " for " + playerName + "; item will be preserved.", failure);
                    restoreDroppedItem(itemEntity, origin);
                    if (player.isOnline()) player.sendMessage("§cChưa nhận được VNCOIN vì lỗi lưu trữ. Vật phẩm vẫn được giữ.");
                    return;
                }
                if (itemEntity.isValid()) itemEntity.remove();
                itemNameCategories.remove(itemEntity.getEntityId());
                if (result.status() == PlayerProfileDatabaseManager.WalletTransactionStatus.APPLIED) {
                    debug("[RPG-DEBUG][VNCOIN] pickup-result player=" + playerName + ", amount=" + amount
                        + ", balance=" + result.balance() + ", dropId=" + dropId + ", credited=true");
                    if (player.isOnline()) {
                        player.sendActionBar("§6+" + amount + " VNCOIN §8• §eVí: " + result.balance());
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.45f, 1.35f);
                    }
                } else {
                    plugin.getLogger().warning("[Economy] Duplicate VNCOIN drop claim rejected: dropId="
                        + dropId + ", player=" + playerName + ".");
                    debug("[RPG-DEBUG][VNCOIN] pickup-result player=" + playerName
                        + ", dropId=" + dropId + ", status=" + result.status() + ", credited=false");
                }
            }));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        if (hasCoinTag(event.getItem().getItemStack())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        if (hasCoinTag(event.getItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCoinMerge(ItemMergeEvent event) {
        if (hasCoinTag(event.getEntity().getItemStack()) || hasCoinTag(event.getTarget().getItemStack())) {
            event.setCancelled(true);
            return;
        }
        itemNameCategories.remove(event.getEntity().getEntityId());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getTarget().isValid()) trackGroundItem(event.getTarget());
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCoinDespawn(ItemDespawnEvent event) {
        itemNameCategories.remove(event.getEntity().getEntityId());
        if (hasCoinTag(event.getEntity().getItemStack())
                && event.getEntity().getItemStack().getItemMeta().getPersistentDataContainer()
                    .has(coinDropIdKey, PersistentDataType.STRING)) {
            String rawId = event.getEntity().getItemStack().getItemMeta().getPersistentDataContainer()
                .get(coinDropIdKey, PersistentDataType.STRING);
            try {
                if (pendingClaims.contains(UUID.fromString(rawId))) event.setCancelled(true);
            } catch (IllegalArgumentException ignored) {
                // Malformed tagged items are cleaned up on pickup; do not block ordinary despawn.
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTaggedCoinDrop(PlayerDropItemEvent event) {
        if (hasCoinTag(event.getItemDrop().getItemStack())) event.setCancelled(true);
        if (isLockedMerchantDelivery(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLockedMerchantDeliveryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !containsLockedMerchantDelivery(player)) return;
        event.setCancelled(true);
        player.sendMessage("§7Vật phẩm mua đang được xác nhận an toàn, vui lòng chờ.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLockedMerchantDeliveryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !containsLockedMerchantDelivery(player)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLockedMerchantDeliveryInteract(PlayerInteractEvent event) {
        if (isLockedMerchantDelivery(event.getItem())
                || isLockedMerchantDelivery(event.getPlayer().getInventory().getItemInOffHand())) {
            event.setCancelled(true);
        }
    }

    public void openWalletMenu(Player player) {
        reconcileMerchantDeliveries(player);
        String uuid = player.getUniqueId().toString();
        String playerName = player.getName();
        player.sendMessage("§7Đang tải ví VNCOIN...");
        database.executeGameplayDatabaseTask("wallet.menu player=" + playerName, () ->
            new WalletView(database.getWalletBalance(uuid), database.getWalletHistory(uuid, 8)))
            .whenComplete((view, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (failure != null) {
                    plugin.getLogger().log(Level.SEVERE, "[Economy] Could not load wallet menu for "
                        + playerName + ".", failure);
                    player.sendMessage("§cKhông thể tải ví VNCOIN lúc này.");
                    return;
                }
                WalletMenuHolder holder = new WalletMenuHolder(player.getUniqueId());
                Inventory inventory = Bukkit.createInventory(holder, 27, "VnMine • Ví VNCOIN");
                holder.inventory = inventory;
                inventory.setItem(11, menuItem(Material.SUNFLOWER, "§6Ví VNCOIN: §e" + view.balance(),
                    List.of("§7Đơn vị tiền tệ của VnMine.", "§7Số dư được lưu an toàn phía máy chủ.")));
                List<String> historyLore = new ArrayList<>();
                if (view.history().isEmpty()) {
                    historyLore.add("§7Chưa có giao dịch.");
                } else {
                    for (PlayerProfileDatabaseManager.WalletLedgerEntry entry : view.history()) {
                        historyLore.add((entry.delta() > 0 ? "§a+" : "§c") + entry.delta()
                            + " §6VNCOIN §8• §7" + entry.source());
                    }
                }
                inventory.setItem(15, menuItem(Material.BOOK, "§eGiao dịch gần đây", historyLore));
                inventory.setItem(22, menuItem(Material.ARROW, "§7Về menu chính", List.of()));
                player.openInventory(inventory);
                debug("[RPG-DEBUG][VNCOIN] wallet-menu player=" + playerName + ", balance=" + view.balance()
                    + ", historyEntries=" + view.history().size());
            }));
    }

    @EventHandler
    public void onWalletMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof WalletMenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.viewer.equals(player.getUniqueId())
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (event.getRawSlot() == 22 && plugin.getMercenaryManager() != null) {
            plugin.getMercenaryManager().openMainMenu(player);
        }
    }

    public void openMerchantMenu(Player player) {
        if (!economyEnabled || !plugin.getConfig().getBoolean("economy.merchant.enabled", true)) {
            player.sendMessage("§cCửa hàng VNCOIN hiện đang đóng.");
            return;
        }
        openMerchantInventory(player, "VnMine • Thương nhân", loadMerchantOffers());
    }

    public void openCustomMerchantMenu(Player player, String merchantId, String title,
                                       List<CustomMerchantOffer> customOffers) {
        if (!economyEnabled || !plugin.getConfig().getBoolean("economy.merchant.enabled", true)) {
            player.sendMessage("§cCửa hàng VNCOIN hiện đang đóng.");
            return;
        }
        if (!plugin.getConfig().getBoolean("economy.merchant.custom.enabled", true)) {
            player.sendMessage("§cCửa hàng NPC tùy chỉnh hiện đang tắt.");
            return;
        }
        List<MerchantOffer> offers = new ArrayList<>();
        for (int index = 0; index < customOffers.size() && index < 45; index++) {
            CustomMerchantOffer offer = customOffers.get(index);
            ItemStack item = offer.item().clone();
            ItemMeta meta = item.getItemMeta();
            String name = meta != null && meta.hasDisplayName() ? meta.getDisplayName()
                : item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
            offers.add(new MerchantOffer(merchantId + "-" + index, item, offer.price(), name));
        }
        openMerchantInventory(player, title, offers);
    }

    private void openMerchantInventory(Player player, String title, List<MerchantOffer> offers) {
        if (offers.isEmpty()) {
            player.sendMessage("§cCửa hàng hiện chưa có mặt hàng nào.");
            return;
        }
        MerchantMenuHolder holder = new MerchantMenuHolder(player.getUniqueId(), List.copyOf(offers));
        Inventory inventory = Bukkit.createInventory(holder, 54, title);
        holder.inventory = inventory;
        renderMerchantPage(inventory, holder);
        player.openInventory(inventory);
    }

    private void renderMerchantPage(Inventory inventory, MerchantMenuHolder holder) {
        holder.offers.clear();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, menuItem(Material.GRAY_STAINED_GLASS_PANE, "§r", List.of()));
        }
        int startIndex = holder.page * 44;
        for (int slot = 0; slot < 44 && startIndex + slot < holder.allOffers.size(); slot++) {
            MerchantOffer offer = holder.allOffers.get(startIndex + slot);
            ItemStack display = offer.item().clone();
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                if (!meta.hasDisplayName()) meta.setDisplayName("§f" + offer.name());
                List<String> lore = meta.hasLore() && meta.getLore() != null
                    ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add("§7Giá: §6" + offer.price() + " VNCOIN");
                lore.add("§eNhấn để mua.");
                meta.setLore(lore);
                meta.getPersistentDataContainer().set(merchantOfferKey, PersistentDataType.STRING, offer.id());
                display.setItemMeta(meta);
            }
            inventory.setItem(slot, display);
            holder.offers.put(slot, offer);
        }
        int pages = Math.max(1, (holder.allOffers.size() + 43) / 44);
        inventory.setItem(44, menuItem(Material.GOLD_INGOT, "§6Trang " + (holder.page + 1) + "/" + pages,
            List.of("§7Danh mục hàng hóa VNCOIN.")));
        if (holder.page > 0) inventory.setItem(45,
            menuItem(Material.ARROW, "§eTrang trước", List.of("§7Quay lại trang " + holder.page + ".")));
        inventory.setItem(49, menuItem(Material.BARRIER, "§cĐóng cửa hàng", List.of()));
        if (holder.page + 1 < pages) inventory.setItem(53,
            menuItem(Material.ARROW, "§eTrang sau", List.of("§7Mở trang " + (holder.page + 2) + ".")));
    }

    @EventHandler
    public void onMerchantMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MerchantMenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.viewer.equals(player.getUniqueId())
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (event.getRawSlot() == 49) {
            player.closeInventory();
            return;
        }
        if (event.getRawSlot() == 45 && holder.page > 0) {
            holder.page--;
            renderMerchantPage(event.getView().getTopInventory(), holder);
            return;
        }
        if (event.getRawSlot() == 53 && (holder.page + 1) * 44 < holder.allOffers.size()) {
            holder.page++;
            renderMerchantPage(event.getView().getTopInventory(), holder);
            return;
        }
        MerchantOffer offer = holder.offers.get(event.getRawSlot());
        if (offer == null) return;
        purchaseMerchantOffer(player, offer);
    }

    private List<MerchantOffer> loadMerchantOffers() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("economy.merchant.offers");
        if (section == null) return List.of();
        List<MerchantOffer> offers = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null || !id.matches("[a-z0-9_-]{1,48}")) {
                plugin.getLogger().warning("[Economy][Merchant] Invalid offer id/configuration: " + id);
                continue;
            }
            Material material = Material.matchMaterial(entry.getString("material", ""));
            int amount = entry.getInt("amount", 1);
            long price = entry.getLong("price", 0L);
            String name = entry.getString("name", id);
            if (material == null || !material.isItem() || amount < 1 || amount > material.getMaxStackSize()
                    || price < 1 || price > MAX_ADMIN_TRANSACTION || name == null || name.isBlank()
                    || name.length() > 48) {
                plugin.getLogger().warning("[Economy][Merchant] Ignoring invalid offer '" + id
                    + "': material/item, amount, price or name is outside supported bounds.");
                continue;
            }
            ItemStack item = new ItemStack(material, amount);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§f" + sanitizeVisibleName(name));
                item.setItemMeta(meta);
            }
            offers.add(new MerchantOffer(id, item, price, sanitizeVisibleName(name)));
        }
        if (offers.size() > 1_000) {
            plugin.getLogger().warning("[Economy][Merchant] Config has " + offers.size()
                + " valid offers; only the first 1000 are loaded.");
        }
        return List.copyOf(offers.subList(0, Math.min(offers.size(), 1_000)));
    }

    private void purchaseMerchantOffer(Player player, MerchantOffer offer) {
        UUID playerId = player.getUniqueId();
        if (pendingMerchantPurchases.contains(playerId)) {
            player.sendMessage("§eĐang xử lý giao dịch trước đó, vui lòng chờ.");
            return;
        }
        if (!pendingMerchantPurchases.add(playerId)) return;
        String transactionId = "merchant:" + UUID.randomUUID();
        byte[] purchaseItemData = offer.item().serializeAsBytes();
        player.sendMessage("§7Đang xử lý giao dịch VNCOIN...");
        database.executeGameplayDatabaseTask("wallet.merchant-buy player=" + player.getName()
                + " offer=" + offer.id(),
            () -> database.purchaseWalletItem(playerId.toString(), offer.price(), transactionId,
                "merchant:" + offer.id(), playerId.toString(), "Mua " + offer.name(), Instant.now().toString(),
                purchaseItemData))
            .whenComplete((result, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (failure != null) {
                    pendingMerchantPurchases.remove(playerId);
                    plugin.getLogger().log(Level.SEVERE, "[Economy][Merchant] Purchase failed: player="
                        + player.getName() + ", offer=" + offer.id(), failure);
                    if (player.isOnline()) {
                        player.sendMessage("§eĐang đối soát giao dịch và vật phẩm mua; vui lòng chờ.");
                        reconcileMerchantDeliveries(player);
                    }
                    return;
                }
                if (result.status() != PlayerProfileDatabaseManager.WalletTransactionStatus.APPLIED) {
                    pendingMerchantPurchases.remove(playerId);
                    if (player.isOnline()) player.sendMessage(result.status()
                        == PlayerProfileDatabaseManager.WalletTransactionStatus.INSUFFICIENT_FUNDS
                        ? "§cBạn không đủ VNCOIN để mua vật phẩm này."
                        : "§cKhông thể hoàn tất giao dịch VNCOIN.");
                    return;
                }
                pendingMerchantPurchases.remove(playerId);
                debug("[RPG-DEBUG][VNCOIN][Merchant] purchase player=" + player.getName()
                    + ", offer=" + offer.id() + ", amount=" + offer.item().getAmount() + ", price=" + offer.price()
                    + ", balance=" + result.balance() + ", transaction=" + transactionId);
                if (player.isOnline()) {
                    player.sendMessage("§aĐã thanh toán §6" + offer.price()
                        + " VNCOIN§a. Vật phẩm sẽ được giao an toàn vào túi khi còn chỗ.");
                    reconcileMerchantDeliveries(player);
                }
            }));
    }

    private void reconcileMerchantDeliveries(Player player) {
        UUID playerId = player.getUniqueId();
        if (!reconcilingMerchantDeliveries.add(playerId)) return;
        Set<String> taggedDeliveries = lockExistingMerchantDeliveryItems(player);
        database.executeGameplayDatabaseTask("wallet.merchant-reconcile player=" + player.getName(),
                () -> database.getPendingMerchantDeliveries(playerId.toString()))
            .whenComplete((deliveries, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (failure != null) {
                    reconcilingMerchantDeliveries.remove(playerId);
                    plugin.getLogger().log(Level.SEVERE, "[Economy][Merchant] Could not load pending deliveries for "
                        + player.getName() + ".", failure);
                    if (player.isOnline()) player.sendMessage("§cKhông tải được vật phẩm đang chờ giao. Hãy thử vào lại.");
                    return;
                }
                Set<String> pendingIds = new HashSet<>();
                for (PlayerProfileDatabaseManager.MerchantDelivery delivery : deliveries) {
                    pendingIds.add(delivery.transactionId());
                }
                for (String taggedId : taggedDeliveries) {
                    if (!pendingIds.contains(taggedId)) lockedMerchantDeliveries.remove(taggedId);
                }
                processMerchantDeliveries(player, deliveries, 0);
            }));
    }

    private void processMerchantDeliveries(Player player,
            List<PlayerProfileDatabaseManager.MerchantDelivery> deliveries, int index) {
        UUID playerId = player.getUniqueId();
        if (index >= deliveries.size()) {
            reconcilingMerchantDeliveries.remove(playerId);
            return;
        }
        PlayerProfileDatabaseManager.MerchantDelivery delivery = deliveries.get(index);
        if (containsMerchantDelivery(player, delivery.transactionId())) {
            acknowledgeMerchantDelivery(player, delivery.transactionId(),
                () -> processMerchantDeliveries(player, deliveries, index + 1));
            return;
        }
        if (!player.isOnline() || player.getInventory().firstEmpty() < 0) {
            reconcilingMerchantDeliveries.remove(playerId);
            if (player.isOnline()) player.sendMessage("§eCó vật phẩm mua đang chờ giao. Hãy chừa ô trống rồi dùng /vncoin.");
            return;
        }
        final ItemStack item;
        try {
            item = ItemStack.deserializeBytes(delivery.itemData());
        } catch (RuntimeException failure) {
            reconcilingMerchantDeliveries.remove(playerId);
            plugin.getLogger().log(Level.SEVERE, "[Economy][Merchant] Could not decode delivery transaction="
                + delivery.transactionId() + ", player=" + player.getName(), failure);
            player.sendMessage("§cKhông đọc được vật phẩm mua; dữ liệu được giữ để quản trị viên kiểm tra.");
            return;
        }
        if (item.getType().isAir() || !tagMerchantDelivery(item, delivery.transactionId())) {
            reconcilingMerchantDeliveries.remove(playerId);
            plugin.getLogger().severe("[Economy][Merchant] Invalid serialized item for transaction="
                + delivery.transactionId() + ", player=" + player.getName());
            return;
        }
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        if (!overflow.isEmpty()) {
            reconcilingMerchantDeliveries.remove(playerId);
            plugin.getLogger().severe("[Economy][Merchant] Inventory changed during delivery; transaction="
                + delivery.transactionId() + ", player=" + player.getName());
            return;
        }
        lockedMerchantDeliveries.add(delivery.transactionId());
        try {
            player.saveData();
        } catch (RuntimeException failure) {
            reconcilingMerchantDeliveries.remove(playerId);
            plugin.getLogger().log(Level.SEVERE, "[Economy][Merchant] Could not persist player inventory before "
                + "delivery acknowledgement: transaction=" + delivery.transactionId()
                + ", player=" + player.getName(), failure);
            player.sendMessage("§eVật phẩm đã vào túi nhưng xác nhận đang chờ. Không di chuyển vật phẩm này.");
            return;
        }
        player.sendMessage("§aĐã giao vật phẩm mua. Đang xác nhận giao dịch...");
        acknowledgeMerchantDelivery(player, delivery.transactionId(),
            () -> processMerchantDeliveries(player, deliveries, index + 1));
    }

    private void acknowledgeMerchantDelivery(Player player, String transactionId, Runnable onSuccess) {
        UUID playerId = player.getUniqueId();
        database.executeGameplayDatabaseTask("wallet.merchant-ack transaction=" + transactionId,
                () -> database.markMerchantDeliveryDelivered(playerId.toString(), transactionId, Instant.now().toString()))
            .whenComplete((acknowledged, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (failure != null || !Boolean.TRUE.equals(acknowledged)) {
                    plugin.getLogger().log(Level.SEVERE, "[Economy][Merchant] Could not acknowledge item delivery: "
                        + "transaction=" + transactionId + ", player=" + player.getName(), failure);
                    reconcilingMerchantDeliveries.remove(playerId);
                    if (player.isOnline()) player.sendMessage("§eVật phẩm đã được giao nhưng xác nhận còn chờ; không di chuyển vật phẩm này.");
                    return;
                }
                lockedMerchantDeliveries.remove(transactionId);
                debug("[RPG-DEBUG][VNCOIN][Merchant] delivery-confirmed player=" + player.getName()
                    + ", transaction=" + transactionId);
                onSuccess.run();
            }));
    }

    private boolean tagMerchantDelivery(ItemStack item, String transactionId) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        meta.getPersistentDataContainer().set(merchantDeliveryIdKey, PersistentDataType.STRING, transactionId);
        item.setItemMeta(meta);
        return true;
    }

    private boolean isLockedMerchantDelivery(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getItemMeta() == null) return false;
        String id = item.getItemMeta().getPersistentDataContainer()
            .get(merchantDeliveryIdKey, PersistentDataType.STRING);
        return id != null && lockedMerchantDeliveries.contains(id);
    }

    private boolean containsLockedMerchantDelivery(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isLockedMerchantDelivery(item)) return true;
        }
        for (ItemStack item : player.getEnderChest().getContents()) {
            if (isLockedMerchantDelivery(item)) return true;
        }
        return isLockedMerchantDelivery(player.getItemOnCursor());
    }

    private boolean containsMerchantDelivery(Player player, String transactionId) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (hasMerchantDeliveryId(item, transactionId)) return true;
        }
        for (ItemStack item : player.getEnderChest().getContents()) {
            if (hasMerchantDeliveryId(item, transactionId)) return true;
        }
        return false;
    }

    private Set<String> lockExistingMerchantDeliveryItems(Player player) {
        Set<String> ids = new HashSet<>();
        for (ItemStack item : player.getInventory().getContents()) addMerchantDeliveryId(item, ids);
        for (ItemStack item : player.getEnderChest().getContents()) addMerchantDeliveryId(item, ids);
        lockedMerchantDeliveries.addAll(ids);
        return ids;
    }

    private void addMerchantDeliveryId(ItemStack item, Set<String> ids) {
        if (item == null || item.getType().isAir() || item.getItemMeta() == null) return;
        String id = item.getItemMeta().getPersistentDataContainer()
            .get(merchantDeliveryIdKey, PersistentDataType.STRING);
        if (id != null && !id.isBlank()) ids.add(id);
    }

    private boolean hasMerchantDeliveryId(ItemStack item, String transactionId) {
        if (item == null || item.getType().isAir() || item.getItemMeta() == null) return false;
        return transactionId.equals(item.getItemMeta().getPersistentDataContainer()
            .get(merchantDeliveryIdKey, PersistentDataType.STRING));
    }

    private ItemStack createCoinDrop(int amount, boolean critical, boolean bossDrop) {
        return createCoinDrop(amount, critical, bossDrop, UUID.randomUUID());
    }

    private ItemStack createCoinDrop(int amount, boolean critical, boolean bossDrop, UUID dropId) {
        ItemStack item = new ItemStack(Material.SUNFLOWER, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(critical ? "§6§l✦ VNCOIN HIẾM §7x" + amount
            : bossDrop ? "§6§lVNCOIN BOSS §7x" + amount : "§6VNCOIN §7x" + amount);
        meta.setLore(List.of("§eNhặt để tự động cộng vào ví.",
            critical ? "§cCritical drop hiếm." : bossDrop ? "§dPhần thưởng boss." : "§7Tiền tệ của VnMine."));
        meta.getPersistentDataContainer().set(coinDropIdKey, PersistentDataType.STRING, dropId.toString());
        meta.getPersistentDataContainer().set(coinAmountKey, PersistentDataType.INTEGER, amount);
        if (bossDrop) meta.getPersistentDataContainer().set(coinBossDropKey, PersistentDataType.BYTE, (byte) 1);
        if (critical) meta.getPersistentDataContainer().set(coinCriticalKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean hasCoinTag(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return false;
        return stack.getItemMeta().getPersistentDataContainer().has(coinDropIdKey, PersistentDataType.STRING);
    }

    private boolean isEligibleSpawn(LivingEntity mob) {
        Byte eligible = mob.getPersistentDataContainer().get(spawnEligibleKey, PersistentDataType.BYTE);
        return eligible == null || eligible == (byte) 1;
    }

    private boolean isSpecialMob(LivingEntity mob) {
        if (mob.getPersistentDataContainer().has(armyUnitKey, PersistentDataType.STRING)
                || mob.getPersistentDataContainer().has(armySummonKey, PersistentDataType.STRING)) return true;
        return plugin.getBossEventManager() != null && plugin.getBossEventManager().isBossOrSummon(mob);
    }

    private String dropSkipReason(LivingEntity mob, Player killer, MobTier tier) {
        if (tier == null) return "not-in-basic-mob-allowlist";
        if (killer == null) return "no-player-killer";
        if (!isEligibleSpawn(mob)) return "disallowed-spawn-reason";
        if (isSpecialMob(mob)) return "boss-or-summon";
        return "disabled";
    }

    private void restoreDroppedItem(Item item, LocationSnapshot snapshot) {
        if (item.isValid()) return;
        World world = snapshot.location().getWorld();
        if (world != null) world.dropItemNaturally(snapshot.location(), snapshot.stack());
    }

    public void openItemNameMenu(Player player) {
        ItemNameMode selected = getItemNameMode(player);
        ItemNameMenuHolder holder = new ItemNameMenuHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, 27, "VnMine • Tên item rơi");
        holder.inventory = inventory;
        for (int slot = 0; slot < 27; slot++) {
            if (slot < 9 || slot >= 18) inventory.setItem(slot,
                menuItem(Material.GRAY_STAINED_GLASS_PANE, "§r", List.of()));
        }
        inventory.setItem(4, menuItem(Material.NAME_TAG, "§6Nhãn item trên mặt đất", List.of(
            "§7Thiết lập này chỉ áp dụng với bạn.", "§7Vật phẩm vẫn hiện và nhặt được.")));
        setItemNameOption(inventory, 10, ItemNameMode.OFF, Material.BARRIER, selected,
            "Ẩn toàn bộ tên item.");
        setItemNameOption(inventory, 12, ItemNameMode.ALL, Material.NAME_TAG, selected,
            "Hiển thị tên của mọi item rơi.");
        setItemNameOption(inventory, 14, ItemNameMode.RPG_ONLY, Material.ENCHANTED_BOOK, selected,
            "Chỉ item có dấu hiệu RPG của server.");
        setItemNameOption(inventory, 16, ItemNameMode.RARE_ONLY, Material.NETHER_STAR, selected,
            "Item quý hiếm, cường hóa hoặc có metadata đặc biệt.");
        inventory.setItem(22, menuItem(Material.ARROW, "§7Về menu chính", List.of()));
        player.openInventory(inventory);
    }

    private void setItemNameOption(Inventory inventory, int slot, ItemNameMode mode, Material material,
                                  ItemNameMode selected, String description) {
        boolean active = mode == selected;
        inventory.setItem(slot, menuItem(active ? Material.LIME_DYE : material,
            (active ? "§a✔ " : "§f") + getItemNameModeLabel(mode),
            List.of("§7" + description, active ? "§aĐang chọn" : "§eNhấp để chọn")));
    }

    @EventHandler
    public void onItemNameMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ItemNameMenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.viewer.equals(player.getUniqueId())
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemNameMode mode = switch (event.getRawSlot()) {
            case 10 -> ItemNameMode.OFF;
            case 12 -> ItemNameMode.ALL;
            case 14 -> ItemNameMode.RPG_ONLY;
            case 16 -> ItemNameMode.RARE_ONLY;
            default -> null;
        };
        if (mode != null) {
            setItemNameMode(player, mode);
            player.sendMessage("§aChế độ nhãn item: " + getItemNameModeLabel(mode));
            openItemNameMenu(player);
        } else if (event.getRawSlot() == 22 && plugin.getMercenaryManager() != null) {
            plugin.getMercenaryManager().openMainMenu(player);
        }
    }

    private ItemStack menuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void debug(String message) {
        if (plugin.getConfig().getBoolean("logging.console-debug", false)) plugin.getLogger().info(message);
    }

    private void debugDrop(String message) {
        if (plugin.getConfig().getBoolean("logging.console-debug", false)
                && plugin.getConfig().getBoolean("economy.mob-drops.debug-logs", false)) {
            plugin.getLogger().info(message);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        debug("[RPG-DEBUG][Command] command=/vncoin, sender=" + sender.getName()
            + ", args=" + String.join(" ", args));
        if (!economyEnabled) {
            sender.sendMessage("§cHệ thống VNCOIN hiện đang tắt.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("balance")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cConsole cần chỉ định người chơi: /vncoin balance <người_chơi>.");
                return true;
            }
            showBalance(sender, player.getUniqueId(), player.getName());
            return true;
        }
        if (args[0].equalsIgnoreCase("history")) {
            handleHistory(sender, args);
            return true;
        }
        if (args[0].equalsIgnoreCase("stats")) {
            handleEconomyStats(sender, args);
            return true;
        }
        if ((args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("take"))
                && args.length >= 3) {
            handleAdminTransaction(sender, args);
            return true;
        }
        sender.sendMessage("§eDùng: /vncoin [balance|history [người_chơi]|give|take <người_chơi> <số> [lý_do]]");
        return true;
    }

    private void showBalance(CommandSender sender, UUID targetId, String targetName) {
        database.executeGameplayDatabaseTask("wallet.balance target=" + targetName,
                () -> database.getWalletBalance(targetId.toString()))
            .whenComplete((balance, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (failure != null) {
                    plugin.getLogger().log(Level.SEVERE, "[Economy] Could not load wallet balance for "
                        + targetName + ".", failure);
                    sender.sendMessage("§cKhông thể tải số dư VNCOIN.");
                    return;
                }
                sender.sendMessage("§6VNCOIN §7của " + targetName + ": §e" + balance);
                debug("[RPG-DEBUG][VNCOIN] balance-view requester=" + sender.getName()
                    + ", target=" + targetName + ", balance=" + balance);
            }));
    }

    private void handleEconomyStats(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cBạn không có quyền xem thống kê VNCOIN.");
            return;
        }
        if (args.length != 1) {
            sender.sendMessage("§eDùng: /vncoin stats");
            return;
        }
        Instant since = Instant.now().minus(inflationWindowDays, java.time.temporal.ChronoUnit.DAYS);
        sender.sendMessage("§7Đang tổng hợp ledger VNCOIN trong " + inflationWindowDays + " ngày...");
        database.executeGameplayDatabaseTask("wallet.economy-stats days=" + inflationWindowDays,
                () -> database.getWalletEconomyReport(since.toString()))
            .whenComplete((report, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (failure != null) {
                    plugin.getLogger().log(Level.SEVERE, "[Economy] Could not load VNCOIN economy report.", failure);
                    sender.sendMessage("§cKhông thể tải thống kê VNCOIN lúc này.");
                    return;
                }
                sender.sendMessage("§6§lVNCOIN · " + inflationWindowDays + " ngày gần nhất");
                sender.sendMessage("§aPhát hành: " + formatCoinAmount(report.issued())
                    + " §8| §cĐã tiêu/thu hồi: " + formatCoinAmount(report.spent())
                    + " §8| §eChênh lệch: " + formatCoinAmount(report.issued() - report.spent()));
                sender.sendMessage("§7Giao dịch: " + formatCoinAmount(report.transactionCount())
                    + " §8| §7Nguồn lớn nhất (tối đa 20):");
                if (report.sources().isEmpty()) sender.sendMessage("§8Chưa có giao dịch trong khoảng thời gian này.");
                for (PlayerProfileDatabaseManager.WalletSourceStats source : report.sources()) {
                    sender.sendMessage("§8• §f" + source.source() + " §8(§7" + source.transactions()
                        + " giao dịch§8) §a+" + formatCoinAmount(source.issued())
                        + " §c-" + formatCoinAmount(source.spent()));
                }
            }));
    }

    private String formatCoinAmount(long amount) {
        return String.format(Locale.ROOT, "%,d", amount);
    }

    private void handleHistory(CommandSender sender, String[] args) {
        if (args.length > 2) {
            sender.sendMessage("§eDùng: /vncoin history [người_chơi]");
            return;
        }
        UUID targetId;
        String targetName;
        if (args.length == 1 && sender instanceof Player player) {
            targetId = player.getUniqueId();
            targetName = player.getName();
        } else if (args.length == 2) {
            if (!sender.hasPermission(HISTORY_OTHERS_PERMISSION) && !sender.hasPermission(ADMIN_PERMISSION)) {
                sender.sendMessage("§cBạn không có quyền xem ví người chơi khác.");
                return;
            }
            OfflinePlayer target = findKnownPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("§cKhông tìm thấy người chơi đã từng vào máy chủ đó.");
                return;
            }
            targetId = target.getUniqueId();
            targetName = displayName(target, args[1]);
        } else {
            sender.sendMessage("§cConsole cần nêu người chơi: /vncoin history <người_chơi>.");
            return;
        }
        UUID id = targetId;
        String name = targetName;
        database.executeGameplayDatabaseTask("wallet.history target=" + name,
                () -> database.getWalletHistory(id.toString(), 10))
            .whenComplete((history, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (failure != null) {
                    plugin.getLogger().log(Level.SEVERE, "[Economy] Could not load wallet history for " + name, failure);
                    sender.sendMessage("§cKhông thể tải lịch sử VNCOIN.");
                    return;
                }
                sender.sendMessage("§6§lLịch sử VNCOIN: §e" + name);
                if (history.isEmpty()) sender.sendMessage("§7Chưa có giao dịch.");
                for (PlayerProfileDatabaseManager.WalletLedgerEntry entry : history) {
                    sender.sendMessage((entry.delta() > 0 ? "§a+" : "§c") + entry.delta()
                        + " §6VNCOIN §8• §7" + entry.source() + " §8• §f" + entry.createdAt());
                }
            }));
    }

    private void handleAdminTransaction(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cBạn không có quyền quản trị VNCOIN.");
            return;
        }
        if (args.length < 3 || args.length > 4) {
            sender.sendMessage("§eDùng: /vncoin give|take <người_chơi> <số> [lý_do]");
            return;
        }
        OfflinePlayer target = findKnownPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cKhông tìm thấy người chơi đã từng vào máy chủ đó.");
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException exception) {
            sender.sendMessage("§cSố VNCOIN phải là số nguyên hợp lệ.");
            return;
        }
        if (amount < 1 || amount > MAX_ADMIN_TRANSACTION) {
            sender.sendMessage("§cSố tiền phải nằm trong khoảng 1–" + MAX_ADMIN_TRANSACTION + ".");
            return;
        }
        boolean give = args[0].equalsIgnoreCase("give");
        long delta = give ? amount : -amount;
        String targetName = displayName(target, args[1]);
        String reason = args.length > 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length))
            : "admin " + (give ? "give" : "take");
        if (reason.length() > 240) {
            sender.sendMessage("§cLý do giao dịch không được vượt quá 240 ký tự.");
            return;
        }
        String actorUuid = sender instanceof Player player ? player.getUniqueId().toString() : null;
        UUID targetId = target.getUniqueId();
        String transactionId = "admin:" + UUID.randomUUID();
        String actorName = sender.getName();
        database.executeGameplayDatabaseTask("wallet.admin-" + (give ? "give" : "take")
                + " actor=" + actorName + " target=" + targetName,
                () -> database.applyWalletTransaction(targetId.toString(), delta, transactionId,
                    give ? "admin-give" : "admin-take", actorUuid, reason, Instant.now().toString()))
            .whenComplete((result, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (failure != null) {
                    plugin.getLogger().log(Level.SEVERE, "[Economy] Admin VNCOIN transaction failed: actor="
                        + actorName + ", target=" + targetName + ", delta=" + delta + ", reason=" + reason, failure);
                    sender.sendMessage("§cGiao dịch thất bại; số dư không thay đổi.");
                    return;
                }
                switch (result.status()) {
                    case APPLIED -> {
                        plugin.getLogger().info("[Economy][Audit] actor=" + actorName + ", target=" + targetName
                            + ", delta=" + delta + ", balance=" + result.balance() + ", reason=" + reason
                            + ", transactionId=" + transactionId);
                        sender.sendMessage("§aĐã " + (give ? "cộng" : "trừ") + " " + amount
                            + " VNCOIN cho " + targetName + ". Số dư mới: " + result.balance());
                        Player onlineTarget = Bukkit.getPlayer(targetId);
                        if (onlineTarget != null) onlineTarget.sendMessage("§6Ví VNCOIN đã được quản trị viên cập nhật: §e"
                            + result.balance());
                    }
                    case DUPLICATE -> sender.sendMessage("§eGiao dịch này đã được xử lý trước đó.");
                    case INSUFFICIENT_FUNDS -> sender.sendMessage("§cNgười chơi không đủ VNCOIN. Số dư: "
                        + result.balance());
                    case BALANCE_LIMIT -> sender.sendMessage("§cGiao dịch vượt giới hạn số dư VNCOIN.");
                }
            }));
    }

    private OfflinePlayer findKnownPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() ? offline : null;
    }

    private String displayName(OfflinePlayer player, String fallback) {
        return player.getName() == null ? fallback : player.getName();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> choices = new ArrayList<>(List.of("balance", "history"));
            if (sender.hasPermission(ADMIN_PERMISSION)) choices.add("stats");
            if (sender.hasPermission(ADMIN_PERMISSION)) choices.addAll(List.of("give", "take"));
            return matching(args[0], choices);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("history")
                || args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("take"))) {
            return matching(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("take"))) {
            return List.of("1");
        }
        return List.of();
    }

    private List<String> matching(String prefix, List<String> choices) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return choices.stream().filter(choice -> choice.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }

    @Override
    public void close() {
        ProtocolLibrary.getProtocolManager().removePacketListener(itemNamePacketListener);
        itemNameModes.clear();
        itemNameCategories.clear();
    }

    public enum ItemNameMode {
        OFF,
        ALL,
        RPG_ONLY,
        RARE_ONLY
    }

    private record ItemNameCategory(boolean rpg, boolean rare) {
    }

    private record MobTier(String id, int minimum, int maximum) {
    }

    private record WalletView(long balance, List<PlayerProfileDatabaseManager.WalletLedgerEntry> history) {
    }

    public record CustomMerchantOffer(ItemStack item, long price) {
        public CustomMerchantOffer {
            item = item.clone();
        }
    }

    private record MerchantOffer(String id, ItemStack item, long price, String name) {
    }

    private static final class ItemNameMenuHolder implements InventoryHolder {
        private final UUID viewer;
        private Inventory inventory;

        private ItemNameMenuHolder(UUID viewer) {
            this.viewer = viewer;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private record LocationSnapshot(org.bukkit.Location location, ItemStack stack) {
    }

    private static final class WalletMenuHolder implements InventoryHolder {
        private final UUID viewer;
        private Inventory inventory;

        private WalletMenuHolder(UUID viewer) {
            this.viewer = viewer;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class MerchantMenuHolder implements InventoryHolder {
        private final UUID viewer;
        private final List<MerchantOffer> allOffers;
        private final Map<Integer, MerchantOffer> offers = new HashMap<>();
        private int page;
        private Inventory inventory;

        private MerchantMenuHolder(UUID viewer, List<MerchantOffer> allOffers) {
            this.viewer = viewer;
            this.allOffers = allOffers;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
