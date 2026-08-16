# Update system

PinDB checks the GitHub Releases API for `mccreeper1318/pindb`. Draft releases are ignored. Pre-releases are ignored unless the user enables them in Settings.

Supported tag forms include `0.2`, `v0.2.1`, `v.0.2.1`, and `0.2-beta.3`. Numeric components are compared numerically, and pre-release identifiers use semantic-version ordering.

## Platform and package selection

- Debian, Ubuntu, Linux Mint, and related systems select `.deb` assets.
- Fedora, RHEL-family systems, and traditional Fedora spins select `.rpm` assets.
- Windows 11 selects the Windows `.exe` installer asset.
- Package assets must include `pindb` in the filename and match the current CPU architecture when an architecture is encoded in the filename.
- A matching `<package>.sha256`, `checksums.sha256`, `checksums-linux.sha256`, or `checksums-windows.sha256` asset is required for automatic installation.

Linux distribution classification reads `/etc/os-release`, with `/usr/lib/os-release` as a fallback. Windows is detected from the Java operating-system property. The initial official Debian, Fedora, and Windows packages target 64-bit x86 systems. The selection model also recognizes ARM64 package names for future expansion.

A release without a matching package for the detected platform and architecture is not offered as an installable update.

## Linux installation

Updates always require approval. The selected package downloads to the user's XDG cache directory and is checksum-verified before installation. PinDB never runs scripts from that user-writable directory with elevated privileges.

- `pkexec` launches only the fixed `pindb-update-helper` shipped inside the root-owned native PinDB installation.
- The helper exposes only an `install` command accepting the package type, expected SHA-256 digest, and absolute downloaded-package path.
- The helper opens the package relative to a secure directory handle without following the final symbolic link, then copies from that open handle into a randomized `0700` root-owned directory under `/var/tmp`.
- The staged package is written with `0600` permissions and its SHA-256 digest is checked again before any package manager can open it.
- Debian packages are installed from the verified staged copy with `/usr/bin/apt-get`.
- Fedora RPMs are installed from the verified staged copy with `/usr/bin/dnf5`, falling back to `/usr/bin/dnf`.

Package replacement, symlink substitution, or modification between the desktop checksum and privileged installation therefore either leaves the already-open verified content unchanged or causes installation to stop with a checksum error. The package manager never receives the original user-writable cache path.

Before invoking the package manager, PinDB copies the existing `/opt/pindb` application directory. If the package-manager command fails, the updater restores those application files. Diagnostic details are written to `~/.local/state/pindb/update-error.log`.

After a successful Linux package installation, PinDB restarts the installed launcher and passes the release tag and release notes to the updated application.

## Windows 11 installation and updates

Windows releases are self-contained unsigned x64 `.exe` installers produced by `jpackage` on a Windows GitHub Actions runner. They include the Java runtime and install per-user, so a separate Java installation is not required and normal installation does not target the system-wide Program Files directory.

The installer creates Start Menu integration, registers `.pindb` files, permits the user to choose the installation directory, and can offer a desktop shortcut. PinDB configuration is stored under `%APPDATA%\\PinDB`; state, cache, update downloads, and diagnostics are stored under `%LOCALAPPDATA%\\PinDB`.

For an in-application Windows update, PinDB downloads the matching `.exe`, requires and verifies its SHA-256 checksum, launches that verified installer, and exits so the installer can replace the application. Because the installer is currently unsigned, Windows may identify the publisher as unknown or display SmartScreen warnings. Users should obtain installers only from the official PinDB GitHub release.

## Fedora Atomic desktops

Fedora Atomic variants such as Silverblue and Kinoite are detected through `VARIANT_ID` in `os-release`. PinDB can identify the RPM release asset, but its normal DNF-based automatic installer is disabled on those immutable systems. Users must install or update the RPM with `rpm-ostree` and reboot into the new deployment.

## Manual recovery

When installation fails, the error dialog retains the downloaded package and shows the appropriate manual command. On Windows, run the retained `.exe` directly.

```text
sudo apt install "/path/to/pindb.deb"
sudo dnf install "/path/to/pindb.rpm"
sudo rpm-ostree install "/path/to/pindb.rpm"
"C:\\path\\to\\PinDB-version-windows-x64.exe"
```
