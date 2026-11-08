#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)" python3 - <<'PY'
from pathlib import Path
import re
import sys
import os

root = Path(os.environ["ROOT_DIR"])
java_root = root / "app/src/main/java"
allowed_theme = java_root / "com/venujatech/netray/ui/theme/Theme.kt"
raw_patterns = [
    re.compile(r"Color\s*\(\s*0x[0-9A-Fa-f]{6,8}\s*\)"),
    re.compile(r"Color\.(?:White|Black)\b"),
    re.compile(r"Color\.parseColor\s*\("),
    re.compile(r"#[0-9A-Fa-f]{6,8}\b"),
]
violations = []
for path in java_root.rglob("*.kt"):
    if path == allowed_theme:
        continue
    text = path.read_text(encoding="utf-8")
    for number, line in enumerate(text.splitlines(), 1):
        if any(pattern.search(line) for pattern in raw_patterns):
            violations.append(f"{path.relative_to(root)}:{number}: {line.strip()}")

if violations:
    print("Raw colors belong in ui/theme/Theme.kt only:", file=sys.stderr)
    print("\n".join(violations), file=sys.stderr)
    raise SystemExit(1)
print("Theme color guard passed: no raw Kotlin colors outside Theme.kt")
PY
