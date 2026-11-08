#!/usr/bin/env bash
set -euo pipefail
export GOTOOLCHAIN=go1.27.3
export PATH="$(go env GOPATH)/bin:$PATH"

version=v26.10.31
core_commit=a4a3dff5b20c2faae567b742c172c7b95e75c083
root="$(cd "$(dirname "$0")/.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
git clone --quiet --depth 1 --branch "$version" https://github.com/XTLS/libXray.git "$tmp/libXray"
git init --quiet "$tmp/Xray-core"
git -C "$tmp/Xray-core" fetch --quiet --depth=1 https://github.com/XTLS/Xray-core.git "$core_commit"
git -C "$tmp/Xray-core" checkout --quiet "$core_commit"
python3 "$root/scripts/patch-xray-tls.py" "$tmp/Xray-core"
cp "$root/scripts/test-xray-tls.go" "$tmp/Xray-core/transport/internet/tls/netray_insecure_test.go"
(cd "$tmp/Xray-core" && go test ./transport/internet/tls -run '^TestNetrayAllowInsecure$' -count=1)
# shortcut: Netray has no geosite/geoip routing; restore this check if geo rules are added.
python3 - "$tmp/libXray/build/app/build.py" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
source = path.read_text()
call = "        self.download_geo()\n"
if source.count(call) != 1:
    raise SystemExit("Pinned libXray build hook changed; review geodata handling")
path.write_text(source.replace(call, ""))
PY
python3 "$tmp/libXray/build/main.py" android local
mkdir -p "$root/app/libs"
install -m 0644 "$tmp/libXray/libXray.aar" "$root/app/libs/libXray.aar"
printf '%s\n' "$version-netray-tls1" > "$root/app/libs/libXray.version"
mkdir -p "$root/app/src/main/assets/licenses"
install -m 0644 "$tmp/libXray/LICENSE" "$root/app/src/main/assets/licenses/libxray-MIT.txt"
curl -fsSL "https://raw.githubusercontent.com/XTLS/Xray-core/$core_commit/LICENSE" -o "$root/app/src/main/assets/licenses/xray-MPL-2.0.txt"

tar --exclude=.git -czf "$root/app/src/main/assets/licenses/xray-source.tar.gz" -C "$tmp" Xray-core
