# Ruined Ecosystem Parity

RuinedCollections belongs in the same ecosystem as [RuinedWardrobe](https://github.com/Riqqqque/RuinedWardrobe). This page is the compatibility checklist for keeping both plugins familiar to server owners and safe to run together.

## Shared Standards

| Area | RuinedCollections target |
| --- | --- |
| Ownership | Author is `Rique`; license allows free server use and blocks resale. |
| Commands | Predictable player/admin commands, strong tab completion, clear usage on mistakes. |
| Permissions | Explicit player, admin, diagnostics, import/export, and view nodes. |
| Storage | SQLite by default, MySQL for bigger servers, schema versioning, backups, and import/export safety. |
| Diagnostics | Dedicated logs and in-game commands that help support real live-server problems. |
| Placeholders | PlaceholderAPI identifiers use the plugin name and document cache/offline behavior. |
| Docs | Wiki-first, server-owner friendly, checklist-heavy, and practical. |
| Safety | Validate configs, avoid duplicate rewards, preview imports, and keep rollback paths clear. |

## RuinedWardrobe Reference Points

RuinedWardrobe currently provides:

- `/wardrobe` and `/rw` player access with permission-gated subcommands.
- `/wardrobe doctor` for runtime storage/cache/queue/sync diagnostics.
- Snapshot storage migration with `--dry-run`, target overwrite protection, backups, and digest verification.
- Config version guards that back up old templates before regenerating new ones.
- PlaceholderAPI, Vault, and combat integration toggles.
- A dedicated wardrobe audit log for support investigations.
- Public API services and Bukkit events for integrations.
- Paper/Folia scheduler abstraction.

## Compatibility Rules

1. Keep both plugins able to run on the same server without dependency conflicts.
2. Keep PlaceholderAPI identifiers separate: `ruinedcollections` and `ruinedwardrobe`.
3. Keep permissions namespaced and explicit: `ruinedcollections.*` and `ruinedwardrobe.*`.
4. Keep storage tables namespaced and configurable where appropriate.
5. Keep docs language consistent around backups, diagnostics, permissions, and storage safety.
6. Do not force RuinedCollections to match RuinedWardrobe's runtime target unless the project requirement changes. RuinedCollections remains Paper/Folia `1.21` through `26.1.2` and Java `21+`.
7. Borrow proven operational ideas from RuinedWardrobe when they fit, especially dry-run safety, stronger diagnostics, config backup behavior, and public events.

## Current Intentional Differences

| Area | RuinedCollections | RuinedWardrobe |
| --- | --- | --- |
| Build | Maven | Gradle |
| Java | 21+ | 25 |
| Platform | Paper/Folia 1.21 through 26.1.2 | Newer Paper/Folia target |
| Main command | `/ruinedcollections`, `/rc`, `/collections` | `/wardrobe`, `/rw` |
| Data movement | Export/import YAML snapshots | Direct storage migration with digest verification |
| Logging | Rotating diagnostics log | Dated audit log plus doctor command |

These differences are fine unless a specific feature needs convergence.
