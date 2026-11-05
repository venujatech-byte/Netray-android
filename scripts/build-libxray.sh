#!/usr/bin/env bash
set -euo pipefail

version=v26.10.31
core_commit=a4a3dff5b20c
root="$(cd "$(dirname "$0")/.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
git clone --quiet --depth 1 --branch "$version" https://github.com/XTLS/libXray.git "$tmp/libXray"
python3 "$tmp/libXray/build/main.py" android
mkdir -p "$root/app/libs"
install -m 0644 "$tmp/libXray/libXray.aar" "$root/app/libs/libXray.aar"
printf '%s\n' "$version" > "$root/app/libs/libXray.version"
mkdir -p "$root/app/src/main/assets/licenses"
install -m 0644 "$tmp/libXray/LICENSE" "$root/app/src/main/assets/licenses/libxray-MIT.txt"
curl -fsSL "https://raw.githubusercontent.com/XTLS/Xray-core/$core_commit/LICENSE" -o "$root/app/src/main/assets/licenses/xray-MPL-2.0.txt"
