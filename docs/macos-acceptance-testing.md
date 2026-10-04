# macOS 0.3 Acceptance Testing

This checklist is the manual acceptance gate for Issue #79. Run it on both a native Apple Silicon Mac and a native Intel Mac using the packaged `.pkg` build for the candidate PinDB 0.3 release.

## Automated regression coverage

The normal macOS CI matrix runs the full Java test suite on both `macos-15` (Apple Silicon) and `macos-15-intel` (Intel), then builds and verifies the native PKG. Automated coverage includes:

- strict ARM64/x64 macOS package selection and rejection of wrong, unknown, or unmarked architectures;
- package-specific and macOS aggregate checksum discovery;
- native `~/Library/Application Support/PinDB` configuration/state paths and `~/Library/Caches/PinDB` cache paths;
- native Installer command construction and protected staging rules;
- manual recovery command construction;
- prevention of macOS packages entering Linux `pkexec`, DEB, or RPM privileged-update paths;
- the existing Windows, Debian, and Fedora updater regression tests;
- PKG launcher/runtime architecture, bundle metadata, icon, and `.pindb` association verification.

Automated tests are required to be green before beginning the manual matrix below.

## Test preparation

For each architecture:

1. Use a clean test account or record the current PinDB user-data state before testing.
2. Download the architecture-matched PKG and its `.sha256` file from the same GitHub Release.
3. Verify the checksum before installation.
4. Record the exact PinDB version, macOS version, Mac model, and architecture in the results table.
5. Keep at least one disposable `.pindb` database for destructive restore/trash/update testing.

| Test environment | Apple Silicon | Intel |
| --- | --- | --- |
| PinDB version |  |  |
| macOS version |  |  |
| Mac/model |  |  |
| Architecture confirmed | [ ] ARM64 | [ ] x86_64 |
| PKG checksum verified | [ ] | [ ] |

## Installation and launch

| Check | Apple Silicon | Intel | Notes/evidence |
| --- | --- | --- | --- |
| Fresh PKG install completes | [ ] | [ ] | |
| PinDB launches from Applications/Finder | [ ] | [ ] | |
| App icon is correct in Finder/Dock | [ ] | [ ] | |
| No unexpected console/startup error is shown | [ ] | [ ] | |
| `.pindb` file association appears in Finder | [ ] | [ ] | |

## Database lifecycle and file association

| Check | Apple Silicon | Intel | Notes/evidence |
| --- | --- | --- | --- |
| Create a new `.pindb` database | [ ] | [ ] | |
| Add records and save/close | [ ] | [ ] | |
| Reopen the database from PinDB | [ ] | [ ] | |
| Reopen the database directly from Finder | [ ] | [ ] | |
| Database contents persist after reopening | [ ] | [ ] | |
| Multiple normal open/close cycles complete cleanly | [ ] | [ ] | |

## Fields and validation

Create fields covering every supported field type and exercise valid and invalid values.

| Check | Apple Silicon | Intel | Notes/evidence |
| --- | --- | --- | --- |
| Text fields | [ ] | [ ] | |
| Number fields | [ ] | [ ] | |
| Date/time fields | [ ] | [ ] | |
| Boolean/choice fields where supported | [ ] | [ ] | |
| Required-field validation | [ ] | [ ] | |
| Type/range/format validation | [ ] | [ ] | |
| Validation errors do not corrupt saved records | [ ] | [ ] | |

## Embedded documents

Attach, save, reopen, and view each supported document category.

| Check | Apple Silicon | Intel | Notes/evidence |
| --- | --- | --- | --- |
| PDF document | [ ] | [ ] | |
| DOCX document | [ ] | [ ] | |
| Image document | [ ] | [ ] | |
| Plain-text document | [ ] | [ ] | |
| Embedded documents survive close/reopen | [ ] | [ ] | |

## Views, search, and summaries

| Check | Apple Silicon | Intel | Notes/evidence |
| --- | --- | --- | --- |
| Search finds expected records | [ ] | [ ] | |
| Sort ascending/descending | [ ] | [ ] | |
| Filters include/exclude expected records | [ ] | [ ] | |
| Summaries calculate correctly | [ ] | [ ] | |
| Table view behaves normally | [ ] | [ ] | |
| Record view behaves normally | [ ] | [ ] | |

## CSV import/export

| Check | Apple Silicon | Intel | Notes/evidence |
| --- | --- | --- | --- |
| Export records to CSV | [ ] | [ ] | |
| Exported CSV contains expected headers/data | [ ] | [ ] | |
| Import a valid CSV | [ ] | [ ] | |
| Imported values map to the expected fields | [ ] | [ ] | |
| Invalid import data reports a useful error | [ ] | [ ] | |

## Backup, restore, and Recently Deleted

| Check | Apple Silicon | Intel | Notes/evidence |
| --- | --- | --- | --- |
| Create internal backup | [ ] | [ ] | |
| Change data after backup | [ ] | [ ] | |
| Restore backup successfully | [ ] | [ ] | |
| Restored database matches expected state | [ ] | [ ] | |
| Delete a record to Recently Deleted | [ ] | [ ] | |
| Restore a deleted record | [ ] | [ ] | |
| Permanently remove a disposable deleted record | [ ] | [ ] | |

## Printing

| Check | Apple Silicon | Intel | Notes/evidence |
| --- | --- | --- | --- |
| Print preview opens | [ ] | [ ] | |
| Preview contains expected fields/records | [ ] | [ ] | |
| System print dialog opens | [ ] | [ ] | |
| Print/PDF output completes without PinDB error | [ ] | [ ] | |

## Appearance

| Check | Apple Silicon | Intel | Notes/evidence |
| --- | --- | --- | --- |
| Light theme | [ ] | [ ] | |
| Dark theme | [ ] | [ ] | |
| System theme follows macOS setting | [ ] | [ ] | |
| Dialogs/popups remain readable in each theme | [ ] | [ ] | |

## GitHub integration

| Check | Apple Silicon | Intel | Notes/evidence |
| --- | --- | --- | --- |
| Device authorization starts successfully | [ ] | [ ] | |
| Authorization completes and persists | [ ] | [ ] | |
| Bug report can be prepared/submitted | [ ] | [ ] | |
| Sign-out/re-authentication behaves normally | [ ] | [ ] | |

## Update behavior

Use disposable beta releases when validating the real update flow.

| Check | Apple Silicon | Intel | Notes/evidence |
| --- | --- | --- | --- |
| Stable-only update check ignores prereleases | [ ] | [ ] | |
| Prerelease-enabled update check sees newer beta | [ ] | [ ] | |
| Wrong-architecture PKG is not selected | [ ] | [ ] | |
| Missing/mismatched checksum blocks installation | [ ] | [ ] | |
| Real Beta-to-Beta update downloads matching unsigned PKG | [ ] | [ ] | |
| Administrator approval secures the verified PKG | [ ] | [ ] | |
| macOS Installer opens normally | [ ] | [ ] | |
| PinDB exits cleanly after Installer handoff | [ ] | [ ] | |
| Updated PinDB launches after manual reopen | [ ] | [ ] | |
| Pending release notes appear on the next launch | [ ] | [ ] | |
| Existing database data remains intact after update | [ ] | [ ] | |

## Uninstall and reinstall

| Check | Apple Silicon | Intel | Notes/evidence |
| --- | --- | --- | --- |
| Remove the installed PinDB application/package | [ ] | [ ] | |
| User-created `.pindb` files remain untouched | [ ] | [ ] | |
| Reinstall the same/newer PKG | [ ] | [ ] | |
| Existing user database opens after reinstall | [ ] | [ ] | |
| PinDB settings/state behave as expected after reinstall | [ ] | [ ] | |

## Expected macOS-specific behavior

These differences are intentional and are not parity failures:

- The current PinDB macOS PKG is unsigned. Gatekeeper or Installer may require explicit user approval depending on the Mac's security policy.
- PinDB securely stages the checksum-verified PKG with administrator approval before handing it to macOS Installer.
- PinDB closes after handing the PKG to Installer. Unlike the current Windows/Linux path, the user must reopen PinDB after Installer finishes.
- Protected staged update packages are retained temporarily for recovery and are automatically cleaned up after the configured retention period.

Any additional architecture-specific limitation discovered during this checklist must be documented before PinDB 0.3 stable is released.

## Acceptance record

Issue #79 is ready to close only when:

- [ ] all automated CI checks are green on both Mac architectures;
- [ ] every required Apple Silicon manual check passes or has a documented accepted limitation;
- [ ] every required Intel manual check passes or has a documented accepted limitation;
- [ ] a real Beta-to-Beta update succeeds on both architectures;
- [ ] release notes display after both updates;
- [ ] no unresolved macOS-only regression remains.
