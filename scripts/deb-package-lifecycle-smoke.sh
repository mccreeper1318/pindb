#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -ne 2 ]]; then
  echo "Usage: $0 <baseline.deb> <candidate.deb>" >&2
  exit 2
fi

BASELINE="$(realpath "$1")"
CANDIDATE="$(realpath "$2")"

baseline_version="$(dpkg-deb -f "$BASELINE" Version)"
candidate_version="$(dpkg-deb -f "$CANDIDATE" Version)"

if ! dpkg --compare-versions "$candidate_version" gt "$baseline_version"; then
  echo "Candidate Debian package must be newer than baseline: $baseline_version -> $candidate_version" >&2
  exit 1
fi

sudo apt-get install -y "$BASELINE"
installed="$(dpkg-query -W -f='${Version}' pindb)"
[[ "$installed" == "$baseline_version" ]] || {
  echo "Baseline Debian install mismatch: expected $baseline_version, got $installed" >&2
  exit 1
}

sudo apt-get install -y "$CANDIDATE"
installed="$(dpkg-query -W -f='${Version}' pindb)"
[[ "$installed" == "$candidate_version" ]] || {
  echo "Debian upgrade mismatch: expected $candidate_version, got $installed" >&2
  exit 1
}

test -x /opt/pindb/bin/PinDB
test -x /opt/pindb/bin/pindb-update-helper

sudo apt-get purge -y pindb
if dpkg-query -W pindb >/dev/null 2>&1; then
  echo "Debian package still appears installed after purge." >&2
  exit 1
fi
if [[ -e /opt/pindb ]]; then
  echo "/opt/pindb remains after Debian package removal." >&2
  find /opt/pindb -maxdepth 3 -print >&2 || true
  exit 1
fi
