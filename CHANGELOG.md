# Changelog

## 0.3-beta.2

### Fixed
- Fixed the Windows installer package task and hardened Windows installer verification.
- Fixed the Windows build test suite failing on Linux-only privileged updater tests by restricting those tests to Linux runners.
- Fixed Linux-path assertions in updater regression tests running on Windows.
- Added Windows ACL support for the fallback GitHub credential file so owner-only credential storage works on Windows instead of failing on POSIX permission APIs.
- Updated credential fallback regression coverage to verify POSIX permissions on Linux and owner-only ACLs on Windows.

## 0.3-beta.1

### Added

- Added Windows 11 x64 support for Issue #34, including a self-contained unsigned `.exe` installer built with `jpackage` and a bundled Java runtime.
- Added per-user Windows installation, Start Menu integration, optional desktop shortcut prompting, install-directory selection, and `.pindb` file association support.
- Added Windows-aware application data paths using `%APPDATA%` for configuration and `%LOCALAPPDATA%` for state, cache, and downloaded updates.
- Added Windows GitHub Release package discovery, x64 architecture matching, `.exe` checksum discovery, SHA-256 verification, and launching of verified Windows update installers from PinDB.
- Added a Windows release workflow using a Windows runner and WiX to build, checksum, normalize, and attach `PinDB-<version>-windows-x64.exe` to GitHub Releases.
- Added Windows CI coverage and regression tests for Windows platform detection and Windows x64 release-package selection.

### Changed

- Extended native package handling so PinDB can distinguish Debian `.deb`, Fedora `.rpm`, and Windows `.exe` release assets while preserving the existing Linux privileged-update security path.
- Updated architecture detection to recognize `x64` Windows release asset names.

## 0.2.1-rc.1

### Added

- Added version matching and regression coverage for post-update launch detection and release-note handoff behavior.
- Added regression coverage for release-note bodies larger than `Preferences.MAX_VALUE_LENGTH` and legacy pending-note migration.
