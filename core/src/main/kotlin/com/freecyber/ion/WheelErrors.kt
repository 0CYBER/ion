package com.freecyber.ion

/** Human-readable names (Russian / English, see [tr]) for the WheelLog error identifiers decoded by [V12Protocol.errors].
 * Unknown identifiers fall back to a cleaned-up form of the raw name, never to an invented meaning. */
object WheelErrors {
    private val NAMES = mapOf(
        "err_iPhaseSensorState" to ("Датчик фазного тока" to "Phase current sensor"),
        "err_iBusSensorState" to ("Датчик тока шины" to "Bus current sensor"),
        "err_motorHallState" to ("Датчики Холла мотора" to "Motor Hall sensors"),
        "err_batteryState" to ("Батарея" to "Battery"),
        "err_imuSensorState" to ("Гироскоп (IMU)" to "Gyroscope (IMU)"),
        "err_controllerCom1State" to ("Связь с контроллером 1" to "Controller link 1"),
        "err_controllerCom2State" to ("Связь с контроллером 2" to "Controller link 2"),
        "err_bleCom1State" to ("Связь BLE 1" to "BLE link 1"),
        "err_bleCom2State" to ("Связь BLE 2" to "BLE link 2"),
        "err_mosTempSensorState" to ("Датчик температуры MOS" to "MOS temperature sensor"),
        "err_motorTempSensorState" to ("Датчик температуры мотора" to "Motor temperature sensor"),
        "err_batteryTempSensorState" to ("Датчик температуры батареи" to "Battery temperature sensor"),
        "err_boardTempSensorState" to ("Датчик температуры платы" to "Board temperature sensor"),
        "err_fanState" to ("Вентилятор" to "Fan"),
        "err_rtcState" to ("Часы (RTC)" to "Clock (RTC)"),
        "err_externalRomState" to ("Внешняя память" to "External memory"),
        "err_vBusSensorState" to ("Датчик напряжения шины" to "Bus voltage sensor"),
        "err_vBatterySensorState" to ("Датчик напряжения батареи" to "Battery voltage sensor"),
        "err_canNotPowerOffState" to ("Не удаётся выключить" to "Cannot power off"),
        "err_underVoltageState" to ("Пониженное напряжение" to "Undervoltage"),
        "err_overVoltageState" to ("Повышенное напряжение" to "Overvoltage"),
        "err_mosTempState" to ("Перегрев MOS" to "MOS overheat"),
        "err_motorTempState" to ("Перегрев мотора" to "Motor overheat"),
        "err_batteryTempState" to ("Температура батареи" to "Battery temperature"),
        "err_overBoardTempState" to ("Перегрев платы" to "Board overheat"),
        "err_overSpeedState" to ("Превышение скорости" to "Overspeed"),
        "err_outputSaturationState" to ("Предел мощности" to "Power limit"),
        "err_motorSpinState" to ("Прокрутка мотора" to "Motor spin"),
        "err_motorBlockState" to ("Блокировка мотора" to "Motor blocked"),
        "err_postureState" to ("Положение колеса" to "Wheel posture"),
        "err_riskBehaviourState" to ("Опасное поведение" to "Risky behaviour"),
        "err_motorNoLoadState" to ("Мотор без нагрузки" to "Motor without load"),
        "err_noSelfTestState" to ("Нет самопроверки" to "No self-test"),
        "err_compatibilityState" to ("Совместимость" to "Compatibility"),
        "err_powerKeyLongPressState" to ("Долгое нажатие кнопки питания" to "Power button long press"),
        "err_forceDfuState" to ("Принудительный режим прошивки" to "Forced firmware mode"),
        "err_deviceLockState" to ("Колесо заблокировано" to "Wheel locked"),
        "err_cpuOverTempState" to ("Перегрев процессора" to "CPU overheat"),
        "err_imuOverTempState" to ("Перегрев гироскопа" to "Gyroscope overheat"),
        "err_hwCompatibilityState" to ("Совместимость железа" to "Hardware compatibility"),
        "err_fanLowSpeedState" to ("Низкие обороты вентилятора" to "Fan speed low"),
    )
    private val LEVELED = mapOf(
        "err_overBusCurrentState" to ("Перегрузка по току" to "Overcurrent"),
        "err_lowBatteryState" to ("Низкий заряд" to "Low battery"),
    )

    private fun Pair<String, String>.text() = tr(first, second)

    fun label(id: String): String {
        NAMES[id]?.let { return it.text() }
        val base = id.substringBeforeLast('-')
        val level = id.substringAfterLast('-', "").toIntOrNull()
        LEVELED[base]?.let { return if (level != null) "${it.text()} · ${tr("уровень","level")} $level" else it.text() }
        return id.removePrefix("err_").removeSuffix("State")
    }

    /** Errors that are informative rather than a fault worth an alert (lock state is a deliberate user action). */
    fun isBenign(id: String) = id == "err_deviceLockState"
}
