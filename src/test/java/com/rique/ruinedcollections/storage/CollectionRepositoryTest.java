package com.rique.ruinedcollections.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CollectionRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    private HikariDataSource dataSource;
    private CollectionRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + temporaryDirectory.resolve("test.db"));
        config.setMaximumPoolSize(1);
        dataSource = new HikariDataSource(config);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE rc_player_progress (player_uuid VARCHAR(36) NOT NULL, collection_id VARCHAR(64) NOT NULL, progress BIGINT NOT NULL, updated_at BIGINT NOT NULL, PRIMARY KEY (player_uuid, collection_id))");
            statement.execute("CREATE TABLE rc_claimed_tiers (player_uuid VARCHAR(36) NOT NULL, collection_id VARCHAR(64) NOT NULL, tier_id VARCHAR(64) NOT NULL, claimed_at BIGINT NOT NULL, PRIMARY KEY (player_uuid, collection_id, tier_id))");
            statement.execute("CREATE TABLE rc_player_names (player_uuid VARCHAR(36) NOT NULL, player_name VARCHAR(64) NOT NULL, updated_at BIGINT NOT NULL, PRIMARY KEY (player_uuid))");
            statement.execute("CREATE TABLE rc_placed_blocks (world VARCHAR(128) NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL, material VARCHAR(64) NOT NULL, created_at BIGINT NOT NULL, PRIMARY KEY (world, x, y, z))");
        }
        repository = new CollectionRepository(dataSource, StorageType.SQLITE, "rc_");
    }

    @AfterEach
    void tearDown() {
        if (repository != null) {
            repository.close();
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void mutationsStayOrderedAndSnapshotIncludesRelatedData() {
        UUID playerId = UUID.randomUUID();
        ProgressKey key = new ProgressKey(playerId, "oak_log");

        repository.addProgressBatch(Map.of(key, 5L));
        repository.setProgress(playerId, "oak_log", 10L);
        repository.addProgressBatch(Map.of(key, 7L)).join();
        repository.savePlayerName(playerId, "Rique").join();
        assertTrue(repository.claimTier(playerId, "oak_log", "I").join());

        SnapshotData snapshot = repository.loadSnapshot().join();
        assertEquals(17L, repository.loadPlayer(playerId).join().progress().get("oak_log"));
        assertEquals(1, snapshot.progressRows().size());
        assertEquals(17L, snapshot.progressRows().getFirst().progress());
        assertEquals("Rique", snapshot.playerNames().getFirst().playerName());
        assertEquals("I", snapshot.claimedRows().getFirst().tierId());
    }

    @Test
    void additionsClampAtLongAndConfiguredLimits() {
        UUID playerId = UUID.randomUUID();
        ProgressKey overflowKey = new ProgressKey(playerId, "overflow");
        ProgressKey cappedKey = new ProgressKey(playerId, "capped");

        repository.setProgress(playerId, "overflow", Long.MAX_VALUE - 2).join();
        repository.addProgressBatch(Map.of(overflowKey, 10L)).join();
        repository.setProgress(playerId, "capped", 90L).join();
        repository.addProgressBatch(Map.of(cappedKey, 20L), Map.of("capped", 100L)).join();

        PlayerData data = repository.loadPlayer(playerId).join();
        assertEquals(Long.MAX_VALUE, data.progress().get("overflow"));
        assertEquals(100L, data.progress().get("capped"));
    }

    @Test
    void placedBlockBatchesApplyUpsertsAndRemovalsAtomically() {
        BlockLocationKey first = new BlockLocationKey("world", 1, 64, 1);
        BlockLocationKey second = new BlockLocationKey("world", 2, 64, 2);

        repository.applyPlacedBlockBatch(Map.of(first, "OAK_LOG", second, "OBSIDIAN"), Set.of()).join();
        assertEquals(Set.of(first, second), repository.loadPlacedBlocks().join());

        repository.applyPlacedBlockBatch(Map.of(), Set.of(first)).join();
        assertEquals(Set.of(second), repository.loadPlacedBlocks().join());
    }
}
