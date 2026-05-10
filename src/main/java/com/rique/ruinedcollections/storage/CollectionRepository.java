package com.rique.ruinedcollections.storage;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class CollectionRepository {
    private final DataSource dataSource;
    private final StorageType storageType;
    private final String prefix;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "RuinedCollections-Storage");
        thread.setDaemon(true);
        return thread;
    });

    public CollectionRepository(DataSource dataSource, StorageType storageType, String prefix) {
        this.dataSource = dataSource;
        this.storageType = storageType;
        this.prefix = prefix;
    }

    public CompletableFuture<PlayerData> loadPlayer(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return loadPlayerSync(playerId);
            } catch (SQLException exception) {
                throw new StorageException(exception);
            }
        }, executor);
    }

    public PlayerData loadPlayerSync(UUID playerId) throws SQLException {
        Map<String, Long> progress = new HashMap<>();
        Set<ClaimKey> claimed = new HashSet<>();
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT collection_id, progress FROM " + prefix + "player_progress WHERE player_uuid=?")) {
                statement.setString(1, playerId.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        progress.put(resultSet.getString("collection_id"), Math.max(0, resultSet.getLong("progress")));
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT collection_id, tier_id FROM " + prefix + "claimed_tiers WHERE player_uuid=?")) {
                statement.setString(1, playerId.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        claimed.add(new ClaimKey(resultSet.getString("collection_id"), resultSet.getString("tier_id")));
                    }
                }
            }
        }
        return new PlayerData(progress, claimed);
    }

    public CompletableFuture<Void> addProgressBatch(Map<ProgressKey, Long> progress) {
        return CompletableFuture.runAsync(() -> {
            try {
                addProgressBatchSync(progress);
            } catch (SQLException exception) {
                throw new StorageException(exception);
            }
        }, executor);
    }

    public void addProgressBatchSync(Map<ProgressKey, Long> progress) throws SQLException {
        if (progress.isEmpty()) {
            return;
        }
        String sql = storageType == StorageType.SQLITE
                ? "INSERT INTO " + prefix + "player_progress (player_uuid, collection_id, progress, updated_at) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT(player_uuid, collection_id) DO UPDATE SET progress = CASE WHEN progress > ? THEN ? ELSE progress + ? END, updated_at = ?"
                : "INSERT INTO " + prefix + "player_progress (player_uuid, collection_id, progress, updated_at) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE progress = CASE WHEN progress > ? THEN ? ELSE progress + ? END, updated_at = ?";
        long now = System.currentTimeMillis();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);
            for (Map.Entry<ProgressKey, Long> entry : progress.entrySet()) {
                long amount = Math.max(0, entry.getValue());
                if (amount == 0) {
                    continue;
                }
                statement.setString(1, entry.getKey().playerId().toString());
                statement.setString(2, entry.getKey().collectionId());
                statement.setLong(3, amount);
                statement.setLong(4, now);
                statement.setLong(5, Long.MAX_VALUE - amount);
                statement.setLong(6, Long.MAX_VALUE);
                statement.setLong(7, amount);
                statement.setLong(8, now);
                statement.addBatch();
            }
            statement.executeBatch();
            connection.commit();
        }
    }

    public CompletableFuture<Void> setProgress(UUID playerId, String collectionId, long amount) {
        return CompletableFuture.runAsync(() -> {
            try {
                setProgressSync(playerId, collectionId, amount);
            } catch (SQLException exception) {
                throw new StorageException(exception);
            }
        }, executor);
    }

    public void setProgressSync(UUID playerId, String collectionId, long amount) throws SQLException {
        String sql = storageType == StorageType.SQLITE
                ? "INSERT INTO " + prefix + "player_progress (player_uuid, collection_id, progress, updated_at) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT(player_uuid, collection_id) DO UPDATE SET progress = excluded.progress, updated_at = excluded.updated_at"
                : "INSERT INTO " + prefix + "player_progress (player_uuid, collection_id, progress, updated_at) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE progress = VALUES(progress), updated_at = VALUES(updated_at)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, collectionId);
            statement.setLong(3, Math.max(0, amount));
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    public CompletableFuture<List<LeaderboardRow>> loadLeaderboard(String collectionId, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return loadLeaderboardSync(collectionId, limit);
            } catch (SQLException exception) {
                throw new StorageException(exception);
            }
        }, executor);
    }

    public List<LeaderboardRow> loadLeaderboardSync(String collectionId, int limit) throws SQLException {
        List<LeaderboardRow> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT player_uuid, progress FROM " + prefix + "player_progress "
                             + "WHERE collection_id=? AND progress > 0 ORDER BY progress DESC, player_uuid ASC LIMIT ?")) {
            statement.setString(1, collectionId);
            statement.setInt(2, Math.max(1, limit));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new LeaderboardRow(
                            UUID.fromString(resultSet.getString("player_uuid")),
                            Math.max(0, resultSet.getLong("progress"))
                    ));
                }
            }
        }
        return rows;
    }

    public CompletableFuture<Long> loadPlayerRank(UUID playerId, String collectionId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return loadPlayerRankSync(playerId, collectionId);
            } catch (SQLException exception) {
                throw new StorageException(exception);
            }
        }, executor);
    }

    public long loadPlayerRankSync(UUID playerId, String collectionId) throws SQLException {
        long progress;
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT progress FROM " + prefix + "player_progress WHERE player_uuid=? AND collection_id=?")) {
                statement.setString(1, playerId.toString());
                statement.setString(2, collectionId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return 0;
                    }
                    progress = Math.max(0, resultSet.getLong("progress"));
                    if (progress <= 0) {
                        return 0;
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM " + prefix + "player_progress WHERE collection_id=? AND progress > ?")) {
                statement.setString(1, collectionId);
                statement.setLong(2, progress);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? resultSet.getLong(1) + 1 : 1;
                }
            }
        }
    }

    public CompletableFuture<Boolean> claimTier(UUID playerId, String collectionId, String tierId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return claimTierSync(playerId, collectionId, tierId);
            } catch (SQLException exception) {
                throw new StorageException(exception);
            }
        }, executor);
    }

    public boolean claimTierSync(UUID playerId, String collectionId, String tierId) throws SQLException {
        String sql = storageType == StorageType.SQLITE
                ? "INSERT OR IGNORE INTO " + prefix + "claimed_tiers (player_uuid, collection_id, tier_id, claimed_at) VALUES (?, ?, ?, ?)"
                : "INSERT IGNORE INTO " + prefix + "claimed_tiers (player_uuid, collection_id, tier_id, claimed_at) VALUES (?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, collectionId);
            statement.setString(3, tierId);
            statement.setLong(4, System.currentTimeMillis());
            return statement.executeUpdate() > 0;
        }
    }

    public CompletableFuture<Void> applySnapshot(List<ProgressRow> progressRows, List<ClaimedRow> claimedRows) {
        return CompletableFuture.runAsync(() -> {
            try {
                applySnapshotSync(progressRows, claimedRows);
            } catch (SQLException exception) {
                throw new StorageException(exception);
            }
        }, executor);
    }

    public void applySnapshotSync(List<ProgressRow> progressRows, List<ClaimedRow> claimedRows) throws SQLException {
        if (progressRows.isEmpty() && claimedRows.isEmpty()) {
            return;
        }
        String progressSql = storageType == StorageType.SQLITE
                ? "INSERT INTO " + prefix + "player_progress (player_uuid, collection_id, progress, updated_at) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT(player_uuid, collection_id) DO UPDATE SET progress = excluded.progress, updated_at = excluded.updated_at"
                : "INSERT INTO " + prefix + "player_progress (player_uuid, collection_id, progress, updated_at) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE progress = VALUES(progress), updated_at = VALUES(updated_at)";
        String claimSql = storageType == StorageType.SQLITE
                ? "INSERT OR IGNORE INTO " + prefix + "claimed_tiers (player_uuid, collection_id, tier_id, claimed_at) VALUES (?, ?, ?, ?)"
                : "INSERT IGNORE INTO " + prefix + "claimed_tiers (player_uuid, collection_id, tier_id, claimed_at) VALUES (?, ?, ?, ?)";
        long now = System.currentTimeMillis();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement progressStatement = connection.prepareStatement(progressSql);
             PreparedStatement claimStatement = connection.prepareStatement(claimSql)) {
            connection.setAutoCommit(false);
            try {
                for (ProgressRow row : progressRows) {
                    progressStatement.setString(1, row.playerId().toString());
                    progressStatement.setString(2, row.collectionId());
                    progressStatement.setLong(3, Math.max(0, row.progress()));
                    progressStatement.setLong(4, now);
                    progressStatement.addBatch();
                }
                progressStatement.executeBatch();

                for (ClaimedRow row : claimedRows) {
                    claimStatement.setString(1, row.playerId().toString());
                    claimStatement.setString(2, row.collectionId());
                    claimStatement.setString(3, row.tierId());
                    claimStatement.setLong(4, now);
                    claimStatement.addBatch();
                }
                claimStatement.executeBatch();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public CompletableFuture<Void> unclaimTier(UUID playerId, String collectionId, String tierId) {
        return CompletableFuture.runAsync(() -> {
            try {
                unclaimTierSync(playerId, collectionId, tierId);
            } catch (SQLException exception) {
                throw new StorageException(exception);
            }
        }, executor);
    }

    public void unclaimTierSync(UUID playerId, String collectionId, String tierId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM " + prefix + "claimed_tiers WHERE player_uuid=? AND collection_id=? AND tier_id=?")) {
            statement.setString(1, playerId.toString());
            statement.setString(2, collectionId);
            statement.setString(3, tierId);
            statement.executeUpdate();
        }
    }

    public CompletableFuture<List<ProgressRow>> loadAllProgress() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return loadAllProgressSync();
            } catch (SQLException exception) {
                throw new StorageException(exception);
            }
        }, executor);
    }

    public List<ProgressRow> loadAllProgressSync() throws SQLException {
        List<ProgressRow> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT player_uuid, collection_id, progress FROM " + prefix + "player_progress");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rows.add(new ProgressRow(
                        UUID.fromString(resultSet.getString("player_uuid")),
                        resultSet.getString("collection_id"),
                        Math.max(0, resultSet.getLong("progress"))
                ));
            }
        }
        return rows;
    }

    public CompletableFuture<List<ClaimedRow>> loadAllClaimed() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return loadAllClaimedSync();
            } catch (SQLException exception) {
                throw new StorageException(exception);
            }
        }, executor);
    }

    public List<ClaimedRow> loadAllClaimedSync() throws SQLException {
        List<ClaimedRow> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT player_uuid, collection_id, tier_id FROM " + prefix + "claimed_tiers");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rows.add(new ClaimedRow(
                        UUID.fromString(resultSet.getString("player_uuid")),
                        resultSet.getString("collection_id"),
                        resultSet.getString("tier_id")
                ));
            }
        }
        return rows;
    }

    public CompletableFuture<Set<BlockLocationKey>> loadPlacedBlocks() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return loadPlacedBlocksSync();
            } catch (SQLException exception) {
                throw new StorageException(exception);
            }
        }, executor);
    }

    public Set<BlockLocationKey> loadPlacedBlocksSync() throws SQLException {
        Set<BlockLocationKey> blocks = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT world, x, y, z FROM " + prefix + "placed_blocks");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                blocks.add(new BlockLocationKey(
                        resultSet.getString("world"),
                        resultSet.getInt("x"),
                        resultSet.getInt("y"),
                        resultSet.getInt("z")
                ));
            }
        }
        return blocks;
    }

    public CompletableFuture<Void> addPlacedBlock(BlockLocationKey key, String material) {
        return CompletableFuture.runAsync(() -> {
            String sql = storageType == StorageType.SQLITE
                    ? "INSERT OR REPLACE INTO " + prefix + "placed_blocks (world, x, y, z, material, created_at) VALUES (?, ?, ?, ?, ?, ?)"
                    : "REPLACE INTO " + prefix + "placed_blocks (world, x, y, z, material, created_at) VALUES (?, ?, ?, ?, ?, ?)";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, key.world());
                statement.setInt(2, key.x());
                statement.setInt(3, key.y());
                statement.setInt(4, key.z());
                statement.setString(5, material);
                statement.setLong(6, System.currentTimeMillis());
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new StorageException(exception);
            }
        }, executor);
    }

    public CompletableFuture<Void> removePlacedBlock(BlockLocationKey key) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "DELETE FROM " + prefix + "placed_blocks WHERE world=? AND x=? AND y=? AND z=?")) {
                statement.setString(1, key.world());
                statement.setInt(2, key.x());
                statement.setInt(3, key.y());
                statement.setInt(4, key.z());
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new StorageException(exception);
            }
        }, executor);
    }

    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    public static final class StorageException extends RuntimeException {
        public StorageException(Throwable cause) {
            super(cause);
        }
    }
}
