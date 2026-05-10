# Installation

This page covers the normal install path, first boot, and the checks worth doing before a live server uses RuinedCollections.

## Requirements

| Requirement | Notes |
| --- | --- |
| Paper or Folia | `1.21`, `1.21.1`, or `1.21.2` |
| Java | `21+` |
| PlaceholderAPI | Optional, for placeholders |
| Vault | Optional, for economy rewards |
| Economy plugin | Optional, required only if using Vault economy rewards |
| LuckPerms | Optional, usually used through command rewards |

## Install The Jar

1. Download the latest jar from the [releases page](https://github.com/Riqqqque/RuinedCollections/releases/latest).
2. Put the jar in your server `plugins` folder.
3. Start the server.
4. Stop the server after files generate.
5. Edit the generated files.
6. Start the server again.

Generated files:

```text
plugins/RuinedCollections/config.yml
plugins/RuinedCollections/collections/oak_log.yml
plugins/RuinedCollections/collections/obsidian.yml
plugins/RuinedCollections/collections/template_custom_item.yml
plugins/RuinedCollections/menus/collections.yml
```

## First Server Check

Run these commands after first boot:

```text
/rc validate
/rc list
/collections
```

Expected result:

| Command | Expected |
| --- | --- |
| `/rc validate` | No collection issues |
| `/rc list` | Default collections show |
| `/collections` | Menu opens |

## Recommended Production Setup

For a small server, SQLite is fine.

For a large server, public SMP, SkyBlock server, or network, use MySQL or MariaDB.

```yaml
storage:
  type: mysql
  table-prefix: rc_
  mysql:
    host: localhost
    port: 3306
    database: minecraft
    username: root
    password: ''
    use-ssl: false
    pool-size: 10
```

Before switching storage on a real server:

1. Run `/rc export before-storage-change.yml`.
2. Stop the server.
3. Back up `plugins/RuinedCollections`.
4. Change storage settings.
5. Start the server.
6. Run `/rc import before-storage-change.yml --apply`.
7. Check player progress.

## Updating

1. Run `/rc export before-update.yml`.
2. Stop the server.
3. Back up the plugin folder.
4. Replace the jar.
5. Start the server.
6. Watch console for storage messages.
7. Run `/rc validate`.

## Build From Source

```powershell
mvn clean package
```

Output:

```text
target/RuinedCollections.jar
```
