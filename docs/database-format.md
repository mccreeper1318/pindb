# PinDB database format

A `.pindb` file is a SQLite database. The custom extension identifies files intended for PinDB while retaining SQLite's transactional and portability benefits.

## Schema version 1

The `pindb_meta` table stores the database name, description, schema version, default view, delete-confirmation preference, backup limit, and print preferences.

Field definitions are stored in `field_definitions`. Records use a stable row in `records` and field values in `record_values`. Removing a field cascades its values from current records.

Recently deleted entries remain in `records` with a `deleted_at` timestamp until restored or permanently removed.

Logical snapshots are stored in the `backup_*` tables. They include metadata, fields, records, deleted records, and values. The default retention limit is 10 snapshots.

## Migrations

PinDB reads `schema_version` before opening a database. A file created by a newer unsupported schema is rejected without modification. Before an older schema is upgraded, an untouched copy is placed beside the original with a `.pre-migration-<timestamp>.pindb` suffix.
