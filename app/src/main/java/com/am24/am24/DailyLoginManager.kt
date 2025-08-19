package com.am24.am24

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/**
 * Checks daily login streak and applies Plus rewards.
 * Returns info to show on first login of the day, else null.
 */
data class DailyLoginInfo(val streak: Int, val rewardHours: Int)

suspend fun checkDailyLoginReward(): DailyLoginInfo? {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return null
    val ref = FirebaseRefs.db.getReference("users").child(uid)
    val snap = ref.get().await()
    val now = System.currentTimeMillis()
    val today = LocalDate.now().toEpochDay()
    val lastLoginDay = snap.child("lastLoginDay").getValue(Long::class.java) ?: -1L
    var streak = snap.child("loginStreak").getValue(Int::class.java) ?: 0
    val isPremium = snap.child("isPremium").getValue(Boolean::class.java) ?: false
    val rewardExpiry = snap.child("loginPlusExpiry").getValue(Long::class.java) ?: 0L

    if (rewardExpiry > 0 && rewardExpiry < now && !isPremium) {
        ref.child("loginPlusExpiry").removeValue()
        ref.child("isPlus").setValue(false)
    }

    if (lastLoginDay == today) return null

    streak = if (lastLoginDay == today - 1) streak + 1 else 1
    ref.child("lastLoginDay").setValue(today)
    ref.child("loginStreak").setValue(streak)

    var rewardHours = 0
    when (streak) {
        3 -> rewardHours = 24
        5 -> rewardHours = 48
    }

    if (rewardHours > 0) {
        val expiry = now + rewardHours * 60 * 60 * 1000
        ref.child("loginPlusExpiry").setValue(expiry)
        ref.child("isPlus").setValue(true)
    }

    return DailyLoginInfo(streak, rewardHours)
}
