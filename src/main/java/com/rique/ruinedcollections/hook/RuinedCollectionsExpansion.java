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
        if (player == null || player.getUniqueId() == null) {
            return "";
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

    private long nextGoal(OfflinePlayer player, String collectionId) {
        Optional<CollectionDefinition> collection = plugin.collectionRegistry().get(collectionId);
        if (collection.isEmpty()) {
            return 0;
        }
        long progress = plugin.progressService().cachedProgress(player.getUniqueId(), collectionId);
        return collection.get().nextTier(progress).map(CollectionTier::goal).orElse(collection.get().highestUnlockedGoal(progress));
    }
}
