package com.rique.ruinedcollections.leaderboard;

import com.rique.ruinedcollections.RuinedCollectionsPlugin;
import com.rique.ruinedcollections.collection.CollectionDefinition;
import com.rique.ruinedcollections.diagnostics.DiagnosticService;
import com.rique.ruinedcollections.storage.LeaderboardRow;
import com.rique.ruinedcollections.util.Longs;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitTask;

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
    private final ConcurrentMap<RankKey, Boolean> rankLoads = new ConcurrentHashMap<>();

    private BukkitTask refreshTask;
    private boolean enabled;
    private int limit;
    private long refreshTicks;
    private long rankCacheMillis;
    private String emptyName;
    private String emptyValue;
    private String loadingValue;

    public LeaderboardService(RuinedCollectionsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        load();
        if (!enabled) {
            return;
        }
        refreshAll();
        refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refreshAll, refreshTicks, refreshTicks);
    }

    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    public void reload() {
        stop();
        leaderboards.clear();
        rankCache.clear();
        rankLoads.clear();
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
        requestRank(key);
        if (cached == null) {
            return raw ? "0" : loadingValue;
        }
        return cached.rank() <= 0 ? (raw ? "0" : emptyValue) : String.valueOf(cached.rank());
    }

    public void refreshAll() {
        if (!enabled) {
            return;
        }
        List<String> collectionIds = plugin.collectionRegistry().enabledCollections().stream()
                .map(CollectionDefinition::id)
                .toList();
        for (String collectionId : collectionIds) {
            plugin.repository().loadLeaderboard(collectionId, limit).whenComplete((rows, throwable) -> {
                if (throwable != null) {
                    plugin.diagnostics().error("leaderboards", "Could not load leaderboard", DiagnosticService.fields(
                            "collection", collectionId,
                            "limit", limit
                    ), throwable);
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> store(collectionId, rows));
            });
        }
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
            entries.add(new LeaderboardEntry(row.playerId(), resolveName(row.playerId()), row.progress()));
        }
        leaderboards.put(normalize(collectionId), List.copyOf(entries));
        plugin.diagnostics().debug("progress", "Leaderboard refreshed", DiagnosticService.fields(
                "collection", collectionId,
                "entries", entries.size()
        ));
    }

    private void requestRank(RankKey key) {
        if (rankLoads.putIfAbsent(key, true) != null) {
            return;
        }
        plugin.repository().loadPlayerRank(key.playerId(), key.collectionId()).whenComplete((rank, throwable) -> {
            rankLoads.remove(key);
            if (throwable != null) {
                plugin.diagnostics().error("leaderboards", "Could not load player rank", DiagnosticService.fields(
                        "uuid", key.playerId(),
                        "collection", key.collectionId()
                ), throwable);
                return;
            }
            rankCache.put(key, new RankCacheEntry(rank == null ? 0L : rank, System.currentTimeMillis()));
        });
    }

    private String resolveName(UUID playerId) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        String name = player.getName();
        return name == null || name.isBlank() ? playerId.toString() : name;
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
