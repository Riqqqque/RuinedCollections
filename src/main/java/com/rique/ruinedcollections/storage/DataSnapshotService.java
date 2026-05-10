package com.rique.ruinedcollections.storage;

import com.rique.ruinedcollections.RuinedCollectionsPlugin;
import com.rique.ruinedcollections.diagnostics.DiagnosticService;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class DataSnapshotService {
    private final RuinedCollectionsPlugin plugin;
    private final CollectionRepository repository;

    public DataSnapshotService(RuinedCollectionsPlugin plugin, CollectionRepository repository) {
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
                plugin.diagnostics().info("export", "Exported collection data", DiagnosticService.fields(
                        "file", file.getAbsolutePath(),
                        "progressRows", progress.size(),
                        "claimedRows", claimed.size()
                ));
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
                plugin.diagnostics().warn("import", "Skipped invalid UUID in import", DiagnosticService.fields("uuid", uuidText));
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
        plugin.diagnostics().info("import", "Applying import snapshot", DiagnosticService.fields(
                "progressRows", preview.progressRows().size(),
                "claimedRows", preview.claimedRows().size()
        ));
        return repository.applySnapshot(preview.progressRows(), preview.claimedRows());
    }
}
