package com.am24.am24

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase        // ✅ RTDB (matches Cloud Function)
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class PushService : FirebaseMessagingService() {

    companion object {
        private const val CHANNEL_ID = "am24_notif"

        /** Call right after login (or on app start) so the current token is always in RTDB */
        fun uploadCurrentToken() {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@addOnSuccessListener
                FirebaseDatabase.getInstance().reference
                    .child("users").child(uid)
                    .child("fcmTokens").child(token)
                    .setValue(true)            // Boolean flag is enough for pushSummary
            }
        }
    }

    /** Fires if FCM rotates the token (rare). Mirrors it to RTDB. */
    override fun onNewToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseDatabase.getInstance().reference
            .child("users").child(uid)
            .child("fcmTokens").child(token)
            .setValue(true)
    }

    /** Handles data-only summary messages from pushSummary */
    override fun onMessageReceived(msg: RemoteMessage) {
        val data = msg.data                            // e.g. {type=notif_summary, count=3}
        if (data["type"] != "notif_summary") return

        val unread = data["count"]?.toIntOrNull() ?: return
        if (unread <= 0) return

        /* Android 13+ runtime permission guard */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        /* Tap action → open notifications screen */
        val pending = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                putExtra("open_notifications", true)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        ensureChannel()

        NotificationManagerCompat.from(this).notify(
            99,    // static ID → replaces the previous summary
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notify)     // ensure this icon exists
                .setContentTitle(getString(R.string.app_name))   // ← change was here
                .setContentText(
                    "You have $unread unread notification" +
                            if (unread > 1) "s" else ""
                )
                .setNumber(unread)                      // badge count on some launchers
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
        )
    }

    /** Creates the notification channel once (Android 8+) */
    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "AM24 notifications",
                        NotificationManager.IMPORTANCE_HIGH
                    )
                )
            }
        }
    }
}
