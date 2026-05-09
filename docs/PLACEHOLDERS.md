# PlaceholderAPI

Install PlaceholderAPI to use these placeholders in other plugins.

Identifier:

```text
ruinedcollections
```

## Progress

Formatted progress:

```text
%ruinedcollections_progress_<collection>%
```

Example:

```text
%ruinedcollections_progress_oak_log%
```

Raw progress:

```text
%ruinedcollections_raw_progress_<collection>%
```

## Next Goal

```text
%ruinedcollections_next_goal_<collection>%
```

Example:

```text
%ruinedcollections_next_goal_obsidian%
```

## Remaining

```text
%ruinedcollections_remaining_<collection>%
```

## Percent

```text
%ruinedcollections_percent_<collection>%
```

Returns `0` to `100`.

## Tier Count

```text
%ruinedcollections_tier_<collection>%
```

Returns how many tiers the player has unlocked.

## Notes

Placeholders use cached online player data. Offline placeholder requests return empty or zero values because the plugin does not load every offline player into memory.
