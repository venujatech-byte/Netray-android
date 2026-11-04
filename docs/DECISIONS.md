# Architecture decisions

Record decisions that change the app's security model, compatibility, runtime behavior, or distribution. Keep entries short and replace a decision only with a dated follow-up.

## ADR 0001 — Kotlin Android client

- **Status:** Accepted
- **Decision:** Implement the Android client in Kotlin.
- **Reason:** Kotlin is the requested implementation language and has direct Android platform/lifecycle support.
- **Consequence:** Platform VPN and service ownership remain in Android/Kotlin code; Xray is behind an adapter.

## ADR 0002 — Android VPN uses `VpnService`

- **Status:** Accepted
- **Decision:** Route device traffic through Android `VpnService` and a TUN descriptor. Do not model desktop global-proxy mode as Android's full-device mode.
- **Reason:** Android provides VPN consent, routes, DNS, and per-app allow/disallow lists through this API. An app cannot reliably set a global proxy for every other app.
- **Consequence:** The service must own descriptor lifecycle, foreground notification, revoke, and network routing. TUN-to-Xray integration is a Phase 0 proof of concept.

## ADR 0003 — Compose and a single app module to start

- **Status:** Accepted
- **Decision:** Use Jetpack Compose for UI and begin with one app module organized by package responsibility.
- **Reason:** The project is greenfield and should avoid module/dependency overhead before boundaries are proven.
- **Consequence:** Split modules only when native integration or build/test ownership makes the benefit clear.

## ADR 0004 — Xray integration remains behind an adapter

- **Status:** Proposed pending Phase 0
- **Decision:** UI and VPN lifecycle depend on an `XrayEngine` interface, not a specific binary launcher or Go wrapper.
- **Reason:** Android requires the host app to pass its TUN file descriptor to Xray; supported API, ABI, process model, and shutdown semantics must be verified.
- **Consequence:** Do not commit to shelling out to a desktop binary or packaging strategy until the device spike passes and license obligations are reviewed.

## ADR 0005 — Local-first profile storage

- **Status:** Accepted
- **Decision:** Store profiles locally in app-private storage; do not add accounts or cloud sync to MVP.
- **Reason:** Profiles contain credentials and sync adds a remote security boundary not requested for the first release.
- **Consequence:** Define Keystore-backed at-rest protection, backup exclusion, migrations, and export behavior before shipping profile persistence.
