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

fun canonicalGender(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val normalized = raw.stripAccents().lowercase(Locale.ROOT)
    return when (normalized) {
        // English
        "male", "m", "man", "men" -> "male"
        "female", "f", "woman", "women" -> "female"
        "other", "others", "non-binary", "nonbinary", "nb" -> "other"

        // Spanish
        "hombre", "hombres", "masculino" -> "male"
        "mujer", "mujeres", "femenino" -> "female"
        "otro", "otra", "otros", "otras", "no binario", "no-binario", "no_binario", "nobinario" -> "other"

        // Stored resource keys
        "male_option" -> "male"
        "female_option" -> "female"
        "gender_other" -> "other"
        "gender_either", "either", "both", "gender_both", "ambos" -> ""

        else -> normalized
    }
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

fun canonicalRole(name: String?): String {
    if (name.isNullOrBlank()) return ""
    val n = name.stripAccents().lowercase()
    return when {
        n.contains("switch") -> "switch"
        n.contains("vers") -> "vers"
        n.contains("open") || n.contains("abierto") || n.contains("preguntame") -> "open"
        n.contains("side") -> "side"
        n.contains("top") || n.contains("activo") -> "top"
        n.contains("bottom") || n.contains("pasivo") -> "bottom"
        n.contains("dom") -> "dom"
        n.contains("sub") || n.contains("sumiso") -> "sub"
        else -> n
    }
}

fun canonicalTribe(name: String?): String {
    if (name.isNullOrBlank()) return ""
    val n = name.stripAccents().lowercase()
    return when {
        n.contains("bear") || n.contains("oso") -> "bear"
        n.contains("cub") || n.contains("osito") -> "cub"
        n.contains("otter") || n.contains("nutria") -> "otter"
        n.contains("twink") -> "twink"
        n.contains("jock") || n.contains("deportista") -> "jock"
        n.contains("daddy") -> "daddy"
        n.contains("boy") || n.contains("chico") -> "boy"
        n.contains("chub") || n.contains("gordito") -> "chub"
        n.contains("leather") -> "leather"
        n.contains("discreet") || n.contains("discreto") -> "discreet"
        else -> n
    }
}

fun canonicalKink(name: String?): String {
    if (name.isNullOrBlank()) return ""
    val n = name.stripAccents().lowercase()
    return when {
        n.contains("bdsm") -> "bdsm"
        n.contains("rope") || n.contains("cuerda") || n.contains("bondage") -> "rope/bondage"
        n.contains("chastity") || n.contains("castidad") -> "chastity"
        n.contains("roleplay") || n.contains("juego de roles") -> "roleplay"
        n.contains("costume") || n.contains("disfraces") -> "costume"
        n.contains("blindfold") || n.contains("antifaces") || n.contains("vendas") -> "blindfolds"
        n.contains("sensation") || n.contains("sensorial") -> "sensation play"
        n.contains("spanking") || n.contains("nalgadas") || n.contains("impact") || n.contains("impacto") -> "spanking/impact"
        n.contains("edging") -> "edging"
        n.contains("temperature") || n.contains("temperatura") -> "temperature play"
        n.contains("wax") || n.contains("cera") -> "wax play"
        n.contains("voyeur") -> "voyeurism"
        n.contains("exhibition") || n.contains("exhibicion") -> "exhibitionism"
        n.contains("outdoor") || n.contains("aire libre") -> "outdoor"
        n.contains("pet") || n.contains("mascotas") -> "pet play"
        n.contains("leather") || n.contains("cuero") -> "leather"
        n.contains("latex") || n.contains("goma") -> "latex/rubber"
        n.contains("glove") || n.contains("guante") -> "gloves"
        n.contains("boot") || n.contains("bota") -> "boots"
        n.contains("feet") || n.contains("pies") -> "feet"
        n.contains("toy") || n.contains("juguet") -> "toys"
        n.contains("group") || n.contains("grupo") -> "group"
        n.contains("dirty") || n.contains("sucio") -> "dirty talk"
        n.contains("shower") || n.contains("ducha") -> "shower play"
        n.contains("power dynamics") || n.contains("dinamicas de poder") -> "power dynamics"
        n.contains("aftercare") || n.contains("cuidado posterior") -> "aftercare"
        else -> n
    }
}