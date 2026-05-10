package com.rique.ruinedcollections.menu;

import com.rique.ruinedcollections.RuinedCollectionsPlugin;
import com.rique.ruinedcollections.collection.CollectionDefinition;
import com.rique.ruinedcollections.collection.CollectionTier;
import com.rique.ruinedcollections.collection.DisplayItem;
import com.rique.ruinedcollections.util.Longs;
import com.rique.ruinedcollections.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class MenuService implements Listener {
    private final RuinedCollectionsPlugin plugin;
    private volatile YamlConfiguration config;

    public MenuService(RuinedCollectionsPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "menus/collections.yml");
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void openMain(Player player, int page) {
        List<CollectionDefinition> collections = plugin.collectionRegistry().enabledCollections();
        int size = validSize(config.getInt("main.size", 54));
        MainMenuHolder holder = new MainMenuHolder(Math.max(0, page));
        Inventory inventory = Bukkit.createInventory(holder, size, Text.component(plugin.getConfig().getString("menus.main-title", "&8Collections")));
        holder.setInventory(inventory);
        fill(inventory, "main.filler");

        int[] contentSlots = contentSlots(size);
        int start = holder.page() * contentSlots.length;
        Set<Integer> usedSlots = new HashSet<>();
        for (int index = 0; index < contentSlots.length; index++) {
            int collectionIndex = start + index;
            if (collectionIndex >= collections.size()) {
                break;
            }
            CollectionDefinition collection = collections.get(collectionIndex);
            int slot = collectionSlot(collection.menuSlot(), contentSlots[index], contentSlots, usedSlots, size, holder.page() == 0);
            if (slot < 0) {
                continue;
            }
            usedSlots.add(slot);
            inventory.setItem(slot, collectionItem(player, collection));
            holder.setCollection(slot, collection.id());
        }

        if (holder.page() > 0) {
            inventory.setItem(previousSlot(size), simpleItem(material(plugin.getConfig().getString("menus.previous-item", "ARROW"), Material.ARROW), "&ePrevious"));
        }
        if (start + contentSlots.length < collections.size()) {
            inventory.setItem(nextSlot(size), simpleItem(material(plugin.getConfig().getString("menus.next-item", "ARROW"), Material.ARROW), "&eNext"));
        }
        player.openInventory(inventory);
    }

    public void openDetail(Player player, CollectionDefinition collection) {
        openDetail(player, collection, 0);
    }

    public void openDetail(Player player, CollectionDefinition collection, int page) {
        int size = validSize(config.getInt("detail.size", 54));
        int[] contentSlots = contentSlots(size);
        int safePage = Math.max(0, page);
        DetailMenuHolder holder = new DetailMenuHolder(collection.id(), safePage);
        String title = plugin.getConfig().getString("menus.detail-title", "&8%collection%")
                .replace("%collection%", Text.color(collection.displayName()));
        Inventory inventory = Bukkit.createInventory(holder, size, Text.component(title));
        holder.setInventory(inventory);
        fill(inventory, "detail.filler");

        long progress = plugin.progressService().cachedProgress(player.getUniqueId(), collection.id());
        int start = safePage * contentSlots.length;
        for (int i = 0; i < contentSlots.length; i++) {
            int tierIndex = start + i;
            if (tierIndex >= collection.tiers().size()) {
                break;
            }
            CollectionTier tier = collection.tiers().get(tierIndex);
            inventory.setItem(contentSlots[i], tierItem(player, collection, tier, progress));
        }
        if (safePage > 0) {
            inventory.setItem(previousSlot(size), simpleItem(material(plugin.getConfig().getString("menus.previous-item", "ARROW"), Material.ARROW), "&ePrevious"));
        }
        if (start + contentSlots.length < collection.tiers().size()) {
            inventory.setItem(nextSlot(size), simpleItem(material(plugin.getConfig().getString("menus.next-item", "ARROW"), Material.ARROW), "&eNext"));
        }
        inventory.setItem(backSlot(size), simpleItem(material(plugin.getConfig().getString("menus.back-item", "BARRIER"), Material.BARRIER), "&cBack"));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        int rawSlot = event.getRawSlot();
        if (top.getHolder() instanceof MainMenuHolder holder) {
            event.setCancelled(true);
            int size = top.getSize();
            if (rawSlot == previousSlot(size) && holder.page() > 0) {
                plugin.scheduler().runPlayer(player, () -> openMain(player, holder.page() - 1));
                return;
            }
            if (rawSlot == nextSlot(size) && hasMainNext(holder.page(), size)) {
                plugin.scheduler().runPlayer(player, () -> openMain(player, holder.page() + 1));
                return;
            }
            if (rawSlot < 0 || rawSlot >= size) {
                return;
            }
            String collectionId = holder.collectionAt(rawSlot);
            if (collectionId != null) {
                plugin.collectionRegistry().get(collectionId)
                        .ifPresent(collection -> plugin.scheduler().runPlayer(player, () -> openDetail(player, collection)));
            }
        } else if (top.getHolder() instanceof DetailMenuHolder holder) {
            event.setCancelled(true);
            int size = top.getSize();
            if (rawSlot == previousSlot(size) && holder.page() > 0) {
                plugin.collectionRegistry().get(holder.collectionId())
                        .ifPresent(collection -> plugin.scheduler().runPlayer(player, () -> openDetail(player, collection, holder.page() - 1)));
                return;
            }
            if (rawSlot == nextSlot(size) && hasDetailNext(holder, size)) {
                plugin.collectionRegistry().get(holder.collectionId())
                        .ifPresent(collection -> plugin.scheduler().runPlayer(player, () -> openDetail(player, collection, holder.page() + 1)));
                return;
            }
            if (rawSlot == backSlot(size)) {
                plugin.scheduler().runPlayer(player, () -> openMain(player, 0));
            }
        }
    }

    private ItemStack collectionItem(Player player, CollectionDefinition collection) {
        long progress = plugin.progressService().cachedProgress(player.getUniqueId(), collection.id());
        long nextGoal = collection.nextTier(progress).map(CollectionTier::goal).orElse(collection.highestUnlockedGoal(progress));
        int unlocked = 0;
        for (CollectionTier tier : collection.tiers()) {
            if (progress >= tier.goal()) {
                unlocked++;
            }
        }
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("progress", Longs.format(progress));
        placeholders.put("next_goal", Longs.format(nextGoal));
        placeholders.put("tier", String.valueOf(unlocked));
        placeholders.put("collection", Text.color(collection.displayName()));
        placeholders.put("collection_id", collection.id());

        ItemStack item = displayItem(collection.displayItem());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.component(plugin.hooks().placeholders(player, Text.placeholders(collection.displayName(), placeholders))));
            List<Component> lore = new ArrayList<>();
            for (String line : collection.description()) {
                lore.add(Text.component(plugin.hooks().placeholders(player, Text.placeholders(line, placeholders))));
            }
            for (String line : config.getStringList("main.collection-lore")) {
                lore.add(Text.component(plugin.hooks().placeholders(player, Text.placeholders(line, placeholders))));
            }
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack tierItem(Player player, CollectionDefinition collection, CollectionTier tier, long progress) {
        boolean unlocked = progress >= tier.goal();
        Material material = unlocked ? Material.LIME_DYE : Material.GRAY_DYE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.component((unlocked ? "&a" : "&c") + collection.displayName() + " " + tier.id()));
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("goal", Longs.format(tier.goal()));
            placeholders.put("progress", Longs.format(progress));
            placeholders.put("collection", Text.color(collection.displayName()));
            placeholders.put("tier", tier.id());
            List<Component> lore = config.getStringList(unlocked ? "detail.tier-lore.unlocked" : "detail.tier-lore.locked").stream()
                    .map(line -> Text.component(plugin.hooks().placeholders(player, Text.placeholders(line, placeholders))))
                    .toList();
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    @SuppressWarnings("deprecation")
    private ItemStack displayItem(DisplayItem displayItem) {
        ItemStack item = new ItemStack(displayItem.material());
        if (displayItem.customModelData() != null) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(displayItem.customModelData());
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    private void fill(Inventory inventory, String path) {
        if (!config.getBoolean(path + ".enabled", false)) {
            return;
        }
        ItemStack filler = simpleItem(material(config.getString(path + ".material", "BLACK_STAINED_GLASS_PANE"), Material.BLACK_STAINED_GLASS_PANE), config.getString(path + ".name", " "));
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private ItemStack simpleItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.component(name));
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    private Material material(String name, Material fallback) {
        Optional<Material> material = Optional.ofNullable(Material.matchMaterial(name == null ? "" : name));
        return material.orElse(fallback);
    }

    private int validSize(int size) {
        if (size < 27) {
            return 27;
        }
        if (size > 54) {
            return 54;
        }
        return (size / 9) * 9;
    }

    private int[] contentSlots(int size) {
        int rows = size / 9;
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row < rows - 1; row++) {
            for (int column = 1; column <= 7; column++) {
                slots.add(row * 9 + column);
            }
        }
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }

    private boolean usableCollectionSlot(int slot, int size) {
        return slot >= 0 && slot < size && !isControlSlot(slot, size);
    }

    private int collectionSlot(int configuredSlot, int defaultSlot, int[] contentSlots, Set<Integer> usedSlots, int size, boolean allowConfiguredSlot) {
        if (allowConfiguredSlot && usableCollectionSlot(configuredSlot, size) && !usedSlots.contains(configuredSlot)) {
            return configuredSlot;
        }
        if (!usedSlots.contains(defaultSlot)) {
            return defaultSlot;
        }
        for (int slot : contentSlots) {
            if (!usedSlots.contains(slot)) {
                return slot;
            }
        }
        return -1;
    }

    private boolean isControlSlot(int slot, int size) {
        return slot == previousSlot(size) || slot == backSlot(size) || slot == nextSlot(size);
    }

    private boolean hasMainNext(int page, int size) {
        int nextStart = (page + 1) * contentSlots(size).length;
        return nextStart < plugin.collectionRegistry().enabledCollections().size();
    }

    private boolean hasDetailNext(DetailMenuHolder holder, int size) {
        int nextStart = (holder.page() + 1) * contentSlots(size).length;
        return plugin.collectionRegistry().get(holder.collectionId())
                .map(collection -> nextStart < collection.tiers().size())
                .orElse(false);
    }

    private int previousSlot(int size) {
        return size - 9;
    }

    private int backSlot(int size) {
        return size - 5;
    }

    private int nextSlot(int size) {
        return size - 1;
    }
}
