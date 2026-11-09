# Ion 0.9.3-ai — INMOTION V12 HT companion

Native Kotlin / Jetpack Compose / Material 3 Expressive personal app for one INMOTION V12 HT. Not an official INMOTION app.

- Package / applicationId: `com.freecyber.ion` (since 0.4.0; earlier builds were `dev.vela.v12` and install side by side).
- Tabs: Ride, Settings, Wheel. Settings opens focused categories; diagnostics (fields, raw and export) lives under Wheel. No demo mode.
- Background: foreground service (`connectedDevice|location`) keeps the link, auto-reconnects, records trips with GPS tracks; live notification, home-screen widget and Quick Settings tile. Internet is used only for trip map tiles.
- Customisable ride dashboard (repeatable tiles, universal toggle), alerts with voice, RU/EN.

## What it does
- BLE (Nordic UART) link to a verified V12 HT (series 7 / variant 2); the wheel is remembered after the first valid telemetry.
- Ride: speed, battery, voltage, trip distance (telemetry u16[22], distance since power-on), odometer (statistics), remaining range, current, MOS/motor temperature.
- Data: decoded fields and full raw payloads for nine read requests, plus JSON export of raw frames for before/after diffs.
- Controls: lights, auto-light, brightness, mute, sound wave, volume, play sound, ride modes (Off-road / Advanced), split mode and split accel/brake, pedal sensitivity and tilt, alarm thresholds, handle button, transport mode, wheel lock, standby timer.

## Write safeguards
- Two tiers: SAFE (lights/sound) needs a live verified link; STATIONARY (modes, pedals, service) also needs fresh telemetry and a still wheel. Rechecked right before sending.
- Switches write immediately (no confirmation dialog). Sliders sit inside expandable rows and are sent with Apply.
- Success is decided only by a fresh read-back; failures are shown, there is no automatic retry.
- Lock uses the observed V12 HT security exchange before the lock frame (see `docs/rolling-handoff.md`).
- Pedal tilt is in 0.01° on the wire (1° = 100) for both read and write.

This is not a certified safety interlock. Stop the wheel before changing ride settings.

## Build
JDK 21, Android SDK 37, AGP 9.3.1, Gradle 9.7.1. Min API 26, target API 35.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk` (debug-signed personal build).

## Signed release

Place the existing Ion PKCS#12 keystore on the build machine and create an ignored
`keystore.properties` in the repository root. Set `storeFile` to the keystore path
(use forward slashes on Windows) and `storePassword` to its password. The key alias
is `ion-release`, and the key and store use the same password.

```powershell
.\gradlew.bat --no-configuration-cache :app:assembleRelease
```

The signed APK is `app/build/outputs/apk/release/app-release.apk`. GitHub Actions
uses the same keystore through `ION_RELEASE_KEYSTORE_BASE64` and
`ION_RELEASE_STORE_PASSWORD`. Pull requests build an unsigned APK without signing
credentials. Keep a secure backup of the keystore and password for future updates.

## Docs
- `docs/design-system.md` — Material 3 Expressive design code: visual direction, tokens, components, motion, states and implementation rules. Redesign based on commit `3b82cd3` (0.4.0).
- `handoff.md` — current implementation: status, verified lock protocol, modules, build, CI.
- `docs/rolling-handoff.md` — full running work log: lock RE (hypotheses, refutations, evidence) and app evolution.

Protocol derived from WheelLog (GPL-3.0); see `THIRD_PARTY.md` and `third-party/WheelLog-LICENSE.txt` before redistribution.


## Release APK startup check

Before handing out an optimized APK, test the packaged release on a fully booted isolated emulator. Debug/JVM tests and a successful build do not exercise R8 startup failures.

```powershell
.\tools\Test-ApkStartup.ps1 -Apk app/build/outputs/apk/release/app-release.apk -Serial emulator-5580
```

This installs without clearing app data, performs two cold launches, and checks the process, resumed activity and crash log after each launch. It captures no screenshots. Version 0.9.1-ai/v19 failed this check; use the fixed 0.9.2-ai/v20 or later.
