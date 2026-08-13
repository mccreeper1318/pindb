# Fedora testing checklist

Use this checklist before announcing an RPM release.

## Build verification

- Run `./gradlew clean test packageRpm -PappVersion=<version>` on Fedora.
- Confirm exactly one `.rpm` is written to `build/packages/`.
- Inspect metadata with `rpm -qpi build/packages/*.rpm`.
- Inspect installed paths with `rpm -qpl build/packages/*.rpm`.
- Confirm the package version and release match the GitHub release tag.
- Generate and verify the package SHA-256 checksum.

## Fresh installation

- Test on a current Fedora Workstation installation.
- Test on the current Fedora KDE edition or spin.
- Install graphically through Software or Discover.
- Install from a terminal with `sudo dnf install ./pindb-*.x86_64.rpm`.
- Confirm PinDB appears in the application menu under Office.
- Confirm the PinDB icon is displayed correctly.
- Confirm `.pindb` files open by double-clicking them.
- Confirm uninstalling PinDB does not delete databases stored in user folders.

## Application behavior

- Create, save, close, and reopen a database.
- Open a database created on a Debian-family installation.
- Test table and record-card views.
- Test CSV import and export.
- Test PDF, DOCX, text, and image document previews.
- Test database and document printing through Fedora's configured printer system.
- Test GitHub device authorization and in-application bug reporting.
- Confirm configuration, cache, and state files use the normal XDG user directories.

## Automatic update

- Publish a private or pre-release RPM and matching checksum asset.
- Enable pre-release updates in PinDB when testing a beta.
- Confirm Fedora selects the RPM rather than the Debian package.
- Confirm the correct x86-64 asset is selected when multiple architectures exist.
- Confirm PinDB prefers `dnf5` and falls back to `dnf` when required.
- Confirm the `pkexec` administrator prompt appears.
- Complete a successful RPM-to-RPM update.
- Confirm PinDB restarts and displays the release notes.
- Test a deliberately invalid checksum and confirm installation is refused.
- Test a failed package-manager command and confirm the previous application files are preserved.
- Confirm the failure dialog shows the downloaded RPM, manual DNF command, and diagnostic log path.

## Fedora Atomic variants

- Test detection on Silverblue and Kinoite.
- Confirm PinDB does not attempt a DNF-based automatic installation.
- Confirm the user is directed to install the RPM with `rpm-ostree` and reboot.
