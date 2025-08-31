package com.am24.am24

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import android.content.Context

/**
 * Checks daily login streak and applies Plus rewards.
 * Returns info to show on first login of the day, else null.
 */
data class DailyLoginInfo(val streak: Int, val rewardHours: Int)

suspend fun checkDailyLoginReward(context: Context): DailyLoginInfo? {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return null
    val ref = FirebaseRefs.db.getReference("users").child(uid)
    val snap = ref.get().await()
    val now = System.currentTimeMillis()
    val today = LocalDate.now().toEpochDay()
    val lastLoginDay = snap.child("lastLoginDay").getValue(Long::class.java) ?: -1L
    var streak = snap.child("loginStreak").getValue(Int::class.java) ?: 0
    val isPremium = snap.child("isPremium").getValue(Boolean::class.java) ?: false
    val isPlus = snap.child("isPlus").getValue(Boolean::class.java) ?: false
    val rewardExpiry = snap.child("loginPlusExpiry").getValue(Long::class.java) ?: 0L

    if (rewardExpiry > 0 && rewardExpiry < now && !isPremium) {
        ref.child("loginPlusExpiry").removeValue()
        ref.child("isPlus").setValue(false)
    }

    if (isPlus || isPremium) return null

    if (lastLoginDay == today) return null

    streak = if (lastLoginDay == today - 1) streak + 1 else 1
    ref.child("lastLoginDay").setValue(today)
    ref.child("loginStreak").setValue(streak)

    var rewardHours = 0
    when {
        streak == 3 -> rewardHours = 24
        streak >= 5 -> rewardHours = 24
    }

    if (rewardHours > 0) {
        val expiry = now + rewardHours * 60 * 60 * 1000
        ref.child("loginPlusExpiry").setValue(expiry)
        ref.child("isPlus").setValue(true)


        val notificationsRef: DatabaseReference = FirebaseRefs.db.getReference("notifications")
        val notificationId = notificationsRef.child(uid).push().key
        notificationId?.let { id ->
            val message = context.getString(R.string.notification_streak_plus_message)
            val notification = Notification(
                id = id,
                type = "streak_plus",
                senderId = "Kupidx",
                senderUsername = "Kupidx",
                message = message,
                timestamp = now,
                isRead = "false"
            )
            notificationsRef.child(uid).child(id).setValue(notification).await()
        }
    }

    return DailyLoginInfo(streak, rewardHours)
}
