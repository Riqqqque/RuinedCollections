package com.rique.ruinedcollections.menu;

import com.rique.ruinedcollections.RuinedCollectionsPlugin;
import com.rique.ruinedcollections.collection.CollectionDefinition;
import com.rique.ruinedcollections.collection.CollectionTier;
import com.rique.ruinedcollections.collection.DisplayItem;
import com.rique.ruinedcollections.util.Longs;
import com.rique.ruinedcollections.util.Text;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MenuService implements Listener {
    private static final int PREVIOUS_SLOT = 45;
    private static final int BACK_SLOT = 49;
    private static final int NEXT_SLOT = 53;
    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final RuinedCollectionsPlugin plugin;
    private YamlConfiguration config;

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
        Inventory inventory = Bukkit.createInventory(holder, size, Text.color(plugin.getConfig().getString("menus.main-title", "&8Collections")));
        holder.setInventory(inventory);
        fill(inventory, "main.filler");

        int start = holder.page() * CONTENT_SLOTS.length;
        for (int index = 0; index < CONTENT_SLOTS.length; index++) {
            int collectionIndex = start + index;
            if (collectionIndex >= collections.size()) {
                break;
            }
            CollectionDefinition collection = collections.get(collectionIndex);
            int slot = collection.menuSlot() >= 0 && collection.menuSlot() < size && holder.page() == 0
                    ? collection.menuSlot()
                    : CONTENT_SLOTS[index];
            inventory.setItem(slot, collectionItem(player, collection));
            holder.setCollection(slot, collection.id());
        }

        if (holder.page() > 0) {
            inventory.setItem(PREVIOUS_SLOT, simpleItem(material(plugin.getConfig().getString("menus.previous-item", "ARROW"), Material.ARROW), "&ePrevious"));
        }
        if (start + CONTENT_SLOTS.length < collections.size()) {
            inventory.setItem(NEXT_SLOT, simpleItem(material(plugin.getConfig().getString("menus.next-item", "ARROW"), Material.ARROW), "&eNext"));
        }
        player.openInventory(inventory);
    }

    public void openDetail(Player player, CollectionDefinition collection) {
        int size = validSize(config.getInt("detail.size", 54));
        DetailMenuHolder holder = new DetailMenuHolder(collection.id());
        String title = plugin.getConfig().getString("menus.detail-title", "&8%collection%")
                .replace("%collection%", Text.color(collection.displayName()));
        Inventory inventory = Bukkit.createInventory(holder, size, Text.color(title));
        holder.setInventory(inventory);
        fill(inventory, "detail.filler");

        long progress = plugin.progressService().cachedProgress(player.getUniqueId(), collection.id());
        for (int i = 0; i < collection.tiers().size() && i < CONTENT_SLOTS.length; i++) {
            CollectionTier tier = collection.tiers().get(i);
            inventory.setItem(CONTENT_SLOTS[i], tierItem(player, collection, tier, progress));
        }
        inventory.setItem(BACK_SLOT, simpleItem(material(plugin.getConfig().getString("menus.back-item", "BARRIER"), Material.BARRIER), "&cBack"));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof MainMenuHolder holder) {
            event.setCancelled(true);
            if (event.getRawSlot() == PREVIOUS_SLOT && holder.page() > 0) {
                openMain(player, holder.page() - 1);
                return;
            }
            if (event.getRawSlot() == NEXT_SLOT) {
                openMain(player, holder.page() + 1);
                return;
            }
            String collectionId = holder.collectionAt(event.getRawSlot());
            if (collectionId != null) {
                plugin.collectionRegistry().get(collectionId).ifPresent(collection -> openDetail(player, collection));
            }
        } else if (event.getInventory().getHolder() instanceof DetailMenuHolder) {
            event.setCancelled(true);
            if (event.getRawSlot() == BACK_SLOT) {
                openMain(player, 0);
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
            meta.setDisplayName(Text.color(plugin.hooks().placeholders(player, Text.placeholders(collection.displayName(), placeholders))));
            List<String> lore = new ArrayList<>();
            for (String line : collection.description()) {
                lore.add(Text.color(plugin.hooks().placeholders(player, Text.placeholders(line, placeholders))));
            }
            for (String line : config.getStringList("main.collection-lore")) {
                lore.add(Text.color(plugin.hooks().placeholders(player, Text.placeholders(line, placeholders))));
            }
            meta.setLore(lore);
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
            meta.setDisplayName(Text.color((unlocked ? "&a" : "&c") + collection.displayName() + " " + tier.id()));
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("goal", Longs.format(tier.goal()));
            placeholders.put("progress", Longs.format(progress));
            placeholders.put("collection", Text.color(collection.displayName()));
            placeholders.put("tier", tier.id());
            List<String> lore = config.getStringList(unlocked ? "detail.tier-lore.unlocked" : "detail.tier-lore.locked").stream()
                    .map(line -> Text.color(plugin.hooks().placeholders(player, Text.placeholders(line, placeholders))))
                    .toList();
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

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
            meta.setDisplayName(Text.color(name));
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
        if (size < 9) {
            return 9;
        }
        if (size > 54) {
            return 54;
        }
        return (size / 9) * 9;
    }
}
