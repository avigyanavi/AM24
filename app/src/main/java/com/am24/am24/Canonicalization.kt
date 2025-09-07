package com.am24.am24

import android.content.Context
import java.text.Normalizer
import java.util.Locale

enum class Gender { MALE, FEMALE, OTHER }

enum class SexualOrientation { STRAIGHT, GAY, LESBIAN, BISEXUAL, PANSEXUAL, ASEXUAL, QUEER }

fun String.toGenderCode(): Gender? = when (trim().lowercase()) {
    "male", "hombre" -> Gender.MALE
    "female", "mujer" -> Gender.FEMALE
    "other", "otro", "otra", "otros" -> Gender.OTHER
    else -> null
}

fun String.toOrientationCode(): SexualOrientation? = when (trim().lowercase()) {
    "straight", "heterosexual" -> SexualOrientation.STRAIGHT
    "gay" -> SexualOrientation.GAY
    "lesbian", "lesbiana" -> SexualOrientation.LESBIAN
    "bisexual" -> SexualOrientation.BISEXUAL
    "pansexual" -> SexualOrientation.PANSEXUAL
    "asexual" -> SexualOrientation.ASEXUAL
    "queer" -> SexualOrientation.QUEER
    else -> null
}

// ─── Country & City canonicalization ──────────────────────────────────────────

private fun String.stripAccents(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .trim()

private val countryMap: Map<String, String> by lazy {
    val languages = listOf(Locale.ENGLISH, Locale("es"))
    Locale.getISOCountries().flatMap { code ->
        val en = Locale("", code).getDisplayCountry(Locale.ENGLISH)
        languages.map { lang ->
            Locale("", code).getDisplayCountry(lang).stripAccents().lowercase(Locale.US) to en
        }
    }.toMap()
}

fun canonicalCountry(name: String?): String {
    if (name.isNullOrBlank()) return ""
    val key = name.stripAccents().lowercase(Locale.US)
    return countryMap[key] ?: name.stripAccents()
}

fun String.sameCountry(other: String?): Boolean =
    canonicalCountry(this) == canonicalCountry(other)

private val cityMap: Map<String, String> by lazy {
    val resToEn = mutableMapOf<Int, String>()
    cityNameToRes.forEach { (n, r) -> resToEn.putIfAbsent(r, n) }
    cityNameToRes.map { (name, res) ->
        name.stripAccents().lowercase(Locale.US) to resToEn[res]!!.stripAccents()
    }.toMap()
}

fun canonicalCity(name: String?): String {
    if (name.isNullOrBlank()) return ""
    val key = name.stripAccents().lowercase(Locale.US)
    return cityMap[key] ?: name.stripAccents()
}

fun String.sameCity(other: String?): Boolean =
    canonicalCity(this) == canonicalCity(other)

fun Gender.localized(context: Context): String = when (this) {
    Gender.MALE -> context.getString(R.string.gender_men)
    Gender.FEMALE -> context.getString(R.string.gender_women)
    Gender.OTHER -> context.getString(R.string.gender_other)
}

fun SexualOrientation.localized(context: Context): String = when (this) {
    SexualOrientation.STRAIGHT -> context.getString(R.string.orientation_straight)
    SexualOrientation.GAY -> context.getString(R.string.orientation_gay)
    SexualOrientation.LESBIAN -> context.getString(R.string.orientation_lesbian)
    SexualOrientation.BISEXUAL -> context.getString(R.string.orientation_bisexual)
    SexualOrientation.PANSEXUAL -> context.getString(R.string.orientation_pansexual)
    SexualOrientation.ASEXUAL -> context.getString(R.string.orientation_asexual)
    SexualOrientation.QUEER -> context.getString(R.string.orientation_queer)
}