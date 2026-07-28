# Performance and Safety

RuinedCollections is built around cheap event checks and batched storage writes.

## Server Platform

RuinedCollections supports Paper and Folia `1.21` through `26.2` while compiling the release jar against the Paper `1.21` API. Player-facing work is routed through a scheduler adapter so Folia servers do not run player inventory, message, or reward work from a shared global task.

The jar targets Java `21` bytecode for the older supported API baseline. Run the server on the Java version required by that Paper/Folia version; Paper/Folia `26.1+` requires Java `25`.

## Progress Writes

Progress is not written to SQL on every event. The plugin batches progress and flushes it on an interval.

```yaml
progress:
  flush-interval-seconds: 15
```

On player quit, that player's pending progress is flushed without forcing every other online player through an immediate database write. On shutdown, all pending progress is flushed synchronously.

Progress earned while a player is still loading is queued and saved safely if the player leaves before loading finishes.

Exports and imports force a synchronous flush of pending and load-waiting progress first. Imports also pause progress loads for affected players until the snapshot apply finishes, then refresh online sessions.

Player and export reads wait for storage writes that were already queued before the read. This prevents a fast reconnect or offline admin edit from loading an older value while keeping normal leaderboard reads parallel.

Craft tracking checks the final server inventory state on the next tick. Shift-click crafting only counts the items that actually reached the player's inventory or cursor.

Armor equip tracking uses Paper's armor change event, so cancelled inventory clicks and invalid client packets do not award progress.

## Large Goals

Progress uses Java `long` values and SQL `BIGINT`.

Safe goal examples:

```yaml
goal: 1000
goal: 1000000
goal: 1000000000
```

Progress addition is clamped to avoid overflow.

`progress.cap-at-highest-tier` is enabled by default. Live tracking caps progress before updating the player session, and storage writes also enforce the cap so queued saves, offline manual adds, and shutdown flushes cannot exceed the highest configured tier.

## Reward Safety

Rewards are protected by the claimed tier table. If a player crosses a tier multiple times, the reward should only run once.

Console command rewards run through the global scheduler on Folia. Player command rewards run on that player's scheduler.

## Player-Placed Block Protection

When enabled, tracked placed blocks are saved in SQL.

```yaml
tracking:
  ignore-player-placed-blocks: true
  placed-block-flush-interval-seconds: 1
```

Protection is immediate in memory. Database changes are grouped into ordered transactions and flushed during graceful shutdown, so busy building servers do not create one SQL transaction per block event.

Placed-block records move with piston-moved and falling gravity blocks, and are removed when tracked blocks are destroyed by explosions, burning, fading, or leaf decay. Multi-block placement events record every placed block.

## World Filters

Use `disabled-worlds` to block collection progress in worlds like mines, test worlds, or creative worlds.

```yaml
tracking:
  disabled-worlds:
    - creative
    - build
```

Use `enabled-worlds` when only a few worlds should count.

```yaml
tracking:
  enabled-worlds:
    - world
    - resource_world
```

## Huge Servers

Recommended settings:

- Use MySQL or MariaDB.
- Keep `flush-interval-seconds` between `10` and `30`.
- Keep collection sources specific.
- Avoid catch-all item pickup sources unless needed.
- Use `/rc validate` after every config change.
- Back up before imports.
- Keep `leaderboards.refresh-interval-seconds` at `30` or higher unless the server is small.
- Leaderboard refreshes are cached and coalesced so repeated placeholder/menu requests do not stack duplicate loads for the same collection.
- Set leaderboards are aggregated in SQL from the collections assigned to that set, then cached with the normal leaderboard refresh interval.

## Known Limits

- Offline progress-style PlaceholderAPI requests return zero or empty values because the plugin does not load all offline player data into memory.
- Collection and set leaderboard top placeholders use stored last-known names and do not require the player to be online.
- Custom block support depends on the custom block plugin unless progress is added manually.
- Economy rewards need Vault and a registered economy provider.
- Reward claims use at-most-once delivery. After an unclean process crash, check diagnostics and manually reconcile any external reward that was claimed immediately before the crash.
