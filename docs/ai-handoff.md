# AI handoff — ветка `ai-experimental` (структурированно)

Актуальное состояние; хронология — `docs/ai-rolling-handoff.md`.

## Состояние

| Область | Статус | Где |
|---|---|---|
| Фоновая связь (FGS connectedDevice) | готово, не проверено на устройстве | `WheelService.kt`, `IonApplication.kt` |
| Авто-переподключение | готово, unit-тест backoff | `WheelMonitor.kt` (`ReconnectPolicy`) |
| Уведомления (live / alerts / link) | готово | `IonNotifications.kt`, `WheelErrors.kt` |
| Оповещения с гистерезисом | готово, 8 тестов | `AlertEngine.kt` |
| Статистика поездки | готово, 3 теста | `TripRecorder.kt` |
| Виджет рабочего стола (Glance) | готово | `IonWidget.kt`, `WidgetSnapshot.kt`, `res/xml/ion_widget_info.xml` |
| Модульный экран поездки | готово, 5 тестов раскладки | `Dashboard.kt`, `RideScreen.kt`, `FeatureScreens.kt` |
| Настройки приложения | готово | `AppPrefs.kt`, `FeatureScreens.kt` (`BackgroundScreen`) |

## Сборка

```
./gradlew testDebugUnitTest assembleDebug compileDebugAndroidTestKotlin
```
На машине с 4 ГБ RAM нужен `~/.gradle/gradle.properties` с `org.gradle.jvmargs=-Xmx1800m` и `kotlin.compiler.execution.strategy=in-process`.

## Риски / что проверить на телефоне

1. Android 14+: FGS `connectedDevice` требует выданного `BLUETOOTH_CONNECT` до старта сервиса — сервис стартует только после подключения из UI.
2. Кнопка «Фара» в уведомлении и виджете шлёт запись только при `safeWrite` (стоянка, проверенное HT).
3. Авто-переподключение не срабатывает после ручного «Отключить» и после «Забыть».
4. Уведомление о потере связи — канал `ride_alerts` (высокий приоритет).

## Следующие идеи (бэклог)

CI (GitHub Actions), история поездок + экспорт CSV/GPX, графики, Quick Settings tile, EN-локализация, вибро/голос оповещения, ландшафт/планшет, варианты виджета, lint-фиксы, разбиение на пакеты.
