# RE-handoff: блокировка V12 HT (для следующего агента)

Дата: 2026-11-01. Статус: **опкод найден, осталась функция сериализации фрейма.**

## Цель
Заставить Ion переключать замок колеса. Чтение состояния УЖЕ работает: **settings byte39 бит 5 (0x20)** = locked (найдено before/after-диффом, см. `Protocol.kt` → `Settings.locked`), read-back готов.

## Что найдено (3 прохода статического RE libapp.so офф. INMOTION 8.1.34)

### Главное: замок — это НЕ старый протокол
- **WheelLog-овский write `0x31` игнорируется V12 HT** — потому что 0x31 принадлежит старому one2one-поколению, где опкод — рантайм-свойство модели (доказано разбором `genLockMsg` overload ci 15273: opcode берётся dispatch-вызовом из объекта модели).
- Офф. приложение использует **второе поколение протокола — EzcanMessage**.

### Команда замка (payload, откалибровано)
`genLockMsg(bool)` → `genSetModeMsg` (ci 12988) строит `Uint8List(8)`:
```
lock:   B2 00 00 00 04 00 00 00    (LE u32 command=0xB2 "set mode", LE u32 param=4)
unlock: B2 00 00 00 03 00 00 00    (param=3)
```
Калибровка пайплайна: тем же методом извлечён `mov r0,#0x26` из `genSetVolumeMsg` — совпадает с WheelLog 0x26 байт-в-байт. Пайплайн доказан.

### Wire-frame (Ezcan) — ЧТО ИЗВЕСТНО
Цепочка: `genLocalStandardWriteMsg` (ci 9467, 64-битный аргумент 0x0F550116 — внутренний routing-key, НЕ wire-байт) → `genLocalMsg` (ci 9468, инжектит type=**0x0A**, length=**0x10**=16 байт) → `genMsg` (ci 9469) строит EzcanMessage:
- два `Uint8ClampedList(4)`, оба начинаются с **[0xAA, 0xAA]** (подтверждено Smi-энкодингом 0x154 = 0xAA<<1)
- **контрольная сумма — СУММА байтов** (`genCheck` ci 9471: adds/adcs накопление, нормализация +256, диапазон −127..255), НЕ XOR как в старом протоколе
- 64-битная identity проверяется через `Integral.checked(value, 0x40, null)`; константы-геттеры: 0x01D81408 (protocol identity), 0x009A3608 (device constant)

### ОСТАВШИЙСЯ ШАГ (единственный)
Декодировать **сериализатор EzcanMessage → финальный Uint8List для BLE write** (метод toBytes/serialize/data в классе EzcanMessage; go от полей, выставленных genMsg). Известный шаблон:
```
0–1  AA AA      преамбула
2–3  ?? ??      type=0x0A / length=0x10 (как расставить — выяснить)
4–11 B2 00 00 00 04 00 00 00
12   ??         сумма (какой диапазон байтов суммируется)
...   всего 16 байт (length=0x10)
```
Детали: `docs/protocol-re-lock.md`, раздел **A7** (там же два Uint8ClampedList и genCheck).

### Бонус-находки (в notes; НЕ интегрировать без обсуждения)
- Поворотник **0x30**, габарит **0x3B**, pitch angle **0x22**, standby **0x16** (new-gen payload'ы того же семейства)
- **Power off = 0x77, payload `01 00`** (high-confidence) — ⚠️ ОПАСНАЯ команда, в UI не добавлять, тестировать только с явного разрешения пользователя
- Порог скорости/автофара: settings byte15 меняется при toggling «Автофары» (00→C8, вероятно порог скорости переключения фар ~20 км/ч)

## Инфраструктура (всё готово к переиспользованию)
- **Парсер снапшота**: `%TEMP%\inmotion-re\d219.py` — разбирает alloc pass **побайтово точно** (1055 кластеров, 317,129 объектов). Ключевые исправления: Function fill в product AOT = `[4 refs][code_index uleb][kind_tag]` (БЕЗ token_pos!); ref-id до 317129; cid: Smi=59/Mint=60 (мержатся), OneByteString=93 в image, 92 в кластерах
- **Пиклы**: `%TEMP%\inmotion-re\` — `funcs.pkl` (все 44,358 функций: name→ci→blob), pool (69,024 записей), все 22,616 int-констант с ref-id, все canonical arrays
- **InstructionsTable**: 46,112 записей; `file_addr = 0x95ce00 + pc_offset[ci-1]`; проверено по `push {r11,lr}` на 0x95ce54
- **Target**: `C:\Users\Bishkeks\Documents\inmotion-research\input\8.1.34\...\libapp.so`, SHA256 `92a24d380f129750f4893e92e823b878db531dee5483936b1d66c07d015263f6`, ELF32 ARM, Dart 2.19.1 (Flutter 3.7.x), **имена функций сохранены**
- Подробности формата с цитатами SDK: `docs/protocol-re-lock.md` (A1–A7) и `docs/protocol-re-notes.md` (pass 1–2)

## Как внедрить в Ion (после получения фрейма)
1. `Protocol.kt`: новый Write-пейлоад для `Control.LOCK` (или отдельный builder) с 16-байтовым Ezcan-фреймом
2. Read-back уже есть: `Settings.locked` (bit5 byte39) — `Write.confirmed` уже сравнивает
3. Guard: STATIONARY (замок на ходу = плохая идея)
4. ⚠️ Если фрейм неверный — колесо его молча проигнорирует, read-back скажет «НЕ подтверждено» — это безопасный путь валидации
5. НЕ добавлять power off 0x77 и калибровки без отдельного решения пользователя

## Ограничения среды (не повторять чужих ошибок)
- Захват HCI на телефоне пользователя (CPH2745, ColorOS) НЕ работает: вендор вырезает GATT-данные из cached_hci; свойства btsnooplog/btsnooprootpath проверены и не помогают; Frida на этом телефоне ронял загрузку ОС. Рабочий метод — before/after-дифф через «Экспорт пакетов» в Ion
- Bluetooth-донгл MA530 (VID 2C4E:0115, RTL8761B): Bumble видит, HCI отвечает, но LE RX глух под 3 вариантами прошивок; донгл возвращён в Windows (usbipd unbind). Если продолжать — пробовать Zadig+нативный Windows Bumble
- Fake-wheel+эмулятор план отклонён пользователем (риск привязки к его аккаунту)
