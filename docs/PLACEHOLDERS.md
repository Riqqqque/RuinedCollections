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

Highest configured tier goal:

```text
%ruinedcollections_max_goal_<collection>%
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

Returns progress toward the next tier from `0` to `100`.

Progress toward the final tier:

```text
%ruinedcollections_max_percent_<collection>%
```

## Progress Bars

Next-tier progress bar:

```text
%ruinedcollections_progressbar_<collection>%
```

Final-tier progress bar:

```text
%ruinedcollections_max_progressbar_<collection>%
```

The bar style is configured in `placeholders.progress-bar` in `config.yml`.

## Tier Count

```text
%ruinedcollections_tier_<collection>%
```

Returns how many tiers the player has unlocked.

Total tiers:

```text
%ruinedcollections_total_tiers_<collection>%
```

Whether the player has reached the final tier:

```text
%ruinedcollections_maxed_<collection>%
```

Returns `yes` or `no`.

## Collection Sets

Set placeholders use the `set` field in collection files.

Total progress across a set:

```text
%ruinedcollections_set_progress_<set>%
%ruinedcollections_set_raw_progress_<set>%
```

Set completion:

```text
%ruinedcollections_set_percent_<set>%
%ruinedcollections_set_progressbar_<set>%
```

Collection counts:

```text
%ruinedcollections_set_maxed_<set>%
%ruinedcollections_set_total_<set>%
```

Examples:

```text
%ruinedcollections_set_progress_blocks%
%ruinedcollections_set_percent_blocks%
%ruinedcollections_set_maxed_blocks%
```

## Leaderboards

Leaderboard placeholders are cached so scoreboard plugins can use them without querying the database every refresh.

Top player name:

```text
%ruinedcollections_top_<collection>_<position>_name%
%ruinedcollections_leaderboard_<collection>_<position>_name%
```

Top player progress:

```text
%ruinedcollections_top_<collection>_<position>_progress%
%ruinedcollections_top_<collection>_<position>_raw%
%ruinedcollections_top_<collection>_<position>_raw_progress%
```

Top player UUID:

```text
%ruinedcollections_top_<collection>_<position>_uuid%
```

Set top player:

```text
%ruinedcollections_set_top_<set>_<position>_name%
%ruinedcollections_set_top_<set>_<position>_progress%
%ruinedcollections_set_top_<set>_<position>_raw%
%ruinedcollections_set_top_<set>_<position>_uuid%
%ruinedcollections_set_leaderboard_<set>_<position>_name%
```

Examples:

```text
%ruinedcollections_top_oak_log_1_name%
%ruinedcollections_top_oak_log_1_progress%
%ruinedcollections_top_oak_log_2_raw%
%ruinedcollections_top_obsidian_3_uuid%
%ruinedcollections_set_top_blocks_1_name%
%ruinedcollections_set_top_blocks_1_progress%
```

Player rank in a collection:

```text
%ruinedcollections_rank_<collection>%
%ruinedcollections_raw_rank_<collection>%
%ruinedcollections_leaderboard_rank_<collection>%
%ruinedcollections_leaderboard_raw_rank_<collection>%
```

Examples:

```text
%ruinedcollections_rank_oak_log%
%ruinedcollections_raw_rank_obsidian%
```

Rank placeholders use the player supplied by PlaceholderAPI. The first formatted rank request may return the configured loading value while the rank is loaded async. Raw rank placeholders return `0` until the cache is ready.

Top collection and set leaderboard names use a last-known player name saved when the player joins. This keeps names stable after the player logs out and avoids depending on Bukkit's offline player name cache. If a UUID has progress but no saved name yet, the name placeholder uses `leaderboards.unknown-name-format`.

## Notes

Progress, next goal, remaining, percent, tier, maxed, and set completion placeholders use cached online player data. Offline requests for those values return empty or zero because the plugin does not load every offline player into memory.

Collection and set leaderboard top placeholders do not require an online player. Player rank placeholders work for online or offline player contexts as long as PlaceholderAPI supplies a UUID.
