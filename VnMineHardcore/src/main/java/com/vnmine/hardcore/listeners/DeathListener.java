package com.vnmine.hardcore.listeners;

import com.vnmine.hardcore.VnMineHardcore;
import com.vnmine.hardcore.managers.BanManager;
import com.vnmine.hardcore.managers.ConfigManager;
import com.vnmine.hardcore.managers.DeathPenaltyManager;
import com.vnmine.hardcore.managers.DeathRenameManager;
import com.vnmine.hardcore.managers.LogManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class DeathListener implements Listener {

    private final VnMineHardcore plugin;
    private final BanManager banManager;
    private final LogManager logManager;
    private final DeathRenameManager renameManager;
    private final DeathPenaltyManager deathPenaltyManager;
    private final ConfigManager config;
    private final Logger logger;
    private final Map<UUID, Long> combatTagged = new HashMap<>();
    private long combatTagDurationMs;
    private BukkitRunnable combatCheckTask;

    public DeathListener(VnMineHardcore plugin, ConfigManager config, DeathRenameManager renameManager, DeathPenaltyManager deathPenaltyManager) {
        this.plugin = plugin;
        this.banManager = plugin.getBanManager();
        this.logManager = plugin.getLogManager();
        this.renameManager = renameManager;
        this.deathPenaltyManager = deathPenaltyManager;
        this.config = config;
        this.logger = plugin.getLogger();

        // Load combat tag duration from config (default 30s)
        reloadCombatTagDuration();
        startCombatCheckTask();

        logger.info("[Death] Initialized: ban-on-death=" + config.banOnDeath +
            ", combat-tag-duration=" + (combatTagDurationMs / 1000) + "s");
    }

    public void reload() {
        reloadCombatTagDuration();
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

        // Extract plain text death message from Component
        Component deathMsgComponent = event.deathMessage();
        String deathMessage = deathMsgComponent != null
            ? PlainTextComponentSerializer.plainText().serialize(deathMsgComponent)
            : "Unknown cause";

        logger.info("[Death] " + player.getName() + " DIED! Cause: " + deathMessage);

        // Set a clean death message for broadcast
        event.deathMessage(Component.text("§4§l☠ " + player.getName() + " đã chết (" + deathMessage + ")"));

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

        // Ban the player (if enabled)
        if (config.banOnDeath) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                banManager.banPlayer(player, "Đã chết trong VnMineHardcore!");
                logger.info("[Death] " + player.getName() + " has been banned permanently!");
            });
        }

        // Update display name after death (if rename enabled)
        renameManager.updateDisplayName(player, logManager.getDeathCount(player));

        // Apply death penalty
        deathPenaltyManager.onPlayerDeath(player);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        // Apply death penalty on respawn (start recovery timer)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            deathPenaltyManager.onPlayerRespawn(player);
        }, 10L);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        logManager.trackFirstJoin(player);

        event.joinMessage(Component.text("§8[§a+§8] §7" + player.getName()));

        player.sendTitle(
            "§4§lVnMineHardcore",
            "§c§lChết = Ban vĩnh viễn!",
            10, 40, 10
        );

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            player.sendMessage("§6=== VnMineHardcore ===");
            player.sendMessage("§c☠ Chết = Ban vĩnh viễn (Username + IP)");
            player.sendMessage("§e⚔ Sát thương x" + config.mobDamageMultiplier + "!");
            player.sendMessage("§b💧 Hệ thống khát nước!");
            player.sendMessage("§4🌋 Thiên tai: Blood Moon, Meteor, Storm, Solar Flare, Plague, Tornado, Eclipse");
            player.sendMessage("§a✊ Hãy cố gắng sống sót!");
        }, 60L);

        // Apply display name from death count on join (if rename enabled)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            renameManager.updateDisplayName(player, logManager.getDeathCount(player));
        }, 40L);

        logger.info("[Death] " + player.getName() + " joined (deaths: " + logManager.getDeathCount(player) + ")");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (isCombatTagged(player)) {
            logger.info("[Death] " + player.getName() + " combat-logged! Banning...");
            banManager.combatLogDeath(player);
        }

        // Remove from combat map on quit (if not banned)
        combatTagged.remove(player.getUniqueId());

        event.quitMessage(Component.text("§8[§c-§8] §7" + player.getName()));
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

    public boolean isCombatTagEnabled() { return true; }
    public boolean isBanOnDeathEnabled() { return config.banOnDeath; }
    public int getTaggedPlayerCount() { return combatTagged.size(); }
    public long getCombatTagDurationMs() { return combatTagDurationMs; }
}