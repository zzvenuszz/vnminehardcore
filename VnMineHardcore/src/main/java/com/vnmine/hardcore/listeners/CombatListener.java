package com.vnmine.hardcore.listeners;

import com.vnmine.hardcore.VnMineHardcore;
import com.vnmine.hardcore.managers.ConfigManager;
import com.vnmine.hardcore.managers.DeathPenaltyManager;
import com.vnmine.hardcore.managers.LogManager;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

public class CombatListener implements Listener {

    private final VnMineHardcore plugin;
    private final LogManager logManager;
    private final DeathListener deathListener;
    private final DeathPenaltyManager deathPenaltyManager;
    private final ConfigManager config;
    private final Logger logger;
    private final Random random = new Random();

    // Set of friendly (passive) mob types that should NOT trigger combat tag
    private static final Set<Class<? extends Entity>> FRIENDLY_MOBS = new HashSet<>();

    static {
        FRIENDLY_MOBS.add(Cow.class);
        FRIENDLY_MOBS.add(Sheep.class);
        FRIENDLY_MOBS.add(Pig.class);
        FRIENDLY_MOBS.add(Chicken.class);
        FRIENDLY_MOBS.add(Rabbit.class);
        FRIENDLY_MOBS.add(Horse.class);
        FRIENDLY_MOBS.add(Donkey.class);
        FRIENDLY_MOBS.add(Mule.class);
        FRIENDLY_MOBS.add(Llama.class);
        FRIENDLY_MOBS.add(TraderLlama.class);
        FRIENDLY_MOBS.add(Wolf.class);
        FRIENDLY_MOBS.add(Cat.class);
        FRIENDLY_MOBS.add(Ocelot.class);
        FRIENDLY_MOBS.add(Parrot.class);
        FRIENDLY_MOBS.add(Villager.class);
        FRIENDLY_MOBS.add(WanderingTrader.class);
        FRIENDLY_MOBS.add(IronGolem.class);
        FRIENDLY_MOBS.add(Snowman.class);
        FRIENDLY_MOBS.add(Bee.class);
        FRIENDLY_MOBS.add(Fox.class);
        FRIENDLY_MOBS.add(Panda.class);
        FRIENDLY_MOBS.add(PolarBear.class);
        FRIENDLY_MOBS.add(Turtle.class);
        FRIENDLY_MOBS.add(Dolphin.class);
        FRIENDLY_MOBS.add(Squid.class);
        FRIENDLY_MOBS.add(GlowSquid.class);
        FRIENDLY_MOBS.add(Allay.class);
        FRIENDLY_MOBS.add(Axolotl.class);
        FRIENDLY_MOBS.add(Camel.class);
        FRIENDLY_MOBS.add(Frog.class);
        FRIENDLY_MOBS.add(Goat.class);
        FRIENDLY_MOBS.add(Sniffer.class);
    }

    public CombatListener(VnMineHardcore plugin, DeathListener deathListener, ConfigManager config) {
        this.plugin = plugin;
        this.logManager = plugin.getLogManager();
        this.deathListener = deathListener;
        this.deathPenaltyManager = plugin.getDeathPenaltyManager();
        this.config = config;
        this.logger = plugin.getLogger();
        logger.info("[Combat] Initialized: mob=x" + config.mobDamageMultiplier +
            " fall=x" + config.fallDamageMultiplier +
            " regen=" + (config.disableNaturalRegen ? "OFF" : "x" + config.regenMultiplier));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        double original = event.getDamage();
        double newDamage = original;

        switch (event.getCause()) {
            case FALL -> {
                newDamage = original * config.fallDamageMultiplier;
                if (original > 0) {
                    player.sendActionBar("§c§l⚠ Ngã! §7Sát thương x" + config.fallDamageMultiplier + "!");
                    logger.fine("[Combat] " + player.getName() + " fall: " + original + "->" + newDamage);
                }
            }
            case FIRE, FIRE_TICK, LAVA -> {
                newDamage = original * config.fireDamageMultiplier;
                if (player.getFireTicks() > 0) player.setFireTicks(player.getFireTicks() * 2);
                player.sendActionBar("§c§l🔥 Lửa! §7Sát thương x" + config.fireDamageMultiplier + "!");
                logger.fine("[Combat] " + player.getName() + " fire: " + original + "->" + newDamage);
            }
            case DROWNING -> {
                newDamage = original * config.drowningDamageMultiplier;
                player.setRemainingAir(Math.max(0, player.getRemainingAir() - 10));
                logger.fine("[Combat] " + player.getName() + " drown: " + original + "->" + newDamage);
            }
            case ENTITY_EXPLOSION, BLOCK_EXPLOSION -> {
                newDamage = original * config.explosionDamageMultiplier;
                logger.fine("[Combat] " + player.getName() + " explode: " + original + "->" + newDamage);
            }
            case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK -> {
                if (event instanceof EntityDamageByEntityEvent entityEvent) {
                    Entity damager = entityEvent.getDamager();
                    if (damager instanceof Monster || damager instanceof Phantom || damager instanceof Slime) {
                        newDamage = original * config.mobDamageMultiplier;
                        player.sendActionBar("§c§l⚔ Quái! §7Sát thương x" + config.mobDamageMultiplier + "!");
                        deathListener.tagCombat(player);
                        logger.fine("[Combat] " + player.getName() + " mob from " + damager.getType() + ": " + original + "->" + newDamage);
                    } else if (damager instanceof Player attacker) {
                        newDamage = original * config.pvpDamageMultiplier;
                        player.sendActionBar("§c§l⚔ PvP! §7Sát thương x" + config.pvpDamageMultiplier + "!");
                        deathListener.tagCombat(player);
                        deathListener.tagCombat(attacker);
                        logger.fine("[Combat] PvP " + attacker.getName() + "->" + player.getName() + ": " + original + "->" + newDamage);
                    }
                }
            }
            case PROJECTILE -> {
                if (event instanceof EntityDamageByEntityEvent entityEvent && entityEvent.getDamager() instanceof Arrow arrow) {
                    ProjectileSource shooter = arrow.getShooter();
                    if (shooter instanceof Skeleton || shooter instanceof Stray) {
                        newDamage = original * config.mobDamageMultiplier;
                        player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 0));
                    }
                    deathListener.tagCombat(player);
                }
            }
            case MAGIC -> newDamage = original * config.mobDamageMultiplier;
            case POISON, WITHER -> newDamage = original * 2.0;
        }

        // Apply death penalty: incoming damage multiplier
        newDamage *= deathPenaltyManager.getIncomingDamageMultiplier(player);

        event.setDamage(newDamage);
    }

    /**
     * Tag combat khi player CHỦ ĐỘNG tấn công entity.
     * Chỉ tag nếu entity là hostile mob hoặc player (PvP).
     * Không tag nếu tấn công friendly/passive mob.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerAttackEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (event.getEntity() == attacker) return; // self-damage

        Entity target = event.getEntity();

        // PvP - tag both
        if (target instanceof Player targetPlayer) {
            deathListener.tagCombat(attacker);
            deathListener.tagCombat(targetPlayer);
            logger.fine("[Combat] " + attacker.getName() + " attacked player " + targetPlayer.getName() + " - combat tagged");
            return;
        }

        // Check if target is a hostile mob
        if (target instanceof Monster || target instanceof Phantom || target instanceof Slime) {
            deathListener.tagCombat(attacker);
            logger.fine("[Combat] " + attacker.getName() + " attacked hostile mob " + target.getType() + " - combat tagged");
            return;
        }

        // Check if target is a projectile shooter (like Skeleton arrow)
        if (target instanceof AbstractArrow || target instanceof Trident) {
            // Don't tag for projectiles - the projectile owner is already handled
            return;
        }

        // For other entities (friendly/passive) - DO NOT tag combat
        // The FRIENDLY_MOBS set is used as reference but we simply don't tag for non-hostile
        logger.fine("[Combat] " + attacker.getName() + " attacked " + target.getType() + " - friendly, no combat tag");
    }

    @EventHandler
    public void onEntitySpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;

        if (mob instanceof Zombie || mob instanceof Skeleton || mob instanceof Creeper) {
            mob.setMaxHealth(mob.getMaxHealth() * config.mobExtraHpMultiplier);
            mob.setHealth(mob.getMaxHealth());

            if (random.nextBoolean()) {
                switch (random.nextInt(3)) {
                    case 0 -> mob.getEquipment().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
                    case 1 -> mob.getEquipment().setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
                    case 2 -> mob.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
                }
                mob.getEquipment().setChestplateDropChance(0.0f);
            }

            if (random.nextInt(3) == 0) {
                mob.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
                mob.getEquipment().setItemInMainHandDropChance(0.0f);
            }
        }

        if (mob instanceof Witch witch) {
            witch.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() instanceof Player player) {
            logManager.logMobKill(player);
        }
    }

    @EventHandler
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        EntityRegainHealthEvent.RegainReason reason = event.getRegainReason();

        // Only affect natural regen (SATIATED = natural regen from full food bar)
        if (reason == EntityRegainHealthEvent.RegainReason.SATIATED ||
            reason == EntityRegainHealthEvent.RegainReason.REGEN) {

            if (config.disableNaturalRegen) {
                // Completely block natural regen
                event.setCancelled(true);
                return;
            }

            // Apply regen multiplier if less than 1.0
            if (config.regenMultiplier < 1.0) {
                double original = event.getAmount();
                double newAmount = original * config.regenMultiplier;
                event.setAmount(newAmount);
                logger.fine("[Combat] Regen: " + original + " -> " + newAmount + " (x" + config.regenMultiplier + ")");
            }
        }
    }

    @EventHandler
    public void onCreeperExplode(EntityExplodeEvent event) {
        if (event.getEntity() instanceof Creeper) {
            event.setYield(event.getYield() * 2);
        }
    }

    @EventHandler
    public void onEnderPearlUse(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player && event.getEntity() instanceof EnderPearl) {
            player.damage(config.enderPearlDamage);
            player.sendMessage("§c§l⚠ Ender Pearl gây sát thương! (-" + (config.enderPearlDamage / 2) + " HP)");
            logger.info("[Combat] " + player.getName() + " used ender pearl - damaged");
        }
    }

    // Status accessors
    public double getMobDamageMultiplier() { return config.mobDamageMultiplier; }
    public double getFallDamageMultiplier() { return config.fallDamageMultiplier; }
    public double getFireDamageMultiplier() { return config.fireDamageMultiplier; }
    public double getDrowningDamageMultiplier() { return config.drowningDamageMultiplier; }
    public double getExplosionDamageMultiplier() { return config.explosionDamageMultiplier; }
    public double getPvpDamageMultiplier() { return config.pvpDamageMultiplier; }
    public boolean isNaturalRegenDisabled() { return config.disableNaturalRegen; }
}