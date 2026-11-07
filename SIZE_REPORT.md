# Android size and runtime report

## Baseline (before release-size changes)

Measured on 2026-11-07 from the unsigned `assembleRelease` APK and `bundleRelease` AAB. APK entry sizes come from the ZIP directory; AAB per-ABI figures are estimated as compressed base resources plus that ABI's compressed native entries (actual Play delivery may vary).

| Metric | Baseline |
|---|---:|
| Universal release APK | 166,698,247 bytes (166.7 MB / 158.9 MiB) |
| Release AAB | 68,534,185 bytes (68.5 MB / 65.4 MiB) |
| AAB arm64-v8a download estimate | 18,667,261 bytes (18.7 MB) |
| AAB armeabi-v7a download estimate | 18,912,205 bytes (18.9 MB) |
| AAB x86_64 download estimate | 20,055,643 bytes (20.1 MB) |
| AAB x86 download estimate | 20,657,019 bytes (20.7 MB) |
| Installed app directory on attached SM-T295 | 175,360 KiB (~171 MiB; existing install, universal APK) |
| Cold launch (`am start -W`) | 6,075 ms; 5,955 ms; 5,777 ms |
| Idle PSS | ~96,986 KiB |
| Idle CPU sample | 0% for Netray |
| Connected PSS/CPU and 10-minute battery | Not measured: no authorized test endpoint was available for a safe tunnel session |

## APK contents (largest size contributors)

Native libraries are packaged uncompressed in the APK. AAB native libraries are compressed in the bundle and split by ABI for store delivery.

| Entry / group | Uncompressed size | APK compressed size |
|---|---:|---:|
| `libgojni.so` x86_64 | 36,433,496 | 36,433,496 |
| `libgojni.so` arm64-v8a | 34,065,816 | 34,065,816 |
| `libgojni.so` x86 | 33,338,896 | 33,338,896 |
| `libgojni.so` armeabi-v7a | 33,331,820 | 33,331,820 |
| `xray-source.tar` MPL source archive | 6,727,680 | 1,288,374 |
| `libbarhopper_v3.so` x86 | 6,122,368 | 6,122,368 |
| `libbarhopper_v3.so` x86_64 | 5,909,280 | 5,909,280 |
| `libbarhopper_v3.so` arm64-v8a | 4,946,720 | 4,946,720 |
| `classes.dex` | 3,910,612 | 1,870,423 |
| `libbarhopper_v3.so` armeabi-v7a | 3,244,440 | 3,244,440 |

The four-ABI Go core is the main reason a universal APK is ~170 MB. The next ranked savings opportunities are shipping only ARM ABIs in the APK/AAB, dropping the bundled ML Kit scanner in favor of the already-used ZXing decoder for live frames, removing duplicate source artifacts only if MPL source obligations remain satisfied, and shrinking resources. R8 minification was already enabled; resource shrinking was not.

## Device/runtime notes

- Device: Samsung SM-T295, API 30, arm64-v8a.
- Cold start samples include the current build and device state; no profiler trace or connected tunnel session was available, so no CPU/battery claims are made beyond the idle `dumpsys cpuinfo` sample.
- The attached device's installed app directory was measured before updating it to the current debug build.

## After optimization

### Release-size batch 1 (two ARM ABIs, R8 resource shrink, ZXing-only QR decode)

| Metric | Baseline | After batch 1 | Delta |
|---|---:|---:|---:|
| Universal release APK | 166,698,247 bytes | 73,261,448 bytes | -93,436,799 bytes (-56.0%) |
| Release AAB | 68,534,185 bytes | 31,285,088 bytes | -37,249,097 bytes (-54.3%) |
| AAB arm64-v8a download estimate | 18,667,261 bytes | 15,601,558 bytes | -16.4% |
| AAB armeabi-v7a download estimate | 18,912,205 bytes | 16,164,475 bytes | -14.5% |
| Universal APK ABI payloads | 4 ABIs | arm64-v8a + armeabi-v7a | x86 and x86_64 removed |

The AAB estimates use compressed base-module entries plus compressed native entries for the ABI. They are estimates rather than a `bundletool get-size total` result because bundletool is not installed in this environment. The universal APK still contains both ARM ABIs for sideloading.

The bundled `libgojni.so` remains ~33–34 MB per ARM ABI uncompressed and is still the largest component. Stripping did not apply to it because the AAR's Go shared library is prebuilt; optimize only by changing/rebuilding the upstream integration and checking MPL obligations. Removing ML Kit and its `barhopper_v3.so` libraries cut another ~20 MB from the universal APK. The Xray source archive remains bundled for source availability. An unused Rust QR SVG JNI function and the Rust `qrcode` crate were also removed; QR image and live decoding now both use the already-needed ZXing core.

### Release-size batch 2 (remove dead Rust QR generator)

| Metric | Batch 1 | Batch 2 | Delta |
|---|---:|---:|---:|
| Universal release APK | 73,261,448 bytes | 73,146,696 bytes | -114,752 bytes (-0.16%) |
| Release AAB | 31,285,088 bytes | 31,231,327 bytes | -53,761 bytes (-0.17%) |
| AAB arm64-v8a download estimate | 15,601,558 bytes | 15,571,895 bytes | -0.19% |
| AAB armeabi-v7a download estimate | 16,164,475 bytes | 16,138,842 bytes | -0.16% |

Runtime before/after remains unmeasured for connected state; idle PSS/startup measurements after the split build are recorded when the debug APK is installed from the requested command.
