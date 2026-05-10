# Commands

Main command:

```text
/ruinedcollections
/rc
```

Player menu command:

```text
/collections
```

## Tab Completion

`/rc` has command-aware tab completion. It suggests subcommands, collections, online players, materials, source types, entity types, tier ids, common goals, export files, and common reward command shapes.

Examples:

```text
/rc create <tab> <tab>
/rc source add <collection> <tab>
/rc reward add-command <collection> <tab>
/rc diagnostics <tab>
```

## Player Commands

```text
/collections
```

Opens the collections menu.

Permission:

```text
ruinedcollections.menu
```

## Admin Commands

### Help

```text
/rc help
```

### Reload

```text
/rc reload
```

Reloads config, collection files, and menus. It also re-checks online players for newly qualified rewards.

Permission:

```text
ruinedcollections.admin.reload
```

### Validate

```text
/rc validate
```

Checks collection files for issues.

### List Collections

```text
/rc list
```

### Collection Info

```text
/rc info <collection>
```

Example:

```text
/rc info oak_log
```

### Open Menu

```text
/rc open [player]
```

### Add Progress

```text
/rc add <player|uuid> <collection> <amount>
```

Example:

```text
/rc add Rique oak_log 50
```

### Set Progress

```text
/rc set <player|uuid> <collection> <amount>
```

Example:

```text
/rc set Rique obsidian 1000
```

### Reset Progress

```text
/rc reset <player|uuid> <collection>
```

### Create Collection

```text
/rc create <id> <material> [display name]
```

Example:

```text
/rc create oak_log OAK_LOG &6Oak Log
```

This creates a new collection file with starter tiers and a block break source.

### Delete Collection

```text
/rc delete <id>
```

This moves the collection file to:

```text
plugins/RuinedCollections/collections/deleted/
```

It does not erase player database rows.

### Add Tier

```text
/rc tier add <collection> <tier> <goal>
```

Example:

```text
/rc tier add oak_log IV 1000
```

### Add Source

```text
/rc source add <collection> <type> [key]
```

Examples:

```text
/rc source add oak_log BLOCK_BREAK OAK_LOG
/rc source add zombie_kills ENTITY_KILL ZOMBIE
/rc source add custom_gem MANUAL
```

### Add Command Reward

```text
/rc reward add-command <collection> <tier> <console|player> <command>
```

Example:

```text
/rc reward add-command oak_log IV console give %player% oak_log 64
```

### Export

```text
/rc export <file>
```

Example:

```text
/rc export before-reset.yml
```

Exports to:

```text
plugins/RuinedCollections/exports/
```

### Import Preview

```text
/rc import <file>
```

Shows how many progress rows, claimed tiers, and saved player names will be imported.

### Import Apply

```text
/rc import <file> --apply
```

Online players touched by the import are refreshed after the import applies.

### Diagnostics

```text
/rc diagnostics
/rc diagnostics path
/rc diagnostics tail [lines]
```

Shows diagnostics status, the diagnostics log path, or recent diagnostics lines.

Permission:

```text
ruinedcollections.admin.diagnostics
```

## Permissions

```text
ruinedcollections.admin
ruinedcollections.admin.reload
ruinedcollections.admin.modify
ruinedcollections.admin.export
ruinedcollections.admin.import
ruinedcollections.admin.diagnostics
ruinedcollections.menu
ruinedcollections.view
```
