# Storage, Import, Export, and Migrations

RuinedCollections stores player data in SQL.

SQLite default file:

```text
plugins/RuinedCollections/data.db
```

Tables:

```text
rc_schema
rc_player_progress
rc_claimed_tiers
rc_placed_blocks
```

The table prefix comes from:

```yaml
storage:
  table-prefix: rc_
```

## Player Progress

Progress is stored by player UUID and collection id.

```text
player_uuid
collection_id
progress
updated_at
```

Progress values are `BIGINT` and are clamped in code to avoid overflow.

## Claimed Tiers

Claimed tiers prevent duplicate rewards.

```text
player_uuid
collection_id
tier_id
claimed_at
```

The primary key is player UUID + collection id + tier id.

## Placed Blocks

Placed block records stop players from placing tracked blocks and breaking them for collection progress.

```text
world
x
y
z
material
created_at
```

## Export

```text
/rc export backup.yml
```

Exports to:

```text
plugins/RuinedCollections/exports/backup.yml
```

Exports include:

- format version
- export timestamp
- player UUIDs
- collection progress
- claimed tiers

## Import Preview

```text
/rc import backup.yml
```

Preview mode shows how many rows will be imported.

## Import Apply

```text
/rc import backup.yml --apply
```

Import sets progress to the values in the file and inserts claimed tiers if missing.

## Migration Design

The plugin creates `rc_schema` with a version number. Future builds can use this table to apply database migrations safely.

Recommended server practice:

1. Stop the server.
2. Back up the database.
3. Update the jar.
4. Start the server.
5. Check console for migration messages.
6. Run `/rc validate`.

## Moving From SQLite to MySQL

Current safe path:

1. Run `/rc export sqlite-backup.yml`.
2. Stop the server.
3. Change `storage.type` to `mysql`.
4. Fill in MySQL credentials.
5. Start the server.
6. Run `/rc import sqlite-backup.yml`.
7. Confirm player data.

Keep the SQLite file until the MySQL data is verified.
