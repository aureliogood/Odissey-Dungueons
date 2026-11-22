package com.odisseymc.odisseydungueons.util;

import net.md_5.bungee.api.ChatColor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for color coding, hex formatting and time parsing.
 */
public final class TextUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private TextUtil() {}

    /**
     * Translates standard color codes (&a) and hex codes (&#123456) into formatted strings.
     */
    public static String colorize(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder buffer = new StringBuilder(message.length() + 32);

        while (matcher.find()) {
            String hexCode = matcher.group(1);
            matcher.appendReplacement(buffer, ChatColor.of("#" + hexCode).toString());
        }
        matcher.appendTail(buffer);

        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    /**
     * Formats seconds into human-readable format: "01h 25m 30s", "05m 12s", etc.
     */
    public static String formatDuration(long totalSeconds) {
        if (totalSeconds <= 0) {
            return "00s";
        }

        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%02dh %02dm %02ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%02dm %02ds", minutes, seconds);
        } else {
            return String.format("%02ds", seconds);
        }
    }

    /**
     * Formats milliseconds into human-readable duration.
     */
    public static String formatDurationMillis(long millis) {
        return formatDuration(Math.max(0, millis / 1000));
    }
}
