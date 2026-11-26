> Ветка m3-test, 26 ноября 2026. Эта редакция заменяет «Lumen-подобную» систему (тональные плитки, плавающий док, фиксированная палитра) на эталонный Material 3 Expressive с Material You. Исторические скриншоты в design-preview относятся к прежней редакции.

# Ion — дизайн-система (Material 3 Expressive + Material You)

## 1. Принципы

1. **Только роли Material.** Экраны берут цвета из `MaterialTheme.colorScheme`, формы — из шкалы форм, типографику — из шкалы Material 3. Своих «тонов» нет; единственные дополнительные роли — `warning` и `success` в `ionColors()` (у Material для них нет слотов).
2. **Material You.** На Android 12+ схема строится из обоев (dynamic color, включено по умолчанию). Если цвета обоев выключены или недоступны (Android 8–11), схема Tonal Spot генерируется из одного из шести seed-цветов (`IonPalette`) через material-kolor (HCT). Все тональные отношения и контраст — как у системной схемы.
3. **Одно главное число на экран.** Expressive разрешает выйти за display large ровно для одного героя: скорость на «Поездке», дистанция в поездке, итог в истории.
4. **Навигация — по гайдлайнам.** 4 назначения верхнего уровня → flexible navigation bar; в альбомной ориентации — navigation rail. Вложенные страницы полноэкранные, с back в top app bar; нижняя навигация на них скрывается.
5. **Безопасность записи не трогается.** Визуальные изменения не меняют ни одного условия `canWrite`, read-back или tier.

## 2. Цвет

| Источник | Когда |
| --- | --- |
| `dynamicLight/DarkColorScheme` | Android 12+, «Цвета обоев» включены (по умолчанию) |
| `seedScheme(IonPalette.seed)` (Tonal Spot) | Цвета обоев выключены или Android < 12 |

Палитры: Фиалка `#6937D4` (бренд Ion), Синий `#1F5FBF`, Бирюза `#00796B`, Зелень `#4C7A1E`, Янтарь `#B0660B`, Роза `#B02A5B`. Схемы кешируются на пару (seed, тёмная).

Применение ролей:

| Элемент | Роль |
| --- | --- |
| Фон страницы, top app bar | `surface` → `surfaceContainer` при прокрутке |
| Строки списков, карточки данных | `surfaceContainer` |
| Карточка-герой (скорость, итоги, поездка, обновления, сохранённое колесо на связи) | `primaryContainer` / `onPrimaryContainer` |
| Заряд | `tertiaryContainer` |
| Запас хода, нагрузка (PWM) | `secondaryContainer`; PWM ≥ 80 % — `warning` |
| Включённый быстрый переключатель (QS-плитка) | `primary` / `onPrimary` |
| Бейджи категорий | `primary/secondary/tertiaryContainer`, нейтральный — `surfaceContainerHighest` |
| Ошибка, предупреждение | `errorContainer`; `warning` (фиксированный янтарь тона 90/10 и 30/90) |
| Навигационная панель | `surfaceContainer`, индикатор `secondaryContainer` |

Цвет никогда не единственный носитель смысла: рядом всегда текст, иконка или форма.

## 3. Типографика

Шкала Material 3 без изменений размеров и интерлиньяжа, гарнитура **Onest Variable** (локальный файл, кириллица; по пропорциям близка к Google Sans). JetBrains Mono — только raw-пакеты диагностики.

| Использование | Стиль |
| --- | --- |
| Заголовок вкладки (large flexible app bar) | headlineMedium → titleLarge при сворачивании |
| Заголовок вложенной страницы (medium flexible) | headlineSmall + подзаголовок |
| Подзаголовок списка | titleSmallEmphasized, `primary` |
| Заголовок строки списка | titleMediumEmphasized |
| Вспомогательный текст строки | bodyMedium, `onSurfaceVariant` |
| Скорость | `IonType.speed` 96/100 sp (компакт 64/72), Medium, tnum |
| Показания плиток | `IonType.figure` 32/40 sp, tnum |
| Итоги, дистанция поездки | displaySmall/MediumEmphasized, tnum |

Все числа — с табличными цифрами (`tnum`), чтобы не «прыгали» при обновлении.

## 4. Формы

Шкала: extraSmall 4 · small 8 · medium 12 · large 16 · largeIncreased 20 · extraLarge 28 · extraLargeIncreased 32 · extraExtraLarge 48 dp.

| Компонент | Форма |
| --- | --- |
| Карточка-герой | 32 dp (`IonShapes.hero`) |
| Карточки, плитки панели | 28 dp (`IonShapes.card`) |
| Сегментированная группа | внешние углы 24 dp, внутренние стыки 4 dp, зазор 2 dp |
| Метрика, уведомление | 20 dp |
| Чипы, значения слайдеров | 8 dp |
| Кнопки | `ButtonDefaults.shapes()` — морфинг круг → скруглённый прямоугольник при нажатии |
| Бейджи иконок | `MaterialShapes` (Cookie9/6/12, Clover4Leaf, Sunny, Flower, SoftBurst, Pill, Arch…) — у каждой категории своя форма |
| Иконка включённой настройки | морфинг Cookie9Sided → Circle (`MorphShape`) |
| Быстрый переключатель | выкл. — пилюля (40 dp), вкл. — 20 dp, как плитки Quick Settings |

## 5. Компоненты

| Компонент | Реализация |
| --- | --- |
| Страница | `IonPage` — LargeFlexibleTopAppBar (вкладки) или MediumFlexibleTopAppBar (вложенные), exitUntilCollapsed, LazyColumn с полями 16 dp и шагом 12 dp |
| Поездка | компактный TopAppBar (pinned) — максимум места для показаний |
| Навигация | `ShortNavigationBar` (64 dp, заполненная иконка у выбранного, контурные у остальных); `WideNavigationRail` в альбомной |
| Группа настроек | `GroupColumn` + `SwitchRow` / `ExpandRow` / `ActionRow` / `ListRow` (min 72 dp) |
| Переключатель | `IonSwitch` — Switch с иконками ✓ / ✕ в бегунке |
| Слайдер | Slider + значение в тональной пилюле; запись только по «Применить» |
| Выбор из 2–5 вариантов | Connected button group (`ToggleButton` + `ButtonGroupDefaults.connected*Shapes`) — тема, число попыток, размер плитки |
| Уведомление | `Banner` — тональная карточка с иконкой и опциональным действием |
| Ожидание | `LoadingIndicator`, `LinearWavyProgressIndicator` |
| Главное действие страницы | Extended FAB («Добавить плитку») |
| Диалоги | AlertDialog с иконкой |
| Пустые состояния | крупный бейдж-форма 112 dp + текст + тональная кнопка |

## 6. Движение

Источник — `MotionScheme.expressive()`.

| Событие | Поведение |
| --- | --- |
| Смена вкладки | fade through (fade + scale 0.96 → 1) |
| Вглубь / назад | shared axis X (сдвиг 1/10 ширины + fade) |
| Нижняя навигация на вложенной странице | уезжает вниз (shrink + fade) |
| Top app bar | сворачивается при прокрутке, тонируется в surfaceContainer |
| Кнопки | морфинг формы при нажатии |
| Включение настройки | морфинг бейджа + смена цвета (defaultSpatial/fastEffects) |
| Быстрый переключатель | морфинг радиуса и цвета |
| Сохранённое колесо на связи | бейдж медленно вращается; при подключении — быстрее |

Анимированные значения не входят в бизнес-состояние; скорость обновляется без интерполяции.

## 7. Доступность

Цели касания ≥ 48 dp; строки 72 dp. Переключатель строки — одно действие `Role.Switch`. Выбор темы/палитры — `Role.RadioButton`. Пункты навигации — `Role.Tab` с полным названием даже при скрытых подписях (fontScale > 1.3). Заголовок в top app bar помечен `heading`. Пары плиток становятся колонкой при fontScale > 1.2. Удаление поездки из списка доступно долгим нажатием и действием TalkBack.

## 8. Файлы

| Файл | Ответственность |
| --- | --- |
| Theme.kt | Material You: dynamic / seed-схемы, `IonPalette`, `ThemeSettings`, шкалы типографики и форм, `IonType`, роли warning/success |
| ExpressiveComponents.kt | `IonPage`, `IonTopBar`, `GroupColumn`, `ListRow`, `ActionRow`, `ShapeBadge`, `Banner`, `SectionHeader` |
| Ui.kt | `SwitchRow`, `IonSwitch`, `SettingIcon` (морфинг), `ExpandRow`, `ExpandSliders`, `SettingSlider`, `MorphShape`, `SoundButtons` |
| IonApp.kt | Навигационная панель / rail, стек вложенных страниц, переходы |
| SettingsScreen.kt | Обзор настроек, «Оформление» (режим темы, цвета обоев, палитры, живой предпросмотр) |
| AppPrefs.kt | `dynamic_color`, `palette` рядом с прежним `dark` |

Проверка: `.\gradlew.bat :app:testDebugUnitTest --tests "com.freecyber.ion.M3Shots"` рендерит все экраны (светлая/тёмная, палитры, альбомная, крупный шрифт) в `app/build/outputs/m3/`.

## 9. Источники

- [Material 3 Expressive](https://m3.material.io/blog/building-with-m3-expressive)
- [Color roles и dynamic color](https://m3.material.io/styles/color/roles)
- [Navigation bar](https://m3.material.io/components/navigation-bar/guidelines), [Top app bar](https://m3.material.io/components/app-bars/guidelines)
- [Shape](https://m3.material.io/styles/shape/overview-principles), [Typography](https://m3.material.io/styles/typography/type-scale-tokens), [Motion](https://m3.material.io/styles/motion/overview/how-it-works)
- Ориентир по подходу: huecoder/MD3-Agent (flexible top app bars, сегментированные списки с бейджами-формами, dynamic color с запасной seed-схемой).
