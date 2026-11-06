package com.freecyber.ion

/** Two-language text for labels produced by pure Kotlin (diagnostic fields, command descriptions, error names),
 * where Android resources are not available. Follows [IonFormat.locale] set by the app; Russian is the default. */
internal fun tr(ru: String, en: String): String = if (IonFormat.locale.language == "en") en else ru
