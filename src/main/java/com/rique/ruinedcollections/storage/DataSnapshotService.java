package com.rique.ruinedcollections.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class DataSnapshotService {
    private final JavaPlugin plugin;
    private final CollectionRepository repository;

    public DataSnapshotService(JavaPlugin plugin, CollectionRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public CompletableFuture<File> exportData(File file) {
        return repository.loadAllProgress().thenCombine(repository.loadAllClaimed(), (progress, claimed) -> {
            YamlConfiguration config = new YamlConfiguration();
            config.set("format-version", 1);
            config.set("exported-at", Instant.now().toString());
            for (ProgressRow row : progress) {
                String base = "players." + row.playerId();
                config.set(base + ".collections." + row.collectionId() + ".progress", row.progress());
            }
            for (ClaimedRow row : claimed) {
                String path = "players." + row.playerId() + ".collections." + row.collectionId() + ".claimed-tiers";
                List<String> tiers = config.getStringList(path);
                if (!tiers.contains(row.tierId())) {
                    tiers.add(row.tierId());
                }
                config.set(path, tiers);
            }
            try {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("Could not create " + parent.getAbsolutePath());
                }
                config.save(file);
                return file;
            } catch (IOException exception) {
                throw new CollectionRepository.StorageException(exception);
            }
        });
    }

    public ImportPreview preview(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<ProgressRow> progressRows = new ArrayList<>();
        List<ClaimedRow> claimedRows = new ArrayList<>();
        ConfigurationSection players = config.getConfigurationSection("players");
        if (players == null) {
            return new ImportPreview(progressRows, claimedRows);
        }
        for (String uuidText : players.getKeys(false)) {
            UUID playerId;
            try {
                playerId = UUID.fromString(uuidText);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Skipped invalid UUID in import: " + uuidText);
                continue;
            }
            ConfigurationSection collections = players.getConfigurationSection(uuidText + ".collections");
            if (collections == null) {
                continue;
            }
            for (String collectionId : collections.getKeys(false)) {
                long progress = Math.max(0, collections.getLong(collectionId + ".progress", 0));
                progressRows.add(new ProgressRow(playerId, collectionId, progress));
                for (String tierId : collections.getStringList(collectionId + ".claimed-tiers")) {
                    claimedRows.add(new ClaimedRow(playerId, collectionId, tierId));
                }
            }
        }
        return new ImportPreview(progressRows, claimedRows);
    }

    public CompletableFuture<Void> apply(ImportPreview preview) {
        return CompletableFuture.runAsync(() -> {
            try {
                for (ProgressRow row : preview.progressRows()) {
                    repository.setProgressSync(row.playerId(), row.collectionId(), row.progress());
                }
                for (ClaimedRow row : preview.claimedRows()) {
                    repository.claimTierSync(row.playerId(), row.collectionId(), row.tierId());
                }
            } catch (SQLException exception) {
                throw new CollectionRepository.StorageException(exception);
            }
        });
    }
}
