# Custom Items and Custom Blocks

Minecraft plugins store custom items and blocks in different ways. RuinedCollections supports the common safe pieces directly and lets commands cover the rest.

## Custom Model Data

```yaml
sources:
  - type: ITEM_PICKUP
    materials:
      - DIAMOND
    item-match:
      custom-model-data: 1001
```

This tracks a custom item that is still technically a diamond but has model data `1001`.

## Display Name Match

```yaml
sources:
  - type: ITEM_PICKUP
    materials:
      - DIAMOND
    item-match:
      display-name: '&bCustom Gem'
```

Display name matching strips colors before comparing.

## Persistent Data Match

If another plugin stores a public persistent data key, match it like this:

```yaml
sources:
  - type: ITEM_PICKUP
    materials:
      - DIAMOND
    item-match:
      persistent-data:
        - key: otherplugin:item_id
          type: STRING
          value: custom_gem
```

Supported persistent data types:

```text
STRING
INTEGER
LONG
DOUBLE
```

## Custom Block Plugins

There is no single Paper API that identifies every custom block from every plugin.

Use one of these options:

### Manual Progress

```yaml
sources:
  - type: MANUAL
```

Then use:

```text
/rc add <player> <collection> <amount>
```

### External Plugin Commands

If the custom block plugin can run commands when a block is broken, make it run:

```text
rc add %player% custom_block_collection 1
```

### Reward Commands

If the custom item plugin has a give command, rewards can run it.

```yaml
rewards:
  - type: COMMAND
    sender: CONSOLE
    command: 'itemsadder give %player% custom_gem 1'
```

Replace the command with the real command for the custom item plugin.

## Practical Template

The plugin ships:

```text
collections/template_custom_item.yml
```

Copy it, change `id`, enable it, and adjust the material/model/data checks.
