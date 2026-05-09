package com.rique.ruinedcollections.listener;

import com.rique.ruinedcollections.RuinedCollectionsPlugin;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;

public final class TrackingFilter {
    private final RuinedCollectionsPlugin plugin;

    public TrackingFilter(RuinedCollectionsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean blocked(Player player) {
        if (plugin.getConfig().getBoolean("tracking.ignore-creative", true) && player.getGameMode() == GameMode.CREATIVE) {
            return true;
        }
        if (plugin.getConfig().getBoolean("tracking.ignore-spectator", true) && player.getGameMode() == GameMode.SPECTATOR) {
            return true;
        }
        return blockedWorld(player.getWorld());
    }

    public boolean blockedWorld(World world) {
        List<String> enabledWorlds = plugin.getConfig().getStringList("tracking.enabled-worlds");
        if (!enabledWorlds.isEmpty() && !enabledWorlds.contains(world.getName())) {
            return true;
        }
        return plugin.getConfig().getStringList("tracking.disabled-worlds").contains(world.getName());
    }
}
