package com.vnmine.hardcore.managers;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedEnumEntityUseAction;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import com.vnmine.hardcore.VnMineHardcore;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.scheduler.BukkitTask;

/** Packet-backed player-model overlays for managed NPCs, while Bukkit villagers remain their server hitboxes. */
final class PlayerNpcVisualManager {
    private static final Pattern UUID_PATTERN = Pattern.compile("\\\"id\\\"\\s*:\\s*\\\"([0-9a-fA-F]{32})\\\"");
    private static final Pattern TEXTURES_PATTERN = Pattern.compile(
        "\\\"name\\\"\\s*:\\s*\\\"textures\\\"\\s*,\\s*\\\"value\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"\\s*,\\s*\\\"signature\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private final VnMineHardcore plugin;
    private final NpcManager npcManager;
    private final ProtocolManager protocolManager;
    private final ExecutorService skinExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "VnMine-NpcSkin");
        thread.setDaemon(true);
        return thread;
    });
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5)).executor(skinExecutor).build();
    private final Map<UUID, Map<String, FakeNpc>> visibleByViewer = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Integer, String>> interactableByViewer = new ConcurrentHashMap<>();
    private final Map<String, SkinProperty> skinsByOwner = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<SkinProperty>> pendingSkins = new ConcurrentHashMap<>();
    private final Map<String, String> skinFailures = new ConcurrentHashMap<>();
    private final AtomicInteger nextEntityId = new AtomicInteger(-1);
    private final PacketAdapter interactionListener;
    private final BukkitTask updateTask;

    PlayerNpcVisualManager(VnMineHardcore plugin, NpcManager npcManager) {
        this.plugin = plugin;
        this.npcManager = npcManager;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        this.interactionListener = new PacketAdapter(plugin, ListenerPriority.NORMAL,
                PacketType.Play.Client.USE_ENTITY) {
            @Override public void onPacketReceiving(PacketEvent event) {
                int entityId;
                WrappedEnumEntityUseAction useAction;
                try {
                    entityId = event.getPacket().getIntegers().read(0);
                    useAction = event.getPacket().getEnumEntityUseActions().read(0);
                } catch (RuntimeException exception) {
                    return;
                }
                UUID viewerId = event.getPlayer().getUniqueId();
                Map<Integer, String> entries = interactableByViewer.get(viewerId);
                String npcId = entries == null ? null : entries.get(entityId);
                if (npcId == null) return;
                event.setCancelled(true);
                if (useAction == null || useAction.getAction() == EnumWrappers.EntityUseAction.ATTACK
                        || useAction.getHand() != EnumWrappers.Hand.MAIN_HAND) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player viewer = Bukkit.getPlayer(viewerId);
                    if (viewer != null && viewer.isOnline()) npcManager.handlePlayerNpcInteract(viewer, npcId);
                });
            }
        };
        protocolManager.addPacketListener(interactionListener);
        this.updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateVisibleNpcs, 1L, 10L);
    }

    void shutdown() {
        updateTask.cancel();
        protocolManager.removePacketListener(interactionListener);
        for (Player viewer : Bukkit.getOnlinePlayers()) clearViewer(viewer);
        skinExecutor.shutdownNow();
    }

    private void updateVisibleNpcs() {
        boolean enabled = plugin.getConfig().getBoolean("rpg.npc.player-model.enabled", true);
        for (NpcVisual npc : npcManager.getPlayerVisuals()) {
            UUID bukkitId = npcManager.entityIdFor(npc.id());
            Entity entity = bukkitId == null ? null : Bukkit.getEntity(bukkitId);
            if (entity instanceof org.bukkit.entity.Villager villager && villager.isValid()) {
                villager.setInvisible(enabled);
            }
        }
        if (!enabled) {
            for (Player viewer : Bukkit.getOnlinePlayers()) clearViewer(viewer);
            return;
        }
        Collection<NpcVisual> all = npcManager.getPlayerVisuals();
        Map<String, NpcVisual> byId = new HashMap<>();
        for (NpcVisual npc : all) byId.put(npc.id(), npc);
        double viewDistance = Math.max(8.0, Math.min(128.0,
            plugin.getConfig().getDouble("rpg.npc.player-model.view-distance-blocks", 48.0)));
        double viewDistanceSquared = viewDistance * viewDistance;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Map<String, FakeNpc> current = visibleByViewer.computeIfAbsent(viewer.getUniqueId(), ignored -> new ConcurrentHashMap<>());
            for (Map.Entry<String, FakeNpc> entry : List.copyOf(current.entrySet())) {
                NpcVisual npc = byId.get(entry.getKey());
                if (npc == null || !isVisibleTo(viewer, npc, viewDistanceSquared)) remove(viewer, entry.getValue());
            }
            for (NpcVisual npc : all) {
                if (!isVisibleTo(viewer, npc, viewDistanceSquared)) continue;
                ensureSkin(npc.skinOwner());
                String profileKey = profileKey(npc.skinOwner());
                FakeNpc old = current.get(npc.id());
                if (old == null || !old.profileKey().equals(profileKey)
                        || old.location().distanceSquared(npc.location()) > 0.01) {
                    if (old != null) remove(viewer, old);
                    spawn(viewer, npc, profileKey);
                } else {
                    rotate(viewer, old, npc.location().getYaw());
                }
            }
        }
        visibleByViewer.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
        interactableByViewer.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
    }

    private boolean isVisibleTo(Player viewer, NpcVisual npc, double rangeSquared) {
        Location location = npc.location();
        World world = location.getWorld();
        return world != null && viewer.getWorld().equals(world)
            && world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)
            && viewer.getLocation().distanceSquared(location) <= rangeSquared;
    }

    private void ensureSkin(String owner) {
        if (owner == null || owner.isBlank()) return;
        String key = owner.toLowerCase(java.util.Locale.ROOT);
        if (skinsByOwner.containsKey(key) || skinFailures.containsKey(key) || pendingSkins.containsKey(key)) return;
        CompletableFuture<SkinProperty> request = CompletableFuture.supplyAsync(() -> lookupSkin(owner), skinExecutor);
        CompletableFuture<SkinProperty> previous = pendingSkins.putIfAbsent(key, request);
        if (previous != null) return;
        request.whenComplete((skin, failure) -> {
            if (!plugin.isEnabled()) {
                pendingSkins.remove(key, request);
                return;
            }
            try {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    pendingSkins.remove(key, request);
                    if (!plugin.isEnabled()) return;
                    if (failure != null) {
                        String reason = rootMessage(failure);
                        skinFailures.put(key, reason);
                        plugin.getLogger().warning("[NPC][Skin] Could not load Minecraft account skin '" + owner + "': " + reason);
                    } else {
                        skinsByOwner.put(key, skin);
                        plugin.getLogger().info("[NPC][Skin] Loaded skin profile for '" + owner + "'.");
                    }
                    updateVisibleNpcs();
                });
            } catch (IllegalPluginAccessException ignored) {
                pendingSkins.remove(key, request);
            }
        });
    }

    private SkinProperty lookupSkin(String owner) {
        try {
            HttpResponse<String> account = get("https://api.mojang.com/users/profiles/minecraft/" + owner);
            if (account.statusCode() != 200) throw new IllegalArgumentException("account lookup returned HTTP " + account.statusCode());
            Matcher uuidMatch = UUID_PATTERN.matcher(account.body());
            if (!uuidMatch.find()) throw new IllegalArgumentException("account lookup returned no profile UUID");
            String uuid = uuidMatch.group(1);
            HttpResponse<String> profile = get("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
            if (profile.statusCode() != 200) throw new IllegalArgumentException("skin profile returned HTTP " + profile.statusCode());
            Matcher textures = TEXTURES_PATTERN.matcher(profile.body());
            if (!textures.find()) throw new IllegalArgumentException("skin profile contains no signed textures property");
            return new SkinProperty(textures.group(1), textures.group(2));
        } catch (IOException exception) {
            throw new IllegalStateException("profile service request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("profile service request was interrupted", exception);
        }
    }

    private HttpResponse<String> get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(8)).header("User-Agent", "VnMineHardcore/" + plugin.getDescription().getVersion())
            .GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String profileKey(String owner) {
        if (owner == null || owner.isBlank()) return "default";
        String key = owner.toLowerCase(java.util.Locale.ROOT);
        SkinProperty skin = skinsByOwner.get(key);
        if (skin != null) return key + ":" + skin.value().hashCode();
        return key + (skinFailures.containsKey(key) ? ":failed" : ":loading");
    }

    private void spawn(Player viewer, NpcVisual npc, String profileKey) {
        UUID profileId = UUID.nameUUIDFromBytes((plugin.getName() + ":npc:" + npc.id()).getBytes(StandardCharsets.UTF_8));
        String profileName = "vn" + Integer.toUnsignedString(npc.id().hashCode(), 36);
        if (profileName.length() > 16) profileName = profileName.substring(0, 16);
        WrappedGameProfile profile = new WrappedGameProfile(profileId, profileName);
        SkinProperty skin = npc.skinOwner() == null ? null : skinsByOwner.get(npc.skinOwner().toLowerCase(java.util.Locale.ROOT));
        if (skin != null) profile.getProperties().put("textures",
            new WrappedSignedProperty("textures", skin.value(), skin.signature()));

        int entityId = nextEntityId.getAndDecrement();
        if (entityId == Integer.MIN_VALUE) nextEntityId.compareAndSet(Integer.MIN_VALUE, -1);
        PacketContainer info = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO);
        info.getPlayerInfoActions().write(0, EnumSet.of(EnumWrappers.PlayerInfoAction.ADD_PLAYER));
        info.getPlayerInfoDataLists().write(0, List.of(new PlayerInfoData(profileId, 0, false,
            EnumWrappers.NativeGameMode.SURVIVAL, profile, WrappedChatComponent.fromLegacyText(npc.displayName()),
            false, 0, null)));
        if (!send(viewer, info)) return;

        Location location = npc.location();
        PacketContainer spawn = protocolManager.createPacket(PacketType.Play.Server.NAMED_ENTITY_SPAWN);
        spawn.getIntegers().write(0, entityId);
        spawn.getUUIDs().write(0, profileId);
        spawn.getDoubles().write(0, location.getX()).write(1, location.getY()).write(2, location.getZ());
        spawn.getBytes().write(0, angle(location.getYaw())).write(1, angle(0.0f));
        if (!send(viewer, spawn)) {
            removePlayerInfo(viewer, profileId);
            return;
        }
        PacketContainer metadata = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
        metadata.getIntegers().write(0, entityId);
        WrappedChatComponent customName = WrappedChatComponent.fromLegacyText(npc.displayName());
        metadata.getDataValueCollectionModifier().write(0, List.of(
            new WrappedDataValue(2, WrappedDataWatcher.Registry.getChatComponentSerializer(true),
                java.util.Optional.of(customName.getHandle())),
            new WrappedDataValue(3, WrappedDataWatcher.Registry.get(Boolean.class), true),
            new WrappedDataValue(17, WrappedDataWatcher.Registry.get(Byte.class), (byte) 0x7f)));
        if (!send(viewer, metadata)) {
            destroy(viewer, entityId);
            removePlayerInfo(viewer, profileId);
            return;
        }
        FakeNpc fake = new FakeNpc(npc.id(), entityId, profileId, profileKey, location.clone(), location.getYaw());
        visibleByViewer.computeIfAbsent(viewer.getUniqueId(), ignored -> new ConcurrentHashMap<>()).put(npc.id(), fake);
        interactableByViewer.computeIfAbsent(viewer.getUniqueId(), ignored -> new ConcurrentHashMap<>()).put(entityId, npc.id());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (viewer.isOnline()) removePlayerInfo(viewer, profileId);
        }, 40L);
    }

    private void rotate(Player viewer, FakeNpc fake, float yaw) {
        float delta = Math.abs(((fake.yaw() - yaw + 540.0f) % 360.0f) - 180.0f);
        if (delta < 2.0f) return;
        PacketContainer look = protocolManager.createPacket(PacketType.Play.Server.ENTITY_LOOK);
        look.getIntegers().write(0, fake.entityId());
        look.getBytes().write(0, angle(yaw)).write(1, angle(0.0f));
        look.getBooleans().write(0, true);
        send(viewer, look);
        PacketContainer head = protocolManager.createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
        head.getIntegers().write(0, fake.entityId());
        head.getBytes().write(0, angle(yaw));
        send(viewer, head);
        Map<String, FakeNpc> visible = visibleByViewer.get(viewer.getUniqueId());
        if (visible != null) visible.replace(fake.npcId(), fake, fake.withYaw(yaw));
    }

    private void remove(Player viewer, FakeNpc fake) {
        destroy(viewer, fake.entityId());
        removePlayerInfo(viewer, fake.profileId());
        Map<String, FakeNpc> visible = visibleByViewer.get(viewer.getUniqueId());
        if (visible != null) visible.remove(fake.npcId(), fake);
        Map<Integer, String> ids = interactableByViewer.get(viewer.getUniqueId());
        if (ids != null) ids.remove(fake.entityId());
    }

    private void clearViewer(Player viewer) {
        Map<String, FakeNpc> entries = visibleByViewer.remove(viewer.getUniqueId());
        if (entries != null) for (FakeNpc fake : List.copyOf(entries.values())) {
            destroy(viewer, fake.entityId());
            removePlayerInfo(viewer, fake.profileId());
        }
        interactableByViewer.remove(viewer.getUniqueId());
    }

    private void destroy(Player viewer, int entityId) {
        PacketContainer destroy = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
        destroy.getIntLists().write(0, List.of(entityId));
        send(viewer, destroy);
    }

    private void removePlayerInfo(Player viewer, UUID profileId) {
        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);
        packet.getUUIDLists().write(0, List.of(profileId));
        send(viewer, packet);
    }

    private boolean send(Player viewer, PacketContainer packet) {
        try {
            protocolManager.sendServerPacket(viewer, packet);
            return true;
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("[NPC][PlayerModel] Packet send failed for viewer=" + viewer.getName()
                + ", type=" + packet.getType());
            return false;
        }
    }

    private static byte angle(float degrees) { return (byte) Math.floor(degrees * 256.0 / 360.0); }

    private static String rootMessage(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    record NpcVisual(String id, String displayName, String skinOwner, Location location) {
        NpcVisual { location = location.clone(); }
        @Override public Location location() { return location.clone(); }
    }

    private record SkinProperty(String value, String signature) { }

    private record FakeNpc(String npcId, int entityId, UUID profileId, String profileKey,
                           Location location, float yaw) {
        private FakeNpc withYaw(float nextYaw) { return new FakeNpc(npcId, entityId, profileId, profileKey, location, nextYaw); }
    }
}
