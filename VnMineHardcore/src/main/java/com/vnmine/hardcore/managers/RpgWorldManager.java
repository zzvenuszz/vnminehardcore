package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
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
    private Map<String, HumanRealmDefinitionLoader.HumanRealmDefinition> definitions = Map.of();
    private boolean createWorldsOnNextReload = true;

    public RpgWorldManager(VnMineHardcore plugin) {
        this.plugin = plugin;
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
        createConfiguredWorld(plugin.getConfig().getString("rpg.onboarding.start-world.name", "world_start"),
            plugin.getConfig().getString("rpg.onboarding.start-world.environment", "NORMAL"),
            plugin.getConfig().getBoolean("rpg.onboarding.start-world.create-if-missing", true), "onboarding start world");
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
        createConfiguredWorld(yaml.getString("world-name"), yaml.getString("world.environment", "NORMAL"), true,
            "realm " + yaml.getString("id", file.getName()));
    }

    private void createConfiguredWorld(String name, String environmentName, boolean enabled, String reason) {
        if (!enabled || name == null || name.isBlank() || Bukkit.getWorld(name) != null) return;
        World.Environment environment;
        try {
            environment = World.Environment.valueOf(environmentName == null ? "NORMAL"
                : environmentName.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("[World] Invalid environment '" + environmentName + "' for " + reason
                + "; using NORMAL.");
            environment = World.Environment.NORMAL;
        }
        try {
            World world = Bukkit.createWorld(new WorldCreator(name).environment(environment));
            if (world == null) plugin.getLogger().severe("[World] Paper failed to create " + reason + " '" + name + "'.");
            else plugin.getLogger().info("[World] Created " + reason + " '" + name + "' (" + environment + ").");
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "[World] Failed to create " + reason + " '" + name + "'.", exception);
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
