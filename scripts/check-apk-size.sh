#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
apk="${1:-$root/app/build/outputs/apk/release/app-release-unsigned.apk}"
baseline_file="$root/scripts/apk-size-baseline.txt"
python3 - "$apk" "$baseline_file" <<'PY'
import os
import sys

apk, baseline_file = sys.argv[1:]
baseline = int(open(baseline_file, encoding="utf-8").read().strip())
actual = os.path.getsize(apk)
limit = int(baseline * 1.02)
print(f"Release APK size: {actual:,} bytes; baseline: {baseline:,}; +2% limit: {limit:,}")
if actual > limit:
    raise SystemExit("Release APK exceeded the committed size baseline by more than 2%")
PY
