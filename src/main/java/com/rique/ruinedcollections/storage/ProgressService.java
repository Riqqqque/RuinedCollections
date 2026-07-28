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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ProgressService {
    private final RuinedCollectionsPlugin plugin;
    private final CollectionRepository repository;
    private final RewardService rewardService;
    private final Map<UUID, PlayerProgressSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> loading = new ConcurrentHashMap<>();
    private final Map<UUID, Long> loadVersions = new ConcurrentHashMap<>();
    private final AtomicLong loadSequence = new AtomicLong();
    private final Map<ProgressKey, AtomicLong> waitingForLoad = new ConcurrentHashMap<>();
    private final Map<ProgressKey, AtomicLong> pendingFlush = new ConcurrentHashMap<>();
    private final Set<UUID> snapshotLocked = ConcurrentHashMap.newKeySet();
    private final Set<UUID> savingOnUnload = ConcurrentHashMap.newKeySet();
    private SchedulerAdapter.TaskHandle flushTask;
    private volatile boolean loadOnJoin;
    private volatile boolean saveOnQuit;
    private volatile boolean capAtHighestTier;
    private volatile long flushIntervalTicks;

    public ProgressService(RuinedCollectionsPlugin plugin, CollectionRepository repository, RewardService rewardService) {
        this.plugin = plugin;
        this.repository = repository;
        this.rewardService = rewardService;
    }

    public void start() {
        loadSettings();
        scheduleFlush();
        if (loadOnJoin) {
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
            flushTask = null;
        }
        flushAllSync();
        sessions.clear();
        loading.clear();
        loadVersions.clear();
        snapshotLocked.clear();
        savingOnUnload.clear();
    }

    public void reload() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        loadSettings();
        scheduleFlush();
    }

    public void load(Player player) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        recordPlayerName(playerId, playerName);
        if (snapshotLocked.contains(playerId)) {
            plugin.diagnostics().debug("progress", "Deferred player progress load during snapshot import", DiagnosticService.fields(
                    "player", playerName,
                    "uuid", playerId
            ));
            return;
        }
        if (savingOnUnload.contains(playerId)) {
            plugin.diagnostics().debug("progress", "Deferred player progress load during quit save", DiagnosticService.fields(
                    "player", playerName,
                    "uuid", playerId
            ));
            return;
        }
        if (sessions.containsKey(playerId)) {
            return;
        }
        long version = nextLoadVersion();
        if (loading.putIfAbsent(playerId, version) != null) {
            return;
        }
        loadVersions.put(playerId, version);
        repository.loadPlayer(playerId).whenComplete((data, throwable) -> {
            loading.remove(playerId, version);
            if (throwable != null) {
                loadVersions.remove(playerId, version);
                plugin.diagnostics().error("progress", "Could not load player collection data", DiagnosticService.fields(
                        "player", playerName,
                        "uuid", playerId
                ), throwable);
                flushWaitingAsync(playerId, playerName);
                return;
            }
            plugin.scheduler().runPlayer(player, () -> {
                if (!player.isOnline() || snapshotLocked.contains(playerId) || savingOnUnload.contains(playerId) || loadVersion(playerId) != version) {
                    loadVersions.remove(playerId, version);
                    plugin.diagnostics().debug("progress", "Skipped applying stale loaded player data", DiagnosticService.fields(
                            "player", player.getName(),
                            "uuid", playerId
                    ));
                    return;
                }
                PlayerProgressSession session = new PlayerProgressSession(data);
                sessions.put(playerId, session);
                loadVersions.remove(playerId, version);
                applyWaiting(player, session);
                checkAllRewards(player, session);
            }, () -> loadVersions.remove(playerId, version));
        });
    }

    public void unload(Player player) {
        long invalidationVersion = bumpLoadVersion(player.getUniqueId());
        if (saveOnQuit) {
            flushOnUnloadAsync(player.getUniqueId(), player.getName());
        }
        sessions.remove(player.getUniqueId());
        loading.remove(player.getUniqueId());
        loadVersions.remove(player.getUniqueId(), invalidationVersion);
    }

    public void refresh(Player player) {
        bumpLoadVersion(player.getUniqueId());
        sessions.remove(player.getUniqueId());
        loading.remove(player.getUniqueId());
        load(player);
    }

    public void refreshOnlinePlayer(UUID playerId) {
        if (playerId != null) {
            refreshOnlinePlayers(Set.of(playerId));
        }
    }

    public void addProgress(Player player, CollectionDefinition collection, long amount) {
        addProgress(player, collection, amount, true);
    }

    private void addProgress(Player player, CollectionDefinition collection, long amount, boolean trackedSource) {
        if (amount <= 0 || (trackedSource && !collection.enabled())) {
            return;
        }
        if (trackedSource && !collection.hasAccess(player)) {
            plugin.diagnostics().debug("tracking", "Skipped collection progress because the player lacks the collection permission", DiagnosticService.fields(
                    "player", player.getName(),
                    "uuid", player.getUniqueId(),
                    "collection", collection.id(),
                    "permission", collection.permission()
            ));
            return;
        }
        UUID playerId = player.getUniqueId();
        if (snapshotLocked.contains(playerId)) {
            addQueued(waitingForLoad, new ProgressKey(playerId, collection.id()), amount);
            return;
        }
        PlayerProgressSession session = sessions.get(playerId);
        if (session == null) {
            addQueued(waitingForLoad, new ProgressKey(playerId, collection.id()), amount);
            load(player);
            return;
        }
        long current = session.progress(collection.id());
        long cappedAmount = cappedDelta(collection, current, amount);
        if (cappedAmount <= 0) {
            plugin.diagnostics().debug("progress", "Skipped progress because the collection is already capped", DiagnosticService.fields(
                    "player", player.getName(),
                    "uuid", playerId,
                    "collection", collection.id(),
                    "current", current,
                    "max", collection.maxGoal()
            ));
            return;
        }
        long progress = session.addProgress(collection.id(), cappedAmount);
        addQueued(pendingFlush, new ProgressKey(playerId, collection.id()), cappedAmount);
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
            String storedCollectionId = collection == null ? collectionId : collection.id();
            Map<ProgressKey, Long> batch = Map.of(new ProgressKey(playerId, storedCollectionId), amount);
            repository.addProgressBatch(batch, capsFor(batch))
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
        plugin.scheduler().runGlobal(() -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                plugin.scheduler().runPlayer(player, () -> setProgressNow(playerId, collectionId, amount));
            } else {
                setProgressNow(playerId, collectionId, amount);
            }
        });
    }

    private void setProgressNow(UUID playerId, String collectionId, long amount) {
        CollectionDefinition collection = plugin.collectionRegistry().get(collectionId).orElse(null);
        String storedCollectionId = collection == null ? collectionId : collection.id();
        long storedAmount = cappedAmount(collection, amount);
        ProgressKey key = new ProgressKey(playerId, storedCollectionId);
        AtomicLong removedPending = pendingFlush.remove(key);
        AtomicLong removedWaiting = waitingForLoad.remove(key);
        long removedPendingAmount = removedPending == null ? 0 : Math.max(0, removedPending.get());
        long removedWaitingAmount = removedWaiting == null ? 0 : Math.max(0, removedWaiting.get());
        PlayerProgressSession session = sessions.get(playerId);
        Long previous = session == null ? null : session.progress(storedCollectionId);
        if (session != null) {
            session.setProgress(storedCollectionId, storedAmount);
        }
        repository.setProgress(playerId, storedCollectionId, storedAmount).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                PlayerProgressSession liveSession = sessions.get(playerId);
                if (previous != null && liveSession != null && liveSession.progress(storedCollectionId) == storedAmount) {
                    liveSession.setProgress(storedCollectionId, previous);
                }
                addQueued(pendingFlush, key, removedPendingAmount);
                addQueued(waitingForLoad, key, removedWaitingAmount);
                plugin.diagnostics().error("progress", "Could not set collection progress", DiagnosticService.fields(
                        "uuid", playerId,
                        "collection", storedCollectionId,
                        "amount", storedAmount
                ), throwable);
                return;
            }
            plugin.scheduler().runGlobal(() -> {
                Player player = Bukkit.getPlayer(playerId);
                CollectionDefinition liveCollection = plugin.collectionRegistry().get(storedCollectionId).orElse(null);
                PlayerProgressSession liveSession = sessions.get(playerId);
                if (player != null && liveCollection != null && liveCollection.enabled() && liveSession != null) {
                    plugin.scheduler().runPlayer(player, () -> rewardService.check(
                            player,
                            liveCollection,
                            liveSession.progress(storedCollectionId),
                            liveSession
                    ));
                }
                plugin.leaderboards().invalidate(playerId, storedCollectionId);
                plugin.leaderboards().refreshCollection(storedCollectionId);
                plugin.leaderboards().refreshCategoriesForCollections(Set.of(storedCollectionId));
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

    public boolean shouldLoadOnJoin() {
        return loadOnJoin;
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
        repository.addProgressBatch(batch, capsFor(batch)).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                plugin.diagnostics().error("progress", "Could not save collection progress batch", DiagnosticService.fields("rows", batch.size()), throwable);
                restorePending(batch);
                return;
            }
            afterProgressSaved(batch);
        });
    }

    public void flushSync() {
        flushPendingSync("during synchronous progress flush");
    }

    public boolean flushAllSync() {
        boolean pendingSaved = flushPendingSync("during full progress flush");
        boolean waitingSaved = flushWaitingSync();
        return pendingSaved && waitingSaved;
    }

    public boolean prepareSnapshotApply(Set<UUID> playerIds) {
        if (!flushAllSync()) {
            return false;
        }
        snapshotLocked.addAll(playerIds);
        for (UUID playerId : playerIds) {
            long invalidationVersion = bumpLoadVersion(playerId);
            sessions.remove(playerId);
            loading.remove(playerId);
            loadVersions.remove(playerId, invalidationVersion);
        }
        return true;
    }

    public boolean finishSnapshotApply(Set<UUID> playerIds) {
        boolean saved = flushAllSync();
        snapshotLocked.removeAll(playerIds);
        refreshOnlinePlayers(playerIds);
        return saved;
    }

    private boolean flushPendingSync(String failureAction) {
        Map<ProgressKey, Long> batch = drainPending();
        if (batch.isEmpty()) {
            return true;
        }
        try {
            repository.addProgressBatchOrderedSync(batch, capsFor(batch));
            afterProgressSaved(batch);
            return true;
        } catch (SQLException exception) {
            plugin.diagnostics().error("progress", "Could not save collection progress batch " + failureAction, DiagnosticService.fields("rows", batch.size()), exception);
            restorePending(batch);
            return false;
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
            long cappedAmount = cappedDelta(collection, session.progress(collection.id()), amount);
            if (cappedAmount <= 0) {
                continue;
            }
            long progress = session.addProgress(collection.id(), cappedAmount);
            addQueued(pendingFlush, entry.getKey(), cappedAmount);
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

    private void flushWaitingAsync(UUID playerId, String playerName) {
        Map<ProgressKey, Long> batch = drainWaiting(playerId);
        if (batch.isEmpty()) {
            return;
        }
        repository.addProgressBatch(batch, capsFor(batch)).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                plugin.diagnostics().error("progress", "Could not save queued progress for unloaded player", DiagnosticService.fields(
                        "player", playerName,
                        "uuid", playerId,
                        "rows", batch.size()
                ), throwable);
                restoreWaiting(batch);
                return;
            }
            afterProgressSaved(batch);
            refreshOnlinePlayers(Set.of(playerId));
        });
    }

    private boolean flushWaitingSync() {
        Map<ProgressKey, Long> batch = drainWaiting(null);
        if (batch.isEmpty()) {
            return true;
        }
        try {
            repository.addProgressBatchOrderedSync(batch, capsFor(batch));
            afterProgressSaved(batch);
            refreshOnlinePlayers(playerIds(batch));
            return true;
        } catch (SQLException exception) {
            plugin.diagnostics().error("progress", "Could not save queued progress during shutdown", DiagnosticService.fields("rows", batch.size()), exception);
            restoreWaiting(batch);
            return false;
        }
    }

    private void flushOnUnloadAsync(UUID playerId, String playerName) {
        Map<ProgressKey, Long> batch = drainPending(playerId);
        Map<ProgressKey, Long> waiting = drainWaiting(playerId);
        for (Map.Entry<ProgressKey, Long> entry : waiting.entrySet()) {
            add(batch, entry.getKey(), entry.getValue());
        }
        if (batch.isEmpty()) {
            return;
        }
        savingOnUnload.add(playerId);
        repository.addProgressBatch(batch, capsFor(batch)).whenComplete((ignored, throwable) -> {
            savingOnUnload.remove(playerId);
            if (throwable != null) {
                plugin.diagnostics().error("progress", "Could not save progress for unloaded player", DiagnosticService.fields(
                        "player", playerName,
                        "uuid", playerId,
                        "rows", batch.size()
                ), throwable);
                restorePending(batch);
                refreshOnlinePlayers(Set.of(playerId));
                return;
            }
            afterProgressSaved(batch);
            refreshOnlinePlayers(Set.of(playerId));
        });
    }

    private void afterProgressSaved(Map<ProgressKey, Long> batch) {
        Set<String> collectionIds = new LinkedHashSet<>();
        for (ProgressKey key : batch.keySet()) {
            plugin.leaderboards().invalidate(key.playerId(), key.collectionId());
            collectionIds.add(key.collectionId());
        }
        for (String collectionId : collectionIds) {
            plugin.leaderboards().refreshCollection(collectionId);
        }
        plugin.leaderboards().refreshCategoriesForCollections(collectionIds);
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

    private long loadVersion(UUID playerId) {
        return loadVersions.getOrDefault(playerId, 0L);
    }

    private long bumpLoadVersion(UUID playerId) {
        long version = nextLoadVersion();
        loadVersions.put(playerId, version);
        return version;
    }

    private long nextLoadVersion() {
        return loadSequence.updateAndGet(current -> current == Long.MAX_VALUE ? 1L : current + 1L);
    }

    private Set<UUID> playerIds(Map<ProgressKey, Long> batch) {
        Set<UUID> playerIds = new LinkedHashSet<>();
        for (ProgressKey key : batch.keySet()) {
            playerIds.add(key.playerId());
        }
        return playerIds;
    }

    private long cappedDelta(CollectionDefinition collection, long current, long requested) {
        long amount = Math.max(0, requested);
        if (amount == 0 || !capAtHighestTier()) {
            return amount;
        }
        long maxGoal = collection == null ? 0 : collection.maxGoal();
        if (maxGoal <= 0 || current < 0) {
            return amount;
        }
        if (current >= maxGoal) {
            return 0;
        }
        return Math.min(amount, maxGoal - current);
    }

    private long cappedAmount(CollectionDefinition collection, long requested) {
        long amount = Math.max(0, requested);
        if (!capAtHighestTier()) {
            return amount;
        }
        long maxGoal = collection == null ? 0 : collection.maxGoal();
        return maxGoal > 0 ? Math.min(amount, maxGoal) : amount;
    }

    private Map<String, Long> capsFor(Map<ProgressKey, Long> batch) {
        if (!capAtHighestTier() || batch.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> caps = new HashMap<>();
        for (ProgressKey key : batch.keySet()) {
            if (caps.containsKey(key.collectionId())) {
                continue;
            }
            plugin.collectionRegistry().get(key.collectionId())
                    .map(CollectionDefinition::maxGoal)
                    .filter(maxGoal -> maxGoal > 0)
                    .ifPresent(maxGoal -> caps.put(key.collectionId(), maxGoal));
        }
        return caps;
    }

    private boolean capAtHighestTier() {
        return capAtHighestTier;
    }

    private void loadSettings() {
        loadOnJoin = plugin.getConfig().getBoolean("progress.load-on-join", true);
        saveOnQuit = plugin.getConfig().getBoolean("progress.save-on-quit", true);
        capAtHighestTier = plugin.getConfig().getBoolean("progress.cap-at-highest-tier", true);
        long flushSeconds = plugin.getConfig().getLong("progress.flush-interval-seconds", 15L);
        flushSeconds = Math.max(1L, Math.min(flushSeconds, Long.MAX_VALUE / 20L));
        flushIntervalTicks = Math.max(20L, flushSeconds * 20L);
    }

    private void scheduleFlush() {
        flushTask = plugin.scheduler().runTimerAsync(this::flushAsync, flushIntervalTicks, flushIntervalTicks);
    }

    private void refreshOnlinePlayers(Set<UUID> playerIds) {
        if (playerIds.isEmpty()) {
            return;
        }
        plugin.scheduler().runGlobal(() -> {
            for (UUID playerId : playerIds) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    plugin.scheduler().runPlayer(player, () -> refresh(player));
                }
            }
        });
    }
}
