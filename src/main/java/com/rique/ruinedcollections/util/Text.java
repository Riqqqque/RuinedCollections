package com.rique.ruinedcollections.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Map;

public final class Text {
    private static final char SECTION = '\u00A7';
    private static final String COLOR_CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private Text() {
    }

    public static String color(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '&' && index + 1 < text.length() && COLOR_CODES.indexOf(text.charAt(index + 1)) >= 0) {
                builder.append(SECTION);
                continue;
            }
            builder.append(current);
        }
        return builder.toString();
    }

    public static Component component(String text) {
        return LEGACY.deserialize(color(text));
    }

    public static String legacy(Component component) {
        if (component == null) {
            return "";
        }
        return LEGACY.serialize(component);
    }

    public static String strip(String text) {
        return PLAIN.serialize(component(text));
    }

    public static String placeholders(String text, Map<String, String> placeholders) {
        String result = text == null ? "" : text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace('%' + entry.getKey() + '%', entry.getValue());
        }
        return result;
    }
}
