package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.bossbar.BossBar.Color;
import net.kyori.adventure.bossbar.BossBar.Overlay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Armadillo;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Camel;
import org.bukkit.entity.Cat;
import org.bukkit.entity.CaveSpider;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Dolphin;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Flying;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Giant;
import org.bukkit.entity.Goat;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Sniffer;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.Spider;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Turtle;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkull;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.EnderDragon.Phase;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

public class BossEventManager implements Listener {
   private final VnMineHardcore plugin;
   private final NamespacedKey armyOwnerKey;
   private final NamespacedKey bossArmorSetKey;
   private final ConfigManager config;
   private final LogManager logManager;
   private final BossLeaderboardManager leaderboardManager;
   private final Logger logger;
   private final Random random = ThreadLocalRandom.current();
   private boolean bossActive = false;
   private double bossRewardThresholdHealth;
   private final Map<UUID, Double> bossDamageContributions = new HashMap<>();
   private String currentBossName = null;
   private LivingEntity currentBoss = null;
   private ConfigManager.BossConfig activeBossConfig;
   private BossBar bossBar;
   private int timeSinceLastBoss = 0;
   private int bossEventIntervalSeconds = 0;
   private int warningTimeLeft = 0;
   private ConfigManager.BossConfig pendingBoss = null;
   private Integer pendingBossLevel = null;
   // Tracking thời gian boss đã tồn tại
   private int bossElapsedSeconds = 0;
   private int bossDurationSeconds = 0;
   private BukkitTask schedulerTask;
   private BukkitTask bossMonitorTask;
   // AI tracking
   private Location currentBossSpawnLocation = null;
   private int currentBossActivityRadius = 100;
   private World forcedBossChunkWorld;
   private int forcedBossChunkX;
   private int forcedBossChunkZ;
   private boolean bossChunkWasAlreadyForceLoaded;
   private boolean returningToAnchor;
   private long anchorPathUpdatedAtMs;
   private Location giantWanderTarget;
   private long giantWanderUntilMs;
   private BukkitTask aiTask;
   private int currentBossLevel = 1;
   private double logicalBossMaxHealth;
   private double logicalBossHealth;
   private boolean bossUsesVirtualHealth;
   private final Map<EntityDamageByEntityEvent, Double> virtualHealthDamageEvents = new IdentityHashMap<>();
   private long nextCreeperExplosionAtMs;
   private long nextBossProjectileDodgeAtMs;
   private BukkitTask creeperChargeTask;
   private final Map<String, Long> bossSkillCooldowns = new HashMap<>();
   private final Set<Entity> activeBossProjectiles = new HashSet<>();
   private final Map<UUID, BossEventManager.BossProjectileConfig> bossProjectileConfigs = new HashMap<>();
   private final Set<BukkitTask> bossSkillTasks = new HashSet<>();
   private final Set<UUID> bossSummons = new HashSet<>();
   private final Set<UUID> bossTerrainEntities = new HashSet<>();
   private final Map<UUID, Boolean> bossTerrainDrops = new HashMap<>();
   private final Set<UUID> bossBoulders = new HashSet<>();
   private final Set<UUID> webTrapProjectiles = new HashSet<>();
   private final Set<UUID> disarmProjectiles = new HashSet<>();
   private final Map<UUID, Location> splitFireballs = new HashMap<>();
   private final Map<UUID, Long> ravagerThornsCooldowns = new HashMap<>();
   private final Map<UUID, Long> spinDamageCooldowns = new HashMap<>();
   private final Map<UUID, Long> armorKillHealCooldowns = new HashMap<>();
   private final Map<BossEventManager.BlockPosition, BossEventManager.TemporaryBlock> temporaryBlocks = new HashMap<>();
   private final Set<BossEventManager.BlockPosition> playerModifiedTemporaryBlocks = new HashSet<>();
   private final Map<UUID, Long> phantomRevealedUntil = new HashMap<>();
   private boolean spiderBroodReleased;
   private static final double ENDERMAN_LOOK_DOT_THRESHOLD = 0.95;
   private static final double MAX_CONFIGURED_BOSS_HEALTH = 1.0E9;

   public BossEventManager(VnMineHardcore plugin, ConfigManager config) {
      this.plugin = plugin;
      this.armyOwnerKey = new NamespacedKey(plugin, "army_owner");
      this.bossArmorSetKey = new NamespacedKey(plugin, "boss_armor_set");
      this.config = config;
      this.logManager = plugin.getLogManager();
      this.leaderboardManager = plugin.getBossLeaderboardManager();
      this.logger = plugin.getLogger();
      this.bossBar = BossBar.bossBar(Component.text(""), 1.0F, Color.PURPLE, Overlay.PROGRESS);
      if (config.bossEventsEnabled) {
         this.start();
      }

      // Đăng ký listener
      Bukkit.getPluginManager().registerEvents(this, plugin);
      this.logger
         .info(
            "[BossEvent] Initialized: enabled="
               + config.bossEventsEnabled
               + ", interval="
               + config.bossEventMinIntervalRaw
               + "s, bosses="
               + config.bossConfigs.size()
         );
   }

   private void keepBossChunkLoaded() {
      if (this.currentBoss != null && this.currentBoss.isValid()) {
         Location location = this.currentBoss.getLocation();
         World world = location.getWorld();
         if (world != null) {
            int chunkX = location.getBlockX() >> 4;
            int chunkZ = location.getBlockZ() >> 4;
            if (!world.equals(this.forcedBossChunkWorld) || chunkX != this.forcedBossChunkX || chunkZ != this.forcedBossChunkZ) {
               if (this.forcedBossChunkWorld != null && !this.bossChunkWasAlreadyForceLoaded) {
                  this.forcedBossChunkWorld.setChunkForceLoaded(this.forcedBossChunkX, this.forcedBossChunkZ, false);
               }

               this.bossChunkWasAlreadyForceLoaded = world.isChunkForceLoaded(chunkX, chunkZ);
               if (!this.bossChunkWasAlreadyForceLoaded) {
                  world.setChunkForceLoaded(chunkX, chunkZ, true);
               }

               this.forcedBossChunkWorld = world;
               this.forcedBossChunkX = chunkX;
               this.forcedBossChunkZ = chunkZ;
            }
         }
      }
   }

   public void start() {
      if (this.config.bossEventsEnabled && (this.schedulerTask == null || this.schedulerTask.isCancelled())) {
         this.timeSinceLastBoss = 0;
         this.bossEventIntervalSeconds = this.sampleBossEventInterval();
         this.schedulerTask = (new BukkitRunnable() {
            public void run() {
               if (!Bukkit.getWorlds().isEmpty()) {
                  if (!BossEventManager.this.bossActive && BossEventManager.this.pendingBoss == null) {
                     BossEventManager.this.timeSinceLastBoss++;
                     if (BossEventManager.this.timeSinceLastBoss >= BossEventManager.this.bossEventIntervalSeconds) {
                        BossEventManager.this.trySpawnBoss();
                     }
                  }

                  if (BossEventManager.this.warningTimeLeft > 0 && BossEventManager.this.pendingBoss != null) {
                     BossEventManager.this.warningTimeLeft--;
                     BossEventManager.this.updateWarningBar();
                     if (BossEventManager.this.warningTimeLeft <= 0) {
                        ConfigManager.BossConfig bossToSpawn = BossEventManager.this.pendingBoss;
                        Integer levelToSpawn = BossEventManager.this.pendingBossLevel;
                        BossEventManager.this.pendingBoss = null;
                        BossEventManager.this.pendingBossLevel = null;
                        BossEventManager.this.spawnBoss(bossToSpawn, levelToSpawn);
                     }
                  }
               }
            }
         }).runTaskTimer(this.plugin, 20L, 20L);
      }
   }

   private int sampleBossEventInterval() {
      return Math.max(1, ConfigManager.parseRangeOrInt(this.config.bossEventMinIntervalRaw, 1200));
   }

   private int sampleBossLevel(ConfigManager.BossConfig bc) {
      if (this.config.bossLevelProgressionEnabled && bc != null) {
         if (this.config.bossLevelThresholds != null && !this.config.bossLevelThresholds.isEmpty()) {
            long worldDay = this.getCurrentWorldDay();
            ConfigManager.BossLevelThreshold selected = null;

            for (ConfigManager.BossLevelThreshold threshold : this.config.bossLevelThresholds) {
               if (threshold != null && threshold.minDay <= worldDay && (selected == null || threshold.minDay > selected.minDay)) {
                  selected = threshold;
               }
            }

            if (selected == null) {
               return 1;
            }

            int min = Math.max(1, Math.min(1000, selected.minLevel));
            int max = Math.max(min, Math.min(1000, selected.maxLevel));
            int level = this.random.nextInt(min, max + 1);
            String worldName = Bukkit.getWorlds().isEmpty() ? "<none>" : ((World)Bukkit.getWorlds().get(0)).getName();
            this.logger
               .info(
                  "[BossEvent] Automatic level selection: world="
                     + worldName
                     + ", day="
                     + worldDay
                     + ", threshold="
                     + selected.minDay
                     + ", range="
                     + min
                     + "-"
                     + max
                     + ", selected="
                     + level
                     + "."
               );
            return level;
         } else {
            return 1;
         }
      } else {
         return 1;
      }
   }

   private long getCurrentWorldDay() {
      return Bukkit.getWorlds().isEmpty() ? 0L : Math.max(0L, ((World)Bukkit.getWorlds().get(0)).getFullTime() / 24000L);
   }

   private double getBossSpawnSuccessChance() {
      if (this.config.bossSpawnSuccessProgressionEnabled && this.config.bossSpawnSuccessThresholds != null && !this.config.bossSpawnSuccessThresholds.isEmpty()
         )
       {
         long worldDay = this.getCurrentWorldDay();
         ConfigManager.BossSpawnSuccessThreshold selected = null;

         for (ConfigManager.BossSpawnSuccessThreshold threshold : this.config.bossSpawnSuccessThresholds) {
            if (threshold != null && threshold.minDay <= worldDay && (selected == null || threshold.minDay > selected.minDay)) {
               selected = threshold;
            }
         }

         return selected == null ? this.config.bossSpawnSuccessDefaultPercent : selected.chancePercent;
      } else {
         return this.config.bossSpawnSuccessProgressionEnabled ? this.config.bossSpawnSuccessDefaultPercent : 100.0;
      }
   }

   private double resolveBossHp(ConfigManager.BossConfig bc, int level) {
      if (bc == null) {
         return 1.0;
      }

      if (this.config.bossRandomHealthEnabled) {
         double levelProgress = (Math.max(1, Math.min(10, level)) - 1) / 9.0;
         int minimum = (int)Math.round(
            this.config.bossHealthLevelOneMinimum + (this.config.bossHealthLevelTenMinimum - this.config.bossHealthLevelOneMinimum) * levelProgress
         );
         int maximum = (int)Math.round(
            this.config.bossHealthLevelOneMaximum + (this.config.bossHealthLevelTenMaximum - this.config.bossHealthLevelOneMaximum) * levelProgress
         );
         double hp = this.random.nextInt(minimum, maximum + 1);
         this.logger.info("[BossEvent] Rolled " + hp + " HP for " + bc.displayName + " at level " + level + " (range " + minimum + "-" + maximum + ").");
         return hp;
      }

      if (bc.hpByLevel != null && !bc.hpByLevel.isEmpty()) {
         Double exact = bc.hpByLevel.get(level);
         if (this.isValidBossHealth(exact)) {
            return exact;
         }

         Integer bestLevel = null;
         Double bestHp = null;

         for (Entry<Integer, Double> entry : bc.hpByLevel.entrySet()) {
            if (entry.getKey() != null
               && entry.getKey() <= level
               && this.isValidBossHealth(entry.getValue())
               && (bestLevel == null || entry.getKey() > bestLevel)) {
               bestLevel = entry.getKey();
               bestHp = entry.getValue();
            }
         }

         if (bestHp != null) {
            return bestHp;
         }
      }

      double baseHp = this.isValidBossHealth(bc.hp) ? bc.hp : 1.0;
      double scaledHp = baseHp * Math.pow(1.5, Math.max(0, level - 1));
      if (Double.isFinite(scaledHp) && !(scaledHp > 1.0E9)) {
         return Math.max(1.0, scaledHp);
      }

      this.logger.warning("[BossEvent] Calculated boss HP is invalid or exceeds 1.0E9; using 1.0E9 HP.");
      return 1.0E9;
   }

   private boolean isValidBossHealth(Double hp) {
      return hp != null && Double.isFinite(hp) && hp > 0.0 && hp <= 1.0E9;
   }

   private double safeSkillValue(double value, double fallback, double minimum, double maximum) {
      return !Double.isFinite(value) ? fallback : Math.max(minimum, Math.min(maximum, value));
   }

   private void registerBossProjectile(Projectile projectile, double damage, double radius, boolean splashOnImpact) {
      this.activeBossProjectiles.add(projectile);
      this.bossProjectileConfigs.put(projectile.getUniqueId(), new BossEventManager.BossProjectileConfig(damage, radius, splashOnImpact));
   }

   private void tickSplitFireballs() {
      if (!this.splitFireballs.isEmpty()) {
         for (Entry<UUID, Location> entry : new ArrayList<>(this.splitFireballs.entrySet())) {
            if (!(Bukkit.getEntity(entry.getKey()) instanceof Fireball fireball && fireball.isValid())) {
               this.splitFireballs.remove(entry.getKey());
            } else if (fireball.getLocation().distanceSquared(entry.getValue()) >= 100.0) {
               this.splitFireballs.remove(entry.getKey());
               this.bossProjectileConfigs.remove(entry.getKey());
               this.activeBossProjectiles.remove(fireball);
               Location splitAt = fireball.getLocation().clone();
               Vector incoming = fireball.getVelocity();
               fireball.remove();
               this.splitGhastFireball(splitAt, incoming);
            }
         }
      }
   }

   private void tickBossTraits(ConfigManager.BossConfig bc) {
      if (this.currentBoss != null && !this.currentBoss.isDead()) {
         if (this.currentBoss.getType() == EntityType.ENDERMAN && this.traitEnabled(bc, "hydro-phobic-aura", true)) {
            Block feet = this.currentBoss.getLocation().getBlock();
            if (feet.isLiquid() || feet.getRelative(0, -1, 0).isLiquid() || this.currentBoss.getWorld().hasStorm()) {
               String cooldownKey = this.currentBoss.getUniqueId() + ":hydro-aura";
               long now = System.currentTimeMillis();
               if (now >= this.bossSkillCooldowns.getOrDefault(cooldownKey, 0L)) {
                  double range = this.traitNumber(bc, "hydro-phobic-aura", "radius", 5.0, 1.0, 12.0);
                  int wear = (int)this.traitNumber(bc, "hydro-phobic-aura", "armor-damage", 1.0, 0.0, 5.0);

                  for (Entity entity : this.currentBoss.getWorld().getNearbyEntities(this.currentBoss.getLocation(), range, range, range)) {
                     if (entity instanceof Player player && !(player.getLocation().distanceSquared(this.currentBoss.getLocation()) > range * range)) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0, false, true, true));
                        this.damageArmorDurability(player, wear);
                     }
                  }

                  this.bossSkillCooldowns.put(cooldownKey, now + (long)this.traitNumber(bc, "hydro-phobic-aura", "cooldown-seconds", 3.0, 1.0, 30.0) * 1000L);
               }
            }
         }

         if (this.currentBoss.getType() == EntityType.PHANTOM && this.traitEnabled(bc, "nocturnal-cloak", true)) {
            long revealedUntil = this.phantomRevealedUntil.getOrDefault(this.currentBoss.getUniqueId(), 0L);
            int lightThreshold = (int)this.traitNumber(bc, "nocturnal-cloak", "light-threshold", 7.0, 0.0, 15.0);
            if (this.currentBoss.getLocation().getBlock().getLightLevel() < lightThreshold && System.currentTimeMillis() >= revealedUntil) {
               this.currentBoss.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 40, 0, false, false, false));
            } else {
               this.currentBoss.removePotionEffect(PotionEffectType.INVISIBILITY);
            }
         }

         if (this.currentBoss.getType() == EntityType.SPIDER
            && !this.spiderBroodReleased
            && this.traitEnabled(bc, "brood-release", true)
            && this.currentBoss.getHealth() <= this.currentBoss.getMaxHealth() * this.traitNumber(bc, "brood-release", "health-threshold", 0.5, 0.1, 0.9)) {
            this.spiderBroodReleased = true;
            int minimum = (int)this.traitNumber(bc, "brood-release", "minions-min", 4.0, 1.0, 8.0);
            int maximum = (int)this.traitNumber(bc, "brood-release", "minions-max", 6.0, minimum, 8.0);

            for (int i = 0; i < this.random.nextInt(minimum, maximum + 1); i++) {
               Location spawn = this.currentBoss.getLocation().add(this.random.nextDouble() * 3.0 - 1.5, 0.0, this.random.nextDouble() * 3.0 - 1.5);
               CaveSpider minion = (CaveSpider)this.currentBoss.getWorld().spawn(spawn, CaveSpider.class);
               this.bossSummons.add(minion.getUniqueId());
            }
         }

         if (this.currentBoss.getType() == EntityType.VINDICATOR
            && this.traitEnabled(bc, "enrage", true)
            && this.currentBoss.getHealth() <= this.currentBoss.getMaxHealth() * this.traitNumber(bc, "enrage", "health-threshold", 0.3, 0.05, 0.8)) {
            this.currentBoss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, false, true, true));
            this.currentBoss.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 0, false, true, true));
            if (this.currentBoss.getAttribute(Attribute.KNOCKBACK_RESISTANCE) != null) {
               this.currentBoss.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
            }
         }
      }
   }

   private void damageArmorDurability(Player player, int amount) {
      if (amount > 0) {
         for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && !armor.getType().isAir() && armor.getItemMeta() instanceof Damageable damageable && !armor.getItemMeta().isUnbreakable()) {
               int unbreaking = armor.getEnchantmentLevel(Enchantment.UNBREAKING);
               int actualDamage = 0;

               for (int point = 0; point < amount; point++) {
                  if (this.random.nextDouble() < 0.6 / (unbreaking + 1.0)) {
                     actualDamage++;
                  }
               }

               if (actualDamage != 0) {
                  int nextDamage = damageable.getDamage() + actualDamage;
                  if (nextDamage >= armor.getType().getMaxDurability()) {
                     armor.setAmount(0);
                  } else {
                     damageable.setDamage(nextDamage);
                     armor.setItemMeta(damageable);
                  }
               }
            }
         }
      }
   }

   private void damageEntitiesInRadius(World world, Location center, double radius, double damage) {
      double radiusSquared = radius * radius;

      for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
         if (entity instanceof LivingEntity living
            && !living.equals(this.currentBoss)
            && living.isValid()
            && !living.isDead()
            && living.getLocation().distanceSquared(center) <= radiusSquared) {
            living.damage(damage, this.currentBoss);
         }
      }
   }

   private boolean belongsToCurrentBoss(Entity entity) {
      if (this.currentBoss == null) {
         return false;
      } else {
         return entity.equals(this.currentBoss)
            ? true
            : entity instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter && shooter.equals(this.currentBoss);
      }
   }

   @EventHandler
   public void onBossSkillProjectileDamage(EntityDamageByEntityEvent event) {
      if (event.getEntity() instanceof Projectile fireball
         && event.getDamager() instanceof Player
         && this.currentBoss instanceof Ghast
         && this.traitEnabled(this.activeBossConfig, "fireball-shield", true)
         && fireball.getShooter() instanceof Entity shooter
         && shooter.equals(this.currentBoss)) {
         event.setCancelled(true);
         this.splitFireballs.remove(fireball.getUniqueId());
         this.bossProjectileConfigs.remove(fireball.getUniqueId());
         this.activeBossProjectiles.remove(fireball);
         fireball.remove();
      } else if (event.getDamager() instanceof Projectile projectile) {
         BossEventManager.BossProjectileConfig skill = this.bossProjectileConfigs.get(projectile.getUniqueId());
         if (skill != null) {
            if (event.getEntity().getLocation().distanceSquared(projectile.getLocation()) > skill.radius() * skill.radius()) {
               event.setCancelled(true);
               return;
            }

            event.setDamage(skill.damage());
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onBossDamagePlayer(EntityDamageByEntityEvent event) {
      if (event.getEntity() instanceof Player player && this.belongsToCurrentBoss(event.getDamager())) {
         boolean hasLineOfSight = this.hasBossLineOfSight(player);
         boolean spiderDebug = this.currentBoss instanceof Spider && this.config.spiderBossDamageDebugEnabled;
         if (spiderDebug) {
            Location bossLocation = this.currentBoss.getLocation();
            Location playerLocation = player.getLocation();
            this.logger
               .info(
                  "[BossDebug][Spider] damage player="
                     + player.getName()
                     + " cause="
                     + event.getCause()
                     + " damager="
                     + event.getDamager().getType()
                     + " distance="
                     + String.format(Locale.ROOT, "%.2f", bossLocation.distance(playerLocation))
                     + " lineOfSight="
                     + hasLineOfSight
                     + " rawDamage="
                     + String.format(Locale.ROOT, "%.2f", event.getDamage())
                     + " finalDamage="
                     + String.format(Locale.ROOT, "%.2f", event.getFinalDamage())
                     + " alreadyCancelled="
                     + event.isCancelled()
                     + " bossBlock="
                     + this.blockPosition(bossLocation)
                     + " playerBlock="
                     + this.blockPosition(playerLocation)
               );
         }

         if (!event.isCancelled()) {
            if (!hasLineOfSight) {
               event.setCancelled(true);
               if (spiderDebug) {
                  this.logger.info("[BossDebug][Spider] decision=CANCEL_NO_LINE_OF_SIGHT player=" + player.getName());
               }
            } else {
               double cappedDamage = Math.min(event.getDamage(), this.config.bossMaxPlayerHitDamage);
               if (spiderDebug && cappedDamage < event.getDamage()) {
                  this.logger
                     .info(
                        "[BossDebug][Spider] decision=CAP_DAMAGE player="
                           + player.getName()
                           + " from="
                           + String.format(Locale.ROOT, "%.2f", event.getDamage())
                           + " to="
                           + String.format(Locale.ROOT, "%.2f", cappedDamage)
                     );
               }

               event.setDamage(cappedDamage);
            }
         }
      }
   }

   private String blockPosition(Location location) {
      return location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
   }

   @EventHandler
   public void onBossSkillProjectileHit(ProjectileHitEvent event) {
      Projectile projectile = event.getEntity();
      UUID projectileId = projectile.getUniqueId();
      if (this.splitFireballs.remove(projectileId) != null) {
         this.splitGhastFireball(projectile.getLocation(), projectile.getVelocity());
      }

      if (this.webTrapProjectiles.remove(projectileId) && this.currentBoss != null && !this.currentBoss.isDead()) {
         ConfigManager.BossSkillConfig trap = this.skillConfig(this.activeBossConfig, "cobweb-trap");
         this.placeTemporaryWebTrap(projectile.getLocation(), trap);
      }

      if (this.disarmProjectiles.remove(projectileId) && event.getHitEntity() instanceof Player player) {
         this.disarmPlayer(player, this.skillConfig(this.activeBossConfig, "disarm-magic"));
      }

      BossEventManager.BossProjectileConfig skill = this.bossProjectileConfigs.remove(projectile.getUniqueId());
      this.activeBossProjectiles.remove(projectile);
      projectile.remove();
      if (skill != null && skill.splashOnImpact() && this.currentBoss != null && !this.currentBoss.isDead()) {
         World world = projectile.getWorld();
         Location hitLocation = projectile.getLocation();
         double radiusSquared = skill.radius() * skill.radius();

         for (Entity entity : world.getNearbyEntities(hitLocation, skill.radius(), skill.radius(), skill.radius())) {
            if (entity instanceof LivingEntity living
               && !living.equals(this.currentBoss)
               && living.isValid()
               && !living.isDead()
               && living.getLocation().distanceSquared(hitLocation) <= radiusSquared) {
               living.damage(skill.damage(), this.currentBoss);
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onBossTerrainExplosion(EntityExplodeEvent event) {
      boolean witherAttack = this.currentBoss instanceof Wither
         && (
            event.getEntity().equals(this.currentBoss)
               || event.getEntity() instanceof WitherSkull skull && skull.getShooter() != null && skull.getShooter().equals(this.currentBoss)
         );
      if (witherAttack || this.belongsToCurrentBoss(event.getEntity()) || this.bossProjectileConfigs.containsKey(event.getEntity().getUniqueId())) {
         event.blockList().clear();
         event.setYield(0.0F);
      }
   }

   @EventHandler
   public void onBossCreeperExplosionPrime(ExplosionPrimeEvent event) {
      if (this.currentBoss instanceof Creeper && event.getEntity().equals(this.currentBoss)) {
         event.setCancelled(true);
         ((Creeper)this.currentBoss).setIgnited(false);
         ((Creeper)this.currentBoss).setFuseTicks(0);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onBossTerrainChange(EntityChangeBlockEvent event) {
      UUID id = event.getEntity().getUniqueId();
      if (this.bossTerrainEntities.remove(id)) {
         event.setCancelled(true);
         if (this.bossBoulders.remove(id)) {
            this.detonateBoulder(event.getEntity().getLocation());
         }

         boolean dropBlock = this.bossTerrainDrops.remove(id) == Boolean.TRUE;
         if (dropBlock && event.getEntity() instanceof FallingBlock falling) {
            falling.getWorld().dropItemNaturally(falling.getLocation(), new ItemStack(event.getTo()));
         }

         event.getEntity().remove();
         this.activeBossProjectiles.remove(event.getEntity());
      } else {
         boolean witherAttack = this.currentBoss instanceof Wither
            && (
               event.getEntity().equals(this.currentBoss)
                  || event.getEntity() instanceof WitherSkull skull && skull.getShooter() != null && skull.getShooter().equals(this.currentBoss)
            );
         if (witherAttack || this.belongsToCurrentBoss(event.getEntity())) {
            event.setCancelled(true);
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onBossDamageProtection(EntityDamageEvent event) {
      if (this.currentBoss != null && event.getEntity() == this.currentBoss) {
         if (!(event instanceof EntityDamageByEntityEvent damageEvent && this.playerAttacker(damageEvent.getDamager()) != null)) {
            event.setCancelled(true);
         } else if (this.bossUsesVirtualHealth) {
            double appliedDamage = Math.min(this.logicalBossHealth, Math.max(0.0, event.getFinalDamage()));
            this.virtualHealthDamageEvents.put(damageEvent, appliedDamage);
            this.logicalBossHealth = Math.max(0.0, this.logicalBossHealth - appliedDamage);
            if (this.logicalBossHealth <= 0.0) {
               event.setDamage(Math.max(1.0, this.currentBoss.getHealth() + this.currentBoss.getMaxHealth()));
            } else {
               event.setCancelled(true);
               this.currentBoss.setHealth(Math.max(1.0, this.currentBoss.getMaxHealth() * this.logicalBossHealth / this.logicalBossMaxHealth));
               this.updateBossDisplay(this.currentBossName, Math.max(0, this.bossDurationSeconds - this.bossElapsedSeconds));
            }
         } else {
            if (this.currentBoss.getType() == EntityType.GIANT && this.traitEnabled(this.activeBossConfig, "giant-armor", true)) {
               boolean headshot = damageEvent.getDamager() instanceof Arrow projectile
                  && projectile.getShooter() instanceof Player
                  && projectile.getLocation().getY() >= this.currentBoss.getLocation().getY() + this.currentBoss.getHeight() * 0.72;
               if (headshot) {
                  damageEvent.setDamage(damageEvent.getDamage() * this.traitNumber(this.activeBossConfig, "giant-armor", "headshot-multiplier", 2.0, 1.0, 5.0));
                  this.currentBoss.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 8, false, true, true));
                  this.currentBoss.setVelocity(new Vector(0.0, 0.0, 0.0));
               } else {
                  damageEvent.setDamage(damageEvent.getDamage() * 0.2);
               }
            }

            if (this.currentBoss.getType() == EntityType.RAVAGER
               && damageEvent.getDamager() instanceof Player player
               && damageEvent.getCause() == DamageCause.ENTITY_ATTACK
               && this.traitEnabled(this.activeBossConfig, "thorns-armor", true)
               && this.ravagerThornsCooldowns.getOrDefault(player.getUniqueId(), 0L) < System.currentTimeMillis()) {
               double reflected = Math.max(
                  0.0, damageEvent.getDamage() * this.traitNumber(this.activeBossConfig, "thorns-armor", "reflection-percent", 0.2, 0.0, 1.0)
               );
               this.ravagerThornsCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + 250L);
               if (reflected > 0.0) {
                  LivingEntity source = this.currentBoss;
                  Bukkit.getScheduler().runTask(this.plugin, () -> {
                     if (source != null && source.isValid() && player.isOnline() && player.getHealth() > 0.0) {
                        player.damage(reflected, source);
                     }
                  });
               }
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onBossPlayerDamage(EntityDamageByEntityEvent event) {
      if (this.currentBoss != null && event.getEntity() == this.currentBoss && this.playerAttacker(event.getDamager()) != null) {
         Double virtualDamage = this.virtualHealthDamageEvents.remove(event);
         if (!event.isCancelled() || virtualDamage != null) {
            double damage = virtualDamage != null ? virtualDamage : Math.min(event.getFinalDamage(), this.currentBoss.getHealth());
            if (Double.isFinite(damage) && damage > 0.0) {
               Player attacker = this.playerAttacker(event.getDamager());
               this.bossDamageContributions.merge(attacker.getUniqueId(), damage, Double::sum);
               this.leaderboardManager.recordBossDamage(attacker, damage);
               if (this.currentBoss.getType() == EntityType.PHANTOM) {
                  this.revealPhantom(3000L);
               }

               if (this.currentBoss.getType() == EntityType.PHANTOM
                  && event.getDamager() instanceof Projectile projectile
                  && projectile.getShooter() instanceof Player) {
                  this.phantomRevealedUntil.put(this.currentBoss.getUniqueId(), System.currentTimeMillis() + 3000L);
                  this.currentBoss.removePotionEffect(PotionEffectType.INVISIBILITY);
               }
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onBossEffectDrain(EntityDamageEvent event) {
      if (event.getEntity() instanceof Player player
         && this.currentBoss != null
         && this.currentBoss.getType() == EntityType.WITCH
         && !this.currentBoss.isDead()
         && this.traitEnabled(this.activeBossConfig, "witchcraft-drain", true)) {
         DamageCause cause = event.getCause();
         if (cause == DamageCause.POISON || cause == DamageCause.WITHER || cause == DamageCause.MAGIC || cause == DamageCause.DRAGON_BREATH) {
            double radius = this.traitNumber(this.activeBossConfig, "witchcraft-drain", "radius", 16.0, 1.0, 64.0);
            if (player.getWorld().equals(this.currentBoss.getWorld())
               && !(player.getLocation().distanceSquared(this.currentBoss.getLocation()) > radius * radius)) {
               double heal = event.getFinalDamage() * this.traitNumber(this.activeBossConfig, "witchcraft-drain", "heal-percent", 0.5, 0.0, 1.0);
               if (Double.isFinite(heal) && heal > 0.0) {
                  this.currentBoss.setHealth(Math.min(this.currentBoss.getMaxHealth(), this.currentBoss.getHealth() + heal));
               }
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onBossDeathDrops(EntityDeathEvent event) {
      if (this.currentBoss == event.getEntity()) {
         event.setDroppedExp(0);
      }

      if (this.currentBoss == event.getEntity() && this.getRewardEligiblePlayers().isEmpty()) {
         event.getDrops().clear();
      }

      Player killer = event.getEntity().getKiller();
      if (killer != null
         && !this.bossSummons.contains(event.getEntity().getUniqueId())
         && event.getEntity()
            .getType()
            .name()
            .matches("ZOMBIE|HUSK|DROWNED|ZOMBIE_VILLAGER|SKELETON|STRAY|WITHER_SKELETON|PHANTOM|ZOGLIN|ZOMBIFIED_PIGLIN|WITHER")) {
         long now = System.currentTimeMillis();
         if (this.armorKillHealCooldowns.getOrDefault(killer.getUniqueId(), 0L) <= now) {
            for (ConfigManager.BossConfig boss : this.config.bossConfigs.values()) {
               if (boss.armorSet != null && boss.armorSet.storyId != null && this.countArmorSetPieces(killer, boss.armorSet.storyId) >= 4) {
                  ConfigManager.BossArmorBonusConfig bonus = boss.armorSet.bonuses.get(4);
                  if (bonus != null && !(bonus.healOnUndeadKill <= 0.0)) {
                     killer.setHealth(Math.min(killer.getMaxHealth(), killer.getHealth() + bonus.healOnUndeadKill));
                     this.armorKillHealCooldowns.put(killer.getUniqueId(), now + bonus.healCooldownSeconds * 1000L);
                     break;
                  }
               }
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onBossArmorSetDamage(EntityDamageEvent event) {
      if (event.getEntity() instanceof Player player) {
         double var9 = 0.0;

         for (ConfigManager.BossConfig boss : this.config.bossConfigs.values()) {
            if (boss.armorSet != null && boss.armorSet.storyId != null) {
               int pieces = this.countArmorSetPieces(player, boss.armorSet.storyId);
               ConfigManager.BossArmorBonusConfig bonus = this.highestArmorSetBonus(boss.armorSet, pieces);
               if (bonus != null
                  && !(bonus.damageReductionPercent <= var9)
                  && (bonus.damageCauses.isEmpty() || bonus.damageCauses.contains(event.getCause().name()))) {
                  var9 = bonus.damageReductionPercent;
               }
            }
         }

         if (var9 > 0.0) {
            event.setDamage(Math.max(0.0, event.getDamage() - event.getFinalDamage() * Math.min(1.0, var9)));
         }
      }
   }

   private Set<UUID> getRewardEligiblePlayers() {
      if (this.bossRewardThresholdHealth <= 0.0) {
         return Set.of();
      }

      double requiredDamage = this.bossRewardThresholdHealth * this.config.bossRewardDamageThresholdPercent / 100.0;
      Set<UUID> eligible = new HashSet<>();

      for (Entry<UUID, Double> contribution : this.bossDamageContributions.entrySet()) {
         if (contribution.getValue() > requiredDamage) {
            eligible.add(contribution.getKey());
         }
      }

      return eligible;
   }

   private Player playerAttacker(Entity damager) {
      Entity source = damager;
      if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
         source = shooter;
      }

      if (source instanceof Player player) {
         return player;
      } else {
         String owner = (String)source.getPersistentDataContainer().get(this.armyOwnerKey, PersistentDataType.STRING);

         try {
            return owner == null ? null : Bukkit.getPlayer(UUID.fromString(owner));
         } catch (IllegalArgumentException exception) {
            return null;
         }
      }
   }

   private String findSkillKey(ConfigManager.BossConfig bc, String... candidates) {
      if (bc != null && bc.skills != null && !bc.skills.isEmpty()) {
         for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
               for (String key : bc.skills.keySet()) {
                  if (key != null) {
                     if (key.equalsIgnoreCase(candidate)) {
                        return key;
                     }

                     if (key.replace("-", "").replace("_", "").equalsIgnoreCase(candidate.replace("-", "").replace("_", ""))) {
                        return key;
                     }
                  }
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private ConfigManager.BossSkillConfig getCreeperSkillConfig(ConfigManager.BossConfig bc) {
      String skillKey = this.findSkillKey(bc, "charged-explosion", "creeper-blast", "blast");
      return skillKey == null ? null : bc.skills.get(skillKey);
   }

   private double skillNumber(ConfigManager.BossSkillConfig skill, String key, double fallback, double minimum, double maximum) {
      return (skill == null ? null : skill.options.get(key)) instanceof Number number && Double.isFinite(number.doubleValue())
         ? Math.max(minimum, Math.min(maximum, number.doubleValue()))
         : fallback;
   }

   private int skillInteger(ConfigManager.BossSkillConfig skill, String key, int fallback, int minimum, int maximum) {
      return (skill == null ? null : skill.options.get(key)) instanceof Number number ? Math.max(minimum, Math.min(maximum, number.intValue())) : fallback;
   }

   private boolean skillBoolean(ConfigManager.BossSkillConfig skill, String key, boolean fallback) {
      return (skill == null ? null : skill.options.get(key)) instanceof Boolean enabled ? enabled : fallback;
   }

   private String skillString(ConfigManager.BossSkillConfig skill, String key, String fallback) {
      return (skill == null ? null : skill.options.get(key)) instanceof String text && !text.isBlank() ? text : fallback;
   }

   private ConfigManager.BossSkillConfig skillConfig(ConfigManager.BossConfig boss, String key) {
      return boss == null ? null : boss.skills.get(key);
   }

   private double traitNumber(ConfigManager.BossConfig boss, String trait, String key, double fallback, double minimum, double maximum) {
      return (boss == null ? null : boss.traits.getOrDefault(trait, Map.of())) instanceof Map<?, ?> settings
            && settings.get(key) instanceof Number number
            && Double.isFinite(number.doubleValue())
         ? Math.max(minimum, Math.min(maximum, number.doubleValue()))
         : fallback;
   }

   private boolean traitEnabled(ConfigManager.BossConfig boss, String trait, boolean fallback) {
      if ((boss == null ? null : boss.traits.getOrDefault(trait, Map.of())) instanceof Map<?, ?> settings) {
         return settings.get("enabled") instanceof Boolean flag ? flag : fallback;
      } else {
         return fallback;
      }
   }

   private List<String> skillStringList(ConfigManager.BossSkillConfig skill, String key) {
      return (skill == null ? null : skill.options.get(key)) instanceof List<?> entries
         ? entries.stream().filter(String.class::isInstance).map(String.class::cast).toList()
         : List.of();
   }

   private boolean isExtendedBossSkill(String key) {
      return switch (this.currentBoss.getType()) {
         case ENDERMAN -> key.equals("void-snatch") || key.equals("end-rift");
         case RAVAGER -> key.equals("siege-charge") || key.equals("roar-weakness");
         case PHANTOM -> key.equals("dive-bomb") || key.equals("phantom-minions");
         case GHAST -> key.equals("fireball-volley") || key.equals("fiery-drag");
         case SPIDER -> key.equals("cobweb-trap") || key.equals("venom-pounce");
         case VINDICATOR -> key.equals("axe-spin") || key.equals("axe-throw");
         case WITCH -> key.equals("potion-rain") || key.equals("disarm-magic");
         case GIANT -> key.equals("ground-slam") || key.equals("boulder-toss");
         default -> false;
      };
   }

   private boolean castExtendedBossSkill(
      String key,
      ConfigManager.BossSkillConfig skill,
      World world,
      LivingEntity target,
      Location bossLoc,
      Location targetLoc,
      Vector direction,
      double damage,
      double radius,
      double velocity,
      double spread,
      int count
   ) {
      return switch (key) {
         case "void-snatch" -> this.castVoidSnatch(skill, target, targetLoc, damage);
         case "end-rift" -> this.castEndRift(skill, world, targetLoc);
         case "siege-charge" -> this.castSiegeCharge(skill, target, direction, damage);
         case "roar-weakness" -> this.castRavagerRoar(skill, world, bossLoc, damage, radius);
         case "dive-bomb" -> this.castPhantomDive(skill, target, targetLoc, direction, damage, radius, velocity);
         case "phantom-minions" -> this.castPhantomMinions(skill, world, bossLoc);
         case "fireball-volley" -> this.currentBoss instanceof Ghast
            ? this.castGhastFireball(skill, world, bossLoc, target, targetLoc, direction, damage, radius, velocity)
            : false;
         case "fiery-drag" -> this.castFieryDrag(skill, world, bossLoc, damage, radius);
         case "cobweb-trap" -> this.castCobwebTrap(skill, world, bossLoc, targetLoc, direction, damage, radius, velocity);
         case "venom-pounce" -> this.castVenomPounce(skill, target, targetLoc, direction, damage, radius, velocity);
         case "axe-spin" -> this.castAxeSpin(skill, target, world, radius, damage);
         case "axe-throw" -> this.castAxeThrow(skill, target, direction, damage, velocity);
         case "potion-rain" -> this.castPotionRain(skill, world, bossLoc, targetLoc, direction, damage, radius, count);
         case "disarm-magic" -> this.castDisarm(skill, world, bossLoc, targetLoc, direction, damage, radius, velocity);
         case "ground-slam" -> this.castGiantGroundSlam(skill, world, bossLoc, targetLoc, direction, damage, radius);
         case "boulder-toss" -> this.castBoulderToss(skill, world, bossLoc, targetLoc, direction, damage, radius, velocity);
         default -> false;
      };
   }

   private void trackSkillTask(BukkitTask task) {
      if (task != null) {
         this.bossSkillTasks.add(task);
      }
   }

   private boolean castVoidSnatch(ConfigManager.BossSkillConfig skill, LivingEntity target, Location targetLoc, double damage) {
      if (!(target instanceof Player)) {
         return false;
      }

      Vector facing = targetLoc.getDirection().setY(0.0);
      if (facing.lengthSquared() < 1.0E-4) {
         facing = new Vector(0.0, 0.0, 1.0);
      }

      Location behind = targetLoc.clone().subtract(facing.normalize().multiply(this.skillNumber(skill, "behind-distance", 1.5, 0.5, 4.0)));
      behind.setY(targetLoc.getY());
      if (!behind.getBlock().isPassable() || !behind.clone().add(0.0, 1.0, 0.0).getBlock().isPassable()) {
         behind = this.currentBoss.getLocation().clone();
      }

      if (!this.currentBoss.teleport(behind)) {
         return false;
      }

      target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 9, false, true, true));
      target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0, false, true, true));
      target.damage(damage, this.currentBoss);
      this.currentBoss.getWorld().spawnParticle(Particle.PORTAL, behind.clone().add(0.0, 1.0, 0.0), 28, 0.45, 0.65, 0.45, 0.2);
      this.currentBoss.getWorld().playSound(behind, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 0.8F);
      return true;
   }

   private boolean castEndRift(ConfigManager.BossSkillConfig skill, final World world, final Location center) {
      final int length = this.skillInteger(skill, "length", 6, 2, 12);
      final int halfWidth = this.skillInteger(skill, "half-width", 1, 0, 2);
      final int warningTicks = this.skillInteger(skill, "warning-ticks", 30, 10, 100);
      world.playSound(center, Sound.BLOCK_PORTAL_AMBIENT, 0.8F, 0.7F);
      BukkitRunnable rift = new BukkitRunnable() {
         private int tick;

         public void run() {
            if (BossEventManager.this.bossActive && BossEventManager.this.currentBoss != null && !BossEventManager.this.currentBoss.isDead()) {
               for (int offset = -length; offset <= length; offset++) {
                  for (int side = -halfWidth; side <= halfWidth; side++) {
                     world.spawnParticle(Particle.DRAGON_BREATH, center.clone().add(offset, 0.12, side), 2, 0.08, 0.02, 0.08, 0.0);
                     world.spawnParticle(Particle.PORTAL, center.clone().add(side, 0.12, offset), 1, 0.05, 0.02, 0.05, 0.0);
                  }
               }

               if (++this.tick >= warningTicks) {
                  for (Entity entity : world.getNearbyEntities(center, length + 1.0, 2.5, length + 1.0)) {
                     if (entity instanceof Player player && !player.isDead()) {
                        double dx = Math.abs(player.getLocation().getX() - center.getX());
                        double dz = Math.abs(player.getLocation().getZ() - center.getZ());
                        boolean inCross = dx <= halfWidth + 0.5 && dz <= length || dz <= halfWidth + 0.5 && dx <= length;
                        if (inCross && !(Math.abs(player.getLocation().getY() - center.getY()) > 2.0)) {
                           player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 4, 0, false, true, true));
                           player.setVelocity(new Vector(player.getVelocity().getX(), 1.55, player.getVelocity().getZ()));
                        }
                     }
                  }

                  BossEventManager.this.bossSkillTasks.remove(this);
                  this.cancel();
               }
            } else {
               BossEventManager.this.bossSkillTasks.remove(this);
               this.cancel();
            }
         }
      };
      this.trackSkillTask(rift.runTaskTimer(this.plugin, 0L, 1L));
      return true;
   }

   private boolean castSiegeCharge(final ConfigManager.BossSkillConfig skill, LivingEntity target, Vector initialDirection, final double damage) {
      final Vector chargeDirection = initialDirection.clone().setY(0.0);
      if (chargeDirection.lengthSquared() < 1.0E-4) {
         return false;
      }

      chargeDirection.normalize();
      final int durationTicks = this.skillInteger(skill, "duration-seconds", 3, 1, 6) * 20;
      final int maxBlocks = this.skillInteger(skill, "max-break-blocks", 12, 0, 40);
      final double speed = this.skillNumber(skill, "charge-speed", 1.15, 0.4, 2.0);
      final List<Material> breakable = this.skillStringList(skill, "breakable-blocks")
         .stream()
         .<Material>map(Material::matchMaterial)
         .filter(Objects::nonNull)
         .toList();
      final Set<UUID> hitEntities = new HashSet<>();
      BukkitRunnable charge = new BukkitRunnable() {
         private int ticks;
         private int broken;

         public void run() {
            if (BossEventManager.this.bossActive
               && BossEventManager.this.currentBoss != null
               && !BossEventManager.this.currentBoss.isDead()
               && this.ticks++ < durationTicks) {
               Location next = BossEventManager.this.currentBoss.getLocation().add(chargeDirection.clone().multiply(0.7));
               Block obstacle = next.clone().add(0.0, 0.8, 0.0).getBlock();
               if (!obstacle.isPassable()) {
                  boolean canBreak = BossEventManager.this.skillBoolean(skill, "break-blocks", false)
                     && this.broken < maxBlocks
                     && breakable.contains(obstacle.getType())
                     && !obstacle.isLiquid()
                     && !BossEventManager.this.isBossBlockProtected(obstacle);
                  if (!canBreak) {
                     BossEventManager.this.bossSkillTasks.remove(this);
                     this.cancel();
                     return;
                  }

                  Material material = obstacle.getType();
                  FallingBlock falling = obstacle.getWorld().spawnFallingBlock(obstacle.getLocation().add(0.5, 0.0, 0.5), material.createBlockData());
                  falling.setDropItem(true);
                  falling.setHurtEntities(false);
                  falling.setVelocity(chargeDirection.clone().multiply(0.35).setY(0.35));
                  BossEventManager.this.bossTerrainEntities.add(falling.getUniqueId());
                  BossEventManager.this.bossTerrainDrops.put(falling.getUniqueId(), true);
                  BossEventManager.this.activeBossProjectiles.add(falling);
                  obstacle.setType(Material.AIR, false);
                  this.broken++;
               }

               BossEventManager.this.currentBoss
                  .setVelocity(chargeDirection.clone().multiply(speed).setY(BossEventManager.this.currentBoss.getVelocity().getY()));
               BossEventManager.this.currentBoss
                  .getWorld()
                  .spawnParticle(Particle.CLOUD, BossEventManager.this.currentBoss.getLocation(), 8, 0.45, 0.2, 0.45, 0.03);

               for (Entity entity : BossEventManager.this.currentBoss
                  .getWorld()
                  .getNearbyEntities(BossEventManager.this.currentBoss.getLocation(), 1.8, 1.4, 1.8)) {
                  if (entity instanceof Player victim && !victim.isDead() && hitEntities.add(victim.getUniqueId())) {
                     victim.damage(damage, BossEventManager.this.currentBoss);
                     Vector knockback = victim.getLocation().toVector().subtract(BossEventManager.this.currentBoss.getLocation().toVector()).setY(0.55);
                     if (knockback.lengthSquared() > 1.0E-4) {
                        victim.setVelocity(knockback.normalize().multiply(BossEventManager.this.skillNumber(skill, "knockback", 1.8, 0.5, 3.0)));
                     }
                  }
               }
            } else {
               BossEventManager.this.bossSkillTasks.remove(this);
               this.cancel();
            }
         }
      };
      this.currentBoss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationTicks, 4, false, true, true));
      this.trackSkillTask(charge.runTaskTimer(this.plugin, 0L, 1L));
      return true;
   }

   private boolean castRavagerRoar(ConfigManager.BossSkillConfig skill, World world, Location center, double damage, double radius) {
      radius = this.skillNumber(skill, "radius", 8.0, 2.0, 20.0);
      world.playSound(center, Sound.ENTITY_RAVAGER_ROAR, 1.5F, 0.8F);
      double range = radius;

      for (Entity entity : world.getNearbyEntities(center, range, range, range)) {
         if (entity instanceof Player player && !player.isDead() && !(player.getLocation().distanceSquared(center) > range * range)) {
            Vector push = player.getLocation().toVector().subtract(center.toVector());
            if (push.lengthSquared() < 1.0E-4) {
               push = new Vector(0.0, 0.0, 1.0);
            }

            player.setVelocity(push.normalize().multiply(this.skillNumber(skill, "knockback", 1.2, 0.3, 2.5)).setY(0.35));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2, false, true, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 1, false, true, true));
         }
      }

      world.spawnParticle(Particle.SONIC_BOOM, center.clone().add(0.0, 1.0, 0.0), 1);
      return true;
   }

   private boolean castPhantomDive(
      final ConfigManager.BossSkillConfig skill,
      final LivingEntity target,
      Location targetLoc,
      Vector direction,
      final double damage,
      final double radius,
      double velocity
   ) {
      Vector dive = direction.clone();
      if (dive.lengthSquared() < 1.0E-4) {
         dive = new Vector(0.0, -0.6, 0.0);
      }

      dive.normalize().multiply(velocity);
      dive.setY(Math.min(-0.45, dive.getY() - 0.35));
      this.currentBoss.setVelocity(dive);
      this.revealPhantom(3000L);
      BukkitRunnable impact = new BukkitRunnable() {
         private int ticks;

         public void run() {
            if (BossEventManager.this.bossActive
               && BossEventManager.this.currentBoss != null
               && !BossEventManager.this.currentBoss.isDead()
               && this.ticks++ < 50) {
               Location at = BossEventManager.this.currentBoss.getLocation();
               if (BossEventManager.this.currentBoss.isOnGround()) {
                  at.getWorld().spawnParticle(Particle.EXPLOSION, at, 2, 0.4, 0.2, 0.4, 0.0);
                  at.getWorld().createExplosion(at, 0.0F, false, false, BossEventManager.this.currentBoss);
                  double effectRadius = BossEventManager.this.skillNumber(skill, "blindness-radius", 5.0, 1.0, 12.0);

                  for (Entity entity : at.getWorld().getNearbyEntities(at, effectRadius, effectRadius, effectRadius)) {
                     if (entity instanceof Player player && player.getLocation().distanceSquared(at) <= effectRadius * effectRadius) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0, false, true, true));
                        if (player.equals(target) && player.getLocation().distanceSquared(at) <= radius * radius) {
                           player.damage(damage, BossEventManager.this.currentBoss);
                        }
                     }
                  }

                  BossEventManager.this.bossSkillTasks.remove(this);
                  this.cancel();
               }
            } else {
               BossEventManager.this.bossSkillTasks.remove(this);
               this.cancel();
            }
         }
      };
      this.trackSkillTask(impact.runTaskTimer(this.plugin, 1L, 1L));
      return true;
   }

   private void revealPhantom(long milliseconds) {
      if (this.currentBoss != null && this.currentBoss.getType() == EntityType.PHANTOM) {
         this.phantomRevealedUntil.put(this.currentBoss.getUniqueId(), System.currentTimeMillis() + milliseconds);
         this.currentBoss.removePotionEffect(PotionEffectType.INVISIBILITY);
      }
   }

   private boolean castPhantomMinions(ConfigManager.BossSkillConfig skill, World world, Location center) {
      int minimum = this.skillInteger(skill, "minions-min", 3, 1, 8);
      int maximum = this.skillInteger(skill, "minions-max", 5, minimum, 8);
      this.currentBoss.setVelocity(new Vector(this.currentBoss.getVelocity().getX(), 0.7, this.currentBoss.getVelocity().getZ()));
      int amount = this.random.nextInt(minimum, maximum + 1);

      for (int i = 0; i < amount; i++) {
         double angle = (Math.PI * 2) * i / amount;
         Location spawn = center.clone().add(Math.cos(angle) * 2.0, 2.0, Math.sin(angle) * 2.0);
         Phantom minion = (Phantom)world.spawn(spawn, Phantom.class);
         minion.setSize(1);
         minion.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 1200, 1, false, true, true));
         minion.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 1200, 0, false, true, true));
         this.bossSummons.add(minion.getUniqueId());
      }

      return true;
   }

   private boolean castGhastFireball(
      ConfigManager.BossSkillConfig skill,
      World world,
      Location bossLoc,
      LivingEntity target,
      Location targetLoc,
      Vector direction,
      double damage,
      double radius,
      double velocity
   ) {
      Vector aim = targetLoc.toVector().subtract(bossLoc.clone().add(0.0, 1.0, 0.0).toVector());
      if (aim.lengthSquared() < 1.0E-4) {
         aim = direction;
      }

      aim.normalize().multiply(velocity);
      Location launch = bossLoc.clone().add(0.0, 1.0, 0.0);
      LargeFireball fireball = (LargeFireball)world.spawn(launch, LargeFireball.class);
      fireball.setShooter(this.currentBoss);
      fireball.setDirection(aim);
      fireball.setVelocity(aim);
      fireball.setYield(0.0F);
      fireball.setIsIncendiary(false);
      this.registerBossProjectile(fireball, damage, radius, false);
      this.splitFireballs.put(fireball.getUniqueId(), launch.clone());
      return true;
   }

   private void splitGhastFireball(Location location, Vector incoming) {
      if (this.currentBoss != null && !this.currentBoss.isDead() && this.currentBoss.getType() == EntityType.GHAST) {
         ConfigManager.BossSkillConfig skill = this.skillConfig(this.activeBossConfig, "fireball-volley");
         int count = this.skillInteger(skill, "split-count", 6, 5, 8);
         double velocity = this.skillNumber(skill, "split-velocity", 1.2, 0.4, 3.0);
         double damage = this.skillNumber(skill, "damage", 8.0, 0.0, 1024.0) * Math.max(1, this.currentBossLevel);
         double radius = this.skillNumber(skill, "radius", 5.0, 1.0, 64.0);
         Vector base = incoming.lengthSquared() < 1.0E-4 ? this.currentBoss.getLocation().getDirection() : incoming.clone();
         base.normalize();

         for (int i = 0; i < count; i++) {
            double offset = (i - (count - 1) / 2.0) * this.skillNumber(skill, "split-spread", 0.3, 0.05, 1.0);
            Vector shot = base.clone().rotateAroundY(offset);
            shot.setY(Math.max(-0.4, Math.min(0.5, shot.getY() + (this.random.nextDouble() - 0.5) * 0.25))).normalize().multiply(velocity);
            SmallFireball small = (SmallFireball)location.getWorld().spawn(location, SmallFireball.class);
            small.setShooter(this.currentBoss);
            small.setDirection(shot);
            small.setVelocity(shot);
            small.setIsIncendiary(false);
            this.registerBossProjectile(small, damage, radius, false);
         }
      }
   }

   private boolean castFieryDrag(ConfigManager.BossSkillConfig skill, World world, Location center, double damage, double radius) {
      double range = this.skillNumber(skill, "radius", 10.0, 2.0, 24.0);

      for (Entity entity : world.getNearbyEntities(center, range, range, range)) {
         if (entity instanceof Player player && !player.isDead() && !(player.getLocation().distanceSquared(center) > range * range)) {
            Vector pull = center.toVector().subtract(player.getLocation().toVector());
            if (pull.lengthSquared() > 1.0E-4) {
               player.setVelocity(pull.normalize().multiply(this.skillNumber(skill, "pull-speed", 0.8, 0.2, 1.5)).setY(0.2));
            }

            if (this.skillBoolean(skill, "terrain-enabled", true)) {
               Block feet = player.getLocation().getBlock();
               Block below = feet.getRelative(0, -1, 0);
               Material fireFloor = Material.matchMaterial(this.skillString(skill, "terrain-material", "MAGMA_BLOCK"));
               if (fireFloor != Material.MAGMA_BLOCK && fireFloor != Material.FIRE) {
                  fireFloor = Material.MAGMA_BLOCK;
               }

               Block hazard = fireFloor == Material.FIRE ? feet : below;
               List<String> configuredReplaceables = this.skillStringList(skill, "replaceable-blocks");
               Set<Material> defaultReplaceables = EnumSet.of(
                  Material.DIRT,
                  Material.GRASS_BLOCK,
                  Material.STONE,
                  Material.COBBLESTONE,
                  Material.DEEPSLATE,
                  Material.NETHERRACK,
                  Material.SAND,
                  Material.RED_SAND,
                  Material.GRAVEL
               );
               boolean replaceable = configuredReplaceables.isEmpty()
                  ? defaultReplaceables.contains(hazard.getType())
                  : configuredReplaceables.stream().anyMatch(name -> name.equalsIgnoreCase(hazard.getType().name()));
               if (hazard.getType().isAir()
                  || hazard.isPassable()
                  || fireFloor == Material.MAGMA_BLOCK && replaceable && !(hazard.getState() instanceof TileState)) {
                  this.placeTemporaryBlock(hazard, fireFloor, this.skillInteger(skill, "terrain-duration-seconds", 5, 1, 30));
               }
            }
         }
      }

      world.spawnParticle(Particle.FLAME, center, 24, radius * 0.4, 0.3, radius * 0.4, 0.02);
      return true;
   }

   private boolean castCobwebTrap(
      ConfigManager.BossSkillConfig skill, World world, Location bossLoc, Location targetLoc, Vector direction, double damage, double radius, double velocity
   ) {
      Snowball projectile = (Snowball)world.spawn(bossLoc.clone().add(0.0, 1.0, 0.0), Snowball.class);
      projectile.setShooter(this.currentBoss);
      Vector shot = targetLoc.toVector().subtract(projectile.getLocation().toVector());
      if (shot.lengthSquared() < 1.0E-4) {
         shot = direction;
      }

      projectile.setVelocity(shot.normalize().multiply(velocity));
      this.webTrapProjectiles.add(projectile.getUniqueId());
      this.activeBossProjectiles.add(projectile);
      return true;
   }

   private void placeTemporaryWebTrap(Location impact, ConfigManager.BossSkillConfig skill) {
      int radius = this.skillInteger(skill, "trap-radius", 1, 1, 2);
      int duration = this.skillInteger(skill, "trap-duration-seconds", 6, 1, 30);
      Block center = impact.getBlock();
      if (center.getType().isAir() || center.isPassable()) {
         center = center.getRelative(0, -1, 0);
      }

      for (int x = -radius; x <= radius; x++) {
         for (int z = -radius; z <= radius; z++) {
            Block block = center.getRelative(x, 1, z);
            if (block.getType().isAir() || block.isPassable()) {
               this.placeTemporaryBlock(block, Material.COBWEB, duration);
            }
         }
      }
   }

   private void placeTemporaryBlock(Block block, Material material, int durationSeconds) {
      if (!this.isBossBlockProtected(block)) {
         BossEventManager.BlockPosition key = new BossEventManager.BlockPosition(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
         boolean replaceableFloor = material == Material.MAGMA_BLOCK
            && block.getType().isSolid()
            && !block.getType().isAir()
            && !(block.getState() instanceof TileState)
            && block.getType() != Material.BEDROCK
            && block.getType() != Material.BARRIER;
         if (!this.temporaryBlocks.containsKey(key) && (block.getType().isAir() || replaceableFloor)) {
            BlockState original = block.getState();
            block.setType(material, false);
            this.temporaryBlocks.put(key, new BossEventManager.TemporaryBlock(original, block.getBlockData().clone()));
            BukkitTask expiry = Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.restoreTemporaryBlock(key), durationSeconds * 20L);
            this.trackSkillTask(expiry);
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onPlayerBreakTemporaryBlock(BlockBreakEvent event) {
      this.markPlayerModifiedTemporaryBlock(event.getBlock());
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onPlayerPlaceTemporaryBlock(BlockPlaceEvent event) {
      this.markPlayerModifiedTemporaryBlock(event.getBlockPlaced());
   }

   private void markPlayerModifiedTemporaryBlock(Block block) {
      BossEventManager.BlockPosition key = new BossEventManager.BlockPosition(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
      if (this.temporaryBlocks.containsKey(key)) {
         this.playerModifiedTemporaryBlocks.add(key);
      }
   }

   private boolean isBossBlockProtected(Block block) {
      return this.plugin.getSpawnProtectionManager() != null && this.plugin.getSpawnProtectionManager().isProtected(block.getLocation());
   }

   private void restoreTemporaryBlock(BossEventManager.BlockPosition key) {
      BossEventManager.TemporaryBlock temporary = this.temporaryBlocks.remove(key);
      if (temporary != null) {
         World world = Bukkit.getWorld(key.worldId());
         if (world != null) {
            Block block = world.getBlockAt(key.x(), key.y(), key.z());
            boolean playerModified = this.playerModifiedTemporaryBlocks.remove(key);
            if (!playerModified && temporary.placedData().matches(block.getBlockData())) {
               temporary.original().update(true, false);
            }
         }
      }
   }

   private void detonateBoulder(Location impact) {
      if (this.currentBoss != null && !this.currentBoss.isDead()) {
         ConfigManager.BossSkillConfig skill = this.skillConfig(this.activeBossConfig, "boulder-toss");
         double radius = skill == null ? 3.0 : this.safeSkillValue(skill.radius, 3.0, 1.0, 64.0);
         double damage = skill == null ? 12.0 : this.safeSkillValue(skill.damage, 12.0, 0.0, 1024.0) * Math.max(1, this.currentBossLevel);
         impact.getWorld().spawnParticle(Particle.EXPLOSION, impact, 2);
         impact.getWorld().playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.2F, 0.7F);
         impact.getWorld().createExplosion(impact, 0.0F, false, false, this.currentBoss);

         for (Entity entity : impact.getWorld().getNearbyEntities(impact, radius, radius, radius)) {
            if (entity instanceof Player victim && !victim.isDead() && victim.getLocation().distanceSquared(impact) <= radius * radius) {
               victim.damage(damage, this.currentBoss);
            }
         }
      }
   }

   private boolean castVenomPounce(
      ConfigManager.BossSkillConfig skill, LivingEntity target, Location targetLoc, Vector direction, final double damage, final double radius, double velocity
   ) {
      if (Math.abs(targetLoc.getY() - this.currentBoss.getLocation().getY()) > radius) {
         return false;
      }

      Vector leap = direction.clone().normalize().multiply(velocity);
      leap.setY(this.skillNumber(skill, "jump-y", 0.75, 0.3, 1.5));
      this.currentBoss.setVelocity(leap);
      BukkitRunnable landing = new BukkitRunnable() {
         private int ticks;
         private boolean hit;

         public void run() {
            if (!BossEventManager.this.bossActive
               || BossEventManager.this.currentBoss == null
               || BossEventManager.this.currentBoss.isDead()
               || this.hit
               || this.ticks++ >= 50) {
               BossEventManager.this.bossSkillTasks.remove(this);
               this.cancel();
            } else if (BossEventManager.this.currentBoss.isOnGround()) {
               for (Entity entity : BossEventManager.this.currentBoss
                  .getWorld()
                  .getNearbyEntities(BossEventManager.this.currentBoss.getLocation(), radius, radius, radius)) {
                  if (entity instanceof Player player
                     && !player.isDead()
                     && !(player.getLocation().distanceSquared(BossEventManager.this.currentBoss.getLocation()) > radius * radius)) {
                     player.damage(
                        BossEventManager.this.spiderPlayerHitDamage(BossEventManager.this.activeBossConfig, damage), BossEventManager.this.currentBoss
                     );
                     player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 120, 1, false, true, true));
                     player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 120, 2, false, true, true));
                     this.hit = true;
                     break;
                  }
               }

               if (this.hit || BossEventManager.this.currentBoss.isOnGround() && this.ticks > 4 || this.ticks >= 45) {
                  BossEventManager.this.bossSkillTasks.remove(this);
                  this.cancel();
               }
            }
         }
      };
      this.trackSkillTask(landing.runTaskTimer(this.plugin, 1L, 1L));
      return true;
   }

   private boolean castAxeSpin(
      final ConfigManager.BossSkillConfig skill, final LivingEntity target, final World world, final double radius, final double damage
   ) {
      final int durationTicks = this.skillInteger(skill, "duration-seconds", 3, 1, 6) * 20;
      final long hitCooldownMs = this.skillInteger(skill, "hit-interval-ticks", 10, 5, 40) * 50L;
      BukkitRunnable spin = new BukkitRunnable() {
         private int ticks;
         private float yaw = BossEventManager.this.currentBoss.getLocation().getYaw();

         public void run() {
            if (BossEventManager.this.bossActive
               && BossEventManager.this.currentBoss != null
               && !BossEventManager.this.currentBoss.isDead()
               && this.ticks++ < durationTicks) {
               this.yaw += 45.0F;
               Location at = BossEventManager.this.currentBoss.getLocation();
               BossEventManager.this.currentBoss.setRotation(this.yaw, at.getPitch());
               Vector approach = target.getLocation().toVector().subtract(at.toVector()).setY(0.0);
               if (approach.lengthSquared() > 1.0) {
                  BossEventManager.this.currentBoss.setVelocity(approach.normalize().multiply(0.22));
               }

               world.spawnParticle(Particle.SWEEP_ATTACK, at.clone().add(0.0, 1.0, 0.0), 3, radius * 0.4, 0.35, radius * 0.4, 0.0);

               for (Entity entity : world.getNearbyEntities(at, radius, 1.5, radius)) {
                  if (entity instanceof Player player
                     && !player.isDead()
                     && !(player.getLocation().distanceSquared(at) > radius * radius)
                     && BossEventManager.this.spinDamageCooldowns.getOrDefault(player.getUniqueId(), 0L) <= System.currentTimeMillis()) {
                     player.damage(damage, BossEventManager.this.currentBoss);
                     if (player.isBlocking()) {
                        player.setCooldown(Material.SHIELD, BossEventManager.this.skillInteger(skill, "shield-cooldown-ticks", 100, 20, 200));
                     }

                     BossEventManager.this.spinDamageCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + hitCooldownMs);
                  }
               }
            } else {
               BossEventManager.this.bossSkillTasks.remove(this);
               this.cancel();
            }
         }
      };
      this.trackSkillTask(spin.runTaskTimer(this.plugin, 0L, 1L));
      return true;
   }

   private boolean castAxeThrow(ConfigManager.BossSkillConfig skill, LivingEntity target, Vector direction, final double damage, final double velocity) {
      final LivingEntity owner = this.currentBoss;
      EntityEquipment equipment = owner.getEquipment();
      final ItemStack originalWeapon = equipment != null && !equipment.getItemInMainHand().getType().isAir() ? equipment.getItemInMainHand().clone() : null;
      if (originalWeapon != null) {
         equipment.setItemInMainHand(new ItemStack(Material.AIR));
      }

      final ArmorStand axe = (ArmorStand)this.currentBoss.getWorld().spawn(this.currentBoss.getLocation().add(0.0, 1.0, 0.0), ArmorStand.class);
      axe.setVisible(false);
      axe.setArms(true);
      axe.setMarker(true);
      axe.setGravity(false);
      axe.setInvulnerable(true);
      axe.setSilent(true);
      axe.setCollidable(false);
      axe.getEquipment()
         .setItemInMainHand(
            new ItemStack(
               Material.matchMaterial(this.skillString(skill, "axe-material", "IRON_AXE")) == null
                  ? Material.IRON_AXE
                  : Material.matchMaterial(this.skillString(skill, "axe-material", "IRON_AXE"))
            )
         );
      this.bossSummons.add(axe.getUniqueId());
      final Vector outbound = direction.clone().normalize().multiply(velocity);
      int maxDistance = this.skillInteger(skill, "max-distance", 16, 6, 32);
      final int maxTicks = Math.max(6, Math.min(80, (int)Math.ceil(maxDistance / Math.max(0.2, velocity))));
      BukkitRunnable flight = new BukkitRunnable() {
         private int ticks;
         private boolean returning;
         private final Set<UUID> outwardHits = new HashSet<>();
         private final Set<UUID> returnHits = new HashSet<>();

         private void restoreWeapon() {
            if (originalWeapon != null
               && owner.isValid()
               && !owner.isDead()
               && owner.getEquipment() != null
               && owner.getEquipment().getItemInMainHand().getType().isAir()) {
               owner.getEquipment().setItemInMainHand(originalWeapon);
            }
         }

         public void run() {
            if (BossEventManager.this.bossActive
               && BossEventManager.this.currentBoss != null
               && !BossEventManager.this.currentBoss.isDead()
               && axe.isValid()
               && this.ticks++ < maxTicks * 3) {
               if (!this.returning && this.ticks >= maxTicks) {
                  this.returning = true;
               }

               Vector travel;
               if (this.returning) {
                  travel = BossEventManager.this.currentBoss.getLocation().toVector().subtract(axe.getLocation().toVector());
                  if (travel.lengthSquared() < 1.0) {
                     this.restoreWeapon();
                     BossEventManager.this.bossSummons.remove(axe.getUniqueId());
                     axe.remove();
                     BossEventManager.this.bossSkillTasks.remove(this);
                     this.cancel();
                     return;
                  }

                  travel.normalize().multiply(velocity);
               } else {
                  travel = outbound;
               }

               Location next = axe.getLocation().add(travel);
               if (!next.getBlock().isPassable()) {
                  this.returning = true;
               }

               axe.teleport(next);
               axe.setRightArmPose(new EulerAngle(this.ticks * 0.55, 0.0, 0.0));

               for (Entity entity : axe.getWorld().getNearbyEntities(next, 0.8, 0.8, 0.8)) {
                  if (entity instanceof Player player && !player.isDead()) {
                     Set<UUID> passHits = this.returning ? this.returnHits : this.outwardHits;
                     if (passHits.add(player.getUniqueId())) {
                        player.damage(damage, BossEventManager.this.currentBoss);
                     }
                  }
               }
            } else {
               this.restoreWeapon();
               BossEventManager.this.bossSummons.remove(axe.getUniqueId());
               axe.remove();
               BossEventManager.this.bossSkillTasks.remove(this);
               this.cancel();
            }
         }
      };
      this.trackSkillTask(flight.runTaskTimer(this.plugin, 0L, 1L));
      return true;
   }

   private boolean castPotionRain(
      ConfigManager.BossSkillConfig skill, World world, Location bossLoc, Location targetLoc, Vector direction, double damage, double radius, int count
   ) {
      int potionCount = this.skillInteger(skill, "potion-count", 8, 3, 16);

      for (int i = 0; i < potionCount; i++) {
         ThrownPotion potion = (ThrownPotion)world.spawn(bossLoc.clone().add(0.0, 1.2, 0.0), ThrownPotion.class);
         ItemStack potionItem = new ItemStack(Material.SPLASH_POTION);
         PotionMeta meta = (PotionMeta)potionItem.getItemMeta();
         meta.addCustomEffect(new PotionEffect(PotionEffectType.INSTANT_DAMAGE, 1, 0), true);
         meta.addCustomEffect(new PotionEffect(PotionEffectType.POISON, 100, 0), true);
         meta.addCustomEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1), true);
         potionItem.setItemMeta(meta);
         potion.setItem(potionItem);
         potion.setShooter(this.currentBoss);
         Vector launch = direction.clone()
            .rotateAroundY((this.random.nextDouble() - 0.5) * this.skillNumber(skill, "spread", 0.8, 0.0, Math.PI))
            .setY(this.skillNumber(skill, "launch-y", 0.55, 0.1, 1.5))
            .normalize()
            .multiply(this.skillNumber(skill, "velocity", 1.0, 0.2, 2.0));
         potion.setVelocity(launch);
         this.registerBossProjectile(potion, damage, radius, true);
      }

      return true;
   }

   private boolean castDisarm(
      ConfigManager.BossSkillConfig skill, World world, Location bossLoc, Location targetLoc, Vector direction, double damage, double radius, double velocity
   ) {
      Snowball projectile = (Snowball)world.spawn(bossLoc.clone().add(0.0, 1.0, 0.0), Snowball.class);
      projectile.setShooter(this.currentBoss);
      Vector shot = targetLoc.toVector().subtract(projectile.getLocation().toVector());
      if (shot.lengthSquared() < 1.0E-4) {
         shot = direction;
      }

      projectile.setVelocity(shot.normalize().multiply(velocity));
      this.disarmProjectiles.add(projectile.getUniqueId());
      this.registerBossProjectile(projectile, damage, radius, false);
      return true;
   }

   private void disarmPlayer(Player player, ConfigManager.BossSkillConfig skill) {
      int selected = player.getInventory().getHeldItemSlot();
      int replacement = -1;

      for (int slot = 0; slot < 9; slot++) {
         if (slot != selected && player.getInventory().getItem(slot) == null) {
            replacement = slot;
            break;
         }
      }

      if (replacement < 0) {
         for (int slot = 0; slot < 9; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (slot != selected && item != null && item.getType() == Material.CARROT) {
               replacement = slot;
               break;
            }
         }
      }

      if (replacement >= 0) {
         player.getInventory().setHeldItemSlot(replacement);
      }

      player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, this.skillInteger(skill, "fatigue-ticks", 80, 20, 200), 4, false, true, true));
   }

   private boolean castGiantGroundSlam(
      ConfigManager.BossSkillConfig skill, World world, Location bossLoc, Location targetLoc, Vector direction, double damage, double radius
   ) {
      Vector path = direction.clone().setY(0.0);
      if (path.lengthSquared() < 1.0E-4) {
         return false;
      }

      path.normalize();
      int length = this.skillInteger(skill, "line-length", 15, 4, 24);
      Set<UUID> hitPlayers = new HashSet<>();

      for (int step = 1; step <= length; step++) {
         Location point = bossLoc.clone().add(path.clone().multiply(step));
         point.setY(world.getHighestBlockYAt(point) + 0.15);
         world.spawnParticle(Particle.EXPLOSION, point, 1, 0.15, 0.05, 0.15, 0.0);
         world.spawnParticle(Particle.BLOCK, point, 8, 0.3, 0.15, 0.3, 0.0, point.clone().add(0.0, -0.1, 0.0).getBlock().getBlockData());

         for (Entity entity : world.getNearbyEntities(point, 1.25, 1.0, 1.25)) {
            if (entity instanceof Player victim
               && !victim.isDead()
               && !(victim.getLocation().distanceSquared(point) > 2.25)
               && hitPlayers.add(victim.getUniqueId())) {
               victim.damage(damage, this.currentBoss);
               victim.setVelocity(new Vector(path.getX() * 0.5, 0.85, path.getZ() * 0.5));
            }
         }
      }

      world.playSound(bossLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.5F, 0.6F);
      return true;
   }

   private boolean castBoulderToss(
      ConfigManager.BossSkillConfig skill, World world, Location bossLoc, Location targetLoc, Vector direction, double damage, double radius, double velocity
   ) {
      final FallingBlock boulder = world.spawnFallingBlock(bossLoc.clone().add(0.0, 3.0, 0.0), Material.COBBLESTONE.createBlockData());
      boulder.setDropItem(false);
      boulder.setHurtEntities(false);
      boulder.setVelocity(direction.clone().normalize().multiply(velocity));
      this.bossTerrainEntities.add(boulder.getUniqueId());
      this.bossTerrainDrops.put(boulder.getUniqueId(), false);
      this.bossBoulders.add(boulder.getUniqueId());
      this.activeBossProjectiles.add(boulder);
      BukkitRunnable impact = new BukkitRunnable() {
         private int ticks;

         public void run() {
            if (BossEventManager.this.bossActive
               && BossEventManager.this.currentBoss != null
               && !BossEventManager.this.currentBoss.isDead()
               && boulder.isValid()
               && this.ticks++ < 100) {
               if (boulder.isOnGround() || boulder.getVelocity().lengthSquared() < 0.03) {
                  Location impactx = boulder.getLocation();
                  BossEventManager.this.detonateBoulder(impactx);
                  BossEventManager.this.bossTerrainEntities.remove(boulder.getUniqueId());
                  BossEventManager.this.activeBossProjectiles.remove(boulder);
                  BossEventManager.this.bossTerrainDrops.remove(boulder.getUniqueId());
                  BossEventManager.this.bossBoulders.remove(boulder.getUniqueId());
                  boulder.remove();
                  BossEventManager.this.bossSkillTasks.remove(this);
                  this.cancel();
               }
            } else {
               BossEventManager.this.bossTerrainEntities.remove(boulder.getUniqueId());
               BossEventManager.this.activeBossProjectiles.remove(boulder);
               BossEventManager.this.bossTerrainDrops.remove(boulder.getUniqueId());
               BossEventManager.this.bossBoulders.remove(boulder.getUniqueId());
               boulder.remove();
               BossEventManager.this.bossSkillTasks.remove(this);
               this.cancel();
            }
         }
      };
      this.trackSkillTask(impact.runTaskTimer(this.plugin, 1L, 1L));
      return true;
   }

   private boolean useBossSkill(ConfigManager.BossConfig bc, LivingEntity target) {
      if (this.currentBoss != null && !this.currentBoss.isDead() && bc != null && target != null && bc.skills != null && !bc.skills.isEmpty()) {
         List<String> candidates = new ArrayList<>();
         switch (this.currentBoss.getType()) {
            case ENDERMAN:
               candidates.add(this.findSkillKey(bc, "void-snatch", "shadow-strike", "enderman-teleport", "teleport"));
               candidates.add(this.findSkillKey(bc, "end-rift"));
               break;
            case RAVAGER:
               candidates.add(this.findSkillKey(bc, "siege-charge", "charge", "ravager-charge"));
               candidates.add(this.findSkillKey(bc, "roar-weakness"));
               break;
            case PHANTOM:
               candidates.add(this.findSkillKey(bc, "dive-bomb", "phantom-dive", "dive"));
               candidates.add(this.findSkillKey(bc, "phantom-minions"));
               break;
            case GHAST:
               candidates.add(this.findSkillKey(bc, "fireball-volley", "ghast-fireball", "fireball"));
               candidates.add(this.findSkillKey(bc, "fiery-drag"));
               break;
            case SPIDER:
               candidates.add(this.findSkillKey(bc, "cobweb-trap"));
               candidates.add(this.findSkillKey(bc, "venom-pounce"));
               candidates.add(this.findSkillKey(bc, "venom-web", "spider-web", "spider-poison", "web"));
               break;
            case VINDICATOR:
               candidates.add(this.findSkillKey(bc, "axe-spin", "vindicator-shockwave", "vindicator_shockwave", "shockwave"));
               candidates.add(this.findSkillKey(bc, "axe-throw"));
               break;
            case WITCH:
               candidates.add(this.findSkillKey(bc, "potion-rain", "potion-volley", "witch-potion", "potion"));
               candidates.add(this.findSkillKey(bc, "disarm-magic"));
               break;
            case GIANT:
               candidates.add(this.findSkillKey(bc, "ground-slam", "stomp", "giant-stomp"));
               candidates.add(this.findSkillKey(bc, "boulder-toss"));
               break;
            case SKELETON:
               candidates.add(this.findSkillKey(bc, "arrow-barrage", "skeleton-arrow", "arrow-fan", "fire-arrow-fan"));
               break;
            case ENDER_DRAGON:
               candidates.add(this.findSkillKey(bc, "fireball-volley", "dragon-fireball", "ender-dragon-fireball", "fireball-fan"));
               break;
            case CREEPER:
               candidates.add(this.findSkillKey(bc, "charged-explosion", "creeper-blast", "blast"));
               break;
            case WITHER:
               candidates.add(this.findSkillKey(bc, "skull-volley", "wither-skull", "skull"));
               break;
            case ZOMBIE:
               candidates.add(this.findSkillKey(bc, "ground-slam", "zombie-slam", "slam"));
         }

         double targetDistance = this.currentBoss.getLocation().distance(target.getLocation());
         int combatPhase = this.bossCombatPhase();
         if (this.config.bossAiEnabled && this.config.bossAiPhasesEnabled && combatPhase >= 2) {
            candidates.sort((first, second) -> Integer.compare(this.skillPhasePriority(second, combatPhase), this.skillPhasePriority(first, combatPhase)));
         }

         long now = System.currentTimeMillis();
         String skillKey = null;
         ConfigManager.BossSkillConfig skill = null;

         for (String candidate : candidates) {
            if (candidate != null) {
               ConfigManager.BossSkillConfig option = bc.skills.get(candidate);
               if (option != null && option.enabled && (!this.config.bossAiEnabled || this.isSkillSuitableForDistance(candidate, option, targetDistance))) {
                  Long lastUse = this.bossSkillCooldowns.get(this.currentBoss.getUniqueId() + ":" + candidate);
                  if (lastUse == null || now - lastUse >= (long)(this.safeSkillValue(option.cooldownSeconds, 8.0, 1.0, 86400.0) * 1000.0)) {
                     skillKey = candidate;
                     skill = option;
                     break;
                  }
               }
            }
         }

         if (skillKey != null && skill != null) {
            World world = this.currentBoss.getWorld();
            if (world == null) {
               return false;
            }

            double multiply = this.safeSkillValue(skill.multiply, 1.0, 0.0, 40.0);
            if (multiply <= 0.0) {
               return false;
            }

            int castCount = (int)Math.max(1.0, Math.min(40.0, Math.ceil(this.currentBossLevel * multiply)));
            double skillDamage = this.safeSkillValue(skill.damage, 0.0, 0.0, 1024.0) * Math.max(1, this.currentBossLevel);
            double skillRadius = this.safeSkillValue(skill.radius, 1.0, 1.0, 64.0);
            double skillVelocity = this.safeSkillValue(skill.velocity, 1.0, 0.2, 4.0);
            double skillSpread = this.safeSkillValue(skill.spread, 0.0, 0.0, Math.PI);
            Location bossLoc = this.currentBoss.getLocation();
            Location targetLoc = target.getLocation();
            Vector dir = targetLoc.toVector().subtract(bossLoc.toVector());
            if (dir.lengthSquared() < 1.0E-4) {
               dir = this.currentBoss.getLocation().getDirection();
            }

            dir.normalize();
            if (this.isExtendedBossSkill(skillKey)) {
               boolean used = this.castExtendedBossSkill(
                  skillKey, skill, world, target, bossLoc, targetLoc, dir, skillDamage, skillRadius, skillVelocity, skillSpread, castCount
               );
               if (used) {
                  this.completeBossSkillCast(skillKey, target, now);
               }

               return used;
            } else {
               switch (this.currentBoss.getType()) {
                  case ENDERMAN:
                     if (target == null) {
                        return false;
                     } else {
                        Location tp = target.getLocation().clone().add((Math.random() - 0.5) * 4.0, 0.0, (Math.random() - 0.5) * 4.0);
                        tp.setY(world.getHighestBlockYAt(tp) + 1.0);
                        if (tp.getBlock().getType().isAir() && this.currentBoss.teleport(tp)) {
                           world.spawnParticle(Particle.PORTAL, tp.clone().add(0.0, 1.0, 0.0), 24, 0.4, 0.6, 0.4, 0.2);
                           world.playSound(tp, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 0.8F);
                           if (target.getLocation().distanceSquared(tp) <= skillRadius * skillRadius) {
                              target.damage(skillDamage, this.currentBoss);
                           }

                           return this.completeBossSkillCast(skillKey, target, now);
                        }

                        return false;
                     }
                  case RAVAGER:
                     if (target != null) {
                        Vector charge = targetLoc.toVector().subtract(bossLoc.toVector()).normalize();
                        this.currentBoss.setVelocity(charge.multiply(skillVelocity > 0.0 ? skillVelocity : 1.3));
                        this.currentBoss.setRotation((float)Math.toDegrees(Math.atan2(charge.getZ(), charge.getX())), 0.0F);
                     }

                     for (Entity entity : world.getNearbyEntities(bossLoc, skillRadius, skillRadius, skillRadius)) {
                        if (entity instanceof LivingEntity living
                           && !entity.equals(this.currentBoss)
                           && living.getLocation().distanceSquared(bossLoc) <= skillRadius * skillRadius) {
                           living.damage(skillDamage, this.currentBoss);
                           Vector knock = living.getLocation().toVector().subtract(bossLoc.toVector()).normalize();
                           knock.setY(0.65);
                           living.setVelocity(knock.multiply(skillVelocity));
                        }
                     }

                     return this.completeBossSkillCast(skillKey, target, now);
                  case PHANTOM:
                     Vector dive = targetLoc.toVector().subtract(bossLoc.toVector()).normalize();
                     dive.setY(Math.max(0.2, dive.getY() + 0.25));
                     this.currentBoss.setVelocity(dive.multiply(skillVelocity > 0.0 ? skillVelocity : 1.6));
                     if (target.getLocation().distanceSquared(bossLoc) <= skillRadius * skillRadius) {
                        target.damage(skillDamage, this.currentBoss);
                     }

                     return this.completeBossSkillCast(skillKey, target, now);
                  case GHAST:
                     for (int i = 0; i < castCount; i++) {
                        Vector fireballDir = dir.clone();
                        if (skillSpread > 0.0) {
                           fireballDir.rotateAroundY((this.random.nextDouble() - 0.5) * skillSpread);
                        }

                        LargeFireball fireball = (LargeFireball)world.spawn(bossLoc.clone().add(0.0, 2.0, 0.0), LargeFireball.class);
                        fireball.setDirection(fireballDir.normalize().multiply(skillVelocity));
                        fireball.setYield(0.0F);
                        fireball.setIsIncendiary(false);
                        fireball.setShooter(this.currentBoss);
                        this.registerBossProjectile(fireball, skillDamage, skillRadius, false);
                     }

                     return this.completeBossSkillCast(skillKey, target, now);
                  case SPIDER:
                     if (bossLoc.distanceSquared(targetLoc) > skillRadius * skillRadius) {
                        return false;
                     } else {
                        double radiusSquared = skillRadius * skillRadius;

                        for (Entity entity : world.getNearbyEntities(targetLoc, skillRadius, skillRadius, skillRadius)) {
                           if (entity instanceof LivingEntity living
                              && !living.equals(this.currentBoss)
                              && !living.isDead()
                              && !(living.getLocation().distanceSquared(targetLoc) > radiusSquared)) {
                              double damage = living instanceof Player ? this.spiderPlayerHitDamage(bc, skillDamage) : skillDamage;
                              living.damage(damage, this.currentBoss);
                           }
                        }

                        for (Entity entity : world.getNearbyEntities(targetLoc, skillRadius, skillRadius, skillRadius)) {
                           if (entity instanceof LivingEntity living
                              && !living.equals(this.currentBoss)
                              && living.getLocation().distanceSquared(targetLoc) <= skillRadius * skillRadius) {
                              living.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, Math.max(0, this.currentBossLevel / 3), false, false));
                           }
                        }

                        return this.completeBossSkillCast(skillKey, target, now);
                     }
                  case VINDICATOR:
                     for (Entity entity : world.getNearbyEntities(bossLoc, skillRadius, skillRadius, skillRadius)) {
                        if (entity instanceof LivingEntity living
                           && !entity.equals(this.currentBoss)
                           && living.getLocation().distanceSquared(bossLoc) <= skillRadius * skillRadius) {
                           living.damage(skillDamage, this.currentBoss);
                           Vector knock = living.getLocation().toVector().subtract(bossLoc.toVector()).normalize();
                           knock.setY(0.5);
                           living.setVelocity(knock.multiply(skillVelocity));
                        }
                     }

                     return this.completeBossSkillCast(skillKey, target, now);
                  case WITCH:
                     for (int i = 0; i < castCount; i++) {
                        ThrownPotion potion = (ThrownPotion)world.spawn(bossLoc.clone().add(0.0, 1.0, 0.0), ThrownPotion.class);
                        Vector potionDir = dir.clone();
                        if (skillSpread > 0.0) {
                           potionDir.rotateAroundY((this.random.nextDouble() - 0.5) * skillSpread);
                        }

                        potion.setItem(new ItemStack(Material.SPLASH_POTION));
                        potion.setVelocity(potionDir.normalize().multiply(skillVelocity));
                        this.registerBossProjectile(potion, skillDamage, skillRadius, true);
                     }

                     return this.completeBossSkillCast(skillKey, target, now);
                  case GIANT:
                     this.damageEntitiesInRadius(world, bossLoc, skillRadius, skillDamage);

                     for (Entity entity : world.getNearbyEntities(bossLoc, skillRadius, skillRadius, skillRadius)) {
                        if (entity instanceof LivingEntity living
                           && !living.equals(this.currentBoss)
                           && living.getLocation().distanceSquared(bossLoc) <= skillRadius * skillRadius) {
                           Vector knockback = living.getLocation().toVector().subtract(bossLoc.toVector());
                           if (knockback.lengthSquared() < 1.0E-4) {
                              knockback = new Vector(0.0, 0.0, 1.0);
                           }

                           knockback.normalize().setY(0.7);
                           living.setVelocity(knockback.multiply(skillVelocity));
                        }
                     }

                     return this.completeBossSkillCast(skillKey, target, now);
                  case SKELETON:
                     this.launchArrowBarrage(world, bossLoc, targetLoc, castCount, skillDamage, skillRadius, skillVelocity, skillSpread);
                     return this.completeBossSkillCast(skillKey, target, now);
                  case ENDER_DRAGON:
                     EnderDragon dragon = (EnderDragon)this.currentBoss;
                     dragon.setAware(true);
                     dragon.setTarget(target);
                     dragon.setPhase(Phase.STRAFING);
                     Vector aim = target.getEyeLocation().toVector().subtract(bossLoc.clone().add(0.0, 2.0, 0.0).toVector());
                     if (aim.lengthSquared() < 1.0E-4) {
                        aim = this.currentBoss.getLocation().getDirection();
                     }

                     aim.normalize();
                     Location launchLocation = bossLoc.clone().add(aim.clone().multiply(4.0)).add(0.0, 1.5, 0.0);

                     for (int i = 0; i < castCount; i++) {
                        Vector fireballDir = target.getEyeLocation().toVector().subtract(launchLocation.toVector());
                        if (fireballDir.lengthSquared() < 1.0E-4) {
                           fireballDir = aim.clone();
                        }

                        if (skillSpread > 0.0) {
                           double offset = (i - (castCount - 1) / 2.0) * skillSpread / Math.max(1, castCount - 1);
                           fireballDir.rotateAroundY(offset);
                           fireballDir.setY(Math.max(-0.7, Math.min(0.9, fireballDir.getY() + (this.random.nextDouble() - 0.5) * skillSpread * 0.25)));
                        }

                        fireballDir.normalize().multiply(skillVelocity);
                        LargeFireball fireball = (LargeFireball)world.spawn(launchLocation, LargeFireball.class);
                        fireball.setDirection(fireballDir);
                        fireball.setVelocity(fireballDir);
                        fireball.setYield(0.0F);
                        fireball.setIsIncendiary(false);
                        fireball.setShooter(this.currentBoss);
                        this.registerBossProjectile(fireball, skillDamage, skillRadius, false);
                     }

                     return this.completeBossSkillCast(skillKey, target, now);
                  case CREEPER:
                     if (this.currentBoss instanceof Creeper creeper) {
                        final double var51 = this.safeSkillValue(skill.explosionPower, 3.0, 1.0, 64.0);
                        if (this.creeperChargeTask != null) {
                           this.creeperChargeTask.cancel();
                           this.bossSkillTasks.remove(this.creeperChargeTask);
                        }

                        creeper.setPowered(true);
                        world.strikeLightningEffect(bossLoc);
                        this.damageEntitiesInRadius(world, bossLoc, skillRadius, skillDamage);
                        int durationSeconds = Math.max(1, Math.min(600, skill.durationSeconds));
                        BukkitRunnable chargeRunnable = new BukkitRunnable() {
                           public void run() {
                              BossEventManager.this.bossSkillTasks.remove(this);
                              if (BossEventManager.this.currentBoss == creeper && creeper.isValid() && !creeper.isDead()) {
                                 creeper.setPowered(false);
                                 creeper.setExplosionRadius((int)Math.round(var51));
                              }

                              if (BossEventManager.this.creeperChargeTask == this) {
                                 BossEventManager.this.creeperChargeTask = null;
                              }
                           }
                        };
                        this.creeperChargeTask = chargeRunnable.runTaskLater(this.plugin, durationSeconds * 20L);
                        this.bossSkillTasks.add(this.creeperChargeTask);
                        return this.completeBossSkillCast(skillKey, target, now);
                     }

                     return false;
                  case WITHER:
                     for (int i = 0; i < castCount; i++) {
                        Vector skullDir = dir.clone();
                        if (skillSpread > 0.0) {
                           skullDir.rotateAroundY((Math.random() - 0.5) * skillSpread * 0.7);
                        }

                        WitherSkull skull = (WitherSkull)world.spawn(bossLoc.clone().add(0.0, 1.0, 0.0), WitherSkull.class);
                        skull.setCharged(true);
                        skull.setDirection(skullDir.normalize().multiply(skillVelocity));
                        skull.setVelocity(skullDir.normalize().multiply(skillVelocity));
                        skull.setYield(0.0F);
                        skull.setIsIncendiary(false);
                        this.registerBossProjectile(skull, skillDamage, skillRadius, false);
                     }

                     return this.completeBossSkillCast(skillKey, target, now);
                  case ZOMBIE:
                     for (Entity entity : world.getNearbyEntities(bossLoc, skillRadius, skillRadius, skillRadius)) {
                        if (entity instanceof LivingEntity living
                           && !entity.equals(this.currentBoss)
                           && living.getLocation().distanceSquared(bossLoc) <= skillRadius * skillRadius) {
                           living.damage(skillDamage, this.currentBoss);
                           Vector slam = living.getLocation().toVector().subtract(bossLoc.toVector()).normalize();
                           slam.setY(0.6);
                           living.setVelocity(slam.multiply(skillVelocity));
                        }
                     }

                     return this.completeBossSkillCast(skillKey, target, now);
                  default:
                     return false;
               }
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean isCloseRangeSkill(String skillKey) {
      return this.anySkillKey(
         skillKey,
         "ground-slam",
         "stomp",
         "giant-stomp",
         "zombie-slam",
         "slam",
         "venom-pounce",
         "axe-spin",
         "siege-charge",
         "ravager-charge",
         "dive-bomb",
         "phantom-dive",
         "dive",
         "charged-explosion",
         "creeper-blast",
         "blast"
      );
   }

   private int skillPhasePriority(String skillKey, int phase) {
      boolean closeRange = this.isCloseRangeSkill(skillKey);
      if (phase == 2) {
         return closeRange ? 0 : 1;
      } else {
         return closeRange ? 1 : 0;
      }
   }

   private boolean isSkillSuitableForDistance(String skillKey, ConfigManager.BossSkillConfig skill, double distance) {
      if (!this.isCloseRangeSkill(skillKey)) {
         return true;
      }

      double radius = this.safeSkillValue(skill.radius, 3.0, 1.0, 64.0);
      double allowedDistance = Math.max(6.0, radius + 2.0);
      if (this.config.bossAiPhasesEnabled && this.bossCombatPhase() >= 3) {
         allowedDistance += 2.0;
      }

      return distance <= allowedDistance;
   }

   private int bossCombatPhase() {
      if (this.currentBoss != null && this.currentBoss.isValid()) {
         double health = this.bossUsesVirtualHealth ? this.logicalBossHealth : this.currentBoss.getHealth();
         double maximum = this.bossUsesVirtualHealth ? this.logicalBossMaxHealth : this.currentBoss.getMaxHealth();
         if (maximum <= 0.0) {
            return 1;
         } else {
            double healthPercent = health / maximum * 100.0;
            if (healthPercent <= this.config.bossAiPhaseThreeHealthPercent) {
               return 3;
            } else {
               return healthPercent <= this.config.bossAiPhaseTwoHealthPercent ? 2 : 1;
            }
         }
      } else {
         return 1;
      }
   }

   private boolean completeBossSkillCast(String skillKey, LivingEntity target, long castTimeMillis) {
      if (this.currentBoss != null && skillKey != null) {
         this.bossSkillCooldowns.put(this.currentBoss.getUniqueId() + ":" + skillKey, castTimeMillis);
         this.announceBossSkillCast(skillKey, target);
         return true;
      } else {
         return false;
      }
   }

   private void announceBossSkillCast(String skillKey, LivingEntity target) {
      if (this.currentBoss != null) {
         String bossName = this.currentBossName == null ? this.currentBoss.getType().name() : this.currentBossName;
         String localizedSkillName = this.getLocalizedSkillName(this.currentBoss.getType(), skillKey);
         String targetName = target instanceof Player player ? player.getName() : target.getType().name();
         Location location = this.currentBoss.getLocation();
         String details = bossName
            + " Lv."
            + this.currentBossLevel
            + " cast "
            + localizedSkillName
            + " ["
            + skillKey
            + "] at target="
            + targetName
            + " position="
            + location.getBlockX()
            + ","
            + location.getBlockY()
            + ","
            + location.getBlockZ()
            + " world="
            + location.getWorld().getName();
         if (this.config.bossSkillCastLogsEnabled) {
            this.logger.info("[BossSkill] " + details);
         }

         String message = "§5§l[BOSS] §d"
            + bossName
            + " §7(Lv."
            + this.currentBossLevel
            + ") dùng kỹ năng §e"
            + localizedSkillName
            + "§7 nhắm tới §f"
            + targetName
            + "§7.";

         for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOp() ? this.config.bossSkillCastOpBroadcastEnabled : this.config.bossSkillCastPlayerBroadcastEnabled) {
               player.sendMessage(message);
            }
         }
      }
   }

   private String getLocalizedSkillName(EntityType bossType, String skillKey) {
      return switch (bossType) {
         case ENDERMAN -> this.anySkillKey(skillKey, "void-snatch", "shadow-strike", "enderman-teleport", "teleport")
            ? "Bắt Hư Không"
            : ("end-rift".equalsIgnoreCase(skillKey) ? "Vết Nứt Hư Không" : skillKey);
         case RAVAGER -> this.anySkillKey(skillKey, "siege-charge", "charge", "ravager-charge")
            ? "Cú Tông Địa Chấn"
            : ("roar-weakness".equalsIgnoreCase(skillKey) ? "Tiếng Gầm Áp Đảo" : skillKey);
         case PHANTOM -> this.anySkillKey(skillKey, "dive-bomb", "phantom-dive", "dive")
            ? "Cú Lao Đêm Mộng"
            : ("phantom-minions".equalsIgnoreCase(skillKey) ? "Triệu Hồi Bóng Ma" : skillKey);
         case GHAST -> this.anySkillKey(skillKey, "fireball-volley", "ghast-fireball", "fireball")
            ? "Mưa Cầu Lửa"
            : ("fiery-drag".equalsIgnoreCase(skillKey) ? "Kéo Lửa" : skillKey);
         case SPIDER -> this.anySkillKey(skillKey, "venom-web", "spider-web", "spider-poison", "web")
            ? "Tơ Độc"
            : ("cobweb-trap".equalsIgnoreCase(skillKey) ? "Bẫy Tơ Nhện" : ("venom-pounce".equalsIgnoreCase(skillKey) ? "Nhảy Độc" : skillKey));
         case VINDICATOR -> this.anySkillKey(skillKey, "axe-spin", "vindicator-shockwave", "vindicator_shockwave", "shockwave")
            ? "Múa Rìu Xoay"
            : ("axe-throw".equalsIgnoreCase(skillKey) ? "Phóng Rìu" : skillKey);
         case WITCH -> this.anySkillKey(skillKey, "potion-rain", "potion-volley", "witch-potion", "potion")
            ? "Mưa Thuốc Độc"
            : ("disarm-magic".equalsIgnoreCase(skillKey) ? "Tước Vũ Khí" : skillKey);
         case GIANT -> this.anySkillKey(skillKey, "ground-slam", "stomp", "giant-stomp")
            ? "Đập Đất Sóng Xung Kích"
            : ("boulder-toss".equalsIgnoreCase(skillKey) ? "Ném Đá Khổng Lồ" : skillKey);
         case SKELETON -> this.anySkillKey(skillKey, "arrow-barrage", "skeleton-arrow", "arrow-fan", "fire-arrow-fan") ? "Mưa Tên Vạn Cốt" : skillKey;
         case ENDER_DRAGON -> this.anySkillKey(skillKey, "fireball-volley", "dragon-fireball", "ender-dragon-fireball", "fireball-fan")
            ? "Long Viêm Liên Xạ"
            : skillKey;
         case CREEPER -> this.anySkillKey(skillKey, "charged-explosion", "creeper-blast", "blast") ? "Lôi Nộ Tích Điện" : skillKey;
         case WITHER -> this.anySkillKey(skillKey, "skull-volley", "wither-skull", "skull") ? "Mưa Sọ Hư Vô" : skillKey;
         case ZOMBIE -> this.anySkillKey(skillKey, "ground-slam", "zombie-slam", "slam") ? "Đập Đất" : skillKey;
         default -> skillKey;
      };
   }

   private boolean anySkillKey(String actualKey, String... aliases) {
      for (String alias : aliases) {
         if (alias.equalsIgnoreCase(actualKey)) {
            return true;
         }
      }

      return false;
   }

   private void launchArrowBarrage(
      final World world,
      Location bossLoc,
      final Location targetLoc,
      final int arrowCount,
      final double damage,
      final double radius,
      final double velocity,
      final double spread
   ) {
      double launchHeight = Math.min(7.0, Math.max(2.0, world.getMaxHeight() - bossLoc.getY() - 2.0));
      Location launchLoc = bossLoc.clone().add(0.0, launchHeight, 0.0);
      Vector launchDirection = targetLoc.toVector().subtract(launchLoc.toVector());
      launchDirection.setY(Math.max(0.8, launchDirection.getY() + 1.2));
      if (launchDirection.lengthSquared() < 1.0E-4) {
         launchDirection.setY(1.0);
      }

      final Arrow signalArrow = (Arrow)world.spawn(launchLoc, Arrow.class);
      signalArrow.setShooter(this.currentBoss);
      signalArrow.setFireTicks(60);
      signalArrow.setVelocity(launchDirection.normalize().multiply(velocity));
      this.activeBossProjectiles.add(signalArrow);
      final BukkitTask[] taskRef = new BukkitTask[1];
      BukkitRunnable burstTask = new BukkitRunnable() {
         private int ticks;

         public void run() {
            this.ticks++;
            if (signalArrow.isValid() && !signalArrow.isDead() && this.ticks < 100) {
               if (this.ticks > 1 && signalArrow.getVelocity().getY() <= 0.0) {
                  Location apex = signalArrow.getLocation().clone();
                  signalArrow.remove();
                  BossEventManager.this.activeBossProjectiles.remove(signalArrow);
                  world.spawnParticle(Particle.FIREWORK, apex, 24, 0.3, 0.3, 0.3, 0.08);
                  world.spawnParticle(Particle.EXPLOSION, apex, 1);
                  world.playSound(apex, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0F, 1.2F);
                  double spreadRadius = Math.min(radius, spread * 3.0);

                  for (int i = 0; i < arrowCount; i++) {
                     double angle = (Math.PI * 2) * i / arrowCount;
                     Location aim = targetLoc.clone().add(Math.cos(angle) * spreadRadius, 0.0, Math.sin(angle) * spreadRadius);
                     Vector direction = aim.toVector().subtract(apex.toVector());
                     direction.setY(Math.min(-0.15, direction.getY()));
                     if (direction.lengthSquared() < 1.0E-4) {
                        direction.setY(-1.0);
                     }

                     Arrow arrow = (Arrow)world.spawn(apex, Arrow.class);
                     arrow.setShooter(BossEventManager.this.currentBoss);
                     arrow.setFireTicks(60);
                     arrow.setCritical(true);
                     arrow.setDamage(damage);
                     arrow.setVelocity(direction.normalize().multiply(velocity));
                     BossEventManager.this.activeBossProjectiles.add(arrow);
                  }

                  this.finish();
               }
            } else {
               this.finish();
            }
         }

         private void finish() {
            this.cancel();
            BossEventManager.this.bossSkillTasks.remove(taskRef[0]);
         }
      };
      taskRef[0] = burstTask.runTaskTimer(this.plugin, 1L, 1L);
      this.bossSkillTasks.add(taskRef[0]);
   }

   @EventHandler
   /**
    * Khi player join, show boss bar hoặc warning bar nếu đang có sự kiện
    */
   public void onPlayerJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      if (this.pendingBoss != null && this.warningTimeLeft > 0) {
         // Đang trong giai đoạn warning
         this.showWarningBarToPlayer(player);
         player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 1.0F, 0.5F);
      } else if (this.bossActive && this.currentBoss != null && !this.currentBoss.isDead()) {
         // Boss đang hoạt động
         this.showBossBarToPlayer(player);
         player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0F, 0.5F);
      }
   }

   /**
    * Hiển thị warning bar cho một player cụ thể
    */
   private void showWarningBarToPlayer(Player player) {
      if (this.pendingBoss != null) {
         this.bossBar.name(Component.text("§e§l⚠ " + this.pendingBoss.displayName + " §7- §c§l" + this.warningTimeLeft + "s"));
         this.bossBar.progress((float)this.warningTimeLeft / this.pendingBoss.warningSeconds);
         player.showBossBar(this.bossBar);
      }
   }

   /**
    * Hiển thị boss bar cho một player cụ thể
    */
   private void showBossBarToPlayer(Player player) {
      if (this.currentBoss != null && !this.currentBoss.isDead()) {
         int remaining = Math.max(0, this.bossDurationSeconds - this.bossElapsedSeconds);
         this.updateBossDisplay(this.currentBossName, remaining);
         player.showBossBar(this.bossBar);
      }
   }

   private void updateBossDisplay(String displayName, int secondsRemaining) {
      if (this.currentBoss != null && this.currentBoss.isValid() && !this.currentBoss.isDead()) {
         double maximum = this.bossUsesVirtualHealth ? this.logicalBossMaxHealth : Math.max(1.0, this.currentBoss.getMaxHealth());
         double health = this.bossUsesVirtualHealth ? this.logicalBossHealth : this.currentBoss.getHealth();
         health = Math.max(0.0, Math.min(maximum, health));
         this.currentBoss
            .setCustomName(
               EconomyManager.sanitizeVisibleName(displayName)
                  + " §8[Lv."
                  + this.currentBossLevel
                  + "] §c"
                  + String.format(Locale.US, "%.0f", health)
                  + "/"
                  + String.format(Locale.US, "%.0f", maximum)
                  + " HP"
            );
         this.bossBar.progress((float)Math.max(0.0, Math.min(1.0, health / maximum)));
         this.bossBar
            .name(
               LegacyComponentSerializer.legacySection()
                  .deserialize(
                     "§4§l⚠ "
                        + displayName
                        + " §7[Lv."
                        + this.currentBossLevel
                        + "] §7- §c"
                        + String.format(Locale.US, "%.0f", health)
                        + "/"
                        + String.format(Locale.US, "%.0f", maximum)
                        + " HP §7| §e"
                        + Math.max(0, secondsRemaining)
                        + "s"
                  )
            );
      }
   }

   private void trySpawnBoss() {
      if (!Bukkit.getOnlinePlayers().isEmpty()) {
         if (this.pendingBoss == null) {
            List<Entry<String, ConfigManager.BossConfig>> enabledBosses = new ArrayList<>();

            for (Entry<String, ConfigManager.BossConfig> entry : this.config.bossConfigs.entrySet()) {
               if (entry.getValue().enabled) {
                  enabledBosses.add(entry);
               }
            }

            if (!enabledBosses.isEmpty()) {
               // Tính tổng chance
               int totalChance = 0;

               for (Entry<String, ConfigManager.BossConfig> entry : enabledBosses) {
                  totalChance += Math.max(0, entry.getValue().chance);
               }

               ConfigManager.BossConfig selectedBoss;
               if (totalChance <= 0) {
                  // Preserve legacy behavior when all per-boss weights are zero.
                  selectedBoss = enabledBosses.get(0).getValue();
               } else {
                  // First select boss by its individual weight, then independently roll
                  // the day-based chance that this selected boss event proceeds.
                  int roll = this.random.nextInt(totalChance);
                  int cumulative = 0;
                  selectedBoss = enabledBosses.get(enabledBosses.size() - 1).getValue();

                  for (Entry<String, ConfigManager.BossConfig> entry : enabledBosses) {
                     cumulative += Math.max(0, entry.getValue().chance);
                     if (roll < cumulative) {
                        selectedBoss = entry.getValue();
                        break;
                     }
                  }
               }

               double successChance = this.getBossSpawnSuccessChance();
               if (this.random.nextDouble() * 100.0 >= successChance) {
                  long worldDay = this.getCurrentWorldDay();
                  this.logger
                     .info(
                        "[BossEvent] "
                           + selectedBoss.displayName
                           + " rolled but did not pass the day "
                           + worldDay
                           + " spawn-success check ("
                           + successChance
                           + "%)."
                     );
                  this.timeSinceLastBoss = 0;
                  this.bossEventIntervalSeconds = this.sampleBossEventInterval();
               } else {
                  this.startWarning(selectedBoss);
               }
            }
         }
      }
   }

   private void startWarning(ConfigManager.BossConfig bc) {
      this.startWarning(bc, null);
   }

   private void startWarning(ConfigManager.BossConfig bc, Integer forcedLevel) {
      if (!this.bossActive && this.pendingBoss == null) {
         this.pendingBoss = bc;
         this.pendingBossLevel = forcedLevel;
         this.warningTimeLeft = Math.max(0, bc.warningSeconds);
         // Show warning bar cho tất cả
         this.bossBar.name(Component.text("§e§l⚠ BOSS SẮP XUẤT HIỆN ⚠"));
         this.bossBar.progress(1.0F);

         for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(this.bossBar);
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1.0F, 0.5F);
         }

         Bukkit.broadcastMessage("§4§l⚠ CẢNH BÁO: §c" + bc.displayName + " §4§lsẽ xuất hiện trong §e§l" + bc.warningSeconds + " giây§4§l!");
         if (this.warningTimeLeft == 0) {
            Integer levelToSpawn = this.pendingBossLevel;
            this.pendingBoss = null;
            this.pendingBossLevel = null;
            this.spawnBoss(bc, levelToSpawn);
         }
      }
   }

   private void updateWarningBar() {
      // Update warning bar
      if (this.warningTimeLeft > 0 && this.pendingBoss != null) {
         this.bossBar.name(Component.text("§e§l⚠ " + this.pendingBoss.displayName + " §7- §c§l" + this.warningTimeLeft + "s"));
         this.bossBar.progress((float)this.warningTimeLeft / this.pendingBoss.warningSeconds);
         if (this.warningTimeLeft <= 10 || this.warningTimeLeft == 30) {
            Bukkit.broadcastMessage("§c§l⚠ " + this.pendingBoss.displayName + " §csẽ xuất hiện trong §4§l" + this.warningTimeLeft + "§c giây!");
         }
      }
   }

   @EventHandler
   /**
    * Ngăn boss bị cháy dưới ánh nắng nếu có immunity sunlight-burn
    */
   public void onEntityCombust(EntityCombustEvent event) {
      if (event.getEntity() instanceof LivingEntity entity) {
         if (this.currentBoss != null && entity.equals(this.currentBoss)) {
            for (Entry<String, ConfigManager.BossConfig> entry : this.config.bossConfigs.entrySet()) {
               ConfigManager.BossConfig bc = entry.getValue();
               if (bc.displayName.equals(this.currentBossName)) {
                  if (bc.immunities.getOrDefault("sunlight-burn", false)) {
                     event.setCancelled(true);
                     return;
                  }

                  // Kiểm tra fire immunity
                  if (bc.immunities.getOrDefault("fire", false) && event instanceof EntityCombustEvent) {
                     event.setCancelled(true);
                     return;
                  }
               }
            }
         }
      }
   }

   private void spawnBoss(ConfigManager.BossConfig bc) {
      this.spawnBoss(bc, null);
   }

   private void spawnBoss(final ConfigManager.BossConfig bc, Integer forcedLevel) {
      if (this.bossActive) {
         this.clearWarningBar();
      } else {
         if (forcedLevel != null) {
            this.currentBossLevel = Math.max(1, forcedLevel);
         } else {
            this.currentBossLevel = this.sampleBossLevel(bc);
         }

         // Chọn player ngẫu nhiên
         List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
         if (onlinePlayers.isEmpty()) {
            this.clearWarningBar();
         } else {
            Player targetPlayer = onlinePlayers.get(this.random.nextInt(onlinePlayers.size()));
            // Tính vị trí spawn ngẫu nhiên quanh player
            int radius = this.config.bossEventSpawnRadius;
            int x = targetPlayer.getLocation().getBlockX() + this.random.nextInt(radius * 2) - radius;
            int z = targetPlayer.getLocation().getBlockZ() + this.random.nextInt(radius * 2) - radius;
            World world = targetPlayer.getWorld();
            int y = world.getHighestBlockYAt(x, z) + 1;
            Location spawnLoc = new Location(world, x, y, z);

            // Get entity type from config
            EntityType entityType;
            try {
               entityType = EntityType.valueOf(bc.entityType.toUpperCase());
            } catch (IllegalArgumentException e) {
               this.logger.warning("[BossEvent] Invalid entity-type '" + bc.entityType + "' for boss");
               this.clearWarningBar();
               return;
            }

            this.clearWarningBar();
            // Spawn boss
            Entity spawnedEntity = world.spawnEntity(spawnLoc, entityType);
            if (!(spawnedEntity instanceof LivingEntity livingBoss)) {
               spawnedEntity.remove();
               this.logger.warning("[BossEvent] Entity-type '" + bc.entityType + "' is not a living entity");
            } else {
               this.currentBoss = livingBoss;
               this.activeBossConfig = bc;
               if (this.config.bossSizeEnabled && this.config.bossSizeEntityTypes.contains(this.currentBoss.getType().name())) {
                  AttributeInstance scaleAttribute = this.currentBoss.getAttribute(Attribute.SCALE);
                  if (scaleAttribute == null) {
                     this.logger.warning("[BossEvent] " + this.currentBoss.getType() + " does not expose the SCALE attribute; keeping its default size.");
                  } else {
                     scaleAttribute.setBaseValue(this.config.bossSizeMultiplier);
                  }
               }

               this.bossDamageContributions.clear();
               this.spiderBroodReleased = false;
               this.currentBossSpawnLocation = spawnLoc.clone();
               this.currentBossActivityRadius = bc.activityRadius;
               this.returningToAnchor = false;
               this.anchorPathUpdatedAtMs = 0L;
               this.giantWanderTarget = null;
               this.giantWanderUntilMs = 0L;
               if (this.currentBoss instanceof Creeper creeper) {
                  ConfigManager.BossSkillConfig skill = this.getCreeperSkillConfig(bc);
                  double explosionPower = skill == null ? 3.0 : this.safeSkillValue(skill.explosionPower, 3.0, 1.0, 64.0);
                  creeper.setExplosionRadius((int)Math.round(explosionPower));
                  creeper.setPowered(false);
                  creeper.setIgnited(false);
                  creeper.setFuseTicks(0);
                  creeper.setGravity(true);
                  this.nextCreeperExplosionAtMs = 0L;
               }

               double effectiveHp = this.resolveBossHp(bc, this.currentBossLevel);
               this.currentBossName = bc.displayName;
               this.logicalBossMaxHealth = effectiveHp;
               this.logicalBossHealth = effectiveHp;
               this.bossUsesVirtualHealth = false;

               try {
                  if (this.currentBoss.getAttribute(Attribute.MAX_HEALTH) != null) {
                     this.currentBoss.getAttribute(Attribute.MAX_HEALTH).setBaseValue(effectiveHp);
                  } else {
                     this.currentBoss.setMaxHealth(effectiveHp);
                  }
               } catch (IllegalArgumentException exception) {
                  this.logger
                     .warning(
                        "[BossEvent] Runtime rejected "
                           + effectiveHp
                           + " max HP for "
                           + bc.displayName
                           + "; using virtual health pool with entity max HP "
                           + this.currentBoss.getMaxHealth()
                           + "."
                     );
               }

               double entityMaximum = this.currentBoss.getMaxHealth();
               if (entityMaximum + 0.001 < effectiveHp) {
                  this.bossUsesVirtualHealth = true;
                  this.logger
                     .warning(
                        "[BossEvent] Runtime max-health attribute is "
                           + entityMaximum
                           + " HP, below configured "
                           + effectiveHp
                           + " HP for "
                           + bc.displayName
                           + "; virtual health pool enabled."
                     );
                  this.currentBoss.setHealth(entityMaximum);
               } else {
                  this.currentBoss.setHealth(effectiveHp);
               }

               this.bossRewardThresholdHealth = effectiveHp;
               this.currentBoss.setCustomNameVisible(true);
               this.currentBoss.setGlowing(true);
               this.updateBossDisplay(bc.displayName, Math.max(0, bc.durationSeconds));
               if (this.currentBoss instanceof Mob mob) {
                  mob.setAI(true);
                  mob.setPersistent(true);
                  mob.setRemoveWhenFarAway(false);
               }

               this.keepBossChunkLoaded();
               if (bc.immunities.getOrDefault("sunlight-burn", false)) {
                  this.currentBoss.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
               }

               // Fire immunity
               if (bc.immunities.getOrDefault("fire", false)) {
                  this.currentBoss.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
               }

               // Fall damage immunity
               if (bc.immunities.getOrDefault("fall-damage", false)) {
                  this.currentBoss.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, Integer.MAX_VALUE, 0, false, false));
               }

               // Wither immunity
               if (bc.immunities.getOrDefault("wither", false)) {
                  this.currentBoss.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
               }

               // Poison immunity
               if (bc.immunities.getOrDefault("poison", false)) {
                  this.currentBoss.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
               }

               this.bossActive = true;
               this.leaderboardManager.startBoss(this.currentBoss);
               this.timeSinceLastBoss = 0;
               this.bossEventIntervalSeconds = this.sampleBossEventInterval();
               this.bossElapsedSeconds = 0;
               this.bossDurationSeconds = bc.durationSeconds;
               String msg = "§4§l⚠ SỰ KIỆN BOSS: §c" + bc.displayName + " §4§lđã xuất hiện tại §e" + x + ", " + y + ", " + z + " §4§l(" + world.getName() + ")";
               Bukkit.broadcastMessage("§4§m========================================");
               Bukkit.broadcastMessage(msg);
               Bukkit.broadcastMessage("§e§lHP: §c" + (int)effectiveHp + " §e| Sát thương: x" + bc.damageMultiplier + " §e| Lv." + this.currentBossLevel);
               Bukkit.broadcastMessage("§eThời gian tồn tại: " + bc.durationSeconds + " giây");
               this.broadcastPossibleDrops(bc, this.currentBossLevel);
               Bukkit.broadcastMessage("§4§m========================================");

               for (Player p : Bukkit.getOnlinePlayers()) {
                  p.sendTitle(
                     "§4§l" + bc.displayName,
                     "§eLv."
                        + this.currentBossLevel
                        + " • "
                        + String.format(Locale.US, "%.0f", this.currentBoss.getHealth())
                        + "/"
                        + String.format(Locale.US, "%.0f", this.currentBoss.getMaxHealth())
                        + " HP",
                     10,
                     40,
                     20
                  );
                  this.showBossBarToPlayer(p);
                  p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0F, 0.5F);
               }

               this.logManager.logDisaster("BOSS: " + bc.displayName + " spawned at " + x + "," + y + "," + z + " (Lv." + this.currentBossLevel + ")");
               // Khởi động AI task cho boss
               this.startBossAI(bc);
               this.bossMonitorTask = (new BukkitRunnable() {
                     public void run() {
                        if (BossEventManager.this.currentBoss != null
                           && !BossEventManager.this.currentBoss.isDead()
                           && BossEventManager.this.currentBoss.isValid()) {
                           BossEventManager.this.bossElapsedSeconds++;
                           int remaining = Math.max(0, BossEventManager.this.bossDurationSeconds - BossEventManager.this.bossElapsedSeconds);
                           BossEventManager.this.updateBossDisplay(bc.displayName, remaining);
                           if (BossEventManager.this.bossElapsedSeconds >= BossEventManager.this.bossDurationSeconds) {
                              // Hết thời gian - boss biến mất
                              Bukkit.broadcastMessage("§c§l⏰ " + bc.displayName + " đã biến mất!");
                              BossEventManager.this.cleanup();
                              this.cancel();
                           }
                        } else {
                           if (BossEventManager.this.currentBoss != null && BossEventManager.this.currentBoss.isDead()) {
                              BossEventManager.this.onBossKilled(bc);
                           } else if (BossEventManager.this.currentBoss != null) {
                              BossEventManager.this.logger
                                 .warning(
                                    "[BossEvent] Boss entity became invalid before timeout: "
                                       + BossEventManager.this.currentBoss.getType()
                                       + " uuid="
                                       + BossEventManager.this.currentBoss.getUniqueId()
                                 );
                           }

                           BossEventManager.this.cleanup();
                           this.cancel();
                        }
                     }
                  })
                  .runTaskTimer(this.plugin, 0L, 20L);
            }
         }
      }
   }

   /**
    * AI cho boss: chạy mỗi 10 ticks (0.5 giây).
    */
   private void startBossAI(final ConfigManager.BossConfig bc) {
      if (this.aiTask != null) {
         this.aiTask.cancel();
         this.aiTask = null;
      }

      BukkitRunnable aiRunnable = new BukkitRunnable() {
         public void run() {
            if (BossEventManager.this.currentBoss != null && !BossEventManager.this.currentBoss.isDead() && BossEventManager.this.bossActive) {
               Location bossLoc = BossEventManager.this.currentBoss.getLocation();
               World bossWorld = bossLoc.getWorld();
               if (bossWorld != null) {
                  BossEventManager.this.keepBossChunkLoaded();
                  BossEventManager.this.enforceBossFlightHeight(bossLoc, bossWorld);
                  bossLoc = BossEventManager.this.currentBoss.getLocation();
                  BossEventManager.this.tickBossTraits(bc);
                  BossEventManager.this.tickSplitFireballs();
                  if (BossEventManager.this.currentBoss instanceof Mob mob
                     && mob.getTarget() != null
                     && !BossEventManager.this.hasBossLineOfSight(mob.getTarget())) {
                     mob.setTarget(null);
                  }

                  double distanceFromAnchorSquared = BossEventManager.this.currentBossSpawnLocation == null
                     ? 0.0
                     : BossEventManager.this.horizontalDistanceSquared(bossLoc, BossEventManager.this.currentBossSpawnLocation);
                  boolean outsideActivityRadius = BossEventManager.this.currentBossSpawnLocation != null
                     && distanceFromAnchorSquared > (double)BossEventManager.this.currentBossActivityRadius * BossEventManager.this.currentBossActivityRadius;
                  if (!outsideActivityRadius) {
                     BossEventManager.this.returningToAnchor = false;
                     if (!BossEventManager.this.config.bossAiEnabled
                        || !BossEventManager.this.config.bossAiProjectileDodgeEnabled
                        || !BossEventManager.this.dodgeIncomingPlayerProjectile(bossLoc)) {
                        double searchRadius = BossEventManager.this.isFlightLimitedBoss() ? BossEventManager.this.config.bossFlightTargetRange : 20.0;
                        Player selectedPlayer = null;
                        double selectedPlayerScore = Double.NEGATIVE_INFINITY;

                        for (Player p : Bukkit.getOnlinePlayers()) {
                           if (p.getWorld().equals(bossWorld)
                              && !p.isDead()
                              && p.isOnline()
                              && (!(BossEventManager.this.currentBoss instanceof Enderman) || BossEventManager.this.isValidEndermanTarget(p))
                              && BossEventManager.this.hasBossLineOfSight(p)) {
                              double d = BossEventManager.this.bossTargetDistanceSquared(p.getLocation(), bossLoc);
                              if (!(d >= searchRadius * searchRadius)) {
                                 double score = BossEventManager.this.config.bossAiEnabled ? BossEventManager.this.playerThreatScore(p, d, searchRadius) : -d;
                                 if (score > selectedPlayerScore) {
                                    selectedPlayerScore = score;
                                    selectedPlayer = p;
                                 }
                              }
                           }
                        }

                        Player existingPlayerTarget = BossEventManager.this.currentBoss instanceof Mob bossMob && bossMob.getTarget() instanceof Player player
                           ? player
                           : null;
                        if (selectedPlayer != null
                           && existingPlayerTarget != null
                           && existingPlayerTarget != selectedPlayer
                           && existingPlayerTarget.isOnline()
                           && !existingPlayerTarget.isDead()
                           && existingPlayerTarget.getWorld().equals(bossWorld)
                           && BossEventManager.this.hasBossLineOfSight(existingPlayerTarget)) {
                           double currentDistance = BossEventManager.this.bossTargetDistanceSquared(existingPlayerTarget.getLocation(), bossLoc);
                           if (currentDistance < searchRadius * searchRadius) {
                              double currentScore = BossEventManager.this.config.bossAiEnabled
                                 ? BossEventManager.this.playerThreatScore(existingPlayerTarget, currentDistance, searchRadius)
                                 : -currentDistance;
                              if (currentScore + BossEventManager.this.config.bossAiRetargetScoreMargin >= selectedPlayerScore) {
                                 selectedPlayer = existingPlayerTarget;
                              }
                           }
                        }

                        if (selectedPlayer == null) {
                           // Ưu tiên 2: Tấn công friendly mobs (Villager, Iron Golem, Snowman, etc.)
                           LivingEntity nearestFriendly = null;
                           double nearestFriendlyDist = Double.MAX_VALUE;
                           LivingEntity nearestMob = null;
                           double nearestMobDist = Double.MAX_VALUE;
                           double mobSearchRadius = BossEventManager.this.isFlightLimitedBoss() ? BossEventManager.this.config.bossFlightTargetRange : 15.0;
                           double mobSearchVerticalRadius = BossEventManager.this.isFlightLimitedBoss()
                              ? BossEventManager.this.config.bossMaxFlightHeightAboveGround + 20.0
                              : 10.0;

                           for (Entity e : bossWorld.getNearbyEntities(bossLoc, mobSearchRadius, mobSearchVerticalRadius, mobSearchRadius)) {
                              if (!e.equals(BossEventManager.this.currentBoss)
                                 && e instanceof LivingEntity le
                                 && !(e instanceof Player)
                                 && !le.isDead()
                                 && BossEventManager.this.hasBossLineOfSight(le)) {
                                 double d = BossEventManager.this.bossTargetDistanceSquared(le.getLocation(), bossLoc);
                                 if (BossEventManager.this.isFriendlyMob(le)) {
                                    if (d < nearestFriendlyDist) {
                                       nearestFriendlyDist = d;
                                       nearestFriendly = le;
                                    }
                                 } else if (!(le instanceof Boss) && d < nearestMobDist) {
                                    nearestMobDist = d;
                                    nearestMob = le;
                                 }
                              }
                           }

                           if (nearestFriendly != null) {
                              if (BossEventManager.this.prepareBossTarget(nearestFriendly)) {
                                 if (!BossEventManager.this.useBossSkill(bc, nearestFriendly)) {
                                    BossEventManager.this.attackTarget(nearestFriendly, bc);
                                 }
                              }
                           } else if (nearestMob != null) {
                              if (BossEventManager.this.prepareBossTarget(nearestMob)) {
                                 if (!BossEventManager.this.useBossSkill(bc, nearestMob)) {
                                    BossEventManager.this.attackTarget(nearestMob, bc);
                                 }
                              }
                           } else {
                              if (BossEventManager.this.currentBoss instanceof Giant) {
                                 BossEventManager.this.wanderGiant(bossLoc);
                              } else if (BossEventManager.this.currentBoss instanceof EnderDragon dragon) {
                                 if (dragon.getTarget() != null) {
                                    dragon.setTarget(null);
                                 }

                                 if (dragon.getPhase() != Phase.CIRCLING) {
                                    dragon.setPhase(Phase.CIRCLING);
                                 }
                              }
                           }
                        } else if (BossEventManager.this.prepareBossTarget(selectedPlayer)) {
                           if (!BossEventManager.this.useBossSkill(bc, selectedPlayer)) {
                              BossEventManager.this.attackTarget(selectedPlayer, bc);
                           }
                        }
                     }
                  } else if (BossEventManager.this.currentBoss instanceof EnderDragon) {
                     long now = System.currentTimeMillis();
                     if (!BossEventManager.this.returningToAnchor || now >= BossEventManager.this.anchorPathUpdatedAtMs) {
                        BossEventManager.this.steerBossTowardAnchor(bossLoc);
                        BossEventManager.this.returningToAnchor = true;
                        BossEventManager.this.anchorPathUpdatedAtMs = now + 1000L;
                     }
                  } else if (BossEventManager.this.currentBoss instanceof Mob mob) {
                     if (!BossEventManager.this.returningToAnchor && mob.getTarget() != null) {
                        mob.setTarget(null);
                     }

                     if (!BossEventManager.this.returningToAnchor || System.currentTimeMillis() >= BossEventManager.this.anchorPathUpdatedAtMs) {
                        Location destination = BossEventManager.this.currentBossSpawnLocation.clone();
                        destination.setY(bossWorld.getHighestBlockYAt(destination) + 1.0);
                        mob.getPathfinder().moveTo(destination, BossEventManager.this.currentBoss instanceof Giant ? 1.1 : 1.0);
                        BossEventManager.this.returningToAnchor = true;
                        BossEventManager.this.anchorPathUpdatedAtMs = System.currentTimeMillis() + 2000L;
                     }
                  } else {
                     BossEventManager.this.steerBossTowardAnchor(bossLoc);
                  }
               }
            } else {
               this.cancel();
            }
         }
      };
      this.aiTask = aiRunnable.runTaskTimer(this.plugin, 0L, 10L);
   }

   private double playerThreatScore(Player player, double distanceSquared, double searchRadius) {
      double damage = this.bossDamageContributions.getOrDefault(player.getUniqueId(), 0.0);
      double maximumHealth = Math.max(1.0, this.bossUsesVirtualHealth ? this.logicalBossMaxHealth : this.currentBoss.getMaxHealth());
      double damageScore = Math.min(1.0, damage / maximumHealth);
      double proximityScore = 1.0 - Math.min(1.0, Math.sqrt(distanceSquared) / searchRadius);
      double healthScore = 1.0 - Math.min(1.0, player.getHealth() / Math.max(1.0, player.getMaxHealth()));
      return this.config.bossAiDamageWeight * damageScore
         + this.config.bossAiProximityWeight * proximityScore
         + this.config.bossAiLowHealthWeight * healthScore;
   }

   private boolean prepareBossTarget(LivingEntity target) {
      this.updateNativeTarget(target);
      if (this.config.bossAiEnabled && this.config.bossAiMinionCoordinationEnabled) {
         this.coordinateBossMinions(target);
      }

      this.approachFlyingBossTarget(target);
      return !this.config.bossAiEnabled || !this.config.bossAiProjectileDodgeEnabled || !this.dodgeIncomingPlayerProjectile(this.currentBoss.getLocation());
   }

   private void coordinateBossMinions(LivingEntity target) {
      if (!target.isDead() && target.isValid()) {
         for (UUID summonId : this.bossSummons) {
            if (Bukkit.getEntity(summonId) instanceof Mob minion
               && !minion.equals(this.currentBoss)
               && !minion.isDead()
               && minion.isValid()
               && minion.getWorld().equals(target.getWorld())
               && !(minion.getLocation().distanceSquared(this.currentBoss.getLocation()) > 1024.0)
               && minion.hasLineOfSight(target)) {
               minion.setAware(true);
               if (minion.getTarget() != target) {
                  minion.setTarget(target);
               }

               minion.getPathfinder().moveTo(target, this.config.bossAiGroundPathfindingSpeed);
            }
         }
      }
   }

   private boolean dodgeIncomingPlayerProjectile(Location bossLoc) {
      World world = bossLoc.getWorld();
      if (world != null && System.currentTimeMillis() >= this.nextBossProjectileDodgeAtMs) {
         double range = this.config.bossAiProjectileDodgeRange;
         double dangerDistance = Math.max(1.25, this.currentBoss.getWidth() * 0.5 + 0.75);

         for (Entity entity : world.getNearbyEntities(bossLoc, range, range, range)) {
            if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player && projectile.isValid()) {
               Vector velocity = projectile.getVelocity();
               double speedSquared = velocity.lengthSquared();
               if (!(speedSquared < 0.01)) {
                  Vector relativePosition = bossLoc.toVector().subtract(projectile.getLocation().toVector());
                  double timeToClosest = relativePosition.dot(velocity) / speedSquared;
                  double maxPredictionTicks = Math.min(12.0, range / Math.sqrt(speedSquared));
                  if (!(timeToClosest <= 0.0) && !(timeToClosest > maxPredictionTicks)) {
                     Vector closestApproach = relativePosition.clone().subtract(velocity.clone().multiply(timeToClosest));
                     closestApproach.setY(0.0);
                     if (!(closestApproach.lengthSquared() > dangerDistance * dangerDistance)) {
                        Vector dodgeDirection = closestApproach;
                        if (dodgeDirection.lengthSquared() < 0.01) {
                           dodgeDirection = new Vector(-velocity.getZ(), 0.0, velocity.getX());
                           if (this.random.nextBoolean()) {
                              dodgeDirection.multiply(-1.0);
                           }
                        }

                        if (!(dodgeDirection.lengthSquared() < 0.01)) {
                           Vector currentVelocity = this.currentBoss.getVelocity();
                           dodgeDirection.normalize().multiply(0.55);
                           dodgeDirection.setY(currentVelocity.getY());
                           this.currentBoss.setVelocity(dodgeDirection);
                           this.nextBossProjectileDodgeAtMs = System.currentTimeMillis() + this.config.bossAiProjectileDodgeCooldownTicks * 50L;
                           return true;
                        }
                     }
                  }
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onBossTarget(EntityTargetLivingEntityEvent event) {
      if (this.currentBoss != null && event.getEntity().equals(this.currentBoss) && event.getTarget() instanceof LivingEntity target) {
         if (!this.hasBossLineOfSight(target) || this.currentBoss instanceof Enderman && target instanceof Player player && !this.isValidEndermanTarget(player)
            )
          {
            event.setCancelled(true);
         }
      }
   }

   private boolean hasBossLineOfSight(LivingEntity target) {
      if (this.currentBoss != null
         && this.currentBoss.isValid()
         && !this.currentBoss.isDead()
         && target != null
         && target.isValid()
         && !target.isDead()
         && target.getWorld().equals(this.currentBoss.getWorld())
         && this.isWithinBossArea(target.getLocation())
         && this.currentBoss.hasLineOfSight(target)) {
         Location bossLocation = this.currentBoss.getLocation();
         Location targetLocation = target.getLocation();
         return !this.isFlightLimitedBoss()
            ? bossLocation.distanceSquared(targetLocation) <= 400.0
            : this.bossTargetDistanceSquared(targetLocation, bossLocation) <= (double)this.config.bossFlightTargetRange * this.config.bossFlightTargetRange
               && Math.abs(targetLocation.getY() - bossLocation.getY()) <= this.config.bossMaxFlightHeightAboveGround + 20.0;
      } else {
         return false;
      }
   }

   private boolean isFlightLimitedBoss() {
      return this.config.bossFlightHeightEnabled && this.currentBoss != null && this.config.bossFlightEntityTypes.contains(this.currentBoss.getType().name());
   }

   private double bossTargetDistanceSquared(Location targetLocation, Location bossLocation) {
      return !this.isFlightLimitedBoss() ? targetLocation.distanceSquared(bossLocation) : this.horizontalDistanceSquared(targetLocation, bossLocation);
   }

   private void approachFlyingBossTarget(LivingEntity target) {
      if (this.isFlightLimitedBoss() && this.hasBossLineOfSight(target)) {
         if (this.currentBoss instanceof Mob mob) {
            mob.setAware(true);
            if (mob.getTarget() != target) {
               mob.setTarget(target);
            }
         }

         if (this.currentBoss instanceof EnderDragon dragon) {
            dragon.setAware(true);
            dragon.setTarget(target);
            dragon.setPhase(Phase.STRAFING);
         }

         Location bossLocation = this.currentBoss.getLocation();
         Location targetLocation = target.getLocation();
         Vector direction = targetLocation.toVector().subtract(bossLocation.toVector());
         double distance = direction.length();
         double preferredDistance = this.config.bossFlightPreferredCombatDistance;
         Vector velocity = this.currentBoss.getVelocity();
         if (distance > 1.0E-4 && distance > preferredDistance + 2.0) {
            this.currentBoss.setVelocity(direction.normalize().multiply(0.4));
         } else if (distance > 1.0E-4 && distance < preferredDistance - 2.0) {
            this.currentBoss.setVelocity(direction.multiply(-1.0 / distance).multiply(0.3));
         } else {
            velocity.setX(0.0);
            velocity.setZ(0.0);
            this.currentBoss.setVelocity(velocity);
         }
      }
   }

   private void enforceBossFlightHeight(Location bossLocation, World world) {
      if (this.isFlightLimitedBoss()) {
         int groundY = world.getHighestBlockYAt(bossLocation.getBlockX(), bossLocation.getBlockZ()) + 1;
         double maximumY = groundY + this.config.bossMaxFlightHeightAboveGround;
         Vector velocity = this.currentBoss.getVelocity();
         if (bossLocation.getY() > maximumY) {
            bossLocation.setY(maximumY);
            this.currentBoss.teleport(bossLocation);
            if (velocity.getY() > 0.0) {
               velocity.setY(0.0);
            }

            this.currentBoss.setVelocity(velocity);
         } else if (bossLocation.getY() >= maximumY - 0.25 && velocity.getY() > 0.0) {
            velocity.setY(0.0);
            this.currentBoss.setVelocity(velocity);
         }
      }
   }

   private boolean isValidEndermanTarget(Player player) {
      if (this.currentBoss instanceof Enderman enderman
         && player.isOnline()
         && !player.isDead()
         && player.getWorld().equals(enderman.getWorld())
         && enderman.hasLineOfSight(player)
         && player.hasLineOfSight(enderman)) {
         Vector directionToBoss = enderman.getEyeLocation().toVector().subtract(player.getEyeLocation().toVector());
         return directionToBoss.lengthSquared() < 1.0E-4 ? true : player.getEyeLocation().getDirection().normalize().dot(directionToBoss.normalize()) >= 0.95;
      } else {
         return false;
      }
   }

   private void wanderGiant(Location bossLoc) {
      long now = System.currentTimeMillis();
      if (this.giantWanderTarget == null || now >= this.giantWanderUntilMs || bossLoc.distanceSquared(this.giantWanderTarget) < 2.25) {
         double angle = this.random.nextDouble() * Math.PI * 2.0;
         double distance = 4.0 + this.random.nextDouble() * 6.0;
         Location candidate = bossLoc.clone().add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance);
         Block feet = candidate.getBlock();
         if (!this.isWithinBossArea(candidate) || !feet.isPassable() || !feet.getRelative(0, -1, 0).getType().isSolid()) {
            this.giantWanderTarget = null;
            this.giantWanderUntilMs = now + 1500L;
            return;
         }

         this.giantWanderTarget = candidate;
         this.giantWanderUntilMs = now + 5000L;
      }

      if (this.currentBoss instanceof Giant giant && this.giantWanderTarget != null) {
         Vector direction = this.giantWanderTarget.toVector().subtract(bossLoc.toVector()).setY(0.0);
         if (direction.lengthSquared() > 0.25) {
            Vector velocity = direction.normalize().multiply(0.07);
            velocity.setY(giant.getVelocity().getY());
            giant.setVelocity(velocity);
         }
      }
   }

   /**
    * Tấn công mục tiêu: di chuyển đến và gây sát thương
    */
   private void attackTarget(LivingEntity target, ConfigManager.BossConfig bc) {
      if (this.hasBossLineOfSight(target)) {
         Location bossLoc = this.currentBoss.getLocation();
         Location targetLoc = target.getLocation();
         World world = bossLoc.getWorld();
         if (world != null) {
            double distance = bossLoc.distance(targetLoc);
            if (this.currentBoss instanceof EnderDragon dragon) {
               dragon.setAware(true);
               dragon.setTarget(target);
               dragon.setPhase(Phase.STRAFING);
               if (distance <= 3.0) {
                  target.damage(10.0 * bc.damageMultiplier, this.currentBoss);
                  Vector knockback = target.getLocation().toVector().subtract(bossLoc.toVector()).normalize();
                  knockback.setY(0.5);
                  target.setVelocity(knockback.multiply(1.5));
               }
            } else {
               if (this.currentBoss instanceof Creeper creeper) {
                  if (creeper.getTarget() != target) {
                     creeper.setTarget(target);
                  }

                  if (this.config.bossAiEnabled) {
                     creeper.getPathfinder().moveTo(target, this.config.bossAiGroundPathfindingSpeed);
                  }

                  if (distance <= 3.0) {
                     ConfigManager.BossSkillConfig skill = this.getCreeperSkillConfig(bc);
                     int cooldownSeconds = skill == null ? 4 : Math.max(1, Math.min(120, skill.explosionCooldownSeconds));
                     long now = System.currentTimeMillis();
                     if (now >= this.nextCreeperExplosionAtMs) {
                        double explosionPower = creeper.isPowered() && skill != null
                           ? this.safeSkillValue(skill.chargedExplosionPower, 6.0, 1.0, 64.0)
                           : (skill == null ? Math.max(1, creeper.getExplosionRadius()) : this.safeSkillValue(skill.explosionPower, 3.0, 1.0, 64.0));
                        world.createExplosion(bossLoc, (float)explosionPower, false, false, creeper);
                        Vector postExplosionVelocity = creeper.getVelocity();
                        creeper.setVelocity(new Vector(postExplosionVelocity.getX(), Math.min(0.0, postExplosionVelocity.getY()), postExplosionVelocity.getZ()));
                        this.nextCreeperExplosionAtMs = now + cooldownSeconds * 1000L;
                     }
                  }

                  return;
               }

               if (this.currentBoss instanceof Giant giant) {
                  giant.setAware(true);
                  if (giant.getTarget() != target) {
                     giant.setTarget(target);
                  }

                  if (distance > 3.0) {
                     Vector direction = targetLoc.toVector().subtract(bossLoc.toVector()).setY(0.0);
                     if (direction.lengthSquared() > 1.0E-4) {
                        Vector velocity = direction.normalize().multiply(0.12);
                        velocity.setY(giant.getVelocity().getY());
                        giant.setVelocity(velocity);
                     }
                  } else {
                     target.damage(12.0 * bc.damageMultiplier, this.currentBoss);
                     Vector knockback = target.getLocation().toVector().subtract(bossLoc.toVector());
                     if (knockback.lengthSquared() < 1.0E-4) {
                        knockback.setZ(1.0);
                     }

                     target.setVelocity(knockback.normalize().setY(0.3).multiply(2.0));
                  }

                  return;
               }

               if (this.isFlightLimitedBoss()) {
                  this.approachFlyingBossTarget(target);
               } else if (this.currentBoss instanceof Mob mob) {
                  mob.setAware(true);
                  if (mob.getTarget() != target) {
                     mob.setTarget(target);
                  }

                  if (this.config.bossAiEnabled) {
                     mob.getPathfinder().moveTo(target, this.config.bossAiGroundPathfindingSpeed);
                  }

                  if (this.currentBoss instanceof Spider) {
                     return;
                  }
               } else if (this.currentBoss instanceof Flying flying) {
                  // Nếu là flying entity (Ghast)
                  if (distance > 4.0) {
                     Vector dir = targetLoc.toVector().subtract(bossLoc.toVector()).normalize();
                     this.currentBoss.setVelocity(dir.multiply(0.4));
                  }
               } else if (distance > 2.0) {
                  Vector dir = targetLoc.toVector().subtract(bossLoc.toVector()).normalize();
                  dir.setY(0);
                  this.currentBoss.setVelocity(dir.multiply(0.3));
               }
            }

            // Tấn công nếu ở gần (cho các entity không phải dragon/giant)
            if (!(this.currentBoss instanceof EnderDragon) && !(this.currentBoss instanceof Giant) && distance <= 4.0) {
               target.damage(8.0 * bc.damageMultiplier, this.currentBoss);
            }
         }
      }
   }

   private double spiderPlayerHitDamage(ConfigManager.BossConfig bossConfig, double damage) {
      return bossConfig != null && this.currentBoss != null && this.currentBoss.getType() == EntityType.SPIDER
         ? Math.min(damage, bossConfig.maxPlayerHitDamage)
         : damage;
   }

   private void updateNativeTarget(LivingEntity target) {
      if (this.currentBoss instanceof Mob mob) {
         mob.setAware(true);
         if (mob.getTarget() != target) {
            mob.setTarget(target);
         }
      }
   }

   private boolean isWithinBossArea(Location location) {
      return this.currentBossSpawnLocation == null
         || location.getWorld() == this.currentBossSpawnLocation.getWorld()
            && this.horizontalDistanceSquared(location, this.currentBossSpawnLocation)
               <= (double)this.currentBossActivityRadius * this.currentBossActivityRadius;
   }

   private double horizontalDistanceSquared(Location first, Location second) {
      double dx = first.getX() - second.getX();
      double dz = first.getZ() - second.getZ();
      return dx * dx + dz * dz;
   }

   private void steerBossTowardAnchor(Location bossLoc) {
      if (this.currentBoss != null && this.currentBossSpawnLocation != null) {
         Location anchor = this.currentBossSpawnLocation;
         Vector direction = new Vector(anchor.getX() - bossLoc.getX(), 0.0, anchor.getZ() - bossLoc.getZ());
         if (!(direction.lengthSquared() < 1.0E-4)) {
            direction.normalize();
            if (this.currentBoss instanceof EnderDragon dragon) {
               if (dragon.getTarget() != null) {
                  dragon.setTarget(null);
               }

               this.currentBoss.setVelocity(new Vector(direction.getX() * 0.65, 0.0, direction.getZ() * 0.65));
            } else {
               this.currentBoss.setVelocity(new Vector(direction.getX() * 0.35, Math.min(0.0, this.currentBoss.getVelocity().getY()), direction.getZ() * 0.35));
            }
         }
      }
   }

   /**
    * Kiểm tra entity có phải friendly mob không
    */
   private boolean isFriendlyMob(LivingEntity entity) {
      return entity instanceof Villager
         || entity instanceof IronGolem
         || entity instanceof Snowman
         || entity instanceof Cat
         || entity instanceof Wolf wolf && !wolf.isAngry()
         || entity instanceof Parrot
         || entity instanceof Bee
         || entity instanceof Dolphin
         || entity instanceof Fox
         || entity instanceof Panda
         || entity instanceof Turtle
         || entity instanceof Axolotl
         || entity instanceof Goat
         || entity instanceof Allay
         || entity instanceof Camel
         || entity instanceof Sniffer
         || entity instanceof Armadillo;
   }

   private void onBossKilled(ConfigManager.BossConfig bc) {
      EntityDamageEvent lastDamage = this.currentBoss == null ? null : this.currentBoss.getLastDamageCause();
      Set<UUID> eligiblePlayers = this.getRewardEligiblePlayers();
      this.awardBossExperience(bc);
      boolean playerEarnedRewards = !eligiblePlayers.isEmpty();
      if (!playerEarnedRewards) {
         String damageSource = lastDamage == null ? "unknown" : lastDamage.getCause().name();
         if (lastDamage instanceof EntityDamageByEntityEvent damageByEntity) {
            damageSource = damageSource + " from " + damageByEntity.getDamager().getType();
         }

         this.logger
            .warning(
               "[BossEvent] Boss died without a qualified player contribution: "
                  + bc.displayName
                  + ", cause="
                  + damageSource
                  + ", requiredDamage="
                  + this.bossRewardThresholdHealth * this.config.bossRewardDamageThresholdPercent / 100.0
                  + ", entity="
                  + this.currentBoss.getType()
                  + ", uuid="
                  + this.currentBoss.getUniqueId()
            );
      }

      if (playerEarnedRewards) {
         Bukkit.broadcastMessage("§a§l\ud83c\udf89 " + bc.displayName + " đã bị hạ gục; người chơi đủ đóng góp nhận điểm và vật phẩm.");
      } else {
         Bukkit.broadcastMessage(
            "§c" + bc.displayName + " đã chết nhưng không có người chơi đạt ngưỡng sát thương " + this.config.bossRewardDamageThresholdPercent + "%."
         );
      }

      if (playerEarnedRewards) {
         if (this.currentBoss != null && this.plugin.getEconomyManager() != null) {
            this.plugin.getEconomyManager().dropBossCoins(this.currentBoss.getLocation(), bc.displayName);
         }
         for (Entry<String, ConfigManager.DropConfig> dropEntry : bc.drops.entrySet()) {
            ConfigManager.DropConfig dc = dropEntry.getValue();
            if (Double.isFinite(dc.chance) && dc.chance >= 0.0 && dc.chance <= 1.0 && this.random.nextDouble() < dc.chance) {
               String materialName = dropEntry.getKey();

               try {
                  Material material = Material.valueOf(materialName.toUpperCase());
                  if (!material.isAir() && dc.minAmount >= 1 && dc.maxAmount >= dc.minAmount && dc.maxAmount <= 64) {
                     int amount = this.random.nextInt(dc.maxAmount - dc.minAmount + 1) + dc.minAmount;
                     ItemStack dropItem = new ItemStack(material);
                     String itemName = dc.name;
                     List<String> itemLore = dc.lore;
                     if (material == Material.ENCHANTED_BOOK) {
                        if (itemName == null || itemName.isBlank()) {
                           itemName = bc.displayName + " §dBí Kíp";
                        }

                        if (itemLore.isEmpty()) {
                           itemLore = List.of("§7Sách enchant dùng lên đồ.");
                        }
                     }

                     BossLeaderboardManager.applyMetadata(dropItem, itemName, itemLore, null, null);
                     if (material == Material.ENCHANTED_BOOK && dc.bookEnchantments.isEmpty()) {
                        this.addConfiguredEnchantment(dropItem, "unbreaking", 1, true);
                     }

                     for (Entry<String, Integer> enchantment : dc.bookEnchantments.entrySet()) {
                        this.addConfiguredEnchantment(dropItem, enchantment.getKey(), enchantment.getValue(), true);
                     }

                     if (this.currentBoss != null) {
                        this.dropItemsNaturally(this.currentBoss.getLocation(), dropItem, amount);
                     }
                  }
               } catch (IllegalArgumentException e) {
                  this.logger.warning("[BossEvent] Invalid drop material: " + materialName);
               }
            }
         }

         if (this.currentBoss != null) {
            for (ConfigManager.ConfiguredItem configuredItem : this.levelDropsFor(bc, this.currentBossLevel)) {
               if (!(this.random.nextDouble() >= configuredItem.chance)) {
                  Material material = Material.matchMaterial(configuredItem.material);
                  if (material != null && !material.isAir()) {
                     int amount = this.random.nextInt(configuredItem.maxAmount - configuredItem.minAmount + 1) + configuredItem.minAmount;
                     ItemStack item = new ItemStack(material);
                     BossLeaderboardManager.applyMetadata(item, configuredItem.name, configuredItem.lore, null, null);

                     for (Entry<String, Integer> enchantment : configuredItem.enchantments.entrySet()) {
                        this.addConfiguredEnchantment(item, enchantment.getKey(), enchantment.getValue(), false);
                     }

                     for (Entry<String, Integer> enchantment : configuredItem.bookEnchantments.entrySet()) {
                        this.addConfiguredEnchantment(item, enchantment.getKey(), enchantment.getValue(), true);
                     }

                     this.dropItemsNaturally(this.currentBoss.getLocation(), item, amount);
                  } else {
                     this.logger.warning("[BossEvent] Invalid configured drop material: " + configuredItem.material);
                  }
               }
            }

            this.dropBossArmorSet(bc, this.currentBossLevel);
            this.dropMercenaryGearSet(bc, this.currentBossLevel);
         }
      }

      if (this.currentBoss != null && this.bossDamageContributions.values().stream().anyMatch(damage -> damage > 0.0)) {
         this.dropArmySummonToken(this.currentBoss.getLocation(), this.currentBossLevel, bc.armyTokenClass);
      }

      this.leaderboardManager.finishBoss(this.currentBossLevel, Map.copyOf(this.bossDamageContributions));
      this.logManager
         .logDisaster(
            "BOSS: "
               + bc.displayName
               + (playerEarnedRewards ? " defeated; eligible contributors=" + eligiblePlayers : " died without eligible contribution")
               + "; cause="
               + (lastDamage == null ? "unknown" : lastDamage.getCause().name())
         );
   }

   private void awardBossExperience(ConfigManager.BossConfig bossConfig) {
      int level = Math.max(1, Math.min(10, this.currentBossLevel));
      int experiencePool = (int)Math.round(
         this.config.bossExperienceLevelOne + (this.config.bossExperienceLevelTen - this.config.bossExperienceLevelOne) * (level - 1) / 9.0
      );
      Map<UUID, Double> onlineContributions = new HashMap<>();

      for (Entry<UUID, Double> entry : this.bossDamageContributions.entrySet()) {
         Player player = Bukkit.getPlayer(entry.getKey());
         if (entry.getValue() > 0.0 && player != null && player.isOnline()) {
            onlineContributions.put(entry.getKey(), entry.getValue());
         }
      }

      double totalDamage = onlineContributions.values().stream().mapToDouble(Double::doubleValue).sum();
      if (!(totalDamage <= 0.0)) {
         Map<UUID, Integer> experienceShares = new HashMap<>();
         Map<UUID, Double> fractionalShares = new HashMap<>();
         int distributedExperience = 0;

         for (Entry<UUID, Double> entry : onlineContributions.entrySet()) {
            double exactShare = experiencePool * entry.getValue() / totalDamage;
            int wholeShare = (int)Math.floor(exactShare);
            experienceShares.put(entry.getKey(), wholeShare);
            fractionalShares.put(entry.getKey(), exactShare - wholeShare);
            distributedExperience += wholeShare;
         }

         int remainingExperience = experiencePool - distributedExperience;
         List<UUID> remainderRecipients = onlineContributions.keySet()
            .stream()
            .sorted(Comparator.<UUID>comparingDouble(fractionalShares::get).reversed().thenComparing(UUID::toString))
            .toList();

         for (int index = 0; index < remainingExperience; index++) {
            UUID recipient = remainderRecipients.get(index % remainderRecipients.size());
            experienceShares.merge(recipient, 1, Integer::sum);
         }

         int playersRewarded = 0;
         int mercenariesRewarded = 0;

         for (Entry<UUID, Integer> entry : experienceShares.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline() && entry.getValue() > 0) {
               player.giveExp(entry.getValue());
               playersRewarded++;
               if (this.plugin.getMercenaryManager() != null) {
                  mercenariesRewarded += this.plugin.getMercenaryManager().awardBossExperience(player.getUniqueId(), entry.getValue());
               }

               player.sendMessage("§aBạn nhận được §e" + entry.getValue() + " EXP§a khi hạ " + bossConfig.displayName + ".");
            }
         }

         this.logger
            .info(
               "[BossEvent] Shared "
                  + experiencePool
                  + " XP across "
                  + playersRewarded
                  + " contributor(s) and "
                  + mercenariesRewarded
                  + " deployed mercenary unit(s)."
            );
      }
   }

   private List<ConfigManager.ConfiguredItem> levelDropsFor(ConfigManager.BossConfig bc, int level) {
      if (bc.levelDrops.isEmpty()) {
         return List.of();
      }

      int selectedLevel = bc.levelDrops.keySet().stream().filter(configuredLevel -> configuredLevel <= level).mapToInt(Integer::intValue).max().orElse(-1);
      return selectedLevel < 0 ? List.of() : bc.levelDrops.getOrDefault(selectedLevel, List.of());
   }

   private void dropMercenaryGearSet(ConfigManager.BossConfig bossConfig, int level) {
      ConfigManager.BossMercenaryGearDropConfig gearDrops = bossConfig.mercenaryGearDrops;
      if (gearDrops != null && gearDrops.enabled) {
         ConfigManager.BossMercenaryGearTier tier = this.mercenaryGearTierForLevel(gearDrops, level);
         if (tier == null) {
            this.logger.warning("[BossEvent] No mercenary gear tier is configured at or below level " + level + " for " + bossConfig.displayName + ".");
         } else {
            MercenaryManager mercenaryManager = this.plugin.getMercenaryManager();
            if (mercenaryManager == null) {
               this.logger.warning("[BossEvent] Mercenary manager is unavailable; skipping gear loot for " + bossConfig.displayName + ".");
            } else {
               List<ItemStack> gearItems;
               try {
                  gearItems = mercenaryManager.createRandomBossGearSet(tier.material, tier.stars);
               } catch (IllegalArgumentException | IllegalStateException exception) {
                  this.logger.warning("[BossEvent] Cannot create mercenary gear loot for " + bossConfig.displayName + ": " + exception.getMessage());
                  return;
               }

               double chance = gearDrops.chanceFor(tier.material, tier.stars);

               for (ItemStack item : gearItems) {
                  if (this.random.nextDouble() < chance) {
                     this.dropItemsNaturally(this.currentBoss.getLocation(), item, 1);
                  }
               }
            }
         }
      }
   }

   private ConfigManager.BossMercenaryGearTier mercenaryGearTierForLevel(ConfigManager.BossMercenaryGearDropConfig gearDrops, int level) {
      int bossLevel = Math.max(1, Math.min(10, level));
      int selectedLevel = gearDrops.levelTiers
         .keySet()
         .stream()
         .filter(configuredLevel -> configuredLevel <= bossLevel)
         .mapToInt(Integer::intValue)
         .max()
         .orElse(-1);
      return selectedLevel < 0 ? null : gearDrops.levelTiers.get(selectedLevel);
   }

   private void dropArmySummonToken(Location location, int level, String classId) {
      if (this.config.bossArmyTokenDropEnabled && !(this.random.nextDouble() >= this.config.bossArmyTokenDropChance)) {
         if (this.plugin.getMercenaryManager() != null) {
            ItemStack token = this.plugin.getMercenaryManager().createBossSummonToken(classId, level);
            if (token == null) {
               this.logger.warning("[BossEvent] Invalid army-token-class '" + classId + "'; no token dropped.");
            } else {
               this.dropItemsNaturally(location, token, 1);
            }
         }
      }
   }

   private void dropBossArmorSet(ConfigManager.BossConfig bossConfig, int level) {
      ConfigManager.BossArmorSetConfig armorSet = bossConfig.armorSet;
      if (armorSet != null && armorSet.enabled && !armorSet.materialTiers.isEmpty()) {
         int tierLevel = armorSet.materialTiers
            .keySet()
            .stream()
            .filter(configuredLevel -> configuredLevel <= level)
            .mapToInt(Integer::intValue)
            .max()
            .orElse(-1);
         if (tierLevel >= 0) {
            String materialTier = armorSet.materialTiers.get(tierLevel);
            String[][] pieces = new String[][]{{"HELMET", "Mũ"}, {"CHESTPLATE", "Áo Giáp"}, {"LEGGINGS", "Quần Giáp"}, {"BOOTS", "Ủng"}};
            List<String> fallbackLore = this.config.getBossItemStory(armorSet.storyId, level);

            for (String[] piece : pieces) {
               if (!(this.random.nextDouble() >= armorSet.dropChance)) {
                  String slot = piece[0].toLowerCase(Locale.ROOT);
                  ConfigManager.BossArmorPieceStory pieceStory = this.config.getBossArmorPieceStory(armorSet.storyId, slot, level);
                  List<String> lore = pieceStory != null && !pieceStory.lore.isEmpty() ? pieceStory.lore : fallbackLore;
                  if (lore.isEmpty()) {
                     this.logger.warning("[BossEvent] Armor set story is unavailable for " + armorSet.storyId + ":" + slot);
                  } else {
                     Material material = Material.matchMaterial(materialTier + "_" + piece[0]);
                     if (material != null && !material.isAir()) {
                        ItemStack item = new ItemStack(material);
                        String configuredName = pieceStory != null && pieceStory.itemName != null
                           ? pieceStory.itemName
                           : armorSet.pieceNames.getOrDefault(piece[0], piece[1]);
                        String itemName = configuredName + " · Cấp " + level;
                        BossLeaderboardManager.applyMetadata(item, itemName, lore, null, null);
                        ItemMeta meta = item.getItemMeta();
                        meta.getPersistentDataContainer().set(this.bossArmorSetKey, PersistentDataType.STRING, armorSet.storyId);
                        item.setItemMeta(meta);
                        List<String> enchants = armorSet.enchantmentsBySlot.getOrDefault(piece[0], this.armorEnchantments(piece[0]));
                        int enchantCount = Math.min(4, level == 1 ? 2 : (level == 2 ? 3 : 4));

                        for (int index = 0; index < Math.min(enchantCount, enchants.size()); index++) {
                           String enchantment = enchants.get(index);
                           int enchantmentLevel = this.armorEnchantmentLevel(enchantment, slot, index, level);
                           this.addArmorEnchantment(item, enchantment, enchantmentLevel);
                        }

                        this.dropItemsNaturally(this.currentBoss.getLocation(), item, 1);
                     } else {
                        this.logger.warning("[BossEvent] Invalid armor material tier '" + materialTier + "' for " + bossConfig.displayName + ".");
                     }
                  }
               }
            }
         }
      }
   }

   private List<String> armorEnchantments(String armorSlot) {
      return switch (armorSlot) {
         case "HELMET" -> List.of("protection", "unbreaking", "respiration", "aqua_affinity");
         case "CHESTPLATE" -> List.of("protection", "unbreaking", "thorns");
         case "LEGGINGS" -> List.of("protection", "unbreaking", "swift_sneak");
         case "BOOTS" -> List.of("protection", "unbreaking", "feather_falling", "depth_strider");
         default -> List.of();
      };
   }

   private int armorEnchantmentLevel(String enchantmentKey, String slot, int enchantIndex, int bossLevel) {
      Enchantment enchantment;
      try {
         enchantment = Enchantment.getByKey(NamespacedKey.minecraft(enchantmentKey));
      } catch (IllegalArgumentException exception) {
         enchantment = null;
      }

      if (enchantment == null) {
         this.logger.warning("[BossEvent] Unknown armor enchantment: " + enchantmentKey);
         return 0;
      }

      if (bossLevel <= 3) {
         return 1;
      }

      if ((slot.equals("CHESTPLATE") || slot.equals("BOOTS")) && enchantIndex == 0) {
         return bossLevel <= 7 ? bossLevel - 2 : 5;
      }

      int naturalMaximum = enchantment.getMaxLevel();
      if ((slot.equals("CHESTPLATE") || slot.equals("BOOTS")) && enchantIndex == 1 && bossLevel >= 8) {
         return Math.min(Math.min(naturalMaximum, this.config.bossArmorMaximumEnchantLevel), bossLevel - 6);
      }

      int scaled = 1 + Math.max(0, bossLevel - 3) / 2;
      return Math.min(naturalMaximum, scaled);
   }

   private void addArmorEnchantment(ItemStack item, String enchantmentKey, int level) {
      Enchantment enchantment;
      try {
         enchantment = Enchantment.getByKey(NamespacedKey.minecraft(enchantmentKey));
      } catch (IllegalArgumentException exception) {
         enchantment = null;
      }

      if (enchantment != null && level >= 1) {
         int appliedLevel = this.config.bossArmorAllowUnsafeEnchants
            ? Math.min(this.config.bossArmorMaximumEnchantLevel, level)
            : Math.min(enchantment.getMaxLevel(), level);

         for (Enchantment existing : item.getEnchantments().keySet()) {
            if (enchantment.conflictsWith(existing)) {
               this.logger.warning("[BossEvent] Skipping conflicting armor enchantment " + enchantmentKey + ".");
               return;
            }
         }

         if (appliedLevel > enchantment.getMaxLevel()) {
            item.addUnsafeEnchantment(enchantment, appliedLevel);
         } else {
            item.addEnchantment(enchantment, appliedLevel);
         }
      }
   }

   private int countArmorSetPieces(Player player, String storyId) {
      int count = 0;

      for (ItemStack item : player.getInventory().getArmorContents()) {
         if (item != null && !item.getType().isAir() && item.hasItemMeta()) {
            String itemStory = (String)item.getItemMeta().getPersistentDataContainer().get(this.bossArmorSetKey, PersistentDataType.STRING);
            if (storyId.equals(itemStory)) {
               count++;
            }
         }
      }

      return count;
   }

   private ConfigManager.BossArmorBonusConfig highestArmorSetBonus(ConfigManager.BossArmorSetConfig armorSet, int pieces) {
      for (int threshold = Math.min(4, pieces); threshold >= 2; threshold--) {
         ConfigManager.BossArmorBonusConfig bonus = armorSet.bonuses.get(threshold);
         if (bonus != null) {
            return bonus;
         }
      }

      return null;
   }

   private void broadcastPossibleDrops(ConfigManager.BossConfig bc, int level) {
      Bukkit.broadcastMessage("§d§lVật phẩm có thể rơi (tỉ lệ từng món):");

      for (Entry<String, ConfigManager.DropConfig> entry : bc.drops.entrySet()) {
         ConfigManager.DropConfig drop = entry.getValue();
         if (!(drop.chance < 0.0) && !(drop.chance > 1.0) && Double.isFinite(drop.chance)) {
            String itemName = drop.name;
            if (itemName == null || itemName.isBlank()) {
               itemName = entry.getKey().equalsIgnoreCase("ENCHANTED_BOOK") ? bc.displayName + " §dBí Kíp" : entry.getKey();
            }

            Bukkit.broadcastMessage("§d• " + itemName + " §7— §e" + String.format(Locale.US, "%.1f%%", drop.chance * 100.0));
         }
      }

      for (ConfigManager.ConfiguredItem item : this.levelDropsFor(bc, level)) {
         String displayName = item.name != null && !item.name.isBlank() ? item.name : item.material;
         Bukkit.broadcastMessage("§d• " + displayName + " §7— §e" + String.format(Locale.US, "%.1f%%", item.chance * 100.0));
      }

      if (bc.armorSet != null && bc.armorSet.enabled) {
         Bukkit.broadcastMessage("§d• " + bc.armorSet.name + " (4 món) §7— §e" + String.format(Locale.US, "%.1f%% mỗi món", bc.armorSet.dropChance * 100.0));
      }

      ConfigManager.BossMercenaryGearDropConfig gearDrops = bc.mercenaryGearDrops;
      ConfigManager.BossMercenaryGearTier tier = gearDrops != null && gearDrops.enabled ? this.mercenaryGearTierForLevel(gearDrops, level) : null;
      if (tier != null) {
         Bukkit.broadcastMessage(
            "§b• Gear lính đánh thuê ngẫu nhiên §7— §e"
               + String.format(Locale.US, "%.1f%% mỗi món", gearDrops.chanceFor(tier.material, tier.stars))
               + " §7("
               + tier.material
               + " "
               + "★".repeat(tier.stars)
               + ", class/set ngẫu nhiên)"
         );
      }
   }

   private void addConfiguredEnchantment(ItemStack item, String name, int level, boolean stored) {
      if (level >= 1 && level <= 255) {
         String id = name.toLowerCase(Locale.ROOT);
         if (id.equals("damage_all")) {
            id = "sharpness";
         } else if (id.equals("damage_undead")) {
            id = "smite";
         } else if (id.equals("damage_arthropods")) {
            id = "bane_of_arthropods";
         } else if (id.equals("durability")) {
            id = "unbreaking";
         } else if (id.equals("loot_bonus_mobs")) {
            id = "looting";
         }

         Enchantment enchantment;
         try {
            enchantment = Enchantment.getByKey(NamespacedKey.minecraft(id));
         } catch (IllegalArgumentException exception) {
            enchantment = null;
         }

         if (enchantment == null) {
            this.logger.warning("[BossEvent] Unknown configured enchantment: " + name);
         } else {
            if (stored && item.getItemMeta() instanceof EnchantmentStorageMeta meta) {
               meta.addStoredEnchant(enchantment, level, true);
               item.setItemMeta(meta);
            } else if (!stored) {
               item.addUnsafeEnchantment(enchantment, level);
            }
         }
      }
   }

   private void dropItemsNaturally(Location location, ItemStack template, int amount) {
      int remaining = amount;
      int stackLimit = Math.max(1, template.getMaxStackSize());

      while (remaining > 0) {
         ItemStack stack = template.clone();
         int stackAmount = Math.min(remaining, stackLimit);
         stack.setAmount(stackAmount);
         location.getWorld().dropItemNaturally(location, stack);
         remaining -= stackAmount;
      }
   }

   private void cleanup() {
      this.leaderboardManager.finishBoss(this.currentBossLevel, false);
      if (this.aiTask != null) {
         this.aiTask.cancel();
         this.aiTask = null;
      }

      if (this.bossMonitorTask != null) {
         this.bossMonitorTask.cancel();
         this.bossMonitorTask = null;
      }

      for (Entity projectile : this.activeBossProjectiles) {
         if (projectile != null && !projectile.isDead()) {
            projectile.remove();
         }
      }

      this.activeBossProjectiles.clear();

      for (BukkitTask task : this.bossSkillTasks) {
         if (task != null) {
            task.cancel();
         }
      }

      this.bossSkillTasks.clear();
      this.creeperChargeTask = null;
      this.nextCreeperExplosionAtMs = 0L;
      this.bossProjectileConfigs.clear();
      this.bossSkillCooldowns.clear();
      this.splitFireballs.clear();
      this.webTrapProjectiles.clear();
      this.disarmProjectiles.clear();
      this.ravagerThornsCooldowns.clear();
      this.spinDamageCooldowns.clear();
      this.phantomRevealedUntil.clear();

      for (UUID summonId : this.bossSummons) {
         Entity summon = Bukkit.getEntity(summonId);
         if (summon != null && summon.isValid()) {
            summon.remove();
         }
      }

      this.bossSummons.clear();
      this.bossTerrainEntities.clear();
      this.bossTerrainDrops.clear();
      this.bossBoulders.clear();

      for (BossEventManager.BlockPosition position : new ArrayList<>(this.temporaryBlocks.keySet())) {
         this.restoreTemporaryBlock(position);
      }

      this.playerModifiedTemporaryBlocks.clear();
      if (this.currentBoss != null && !this.currentBoss.isDead()) {
         this.currentBoss.remove();
      }

      if (this.forcedBossChunkWorld != null && !this.bossChunkWasAlreadyForceLoaded) {
         this.forcedBossChunkWorld.setChunkForceLoaded(this.forcedBossChunkX, this.forcedBossChunkZ, false);
      }

      this.forcedBossChunkWorld = null;
      this.forcedBossChunkX = 0;
      this.forcedBossChunkZ = 0;
      this.bossChunkWasAlreadyForceLoaded = false;
      this.bossActive = false;
      this.currentBoss = null;
      this.activeBossConfig = null;
      this.currentBossSpawnLocation = null;
      this.currentBossActivityRadius = 100;
      this.returningToAnchor = false;
      this.anchorPathUpdatedAtMs = 0L;
      this.giantWanderTarget = null;
      this.giantWanderUntilMs = 0L;
      this.currentBossName = null;
      this.pendingBoss = null;
      this.pendingBossLevel = null;
      this.warningTimeLeft = 0;
      this.currentBossLevel = 1;
      this.logicalBossMaxHealth = 0.0;
      this.logicalBossHealth = 0.0;
      this.bossUsesVirtualHealth = false;
      this.virtualHealthDamageEvents.clear();
      this.nextBossProjectileDodgeAtMs = 0L;
      this.bossRewardThresholdHealth = 0.0;
      this.bossDamageContributions.clear();
      this.spiderBroodReleased = false;
      this.bossElapsedSeconds = 0;
      this.bossDurationSeconds = 0;
      this.bossBar.name(Component.text(""));
      this.bossBar.progress(0.0F);

      for (Player p : Bukkit.getOnlinePlayers()) {
         p.hideBossBar(this.bossBar);
      }
   }

   private void clearWarningBar() {
      this.warningTimeLeft = 0;
      this.pendingBoss = null;
      this.pendingBossLevel = null;
      this.bossBar.name(Component.text(""));
      this.bossBar.progress(0.0F);

      for (Player player : Bukkit.getOnlinePlayers()) {
         player.hideBossBar(this.bossBar);
      }
   }

   public boolean isBossActive() {
      return this.bossActive;
   }

   public boolean killActiveBoss() {
      if (this.bossActive && this.currentBoss != null && this.currentBoss.isValid() && !this.currentBoss.isDead()) {
         String name = this.currentBossName == null ? "Boss" : this.currentBossName;
         this.cleanup();
         Bukkit.broadcastMessage("§c§l" + name + " đã bị quản trị viên kết thúc.");
         return true;
      } else {
         return false;
      }
   }

   public String getCurrentBossName() {
      return this.currentBossName;
   }

   public boolean isBossOrSummon(Entity entity) {
      return entity != null && (entity.equals(this.currentBoss)
         || this.bossSummons.contains(entity.getUniqueId()));
   }

   public Set<String> getBossIds() {
      return this.config.bossConfigs.keySet();
   }

   public Set<String> getEnabledBossIds() {
      Set<String> ids = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

      for (Entry<String, ConfigManager.BossConfig> entry : this.config.bossConfigs.entrySet()) {
         if (entry.getValue().enabled) {
            ids.add(entry.getKey());
         }
      }

      return ids;
   }

   public String getBossName(String id) {
      ConfigManager.BossConfig bc = this.config.bossConfigs.get(id.toLowerCase());
      return bc == null ? "Unknown Boss" : bc.displayName;
   }

   public String getBossEntityType(String id) {
      ConfigManager.BossConfig bc = this.config.bossConfigs.get(id.toLowerCase());
      return bc == null ? null : bc.entityType;
   }

   public boolean triggerBoss(String bossId, int warningTimeSeconds, int durationSeconds) {
      return this.triggerBoss(bossId, 1, warningTimeSeconds, durationSeconds);
   }

   public boolean triggerBoss(String bossId, int level, int warningTimeSeconds, int durationSeconds) {
      if (this.bossActive || this.pendingBoss != null) {
         return false;
      }

      if (level >= 1 && level <= 1000 && warningTimeSeconds >= 1 && durationSeconds >= 1) {
         ConfigManager.BossConfig bc = this.config.bossConfigs.get(bossId.toLowerCase());
         if (bc != null && bc.enabled) {
            ConfigManager.BossConfig manualBoss = new ConfigManager.BossConfig();
            manualBoss.enabled = bc.enabled;
            manualBoss.entityType = bc.entityType;
            manualBoss.displayName = bc.displayName;
            manualBoss.hp = bc.hp;
            manualBoss.damageMultiplier = bc.damageMultiplier;
            manualBoss.maxPlayerHitDamage = bc.maxPlayerHitDamage;
            manualBoss.chance = bc.chance;
            manualBoss.warningSeconds = warningTimeSeconds;
            manualBoss.durationSeconds = durationSeconds;
            manualBoss.activityRadius = bc.activityRadius;
            manualBoss.mercenaryGearDrops = bc.mercenaryGearDrops;
            manualBoss.immunities.putAll(bc.immunities);
            manualBoss.immunityPotionEffects = new ArrayList<>(bc.immunityPotionEffects);
            manualBoss.drops.putAll(bc.drops);
            bc.levelDrops
               .forEach(
                  (configuredLevel, items) -> manualBoss.levelDrops
                     .put(configuredLevel, new ArrayList<>((Collection<? extends ConfigManager.ConfiguredItem>)items))
               );
            if (bc.hpByLevel != null) {
               manualBoss.hpByLevel.putAll(bc.hpByLevel);
            }

            if (bc.skills != null) {
               manualBoss.skills.putAll(bc.skills);
            }

            if (bc.traits != null) {
               manualBoss.traits.putAll(bc.traits);
            }

            this.startWarning(manualBoss, level);
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public void stop() {
      if (this.schedulerTask != null) {
         this.schedulerTask.cancel();
         this.schedulerTask = null;
      }

      this.cleanup();
      this.timeSinceLastBoss = 0;
      this.bossEventIntervalSeconds = 0;
   }

   public void reload() {
      this.stop();
      if (this.config.bossEventsEnabled) {
         this.start();
      }
   }

   private record BlockPosition(UUID worldId, int x, int y, int z) {
   }

   private record BossProjectileConfig(double damage, double radius, boolean splashOnImpact) {
   }

   private record TemporaryBlock(BlockState original, BlockData placedData) {
   }
}
