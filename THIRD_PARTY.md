# Research references and provenance

## WheelLog
Pinned source: https://github.com/Wheellog/Wheellog.Android/tree/f44e39e5388e500a5d2f315d1dcf32f167607bf8
- `InmotionAdapterV2.java`: protocol behavior, V12 model routing, field layouts, read requests and the seven allowlisted accessory control builders.
- `Constants.kt` and `BluetoothService.kt`: UUID roles and write mode.
- `InmotionAdapterV2Test.kt`: 25 published V12/V12 Pro fixture packets used by the tests.

WheelLog is GPL-3.0. Its license is included in `third-party/WheelLog-LICENSE.txt`. Reference fixtures are attributed and kept as research test data. No claim of a legal clean-room implementation is made. Review licensing obligations before publishing or incorporating this prototype into a distributed app; no final license decision is made for newly authored project code here.

Recorded fixtures selected here are HS/Pro, NOT user V12 HT recordings. The HT discriminator test is explicitly synthetic. Hardware compatibility remains unverified.

## INMOTION APK static observations
INMOTION 8.1.34 XAPK provided by the user; UUID/string constants corroborated in `libapp.so`. No vendor binaries, original APK, decompiled vendor source or vendor graphics are bundled into this project/APK. Original research evidence stays outside this project.

## Visual reference
Orion Store: https://github.com/RookieEnough/Orion-Store
Visual inspiration only (expressive Material typography, tonal colors and rounded containers). No source, screenshots, trademarks or assets copied into the app. Vela UI is newly authored.

## Android / Compose
AndroidX, Material 3, Kotlin and kotlinx.coroutines retain their upstream licenses. The build resolves dependencies from Google's Maven repository and Maven Central.
