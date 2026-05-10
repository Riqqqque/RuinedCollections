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

Examples:

```text
%ruinedcollections_top_oak_log_1_name%
%ruinedcollections_top_oak_log_1_progress%
%ruinedcollections_top_oak_log_2_raw%
%ruinedcollections_top_obsidian_3_uuid%
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

## Notes

Placeholders use cached online player data. Offline placeholder requests return empty or zero values because the plugin does not load every offline player into memory.

Leaderboard top placeholders do not require an online player. Player rank placeholders require a player context.
