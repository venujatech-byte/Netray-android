# Android architecture

## Runtime shape

```text
Compose UI (Activity / screens)
          │ user intents and observed immutable state
          ▼
ViewModels ── ProfileRepository ── app-private profile storage
          │ commands / StateFlow
          ▼
NetrayVpnService (foreground service; sole tunnel owner)
          ├── Android VpnService.Builder → TUN ParcelFileDescriptor
          ├── Rust JNI backend → profile validation and Xray TUN config
          ├── libXray adapter → pinned Xray-core Go runtime
          ├── ConnectionSupervisor → readiness, process health, bounded reconnect
          └── TunnelStats / redacted log events → local state and usage history
```

Start with one Gradle app module and package boundaries. Extract a `core` module only if native Xray integration, build time, or independent tests justify it.

## Responsibilities

### UI and state

- Compose screens render state and send user actions; they do not own sockets, TUN descriptors, or Xray processes.
- A ViewModel exposes immutable `StateFlow<UiState>` and maps service/repository state into screen state.
- Recreating or dismissing an Activity must reconnect to existing service state rather than starting another tunnel.
- Use explicit states: `Disconnected`, `AwaitingVpnConsent`, `Starting`, `Connecting`, `Connected`, `Reconnecting`, `Stopping`, and `Error`. Include an actionable message and retryability for errors.
- `Connected` means the Xray engine is running and a configured readiness check has passed. A button tap or foreground notification alone is not proof of connectivity.

### VPN service

- Declare one service protected by `android.permission.BIND_VPN_SERVICE` with the `android.net.VpnService` intent action.
- Call `VpnService.prepare()` in the UI flow and only start after the user grants consent.
- The service owns `VpnService.Builder`, the established TUN `ParcelFileDescriptor`, the engine handle, notification, and connection supervisor.
- Establish routes and DNS deliberately. Decide IPv4, IPv6, private-network bypass, MTU, and allowed/disallowed apps explicitly; document each leak or compatibility tradeoff.
- Call `protect()` on sockets that must reach the proxy endpoint outside the VPN to prevent a routing loop.
- Enter foreground state promptly when starting the VPN. Keep a persistent notification with connection state and a Disconnect action while connected.
- Close all owned resources on explicit disconnect, `onRevoke()`, startup failure, and service destruction. Closing must be idempotent.
- Support Always-on only after the service can safely recover after process death/reboot and expose accurate error notifications.

### Xray integration

- Define a narrow `XrayEngine` contract: validate/generate config, start with a TUN descriptor and callbacks, expose health/statistics, and stop/join.
- Keep Xray-specific JSON generation in the Rust JNI backend and native calls behind the Kotlin adapter. UI and ViewModels should not depend on Xray JSON.
- Run an early proof of concept using the official Xray Android integration route. Current Xray TUN documentation says Android receives the TUN file descriptor from the host app through `XRAY_TUN_FD`; determine the supported libXray API and ownership/duplication requirements before implementation.
- Do not launch arbitrary shell commands or assume a desktop Xray executable works on Android. Decide between supported in-process bindings and an Android-compatible child process only after measuring ABI support, descriptor passing, lifecycle, crash handling, and license obligations.
- Validate profiles before start. Generate temporary configs in private cache, avoid logging them, and remove them when the engine stops.
- Keep engine stdout/stderr consumption bounded and non-blocking; map errors to redacted app events.

### Profiles and persistence

- Support manual VLESS URI import and clipboard/paste first; add QR scanning after the tunnel path works.
- Parse into a typed internal profile. Preserve fields needed to round-trip valid imports; reject unsupported transports with a clear message instead of silently changing semantics.
- Store user profiles in app-private storage. DataStore is suitable for small non-secret preferences; choose a profile store and a Keystore-backed protection plan for secrets before implementing persistence.
- Use atomic writes or a transactional store. A failed write must not replace the last valid profile set.
- Never commit or sync profile data as sample config. Tests use fake UUIDs and reserved domains.

### Connectivity, health, and usage

- Use system network callbacks for network changes. Use bounded exponential backoff with cancellation for reconnect attempts.
- A short HTTP readiness probe through the tunnel may determine initial connection success and provide latency. A periodic probe may inform UX but must not be the sole process/tunnel supervisor.
- Prevent duplicate reconnect loops. Reset backoff after the engine is healthy; stop reconnecting on explicit user disconnect or VPN revoke.
- Record only tunnel bytes if the platform/core exposes reliable counters. Define upload/download direction and whether failed/direct routes count. Keep totals local and make reset/retention behavior explicit.
- Do not promise per-app or per-domain visibility unless Android/core APIs provide accurate, privacy-reviewed attribution. Logs must remain useful without collecting browsing history.

## Data flow: connect

1. User selects a profile and taps Connect.
2. UI validates that a profile exists and requests VPN consent with `VpnService.prepare()`.
3. After consent, UI starts the foreground VPN service with a profile identifier, not raw credentials in an implicit broadcast.
4. Service loads the profile, validates/builds Xray config, establishes the TUN interface, starts Xray with the owned descriptor, and begins monitoring.
5. Service reports `Connected` only after startup and readiness checks complete. UI and notification render the same service state.
6. On error, service closes partial resources, reports a redacted error, and either stops or enters bounded reconnect according to user settings.

## Data flow: disconnect

1. Explicit user action or system `onRevoke()` transitions state to `Stopping`.
2. Cancel probes/reconnect jobs, stop Xray, close the TUN descriptor, restore owned resources, and remove the foreground notification.
3. Report `Disconnected` only after cleanup completes. Repeated stop events remain safe.

## Package layout proposal

Keep one app module initially; organize by responsibility, not by tiny generic layers:

```text
app/src/main/java/.../
  app/                 Application, navigation, dependency wiring
  profiles/            Profile model, parser, repository, screens
  connection/          UiState, ViewModel, connect/disconnect intents
  vpn/                 VpnService, notifications, Android routing
  xray/                XrayEngine adapter and config conversion
  settings/             Preferences and settings screen
  diagnostics/          Redacted logs, health and traffic stats
native/src/lib.rs       Rust JNI profile validation and TUN config generation
```

Only split Gradle modules if there is a clear build or ownership benefit.
