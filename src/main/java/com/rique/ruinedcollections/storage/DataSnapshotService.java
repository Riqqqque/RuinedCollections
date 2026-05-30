package com.rique.ruinedcollections.storage;

import com.rique.ruinedcollections.RuinedCollectionsPlugin;
import com.rique.ruinedcollections.diagnostics.DiagnosticService;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class DataSnapshotService {
    private static final String ID_PATTERN = "[a-z0-9_-]+";
    private static final String TIER_PATTERN = "[A-Za-z0-9_-]+";
    private static final int MAX_ID_LENGTH = 64;

    private final RuinedCollectionsPlugin plugin;
    private final CollectionRepository repository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "RuinedCollections-Snapshots");
        thread.setDaemon(true);
        return thread;
    });

    public DataSnapshotService(RuinedCollectionsPlugin plugin, CollectionRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public CompletableFuture<File> exportData(File file) {
        CompletableFuture<List<ProgressRow>> progressFuture = repository.loadAllProgress();
        CompletableFuture<List<ClaimedRow>> claimedFuture = repository.loadAllClaimed();
        CompletableFuture<List<PlayerNameRow>> namesFuture = repository.loadAllPlayerNames();
        return CompletableFuture.allOf(progressFuture, claimedFuture, namesFuture).thenApplyAsync(ignored -> {
            List<ProgressRow> progress = progressFuture.join();
            List<ClaimedRow> claimed = claimedFuture.join();
            List<PlayerNameRow> names = namesFuture.join();
            YamlConfiguration config = new YamlConfiguration();
            config.set("format-version", 2);
            config.set("exported-at", Instant.now().toString());
            for (PlayerNameRow row : names) {
                config.set("players." + row.playerId() + ".name", row.playerName());
            }
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
                plugin.diagnostics().info("export", "Exported collection data", DiagnosticService.fields(
                        "file", file.getAbsolutePath(),
                        "progressRows", progress.size(),
                        "claimedRows", claimed.size(),
                        "playerNames", names.size()
                ));
                return file;
            } catch (IOException exception) {
                throw new CollectionRepository.StorageException(exception);
            }
        }, executor);
    }

    public ImportPreview preview(File file) {
        YamlConfiguration config = loadYaml(file);
        List<ProgressRow> progressRows = new ArrayList<>();
        List<ClaimedRow> claimedRows = new ArrayList<>();
        List<PlayerNameRow> playerNames = new ArrayList<>();
        ConfigurationSection players = config.getConfigurationSection("players");
        if (players == null) {
            return new ImportPreview(progressRows, claimedRows, playerNames);
        }
        for (String uuidText : players.getKeys(false)) {
            UUID playerId;
            try {
                playerId = UUID.fromString(uuidText);
            } catch (IllegalArgumentException ignored) {
                plugin.diagnostics().warn("import", "Skipped invalid UUID in import", DiagnosticService.fields("uuid", uuidText));
                continue;
            }
            String playerName = players.getString(uuidText + ".name");
            if (playerName != null && !playerName.isBlank()) {
                playerNames.add(new PlayerNameRow(playerId, playerName));
            }
            ConfigurationSection collections = players.getConfigurationSection(uuidText + ".collections");
            if (collections == null) {
                continue;
            }
            for (String collectionId : collections.getKeys(false)) {
                String normalizedCollection = normalizeCollectionId(collectionId);
                if (!validCollectionId(normalizedCollection)) {
                    plugin.diagnostics().warn("import", "Skipped invalid collection id in import", DiagnosticService.fields(
                            "uuid", playerId,
                            "collection", collectionId
                    ));
                    continue;
                }
                long progress = Math.max(0, collections.getLong(collectionId + ".progress", 0));
                progressRows.add(new ProgressRow(playerId, normalizedCollection, progress));
                for (String tierId : collections.getStringList(collectionId + ".claimed-tiers")) {
                    if (!validTierId(tierId)) {
                        plugin.diagnostics().warn("import", "Skipped invalid claimed tier in import", DiagnosticService.fields(
                                "uuid", playerId,
                                "collection", normalizedCollection,
                                "tier", tierId
                        ));
                        continue;
                    }
                    claimedRows.add(new ClaimedRow(playerId, normalizedCollection, tierId));
                }
            }
        }
        return new ImportPreview(progressRows, claimedRows, playerNames);
    }

    public CompletableFuture<ImportPreview> previewAsync(File file) {
        return CompletableFuture.supplyAsync(() -> preview(file), executor);
    }

    public CompletableFuture<Void> apply(ImportPreview preview) {
        plugin.diagnostics().info("import", "Applying import snapshot", DiagnosticService.fields(
                "progressRows", preview.progressRows().size(),
                "claimedRows", preview.claimedRows().size(),
                "playerNames", preview.playerNames().size()
        ));
        return repository.applySnapshot(preview.progressRows(), preview.claimedRows(), preview.playerNames());
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

    private String normalizeCollectionId(String collectionId) {
        return collectionId == null ? "" : collectionId.toLowerCase(Locale.ROOT).trim();
    }

    private boolean validCollectionId(String collectionId) {
        return collectionId != null && !collectionId.isBlank()
                && collectionId.length() <= MAX_ID_LENGTH
                && collectionId.matches(ID_PATTERN);
    }

    private boolean validTierId(String tierId) {
        return tierId != null && !tierId.isBlank()
                && tierId.length() <= MAX_ID_LENGTH
                && tierId.matches(TIER_PATTERN);
    }

    private YamlConfiguration loadYaml(File file) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
            return config;
        } catch (IOException | InvalidConfigurationException exception) {
            throw new CollectionRepository.StorageException(exception);
        }
    }
}
