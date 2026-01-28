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
import com.google.firebase.database.ServerValue
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
                FirebaseRefs.db.reference
                    .child("users").child(uid)
                    .child("fcmTokens").child(token)
                    .setValue(true)            // Boolean flag is enough for pushSummary
            }
        }

        /** Update the user's lastActive timestamp to the server's time */
        fun updateLastActive() {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            FirebaseRefs.db.reference
                .child("users").child(uid)
                .child("lastActive")
                .setValue(ServerValue.TIMESTAMP)
        }
    }

    /** Fires if FCM rotates the token (rare). Mirrors it to RTDB. */
    override fun onNewToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseRefs.db.reference
            .child("users").child(uid)
            .child("fcmTokens").child(token)
            .setValue(true)
    }

    /** Handles data-only summary messages from pushSummary */
    override fun onMessageReceived(msg: RemoteMessage) {
        val data = msg.data
        when (data["type"]) {
            "notif_summary" -> {
                val unread = data["count"]?.toIntOrNull() ?: return
                if (unread <= 0) return

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        this, android.Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED) return

        /* Android 13+ runtime permission guard */
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
                    99,
                    NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.kupidx_logo1_round)
                        .setContentTitle(getString(R.string.app_name))
                        .setContentText(
                            resources.getQuantityString(
                                R.plurals.notification_unread_messages,
                                unread,
                                unread
                            )
                        )
                        .setNumber(unread)
                        .setAutoCancel(true)
                        .setContentIntent(pending)
                        .build()
                )
            }
            "upgrade_prompt" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        this, android.Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED) return

                val pending = PendingIntent.getActivity(
                    this, 1,
                    Intent(this, MainActivity::class.java).apply {
                        putExtra("open_upgrade_landing", true)
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_IMMUTABLE
                )
                ensureChannel()

                NotificationManagerCompat.from(this).notify(
                    98,
                    NotificationCompat.Builder(this, CHANNEL_ID)
                       .setSmallIcon(R.drawable.kupidx_logo1_round)
                        .setContentTitle(getString(R.string.app_name))
                        .setContentText(getString(R.string.notification_upgrade_prompt))
                        .setAutoCancel(true)
                        .setContentIntent(pending)
                        .build()
                )
            }
            "omegle_invite" -> {
                val chatId = data["chatId"] ?: return
                val otherUid = data["otherUid"] ?: return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        this, android.Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED) return

                val pending = PendingIntent.getActivity(
                    this, 2,
                    Intent(this, MainActivity::class.java).apply {
                        putExtra("open_omegle_chat", true)
                        putExtra("chatId", chatId)
                        putExtra("otherUid", otherUid)
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_IMMUTABLE
                )
                ensureChannel()
                NotificationManagerCompat.from(this).notify(
                    97,
                    NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.kupidx_logo1)
                        .setContentTitle(getString(R.string.app_name))
                        .setContentText(getString(R.string.notification_omegle_invite))
                        .setAutoCancel(true)
                        .setContentIntent(pending)
                        .build()
                )
            }
            "new_like" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        this, android.Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED) return

                val pending = PendingIntent.getActivity(
                    this, 3,
                    Intent(this, MainActivity::class.java).apply {
                        putExtra("open_notifications", true)
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_IMMUTABLE
                )
                ensureChannel()
                val message = data["message"] ?: getString(R.string.notification_like_received)
                NotificationManagerCompat.from(this).notify(
                    96,
                    NotificationCompat.Builder(this, CHANNEL_ID)
                       .setSmallIcon(R.drawable.kupidx_logo1_round)
                        .setContentTitle(getString(R.string.app_name))
                        .setContentText(message)
                        .setAutoCancel(true)
                        .setContentIntent(pending)
                        .build()
                )
            }
            "new_match" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        this, android.Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED) return

                val pending = PendingIntent.getActivity(
                    this, 4,
                    Intent(this, MainActivity::class.java).apply {
                        putExtra("open_notifications", true)
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_IMMUTABLE
                )
                ensureChannel()
                val message = data["message"] ?: getString(R.string.notification_like_accepted)
                NotificationManagerCompat.from(this).notify(
                    95,
                    NotificationCompat.Builder(this, CHANNEL_ID)
                       .setSmallIcon(R.drawable.kupidx_logo1_round)
                        .setContentTitle(getString(R.string.app_name))
                        .setContentText(message)
                        .setAutoCancel(true)
                        .setContentIntent(pending)
                        .build()
                )
            }
            "chat_message" -> {
                val senderId = data["senderId"] ?: return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        this, android.Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED) return

                val pending = PendingIntent.getActivity(
                    this, 5,
                    Intent(this, MainActivity::class.java).apply {
                        putExtra("open_chat_user_id", senderId)
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_IMMUTABLE
                )
                ensureChannel()
                val message = data["message"] ?: getString(R.string.notification_new_message)
                NotificationManagerCompat.from(this).notify(
                    94,
                    NotificationCompat.Builder(this, CHANNEL_ID)
                       .setSmallIcon(R.drawable.kupidx_logo1_round)
                        .setContentTitle(getString(R.string.app_name))
                        .setContentText(message)
                        .setAutoCancel(true)
                        .setContentIntent(pending)
                        .build()
                )
            }
            "post_upvote",
            "post_downvote",
            "comment_upvote",
            "comment_downvote",
            "post_comment",
            "match_post" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        this, android.Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED) return

                val pending = PendingIntent.getActivity(
                    this, 6,
                    Intent(this, MainActivity::class.java).apply {
                        putExtra("open_notifications", true)
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_IMMUTABLE
                )
                ensureChannel()
                val message = data["message"] ?: getString(R.string.notification_post_update)
                NotificationManagerCompat.from(this).notify(
                    93,
                    NotificationCompat.Builder(this, CHANNEL_ID)
                       .setSmallIcon(R.drawable.kupidx_logo1_round)
                        .setContentTitle(getString(R.string.app_name))
                        .setContentText(message)
                        .setAutoCancel(true)
                        .setContentIntent(pending)
                        .build()
                )
            }
            else -> return
        }
    }

    /** Creates the notification channel once (Android 8+) */
    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.notification_channel_name),
                        NotificationManager.IMPORTANCE_HIGH
                    )
                )
            }
        }
    }
}
