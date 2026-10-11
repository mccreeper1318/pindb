# PinDB

PinDB is a desktop personal database application for organizing structured information without writing SQL or running a separate database server.

Each database is stored as a portable SQLite file with the `.pindb` extension. A single file contains its field definitions, entries, display preferences, deleted records, internal backups, and embedded documents.

PinDB provides self-contained native packages for Windows 11, macOS, Debian-family Linux, and Fedora-family Linux. Official packages include a private Java runtime, so Java does not need to be installed separately for normal use.

## Download and install

Download the newest package for your operating system from the [PinDB Releases](https://github.com/mccreeper1318/pindb/releases) page.

- **Windows 11 x64:** `PinDB-<version>-windows-x64.exe`
- **macOS Apple Silicon:** `PinDB-<version>-macos-arm64.pkg`
- **macOS Intel:** `PinDB-<version>-macos-x64.pkg`
- **Debian / Ubuntu / Linux Mint x86-64:** `.deb`
- **Fedora Workstation / Fedora spins x86-64:** `.rpm`

Download the matching `.sha256` file with the package and verify it before installation.

### Windows 11

Run the Windows x64 `.exe` installer normally. PinDB installs per-user, provides Start Menu integration, can create a desktop shortcut, and associates `.pindb` files with PinDB.

The Windows installer is currently unsigned, so Windows may show an unknown-publisher or SmartScreen warning. Only use installers from the official PinDB GitHub Release and verify the published SHA-256 checksum before continuing.

### macOS

Choose the PKG that matches the Mac:

- **Apple Silicon (M-series):** `-macos-arm64.pkg`
- **Intel:** `-macos-x64.pkg`

PinDB 0.3 macOS packages are **unsigned and not notarized**. macOS may warn that the package cannot be verified or that it came from an unidentified developer. Before opening it:

1. Download the `.pkg` and matching `.pkg.sha256` file from the same official GitHub Release.
2. In Terminal, change to the download folder and run:

```bash
shasum -a 256 -c PinDB-<version>-macos-arm64.pkg.sha256
```

or, on Intel:

```bash
shasum -a 256 -c PinDB-<version>-macos-x64.pkg.sha256
```

The command must report `OK`.

3. Open the verified PKG in Finder. If macOS blocks the unsigned package, use the normal macOS security prompt or **System Settings → Privacy & Security** to approve that specific verified package. Do not disable Gatekeeper globally.
4. Complete installation in macOS Installer, then launch PinDB from Applications/Finder.

The package registers `.pindb` files so databases can be opened directly from Finder.

### Debian, Ubuntu, and Linux Mint

Graphically, open the downloaded `.deb` with the distribution package installer. From a terminal:

```bash
sudo apt install ./pindb_*_amd64.deb
```

### Fedora Workstation and Fedora spins

Graphically, open the downloaded `.rpm` with Software or Discover. From a terminal:

```bash
sudo dnf install ./pindb-*.x86_64.rpm
```

Fedora Atomic desktops such as Silverblue and Kinoite use `rpm-ostree`. PinDB does not automatically install updates on those immutable systems:

```bash
sudo rpm-ostree install ./pindb-*.x86_64.rpm
```

Reboot into the new deployment after an `rpm-ostree` installation or update.

Installing a newer native package upgrades PinDB while preserving databases stored in user-selected folders.

## Getting started

1. Launch PinDB.
2. Select **Create New Database**.
3. Enter a database name and choose where the `.pindb` file should be saved.
4. Add the fields needed for the information you want to track.
5. Select **Create Database**.
6. Use the **+** button to begin adding entries.

Existing `.pindb` files can be opened from the launcher, the recent-databases list, Windows File Explorer, macOS Finder, or a Linux file manager.

## Features

### Portable personal databases

- One portable `.pindb` file per database.
- Multiple databases can be opened in independent windows.
- Changes are saved automatically.
- Table and record views are remembered per database.
- SQLite WAL data is checkpointed during normal shutdown so a closed database can be copied safely.
- Invalid, damaged, incomplete, and newer-format databases receive targeted diagnostics.

### Custom fields

PinDB supports text, multiline text, number, currency, date, date/time, yes/no, dropdown, and embedded-document fields. Fields can use required values, defaults, numeric ranges, uniqueness requirements, text limits, dropdown choices, and summaries where supported.

### Entries and organization

- Add, edit, delete, restore, and permanently remove entries.
- Search visible field values.
- Sort table columns and create field-specific filters.
- Switch between table and record views.
- Use **Add & Add Another** for repeated entry.

### Embedded documents

Document fields store the original file inside the `.pindb` database. Embedded documents are limited to **50 MiB per file** so document operations remain bounded. PinDB can preview PDF, DOCX, plain-text/common text files, and common image formats. PDF pages are rendered lazily as they are viewed rather than all at once. Stored documents can also be printed, saved as copies, or opened with the system application.

### Summaries, printing, and CSV

- Sum, average, minimum, maximum, and entry-count summaries where supported.
- CSV import and export.
- Printable column or record layouts.
- Portrait/landscape, headings, database names, dates, page numbers, and summaries.
- Printing of the currently visible searched/filtered result set.

### Backups and recovery

PinDB stores timestamped logical backups inside each database, including embedded documents, and can restore a selected snapshot. It also creates an external safety copy before schema migration. Keep separate external backups of important `.pindb` files as part of a normal backup routine.

## Updates and release history

PinDB checks GitHub Releases for new versions. Stable updates are checked by default; pre-release updates can be enabled in Settings.

### Windows 11

PinDB selects the Windows x64 `.exe`, downloads it to the local application cache, verifies the published SHA-256 checksum, launches the verified installer, and exits.

### macOS

PinDB only selects the PKG matching the current Mac architecture. Unknown, wrong-architecture, or unmarked Mac packages are rejected.

For an accepted update PinDB:

1. Downloads the matching PKG into `~/Library/Caches/PinDB/updates`.
2. Requires and verifies its published SHA-256 checksum.
3. Requests administrator approval to copy the verified package into a unique, root-owned protected staging directory under `/private/var/tmp`.
4. Re-verifies the staged package before handing it to `/usr/bin/open`.
5. Opens the protected PKG in macOS Installer and exits PinDB.
6. Leaves the protected package available temporarily for recovery, then removes the staging directory automatically.

Complete the installation in macOS Installer and **reopen PinDB manually**. Pending release notes are shown on the next launch. PinDB does not silently bypass Gatekeeper, code-signing, or notarization checks.

### Debian and Fedora

On supported traditional Linux installations PinDB downloads the matching `.deb` or `.rpm`, verifies SHA-256, requests administrator approval, securely stages and re-verifies the package through the fixed root-owned update helper, installs it with `apt-get`, `dnf5`, or `dnf`, then restarts PinDB and displays release notes.

Previous release notes are available under **Help → PinDB Help → Updates**.

## Native application paths

### macOS

- Configuration: `~/Library/Application Support/PinDB`
- State and diagnostics: `~/Library/Application Support/PinDB/State`
- Cache/update downloads: `~/Library/Caches/PinDB`

### Windows

- Configuration: `%APPDATA%\PinDB`
- State/cache: `%LOCALAPPDATA%\PinDB`

### Linux

PinDB honors the normal XDG configuration, state, and cache locations, with standard home-directory fallbacks.

## In-application bug reporting

Select **Report Bug** from the launcher or **Help → Report a Bug…** from an open database window. A GitHub account is required. PinDB uses GitHub device authorization and does not automatically include database contents, embedded documents, document filenames, database filenames, or personal file paths in reports.

## Data compatibility

New PinDB versions may migrate databases created by earlier versions. Before a schema migration, PinDB creates a file similar to:

```text
Database.pre-migration-20260730-123456.pindb
```

Older PinDB versions may not be able to open a database after it has been migrated by a newer version.

## Current limitations

- Windows packages currently target Windows 11 x64.
- macOS packages target Apple Silicon ARM64 and Intel x64; PinDB 0.3 Mac packages are unsigned and not notarized.
- Windows packages are also currently unsigned.
- Automatic installation is not supported on Fedora Atomic desktops such as Silverblue and Kinoite.
- Database encryption is not currently included.
- Embedded document previews are not intended to reproduce every detail of a full office suite.
- A GitHub account and internet connection are required to submit a bug report from inside PinDB.

PinDB is still an early-stage application. Keep external backups of important databases and verify exported or printed information before relying on it for legal, medical, tax, or financial records.

# Building from source

## Requirements

All builds require Git and JDK 25, including `jpackage`. PinDB uses the included Gradle wrapper, so a system-wide Gradle installation is not required.

Additional platform requirements:

- Debian packaging: `dpkg`, `dpkg-deb`, and `fakeroot`.
- RPM packaging: `rpm-build` / `rpmbuild`.
- Windows packaging: Windows x64 plus WiX as required by `jpackage`.
- macOS packaging: a native Mac runner/system. ARM64 packages must be built on Apple Silicon and x64 packages on Intel.

JavaFX, SQLite JDBC, Apache PDFBox, Apache POI, JUnit, and other Java dependencies are downloaded automatically from Maven Central.

## Clone and test

```bash
git clone https://github.com/mccreeper1318/pindb.git
cd pindb
git switch dev
chmod +x gradlew
./gradlew clean test
```

On Windows use `gradlew.bat` instead of `./gradlew`.

## Run from source

```bash
./gradlew run -PappVersion=0.3
```

## Build native packages

Windows x64:

```powershell
.\gradlew.bat clean test packageWindows -PappVersion=0.3
```

macOS on either native architecture:

```bash
./gradlew clean test packageMacPkg -PappVersion=0.3
```

Debian-family Linux:

```bash
./gradlew clean test packageDeb -PappVersion=0.3
```

Fedora/RPM system:

```bash
./gradlew clean test packageRpm -PappVersion=0.3
```

Packages are written under `build/packages/` and include a private runtime generated by `jpackage`.

## Release builds

Publishing a GitHub Release triggers independent Linux, Windows, and macOS release workflows. For PinDB 0.3 the complete native asset set is:

```text
PinDB-<version>-windows-x64.exe
PinDB-<version>-windows-x64.exe.sha256
pindb_<debian-version>_amd64.deb
pindb_<debian-version>_amd64.deb.sha256
pindb-<rpm-version>.x86_64.rpm
pindb-<rpm-version>.x86_64.rpm.sha256
PinDB-<version>-macos-arm64.pkg
PinDB-<version>-macos-arm64.pkg.sha256
PinDB-<version>-macos-x64.pkg
PinDB-<version>-macos-x64.pkg.sha256
```

The macOS workflow builds and verifies both architectures independently before publishing them to the same GitHub Release.

See:

- [Release process](docs/release-process.md)
- [Update-system design](docs/update-system.md)
- [macOS acceptance testing](docs/macos-acceptance-testing.md)
- [Changelog](CHANGELOG.md)

## Reporting problems and contributing

Use [GitHub Issues](https://github.com/mccreeper1318/pindb/issues) for reproducible bugs and feature requests. Include the PinDB version, operating system/version, package type (`.exe`, `.pkg`, `.deb`, or `.rpm`), reproduction steps, expected behavior, actual behavior, and relevant error messages or logs.

Before submitting code changes, run the test suite appropriate for your platform. Keep changes focused and do not commit real `.pindb` files or documents containing private information.
