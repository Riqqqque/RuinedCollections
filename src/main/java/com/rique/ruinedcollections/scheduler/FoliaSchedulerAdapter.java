package com.rique.ruinedcollections.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;

public final class FoliaSchedulerAdapter implements SchedulerAdapter {
    private final JavaPlugin plugin;

    public FoliaSchedulerAdapter(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public TaskHandle runAsync(Runnable runnable) {
        if (!plugin.isEnabled()) {
            return NoOpTaskHandle.INSTANCE;
        }
        ScheduledTask task = Bukkit.getAsyncScheduler().runNow(plugin, ignored -> runnable.run());
        return task::cancel;
    }

    @Override
    public TaskHandle runGlobal(Runnable runnable) {
        if (!plugin.isEnabled()) {
            return NoOpTaskHandle.INSTANCE;
        }
        ScheduledTask task = Bukkit.getGlobalRegionScheduler().run(plugin, ignored -> runnable.run());
        return task::cancel;
    }

    @Override
    public TaskHandle runPlayer(Player player, Runnable runnable) {
        if (!plugin.isEnabled()) {
            return NoOpTaskHandle.INSTANCE;
        }
        ScheduledTask task = player.getScheduler().run(plugin, ignored -> runnable.run(), null);
        return task::cancel;
    }

    @Override
    public TaskHandle runSender(CommandSender sender, Runnable runnable) {
        if (sender instanceof Player player) {
            return runPlayer(player, runnable);
        }
        return runGlobal(runnable);
    }

    @Override
    public TaskHandle runTimerAsync(Runnable runnable, long delayTicks, long periodTicks) {
        if (!plugin.isEnabled()) {
            return NoOpTaskHandle.INSTANCE;
        }
        ScheduledTask task = Bukkit.getAsyncScheduler().runAtFixedRate(
                plugin,
                ignored -> runnable.run(),
                Math.max(1L, delayTicks * 50L),
                Math.max(1L, periodTicks * 50L),
                TimeUnit.MILLISECONDS
        );
        return task::cancel;
    }

    @Override
    public void broadcast(Component component) {
        if (!plugin.isEnabled()) {
            return;
        }
        Bukkit.getGlobalRegionScheduler().run(plugin, ignored -> {
            Bukkit.getConsoleSender().sendMessage(component);
            for (Player player : Bukkit.getOnlinePlayers()) {
                runPlayer(player, () -> player.sendMessage(component));
            }
        });
    }

    @Override
    public void shutdown() {
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
    }

    private enum NoOpTaskHandle implements TaskHandle {
        INSTANCE;

        @Override
        public void cancel() {
        }
    }
}
