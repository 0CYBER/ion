# Ion — handoff (обновлён после 0.3.0)

> **Актуальный статус блокировки, 2026-11-02:** владелец успешно проверил включение и выключение на своём V12 HT из тестовой Ion. Оба действия подтверждены read-back; перед командой реализован наблюдавшийся защитный обмен. Подробности, ограничения, установленная сборка и свидетельства — в `docs/rolling-handoff.md`. Историю фикса см. в git. Сведения ниже про незавершённый RE, B2 и отсутствие аппаратной проверки — исторические, не текущий статус.

> RE update 2026-11-01: прежний вывод «V12 HT использует Ezcan/B2; остался сериализатор» НЕ подтверждён. Общая facade.genLockMsg вызывает one2one.genLockMsg; связь Ezcan с HT не доказана. EzcanEncoder.msgToBytes найден (ci39358), escape ci39359; footer = 55 55, есть A5 escaping. Правильная HT-команда остаётся открытой; см. docs/protocol-re-lock.md A8 и обновлённый docs/re-lock-handoff.md. Не интегрировать исторический B2-фрейм как команду HT.

## 1. Коротко: где продолжать и что сейчас

**Ion** — персональное Android-приложение (Kotlin + Compose, один модуль `app`) для моноколеса **INMOTION V12 HT**: BLE-телеметрия + пульт настроек с двухуровневой защитой записей.

- Актуальный проект: `C:\Users\Bishkeks\Desktop\ion`. Research root (не трогать без нужды): `C:\Users\Bishkeks\Documents\inmotion-research` (REA-разбор офф. INMOTION 8.1.34, WheelLog reference, evidence).
- Текущий код: **Ion 0.3.8, versionCode 12** — собран, тесты зелёные, **установлен на телефон пользователя** (`adb serial 3B15BM00SJR00000`). `applicationId` `dev.vela.v12`, ключ подписи и `vela_preferences` не менялись. Всё закоммичено и запушено в `origin/main` (github.com/0CYBER/ion); .zcode/ в .gitignore.
- **Одометр подтверждён пользователем**: статистика (u32LE[0], км = raw/100) совпадает с реальным пробегом ✓.
- **0.3.8**: остаточная серость переключателей = STATIONARY-гейт проверял возраст телеметрии ≤1500 мс, а один потерянный цикл опроса даёт разрыв до 1.7 с → окно расширено до 2500 мс (`V12Protocol.STATIONARY_AGE_WINDOW_MS`). Неподвижность по-прежнему держит дебаунс-счётчик.
- **Bluetooth-донгл MA530 возвращён в Windows** (usbipd unbind выполнен, устройство снова в Windows BT-стеке; usbipd-win остался установлен). Эксперименты с Bumble-бриджем закрыты: HCI отвечает, но LE-приёмник глух под тремя вариантами прошивок (BU/B/ROM-patch); fake-wheel+эмулятор план отклонён пользователем (риск привязки фейкового колеса к его аккаунту офф. приложения).
- Динамический RE замка продолжается статическим агентом (см. ниже); захват трафика на этом телефоне недоступен.
- **⚠️ ЛИМИТЫ СЕССИИ ЗАКАНЧИВАЮТСЯ — передача RE-работы**: полный контекст по блокировке вынесен в **`docs/re-lock-handoff.md`** (ЧИТАТЬ ПЕРВЫМ при продолжении RE). Статус: опкод замка найден (payload `B2 00 00 00 04 00 00 00` / unlock `...03...`, поколение Ezcan, НЕ 0x31), осталась одна функция — сериализатор EzcanMessage (16-байтовый фрейм: AA AA, type 0x0A, len 0x10, сумма байтов вместо XOR). Детали: `docs/protocol-re-lock.md` (A1–A7). Инфраструктура парсинга в `%TEMP%\inmotion-re\` (d219.py, funcs.pkl) — сохранить до очистки TEMP! Read-back замка уже в Ion (settings byte39 bit5). Бонус-команды: поворотник 0x30, габарит 0x3B, standby 0x16; ⚠️ power off 0x77 — не интегрировать без явного решения пользователя.
- **0.3.7**: (а) серые переключатели — колесо на балансировке микродёргается, порог неподвижности поднят до 0.5 км/ч, одиночный всплеск скорости больше не обнуляет счётчик (`movingStreak`, сброс только после 2 подряд «движущихся» пакетов); (б) режимы — два НЕЗАВИСИМЫХ переключателя по вендору: classic = **Бездорожье** (выкл = Комфорт), fancier = **Продвинутый** (выкл = Нормальный); (в) DRL удалён из протокола и UI (в офф. приложении HT его нет; у пользователя «Автофара» = наш autoLight).
- **0.3.2**: счётчик неподвижных пакетов не сбрасывается на зарядке (STATIONARY работает на зарядке на стоянке).
- **0.3.4**: кнопка «Экспорт пакетов» на «Данных» — JSON всех сырых фреймов в `Android/data/dev.vela.v12/files/`; метод before/after-диффа вокруг изменения в офф. приложении.
- **0.3.5**: **байт замка найден диффом: settings byte39 бит 5 (0x20)**; «Автофара» офф. приложения = наш autoLight (бит 3); телеметрия pcMode 4→3 при блокировке; LOCK получил настоящий read-back.
- **0.3.6**: (а) флаг `writeInFlight` — фоновые чтения (5-секундный settings-полл) больше не сереют переключатели; блокирует только реально летящий write; (б) замок — read-only строка с объяснением: **колесо игнорирует наш write 0x31, хотя состояние читается и офф. приложение переключает** — вероятно, authenticated/signed writes (машина есть в бинарнике офф. приложения, см. docs/protocol-re-notes.md §5); (в) режимы переименованы по вендору: classic = «Бездорожье», fancier = «Комфорт» (проверено пользователем).
- **Frida-сессия на этом телефоне — НЕ использовать**: spawn frida 16 с зависшим инъектом уронил загрузку (вылечено хард-ребутом), а ColorOS вырезает GATT-данные из всех HCI-логов (cached_hci содержит только management-пакеты; свойства persist.vendor.bluetooth.btsnooprootpath и persist.bluetooth.btsnooplog проверены — не помогают; свойства сброшены, /data/local/tmp чист). Рабочий метод динамического RE на этом телефоне — before/after-дифф через экспорт Ion.
- **0.3.1 правки по фидбеку**: FAN/FAN_QUIET удалены (у V12 HT/HS вентилятора нет); SAFE-tier больше не требует свежий возраст телеметрии (только живой verified-линк — как в офф. приложении; это главный фикс «переключатели через раз не доходят»); STATIONARY убран запрет зарядки (заряжающееся колесо неподвижно); stale-баннер только после ~5 с простоя; одометр на «Езде» берётся из STATISTICS (читается один раз после подключения), телеметрический mileage помечен как неподтверждённый по семантике на HT; read-back перечитывается 1 раз при таймауте (read-only, безопасно).
- Пользователь подтвердил: hotfix 0.2.1 против мигания статуса **сработал**, но при пропадании пакетов на 1–2 с ещё мелькал баннер — это починено в 0.3.0 (см. §3), подтверждения на колесе пока нет.
- На телефоне фолкпатч-рут (если `su` не работает — `truncate su`), установлена Ion 0.3.0, колесо подключено и на зарядке. Пользователь разрешил install + чтения, **записи колесу не отправлять без его ведома**.

## 2. Продуктовые решения (зафиксированы пользователем)

- Дизайн-ориентиры: **Orion Store** (github.com/RookieEnough/Orion-Store) и **Lumen** (github.com/krambovic/Lumen, Android UI), Material 3 Expressive. В 0.3.0 сделан полный рестайл (см. §5).
- **Двухуровневый guard записи** (решение пользователя): SAFE-команды доступны всегда при живой связи (даже в движении и на зарядке, как в офф. приложении); STATIONARY — только на стоячем колесе без зарядки.
- Добавлены **все** группы команд WheelLog + пользователь просил **углублённый RE офф. приложения** для команд сверх WheelLog (см. §6).
- Деморежима и «О Vela» в приложении нет (не возвращать без запроса). Вкладки: Езда / Данные / Пульт / Связь.
- Название Ion; пакет/классы/префы остаются `dev.vela.v12`, `Vela*`, `vela_preferences` ради совместимого обновления — не переименовывать.

## 3. Что изменилось в 0.3.0

### Фикс остаточного мигания статуса
- Причина второго пути: цикл опроса при одном таймауте (2000+500 мс) превышал окно свежести 2500 мс → красный баннер на ~0.5 с.
- `BleController.kt`: объект `Timing` (TELEMETRY_ASK_MS=1200, POLL_DELAY_MS=500, FRESH_WINDOW_MS=2500, STALE_HYSTERESIS_MS=1000); ask телеметрии с таймаутом 1200 → худший цикл ~1.7 с < окна.
- `VelaViewModel.kt`: hysteresis — `staleSince` в combine, баннер показывается только после ≥1 с непрерывной не-свежести (`staleConfirmed`); стиль warning, не error.
- `BleController.onConnectionStateChange`: одиночный GATT status ≠ SUCCESS при живом линке больше не роняет соединение (лог+сообщение), разрыв только по STATE_DISCONNECTED.
- Тесты: `ConnectionTimingTest` (в т.ч. `oneTelemetryTimeoutCannotExceedTheFreshnessWindow`, `stalenessNeedsSustainedHysteresisBeforeAnyBanner`), UI-тест `briefPollHiccupShowsNoStaleBanner`.

### Двухуровневый guard
- `V12Protocol.Tier { SAFE, STATIONARY }`; `canWriteSafe(...)` и `canWrite(tier, ...)`. Проверяется в UI (`VelaUi.safeWrite/stoppedWrite/canTier/canWrite(write)`) и повторно перед отправкой (`mayWrite(tier)`).
- SAFE: verified HT + CONNECTED + телеметрия ≤1500 мс + не firmwareUpdate. STATIONARY добавляет 3 неподвижных пакета, |speed|<0.1, !charging.
- Диалог подтверждения: для STATIONARY — «колесо неподвижно и не на зарядке»; mute/volume и lock имеют красные предупреждения.

### Новые команды (все из WheelLog `InmotionAdapterV2.java`, flags14/cmd60)
LIGHTS 50, BRIGHTNESS 2b, AUTO_LIGHT 2f, DRL 2d, VOLUME 26, MUTE 2c (инверт), SOUND_WAVE 39, FAN 43, FAN_QUIET 38, PLAY_SOUND 51,a,01, CLASSIC 23, FANCIER 24, SPLIT_MODE 42, SPLIT_ACCEL_BRAKE 40,a,b, PEDAL_SENS 25,a,a, PEDAL_TILT 22 s16, ALARMS 3E two s16, HANDLE_BUTTON 2e (инверт), LOCK 31, TRANSPORT 32, STANDBY 28. **Все многобайтовые значения на проводе little-endian** (WheelLog getBytes BE → реверс; проверено в репо). Валидация: VOLUME/PEDAL_SENS/SPLIT 0..100, PEDAL_TILT −10..10°, ALARMS 0..70, PLAY_SOUND 0..15, STANDBY 1..30 мин.
Read-back: SETTINGS для настроечных, TELEMETRY для LIGHTS; **DRL/FAN/FAN_QUIET/PLAY_SOUND/LOCK — readBack=null** (состояние нечитаемо), UI честно это помечает (`BlindSwitchRow` с локальным optimistic-состоянием).

### Декодирование данных (по WheelLog)
- **STATISTICS (0x14/0x11)**: u32LE×6; пробег u32[0] в единицах 10 м (**км = raw/100**), энергия [4]/[8] /100 кВт·ч (единица не подтверждена), время поездки/включения секунды. Fixture-проверка: 205.79 km.
- **BATTERY (0x05)**: обе батареи напряжение/темп/valid/enabled/work, зарядное напряжение/ток; масштаб /100 В — по аналогии, не подтверждён.
- **SERIAL** (key 02, 16 ASCII), **VERSIONS** (key 06, тройки X.Y.Z: driver 2/4/5, main 11/13/14, BLE 20/22/23), **DIAGNOSTIC** (все нули = ок).
- Телеметрия: пробег u16[22] и остаток u16[26] → км (/100, единицы WheelLog — пользователь подтвердит на колесе); ошибки [59..] декодируются в имена err_* (WheelLog getError, байт 3 — два 2-битных поля).
- Пробег и остаток пути выведены также на экран «Езда» метриками.

## 4. Архитектура и карта файлов

| Файл | Назначение |
| --- | --- |
| `Protocol.kt` | `V12Protocol`: framing/decoder, модель HT, Read-каталог (9), телеметрия/настройки/статистика/батарея/serial/версии/ошибки, `Tier`+21 `Control`, allowlist Write c read-back, `canWriteSafe/canWrite(tier)` |
| `BleController.kt` | GATT-машина состояний, `Timing`, последовательный setup, опрос, Work-канал, tier-aware `mayWrite`, мягкий GATT-status |
| `VelaViewModel.kt` | combine + `wheelUiSnapshot(wheel,saved,staleSince){clock}`, hysteresis staleSince, `VelaUi` (fresh/busy/connected/staleConfirmed/safeWrite/stoppedWrite/settingsFresh) |
| `WheelStore.kt` | Сохранение проверенного колеса (`vela_preferences`) |
| `Theme.kt` | **Единственный дом сырых цветов/шейпов**: схема light/dark, `IonColors` (hero/accent/warning), `IonShapes` (8/12/14/18/20/28/32), MaterialExpressiveTheme |
| `Ui.kt` | Общие компоненты: `squashClick` (0.94+haptic), Heading, SectionHeader, Status, Banner(info/warning/error), GroupColumn/GroupRow (морф углов 28↔4), SwitchRow (морф фона/рамки), BlindSwitchRow, SettingSlider, ApplyButton, Metric(ExtraBold), MetricPair, ReadAllButton |
| `MainActivity.kt` | Route-часть: док-навигация (pill, border, morph), 4 экрана, диалоги |
| Тесты | `ProtocolTest` (9), `ControlsTest` (15: tier-guard, payload'ы, read-back, декодеры, fixture статистики), `ConnectionTimingTest` (6), `UiVerificationTest` (9, androidTest) |

Фазы WheelState: OFFLINE, SCANNING, CONNECTING, DISCOVERING, IDENTIFYING, CONNECTED, ERROR. Сборка/опрос/записи — см. комментарии в коде; последовательность setup не менялась.

NUS UUID и framing — без изменений (см. git history 0.2.1 или Protocol.kt). HT identity: flags11/cmd02/selector01, series=7 variant=2.

## 5. Рестайл (Orion/Lumen токены)

- Палитра: lilac/acid сохранены; extended-слоты `IonColors` (hero, accent, warning). Тёмная тема без чистого чёрного. **Hex-цвета только в Theme.kt** (гейт compose-скилла проверен grep'ом).
- Шейпы: лестница IonShapes; группированные ряды с морфом углов; SwitchRow с анимированным фоном/рамкой; FilterChips; squash-пресс с haptics; док-навигация с morphing pill и scale иконки.
- Типографика: ExtraBold для крупных цифр с отрицательным трекингом, Monospace для тикающих значений и полей данных.
- Известные отступления от compose-правил (осознанные): строки хардкодом RU (одноязычное личное приложение), `ui.now` читается на верхнем уровне (500 мс тикер инвалидирует экраны — потенциальный follow-up: читать возраст в листьях).
- Шрифты Geist/JetBrains Mono НЕ бандлились (нулевой вес активов); дефолтный Roboto — возможный follow-up.

## 6. RE офф. приложения (этап 5) — PASS 2 ЗАВЕРШЁН, без новых опкодов

- Отчёт: **`docs/protocol-re-notes.md`** (169 строк, всё с confidence-метками; ничего не выдумано). Скрипты — `%LOCALAPPDATA%\Temp\inmotion-re\`, patched darter — `%TEMP%\darter`.
- Главный вывод: **новых V12 опкодов не извлечено**. Время ушло на необходимый фундамент: полное маппинг-описание Dart AOT контейнера. Ключевые факты: libapp.so — **ELF32 ARM (armeabi-v7a), не ARM64**; snapshot Dart 3.7.x era, имена функций СОХРАНЕНЫ (`no-dwarf_stack_traces_mode`); заголовки/кластеры/LEB-энкодинги (вкл. big-endian ref-id +128) задокументированы.
- **Блокер**: энкодинг cluster-tags первого кластера изолята противоречит `ClassIdTag::encode` всех проверенных SDK-исходников → ref-id не присваиваются → Function→Code→ObjectPool→Smi не резолвятся. Известные ищейки (immediates в .text, Smi-паттерны, LEB-пары) дали только шум — зафиксирован как leads, не evidence.
- **Auth-gating записей V12: не определено.** Машина (`genVerifyPwdMsg`, `genRequestEncryptedKeyMsg`, `authenticated_signed_writes`, `V12Settings{passwordType:`) в бинарнике есть; команды WheelLog заведомо работают без аутентификации.
- Deep-RE инструменты (Ghidra/Hopper/Rizin через REA) на этом Windows-хосте недоступны для ELF — только ручной парсинг.
- **Следующий проход** (план в notes §6, ранжирован): добить darter-порт (дельты точно известны, tags-энкодинг — единственное неизвестное), либо **blutter** с matched SDK (тяжело, но надёжно). Дешёвая частичная победа: Smi-cluster fill → все целые константы приложения с ref-id, привязка к пулу через литерал "Low beam and high beam state should be set in the same time in V12." (`0x4cca74`). Динамическое подтверждение — Frida-хук BLE write path (ground truth).

## 7. Сборка и окружение

- JDK 21: `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`; SDK `C:\Users\Bishkeks\AppData\Local\Android\Sdk`; adb оттуда.
- AGP 9.3.1, Kotlin/Compose plugin 2.4.0, Gradle 9.7.1; compileSdk 37, targetSdk 35, minSdk 26; Compose BOM 2026.09.00, Material3 1.5.0-beta01; Espresso 3.7.0 закреплён (не откатывать).
- Команда: `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` (JAVA_HOME на JDK 21).
- Статус 0.3.0: unit 30/30, UI 9/9 (headless AVD `Pixel_10_Pro_XL`, emulator-5580, read-only), lint 0 errors, APK `app\build\outputs\apk\debug\app-debug.apk`.
- Изолированный headless эмулятор для UI-тестов — всегда отдельный serial (не трогать физический телефон). Синтетические данные только в androidTest APK.
- Не коммитить/пушить без запроса пользователя; `.gitignore` уже покрывает кэши/ключи.

## 8. Лицензии — не потерять перед публикацией

- Протокол выведен из WheelLog (GPL-3.0, pinned `f44e39e...`, `third-party/WheelLog-LICENSE.txt`). MIT LICENSE в корне НЕ снимает GPL-обязательств; перед публикацией решить лицензионный вопрос.
- Вендорский APK/декомпилят в приложение не включены; RE-заметки — только константы/заметки.
- Orion/Lumen — визуальные ориентиры (оба GPL-3.0), код не копировался.

## 9. Ближайшие шаги

1. **Пользователь проверяет 0.3.0 на колесе**: мигание при лагах пакетов; декодированные пробег/остаток (единицы!), статистика, BMS, версии, serial; SAFE-записи в движении/на зарядке (свет/громкость) и STATIONARY на стоянке; read-back статусы. Особо: PEDAL_TILT/ALARMS на HT не тестировались — по одной команде за раз.
2. Завершить/проверить RE-заметки (§6); подтверждённые команды — только после фиксации в notes.
3. Возможные follow-ups: чтение возраста телеметрии в листьях (compose rule 2), фоновый сервис + виджеты (Glance), бандл шрифтов, экспорт/история поездок, strings в ресурсы при локализации.
4. versionCode следующий — 5; пакет/ключ не менять.

**Резюме: Ion 0.3.0 собран, протестирован (30 unit + 9 UI, lint 0 err) и установлен на телефон. Открыто: проверка на колесе (мигание, единицы пробега, новые команды), RE-этап, лицензии перед публикацией.**
