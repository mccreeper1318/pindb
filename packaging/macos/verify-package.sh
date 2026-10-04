#!/bin/bash
set -euo pipefail

if [[ "$#" -ne 3 ]]; then
  echo "Usage: verify-package.sh <pkg> <arm64|x64> <package-identifier>" >&2
  exit 64
fi

PKG="$1"
EXPECTED_ARCH="$2"
EXPECTED_IDENTIFIER="$3"

case "$EXPECTED_ARCH" in
  arm64)
    EXPECTED_MACHINE="arm64"
    ;;
  x64)
    EXPECTED_MACHINE="x86_64"
    ;;
  *)
    echo "Unsupported expected macOS architecture: $EXPECTED_ARCH" >&2
    exit 64
    ;;
esac

if [[ ! -s "$PKG" ]]; then
  echo "macOS PKG is missing or empty: $PKG" >&2
  exit 1
fi

if [[ "$(basename "$PKG")" != *"-macos-${EXPECTED_ARCH}.pkg" ]]; then
  echo "macOS PKG filename does not match expected architecture $EXPECTED_ARCH: $PKG" >&2
  exit 1
fi

HOST_ARCH="$(uname -m)"
if [[ "$HOST_ARCH" != "$EXPECTED_MACHINE" ]]; then
  echo "Runner architecture mismatch: expected $EXPECTED_MACHINE, got $HOST_ARCH" >&2
  exit 1
fi

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT
EXPANDED="$WORK_DIR/expanded"

pkgutil --expand-full "$PKG" "$EXPANDED"

APP_LIST="$WORK_DIR/apps.txt"
find "$EXPANDED" -type d -name 'PinDB.app' -prune -print > "$APP_LIST"
APP_COUNT="$(wc -l < "$APP_LIST" | tr -d '[:space:]')"
if [[ "$APP_COUNT" != "1" ]]; then
  echo "Expected exactly one PinDB.app in the PKG; found $APP_COUNT." >&2
  cat "$APP_LIST" >&2 || true
  exit 1
fi
APP="$(cat "$APP_LIST")"
INFO_PLIST="$APP/Contents/Info.plist"
LAUNCHER="$APP/Contents/MacOS/PinDB"
RUNTIME_HOME="$APP/Contents/runtime/Contents/Home"

if [[ ! -f "$INFO_PLIST" ]]; then
  echo "PinDB.app is missing Contents/Info.plist." >&2
  exit 1
fi
if [[ ! -x "$LAUNCHER" ]]; then
  echo "PinDB.app is missing its executable launcher." >&2
  exit 1
fi
if [[ ! -d "$RUNTIME_HOME" ]]; then
  echo "PinDB.app is missing its bundled Java runtime image." >&2
  exit 1
fi

RUNTIME_BINARY=""
for candidate in \
  "$RUNTIME_HOME/lib/libjli.dylib" \
  "$RUNTIME_HOME/lib/server/libjvm.dylib"; do
  if [[ -f "$candidate" ]]; then
    RUNTIME_BINARY="$candidate"
    break
  fi
done
if [[ -z "$RUNTIME_BINARY" ]]; then
  echo "PinDB.app runtime image does not contain libjli.dylib or libjvm.dylib." >&2
  find "$RUNTIME_HOME" -maxdepth 3 -type f -print >&2 || true
  exit 1
fi

LAUNCHER_INFO="$(file "$LAUNCHER")"
RUNTIME_INFO="$(file "$RUNTIME_BINARY")"
echo "$LAUNCHER_INFO"
echo "$RUNTIME_INFO"
if ! grep -Fq "$EXPECTED_MACHINE" <<< "$LAUNCHER_INFO"; then
  echo "PinDB launcher architecture does not match $EXPECTED_MACHINE." >&2
  exit 1
fi
if ! grep -Fq "$EXPECTED_MACHINE" <<< "$RUNTIME_INFO"; then
  echo "Bundled Java runtime architecture does not match $EXPECTED_MACHINE." >&2
  exit 1
fi

if ! find "$EXPANDED" -type f -name PackageInfo -exec grep -lF "identifier=\"$EXPECTED_IDENTIFIER\"" {} \; | grep -q .; then
  echo "The PKG metadata does not contain the expected identifier: $EXPECTED_IDENTIFIER" >&2
  exit 1
fi

python3 - "$INFO_PLIST" "$EXPECTED_IDENTIFIER" <<'PY'
import plistlib
import sys

plist_path, expected_identifier = sys.argv[1:]
with open(plist_path, "rb") as handle:
    plist = plistlib.load(handle)

bundle_identifier = plist.get("CFBundleIdentifier")
if bundle_identifier != expected_identifier:
    raise SystemExit(
        f"Unexpected CFBundleIdentifier: {bundle_identifier!r}; expected {expected_identifier!r}"
    )

bundle_name = plist.get("CFBundleName") or plist.get("CFBundleDisplayName")
if bundle_name != "PinDB":
    raise SystemExit(f"Unexpected PinDB bundle name: {bundle_name!r}")


def values(value):
    if value is None:
        return set()
    if isinstance(value, (list, tuple)):
        return {str(item).lower() for item in value}
    return {str(value).lower()}


declarations = {}
for declaration_key in ("UTExportedTypeDeclarations", "UTImportedTypeDeclarations"):
    for declaration in plist.get(declaration_key, []):
        identifier = declaration.get("UTTypeIdentifier")
        if identifier:
            declarations[str(identifier)] = declaration.get("UTTypeTagSpecification", {})

association_found = False
association_details = []
for document_type in plist.get("CFBundleDocumentTypes", []):
    direct_extensions = values(document_type.get("CFBundleTypeExtensions"))
    direct_mime_types = values(document_type.get("CFBundleTypeMIMETypes"))
    if "pindb" in direct_extensions and "application/x-pindb" in direct_mime_types:
        association_found = True
        association_details.append("legacy CFBundleDocumentTypes tags")

    for uti in values(document_type.get("LSItemContentTypes")):
        tags = declarations.get(uti, {})
        linked_extensions = values(tags.get("public.filename-extension"))
        linked_mime_types = values(tags.get("public.mime-type"))
        if "pindb" in linked_extensions and "application/x-pindb" in linked_mime_types:
            association_found = True
            association_details.append(f"referenced UTI {uti}")

if not association_found:
    referenced = sorted({
        uti
        for document_type in plist.get("CFBundleDocumentTypes", [])
        for uti in values(document_type.get("LSItemContentTypes"))
    })
    raise SystemExit(
        "PinDB.app does not link a document type to both the .pindb extension and "
        "application/x-pindb MIME type; referenced UTIs: " + repr(referenced)
    )

icon_name = plist.get("CFBundleIconFile")
if not icon_name:
    raise SystemExit("PinDB.app does not declare a CFBundleIconFile")

print(f"Verified bundle identifier: {bundle_identifier}")
print("Verified .pindb association through " + ", ".join(association_details))
print(f"Verified application icon metadata: {icon_name}")
PY

ICON_NAME="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIconFile' "$INFO_PLIST")"
if [[ "$ICON_NAME" != *.icns ]]; then
  ICON_NAME="${ICON_NAME}.icns"
fi
if [[ ! -s "$APP/Contents/Resources/$ICON_NAME" ]]; then
  echo "PinDB.app icon resource is missing or empty: $ICON_NAME" >&2
  exit 1
fi

echo "Verified macOS PKG: $PKG"
echo "Verified architecture: $EXPECTED_MACHINE"
echo "Verified bundled runtime binary: $RUNTIME_BINARY"
echo "Verified package and bundle identifier: $EXPECTED_IDENTIFIER"
