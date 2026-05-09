# Architecture

This page explains how RuinedCollections is put together.

```mermaid
flowchart TD
    A["Paper events"] --> B["Tracking listeners"]
    B --> C["Collection registry"]
    C --> D["Progress service"]
    D --> E["Online player cache"]
    D --> F["Batched SQL writes"]
    D --> G["Reward service"]
    G --> H["Claimed tier table"]
    G --> I["Commands, messages, economy"]
    J["Admin commands"] --> C
    J --> D
    J --> K["Import/export service"]
    L["Menus"] --> C
    L --> D
    M["PlaceholderAPI"] --> D
```

## Main Parts

| Part | Job |
| --- | --- |
| `CollectionRegistry` | Loads and validates collection YAML files |
| `ProgressService` | Tracks online progress and batches saves |
| `CollectionRepository` | Handles SQL reads and writes |
| `RewardService` | Claims tiers and runs rewards |
| `MenuService` | Builds collection menus |
| `DataSnapshotService` | Exports and imports data |
| `PlacedBlockService` | Protects block collections from place-break farming |
| `HookManager` | Connects optional plugins |

## Storage Flow

1. A player triggers a tracked action.
2. The listener asks the registry which collections match.
3. Progress is added to the online session.
4. Pending progress is queued.
5. The async flush writes batched progress to SQL.
6. Shutdown flushes pending progress before the plugin disables.

## Reward Flow

1. Progress reaches a tier goal.
2. The service checks whether the tier was already claimed.
3. The repository inserts a claimed-tier row.
4. If the insert succeeds, rewards run on the main server thread.
5. If the row already exists, rewards do not run again.

## Why SQL

SQL gives the plugin:

- safer large-server storage
- clear export/import behavior
- duplicate reward protection
- future migration support
- easy backups

SQLite is simple for smaller servers. MySQL/MariaDB is better for larger servers and networks.
