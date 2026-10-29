package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Keeps the real GUI parent for Back buttons and normalizes navigation icons. */
public final class MenuNavigationManager implements Listener {
    private static final int MAX_PARENT_LINKS = 4096;

    private final VnMineHardcore plugin;
    private final NamespacedKey backButtonKey;
    private final Map<Inventory, Inventory> parents = new LinkedHashMap<>();
    private final java.util.Set<UUID> restoring = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public MenuNavigationManager(VnMineHardcore plugin) {
        this.plugin = plugin;
        this.backButtonKey = new NamespacedKey(plugin, "menu_back_button");
    }

    public void linkParent(Inventory child, Inventory parent) {
        if (child == null || parent == null || child == parent) return;
        synchronized (parents) {
            parents.put(child, parent);
            while (parents.size() > MAX_PARENT_LINKS) {
                parents.remove(parents.keySet().iterator().next());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player) || event.isCancelled()) return;
        Inventory next = event.getInventory();
        Inventory current = player.getOpenInventory().getTopInventory();
        if (restoring.remove(player.getUniqueId())) {
            // A history restore must not create a child -> parent loop.
        } else if (next != current && isPluginInventory(next) && isPluginInventory(current)) {
            synchronized (parents) {
                parents.put(next, current);
                while (parents.size() > MAX_PARENT_LINKS) {
                    parents.remove(parents.keySet().iterator().next());
                }
            }
        }
        normalizeNavigationItems(next);
    }

    private void normalizeNavigationItems(Inventory inventory) {
        if (!isPluginInventory(inventory)) return;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack original = inventory.getItem(slot);
            if (original == null || original.getType().isAir()) continue;
            ItemMeta meta = original.getItemMeta();
            if (meta == null || !meta.hasDisplayName()) continue;
            String label = ChatColor.stripColor(meta.getDisplayName());
            if (label == null) continue;
            String normalized = label.trim().toLowerCase(java.util.Locale.ROOT);
            Material icon = navigationIcon(normalized);
            if (icon == null) continue;
            ItemStack replacement = original.clone();
            replacement.setType(icon);
            ItemMeta replacementMeta = replacement.getItemMeta();
            if (icon == Material.CRAFTING_TABLE) {
                replacementMeta.getPersistentDataContainer().set(backButtonKey, PersistentDataType.BYTE, (byte) 1);
            }
            replacement.setItemMeta(replacementMeta);
            inventory.setItem(slot, replacement);
        }
    }

    private Material navigationIcon(String label) {
        if (label.contains("quay lại") || label.contains("trở lại") || label.contains("trở về")
                || label.contains("về menu") || label.contains("về trang") || label.contains("back to")) {
            return Material.CRAFTING_TABLE;
        }
        if (label.contains("trang trước") || label.contains("đại lục trước")
                || label.contains("trước đó") || label.contains("previous page")) {
            return Material.LIME_CARPET;
        }
        if (label.contains("trang sau") || label.contains("trang tiếp") || label.contains("đại lục tiếp")
                || label.contains("tiếp theo") || label.contains("next page")) {
            return Material.CYAN_CARPET;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBackButtonClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()
                || !clicked.getItemMeta().getPersistentDataContainer().has(backButtonKey, PersistentDataType.BYTE)) return;
        Inventory source = event.getView().getTopInventory();
        Inventory parent;
        synchronized (parents) {
            parent = parents.remove(source);
        }
        if (parent == null) return;
        event.setCancelled(true);
        UUID playerId = player.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            restoring.add(playerId);
            try {
                player.openInventory(parent);
            } catch (RuntimeException exception) {
                restoring.remove(playerId);
                plugin.getLogger().log(Level.WARNING, "Could not return " + player.getName()
                    + " to the previous plugin menu.", exception);
            }
        });
    }

    private boolean isPluginInventory(Inventory inventory) {
        if (inventory == null) return false;
        InventoryHolder holder = inventory.getHolder();
        return holder != null && holder.getClass().getName().startsWith("com.vnmine.hardcore.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        restoring.remove(event.getPlayer().getUniqueId());
    }
}
