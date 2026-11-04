# Security, privacy, and licensing requirements

## Threat model

The app handles V2Ray profile credentials and can route device-wide traffic. Treat profiles, generated Xray configuration, subscription URLs, and diagnostic output as secrets. The threat model includes accidental log/backup/repository exposure, malformed imports, lifecycle races, local listener exposure, and traffic escaping the VPN after failure.

## Required safeguards

- Keep profiles in app-private storage; never include real profiles in assets, tests, CI, screenshots, issue reports, or git history.
- Decide and document encryption at rest. Keep encryption keys in Android Keystore; do not hard-code keys or store a key beside encrypted profile data.
- Exclude secrets from Android Auto Backup unless a reviewed encrypted backup design exists. Document restore behavior.
- Redact credentials and sensitive URI parameters before logging. Prefer profile IDs and error categories over raw configs or full URLs.
- Do not collect browsing destinations or app names by default. Keep diagnostics local and offer an explicit export path only after redaction.
- Bind any local SOCKS/HTTP control port to loopback. Never expose unauthenticated listeners to Wi-Fi or cellular interfaces.
- Validate imported URI/JSON fields, scheme, host, and port. Bound input size and avoid logging parse failures with their raw input.
- Use Android `VpnService` consent; declare the protected service correctly; enter foreground state promptly while connected.
- Define and test DNS, IPv6, direct-route, bypass, reconnect, and fail-closed behavior. Do not state that traffic is protected if routes are not installed or the core is unhealthy.
- Cancel asynchronous work on disconnect/revoke and close every descriptor/process/engine exactly once.
- Release signing keys and secrets stay in protected CI storage and outside the repository.

## Permissions and disclosures

Request only permissions needed for the implemented features. Explain VPN consent, persistent notification, optional notifications, and any per-app routing behavior in plain language. Re-check Android and store policy requirements at release time.

## Xray and third-party licenses

- Xray-core is distributed under MPL-2.0. Keep upstream license and notices with every binary distribution, record the exact version and source, and follow source availability/notice requirements for modified MPL files.
- Before choosing XTLS/libXray or another wrapper, inspect its current license, generated/native artifacts, Android ABI support, build instructions, and transitive dependencies.
- Maintain a third-party inventory with component, version, license, upstream URL, modifications, and included notice. Verify it against the final APK/AAB.
- Netray's license and Xray's license are separate. Do not remove, rewrite, or imply ownership of upstream notices.
