package com.freecyber.ion

/** Human-readable names for the WheelLog error identifiers decoded by [V12Protocol.errors].
 * Unknown identifiers fall back to a cleaned-up form of the raw name, never to an invented meaning. */
object WheelErrors {
    private val NAMES = mapOf(
        "err_iPhaseSensorState" to "Датчик фазного тока",
        "err_iBusSensorState" to "Датчик тока шины",
        "err_motorHallState" to "Датчики Холла мотора",
        "err_batteryState" to "Батарея",
        "err_imuSensorState" to "Гироскоп (IMU)",
        "err_controllerCom1State" to "Связь с контроллером 1",
        "err_controllerCom2State" to "Связь с контроллером 2",
        "err_bleCom1State" to "Связь BLE 1",
        "err_bleCom2State" to "Связь BLE 2",
        "err_mosTempSensorState" to "Датчик температуры MOS",
        "err_motorTempSensorState" to "Датчик температуры мотора",
        "err_batteryTempSensorState" to "Датчик температуры батареи",
        "err_boardTempSensorState" to "Датчик температуры платы",
        "err_fanState" to "Вентилятор",
        "err_rtcState" to "Часы (RTC)",
        "err_externalRomState" to "Внешняя память",
        "err_vBusSensorState" to "Датчик напряжения шины",
        "err_vBatterySensorState" to "Датчик напряжения батареи",
        "err_canNotPowerOffState" to "Не удаётся выключить",
        "err_underVoltageState" to "Пониженное напряжение",
        "err_overVoltageState" to "Повышенное напряжение",
        "err_mosTempState" to "Перегрев MOS",
        "err_motorTempState" to "Перегрев мотора",
        "err_batteryTempState" to "Температура батареи",
        "err_overBoardTempState" to "Перегрев платы",
        "err_overSpeedState" to "Превышение скорости",
        "err_outputSaturationState" to "Предел мощности",
        "err_motorSpinState" to "Прокрутка мотора",
        "err_motorBlockState" to "Блокировка мотора",
        "err_postureState" to "Положение колеса",
        "err_riskBehaviourState" to "Опасное поведение",
        "err_motorNoLoadState" to "Мотор без нагрузки",
        "err_noSelfTestState" to "Нет самопроверки",
        "err_compatibilityState" to "Совместимость",
        "err_powerKeyLongPressState" to "Долгое нажатие кнопки питания",
        "err_forceDfuState" to "Принудительный режим прошивки",
        "err_deviceLockState" to "Колесо заблокировано",
        "err_cpuOverTempState" to "Перегрев процессора",
        "err_imuOverTempState" to "Перегрев гироскопа",
        "err_hwCompatibilityState" to "Совместимость железа",
        "err_fanLowSpeedState" to "Низкие обороты вентилятора",
    )
    private val LEVELED = mapOf(
        "err_overBusCurrentState" to "Перегрузка по току",
        "err_lowBatteryState" to "Низкий заряд",
    )

    fun label(id: String): String {
        NAMES[id]?.let { return it }
        val base = id.substringBeforeLast('-')
        val level = id.substringAfterLast('-', "").toIntOrNull()
        LEVELED[base]?.let { return if (level != null) "$it · уровень $level" else it }
        return id.removePrefix("err_").removeSuffix("State")
    }

    /** Errors that are informative rather than a fault worth an alert (lock state is a deliberate user action). */
    fun isBenign(id: String) = id == "err_deviceLockState"
}
