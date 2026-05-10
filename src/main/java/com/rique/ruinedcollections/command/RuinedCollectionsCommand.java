package com.rique.ruinedcollections.command;

import com.rique.ruinedcollections.RuinedCollectionsPlugin;
import com.rique.ruinedcollections.collection.CollectionDefinition;
import com.rique.ruinedcollections.collection.CollectionSourceType;
import com.rique.ruinedcollections.diagnostics.DiagnosticService;
import com.rique.ruinedcollections.platform.ServerCompatibility;
import com.rique.ruinedcollections.storage.ImportPreview;
import com.rique.ruinedcollections.util.Longs;
import com.rique.ruinedcollections.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class RuinedCollectionsCommand implements CommandExecutor, TabCompleter {
    private final RuinedCollectionsPlugin plugin;

    public RuinedCollectionsCommand(RuinedCollectionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        try {
            switch (sub) {
                case "reload" -> reload(sender);
                case "validate" -> validate(sender);
                case "list" -> list(sender);
                case "info" -> info(sender, args);
                case "open" -> open(sender, args);
                case "add" -> add(sender, args);
                case "set" -> set(sender, args);
                case "reset" -> reset(sender, args);
                case "create" -> create(sender, args);
                case "delete" -> delete(sender, args);
                case "tier" -> tier(sender, args);
                case "source" -> source(sender, args);
                case "reward" -> reward(sender, args);
                case "export" -> export(sender, args);
                case "import" -> importData(sender, args);
                case "diagnostics" -> diagnostics(sender, args);
                default -> help(sender);
            }
        } catch (NoPermission ignored) {
            return true;
        } catch (IOException exception) {
            plugin.diagnostics().error("commands", "File change failed", DiagnosticService.fields(
                    "sender", sender.getName(),
                    "subcommand", sub
            ), exception);
            sender.sendMessage(color("&cFile change failed: " + exception.getMessage()));
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(color("&6RuinedCollections commands:"));
        sender.sendMessage(color("&e/rc reload &7- reload configs"));
        sender.sendMessage(color("&e/rc validate &7- check collection files"));
        sender.sendMessage(color("&e/rc list &7- list collections"));
        sender.sendMessage(color("&e/rc info <collection> &7- show collection info"));
        sender.sendMessage(color("&e/rc open [player] &7- open the menu"));
        sender.sendMessage(color("&e/rc add <player> <collection> <amount> &7- add progress"));
        sender.sendMessage(color("&e/rc set <player> <collection> <amount> &7- set progress"));
        sender.sendMessage(color("&e/rc reset <player> <collection> &7- set progress to 0"));
        sender.sendMessage(color("&e/rc create <id> <material> [display] &7- create a collection file"));
        sender.sendMessage(color("&e/rc delete <id> &7- move a collection file to collections/deleted"));
        sender.sendMessage(color("&e/rc tier add <collection> <tier> <goal> &7- add a tier"));
        sender.sendMessage(color("&e/rc source add <collection> <type> <key> &7- add a source"));
        sender.sendMessage(color("&e/rc reward add-command <collection> <tier> <console|player> <command> &7- add command reward"));
        sender.sendMessage(color("&e/rc export <file> &7- export data"));
        sender.sendMessage(color("&e/rc import <file> [--apply] &7- preview or apply data"));
        sender.sendMessage(color("&e/rc diagnostics [tail|path] &7- view diagnostics"));
    }

    private void reload(CommandSender sender) {
        require(sender, "ruinedcollections.admin.reload");
        Set<String> before = new LinkedHashSet<>(collectionIds());
        plugin.reloadAll();
        List<String> after = collectionIds();
        List<String> added = after.stream().filter(id -> !before.contains(id)).toList();
        sender.sendMessage(color(plugin.getConfig().getString("messages.reloaded", "&aReloaded %collections% collections.")
                .replace("%collections%", String.valueOf(plugin.collectionRegistry().all().size()))));
        if (!added.isEmpty()) {
            sender.sendMessage(color("&aNew collections loaded: &f" + String.join("&7, &f", added)));
        }
    }

    private void validate(CommandSender sender) {
        require(sender, "ruinedcollections.admin.reload");
        List<String> issues = plugin.collectionRegistry().load();
        plugin.menuService().load();
        if (issues.isEmpty()) {
            sender.sendMessage(color("&aNo collection issues found."));
            return;
        }
        sender.sendMessage(color("&cFound " + issues.size() + " issue(s):"));
        for (String issue : issues.stream().limit(10).toList()) {
            sender.sendMessage(color("&7- &f" + issue));
        }
        if (issues.size() > 10) {
            sender.sendMessage(color("&7...and " + (issues.size() - 10) + " more."));
        }
    }

    private void list(CommandSender sender) {
        require(sender, "ruinedcollections.view");
        sender.sendMessage(color("&6Collections:"));
        for (CollectionDefinition collection : plugin.collectionRegistry().all()) {
            sender.sendMessage(color("&7- &f" + collection.id() + " &8(" + collection.tiers().size() + " tiers, " + (collection.enabled() ? "enabled" : "disabled") + ")"));
        }
    }

    private void info(CommandSender sender, String[] args) {
        require(sender, "ruinedcollections.view");
        if (args.length < 2) {
            sender.sendMessage(color("&cUsage: /rc info <collection>"));
            return;
        }
        Optional<CollectionDefinition> collection = plugin.collectionRegistry().get(args[1]);
        if (collection.isEmpty()) {
            sender.sendMessage(color(plugin.getConfig().getString("messages.invalid-collection").replace("%collection%", args[1])));
            return;
        }
        CollectionDefinition value = collection.get();
        sender.sendMessage(color("&6" + value.id() + " &7- " + value.displayName()));
        sender.sendMessage(color("&7Sources: &f" + value.sources().size()));
        for (var tier : value.tiers()) {
            sender.sendMessage(color("&7Tier &f" + tier.id() + "&7: &f" + Longs.format(tier.goal()) + " &7goal, &f" + tier.rewards().size() + " &7rewards"));
        }
    }

    private void open(CommandSender sender, String[] args) {
        require(sender, "ruinedcollections.admin");
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(color("&cThat player is not online."));
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(color("&cUsage: /rc open <player>"));
            return;
        }
        plugin.scheduler().runPlayer(target, () -> plugin.menuService().openMain(target, 0));
    }

    private void add(CommandSender sender, String[] args) {
        require(sender, "ruinedcollections.admin.modify");
        if (args.length < 4) {
            sender.sendMessage(color("&cUsage: /rc add <player> <collection> <amount>"));
            return;
        }
        Optional<CollectionDefinition> collection = plugin.collectionRegistry().get(args[2]);
        Long amount = Longs.parsePositive(args[3]);
        if (collection.isEmpty() || amount == null) {
            sender.sendMessage(color("&cInvalid collection or amount."));
            return;
        }
        OfflinePlayer player = resolvePlayer(args[1]);
        if (player == null) {
            sender.sendMessage(color("&cPlayer not found. Use an online player, cached player, or UUID."));
            return;
        }
        rememberPlayerName(player);
        plugin.progressService().addManual(player.getUniqueId(), collection.get().id(), amount);
        sender.sendMessage(color(plugin.getConfig().getString("messages.progress-added")
                .replace("%amount%", Longs.format(amount))
                .replace("%player%", args[1])
                .replace("%collection%", collection.get().id())));
    }

    private void set(CommandSender sender, String[] args) {
        require(sender, "ruinedcollections.admin.modify");
        if (args.length < 4) {
            sender.sendMessage(color("&cUsage: /rc set <player> <collection> <amount>"));
            return;
        }
        Optional<CollectionDefinition> collection = plugin.collectionRegistry().get(args[2]);
        Long amount = parseZeroOrPositive(args[3]);
        if (collection.isEmpty() || amount == null) {
            sender.sendMessage(color("&cInvalid collection or amount."));
            return;
        }
        OfflinePlayer player = resolvePlayer(args[1]);
        if (player == null) {
            sender.sendMessage(color("&cPlayer not found. Use an online player, cached player, or UUID."));
            return;
        }
        rememberPlayerName(player);
        plugin.progressService().setProgress(player.getUniqueId(), collection.get().id(), amount);
        sender.sendMessage(color(plugin.getConfig().getString("messages.progress-set")
                .replace("%amount%", Longs.format(amount))
                .replace("%player%", args[1])
                .replace("%collection%", collection.get().id())));
    }

    private void reset(CommandSender sender, String[] args) {
        require(sender, "ruinedcollections.admin.modify");
        if (args.length < 3) {
            sender.sendMessage(color("&cUsage: /rc reset <player> <collection>"));
            return;
        }
        Optional<CollectionDefinition> collection = plugin.collectionRegistry().get(args[2]);
        if (collection.isEmpty()) {
            sender.sendMessage(color("&cInvalid collection."));
            return;
        }
        OfflinePlayer player = resolvePlayer(args[1]);
        if (player == null) {
            sender.sendMessage(color("&cPlayer not found. Use an online player, cached player, or UUID."));
            return;
        }
        rememberPlayerName(player);
        plugin.progressService().setProgress(player.getUniqueId(), collection.get().id(), 0);
        sender.sendMessage(color(plugin.getConfig().getString("messages.progress-reset")
                .replace("%player%", args[1])
                .replace("%collection%", collection.get().id())));
    }

    private void create(CommandSender sender, String[] args) throws IOException {
        require(sender, "ruinedcollections.admin.modify");
        if (args.length < 3) {
            sender.sendMessage(color("&cUsage: /rc create <id> <material> [display name]"));
            plugin.diagnostics().debug("commands", "Create collection rejected", DiagnosticService.fields("sender", sender.getName(), "reason", "usage"));
            return;
        }
        Material material = Material.matchMaterial(args[2]);
        if (material == null || material.isAir()) {
            sender.sendMessage(color("&cInvalid material."));
            plugin.diagnostics().debug("commands", "Create collection rejected", DiagnosticService.fields(
                    "sender", sender.getName(),
                    "collection", args[1],
                    "material", args[2],
                    "reason", "invalid_material"
            ));
            return;
        }
        String displayName = args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : args[1];
        if (!plugin.collectionRegistry().createCollection(args[1], material, displayName)) {
            sender.sendMessage(color("&cCould not create that collection. Check the id or duplicates."));
            return;
        }
        plugin.reloadAll();
        sender.sendMessage(color("&aCreated collection &f" + args[1] + "&a."));
    }

    private void delete(CommandSender sender, String[] args) throws IOException {
        require(sender, "ruinedcollections.admin.modify");
        if (args.length < 2) {
            sender.sendMessage(color("&cUsage: /rc delete <id>"));
            return;
        }
        if (!plugin.collectionRegistry().deleteCollection(args[1])) {
            sender.sendMessage(color("&cUnknown collection."));
            return;
        }
        plugin.reloadAll();
        sender.sendMessage(color("&aMoved collection &f" + args[1] + " &ato the deleted folder."));
    }

    private void tier(CommandSender sender, String[] args) throws IOException {
        require(sender, "ruinedcollections.admin.modify");
        if (args.length < 5 || !"add".equalsIgnoreCase(args[1])) {
            sender.sendMessage(color("&cUsage: /rc tier add <collection> <tier> <goal>"));
            return;
        }
        Long goal = Longs.parsePositive(args[4]);
        if (goal == null || !plugin.collectionRegistry().addTier(args[2], args[3], goal)) {
            sender.sendMessage(color("&cCould not add that tier."));
            return;
        }
        plugin.reloadAll();
        sender.sendMessage(color("&aAdded tier &f" + args[3] + "&a."));
    }

    private void source(CommandSender sender, String[] args) throws IOException {
        require(sender, "ruinedcollections.admin.modify");
        if (args.length < 4 || !"add".equalsIgnoreCase(args[1])) {
            sender.sendMessage(color("&cUsage: /rc source add <collection> <type> [key]"));
            return;
        }
        CollectionSourceType type;
        try {
            type = CollectionSourceType.valueOf(args[3].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(color("&cInvalid source type."));
            return;
        }
        if (type != CollectionSourceType.MANUAL && args.length < 5) {
            sender.sendMessage(color("&cThat source type needs a material or entity key."));
            return;
        }
        String key = args.length >= 5 ? args[4] : "";
        if (type == CollectionSourceType.ENTITY_KILL && entityType(key) == null) {
            sender.sendMessage(color("&cInvalid entity type."));
            return;
        }
        Material material = Material.matchMaterial(key);
        if (type != CollectionSourceType.ENTITY_KILL && type != CollectionSourceType.MANUAL && (material == null || material.isAir())) {
            sender.sendMessage(color("&cInvalid material."));
            return;
        }
        if (!plugin.collectionRegistry().addSource(args[2], type, key)) {
            sender.sendMessage(color("&cCould not add that source."));
            return;
        }
        plugin.reloadAll();
        sender.sendMessage(color("&aAdded source to &f" + args[2] + "&a."));
    }

    private void reward(CommandSender sender, String[] args) throws IOException {
        require(sender, "ruinedcollections.admin.modify");
        if (args.length < 6 || !"add-command".equalsIgnoreCase(args[1])) {
            sender.sendMessage(color("&cUsage: /rc reward add-command <collection> <tier> <console|player> <command>"));
            return;
        }
        String senderType = args[4].toUpperCase(Locale.ROOT);
        if (!senderType.equals("CONSOLE") && !senderType.equals("PLAYER")) {
            sender.sendMessage(color("&cReward sender must be console or player."));
            return;
        }
        String command = String.join(" ", Arrays.copyOfRange(args, 5, args.length));
        if (!plugin.collectionRegistry().addCommandReward(args[2], args[3], senderType, command)) {
            sender.sendMessage(color("&cCould not add that reward."));
            return;
        }
        plugin.reloadAll();
        sender.sendMessage(color("&aAdded command reward to &f" + args[2] + " " + args[3] + "&a."));
    }

    private void export(CommandSender sender, String[] args) {
        require(sender, "ruinedcollections.admin.export");
        if (args.length < 2) {
            sender.sendMessage(color("&cUsage: /rc export <file>"));
            return;
        }
        plugin.progressService().flushSync();
        File file = dataFile(args[1]);
        plugin.snapshots().exportData(file).whenComplete((result, throwable) ->
                plugin.scheduler().runSender(sender, () -> {
                    if (throwable != null) {
                        plugin.diagnostics().error("export", "Export failed", DiagnosticService.fields(
                                "sender", sender.getName(),
                                "file", file.getAbsolutePath()
                        ), throwable);
                        sender.sendMessage(color("&cExport failed: " + throwable.getMessage()));
                        return;
                    }
                    sender.sendMessage(color("&aExported data to &f" + result.getName() + "&a."));
                }));
    }

    private void importData(CommandSender sender, String[] args) {
        require(sender, "ruinedcollections.admin.import");
        if (args.length < 2) {
            sender.sendMessage(color("&cUsage: /rc import <file> [--apply]"));
            plugin.diagnostics().debug("commands", "Import rejected", DiagnosticService.fields("sender", sender.getName(), "reason", "usage"));
            return;
        }
        File file = dataFile(args[1]);
        if (!file.exists()) {
            sender.sendMessage(color("&cThat file does not exist."));
            plugin.diagnostics().warn("import", "Import file was missing", DiagnosticService.fields(
                    "sender", sender.getName(),
                    "file", file.getAbsolutePath()
            ));
            return;
        }
        boolean apply = args.length >= 3 && "--apply".equalsIgnoreCase(args[2]);
        plugin.snapshots().previewAsync(file).whenComplete((preview, previewError) ->
                plugin.scheduler().runSender(sender, () -> {
                    if (previewError != null) {
                        plugin.diagnostics().error("import", "Import preview failed", DiagnosticService.fields(
                                "sender", sender.getName(),
                                "file", file.getAbsolutePath()
                        ), previewError);
                        sender.sendMessage(color("&cImport preview failed: " + previewError.getMessage()));
                        return;
                    }
                    if (!apply) {
                        sender.sendMessage(color("&eImport preview: &f" + preview.progressRows().size() + " &eprogress rows, &f"
                                + preview.claimedRows().size() + " &eclaimed tiers, &f"
                                + preview.playerNames().size() + " &eplayer names. Run with --apply to import."));
                        return;
                    }
                    applyImport(sender, file, preview);
                }));
    }

    private void applyImport(CommandSender sender, File file, ImportPreview preview) {
        plugin.snapshots().apply(preview).whenComplete((ignored, throwable) ->
                plugin.scheduler().runSender(sender, () -> {
                    if (throwable != null) {
                        plugin.diagnostics().error("import", "Import failed", DiagnosticService.fields(
                                "sender", sender.getName(),
                                "file", file.getAbsolutePath(),
                                "progressRows", preview.progressRows().size(),
                                "claimedRows", preview.claimedRows().size(),
                                "playerNames", preview.playerNames().size()
                        ), throwable);
                        sender.sendMessage(color("&cImport failed: " + throwable.getMessage()));
                        return;
                    }
                    plugin.scheduler().runGlobal(() -> preview.progressRows().stream()
                            .map(row -> Bukkit.getPlayer(row.playerId()))
                            .filter(player -> player != null && player.isOnline())
                            .distinct()
                            .forEach(player -> plugin.scheduler().runPlayer(player, () -> plugin.progressService().refresh(player))));
                    sender.sendMessage(color("&aImported data."));
                }));
    }

    private void diagnostics(CommandSender sender, String[] args) {
        require(sender, "ruinedcollections.admin.diagnostics");
        if (args.length >= 2 && "tail".equalsIgnoreCase(args[1])) {
            Long requestedLines = args.length >= 3 ? parseZeroOrPositive(args[2]) : null;
            int lines = requestedLines == null ? 10 : Math.max(1, Math.min(requestedLines.intValue(), 50));
            sender.sendMessage(color("&6Last " + lines + " diagnostics line(s):"));
            for (String line : plugin.diagnostics().tail(lines)) {
                sender.sendMessage(Text.color("&7" + line));
            }
            return;
        }
        if (args.length >= 2 && "path".equalsIgnoreCase(args[1])) {
            sender.sendMessage(color("&7Diagnostics file: &f" + plugin.diagnostics().logPath()));
            return;
        }
        sender.sendMessage(color("&6RuinedCollections diagnostics:"));
        sender.sendMessage(color("&7Server: &f" + plugin.compatibility().serverName()
                + " " + plugin.compatibility().minecraftVersion()));
        sender.sendMessage(color("&7Bukkit: &f" + plugin.compatibility().bukkitVersion()));
        sender.sendMessage(color("&7Support: &f" + plugin.compatibility().statusLabel()
                + " &8(" + ServerCompatibility.SUPPORTED_RANGE + ")"));
        sender.sendMessage(color("&7Enabled: &f" + plugin.diagnostics().enabled()));
        sender.sendMessage(color("&7File: &f" + plugin.diagnostics().logPath()));
        sender.sendMessage(color("&7Size: &f" + plugin.diagnostics().logSizeBytes() + " bytes"));
        sender.sendMessage(color("&7Debug: &ftracking=" + plugin.diagnostics().debugTrackingSkips()
                + " progress=" + plugin.diagnostics().debugProgress()
                + " rewards=" + plugin.diagnostics().debugRewards()
                + " commands=" + plugin.diagnostics().debugCommands()));
    }

    private void require(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission) && !sender.hasPermission("ruinedcollections.admin")) {
            sender.sendMessage(color(plugin.getConfig().getString("messages.no-permission", "&cYou do not have permission.")));
            throw new NoPermission();
        }
    }

    private File dataFile(String name) {
        File exports = new File(plugin.getDataFolder(), "exports");
        String cleanName = new File(name.replace('\\', '/')).getName();
        if (cleanName.isBlank()) {
            cleanName = "backup.yml";
        }
        return new File(exports, cleanName.endsWith(".yml") || cleanName.endsWith(".yaml") ? cleanName : cleanName + ".yml");
    }

    private Long parseZeroOrPositive(String input) {
        try {
            long value = Long.parseLong(input);
            return value >= 0 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private OfflinePlayer resolvePlayer(String input) {
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            return online;
        }
        try {
            return Bukkit.getOfflinePlayer(UUID.fromString(input));
        } catch (IllegalArgumentException ignored) {
            return Bukkit.getOfflinePlayerIfCached(input);
        }
    }

    private void rememberPlayerName(OfflinePlayer player) {
        plugin.progressService().recordPlayerName(player.getUniqueId(), player.getName());
    }

    private String color(String text) {
        return Text.color(plugin.messagePrefix() + text);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], subcommands());
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "help" -> args.length == 2 ? filter(args[1], subcommands()) : List.of();
            case "reload", "validate", "list" -> List.of();
            case "info", "delete" -> completeCollection(args, 2);
            case "open" -> args.length == 2 ? filter(args[1], onlinePlayerNames()) : List.of();
            case "add", "set" -> completeProgressEdit(args);
            case "reset" -> completeReset(args);
            case "create" -> completeCreate(args);
            case "tier" -> completeTier(args);
            case "source" -> completeSource(args);
            case "reward" -> completeReward(args);
            case "export" -> completeExport(args);
            case "import" -> completeImport(args);
            case "diagnostics" -> completeDiagnostics(args);
            default -> List.of();
        };
    }

    private List<String> completeProgressEdit(String[] args) {
        if (args.length == 2) {
            return filter(args[1], playerTargets());
        }
        if (args.length == 3) {
            return filter(args[2], collectionIds());
        }
        if (args.length == 4) {
            return filter(args[3], List.of("1", "10", "50", "100", "1000", "1000000"));
        }
        return List.of();
    }

    private List<String> completeReset(String[] args) {
        if (args.length == 2) {
            return filter(args[1], playerTargets());
        }
        if (args.length == 3) {
            return filter(args[2], collectionIds());
        }
        return List.of();
    }

    private List<String> completeCreate(String[] args) {
        if (args.length == 2) {
            return filter(args[1], List.of("collection_id", "oak_log", "obsidian", "custom_gem"));
        }
        if (args.length == 3) {
            return filter(args[2], materialNames());
        }
        if (args.length == 4) {
            return filter(args[3], List.of("&6Oak_Log", "&aCollection_Name", "&bCustom_Gem"));
        }
        return List.of();
    }

    private List<String> completeTier(String[] args) {
        if (args.length == 2) {
            return filter(args[1], List.of("add"));
        }
        if (args.length == 3) {
            return filter(args[2], collectionIds());
        }
        if (args.length == 4) {
            return filter(args[3], List.of("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"));
        }
        if (args.length == 5) {
            return filter(args[4], List.of("50", "100", "500", "1000", "10000", "100000", "1000000", "1000000000"));
        }
        return List.of();
    }

    private List<String> completeSource(String[] args) {
        if (args.length == 2) {
            return filter(args[1], List.of("add"));
        }
        if (args.length == 3) {
            return filter(args[2], collectionIds());
        }
        if (args.length == 4) {
            return filter(args[3], enumNames(CollectionSourceType.values()));
        }
        if (args.length == 5) {
            CollectionSourceType type = sourceType(args[3]);
            if (type == CollectionSourceType.ENTITY_KILL) {
                return filter(args[4], enumNames(EntityType.values()));
            }
            if (type == CollectionSourceType.MANUAL) {
                return List.of();
            }
            return filter(args[4], materialNames());
        }
        return List.of();
    }

    private List<String> completeReward(String[] args) {
        if (args.length == 2) {
            return filter(args[1], List.of("add-command"));
        }
        if (args.length == 3) {
            return filter(args[2], collectionIds());
        }
        if (args.length == 4) {
            return filter(args[3], tierIds(args[2]));
        }
        if (args.length == 5) {
            return filter(args[4], List.of("CONSOLE", "PLAYER"));
        }
        if (args.length == 6) {
            return filter(args[5], List.of("give", "lp", "eco", "say", "broadcast"));
        }
        if (args.length == 7 && "give".equalsIgnoreCase(args[5])) {
            return filter(args[6], List.of("%player%"));
        }
        if (args.length == 8 && "give".equalsIgnoreCase(args[5])) {
            return filter(args[7], materialNames());
        }
        if (args.length == 9 && "give".equalsIgnoreCase(args[5])) {
            return filter(args[8], List.of("1", "8", "16", "32", "64"));
        }
        if (args.length == 7 && "eco".equalsIgnoreCase(args[5])) {
            return filter(args[6], List.of("give"));
        }
        if (args.length == 8 && "eco".equalsIgnoreCase(args[5])) {
            return filter(args[7], List.of("%player%"));
        }
        if (args.length == 9 && "eco".equalsIgnoreCase(args[5])) {
            return filter(args[8], List.of("100", "1000", "10000"));
        }
        if (args.length == 7 && "lp".equalsIgnoreCase(args[5])) {
            return filter(args[6], List.of("user"));
        }
        if (args.length == 8 && "lp".equalsIgnoreCase(args[5])) {
            return filter(args[7], List.of("%player%"));
        }
        if (args.length == 9 && "lp".equalsIgnoreCase(args[5])) {
            return filter(args[8], List.of("permission", "parent"));
        }
        return List.of();
    }

    private List<String> completeExport(String[] args) {
        if (args.length == 2) {
            return filter(args[1], List.of("backup.yml", "before-update.yml", "before-import.yml"));
        }
        return List.of();
    }

    private List<String> completeImport(String[] args) {
        if (args.length == 2) {
            return filter(args[1], exportFiles());
        }
        if (args.length == 3) {
            return filter(args[2], List.of("--apply"));
        }
        return List.of();
    }

    private List<String> completeDiagnostics(String[] args) {
        if (args.length == 2) {
            return filter(args[1], List.of("tail", "path"));
        }
        if (args.length == 3 && "tail".equalsIgnoreCase(args[1])) {
            return filter(args[2], List.of("10", "25", "50"));
        }
        return List.of();
    }

    private List<String> completeCollection(String[] args, int index) {
        return args.length == index ? filter(args[index - 1], collectionIds()) : List.of();
    }

    private List<String> collectionIds() {
        return plugin.collectionRegistry().all().stream().map(CollectionDefinition::id).toList();
    }

    private List<String> tierIds(String collectionId) {
        return plugin.collectionRegistry().get(collectionId)
                .map(collection -> collection.tiers().stream().map(tier -> tier.id()).toList())
                .orElse(List.of("I", "II", "III", "IV", "V"));
    }

    private List<String> subcommands() {
        return List.of("help", "reload", "validate", "list", "info", "open", "add", "set", "reset", "create", "delete", "tier", "source", "reward", "export", "import", "diagnostics");
    }

    private List<String> playerTargets() {
        List<String> targets = new ArrayList<>(onlinePlayerNames());
        targets.add("player");
        targets.add("uuid");
        return targets;
    }

    private List<String> onlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    private List<String> materialNames() {
        return Arrays.stream(Material.values())
                .filter(material -> !material.isAir())
                .map(Enum::name)
                .toList();
    }

    private <T extends Enum<T>> List<String> enumNames(T[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    private CollectionSourceType sourceType(String input) {
        try {
            return CollectionSourceType.valueOf(input.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private EntityType entityType(String input) {
        try {
            return EntityType.valueOf(input.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private List<String> exportFiles() {
        File folder = new File(plugin.getDataFolder(), "exports");
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null || files.length == 0) {
            return List.of("backup.yml");
        }
        return Arrays.stream(files).map(File::getName).toList();
    }

    private List<String> filter(String prefix, List<String> options) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }

    private static final class NoPermission extends RuntimeException {
        private NoPermission() {
        }
    }
}
