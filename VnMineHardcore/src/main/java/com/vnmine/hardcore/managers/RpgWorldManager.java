package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class RpgWorldManager implements Listener {
    private static final String HUMAN_REALM_ID = "human-realm";
    private final VnMineHardcore plugin;
    private final File definitionsFolder;
    private final File worldManifestFile;
    private final org.bukkit.configuration.file.YamlConfiguration worldManifest =
        new org.bukkit.configuration.file.YamlConfiguration();
    private final Map<String, String> requestedWorldSettings = new LinkedHashMap<>();
    private Map<String, HumanRealmDefinitionLoader.HumanRealmDefinition> definitions = Map.of();
    private boolean createWorldsOnNextReload = true;

    public RpgWorldManager(VnMineHardcore plugin) {
        this.plugin = plugin;
        this.worldManifestFile = new File(plugin.getDataFolder(), "managed-worlds.yml");
        if (worldManifestFile.isFile()) {
            try { worldManifest.load(worldManifestFile); }
            catch (org.bukkit.configuration.InvalidConfigurationException | IOException exception) {
                plugin.getLogger().log(Level.SEVERE,
                    "[World] Managed-world manifest is invalid; existing worlds will be checked against their live metadata.",
                    exception);
            }
        }
        File worldsFolder = new File(plugin.getDataFolder(), "worlds");
        definitionsFolder = worldsFolder;
        if (!worldsFolder.isDirectory() && !worldsFolder.mkdirs()) {
            plugin.getLogger().severe("[World] Could not create the worlds configuration directory.");
            return;
        }

        File humanRealmFile = new File(worldsFolder, "human-realm.yml");
        if (!humanRealmFile.isFile()) {
            try {
                plugin.saveResource("worlds/human-realm.yml", false);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().log(Level.SEVERE, "[World] Bundled human-realm.yml resource is missing.", exception);
                return;
            }
        }
        File yeuRealmFile = new File(worldsFolder, "yeu-realm.yml");
        if (!yeuRealmFile.isFile()) {
            try {
                plugin.saveResource("worlds/yeu-realm.yml", false);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().log(Level.SEVERE, "[World] Bundled yeu-realm.yml resource is missing.", exception);
            }
        }
        String startSeedPath = "rpg.onboarding.start-world.seed";
        Long startSeed = readOptionalSeed(plugin.getConfig(), startSeedPath, "onboarding start world");
        boolean invalidStartSeed = plugin.getConfig().contains(startSeedPath)
            && plugin.getConfig().get(startSeedPath) != null && startSeed == null;
        if (!invalidStartSeed) {
            createConfiguredWorld(plugin.getConfig().getString("rpg.onboarding.start-world.name", "world_start"),
                plugin.getConfig().getString("rpg.onboarding.start-world.environment", "NORMAL"), startSeed,
                plugin.getConfig().getBoolean("rpg.onboarding.start-world.create-if-missing", true), "onboarding start world");
        }
        reload();
    }

    public boolean reload() {
        File[] definitionFiles = definitionsFolder.listFiles((directory, name) ->
            name.toLowerCase(java.util.Locale.ROOT).endsWith(".yml"));
        if (definitionFiles == null) {
            plugin.getLogger().severe("[World] Could not list the worlds configuration directory.");
            return false;
        }
        Arrays.sort(definitionFiles, Comparator.comparing(File::getName));
        Map<String, HumanRealmDefinitionLoader.HumanRealmDefinition> loaded = new LinkedHashMap<>();
        for (File file : definitionFiles) {
            if (createWorldsOnNextReload) createRealmWorldIfConfigured(file);
            Optional<HumanRealmDefinitionLoader.HumanRealmDefinition> definition =
                HumanRealmDefinitionLoader.load(file, plugin.getLogger());
            if (definition.isEmpty()) continue;
            String realmId = definition.get().id();
            if (loaded.putIfAbsent(realmId, definition.get()) != null) {
                plugin.getLogger().warning("[World] Duplicate realm ID '" + realmId + "' in " + file.getName() +
                    "; the first definition remains active.");
                continue;
            }
            logWorldAvailability(definition.get());
        }
        if (loaded.isEmpty()) {
            plugin.getLogger().severe("[World] No valid realm definitions were loaded.");
            return false;
        }

        definitions = java.util.Collections.unmodifiableMap(loaded);
        createWorldsOnNextReload = false;
        if (!definitions.containsKey(HUMAN_REALM_ID)) {
            plugin.getLogger().warning("[World] The default human-realm definition is missing.");
        }
        return true;
    }

    private void createRealmWorldIfConfigured(File file) {
        org.bukkit.configuration.file.YamlConfiguration yaml =
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        if (!yaml.getBoolean("world.create-if-missing", false)) return;
        Long seed = readOptionalSeed(yaml, "world.seed", "realm " + yaml.getString("id", file.getName()));
        if (yaml.contains("world.seed") && seed == null) return;
        createConfiguredWorld(yaml.getString("world-name"), yaml.getString("world.environment", "NORMAL"), seed, true,
            "realm " + yaml.getString("id", file.getName()));
    }

    private Long readOptionalSeed(org.bukkit.configuration.ConfigurationSection configuration,
            String path, String reason) {
        if (!configuration.contains(path) || configuration.get(path) == null) return null;
        Object raw = configuration.get(path);
        if (raw instanceof Byte || raw instanceof Short || raw instanceof Integer || raw instanceof Long)
            return ((Number) raw).longValue();
        if (raw instanceof java.math.BigInteger integer) {
            try { return integer.longValueExact(); }
            catch (ArithmeticException ignored) { /* log below */ }
        }
        plugin.getLogger().severe("[World] Invalid seed at " + path + " for " + reason
            + "; expected an integer from -9223372036854775808 to 9223372036854775807. World was not changed.");
        return null;
    }

    private void createConfiguredWorld(String name, String environmentName, Long seed,
            boolean enabled, String reason) {
        if (!enabled || name == null || !name.matches("[A-Za-z0-9_-]{1,64}")) {
            if (enabled) plugin.getLogger().severe("[World] Invalid world name for " + reason + "; world was not changed.");
            return;
        }
        World.Environment environment;
        try {
            environment = World.Environment.valueOf(environmentName == null ? "NORMAL"
                : environmentName.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().severe("[World] Invalid environment '" + environmentName + "' for " + reason
                + "; world was not changed.");
            return;
        }
        String requestedSettings = environment.name() + "/" + (seed == null ? "generated-or-existing" : seed);
        String previousRequest = requestedWorldSettings.putIfAbsent(name, requestedSettings);
        if (previousRequest != null && !previousRequest.equals(requestedSettings)) {
            plugin.getLogger().severe("[World] Conflicting startup settings for shared world '" + name + "': "
                + previousRequest + " vs '" + requestedSettings + "' (" + reason
                + "); refusing to replace the world.");
            return;
        }
        String manifestPath = "worlds." + name + ".environment";
        String previousEnvironment = worldManifest.getString(manifestPath);
        Long previousSeed = worldManifest.contains("worlds." + name + ".seed")
            ? worldManifest.getLong("worlds." + name + ".seed") : null;
        boolean manifestEnvironmentChanged = previousEnvironment != null
            && !previousEnvironment.equalsIgnoreCase(environment.name());
        boolean manifestHasSettings = worldManifest.contains(manifestPath);
        boolean previousSeedConfigured = worldManifest.getBoolean("worlds." + name + ".seed-configured",
            previousSeed != null);
        boolean manifestSeedChanged = manifestHasSettings
            && (previousSeedConfigured != (seed != null)
                || (seed != null && !java.util.Objects.equals(seed, previousSeed)));
        World world = Bukkit.getWorld(name);
        try {
            if (manifestEnvironmentChanged || manifestSeedChanged) {
                plugin.getLogger().warning("[World] Stored world settings changed for '" + name + "': environment "
                    + previousEnvironment + " -> " + environment + ", seed " + previousSeed + " -> " + seed + ".");
                world = replaceWorld(world, name, environment, seed, reason);
                if (world == null) return;
                saveWorldManifest(name, environment, seed, world);
                return;
            }
            if (world == null) world = Bukkit.createWorld(worldCreator(name, environment, seed));
            if (world == null) {
                plugin.getLogger().severe("[World] Paper failed to load/create " + reason + " '" + name + "'.");
                return;
            }
            boolean environmentChanged = world.getEnvironment() != environment;
            boolean seedChanged = seed != null && world.getSeed() != seed;
            if (!environmentChanged && !seedChanged) {
                plugin.getLogger().info("[World] Ready " + reason + " '" + name + "' (" + environment
                    + ", seed=" + (seed == null ? "existing/default" : seed) + ").");
                saveWorldManifest(name, environment, seed, world);
                return;
            }
            plugin.getLogger().warning("[World] Configuration changed for '" + name + "': environment "
                + world.getEnvironment() + " -> " + environment + ", seed " + world.getSeed() + " -> "
                + (seed == null ? "unchanged" : seed) + ". Recreating world and preserving the old folder as backup.");
            World replacement = replaceWorld(world, name, environment, seed, reason);
            if (replacement != null) saveWorldManifest(name, environment, seed, replacement);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "[World] Failed to load/create " + reason + " '" + name + "'.", exception);
        }
    }

    private WorldCreator worldCreator(String name, World.Environment environment, Long seed) {
        WorldCreator creator = new WorldCreator(name).environment(environment);
        if (seed != null) creator.seed(seed);
        return creator;
    }

    private World replaceWorld(World oldWorld, String name, World.Environment environment, Long seed, String reason) {
        Path container = plugin.getServer().getWorldContainer().toPath().toAbsolutePath().normalize();
        Path worldFolder = resolveManagedWorldFolder(name, oldWorld, container);
        if (worldFolder == null || !worldFolder.startsWith(container)) {
            plugin.getLogger().severe("[World] Could not safely resolve the managed folder for '" + name
                + "'; refusing to replace the world.");
            return null;
        }
        if (oldWorld != null && !Bukkit.unloadWorld(oldWorld, false)) {
            plugin.getLogger().severe("[World] Could not unload '" + name + "'; refusing to replace it.");
            return null;
        }
        Path backup = null;
        try {
            if (Files.exists(worldFolder)) {
                String stamp = LocalDateTime.now(java.time.ZoneOffset.UTC)
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                backup = worldFolder.resolveSibling(name + ".backup-" + stamp);
                int suffix = 1;
                while (Files.exists(backup)) backup = worldFolder.resolveSibling(name + ".backup-" + stamp + "-" + suffix++);
                moveWorldFolder(worldFolder, backup);
                plugin.getLogger().warning("[World] Preserved previous world '" + name + "' at " + backup + ".");
            }
            World replacement = Bukkit.createWorld(worldCreator(name, environment, seed));
            if (replacement == null || replacement.getEnvironment() != environment
                    || (seed != null && replacement.getSeed() != seed)) {
                if (replacement != null) Bukkit.unloadWorld(replacement, false);
                Path failedFolder = worldFolder.resolveSibling(name + ".failed-" + System.currentTimeMillis());
                if (Files.exists(worldFolder)) moveWorldFolder(worldFolder, failedFolder);
                if (backup != null && Files.exists(backup) && !Files.exists(worldFolder)) moveWorldFolder(backup, worldFolder);
                plugin.getLogger().severe("[World] New world did not match the requested environment/seed for "
                    + reason + "; previous world folder was restored where possible.");
                return null;
            }
            plugin.getLogger().info("[World] Recreated " + reason + " '" + name + "' (" + environment
                + ", seed=" + (seed == null ? "generated" : seed) + ").");
            return replacement;
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "[World] Could not replace world '" + name + "'.", exception);
            try {
                if (backup != null && Files.exists(backup) && !Files.exists(worldFolder))
                    moveWorldFolder(backup, worldFolder);
            } catch (IOException restoreFailure) {
                plugin.getLogger().log(Level.SEVERE, "[World] Failed restoring the previous world folder '"
                    + name + "' from " + backup + ".", restoreFailure);
            }
            return null;
        }
    }

    private Path resolveManagedWorldFolder(String name, World loadedWorld, Path container) {
        if (loadedWorld != null) {
            Path folder = loadedWorld.getWorldFolder().toPath().toAbsolutePath().normalize();
            if (folder.startsWith(container)) return folder;
            plugin.getLogger().severe("[World] Loaded world '" + name + "' resolves outside the server world container: "
                + folder);
            return null;
        }

        String recorded = worldManifest.getString("worlds." + name + ".folder");
        if (recorded != null && !recorded.isBlank()) {
            Path folder = container.resolve(recorded).normalize();
            if (folder.startsWith(container) && Files.exists(folder)) return folder;
            plugin.getLogger().warning("[World] Ignoring stale/unsafe managed folder path for '" + name + "': "
                + recorded);
        }

        java.util.LinkedHashSet<Path> existingCandidates = new java.util.LinkedHashSet<>();
        Path legacyFolder = container.resolve(name).normalize();
        if (Files.exists(legacyFolder)) existingCandidates.add(legacyFolder);
        for (World rootWorld : Bukkit.getWorlds()) {
            Path nestedFolder = rootWorld.getWorldFolder().toPath().toAbsolutePath().normalize()
                .resolve("dimensions").resolve("minecraft").resolve(name).normalize();
            if (nestedFolder.startsWith(container) && Files.exists(nestedFolder)) existingCandidates.add(nestedFolder);
        }
        if (existingCandidates.size() == 1) return existingCandidates.iterator().next();
        if (existingCandidates.size() > 1) {
            plugin.getLogger().severe("[World] Multiple existing folders match managed world '" + name + "': "
                + existingCandidates + "; refusing an ambiguous replacement.");
            return null;
        }

        // Modern Paper stores secondary dimensions under the primary world's dimension directory.
        for (World rootWorld : Bukkit.getWorlds()) {
            if (rootWorld.getEnvironment() != World.Environment.NORMAL) continue;
            Path modernFolder = rootWorld.getWorldFolder().toPath().toAbsolutePath().normalize()
                .resolve("dimensions").resolve("minecraft").resolve(name).normalize();
            if (modernFolder.startsWith(container)) return modernFolder;
        }
        return legacyFolder;
    }

    private void saveWorldManifest(String name, World.Environment environment, Long configuredSeed, World world) {
        String base = "worlds." + name;
        worldManifest.set(base + ".environment", environment.name());
        worldManifest.set(base + ".seed", configuredSeed);
        worldManifest.set(base + ".seed-configured", configuredSeed != null);
        worldManifest.set(base + ".actual-seed", world.getSeed());
        Path container = plugin.getServer().getWorldContainer().toPath().toAbsolutePath().normalize();
        Path folder = world.getWorldFolder().toPath().toAbsolutePath().normalize();
        worldManifest.set(base + ".folder", folder.startsWith(container)
            ? container.relativize(folder).toString().replace(File.separatorChar, '/') : folder.toString());
        try {
            File parent = worldManifestFile.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs())
                throw new IOException("Could not create plugin data directory.");
            File temporary = new File(worldManifestFile.getParentFile(), worldManifestFile.getName() + ".tmp");
            worldManifest.save(temporary);
            try {
                Files.move(temporary.toPath(), worldManifestFile.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary.toPath(), worldManifestFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            plugin.getLogger().info("[World] Stored managed settings for '" + name + "' (" + environment
                + ", seed=" + (configuredSeed == null ? "optional/unset" : configuredSeed)
                + ", actualSeed=" + world.getSeed() + ", folder=" + folder + ").");
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "[World] Could not save managed-world manifest for '" + name + "'.", exception);
        }
    }

    private void moveWorldFolder(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination);
        }
    }

    public Optional<HumanRealmDefinitionLoader.HumanRealmDefinition> getDefinition() {
        return Optional.ofNullable(definitions.get(HUMAN_REALM_ID));
    }

    public Map<String, HumanRealmDefinitionLoader.HumanRealmDefinition> getDefinitions() {
        return definitions;
    }

    public Optional<ResolvedSpawn> resolveHumanRealmSpawn() {
        return resolveSpawn(HUMAN_REALM_ID);
    }

    public Optional<ResolvedSpawn> resolveSpawn(String realmId) {
        HumanRealmDefinitionLoader.HumanRealmDefinition definition = definitions.get(realmId);
        if (definition == null) return Optional.empty();

        World realmWorld = Bukkit.getWorld(definition.worldName());
        if (realmWorld != null) {
            return createRealmSpawn(definition, realmWorld, false);
        }

        String fallbackName = definition.fallbackWorldName();
        if (fallbackName.equalsIgnoreCase(definition.worldName())) return Optional.empty();
        World fallbackWorld = Bukkit.getWorld(fallbackName);
        if (fallbackWorld == null) return Optional.empty();
        Location fallbackSpawn = fallbackWorld.getSpawnLocation();
        if (!isValidLocation(fallbackSpawn)) return Optional.empty();
        return Optional.of(new ResolvedSpawn(fallbackSpawn.clone(), true));
    }

    public boolean isInSafeZone(Location location) {
        HumanRealmDefinitionLoader.HumanRealmDefinition humanRealm = definitions.get(HUMAN_REALM_ID);
        if (location == null || location.getWorld() == null || humanRealm == null
            || !humanRealm.safeZone().enabled()) {
            return false;
        }
        Optional<ResolvedSpawn> resolvedSpawn = resolveHumanRealmSpawn();
        if (resolvedSpawn.isEmpty()) return false;

        Location center = resolvedSpawn.get().location();
        if (location.getWorld() != center.getWorld()) return false;
        return isWithinRadius(center.getX(), center.getZ(), location.getX(), location.getZ(),
            humanRealm.safeZone().radius());
    }

    static boolean isWithinRadius(double centerX, double centerZ, double x, double z, int radius) {
        double dx = x - centerX;
        double dz = z - centerZ;
        return dx * dx + dz * dz <= (double) radius * radius;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSafeZonePlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isInSafeZone(player.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSafeZonePlayerAttack(EntityDamageByEntityEvent event) {
        Player attacker = findPlayerAttacker(event.getDamager());
        if (attacker != null && isInSafeZone(attacker.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSafeZoneProjectileLaunch(ProjectileLaunchEvent event) {
        ProjectileSource shooter = event.getEntity().getShooter();
        if (shooter instanceof Player player && isInSafeZone(player.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSafeZoneHostileSpawn(CreatureSpawnEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Monster && isInSafeZone(entity.getLocation())) {
            event.setCancelled(true);
        }
    }

        private Optional<ResolvedSpawn> createRealmSpawn(
            HumanRealmDefinitionLoader.HumanRealmDefinition definition, World world, boolean fallback) {
        HumanRealmDefinitionLoader.Spawn spawn = definition.spawn();
        Location location = spawn.useWorldSpawn()
            ? world.getSpawnLocation()
            : new Location(world, spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
        location.setYaw(spawn.yaw());
        location.setPitch(spawn.pitch());
        if (!isValidLocation(location)) return Optional.empty();
        return Optional.of(new ResolvedSpawn(location.clone(), fallback));
    }

    private boolean isValidLocation(Location location) {
        World world = location.getWorld();
        return world != null
            && Double.isFinite(location.getX())
            && Double.isFinite(location.getY())
            && Double.isFinite(location.getZ())
            && location.getY() >= world.getMinHeight()
            && location.getY() < world.getMaxHeight()
            && world.getWorldBorder().isInside(location);
    }

    private Player findPlayerAttacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) return player;
        }
        return null;
    }

    private void logWorldAvailability(HumanRealmDefinitionLoader.HumanRealmDefinition definition) {
        if (Bukkit.getWorld(definition.worldName()) != null) {
            plugin.getLogger().info("[World] Realm '" + definition.id() + "' uses loaded world '" +
                definition.worldName() + "'.");
            if (createRealmSpawn(definition, Bukkit.getWorld(definition.worldName()), false).isEmpty()) {
                plugin.getLogger().severe("[World] Realm '" + definition.id() +
                    "' spawn is outside the loaded world's valid bounds.");
            }
            return;
        }

        plugin.getLogger().warning("[World] Realm world '" + definition.worldName() + "' is not loaded.");
        World fallbackWorld = Bukkit.getWorld(definition.fallbackWorldName());
        if (!definition.fallbackWorldName().equalsIgnoreCase(definition.worldName())
            && fallbackWorld != null
            && isValidLocation(fallbackWorld.getSpawnLocation())) {
            plugin.getLogger().warning("[World] Realm '" + definition.id() + "' uses fallback world '" +
                definition.fallbackWorldName() + "' spawn; no world will be created automatically.");
        } else {
            plugin.getLogger().severe("[World] Realm '" + definition.id() + "' has no valid primary or fallback world.");
        }
    }

    public record ResolvedSpawn(Location location, boolean fallbackUsed) {
    }
}
