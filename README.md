# PinDB

PinDB is a JavaFX personal database application for Linux. Each database is a portable SQLite file with the custom `.pindb` extension. PinDB 0.1 focuses on creating simple databases without requiring users to design SQL tables or install a separate database server.

## Version 0.1 features

- Launcher with new, open, recent, import, restore, update, settings, help, and template options
- Multiple independent database windows
- Text, multiline text, number, currency, date, date/time, Yes/No, and dropdown fields
- Required values, defaults, numeric ranges, unique values, text limits, and dropdown validation
- Drag-and-drop field ordering and safe field-management warnings
- Table and record views remembered by each `.pindb` file
- Add, edit, search, sort, filter, and automatically save entries
- One configurable summary per field: sum, average, minimum, maximum, or count
- Recoverable Recently Deleted area
- Timestamped logical backups stored inside each `.pindb` file, with the newest 10 kept by default
- CSV import and export
- Printable column or row layouts with orientation, headings, field selection, dates, and page numbers
- GitHub Releases updater targeting `mccreeper1318/pindb`
- `.deb` packaging with a bundled Java runtime

Encryption is intentionally deferred to a later release.

## Requirements for development

- JDK 21
- Linux is required to build the `.deb` package
- `fakeroot` is normally required by `jpackage` on Debian-based build systems

PinDB uses the included Gradle wrapper, so a system Gradle installation is not required.

```bash
./gradlew clean test
./gradlew run
./gradlew packageDeb
```

The Debian package is written to `build/packages/`.

## Opening the project in IntelliJ IDEA

1. Clone the repository.
2. Open the repository folder as a Gradle project.
3. Select JDK 21 for the project SDK and Gradle JVM.
4. Run the `org.pindb.PinDBApplication` class or the Gradle `run` task.

## Release and update rules

PinDB accepts release tags in these forms:

- `0.1`
- `v0.1`
- `v.0.1`
- `1.3.65`
- `0.2-beta.1`

Stable update checks ignore pre-releases by default. The setting to include pre-releases is available in the launcher. A GitHub Release must contain a PinDB `.deb` asset. The release workflow also attaches a SHA-256 checksum.

See [the release process](docs/release-process.md) and [update design](docs/update-system.md) for details.

## Database compatibility

New versions may migrate databases created by old PinDB versions. Before a schema migration, PinDB creates an untouched `.pre-migration-<timestamp>.pindb` copy beside the original file. Older PinDB versions are not expected to open databases already migrated by newer versions.

## Project status

Version 0.1 is the first implementation and should be treated as an early release. Keep backups of important information and verify printed or exported data before relying on it for legal or financial records.
