package com.rique.ruinedcollections.reward;

import com.rique.ruinedcollections.RuinedCollectionsPlugin;
import com.rique.ruinedcollections.collection.CollectionDefinition;
import com.rique.ruinedcollections.collection.CollectionTier;
import com.rique.ruinedcollections.diagnostics.DiagnosticService;
import com.rique.ruinedcollections.storage.PlayerProgressSession;
import com.rique.ruinedcollections.util.Longs;
import com.rique.ruinedcollections.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public final class RewardService {
    private final RuinedCollectionsPlugin plugin;

    public RewardService(RuinedCollectionsPlugin plugin) {
        this.plugin = plugin;
    }

    public void check(Player player, CollectionDefinition collection, long progress, PlayerProgressSession session) {
        String playerName = player.getName();
        String playerId = player.getUniqueId().toString();
        for (CollectionTier tier : collection.tiers()) {
            if (progress < tier.goal() || session.hasClaimed(collection.id(), tier.id())) {
                continue;
            }
            if (!session.startClaim(collection.id(), tier.id())) {
                plugin.diagnostics().debug("rewards", "Skipped reward claim because tier is already claiming", DiagnosticService.fields(
                        "player", playerName,
                        "uuid", playerId,
                        "collection", collection.id(),
                        "tier", tier.id()
                ));
                continue;
            }
            plugin.repository().claimTier(player.getUniqueId(), collection.id(), tier.id()).whenComplete((inserted, throwable) -> {
                if (throwable != null) {
                    session.cancelClaim(collection.id(), tier.id());
                    plugin.diagnostics().error("rewards", "Could not mark tier as claimed", DiagnosticService.fields(
                            "player", playerName,
                            "uuid", playerId,
                            "collection", collection.id(),
                            "tier", tier.id()
                    ), throwable);
                    return;
                }
                plugin.scheduler().runPlayer(player, () -> {
                    if (!Boolean.TRUE.equals(inserted)) {
                        session.finishClaim(collection.id(), tier.id());
                        plugin.diagnostics().debug("rewards", "Skipped reward execution because tier was already claimed in storage", DiagnosticService.fields(
                                "player", player.getName(),
                                "uuid", player.getUniqueId(),
                                "collection", collection.id(),
                                "tier", tier.id()
                        ));
                        return;
                    }
                    if (!player.isOnline()) {
                        session.cancelClaim(collection.id(), tier.id());
                        plugin.diagnostics().warn("rewards", "Player left before reward execution; restored claim for retry", DiagnosticService.fields(
                                "player", player.getName(),
                                "uuid", player.getUniqueId(),
                                "collection", collection.id(),
                                "tier", tier.id()
                        ));
                        plugin.repository().unclaimTier(player.getUniqueId(), collection.id(), tier.id()).exceptionally(exception -> {
                            plugin.diagnostics().error("rewards", "Could not restore tier claim for offline player", DiagnosticService.fields(
                                    "player", player.getName(),
                                    "uuid", player.getUniqueId(),
                                    "collection", collection.id(),
                                    "tier", tier.id()
                            ), exception);
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
                case BROADCAST -> plugin.scheduler().broadcast(Text.component(plugin.hooks().placeholders(player, Text.placeholders(reward.text(), placeholders))));
                case COMMAND -> runCommand(player, reward, placeholders);
                case ECONOMY -> {
                    if (reward.amount() > 0 && !plugin.hooks().economy().deposit(player, reward.amount())) {
                        plugin.diagnostics().warn("rewards", "Economy reward failed", DiagnosticService.fields(
                                "player", player.getName(),
                                "uuid", player.getUniqueId(),
                                "collection", collection.id(),
                                "tier", tier.id(),
                                "amount", reward.amount()
                        ));
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
        if (command.isBlank()) {
            plugin.diagnostics().warn("rewards", "Skipped empty command reward", DiagnosticService.fields(
                    "player", player.getName(),
                    "uuid", player.getUniqueId(),
                    "sender", reward.sender()
            ));
            return;
        }
        boolean dispatched;
        if ("PLAYER".equalsIgnoreCase(reward.sender())) {
            dispatched = Bukkit.dispatchCommand(player, command);
        } else {
            dispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
        if (!dispatched) {
            plugin.diagnostics().warn("rewards", "Reward command was not handled", DiagnosticService.fields(
                    "player", player.getName(),
                    "uuid", player.getUniqueId(),
                    "sender", reward.sender(),
                    "command", command
            ));
        }
    }
}
