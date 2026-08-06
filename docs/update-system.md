# Update system

PinDB checks the GitHub Releases API for `mccreeper1318/pindb`. Draft releases are ignored. Pre-releases are ignored unless the user enables them in Settings.

Supported tag forms include `0.2`, `v0.2.1`, `v.0.2.1`, and `0.2-beta.3`. Numeric components are compared numerically, and pre-release identifiers use semantic-version ordering.

## Platform and package selection

PinDB reads `/etc/os-release`, with `/usr/lib/os-release` as a fallback, to classify the running Linux distribution.

- Debian, Ubuntu, Linux Mint, and related systems select `.deb` assets.
- Fedora, RHEL-family systems, and traditional Fedora spins select `.rpm` assets.
- Package assets must include `pindb` in the filename and match the current CPU architecture.
- A matching `<package>.sha256`, `checksums.sha256`, or `checksums-linux.sha256` asset is used when available.

A release without a matching package for the detected distribution and architecture is not offered as an installable update.

## Installation

Updates always require approval. The selected package downloads to the user's XDG cache directory and is checksum-verified before installation.

- Debian packages are installed with `/usr/bin/apt-get` through `pkexec`.
- Fedora RPMs prefer `/usr/bin/dnf5` and fall back to `/usr/bin/dnf`, also through `pkexec`.

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
