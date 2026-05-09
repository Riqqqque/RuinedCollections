package com.rique.ruinedcollections.storage;

import org.bukkit.Location;

public record BlockLocationKey(String world, int x, int y, int z) {
    public static BlockLocationKey from(Location location) {
        String worldName = location.getWorld() == null ? "" : location.getWorld().getName();
        return new BlockLocationKey(worldName, location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
