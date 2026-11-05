#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
command -v cargo-ndk >/dev/null || { echo "Install cargo-ndk first: cargo install cargo-ndk" >&2; exit 1; }
rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
cd "$root/native"
cargo ndk -P 26 -t arm64-v8a -t armeabi-v7a -t x86 -t x86_64 -o "$root/app/src/main/jniLibs" build --release
