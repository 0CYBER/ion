# AI handoff — ветка `main` (структурированно)

Актуальное состояние; хронология — `docs/ai-rolling-handoff.md`. Версия `0.9.2-ai` (versionCode 20). Автор коммитов — `0CYBER <heyartemka@gmail.com>` (локальный `git config` репозитория).

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
| Модульный экран поездки + редактор | экземпляры с id, нижний лист, каталог, 11 назначений переключателя | `Dashboard.kt`, `RideScreen.kt`, `DashboardEditor.kt` |
| Ландшафт: боковая навигация + скорость закреплена слева | готово, скриншот `ride_landscape` | `IonApp.kt` (`IonRail`), `RideScreen.kt` |
| Плитка «График» (скорость/мощность за 6 мин) | готово | `SampleBuffer.kt`, `ChartTile.kt` |
| Скриншот-тесты (Robolectric + Roborazzi) | архив; владелец просит не запускать и не просматривать снимки | `ScreenshotTest.kt`, `docs/screenshots/` |
| CI | GitHub Actions | `.github/workflows/android.yml` |

## Сборка

```
./gradlew :core:test :app:testDebugUnitTest -PskipScreenshots
./gradlew lintDebug assembleDebug assembleDebugAndroidTest
```
На машине с 4 ГБ RAM нужен `~/.gradle/gradle.properties` с `org.gradle.jvmargs=-Xmx1800m` и `kotlin.compiler.execution.strategy=in-process`. Скриншоты появляются в `app/build/outputs/roborazzi/`. 101 тест без ScreenshotTest (58 core + 43 app), также 16/16 инструментальных тестов на emulator-5580 с RU locale и отключёнными снимками. `lintDebug` запускать **отдельно** от Robolectric-тестов (иначе OOM на 4 ГБ); при длинной сессии Gradle-демон растёт до 2,2 ГБ — перед тестами помогает `./gradlew --stop`, в `~/.gradle/gradle.properties`: `-Xmx1600m -XX:+UseSerialGC`, `org.gradle.workers.max=1`. Lint: 0 ошибок.

## Риски / что проверить на телефоне

1. Android 14+: FGS `connectedDevice` требует `BLUETOOTH_CONNECT` до старта сервиса — сервис стартует только после подключения.
2. Фара в уведомлении/виджете пишет только при `safeWrite` (стоянка, проверенное HT).
3. Авто-переподключение не срабатывает после ручного «Отключить» и «Забыть».
4. Wake lock держится только пока приходят данные (продление на 2 мин) — проверить расход батареи телефона.
5. TTS: если на телефоне нет голоса нужного языка, используется системный.
6. Баннер «Снять ограничение батареи» — показывается, если включена фоновая связь/переподключение, а приложение под Doze-оптимизацией; проверить диалог `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` на MIUI/One UI.

## Бэклог

второй вариант виджета, планшет (двухпанельные настройки), вибро-оповещения, GPX-экспорт трека (сам трек уже пишется), вынос логики `WheelMonitor` в `:core`.


## UI и производительность — 2026-11-06

См. сессию 3 в ai-rolling-handoff.md и актуальный блок в ../handoff.md. Раскладка v2 с id, повторяемые переключатели, новые метрики, стек вложенных экранов, компактный док без общей подложки, Onest для цифр. Из UI убраны clock-only инвалидации; проверки свежести и SAFE/STATIONARY сохранены. Новый редактор — DashboardEditor.kt; FeatureScreens.kt хранит общие подписи/иконки каталога и read-only sample.

По инструкции владельца проверять без снимков: `-PskipScreenshots` для JVM, `-e screenshots false` для instrumentation. Старые картинки не отражают эту ревизию. Измерений FPS на реальном устройстве нет.

Код текущей ревизии: `704f5c5`. Инструментальные тесты: выставить app locale ru и запускать с `-e screenshots false`. Все 16 прошли.


Уточнение 0.9.1: капсула дока сохранена, прямоугольная полоса удалена переносом дока из Scaffold.bottomBar в overlay и scroll clearance. Скорость компактнее; Onest в полях диагностики; отдельный нативный charging wave. Для оценки плавности отдавать release APK (`assembleRelease`, R8/shrinkResources, локальная debug-подпись, не debuggable), а не debug. Высокая частота запрашивается через FrameRateCategory.High; реальных измерений 165 fps пока нет. См. актуальный блок ../handoff.md.


## Критическое исправление release 0.9.2

v19 optimized не запускается из-за удалённого R8 no-arg конструктора WorkDatabase_Impl. Исправлено точечными keepclassmembers (также InputMerger). v20 release реально установлен и дважды холодно запущен на emulator-5580, UI отрисован; без скриншотов. Перед передачей будущих APK обязателен tools/Test-ApkStartup.ps1, проверяющий pid/resumed Activity/logcat самого артефакта. Сборка, подпись, JVM-тесты и lint сами по себе недостаточны. APK: app/build/outputs/apk/release/Ion-0.9.2-ai-v20-optimized.apk. История и доказательство red/green — в конце ai-rolling-handoff.md.

2026-11-06: все 35 коммитов ai-experimental до b13af2a перенесены в main fast-forward по запросу владельца. Продолжать на main; экспериментальная ветка сохранена.
