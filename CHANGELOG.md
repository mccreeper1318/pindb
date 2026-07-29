# Changelog

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
