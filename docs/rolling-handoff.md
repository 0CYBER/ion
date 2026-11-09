# Rolling handoff — Ion

Единый рабочий журнал «по мере работы». Часть I — реверс блокировки V12 HT (завершено, проверено на колесе 2026-11-02). Часть II — развитие приложения AI-сессиями до 0.9.2-ai и перенос в main. Гипотезы, включая опровергнутые, сохранены с пометками — чтобы не наступать повторно.

Финальная реализация (что работает сейчас) — `../handoff.md`.

---

# Часть I — RE блокировки V12 HT (2026-10-30 → 2026-11-02)

Цель: установить точный wire-frame блокировки/разблокировки V12 HT из офф. приложения INMOTION 8.1.34 и подтвердить его; не считать исследование завершённым без проверки.

Research root (не в репозитории): `C:\Users\Bishkeks\Documents\inmotion-research`. Целевой артефакт: `libapp.so` из split APK `config.armeabi_v7a.apk`, SHA-256 `92a24d380f129750f4893e92e823b878db531dee5483936b1d66c07d015263f6`, 23 462 480 байт. Snapshot hash `adb4292f3ec25074ca70abcd2d5c7251`.

## Ограничения среды (не повторять)

- Native-RE (Ghidra/Hopper/Rizin/IDA) на этом Windows-хосте недоступны для ELF (`ERR_REA_WINDOWS_NATIVE`); разбор — локальный Python 3.11 + Capstone 5.0.7.
- Захват HCI на телефоне владельца (OnePlus CPH2745, ColorOS) НЕ работает: вендор вырезает GATT-данные из cached_hci; свойства btsnooplog/btsnooprootpath проверены — не помогают. Frida на этом телефоне однажды уронил загрузку ОС (вылечено хард-ребутом) — НЕ использовать. Рабочий метод динамического RE — before/after-дифф через «Экспорт пакетов» Ion и PID-filtered `adb logcat` офф. приложения.
- Bluetooth-донгл MA530 (VID 2C4E:0115, RTL8761B): Bumble видит, HCI отвечает, но LE RX глух под тремя прошивками; донгл возвращён в Windows (usbipd unbind). План fake-wheel+эмулятор отклонён владельцем (риск привязки фейкового колеса к его аккаунту офф. приложения).
- Read-back состояния замка: settings byte39 бит 5 (`0x20`) — найден before/after-диффом, работает.

## Pass 1–2 (2026-10-30): контейнер разобран, wire-опкоды не извлечены

- Главное: libapp.so — **ELF32 ARM (armeabi-v7a), не ARM64** (формулировка брифа «ARM64» была неверна для этого файла). `no-dwarf_stack_traces_mode` → имена функций сохранены. Snapshot изначально считали «Dart 3.7.x era» — **это предположение оказалось ошибкой** (см. Pass 3).
- Установленные факты формата: заголовки blob'ов (magic 0xdcdcf5f5, kind 3 = kFullAOT), isolate blob 1055 кластеров / 317 129 объектов, InstructionsTable 46 112 записей; OneByteString cid = 93 в image; encodings: WriteUnsigned/Write\<T\> — LEB с особым терминатором, **ReadRefId — big-endian**.
- Поиск опкодов провалился корректно: ARM immediate scan `.text` (13 035 сайтов) — dead end; pool-entry Smi scan — 10³–10⁴ ложных срабатываний/оп; LEB-пары констант — шум, зафиксирован как leads, не evidence.
- **Блокер pass 2**: cluster-tags энкодинг первого кластера (значение 185 = `(92<<1)|1`) противоречил `ClassIdTag::encode` всех проверенных SDK-исходников → ref-id не присваивались → Function→Code→ObjectPool→Smi не резолвились.
- **Опровергнуто впоследствии** (не повторять): «+128 bias в ReadRefId» (биаса нет — знаковый декод терминатора самоаннулирует +128; стоило часы); «константы команд живут в пуле» (см. Pass 3 §4); «дельты от 4+length» для RO image (база — 0x3DC000 page-aligned).
- Auth-gating V12-записей: машина есть в бинарнике (`genVerifyPwdMsg`, `genRequestEncryptedKeyMsg`, `authenticated_signed_writes`, `V12Settings{passwordType:`), но WheelLog-команды работают без аутентификации; гейтинг других записей на HT тогда не определён.

## Pass 3 (2026-11-01): формат снапшота решён, пул декодирован

- **Корень всех противоречий pass 2: версия SDK.** Snapshot hash соответствует **Flutter 3.7.1–3.7.12 = Dart 2.19.x**, а не Dart 3.7. Кластер-тег `(cid<<1)|canonical` подтверждён дословно из `app_snapshot.cc` 2.19.1. Написан свежий парсер `d219.py` (доработка darter была бы дороже): alloc pass точен, 316 042 ref'а ровно.
- Два бага, снявшие fill-цепочку: (1) Function fill в product AOT — **без token_pos**: `[4 refs: name, owner, signature, data][code_index uleb][kind_tag u32]`; (2) диапазон ref-id 1088..317129, а не ≤316042.
- ObjectPool: один глобальный, 69 024 записей (63 719 tagged, 4370 immediates). **Решающий негативный результат: в пуле нет малых целых констант вообще** ⇒ байты команд — inline ARM immediates внутри gen*Msg тел (формат «команды в пуле» опровергнут), константных byte-array шаблонов тоже нет.
- InstructionsTable: data struct 0x33DC4C, code file address = `0x95ce00 + pc_offset`, entry N−1 для code_index N. Асимметрия баз RO image (строки 0x3DC000, InstructionsTable 0x33DC40) — эмпирическая, обе валидированы содержимым.
- Калибровка пайплайна: `genSetVolumeMsg` → `mov r0,#0x26` = ровно WheelLog-опкод setVolume. Пайплайн имена→ci→blob→capstone доказан.
- **Вывод A3 pass 3 (впоследствии отозван для HT)**: lock-команда — 8-байтовый LE-пейлоад **`B2 00 00 00 04 00 00 00`** (lock) / `B2 ...03 00 00` (unlock) через Ezcan-путь `genSetModeMsg` ci 12988. Инфраструктура (`funcs.pkl` 44 358 функций, pool/arrays pickles) — в `%TEMP%\inmotion-re\` и `re-infra`/`re-work` вне TEMP; **сохранить до очистки TEMP**.
- Бонус-опкоды того же метода: поворотник 0x30, габарит 0x3B, pitch 0x22, standby 0x16, speed unit 0x0E/0x46/0x47. **Power off 0x77 payload `01 00`** — опасная команда, в UI не добавлять без отдельного решения владельца.
- Ezcan wire-детали (pass 3 A7): EzcanMessage строит **два** преамбульных массива — `AA AA` и `55 55`; checksum — **сумма байтов, не XOR** (add/adcs, нормализация +256); 0x0F550116 — внутренний routing-key, не wire-байт; escape ci39359 вставляет A5 перед 55/AA/A5 тела.

## Pass 4 (2026-11-01): гипотеза «Ezcan = HT» отозвана; 0x31 подтверждена dispatch-таблицей

- **Независимая проверка dispatch** (`tools/re/dispatch_lock.py`, калибровка на геттерах фары V1=0x40/V2=0x50): для принимающих классов `_V12CmdV1` CID 2109 / `_V12CmdV2` CID 2110 обращение `SpecialMessageFactory.genLockMsg` ci15273 приводит к одному ci19327: `mov r0,#0x31; mov r1,#0; bx lr`. Кандидат lock-опкода для V12 — **0x31**, а не Ezcan B2. Условно-статическое доказательство; выбор HT-класса нужно было показать отдельно.
- Маршрут модели замкнут статически: `InMotionProtocol.parseProtocolType` (имя начинается с `V12`) → `preInit` создаёт `LorinMessageFactory` CID 1767 + `LorinProtocol` CID 2423; `LorinMessageFactory.init` → при `isV12Series` создаёт `V12MessageFactory` CID 1760 → `_V12CmdV1/V2` → 0x31. `bool2Bytes`: true → `01`, false → `00` (SDK 2.19.1 object.cc: бит адреса true=0).
- Поле команды: `genCmd` для 0x31 даёт **`60 31`** (порог 0x20 → два байта `00 31`, write=false → +0x40, длина>1 → +0x20) → содержимое `60 31 01` / `60 31 00` до обрамления.
- **Расхождение с Ion, найденное статикой (не стало причиной — но проверялось)**: builder'ы дают format `0x16` (`Util.calFormat(0x10|0|0|6)`), Ion строит `0x14`; плюс дополнительное поле назначения `0x21` от `getHmicDevice` для Lorin EUC. Запрет: не исправлять команду вслепую, не подменять исследование пробной отправкой.
- Security path: `LorinProtocol.buildSecurityConnection` → `genRequestEncryptedKeyMsg`; `Encoder.encode` проверяет бит 0x08 в message[+7] и вызывает `Cryptor.encrypt` (XOR-цикл от байта `[data+0xe]`); у builder'а `0x16 & 0x08 == 0` — шифрование не выставляется этим путём. Runtime-необходимость не была доказана на этом этапе.
- Телефон подключён (read-only): OnePlus CPH2745, Android 17/SDK 37, офф. приложение INMOTION 8.1.34 vc 9302, **arm64** (предыдущий листинг — armeabi-v7a; идентичность поведения требовала отдельной проверки). Flutter-строки `write characteristic ...` с hex-фреймами доступны в обычном logcat по PID — неожиданно, несмотря на обрезанный HCI snoop.

## ARM64: независимая проверка установленной сборки

- Восстановлены 1055 allocation clusters, 11 525 Class, typed Functions, все 115 054 записей dispatch table. V12 factory CID 1760, `_V12CmdV1/V2` 2109/2110 — идентично ARM32. `LorinProtocol.lock` (code 43775) → `genLockMsg` (19477→19478); через dispatch у обоих V12-классов getter → code 18407: `mov x0,#0x31; ret`. Origin 4096 подтверждён первоисточником dispatch_table.h Dart 2.19.1; контроль headlight — 0x40/0x50.
- ARM64 `genSpecialOne2ManyMsg`→`calFormat(6,0,0)` даёт `0x16` — то же расхождение с Ion `0x14`. MTU-рисок зафиксирован: второй security write 25 байт, `BleController` не запрашивал MTU (стандартный ATT 23 = 20 байт полезных).
- Скрипт `tools/re/verify_arm64_lock.py` воспроизводим на `libapp.arm64.so` (SHA `334d0cdba71abd205a550b952faa9541490170132fd7f1347b8d0efe0024f8da`). Evidence: `docs/evidence/lock/phone-arm64-lock-path.txt`, `phone-arm64-dispatch.txt`.

## Реальные кадры офф. приложения (PID-filtered logcat)

- Контрольные снимки Ion: между locked/unlocked экспортами SETTINGS отличается **ровно один бит** — byte39 XOR `0x20` (1=locked, 0=unlocked). Привязка к действиям — из запрошенного порядка + состояние через Ion.
- Владелец вручную включил/выключил замок через офф. приложение. Захвачено (`docs/evidence/lock/phone-official-observed-lock.txt` — обезличенно; полный лог вне репо, содержит идентификаторы):
  - lock: **`AA AA 16 05 02 21 60 31 01 60`**, unlock: **`AA AA 16 05 02 21 60 31 00 61`**; ответ обоих: `AA AA 16 05 21 02 E0 31 00 E1` (адреса переставлены, XOR E0 31 00 = E1). Контрольный байт = XOR от format до bool.
  - Ion генерировал другой кадр: `AA AA 14 03 60 31 01 47` — различие format/длина/адреса подтверждено. Причинность требовала управляемого теста.
- **Пропущенный шаг найден перед каждым lock/unlock**: `AA AA 13 04` (9Б) → ответ `AA AA 12 13` (24Б) → write `AA AA 13 14` (25Б) → ответ `AA AA 12 04` (9Б), потом основной кадр. Интервалы ~116/~69 мс. Все 16 байтов первого ответа == следующим 16 байтам запроса 0x82; на 4 действиях в 2 соединениях все 16-байтовки совпали между собой. Значения не публиковать и не захардкоживать. Гипотеза: подготовительный защитный обмен/авторизация, отсутствующий в Ion.
- Статическое подтверждение назначения (ARM64): `buildSecurityConnection` CI 43901 → `genRequestEncryptedKeyMsg` CI 7763 (0x02) → `AesUtil.decryptEcbNoPadding` CI 8450 → `genVerifyCryptKeyMsg` CI 7765 (`AesUtil.encryptEcbNoPadding`, сообщение с 0x82). Оба AES читают один слот; decrypt→encrypt сокращение требует анализа. Не считать наблюдение равенства wire bytes доказательством отсутствия AES.
- Повторное наблюдение после переподключения: те же кадры и ACK; во втором действии официальный автоматический повтор 25-байтового write через ~302 мс — **не копировать повтор в экспериментальный контроль без обоснования**.

## Локальная реализация защитного обмена и аппаратная проверка

- `LockSecurity.kt`: запрос 0x02, строгая проверка ответа (16 байтов, AA/A5-экранирование, разбиение), формирование 0x82 из байтов текущего ответа, проверка наблюдавшегося ответа `80 82 01`. Явная гипотеза на 4 runtime-парах + статике AES-пути; без хардкода значений.
- `BleController`: перед lock запрашивает MTU, отказ при <44; security-ответы маршрутизируются до legacy-фильтра; при неверном security lock не отправляется; guard пере-проверяется после обмена; ровно один lock write; read-back повторно. Debug-файл `lock-test-diagnostic.txt` (без payload и идентификаторов) — запасной путь, т.к. logcat-тег IonLockTest на телефоне сначала не наблюдался.
- Итог наблюдаемого пути (проверен на этом колесе):
  1. Подключение, неподвижность/свежесть, MTU 247.
  2. Запрос `AA AA 13 04 02 00 00 02 17` → ответ `12 13 02 80 02` с 16 байтами; те же байты возвращаются в запросе `13 14 02 00 00 82`. Не переиспользовать между сессиями.
  3. Ожидать строго `AA AA 12 04 02 80 82 01`; иначе lock не отправлять.
  4. Guard → один раз `AA AA 16 05 02 21 60 31 01 60` (lock) / `... 31 00 61` (unlock). ACK `... E0 31 00 E1` наблюдается, но не заменяет read-back.
  5. SETTINGS byte39 mask `0x20`: установлен = locked.
- **Аппаратная проверка: успех.** Владелец подтвердил включение и выключение на своём V12 HT. `docs/evidence/lock/ion-lock-unlock-success-logcat.txt` (SHA `018c838edf84c49f...`) независимо фиксирует: MTU=247, security-ответы, правильный кадр, reply, `readback result=match` для обоих действий. Первый тест до реализации security дал «НЕ подтверждено» — read-back не совпал (red); с обменом — match (green).
- Ограничения: проверено на одном V12 HT / прошивке / телефоне; универсальность, семантика кодов, криптоконтекст, краевые случаи, единственная причина старого отказа — не установлены. Раздельного эксперимента, изолирующего одну причину, не было: рабочая версия изменила security-последовательность и MTU-поведение одновременно.

---

# Часть II — развитие приложения (0.4.0 → 0.9.2-ai, main)

## 2026-11-02 (вечер) — правки по списку владельца (0.4.0, versionCode 13)

Предыдущий агент успел только переименовать заголовок журнала; работа с нуля. Закрыты 8 пунктов: (1) наклон педалей ×10 → settings[15] s16 в **0.01°** (единица записи выведена из чтения, на колесе сразу не проверялась — первый раз малое значение); (2) телеметрический «пробег» → `tripKm` (дистанция поездки, u16[22]); (3) убраны фейковые/неподтверждённые поля (темп. батареи/фары, темп. батарей 1/2, зарядный ток, поле [20]); (4) диалог подтверждения переключателей удалён, тап = запись (guard и read-back остались); (5) слайдеры в раскрывающихся `ExpandRow`; (6) убраны служебные/пояснительные тексты, writeMessage — только ошибки; (7) пакет → `com.freecyber.ion` (отдельное приложение рядом со старым `dev.vela.v12`, колесо не переносится; классы `Vela*` и префы `vela_preferences` не трогали); (8) README и handoff актуализированы. Сборка: unit 35/35, lint 0 errors. APK не установлен, не закоммичено.

## Сессия 1 — фон, уведомления, виджет, модульный экран поездки (ветка `ai-experimental`, 0.7.0-ai / v16)

Проблема исходника: BLE жил во ViewModel и рвался в `onStop`. Архитектура:
- `IonApplication` + `IonGraph` — процессный граф: `AppPrefs`, `WheelStore`, `BleController`, `TripRecorder`, `WheelMonitor`.
- `WheelService` — foreground service `connectedDevice`: живая карточка, обновление раз в секунду, авто-стоп через 4 с без связи.
- `WheelMonitor` — координатор: питает `TripRecorder`, `AlertEngine`, авто-переподключение с backoff (`ReconnectPolicy`: 2 с → 60 с, 40 попыток), поднимает сервис, пушит виджет.
- `AlertEngine` — edge-triggered с гистерезисом: низкий/критический заряд, перегрев, скорость (cooldown 20 с), потеря/восстановление связи, ошибки колеса. `IonNotifications` — 3 канала; `WheelErrors` — RU-названия err_*.
- `IonWidget` (Glance) + `WidgetSnapshot` — снапшот переживает рестарт процесса; `Dashboard.kt` — `DashTile` (18 модулей), кодек раскладки совместим вперёд; `RideScreen` собирается из раскладки. Новые плитки: мощность, PWM, наклон, дальний свет, звук, режим, сессия поездки.
- Проверка: 55 unit-тестов зелёные. CI `.github/workflows/android.yml` добавлен. На реальном колесе/телефоне не проверялось.

## Сессия 1 — продолжение

- `IonTileService`: QS-плитка. `TripHistory`/`TripSummary`/`TripTotals`: поездки до 200, <50 м отбрасываются, CSV-экспорт, удаление. +4 теста (59).
- lint: единственная ошибка (deprecated `startActivityAndCollapse`) подавлена точечно. «Подключаться при запуске»; разрешение на уведомления перепроверяется в `onResume`; checkpoint поездки раз в 30 с + `recoverPending()`; голос `VoiceAnnouncer` (TTS, RU, `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`) + `BatteryStepper` (каждые 10 %, защита от дрожания).
- Скриншот-тесты Robolectric 4.17 + Roborazzi: 9 экранов без устройства; по снимкам исправлены высоты парных плиток, обрезанные подписи, FlowRow в карточке сессии.
- Wake lock: несчётный `PARTIAL_WAKE_LOCK`, продление на 2 мин — колесо опрашивается, а не стримит; без него CPU засыпает между опросами.
- Модуль `:core` (чистый JVM): Protocol, LockSecurity, Formatters, AlertEngine, Config, Dashboard, TripRecorder, BatteryStepper, ReconnectPolicy, WheelErrors, WidgetSnapshot. Android-сторона `WidgetMapping` осталась в `:app`.
- Авторство коммитов: ветка шла от глобального `git config` машины (`mybydemvarit jesse <free.wplace4@gmail.com>`); задано `0CYBER <heyartemka@gmail.com>`, 8 коммитов переписаны `rebase --exec` и отправлены `--force-with-lease`. `main` не трогался.
- Плитка «График»: `SampleBuffer` (6 мин, 1 точка/с) + `ChartTile` (Canvas, заливка скорости + линия мощности).
- Виджет: `carryOver(previous)` — без связи хранит последние цифры и время; устаревшие приглушены; полоска заряда при ≥110 dp; `widget_preview.xml` для списка виджетов.
- Чип в статус-баре (Android 16 Live Updates): `setRequestPromotedOngoing(true)` + `setShortCriticalText`; разрешение `POST_PROMOTED_NOTIFICATIONS`. Риск: targetSdk 35 — проверить на Android 16.
- Доступность: fontScale 1.5 снимки; FlowRow, мягкие переносы `\u00AD`, `Hyphens.Auto`.
- Локализация RU/EN: `res/values-en/`; BLE-строки в `ble_strings.xml`; `IonFormat.locale` из ресурсов; плюралы для голоса; `localeConfig` (выбор языка приложения на Android 13+). Не переведено осознанно — позже закрыто слоем `tr(ru, en)` (`CoreText.kt`) в `:core`: чтения, команды, ~60 полей диагностики, 43 ошибки. Числа диагностики по `IonFormat.locale`.
- Батарея: баннер + кнопка «Снять ограничение батареи» (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, приложение-компаньон устройства безопасности), запасной путь — настройки.
- `ReconnectPolicy.decide(...)` вынесен в чистую функцию (Cancel/Show/Retry). Ландшафт: `IonRail` слева при высоте <480 dp; при ширине ≥560 dp спидометр закреплён слева 42 %.
- Оповещение «цель зарядки»: `AlertKind.CHARGE_TARGET` добавлен в конец enum (ordinal-совместимость id), перевзводится после −5 % ниже цели. `ThresholdSlider` получил `steps`.
- Glance-тесты `WidgetTest` (4). Итог сессии: 94 теста, lint 0 ошибок, debug APK 0.7.0-ai собран по запросу.

## Сессия 2 — редакторы, GPS (0.8.0-ai / v17)

- **Фикс одноразового применения настроек**: причина — `remember(s.field)` пересоздавал драфты на каждый read-back. Новый `ExpandSliders` + `SliderSpec`: нетронутый слайдер следует за колесом, тронутый хранит значение; Apply держит драфты до подтверждения read-back'ом. `IonApp.write` больше не молча дропает при неактивном гейте — отказ честно в баннер.
- **Дашборд**: редактор вокруг живого предпросмотра (`DashTileView`, read-only слой, тап = выбор); `DashTile.param` — выбираемый номер звука 1..8 (по умолчанию **4** — правильный сигнал V12 HT по словам владельца; раньше хардкод 1); SPEED/MODE половинные; новые плитки `QUICK_AUTO_LIGHT`, `QUICK_SOUND_WAVE`.
- **Меню «Приложение»** (route `app`): Оформление, Экран поездки, История, Фон и уведомления.
- **GPS-треки**: `:core` `Track.kt` (`TrackPoint`, Haversine, thin до 2400, дельта-кодек 1e-5°≈1.1 м); `TrackRecorder` (LocationManager GPS+NETWORK без Play Services, 1 с/3 м, отбраковка accuracy>30 м, дедуп стоянки); треки под `track_<startedAt>` (≤30, удаляются с поездкой); `TripDetail.kt`: Canvas-маршрут с метрической проекцией, скраббер, цифры в моменте. GPX-экспорт (`TrackCodec.gpx`, экранирование — поймало отсутствие `&gt;`). Счётчик GPS-фиксов в карточке сессии; разрешение, выданное посреди сессии, подхватывается.
- **Регресс-тест `ControlEditorTest` поймал реальный баг моего же рерайта**: change-лямбда писала захваченное `value` вместо аргумента — слайдер возвращался назад. Исправлено. `SettingSlider` получил `sliderTag`, `ApplyButton` — modifier.
- Итог: 105 unit-тестов (core 48 + app 57), lint 0 ошибок. Ограничения треков: checkpoint сохраняет сводку, но не точки; при выключенной записи трек не пишется.

## Сессия 3 — редактор главного экрана, навигация, плавность (0.9.0-ai / v18, коммит `704f5c5`)

Запрос владельца: пересмотреть кастомизацию, повторяемый универсальный переключатель, новый цифровой шрифт, меньше док без подложки, настройки, убрать лаги. Инструкции: коммиты без Co-authored-by, обновлять журнал и handoff; **не проверять и не генерировать скриншоты** (уточнение пришло после первоначального просмотра).

- `DashboardEditor.kt`: тап по плитке → нижний лист (порядок, размер, назначение, удаление); каталог сгруппирован; превью фиксируется при входе и не шлёт команды; accessibility-дерево очищено, у каждой плитки именованный target.
- `Dashboard.kt`: независимый `TileSlot.id` — несколько экземпляров одной плитки; кодек `v2|NAME:visible:wide:param:id:toggle`; старые строки читаются; QUICK_* — псевдонимы универсального переключателя; удалённые плитки и пустая раскладка v2 не восстанавливаются автоматически. **Не откатывать кодек к уникальности по DashTile.**
- `DashToggle`: 11 читаемых переключателей колеса; назначение — семантическим именем; свет сохраняет состояние второй фары; tier-защита протокола не менялась. Новые метрики: макс/средняя скорость, энергия, расход.
- Навигация: сохраняемый стек вложенных экранов; системный Back снимает один экран (App → дочерний, Trips → Trip); переключение вкладок закрывает цепочку.
- Док: убрана общая капсула-подложка, компактные targets от 48 dp, иконки 22 dp, анимированная подпись; на Android 29+ отключён контрастный scrim. Настройки: ровные пары категорий, три мотива анимации нажатия.
- Цифры: встроенный Onest, табличные `tnum`; JetBrains Mono остаётся в диагностике. Новых зависимостей нет.
- Crash «половинная скорость + другая плитка»: intrinsic sizing измерял BoxWithConstraints — пары измеряются естественно.
- Плавность: 500-мс tick менял `VelaUi.now` и инвалидал корень — State до нужного destination, `samePresentationAs` пропускает только полезные изменения (включая fresh/stoppedWrite/settingsFresh/staleConfirmed), возраст пакета тикает в отдельном компоненте, clock-flow прекращается после остановки подписки (WhileSubscribed 5 с).
- Красный/зелёный: `NavigationTest.appChildBackReturnsToAppMenu` сначала воспроизвёл ошибку, затем прошёл. 98 тестов (core 58 + app 40) без снимков; lint 0 ошибок / 24 предупреждения.
- Instrumentation на изолированном emulator-5580: **16/16 без снимков**; свежий AVD стартовал en-US — перед RU-тестами `adb shell cmd locale set-app-locales com.freecyber.ion --locales ru`; зависший System UI эмулятора перезапускался.

## Сессия 3 — продолжение: док, зарядка, шрифт, плавность (0.9.1-ai / v19)

Уточнения владельца: убрать прямоугольную полосу позади дока (капсулу оставить), диагностика тоже в Onest, скорость слишком высокая, потеряна Material-анимация зарядки, 165 Гц ощущается неровным.

- Причина полосы: `Scaffold.bottomBar` резервировал область. Док перенесён в overlay `Box`, капсула возвращена; `LocalDockClearance` — нижний отступ содержимого скролла, доскролл выше дока.
- Диагностика: Onest + tnum, Mono только для raw. Скорость: padding 24→16 dp, gaps 12→8 dp, убрана избыточная подпись «Показания с колеса».
- `BatteryTile` теперь принимает charging: при свежей зарядке — нативный indeterminate `LinearWavyProgressIndicator`, реальный процент виден (включая 100 %); обычный уровень — determinate wave + `ProgressAnimationSpec`; циклы MD3 от frame clock, независимо от тика телеметрии.
- Плавность: dock pill цвет читается в drawBehind; Paths графика и Stroke кэшируются drawWithCache; относительное время в Long до Float (точность на большом uptime); сдвиг перехода 1/30→1/12 ширины; `FrameRateCategory.High` без жёсткого cap.
- **Отдельный release-сборка**: R8 + shrinkResources, не debuggable, локальный debug-ключ (обновление личной установки). v19 собрана: 2,2 МБ, aapt2/apksigner проверки пройдены, 101 JVM-тест, lint 0 ошибок. Реальных замеров кадров на телефоне нет — подключённых устройств не было.

## Сессия 3 — исправление падения оптимизированного APK (0.9.2-ai / v20)

**v19 неработоспособна: не распространять.** Build/lint/подпись недостаточны — сам minified APK до передачи не запускался. JVM-тесты используют debug-классы и не видят R8-ошибок.

- Воспроизведено на headless emulator-5580: установка ok, `am start -W` даже `Status: ok`, затем процесс умирает: `InitializationProvider → WorkManagerInitializer → Failed to create WorkDatabase`. Stack trace: `build/release-startup-crash.log`.
- Причина: Glance 1.1.1 транзитивно → WorkManager 2.7.1 / Room 2.2.5; consumer rules сохраняют классы, но не default constructors при AGP 9/R8 — R8 удалил вызываемый через reflection `WorkDatabase_Impl`. Точечные keepclassmembers для `WorkDatabase_Impl` и наследников `InputMerger` в `app/proguard-rules.pro`; R8 и shrink сохранены, пакетных keep-all нет.
- `tools/Test-ApkStartup.ps1`: устанавливает готовый APK только на явно заданный emulator-serial, force-stop/холодный запуск дважды, через 5 с проверяет pid, resumed MainActivity, отсутствие app crash; `am start success` не считается прохождением; без снимков.
- Red/green: скрипт отклонил v19 с исходным падением; v20 прошёл оба запуска (разные процессы), XML-иерархия подтвердила 48 узлов и заголовок Ion. `Ion-0.9.2-ai-v20-optimized.apk` побайтно совпадает с проверенным (SHA-256).

## 2026-11-06 — перенос ai-experimental в main

Все 35 коммитов (до b13af2a) fast-forward в main по запросу владельца; без squash/rewrite; экспериментальная ветка сохранена; origin/main не содержал отдельных коммитов. Дальше — обычный push в https://github.com/0CYBER/ion без force. Продолжать на main; заголовки ветки в этом журнале — исторические.

## 2026-11-06 — CI собирает release

Workflow: `:core:test`, `:app:testDebugUnitTest`, `:app:lintRelease`, `:app:assembleRelease`, `:app:compileDebugAndroidTestKotlin`; артефакт `app-release` + mapping в reports. **Владелец отдельно запретил проверку эмулятором в workflow** — явное исключение из рекомендации smoke test (тот остаётся обязательным для ручной передачи APK). Проверено локально: YAML, отсутствие emulator steps, task graph (--dry-run: assembleRelease включает minifyReleaseWithR8). Подпись CI — debug signingConfig раннера; обновление поверх локального APK без общего ключа не гарантируется.

# Часть III — ветка `ai-upgrader` (непрерывная доработка)

Запрос владельца (2026-11-09): отдельная ветка `ai-upgrader` от main (`5ff58ab`), циклы «анализ → улучшение» (код, дизайн, UX, фичи), частые коммиты, обновлять этот журнал и `handoff.md`. Push не запрашивался. Сборка/тесты — Linux-машина агента (`/home/agent/workspace/ion`, 4 ГБ, без swap; настройки `~/.gradle/gradle.properties` как в handoff).

## Цикл 1 — заголовки в тёмной теме

- Симптом: заголовки страниц (Ion, Настройки, Оформление, Педали…) чёрные в обеих темах, в тёмной не видны.
- Причина: `Scaffold(containerColor=Color.Transparent)` берёт `contentColorFor(Transparent)` = текущий `LocalContentColor`, а на корне это `Color.Black` (фон рисует `BoxWithConstraints`, не `Surface`). `IonType.page` цвета не задаёт → `Text` наследовал чёрный. Карточки не страдали — они внутри `Surface` с собственным contentColor.
- Исправление: `Scaffold(contentColor=colorScheme.onBackground)` в `IonApp.kt` (исправляет весь текст прямо на фоне) + явный `onBackground` в `ScreenHeading`/`DetailHeading` (`ExpressiveComponents.kt`).
- Проверка: `:app:compileDebugKotlin` ok; `:core:test :app:testDebugUnitTest -PskipScreenshots` ok. Скриншоты не делались (правило владельца). Коммит `9fa6793`.
