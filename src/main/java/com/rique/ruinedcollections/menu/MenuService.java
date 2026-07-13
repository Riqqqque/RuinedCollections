package com.rique.ruinedcollections.menu;

import com.rique.ruinedcollections.RuinedCollectionsPlugin;
import com.rique.ruinedcollections.collection.CollectionDefinition;
import com.rique.ruinedcollections.collection.CollectionTier;
import com.rique.ruinedcollections.collection.DisplayItem;
import com.rique.ruinedcollections.diagnostics.DiagnosticService;
import com.rique.ruinedcollections.util.Longs;
import com.rique.ruinedcollections.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
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
        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(file);
            config = loaded;
        } catch (IOException | InvalidConfigurationException exception) {
            config = new YamlConfiguration();
            plugin.diagnostics().warn("menus", "Could not load menu config", DiagnosticService.fields(
                    "file", file.getAbsolutePath()
            ), exception);
        }
    }

    public void openMain(Player player, int page) {
        List<CollectionDefinition> collections = visibleCollections(player);
        int size = validSize(config.getInt("main.size", 54));
        MainMenuHolder holder = new MainMenuHolder(player.getUniqueId(), Math.max(0, page));
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
        if (!canView(player, collection)) {
            player.sendMessage(Text.color(plugin.messagePrefix() + plugin.getConfig().getString("messages.no-permission", "&cYou do not have permission.")));
            return;
        }
        int size = validSize(config.getInt("detail.size", 54));
        int[] contentSlots = contentSlots(size);
        int safePage = Math.max(0, page);
        DetailMenuHolder holder = new DetailMenuHolder(player.getUniqueId(), collection.id(), safePage);
        long progress = plugin.progressService().cachedProgress(player.getUniqueId(), collection.id());
        Map<String, String> titlePlaceholders = collectionPlaceholders(collection, progress);
        String titleFormat = collection.menuTitle().isBlank()
                ? plugin.getConfig().getString("menus.detail-title", "&8%collection%")
                : collection.menuTitle();
        String title = Text.placeholders(titleFormat, titlePlaceholders);
        Inventory inventory = Bukkit.createInventory(holder, size, Text.component(title));
        holder.setInventory(inventory);
        fill(inventory, "detail.filler");

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
            if (!holder.ownerId().equals(player.getUniqueId())) {
                closeForeignMenu(player);
                return;
            }
            int size = top.getSize();
            if (rawSlot == previousSlot(size) && holder.page() > 0) {
                plugin.scheduler().runPlayer(player, () -> openMain(player, holder.page() - 1));
                return;
            }
            if (rawSlot == nextSlot(size) && hasMainNext(player, holder.page(), size)) {
                plugin.scheduler().runPlayer(player, () -> openMain(player, holder.page() + 1));
                return;
            }
            if (rawSlot < 0 || rawSlot >= size) {
                return;
            }
            String collectionId = holder.collectionAt(rawSlot);
            if (collectionId != null) {
                plugin.collectionRegistry().get(collectionId)
                        .filter(collection -> canView(player, collection))
                        .ifPresent(collection -> plugin.scheduler().runPlayer(player, () -> openDetail(player, collection)));
            }
        } else if (top.getHolder() instanceof DetailMenuHolder holder) {
            event.setCancelled(true);
            if (!holder.ownerId().equals(player.getUniqueId())) {
                closeForeignMenu(player);
                return;
            }
            CollectionDefinition collection = plugin.collectionRegistry().get(holder.collectionId())
                    .filter(candidate -> canView(player, candidate))
                    .orElse(null);
            if (collection == null) {
                closeForeignMenu(player);
                return;
            }
            int size = top.getSize();
            if (rawSlot == previousSlot(size) && holder.page() > 0) {
                CollectionDefinition previousCollection = collection;
                plugin.scheduler().runPlayer(player, () -> openDetail(player, previousCollection, holder.page() - 1));
                return;
            }
            if (rawSlot == nextSlot(size) && hasDetailNext(collection, holder.page(), size)) {
                CollectionDefinition nextCollection = collection;
                plugin.scheduler().runPlayer(player, () -> openDetail(player, nextCollection, holder.page() + 1));
                return;
            }
            if (rawSlot == backSlot(size)) {
                plugin.scheduler().runPlayer(player, () -> openMain(player, 0));
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        boolean mainMenu = top.getHolder() instanceof MainMenuHolder;
        boolean detailMenu = top.getHolder() instanceof DetailMenuHolder;
        if (!mainMenu && !detailMenu) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }
        if ((mainMenu && !((MainMenuHolder) top.getHolder()).ownerId().equals(player.getUniqueId()))
                || (detailMenu && !((DetailMenuHolder) top.getHolder()).ownerId().equals(player.getUniqueId()))) {
            event.setCancelled(true);
            closeForeignMenu(player);
            return;
        }
        int topSize = top.getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private void closeForeignMenu(Player player) {
        plugin.scheduler().runPlayer(player, player::closeInventory);
    }

    private ItemStack collectionItem(Player player, CollectionDefinition collection) {
        long progress = plugin.progressService().cachedProgress(player.getUniqueId(), collection.id());
        Map<String, String> placeholders = collectionPlaceholders(collection, progress);

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
            placeholders.put("raw_progress", String.valueOf(progress));
            placeholders.put("remaining", Longs.format(Math.max(0, tier.goal() - progress)));
            placeholders.put("percent", String.valueOf(percent(progress, tier.goal())));
            placeholders.put("progress_bar", progressBar(progress, tier.goal()));
            placeholders.put("collection", Text.color(collection.displayName()));
            placeholders.put("collection_id", collection.id());
            placeholders.put("set", collection.category());
            placeholders.put("category", collection.category());
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

    private boolean hasMainNext(Player player, int page, int size) {
        int nextStart = (page + 1) * contentSlots(size).length;
        return nextStart < visibleCollections(player).size();
    }

    private boolean hasDetailNext(CollectionDefinition collection, int page, int size) {
        int nextStart = (page + 1) * contentSlots(size).length;
        return nextStart < collection.tiers().size();
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

    private List<CollectionDefinition> visibleCollections(Player player) {
        return plugin.collectionRegistry().enabledCollections().stream()
                .filter(collection -> canView(player, collection))
                .toList();
    }

    private boolean canView(Player player, CollectionDefinition collection) {
        long progress = plugin.progressService().cachedProgress(player.getUniqueId(), collection.id());
        return collection.enabled() && collection.visibleTo(player, progress);
    }

    private Map<String, String> collectionPlaceholders(CollectionDefinition collection, long progress) {
        long nextGoal = collection.nextTier(progress).map(CollectionTier::goal).orElse(collection.highestUnlockedGoal(progress));
        long maxGoal = collection.maxGoal();
        int unlocked = collection.unlockedTierCount(progress);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("progress", Longs.format(progress));
        placeholders.put("raw_progress", String.valueOf(progress));
        placeholders.put("next_goal", Longs.format(nextGoal));
        placeholders.put("goal", Longs.format(nextGoal));
        placeholders.put("max_goal", Longs.format(maxGoal));
        placeholders.put("remaining", Longs.format(Math.max(0, nextGoal - progress)));
        placeholders.put("percent", String.valueOf(percent(progress, nextGoal)));
        placeholders.put("max_percent", String.valueOf(percent(Math.min(progress, maxGoal), maxGoal)));
        placeholders.put("progress_bar", progressBar(progress, nextGoal));
        placeholders.put("max_progress_bar", progressBar(Math.min(progress, maxGoal), maxGoal));
        placeholders.put("tier", String.valueOf(unlocked));
        placeholders.put("tier_count", String.valueOf(unlocked));
        placeholders.put("total_tiers", String.valueOf(collection.tiers().size()));
        placeholders.put("maxed", collection.maxed(progress) ? "yes" : "no");
        placeholders.put("collection", Text.color(collection.displayName()));
        placeholders.put("collection_id", collection.id());
        placeholders.put("set", collection.category());
        placeholders.put("category", collection.category());
        return placeholders;
    }

    private int percent(long progress, long goal) {
        if (goal <= 0) {
            return 100;
        }
        return (int) Math.min(100D, Math.max(0D, (progress * 100D) / goal));
    }

    private String progressBar(long progress, long goal) {
        int length = Math.max(1, Math.min(config.getInt("progress-bar.length", 20), 100));
        String filled = config.getString("progress-bar.filled", "&a|");
        String empty = config.getString("progress-bar.empty", "&7|");
        int filledCount = goal <= 0 ? length : (int) Math.floor(Math.min(1D, Math.max(0D, progress / (double) goal)) * length);
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < length; index++) {
            builder.append(index < filledCount ? filled : empty);
        }
        return builder.toString();
    }
}
