# PinDB 0.1 user guide

## Launcher

Use **Create New Database** for the guided field setup. **Database Templates** starts the same wizard with common fields already added. **Open Database** loads an existing `.pindb` file. **Import CSV** creates a new `.pindb` file from the CSV header and rows. **Restore Backup** opens a database's internal snapshot list.

## Entries

The **+** button opens a separate entry window. Double-click an entry or select it and use **Edit** to change it. The **−** button moves the selected entry to Recently Deleted. The confirmation can be disabled independently for each database.

## Views and finding information

Table view displays fields as columns. Record view displays each entry as a vertical field list. The selected view is remembered inside the database. Search checks every field. Filter supports date ranges, numeric ranges, dropdowns, and Yes/No values.

## Fields and summaries

Use **Fields…** to add, edit, reorder, or delete fields after creation. Deleting a field removes all current values for that field and requires confirmation. One summary may be selected per field in version 0.1.

## Backups and trash

PinDB automatically creates timestamped snapshots before data-changing operations and retains 10 by default. Snapshots are kept inside the `.pindb` file. Recently Deleted entries may be restored or permanently removed.

## Printing and CSV

Printing can arrange fields as columns or rows, use portrait or landscape orientation, repeat headings, select fields, and include database name, print date, and page numbers. CSV export uses the entries currently visible after search and filters.
