package com.am24.am24

import android.content.Context

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