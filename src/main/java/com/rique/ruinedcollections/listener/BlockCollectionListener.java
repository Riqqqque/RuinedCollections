package com.rique.ruinedcollections.listener;

import com.rique.ruinedcollections.RuinedCollectionsPlugin;
import com.rique.ruinedcollections.collection.ProgressMatch;
import com.rique.ruinedcollections.diagnostics.DiagnosticService;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.PistonMoveReaction;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class BlockCollectionListener implements Listener {
    private final RuinedCollectionsPlugin plugin;
    private final TrackingFilter filter;
    private final NamespacedKey placedFallingBlockKey;

    public BlockCollectionListener(RuinedCollectionsPlugin plugin, TrackingFilter filter) {
        this.plugin = plugin;
        this.filter = filter;
        this.placedFallingBlockKey = new NamespacedKey(plugin, "placed_falling_block");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!filter.ignorePlayerPlacedBlocks()) {
            return;
        }
        if (filter.blockedWorld(event.getBlockPlaced().getWorld())) {
            return;
        }
        if (event instanceof BlockMultiPlaceEvent multiPlaceEvent) {
            multiPlaceEvent.getReplacedBlockStates().forEach(state -> markIfTracked(state.getBlock()));
            return;
        }
        markIfTracked(event.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        boolean playerPlaced = filter.ignorePlayerPlacedBlocks()
                && plugin.placedBlocks().consume(event.getBlock());
        if (filter.blocked(event.getPlayer())) {
            return;
        }
        if (playerPlaced) {
            plugin.diagnostics().debug("tracking", "Skipped player-placed block break", DiagnosticService.fields(
                    "player", event.getPlayer().getName(),
                    "uuid", event.getPlayer().getUniqueId(),
                    "world", event.getBlock().getWorld().getName(),
                    "x", event.getBlock().getX(),
                    "y", event.getBlock().getY(),
                    "z", event.getBlock().getZ(),
                    "material", event.getBlock().getType().name()
            ));
            return;
        }
        for (ProgressMatch match : plugin.collectionRegistry().matchBlockBreak(event.getBlock().getType())) {
            plugin.progressService().addProgress(event.getPlayer(), match.collection(), match.amount());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        consumeDestroyed(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        consumeDestroyed(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        plugin.placedBlocks().consume(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        plugin.placedBlocks().consume(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        plugin.placedBlocks().consume(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFallingBlockChange(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock fallingBlock)) {
            return;
        }
        if (fallingBlock.getPersistentDataContainer().has(placedFallingBlockKey, PersistentDataType.BYTE)) {
            if (!event.getTo().isAir()) {
                plugin.placedBlocks().mark(event.getBlock(), event.getTo());
            }
            return;
        }
        if (plugin.placedBlocks().consume(event.getBlock())) {
            fallingBlock.getPersistentDataContainer().set(placedFallingBlockKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        movePistonBlocks(event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        movePistonBlocks(event.getBlocks(), event.getDirection());
    }

    private void markIfTracked(Block block) {
        if (plugin.collectionRegistry().isTrackedBlockMaterial(block.getType())) {
            plugin.placedBlocks().mark(block);
        }
    }

    private void consumeDestroyed(List<Block> blocks) {
        blocks.forEach(plugin.placedBlocks()::consume);
    }

    private void movePistonBlocks(List<Block> blocks, BlockFace direction) {
        List<Block> moved = new ArrayList<>();
        for (Block block : blocks) {
            if (block.getPistonMoveReaction() == PistonMoveReaction.BREAK) {
                plugin.placedBlocks().consume(block);
            } else {
                moved.add(block);
            }
        }
        plugin.placedBlocks().moveBlocks(moved, direction);
    }
}
