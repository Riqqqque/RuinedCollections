package com.rique.ruinedcollections.hook;

import com.rique.ruinedcollections.RuinedCollectionsPlugin;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

public final class PlaceholderBridge {
    private PlaceholderBridge() {
    }

    public static void register(RuinedCollectionsPlugin plugin) {
        new RuinedCollectionsExpansion(plugin).register();
    }

    public static String set(Player player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}
