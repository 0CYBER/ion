package com.freecyber.ion

/** How long ago the last wheel packet arrived, in the coarsest unit that still reads naturally ("12 s", "3 min", "2 h"). */
object ReadingAge {
    sealed interface Value { val amount: Long }
    data class Seconds(override val amount: Long) : Value
    data class Minutes(override val amount: Long) : Value
    data class Hours(override val amount: Long) : Value

    /** Null when there is no packet yet or the clock went backwards (a reboot between snapshots). */
    fun of(ageMs: Long?): Value? = when {
        ageMs == null || ageMs < 0 -> null
        ageMs < 60_000 -> Seconds(ageMs / 1000)
        ageMs < 3_600_000 -> Minutes(ageMs / 60_000)
        else -> Hours(ageMs / 3_600_000)
    }
}
