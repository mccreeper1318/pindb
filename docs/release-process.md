# Release process

1. Merge the intended changes to `main` and confirm the CI workflow passes on the merged commit.
2. Create a GitHub Release targeting `main` with a tag such as `0.2`, `v0.2.1`, or `0.2-beta.2`.
3. Mark a release as a **pre-release** when its tag contains a pre-release suffix such as `-beta.2`. Stable tags must be published as normal releases.
4. Add the matching changelog section to the release description and publish the GitHub Release.
5. Publishing the release triggers the release workflow, which checks out the tag, validates the version, runs tests, builds the `.deb`, verifies the Debian package version, generates a SHA-256 checksum, and uploads both files to the release.
6. Confirm that the `.deb` and matching `.sha256` assets were attached successfully.
7. Test installation and the in-application update path before broadly announcing the release.

The Gradle version is supplied by the workflow from the normalized release tag rather than being permanently duplicated in source files.
