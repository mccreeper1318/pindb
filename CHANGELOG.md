# Changelog

## 0.3

### Added
- Added Windows 11 x64 support with a self-contained unsigned `.exe` installer and bundled Java runtime (Issue #34).
- Added per-user Windows installation, Start Menu integration, optional desktop shortcuts, install-directory selection, and `.pindb` file associations.
- Added Windows-aware configuration and state paths using `%APPDATA%` and `%LOCALAPPDATA%`.
- Added Windows release discovery, x64 matching, checksum verification, and verified installer launching.
- Added Windows release automation with WiX plus Windows-specific CI and regression coverage.

### Changed
- Extended native package handling across Debian, Fedora, and Windows packages.
- Updated architecture detection to recognize Windows `x64` release assets.
- Updated the main CI workflow to run on direct pushes to `dev`.

### Fixed
- Fixed the Windows installer package task and hardened installer verification.
- Fixed Windows CI failures caused by Linux-only privileged updater tests and Linux-specific path assertions.
- Fixed fallback GitHub credential storage on Windows by using owner-only ACLs (Issue #65).
- Fixed Windows credential staging so inherited ACL entries cannot expose token data before permissions are restricted (Issue #68).
- Expanded credential fallback tests for Linux permissions, Windows ACLs, protected staging, and cleanup.

## 0.2.1

### Added
- Added clearer database-open diagnostics for invalid, damaged, incomplete, and newer-version databases (Issue #26).
- Added recovery guidance for incomplete or damaged `.pindb` files (Issue #26).
- Added a Java/CUPS printing fallback when JavaFX cannot discover a configured Linux printer (Issue #32).
- Added regression coverage for database diagnostics, portable copies, document rollback, and credential permissions.
- Added a package-installed privileged update helper for Debian and Fedora packages (Issue #36).
- Added updater security tests for package replacement, symlinks, checksum races, and helper packaging.
- Added Fedora package regression checks for safe RPM upgrades and uninstalls.
- Added post-update version matching and release-note handoff regression coverage.
- Added regression coverage for oversized release notes and legacy pending-note migration.

### Changed
- PinDB now checkpoints and truncates SQLite WAL data during normal shutdown for safer portable database copies (Issue #26).
- Upgraded the packaged runtime and build toolchain to Java 25 with JavaFX 25.0.3.
- Updated Debian and Fedora CI and release builds to use Temurin Java 25.
- Replaced native JavaFX menu popups with in-window drop-down panels on affected Linux desktops (Issue #31).
- Pending release notes now use target-version files with atomic replacement where supported and legacy fallback compatibility.

### Fixed
- Fixed record values and embedded documents becoming inconsistent after document transaction failures (Issue #28).
- Fixed fallback GitHub credentials being written before owner-only permissions were applied (Issue #29).
- Fixed GitHub keyring I/O failures incorrectly marking the worker thread as interrupted (Issue #30).
- Fixed Linux menu drop-downs appearing far below the menu bar on affected desktops (Issue #31).
- Fixed printing reporting no printer when the system print service could still find one (Issue #32).
- Fixed RPM upgrades unregistering the PinDB desktop entry (Issue #33).
- Fixed RPM uninstall scripts causing nested RPM lock errors during DNF removal (Issue #33).
- Fixed the custom Fedora RPM specification being ignored by `jpackage` (Issue #33).
- Fixed post-update restart failures caused by package cleanup or attached output streams (Issue #33).
- Fixed unreadable Markdown release-note text in the dark theme (Issue #33).
- Fixed restoring the oldest retained backup from erasing the active database during snapshot pruning (Issue #35).
- Fixed the updater executing user-writable content as root by using a fixed privileged helper and protected staging (Issue #36).
- Fixed automatic package installation to require and re-verify published SHA-256 checksums before APT or DNF runs (Issue #36).
- Fixed the post-update launcher starting before the old updater process had exited (Issue #55).
- Fixed release notes disappearing when restart or temporary-note handoff failed.
- Fixed large release notes exceeding Java `Preferences` limits by storing them in state files (Issue #57).
- Fixed pending release notes being consumed by the wrong running version (Issue #58).
- Fixed concurrent updates pairing the wrong release tag and changelog body (Issue #59).
- Fixed transient read failures deleting the only pending release-note fallback (Issue #60).
- Added regression coverage for version mismatches, concurrent pending notes, and retryable read failures.

## 0.2

### Added
- Added a **Document** field type that stores original files inside `.pindb` databases.
- Added clickable document filenames and an in-app viewer for PDF, DOCX, text, and common images.
- Added printing, Save Copy, and system-application actions to the document viewer.
- Added embedded-document support to logical database backups.
- Added an update-history browser with online refresh, local caching, and bundled offline notes.
- Added an in-app GitHub bug reporter with device authorization.
- Added privacy-conscious diagnostics that exclude database contents, embedded files, filenames, and personal paths.
- Added secure GitHub authorization storage through the Linux keyring with an owner-only file fallback.
- Added self-contained RPM packaging for traditional Fedora-family systems.
- Added Fedora distribution detection and architecture-aware Debian and RPM release selection.
- Added automatic Fedora updates through `dnf5` or `dnf` with checksum verification and restart support.
- Added Fedora Atomic detection with a manual `rpm-ostree` installation path.
- Added Fedora CI and release jobs plus Fedora-specific regression coverage.

### Fixed
- Fixed CSV imports preserving display-formatted dates instead of PinDB's internal format (Issue #12).
- Fixed typed DatePicker values being replaced when Enter saved an entry (Issue #13).
- Fixed printed summaries being clipped instead of continuing onto later pages (Issue #16).
- Fixed table-print pagination using a different wrap width than the rendered table (Issue #17).
- Fixed GitHub authorization blocking the JavaFX thread while opening the browser (Issue #18).
- Added a visible device code and copyable authorization fallback when the browser cannot open (Issue #18).
- Fixed the About dialog referring specifically to Version 0.1 in the encryption notice (Issue #19).
- Fixed authorization and submission dialogs closing, disabling, or displaying empty confirmations incorrectly (Issues #20-#22).
- Fixed Fedora 44 RPM builds by using Temurin Java instead of unavailable Fedora Java packages.
- Fixed RPM verification assuming the generated desktop entry lived under an `applications` directory.

## 0.1.1

### Added
- Added an **Add & Add Another** action for faster entry creation.
- Added optional printing of configured field summaries for visible entries.
- Added persistent updater diagnostics at `~/.local/state/pindb/update-error.log`.
- Added a plain-text fallback when Markdown release-note rendering fails.
- Added updater regression tests for launcher discovery and checksum filename handling.

### Changed
- Reworked print pagination to account for wrapped content and reserved header and footer space.
- Improved printer page-layout selection and print failure messaging.
- Replaced platform-managed entry dialog buttons with a fixed **Cancel**, **Add & Add Another**, **Add Entry** layout.
- Switched automatic Linux updates to use `apt-get` through `pkexec`.
- Updated launcher detection to recognize the packaged `/opt/pindb/pindb/bin/PinDB` path.
- Updated checksum verification to accept normalized, directory-prefixed, and single unambiguous entries.
- Updated release packaging so `.deb` filenames and checksum entries always match.

### Fixed
- Fixed the new-entry dialog so Cancel, Escape, and the title-bar close button work correctly (Issue #9).
- Fixed a duplicate **Cancel** button in the new-entry dialog.
- Fixed the post-update release-notes window so notes remain readable and scrollable.
- Fixed update failure alerts appearing before the progress dialog had closed.
- Replaced the blank custom update failure dialog with a populated standard error alert.

## 0.1

### Added
- Added the PinDB JavaFX launcher and multi-window database workspace.
- Added SQLite-backed `.pindb` files for fields, entries, view preferences, trash, and internal backups.
- Added eight initial field types and validation rules.
- Added editable table and record views with summaries, search, sorting, and filtering.
- Added Recently Deleted recovery and internal backup restoration.
- Added CSV import and export plus formatted printing.
- Added configurable GitHub Release update checks with stable and pre-release channels.
- Added self-contained Debian packaging and automated GitHub build and release workflows.
