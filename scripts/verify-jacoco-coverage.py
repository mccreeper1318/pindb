#!/usr/bin/env python3
import sys
import xml.etree.ElementTree as ET
from decimal import Decimal, InvalidOperation

if len(sys.argv) != 3:
    raise SystemExit("Usage: verify-jacoco-coverage.py <jacoco.xml> <minimum-ratio>")

report_path = sys.argv[1]
try:
    minimum = Decimal(sys.argv[2])
except InvalidOperation as exc:
    raise SystemExit(f"Invalid minimum ratio: {sys.argv[2]}") from exc

root = ET.parse(report_path).getroot()
counter = next((item for item in root.findall("counter") if item.get("type") == "LINE"), None)
if counter is None:
    raise SystemExit("JaCoCo report does not contain a report-level LINE counter.")

covered = int(counter.get("covered", "0"))
missed = int(counter.get("missed", "0"))
total = covered + missed
if total <= 0:
    raise SystemExit("JaCoCo report contains no executable lines.")

ratio = Decimal(covered) / Decimal(total)
print(f"JaCoCo line coverage: {ratio:.4f} ({covered}/{total}); minimum: {minimum:.4f}")
if ratio < minimum:
    raise SystemExit(
        f"Line coverage {ratio:.4f} is below the required minimum {minimum:.4f}."
    )
