package com.rique.ruinedcollections.collection;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.bukkit.entity.Player;

public final class CollectionDefinition {
    private final String id;
    private final boolean enabled;
    private final String category;
    private final String permission;
    private final boolean hiddenUntilProgress;
    private final String displayName;
    private final String menuTitle;
    private final List<String> description;
    private final DisplayItem displayItem;
    private final int menuSlot;
    private final List<SourceRule> sources;
    private final List<CollectionTier> tiers;
    private final File file;

    public CollectionDefinition(
            String id,
            boolean enabled,
            String category,
            String permission,
            boolean hiddenUntilProgress,
            String displayName,
            String menuTitle,
            List<String> description,
            DisplayItem displayItem,
            int menuSlot,
            List<SourceRule> sources,
            List<CollectionTier> tiers,
            File file
    ) {
        this.id = id;
        this.enabled = enabled;
        this.category = category == null ? "" : category;
        this.permission = permission == null ? "" : permission;
        this.hiddenUntilProgress = hiddenUntilProgress;
        this.displayName = displayName;
        this.menuTitle = menuTitle == null ? "" : menuTitle;
        this.description = List.copyOf(description);
        this.displayItem = displayItem;
        this.menuSlot = menuSlot;
        this.sources = List.copyOf(sources);
        this.tiers = tiers.stream()
                .sorted(Comparator.comparingLong(CollectionTier::goal))
                .toList();
        this.file = file;
    }

    public String id() {
        return id;
    }

    public boolean enabled() {
        return enabled;
    }

    public String category() {
        return category;
    }

    public String permission() {
        return permission;
    }

    public boolean hiddenUntilProgress() {
        return hiddenUntilProgress;
    }

    public String displayName() {
        return displayName;
    }

    public String menuTitle() {
        return menuTitle;
    }

    public List<String> description() {
        return description;
    }

    public DisplayItem displayItem() {
        return displayItem;
    }

    public int menuSlot() {
        return menuSlot;
    }

    public List<SourceRule> sources() {
        return sources;
    }

    public List<CollectionTier> tiers() {
        return tiers;
    }

    public File file() {
        return file;
    }

    public Optional<CollectionTier> nextTier(long progress) {
        return tiers.stream().filter(tier -> progress < tier.goal()).findFirst();
    }

    public long highestUnlockedGoal(long progress) {
        long unlocked = 0;
        for (CollectionTier tier : tiers) {
            if (progress >= tier.goal()) {
                unlocked = tier.goal();
            }
        }
        return unlocked;
    }

    public long maxGoal() {
        return tiers.isEmpty() ? 0 : tiers.get(tiers.size() - 1).goal();
    }

    public int unlockedTierCount(long progress) {
        int unlocked = 0;
        for (CollectionTier tier : tiers) {
            if (progress >= tier.goal()) {
                unlocked++;
            }
        }
        return unlocked;
    }

    public boolean maxed(long progress) {
        long maxGoal = maxGoal();
        return maxGoal > 0 && progress >= maxGoal;
    }

    public boolean hasAccess(Player player) {
        if (permission.isBlank()) {
            return true;
        }
        return player != null && (player.hasPermission(permission) || player.hasPermission("ruinedcollections.admin"));
    }

    public boolean visibleTo(Player player, long progress) {
        if (!hasAccess(player)) {
            return false;
        }
        return !hiddenUntilProgress || progress > 0 || (player != null && player.hasPermission("ruinedcollections.admin"));
    }
}
