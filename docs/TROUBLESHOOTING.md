# Troubleshooting

Use this page when something does not behave the way you expect.

## Plugin Does Not Load

Check:

- Server is Paper or Folia, not vanilla.
- Server is `1.21+`.
- Java is `21+`.
- The jar is in the server `plugins` folder.

Look for startup errors near:

```text
[RuinedCollections]
```

Then check:

```text
/rc diagnostics path
/rc diagnostics tail 25
```

## Collection Does Not Count Progress

Run:

```text
/rc validate
```

Check the collection file:

- `enabled: true`
- Source type is valid.
- Material or entity name is valid.
- World is not blocked in `config.yml`.
- Player is not in creative or spectator if those modes are ignored.
- For block break collections, the block was not player-placed while placed-block protection is enabled.

If it still does not count, temporarily enable:

```yaml
diagnostics:
  debug:
    tracking-skips: true
```

Run `/rc reload`, reproduce the issue, then check `/rc diagnostics tail 25`.

## Reward Did Not Run

Check:

- Player reached the exact goal or higher.
- Reward type is valid.
- Command does not start with the wrong syntax for that plugin.
- Console command works when run manually.
- Economy rewards have Vault and an economy plugin installed.

Remember: claimed tier rewards only run once per player.

For reward debugging, temporarily enable:

```yaml
diagnostics:
  debug:
    rewards: true
```

## Economy Reward Does Nothing

Check console for Vault hook messages.

Required:

- Vault installed.
- Economy plugin installed.
- Economy plugin registered with Vault.
- Reward amount is above `0`.

## Placeholder Shows Zero

PlaceholderAPI values use online cached player data. Offline player placeholder requests may return zero or empty values.

Check:

- PlaceholderAPI is installed.
- The player is online.
- Collection id is correct.
- Placeholder format is correct.

Example:

```text
%ruinedcollections_progress_oak_log%
```

## Import Did Not Change Online Player Data

After an applied import, online players touched by the import are refreshed automatically.

If something still looks stale:

```text
/rc reload
```

Then reopen menus.

## Menus Look Wrong

Check:

- `menus/collections.yml`
- `display-item.material`
- `menu-slot`
- Inventory size is a multiple of `9`, between `9` and `54`.

Run:

```text
/rc reload
```

## MySQL Cannot Connect

Check:

- Host and port.
- Database exists.
- Username and password.
- User has table create/update permissions.
- Firewall allows the connection.
- `use-ssl` matches your database setup.

Switch back to SQLite temporarily if the server needs to come online fast.

## Duplicate Rewards

Rewards are guarded by SQL claimed-tier rows.

If duplicate rewards happen, check whether:

- The database was manually edited.
- The table prefix changed.
- The same server is using two different databases.
- Multiple plugin copies are installed.

## Commands Say Player Not Found

For offline progress edits, use a UUID when the player is not cached.

```text
/rc set <uuid> oak_log 500
```

## Leaderboard Shows Unknown Names

The plugin saves player names when players join and uses those saved names for offline leaderboard rows. If an older UUID has progress but no saved name yet, it will use `leaderboards.unknown-name-format` until the player joins or an admin edits that player by a cached name.
