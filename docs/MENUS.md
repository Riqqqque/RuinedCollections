# Menus

Menu config path:

```text
plugins/RuinedCollections/menus/collections.yml
```

Main menu title and button materials are in `config.yml`.

## Main Menu

```yaml
main:
  size: 54
  filler:
    enabled: true
    material: BLACK_STAINED_GLASS_PANE
    name: ' '
  collection-lore:
    - '&7Progress: &f%progress%&7/&f%next_goal%'
    - '&7Unlocked tiers: &f%tier%'
    - ''
    - '&eClick to view rewards.'
```

Collection items come from each collection file:

```yaml
display-item:
  material: OAK_LOG
menu-slot: 10
```

If `menu-slot` is `-1`, the plugin places the collection in the normal content grid.

## Detail Menu

```yaml
detail:
  size: 54
  filler:
    enabled: true
    material: BLACK_STAINED_GLASS_PANE
    name: ' '
  tier-lore:
    locked:
      - '&7Goal: &f%goal%'
      - '&7Progress: &f%progress%&7/&f%goal%'
      - ''
      - '&cLocked'
    unlocked:
      - '&7Goal: &f%goal%'
      - ''
      - '&aUnlocked'
```

## Menu Placeholders

Main menu collection lore:

```text
%progress%
%next_goal%
%tier%
%collection%
%collection_id%
```

Detail menu tier lore:

```text
%goal%
%progress%
%collection%
%tier%
```

PlaceholderAPI placeholders also work when PlaceholderAPI is installed.
