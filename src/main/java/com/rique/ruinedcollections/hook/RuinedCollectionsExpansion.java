package com.rique.ruinedcollections.hook;

import com.rique.ruinedcollections.RuinedCollectionsPlugin;
import com.rique.ruinedcollections.collection.CollectionDefinition;
import com.rique.ruinedcollections.collection.CollectionTier;
import com.rique.ruinedcollections.util.Longs;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class RuinedCollectionsExpansion extends PlaceholderExpansion {
    private final RuinedCollectionsPlugin plugin;
    private volatile int progressBarLength;
    private volatile String progressBarFilled;
    private volatile String progressBarEmpty;

    public RuinedCollectionsExpansion(RuinedCollectionsPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    @Override
    public @NotNull String getIdentifier() {
        return "ruinedcollections";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Rique";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    public void reload() {
        progressBarLength = Math.max(1, Math.min(plugin.getConfig().getInt("placeholders.progress-bar.length", 20), 100));
        progressBarFilled = plugin.getConfig().getString("placeholders.progress-bar.filled", "&a|");
        progressBarEmpty = plugin.getConfig().getString("placeholders.progress-bar.empty", "&7|");
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        String top = topPlaceholder(params);
        if (top != null) {
            return top;
        }
        if (player == null || player.getUniqueId() == null) {
            return "";
        }
        if (params.startsWith("rank_")) {
            return plugin.leaderboards().rankValue(player.getUniqueId(), params.substring("rank_".length()), false);
        }
        if (params.startsWith("raw_rank_")) {
            return plugin.leaderboards().rankValue(player.getUniqueId(), params.substring("raw_rank_".length()), true);
        }
        if (params.startsWith("leaderboard_rank_")) {
            return plugin.leaderboards().rankValue(player.getUniqueId(), params.substring("leaderboard_rank_".length()), false);
        }
        if (params.startsWith("leaderboard_raw_rank_")) {
            return plugin.leaderboards().rankValue(player.getUniqueId(), params.substring("leaderboard_raw_rank_".length()), true);
        }
        if (params.startsWith("progress_")) {
            return Longs.format(plugin.progressService().cachedProgress(player.getUniqueId(), params.substring("progress_".length())));
        }
        if (params.startsWith("raw_progress_")) {
            return String.valueOf(plugin.progressService().cachedProgress(player.getUniqueId(), params.substring("raw_progress_".length())));
        }
        if (params.startsWith("max_goal_")) {
            return Longs.format(maxGoal(params.substring("max_goal_".length())));
        }
        if (params.startsWith("next_goal_")) {
            return Longs.format(nextGoal(player, params.substring("next_goal_".length())));
        }
        if (params.startsWith("remaining_")) {
            String id = params.substring("remaining_".length());
            long progress = plugin.progressService().cachedProgress(player.getUniqueId(), id);
            long next = nextGoal(player, id);
            return Longs.format(Math.max(0, next - progress));
        }
        if (params.startsWith("percent_")) {
            String id = params.substring("percent_".length());
            long progress = plugin.progressService().cachedProgress(player.getUniqueId(), id);
            long next = nextGoal(player, id);
            if (next <= 0) {
                return "100";
            }
            return String.valueOf((long) Math.min(100D, (progress * 100D) / next));
        }
        if (params.startsWith("max_percent_")) {
            String id = params.substring("max_percent_".length());
            long progress = plugin.progressService().cachedProgress(player.getUniqueId(), id);
            long maxGoal = maxGoal(id);
            return String.valueOf(percent(Math.min(progress, maxGoal), maxGoal));
        }
        if (params.startsWith("progressbar_")) {
            String id = params.substring("progressbar_".length());
            long progress = plugin.progressService().cachedProgress(player.getUniqueId(), id);
            return progressBar(progress, nextGoal(player, id));
        }
        if (params.startsWith("max_progressbar_")) {
            String id = params.substring("max_progressbar_".length());
            long progress = plugin.progressService().cachedProgress(player.getUniqueId(), id);
            long maxGoal = maxGoal(id);
            return progressBar(Math.min(progress, maxGoal), maxGoal);
        }
        if (params.startsWith("maxed_")) {
            String id = params.substring("maxed_".length());
            Optional<CollectionDefinition> collection = plugin.collectionRegistry().get(id);
            long progress = plugin.progressService().cachedProgress(player.getUniqueId(), id);
            return collection.map(value -> value.maxed(progress) ? "yes" : "no").orElse("no");
        }
        if (params.startsWith("total_tiers_")) {
            Optional<CollectionDefinition> collection = plugin.collectionRegistry().get(params.substring("total_tiers_".length()));
            return collection.map(value -> String.valueOf(value.tiers().size())).orElse("0");
        }
        if (params.startsWith("tier_")) {
            String id = params.substring("tier_".length());
            Optional<CollectionDefinition> collection = plugin.collectionRegistry().get(id);
            if (collection.isEmpty()) {
                return "0";
            }
            long progress = plugin.progressService().cachedProgress(player.getUniqueId(), id);
            return String.valueOf(collection.get().unlockedTierCount(progress));
        }
        if (params.startsWith("category_raw_progress_")) {
            return String.valueOf(categoryProgress(player.getUniqueId(), params.substring("category_raw_progress_".length())));
        }
        if (params.startsWith("set_raw_progress_")) {
            return String.valueOf(categoryProgress(player.getUniqueId(), params.substring("set_raw_progress_".length())));
        }
        if (params.startsWith("category_progress_")) {
            return Longs.format(categoryProgress(player.getUniqueId(), params.substring("category_progress_".length())));
        }
        if (params.startsWith("set_progress_")) {
            return Longs.format(categoryProgress(player.getUniqueId(), params.substring("set_progress_".length())));
        }
        if (params.startsWith("category_total_")) {
            return String.valueOf(categoryCollections(params.substring("category_total_".length())).size());
        }
        if (params.startsWith("set_total_")) {
            return String.valueOf(categoryCollections(params.substring("set_total_".length())).size());
        }
        if (params.startsWith("category_maxed_")) {
            return String.valueOf(categoryMaxed(player.getUniqueId(), params.substring("category_maxed_".length())));
        }
        if (params.startsWith("set_maxed_")) {
            return String.valueOf(categoryMaxed(player.getUniqueId(), params.substring("set_maxed_".length())));
        }
        if (params.startsWith("category_percent_")) {
            String category = params.substring("category_percent_".length());
            long progress = categoryCappedProgress(player.getUniqueId(), category);
            long maxGoal = categoryMaxGoal(category);
            return String.valueOf(percent(progress, maxGoal));
        }
        if (params.startsWith("set_percent_")) {
            String category = params.substring("set_percent_".length());
            long progress = categoryCappedProgress(player.getUniqueId(), category);
            long maxGoal = categoryMaxGoal(category);
            return String.valueOf(percent(progress, maxGoal));
        }
        if (params.startsWith("category_progressbar_")) {
            String category = params.substring("category_progressbar_".length());
            return progressBar(categoryCappedProgress(player.getUniqueId(), category), categoryMaxGoal(category));
        }
        if (params.startsWith("set_progressbar_")) {
            String category = params.substring("set_progressbar_".length());
            return progressBar(categoryCappedProgress(player.getUniqueId(), category), categoryMaxGoal(category));
        }
        return null;
    }

    private String topPlaceholder(String params) {
        if (params.startsWith("set_top_")) {
            return parseCategoryTop(params.substring("set_top_".length()));
        }
        if (params.startsWith("set_leaderboard_")) {
            return parseCategoryTop(params.substring("set_leaderboard_".length()));
        }
        if (params.startsWith("category_top_")) {
            return parseCategoryTop(params.substring("category_top_".length()));
        }
        if (params.startsWith("category_leaderboard_")) {
            return parseCategoryTop(params.substring("category_leaderboard_".length()));
        }
        if (params.startsWith("top_")) {
            return parseTop(params.substring("top_".length()));
        }
        if (params.startsWith("leaderboard_")) {
            return parseTop(params.substring("leaderboard_".length()));
        }
        return null;
    }

    private String parseTop(String input) {
        ParsedTop parsed = parseTopParts(input);
        if (parsed == null) {
            return null;
        }
        return plugin.leaderboards().topValue(parsed.collectionId(), parsed.position(), parsed.field());
    }

    private String parseCategoryTop(String input) {
        ParsedTop parsed = parseTopParts(input);
        if (parsed == null) {
            return null;
        }
        return plugin.leaderboards().topCategoryValue(parsed.collectionId(), parsed.position(), parsed.field());
    }

    private ParsedTop parseTopParts(String input) {
        String field;
        String withoutField;
        if (input.endsWith("_raw_progress")) {
            field = "raw_progress";
            withoutField = input.substring(0, input.length() - "_raw_progress".length());
        } else {
            int fieldSplit = input.lastIndexOf('_');
            if (fieldSplit <= 0 || fieldSplit >= input.length() - 1) {
                return null;
            }
            field = input.substring(fieldSplit + 1);
            withoutField = input.substring(0, fieldSplit);
        }
        int positionSplit = withoutField.lastIndexOf('_');
        if (positionSplit <= 0 || positionSplit >= withoutField.length() - 1) {
            return null;
        }
        try {
            int position = Integer.parseInt(withoutField.substring(positionSplit + 1));
            return new ParsedTop(withoutField.substring(0, positionSplit), position, field);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private long nextGoal(OfflinePlayer player, String collectionId) {
        Optional<CollectionDefinition> collection = plugin.collectionRegistry().get(collectionId);
        if (collection.isEmpty()) {
            return 0;
        }
        long progress = plugin.progressService().cachedProgress(player.getUniqueId(), collectionId);
        return collection.get().nextTier(progress).map(CollectionTier::goal).orElse(collection.get().highestUnlockedGoal(progress));
    }

    private long maxGoal(String collectionId) {
        return plugin.collectionRegistry().get(collectionId)
                .map(CollectionDefinition::maxGoal)
                .orElse(0L);
    }

    private int percent(long progress, long goal) {
        if (goal <= 0) {
            return 100;
        }
        return (int) Math.min(100D, Math.max(0D, (progress * 100D) / goal));
    }

    private String progressBar(long progress, long goal) {
        int length = progressBarLength;
        String filled = progressBarFilled;
        String empty = progressBarEmpty;
        int filledCount = goal <= 0 ? length : (int) Math.floor(Math.min(1D, Math.max(0D, progress / (double) goal)) * length);
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < length; index++) {
            builder.append(index < filledCount ? filled : empty);
        }
        return builder.toString();
    }

    private List<CollectionDefinition> categoryCollections(String category) {
        return plugin.collectionRegistry().enabledCollectionsInCategory(category);
    }

    private long categoryProgress(UUID playerId, String category) {
        long total = 0;
        for (CollectionDefinition collection : categoryCollections(category)) {
            total = Longs.addClamped(total, plugin.progressService().cachedProgress(playerId, collection.id()));
        }
        return total;
    }

    private long categoryCappedProgress(UUID playerId, String category) {
        long total = 0;
        for (CollectionDefinition collection : categoryCollections(category)) {
            long maxGoal = collection.maxGoal();
            long progress = plugin.progressService().cachedProgress(playerId, collection.id());
            total = Longs.addClamped(total, maxGoal > 0 ? Math.min(progress, maxGoal) : progress);
        }
        return total;
    }

    private long categoryMaxGoal(String category) {
        long total = 0;
        for (CollectionDefinition collection : categoryCollections(category)) {
            total = Longs.addClamped(total, collection.maxGoal());
        }
        return total;
    }

    private int categoryMaxed(UUID playerId, String category) {
        int maxed = 0;
        for (CollectionDefinition collection : categoryCollections(category)) {
            if (collection.maxed(plugin.progressService().cachedProgress(playerId, collection.id()))) {
                maxed++;
            }
        }
        return maxed;
    }

    private record ParsedTop(String collectionId, int position, String field) {
    }
}
