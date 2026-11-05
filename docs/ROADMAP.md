# Roadmap

Each phase has an exit gate. The current project is an early prototype; device validation remains required before claiming reliable all-traffic routing.

## Prototype status

- Phase 1 has a Compose profile list, Gradle wrapper, Android VPN service scaffold, Rust JNI backend, and CI APK workflow.
- VLESS URI validation and minimal TUN config generation have Rust unit tests.
- Phase 2 has encrypted local profile storage and import; editing, QR, and migrations remain open.
- Phase 3 has an initial Xray TUN integration and readiness probe. It still needs physical-device validation for descriptor ownership, DNS, IPv4/IPv6, UDP, revoke, reconnect, and vendor background behavior.

## Phase 0 — Validate integration choices

- Choose the initial Android API range and device matrix based on actual support needs.
- Read current Android VPN, foreground service, background execution, notification, and Play distribution requirements.
- Build a small Kotlin spike that obtains a `VpnService` TUN descriptor and passes it to the supported Xray Android integration.
- Confirm supported ABIs, descriptor ownership, engine shutdown, crash reporting, configuration API, stats API, and Xray/MPL-2.0 distribution obligations.
- Capture memory/CPU baseline while idle and under a known traffic load.

**Exit:** one synthetic VLESS profile routes a test request through Xray on at least one physical device, and explicit stop/revoke closes the engine and descriptor without leaking traffic.

## Phase 1 — App foundation

- Add Gradle wrapper, version catalog, Kotlin Android app module, Compose, lifecycle, coroutines, and CI compile job.
- Add app theme, navigation, app icon placeholder, and profile-list/empty states.
- Add fake engine and fake profile repository for deterministic ViewModel development.
- Add profile parser tests with synthetic VLESS fixtures and malformed-input cases.

**Exit:** clean checkout builds in CI; UI handles empty, loading, and parse-error states.

## Phase 2 — Profiles

- Add create/import/edit/delete for supported VLESS URIs and JSON only if a real Netray compatibility requirement exists.
- Validate required fields, ports, UUIDs, TLS/security, transport, and unsupported options before saving or connecting.
- Persist profiles privately with safe migration and atomic/transactional writes.
- Add QR scan/import after paste/manual import has stable validation.

**Exit:** profiles survive restart, can be edited/deleted with confirmation, and malformed imports never crash or destroy saved profiles.

## Phase 3 — VPN and Xray tunnel

- Implement VPN consent and a foreground `VpnService`.
- Establish TUN routes/DNS and integrate the Xray adapter from Phase 0.
- Add explicit state machine, readiness check, stop/revoke cleanup, and one reconnect supervisor.
- Add persistent notification actions for disconnect/open app.
- Keep Android VPN service authoritative when the Activity is killed or recreated.

**Exit:** verify IPv4, IPv6, DNS, TCP, UDP/QUIC behavior, endpoint socket protection, network handoff, VPN revoke, process death, and user disconnect on physical devices. Document any unsupported traffic.

## Phase 4 — Useful daily experience

- Add per-profile ping/readiness results, connection uptime, detailed redacted logs, and tunnel-only usage totals.
- Add connection-drop notification preference and bounded auto-reconnect behavior.
- Add sort, profile editing lock while connected, delete confirmation, and settings.
- Add optional per-app routing only after the full-device VPN path is stable.

**Exit:** no duplicate connect/reconnect work; errors tell users what happened; logs do not expose credentials or browsing history.

## Phase 5 — Reliability and release

- Add unit, coroutine, parser, persistence migration, service lifecycle, and instrumentation tests.
- Run device checks across supported Android versions and at least one current Pixel plus one vendor-customized device.
- Profile idle/connected CPU, memory, battery, network handoff, and repeated connect/disconnect cycles.
- Add CI for debug builds and tests. Configure release signing outside the repository using protected CI secrets.
- Inspect APK/AAB contents, ABI coverage, notices, permissions, target SDK, privacy disclosures, and crash-free tunnel shutdown.
- Publish a small internal/beta release before general availability.

**Exit:** release checklist passes; reproducible artifacts and source/notices match the bundled Xray version.

## Not in the first release

- User accounts, cloud sync, remote telemetry, or a subscription service.
- A custom VPN protocol or an Xray fork before the official integration is evaluated.
- Global Android system-proxy mode as a substitute for `VpnService`.
- Per-app/domain activity history or traffic inspection UI.
