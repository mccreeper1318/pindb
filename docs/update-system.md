# Update system

PinDB checks the GitHub Releases API for `mccreeper1318/pindb`. Draft releases are ignored. Pre-releases are ignored unless the user enables them in Settings.

Supported tag forms include `0.3`, `v0.3.1`, `v.0.3.1`, and `0.3-beta.3`. Numeric components are compared numerically and pre-release identifiers use semantic-version ordering.

## Platform and package selection

- Debian, Ubuntu, Linux Mint, and related systems select `.deb` assets.
- Fedora, RHEL-family systems, and traditional Fedora spins select `.rpm` assets.
- Windows 11 selects the Windows x64 `.exe` installer.
- macOS selects a `.pkg` matching the exact host architecture:
  - Apple Silicon → `-macos-arm64.pkg`
  - Intel → `-macos-x64.pkg`
- Mac packages with the wrong architecture, unknown architecture, or no canonical architecture suffix are rejected rather than used as a fallback.
- Package assets must include `pindb` in the filename and match the platform/package rules.

Automatic installation requires a published SHA-256 checksum. Selection prefers the exact `<package>.sha256` asset. If an exact checksum is not present, PinDB can use the matching platform aggregate (`checksums-linux.sha256`, `checksums-windows.sha256`, or `checksums-macos.sha256`) and finally the generic `checksums.sha256`. It never substitutes another platform's aggregate checksum file.

Linux distribution classification reads `/etc/os-release`, with `/usr/lib/os-release` as a fallback. Windows and macOS are detected from the Java operating-system property. A release without a matching package for the detected platform and architecture is not offered as an installable update.

## Native data and cache paths

### macOS

PinDB follows native macOS user directories:

- Configuration: `~/Library/Application Support/PinDB`
- State/diagnostics: `~/Library/Application Support/PinDB/State`
- Cache: `~/Library/Caches/PinDB`
- Downloaded update packages: `~/Library/Caches/PinDB/updates`

Legacy Mac fallback credential data from the earlier XDG-style config location is migrated by the application where supported.

### Windows

- Configuration: `%APPDATA%\PinDB`
- State/cache: `%LOCALAPPDATA%\PinDB`

### Linux

PinDB honors XDG configuration, state, and cache variables with the normal home-directory fallbacks.

## macOS installation and updates

PinDB 0.3 macOS packages are self-contained `jpackage` PKGs for Apple Silicon and Intel. They include the private Java runtime and register `.pindb` files with the operating system.

The 0.3 PKGs are **unsigned and not notarized**. Users should download only from the official GitHub Release, verify the accompanying SHA-256 checksum, and approve only that verified package through the normal macOS security/Installer flow. PinDB does not disable or bypass Gatekeeper.

For an in-application update:

1. PinDB chooses only the PKG matching the current Mac architecture.
2. The package downloads to `~/Library/Caches/PinDB/updates`.
3. PinDB requires and verifies the published SHA-256 checksum before installation can continue.
4. With administrator approval, `/usr/bin/osascript` runs a fixed staging command that creates a unique directory under `/private/var/tmp`.
5. The verified PKG is copied into that unique directory as `PinDB-verified-update.pkg`, owned by `root:wheel` and read-only to unprivileged users.
6. The staged package is SHA-256 verified again after the privileged copy.
7. PinDB independently verifies the expected staging path, ownership, permissions, root-owned staging directory, sticky staging root, and digest.
8. `/usr/bin/open` opens the protected PKG in macOS Installer from a background task so the JavaFX UI is not blocked while LaunchServices responds.
9. PinDB exits only after `/usr/bin/open` succeeds. The user completes the installation in Installer and then reopens PinDB manually.
10. Pending release notes are displayed on the next launch.

Each successful staging attempt schedules a detached privileged cleanup of its own unique staging directory after the configured recovery period. This keeps the package available temporarily for manual recovery without leaving root-owned update files indefinitely.

If the staging or Installer handoff fails after a protected package exists, the error dialog retains that protected path and presents the native recovery command:

```text
/usr/bin/open "/private/var/tmp/PinDB-verified-update.<unique>/PinDB-verified-update.pkg"
```

## Windows 11 installation and updates

Windows releases are self-contained unsigned x64 `.exe` installers produced by `jpackage` on a Windows GitHub Actions runner. They include the Java runtime and install per-user.

The installer provides Start Menu integration, registers `.pindb` files, permits the installation directory to be selected, and can offer a desktop shortcut.

For an in-application Windows update, PinDB downloads the matching `.exe`, requires and verifies its SHA-256 checksum, launches that verified installer, and exits so the installer can replace the application. Because the installer is currently unsigned, Windows may identify the publisher as unknown or display SmartScreen warnings. Users should obtain installers only from the official PinDB GitHub Release.

## Linux installation

Updates always require approval. The selected package downloads to the user's XDG cache directory and is checksum-verified before installation. PinDB never runs scripts from that user-writable directory with elevated privileges.

- `pkexec` launches only the fixed `pindb-update-helper` shipped inside the root-owned native PinDB installation.
- The helper exposes only an `install` command accepting the package type, expected SHA-256 digest, and absolute downloaded-package path.
- The helper opens the package relative to a secure directory handle without following the final symbolic link, then copies from that open handle into a randomized `0700` root-owned directory under `/var/tmp`.
- The staged package is written with `0600` permissions and its SHA-256 digest is checked again before any package manager can open it.
- Debian packages are installed from the verified staged copy with `/usr/bin/apt-get`.
- Fedora RPMs are installed from the verified staged copy with `/usr/bin/dnf5`, falling back to `/usr/bin/dnf`.

Package replacement, symlink substitution, or modification between the desktop checksum and privileged installation therefore either leaves the already-open verified content unchanged or causes installation to stop with a checksum error. The package manager never receives the original user-writable cache path.

Before invoking the package manager, PinDB copies the existing `/opt/pindb` application directory. If the package-manager command fails, the updater restores those application files. Diagnostic details are written to the PinDB state directory.

After a successful Linux package installation, PinDB restarts the installed launcher and passes the release tag and release notes to the updated application.

## Fedora Atomic desktops

Fedora Atomic variants such as Silverblue and Kinoite are detected through `VARIANT_ID` in `os-release`. PinDB can identify the RPM release asset, but its normal DNF-based automatic installer is disabled on those immutable systems. Users must install or update the RPM with `rpm-ostree` and reboot into the new deployment.

## Manual recovery

When installation fails, the error dialog retains the usable package when safe and shows the platform-appropriate manual command.

```text
# Debian-family Linux
sudo apt install "/path/to/pindb.deb"

# Fedora traditional
sudo dnf install "/path/to/pindb.rpm"

# Fedora Atomic
sudo rpm-ostree install "/path/to/pindb.rpm"

# Windows
"C:\path\to\PinDB-version-windows-x64.exe"

# macOS protected staged package
/usr/bin/open "/private/var/tmp/PinDB-verified-update.<unique>/PinDB-verified-update.pkg"
```

## User checksum verification

Before manually installing any downloaded package, verify the package-specific checksum published on the same GitHub Release.

macOS:

```bash
shasum -a 256 -c PinDB-<version>-macos-arm64.pkg.sha256
shasum -a 256 -c PinDB-<version>-macos-x64.pkg.sha256
```

Linux:

```bash
sha256sum -c <package>.sha256
```

Windows PowerShell:

```powershell
Get-FileHash .\PinDB-<version>-windows-x64.exe -Algorithm SHA256
Get-Content .\PinDB-<version>-windows-x64.exe.sha256
```

The calculated hexadecimal digest must match the published checksum exactly.
