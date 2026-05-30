package com.rique.ruinedcollections.leaderboard;

import com.rique.ruinedcollections.RuinedCollectionsPlugin;
import com.rique.ruinedcollections.collection.CollectionDefinition;
import com.rique.ruinedcollections.diagnostics.DiagnosticService;
import com.rique.ruinedcollections.scheduler.SchedulerAdapter;
import com.rique.ruinedcollections.storage.LeaderboardRow;
import com.rique.ruinedcollections.util.Longs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class LeaderboardService {
    private final RuinedCollectionsPlugin plugin;
    private final ConcurrentMap<String, List<LeaderboardEntry>> leaderboards = new ConcurrentHashMap<>();
    private final ConcurrentMap<RankKey, RankCacheEntry> rankCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<RankKey, Long> rankLoads = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> leaderboardLoads = new ConcurrentHashMap<>();

    private SchedulerAdapter.TaskHandle refreshTask;
    private volatile boolean enabled;
    private volatile int limit;
    private volatile long refreshTicks;
    private volatile long rankCacheMillis;
    private volatile long generation;
    private volatile String emptyName;
    private volatile String emptyValue;
    private volatile String loadingValue;
    private volatile String unknownNameFormat;

    public LeaderboardService(RuinedCollectionsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        load();
        if (!enabled) {
            return;
        }
        refreshAll();
        refreshTask = plugin.scheduler().runTimerAsync(this::refreshAll, refreshTicks, refreshTicks);
    }

    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    public void reload() {
        stop();
        generation++;
        leaderboards.clear();
        rankCache.clear();
        rankLoads.clear();
        leaderboardLoads.clear();
        start();
    }

    public String topValue(String collectionId, int position, String field) {
        if (!enabled || position <= 0) {
            return empty(field);
        }
        List<LeaderboardEntry> entries = leaderboards.get(normalize(collectionId));
        if (entries == null || position > entries.size()) {
            return empty(field);
        }
        LeaderboardEntry entry = entries.get(position - 1);
        return switch (field.toLowerCase(Locale.ROOT)) {
            case "name" -> entry.name();
            case "uuid" -> entry.playerId().toString();
            case "raw", "raw_progress" -> String.valueOf(entry.progress());
            case "progress", "value" -> Longs.format(entry.progress());
            default -> "";
        };
    }

    public String rankValue(UUID playerId, String collectionId, boolean raw) {
        if (!enabled || playerId == null) {
            return raw ? "0" : emptyValue;
        }
        String normalized = normalize(collectionId);
        RankKey key = new RankKey(playerId, normalized);
        long now = System.currentTimeMillis();
        RankCacheEntry cached = rankCache.get(key);
        if (cached != null && now - cached.loadedAt() <= rankCacheMillis) {
            return cached.rank() <= 0 ? (raw ? "0" : emptyValue) : String.valueOf(cached.rank());
        }
        requestRank(key, generation);
        if (cached == null) {
            return raw ? "0" : loadingValue;
        }
        return cached.rank() <= 0 ? (raw ? "0" : emptyValue) : String.valueOf(cached.rank());
    }

    public void refreshAll() {
        if (!enabled) {
            return;
        }
        long currentGeneration = generation;
        List<String> collectionIds = plugin.collectionRegistry().enabledCollections().stream()
                .map(CollectionDefinition::id)
                .toList();
        for (String collectionId : collectionIds) {
            refreshCollection(collectionId, currentGeneration);
        }
    }

    public void refreshCollection(String collectionId) {
        refreshCollection(collectionId, generation);
    }

    private void refreshCollection(String collectionId, long refreshGeneration) {
        if (!enabled) {
            return;
        }
        String normalized = normalize(collectionId);
        if (leaderboardLoads.putIfAbsent(normalized, refreshGeneration) != null) {
            return;
        }
        plugin.repository().loadLeaderboard(normalized, limit).whenComplete((rows, throwable) -> {
            leaderboardLoads.remove(normalized, refreshGeneration);
            if (throwable != null) {
                plugin.diagnostics().error("leaderboards", "Could not load leaderboard", DiagnosticService.fields(
                        "collection", normalized,
                        "limit", limit
                ), throwable);
                return;
            }
            if (refreshGeneration != generation || !enabled) {
                return;
            }
            plugin.scheduler().runGlobal(() -> {
                if (refreshGeneration == generation && enabled) {
                    store(normalized, rows);
                }
            });
        });
    }

    public void invalidate(UUID playerId, String collectionId) {
        RankKey key = new RankKey(playerId, normalize(collectionId));
        rankCache.remove(key);
        rankLoads.remove(key);
    }

    private void load() {
        enabled = plugin.getConfig().getBoolean("leaderboards.enabled", true);
        limit = Math.max(1, Math.min(plugin.getConfig().getInt("leaderboards.entries-per-collection", 10), 100));
        long refreshSeconds = Math.max(15L, plugin.getConfig().getLong("leaderboards.refresh-interval-seconds", 60L));
        refreshTicks = refreshSeconds * 20L;
        long rankSeconds = Math.max(5L, plugin.getConfig().getLong("leaderboards.rank-cache-seconds", 30L));
        rankCacheMillis = rankSeconds * 1000L;
        emptyName = plugin.getConfig().getString("leaderboards.empty-name", "-");
        emptyValue = plugin.getConfig().getString("leaderboards.empty-value", "0");
        loadingValue = plugin.getConfig().getString("leaderboards.loading-value", "...");
        unknownNameFormat = plugin.getConfig().getString("leaderboards.unknown-name-format", "Unknown-%short_uuid%");
        if (unknownNameFormat == null || unknownNameFormat.isBlank()) {
            unknownNameFormat = "Unknown-%short_uuid%";
        }
        plugin.diagnostics().info("leaderboards", "Leaderboards loaded", DiagnosticService.fields(
                "enabled", enabled,
                "limit", limit,
                "refreshSeconds", refreshSeconds,
                "rankCacheSeconds", rankSeconds
        ));
    }

    private void store(String collectionId, List<LeaderboardRow> rows) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        for (LeaderboardRow row : rows) {
            entries.add(new LeaderboardEntry(row.playerId(), resolveName(row), row.progress()));
        }
        leaderboards.put(normalize(collectionId), List.copyOf(entries));
        plugin.diagnostics().debug("progress", "Leaderboard refreshed", DiagnosticService.fields(
                "collection", collectionId,
                "entries", entries.size()
        ));
    }

    private void requestRank(RankKey key, long requestGeneration) {
        if (rankLoads.putIfAbsent(key, requestGeneration) != null) {
            return;
        }
        plugin.repository().loadPlayerRank(key.playerId(), key.collectionId()).whenComplete((rank, throwable) -> {
            rankLoads.remove(key, requestGeneration);
            if (throwable != null) {
                plugin.diagnostics().error("leaderboards", "Could not load player rank", DiagnosticService.fields(
                        "uuid", key.playerId(),
                        "collection", key.collectionId()
                ), throwable);
                return;
            }
            if (requestGeneration != generation || !enabled) {
                return;
            }
            rankCache.put(key, new RankCacheEntry(rank == null ? 0L : rank, System.currentTimeMillis()));
        });
    }

    private String resolveName(LeaderboardRow row) {
        if (row.playerName() != null && !row.playerName().isBlank()) {
            return row.playerName();
        }
        String uuid = row.playerId().toString();
        return unknownNameFormat
                .replace("%uuid%", uuid)
                .replace("%short_uuid%", uuid.substring(0, 8));
    }

    private String empty(String field) {
        String normalized = field.toLowerCase(Locale.ROOT);
        if ("name".equals(normalized)) {
            return emptyName;
        }
        if ("uuid".equals(normalized)) {
            return "";
        }
        return emptyValue;
    }

    private String normalize(String collectionId) {
        return collectionId == null ? "" : collectionId.toLowerCase(Locale.ROOT).trim();
    }
}
