# AI rolling handoff — ветка `ai-experimental`

Хронологический журнал автономной ночной сессии. Каждая запись: что сделано, зачем, чем проверено. Структурированная сводка — `docs/ai-handoff.md`.

## Сессия 1 — подготовка окружения

- Клонирован репозиторий, создана ветка `ai-experimental` от `395db99` (md3 expressive redesign).
- Установлены JDK 21 и Android SDK (platforms 36/37, build-tools 36.0.0). Первая сборка с `-Xmx3072m` убита OOM на 3.9 ГБ RAM — переопределено в `~/.gradle/gradle.properties` (`-Xmx1800m`, Kotlin in-process, 2 воркера). Проектный `gradle.properties` не трогали.
- Коммит `fe8db12`: `gradlew` сделан исполняемым (CI/Linux), `local.properties` в `.gitignore`.

## Сессия 1 — фон, уведомления, виджет, модульный экран поездки (коммит «feat: background link, alerts, widget, modular dashboard»)

Проблема исходника: BLE жил во ViewModel и рвался в `onStop` — приложение не работало при выключенном экране, не было уведомлений и виджетов, экран поездки был жёстко свёрстан.

Архитектура:
- `IonApplication` + `IonGraph` — процессный граф: `AppPrefs`, `WheelStore`, `BleController`, `TripRecorder`, `WheelMonitor`. BLE больше не принадлежит Activity/ViewModel.
- `WheelService` — foreground service типа `connectedDevice`: держит связь в фоне, живая карточка (скорость/заряд/температура, кнопки «Фара» и «Отключить»), обновление раз в секунду, авто-стоп через 4 с без связи, корректное поведение при смахивании задачи.
- `WheelMonitor` — координатор сессии: питает `TripRecorder`, гоняет `AlertEngine`, авто-переподключение с экспоненциальным backoff (`ReconnectPolicy`: 2 с → 60 с, 40 попыток), поднимает сервис, пушит виджет.
- `AlertEngine` — чистый, edge-triggered с гистерезисом: низкий/критический заряд, перегрев (худший из MOS/мотора), превышение скорости (cooldown 20 с), потеря/восстановление связи (только если пользователь не отключался сам), новые ошибки колеса.
- `IonNotifications` — 3 канала (live / alerts / link), `WheelErrors` — человекочитаемые RU-названия err_*.
- `IonWidget` (Glance) + `WidgetSnapshot` — виджет рабочего стола: состояние, заряд, скорость, запас хода, поездка, температура; кнопки подключения и фары; снапшот переживает рестарт процесса.
- `Dashboard.kt` — `DashTile` (18 модулей), `DashboardLayout` с совместимым вперёд кодированием, перестановкой, скрытием, размером (широкая/половина), упаковкой в ряды.
- `RideScreen` переписан: экран собирается из раскладки пользователя, каждая плитка — независимый модуль (`DashTileView` + `TileEnv`). Новые плитки: мощность, PWM (нагрузка мотора, с цветовыми порогами), наклон, дальний свет, звук, режим, сессия поездки (дистанция/макс/средняя/энергия/расход/время/заряд).
- `FeatureScreens.kt`: редактор экрана поездки (вверх/вниз, показать/скрыть, широкая/половина, сброс) и экран «Фон и уведомления» (держать связь, автопереподключение, живое уведомление, не гасить экран, разрешение на уведомления, пороги оповещений — слайдер коммитит на отпускании).
- `Protocol.Telemetry`: добавлены `pwm`, `batteryPower`, `motorPower`, `power`.
- `ScreenHeading` получил необязательную вторичную кнопку (редактировать экран).
- Настройки: секция «Приложение» со входами в оба новых экрана.
- Manifest: `IonApplication`, FGS-разрешения, `POST_NOTIFICATIONS`, сервис, receiver виджета, `singleTop`.

Проверка: `./gradlew testDebugUnitTest assembleDebug compileDebugAndroidTestKotlin` — BUILD SUCCESSFUL, 55 unit-тестов зелёные (новые: AlertEngineTest 8, DashboardLayoutTest 5, TripRecorderTest 3, WidgetSnapshotTest, ReconnectPolicyTest, WheelErrorsTest). На реальном колесе/телефоне не проверялось.

Версия: 0.7.0-ai (versionCode 16).
