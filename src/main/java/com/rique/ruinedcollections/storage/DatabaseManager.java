package com.rique.ruinedcollections.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

public final class DatabaseManager {
    private static final int MAX_INDEX_NAME_LENGTH = 60;

    private HikariDataSource dataSource;
    private StorageType storageType;
    private String tablePrefix;

    public void start(JavaPlugin plugin) throws SQLException {
        FileConfiguration config = plugin.getConfig();
        storageType = "mysql".equalsIgnoreCase(config.getString("storage.type", "sqlite")) ? StorageType.MYSQL : StorageType.SQLITE;
        tablePrefix = sanitizePrefix(config.getString("storage.table-prefix", "rc_"));

        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("RuinedCollections-" + storageType.name());
        if (storageType == StorageType.SQLITE) {
            File dbFile = new File(plugin.getDataFolder(), config.getString("storage.sqlite.file", "data.db"));
            File parent = dbFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new SQLException("Could not create SQLite folder " + parent.getAbsolutePath());
            }
            hikari.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            hikari.setMaximumPoolSize(1);
            hikari.setConnectionInitSql("PRAGMA busy_timeout=5000");
        } else {
            String host = config.getString("storage.mysql.host", "localhost");
            int port = config.getInt("storage.mysql.port", 3306);
            String database = config.getString("storage.mysql.database", "minecraft");
            boolean useSsl = config.getBoolean("storage.mysql.use-ssl", false);
            hikari.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=" + useSsl
                    + "&characterEncoding=utf8"
                    + "&useUnicode=true"
                    + "&serverTimezone=UTC");
            hikari.setUsername(config.getString("storage.mysql.username", "root"));
            hikari.setPassword(config.getString("storage.mysql.password", ""));
            hikari.setMaximumPoolSize(Math.max(2, config.getInt("storage.mysql.pool-size", 10)));
        }
        hikari.setMinimumIdle(1);
        hikari.setConnectionTimeout(10000);
        hikari.setValidationTimeout(5000);
        dataSource = new HikariDataSource(hikari);
        migrate();
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public StorageType storageType() {
        return storageType;
    }

    public String tablePrefix() {
        return tablePrefix;
    }

    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private void migrate() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            if (storageType == StorageType.SQLITE) {
                statement.execute("PRAGMA foreign_keys=ON");
                statement.execute("PRAGMA journal_mode=WAL");
            }
            statement.execute("CREATE TABLE IF NOT EXISTS " + tablePrefix + "schema (version INTEGER NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS " + tablePrefix + "player_progress ("
                    + "player_uuid VARCHAR(36) NOT NULL,"
                    + "collection_id VARCHAR(64) NOT NULL,"
                    + "progress BIGINT NOT NULL DEFAULT 0,"
                    + "updated_at BIGINT NOT NULL,"
                    + "PRIMARY KEY (player_uuid, collection_id)"
                    + ")");
            statement.execute("CREATE TABLE IF NOT EXISTS " + tablePrefix + "claimed_tiers ("
                    + "player_uuid VARCHAR(36) NOT NULL,"
                    + "collection_id VARCHAR(64) NOT NULL,"
                    + "tier_id VARCHAR(64) NOT NULL,"
                    + "claimed_at BIGINT NOT NULL,"
                    + "PRIMARY KEY (player_uuid, collection_id, tier_id)"
                    + ")");
            statement.execute("CREATE TABLE IF NOT EXISTS " + tablePrefix + "placed_blocks ("
                    + "world VARCHAR(128) NOT NULL,"
                    + "x INTEGER NOT NULL,"
                    + "y INTEGER NOT NULL,"
                    + "z INTEGER NOT NULL,"
                    + "material VARCHAR(64) NOT NULL,"
                    + "created_at BIGINT NOT NULL,"
                    + "PRIMARY KEY (world, x, y, z)"
                    + ")");
            int schemaVersion = schemaVersion(statement);
            if (schemaVersion == 0) {
                statement.executeUpdate("INSERT INTO " + tablePrefix + "schema (version) VALUES (1)");
                schemaVersion = 1;
            }
            statement.execute("CREATE TABLE IF NOT EXISTS " + tablePrefix + "player_names ("
                    + "player_uuid VARCHAR(36) NOT NULL,"
                    + "player_name VARCHAR(64) NOT NULL,"
                    + "updated_at BIGINT NOT NULL,"
                    + "PRIMARY KEY (player_uuid)"
                    + ")");
            if (schemaVersion < 2) {
                statement.executeUpdate("UPDATE " + tablePrefix + "schema SET version=2");
            }
            createIndexes(statement);
        }
    }

    private void createIndexes(Statement statement) throws SQLException {
        createIndex(statement, indexName("progress_collection_idx"),
                tablePrefix + "player_progress", "collection_id, progress");
        createIndex(statement, indexName("progress_updated_idx"),
                tablePrefix + "player_progress", "updated_at");
    }

    private void createIndex(Statement statement, String name, String table, String columns) throws SQLException {
        String sql = storageType == StorageType.SQLITE
                ? "CREATE INDEX IF NOT EXISTS " + name + " ON " + table + " (" + columns + ")"
                : "CREATE INDEX " + name + " ON " + table + " (" + columns + ")";
        try {
            statement.execute(sql);
        } catch (SQLException exception) {
            if (storageType == StorageType.MYSQL && duplicateIndex(exception)) {
                return;
            }
            throw exception;
        }
    }

    private boolean duplicateIndex(SQLException exception) {
        return exception.getErrorCode() == 1061;
    }

    private String indexName(String suffix) {
        String name = tablePrefix + suffix;
        if (name.length() <= MAX_INDEX_NAME_LENGTH) {
            return name;
        }
        return "rc_" + Integer.toHexString(tablePrefix.hashCode()).replace("-", "n") + "_" + suffix;
    }

    private int schemaVersion(Statement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery("SELECT version FROM " + tablePrefix + "schema LIMIT 1")) {
            return resultSet.next() ? resultSet.getInt("version") : 0;
        }
    }

    private String sanitizePrefix(String prefix) {
        String value = prefix == null ? "rc_" : prefix.toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z0-9_]+")) {
            return "rc_";
        }
        return value;
    }
}
