package com.rique.ruinedcollections.collection;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public record PersistentDataRule(NamespacedKey key, String type, String value) {
    public boolean matches(PersistentDataContainer container) {
        return switch (type.toUpperCase()) {
            case "INTEGER", "INT" -> {
                Integer stored = container.get(key, PersistentDataType.INTEGER);
                yield stored != null && stored.toString().equals(value);
            }
            case "LONG" -> {
                Long stored = container.get(key, PersistentDataType.LONG);
                yield stored != null && stored.toString().equals(value);
            }
            case "DOUBLE" -> {
                Double stored = container.get(key, PersistentDataType.DOUBLE);
                yield stored != null && stored.toString().equals(value);
            }
            default -> {
                String stored = container.get(key, PersistentDataType.STRING);
                yield stored != null && stored.equals(value);
            }
        };
    }
}
