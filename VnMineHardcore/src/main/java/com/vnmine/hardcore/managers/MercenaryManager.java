package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import io.papermc.paper.event.entity.EntityDamageItemEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.Particle.DustOptions;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Evoker;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LingeringPotion;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Slime;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.SplashPotion;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.TippedArrow;
import org.bukkit.entity.Vex;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wither;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.AbstractArrow.PickupStatus;
import org.bukkit.entity.Spellcaster.Spell;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseArmorEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntitySpellCastEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.permissions.Permission;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffectTypeCategory;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

public final class MercenaryManager implements CommandExecutor, TabCompleter, Listener {
   private static final String COMMAND_PERMISSION = "vnmine.command.army";
   private static final String ADMIN_PERMISSION = "vnmine.command.army.admin";
    private static final int MAIN_MENU_SIZE = 54;
    private static final int MAIN_MENU_STATS_SLOT = 10;
    private static final int MAIN_MENU_STATUS_SLOT = 12;
    private static final int MAIN_MENU_ARMY_SLOT = 14;
    private static final int MAIN_MENU_REWARD_SLOT = 16;
    private static final int MAIN_MENU_RPG_SLOT = 21;
    private static final int MAIN_MENU_WALLET_SLOT = 23;
    private static final int MAIN_MENU_ITEM_NAMES_SLOT = 25;
    private static final int MAIN_MENU_HELP_SLOT = 47;
    private static final int MAIN_MENU_ADMIN_SLOT = 51;
   private static final Set<String> VALID_SKILLS = Set.of(
      "ATTACK",
      "RANGED_ATTACK",
      "HEAL_OWNER",
      "HEAL_ALLIES",
      "BUFF_OWNER",
      "BUFF_ALLIES",
      "SHIELD_OWNER",
      "VOLLEY",
      "POWER_STRIKE",
      "TAUNT",
      "CLEANSE_OWNER",
      "FIRE_BOLT",
      "GUARDIAN_LINK",
      "GUARDIAN_CHARGE",
      "GUARDIAN_SHIELD",
      "GUARDIAN_QUAKE",
      "GUARDIAN_ULTIMATE",
      "ROOT_TARGET",
      "LIFESTEAL",
      "BERSERK",
      "SUMMON_VEXES",
      "LANCE_CHARGE",
      "ILLUMINATION",
      "FLAME_BOLT",
      "IGNITE",
      "FLAME_NOVA",
      "FIRE_WALL",
      "METEOR_STRIKE",
      "PHOENIX_REBIRTH",
      "ICE_SPIKE",
      "BLIZZARD",
      "FROST_NOVA",
      "ICE_WALL",
      "FROST_SHIELD",
      "FROST_STEP",
      "FROST_WALKER",
      "ICE_AGE"
   );
   private static final Set<String> FROST_MAGE_SKILLS = Set.of(
      "ICE_SPIKE", "BLIZZARD", "FROST_NOVA", "ICE_WALL", "FROST_SHIELD", "FROST_STEP", "FROST_WALKER", "ICE_AGE"
   );
   private static final Set<String> GUARDIAN_SKILLS = Set.of("GUARDIAN_LINK", "GUARDIAN_CHARGE", "GUARDIAN_SHIELD", "GUARDIAN_QUAKE", "GUARDIAN_ULTIMATE");
   private static final Set<String> FIRE_MAGE_SKILLS = Set.of("FLAME_BOLT", "IGNITE", "FLAME_NOVA", "FIRE_WALL", "METEOR_STRIKE", "PHOENIX_REBIRTH");
   private static final List<EquipmentSlot> EQUIPMENT_SLOTS = List.of(
      EquipmentSlot.HAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFF_HAND
   );
   private static final List<String> GEAR_MATERIALS = List.of("leather", "copper", "iron", "gold", "diamond", "netherite");
   private static final List<EquipmentSlot> SOLDIER_GEAR_SLOTS = List.of(
      EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.HAND
   );
   private static final List<Double> DEFAULT_GEAR_MATERIAL_MULTIPLIERS = List.of(1.0, 1.1, 1.22, 1.3, 1.45, 1.6);
   private static final List<Double> DEFAULT_GEAR_STAR_MULTIPLIERS = List.of(1.0, 1.08, 1.16, 1.25, 1.35);
   private static final String[] COMMANDS = new String[]{
      "list", "gui", "hire", "recall", "rest", "attack", "defend", "protect", "gear", "give", "token", "item", "remove", "clear", "mute", "rename"
   };
   private final VnMineHardcore plugin;
   private final File configFile;
   private final File dataFile;
   private final File torchFile;
   private final NamespacedKey unitKey;
   private final NamespacedKey ownerKey;
   private final NamespacedKey horseMountKey;
   private final NamespacedKey summonedMinionKey;
   private final NamespacedKey tokenClassKey;
   private final NamespacedKey tokenLevelKey;
   private final NamespacedKey soldierGearKey;
   private final NamespacedKey rpgSellValueKey;
   private final NamespacedKey soldierGearClassKey;
   private final NamespacedKey soldierGearMaterialKey;
   private final NamespacedKey soldierGearStarsKey;
   private final NamespacedKey soldierGearSetKey;
   private final NamespacedKey soldierGearSlotKey;
   private final NamespacedKey nativeSoldierGearKey;
   private final NamespacedKey welcomeGiftKey;
   private final NamespacedKey illuminationArrowKey;
   private final NamespacedKey frostProjectileDamageKey;
   private final NamespacedKey flameProjectileKey;
   private final NamespacedKey flameProjectileDamageKey;
   private final NamespacedKey meteorFireballKey;
   private final NamespacedKey meteorFireballDamageKey;
   private final NamespacedKey meteorFireballRadiusKey;
   private final NamespacedKey meteorFireballMaxTargetsKey;
   private final NamespacedKey meteorFireballBurnTicksKey;
   private final NamespacedKey flameBoltSlowKey;
   private final NamespacedKey flameBoltSlowExpiryKey;
   private final NamespacedKey icePrisonSlowKey;
   private final NamespacedKey icePrisonSlowExpiryKey;
   private final File welcomeGiftFile;
   private final Set<UUID> claimedWelcomeGiftPlayers = new HashSet<>();
   private final Map<UUID, MercenaryManager.TemporaryTorch> temporaryTorches = new HashMap<>();
   private final Map<UUID, BukkitTask> torchRemovalTasks = new HashMap<>();
   private final Map<String, MercenaryManager.TemporaryFrostBlock> temporaryFrostBlocks = new HashMap<>();
   private final Map<String, MercenaryManager.TemporaryFrostBlock> temporaryFireBlocks = new HashMap<>();
   private final Set<BukkitTask> frostSkillTasks = new HashSet<>();
   private final Set<BukkitTask> fireSkillTasks = new HashSet<>();
   private final Map<UUID, Set<BukkitTask>> fireTasksByUnit = new HashMap<>();
   private final Map<UUID, Set<BukkitTask>> frostTasksByUnit = new HashMap<>();
   private final Map<UUID, Set<UUID>> flameProjectilesByUnit = new HashMap<>();
   private final Map<UUID, Long> frostShieldExpiries = new HashMap<>();
   private final Map<UUID, Long> phoenixImmunityExpiries = new HashMap<>();
   private final Map<UUID, Long> flameBoltSlowExpiries = new HashMap<>();
   private final Map<UUID, Long> icePrisonSlowExpiries = new HashMap<>();
   private final Map<UUID, Map<PotionEffectType, MercenaryManager.FrostEffectSnapshot>> temporaryFrostEffects = new HashMap<>();
   private final Map<UUID, MercenaryManager.FrostFreezeSnapshot> temporaryFrostFreezes = new HashMap<>();
   private final MercenaryNamePacketListener namePacketListener;
   private final MercenaryStatusPacketListener statusPacketListener;
   private final Map<UUID, MercenaryManager.Unit> units = new HashMap<>();
   private final Map<UUID, Mob> activeEntities = new HashMap<>();
   private final Map<UUID, Map<String, Long>> skillCooldowns = new HashMap<>();
   private final Map<UUID, Long> soldierGearEffectCooldowns = new HashMap<>();
   private final Map<UUID, Set<UUID>> summonedMinions = new HashMap<>();
   private final Map<UUID, Integer> guardianHitCounters = new HashMap<>();
   private final Map<UUID, Long> guardianOwnerImmunityExpiries = new HashMap<>();
   private final Map<UUID, Set<BukkitTask>> guardianTasksByUnit = new HashMap<>();
   private final Map<String, MercenaryManager.GuardianFakeBlockOverlay> guardianFakeBlockOverlays = new HashMap<>();
   private final Map<UUID, UUID> ownerAttackTargets = new HashMap<>();
   private final Map<UUID, Long> ownerAttackTargetExpiries = new HashMap<>();
   private final Map<UUID, Map<UUID, Long>> ownerThreats = new HashMap<>();
   private final Map<UUID, Map<UUID, Long>> ownerAttackers = new HashMap<>();
   private final Map<UUID, Map<UUID, Long>> unitThreats = new HashMap<>();
   private final Map<UUID, UUID> mountByUnit = new HashMap<>();
   private final Map<UUID, Location> wanderTargets = new HashMap<>();
   private final Map<UUID, Long> wanderTargetExpiries = new HashMap<>();
   private final Set<UUID> awaitingGroupName = ConcurrentHashMap.newKeySet();
   private YamlConfiguration settings;
   private YamlConfiguration data;
   private boolean enabled;
   private boolean ownerCommandedPvpEnabled;
   private int maxUnits;
   private int maxDeployedUnits;
   private double followDistance;
   private double teleportDistance;
   private double targetRadius;
   private double wanderRadius;
   private boolean requireLineOfSight;
   private int aiIntervalTicks;
   private double healAmount;
   private double healRadius;
   private String ownerBuff;
   private int ownerBuffTicks;
   private double shieldReduction;
   private int experiencePerKill;
   private int experiencePerLevel;
   private int maxLevel;
   private int generatedGearSellValue;
   private int recoverySeconds;
   private double skillPowerPerLevel;
   private double maxSkillPowerMultiplier;
   private double skillCooldownReductionAtMaxLevel;
   private double foodHealAmount;
   private boolean experienceOrbPickupEnabled;
   private double experienceOrbPickupRadius;
   private boolean statusDisplayEnabled;
   private boolean statusDisplaySeeThrough;
   private int statusDisplayBarLength;
   private ChatColor vexHealthColor;
   private String vexAttackIcon;
   private String vexFollowIcon;
   private String vexIdleIcon;
   private BukkitRunnable aiTask;

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOwnerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player owner)) return;
        Long immunityExpiresAt = guardianOwnerImmunityExpiries.get(owner.getUniqueId());
        if (immunityExpiresAt != null) {
            if (immunityExpiresAt > System.currentTimeMillis()) {
                event.setCancelled(true);
                return;
            }
            guardianOwnerImmunityExpiries.remove(owner.getUniqueId(), immunityExpiresAt);
        }
        applyGuardianLink(event, owner);
        if (event instanceof EntityDamageByEntityEvent byEntity) applyOwnerShield(byEntity);
    }

   @EventHandler(ignoreCancelled = true)
    public void onFrostShieldDamage(EntityDamageByEntityEvent event) {
        UUID defenderId = event.getEntity().getUniqueId();
        Long expiresAt = frostShieldExpiries.get(defenderId);
        if (expiresAt == null) return;
        if (expiresAt <= System.currentTimeMillis()) {
            frostShieldExpiries.remove(defenderId);
            return;
        }
        Entity source = resolveDamageSource(event.getDamager());
        if (!(source instanceof Monster attacker) || !isValidTarget(attacker)) return;
        UUID unitId = getUnitId(event.getEntity());
        Unit unit = unitId == null ? null : units.get(unitId);
        if (unit == null) return;
        int duration = (int) skillOptionDouble(unit, "FROST_SHIELD",
            "attacker-slowness-duration-seconds", 3.0, 1.0, 15.0);
        int amplifier = (int) skillOptionDouble(unit, "FROST_SHIELD",
            "attacker-slowness-amplifier", 1.0, 0.0, 4.0);
        applyFrostPotionEffect(attacker, new PotionEffect(PotionEffectType.SLOWNESS,
            duration * 20, amplifier, true, true, true));
        attacker.getWorld().spawnParticle(Particle.SNOWFLAKE,
            attacker.getLocation().add(0.0, 1.0, 0.0), 8, 0.3, 0.4, 0.3, 0.02);
    }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPhoenixRebirthImmunity(EntityDamageEvent event) {
        Long expiresAt = phoenixImmunityExpiries.get(event.getEntity().getUniqueId());
        if (expiresAt == null) return;
        if (expiresAt <= System.currentTimeMillis()) {
            phoenixImmunityExpiries.remove(event.getEntity().getUniqueId());
            return;
        }
        event.setCancelled(true);
    }

   @EventHandler
    public void onFlameMageProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Projectile projectile)
            || !projectile.getPersistentDataContainer().has(flameProjectileKey, PersistentDataType.BYTE)
            || !(projectile.getShooter() instanceof Mob shooter)) return;
        removeTrackedFlameProjectile(shooter, projectile);
        if (!(event.getHitEntity() instanceof LivingEntity target) || !isActiveFireCaster(shooter)) {
            projectile.remove();
            return;
        }
        UUID ownerId = getOwnerId(shooter);
        if (!canMercenaryDamageTarget(ownerId, target)
                || (!isValidTarget(target) && !isOwnerCommandedPvpTarget(ownerId, target))) return;
        double damage = projectile.getPersistentDataContainer()
            .getOrDefault(flameProjectileDamageKey, PersistentDataType.DOUBLE, 0.0);
        projectile.remove();
        if (damage <= 0.0) return;
        target.damage(damage, shooter);
        UUID unitId = getUnitId(shooter);
        Unit unit = unitId == null ? null : units.get(unitId);
        if (unit != null) {
            applyFlameBoltSlow(target,
                flameOption(unit, "FLAME_BOLT", "slowness-percent", 20.0, 0.0, 80.0),
                flameOption(unit, "FLAME_BOLT", "slowness-duration-seconds", 3.0, 1.0, 15.0));
        }
        target.getWorld().spawnParticle(Particle.FLAME,
            target.getLocation().add(0.0, 1.0, 0.0), 16, 0.35, 0.4, 0.35, 0.02);
        target.getWorld().playSound(target.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.5f, 1.4f);
    }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMeteorFireballDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof SmallFireball fireball
                && (fireball.getPersistentDataContainer().has(meteorFireballKey, PersistentDataType.BYTE)
                    || fireball.getPersistentDataContainer().has(flameProjectileKey, PersistentDataType.BYTE))) {
            event.setCancelled(true);
        }
    }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMagePotionLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof ThrownPotion potion)
                || !(potion.getShooter() instanceof Mob shooter)) return;
        UUID unitId = getUnitId(shooter);
        Unit unit = unitId == null ? null : units.get(unitId);
        if (unit != null && ("pyromancer".equals(unit.unitClass) || "frost_mage".equals(unit.unitClass))) {
            event.setCancelled(true);
        }
    }

   @EventHandler
    public void onMeteorFireballHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof SmallFireball fireball)
                || !fireball.getPersistentDataContainer().has(meteorFireballKey, PersistentDataType.BYTE)
                || !(fireball.getShooter() instanceof Mob shooter)) return;
        removeTrackedFlameProjectile(shooter, fireball);
        if (!isActiveFireCaster(shooter)) {
            fireball.remove();
            return;
        }
        Location impact = fireball.getLocation().clone();
        double damage = fireball.getPersistentDataContainer()
            .getOrDefault(meteorFireballDamageKey, PersistentDataType.DOUBLE, 0.0);
        double radius = fireball.getPersistentDataContainer()
            .getOrDefault(meteorFireballRadiusKey, PersistentDataType.DOUBLE, 0.0);
        int maxTargets = fireball.getPersistentDataContainer()
            .getOrDefault(meteorFireballMaxTargetsKey, PersistentDataType.INTEGER, 0);
        int burnTicks = fireball.getPersistentDataContainer()
            .getOrDefault(meteorFireballBurnTicksKey, PersistentDataType.INTEGER, 0);
        fireball.remove();
        if (damage <= 0.0 || radius <= 0.0 || maxTargets <= 0) return;
        impact.getWorld().spawnParticle(Particle.FLAME, impact, 24, 0.4, 0.3, 0.4, 0.04);
        impact.getWorld().playSound(impact, Sound.ENTITY_BLAZE_SHOOT, 0.6f, 0.8f);
        for (LivingEntity victim : flameTargets(shooter, impact, radius, maxTargets)) {
            victim.damage(damage, shooter);
            if (burnTicks > 0) victim.setFireTicks(Math.max(victim.getFireTicks(), burnTicks));
        }
    }

   @EventHandler
    public void onFrostProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball)
                || !snowball.getPersistentDataContainer().has(frostProjectileDamageKey, PersistentDataType.DOUBLE)
                || !(event.getHitEntity() instanceof LivingEntity target)
                || !(snowball.getShooter() instanceof Mob shooter)
                || !canMercenaryDamageTarget(getOwnerId(shooter), target)
                || (!isValidTarget(target)
                    && !isOwnerCommandedPvpTarget(getOwnerId(shooter), target))) return;
        double damage = snowball.getPersistentDataContainer()
            .getOrDefault(frostProjectileDamageKey, PersistentDataType.DOUBLE, 0.0);
        if (damage <= 0.0) return;
        target.damage(damage, shooter);
        target.getWorld().spawnParticle(Particle.SNOWFLAKE,
            target.getLocation().add(0.0, 1.0, 0.0), 12, 0.3, 0.4, 0.3, 0.02);
    }

   @EventHandler(priority = EventPriority.HIGHEST)
    public void onOwnerExperienceOrbPickup(PlayerExpChangeEvent event) {
        if (!experienceOrbPickupEnabled) return;
        Player owner = event.getPlayer();
        double radiusSquared = experienceOrbPickupRadius * experienceOrbPickupRadius;
        boolean mercenaryNearby = getOwnerUnits(owner.getUniqueId()).stream()
            .filter(unit -> unit.deployed && unit.recoveryUntilMillis <= System.currentTimeMillis())
            .map(unit -> activeEntities.get(unit.id))
            .filter(mob -> mob != null && mob.isValid() && mob.getWorld().equals(owner.getWorld()))
            .anyMatch(mob -> mob.getLocation().distanceSquared(owner.getLocation()) <= radiusSquared);
        int experience = event.getAmount();
        if (!mercenaryNearby || experience <= 0) return;
        event.setAmount(0);
        owner.giveExp(experience);
    }

    private void despawnAllForReload() {
        for (UUID ownerId : units.values().stream().map(unit -> unit.owner).distinct().toList()) {
            despawnForOwner(ownerId);
        }
    }

   public MercenaryManager(VnMineHardcore plugin) {
      this.plugin = plugin;
      this.configFile = new File(plugin.getDataFolder(), "armies.yml");
      this.dataFile = new File(plugin.getDataFolder(), "mercenaries.yml");
      this.torchFile = new File(plugin.getDataFolder(), "ranger-torches.yml");
      this.welcomeGiftFile = new File(plugin.getDataFolder(), "new-player-gifts.yml");
      this.unitKey = new NamespacedKey(plugin, "army_unit");
      this.ownerKey = new NamespacedKey(plugin, "army_owner");
      this.horseMountKey = new NamespacedKey(plugin, "army_horse_mount");
      this.summonedMinionKey = new NamespacedKey(plugin, "army_summoned_minion");
      this.tokenClassKey = new NamespacedKey(plugin, "army_token_class");
      this.tokenLevelKey = new NamespacedKey(plugin, "army_token_level");
      this.soldierGearKey = new NamespacedKey(plugin, "army_soldier_gear");
      this.rpgSellValueKey = new NamespacedKey(plugin, "rpg_sell_value");
      this.soldierGearClassKey = new NamespacedKey(plugin, "army_soldier_gear_class");
      this.soldierGearMaterialKey = new NamespacedKey(plugin, "army_soldier_gear_material");
      this.soldierGearStarsKey = new NamespacedKey(plugin, "army_soldier_gear_stars");
      this.soldierGearSetKey = new NamespacedKey(plugin, "army_soldier_gear_set");
      this.soldierGearSlotKey = new NamespacedKey(plugin, "army_soldier_gear_slot");
      this.nativeSoldierGearKey = new NamespacedKey(plugin, "army_native_soldier_gear");
      this.welcomeGiftKey = new NamespacedKey(plugin, "welcome_gift");
      this.illuminationArrowKey = new NamespacedKey(plugin, "ranger_illumination_arrow");
      this.frostProjectileDamageKey = new NamespacedKey(plugin, "frost_projectile_damage");
      this.flameProjectileKey = new NamespacedKey(plugin, "flame_mage_projectile");
      this.flameProjectileDamageKey = new NamespacedKey(plugin, "flame_mage_projectile_damage");
      this.meteorFireballKey = new NamespacedKey(plugin, "meteor_fireball");
      this.meteorFireballDamageKey = new NamespacedKey(plugin, "meteor_fireball_damage");
      this.meteorFireballRadiusKey = new NamespacedKey(plugin, "meteor_fireball_radius");
      this.meteorFireballMaxTargetsKey = new NamespacedKey(plugin, "meteor_fireball_max_targets");
      this.meteorFireballBurnTicksKey = new NamespacedKey(plugin, "meteor_fireball_burn_ticks");
      this.flameBoltSlowKey = new NamespacedKey(plugin, "flame_bolt_slow");
      this.flameBoltSlowExpiryKey = new NamespacedKey(plugin, "flame_bolt_slow_expires");
      this.icePrisonSlowKey = new NamespacedKey(plugin, "ice_prison_slow");
      this.icePrisonSlowExpiryKey = new NamespacedKey(plugin, "ice_prison_slow_expires");
      this.namePacketListener = new MercenaryNamePacketListener(plugin);
      this.statusPacketListener = new MercenaryStatusPacketListener(plugin);
      this.loadSettings();
      this.loadData();
      this.loadWelcomeGiftClaims();
      this.loadTemporaryTorches();
      this.restoreFlameBoltSlowState();
      this.restoreIcePrisonSlowState();
      Bukkit.getPluginManager().registerEvents(this, plugin);
      this.startAiTask();

      for (Player player : Bukkit.getOnlinePlayers()) {
         Bukkit.getScheduler().runTask(plugin, () -> {
            this.removeSoldierGearFromPlayerEquipment(player);
            this.restoreDeployedUnits(player);
         });
      }

      plugin.getLogger().info("[Army] Loaded " + this.units.size() + " mercenary record(s).");
   }

    public boolean reload() {
        try {
            loadSettingsFromDisk();
            cleanupFrostEffects();
            cleanupGuardianTransientEffects();
            if (!enabled) despawnAllForReload();
            if (aiTask != null) {
                aiTask.cancel();
                aiTask = null;
            }

            startAiTask();
            if (enabled) {
                for (Player player : Bukkit.getOnlinePlayers()) restoreDeployedUnits(player);
            }
            return true;
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().log(Level.SEVERE, "[Army] Could not reload armies.yml; keeping old settings.",
                exception);
            return false;
        }
    }

    public int awardBossExperience(UUID ownerId, int amount) {
        if (amount <= 0) return 0;
        int recipients = 0;
        for (Unit unit : getOwnerUnits(ownerId)) {
            if (!unit.deployed || unit.recoveryUntilMillis > System.currentTimeMillis()) continue;
            if (addExperience(unit, amount)) recipients++;
        }
        if (recipients > 0 && !saveData()) {
            plugin.getLogger().severe("[Army] Could not save boss XP for owner " + ownerId + ".");
        }
        return recipients;
    }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      debugRpg("[RPG-DEBUG][Command] command=/" + command.getName() + ", sender="
         + sender.getName() + ", args=" + String.join(" ", args));
      if (command.getName().equalsIgnoreCase("vnmenu")) {
         if (sender instanceof Player player) {
            this.openMainMenu(player);
         } else {
            sender.sendMessage("§cMenu chỉ dành cho người chơi.");
         }

         return true;
      } else {
         if (command.getName().equalsIgnoreCase("quatanthu")) {
            return this.handleWelcomeGiftCommand(sender, args);
         }

         if (!sender.hasPermission("vnmine.command.army")) {
            sender.sendMessage("§cBạn không có quyền dùng /vnarmy.");
            return true;
         }

         if (!this.enabled) {
            sender.sendMessage("§cHệ thống lính đánh thuê hiện đang tắt.");
            return true;
         }

         if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            this.sendHelp(sender);
            return true;
         }

         if (args[0].equalsIgnoreCase("token")) {
            return this.handleTokenCommand(sender, args);
         }

         if (args[0].equalsIgnoreCase("give")) {
            return this.handleGiveCommand(sender, args);
         }

         if (args[0].equalsIgnoreCase("item")) {
            return this.handleSoldierGearCommand(sender, args);
         }

         if (args[0].equalsIgnoreCase("remove")) {
            return this.handleRemoveCommand(sender, args);
         }

         if (args[0].equalsIgnoreCase("clear")) {
            return this.handleClearCommand(sender, args);
         }

         if (args[0].equalsIgnoreCase("list") && args.length == 2 && sender.hasPermission("vnmine.command.army.admin")) {
            this.listUnits(sender, args[1]);
            return true;
         }

         if (sender instanceof Player player) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
               case "list":
                  this.listUnits(player);
                  break;
               case "gui":
                  this.openArmyMenu(player, 0);
                  break;
               case "hire":
                  this.hire(player, args);
                  break;
               case "recall":
                  this.recall(player, args);
                  break;
               case "rest":
                  this.rest(player, args);
                  break;
               case "attack":
                  this.setMode(player, MercenaryManager.Mode.ATTACK);
                  break;
               case "defend":
                  this.setMode(player, MercenaryManager.Mode.DEFEND);
                  break;
               case "protect":
                  this.setMode(player, MercenaryManager.Mode.PROTECT);
                  break;
               case "gear":
                  this.openGear(player, args);
                  break;
               case "mute":
                  this.toggleMuteCommand(player, args);
                  break;
               case "rename":
                  this.rename(player, args);
                  break;
               default:
                  this.sendHelp(player);
            }

            return true;
         } else {
            sender.sendMessage("§cLệnh này chỉ dành cho người chơi.");
            return true;
         }
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (!sender.hasPermission("vnmine.command.army")) {
         return List.of();
      } else if (args.length == 1) {
         return matching(args[0], COMMANDS);
      } else if (args.length == 2 && args[0].equalsIgnoreCase("item") && sender.hasPermission("vnmine.command.army.admin")) {
         return matching(args[1], "give");
      } else if (args.length == 3 && args[0].equalsIgnoreCase("item") && args[1].equalsIgnoreCase("give") && sender.hasPermission("vnmine.command.army.admin")) {
         return Bukkit.getOnlinePlayers().stream().<String>map(Player::getName).toList();
      } else if (args.length == 4 && args[0].equalsIgnoreCase("item") && args[1].equalsIgnoreCase("give") && sender.hasPermission("vnmine.command.army.admin")) {
         return matching(args[3], this.getClassIds().toArray(String[]::new));
      } else if (args.length == 5 && args[0].equalsIgnoreCase("item") && args[1].equalsIgnoreCase("give") && sender.hasPermission("vnmine.command.army.admin")) {
         return matching(args[4], GEAR_MATERIALS.toArray(String[]::new));
      } else if (args.length == 6 && args[0].equalsIgnoreCase("item") && args[1].equalsIgnoreCase("give") && sender.hasPermission("vnmine.command.army.admin")) {
         return matching(args[5], "1", "2", "3", "4", "5");
      } else if (args.length == 7 && args[0].equalsIgnoreCase("item") && args[1].equalsIgnoreCase("give") && sender.hasPermission("vnmine.command.army.admin")) {
         String classId = this.normalizeClass(args[3]);
         return classId == null ? List.of() : matching(args[6], this.gearSetIds(classId).toArray(String[]::new));
      } else if (args.length == 8 && args[0].equalsIgnoreCase("item") && args[1].equalsIgnoreCase("give") && sender.hasPermission("vnmine.command.army.admin")) {
         return matching(args[7], "helmet", "chestplate", "leggings", "boots", "mainhand", "offhand", "set");
      } else if (args.length == 2
         && (args[0].equalsIgnoreCase("list") || args[0].equalsIgnoreCase("clear"))
         && sender.hasPermission("vnmine.command.army.admin")) {
         return Bukkit.getOnlinePlayers().stream().<String>map(Player::getName).toList();
      } else if (args.length == 2 && args[0].equalsIgnoreCase("remove") && sender instanceof Player player) {
         List<String> ids = this.getOwnerUnits(player.getUniqueId()).stream().map(unit -> shortId(unit.id)).toList();
         return !sender.hasPermission("vnmine.command.army.admin")
            ? ids
            : Stream.concat(Bukkit.getOnlinePlayers().stream().map(Player::getName), ids.stream()).toList();
      } else if (args.length == 3 && args[0].equalsIgnoreCase("remove") && sender.hasPermission("vnmine.command.army.admin")) {
         Player owner = Bukkit.getPlayerExact(args[1]);
         return owner == null ? List.of() : this.getOwnerUnits(owner.getUniqueId()).stream().map(unit -> shortId(unit.id)).toList();
      } else if (args.length == 2 && args[0].equalsIgnoreCase("mute") && sender instanceof Player player) {
         return this.getOwnerUnits(player.getUniqueId()).stream().map(unit -> shortId(unit.id)).toList();
      } else if (args.length == 2 && Set.of("rename", "recall", "rest").contains(args[0].toLowerCase(Locale.ROOT)) && sender instanceof Player player) {
         return this.getOwnerUnits(player.getUniqueId()).stream().map(unit -> shortId(unit.id)).toList();
      } else if (args.length == 2 && args[0].equalsIgnoreCase("give") && sender.hasPermission("vnmine.command.army.admin")) {
         return Bukkit.getOnlinePlayers().stream().<String>map(Player::getName).toList();
      } else if (args.length == 3 && args[0].equalsIgnoreCase("give") && sender.hasPermission("vnmine.command.army.admin")) {
         return matching(args[2], "token", "army");
      } else if (args.length == 4 && args[0].equalsIgnoreCase("give") && sender.hasPermission("vnmine.command.army.admin") && args[2].equalsIgnoreCase("army")) {
         return matching(args[3], this.getClassIds().toArray(String[]::new));
      } else if (args.length == 5 && args[0].equalsIgnoreCase("give") && sender.hasPermission("vnmine.command.army.admin") && args[2].equalsIgnoreCase("army")) {
         return matching(args[4], this.getTierIds().toArray(String[]::new));
      } else if (args.length == 4 && args[0].equalsIgnoreCase("give") && sender.hasPermission("vnmine.command.army.admin") && args[2].equalsIgnoreCase("token")
         )
       {
         return matching(args[3], this.getTierIds().toArray(String[]::new));
      } else if (args.length == 2 && args[0].equalsIgnoreCase("hire")) {
         return matching(args[1], this.getClassIds().toArray(String[]::new));
      } else if (args.length == 3 && args[0].equalsIgnoreCase("hire")) {
         return matching(args[2], this.getTierIds().toArray(String[]::new));
      } else if (args.length == 2 && args[0].equalsIgnoreCase("token") && sender.hasPermission("vnmine.command.army.admin")) {
         return matching(args[1], "give");
      } else if (args.length == 3 && args[0].equalsIgnoreCase("token") && args[1].equalsIgnoreCase("give") && sender.hasPermission("vnmine.command.army.admin")
         )
       {
         return Bukkit.getOnlinePlayers().stream().<String>map(Player::getName).toList();
      } else if (args.length == 4 && args[0].equalsIgnoreCase("token") && args[1].equalsIgnoreCase("give") && sender.hasPermission("vnmine.command.army.admin")
         )
       {
         return matching(args[3], this.getClassIds().toArray(String[]::new));
      } else if (args.length == 5 && args[0].equalsIgnoreCase("token") && sender.hasPermission("vnmine.command.army.admin")) {
         return matching(args[4], this.getTierIds().toArray(String[]::new));
      } else if (args.length == 2 && args[0].equalsIgnoreCase("gear") && sender instanceof Player player) {
         String prefix = args[1].toLowerCase(Locale.ROOT);
         return this.getOwnerUnits(player.getUniqueId()).stream().map(unit -> shortId(unit.id)).filter(id -> id.startsWith(prefix)).toList();
      } else {
         return List.of();
      }
   }

   @EventHandler
   public void onPlayerJoin(PlayerJoinEvent event) {
      this.restoreFlameBoltSlowState(event.getPlayer());
      this.restoreIcePrisonSlowState(event.getPlayer());
      Bukkit.getScheduler().runTask(this.plugin, () -> this.removeSoldierGearFromPlayerEquipment(event.getPlayer()));
      Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.restoreDeployedUnits(event.getPlayer()), 1L);
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void preventSoldierGearArmorEquip(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         if (event.isShiftClick()
            && event.getClickedInventory() == player.getInventory()
            && event.getView().getType() == InventoryType.CRAFTING
            && this.isSoldierGear(event.getCurrentItem())
            && this.isPlayerEquipmentItem(event.getCurrentItem())) {
            event.setCancelled(true);
            player.sendMessage("§cVật phẩm này chỉ dành cho lính đánh thuê, bạn không thể trang bị.");
         } else if (event.getClickedInventory() == player.getInventory()) {
            int slot = event.getSlot();
            boolean armorOrOffhandSlot = slot >= 36 && slot <= 40;
            ItemStack incoming = event.getCursor();
            if (event.getHotbarButton() >= 0 && event.getHotbarButton() < 9) {
               ItemStack hotbar = player.getInventory().getItem(event.getHotbarButton());
               if (this.isSoldierGear(hotbar) && armorOrOffhandSlot) {
                  incoming = hotbar;
               }
            }

            if (armorOrOffhandSlot && this.isSoldierGear(incoming)) {
               event.setCancelled(true);
               player.sendMessage("§cVật phẩm này chỉ dành cho lính đánh thuê, bạn không thể trang bị.");
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void preventSoldierGearDispenserEquip(BlockDispenseArmorEvent event) {
      if (event.getTargetEntity() instanceof Player player && this.isSoldierGear(event.getItem())) {
         event.setCancelled(true);
         player.sendMessage("§cVật phẩm này chỉ dành cho lính đánh thuê, bạn không thể trang bị.");
      }
   }

   private boolean isPlayerEquipmentItem(ItemStack item) {
      EquipmentSlot slot = this.detectedEquipmentSlot(item.getType());
      return slot == EquipmentSlot.HEAD
         || slot == EquipmentSlot.CHEST
         || slot == EquipmentSlot.LEGS
         || slot == EquipmentSlot.FEET
         || slot == EquipmentSlot.OFF_HAND;
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void preventSoldierGearOffhandSwap(PlayerSwapHandItemsEvent event) {
      if (this.isSoldierGear(event.getMainHandItem()) || this.isSoldierGear(event.getOffHandItem())) {
         event.setCancelled(true);
         event.getPlayer().sendMessage("§cVật phẩm này chỉ dành cho lính đánh thuê, bạn không thể trang bị.");
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void preventSoldierGearArmorUse(PlayerInteractEvent event) {
      ItemStack item = event.getItem();
      if (this.isSoldierGear(item) && this.detectedEquipmentSlot(item.getType()) != null) {
         if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET).contains(this.detectedEquipmentSlot(item.getType()))) {
               event.setCancelled(true);
               event.getPlayer().sendMessage("§cVật phẩm này chỉ dành cho lính đánh thuê, bạn không thể trang bị.");
            }
         }
      }
   }

   private void removeSoldierGearFromPlayerEquipment(Player player) {
      if (player != null && player.isOnline()) {
         List<ItemStack> removed = new ArrayList<>();
         ItemStack[] armor = player.getInventory().getArmorContents();

         for (int slot = 0; slot < armor.length; slot++) {
            if (this.isSoldierGear(armor[slot])) {
               removed.add(armor[slot].clone());
               armor[slot] = null;
            }
         }

         player.getInventory().setArmorContents(armor);
         ItemStack offhand = player.getInventory().getItemInOffHand();
         if (this.isSoldierGear(offhand)) {
            removed.add(offhand.clone());
            player.getInventory().setItemInOffHand(null);
         }

         if (!removed.isEmpty()) {
            for (ItemStack item : removed) {
               this.giveItem(player, item);
            }

            player.sendMessage("§cVật phẩm dành riêng cho lính đã được tháo khỏi trang bị và trả vào túi đồ.");
         }
      }
   }

   @EventHandler(ignoreCancelled = true)
    public void onWelcomeGiftHold(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (isWelcomeGiftClaimed(player.getUniqueId())) return;
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        if (!isWelcomeGiftBag(newItem)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || isWelcomeGiftClaimed(player.getUniqueId())) return;
            if (isWelcomeGiftBag(player.getInventory().getItemInMainHand())) {
                openWelcomeGiftMenu(player);
            }
        });
    }

   @EventHandler(ignoreCancelled = true)
    public void onWelcomeGiftRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (!isWelcomeGiftBag(item)) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (isWelcomeGiftClaimed(player.getUniqueId())) {
            player.sendMessage("§cBạn đã nhận quà tân thủ rồi.");
            return;
        }
        openWelcomeGiftMenu(player);
    }

   @EventHandler(ignoreCancelled = true)
   public void onOpenMainMenu(PlayerSwapHandItemsEvent event) {
      if (!this.isSoldierGear(event.getMainHandItem()) && !this.isSoldierGear(event.getOffHandItem())) {
         if (this.plugin.getConfig().getBoolean("menus.main.enabled", true) && this.plugin.getConfig().getBoolean("menus.main.intercept-f-key", true)) {
            event.setCancelled(true);
            this.openMainMenu(event.getPlayer());
         }
      } else {
         event.setCancelled(true);
         event.getPlayer().sendMessage("§cVật phẩm này chỉ dành cho lính đánh thuê, bạn không thể trang bị.");
      }
   }

   @EventHandler(ignoreCancelled = true)
    public void onShiftRightClickMercenary(PlayerInteractEntityEvent event) {
        if (!event.getPlayer().isSneaking()) return;
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Mob mob) || !isMercenary(mob)) return;
        UUID ownerId = getOwnerId(mob);
        if (ownerId == null || !ownerId.equals(event.getPlayer().getUniqueId())) return;
        Unit unit = units.get(getUnitId(mob));
        if (unit == null) return;
        event.setCancelled(true);
        openGear(event.getPlayer(), unit);
    }

   @EventHandler
    public void onAdminGroupNameInput(AsyncPlayerChatEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (!awaitingGroupName.remove(playerId)) return;
        event.setCancelled(true);
        String groupName = event.getMessage().trim().toLowerCase(Locale.ROOT);
        Player player = event.getPlayer();
        if (!groupName.matches("[a-z0-9_-]{1,32}")) {
            Bukkit.getScheduler().runTask(plugin,
                () -> player.sendMessage("§cTên nhóm chỉ gồm chữ thường, số, _ hoặc - (1-32 ký tự)."));
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) player.performCommand("vngroup create " + groupName);
        });
    }

   @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        ownerAttackTargets.remove(event.getPlayer().getUniqueId());
        ownerAttackTargetExpiries.remove(event.getPlayer().getUniqueId());
        guardianOwnerImmunityExpiries.remove(event.getPlayer().getUniqueId());
        awaitingGroupName.remove(event.getPlayer().getUniqueId());
        despawnForOwner(event.getPlayer().getUniqueId());
    }

   @EventHandler
    public void onMercenaryDeath(EntityDeathEvent event) {
        String idValue = event.getEntity().getPersistentDataContainer().get(unitKey, PersistentDataType.STRING);
        if (idValue == null) return;
        namePacketListener.remove(event.getEntity());
        UUID id;
        try {
            id = UUID.fromString(idValue);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("[Army] Ignoring invalid unit UUID on entity " + event.getEntity().getUniqueId());
            return;
        }
        Unit unit = units.get(id);
        removeStatusDisplay(id);
        cleanupFireStateForUnit(id);
        cleanupGuardianStateForUnit(id);
        activeEntities.remove(id);
        skillCooldowns.remove(id);
        cleanupSummonedMinions(id);
        if (unit != null) {
            removeMount(id, event.getEntity() instanceof Mob deadMob ? deadMob : null);
            event.getDrops().clear();
            event.setDroppedExp(0);
            unit.deployed = false;
            unit.entityId = null;
            unit.recoveryUntilMillis = System.currentTimeMillis() + recoverySeconds * 1000L;
            Player owner = Bukkit.getPlayer(unit.owner);
            if (owner != null) owner.sendMessage("§cLính đánh thuê §e" + displayName(unit)
                + " §cđã bị hạ và đang hồi phục trong §e" + recoverySeconds + " giây§c. Trang bị được bảo toàn.");
            if (!saveData()) {
                plugin.getLogger().severe("[Army] Could not persist recovery state for mercenary " + id + ".");
            }
        }
    }

   @EventHandler
    public void onMercenaryHorseDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Horse horse)) return;
        String unitIdValue = horse.getPersistentDataContainer()
            .get(horseMountKey, PersistentDataType.STRING);
        if (unitIdValue == null) return;
        UUID unitId;
        try {
            unitId = UUID.fromString(unitIdValue);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("[Army] Ignoring invalid horse unit UUID on entity " + horse.getUniqueId());
            return;
        }
        Unit unit = units.get(unitId);
        if (unit == null || !"paladin".equals(unit.unitClass)) return;
        unit.horseDead = true;
        mountByUnit.remove(unitId, horse.getUniqueId());
        event.getDrops().clear();
        event.setDroppedExp(0);
        Player owner = Bukkit.getPlayer(unit.owner);
        if (owner != null) {
            owner.sendMessage("§eNgựa của §6" + displayName(unit)
                + " §eđã chết. Kỵ sĩ sẽ tiếp tục đi bộ; hãy cho lính nghỉ rồi xuất chiến lại để có ngựa mới.");
        }
        if (!saveData()) {
            plugin.getLogger().severe("[Army] Could not persist dead horse state for mercenary " + unitId + ".");
        }
    }

   @EventHandler
    public void onMercenaryEntityRemove(EntityRemoveEvent event) {
        if (isMercenary(event.getEntity())) namePacketListener.remove(event.getEntity());
        UUID unitId = getUnitId(event.getEntity());
        if (unitId != null) removeStatusDisplay(unitId);
    }

   @EventHandler
    public void onMercenaryEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (isMercenary(entity)) namePacketListener.remove(entity);
            UUID unitId = getUnitId(entity);
            if (unitId != null) removeStatusDisplay(unitId);
        }
    }

   @EventHandler
    public void onMercenaryEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof LivingEntity living) restoreFlameBoltSlowState(living);
            if (entity instanceof LivingEntity living) restoreIcePrisonSlowState(living);
            if (!(entity instanceof Mob mob) || !isMercenary(mob)) continue;
            String idValue = mob.getPersistentDataContainer().get(unitKey, PersistentDataType.STRING);
            try {
                Unit unit = idValue == null ? null : units.get(UUID.fromString(idValue));
                if (unit != null && unit.deployed) {
                    preventZombification(mob);
                    activeEntities.put(unit.id, mob);
                    unit.entityId = mob.getUniqueId();
                    repairEquipmentFromSaved(unit, mob);
                    updateEntityName(unit, mob);
                }
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("[Army] Ignoring invalid unit UUID on entity " + mob.getUniqueId());
            }
        }
    }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMercenaryEnvironmentDamage(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK
                && isMercenaryMount(event.getEntity())) {
            event.setCancelled(true);
            return;
        }
        if (!isMercenary(event.getEntity())) return;
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            event.setCancelled(true);
            return;
        }
        Entity attacker = byEntity.getDamager();
        if (attacker instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Entity shooter) attacker = shooter;
        if (!(attacker instanceof LivingEntity) || isMercenary(attacker)) event.setCancelled(true);
    }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMercenarySunCombust(EntityCombustEvent event) {
        if (event.getClass() != EntityCombustEvent.class) return;
        if (isMercenary(event.getEntity()) || isMercenaryMount(event.getEntity())) {
            event.setCancelled(true);
        }
    }

   @EventHandler
    public void onMercenaryKillExperience(EntityDeathEvent event) {
        Entity source = event.getDamageSource().getCausingEntity();
        if (source instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Entity shooter) source = shooter;
        if (source == null) return;
        boolean mercenaryKill = isMercenary(source) || isSummonedMinion(source);
        UUID ownerId = null;
        String unitId = source.getPersistentDataContainer().get(unitKey, PersistentDataType.STRING);
        if (unitId != null) {
            try {
                Unit killer = units.get(UUID.fromString(unitId));
                if (killer != null) ownerId = killer.owner;
            } catch (IllegalArgumentException ignored) {
                return;
            }
        } else {
            ownerId = getOwnerId(source);
            if (ownerId == null && source instanceof Player player) ownerId = player.getUniqueId();
        }
        if (ownerId == null) return;
        if (mercenaryKill) {
            Player owner = Bukkit.getPlayer(ownerId);
            int droppedExperience = event.getDroppedExp();
            if (owner != null && owner.isOnline() && droppedExperience > 0) {
                owner.giveExp(droppedExperience);
                event.setDroppedExp(0);
            }
        }
        if (experiencePerKill <= 0) return;
        List<Unit> recipients = getOwnerUnits(ownerId).stream()
            .filter(unit -> unit.deployed && unit.recoveryUntilMillis <= System.currentTimeMillis())
            .sorted(Comparator.comparing(unit -> unit.id.toString()))
            .toList();
        if (recipients.isEmpty()) return;
        int baseShare = experiencePerKill / recipients.size();
        int remainder = experiencePerKill % recipients.size();
        boolean changed = false;
        for (int index = 0; index < recipients.size(); index++) {
            Unit unit = recipients.get(index);
            int share = baseShare + (index < remainder ? 1 : 0);
            if (share <= 0) continue;
            changed |= addExperience(unit, share);
        }
        if (changed && !saveData()) {
            plugin.getLogger().warning("[Army] Could not save kill XP for owner " + ownerId + ".");
        }
    }

   @EventHandler
    public void onMercenaryTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !isMercenary(mob)) return;
        if (isSummonedMinion(mob)) {
            if (!canMinionTarget(mob, event.getTarget())) {
                event.setCancelled(true);
                mob.setTarget(null);
            }
            return;
        }
        LivingEntity target = event.getTarget();
        if (target == null) return;
        if (mob instanceof Villager) {
            event.setCancelled(true);
            mob.setTarget(null);
            return;
        }
        UUID ownerId = getOwnerId(mob);
        boolean ownerCommandedTarget = ownerId != null
            && isActiveOwnerAttackTarget(ownerId, target.getUniqueId());
        if (target.getUniqueId().equals(ownerId)
                || !isAllowedMercenaryTarget(ownerId, target, ownerCommandedTarget)
                || (requireLineOfSight && !mob.hasLineOfSight(target))
                || isHiddenUndergroundTarget(mob, target)) {
            event.setCancelled(true);
            mob.setTarget(null);
            return;
        }
    }

   @EventHandler(ignoreCancelled = true)
    public void onMercenarySpellCast(EntitySpellCastEvent event) {
        if (event.getEntity() instanceof Evoker evoker && isMercenary(evoker)
                && event.getSpell() == Spell.SUMMON_VEX) {
            event.setCancelled(true);
        }
    }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onMercenaryTransform(EntityTransformEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Mob mob) || !isMercenary(mob)) return;
        boolean converting = false;
        String conversionTime = "n/a";
        boolean immune = false;
        if (mob instanceof PiglinAbstract piglin) {
            immune = piglin.isImmuneToZombification();
            if (piglin.isConverting()) {
                converting = true;
                conversionTime = Integer.toString(piglin.getConversionTime());
            }
        }
        List<String> equipmentBefore = occupiedEquipmentSlots(mob);
        event.setCancelled(true);
        preventZombification(mob);
        plugin.getLogger().warning("[Army] Cancelled transform for mercenary " + mob.getUniqueId()
            + " (reason=" + event.getTransformReason() + ", converting=" + converting
            + ", conversionTicks=" + conversionTime + ", immuneBefore=" + immune
            + ", equipmentBefore=" + equipmentBefore + ").");

        UUID unitId = getUnitId(mob);
        Unit unit = unitId == null ? null : units.get(unitId);
        if (unit != null) {
            reconcileEquipment(unit, mob, false);
            if (!saveData()) {
                plugin.getLogger().warning("[Army] Could not save gear after cancelling transform for " + unit.id);
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!mob.isValid() || !isUnit(mob, unit.id)) return;
                preventZombification(mob);
                repairEquipmentFromSaved(unit, mob);
                if (!saveData()) {
                    plugin.getLogger().warning("[Army] Could not save restored gear for mercenary " + unit.id + ".");
                }
            });
        }
    }

    private void repairEquipmentFromSaved(Unit unit, Mob mob) {
        List<String> restored = reconcileEquipment(unit, mob, true);
        if (!restored.isEmpty()) {
            plugin.getLogger().warning("[Army] Restored missing equipment slots " + restored
                + " for mercenary " + unit.id + ".");
        }
    }

    private List<String> reconcileEquipment(Unit unit, Mob mob, boolean restoreMissing) {
        EntityEquipment equipment = mob.getEquipment();
        if (equipment == null) return List.of();
        List<String> restored = new ArrayList<>();
        for (org.bukkit.inventory.EquipmentSlot slot : EQUIPMENT_SLOTS) {
            ItemStack item = equipment.getItem(slot);
            ItemStack saved = unit.equipment.get(slot);
            if (restoreMissing && (item == null || item.getType().isAir())
                    && saved != null && !saved.getType().isAir()) {
                equipment.setItem(slot, saved.clone());
                restored.add(slot.name());
                item = equipment.getItem(slot);
            }
            if (item != null && !item.getType().isAir()) {
                unit.equipment.put(slot, item.clone());
            }
        }
        return restored;
    }

    private List<String> occupiedEquipmentSlots(Mob mob) {
        EntityEquipment equipment = mob.getEquipment();
        if (equipment == null) return List.of();
        List<String> slots = new ArrayList<>();
        for (org.bukkit.inventory.EquipmentSlot slot : EQUIPMENT_SLOTS) {
            ItemStack item = equipment.getItem(slot);
            if (item != null && !item.getType().isAir()) slots.add(slot.name());
        }
        return slots;
    }

   @EventHandler(ignoreCancelled = true)
    public void onSummonedMinionTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !isSummonedMinion(mob)) return;
        if (!canMinionTarget(mob, event.getTarget())) {
            event.setCancelled(true);
            mob.setTarget(null);
        }
    }

   @EventHandler(priority = EventPriority.HIGHEST)
    public void onMercenaryFriendlyFire(EntityDamageByEntityEvent event) {
        Entity source = resolveDamageSource(event.getDamager());
        if (!isMercenary(source)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (!canMercenaryDamageTarget(getOwnerId(source), target)) event.setCancelled(true);
    }

   @EventHandler
    public void onMercenaryItemDamage(EntityDamageItemEvent event) {
        if (isMercenary(event.getEntity())) event.setCancelled(true);
    }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onMercenaryCombat(EntityDamageByEntityEvent event) {
      Entity source = this.resolveDamageSource(event.getDamager());
      if (this.isMercenary(source) && event.getEntity() instanceof LivingEntity victim && !this.canMercenaryDamageTarget(this.getOwnerId(source), victim)) {
         event.setCancelled(true);
      } else if (!event.isCancelled()) {
         MercenaryManager.Unit attackingUnit = null;
         if (source instanceof Mob mob && this.isMercenary(mob)) {
            UUID unitId = this.getUnitId(mob);
            attackingUnit = unitId == null ? null : this.units.get(unitId);
            double multiplier = 1.0;
            if (this.hasMainHandWeapon(mob)) {
               multiplier *= attackingUnit == null
                  ? this.readBoundedDouble(this.settings, "combat.main-hand-damage-multiplier", 1.3, 1.0, 3.0)
                  : this.mainHandDamageMultiplier(attackingUnit);
            }

            if (attackingUnit != null) {
               MercenaryManager.GearStats gear = this.soldierGearStats(attackingUnit);

               double elementalBonus = switch (attackingUnit.unitClass) {
                  case "pyromancer" -> gear.fireDamagePercent();
                  case "frost_mage" -> gear.frostDamagePercent();
                  case "druid" -> gear.natureDamagePercent();
                  default -> 0.0;
               };
               multiplier *= 1.0 + (gear.damagePercent() + elementalBonus) / 100.0;
            }

            if (multiplier > 1.0) {
               event.setDamage(event.getDamage() * multiplier);
            }
         }

         if (attackingUnit != null
            && "guardian".equals(attackingUnit.unitClass)
            && event.getEntity() instanceof LivingEntity target
            && this.isHostileMob(target)) {
            this.handleGuardianBasicHit(attackingUnit, (Mob)source, target, event);
         }

         if (attackingUnit != null && event.getEntity() instanceof LivingEntity target && !event.isCancelled()) {
            this.applySoldierGearHitEffect(attackingUnit, target);
         }

         if (event.getEntity() instanceof Player player) {
            this.registerOwnerThreat(player.getUniqueId(), source);
            this.registerThreat(this.ownerAttackers, player.getUniqueId(), source);
         }

         if (event.getEntity() instanceof LivingEntity victim && !(victim instanceof Player)) {
            UUID victimOwner = this.getOwnerId(victim);
            if (victimOwner != null) {
               this.registerOwnerThreat(victimOwner, source);
            }

            UUID victimUnitId = this.getUnitId(victim);
            if (victimUnitId != null) {
               this.registerThreat(this.unitThreats, victimUnitId, source);
            }
         }

         UUID attackingOwner = source instanceof LivingEntity living ? this.getOwnerId(living) : null;
         if (attackingOwner != null && event.getEntity() instanceof LivingEntity victim) {
            Player owner = Bukkit.getPlayer(attackingOwner);
            if (victim.getUniqueId().equals(attackingOwner)) {
               event.setCancelled(true);
               return;
            }

            if (owner != null && owner.isOnline()) {
               victim.setKiller(owner);
            }
         }

         if (this.isMercenary(event.getEntity())) {
            UUID ownerId = this.getOwnerId(event.getEntity());
            Entity attacker = event.getDamager();
            if (attacker instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
               attacker = shooter;
            }

            if (ownerId != null && (attacker.getUniqueId().equals(ownerId) || this.isMercenary(attacker) && ownerId.equals(this.getOwnerId(attacker)))) {
               event.setCancelled(true);
               return;
            }
         }

         if (source instanceof Player owner && event.getEntity() instanceof LivingEntity target && !target.getUniqueId().equals(owner.getUniqueId())) {
            UUID targetOwner = this.getOwnerId(target);
            if (targetOwner == null || !targetOwner.equals(owner.getUniqueId())) {
               this.ownerAttackTargets.put(owner.getUniqueId(), target.getUniqueId());
               this.ownerAttackTargetExpiries.put(owner.getUniqueId(), System.currentTimeMillis() + 15000L);

               for (MercenaryManager.Unit unit : this.getOwnerUnits(owner.getUniqueId())) {
                  Mob mob = this.activeEntities.get(unit.id);
                  if (unit.deployed
                     && mob != null
                     && mob.isValid()
                     && (this.isValidTarget(target) && !this.isMercenary(target) || this.isMercenary(target))
                     && this.isAllowedMercenaryTarget(unit.owner, target, true)) {
                     mob.setTarget(target);
                  }
               }
            }
         }
      }
   }

   @EventHandler
   public void onGearInventoryClose(InventoryCloseEvent event) {
      if (event.getInventory().getHolder() instanceof MercenaryManager.GearHolder holder
         && event.getPlayer() instanceof Player player
         && holder.owner.equals(player.getUniqueId())) {
         MercenaryManager.Unit unit = this.units.get(holder.unit);
         if (unit != null && unit.owner.equals(player.getUniqueId())) {
            Inventory inventory = event.getInventory();
            EnumMap<EquipmentSlot, ItemStack> previousEquipment = new EnumMap<>(EquipmentSlot.class);
            unit.equipment.forEach((slot, item) -> previousEquipment.put(slot, cloneOrNull(item)));
            ItemStack[] previousInventory = new ItemStack[unit.inventory.length];

            for (int slot = 0; slot < unit.inventory.length; slot++) {
               previousInventory[slot] = cloneOrNull(unit.inventory[slot]);
            }

            boolean previousCustomized = unit.gearCustomized;
            ItemStack previousHorseArmor = cloneOrNull(unit.horseArmor);
            this.captureGearInventory(player, unit, inventory);
            unit.gearCustomized = true;
            Mob mob = this.activeEntities.get(unit.id);
            if (mob != null && mob.isValid()) {
               this.applyEquipment(unit, mob);
               this.applyStats(unit, mob, false);
            }

            this.applyHorseArmorToMount(unit);
            if (!this.saveData()) {
               unit.equipment.clear();
               unit.equipment.putAll(previousEquipment);
               System.arraycopy(previousInventory, 0, unit.inventory, 0, unit.inventory.length);
               unit.gearCustomized = previousCustomized;
               unit.horseArmor = previousHorseArmor;
               if (mob != null && mob.isValid()) {
                  this.applyEquipment(unit, mob);
                  this.applyStats(unit, mob, false);
               }

               this.applyHorseArmorToMount(unit);
               this.plugin.getLogger().severe("[Army] Could not save gear changes for mercenary " + unit.id + ".");
               player.sendMessage("§cKhông thể lưu trang bị lính vào mercenaries.yml; giữ lính hiện tại và thử lưu lại.");
               this.openUnitMenuAfterGearClose(player, unit, holder);
            } else {
               player.sendMessage("§aĐã lưu trang bị cho lính §e#" + shortId(unit.id) + "§a.");
               this.openUnitMenuAfterGearClose(player, unit, holder);
            }
         }
      }
   }

   private void openUnitMenuAfterGearClose(Player player, MercenaryManager.Unit unit, MercenaryManager.GearHolder holder) {
      if (holder.returnToUnitMenu) {
         Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (player.isOnline() && this.units.get(unit.id) == unit) {
               this.openUnitMenu(player, unit);
            }
         });
      }
   }

   @EventHandler
   public void onGearInventoryClick(InventoryClickEvent event) {
      if (event.getView().getTopInventory().getHolder() instanceof MercenaryManager.GearHolder holder) {
         if (event.isShiftClick()) {
            event.setCancelled(true);
         } else if (event.getClickedInventory() == event.getView().getTopInventory()) {
            int slot = event.getRawSlot();
            if (slot == 53) {
               event.setCancelled(true);
               ItemStack cursor = event.getCursor();
               if (cursor != null && !cursor.getType().isAir()) {
                  event.getWhoClicked().sendMessage("§cHãy đặt vật phẩm đang cầm vào kho hoặc ô trang bị trước khi quay lại.");
               } else {
                  holder.returnToUnitMenu = true;
                  event.getWhoClicked().closeInventory();
               }
            } else {
               if (slot >= 36 && slot < 45 || slot > 51) {
                  event.setCancelled(true);
               }
            }
         }
      }
   }

   @EventHandler
    public void onGearInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GearHolder)) return;
        if (event.getRawSlots().stream().anyMatch(slot -> (slot >= 36 && slot < 45) || slot > 51)) {
            event.setCancelled(true);
        }
    }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void preventSoldierGearArmorDrag(InventoryDragEvent event) {
      if (event.getWhoClicked() instanceof Player player && this.isSoldierGear(event.getOldCursor())) {
         int topSize = event.getView().getTopInventory().getSize();
         boolean targetsPlayerEquipment = event.getRawSlots()
            .stream()
            .filter(rawSlot -> rawSlot >= topSize)
            .<Integer>map(event.getView()::convertSlot)
            .anyMatch(slot -> slot >= 36 && slot <= 40);
         if (targetsPlayerEquipment) {
            event.setCancelled(true);
            player.sendMessage("§cVật phẩm này chỉ dành cho lính đánh thuê, bạn không thể trang bị.");
         }
      }
   }

    private void loadSettings() {
        if (!configFile.isFile()) {
            if (!plugin.getDataFolder().isDirectory() && !plugin.getDataFolder().mkdirs()) {
                throw new IllegalStateException("Could not create plugin data directory for armies.yml.");
            }
            plugin.saveResource("armies.yml", false);
        }
        try {
            loadSettingsFromDisk();
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalStateException("Could not load armies.yml.", exception);
        }
    }

   private void loadSettingsFromDisk() throws IOException, InvalidConfigurationException {
      YamlConfiguration loaded = new YamlConfiguration();
      loaded.options().parseComments(true);
      loaded.load(this.configFile);
      this.mergeBundledDefaults(loaded);
      if (loaded.getConfigurationSection("classes") == null || loaded.getConfigurationSection("tiers") == null) {
         throw new InvalidConfigurationException("armies.yml must define classes and tiers.");
      }

      if (loaded.contains("enabled") && !loaded.isBoolean("enabled")) {
         throw new InvalidConfigurationException("enabled must be true or false.");
      }

      if (loaded.contains("pvp.allow-owner-commanded-targets") && !loaded.isBoolean("pvp.allow-owner-commanded-targets")) {
         throw new InvalidConfigurationException("pvp.allow-owner-commanded-targets must be true or false.");
      }

      if (!loaded.getConfigurationSection("classes").getKeys(false).isEmpty() && !loaded.getConfigurationSection("tiers").getKeys(false).isEmpty()) {
         int configuredMaxLevel = this.readBoundedInt(loaded, "progression.max-level", 30, 1, 30);

         for (String classId : loaded.getConfigurationSection("classes").getKeys(false)) {
            if (!classId.matches("[a-z0-9_-]{1,32}")) {
               throw new InvalidConfigurationException("Invalid mercenary class ID '" + classId + "'.");
            }

            Material menuIcon = Material.matchMaterial(loaded.getString("classes." + classId + ".menu-icon", "IRON_SWORD"));
            if (menuIcon == null && "paladin".equals(classId) && "IRON_SPEAR".equalsIgnoreCase(loaded.getString("classes." + classId + ".menu-icon", ""))) {
               menuIcon = Material.TRIDENT;
            }

            if (menuIcon != null && !menuIcon.isAir()) {
               Material startingWeapon = Material.matchMaterial(loaded.getString("classes." + classId + ".starting-weapon", "IRON_SWORD"));
               String startingWeaponName = loaded.getString("classes." + classId + ".starting-weapon", "IRON_SWORD");
               boolean deferredSpear = "paladin".equals(classId)
                  && ("IRON_SPEAR".equalsIgnoreCase(startingWeaponName) || "WOODEN_SPEAR".equalsIgnoreCase(startingWeaponName));
               boolean unarmed = "AIR".equalsIgnoreCase(startingWeaponName) && startingWeapon != null && startingWeapon.isAir();
               if ((startingWeapon == null || startingWeapon.isAir()) && !deferredSpear && !unarmed) {
                  throw new InvalidConfigurationException("Invalid starting-weapon for class '" + classId + "'.");
               }

               String offhandName = loaded.getString("classes." + classId + ".starting-offhand", "AIR");
               Material startingOffhand = Material.matchMaterial(offhandName);
               if (startingOffhand != null && (!startingOffhand.isAir() || "AIR".equalsIgnoreCase(offhandName))) {
                  for (String preferenceKey : List.of("preferred-main-hand-items", "preferred-offhand-items")) {
                     String preferencePath = "classes." + classId + "." + preferenceKey;
                     Object configuredPreferences = loaded.get(preferencePath);
                     if (configuredPreferences != null) {
                        if (!(configuredPreferences instanceof List<?> preferences) || preferences.stream().anyMatch(item -> !(item instanceof String))) {
                           throw new InvalidConfigurationException(preferencePath + " must be a list of materials.");
                        }

                        for (Object preference : preferences) {
                           String materialName = (String)preference;
                           boolean spearAlias = "paladin".equals(classId)
                              && Set.of("IRON_SPEAR", "WOODEN_SPEAR").contains(materialName.toUpperCase(Locale.ROOT));
                           if (Material.matchMaterial(materialName) == null && !spearAlias) {
                              throw new InvalidConfigurationException("Unknown material '" + materialName + "' in " + preferencePath + ".");
                           }
                        }
                     }
                  }

                  String skillsPath = "classes." + classId + ".skills";
                  if (loaded.get(skillsPath) instanceof List<?> configuredSkills && !configuredSkills.stream().anyMatch(skillx -> !(skillx instanceof String))) {
                     String entityName = loaded.getString("classes." + classId + ".entity-type", "");
                     EntityType entityType = EntityType.fromName(entityName);
                     Class<? extends Entity> entityClass = entityType == null ? null : entityType.getEntityClass();
                     if (entityType != null && entityType.isAlive() && entityClass != null && Mob.class.isAssignableFrom(entityClass)) {
                        for (String skill : loaded.getStringList("classes." + classId + ".skills")) {
                           if (!VALID_SKILLS.contains(skill.toUpperCase(Locale.ROOT))) {
                              throw new InvalidConfigurationException("Unknown skill '" + skill + "' in class '" + classId + "'.");
                           }

                           if (FROST_MAGE_SKILLS.contains(skill.toUpperCase(Locale.ROOT)) && !"frost_mage".equals(classId)) {
                              throw new InvalidConfigurationException("Frost Mage skill '" + skill + "' can only be used by class 'frost_mage'.");
                           }

                           if (FIRE_MAGE_SKILLS.contains(skill.toUpperCase(Locale.ROOT)) && !"pyromancer".equals(classId)) {
                              throw new InvalidConfigurationException("Fire Mage skill '" + skill + "' can only be used by class 'pyromancer'.");
                           }

                           if (GUARDIAN_SKILLS.contains(skill.toUpperCase(Locale.ROOT)) && !"guardian".equals(classId)) {
                              throw new InvalidConfigurationException("Guardian skill '" + skill + "' can only be used by class 'guardian'.");
                           }
                        }

                        ConfigurationSection skillSettings = loaded.getConfigurationSection("classes." + classId + ".skill-settings");
                        if (skillSettings != null) {
                           for (String skill : skillSettings.getKeys(false)) {
                              if (!VALID_SKILLS.contains(skill.toUpperCase(Locale.ROOT))) {
                                 throw new InvalidConfigurationException("Unknown skill-settings entry '" + skill + "' in class '" + classId + "'.");
                              }

                              if (FROST_MAGE_SKILLS.contains(skill.toUpperCase(Locale.ROOT)) && !"frost_mage".equals(classId)) {
                                 throw new InvalidConfigurationException("Frost Mage skill-settings '" + skill + "' can only be used by class 'frost_mage'.");
                              }

                              if (FIRE_MAGE_SKILLS.contains(skill.toUpperCase(Locale.ROOT)) && !"pyromancer".equals(classId)) {
                                 throw new InvalidConfigurationException("Fire Mage skill-settings '" + skill + "' can only be used by class 'pyromancer'.");
                              }

                              if (GUARDIAN_SKILLS.contains(skill.toUpperCase(Locale.ROOT)) && !"guardian".equals(classId)) {
                                 throw new InvalidConfigurationException("Guardian skill-settings '" + skill + "' can only be used by class 'guardian'.");
                              }

                              String target = loaded.getString("classes." + classId + ".skill-settings." + skill + ".target");
                              if (target != null && !Set.of("OWNER", "ALLIES", "OWNER_AND_ALLIES").contains(target.toUpperCase(Locale.ROOT))) {
                                 throw new InvalidConfigurationException(
                                    "Invalid target '" + target + "' for " + classId + "." + skill + "; expected OWNER, ALLIES, or OWNER_AND_ALLIES."
                                 );
                              }

                              if (skill.equalsIgnoreCase("CLEANSE_OWNER")) {
                                 for (String effect : loaded.getStringList("classes." + classId + ".skill-settings." + skill + ".effects")) {
                                    if (Registry.EFFECT.get(NamespacedKey.minecraft(effect.toLowerCase(Locale.ROOT))) == null) {
                                       throw new InvalidConfigurationException(
                                          "Unknown potion effect '" + effect + "' in " + classId + ".CLEANSE_OWNER.effects."
                                       );
                                    }
                                 }
                              }

                              if (skill.equalsIgnoreCase("ROOT_TARGET") || skill.equalsIgnoreCase("BERSERK")) {
                                 String defaultEffect = skill.equalsIgnoreCase("ROOT_TARGET") ? "SLOWNESS" : "STRENGTH";
                                 String effect = loaded.getString("classes." + classId + ".skill-settings." + skill + ".effect", defaultEffect);
                                 if (effect == null || effect.isBlank()) {
                                    effect = defaultEffect;
                                 }

                                 if (Registry.EFFECT.get(NamespacedKey.minecraft(effect.toLowerCase(Locale.ROOT))) == null) {
                                    throw new InvalidConfigurationException("Unknown potion effect '" + effect + "' in " + classId + "." + skill + ".effect.");
                                 }
                              }

                              if (skill.equalsIgnoreCase("FIRE_WALL")) {
                                 String configuredMaterial = loaded.getString(
                                    "classes." + classId + ".skill-settings." + skill + ".block-material", "MAGMA_BLOCK"
                                 );
                                 Material wallMaterial = Material.matchMaterial(configuredMaterial);
                                 if (wallMaterial == null || wallMaterial.isAir() || !wallMaterial.isSolid()) {
                                    throw new InvalidConfigurationException(
                                       "Invalid solid block-material '" + configuredMaterial + "' in " + classId + ".FIRE_WALL."
                                    );
                                 }
                              }

                              if (skill.equalsIgnoreCase("GUARDIAN_QUAKE")) {
                                 String configuredMaterial = loaded.getString(
                                    "classes." + classId + ".skill-settings." + skill + ".fake-block-material", "COBBLESTONE"
                                 );
                                 Material fakeMaterial = Material.matchMaterial(configuredMaterial);
                                 if (fakeMaterial == null || fakeMaterial.isAir() || !fakeMaterial.isSolid()) {
                                    throw new InvalidConfigurationException(
                                       "Invalid solid fake-block-material '" + configuredMaterial + "' in " + classId + ".GUARDIAN_QUAKE."
                                    );
                                 }
                              }
                           }
                        }

                        double baseHealth = loaded.getDouble("classes." + classId + ".base-health", 24.0);
                        double healthGrowth = loaded.getDouble("classes." + classId + ".health-per-level", 4.0);
                        double baseDamage = loaded.getDouble("classes." + classId + ".base-damage", 4.0);
                        double damageGrowth = loaded.getDouble("classes." + classId + ".damage-per-level", 0.5);
                        double baseArmor = loaded.getDouble("classes." + classId + ".base-armor", 0.0);
                        double armorGrowth = loaded.getDouble("classes." + classId + ".armor-per-level", 0.0);
                        if (Double.isFinite(baseHealth)
                           && !(baseHealth <= 0.0)
                           && Double.isFinite(healthGrowth)
                           && !(healthGrowth < 0.0)
                           && Double.isFinite(baseDamage)
                           && !(baseDamage < 0.0)
                           && Double.isFinite(damageGrowth)
                           && !(damageGrowth < 0.0)
                           && Double.isFinite(baseArmor)
                           && !(baseArmor < 0.0)
                           && Double.isFinite(armorGrowth)
                           && !(armorGrowth < 0.0)
                           && !(baseHealth + healthGrowth * (configuredMaxLevel + 12.0) > 1024.0)
                           && !(baseDamage + damageGrowth * (configuredMaxLevel + 12.0) > 1024.0)
                           && !(baseArmor + armorGrowth * (configuredMaxLevel + 12.0) > 100.0)) {
                           if ("guardian".equals(classId)) {
                              double healthPerTier = loaded.getDouble("classes.guardian.health-per-tier", 6.0);
                              double armorPerTier = loaded.getDouble("classes.guardian.armor-per-tier", 0.5);
                              int armorStepLevels = loaded.getInt("classes.guardian.armor-level-step-levels", 5);
                              double armorPerLevelStep = loaded.getDouble("classes.guardian.armor-per-level-step", 0.5);
                              double damagePerTier = loaded.getDouble("classes.guardian.damage-per-tier", 0.75);
                              double shieldHealthBonus = loaded.getDouble("classes.guardian.shield-offhand-health-bonus", 8.0);
                              double swordMultiplier = loaded.getDouble("classes.guardian.sword-damage-multiplier", 1.1);
                              if (!Double.isFinite(healthPerTier)
                                 || healthPerTier < 0.0
                                 || healthPerTier > 100.0
                                 || !Double.isFinite(armorPerTier)
                                 || armorPerTier < 0.0
                                 || armorPerTier > 10.0
                                 || armorStepLevels < 1
                                 || armorStepLevels > 30
                                 || !Double.isFinite(armorPerLevelStep)
                                 || armorPerLevelStep < 0.0
                                 || armorPerLevelStep > 10.0
                                 || !Double.isFinite(damagePerTier)
                                 || damagePerTier < 0.0
                                 || damagePerTier > 10.0
                                 || !Double.isFinite(shieldHealthBonus)
                                 || shieldHealthBonus < 0.0
                                 || shieldHealthBonus > 100.0
                                 || !Double.isFinite(swordMultiplier)
                                 || swordMultiplier < 1.0
                                 || swordMultiplier > 3.0
                                 || baseHealth + healthPerTier * 12.0 + healthGrowth * (configuredMaxLevel - 1.0) + shieldHealthBonus > 1024.0
                                 || baseArmor + armorPerTier * 12.0 + armorPerLevelStep * ((double)configuredMaxLevel / armorStepLevels) > 100.0
                                 || baseDamage + damagePerTier * 12.0 + damageGrowth * (configuredMaxLevel - 1.0) > 1024.0) {
                                 throw new InvalidConfigurationException("Invalid tier/level/equipment stats for class 'guardian'.");
                              }
                           }

                           ConfigurationSection upgrades = loaded.getConfigurationSection("classes." + classId + ".tier-upgrades");
                           if (upgrades != null) {
                              label367:
                              for (String targetTier : upgrades.getKeys(false)) {
                                 int tierNumber = parsePositiveInt(targetTier);
                                 ConfigurationSection costs = upgrades.getConfigurationSection(targetTier + ".materials");
                                 if (tierNumber >= 2 && tierNumber <= 13 && costs != null) {
                                    Iterator upgradeMaterialKeys = costs.getKeys(false).iterator();

                                    String materialName;
                                    int amount;
                                    Material material;
                                    do {
                                       if (!upgradeMaterialKeys.hasNext()) {
                                          continue label367;
                                       }

                                       materialName = (String)upgradeMaterialKeys.next();
                                       material = Material.matchMaterial(materialName);
                                       amount = costs.getInt(materialName, -1);
                                    } while (material != null && !material.isAir() && amount >= 0 && amount <= 1000000);

                                    throw new InvalidConfigurationException(
                                       "Invalid tier upgrade material '" + materialName + "' for class '" + classId + "' at tier " + targetTier + "."
                                    );
                                 }

                                 throw new InvalidConfigurationException("Invalid tier upgrade cost for class '" + classId + "' at tier " + targetTier + ".");
                              }
                           }
                           continue;
                        }

                        throw new InvalidConfigurationException("Invalid stats for class '" + classId + "'.");
                     }

                     throw new InvalidConfigurationException("Entity type for class '" + classId + "' must be an AI mob.");
                  }

                  throw new InvalidConfigurationException(skillsPath + " must be a list of skill names.");
               }

               throw new InvalidConfigurationException("Invalid starting-offhand for class '" + classId + "'.");
            }

            throw new InvalidConfigurationException("Invalid menu-icon for class '" + classId + "'.");
         }

         ConfigurationSection configuredGearSets = loaded.getConfigurationSection("gear-system.sets");
         if (configuredGearSets != null) {
            for (String classId : configuredGearSets.getKeys(false)) {
               if (loaded.getConfigurationSection("classes." + classId) == null) {
                  throw new InvalidConfigurationException("Gear sets reference unknown mercenary class '" + classId + "'.");
               }

               ConfigurationSection classSets = loaded.getConfigurationSection("gear-system.sets." + classId);
               if (classSets == null) {
                  throw new InvalidConfigurationException("Gear sets for class '" + classId + "' must be a section of set IDs.");
               }

               for (String setId : classSets.getKeys(false)) {
                  String setPath = "gear-system.sets." + classId + "." + setId;
                  if (!setId.matches("[a-z0-9_-]{1,32}") || loaded.getConfigurationSection(setPath) == null) {
                     throw new InvalidConfigurationException("Invalid gear set '" + setPath + "'; set IDs must match [a-z0-9_-]{1,32}.");
                  }

                  String displayName = loaded.getString(setPath + ".display-name");
                  if (displayName != null && (displayName.isBlank() || displayName.length() > 64)) {
                     throw new InvalidConfigurationException("Gear set display-name at '" + setPath + "' must contain 1-64 characters.");
                  }
               }
            }
         }

         Set<String> validAiActions = Set.of(
            "OWNER_ATTACK_TARGET", "OWNER_ATTACKER", "OWNER_THREAT", "NEAREST_HOSTILE", "SEARCH", "FOLLOW_OWNER", "RETURN_TO_OWNER", "WANDER"
         );

         for (String mode : List.of("attack", "defend", "protect")) {
            String path = "ai.priorities." + mode;
            List<String> priorities = loaded.getStringList(path);
            Set<String> normalized = priorities.stream().map(value -> value.toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
            if (priorities.isEmpty() || normalized.size() != priorities.size() || !validAiActions.containsAll(normalized)) {
               throw new InvalidConfigurationException("Invalid AI priority list at '" + path + "'.");
            }
         }

         for (String tier : loaded.getConfigurationSection("tiers").getKeys(false)) {
            int level = parsePositiveInt(tier);
            int xp = loaded.getInt("tiers." + tier + ".experience-levels", -1);
            ConfigurationSection materials = loaded.getConfigurationSection("tiers." + tier + ".materials");
            if (level < 1 || level > 10 || xp < 0 || xp > 100000 || materials == null) {
               throw new InvalidConfigurationException("Invalid costs in tier '" + tier + "'.");
            }

            for (String materialName : materials.getKeys(false)) {
               Material material = Material.matchMaterial(materialName);
               int amount = materials.getInt(materialName, -1);
               if (material == null || material.isAir() || amount < 0 || amount > 1000000) {
                  throw new InvalidConfigurationException("Invalid material cost '" + materialName + "' in tier '" + tier + "'.");
               }
            }
         }

         String configuredBuff = loaded.getString("skills.owner-buff", "SPEED");
         String resolvedBuff = configuredBuff != null && !configuredBuff.isBlank() ? configuredBuff.toUpperCase(Locale.ROOT) : "SPEED";
         if (Registry.EFFECT.get(NamespacedKey.minecraft(resolvedBuff.toLowerCase(Locale.ROOT))) == null) {
            throw new InvalidConfigurationException("Unknown skills.owner-buff effect '" + resolvedBuff + "'.");
         }

         this.settings = loaded;
         this.generatedGearSellValue = this.readBoundedInt(loaded, "gear-system.sell-value", 0, 0, 1_000_000);
         this.enabled = loaded.getBoolean("enabled", true);
         this.ownerCommandedPvpEnabled = loaded.getBoolean("pvp.allow-owner-commanded-targets", true);
         this.maxUnits = this.readBoundedInt(loaded, "max-units-per-player", 10, 1, 100);
         this.maxDeployedUnits = this.readBoundedInt(loaded, "max-summoned-per-player", 3, 1, 10);
         this.followDistance = this.readBoundedDouble(loaded, "follow-distance-blocks", 8.0, 2.0, 64.0);
         this.teleportDistance = this.readBoundedDouble(loaded, "teleport-distance-blocks", 40.0, 8.0, 256.0);
         this.targetRadius = this.readBoundedDouble(loaded, "target-radius-blocks", 50.0, 4.0, 50.0);
         this.wanderRadius = this.readBoundedDouble(loaded, "ai.wander-radius-blocks", 6.0, 1.0, 32.0);
         this.requireLineOfSight = loaded.getBoolean("ai.require-line-of-sight", true);
         this.aiIntervalTicks = this.readBoundedInt(loaded, "ai-interval-ticks", 20, 10, 100);
         this.healAmount = this.readBoundedDouble(loaded, "skills.heal-amount", 4.0, 0.0, 100.0);
         this.healRadius = this.readBoundedDouble(loaded, "skills.heal-radius-blocks", 8.0, 1.0, 32.0);
         this.ownerBuff = resolvedBuff;
         this.ownerBuffTicks = this.readBoundedInt(loaded, "skills.owner-buff-seconds", 8, 1, 600) * 20;
         this.shieldReduction = this.readBoundedDouble(loaded, "skills.owner-damage-reduction", 0.2, 0.0, 0.9);
         this.experiencePerKill = this.readBoundedInt(loaded, "progression.experience-per-kill", 5, 0, 100000);
         this.experiencePerLevel = this.readBoundedInt(loaded, "progression.experience-per-level", 100, 1, 100000);
         this.skillPowerPerLevel = this.readBoundedDouble(loaded, "progression.skill-power-per-level", 0.05, 0.0, 1.0);
         this.maxSkillPowerMultiplier = this.readBoundedDouble(loaded, "progression.max-skill-power-multiplier", 2.5, 1.0, 5.0);
         this.skillCooldownReductionAtMaxLevel = this.readBoundedDouble(loaded, "progression.skill-cooldown-reduction-at-max-level-seconds", 3.0, 0.0, 10.0);
         this.recoverySeconds = this.readBoundedInt(loaded, "recovery.seconds", 300, 1, 86400);
         this.foodHealAmount = this.readBoundedDouble(loaded, "gear.food-heal-amount", 6.0, 0.0, 100.0);
         this.experienceOrbPickupEnabled = loaded.getBoolean("experience-orb-pickup.enabled", true);
         this.experienceOrbPickupRadius = this.readBoundedDouble(loaded, "experience-orb-pickup.radius-blocks", 4.0, 1.0, 16.0);
         this.statusDisplayEnabled = loaded.getBoolean("status-display.enabled", true);
         this.statusDisplaySeeThrough = loaded.getBoolean("status-display.see-through", true);
         this.statusDisplayBarLength = this.readBoundedInt(loaded, "status-display.bar-length", 10, 5, 20);
         this.vexHealthColor = this.readVexHealthColor(loaded);
         this.vexAttackIcon = this.readVexActionIcon(loaded, "attack", "⚔");
         this.vexFollowIcon = this.readVexActionIcon(loaded, "follow", "\ud83d\udc3e");
         this.vexIdleIcon = this.readVexActionIcon(loaded, "idle", "•");
         this.maxLevel = configuredMaxLevel;
         this.plugin.getLogger().info("[Army] Loaded " + this.getClassIds().size() + " class(es), enabled="
            + this.enabled + ", generated-gear-sell-value=" + this.generatedGearSellValue + " VNCOIN/item.");
      } else {
         throw new InvalidConfigurationException("armies.yml must define at least one class and one tier.");
      }
   }

   private void mergeBundledDefaults(YamlConfiguration loaded) throws IOException, InvalidConfigurationException {
      boolean rangerIlluminationMigrationComplete = loaded.getBoolean("_migrations.ranger-illumination-skill", false);
      boolean rangerRangeTuningMigrationComplete = loaded.getBoolean("_migrations.ranger-range-and-damage-tuning", false);
      InputStream resource = this.plugin.getResource("armies.yml");
      if (resource == null) {
         throw new IOException("Bundled armies.yml is missing.");
      }

      YamlConfiguration defaults = new YamlConfiguration();
      defaults.options().parseComments(true);
      InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8);

      try {
         defaults.load(reader);
      } catch (Throwable resourceLoadFailure) {
         try {
            reader.close();
         } catch (Throwable resourceCloseFailure) {
            resourceLoadFailure.addSuppressed(resourceCloseFailure);
         }

         throw resourceLoadFailure;
      }

      reader.close();
      boolean settingsChanged = this.mergeSettingsSection(loaded, defaults, "");
      if (!loaded.getBoolean("_migrations.mage-prison-and-firestorm-skills", false)) {
         String iceWallPath = "classes.frost_mage.skill-settings.ICE_WALL";
         if (loaded.getDouble(iceWallPath + ".duration-seconds", 3.0) == 6.0
            && loaded.getInt(iceWallPath + ".width-blocks", 5) == 5
            && loaded.getInt(iceWallPath + ".height-blocks", 2) == 2) {
            loaded.set(iceWallPath + ".duration-seconds", 3.0);
            loaded.setComments(iceWallPath + ".duration-seconds", defaults.getComments(iceWallPath + ".duration-seconds"));
            this.plugin.getLogger().info("[Army] Updated the default ICE_WALL duration to 3 seconds.");
         }

         String oldFlameNovaDamagePath = "classes.pyromancer.skill-settings.FLAME_NOVA.damage";
         double oldFlameNovaDamage = loaded.getDouble(oldFlameNovaDamagePath, 6.0);
         if (Double.compare(oldFlameNovaDamage, 6.0) != 0) {
            String newDamagePath = "classes.pyromancer.skill-settings.FLAME_NOVA.damage-per-second";
            loaded.set(newDamagePath, oldFlameNovaDamage);
            loaded.setComments(newDamagePath, defaults.getComments(newDamagePath));
         }

         String oldMeteorRadiusPath = "classes.pyromancer.skill-settings.METEOR_STRIKE.radius-blocks";
         double oldMeteorRadius = loaded.getDouble(oldMeteorRadiusPath, 4.0);
         if (Double.compare(oldMeteorRadius, 4.0) != 0) {
            String newRadiusPath = "classes.pyromancer.skill-settings.METEOR_STRIKE.area-radius-blocks";
            loaded.set(newRadiusPath, oldMeteorRadius);
            loaded.setComments(newRadiusPath, defaults.getComments(newRadiusPath));
         }

         String oldMeteorDelayPath = "classes.pyromancer.skill-settings.METEOR_STRIKE.impact-delay-ticks";
         double oldMeteorDelay = loaded.getDouble(oldMeteorDelayPath, 30.0);
         if (Double.compare(oldMeteorDelay, 30.0) != 0) {
            String newDelayPath = "classes.pyromancer.skill-settings.METEOR_STRIKE.warning-delay-seconds";
            loaded.set(newDelayPath, oldMeteorDelay / 20.0);
            loaded.setComments(newDelayPath, defaults.getComments(newDelayPath));
         }

         String oldMeteorTargetsPath = "classes.pyromancer.skill-settings.METEOR_STRIKE.max-targets";
         int oldMeteorTargets = loaded.getInt(oldMeteorTargetsPath, 24);
         if (oldMeteorTargets != 24) {
            String newTargetsPath = "classes.pyromancer.skill-settings.METEOR_STRIKE.max-targets-per-fireball";
            loaded.set(newTargetsPath, oldMeteorTargets);
            loaded.setComments(newTargetsPath, defaults.getComments(newTargetsPath));
         }

         loaded.set("_migrations.mage-prison-and-firestorm-skills", true);
         settingsChanged = true;
      }

      if (!loaded.getBoolean("_migrations.pyromancer-flame-nova-cone", false)) {
         String flameNovaRadiusPath = "classes.pyromancer.skill-settings.FLAME_NOVA.radius-blocks";
         if (Double.compare(loaded.getDouble(flameNovaRadiusPath, 3.0), 3.0) == 0) {
            loaded.set(flameNovaRadiusPath, 5.0);
            loaded.setComments(flameNovaRadiusPath, defaults.getComments(flameNovaRadiusPath));
            this.plugin.getLogger().info("[Army] Updated the default FLAME_NOVA radius to 5 blocks.");
         }

         loaded.set("_migrations.pyromancer-flame-nova-cone", true);
         settingsChanged = true;
      }

      if (!loaded.getBoolean("_migrations.pyromancer-six-skills", false)) {
         Set<String> previousDefaults = Set.of("RANGED_ATTACK", "FIRE_BOLT", "ROOT_TARGET", "BUFF_OWNER");
         List<String> configuredSkills = loaded.getStringList("classes.pyromancer.skills");
         Set<String> normalizedSkills = configuredSkills.stream().map(skill -> skill.toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
         if (normalizedSkills.equals(previousDefaults)) {
            String skillPath = "classes.pyromancer.skills";
            loaded.set(skillPath, defaults.getStringList(skillPath));
            loaded.setComments(skillPath, defaults.getComments(skillPath));
            String descriptionPath = "classes.pyromancer.description";
            if ("Thiêu đốt mục tiêu, làm chậm kẻ địch và kháng lửa cho chủ.".equals(loaded.getString(descriptionPath))) {
               loaded.set(descriptionPath, defaults.getString(descriptionPath));
               loaded.setComments(descriptionPath, defaults.getComments(descriptionPath));
            }

            this.plugin.getLogger().info("[Army] Migrated the default Pyromancer skill kit to the six fire skills.");
         }

         loaded.set("_migrations.pyromancer-six-skills", true);
         settingsChanged = true;
      }

      if (!loaded.getBoolean("_migrations.pyromancer-fire-charge-weapon", false)) {
         String weaponPath = "classes.pyromancer.starting-weapon";
         if ("BLAZE_ROD".equalsIgnoreCase(loaded.getString(weaponPath, "BLAZE_ROD"))) {
            loaded.set(weaponPath, "FIRE_CHARGE");
            loaded.setComments(weaponPath, defaults.getComments(weaponPath));
         }

         loaded.set("_migrations.pyromancer-fire-charge-weapon", true);
         settingsChanged = true;
      }

      if (!loaded.getBoolean("_migrations.class-weapons-and-icons-2026", false)) {
         Map<String, String> oldDefaults = Map.ofEntries(
            Map.entry("classes.guardian.starting-weapon", "IRON_SWORD"),
            Map.entry("classes.medic.starting-weapon", "GOLDEN_HOE"),
            Map.entry("classes.paladin.starting-weapon", "IRON_SPEAR"),
            Map.entry("classes.berserker.starting-weapon", "IRON_AXE"),
            Map.entry("classes.assassin.starting-weapon", "IRON_SWORD"),
            Map.entry("classes.druid.starting-weapon", "STICK"),
            Map.entry("classes.frost_mage.starting-weapon", "SNOWBALL"),
            Map.entry("classes.pyromancer.starting-weapon", "FIRE_CHARGE"),
            Map.entry("classes.paladin.menu-icon", "TRIDENT"),
            Map.entry("classes.assassin.menu-icon", "DIAMOND_SWORD"),
            Map.entry("classes.druid.menu-icon", "OAK_SAPLING")
         );
         Map<String, String> newDefaults = Map.ofEntries(
            Map.entry("classes.guardian.starting-weapon", "WOODEN_SWORD"),
            Map.entry("classes.medic.starting-weapon", "AIR"),
            Map.entry("classes.paladin.starting-weapon", "WOODEN_SPEAR"),
            Map.entry("classes.berserker.starting-weapon", "WOODEN_AXE"),
            Map.entry("classes.assassin.starting-weapon", "WOODEN_SWORD"),
            Map.entry("classes.druid.starting-weapon", "AIR"),
            Map.entry("classes.frost_mage.starting-weapon", "AIR"),
            Map.entry("classes.pyromancer.starting-weapon", "AIR"),
            Map.entry("classes.paladin.menu-icon", "IRON_SPEAR"),
            Map.entry("classes.assassin.menu-icon", "IRON_SWORD"),
            Map.entry("classes.druid.menu-icon", "ENCHANTED_BOOK")
         );

         for (Entry<String, String> migration : oldDefaults.entrySet()) {
            String path = migration.getKey();
            if (migration.getValue().equalsIgnoreCase(loaded.getString(path, migration.getValue()))) {
               loaded.set(path, newDefaults.get(path));
               loaded.setComments(path, defaults.getComments(path));
            }
         }

         loaded.set("_migrations.class-weapons-and-icons-2026", true);
         settingsChanged = true;
      }

      String legacySnowballSettingsPath = "classes.frost_mage.skill-settings.SNOWBALL";
      ConfigurationSection legacySnowballSettings = loaded.getConfigurationSection(legacySnowballSettingsPath);
      if (legacySnowballSettings != null) {
         String basicAttackPath = "classes.frost_mage.basic-attack";

         for (String option : legacySnowballSettings.getKeys(false)) {
            String targetPath = basicAttackPath + "." + option;
            if (!loaded.contains(targetPath)) {
               loaded.set(targetPath, legacySnowballSettings.get(option));
            }
         }

         loaded.set(legacySnowballSettingsPath, null);
         settingsChanged = true;
         this.plugin.getLogger().info("[Army] Migrated legacy frost_mage.skill-settings.SNOWBALL settings to frost_mage.basic-attack.");
      }

      List<String> rangerSkills = new ArrayList<>(loaded.getStringList("classes.ranger.skills"));
      if (!rangerIlluminationMigrationComplete) {
         boolean addedIllumination = false;
         if (rangerSkills.stream().noneMatch(skill -> skill.equalsIgnoreCase("ILLUMINATION"))) {
            rangerSkills.add("ILLUMINATION");
            loaded.set("classes.ranger.skills", rangerSkills);
            loaded.setComments("classes.ranger.skills", defaults.getComments("classes.ranger.skills"));
            addedIllumination = true;
         }

         loaded.set("_migrations.ranger-illumination-skill", true);
         settingsChanged = true;
         if (addedIllumination) {
            this.plugin.getLogger().info("[Army] Enabled the new ILLUMINATION skill for the Ranger in armies.yml.");
         }
      }

      if (!rangerRangeTuningMigrationComplete) {
         this.migrateRangerDefault(loaded, defaults, "classes.ranger.base-damage", 4.0, 2.8);
         this.migrateRangerDefault(loaded, defaults, "classes.ranger.damage-per-level", 0.45, 0.35);
         this.migrateRangerDefault(loaded, defaults, "classes.ranger.skill-settings.RANGED_ATTACK.damage", 4.5, 4.2);
         this.migrateRangerDefault(loaded, defaults, "classes.ranger.skill-settings.VOLLEY.damage", 2.5, 2.1);
         loaded.set("_migrations.ranger-range-and-damage-tuning", true);
         settingsChanged = true;
      }

      if (loaded.getInt("max-units-per-player", 10) == 4) {
         loaded.set("max-units-per-player", 10);
         settingsChanged = true;
      }

      if (loaded.getInt("progression.max-level", 30) > 30) {
         loaded.set("progression.max-level", 30);
         settingsChanged = true;
      }

      if ("TRIDENT".equalsIgnoreCase(loaded.getString("classes.paladin.starting-weapon", ""))) {
         String weaponPath = "classes.paladin.starting-weapon";
         loaded.set(weaponPath, "IRON_SPEAR");
         loaded.setComments(weaponPath, defaults.getComments(weaponPath));
         settingsChanged = true;
      }

      if (!loaded.getBoolean("_migrations.guardian-husk-model", false)) {
         String guardianEntityPath = "classes.guardian.entity-type";
         if ("VINDICATOR".equalsIgnoreCase(loaded.getString(guardianEntityPath, ""))) {
            loaded.set(guardianEntityPath, defaults.getString(guardianEntityPath, "HUSK"));
            loaded.setComments(guardianEntityPath, defaults.getComments(guardianEntityPath));
            this.plugin.getLogger().info("[Army] Updated the default Guardian entity to HUSK to display items in both hands.");
         }

         loaded.set("_migrations.guardian-husk-model", true);
         settingsChanged = true;
      }

      String berserkerEntityPath = "classes.berserker.entity-type";
      if ("PIGLIN_BRUTE".equalsIgnoreCase(loaded.getString(berserkerEntityPath, ""))) {
         loaded.set(berserkerEntityPath, defaults.getString(berserkerEntityPath, "ZOMBIFIED_PIGLIN"));
         loaded.setComments(berserkerEntityPath, defaults.getComments(berserkerEntityPath));
         settingsChanged = true;
         this.plugin.getLogger().info("[Army] Migrated berserker entity to ZOMBIFIED_PIGLIN to prevent transformation.");
      }

      if (loaded.getStringList("classes.archer.skills").equals(List.of("RANGED_ATTACK"))) {
         loaded.set("classes.archer.skills", defaults.getStringList("classes.archer.skills"));
         settingsChanged = true;
      }

      if (loaded.getStringList("classes.archer.skills").equals(List.of("ATTACK", "RANGED_ATTACK", "BUFF_OWNER"))) {
         loaded.set("classes.archer.skills", defaults.getStringList("classes.archer.skills"));
         settingsChanged = true;
      }

      if (!loaded.getBoolean("_migrations.guardian-six-skills-v1", false)) {
         String guardianSkillsPath = "classes.guardian.skills";
         loaded.set(guardianSkillsPath, defaults.getStringList(guardianSkillsPath));
         loaded.setComments(guardianSkillsPath, defaults.getComments(guardianSkillsPath));

         for (String legacySkill : List.of("SHIELD_OWNER", "HEAL_OWNER", "POWER_STRIKE", "TAUNT")) {
            loaded.set("classes.guardian.skill-settings." + legacySkill, null);
         }

         String ultimateCooldownPath = "classes.guardian.skill-settings.GUARDIAN_ULTIMATE.cooldown-seconds";
         if (loaded.getInt(ultimateCooldownPath, 90) == 8) {
            loaded.set(ultimateCooldownPath, 90);
            loaded.setComments(ultimateCooldownPath, defaults.getComments(ultimateCooldownPath));
         }

         loaded.set("classes.guardian.skill-settings.GUARDIAN_ULTIMATE.radius-blocks", null);
         loaded.set("classes.guardian.skill-settings.GUARDIAN_ULTIMATE.damage-reduction", null);
         String guardianDescriptionPath = "classes.guardian.description";
         if ("Chiến đấu cận chiến và bảo vệ chủ nhân.".equals(loaded.getString(guardianDescriptionPath))) {
            loaded.set(guardianDescriptionPath, defaults.getString(guardianDescriptionPath));
            loaded.setComments(guardianDescriptionPath, defaults.getComments(guardianDescriptionPath));
         }

         loaded.set("_migrations.guardian-six-skills-v1", true);
         settingsChanged = true;
         this.plugin.getLogger().info("[Army] Migrated the Guardian to its six tank skills.");
      }

      if (!loaded.getBoolean("_migrations.guardian-balance-v2", false)) {
         String swordMultiplierPath = "classes.guardian.sword-damage-multiplier";
         if (Double.compare(loaded.getDouble(swordMultiplierPath, 1.5), 1.5) == 0) {
            loaded.set(swordMultiplierPath, 1.1);
            loaded.setComments(swordMultiplierPath, defaults.getComments(swordMultiplierPath));
         }

         String redirectedDamagePath = "classes.guardian.skill-settings.GUARDIAN_LINK.redirected-damage-fraction";
         if (Double.compare(loaded.getDouble(redirectedDamagePath, 0.2), 0.2) == 0) {
            loaded.set(redirectedDamagePath, 0.4);
            loaded.setComments(redirectedDamagePath, defaults.getComments(redirectedDamagePath));
         }

         String chargeRangePath = "classes.guardian.skill-settings.GUARDIAN_CHARGE.maximum-range-blocks";
         if (Double.compare(loaded.getDouble(chargeRangePath, 12.0), 12.0) == 0) {
            loaded.set(chargeRangePath, 8.0);
            loaded.setComments(chargeRangePath, defaults.getComments(chargeRangePath));
         }

         loaded.set("_migrations.guardian-balance-v2", true);
         settingsChanged = true;
         this.plugin.getLogger().info("[Army] Updated Guardian weapon damage, owner protection, and charge defaults.");
      }

      if (loaded.getStringList("classes.guardian.skills").equals(List.of("ATTACK", "SHIELD_OWNER"))) {
         loaded.set("classes.guardian.skills", defaults.getStringList("classes.guardian.skills"));
         settingsChanged = true;
      }

      if (loaded.getStringList("classes.guardian.skills").equals(List.of("ATTACK", "SHIELD_OWNER", "HEAL_OWNER"))) {
         loaded.set("classes.guardian.skills", defaults.getStringList("classes.guardian.skills"));
         settingsChanged = true;
      }

      if (loaded.getStringList("classes.medic.skills").equals(List.of("HEAL_OWNER", "HEAL_ALLIES", "BUFF_OWNER", "BUFF_ALLIES"))) {
         loaded.set("classes.medic.skills", defaults.getStringList("classes.medic.skills"));
         settingsChanged = true;
      }

      if (loaded.getStringList("classes.frost_mage.skills").equals(List.of("RANGED_ATTACK", "VOLLEY", "ROOT_TARGET", "HEAL_OWNER"))) {
         String frostSkillsPath = "classes.frost_mage.skills";
         loaded.set(frostSkillsPath, defaults.getStringList(frostSkillsPath));
         loaded.setComments(frostSkillsPath, defaults.getComments(frostSkillsPath));
         loaded.set("classes.frost_mage.starting-weapon", defaults.getString("classes.frost_mage.starting-weapon", "SNOWBALL"));
         loaded.setComments("classes.frost_mage.starting-weapon", defaults.getComments("classes.frost_mage.starting-weapon"));
         if ("Bắn loạt băng, làm chậm quái và hồi máu cho chủ nhân.".equals(loaded.getString("classes.frost_mage.description"))) {
            loaded.set("classes.frost_mage.description", defaults.getString("classes.frost_mage.description"));
            loaded.setComments("classes.frost_mage.description", defaults.getComments("classes.frost_mage.description"));
         }

         settingsChanged = true;
      }

      String vexMaximumPath = "classes.druid.skill-settings.SUMMON_VEXES.max-active";
      int oldVexMaximum = loaded.getInt(vexMaximumPath, 7);
      if (oldVexMaximum == 2 || oldVexMaximum == 4) {
         loaded.set(vexMaximumPath, 7);
         loaded.setComments(vexMaximumPath, defaults.getComments(vexMaximumPath));
         settingsChanged = true;
      }

      String druidNamePath = "classes.druid.display-name";
      if ("Druid".equals(loaded.getString(druidNamePath))) {
         loaded.set(druidNamePath, defaults.getString(druidNamePath, "Tu sĩ rừng"));
         loaded.setComments(druidNamePath, defaults.getComments(druidNamePath));
         settingsChanged = true;
      }

      String berserkerNamePath = "classes.berserker.display-name";
      if ("Cuồng Chiến".equals(loaded.getString(berserkerNamePath))) {
         loaded.set(berserkerNamePath, defaults.getString(berserkerNamePath, "Chiến binh"));
         loaded.setComments(berserkerNamePath, defaults.getComments(berserkerNamePath));
         settingsChanged = true;
      }

      String skillPowerPath = "progression.skill-power-per-level";
      if (Math.abs(loaded.getDouble(skillPowerPath, 0.05) - 0.02) < 1.0E-6) {
         loaded.set(skillPowerPath, 0.05);
         loaded.setComments(skillPowerPath, defaults.getComments(skillPowerPath));
         settingsChanged = true;
      }

      if (settingsChanged) {
         loaded.save(this.configFile);
      }
   }

    private void migrateRangerDefault(YamlConfiguration loaded, YamlConfiguration defaults, String path,
                                     double previous, double updated) {
        if (loaded.contains(path) && Double.compare(loaded.getDouble(path), previous) == 0) {
            loaded.set(path, updated);
            loaded.setComments(path, defaults.getComments(path));
        }
    }

   private boolean mergeSettingsSection(YamlConfiguration target, ConfigurationSection defaults, String prefix) throws InvalidConfigurationException {
      boolean changed = false;

      for (String key : defaults.getKeys(false)) {
         String path = prefix.isEmpty() ? key : prefix + "." + key;
         ConfigurationSection defaultChild = defaults.getConfigurationSection(key);
         if (defaultChild != null) {
            if (target.contains(path) && !target.isConfigurationSection(path)) {
               throw new InvalidConfigurationException("Expected a configuration section at '" + path + "'.");
            }

            if (!target.isConfigurationSection(path)) {
               target.createSection(path);
               target.setComments(path, defaults.getComments(key));
               changed = true;
            }

            changed |= this.mergeSettingsSection(target, defaultChild, path);
         } else if (!target.contains(path)) {
            target.set(path, defaults.get(key));
            target.setComments(path, defaults.getComments(key));
            changed = true;
         }
      }

      return changed;
   }

   private void loadData() {
      if (!this.dataFile.isFile()) {
         this.data = new YamlConfiguration();
         this.data.createSection("units");
         if (!this.saveData()) {
            throw new IllegalStateException("Could not create mercenaries.yml.");
         }
      } else {
         YamlConfiguration loaded = new YamlConfiguration();

         try {
            loaded.load(this.dataFile);
         } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalStateException("Could not load mercenaries.yml; refusing to overwrite stored army data.", exception);
         }

         ConfigurationSection section = loaded.getConfigurationSection("units");
         boolean migratedGear = false;
         if (section != null) {
            for (String idText : section.getKeys(false)) {
               try {
                  UUID id = UUID.fromString(idText);
                  UUID owner = UUID.fromString(loaded.getString("units." + idText + ".owner", ""));
                  String classId = this.normalizeClass(loaded.getString("units." + idText + ".class", ""));
                  boolean hasSeparateTier = loaded.contains("units." + idText + ".tier");
                  int tier = loaded.getInt("units." + idText + ".tier", loaded.getInt("units." + idText + ".level", 1));
                  int originalTier = loaded.getInt("units." + idText + ".original-tier", tier);
                  if (classId != null && originalTier >= 1 && originalTier <= 10 && tier >= originalTier && tier <= originalTier + 3 && tier <= 13) {
                     MercenaryManager.Unit unit = new MercenaryManager.Unit(
                        id,
                        owner,
                        classId,
                        tier,
                        parseMode(loaded.getString("units." + idText + ".mode", "PROTECT")),
                        loaded.getBoolean("units." + idText + ".deployed", false)
                     );
                     unit.originalTier = originalTier;
                     unit.level = hasSeparateTier ? Math.max(1, Math.min(this.maxLevel, loaded.getInt("units." + idText + ".level", 1))) : 1;
                     unit.experience = Math.max(0, loaded.getInt("units." + idText + ".experience", 0));
                     unit.customName = loaded.getString("units." + idText + ".custom-name", "");
                     unit.recoveryUntilMillis = Math.max(0L, loaded.getLong("units." + idText + ".recovery-until", 0L));
                     unit.gearCustomized = loaded.getBoolean("units." + idText + ".gear-customized", false);
                     unit.horseDead = loaded.getBoolean("units." + idText + ".horse-dead", false);
                     unit.entityId = parseUuid(loaded.getString("units." + idText + ".entity-uuid", ""));
                     unit.muted = loaded.getBoolean("units." + idText + ".muted", false);
                     unit.worldName = loaded.getString("units." + idText + ".world", "");
                     unit.x = loaded.getDouble("units." + idText + ".x");
                     unit.y = loaded.getDouble("units." + idText + ".y");
                     unit.z = loaded.getDouble("units." + idText + ".z");
                     ItemStack horseArmor = loaded.getItemStack("units." + idText + ".horse-armor");
                     if (this.isHorseArmor(horseArmor)) {
                        unit.horseArmor = horseArmor.clone();
                     }

                     for (EquipmentSlot slot : EquipmentSlot.values()) {
                        String path = "units." + idText + ".equipment." + slotPath(slot);
                        ItemStack item = loaded.getItemStack(path);
                        if (item != null && !item.getType().isAir()) {
                           unit.equipment.put(slot, item.clone());
                        }
                     }

                     this.clearLegacyGeneratedMainHand(unit);

                     for (int slot = 0; slot < unit.inventory.length; slot++) {
                        ItemStack item = loaded.getItemStack("units." + idText + ".inventory." + slot);
                        if (item != null && !item.getType().isAir()) {
                           unit.inventory[slot] = item.clone();
                        }
                     }

                     migratedGear |= this.sanitizeUnitGear(unit);
                     this.units.put(id, unit);
                  } else {
                     this.plugin.getLogger().warning("[Army] Skipping invalid mercenary record " + idText + ".");
                  }
               } catch (IllegalArgumentException exception) {
                  this.plugin.getLogger().warning("[Army] Skipping malformed mercenary data at units." + idText + ".");
               }
            }
         }

         this.data = loaded;
         if (migratedGear && !this.saveData()) {
            this.plugin.getLogger().severe("[Army] Legacy non-soldier equipment was moved to storage in memory but could not be persisted to mercenaries.yml.");
         }
      }
   }

   private boolean sanitizeUnitGear(MercenaryManager.Unit unit) {
      boolean moved = false;

      for (EquipmentSlot slot : EQUIPMENT_SLOTS) {
         ItemStack item = unit.equipment.get(slot);
         if (item != null && !item.getType().isAir() && !this.validSoldierGear(item, slot) && this.storeInUnitInventory(unit, item)) {
            unit.equipment.remove(slot);
            moved = true;
         }
      }

      if (unit.horseArmor != null && !unit.horseArmor.getType().isAir() && this.storeInUnitInventory(unit, unit.horseArmor)) {
         unit.horseArmor = null;
         moved = true;
      }

      return moved;
   }

    private void clearLegacyGeneratedMainHand(Unit unit) {
        if (unit.gearCustomized) return;
        Set<String> previousDefaults = switch (unit.unitClass) {
            case "guardian", "assassin" -> Set.of("IRON_SWORD");
            case "medic" -> Set.of("GOLDEN_HOE");
            case "paladin" -> Set.of("TRIDENT", "IRON_SPEAR");
            case "berserker" -> Set.of("IRON_AXE");
            case "druid" -> Set.of("STICK");
            case "frost_mage" -> Set.of("SNOWBALL");
            case "pyromancer" -> Set.of("BLAZE_ROD", "FIRE_CHARGE");
            default -> Set.of();
        };
        ItemStack saved = unit.equipment.get(org.bukkit.inventory.EquipmentSlot.HAND);
        if (saved != null && previousDefaults.contains(saved.getType().name())) {
            unit.equipment.remove(org.bukkit.inventory.EquipmentSlot.HAND);
        }
    }

    private void startAiTask() {
        aiTask = new BukkitRunnable() {
            private int saveCounter;

            @Override
            public void run() {
                if (!enabled) return;
                guardianOwnerImmunityExpiries.entrySet().removeIf(
                    entry -> entry.getValue() <= System.currentTimeMillis());
                cleanupFrostPotionEffects(false);
                cleanupFireExpiries(false);
                for (Unit unit : new ArrayList<>(units.values())) {
                    if (!unit.deployed) {
                        cleanupFireStateForUnit(unit.id);
                        cleanupGuardianStateForUnit(unit.id);
                        continue;
                    }
                    Player owner = Bukkit.getPlayer(unit.owner);
                    Mob mob = activeEntities.get(unit.id);
                    if (mob != null && mob.isValid()) preventZombification(mob);
                    if (owner == null || !owner.isOnline()) {
                        cleanupFireStateForUnit(unit.id);
                        cleanupGuardianStateForUnit(unit.id);
                        cleanupSummonedMinions(unit.id);
                        if (mob != null) {
                            captureEquipment(unit, mob);
                            updateLocation(unit, mob.getLocation());
                            removeMount(unit.id, mob);
                            mob.remove();
                            activeEntities.remove(unit.id);
                            unit.entityId = null;
                        } else {
                            removeMount(unit.id, null);
                        }
                        continue;
                    }
                    if (isGearOpen(owner, unit.id)) continue;
                    if (mob == null || !mob.isValid() || mob.isDead()) {
                        mob = findExistingEntity(unit);
                        if (mob == null) mob = spawnUnit(unit, owner.getLocation());
                        if (mob == null) {
                            owner.sendMessage("§cKhông thể triệu hồi lính §e#" + shortId(unit.id) + "§c; kiểm tra cấu hình entity.");
                            unit.deployed = false;
                            continue;
                        }
                        activeEntities.put(unit.id, mob);
                    }
                    EntityType configuredType = configuredEntityType(unit);
                    if (configuredType != null && mob.getType() != configuredType) {
                        captureEquipment(unit, mob);
                        updateLocation(unit, mob.getLocation());
                        if (!saveData()) {
                            plugin.getLogger().severe("[Army] Could not save gear before replacing entity "
                                + "for mercenary " + unit.id + "; keeping the current entity.");
                            continue;
                        }
                        Mob replacement = spawnUnit(unit, mob.getLocation());
                        if (replacement == null) {
                            plugin.getLogger().severe("[Army] Could not replace outdated entity for mercenary "
                                + unit.id + "; keeping the current entity.");
                            unit.entityId = mob.getUniqueId();
                            continue;
                        }
                        mob.remove();
                        mob = replacement;
                        activeEntities.put(unit.id, mob);
                    }
                    mob.setSilent(unit.muted);
                    removeUnskilledVexes(mob);
                    ensurePaladinMounted(unit, mob);
                    updateStatusDisplay(unit, mob);
                    updateLocation(unit, mob.getLocation());
                    runUnitAi(unit, mob, owner);
                }
                collectExperienceOrbs();
                if (++saveCounter >= Math.max(1, 100 / aiIntervalTicks)) {
                    saveCounter = 0;
                    for (Unit unit : units.values()) {
                        Mob active = activeEntities.get(unit.id);
                        if (active != null && active.isValid()) {
                            captureEquipment(unit, active);
                            updateLocation(unit, active.getLocation());
                        }
                    }
                    saveData();
                }
            }
        };
        aiTask.runTaskTimer(plugin, aiIntervalTicks, aiIntervalTicks);
    }

    private void runUnitAi(Unit unit, Mob mob, Player owner) {
        mob.setAware(true);
        if (!owner.getWorld().equals(mob.getWorld())) {
            Location spawn = safeLocation(owner.getLocation());
            teleportUnit(unit, mob, spawn);
            wanderTargets.remove(unit.id);
            wanderTargetExpiries.remove(unit.id);
            updateSummonedMinions(unit, null);
            return;
        }
        double distanceSquared = owner.getLocation().distanceSquared(mob.getLocation());
        if (distanceSquared > teleportDistance * teleportDistance) {
            teleportUnit(unit, mob, owner.getLocation().clone().add(0.8, 0.0, 0.8));
            mob.setTarget(null);
            wanderTargets.remove(unit.id);
            wanderTargetExpiries.remove(unit.id);
            updateSummonedMinions(unit, null);
            return;
        }
        LivingEntity target = findTarget(unit, mob, owner);
        if (target == null && unit.mode == Mode.PROTECT && hasAiPriority(unit, "FOLLOW_OWNER")
                && distanceSquared > followDistance * followDistance) {
            mob.setTarget(null);
            mob.getPathfinder().moveTo(owner.getLocation(), 1.15);
            useSkills(unit, owner, null);
            updateSummonedMinions(unit, null);
            feedMercenary(unit, mob, owner);
            return;
        }

        if (target != null) {
            mob.setAware(true);
            mob.setSilent(unit.muted);
            if ("frost_mage".equals(unit.unitClass)) {
                mob.setTarget(null);
                double range = frostRange(unit, "BASIC_ATTACK", "range-blocks", 5.0);
                if (mob.getLocation().distanceSquared(target.getLocation()) > range * range
                        || !mob.hasLineOfSight(target)) {
                    mob.getPathfinder().moveTo(target, 1.0);
                } else {
                    mob.getPathfinder().stopPathfinding();
                }
            } else if (hasSkill(unit, "RANGED_ATTACK") && !hasSkill(unit, "ATTACK")) {
                mob.setTarget(null);
                double range = preferredRangedPositionRange(unit, mob);
                if (mob.getLocation().distanceSquared(target.getLocation()) > range * range
                        || !mob.hasLineOfSight(target)) {
                    mob.getPathfinder().moveTo(target, 1.0);
                } else {
                    mob.getPathfinder().stopPathfinding();
                }
            } else if (mob.getTarget() != target) {
                mob.setTarget(target);
                if (hasSkill(unit, "ATTACK") && ready(unit, "attack")) {
                    markUsed(unit, "attack", skillCooldownSeconds(unit, "ATTACK"));
                    announceSkillCast(unit, owner, "ATTACK", Particle.CRIT);
                }
            }
        } else {
            mob.setTarget(null);
            if (unit.mode == Mode.ATTACK && distanceSquared > followDistance * followDistance
                    && hasAiPriority(unit, "RETURN_TO_OWNER")) {
                mob.getPathfinder().moveTo(owner.getLocation(), 1.15);
            } else if (hasAiPriority(unit, "WANDER")) {
                Location center = unit.mode == Mode.ATTACK ? mob.getLocation() : owner.getLocation();
                moveToWanderTarget(unit, mob, center);
            } else {
                mob.getPathfinder().stopPathfinding();
            }
        }
        useSkills(unit, owner, target);
        updateSummonedMinions(unit, target);
        feedMercenary(unit, mob, owner);
    }

    private double preferredRangedPositionRange(Unit unit, Mob mob) {
        double range = skillOptionDouble(unit, "RANGED_ATTACK", "range-blocks", 16.0, 4.0, 64.0);
        if (!"ranger".equals(unit.unitClass)) return range;
        if (hasSkill(unit, "VOLLEY") && ready(unit, "volley")) {
            range = Math.min(range, skillOptionDouble(unit, "VOLLEY", "range-blocks", range, 4.0, 64.0));
        }
        if (hasSkill(unit, "ILLUMINATION") && ready(unit, "illumination")
                && canUseIlluminationInCurrentLight(mob)) {
            range = Math.min(range,
                skillOptionDouble(unit, "ILLUMINATION", "range-blocks", range, 4.0, 64.0));
        }
        return range;
    }

    private void feedMercenary(Unit unit, Mob mob, Player owner) {
        double maximum = maxHealth(mob);
        if (maximum <= 0.0 || mob.getHealth() >= maximum || foodHealAmount <= 0.0) return;
        for (int slot = 0; slot < unit.inventory.length; slot++) {
            ItemStack food = unit.inventory[slot];
            if (food == null || !food.getType().isEdible()) continue;
            double oldHealth = mob.getHealth();
            mob.setHealth(Math.min(maximum, oldHealth + foodHealAmount));
            if (food.getAmount() <= 1) unit.inventory[slot] = null;
            else {
                ItemStack remainder = food.clone();
                remainder.setAmount(food.getAmount() - 1);
                unit.inventory[slot] = remainder;
            }
            mob.getWorld().spawnParticle(Particle.HEART, mob.getLocation().add(0, 1, 0),
                6, 0.3, 0.35, 0.3, 0.02);
            mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.8f, 1.0f);
            owner.sendActionBar("§a" + displayName(unit) + " §eđã dùng thức ăn để hồi máu");
            break;
        }
    }

    private void collectExperienceOrbs() {
        if (!experienceOrbPickupEnabled) return;
        Map<UUID, org.bukkit.entity.ExperienceOrb> nearbyOrbs = new HashMap<>();
        Map<UUID, UUID> orbOwners = new HashMap<>();
        Map<UUID, Double> closestDistances = new HashMap<>();
        double radiusSquared = experienceOrbPickupRadius * experienceOrbPickupRadius;
        for (Unit unit : units.values()) {
            if (!unit.deployed || unit.recoveryUntilMillis > System.currentTimeMillis()) continue;
            Player owner = Bukkit.getPlayer(unit.owner);
            Mob mob = activeEntities.get(unit.id);
            if (owner == null || !owner.isOnline() || mob == null || !mob.isValid()
                    || !owner.getWorld().equals(mob.getWorld())) continue;
            for (Entity entity : mob.getNearbyEntities(experienceOrbPickupRadius,
                    experienceOrbPickupRadius, experienceOrbPickupRadius)) {
                if (!(entity instanceof org.bukkit.entity.ExperienceOrb orb)
                        || !orb.isValid() || orb.isDead()) continue;
                double distance = mob.getLocation().distanceSquared(orb.getLocation());
                if (distance > radiusSquared || distance >= closestDistances.getOrDefault(
                        orb.getUniqueId(), Double.MAX_VALUE)) continue;
                nearbyOrbs.put(orb.getUniqueId(), orb);
                orbOwners.put(orb.getUniqueId(), unit.owner);
                closestDistances.put(orb.getUniqueId(), distance);
            }
        }
        for (Map.Entry<UUID, org.bukkit.entity.ExperienceOrb> entry : nearbyOrbs.entrySet()) {
            org.bukkit.entity.ExperienceOrb orb = entry.getValue();
            Player owner = Bukkit.getPlayer(orbOwners.get(entry.getKey()));
            if (owner == null || !owner.isOnline() || !orb.isValid() || orb.isDead()) continue;
            int experience = orb.getExperience();
            if (experience <= 0) continue;
            owner.giveExp(experience);
            orb.remove();
        }
    }

    private LivingEntity findTarget(Unit unit, Mob mob, Player owner) {
        if (!hasSkill(unit, "ATTACK") && !hasSkill(unit, "RANGED_ATTACK")
            && !hasSkill(unit, "FIRE_BOLT") && !hasSkill(unit, "ROOT_TARGET")
            && FROST_MAGE_SKILLS.stream().noneMatch(skill -> hasSkill(unit, skill))
            && FIRE_MAGE_SKILLS.stream().noneMatch(skill -> hasSkill(unit, skill))) return null;
        double classRadius = readBoundedDouble(settings, "classes." + unit.unitClass + ".target-radius-blocks",
            targetRadius, 4.0, 50.0);
        Location center = unit.mode == Mode.ATTACK ? mob.getLocation() : owner.getLocation();
        for (String action : aiPriorities(unit)) {
            LivingEntity target = switch (action) {
                case "OWNER_ATTACK_TARGET" -> unit.mode == Mode.DEFEND ? null : ownerAttackTarget(unit, mob, owner);
                case "OWNER_ATTACKER" -> unit.mode == Mode.PROTECT ? findOwnerAttacker(mob, owner) : null;
                case "OWNER_THREAT" -> unit.mode == Mode.DEFEND ? null : findOwnerThreatTarget(unit.owner, mob);
                case "NEAREST_HOSTILE" -> findNearestTarget(unit, mob, center, classRadius);
                case "SEARCH" -> findNearestTarget(unit, mob, center,
                    "ranger".equals(unit.unitClass) ? classRadius : targetRadius);
                default -> null;
            };
            if (target != null
                    && (!requireLineOfSight || mob.hasLineOfSight(target))
                    && !isHiddenUndergroundTarget(mob, target)) return target;
        }
        return null;
    }

    private LivingEntity ownerAttackTarget(Unit unit, Mob mob, Player owner) {
        UUID targetId = ownerAttackTargets.get(owner.getUniqueId());
        if (targetId == null || !isActiveOwnerAttackTarget(owner.getUniqueId(), targetId)) return null;
        Entity entity = Bukkit.getEntity(targetId);
        if (entity instanceof LivingEntity target && isAllowedMercenaryTarget(unit.owner, target, true)
                && mob.getWorld().equals(target.getWorld())) return target;
        if (targetId != null && (entity == null || entity.isDead())) ownerAttackTargets.remove(owner.getUniqueId());
        return null;
    }

    private boolean isActiveOwnerAttackTarget(UUID ownerId, UUID targetId) {
        Long expiresAt = ownerAttackTargetExpiries.get(ownerId);
        if (expiresAt == null || System.currentTimeMillis() >= expiresAt) {
            ownerAttackTargets.remove(ownerId);
            ownerAttackTargetExpiries.remove(ownerId);
            return false;
        }
        return targetId.equals(ownerAttackTargets.get(ownerId));
    }

    private LivingEntity findOwnerAttacker(Mob mob, Player owner) {
        return mob.getWorld().getNearbyEntities(owner.getLocation(), targetRadius, targetRadius, targetRadius)
            .stream()
            .filter(entity -> entity instanceof LivingEntity)
            .map(entity -> (LivingEntity) entity)
            .filter(this::isHostileMob)
            .filter(entity -> {
                if (entity instanceof Mob attacker) {
                    return attacker.getTarget() != null
                        && attacker.getTarget().getUniqueId().equals(owner.getUniqueId());
                }
                if (entity instanceof org.bukkit.entity.EnderDragon dragon) {
                    return dragon.getTarget() != null
                        && dragon.getTarget().getUniqueId().equals(owner.getUniqueId());
                }
                return false;
            })
            .min(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(owner.getLocation())))
            .orElse(null);
    }

    private LivingEntity findOwnerThreatTarget(UUID ownerId, Mob mob) {
        Map<UUID, Long> threats = ownerThreats.get(ownerId);
        if (threats == null) return null;
        long now = System.currentTimeMillis();
        threats.entrySet().removeIf(entry -> now - entry.getValue() > 15_000L);
        for (Map.Entry<UUID, Long> threat : threats.entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed()).toList()) {
            Entity entity = Bukkit.getEntity(threat.getKey());
            if (entity instanceof LivingEntity target && !target.isDead()
                    && mob.getWorld().equals(target.getWorld())
                    && isAllowedMercenaryTarget(ownerId, target, false)) return target;
        }
        return null;
    }

    private LivingEntity findNearestTarget(Unit unit, Mob mob, Location center, double radius) {
        return mob.getWorld().getNearbyEntities(center, radius, radius, radius).stream()
            .filter(entity -> entity instanceof LivingEntity)
            .map(entity -> (LivingEntity) entity)
            .filter(living -> !living.equals(mob))
            .filter(living -> living.getLocation().distanceSquared(center) <= radius * radius)
            .filter(living -> isAllowedMercenaryTarget(unit.owner, living, false))
            .filter(living -> !requireLineOfSight || mob.hasLineOfSight(living))
            .filter(living -> !isHiddenUndergroundTarget(mob, living))
            .min(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(center)))
            .orElse(null);
    }

    private boolean isHiddenUndergroundTarget(Mob mob, LivingEntity target) {
        if (!settings.getBoolean("ai.ignore-hidden-underground-targets", true)
                || mob.hasLineOfSight(target) || !mob.getWorld().equals(target.getWorld())) return false;
        Location targetLocation = target.getLocation();
        int terrainHeight = target.getWorld().getHighestBlockYAt(targetLocation);
        return targetLocation.getBlockY() <= terrainHeight;
    }

    private List<String> aiPriorities(Unit unit) {
        return settings.getStringList("ai.priorities." + unit.mode.name().toLowerCase(Locale.ROOT)).stream()
            .map(value -> value.toUpperCase(Locale.ROOT)).toList();
    }

    private boolean hasAiPriority(Unit unit, String action) {
        return aiPriorities(unit).contains(action);
    }

    private void moveToWanderTarget(Unit unit, Mob mob, Location center) {
        long now = System.currentTimeMillis();
        Location destination = wanderTargets.get(unit.id);
        if (destination == null || !destination.getWorld().equals(center.getWorld())
                || now >= wanderTargetExpiries.getOrDefault(unit.id, 0L)
                || mob.getLocation().distanceSquared(destination) < 2.25) {
            double angle = java.util.concurrent.ThreadLocalRandom.current().nextDouble(Math.PI * 2.0);
            double radius = java.util.concurrent.ThreadLocalRandom.current().nextDouble(1.0, wanderRadius + 1.0);
            destination = center.clone().add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
            wanderTargets.put(unit.id, destination);
            wanderTargetExpiries.put(unit.id, now + 8_000L);
        }
        mob.getPathfinder().moveTo(destination, 0.8);
    }

    private void updateSummonedMinions(Unit unit, LivingEntity target) {
        Set<UUID> minionIds = summonedMinions.get(unit.id);
        if (minionIds == null) return;
        Mob druid = activeEntities.get(unit.id);
        Player owner = Bukkit.getPlayer(unit.owner);
        for (UUID minionId : List.copyOf(minionIds)) {
            Entity entity = Bukkit.getEntity(minionId);
            if (!(entity instanceof Vex vex) || !vex.isValid() || vex.isDead()) {
                removeStatusDisplay(minionId);
                continue;
            }
            if (druid != null && druid.isValid() && owner != null && owner.isOnline()) {
                LivingEntity selectedTarget = selectVexTarget(unit, druid, owner, vex, target);
                if (selectedTarget != null) {
                    if (vex.getTarget() != selectedTarget) vex.setTarget(selectedTarget);
                } else {
                    if (vex.getTarget() != null) vex.setTarget(null);
                    if (!vex.getWorld().equals(druid.getWorld())) {
                        removeStatusDisplay(vex.getUniqueId());
                        vex.remove();
                        minionIds.remove(minionId);
                        continue;
                    }
                    double returnRadius = skillOptionDouble(
                        unit, "SUMMON_VEXES", "follow-radius-blocks", 5.0, 2.0, 16.0);
                    if (vex.getLocation().distanceSquared(druid.getLocation()) > returnRadius * returnRadius) {
                        vex.getPathfinder().moveTo(druid.getLocation(), 0.6);
                    } else {
                        vex.getPathfinder().stopPathfinding();
                    }
                }
            }
            updateVexStatusDisplay(vex, unit.owner, vexActionIcon(vex, druid));
        }
    }

    private LivingEntity selectVexTarget(Unit unit, Mob druid, Player owner, Vex vex,
                                         LivingEntity druidTarget) {
        List<String> priorities = settings.getStringList(
            "classes.druid.skill-settings.SUMMON_VEXES.ai-priorities").stream()
            .map(value -> value.toUpperCase(Locale.ROOT)).toList();
        if (priorities.isEmpty()) {
            priorities = List.of("DRUID_TARGET", "DRUID_ATTACKER", "OWNER_ATTACKER", "FOLLOW_DRUID");
        }
        for (String priority : priorities) {
            if ("FOLLOW_DRUID".equals(priority)) return null;
            LivingEntity candidate = switch (priority) {
                case "DRUID_TARGET" -> druidTarget != null ? druidTarget : druid.getTarget();
                case "DRUID_ATTACKER" -> findRecentThreatTarget(vex, unitThreats.get(unit.id));
                case "OWNER_ATTACKER" -> {
                    LivingEntity recentAttacker = findRecentThreatTarget(vex, ownerAttackers.get(unit.owner));
                    yield recentAttacker != null ? recentAttacker : findOwnerAttacker(vex, owner);
                }
                default -> null;
            };
            if (candidate != null && canMinionTarget(vex, candidate)) return candidate;
        }
        return null;
    }

    private LivingEntity findRecentThreatTarget(Mob mob, Map<UUID, Long> threats) {
        if (threats == null) return null;
        long now = System.currentTimeMillis();
        threats.entrySet().removeIf(entry -> now - entry.getValue() > 15_000L);
        for (Map.Entry<UUID, Long> threat : threats.entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed()).toList()) {
            Entity entity = Bukkit.getEntity(threat.getKey());
            if (entity instanceof LivingEntity target && !target.isDead()
                    && canMinionTarget(mob, target)) return target;
        }
        return null;
    }

    private String vexActionIcon(Vex vex, Mob druid) {
        LivingEntity target = vex.getTarget();
        if (target != null && canMinionTarget(vex, target)) return vexAttackIcon;
        if (druid != null && druid.isValid()
                && (!vex.getWorld().equals(druid.getWorld())
                    || vex.getLocation().distanceSquared(druid.getLocation()) > 16.0)) {
            return vexFollowIcon;
        }
        return vexIdleIcon;
    }

    private void updateVexStatusDisplay(Vex vex, UUID ownerId, String actionIcon) {
        if (!statusDisplayEnabled) {
            removeStatusDisplay(vex.getUniqueId());
            return;
        }
        double maximumHealth = vex.getAttribute(Attribute.MAX_HEALTH) == null
            ? vex.getHealth() : vex.getAttribute(Attribute.MAX_HEALTH).getValue();
        int filledBars = statusBarFilled(vex.getHealth(), maximumHealth);
        String healthBar = "§e" + actionIcon + " " + vexHealthColor
            + "■".repeat(filledBars)
            + "§7" + "□".repeat(statusDisplayBarLength - filledBars);
        statusPacketListener.update(vex.getUniqueId(), vex, ownerId, healthBar, statusDisplaySeeThrough);
    }

   private ChatColor readVexHealthColor(YamlConfiguration loaded) throws InvalidConfigurationException {
      String configuredColor = loaded.getString("status-display.vex-health-color", "GREEN");
      if (configuredColor != null && !configuredColor.isBlank()) {
         try {
            ChatColor color = ChatColor.valueOf(configuredColor.toUpperCase(Locale.ROOT));
            if (!color.isColor()) {
               throw new IllegalArgumentException("Formatting colors are not allowed.");
            } else {
               return color;
            }
         } catch (IllegalArgumentException exception) {
            throw new InvalidConfigurationException("Unknown status-display.vex-health-color '" + configuredColor + "'. Use a ChatColor color name.");
         }
      } else {
         throw new InvalidConfigurationException("status-display.vex-health-color must be a valid ChatColor color name.");
      }
   }

   private String readVexActionIcon(YamlConfiguration loaded, String action, String defaultIcon) throws InvalidConfigurationException {
      String path = "status-display.vex-action-icons." + action;
      String icon = loaded.getString(path, defaultIcon);
      if (icon != null && !icon.isBlank() && icon.codePointCount(0, icon.length()) <= 4) {
         return icon;
      } else {
         throw new InvalidConfigurationException("Invalid " + path + "; use a non-empty symbol of at most 4 Unicode code points.");
      }
   }

    private void useGuardianSkills(Unit unit, Player owner, Mob guardian, LivingEntity target) {
        if (guardian == null || !guardian.isValid() || !owner.isOnline()
                || !guardian.getWorld().equals(owner.getWorld())) return;

        if (target != null && isHostileMob(target)) {
            double targetDistance = guardian.getLocation().distance(target.getLocation());
            double chargeMinimum = skillOptionDouble(unit, "GUARDIAN_CHARGE",
                "minimum-range-blocks", 4.0, 1.0, 16.0);
            double chargeMaximum = guardianChargeMaximumRange(unit);
            if (hasSkill(unit, "GUARDIAN_CHARGE") && targetDistance >= chargeMinimum
                    && targetDistance <= chargeMaximum && ready(unit, "GUARDIAN_CHARGE")) {
                startGuardianCharge(unit, owner, guardian, target);
            }
            double quakeRadius = skillOptionDouble(unit, "GUARDIAN_QUAKE",
                "radius-blocks", 3.0, 1.0, 8.0);
            if (hasSkill(unit, "GUARDIAN_QUAKE") && targetDistance <= quakeRadius + 1.0
                    && ready(unit, "GUARDIAN_QUAKE")) {
                activateGuardianQuake(unit, owner, target.getLocation());
            }
        }

        double shieldRadius = skillOptionDouble(unit, "GUARDIAN_SHIELD", "radius-blocks", 10.0, 1.0, 32.0);
        boolean underThreat = target != null || hasGuardianHostileNear(owner, shieldRadius);
        if (hasSkill(unit, "GUARDIAN_SHIELD") && underThreat && ready(unit, "GUARDIAN_SHIELD")) {
            activateGuardianShield(unit, owner, guardian);
        }

        double tauntRadius = skillOptionDouble(unit, "GUARDIAN_ULTIMATE",
            "taunt-radius-blocks", 8.0, 1.0, 32.0);
        double ownerHealthPercent = owner.getHealth() / Math.max(1.0, maxHealth(owner)) * 100.0;
        double ultimateThreshold = skillOptionDouble(unit, "GUARDIAN_ULTIMATE",
            "owner-health-threshold-percent", 50.0, 1.0, 99.0);
        if (hasSkill(unit, "GUARDIAN_ULTIMATE") && ownerHealthPercent <= ultimateThreshold
                && hasGuardianHostileNear(owner, tauntRadius) && ready(unit, "GUARDIAN_ULTIMATE")) {
            activateGuardianUltimate(unit, owner, guardian, tauntRadius);
        }
    }

    private boolean hasGuardianHostileNear(Player owner, double radius) {
        return owner.getNearbyEntities(radius, radius, radius).stream()
            .filter(LivingEntity.class::isInstance)
            .map(LivingEntity.class::cast)
            .anyMatch(this::isHostileMob);
    }

    private void startGuardianCharge(Unit unit, Player owner, Mob guardian, LivingEntity target) {
        Vector direction = target.getLocation().toVector().subtract(guardian.getLocation().toVector());
        direction.setY(0.0);
        if (direction.lengthSquared() <= 0.0001) return;
        direction.normalize();
        Location chargeOrigin = guardian.getLocation().clone();
        double maximumRange = guardianChargeMaximumRange(unit);
        double speed = skillOptionDouble(unit, "GUARDIAN_CHARGE", "speed", 1.5, 0.1, 4.0);
        int maximumTicks = (int) skillOptionDouble(unit, "GUARDIAN_CHARGE",
            "duration-ticks", 20.0, 1.0, 100.0);
        double sweepRadius = skillOptionDouble(unit, "GUARDIAN_CHARGE",
            "sweep-radius-blocks", 2.0, 1.0, 6.0);
        double horizontalKnockback = skillOptionDouble(unit, "GUARDIAN_CHARGE",
            "knockback-strength", 0.8, 0.0, 4.0);
        double verticalKnockback = skillOptionDouble(unit, "GUARDIAN_CHARGE",
            "knockback-y", 0.5, 0.0, 2.0);
        Set<UUID> hitTargets = new HashSet<>();
        guardian.setVelocity(direction.clone().multiply(speed));
        markUsed(unit, "GUARDIAN_CHARGE", skillCooldownSeconds(unit, "GUARDIAN_CHARGE"));
        announceSkillCast(unit, owner, "GUARDIAN_CHARGE", Particle.CRIT);

        BukkitRunnable chargeTask = new BukkitRunnable() {
            private int elapsedTicks;

            @Override
            public void run() {
                if (!guardian.isValid() || guardian.isDead() || !owner.isOnline()
                        || !target.isValid() || target.isDead() || !isHostileMob(target)
                        || !guardian.getWorld().equals(target.getWorld())
                        || !guardian.getWorld().equals(owner.getWorld())) {
                    finish();
                    return;
                }
                double traveled = guardian.getLocation().distance(chargeOrigin);
                double remainingRange = maximumRange - traveled;
                if (remainingRange <= 0.05) {
                    finish();
                    return;
                }
                Location nextLocation = guardian.getLocation().clone().add(direction.clone().multiply(0.9));
                if (nextLocation.getBlock().getType().isSolid()) {
                    finish();
                    return;
                }
                for (Entity nearby : guardian.getNearbyEntities(sweepRadius, 2.0, sweepRadius)) {
                    if (!(nearby instanceof LivingEntity hostile) || !isHostileMob(hostile)
                            || !hitTargets.add(hostile.getUniqueId())) continue;
                    Vector push = hostile.getLocation().toVector().subtract(guardian.getLocation().toVector());
                    push.setY(0.0);
                    if (push.lengthSquared() <= 0.0001) push = direction.clone();
                    else push.normalize();
                    hostile.setVelocity(push.multiply(horizontalKnockback).setY(verticalKnockback));
                }
                elapsedTicks++;
                if (elapsedTicks >= maximumTicks) {
                    finish();
                    return;
                }
                guardian.setVelocity(direction.clone().multiply(Math.min(speed, remainingRange)));
            }

            private void finish() {
                guardian.getWorld().spawnParticle(Particle.EXPLOSION,
                    guardian.getLocation().add(0.0, 0.8, 0.0), 1, 0.0, 0.0, 0.0, 0.0);
                cancel();
                forgetGuardianTask(unit.id, getTaskId());
            }
        };
        BukkitTask task = chargeTask.runTaskTimer(plugin, 1L, 1L);
        guardianTasksByUnit.computeIfAbsent(unit.id, ignored -> new HashSet<>()).add(task);
    }

    private double guardianChargeMaximumRange(Unit unit) {
        double baseRange = skillOptionDouble(unit, "GUARDIAN_CHARGE",
            "maximum-range-blocks", 8.0, 2.0, 32.0);
        double rangePerStep = skillOptionDouble(unit, "GUARDIAN_CHARGE",
            "maximum-range-per-level-step-blocks", 1.0, 0.0, 10.0);
        int levelsPerStep = readBoundedInt(settings, "classes.guardian.skill-settings.GUARDIAN_CHARGE."
            + "maximum-range-step-levels", 5, 1, 30);
        return Math.min(32.0, baseRange + Math.max(0, unit.level / levelsPerStep) * rangePerStep);
    }

    private void activateGuardianShield(Unit unit, Player owner, Mob guardian) {
        int durationTicks = (int) skillOptionDouble(unit, "GUARDIAN_SHIELD",
            "duration-seconds", 5.0, 1.0, 60.0) * 20;
        int resistanceAmplifier = (int) skillOptionDouble(unit, "GUARDIAN_SHIELD",
            "resistance-amplifier", 0.0, 0.0, 4.0);
        double shieldAmount = guardianAbsorptionAmount(unit);
        applyGuardianDefensiveEffects(guardian, shieldAmount, durationTicks, resistanceAmplifier);

        double radius = skillOptionDouble(unit, "GUARDIAN_SHIELD", "radius-blocks", 10.0, 1.0, 32.0);
        double allyRatio = skillOptionDouble(unit, "GUARDIAN_SHIELD",
            "ally-absorption-ratio", 0.5, 0.0, 1.0);
        for (LivingEntity recipient : guardianShieldRecipients(unit, owner, radius)) {
            applyGuardianDefensiveEffects(recipient, shieldAmount * allyRatio,
                durationTicks, resistanceAmplifier);
            recipient.getWorld().spawnParticle(Particle.WAX_ON,
                recipient.getLocation().add(0.0, 1.0, 0.0), 8, 0.35, 0.45, 0.35, 0.02);
        }
        markUsed(unit, "GUARDIAN_SHIELD", skillCooldownSeconds(unit, "GUARDIAN_SHIELD"));
        announceSkillCast(unit, owner, "GUARDIAN_SHIELD", Particle.WAX_ON);
    }

    private double guardianAbsorptionAmount(Unit unit) {
        GuardianStats stats = guardianStats(unit);
        double base = skillOptionDouble(unit, "GUARDIAN_SHIELD", "absorption-base", 6.0, 0.0, 40.0);
        double perLevel = skillOptionDouble(unit, "GUARDIAN_SHIELD", "absorption-per-level", 0.5, 0.0, 2.0);
        double perArmor = skillOptionDouble(unit, "GUARDIAN_SHIELD", "absorption-per-armor", 0.5, 0.0, 2.0);
        double offhandBonus = isGuardianHoldingOffhandShield(unit)
            ? skillOptionDouble(unit, "GUARDIAN_SHIELD", "shield-absorption-bonus", 4.0, 0.0, 20.0)
            : 0.0;
        return Math.min(40.0, Math.max(0.0,
            base + Math.max(0, unit.level - 1) * perLevel + stats.armor() * perArmor + offhandBonus));
    }

    private void applyGuardianDefensiveEffects(LivingEntity recipient, double absorption,
                                              int durationTicks, int resistanceAmplifier) {
        if (!recipient.isValid() || recipient.isDead()) return;
        int absorptionAmplifier = absorptionEffectAmplifier(absorption);
        if (absorptionAmplifier >= 0) {
            recipient.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,
                durationTicks, absorptionAmplifier, true, true, true));
        }
        recipient.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
            durationTicks, resistanceAmplifier, true, true, true));
    }

    private int absorptionEffectAmplifier(double absorptionAmount) {
        if (absorptionAmount <= 0.0) return -1;
        return Math.max(0, Math.min(9, (int) Math.ceil(absorptionAmount / 4.0) - 1));
    }

    private List<LivingEntity> guardianShieldRecipients(Unit unit, Player owner, double radius) {
        List<LivingEntity> recipients = new ArrayList<>();
        Set<UUID> added = new HashSet<>();
        double radiusSquared = radius * radius;
        if (added.add(owner.getUniqueId())) recipients.add(owner);
        org.bukkit.scoreboard.Team team = owner.getScoreboard().getEntryTeam(owner.getName());
        if (team != null) {
            for (Player teammate : Bukkit.getOnlinePlayers()) {
                if (teammate == owner || !teammate.getWorld().equals(owner.getWorld())
                        || !team.hasEntry(teammate.getName())
                        || teammate.getLocation().distanceSquared(owner.getLocation()) > radiusSquared) continue;
                if (added.add(teammate.getUniqueId())) recipients.add(teammate);
            }
        }
        for (Unit sibling : getOwnerUnits(unit.owner)) {
            if (sibling.id.equals(unit.id)) continue;
            Mob ally = activeEntities.get(sibling.id);
            if (!sibling.deployed || ally == null || !ally.isValid() || ally.isDead()
                    || !ally.getWorld().equals(owner.getWorld())
                    || ally.getLocation().distanceSquared(owner.getLocation()) > radiusSquared) continue;
            if (added.add(ally.getUniqueId())) recipients.add(ally);
        }
        return recipients;
    }

    private void activateGuardianQuake(Unit unit, Player owner, Location center) {
        Mob guardian = activeEntities.get(unit.id);
        if (guardian == null || !guardian.isValid() || center.getWorld() == null
                || !guardian.getWorld().equals(center.getWorld())) return;
        double radius = skillOptionDouble(unit, "GUARDIAN_QUAKE", "radius-blocks", 3.0, 1.0, 8.0);
        int durationSeconds = (int) skillOptionDouble(unit, "GUARDIAN_QUAKE",
            "duration-seconds", 5.0, 1.0, 15.0);
        int slowSeconds = (int) skillOptionDouble(unit, "GUARDIAN_QUAKE",
            "slowness-duration-seconds", 3.0, 1.0, 10.0);
        int slowAmplifier = (int) skillOptionDouble(unit, "GUARDIAN_QUAKE",
            "slowness-amplifier", 4.0, 0.0, 10.0);
        String fakeMaterialName = skillOptionString(unit, "GUARDIAN_QUAKE",
            "fake-block-material", "COBBLESTONE");
        Material fakeMaterial = Material.matchMaterial(fakeMaterialName);
        if (fakeMaterial == null || fakeMaterial.isAir() || !fakeMaterial.isSolid()) {
            plugin.getLogger().warning("[Army] Invalid fake block material '" + fakeMaterialName
                + "' for GUARDIAN_QUAKE; skipping fake block display.");
        } else {
            displayGuardianQuakeBlocks(unit, center, radius, fakeMaterial.createBlockData(),
                durationSeconds * 20);
        }

        for (Entity nearby : center.getWorld().getNearbyEntities(center, radius, 2.0, radius)) {
            if (!(nearby instanceof LivingEntity hostile) || !isHostileMob(hostile)) continue;
            hostile.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                slowSeconds * 20, slowAmplifier, true, true, true));
            hostile.setVelocity(new Vector(0.0, Math.max(0.0, hostile.getVelocity().getY()), 0.0));
        }
        center.getWorld().spawnParticle(Particle.EXPLOSION, center.clone().add(0.0, 0.2, 0.0),
            1, 0.0, 0.0, 0.0, 0.0);
        markUsed(unit, "GUARDIAN_QUAKE", skillCooldownSeconds(unit, "GUARDIAN_QUAKE"));
        announceSkillCast(unit, owner, "GUARDIAN_QUAKE", Particle.WAX_ON);
    }

    private void displayGuardianQuakeBlocks(Unit unit, Location center, double radius,
                                            BlockData fakeBlockData, int durationTicks) {
        World world = center.getWorld();
        if (world == null) return;
        int blockRadius = (int) Math.ceil(radius);
        Set<UUID> viewers = new HashSet<>();
        double viewerRadius = radius + 8.0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(world)
                    && player.getLocation().distanceSquared(center) <= viewerRadius * viewerRadius) {
                viewers.add(player.getUniqueId());
            }
        }
        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();
        long expiresAt = System.currentTimeMillis() + durationTicks * 50L;
        for (int dx = -blockRadius; dx <= blockRadius; dx++) {
            for (int dz = -blockRadius; dz <= blockRadius; dz++) {
                if (Math.abs(Math.sqrt(dx * dx + dz * dz) - radius) > 0.6) continue;
                for (int dy = 0; dy <= 1; dy++) {
                    int y = centerY + dy;
                    if (y < world.getMinHeight() || y >= world.getMaxHeight()) continue;
                    Block block = world.getBlockAt(centerX + dx, y, centerZ + dz);
                    if (!block.getType().isAir()) continue;
                    String key = guardianFakeBlockKey(block);
                    GuardianFakeBlockOverlay overlay = guardianFakeBlockOverlays.computeIfAbsent(
                        key, ignored -> new GuardianFakeBlockOverlay(block));
                    overlay.unitExpiries.put(unit.id, expiresAt);
                    overlay.viewers.addAll(viewers);
                    overlay.expiresAtMillis = Math.max(overlay.expiresAtMillis, expiresAt);
                    for (UUID viewerId : viewers) {
                        Player viewer = Bukkit.getPlayer(viewerId);
                        if (viewer != null && viewer.isOnline()) {
                            viewer.sendBlockChange(block.getLocation(), fakeBlockData);
                        }
                    }
                    scheduleGuardianFakeBlockRestore(key, overlay);
                }
            }
        }
    }

    private String guardianFakeBlockKey(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private void scheduleGuardianFakeBlockRestore(String key, GuardianFakeBlockOverlay overlay) {
        if (overlay.removalTask != null) overlay.removalTask.cancel();
        long delayTicks = Math.max(1L,
            (overlay.expiresAtMillis - System.currentTimeMillis() + 49L) / 50L);
        overlay.removalTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (guardianFakeBlockOverlays.get(key) != overlay) return;
            long now = System.currentTimeMillis();
            overlay.unitExpiries.entrySet().removeIf(entry -> entry.getValue() <= now);
            if (!overlay.unitExpiries.isEmpty()) {
                overlay.expiresAtMillis = overlay.unitExpiries.values().stream()
                    .mapToLong(Long::longValue).max().orElse(now);
                scheduleGuardianFakeBlockRestore(key, overlay);
                return;
            }
            restoreGuardianFakeBlock(key, overlay);
        }, delayTicks);
    }

    private void restoreGuardianFakeBlock(String key, GuardianFakeBlockOverlay overlay) {
        if (overlay.removalTask != null) {
            overlay.removalTask.cancel();
            overlay.removalTask = null;
        }
        if (overlay.world.isChunkLoaded(overlay.x >> 4, overlay.z >> 4)) {
            Location location = overlay.location();
            BlockData currentData = overlay.world.getBlockAt(overlay.x, overlay.y, overlay.z).getBlockData();
            for (UUID viewerId : overlay.viewers) {
                Player viewer = Bukkit.getPlayer(viewerId);
                if (viewer != null && viewer.isOnline() && viewer.getWorld().equals(overlay.world)) {
                    viewer.sendBlockChange(location, currentData);
                }
            }
        }
        guardianFakeBlockOverlays.remove(key, overlay);
    }

    private void activateGuardianUltimate(Unit unit, Player owner, Mob guardian, double tauntRadius) {
        Location destination = safeLocation(owner.getLocation().clone().add(1.0, 0.0, 1.0));
        if (!guardian.teleport(destination)) return;
        int protectionSeconds = (int) skillOptionDouble(unit, "GUARDIAN_ULTIMATE",
            "protection-duration-seconds", 3.0, 1.0, 10.0);
        guardianOwnerImmunityExpiries.merge(owner.getUniqueId(),
            System.currentTimeMillis() + protectionSeconds * 1000L, Math::max);
        double horizontalKnockback = skillOptionDouble(unit, "GUARDIAN_ULTIMATE",
            "knockback-strength", 1.8, 0.0, 5.0);
        double verticalKnockback = skillOptionDouble(unit, "GUARDIAN_ULTIMATE",
            "knockback-y", 0.65, 0.0, 2.0);
        for (Entity nearby : owner.getNearbyEntities(tauntRadius, 3.0, tauntRadius)) {
            if (!(nearby instanceof LivingEntity hostile) || !isHostileMob(hostile)) continue;
            Vector push = hostile.getLocation().toVector().subtract(owner.getLocation().toVector());
            push.setY(0.0);
            if (push.lengthSquared() <= 0.0001) {
                push = owner.getLocation().getDirection().setY(0.0);
            }
            if (push.lengthSquared() <= 0.0001) push = new Vector(1.0, 0.0, 0.0);
            push.normalize().multiply(horizontalKnockback).setY(verticalKnockback);
            hostile.setVelocity(push);
            if (hostile instanceof Mob hostileMob) hostileMob.setTarget(guardian);
        }
        owner.getWorld().spawnParticle(Particle.WAX_ON,
            owner.getLocation().add(0.0, 1.0, 0.0), 32, 0.8, 0.8, 0.8, 0.08);
        markUsed(unit, "GUARDIAN_ULTIMATE", skillCooldownSeconds(unit, "GUARDIAN_ULTIMATE"));
        announceSkillCast(unit, owner, "GUARDIAN_ULTIMATE", Particle.WAX_ON);
    }

    private void forgetGuardianTask(UUID unitId, int taskId) {
        Set<BukkitTask> tasks = guardianTasksByUnit.get(unitId);
        if (tasks == null) return;
        tasks.removeIf(task -> task.getTaskId() == taskId);
        if (tasks.isEmpty()) guardianTasksByUnit.remove(unitId);
    }

    private void useSkills(Unit unit, Player owner, LivingEntity target) {
        Mob mob = activeEntities.get(unit.id);
        if (target != null && (mob == null || !mob.isValid() || !target.isValid() || target.isDead()
                || !mob.getWorld().equals(target.getWorld()) || !mob.hasLineOfSight(target)
                || isHiddenUndergroundTarget(mob, target))) {
            if (mob != null && mob.isValid()) mob.setTarget(null);
            target = null;
        }
        if ("frost_mage".equals(unit.unitClass)) {
            useFrostMageSkills(unit, owner, mob, target);
            return;
        }
        if ("guardian".equals(unit.unitClass)) {
            useGuardianSkills(unit, owner, mob, target);
            return;
        }
        if (hasSkill(unit, "SUMMON_VEXES") && mob != null && target instanceof Monster hostile
                && !isMercenary(hostile) && ready(unit, "summon-vexes")) {
            int maximum = maxSummonedMinions(unit);
            int lifetime = (int) skillOptionDouble(unit, "SUMMON_VEXES",
                "lifetime-seconds", 15.0, 1.0, 120.0);
            double damage = skillOptionDouble(unit, "SUMMON_VEXES", "damage", 4.0, 0.0, 100.0)
                * skillPowerMultiplier(unit);
            Set<UUID> active = summonedMinions.computeIfAbsent(unit.id, ignored -> new HashSet<>());
            active.removeIf(id -> {
                Entity entity = Bukkit.getEntity(id);
                return entity == null || !entity.isValid() || entity.isDead();
            });
            int summoned = 0;
            for (int index = active.size(); index < maximum; index++) {
                Location spawnLocation = mob.getLocation().clone().add(
                    index % 2 == 0 ? 0.7 : -0.7, 1.0, index % 2 == 0 ? -0.7 : 0.7);
                Entity entity;
                try {
                    entity = mob.getWorld().spawnEntity(spawnLocation, EntityType.VEX);
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().warning("[Army] Could not summon Vex for class "
                        + unit.unitClass + ": " + exception.getMessage());
                    break;
                }
                if (!(entity instanceof Vex vex)) {
                    entity.remove();
                    continue;
                }
                vex.getPersistentDataContainer().set(summonedMinionKey,
                    PersistentDataType.STRING, unit.id.toString());
                vex.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, unit.owner.toString());
                vex.setSummoner(mob);
                vex.setPersistent(false);
                vex.setRemoveWhenFarAway(true);
                vex.setCanPickupItems(false);
                vex.setAware(true);
                vex.setLimitedLifetime(true);
                vex.setLimitedLifetimeTicks(lifetime * 20);
                if (vex.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
                    vex.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(Math.min(100.0, damage));
                }
                vex.setTarget(hostile);
                active.add(vex.getUniqueId());
                summoned++;
                break;
            }
            if (summoned > 0) {
                markUsed(unit, "summon-vexes", skillCooldownSeconds(unit, "SUMMON_VEXES"));
                announceSkillCast(unit, owner, "SUMMON_VEXES", Particle.SOUL);
            }
        }
        if (hasSkill(unit, "LANCE_CHARGE") && mob != null && target != null && ready(unit, "lance-charge")
                && "paladin".equals(unit.unitClass) && mob instanceof Zombie zombie) {
            Horse horse = getMountedHorse(unit, zombie);
            if (horse != null && target instanceof Monster hostile && !isMercenary(hostile)) {
                double damage = Math.min(100.0,
                    skillOptionDouble(unit, "LANCE_CHARGE", "damage", 12.0, 0.0, 100.0)
                        * skillPowerMultiplier(unit));
                double radius = skillOptionDouble(unit, "LANCE_CHARGE", "radius-blocks", 4.0, 1.0, 12.0);
                Vector chargeDir = hostile.getLocation().toVector().subtract(horse.getLocation().toVector());
                if (chargeDir.lengthSquared() <= 0.0001) chargeDir = new Vector(0.0, 0.0, 1.0);
                else chargeDir.normalize();
                horse.setVelocity(chargeDir.clone().multiply(1.2));
                zombie.swingMainHand();
                zombie.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, 0.8f);
                horse.getWorld().spawnParticle(Particle.CRIT, horse.getLocation().add(0, 1, 0),
                    18, 0.6, 0.7, 0.6, 0.05);
                horse.getWorld().spawnParticle(Particle.SWEEP_ATTACK, zombie.getLocation().add(0, 1, 0),
                    1, 0.15, 0.15, 0.15, 0.0);
                hostile.getWorld().spawnParticle(Particle.CRIT, hostile.getLocation().add(0, 1, 0),
                    12, 0.35, 0.5, 0.35, 0.04);
                for (Entity nearby : hostile.getWorld().getNearbyEntities(hostile.getLocation(), radius, radius, radius)) {
                    if (!(nearby instanceof Monster victim) || !isValidTarget(victim) || isMercenary(victim)) continue;
                    if (zombie.hasLineOfSight(victim)
                            && victim.getLocation().distanceSquared(hostile.getLocation()) <= radius * radius) {
                        victim.damage(damage, zombie);
                        victim.setVelocity(victim.getVelocity().add(chargeDir.clone().multiply(0.35)));
                    }
                }
                markUsed(unit, "lance-charge", skillCooldownSeconds(unit, "LANCE_CHARGE"));
                announceSkillCast(unit, owner, "LANCE_CHARGE", Particle.CRIT);
            }
        }
        if (hasSkill(unit, "ILLUMINATION") && mob != null && target != null
                && isWithinConfiguredSkillRange(unit, mob, target, "ILLUMINATION")
                && canUseIlluminationInCurrentLight(mob)
                && ready(unit, "illumination")) {
            fireIlluminationVolley(unit, mob, target, owner);
        }
        if (hasSkill(unit, "RANGED_ATTACK") && target != null && mob != null
                && isWithinConfiguredSkillRange(unit, mob, target, "RANGED_ATTACK")
                && ready(unit, "ranged-attack")) {
            try {
                int ammoBefore = countStoredArrows(unit);
                AbstractArrow arrow = launchConfiguredArrow(mob, unit);
                arrow.setDamage(Math.min(100.0, skillOptionDouble(unit, "RANGED_ATTACK",
                    "damage", 4.0, 0.0, 100.0) * skillPowerMultiplier(unit)));
                arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
                if ("ranger".equals(unit.unitClass)) {
                    Vector aim = target.getEyeLocation().toVector().subtract(mob.getEyeLocation().toVector());
                    if (aim.lengthSquared() > 0.0001) arrow.setVelocity(aim.normalize().multiply(2.4));
                }
                persistAmmoIfConsumed(unit, ammoBefore);
                markUsed(unit, "ranged-attack", skillCooldownSeconds(unit, "RANGED_ATTACK"));
                announceSkillCast(unit, owner, "RANGED_ATTACK", Particle.CRIT);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("[Army] Could not launch RANGED_ATTACK for class "
                    + unit.unitClass + ": " + exception.getMessage());
            }
        }

        useSkillsRemaining(unit, owner, target, mob);
    }

    private void useFrostMageSkills(Unit unit, Player owner, Mob mob, LivingEntity target) {
        if (mob == null || !mob.isValid()) return;
        if (target != null && isWithinFrostRange(unit, mob, target, "BASIC_ATTACK")
                && ready(unit, "basic-attack")) {
            launchFrostProjectile(mob, target, frostDamage(unit, "BASIC_ATTACK", 3.0),
                frostOptionDouble("BASIC_ATTACK", "projectile-speed", 1.65, 0.5, 4.0));
            markUsed(unit, "basic-attack", frostCooldown(unit, "BASIC_ATTACK", 2.0));
        }
        if (hasSkill(unit, "ICE_SPIKE") && target != null
                && isWithinFrostRange(unit, mob, target, "ICE_SPIKE") && ready(unit, "ICE_SPIKE")) {
            launchFrostProjectile(mob, target, frostDamage(unit, "ICE_SPIKE", 3.0),
                frostOptionDouble("ICE_SPIKE", "projectile-speed", 2.1, 0.5, 4.0));
            markUsed(unit, "ICE_SPIKE", frostCooldown(unit, "ICE_SPIKE", 6.0));
            announceSkillCast(unit, owner, "ICE_SPIKE", Particle.SNOWFLAKE);
        }
        if (hasSkill(unit, "BLIZZARD") && target != null
                && isWithinFrostRange(unit, mob, target, "BLIZZARD") && ready(unit, "BLIZZARD")) {
            castBlizzard(unit, mob, target);
            markUsed(unit, "BLIZZARD", frostCooldown(unit, "BLIZZARD", 14.0));
            announceSkillCast(unit, owner, "BLIZZARD", Particle.SNOWFLAKE);
        }
        if (hasSkill(unit, "FROST_NOVA") && target != null
                && isWithinFrostRange(unit, mob, target, "FROST_NOVA") && ready(unit, "FROST_NOVA")) {
            castFrostNova(unit, mob);
            markUsed(unit, "FROST_NOVA", frostCooldown(unit, "FROST_NOVA", 12.0));
            announceSkillCast(unit, owner, "FROST_NOVA", Particle.SNOWFLAKE);
        }
        if (hasSkill(unit, "ICE_WALL") && target != null
                && isWithinFrostRange(unit, mob, target, "ICE_WALL") && ready(unit, "ICE_WALL")) {
            if (castIceWall(unit, mob, target)) {
                markUsed(unit, "ICE_WALL", frostCooldown(unit, "ICE_WALL", 18.0));
                announceSkillCast(unit, owner, "ICE_WALL", Particle.SNOWFLAKE);
            }
        }
        if (hasSkill(unit, "FROST_SHIELD") && target != null && ready(unit, "FROST_SHIELD")) {
            castFrostShield(unit, mob);
            markUsed(unit, "FROST_SHIELD", frostCooldown(unit, "FROST_SHIELD", 24.0));
            announceSkillCast(unit, owner, "FROST_SHIELD", Particle.WAX_ON);
        }
        if (hasSkill(unit, "FROST_STEP") && target != null
                && isWithinFrostRange(unit, mob, target, "FROST_STEP") && ready(unit, "FROST_STEP")) {
            castFrostStep(unit, mob, target);
            markUsed(unit, "FROST_STEP", frostCooldown(unit, "FROST_STEP", 8.0));
            announceSkillCast(unit, owner, "FROST_STEP", Particle.SNOWFLAKE);
        }
        if (hasSkill(unit, "FROST_WALKER") && target != null
                && isWithinFrostRange(unit, mob, target, "FROST_WALKER") && ready(unit, "FROST_WALKER")) {
            castFrostWalker(unit, mob);
            markUsed(unit, "FROST_WALKER", frostCooldown(unit, "FROST_WALKER", 30.0));
            announceSkillCast(unit, owner, "FROST_WALKER", Particle.SNOWFLAKE);
        }
        if (hasSkill(unit, "ICE_AGE") && target != null && ready(unit, "ICE_AGE")
                && mob.getLocation().distanceSquared(target.getLocation())
                    <= Math.pow(frostRange(unit, "ICE_AGE", "range-blocks", 15.0), 2)) {
            castIceAge(unit, mob);
            markUsed(unit, "ICE_AGE", frostCooldown(unit, "ICE_AGE", 60.0));
            announceSkillCast(unit, owner, "ICE_AGE", Particle.SNOWFLAKE);
        }
    }

    private void launchFrostProjectile(Mob mob, LivingEntity target, double damage, double speed) {
        Snowball snowball = mob.launchProjectile(Snowball.class);
        Vector direction = target.getEyeLocation().toVector().subtract(mob.getEyeLocation().toVector());
        if (direction.lengthSquared() > 0.0001) snowball.setVelocity(direction.normalize().multiply(speed));
        snowball.setGravity(false);
        snowball.getPersistentDataContainer().set(frostProjectileDamageKey, PersistentDataType.DOUBLE, damage);
    }

    private boolean isWithinFrostRange(Unit unit, Mob mob, LivingEntity target, String skill) {
        double range = frostRange(unit, skill, "range-blocks", 5.0);
        return mob.getWorld().equals(target.getWorld())
            && mob.getLocation().distanceSquared(target.getLocation()) <= range * range;
    }

   private double frostRange(MercenaryManager.Unit unit, String skill, String option, double fallback) {
      String basePath = "classes.frost_mage." + (skill.equals("BASIC_ATTACK") ? "basic-attack." : "skill-settings." + skill + ".") + option;
      double base = this.readBoundedDouble(this.settings, basePath, fallback, 1.0, 64.0);
      int interval = this.readBoundedInt(this.settings, "classes.frost_mage.level-scaling.range-increase-every-levels", 5, 1, 30);
      double increase = this.readBoundedDouble(this.settings, "classes.frost_mage.level-scaling.range-increase-blocks", 1.0, 0.0, 16.0);
      return Math.min(64.0, base + unit.level / interval * increase + this.soldierGearStats(unit).attackRangeBlocks());
   }

    private double frostDamage(Unit unit, String skill, double fallback) {
        double base = frostOptionDouble(skill, "damage", fallback, 0.0, 100.0);
        double increase = readBoundedDouble(settings,
            "classes.frost_mage.level-scaling.damage-per-level", 0.1, 0.0, 10.0);
        return Math.min(100.0, base + Math.max(0, unit.level - 1) * increase);
    }

   private double frostCooldown(MercenaryManager.Unit unit, String skill, double fallback) {
      double base = this.frostOptionDouble(skill, "cooldown-seconds", fallback, 1.0, 3600.0);
      double reduction = this.readBoundedDouble(this.settings, "classes.frost_mage.level-scaling.cooldown-reduction-per-level-seconds", 0.1, 0.0, 10.0);
      double levelAdjusted = Math.max(1.0, base - Math.max(0, unit.level - 1) * reduction);
      return Math.max(1.0, levelAdjusted * (1.0 - Math.min(20.0, this.soldierGearStats(unit).cooldownReductionPercent()) / 100.0));
   }

    private int frostDurationTicks(Unit unit, String skill, double fallback, double min, double max) {
        double base = skillOptionDouble(unit, skill, "duration-seconds", fallback, min, max);
        double increase = skillOptionDouble(unit, skill, "duration-increase-per-level-seconds",
            0.1, 0.0, 10.0);
        return (int) Math.round(Math.min(max, base + Math.max(0, unit.level - 1) * increase) * 20.0);
    }

    private void castBlizzard(Unit unit, Mob mob, LivingEntity target) {
        double radius = skillOptionDouble(unit, "BLIZZARD", "radius-blocks", 4.0, 1.0, 12.0);
        int durationTicks = frostDurationTicks(unit, "BLIZZARD", 3.0, 1.0, 12.0);
        int intervalTicks = (int) skillOptionDouble(unit, "BLIZZARD", "pulse-interval-ticks", 20.0, 10.0, 100.0);
        int maxTargets = (int) skillOptionDouble(unit, "BLIZZARD", "max-targets", 8.0, 1.0, 32.0);
        int slowTicks = (int) skillOptionDouble(unit, "BLIZZARD", "slowness-duration-ticks", 40.0, 10.0, 200.0);
        int amplifier = (int) skillOptionDouble(unit, "BLIZZARD", "slowness-amplifier", 1.0, 0.0, 4.0);
        Location center = target.getLocation().clone();
        double damage = frostDamage(unit, "BLIZZARD", 3.0);
        BukkitTask task = new BukkitRunnable() {
            private int elapsed;

            @Override
            public void run() {
                if (!mob.isValid() || mob.isDead() || elapsed >= durationTicks) {
                    cancel();
                    return;
                }
                mob.getWorld().spawnParticle(Particle.SNOWFLAKE, center.clone().add(0.0, 1.0, 0.0),
                    24, radius / 2.0, 0.8, radius / 2.0, 0.02);
                for (LivingEntity victim : frostTargets(mob, center, radius, maxTargets)) {
                    victim.damage(damage, mob);
                    applyFrostPotionEffect(victim, new PotionEffect(PotionEffectType.SLOWNESS,
                        slowTicks, amplifier, true, true, true));
                }
                elapsed += intervalTicks;
            }
        }.runTaskTimer(plugin, 0L, intervalTicks);
        trackFrostTask(task);
    }

    private void castFrostNova(Unit unit, Mob mob) {
        double radius = skillOptionDouble(unit, "FROST_NOVA", "radius-blocks", 4.0, 1.0, 12.0);
        int maxTargets = (int) skillOptionDouble(unit, "FROST_NOVA", "max-targets", 8.0, 1.0, 32.0);
        int amplifier = (int) skillOptionDouble(unit, "FROST_NOVA", "slowness-amplifier", 3.0, 0.0, 6.0);
        int duration = frostDurationTicks(unit, "FROST_NOVA", 3.0, 1.0, 10.0);
        Location center = mob.getLocation().clone();
        mob.getWorld().spawnParticle(Particle.SNOWFLAKE, center.add(0.0, 1.0, 0.0),
            48, radius / 2.0, 0.8, radius / 2.0, 0.04);
        for (LivingEntity victim : frostTargets(mob, mob.getLocation(), radius, maxTargets)) {
            applyFrostPotionEffect(victim, new PotionEffect(PotionEffectType.SLOWNESS,
                duration, amplifier, true, true, true));
            Block below = victim.getLocation().getBlock().getRelative(org.bukkit.block.BlockFace.DOWN);
            placeTemporaryFrostBlock(below, Material.PACKED_ICE,
                (int) skillOptionDouble(unit, "FROST_NOVA", "ice-duration-seconds", 3.0, 1.0, 30.0));
        }
    }

    private boolean castIceWall(Unit unit, Mob mob, LivingEntity target) {
        long durationTicks = Math.max(1L, Math.round(
            scaledSkillDurationSeconds(unit, "ICE_WALL", 3.0, 0.0, 30.0) * 20.0));
        Location center = target.getLocation().clone();
        int centerY = center.getBlockY();
        boolean placed = false;
        for (Block block : enclosureBlocks(center)) {
            placed |= placeTemporaryFrostBlockForTicks(block, Material.PACKED_ICE,
                durationTicks, unit.id, block.getY() == centerY - 1);
        }
        if (!placed) return false;

        double damage = Math.min(100.0, (
            skillOptionDouble(unit, "ICE_WALL", "damage-per-second", 1.0, 0.0, 100.0)
                + Math.max(0, unit.level - 1) * skillOptionDouble(unit, "ICE_WALL",
                    "damage-increase-per-level", 0.1, 0.0, 10.0)) * skillPowerMultiplier(unit));
        double slowSeconds = Math.min(30.0,
            skillOptionDouble(unit, "ICE_WALL", "slowness-duration-seconds", 3.0, 0.1, 30.0)
                + Math.max(0, unit.level - 1) * skillOptionDouble(unit, "ICE_WALL",
                    "slowness-duration-increase-per-level-seconds", 0.1, 0.0, 10.0));
        BukkitTask task = new BukkitRunnable() {
            private long elapsedTicks;

            @Override
            public void run() {
                if (!target.isValid() || target.isDead()) {
                    cancel();
                    return;
                }
                if (!isActiveFireCaster(mob)) {
                    applyIcePrisonSlow(target, skillOptionDouble(unit, "ICE_WALL",
                        "slowness-percent", 50.0, 0.0, 80.0), slowSeconds);
                    cancel();
                    return;
                }
                if (elapsedTicks >= durationTicks || !isWithinEnclosure(target, center, centerY)) {
                    applyIcePrisonSlow(target, skillOptionDouble(unit, "ICE_WALL",
                        "slowness-percent", 50.0, 0.0, 80.0), slowSeconds);
                    cancel();
                    return;
                }
                elapsedTicks++;
                if (elapsedTicks % 20L == 0L && damage > 0.0
                        && canMercenaryDamageTarget(unit.owner, target)
                        && (isValidTarget(target) || isOwnerCommandedPvpTarget(unit.owner, target))) {
                    target.damage(damage, mob);
                    target.getWorld().spawnParticle(Particle.SNOWFLAKE,
                        target.getLocation().add(0.0, 1.0, 0.0), 8, 0.25, 0.35, 0.25, 0.02);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        trackFrostTask(unit, task);
        return true;
    }

    private List<Block> enclosureBlocks(Location target) {
        int centerX = target.getBlockX();
        int baseY = target.getBlockY();
        int centerZ = target.getBlockZ();
        World world = target.getWorld();
        List<Block> blocks = new ArrayList<>(25);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                addEnclosureBlock(blocks, world, centerX + x, baseY - 1, centerZ + z);
                if (Math.abs(x) == 1 || Math.abs(z) == 1) {
                    addEnclosureBlock(blocks, world, centerX + x, baseY, centerZ + z);
                    addEnclosureBlock(blocks, world, centerX + x, baseY + 1, centerZ + z);
                }
            }
        }
        return blocks;
    }

    private void addEnclosureBlock(List<Block> blocks, World world, int x, int y, int z) {
        if (y >= world.getMinHeight() && y < world.getMaxHeight()) {
            blocks.add(world.getBlockAt(x, y, z));
        }
    }

    private boolean isWithinEnclosure(LivingEntity target, Location center, int baseY) {
        Location location = target.getLocation();
        return location.getWorld().equals(center.getWorld())
            && location.getBlockX() == center.getBlockX()
            && location.getBlockZ() == center.getBlockZ()
            && location.getBlockY() >= baseY
            && location.getBlockY() <= baseY + 1;
    }

    private double scaledSkillDurationSeconds(Unit unit, String skill,
                                                double fallback, double increaseFallback, double maximum) {
        double base = skillOptionDouble(unit, skill, "duration-seconds", fallback, 0.1, maximum);
        double increase = skillOptionDouble(unit, skill, "duration-increase-per-level-seconds",
            increaseFallback, 0.0, 10.0);
        return Math.min(maximum, base + Math.max(0, unit.level - 1) * increase);
    }

    private void applyIcePrisonSlow(LivingEntity target, double slowPercent, double durationSeconds) {
        var movementSpeed = target.getAttribute(Attribute.MOVEMENT_SPEED);
        if (movementSpeed == null || slowPercent <= 0.0) return;
        if (movementSpeed.getModifier(icePrisonSlowKey) != null) {
            movementSpeed.removeModifier(icePrisonSlowKey);
        }
        movementSpeed.addModifier(new AttributeModifier(
            icePrisonSlowKey, -Math.min(80.0, slowPercent) / 100.0,
            AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        long expiresAt = System.currentTimeMillis() + Math.round(durationSeconds * 1000.0);
        target.getPersistentDataContainer().set(icePrisonSlowExpiryKey, PersistentDataType.LONG, expiresAt);
        icePrisonSlowExpiries.put(target.getUniqueId(), expiresAt);
    }

    private void removeIcePrisonSlow(LivingEntity target) {
        var movementSpeed = target.getAttribute(Attribute.MOVEMENT_SPEED);
        if (movementSpeed != null && movementSpeed.getModifier(icePrisonSlowKey) != null) {
            movementSpeed.removeModifier(icePrisonSlowKey);
        }
        target.getPersistentDataContainer().remove(icePrisonSlowExpiryKey);
        icePrisonSlowExpiries.remove(target.getUniqueId());
    }

    private void castFrostShield(Unit unit, Mob mob) {
        int duration = frostDurationTicks(unit, "FROST_SHIELD", 6.0, 1.0, 30.0);
        int amplifier = (int) skillOptionDouble(unit, "FROST_SHIELD", "absorption-amplifier", 1.0, 0.0, 10.0);
        applyFrostPotionEffect(mob, new PotionEffect(PotionEffectType.ABSORPTION,
            duration, amplifier, true, true, true));
        frostShieldExpiries.entrySet().removeIf(entry -> entry.getValue() <= System.currentTimeMillis());
        frostShieldExpiries.put(mob.getUniqueId(), System.currentTimeMillis() + duration * 50L);
    }

    private void castFrostStep(Unit unit, Mob mob, LivingEntity target) {
        Location start = mob.getLocation().clone().add(0.0, 0.5, 0.0);
        Vector direction = target.getLocation().toVector().subtract(start.toVector());
        double distance = direction.length();
        if (distance < 0.1) return;
        Vector normalized = direction.clone().normalize();
        if (mob.getWorld().rayTraceBlocks(start, normalized, Math.min(distance, 6.0),
                FluidCollisionMode.NEVER, true) != null) return;
        double speed = skillOptionDouble(unit, "FROST_STEP", "dash-speed", 0.7, 0.2, 1.5);
        normalized.setY(Math.max(-0.15, Math.min(0.2, normalized.getY())));
        mob.setVelocity(normalized.multiply(speed));
    }

    private void castFrostWalker(Unit unit, Mob mob) {
        int durationTicks = frostDurationTicks(unit, "FROST_WALKER", 8.0, 1.0, 30.0);
        int intervalTicks = (int) skillOptionDouble(unit, "FROST_WALKER", "trail-interval-ticks", 10.0, 5.0, 40.0);
        double radius = skillOptionDouble(unit, "FROST_WALKER", "trail-radius-blocks", 2.0, 1.0, 5.0);
        int blockDuration = (int) skillOptionDouble(unit, "FROST_WALKER", "ice-duration-seconds", 3.0, 1.0, 15.0);
        BukkitTask task = new BukkitRunnable() {
            private int elapsed;

            @Override
            public void run() {
                if (!mob.isValid() || mob.isDead() || elapsed >= durationTicks) {
                    cancel();
                    return;
                }
                Location feet = mob.getLocation();
                mob.getWorld().spawnParticle(Particle.SNOWFLAKE, feet.clone().add(0.0, 0.1, 0.0),
                    5, 0.35, 0.08, 0.35, 0.01);
                int search = (int) Math.ceil(radius);
                for (int x = -search; x <= search; x++) {
                    for (int z = -search; z <= search; z++) {
                        if (x * x + z * z > radius * radius) continue;
                        Block surface = feet.getBlock().getRelative(x, -1, z);
                        if (surface.getType() == Material.WATER && surface.getRelative(0, 1, 0).getType().isAir()) {
                            placeTemporaryFrostBlock(surface, Material.PACKED_ICE, blockDuration);
                        }
                    }
                }
                elapsed += intervalTicks;
            }
        }.runTaskTimer(plugin, 0L, intervalTicks);
        trackFrostTask(task);
    }

    private void castIceAge(Unit unit, Mob mob) {
        double radius = frostRange(unit, "ICE_AGE", "radius-blocks", 15.0);
        int maxTargets = (int) skillOptionDouble(unit, "ICE_AGE", "max-targets", 24.0, 1.0, 64.0);
        int duration = frostDurationTicks(unit, "ICE_AGE", 4.0, 1.0, 10.0);
        int amplifier = (int) skillOptionDouble(unit, "ICE_AGE", "slowness-amplifier", 2.0, 0.0, 6.0);
        Location center = mob.getLocation().clone();
        mob.getWorld().spawnParticle(Particle.SNOWFLAKE, center.clone().add(0.0, 1.0, 0.0),
            160, radius / 2.0, 2.0, radius / 2.0, 0.04);
        for (LivingEntity victim : frostTargets(mob, center, radius, maxTargets)) {
            applyFrostPotionEffect(victim, new PotionEffect(PotionEffectType.SLOWNESS,
                duration, amplifier, true, true, true));
            applyFrostFreeze(victim, duration);
        }
    }

    private List<LivingEntity> frostTargets(Mob source, Location center, double radius, int maxTargets) {
        double radiusSquared = radius * radius;
        return source.getNearbyEntities(radius, radius, radius).stream()
            .filter(LivingEntity.class::isInstance)
            .map(LivingEntity.class::cast)
            .filter(this::isValidTarget)
            .filter(target -> source.getWorld().equals(target.getWorld())
                && target.getLocation().distanceSquared(center) <= radiusSquared
                && source.hasLineOfSight(target))
            .sorted(Comparator.comparingDouble(target -> target.getLocation().distanceSquared(center)))
            .limit(maxTargets)
            .toList();
    }

    private void placeTemporaryFrostBlock(Block block, Material type, int durationSeconds) {
        placeTemporaryFrostBlockForTicks(block, type, Math.max(1L, durationSeconds * 20L), null);
    }

    private boolean placeTemporaryFrostBlockForTicks(Block block, Material type,
                                                     long durationTicks, UUID ownerUnitId) {
        return placeTemporaryFrostBlockForTicks(block, type, durationTicks, ownerUnitId, false);
    }

    private boolean placeTemporaryFrostBlockForTicks(Block block, Material type,
                                                     long durationTicks, UUID ownerUnitId,
                                                     boolean replaceFloor) {
        if (!block.getType().isAir() && block.getType() != Material.WATER
                && (!replaceFloor || !block.getType().isSolid()
                    || block.getState() instanceof org.bukkit.block.TileState)) return false;
        String key = block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
        int limit = (int) frostTemporaryBlockLimit();
        if (temporaryFrostBlocks.containsKey(key) || temporaryFireBlocks.containsKey(key)
                || temporaryFrostBlocks.size() >= limit) return false;
        TemporaryFrostBlock temporary = new TemporaryFrostBlock(key, block, type, ownerUnitId);
        block.setType(type, false);
        temporaryFrostBlocks.put(key, temporary);
        temporary.removalTask = Bukkit.getScheduler().runTaskLater(plugin,
            () -> restoreTemporaryFrostBlock(temporary), Math.max(1L, durationTicks));
        return true;
    }

    private double frostTemporaryBlockLimit() {
        return readBoundedDouble(settings, "classes.frost_mage.temporary-block-limit",
            128.0, 1.0, 512.0);
    }

    private double frostOptionDouble(String skill, String option, double fallback, double minimum, double maximum) {
        String path = "classes.frost_mage."
            + (skill.equals("BASIC_ATTACK") ? "basic-attack." : "skill-settings." + skill + ".")
            + option;
        return readBoundedDouble(settings, path, fallback, minimum, maximum);
    }

    private void restoreTemporaryFrostBlock(TemporaryFrostBlock temporary) {
        temporaryFrostBlocks.remove(temporary.key);
        Block block = temporary.world.getBlockAt(temporary.x, temporary.y, temporary.z);
        if (block.getType() == temporary.temporaryType) block.setBlockData(temporary.originalData, false);
    }

    private void trackFrostTask(BukkitTask task) {
        frostSkillTasks.removeIf(BukkitTask::isCancelled);
        frostSkillTasks.add(task);
    }

    private void trackFrostTask(Unit unit, BukkitTask task) {
        trackFrostTask(task);
        frostTasksByUnit.values().forEach(tasks -> tasks.removeIf(BukkitTask::isCancelled));
        frostTasksByUnit.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        frostTasksByUnit.computeIfAbsent(unit.id, ignored -> new HashSet<>()).add(task);
    }

    private void applyFrostPotionEffect(LivingEntity target, PotionEffect effect) {
        long now = System.currentTimeMillis();
        Map<PotionEffectType, FrostEffectSnapshot> snapshots =
            temporaryFrostEffects.computeIfAbsent(target.getUniqueId(), ignored -> new HashMap<>());
        FrostEffectSnapshot snapshot = snapshots.computeIfAbsent(effect.getType(), ignored -> {
            PotionEffect previous = target.getPotionEffect(effect.getType());
            long previousExpiry = previous == null ? 0L : now + previous.getDuration() * 50L;
            return new FrostEffectSnapshot(previous, previousExpiry);
        });
        snapshot.appliedAmplifier = effect.getAmplifier();
        snapshot.appliedExpiresAt = now + effect.getDuration() * 50L;
        target.addPotionEffect(effect);
    }

    private void applyFrostFreeze(LivingEntity target, int durationTicks) {
        long now = System.currentTimeMillis();
        int originalTicks = target.getFreezeTicks();
        FrostFreezeSnapshot snapshot = temporaryFrostFreezes.computeIfAbsent(target.getUniqueId(),
            ignored -> new FrostFreezeSnapshot(originalTicks, now + originalTicks * 50L));
        snapshot.appliedExpiresAt = Math.max(snapshot.appliedExpiresAt, now + durationTicks * 50L);
        target.setFreezeTicks(Math.max(originalTicks, durationTicks));
    }

    private void cleanupFrostPotionEffects(boolean force) {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Map<PotionEffectType, FrostEffectSnapshot>> targetEntry
                : new HashMap<>(temporaryFrostEffects).entrySet()) {
            Entity entity = Bukkit.getEntity(targetEntry.getKey());
            if (!(entity instanceof LivingEntity target) || !target.isValid()) {
                temporaryFrostEffects.remove(targetEntry.getKey());
                continue;
            }
            Map<PotionEffectType, FrostEffectSnapshot> snapshots = targetEntry.getValue();
            for (Map.Entry<PotionEffectType, FrostEffectSnapshot> effectEntry
                    : new HashMap<>(snapshots).entrySet()) {
                FrostEffectSnapshot snapshot = effectEntry.getValue();
                if (!force && snapshot.appliedExpiresAt > now) continue;
                PotionEffect current = target.getPotionEffect(effectEntry.getKey());
                long expectedRemainingTicks = Math.max(0L, (snapshot.appliedExpiresAt - now) / 50L);
                boolean stillOurs = current == null || (current.getAmplifier() == snapshot.appliedAmplifier
                    && current.getDuration() <= expectedRemainingTicks + 2L);
                if (stillOurs) {
                    if (current != null) target.removePotionEffect(effectEntry.getKey());
                    long previousTicks = Math.max(0L, (snapshot.previousExpiresAt - now) / 50L);
                    if (snapshot.previousEffect != null && previousTicks > 0L) {
                        PotionEffect previous = snapshot.previousEffect;
                        target.addPotionEffect(new PotionEffect(previous.getType(),
                            (int) Math.min(Integer.MAX_VALUE, previousTicks), previous.getAmplifier(),
                            previous.isAmbient(), previous.hasParticles(), previous.hasIcon()));
                    }
                }
                snapshots.remove(effectEntry.getKey());
            }
            if (snapshots.isEmpty()) temporaryFrostEffects.remove(targetEntry.getKey());
        }
        for (Map.Entry<UUID, FrostFreezeSnapshot> entry
                : new HashMap<>(temporaryFrostFreezes).entrySet()) {
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity target) || !target.isValid()) {
                temporaryFrostFreezes.remove(entry.getKey());
                continue;
            }
            FrostFreezeSnapshot snapshot = entry.getValue();
            if (!force && snapshot.appliedExpiresAt > now) continue;
            long expectedRemaining = Math.max(0L, (snapshot.appliedExpiresAt - now) / 50L);
            if (target.getFreezeTicks() <= expectedRemaining + 2L) {
                long previousRemaining = Math.max(0L, (snapshot.originalExpiresAt - now) / 50L);
                target.setFreezeTicks((int) Math.min(Integer.MAX_VALUE, previousRemaining));
            }
            temporaryFrostFreezes.remove(entry.getKey());
        }
    }

   private void useSkillsRemaining(MercenaryManager.Unit unit, Player owner, LivingEntity target, Mob mob) {
      if ("medic".equals(unit.unitClass) && mob != null) {
         this.useMedicPotion(unit, owner, mob);
      }

      if (this.hasSkill(unit, "VOLLEY")
         && target != null
         && mob != null
         && this.isWithinConfiguredSkillRange(unit, mob, target, "VOLLEY")
         && this.ready(unit, "volley")) {
         int arrowCount = (int)this.skillOptionDouble(unit, "VOLLEY", "arrow-count", 3.0, 2.0, 5.0);
         double speed = this.skillOptionDouble(unit, "VOLLEY", "arrow-speed", 2.0, 0.5, 4.0);
         double spread = this.skillOptionDouble(unit, "VOLLEY", "spread-degrees", 8.0, 0.0, 30.0);
         double damage = Math.min(100.0, this.skillOptionDouble(unit, "VOLLEY", "damage", 3.0, 0.0, 100.0) * this.skillPowerMultiplier(unit));
         int ammoBefore = this.countStoredArrows(unit);
         Vector direction = target.getEyeLocation().toVector().subtract(mob.getEyeLocation().toVector());
         if (direction.lengthSquared() > 0.0) {
            for (int index = 0; index < arrowCount; index++) {
               double offset = index - (arrowCount - 1) / 2.0;
               AbstractArrow arrow = this.launchConfiguredArrow(mob, unit);
               arrow.setVelocity(direction.clone().normalize().rotateAroundY(Math.toRadians(offset * spread)).multiply(speed));
               arrow.setDamage(damage);
               arrow.setPickupStatus(PickupStatus.DISALLOWED);
            }

            this.persistAmmoIfConsumed(unit, ammoBefore);
            this.markUsed(unit, "volley", this.skillCooldownSeconds(unit, "VOLLEY"));
            this.announceSkillCast(unit, owner, "VOLLEY", Particle.CRIT);
         }
      }

      if (this.hasSkill(unit, "POWER_STRIKE") && target != null && mob != null && this.ready(unit, "power-strike")) {
         double damage = Math.min(100.0, this.skillOptionDouble(unit, "POWER_STRIKE", "damage", 8.0, 0.0, 100.0) * this.skillPowerMultiplier(unit));
         target.damage(damage, mob);
         target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0.0, 1.0, 0.0), 16, 0.35, 0.45, 0.35, 0.15);
         this.markUsed(unit, "power-strike", this.skillCooldownSeconds(unit, "POWER_STRIKE"));
         this.announceSkillCast(unit, owner, "POWER_STRIKE", Particle.SWEEP_ATTACK);
      }

      if (this.hasSkill(unit, "TAUNT") && mob != null && this.ready(unit, "taunt")) {
         double radius = this.skillOptionDouble(unit, "TAUNT", "radius-blocks", 8.0, 1.0, 32.0);
         List<Monster> taunted = mob.getWorld()
            .getNearbyEntities(owner.getLocation(), radius, radius, radius)
            .stream()
            .filter(Monster.class::isInstance)
            .map(Monster.class::cast)
            .filter(
               monster -> monster.isValid()
                  && !monster.isDead()
                  && (monster.getTarget() == null || monster.getTarget().getUniqueId().equals(owner.getUniqueId()))
            )
            .toList();
         if (!taunted.isEmpty()) {
            taunted.forEach(monster -> monster.setTarget(mob));
            this.markUsed(unit, "taunt", this.skillCooldownSeconds(unit, "TAUNT"));
            this.announceSkillCast(unit, owner, "TAUNT", Particle.WAX_ON);
         }
      }

      if (this.hasSkill(unit, "CLEANSE_OWNER") && this.ready(unit, "cleanse-owner")) {
         boolean cleansed = false;

         for (String effect : this.settings.getStringList("classes." + unit.unitClass + ".skill-settings.CLEANSE_OWNER.effects")) {
            PotionEffectType type = (PotionEffectType)Registry.EFFECT.get(NamespacedKey.minecraft(effect.toLowerCase(Locale.ROOT)));
            if (type != null && owner.hasPotionEffect(type)) {
               owner.removePotionEffect(type);
               cleansed = true;
            }
         }

         if (cleansed) {
            this.markUsed(unit, "cleanse-owner", this.skillCooldownSeconds(unit, "CLEANSE_OWNER"));
            this.announceSkillCast(unit, owner, "CLEANSE_OWNER", Particle.HAPPY_VILLAGER);
         }
      }

      if (this.hasSkill(unit, "FIRE_BOLT") && target != null && mob != null && this.ready(unit, "fire-bolt")) {
         double damage = Math.min(100.0, this.skillOptionDouble(unit, "FIRE_BOLT", "damage", 7.0, 0.0, 100.0) * this.skillPowerMultiplier(unit));
         int fireSeconds = (int)this.skillOptionDouble(unit, "FIRE_BOLT", "fire-seconds", 3.0, 0.0, 15.0);
         target.damage(damage, mob);
         if (fireSeconds > 0) {
            target.setFireTicks(Math.max(target.getFireTicks(), fireSeconds * 20));
         }

         target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().add(0.0, 1.0, 0.0), 18, 0.35, 0.45, 0.35, 0.03);
         this.markUsed(unit, "fire-bolt", this.skillCooldownSeconds(unit, "FIRE_BOLT"));
         this.announceSkillCast(unit, owner, "FIRE_BOLT", Particle.FLAME);
      }

      if (this.hasSkill(unit, "ROOT_TARGET") && target != null && this.ready(unit, "root-target")) {
         String effectName = this.skillOptionString(unit, "ROOT_TARGET", "effect", "SLOWNESS");
         PotionEffectType effect = (PotionEffectType)Registry.EFFECT.get(NamespacedKey.minecraft(effectName.toLowerCase(Locale.ROOT)));
         if (effect != null) {
            int duration = (int)this.skillOptionDouble(unit, "ROOT_TARGET", "duration-seconds", 4.0, 1.0, 600.0);
            int amplifier = (int)this.skillOptionDouble(unit, "ROOT_TARGET", "amplifier", 1.0, 0.0, 10.0);
            target.addPotionEffect(new PotionEffect(effect, duration * 20, amplifier, true, true, true));
            target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0.0, 1.0, 0.0), 14, 0.35, 0.45, 0.35, 0.02);
            this.markUsed(unit, "root-target", this.skillCooldownSeconds(unit, "ROOT_TARGET"));
            this.announceSkillCast(unit, owner, "ROOT_TARGET", Particle.SNOWFLAKE);
         }
      }

      if (this.hasSkill(unit, "LIFESTEAL") && target != null && mob != null && this.ready(unit, "lifesteal")) {
         double maximum = maxHealth(mob);
         if (maximum > 0.0 && mob.getHealth() < maximum) {
            double damage = Math.min(100.0, this.skillOptionDouble(unit, "LIFESTEAL", "damage", 6.0, 0.0, 100.0) * this.skillPowerMultiplier(unit));
            double healing = Math.min(100.0, this.skillOptionDouble(unit, "LIFESTEAL", "heal-amount", 4.0, 0.0, 100.0) * this.skillPowerMultiplier(unit))
               * this.soldierHealingMultiplier(unit);
            target.damage(damage, mob);
            mob.setHealth(Math.min(maximum, mob.getHealth() + healing));
            mob.getWorld().spawnParticle(Particle.HEART, mob.getLocation().add(0.0, 1.0, 0.0), 10, 0.3, 0.4, 0.3, 0.02);
            this.markUsed(unit, "lifesteal", this.skillCooldownSeconds(unit, "LIFESTEAL"));
            this.announceSkillCast(unit, owner, "LIFESTEAL", Particle.DAMAGE_INDICATOR);
         }
      }

      if (this.hasSkill(unit, "BERSERK") && target != null && mob != null && this.ready(unit, "berserk")) {
         double maximum = maxHealth(mob);
         double threshold = this.skillOptionDouble(unit, "BERSERK", "health-percent", 50.0, 1.0, 99.0);
         String effectName = this.skillOptionString(unit, "BERSERK", "effect", "STRENGTH");
         PotionEffectType effect = (PotionEffectType)Registry.EFFECT.get(NamespacedKey.minecraft(effectName.toLowerCase(Locale.ROOT)));
         if (maximum > 0.0 && mob.getHealth() / maximum * 100.0 <= threshold && effect != null && !mob.hasPotionEffect(effect)) {
            int duration = (int)this.skillOptionDouble(unit, "BERSERK", "duration-seconds", 8.0, 1.0, 600.0);
            int amplifier = (int)this.skillOptionDouble(unit, "BERSERK", "amplifier", 0.0, 0.0, 10.0);
            mob.addPotionEffect(new PotionEffect(effect, duration * 20, amplifier, true, true, true));
            mob.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, mob.getLocation().add(0.0, 1.0, 0.0), 12, 0.35, 0.45, 0.35, 0.02);
            this.markUsed(unit, "berserk", this.skillCooldownSeconds(unit, "BERSERK"));
            this.announceSkillCast(unit, owner, "BERSERK", Particle.ANGRY_VILLAGER);
         }
      }

      double ownerMaxHealth = maxHealth(owner);
      if (this.hasSkill(unit, "HEAL_OWNER") && ownerMaxHealth > 0.0 && owner.getHealth() < ownerMaxHealth && this.ready(unit, "heal-owner")) {
         double amount = Math.min(100.0, this.skillOptionDouble(unit, "HEAL_OWNER", "amount", this.healAmount, 0.0, 100.0) * this.skillPowerMultiplier(unit))
            * this.soldierHealingMultiplier(unit);
         owner.setHealth(Math.min(ownerMaxHealth, owner.getHealth() + amount));
         this.markUsed(unit, "heal-owner", this.skillCooldownSeconds(unit, "HEAL_OWNER"));
         this.announceSkillCast(unit, owner, "HEAL_OWNER", Particle.HEART);
      }

      if (this.hasSkill(unit, "HEAL_ALLIES") && this.ready(unit, "heal-allies")) {
         List<LivingEntity> allies = new ArrayList<>();
         String targetRule = this.skillOptionString(unit, "HEAL_ALLIES", "target", "OWNER_AND_ALLIES");
         if (!targetRule.equals("ALLIES")) {
            allies.add(owner);
         }

         for (UUID siblingId : this.getOwnerUnits(unit.owner).stream().map(sibling -> sibling.id).toList()) {
            Mob sibling = this.activeEntities.get(siblingId);
            if (sibling != null && sibling.isValid() && !targetRule.equals("OWNER")) {
               allies.add(sibling);
            }
         }

         boolean healed = false;

         for (LivingEntity ally : allies) {
            double maxHealth = maxHealth(ally);
            if (!ally.isDead()
               && ally.getWorld().equals(owner.getWorld())
               && !(
                  ally.getLocation().distanceSquared(owner.getLocation())
                     > Math.pow(this.skillOptionDouble(unit, "HEAL_ALLIES", "radius-blocks", this.healRadius, 1.0, 32.0), 2.0)
               )
               && !(maxHealth <= 0.0)
               && !(ally.getHealth() >= maxHealth)) {
               double amount = Math.min(
                     100.0, this.skillOptionDouble(unit, "HEAL_ALLIES", "amount", this.healAmount, 0.0, 100.0) * this.skillPowerMultiplier(unit)
                  )
                  * this.soldierHealingMultiplier(unit);
               ally.setHealth(Math.min(maxHealth, ally.getHealth() + amount));
               healed = true;
            }
         }

         if (healed) {
            this.markUsed(unit, "heal-allies", this.skillCooldownSeconds(unit, "HEAL_ALLIES"));
            this.announceSkillCast(unit, owner, "HEAL_ALLIES", Particle.HEART);
         }
      }

      if (this.hasSkill(unit, "BUFF_OWNER") && this.ready(unit, "buff-owner")) {
         this.applyConfiguredBuff(unit, owner, "BUFF_OWNER", "buff-owner");
      }

      if (this.hasSkill(unit, "BUFF_ALLIES") && this.ready(unit, "buff-allies")) {
         double radius = this.skillOptionDouble(unit, "BUFF_ALLIES", "radius-blocks", this.healRadius, 1.0, 32.0);
         String targetRule = this.skillOptionString(unit, "BUFF_ALLIES", "target", "OWNER_AND_ALLIES");
         List<LivingEntity> allies = new ArrayList<>();
         if (!targetRule.equals("ALLIES")) {
            allies.add(owner);
         }

         if (!targetRule.equals("OWNER")) {
            for (UUID siblingId : this.getOwnerUnits(unit.owner).stream().map(sibling -> sibling.id).toList()) {
               Mob sibling = this.activeEntities.get(siblingId);
               if (sibling != null && sibling.isValid()) {
                  allies.add(sibling);
               }
            }
         }

         boolean buffed = false;

         for (LivingEntity ally : allies) {
            if (!ally.isDead() && ally.getWorld().equals(owner.getWorld()) && !(ally.getLocation().distanceSquared(owner.getLocation()) > radius * radius)) {
               buffed |= this.applyConfiguredBuff(unit, ally, "BUFF_ALLIES", "buff-allies");
            }
         }

         if (buffed) {
            this.markUsed(unit, "buff-allies", this.skillCooldownSeconds(unit, "BUFF_ALLIES"));
            this.announceSkillCast(unit, owner, "BUFF_ALLIES", Particle.ENCHANT);
         }
      }

      if ("pyromancer".equals(unit.unitClass)) {
         this.useFireMageSkills(unit, owner, target, mob);
      }
   }

    private void useMedicPotion(Unit unit, Player owner, Mob mob) {
        ItemStack potionItem = unit.equipment.get(org.bukkit.inventory.EquipmentSlot.OFF_HAND);
        if (!isBeneficialThrowablePotion(potionItem) || !ready(unit, "medic-potion")) return;
        double range = readBoundedDouble(settings,
            "classes.medic.potion-support.range-blocks", 12.0, 1.0, 32.0);
        double rangeSquared = range * range;
        List<LivingEntity> allies = new ArrayList<>();
        allies.add(owner);
        for (Unit sibling : getOwnerUnits(unit.owner)) {
            Mob ally = activeEntities.get(sibling.id);
            if (sibling.deployed && ally != null && ally.isValid() && ally != mob) allies.add(ally);
        }
        LivingEntity recipient = allies.stream()
            .filter(ally -> ally.isValid() && !ally.isDead() && ally.getWorld().equals(mob.getWorld()))
            .filter(ally -> ally.getLocation().distanceSquared(mob.getLocation()) <= rangeSquared
                && mob.hasLineOfSight(ally))
            .filter(ally -> potionWouldBenefit(potionItem, ally))
            .sorted(Comparator.comparingDouble(ally -> ally.getLocation().distanceSquared(mob.getLocation())))
            .findFirst().orElse(null);
        if (recipient == null) return;

        ThrownPotion projectile = potionItem.getType() == Material.LINGERING_POTION
            ? mob.launchProjectile(LingeringPotion.class)
            : mob.launchProjectile(SplashPotion.class);
        projectile.setItem(potionItem.clone());
        Vector direction = recipient.getEyeLocation().toVector().subtract(mob.getEyeLocation().toVector());
        direction.setY(direction.getY() + 0.2);
        if (direction.lengthSquared() > 0.0001) projectile.setVelocity(direction.normalize().multiply(0.75));
        markUsed(unit, "medic-potion", readBoundedDouble(settings,
            "classes.medic.potion-support.cooldown-seconds", 12.0, 1.0, 3600.0));
        mob.getWorld().spawnParticle(Particle.HEART, mob.getLocation().add(0.0, 1.0, 0.0),
            6, 0.25, 0.3, 0.25, 0.02);
    }

    private boolean potionWouldBenefit(ItemStack potionItem, LivingEntity target) {
        double maximumHealth = maxHealth(target);
        for (PotionEffect effect : potionEffects(potionItem)) {
            String effectKey = effect.getType().getKey().getKey();
            if ("instant_health".equals(effectKey)) {
                if (maximumHealth > target.getHealth()) return true;
                continue;
            }
            PotionEffect active = target.getPotionEffect(effect.getType());
            if (active == null || active.getAmplifier() < effect.getAmplifier()
                    || active.getDuration() < effect.getDuration() / 2) return true;
        }
        return false;
    }

    private boolean isHealingPotionItem(ItemStack item) {
        return isBeneficialPotion(item) && potionEffects(item).stream()
            .anyMatch(effect -> "instant_health".equals(effect.getType().getKey().getKey()));
    }

    private boolean isBeneficialThrowablePotion(ItemStack item) {
        if (item == null || (item.getType() != Material.SPLASH_POTION
                && item.getType() != Material.LINGERING_POTION)) return false;
        return isBeneficialPotion(item);
    }

    private boolean isBeneficialPotion(ItemStack item) {
        List<PotionEffect> effects = potionEffects(item);
        return !effects.isEmpty() && effects.stream()
            .allMatch(effect -> effect.getType().getCategory() == PotionEffectTypeCategory.BENEFICIAL);
    }

    private List<PotionEffect> potionEffects(ItemStack item) {
        if (item == null || !(item.getItemMeta() instanceof PotionMeta meta)) return List.of();
        List<PotionEffect> effects = new ArrayList<>(meta.getCustomEffects());
        PotionType baseType = meta.getBasePotionType();
        if (baseType != null) effects.addAll(baseType.getPotionEffects());
        return effects;
    }

    private void useFireMageSkills(Unit unit, Player owner, LivingEntity target, Mob mob) {
        if (mob == null || !mob.isValid()) return;
        boolean targetAvailable = target != null
            && isAllowedMercenaryTarget(unit.owner, target, isOwnerCommandedPvpTarget(unit.owner, target));

        if (hasSkill(unit, "FLAME_BOLT") && targetAvailable && ready(unit, "FLAME_BOLT")
                && isWithinFlameRange(unit, mob, target, "FLAME_BOLT", 24.0)) {
            launchFlameBolt(unit, mob, target);
            markUsed(unit, "FLAME_BOLT", flameCooldown(unit, "FLAME_BOLT", 2.0));
            announceSkillCast(unit, owner, "FLAME_BOLT", Particle.FLAME);
        }
        if (hasSkill(unit, "IGNITE") && targetAvailable && ready(unit, "IGNITE")
                && isWithinFlameRange(unit, mob, target, "IGNITE", 24.0)) {
            castIgnite(unit, mob, target);
            markUsed(unit, "IGNITE", flameCooldown(unit, "IGNITE", 6.0));
            announceSkillCast(unit, owner, "IGNITE", Particle.SOUL_FIRE_FLAME);
        }
        if (hasSkill(unit, "FLAME_NOVA") && targetAvailable && ready(unit, "FLAME_NOVA")
                && mob.getWorld().equals(target.getWorld())
                && mob.getLocation().distanceSquared(target.getLocation())
                    <= Math.pow(flameOption(unit, "FLAME_NOVA", "trigger-range-blocks", 5.0, 1.0, 12.0), 2)) {
            castFlameNova(unit, mob);
            markUsed(unit, "FLAME_NOVA", flameCooldown(unit, "FLAME_NOVA", 10.0));
            announceSkillCast(unit, owner, "FLAME_NOVA", Particle.FLAME);
        }
        if (hasSkill(unit, "FIRE_WALL") && targetAvailable && ready(unit, "FIRE_WALL")
                && isWithinFlameRange(unit, mob, target, "FIRE_WALL", 16.0)) {
            if (castFireWall(unit, mob, target)) {
                markUsed(unit, "FIRE_WALL", flameCooldown(unit, "FIRE_WALL", 12.0));
                announceSkillCast(unit, owner, "FIRE_WALL", Particle.FLAME);
            }
        }
        if (hasSkill(unit, "METEOR_STRIKE") && targetAvailable && ready(unit, "METEOR_STRIKE")
                && isWithinFlameRange(unit, mob, target, "METEOR_STRIKE", 32.0)) {
            castMeteorStrike(unit, mob, target);
            markUsed(unit, "METEOR_STRIKE", flameCooldown(unit, "METEOR_STRIKE", 25.0));
            announceSkillCast(unit, owner, "METEOR_STRIKE", Particle.LAVA);
        }
        double maximumHealth = maxHealth(mob);
        double healthPercent = maximumHealth <= 0.0 ? 100.0 : mob.getHealth() / maximumHealth * 100.0;
        if (hasSkill(unit, "PHOENIX_REBIRTH") && targetAvailable && ready(unit, "PHOENIX_REBIRTH")
                && healthPercent <= flameOption(unit, "PHOENIX_REBIRTH",
                    "health-percent-trigger", 65.0, 1.0, 99.0)
                && isWithinFlameRange(unit, mob, target, "PHOENIX_REBIRTH", 32.0)) {
            castPhoenixRebirth(unit, mob, target);
            markUsed(unit, "PHOENIX_REBIRTH", flameCooldown(unit, "PHOENIX_REBIRTH", 90.0));
            announceSkillCast(unit, owner, "PHOENIX_REBIRTH", Particle.FLAME);
        }
    }

    private boolean isWithinFlameRange(Unit unit, Mob mob, LivingEntity target, String skill, double fallback) {
        double range = flameOption(unit, skill, "range-blocks", fallback, 1.0, 64.0);
        return mob.getWorld().equals(target.getWorld())
            && mob.getLocation().distanceSquared(target.getLocation()) <= range * range
            && mob.hasLineOfSight(target);
    }

    private double flameOption(Unit unit, String skill, String option,
                               double fallback, double minimum, double maximum) {
        return skillOptionDouble(unit, skill, option, fallback, minimum, maximum);
    }

    private double flameDamage(Unit unit, String skill, String option, double fallback) {
        return Math.min(100.0, flameOption(unit, skill, option, fallback, 0.0, 100.0)
            * skillPowerMultiplier(unit));
    }

   private double flameCooldown(MercenaryManager.Unit unit, String skill, double fallback) {
      double configured = this.flameOption(unit, skill, "cooldown-seconds", fallback, 1.0, 3600.0);
      double levelProgress = this.maxLevel <= 1 ? 0.0 : (double)(unit.level - 1) / (this.maxLevel - 1);
      double levelAdjusted = Math.max(1.0, configured - this.skillCooldownReductionAtMaxLevel * levelProgress);
      return Math.max(1.0, levelAdjusted * (1.0 - Math.min(20.0, this.soldierGearStats(unit).cooldownReductionPercent()) / 100.0));
   }

    private void launchFlameBolt(Unit unit, Mob mob, LivingEntity target) {
        Projectile projectile;
        if (mob.getEquipment() != null
                && mob.getEquipment().getItemInMainHand().getType() == Material.FIRE_CHARGE) {
            SmallFireball fireball = mob.launchProjectile(SmallFireball.class);
            fireball.setIsIncendiary(false);
            fireball.setYield(0.0f);
            projectile = fireball;
        } else {
            projectile = mob.launchProjectile(Snowball.class);
        }
        flameProjectilesByUnit.computeIfAbsent(unit.id, ignored -> new HashSet<>())
            .add(projectile.getUniqueId());
        Vector direction = target.getEyeLocation().toVector().subtract(mob.getEyeLocation().toVector());
        if (direction.lengthSquared() > 0.0001) {
            projectile.setVelocity(direction.normalize()
                .multiply(flameOption(unit, "FLAME_BOLT", "projectile-speed", 1.8, 0.5, 4.0)));
        }
        projectile.setGravity(false);
        projectile.getPersistentDataContainer().set(flameProjectileKey, PersistentDataType.BYTE, (byte) 1);
        projectile.getPersistentDataContainer().set(flameProjectileDamageKey, PersistentDataType.DOUBLE,
            flameDamage(unit, "FLAME_BOLT", "damage", 6.0));
        double speed = flameOption(unit, "FLAME_BOLT", "projectile-speed", 1.8, 0.5, 4.0);
        int maxAgeTicks = (int) Math.ceil(
            flameOption(unit, "FLAME_BOLT", "range-blocks", 24.0, 1.0, 64.0) / speed) + 10;
        BukkitTask trail = new BukkitRunnable() {
            private int ageTicks;

            @Override
            public void run() {
                if (!projectile.isValid() || projectile.isDead()) {
                    removeTrackedFlameProjectile(mob, projectile);
                    cancel();
                    return;
                }
                if (++ageTicks > maxAgeTicks) {
                    projectile.remove();
                    removeTrackedFlameProjectile(mob, projectile);
                    cancel();
                    return;
                }
                projectile.getWorld().spawnParticle(Particle.FLAME, projectile.getLocation(),
                    2, 0.03, 0.03, 0.03, 0.005);
            }
        }.runTaskTimer(plugin, 0L, 1L);
        trackFireTask(unit, trail);
    }

    private void applyFlameBoltSlow(LivingEntity target, double slowPercent, double durationSeconds) {
        if (slowPercent <= 0.0) return;
        var movementSpeed = target.getAttribute(Attribute.MOVEMENT_SPEED);
        if (movementSpeed == null) return;
        if (movementSpeed.getModifier(flameBoltSlowKey) != null) {
            movementSpeed.removeModifier(flameBoltSlowKey);
        }
        movementSpeed.addModifier(new AttributeModifier(
            flameBoltSlowKey, -slowPercent / 100.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        long expiresAt = System.currentTimeMillis() + Math.round(durationSeconds * 1000.0);
        target.getPersistentDataContainer().set(flameBoltSlowExpiryKey, PersistentDataType.LONG, expiresAt);
        flameBoltSlowExpiries.put(target.getUniqueId(), expiresAt);
    }

    private void castIgnite(Unit unit, Mob mob, LivingEntity target) {
        int targetLimit = (int) flameOption(unit, "IGNITE", "max-spread-targets", 6.0, 1.0, 24.0);
        IgniteContext context = new IgniteContext(targetLimit);
        context.ignitedTargets.add(target.getUniqueId());
        startIgniteTicks(unit, mob, target, context);
    }

    private void startIgniteTicks(Unit unit, Mob mob, LivingEntity target, IgniteContext context) {
        int durationSeconds = (int) flameOption(unit, "IGNITE", "duration-seconds", 8.0, 2.0, 30.0);
        int intervalTicks = (int) Math.min(
            flameOption(unit, "IGNITE", "pulse-interval-ticks", 40.0, 20.0, 200.0), durationSeconds * 20.0);
        int pulseCount = Math.max(1, (int) Math.ceil(durationSeconds * 20.0 / intervalTicks));
        BukkitTask task = new BukkitRunnable() {
            private int pulses;

            @Override
            public void run() {
                if (!isActiveFireCaster(mob) || !target.isValid() || target.isDead()
                        || !canMercenaryDamageTarget(unit.owner, target)
                        || (!isValidTarget(target) && !isOwnerCommandedPvpTarget(unit.owner, target))) {
                    cancel();
                    return;
                }
                target.damage(flameDamage(unit, "IGNITE", "damage-per-pulse", 2.5), mob);
                target.setFireTicks(Math.max(target.getFireTicks(), intervalTicks));
                target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().add(0.0, 1.0, 0.0),
                    8, 0.25, 0.35, 0.25, 0.015);
                spreadIgnite(unit, mob, target, context);
                if (++pulses >= pulseCount) cancel();
            }
        }.runTaskTimer(plugin, intervalTicks, intervalTicks);
        trackFireTask(unit, task);
    }

    private void spreadIgnite(Unit unit, Mob mob, LivingEntity burningTarget, IgniteContext context) {
        if (context.ignitedTargets.size() >= context.targetLimit) return;
        double radius = flameOption(unit, "IGNITE", "spread-radius-blocks", 4.0, 1.0, 12.0);
        for (Entity entity : burningTarget.getNearbyEntities(radius, radius, radius).stream()
                .filter(LivingEntity.class::isInstance)
                .sorted(Comparator.comparingDouble(candidate ->
                    candidate.getLocation().distanceSquared(burningTarget.getLocation())))
                .toList()) {
            LivingEntity next = (LivingEntity) entity;
            if (context.ignitedTargets.contains(next.getUniqueId())
                    || !isAllowedMercenaryTarget(unit.owner, next,
                        isOwnerCommandedPvpTarget(unit.owner, next))
                    || !burningTarget.hasLineOfSight(next)) continue;
            context.ignitedTargets.add(next.getUniqueId());
            next.getWorld().spawnParticle(Particle.FLAME, next.getLocation().add(0.0, 1.0, 0.0),
                10, 0.25, 0.35, 0.25, 0.02);
            startIgniteTicks(unit, mob, next, context);
            break;
        }
    }

    private void castFlameNova(Unit unit, Mob mob) {
        double baseRadius = flameOption(unit, "FLAME_NOVA", "radius-blocks", 5.0, 1.0, 12.0);
        double radius = Math.min(16.0,
            baseRadius
                + Math.floor(unit.level / flameOption(unit, "FLAME_NOVA",
                    "radius-increase-every-levels", 5.0, 1.0, 30.0)));
        double baseHeight = flameOption(unit, "FLAME_NOVA", "height-blocks", 3.0, 1.0, 16.0);
        double heightIncreasePerRadiusBlock = flameOption(unit, "FLAME_NOVA",
            "height-increase-per-radius-block", 0.5, 0.0, 4.0);
        double coneHeight = Math.min(32.0,
            baseHeight + Math.max(0.0, radius - baseRadius) * heightIncreasePerRadiusBlock);
        double radiusReductionPerBlockUp = flameOption(unit, "FLAME_NOVA",
            "radius-reduction-per-block-up", 1.0, 0.1, 4.0);
        int maxTargets = (int) flameOption(unit, "FLAME_NOVA", "max-targets", 24.0, 1.0, 64.0);
        int particleCount = (int) Math.min(64.0, Math.floor(
            flameOption(unit, "FLAME_NOVA", "particle-count", 10.0, 1.0, 64.0)
                + Math.max(0, unit.level - 1) * flameOption(
                    unit, "FLAME_NOVA", "particles-increase-per-level", 1.0, 0.0, 10.0)));
        double rotationSpeed = flameOption(unit, "FLAME_NOVA",
            "particle-rotation-radians-per-tick", 0.3, 0.01, 3.0);
        int particleInterval = (int) flameOption(unit, "FLAME_NOVA", "particle-interval-ticks", 2.0, 1.0, 20.0);
        int pulseInterval = (int) flameOption(unit, "FLAME_NOVA", "damage-interval-ticks", 20.0, 1.0, 100.0);
        long durationTicks = Math.max(1L, Math.round(
            scaledSkillDurationSeconds(unit, "FLAME_NOVA", 3.0, 0.1, 30.0) * 20.0));
        double damage = Math.min(100.0,
            flameDamage(unit, "FLAME_NOVA", "damage-per-second", 1.0)
                + Math.max(0, unit.level - 1) * flameOption(unit, "FLAME_NOVA",
                    "damage-increase-per-level", 0.1, 0.0, 10.0) * skillPowerMultiplier(unit));
        Location origin = mob.getLocation().clone();
        mob.getWorld().playSound(origin, Sound.ENTITY_BLAZE_SHOOT, 0.8f, 0.7f);
        BukkitTask task = new BukkitRunnable() {
            private long elapsedTicks;
            private long nextDamageTicks = pulseInterval;

            @Override
            public void run() {
                if (!isActiveFireCaster(mob) || elapsedTicks > durationTicks) {
                    cancel();
                    return;
                }
                if (elapsedTicks < durationTicks && elapsedTicks % particleInterval == 0L) {
                    Location center = mob.getLocation().clone().add(0.0, 0.2, 0.0);
                    double angleOffset = elapsedTicks * rotationSpeed;
                    for (int index = 0; index < particleCount; index++) {
                        double verticalFraction = particleCount <= 1
                            ? 0.0 : (double) index / (particleCount - 1);
                        double verticalOffset = coneHeight * verticalFraction;
                        double particleRadius = Math.max(0.0,
                            radius - verticalOffset * radiusReductionPerBlockUp);
                        double angle = -angleOffset + Math.PI * 2.0 * index / particleCount;
                        Location particle = center.clone().add(
                            Math.cos(angle) * particleRadius, verticalOffset,
                            Math.sin(angle) * particleRadius);
                        mob.getWorld().spawnParticle(Particle.FLAME, particle,
                            1, 0.02, 0.02, 0.02, 0.005);
                    }
                }
                if (elapsedTicks >= nextDamageTicks && damage > 0.0) {
                    for (LivingEntity victim : flameConeTargets(mob, mob.getLocation(), radius,
                            coneHeight, radiusReductionPerBlockUp, maxTargets)) {
                        victim.damage(damage, mob);
                    }
                    nextDamageTicks += pulseInterval;
                }
                if (elapsedTicks >= durationTicks) {
                    cancel();
                    return;
                }
                elapsedTicks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        trackFireTask(unit, task);
    }

    private boolean castFireWall(Unit unit, Mob mob, LivingEntity target) {
        long durationTicks = Math.max(1L, Math.round(
            scaledSkillDurationSeconds(unit, "FIRE_WALL", 3.0, 0.1, 30.0) * 20.0));
        Material wallMaterial = Material.matchMaterial(skillOptionString(unit,
            "FIRE_WALL", "block-material", "MAGMA_BLOCK"));
        if (wallMaterial == null || !wallMaterial.isSolid() || wallMaterial.isAir()) return false;
        int placed = 0;
        int floorY = target.getLocation().getBlockY() - 1;
        for (Block block : enclosureBlocks(target.getLocation())) {
            if (placeTemporaryFireBlock(unit, block, wallMaterial, durationTicks, block.getY() == floorY)) {
                placed++;
            }
        }
        return placed > 0;
    }

    private boolean placeTemporaryFireBlock(Unit unit, Block block, Material type,
                                            long durationTicks, boolean replaceFloor) {
        if (!block.getType().isAir() && block.getType() != Material.WATER
                && (!replaceFloor || !block.getType().isSolid()
                    || block.getState() instanceof org.bukkit.block.TileState)) return false;
        String key = block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
        int limit = (int) flameOptionForClass("FIRE_WALL", "temporary-block-limit", 96.0, 1.0, 512.0);
        if (temporaryFireBlocks.containsKey(key) || temporaryFrostBlocks.containsKey(key)
                || temporaryFireBlocks.size() >= limit) return false;
        TemporaryFrostBlock temporary = new TemporaryFrostBlock(key, block, type, unit.id);
        block.setType(type, false);
        temporaryFireBlocks.put(key, temporary);
        temporary.removalTask = Bukkit.getScheduler().runTaskLater(plugin,
            () -> restoreTemporaryFireBlock(temporary), Math.max(1L, durationTicks));
        return true;
    }

    private double flameOptionForClass(String skill, String option,
                                       double fallback, double minimum, double maximum) {
        return readBoundedDouble(settings, "classes.pyromancer.skill-settings." + skill + "." + option,
            fallback, minimum, maximum);
    }

    private void restoreTemporaryFireBlock(TemporaryFrostBlock temporary) {
        temporaryFireBlocks.remove(temporary.key);
        Block block = temporary.world.getBlockAt(temporary.x, temporary.y, temporary.z);
        if (block.getType() == temporary.temporaryType) block.setBlockData(temporary.originalData, false);
    }

    private List<LivingEntity> flameTargets(Mob source, Location center, double radius, int maxTargets) {
        return flameTargets(source, center, radius, maxTargets, true);
    }

    private List<LivingEntity> flameTargets(Mob source, Location center, double radius,
                                            int maxTargets, boolean requireLineOfSight) {
        double radiusSquared = radius * radius;
        return center.getWorld().getNearbyEntities(center, radius, radius, radius).stream()
            .filter(LivingEntity.class::isInstance)
            .map(LivingEntity.class::cast)
            .filter(target -> isAllowedMercenaryTarget(getOwnerId(source), target,
                isOwnerCommandedPvpTarget(getOwnerId(source), target)))
            .filter(target -> source.getWorld().equals(target.getWorld())
                && target.getLocation().distanceSquared(center) <= radiusSquared
                && (!requireLineOfSight || source.hasLineOfSight(target)))
            .sorted(Comparator.comparingDouble(target -> target.getLocation().distanceSquared(center)))
            .limit(maxTargets)
            .toList();
    }

    private List<LivingEntity> flameConeTargets(Mob source, Location base, double radius,
                                                double height, double radiusReductionPerBlockUp,
                                                int maxTargets) {
        Location queryCenter = base.clone().add(0.0, height / 2.0, 0.0);
        UUID ownerId = getOwnerId(source);
        return base.getWorld().getNearbyEntities(queryCenter, radius, height / 2.0, radius).stream()
            .filter(LivingEntity.class::isInstance)
            .map(LivingEntity.class::cast)
            .filter(target -> target != source && !target.getUniqueId().equals(ownerId)
                && isAllowedMercenaryTarget(ownerId, target,
                    isOwnerCommandedPvpTarget(ownerId, target)))
            .filter(target -> source.getWorld().equals(target.getWorld())
                && isInsideFlameCone(base, target.getLocation(), radius, height,
                    radiusReductionPerBlockUp)
                && source.hasLineOfSight(target))
            .sorted(Comparator.comparingDouble(target ->
                target.getLocation().distanceSquared(base)))
            .limit(maxTargets)
            .toList();
    }

    private boolean isInsideFlameCone(Location base, Location target, double radius,
                                      double height, double radiusReductionPerBlockUp) {
        double verticalOffset = target.getY() - base.getY();
        if (verticalOffset < 0.0 || verticalOffset > height) return false;
        double radiusAtHeight = radius - verticalOffset * radiusReductionPerBlockUp;
        if (radiusAtHeight < 0.0) return false;
        double offsetX = target.getX() - base.getX();
        double offsetZ = target.getZ() - base.getZ();
        return offsetX * offsetX + offsetZ * offsetZ <= radiusAtHeight * radiusAtHeight;
    }

    private void castMeteorStrike(Unit unit, Mob mob, LivingEntity target) {
        Location impact = target.getLocation().clone();
        World world = impact.getWorld();
        int count = Math.min(32, (int) flameOption(unit, "METEOR_STRIKE",
            "fireball-count", 3.0, 1.0, 32.0)
            + (int) (unit.level / flameOption(unit, "METEOR_STRIKE",
                "fireballs-increase-every-levels", 3.0, 1.0, 30.0)));
        double areaRadius = Math.min(16.0, flameOption(unit, "METEOR_STRIKE",
            "area-radius-blocks", 3.0, 1.0, 12.0)
            + Math.floor(unit.level / flameOption(unit, "METEOR_STRIKE",
                "area-radius-increase-every-levels", 5.0, 1.0, 30.0)));
        int warningTicks = (int) Math.round(flameOption(unit, "METEOR_STRIKE",
            "warning-delay-seconds", 2.0, 0.1, 30.0) * 20.0);
        int particleCount = (int) flameOption(unit, "METEOR_STRIKE",
            "warning-particle-count", 32.0, 8.0, 128.0);
        int particleInterval = (int) flameOption(unit, "METEOR_STRIKE",
            "warning-particle-interval-ticks", 5.0, 1.0, 40.0);
        List<Location> impacts = meteorImpactLocations(impact, areaRadius, count);
        int fireballInterval = (int) flameOption(unit, "METEOR_STRIKE",
            "fireball-interval-ticks", 10.0, 1.0, 100.0);
        double height = flameOption(unit, "METEOR_STRIKE",
            "fall-height-blocks", 16.0, 4.0, 80.0);
        double speed = flameOption(unit, "METEOR_STRIKE", "fall-speed", 1.2, 0.2, 4.0);
        double damage = flameDamage(unit, "METEOR_STRIKE", "damage", 12.0);
        double hitRadius = flameOption(unit, "METEOR_STRIKE", "impact-radius-blocks", 1.5, 0.5, 8.0);
        int maxTargets = (int) flameOption(unit, "METEOR_STRIKE", "max-targets-per-fireball", 8.0, 1.0, 64.0);
        int burnTicks = (int) flameOption(unit, "METEOR_STRIKE",
            "burn-duration-seconds", 2.0, 0.0, 15.0) * 20;
        world.playSound(impact, Sound.ENTITY_BLAZE_SHOOT, 0.8f, 0.6f);

        BukkitTask markerTask = new BukkitRunnable() {
            private int elapsedTicks;

            @Override
            public void run() {
                if (!isActiveFireCaster(mob) || elapsedTicks >= warningTicks) {
                    cancel();
                    return;
                }
                drawMeteorWarning(world, impact, areaRadius, particleCount);
                elapsedTicks += particleInterval;
            }
        }.runTaskTimer(plugin, 0L, particleInterval);
        trackFireTask(unit, markerTask);
        scheduleFireTask(unit, warningTicks, () -> launchMeteorFireball(unit, mob, impacts, 0,
            fireballInterval, height, speed, hitRadius, maxTargets, damage, burnTicks));
    }

    private List<Location> meteorImpactLocations(Location center, double radius, int count) {
        List<Location> locations = new ArrayList<>(count);
        locations.add(center.clone());
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int index = 1; index < count; index++) {
            double angle = random.nextDouble(Math.PI * 2.0);
            double distance = Math.sqrt(random.nextDouble()) * radius;
            locations.add(center.clone().add(Math.cos(angle) * distance, 0.0,
                Math.sin(angle) * distance));
        }
        for (int index = locations.size() - 1; index > 0; index--) {
            int swapIndex = random.nextInt(index + 1);
            Location current = locations.get(index);
            locations.set(index, locations.get(swapIndex));
            locations.set(swapIndex, current);
        }
        return locations;
    }

    private void drawMeteorWarning(World world, Location center, double radius, int count) {
        Particle.DustOptions redFire = new Particle.DustOptions(Color.fromRGB(255, 45, 0), 1.2f);
        for (int index = 0; index < count; index++) {
            double angle = Math.PI * 2.0 * index / count;
            Location point = center.clone().add(Math.cos(angle) * radius, 0.15,
                Math.sin(angle) * radius);
            world.spawnParticle(Particle.DUST, point, 1, 0.03, 0.02, 0.03, 0.0, redFire);
            world.spawnParticle(Particle.FLAME, point, 1, 0.02, 0.02, 0.02, 0.005);
        }
    }

    private void launchMeteorFireball(Unit unit, Mob mob, List<Location> impacts, int index,
                                      int intervalTicks, double height, double speed, double hitRadius,
                                      int maxTargets, double damage, int burnTicks) {
        if (!isActiveFireCaster(mob) || index >= impacts.size()) return;
        Location impact = impacts.get(index);
        double spawnY = Math.min(impact.getWorld().getMaxHeight() - 1.0, impact.getY() + height);
        if (spawnY <= impact.getY()) return;
        Location spawn = impact.clone();
        spawn.setY(spawnY);
        SmallFireball fireball = impact.getWorld().spawn(spawn, SmallFireball.class);
        fireball.setShooter(mob);
        fireball.setIsIncendiary(false);
        fireball.setYield(0.0f);
        fireball.setGravity(false);
        fireball.setDirection(new Vector(0.0, -1.0, 0.0));
        fireball.setVelocity(new Vector(0.0, -speed, 0.0));
        fireball.getPersistentDataContainer().set(meteorFireballKey, PersistentDataType.BYTE, (byte) 1);
        fireball.getPersistentDataContainer().set(meteorFireballDamageKey, PersistentDataType.DOUBLE, damage);
        fireball.getPersistentDataContainer().set(meteorFireballRadiusKey, PersistentDataType.DOUBLE, hitRadius);
        fireball.getPersistentDataContainer().set(meteorFireballMaxTargetsKey,
            PersistentDataType.INTEGER, maxTargets);
        fireball.getPersistentDataContainer().set(meteorFireballBurnTicksKey,
            PersistentDataType.INTEGER, burnTicks);
        flameProjectilesByUnit.computeIfAbsent(unit.id, ignored -> new HashSet<>())
            .add(fireball.getUniqueId());
        if (index + 1 < impacts.size()) {
            scheduleFireTask(unit, intervalTicks, () -> launchMeteorFireball(unit, mob, impacts,
                index + 1, intervalTicks, height, speed, hitRadius, maxTargets, damage, burnTicks));
        }
    }

    private void castPhoenixRebirth(Unit unit, Mob mob, LivingEntity target) {
        double maximumHealth = maxHealth(mob);
        if (maximumHealth > 0.0) {
            double healPercent = flameOption(unit, "PHOENIX_REBIRTH", "heal-percent", 30.0, 1.0, 100.0);
            mob.setHealth(Math.min(maximumHealth, mob.getHealth() + maximumHealth * healPercent / 100.0));
        }
        int immunitySeconds = (int) flameOption(unit, "PHOENIX_REBIRTH",
            "immunity-duration-seconds", 2.0, 1.0, 5.0);
        phoenixImmunityExpiries.put(mob.getUniqueId(),
            System.currentTimeMillis() + immunitySeconds * 1000L);
        Location start = mob.getLocation().clone();
        mob.getWorld().spawnParticle(Particle.FLAME, start.add(0.0, 1.0, 0.0),
            50, 0.7, 0.8, 0.7, 0.08);
        int immunityTicks = immunitySeconds * 20;
        scheduleFireTask(unit, immunityTicks, () -> {
            if (!isActiveFireCaster(mob)) return;
            LivingEntity currentTarget = target.isValid() && !target.isDead() ? target : null;
            if (currentTarget == null || !isAllowedMercenaryTarget(unit.owner, currentTarget,
                    isOwnerCommandedPvpTarget(unit.owner, currentTarget))) return;
            Vector charge = currentTarget.getLocation().toVector().subtract(mob.getLocation().toVector());
            charge.setY(Math.max(-0.1, Math.min(0.5, charge.getY())));
            if (charge.lengthSquared() > 0.0001) {
                mob.setVelocity(charge.normalize().multiply(
                    flameOption(unit, "PHOENIX_REBIRTH", "dash-speed", 1.8, 0.5, 3.5)));
            }
            mob.getWorld().spawnParticle(Particle.FLAME, mob.getLocation().add(0.0, 1.0, 0.0),
                35, 0.5, 0.6, 0.5, 0.06);
            scheduleFireTask(unit, (long) flameOption(unit, "PHOENIX_REBIRTH",
                "dash-impact-delay-ticks", 10.0, 1.0, 40.0), () -> {
                if (!isActiveFireCaster(mob)) return;
                Location impact = mob.getLocation().clone();
                double radius = flameOption(unit, "PHOENIX_REBIRTH", "radius-blocks", 8.0, 1.0, 16.0);
                int maxTargets = (int) flameOption(unit, "PHOENIX_REBIRTH", "max-targets", 32.0, 1.0, 64.0);
                mob.getWorld().spawnParticle(Particle.EXPLOSION, impact, 4,
                    radius / 3.0, 0.5, radius / 3.0, 0.0);
                mob.getWorld().spawnParticle(Particle.FLAME, impact, 120,
                    radius / 2.0, 1.0, radius / 2.0, 0.08);
                mob.getWorld().playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.55f);
                for (LivingEntity victim : flameTargets(mob, impact, radius, maxTargets)) {
                    victim.damage(flameDamage(unit, "PHOENIX_REBIRTH", "damage", 18.0), mob);
                    int burnSeconds = (int) flameOption(unit, "PHOENIX_REBIRTH",
                        "burn-duration-seconds", 5.0, 1.0, 15.0);
                    victim.setFireTicks(Math.max(victim.getFireTicks(), burnSeconds * 20));
                }
            });
        });
    }

    private void trackFireTask(Unit unit, BukkitTask task) {
        if (task == null) return;
        fireSkillTasks.removeIf(BukkitTask::isCancelled);
        fireTasksByUnit.values().forEach(tasks -> tasks.removeIf(BukkitTask::isCancelled));
        fireSkillTasks.add(task);
        fireTasksByUnit.computeIfAbsent(unit.id, ignored -> new HashSet<>()).add(task);
    }

    private void scheduleFireTask(Unit unit, long delayTicks, Runnable action) {
        BukkitTask[] scheduledTask = new BukkitTask[1];
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    action.run();
                } finally {
                    cancel();
                    forgetFireTask(unit.id, scheduledTask[0]);
                }
            }
        }.runTaskLater(plugin, delayTicks);
        scheduledTask[0] = task;
        trackFireTask(unit, task);
    }

    private void forgetFireTask(UUID unitId, BukkitTask task) {
        if (task == null) return;
        fireSkillTasks.remove(task);
        Set<BukkitTask> unitTasks = fireTasksByUnit.get(unitId);
        if (unitTasks == null) return;
        unitTasks.remove(task);
        if (unitTasks.isEmpty()) fireTasksByUnit.remove(unitId);
    }

    private void removeTrackedFlameProjectile(Mob mob, Projectile projectile) {
        UUID unitId = getUnitId(mob);
        if (unitId == null) return;
        Set<UUID> projectiles = flameProjectilesByUnit.get(unitId);
        if (projectiles == null) return;
        projectiles.remove(projectile.getUniqueId());
        if (projectiles.isEmpty()) flameProjectilesByUnit.remove(unitId);
    }

    private boolean isActiveFireCaster(Mob mob) {
        UUID unitId = getUnitId(mob);
        Unit unit = unitId == null ? null : units.get(unitId);
        return unit != null && unit.deployed && activeEntities.get(unit.id) == mob
            && mob.isValid() && !mob.isDead();
    }

    private void restoreFlameBoltSlowState() {
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) restoreFlameBoltSlowState(entity);
        }
    }

    private void restoreFlameBoltSlowState(LivingEntity entity) {
        Long expiresAt = entity.getPersistentDataContainer()
            .get(flameBoltSlowExpiryKey, PersistentDataType.LONG);
        if (expiresAt == null) return;
        if (expiresAt <= System.currentTimeMillis()) {
            removeFlameBoltSlow(entity);
            return;
        }
        flameBoltSlowExpiries.put(entity.getUniqueId(), expiresAt);
    }

    private void restoreIcePrisonSlowState() {
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) restoreIcePrisonSlowState(entity);
        }
    }

    private void restoreIcePrisonSlowState(LivingEntity entity) {
        Long expiresAt = entity.getPersistentDataContainer()
            .get(icePrisonSlowExpiryKey, PersistentDataType.LONG);
        if (expiresAt == null) return;
        if (expiresAt <= System.currentTimeMillis()) {
            removeIcePrisonSlow(entity);
            return;
        }
        icePrisonSlowExpiries.put(entity.getUniqueId(), expiresAt);
    }

    private void cleanupFireExpiries(boolean force) {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> entry : new HashMap<>(phoenixImmunityExpiries).entrySet()) {
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (force || entry.getValue() <= now || entity == null || !entity.isValid()) {
                phoenixImmunityExpiries.remove(entry.getKey());
            }
        }
        for (Map.Entry<UUID, Long> entry : new HashMap<>(flameBoltSlowExpiries).entrySet()) {
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (!force && entry.getValue() > now) {
                continue;
            }
            if (entity instanceof LivingEntity living) removeFlameBoltSlow(living);
            flameBoltSlowExpiries.remove(entry.getKey());
        }
        for (Map.Entry<UUID, Long> entry : new HashMap<>(icePrisonSlowExpiries).entrySet()) {
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (!force && entry.getValue() > now) continue;
            if (entity instanceof LivingEntity living) removeIcePrisonSlow(living);
            icePrisonSlowExpiries.remove(entry.getKey());
        }
    }

    private void removeFlameBoltSlow(LivingEntity target) {
        var movementSpeed = target.getAttribute(Attribute.MOVEMENT_SPEED);
        if (movementSpeed != null && movementSpeed.getModifier(flameBoltSlowKey) != null) {
            movementSpeed.removeModifier(flameBoltSlowKey);
        }
        target.getPersistentDataContainer().remove(flameBoltSlowExpiryKey);
        flameBoltSlowExpiries.remove(target.getUniqueId());
    }

    private void cleanupFireEffects() {
        for (BukkitTask task : fireSkillTasks) task.cancel();
        fireSkillTasks.clear();
        fireTasksByUnit.clear();
        for (Set<UUID> projectileIds : flameProjectilesByUnit.values()) {
            for (UUID projectileId : projectileIds) {
                Entity projectile = Bukkit.getEntity(projectileId);
                if (projectile != null) projectile.remove();
            }
        }
        flameProjectilesByUnit.clear();
        for (TemporaryFrostBlock temporary : new ArrayList<>(temporaryFireBlocks.values())) {
            if (temporary.removalTask != null) temporary.removalTask.cancel();
            restoreTemporaryFireBlock(temporary);
        }
        cleanupFireExpiries(true);
    }

    private void cleanupFireStateForUnit(UUID unitId) {
        Set<BukkitTask> frostTasks = frostTasksByUnit.remove(unitId);
        if (frostTasks != null) {
            for (BukkitTask task : frostTasks) {
                task.cancel();
                frostSkillTasks.remove(task);
            }
        }
        Set<BukkitTask> tasks = fireTasksByUnit.remove(unitId);
        if (tasks != null) {
            for (BukkitTask task : tasks) {
                task.cancel();
                fireSkillTasks.remove(task);
            }
        }
        Set<UUID> projectileIds = flameProjectilesByUnit.remove(unitId);
        if (projectileIds != null) {
            for (UUID projectileId : projectileIds) {
                Entity projectile = Bukkit.getEntity(projectileId);
                if (projectile != null) projectile.remove();
            }
        }
        for (TemporaryFrostBlock temporary : new ArrayList<>(temporaryFireBlocks.values())) {
            if (!unitId.equals(temporary.ownerUnitId)) continue;
            if (temporary.removalTask != null) temporary.removalTask.cancel();
            restoreTemporaryFireBlock(temporary);
        }
        for (TemporaryFrostBlock temporary : new ArrayList<>(temporaryFrostBlocks.values())) {
            if (!unitId.equals(temporary.ownerUnitId)) continue;
            if (temporary.removalTask != null) temporary.removalTask.cancel();
            restoreTemporaryFrostBlock(temporary);
        }
        Unit unit = units.get(unitId);
        Mob mob = unit == null ? null : activeEntities.get(unitId);
        if (mob != null) phoenixImmunityExpiries.remove(mob.getUniqueId());
    }

    private ItemStack findStoredArrow(Unit unit) {
        ItemStack offhand = unit.equipment.get(org.bukkit.inventory.EquipmentSlot.OFF_HAND);
        if (isArrowItem(offhand)) return offhand;
        for (ItemStack item : unit.inventory) {
            if (isArrowItem(item)) return item;
        }
        return null;
    }

    private boolean isWithinConfiguredSkillRange(Unit unit, Mob mob, LivingEntity target, String skill) {
        if (!"ranger".equals(unit.unitClass)) return true;
        double defaultRange = skillOptionDouble(unit, "RANGED_ATTACK", "range-blocks", 16.0, 4.0, 64.0);
        double range = skillOptionDouble(unit, skill, "range-blocks", defaultRange, 4.0, 64.0)
            + soldierGearStats(unit).attackRangeBlocks();
        return mob.getWorld().equals(target.getWorld())
            && mob.getLocation().distanceSquared(target.getLocation()) <= range * range;
    }

    private boolean isArrowItem(ItemStack item) {
        return item != null && Set.of(Material.ARROW, Material.SPECTRAL_ARROW, Material.TIPPED_ARROW)
            .contains(item.getType());
    }

    private AbstractArrow launchConfiguredArrow(Mob mob, Unit unit) {
        ItemStack ammunition = findStoredArrow(unit);
        AbstractArrow arrow;
        boolean scout = "ranger".equals(unit.unitClass);
        if (ammunition == null) {
            arrow = scout ? mob.launchProjectile(SpectralArrow.class) : mob.launchProjectile(Arrow.class);
        } else if (ammunition.getType() == Material.SPECTRAL_ARROW) {
            arrow = mob.launchProjectile(SpectralArrow.class);
            if (scout) consumeStoredArrow(unit, ammunition);
        } else if (ammunition.getType() == Material.TIPPED_ARROW) {
            TippedArrow tippedArrow = mob.launchProjectile(TippedArrow.class);
            if (ammunition.getItemMeta() instanceof PotionMeta potionMeta) {
                PotionType potionType = potionMeta.getBasePotionType();
                if (potionType != null) tippedArrow.setBasePotionType(potionType);
                for (PotionEffect effect : potionMeta.getCustomEffects()) {
                    tippedArrow.addCustomEffect(effect, true);
                }
            }
            arrow = tippedArrow;
            if (scout) consumeStoredArrow(unit, ammunition);
        } else {
            arrow = mob.launchProjectile(Arrow.class);
            if (scout) consumeStoredArrow(unit, ammunition);
        }
        return arrow;
    }

    private void consumeStoredArrow(Unit unit, ItemStack ammunition) {
        org.bukkit.inventory.EquipmentSlot offhandSlot = org.bukkit.inventory.EquipmentSlot.OFF_HAND;
        if (unit.equipment.get(offhandSlot) == ammunition) {
            ItemStack remaining = decrementStack(ammunition);
            if (remaining == null) unit.equipment.remove(offhandSlot);
            else unit.equipment.put(offhandSlot, remaining);
            return;
        }
        for (int slot = 0; slot < unit.inventory.length; slot++) {
            if (unit.inventory[slot] == ammunition) {
                unit.inventory[slot] = decrementStack(ammunition);
                return;
            }
        }
    }

    private ItemStack decrementStack(ItemStack item) {
        if (item.getAmount() <= 1) return null;
        ItemStack remaining = item.clone();
        remaining.setAmount(item.getAmount() - 1);
        return remaining;
    }

    private int countStoredArrows(Unit unit) {
        int count = 0;
        ItemStack offhand = unit.equipment.get(org.bukkit.inventory.EquipmentSlot.OFF_HAND);
        if (isArrowItem(offhand)) count += offhand.getAmount();
        for (ItemStack item : unit.inventory) {
            if (isArrowItem(item)) count += item.getAmount();
        }
        return count;
    }

    private void persistAmmoIfConsumed(Unit unit, int previousCount) {
        if (countStoredArrows(unit) < previousCount && !saveData()) {
            plugin.getLogger().warning("[Army] Could not persist consumed arrows for mercenary " + unit.id + ".");
        }
    }

    private void fireIlluminationVolley(Unit unit, Mob mob, LivingEntity target, Player owner) {
        String skill = "ILLUMINATION";
        int baseArrowCount = (int) skillOptionDouble(unit, skill, "base-arrow-count", 1.0, 1.0, 20.0);
        int levelsPerArrow = (int) skillOptionDouble(unit, skill, "levels-per-extra-arrow", 5.0, 1.0, 30.0);
        int arrowCount = Math.min(20, baseArrowCount + unit.level / levelsPerArrow + rangerSpyglassArrowBonus(unit));
        double radius = skillOptionDouble(unit, skill, "spread-radius-blocks", 5.0, 0.0, 16.0);
        double angle = Math.toRadians(skillOptionDouble(unit, skill, "upward-angle-degrees", 55.0, 10.0, 85.0));
        double speedMultiplier = skillOptionDouble(unit, skill, "speed-multiplier", 1.0, 0.5, 2.0);
        Vector origin = mob.getEyeLocation().toVector();
        Vector targetPosition = target.getLocation().toVector();
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();

        for (int index = 0; index < arrowCount; index++) {
            double spreadAngle = random.nextDouble(Math.PI * 2.0);
            double spreadDistance = radius * Math.sqrt(random.nextDouble());
            Vector landingPoint = targetPosition.clone().add(new Vector(
                Math.cos(spreadAngle) * spreadDistance, 0.0, Math.sin(spreadAngle) * spreadDistance));
            Vector horizontal = landingPoint.subtract(origin.clone());
            horizontal.setY(0.0);
            double horizontalDistance = Math.max(1.0, horizontal.length());
            double sinDoubleAngle = Math.max(0.1, Math.sin(angle * 2.0));
            double speed = Math.sqrt(horizontalDistance * 0.05 / sinDoubleAngle) * speedMultiplier;
            speed = Math.max(0.5, Math.min(4.0, speed));

            Arrow arrow = mob.launchProjectile(Arrow.class);
            Vector velocity = horizontal.normalize().multiply(speed * Math.cos(angle));
            velocity.setY(speed * Math.sin(angle));
            arrow.setVelocity(velocity);
            arrow.setDamage(0.0);
            arrow.setFireTicks(1_200);
            arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
            arrow.getPersistentDataContainer().set(illuminationArrowKey, PersistentDataType.BYTE, (byte) 1);
        }

        markUsed(unit, "illumination", illuminationCooldownSeconds(unit));
        announceSkillCast(unit, owner, skill, Particle.FLAME);
    }

   private double illuminationCooldownSeconds(MercenaryManager.Unit unit) {
      double cooldown = this.skillOptionDouble(unit, "ILLUMINATION", "cooldown-seconds", 30.0, 1.0, 3600.0);
      double reduction = this.skillOptionDouble(unit, "ILLUMINATION", "cooldown-reduction-per-level-seconds", 0.2, 0.0, 30.0) * (unit.level - 1);
      double levelAdjusted = Math.max(1.0, cooldown - reduction);
      return Math.max(1.0, levelAdjusted * (1.0 - Math.min(20.0, this.soldierGearStats(unit).cooldownReductionPercent()) / 100.0));
   }

    private boolean canUseIlluminationInCurrentLight(Mob mob) {
        if (!settings.getBoolean("classes.ranger.skill-settings.ILLUMINATION.darkness-only", true)) return true;
        long time = mob.getWorld().getTime();
        if (time >= 13_000L && time <= 23_000L) return true;
        int threshold = (int) readBoundedDouble(settings,
            "classes.ranger.skill-settings.ILLUMINATION.low-light-threshold", 7.0, 0.0, 15.0);
        return mob.getLocation().getBlock().getLightLevel() <= threshold;
    }

   @EventHandler
    public void onIlluminationArrowHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)
                || !arrow.getPersistentDataContainer().has(illuminationArrowKey, PersistentDataType.BYTE)) return;
        Block hitBlock = event.getHitBlock();
        if (hitBlock == null) return;

        Block torchBlock = hitBlock.getRelative(org.bukkit.block.BlockFace.UP);
        if (!hitBlock.getType().isSolid() || !torchBlock.getType().isAir()) return;
        torchBlock.setType(Material.TORCH, false);
        Unit unit = null;
        if (arrow.getShooter() instanceof Mob mob) {
            UUID unitId = getUnitId(mob);
            unit = unitId == null ? null : units.get(unitId);
        }
        int duration = Math.min(600, (int) skillOptionDoubleForRanger(
            "ILLUMINATION", "torch-duration-seconds", 30.0, 1.0, 600.0) + rangerSpyglassTorchBonus(unit));
        TemporaryTorch temporaryTorch = new TemporaryTorch(UUID.randomUUID(),
            torchBlock.getWorld().getName(), torchBlock.getX(), torchBlock.getY(), torchBlock.getZ(),
            System.currentTimeMillis() + duration * 1000L);
        temporaryTorches.put(temporaryTorch.id, temporaryTorch);
        saveTemporaryTorches();
        scheduleTorchRemoval(temporaryTorch);
    }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTemporaryTorchBreak(BlockBreakEvent event) {
        if (isTemporaryTorchOrSupport(event.getBlock())) event.setCancelled(true);
    }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTemporaryTorchPhysics(BlockPhysicsEvent event) {
        if (isTemporaryTorch(event.getBlock())) event.setCancelled(true);
    }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTemporaryTorchWaterFlow(BlockFromToEvent event) {
        if (isTemporaryTorch(event.getToBlock())) event.setCancelled(true);
    }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTemporaryTorchEntityExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isTemporaryTorchOrSupport);
    }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTemporaryTorchBlockExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isTemporaryTorchOrSupport);
    }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTemporaryTorchPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isTemporaryTorchOrSupport)) event.setCancelled(true);
    }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTemporaryTorchPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isTemporaryTorchOrSupport)) event.setCancelled(true);
    }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTemporaryFrostBlockBreak(BlockBreakEvent event) {
        if (isTemporaryFrostBlock(event.getBlock())) event.setCancelled(true);
    }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTemporaryFrostBlockPhysics(BlockPhysicsEvent event) {
        if (isTemporaryFrostBlock(event.getBlock())) event.setCancelled(true);
    }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTemporaryFrostBlockWaterFlow(BlockFromToEvent event) {
        if (isTemporaryFrostBlock(event.getToBlock())) event.setCancelled(true);
    }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTemporaryFrostBlockEntityExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isTemporaryFrostBlock);
    }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTemporaryFrostBlockBlockExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isTemporaryFrostBlock);
    }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTemporaryFrostBlockPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isTemporaryFrostBlock)) event.setCancelled(true);
    }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTemporaryFrostBlockPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isTemporaryFrostBlock)) event.setCancelled(true);
    }

    private boolean isTemporaryFrostBlock(Block block) {
        String key = block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
        return temporaryFrostBlocks.containsKey(key) || temporaryFireBlocks.containsKey(key);
    }

    private boolean isTemporaryTorch(Block block) {
        return temporaryTorches.values().stream().anyMatch(torch ->
            torch.worldName.equals(block.getWorld().getName())
                && torch.x == block.getX() && torch.y == block.getY() && torch.z == block.getZ());
    }

    private boolean isTemporaryTorchOrSupport(Block block) {
        return temporaryTorches.values().stream().anyMatch(torch ->
            torch.worldName.equals(block.getWorld().getName())
                && torch.x == block.getX() && torch.z == block.getZ()
                && (torch.y == block.getY() || torch.y - 1 == block.getY()));
    }

    private double skillOptionDoubleForRanger(String skill, String key, double fallback,
                                               double minimum, double maximum) {
        return readBoundedDouble(settings, "classes.ranger.skill-settings." + skill + "." + key,
            fallback, minimum, maximum);
    }

   @EventHandler
    public void onTemporaryTorchChunkLoad(ChunkLoadEvent event) {
        for (TemporaryTorch temporaryTorch : temporaryTorches.values()) {
            if (temporaryTorch.worldName.equals(event.getWorld().getName())
                    && temporaryTorch.x >> 4 == event.getChunk().getX()
                    && temporaryTorch.z >> 4 == event.getChunk().getZ()) {
                scheduleTorchRemoval(temporaryTorch);
            }
        }
    }

    private void loadTemporaryTorches() {
        if (!torchFile.isFile()) return;
        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(torchFile);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().log(Level.SEVERE, "[Army] Could not load temporary Ranger torches.", exception);
            return;
        }
        if (!loaded.isConfigurationSection("torches")) return;
        for (String id : loaded.getConfigurationSection("torches").getKeys(false)) {
            try {
                TemporaryTorch torch = new TemporaryTorch(UUID.fromString(id),
                    loaded.getString("torches." + id + ".world", ""),
                    loaded.getInt("torches." + id + ".x"), loaded.getInt("torches." + id + ".y"),
                    loaded.getInt("torches." + id + ".z"), loaded.getLong("torches." + id + ".expiresAt"));
                temporaryTorches.put(torch.id, torch);
                scheduleTorchRemoval(torch);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("[Army] Ignoring invalid temporary Ranger torch ID: " + id);
            }
        }
    }

    private void scheduleTorchRemoval(TemporaryTorch torch) {
        if (torchRemovalTasks.containsKey(torch.id)) return;
        World world = Bukkit.getWorld(torch.worldName);
        if (world == null || !world.isChunkLoaded(torch.x >> 4, torch.z >> 4)) return;
        long delay = Math.max(1L, (torch.expiresAt - System.currentTimeMillis() + 49L) / 50L);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> expireTemporaryTorch(torch), delay);
        torchRemovalTasks.put(torch.id, task);
    }

    private void expireTemporaryTorch(TemporaryTorch torch) {
        torchRemovalTasks.remove(torch.id);
        World world = Bukkit.getWorld(torch.worldName);
        if (world == null || !world.isChunkLoaded(torch.x >> 4, torch.z >> 4)) return;
        Block block = world.getBlockAt(torch.x, torch.y, torch.z);
        if (block.getType() == Material.TORCH) block.setType(Material.AIR, false);
        temporaryTorches.remove(torch.id);
        saveTemporaryTorches();
    }

    private void saveTemporaryTorches() {
        YamlConfiguration output = new YamlConfiguration();
        for (TemporaryTorch torch : temporaryTorches.values()) {
            String path = "torches." + torch.id;
            output.set(path + ".world", torch.worldName);
            output.set(path + ".x", torch.x);
            output.set(path + ".y", torch.y);
            output.set(path + ".z", torch.z);
            output.set(path + ".expiresAt", torch.expiresAt);
        }
        try {
            output.save(torchFile);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "[Army] Could not save temporary Ranger torch data.", exception);
        }
    }

    private void announceSkillCast(Unit unit, Player owner, String skill, Particle particle) {
        Mob mob = activeEntities.get(unit.id);
        if (mob != null && mob.isValid()) {
            Location effectLocation = mob.getLocation().add(0, 1, 0);
            mob.getWorld().spawnParticle(particle, effectLocation, 14, 0.35, 0.45, 0.35, 0.05);
        }
        owner.sendActionBar("§a" + displayName(unit) + " §eđã dùng kỹ năng §b" + skillDisplayName(skill));
    }

    private boolean applyConfiguredBuff(Unit unit, LivingEntity target, String skill, String cooldownKey) {
        String configured = settings.getString("classes." + unit.unitClass + ".skill-settings."
            + skill + ".effect", ownerBuff);
        String effectName = configured == null || configured.isBlank()
            ? ownerBuff : configured.toUpperCase(Locale.ROOT);
        PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(effectName.toLowerCase(Locale.ROOT)));
        if (type == null) {
            plugin.getLogger().warning("[Army] Unknown " + skill + " effect '" + effectName
                + "' for class " + unit.unitClass + ".");
            return false;
        }
        if (target.hasPotionEffect(type)) return false;
        int seconds = (int) Math.min(600, Math.round(skillOptionDouble(unit, skill,
            "duration-seconds", ownerBuffTicks / 20.0, 1.0, 600.0) * skillPowerMultiplier(unit)));
        int amplifier = (int) skillOptionDouble(unit, skill, "amplifier", 0.0, 0.0, 10.0);
        target.addPotionEffect(new PotionEffect(type, seconds * 20, amplifier, true, true, true));
        markUsed(unit, cooldownKey, skillCooldownSeconds(unit, skill));
        if (skill.equalsIgnoreCase("BUFF_OWNER")) {
            Player owner = Bukkit.getPlayer(unit.owner);
            if (owner != null) announceSkillCast(unit, owner, skill, Particle.ENCHANT);
        }
        return true;
    }

   private double skillOptionDouble(MercenaryManager.Unit unit, String skill, String option, double fallback, double minimum, double maximum) {
      String path = "classes." + unit.unitClass + ".skill-settings." + skill + "." + option;
      double value = this.readBoundedDouble(this.settings, path, fallback, minimum, maximum);
      if ("range-blocks".equals(option)) {
         value = Math.min(maximum, value + this.soldierGearStats(unit).attackRangeBlocks());
      }

      return value;
   }

   private double skillCooldownSeconds(MercenaryManager.Unit unit, String skill) {
      double configured = this.readBoundedInt(
         this.settings,
         "classes." + unit.unitClass + ".skill-settings." + skill + ".cooldown-seconds",
         this.settings.getInt("skills.cooldown-seconds", 15),
         1,
         3600
      );
      double levelProgress = this.maxLevel <= 1 ? 0.0 : (double)(unit.level - 1) / (this.maxLevel - 1);
      double levelAdjusted = Math.max(1.0, configured - this.skillCooldownReductionAtMaxLevel * levelProgress);
      double gearReduction = Math.min(20.0, this.soldierGearStats(unit).cooldownReductionPercent());
      return Math.max(1.0, levelAdjusted * (1.0 - gearReduction / 100.0));
   }

    private String skillOptionString(Unit unit, String skill, String option, String fallback) {
        String value = settings.getString("classes." + unit.unitClass + ".skill-settings."
            + skill + "." + option, fallback);
        if (value == null || value.isBlank()) return fallback;
        return value.toUpperCase(Locale.ROOT);
    }

    private void applyGuardianLink(EntityDamageEvent event, Player owner) {
        double damage = event.getDamage();
        if (damage <= 0.0) return;
        for (Unit unit : getOwnerUnits(owner.getUniqueId())) {
            Mob guardian = activeEntities.get(unit.id);
            if (!unit.deployed || !"guardian".equals(unit.unitClass) || guardian == null
                    || !guardian.isValid() || guardian.isDead()
                    || !hasSkill(unit, "GUARDIAN_LINK")
                    || !guardian.getWorld().equals(owner.getWorld())) continue;
            double radius = skillOptionDouble(unit, "GUARDIAN_LINK", "radius-blocks", 5.0, 1.0, 16.0);
            if (guardian.getLocation().distanceSquared(owner.getLocation()) > radius * radius) continue;
            double redirected = damage * skillOptionDouble(unit, "GUARDIAN_LINK",
                "redirected-damage-fraction", 0.4, 0.0, 0.9);
            if (redirected <= 0.0) continue;
            event.setDamage(Math.max(0.0, damage - redirected));
            guardian.damage(redirected);
            spawnGuardianLinkParticles(owner.getLocation(), guardian.getLocation());
            return;
        }
    }

    private void spawnGuardianLinkParticles(Location ownerLocation, Location guardianLocation) {
        Vector path = guardianLocation.toVector().subtract(ownerLocation.toVector());
        double distance = path.length();
        if (distance <= 0.0) return;
        Vector step = path.normalize().multiply(0.5);
        Location particle = ownerLocation.clone().add(0.0, 1.0, 0.0);
        Location end = guardianLocation.clone().add(0.0, 1.0, 0.0);
        for (double traveled = 0.0; traveled < distance; traveled += 0.5) {
            ownerLocation.getWorld().spawnParticle(Particle.WAX_ON, particle, 1, 0.0, 0.0, 0.0, 0.0);
            particle.add(step);
        }
        ownerLocation.getWorld().spawnParticle(Particle.WAX_ON, end, 1, 0.0, 0.0, 0.0, 0.0);
    }

    private void handleGuardianBasicHit(Unit unit, Mob guardian, LivingEntity target,
                                        EntityDamageByEntityEvent event) {
        if (!hasSkill(unit, "ATTACK")) return;
        int hitCount = guardianHitCounters.getOrDefault(unit.id, 0) + 1;
        if (hitCount < 3) {
            guardianHitCounters.put(unit.id, hitCount);
            return;
        }
        guardianHitCounters.remove(unit.id);
        double maximumHealth = guardian.getAttribute(Attribute.MAX_HEALTH) == null
            ? guardian.getHealth() : guardian.getAttribute(Attribute.MAX_HEALTH).getValue();
        double extraDamage = maximumHealth * skillOptionDouble(unit, "ATTACK",
            "third-hit-max-health-damage", 0.05, 0.0, 1.0);
        event.setDamage(event.getDamage() + extraDamage);

        double coneThreshold = skillOptionDouble(unit, "ATTACK", "cone-dot-threshold", 0.7, 0.0, 1.0);
        Vector forward = guardian.getLocation().getDirection().setY(0.0);
        if (forward.lengthSquared() <= 0.0001) return;
        forward.normalize();
        int duration = (int) skillOptionDouble(unit, "ATTACK",
            "slowness-duration-seconds", 2.0, 1.0, 10.0);
        int amplifier = (int) skillOptionDouble(unit, "ATTACK", "slowness-amplifier", 0.0, 0.0, 4.0);
        double frontalRadius = skillOptionDouble(unit, "ATTACK",
            "frontal-radius-blocks", 4.0, 1.0, 12.0);
        double frontalHeight = skillOptionDouble(unit, "ATTACK",
            "frontal-height-blocks", 2.0, 1.0, 8.0);
        for (Entity nearby : guardian.getNearbyEntities(frontalRadius, frontalHeight, frontalRadius)) {
            if (!(nearby instanceof LivingEntity hostile) || !isHostileMob(hostile)) continue;
            Vector targetDirection = hostile.getLocation().toVector()
                .subtract(guardian.getLocation().toVector()).setY(0.0);
            if (targetDirection.lengthSquared() <= 0.0001) continue;
            if (forward.dot(targetDirection.normalize()) < coneThreshold) continue;
            hostile.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                duration * 20, amplifier, true, true, true));
        }
        guardian.getWorld().spawnParticle(Particle.SWEEP_ATTACK,
            guardian.getLocation().add(0.0, 1.0, 0.0), 1, 0.0, 0.0, 0.0, 0.0);
        guardian.getWorld().playSound(guardian.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.75f);
    }

   private void applySoldierGearHitEffect(MercenaryManager.Unit unit, LivingEntity target) {
      MercenaryManager.GearStats gear = this.soldierGearStats(unit);
      if (!(gear.effectChancePercent() <= 0.0) && !(ThreadLocalRandom.current().nextDouble(100.0) >= gear.effectChancePercent())) {
         long now = System.currentTimeMillis();
         long cooldownMillis = (long)(this.readBoundedDouble(this.settings, "gear-system.effect-internal-cooldown-seconds", 5.0, 0.0, 120.0) * 1000.0);
         if (now >= this.soldierGearEffectCooldowns.getOrDefault(unit.id, 0L)) {
            this.soldierGearEffectCooldowns.put(unit.id, now + cooldownMillis);
            int durationTicks = (int)Math.min(1200.0, Math.max(1.0, (1.0 + gear.effectDurationSeconds()) * 20.0));
            switch (unit.unitClass) {
               case "pyromancer":
                  target.setFireTicks(Math.max(target.getFireTicks(), durationTicks));
                  break;
               case "assassin":
                  target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, durationTicks, 0, true, true, true));
                  break;
               case "archer":
               case "ranger":
               case "guardian":
               case "druid":
               case "frost_mage":
                  target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, durationTicks, 0, true, true, true));
            }
         }
      }
   }

   private double soldierHealingMultiplier(MercenaryManager.Unit unit) {
      return 1.0 + this.soldierGearStats(unit).healingPercent() / 100.0;
   }

    private void applyOwnerShield(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player owner)) return;
        boolean hostile = event.getDamager() instanceof Monster;
        if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Monster) hostile = true;
        if (!hostile) return;
        for (Unit unit : getOwnerUnits(owner.getUniqueId())) {
            Mob mob = activeEntities.get(unit.id);
            if (!unit.deployed || mob == null || !mob.isValid() || !hasSkill(unit, "SHIELD_OWNER")
                        || !mob.getWorld().equals(owner.getWorld())) continue;
            double radius = skillOptionDouble(unit, "SHIELD_OWNER", "radius-blocks", 8.0, 1.0, 32.0);
            if (mob.getLocation().distanceSquared(owner.getLocation()) > radius * radius
                        || !ready(unit, "shield-owner")) continue;
            double reduction = Math.min(0.9, skillOptionDouble(unit, "SHIELD_OWNER",
                "damage-reduction", shieldReduction, 0.0, 0.9) * skillPowerMultiplier(unit));
            event.setDamage(event.getDamage() * (1.0 - reduction));
            markUsed(unit, "shield-owner", skillCooldownSeconds(unit, "SHIELD_OWNER"));
            announceSkillCast(unit, owner, "SHIELD_OWNER", Particle.WAX_ON);
            return;
        }
    }

    private boolean ready(Unit unit, String skill) {
        return skillCooldowns.computeIfAbsent(unit.id, ignored -> new HashMap<>())
            .getOrDefault(skillCooldownKey(skill), 0L) <= System.currentTimeMillis();
    }

    private void markUsed(Unit unit, String skill, double cooldownSeconds) {
        skillCooldowns.computeIfAbsent(unit.id, ignored -> new HashMap<>())
            .put(skillCooldownKey(skill), System.currentTimeMillis() + Math.round(cooldownSeconds * 1000.0));
    }

    private boolean hasSkill(Unit unit, String skill) {
        List<String> skills = settings.getStringList("classes." + unit.unitClass + ".skills");
        return skills.stream().anyMatch(value -> value.equalsIgnoreCase(skill));
    }

    private boolean isHostileMob(LivingEntity target) {
        if (target == null || !target.isValid() || target.isDead()) return false;
        if (target instanceof Player || isMercenary(target)) return false;
        if (target instanceof Monster) return true;
        return target instanceof org.bukkit.entity.EnderDragon
            || target instanceof org.bukkit.entity.Ghast
            || target instanceof org.bukkit.entity.Phantom
            || target instanceof org.bukkit.entity.Wither
            || target instanceof org.bukkit.entity.Slime
            || target instanceof org.bukkit.entity.MagmaCube;
    }

    private boolean isValidTarget(LivingEntity target) {
        return isHostileMob(target);
    }

    private boolean isAllowedMercenaryTarget(UUID ownerId, LivingEntity target, boolean ownerCommandedTarget) {
        if (target == null || !target.isValid() || target.isDead()) return false;
        if (target.getUniqueId().equals(ownerId)) return false;
        if (target instanceof Player || isMercenary(target)) {
            return canMercenaryDamageTarget(ownerId, target)
                && ownerCommandedTarget
                && isActiveOwnerAttackTarget(ownerId, target.getUniqueId());
        }
        return isHostileMob(target);
    }

    private boolean canMinionTarget(Mob minion, LivingEntity target) {
        UUID ownerId = getOwnerId(minion);
        return target != null && isAllowedMinionVictim(minion, target)
            && (isHostileMob(target) || isRetaliationTarget(ownerId, target))
            && minion.hasLineOfSight(target);
    }

   private int maxSummonedMinions(MercenaryManager.Unit unit) {
      int configuredMaximum = (int)this.skillOptionDouble(unit, "SUMMON_VEXES", "max-active", 7.0, 1.0, 10.0);
      int gearBonus = (int)Math.floor(this.soldierGearStats(unit).minions()) + druidSpellbookVexBonus(unit);
      return Math.min(10, Math.min(configuredMaximum + gearBonus, 1 + unit.level / 5 + gearBonus));
   }

   private int offhandGearQuality(Unit unit, String classId) {
      ItemStack item = unit.equipment.get(EquipmentSlot.OFF_HAND);
      if (!validSoldierGear(item, EquipmentSlot.OFF_HAND)
            || !classId.equals(soldierGearValue(item, soldierGearClassKey))) return 0;
      return gearQuality(soldierGearValue(item, soldierGearMaterialKey), soldierGearStars(item));
   }

   private int gearQuality(String material, int stars) {
      int materialTier = GEAR_MATERIALS.indexOf(normalizeGearMaterial(material));
      return Math.max(0, materialTier) + Math.max(0, stars - 1);
   }

   private int druidSpellbookVexBonus(Unit unit) {
      return "druid".equals(unit.unitClass) ? Math.min(2, offhandGearQuality(unit, "druid") / 4) : 0;
   }

   private int rangerSpyglassArrowBonus(Unit unit) {
      return "ranger".equals(unit.unitClass) ? Math.min(3, offhandGearQuality(unit, "ranger") / 3) : 0;
   }

   private int rangerSpyglassTorchBonus(Unit unit) {
      return unit != null && "ranger".equals(unit.unitClass) ? offhandGearQuality(unit, "ranger") * 5 : 0;
   }

    private boolean isAllowedMinionVictim(Mob minion, LivingEntity target) {
        UUID ownerId = getOwnerId(minion);
        if (target == null || ownerId == null || target.getUniqueId().equals(ownerId)) return false;
        if (target instanceof Player || isMercenary(target)) {
            return canMercenaryDamageTarget(ownerId, target)
                && isActiveOwnerAttackTarget(ownerId, target.getUniqueId());
        }
        return isHostileMob(target);
    }

    private boolean canMercenaryDamageTarget(UUID sourceOwnerId, LivingEntity target) {
        if (target == null) return false;
        if (!(target instanceof Player) && !isMercenary(target)) return true;
        if (sourceOwnerId == null) return false;
        UUID targetOwnerId = target instanceof Player player
            ? player.getUniqueId() : getOwnerId(target);
        if (sourceOwnerId.equals(targetOwnerId)) return false;
        return isOwnerCommandedPvpTarget(sourceOwnerId, target);
    }

    private boolean isOwnerCommandedPvpTarget(UUID ownerId, LivingEntity target) {
        return ownerCommandedPvpEnabled && target != null
            && isActiveOwnerAttackTarget(ownerId, target.getUniqueId());
    }

    private Entity resolveDamageSource(Entity damager) {
        if (damager instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        return damager;
    }

    private void registerOwnerThreat(UUID ownerId, Entity source) {
        if (ownerId == null || source == null) return;
        registerThreat(ownerThreats, ownerId, source);
        UUID sourceOwner = getOwnerId(source);
        if (sourceOwner != null && !sourceOwner.equals(ownerId)) {
            ownerThreats.get(ownerId).put(sourceOwner, System.currentTimeMillis());
        }
    }

    private void registerThreat(Map<UUID, Map<UUID, Long>> threatMap, UUID subjectId, Entity source) {
        if (subjectId == null || source == null) return;
        Map<UUID, Long> threats = threatMap.computeIfAbsent(subjectId, ignored -> new HashMap<>());
        threats.put(source.getUniqueId(), System.currentTimeMillis());
    }

    private boolean isRetaliationTarget(UUID ownerId, LivingEntity target) {
        if (ownerId == null || target == null) return false;
        if (target instanceof Player || isMercenary(target)) {
            return isOwnerCommandedPvpTarget(ownerId, target);
        }
        Map<UUID, Long> threats = ownerThreats.get(ownerId);
        long now = System.currentTimeMillis();
        if (threats != null) {
            threats.entrySet().removeIf(entry -> now - entry.getValue() > 15000L);
            if (threats.containsKey(target.getUniqueId())) return true;
            UUID targetOwner = getOwnerId(target);
            if (targetOwner != null && threats.containsKey(targetOwner)) return true;
        }
        return isActiveOwnerAttackTarget(ownerId, target.getUniqueId());
    }

    private void sanitizeMobAi(Mob mob) {
        if (mob instanceof Villager) {
            mob.setAware(true);
            mob.setTarget(null);
        }
    }

    private boolean addExperience(Unit unit, int amount) {
        if (amount <= 0 || unit.level >= maxLevel) return false;
        unit.experience = (int) Math.min(Integer.MAX_VALUE, (long) unit.experience + amount);
        boolean leveledUp = false;
        while (unit.level < maxLevel && unit.experience >= nextLevelExperience(unit)) {
            unit.experience -= nextLevelExperience(unit);
            unit.level++;
            leveledUp = true;
        }
        if (leveledUp) {
            Mob mob = activeEntities.get(unit.id);
            if (mob != null && mob.isValid()) {
                applyStats(unit, mob);
                updateEntityName(unit, mob);
                mob.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, mob.getLocation().add(0, 1, 0),
                    18, 0.45, 0.5, 0.45, 0.02);
                mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.35f);
            }
            Player owner = Bukkit.getPlayer(unit.owner);
            if (owner != null) owner.sendMessage("§aLính §e" + displayName(unit) + "§a lên cấp §f"
                + unit.level + "§a (Bậc " + unit.tier + ").");
        }
        return true;
    }

    private double skillPowerMultiplier(Unit unit) {
        return Math.min(maxSkillPowerMultiplier,
            1.0 + Math.max(0, unit.level - 1) * skillPowerPerLevel);
    }

    private UUID getOwnerId(Entity entity) {
        String value = entity.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private UUID getUnitId(Entity entity) {
        String value = entity.getPersistentDataContainer().get(unitKey, PersistentDataType.STRING);
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void hire(Player player, String[] args) {
        if (args.length != 3) {
            player.sendMessage("§eSử dụng: /vnarmy hire <loại> <bậc>");
            return;
        }
        String classId = normalizeClass(args[1]);
        int tier = parsePositiveInt(args[2]);
        if (classId == null || !hasTier(tier)) {
            player.sendMessage("§cLoại lính hoặc bậc không hợp lệ. Dùng /vnarmy để xem danh sách.");
            return;
        }
        List<Unit> owned = getOwnerUnits(player.getUniqueId());
        if (owned.size() >= maxUnits) {
            player.sendMessage("§cĐã đạt giới hạn sở hữu " + maxUnits + " lính đánh thuê.");
            return;
        }
        ItemStack token = findToken(player, classId, tier);
        int xpCost = settings.getInt("tiers." + tier + ".experience-levels", -1);
        ConfigurationSection materialCosts = settings.getConfigurationSection("tiers." + tier + ".materials");
        if (xpCost < 0 || materialCosts == null) {
            player.sendMessage("§cCấu hình chi phí Bậc " + tier + " không hợp lệ; chưa trừ tài nguyên.");
            plugin.getLogger().warning("[Army] Invalid cost configuration for tier " + tier + ".");
            return;
        }
        Map<Material, Integer> costs = readMaterialCosts(materialCosts, player, "thuê Bậc " + tier);
        if (costs == null) return;
        List<String> missing = missingMaterials(player, costs);
        if (token == null) {
            missing.add(0, "Lệnh Triệu Hồi " + classDisplay(classId) + " Bậc " + tier);
        }
        if (player.getLevel() < xpCost) {
            missing.add("Cấp kinh nghiệm: cần " + xpCost + ", hiện có " + player.getLevel());
        }
        if (!missing.isEmpty()) {
            player.sendMessage("§cThiếu điều kiện để thuê lính Bậc " + tier + ":");
            missing.forEach(value -> player.sendMessage("§7- " + value));
            return;
        }

        ItemStack[] inventoryBefore = cloneContents(player.getInventory().getContents());
        int levelsBefore = player.getLevel();
        if (!removeOne(player, token) || !removeMaterials(player, costs)) {
            player.getInventory().setContents(inventoryBefore);
            player.sendMessage("§cKhông thể trừ đủ vật phẩm; giao dịch đã được hoàn tác.");
            return;
        }
        player.setLevel(levelsBefore - xpCost);
        boolean deployImmediately = deployedUnitCount(player.getUniqueId()) < maxDeployedUnits;
        Unit unit = new Unit(UUID.randomUUID(), player.getUniqueId(), classId, tier,
            Mode.PROTECT, deployImmediately);
        Location spawnAt = player.getLocation().clone().add(0.8, 0.0, 0.8);
        Mob mob = deployImmediately ? spawnUnit(unit, spawnAt) : null;
        if (deployImmediately && mob == null) {
            player.getInventory().setContents(inventoryBefore);
            player.setLevel(levelsBefore);
            player.sendMessage("§cKhông thể tạo lính; toàn bộ chi phí đã được hoàn lại.");
            return;
        }
        units.put(unit.id, unit);
        if (mob != null) {
            activeEntities.put(unit.id, mob);
            updateLocation(unit, mob.getLocation());
        }
        if (!saveData()) {
            units.remove(unit.id);
            removeUnitData(unit);
            if (mob != null) {
                activeEntities.remove(unit.id);
                mob.remove();
            }
            player.getInventory().setContents(inventoryBefore);
            player.setLevel(levelsBefore);
            saveData();
            player.sendMessage("§cKhông thể lưu lính vào mercenaries.yml; chi phí đã được hoàn lại.");
            return;
        }
        player.sendMessage("§aĐã thuê §e" + displayName(unit) + " §aBậc " + tier
            + " (§7ID " + shortId(unit.id) + "§a)."
            + (deployImmediately ? "" : " §7Lính đang nghỉ vì đã đủ giới hạn triệu hồi.")
            + " Dùng /vnarmy gear " + shortId(unit.id) + " để trang bị.");
    }

    private void recall(Player player, String[] args) {
        List<Unit> requested = selectedUnits(player, args, "recall");
        if (requested == null) return;
        if (requested.isEmpty()) {
            player.sendMessage("§7Bạn chưa sở hữu lính đánh thuê nào.");
            return;
        }
        boolean summonAll = args.length == 1;
        List<Unit> candidates = summonAll
            ? getOwnerUnits(player.getUniqueId()).stream()
                .filter(unit -> unit.recoveryUntilMillis <= System.currentTimeMillis())
                .sorted(summonPriority())
                .limit(maxDeployedUnits)
                .toList()
            : requested;
        Set<UUID> selectedIds = candidates.stream().map(unit -> unit.id)
            .collect(java.util.stream.Collectors.toSet());
        if (summonAll) {
            for (Unit unit : getOwnerUnits(player.getUniqueId())) {
                if (unit.deployed && unit.recoveryUntilMillis <= System.currentTimeMillis()
                        && !selectedIds.contains(unit.id)) {
                    restForQuickRecall(player, unit);
                }
            }
        } else if (candidates.get(0).recoveryUntilMillis <= System.currentTimeMillis()
                && !candidates.get(0).deployed
                && deployedUnitCount(player.getUniqueId()) >= maxDeployedUnits) {
            player.sendMessage("§cBạn đã đạt giới hạn triệu hồi " + maxDeployedUnits
                + " lính. Hãy cho một lính nghỉ trước.");
            return;
        }

        int restored = 0;
        int recovering = (int) (summonAll ? getOwnerUnits(player.getUniqueId()).stream()
            : candidates.stream()).filter(unit -> unit.recoveryUntilMillis > System.currentTimeMillis()).count();
        for (Unit unit : candidates) {
            if (unit.recoveryUntilMillis > System.currentTimeMillis()) {
                player.sendMessage("§e" + displayName(unit) + " §7đang hồi phục, còn "
                    + remainingRecoverySeconds(unit) + " giây.");
                continue;
            }
            if (!unit.deployed && deployedUnitCount(player.getUniqueId()) >= maxDeployedUnits) {
                player.sendMessage("§cKhông thể gọi thêm lính: đã đạt giới hạn triệu hồi "
                    + maxDeployedUnits + ".");
                continue;
            }
            unit.deployed = true;
            Mob mob = findExistingEntity(unit);
            if (mob == null) mob = spawnUnit(unit, player.getLocation().clone().add(0.8, 0.0, 0.8));
            if (mob == null) {
                unit.deployed = false;
                player.sendMessage("§cKhông thể triệu hồi lính §e#" + shortId(unit.id) + "§c.");
                continue;
            }
            mob.teleport(player.getLocation().clone().add(0.8, 0.0, 0.8));
            mob.setTarget(null);
            ensurePaladinMounted(unit, mob);
            activeEntities.put(unit.id, mob);
            updateLocation(unit, mob.getLocation());
            restored++;
        }
        if (!saveData()) {
            player.sendMessage("§cLính đã được gọi ra nhưng không thể lưu trạng thái; kiểm tra log máy chủ.");
            plugin.getLogger().severe("[Army] Could not save recall state for owner " + player.getUniqueId() + ".");
            return;
        }
        player.sendMessage("§aĐã triệu hồi " + restored + "/" + candidates.size() + " lính đánh thuê."
            + (recovering > 0 ? " §7(" + recovering + " đang hồi phục)" : ""));
    }

    private void restoreDeployedUnits(Player player) {
        if (!enabled || !player.isOnline()) return;
        List<Unit> owned = getOwnerUnits(player.getUniqueId()).stream()
            .filter(unit -> unit.deployed && unit.recoveryUntilMillis <= System.currentTimeMillis())
            .sorted(summonPriority())
            .toList();
        boolean changed = false;
        for (int index = 0; index < owned.size(); index++) {
            Unit unit = owned.get(index);
            if (index >= maxDeployedUnits) {
                changed |= restForQuickRecall(player, unit);
                continue;
            }
            Mob mob = findExistingEntity(unit);
            if (mob == null) mob = spawnUnit(unit, player.getLocation().clone().add(0.8, 0.0, 0.8));
            if (mob == null) {
                unit.deployed = false;
                changed = true;
                plugin.getLogger().warning("[Army] Could not restore unit " + unit.id
                    + " for owner " + player.getUniqueId() + ".");
            } else {
                activeEntities.put(unit.id, mob);
                updateLocation(unit, mob.getLocation());
            }
        }
        if (changed || !owned.isEmpty()) saveData();
    }

    private Comparator<Unit> summonPriority() {
        return Comparator.comparingInt((Unit unit) -> unit.tier).reversed()
            .thenComparing(Comparator.comparingInt((Unit unit) -> unit.level).reversed())
            .thenComparing(unit -> unit.id.toString());
    }

    private int deployedUnitCount(UUID ownerId) {
        long now = System.currentTimeMillis();
        return (int) getOwnerUnits(ownerId).stream()
            .filter(unit -> unit.deployed && unit.recoveryUntilMillis <= now)
            .count();
    }

    private boolean restForQuickRecall(Player player, Unit unit) {
        Mob mob = activeEntities.get(unit.id);
        if (mob == null || !mob.isValid()) mob = findExistingEntity(unit);
        boolean wasDeployed = unit.deployed;
        boolean previousHorseDead = unit.horseDead;
        UUID previousEntityId = unit.entityId;
        if (mob != null && mob.isValid()) {
            captureEquipment(unit, mob);
            updateLocation(unit, mob.getLocation());
        }
        unit.deployed = false;
        unit.horseDead = false;
        unit.entityId = null;
        if (!saveData()) {
            unit.deployed = wasDeployed;
            unit.horseDead = previousHorseDead;
            unit.entityId = previousEntityId;
            player.sendMessage("§cKhông thể lưu trạng thái nghỉ của lính §e#" + shortId(unit.id)
                + "§c; lính vẫn được giữ nguyên.");
            plugin.getLogger().severe("[Army] Could not save deployment-limit rest for unit " + unit.id + ".");
            return false;
        }
        cleanupGuardianStateForUnit(unit.id);
        cleanupSummonedMinions(unit.id);
        removeMount(unit.id, mob);
        if (mob != null && mob.isValid()) mob.remove();
        activeEntities.remove(unit.id);
        return true;
    }

    private void rest(Player player, String[] args) {
        List<Unit> owned = selectedUnits(player, args, "rest");
        if (owned == null) return;
        if (owned.isEmpty()) {
            player.sendMessage("§7Bạn chưa sở hữu lính đánh thuê nào.");
            return;
        }
        int rested = 0;
        for (Unit unit : owned) {
            if (unit.recoveryUntilMillis > System.currentTimeMillis()) continue;
            Mob mob = activeEntities.get(unit.id);
            if (mob == null || !mob.isValid()) mob = findExistingEntity(unit);
            if (mob != null && mob.isValid()) {
                activeEntities.put(unit.id, mob);
                captureEquipment(unit, mob);
                updateLocation(unit, mob.getLocation());
                boolean previousHorseDead = unit.horseDead;
                unit.horseDead = false;
                unit.entityId = null;
                unit.deployed = false;
                if (!saveData()) {
                    unit.horseDead = previousHorseDead;
                    unit.deployed = true;
                    unit.entityId = mob.getUniqueId();
                    applyEquipment(unit, mob);
                    player.sendMessage("§cKhông thể lưu trạng thái nghỉ của lính §e#" + shortId(unit.id)
                        + "§c; lính vẫn được giữ nguyên.");
                    continue;
                }
                cleanupGuardianStateForUnit(unit.id);
                cleanupSummonedMinions(unit.id);
                removeMount(unit.id, mob);
                mob.remove();
                activeEntities.remove(unit.id);
                rested++;
            } else if (unit.deployed) {
                boolean previousHorseDead = unit.horseDead;
                unit.horseDead = false;
                unit.deployed = false;
                unit.entityId = null;
                if (saveData()) {
                    cleanupGuardianStateForUnit(unit.id);
                    cleanupSummonedMinions(unit.id);
                    removeMount(unit.id, null);
                    rested++;
                } else {
                    unit.horseDead = previousHorseDead;
                    unit.deployed = true;
                    player.sendMessage("§cKhông thể lưu trạng thái nghỉ của lính §e#" + shortId(unit.id) + "§c.");
                }
            }
        }
        player.sendMessage(rested == 0 ? "§7Không có lính nào đang triển khai."
            : "§aĐã cho " + rested + " lính nghỉ. Trang bị được lưu.");
    }

    private List<Unit> selectedUnits(Player player, String[] args, String commandName) {
        if (args.length > 2) {
            player.sendMessage("§eSử dụng: /vnarmy " + commandName + " [ID]");
            return null;
        }
        List<Unit> owned = getOwnerUnits(player.getUniqueId());
        if (args.length == 1) return owned;
        Unit unit = findOwnedByShortId(player.getUniqueId(), args[1]);
        if (unit == null) {
            player.sendMessage("§cKhông tìm thấy lính của bạn với ID bắt đầu bằng §e" + args[1] + "§c.");
            return null;
        }
        return List.of(unit);
    }

    private void despawnForOwner(UUID ownerId) {
        boolean changed = false;
        for (Unit unit : getOwnerUnits(ownerId)) {
            cleanupGuardianStateForUnit(unit.id);
            Mob mob = activeEntities.remove(unit.id);
            if (mob == null || !mob.isValid()) mob = findExistingEntity(unit);
            if (mob == null || !mob.isValid()) continue;
            captureEquipment(unit, mob);
            updateLocation(unit, mob.getLocation());
            unit.entityId = null;
            removeMount(unit.id, mob);
            mob.remove();
            activeEntities.remove(unit.id);
            changed = true;
        }
        if (changed && !saveData()) {
            plugin.getLogger().severe("[Army] Could not persist mercenary state on player logout for " + ownerId + ".");
        }
    }

    private void removeMount(UUID unitId, Mob rider) {
        UUID mountId = mountByUnit.remove(unitId);
        Entity mount = mountId == null ? null : Bukkit.getEntity(mountId);
        if (mount == null && rider != null && rider.getVehicle() instanceof Horse) {
            mount = rider.getVehicle();
        }
        if (mount instanceof Horse horse && horse.isValid()) {
            captureHorseArmor(unitId, horse);
            horse.remove();
        }
    }

    private void ensurePaladinMounted(Unit unit, Mob rider) {
        if (!(rider instanceof Zombie) || !"paladin".equals(unit.unitClass) || unit.horseDead) return;
        if (rider.getVehicle() instanceof Horse current && isUnitHorse(current, unit)) {
            mountByUnit.put(unit.id, current.getUniqueId());
            applyHorseStatsAndArmor(unit, current);
            return;
        }
        Horse horse = getOrCreateUnitHorse(unit, rider.getLocation());
        if (horse == null) return;
        if (horse.getPassengers().isEmpty() || horse.getPassengers().contains(rider)) {
            horse.addPassenger(rider);
            applyHorseStatsAndArmor(unit, horse);
        }
    }

    private Horse getOrCreateUnitHorse(Unit unit, Location location) {
        if (unit.horseDead) return null;
        Entity tracked = mountByUnit.containsKey(unit.id) ? Bukkit.getEntity(mountByUnit.get(unit.id)) : null;
        if (tracked instanceof Horse horse && horse.isValid() && isUnitHorse(horse, unit)
                && (horse.getPassengers().isEmpty() || horse.getPassengers().stream()
                    .anyMatch(passenger -> isUnit(passenger, unit.id)))) {
            applyHorseStatsAndArmor(unit, horse);
            return horse;
        }
        mountByUnit.remove(unit.id);
        for (Entity nearby : location.getWorld().getNearbyEntities(location, 12.0, 8.0, 12.0)) {
            if (nearby instanceof Horse horse && horse.isValid() && isUnitHorse(horse, unit)
                    && (horse.getPassengers().isEmpty() || horse.getPassengers().stream()
                        .anyMatch(passenger -> isUnit(passenger, unit.id)))) {
                mountByUnit.put(unit.id, horse.getUniqueId());
                applyHorseStatsAndArmor(unit, horse);
                return horse;
            }
        }
        Entity created = location.getWorld().spawnEntity(location.clone().add(1.2, 0.0, 0.0), EntityType.HORSE);
        if (!(created instanceof Horse horse)) {
            created.remove();
            return null;
        }
        horse.setPersistent(false);
        horse.setRemoveWhenFarAway(false);
        horse.setTamed(true);
        horse.getPersistentDataContainer().set(horseMountKey, PersistentDataType.STRING, unit.id.toString());
        horse.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, unit.owner.toString());
        mountByUnit.put(unit.id, horse.getUniqueId());
        applyHorseStatsAndArmor(unit, horse);
        return horse;
    }

    private boolean isUnitHorse(Horse horse, Unit unit) {
        return unit.id.toString().equals(horse.getPersistentDataContainer()
            .get(horseMountKey, PersistentDataType.STRING));
    }

   private void applyHorseStatsAndArmor(MercenaryManager.Unit unit, Horse horse) {
      double horseHealth = this.readBoundedDouble(this.settings, "classes.paladin.horse-health", 50.0, 1.0, 1024.0);
      double horseSpeed = this.readBoundedDouble(this.settings, "classes.paladin.horse-speed", 0.3375, 0.1, 1.0);
      if (horse.getAttribute(Attribute.MAX_HEALTH) != null) {
         double previousMaximum = horse.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
         horse.getAttribute(Attribute.MAX_HEALTH).setBaseValue(horseHealth);
         double currentHealth = horse.getHealth();
         horse.setHealth(currentHealth >= previousMaximum ? horseHealth : Math.min(currentHealth, horseHealth));
      }

      if (horse.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
         horse.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(horseSpeed);
      }

      ItemStack wornArmor = horse.getInventory().getArmor();
      if (this.isHorseArmor(wornArmor) && !this.storeInUnitInventory(unit, wornArmor)) {
         unit.horseArmor = cloneOrNull(wornArmor);
      }

      horse.getInventory().setArmor(null);
   }

    private void applyHorseArmorToMount(Unit unit) {
        Entity mount = mountByUnit.containsKey(unit.id) ? Bukkit.getEntity(mountByUnit.get(unit.id)) : null;
        if (mount instanceof Horse horse && horse.isValid()) applyHorseStatsAndArmor(unit, horse);
    }

   private void captureHorseArmor(UUID unitId, Horse horse) {
      MercenaryManager.Unit unit = this.units.get(unitId);
      ItemStack armor = horse.getInventory().getArmor();
      if (unit != null && this.isHorseArmor(armor)) {
         if (!this.storeInUnitInventory(unit, armor)) {
            unit.horseArmor = this.selectBestHorseArmor(unit.horseArmor, armor);
         }

         horse.getInventory().setArmor(null);
      }
   }

    private boolean isHorseArmor(ItemStack item) {
        return item != null && item.getType().name().endsWith("_HORSE_ARMOR");
    }

    private ItemStack selectBestHorseArmor(ItemStack first, ItemStack second) {
        if (!isHorseArmor(first)) return cloneOrNull(second);
        if (!isHorseArmor(second)) return first.clone();
        return horseArmorScore(first) >= horseArmorScore(second) ? first.clone() : second.clone();
    }

    private int horseArmorScore(ItemStack armor) {
        String material = armor.getType().name();
        return material.startsWith("NETHERITE_") ? 5
            : material.startsWith("DIAMOND_") ? 4
            : material.startsWith("GOLDEN_") ? 3
            : material.startsWith("IRON_") ? 2 : 1;
    }

    private Horse getMountedHorse(Unit unit, Zombie rider) {
        if (unit.horseDead) return null;
        UUID mountId = mountByUnit.get(unit.id);
        Entity mount = mountId == null ? null : Bukkit.getEntity(mountId);
        Horse horse;
        if (mount instanceof Horse existing && existing.isValid()) {
            horse = existing;
        } else {
            mountByUnit.remove(unit.id);
            if (!(rider.getVehicle() instanceof Horse attached) || !attached.isValid()) return null;
            horse = attached;
            mountByUnit.put(unit.id, horse.getUniqueId());
        }
        return rider.getVehicle() == horse ? horse : null;
    }

    private void teleportUnit(Unit unit, Mob mob, Location destination) {
        if ("paladin".equals(unit.unitClass) && mob instanceof Zombie rider) {
            Horse horse = getMountedHorse(unit, rider);
            if (horse != null) {
                if (!horse.teleport(destination)) {
                    plugin.getLogger().warning("[Army] Could not teleport horse for mercenary " + unit.id + ".");
                    return;
                }
                if (rider.getVehicle() != horse) horse.addPassenger(rider);
                return;
            }
        }
        mob.teleport(destination);
    }

    private void setMode(Player player, Mode mode) {
        List<Unit> owned = getOwnerUnits(player.getUniqueId());
        if (owned.isEmpty()) {
            player.sendMessage("§7Bạn chưa sở hữu lính đánh thuê nào.");
            return;
        }
        owned.forEach(unit -> unit.mode = mode);
        if (!saveData()) {
            player.sendMessage("§cKhông thể lưu chế độ AI; thử lại sau.");
            return;
        }
        player.sendMessage("§aChế độ của " + owned.size() + " lính đã chuyển thành §e" + modeName(mode) + "§a.");
    }

    private void listUnits(Player player) {
        List<Unit> owned = getOwnerUnits(player.getUniqueId());
        player.sendMessage("§6§lLính đánh thuê §7(Sở hữu " + owned.size() + "/" + maxUnits
            + " | Đang gọi " + deployedUnitCount(player.getUniqueId()) + "/" + maxDeployedUnits + ")");
        if (owned.isEmpty()) {
            player.sendMessage("§7Thuê lính bằng /vnarmy hire <loại> <bậc>.");
            return;
        }
        for (Unit unit : owned) {
            player.sendMessage("§e#" + shortId(unit.id) + " §f" + unitLabel(unit, true)
                + " §7| Bậc " + unit.tier + " | Cấp " + unit.level + " | EXP "
                + unit.experience + "/" + nextLevelExperience(unit) + " | " + modeName(unit.mode)
                + " | " + unitStatus(unit));
        }
    }

    private void listUnits(CommandSender sender, String playerName) {
        UUID ownerId = resolvePlayerId(playerName);
        if (ownerId == null) {
            sender.sendMessage("§cKhông tìm thấy hồ sơ người chơi " + playerName + ".");
            return;
        }
        List<Unit> owned = getOwnerUnits(ownerId);
        sender.sendMessage("§6§lLính của " + playerName + " §7(" + owned.size() + ")");
        if (owned.isEmpty()) {
            sender.sendMessage("§7Người chơi chưa sở hữu lính đánh thuê.");
            return;
        }
        for (Unit unit : owned) {
            sender.sendMessage("§e#" + shortId(unit.id) + " §f" + unitLabel(unit, false)
                + " §7| Bậc " + unit.tier + " | Cấp " + unit.level + " | EXP "
                + unit.experience + "/" + nextLevelExperience(unit) + " | " + modeName(unit.mode)
                + " | " + unitStatus(unit));
        }
    }

    private String unitStatus(Unit unit) {
        if (unit.recoveryUntilMillis > System.currentTimeMillis()) {
            return "§cHồi phục (" + remainingRecoverySeconds(unit) + "s)";
        }
        return unit.deployed ? "§aXuất chiến" : "§7Nghỉ ngơi";
    }

    private long remainingRecoverySeconds(Unit unit) {
        return Math.max(0L, (unit.recoveryUntilMillis - System.currentTimeMillis() + 999L) / 1000L);
    }

    private String unitLabel(Unit unit, boolean ownerView) {
        String ownerName = Bukkit.getOfflinePlayer(unit.owner).getName();
        if (ownerName == null || ownerName.isBlank()) ownerName = "Người chơi";
        return (ownerView ? "§a§l[" : "§b§l[") + ownerName + "]§r §f" + displayName(unit);
    }

    private void rename(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§eSử dụng: /vnarmy rename <ID> <Tên mới>");
            return;
        }
        Unit unit = findOwnedByShortId(player.getUniqueId(), args[1]);
        if (unit == null) {
            player.sendMessage("§cKhông tìm thấy lính của bạn với ID bắt đầu bằng §e" + args[1] + "§c.");
            return;
        }
        String name = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length))
            .replace("§", "").replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isBlank()) {
            player.sendMessage("§cTên lính không được để trống.");
            return;
        }
        if (name.length() > 32) name = name.substring(0, 32);
        String previous = unit.customName;
        unit.customName = name;
        Mob mob = activeEntities.get(unit.id);
        if (mob != null && mob.isValid()) updateEntityName(unit, mob);
        if (!saveData()) {
            unit.customName = previous;
            if (mob != null && mob.isValid()) updateEntityName(unit, mob);
            player.sendMessage("§cKhông thể lưu tên mới; tên lính được khôi phục.");
            return;
        }
        player.sendMessage("§aĐã đổi tên lính thành §f" + name + "§a.");
    }

    private boolean handleRemoveCommand(CommandSender sender, String[] args) {
        Unit unit;
        if (args.length == 2) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§eDùng /vnarmy remove <ID> hoặc /vnarmy remove <user> <id>.");
                return true;
            }
            unit = findOwnedByShortId(player.getUniqueId(), args[1]);
            if (unit == null && player.hasPermission(ADMIN_PERMISSION)) unit = findUnitByShortId(args[1]);
        } else if (args.length == 3 && sender.hasPermission(ADMIN_PERMISSION)) {
            UUID ownerId = resolvePlayerId(args[1]);
            unit = ownerId == null ? null : findOwnedByShortId(ownerId, args[2]);
        } else {
            sender.sendMessage("§eDùng /vnarmy remove <ID> hoặc /vnarmy remove <user> <id> (admin).");
            return true;
        }
        if (unit == null) {
            sender.sendMessage("§cKhông tìm thấy lính hoặc ID chưa đủ chính xác.");
            return true;
        }
        units.remove(unit.id);
        removeUnitData(unit);
        if (!saveData()) {
            units.put(unit.id, unit);
            sender.sendMessage("§cKhông lưu được; lính vẫn được giữ nguyên.");
            return true;
        }
        Mob mob = activeEntities.remove(unit.id);
        if (mob != null && mob.isValid()) mob.remove();
        skillCooldowns.remove(unit.id);
        sender.sendMessage("§aĐã xóa lính §e#" + shortId(unit.id) + "§a.");
        return true;
    }

    private boolean handleClearCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cBạn không có quyền xóa toàn bộ lính của người chơi.");
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage("§eDùng /vnarmy clear <user>.");
            return true;
        }
        UUID ownerId = resolvePlayerId(args[1]);
        if (ownerId == null) {
            sender.sendMessage("§cKhông tìm thấy hồ sơ người chơi " + args[1] + ".");
            return true;
        }
        List<Unit> removed = getOwnerUnits(ownerId);
        removed.forEach(unit -> {
            units.remove(unit.id);
            removeUnitData(unit);
        });
        if (!saveData()) {
            removed.forEach(unit -> units.put(unit.id, unit));
            sender.sendMessage("§cKhông lưu được; dữ liệu lính đã được giữ lại trong bộ nhớ.");
            return true;
        }
        for (Unit unit : removed) {
            Mob mob = activeEntities.remove(unit.id);
            if (mob != null && mob.isValid()) mob.remove();
            skillCooldowns.remove(unit.id);
        }
        sender.sendMessage("§aĐã xóa " + removed.size() + " lính của " + args[1] + ".");
        return true;
    }

    private UUID resolvePlayerId(String playerName) {
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) return online.getUniqueId();
        return java.util.Arrays.stream(Bukkit.getOfflinePlayers())
            .filter(player -> playerName.equalsIgnoreCase(player.getName()))
            .map(org.bukkit.OfflinePlayer::getUniqueId).findFirst().orElse(null);
    }

    private Unit findUnitByShortId(String shortId) {
        String prefix = shortId.toLowerCase(Locale.ROOT);
        List<Unit> matches = units.values().stream()
            .filter(unit -> unit.id.toString().startsWith(prefix)).toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private void toggleMuteCommand(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage("§eDùng /vnarmy mute <ID>.");
            return;
        }
        Unit unit = findOwnedByShortId(player.getUniqueId(), args[1]);
        if (unit == null) {
            player.sendMessage("§cKhông tìm thấy lính với ID đó.");
            return;
        }
        toggleMute(player, unit);
    }

    private void toggleMute(Player player, Unit unit) {
        boolean previous = unit.muted;
        unit.muted = !previous;
        Mob mob = activeEntities.get(unit.id);
        if (mob != null && mob.isValid()) mob.setSilent(unit.muted);
        if (!saveData()) {
            unit.muted = previous;
            if (mob != null && mob.isValid()) mob.setSilent(previous);
            player.sendMessage("§cKhông lưu được thiết lập âm thanh.");
            return;
        }
        player.sendMessage(unit.muted ? "§aĐã tắt âm thanh lính." : "§aĐã bật âm thanh lính.");
        openUnitMenu(player, unit);
    }

    private ServiceMenuHolder createServiceMenu(Player player, MenuType type, UUID target,
                                                String itemType, String classId, int page,
                                                int size, String title) {
        ServiceMenuHolder holder = new ServiceMenuHolder(player.getUniqueId(), type, target, itemType, classId, page);
        holder.inventory = Bukkit.createInventory(holder, Math.max(54, size), title);
        return holder;
    }

    public void openMainMenu(Player player) {
        if (!plugin.getConfig().getBoolean("menus.main.enabled", true)) {
            player.sendMessage("§cMenu chính hiện đang tắt.");
            return;
        }
        ServiceMenuHolder holder = createServiceMenu(player, MenuType.MAIN, null, null, null, 0,
            MAIN_MENU_SIZE, "VnMine • Menu chính");
        Inventory inventory = holder.inventory;
        for (int slot = 0; slot < MAIN_MENU_SIZE; slot++) {
            if (slot < 9 || slot >= 45) {
                inventory.setItem(slot, menuItem(Material.GRAY_STAINED_GLASS_PANE, "§r", List.of()));
            }
        }
        inventory.setItem(4, menuItem(Material.CLOCK, "§6VnMineHardcore", List.of(
            "§7Menu chính • chọn một nhóm chức năng bên dưới.")));
        inventory.setItem(MAIN_MENU_RPG_SLOT, menuItem(Material.AMETHYST_SHARD, "§dHành trình RPG", List.of(
            "§7Nhân vật, kỹ năng, bản đồ, nhiệm vụ.", "§eNhấp để mở menu RPG.")));
        if (plugin.getEconomyManager() != null && plugin.getConfig().getBoolean("economy.enabled", true)) {
            inventory.setItem(MAIN_MENU_WALLET_SLOT, menuItem(Material.SUNFLOWER, "§6Ví VNCOIN", List.of(
                "§7Xem số dư và giao dịch gần đây.", "§eNhấp để mở ví.")));
        }
        if (plugin.getEconomyManager() != null) {
            inventory.setItem(MAIN_MENU_ITEM_NAMES_SLOT, menuItem(Material.NAME_TAG, "§eTên item rơi", List.of(
                "§7Hiện tại: " + plugin.getEconomyManager().getItemNameModeLabel(
                    plugin.getEconomyManager().getItemNameMode(player)),
                "§eNhấp để đổi chế độ hiển thị.")));
        }
        if (player.hasPermission("vnmine.command.stats")) inventory.setItem(MAIN_MENU_STATS_SLOT,
            menuItem(Material.BOOK, "§eThống kê", List.of("§7Xem thống kê sinh tồn của bạn.")));
        if (player.hasPermission("vnmine.command.status")) inventory.setItem(MAIN_MENU_STATUS_SLOT,
            menuItem(Material.COMPASS, "§bTrạng thái server", List.of("§7Xem tình trạng VnMineHardcore.")));
        if (player.hasPermission(COMMAND_PERMISSION) && enabled) {
            inventory.setItem(MAIN_MENU_ARMY_SLOT, menuItem(Material.IRON_SWORD, "§6Quản lý quân đội", List.of("§7Xem và điều khiển lính.")));
        }
        if (player.hasPermission("vnmine.command.reward")) inventory.setItem(MAIN_MENU_REWARD_SLOT,
            menuItem(Material.CHEST, "§6Phần thưởng", List.of("§7Nhận phần thưởng boss đang chờ.")));
        inventory.setItem(MAIN_MENU_HELP_SLOT, menuItem(Material.WRITABLE_BOOK, "§bTrợ giúp", List.of("§7Mở hướng dẫn lệnh máy chủ.")));
        if (hasAdminMenuAccess(player)
                && plugin.getConfig().getBoolean("menus.admin.enabled", true)) {
            inventory.setItem(MAIN_MENU_ADMIN_SLOT, menuItem(Material.COMMAND_BLOCK, "§cAdmin", List.of("§7Mở công cụ quản trị.")));
        }
        player.openInventory(inventory);
    }

    private void openRpgHubMenu(Player player) {
        ServiceMenuHolder holder = createServiceMenu(player, MenuType.RPG_HUB, null, null, null, 0,
            54, "VnMine • Hành trình RPG");
        Inventory inventory = holder.inventory;
        for (int slot = 0; slot < 9; slot++) {
            inventory.setItem(slot, menuItem(Material.PURPLE_STAINED_GLASS_PANE, "§r", List.of()));
            inventory.setItem(slot + 45, menuItem(Material.PURPLE_STAINED_GLASS_PANE, "§r", List.of()));
        }
        inventory.setItem(4, menuItem(Material.AMETHYST_SHARD, "§dHành trình RPG", List.of(
            "§7Tiến trình nhân vật và nội dung thế giới.")));
        if (plugin.getConfig().getBoolean("rpg.character-creation.enabled", true)) {
            inventory.setItem(20, menuItem(Material.ENDER_EYE, "§dNhân vật / Hồ sơ", List.of(
                "§7Mở nhân vật và hồ sơ RPG.")));
        }
        if (player.hasPermission("vnmine.command.skills") && plugin.getSkillManager() != null) {
            inventory.setItem(22, menuItem(Material.ENCHANTED_BOOK, "§eCây kỹ năng", List.of(
                "§7Xem skill tree, điểm kỹ năng và nâng cấp.")));
        }
        if (plugin.getConfig().getBoolean("rpg.world-map.enabled", true)) {
            inventory.setItem(24, menuItem(Material.MAP, "§bBản đồ đại lục", List.of(
                "§7Xem các vùng và trạng thái thế giới.")));
        }
        if (plugin.getConfig().getBoolean("rpg.quest-journal.enabled", true)) {
            inventory.setItem(30, menuItem(Material.WRITABLE_BOOK, "§bNhật ký nhiệm vụ", List.of(
                "§7Xem mục tiêu và tiến độ nhiệm vụ.")));
        }
        inventory.setItem(32, menuItem(Material.KNOWLEDGE_BOOK, "§dSổ truyện", List.of(
            "§7Đọc danh sách chapter đã được nạp.")));
        inventory.setItem(49, menuItem(Material.ARROW, "§7Về menu chính", List.of()));
        player.openInventory(inventory);
    }

    static boolean isRealmAccessAllowed(
            HumanRealmDefinitionLoader.AccessCondition access, boolean hasActiveProfile, boolean questCompleted) {
        return "default".equals(access.type())
            || ("completed-quest".equals(access.type()) && hasActiveProfile && questCompleted);
    }

    static List<String> buildRealmMapLore(
            HumanRealmDefinitionLoader.HumanRealmDefinition definition,
            boolean accessAllowed, boolean worldUnlocked, boolean spawnAvailable,
            boolean fallbackUsed, boolean hasProfile) {
        String accessStatus = !accessAllowed ? "§cTrạng thái: §fĐang khóa"
            : (worldUnlocked ? "§aTrạng thái: §fĐã mở" : "§eTrạng thái: §fĐã đủ điều kiện");
        String accessCondition = "default".equals(definition.access().type())
            ? "§7Điều kiện: §fĐại lục khởi đầu"
            : "§7Điều kiện: §fHoàn thành nhiệm vụ " + definition.access().questId();
        return List.of(
            accessStatus,
            accessCondition,
            "§7ID: §f" + definition.id(),
            "§7World: §f" + definition.worldName(),
            spawnAvailable
                ? (fallbackUsed ? "§eSpawn: đang dùng world dự phòng" : "§aSpawn: sẵn sàng")
                : "§cSpawn: world chưa được nạp hoặc không hợp lệ",
            hasProfile
                ? "§aHồ sơ nhân vật đã sẵn sàng"
                : "§eTạo nhân vật để lưu tiến trình cá nhân"
        );
    }

    private void openWorldMap(Player player, int requestedPage) {
        if (!plugin.getConfig().getBoolean("rpg.world-map.enabled", true)) {
            player.sendMessage("§cBản đồ đại lục hiện đang tắt.");
            return;
        }
        RpgWorldManager worldManager = plugin.getRpgWorldManager();
        if (worldManager == null || worldManager.getDefinitions().isEmpty()) {
            player.sendMessage("§cBản đồ đại lục hiện chưa khả dụng. Hãy báo quản trị viên.");
            return;
        }
        PlayerProfileDatabaseManager database = plugin.getPlayerProfileDatabaseManager();
        if (database == null) {
            player.sendMessage("§cKhông thể tải tiến trình bản đồ lúc này.");
            return;
        }

        List<HumanRealmDefinitionLoader.HumanRealmDefinition> realms =
            List.copyOf(worldManager.getDefinitions().values());
        String playerId = player.getUniqueId().toString();
        debugRpg("[RPG-DEBUG][WorldMap] open-start player=" + player.getName()
            + ", page=" + requestedPage + ", realms=" + realms.size());
        database.executeGameplayDatabaseTask("world-map.load player=" + player.getName(), () -> {
            Optional<PlayerProfileDatabaseManager.PlayerProfile> profile = database.getPlayerProfile(playerId)
                .filter(PlayerProfileDatabaseManager.PlayerProfile::active);
            Map<String, RealmProgress> progressByRealm = new HashMap<>();
            if (profile.isPresent()) {
                for (HumanRealmDefinitionLoader.HumanRealmDefinition realm : realms) {
                    boolean questCompleted = false;
                    if ("completed-quest".equals(realm.access().type())) {
                        Optional<PlayerProfileDatabaseManager.QuestProgress> progress =
                            database.getQuestProgress(playerId, realm.access().questId());
                        questCompleted = progress.isPresent() && "completed".equals(progress.get().status());
                    }
                    boolean worldUnlocked = "default".equals(realm.access().type())
                        || database.isWorldUnlocked(playerId, realm.id());
                    progressByRealm.put(realm.id(), new RealmProgress(questCompleted, worldUnlocked));
                }
            }
            return new WorldMapProgress(profile.isPresent(), progressByRealm);
        }).whenComplete((progress, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (failure != null) {
                plugin.getLogger().log(Level.SEVERE, "[World] Failed to load realm progress for " +
                    player.getName(), failure);
                player.sendMessage("§cKhông thể tải tiến trình bản đồ lúc này. Dữ liệu của bạn vẫn được giữ.");
                return;
            }
            debugRpg("[RPG-DEBUG][WorldMap] open-result player=" + player.getName()
                + ", activeProfile=" + progress.hasProfile() + ", realms=" + progress.realms().size());
            renderWorldMap(player, realms, requestedPage, worldManager, progress);
        }));
    }

    private record RealmProgress(boolean questCompleted, boolean worldUnlocked) {
    }

    private record WorldMapProgress(boolean hasProfile, Map<String, RealmProgress> realms) {
    }

    private void renderWorldMap(Player player,
                                List<HumanRealmDefinitionLoader.HumanRealmDefinition> realms,
                                int requestedPage, RpgWorldManager worldManager, WorldMapProgress progress) {
        int pageCount = Math.max(1, (realms.size() + 27) / 28);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        ServiceMenuHolder holder = createServiceMenu(player, MenuType.WORLD_MAP, null, null, null,
            page, 54, "VnMine • Bản đồ đại lục");
        for (int index = page * 28; index < Math.min((page + 1) * 28, realms.size()); index++) {
            HumanRealmDefinitionLoader.HumanRealmDefinition realm = realms.get(index);
            RealmProgress realmProgress = progress.realms().getOrDefault(realm.id(),
                new RealmProgress(false, "default".equals(realm.access().type())));
            boolean accessAllowed = isRealmAccessAllowed(realm.access(), progress.hasProfile(),
                realmProgress.questCompleted());
            java.util.Optional<RpgWorldManager.ResolvedSpawn> resolvedSpawn = worldManager.resolveSpawn(realm.id());
            int pageIndex = index % 28;
            int slot = 10 + pageIndex % 7 + pageIndex / 7 * 9;
            holder.choices.put(slot, realm.id());
            holder.inventory.setItem(slot, menuItem(accessAllowed ? Material.COMPASS : Material.BARRIER,
                (accessAllowed ? "§b" : "§c") + realm.displayName(), buildRealmMapLore(realm,
                    accessAllowed, realmProgress.worldUnlocked(),
                    resolvedSpawn.isPresent(),
                    resolvedSpawn.map(RpgWorldManager.ResolvedSpawn::fallbackUsed).orElse(false),
                    progress.hasProfile())));
        }
        holder.inventory.setItem(4, menuItem(Material.MAP, "§bTrang " + (page + 1) + " / " + pageCount,
            List.of("§7Đại lục được hiển thị theo ID cấu hình.")));
        if (page > 0) holder.inventory.setItem(45, menuItem(Material.ARROW, "§7Trang trước", List.of()));
        holder.inventory.setItem(49, menuItem(Material.ARROW, "§7Về menu chính", List.of()));
        if (page + 1 < pageCount) holder.inventory.setItem(53, menuItem(Material.ARROW, "§7Trang tiếp", List.of()));
        player.openInventory(holder.inventory);
    }

    private void openStoryMenu(Player player, int requestedPage) {
        List<StoryChapterLoader.StoryChapter> chapters =
            List.copyOf(plugin.getConfigManager().getStoryChapters().values());
        int pageCount = Math.max(1, (chapters.size() + 27) / 28);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        ServiceMenuHolder holder = createServiceMenu(player, MenuType.STORY, null, null, null,
            page, 54, "VnMine • Sổ truyện");
        Inventory inventory = holder.inventory;

        int firstChapter = page * 28;
        int lastChapter = Math.min(firstChapter + 28, chapters.size());
        for (int index = firstChapter; index < lastChapter; index++) {
            StoryChapterLoader.StoryChapter chapter = chapters.get(index);
            int slot = 10 + (index - firstChapter) % 7 + (index - firstChapter) / 7 * 9;
            inventory.setItem(slot, menuItem(Material.WRITTEN_BOOK,
                "§6" + chapter.order() + ". " + chapter.title(), List.of(
                    "§8ID: " + chapter.id(), "§7" + chapter.summary())));
            holder.choices.put(slot, chapter.id());
        }
        inventory.setItem(4, menuItem(Material.BOOK, "§dTrang " + (page + 1) + " / " + pageCount,
            List.of("§7Chapter được sắp xếp theo thứ tự cấu hình.")));
        if (page > 0) {
            inventory.setItem(45, menuItem(Material.ARROW, "§7Trang trước", List.of()));
        }
        inventory.setItem(49, menuItem(Material.ARROW, "§7Về menu chính", List.of()));
        if (page + 1 < pageCount) {
            inventory.setItem(53, menuItem(Material.ARROW, "§7Trang tiếp", List.of()));
        }
        if (chapters.isEmpty()) {
            inventory.setItem(22, menuItem(Material.BARRIER, "§cChưa có chapter", List.of(
                "§7Quản trị viên chưa cấu hình chapter truyện.")));
        }
        player.openInventory(inventory);
    }

    public void openStoryJournal(Player player) {
        openStoryMenu(player, 0);
    }

    private void openStoryChapter(Player player, String chapterId) {
        StoryChapterLoader.StoryChapter chapter = plugin.getConfigManager().getStoryChapters().get(chapterId);
        if (chapter == null) {
            player.sendMessage("§cKhông tìm thấy nội dung chương truyện này.");
            return;
        }
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta rawMeta = book.getItemMeta();
        if (!(rawMeta instanceof BookMeta bookMeta)) {
            player.sendMessage("§cKhông thể mở sách truyện trên máy chủ này.");
            return;
        }
        bookMeta.setTitle(chapter.title().length() <= 32
            ? chapter.title() : chapter.title().substring(0, 32));
        bookMeta.setAuthor("VnMineHardcore");
        bookMeta.setPages(chapter.pages());
        book.setItemMeta(bookMeta);
        player.closeInventory();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) player.openBook(book);
        }, 1L);
    }

    private void travelToRealm(Player player, HumanRealmDefinitionLoader.HumanRealmDefinition realm) {
        debugRpg("[RPG-DEBUG][Portal] access-check-start player=" + player.getName()
            + ", realm=" + realm.id() + ", accessType=" + realm.access().type());
        if (!plugin.getConfig().getBoolean("rpg.portal-travel.enabled", true)) {
            debugRpg("[RPG-DEBUG][Portal] travel-rejected player=" + player.getName()
                + ", realm=" + realm.id() + ", reason=disabled");
            player.sendMessage("§cDịch chuyển qua cổng hiện đang tắt.");
            return;
        }
        if (player.isDead() || plugin.getDeathListener().isPortalTravelBlocked(player)) {
            debugRpg("[RPG-DEBUG][Portal] travel-rejected player=" + player.getName()
                + ", realm=" + realm.id() + ", reason=death-or-respawn-lock");
            player.sendMessage("§cKhông thể dịch chuyển khi đang chết hoặc trong thời gian hồi sinh.");
            return;
        }
        if (plugin.getConfig().getBoolean("rpg.portal-travel.block-in-combat", true)
                && plugin.getDeathListener().isCombatTagged(player)) {
            debugRpg("[RPG-DEBUG][Portal] travel-rejected player=" + player.getName()
                + ", realm=" + realm.id() + ", reason=combat-tag");
            player.sendMessage("§cKhông thể dịch chuyển khi đang giao chiến.");
            return;
        }

        PlayerProfileDatabaseManager database = plugin.getPlayerProfileDatabaseManager();
        if (database == null) {
            debugRpg("[RPG-DEBUG][Portal] travel-rejected player=" + player.getName()
                + ", realm=" + realm.id() + ", reason=database-unavailable");
            player.sendMessage("§cHệ thống hồ sơ chưa sẵn sàng.");
            return;
        }
        String playerId = player.getUniqueId().toString();
        database.executeGameplayDatabaseTask("portal.access player=" + player.getName()
                + " realm=" + realm.id(), () -> {
            boolean activeProfile = database.getPlayerProfile(playerId)
                .filter(PlayerProfileDatabaseManager.PlayerProfile::active).isPresent();
            boolean questCompleted = false;
            if ("completed-quest".equals(realm.access().type()) && activeProfile) {
                Optional<PlayerProfileDatabaseManager.QuestProgress> progress =
                    database.getQuestProgress(playerId, realm.access().questId());
                questCompleted = progress.isPresent() && "completed".equals(progress.get().status());
            }
            return new RealmAccessData(activeProfile, questCompleted,
                isRealmAccessAllowed(realm.access(), activeProfile, questCompleted));
        }).whenComplete((access, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (failure != null) {
                plugin.getLogger().log(Level.SEVERE, "[Portal] Failed to validate access to realm '" +
                    realm.id() + "' for " + player.getName(), failure);
                player.sendMessage("§cKhông thể xác minh điều kiện cổng lúc này.");
                return;
            }
            if (!access.allowed()) {
                debugRpg("[RPG-DEBUG][Portal] access-check-result player=" + player.getName()
                    + ", realm=" + realm.id() + ", allowed=false, activeProfile=" + access.activeProfile()
                    + ", questCompleted=" + access.questCompleted());
                player.sendMessage("§cĐại lục đang khóa. Điều kiện: hoàn thành nhiệm vụ " +
                    realm.access().questId() + ".");
                return;
            }
            debugRpg("[RPG-DEBUG][Portal] access-check-result player=" + player.getName()
                + ", realm=" + realm.id() + ", allowed=true, activeProfile=" + access.activeProfile()
                + ", questCompleted=" + access.questCompleted());
            beginRealmTravel(player, realm, access.activeProfile(), database);
        }));
    }

    private record RealmAccessData(boolean activeProfile, boolean questCompleted, boolean allowed) {
    }

    private void beginRealmTravel(Player player, HumanRealmDefinitionLoader.HumanRealmDefinition realm,
                                  boolean activeProfile, PlayerProfileDatabaseManager database) {
        String playerId = player.getUniqueId().toString();

        java.util.Optional<RpgWorldManager.ResolvedSpawn> destination =
            plugin.getRpgWorldManager().resolveSpawn(realm.id());
        if (destination.isEmpty() || destination.get().fallbackUsed()) {
            plugin.getLogger().warning("[Portal] Refused travel to realm '" + realm.id() +
                "': primary world/spawn is not available.");
            player.sendMessage("§cWorld hoặc spawn của đại lục chưa sẵn sàng. Tiến trình không thay đổi.");
            return;
        }

        debugRpg("[RPG-DEBUG][Portal] teleport-start player=" + player.getName() + ", realm=" + realm.id()
            + ", world=" + destination.get().location().getWorld().getName()
            + ", x=" + destination.get().location().getBlockX()
            + ", y=" + destination.get().location().getBlockY()
            + ", z=" + destination.get().location().getBlockZ());

        player.sendMessage("§eĐang kiểm tra điểm đến của " + realm.displayName() + "...");
        player.teleportAsync(destination.get().location(), org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN)
            .whenComplete((teleported, throwable) -> {
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                if (throwable != null || !Boolean.TRUE.equals(teleported)) {
                    plugin.getLogger().log(Level.WARNING, "[Portal] Teleport to realm '" + realm.id() +
                        "' failed; unlock state was not changed.", throwable);
                    debugRpg("[RPG-DEBUG][Portal] teleport-result player=" + player.getName()
                        + ", realm=" + realm.id() + ", success=false");
                    if (player.isOnline()) {
                        player.sendMessage("§cDịch chuyển thất bại. Tiến trình đại lục không thay đổi.");
                    }
                    return;
                }
                debugRpg("[RPG-DEBUG][Portal] teleport-result player=" + player.getName()
                    + ", realm=" + realm.id() + ", success=true");
                if (!activeProfile) {
                    if (player.isOnline()) {
                        player.sendMessage("§aĐã tới " + realm.displayName() + ". Tạo nhân vật để lưu tiến trình cá nhân.");
                    }
                    return;
                }
                database.executeGameplayDatabaseTask("portal.unlock player=" + player.getName()
                        + " realm=" + realm.id(), () -> database.unlockWorld(playerId, realm.id()))
                    .whenComplete((unlocked, saveFailure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (saveFailure != null) {
                            plugin.getLogger().log(Level.SEVERE, "[Portal] Travel to realm '" + realm.id() +
                                "' succeeded but unlock state could not be saved for " + player.getName(), saveFailure);
                            if (player.isOnline()) {
                                player.sendMessage("§eĐã tới đại lục, nhưng chưa lưu được unlock. Hãy thử mở cổng lại.");
                            }
                        } else if (player.isOnline()) {
                            debugRpg("[RPG-DEBUG][Portal] unlock-result player=" + player.getName()
                                + ", realm=" + realm.id() + ", changed=" + unlocked);
                            player.sendMessage("§aĐã tới " + realm.displayName() + ". Tiến trình đại lục đã được lưu.");
                        }
                    }));
                });
            });
    }

   private void debugRpg(String message) {
      if (plugin.getConfig().getBoolean("logging.console-debug", false)) {
         plugin.getLogger().info(message);
      }
   }

   public void openAdminMenu(Player player) {
      if (this.hasAdminMenuAccess(player)) {
         ServiceMenuHolder holder = this.createServiceMenu(player, MenuType.ADMIN, null, null, null, 0, 54,
             "VnMine • Quản trị");
         holder.inventory.setItem(4, this.menuItem(Material.COMMAND_BLOCK, "§cCông cụ quản trị",
             List.of("§7Chọn nhóm chức năng cần quản lý.")));
         if (hasAnyPermission(player, "vnmine.command.disasters", "vnmine.command.bosses",
                 "vnmine.command.reload", "vnmine.command.time.manage", "vnmine.command.day.manage",
                 "vnmine.command.spawn.manage", "vnmine.command.scoreboard.reset")) {
             holder.inventory.setItem(10, this.menuItem(Material.REDSTONE, "§eMáy chủ",
                 List.of("§7Sự kiện, boss, thời gian và spawn.")));
         }
         boolean hasLegacyPlayerTools = hasAnyPermission(player, "vnmine.command.teleport.other",
                 "vnmine.command.users.manage", "vnmine.command.fly", "vnmine.command.nodame",
                 "vnmine.command.gamemode", "vnmine.command.death.reset", "vnmine.command.death.history");
         boolean hasPlayerAdmin = player.hasPermission("vnmine.command.playeradmin");
         if (hasPlayerAdmin) {
             holder.inventory.setItem(12, this.menuItem(Material.PLAYER_HEAD, "§bNgười chơi",
                 List.of("§7Chọn người chơi online hoặc tìm người offline.",
                     "§eMở menu thao tác hồ sơ người chơi.")));
         } else if (hasLegacyPlayerTools) {
             holder.inventory.setItem(12, this.menuItem(Material.PLAYER_HEAD, "§bNgười chơi",
                 List.of("§7Dịch chuyển, chế độ chơi và công cụ người chơi.")));
         }
         if (hasAnyPermission(player, "vnmine.command.groups.manage", "vnmine.command.users.manage")) {
             holder.inventory.setItem(14, this.menuItem(Material.BOOKSHELF, "§dQuản lý quyền",
                 List.of("§7Nhóm, permission và hồ sơ người chơi.")));
         }
         if (isAdminRpgToolsEnabled() && (player.hasPermission("vnmine.command.skillbook.admin")
                 || player.hasPermission("vnmine.command.progression.admin"))) {
             holder.inventory.setItem(16, this.menuItem(Material.ENCHANTED_BOOK, "§6RPG",
                 List.of("§7Cấp sách kỹ năng, EXP và level.")));
         }
         if (player.hasPermission("vnmine.command.army.admin")) {
             holder.inventory.setItem(22, this.menuItem(Material.IRON_SWORD, "§6Quân đội",
                 List.of("§7Cấp lính, token và trang bị.")));
         }
         if (player.hasPermission("vnmine.command.quest.admin")) {
             holder.inventory.setItem(18, this.menuItem(Material.FILLED_MAP, "§bQuản lý nhiệm vụ",
                 List.of("§7Tạo, sửa, bật/tắt và xóa quest.")));
         }
         if (player.hasPermission("vnmine.command.npc.admin")) {
             holder.inventory.setItem(20, this.menuItem(Material.VILLAGER_SPAWN_EGG, "§eQuản lý NPC",
                 List.of("§7Tạo, sửa, di chuyển và xóa NPC.")));
         }
         if (hasPlayerAdmin && hasLegacyPlayerTools) {
            holder.inventory.setItem(24, this.menuItem(Material.COMPASS, "§dTiện ích người chơi",
                List.of("§7Dịch chuyển, chế độ chơi và tiện ích khác.")));
         }
         holder.inventory.setItem(49, this.menuItem(Material.ARROW, "§7Menu chính", List.of()));
         player.openInventory(holder.inventory);
      }
   }

    private boolean hasAdminMenuAccess(Player player) {
        return List.of(ADMIN_PERMISSION, "vnmine.command.disasters", "vnmine.command.bosses",
            "vnmine.command.reload", "vnmine.command.time.manage", "vnmine.command.day.manage",
            "vnmine.command.spawn.manage", "vnmine.command.teleport.other", "vnmine.command.groups.manage",
            "vnmine.command.users.manage", "vnmine.command.fly", "vnmine.command.nodame",
            "vnmine.command.gamemode", "vnmine.command.scoreboard.reset", "vnmine.command.death.reset",
            "vnmine.command.skillbook.admin", "vnmine.command.progression.admin",
            "vnmine.command.quest.admin", "vnmine.command.npc.admin", "vnmine.command.playeradmin")
            .stream().anyMatch(player::hasPermission)
            || player.hasPermission("vnmine.command.death.history");
    }

    private boolean isAdminRpgToolsEnabled() {
        return plugin.getConfig().getBoolean("menus.admin.rpg-tools.enabled", true);
    }

    private boolean hasAnyPermission(Player player, String... permissions) {
        for (String permission : permissions) {
            if (player.hasPermission(permission)) return true;
        }
        return false;
    }

    private void setAdminCategoryItem(Player player, ServiceMenuHolder holder, int slot, String permission,
                                      Material material, String title, List<String> lore) {
        if (player.hasPermission(permission)) {
            holder.inventory.setItem(slot, this.menuItem(material, title, lore));
        }
    }

    private void openAdminCategory(Player player, String category) {
        ServiceMenuHolder holder = createServiceMenu(player, MenuType.ADMIN_CATEGORY, null, category, null,
            0, 54, "Quản trị • " + category);
        switch (category) {
            case "server" -> {
                setAdminCategoryItem(player, holder, 10, "vnmine.command.disasters", Material.CLOCK, "§eGọi thiên tai", List.of());
                setAdminCategoryItem(player, holder, 11, "vnmine.command.disasters", Material.BARRIER, "§cDừng thiên tai", List.of());
                setAdminCategoryItem(player, holder, 12, "vnmine.command.bosses", Material.DRAGON_HEAD, "§cGọi boss", List.of());
                setAdminCategoryItem(player, holder, 13, "vnmine.command.bosses", Material.DIAMOND_SWORD, "§4Kết thúc boss", List.of());
                setAdminCategoryItem(player, holder, 14, "vnmine.command.reload", Material.REDSTONE, "§eTải lại cấu hình", List.of());
                setAdminCategoryItem(player, holder, 15, "vnmine.command.spawn.manage", Material.COMPASS, "§bVề spawn", List.of());
                setAdminCategoryItem(player, holder, 16, "vnmine.command.spawn.manage", Material.LODESTONE, "§bĐặt spawn world", List.of());
                setAdminCategoryItem(player, holder, 19, "vnmine.command.time.manage", Material.CLOCK, "§eThời lượng ngày/đêm", List.of());
                setAdminCategoryItem(player, holder, 20, "vnmine.command.day.manage", Material.SUNFLOWER, "§eĐặt ngày", List.of());
                setAdminCategoryItem(player, holder, 21, "vnmine.command.spawn.manage", Material.SHIELD, "§6Spawn protection", List.of());
                setAdminCategoryItem(player, holder, 22, "vnmine.command.scoreboard.reset", Material.REDSTONE_BLOCK, "§cReset scoreboard", List.of());
                setAdminCategoryItem(player, holder, 23, "vnmine.command.spawn.manage", Material.MAP, "§bĐi tới spawn world", List.of());
            }
            case "players" -> {
                setAdminCategoryItem(player, holder, 10, "vnmine.command.teleport.other", Material.ENDER_PEARL, "§bDịch chuyển người chơi", List.of());
                setAdminCategoryItem(player, holder, 11, "vnmine.command.users.manage", Material.PLAYER_HEAD, "§dQuản lý người chơi", List.of());
                setAdminCategoryItem(player, holder, 12, "vnmine.command.fly", Material.FEATHER, "§aBật/tắt bay", List.of());
                setAdminCategoryItem(player, holder, 13, "vnmine.command.nodame", Material.BARRIER, "§cMiễn sát thương", List.of());
                setAdminCategoryItem(player, holder, 14, "vnmine.command.gamemode", Material.IRON_PICKAXE, "§eSurvival", List.of());
                setAdminCategoryItem(player, holder, 15, "vnmine.command.gamemode", Material.GRASS_BLOCK, "§aCreative", List.of());
                setAdminCategoryItem(player, holder, 16, "vnmine.command.gamemode", Material.MAP, "§6Adventure", List.of());
                setAdminCategoryItem(player, holder, 19, "vnmine.command.gamemode", Material.ENDER_EYE, "§7Spectator", List.of());
                setAdminCategoryItem(player, holder, 20, "vnmine.command.death.reset", Material.SKELETON_SKULL, "§cReset số lần chết", List.of());
                if (player.hasPermission("vnmine.command.death.history")) {
                    holder.inventory.setItem(21, this.menuItem(Material.CLOCK, "§cLịch sử chết", List.of()));
                }
            }
            case "permissions" -> {
                setAdminCategoryItem(player, holder, 11, "vnmine.command.groups.manage",
                    Material.BOOKSHELF, "§dQuản lý nhóm/quyền", List.of());
                setAdminCategoryItem(player, holder, 15, "vnmine.command.users.manage",
                    Material.PLAYER_HEAD, "§dQuản lý người chơi", List.of());
            }
            case "army" -> {
                if (player.hasPermission("vnmine.command.army.admin")) {
                    holder.inventory.setItem(11, this.menuItem(Material.IRON_SWORD, "§6Cấp lính / token", List.of()));
                    holder.inventory.setItem(15, this.menuItem(Material.NETHERITE_CHESTPLATE, "§6Give item lính",
                        List.of("§7Chọn người chơi, class và vật phẩm.")));
                }
            }
            case "rpg" -> {
                if (isAdminRpgToolsEnabled() && player.hasPermission("vnmine.command.skillbook.admin")) {
                    holder.inventory.setItem(11, this.menuItem(Material.ENCHANTED_BOOK, "§dGive sách kỹ năng", List.of()));
                    holder.inventory.setItem(13, this.menuItem(Material.WRITABLE_BOOK, "§dSách skill khởi đầu",
                        List.of("§7Gán sách theo từng race và class.", "§eMở giao diện cấu hình.")));
                }
                if (isAdminRpgToolsEnabled() && player.hasPermission("vnmine.command.progression.admin")) {
                    holder.inventory.setItem(15, this.menuItem(Material.EXPERIENCE_BOTTLE, "§6Quản lý EXP / level", List.of()));
                }
            }
            default -> {
                player.sendMessage("§cNhóm quản trị không hợp lệ.");
                return;
            }
        }
        holder.inventory.setItem(49, this.menuItem(Material.ARROW, "§7Quay lại", List.of()));
        player.openInventory(holder.inventory);
    }

    private void openAdminRpgPlayers(Player player, String action, int page) {
        List<? extends Player> online = Bukkit.getOnlinePlayers().stream()
            .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER)).toList();
        int pages = Math.max(1, (online.size() + 44) / 45);
        int safePage = Math.max(0, Math.min(page, pages - 1));
        ServiceMenuHolder holder = createServiceMenu(player, MenuType.ADMIN_RPG_PLAYERS, null, action, null,
            safePage, 54, "RPG • Người chơi " + (safePage + 1) + "/" + pages);
        for (int index = safePage * 45; index < Math.min((safePage + 1) * 45, online.size()); index++) {
            Player target = online.get(index);
            int slot = index - safePage * 45;
            holder.choices.put(slot, target.getUniqueId().toString());
            holder.inventory.setItem(slot, menuItem(Material.PLAYER_HEAD, "§e" + target.getName(),
                List.of("§7Chọn người chơi này.")));
        }
        if (safePage > 0) holder.inventory.setItem(45, menuItem(Material.ARROW, "§eTrang trước", List.of()));
        holder.inventory.setItem(49, menuItem(Material.ARROW, "§7Quay lại RPG", List.of()));
        if (safePage + 1 < pages) holder.inventory.setItem(53, menuItem(Material.ARROW, "§eTrang sau", List.of()));
        player.openInventory(holder.inventory);
    }

    private void openAdminSkillBooks(Player player, UUID target, int page) {
        var books = plugin.getSkillManager().getSkillBooks();
        int pages = Math.max(1, (books.size() + 44) / 45);
        int safePage = Math.max(0, Math.min(page, pages - 1));
        ServiceMenuHolder holder = createServiceMenu(player, MenuType.ADMIN_SKILLBOOKS, target, null, null,
            safePage, 54, "RPG • Sách kỹ năng " + (safePage + 1) + "/" + pages);
        for (int index = safePage * 45; index < Math.min((safePage + 1) * 45, books.size()); index++) {
            var book = books.get(index);
            int slot = index - safePage * 45;
            holder.choices.put(slot, book.id());
            holder.inventory.setItem(slot, menuItem(book.material(), "§e" + book.displayName(),
                List.of("§7ID: §f" + book.id(), "§7Nhấn để cấp cho người chơi.")));
        }
        if (safePage > 0) holder.inventory.setItem(45, menuItem(Material.ARROW, "§eTrang trước", List.of()));
        holder.inventory.setItem(49, menuItem(Material.ARROW, "§7Quay lại", List.of()));
        if (safePage + 1 < pages) holder.inventory.setItem(53, menuItem(Material.ARROW, "§eTrang sau", List.of()));
        player.openInventory(holder.inventory);
    }

    public void openAdminProgressionForPlayer(Player player, UUID target) {
        if (!player.hasPermission("vnmine.command.progression.admin")) {
            player.sendMessage("§cBạn không có quyền quản lý EXP/level.");
            return;
        }
        openAdminProgression(player, target);
    }

    private void openAdminProgression(Player player, UUID target) {
        ServiceMenuHolder holder = createServiceMenu(player, MenuType.ADMIN_PROGRESSION, target, null, null,
            0, 27, "RPG • EXP / level");
        holder.choices.put(10, "exp:100");
        holder.choices.put(11, "exp:500");
        holder.choices.put(12, "exp:1000");
        holder.choices.put(14, "level:1");
        holder.choices.put(15, "level:5");
        holder.inventory.setItem(10, menuItem(Material.EXPERIENCE_BOTTLE, "§a+100 EXP", List.of()));
        holder.inventory.setItem(11, menuItem(Material.EXPERIENCE_BOTTLE, "§a+500 EXP", List.of()));
        holder.inventory.setItem(12, menuItem(Material.EXPERIENCE_BOTTLE, "§a+1000 EXP", List.of()));
        holder.inventory.setItem(14, menuItem(Material.ENCHANTED_BOOK, "§6+1 level", List.of()));
        holder.inventory.setItem(15, menuItem(Material.ENCHANTED_BOOK, "§6+5 levels", List.of()));
        holder.inventory.setItem(22, menuItem(Material.ARROW, "§7Quay lại", List.of()));
        player.openInventory(holder.inventory);
    }

    private void openAdminArmyPlayers(Player player, int page) {
        List<? extends Player> online = Bukkit.getOnlinePlayers().stream()
            .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER)).toList();
        int pages = Math.max(1, (online.size() + 44) / 45);
        int safePage = Math.max(0, Math.min(page, pages - 1));
        ServiceMenuHolder holder = createServiceMenu(player, MenuType.ADMIN_PLAYERS, null, null, null,
            safePage, 54, "Cấp lính • Người chơi " + (safePage + 1) + "/" + pages);
        for (int index = safePage * 45; index < Math.min((safePage + 1) * 45, online.size()); index++) {
            Player target = online.get(index);
            int slot = index - safePage * 45;
            holder.choices.put(slot, target.getUniqueId().toString());
            holder.inventory.setItem(slot, menuItem(Material.PLAYER_HEAD, "§e" + target.getName(),
                List.of("§7Chọn người chơi này.")));
        }
        if (safePage > 0) holder.inventory.setItem(45, menuItem(Material.ARROW, "§eTrang trước", List.of()));
        holder.inventory.setItem(49, menuItem(Material.ARROW, "§7Quay lại", List.of()));
        if (safePage + 1 < pages) holder.inventory.setItem(53, menuItem(Material.ARROW, "§eTrang sau", List.of()));
        player.openInventory(holder.inventory);
    }

   private void openAdminItemPlayers(Player player, int page) {
      if (player.hasPermission("vnmine.command.army.admin")) {
         List<? extends Player> online = Bukkit.getOnlinePlayers()
            .stream()
            .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
            .toList();
         int pages = Math.max(1, (online.size() + 44) / 45);
         int safePage = Math.max(0, Math.min(page, pages - 1));
         MercenaryManager.ServiceMenuHolder holder = this.createServiceMenu(
            player, MercenaryManager.MenuType.ITEM_GIVE_PLAYERS, null, null, null, safePage, 54, "Give item lính • Người chơi " + (safePage + 1) + "/" + pages
         );

         for (int index = safePage * 45; index < Math.min((safePage + 1) * 45, online.size()); index++) {
            Player target = online.get(index);
            int slot = index - safePage * 45;
            holder.choices.put(slot, target.getUniqueId().toString());
            holder.inventory.setItem(slot, this.menuItem(Material.PLAYER_HEAD, "§e" + target.getName(), List.of("§7Chọn người nhận trang bị lính.")));
         }

         if (safePage > 0) {
            holder.inventory.setItem(45, this.menuItem(Material.ARROW, "§eTrang trước", List.of()));
         }

         holder.inventory.setItem(49, this.menuItem(Material.ARROW, "§7Quay lại admin", List.of()));
         if (safePage + 1 < pages) {
            holder.inventory.setItem(53, this.menuItem(Material.ARROW, "§eTrang sau", List.of()));
         }

         player.openInventory(holder.inventory);
      }
   }

   private void openItemGearClassMenu(Player player, UUID target) {
      MercenaryManager.ServiceMenuHolder holder = this.createServiceMenu(
         player, MercenaryManager.MenuType.ITEM_GIVE_CLASS, target, null, null, 0, 54, "Give item lính • Class"
      );
      List<String> classes = this.getClassIds();

      for (int index = 0; index < classes.size() && index < 28; index++) {
         String classId = classes.get(index);
         int slot = 10 + index / 7 * 9 + index % 7;
         Material icon = Material.matchMaterial(this.settings.getString("classes." + classId + ".menu-icon", "IRON_SWORD"));
         if (icon == null || icon.isAir()) {
            icon = Material.IRON_SWORD;
         }

         holder.choices.put(slot, classId);
         holder.inventory.setItem(slot, this.menuItem(icon, "§e" + this.classDisplay(classId), List.of("§7Chọn class để tạo trang bị.")));
      }

      holder.inventory.setItem(49, this.menuItem(Material.ARROW, "§7Quay lại", List.of("§7Chọn người chơi")));
      player.openInventory(holder.inventory);
   }

   private void openItemGearMaterialMenu(Player player, UUID target, String classId) {
      MercenaryManager.ServiceMenuHolder holder = this.createServiceMenu(
         player, MercenaryManager.MenuType.ITEM_GIVE_MATERIAL, target, null, classId, 0, 54, this.classDisplay(classId) + " • Vật liệu"
      );
      Material[] icons = new Material[]{
         Material.LEATHER_CHESTPLATE,
         Material.IRON_INGOT,
         Material.IRON_CHESTPLATE,
         Material.GOLD_INGOT,
         Material.DIAMOND_CHESTPLATE,
         Material.NETHERITE_CHESTPLATE
      };

      for (int index = 0; index < GEAR_MATERIALS.size(); index++) {
         String material = GEAR_MATERIALS.get(index);
         int slot = 10 + index;
         holder.choices.put(slot, material);
         holder.inventory.setItem(slot, this.menuItem(icons[index], "§e" + this.gearMaterialDisplay(material), List.of("§7Chọn cấp vật liệu.")));
      }

      holder.inventory.setItem(49, this.menuItem(Material.ARROW, "§7Quay lại", List.of("§7Chọn class")));
      player.openInventory(holder.inventory);
   }

   private void openItemGearStarsMenu(Player player, UUID target, String classId, String material) {
      MercenaryManager.ServiceMenuHolder holder = this.createServiceMenu(
         player, MercenaryManager.MenuType.ITEM_GIVE_STARS, target, null, classId, 0, 54, this.gearMaterialDisplay(material) + " • Số sao"
      );
      holder.gearMaterial = material;

      for (int stars = 1; stars <= 5; stars++) {
         int slot = 19 + stars;
         holder.choices.put(slot, Integer.toString(stars));
         holder.inventory.setItem(slot, this.menuItem(Material.NETHER_STAR, "§e" + "★".repeat(stars), List.of("§7Chọn bậc sao của set.")));
      }

      holder.inventory.setItem(49, this.menuItem(Material.ARROW, "§7Quay lại", List.of("§7Chọn vật liệu")));
      player.openInventory(holder.inventory);
   }

   private void openItemGearSetMenu(Player player, UUID target, String classId, String material, int stars) {
      MercenaryManager.ServiceMenuHolder holder = this.createServiceMenu(
         player, MercenaryManager.MenuType.ITEM_GIVE_SET, target, null, classId, 0, 54, this.classDisplay(classId) + " • Chọn set"
      );
      holder.gearMaterial = material;
      holder.gearStars = stars;
      List<String> sets = this.gearSetIds(classId);

      for (int index = 0; index < sets.size() && index < 28; index++) {
         String setId = sets.get(index);
         int slot = 10 + index / 7 * 9 + index % 7;
         String path = "gear-system.sets." + classId + "." + setId;
         String displayName = this.settings.getString(path + ".display-name", setId);
         holder.choices.put(slot, setId);
         holder.inventory.setItem(slot, this.menuItem(Material.CHEST, "§e" + displayName, List.of("§7ID: §f" + setId, "§7Chọn set trang bị này.")));
      }

      holder.inventory.setItem(49, this.menuItem(Material.ARROW, "§7Quay lại", List.of("§7Chọn số sao")));
      player.openInventory(holder.inventory);
   }

   private void openItemGearSlotMenu(Player player, UUID target, String classId, String material, int stars, String setId) {
      MercenaryManager.ServiceMenuHolder holder = this.createServiceMenu(
         player, MercenaryManager.MenuType.ITEM_GIVE_SLOT, target, null, classId, 0, 54, this.classDisplay(classId) + " • Chọn món"
      );
      holder.gearMaterial = material;
      holder.gearStars = stars;
      holder.gearSet = setId;

      List<EquipmentSlot> classSlots = this.soldierGearSlots(classId);
      for (int index = 0; index < classSlots.size(); index++) {
         EquipmentSlot equipmentSlot = classSlots.get(index);
         ItemStack preview = this.createSoldierGearItem(classId, material, stars, setId, equipmentSlot);
         int slot = 10 + index;
         holder.choices.put(slot, this.gearSlotCommandName(equipmentSlot));
         holder.inventory.setItem(slot, this.menuItem(preview.getType(), this.gearSlotDisplayName(equipmentSlot), preview.getItemMeta().getLore()));
      }

      holder.inventory.setItem(25, this.menuItem(Material.CHEST, "§aGive trọn set", List.of("§7Cấp đủ " + classSlots.size() + " ô trang bị hợp lệ.")));
      holder.choices.put(25, "set");
      holder.inventory.setItem(49, this.menuItem(Material.ARROW, "§7Quay lại", List.of("§7Chọn set")));
      player.openInventory(holder.inventory);
   }

   private ItemStack createSoldierGearItem(String classId, String material, int stars, String setId, EquipmentSlot slot) {
      Material itemMaterial = this.soldierGearMaterial(classId, material, slot);
      ItemStack item = new ItemStack(itemMaterial);
      ItemMeta meta = item.getItemMeta();
      String setName = this.settings.getString("gear-system.sets." + classId + "." + setId + ".display-name", setId);
      meta.setDisplayName("§b" + this.gearSlotDisplayName(slot) + " §7• §e" + this.gearMaterialDisplay(material) + " " + "★".repeat(stars));
      List<String> lore = new ArrayList<>(
         List.of(
            "§7Dành riêng cho lính: §f" + this.classDisplay(classId),
            "§7Set: §f" + setName + " §8(" + setId + ")",
            "§7Vật liệu: §f" + this.gearMaterialDisplay(material) + " §8| §7Bậc sao: §e" + stars,
            "§7Ô trang bị: §f" + this.gearSlotDisplayName(slot)
         )
      );
      meta.getPersistentDataContainer().set(this.soldierGearKey, PersistentDataType.BYTE, (byte)1);
      meta.getPersistentDataContainer().set(this.rpgSellValueKey, PersistentDataType.LONG,
         (long)this.generatedGearSellValue);
      meta.getPersistentDataContainer().set(this.soldierGearClassKey, PersistentDataType.STRING, classId);
      meta.getPersistentDataContainer().set(this.soldierGearMaterialKey, PersistentDataType.STRING, material);
      meta.getPersistentDataContainer().set(this.soldierGearStarsKey, PersistentDataType.INTEGER, stars);
      meta.getPersistentDataContainer().set(this.soldierGearSetKey, PersistentDataType.STRING, setId);
      meta.getPersistentDataContainer().set(this.soldierGearSlotKey, PersistentDataType.STRING, slot.name());
      item.setItemMeta(meta);
      lore.addAll(this.soldierGearLore(this.soldierGearItemStats(item)));
      String roleLore = soldierGearRoleLore(classId, material, stars, slot);
      if (roleLore != null) lore.add(roleLore);
      lore.add("§8Item dành cho lính đánh thuê; người chơi không thể trang bị.");
      lore.add("§8System merchant: " + this.generatedGearSellValue + " VNCOIN/item.");
      meta = item.getItemMeta();
      meta.setLore(lore);
      item.setItemMeta(meta);
      return item;
   }

   private String soldierGearRoleLore(String classId, String material, int stars, EquipmentSlot slot) {
      if (slot == EquipmentSlot.OFF_HAND) {
         return switch (classId) {
            case "archer" -> "§eBùa may mắn: cộng sát thương đòn bắn thường bằng cung.";
            case "assassin", "berserker" -> "§eSong vũ khí: chỉ số gear hai tay được cộng dồn.";
            case "druid" -> "§aSách phép: +" + Math.min(2, gearQuality(material, stars) / 4) + " Vex tối đa theo vật liệu/sao.";
            case "frost_mage" -> "§bSách phép: tăng sát thương băng và hiệu quả kỹ năng.";
            case "guardian", "paladin" -> "§bKhiên: chỉ số phòng thủ của bộ gear được cộng vào lính.";
            case "medic" -> "§dSách phép: tăng hiệu quả hồi máu và giảm hồi chiêu.";
            case "pyromancer" -> "§cSách phép: tăng sát thương hỏa và hiệu quả kỹ năng.";
            case "ranger" -> "§eỐng nhòm: +" + Math.min(3, gearQuality(material, stars) / 3)
               + " tên soi sáng, +" + gearQuality(material, stars) * 5 + " giây đuốc; gear tăng tầm.";
            default -> null;
         };
      }
      if (slot == EquipmentSlot.HAND) {
         return switch (classId) {
            case "archer" -> "§eCung: vũ khí chính của Xạ Thủ.";
            case "druid" -> "§aTrượng đinh ba: chất liệu hiển thị là đinh ba; sức mạnh theo bậc gear.";
            case "frost_mage" -> "§bCầu băng: quả cầu tuyết dùng làm vật phẩm tay chính.";
            case "medic" -> "§dBình thuốc: vật phẩm tay chính của Y Sư.";
            case "paladin" -> "§bGiáo: vũ khí chính của Kỵ sĩ.";
            case "pyromancer" -> "§cCầu lửa: fire charge dùng làm vật phẩm tay chính.";
            case "ranger" -> "§eNỏ: vũ khí chính của Trinh Sát.";
            default -> null;
         };
      }
      return null;
   }

   public List<AdminMercenaryItem> getAdminRpgItems() {
      ConfigurationSection classes = settings.getConfigurationSection("classes");
      if (classes == null) return List.of();
      List<AdminMercenaryItem> result = new ArrayList<>();
      for (String classId : classes.getKeys(false).stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()) {
         for (String setId : gearSetIds(classId)) {
            for (String material : GEAR_MATERIALS) {
               for (int stars = 1; stars <= 5; stars++) {
                  for (EquipmentSlot slot : soldierGearSlots(classId)) {
                     ItemStack item;
                     try {
                        item = createSoldierGearItem(classId, material, stars, setId, slot);
                     } catch (IllegalArgumentException exception) {
                        plugin.getLogger().warning("[PlayerAdmin] Skip invalid mercenary item class="
                           + classId + ", material=" + material + ", stars=" + stars + ", set=" + setId
                           + ", slot=" + slot + ": " + exception.getMessage());
                        continue;
                     }
                     String id = "army-gear/" + classId + "/" + setId + "/" + material + "/" + stars
                        + "/" + slot.name().toLowerCase(Locale.ROOT);
                     String name = gearSlotDisplayName(slot) + " • " + gearMaterialDisplay(material) + " "
                        + "★".repeat(stars) + " • " + classDisplay(classId);
                     result.add(new AdminMercenaryItem(id, name, item.getType(), item, "Lính đánh thuê • gear"));
                  }
               }
            }
         }
      }
      for (int tier : getTierIds().stream().map(MercenaryManager::parsePositiveInt).toList()) {
         result.add(new AdminMercenaryItem("army-token/any/" + tier,
            "Lệnh Triệu Hồi Lính Đánh Thuê • Tier " + tier, Material.PAPER, createToken("any", tier),
            "Lính đánh thuê • token"));
         for (String classId : classes.getKeys(false).stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()) {
            ItemStack token = createToken(classId, tier);
            result.add(new AdminMercenaryItem("army-token/" + classId + "/" + tier,
               "Lệnh Triệu Hồi " + classDisplay(classId) + " • Tier " + tier,
               Material.PAPER, token, "Lính đánh thuê • token"));
         }
      }
      return List.copyOf(result);
   }

   public record AdminMercenaryItem(String id, String name, Material material, ItemStack item, String category) { }

   public List<ItemStack> createRandomBossGearSet(String material, int stars) {
      String normalizedMaterial = this.normalizeGearMaterial(material);
      if (normalizedMaterial != null && stars >= 1 && stars <= 5) {
         ConfigurationSection classesSection = this.settings.getConfigurationSection("classes");
         if (classesSection == null) {
            throw new IllegalStateException("Mercenary classes are not configured.");
         }

         List<String> classIds = classesSection.getKeys(false)
            .stream()
            .filter(classIdx -> classIdx.matches("[a-z0-9_]+"))
            .filter(classIdx -> !this.gearSetIds(classIdx).isEmpty())
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
         if (classIds.isEmpty()) {
            throw new IllegalStateException("No mercenary classes with configured gear sets are available.");
         }

         ThreadLocalRandom random = ThreadLocalRandom.current();
         String classId = classIds.get(random.nextInt(classIds.size()));
         List<String> setIds = this.gearSetIds(classId);
         String setId = setIds.get(random.nextInt(setIds.size()));
         List<EquipmentSlot> classSlots = this.soldierGearSlots(classId);
         List<ItemStack> gearItems = new ArrayList<>(classSlots.size());

         for (EquipmentSlot slot : classSlots) {
            Material itemMaterial = this.soldierGearMaterial(classId, normalizedMaterial, slot);
            if (itemMaterial != null && !itemMaterial.isAir()) {
               ItemStack item = this.createSoldierGearItem(classId, normalizedMaterial, stars, setId, slot);
               if (item != null && !item.getType().isAir()) {
                  gearItems.add(item);
               }
            }
         }

         if (gearItems.isEmpty()) {
            throw new IllegalStateException("The selected mercenary class has no valid gear slots.");
         } else {
            return List.copyOf(gearItems);
         }
      } else {
         throw new IllegalArgumentException("Boss mercenary gear requires a valid material and 1-5 stars.");
      }
   }

   private List<String> soldierGearLore(MercenaryManager.GearStats stats) {
      List<String> lore = new ArrayList<>();
      this.addGearLore(lore, "HP", stats.health(), "", "§c");
      this.addGearLore(lore, "Giáp", stats.armor(), "", "§b");
      this.addGearLore(lore, "Sát thương tay", stats.damagePercent(), "%", "§6");
      this.addGearLore(lore, "Sát thương hỏa", stats.fireDamagePercent(), "%", "§c");
      this.addGearLore(lore, "Sát thương băng", stats.frostDamagePercent(), "%", "§b");
      this.addGearLore(lore, "Sát thương tự nhiên", stats.natureDamagePercent(), "%", "§a");
      this.addGearLore(lore, "Giảm hồi chiêu", stats.cooldownReductionPercent(), "%", "§d");
      this.addGearLore(lore, "Tầm đánh", stats.attackRangeBlocks(), " block", "§e");
      this.addGearLore(lore, "Tỉ lệ hiệu ứng", stats.effectChancePercent(), "%", "§5");
      this.addGearLore(lore, "Thời lượng hiệu ứng", stats.effectDurationSeconds(), " giây", "§5");
      this.addGearLore(lore, "Minion", stats.minions(), "", "§3");
      this.addGearLore(lore, "Hồi máu", stats.healingPercent(), "%", "§d");
      return lore.isEmpty() ? List.of("§7Chỉ số riêng theo class, vật liệu và sao.") : lore;
   }

   private void addGearLore(List<String> lore, String name, double value, String suffix, String color) {
      if (!(value <= 1.0E-4)) {
         lore.add(color + "+" + formatStat(value) + suffix + " " + name);
      }
   }

   private Material soldierGearMaterial(String classId, String tier, EquipmentSlot slot) {
      String tierPrefix = switch (tier) {
         case "leather" -> "LEATHER";
         case "copper" -> "COPPER";
         case "iron" -> "IRON";
         case "gold" -> "GOLDEN";
         case "diamond" -> "DIAMOND";
         case "netherite" -> "NETHERITE";
         default -> "IRON";
      };

      String armorSuffix = switch (slot) {
         case HEAD -> "_HELMET";
         case CHEST -> "_CHESTPLATE";
         case LEGS -> "_LEGGINGS";
         case FEET -> "_BOOTS";
         default -> null;
      };
      if (armorSuffix != null) {
         String itemName = tierPrefix + armorSuffix;
         if (tier.equals("leather")) {
            itemName = "LEATHER" + armorSuffix;
         }

         Material armor = Material.matchMaterial(itemName);
         if (armor == null && tier.equals("copper")) {
            armor = Material.matchMaterial("IRON" + armorSuffix);
         }

         return armor == null ? Material.IRON_CHESTPLATE : armor;
      } else {
         String configured = this.settings.getString("gear-system.item-materials." + classId + "." + (slot == EquipmentSlot.HAND ? "mainhand" : "offhand"));
         if (configured == null || configured.isBlank()) {
            configured = slot == EquipmentSlot.HAND
               ? this.settings.getString("classes." + classId + ".starting-weapon", "IRON_SWORD")
               : this.settings.getString("classes." + classId + ".starting-offhand", "AIR");
         }

         String weapon = configured.toUpperCase(Locale.ROOT);
         if ("AIR".equals(weapon) || "NONE".equals(weapon)) return null;
         if (slot == EquipmentSlot.HAND && ("WOODEN_SPEAR".equals(weapon) || "IRON_SPEAR".equals(weapon))) {
            weapon = "WOODEN_SPEAR".equals(weapon) ? "STICK" : "TRIDENT";
         }

         if (weapon.matches("(WOODEN|COPPER|IRON|GOLDEN|DIAMOND|NETHERITE)_.+")) {
            int separator = weapon.indexOf(95);
            String weaponSuffix = weapon.substring(separator + 1);

            String prefix = switch (tier) {
               case "leather" -> "WOODEN";
               case "copper" -> "COPPER";
               case "iron" -> "IRON";
               case "gold" -> "GOLDEN";
               case "diamond" -> "DIAMOND";
               case "netherite" -> "NETHERITE";
               default -> "IRON";
            };
            Material tieredWeapon = Material.matchMaterial(prefix + "_" + weaponSuffix);
            if (tieredWeapon != null && !tieredWeapon.isAir()) {
               return tieredWeapon;
            }
         }

         Material selected = Material.matchMaterial(weapon);
         if (selected == null || selected.isAir()) {
            selected = slot == EquipmentSlot.HAND ? Material.STICK : null;
         }

         return selected;
      }
   }

   private List<EquipmentSlot> soldierGearSlots(String classId) {
      List<EquipmentSlot> slots = new ArrayList<>(SOLDIER_GEAR_SLOTS);
      Material offhand = soldierGearMaterial(classId, "iron", EquipmentSlot.OFF_HAND);
      if (offhand != null && !offhand.isAir()) slots.add(EquipmentSlot.OFF_HAND);
      return List.copyOf(slots);
   }

   private boolean isSoldierGear(ItemStack item) {
      if (item != null && !item.getType().isAir() && item.hasItemMeta()) {
         Byte marker = (Byte)item.getItemMeta().getPersistentDataContainer().get(this.soldierGearKey, PersistentDataType.BYTE);
         return marker != null && marker == 1;
      } else {
         return false;
      }
   }

   private boolean isNativeSoldierGear(ItemStack item) {
      if (!this.isSoldierGear(item)) {
         return false;
      }

      Byte marker = (Byte)item.getItemMeta().getPersistentDataContainer().get(this.nativeSoldierGearKey, PersistentDataType.BYTE);
      return marker != null && marker == 1;
   }

   private ItemStack markNativeSoldierGear(ItemStack item, String classId, EquipmentSlot slot) {
      if (item != null && !item.getType().isAir()) {
         ItemMeta meta = item.getItemMeta();
         PersistentDataContainer pdc = meta.getPersistentDataContainer();
         pdc.set(this.soldierGearKey, PersistentDataType.BYTE, (byte)1);
         pdc.set(this.soldierGearClassKey, PersistentDataType.STRING, classId);
         pdc.set(this.soldierGearMaterialKey, PersistentDataType.STRING, "leather");
         pdc.set(this.soldierGearStarsKey, PersistentDataType.INTEGER, 1);
         pdc.set(this.soldierGearSetKey, PersistentDataType.STRING, "standard");
         pdc.set(this.soldierGearSlotKey, PersistentDataType.STRING, slot.name());
         pdc.set(this.nativeSoldierGearKey, PersistentDataType.BYTE, (byte)1);
         item.setItemMeta(meta);
         return item;
      } else {
         return null;
      }
   }

   private String soldierGearValue(ItemStack item, NamespacedKey key) {
      return !this.isSoldierGear(item) ? null : (String)item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
   }

   private int soldierGearStars(ItemStack item) {
      if (!this.isSoldierGear(item)) {
         return 0;
      }

      Integer stars = (Integer)item.getItemMeta().getPersistentDataContainer().get(this.soldierGearStarsKey, PersistentDataType.INTEGER);
      return stars == null ? 0 : stars;
   }

   private boolean validSoldierGear(ItemStack item, EquipmentSlot slot) {
      if (!this.isSoldierGear(item)) {
         return false;
      }

      String classId = this.normalizeClass(this.soldierGearValue(item, this.soldierGearClassKey));
      String itemSlot = this.soldierGearValue(item, this.soldierGearSlotKey);
      String tier = this.normalizeGearMaterial(this.soldierGearValue(item, this.soldierGearMaterialKey));
      Material expectedMaterial = classId == null || tier == null ? null : this.soldierGearMaterial(classId, tier, slot);
      return slot.name().equals(itemSlot)
         && classId != null
         && this.soldierGearSlots(classId).contains(slot)
         && tier != null
         && (slot != EquipmentSlot.OFF_HAND || expectedMaterial != null && item.getType() == expectedMaterial)
         && this.soldierGearStars(item) >= 1
         && this.soldierGearStars(item) <= 5
         && this.normalizeGearSet(this.soldierGearValue(item, this.soldierGearSetKey)) != null;
   }

   private String gearSlotDisplayName(EquipmentSlot slot) {
      return switch (slot) {
         case HEAD -> "Mũ";
         case CHEST -> "Áo giáp";
         case LEGS -> "Quần";
         case FEET -> "Giày";
         case HAND -> "Vũ khí chính";
         case OFF_HAND -> "Vũ khí tay phụ";
         default -> slot.name();
      };
   }

   private String gearSlotCommandName(EquipmentSlot slot) {
      return switch (slot) {
         case HEAD -> "helmet";
         case CHEST -> "chestplate";
         case LEGS -> "leggings";
         case FEET -> "boots";
         case HAND -> "mainhand";
         case OFF_HAND -> "offhand";
         default -> slot.name().toLowerCase(Locale.ROOT);
      };
   }

   private MercenaryManager.GearStats soldierGearBaseStats(String classId, String path) {
      String statsPath = path + ".gear-stats.";
      return new MercenaryManager.GearStats(
         this.readBoundedDouble(this.settings, statsPath + "health", 0.0, 0.0, 100.0),
         this.readBoundedDouble(this.settings, statsPath + "armor", 0.0, 0.0, 20.0),
         this.readBoundedDouble(this.settings, statsPath + "damage-percent", 0.0, 0.0, 100.0),
         this.readBoundedDouble(this.settings, statsPath + "fire-damage-percent", 0.0, 0.0, 100.0),
         this.readBoundedDouble(this.settings, statsPath + "frost-damage-percent", 0.0, 0.0, 100.0),
         this.readBoundedDouble(this.settings, statsPath + "cooldown-reduction-percent", 0.0, 0.0, 100.0),
         this.readBoundedDouble(this.settings, statsPath + "attack-range-blocks", 0.0, 0.0, 10.0),
         this.readBoundedDouble(this.settings, statsPath + "effect-chance-percent", 0.0, 0.0, 100.0),
         this.readBoundedDouble(this.settings, statsPath + "effect-duration-seconds", 0.0, 0.0, 10.0),
         this.readBoundedDouble(this.settings, statsPath + "minions", 0.0, 0.0, 10.0),
         this.readBoundedDouble(this.settings, statsPath + "healing-percent", 0.0, 0.0, 100.0),
         this.readBoundedDouble(this.settings, statsPath + "nature-damage-percent", 0.0, 0.0, 100.0)
      );
   }

   private MercenaryManager.GearStats soldierGearItemStats(ItemStack item) {
      if (this.isSoldierGear(item) && !this.isNativeSoldierGear(item)) {
         String classId = this.normalizeClass(this.soldierGearValue(item, this.soldierGearClassKey));
         String material = this.normalizeGearMaterial(this.soldierGearValue(item, this.soldierGearMaterialKey));
         int stars = this.soldierGearStars(item);
         if (classId != null && material != null && stars >= 1 && stars <= 5) {
            int materialIndex = GEAR_MATERIALS.indexOf(material);
            double materialMultiplier = this.readBoundedDouble(
               this.settings, "gear-system.material-multipliers." + material, DEFAULT_GEAR_MATERIAL_MULTIPLIERS.get(materialIndex), 0.1, 5.0
            );
            double starMultiplier = this.readBoundedDouble(
               this.settings, "gear-system.star-multipliers." + stars, DEFAULT_GEAR_STAR_MULTIPLIERS.get(stars - 1), 0.1, 5.0
            );
            return this.soldierGearBaseStats(classId, "classes." + classId)
               .scaled(materialMultiplier * starMultiplier / this.soldierGearSlots(classId).size());
         } else {
            return MercenaryManager.GearStats.ZERO;
         }
      } else {
         return MercenaryManager.GearStats.ZERO;
      }
   }

   private MercenaryManager.GearStats soldierGearSetBonus(String classId, String setId, int pieces, double scaleFactor) {
      MercenaryManager.GearStats bonus = MercenaryManager.GearStats.ZERO;

      for (int threshold : List.of(2, 4, 6)) {
         if (pieces >= threshold) {
            String specific = "gear-system.sets." + classId + "." + setId + ".bonuses." + threshold + ".";
            String fallback = "gear-system.default-set-bonuses." + threshold + ".";
            bonus = bonus.plus(
               new MercenaryManager.GearStats(
                     this.readBoundedDouble(
                        this.settings, specific + "health", this.readBoundedDouble(this.settings, fallback + "health", 0.0, 0.0, 20.0), 0.0, 20.0
                     ),
                     this.readBoundedDouble(
                        this.settings, specific + "armor", this.readBoundedDouble(this.settings, fallback + "armor", 0.0, 0.0, 4.0), 0.0, 4.0
                     ),
                     this.readBoundedDouble(
                        this.settings,
                        specific + "damage-percent",
                        this.readBoundedDouble(this.settings, fallback + "damage-percent", 0.0, 0.0, 15.0),
                        0.0,
                        15.0
                     ),
                     this.readBoundedDouble(
                        this.settings,
                        specific + "fire-damage-percent",
                        this.readBoundedDouble(this.settings, fallback + "fire-damage-percent", 0.0, 0.0, 15.0),
                        0.0,
                        15.0
                     ),
                     this.readBoundedDouble(
                        this.settings,
                        specific + "frost-damage-percent",
                        this.readBoundedDouble(this.settings, fallback + "frost-damage-percent", 0.0, 0.0, 15.0),
                        0.0,
                        15.0
                     ),
                     this.readBoundedDouble(
                        this.settings,
                        specific + "cooldown-reduction-percent",
                        this.readBoundedDouble(this.settings, fallback + "cooldown-reduction-percent", 0.0, 0.0, 20.0),
                        0.0,
                        20.0
                     ),
                     this.readBoundedDouble(
                        this.settings,
                        specific + "attack-range-blocks",
                        this.readBoundedDouble(this.settings, fallback + "attack-range-blocks", 0.0, 0.0, 1.5),
                        0.0,
                        1.5
                     ),
                     this.readBoundedDouble(
                        this.settings,
                        specific + "effect-chance-percent",
                        this.readBoundedDouble(this.settings, fallback + "effect-chance-percent", 0.0, 0.0, 10.0),
                        0.0,
                        10.0
                     ),
                     this.readBoundedDouble(
                        this.settings,
                        specific + "effect-duration-seconds",
                        this.readBoundedDouble(this.settings, fallback + "effect-duration-seconds", 0.0, 0.0, 1.0),
                        0.0,
                        1.0
                     ),
                     this.readBoundedDouble(
                        this.settings, specific + "minions", this.readBoundedDouble(this.settings, fallback + "minions", 0.0, 0.0, 1.0), 0.0, 1.0
                     ),
                     this.readBoundedDouble(
                        this.settings,
                        specific + "healing-percent",
                        this.readBoundedDouble(this.settings, fallback + "healing-percent", 0.0, 0.0, 15.0),
                        0.0,
                        15.0
                     ),
                     this.readBoundedDouble(
                        this.settings,
                        specific + "nature-damage-percent",
                        this.readBoundedDouble(this.settings, fallback + "nature-damage-percent", 0.0, 0.0, 15.0),
                        0.0,
                        15.0
                     )
                  )
                  .scaled(scaleFactor)
            );
         }
      }

      return bonus;
   }

   private MercenaryManager.GearStats soldierGearStats(MercenaryManager.Unit unit) {
      MercenaryManager.GearStats total = MercenaryManager.GearStats.ZERO;
      Map<String, Integer> matchingSets = new HashMap<>();
      Map<String, Double> setScales = new HashMap<>();

      for (EquipmentSlot slot : EQUIPMENT_SLOTS) {
         ItemStack item = unit.equipment.get(slot);
         if (this.validSoldierGear(item, slot)) {
            total = total.plus(this.soldierGearItemStats(item));
            String classId = this.soldierGearValue(item, this.soldierGearClassKey);
            String material = this.soldierGearValue(item, this.soldierGearMaterialKey);
            int stars = this.soldierGearStars(item);
            String setId = this.soldierGearValue(item, this.soldierGearSetKey);
            String setKey = classId + "|" + material + "|" + stars + "|" + setId;
            matchingSets.merge(setKey, 1, Integer::sum);
            int materialIndex = GEAR_MATERIALS.indexOf(material);
            double scale = this.readBoundedDouble(
                  this.settings, "gear-system.material-multipliers." + material, DEFAULT_GEAR_MATERIAL_MULTIPLIERS.get(materialIndex), 0.1, 5.0
               )
               * this.readBoundedDouble(this.settings, "gear-system.star-multipliers." + stars, DEFAULT_GEAR_STAR_MULTIPLIERS.get(stars - 1), 0.1, 5.0);
            setScales.merge(setKey, scale, Math::max);
         }
      }

      for (Entry<String, Integer> entry : matchingSets.entrySet()) {
         if (entry.getValue() >= 2) {
            String[] parts = entry.getKey().split("\\|", 4);
            total = total.plus(this.soldierGearSetBonus(parts[0], parts[3], entry.getValue(), setScales.getOrDefault(entry.getKey(), 1.0)));
         }
      }

      return this.capSoldierGearStats(total);
   }

   private MercenaryManager.GearStats capSoldierGearStats(MercenaryManager.GearStats stats) {
      return new MercenaryManager.GearStats(
         Math.min(stats.health(), this.readBoundedDouble(this.settings, "gear-system.caps.health", 20.0, 0.0, 100.0)),
         Math.min(stats.armor(), this.readBoundedDouble(this.settings, "gear-system.caps.armor", 4.0, 0.0, 20.0)),
         Math.min(stats.damagePercent(), this.readBoundedDouble(this.settings, "gear-system.caps.damage-percent", 15.0, 0.0, 100.0)),
         Math.min(stats.fireDamagePercent(), this.readBoundedDouble(this.settings, "gear-system.caps.fire-damage-percent", 15.0, 0.0, 100.0)),
         Math.min(stats.frostDamagePercent(), this.readBoundedDouble(this.settings, "gear-system.caps.frost-damage-percent", 15.0, 0.0, 100.0)),
         Math.min(stats.cooldownReductionPercent(), this.readBoundedDouble(this.settings, "gear-system.caps.cooldown-reduction-percent", 20.0, 0.0, 80.0)),
         Math.min(stats.attackRangeBlocks(), this.readBoundedDouble(this.settings, "gear-system.caps.attack-range-blocks", 1.5, 0.0, 10.0)),
         Math.min(stats.effectChancePercent(), this.readBoundedDouble(this.settings, "gear-system.caps.effect-chance-percent", 10.0, 0.0, 100.0)),
         Math.min(stats.effectDurationSeconds(), this.readBoundedDouble(this.settings, "gear-system.caps.effect-duration-seconds", 1.0, 0.0, 30.0)),
         Math.min(stats.minions(), this.readBoundedDouble(this.settings, "gear-system.caps.minions", 1.0, 0.0, 10.0)),
         Math.min(stats.healingPercent(), this.readBoundedDouble(this.settings, "gear-system.caps.healing-percent", 15.0, 0.0, 100.0)),
         Math.min(stats.natureDamagePercent(), this.readBoundedDouble(this.settings, "gear-system.caps.nature-damage-percent", 15.0, 0.0, 100.0))
      );
   }

    private void openTimeMenu(Player player) {
        ServiceMenuHolder holder = createServiceMenu(player, MenuType.ADMIN_TIME, null, null, null,
            0, 27, "Admin • Thời lượng ngày/đêm");
        holder.inventory.setItem(11, menuItem(Material.SUNFLOWER, "§eBan ngày", List.of("§7Chọn thời lượng.")));
        holder.inventory.setItem(15, menuItem(Material.BLUE_ICE, "§9Ban đêm", List.of("§7Chọn thời lượng.")));
        holder.inventory.setItem(22, menuItem(Material.ARROW, "§7Quay lại admin", List.of()));
        player.openInventory(holder.inventory);
    }

    private void openTimeDurationMenu(Player player, String period) {
        ServiceMenuHolder holder = createServiceMenu(player, MenuType.TIME_DURATION, null, period, null,
            0, 27, "Admin • " + (period.equals("day") ? "Ban ngày" : "Ban đêm"));
        int[] minutes = {10, 20, 30, 60};
        for (int index = 0; index < minutes.length; index++) {
            int slot = 10 + index;
            holder.choices.put(slot, Integer.toString(minutes[index]));
            holder.inventory.setItem(slot, menuItem(Material.CLOCK, "§e" + minutes[index] + " phút",
                List.of("§7Áp dụng toàn cục.")));
        }
        holder.inventory.setItem(22, menuItem(Material.ARROW, "§7Quay lại", List.of()));
        player.openInventory(holder.inventory);
    }

    private void openDayMenu(Player player) {
        ServiceMenuHolder holder = createServiceMenu(player, MenuType.ADMIN_DAY, null, null, null,
            0, 27, "Admin • Chọn ngày game");
        long[] days = {0, 1, 7, 30, 100, 400};
        for (int index = 0; index < days.length; index++) {
            int slot = 10 + index;
            holder.choices.put(slot, Long.toString(days[index]));
            holder.inventory.setItem(slot, menuItem(Material.SUNFLOWER, "§eNgày " + days[index],
                List.of("§7Áp dụng cho world hiện tại.")));
        }
        holder.inventory.setItem(22, menuItem(Material.ARROW, "§7Quay lại admin", List.of()));
        player.openInventory(holder.inventory);
    }

    private void openProtectionMenu(Player player) {
        ServiceMenuHolder holder = createServiceMenu(player, MenuType.ADMIN_PROTECT, null, null, null,
            0, 27, "Admin • Spawn protection");
        int[] radii = {10, 25, 50, 100};
        for (int index = 0; index < radii.length; index++) {
            int slot = 10 + index;
            holder.choices.put(slot, "protect:" + radii[index]);
            holder.inventory.setItem(slot, menuItem(Material.SHIELD, "§eBảo vệ " + radii[index] + " block",
                List.of("§7Tâm tại vị trí hiện tại.")));
        }
        holder.inventory.setItem(15, menuItem(Material.BOOK, "§bDanh sách khu vực", List.of("§7Xem các vùng đang bảo vệ.")));
        holder.inventory.setItem(16, menuItem(Material.BARRIER, "§cXóa khu vực", List.of("§7Chọn ID cần xóa.")));
        holder.inventory.setItem(22, menuItem(Material.ARROW, "§7Quay lại admin", List.of()));
        player.openInventory(holder.inventory);
    }

    private void openProtectionRemoveMenu(Player player) {
        ServiceMenuHolder holder = createServiceMenu(player, MenuType.PROTECTION_REMOVE, null, null, null,
            0, 54, "Admin • Xóa spawn protection");
        List<Integer> ids = plugin.getSpawnProtectionManager().getProtectionIds();
        for (int index = 0; index < ids.size() && index < 28; index++) {
            int id = ids.get(index);
            int slot = 10 + index / 7 * 9 + index % 7;
            holder.choices.put(slot, Integer.toString(id));
            holder.inventory.setItem(slot, menuItem(Material.BARRIER, "§cKhu vực #" + id, List.of("§eNhấn để xóa.")));
        }
        holder.inventory.setItem(49, menuItem(Material.ARROW, "§7Quay lại", List.of()));
        player.openInventory(holder.inventory);
    }

    private void openTeleportPlayers(Player player, int page) {
        List<? extends Player> online = Bukkit.getOnlinePlayers().stream()
            .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER)).toList();
        int pages = Math.max(1, (online.size() + 44) / 45);
        int safePage = Math.max(0, Math.min(page, pages - 1));
        ServiceMenuHolder holder = createServiceMenu(player, MenuType.TELEPORT_PLAYERS, null, null, null,
            safePage, 54, "Admin • Dịch chuyển người chơi");
        for (int index = safePage * 45; index < Math.min((safePage + 1) * 45, online.size()); index++) {
            Player target = online.get(index);
            int slot = index - safePage * 45;
            holder.choices.put(slot, target.getUniqueId().toString());
            holder.inventory.setItem(slot, menuItem(Material.PLAYER_HEAD, "§e" + target.getName(), List.of("§7Chọn người chơi.")));
        }
        if (safePage > 0) holder.inventory.setItem(45, menuItem(Material.ARROW, "§eTrang trước", List.of()));
        holder.inventory.setItem(49, menuItem(Material.ARROW, "§7Quay lại admin", List.of()));
        if (safePage + 1 < pages) holder.inventory.setItem(53, menuItem(Material.ARROW, "§eTrang sau", List.of()));
        player.openInventory(holder.inventory);
    }

    private void openTeleportWorlds(Player player, UUID target) {
        ServiceMenuHolder holder = createServiceMenu(player, MenuType.TELEPORT_WORLDS, target, null, null,
            0, 54, "Admin • Chọn world spawn");
        List<World> worlds = Bukkit.getWorlds();
        for (int index = 0; index < worlds.size() && index < 28; index++) {
            World world = worlds.get(index);
            int slot = 10 + index / 7 * 9 + index % 7;
            holder.choices.put(slot, world.getName());
            holder.inventory.setItem(slot, menuItem(Material.GRASS_BLOCK, "§e" + world.getName(),
                List.of("§7Dịch chuyển người chơi đến spawn.")));
        }
        holder.inventory.setItem(49, menuItem(Material.ARROW, "§7Quay lại người chơi", List.of()));
        player.openInventory(holder.inventory);
    }

    private void openAdminUserPlayers(Player player, int page) {
        List<? extends Player> online = Bukkit.getOnlinePlayers().stream()
            .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER)).toList();
        int pages = Math.max(1, (online.size() + 44) / 45);
        int safePage = Math.max(0, Math.min(page, pages - 1));
        ServiceMenuHolder holder = createServiceMenu(player, MenuType.ADMIN_USER_PLAYERS, null, null, null,
            safePage, 54, "Admin • Quyền người chơi");
        for (int index = safePage * 45; index < Math.min((safePage + 1) * 45, online.size()); index++) {
            Player target = online.get(index);
            int slot = index - safePage * 45;
            holder.choices.put(slot, target.getUniqueId().toString());
            holder.inventory.setItem(slot, menuItem(Material.PLAYER_HEAD, "§e" + target.getName(), List.of("§7Chọn người chơi.")));
        }
        if (safePage > 0) holder.inventory.setItem(45, menuItem(Material.ARROW, "§eTrang trước", List.of()));
        holder.inventory.setItem(49, menuItem(Material.ARROW, "§7Quay lại admin", List.of()));
        if (safePage + 1 < pages) holder.inventory.setItem(53, menuItem(Material.ARROW, "§eTrang sau", List.of()));
        player.openInventory(holder.inventory);
    }

    private void openAdminUserActions(Player player, UUID target) {
        ServiceMenuHolder holder = createServiceMenu(player, MenuType.ADMIN_USER_ACTION, target, null, null,
            0, 27, "Admin • Người chơi");
        holder.inventory.setItem(11, menuItem(Material.BOOK, "§eXem quyền", List.of("§7Hiển thị nhóm và quyền riêng.")));
        holder.inventory.setItem(13, menuItem(Material.BARRIER, "§cĐặt lại quyền", List.of("§7Đưa về nhóm mặc định.")));
        holder.inventory.setItem(15, menuItem(Material.NAME_TAG, "§bGán nhóm", List.of("§7Chọn nhóm cho người chơi.")));
        holder.inventory.setItem(16, menuItem(Material.PAPER, "§dQuyền cá nhân", List.of("§7Cấp hoặc thu hồi permission.")));
        holder.inventory.setItem(22, menuItem(Material.ARROW, "§7Quay lại", List.of()));
        player.openInventory(holder.inventory);
    }

    private void openAdminGroupMenu(Player player) {
        ServiceMenuHolder holder = createServiceMenu(player, MenuType.ADMIN_GROUPS, null, null, null,
            0, 27, "Admin • Nhóm quyền");
        holder.inventory.setItem(10, menuItem(Material.BOOK, "§eDanh sách nhóm", List.of()));
        holder.inventory.setItem(11, menuItem(Material.WRITABLE_BOOK, "§aTạo nhóm", List.of("§7Nhập tên nhóm trong chat.")));
        holder.inventory.setItem(12, menuItem(Material.KNOWLEDGE_BOOK, "§bThông tin nhóm", List.of("§7Chọn nhóm cần xem.")));
        holder.inventory.setItem(13, menuItem(Material.BARRIER, "§cXóa nhóm", List.of("§7Chọn nhóm cần xóa.")));
        holder.inventory.setItem(14, menuItem(Material.LIME_DYE, "§aCấp quyền nhóm", List.of("§7Chọn nhóm và permission.")));
        holder.inventory.setItem(15, menuItem(Material.RED_DYE, "§cThu hồi quyền nhóm", List.of("§7Chọn nhóm và permission.")));
        holder.inventory.setItem(22, menuItem(Material.ARROW, "§7Quay lại admin", List.of()));
        player.openInventory(holder.inventory);
    }

    private void openAdminGroupSelect(Player player, String action) {
        ServiceMenuHolder holder = createServiceMenu(player, MenuType.ADMIN_GROUP_SELECT, null, action, null,
            0, 54, "Admin • Chọn nhóm");
        List<String> groups = plugin.getPermissionManager().getGroupNames();
        for (int index = 0; index < groups.size() && index < 28; index++) {
            String group = groups.get(index);
            int slot = 10 + index / 7 * 9 + index % 7;
            holder.choices.put(slot, group);
            holder.inventory.setItem(slot, menuItem(Material.NAME_TAG, "§e" + group, List.of("§7Chọn nhóm.")));
        }
        holder.inventory.setItem(49, menuItem(Material.ARROW, "§7Quay lại", List.of()));
        player.openInventory(holder.inventory);
    }

    private void openAdminPermissionSelect(Player player, UUID target, String action, String group) {
        ServiceMenuHolder holder = createServiceMenu(player, MenuType.ADMIN_PERMISSION_SELECT, target, action,
            group, 0, 54, "Admin • Chọn permission");
        List<String> permissions = plugin.getDescription().getPermissions().stream()
            .map(org.bukkit.permissions.Permission::getName).sorted().toList();
        for (int index = 0; index < permissions.size() && index < 45; index++) {
            String permission = permissions.get(index);
            holder.choices.put(index, permission);
            holder.inventory.setItem(index, menuItem(Material.PAPER, "§e" + permission,
                List.of("§7Nhấn để áp dụng.")));
        }
        holder.inventory.setItem(49, menuItem(Material.ARROW, "§7Quay lại", List.of()));
        player.openInventory(holder.inventory);
    }

    private void openAdminUserPermissionActions(Player player, UUID target) {
        ServiceMenuHolder holder = createServiceMenu(player, MenuType.ADMIN_USER_PERMISSION_ACTIONS,
            target, null, null, 0, 27, "Admin • Quyền cá nhân");
        holder.inventory.setItem(11, menuItem(Material.LIME_DYE, "§aCấp permission", List.of()));
        holder.inventory.setItem(15, menuItem(Material.RED_DYE, "§cThu hồi permission", List.of()));
        holder.inventory.setItem(22, menuItem(Material.ARROW, "§7Quay lại", List.of()));
        player.openInventory(holder.inventory);
    }

    private void openAdminUserGroupSelect(Player player, UUID target) {
        ServiceMenuHolder holder = createServiceMenu(player, MenuType.ADMIN_USER_GROUP_SELECT, target,
            null, null, 0, 54, "Admin • Gán nhóm");
        List<String> groups = plugin.getPermissionManager().getGroupNames();
        for (int index = 0; index < groups.size() && index < 28; index++) {
            String group = groups.get(index);
            int slot = 10 + index / 7 * 9 + index % 7;
            holder.choices.put(slot, group);
            holder.inventory.setItem(slot, menuItem(Material.NAME_TAG, "§e" + group, List.of("§7Gán nhóm.")));
        }
        holder.inventory.setItem(49, menuItem(Material.ARROW, "§7Quay lại", List.of()));
        player.openInventory(holder.inventory);
    }

    private void openAdminDeathPlayers(Player player) {
        ServiceMenuHolder holder = createServiceMenu(player, MenuType.ADMIN_DEATH_PLAYERS,
            null, null, null, 0, 54, "Admin • Reset số lần chết");
        List<? extends Player> online = Bukkit.getOnlinePlayers().stream()
            .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER)).toList();
        for (int index = 0; index < online.size() && index < 45; index++) {
            Player target = online.get(index);
            holder.choices.put(index, target.getUniqueId().toString());
            holder.inventory.setItem(index, menuItem(Material.PLAYER_HEAD, "§e" + target.getName(),
                List.of("§7Reset số lần chết.")));
        }
        holder.inventory.setItem(49, menuItem(Material.ARROW, "§7Quay lại admin", List.of()));
        player.openInventory(holder.inventory);
    }

    private void openAdminSpawnWorlds(Player player) {
        ServiceMenuHolder holder = createServiceMenu(player, MenuType.ADMIN_SPAWN_WORLD,
            null, null, null, 0, 54, "Admin • Spawn world");
        List<World> worlds = Bukkit.getWorlds();
        for (int index = 0; index < worlds.size() && index < 28; index++) {
            World world = worlds.get(index);
            int slot = 10 + index / 7 * 9 + index % 7;
            holder.choices.put(slot, world.getName());
            holder.inventory.setItem(slot, menuItem(Material.GRASS_BLOCK, "§e" + world.getName(),
                List.of("§7Dịch chuyển bạn đến spawn.")));
        }
        holder.inventory.setItem(49, menuItem(Material.ARROW, "§7Quay lại admin", List.of()));
        player.openInventory(holder.inventory);
    }

    private void openAdminScoreboardConfirm(Player player) {
        ServiceMenuHolder holder = createServiceMenu(player, MenuType.ADMIN_SCOREBOARD_CONFIRM,
            null, null, null, 0, 27, "Admin • Reset scoreboard");
        holder.inventory.setItem(11, menuItem(Material.REDSTONE_BLOCK, "§cXác nhận reset",
            List.of("§7Xóa toàn bộ điểm mùa và thưởng chờ.")));
        holder.inventory.setItem(15, menuItem(Material.ARROW, "§7Hủy", List.of()));
        player.openInventory(holder.inventory);
    }

    private void openDisasterMenu(Player player) {
        List<String> ids = plugin.getDisasterManager().getDisasterIds().stream().sorted().toList();
        ServiceMenuHolder holder = createServiceMenu(player, MenuType.DISASTER_SELECT, null, null, null,
            0, 54, "Admin • Chọn thiên tai");
        for (int index = 0; index < ids.size() && index < 28; index++) {
            String id = ids.get(index);
            int slot = 10 + index / 7 * 9 + index % 7;
            holder.choices.put(slot, id);
            holder.inventory.setItem(slot, menuItem(Material.CLOCK,
                "§e" + plugin.getDisasterManager().getDisasterName(id), List.of("§7ID: §f" + id,
                    "§eNhấn để kích hoạt.")));
        }
        holder.inventory.setItem(49, menuItem(Material.ARROW, "§7Quay lại admin", List.of()));
        player.openInventory(holder.inventory);
    }

    private void openBossMenu(Player player) {
        List<String> ids = plugin.getBossEventManager().getEnabledBossIds().stream().sorted().toList();
        ServiceMenuHolder holder = createServiceMenu(player, MenuType.BOSS_SELECT, null, null, null,
            0, 54, "Admin • Chọn boss");
        for (int index = 0; index < ids.size() && index < 28; index++) {
            String id = ids.get(index);
            int slot = 10 + index / 7 * 9 + index % 7;
            holder.choices.put(slot, id);
            holder.inventory.setItem(slot, menuItem(bossMenuIcon(id),
                "§c" + plugin.getBossEventManager().getBossName(id), List.of("§7ID: §f" + id,
                    "§eNhấn để chọn level boss.")));
        }
        holder.inventory.setItem(49, menuItem(Material.ARROW, "§7Quay lại admin", List.of()));
        player.openInventory(holder.inventory);
    }

    private Material bossMenuIcon(String bossId) {
        String configuredType = plugin.getBossEventManager().getBossEntityType(bossId);
        if (configuredType == null || configuredType.isBlank()) return Material.BARRIER;
        String entityType = configuredType.toUpperCase(Locale.ROOT);
        Material head = switch (entityType) {
            case "CREEPER" -> Material.CREEPER_HEAD;
            case "ENDER_DRAGON" -> Material.DRAGON_HEAD;
            case "PIGLIN", "PIGLIN_BRUTE" -> Material.PIGLIN_HEAD;
            case "PLAYER" -> Material.PLAYER_HEAD;
            case "SKELETON" -> Material.SKELETON_SKULL;
            case "WITHER_SKELETON" -> Material.WITHER_SKELETON_SKULL;
            case "ZOMBIE" -> Material.ZOMBIE_HEAD;
            default -> null;
        };
        if (head != null) return head;
        Material spawnEgg = Material.matchMaterial(entityType + "_SPAWN_EGG");
        return spawnEgg == null ? Material.BARRIER : spawnEgg;
    }

    private void openBossLevelMenu(Player player, String bossId) {
        ServiceMenuHolder holder = createServiceMenu(player, MenuType.BOSS_LEVEL_SELECT, null, null,
            bossId, 0, 27, "Boss • Chọn level");
        for (int level = 1; level <= 10; level++) {
            int slot = 9 + level;
            holder.choices.put(slot, Integer.toString(level));
            holder.inventory.setItem(slot, menuItem(Material.NETHER_STAR, "§cLevel " + level,
                List.of("§7Sự kiện boss: §f" + plugin.getBossEventManager().getBossName(bossId),
                    "§eNhấn để gọi boss.")));
        }
        holder.inventory.setItem(22, menuItem(Material.ARROW, "§7Quay lại", List.of("§7Chọn boss")));
        player.openInventory(holder.inventory);
    }

    private void openGiveTypeMenu(Player player, UUID target) {
        ServiceMenuHolder holder = createServiceMenu(player, MenuType.GIVE_TYPE, target, null, null,
            0, 27, "Cấp cho " + Bukkit.getOfflinePlayer(target).getName());
        holder.inventory.setItem(11, menuItem(Material.IRON_SWORD, "§6Lính đánh thuê",
            List.of("§7Tạo đơn vị trực tiếp; không tiêu thụ vật phẩm.")));
        holder.inventory.setItem(15, menuItem(Material.PAPER, "§eToken triệu hồi",
            List.of("§7Token gắn lớp và tier đã chọn.")));
        holder.inventory.setItem(22, menuItem(Material.ARROW, "§7Quay lại", List.of()));
        player.openInventory(holder.inventory);
    }

    private void openClassMenu(Player player, UUID target, String itemType) {
        ServiceMenuHolder holder = createServiceMenu(player, MenuType.CLASS_SELECT, target, itemType,
            null, 0, 54, itemType.equals("hire") ? "Thuê lính • Chọn lớp" : "Cấp lính • Chọn lớp");
        List<String> classes = getClassIds();
        for (int index = 0; index < classes.size() && index < 28; index++) {
            String classId = classes.get(index);
            int slot = 10 + index / 7 * 9 + index % 7;
            Material icon = Material.matchMaterial(settings.getString("classes." + classId + ".menu-icon", "IRON_SWORD"));
            if (icon == null || icon.isAir()) icon = Material.IRON_SWORD;
            holder.choices.put(slot, classId);
            holder.inventory.setItem(slot, menuItem(icon, "§e" + classDisplay(classId), classSummary(classId)));
        }
        holder.inventory.setItem(49, menuItem(Material.ARROW, "§7Quay lại",
            List.of(itemType.equals("hire") ? "§7Menu chính" : "§7Chọn loại item")));
        player.openInventory(holder.inventory);
    }

    private List<String> classSummary(String classId) {
        List<String> lore = new ArrayList<>();
        Unit preview = new Unit(new UUID(0L, 0L), new UUID(0L, 1L), classId, 1, Mode.PROTECT, false);
        lore.add("§7" + settings.getString("classes." + classId + ".description", "Lính " + classDisplay(classId)));
        lore.addAll(classEquipmentSummary(classId));
        String damageSummary = basicDamageSummary(preview);
        if (damageSummary != null) lore.add("§7" + damageSummary);
        lore.add("§7Skill:");
        for (String skill : settings.getStringList("classes." + classId + ".skills")) {
            lore.add("§f• " + skillDisplayName(skill) + ": " + skillDescription(preview, skill));
            boolean guardianBasicAttack = "guardian".equals(classId) && skill.equalsIgnoreCase("ATTACK");
            boolean guardianPassive = "guardian".equals(classId) && skill.equalsIgnoreCase("GUARDIAN_LINK");
            String status = guardianBasicAttack ? "Đòn cơ bản"
                : guardianPassive ? ""
                : "Hồi chiêu: §f" + formatStat(skillCooldownSeconds(preview, skill)) + " giây";
            if (!status.isEmpty()) lore.add("  §7" + status);
        }
        lore.add("§eNhấn để chọn Bậc.");
        return lore;
    }

    private void openTierMenu(Player player, UUID target, String itemType, String classId) {
        ServiceMenuHolder holder = createServiceMenu(player, MenuType.TIER_SELECT, target, itemType,
            classId, 0, 54, classDisplay(classId) + " • Chọn Bậc");
        List<Integer> tiers = getTierIds().stream().map(MercenaryManager::parsePositiveInt).toList();
        for (int index = 0; index < tiers.size() && index < 28; index++) {
            int tier = tiers.get(index);
            int slot = 10 + index / 7 * 9 + index % 7;
            holder.choices.put(slot, Integer.toString(tier));
            holder.inventory.setItem(slot, menuItem(Material.AMETHYST_SHARD,
                "§eBậc " + tier, tierSummary(classId, tier, itemType)));
        }
        holder.inventory.setItem(49, menuItem(Material.ARROW, "§7Quay lại", List.of("§7Chọn lớp")));
        player.openInventory(holder.inventory);
    }

    private List<String> tierSummary(String classId, int tier, String itemType) {
        List<String> lore = new ArrayList<>();
        Unit preview = new Unit(new UUID(0L, 0L), new UUID(0L, 1L), classId, tier, Mode.PROTECT, false);
        lore.add("§7Bậc triệu hồi: §f" + tier + " §8| §7Level ban đầu: §f1");
        if ("guardian".equals(classId)) {
            GuardianStats stats = guardianStats(preview);
            lore.add(guardianHealthBreakdown(stats));
            lore.add(guardianArmorBreakdown(stats));
        } else {
            double health = settings.getDouble("classes." + classId + ".base-health", 24.0)
                + Math.max(0, tier - 1) * settings.getDouble("classes." + classId + ".health-per-level", 4.0);
            double armor = settings.getDouble("classes." + classId + ".base-armor", 0.0)
                + Math.max(0, tier - 1) * settings.getDouble("classes." + classId + ".armor-per-level", 0.0);
            lore.add("§cHP: §f" + formatStat(health) + " §7| §bGiáp: §f" + formatStat(armor));
        }
        lore.addAll(classEquipmentSummary(classId));
        String damageSummary = basicDamageSummary(preview);
        if (damageSummary != null) lore.add("§7" + damageSummary);
        if (itemType.equals("hire")) {
            lore.add("§7Token: §f" + classDisplay(classId) + " Bậc " + tier);
            lore.add("§7XP người chơi: §f" + settings.getInt("tiers." + tier + ".experience-levels", 0) + " level");
            ConfigurationSection costs = settings.getConfigurationSection("tiers." + tier + ".materials");
            if (costs != null) for (String material : costs.getKeys(false)) {
                int amount = costs.getInt(material);
                if (amount > 0) lore.add("§7" + material + ": §f" + amount);
            }
        } else {
            lore.add("§7Loại item: §f" + itemType.toUpperCase(Locale.ROOT));
        }
        for (String skill : settings.getStringList("classes." + classId + ".skills")) {
            lore.add("§7" + skillDisplayName(skill) + ": "
                + skillDescription(preview, skill));
        }
        lore.add("§eNhấn để " + (itemType.equals("hire") ? "thuê" : "cấp") + ".");
        return lore;
    }

    private List<String> classEquipmentSummary(String classId) {
        List<String> summary = new ArrayList<>(List.of(
            "§7Vũ khí tay chính: §f" + configuredHandCategories(classId,
                "preferred-main-hand-items", "starting-weapon"),
            "§7Vũ khí/vật phẩm tay phụ: §f" + configuredHandCategories(classId,
                "preferred-offhand-items", "starting-offhand")));
        if ("guardian".equals(classId)) {
            summary.add("§7Bonus Vệ Binh: vũ khí tay chính §a+"
                + formatStat(readBoundedDouble(settings,
                    "classes.guardian.sword-damage-multiplier", 1.1, 1.0, 3.0) * 100.0 - 100.0)
                + " sát thương; khiên §b+"
                + formatStat(readBoundedDouble(settings,
                    "classes.guardian.shield-offhand-health-bonus", 8.0, 0.0, 100.0))
                + " HP và tăng lớp máu hấp thụ sát thương");
        }
        return summary;
    }

    private String configuredHandCategories(String classId, String preferenceKey, String startingKey) {
        List<String> configured = settings.getStringList("classes." + classId + "." + preferenceKey);
        if (configured.isEmpty()) {
            String starting = settings.getString("classes." + classId + "." + startingKey, "AIR");
            if (starting.equalsIgnoreCase("AIR")) return "Không trang bị";
            configured = List.of(starting);
        }
        List<String> categories = configured.stream()
            .map(MercenaryManager::itemCategory)
            .filter(category -> !category.equals("Không cầm"))
            .distinct()
            .toList();
        return categories.isEmpty() ? "Không trang bị" : String.join(", ", categories);
    }

    private static String itemCategory(String itemName) {
        if (itemName == null || itemName.isBlank() || itemName.equalsIgnoreCase("AIR")) return "Không cầm";
        String name = itemName.toUpperCase(Locale.ROOT);
        if (name.contains("SPEAR") || name.equals("TRIDENT")) return "Giáo";
        if (name.contains("SWORD")) return "Kiếm";
        if (name.contains("AXE")) return "Rìu";
        if (name.equals("BOW")) return "Cung";
        if (name.equals("CROSSBOW")) return "Nỏ";
        if (name.equals("SHIELD")) return "Khiên";
        if (name.equals("MACE")) return "Chùy";
        if (name.endsWith("_HOE")) return "Cuốc";
        if (name.equals("SNOWBALL")) return "Cầu tuyết";
        if (name.equals("FIRE_CHARGE")) return "Cầu lửa";
        if (name.contains("POTION")) return "Thuốc";
        if (name.equals("EMERALD")) return "Ngọc lục bảo";
        if (name.equals("STICK") || name.equals("BLAZE_ROD")) return "Trượng";
        return "Vật phẩm hỗ trợ";
    }

    private static String itemCategory(ItemStack item) {
        return item == null ? "Không cầm" : itemCategory(item.getType().name());
    }

    private String unitHandDescription(Unit unit, ItemStack item, String preferenceKey, String startingKey) {
        String actual = itemCategory(item);
        if (!actual.equals("Không cầm")) return actual;
        String preferred = configuredHandCategories(unit.unitClass, preferenceKey, startingKey);
        return preferred.equals("Không trang bị") ? actual : "Trống (ưu tiên " + preferred + " nếu có)";
    }

   private ItemStack displayedMainHand(MercenaryManager.Unit unit) {
      Mob mob = this.activeEntities.get(unit.id);
      if (mob != null && mob.isValid() && mob.getEquipment() != null) {
         return mob.getEquipment().getItemInMainHand();
      } else {
         ItemStack current = unit.equipment.get(EquipmentSlot.HAND);
         if (this.validSoldierGear(current, EquipmentSlot.HAND)) {
            return current;
         } else {
            return unit.gearCustomized ? null : this.configuredStartingWeapon(unit);
         }
      }
   }

   private ItemStack displayedOffhand(MercenaryManager.Unit unit) {
      Mob mob = this.activeEntities.get(unit.id);
      if (mob != null && mob.isValid() && mob.getEquipment() != null) {
         return mob.getEquipment().getItemInOffHand();
      } else {
         ItemStack current = unit.equipment.get(EquipmentSlot.OFF_HAND);
         if (this.validSoldierGear(current, EquipmentSlot.OFF_HAND)) {
            return current;
         } else {
            return unit.gearCustomized ? null : this.configuredStartingOffhand(unit);
         }
      }
   }

    private String basicDamageSummary(Unit unit) {
        if (!hasSkill(unit, "ATTACK")) return null;
        if ("guardian".equals(unit.unitClass)) return guardianBasicAttackDescription(unit);
        return "Sát thương đòn cận chiến: " + meleeDamageDescription(unit);
    }

   private String guardianBasicAttackDescription(MercenaryManager.Unit unit) {
      MercenaryManager.GuardianStats stats = this.guardianStats(unit);
      ItemStack weapon = this.displayedMainHand(unit);
      MercenaryManager.GearStats gear = this.soldierGearStats(unit);
      double adjustedDamage = stats.damage() * (1.0 + gear.damagePercent() / 100.0);
      double weaponBonus = this.itemDamageBonus(weapon, adjustedDamage);
      double handMultiplier = this.mainHandDamageMultiplier(unit);
      double normalDamage = (adjustedDamage + weaponBonus) * handMultiplier * (1.0 + this.skillGearDamagePercent(unit) / 100.0);
      double gearHealth = this.soldierGearStats(unit).health();
      double maxHealth = Math.min(1024.0, stats.maxHealth() + gearHealth);
      double thirdHitBonus = maxHealth * this.skillOptionDouble(unit, "ATTACK", "third-hit-max-health-damage", 0.05, 0.0, 1.0);
      return "Sát thương đòn thường: "
         + this.meleeDamageDescription(unit)
         + " §8| §7đòn thứ 3: §6"
         + formatStat(normalDamage + thirdHitBonus)
         + " §8(§f"
         + formatStat(normalDamage)
         + " §7+ §6"
         + formatStat(thirdHitBonus)
         + " ("
         + formatStat(this.skillOptionDouble(unit, "ATTACK", "third-hit-max-health-damage", 0.05, 0.0, 1.0) * 100.0)
         + "% max HP)§8)";
   }

   private String meleeDamageDescription(MercenaryManager.Unit unit) {
      MercenaryManager.GearStats gear = this.soldierGearStats(unit);
      double handMultiplier = this.mainHandDamageMultiplier(unit);
      double elementalPercent = this.skillGearDamagePercent(unit);
      if ("guardian".equals(unit.unitClass)) {
         MercenaryManager.GuardianStats stats = this.guardianStats(unit);
         double rawDamage = stats.damage();
         double gearBonus = rawDamage * gear.damagePercent() / 100.0;
         double weaponBonus = this.itemDamageBonus(this.displayedMainHand(unit), rawDamage + gearBonus);
         return this.meleeDamageBreakdown(stats.baseDamage(), weaponBonus, stats.tierDamage(), stats.levelDamage(), gearBonus, handMultiplier, elementalPercent);
      } else {
         double base = this.settings.getDouble("classes." + unit.unitClass + ".base-damage", 4.0);
         double damagePerLevel = this.settings.getDouble("classes." + unit.unitClass + ".damage-per-level", 0.5);
         double tierBonus = Math.max(0, unit.tier - 1) * damagePerLevel;
         double levelBonus = Math.max(0, unit.level - 1) * damagePerLevel;
         double rawDamage = base + tierBonus + levelBonus;
         double gearBonus = rawDamage * gear.damagePercent() / 100.0;
         double weaponBonus = this.itemDamageBonus(this.displayedMainHand(unit), rawDamage + gearBonus);
         return this.meleeDamageBreakdown(base, weaponBonus, tierBonus, levelBonus, gearBonus, handMultiplier, elementalPercent);
      }
   }

    private String frostDamageDescription(Unit unit, String skill, double fallback) {
        double base = frostOptionDouble(skill, "damage", fallback, 0.0, 100.0);
        double levelBonus = Math.max(0, unit.level - 1) * readBoundedDouble(settings,
            "classes.frost_mage.level-scaling.damage-per-level", 0.1, 0.0, 10.0);
        return skillDamageDescription(unit, base, levelBonus, 1.0, 100.0, true);
    }

    private String flameDamageDescription(Unit unit, String skill, String option, double fallback) {
        return skillDamageDescription(unit,
            flameOption(unit, skill, option, fallback, 0.0, 100.0),
            0.0, skillPowerMultiplier(unit), 100.0, true);
    }

   private String skillDamageDescription(MercenaryManager.Unit unit, double base, double levelBonus, double skillMultiplier, double cap, boolean applyHandBonus) {
      return this.damageBreakdown(
         base,
         0.0,
         0.0,
         levelBonus,
         skillMultiplier,
         applyHandBonus ? this.mainHandDamageMultiplier(unit) : 1.0,
         applyHandBonus ? this.skillGearDamagePercent(unit) : 0.0,
         cap
      );
   }

   private String damageBreakdown(
      double base, double weaponBonus, double tierBonus, double levelBonus, double skillMultiplier, double handMultiplier, double gearPercent, double cap
   ) {
      double skillBonus = Math.max(0.0, base + weaponBonus + tierBonus + levelBonus) * (skillMultiplier - 1.0);
      double uncapped = Math.max(0.0, base + weaponBonus + tierBonus + levelBonus + skillBonus);
      double capped = Math.min(cap, uncapped);
      double capScale = uncapped > 0.0 ? capped / uncapped : 0.0;
      double handBonus = capped * (handMultiplier - 1.0);
      double gearBonus = capped * handMultiplier * gearPercent / 100.0;
      double total = capped * handMultiplier + gearBonus;
      List<MercenaryManager.StatPart> parts = new ArrayList<>();
      parts.add(new MercenaryManager.StatPart(base * capScale, "§f"));
      parts.add(new MercenaryManager.StatPart(weaponBonus * capScale, "§b"));
      parts.add(new MercenaryManager.StatPart(tierBonus * capScale, "§d"));
      parts.add(new MercenaryManager.StatPart(levelBonus * capScale, "§c"));
      parts.add(new MercenaryManager.StatPart(skillBonus * capScale, "§6"));
      parts.add(new MercenaryManager.StatPart(handBonus, "§e"));
      parts.add(new MercenaryManager.StatPart(gearBonus, "§a"));
      return "§6" + formatStat(total) + this.formatStatPartSuffix(parts);
   }

   private double skillGearDamagePercent(MercenaryManager.Unit unit) {
      MercenaryManager.GearStats gear = this.soldierGearStats(unit);

      return switch (unit.unitClass) {
         case "pyromancer" -> gear.fireDamagePercent();
         case "frost_mage" -> gear.frostDamagePercent();
         case "druid" -> gear.natureDamagePercent();
         default -> 0.0;
      };
   }

   private String meleeDamageBreakdown(
      double base, double weaponBonus, double tierBonus, double levelBonus, double gearBonus, double handMultiplier, double elementalPercent
   ) {
      double beforeHand = base + weaponBonus + tierBonus + levelBonus + gearBonus;
      double handBonus = beforeHand * (handMultiplier - 1.0);
      double elementalBonus = beforeHand * handMultiplier * elementalPercent / 100.0;
      double total = beforeHand * handMultiplier + elementalBonus;
      List<MercenaryManager.StatPart> parts = List.of(
         new MercenaryManager.StatPart(base, "§f"),
         new MercenaryManager.StatPart(weaponBonus, "§b"),
         new MercenaryManager.StatPart(tierBonus, "§d"),
         new MercenaryManager.StatPart(levelBonus, "§c"),
         new MercenaryManager.StatPart(gearBonus, "§a"),
         new MercenaryManager.StatPart(handBonus, "§e"),
         new MercenaryManager.StatPart(elementalBonus, "§a")
      );
      return "§6" + formatStat(total) + this.formatStatPartSuffix(parts);
   }

   private String formatStatParts(List<MercenaryManager.StatPart> parts) {
      StringBuilder formatted = new StringBuilder();

      for (MercenaryManager.StatPart part : parts) {
         if (Double.isFinite(part.value()) && !(Math.abs(part.value()) < 1.0E-4)) {
            if (!formatted.isEmpty()) {
               formatted.append(" §7+ ");
            }

            formatted.append(part.color()).append(formatStat(part.value()));
         }
      }

      return formatted.toString();
   }

    private double mainHandDamageMultiplier(Unit unit) {
        ItemStack mainHand = displayedMainHand(unit);
        if (!isWeaponItem(mainHand)) return 1.0;
        if ("guardian".equals(unit.unitClass)) {
            return readBoundedDouble(settings,
                "classes.guardian.sword-damage-multiplier", 1.1, 1.0, 3.0);
        }
        return readBoundedDouble(settings, "combat.main-hand-damage-multiplier", 1.3, 1.0, 3.0);
    }

    private double itemDamageBonus(ItemStack item, double baseDamage) {
        if (item == null || item.getType().isAir()) return 0.0;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            var modifiers = meta.getAttributeModifiers(Attribute.ATTACK_DAMAGE);
            if (modifiers != null && !modifiers.isEmpty()) {
                boolean hasMainHandModifier = false;
                double addNumber = 0.0;
                double addScalar = 0.0;
                double multiplyScalar = 1.0;
                for (AttributeModifier modifier : modifiers) {
                    if (!modifier.getSlotGroup().test(org.bukkit.inventory.EquipmentSlot.HAND)) continue;
                    hasMainHandModifier = true;
                    switch (modifier.getOperation()) {
                        case ADD_NUMBER -> addNumber += modifier.getAmount();
                        case ADD_SCALAR -> addScalar += modifier.getAmount();
                        case MULTIPLY_SCALAR_1 -> multiplyScalar *= 1.0 + modifier.getAmount();
                    }
                }
                if (!hasMainHandModifier) return 0.0;
                return Math.max(0.0,
                    (baseDamage + addNumber) * (1.0 + addScalar) * multiplyScalar - baseDamage);
            }
        }
        return defaultItemDamageBonus(item.getType());
    }

    private static double defaultItemDamageBonus(Material material) {
        String name = material.name();
        if (name.endsWith("_SWORD")) {
            if (name.startsWith("WOODEN_") || name.startsWith("GOLDEN_")) return 3.0;
            if (name.startsWith("STONE_")) return 4.0;
            if (name.startsWith("IRON_")) return 5.0;
            if (name.startsWith("DIAMOND_")) return 6.0;
            if (name.startsWith("NETHERITE_")) return 7.0;
        }
        if (name.endsWith("_AXE")) {
            if (name.startsWith("WOODEN_") || name.startsWith("GOLDEN_")) return 6.0;
            if (name.startsWith("STONE_")) return 8.0;
            if (name.startsWith("IRON_") || name.startsWith("DIAMOND_")) return 9.0;
            if (name.startsWith("NETHERITE_")) return 10.0;
        }
        if (name.endsWith("_HOE")) {
            if (name.startsWith("STONE_")) return 1.0;
            if (name.startsWith("IRON_")) return 2.0;
            if (name.startsWith("DIAMOND_")) return 3.0;
            if (name.startsWith("NETHERITE_")) return 4.0;
        }
        if (material == Material.TRIDENT) return 8.0;
        if (material == Material.MACE) return 5.0;
        return 0.0;
    }

    private void openArmyMenu(Player player, int page) {
        List<Unit> owned = getOwnerUnits(player.getUniqueId());
        int pages = Math.max(1, (owned.size() + 27) / 28);
        int safePage = Math.max(0, Math.min(page, pages - 1));
        int start = safePage * 28;
        List<UUID> pageIds = owned.subList(Math.min(start, owned.size()),
            Math.min(start + 28, owned.size())).stream().map(unit -> unit.id).toList();
        ArmyMenuHolder holder = new ArmyMenuHolder(player.getUniqueId(), safePage, pageIds);
        Inventory inventory = Bukkit.createInventory(holder, 54, "Đội quân • " + (safePage + 1) + "/" + pages);
        holder.inventory = inventory;
        inventory.setItem(2, menuItem(Material.WOODEN_SWORD, "§aTriệu hồi đội hình",
            List.of("§7Gọi tối đa " + maxDeployedUnits + " lính.",
                "§7Ưu tiên bậc cao nhất, sau đó level cao nhất.")));
        inventory.setItem(4, menuItem(Material.EMERALD, "§aThuê lính", List.of("§7Chọn lớp và Bậc.")));
        inventory.setItem(6, menuItem(Material.RED_BED, "§7Nghỉ tất cả", List.of("§7Cho tất cả lính nghỉ ngơi.")));
        for (int index = 0; index < pageIds.size(); index++) {
            Unit unit = units.get(pageIds.get(index));
            if (unit == null) continue;
            Material icon = Material.matchMaterial(settings.getString(
                "classes." + unit.unitClass + ".menu-icon", "IRON_SWORD"));
            if (icon == null || icon.isAir()) icon = Material.IRON_SWORD;
            ItemStack entry = new ItemStack(icon);
            ItemMeta meta = entry.getItemMeta();
            meta.setDisplayName(unitLabel(unit, true) + " §7• #" + shortId(unit.id));
            meta.setLore(List.of("§7Bậc: §f" + unit.tier + " §8| §7Cấp: §f" + unit.level,
                "§7XP: §f" + unit.experience + "/" + nextLevelExperience(unit),
                "§7Chế độ: §f" + modeName(unit.mode),
                unitStatus(unit),
                "§eNhấn để quản lý"));
            entry.setItemMeta(meta);
            inventory.setItem(10 + (index / 7) * 9 + index % 7, entry);
        }
        inventory.setItem(49, menuItem(Material.ARROW, "§7Quay lại menu chính",
            List.of("§7Trở về menu chính của plugin.")));
        if (safePage > 0) inventory.setItem(45, menuItem(Material.ARROW, "§eTrang trước", List.of()));
        if (safePage + 1 < pages) inventory.setItem(53, menuItem(Material.ARROW, "§eTrang sau", List.of()));
        if (owned.isEmpty()) inventory.setItem(22, menuItem(Material.BARRIER, "§7Chưa có lính",
            List.of("§7Thuê bằng /vnarmy hire <loại> <bậc>.")));
        player.openInventory(inventory);
    }

    private void openUnitMenu(Player player, Unit unit) {
        UnitMenuHolder holder = new UnitMenuHolder(player.getUniqueId(), unit.id);
        Inventory inventory = Bukkit.createInventory(holder, 54, "Quản lý lính #" + shortId(unit.id));
        holder.inventory = inventory;
        inventory.setItem(4, menuItem(Material.NAME_TAG, unitLabel(unit, true), unitDetails(unit)));
        inventory.setItem(10, menuItem(Material.IRON_SWORD, "§cChế độ tấn công", List.of()));
        inventory.setItem(11, menuItem(Material.SHIELD, "§6Chế độ phòng thủ", List.of()));
        inventory.setItem(12, menuItem(Material.LEAD, "§aBảo vệ chủ nhân", List.of()));
        inventory.setItem(13, menuItem(unit.muted ? Material.MUSIC_DISC_13 : Material.NOTE_BLOCK,
            unit.muted ? "§7Bật âm thanh" : "§aTắt âm thanh", List.of()));
        inventory.setItem(15, menuItem(Material.BARRIER, "§cXóa lính", List.of("§7Xóa đơn vị này khỏi đội quân.")));
        inventory.setItem(14, menuItem(unit.deployed ? Material.RED_BED : Material.LIME_BED,
            unit.deployed ? "§cCho lính nghỉ" : "§aTriệu hồi lính", List.of()));
        inventory.setItem(16, menuItem(Material.CHEST, "§bTrang bị", List.of()));
        inventory.setItem(17, menuItem(canUpgrade(unit) ? Material.EMERALD : Material.GRAY_DYE,
            "§aNâng bậc", tierUpgradeLore(unit)));
        inventory.setItem(22, menuItem(Material.ARROW, "§7Quay lại đội quân", List.of()));
        player.openInventory(inventory);
    }

    private void openUnitDeleteConfirm(Player player, Unit unit) {
        ServiceMenuHolder holder = createServiceMenu(player, MenuType.UNIT_DELETE_CONFIRM,
            unit.owner, unit.id.toString(), null, 0, 27, "Xác nhận xóa lính");
        holder.inventory.setItem(4, menuItem(Material.BARRIER, "§cXóa " + unitLabel(unit, true) + "?",
            List.of("§7ID: §f#" + shortId(unit.id), "§cThao tác này không thể hoàn tác.")));
        holder.inventory.setItem(11, menuItem(Material.LIME_WOOL, "§aXÁC NHẬN XÓA",
            List.of("§7Xóa lính khỏi đội quân.")));
        holder.inventory.setItem(15, menuItem(Material.RED_WOOL, "§cHỦY",
            List.of("§7Giữ lại lính.")));
        holder.inventory.setItem(22, menuItem(Material.ARROW, "§7Quay lại lính", List.of()));
        player.openInventory(holder.inventory);
    }

   private List<String> unitDetails(MercenaryManager.Unit unit) {
      List<String> details = new ArrayList<>();
      Mob mob = this.activeEntities.get(unit.id);
      boolean guardianClass = "guardian".equals(unit.unitClass);
      MercenaryManager.GuardianStats guardianStats = guardianClass ? this.guardianStats(unit) : null;
      MercenaryManager.GearStats gear = this.soldierGearStats(unit);
      double calculatedHealth = (
            guardianClass
               ? guardianStats.maxHealth()
               : this.settings.getDouble("classes." + unit.unitClass + ".base-health", 24.0)
                  + Math.max(0, this.statRank(unit) - 1) * this.settings.getDouble("classes." + unit.unitClass + ".health-per-level", 4.0)
         )
         + gear.health();
      double maxHealth = mob != null && mob.isValid() ? maxHealth(mob) : Math.min(1024.0, calculatedHealth);
      double health = mob != null && mob.isValid() ? mob.getHealth() : maxHealth;
      double armor = mob != null && mob.getAttribute(Attribute.ARMOR) != null
         ? mob.getAttribute(Attribute.ARMOR).getValue()
         : Math.min(
            100.0,
            (
                  guardianClass
                     ? guardianStats.armor()
                     : this.settings.getDouble("classes." + unit.unitClass + ".base-armor", 0.0)
                        + Math.max(0, this.statRank(unit) - 1) * this.settings.getDouble("classes." + unit.unitClass + ".armor-per-level", 0.0)
               )
               + gear.armor()
         );
      details.add(
         "§7Bậc: §f"
            + unit.tier
            + " (gốc "
            + unit.originalTier
            + ") §8| §7Cấp: §f"
            + unit.level
            + " §8| §7XP: §f"
            + unit.experience
            + "/"
            + this.nextLevelExperience(unit)
      );
      details.add("§eNhấn vào bảng tên này để làm mới thông tin chỉ số.");
      details.add("§7Trạng thái: " + this.unitStatus(unit));
      details.add("§7Vũ khí tay chính: §f" + this.unitHandDescription(unit, this.displayedMainHand(unit), "preferred-main-hand-items", "starting-weapon"));
      details.add("§7Vũ khí/vật phẩm tay phụ: §f" + this.unitHandDescription(unit, this.displayedOffhand(unit), "preferred-offhand-items", "starting-offhand"));
      List<MercenaryManager.StatPart> healthParts = this.fitStatParts(this.unitHealthParts(unit, guardianStats, gear), maxHealth);
      List<MercenaryManager.StatPart> armorParts = this.fitStatParts(this.unitArmorParts(unit, guardianStats, gear), armor);
      details.add("§cHP: §f" + formatStat(health) + "/§6" + formatStat(maxHealth) + this.formatStatPartSuffix(healthParts));
      if (armor > 1.0E-4) {
         details.add(this.formatStatBreakdown("Giáp", armor, armorParts));
      }

      String gearSummary = this.gearDetails(gear);
      if (gearSummary != null) {
         details.add(gearSummary);
      }

      String damageSummary = this.basicDamageSummary(unit);
      if (damageSummary != null) {
         details.add("§7" + damageSummary);
      }

      if ("frost_mage".equals(unit.unitClass)) {
         details.add(
            "§7Đòn cơ bản (cầu tuyết): "
               + this.frostDamageDescription(unit, "BASIC_ATTACK", 3.0)
               + ", tầm "
               + formatStat(this.frostRange(unit, "BASIC_ATTACK", "range-blocks", 5.0))
               + " block §8| §7Hồi chiêu: §f"
               + formatStat(this.frostCooldown(unit, "BASIC_ATTACK", 2.0))
               + " giây"
         );
      }

      details.add("§8Skill:");

      for (String skill : this.settings.getStringList("classes." + unit.unitClass + ".skills")) {
         String normalized = skill.toUpperCase(Locale.ROOT);
         if (guardianClass && normalized.equals("ATTACK")) {
            details.add("§7" + skillDisplayName(normalized) + ": §f" + this.skillDescription(unit, normalized) + " §8| §7Đòn cơ bản");
         } else if (guardianClass && normalized.equals("GUARDIAN_LINK")) {
            details.add("§7" + skillDisplayName(normalized) + ": §f" + this.skillDescription(unit, normalized));
         } else {
            String cooldownKey = skillCooldownKey(normalized);
            long remaining = Math.max(0L, this.skillCooldowns.getOrDefault(unit.id, Map.of()).getOrDefault(cooldownKey, 0L) - System.currentTimeMillis());
            double cooldownSeconds = normalized.equals("ILLUMINATION")
               ? this.illuminationCooldownSeconds(unit)
               : (
                  FROST_MAGE_SKILLS.contains(normalized)
                     ? this.frostCooldown(unit, normalized, 1.0)
                     : (FIRE_MAGE_SKILLS.contains(normalized) ? this.flameCooldown(unit, normalized, 1.0) : this.skillCooldownSeconds(unit, normalized))
               );
            String cooldown = formatStat(cooldownSeconds) + " giây";
            if (remaining > 0L) {
               cooldown = cooldown + " (còn " + formatStat(remaining / 1000.0) + " giây)";
            }

            details.add("§7" + skillDisplayName(normalized) + ": §f" + this.skillDescription(unit, normalized) + " §8| §7Hồi chiêu: §f" + cooldown);
         }
      }

      details.add("§8Nguồn: §fNền §8/ §bVũ khí §8/ §dBậc §8/ §cLevel §8/ §eTay chính §8/ §3Tay phụ §8/ §6Kỹ năng §8/ §aTrang bị");
      return details;
   }

   private String guardianHealthBreakdown(MercenaryManager.GuardianStats stats) {
      return this.formatStatBreakdown(
         "HP",
         stats.maxHealth(),
         this.fitStatParts(
            List.of(
               new MercenaryManager.StatPart(stats.baseHealth(), "§f"),
               new MercenaryManager.StatPart(stats.tierHealth(), "§d"),
               new MercenaryManager.StatPart(stats.levelHealth(), "§c"),
               new MercenaryManager.StatPart(stats.equipmentHealth(), "§3")
            ),
            stats.maxHealth()
         )
      );
   }

   private String guardianArmorBreakdown(MercenaryManager.GuardianStats stats) {
      return this.formatStatBreakdown(
         "Giáp",
         stats.armor(),
         this.fitStatParts(
            List.of(
               new MercenaryManager.StatPart(stats.baseArmor(), "§f"),
               new MercenaryManager.StatPart(stats.tierArmor(), "§d"),
               new MercenaryManager.StatPart(stats.levelArmor(), "§c")
            ),
            stats.armor()
         )
      );
   }

   private List<MercenaryManager.StatPart> unitHealthParts(MercenaryManager.Unit unit, MercenaryManager.GuardianStats guardian, MercenaryManager.GearStats gear) {
      if (guardian != null) {
         return List.of(
            new MercenaryManager.StatPart(guardian.baseHealth(), "§f"),
            new MercenaryManager.StatPart(guardian.tierHealth(), "§d"),
            new MercenaryManager.StatPart(guardian.levelHealth(), "§c"),
            new MercenaryManager.StatPart(guardian.equipmentHealth(), "§3"),
            new MercenaryManager.StatPart(gear.health(), "§a")
         );
      }

      double perLevel = this.settings.getDouble("classes." + unit.unitClass + ".health-per-level", 4.0);
      return List.of(
         new MercenaryManager.StatPart(this.settings.getDouble("classes." + unit.unitClass + ".base-health", 24.0), "§f"),
         new MercenaryManager.StatPart(Math.max(0, unit.tier - 1) * perLevel, "§d"),
         new MercenaryManager.StatPart(Math.max(0, unit.level - 1) * perLevel, "§c"),
         new MercenaryManager.StatPart(gear.health(), "§a")
      );
   }

   private List<MercenaryManager.StatPart> unitArmorParts(MercenaryManager.Unit unit, MercenaryManager.GuardianStats guardian, MercenaryManager.GearStats gear) {
      if (guardian != null) {
         return List.of(
            new MercenaryManager.StatPart(guardian.baseArmor(), "§f"),
            new MercenaryManager.StatPart(guardian.tierArmor(), "§d"),
            new MercenaryManager.StatPart(guardian.levelArmor(), "§c"),
            new MercenaryManager.StatPart(gear.armor(), "§a")
         );
      }

      double perLevel = this.settings.getDouble("classes." + unit.unitClass + ".armor-per-level", 0.0);
      return List.of(
         new MercenaryManager.StatPart(this.settings.getDouble("classes." + unit.unitClass + ".base-armor", 0.0), "§f"),
         new MercenaryManager.StatPart(Math.max(0, unit.tier - 1) * perLevel, "§d"),
         new MercenaryManager.StatPart(Math.max(0, unit.level - 1) * perLevel, "§c"),
         new MercenaryManager.StatPart(gear.armor(), "§a")
      );
   }

   private String formatStatBreakdown(String label, double total, List<MercenaryManager.StatPart> parts) {
      return "§7" + label + ": §6" + formatStat(total) + this.formatStatPartSuffix(parts);
   }

   private List<MercenaryManager.StatPart> fitStatParts(List<MercenaryManager.StatPart> parts, double total) {
      double sum = parts.stream().mapToDouble(part -> Math.max(0.0, part.value())).sum();
      if (!(sum <= total) && !(sum <= 0.0)) {
         double factor = total / sum;
         return parts.stream().map(part -> new MercenaryManager.StatPart(Math.max(0.0, part.value()) * factor, part.color())).toList();
      } else {
         return parts;
      }
   }

   private String formatStatPartSuffix(List<MercenaryManager.StatPart> parts) {
      String formatted = this.formatStatParts(parts);
      return formatted.isEmpty() ? "" : " §8(" + formatted + "§8)";
   }

   private String gearDetails(MercenaryManager.GearStats gear) {
      List<String> bonuses = new ArrayList<>();
      this.addGearDetail(bonuses, "Giảm hồi chiêu", gear.cooldownReductionPercent(), "%");
      this.addGearDetail(bonuses, "Tầm đánh", gear.attackRangeBlocks(), " block");
      this.addGearDetail(bonuses, "Sát thương tay", gear.damagePercent(), "%");
      this.addGearDetail(bonuses, "Sát thương hỏa", gear.fireDamagePercent(), "%");
      this.addGearDetail(bonuses, "Sát thương băng", gear.frostDamagePercent(), "%");
      this.addGearDetail(bonuses, "Sát thương tự nhiên", gear.natureDamagePercent(), "%");
      this.addGearDetail(bonuses, "Tỉ lệ hiệu ứng", gear.effectChancePercent(), "%");
      this.addGearDetail(bonuses, "Thời lượng hiệu ứng", gear.effectDurationSeconds(), " giây");
      this.addGearDetail(bonuses, "Minion", gear.minions(), "");
      this.addGearDetail(bonuses, "Hồi máu", gear.healingPercent(), "%");
      return bonuses.isEmpty() ? null : "§aTrang bị: §7" + String.join(" §8| §7", bonuses);
   }

   private void addGearDetail(List<String> bonuses, String label, double value, String suffix) {
      if (value > 1.0E-4) {
         bonuses.add(label + " §a+" + formatStat(value) + suffix);
      }
   }

   private String skillDescription(MercenaryManager.Unit unit, String skill) {
      double power = this.skillPowerMultiplier(unit);

      return switch (skill.toUpperCase(Locale.ROOT)) {
         case "ATTACK" -> "guardian".equals(unit.unitClass)
            ? this.guardianBasicAttackDescription(unit) + "; làm chậm mục tiêu trong góc phía trước"
            : "Sát thương mỗi đòn cận chiến: " + this.meleeDamageDescription(unit);
         case "RANGED_ATTACK" -> "Sát thương mỗi tên: "
            + this.skillDamageDescription(unit, this.skillOptionDouble(unit, skill, "damage", 4.0, 0.0, 100.0), 0.0, power, 100.0, true)
            + ", tầm "
            + formatStat(this.skillOptionDouble(unit, skill, "range-blocks", 16.0, 4.0, 64.0))
            + " block";
         case "HEAL_OWNER" -> "Hồi "
            + formatStat(this.skillOptionDouble(unit, skill, "amount", this.healAmount, 0.0, 100.0) * power * this.soldierHealingMultiplier(unit))
            + " HP cho chủ nhân";
         case "HEAL_ALLIES" -> "Hồi "
            + formatStat(this.skillOptionDouble(unit, skill, "amount", this.healAmount, 0.0, 100.0) * power * this.soldierHealingMultiplier(unit))
            + " HP cho "
            + this.skillTargetDescription(unit, skill)
            + ", bán kính "
            + formatStat(this.skillOptionDouble(unit, skill, "radius-blocks", this.healRadius, 1.0, 32.0))
            + " block";
         case "SHIELD_OWNER" -> "Giảm "
            + formatStat(Math.min(0.9, this.skillOptionDouble(unit, skill, "damage-reduction", this.shieldReduction, 0.0, 0.9) * power) * 100.0)
            + "% sát thương lên chủ nhân, bán kính "
            + formatStat(this.skillOptionDouble(unit, skill, "radius-blocks", 8.0, 1.0, 32.0))
            + " block";
         case "BUFF_OWNER" -> "Tăng "
            + localizedEffectName(this.skillOptionString(unit, skill, "effect", this.ownerBuff))
            + " cho chủ nhân trong "
            + this.scaledDuration(unit, skill, this.ownerBuffTicks / 20.0)
            + " giây";
         case "BUFF_ALLIES" -> "Tăng "
            + localizedEffectName(this.skillOptionString(unit, skill, "effect", this.ownerBuff))
            + " cho "
            + this.skillTargetDescription(unit, skill)
            + " trong "
            + this.scaledDuration(unit, skill, this.ownerBuffTicks / 20.0)
            + " giây, bán kính "
            + formatStat(this.skillOptionDouble(unit, skill, "radius-blocks", this.healRadius, 1.0, 32.0))
            + " block";
         case "VOLLEY" -> "Bắn "
            + (int)this.skillOptionDouble(unit, skill, "arrow-count", 3.0, 2.0, 5.0)
            + " mũi tên, sát thương mỗi mũi: "
            + this.skillDamageDescription(unit, this.skillOptionDouble(unit, skill, "damage", 3.0, 0.0, 100.0), 0.0, power, 100.0, true)
            + ("ranger".equals(unit.unitClass) ? ", tầm " + formatStat(this.skillOptionDouble(unit, skill, "range-blocks", 20.8, 4.0, 64.0)) + " block" : "");
         case "POWER_STRIKE" -> "Sát thương đòn cường kích: "
            + this.skillDamageDescription(unit, this.skillOptionDouble(unit, skill, "damage", 8.0, 0.0, 100.0), 0.0, power, 100.0, true);
         case "TAUNT" -> "Khiêu khích quái trong bán kính " + formatStat(this.skillOptionDouble(unit, skill, "radius-blocks", 8.0, 1.0, 32.0)) + " block";
         case "GUARDIAN_LINK" -> "§7(Nội tại) Nhận §c"
            + formatStat(this.skillOptionDouble(unit, skill, "redirected-damage-fraction", 0.4, 0.0, 0.9) * 100.0)
            + "%§f sát thương thay cho chủ nhân trong "
            + formatStat(this.skillOptionDouble(unit, skill, "radius-blocks", 5.0, 1.0, 16.0))
            + " block";
         case "GUARDIAN_CHARGE" -> "Tự lao "
            + formatStat(this.skillOptionDouble(unit, skill, "minimum-range-blocks", 4.0, 1.0, 16.0))
            + "-"
            + formatStat(this.guardianChargeMaximumRange(unit))
            + " block (tăng 1 block mỗi 5 Level), hất lùi kẻ địch trong bán kính "
            + formatStat(this.skillOptionDouble(unit, skill, "sweep-radius-blocks", 2.0, 1.0, 6.0))
            + " block";
         case "GUARDIAN_SHIELD" -> this.guardianShieldDescription(unit);
         case "GUARDIAN_QUAKE" -> "Tạo vòng đá giả vây mục tiêu trong "
            + formatStat(this.skillOptionDouble(unit, skill, "duration-seconds", 5.0, 1.0, 15.0))
            + " giây và làm chậm mục tiêu (bán kính "
            + formatStat(this.skillOptionDouble(unit, skill, "radius-blocks", 3.0, 1.0, 8.0))
            + " block)";
         case "GUARDIAN_ULTIMATE" -> "Tự kích hoạt khi chủ dưới "
            + formatStat(this.skillOptionDouble(unit, skill, "owner-health-threshold-percent", 50.0, 1.0, 99.0))
            + "% HP: dịch chuyển đến chủ, hất lùi/khiêu khích quái, miễn sát thương "
            + formatStat(this.skillOptionDouble(unit, skill, "protection-duration-seconds", 3.0, 1.0, 10.0))
            + " giây";
         case "CLEANSE_OWNER" -> "Gỡ hiệu ứng "
            + (String)this.settings
               .getStringList("classes." + unit.unitClass + ".skill-settings.CLEANSE_OWNER.effects")
               .stream()
               .map(MercenaryManager::localizedEffectName)
               .reduce((left, right) -> left + ", " + right)
               .orElse("bất lợi")
            + " khỏi chủ nhân";
         case "FIRE_BOLT" -> this.skillDamageDescription(unit, this.skillOptionDouble(unit, skill, "damage", 6.0, 0.0, 100.0), 0.0, power, 100.0, true)
            + " và đốt mục tiêu trong "
            + (int)this.skillOptionDouble(unit, skill, "fire-seconds", 3.0, 0.0, 15.0)
            + " giây";
         case "FLAME_BOLT" -> "Phóng hỏa cầu gây " + this.flameDamageDescription(unit, skill, "damage", 6.0) + "; giảm 20% tốc độ trong 3 giây";
         case "IGNITE" -> "Thiêu đốt gây "
            + this.flameDamageDescription(unit, skill, "damage-per-pulse", 2.5)
            + " mỗi 2 giây trong "
            + (int)this.flameOption(unit, skill, "duration-seconds", 8.0, 2.0, 30.0)
            + " giây, lây tối đa "
            + (int)this.flameOption(unit, skill, "max-spread-targets", 6.0, 1.0, 24.0)
            + " mục tiêu";
         case "FLAME_NOVA" -> "Xoáy lửa gây "
            + this.skillDamageDescription(
               unit,
               this.flameOption(unit, skill, "damage-per-second", 1.0, 0.0, 100.0),
               Math.max(0, unit.level - 1) * this.flameOption(unit, skill, "damage-increase-per-level", 0.1, 0.0, 10.0),
               power,
               100.0,
               true
            )
            + "/giây với "
            + (int)Math.min(
               64.0,
               Math.floor(
                  this.flameOption(unit, skill, "particle-count", 10.0, 1.0, 64.0)
                     + Math.max(0, unit.level - 1) * this.flameOption(unit, skill, "particles-increase-per-level", 1.0, 0.0, 10.0)
               )
            )
            + " hạt lửa trong "
            + formatStat(this.scaledSkillDurationSeconds(unit, skill, 3.0, 0.1, 30.0))
            + " giây, bán kính "
            + formatStat(
               Math.min(
                  16.0,
                  this.flameOption(unit, skill, "radius-blocks", 3.0, 1.0, 12.0)
                     + Math.floor(unit.level / this.flameOption(unit, skill, "radius-increase-every-levels", 5.0, 1.0, 30.0))
               )
            )
            + " block";
         case "FIRE_WALL" -> "Lồng 3×3, cao 2 block, tồn tại " + formatStat(this.scaledSkillDurationSeconds(unit, skill, 3.0, 0.1, 30.0)) + " giây";
         case "METEOR_STRIKE" -> "Gọi "
            + Math.min(
               32,
               (int)this.flameOption(unit, skill, "fireball-count", 3.0, 1.0, 32.0)
                  + (int)(unit.level / this.flameOption(unit, skill, "fireballs-increase-every-levels", 3.0, 1.0, 30.0))
            )
            + " fireball; mỗi quả gây "
            + this.flameDamageDescription(unit, skill, "damage", 12.0)
            + ", tối đa "
            + (int)this.flameOption(unit, skill, "max-targets-per-fireball", 8.0, 1.0, 64.0)
            + " mục tiêu trong bán kính "
            + formatStat(
               Math.min(
                  16.0,
                  this.flameOption(unit, skill, "area-radius-blocks", 3.0, 1.0, 12.0)
                     + Math.floor(unit.level / this.flameOption(unit, skill, "area-radius-increase-every-levels", 5.0, 1.0, 30.0))
               )
            )
            + " block sau "
            + formatStat(this.flameOption(unit, skill, "warning-delay-seconds", 2.0, 0.1, 30.0))
            + " giây cảnh báo";
         case "PHOENIX_REBIRTH" -> "Hồi "
            + formatStat(this.flameOption(unit, skill, "heal-percent", 30.0, 1.0, 100.0))
            + "% máu, miễn sát thương 2 giây rồi gây "
            + this.flameDamageDescription(unit, skill, "damage", 18.0)
            + " AoE trong bán kính "
            + formatStat(this.flameOption(unit, skill, "radius-blocks", 8.0, 1.0, 16.0))
            + " block";
         case "ROOT_TARGET" -> "Gây "
            + localizedEffectName(this.skillOptionString(unit, skill, "effect", "SLOWNESS"))
            + " lên mục tiêu trong "
            + this.scaledDuration(unit, skill, 5.0)
            + " giây";
         case "LIFESTEAL" -> this.skillDamageDescription(unit, this.skillOptionDouble(unit, skill, "damage", 8.0, 0.0, 100.0), 0.0, power, 100.0, true)
            + " và hồi "
            + formatStat(this.skillOptionDouble(unit, skill, "heal", 4.0, 0.0, 100.0) * power)
            + " HP";
         case "BERSERK" -> "Kích hoạt dưới "
            + formatStat(this.skillOptionDouble(unit, skill, "health-percent", 50.0, 1.0, 99.0))
            + "% HP, tăng sức mạnh trong "
            + this.scaledDuration(unit, skill, 8.0)
            + " giây";
         case "SUMMON_VEXES" -> "Triệu hồi tối đa "
            + this.maxSummonedMinions(unit)
            + " Vex, mỗi lần gọi thêm một con; mỗi Vex: "
            + this.skillDamageDescription(unit, this.skillOptionDouble(unit, skill, "damage", 4.0, 0.0, 100.0), 0.0, power, 100.0, false);
         case "LANCE_CHARGE" -> "Khi cưỡi ngựa: "
            + this.skillDamageDescription(unit, this.skillOptionDouble(unit, skill, "damage", 12.0, 0.0, 100.0), 0.0, power, 100.0, true)
            + " trong bán kính "
            + formatStat(this.skillOptionDouble(unit, skill, "radius-blocks", 4.0, 1.0, 12.0))
            + " block";
         case "ILLUMINATION" -> "Bắn "
            + Math.min(
               20,
               (int)this.skillOptionDouble(unit, skill, "base-arrow-count", 1.0, 1.0, 20.0)
                  + unit.level / (int)this.skillOptionDouble(unit, skill, "levels-per-extra-arrow", 5.0, 1.0, 30.0)
            )
            + " tên lửa rơi quanh mục tiêu, cắm đuốc "
            + (int)this.skillOptionDouble(unit, skill, "torch-duration-seconds", 30.0, 1.0, 600.0)
            + " giây, tầm "
            + formatStat(this.skillOptionDouble(unit, skill, "range-blocks", 20.8, 4.0, 64.0))
            + " block";
         case "ICE_SPIKE" -> "Phóng gai băng gây "
            + this.frostDamageDescription(unit, skill, 3.0)
            + ", tầm "
            + formatStat(this.frostRange(unit, skill, "range-blocks", 5.0))
            + " block";
         case "BLIZZARD" -> "Bão tuyết gây "
            + this.frostDamageDescription(unit, skill, 3.0)
            + " mỗi nhịp trong bán kính "
            + formatStat(this.skillOptionDouble(unit, skill, "radius-blocks", 4.0, 1.0, 12.0))
            + " block, kéo dài "
            + this.frostDurationTicks(unit, skill, 3.0, 1.0, 12.0) / 20
            + " giây";
         case "FROST_NOVA" -> "Làm chậm mục tiêu quanh lính trong "
            + this.frostDurationTicks(unit, skill, 3.0, 1.0, 10.0) / 20
            + " giây, bán kính "
            + formatStat(this.skillOptionDouble(unit, skill, "radius-blocks", 4.0, 1.0, 12.0))
            + " block";
         case "ICE_WALL" -> "Lồng băng gây "
            + this.skillDamageDescription(
               unit,
               this.skillOptionDouble(unit, skill, "damage-per-second", 1.0, 0.0, 100.0),
               Math.max(0, unit.level - 1) * this.skillOptionDouble(unit, skill, "damage-increase-per-level", 0.1, 0.0, 10.0),
               power,
               100.0,
               true
            )
            + "/giây; sau khi thoát giảm tốc "
            + formatStat(this.skillOptionDouble(unit, skill, "slowness-percent", 50.0, 0.0, 80.0))
            + "% trong "
            + formatStat(
               Math.min(
                  30.0,
                  this.skillOptionDouble(unit, skill, "slowness-duration-seconds", 3.0, 0.1, 30.0)
                     + Math.max(0, unit.level - 1) * this.skillOptionDouble(unit, skill, "slowness-duration-increase-per-level-seconds", 0.1, 0.0, 10.0)
               )
            )
            + " giây";
         case "FROST_SHIELD" -> "Nhận Absorption và làm chậm kẻ tấn công trong " + this.frostDurationTicks(unit, skill, 6.0, 1.0, 30.0) / 20 + " giây";
         case "FROST_STEP" -> "Lướt áp sát mục tiêu, tầm " + formatStat(this.frostRange(unit, skill, "range-blocks", 5.0)) + " block";
         case "FROST_WALKER" -> "Tạo băng tạm trên mặt nước trong " + this.frostDurationTicks(unit, skill, 8.0, 1.0, 30.0) / 20 + " giây";
         case "ICE_AGE" -> "Đóng băng tối đa "
            + (int)this.skillOptionDouble(unit, skill, "max-targets", 24.0, 1.0, 64.0)
            + " mục tiêu trong bán kính "
            + formatStat(this.frostRange(unit, skill, "radius-blocks", 15.0))
            + " block";
         default -> "Chiến đấu với mục tiêu";
      };
   }

    private String guardianShieldDescription(Unit unit) {
        GuardianStats stats = guardianStats(unit);
        double base = skillOptionDouble(unit, "GUARDIAN_SHIELD", "absorption-base", 6.0, 0.0, 40.0);
        double level = Math.max(0, unit.level - 1)
            * skillOptionDouble(unit, "GUARDIAN_SHIELD", "absorption-per-level", 0.5, 0.0, 2.0);
        double armor = stats.armor()
            * skillOptionDouble(unit, "GUARDIAN_SHIELD", "absorption-per-armor", 0.5, 0.0, 2.0);
        double offhand = isGuardianHoldingOffhandShield(unit)
            ? skillOptionDouble(unit, "GUARDIAN_SHIELD", "shield-absorption-bonus", 4.0, 0.0, 20.0)
            : 0.0;
        double raw = Math.min(40.0, base + level + armor + offhand);
        double actualGuardian = absorptionEffectAmplifier(raw) < 0
            ? 0.0 : (absorptionEffectAmplifier(raw) + 1) * 4.0;
        double allyRatio = skillOptionDouble(unit, "GUARDIAN_SHIELD",
            "ally-absorption-ratio", 0.5, 0.0, 1.0);
        double rawAlly = raw * allyRatio;
        int allyAmplifier = absorptionEffectAmplifier(rawAlly);
        double actualAlly = allyAmplifier < 0 ? 0.0 : (allyAmplifier + 1) * 4.0;
        int resistanceAmplifier = (int) skillOptionDouble(unit, "GUARDIAN_SHIELD",
            "resistance-amplifier", 0.0, 0.0, 4.0);
        double resistancePercent = (resistanceAmplifier + 1) * 20.0;
        return "Lớp máu hấp thụ sát thương của Vệ Binh Tổng §6" + formatStat(actualGuardian)
            + " HP §8(§f"
            + formatStat(base) + " nền §7+ §c" + formatStat(level) + " Level §7+ §b"
            + formatStat(armor) + " từ giáp §7+ §e" + formatStat(offhand) + " khiên§8); chủ/đồng đội Tổng §6"
            + formatStat(actualAlly) + " HP lớp máu hấp thụ, kháng "
            + formatStat(resistancePercent) + "% sát thương, bán kính "
            + formatStat(skillOptionDouble(unit, "GUARDIAN_SHIELD", "radius-blocks", 10.0, 1.0, 32.0))
            + " block";
    }

    private double scaledDuration(Unit unit, String skill, double fallback) {
        return Math.min(600.0, Math.round(skillOptionDouble(unit, skill, "duration-seconds",
            fallback, 1.0, 600.0) * skillPowerMultiplier(unit)));
    }

    private String skillTargetDescription(Unit unit, String skill) {
        return switch (skillOptionString(unit, skill, "target", "OWNER_AND_ALLIES")) {
            case "OWNER" -> "chủ nhân";
            case "ALLIES" -> "đồng minh";
            default -> "chủ nhân và đồng minh";
        };
    }

    private static String skillDisplayName(String skill) {
        return switch (skill.toUpperCase(Locale.ROOT)) {
            case "ATTACK" -> "Đánh cận chiến";
            case "RANGED_ATTACK" -> "Tấn công tầm xa";
            case "HEAL_OWNER" -> "Hồi máu chủ nhân";
            case "HEAL_ALLIES" -> "Hồi máu đồng đội";
            case "BUFF_OWNER" -> "Cường hóa chủ nhân";
            case "BUFF_ALLIES" -> "Cường hóa đồng đội";
            case "SHIELD_OWNER" -> "Khiên bảo hộ";
            case "VOLLEY" -> "Bắn loạt tên";
            case "POWER_STRIKE" -> "Cường kích";
            case "TAUNT" -> "Khiêu khích";
            case "GUARDIAN_LINK" -> "Hộ Vệ";
            case "GUARDIAN_CHARGE" -> "Xung Phong";
            case "GUARDIAN_SHIELD" -> "Thánh Thể";
            case "GUARDIAN_QUAKE" -> "Vây Hãm";
            case "GUARDIAN_ULTIMATE" -> "Hộ Giá";
            case "CLEANSE_OWNER" -> "Thanh tẩy chủ nhân";
            case "FIRE_BOLT" -> "Hỏa cầu";
            case "ROOT_TARGET" -> "Trói chân mục tiêu";
            case "LIFESTEAL" -> "Hút máu";
            case "BERSERK" -> "Cuồng nộ";
            case "SUMMON_VEXES" -> "Triệu hồi Vex";
            case "LANCE_CHARGE" -> "Lao giáo";
            case "ILLUMINATION" -> "Soi Sáng";
            case "FLAME_BOLT" -> "Hỏa Cầu";
            case "IGNITE" -> "Thiêu Đốt";
            case "FLAME_NOVA" -> "Bão Lửa";
            case "FIRE_WALL" -> "Hỏa Ngục";
            case "METEOR_STRIKE" -> "Mưa Lửa";
            case "PHOENIX_REBIRTH" -> "Phượng Hoàng Tái Sinh";
            case "ICE_SPIKE" -> "Gai Băng";
            case "BLIZZARD" -> "Bão Tuyết";
            case "FROST_NOVA" -> "Băng Kích";
            case "ICE_WALL" -> "Băng Ngục";
            case "FROST_SHIELD" -> "Khiên Băng";
            case "FROST_STEP" -> "Băng Bộ";
            case "FROST_WALKER" -> "Băng Hành";
            case "ICE_AGE" -> "Kỷ Băng Hà";
            default -> "Kỹ năng chiến đấu";
        };
    }

    private static String localizedEffectName(String effect) {
        return switch (effect.toUpperCase(Locale.ROOT)) {
            case "SPEED" -> "Tốc độ";
            case "SLOWNESS" -> "Chậm chạp";
            case "REGENERATION" -> "Hồi phục";
            case "RESISTANCE" -> "Kháng cự";
            case "STRENGTH" -> "Sức mạnh";
            case "POISON" -> "Độc";
            case "WITHER" -> "Khô héo";
            case "WEAKNESS" -> "Suy yếu";
            case "BLINDNESS" -> "Mù";
            case "HUNGER" -> "Đói";
            case "FIRE_RESISTANCE" -> "Kháng lửa";
            case "NIGHT_VISION" -> "Nhìn trong đêm";
            case "INVISIBILITY" -> "Tàng hình";
            default -> "Hiệu ứng hỗ trợ";
        };
    }

    private static String skillCooldownKey(String skill) {
        return skill.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String formatStat(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private ItemStack menuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

   @EventHandler
   public void onServiceMenuClick(InventoryClickEvent event) {
      if (event.getInventory().getHolder() instanceof MercenaryManager.ServiceMenuHolder holder) {
         event.setCancelled(true);
         if (event.getWhoClicked() instanceof Player player
            && holder.viewer.equals(player.getUniqueId())
            && event.getClickedInventory() == event.getView().getTopInventory()) {
            int slot = event.getRawSlot();
            String choice = holder.choices.get(slot);
            switch (holder.type) {
               case MAIN:
                        if (slot == MAIN_MENU_STATS_SLOT) {
                     player.performCommand("vnstats");
                        } else if (slot == MAIN_MENU_STATUS_SLOT) {
                     player.performCommand("vnhardcore");
                        } else if (slot == MAIN_MENU_ARMY_SLOT && this.enabled) {
                     this.openArmyMenu(player, 0);
                        } else if (slot == MAIN_MENU_REWARD_SLOT) {
                     player.performCommand("reward");
                        } else if (slot == MAIN_MENU_RPG_SLOT) {
                            this.openRpgHubMenu(player);
                        } else if (slot == MAIN_MENU_WALLET_SLOT && plugin.getEconomyManager() != null) {
                            plugin.getEconomyManager().openWalletMenu(player);
                        } else if (slot == MAIN_MENU_ITEM_NAMES_SLOT && plugin.getEconomyManager() != null) {
                            plugin.getEconomyManager().openItemNameMenu(player);
                        } else if (slot == MAIN_MENU_HELP_SLOT) {
                     player.performCommand("vnhelp");
                        } else if (slot == MAIN_MENU_ADMIN_SLOT && this.hasAdminMenuAccess(player)) {
                     this.openAdminMenu(player);
                  }
                  break;
                    case RPG_HUB:
                        if (slot == 20 && plugin.getCharacterSelectionManager() != null
                                && plugin.getConfig().getBoolean("rpg.character-creation.enabled", true)) {
                            plugin.getCharacterSelectionManager().openCharacterMenu(player);
                        } else if (slot == 22 && plugin.getSkillManager() != null
                                && player.hasPermission("vnmine.command.skills")) {
                            plugin.getSkillManager().openSkillTreeFromMainMenu(player);
                        } else if (slot == 24 && plugin.getConfig().getBoolean("rpg.world-map.enabled", true)) {
                            this.openWorldMap(player, 0);
                        } else if (slot == 30 && plugin.getTutorialQuestManager() != null
                                && plugin.getConfig().getBoolean("rpg.quest-journal.enabled", true)) {
                            plugin.getTutorialQuestManager().openJournal(player);
                        } else if (slot == 32) {
                            this.openStoryMenu(player, 0);
                        } else if (slot == 49) {
                            this.openMainMenu(player);
                        }
                        break;
                    case WORLD_MAP:
                        if (slot == 45 && holder.page > 0) {
                            this.openWorldMap(player, holder.page - 1);
                        } else if (slot == 49) {
                            this.openMainMenu(player);
                        } else if (slot == 53) {
                            this.openWorldMap(player, holder.page + 1);
                        } else if (choice != null) {
                            HumanRealmDefinitionLoader.HumanRealmDefinition realm =
                                plugin.getRpgWorldManager().getDefinitions().get(choice);
                            if (realm != null) this.travelToRealm(player, realm);
                        }
                        break;
                    case STORY:
                        if (slot == 45 && holder.page > 0) {
                            this.openStoryMenu(player, holder.page - 1);
                        } else if (slot == 49) {
                            this.openMainMenu(player);
                        } else if (slot == 53) {
                            this.openStoryMenu(player, holder.page + 1);
                        } else if (choice != null) {
                            this.openStoryChapter(player, choice);
                        }
                        break;
               case ADMIN:
                  if (slot == 10) {
                     this.openAdminCategory(player, "server");
                  } else if (slot == 12) {
                     if (player.hasPermission("vnmine.command.playeradmin")) {
                        if (plugin.getPlayerAdminManager() != null) plugin.getPlayerAdminManager().open(player);
                     } else if (hasAnyPermission(player, "vnmine.command.teleport.other",
                             "vnmine.command.users.manage", "vnmine.command.fly", "vnmine.command.nodame",
                             "vnmine.command.gamemode", "vnmine.command.death.reset",
                             "vnmine.command.death.history")) {
                        this.openAdminCategory(player, "players");
                     }
                  } else if (slot == 14) {
                     this.openAdminCategory(player, "permissions");
                  } else if (slot == 16 && isAdminRpgToolsEnabled()
                          && (player.hasPermission("vnmine.command.skillbook.admin")
                              || player.hasPermission("vnmine.command.progression.admin"))) {
                     this.openAdminCategory(player, "rpg");
                  } else if (slot == 22 && player.hasPermission("vnmine.command.army.admin")) {
                     this.openAdminCategory(player, "army");
                  } else if (slot == 18 && player.hasPermission("vnmine.command.quest.admin")) {
                     if (plugin.getQuestCatalogManager() != null) plugin.getQuestCatalogManager().openAdmin(player);
                  } else if (slot == 20 && player.hasPermission("vnmine.command.npc.admin")) {
                     if (plugin.getNpcManager() != null) plugin.getNpcManager().openAdminMenu(player);
                  } else if (slot == 24 && player.hasPermission("vnmine.command.playeradmin")
                          && hasAnyPermission(player, "vnmine.command.teleport.other",
                              "vnmine.command.users.manage", "vnmine.command.fly", "vnmine.command.nodame",
                              "vnmine.command.gamemode", "vnmine.command.death.reset",
                              "vnmine.command.death.history")) {
                     this.openAdminCategory(player, "players");
                  } else if (slot == 49) {
                     this.openMainMenu(player);
                  }
                  break;
               case ADMIN_CATEGORY:
                  if (slot == 49) {
                     this.openAdminMenu(player);
                  } else if ("server".equals(holder.itemType)) {
                     if (slot == 10 && player.hasPermission("vnmine.command.disasters")) this.openDisasterMenu(player);
                     else if (slot == 11 && player.hasPermission("vnmine.command.disasters")) player.performCommand("vnevent stop");
                     else if (slot == 12 && player.hasPermission("vnmine.command.bosses")) this.openBossMenu(player);
                     else if (slot == 13 && player.hasPermission("vnmine.command.bosses")) player.performCommand("vnboss kill");
                     else if (slot == 14 && player.hasPermission("vnmine.command.reload")) player.performCommand("vnreload");
                     else if (slot == 15 && player.hasPermission("vnmine.command.spawn.manage")) player.performCommand("spawn");
                     else if (slot == 16 && player.hasPermission("vnmine.command.spawn.manage")) player.performCommand("vnspawn set");
                     else if (slot == 19 && player.hasPermission("vnmine.command.time.manage")) this.openTimeMenu(player);
                     else if (slot == 20 && player.hasPermission("vnmine.command.day.manage")) this.openDayMenu(player);
                     else if (slot == 21 && player.hasPermission("vnmine.command.spawn.manage")) this.openProtectionMenu(player);
                     else if (slot == 22 && player.hasPermission("vnmine.command.scoreboard.reset")) this.openAdminScoreboardConfirm(player);
                     else if (slot == 23 && player.hasPermission("vnmine.command.spawn.manage")) this.openAdminSpawnWorlds(player);
                  } else if ("players".equals(holder.itemType)) {
                     if (slot == 10 && player.hasPermission("vnmine.command.teleport.other")) this.openTeleportPlayers(player, 0);
                     else if (slot == 11 && player.hasPermission("vnmine.command.users.manage")) this.openAdminUserPlayers(player, 0);
                     else if (slot == 12 && player.hasPermission("vnmine.command.fly")) player.performCommand("fly");
                     else if (slot == 13 && player.hasPermission("vnmine.command.nodame")) player.performCommand("nodame");
                     else if (slot == 14 && player.hasPermission("vnmine.command.gamemode")) player.performCommand("gm0");
                     else if (slot == 15 && player.hasPermission("vnmine.command.gamemode")) player.performCommand("gm1");
                     else if (slot == 16 && player.hasPermission("vnmine.command.gamemode")) player.performCommand("gm2");
                     else if (slot == 19 && player.hasPermission("vnmine.command.gamemode")) player.performCommand("gm3");
                     else if (slot == 20 && player.hasPermission("vnmine.command.death.reset")) this.openAdminDeathPlayers(player);
                     else if (slot == 21 && player.hasPermission("vnmine.command.death.history")) player.performCommand("vndeathhistory");
                  } else if ("permissions".equals(holder.itemType)) {
                     if (slot == 11 && player.hasPermission("vnmine.command.groups.manage")) this.openAdminGroupMenu(player);
                     else if (slot == 15 && player.hasPermission("vnmine.command.users.manage")) this.openAdminUserPlayers(player, 0);
                  } else if ("army".equals(holder.itemType)
                          && player.hasPermission("vnmine.command.army.admin")) {
                     if (slot == 11) this.openAdminArmyPlayers(player, 0);
                     else if (slot == 15) this.openAdminItemPlayers(player, 0);
                  } else if ("rpg".equals(holder.itemType) && isAdminRpgToolsEnabled()) {
                     if (slot == 11 && player.hasPermission("vnmine.command.skillbook.admin")) {
                        this.openAdminRpgPlayers(player, "skillbook", 0);
                     } else if (slot == 13 && player.hasPermission("vnmine.command.skillbook.admin")) {
                        plugin.getCharacterSelectionManager().openStarterBookAdminMenu(player);
                     } else if (slot == 15 && player.hasPermission("vnmine.command.progression.admin")) {
                        this.openAdminRpgPlayers(player, "progression", 0);
                     }
                  }
                  break;
               case ADMIN_RPG_PLAYERS:
                  if (slot == 45 && holder.page > 0) {
                     this.openAdminRpgPlayers(player, holder.itemType, holder.page - 1);
                  } else if (slot == 53) {
                     this.openAdminRpgPlayers(player, holder.itemType, holder.page + 1);
                  } else if (slot == 49) {
                     this.openAdminCategory(player, "rpg");
                  } else if (choice != null) {
                     UUID targetId = UUID.fromString(choice);
                     if (Bukkit.getPlayer(targetId) == null) {
                        player.sendMessage("§cNgười chơi đã ngoại tuyến.");
                     } else if ("skillbook".equals(holder.itemType)) {
                        this.openAdminSkillBooks(player, targetId, 0);
                     } else {
                        this.openAdminProgression(player, targetId);
                     }
                  }
                  break;
               case ADMIN_SKILLBOOKS:
                  if (slot == 45 && holder.page > 0) {
                     this.openAdminSkillBooks(player, holder.target, holder.page - 1);
                  } else if (slot == 53) {
                     this.openAdminSkillBooks(player, holder.target, holder.page + 1);
                  } else if (slot == 49) {
                     this.openAdminRpgPlayers(player, "skillbook", 0);
                  } else if (choice != null && isAdminRpgToolsEnabled()
                          && player.hasPermission("vnmine.command.skillbook.admin")) {
                     Player target = Bukkit.getPlayer(holder.target);
                     if (target == null) {
                        player.sendMessage("§cNgười chơi đã ngoại tuyến.");
                     } else if (plugin.getSkillManager().giveSkillBookTo(target, choice)) {
                        player.sendMessage("§aĐã cấp sách kỹ năng §e" + choice + "§a cho §f"
                            + target.getName() + "§a.");
                     } else {
                        player.sendMessage("§cKhông cấp được sách kỹ năng. Hãy kiểm tra ID hoặc túi đồ người chơi.");
                     }
                  }
                  break;
               case ADMIN_PROGRESSION:
                  if (slot == 22) {
                     this.openAdminRpgPlayers(player, "progression", 0);
                  } else if (choice != null && isAdminRpgToolsEnabled()
                          && player.hasPermission("vnmine.command.progression.admin")) {
                     org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(holder.target);
                     if (target.getName() == null) {
                        player.sendMessage("§cKhông tìm thấy hồ sơ người chơi.");
                        break;
                     }
                     boolean experienceGrant = choice.startsWith("exp:");
                     int amount;
                     try {
                        amount = Integer.parseInt(choice.substring(experienceGrant ? 4 : 6));
                     } catch (NumberFormatException exception) {
                        player.sendMessage("§cGiá trị tiến trình không hợp lệ.");
                        break;
                     }
                     UUID targetId = target.getUniqueId();
                     String targetName = target.getName();
                     var database = plugin.getPlayerProfileDatabaseManager();
                     debugRpg("[RPG-DEBUG][AdminProgression] actor=" + player.getName()
                         + ", target=" + targetName + ", kind=" + (experienceGrant ? "exp" : "levels")
                         + ", amount=" + amount);
                     database.executeGameplayDatabaseTask("admin.progression actor=" + player.getName()
                             + " target=" + targetName, () -> experienceGrant
                             ? database.awardCharacterExperience(targetId.toString(), amount,
                                 java.time.Instant.now().toString())
                             : database.grantCharacterLevels(targetId.toString(), amount,
                                 java.time.Instant.now().toString()))
                         .whenComplete((grant, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                             if (failure != null) {
                                 plugin.getLogger().log(Level.SEVERE,
                                     "Could not apply admin RPG progression grant to " + targetName + ".", failure);
                                 if (player.isOnline()) {
                                     player.sendMessage("§cKhông thể cập nhật tiến trình RPG. Hãy kiểm tra console.");
                                 }
                                 return;
                             }
                             if (grant.isEmpty()) {
                                 if (player.isOnline()) {
                                     player.sendMessage("§cNgười chơi chưa có nhân vật RPG đang hoạt động.");
                                 }
                                 return;
                             }
                             var result = grant.get();
                             debugRpg("[RPG-DEBUG][AdminProgression] target=" + targetName
                                 + ", previousLevel=" + result.previousLevel()
                                 + ", currentLevel=" + result.currentLevel()
                                 + ", skillPointsGained=" + result.skillPointsGained());
                             String grantDescription = experienceGrant ? "+" + amount + " EXP" : "+" + amount + " level";
                             String message = "§aĐã cấp " + grantDescription + " cho §f" + targetName
                                 + "§a: level " + result.previousLevel() + " → " + result.currentLevel()
                                 + ", EXP dư " + result.experience() + ", +" + result.skillPointsGained()
                                 + " điểm kỹ năng.";
                             if (player.isOnline()) player.sendMessage(message);
                             Player onlineTarget = Bukkit.getPlayer(targetId);
                             if (onlineTarget != null) {
                                 onlineTarget.sendMessage("§aQuản trị viên đã cập nhật tiến trình RPG của bạn. Cấp hiện tại: §e"
                                     + result.currentLevel() + "§a.");
                                 if (plugin.getCharacterSelectionManager() != null) {
                                     plugin.getCharacterSelectionManager().refreshLoadedProfile(onlineTarget);
                                 }
                             }
                         }));
                  }
                  break;
               case ADMIN_PLAYERS:
                  if (slot == 45 && holder.page > 0) {
                     this.openAdminArmyPlayers(player, holder.page - 1);
                  } else if (slot == 53) {
                     this.openAdminArmyPlayers(player, holder.page + 1);
                  } else if (slot == 49) {
                     this.openAdminMenu(player);
                  } else if (choice != null) {
                     this.openGiveTypeMenu(player, UUID.fromString(choice));
                  }
                  break;
               case GIVE_TYPE:
                  if (slot == 11) {
                     this.openClassMenu(player, holder.target, "army");
                  } else if (slot == 15) {
                     this.openClassMenu(player, holder.target, "token");
                  } else if (slot == 22) {
                     this.openAdminArmyPlayers(player, 0);
                  }
                  break;
               case CLASS_SELECT:
                  if (slot == 49) {
                     if (holder.itemType.equals("hire")) {
                        this.openMainMenu(player);
                     } else {
                        this.openGiveTypeMenu(player, holder.target);
                     }
                  } else if (choice != null) {
                     this.openTierMenu(player, holder.target, holder.itemType, choice);
                  }
                  break;
               case TIER_SELECT:
                  if (slot == 49) {
                     this.openClassMenu(player, holder.target, holder.itemType);
                  } else if (choice != null) {
                     int tier = parsePositiveInt(choice);
                     player.closeInventory();
                     switch (holder.itemType) {
                        case "hire":
                           this.hire(player, new String[]{"hire", holder.classId, Integer.toString(tier)});
                           return;
                        case "token":
                           Player target = Bukkit.getPlayer(holder.target);
                           if (target == null) {
                              player.sendMessage("§cNgười nhận đã offline.");
                           } else {
                              this.handleTokenCommand(player, new String[]{"token", "give", target.getName(), holder.classId, Integer.toString(tier)});
                           }

                           return;
                        case "army":
                           Player armyRecipient = Bukkit.getPlayer(holder.target);
                           if (armyRecipient == null) {
                              player.sendMessage("§cNgười nhận đã offline.");
                           } else {
                              this.handleGiveCommand(player, new String[]{"give", armyRecipient.getName(), "army", holder.classId, Integer.toString(tier)});
                           }

                           return;
                        default:
                           player.sendMessage("§cLoại cấp không hợp lệ.");
                     }
                  }
                  break;
               case WELCOME_GIFT:
                  if (slot == 49) {
                     player.closeInventory();
                  } else if (choice != null) {
                     this.selectWelcomeGift(player, choice);
                  }
                  break;
               case ITEM_GIVE_PLAYERS:
                  if (slot == 45 && holder.page > 0) {
                     this.openAdminItemPlayers(player, holder.page - 1);
                  } else if (slot == 53) {
                     this.openAdminItemPlayers(player, holder.page + 1);
                  } else if (slot == 49) {
                     this.openAdminMenu(player);
                  } else if (choice != null) {
                     this.openItemGearClassMenu(player, UUID.fromString(choice));
                  }
                  break;
               case ITEM_GIVE_CLASS:
                  if (slot == 49) {
                     this.openAdminItemPlayers(player, 0);
                  } else if (choice != null) {
                     this.openItemGearMaterialMenu(player, holder.target, choice);
                  }
                  break;
               case ITEM_GIVE_MATERIAL:
                  if (slot == 49) {
                     this.openItemGearClassMenu(player, holder.target);
                  } else if (choice != null) {
                     this.openItemGearStarsMenu(player, holder.target, holder.classId, choice);
                  }
                  break;
               case ITEM_GIVE_STARS:
                  if (slot == 49) {
                     this.openItemGearMaterialMenu(player, holder.target, holder.classId);
                  } else if (choice != null) {
                     this.openItemGearSetMenu(player, holder.target, holder.classId, holder.gearMaterial, parsePositiveInt(choice));
                  }
                  break;
               case ITEM_GIVE_SET:
                  if (slot == 49) {
                     this.openItemGearStarsMenu(player, holder.target, holder.classId, holder.gearMaterial);
                  } else if (choice != null) {
                     this.openItemGearSlotMenu(player, holder.target, holder.classId, holder.gearMaterial, holder.gearStars, choice);
                  }
                  break;
               case ITEM_GIVE_SLOT:
                  if (slot == 49) {
                     this.openItemGearSetMenu(player, holder.target, holder.classId, holder.gearMaterial, holder.gearStars);
                  } else if (choice != null) {
                     Player target = Bukkit.getPlayer(holder.target);
                     if (target == null) {
                        player.sendMessage("§cNgười nhận đã offline; hãy chọn người chơi khác.");
                        this.openAdminItemPlayers(player, 0);
                     } else {
                        this.giveSoldierGear(target, holder.classId, holder.gearMaterial, holder.gearStars, holder.gearSet, choice);
                        player.sendMessage(
                           "§aĐã cấp "
                              + (choice.equals("set") ? "trọn set" : "một món")
                              + " §e"
                              + this.classDisplay(holder.classId)
                              + " "
                              + this.gearMaterialDisplay(holder.gearMaterial)
                              + " "
                              + "★".repeat(holder.gearStars)
                              + " §acho §e"
                              + target.getName()
                              + "§a."
                        );
                        target.sendMessage(
                           "§aBạn nhận được "
                              + (choice.equals("set") ? "bộ trang bị lính" : "trang bị lính")
                              + " §e"
                              + this.classDisplay(holder.classId)
                              + " "
                              + this.gearMaterialDisplay(holder.gearMaterial)
                              + " "
                              + "★".repeat(holder.gearStars)
                              + "§a. Chỉ lính mới có thể trang bị vật phẩm này."
                        );
                        player.closeInventory();
                     }
                  }
                  break;
               case DISASTER_SELECT:
                  if (slot == 49) {
                     this.openAdminMenu(player);
                  } else if (choice != null) {
                     if (!player.hasPermission("vnmine.command.disasters")) {
                        player.sendMessage("§cBạn không có quyền gọi thiên tai.");
                        return;
                     }

                     int warning = Math.max(1, Math.min(3600, this.plugin.getConfig().getInt("menus.admin.warning-seconds", 30)));
                     int duration = Math.max(1, Math.min(86400, this.plugin.getConfig().getInt("menus.admin.duration-seconds", 120)));
                     if (this.plugin.getDisasterManager().triggerDisaster(choice, warning, duration)) {
                        player.sendMessage("§aĐã kích hoạt §e" + this.plugin.getDisasterManager().getDisasterName(choice) + "§a.");
                     } else {
                        player.sendMessage("§cKhông thể kích hoạt thiên tai; có thể đang có sự kiện khác chạy.");
                     }

                     player.closeInventory();
                  }
                  break;
               case BOSS_SELECT:
                  if (slot == 49) {
                     this.openAdminMenu(player);
                  } else if (choice != null) {
                     if (!player.hasPermission("vnmine.command.bosses")) {
                        player.sendMessage("§cBạn không có quyền gọi boss.");
                        return;
                     }

                     this.openBossLevelMenu(player, choice);
                  }
                  break;
               case BOSS_LEVEL_SELECT:
                  if (slot == 22) {
                     this.openBossMenu(player);
                  } else if (choice != null) {
                     if (!player.hasPermission("vnmine.command.bosses")) {
                        player.sendMessage("§cBạn không có quyền gọi boss.");
                        return;
                     }

                     int warning = Math.max(1, Math.min(3600, this.plugin.getConfig().getInt("menus.admin.warning-seconds", 30)));
                     int duration = Math.max(1, Math.min(86400, this.plugin.getConfig().getInt("menus.admin.duration-seconds", 120)));
                     int bossLevel = parsePositiveInt(choice);
                     if (this.plugin.getBossEventManager().triggerBoss(holder.classId, bossLevel, warning, duration)) {
                        player.sendMessage("§aĐã gọi §e" + this.plugin.getBossEventManager().getBossName(holder.classId) + " §alevel " + bossLevel + ".");
                     } else {
                        player.sendMessage("§cKhông thể gọi boss; có thể đang có boss khác chạy.");
                     }

                     player.closeInventory();
                  }
                  break;
               case ADMIN_TIME:
                  if (slot == 11) {
                     this.openTimeDurationMenu(player, "day");
                  } else if (slot == 15) {
                     this.openTimeDurationMenu(player, "night");
                  } else if (slot == 22) {
                     this.openAdminMenu(player);
                  }
                  break;
               case TIME_DURATION:
                  if (slot == 22) {
                     this.openTimeMenu(player);
                  } else if (choice != null) {
                     player.closeInventory();
                     player.performCommand("vntime set " + holder.itemType + " " + choice);
                  }
                  break;
               case ADMIN_DAY:
                  if (slot == 22) {
                     this.openAdminMenu(player);
                  } else if (choice != null) {
                     player.closeInventory();
                     player.performCommand("vnday set " + choice);
                  }
                  break;
               case ADMIN_PROTECT:
                  if (slot == 22) {
                     this.openAdminMenu(player);
                  } else if (slot == 15) {
                     player.performCommand("vnspawn list");
                  } else if (slot == 16) {
                     this.openProtectionRemoveMenu(player);
                  } else if (choice != null && choice.startsWith("protect:")) {
                     player.closeInventory();
                     player.performCommand("vnspawn protect " + choice.substring("protect:".length()));
                  }
                  break;
               case PROTECTION_REMOVE:
                  if (slot == 49) {
                     this.openProtectionMenu(player);
                  } else if (choice != null) {
                     player.closeInventory();
                     player.performCommand("vnspawn remove " + choice);
                  }
                  break;
               case TELEPORT_PLAYERS:
                  if (slot == 45 && holder.page > 0) {
                     this.openTeleportPlayers(player, holder.page - 1);
                  } else if (slot == 53) {
                     this.openTeleportPlayers(player, holder.page + 1);
                  } else if (slot == 49) {
                     this.openAdminMenu(player);
                  } else if (choice != null) {
                     this.openTeleportWorlds(player, UUID.fromString(choice));
                  }
                  break;
               case TELEPORT_WORLDS:
                  if (slot == 49) {
                     this.openTeleportPlayers(player, 0);
                  } else if (choice != null) {
                     Player target = Bukkit.getPlayer(holder.target);
                     if (target == null) {
                        player.sendMessage("§cNgười chơi đã offline.");
                     } else {
                        player.closeInventory();
                        player.performCommand("tp " + target.getName() + " " + choice);
                     }
                  }
                  break;
               case ADMIN_USER_PLAYERS:
                  if (slot == 45 && holder.page > 0) {
                     this.openAdminUserPlayers(player, holder.page - 1);
                  } else if (slot == 53) {
                     this.openAdminUserPlayers(player, holder.page + 1);
                  } else if (slot == 49) {
                     this.openAdminMenu(player);
                  } else if (choice != null) {
                     this.openAdminUserActions(player, UUID.fromString(choice));
                  }
                  break;
               case ADMIN_USER_ACTION:
                  if (slot == 22) {
                     this.openAdminUserPlayers(player, 0);
                  } else if (slot == 15) {
                     this.openAdminUserGroupSelect(player, holder.target);
                  } else if (slot == 16) {
                     this.openAdminUserPermissionActions(player, holder.target);
                  } else if (slot == 11 || slot == 13) {
                     Player target = Bukkit.getPlayer(holder.target);
                     if (target == null) {
                        player.sendMessage("§cNgười chơi đã offline.");
                     } else {
                        player.closeInventory();
                        player.performCommand(slot == 11 ? "vnuser info " + target.getName() : "vnuser reset " + target.getName());
                     }
                  }
                  break;
               case ADMIN_GROUPS:
                  if (slot == 10) {
                     player.performCommand("vngroup list");
                  } else if (slot == 11) {
                     if (!player.hasPermission("vnmine.command.groups.manage")) {
                        player.sendMessage("§cBạn không có quyền quản lý nhóm.");
                        return;
                     }

                     this.awaitingGroupName.add(player.getUniqueId());
                     player.closeInventory();
                     player.sendMessage("§eNhập tên nhóm (1-32 ký tự, chữ thường/số/_/-) vào chat.");
                  } else if (slot == 12) {
                     this.openAdminGroupSelect(player, "info");
                  } else if (slot == 13) {
                     this.openAdminGroupSelect(player, "delete");
                  } else if (slot == 14) {
                     this.openAdminGroupSelect(player, "grant");
                  } else if (slot == 15) {
                     this.openAdminGroupSelect(player, "revoke");
                  } else if (slot == 22) {
                     this.openAdminMenu(player);
                  }
                  break;
               case ADMIN_GROUP_SELECT:
                  if (slot == 49) {
                     this.openAdminGroupMenu(player);
                  } else if (choice != null) {
                     switch (holder.itemType) {
                        case "info":
                           player.closeInventory();
                           player.performCommand("vngroup info " + choice);
                           return;
                        case "delete":
                           MercenaryManager.ServiceMenuHolder confirm = this.createServiceMenu(
                              player, MercenaryManager.MenuType.ADMIN_GROUP_DELETE_CONFIRM, null, null, choice, 0, 27, "Admin • Xóa nhóm " + choice
                           );
                           confirm.inventory
                              .setItem(
                                 11, this.menuItem(Material.RED_DYE, "§cXác nhận xóa", List.of("§7Nhóm có thành viên hoặc nhóm hệ thống sẽ được giữ lại."))
                              );
                           confirm.inventory.setItem(15, this.menuItem(Material.ARROW, "§7Hủy", List.of()));
                           player.openInventory(confirm.inventory);
                           return;
                        case "grant":
                        case "revoke":
                           this.openAdminPermissionSelect(player, null, holder.itemType.equals("grant") ? "group-add" : "group-remove", choice);
                     }
                  }
                  break;
               case ADMIN_GROUP_DELETE_CONFIRM:
                  if (slot == 11) {
                     player.closeInventory();
                     player.performCommand("vngroup delete " + holder.classId);
                  } else if (slot == 15) {
                     this.openAdminGroupSelect(player, "delete");
                  }
                  break;
               case ADMIN_PERMISSION_SELECT:
                  if (slot == 49) {
                     if (holder.itemType.startsWith("group-")) {
                        this.openAdminGroupSelect(player, holder.itemType.equals("group-add") ? "grant" : "revoke");
                     } else {
                        this.openAdminUserPermissionActions(player, holder.target);
                     }
                  } else if (choice != null) {
                     player.closeInventory();
                     if (holder.itemType.startsWith("group-")) {
                        String action = holder.itemType.equals("group-add") ? "add" : "remove";
                        player.performCommand("vngroup permission " + action + " " + holder.classId + " " + choice);
                     } else {
                        Player target = Bukkit.getPlayer(holder.target);
                        if (target == null) {
                           player.sendMessage("§cNgười chơi đã offline.");
                        } else {
                           String action = holder.itemType.equals("user-add") ? "add" : "remove";
                           player.performCommand("vnuser permission " + action + " " + target.getName() + " " + choice);
                        }
                     }
                  }
                  break;
               case ADMIN_USER_GROUP_SELECT:
                  if (slot == 49) {
                     this.openAdminUserActions(player, holder.target);
                  } else if (choice != null) {
                     Player target = Bukkit.getPlayer(holder.target);
                     if (target == null) {
                        player.sendMessage("§cNgười chơi đã offline.");
                     } else {
                        player.closeInventory();
                        player.performCommand("vnuser group " + target.getName() + " " + choice);
                     }
                  }
                  break;
               case ADMIN_USER_PERMISSION_ACTIONS:
                  if (slot == 22) {
                     this.openAdminUserActions(player, holder.target);
                  } else if (slot == 11) {
                     this.openAdminPermissionSelect(player, holder.target, "user-add", null);
                  } else if (slot == 15) {
                     this.openAdminPermissionSelect(player, holder.target, "user-remove", null);
                  }
               case ADMIN_USER_PERMISSION_SELECT:
               default:
                  break;
               case ADMIN_SCOREBOARD_CONFIRM:
                  if (slot == 11) {
                     player.closeInventory();
                     player.performCommand("scoreboard reset");
                  } else if (slot == 15) {
                     this.openAdminMenu(player);
                  }
                  break;
               case ADMIN_DEATH_PLAYERS:
                  if (slot == 49) {
                     this.openAdminMenu(player);
                  } else if (choice != null) {
                     Player target = Bukkit.getPlayer(UUID.fromString(choice));
                     if (target == null) {
                        player.sendMessage("§cNgười chơi đã offline.");
                     } else {
                        player.closeInventory();
                        player.performCommand("vn death reset " + target.getName());
                     }
                  }
                  break;
               case ADMIN_SPAWN_WORLD:
                  if (slot == 49) {
                     this.openAdminMenu(player);
                  } else if (choice != null) {
                     player.closeInventory();
                     player.performCommand("vnspawn go " + choice);
                  }
                  break;
               case UNIT_DELETE_CONFIRM:
                  if (slot != 15 && slot != 22) {
                     if (slot == 11) {
                        MercenaryManager.Unit unit = this.units.get(UUID.fromString(holder.itemType));
                        if (unit != null && unit.owner.equals(player.getUniqueId())) {
                           this.handleRemoveCommand(player, new String[]{"remove", shortId(unit.id)});
                           if (!this.units.containsKey(unit.id)) {
                              player.closeInventory();
                           }
                        } else {
                           player.sendMessage("§cKhông tìm thấy lính cần xóa.");
                           player.closeInventory();
                        }
                     }
                  } else {
                     UUID unitId = holder.itemType == null ? null : UUID.fromString(holder.itemType);
                     MercenaryManager.Unit unit = unitId == null ? null : this.units.get(unitId);
                     if (unit != null && unit.owner.equals(player.getUniqueId())) {
                        this.openUnitMenu(player, unit);
                     } else {
                        this.openArmyMenu(player, 0);
                     }
                  }
            }
         }
      }
   }

   @EventHandler
    public void onServiceMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ServiceMenuHolder) event.setCancelled(true);
    }

   @EventHandler
   public void onArmyMenuClick(InventoryClickEvent event) {
      InventoryHolder inventoryHolder = event.getInventory().getHolder();
      if (inventoryHolder instanceof MercenaryManager.ArmyMenuHolder || inventoryHolder instanceof MercenaryManager.UnitMenuHolder) {
         event.setCancelled(true);
         if (event.getWhoClicked() instanceof Player player) {
            if (inventoryHolder instanceof MercenaryManager.ArmyMenuHolder holder) {
               if (holder.owner.equals(player.getUniqueId()) && event.getClickedInventory() == event.getView().getTopInventory()) {
                  int slot = event.getRawSlot();
                  if (slot == 2) {
                     this.recall(player, new String[]{"recall"});
                  } else if (slot == 4) {
                     this.openClassMenu(player, null, "hire");
                  } else if (slot == 6) {
                     this.rest(player, new String[]{"rest"});
                  } else if (slot == 49) {
                     this.openMainMenu(player);
                  } else if (slot == 45 && holder.page > 0) {
                     this.openArmyMenu(player, holder.page - 1);
                  } else if (slot == 53) {
                     this.openArmyMenu(player, holder.page + 1);
                  } else if (slot >= 0 && slot < 54 && slot != 49) {
                     int row = slot / 9 - 1;
                     int column = slot % 9;
                     if (row >= 0 && row <= 3 && column >= 1 && column <= 7) {
                        int index = row * 7 + column - 1;
                        if (index < holder.unitIds.size()) {
                           MercenaryManager.Unit unit = this.units.get(holder.unitIds.get(index));
                           if (unit != null && unit.owner.equals(player.getUniqueId())) {
                              this.openUnitMenu(player, unit);
                           }
                        }
                     }
                  }
               }
            } else {
               MercenaryManager.UnitMenuHolder holder = (MercenaryManager.UnitMenuHolder)inventoryHolder;
               if (holder.owner.equals(player.getUniqueId()) && event.getClickedInventory() == event.getView().getTopInventory()) {
                  MercenaryManager.Unit unit = this.units.get(holder.unit);
                  if (unit != null && unit.owner.equals(player.getUniqueId())) {
                     switch (event.getRawSlot()) {
                        case 4:
                           this.openUnitMenu(player, unit);
                           break;
                        case 5:
                        case 6:
                        case 7:
                        case 8:
                        case 9:
                        case 18:
                        case 19:
                        case 20:
                        case 21:
                        default:
                           return;
                        case 10:
                           this.updateUnitMode(player, unit, MercenaryManager.Mode.ATTACK);
                           break;
                        case 11:
                           this.updateUnitMode(player, unit, MercenaryManager.Mode.DEFEND);
                           break;
                        case 12:
                           this.updateUnitMode(player, unit, MercenaryManager.Mode.PROTECT);
                           break;
                        case 13:
                           this.toggleMute(player, unit);
                           break;
                        case 14:
                           this.toggleUnitDeployment(player, unit);
                           break;
                        case 15:
                           this.openUnitDeleteConfirm(player, unit);
                           break;
                        case 16:
                           this.openGear(player, new String[]{"gear", shortId(unit.id)});
                           break;
                        case 17:
                           this.upgradeTier(player, unit);
                           break;
                        case 22:
                           this.openArmyMenu(player, 0);
                     }

                     if (player.getOpenInventory().getTopInventory().getHolder() instanceof MercenaryManager.UnitMenuHolder) {
                        this.openUnitMenu(player, unit);
                     }
                  } else {
                     this.openArmyMenu(player, 0);
                  }
               }
            }
         }
      }
   }

    private boolean canUpgrade(Unit unit) {
        return unit.level >= maxLevel && unit.tier < unit.originalTier + 3;
    }

    private List<String> tierUpgradeLore(Unit unit) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Bậc hiện tại: §f" + unit.tier + " §7| Bậc gốc: §f" + unit.originalTier);
        if (unit.level < maxLevel) {
            lore.add("§cCần đạt cấp " + maxLevel + ".");
        } else if (unit.tier >= unit.originalTier + 3) {
            lore.add("§7Đã đạt giới hạn nâng bậc.");
        } else {
            int nextTier = unit.tier + 1;
            lore.add("§eNâng lên Bậc " + nextTier + ":");
            ConfigurationSection costs = settings.getConfigurationSection("classes." + unit.unitClass
                + ".tier-upgrades." + nextTier + ".materials");
            if (costs == null || costs.getKeys(false).isEmpty()) {
                lore.add("§cChưa cấu hình vật phẩm nâng bậc.");
            } else {
                for (String material : costs.getKeys(false)) {
                    lore.add("§7" + material + " x" + costs.getInt(material));
                }
            }
            lore.add("§aNhấn để nâng bậc.");
        }
        return lore;
    }

    private void upgradeTier(Player player, Unit unit) {
        if (!canUpgrade(unit)) {
            player.sendMessage(unit.level < maxLevel
                ? "§cLính phải đạt cấp " + maxLevel + " mới nâng bậc được."
                : "§cLính đã đạt giới hạn tăng tối đa 3 bậc so với bậc gốc.");
            return;
        }
        int nextTier = unit.tier + 1;
        ConfigurationSection section = settings.getConfigurationSection("classes." + unit.unitClass
            + ".tier-upgrades." + nextTier + ".materials");
        if (section == null || section.getKeys(false).isEmpty()) {
            player.sendMessage("§cChưa cấu hình vật phẩm nâng lên Bậc " + nextTier + " cho class này.");
            return;
        }
        Map<Material, Integer> costs = readMaterialCosts(section, player, "nâng lên Bậc " + nextTier);
        if (costs == null) return;
        List<String> missing = missingMaterials(player, costs);
        if (!missing.isEmpty()) {
            player.sendMessage("§cThiếu điều kiện nâng bậc:");
            missing.forEach(value -> player.sendMessage("§7- " + value));
            return;
        }
        ItemStack[] before = cloneContents(player.getInventory().getContents());
        int previousTier = unit.tier;
        int previousLevel = unit.level;
        int previousExperience = unit.experience;
        if (!removeMaterials(player, costs)) {
            player.getInventory().setContents(before);
            player.sendMessage("§cKhông thể trừ đủ vật phẩm; giao dịch đã được hoàn tác.");
            return;
        }
        unit.tier = nextTier;
        unit.level = 1;
        unit.experience = 0;
        Mob mob = activeEntities.get(unit.id);
        if (mob != null && mob.isValid()) {
            applyStats(unit, mob);
            updateEntityName(unit, mob);
        }
        if (!saveData()) {
            unit.tier = previousTier;
            unit.level = previousLevel;
            unit.experience = previousExperience;
            player.getInventory().setContents(before);
            if (mob != null && mob.isValid()) {
                applyStats(unit, mob);
                updateEntityName(unit, mob);
            }
            player.sendMessage("§cKhông thể lưu bậc mới; vật phẩm đã được hoàn lại.");
            return;
        }
        player.sendMessage("§a" + displayName(unit) + " đã nâng lên Bậc " + nextTier + "!");
    }

   @EventHandler
    public void onArmyMenuDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof ArmyMenuHolder || holder instanceof UnitMenuHolder) event.setCancelled(true);
    }

    private void updateUnitMode(Player player, Unit unit, Mode mode) {
        Mode previous = unit.mode;
        unit.mode = mode;
        if (!saveData()) {
            unit.mode = previous;
            player.sendMessage("§cKhông thể lưu chế độ lính; vui lòng thử lại.");
        }
    }

    private void toggleUnitDeployment(Player player, Unit unit) {
        if (!unit.deployed && unit.recoveryUntilMillis > System.currentTimeMillis()) {
            player.sendMessage("§cLính đang hồi phục, còn " + remainingRecoverySeconds(unit) + " giây.");
            return;
        }
        if (unit.deployed) {
            Mob mob = activeEntities.get(unit.id);
            if (mob == null || !mob.isValid()) mob = findExistingEntity(unit);
            if (mob != null && mob.isValid()) {
                captureEquipment(unit, mob);
                updateLocation(unit, mob.getLocation());
            }
            boolean previousHorseDead = unit.horseDead;
            UUID previousEntityId = unit.entityId;
            unit.entityId = null;
            unit.deployed = false;
            unit.horseDead = false;
            if (!saveData()) {
                unit.deployed = true;
                unit.horseDead = previousHorseDead;
                unit.entityId = previousEntityId;
                player.sendMessage("§cKhông thể lưu trạng thái lính; hãy thử lại.");
                return;
            }
            cleanupSummonedMinions(unit.id);
            removeMount(unit.id, mob);
            Mob removed = activeEntities.remove(unit.id);
            if (removed == null) removed = mob;
            if (removed != null && removed.isValid()) removed.remove();
            return;
        }
        if (deployedUnitCount(player.getUniqueId()) >= maxDeployedUnits) {
            player.sendMessage("§cBạn đã đạt giới hạn triệu hồi " + maxDeployedUnits
                + " lính. Hãy cho một lính nghỉ trước.");
            return;
        }
        unit.deployed = true;
        Mob mob = findExistingEntity(unit);
        if (mob == null) mob = spawnUnit(unit, player.getLocation().clone().add(0.8, 0.0, 0.8));
        if (mob == null) {
            unit.deployed = false;
            player.sendMessage("§cKhông thể triệu hồi lính này.");
            return;
        }
        ensurePaladinMounted(unit, mob);
        activeEntities.put(unit.id, mob);
        updateLocation(unit, mob.getLocation());
        if (!saveData()) {
            mob.remove();
            activeEntities.remove(unit.id);
            unit.deployed = false;
            unit.entityId = null;
            player.sendMessage("§cKhông thể lưu trạng thái lính; đã hủy triệu hồi.");
        }
    }

    private void openGear(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage("§eSử dụng: /vnarmy gear <ID>");
            return;
        }
        Unit unit = findOwnedByShortId(player.getUniqueId(), args[1]);
        if (unit == null) {
            player.sendMessage("§cKhông tìm thấy lính của bạn với ID bắt đầu bằng §e" + args[1] + "§c.");
            return;
        }
        openGear(player, unit);
    }

   private void openGear(Player player, MercenaryManager.Unit unit) {
      Mob active = this.activeEntities.get(unit.id);
      if (active != null && active.isValid()) {
         this.captureEquipment(unit, active);
      }

      MercenaryManager.GearHolder holder = new MercenaryManager.GearHolder(player.getUniqueId(), unit.id);
      Inventory inventory = Bukkit.createInventory(holder, 54, "Kho và trang bị #" + shortId(unit.id));
      holder.inventory = inventory;

      for (int slot = 0; slot < unit.inventory.length; slot++) {
         inventory.setItem(slot, cloneOrNull(unit.inventory[slot]));
      }

      ItemStack divider = this.menuItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());

      for (int slot = 36; slot < 45; slot++) {
         inventory.setItem(slot, divider.clone());
      }

      inventory.setItem(45, cloneOrNull(unit.equipment.get(EquipmentSlot.HAND)));
      inventory.setItem(46, cloneOrNull(unit.equipment.get(EquipmentSlot.HEAD)));
      inventory.setItem(47, cloneOrNull(unit.equipment.get(EquipmentSlot.CHEST)));
      inventory.setItem(48, cloneOrNull(unit.equipment.get(EquipmentSlot.LEGS)));
      inventory.setItem(49, cloneOrNull(unit.equipment.get(EquipmentSlot.FEET)));
      inventory.setItem(50, cloneOrNull(unit.equipment.get(EquipmentSlot.OFF_HAND)));
      inventory.setItem(51, cloneOrNull(unit.horseArmor));
      inventory.setItem(52, divider.clone());
      inventory.setItem(53, this.menuItem(Material.ARROW, "§eQuay lại menu lính", List.of("§7Lưu thay đổi và quay lại thông tin lính.")));
      player.openInventory(inventory);
      player.sendMessage("§7Kho lính: 36 ô đầu để chứa đồ; hàng cuối là các ô trang bị. Nút mũi tên dưới cùng bên phải để quay lại.");
   }

    private void loadWelcomeGiftClaims() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("[Gift] Could not create data folder for welcome-gift claims.");
            return;
        }
        YamlConfiguration loaded = new YamlConfiguration();
        if (!welcomeGiftFile.isFile()) {
            saveWelcomeGiftClaims();
            return;
        }
        try {
            loaded.load(welcomeGiftFile);
            for (String entry : loaded.getStringList("claimed")) {
                try {
                    claimedWelcomeGiftPlayers.add(UUID.fromString(entry));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("[Gift] Ignoring invalid UUID in new-player-gifts.yml: " + entry);
                }
            }
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "[Gift] Could not load new-player-gifts.yml; resetting claim state.", exception);
            claimedWelcomeGiftPlayers.clear();
        }
    }

    private void saveWelcomeGiftClaims() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("claimed", claimedWelcomeGiftPlayers.stream().map(UUID::toString).sorted().toList());
        try {
            data.save(welcomeGiftFile);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "[Gift] Could not save new-player-gifts.yml.", exception);
        }
    }

    private boolean isWelcomeGiftClaimed(UUID playerId) {
        return claimedWelcomeGiftPlayers.contains(playerId);
    }

    private boolean hasWelcomeGiftBag(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isWelcomeGiftBag(stack)) return true;
        }
        return false;
    }

    private boolean consumeWelcomeGiftBag(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isWelcomeGiftBag(stack)) {
                player.getInventory().setItem(slot, null);
                return true;
            }
        }
        return false;
    }

    private ItemStack createWelcomeGiftBag() {
        ItemStack bag = new ItemStack(Material.BLACK_BUNDLE);
        ItemMeta meta = bag.getItemMeta();
        meta.setDisplayName("§5§lTúi Quà Tân Thủ");
        meta.setLore(List.of("§7Nhận token triệu hồi lính đánh thuê bậc 1.", "§8Cầm hoặc nhấn chuột phải để mở."));
        meta.getPersistentDataContainer().set(welcomeGiftKey, PersistentDataType.BYTE, (byte) 1);
        bag.setItemMeta(meta);
        return bag;
    }

    private boolean isWelcomeGiftBag(ItemStack stack) {
        if (stack == null || stack.getType() != Material.BLACK_BUNDLE || !stack.hasItemMeta()) return false;
        return stack.getItemMeta().getPersistentDataContainer().has(welcomeGiftKey, PersistentDataType.BYTE);
    }

    private boolean handleWelcomeGiftCommand(CommandSender sender, String[] args) {
        if (args.length != 0) {
            sender.sendMessage("§eSử dụng: /quatanthu");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cLệnh chỉ dành cho người chơi.");
            return true;
        }
        if (isWelcomeGiftClaimed(player.getUniqueId())) {
            sender.sendMessage("§cBạn đã nhận quà tân thủ rồi.");
            return true;
        }
        if (hasOwnedMercenaries(player.getUniqueId())) {
            sender.sendMessage("§cBạn đã sở hữu lính đánh thuê nên không thể nhận quà tân thủ.");
            return true;
        }
        if (hasWelcomeGiftBag(player)) {
            sender.sendMessage("§eBạn đã có túi quà tân thủ trong túi đồ.");
            return true;
        }
        giveItem(player, createWelcomeGiftBag());
        sender.sendMessage("§aBạn nhận được §5Túi Quà Tân Thủ§a. Cầm lên hoặc nhấn chuột phải để mở.");
        return true;
    }

    private void openWelcomeGiftMenu(Player player) {
        if (isWelcomeGiftClaimed(player.getUniqueId())) {
            player.sendMessage("§cBạn đã nhận quà tân thủ rồi.");
            return;
        }
        if (hasOwnedMercenaries(player.getUniqueId())) {
            player.sendMessage("§cBạn đã sở hữu lính đánh thuê nên không thể dùng quà tân thủ.");
            return;
        }
        ServiceMenuHolder holder = createServiceMenu(player, MenuType.WELCOME_GIFT, null, null, null,
            0, 54, "Quà Tân Thủ");
        List<String> classIds = getClassIds();
        for (int index = 0; index < classIds.size() && index < 45; index++) {
            String classId = classIds.get(index);
            Material icon = Material.matchMaterial(settings.getString("classes." + classId + ".menu-icon", "IRON_SWORD"));
            if (icon == null || icon.isAir()) icon = Material.IRON_SWORD;
            holder.choices.put(index + 1, classId);
            holder.inventory.setItem(index + 1, menuItem(icon, "§e" + classDisplay(classId),
                List.of("§7Nhận token triệu hồi lính Bậc 1.", "§8Nhấn để chọn.")));
        }
        holder.inventory.setItem(49, menuItem(Material.ARROW, "§7Đóng", List.of("§8Quay lại chơi.")));
        player.openInventory(holder.inventory);
    }

    private void selectWelcomeGift(Player player, String classId) {
        if (isWelcomeGiftClaimed(player.getUniqueId())) {
            player.sendMessage("§cBạn đã nhận quà tân thủ rồi.");
            player.closeInventory();
            return;
        }
        if (hasOwnedMercenaries(player.getUniqueId())) {
            player.sendMessage("§cBạn đã sở hữu lính đánh thuê nên không thể dùng quà tân thủ.");
            player.closeInventory();
            return;
        }
        if (classId == null || normalizeClass(classId) == null) {
            player.sendMessage("§cLoại token không hợp lệ.");
            player.closeInventory();
            return;
        }
        if (!consumeWelcomeGiftBag(player)) {
            player.sendMessage("§cKhông tìm thấy túi quà tân thủ trong túi đồ của bạn.");
            player.closeInventory();
            return;
        }
        giveItem(player, createToken(classId, 1));
        giveItem(player, new ItemStack(Material.EMERALD, 8));
        claimedWelcomeGiftPlayers.add(player.getUniqueId());
        saveWelcomeGiftClaims();
        player.closeInventory();
        player.sendMessage("§aBạn đã nhận §e" + classDisplay(classId) + " §aBậc 1, thêm §e8 viên lục bảo §a, và túi quà tân thủ đã biến mất.");
    }

    private boolean isGearOpen(Player player, UUID unitId) {
        InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
        return holder instanceof GearHolder gearHolder && gearHolder.unit.equals(unitId);
    }

   private void captureGearInventory(Player player, MercenaryManager.Unit unit, Inventory inventory) {
      EnumMap<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
      List<ItemStack> stored = new ArrayList<>();

      for (int slot = 0; slot < inventory.getSize(); slot++) {
         if (slot != 53) {
            ItemStack item = inventory.getItem(slot);
            if (!this.isGearDecorationSlot(slot) || !this.isGearDecoration(item)) {
               if (this.isHorseArmor(item)) {
                  stored.add(item.clone());
               } else {
                  EquipmentSlot equipmentSlot = switch (slot) {
                     case 45 -> EquipmentSlot.HAND;
                     case 46 -> EquipmentSlot.HEAD;
                     case 47 -> EquipmentSlot.CHEST;
                     case 48 -> EquipmentSlot.LEGS;
                     case 49 -> EquipmentSlot.FEET;
                     case 50 -> EquipmentSlot.OFF_HAND;
                     default -> null;
                  };
                  if (equipmentSlot != null) {
                     if (item != null && !item.getType().isAir()) {
                        if (this.validSoldierGear(item, equipmentSlot)) {
                           equipment.put(equipmentSlot, item.clone());
                        } else {
                           stored.add(item.clone());
                        }
                     }
                  } else {
                     this.addGearItem(stored, item);
                  }
               }
            }
         }
      }

      unit.horseArmor = null;

      for (EquipmentSlot slot : List.of(
         EquipmentSlot.HAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFF_HAND
      )) {
         unit.equipment.put(slot, cloneOrNull(equipment.get(slot)));
      }

      Arrays.fill(unit.inventory, null);

      for (int index = 0; index < Math.min(unit.inventory.length, stored.size()); index++) {
         unit.inventory[index] = stored.get(index).clone();
      }

      for (int index = unit.inventory.length; index < stored.size(); index++) {
         Map<Integer, ItemStack> overflow = player.getInventory().addItem(new ItemStack[]{stored.get(index).clone()});
         overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
         player.sendMessage("§eKho lính đầy; vật phẩm vượt giới hạn đã trả lại cho bạn.");
      }
   }

    private boolean isGearDecorationSlot(int slot) {
        return (slot >= 36 && slot < 45) || slot >= 52;
    }

    private boolean isGearDecoration(ItemStack item) {
        return item != null && item.getType() == Material.GRAY_STAINED_GLASS_PANE
            && item.hasItemMeta() && " ".equals(item.getItemMeta().getDisplayName());
    }

   private void addGearItem(List<ItemStack> stored, ItemStack item) {
      if (item != null && !item.getType().isAir()) {
         stored.add(item.clone());
      }
   }

    private org.bukkit.inventory.EquipmentSlot detectedEquipmentSlot(Material material) {
        String name = material.name();
        if (material == Material.SPLASH_POTION || material == Material.LINGERING_POTION
            || material == Material.SHIELD) return org.bukkit.inventory.EquipmentSlot.OFF_HAND;
        if (name.endsWith("_HELMET") || material == Material.TURTLE_HELMET
                || material == Material.CARVED_PUMPKIN) return org.bukkit.inventory.EquipmentSlot.HEAD;
        if (name.endsWith("_CHESTPLATE") || material == Material.ELYTRA) return org.bukkit.inventory.EquipmentSlot.CHEST;
        if (name.endsWith("_LEGGINGS")) return org.bukkit.inventory.EquipmentSlot.LEGS;
        if (name.endsWith("_BOOTS")) return org.bukkit.inventory.EquipmentSlot.FEET;
        if (material == Material.EMERALD || material == Material.POTION) {
            return org.bukkit.inventory.EquipmentSlot.HAND;
        }
        if (name.endsWith("_SWORD") || name.endsWith("_AXE") || name.equals("BOW")
                || name.equals("CROSSBOW") || name.equals("TRIDENT") || name.endsWith("_SPEAR")
            || name.equals("MACE") || name.equals("BLAZE_ROD") || name.equals("SNOWBALL")
            || name.equals("FIRE_CHARGE")) {
            return org.bukkit.inventory.EquipmentSlot.HAND;
        }
        return null;
    }

   private boolean handleSoldierGearCommand(CommandSender sender, String[] args) {
      if (!sender.hasPermission("vnmine.command.army.admin")) {
         sender.sendMessage("§cBạn không có quyền cấp trang bị dành cho lính.");
         return true;
      }

      if (args.length == 2 && args[1].equalsIgnoreCase("give")) {
         if (sender instanceof Player player) {
            this.openAdminItemPlayers(player, 0);
            return true;
         } else {
            sender.sendMessage("§cConsole cần dùng đủ tham số: /vnarmy item give <người chơi> <class> <vật-liệu> <sao> <set> <món|set>.");
            return true;
         }
      } else if (args.length == 8 && args[1].equalsIgnoreCase("give")) {
         Player target = Bukkit.getPlayerExact(args[2]);
         String classId = this.normalizeClass(args[3]);
         String material = this.normalizeGearMaterial(args[4]);
         int stars = parsePositiveInt(args[5]);
         String setId = this.normalizeGearSet(args[6]);
         String itemSlot = args[7].toLowerCase(Locale.ROOT);
         if (target != null
            && classId != null
            && material != null
            && stars >= 1
            && stars <= 5
            && setId != null
            && this.gearSetIds(classId).contains(setId)
            && (itemSlot.equals("set") || this.parseGearSlot(itemSlot) != null)) {
            this.giveSoldierGear(target, classId, material, stars, setId, itemSlot);
            sender.sendMessage(
               "§aĐã cấp "
                  + (itemSlot.equals("set") ? "trọn set" : "món trang bị")
                  + " §e"
                  + this.classDisplay(classId)
                  + " "
                  + this.gearMaterialDisplay(material)
                  + " "
                  + "★".repeat(stars)
                  + " §acho §e"
                  + target.getName()
                  + "§a."
            );
            target.sendMessage(
               "§aBạn nhận được "
                  + (itemSlot.equals("set") ? "bộ trang bị lính" : "trang bị lính")
                  + " §e"
                  + this.classDisplay(classId)
                  + " "
                  + this.gearMaterialDisplay(material)
                  + " "
                  + "★".repeat(stars)
                  + "§a. Chỉ lính mới có thể trang bị vật phẩm này."
            );
            return true;
         } else {
            sender.sendMessage("§cNgười chơi phải online và class, vật liệu, sao, set, slot phải hợp lệ.");
            return true;
         }
      } else {
         sender.sendMessage(
            "§eSử dụng: /vnarmy item give <người chơi> <class> <da|đồng|sắt|vàng|kim-cương|netherite> <sao 1-5> <set> <helmet|chestplate|leggings|boots|mainhand|offhand|set>"
         );
         return true;
      }
   }

   private void giveSoldierGear(Player target, String classId, String material, int stars, String setId, String itemSlot) {
      if (itemSlot.equals("set")) {
         for (EquipmentSlot slot : soldierGearSlots(classId)) {
            this.giveItem(target, this.createSoldierGearItem(classId, material, stars, setId, slot));
         }
      } else {
         EquipmentSlot slot = this.parseGearSlot(itemSlot);
         if (slot == null || !soldierGearSlots(classId).contains(slot)) {
            throw new IllegalArgumentException("Unsupported mercenary gear slot.");
         }

         this.giveItem(target, this.createSoldierGearItem(classId, material, stars, setId, slot));
      }
   }

   private String normalizeGearMaterial(String input) {
      if (input == null) {
         return null;
      }

      String normalized = input.toLowerCase(Locale.ROOT).replace('-', '_');
      return GEAR_MATERIALS.contains(normalized) ? normalized : null;
   }

   private String normalizeGearSet(String input) {
      return input != null && input.matches("[a-z0-9_-]{1,32}") ? input.toLowerCase(Locale.ROOT) : null;
   }

   private EquipmentSlot parseGearSlot(String value) {
      return switch (value.toLowerCase(Locale.ROOT)) {
         case "helmet", "head" -> EquipmentSlot.HEAD;
         case "chestplate", "chest" -> EquipmentSlot.CHEST;
         case "leggings", "legs" -> EquipmentSlot.LEGS;
         case "boots", "feet" -> EquipmentSlot.FEET;
         case "mainhand", "hand" -> EquipmentSlot.HAND;
         case "offhand", "off_hand" -> EquipmentSlot.OFF_HAND;
         default -> null;
      };
   }

   private List<String> gearSetIds(String classId) {
      ConfigurationSection section = this.settings.getConfigurationSection("gear-system.sets." + classId);
      return section != null && !section.getKeys(false).isEmpty()
         ? section.getKeys(false).stream().filter(key -> key.matches("[a-z0-9_-]{1,32}")).sorted(String.CASE_INSENSITIVE_ORDER).toList()
         : List.of("standard");
   }

   private String gearMaterialDisplay(String material) {
      return switch (material) {
         case "leather" -> "Da";
         case "copper" -> "Đồng";
         case "iron" -> "Sắt";
         case "gold" -> "Vàng";
         case "diamond" -> "Kim cương";
         case "netherite" -> "Netherite";
         default -> material;
      };
   }

    private boolean handleTokenCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cBạn không có quyền cấp lệnh triệu hồi.");
            return true;
        }
        if (args.length != 5 || !args[1].equalsIgnoreCase("give")) {
            sender.sendMessage("§eSử dụng: /vnarmy token give <người chơi> <loại> <tier>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        String classId = normalizeClass(args[3]);
        int level = parsePositiveInt(args[4]);
        if (target == null || classId == null || !hasTier(level)) {
            sender.sendMessage("§cNgười chơi phải đang online; lớp/tier lính không hợp lệ.");
            return true;
        }
        ItemStack token = createToken(classId, level);
        Map<Integer, ItemStack> overflow = target.getInventory().addItem(token);
        overflow.values().forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));
        sender.sendMessage("§aĐã cấp lệnh triệu hồi " + classDisplay(classId) + " tier " + level
            + " cho §e" + target.getName() + "§a.");
        target.sendMessage("§aBạn nhận được Lệnh Triệu Hồi " + classDisplay(classId) + " tier " + level + "§a.");
        return true;
    }

    private boolean handleGiveCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cBạn không có quyền cấp token hoặc lính.");
            return true;
        }
        if (args.length != 4 && args.length != 5) {
            sender.sendMessage("§eDùng: /vnarmy give <người chơi> <token> <tier>");
            sender.sendMessage("§eHoặc: /vnarmy give <người chơi> <army> <loại> <tier>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§cNgười chơi phải đang online.");
            return true;
        }
        if (args.length == 4 && args[2].equalsIgnoreCase("token")) {
            int level = parsePositiveInt(args[3]);
            if (!hasTier(level)) {
                sender.sendMessage("§cTier token không hợp lệ.");
                return true;
            }
            giveItem(target, createToken("any", level));
            sender.sendMessage("§aĐã cấp token triệu hồi tier " + level + " cho §e" + target.getName() + "§a.");
            target.sendMessage("§aBạn nhận được Lệnh Triệu Hồi tier " + level + "§a.");
            return true;
        }
        if (args.length == 5 && args[2].equalsIgnoreCase("army")) {
            String classId = normalizeClass(args[3]);
            int level = parsePositiveInt(args[4]);
            if (classId == null || !hasTier(level)) {
                sender.sendMessage("§cLớp lính hoặc tier không hợp lệ.");
                return true;
            }
            if (getOwnerUnits(target.getUniqueId()).size() >= maxUnits) {
                sender.sendMessage("§cNgười chơi đã đạt giới hạn " + maxUnits + " lính.");
                return true;
            }
            boolean deployImmediately = deployedUnitCount(target.getUniqueId()) < maxDeployedUnits;
            Unit unit = new Unit(UUID.randomUUID(), target.getUniqueId(), classId, level,
                Mode.PROTECT, deployImmediately);
            Mob mob = deployImmediately ? spawnUnit(unit, safeLocation(target.getLocation())) : null;
            if (deployImmediately && mob == null) {
                sender.sendMessage("§cKhông thể tạo lính; kiểm tra cấu hình entity.");
                return true;
            }
            units.put(unit.id, unit);
            if (mob != null) {
                activeEntities.put(unit.id, mob);
                updateLocation(unit, mob.getLocation());
            }
            if (!saveData()) {
                units.remove(unit.id);
                if (mob != null) {
                    activeEntities.remove(unit.id);
                    mob.remove();
                }
                removeUnitData(unit);
                saveData();
                sender.sendMessage("§cKhông thể lưu lính; chưa hoàn tất việc cấp.");
                return true;
            }
            String deploymentMessage = deployImmediately ? "" : " (đang nghỉ do đã đủ giới hạn triệu hồi)";
            sender.sendMessage("§aĐã cấp " + displayName(unit) + " tier " + level + " cho §e"
                + target.getName() + "§a" + deploymentMessage + ".");
            target.sendMessage("§aBạn được cấp " + displayName(unit) + " tier " + level
                + "§a" + deploymentMessage + ".");
            return true;
        }
        sender.sendMessage("§eDùng: /vnarmy give <người chơi> <token> <tier>");
        sender.sendMessage("§eHoặc: /vnarmy give <người chơi> <army> <loại> <tier>");
        return true;
    }

    private void giveItem(Player target, ItemStack item) {
        Map<Integer, ItemStack> overflow = target.getInventory().addItem(item);
        overflow.values().forEach(stack -> target.getWorld().dropItemNaturally(target.getLocation(), stack));
    }

    private ItemStack createToken(String classId, int level) {
        ItemStack token = new ItemStack(Material.PAPER);
        ItemMeta meta = token.getItemMeta();
        String display = classId.equals("any") ? "Lính Đánh Thuê" : classDisplay(classId);
        meta.setDisplayName("§6§lLệnh Triệu Hồi " + display + " §fTier " + level);
        meta.setLore(List.of("§7Thuê lính tier " + level + ".", "§8Lệnh sẽ bị tiêu thụ khi dùng."));
        meta.getPersistentDataContainer().set(tokenClassKey, PersistentDataType.STRING, classId);
        meta.getPersistentDataContainer().set(tokenLevelKey, PersistentDataType.INTEGER, level);
        token.setItemMeta(meta);
        return token;
    }

    public ItemStack createBossSummonToken(String classId, int level) {
        String normalizedClass = normalizeClass(classId);
        return normalizedClass == null || !hasTier(level) ? null : createToken(normalizedClass, level);
    }

    private ItemStack findToken(Player player, String classId, int level) {
        ItemStack generic = null;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType() != Material.PAPER || !stack.hasItemMeta()) continue;
            PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
            if (!Integer.valueOf(level).equals(pdc.get(tokenLevelKey, PersistentDataType.INTEGER))) continue;
            String tokenClass = pdc.get(tokenClassKey, PersistentDataType.STRING);
            if (classId.equals(tokenClass)) return stack;
            if ("any".equals(tokenClass)) generic = stack;
        }
        return generic;
    }

   private Mob spawnUnit(MercenaryManager.Unit unit, Location location) {
      EntityType type = this.configuredEntityType(unit);
      if (type != null && type.isAlive()) {
         String configuredType = this.settings.getString("classes." + unit.unitClass + ".entity-type", "");
         if ("berserker".equals(unit.unitClass) && "PIGLIN_BRUTE".equalsIgnoreCase(configuredType)) {
            this.plugin.getLogger().warning("[Army] Refusing to spawn PIGLIN_BRUTE for berserker; using ZOMBIFIED_PIGLIN.");
         }

         Entity entity;
         try {
            entity = location.getWorld().spawnEntity(location, type);
         } catch (IllegalArgumentException exception) {
            this.plugin.getLogger().log(Level.WARNING, "[Army] Could not spawn class " + unit.unitClass + ".", exception);
            return null;
         }

         if (entity instanceof Mob mob) {
            if (mob instanceof Villager) {
               mob.setAware(true);
               mob.setTarget(null);
            }

            PersistentDataContainer pdc = mob.getPersistentDataContainer();
            pdc.set(this.unitKey, PersistentDataType.STRING, unit.id.toString());
            pdc.set(this.ownerKey, PersistentDataType.STRING, unit.owner.toString());
            this.preventZombification(mob);
            if ("paladin".equals(unit.unitClass)) {
               Horse horse = this.getOrCreateUnitHorse(unit, location);
               if (horse != null) {
                  horse.addPassenger(mob);
               }
            }

            mob.setPersistent(true);
            mob.setRemoveWhenFarAway(false);
            mob.setCanPickupItems(false);
            mob.setAware(true);
            mob.setTarget(null);
            this.updateEntityName(unit, mob);
            this.applyEquipment(unit, mob);
            this.applyStats(unit, mob);
            unit.entityId = mob.getUniqueId();
            this.updateLocation(unit, mob.getLocation());
            return mob;
         } else {
            entity.remove();
            this.plugin.getLogger().warning("[Army] Configured entity type is not a Mob: " + type.name());
            return null;
         }
      } else {
         return null;
      }
   }

    private EntityType configuredEntityType(Unit unit) {
        EntityType configured = EntityType.fromName(
            settings.getString("classes." + unit.unitClass + ".entity-type", ""));
        if ("berserker".equals(unit.unitClass) && configured == EntityType.PIGLIN_BRUTE) {
            return EntityType.ZOMBIFIED_PIGLIN;
        }
        return configured;
    }

    private void removeUnskilledVexes(Mob mob) {
        if (!(mob instanceof Evoker)) return;
        for (Entity nearby : mob.getNearbyEntities(24.0, 24.0, 24.0)) {
            if (nearby instanceof Vex vex && vex.getSummoner() == mob && !isSummonedMinion(vex)) {
                vex.remove();
            }
        }
    }

    private void cleanupSummonedMinions(UUID unitId) {
        Set<UUID> minionIds = summonedMinions.remove(unitId);
        if (minionIds == null) return;
        for (UUID minionId : minionIds) {
            removeStatusDisplay(minionId);
            Entity minion = Bukkit.getEntity(minionId);
            if (minion != null && minion.isValid()) minion.remove();
        }
    }

    private GuardianStats guardianStats(Unit unit) {
        double tierProgress = Math.max(0, unit.tier - 1);
        double levelProgress = Math.max(0, unit.level - 1);
        double baseHealth = settings.getDouble("classes.guardian.base-health", 32.0);
        double tierHealth = tierProgress
            * readBoundedDouble(settings, "classes.guardian.health-per-tier", 6.0, 0.0, 100.0);
        double levelHealth = levelProgress
            * readBoundedDouble(settings, "classes.guardian.health-per-level", 5.0, 0.0, 100.0);
        double equipmentHealth = isGuardianHoldingOffhandShield(unit)
            ? readBoundedDouble(settings,
                "classes.guardian.shield-offhand-health-bonus", 8.0, 0.0, 100.0)
            : 0.0;
        double baseArmor = settings.getDouble("classes.guardian.base-armor", 0.0);
        double tierArmor = tierProgress
            * readBoundedDouble(settings, "classes.guardian.armor-per-tier", 0.5, 0.0, 10.0);
        int armorStepLevels = readBoundedInt(settings,
            "classes.guardian.armor-level-step-levels", 5, 1, 30);
        double levelArmor = unit.level / (double) armorStepLevels
            * readBoundedDouble(settings, "classes.guardian.armor-per-level-step", 0.5, 0.0, 10.0);
        double baseDamage = settings.getDouble("classes.guardian.base-damage", 7.0);
        double tierDamage = tierProgress
            * readBoundedDouble(settings, "classes.guardian.damage-per-tier", 0.75, 0.0, 10.0);
        double levelDamage = levelProgress
            * readBoundedDouble(settings, "classes.guardian.damage-per-level", 0.75, 0.0, 10.0);
        return new GuardianStats(
            baseHealth, tierHealth, levelHealth, equipmentHealth,
            Math.min(1024.0, Math.max(1.0, baseHealth + tierHealth + levelHealth + equipmentHealth)),
            baseArmor, tierArmor, levelArmor, Math.min(100.0, baseArmor + tierArmor + levelArmor),
            baseDamage, tierDamage, levelDamage,
            Math.max(0.0, baseDamage + tierDamage + levelDamage));
    }

    private boolean isGuardianHoldingOffhandShield(Unit unit) {
        ItemStack offhand = displayedOffhand(unit);
        return offhand != null && offhand.getType() == Material.SHIELD;
    }

    private void applyStats(Unit unit, Mob mob) {
        applyStats(unit, mob, true);
    }

   private void applyStats(MercenaryManager.Unit unit, Mob mob, boolean refillHealth) {
      double baseHealth = this.settings.getDouble("classes." + unit.unitClass + ".base-health", 24.0);
      double healthPerLevel = this.settings.getDouble("classes." + unit.unitClass + ".health-per-level", 4.0);
      int statRank = this.statRank(unit);
      double oldMaximumHealth = maxHealth(mob);
      double oldHealth = mob.getHealth();
      double maxHealth;
      double damage;
      double armor;
      if ("guardian".equals(unit.unitClass)) {
         MercenaryManager.GuardianStats stats = this.guardianStats(unit);
         maxHealth = stats.maxHealth();
         damage = stats.damage();
         armor = stats.armor();
      } else {
         maxHealth = Math.max(1.0, baseHealth + Math.max(0, statRank - 1) * healthPerLevel);
         double baseDamage = this.settings.getDouble("classes." + unit.unitClass + ".base-damage", 4.0);
         double damagePerLevel = this.settings.getDouble("classes." + unit.unitClass + ".damage-per-level", 0.5);
         damage = Math.max(0.0, baseDamage + Math.max(0, statRank - 1) * damagePerLevel);
         double baseArmor = this.settings.getDouble("classes." + unit.unitClass + ".base-armor", 0.0);
         double armorPerLevel = this.settings.getDouble("classes." + unit.unitClass + ".armor-per-level", 0.0);
         armor = Math.max(0.0, baseArmor + Math.max(0, statRank - 1) * armorPerLevel);
      }

      MercenaryManager.GearStats gear = this.soldierGearStats(unit);
      maxHealth = Math.min(1024.0, maxHealth + gear.health());
      armor = Math.min(100.0, armor + gear.armor());
      damage = Math.max(0.0, damage * (1.0 + gear.damagePercent() / 100.0));
      if (mob.getAttribute(Attribute.MAX_HEALTH) != null) {
         mob.getAttribute(Attribute.MAX_HEALTH).setBaseValue(maxHealth);
         double updatedHealth = !refillHealth && !(oldMaximumHealth <= 0.0) ? Math.min(maxHealth, oldHealth * maxHealth / oldMaximumHealth) : maxHealth;
         mob.setHealth(Math.max(0.0, updatedHealth));
      }

      if (mob.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
         mob.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(damage);
      }

      if (mob.getAttribute(Attribute.ARMOR) != null) {
         mob.getAttribute(Attribute.ARMOR).setBaseValue(armor);
      }
   }

   private void applyEquipment(MercenaryManager.Unit unit, Mob mob) {
      EntityEquipment equipment = mob.getEquipment();
      if (equipment != null) {
         this.sanitizeUnitGear(unit);

         for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            ItemStack current = unit.equipment.get(slot);
            ItemStack selected = this.selectBestSoldierGear(slot, current);
            if (selected != null) {
               unit.equipment.put(slot, selected);
            } else if (current == null || this.validSoldierGear(current, slot)) {
               unit.equipment.remove(slot);
            }
         }

         ItemStack currentMainHand = unit.equipment.get(EquipmentSlot.HAND);
         ItemStack mainHand = this.selectBestSoldierGear(EquipmentSlot.HAND, currentMainHand);
         if (mainHand != null) {
            unit.equipment.put(EquipmentSlot.HAND, mainHand.clone());
         } else if (currentMainHand == null || this.validSoldierGear(currentMainHand, EquipmentSlot.HAND)) {
            unit.equipment.remove(EquipmentSlot.HAND);
         }

         if (mainHand == null && !unit.gearCustomized) {
            mainHand = this.configuredStartingWeapon(unit);
         }

         ItemStack currentOffHand = unit.equipment.get(EquipmentSlot.OFF_HAND);
         ItemStack offHand = this.selectBestSoldierGear(EquipmentSlot.OFF_HAND, currentOffHand);
         if (offHand != null) {
            unit.equipment.put(EquipmentSlot.OFF_HAND, offHand.clone());
         } else if (currentOffHand == null || this.validSoldierGear(currentOffHand, EquipmentSlot.OFF_HAND)) {
            unit.equipment.remove(EquipmentSlot.OFF_HAND);
         }

         if (offHand == null && !unit.gearCustomized) {
            offHand = this.configuredStartingOffhand(unit);
         }

         equipment.setItemInMainHand(mainHand);
         equipment.setHelmet(
            this.validSoldierGear(unit.equipment.get(EquipmentSlot.HEAD), EquipmentSlot.HEAD) ? cloneOrNull(unit.equipment.get(EquipmentSlot.HEAD)) : null
         );
         equipment.setChestplate(
            this.validSoldierGear(unit.equipment.get(EquipmentSlot.CHEST), EquipmentSlot.CHEST) ? cloneOrNull(unit.equipment.get(EquipmentSlot.CHEST)) : null
         );
         equipment.setLeggings(
            this.validSoldierGear(unit.equipment.get(EquipmentSlot.LEGS), EquipmentSlot.LEGS) ? cloneOrNull(unit.equipment.get(EquipmentSlot.LEGS)) : null
         );
         equipment.setBoots(
            this.validSoldierGear(unit.equipment.get(EquipmentSlot.FEET), EquipmentSlot.FEET) ? cloneOrNull(unit.equipment.get(EquipmentSlot.FEET)) : null
         );
         equipment.setItemInOffHand(offHand);
         equipment.setItemInMainHandDropChance(0.0F);
         equipment.setHelmetDropChance(0.0F);
         equipment.setChestplateDropChance(0.0F);
         equipment.setLeggingsDropChance(0.0F);
         equipment.setBootsDropChance(0.0F);
         equipment.setItemInOffHandDropChance(0.0F);
      }
   }

   private ItemStack configuredStartingWeapon(MercenaryManager.Unit unit) {
      String configuredWeapon = this.settings.getString("classes." + unit.unitClass + ".starting-weapon", "");
      Material material = Material.matchMaterial(configuredWeapon);
      if (material == null && "paladin".equals(unit.unitClass) && "IRON_SPEAR".equalsIgnoreCase(configuredWeapon)) {
         material = Material.TRIDENT;
      }

      if (material == null && "paladin".equals(unit.unitClass) && "WOODEN_SPEAR".equalsIgnoreCase(configuredWeapon)) {
         material = Material.STICK;
      }

      return material != null && !material.isAir() ? this.markNativeSoldierGear(new ItemStack(material), unit.unitClass, EquipmentSlot.HAND) : null;
   }

   private ItemStack selectBestSoldierGear(EquipmentSlot slot, ItemStack current) {
      return this.validSoldierGear(current, slot) ? current.clone() : null;
   }

   private ItemStack configuredStartingOffhand(MercenaryManager.Unit unit) {
      Material material = Material.matchMaterial(this.settings.getString("classes." + unit.unitClass + ".starting-offhand", "AIR"));
      return material != null && !material.isAir() ? this.markNativeSoldierGear(new ItemStack(material), unit.unitClass, EquipmentSlot.OFF_HAND) : null;
   }

    private boolean isWeaponItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        String material = item.getType().name();
        if (item.getType() == Material.EMERALD || item.getType() == Material.POTION) return false;
        return detectedEquipmentSlot(item.getType()) == org.bukkit.inventory.EquipmentSlot.HAND
            || material.endsWith("_HOE") || material.equals("STICK")
            || material.equals("BLAZE_ROD") || material.equals("SNOWBALL")
            || material.equals("FIRE_CHARGE");
    }

    private boolean hasMainHandWeapon(Mob mob) {
        EntityEquipment equipment = mob.getEquipment();
        return equipment != null && isWeaponItem(equipment.getItemInMainHand());
    }

    private boolean storeInUnitInventory(Unit unit, ItemStack item) {
        for (int slot = 0; slot < unit.inventory.length; slot++) {
            if (unit.inventory[slot] == null || unit.inventory[slot].getType().isAir()) {
                unit.inventory[slot] = item.clone();
                return true;
            }
        }
        return false;
    }

    private void updateEntityName(Unit unit, Mob mob) {
        String ownerName = Bukkit.getOfflinePlayer(unit.owner).getName();
        if (ownerName == null || ownerName.isBlank()) ownerName = "Người chơi";
        mob.setCustomName(EconomyManager.sanitizeVisibleName("§b§l[" + ownerName + "]§r §f" + displayName(unit)
            + " §eBậc " + unit.tier + " §fCấp " + unit.level));
        mob.setCustomNameVisible(true);
        namePacketListener.update(mob, unit.owner, ownerName,
            EconomyManager.sanitizeVisibleName(displayName(unit)), unit.tier, unit.level);
    }

    private void updateStatusDisplay(Unit unit, Mob mob) {
        if (!statusDisplayEnabled) {
            statusPacketListener.remove(unit.id);
            return;
        }
        double maximumHealth = maxHealth(mob);
        double requiredExperience = nextLevelExperience(unit);
        int filledHealthBars = statusBarFilled(mob.getHealth(), maximumHealth);
        String healthBar = "§c" + "■".repeat(filledHealthBars)
            + "§7" + "□".repeat(statusDisplayBarLength - filledHealthBars);
        String statusLine = "§c❤: §f" + formatStat(mob.getHealth()) + "/" + formatStat(maximumHealth)
            + " §e✦ EXP: " + unit.experience + "/" + (int) requiredExperience;
        String ownerName = Bukkit.getOfflinePlayer(unit.owner).getName();
        if (ownerName == null || ownerName.isBlank()) ownerName = "Người chơi";
        String nameLine = "§a[" + ownerName + "] §f" + displayName(unit)
            + " §e[Bậc " + unit.tier + "] §f[Level: " + unit.level + "]";
        statusPacketListener.update(unit.id, mob, unit.owner,
            healthBar + "\n" + statusLine + "\n" + nameLine,
            statusDisplaySeeThrough);
    }

    private int statusBarFilled(double value, double maximum) {
        double progress = maximum <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, value / maximum));
        return (int) Math.round(progress * statusDisplayBarLength);
    }

    private void removeStatusDisplay(UUID unitId) {
        statusPacketListener.remove(unitId);
    }

   private void captureEquipment(MercenaryManager.Unit unit, Mob mob) {
      EntityEquipment equipment = mob.getEquipment();
      if (equipment != null) {
         if (mob instanceof PiglinAbstract) {
            this.reconcileEquipment(unit, mob, false);
         } else {
            unit.equipment.put(EquipmentSlot.HAND, cloneOrNull(equipment.getItemInMainHand()));
            unit.equipment.put(EquipmentSlot.HEAD, cloneOrNull(equipment.getHelmet()));
            unit.equipment.put(EquipmentSlot.CHEST, cloneOrNull(equipment.getChestplate()));
            unit.equipment.put(EquipmentSlot.LEGS, cloneOrNull(equipment.getLeggings()));
            unit.equipment.put(EquipmentSlot.FEET, cloneOrNull(equipment.getBoots()));
            unit.equipment.put(EquipmentSlot.OFF_HAND, cloneOrNull(equipment.getItemInOffHand()));
         }

         this.sanitizeUnitGear(unit);
         equipment.setItemInMainHand(
            this.validSoldierGear(unit.equipment.get(EquipmentSlot.HAND), EquipmentSlot.HAND) ? cloneOrNull(unit.equipment.get(EquipmentSlot.HAND)) : null
         );
         equipment.setHelmet(
            this.validSoldierGear(unit.equipment.get(EquipmentSlot.HEAD), EquipmentSlot.HEAD) ? cloneOrNull(unit.equipment.get(EquipmentSlot.HEAD)) : null
         );
         equipment.setChestplate(
            this.validSoldierGear(unit.equipment.get(EquipmentSlot.CHEST), EquipmentSlot.CHEST) ? cloneOrNull(unit.equipment.get(EquipmentSlot.CHEST)) : null
         );
         equipment.setLeggings(
            this.validSoldierGear(unit.equipment.get(EquipmentSlot.LEGS), EquipmentSlot.LEGS) ? cloneOrNull(unit.equipment.get(EquipmentSlot.LEGS)) : null
         );
         equipment.setBoots(
            this.validSoldierGear(unit.equipment.get(EquipmentSlot.FEET), EquipmentSlot.FEET) ? cloneOrNull(unit.equipment.get(EquipmentSlot.FEET)) : null
         );
         equipment.setItemInOffHand(
            this.validSoldierGear(unit.equipment.get(EquipmentSlot.OFF_HAND), EquipmentSlot.OFF_HAND)
               ? cloneOrNull(unit.equipment.get(EquipmentSlot.OFF_HAND))
               : null
         );
         if ((this.mountByUnit.containsKey(unit.id) ? Bukkit.getEntity(this.mountByUnit.get(unit.id)) : null) instanceof Horse horse && horse.isValid()) {
            this.captureHorseArmor(unit.id, horse);
         }
      }
   }

    private Mob findExistingEntity(Unit unit) {
        if (unit.entityId != null) {
            Entity entity = Bukkit.getEntity(unit.entityId);
            if (entity instanceof Mob mob && isUnit(mob, unit.id)) {
                preventZombification(mob);
                activeEntities.put(unit.id, mob);
                repairEquipmentFromSaved(unit, mob);
                updateEntityName(unit, mob);
                return mob;
            }
        }
        World world = Bukkit.getWorld(unit.worldName == null ? "" : unit.worldName);
        if (world == null) return null;
        int centerX = ((int) Math.floor(unit.x)) >> 4;
        int centerZ = ((int) Math.floor(unit.z)) >> 4;
        List<org.bukkit.Chunk> newlyLoaded = new ArrayList<>();
        Set<UUID> seenEntities = new HashSet<>();
        Mob found = null;
        try {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int chunkX = centerX + dx;
                    int chunkZ = centerZ + dz;
                    if (!world.isChunkGenerated(chunkX, chunkZ)) continue;
                    boolean wasLoaded = world.isChunkLoaded(chunkX, chunkZ);
                    org.bukkit.Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                    if (!wasLoaded) newlyLoaded.add(chunk);
                    for (Entity entity : chunk.getEntities()) {
                        if (!(entity instanceof Mob mob) || !isUnit(mob, unit.id)) continue;
                        if (seenEntities.add(entity.getUniqueId()) && found == null) found = mob;
                        else entity.remove();
                    }
                }
            }
        } finally {
            for (org.bukkit.Chunk chunk : newlyLoaded) world.unloadChunkRequest(chunk.getX(), chunk.getZ());
        }
        if (found != null) {
            unit.entityId = found.getUniqueId();
            activeEntities.put(unit.id, found);
            preventZombification(found);
            updateEntityName(unit, found);
            repairEquipmentFromSaved(unit, found);
            return found;
        }
        return null;
    }

    private boolean isUnit(Entity mob, UUID unitId) {
        return unitId.toString().equals(
            mob.getPersistentDataContainer().get(unitKey, PersistentDataType.STRING));
    }

    private boolean isMercenary(Entity entity) {
        return entity.getPersistentDataContainer().has(unitKey, PersistentDataType.STRING)
            || isSummonedMinion(entity);
    }

    private void preventZombification(Mob mob) {
        if (mob instanceof PiglinAbstract piglin) piglin.setImmuneToZombification(true);
    }

    private boolean isMercenaryMount(Entity entity) {
        if (!(entity instanceof Horse)) return false;
        UUID entityId = entity.getUniqueId();
        return entity.getPersistentDataContainer().has(horseMountKey, PersistentDataType.STRING)
            || mountByUnit.containsValue(entityId) || activeEntities.values().stream()
            .anyMatch(rider -> rider.isValid() && isMercenary(rider) && rider.getVehicle() == entity);
    }

    private boolean isSummonedMinion(Entity entity) {
        return entity.getPersistentDataContainer().has(summonedMinionKey, PersistentDataType.STRING);
    }

    private Location safeLocation(Location origin) {
        World world = origin.getWorld();
        if (world == null) return origin;
        for (int radius = 0; radius <= 3; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int x = origin.getBlockX() + dx;
                    int z = origin.getBlockZ() + dz;
                    int y = world.getHighestBlockYAt(x, z) + 1;
                    if (y >= world.getMaxHeight() - 1) continue;
                    Location candidate = new Location(world, x + 0.5, y, z + 0.5, origin.getYaw(), origin.getPitch());
                    if (candidate.getBlock().isPassable()
                            && candidate.clone().add(0, 1, 0).getBlock().isPassable()
                            && candidate.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) return candidate;
                }
            }
        }
        return origin.clone().add(1.5, 0, 1.5);
    }

    private void updateLocation(Unit unit, Location location) {
        if (location.getWorld() == null) return;
        unit.worldName = location.getWorld().getName();
        unit.x = location.getX();
        unit.y = location.getY();
        unit.z = location.getZ();
    }

    private void saveUnit(Unit unit) {
        String path = "units." + unit.id;
        data.set(path + ".owner", unit.owner.toString());
        data.set(path + ".class", unit.unitClass);
        data.set(path + ".tier", unit.tier);
        data.set(path + ".original-tier", unit.originalTier);
        data.set(path + ".level", unit.level);
        data.set(path + ".experience", unit.experience);
        data.set(path + ".custom-name", unit.customName);
        data.set(path + ".recovery-until", unit.recoveryUntilMillis);
        data.set(path + ".gear-customized", unit.gearCustomized);
        data.set(path + ".mode", unit.mode.name());
        data.set(path + ".deployed", unit.deployed);
        data.set(path + ".horse-dead", unit.horseDead);
        data.set(path + ".muted", unit.muted);
        data.set(path + ".entity-uuid", unit.entityId == null ? null : unit.entityId.toString());
        data.set(path + ".world", unit.worldName);
        data.set(path + ".x", unit.x);
        data.set(path + ".y", unit.y);
        data.set(path + ".z", unit.z);
        data.set(path + ".horse-armor", cloneOrNull(unit.horseArmor));
        for (org.bukkit.inventory.EquipmentSlot slot : List.of(
                org.bukkit.inventory.EquipmentSlot.HAND,
                org.bukkit.inventory.EquipmentSlot.HEAD,
                org.bukkit.inventory.EquipmentSlot.CHEST,
                org.bukkit.inventory.EquipmentSlot.LEGS,
                org.bukkit.inventory.EquipmentSlot.FEET,
                org.bukkit.inventory.EquipmentSlot.OFF_HAND)) {
            data.set(path + ".equipment." + slotPath(slot), cloneOrNull(unit.equipment.get(slot)));
        }
        for (int slot = 0; slot < unit.inventory.length; slot++) {
            data.set(path + ".inventory." + slot, cloneOrNull(unit.inventory[slot]));
        }
    }

    private boolean saveData() {
        for (Unit unit : units.values()) saveUnit(unit);
        if (data == null) data = new YamlConfiguration();
        File temporary = new File(dataFile.getParentFile(), dataFile.getName() + ".tmp");
        try {
            if (!dataFile.getParentFile().isDirectory() && !dataFile.getParentFile().mkdirs()) {
                throw new IOException("Could not create plugin data directory.");
            }
            data.save(temporary);
            try {
                Files.move(temporary.toPath(), dataFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "[Army] Could not save mercenaries.yml.", exception);
            return false;
        } finally {
            if (temporary.exists() && !temporary.delete()) {
                plugin.getLogger().warning("[Army] Could not remove temporary data file " + temporary.getName() + ".");
            }
        }
    }

    private void removeUnitData(Unit unit) {
        cleanupGuardianStateForUnit(unit.id);
        cleanupSummonedMinions(unit.id);
        data.set("units." + unit.id, null);
    }

    private static double maxHealth(LivingEntity entity) {
        return entity.getAttribute(Attribute.MAX_HEALTH) == null
            ? 0.0 : entity.getAttribute(Attribute.MAX_HEALTH).getValue();
    }

    private boolean hasMaterials(Player player, Map<Material, Integer> costs) {
        for (Map.Entry<Material, Integer> cost : costs.entrySet()) {
            int count = 0;
            for (ItemStack stack : player.getInventory().getStorageContents()) {
                if (stack != null && stack.getType() == cost.getKey()) count += stack.getAmount();
            }
            if (count < cost.getValue()) return false;
        }
        return true;
    }

    private List<String> missingMaterials(Player player, Map<Material, Integer> costs) {
        List<String> missing = new ArrayList<>();
        for (Map.Entry<Material, Integer> cost : costs.entrySet()) {
            int count = 0;
            for (ItemStack stack : player.getInventory().getStorageContents()) {
                if (stack != null && stack.getType() == cost.getKey()) count += stack.getAmount();
            }
            if (count < cost.getValue()) {
                missing.add(cost.getKey().name() + ": cần " + cost.getValue() + ", hiện có " + count);
            }
        }
        return missing;
    }

    private Map<Material, Integer> readMaterialCosts(ConfigurationSection section,
                                                     Player player, String description) {
        Map<Material, Integer> costs = new EnumMap<>(Material.class);
        for (String materialName : section.getKeys(false)) {
            Material material = Material.matchMaterial(materialName);
            int amount = section.getInt(materialName, -1);
            if (material == null || material.isAir() || amount < 0 || amount > 1_000_000) {
                player.sendMessage("§cCấu hình vật phẩm chi phí không hợp lệ: " + materialName + ".");
                plugin.getLogger().warning("[Army] Invalid material cost '" + materialName
                    + "' for " + description + ".");
                return null;
            }
            if (amount > 0) costs.put(material, amount);
        }
        return costs;
    }

    private boolean removeMaterials(Player player, Map<Material, Integer> costs) {
        for (Map.Entry<Material, Integer> cost : costs.entrySet()) {
            int remaining = cost.getValue();
            ItemStack[] contents = player.getInventory().getStorageContents();
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack stack = contents[slot];
                if (stack == null || stack.getType() != cost.getKey()) continue;
                int removed = Math.min(remaining, stack.getAmount());
                int updatedAmount = stack.getAmount() - removed;
                remaining -= removed;
                if (updatedAmount <= 0) {
                    contents[slot] = null;
                } else {
                    ItemStack remainder = stack.clone();
                    remainder.setAmount(updatedAmount);
                    contents[slot] = remainder;
                }
                if (remaining == 0) break;
            }
            if (remaining > 0) return false;
            player.getInventory().setStorageContents(contents);
        }
        return true;
    }

    private boolean removeOne(Player player, ItemStack token) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || !stack.isSimilar(token) || stack.getAmount() <= 0) continue;
            if (stack.getAmount() == 1) contents[slot] = null;
            else stack.setAmount(stack.getAmount() - 1);
            player.getInventory().setStorageContents(contents);
            return true;
        }
        return false;
    }

    private List<Unit> getOwnerUnits(UUID owner) {
        return units.values().stream().filter(unit -> unit.owner.equals(owner))
            .sorted(Comparator.comparing(unit -> unit.id)).toList();
    }

    private boolean hasOwnedMercenaries(UUID owner) {
        return !getOwnerUnits(owner).isEmpty();
    }

    private Unit findOwnedByShortId(UUID owner, String shortId) {
        String prefix = shortId.toLowerCase(Locale.ROOT);
        List<Unit> matches = getOwnerUnits(owner).stream()
            .filter(unit -> unit.id.toString().startsWith(prefix)).toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private List<String> getClassIds() {
        ConfigurationSection classes = settings.getConfigurationSection("classes");
        return classes == null ? List.of() : classes.getKeys(false).stream().sorted().toList();
    }

    private List<String> getTierIds() {
        ConfigurationSection tiers = settings.getConfigurationSection("tiers");
        return tiers == null ? List.of() : tiers.getKeys(false).stream()
            .sorted(Comparator.comparingInt(MercenaryManager::parsePositiveInt)).toList();
    }

    private String normalizeClass(String value) {
        if (value == null) return null;
        String normalized = value.toLowerCase(Locale.ROOT);
        return settings.isConfigurationSection("classes." + normalized) ? normalized : null;
    }

    private boolean hasTier(int level) {
        return level >= 1 && level <= 10 && settings.isConfigurationSection("tiers." + level);
    }

    private int statRank(Unit unit) {
        return Math.min(1000, unit.tier + unit.level - 1);
    }

    private int nextLevelExperience(Unit unit) {
        return (int) Math.min(Integer.MAX_VALUE, (long) experiencePerLevel * unit.level);
    }

    private String classDisplay(String classId) {
        return settings.getString("classes." + classId + ".display-name", classId);
    }

    private String displayName(Unit unit) {
        return unit.customName == null || unit.customName.isBlank()
            ? classDisplay(unit.unitClass) : unit.customName;
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private static Mode parseMode(String value) {
        try {
            return Mode.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return Mode.PROTECT;
        }
    }

    private static String modeName(Mode mode) {
        return switch (mode) {
            case ATTACK -> "Tấn công";
            case DEFEND -> "Phòng thủ";
            case PROTECT -> "Bảo vệ chủ";
        };
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static int parsePositiveInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private int readBoundedInt(YamlConfiguration source, String path, int fallback, int min, int max) {
        if (source.contains(path) && !source.isInt(path) && !source.isDouble(path)) {
            plugin.getLogger().warning("[Army] " + path + " is not numeric; using " + fallback + ".");
            return fallback;
        }
        int value = source.getInt(path, fallback);
        if (value < min || value > max) {
            plugin.getLogger().warning("[Army] " + path + " must be between " + min + " and " + max
                + "; using " + fallback + ".");
            return fallback;
        }
        return value;
    }

    private double readBoundedDouble(YamlConfiguration source, String path, double fallback, double min, double max) {
        if (source.contains(path) && !source.isInt(path) && !source.isDouble(path)) {
            plugin.getLogger().warning("[Army] " + path + " is not numeric; using " + fallback + ".");
            return fallback;
        }
        double value = source.getDouble(path, fallback);
        if (!Double.isFinite(value) || value < min || value > max) {
            plugin.getLogger().warning("[Army] " + path + " must be between " + min + " and " + max
                + "; using " + fallback + ".");
            return fallback;
        }
        return value;
    }

    private static ItemStack copyOrNull(ItemStack item) {
        return item == null || item.getType().isAir() ? null : item.clone();
    }

    private static ItemStack cloneOrNull(ItemStack item) {
        return item == null ? null : item.clone();
    }

    private static ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) copy[i] = cloneOrNull(contents[i]);
        return copy;
    }

   private static String slotPath(EquipmentSlot slot) {
      return switch (slot) {
         case HEAD -> "helmet";
         case CHEST -> "chestplate";
         case LEGS -> "leggings";
         case FEET -> "boots";
         case HAND -> "main-hand";
         case OFF_HAND -> "off-hand";
         default -> slot.name().toLowerCase(Locale.ROOT);
      };
   }

    private static List<String> matching(String input, String... options) {
        String prefix = input.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) matches.add(option);
        return matches;
    }

   private void sendHelp(CommandSender sender) {
      sender.sendMessage("§6§lQuản lý lính đánh thuê:");
      sender.sendMessage("§e/vnarmy gui §7- Mở giao diện quản lý đội quân.");
      sender.sendMessage("§e/vnarmy list [user] §7- Xem đội quân của bạn; admin có thể xem người chơi khác.");
      sender.sendMessage("§e/vnarmy hire <loại> <bậc> §7- Thuê bằng token, XP và vật phẩm.");
      sender.sendMessage("§e/vnarmy recall [ID] | rest [ID] §7- Gọi tối đa " + this.maxDeployedUnits + " lính bậc/level cao nhất hoặc gọi/nghỉ một lính.");
      sender.sendMessage("§e/vnarmy rename <ID> <tên mới> §7- Đổi tên lính.");
      sender.sendMessage("§e/vnarmy attack | defend | protect §7- Đổi xu hướng AI của cả đội.");
      sender.sendMessage("§e/vnarmy gear <ID> §7- Mở kho trang bị cho một lính.");
      sender.sendMessage("§e/vnarmy mute <ID> §7- Bật/tắt âm thanh của một lính.");
      sender.sendMessage("§e/vnarmy remove <ID> §7- Xóa lính khỏi đội của bạn.");
      if (sender.hasPermission("vnmine.command.army.admin")) {
         sender.sendMessage("§e/vnarmy remove <user> <ID> §7- Xóa lính của người chơi.");
         sender.sendMessage("§e/vnarmy clear <user> §7- Xóa toàn bộ lính của người chơi.");
         sender.sendMessage("§e/vnarmy give <người chơi> token <tier> §7- Cấp lệnh triệu hồi.");
         sender.sendMessage("§e/vnarmy give <người chơi> army <loại> <tier> §7- Cấp lính trực tiếp.");
         sender.sendMessage("§e/vnarmy item give §7- Mở GUI cấp trang bị lính.");
         sender.sendMessage("§e/vnarmy item give <người chơi> <class> <vật liệu> <sao> <set> <món|set> §7- Cấp trực tiếp trang bị lính.");
      }

      sender.sendMessage("§7Loại lính: " + String.join(", ", this.getClassIds()));
      sender.sendMessage(
         "§7Bậc thuê: "
            + String.join(", ", this.getTierIds())
            + " §8| §7Cấp tối đa: §f"
            + this.maxLevel
            + " §8| §7Sở hữu: §f"
            + this.maxUnits
            + " §8| §7Triệu hồi: §f"
            + this.maxDeployedUnits
      );
   }

    private void cleanupFrostEffects() {
        for (BukkitTask task : frostSkillTasks) task.cancel();
        frostSkillTasks.clear();
        frostTasksByUnit.clear();
        for (TemporaryFrostBlock temporary : new ArrayList<>(temporaryFrostBlocks.values())) {
            if (temporary.removalTask != null) temporary.removalTask.cancel();
            restoreTemporaryFrostBlock(temporary);
        }
        cleanupFrostPotionEffects(true);
        frostShieldExpiries.clear();
        cleanupFireEffects();
    }

    private void cleanupGuardianStateForUnit(UUID unitId) {
        Set<BukkitTask> tasks = guardianTasksByUnit.remove(unitId);
        if (tasks != null) tasks.forEach(BukkitTask::cancel);
        guardianHitCounters.remove(unitId);
        for (Map.Entry<String, GuardianFakeBlockOverlay> entry
                : new ArrayList<>(guardianFakeBlockOverlays.entrySet())) {
            GuardianFakeBlockOverlay overlay = entry.getValue();
            if (overlay.unitExpiries.remove(unitId) == null) continue;
            if (overlay.unitExpiries.isEmpty()) {
                restoreGuardianFakeBlock(entry.getKey(), overlay);
            } else {
                overlay.expiresAtMillis = overlay.unitExpiries.values().stream()
                    .mapToLong(Long::longValue).max().orElse(System.currentTimeMillis());
                scheduleGuardianFakeBlockRestore(entry.getKey(), overlay);
            }
        }
    }

    private void cleanupGuardianTransientEffects() {
        for (UUID unitId : new ArrayList<>(guardianTasksByUnit.keySet())) {
            cleanupGuardianStateForUnit(unitId);
        }
        for (Map.Entry<String, GuardianFakeBlockOverlay> entry
                : new ArrayList<>(guardianFakeBlockOverlays.entrySet())) {
            restoreGuardianFakeBlock(entry.getKey(), entry.getValue());
        }
        guardianHitCounters.clear();
        guardianOwnerImmunityExpiries.clear();
    }

    public void stop() {
        if (aiTask != null) {
            aiTask.cancel();
            aiTask = null;
        }
        cleanupFrostEffects();
        cleanupGuardianTransientEffects();
        for (BukkitTask task : torchRemovalTasks.values()) task.cancel();
        torchRemovalTasks.clear();
        for (Unit unit : units.values()) {
            removeStatusDisplay(unit.id);
            Mob mob = activeEntities.remove(unit.id);
            if (mob == null || !mob.isValid()) mob = findExistingEntity(unit);
            if (mob != null && mob.isValid()) {
                captureEquipment(unit, mob);
                updateLocation(unit, mob.getLocation());
                unit.entityId = null;
                mob.remove();
                activeEntities.remove(unit.id);
            }
        }
        if (!saveData()) plugin.getLogger().severe("[Army] Could not persist mercenaries on plugin shutdown.");
        namePacketListener.close();
        statusPacketListener.close();
    }

   private static final class ArmyMenuHolder implements InventoryHolder {
      private final UUID owner;
      private final int page;
      private final List<UUID> unitIds;
      private Inventory inventory;

      private ArmyMenuHolder(UUID owner, int page, List<UUID> unitIds) {
         this.owner = owner;
         this.page = page;
         this.unitIds = unitIds;
      }

      public Inventory getInventory() {
         return this.inventory;
      }
   }

   private static final class FrostEffectSnapshot {
      private final PotionEffect previousEffect;
      private final long previousExpiresAt;
      private int appliedAmplifier;
      private long appliedExpiresAt;

      private FrostEffectSnapshot(PotionEffect previousEffect, long previousExpiresAt) {
         this.previousEffect = previousEffect;
         this.previousExpiresAt = previousExpiresAt;
      }
   }

   private static final class FrostFreezeSnapshot {
      private final int originalFreezeTicks;
      private final long originalExpiresAt;
      private long appliedExpiresAt;

      private FrostFreezeSnapshot(int originalFreezeTicks, long originalExpiresAt) {
         this.originalFreezeTicks = originalFreezeTicks;
         this.originalExpiresAt = originalExpiresAt;
      }
   }

   private static final class GearHolder implements InventoryHolder {
      private final UUID owner;
      private final UUID unit;
      private boolean returnToUnitMenu;
      private Inventory inventory;

      private GearHolder(UUID owner, UUID unit) {
         this.owner = owner;
         this.unit = unit;
      }

      public Inventory getInventory() {
         return this.inventory;
      }
   }

   private record GearStats(
      double health,
      double armor,
      double damagePercent,
      double fireDamagePercent,
      double frostDamagePercent,
      double cooldownReductionPercent,
      double attackRangeBlocks,
      double effectChancePercent,
      double effectDurationSeconds,
      double minions,
      double healingPercent,
      double natureDamagePercent
   ) {
      private static final MercenaryManager.GearStats ZERO = new MercenaryManager.GearStats(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

      private MercenaryManager.GearStats plus(MercenaryManager.GearStats other) {
         return new MercenaryManager.GearStats(
            this.health + other.health,
            this.armor + other.armor,
            this.damagePercent + other.damagePercent,
            this.fireDamagePercent + other.fireDamagePercent,
            this.frostDamagePercent + other.frostDamagePercent,
            this.cooldownReductionPercent + other.cooldownReductionPercent,
            this.attackRangeBlocks + other.attackRangeBlocks,
            this.effectChancePercent + other.effectChancePercent,
            this.effectDurationSeconds + other.effectDurationSeconds,
            this.minions + other.minions,
            this.healingPercent + other.healingPercent,
            this.natureDamagePercent + other.natureDamagePercent
         );
      }

      private MercenaryManager.GearStats scaled(double factor) {
         return new MercenaryManager.GearStats(
            this.health * factor,
            this.armor * factor,
            this.damagePercent * factor,
            this.fireDamagePercent * factor,
            this.frostDamagePercent * factor,
            this.cooldownReductionPercent * factor,
            this.attackRangeBlocks * factor,
            this.effectChancePercent * factor,
            this.effectDurationSeconds * factor,
            this.minions * factor,
            this.healingPercent * factor,
            this.natureDamagePercent * factor
         );
      }
   }

   private static final class GuardianFakeBlockOverlay {
      private final World world;
      private final int x;
      private final int y;
      private final int z;
      private final Map<UUID, Long> unitExpiries = new HashMap<>();
      private final Set<UUID> viewers = new HashSet<>();
      private long expiresAtMillis;
      private BukkitTask removalTask;

      private GuardianFakeBlockOverlay(Block block) {
         this.world = block.getWorld();
         this.x = block.getX();
         this.y = block.getY();
         this.z = block.getZ();
      }

      private Location location() {
         return new Location(this.world, this.x, this.y, this.z);
      }
   }

   private record GuardianStats(
      double baseHealth,
      double tierHealth,
      double levelHealth,
      double equipmentHealth,
      double maxHealth,
      double baseArmor,
      double tierArmor,
      double levelArmor,
      double armor,
      double baseDamage,
      double tierDamage,
      double levelDamage,
      double damage
   ) {
   }

   private static final class IgniteContext {
      private final Set<UUID> ignitedTargets = new HashSet<>();
      private int targetLimit;

      private IgniteContext(int targetLimit) {
         this.targetLimit = targetLimit;
      }
   }

   private enum MenuType {
      MAIN,
        RPG_HUB,
        WORLD_MAP,
        STORY,
      ADMIN,
      ADMIN_CATEGORY,
      ADMIN_RPG_PLAYERS,
      ADMIN_SKILLBOOKS,
      ADMIN_PROGRESSION,
      ADMIN_PLAYERS,
      GIVE_TYPE,
      CLASS_SELECT,
      TIER_SELECT,
      WELCOME_GIFT,
      ITEM_GIVE_PLAYERS,
      ITEM_GIVE_CLASS,
      ITEM_GIVE_MATERIAL,
      ITEM_GIVE_STARS,
      ITEM_GIVE_SET,
      ITEM_GIVE_SLOT,
      DISASTER_SELECT,
      BOSS_SELECT,
      BOSS_LEVEL_SELECT,
      ADMIN_TIME,
      TIME_DURATION,
      ADMIN_DAY,
      ADMIN_PROTECT,
      PROTECTION_REMOVE,
      TELEPORT_PLAYERS,
      TELEPORT_WORLDS,
      ADMIN_USER_PLAYERS,
      ADMIN_USER_ACTION,
      ADMIN_GROUPS,
      ADMIN_GROUP_SELECT,
      ADMIN_GROUP_DELETE_CONFIRM,
      ADMIN_PERMISSION_SELECT,
      ADMIN_USER_GROUP_SELECT,
      ADMIN_USER_PERMISSION_ACTIONS,
      ADMIN_USER_PERMISSION_SELECT,
      ADMIN_SCOREBOARD_CONFIRM,
      ADMIN_DEATH_PLAYERS,
      ADMIN_SPAWN_WORLD,
      UNIT_DELETE_CONFIRM;
   }

   private enum Mode {
      ATTACK,
      DEFEND,
      PROTECT;
   }

   private static final class ServiceMenuHolder implements InventoryHolder {
      private final UUID viewer;
      private final MercenaryManager.MenuType type;
      private final UUID target;
      private final String itemType;
      private final String classId;
      private final int page;
      private String gearMaterial;
      private int gearStars;
      private String gearSet;
      private final Map<Integer, String> choices = new HashMap<>();
      private Inventory inventory;

      private ServiceMenuHolder(UUID viewer, MercenaryManager.MenuType type, UUID target, String itemType, String classId, int page) {
         this.viewer = viewer;
         this.type = type;
         this.target = target;
         this.itemType = itemType;
         this.classId = classId;
         this.page = page;
      }

      public Inventory getInventory() {
         return this.inventory;
      }
   }

   private record StatPart(double value, String color) {
   }

   private static final class TemporaryFrostBlock {
      private final String key;
      private final World world;
      private final int x;
      private final int y;
      private final int z;
      private final BlockData originalData;
      private final Material temporaryType;
      private final UUID ownerUnitId;
      private BukkitTask removalTask;

      private TemporaryFrostBlock(String key, Block block, Material temporaryType, UUID ownerUnitId) {
         this.key = key;
         this.world = block.getWorld();
         this.x = block.getX();
         this.y = block.getY();
         this.z = block.getZ();
         this.originalData = block.getBlockData().clone();
         this.temporaryType = temporaryType;
         this.ownerUnitId = ownerUnitId;
      }
   }

   private static final class TemporaryTorch {
      private final UUID id;
      private final String worldName;
      private final int x;
      private final int y;
      private final int z;
      private final long expiresAt;

      private TemporaryTorch(UUID id, String worldName, int x, int y, int z, long expiresAt) {
         this.id = id;
         this.worldName = worldName;
         this.x = x;
         this.y = y;
         this.z = z;
         this.expiresAt = expiresAt;
      }
   }

   private static final class Unit {
      private final UUID id;
      private final UUID owner;
      private final String unitClass;
      private int originalTier;
      private int tier;
      private int level = 1;
      private int experience;
      private String customName = "";
      private long recoveryUntilMillis;
      private boolean gearCustomized;
      private MercenaryManager.Mode mode;
      private boolean deployed;
      private boolean muted;
      private boolean horseDead;
      private UUID entityId;
      private String worldName;
      private double x;
      private double y;
      private double z;
      private final EnumMap<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
      private final ItemStack[] inventory = new ItemStack[36];
      private ItemStack horseArmor;

      private Unit(UUID id, UUID owner, String unitClass, int level, MercenaryManager.Mode mode, boolean deployed) {
         this.id = id;
         this.owner = owner;
         this.unitClass = unitClass;
         this.originalTier = level;
         this.tier = level;
         this.mode = mode;
         this.deployed = deployed;
      }
   }

   private static final class UnitMenuHolder implements InventoryHolder {
      private final UUID owner;
      private final UUID unit;
      private Inventory inventory;

      private UnitMenuHolder(UUID owner, UUID unit) {
         this.owner = owner;
         this.unit = unit;
      }

      public Inventory getInventory() {
         return this.inventory;
      }
   }
}
