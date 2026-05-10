package com.rique.ruinedcollections.collection;

import com.rique.ruinedcollections.RuinedCollectionsPlugin;
import com.rique.ruinedcollections.diagnostics.DiagnosticService;
import com.rique.ruinedcollections.reward.RewardAction;
import com.rique.ruinedcollections.reward.RewardType;
import com.rique.ruinedcollections.util.Longs;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class CollectionRegistry {
    private static final String ID_PATTERN = "[a-z0-9_-]+";
    private static final String TIER_ID_PATTERN = "[A-Za-z0-9_-]+";
    private static final int MAX_ID_LENGTH = 64;

    private final RuinedCollectionsPlugin plugin;
    private final File collectionsFolder;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<String, CollectionDefinition> collections = new LinkedHashMap<>();
    private final Map<String, File> filesById = new HashMap<>();

    private final Map<Material, List<TrackedSource>> blockBreak = new HashMap<>();
    private final List<TrackedSource> blockBreakAny = new ArrayList<>();
    private final Map<EntityType, List<TrackedSource>> entityKill = new HashMap<>();
    private final List<TrackedSource> entityKillAny = new ArrayList<>();
    private final Map<Material, List<TrackedSource>> itemPickup = new HashMap<>();
    private final List<TrackedSource> itemPickupAny = new ArrayList<>();
    private final Map<Material, List<TrackedSource>> craft = new HashMap<>();
    private final List<TrackedSource> craftAny = new ArrayList<>();
    private final Map<Material, List<TrackedSource>> fish = new HashMap<>();
    private final List<TrackedSource> fishAny = new ArrayList<>();

    public CollectionRegistry(RuinedCollectionsPlugin plugin) {
        this.plugin = plugin;
        this.collectionsFolder = new File(plugin.getDataFolder(), "collections");
    }

    public void ensureDefaults() {
        saveDefault("collections/oak_log.yml");
        saveDefault("collections/obsidian.yml");
        saveDefault("collections/template_custom_item.yml");
        saveDefault("menus/collections.yml");
    }

    public List<String> load() {
        lock.writeLock().lock();
        try {
            collections.clear();
            filesById.clear();
            clearIndexes();
            if (!collectionsFolder.exists() && !collectionsFolder.mkdirs()) {
                return List.of("Could not create collections folder.");
            }

            List<String> issues = new ArrayList<>();
            File[] files = collectionsFolder.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
            if (files == null) {
                return List.of("Could not list collection files.");
            }
            Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File file : files) {
                try {
                    parseFile(file, issues).ifPresent(collection -> {
                        collections.put(collection.id(), collection);
                        filesById.put(collection.id(), file);
                    });
                } catch (RuntimeException exception) {
                    issues.add(file.getName() + ": " + exception.getMessage());
                }
            }
            buildIndexes();
            validateMenuSlots(issues);
            for (String issue : issues) {
                plugin.diagnostics().warn("collections", issue);
            }
            return issues;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Collection<CollectionDefinition> all() {
        lock.readLock().lock();
        try {
            return List.copyOf(collections.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<CollectionDefinition> enabledCollections() {
        lock.readLock().lock();
        try {
            return collections.values().stream()
                    .filter(CollectionDefinition::enabled)
                    .sorted(Comparator.comparingInt(CollectionDefinition::menuSlot).thenComparing(CollectionDefinition::id))
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<CollectionDefinition> get(String id) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(collections.get(normalizeId(id)));
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean isTrackedBlockMaterial(Material material) {
        lock.readLock().lock();
        try {
            return blockBreak.containsKey(material) || !blockBreakAny.isEmpty();
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<ProgressMatch> matchBlockBreak(Material material) {
        lock.readLock().lock();
        try {
            return matchMaterial(material, blockBreak, blockBreakAny, 1);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<ProgressMatch> matchEntityKill(EntityType entityType) {
        lock.readLock().lock();
        try {
            Map<String, Long> matches = new LinkedHashMap<>();
            for (TrackedSource trackedSource : entityKillAny) {
                add(matches, trackedSource.collection.id(), trackedSource.rule.amount());
            }
            for (TrackedSource trackedSource : entityKill.getOrDefault(entityType, List.of())) {
                add(matches, trackedSource.collection.id(), trackedSource.rule.amount());
            }
            return toMatches(matches);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<ProgressMatch> matchItemPickup(ItemStack item) {
        lock.readLock().lock();
        try {
            return matchItem(item, itemPickup, itemPickupAny, item == null ? 0 : item.getAmount());
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<ProgressMatch> matchCraft(ItemStack item, int craftedAmount) {
        lock.readLock().lock();
        try {
            return matchItem(item, craft, craftAny, craftedAmount);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<ProgressMatch> matchFish(ItemStack item) {
        lock.readLock().lock();
        try {
            return matchItem(item, fish, fishAny, item == null ? 0 : item.getAmount());
        } finally {
            lock.readLock().unlock();
        }
    }

    public File fileFor(String id) {
        lock.readLock().lock();
        try {
            return filesById.get(normalizeId(id));
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean createCollection(String id, Material material, String displayName) throws IOException {
        if (material == null || material.isAir()) {
            return false;
        }
        String normalized = normalizeId(id);
        lock.writeLock().lock();
        try {
            if (!validId(normalized) || collections.containsKey(normalized)) {
                plugin.diagnostics().debug("commands", "Create collection rejected", DiagnosticService.fields(
                        "collection", normalized,
                        "reason", collections.containsKey(normalized) ? "duplicate" : "invalid_id"
                ));
                return false;
            }
            File file = new File(collectionsFolder, normalized + ".yml");
            if (file.exists()) {
                plugin.diagnostics().debug("commands", "Create collection rejected", DiagnosticService.fields(
                        "collection", normalized,
                        "reason", "file_exists"
                ));
                return false;
            }
            YamlConfiguration config = new YamlConfiguration();
            config.set("id", normalized);
            config.set("enabled", true);
            config.set("display-name", displayName == null || displayName.isBlank() ? title(normalized) : displayName);
            config.set("description", List.of("&7New collection."));
            config.set("display-item.material", material.name());
            config.set("menu-slot", -1);
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("type", "BLOCK_BREAK");
            source.put("materials", List.of(material.name()));
            config.set("sources", List.of(source));
            config.set("tiers", List.of(tierMap("I", 50), tierMap("II", 100), tierMap("III", 500)));
            config.save(file);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean deleteCollection(String id) throws IOException {
        lock.writeLock().lock();
        try {
            File file = filesById.get(normalizeId(id));
            if (file == null || !file.exists()) {
                plugin.diagnostics().debug("commands", "Delete collection rejected", DiagnosticService.fields("collection", id, "reason", "unknown_collection"));
                return false;
            }
            File deletedFolder = new File(collectionsFolder, "deleted");
            if (!deletedFolder.exists() && !deletedFolder.mkdirs()) {
                throw new IOException("Could not create deleted folder.");
            }
            File target = new File(deletedFolder, file.getName() + "." + System.currentTimeMillis() + ".bak");
            if (!file.renameTo(target)) {
                throw new IOException("Could not move collection to " + target.getName());
            }
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean addTier(String collectionId, String tierId, long goal) throws IOException {
        lock.writeLock().lock();
        try {
            File file = filesById.get(normalizeId(collectionId));
            if (file == null || !validTierId(tierId) || goal <= 0) {
                return false;
            }
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            List<Map<?, ?>> tiers = new ArrayList<>(config.getMapList("tiers"));
            long highestGoal = 0;
            for (Map<?, ?> tier : tiers) {
                if (tierId.equalsIgnoreCase(String.valueOf(tier.get("id")))) {
                    return false;
                }
                highestGoal = Math.max(highestGoal, longValue(tier.get("goal"), 0));
            }
            if (goal <= highestGoal) {
                return false;
            }
            tiers.add(tierMap(tierId, goal));
            config.set("tiers", tiers);
            config.save(file);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean addSource(String collectionId, CollectionSourceType type, String key) throws IOException {
        lock.writeLock().lock();
        try {
            if (type == null) {
                return false;
            }
            String safeKey = key == null ? "" : key.trim();
            if (type != CollectionSourceType.MANUAL && safeKey.isBlank()) {
                return false;
            }
            File file = filesById.get(normalizeId(collectionId));
            if (file == null) {
                return false;
            }
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            List<Map<?, ?>> sources = new ArrayList<>(config.getMapList("sources"));
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("type", type.name());
            if (type == CollectionSourceType.ENTITY_KILL) {
                source.put("entities", List.of(safeKey.toUpperCase(Locale.ROOT)));
            } else if (type != CollectionSourceType.MANUAL) {
                source.put("materials", List.of(safeKey.toUpperCase(Locale.ROOT)));
            }
            sources.add(source);
            config.set("sources", sources);
            config.save(file);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean addCommandReward(String collectionId, String tierId, String sender, String command) throws IOException {
        lock.writeLock().lock();
        try {
            if (sender == null || command == null || command.isBlank()) {
                return false;
            }
            File file = filesById.get(normalizeId(collectionId));
            if (file == null) {
                return false;
            }
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            List<Map<?, ?>> tiers = new ArrayList<>(config.getMapList("tiers"));
            boolean changed = false;
            for (int index = 0; index < tiers.size(); index++) {
                Map<?, ?> rawTier = tiers.get(index);
                Map<String, Object> tier = mutable(rawTier);
                if (!tierId.equalsIgnoreCase(String.valueOf(tier.get("id")))) {
                    continue;
                }
                List<Map<?, ?>> rewards = new ArrayList<>();
                Object existing = tier.get("rewards");
                if (existing instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> map) {
                            rewards.add(map);
                        }
                    }
                }
                Map<String, Object> reward = new LinkedHashMap<>();
                reward.put("type", RewardType.COMMAND.name());
                reward.put("sender", sender.toUpperCase(Locale.ROOT));
                reward.put("command", command);
                rewards.add(reward);
                tier.put("rewards", rewards);
                tiers.set(index, tier);
                changed = true;
                break;
            }
            if (!changed) {
                return false;
            }
            config.set("tiers", tiers);
            config.save(file);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private Optional<CollectionDefinition> parseFile(File file, List<String> issues) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String id = normalizeId(config.getString("id", file.getName().replaceFirst("\\.ya?ml$", "")));
        if (!validId(id)) {
            issues.add(file.getName() + ": invalid id '" + id + "'. Use 1-64 lowercase letters, numbers, _ or -.");
            return Optional.empty();
        }
        if (collections.containsKey(id)) {
            issues.add(file.getName() + ": duplicate collection id '" + id + "'.");
            return Optional.empty();
        }

        Material displayMaterial = material(config.getString("display-item.material", "CHEST"), file, issues).orElse(Material.CHEST);
        Integer customModelData = config.isInt("display-item.custom-model-data") ? config.getInt("display-item.custom-model-data") : null;

        List<SourceRule> sources = new ArrayList<>();
        for (Map<?, ?> sourceMap : config.getMapList("sources")) {
            parseSource(sourceMap, file, issues).ifPresent(sources::add);
        }
        if (sources.isEmpty()) {
            issues.add(file.getName() + ": no valid sources found.");
        }

        List<CollectionTier> tiers = new ArrayList<>();
        Set<String> tierIds = new HashSet<>();
        long previousGoal = 0;
        for (Map<?, ?> tierMap : config.getMapList("tiers")) {
            String tierId = string(tierMap, "id", "").trim();
            long goal = longValue(tierMap.get("goal"), -1);
            if (!validTierId(tierId) || goal <= 0) {
                issues.add(file.getName() + ": skipped tier with invalid id or goal.");
                continue;
            }
            if (!tierIds.add(tierId.toLowerCase(Locale.ROOT))) {
                issues.add(file.getName() + ": duplicate tier id '" + tierId + "'.");
                continue;
            }
            if (goal <= previousGoal) {
                issues.add(file.getName() + ": tier '" + tierId + "' goal is not higher than the previous tier.");
            }
            previousGoal = goal;
            tiers.add(new CollectionTier(tierId, goal, parseRewards(tierMap, file, issues)));
        }
        if (tiers.isEmpty()) {
            issues.add(file.getName() + ": no valid tiers found.");
        }

        return Optional.of(new CollectionDefinition(
                id,
                config.getBoolean("enabled", true),
                config.getString("display-name", title(id)),
                config.getStringList("description"),
                new DisplayItem(displayMaterial, customModelData),
                config.getInt("menu-slot", -1),
                sources,
                tiers,
                file
        ));
    }

    private void validateMenuSlots(List<String> issues) {
        Map<Integer, String> usedSlots = new HashMap<>();
        for (CollectionDefinition collection : collections.values()) {
            if (!collection.enabled() || collection.menuSlot() < 0) {
                continue;
            }
            if (collection.menuSlot() > 53) {
                issues.add("Collection '" + collection.id() + "' uses menu-slot " + collection.menuSlot()
                        + ", but inventory slots must be 0-53.");
            }
            if (collection.menuSlot() == 45 || collection.menuSlot() == 49 || collection.menuSlot() == 53) {
                issues.add("Collection '" + collection.id() + "' uses menu-slot " + collection.menuSlot()
                        + ", which is reserved for menu controls.");
            }
            String existing = usedSlots.putIfAbsent(collection.menuSlot(), collection.id());
            if (existing != null) {
                issues.add("Collection '" + collection.id() + "' uses menu-slot " + collection.menuSlot()
                        + ", already used by '" + existing + "'.");
            }
        }
    }

    private void saveDefault(String path) {
        if (!new File(plugin.getDataFolder(), path).exists()) {
            plugin.saveResource(path, false);
        }
    }

    private Optional<SourceRule> parseSource(Map<?, ?> map, File file, List<String> issues) {
        CollectionSourceType type;
        try {
            type = CollectionSourceType.valueOf(string(map, "type", "MANUAL").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            issues.add(file.getName() + ": invalid source type '" + map.get("type") + "'.");
            return Optional.empty();
        }

        List<String> materialValues = stringList(map.get("materials"));
        Set<Material> materials = new LinkedHashSet<>();
        for (String value : materialValues) {
            material(value, file, issues).ifPresent(materials::add);
        }

        List<String> entityValues = stringList(map.get("entities"));
        Set<EntityType> entities = new LinkedHashSet<>();
        for (String value : entityValues) {
            try {
                entities.add(EntityType.valueOf(value.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                issues.add(file.getName() + ": invalid entity type '" + value + "'.");
            }
        }

        if (type == CollectionSourceType.ENTITY_KILL && !entityValues.isEmpty() && entities.isEmpty()) {
            issues.add(file.getName() + ": skipped entity source because no valid entities were found.");
            return Optional.empty();
        }
        if (type != CollectionSourceType.ENTITY_KILL && type != CollectionSourceType.MANUAL && !materialValues.isEmpty() && materials.isEmpty()) {
            issues.add(file.getName() + ": skipped material source because no valid materials were found.");
            return Optional.empty();
        }

        ItemMatcher matcher = parseItemMatcher(map.get("item-match"), file, issues);
        long amount = Math.max(1, longValue(map.get("amount"), 1));
        return Optional.of(new SourceRule(type, materials, entities, matcher, amount));
    }

    private ItemMatcher parseItemMatcher(Object raw, File file, List<String> issues) {
        if (!(raw instanceof Map<?, ?> map)) {
            return ItemMatcher.any();
        }
        Integer customModelData = rawInt(map.get("custom-model-data"));
        String displayName = map.containsKey("display-name") ? String.valueOf(map.get("display-name")) : null;
        List<PersistentDataRule> rules = new ArrayList<>();
        Object persistentData = map.get("persistent-data");
        if (persistentData instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> ruleMap)) {
                    continue;
                }
                String keyText = string(ruleMap, "key", "");
                NamespacedKey key = NamespacedKey.fromString(keyText);
                if (key == null) {
                    issues.add(file.getName() + ": invalid persistent-data key '" + keyText + "'.");
                    continue;
                }
                rules.add(new PersistentDataRule(
                        key,
                        string(ruleMap, "type", "STRING"),
                        string(ruleMap, "value", "")
                ));
            }
        }
        return new ItemMatcher(customModelData, displayName, rules);
    }

    private List<RewardAction> parseRewards(Map<?, ?> tierMap, File file, List<String> issues) {
        List<RewardAction> rewards = new ArrayList<>();
        Object raw = tierMap.get("rewards");
        if (!(raw instanceof List<?> list)) {
            return rewards;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> rewardMap)) {
                continue;
            }
            try {
                rewards.add(RewardAction.fromMap(rewardMap));
            } catch (IllegalArgumentException exception) {
                issues.add(file.getName() + ": invalid reward type '" + rewardMap.get("type") + "'.");
            }
        }
        return rewards;
    }

    private List<ProgressMatch> matchMaterial(Material material, Map<Material, List<TrackedSource>> index, List<TrackedSource> any, long baseAmount) {
        Map<String, Long> matches = new LinkedHashMap<>();
        for (TrackedSource trackedSource : any) {
            add(matches, trackedSource.collection.id(), Longs.multiplyClamped(baseAmount, trackedSource.rule.amount()));
        }
        for (TrackedSource trackedSource : index.getOrDefault(material, List.of())) {
            add(matches, trackedSource.collection.id(), Longs.multiplyClamped(baseAmount, trackedSource.rule.amount()));
        }
        return toMatches(matches);
    }

    private List<ProgressMatch> matchItem(ItemStack item, Map<Material, List<TrackedSource>> index, List<TrackedSource> any, long baseAmount) {
        if (item == null || item.getType().isAir() || baseAmount <= 0) {
            return List.of();
        }
        Map<String, Long> matches = new LinkedHashMap<>();
        for (TrackedSource trackedSource : any) {
            if (trackedSource.rule.matchesItem(item)) {
                add(matches, trackedSource.collection.id(), Longs.multiplyClamped(baseAmount, trackedSource.rule.amount()));
            }
        }
        for (TrackedSource trackedSource : index.getOrDefault(item.getType(), List.of())) {
            if (trackedSource.rule.matchesItem(item)) {
                add(matches, trackedSource.collection.id(), Longs.multiplyClamped(baseAmount, trackedSource.rule.amount()));
            }
        }
        return toMatches(matches);
    }

    private List<ProgressMatch> toMatches(Map<String, Long> matches) {
        List<ProgressMatch> result = new ArrayList<>();
        for (Map.Entry<String, Long> entry : matches.entrySet()) {
            CollectionDefinition collection = collections.get(entry.getKey());
            if (collection != null && entry.getValue() > 0) {
                result.add(new ProgressMatch(collection, entry.getValue()));
            }
        }
        return result;
    }

    private void buildIndexes() {
        for (CollectionDefinition collection : collections.values()) {
            if (!collection.enabled()) {
                continue;
            }
            for (SourceRule source : collection.sources()) {
                TrackedSource tracked = new TrackedSource(collection, source);
                switch (source.type()) {
                    case BLOCK_BREAK -> addMaterialSource(blockBreak, blockBreakAny, source, tracked);
                    case ENTITY_KILL -> addEntitySource(entityKill, entityKillAny, source, tracked);
                    case ITEM_PICKUP -> addMaterialSource(itemPickup, itemPickupAny, source, tracked);
                    case CRAFT -> addMaterialSource(craft, craftAny, source, tracked);
                    case FISH -> addMaterialSource(fish, fishAny, source, tracked);
                    case MANUAL -> {
                    }
                }
            }
        }
    }

    private void addMaterialSource(Map<Material, List<TrackedSource>> index, List<TrackedSource> any, SourceRule source, TrackedSource tracked) {
        if (source.materials().isEmpty()) {
            any.add(tracked);
            return;
        }
        for (Material material : source.materials()) {
            index.computeIfAbsent(material, ignored -> new ArrayList<>()).add(tracked);
        }
    }

    private void addEntitySource(Map<EntityType, List<TrackedSource>> index, List<TrackedSource> any, SourceRule source, TrackedSource tracked) {
        if (source.entities().isEmpty()) {
            any.add(tracked);
            return;
        }
        for (EntityType entityType : source.entities()) {
            index.computeIfAbsent(entityType, ignored -> new ArrayList<>()).add(tracked);
        }
    }

    private void clearIndexes() {
        blockBreak.clear();
        blockBreakAny.clear();
        entityKill.clear();
        entityKillAny.clear();
        itemPickup.clear();
        itemPickupAny.clear();
        craft.clear();
        craftAny.clear();
        fish.clear();
        fishAny.clear();
    }

    private Optional<Material> material(String value, File file, List<String> issues) {
        Material material = Material.matchMaterial(value == null ? "" : value);
        if (material == null || material.isAir()) {
            issues.add(file.getName() + ": invalid material '" + value + "'.");
            return Optional.empty();
        }
        return Optional.of(material);
    }

    private static void add(Map<String, Long> matches, String collectionId, long amount) {
        matches.merge(collectionId, amount, Longs::addClamped);
    }

    private static String normalizeId(String id) {
        return id == null ? "" : id.toLowerCase(Locale.ROOT).trim();
    }

    private static boolean validId(String id) {
        return id != null && !id.isBlank() && id.length() <= MAX_ID_LENGTH && id.matches(ID_PATTERN);
    }

    private static boolean validTierId(String id) {
        return id != null && !id.isBlank() && id.length() <= MAX_ID_LENGTH && id.matches(TIER_ID_PATTERN);
    }

    private static String title(String id) {
        String[] parts = id.replace('-', '_').split("_");
        List<String> words = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            words.add(part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1));
        }
        return String.join(" ", words);
    }

    private static Map<String, Object> tierMap(String id, long goal) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("goal", goal);
        map.put("rewards", new ArrayList<>());
        return map;
    }

    private static Map<String, Object> mutable(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static String string(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : value.toString();
    }

    private static List<String> stringList(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (raw == null) {
            return List.of();
        }
        return List.of(raw.toString());
    }

    private static Integer rawInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static long longValue(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private record TrackedSource(CollectionDefinition collection, SourceRule rule) {
    }
}
