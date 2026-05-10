package com.rique.ruinedcollections.scheduler;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public interface SchedulerAdapter {
    TaskHandle runAsync(Runnable runnable);

    TaskHandle runGlobal(Runnable runnable);

    TaskHandle runPlayer(Player player, Runnable runnable);

    TaskHandle runSender(CommandSender sender, Runnable runnable);

    TaskHandle runTimerAsync(Runnable runnable, long delayTicks, long periodTicks);

    void broadcast(Component component);

    void shutdown();

    interface TaskHandle {
        void cancel();
    }
}
