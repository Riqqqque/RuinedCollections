package com.rique.ruinedcollections.metrics;

import com.rique.ruinedcollections.RuinedCollectionsPlugin;
import com.rique.ruinedcollections.collection.CollectionDefinition;
import com.rique.ruinedcollections.collection.CollectionSourceType;
import com.rique.ruinedcollections.diagnostics.DiagnosticService;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class MetricsService {
    private static final String BSTATS_PLUGIN_ID_PATH = "integrations.bstats.plugin-id";
    private static final int DEFAULT_BSTATS_PLUGIN_ID = 31969;

    private final RuinedCollectionsPlugin plugin;
    private Metrics metrics;
    private int activePluginId;
    private volatile boolean leaderboardsEnabled;

    public MetricsService(RuinedCollectionsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        leaderboardsEnabled = plugin.getConfig().getBoolean("leaderboards.enabled", true);
        int pluginId = configuredPluginId();
        if (pluginId <= 0) {
            stop();
            plugin.diagnostics().info("metrics", "bStats metrics are disabled by config");
            return;
        }
        if (metrics != null) {
            if (activePluginId == pluginId) {
                return;
            }
            stop();
        }
        metrics = new Metrics(plugin, pluginId);
        activePluginId = pluginId;
        registerCharts();
        plugin.diagnostics().info("metrics", "bStats metrics started", DiagnosticService.fields("pluginId", pluginId));
    }

    public void stop() {
        if (metrics == null) {
            return;
        }
        metrics.shutdown();
        metrics = null;
        activePluginId = 0;
    }

    private int configuredPluginId() {
        if (!plugin.getConfig().isSet(BSTATS_PLUGIN_ID_PATH)) {
            plugin.diagnostics().info("metrics", "bStats plugin id missing from config; using bundled default",
                    DiagnosticService.fields("pluginId", DEFAULT_BSTATS_PLUGIN_ID));
            return DEFAULT_BSTATS_PLUGIN_ID;
        }
        if (!plugin.getConfig().isInt(BSTATS_PLUGIN_ID_PATH)) {
            plugin.diagnostics().warn("metrics", "bStats plugin id is not a number; using bundled default",
                    DiagnosticService.fields("path", BSTATS_PLUGIN_ID_PATH, "pluginId", DEFAULT_BSTATS_PLUGIN_ID));
            return DEFAULT_BSTATS_PLUGIN_ID;
        }
        return plugin.getConfig().getInt(BSTATS_PLUGIN_ID_PATH);
    }

    private void registerCharts() {
        metrics.addCustomChart(new SimplePie("storage_type", () ->
                plugin.storageType().name().toLowerCase(Locale.ROOT)));
        metrics.addCustomChart(new SimplePie("leaderboards_enabled", () ->
                enabled(leaderboardsEnabled)));
        metrics.addCustomChart(new SimplePie("placeholderapi_hook", () ->
                enabled(plugin.hooks().placeholderApiEnabled())));
        metrics.addCustomChart(new SimplePie("vault_hook", () ->
                enabled(plugin.hooks().vaultEnabled())));
        metrics.addCustomChart(new SimplePie("luckperms_hook", () ->
                enabled(plugin.hooks().luckPermsEnabled())));
        metrics.addCustomChart(new SingleLineChart("enabled_collections", () ->
                plugin.collectionRegistry().enabledCollections().size()));
        metrics.addCustomChart(new AdvancedPie("source_types", this::sourceTypes));
    }

    private Map<String, Integer> sourceTypes() {
        Map<CollectionSourceType, Integer> counts = new EnumMap<>(CollectionSourceType.class);
        for (CollectionDefinition collection : plugin.collectionRegistry().enabledCollections()) {
            collection.sources().forEach(source -> counts.merge(source.type(), 1, Integer::sum));
        }
        Map<String, Integer> chart = new HashMap<>();
        counts.forEach((type, count) -> {
            if (count > 0) {
                chart.put(type.name().toLowerCase(Locale.ROOT), count);
            }
        });
        return chart;
    }

    private String enabled(boolean value) {
        return value ? "enabled" : "disabled";
    }
}
