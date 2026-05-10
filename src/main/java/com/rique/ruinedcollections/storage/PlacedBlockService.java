package com.rique.ruinedcollections.storage;

import com.rique.ruinedcollections.RuinedCollectionsPlugin;
import com.rique.ruinedcollections.diagnostics.DiagnosticService;
import org.bukkit.block.Block;

import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class PlacedBlockService {
    private final RuinedCollectionsPlugin plugin;
    private final CollectionRepository repository;
    private final Set<BlockLocationKey> placedBlocks = ConcurrentHashMap.newKeySet();

    public PlacedBlockService(RuinedCollectionsPlugin plugin, CollectionRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public void start() {
        try {
            Set<BlockLocationKey> blocks = repository.loadPlacedBlocksSync();
            placedBlocks.addAll(blocks);
            plugin.diagnostics().info("tracking", "Loaded placed block records", DiagnosticService.fields("records", blocks.size()));
        } catch (SQLException exception) {
            plugin.diagnostics().error("tracking", "Could not load placed block cache", exception);
        }
    }

    public void mark(Block block) {
        BlockLocationKey key = BlockLocationKey.from(block.getLocation());
        placedBlocks.add(key);
        repository.addPlacedBlock(key, block.getType().name()).exceptionally(throwable -> {
            plugin.diagnostics().error("tracking", "Could not save placed block record", DiagnosticService.fields(
                    "world", key.world(),
                    "x", key.x(),
                    "y", key.y(),
                    "z", key.z(),
                    "material", block.getType().name()
            ), throwable);
            return null;
        });
    }

    public boolean consume(Block block) {
        BlockLocationKey key = BlockLocationKey.from(block.getLocation());
        if (!placedBlocks.remove(key)) {
            return false;
        }
        repository.removePlacedBlock(key).exceptionally(throwable -> {
            plugin.diagnostics().error("tracking", "Could not remove placed block record", DiagnosticService.fields(
                    "world", key.world(),
                    "x", key.x(),
                    "y", key.y(),
                    "z", key.z()
            ), throwable);
            return null;
        });
        return true;
    }
}
