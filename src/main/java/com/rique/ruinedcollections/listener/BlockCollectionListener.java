package com.rique.ruinedcollections.listener;

import com.rique.ruinedcollections.RuinedCollectionsPlugin;
import com.rique.ruinedcollections.collection.ProgressMatch;
import com.rique.ruinedcollections.diagnostics.DiagnosticService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public final class BlockCollectionListener implements Listener {
    private final RuinedCollectionsPlugin plugin;
    private final TrackingFilter filter;

    public BlockCollectionListener(RuinedCollectionsPlugin plugin, TrackingFilter filter) {
        this.plugin = plugin;
        this.filter = filter;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!filter.ignorePlayerPlacedBlocks()) {
            return;
        }
        if (filter.blockedWorld(event.getBlockPlaced().getWorld())) {
            return;
        }
        if (plugin.collectionRegistry().isTrackedBlockMaterial(event.getBlockPlaced().getType())) {
            plugin.placedBlocks().mark(event.getBlockPlaced());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        boolean playerPlaced = filter.ignorePlayerPlacedBlocks()
                && plugin.placedBlocks().consume(event.getBlock());
        if (filter.blocked(event.getPlayer())) {
            return;
        }
        if (playerPlaced) {
            plugin.diagnostics().debug("tracking", "Skipped player-placed block break", DiagnosticService.fields(
                    "player", event.getPlayer().getName(),
                    "uuid", event.getPlayer().getUniqueId(),
                    "world", event.getBlock().getWorld().getName(),
                    "x", event.getBlock().getX(),
                    "y", event.getBlock().getY(),
                    "z", event.getBlock().getZ(),
                    "material", event.getBlock().getType().name()
            ));
            return;
        }
        for (ProgressMatch match : plugin.collectionRegistry().matchBlockBreak(event.getBlock().getType())) {
            plugin.progressService().addProgress(event.getPlayer(), match.collection(), match.amount());
        }
    }
}
