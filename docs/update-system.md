# Update system

PinDB checks the GitHub Releases API for `mccreeper1318/pindb`. Draft releases are ignored. Pre-releases are ignored unless the user enables them in Settings.

Supported tag forms include `0.2`, `v0.2.1`, `v.0.2.1`, and `0.2-beta.3`. Numeric components are compared numerically, and pre-release identifiers use semantic-version ordering.

## Platform and package selection

PinDB reads `/etc/os-release`, with `/usr/lib/os-release` as a fallback, to classify the running Linux distribution.

- Debian, Ubuntu, Linux Mint, and related systems select `.deb` assets.
- Fedora, RHEL-family systems, and traditional Fedora spins select `.rpm` assets.
- Package assets must include `pindb` in the filename and match the current CPU architecture.
- A matching `<package>.sha256`, `checksums.sha256`, or `checksums-linux.sha256` asset is required for automatic installation.

The initial official Debian and Fedora packages target 64-bit x86 systems. The selection model also recognizes ARM64 package names so that architecture can be added later without another updater redesign.

A release without a matching package for the detected distribution and architecture is not offered as an installable update.

## Installation

Updates always require approval. The selected package downloads to the user's XDG cache directory and is checksum-verified before installation. PinDB never runs scripts from that user-writable directory with elevated privileges.

- `pkexec` launches only the fixed `pindb-update-helper` shipped inside the root-owned native PinDB installation.
- The helper exposes only an `install` command accepting the package type, expected SHA-256 digest, and absolute downloaded-package path.
- The helper opens the package relative to a secure directory handle without following the final symbolic link, then copies from that open handle into a randomized `0700` root-owned directory under `/var/tmp`.
- The staged package is written with `0600` permissions and its SHA-256 digest is checked again before any package manager can open it.
- Debian packages are installed from the verified staged copy with `/usr/bin/apt-get`.
- Fedora RPMs are installed from the verified staged copy with `/usr/bin/dnf5`, falling back to `/usr/bin/dnf`.

Package replacement, symlink substitution, or modification between the desktop checksum and privileged installation therefore either leaves the already-open verified content unchanged or causes installation to stop with a checksum error. The package manager never receives the original user-writable cache path.

Before invoking the package manager, PinDB copies the existing `/opt/pindb` application directory. If the package-manager command fails, the updater restores those application files. Diagnostic details are written to `~/.local/state/pindb/update-error.log`.

After a successful package installation, PinDB restarts the installed launcher and passes the release tag and release notes to the updated application.

## Fedora Atomic desktops

Fedora Atomic variants such as Silverblue and Kinoite are detected through `VARIANT_ID` in `os-release`. PinDB can identify the RPM release asset, but its normal DNF-based automatic installer is disabled on those immutable systems. Users must install or update the RPM with `rpm-ostree` and reboot into the new deployment.

## Manual recovery

When installation fails, the error dialog retains the downloaded package and shows the appropriate manual command:

```text
sudo apt install "/path/to/pindb.deb"
sudo dnf install "/path/to/pindb.rpm"
sudo rpm-ostree install "/path/to/pindb.rpm"
```
