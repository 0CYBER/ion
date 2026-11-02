# Ion 0.4.0 — INMOTION V12 HT companion

Native Kotlin / Jetpack Compose / Material 3 Expressive personal app for one INMOTION V12 HT. Not an official INMOTION app.

- Package / applicationId: `com.freecyber.ion` (since 0.4.0; earlier builds were `dev.vela.v12` and install side by side).
- Tabs: Ride, Data, Controls, Connection. No demo mode, no background service, no internet permission.

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

## Docs
- `handoff.md` — project overview and decisions.
- `docs/rolling-handoff.md` — running work log (lock RE and later app changes).
- `docs/protocol-re-lock.md`, `docs/re-lock-handoff.md`, `docs/protocol-re-notes.md` — reverse-engineering notes.

Protocol derived from WheelLog (GPL-3.0); see `THIRD_PARTY.md` and `third-party/WheelLog-LICENSE.txt` before redistribution.
