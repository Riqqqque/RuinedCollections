# Admin Playbook

This page is for server owners and staff who need practical workflows.

## Create A New Collection Safely

1. Make the collection file or run `/rc create`.
2. Run `/rc validate`.
3. Test it with one admin account.
4. Use `/rc set <player> <collection> <amount>` to test tier unlocks.
5. Check rewards only run once.
6. Run `/rc reload` after final edits.

Fast command flow:

```text
/rc create oak_log OAK_LOG &6Oak Log
/rc tier add oak_log IV 1000
/rc reward add-command oak_log IV console give %player% oak_log 64
/rc validate
/rc reload
```

## Change A Tier Goal

If you raise a goal, players may no longer visually qualify for that tier, but already claimed rewards stay claimed.

If you lower a goal, online players are rechecked after `/rc reload`.

Recommended:

1. Export first.
2. Change the goal.
3. Run `/rc validate`.
4. Run `/rc reload`.
5. Watch console.

```text
/rc export before-tier-change.yml
/rc validate
/rc reload
```

## Give A Player Progress

```text
/rc add <player|uuid> <collection> <amount>
```

Example:

```text
/rc add Rique oak_log 500
```

## Fix A Player's Progress

```text
/rc set <player|uuid> <collection> <amount>
```

Example:

```text
/rc set Rique obsidian 2500
```

## Reset A Player

```text
/rc reset <player|uuid> <collection>
```

This sets progress to `0`. It does not remove claimed tier records in the first public build, so previously paid rewards stay protected from duplicate payout.

## Back Up Before Big Changes

```text
/rc export before-big-change.yml
```

Keep the export somewhere safe before changing storage, replacing collection ids, or importing old data.

## Move From SQLite To MySQL

1. `/rc export sqlite-backup.yml`
2. Stop server.
3. Change `storage.type` to `mysql`.
4. Fill in MySQL settings.
5. Start server.
6. `/rc import sqlite-backup.yml`
7. If preview looks right, `/rc import sqlite-backup.yml --apply`

## Add A LuckPerms Reward

Permission reward:

```yaml
rewards:
  - type: COMMAND
    sender: CONSOLE
    command: 'lp user %player% permission set ruinedcollections.oak_log_iv true'
```

Group reward:

```yaml
rewards:
  - type: COMMAND
    sender: CONSOLE
    command: 'lp user %player% parent add collector'
```

## Add A Custom Item Reward

Use the other plugin's give command.

```yaml
rewards:
  - type: COMMAND
    sender: CONSOLE
    command: 'customitems give %player% custom_gem_reward 1'
```

## Launch Checklist

| Check | Command/File |
| --- | --- |
| Collections validate | `/rc validate` |
| Menu opens | `/collections` |
| Rewards fire once | `/rc set <player> <collection> <goal>` |
| Export works | `/rc export launch-test.yml` |
| Storage selected | `config.yml` |
| Economy hooked if needed | Console on startup |
| PlaceholderAPI hooked if needed | Console on startup |
