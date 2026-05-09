package com.rique.ruinedcollections.listener;

import com.rique.ruinedcollections.RuinedCollectionsPlugin;
import com.rique.ruinedcollections.collection.ProgressMatch;
import com.rique.ruinedcollections.util.Longs;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

public final class ItemCollectionListener implements Listener {
    private final RuinedCollectionsPlugin plugin;
    private final TrackingFilter filter;

    public ItemCollectionListener(RuinedCollectionsPlugin plugin, TrackingFilter filter) {
        this.plugin = plugin;
        this.filter = filter;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player) || filter.blocked(player)) {
            return;
        }
        ItemStack item = event.getItem().getItemStack();
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
        int craftedAmount = craftedAmount(event, result);
        if (craftedAmount <= 0) {
            return;
        }
        for (ProgressMatch match : plugin.collectionRegistry().matchCraft(result, craftedAmount)) {
            plugin.progressService().addProgress(player, match.collection(), match.amount());
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

    private int craftedAmount(CraftItemEvent event, ItemStack result) {
        if (result == null || result.getType().isAir()) {
            return 0;
        }
        if (event.getClick() != ClickType.SHIFT_LEFT && event.getClick() != ClickType.SHIFT_RIGHT) {
            return result.getAmount();
        }
        if (!(event.getInventory() instanceof CraftingInventory inventory)) {
            return result.getAmount();
        }
        int crafts = Integer.MAX_VALUE;
        for (ItemStack ingredient : inventory.getMatrix()) {
            if (ingredient == null || ingredient.getType().isAir()) {
                continue;
            }
            crafts = Math.min(crafts, ingredient.getAmount());
        }
        if (crafts == Integer.MAX_VALUE) {
            return result.getAmount();
        }
        long amount = Longs.multiplyClamped(crafts, result.getAmount());
        return amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;
    }
}
