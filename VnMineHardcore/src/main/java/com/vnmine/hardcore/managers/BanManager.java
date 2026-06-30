package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

public class BanManager {

    private final VnMineHardcore plugin;
    private final LogManager logManager;
    private final ConfigManager config;

    public BanManager(VnMineHardcore plugin, ConfigManager config) {
        this.plugin = plugin;
        this.logManager = plugin.getLogManager();
        this.config = config;
        // Clear all old bans on startup to prevent false bans from plugin reload
        clearAllBans();
    }

    /**
     * Clear all bans created by this plugin on startup
     */
    public void clearAllBans() {
        try {
            BanList nameBanList = Bukkit.getBanList(BanList.Type.NAME);
            BanList ipBanList = Bukkit.getBanList(BanList.Type.IP);

            Set<? extends org.bukkit.BanEntry> nameBans = nameBanList.getBanEntries();
            Set<? extends org.bukkit.BanEntry> ipBans = ipBanList.getBanEntries();

            int nameCount = 0, ipCount = 0;

            for (org.bukkit.BanEntry entry : nameBans) {
                String source = entry.getSource();
                if (source != null && source.equals("VnMineHardcore")) {
                    nameBanList.pardon(entry.getTarget());
                    nameCount++;
                }
            }

            for (org.bukkit.BanEntry entry : ipBans) {
                String source = entry.getSource();
                if (source != null && source.equals("VnMineHardcore")) {
                    ipBanList.pardon(entry.getTarget());
                    ipCount++;
                }
            }

            if (nameCount > 0 || ipCount > 0) {
                plugin.getLogger().info("[Ban] Cleared " + nameCount + " name bans + " + ipCount + " IP bans from previous session");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Ban] Could not clear old bans: " + e.getMessage());
        }
    }

    /**
     * Permanently ban a player: username + optionally IP address.
     */
    @SuppressWarnings("unchecked")
    public void banPlayer(Player player, String reason) {
        // Check if ban is enabled at all
        if (!config.banOnDeath) {
            plugin.getLogger().info("[Ban] Death ban disabled by config, skipping ban for " + player.getName());
            return;
        }

        // Check if player is already banned by this plugin
        BanList nameBanList = Bukkit.getBanList(BanList.Type.NAME);
        if (nameBanList.isBanned(player.getName())) {
            // Already banned, don't ban again
            return;
        }

        String playerName = player.getName();
        String ip = getPlayerIP(player);
        String uuid = player.getUniqueId().toString();

        // Ban using PlayerProfile (Paper 1.21.4 API: addBan(PlayerProfile, String, Instant, String))
        try {
            nameBanList.addBan(
                player.getPlayerProfile(),
                "§c☠ " + reason + " ☠\n§4Ban vĩnh viễn!",
                (Instant) null,
                "VnMineHardcore"
            );
        } catch (Exception e) {
            // Fallback: try the old API signature with String
            plugin.getLogger().warning("[Ban] PlayerProfile ban failed, trying fallback: " + e.getMessage());
            try {
                nameBanList.addBan(
                    playerName,
                    "§c☠ " + reason + " ☠\n§4Ban vĩnh viễn!",
                    (Date) null,
                    "VnMineHardcore"
                );
            } catch (Exception e2) {
                plugin.getLogger().severe("[Ban] CRITICAL: Could not ban player: " + e2.getMessage());
            }
        }

        plugin.getLogger().info("[Ban] BANNED " + playerName + " (UUID: " + uuid + ") - Reason: " + reason);

        // Ban IP only if enabled
        if (config.banIp && ip != null) {
            BanList ipBanList = Bukkit.getBanList(BanList.Type.IP);
            try {
                ipBanList.addBan(
                    ip,
                    "§c☠ " + reason + " ☠\n§4IP ban vĩnh viễn!",
                    (Date) null,
                    "VnMineHardcore"
                );
            } catch (Exception e) {
                plugin.getLogger().warning("[Ban] IP ban failed: " + e.getMessage());
            }
            plugin.getLogger().info("[Ban] Also banned IP " + ip + " for " + playerName);
        }

        // Log the ban
        logManager.logBan(playerName, uuid, ip, reason);

        // Build kick message based on whether IP was banned
        String kickMessage;
        if (config.banIp) {
            kickMessage =
                "§c☠ BẠN ĐÃ CHẾT ☠\n\n" +
                "§4§lBAN VĨNH VIỄN!\n" +
                "§c" + reason + "\n\n" +
                "§7Bạn đã chết trong VnMineHardcore\n" +
                "§7IP của bạn cũng đã bị cấm\n" +
                "§eThời gian: " + new Date().toString();
        } else {
            kickMessage =
                "§c☠ BẠN ĐÃ CHẾT ☠\n\n" +
                "§4§lBAN VĨNH VIỄN!\n" +
                "§c" + reason + "\n\n" +
                "§7Bạn đã chết trong VnMineHardcore\n" +
                "§eThời gian: " + new Date().toString();
        }

        // Kick player
        player.kickPlayer(kickMessage);
    }

    public void combatLogDeath(Player player) {
        banPlayer(player, "Combat Log - Bỏ chạy khi đang chiến đấu!");
    }

    /**
     * Unban a player by name
     */
    public void unbanPlayer(String playerName) {
        Bukkit.getBanList(BanList.Type.NAME).pardon(playerName);
        plugin.getLogger().info("[Ban] Unbanned player: " + playerName);
    }

    private String getPlayerIP(Player player) {
        InetSocketAddress address = player.getAddress();
        return address != null ? address.getAddress().getHostAddress() : null;
    }
}