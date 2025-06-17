package com.am24.am24

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.content.pm.PackageManager           // ← add
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat        // ← add


class PushService : FirebaseMessagingService() {

    companion object {
        private const val CHANNEL_ID = "am24_notif"
    }

    /** Called when the FCM registration token changes */
    override fun onNewToken(token: String) {
        // Save token under /users/{uid}/fcmTokens/{token}
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().uid ?: return
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .collection("fcmTokens").document(token)
            .set(mapOf("createdAt" to com.google.firebase.Timestamp.now()))
    }

    /** Called for every incoming data-only message */
    override fun onMessageReceived(msg: RemoteMessage) {
        val data = msg.data                         // {type: notif_summary, count: "3"}
        if (data["type"] != "notif_summary") return

        val unread = data["count"]?.toIntOrNull() ?: return
        if (unread <= 0) return

        /* ── NEW: permission guard ───────────────────────────── */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {   // API 33
            if (ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {
                // User hasn’t granted notification permission → just bail out
                return
            }
        }

        // Deep-link to your NotificationsScreen
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("open_notifications", true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this, 0, tapIntent, PendingIntent.FLAG_IMMUTABLE
        )

        ensureChannel()

        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notify)    // supply your own icon
            .setContentTitle("AM24")
            .setContentText("You have $unread unread notification${if (unread > 1) "s" else ""}")
            .setNumber(unread)                     // badge count
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
            .also { NotificationManagerCompat.from(this).notify(99, it) }
    }

    /** One-time channel setup (needed on Android 8+) */
    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return          // permission missing → don’t crash, just bail out
        }
        val mgr = getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "AM24 notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
    }
}
