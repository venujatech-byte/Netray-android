#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
directory="${1:-$root/app/build/outputs/apk/release}"
require_signed="${2:-}"

find_apk() {
  local variant="$1"
  local -a matches
  mapfile -t matches < <(find "$directory" -maxdepth 1 -type f -name "app-$variant-release*.apk" -print)
  if [[ ${#matches[@]} -ne 1 ]]; then
    echo "Expected one APK for $variant in $directory; found ${#matches[@]}" >&2
    return 1
  fi
  printf '%s\n' "${matches[0]}"
}

arm64="$(find_apk arm64-v8a)"
armv7="$(find_apk armeabi-v7a)"
universal="$(find_apk universal)"

python3 - "$arm64" "$armv7" "$universal" <<'PY'
import sys
import zipfile

def abis(path):
    with zipfile.ZipFile(path) as apk:
        return {name.split("/", 2)[1] for name in apk.namelist()
                if name.startswith("lib/") and name.count("/") >= 2}

arm64, armv7, universal = map(abis, sys.argv[1:])
expected = {
    "arm64-v8a": {"arm64-v8a"},
    "armeabi-v7a": {"armeabi-v7a"},
    "universal": {"arm64-v8a", "armeabi-v7a"},
}
for name, actual in zip(expected, (arm64, armv7, universal)):
    if actual != expected[name]:
        raise SystemExit(f"{name} APK contains ABIs {sorted(actual)}, expected {sorted(expected[name])}")
PY

arm64_size="$(stat -c %s "$arm64")"
armv7_size="$(stat -c %s "$armv7")"
universal_size="$(stat -c %s "$universal")"
if (( arm64_size >= universal_size || armv7_size >= universal_size )); then
  echo "Per-ABI APKs must be smaller than the universal APK" >&2
  exit 1
fi

if [[ "$require_signed" == "--require-signed" ]]; then
  apksigner="${APKSIGNER:-${ANDROID_HOME:?ANDROID_HOME is required}/build-tools/36.0.0/apksigner}"
  for apk in "$arm64" "$armv7" "$universal"; do
    "$apksigner" verify "$apk"
  done
fi

printf 'Verified APK sizes: arm64-v8a=%s, armeabi-v7a=%s, universal=%s bytes\n' \
  "$arm64_size" "$armv7_size" "$universal_size"
