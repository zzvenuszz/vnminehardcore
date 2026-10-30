package com.vnmine.hardcore.listeners;

import com.vnmine.hardcore.VnMineHardcore;
import com.vnmine.hardcore.managers.ConfigManager;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

public class OreControlListener implements Listener {

    private final VnMineHardcore plugin;
    private final ConfigManager config;
    private final Logger logger;
    private final Random random = new Random();
    private ExplosionContext activeExplosion;

    private record ExplosionContext(World world, Location location, double radius, boolean damagePlayer) {}

    private static final Set<Material> ORE_MATERIALS = new HashSet<>();

    static {
        ORE_MATERIALS.add(Material.DIAMOND_ORE);
        ORE_MATERIALS.add(Material.EMERALD_ORE);
        ORE_MATERIALS.add(Material.IRON_ORE);
        ORE_MATERIALS.add(Material.GOLD_ORE);
        ORE_MATERIALS.add(Material.COAL_ORE);
        ORE_MATERIALS.add(Material.LAPIS_ORE);
        ORE_MATERIALS.add(Material.REDSTONE_ORE);
        ORE_MATERIALS.add(Material.COPPER_ORE);
        ORE_MATERIALS.add(Material.DEEPSLATE_DIAMOND_ORE);
        ORE_MATERIALS.add(Material.DEEPSLATE_EMERALD_ORE);
        ORE_MATERIALS.add(Material.DEEPSLATE_IRON_ORE);
        ORE_MATERIALS.add(Material.DEEPSLATE_GOLD_ORE);
        ORE_MATERIALS.add(Material.DEEPSLATE_COAL_ORE);
        ORE_MATERIALS.add(Material.DEEPSLATE_LAPIS_ORE);
        ORE_MATERIALS.add(Material.DEEPSLATE_REDSTONE_ORE);
        ORE_MATERIALS.add(Material.DEEPSLATE_COPPER_ORE);
        ORE_MATERIALS.add(Material.NETHER_QUARTZ_ORE);
        ORE_MATERIALS.add(Material.NETHER_GOLD_ORE);
        ORE_MATERIALS.add(Material.ANCIENT_DEBRIS);
    }

    private static final Map<Material, Material> ORE_REPLACEMENT = Map.ofEntries(
        Map.entry(Material.DIAMOND_ORE, Material.STONE),
        Map.entry(Material.EMERALD_ORE, Material.STONE),
        Map.entry(Material.IRON_ORE, Material.STONE),
        Map.entry(Material.GOLD_ORE, Material.STONE),
        Map.entry(Material.COAL_ORE, Material.STONE),
        Map.entry(Material.LAPIS_ORE, Material.STONE),
        Map.entry(Material.REDSTONE_ORE, Material.STONE),
        Map.entry(Material.COPPER_ORE, Material.STONE),
        Map.entry(Material.DEEPSLATE_DIAMOND_ORE, Material.DEEPSLATE),
        Map.entry(Material.DEEPSLATE_EMERALD_ORE, Material.DEEPSLATE),
        Map.entry(Material.DEEPSLATE_IRON_ORE, Material.DEEPSLATE),
        Map.entry(Material.DEEPSLATE_GOLD_ORE, Material.DEEPSLATE),
        Map.entry(Material.DEEPSLATE_COAL_ORE, Material.DEEPSLATE),
        Map.entry(Material.DEEPSLATE_LAPIS_ORE, Material.DEEPSLATE),
        Map.entry(Material.DEEPSLATE_REDSTONE_ORE, Material.DEEPSLATE),
        Map.entry(Material.DEEPSLATE_COPPER_ORE, Material.DEEPSLATE),
        Map.entry(Material.NETHER_QUARTZ_ORE, Material.NETHERRACK),
        Map.entry(Material.NETHER_GOLD_ORE, Material.NETHERRACK),
        Map.entry(Material.ANCIENT_DEBRIS, Material.NETHERRACK)
    );

    public OreControlListener(VnMineHardcore plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.logger = plugin.getLogger();
        logger.info("[OreControl] Initialized: enabled=" + config.oreControlEnabled +
            ", worlds=" + config.oreControlWorlds.size());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChunkPopulate(ChunkPopulateEvent event) {
        if (!config.oreControlEnabled) return;

        Chunk chunk = event.getChunk();
        World world = chunk.getWorld();
        Map<String, Double> oreRates = config.oreControlWorlds.get(world.getName());
        if (oreRates == null || oreRates.isEmpty()) return;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                    Block block = chunk.getBlock(x, y, z);
                    Material type = block.getType();
                    if (!ORE_MATERIALS.contains(type)) continue;

                    Double targetRate = oreRates.get(type.name());
                    if (targetRate != null && random.nextDouble() * 100 > targetRate) {
                        block.setType(ORE_REPLACEMENT.getOrDefault(type, Material.STONE));
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!config.blockDropSettingsEnabled || event.getPlayer().getGameMode() == GameMode.CREATIVE) return;

        Map<Material, ConfigManager.BlockMiningRule> toolRules =
            config.blockMiningRules.get(event.getBlock().getType());
        if (toolRules == null) return;
        ConfigManager.BlockMiningRule rule =
            toolRules.get(event.getPlayer().getInventory().getItemInMainHand().getType());
        if (rule == null) return;

        if (config.dropReplaceEnabled && rule.replaceEnabled && rule.replaceMaterial != null &&
            rollChance(rule.replaceChance)) {
            event.setDropItems(false);
            event.getBlock().getWorld().dropItemNaturally(
                event.getBlock().getLocation().add(0.5, 0.5, 0.5),
                new ItemStack(rule.replaceMaterial)
            );
        }

        Player player = event.getPlayer();
        if (config.toolBreakEnabled && rule.toolBreakEnabled && rollChance(rule.toolBreakChance)) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            sendConfiguredMessage(player, rule.toolBreakMessage);
        }

        if (config.oreExplodeEnabled && rule.explodeEnabled && rollChance(rule.explosionChance)) {
            sendConfiguredMessage(player, rule.explosionMessage);
            Location location = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
            ExplosionContext previousExplosion = activeExplosion;
            activeExplosion = new ExplosionContext(
                event.getBlock().getWorld(),
                location,
                rule.explosionPower * 2.0,
                rule.explosionDamagePlayer
            );
            try {
                event.getBlock().getWorld().createExplosion(
                    location,
                    rule.explosionPower,
                    rule.explosionSetFire,
                    rule.explosionBreakBlocks
                );
            } finally {
                activeExplosion = previousExplosion;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosionDamage(EntityDamageEvent event) {
        ExplosionContext explosion = activeExplosion;
        if (explosion == null || explosion.damagePlayer() ||
            !(event.getEntity() instanceof Player player) ||
            event.getCause() != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION ||
            explosion.world() != player.getWorld() ||
            explosion.location().distanceSquared(player.getLocation()) > explosion.radius() * explosion.radius()) {
            return;
        }
        event.setCancelled(true);
    }

    private boolean rollChance(double chance) {
        return random.nextDouble() * 100.0 < chance;
    }

    private void sendConfiguredMessage(Player player, String message) {
        if (message != null && !message.isBlank()) {
            player.sendMessage(com.vnmine.hardcore.managers.LegacyText.translate(message));
        }
    }
}
