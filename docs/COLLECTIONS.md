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
display-name: '&6Oak Log'
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

Collection ids should use lowercase letters, numbers, `_`, and `-`.

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

## Sources

Sources decide what gives progress.

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

### Manual

```yaml
sources:
  - type: MANUAL
```

Manual sources do not listen to a Bukkit event. They are for admin commands, external plugins, command blocks, or future plugin hooks.

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

Tiers are ordered by goal. Goals use `long`, so billions are fine.

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

## Validation

Run this after editing collection files:

```text
/rc validate
```

It catches invalid materials, invalid source types, duplicate ids, invalid tiers, and bad reward types.
