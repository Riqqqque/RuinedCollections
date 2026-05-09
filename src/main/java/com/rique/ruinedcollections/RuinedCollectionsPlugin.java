package com.rique.ruinedcollections;

import com.rique.ruinedcollections.collection.CollectionRegistry;
import com.rique.ruinedcollections.command.CollectionsCommand;
import com.rique.ruinedcollections.command.RuinedCollectionsCommand;
import com.rique.ruinedcollections.hook.HookManager;
import com.rique.ruinedcollections.listener.BlockCollectionListener;
import com.rique.ruinedcollections.listener.EntityKillCollectionListener;
import com.rique.ruinedcollections.listener.ItemCollectionListener;
import com.rique.ruinedcollections.listener.PlayerConnectionListener;
import com.rique.ruinedcollections.listener.TrackingFilter;
import com.rique.ruinedcollections.menu.MenuService;
import com.rique.ruinedcollections.reward.RewardService;
import com.rique.ruinedcollections.storage.CollectionRepository;
import com.rique.ruinedcollections.storage.DataSnapshotService;
import com.rique.ruinedcollections.storage.DatabaseManager;
import com.rique.ruinedcollections.storage.PlacedBlockService;
import com.rique.ruinedcollections.storage.ProgressService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;

public final class RuinedCollectionsPlugin extends JavaPlugin {
    private CollectionRegistry collectionRegistry;
    private DatabaseManager databaseManager;
    private CollectionRepository repository;
    private HookManager hooks;
    private RewardService rewardService;
    private ProgressService progressService;
    private PlacedBlockService placedBlocks;
    private MenuService menuService;
    private DataSnapshotService snapshots;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        collectionRegistry = new CollectionRegistry(this);
        collectionRegistry.ensureDefaults();
        List<String> issues = collectionRegistry.load();
        if (!issues.isEmpty()) {
            getLogger().warning("Loaded with " + issues.size() + " collection issue(s). Run /rc validate for details.");
        }

        databaseManager = new DatabaseManager();
        try {
            databaseManager.start(this);
        } catch (SQLException exception) {
            getLogger().log(Level.SEVERE, "Could not start storage.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        repository = new CollectionRepository(databaseManager.dataSource(), databaseManager.storageType(), databaseManager.tablePrefix());
        hooks = new HookManager(this);
        rewardService = new RewardService(this);
        progressService = new ProgressService(this, repository, rewardService);
        placedBlocks = new PlacedBlockService(this, repository);
        menuService = new MenuService(this);
        menuService.load();
        snapshots = new DataSnapshotService(this, repository);

        hooks.start();
        placedBlocks.start();
        registerListeners();
        registerCommands();
        progressService.start();
    }

    @Override
    public void onDisable() {
        if (progressService != null) {
            progressService.stop();
        }
        if (repository != null) {
            repository.close();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    public void reloadAll() {
        reloadConfig();
        List<String> issues = collectionRegistry.load();
        menuService.load();
        if (progressService != null) {
            progressService.recheckOnlineRewards();
        }
        if (!issues.isEmpty()) {
            getLogger().warning("Reloaded with " + issues.size() + " collection issue(s). Run /rc validate for details.");
        }
    }

    public CollectionRegistry collectionRegistry() {
        return collectionRegistry;
    }

    public CollectionRepository repository() {
        return repository;
    }

    public HookManager hooks() {
        return hooks;
    }

    public ProgressService progressService() {
        return progressService;
    }

    public PlacedBlockService placedBlocks() {
        return placedBlocks;
    }

    public MenuService menuService() {
        return menuService;
    }

    public DataSnapshotService snapshots() {
        return snapshots;
    }

    public String messagePrefix() {
        return getConfig().getString("messages.prefix", "");
    }

    private void registerListeners() {
        TrackingFilter filter = new TrackingFilter(this);
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockCollectionListener(this, filter), this);
        getServer().getPluginManager().registerEvents(new EntityKillCollectionListener(this, filter), this);
        getServer().getPluginManager().registerEvents(new ItemCollectionListener(this, filter), this);
        getServer().getPluginManager().registerEvents(menuService, this);
    }

    private void registerCommands() {
        RuinedCollectionsCommand adminCommand = new RuinedCollectionsCommand(this);
        PluginCommand ruinedCollections = getCommand("ruinedcollections");
        if (ruinedCollections != null) {
            ruinedCollections.setExecutor(adminCommand);
            ruinedCollections.setTabCompleter(adminCommand);
        }
        PluginCommand collections = getCommand("collections");
        if (collections != null) {
            collections.setExecutor(new CollectionsCommand(this));
        }
    }
}
