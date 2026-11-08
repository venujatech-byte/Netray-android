
## ⚠️ Keystore Warning

**Never lose your signing keystore.** Every release **must be signed with the SAME key**.  
Losing it means existing users **cannot update** without uninstalling the app.  
Always keep a secure offline backup of the keystore file and all passwords.

# Android releases

Android releases use `android-vMAJOR.MINOR.PATCH` because the Linux/Windows workflows already publish `v1.0.x` tags/releases in the same GitHub repository. Android updater selection is based on Android APK assets and recognizes the `android-` prefix. The first Android release with no Android tag is `1.0.0`.

## Versioning

Run `bash scripts/compute-version.sh` from the repository to print the next version, version code, Android tag, and bump type. A normal push increments PATCH. Put `#minor` or `#major` in any commit since the last Android tag to raise the corresponding component. Pull-request builds use `bash scripts/compute-version.sh --pr <number>` and append `-pr.<number>`; they are not published.

For ABI-specific APKs, versionCode is `(MAJOR * 1,000,000 + MINOR * 1,000 + PATCH) * 10 + abiOffset`, with universal `0`, arm64-v8a `1`, and armeabi-v7a `2`. The current local build supports ARM ABIs only; the AAB supplies ABI-specific Play delivery.

## Signing setup

Generate a dedicated upload keystore and keep a secure offline backup:

```bash
keytool -genkeypair -v -keystore netray-android-release.jks -alias netray-android \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w 0 netray-android-release.jks
```

Add the base64 output and the password values as repository Actions secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The workflow decodes the keystore to a temporary runner file, verifies signed APK certificates, then deletes the file. If any secret is absent, CI still builds and tests unsigned release artifacts but deliberately skips tag/release publication. Never commit the keystore or passwords. Back up the keystore: losing it prevents existing installations signed by that key from accepting updates.


## Rollback

Do **not** move an existing release tag. Publish a higher patch release with the fix
and explain the issue in its release notes. If a release must be hidden immediately,
mark that GitHub Release as a **draft** or **prerelease**; existing installations will
continue to run their installed version. Preserve the signing key and previous release
assets for recovery. Never delete the tag or the signing key.

The signed AAB and R8 `mapping.txt` are retained as private Actions artifacts for 90 days. Native debug symbols are disabled for release APKs to reduce artifact size. The app's explicit R8 keep rule preserves native JNI entry point names. The libXray AAR contributes its upstream consumer keep rules for the reflective Go bridge; do not remove them without testing the pinned integration.
