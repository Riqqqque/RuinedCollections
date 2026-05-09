package com.rique.ruinedcollections.hook;

import org.bukkit.entity.Player;

public interface EconomyProvider {
    boolean enabled();

    boolean deposit(Player player, double amount);
}
