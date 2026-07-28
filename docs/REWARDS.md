# Rewards

Rewards run when a player reaches a tier goal. Each reward is only paid once per player per tier.

RuinedCollections stores the claim before running economy or command rewards. This prevents duplicate payouts when events repeat or a player reconnects. A forced process crash in the narrow gap between those two steps can leave the tier claimed before an external reward finishes; external commands and economy transactions cannot share the plugin's SQL transaction. Check diagnostics after an unclean shutdown and manually run the missing reward when necessary.

If a player disconnects before reward execution starts, the claim is rolled back asynchronously and any fast reconnect is refreshed after that rollback. Exceptions from individual reward actions are captured in diagnostics without preventing later actions in the same tier from being attempted.

Reward placeholders:

```text
%player%
%uuid%
%collection%
%collection_id%
%tier%
%goal%
```

If PlaceholderAPI is installed, command and message text also supports PlaceholderAPI placeholders.

## Message Reward

```yaml
rewards:
  - type: MESSAGE
    text: '&aYou reached Oak Log I.'
```

Sends a private message to the player.

## Broadcast Reward

```yaml
rewards:
  - type: BROADCAST
    text: '&d%player% reached Obsidian III.'
```

Broadcasts to the server.

## Command Reward

```yaml
rewards:
  - type: COMMAND
    sender: CONSOLE
    command: 'give %player% oak_log 16'
```

Console commands are best for most rewards.

Player command:

```yaml
rewards:
  - type: COMMAND
    sender: PLAYER
    command: 'kit collection_reward'
```

## Economy Reward

Requires Vault and an economy plugin.

```yaml
rewards:
  - type: ECONOMY
    amount: 250.0
```

If Vault is missing or no economy provider is registered, the reward is skipped and a warning is logged. The tier stays claimed to prevent other actions in the same tier from being duplicated, so test economy rewards before using them in production.

## LuckPerms Rewards

LuckPerms is best handled through command rewards.

Give a permission:

```yaml
rewards:
  - type: COMMAND
    sender: CONSOLE
    command: 'lp user %player% permission set ruinedcollections.oak_log_ii true'
```

Add a group:

```yaml
rewards:
  - type: COMMAND
    sender: CONSOLE
    command: 'lp user %player% parent add collector'
```

Add to a track:

```yaml
rewards:
  - type: COMMAND
    sender: CONSOLE
    command: 'lp user %player% promote collections'
```

## Custom Plugin Rewards

If another plugin has a give command, use it.

```yaml
rewards:
  - type: COMMAND
    sender: CONSOLE
    command: 'customitems give %player% custom_gem_reward 1'
```

This is the cleanest universal support for custom items because every custom item plugin stores data differently.
