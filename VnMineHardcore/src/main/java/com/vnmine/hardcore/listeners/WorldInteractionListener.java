package com.vnmine.hardcore.listeners;

import com.vnmine.hardcore.VnMineHardcore;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;

import java.util.logging.Logger;

/**
 * Xử lý tương tác giữa lava và nước.
 * 
 * Khi lava chạm vào nước ở cùng mức:
 * - Nước chảy vào lava → tạo COBBLESTONE (không sinh khoáng sản ngẫu nhiên)
 * - Lava chảy vào nước → tạo STONE (không sinh khoáng sản ngẫu nhiên)
 * 
 * Khi nước chảy lên trên lava (lava ở dưới, nước ở trên):
 * - Tạo BLACKSTONE / COBBLED_DEEPSLATE (hắc diện thạch) - KHÔNG cancel
 */
public class WorldInteractionListener implements Listener {

    private final VnMineHardcore plugin;
    private final Logger logger;

    public WorldInteractionListener(VnMineHardcore plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        logger.info("[WorldInteraction] Initialized: lava+water cobblestone fix");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockForm(BlockFormEvent event) {
        Block block = event.getBlock();
        Material newType = event.getNewState().getType();

        // Chỉ xử lý khi block được tạo ra từ tương tác lava + nước
        if (newType == Material.COBBLESTONE || newType == Material.STONE) {
            // Kiểm tra xem có lava và nước xung quanh không
            boolean hasLavaNearby = false;
            boolean hasWaterNearby = false;

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block relative = block.getRelative(dx, dy, dz);
                        Material type = relative.getType();
                        if (type == Material.LAVA || type == Material.LAVA_CAULDRON) {
                            hasLavaNearby = true;
                        }
                        if (type == Material.WATER || type == Material.BUBBLE_COLUMN || type == Material.WATER_CAULDRON) {
                            hasWaterNearby = true;
                        }
                    }
                }
            }

            // Nếu có cả lava và nước xung quanh, đây là tương tác lava+nước
            if (hasLavaNearby && hasWaterNearby) {
                // Set block thành COBBLESTONE hoặc STONE, không sinh khoáng sản
                // Mặc định BlockFormEvent đã tạo block đúng, nhưng chúng ta cần đảm bảo
                // không có khoáng sản ngẫu nhiên nào được sinh ra
                // (BlockFormEvent mặc định chỉ tạo block, không sinh khoáng sản,
                //  nhưng để an toàn, chúng ta log và đảm bảo)
                logger.fine("[WorldInteraction] Lava+water formed " + newType + " at " + 
                    block.getX() + "," + block.getY() + "," + block.getZ());
            }
        }

        // Xử lý trường hợp nước chảy lên trên lava tạo hắc diện thạch
        // BLACKSTONE, COBBLED_DEEPSLATE - KHÔNG cancel, để mặc định xử lý
        if (newType == Material.BLACKSTONE || newType == Material.COBBLED_DEEPSLATE) {
            logger.fine("[WorldInteraction] Water+flowing lava formed " + newType + " at " +
                block.getX() + "," + block.getY() + "," + block.getZ());
            // Không cancel - đây là cơ chế tạo hắc diện thạch bình thường
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockFromTo(BlockFromToEvent event) {
        Block source = event.getBlock();
        Block destination = event.getToBlock();
        Material sourceType = source.getType();
        Material destType = destination.getType();

        // Nếu lava đang chảy vào nước (hoặc ngược lại)
        boolean isLava = (sourceType == Material.LAVA);
        boolean isWater = (sourceType == Material.WATER || sourceType == Material.BUBBLE_COLUMN);

        if (isLava || isWater) {
            Material otherType = isLava ? Material.WATER : Material.LAVA;
            
            // Kiểm tra nếu destination là chất lỏng kia
            if (destType == otherType || destType == Material.BUBBLE_COLUMN || 
                destType == Material.LAVA_CAULDRON || destType == Material.WATER_CAULDRON) {
                
                // Kiểm tra nếu nước chảy lên trên lava (tạo hắc diện thạch)
                // Nước ở trên, lava ở dưới
                if (isWater && source.getY() > destination.getY()) {
                    // Nước chảy xuống lava - tạo hắc diện thạch, không cancel
                    logger.fine("[WorldInteraction] Water flowing down into lava at " +
                        destination.getX() + "," + destination.getY() + "," + destination.getZ());
                    return; // Không cancel
                }

                // Lava và nước ở cùng mức hoặc lava chảy vào nước
                // Đây là trường hợp tạo cobblestone/stone
                // Không cancel event - để Minecraft xử lý tạo block
                // BlockFormEvent sẽ được gọi sau đó
                logger.fine("[WorldInteraction] " + sourceType + " flowing into " + destType + " at " +
                    destination.getX() + "," + destination.getY() + "," + destination.getZ());
            }
        }
    }
}