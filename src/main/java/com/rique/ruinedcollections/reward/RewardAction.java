package com.rique.ruinedcollections.reward;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.Map;

public final class RewardAction {
    private final RewardType type;
    private final String text;
    private final String command;
    private final String sender;
    private final double amount;

    public RewardAction(RewardType type, String text, String command, String sender, double amount) {
        this.type = type;
        this.text = text;
        this.command = command;
        this.sender = sender;
        this.amount = amount;
    }

    public RewardType type() {
        return type;
    }

    public String text() {
        return text;
    }

    public String command() {
        return command;
    }

    public String sender() {
        return sender;
    }

    public double amount() {
        return amount;
    }

    public static RewardAction from(ConfigurationSection section) {
        RewardType type = RewardType.valueOf(section.getString("type", "MESSAGE").toUpperCase());
        return new RewardAction(
                type,
                section.getString("text", ""),
                section.getString("command", ""),
                section.getString("sender", "CONSOLE"),
                section.getDouble("amount", 0.0)
        );
    }

    public static RewardAction fromMap(Map<?, ?> map) {
        RewardType type = RewardType.valueOf(string(map, "type", "MESSAGE").toUpperCase(Locale.ROOT));
        return new RewardAction(
                type,
                string(map, "text", ""),
                string(map, "command", ""),
                string(map, "sender", "CONSOLE"),
                asDouble(map.get("amount"))
        );
    }

    private static String string(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : value.toString();
    }

    private static double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }
}
