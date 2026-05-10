package com.rique.ruinedcollections.hook;

import com.rique.ruinedcollections.RuinedCollectionsPlugin;
import com.rique.ruinedcollections.collection.CollectionDefinition;
import com.rique.ruinedcollections.collection.CollectionTier;
import com.rique.ruinedcollections.util.Longs;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public final class RuinedCollectionsExpansion extends PlaceholderExpansion {
    private final RuinedCollectionsPlugin plugin;

    public RuinedCollectionsExpansion(RuinedCollectionsPlugin plugin) {
        this.plugin = plugin;
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
            return String.valueOf(Math.min(100, (progress * 100) / next));
        }
        if (params.startsWith("tier_")) {
            String id = params.substring("tier_".length());
            Optional<CollectionDefinition> collection = plugin.collectionRegistry().get(id);
            if (collection.isEmpty()) {
                return "0";
            }
            long progress = plugin.progressService().cachedProgress(player.getUniqueId(), id);
            int unlocked = 0;
            for (CollectionTier tier : collection.get().tiers()) {
                if (progress >= tier.goal()) {
                    unlocked++;
                }
            }
            return String.valueOf(unlocked);
        }
        return null;
    }

    private String topPlaceholder(String params) {
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

    private record ParsedTop(String collectionId, int position, String field) {
    }
}
