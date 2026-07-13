package com.rique.ruinedcollections.storage;

import com.rique.ruinedcollections.RuinedCollectionsPlugin;
import com.rique.ruinedcollections.diagnostics.DiagnosticService;
import org.bukkit.block.Block;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PlacedBlockService {
    private final RuinedCollectionsPlugin plugin;
    private final CollectionRepository repository;
    private final Set<BlockLocationKey> placedBlocks = ConcurrentHashMap.newKeySet();
    private final Map<BlockLocationKey, PendingOperation> pendingOperations = new HashMap<>();
    private final AtomicBoolean flushInProgress = new AtomicBoolean();
    private volatile boolean stopping;
    private com.rique.ruinedcollections.scheduler.SchedulerAdapter.TaskHandle flushTask;

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
        } finally {
            scheduleFlush();
        }
    }

    public void reload() {
        if (flushTask != null) {
            flushTask.cancel();
        }
        scheduleFlush();
    }

    public void stop() {
        stopping = true;
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        try {
            repository.awaitWrites();
            Batch batch = drain();
            repository.applyPlacedBlockBatchOrderedSync(batch.upserts(), batch.removals());
        } catch (SQLException exception) {
            plugin.diagnostics().error("tracking", "Could not flush placed block records during shutdown", exception);
        }
    }

    public void mark(Block block) {
        BlockLocationKey key = BlockLocationKey.from(block.getLocation());
        String material = block.getType().name();
        placedBlocks.add(key);
        synchronized (pendingOperations) {
            pendingOperations.put(key, PendingOperation.upsert(material));
        }
    }

    public boolean consume(Block block) {
        BlockLocationKey key = BlockLocationKey.from(block.getLocation());
        if (!placedBlocks.remove(key)) {
            return false;
        }
        synchronized (pendingOperations) {
            pendingOperations.put(key, PendingOperation.deletion());
        }
        return true;
    }

    private void scheduleFlush() {
        stopping = false;
        long seconds = Math.max(1L, Math.min(
                plugin.getConfig().getLong("tracking.placed-block-flush-interval-seconds", 1L),
                Long.MAX_VALUE / 20L
        ));
        long ticks = seconds * 20L;
        flushTask = plugin.scheduler().runTimerAsync(this::flushAsync, ticks, ticks);
    }

    private void flushAsync() {
        if (stopping || !flushInProgress.compareAndSet(false, true)) {
            return;
        }
        Batch batch = drain();
        if (batch.empty()) {
            flushInProgress.set(false);
            return;
        }
        repository.applyPlacedBlockBatch(batch.upserts(), batch.removals()).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                restore(batch);
                plugin.diagnostics().error("tracking", "Could not save placed block batch", DiagnosticService.fields(
                        "upserts", batch.upserts().size(),
                        "removals", batch.removals().size()
                ), throwable);
            }
            flushInProgress.set(false);
            if (!stopping && hasPending()) {
                flushAsync();
            }
        });
    }

    private Batch drain() {
        Map<BlockLocationKey, String> upserts = new HashMap<>();
        Set<BlockLocationKey> removals = new HashSet<>();
        synchronized (pendingOperations) {
            pendingOperations.forEach((key, operation) -> {
                if (operation.remove()) {
                    removals.add(key);
                } else {
                    upserts.put(key, operation.material());
                }
            });
            pendingOperations.clear();
        }
        return new Batch(Map.copyOf(upserts), Set.copyOf(removals));
    }

    private void restore(Batch batch) {
        synchronized (pendingOperations) {
            batch.upserts().forEach((key, material) -> pendingOperations.putIfAbsent(key, PendingOperation.upsert(material)));
            batch.removals().forEach(key -> pendingOperations.putIfAbsent(key, PendingOperation.deletion()));
        }
    }

    private boolean hasPending() {
        synchronized (pendingOperations) {
            return !pendingOperations.isEmpty();
        }
    }

    private record PendingOperation(boolean remove, String material) {
        private static PendingOperation upsert(String material) {
            return new PendingOperation(false, material);
        }

        private static PendingOperation deletion() {
            return new PendingOperation(true, "");
        }
    }

    private record Batch(Map<BlockLocationKey, String> upserts, Set<BlockLocationKey> removals) {
        private boolean empty() {
            return upserts.isEmpty() && removals.isEmpty();
        }
    }
}
