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

Use a unique `table-prefix` if multiple plugins or test servers share one database.

## Progress Saving

```yaml
progress:
  flush-interval-seconds: 15
  load-on-join: true
  save-on-quit: true
```

Progress is cached in memory and flushed in batches. Lower flush intervals write more often. Higher intervals write less often but keep more unsaved progress in memory between flushes.

The plugin always flushes pending progress on shutdown.

## Tracking Rules

```yaml
tracking:
  ignore-creative: true
  ignore-spectator: true
  ignore-player-placed-blocks: true
  disabled-worlds: []
  enabled-worlds: []
```

`ignore-player-placed-blocks` protects block collections from place-and-break farming. It tracks placed blocks in storage so restarts do not clear the protection.

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
