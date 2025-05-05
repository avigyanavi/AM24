package com.am24.am24.util

import android.content.Context
import androidx.core.os.LocaleListCompat
import androidx.appcompat.app.AppCompatDelegate

object LocaleUtils {
    /** Change the app-wide locale and remember it in SharedPreferences. */
    fun setAppLocale(context: Context, langTag: String) {
        val locales = LocaleListCompat.forLanguageTags(langTag)
        AppCompatDelegate.setApplicationLocales(locales)

        context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            .edit().putString("app_lang", langTag).apply()
    }

    /** Read the last chosen locale (or system default). */
    fun getSavedLang(context: Context): String =
        context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            .getString("app_lang",
                context.resources.configuration.locales[0].language) ?: "en"
}
