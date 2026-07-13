package com.rique.ruinedcollections.listener;

import com.rique.ruinedcollections.RuinedCollectionsPlugin;
import com.rique.ruinedcollections.collection.ProgressMatch;
import com.rique.ruinedcollections.diagnostics.DiagnosticService;
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemCollectionListener implements Listener {
    private final RuinedCollectionsPlugin plugin;
    private final TrackingFilter filter;
    private final Map<UUID, PendingCrafts> pendingCrafts = new ConcurrentHashMap<>();

    public ItemCollectionListener(RuinedCollectionsPlugin plugin, TrackingFilter filter) {
        this.plugin = plugin;
        this.filter = filter;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player) || filter.blocked(player)) {
            return;
        }
        ItemStack item = event.getItem().getItemStack().clone();
        int pickedUp = item.getAmount() - event.getRemaining();
        if (pickedUp <= 0) {
            plugin.diagnostics().debug("tracking", "Skipped item pickup because no items were collected", DiagnosticService.fields(
                    "player", player.getName(),
                    "uuid", player.getUniqueId(),
                    "material", item.getType().name(),
                    "stackAmount", item.getAmount(),
                    "remaining", event.getRemaining()
            ));
            return;
        }
        item.setAmount(pickedUp);
        for (ProgressMatch match : plugin.collectionRegistry().matchItemPickup(item)) {
            plugin.progressService().addProgress(player, match.collection(), match.amount());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || filter.blocked(player)) {
            return;
        }
        ItemStack result = event.getRecipe().getResult();
        if (empty(result)) {
            return;
        }
        ItemStack sample = result.clone();
        int before = countSimilar(player, sample);
        UUID playerId = player.getUniqueId();
        PendingCrafts pending = pendingCrafts.computeIfAbsent(playerId, ignored -> new PendingCrafts());
        pending.record(sample, before);
        if (!pending.schedule()) {
            return;
        }
        plugin.scheduler().runPlayer(player, () -> flushCrafts(player, playerId, pending), () -> pendingCrafts.remove(playerId, pending));
    }

    private void flushCrafts(Player player, UUID playerId, PendingCrafts pending) {
        List<PendingCraft> crafts = pending.drain();
        pendingCrafts.remove(playerId, pending);
        if (!player.isOnline() || filter.blocked(player)) {
            return;
        }
        for (PendingCraft craft : crafts) {
            int craftedAmount = Math.max(0, countSimilar(player, craft.sample()) - craft.before());
            if (craftedAmount <= 0) {
                plugin.diagnostics().debug("tracking", "Skipped craft because no crafted items reached the player inventory", DiagnosticService.fields(
                        "player", player.getName(),
                        "uuid", player.getUniqueId(),
                        "material", craft.sample().getType().name()
                ));
                continue;
            }
            for (ProgressMatch match : plugin.collectionRegistry().matchCraft(craft.sample(), craftedAmount)) {
                plugin.progressService().addProgress(player, match.collection(), match.amount());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || filter.blocked(event.getPlayer())) {
            return;
        }
        if (!(event.getCaught() instanceof Item item)) {
            return;
        }
        ItemStack stack = item.getItemStack();
        for (ProgressMatch match : plugin.collectionRegistry().matchFish(stack)) {
            plugin.progressService().addProgress(event.getPlayer(), match.collection(), match.amount());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArmorChange(PlayerArmorChangeEvent event) {
        Player player = event.getPlayer();
        if (filter.blocked(player)) {
            return;
        }
        ItemStack newItem = event.getNewItem();
        if (empty(newItem) || sameEquippedItem(event.getOldItem(), newItem)) {
            return;
        }
        for (ProgressMatch match : plugin.collectionRegistry().matchArmorEquip(newItem)) {
            plugin.progressService().addProgress(player, match.collection(), match.amount());
        }
    }

    private int countSimilar(Player player, ItemStack sample) {
        int amount = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (similar(item, sample)) {
                amount += item.getAmount();
            }
        }
        ItemStack cursor = player.getItemOnCursor();
        if (similar(cursor, sample)) {
            amount += cursor.getAmount();
        }
        return amount;
    }

    private boolean similar(ItemStack first, ItemStack second) {
        return !empty(first) && !empty(second) && first.isSimilar(second);
    }

    private boolean sameEquippedItem(ItemStack first, ItemStack second) {
        if (similar(first, second)) {
            return true;
        }
        if (empty(first) || empty(second)) {
            return false;
        }
        return withoutDamage(first).isSimilar(withoutDamage(second));
    }

    private ItemStack withoutDamage(ItemStack item) {
        ItemStack clone = item.clone();
        ItemMeta meta = clone.getItemMeta();
        if (meta instanceof Damageable damageable) {
            damageable.setDamage(0);
            clone.setItemMeta(meta);
        }
        return clone;
    }

    private boolean empty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private static final class PendingCrafts {
        private final List<PendingCraft> crafts = new ArrayList<>();
        private boolean scheduled;

        private synchronized void record(ItemStack sample, int before) {
            for (int index = 0; index < crafts.size(); index++) {
                PendingCraft craft = crafts.get(index);
                if (craft.sample().isSimilar(sample)) {
                    crafts.set(index, new PendingCraft(craft.sample(), Math.min(craft.before(), before)));
                    return;
                }
            }
            crafts.add(new PendingCraft(sample, before));
        }

        private synchronized boolean schedule() {
            if (scheduled) {
                return false;
            }
            scheduled = true;
            return true;
        }

        private synchronized List<PendingCraft> drain() {
            List<PendingCraft> captured = List.copyOf(crafts);
            crafts.clear();
            scheduled = false;
            return captured;
        }
    }

    private record PendingCraft(ItemStack sample, int before) {
    }
}
