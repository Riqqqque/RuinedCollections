package com.rique.ruinedcollections.hook;

import com.rique.ruinedcollections.RuinedCollectionsPlugin;
import org.bukkit.entity.Player;

public final class HookManager {
    private final RuinedCollectionsPlugin plugin;
    private EconomyProvider economyProvider = new NoEconomyProvider();
    private boolean placeholderApiEnabled;
    private boolean luckPermsEnabled;

    public HookManager(RuinedCollectionsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (plugin.getServer().getPluginManager().isPluginEnabled("Vault")) {
            try {
                economyProvider = VaultEconomyProvider.create(plugin);
                if (economyProvider.enabled()) {
                    plugin.diagnostics().info("hooks", "Hooked Vault economy");
                }
            } catch (NoClassDefFoundError error) {
                economyProvider = new NoEconomyProvider();
                plugin.diagnostics().debug("rewards", "Vault API was not available for economy rewards", java.util.Map.of("hook", "Vault"));
            }
        }
        placeholderApiEnabled = plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
        if (placeholderApiEnabled) {
            PlaceholderBridge.register(plugin);
            plugin.diagnostics().info("hooks", "Hooked PlaceholderAPI");
        }
        luckPermsEnabled = plugin.getServer().getPluginManager().isPluginEnabled("LuckPerms");
        if (luckPermsEnabled) {
            plugin.diagnostics().info("hooks", "LuckPerms found; command rewards can manage permissions");
        }
    }

    public EconomyProvider economy() {
        return economyProvider;
    }

    public boolean luckPermsEnabled() {
        return luckPermsEnabled;
    }

    public String placeholders(Player player, String text) {
        if (!placeholderApiEnabled) {
            return text;
        }
        return PlaceholderBridge.set(player, text);
    }
}
