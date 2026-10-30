package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Shows player-specific, non-interactive NPC quest/story markers using server-managed text displays. */
public final class NpcMarkerManager implements Listener, AutoCloseable {
    private static final class MarkerEntry {
        private final UUID npcEntityUuid;
        private final Location spawnLocation;
        private final TextDisplay display;
        private String text;

        private MarkerEntry(UUID npcEntityUuid, Location spawnLocation, TextDisplay display, String text) {
            this.npcEntityUuid = npcEntityUuid;
            this.spawnLocation = spawnLocation.clone();
            this.display = display;
            this.text = text;
        }
    }

    private final VnMineHardcore plugin;
    private final Map<UUID, Map<String, MarkerEntry>> markersByViewer = new HashMap<>();
    private final Map<UUID, Long> lastSpawnWarningAt = new HashMap<>();
    private final BukkitTask updateTask;
    private boolean closed;

    public NpcMarkerManager(VnMineHardcore plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        int interval = Math.max(5, Math.min(100,
            plugin.getConfig().getInt("rpg.npc.markers.update-interval-ticks", 20)));
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateMarkers, interval, interval);
        debug("initialized intervalTicks=" + interval + ", enabled=" + enabled()
            + ", rangeBlocks=" + trackingRange());
    }

    @EventHandler
    public void onViewerQuit(PlayerQuitEvent event) {
        UUID viewerId = event.getPlayer().getUniqueId();
        Map<String, MarkerEntry> entries = markersByViewer.remove(viewerId);
        int removed = entries == null ? 0 : entries.size();
        if (entries != null) entries.values().forEach(marker -> marker.display.remove());
        lastSpawnWarningAt.remove(viewerId);
        debug("viewer-cleanup player=" + event.getPlayer().getName() + ", markers=" + removed);
    }

    private void updateMarkers() {
        if (closed) return;
        if (!enabled() || plugin.getNpcManager() == null) {
            clearAllMarkers();
            return;
        }
        List<NpcManager.NpcMarkerTarget> targets = plugin.getNpcManager().getMarkerTargets();
        double range = trackingRange();
        double rangeSquared = range * range;
        Set<UUID> onlineViewers = new HashSet<>();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            onlineViewers.add(viewer.getUniqueId());
            Map<String, String> desired = new HashMap<>();
            for (NpcManager.NpcMarkerTarget target : targets) {
                Entity npc = target.entity();
                if (npc == null || !npc.isValid() || !npc.getWorld().equals(viewer.getWorld())
                        || npc.getLocation().distanceSquared(viewer.getLocation()) > rangeSquared
                        || !npc.getTrackedBy().contains(viewer)) continue;
                String marker = markerText(viewer, target);
                if (marker != null) desired.put(target.id(), marker);
            }
            reconcileViewer(viewer, desired, targets);
        }
        for (UUID viewerId : List.copyOf(markersByViewer.keySet())) {
            if (onlineViewers.contains(viewerId)) continue;
            Map<String, MarkerEntry> entries = markersByViewer.remove(viewerId);
            if (entries != null) entries.values().forEach(marker -> marker.display.remove());
            lastSpawnWarningAt.remove(viewerId);
        }
    }

    private String markerText(Player viewer, NpcManager.NpcMarkerTarget target) {
        boolean hasAvailableQuest = plugin.getQuestCatalogManager() != null
            && plugin.getQuestCatalogManager().hasAvailableQuestForMarker(viewer, target.id());
        boolean unreadStory = "story".equals(target.type()) && target.storyChapterId() != null
            && !plugin.getNpcManager().hasReadStoryChapter(viewer, target.storyChapterId());
        CharacterSelectionManager characters = plugin.getCharacterSelectionManager();
        boolean tutorialPending = "tutorial".equals(target.type()) && characters != null
            && characters.shouldShowTutorialMarker(viewer, target.id());
        if (!hasAvailableQuest && !unreadStory && !tutorialPending) return null;
        StringBuilder text = new StringBuilder();
        if (hasAvailableQuest) text.append("§e!");
        if (unreadStory || tutorialPending) {
            if (text.length() > 0) text.append(' ');
            text.append("§b?");
        }
        return text.toString();
    }

    private void reconcileViewer(Player viewer, Map<String, String> desired,
            List<NpcManager.NpcMarkerTarget> targets) {
        UUID viewerId = viewer.getUniqueId();
        Map<String, MarkerEntry> existing = markersByViewer.computeIfAbsent(viewerId, ignored -> new HashMap<>());
        Map<String, NpcManager.NpcMarkerTarget> targetById = new HashMap<>();
        for (NpcManager.NpcMarkerTarget target : targets) targetById.put(target.id(), target);
        for (Map.Entry<String, MarkerEntry> entry : List.copyOf(existing.entrySet())) {
            String text = desired.get(entry.getKey());
            NpcManager.NpcMarkerTarget target = targetById.get(entry.getKey());
            boolean entityReplaced = target != null && target.entity().isValid()
                && !target.entity().getUniqueId().equals(entry.getValue().npcEntityUuid);
            boolean locationChanged = target != null && target.entity().isValid()
                && (target.entity().getWorld() != entry.getValue().spawnLocation.getWorld()
                    || target.entity().getLocation().distanceSquared(entry.getValue().spawnLocation) > 0.25);
            if (text == null || target == null || !target.entity().isValid()
                    || entityReplaced || locationChanged) {
                destroy(entry.getValue());
                existing.remove(entry.getKey());
                debug("marker-hidden player=" + viewer.getName() + ", npc=" + entry.getKey()
                    + ", reason=" + (text == null ? "eligibility-or-range"
                        : target == null || !target.entity().isValid() ? "npc-unavailable"
                            : entityReplaced ? "npc-entity-replaced" : "npc-location-changed"));
            } else if (!text.equals(entry.getValue().text)) {
                entry.getValue().text = text;
                entry.getValue().display.setText(text);
                debug("marker-updated player=" + viewer.getName() + ", npc=" + entry.getKey()
                    + ", text=" + text);
            }
        }
        for (Map.Entry<String, String> entry : desired.entrySet()) {
            if (existing.containsKey(entry.getKey())) continue;
            NpcManager.NpcMarkerTarget target = targetById.get(entry.getKey());
            if (target == null) continue;
            MarkerEntry spawned = spawn(viewer, target.entity(), entry.getValue());
            if (spawned != null) {
                existing.put(entry.getKey(), spawned);
                debug("marker-shown player=" + viewer.getName() + ", npc=" + entry.getKey()
                    + ", type=" + target.type() + ", text=" + entry.getValue());
            }
        }
        if (existing.isEmpty()) markersByViewer.remove(viewerId);
    }

    private MarkerEntry spawn(Player viewer, Entity npc, String text) {
        Location npcLocation = npc.getLocation();
        Location markerLocation = npcLocation.clone().add(0.0, 2.45, 0.0);
        TextDisplay display = null;
        try {
            display = markerLocation.getWorld().spawn(markerLocation, TextDisplay.class, entity -> {
                entity.setText(text);
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setAlignment(TextDisplay.TextAlignment.CENTER);
                entity.setDefaultBackground(false);
                entity.setShadowed(true);
                entity.setSeeThrough(false);
                entity.setGravity(false);
                entity.setPersistent(false);
                entity.setInvulnerable(true);
                entity.setVisibleByDefault(false);
                float scale = markerScale();
                entity.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(scale, scale, scale), new Quaternionf()));
            });
            viewer.showEntity(plugin, display);
            return new MarkerEntry(npc.getUniqueId(), npcLocation, display, text);
        } catch (RuntimeException exception) {
            if (display != null && display.isValid()) display.remove();
            long now = System.currentTimeMillis();
            long previous = lastSpawnWarningAt.getOrDefault(viewer.getUniqueId(), 0L);
            if (now - previous >= 30_000L) {
                lastSpawnWarningAt.put(viewer.getUniqueId(), now);
                plugin.getLogger().log(Level.WARNING, "[NPC][Marker] Could not create marker for player="
                    + viewer.getName() + ".", exception);
            }
            return null;
        }
    }

    private void destroy(MarkerEntry marker) {
        if (marker.display.isValid()) marker.display.remove();
    }

    private void clearAllMarkers() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Map<String, MarkerEntry> entries = markersByViewer.remove(viewer.getUniqueId());
            if (entries == null) continue;
            for (MarkerEntry marker : entries.values()) destroy(marker);
            debug("markers-cleared player=" + viewer.getName() + ", count=" + entries.size()
                + ", reason=disabled");
        }
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("rpg.npc.markers.enabled", true);
    }

    private double trackingRange() {
        double configured = plugin.getConfig().getDouble("rpg.npc.markers.tracking-range-blocks", 48.0);
        if (!Double.isFinite(configured) || configured < 8.0 || configured > 128.0) return 48.0;
        return configured;
    }

    private float markerScale() {
        double configured = plugin.getConfig().getDouble("rpg.npc.markers.scale", 2.0);
        return (float) (Double.isFinite(configured) && configured >= 1.0 && configured <= 4.0
            ? configured : 2.0);
    }

    private void debug(String message) {
        if (plugin.getConfig().getBoolean("logging.console-debug", false)) {
            plugin.getLogger().info("[RPG-DEBUG][NPCMarker] " + message);
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        updateTask.cancel();
        clearAllMarkers();
        markersByViewer.clear();
        lastSpawnWarningAt.clear();
        HandlerList.unregisterAll(this);
    }
}
