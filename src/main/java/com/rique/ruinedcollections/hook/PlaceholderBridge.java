package com.rique.ruinedcollections.hook;

import com.rique.ruinedcollections.RuinedCollectionsPlugin;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

public final class PlaceholderBridge {
    private PlaceholderBridge() {
    }

    public static RuinedCollectionsExpansion register(RuinedCollectionsPlugin plugin) {
        RuinedCollectionsExpansion expansion = new RuinedCollectionsExpansion(plugin);
        expansion.register();
        return expansion;
    }

    public static String set(Player player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}
