# Third-party notices

- **XTLS/libXray v26.10.31** — MIT — https://github.com/XTLS/libXray/tree/v26.10.31. Bundled as an Android AAR built from the pinned upstream release.
- **XTLS/Xray-core** — MPL-2.0 — https://github.com/XTLS/Xray-core/tree/a4a3dff5b20c. Included through libXray's Go module dependency; the build copies its license into the APK assets.
- Rust dependencies are recorded in `native/Cargo.toml`; their resolved versions are captured by Cargo when it generates `native/Cargo.lock`. Review their licenses before publishing.

The desktop and Android Netray code does not replace these upstream notices. Xray-core is a separate work under MPL-2.0; its source and license remain available at the upstream link above.

Netray modifies Xray-core to restore explicit `allowInsecure` support. The default remains certificate verification. The patch is in `scripts/patch-xray-tls.py`; the complete modified MPL-2.0 source is bundled in APK assets at `licenses/xray-source.tar.gz`. Rebuild with `scripts/build-libxray.sh`.
