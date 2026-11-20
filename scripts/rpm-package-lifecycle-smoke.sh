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

mkdir -p \
  /etc/xdg/menus \
  /usr/local/share/applications \
  /usr/local/share/desktop-directories \
  /usr/local/share/mime/packages \
  /usr/local/share/icons/hicolor

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

mapfile -t LAUNCHERS < <(find /opt/pindb -type f -path '*/bin/PinDB' -executable)
mapfile -t HELPERS < <(find /opt/pindb -type f -path '*/bin/pindb-update-helper' -executable)
if [[ "${#LAUNCHERS[@]}" -ne 1 || "${#HELPERS[@]}" -ne 1 ]]; then
  echo "Installed package is missing the expected executable launcher/helper." >&2
  printf 'Launchers: %s\n' "${LAUNCHERS[*]:-}" >&2
  printf 'Helpers: %s\n' "${HELPERS[*]:-}" >&2
  exit 1
fi

dnf remove -y pindb
if rpm -q pindb >/dev/null 2>&1; then
  echo "RPM still appears installed after removal." >&2
  exit 1
fi
if [[ -d /opt/pindb ]] && find /opt/pindb -mindepth 1 -print -quit | grep -q .; then
  echo "Package files remain under /opt/pindb after removal." >&2
  find /opt/pindb -maxdepth 4 -print >&2 || true
  exit 1
fi
