# AI handoff — ветка `ai-experimental` (структурированно)

Актуальное состояние; хронология — `docs/ai-rolling-handoff.md`. Версия `0.7.0-ai` (versionCode 16). Автор коммитов — `0CYBER <heyartemka@gmail.com>` (локальный `git config` репозитория).

## Модули

| Модуль | Что внутри |
|---|---|
| `:core` (чистый Kotlin/JVM) | протокол V12, защита записи (`LockSecurity`), форматтеры, `AlertEngine`, конфиги, раскладка дашборда, `TripRecorder`/`TripSummary`/`TripTotals`, `BatteryStepper`, `ReconnectPolicy`, `WheelErrors`, `WidgetSnapshot`, `SampleBuffer` + все тесты логики |
| `:app` | Android: BLE, сервис, уведомления, виджет, QS-плитка, TTS, Compose UI, скриншот-тесты |

## Состояние функций

| Область | Статус | Где |
|---|---|---|
| Фоновая связь (FGS connectedDevice) + wake lock | готово, не проверено на устройстве | `WheelService.kt`, `IonApplication.kt` |
| Авто-переподключение, подключение при запуске | готово, тест backoff | `WheelMonitor.kt`, `ReconnectPolicy` |
| Уведомления (live / alerts / link) | готово | `IonNotifications.kt`, `WheelErrors.kt` |
| Оповещения с гистерезисом + голос (TTS) | готово | `AlertEngine.kt`, `VoiceAnnouncer.kt`, `BatteryStepper` |
| Статистика и история поездок, CSV, checkpoint при смерти процесса | готово | `TripRecorder.kt`, `TripHistory.kt`, `TripsScreen.kt` |
| Виджет (Glance) | готово | `IonWidget.kt`, `WidgetMapping.kt` |
| Quick Settings tile | готово | `IonTileService.kt` |
| Модульный экран поездки + редактор | готово | `Dashboard.kt`, `RideScreen.kt`, `FeatureScreens.kt` |
| Плитка «График» (скорость/мощность за 6 мин) | готово | `SampleBuffer.kt`, `ChartTile.kt` |
| Скриншот-тесты (Robolectric + Roborazzi) | 10 экранов | `ScreenshotTest.kt`, `docs/screenshots/` |
| CI | GitHub Actions | `.github/workflows/android.yml` |

## Сборка

```
./gradlew :core:test :app:testDebugUnitTest assembleDebug
```
На машине с 4 ГБ RAM нужен `~/.gradle/gradle.properties` с `org.gradle.jvmargs=-Xmx1800m` и `kotlin.compiler.execution.strategy=in-process`. Скриншоты появляются в `app/build/outputs/roborazzi/`.

## Риски / что проверить на телефоне

1. Android 14+: FGS `connectedDevice` требует `BLUETOOTH_CONNECT` до старта сервиса — сервис стартует только после подключения.
2. Фара в уведомлении/виджете пишет только при `safeWrite` (стоянка, проверенное HT).
3. Авто-переподключение не срабатывает после ручного «Отключить» и «Забыть».
4. Wake lock держится только пока приходят данные (продление на 2 мин) — проверить расход батареи телефона.
5. TTS: если на телефоне нет русского голоса, используется системный.

## Бэклог

EN-локализация (и вынос захардкоженных строк из BLE-слоя), второй вариант виджета, ландшафт/планшет, вибро-оповещения, устаревание данных в виджете, GPX/трек, тесты на fontScale 1.5, вынос логики `WheelMonitor` в `:core`.
