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

## Сессия 1 — CI (коммит «ci: GitHub Actions…»)

- `.github/workflows/android.yml`: JDK 21 + Android SDK 37, `testDebugUnitTest assembleDebug compileDebugAndroidTestKotlin`, артефакты: отчёты и debug-APK. Запускается на push в `main`/`ai-experimental` и на PR.

## Сессия 1 — Quick Settings, история поездок

- `IonTileService`: плитка в шторке. Тап — подключить сохранённое колесо / завершить сессию; без колеса или разрешений открывает приложение. Подзаголовок: «78% · 23 км/ч», «Переподключение…», «Нажмите, чтобы подключить».
- `TripHistory` + `TripSummary` + `TripTotals`: завершённые поездки сохраняются (до 200, новые сверху) в `ion_trips`. Поездка архивируется при ручном отключении, при новой сессии, по кнопке «Завершить» на карточке сессии; поездки < 50 м отбрасываются. `TripStats.lastAt` даёт длительность; монотонное время переводится в настенное при архивации.
- `TripsScreen`: итоги (км, число поездок, время в движении, максимум), карточки поездок (дата, длительность, дистанция, макс/средняя, Wh/км, расход заряда, макс. температура), удаление, очистка с подтверждением, экспорт CSV через системный «Поделиться».
- Вход: карточка «Сессия» на экране поездки (кнопки «История поездок» и «Завершить»), раздел «Приложение» в настройках.
- Тесты: +4 (TripSummaryTest). Всего 59, все зелёные; `assembleDebug` ок.

## Сессия 1 — lint, автоподключение, защита поездки, голос

- `lintDebug`: исправлена единственная ошибка (deprecated `startActivityAndCollapse(Intent)` на API<34 — подавлена точечно), `SharedPreferences.edit {}` (KTX), убран лишний `SDK_INT < 26`, удалены 6 неиспользуемых строк. Остались только предупреждения о версиях зависимостей/targetSdk и API-31-атрибутах (безвредны).
- «Подключаться при запуске» (`BackgroundConfig.connectOnLaunch`, по умолчанию вкл.): холодный старт Activity с сохранённым колесом и разрешениями → `userConnect`. Не срабатывает при повороте экрана и если связь уже идёт/переподключается.
- Разрешение на уведомления перепроверяется при каждом `onResume` (`LifecycleResumeEffect`) — баннер исчезает после выдачи в системных настройках.
- Защита поездки от смерти процесса: `WheelMonitor` раз в 30 с пишет checkpoint текущей поездки (`TripHistory.checkpoint`), `finishTrip` его стирает; при старте процесса `recoverPending()` архивирует осиротевший checkpoint.
- Голос (`VoiceAnnouncer`, TTS, ленивая инициализация, RU-голос если доступен, `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` — приглушает музыку как навигатор): переключатели «Голосовые оповещения» и «Озвучивать заряд» (каждые 10 %, `BatteryStepper` с защитой от дрожания на границе и перебазированием при зарядке). При выключении обоих TTS освобождается.
- Тесты: +2 (BatteryStepperTest).

## Сессия 1 — скриншот-тесты на JVM и правки вёрстки

- Добавлены Robolectric 4.17 + Roborazzi 1.77 (`ScreenshotTest`, `Samples.live()`): 9 экранов рендерятся без устройства в `app/build/outputs/roborazzi/*.png` при обычном `testDebugUnitTest` (SDK 35, 411×1400 dp, тема light/dark). Копии — в `docs/screenshots/`. Тесты используют обычный `Application`, чтобы не поднимать BLE-граф.
- По скриншотам исправлено: парные плитки теперь равной высоты (`TilePair` с `IntrinsicSize.Min`); подписи «Средняя»/«Потрачено» больше не обрезаются; без телеметрии экран поездки не показывает стену прочерков (остаётся только незавершённая сессия); добавлена ссылка «История поездок»; в редакторе размер переключается чипом под названием (3 кнопки вместо 4 — названия не рвутся посреди слова); слайдеры порогов получили фон группы.

## Сессия 1 — wake lock и модуль `:core`

- `WheelService` держит несчётный `PARTIAL_WAKE_LOCK` (продлевается на 2 мин при каждом обновлении, пока связь активна; снимается при остановке). Причина: колесо опрашивается запросами, а не стримит — если CPU засыпает между опросами в кармане, телеметрия и оповещения замирают. Добавлено разрешение `WAKE_LOCK`.
- Новый чисто-Kotlin модуль `:core` (JVM, без Android): `Protocol`, `LockSecurity`, `Formatters`, `AlertEngine`, `Config` (AlertConfig/BackgroundConfig), `Dashboard`, `TripRecorder`, `TripSummary`/`TripTotals`, `BatteryStepper`, `ReconnectPolicy`, `WheelErrors`, `WidgetSnapshot` (данные). Android-сторона (`WidgetMapping.kt`: `WidgetSnapshot.from(WheelState…)`) осталась в `:app`. Пакет прежний (`com.freecyber.ion`), импорты не менялись; `internal` → public для `frame`, `LockSecurity`, `fmt`.
- Тесты логики переехали в `core/src/test` (вместе с `v12-fixtures.txt`), `ControlsTest`/`ConnectionTimingTest`/`ScreenshotTest` — в `:app`. Команда: `./gradlew :core:test :app:testDebugUnitTest assembleDebug`. Всего 70 тестов, зелёные. CI обновлён.

## Сессия 1 — авторство коммитов

- Обнаружено: коммиты ветки шли от `mybydemvarit jesse <free.wplace4@gmail.com>` — это был глобальный `git config` машины (`gh auth` отвечает только за push). Для репозитория задано `user.name=0CYBER`, `user.email=heyartemka@gmail.com` (как в исходных коммитах), 8 коммитов ветки переписаны (`rebase --exec 'commit --amend --reset-author'`) и отправлены `--force-with-lease`. `main` не трогался. Новые хэши: c9c28e7 … 351c58f.

## Сессия 1 — плитка «График»

- `:core` `SampleBuffer`: кольцевой буфер последних 6 мин телеметрии (скорость, мощность), прореживание до 1 точки/с, сброс при `finishTrip`. Тест в `FeatureLogicTest`.
- `DashTile.CHART` (широкая, по умолчанию видна, добавляется в конец сохранённых раскладок автоматически). `ChartTile.kt`: Canvas — заливка-площадь скорости + линия мощности (цвет `tertiary`), легенда «Скорость до … км/ч», «Мощность до … Вт», окно «за N мин», contentDescription с итогами для TalkBack.
- Поток: `WheelMonitor` → `IonGraph.chart` (StateFlow) → `VelaViewModel.combine` → `VelaUi.chart`.
- Новый скриншот `ride_chart` (тёмная тема, график сразу под скоростью). 72 теста зелёные.

## Сессия 1 — виджет: последние известные данные и превью

- `WidgetSnapshot.carryOver(previous)` (`:core`, тест `offlineKeepsLastKnownFiguresAndTime`): без живой связи виджет больше не обнуляется до прочерков — хранит заряд/остаток/поездку/температуру и время, когда они были актуальны. Слияние делается по каждому экземпляру виджета внутри `updateAppWidgetState`. После перезапуска процесса первый пуш `WheelMonitor` превращает застывший «На связи» в «Не в сети · 14:32».
- Виджет: устаревшие цифры приглушены (`onSurfaceVariant`), в статусе время/дата (`DateUtils.formatSameDayTime`); при высоте ≥110 dp — полоска заряда (`LinearProgressIndicator`).
- `previewLayout` (`res/layout/widget_preview.xml`) — в списке виджетов Android 12+ виден настоящий облик вместо заглушки.
- lint: 0 ошибок; 73 теста зелёные.
