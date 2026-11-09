# Ion — handoff (финальная реализация)

Актуальное состояние проекта. Хронология работы, гипотезы и опровергнутые выводы — `docs/rolling-handoff.md`.

## Статус

- Текущая работа — ветка **ai-upgrader** (rebase на main `7d7dbde`, не запушена): непрерывные улучшения, журнал и план — «Часть III» в `docs/rolling-handoff.md`. Сделано: заголовки страниц `onBackground` в обеих темах (корневой `Scaffold` задаёт contentColor); AutoMirrored-иконки; README актуализирован; вибро-оповещения с разными ритмами; тема «Как в системе»; a11y-роли; `LocalContentColor` в `IonTheme`; подтверждение удаления поездки в списке.
- Ветка **main** (35 коммитов ai-experimental перенесены fast-forward до b13af2a; экспериментальная ветка сохранена). Remote: https://github.com/0CYBER/ion.
- Версия **0.9.3-ai / versionCode 21**. Релизный APK: `app/build/outputs/apk/release/app-release.apk`. Начиная с v21, локальные и CI-релизы используют один постоянный ключ Ion. Для перехода со старого APK, подписанного debug-ключом, потребуется новая установка.
- Приложение: персональный компаньон INMOTION V12 HT (Kotlin + Compose, Material 3 Expressive), `applicationId`/`namespace` = `com.freecyber.ion`. Вкладки: Поездка / Настройки / Колесо.
- Исследование: lock/unlock работает на V12 HT владельца, подтверждено аппаратно (обе операции + read-back match).

## Модули

| Модуль | Что внутри |
|---|---|
| `:core` (чистый Kotlin/JVM) | протокол V12, защита записи (`LockSecurity`), форматтеры, `AlertEngine`, конфиги, раскладка дашборда, `TripRecorder`/`Track`/`TripSummary`/`TripTotals`, `BatteryStepper`, `ReconnectPolicy`, `WheelErrors`, `WidgetSnapshot`, `SampleBuffer`, `CoreText` (`tr(ru,en)`) + все тесты логики |
| `:app` | Android: BLE, сервис, уведомления, виджет, QS-плитка, TTS, Compose UI, скриншот-тесты (архив) |

Ключевые файлы: `Protocol.kt` (framing/decoder, Tier+Control, allowlist с read-back), `BleController.kt` (GATT-машина, Timing, tier-aware `mayWrite`), `VelaViewModel.kt` (combine + hysteresis), `Dashboard.kt`/`DashboardEditor.kt` (v2-раскладка с id, повторяемые плитки), `Theme.kt` (единственный дом цветов), `Ui.kt` (`ExpandSliders`, squash-нажатия, группы), `IonApp.kt` (док-навигация, стек вложенных экранов, landscape rail).

## Проверенный протокол замка V12 HT

1. Подключение, неподвижность + свежая телеметрия, MTU ≥ 44 (наблюдалось 247).
2. Запрос `AA AA 13 04 02 00 00 02 17` → валидный ответ `12 13 02 80 02` с 16 байтами; те же байты уходят в запросе `13 14 02 00 00 82` (получаются от текущего колеса, не хардкодятся, между сессиями не хранятся).
3. Ожидать строго `AA AA 12 04 02 80 82 01`; иначе lock не отправлять.
4. Один кадр: lock `AA AA 16 05 02 21 60 31 01 60`, unlock `AA AA 16 05 02 21 60 31 00 61`. ACK `... E0 31 00 E1` не заменяет read-back.
5. Read-back: SETTINGS byte39, mask `0x20` (1 = locked).

Guard STATIONARY пере-проверяется после security-обмена; повторов write нет; fallback — офф. приложение. Реализация: `LockSecurity.kt` + `BleController`. Read-back бит `0x20` найден диффом; подтверждён контрольными экспортами. Проверено на одном V12 HT / прошивке / телефоне; универсальность для других моделей не доказана.

## Функции

| Область | Статус |
|---|---|
| Фоновая связь (FGS `connectedDevice`) + wake lock (продление 2 мин) | готово, не проверено на устройстве |
| Авто-переподключение (`ReconnectPolicy`, backoff 2 с→60 с, 40 попыток), подключение при запуске | готово |
| Уведомления (live/alerts/link) + чип статус-бара Android 16 (`setRequestPromotedOngoing`) | готово |
| Оповещения с гистерезисом + TTS, «цель зарядки» (перевзвод после −5 %) | готово |
| Поездки: статистика, история (≤200), CSV + **GPS-треки** (LocationManager, трек ≤2400 точек, GPX-экспорт, экран с маршрутом и скраббером) | готово; поле не проверено |
| Виджет (Glance): последние известные цифры, полоска заряда, превью | готово, 4 Glance-теста |
| Модульный экран + редактор: v2-раскладка с id, повторяемые плитки, `DashToggle` (11 переключателей), нижний лист | готово |
| Ландшафт: боковая навигация, спидометр закреплён слева при ≥560 dp | готово |
| Локализация RU/EN (`values-en/`, `CoreText.tr`, localeConfig), доступность (fontScale 1.5, переносы, FlowRow) | готово |
| QS-плитка, Doze-баннер (battery optimization) | готово |
| Скорость/плавность: Onest + tnum, drawWithCache, drawBehind, `FrameRateCategory.High`, нативный charging wave | готово; 165 fps не измерялось |
| CI (GitHub Actions) | собран и опубликован app-release |

## Сборка и проверки

```powershell
# JVM-тесты (101: core 58 + app 43) без снимков
.\gradlew.bat :core:test :app:testDebugUnitTest -PskipScreenshots
# lint — отдельно от Robolectric-тестов (иначе OOM на 4 ГБ)
.\gradlew.bat lintDebug assembleDebug assembleDebugAndroidTest
# release
.\gradlew.bat :app:lintRelease :app:assembleRelease
```

Окружение: JDK 21 (`C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`), SDK 37, AGP 9.3.1, Gradle 9.7.1, Compose BOM 2026.09.00, minSdk 26 / targetSdk 35. На машине с 4 ГБ: `~/.gradle/gradle.properties` — `-Xmx1600m -XX:+UseSerialGC`, `workers.max=1`, `kotlin.compiler.execution.strategy=in-process`; перед длинной сессией `./gradlew --stop`.

Instrumentation (изолированный эмулятор, отдельный serial): `-e screenshots false`, RU-локаль — `adb shell cmd locale set-app-locales com.freecyber.ion --locales ru`.

**Release APK (обязательно перед передачей)**: `.\tools\Test-ApkStartup.ps1 -Apk app/build/outputs/apk/release/app-release.apk -Serial emulator-5580` — установка на изолированный эмулятор, два холодных запуска, проверка pid/resumed Activity/лога. Build + JVM-тесты + lint подписи недостаточны: v19 прошла их и падала (R8/reflection). R8 keepclassmembers для `WorkDatabase_Impl` и `InputMerger` в `app/proguard-rules.pro` — не удалять.

## CI

`.github/workflows/android.yml`: JDK 21 + SDK 37, `:core:test`, `:app:testDebugUnitTest`, `:app:lintRelease`, `:app:assembleRelease`, `:app:compileDebugAndroidTestKotlin` (-PskipScreenshots). Push-сборки публикуют подписанный `app-release` с ключом из секретов репозитория. Pull request собирает APK без подписи. Mapping хранится в `reports`. **По прямому указанию владельца проверку эмулятором в CI не добавлять** (Test-ApkStartup — только для ручной передачи).

## Инструкция владельца (постоянно действующие)

- Не генерировать и не просматривать скриншоты для проверки без нового запроса (расходуют лимит). JVM: `-PskipScreenshots`; instrumentation: `-e screenshots false`.
- Коммиты от локального git config владельца (`0CYBER`), **без Co-authored-by**. Push не запрашивался без указания.
- Записи колесу — только с ведома владельца; lock — только через проверенную последовательность с fallback'ом.

## Риски / проверить на телефоне

1. Android 14+: FGS `connectedDevice` требует `BLUETOOTH_CONNECT` до старта сервиса.
2. Фара из уведомления/виджета пишет только при `safeWrite`.
3. Авто-переподключение не срабатывает после ручного «Отключить» и «Забыть».
4. Расход батареи телефона от wake lock.
5. TTS fallback на системный голос; чип Android 16 при targetSdk 35.
6. Doze-баннер на MIUI/One UI.
7. GPS в поле: точность, расход, трек >2400 точек, разрешения на MIUI/ColorOS.

## Бэклог

Второй вариант виджета, планшет (двухпанельные настройки), вибро-оповещения, вынос логики `WheelMonitor` в `:core`; чтение возраста телеметрии в листьях, фоновый сервис-виджеты Glance (частично сделано), бандл шрифтов, frame timing/Perfetto на устройстве владельца при сохранении неровности анимаций.

## Лицензии и RE-инфраструктура

- Протокол выведен из WheelLog (GPL-3.0, `third-party/WheelLog-LICENSE.txt`); MIT LICENSE в корне не снимает GPL-обязательств — решить перед публикацией. Orion/Lumen — визуальные ориентиры, код не копировался. Вендорский APK/декомпилят не включены.
- Research root: `C:\Users\Bishkeks\Documents\inmotion-research` (офф. APK 8.1.34 ARM32+ARM64, evidence, приватные логи — не публиковать). Скрипты: `tools/re/` (`verify_arm64_lock.py`, `dispatch_lock.py`, `extract_observed_lock.py`, `compare_security_sessions.py`, `compare_ion_settings.py`). Парсеры в `%TEMP%\inmotion-re\` и `re-work` — сохранить до очистки TEMP.
