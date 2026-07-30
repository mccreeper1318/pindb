# Update system

PinDB checks the GitHub Releases API for `mccreeper1318/pindb`. Draft releases are ignored. Pre-releases are ignored unless the user enables them in Settings.

Supported tag forms include `0.2`, `v0.2.1`, `v.0.2.1`, and `0.2-beta.2`. Numeric components are compared numerically, and pre-release identifiers use semantic-version ordering.

An eligible release must include a `.deb` asset whose name includes `pindb`. A matching `<package>.sha256` file or `checksums.sha256` file is used when available.

Updates always require approval. The package downloads to the user's XDG cache directory, is checksum-verified, and is installed with `apt-get` through `pkexec`. PinDB closes only after the installer helper has been prepared. The helper relaunches PinDB after installation and passes the release notes back to the updated application.

If installation fails, the helper attempts to restore the previous application files and reopens PinDB with a choice to retain or delete the downloaded package.
