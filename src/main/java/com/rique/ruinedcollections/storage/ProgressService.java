package com.rique.ruinedcollections.storage;

import com.rique.ruinedcollections.RuinedCollectionsPlugin;
import com.rique.ruinedcollections.collection.CollectionDefinition;
import com.rique.ruinedcollections.reward.RewardService;
import com.rique.ruinedcollections.util.Longs;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

public final class ProgressService {
    private final RuinedCollectionsPlugin plugin;
    private final CollectionRepository repository;
    private final RewardService rewardService;
    private final Map<UUID, PlayerProgressSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> loading = new ConcurrentHashMap<>();
    private final Map<ProgressKey, AtomicLong> waitingForLoad = new ConcurrentHashMap<>();
    private final Map<ProgressKey, AtomicLong> pendingFlush = new ConcurrentHashMap<>();
    private BukkitTask flushTask;

    public ProgressService(RuinedCollectionsPlugin plugin, CollectionRepository repository, RewardService rewardService) {
        this.plugin = plugin;
        this.repository = repository;
        this.rewardService = rewardService;
    }

    public void start() {
        long interval = Math.max(20L, plugin.getConfig().getLong("progress.flush-interval-seconds", 15) * 20L);
        flushTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flushAsync, interval, interval);
        if (plugin.getConfig().getBoolean("progress.load-on-join", true)) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                load(player);
            }
        }
    }

    public void stop() {
        if (flushTask != null) {
            flushTask.cancel();
        }
        flushSync();
        sessions.clear();
        waitingForLoad.clear();
    }

    public void load(Player player) {
        UUID playerId = player.getUniqueId();
        if (sessions.containsKey(playerId) || loading.putIfAbsent(playerId, true) != null) {
            return;
        }
        repository.loadPlayer(playerId).whenComplete((data, throwable) -> {
            loading.remove(playerId);
            if (throwable != null) {
                plugin.getLogger().log(Level.SEVERE, "Could not load collection data for " + player.getName(), throwable);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
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
            flushAsync();
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
        if (amount <= 0 || !collection.enabled()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        PlayerProgressSession session = sessions.get(playerId);
        if (session == null) {
            waitingForLoad.computeIfAbsent(new ProgressKey(playerId, collection.id()), ignored -> new AtomicLong()).addAndGet(amount);
            load(player);
            return;
        }
        long progress = session.addProgress(collection.id(), amount);
        pendingFlush.computeIfAbsent(new ProgressKey(playerId, collection.id()), ignored -> new AtomicLong()).addAndGet(amount);
        rewardService.check(player, collection, progress, session);
    }

    public void addManual(UUID playerId, String collectionId, long amount) {
        Player player = Bukkit.getPlayer(playerId);
        CollectionDefinition collection = plugin.collectionRegistry().get(collectionId).orElse(null);
        if (player != null && collection != null) {
            addProgress(player, collection, amount);
            return;
        }
        repository.addProgressBatch(Map.of(new ProgressKey(playerId, collectionId), amount))
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.SEVERE, "Could not add manual progress.", throwable);
                    return null;
                });
    }

    public void setProgress(UUID playerId, String collectionId, long amount) {
        ProgressKey key = new ProgressKey(playerId, collectionId);
        pendingFlush.remove(key);
        waitingForLoad.remove(key);
        PlayerProgressSession session = sessions.get(playerId);
        if (session != null) {
            session.setProgress(collectionId, amount);
        }
        repository.setProgress(playerId, collectionId, amount).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                plugin.getLogger().log(Level.SEVERE, "Could not set collection progress.", throwable);
                return;
            }
            Player player = Bukkit.getPlayer(playerId);
            CollectionDefinition collection = plugin.collectionRegistry().get(collectionId).orElse(null);
            PlayerProgressSession liveSession = sessions.get(playerId);
            if (player != null && collection != null && liveSession != null) {
                Bukkit.getScheduler().runTask(plugin, () -> rewardService.check(player, collection, amount, liveSession));
            }
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
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerProgressSession session = sessions.get(player.getUniqueId());
            if (session != null) {
                checkAllRewards(player, session);
            }
        }
    }

    public void flushAsync() {
        Map<ProgressKey, Long> batch = drainPending();
        if (batch.isEmpty()) {
            return;
        }
        repository.addProgressBatch(batch).exceptionally(throwable -> {
            plugin.getLogger().log(Level.SEVERE, "Could not save collection progress.", throwable);
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
            plugin.getLogger().log(Level.SEVERE, "Could not save collection progress.", exception);
            restorePending(batch);
        }
    }

    private void applyWaiting(Player player, PlayerProgressSession session) {
        UUID playerId = player.getUniqueId();
        for (Map.Entry<ProgressKey, AtomicLong> entry : waitingForLoad.entrySet()) {
            if (!entry.getKey().playerId().equals(playerId)) {
                continue;
            }
            long amount = Math.max(0, entry.getValue().getAndSet(0));
            waitingForLoad.remove(entry.getKey());
            if (amount <= 0) {
                continue;
            }
            CollectionDefinition collection = plugin.collectionRegistry().get(entry.getKey().collectionId()).orElse(null);
            if (collection == null) {
                continue;
            }
            long progress = session.addProgress(collection.id(), amount);
            pendingFlush.computeIfAbsent(entry.getKey(), ignored -> new AtomicLong()).addAndGet(amount);
            rewardService.check(player, collection, progress, session);
        }
    }

    private Map<ProgressKey, Long> drainPending() {
        Map<ProgressKey, Long> batch = new HashMap<>();
        for (Map.Entry<ProgressKey, AtomicLong> entry : pendingFlush.entrySet()) {
            long amount = entry.getValue().getAndSet(0);
            if (amount > 0) {
                batch.put(entry.getKey(), amount);
            }
        }
        return batch;
    }

    private void restorePending(Map<ProgressKey, Long> batch) {
        for (Map.Entry<ProgressKey, Long> entry : batch.entrySet()) {
            pendingFlush.computeIfAbsent(entry.getKey(), ignored -> new AtomicLong())
                    .addAndGet(Longs.addClamped(0, entry.getValue()));
        }
    }
}
