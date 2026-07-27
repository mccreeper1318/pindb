# Release process

1. Merge the intended changes to `main` and confirm the CI workflow passes.
2. Create a tag such as `0.1`, `0.2`, `v0.2.1`, or `0.2-beta.1`.
3. Push the tag to GitHub.
4. The release workflow validates the tag, runs tests, builds the `.deb`, generates SHA-256 files, and publishes the assets to a GitHub Release.
5. Edit the generated release notes when more detail is needed.
6. Test the installed release's update check against the next test release before broadly announcing it.

The Gradle version is supplied by the workflow from the normalized tag rather than being permanently duplicated in source files.
