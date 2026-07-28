package com.rique.ruinedcollections.collection;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public record PersistentDataRule(NamespacedKey key, String type, String value) {
    public boolean matches(PersistentDataContainer container) {
        return switch (type.toUpperCase()) {
            case "INTEGER", "INT" -> {
                Integer stored = container.get(key, PersistentDataType.INTEGER);
                Integer expected = parseInteger(value);
                yield stored != null && expected != null && stored.equals(expected);
            }
            case "LONG" -> {
                Long stored = container.get(key, PersistentDataType.LONG);
                Long expected = parseLong(value);
                yield stored != null && expected != null && stored.equals(expected);
            }
            case "DOUBLE" -> {
                Double stored = container.get(key, PersistentDataType.DOUBLE);
                Double expected = parseDouble(value);
                yield stored != null && expected != null && Double.compare(stored, expected) == 0;
            }
            default -> {
                String stored = container.get(key, PersistentDataType.STRING);
                yield stored != null && stored.equals(value);
            }
        };
    }

    private Integer parseInteger(String input) {
        try {
            return Integer.valueOf(input);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Long parseLong(String input) {
        try {
            return Long.valueOf(input);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Double parseDouble(String input) {
        try {
            double parsed = Double.parseDouble(input);
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
