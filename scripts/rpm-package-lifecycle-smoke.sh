#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -ne 2 ]]; then
  echo "Usage: $0 <baseline.rpm> <candidate.rpm>" >&2
  exit 2
fi

BASELINE="$(realpath "$1")"
CANDIDATE="$(realpath "$2")"

baseline_version="$(rpm -qp --qf '%{VERSION}-%{RELEASE}' "$BASELINE")"
candidate_version="$(rpm -qp --qf '%{VERSION}-%{RELEASE}' "$CANDIDATE")"

set +e
rpmdev-vercmp "$candidate_version" "$baseline_version" >/tmp/pindb-rpm-version-compare.txt 2>&1
compare_status="$?"
set -e
if [[ "$compare_status" -ne 11 ]]; then
  echo "Candidate RPM must be newer than baseline: $baseline_version -> $candidate_version" >&2
  cat /tmp/pindb-rpm-version-compare.txt >&2 || true
  exit 1
fi

dnf install -y "$BASELINE"
installed="$(rpm -q --qf '%{VERSION}-%{RELEASE}' pindb)"
[[ "$installed" == "$baseline_version" ]] || {
  echo "Baseline RPM install mismatch: expected $baseline_version, got $installed" >&2
  exit 1
}

dnf upgrade -y "$CANDIDATE"
installed="$(rpm -q --qf '%{VERSION}-%{RELEASE}' pindb)"
[[ "$installed" == "$candidate_version" ]] || {
  echo "RPM upgrade mismatch: expected $candidate_version, got $installed" >&2
  exit 1
}

test -x /opt/pindb/bin/PinDB
test -x /opt/pindb/bin/pindb-update-helper

dnf remove -y pindb
if rpm -q pindb >/dev/null 2>&1; then
  echo "RPM still appears installed after removal." >&2
  exit 1
fi
if [[ -e /opt/pindb ]]; then
  echo "/opt/pindb remains after RPM package removal." >&2
  find /opt/pindb -maxdepth 3 -print >&2 || true
  exit 1
fi
