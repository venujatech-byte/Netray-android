#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
command -v cargo-ndk >/dev/null || { echo "Install cargo-ndk first: cargo install cargo-ndk" >&2; exit 1; }
rustup target add aarch64-linux-android armv7-linux-androideabi
cd "$root/native"
cargo ndk -P 26 -t arm64-v8a -t armeabi-v7a -o "$root/app/src/main/jniLibs" build --release
