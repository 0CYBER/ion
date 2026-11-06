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
| Авто-переподключение, подключение при запуске | готово, тесты backoff и `ReconnectPolicy.decide` | `WheelMonitor.kt`, `ReconnectPolicy` |
| Уведомления (live / alerts / link) + чип в статус-баре Android 16 | готово, NotificationTest | `IonNotifications.kt`, `WheelErrors.kt` |
| Оповещения с гистерезисом + голос (TTS), вкл. «цель зарядки» | готово | `AlertEngine.kt`, `VoiceAnnouncer.kt`, `BatteryStepper` |
| Статистика и история поездок, CSV, checkpoint при смерти процесса | готово | `TripRecorder.kt`, `TripHistory.kt`, `TripsScreen.kt` |
| Виджет (Glance): последние известные цифры, полоска заряда, превью | готово, 4 Glance-теста размеров и состояний | `IonWidget.kt`, `WidgetMapping.kt`, `WidgetSnapshot` |
| Запрос снятия оптимизации батареи (Doze) | готово | `MainActivity.kt`, `BackgroundScreen` |
| Локализация RU/EN + выбор языка приложения (localeConfig) | готово, включая подписи диагностики и ошибок в `:core` (`tr()` в `CoreText.kt`) | `res/values-en/`, `res/xml/locales_config.xml` |
| Доступность: fontScale 1.5, переносы, FlowRow | готово, скриншоты | `Theme.kt`, `ScreenshotTest.kt` |
| Quick Settings tile | готово | `IonTileService.kt` |
| Модульный экран поездки + редактор | готово | `Dashboard.kt`, `RideScreen.kt`, `FeatureScreens.kt` |
| Ландшафт: боковая навигация + скорость закреплена слева | готово, скриншот `ride_landscape` | `IonApp.kt` (`IonRail`), `RideScreen.kt` |
| Плитка «График» (скорость/мощность за 6 мин) | готово | `SampleBuffer.kt`, `ChartTile.kt` |
| Скриншот-тесты (Robolectric + Roborazzi) | 15 снимков (вкл. ландшафт, RU/EN, светлая/тёмная, крупный шрифт) | `ScreenshotTest.kt`, `docs/screenshots/` |
| CI | GitHub Actions | `.github/workflows/android.yml` |

## Сборка

```
./gradlew :core:test :app:testDebugUnitTest assembleDebug
```
На машине с 4 ГБ RAM нужен `~/.gradle/gradle.properties` с `org.gradle.jvmargs=-Xmx1800m` и `kotlin.compiler.execution.strategy=in-process`. Скриншоты появляются в `app/build/outputs/roborazzi/`. 94 теста. `lintDebug` запускать **отдельно** от Robolectric-тестов (иначе OOM на 4 ГБ); при длинной сессии Gradle-демон растёт до 2,2 ГБ — перед тестами помогает `./gradlew --stop`, в `~/.gradle/gradle.properties`: `-Xmx1600m -XX:+UseSerialGC`, `org.gradle.workers.max=1`. Lint: 0 ошибок.

## Риски / что проверить на телефоне

1. Android 14+: FGS `connectedDevice` требует `BLUETOOTH_CONNECT` до старта сервиса — сервис стартует только после подключения.
2. Фара в уведомлении/виджете пишет только при `safeWrite` (стоянка, проверенное HT).
3. Авто-переподключение не срабатывает после ручного «Отключить» и «Забыть».
4. Wake lock держится только пока приходят данные (продление на 2 мин) — проверить расход батареи телефона.
5. TTS: если на телефоне нет голоса нужного языка, используется системный.
6. Баннер «Снять ограничение батареи» — показывается, если включена фоновая связь/переподключение, а приложение под Doze-оптимизацией; проверить диалог `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` на MIUI/One UI.

## Бэклог

второй вариант виджета, планшет (двухпанельные настройки), вибро-оповещения, GPX/трек, вынос логики `WheelMonitor` в `:core`.
