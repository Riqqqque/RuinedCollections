# Performance and Safety

RuinedCollections is built around cheap event checks and batched storage writes.

## Progress Writes

Progress is not written to SQL on every event. The plugin batches progress and flushes it on an interval.

```yaml
progress:
  flush-interval-seconds: 15
```

On shutdown, pending progress is flushed synchronously.

## Large Goals

Progress uses Java `long` values and SQL `BIGINT`.

Safe goal examples:

```yaml
goal: 1000
goal: 1000000
goal: 1000000000
```

Progress addition is clamped to avoid overflow.

## Reward Safety

Rewards are protected by the claimed tier table. If a player crosses a tier multiple times, the reward should only run once.

## Player-Placed Block Protection

When enabled, tracked placed blocks are saved in SQL.

```yaml
tracking:
  ignore-player-placed-blocks: true
```

This survives restarts.

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

## Known Limits

- Offline PlaceholderAPI requests return zero or empty values because the plugin does not load all offline player data into memory.
- Custom block support depends on the custom block plugin unless progress is added manually.
- Economy rewards need Vault and a registered economy provider.
