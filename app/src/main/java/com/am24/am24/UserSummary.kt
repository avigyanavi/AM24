package com.am24.am24

import com.google.firebase.database.IgnoreExtraProperties
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@IgnoreExtraProperties
data class UserSummary(
    val userId: String = "",
    val username: String = "",
    val name: String = "",
    val dob: String = "",
    val roles: List<String> = emptyList(),
    val jobRole: String = "",
    val profilepicUrl: String? = null,
    val profilepicThumbnailUrl: String? = null,
    val lastActive: Long = 0L,
    val likesReceivedCount: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    val displayName: String
        get() = name.ifBlank { username }

    val age: Int
        get() {
            if (dob.isBlank()) return 0
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val birthDate = try {
                sdf.parse(dob)
            } catch (e: Exception) {
                null
            } ?: return 0

            val today = Calendar.getInstance()
            val birth = Calendar.getInstance().apply { time = birthDate }
            var years = today.get(Calendar.YEAR) - birth.get(Calendar.YEAR)
            if (today.get(Calendar.DAY_OF_YEAR) < birth.get(Calendar.DAY_OF_YEAR)) {
                years--
            }
            return years
        }
}