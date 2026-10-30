# Vela 0.2.1 — INMOTION V12 HT companion

Native Kotlin / Jetpack Compose / Material 3 Expressive. Not an official INMOTION app.

## Changes from 0.1
- Removed demo mode and the About tab entirely from the shipping app.
- Redesigned four-tab interface: Ride, Data, Controls, Connection. Tonal lavender/lime styling, grouped controls, animated tab/status transitions and adaptive metric layouts. Numeric telemetry is not smoothed into invented intermediate readings.
- The last successfully identified V12 HT is stored locally after the first valid telemetry response. Reconnect with one button; forgetting requires confirmation. There is no silent auto-connect or background reconnection.
- Nine allowlisted read requests: model, live telemetry, settings, serial, module versions, main version, battery/BMS, diagnostic, statistics. Live telemetry is polled; settings refresh every five seconds. Other reads are manual, sequential, and have per-request timeouts.
- Data page exposes all decoded fields plus complete raw payloads with byte offsets. Unknown layouts/units remain explicitly raw. A timeout is NOT described as proof that a command is unsupported.
- Seven accessory control families: low/high beam, separate brightness values, auto-light, volume, mute, sound-wave and standby timer. Sliders only stage a value; Apply plus confirmation sends it. No writes during composition, dragging, reconnect or restart.

## Write safeguards
Every write requires verified HT identity, a connected session, three stationary telemetry packets, speed below 0.1 km/h, a packet no older than 1.5 seconds, no charging/firmware-update indication, and no pending operation. The conditions are rechecked immediately before sending. Settings controls additionally require a recent settings snapshot.

A GATT write callback does not mean a setting was applied. Vela issues a fresh read-back and compares the actual reported value. Missing/mismatched replies are shown as unconfirmed. There is no automatic write retry and pending writes are discarded on disconnect/background/error.

This is NOT a certified safety interlock. Wheel-reported speed/status can be wrong. Always stop and verify the wheel before applying a setting. Volume/mute may affect audible warnings: do not disable warnings for riding.

## Limits and honesty
The user reported that 0.1 generally worked. No physical phone/wheel has been connected by the agent while developing 0.2. The expanded commands, units and exact firmware compatibility still need stationary verification on the user's wheel. The catalogue covers the nine known implemented reads, NOT every possible vendor/firmware command. Battery percentage and several service fields are firmware-dependent. Serial/version/BMS/statistics layouts are shown raw rather than guessed.

No internet permission/account/analytics. Preferences store only wheel identity and theme; live telemetry/commands are not persisted. No firmware, calibration, speed-limit, pedal tuning, unlock, transport-mode or power-off writes. No widgets or background service yet.

## Build and install
JDK 21, Android SDK 37, AGP 9.3.1, Gradle 9.7.1. Min Android 8 / API 26, target API 35. Material3 1.5.0-beta01 is a pre-release dependency.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Install `app/build/outputs/apk/debug/app-debug.apk` OVER 0.1 without uninstalling (same package and local debug signing key, versionCode 3). This is a debug-signed personal prototype, not a production release.

JVM tests cover reference framing/fragmentation, command bytes, validation, write gating, read-back confirmation, selector matching, raw completeness and saved-address parsing. UI instrumentation uses synthetic packets in the separately installed TEST APK only; it never calls BLE actions and is not included in the shipping APK. It checks navigation, disabled controls, confirmation, reconnect-without-scan, and preference persistence/forgetting. See the accompanying validation report for actual results.

See `THIRD_PARTY.md` and `third-party/WheelLog-LICENSE.txt` for protocol/reference provenance before redistribution.

## 0.2.1 connection status hotfix
Sample elapsedRealtime on every state emission instead of reusing the previous ticker timestamp. BLE connection status is separate from telemetry freshness; stale data still blocks writes. Regression tests reproduce packets arriving between ticks.
