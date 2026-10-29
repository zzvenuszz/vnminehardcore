package com.vnmine.hardcore.managers;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.vnmine.hardcore.VnMineHardcore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import java.security.MessageDigest;
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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
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
    private final NamespacedKey merchantSaleIdKey;
    private final NamespacedKey merchantSaleAmountKey;
    private final NamespacedKey rpgSellValueKey;
    private final MerchantPriceCatalog merchantPriceCatalog;
    private final ConcurrentMap<UUID, ItemNameMode> itemNameModes = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, ItemNameCategory> itemNameCategories = new ConcurrentHashMap<>();
    private final Set<UUID> pendingClaims = new HashSet<>();
    private final Map<UUID, Long> nextBossDropClaimAt = new HashMap<>();
    private final Set<CreatureSpawnEvent.SpawnReason> allowedSpawnReasons = new HashSet<>();
    private final Set<UUID> pendingMerchantPurchases = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingWalletTransfers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PendingTransferName> pendingTransferNames = new ConcurrentHashMap<>();
    private final Set<UUID> pendingMerchantSales = ConcurrentHashMap.newKeySet();
    private final Set<UUID> reconcilingMerchantDeliveries = ConcurrentHashMap.newKeySet();
    private final Set<UUID> reconcileMerchantDeliveriesAgain = new HashSet<>();
    private final Map<UUID, List<MerchantDeliveryCallback>> merchantDeliveryCallbacks = new HashMap<>();
    private final Set<String> lockedMerchantDeliveries = ConcurrentHashMap.newKeySet();
    private final Set<String> lockedMerchantSales = ConcurrentHashMap.newKeySet();
    private final Set<UUID> reconcilingMerchantSales = ConcurrentHashMap.newKeySet();
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
        merchantSaleIdKey = new NamespacedKey(plugin, "merchant_sale_id");
        merchantSaleAmountKey = new NamespacedKey(plugin, "merchant_sale_amount");
        rpgSellValueKey = new NamespacedKey(plugin, "rpg_sell_value");
        merchantPriceCatalog = new MerchantPriceCatalog(plugin);
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

    public boolean reloadMerchantPrices() {
        if (!merchantPriceCatalog.reload()) return false;
        refreshAllGroundItemNames();
        debug("[RPG-DEBUG][VNCOIN][Merchant] refreshed-ground-item-labels after price catalog reload");
        return true;
    }

    private GroundSellQuote systemMerchantSellQuote(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || hasCoinTag(stack)) return null;
        ItemMeta meta = stack.getItemMeta();
        if (isPluginTaggedItem(stack)) {
            if (meta == null) return null;
            Long configuredValue = meta.getPersistentDataContainer().get(rpgSellValueKey, PersistentDataType.LONG);
            return configuredValue != null && configuredValue > 0 && configuredValue <= 1_000_000L
                ? new GroundSellQuote(1, configuredValue) : null;
        }
        MerchantPriceCatalog.SellPrice price = merchantPriceCatalog.get(stack.getType());
        return price == null ? null : new GroundSellQuote(price.amount(), price.vncoin());
    }

    private boolean isPluginTaggedItem(ItemStack stack) {
        ItemMeta meta = stack == null ? null : stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().getKeys().stream()
            .anyMatch(key -> key.getNamespace().equals(plugin.getName().toLowerCase(Locale.ROOT)));
    }

    private String systemMerchantAlternativeWarning(Player player, ItemStack item, GroundSellQuote systemQuote) {
        if (plugin.getNpcManager() == null
                || !plugin.getConfig().getBoolean("economy.merchant.custom.enabled", true)) return null;
        var custom = plugin.getNpcManager().getBestMerchantBuyQuote(item);
        if (custom.isEmpty() || countExactItem(player, item) < custom.get().amount()
                || custom.get().price() * (long) systemQuote.amount()
                    <= systemQuote.price() * (long) custom.get().amount()) return null;
        String merchantId = custom.get().merchantId();
        String name = plugin.getNpcManager().getNpcDisplayName(merchantId)
            .map(org.bukkit.ChatColor::stripColor).orElse(merchantId);
        String world = plugin.getNpcManager().getNpcTarget(merchantId)
            .map(NpcManager.NpcTarget::world).orElse("không xác định");
        return "§eShop " + name + " (" + merchantId + ", world " + world + ") thu mua "
            + custom.get().amount() + " = " + custom.get().price() + " VNCOIN; giá system là "
            + systemQuote.amount() + " = " + systemQuote.price() + " VNCOIN.";
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
        UUID playerId = event.getPlayer().getUniqueId();
        nextBossDropClaimAt.remove(playerId);
        itemNameModes.remove(playerId);
        reconcilingMerchantDeliveries.remove(playerId);
        reconcileMerchantDeliveriesAgain.remove(playerId);
        merchantDeliveryCallbacks.remove(playerId);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        loadItemNameMode(event.getPlayer());
        reconcilePendingMerchantSales(event.getPlayer());
        reconcileMerchantDeliveries(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLockedMerchantSaleDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        int removed = 0;
        for (ItemStack item : event.getDrops()) {
            if (isLockedMerchantSale(item)) {
                removed += item.getAmount();
                item.setAmount(0);
            }
        }
        event.getDrops().removeIf(item -> item == null || item.getAmount() <= 0 || item.getType().isAir());
        if (removed > 0) {
            plugin.getLogger().warning("[Economy][MerchantSale] Suppressed death drops for escrow-locked items; "
                + "sale recovery will settle the durable intent: player=" + event.getEntity().getName()
                + ", item-amount=" + removed);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item itemEntity = event.getEntity();
        ItemStack stack = itemEntity.getItemStack();
        if (stack.getType().isAir()) return;
        trackGroundItem(itemEntity);
        ItemMeta meta = stack.getItemMeta();
        String visibleName = null;
        if (meta != null && meta.hasDisplayName()) {
            visibleName = sanitizeVisibleName(meta.getDisplayName());
            if (!visibleName.equals(meta.getDisplayName())) {
                meta.setDisplayName(visibleName);
                stack.setItemMeta(meta);
                itemEntity.setItemStack(stack);
            }
        }
        itemEntity.customName(groundItemDisplayName(stack, visibleName));
        itemEntity.setCustomNameVisible(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDrop(BlockDropItemEvent event) {
        for (Item dropped : event.getItems()) {
            ItemStack stack = dropped.getItemStack();
            if (!isPlainStackableBlockDrop(stack)) continue;
            ItemStack normalized = new ItemStack(stack.getType(), stack.getAmount());
            dropped.setItemStack(normalized);
            trackGroundItem(dropped);
            applyGroundItemName(dropped);
        }
    }

    private boolean isPlainStackableBlockDrop(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.getType().isBlock()
                || stack.getMaxStackSize() <= 1) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || meta.hasDisplayName() || meta.hasLore() || meta.hasEnchants()
                || meta.hasAttributeModifiers() || meta.isUnbreakable() || !meta.getItemFlags().isEmpty()
                || !meta.getPersistentDataContainer().getKeys().isEmpty()) return false;
        if (meta.hasCustomModelData()) return false;
        return !(meta instanceof org.bukkit.inventory.meta.BlockStateMeta)
            && !(meta instanceof org.bukkit.inventory.meta.BannerMeta)
            && !(meta instanceof org.bukkit.inventory.meta.SkullMeta)
            && !(meta instanceof org.bukkit.inventory.meta.BookMeta)
            && !(meta instanceof org.bukkit.inventory.meta.PotionMeta)
            && !(meta instanceof org.bukkit.inventory.meta.Damageable);
    }

    @EventHandler
    public void onItemEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof Item item) {
                trackGroundItem(item);
                applyGroundItemName(item);
            }
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

    private void applyGroundItemName(Item item) {
        ItemStack stack = item.getItemStack();
        ItemMeta meta = stack.getItemMeta();
        String name = meta != null && meta.hasDisplayName() ? sanitizeVisibleName(meta.getDisplayName()) : null;
        item.customName(groundItemDisplayName(stack, name));
        item.setCustomNameVisible(true);
    }

    private Component groundItemDisplayName(ItemStack stack, String customName) {
        Component name = customName == null
            ? Component.translatable(stack.getType().translationKey()).color(NamedTextColor.YELLOW)
            : LegacyComponentSerializer.legacySection().deserialize(customName);
        String sellLabel = groundItemSellLabel(stack);
        if (sellLabel.isBlank()) return name;
        return name.append(LegacyComponentSerializer.legacySection().deserialize(sellLabel));
    }

    private String groundItemSellLabel(ItemStack stack) {
        if (hasCoinTag(stack)) return "";
        GroundSellQuote quote = systemMerchantSellQuote(stack);
        if (plugin.getNpcManager() != null) {
            var customQuote = plugin.getNpcManager().getBestMerchantBuyQuote(stack);
            if (customQuote.isPresent() && (quote == null
                    || (double) customQuote.get().price() / customQuote.get().amount()
                        > (double) quote.price() / quote.amount())) {
                return " §7[" + customQuote.get().amount() + " = " + customQuote.get().price() + " VNCOIN]";
            }
        }
        return quote == null ? " §8[Không có giá thu mua]"
            : " §7[" + quote.amount() + " = " + quote.price() + " VNCOIN]";
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

    public void refreshAllGroundItemNames() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item item) {
                    trackGroundItem(item);
                    applyGroundItemName(item);
                }
            }
        }
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
        if (isLockedMerchantDelivery(event.getItemDrop().getItemStack())
                || isLockedMerchantSale(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLockedMerchantDeliveryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !containsLockedMerchantDelivery(player) && !containsLockedMerchantSale(player)) return;
        event.setCancelled(true);
        player.sendMessage("§7Vật phẩm mua đang được xác nhận an toàn, vui lòng chờ.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLockedMerchantDeliveryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !containsLockedMerchantDelivery(player) && !containsLockedMerchantSale(player)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLockedMerchantDeliveryInteract(PlayerInteractEvent event) {
        if (isLockedMerchantDelivery(event.getItem()) || isLockedMerchantSale(event.getItem())
                || isLockedMerchantDelivery(event.getPlayer().getInventory().getItemInOffHand())
                || isLockedMerchantSale(event.getPlayer().getInventory().getItemInOffHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLockedMerchantSaleSwap(org.bukkit.event.player.PlayerSwapHandItemsEvent event) {
        if (isLockedMerchantSale(event.getMainHandItem()) || isLockedMerchantSale(event.getOffHandItem())
                || isLockedMerchantDelivery(event.getMainHandItem())
                || isLockedMerchantDelivery(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLockedMerchantSaleConsume(PlayerItemConsumeEvent event) {
        if (!isLockedMerchantSale(event.getItem())) return;
        event.setCancelled(true);
        debug("[RPG-DEBUG][VNCOIN][MerchantSale] blocked-use player=" + event.getPlayer().getName()
            + ", action=consume, transaction=" + merchantSaleId(event.getItem()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLockedMerchantSaleDamage(PlayerItemDamageEvent event) {
        if (!isLockedMerchantSale(event.getItem())) return;
        event.setCancelled(true);
        debug("[RPG-DEBUG][VNCOIN][MerchantSale] blocked-use player=" + event.getPlayer().getName()
            + ", action=item-damage, transaction=" + merchantSaleId(event.getItem()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLockedMerchantSaleAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)
                || !isLockedMerchantSale(player.getInventory().getItemInMainHand())
                    && !isLockedMerchantSale(player.getInventory().getItemInOffHand())) return;
        event.setCancelled(true);
        debug("[RPG-DEBUG][VNCOIN][MerchantSale] blocked-use player=" + player.getName()
            + ", action=attack, main-hand-transaction="
            + merchantSaleId(player.getInventory().getItemInMainHand()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLockedMerchantSaleBlockBreak(BlockBreakEvent event) {
        if (!isLockedMerchantSale(event.getPlayer().getInventory().getItemInMainHand())
                && !isLockedMerchantSale(event.getPlayer().getInventory().getItemInOffHand())) return;
        event.setCancelled(true);
        debug("[RPG-DEBUG][VNCOIN][MerchantSale] blocked-use player=" + event.getPlayer().getName()
            + ", action=block-break, main-hand-transaction="
            + merchantSaleId(event.getPlayer().getInventory().getItemInMainHand()));
    }

    public void openWalletMenu(Player player) {
        reconcilePendingMerchantSales(player);
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
                Inventory inventory = Bukkit.createInventory(holder, 54, "VnMine • Ví VNCOIN");
                holder.inventory = inventory;
                inventory.setItem(20, menuItem(Material.SUNFLOWER, "§6Ví VNCOIN: §e" + view.balance(),
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
                inventory.setItem(22, menuItem(Material.BOOK, "§eMua/bán gần đây", historyLore));
                inventory.setItem(24, menuItem(Material.EMERALD, "§aChuyển VNCOIN", List.of(
                    "§7Chọn người chơi online hoặc nhập tên offline.",
                    "§7Bạn sẽ kiểm tra và xác nhận số tiền trước khi gửi.")));
                inventory.setItem(49, menuItem(Material.CRAFTING_TABLE, "§7Quay lại menu trước", List.of()));
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
        if (event.getRawSlot() == 24) {
            openTransferPlayerMenu(player, 0);
        } else if (event.getRawSlot() == 49 && plugin.getMercenaryManager() != null) {
            plugin.getMercenaryManager().openMainMenu(player);
        }
    }

    private void openTransferPlayerMenu(Player player, int requestedPage) {
        List<? extends Player> online = Bukkit.getOnlinePlayers().stream()
            .filter(target -> !target.getUniqueId().equals(player.getUniqueId()))
            .sorted(java.util.Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER)).toList();
        int pages = Math.max(1, (online.size() + 44) / 45);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        TransferPlayerHolder holder = new TransferPlayerHolder(player.getUniqueId(), page);
        Inventory inventory = Bukkit.createInventory(holder, 54, "VnMine • Chuyển VNCOIN • Chọn người");
        holder.inventory = inventory;
        for (int slot = 0; slot < 45; slot++) {
            int index = page * 45 + slot;
            if (index >= online.size()) break;
            Player target = online.get(index);
            holder.targets.put(slot, new TransferTarget(target.getUniqueId(), target.getName()));
            inventory.setItem(slot, menuItem(Material.PLAYER_HEAD, "§b" + target.getName(), List.of(
                "§7Trạng thái: §aĐang online", "§eNhấn để nhập số VNCOIN chuyển.")));
        }
        if (online.isEmpty()) inventory.setItem(22, menuItem(Material.BARRIER, "§7Không có người chơi online khác", List.of(
            "§7Bạn vẫn có thể nhập tên người chơi offline ở nút cuối.")));
        if (page > 0) inventory.setItem(45, menuItem(Material.LIME_CARPET, "§eTrang trước", List.of()));
        inventory.setItem(46, menuItem(Material.NAME_TAG, "§eNhập tên người chơi offline", List.of(
            "§7Chỉ gửi đến tài khoản đã từng vào server.", "§eNhấn rồi nhập tên trong chat.")));
        inventory.setItem(48, menuItem(Material.PAPER, "§fTrang " + (page + 1) + "/" + pages,
            List.of("§7Người chơi online: " + online.size())));
        inventory.setItem(49, menuItem(Material.CRAFTING_TABLE, "§7Quay lại ví VNCOIN", List.of()));
        if (page + 1 < pages) inventory.setItem(50, menuItem(Material.CYAN_CARPET, "§eTrang tiếp", List.of()));
        inventory.setItem(53, menuItem(Material.BARRIER, "§cĐóng", List.of()));
        player.openInventory(inventory);
    }

    private void openTransferAmountMenu(Player player, TransferTarget target, long amount) {
        openTransferAmountMenu(player, target, amount, null);
    }

    private void openTransferAmountMenu(Player player, TransferTarget target, long amount, Inventory parentMenu) {
        long safeAmount = Math.max(1L, Math.min(PlayerProfileDatabaseManager.MAX_WALLET_BALANCE, amount));
        TransferAmountHolder holder = new TransferAmountHolder(player.getUniqueId(), target, safeAmount, parentMenu);
        Inventory inventory = Bukkit.createInventory(holder, 54, "VnMine • Chuyển VNCOIN • Số tiền");
        holder.inventory = inventory;
        inventory.setItem(4, menuItem(Material.PAPER, "§eNgười nhận: §f" + target.name(), List.of(
            "§7UUID: §8" + target.uuid())));
        inventory.setItem(13, menuItem(Material.SUNFLOWER, "§6Số tiền: §e" + formatCoinAmount(safeAmount), List.of(
            "§7Dùng các nút bên dưới để chỉnh số tiền.")));
        long[] deltas = {-1000, -100, -10, -1, 1, 10, 100, 1000};
        int[] slots = {19, 20, 21, 22, 24, 25, 26, 27};
        for (int i = 0; i < slots.length; i++) {
            long delta = deltas[i];
            inventory.setItem(slots[i], menuItem(delta < 0 ? Material.RED_DYE : Material.LIME_DYE,
                delta < 0 ? "§c" + delta : "§a+" + delta, List.of("§eNhấn để chỉnh số tiền.")));
        }
        inventory.setItem(45, menuItem(Material.CRAFTING_TABLE, "§7Quay lại chọn người chơi", List.of()));
        inventory.setItem(49, menuItem(Material.LIME_DYE, "§aKiểm tra và chuyển", List.of(
            "§7Bước tiếp theo sẽ yêu cầu bạn xác nhận lại.", "§eSố tiền: §6" + formatCoinAmount(safeAmount) + " VNCOIN")));
        inventory.setItem(53, menuItem(Material.BARRIER, "§cĐóng", List.of()));
        player.openInventory(inventory);
        if (parentMenu != null && plugin.getMenuNavigationManager() != null) {
            plugin.getMenuNavigationManager().linkParent(inventory, parentMenu);
        }
    }

    private void openTransferConfirmation(Player player, TransferTarget target, long amount) {
        openTransferConfirmation(player, target, amount, null);
    }

    private void openTransferConfirmation(Player player, TransferTarget target, long amount, Inventory parentMenu) {
        if (amount < 1 || amount > PlayerProfileDatabaseManager.MAX_WALLET_BALANCE) {
            player.sendMessage("§cSố tiền chuyển phải từ 1 đến "
                + formatCoinAmount(PlayerProfileDatabaseManager.MAX_WALLET_BALANCE) + " VNCOIN.");
            return;
        }
        TransferConfirmHolder holder = new TransferConfirmHolder(player.getUniqueId(), target, amount, parentMenu);
        Inventory inventory = Bukkit.createInventory(holder, 27, "Xác nhận chuyển VNCOIN");
        holder.inventory = inventory;
        inventory.setItem(4, menuItem(Material.SUNFLOWER, "§6Kiểm tra kỹ giao dịch", List.of(
            "§7Người nhận: §f" + target.name(), "§7Số tiền: §6" + formatCoinAmount(amount) + " VNCOIN",
            "§cSau khi xác nhận, giao dịch không thể tự hoàn tác.")));
        inventory.setItem(11, menuItem(Material.LIME_DYE, "§aXÁC NHẬN CHUYỂN", List.of(
            "§7Chuyển §6" + formatCoinAmount(amount) + " VNCOIN§7 cho §f" + target.name() + "§7.",
            "§cHãy kiểm tra lại đúng tên người nhận và số tiền.")));
        inventory.setItem(15, menuItem(Material.RED_DYE, "§cHỦY GIAO DỊCH", List.of(
            "§7Không có VNCOIN nào bị trừ.")));
        player.openInventory(inventory);
        if (parentMenu != null && plugin.getMenuNavigationManager() != null) {
            plugin.getMenuNavigationManager().linkParent(inventory, parentMenu);
        }
    }

    private void requestOfflineTransferName(Player player, int page) {
        Inventory returnInventory = player.getOpenInventory().getTopInventory();
        pendingTransferNames.put(player.getUniqueId(), new PendingTransferName(page, returnInventory));
        player.closeInventory();
        player.sendMessage("§6[VNCOIN] §eNhập tên người chơi offline, ví dụ: §fhoanbh§e. §7Gõ §c!cancel §7để hủy.");
    }

    @EventHandler
    public void onTransferNameChat(AsyncPlayerChatEvent event) {
        PendingTransferName pending = pendingTransferNames.remove(event.getPlayer().getUniqueId());
        if (pending == null) return;
        event.setCancelled(true);
        String name = event.getMessage().trim();
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (name.equalsIgnoreCase("!cancel")) {
                player.openInventory(pending.returnInventory());
                return;
            }
            if (!name.matches("[A-Za-z0-9_]{3,16}") || name.equalsIgnoreCase(player.getName())) {
                player.sendMessage("§cTên không hợp lệ hoặc bạn không thể chuyển tiền cho chính mình.");
                player.openInventory(pending.returnInventory());
                return;
            }
            Player online = Bukkit.getPlayerExact(name);
            if (online != null) {
                openTransferAmountMenu(player, new TransferTarget(online.getUniqueId(), online.getName()),
                    1, pending.returnInventory());
                return;
            }
            database.executeGameplayDatabaseTask("wallet.transfer-recipient-lookup name=" + name,
                    () -> database.findKnownWalletPlayer(name))
                .whenComplete((known, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (failure != null) {
                        plugin.getLogger().log(Level.SEVERE, "[Economy][Pay] Could not resolve offline recipient "
                            + name, failure);
                        player.sendMessage("§cKhông thể tìm người nhận lúc này.");
                        return;
                    }
                    if (known.isEmpty()) {
                        player.sendMessage("§cKhông tìm thấy tài khoản đã từng vào server tên §f" + name + "§c.");
                        player.openInventory(pending.returnInventory());
                        return;
                    }
                    TransferTarget target = new TransferTarget(UUID.fromString(known.get().uuid()), known.get().name());
                    openTransferAmountMenu(player, target, 1, pending.returnInventory());
                }));
        });
    }

    private boolean handlePayCommand(CommandSender sender, String[] args) {
        if (!economyEnabled) {
            sender.sendMessage("§cHệ thống VNCOIN hiện đang tắt.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cLệnh /pay chỉ dùng trong game.");
            return true;
        }
        if (args.length != 2) {
            player.sendMessage("§eDùng: /pay <tên người chơi> <số tiền>");
            return true;
        }
        long amount;
        try { amount = Long.parseLong(args[1]); }
        catch (NumberFormatException exception) { amount = -1; }
        if (amount < 1 || amount > PlayerProfileDatabaseManager.MAX_WALLET_BALANCE) {
            player.sendMessage("§cSố tiền phải là số nguyên từ 1 đến "
                + formatCoinAmount(PlayerProfileDatabaseManager.MAX_WALLET_BALANCE) + ".");
            return true;
        }
        final long transferAmount = amount;
        String name = args[0];
        if (!name.matches("[A-Za-z0-9_]{3,16}") || name.equalsIgnoreCase(player.getName())) {
            player.sendMessage("§cTên không hợp lệ hoặc bạn không thể chuyển cho chính mình.");
            return true;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            openTransferConfirmation(player, new TransferTarget(online.getUniqueId(), online.getName()), transferAmount);
            return true;
        }
        database.executeGameplayDatabaseTask("wallet.pay-recipient-lookup name=" + name,
                () -> database.findKnownWalletPlayer(name))
            .whenComplete((known, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (failure != null) {
                    plugin.getLogger().log(Level.SEVERE, "[Economy][Pay] Could not resolve recipient " + name, failure);
                    player.sendMessage("§cKhông thể tìm người nhận lúc này.");
                } else if (known.isEmpty()) {
                    player.sendMessage("§cKhông tìm thấy tài khoản đã từng vào server tên §f" + name + "§c.");
                } else {
                    openTransferConfirmation(player,
                        new TransferTarget(UUID.fromString(known.get().uuid()), known.get().name()), transferAmount);
                }
            }));
        return true;
    }

    private void executeWalletTransfer(Player sender, TransferTarget target, long amount) {
        UUID senderId = sender.getUniqueId();
        if (senderId.equals(target.uuid())) {
            sender.sendMessage("§cBạn không thể chuyển VNCOIN cho chính mình.");
            return;
        }
        if (!pendingWalletTransfers.add(senderId)) {
            sender.sendMessage("§eĐang xử lý giao dịch VNCOIN trước đó, vui lòng chờ.");
            return;
        }
        String senderName = sender.getName();
        String transaction = UUID.randomUUID().toString();
        debug("[RPG-DEBUG][VNCOIN][Pay] started sender=" + senderName + ", recipient=" + target.name()
            + ", amount=" + amount + ", transaction=" + transaction);
        database.executeGameplayDatabaseTask("wallet.transfer sender=" + senderName + " amount=" + amount,
                () -> database.transferWallet(senderId.toString(), target.uuid().toString(), amount,
                    "pay:" + transaction + ":out", "pay:" + transaction + ":in",
                    senderId.toString(), target.uuid().toString(), "Chuyển VNCOIN cho " + target.name(),
                    "Nhận VNCOIN từ " + senderName, Instant.now().toString()))
            .whenComplete((result, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                pendingWalletTransfers.remove(senderId);
                if (failure != null) {
                    plugin.getLogger().log(Level.SEVERE, "[Economy][Pay] Transfer failed: sender=" + senderName
                        + ", recipient=" + target.name() + ", amount=" + amount + ", transaction=" + transaction,
                        failure);
                    if (sender.isOnline()) sender.sendMessage("§cChuyển VNCOIN thất bại. Số dư được giữ nguyên.");
                    return;
                }
                if (result.status() == PlayerProfileDatabaseManager.WalletTransferStatus.INSUFFICIENT_FUNDS) {
                    if (sender.isOnline()) sender.sendMessage("§cBạn không đủ VNCOIN để chuyển số tiền này.");
                    return;
                }
                if (result.status() == PlayerProfileDatabaseManager.WalletTransferStatus.RECIPIENT_LIMIT) {
                    if (sender.isOnline()) sender.sendMessage("§cVí người nhận đã đạt giới hạn số dư.");
                    return;
                }
                debug("[RPG-DEBUG][VNCOIN][Pay] completed sender=" + senderName + ", recipient="
                    + target.name() + ", amount=" + amount + ", senderBalance=" + result.senderBalance()
                    + ", recipientBalance=" + result.recipientBalance() + ", transaction=" + transaction);
                if (sender.isOnline()) {
                    sender.sendMessage("§aĐã chuyển §6" + formatCoinAmount(amount) + " VNCOIN§a cho §f"
                        + target.name() + "§a. Số dư còn lại: §e" + formatCoinAmount(result.senderBalance()));
                    openWalletMenu(sender);
                }
                Player recipient = Bukkit.getPlayer(target.uuid());
                if (recipient != null) recipient.sendMessage("§aBạn nhận được §6" + formatCoinAmount(amount)
                    + " VNCOIN §atừ §f" + senderName + "§a.");
            }));
    }

    @EventHandler
    public void onTransferPlayerMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof TransferPlayerHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.viewer.equals(player.getUniqueId())
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getRawSlot();
        if (slot == 49) openWalletMenu(player);
        else if (slot == 45 && holder.page > 0) openTransferPlayerMenu(player, holder.page - 1);
        else if (slot == 50) openTransferPlayerMenu(player, holder.page + 1);
        else if (slot == 46) requestOfflineTransferName(player, holder.page);
        else if (slot == 53) player.closeInventory();
        else {
            TransferTarget target = holder.targets.get(slot);
            if (target != null) openTransferAmountMenu(player, target, 1, holder.inventory);
        }
    }

    @EventHandler
    public void onTransferAmountMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof TransferAmountHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.viewer.equals(player.getUniqueId())
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getRawSlot();
        if (slot == 45) {
            if (holder.parentMenu != null) player.openInventory(holder.parentMenu);
            else openTransferPlayerMenu(player, 0);
        }
        else if (slot == 49) openTransferConfirmation(player, holder.target, holder.amount, holder.inventory);
        else if (slot == 53) player.closeInventory();
        else {
            int[] slots = {19, 20, 21, 22, 24, 25, 26, 27};
            long[] deltas = {-1000, -100, -10, -1, 1, 10, 100, 1000};
            for (int index = 0; index < slots.length; index++) {
                if (slots[index] == slot) {
                    long next = Math.max(1, Math.min(PlayerProfileDatabaseManager.MAX_WALLET_BALANCE,
                        holder.amount + deltas[index]));
                    openTransferAmountMenu(player, holder.target, next, holder.parentMenu);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onTransferConfirmClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof TransferConfirmHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.viewer.equals(player.getUniqueId())
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (event.getRawSlot() == 11) {
            player.closeInventory();
            executeWalletTransfer(player, holder.target, holder.amount);
        } else if (event.getRawSlot() == 15) {
            if (holder.parentMenu != null) player.openInventory(holder.parentMenu);
            else openWalletMenu(player);
        }
    }

    public void openMerchantMenu(Player player) {
        if (!economyEnabled || !plugin.getConfig().getBoolean("economy.merchant.enabled", true)) {
            player.sendMessage("§cCửa hàng VNCOIN hiện đang đóng.");
            return;
        }
        openMerchantInventory(player, "VnMine • Thương nhân", loadMerchantOffers(), "system-merchant", List.of());
    }

    public void openCustomMerchantMenu(Player player, String merchantId, String title,
                                       List<CustomMerchantOffer> customOffers) {
        openCustomMerchantMenu(player, merchantId, title, customOffers, List.of());
    }

    public void openCustomMerchantMenu(Player player, String merchantId, String title,
            List<CustomMerchantOffer> customOffers, List<CustomMerchantBuyOrder> customBuyOrders) {
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
            offers.add(new MerchantOffer(merchantId + "-" + index, item, offer.price(), name, merchantId));
        }
        List<MerchantBuyOffer> buyOffers = new ArrayList<>();
        for (int index = 0; index < customBuyOrders.size() && index < 45; index++) {
            CustomMerchantBuyOrder order = customBuyOrders.get(index);
            ItemStack item = order.item().clone();
            item.setAmount(Math.min(order.amount(), item.getMaxStackSize()));
            buyOffers.add(new MerchantBuyOffer(merchantId + "-buy-" + index, order.item(),
                order.amount(), order.price(), itemName(order.item())));
        }
        openMerchantInventory(player, title, offers, merchantId, buyOffers);
    }

    private void openMerchantInventory(Player player, String title, List<MerchantOffer> offers) {
        openMerchantInventory(player, title, offers, "", List.of());
    }

    private void openMerchantInventory(Player player, String title, List<MerchantOffer> offers,
            String merchantId, List<MerchantBuyOffer> buyOffers) {
        if (offers.isEmpty()) {
            if (buyOffers.isEmpty() && merchantId.isBlank()) {
                player.sendMessage("§cCửa hàng hiện chưa có mặt hàng nào.");
                return;
            }
            MerchantMenuHolder holder = new MerchantMenuHolder(player.getUniqueId(), List.of(),
                merchantId, title, buyOffers);
            holder.inventory = Bukkit.createInventory(holder, 54, title);
            renderMerchantPage(holder.inventory, holder);
            player.openInventory(holder.inventory);
            return;
        }
        MerchantMenuHolder holder = new MerchantMenuHolder(player.getUniqueId(), List.copyOf(offers),
            merchantId, title, buyOffers);
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
        if (!holder.merchantId.isBlank()) inventory.setItem(47,
            menuItem(Material.HOPPER, "§bBán vật phẩm cho shop", List.of(
                "§7Đổi item trong túi thành VNCOIN.", "§7Giá thu mua riêng của shop được ưu tiên.",
                "§eNhấn để xem giá thu mua.")));
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
        if (event.getRawSlot() == 47 && !holder.merchantId.isBlank()) {
            openMerchantSellInventory(player, holder);
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
        openMerchantPurchaseConfirmation(player, event.getView().getTopInventory(), offer);
    }

    private void openMerchantSellInventory(Player player, MerchantMenuHolder source) {
        List<MerchantBuyOffer> offers = new ArrayList<>();
        List<ItemStack> seen = new ArrayList<>();
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType().isAir() || hasCoinTag(stack) || isLockedMerchantDelivery(stack)) continue;
            ItemStack template = stack.clone();
            template.setAmount(1);
            if (seen.stream().anyMatch(template::isSimilar)) continue;
            seen.add(template);
            boolean systemMerchant = "system-merchant".equals(source.merchantId);
            GroundSellQuote quote;
            String quoteSource;
            String alternativeWarning = null;
            if (systemMerchant) {
                quote = systemMerchantSellQuote(template);
                if (quote == null) {
                    debug("[RPG-DEBUG][VNCOIN][Merchant] system-quote-rejected player=" + player.getName()
                        + ", item=" + template.getType() + ", available=" + countExactItem(player, template)
                        + ", pdc-fingerprint=" + itemPdcFingerprint(template)
                        + ", reason=" + (isPluginTaggedItem(template)
                            ? "rpg-sell-value-zero-or-missing" : "material-not-allowlisted"));
                    continue;
                }
                quoteSource = isPluginTaggedItem(template) ? "rpg-sell-value" : "material-allowlist";
                alternativeWarning = systemMerchantAlternativeWarning(player, template, quote);
            } else {
                MerchantBuyOffer configured = source.buyOffers.stream()
                    .filter(value -> value.item().isSimilar(template))
                    .max(java.util.Comparator.comparingDouble(value -> (double) value.price() / value.amount()))
                    .orElse(null);
                if (configured == null) {
                    debug("[RPG-DEBUG][VNCOIN][Merchant] custom-quote-rejected player=" + player.getName()
                        + ", npc=" + source.merchantId + ", item=" + template.getType()
                        + ", pdc-fingerprint=" + itemPdcFingerprint(template)
                        + ", reason=no-order-at-this-npc");
                    continue;
                }
                quote = new GroundSellQuote(configured.amount(), configured.price());
                quoteSource = "npc-buy-order";
            }
            offers.add(new MerchantBuyOffer(source.merchantId + "-default-" + stack.getType().name(),
                template, quote.amount(), quote.price(), itemName(template), alternativeWarning));
            debug("[RPG-DEBUG][VNCOIN][Merchant] sell-quote player=" + player.getName()
                + ", npc=" + source.merchantId + ", scope=" + (systemMerchant ? "system" : "custom")
                + ", item=" + template.getType() + ", available=" + countExactItem(player, template)
                + ", required=" + quote.amount() + ", price=" + quote.price()
                + ", pdc-fingerprint=" + itemPdcFingerprint(template)
                + ", source=" + quoteSource + ", alternative="
                + (alternativeWarning == null ? "none" : alternativeWarning));
        }
        source.buyOffers.stream().filter(order -> seen.stream().noneMatch(order.item()::isSimilar))
            .limit(Math.max(0, 45 - offers.size())).forEach(offers::add);
        MerchantSellMenuHolder holder = new MerchantSellMenuHolder(player.getUniqueId(), source,
            List.copyOf(offers));
        Inventory inventory = Bukkit.createInventory(holder, 54, "VnMine • Bán item cho shop");
        holder.inventory = inventory;
        for (int slot = 0; slot < 54; slot++)
            inventory.setItem(slot, menuItem(Material.GRAY_STAINED_GLASS_PANE, "§r", List.of()));
        for (int index = 0; index < Math.min(45, holder.offers.size()); index++) {
            MerchantBuyOffer offer = holder.offers.get(index);
            ItemStack display = offer.item().clone();
            display.setAmount(Math.min(offer.amount(), display.getMaxStackSize()));
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() && meta.getLore() != null
                    ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add("§7Cần giao: §f" + offer.amount() + " item");
                lore.add("§7Nhận: §6" + offer.price() + " VNCOIN");
                if (offer.alternativeWarning() != null) lore.add(offer.alternativeWarning());
                lore.add("§eNhấn để đổi item lấy tiền.");
                display.setItemMeta(meta);
            }
            inventory.setItem(index, display);
            holder.offerSlots.put(index, offer);
        }
        inventory.setItem(49, menuItem(Material.ARROW, "§eQuay lại cửa hàng", List.of()));
        inventory.setItem(53, menuItem(Material.BARRIER, "§cĐóng", List.of()));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onMerchantSellMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MerchantSellMenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.viewer.equals(player.getUniqueId())
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (event.getRawSlot() == 49) {
            openMerchantInventory(player, holder.source.title, holder.source.allOffers,
                holder.source.merchantId, holder.source.buyOffers);
            return;
        }
        if (event.getRawSlot() == 53) {
            player.closeInventory();
            return;
        }
        MerchantBuyOffer offer = holder.offerSlots.get(event.getRawSlot());
        if (offer != null) openMerchantSellConfirmation(player, holder, offer);
    }

    private void openMerchantSellConfirmation(Player player, MerchantSellMenuHolder source, MerchantBuyOffer offer) {
        MerchantSellConfirmHolder holder = new MerchantSellConfirmHolder(player.getUniqueId(), source, offer);
        Inventory inventory = Bukkit.createInventory(holder, 54, "VnMine • Xác nhận bán item");
        holder.inventory = inventory;
        for (int slot = 0; slot < 54; slot++)
            inventory.setItem(slot, menuItem(Material.GRAY_STAINED_GLASS_PANE, "§r", List.of()));
        ItemStack display = offer.item().clone();
        display.setAmount(Math.min(offer.amount(), display.getMaxStackSize()));
        inventory.setItem(22, display);
        inventory.setItem(20, menuItem(Material.LIME_DYE, "§aXác nhận bán", List.of(
            "§7Lấy §f" + offer.amount() + " item§7 khỏi túi.", "§7Cộng §6" + offer.price() + " VNCOIN§7 vào ví.")));
        inventory.setItem(24, menuItem(Material.RED_DYE, "§cHủy", List.of("§7Quay lại danh sách thu mua.")));
        inventory.setItem(4, offer.alternativeWarning() == null
            ? menuItem(Material.PAPER, "§eKiểm tra giá trước khi xác nhận", List.of(
                "§7Giao dịch này trả theo giá của shop đang mở."))
            : menuItem(Material.BOOK, "§6Có shop khác trả giá tốt hơn", List.of(
                offer.alternativeWarning(), "§cNếu xác nhận, giao dịch vẫn bán theo giá system.")));
        inventory.setItem(49, menuItem(Material.PAPER, "§eKiểm tra túi trước khi xác nhận", List.of()));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onMerchantSellConfirmationClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MerchantSellConfirmHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.viewer.equals(player.getUniqueId())
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (event.getRawSlot() == 24) openMerchantSellInventory(player, holder.source.source);
        else if (event.getRawSlot() == 20) {
            player.closeInventory();
            sellMerchantBuyOrder(player, holder.source, holder.offer);
        }
    }

    private void sellMerchantBuyOrder(Player player, MerchantSellMenuHolder source, MerchantBuyOffer offer) {
        UUID playerId = player.getUniqueId();
        String merchantId = source.source.merchantId;
        int available = countExactItem(player, offer.item());
        if ("system-merchant".equals(merchantId)) {
            GroundSellQuote currentSystemQuote = systemMerchantSellQuote(offer.item());
            String currentAlternative = currentSystemQuote == null ? null
                : systemMerchantAlternativeWarning(player, offer.item(), currentSystemQuote);
            boolean quoteUnchanged = currentSystemQuote != null
                && currentSystemQuote.amount() == offer.amount() && currentSystemQuote.price() == offer.price()
                && java.util.Objects.equals(currentAlternative, offer.alternativeWarning());
            debug("[RPG-DEBUG][VNCOIN][Merchant] system-sale-validate player=" + player.getName()
                + ", item=" + offer.item().getType() + ", available=" + available
                + ", required=" + offer.amount() + ", current-quote="
                + (currentSystemQuote == null ? "none" : currentSystemQuote.amount() + "="
                    + currentSystemQuote.price()) + ", quote-unchanged=" + quoteUnchanged
                + ", pdc-fingerprint=" + itemPdcFingerprint(offer.item()));
            if (!quoteUnchanged) {
                player.sendMessage("§eBảng giá hoặc giá shop tham khảo đã thay đổi. Hãy kiểm tra báo giá mới.");
                openMerchantSellInventory(player, source.source);
                return;
            }
        } else {
            var currentQuote = plugin.getNpcManager() == null ? java.util.Optional.<NpcManager.MerchantBuyQuote>empty()
                : plugin.getNpcManager().getMerchantBuyQuote(merchantId, offer.item());
            boolean stillConfigured = currentQuote.isPresent()
                && currentQuote.get().amount() == offer.amount() && currentQuote.get().price() == offer.price();
            debug("[RPG-DEBUG][VNCOIN][Merchant] custom-sale-validate player=" + player.getName()
                + ", npc=" + merchantId + ", offer=" + offer.id() + ", item=" + offer.item().getType()
                + ", available=" + available + ", required=" + offer.amount()
                + ", pdc-keys=" + itemPdcFingerprint(offer.item()) + ", configured=" + stillConfigured);
            if (!stillConfigured) {
                player.sendMessage("§cGiá thu mua của NPC đã thay đổi hoặc vật phẩm không còn được cấu hình."
                    + " Giao dịch chưa thực hiện.");
                openMerchantSellInventory(player, source.source);
                return;
            }
        }
        if (!pendingMerchantSales.add(playerId)) {
            player.sendMessage("§eĐang xử lý giao dịch trước đó, vui lòng chờ.");
            return;
        }
        if (available < offer.amount()) {
            pendingMerchantSales.remove(playerId);
            debug("[RPG-DEBUG][VNCOIN][Merchant] sale-rejected player=" + player.getName()
                + ", npc=" + merchantId + ", offer=" + offer.id() + ", available=" + available
                + ", required=" + offer.amount() + ", reason=insufficient-items");
            player.sendMessage("§cBạn không có đủ " + offer.amount() + " item cần giao.");
            openMerchantSellInventory(player, source.source);
            return;
        }
        String transactionId = "merchant-sell:" + UUID.randomUUID();
        String ledgerReason = "Bán " + offer.amount() + " "
            + (offer.name().length() > 180 ? offer.name().substring(0, 180) : offer.name());
        ItemStack template = offer.item().clone();
        template.setAmount(1);
        List<byte[]> refundItems = serializeSaleRefund(template, offer.amount());
        if (refundItems.isEmpty()) {
            pendingMerchantSales.remove(playerId);
            player.sendMessage("§cKhông tạo được bản sao an toàn để hoàn tác giao dịch; chưa lấy item.");
            return;
        }
        String createdAt = Instant.now().toString();
        player.sendMessage("§7Đang khóa item và ghi giao dịch an toàn...");
        database.executeGameplayDatabaseTask("wallet.merchant-sell player=" + player.getName()
                + " offer=" + offer.id(), () -> {
                    database.createMerchantSaleIntent(playerId.toString(), transactionId,
                        template.serializeAsBytes(), offer.amount(), offer.price(), "merchant-sell",
                        playerId.toString(), ledgerReason, createdAt);
                    return null;
                })
            .whenComplete((ignored, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (failure != null) {
                    pendingMerchantSales.remove(playerId);
                    plugin.getLogger().log(Level.SEVERE, "[Economy][MerchantSale] Could not create sale intent: player="
                        + player.getName() + ", npc=" + merchantId + ", offer=" + offer.id()
                        + ", transaction=" + transactionId, failure);
                    if (player.isOnline()) player.sendMessage("§cKhông lưu được giao dịch an toàn; chưa lấy item.");
                    return;
                }
                lockedMerchantSales.add(transactionId);
                debug("[RPG-DEBUG][VNCOIN][MerchantSale] intent-prepared player=" + player.getName()
                    + ", npc=" + merchantId + ", offer=" + offer.id() + ", amount=" + offer.amount()
                    + ", price=" + offer.price() + ", transaction=" + transactionId);
                if (!player.isOnline()) {
                    pendingMerchantSales.remove(playerId);
                    debug("[RPG-DEBUG][VNCOIN][MerchantSale] prepared-unclaimed player=" + player.getName()
                        + ", transaction=" + transactionId + ", reason=player-offline-before-tag");
                    return;
                }
                if (!tagExactItemsForSale(player, template, offer.amount(), transactionId)) {
                    pendingMerchantSales.remove(playerId);
                    cancelPreparedSale(player, transactionId, "inventory-changed-before-tag");
                    player.sendMessage("§cTúi đồ đã thay đổi hoặc không thể khóa item; giao dịch chưa thực hiện.");
                    openMerchantSellInventory(player, source.source);
                    return;
                }
                try {
                    player.saveData();
                } catch (RuntimeException saveFailure) {
                    pendingMerchantSales.remove(playerId);
                    plugin.getLogger().log(Level.SEVERE, "[Economy][MerchantSale] Could not persist tagged inventory; "
                        + "escrow stays pending for reconnect recovery: player=" + player.getName()
                        + ", transaction=" + transactionId, saveFailure);
                    player.sendMessage("§eGiao dịch đang được giữ an toàn; hãy mở lại game để hệ thống đối soát.");
                    return;
                }
                debug("[RPG-DEBUG][VNCOIN][MerchantSale] item-locked player=" + player.getName()
                    + ", npc=" + merchantId + ", item=" + template.getType() + ", amount=" + offer.amount()
                    + ", transaction=" + transactionId + ", pdc-fingerprint=" + itemPdcFingerprint(template));
                database.executeGameplayDatabaseTask("wallet.merchant-sale-tagged player=" + player.getName(),
                        () -> database.markMerchantSaleTagged(playerId.toString(), transactionId))
                    .whenComplete((tagged, tagFailure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (tagFailure != null || !Boolean.TRUE.equals(tagged)) {
                            pendingMerchantSales.remove(playerId);
                            plugin.getLogger().log(Level.SEVERE, "[Economy][MerchantSale] Could not persist tagged "
                                + "sale state; escrow remains locked: player=" + player.getName()
                                + ", transaction=" + transactionId, tagFailure);
                            if (player.isOnline()) player.sendMessage("§eGiao dịch đang được giữ an toàn; hãy thử mở /vncoin.");
                            return;
                        }
                        if (!player.isOnline()) {
                            pendingMerchantSales.remove(playerId);
                            debug("[RPG-DEBUG][VNCOIN][MerchantSale] deferred-offline player=" + player.getName()
                                + ", transaction=" + transactionId + ", stage=tagged");
                            return;
                        }
                        if (!consumeTaggedMerchantSale(player, transactionId, offer.amount())) {
                            pendingMerchantSales.remove(playerId);
                            plugin.getLogger().severe("[Economy][MerchantSale] Tagged item amount mismatch; "
                                + "escrow remains recoverable: player=" + player.getName()
                                + ", transaction=" + transactionId + ", required=" + offer.amount());
                            player.sendMessage("§eKhông thể đối soát số item; giao dịch đang được giữ an toàn.");
                            return;
                        }
                        try {
                            player.saveData();
                        } catch (RuntimeException removeFailure) {
                            pendingMerchantSales.remove(playerId);
                            plugin.getLogger().log(Level.SEVERE, "[Economy][MerchantSale] Could not persist escrow "
                                + "consumption; tagged transaction remains recoverable: player=" + player.getName()
                                + ", transaction=" + transactionId, removeFailure);
                            player.sendMessage("§eGiao dịch đang chờ đối soát an toàn; hãy mở lại game.");
                            return;
                        }
                        completeMerchantSale(player, transactionId, refundItems, merchantId, offer);
                    }));
            }));
    }

    private List<byte[]> serializeSaleRefund(ItemStack template, int amount) {
        if (template == null || template.getType().isAir() || amount < 1 || amount > 100_000) return List.of();
        List<byte[]> result = new ArrayList<>();
        int maxStack = Math.max(1, template.getMaxStackSize());
        int remaining = amount;
        while (remaining > 0) {
            ItemStack stack = template.clone();
            int stackAmount = Math.min(remaining, maxStack);
            stack.setAmount(stackAmount);
            result.add(stack.serializeAsBytes());
            if (result.size() > 45) return List.of();
            remaining -= stackAmount;
        }
        return List.copyOf(result);
    }

    private boolean tagExactItemsForSale(Player player, ItemStack template, int amount, String transactionId) {
        ItemStack[] original = player.getInventory().getStorageContents();
        ItemStack[] planned = new ItemStack[original.length];
        for (int index = 0; index < original.length; index++)
            planned[index] = original[index] == null ? null : original[index].clone();
        int remaining = amount;
        for (int index = 0; index < planned.length && remaining > 0; index++) {
            ItemStack item = planned[index];
            if (item == null || item.getType().isAir() || !item.isSimilar(template)
                    || isLockedMerchantDelivery(item) || merchantSaleId(item) != null) continue;
            int taken = Math.min(remaining, item.getAmount());
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;
            meta.getPersistentDataContainer().set(merchantSaleIdKey, PersistentDataType.STRING, transactionId);
            meta.getPersistentDataContainer().set(merchantSaleAmountKey, PersistentDataType.INTEGER, taken);
            item.setItemMeta(meta);
            remaining -= taken;
        }
        if (remaining > 0) return false;
        player.getInventory().setStorageContents(planned);
        return true;
    }

    private boolean hasTaggedMerchantSale(Player player, String transactionId) {
        for (ItemStack item : player.getInventory().getStorageContents())
            if (transactionId.equals(merchantSaleId(item))) return true;
        for (ItemStack item : player.getEnderChest().getContents())
            if (transactionId.equals(merchantSaleId(item))) return true;
        return transactionId.equals(merchantSaleId(player.getItemOnCursor()));
    }

    private boolean consumeTaggedMerchantSale(Player player, String transactionId, int expectedAmount) {
        ItemStack[] storage = player.getInventory().getStorageContents();
        ItemStack[] plannedStorage = cloneItems(storage);
        ItemStack[] ender = player.getEnderChest().getContents();
        ItemStack[] plannedEnder = cloneItems(ender);
        int consumed = consumeTaggedSaleFromArray(plannedStorage, transactionId);
        consumed += consumeTaggedSaleFromArray(plannedEnder, transactionId);
        ItemStack cursor = player.getItemOnCursor();
        ItemStack plannedCursor = cursor == null ? null : cursor.clone();
        if (plannedCursor != null && transactionId.equals(merchantSaleId(plannedCursor))) {
            ItemStack[] cursorSlot = {plannedCursor};
            consumed += consumeTaggedSaleFromArray(cursorSlot, transactionId);
            plannedCursor = cursorSlot[0];
        }
        if (consumed != expectedAmount) return false;
        player.getInventory().setStorageContents(plannedStorage);
        player.getEnderChest().setContents(plannedEnder);
        player.setItemOnCursor(plannedCursor);
        return true;
    }

    private int consumeTaggedSaleFromArray(ItemStack[] items, String transactionId) {
        int consumed = 0;
        for (int index = 0; index < items.length; index++) {
            ItemStack item = items[index];
            if (item == null || item.getType().isAir() || !transactionId.equals(merchantSaleId(item))) continue;
            ItemMeta meta = item.getItemMeta();
            Integer take = meta == null ? null
                : meta.getPersistentDataContainer().get(merchantSaleAmountKey, PersistentDataType.INTEGER);
            if (take == null || take < 1 || take > item.getAmount()) return -100_000;
            consumed += take;
            if (take == item.getAmount()) {
                items[index] = null;
            } else {
                item.setAmount(item.getAmount() - take);
                meta.getPersistentDataContainer().remove(merchantSaleIdKey);
                meta.getPersistentDataContainer().remove(merchantSaleAmountKey);
                item.setItemMeta(meta);
            }
        }
        return consumed;
    }

    private ItemStack[] cloneItems(ItemStack[] items) {
        ItemStack[] copy = new ItemStack[items.length];
        for (int index = 0; index < items.length; index++) copy[index] = items[index] == null ? null : items[index].clone();
        return copy;
    }

    private void cancelPreparedSale(Player player, String transactionId, String reason) {
        UUID playerId = player.getUniqueId();
        database.executeGameplayDatabaseTask("wallet.merchant-sale-cancel player=" + player.getName(),
                () -> database.cancelPreparedMerchantSale(playerId.toString(), transactionId))
            .whenComplete((cancelled, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (Boolean.TRUE.equals(cancelled)) lockedMerchantSales.remove(transactionId);
                if (failure != null) plugin.getLogger().log(Level.SEVERE,
                    "[Economy][MerchantSale] Could not cancel unused prepared intent: player=" + player.getName()
                        + ", transaction=" + transactionId + ", reason=" + reason, failure);
                debug("[RPG-DEBUG][VNCOIN][MerchantSale] intent-cancel player=" + player.getName()
                    + ", transaction=" + transactionId + ", cancelled=" + Boolean.TRUE.equals(cancelled)
                    + ", reason=" + reason);
                if (player.isOnline() && !Boolean.TRUE.equals(cancelled)) reconcilePendingMerchantSales(player);
            }));
    }

    private void completeMerchantSale(Player player, String transactionId, List<byte[]> refundItems,
            String merchantId, MerchantBuyOffer offer) {
        UUID playerId = player.getUniqueId();
        database.executeGameplayDatabaseTask("wallet.merchant-sale-complete player=" + player.getName(),
                () -> database.completeMerchantSale(playerId.toString(), transactionId, refundItems))
            .whenComplete((completion, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                pendingMerchantSales.remove(playerId);
                if (failure != null || completion == null) {
                    plugin.getLogger().log(Level.SEVERE, "[Economy][MerchantSale] Final wallet step failed; "
                        + "escrow will be retried: player=" + player.getName() + ", transaction=" + transactionId,
                        failure);
                    if (player.isOnline()) {
                        player.sendMessage("§eItem đã được khóa và giao dịch đang chờ đối soát. Mở /vncoin để thử lại.");
                        reconcilePendingMerchantSales(player);
                    }
                    return;
                }
                lockedMerchantSales.remove(transactionId);
                debug("[RPG-DEBUG][VNCOIN][MerchantSale] completed player=" + player.getName()
                    + ", npc=" + merchantId + ", offer=" + offer.id() + ", status=" + completion.status()
                    + ", amount=" + offer.amount() + ", price=" + offer.price()
                    + ", balance=" + completion.balance() + ", transaction=" + transactionId);
                if (!player.isOnline()) return;
                if (completion.status() == PlayerProfileDatabaseManager.MerchantSaleCompletionStatus.REFUNDED) {
                    player.sendMessage("§cVí đã đạt giới hạn số dư; giao dịch bị hủy và item được hoàn qua kho giao dịch.");
                    reconcileMerchantDeliveries(player);
                } else {
                    if (completion.status() == PlayerProfileDatabaseManager.MerchantSaleCompletionStatus.PAID
                            && plugin.getQuestCatalogManager() != null) {
                        ItemStack sold = offer.item().clone();
                        sold.setAmount(offer.amount());
                        plugin.getQuestCatalogManager().recordShopTransaction(player, false, sold,
                            offer.price(), merchantId);
                    }
                    player.sendMessage("§aĐã bán " + offer.amount() + " item và nhận §6"
                        + offer.price() + " VNCOIN§a.");
                }
            }));
    }

    private void reconcilePendingMerchantSales(Player player) {
        UUID playerId = player.getUniqueId();
        if (!player.isOnline() || pendingMerchantSales.contains(playerId)
                || !reconcilingMerchantSales.add(playerId)) return;
        Set<String> taggedIds = new HashSet<>();
        for (ItemStack item : player.getInventory().getContents()) {
            String id = merchantSaleId(item);
            if (id != null) {
                taggedIds.add(id);
                lockedMerchantSales.add(id);
            }
        }
        for (ItemStack item : player.getEnderChest().getContents()) {
            String id = merchantSaleId(item);
            if (id != null) {
                taggedIds.add(id);
                lockedMerchantSales.add(id);
            }
        }
        database.executeGameplayDatabaseTask("wallet.merchant-sale-reconcile player=" + player.getName(),
                () -> database.getPendingMerchantSales(playerId.toString()))
            .whenComplete((sales, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (failure != null) {
                    reconcilingMerchantSales.remove(playerId);
                    plugin.getLogger().log(Level.SEVERE, "[Economy][MerchantSale] Could not load sale intents for "
                        + player.getName(), failure);
                    return;
                }
                Set<String> pendingIds = sales.stream().map(
                    PlayerProfileDatabaseManager.PendingMerchantSale::transactionId)
                    .collect(java.util.stream.Collectors.toSet());
                for (String taggedId : taggedIds) {
                    if (!pendingIds.contains(taggedId)) {
                        plugin.getLogger().severe("[Economy][MerchantSale] Found item lock without a pending intent; "
                            + "item remains locked for manual recovery: player=" + player.getName()
                            + ", transaction=" + taggedId);
                    }
                }
                debug("[RPG-DEBUG][VNCOIN][MerchantSale] reconcile-loaded player=" + player.getName()
                    + ", pending=" + sales.size() + ", tagged=" + taggedIds.size());
                processPendingMerchantSales(player, sales, 0);
            }));
    }

    private void processPendingMerchantSales(Player player,
            List<PlayerProfileDatabaseManager.PendingMerchantSale> sales, int index) {
        UUID playerId = player.getUniqueId();
        if (index >= sales.size() || !player.isOnline()) {
            reconcilingMerchantSales.remove(playerId);
            return;
        }
        PlayerProfileDatabaseManager.PendingMerchantSale sale = sales.get(index);
        String transactionId = sale.transactionId();
        lockedMerchantSales.add(transactionId);
        boolean hasTaggedItems = hasTaggedMerchantSale(player, transactionId);
        if ("PREPARED".equals(sale.state()) && !hasTaggedItems) {
            database.executeGameplayDatabaseTask("wallet.merchant-sale-cancel-prepared player=" + player.getName(),
                    () -> database.cancelPreparedMerchantSale(playerId.toString(), transactionId))
                .whenComplete((cancelled, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (failure != null) plugin.getLogger().log(Level.SEVERE,
                        "[Economy][MerchantSale] Could not cancel untagged prepared intent: player="
                            + player.getName() + ", transaction=" + transactionId, failure);
                    if (Boolean.TRUE.equals(cancelled)) {
                        lockedMerchantSales.remove(transactionId);
                        debug("[RPG-DEBUG][VNCOIN][MerchantSale] recovered-cancel player=" + player.getName()
                            + ", transaction=" + transactionId + ", reason=never-tagged");
                    }
                    processPendingMerchantSales(player, sales, index + 1);
                }));
            return;
        }
        if (hasTaggedItems) {
            if ("PREPARED".equals(sale.state())) {
                database.executeGameplayDatabaseTask("wallet.merchant-sale-recover-tagged player=" + player.getName(),
                        () -> database.markMerchantSaleTagged(playerId.toString(), transactionId))
                    .whenComplete((tagged, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (failure != null || !Boolean.TRUE.equals(tagged)) {
                            reconcilingMerchantSales.remove(playerId);
                            plugin.getLogger().log(Level.SEVERE,
                                "[Economy][MerchantSale] Could not advance recovered sale intent: player="
                                    + player.getName() + ", transaction=" + transactionId, failure);
                            return;
                        }
                        consumeAndCompleteRecoveredSale(player, sale, sales, index);
                    }));
                return;
            }
            consumeAndCompleteRecoveredSale(player, sale, sales, index);
            return;
        }
        completeRecoveredMerchantSale(player, sale, sales, index);
    }

    private void consumeAndCompleteRecoveredSale(Player player,
            PlayerProfileDatabaseManager.PendingMerchantSale sale,
            List<PlayerProfileDatabaseManager.PendingMerchantSale> sales, int index) {
        UUID playerId = player.getUniqueId();
        String id = sale.transactionId();
        if (!player.isOnline()) {
            reconcilingMerchantSales.remove(playerId);
            debug("[RPG-DEBUG][VNCOIN][MerchantSale] deferred-offline player=" + player.getName()
                + ", transaction=" + id + ", stage=recovered-tagged");
            return;
        }
        if (!consumeTaggedMerchantSale(player, id, sale.itemAmount())) {
            reconcilingMerchantSales.remove(playerId);
            plugin.getLogger().severe("[Economy][MerchantSale] Recovered lock amount mismatch; escrow remains pending: "
                + "player=" + player.getName() + ", transaction=" + id + ", expected=" + sale.itemAmount());
            return;
        }
        try {
            player.saveData();
        } catch (RuntimeException exception) {
            reconcilingMerchantSales.remove(playerId);
            plugin.getLogger().log(Level.SEVERE, "[Economy][MerchantSale] Could not persist recovered item "
                + "consumption: player=" + player.getName() + ", transaction=" + id, exception);
            return;
        }
        completeRecoveredMerchantSale(player, sale, sales, index);
    }

    private void completeRecoveredMerchantSale(Player player,
            PlayerProfileDatabaseManager.PendingMerchantSale sale,
            List<PlayerProfileDatabaseManager.PendingMerchantSale> sales, int index) {
        UUID playerId = player.getUniqueId();
        ItemStack template;
        try {
            template = ItemStack.deserializeBytes(sale.itemData());
        } catch (RuntimeException exception) {
            reconcilingMerchantSales.remove(playerId);
            plugin.getLogger().log(Level.SEVERE, "[Economy][MerchantSale] Stored escrow item cannot be decoded: "
                + "player=" + player.getName() + ", transaction=" + sale.transactionId(), exception);
            return;
        }
        List<byte[]> refundItems = serializeSaleRefund(template, sale.itemAmount());
        if (refundItems.isEmpty()) {
            reconcilingMerchantSales.remove(playerId);
            plugin.getLogger().severe("[Economy][MerchantSale] Cannot serialize sale refund: player="
                + player.getName() + ", transaction=" + sale.transactionId());
            return;
        }
        database.executeGameplayDatabaseTask("wallet.merchant-sale-recover-complete player=" + player.getName(),
                () -> database.completeMerchantSale(playerId.toString(), sale.transactionId(), refundItems))
            .whenComplete((completion, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (failure != null || completion == null) {
                    reconcilingMerchantSales.remove(playerId);
                    plugin.getLogger().log(Level.SEVERE, "[Economy][MerchantSale] Recovery wallet operation failed; "
                        + "intent remains pending: player=" + player.getName()
                        + ", transaction=" + sale.transactionId(), failure);
                    return;
                }
                lockedMerchantSales.remove(sale.transactionId());
                debug("[RPG-DEBUG][VNCOIN][MerchantSale] recovered player=" + player.getName()
                    + ", transaction=" + sale.transactionId() + ", state=" + sale.state()
                    + ", status=" + completion.status() + ", amount=" + sale.itemAmount()
                    + ", price=" + sale.price());
                if (completion.status() == PlayerProfileDatabaseManager.MerchantSaleCompletionStatus.REFUNDED)
                    reconcileMerchantDeliveries(player);
                if (player.isOnline()) processPendingMerchantSales(player, sales, index + 1);
                else reconcilingMerchantSales.remove(playerId);
            }));
    }

    private int countExactItem(Player player, ItemStack template) {
        long count = 0;
        for (ItemStack item : player.getInventory().getStorageContents())
            if (item != null && !item.getType().isAir() && item.isSimilar(template)) count += item.getAmount();
        return (int) Math.min(Integer.MAX_VALUE, count);
    }

    private static String itemName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() ? meta.getDisplayName()
            : item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String itemPdcFingerprint(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return "none";
        String keys = item.getItemMeta().getPersistentDataContainer().getKeys().stream()
            .map(key -> key.getNamespace() + ":" + key.getKey())
            .sorted().collect(java.util.stream.Collectors.joining("|"));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(item.serializeAsBytes());
            return "keys=" + keys + ";sha256=" + java.util.HexFormat.of().formatHex(digest, 0, 8);
        } catch (java.security.NoSuchAlgorithmException exception) {
            return "keys=" + keys + ";sha256=unavailable";
        }
    }

    private void openMerchantPurchaseConfirmation(Player player, Inventory returnInventory, MerchantOffer offer) {
        MerchantConfirmHolder holder = new MerchantConfirmHolder(player.getUniqueId(), returnInventory, offer);
        Inventory inventory = Bukkit.createInventory(holder, 54, "VnMine • Xác nhận mua");
        holder.inventory = inventory;
        ItemStack display = offer.item().clone();
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() && meta.getLore() != null
                ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("§7Số lượng: §f" + offer.item().getAmount());
            lore.add("§7Giá phải trả: §6" + offer.price() + " VNCOIN");
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        inventory.setItem(13, display);
        inventory.setItem(11, menuItem(Material.LIME_STAINED_GLASS_PANE, "§aXác nhận mua",
            List.of("§7Trừ §6" + offer.price() + " VNCOIN§7 và nhận vật phẩm.")));
        inventory.setItem(15, menuItem(Material.RED_STAINED_GLASS_PANE, "§cHủy",
            List.of("§7Quay lại cửa hàng, không trừ tiền.")));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onMerchantPurchaseConfirmationClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MerchantConfirmHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.viewer.equals(player.getUniqueId())
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (event.getRawSlot() == 11) {
            player.closeInventory();
            purchaseMerchantOffer(player, holder.offer);
        } else if (event.getRawSlot() == 15) {
            player.openInventory(holder.returnInventory);
        }
    }

    @EventHandler
    public void onMerchantInventoryDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof MerchantMenuHolder || holder instanceof MerchantConfirmHolder
                || holder instanceof MerchantSellMenuHolder || holder instanceof MerchantSellConfirmHolder) {
            event.setCancelled(true);
        }
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
            offers.add(new MerchantOffer(id, item, price, sanitizeVisibleName(name), "system-merchant"));
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
                if (plugin.getQuestCatalogManager() != null) {
                    plugin.getQuestCatalogManager().recordShopTransaction(player, true, offer.item(),
                        offer.price(), offer.shopId());
                }
                if (player.isOnline()) {
                    player.sendMessage("§aĐã thanh toán §6" + offer.price()
                        + " VNCOIN§a. Vật phẩm sẽ được giao an toàn vào túi khi còn chỗ.");
                    reconcileMerchantDeliveries(player);
                }
            }));
    }

    public void reconcileMerchantDeliveries(Player player) {
        reconcileMerchantDeliveriesInternal(player);
    }

    public void reconcileMerchantDeliveries(Player player, String requiredTransactionId, Runnable onComplete) {
        if (requiredTransactionId == null || requiredTransactionId.isBlank()) {
            throw new IllegalArgumentException("requiredTransactionId must not be blank");
        }
        UUID playerId = player.getUniqueId();
        if (onComplete != null) {
            List<MerchantDeliveryCallback> callbacks = merchantDeliveryCallbacks.computeIfAbsent(playerId,
                ignored -> new ArrayList<>());
            if (callbacks.stream().noneMatch(callback -> callback.transactionId().equals(requiredTransactionId))) {
                callbacks.add(new MerchantDeliveryCallback(requiredTransactionId, onComplete));
                debug("[RPG-DEBUG][Delivery] callback-registered player=" + player.getName()
                    + ", transaction=" + requiredTransactionId);
            } else {
                debug("[RPG-DEBUG][Delivery] callback-already-registered player=" + player.getName()
                    + ", transaction=" + requiredTransactionId);
            }
        }
        reconcileMerchantDeliveriesInternal(player);
    }

    private void reconcileMerchantDeliveriesInternal(Player player) {
        UUID playerId = player.getUniqueId();
        if (!reconcilingMerchantDeliveries.add(playerId)) {
            reconcileMerchantDeliveriesAgain.add(playerId);
            return;
        }
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
                debug("[RPG-DEBUG][Delivery] reconcile-loaded player=" + player.getName()
                    + ", pending=" + deliveries.size() + ", callbacks="
                    + merchantDeliveryCallbacks.getOrDefault(playerId, List.of()).size());
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
            if (reconcileMerchantDeliveriesAgain.remove(playerId) && player.isOnline()) {
                reconcileMerchantDeliveries(player);
                return;
            }
            List<MerchantDeliveryCallback> callbacks = merchantDeliveryCallbacks.get(playerId);
            if (callbacks != null && player.isOnline()) {
                List<MerchantDeliveryCallback> completed = new ArrayList<>();
                for (MerchantDeliveryCallback callback : callbacks) {
                    if (!containsMerchantDelivery(player, callback.transactionId())) {
                        debug("[RPG-DEBUG][Delivery] callback-deferred player=" + player.getName()
                            + ", transaction=" + callback.transactionId() + ", reason=target-item-not-in-inventory");
                        continue;
                    }
                    try {
                        callback.action().run();
                        completed.add(callback);
                        debug("[RPG-DEBUG][Delivery] callback-completed player=" + player.getName()
                            + ", transaction=" + callback.transactionId());
                    } catch (RuntimeException failure) {
                        plugin.getLogger().log(Level.SEVERE, "[Economy][Merchant] Delivery completion callback failed: "
                            + "player=" + player.getName() + ", transaction=" + callback.transactionId(), failure);
                    }
                }
                callbacks.removeAll(completed);
                if (callbacks.isEmpty()) merchantDeliveryCallbacks.remove(playerId);
            }
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
            debug("[RPG-DEBUG][Delivery] waiting-for-space player=" + player.getName()
                + ", transaction=" + delivery.transactionId() + ", online=" + player.isOnline());
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

    private String merchantSaleId(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getItemMeta() == null) return null;
        return item.getItemMeta().getPersistentDataContainer().get(merchantSaleIdKey, PersistentDataType.STRING);
    }

    private boolean isLockedMerchantSale(ItemStack item) {
        String id = merchantSaleId(item);
        return id != null && lockedMerchantSales.contains(id);
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

    private boolean containsLockedMerchantSale(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isLockedMerchantSale(item)) return true;
        }
        for (ItemStack item : player.getEnderChest().getContents()) {
            if (isLockedMerchantSale(item)) return true;
        }
        return isLockedMerchantSale(player.getItemOnCursor());
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

    private record MerchantDeliveryCallback(String transactionId, Runnable action) {
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
        Inventory inventory = Bukkit.createInventory(holder, 54, "VnMine • Tên item rơi");
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
        if (command.getName().equalsIgnoreCase("pay")) return handlePayCommand(sender, args);
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
        if (command.getName().equalsIgnoreCase("pay")) {
            if (args.length == 1) return matching(args[0], Bukkit.getOnlinePlayers().stream()
                .filter(target -> !(sender instanceof Player player)
                    || !target.getUniqueId().equals(player.getUniqueId()))
                .map(Player::getName).toList());
            if (args.length == 2) return matching(args[1], List.of("1"));
            return List.of();
        }
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

    public record CustomMerchantBuyOrder(ItemStack item, int amount, long price) {
        public CustomMerchantBuyOrder {
            item = item.clone();
            item.setAmount(1);
        }
    }

    private record MerchantOffer(String id, ItemStack item, long price, String name, String shopId) {
    }

    private record MerchantBuyOffer(String id, ItemStack item, int amount, long price, String name,
                                    String alternativeWarning) {
        private MerchantBuyOffer(String id, ItemStack item, int amount, long price, String name) {
            this(id, item, amount, price, name, null);
        }

        private MerchantBuyOffer {
            item = item.clone();
            item.setAmount(1);
        }
    }

    private record GroundSellQuote(int amount, long price) { }

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

    private record TransferTarget(UUID uuid, String name) { }

    private record PendingTransferName(int page, Inventory returnInventory) { }

    private static final class TransferPlayerHolder implements InventoryHolder {
        private final UUID viewer;
        private final int page;
        private final Map<Integer, TransferTarget> targets = new HashMap<>();
        private Inventory inventory;

        private TransferPlayerHolder(UUID viewer, int page) {
            this.viewer = viewer;
            this.page = page;
        }

        @Override public Inventory getInventory() { return inventory; }
    }

    private static final class TransferAmountHolder implements InventoryHolder {
        private final UUID viewer;
        private final TransferTarget target;
        private final long amount;
        private final Inventory parentMenu;
        private Inventory inventory;

        private TransferAmountHolder(UUID viewer, TransferTarget target, long amount, Inventory parentMenu) {
            this.viewer = viewer;
            this.target = target;
            this.amount = amount;
            this.parentMenu = parentMenu;
        }

        @Override public Inventory getInventory() { return inventory; }
    }

    private static final class TransferConfirmHolder implements InventoryHolder {
        private final UUID viewer;
        private final TransferTarget target;
        private final long amount;
        private final Inventory parentMenu;
        private Inventory inventory;

        private TransferConfirmHolder(UUID viewer, TransferTarget target, long amount, Inventory parentMenu) {
            this.viewer = viewer;
            this.target = target;
            this.amount = amount;
            this.parentMenu = parentMenu;
        }

        @Override public Inventory getInventory() { return inventory; }
    }

    private static final class MerchantMenuHolder implements InventoryHolder {
        private final UUID viewer;
        private final List<MerchantOffer> allOffers;
        private final String merchantId;
        private final String title;
        private final List<MerchantBuyOffer> buyOffers;
        private final Map<Integer, MerchantOffer> offers = new HashMap<>();
        private int page;
        private Inventory inventory;

        private MerchantMenuHolder(UUID viewer, List<MerchantOffer> allOffers, String merchantId,
                String title, List<MerchantBuyOffer> buyOffers) {
            this.viewer = viewer;
            this.allOffers = allOffers;
            this.merchantId = merchantId;
            this.title = title;
            this.buyOffers = List.copyOf(buyOffers);
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class MerchantConfirmHolder implements InventoryHolder {
        private final UUID viewer;
        private final Inventory returnInventory;
        private final MerchantOffer offer;
        private Inventory inventory;

        private MerchantConfirmHolder(UUID viewer, Inventory returnInventory, MerchantOffer offer) {
            this.viewer = viewer;
            this.returnInventory = returnInventory;
            this.offer = offer;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class MerchantSellMenuHolder implements InventoryHolder {
        private final UUID viewer;
        private final MerchantMenuHolder source;
        private final List<MerchantBuyOffer> offers;
        private final Map<Integer, MerchantBuyOffer> offerSlots = new HashMap<>();
        private Inventory inventory;

        private MerchantSellMenuHolder(UUID viewer, MerchantMenuHolder source, List<MerchantBuyOffer> offers) {
            this.viewer = viewer;
            this.source = source;
            this.offers = offers;
        }

        @Override public Inventory getInventory() { return inventory; }
    }

    private static final class MerchantSellConfirmHolder implements InventoryHolder {
        private final UUID viewer;
        private final MerchantSellMenuHolder source;
        private final MerchantBuyOffer offer;
        private Inventory inventory;

        private MerchantSellConfirmHolder(UUID viewer, MerchantSellMenuHolder source, MerchantBuyOffer offer) {
            this.viewer = viewer;
            this.source = source;
            this.offer = offer;
        }

        @Override public Inventory getInventory() { return inventory; }
    }
}
