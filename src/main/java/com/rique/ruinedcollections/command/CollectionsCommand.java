package com.rique.ruinedcollections.command;

import com.rique.ruinedcollections.RuinedCollectionsPlugin;
import com.rique.ruinedcollections.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class CollectionsCommand implements CommandExecutor {
    private final RuinedCollectionsPlugin plugin;

    public CollectionsCommand(RuinedCollectionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.color(plugin.messagePrefix() + plugin.getConfig().getString("messages.player-only")));
            return true;
        }
        if (!player.hasPermission("ruinedcollections.menu")) {
            player.sendMessage(Text.color(plugin.messagePrefix() + plugin.getConfig().getString("messages.no-permission")));
            return true;
        }
        plugin.scheduler().runPlayer(player, () -> plugin.menuService().openMain(player, 0));
        return true;
    }
}
