package com.am24.am24

private val firebaseKeyInvalidCharsRegex = Regex("[./#$\\[\\]]")

/** Returns true if [value] contains any Firebase reserved key characters (./#$[]). */
fun hasFirebaseKeyInvalidChars(value: String): Boolean =
    firebaseKeyInvalidCharsRegex.containsMatchIn(value)

/** Returns true if [value] is non-blank and does not contain Firebase reserved key characters. */
fun isFirebaseKeyValid(value: String): Boolean =
    value.isNotBlank() && !hasFirebaseKeyInvalidChars(value)

/** Returns [value] if it is a valid Firebase key, otherwise null. */
fun sanitizeFirebaseKeyOrNull(value: String): String? =
    if (isFirebaseKeyValid(value)) value else null