# PinDB

PinDB is a desktop personal database application for organizing structured information without writing SQL or running a separate database server.

Each database is stored as a portable SQLite file with the `.pindb` extension. A single file contains its field definitions, entries, display preferences, deleted records, internal backups, and embedded documents.

PinDB provides self-contained packages for Windows 11 and Debian-family and Fedora-family Linux distributions.

## Download and install

Download the newest package for your operating system from the [PinDB Releases](https://github.com/mccreeper1318/pindb/releases) page:

- Use the Windows x64 `.exe` installer on 64-bit Windows 11.
- Use the `.deb` package on Linux Mint, Ubuntu, Debian, and related distributions.
- Use the `.rpm` package on Fedora Workstation, Fedora KDE, and other traditional Fedora spins.

All official packages include a private Java runtime, so Java does not need to be installed separately for normal use.

### Windows 11

Download the Windows x64 `.exe` installer and run it normally. PinDB installs per-user, so a system-wide Java installation is not required.

The Windows installer provides Start Menu integration, can create a desktop shortcut, and associates `.pindb` database files with PinDB so databases can be opened directly from File Explorer.

### Debian, Ubuntu, and Linux Mint

To install graphically, double-click the downloaded `.deb` and open it with the distribution's package installer.

To install from a terminal, open the folder containing the package and run:

```bash
sudo apt install ./pindb_*_amd64.deb
```

### Fedora Workstation and Fedora spins

To install graphically, double-click the downloaded `.rpm` and open it with Software or Discover.

To install from a terminal, open the folder containing the package and run:

```bash
sudo dnf install ./pindb-*.x86_64.rpm
```

Fedora Atomic desktops such as Silverblue and Kinoite use `rpm-ostree` rather than normal DNF package installation. PinDB's in-application installer does not currently update those immutable systems automatically. An RPM can be layered manually with:

```bash
sudo rpm-ostree install ./pindb-*.x86_64.rpm
```

Reboot into the new deployment after an `rpm-ostree` installation or update.

Installing a newer native package upgrades the existing PinDB installation while preserving databases stored in your own folders.

## Getting started

1. Launch PinDB.
2. Select **Create New Database**.
3. Enter a database name and choose where the `.pindb` file should be saved.
4. Add the fields needed for the information you want to track.
5. Select **Create Database**.
6. Use the **+** button to begin adding entries.

Existing `.pindb` files can be opened from the launcher, the recent-databases list, Windows File Explorer, or a Linux file manager.

## Features

### Portable personal databases

- Stores each database in one portable `.pindb` file.
- Opens multiple databases in independent windows.
- Automatically saves entry and configuration changes.
- Remembers whether each database uses table view or record view.
- Creates a safety copy before performing a database-schema migration.
- Checkpoints SQLite WAL data during a normal database-window shutdown so a closed `.pindb` file can be copied as a self-contained database.
- Provides specific diagnostics for empty, non-SQLite, corrupted, incomplete, and unsupported-newer database files.

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
- Keeps record values and embedded document BLOBs consistent if a document save fails by restoring the preceding internal snapshot.

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
- Uses a system Java/CUPS fallback on Linux when JavaFX printer discovery cannot see a configured printer.

CSV exports contain document filenames, not embedded document contents.

### Backups and recovery

- Stores timestamped logical backups inside each `.pindb` file.
- Keeps the newest 10 internal backups by default.
- Includes embedded documents in internal backup snapshots.
- Restores fields, entries, and stored documents from a selected snapshot.
- Creates an untouched external copy before a schema migration.

Keep separate copies of important `.pindb` files as part of a normal backup routine. Internal snapshots help recover database changes, but they do not protect against losing or damaging the entire file. For the safest manual transfer, close the database window normally before copying the `.pindb` file.

### Updates and release history

PinDB can check GitHub Releases for new versions from inside the application.

On Windows 11, PinDB selects the matching Windows x64 `.exe`, downloads it to the user's local application cache, verifies its published SHA-256 checksum, and launches the verified installer after approval. The installer performs the per-user upgrade without using the Linux privileged-update helper.

When an update is accepted on a supported traditional Linux installation, PinDB:

1. Detects whether the system uses Debian or RPM packages.
2. Downloads the matching `.deb` or `.rpm` package for the current architecture.
3. Requires and verifies its published SHA-256 checksum.
4. Requests administrator approval through the normal Linux privilege prompt.
5. Uses PinDB's fixed, root-owned update helper to securely stage and re-verify the package.
6. Installs only the verified staged package with `apt-get`, `dnf5`, or `dnf` as appropriate.
7. Restarts PinDB.
8. Displays the release notes.

Stable updates are checked by default. Pre-release updates can be enabled in Settings.

Previous release notes can be viewed at any time under **Help → PinDB Help → Updates**. PinDB bundles an offline changelog and can refresh newer release notes from GitHub when an internet connection is available.

### In-application bug reporting

Select **Report Bug** from the launcher or **Help → Report a Bug…** from an open database window to create an issue in the PinDB GitHub repository.

A GitHub account is required. The first report uses GitHub's device-authorization process to connect the application to the user's account.

The report form can include the PinDB version and basic system diagnostics. PinDB does not automatically include database contents, embedded documents, document filenames, database filenames, or personal file paths.

On Linux, GitHub authorization is stored in the system keyring when available. The fallback credential file is created with owner-only permissions before credential data is written.

## Data compatibility

New PinDB versions may migrate databases created by earlier versions. Before a schema migration, PinDB creates a file named similarly to:

```text
Database.pre-migration-20260730-123456.pindb
```

The copy is placed beside the original database. Older PinDB versions may not be able to open a database after it has been migrated by a newer version.

## Current limitations

- Official release packages currently target 64-bit x86 Windows 11 and 64-bit x86 Debian-family and Fedora-family Linux systems.
- The Windows installer is currently unsigned, so Windows may display a security/reputation warning when it is first run.
- Automatic installation is not supported on Fedora Atomic desktops such as Silverblue and Kinoite.
- Database encryption is not currently included.
- Embedded document previews are not intended to reproduce every detail of a full office suite.
- A GitHub account and internet connection are required to submit a bug report from inside PinDB.

PinDB is still an early-stage application. Keep external backups of important databases and verify exported or printed information before relying on it for legal, medical, tax, or financial records.

# Building from source

## Requirements

All builds require:

- Git
- JDK 25, including `jpackage`

Linux package builds require a 64-bit Linux system.

Debian packaging additionally requires:

- `dpkg`
- `dpkg-deb`
- `fakeroot`

RPM packaging additionally requires:

- `rpm-build`
- `rpmbuild`

Windows `.exe` packaging requires:

- A 64-bit Windows system
- WiX Toolset, as required by `jpackage` for Windows installer generation

PinDB uses the included Gradle wrapper. A system-wide Gradle installation is not required.

JavaFX 25.0.3, SQLite JDBC, Apache PDFBox, Apache POI, JUnit, and the other Java dependencies are downloaded automatically from Maven Central during the build.

## Clone the repository

```bash
git clone https://github.com/mccreeper1318/pindb.git
cd pindb
chmod +x gradlew
```

To work with the active 0.2.1 development branch:

```bash
git switch agent/dev_0.2.1
```

## Run the tests

On Linux:

```bash
./gradlew clean test
```

On Windows:

```powershell
.\gradlew.bat clean test
```

The generated JaCoCo reports are written under:

```text
build/reports/jacoco/
```

## Run PinDB from source

On Linux:

```bash
./gradlew run
```

On Windows:

```powershell
.\gradlew.bat run
```

A development run uses the version supplied with `-PappVersion`. Without that property, the source-tree default is `0.0.0-dev`.

Example:

```bash
./gradlew run -PappVersion=0.2.1
```

## Build the Windows installer

On a Windows x64 build system with WiX installed:

```powershell
.\gradlew.bat clean test packageWindows -PappVersion=0.2.1
```

## Build the Debian package

On a Debian-family build system:

```bash
./gradlew clean test packageDeb -PappVersion=0.2.1
```

## Build the Fedora RPM

On Fedora or another RPM build system with `rpm-build` installed:

```bash
./gradlew clean test packageRpm -PappVersion=0.2.1
```

All package tasks write their self-contained package to:

```text
build/packages/
```

The packages include a private runtime generated by `jpackage` and register PinDB with the operating system, including `.pindb` file association. Linux packages install under `/opt/pindb`; the Windows package uses a per-user installation.

Native `jpackage` packages must be built on the corresponding operating system. The CI and release workflows build the `.exe` on Windows, the `.deb` on Ubuntu, and the `.rpm` inside Fedora.

## Open the project in IntelliJ IDEA

1. Clone the repository.
2. Open the repository folder as a Gradle project.
3. Set the project SDK and Gradle JVM to JDK 25.
4. Allow Gradle to download and index the dependencies.
5. Run the Gradle `run` task or the `org.pindb.PinDBLauncher` main class.

## Project structure

```text
src/main/java/          Application source
src/main/resources/     Styles, icons, configuration, and packaged resources
src/test/java/          JUnit tests
packaging/              Native package and file-association resources
docs/                   Release and updater documentation
.github/workflows/      Windows, Debian, and Fedora build, test, and release automation
```

## Release builds

The release workflows run when a GitHub Release is published. They:

1. Validate the release tag and pre-release status.
2. Run the applicable test suites on Windows, Debian, and Fedora build environments.
3. Build a self-contained Windows x64 `.exe` installer on Windows.
4. Build a self-contained `.deb` package on Ubuntu.
5. Build a self-contained `.rpm` package in Fedora.
6. Verify the native package versions and metadata.
7. Generate SHA-256 checksums for the packages.
8. Upload the packages and checksum files to the GitHub Release.

Supported tag formats include:

```text
0.2
0.2.1
v0.2.1
v.0.2.1
0.2-beta.3
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
- Operating system and version
- Package type used (`.exe`, `.deb`, or `.rpm`)
- Steps that reproduce the problem
- Expected behavior
- Actual behavior
- Relevant error messages or logs

Before submitting code changes, run the test suite appropriate for your platform.

Keep changes focused, include tests for new behavior when practical, and avoid committing real `.pindb` files or documents containing private information.
