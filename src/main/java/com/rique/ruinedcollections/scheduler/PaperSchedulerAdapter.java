package com.rique.ruinedcollections.scheduler;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class PaperSchedulerAdapter implements SchedulerAdapter {
    private final JavaPlugin plugin;

    public PaperSchedulerAdapter(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public TaskHandle runAsync(Runnable runnable) {
        if (!plugin.isEnabled()) {
            return NoOpTaskHandle.INSTANCE;
        }
        BukkitTask task = Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
        return task::cancel;
    }

    @Override
    public TaskHandle runGlobal(Runnable runnable) {
        if (!plugin.isEnabled()) {
            return NoOpTaskHandle.INSTANCE;
        }
        BukkitTask task = Bukkit.getScheduler().runTask(plugin, runnable);
        return task::cancel;
    }

    @Override
    public TaskHandle runPlayer(Player player, Runnable runnable) {
        if (!plugin.isEnabled()) {
            return NoOpTaskHandle.INSTANCE;
        }
        BukkitTask task = Bukkit.getScheduler().runTask(plugin, runnable);
        return task::cancel;
    }

    @Override
    public TaskHandle runSender(CommandSender sender, Runnable runnable) {
        return runGlobal(runnable);
    }

    @Override
    public TaskHandle runTimerAsync(Runnable runnable, long delayTicks, long periodTicks) {
        if (!plugin.isEnabled()) {
            return NoOpTaskHandle.INSTANCE;
        }
        BukkitTask task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, delayTicks, periodTicks);
        return task::cancel;
    }

    @Override
    public void broadcast(Component component) {
        if (!plugin.isEnabled()) {
            return;
        }
        Bukkit.broadcast(component);
    }

    @Override
    public void shutdown() {
        Bukkit.getScheduler().cancelTasks(plugin);
    }

    private enum NoOpTaskHandle implements TaskHandle {
        INSTANCE;

        @Override
        public void cancel() {
        }
    }
}
