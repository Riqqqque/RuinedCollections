package com.rique.ruinedcollections.collection;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class CollectionDefinition {
    private final String id;
    private final boolean enabled;
    private final String displayName;
    private final List<String> description;
    private final DisplayItem displayItem;
    private final int menuSlot;
    private final List<SourceRule> sources;
    private final List<CollectionTier> tiers;
    private final File file;

    public CollectionDefinition(
            String id,
            boolean enabled,
            String displayName,
            List<String> description,
            DisplayItem displayItem,
            int menuSlot,
            List<SourceRule> sources,
            List<CollectionTier> tiers,
            File file
    ) {
        this.id = id;
        this.enabled = enabled;
        this.displayName = displayName;
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

    public String displayName() {
        return displayName;
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
}
