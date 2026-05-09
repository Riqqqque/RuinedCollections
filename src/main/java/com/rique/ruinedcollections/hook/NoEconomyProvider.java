package com.rique.ruinedcollections.hook;

import org.bukkit.entity.Player;

public final class NoEconomyProvider implements EconomyProvider {
    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public boolean deposit(Player player, double amount) {
        return false;
    }
}
