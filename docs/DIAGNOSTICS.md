# Diagnostics

RuinedCollections writes a dedicated diagnostics file so server owners and developers can see what the plugin tried to do, what it skipped, and why something failed.

Default path:

```text
plugins/RuinedCollections/logs/diagnostics.log
```

## What Gets Logged

Always logged:

- plugin startup, shutdown, and reload
- detected server platform, Minecraft version, Bukkit version, and support range
- collection validation warnings
- storage load/save failures
- import and export failures
- reward claim failures
- economy reward failures
- reward commands that were empty or not handled
- missing import files
- placed-block cache failures
- hooked optional plugins

Debug logged when enabled:

- tracking skips from creative, spectator, or blocked worlds
- player-placed block breaks ignored by collection tracking
- item pickup events where no item amount was actually collected
- duplicate reward claims already in progress or already claimed
- command-side rejections while admins are editing collections

## Config

```yaml
diagnostics:
  enabled: true
  file: logs/diagnostics.log
  mirror-warnings-to-console: true
  include-stack-traces: true
  max-file-size-mb: 8
  max-archives: 5
  debug:
    tracking-skips: false
    progress: false
    rewards: false
    commands: false
```

`mirror-warnings-to-console` keeps important warnings in the normal server console while still writing the full context to the diagnostics file.

`max-file-size-mb` and `max-archives` rotate the diagnostics file so it does not grow forever. With the defaults, the plugin keeps `diagnostics.log` plus up to five archives.

## Commands

Show diagnostics status, detected server version, support range, and file path:

```text
/rc diagnostics
```

Show only the file path:

```text
/rc diagnostics path
```

Show recent lines in-game:

```text
/rc diagnostics tail
/rc diagnostics tail 25
```

The tail command caps at 50 lines so it stays readable in chat.

## Debugging A Problem

1. Run `/rc diagnostics path`.
2. Reproduce the issue.
3. Run `/rc diagnostics tail 25`.
4. If tracking or rewards are the issue, enable only the matching debug category.
5. Run `/rc reload`.
6. Reproduce the issue again.
7. Turn debug back off after you are done.

Tracking debug can be noisy on active servers because every skipped collection event may write a line.
