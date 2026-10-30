package com.vnmine.hardcore.managers;

import java.util.Locale;
import org.bukkit.ChatColor;

/** Shared legacy text formatting for admin-authored RPG labels and dialogue. */
public final class LegacyText {
    private static final char[] RAINBOW = {'c', '6', 'e', 'a', 'b', '9', 'd'};

    private LegacyText() { }

    public static String translate(String input) {
        if (input == null || input.isEmpty()) return input == null ? "" : input;
        StringBuilder output = new StringBuilder(input.length() + 16);
        int cursor = 0;
        boolean foundRainbow = false;
        while (cursor < input.length()) {
            RainbowToken token = rainbowAt(input, cursor);
            if (token == null) {
                cursor++;
                continue;
            }
            foundRainbow = true;
            output.append(ChatColor.translateAlternateColorCodes('&', input.substring(cursor, token.start())));
            int segmentEnd = token.end();
            while (segmentEnd < input.length() && rainbowAt(input, segmentEnd) == null) segmentEnd++;
            appendRainbow(output, input.substring(token.end(), segmentEnd), token.mode());
            cursor = segmentEnd;
        }
        if (!foundRainbow) return ChatColor.translateAlternateColorCodes('&', input);
        return output.append(ChatColor.translateAlternateColorCodes('&', input.substring(cursor))).toString();
    }

    private static void appendRainbow(StringBuilder output, String value, char mode) {
        int visibleLength = visibleCodePoints(value);
        int visibleIndex = 0;
        long frame = System.currentTimeMillis() / 200L;
        for (int offset = 0; offset < value.length();) {
            char current = value.charAt(offset);
            if (current == '&' && offset + 1 < value.length()
                    && "0123456789abcdefklmnor".indexOf(Character.toLowerCase(value.charAt(offset + 1))) >= 0) {
                output.append(ChatColor.COLOR_CHAR).append(Character.toLowerCase(value.charAt(offset + 1)));
                offset += 2;
                continue;
            }
            int codePoint = value.codePointAt(offset);
            int delay = switch (mode) {
                case 'l' -> 0;
                case '2' -> Math.max(0, visibleLength - visibleIndex - 1);
                case '3' -> Math.min(Math.abs(visibleIndex - (visibleLength - 1) / 2),
                    Math.abs(visibleIndex - visibleLength / 2));
                default -> visibleIndex;
            };
            int paletteIndex = mode == 'l'
                ? Math.floorMod((int) (frame / 2L), RAINBOW.length)
                : frame < delay ? 0 : Math.floorMod((int) ((frame - delay) / 2L), RAINBOW.length);
            output.append(ChatColor.COLOR_CHAR).append(RAINBOW[paletteIndex]).appendCodePoint(codePoint);
            visibleIndex++;
            offset += Character.charCount(codePoint);
        }
    }

    private static int visibleCodePoints(String value) {
        int count = 0;
        for (int offset = 0; offset < value.length();) {
            if (value.charAt(offset) == '&' && offset + 1 < value.length()
                    && "0123456789abcdefklmnor".indexOf(Character.toLowerCase(value.charAt(offset + 1))) >= 0) {
                offset += 2;
                continue;
            }
            int cp = value.codePointAt(offset);
            count++;
            offset += Character.charCount(cp);
        }
        return count;
    }

    private static RainbowToken rainbowAt(String text, int offset) {
        if (offset + 3 >= text.length() || text.charAt(offset) != '&'
                || Character.toLowerCase(text.charAt(offset + 1)) != 'r') return null;
        char mode = Character.toLowerCase(text.charAt(offset + 2));
        if (mode == 'l') {
            return offset + 3 < text.length() && text.charAt(offset + 3) == ':'
                ? new RainbowToken(offset, offset + 4, mode) : null;
        }
        return mode == '1' || mode == '2' || mode == '3'
            ? new RainbowToken(offset, offset + 3, mode) : null;
    }

    private record RainbowToken(int start, int end, char mode) { }

    public static String strip(String input) {
        return ChatColor.stripColor(translate(input));
    }

    public static String normalizeCode(char code) {
        return String.valueOf(Character.toLowerCase(code)).toLowerCase(Locale.ROOT);
    }
}
