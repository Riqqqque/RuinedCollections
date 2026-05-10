package com.rique.ruinedcollections.listener;

import com.rique.ruinedcollections.RuinedCollectionsPlugin;
import com.rique.ruinedcollections.diagnostics.DiagnosticService;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

public final class TrackingFilter {
    private final RuinedCollectionsPlugin plugin;
    private volatile boolean ignoreCreative;
    private volatile boolean ignoreSpectator;
    private volatile boolean ignorePlayerPlacedBlocks;
    private volatile Set<String> enabledWorlds = Set.of();
    private volatile Set<String> disabledWorlds = Set.of();

    public TrackingFilter(RuinedCollectionsPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        ignoreCreative = plugin.getConfig().getBoolean("tracking.ignore-creative", true);
        ignoreSpectator = plugin.getConfig().getBoolean("tracking.ignore-spectator", true);
        ignorePlayerPlacedBlocks = plugin.getConfig().getBoolean("tracking.ignore-player-placed-blocks", true);
        enabledWorlds = Set.copyOf(new HashSet<>(plugin.getConfig().getStringList("tracking.enabled-worlds")));
        disabledWorlds = Set.copyOf(new HashSet<>(plugin.getConfig().getStringList("tracking.disabled-worlds")));
    }

    public boolean ignorePlayerPlacedBlocks() {
        return ignorePlayerPlacedBlocks;
    }

    public boolean blocked(Player player) {
        if (ignoreCreative && player.getGameMode() == GameMode.CREATIVE) {
            debugPlayer(player, "creative_mode");
            return true;
        }
        if (ignoreSpectator && player.getGameMode() == GameMode.SPECTATOR) {
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
        Set<String> enabled = enabledWorlds;
        if (!enabled.isEmpty() && !enabled.contains(world.getName())) {
            debugWorld(world, "not_in_enabled_worlds");
            return true;
        }
        boolean blocked = disabledWorlds.contains(world.getName());
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
