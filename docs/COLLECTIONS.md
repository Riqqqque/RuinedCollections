# Collections

Collection files live here:

```text
plugins/RuinedCollections/collections/
```

Each collection gets its own YAML file. The file name does not have to match the id, but keeping them the same makes life easier.

## Basic Collection

```yaml
id: oak_log
enabled: true
set: blocks
permission: ''
hidden-until-progress: false
display-name: '&6Oak Log'
menu-title: '&8Oak Log Collection'
description:
  - '&7Collect oak logs by chopping trees.'
display-item:
  material: OAK_LOG
menu-slot: 10
sources:
  - type: BLOCK_BREAK
    materials:
      - OAK_LOG
tiers:
  - id: I
    goal: 50
    rewards:
      - type: MESSAGE
        text: '&aYou reached Oak Log I.'
      - type: COMMAND
        sender: CONSOLE
        command: 'give %player% oak_log 16'
```

## Id Rules

Collection ids must use 1-64 lowercase letters, numbers, `_`, and `-`.

Good:

```text
oak_log
obsidian
custom_gem
nether-star
```

Bad:

```text
Oak Log
oak log
oak/log
```

## Display Item

```yaml
display-item:
  material: DIAMOND
  custom-model-data: 1001
```

`custom-model-data` is optional. It is useful for resource-pack items.

## Sets, Permission, And Visibility

Sets group collections for menus, placeholders, and set leaderboards.

```yaml
set: blocks
```

Set ids use the same format as collection ids: lowercase letters, numbers, `_`, and `-`.

Set `permission` to lock a collection behind a LuckPerms or Bukkit permission node. Locked collections are hidden from the menu and automatic tracking will not add progress until the player has the node. Admin/manual progress edits still work.

```yaml
permission: ruinedcollections.collection.oak_log
```

Use `hidden-until-progress` when you want a collection to stay out of the player menu until the player has at least 1 progress. Admins with `ruinedcollections.admin` can still see it.

```yaml
hidden-until-progress: true
```

`menu-title` overrides the detail menu title for one collection. Leave it blank to use `menus.detail-title` from `config.yml`.

```yaml
menu-title: '&8Oak Log Collection'
```

## Sources

Sources decide what gives progress.

Automatic source types must list `materials` or `entities`. To intentionally match everything for that source, use `*`.

### Block Break

```yaml
sources:
  - type: BLOCK_BREAK
    materials:
      - OAK_LOG
```

### Entity Kill

```yaml
sources:
  - type: ENTITY_KILL
    entities:
      - ZOMBIE
      - SKELETON
```

### Item Pickup

```yaml
sources:
  - type: ITEM_PICKUP
    materials:
      - DIAMOND
```

### Craft

```yaml
sources:
  - type: CRAFT
    materials:
      - CRAFTING_TABLE
```

### Fish

```yaml
sources:
  - type: FISH
    materials:
      - COD
      - SALMON
```

### Armor Equip

```yaml
sources:
  - type: ARMOR_EQUIP
    materials:
      - LEATHER_CHESTPLATE
```

This counts when the server confirms the player equipped a new armor item. It supports normal armor and custom armor items that another plugin stores on a normal Bukkit `ItemStack`.

### Manual

```yaml
sources:
  - type: MANUAL
```

Manual sources do not listen to a Bukkit event. They are for admin commands, external plugins, command blocks, or future plugin hooks.

### Explicit Wildcards

```yaml
sources:
  - type: ENTITY_KILL
    entities:
      - '*'
```

Use wildcards carefully. A wildcard block, pickup, craft, fish, or armor source checks every matching event for that source type. If `*` is mixed with named materials or entities, the wildcard takes precedence.

## Custom Amounts

Each source can multiply progress.

```yaml
sources:
  - type: ENTITY_KILL
    entities:
      - WITHER
    amount: 100
```

Killing one Wither gives 100 progress.

## Tiers

Tiers are ordered by goal. Goals use `long`, so billions are fine. Tier ids must use 1-64 letters, numbers, `_`, and `-`.

```yaml
tiers:
  - id: I
    goal: 50
  - id: II
    goal: 100
  - id: III
    goal: 1000000000
```

Rewards are only claimed once per player per collection tier.

By default, progress is capped at the highest tier goal. Disable `progress.cap-at-highest-tier` in `config.yml` if you intentionally want stored progress to go beyond the final reward tier.

## Validation

Run this after editing collection files:

```text
/rc validate
```

It catches invalid materials, missing material/entity lists, invalid source types, duplicate ids, invalid tiers, and bad reward types.
