# Collection Examples

This page gives copy-ready examples for common collection types.

## Oak Log Collection

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
  - id: II
    goal: 100
    rewards:
      - type: COMMAND
        sender: CONSOLE
        command: 'give %player% oak_log 16'
```

## Obsidian Collection

```yaml
id: obsidian
enabled: true
display-name: '&5Obsidian'
description:
  - '&7Mine obsidian to unlock stronger rewards.'
display-item:
  material: OBSIDIAN
menu-slot: 11
sources:
  - type: BLOCK_BREAK
    materials:
      - OBSIDIAN
tiers:
  - id: I
    goal: 25
    rewards:
      - type: MESSAGE
        text: '&dYou reached Obsidian I.'
  - id: II
    goal: 100
    rewards:
      - type: COMMAND
        sender: CONSOLE
        command: 'give %player% obsidian 8'
```

## Mob Kill Collection

```yaml
id: zombie
enabled: true
display-name: '&2Zombie'
description:
  - '&7Kill zombies to climb the collection.'
display-item:
  material: ZOMBIE_HEAD
menu-slot: 12
sources:
  - type: ENTITY_KILL
    entities:
      - ZOMBIE
tiers:
  - id: I
    goal: 25
    rewards:
      - type: MESSAGE
        text: '&aZombie I reached.'
  - id: II
    goal: 250
    rewards:
      - type: COMMAND
        sender: CONSOLE
        command: 'xp add %player% 5 levels'
```

## Fishing Collection

```yaml
id: cod
enabled: true
display-name: '&bCod'
description:
  - '&7Catch cod while fishing.'
display-item:
  material: COD
menu-slot: 13
sources:
  - type: FISH
    materials:
      - COD
tiers:
  - id: I
    goal: 30
    rewards:
      - type: ECONOMY
        amount: 100.0
```

## Crafting Collection

```yaml
id: crafting_tables
enabled: true
display-name: '&eCrafting Tables'
description:
  - '&7Craft crafting tables.'
display-item:
  material: CRAFTING_TABLE
menu-slot: 14
sources:
  - type: CRAFT
    materials:
      - CRAFTING_TABLE
tiers:
  - id: I
    goal: 16
    rewards:
      - type: MESSAGE
        text: '&eCrafting Table I reached.'
```

## Custom Model Data Item

```yaml
id: custom_gem
enabled: true
display-name: '&bCustom Gem'
description:
  - '&7Collect custom gems from another plugin.'
display-item:
  material: DIAMOND
  custom-model-data: 1001
menu-slot: 15
sources:
  - type: ITEM_PICKUP
    materials:
      - DIAMOND
    item-match:
      custom-model-data: 1001
tiers:
  - id: I
    goal: 50
    rewards:
      - type: COMMAND
        sender: CONSOLE
        command: 'customitems give %player% custom_gem_reward 1'
```

## Manual Custom Block Collection

Use this when another plugin owns the custom block logic.

```yaml
id: custom_ore
enabled: true
display-name: '&bCustom Ore'
description:
  - '&7Progress is added by another plugin command.'
display-item:
  material: DIAMOND_ORE
menu-slot: 16
sources:
  - type: MANUAL
tiers:
  - id: I
    goal: 100
    rewards:
      - type: COMMAND
        sender: CONSOLE
        command: 'give %player% diamond 4'
```

External plugin command:

```text
rc add %player% custom_ore 1
```

## Billion-Goal Collection

```yaml
tiers:
  - id: I
    goal: 1000
  - id: X
    goal: 1000000000
```

Large values are supported. Keep rewards readable and test them with `/rc set`.
