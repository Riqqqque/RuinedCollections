package com.rique.ruinedcollections.storage;

import com.rique.ruinedcollections.RuinedCollectionsPlugin;
import com.rique.ruinedcollections.collection.CollectionDefinition;
import com.rique.ruinedcollections.diagnostics.DiagnosticService;
import com.rique.ruinedcollections.reward.RewardService;
import com.rique.ruinedcollections.scheduler.SchedulerAdapter;
import com.rique.ruinedcollections.util.Longs;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ProgressService {
    private final RuinedCollectionsPlugin plugin;
    private final CollectionRepository repository;
    private final RewardService rewardService;
    private final Map<UUID, PlayerProgressSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> loading = new ConcurrentHashMap<>();
    private final Map<ProgressKey, AtomicLong> waitingForLoad = new ConcurrentHashMap<>();
    private final Map<ProgressKey, AtomicLong> pendingFlush = new ConcurrentHashMap<>();
    private SchedulerAdapter.TaskHandle flushTask;

    public ProgressService(RuinedCollectionsPlugin plugin, CollectionRepository repository, RewardService rewardService) {
        this.plugin = plugin;
        this.repository = repository;
        this.rewardService = rewardService;
    }

    public void start() {
        long interval = Math.max(20L, plugin.getConfig().getLong("progress.flush-interval-seconds", 15) * 20L);
        flushTask = plugin.scheduler().runTimerAsync(this::flushAsync, interval, interval);
        if (plugin.getConfig().getBoolean("progress.load-on-join", true)) {
            plugin.scheduler().runGlobal(() -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    plugin.scheduler().runPlayer(player, () -> load(player));
                }
            });
        }
    }

    public void stop() {
        if (flushTask != null) {
            flushTask.cancel();
        }
        flushSync();
        flushWaitingSync();
        sessions.clear();
        waitingForLoad.clear();
    }

    public void load(Player player) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        recordPlayerName(playerId, playerName);
        if (sessions.containsKey(playerId) || loading.putIfAbsent(playerId, true) != null) {
            return;
        }
        repository.loadPlayer(playerId).whenComplete((data, throwable) -> {
            loading.remove(playerId);
            if (throwable != null) {
                plugin.diagnostics().error("progress", "Could not load player collection data", DiagnosticService.fields(
                        "player", playerName,
                        "uuid", playerId
                ), throwable);
                flushWaitingAsync(playerId, playerName);
                return;
            }
            plugin.scheduler().runPlayer(player, () -> {
                if (!player.isOnline()) {
                    plugin.diagnostics().debug("progress", "Skipped applying loaded data because player left", DiagnosticService.fields(
                            "player", player.getName(),
                            "uuid", playerId
                    ));
                    return;
                }
                PlayerProgressSession session = new PlayerProgressSession(data);
                sessions.put(playerId, session);
                applyWaiting(player, session);
                checkAllRewards(player, session);
            });
        });
    }

    public void unload(Player player) {
        if (plugin.getConfig().getBoolean("progress.save-on-quit", true)) {
            flushPendingAsync(player.getUniqueId(), player.getName());
            flushWaitingAsync(player.getUniqueId(), player.getName());
        }
        sessions.remove(player.getUniqueId());
        loading.remove(player.getUniqueId());
    }

    public void refresh(Player player) {
        sessions.remove(player.getUniqueId());
        loading.remove(player.getUniqueId());
        load(player);
    }

    public void addProgress(Player player, CollectionDefinition collection, long amount) {
        addProgress(player, collection, amount, true);
    }

    private void addProgress(Player player, CollectionDefinition collection, long amount, boolean trackedSource) {
        if (amount <= 0 || (trackedSource && !collection.enabled())) {
            return;
        }
        UUID playerId = player.getUniqueId();
        PlayerProgressSession session = sessions.get(playerId);
        if (session == null) {
            addQueued(waitingForLoad, new ProgressKey(playerId, collection.id()), amount);
            load(player);
            return;
        }
        long progress = session.addProgress(collection.id(), amount);
        addQueued(pendingFlush, new ProgressKey(playerId, collection.id()), amount);
        if (collection.enabled()) {
            rewardService.check(player, collection, progress, session);
        }
    }

    public void addManual(UUID playerId, String collectionId, long amount) {
        plugin.scheduler().runGlobal(() -> {
            Player player = Bukkit.getPlayer(playerId);
            CollectionDefinition collection = plugin.collectionRegistry().get(collectionId).orElse(null);
            if (player != null && collection != null) {
                plugin.scheduler().runPlayer(player, () -> addProgress(player, collection, amount, false));
                return;
            }
            repository.addProgressBatch(Map.of(new ProgressKey(playerId, collectionId), amount))
                    .whenComplete((ignored, throwable) -> {
                        if (throwable != null) {
                            plugin.diagnostics().error("progress", "Could not add manual progress", DiagnosticService.fields(
                                    "uuid", playerId,
                                    "collection", collectionId,
                                    "amount", amount
                            ), throwable);
                            return;
                        }
                        plugin.leaderboards().invalidate(playerId, collectionId);
                        plugin.leaderboards().refreshCollection(collectionId);
                    });
        });
    }

    public void setProgress(UUID playerId, String collectionId, long amount) {
        ProgressKey key = new ProgressKey(playerId, collectionId);
        AtomicLong removedPending = pendingFlush.remove(key);
        AtomicLong removedWaiting = waitingForLoad.remove(key);
        long removedPendingAmount = removedPending == null ? 0 : Math.max(0, removedPending.get());
        long removedWaitingAmount = removedWaiting == null ? 0 : Math.max(0, removedWaiting.get());
        PlayerProgressSession session = sessions.get(playerId);
        Long previous = session == null ? null : session.progress(collectionId);
        if (session != null) {
            session.setProgress(collectionId, amount);
        }
        repository.setProgress(playerId, collectionId, amount).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                PlayerProgressSession liveSession = sessions.get(playerId);
                if (previous != null && liveSession != null && liveSession.progress(collectionId) == amount) {
                    liveSession.setProgress(collectionId, previous);
                }
                addQueued(pendingFlush, key, removedPendingAmount);
                addQueued(waitingForLoad, key, removedWaitingAmount);
                plugin.diagnostics().error("progress", "Could not set collection progress", DiagnosticService.fields(
                        "uuid", playerId,
                        "collection", collectionId,
                        "amount", amount
                ), throwable);
                return;
            }
            plugin.scheduler().runGlobal(() -> {
                Player player = Bukkit.getPlayer(playerId);
                CollectionDefinition collection = plugin.collectionRegistry().get(collectionId).orElse(null);
                PlayerProgressSession liveSession = sessions.get(playerId);
                if (player != null && collection != null && collection.enabled() && liveSession != null) {
                    plugin.scheduler().runPlayer(player, () -> rewardService.check(player, collection, amount, liveSession));
                }
                plugin.leaderboards().invalidate(playerId, collectionId);
                plugin.leaderboards().refreshCollection(collectionId);
            });
        });
    }

    public long cachedProgress(UUID playerId, String collectionId) {
        PlayerProgressSession session = sessions.get(playerId);
        if (session == null) {
            return 0;
        }
        return session.progress(collectionId);
    }

    public void checkAllRewards(Player player, PlayerProgressSession session) {
        for (CollectionDefinition collection : plugin.collectionRegistry().enabledCollections()) {
            long progress = session.progress(collection.id());
            if (progress > 0) {
                rewardService.check(player, collection, progress, session);
            }
        }
    }

    public void recheckOnlineRewards() {
        plugin.scheduler().runGlobal(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                plugin.scheduler().runPlayer(player, () -> {
                    PlayerProgressSession session = sessions.get(player.getUniqueId());
                    if (session != null) {
                        checkAllRewards(player, session);
                    }
                });
            }
        });
    }

    public void flushAsync() {
        Map<ProgressKey, Long> batch = drainPending();
        if (batch.isEmpty()) {
            return;
        }
        repository.addProgressBatch(batch).exceptionally(throwable -> {
            plugin.diagnostics().error("progress", "Could not save collection progress batch", DiagnosticService.fields("rows", batch.size()), throwable);
            restorePending(batch);
            return null;
        });
    }

    public void flushSync() {
        Map<ProgressKey, Long> batch = drainPending();
        if (batch.isEmpty()) {
            return;
        }
        try {
            repository.addProgressBatchSync(batch);
        } catch (SQLException exception) {
            plugin.diagnostics().error("progress", "Could not save collection progress batch during shutdown", DiagnosticService.fields("rows", batch.size()), exception);
            restorePending(batch);
        }
    }

    private void applyWaiting(Player player, PlayerProgressSession session) {
        UUID playerId = player.getUniqueId();
        for (Map.Entry<ProgressKey, AtomicLong> entry : waitingForLoad.entrySet()) {
            if (!entry.getKey().playerId().equals(playerId)) {
                continue;
            }
            AtomicLong counter = waitingForLoad.remove(entry.getKey());
            if (counter == null) {
                continue;
            }
            long amount = Math.max(0, counter.getAndSet(0));
            if (amount <= 0) {
                continue;
            }
            CollectionDefinition collection = plugin.collectionRegistry().get(entry.getKey().collectionId()).orElse(null);
            if (collection == null) {
                continue;
            }
            long progress = session.addProgress(collection.id(), amount);
            addQueued(pendingFlush, entry.getKey(), amount);
            if (collection.enabled()) {
                rewardService.check(player, collection, progress, session);
            }
        }
    }

    private Map<ProgressKey, Long> drainPending() {
        return drainPending(null);
    }

    private Map<ProgressKey, Long> drainPending(UUID playerId) {
        Map<ProgressKey, Long> batch = new HashMap<>();
        for (Map.Entry<ProgressKey, AtomicLong> entry : pendingFlush.entrySet()) {
            if (playerId != null && !entry.getKey().playerId().equals(playerId)) {
                continue;
            }
            AtomicLong counter = pendingFlush.remove(entry.getKey());
            if (counter == null) {
                continue;
            }
            long amount = counter.getAndSet(0);
            if (amount > 0) {
                add(batch, entry.getKey(), amount);
            }
        }
        return batch;
    }

    private void restorePending(Map<ProgressKey, Long> batch) {
        for (Map.Entry<ProgressKey, Long> entry : batch.entrySet()) {
            addQueued(pendingFlush, entry.getKey(), entry.getValue());
        }
    }

    private void flushPendingAsync(UUID playerId, String playerName) {
        Map<ProgressKey, Long> batch = drainPending(playerId);
        if (batch.isEmpty()) {
            return;
        }
        repository.addProgressBatch(batch).exceptionally(throwable -> {
            plugin.diagnostics().error("progress", "Could not save pending progress for unloaded player", DiagnosticService.fields(
                    "player", playerName,
                    "uuid", playerId,
                    "rows", batch.size()
            ), throwable);
            restorePending(batch);
            return null;
        });
    }

    private void flushWaitingAsync(UUID playerId, String playerName) {
        Map<ProgressKey, Long> batch = drainWaiting(playerId);
        if (batch.isEmpty()) {
            return;
        }
        repository.addProgressBatch(batch).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                plugin.diagnostics().error("progress", "Could not save queued progress for unloaded player", DiagnosticService.fields(
                        "player", playerName,
                        "uuid", playerId,
                        "rows", batch.size()
                ), throwable);
                restoreWaiting(batch);
                return;
            }
            for (ProgressKey key : batch.keySet()) {
                plugin.leaderboards().invalidate(key.playerId(), key.collectionId());
                plugin.leaderboards().refreshCollection(key.collectionId());
            }
            plugin.scheduler().runGlobal(() -> {
                Player online = Bukkit.getPlayer(playerId);
                if (online != null && online.isOnline()) {
                    plugin.scheduler().runPlayer(online, () -> refresh(online));
                }
            });
        });
    }

    private void flushWaitingSync() {
        Map<ProgressKey, Long> batch = drainWaiting(null);
        if (batch.isEmpty()) {
            return;
        }
        try {
            repository.addProgressBatchSync(batch);
        } catch (SQLException exception) {
            plugin.diagnostics().error("progress", "Could not save queued progress during shutdown", DiagnosticService.fields("rows", batch.size()), exception);
            restoreWaiting(batch);
        }
    }

    private Map<ProgressKey, Long> drainWaiting(UUID playerId) {
        Map<ProgressKey, Long> batch = new HashMap<>();
        for (Map.Entry<ProgressKey, AtomicLong> entry : waitingForLoad.entrySet()) {
            if (playerId != null && !entry.getKey().playerId().equals(playerId)) {
                continue;
            }
            AtomicLong counter = waitingForLoad.remove(entry.getKey());
            if (counter == null) {
                continue;
            }
            long amount = counter.getAndSet(0);
            if (amount > 0) {
                add(batch, entry.getKey(), amount);
            }
        }
        return batch;
    }

    private void restoreWaiting(Map<ProgressKey, Long> batch) {
        for (Map.Entry<ProgressKey, Long> entry : batch.entrySet()) {
            addQueued(waitingForLoad, entry.getKey(), entry.getValue());
        }
    }

    public void recordPlayerName(UUID playerId, String playerName) {
        repository.savePlayerName(playerId, playerName).exceptionally(throwable -> {
            plugin.diagnostics().error("progress", "Could not save player name", DiagnosticService.fields(
                    "uuid", playerId,
                    "player", playerName
            ), throwable);
            return null;
        });
    }

    private void addQueued(Map<ProgressKey, AtomicLong> queue, ProgressKey key, long amount) {
        if (amount <= 0) {
            return;
        }
        queue.computeIfAbsent(key, ignored -> new AtomicLong())
                .updateAndGet(current -> Longs.addClamped(current, amount));
    }

    private void add(Map<ProgressKey, Long> batch, ProgressKey key, long amount) {
        batch.merge(key, amount, Longs::addClamped);
    }
}
