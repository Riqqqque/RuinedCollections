package com.rique.ruinedcollections.storage;

import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class PlacedBlockService {
    private final JavaPlugin plugin;
    private final CollectionRepository repository;
    private final Set<BlockLocationKey> placedBlocks = ConcurrentHashMap.newKeySet();

    public PlacedBlockService(JavaPlugin plugin, CollectionRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public void start() {
        try {
            Set<BlockLocationKey> blocks = repository.loadPlacedBlocksSync();
            placedBlocks.addAll(blocks);
            plugin.getLogger().info("Loaded " + blocks.size() + " placed block records.");
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not load placed block cache.", exception);
        }
    }

    public void mark(Block block) {
        BlockLocationKey key = BlockLocationKey.from(block.getLocation());
        placedBlocks.add(key);
        repository.addPlacedBlock(key, block.getType().name()).exceptionally(throwable -> {
            plugin.getLogger().log(Level.SEVERE, "Could not save placed block record.", throwable);
            return null;
        });
    }

    public boolean consume(Block block) {
        BlockLocationKey key = BlockLocationKey.from(block.getLocation());
        if (!placedBlocks.remove(key)) {
            return false;
        }
        repository.removePlacedBlock(key).exceptionally(throwable -> {
            plugin.getLogger().log(Level.SEVERE, "Could not remove placed block record.", throwable);
            return null;
        });
        return true;
    }
}
