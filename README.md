# RuinedCollections

RuinedCollections is a Paper collections plugin built for servers that want Hypixel SkyBlock-style progression without locking admins into one fixed system.

Admins can create collections, set goals from small numbers to billions, track different gameplay actions, run commands, give economy rewards, build menus, use placeholders, and export or import player progress.

## Status

First public build. The plugin compiles and has been boot-tested on Paper `1.21`.

## Requirements

- Paper `1.21+`
- Java `21+`
- Optional: PlaceholderAPI
- Optional: Vault plus an economy plugin
- Optional: LuckPerms, usually through command rewards

The plugin is compiled against Paper `1.21-R0.1-SNAPSHOT` with `api-version: '1.21'` so it can stay compatible across early 1.21 builds without newer API calls.

## Features

- YAML-based collections
- Default Oak Log and Obsidian collections
- Custom item template
- Manual progress support for custom blocks or external plugins
- Long-based progress values for billion-scale goals
- Block break, entity kill, item pickup, craft, fish, and manual sources
- Player-placed block protection
- SQLite storage by default
- MySQL/MariaDB storage for larger servers
- Schema version table for future migrations
- Batched async progress writes
- Duplicate reward protection
- Command, message, broadcast, and economy rewards
- PlaceholderAPI expansion
- Configurable menus
- Admin commands for creating and editing collections
- Export/import with preview mode

## Build

```powershell
mvn clean package
```

The compiled plugin jar is created at:

```text
target/ruinedcollections-1.0.0.jar
```

## Install

1. Build or download the jar.
2. Put `ruinedcollections-1.0.0.jar` in your server `plugins` folder.
3. Start the server.
4. Edit files in `plugins/RuinedCollections`.
5. Run `/rc reload`.

## Quick Start

Open the player menu:

```text
/collections
```

Create an Oak Log-style collection:

```text
/rc create oak_log OAK_LOG &6Oak Log
```

Add a new tier:

```text
/rc tier add oak_log IV 1000
```

Add a command reward:

```text
/rc reward add-command oak_log IV console give %player% oak_log 64
```

Add progress manually:

```text
/rc add Rique oak_log 50
```

Export data:

```text
/rc export backup.yml
```

Preview an import:

```text
/rc import backup.yml
```

Apply an import:

```text
/rc import backup.yml --apply
```

## Documentation

- [Configuration](docs/CONFIGURATION.md)
- [Collections](docs/COLLECTIONS.md)
- [Commands](docs/COMMANDS.md)
- [Rewards](docs/REWARDS.md)
- [Menus](docs/MENUS.md)
- [Placeholders](docs/PLACEHOLDERS.md)
- [Storage, Import, Export, and Migrations](docs/STORAGE_AND_MIGRATIONS.md)
- [Custom Items and Custom Blocks](docs/CUSTOM_ITEMS_AND_BLOCKS.md)
- [Performance and Safety](docs/PERFORMANCE_AND_SAFETY.md)

## License

RuinedCollections is free to use on personal servers, private servers, public servers, and monetized Minecraft servers.

You may not sell the source code, jar files, modified builds, forks, paid downloads, or paid access to the plugin itself.

See [LICENSE](LICENSE) for the full terms.
