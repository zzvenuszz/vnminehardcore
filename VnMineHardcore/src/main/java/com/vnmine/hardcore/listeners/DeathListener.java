package com.vnmine.hardcore.listeners;

import com.vnmine.hardcore.VnMineHardcore;
import com.vnmine.hardcore.managers.ConfigManager;
import com.vnmine.hardcore.managers.DeathPenaltyManager;
import com.vnmine.hardcore.managers.DeathRenameManager;
import com.vnmine.hardcore.managers.LogManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
public class DeathListener implements Listener {

    private final VnMineHardcore plugin;
    private final LogManager logManager;
    private final DeathRenameManager renameManager;
    private final DeathPenaltyManager deathPenaltyManager;
    private final ConfigManager config;
    private final Logger logger;
    private final NamespacedKey operatorGameModeKey;
    private final Map<UUID, Long> combatTagged = new HashMap<>();
    private final Map<UUID, RespawnLock> respawnLocks = new HashMap<>();
    private final Set<UUID> awaitingRespawnChoice = new HashSet<>();
    private final Set<UUID> noDamagePlayers = new HashSet<>();
    private long combatTagDurationMs;
    private BukkitRunnable combatCheckTask;

    private static final class RespawnLock {
        private final Location anchor;
        private final long interactionEndsAtMillis;
        private final long damageImmunityEndsAtMillis;
        private BukkitTask countdownTask;

        private RespawnLock(Location anchor, long interactionEndsAtMillis, long damageImmunityEndsAtMillis) {
            this.anchor = anchor;
            this.interactionEndsAtMillis = interactionEndsAtMillis;
            this.damageImmunityEndsAtMillis = damageImmunityEndsAtMillis;
        }
    }

    public DeathListener(VnMineHardcore plugin, ConfigManager config, DeathRenameManager renameManager, DeathPenaltyManager deathPenaltyManager) {
        this.plugin = plugin;
        this.logManager = plugin.getLogManager();
        this.renameManager = renameManager;
        this.deathPenaltyManager = deathPenaltyManager;
        this.config = config;
        this.logger = plugin.getLogger();
        this.operatorGameModeKey = new NamespacedKey(plugin, "operator_game_mode");

        // Load combat tag duration from config (default 30s)
        reloadCombatTagDuration();
        startCombatCheckTask();

        logger.info("[Death] Initialized: combat-tag-duration=" + (combatTagDurationMs / 1000) + "s");
    }

    public void reload() {
        reloadCombatTagDuration();
        if (!config.respawnProtectionEnabled) {
            for (UUID playerId : respawnLocks.keySet().toArray(new UUID[0])) clearRespawnLock(playerId);
        }
    }

    private void reloadCombatTagDuration() {
        this.combatTagDurationMs = config.combatTagDurationMs;
    }

    /**
     * Task chạy mỗi 1 giây để kiểm tra và gỡ combat tag hết hạn,
     * đồng thời thông báo thoát combat cho người chơi.
     */
    private void startCombatCheckTask() {
        if (combatCheckTask != null) {
            combatCheckTask.cancel();
        }

        combatCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                Iterator<Map.Entry<UUID, Long>> it = combatTagged.entrySet().iterator();

                while (it.hasNext()) {
                    Map.Entry<UUID, Long> entry = it.next();
                    if (now - entry.getValue() > combatTagDurationMs) {
                        it.remove();
                        Player player = plugin.getServer().getPlayer(entry.getKey());
                        if (player != null && player.isOnline()) {
                            player.sendActionBar(Component.text("§a§l✅ Thoát chiến đấu!"));
                        }
                    }
                }
            }
        };
        combatCheckTask.runTaskTimer(plugin, 20L, 20L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        clearRespawnLock(player.getUniqueId());
        awaitingRespawnChoice.add(player.getUniqueId());

        // Extract plain text death message from Component
        Component deathMsgComponent = event.deathMessage();
        String deathMessage = deathMsgComponent != null
            ? PlainTextComponentSerializer.plainText().serialize(deathMsgComponent)
            : "Unknown cause";

        logger.info("[Death] " + player.getName() + " DIED! Cause: " + deathMessage);

        // Set a clean death message for broadcast
        String deathDisplayName = plugin.getAliasManager() == null ? player.getName()
            : plugin.getAliasManager().displayName(player);
        event.deathMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
            .deserialize("§4§l☠ " + deathDisplayName + " đã chết (" + deathMessage + ")"));

        // Drop ALL inventory
        event.setKeepInventory(false);
        event.setKeepLevel(false);
        event.setDroppedExp(event.getDroppedExp() * 2);

        // Log the death
        logManager.logDeath(player, deathMessage);

        // Broadcast death message (if enabled)
        if (config.broadcastDeath) {
            plugin.getServer().broadcast(Component.text("§eSố lần chết: §c" + logManager.getDeathCount(player)));
        }

        // Play death sound (if enabled)
        if (config.playSound) {
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                online.playSound(online.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.0f, 0.5f);
            }
        }

        // Remove combat tag on death (player already died)
        combatTagged.remove(player.getUniqueId());

        // Force SURVIVAL mode immediately to prevent the hardcore spectator death screen.
        player.setGameMode(GameMode.SURVIVAL);

        // Update display name after death (if rename enabled)
        renameManager.updateDisplayName(player, logManager.getDeathCount(player));

        // Apply death penalty
        deathPenaltyManager.onPlayerDeath(player);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && awaitingRespawnChoice.contains(player.getUniqueId())) {
                sendRespawnChoice(player);
            }
        }, 1L);
    }

    private void sendRespawnChoice(Player player) {
        player.sendMessage(Component.text("Bạn đã chết. Chọn hồi sinh hoặc thoát game:", NamedTextColor.GOLD));
        player.sendMessage(
            Component.text("[HỒI SINH]", NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/vnrespawn"))
                .append(Component.text("   "))
                .append(
                    Component.text("[THOÁT GAME]", NamedTextColor.RED)
                        .decorate(TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/vnleave"))
                )
        );
    }

    public boolean requestRespawn(Player player) {
        if (!awaitingRespawnChoice.remove(player.getUniqueId())) return false;
        player.spigot().respawn();
        return true;
    }

    public boolean isAwaitingRespawnChoice(Player player) {
        return awaitingRespawnChoice.contains(player.getUniqueId());
    }

    public boolean toggleNoDamageMode(Player player) {
        UUID playerId = player.getUniqueId();
        if (noDamagePlayers.remove(playerId)) return false;
        noDamagePlayers.add(playerId);
        return true;
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // Nếu player không có bed spawn, respawn tại world spawn point
        if (!event.isBedSpawn() || event.getRespawnLocation() == null) {
            event.setRespawnLocation(player.getWorld().getSpawnLocation());
        }

        if (config.respawnProtectionEnabled &&
            (config.respawnProtectionSeconds > 0 || config.respawnDamageImmunitySeconds > 0)) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    startRespawnProtection(player);
                } else {
                    awaitingRespawnChoice.remove(player.getUniqueId());
                }
            }, 1L);
        } else {
            awaitingRespawnChoice.remove(player.getUniqueId());
        }

        // Apply death penalty on respawn (start recovery timer)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                deathPenaltyManager.onPlayerRespawn(player);
            }
        }, 10L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() == GameMode.SPECTATOR && !event.getPlayer().isOp()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cChế độ Quan sát đã bị tắt trên máy chủ này.");
        }
    }

    private void startRespawnProtection(Player player) {
        UUID playerId = player.getUniqueId();
        awaitingRespawnChoice.remove(playerId);
        clearRespawnLock(playerId);

        long now = System.currentTimeMillis();
        RespawnLock lock = new RespawnLock(
            player.getLocation().clone(),
            now + config.respawnProtectionSeconds * 1000L,
            now + ((long) config.respawnProtectionSeconds + config.respawnDamageImmunitySeconds) * 1000L
        );
        if (config.respawnProtectionSeconds == 0 && config.respawnDamageImmunitySeconds == 0) return;
        respawnLocks.put(playerId, lock);
        if (config.respawnProtectionSeconds > 0) {
            player.sendMessage("§e⏳ Bạn đang hồi sinh. Không thể tương tác trong §6" +
                config.respawnProtectionSeconds + "§e giây.");
        }
        if (config.respawnDamageImmunitySeconds > 0) {
            player.sendMessage("§aBạn được miễn nhiễm sát thương trong §6" +
                config.respawnDamageImmunitySeconds + "§a giây.");
        }
        showRespawnCountdown(player, lock);

        lock.countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || respawnLocks.get(playerId) != lock) {
                clearRespawnLock(playerId);
                return;
            }
            showRespawnCountdown(player, lock);
        }, 20L, 20L);
    }

    private void showRespawnCountdown(Player player, RespawnLock lock) {
        long now = System.currentTimeMillis();
        long remainingInteractionMillis = lock.interactionEndsAtMillis - now;
        long remainingImmunityMillis = lock.damageImmunityEndsAtMillis - now;
        if (remainingInteractionMillis <= 0 && remainingImmunityMillis <= 0) {
            clearRespawnLock(player.getUniqueId());
            player.sendTitle("§a§lĐÃ HỒI SINH", "§fBạn có thể tự do tương tác", 0, 40, 10);
            return;
        }

        if (remainingInteractionMillis > 0) {
            long remainingSeconds = (remainingInteractionMillis + 999L) / 1000L;
            String immunityStatus = remainingImmunityMillis > 0
                ? " | Miễn sát thương: " + ((remainingImmunityMillis + 999L) / 1000L) + "s"
                : "";
            player.sendTitle("§e§lĐANG HỒI SINH", "§fTự do sau §6" +
                remainingSeconds + "§f giây" + immunityStatus, 0, 25, 0);
        } else {
            long remainingSeconds = (remainingImmunityMillis + 999L) / 1000L;
            player.sendTitle("§a§lMIỄN NHIỄM SÁT THƯƠNG", "§fCòn §6" +
                remainingSeconds + "§f giây", 0, 25, 0);
        }
    }

    private boolean isRespawnLocked(Player player) {
        return config.respawnProtectionEnabled &&
            config.respawnProtectionSeconds > 0 &&
            respawnLocks.containsKey(player.getUniqueId()) &&
            respawnLocks.get(player.getUniqueId()).interactionEndsAtMillis > System.currentTimeMillis();
    }

    private boolean isDamageImmune(Player player) {
        return noDamagePlayers.contains(player.getUniqueId()) || isRespawnDamageImmune(player);
    }

    private boolean isRespawnDamageImmune(Player player) {
        if (!config.respawnProtectionEnabled) return false;
        if (awaitingRespawnChoice.contains(player.getUniqueId())) return true;
        RespawnLock lock = respawnLocks.get(player.getUniqueId());
        return lock != null && lock.damageImmunityEndsAtMillis > System.currentTimeMillis();
    }

    private void clearRespawnLock(UUID playerId) {
        RespawnLock lock = respawnLocks.remove(playerId);
        if (lock != null && lock.countdownTask != null) {
            lock.countdownTask.cancel();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRespawnGraceMove(PlayerMoveEvent event) {
        RespawnLock lock = respawnLocks.get(event.getPlayer().getUniqueId());
        Location destination = event.getTo();
        if (lock == null || destination == null) return;
        if (lock.interactionEndsAtMillis <= System.currentTimeMillis()) return;

        double radius = config.respawnMovementRadiusBlocks;
        if (destination.getWorld() == null ||
            !destination.getWorld().equals(lock.anchor.getWorld()) ||
            destination.distanceSquared(lock.anchor) > radius * radius) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRespawnGraceInteract(PlayerInteractEvent event) {
        if (isRespawnLocked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRespawnGraceEntityInteract(PlayerInteractEntityEvent event) {
        if (isRespawnLocked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRespawnGraceBlockBreak(BlockBreakEvent event) {
        if (isRespawnLocked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRespawnGraceBlockPlace(BlockPlaceEvent event) {
        if (isRespawnLocked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRespawnGraceEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && isRespawnLocked(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRespawnDamageImmunity(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isDamageImmune(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRespawnMobTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player player && isRespawnDamageImmune(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRespawnGraceInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && isRespawnLocked(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRespawnGraceInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && isRespawnLocked(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRespawnGraceInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && isRespawnLocked(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRespawnGraceDrop(PlayerDropItemEvent event) {
        if (isRespawnLocked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRespawnGracePickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && isRespawnLocked(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRespawnGraceConsume(PlayerItemConsumeEvent event) {
        if (isRespawnLocked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRespawnGraceSwapHands(PlayerSwapHandItemsEvent event) {
        if (isRespawnLocked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRespawnGraceBucketEmpty(PlayerBucketEmptyEvent event) {
        if (isRespawnLocked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRespawnGraceBucketFill(PlayerBucketFillEvent event) {
        if (isRespawnLocked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRespawnGraceFishing(PlayerFishEvent event) {
        if (isRespawnLocked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRespawnGraceShear(PlayerShearEntityEvent event) {
        if (isRespawnLocked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRespawnGraceBedEnter(PlayerBedEnterEvent event) {
        if (isRespawnLocked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) {
            String savedMode = player.getPersistentDataContainer().get(operatorGameModeKey, PersistentDataType.STRING);
            if (savedMode != null) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (!player.isOnline() || !player.isOp()) return;
                    try {
                        player.setGameMode(GameMode.valueOf(savedMode));
                        logger.info("[Death] Restored operator game mode for " + player.getName() + ": " + savedMode);
                    } catch (IllegalArgumentException exception) {
                        logger.warning("[Death] Ignored invalid stored operator game mode for " + player.getName() + ": " + savedMode);
                    }
                }, 1L);
            }
        }
        if (!player.isOp() && player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
            player.sendMessage("§cChế độ Quan sát đã bị tắt trên máy chủ này. Bạn đã được chuyển về Sinh tồn.");
        }
        logManager.trackFirstJoin(player);

        String joinDisplayName = plugin.getAliasManager() == null ? player.getName()
            : plugin.getAliasManager().displayName(player);
        event.joinMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
            .deserialize("§8[§a+§8] §7" + joinDisplayName));

        player.sendTitle(
            "§4§lVnMineHardcore",
            "§c§lHãy sống sót!",
            10, 40, 10
        );

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            player.sendMessage("§6=== VnMineHardcore ===");
            player.sendMessage("§c☠ Cái chết được ghi nhận, không tự động ban người chơi.");
            player.sendMessage("§e⚔ Sát thương x" + config.mobDamageMultiplier + "!");
            player.sendMessage("§b💧 Hệ thống khát nước!");
            player.sendMessage("§4🌋 Thiên tai: Blood Moon, Meteor, Storm, Solar Flare, Plague, Tornado, Eclipse");
            player.sendMessage("§a✊ Hãy cố gắng sống sót!");
        }, 60L);

        // Apply display name from death count on join (if rename enabled)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            renameManager.updateDisplayName(player, logManager.getDeathCount(player));
            deathPenaltyManager.onPlayerJoin(player);
        }, 40L);

        logger.info("[Death] " + player.getName() + " joined (deaths: " + logManager.getDeathCount(player) + ")");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (player.isOp()) {
            player.getPersistentDataContainer().set(operatorGameModeKey, PersistentDataType.STRING, player.getGameMode().name());
            player.saveData();
        }

        if (isCombatTagged(player)) {
            logger.info("[Death] " + player.getName() + " left while combat-tagged; no ban applied.");
        }

        clearRespawnLock(player.getUniqueId());
        awaitingRespawnChoice.remove(player.getUniqueId());
        noDamagePlayers.remove(player.getUniqueId());
        combatTagged.remove(player.getUniqueId());

        String quitDisplayName = plugin.getAliasManager() == null ? player.getName()
            : plugin.getAliasManager().displayName(player);
        event.quitMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
            .deserialize("§8[§c-§8] §7" + quitDisplayName));
    }

    /**
     * Tag player vào trạng thái combat.
     * Gọi từ CombatListener khi player bị đánh hoặc chủ động tấn công hostile mob.
     */
    public void tagCombat(Player player) {
        UUID uuid = player.getUniqueId();

        // Only send message if not already tagged (avoid spam)
        if (!combatTagged.containsKey(uuid)) {
            player.sendActionBar(Component.text("§c§l⚔ Vào chiến đấu!"));
        }

        combatTagged.put(uuid, System.currentTimeMillis());
        logger.fine("[Death] " + player.getName() + " combat tagged");
    }

    public void untagCombat(Player player) {
        UUID uuid = player.getUniqueId();
        if (combatTagged.remove(uuid) != null) {
            player.sendActionBar(Component.text("§a§l✅ Thoát chiến đấu!"));
            logger.fine("[Death] " + player.getName() + " combat untagged");
        }
    }

    public boolean isCombatTagged(Player player) {
        Long time = combatTagged.get(player.getUniqueId());
        if (time == null) return false;
        if (System.currentTimeMillis() - time > combatTagDurationMs) {
            combatTagged.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public boolean isPortalTravelBlocked(Player player) {
        return player.isDead() || awaitingRespawnChoice.contains(player.getUniqueId()) || isRespawnLocked(player);
    }

    public boolean isCombatTagEnabled() { return true; }
    public int getTaggedPlayerCount() { return combatTagged.size(); }
    public long getCombatTagDurationMs() { return combatTagDurationMs; }
}
