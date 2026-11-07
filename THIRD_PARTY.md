# Research references and provenance

## WheelLog
Pinned source: https://github.com/Wheellog/Wheellog.Android/tree/f44e39e5388e500a5d2f315d1dcf32f167607bf8
- `InmotionAdapterV2.java`: protocol behavior, V12 model routing, field layouts, read requests and the seven allowlisted accessory control builders.
- `Constants.kt` and `BluetoothService.kt`: UUID roles and write mode.
- `InmotionAdapterV2Test.kt`: 25 published V12/V12 Pro fixture packets used by the tests.
- `LoggingService.kt` (reviewed 2026-11-07): reference for a per-ride telemetry CSV with timestamps and location, rather than a CSV of trip summaries. Ion writes its own explicitly unit-labelled schema; it does not claim WheelLog import compatibility.

WheelLog is GPL-3.0. Its license is included in `third-party/WheelLog-LICENSE.txt`. Reference fixtures are attributed and kept as research test data. No claim of a legal clean-room implementation is made. Review licensing obligations before publishing or incorporating this prototype into a distributed app; no final license decision is made for newly authored project code here.

Recorded fixtures selected here are HS/Pro, NOT user V12 HT recordings. The HT discriminator test is explicitly synthetic. Hardware compatibility remains unverified.

## INMOTION APK static observations
INMOTION 8.1.34 XAPK provided by the user; UUID/string constants corroborated in `libapp.so`. No vendor binaries, original APK, decompiled vendor source or vendor graphics are bundled into this project/APK. Original research evidence stays outside this project.

## Fonts

- Onest Variable: sourced from [Google Fonts](https://github.com/google/fonts/tree/main/ofl/onest), licensed under SIL Open Font License 1.1. License: `third-party/Onest-OFL.txt`.
- JetBrains Mono Variable: sourced from [Google Fonts](https://github.com/google/fonts/tree/main/ofl/jetbrainsmono), licensed under SIL Open Font License 1.1. License: `third-party/JetBrainsMono-OFL.txt`.

The font files are bundled locally. No font downloads occur in the application.

## Android / Compose
AndroidX, Material 3, Kotlin and kotlinx.coroutines retain their upstream licenses. The build resolves dependencies from Google's Maven repository and Maven Central.

## Trip maps

- osmdroid 6.1.20: https://github.com/osmdroid/osmdroid (Apache-2.0); native map rendering and HTTP-aware tile cache. The upstream repository is archived; the dependency is pinned.
- Map data © OpenStreetMap contributors, ODbL: https://www.openstreetmap.org/copyright. Standard tiles follow https://operations.osmfoundation.org/policies/tiles/: HTTPS, app-specific user agent, visible attribution, local caching, no prefetch/download feature.
