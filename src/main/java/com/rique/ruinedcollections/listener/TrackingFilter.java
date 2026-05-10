package com.rique.ruinedcollections.listener;

import com.rique.ruinedcollections.RuinedCollectionsPlugin;
import com.rique.ruinedcollections.diagnostics.DiagnosticService;
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
            debugPlayer(player, "creative_mode");
            return true;
        }
        if (plugin.getConfig().getBoolean("tracking.ignore-spectator", true) && player.getGameMode() == GameMode.SPECTATOR) {
            debugPlayer(player, "spectator_mode");
            return true;
        }
        boolean blocked = blockedWorld(player.getWorld());
        if (blocked) {
            debugPlayer(player, "world_blocked");
        }
        return blocked;
    }

    public boolean blockedWorld(World world) {
        List<String> enabledWorlds = plugin.getConfig().getStringList("tracking.enabled-worlds");
        if (!enabledWorlds.isEmpty() && !enabledWorlds.contains(world.getName())) {
            debugWorld(world, "not_in_enabled_worlds");
            return true;
        }
        boolean blocked = plugin.getConfig().getStringList("tracking.disabled-worlds").contains(world.getName());
        if (blocked) {
            debugWorld(world, "disabled_world");
        }
        return blocked;
    }

    private void debugPlayer(Player player, String reason) {
        plugin.diagnostics().debug("tracking", "Skipped tracking for player", DiagnosticService.fields(
                "player", player.getName(),
                "uuid", player.getUniqueId(),
                "world", player.getWorld().getName(),
                "reason", reason
        ));
    }

    private void debugWorld(World world, String reason) {
        plugin.diagnostics().debug("tracking", "Skipped tracking for world", DiagnosticService.fields(
                "world", world.getName(),
                "reason", reason
        ));
    }
}
