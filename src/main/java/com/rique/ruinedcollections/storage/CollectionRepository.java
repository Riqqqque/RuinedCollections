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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class CollectionRepository {
    private final DataSource dataSource;
    private final StorageType storageType;
    private final String prefix;
    private final ExecutorService readExecutor = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "RuinedCollections-Storage-Read");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService writeExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "RuinedCollections-Storage-Write");
        thread.setDaemon(true);
        return thread;
    });

    public CollectionRepository(DataSource dataSource, StorageType storageType, String prefix) {
        this.dataSource = dataSource;
        this.storageType = storageType;
        this.prefix = prefix;
    }

    public CompletableFuture<PlayerData> loadPlayer(UUID playerId) {
        return supplyAfterWrites(() -> loadPlayerSync(playerId));
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
        return addProgressBatch(progress, Map.of());
    }

    public CompletableFuture<Void> addProgressBatch(Map<ProgressKey, Long> progress, Map<String, Long> maxProgressByCollection) {
        return CompletableFuture.runAsync(() -> {
            try {
                addProgressBatchSync(progress, maxProgressByCollection);
            } catch (SQLException exception) {
                throw new StorageException(exception);
            }
        }, writeExecutor);
    }

    public void addProgressBatchOrderedSync(Map<ProgressKey, Long> progress, Map<String, Long> maxProgressByCollection) throws SQLException {
        awaitWrite(addProgressBatch(progress, maxProgressByCollection));
    }

    public void addProgressBatchSync(Map<ProgressKey, Long> progress) throws SQLException {
        addProgressBatchSync(progress, Map.of());
    }

    public void addProgressBatchSync(Map<ProgressKey, Long> progress, Map<String, Long> maxProgressByCollection) throws SQLException {
        if (progress.isEmpty()) {
            return;
        }
        if (hasCaps(maxProgressByCollection)) {
            addProgressBatchCappedSync(progress, maxProgressByCollection);
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
            try {
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
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private void addProgressBatchCappedSync(Map<ProgressKey, Long> progress, Map<String, Long> maxProgressByCollection) throws SQLException {
        String boundedProgress = "CASE WHEN progress > ? THEN ? ELSE progress + ? END";
        String cappedProgress = storageType == StorageType.SQLITE
                ? "CASE WHEN ? > 0 THEN MIN(?, " + boundedProgress + ") ELSE " + boundedProgress + " END"
                : "CASE WHEN ? > 0 THEN LEAST(?, " + boundedProgress + ") ELSE " + boundedProgress + " END";
        String sql = storageType == StorageType.SQLITE
                ? "INSERT INTO " + prefix + "player_progress (player_uuid, collection_id, progress, updated_at) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT(player_uuid, collection_id) DO UPDATE SET progress = " + cappedProgress + ", updated_at = ?"
                : "INSERT INTO " + prefix + "player_progress (player_uuid, collection_id, progress, updated_at) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE progress = " + cappedProgress + ", updated_at = ?";
        long now = System.currentTimeMillis();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);
            try {
                for (Map.Entry<ProgressKey, Long> entry : progress.entrySet()) {
                    long amount = Math.max(0, entry.getValue());
                    if (amount == 0) {
                        continue;
                    }
                    ProgressKey key = entry.getKey();
                    long maxProgress = Math.max(0, maxProgressByCollection.getOrDefault(key.collectionId(), 0L));
                    long insertedProgress = maxProgress > 0 ? Math.min(amount, maxProgress) : amount;
                    statement.setString(1, key.playerId().toString());
                    statement.setString(2, key.collectionId());
                    statement.setLong(3, insertedProgress);
                    statement.setLong(4, now);
                    statement.setLong(5, maxProgress);
                    statement.setLong(6, maxProgress);
                    statement.setLong(7, Long.MAX_VALUE - amount);
                    statement.setLong(8, Long.MAX_VALUE);
                    statement.setLong(9, amount);
                    statement.setLong(10, Long.MAX_VALUE - amount);
                    statement.setLong(11, Long.MAX_VALUE);
                    statement.setLong(12, amount);
                    statement.setLong(13, now);
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public CompletableFuture<Void> setProgress(UUID playerId, String collectionId, long amount) {
        return CompletableFuture.runAsync(() -> {
            try {
                setProgressSync(playerId, collectionId, amount);
            } catch (SQLException exception) {
                throw new StorageException(exception);
            }
        }, writeExecutor);
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

    public CompletableFuture<Void> savePlayerName(UUID playerId, String playerName) {
        return CompletableFuture.runAsync(() -> {
            try {
                savePlayerNameSync(playerId, playerName);
            } catch (SQLException exception) {
                throw new StorageException(exception);
            }
        }, writeExecutor);
    }

    public void savePlayerNameSync(UUID playerId, String playerName) throws SQLException {
        if (playerId == null || playerName == null || playerName.isBlank()) {
            return;
        }
        String cleanName = playerName.length() > 64 ? playerName.substring(0, 64) : playerName;
        String sql = storageType == StorageType.SQLITE
                ? "INSERT INTO " + prefix + "player_names (player_uuid, player_name, updated_at) VALUES (?, ?, ?) "
                + "ON CONFLICT(player_uuid) DO UPDATE SET player_name = excluded.player_name, updated_at = excluded.updated_at"
                : "INSERT INTO " + prefix + "player_names (player_uuid, player_name, updated_at) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), updated_at = VALUES(updated_at)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, cleanName);
            statement.setLong(3, System.currentTimeMillis());
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
        }, readExecutor);
    }

    public List<LeaderboardRow> loadLeaderboardSync(String collectionId, int limit) throws SQLException {
        List<LeaderboardRow> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT p.player_uuid, n.player_name, p.progress "
                             + "FROM " + prefix + "player_progress p "
                             + "LEFT JOIN " + prefix + "player_names n ON p.player_uuid = n.player_uuid "
                             + "WHERE p.collection_id=? AND p.progress > 0 "
                             + "ORDER BY p.progress DESC, p.player_uuid ASC LIMIT ?")) {
            statement.setString(1, collectionId);
            statement.setInt(2, Math.max(1, limit));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new LeaderboardRow(
                            UUID.fromString(resultSet.getString("player_uuid")),
                            resultSet.getString("player_name"),
                            Math.max(0, resultSet.getLong("progress"))
                    ));
                }
            }
        }
        return rows;
    }

    public CompletableFuture<List<LeaderboardRow>> loadCategoryLeaderboard(String categoryId, List<String> collectionIds, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return loadCategoryLeaderboardSync(categoryId, collectionIds, limit);
            } catch (SQLException exception) {
                throw new StorageException(exception);
            }
        }, readExecutor);
    }

    public List<LeaderboardRow> loadCategoryLeaderboardSync(String categoryId, List<String> collectionIds, int limit) throws SQLException {
        List<String> normalizedIds = collectionIds == null
                ? List.of()
                : collectionIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
        if (normalizedIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", normalizedIds.stream().map(ignored -> "?").toList());
        List<LeaderboardRow> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT p.player_uuid, n.player_name, SUM(p.progress) AS progress "
                             + "FROM " + prefix + "player_progress p "
                             + "LEFT JOIN " + prefix + "player_names n ON p.player_uuid = n.player_uuid "
                             + "WHERE p.collection_id IN (" + placeholders + ") AND p.progress > 0 "
                             + "GROUP BY p.player_uuid, n.player_name "
                             + "ORDER BY progress DESC, p.player_uuid ASC LIMIT ?")) {
            int index = 1;
            for (String collectionId : normalizedIds) {
                statement.setString(index++, collectionId);
            }
            statement.setInt(index, Math.max(1, limit));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new LeaderboardRow(
                            UUID.fromString(resultSet.getString("player_uuid")),
                            resultSet.getString("player_name"),
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
        }, readExecutor);
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
        }, writeExecutor);
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

    public CompletableFuture<Void> applySnapshot(List<ProgressRow> progressRows, List<ClaimedRow> claimedRows, List<PlayerNameRow> playerNames) {
        return CompletableFuture.runAsync(() -> {
            try {
                applySnapshotSync(progressRows, claimedRows, playerNames);
            } catch (SQLException exception) {
                throw new StorageException(exception);
            }
        }, writeExecutor);
    }

    public void applySnapshotSync(List<ProgressRow> progressRows, List<ClaimedRow> claimedRows, List<PlayerNameRow> playerNames) throws SQLException {
        if (progressRows.isEmpty() && claimedRows.isEmpty() && playerNames.isEmpty()) {
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
        String nameSql = storageType == StorageType.SQLITE
                ? "INSERT INTO " + prefix + "player_names (player_uuid, player_name, updated_at) VALUES (?, ?, ?) "
                + "ON CONFLICT(player_uuid) DO UPDATE SET player_name = excluded.player_name, updated_at = excluded.updated_at"
                : "INSERT INTO " + prefix + "player_names (player_uuid, player_name, updated_at) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), updated_at = VALUES(updated_at)";
        long now = System.currentTimeMillis();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement progressStatement = connection.prepareStatement(progressSql);
             PreparedStatement claimStatement = connection.prepareStatement(claimSql);
             PreparedStatement nameStatement = connection.prepareStatement(nameSql)) {
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

                for (PlayerNameRow row : playerNames) {
                    if (row.playerName() == null || row.playerName().isBlank()) {
                        continue;
                    }
                    String cleanName = row.playerName().length() > 64 ? row.playerName().substring(0, 64) : row.playerName();
                    nameStatement.setString(1, row.playerId().toString());
                    nameStatement.setString(2, cleanName);
                    nameStatement.setLong(3, now);
                    nameStatement.addBatch();
                }
                nameStatement.executeBatch();
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
        }, writeExecutor);
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

    public CompletableFuture<SnapshotData> loadSnapshot() {
        return supplyAfterWrites(this::loadSnapshotSync);
    }

    public SnapshotData loadSnapshotSync() throws SQLException {
        List<ProgressRow> progressRows = new ArrayList<>();
        List<ClaimedRow> claimedRows = new ArrayList<>();
        List<PlayerNameRow> playerNames = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT player_uuid, collection_id, progress FROM " + prefix + "player_progress");
                     ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        progressRows.add(new ProgressRow(
                                UUID.fromString(resultSet.getString("player_uuid")),
                                resultSet.getString("collection_id"),
                                Math.max(0, resultSet.getLong("progress"))
                        ));
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT player_uuid, collection_id, tier_id FROM " + prefix + "claimed_tiers");
                     ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        claimedRows.add(new ClaimedRow(
                                UUID.fromString(resultSet.getString("player_uuid")),
                                resultSet.getString("collection_id"),
                                resultSet.getString("tier_id")
                        ));
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT player_uuid, player_name FROM " + prefix + "player_names");
                     ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        playerNames.add(new PlayerNameRow(
                                UUID.fromString(resultSet.getString("player_uuid")),
                                resultSet.getString("player_name")
                        ));
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
        return new SnapshotData(progressRows, claimedRows, playerNames);
    }

    public CompletableFuture<Set<BlockLocationKey>> loadPlacedBlocks() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return loadPlacedBlocksSync();
            } catch (SQLException exception) {
                throw new StorageException(exception);
            }
        }, readExecutor);
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

    public CompletableFuture<Void> applyPlacedBlockBatch(Map<BlockLocationKey, String> upserts, Set<BlockLocationKey> removals) {
        Map<BlockLocationKey, String> capturedUpserts = Map.copyOf(upserts);
        Set<BlockLocationKey> capturedRemovals = Set.copyOf(removals);
        return CompletableFuture.runAsync(() -> {
            try {
                applyPlacedBlockBatchSync(capturedUpserts, capturedRemovals);
            } catch (SQLException exception) {
                throw new StorageException(exception);
            }
        }, writeExecutor);
    }

    public void applyPlacedBlockBatchOrderedSync(Map<BlockLocationKey, String> upserts, Set<BlockLocationKey> removals) throws SQLException {
        awaitWrite(applyPlacedBlockBatch(upserts, removals));
    }

    public void awaitWrites() throws SQLException {
        awaitWrite(CompletableFuture.runAsync(() -> {
        }, writeExecutor));
    }

    private void applyPlacedBlockBatchSync(Map<BlockLocationKey, String> upserts, Set<BlockLocationKey> removals) throws SQLException {
        if (upserts.isEmpty() && removals.isEmpty()) {
            return;
        }
        String upsertSql = storageType == StorageType.SQLITE
                ? "INSERT OR REPLACE INTO " + prefix + "placed_blocks (world, x, y, z, material, created_at) VALUES (?, ?, ?, ?, ?, ?)"
                : "REPLACE INTO " + prefix + "placed_blocks (world, x, y, z, material, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        String removeSql = "DELETE FROM " + prefix + "placed_blocks WHERE world=? AND x=? AND y=? AND z=?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement upsertStatement = connection.prepareStatement(upsertSql);
             PreparedStatement removeStatement = connection.prepareStatement(removeSql)) {
            connection.setAutoCommit(false);
            try {
                for (BlockLocationKey key : removals) {
                    setBlockLocation(removeStatement, key);
                    removeStatement.addBatch();
                }
                removeStatement.executeBatch();
                long now = System.currentTimeMillis();
                for (Map.Entry<BlockLocationKey, String> entry : upserts.entrySet()) {
                    setBlockLocation(upsertStatement, entry.getKey());
                    upsertStatement.setString(5, entry.getValue());
                    upsertStatement.setLong(6, now);
                    upsertStatement.addBatch();
                }
                upsertStatement.executeBatch();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private void setBlockLocation(PreparedStatement statement, BlockLocationKey key) throws SQLException {
        statement.setString(1, key.world());
        statement.setInt(2, key.x());
        statement.setInt(3, key.y());
        statement.setInt(4, key.z());
    }

    public void close() {
        writeExecutor.shutdown();
        readExecutor.shutdown();
        try {
            if (!writeExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                writeExecutor.shutdownNow();
            }
            if (!readExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                readExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            writeExecutor.shutdownNow();
            readExecutor.shutdownNow();
        }
    }

    private void awaitWrite(CompletableFuture<Void> future) throws SQLException {
        try {
            future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting for an ordered storage write", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof StorageException storageException && storageException.getCause() instanceof SQLException sqlException) {
                throw sqlException;
            }
            throw new SQLException("Ordered storage write failed", cause);
        }
    }

    private boolean hasCaps(Map<String, Long> maxProgressByCollection) {
        if (maxProgressByCollection == null || maxProgressByCollection.isEmpty()) {
            return false;
        }
        for (long value : maxProgressByCollection.values()) {
            if (value > 0) {
                return true;
            }
        }
        return false;
    }

    private <T> CompletableFuture<T> supplyAfterWrites(SqlSupplier<T> supplier) {
        return CompletableFuture.runAsync(() -> {
        }, writeExecutor).thenApplyAsync(ignored -> {
            try {
                return supplier.get();
            } catch (SQLException exception) {
                throw new StorageException(exception);
            }
        }, readExecutor);
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws SQLException;
    }

    public static final class StorageException extends RuntimeException {
        public StorageException(Throwable cause) {
            super(cause);
        }
    }
}
