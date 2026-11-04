# Instructions for contributors and coding agents

## Before changing code

1. Read `README.md` and the relevant sections in `docs/`.
2. Keep the implementation Kotlin-first and small. Preserve the current package/layer layout; do not introduce modules or dependencies without a concrete need.
3. Inspect all callers and lifecycle owners before changing VPN, Xray, profile persistence, or connection state.
4. Never copy a real server profile, UUID, subscription URL, private key, or access token into source, tests, screenshots, logs, fixtures, or commit messages.

## Product and platform rules

- `VpnService` is the owner of the Android VPN interface and its `ParcelFileDescriptor`. Request consent with `VpnService.prepare()` before connecting.
- Keep the tunnel in a foreground service while connected. UI closure or Activity recreation must not implicitly stop it. Explicit Disconnect, system revoke, and service shutdown must close the Xray engine and TUN descriptor.
- Treat service state as the source of truth. The UI observes state; it must not claim `Connected` merely because startup was requested.
- Do not report connected until engine startup and the selected readiness check have succeeded. Do not use periodic ping as the only tunnel supervision mechanism.
- Do not expose or start an unauthenticated local SOCKS/HTTP listener on LAN interfaces. Bind local listeners to loopback and validate ports.
- Android's VPN consent and notification behavior are user-facing requirements, not obstacles to work around.
- Do not enable Always-on VPN support until restart, failure, notification, and consent behavior are intentionally supported and tested.

## Code shape

- Use Kotlin, Jetpack Compose, AndroidX lifecycle/ViewModel, immutable UI state, and `StateFlow` for observation.
- Put platform calls behind small interfaces where they allow deterministic tests: VPN controller, Xray engine, profile store, and connectivity monitor.
- Begin with a single app module and feature/package boundaries. Split modules only when build ownership or native integration warrants it.
- Use structured coroutines. Do not block the main thread, launch unmanaged scopes, poll rapidly, or swallow cancellation.
- Make connection transitions explicit and reject duplicate start/stop operations.
- Keep UI state rendering separate from tunnel side effects. All service commands must be idempotent.
- Keep comments for platform constraints and non-obvious security behavior; do not narrate obvious code.

## Data and logs

- User-added profiles stay in app-private storage and out of version control. Use synthetic profile fixtures only.
- Never log full profile JSON, UUIDs, subscription URLs, query parameters, headers, or private keys. Redact endpoint data where it can identify a user.
- Handle malformed, truncated, old, and empty profile data without crashing or overwriting recoverable data.
- Do not silently discard persistence errors. Show an actionable error and preserve the previous valid data.
- Keep usage data local unless a separately reviewed feature explicitly requires otherwise.

## Dependencies and licenses

- Prefer Android platform and already selected AndroidX libraries over new dependencies.
- Record each new runtime dependency and its license in `docs/SECURITY.md` or a dependency/license inventory before shipping it.
- Xray-core is MPL-2.0. Keep its license and notices with distributed binaries, identify the exact upstream version and source, and publish corresponding modifications/source as required. Do not imply that Netray's own license covers Xray.
- Verify the current Android integration and redistribution terms for XTLS/libXray before selecting or packaging it. The integration spike is a release blocker, not an assumed implementation detail.

## Verification

- For Kotlin changes, run the narrowest relevant Gradle compile/check and tests. For UI changes, verify loading, empty, error, connecting, connected, disconnecting, and disconnected states.
- VPN/service changes need automated lifecycle tests where possible and physical-device checks for VPN consent, traffic routing, DNS, IPv4/IPv6, revoke, process recreation, and network handoff.
- Do not claim all-device routing or low battery use based only on unit tests or an emulator.
- Keep CI free of real credentials. Make release builds reproducible and inspect the final APK/AAB contents and licenses.

## Git hygiene

- Use focused commits with plain messages describing the change.
- Never commit local profiles, generated secrets, signing keys, keystores, `local.properties`, or build outputs.
- Do not push or publish unless the project owner explicitly asks.
