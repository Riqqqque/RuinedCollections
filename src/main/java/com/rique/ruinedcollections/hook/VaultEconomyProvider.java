package com.rique.ruinedcollections.hook;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class VaultEconomyProvider implements EconomyProvider {
    private final Economy economy;

    private VaultEconomyProvider(Economy economy) {
        this.economy = economy;
    }

    public static EconomyProvider create(JavaPlugin plugin) {
        RegisteredServiceProvider<Economy> registration = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null) {
            return new NoEconomyProvider();
        }
        return new VaultEconomyProvider(registration.getProvider());
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public boolean deposit(Player player, double amount) {
        return economy.depositPlayer(player, amount).transactionSuccess();
    }
}
