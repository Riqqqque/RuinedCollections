package com.rique.ruinedcollections.listener;

import com.rique.ruinedcollections.RuinedCollectionsPlugin;
import com.rique.ruinedcollections.collection.ProgressMatch;
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
        if (!plugin.getConfig().getBoolean("tracking.ignore-player-placed-blocks", true)) {
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
        boolean playerPlaced = plugin.getConfig().getBoolean("tracking.ignore-player-placed-blocks", true)
                && plugin.placedBlocks().consume(event.getBlock());
        if (filter.blocked(event.getPlayer())) {
            return;
        }
        if (playerPlaced) {
            return;
        }
        for (ProgressMatch match : plugin.collectionRegistry().matchBlockBreak(event.getBlock().getType())) {
            plugin.progressService().addProgress(event.getPlayer(), match.collection(), match.amount());
        }
    }
}
