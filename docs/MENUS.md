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
    - '&7Set: &f%set%'
    - '&7Progress: &f%progress%&7/&f%next_goal% &8(%percent%%)'
    - '%progress_bar%'
    - '&7Unlocked tiers: &f%tier%&7/&f%total_tiers%'
    - '&7Max goal: &f%max_goal%'
    - ''
    - '&eClick to view rewards.'
progress-bar:
  length: 20
  filled: '&a|'
  empty: '&7|'
```

Collection items come from each collection file:

```yaml
display-item:
  material: OAK_LOG
menu-slot: 10
set: blocks
permission: ''
hidden-until-progress: false
```

If `menu-slot` is `-1`, the plugin places the collection in the normal content grid.

Collections with a permission are hidden from players without that permission. Collections with `hidden-until-progress: true` are hidden until the player has progress, unless the player has `ruinedcollections.admin`.

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
      - '&7Progress: &f%progress%&7/&f%goal% &8(%percent%%)'
      - '%progress_bar%'
      - ''
      - '&cLocked'
    unlocked:
      - '&7Goal: &f%goal%'
      - ''
      - '&aUnlocked'
```

The detail menu paginates tiers automatically. If a collection has more tiers than fit in the content grid, previous and next buttons use the materials configured in `config.yml`.

Menu page changes are scheduled through the platform scheduler. This keeps menu navigation safe on Paper and Folia instead of opening a new inventory directly inside a click event.

## Menu Placeholders

Main menu collection lore:

```text
%progress%
%raw_progress%
%next_goal%
%goal%
%max_goal%
%remaining%
%percent%
%max_percent%
%progress_bar%
%max_progress_bar%
%tier%
%tier_count%
%total_tiers%
%maxed%
%collection%
%collection_id%
%set%
```

Detail menu tier lore:

```text
%goal%
%progress%
%raw_progress%
%remaining%
%percent%
%progress_bar%
%collection%
%collection_id%
%set%
%tier%
```

PlaceholderAPI placeholders also work when PlaceholderAPI is installed.
