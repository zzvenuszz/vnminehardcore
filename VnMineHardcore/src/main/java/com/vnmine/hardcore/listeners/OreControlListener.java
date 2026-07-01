package com.vnmine.hardcore.listeners;

import com.vnmine.hardcore.VnMineHardcore;
import com.vnmine.hardcore.managers.ConfigManager;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkPopulateEvent;

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

    // Danh sách các block quặng cần kiểm soát
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

    // Map ore -> default replacement (stone equivalent)
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
        String worldName = world.getName();

        // Kiểm tra world có trong config không
        Map<String, Double> oreRates = config.oreControlWorlds.get(worldName);
        if (oreRates == null || oreRates.isEmpty()) return;

        // Quét tất cả block trong chunk
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                    Block block = chunk.getBlock(x, y, z);
                    Material type = block.getType();

                    if (ORE_MATERIALS.contains(type)) {
                        String oreName = type.name();
                        Double targetRate = oreRates.get(oreName);

                        if (targetRate != null) {
                            // Tính tỉ lệ hiện tại so với mục tiêu
                            // Nếu random > targetRate/100 thì thay thế bằng đá
                            double randomValue = random.nextDouble() * 100;
                            if (randomValue > targetRate) {
                                Material replacement = ORE_REPLACEMENT.getOrDefault(type, Material.STONE);
                                block.setType(replacement);
                            }
                        }
                    }
                }
            }
        }
    }
}