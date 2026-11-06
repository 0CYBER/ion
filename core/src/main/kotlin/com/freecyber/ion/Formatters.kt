package com.freecyber.ion

import java.util.Locale

/** Number formatting follows the UI language (set by the app from its resources): "23,4" in Russian, "23.4" in English.
 * Protocol values stay numeric; exports use [Locale.US] explicitly. */
object IonFormat {
    @Volatile var locale: Locale = Locale.forLanguageTag("ru")
}

fun Double.fmt(digits: Int)=String.format(IonFormat.locale,"%.${digits}f",this)
