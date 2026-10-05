# Issue #40 checksum hardening

Automatic PinDB updates are installable only when the selected native package has a published SHA-256 checksum asset.

Checksum verification requires an exact, case-sensitive filename entry for the downloaded package. Standard `sha256sum` binary-mode entries using a leading `*` before the filename are accepted. Bare hashes, path-prefixed filenames, case-normalized filenames, and unrelated single checksum entries are rejected.

Package selection still prefers the exact `<package>.sha256` asset, then the matching platform aggregate checksum file, then the generic aggregate checksum file. Aggregate files are accepted only if their contents include the exact selected package filename.

These checks are enforced both while choosing an automatically installable update and again immediately before installation.
