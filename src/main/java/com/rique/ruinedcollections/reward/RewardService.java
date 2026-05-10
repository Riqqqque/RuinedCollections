package com.rique.ruinedcollections.reward;

import com.rique.ruinedcollections.RuinedCollectionsPlugin;
import com.rique.ruinedcollections.collection.CollectionDefinition;
import com.rique.ruinedcollections.collection.CollectionTier;
import com.rique.ruinedcollections.storage.PlayerProgressSession;
import com.rique.ruinedcollections.util.Longs;
import com.rique.ruinedcollections.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public final class RewardService {
    private final RuinedCollectionsPlugin plugin;

    public RewardService(RuinedCollectionsPlugin plugin) {
        this.plugin = plugin;
    }

    public void check(Player player, CollectionDefinition collection, long progress, PlayerProgressSession session) {
        for (CollectionTier tier : collection.tiers()) {
            if (progress < tier.goal() || session.hasClaimed(collection.id(), tier.id())) {
                continue;
            }
            if (!session.startClaim(collection.id(), tier.id())) {
                continue;
            }
            plugin.repository().claimTier(player.getUniqueId(), collection.id(), tier.id()).whenComplete((inserted, throwable) -> {
                if (throwable != null) {
                    session.cancelClaim(collection.id(), tier.id());
                    plugin.getLogger().log(Level.SEVERE, "Could not mark tier as claimed for " + player.getName(), throwable);
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!Boolean.TRUE.equals(inserted)) {
                        session.finishClaim(collection.id(), tier.id());
                        return;
                    }
                    if (!player.isOnline()) {
                        session.cancelClaim(collection.id(), tier.id());
                        plugin.repository().unclaimTier(player.getUniqueId(), collection.id(), tier.id()).exceptionally(exception -> {
                            plugin.getLogger().log(Level.SEVERE, "Could not restore tier claim for offline player " + player.getName(), exception);
                            return null;
                        });
                        return;
                    }
                    session.finishClaim(collection.id(), tier.id());
                    execute(player, collection, tier);
                });
            });
        }
    }

    private void execute(Player player, CollectionDefinition collection, CollectionTier tier) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());
        placeholders.put("uuid", player.getUniqueId().toString());
        placeholders.put("collection", Text.color(collection.displayName()));
        placeholders.put("collection_id", collection.id());
        placeholders.put("tier", tier.id());
        placeholders.put("goal", Longs.format(tier.goal()));

        String unlocked = plugin.getConfig().getString("messages.tier-unlocked", "");
        if (!unlocked.isBlank()) {
            player.sendMessage(Text.component(plugin.messagePrefix() + Text.placeholders(unlocked, placeholders)));
        }

        for (RewardAction reward : tier.rewards()) {
            switch (reward.type()) {
                case MESSAGE -> player.sendMessage(Text.component(plugin.hooks().placeholders(player, Text.placeholders(reward.text(), placeholders))));
                case BROADCAST -> Bukkit.broadcast(Text.component(plugin.hooks().placeholders(player, Text.placeholders(reward.text(), placeholders))));
                case COMMAND -> runCommand(player, reward, placeholders);
                case ECONOMY -> {
                    if (reward.amount() > 0 && !plugin.hooks().economy().deposit(player, reward.amount())) {
                        plugin.getLogger().warning("Economy reward failed for " + player.getName() + " in " + collection.id() + " " + tier.id());
                    }
                }
            }
        }
    }

    private void runCommand(Player player, RewardAction reward, Map<String, String> placeholders) {
        String command = plugin.hooks().placeholders(player, Text.placeholders(reward.command(), placeholders));
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        if ("PLAYER".equalsIgnoreCase(reward.sender())) {
            Bukkit.dispatchCommand(player, command);
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }
}
