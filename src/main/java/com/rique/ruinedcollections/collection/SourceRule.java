package com.rique.ruinedcollections.collection;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

public record SourceRule(
        CollectionSourceType type,
        Set<Material> materials,
        Set<EntityType> entities,
        ItemMatcher itemMatcher,
        long amount
) {
    public boolean matchesMaterial(Material material) {
        return materials.isEmpty() || materials.contains(material);
    }

    public boolean matchesEntity(EntityType entityType) {
        return entities.isEmpty() || entities.contains(entityType);
    }

    public boolean matchesItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        return matchesMaterial(item.getType()) && itemMatcher.matches(item);
    }
}
