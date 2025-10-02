package com.am24.am24

import java.util.Locale

/** Returns the default in-app language code based on the device locale. */
fun defaultLanguageCode(): String {
    val deviceLanguage = Locale.getDefault().language.lowercase(Locale.ROOT)
    return when (deviceLanguage) {
        "es" -> "es"
        "th" -> "th"
        else -> "en"
    }
}