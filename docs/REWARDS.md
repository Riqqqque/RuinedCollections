# Rewards

Rewards run when a player reaches a tier goal. Each reward is only paid once per player per tier.

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

If Vault is missing or no economy provider is registered, the reward is skipped and a warning is logged.

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
