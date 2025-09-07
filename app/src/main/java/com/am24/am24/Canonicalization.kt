package com.am24.am24

import android.content.Context
import java.text.Normalizer
import java.util.Locale
import com.am24.am24.LocalizationMaps

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

val religionResToCanonical = mapOf(
    R.string.religion_other to "Other",
    R.string.religion_buddhist to "Buddhist",
    R.string.religion_christian to "Christian",
    R.string.religion_christian_catholic to "Catholic",
    R.string.religion_christian_protestant_mainline to "Mainline Protestant",
    R.string.religion_christian_evangelical to "Evangelical Protestant",
    R.string.religion_christian_orthodox to "Eastern Orthodox",
    R.string.religion_christian_latter_day_saint to "Latter-day Saint (Mormon)",
    R.string.religion_christian_jehovahs_witness to "Jehovah’s Witness",
    R.string.religion_christian_other to "Other Christian",
    R.string.religion_hindu to "Hindu",
    R.string.religion_jain to "Jain",
    R.string.religion_jewish to "Jewish",
    R.string.religion_muslim to "Muslim",
    R.string.religion_muslim_sunni to "Sunni",
    R.string.religion_muslim_shia to "Shia",
    R.string.religion_muslim_ahmadiyya to "Ahmadiyya",
    R.string.religion_muslim_sufi to "Sufi",
    R.string.religion_muslim_other to "Other Muslim",
    R.string.religion_no_religion to "No Religion / Secular",
    R.string.religion_parsi to "Zoroastrian / Parsi",
    R.string.religion_sikh to "Sikh",
    R.string.religion_indigenous_tribal to "Indigenous / Tribal",
    R.string.religion_santeria to "Santería (Afro-Cuban)",
    R.string.religion_voodou to "Vodou (Haitian)",
    R.string.religion_candomble to "Candomblé (Afro-Brazilian)",
    R.string.religion_umbanda to "Umbanda (Brazilian Syncretic)",
    R.string.religion_palo_mayombe to "Palo Mayombe",
    R.string.religion_native_traditional to "Native American Traditional",
    R.string.religion_native_church to "Native American Church (Peyotism)",
    R.string.religion_vision_quest to "Vision Quest / Ceremonial",
    R.string.religion_african_traditional to "African Traditional Religion",
    R.string.religion_obeah to "Obeah (Caribbean Folk)",
    R.string.religion_hoodoo to "Hoodoo (African-American Folk)",
    R.string.religion_rastafari to "Rastafarianism",
    R.string.religion_black_protestant to "Black Protestant Tradition (e.g. AME, COGIC)"
)

fun canonicalReligion(name: String?): String {
    if (name.isNullOrBlank()) return ""
    val res = religionNameToRes.entries.firstOrNull {
        it.key.equals(name, ignoreCase = true)
    }?.value
    return religionResToCanonical[res] ?: name
}

fun canonicalEthnicity(name: String?): String {
    if (name.isNullOrBlank()) return ""
    return when {
        name.equals("Blanco (caucásico)", true) -> "White (Caucasian)"
        name.equals("Negro / Afroamericano", true) -> "Black / African American"
        name.equals("Hispano / Latino", true) -> "Hispanic / Latino"
        name.equals("Asiático", true) -> "Asian"
        name.equals("Nativo americano", true) -> "Native American"
        name.equals("Medioriental", true) -> "Middle Eastern"
        name.equals("Isleño del Pacífico", true) -> "Pacific Islander"
        name.equals("Mixto / Otro", true) -> "Mixed / Other"
        else -> name
    }
}

fun canonicalIncome(name: String?): String {
    if (name.isNullOrBlank()) return ""
    return when {
        name.equals("Menos de $25k", true) -> "Under $25k"
        name.equals("Más de $150k", true) -> "Over $150k"
        else -> name
    }
}