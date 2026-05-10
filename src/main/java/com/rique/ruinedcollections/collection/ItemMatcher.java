package com.rique.ruinedcollections.collection;

import com.rique.ruinedcollections.util.Text;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public record ItemMatcher(Integer customModelData, String displayName, List<PersistentDataRule> persistentDataRules) {
    @SuppressWarnings("deprecation")
    public boolean matches(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return customModelData == null && displayName == null && persistentDataRules.isEmpty();
        }
        if (customModelData != null) {
            if (!meta.hasCustomModelData() || meta.getCustomModelData() != customModelData) {
                return false;
            }
        }
        if (displayName != null && !displayName.isBlank()) {
            if (!meta.hasDisplayName() || !Text.strip(Text.legacy(meta.displayName())).equalsIgnoreCase(Text.strip(displayName))) {
                return false;
            }
        }
        for (PersistentDataRule rule : persistentDataRules) {
            if (!rule.matches(meta.getPersistentDataContainer())) {
                return false;
            }
        }
        return true;
    }

    public static ItemMatcher any() {
        return new ItemMatcher(null, null, List.of());
    }
}
