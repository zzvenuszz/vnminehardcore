package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Logger;

public class DeathRenameManager {

    private final VnMineHardcore plugin;
    private final ConfigManager config;
    private final Logger logger;

    public DeathRenameManager(VnMineHardcore plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.logger = plugin.getLogger();
    }

    /**
     * Cập nhật tên hiển thị (display name + player list name) cho người chơi
     * dựa vào config rename.name-structure và số lần chết hiện tại.
     */
    public void updateDisplayName(Player player, int deathCount) {
        if (!config.renameEnabled) {
            return;
        }

        String structure = config.nameStructure;
        if (structure == null || structure.isEmpty()) {
            return;
        }

        String parsedName = parseNameStructure(structure, player.getName(), deathCount);

        // Chỉ đổi tên hiển thị (chat + tab list), không ảnh hưởng đến tên thật
        player.displayName(Component.text(parsedName));
        player.playerListName(Component.text(parsedName));

        logger.fine("[Rename] Updated display name for " + player.getName()
            + " (UUID: " + player.getUniqueId() + ") -> \"" + parsedName + "\"");
    }

    /**
     * Reset tên hiển thị về tên gốc (khi rename bị tắt).
     */
    public void resetToOriginalName(Player player) {
        player.displayName(null); // null = reset về mặc định (tên gốc)
        player.playerListName(null);
    }

    /**
     * Parse name-structure bằng cách thay thế các placeholder:
     * - <name>  : tên gốc
     * - <count> : số lần chết (số thường)
     * - 000...  : số lần chết có padding 0 (phải đứng riêng, không kề cạnh số khác)
     *
     * Quy trình:
     * 1. Dùng marker tạm thời để bảo vệ <name> và <count> khỏi bị regex ảnh hưởng.
     * 2. Thay dãy 0 padding (dạng 00+, đứng riêng không kề chữ số) bằng số có padding.
     * 3. Khôi phục marker thành giá trị thật.
     */
    private String parseNameStructure(String structure, String playerName, int deathCount) {
        final String NAME_MARKER = "\u0000NAME\u0000";
        final String COUNT_MARKER = "\u0000COUNT\u0000";

        // 1. Thay placeholder bằng marker để tránh bị regex làm hỏng
        String result = structure
            .replace("<name>", NAME_MARKER)
            .replace("<count>", COUNT_MARKER);

        // 2. Tìm dãy số 0 (một hoặc nhiều) đứng riêng, không kề cạnh chữ số khác
        //    (?<![0-9]) = không đứng sau chữ số
        //    (?!\d)     = không đứng trước chữ số
        Pattern paddingPattern = Pattern.compile("(?<![0-9])(0+)(?!\\d)");
        Matcher matcher = paddingPattern.matcher(result);

        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            int minDigits = matcher.group(1).length();
            String paddedNumber = String.format("%0" + minDigits + "d", deathCount);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(paddedNumber));
        }
        matcher.appendTail(sb);

        // 3. Khôi phục marker thành giá trị thật
        result = sb.toString()
            .replace(NAME_MARKER, playerName)
            .replace(COUNT_MARKER, String.valueOf(deathCount));

        return result;
    }
}