# Changelog

## 55-updater-did-not-auto-restart-app-after-update-did-not-properly-show-changelog-after-update

### Fixed

- Fixed Issue #55 so PinDB's post-update launcher waits for the old updater process to exit before JavaFX starts, preventing the restart handoff race that could leave the application closed after a successful update.
- Persisted pending release notes before installation and added a startup fallback so the changelog is still shown after the update even when the temporary notes file is unavailable or automatic restart fails.
- Added version matching and regression coverage for post-update launch detection and release-note handoff behavior.
- Fixed Issue #57 by moving full pending release-note bodies out of Java `Preferences` and into a file under PinDB's state directory, avoiding the 8,192-character preference value limit that could prevent an update from starting.
- Pending release-note persistence is now best-effort, uses atomic replacement where supported, cleans up after retrieval, and remains compatible with older preference-backed pending notes.
- Added regression coverage for release-note bodies larger than `Preferences.MAX_VALUE_LENGTH` and legacy pending-note migration.
- Fixed Issue #58 so pending release notes are only consumed when the running PinDB version matches the stored update target, preventing another older PinDB instance from deleting the fallback notes while an update is still installing.
- Startup now passes the expected updated version into pending-note retrieval, and mismatched versions leave both the pending tag and Markdown file intact for the actual target version.
- Added regression coverage confirming mismatched versions preserve pending notes and the matching target consumes them, including equivalent `v`-prefixed version tags.
- Fixed Issue #59 by storing pending release-note bodies in separate files keyed by normalized target version, preventing concurrent updates from pairing one release tag with another release's changelog.
- Fixed Issue #60 so transient read failures no longer consume or delete the only pending release-note fallback; the target-specific or legacy file is preserved so a later startup can retry after the filesystem problem is resolved.
- Added regression coverage for concurrent pending versions, retrying unreadable version-specific notes, and preserving unreadable legacy fallback files until a successful read.

## 0.2.1-beta.3

### Added

- Added a fixed, package-installed privileged update helper with a narrow install-only interface for Debian and Fedora packages.
- Added updater security regression coverage for package replacement, symlink substitution, and checksum-to-install race conditions, along with DEB and RPM checks that ensure the privileged helper is packaged as an executable without an application-menu shortcut.

### Fixed
- Fixed the updater executing a cache-directory shell script as root and installing directly from a user-writable package path.
- Automatic package installation now requires a matching published SHA-256 checksum. The helper rejects symlinks, holds the source open securely, copies it into an owner-only root staging directory, and re-verifies the expected digest before giving only that staged copy to APT or DNF.
- Fixed restoring the oldest retained internal backup erasing the active database when creating the pre-restore safety snapshot pruned the selected backup. Backup validation, safety snapshot creation, core and embedded-document restoration, and retention pruning now complete atomically, with pruning deferred until the restore has succeeded (Issue #35).

## 0.2.1-beta.2

### Changed

- Replaced the database window's native JavaFX menu popups with in-window drop-down panels that stay anchored directly beneath the menu bar on affected Fedora/Nobara Wayland and KDE configurations (Issue #31).
- Added Fedora package regression checks for safe RPM upgrade and uninstall script behavior.

### Fixed

- Fixed RPM upgrades unregistering PinDB's desktop entry after the new package had installed, which could remove PinDB from the application menu (Issue #33).
- Fixed the generated RPM uninstall script launching nested `rpm -q` commands while DNF held the RPM transaction lock, eliminating the repeated `.rpm.lock` errors reported during removal (Issue #33).
- Fixed the custom Fedora RPM specification being ignored by `jpackage` because its override filename did not match the PinDB package name, ensuring the safe upgrade and uninstall scriptlets are actually packaged (Issue #33).
- Hardened the post-update restart so failure to delete the downloaded package cannot prevent PinDB from reopening, and detached the restarted process from the updater's output streams (Issue #33).
- Fixed Markdown release-note body text using an unreadable default color in PinDB's dark theme, which could make the post-update dialog appear empty (Issue #33).

## 0.2.1-beta.1

### Added

- Added clearer database-open diagnostics that distinguish empty files, non-SQLite files, corrupted SQLite databases, missing PinDB metadata, missing schema-version metadata, and databases created by newer PinDB versions (Issue #26).
- Added recovery guidance when a `.pindb` file appears incomplete or damaged (Issue #26).
- Added a system Java/CUPS printing fallback when JavaFX cannot discover a configured printer on Linux (Issue #32).
- Added regression coverage for invalid database diagnostics, portable database copies after a normal close, record/document rollback behavior, and owner-only credential storage.

### Changed

- PinDB now checkpoints and truncates pending SQLite WAL data during a normal document-store shutdown so the primary `.pindb` file is safer to copy between systems as a self-contained file (Issue #26).
- Upgraded the packaged runtime and build toolchain to Java 25 with JavaFX 25.0.3, incorporating the upstream Linux GTK popup-positioning fix used by menu drop-downs (Issue #31).
- Updated Debian and Fedora CI/release builds to use Temurin Java 25.

### Fixed

- Fixed record values and embedded document BLOBs being able to become inconsistent when the document transaction failed after a record add or edit had already committed. PinDB now restores the immediately preceding internal snapshot when the document save fails (Issue #28).
- Fixed fallback GitHub credentials being written before owner-only permissions were applied. Fallback credentials are now created in a `0600` temporary file before token bytes are written and then moved into place (Issue #29).
- Fixed GitHub keyring I/O failures incorrectly setting the worker thread's interrupted flag. The interrupt status is now restored only for actual `InterruptedException` cases (Issue #30).
- Fixed Linux menu drop-downs appearing far below the menu bar on affected Fedora/Nobara desktop configurations by moving to the JavaFX version containing the upstream GTK coordinate fix (Issue #31).
- Fixed database printing immediately reporting that no printer was configured when JavaFX printer discovery failed even though the system print service could see the printer (Issue #32).

## 0.2-beta.4

### Added

- Added self-contained RPM packaging for Fedora Workstation, Fedora KDE, and other traditional Fedora-family Linux systems.
- Added Fedora-aware distribution detection using the standard `os-release` files.
- Added architecture-aware GitHub Release selection for Debian and RPM packages.
- Added automatic Fedora updates through `dnf5` or `dnf` with `pkexec`, SHA-256 verification, rollback protection, restart, and release-note display.
- Added Fedora Atomic detection with a safe manual `rpm-ostree` installation path instead of attempting an unsupported DNF update.
- Added Fedora CI and release jobs that build and verify an RPM alongside the existing Debian package.
- Added regression tests for Fedora detection, architecture matching, RPM release selection, RPM checksums, package-manager selection, and manual installation commands.

### Fixed

- Fixed Fedora 44 RPM builds failing because `java-21-openjdk-devel` is no longer available in Fedora 44 repositories; Fedora jobs now install Temurin Java 21 through `actions/setup-java` and use DNF only for native RPM build tools.
- Fixed RPM verification incorrectly requiring the generated PinDB desktop entry to be stored under an `applications` directory; validation now finds the actual `jpackage` desktop file inside the application bundle and verifies its launcher and icon entries.

## 0.2-beta.3

### Fixed

- Fixed the About PinDB dialog referring specifically to Version 0.1 in its database-encryption notice (Issue #19).
- Changed the notice to **Current version does not encrypt database contents** so it remains accurate in future releases.
- Fixed **Copy Code** closing the GitHub authorization dialog and leaving the bug-report form disabled (Issue #20).
- Kept the authorization dialog open while copying the device code or opening GitHub, with an explicit cancellation option.
- Fixed the GitHub authorization dialog remaining open after authorization and bug submission succeeded (Issue #21).
- Fixed later successful bug submissions displaying an empty confirmation dialog (Issue #22).
- Replaced the transient success alert with a consistently populated confirmation containing the issue number, address, **Open Issue**, and **Close** actions.

## 0.2-beta.2

### Fixed

- Fixed the GitHub authorization button freezing PinDB while opening the browser during in-app bug reporting (Issue #18).
- Moved GitHub authorization and submitted-issue link opening off the JavaFX application thread.
- Added a visible device code, authorization address, and **Copy Code** fallback when the browser cannot open automatically.

## 0.2-beta.1

### Added

- Added a **Document** field type that stores the original file inside the `.pindb` SQLite database.
- Added clickable document filenames in table and record views.
- Added an in-app viewer for PDF, DOCX, text, and common image files.
- Added printing, Save Copy, and system-application actions to the document viewer.
- Added embedded-document support to logical database backup snapshots.
- Added a clickable update-history browser under **Help → PinDB Help → Updates**, with online refresh, local caching, and bundled offline release notes.
- Added an in-app bug reporter that creates labeled issues in the PinDB GitHub repository using GitHub device authorization.
- Added privacy-conscious diagnostics that exclude database contents, embedded documents, filenames, and personal paths.
- Added secure GitHub authorization storage through the Linux keyring when available, with an owner-only credential-file fallback.

### Fixed

- Fixed CSV imports keeping display-formatted dates instead of normalizing them to PinDB's internal date format (Issue #12).
- Fixed typed DatePicker values being replaced by the previous or current date when Enter saved an entry (Issue #13).
- Fixed large groups of printed summaries being clipped instead of continuing onto additional pages (Issue #16).
- Fixed table-print pagination estimating wrapping with a different column width than the rendered table (Issue #17).

## 0.1.1-beta.6

- Fixed the duplicate **Cancel** button in the new-entry dialog while preserving the exact visible order: **Cancel**, **Add & Add Another**, **Add Entry**.
- Fixed the post-update release-notes window so notes display at a usable size and remain scrollable.
- Added a plain-text fallback if Markdown release-note rendering fails.
- Updated launcher detection to recognize the actual packaged location at `/opt/pindb/pindb/bin/PinDB` as well as alternate install paths.
- Made checksum verification tolerant of GitHub normalizing `~` to `.` in uploaded asset filenames.
- Made checksum verification accept directory-prefixed filenames and single unambiguous SHA-256 entries.
- Added updater regression tests for launcher discovery and checksum filename normalization.
- Updated release packaging so uploaded `.deb` filenames and generated checksum entries always match.

## 0.1.1-beta.5

- Fixed Issue #9: the new-entry dialog can now be closed with the visible **Cancel** button, the Escape key, or the title-bar **X**.
- Preserved the fixed visible button order: **Cancel**, **Add & Add Another**, **Add Entry**.
- Kept the platform dialog controls hidden so Linux cannot rearrange the custom action buttons.
- Includes the Beta 4 updater diagnostics and privileged installation improvements for continued update testing.

## 0.1.1-beta.4

- Replaced the platform-managed new-entry button bar with a fixed custom layout: **Cancel**, **Add & Add Another**, **Add Entry**.
- Switched automatic Linux updates to use `apt-get` through `pkexec`.
- Added persistent updater diagnostics at `~/.local/state/pindb/update-error.log`.
- Delayed update failure alerts until the progress dialog has fully closed.
- Replaced the blank custom failure dialog with a standard error alert containing the cause, package path, manual command, and log location.

## 0.1.1-beta.3

- Reordered the new-entry dialog buttons to display as **Cancel**, **Add & Add Another**, and **Add Entry**.
- Includes the automatic updater, printing, summary, and repeat-entry improvements introduced during the 0.1.1 beta cycle.

## 0.1.1

- Added an **Add & Add Another** action for faster entry creation.
- Reworked print pagination to account for wrapped content and reserved header/footer space.
- Added an option to print configured field summaries for the visible entries.
- Improved printer page-layout selection and print failure messaging.

## 0.1

- Added the PinDB JavaFX launcher and multi-window database workspace.
- Added SQLite-backed `.pindb` files containing fields, entries, view preferences, trash, and internal backups.
- Added eight initial field types and validation rules.
- Added editable table and record views, summaries, search, sorting, and filtering.
- Added Recently Deleted recovery and internal backup restoration.
- Added CSV import/export and formatted printing.
- Added configurable GitHub Release update checks with stable and pre-release channels.
- Added self-contained Debian packaging and automated GitHub build/release workflows.
