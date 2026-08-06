# Release process

1. Merge the intended changes to `main` and confirm that both the Debian and Fedora CI jobs pass on the merged commit.
2. Create a GitHub Release targeting `main` with a tag such as `0.2`, `v0.2.1`, or `0.2-beta.3`.
3. Mark a release as a **pre-release** when its tag contains a suffix such as `-beta.3`. Stable tags must be published as normal releases.
4. Add the matching changelog section to the release description and publish the GitHub Release.
5. Publishing the release triggers the release workflow. It validates the tag and GitHub release type, then builds the native packages independently:
   - Ubuntu builds and verifies the self-contained `.deb`.
   - Fedora builds and verifies the self-contained `.rpm`.
6. Each build creates a package-specific SHA-256 checksum.
7. The publish job downloads the four build artifacts and attaches the `.deb`, `.deb.sha256`, `.rpm`, and `.rpm.sha256` files to the GitHub Release.
8. Confirm that all four assets were attached successfully.
9. Test fresh installation and in-application updating on a Debian-family system and a traditional Fedora installation before broadly announcing the release.

The Gradle version is supplied by the workflow from the normalized release tag rather than being permanently duplicated in source files.

## Native package versions

For a pre-release such as `0.2-beta.3`:

- Debian package version: `0.2-0~beta.3`
- RPM package version-release: `0.2-0.beta.3`

For a stable release such as `0.2`:

- Debian package version: `0.2-1`
- RPM package version-release: `0.2-1`

The lower pre-release package release ensures that the stable package is considered newer by both package families.
