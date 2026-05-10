# RuinedCollections

[![Release](https://img.shields.io/github/v/release/Riqqqque/RuinedCollections?label=release)](https://github.com/Riqqqque/RuinedCollections/releases/latest)
[![Paper](https://img.shields.io/badge/Paper-1.21%2B-2f3136)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-21%2B-b07219)](https://adoptium.net/)
[![License](https://img.shields.io/badge/license-free%20use%2C%20no%20resale-blue)](LICENSE)

RuinedCollections is a Paper collections plugin built for servers that want Hypixel SkyBlock-style progression without locking admins into one fixed system.

Admins can create collections, set goals from small numbers to billions, track different gameplay actions, run commands, give economy rewards, build menus, use placeholders, and export or import player progress.

## Why Use It

| Need | Built In |
| --- | --- |
| Server-specific collections | YAML collection files and admin edit commands |
| Huge goals | `long` progress values and SQL `BIGINT` storage |
| Rewards | Command, message, broadcast, and Vault economy rewards |
| Custom items | Material, custom model data, display name, and PDC matching |
| Custom blocks | Manual progress and external command workflows |
| Big-server storage | SQLite by default, MySQL/MariaDB for production networks |
| Admin safety | Validation, duplicate reward protection, import preview, exports |
| Menus and scoreboards | Configurable menus and PlaceholderAPI placeholders |

## Download

Latest jar:

[Download RuinedCollections](https://github.com/Riqqqque/RuinedCollections/releases/latest)

## Requirements

- Paper `1.21+`
- Java `21+`
- PlaceholderAPI optional
- Vault plus an economy plugin optional
- LuckPerms optional through command rewards

RuinedCollections compiles against Paper `1.21-R0.1-SNAPSHOT` with `api-version: '1.21'` so it avoids depending on newer 1.21.x-only APIs.

## Quick Start

1. Put the jar in `plugins`.
2. Start the server.
3. Open `plugins/RuinedCollections/collections`.
4. Copy or edit a template.
5. Run `/rc validate`.
6. Run `/rc reload`.
7. Open `/collections`.

Create a simple Oak Log collection:

```text
/rc create oak_log OAK_LOG &6Oak Log
/rc tier add oak_log IV 1000
/rc reward add-command oak_log IV console give %player% oak_log 64
```

Export player data:

```text
/rc export before-reset.yml
```

Preview an import:

```text
/rc import before-reset.yml
```

Apply an import:

```text
/rc import before-reset.yml --apply
```

## Documentation

The full guide is available in the GitHub Wiki:

[RuinedCollections Wiki](https://github.com/Riqqqque/RuinedCollections/wiki)

Local docs are also kept in this repository:

- [Installation](docs/INSTALLATION.md)
- [Configuration](docs/CONFIGURATION.md)
- [Collections](docs/COLLECTIONS.md)
- [Collection Examples](docs/EXAMPLES.md)
- [Commands](docs/COMMANDS.md)
- [Rewards](docs/REWARDS.md)
- [Menus](docs/MENUS.md)
- [Placeholders](docs/PLACEHOLDERS.md)
- [Storage, Import, Export, and Migrations](docs/STORAGE_AND_MIGRATIONS.md)
- [Custom Items and Custom Blocks](docs/CUSTOM_ITEMS_AND_BLOCKS.md)
- [Admin Playbook](docs/ADMIN_PLAYBOOK.md)
- [Performance and Safety](docs/PERFORMANCE_AND_SAFETY.md)
- [Troubleshooting](docs/TROUBLESHOOTING.md)
- [Architecture](docs/ARCHITECTURE.md)

## Build

```powershell
mvn clean package
```

The compiled plugin jar is created at:

```text
target/ruinedcollections-1.0.2.jar
```

## License

RuinedCollections is free to use on personal servers, private servers, public servers, and monetized Minecraft servers.

People may not sell the source code, jar files, modified builds, forks, paid downloads, or paid access to the plugin itself.

See [LICENSE](LICENSE) for the full terms.
