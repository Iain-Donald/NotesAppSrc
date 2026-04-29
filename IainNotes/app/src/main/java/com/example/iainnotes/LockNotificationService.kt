package com.example.iainnotes

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class LockNotificationService : Service() {

    companion object {
        const val CHANNEL_ID = "iain_notes_lock_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_DISMISS = "com.example.iainnotes.ACTION_DISMISS_LOCK"

        fun start(context: Context) {
            val intent = Intent(context, LockNotificationService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LockNotificationService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISMISS) {
            // User dismissed from notification — lock and close app
            DataStore.lock()
            stopSelf()
            // Close all activities
            val closeIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("lock_and_close", true)
            }
            startActivity(closeIntent)
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        // Tap notification to open app
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dismiss action from notification
        val dismissIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, LockNotificationService::class.java).apply {
                action = ACTION_DISMISS
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("IainNotes is open")
            .setContentText("Tap to return. Dismiss to lock and close.")
            .setSmallIcon(R.drawable.ic_plus) // replace with a proper icon
            .setContentIntent(openIntent)
            .setOngoing(true)               // prevents swipe-to-dismiss
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // shows on lock screen
            .addAction(
                R.drawable.rounded_lock_24,
                "Lock & close",
                dismissIntent
            )
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
			CHANNEL_ID,
			"IainNotes session",
			NotificationManager.IMPORTANCE_LOW  // silent, no sound
		).apply {
            description = "Shows while IainNotes is unlocked"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}