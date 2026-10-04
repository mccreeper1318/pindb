# Release process

PinDB 0.3 publishes native installers for Windows 11, Debian-family Linux, Fedora-family Linux, and macOS on both Apple Silicon and Intel.

## Release checklist

1. Merge the intended release changes to `main`.
2. Confirm the platform CI matrix is green on the release commit:
   - Windows x64
   - Debian package build/tests
   - Fedora RPM build/tests
   - macOS Apple Silicon (`macos-15`)
   - macOS Intel (`macos-15-intel`)
3. Create a GitHub Release targeting `main` with a supported tag such as `0.3`, `v0.3.1`, or `0.3-beta.5`.
4. Mark the GitHub Release as a **pre-release** when the tag contains a prerelease suffix such as `-beta.5`. Stable tags must be normal releases.
5. Add the matching consolidated changelog notes to the release description and publish the GitHub Release.
6. Publishing the release triggers independent native release workflows:
   - Ubuntu builds and verifies the self-contained `.deb`.
   - Fedora builds and verifies the self-contained `.rpm`.
   - Windows builds and verifies the x64 `.exe`.
   - Apple Silicon macOS builds and verifies the ARM64 `.pkg`.
   - Intel macOS builds and verifies the x64 `.pkg`.
7. Each native package receives a package-specific `.sha256` file.
8. Confirm the complete expected asset set is attached to the GitHub Release.
9. Verify the published checksum for each package before installation testing.
10. Test fresh installation and the in-application update path on the supported platform matrix before broadly announcing the release.

The Gradle application version is supplied from the normalized release tag rather than duplicated permanently in source files.

## Expected 0.3 release assets

A complete 0.3 native release contains 10 package/checksum assets:

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

The GitHub-generated source archives are additional release downloads and are not counted in the 10 native package/checksum assets.

## Native package versions

For a prerelease such as `0.3-beta.5`:

- Application/release version: `0.3-beta.5`
- Windows filename version: `0.3-beta.5`
- macOS filename version: `0.3-beta.5`
- Debian package version: `0.3-0~beta.5`
- RPM package version-release: `0.3-0.beta.5`

For a stable release such as `0.3`:

- Application/release version: `0.3`
- Windows filename version: `0.3`
- macOS filename version: `0.3`
- Debian package version: `0.3-1`
- RPM package version-release: `0.3-1`

The lower Linux prerelease package release ensures that the stable package is considered newer by both package families.

## macOS release automation

The macOS release workflow runs on the two native GitHub-hosted Mac architectures:

- `macos-15` → Apple Silicon / ARM64
- `macos-15-intel` → Intel / x86_64

Each job:

1. Checks out the exact `refs/tags/<release-tag>` ref rather than a similarly named branch.
2. Uses Java 25.
3. Verifies the runner architecture.
4. Runs the test suite and `packageMacPkg`.
5. Runs `packaging/macos/verify-package.sh` to verify package architecture, launcher/runtime architecture, bundle/package metadata, icon metadata, and `.pindb` association.
6. Generates and verifies the package-specific SHA-256 file.
7. Uploads the PKG/checksum pair as a workflow artifact.

The final publishing job receives release-write permission only after both native builds succeed. Build jobs themselves use read-only repository access and do not retain checkout credentials.

When rebuilding an existing release, the macOS publisher performs a staged transactional swap instead of destructively using `--clobber`:

- all four replacement Mac assets are uploaded under non-canonical temporary names;
- GitHub-side digest and size are verified before existing canonical assets are touched;
- old assets are retained as temporary backups;
- checksum assets are promoted before their matching packages so clients do not see a package without a checksum;
- the new canonical set is verified before backups are removed;
- failures, interruptions, and termination signals trigger rollback logic;
- same-tag macOS release runs are serialized to avoid concurrent replacement of the same assets.

Temporary/backup Mac asset names intentionally do not match the updater's canonical `-macos-arm64.pkg` / `-macos-x64.pkg` suffixes, so the application ignores them during a replacement transaction.

## macOS signing and notarization status

PinDB 0.3 macOS PKGs are currently **unsigned and not notarized**. This is an intentional current limitation, not a build failure.

Release notes and the website must make this limitation visible before download. Users should be told to:

1. download the PKG and matching `.sha256` only from the official PinDB GitHub Release;
2. verify the SHA-256 digest;
3. open only that verified package;
4. approve it using the normal macOS security/Installer controls if Gatekeeper warns about the unidentified developer;
5. never disable Gatekeeper globally as part of PinDB installation guidance.

## Release acceptance

Before a stable 0.3 release is considered ready:

- all native CI jobs must be green;
- all expected release assets and checksums must be present;
- the website live release lookup must identify the correct Windows, Linux, Apple Silicon Mac, and Intel Mac assets;
- the macOS manual acceptance checklist in `docs/macos-acceptance-testing.md` must be completed on both architectures;
- a real beta-to-beta macOS update must succeed on both architectures;
- any remaining platform-specific limitations must be documented in the release notes, README, and website.
