package com.vnmine.hardcore.managers;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.Vector3F;
import com.vnmine.hardcore.VnMineHardcore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;
import org.bukkit.scheduler.BukkitTask;

/** Sends player-specific, non-interactive NPC quest/story markers using virtual text displays. */
public final class NpcMarkerManager implements Listener, AutoCloseable {
    private static final AtomicInteger NEXT_ENTITY_ID = new AtomicInteger(-3_000_000);
    private static final WrappedDataWatcher.Serializer CHAT_COMPONENT_SERIALIZER =
        WrappedDataWatcher.Registry.getChatComponentSerializer(false);
    private static final WrappedDataWatcher.Serializer INTEGER_SERIALIZER =
        WrappedDataWatcher.Registry.get(Integer.class);
    private static final WrappedDataWatcher.Serializer BYTE_SERIALIZER =
        WrappedDataWatcher.Registry.get(Byte.class);
    private static final WrappedDataWatcher.Serializer VECTOR3F_SERIALIZER =
        WrappedDataWatcher.Registry.get(Vector3F.class);

    private static final class MarkerEntry {
        private final UUID npcEntityUuid;
        private final int entityId = NEXT_ENTITY_ID.getAndDecrement();
        private final UUID entityUuid = UUID.randomUUID();
        private final Location spawnLocation;
        private String text;

        private MarkerEntry(UUID npcEntityUuid, Location spawnLocation, String text) {
            this.npcEntityUuid = npcEntityUuid;
            this.spawnLocation = spawnLocation.clone();
            this.text = text;
        }
    }

    private final VnMineHardcore plugin;
    private final ProtocolManager protocolManager;
    private final Map<UUID, Map<String, MarkerEntry>> markersByViewer = new HashMap<>();
    private final Map<UUID, Long> lastPacketWarningAt = new HashMap<>();
    private final BukkitTask updateTask;
    private boolean closed;

    public NpcMarkerManager(VnMineHardcore plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
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
        lastPacketWarningAt.remove(viewerId);
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
            markersByViewer.remove(viewerId);
            lastPacketWarningAt.remove(viewerId);
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
                destroy(viewer, entry.getValue());
                existing.remove(entry.getKey());
                debug("marker-hidden player=" + viewer.getName() + ", npc=" + entry.getKey()
                    + ", reason=" + (text == null ? "eligibility-or-range"
                        : target == null || !target.entity().isValid() ? "npc-unavailable"
                            : entityReplaced ? "npc-entity-replaced" : "npc-location-changed"));
            } else if (!text.equals(entry.getValue().text)) {
                String previousText = entry.getValue().text;
                entry.getValue().text = text;
                if (sendMetadata(viewer, entry.getValue())) {
                    debug("marker-updated player=" + viewer.getName() + ", npc=" + entry.getKey()
                        + ", text=" + text);
                } else {
                    entry.getValue().text = previousText;
                }
            }
        }
        for (Map.Entry<String, String> entry : desired.entrySet()) {
            if (existing.containsKey(entry.getKey())) continue;
            NpcManager.NpcMarkerTarget target = targetById.get(entry.getKey());
            if (target == null) continue;
            MarkerEntry marker = new MarkerEntry(target.entity().getUniqueId(),
                target.entity().getLocation(), entry.getValue());
            if (spawn(viewer, target.entity(), marker)) {
                existing.put(entry.getKey(), marker);
                debug("marker-shown player=" + viewer.getName() + ", npc=" + entry.getKey()
                    + ", type=" + target.type() + ", text=" + entry.getValue());
            }
        }
        if (existing.isEmpty()) markersByViewer.remove(viewerId);
    }

    private boolean spawn(Player viewer, Entity npc, MarkerEntry marker) {
        Location location = npc.getLocation();
        PacketContainer spawn = protocolManager.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
        spawn.getIntegers().write(0, marker.entityId).write(1, 0);
        spawn.getUUIDs().write(0, marker.entityUuid);
        spawn.getEntityTypeModifier().write(0, EntityType.TEXT_DISPLAY);
        spawn.getDoubles().write(0, location.getX()).write(1, location.getY() + 2.45)
            .write(2, location.getZ());
        spawn.getBytes().write(0, (byte) 0).write(1, (byte) 0).write(2, (byte) 0);
        spawn.getVectors().write(0, new Vector());
        if (!sendPacket(viewer, spawn)) return false;
        if (sendMetadata(viewer, marker)) return true;
        destroy(viewer, marker);
        return false;
    }

    private boolean sendMetadata(Player viewer, MarkerEntry marker) {
        PacketContainer metadata = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
        metadata.getIntegers().write(0, marker.entityId);
        metadata.getDataValueCollectionModifier().write(0, List.of(
            new WrappedDataValue(15, BYTE_SERIALIZER, (byte) 3),
            new WrappedDataValue(12, VECTOR3F_SERIALIZER, new Vector3F(markerScale(), markerScale(), markerScale())),
            new WrappedDataValue(23, CHAT_COMPONENT_SERIALIZER,
                WrappedChatComponent.fromLegacyText(marker.text).getHandle()),
            new WrappedDataValue(25, INTEGER_SERIALIZER, 0),
            new WrappedDataValue(26, BYTE_SERIALIZER, (byte) -1),
            new WrappedDataValue(27, BYTE_SERIALIZER, (byte) 0)
        ));
        return sendPacket(viewer, metadata);
    }

    private void destroy(Player viewer, MarkerEntry marker) {
        PacketContainer destroy = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
        destroy.getIntLists().write(0, List.of(marker.entityId));
        sendPacket(viewer, destroy);
    }

    private boolean sendPacket(Player viewer, PacketContainer packet) {
        try {
            protocolManager.sendServerPacket(viewer, packet);
            return true;
        } catch (RuntimeException exception) {
            long now = System.currentTimeMillis();
            long previous = lastPacketWarningAt.getOrDefault(viewer.getUniqueId(), 0L);
            if (now - previous >= 30_000L) {
                lastPacketWarningAt.put(viewer.getUniqueId(), now);
                plugin.getLogger().log(Level.WARNING, "[NPC][Marker] Could not send marker packet to player="
                    + viewer.getName() + ".", exception);
            }
            return false;
        }
    }

    private void clearAllMarkers() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Map<String, MarkerEntry> entries = markersByViewer.remove(viewer.getUniqueId());
            if (entries == null) continue;
            for (MarkerEntry marker : entries.values()) destroy(viewer, marker);
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
        lastPacketWarningAt.clear();
        HandlerList.unregisterAll(this);
    }
}
