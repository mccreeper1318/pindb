#!/bin/bash
set -euo pipefail

if [[ "$#" -ne 3 ]]; then
  echo "Usage: prepare-resources.sh <source-png> <output-icns> <associations-properties>" >&2
  exit 64
fi

SOURCE_ICON="$1"
OUTPUT_ICON="$2"
ASSOCIATIONS_FILE="$3"
OUTPUT_DIR="$(dirname "$OUTPUT_ICON")"
DOCUMENT_ICON="$OUTPUT_DIR/PinDBDocument.icns"
ICONSET="$OUTPUT_DIR/PinDB.iconset"

mkdir -p "$OUTPUT_DIR"
rm -rf "$ICONSET"
mkdir -p "$ICONSET"

resize_icon() {
  local size="$1"
  local name="$2"
  sips -z "$size" "$size" "$SOURCE_ICON" --out "$ICONSET/$name" >/dev/null
}

resize_icon 16 icon_16x16.png
resize_icon 32 icon_16x16@2x.png
resize_icon 32 icon_32x32.png
resize_icon 64 icon_32x32@2x.png
resize_icon 128 icon_128x128.png
resize_icon 256 icon_128x128@2x.png
resize_icon 256 icon_256x256.png
resize_icon 512 icon_256x256@2x.png
resize_icon 512 icon_512x512.png
resize_icon 1024 icon_512x512@2x.png

iconutil -c icns "$ICONSET" -o "$OUTPUT_ICON"
cp "$OUTPUT_ICON" "$DOCUMENT_ICON"
rm -rf "$ICONSET"

cat > "$ASSOCIATIONS_FILE" <<EOF
extension=pindb
mime-type=application/x-pindb
description=PinDB Database
icon=$DOCUMENT_ICON
EOF

[[ -s "$OUTPUT_ICON" ]]
[[ -s "$DOCUMENT_ICON" ]]
[[ -s "$ASSOCIATIONS_FILE" ]]
