# PinDB

PinDB is a desktop personal database application for organizing structured information without writing SQL or running a separate database server.

Each database is stored as a portable SQLite file with the `.pindb` extension. A single file contains its field definitions, entries, display preferences, deleted records, internal backups, and embedded documents.

PinDB is designed primarily for Linux Mint, Ubuntu, Debian, and other Debian-based Linux distributions.

## Download and install

Download the newest `.deb` installer from the [PinDB Releases](https://github.com/mccreeper1318/pindb/releases) page.

The installer includes a private Java runtime, so Java does not need to be installed separately for normal use.

### Install with a graphical package manager

1. Download the PinDB `.deb` file.
2. Double-click the downloaded file.
3. Open it with Software Manager, Package Installer, or another Debian package installer.
4. Select **Install** and enter the administrator password when requested.
5. Launch **PinDB** from the application menu.

### Install from a terminal

Open a terminal in the folder containing the downloaded package and run:

```bash
sudo apt install ./pindb_*_amd64.deb
```

Installing a newer package upgrades the existing PinDB installation while preserving databases stored in your own folders.

## Getting started

1. Launch PinDB.
2. Select **Create New Database**.
3. Enter a database name and choose where the `.pindb` file should be saved.
4. Add the fields needed for the information you want to track.
5. Select **Create Database**.
6. Use the **+** button to begin adding entries.

Existing `.pindb` files can be opened from the launcher, the recent-databases list, or a Linux file manager.

## Features

### Portable personal databases

- Stores each database in one portable `.pindb` file.
- Opens multiple databases in independent windows.
- Automatically saves entry and configuration changes.
- Remembers whether each database uses table view or record view.
- Creates a safety copy before performing a database-schema migration.

### Custom fields

PinDB supports:

- Text
- Multiline text
- Number
- Currency
- Date
- Date and time
- Yes/No
- Dropdown lists
- Embedded documents

Fields can use required-value rules, default values, numeric ranges, uniqueness requirements, text limits, dropdown choices, and configurable summaries where supported.

Fields can be added, edited, removed, and rearranged through the field-management window.

### Entries and organization

- Add, edit, and delete entries.
- Use **Add & Add Another** for repeated data entry.
- Search across all visible field values.
- Sort table columns.
- Create field-specific filters.
- View information in a spreadsheet-style table or readable record cards.
- Move deleted entries to **Recently Deleted** before permanently removing them.

### Embedded documents

Document fields store the original file data inside the `.pindb` database rather than keeping only an external file path.

Stored filenames are clickable in table and record views. PinDB provides in-application previews for:

- PDF documents
- DOCX documents
- Plain-text and common text-based files
- Common image formats

The document viewer can print supported previews, save a copy of the original file, or open the file with the system application. Files PinDB cannot preview remain stored and can still be saved or opened externally.

Because embedded files are stored inside the database, adding large documents will increase the size of the `.pindb` file and its backups.

### Summaries, printing, and CSV

- Configure sum, average, minimum, maximum, or entry-count summaries where supported.
- Export visible entries to CSV.
- Import CSV files as new PinDB databases.
- Print selected fields in column or record layouts.
- Choose portrait or landscape orientation.
- Include headings, database names, print dates, page numbers, and field summaries.
- Print only the entries currently visible after searching or filtering.

CSV exports contain document filenames, not embedded document contents.

### Backups and recovery

- Stores timestamped logical backups inside each `.pindb` file.
- Keeps the newest 10 internal backups by default.
- Includes embedded documents in internal backup snapshots.
- Restores fields, entries, and stored documents from a selected snapshot.
- Creates an untouched external copy before a schema migration.

Keep separate copies of important `.pindb` files as part of a normal backup routine. Internal snapshots help recover database changes, but they do not protect against losing or damaging the entire file.

### Updates and release history

PinDB can check GitHub Releases for new versions from inside the application.

When an update is accepted, PinDB:

1. Downloads the matching Debian package.
2. Verifies its SHA-256 checksum when supplied.
3. Requests administrator approval through the normal Linux privilege prompt.
4. Installs the package.
5. Restarts PinDB.
6. Displays the release notes.

Stable updates are checked by default. Pre-release updates can be enabled in Settings.

Previous release notes can be viewed at any time under **Help → PinDB Help → Updates**. PinDB bundles an offline changelog and can refresh newer release notes from GitHub when an internet connection is available.

### In-application bug reporting

Select **Report Bug** from the launcher or **Help → Report a Bug…** from an open database window to create an issue in the PinDB GitHub repository.

A GitHub account is required. The first report uses GitHub's device-authorization process to connect the application to the user's account.

The report form can include the PinDB version and basic system diagnostics. PinDB does not automatically include database contents, embedded documents, document filenames, database filenames, or personal file paths.

## Data compatibility

New PinDB versions may migrate databases created by earlier versions. Before a schema migration, PinDB creates a file named similarly to:

```text
Database.pre-migration-2026-07-30T123456.pindb
```

The copy is placed beside the original database. Older PinDB versions may not be able to open a database after it has been migrated by a newer version.

## Current limitations

- The official installer and automatic updater target Debian-based Linux systems.
- Database encryption is not currently included.
- Embedded document previews are not intended to reproduce every detail of a full office suite.
- A GitHub account and internet connection are required to submit a bug report from inside PinDB.

PinDB is still an early-stage application. Keep external backups of important databases and verify exported or printed information before relying on it for legal, medical, tax, or financial records.

# Building from source

## Requirements

- Git
- JDK 21, including `jpackage`
- A 64-bit Linux system for building the official Debian package
- `dpkg`, `dpkg-deb`, and `fakeroot` for Debian packaging

PinDB uses the included Gradle wrapper. A system-wide Gradle installation is not required.

JavaFX, SQLite JDBC, Apache PDFBox, Apache POI, JUnit, and the other Java dependencies are downloaded automatically from Maven Central during the build.

## Clone the repository

```bash
git clone https://github.com/mccreeper1318/pindb.git
cd pindb
chmod +x gradlew
```

To work with an active development branch, check it out after cloning. For example:

```bash
git switch agent/0.2-dev
```

## Run the tests

```bash
./gradlew clean test
```

The generated JaCoCo reports are written under:

```text
build/reports/jacoco/
```

## Run PinDB from source

```bash
./gradlew run
```

A development run uses the version supplied with `-PappVersion`. Without that property, the source-tree default is `0.0.0-dev`.

Example:

```bash
./gradlew run -PappVersion=0.2-beta.2
```

## Build the Linux installer

```bash
./gradlew clean test packageDeb -PappVersion=0.2-beta.2
```

The self-contained Debian package is written to:

```text
build/packages/
```

The package includes a private runtime generated by `jpackage` and installs PinDB under `/opt/pindb` with an application-menu shortcut and `.pindb` file association.

The `packageDeb` task runs only on Linux.

## Open the project in IntelliJ IDEA

1. Clone the repository.
2. Open the repository folder as a Gradle project.
3. Set the project SDK and Gradle JVM to JDK 21.
4. Allow Gradle to download and index the dependencies.
5. Run the Gradle `run` task or the `org.pindb.PinDBLauncher` main class.

## Project structure

```text
src/main/java/          Application source
src/main/resources/     Styles, icons, configuration, and packaged resources
src/test/java/          JUnit tests
packaging/              Debian package and file-association resources
docs/                   Release and updater documentation
.github/workflows/      Build, test, and release automation
```

## Release builds

The release workflow runs when a GitHub Release is published. It:

1. Checks out the release tag.
2. Validates the version format.
3. Runs the complete test suite.
4. Builds the self-contained `.deb` package.
5. Verifies the Debian package version.
6. Generates a SHA-256 checksum.
7. Uploads both files to the GitHub Release.

Supported tag formats include:

```text
0.2
0.2.1
v0.2.1
v.0.2.1
0.2-beta.2
```

A tag containing a pre-release suffix must be published as a GitHub pre-release. A stable tag must be published as a normal release.

Additional details are available in:

- [Release process](docs/release-process.md)
- [Update-system design](docs/update-system.md)
- [Changelog](CHANGELOG.md)

## Reporting problems and contributing

Use the [GitHub Issues](https://github.com/mccreeper1318/pindb/issues) page for reproducible bugs and feature requests.

A useful bug report should include:

- The PinDB version
- Linux distribution and version
- Steps that reproduce the problem
- Expected behavior
- Actual behavior
- Relevant error messages or logs

Before submitting code changes:

```bash
./gradlew clean test
```

Keep changes focused, include tests for new behavior when practical, and avoid committing real `.pindb` files or documents containing private information.
