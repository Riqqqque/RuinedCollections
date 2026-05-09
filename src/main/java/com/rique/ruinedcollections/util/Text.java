package com.rique.ruinedcollections.util;

import org.bukkit.ChatColor;

import java.util.Map;

public final class Text {
    private Text() {
    }

    public static String color(String text) {
        if (text == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static String strip(String text) {
        return ChatColor.stripColor(color(text));
    }

    public static String placeholders(String text, Map<String, String> placeholders) {
        String result = text == null ? "" : text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace('%' + entry.getKey() + '%', entry.getValue());
        }
        return result;
    }
}
