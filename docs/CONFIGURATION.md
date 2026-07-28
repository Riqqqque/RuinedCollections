# Configuration

Main config path:

```text
plugins/RuinedCollections/config.yml
```

Reload after edits:

```text
/rc reload
```

## Storage

SQLite is the default and is best for small or single-server setups.

```yaml
storage:
  type: sqlite
  table-prefix: rc_
  sqlite:
    file: data.db
```

MySQL is better for bigger servers or setups where database backups and external tooling matter.

```yaml
storage:
  type: mysql
  table-prefix: rc_
  mysql:
    host: localhost
    port: 3306
    database: minecraft
    username: root
    password: ''
    use-ssl: false
    pool-size: 10
```

Use a unique `table-prefix` if multiple plugins or test servers share one database. Prefixes must use 1-32 lowercase letters, numbers, or underscores. Invalid `storage.type` or `table-prefix` values stop startup with a clear storage error instead of silently using another database or prefix.

## Progress Saving

```yaml
progress:
  flush-interval-seconds: 15
  load-on-join: true
  save-on-quit: true
  cap-at-highest-tier: true
```

Progress is cached in memory and flushed in batches. Lower flush intervals write more often. Higher intervals write less often but keep more unsaved progress in memory between flushes.

The plugin flushes the quitting player's pending progress when `save-on-quit` is enabled and always flushes all pending progress on shutdown.

`cap-at-highest-tier` keeps stored progress from going above the highest configured tier goal. It applies to live tracking, queued saves, offline manual adds, and admin set/add writes. Turn it off only if your server intentionally wants overflow progress after the final tier.

## Diagnostics

```yaml
diagnostics:
  enabled: true
  file: logs/diagnostics.log
  mirror-warnings-to-console: true
  include-stack-traces: true
  max-file-size-mb: 8
  max-archives: 5
  debug:
    tracking-skips: false
    progress: false
    rewards: false
    commands: false
```

Diagnostics write detailed plugin events to `plugins/RuinedCollections/logs/diagnostics.log` by default.

Keep debug categories off unless you are actively investigating a problem. `tracking-skips` can be noisy on busy servers.

## Leaderboards

```yaml
leaderboards:
  enabled: true
  refresh-interval-seconds: 60
  entries-per-collection: 10
  rank-cache-seconds: 30
  rank-cache-max-entries: 10000
  empty-name: '-'
  empty-value: '0'
  loading-value: '...'
  unknown-name-format: 'Unknown-%short_uuid%'
```

Leaderboard placeholders use a cache so PlaceholderAPI scoreboards do not query storage every tick.

`entries-per-collection` controls how many top players are cached per collection. `rank-cache-seconds` controls how long player rank placeholders stay cached before being refreshed async. `rank-cache-max-entries` bounds the number of per-player rank results held in memory.

`unknown-name-format` is used only when a leaderboard row has a UUID but the plugin has never stored a player name for it. `%uuid%` and `%short_uuid%` are supported. Normal offline players use the last name saved when they joined the server.

Set leaderboard placeholders use the same cache interval and entry limit. They aggregate all enabled collections with the same `set` value.

## Placeholder Bars

```yaml
placeholders:
  progress-bar:
    length: 20
    filled: '&a|'
    empty: '&7|'
```

These values control PlaceholderAPI progress-bar placeholders. Menu progress bars have their own defaults in `menus/collections.yml`.

## bStats

```yaml
integrations:
  bstats:
    plugin-id: 31969
```

`31969` is the RuinedCollections plugin id on bStats.

When this is set to a positive id, bStats starts on server boot. If the node is missing in an older config, RuinedCollections uses the bundled default id `31969`. Setting it to `0` disables bStats, and `/rc reload` applies that change without needing a full restart.

RuinedCollections only sends aggregate bStats charts:

- storage type
- leaderboard enabled state
- PlaceholderAPI, Vault, and LuckPerms hook state
- enabled collection count
- enabled source type counts

It does not send player names, UUIDs, IPs, server addresses, commands, collection ids, reward commands, database credentials, or free-text config values. Server owners can still use the normal bStats global opt-out in the server's `plugins/bStats/config.yml`.

## Tracking Rules

```yaml
tracking:
  ignore-creative: true
  ignore-spectator: true
  ignore-player-placed-blocks: true
  placed-block-flush-interval-seconds: 1
  disabled-worlds: []
  enabled-worlds: []
```

`ignore-player-placed-blocks` protects block collections from place-and-break farming. It tracks placed blocks in storage so restarts do not clear the protection. Changes are immediate in memory and grouped into SQL transactions using `placed-block-flush-interval-seconds`. Records move with piston-moved blocks and are cleared for explosions, burning, fading, and leaf decay.

Use `enabled-worlds` to allow tracking only in specific worlds. Use `disabled-worlds` to block specific worlds.

## Menus

```yaml
menus:
  main-title: '&8Collections'
  detail-title: '&8%collection%'
  previous-item: ARROW
  next-item: ARROW
  back-item: BARRIER
```

Menu layout and lore live in:

```text
plugins/RuinedCollections/menus/collections.yml
```

## Messages

All main command and unlock messages can be changed in `messages`.

Color codes use `&`.

```yaml
messages:
  prefix: '&8[&6RuinedCollections&8]&r '
  no-permission: '&cYou do not have permission.'
  tier-unlocked: '&aCollection unlocked: &f%collection% %tier%&a!'
```
