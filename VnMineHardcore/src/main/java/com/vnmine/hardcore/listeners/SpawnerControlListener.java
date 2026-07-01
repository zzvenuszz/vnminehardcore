package com.vnmine.hardcore.listeners;

import com.vnmine.hardcore.VnMineHardcore;
import com.vnmine.hardcore.managers.ConfigManager;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;
import java.util.logging.Logger;

public class SpawnerControlListener implements Listener {

    private final VnMineHardcore plugin;
    private final ConfigManager config;
    private final Logger logger;
    private final Random random = new Random();

    public SpawnerControlListener(VnMineHardcore plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.logger = plugin.getLogger();
        logger.info("[SpawnerControl] Initialized: enabled=" + config.spawnerControlEnabled +
            ", rate=" + config.spawnerSpawnRateReduction +
            ", hp=x" + config.spawnerHpMultiplier +
            ", dmg=x" + config.spawnerDamageMultiplier);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!config.spawnerControlEnabled) return;
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER) return;
        if (!(event.getEntity() instanceof Mob mob)) return;

        // Reduce spawn rate
        if (random.nextDouble() > config.spawnerSpawnRateReduction) {
            event.setCancelled(true);
            return;
        }

        // Skip friendly mobs from spawner
        if (!(mob instanceof Monster || mob instanceof Phantom || mob instanceof Slime)) {
            return;
        }

        // Increase HP
        double originalMaxHp = mob.getMaxHealth();
        double newMaxHp = originalMaxHp * config.spawnerHpMultiplier;
        mob.setMaxHealth(newMaxHp);
        mob.setHealth(newMaxHp);

        // Increase damage via Strength effect
        int strengthAmplifier = (int) Math.round(config.spawnerDamageMultiplier - 1.0);
        if (strengthAmplifier >= 0) {
            mob.addPotionEffect(new PotionEffect(
                PotionEffectType.STRENGTH,
                Integer.MAX_VALUE,
                strengthAmplifier,
                false, false
            ));
        }

        logger.fine("[SpawnerControl] " + mob.getType() + " spawned from spawner: HP=" +
            String.format("%.1f", newMaxHp) + " (x" + config.spawnerHpMultiplier + ")");
    }
}