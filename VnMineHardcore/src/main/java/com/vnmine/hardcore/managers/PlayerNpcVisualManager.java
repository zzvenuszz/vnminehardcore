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
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Packet-backed player-model overlays for managed NPCs, while Bukkit villagers remain their server hitboxes. */
final class PlayerNpcVisualManager implements Listener {
    private static final long VIEWER_LOGIN_SETTLE_TICKS = 40L;
    private static final char[] RAINBOW_COLORS = {'c', '6', 'e', 'a', 'b', '9', 'd'};
    private static final EnumSet<EnumWrappers.PlayerInfoAction> PLAYER_INFO_ADD_ACTIONS = EnumSet.of(
        EnumWrappers.PlayerInfoAction.ADD_PLAYER,
        EnumWrappers.PlayerInfoAction.UPDATE_LISTED,
        EnumWrappers.PlayerInfoAction.UPDATE_LATENCY,
        EnumWrappers.PlayerInfoAction.UPDATE_GAME_MODE,
        EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME);
    private static final Pattern UUID_PATTERN = Pattern.compile("\\\"id\\\"\\s*:\\s*\\\"([0-9a-fA-F]{32})\\\"");
    private static final Pattern TEXTURES_PATTERN = Pattern.compile(
        "\\\"name\\\"\\s*:\\s*\\\"textures\\\"\\s*,\\s*\\\"value\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"\\s*,\\s*\\\"signature\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final WrappedDataWatcher.Serializer CHAT_COMPONENT_SERIALIZER =
        WrappedDataWatcher.Registry.getChatComponentSerializer(false);
    private static final WrappedDataWatcher.Serializer INTEGER_SERIALIZER =
        WrappedDataWatcher.Registry.get(Integer.class);
    private static final WrappedDataWatcher.Serializer BYTE_SERIALIZER =
        WrappedDataWatcher.Registry.get(Byte.class);
    private static final AtomicInteger NEXT_NAMEPLATE_ENTITY_ID = new AtomicInteger(-1_000_000);

    private final VnMineHardcore plugin;
    private final NpcManager npcManager;
    private final ProtocolManager protocolManager;
    private final ExecutorService skinExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "VnMine-NpcSkin");
        thread.setDaemon(true);
        return thread;
    });
    // Keep HttpClient's transport executor separate from skinExecutor: lookupSkin blocks
    // in HttpClient.send(), so sharing a small fixed pool can starve concurrent requests.
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5)).build();
    private final Map<UUID, Map<String, FakeNpc>> visibleByViewer = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Integer, String>> interactableByViewer = new ConcurrentHashMap<>();
    private final Map<UUID, Long> viewerReadyAtTick = new ConcurrentHashMap<>();
    private final Map<String, SkinProperty> skinsByOwner = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<SkinProperty>> pendingSkins = new ConcurrentHashMap<>();
    private final Map<String, String> skinFailures = new ConcurrentHashMap<>();
    private final Map<String, Long> skinRetryAt = new ConcurrentHashMap<>();
    private final AtomicInteger nextEntityId = new AtomicInteger(-1);
    private final PacketAdapter interactionListener;
    private final BukkitTask updateTask;
    private final BukkitTask animationTask;
    private boolean playerModelAvailable = true;

    PlayerNpcVisualManager(VnMineHardcore plugin, NpcManager npcManager) {
        this.plugin = plugin;
        this.npcManager = npcManager;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        Bukkit.getPluginManager().registerEvents(this, plugin);
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
        this.animationTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAnimatedNameplates, 4L, 4L);
    }

    void shutdown() {
        updateTask.cancel();
        animationTask.cancel();
        protocolManager.removePacketListener(interactionListener);
        for (Player viewer : Bukkit.getOnlinePlayers()) clearViewer(viewer);
        skinExecutor.shutdownNow();
    }

    @EventHandler
    public void onViewerJoin(PlayerJoinEvent event) {
        viewerReadyAtTick.put(event.getPlayer().getUniqueId(),
            (long) Bukkit.getCurrentTick() + VIEWER_LOGIN_SETTLE_TICKS);
    }

    @EventHandler
    public void onViewerQuit(PlayerQuitEvent event) {
        UUID viewerId = event.getPlayer().getUniqueId();
        viewerReadyAtTick.remove(viewerId);
        visibleByViewer.remove(viewerId);
        interactableByViewer.remove(viewerId);
    }

    private void updateVisibleNpcs() {
        boolean enabled = playerModelAvailable
            && plugin.getConfig().getBoolean("rpg.npc.player-model.enabled", true);
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
            long readyAt = viewerReadyAtTick.getOrDefault(viewer.getUniqueId(), Long.MIN_VALUE);
            if (Bukkit.getCurrentTick() < readyAt) continue;
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
                        || !Objects.equals(old.displayName(), npc.displayName())
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
        viewerReadyAtTick.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
    }

    private void updateAnimatedNameplates() {
        if (!playerModelAvailable || !plugin.getConfig().getBoolean("rpg.npc.player-model.enabled", true)) return;
        long now = Bukkit.getCurrentTick();
        for (Map.Entry<UUID, Map<String, FakeNpc>> viewerEntry : visibleByViewer.entrySet()) {
            Player viewer = Bukkit.getPlayer(viewerEntry.getKey());
            if (viewer == null || !viewer.isOnline()) continue;
            for (FakeNpc fake : viewerEntry.getValue().values()) {
                if (fake.nameplateEntityId() == 0 || !containsRainbow(fake.displayName())) continue;
                long age = Math.max(0L, now - fake.animationStartedAtTick());
                sendNameplateMetadata(viewer, fake.nameplateEntityId(), renderNpcName(fake.displayName(), age));
            }
        }
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
        Long retryAt = skinRetryAt.get(key);
        if (retryAt != null && System.currentTimeMillis() < retryAt) return;
        if (retryAt != null) {
            skinRetryAt.remove(key, retryAt);
            skinFailures.remove(key);
        }
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
                        skinRetryAt.put(key, System.currentTimeMillis() + Duration.ofSeconds(30).toMillis());
                        plugin.getLogger().warning("[NPC][Skin] Could not load Minecraft account skin '" + owner + "': " + reason);
                    } else {
                        skinsByOwner.put(key, skin);
                        skinFailures.remove(key);
                        skinRetryAt.remove(key);
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
            .timeout(Duration.ofSeconds(15)).header("User-Agent", "VnMineHardcore/" + plugin.getDescription().getVersion())
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
        if (!playerModelAvailable) return;
        try {
            spawnPlayerEntity(viewer, npc, profileKey);
        } catch (RuntimeException exception) {
            disablePlayerModels(exception);
        }
    }

    private void spawnPlayerEntity(Player viewer, NpcVisual npc, String profileKey) {
        UUID profileId = UUID.nameUUIDFromBytes((plugin.getName() + ":npc:" + npc.id()).getBytes(StandardCharsets.UTF_8));
        // An empty profile name suppresses the client's plain white player nametag;
        // the colored TextDisplay below renders the configured NPC name instead.
        String profileName = "";
        WrappedGameProfile profile = new WrappedGameProfile(profileId, profileName);
        SkinProperty skin = npc.skinOwner() == null ? null : skinsByOwner.get(npc.skinOwner().toLowerCase(java.util.Locale.ROOT));
        if (skin != null) profile.getProperties().put("textures",
            new WrappedSignedProperty("textures", skin.value(), skin.signature()));

        int entityId = nextEntityId.getAndDecrement();
        if (entityId == Integer.MIN_VALUE) nextEntityId.compareAndSet(Integer.MIN_VALUE, -1);
        PacketContainer info = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO);
        info.getPlayerInfoActions().write(0, PLAYER_INFO_ADD_ACTIONS);
        info.getPlayerInfoDataLists().write(0, List.of(new PlayerInfoData(profileId, 0, false,
            EnumWrappers.NativeGameMode.SURVIVAL, profile, null)));
        if (!send(viewer, info)) return;

        Location location = npc.location();
        // Since the configuration-phase protocol, players use SPAWN_ENTITY with PLAYER type.
        // NAMED_ENTITY_SPAWN is no longer mapped by ProtocolLib on Paper 26.3.
        PacketContainer spawn = protocolManager.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
        spawn.getIntegers().write(0, entityId);
        spawn.getUUIDs().write(0, profileId);
        spawn.getEntityTypeModifier().write(0, EntityType.PLAYER);
        spawn.getDoubles().write(0, location.getX()).write(1, location.getY()).write(2, location.getZ());
        spawn.getBytes().write(0, angle(location.getPitch())).write(1, angle(location.getYaw()))
            .write(2, angle(location.getYaw()));
        spawn.getVectors().write(0, new Vector());
        if (!send(viewer, spawn)) {
            removePlayerInfo(viewer, profileId);
            return;
        }
        // Do not send ENTITY_METADATA for the fake Player: some 26.x clients
        // disconnect while decoding that packet. A separate TextDisplay uses the
        // metadata schema already used by the NPC quest markers and preserves colors.
        long animationStartedAtTick = Bukkit.getCurrentTick();
        int nameplateId = spawnNameplate(viewer, npc, location);
        FakeNpc fake = new FakeNpc(npc.id(), npc.displayName(), entityId, nameplateId, profileId,
            profileKey, location.clone(), location.getYaw(), animationStartedAtTick);
        visibleByViewer.computeIfAbsent(viewer.getUniqueId(), ignored -> new ConcurrentHashMap<>()).put(npc.id(), fake);
        interactableByViewer.computeIfAbsent(viewer.getUniqueId(), ignored -> new ConcurrentHashMap<>()).put(entityId, npc.id());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (viewer.isOnline()) removePlayerInfo(viewer, profileId);
        }, 40L);
    }

    private static String renderNpcName(String source, long ageTicks) {
        String text = source == null ? "NPC" : source;
        StringBuilder rendered = new StringBuilder(text.length() * 3);
        FormatState format = new FormatState();
        RainbowMode mode = null;
        int segmentIndex = 0;
        int segmentLength = 0;
        for (int offset = 0; offset < text.length();) {
            RainbowToken token = rainbowTokenAt(text, offset);
            if (token != null) {
                mode = token.mode();
                offset += token.length();
                segmentIndex = 0;
                segmentLength = countSegmentCharacters(text, offset);
                continue;
            }
            if (offset + 1 < text.length() && isLegacyMarker(text.charAt(offset))) {
                ChatColor code = ChatColor.getByChar(Character.toLowerCase(text.charAt(offset + 1)));
                if (code != null) {
                    rendered.append(ChatColor.COLOR_CHAR).append(code.getChar());
                    format.apply(code);
                    offset += 2;
                    continue;
                }
            }
            int codePoint = text.codePointAt(offset);
            if (mode == null) {
                rendered.appendCodePoint(codePoint);
            } else {
                rendered.append(ChatColor.COLOR_CHAR).append(rainbowColor(mode, segmentIndex,
                    segmentLength, ageTicks));
                format.appendDecorations(rendered);
                rendered.appendCodePoint(codePoint);
                segmentIndex++;
            }
            offset += Character.charCount(codePoint);
        }
        return rendered.toString();
    }

    private static int countSegmentCharacters(String text, int start) {
        int count = 0;
        for (int offset = start; offset < text.length();) {
            if (rainbowTokenAt(text, offset) != null) break;
            if (offset + 1 < text.length() && isLegacyMarker(text.charAt(offset))
                    && ChatColor.getByChar(Character.toLowerCase(text.charAt(offset + 1))) != null) {
                offset += 2;
                continue;
            }
            int codePoint = text.codePointAt(offset);
            count++;
            offset += Character.charCount(codePoint);
        }
        return count;
    }

    private static RainbowToken rainbowTokenAt(String text, int offset) {
        if (offset + 2 >= text.length() || !isLegacyMarker(text.charAt(offset))
                || Character.toLowerCase(text.charAt(offset + 1)) != 'r') return null;
        char effect = Character.toLowerCase(text.charAt(offset + 2));
        RainbowMode mode = switch (effect) {
            case 'l' -> RainbowMode.LINE;
            case '1' -> RainbowMode.LEFT_TO_RIGHT;
            case '2' -> RainbowMode.RIGHT_TO_LEFT;
            case '3' -> RainbowMode.CENTER_OUT;
            default -> null;
        };
        if (mode == null) return null;
        if (effect == 'l' && (offset + 3 >= text.length() || text.charAt(offset + 3) != ':')) return null;
        boolean hasColon = offset + 3 < text.length() && text.charAt(offset + 3) == ':';
        return new RainbowToken(mode, hasColon ? 4 : 3);
    }

    private static boolean containsRainbow(String text) {
        if (text == null || text.isEmpty()) return false;
        for (int offset = 0; offset < text.length();) {
            RainbowToken token = rainbowTokenAt(text, offset);
            if (token != null) return true;
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    private static boolean isLegacyMarker(char value) {
        return value == '&' || value == ChatColor.COLOR_CHAR;
    }

    private static char rainbowColor(RainbowMode mode, int index, int length, long ageTicks) {
        long frame = ageTicks / 4L;
        if (mode == RainbowMode.LINE) return RAINBOW_COLORS[(int) ((frame / 2L) % RAINBOW_COLORS.length)];
        int delay = switch (mode) {
            case LEFT_TO_RIGHT -> index;
            case RIGHT_TO_LEFT -> Math.max(0, length - index - 1);
            case CENTER_OUT -> Math.min(Math.abs(index - (length - 1) / 2), Math.abs(index - length / 2));
            case LINE -> 0;
        };
        if (frame < delay) return RAINBOW_COLORS[0];
        return RAINBOW_COLORS[(int) (((frame - delay) / 2L) % RAINBOW_COLORS.length)];
    }

    private enum RainbowMode { LINE, LEFT_TO_RIGHT, RIGHT_TO_LEFT, CENTER_OUT }
    private record RainbowToken(RainbowMode mode, int length) { }

    private static final class FormatState {
        private boolean obfuscated;
        private boolean bold;
        private boolean strikethrough;
        private boolean underlined;
        private boolean italic;

        private void apply(ChatColor code) {
            if (code.isColor() || code == ChatColor.RESET) {
                obfuscated = bold = strikethrough = underlined = italic = false;
                return;
            }
            switch (code) {
                case MAGIC -> obfuscated = true;
                case BOLD -> bold = true;
                case STRIKETHROUGH -> strikethrough = true;
                case UNDERLINE -> underlined = true;
                case ITALIC -> italic = true;
                default -> { }
            }
        }

        private void appendDecorations(StringBuilder output) {
            if (obfuscated) output.append(ChatColor.MAGIC);
            if (bold) output.append(ChatColor.BOLD);
            if (strikethrough) output.append(ChatColor.STRIKETHROUGH);
            if (underlined) output.append(ChatColor.UNDERLINE);
            if (italic) output.append(ChatColor.ITALIC);
        }
    }

    private int spawnNameplate(Player viewer, NpcVisual npc, Location location) {
        int nameplateId = NEXT_NAMEPLATE_ENTITY_ID.getAndDecrement();
        UUID nameplateUuid = UUID.randomUUID();
        PacketContainer spawn = protocolManager.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
        spawn.getIntegers().write(0, nameplateId).write(1, 0);
        spawn.getUUIDs().write(0, nameplateUuid);
        spawn.getEntityTypeModifier().write(0, EntityType.TEXT_DISPLAY);
        spawn.getDoubles().write(0, location.getX()).write(1, location.getY() + 1.95)
            .write(2, location.getZ());
        spawn.getBytes().write(0, (byte) 0).write(1, (byte) 0).write(2, (byte) 0);
        spawn.getVectors().write(0, new Vector());
        if (!send(viewer, spawn)) return 0;

        String legacyName = renderNpcName(npc.displayName(), 0L);
        if (sendNameplateMetadata(viewer, nameplateId, legacyName)) return nameplateId;
        destroy(viewer, nameplateId);
        return 0;
    }

    private boolean sendNameplateMetadata(Player viewer, int nameplateId, String legacyName) {
        PacketContainer metadata = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
        metadata.getIntegers().write(0, nameplateId);
        metadata.getDataValueCollectionModifier().write(0, List.of(
            new WrappedDataValue(15, BYTE_SERIALIZER, (byte) 3),
            new WrappedDataValue(23, CHAT_COMPONENT_SERIALIZER,
                WrappedChatComponent.fromLegacyText(legacyName).getHandle()),
            new WrappedDataValue(25, INTEGER_SERIALIZER, 0),
            new WrappedDataValue(26, BYTE_SERIALIZER, (byte) -1),
            new WrappedDataValue(27, BYTE_SERIALIZER, (byte) 0)));
        return send(viewer, metadata);
    }

    private void disablePlayerModels(RuntimeException failure) {
        if (!playerModelAvailable) return;
        playerModelAvailable = false;
        plugin.getLogger().log(java.util.logging.Level.SEVERE,
            "[NPC][PlayerModel] Packet spawn failed; disabling packet models for this server session and restoring villager NPCs.",
            failure);
        for (Player viewer : Bukkit.getOnlinePlayers()) clearViewer(viewer);
        for (NpcVisual npc : npcManager.getPlayerVisuals()) {
            UUID bukkitId = npcManager.entityIdFor(npc.id());
            Entity entity = bukkitId == null ? null : Bukkit.getEntity(bukkitId);
            if (entity instanceof org.bukkit.entity.Villager villager && villager.isValid()) {
                villager.setInvisible(false);
            }
        }
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
        destroy(viewer, fake);
        Map<String, FakeNpc> visible = visibleByViewer.get(viewer.getUniqueId());
        if (visible != null) visible.remove(fake.npcId(), fake);
        Map<Integer, String> ids = interactableByViewer.get(viewer.getUniqueId());
        if (ids != null) ids.remove(fake.entityId());
    }

    private void clearViewer(Player viewer) {
        Map<String, FakeNpc> entries = visibleByViewer.remove(viewer.getUniqueId());
        if (entries != null) for (FakeNpc fake : List.copyOf(entries.values())) {
            destroy(viewer, fake);
        }
        interactableByViewer.remove(viewer.getUniqueId());
    }

    private void destroy(Player viewer, int entityId) {
        PacketContainer destroy = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
        destroy.getIntLists().write(0, List.of(entityId));
        send(viewer, destroy);
    }

    private void destroy(Player viewer, FakeNpc fake) {
        destroy(viewer, fake.entityId());
        if (fake.nameplateEntityId() != 0) destroy(viewer, fake.nameplateEntityId());
        removePlayerInfo(viewer, fake.profileId());
    }

    private void removePlayerInfo(Player viewer, UUID profileId) {
        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);
        packet.getUUIDLists().write(0, List.of(profileId));
        send(viewer, packet);
    }

    private boolean send(Player viewer, PacketContainer packet) {
        try {
            // Virtual NPC packets must bypass other plugins' listeners for real entities.
            protocolManager.sendServerPacket(viewer, packet, false);
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

    private record FakeNpc(String npcId, String displayName, int entityId, int nameplateEntityId,
                           UUID profileId, String profileKey, Location location, float yaw,
                           long animationStartedAtTick) {
        private FakeNpc withYaw(float nextYaw) {
            return new FakeNpc(npcId, displayName, entityId, nameplateEntityId, profileId, profileKey,
                location, nextYaw, animationStartedAtTick);
        }
    }
}
