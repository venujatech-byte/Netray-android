# Netray for Android

Android client for Netray: manage V2Ray profiles and route device traffic through Xray using Android's VPN API.

The Android app is Kotlin with a small Rust JNI backend and official XTLS/libXray for Xray-core. Start with [AGENTS.md](AGENTS.md), then read the [architecture](docs/ARCHITECTURE.md), [security requirements](docs/SECURITY.md), [decisions](docs/DECISIONS.md), and [roadmap](docs/ROADMAP.md).

## Product goals

- Import and manage VLESS profiles without exposing their credentials.
- Start, observe, and explicitly stop a reliable Android VPN connection.
- Route device traffic through Xray and show useful connection, latency, and error states.
- Keep the app usable while the VPN runs in a foreground service and after the UI is dismissed.
- Use a Rust native library for URI validation and Xray TUN config generation; retain Kotlin for Android lifecycle APIs.
- Match Netray's simple profile-first workflow while following Android platform conventions.

## Initial scope

The first release targets Android phones and provides full-device VPN routing through `VpnService`. Per-app allow/deny lists can follow after the core tunnel works. A desktop-style global system proxy is not an Android MVP mode: Android apps cannot set a reliable global proxy for other apps, while `VpnService` is the supported mechanism for routing their traffic.

Do not promise that all traffic is protected until DNS, IPv4, IPv6, reconnect, and failure behavior have been verified on physical devices. See the roadmap's tunnel validation gate.

## Prerequisites

- Android Studio and an installed Android SDK compatible with the Gradle/Android Gradle Plugin versions selected when the project skeleton is created.
- A JDK supported by that Android Gradle Plugin.
- Kotlin, AndroidX, Jetpack Compose, Gradle version catalogs, and Kotlin coroutines/Flow.
- An Android emulator for UI and lifecycle work, plus physical Android devices for VPN routing, background limits, Wi-Fi/mobile handoff, and battery behavior.
- A local Xray test endpoint and profile fixtures with synthetic credentials only.
- Go 1.27.3, Python 3, Rust, Android NDK 27.2, and `cargo-ndk` to build pinned native dependencies.

## Build

Run `bash ./gradlew assembleDebug`. Gradle builds the pinned libXray AAR and Rust JNI library when they are missing, then writes the APK to `app/build/outputs/apk/debug/`. The first build needs network access and the Android SDK/NDK. GitHub Actions builds and uploads the debug APK on each push and pull request.

The initial app imports VLESS links, stores profiles encrypted with Android Keystore backed storage, and starts device traffic through a foreground `VpnService` using Xray TUN. Other profile formats, per-app routing, update delivery, and release signing are not in this first build.

Keep toolchain versions pinned in the Gradle wrapper and version catalog. Check current Android publishing requirements before release instead of copying stale target SDK numbers into this plan.

## Documents

- [Contributor and agent instructions](AGENTS.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Roadmap](docs/ROADMAP.md)
- [Security and privacy](docs/SECURITY.md)
- [Architecture decisions](docs/DECISIONS.md)
