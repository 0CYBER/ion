package com.freecyber.ion

/** Announces each whole [step] of battery lost, e.g. 80 → 70 → 60. Pure; one announcement per crossing. */
class BatteryStepper(private val step: Int = 10) {
    private var lastBucket: Int? = null
    fun reset() { lastBucket = null }
    /** Returns the level to announce, or null. Charging re-bases so the next drop is measured from the new level. */
    fun next(battery: Double?, charging: Boolean): Int? {
        val b = battery?.takeIf { it.isFinite() && it in 0.0..100.0 } ?: return null
        val bucket = (b / step).toInt()
        val last = lastBucket
        // Only charging lets the base climb, so a reading wobbling across a boundary is announced once.
        lastBucket = if (last == null || charging) bucket else minOf(last, bucket)
        return if (last != null && !charging && bucket < last) bucket * step else null
    }
}
