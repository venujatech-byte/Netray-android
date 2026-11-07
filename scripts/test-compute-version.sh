#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
script="$root/scripts/compute-version.sh"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
cd "$tmp"
git init -q
git config user.name "Netray version test"
git config user.email "version-test@example.invalid"
commit() {
  git commit --allow-empty -q -m "$1"
}
version() { bash "$script" "$@" | python3 -c 'import sys; d=dict(line.strip().split("=",1) for line in sys.stdin); print(d["version"],d["version_code"])'; }

commit "initial commit"
[[ "$(version)" == "1.0.0 10000000" ]]
[[ "$(version --pr 12)" == "1.0.1-pr.12 10000010" ]]
git tag android-v1.0.0
commit "regular android change"
[[ "$(version)" == "1.0.1 10000010" ]]
commit "add feature #minor"
[[ "$(version)" == "1.1.0 10010000" ]]
git tag android-v1.1.0
commit "breaking update #major"
[[ "$(version)" == "2.0.0 20000000" ]]
[[ "$(version --pr 27)" == "1.1.1-pr.27 10010010" ]]
printf '%s\n' "Version script checks passed"
