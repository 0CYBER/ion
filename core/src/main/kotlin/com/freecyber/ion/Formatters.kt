package com.freecyber.ion

import java.util.Locale

/** Locale-aware presentation formatting; protocol values stay numeric. */
fun Double.fmt(digits: Int)=String.format(Locale.forLanguageTag("ru"),"%.${digits}f",this)
