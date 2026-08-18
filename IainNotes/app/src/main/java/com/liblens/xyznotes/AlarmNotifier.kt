package com.liblens.xyznotes

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat

/** Alarm delivery.
 *
 *  A BroadcastReceiver cannot start an Activity: background activity starts
 *  have been blocked since API 29, and context.startActivity() from onReceive
 *  is dropped silently — no exception, no log, no alarm. The supported
 *  mechanism is a high-importance notification carrying a full-screen intent.
 *
 *  Behaviour that follows from that, and which is not negotiable:
 *   - Screen off / device locked -> the system launches AlarmAlertActivity
 *     immediately, exactly like the old direct start did.
 *   - Screen on and in use -> the system shows a heads-up notification
 *     instead. The user is already looking at the device; hijacking the
 *     foreground would be worse, and this is the only behaviour Play allows.
 *
 *  The channel therefore carries the alarm sound itself, so the heads-up case
 *  is still audible. AlarmAlertActivity cancels the notification before
 *  starting its own looping playback, which stops the channel sound and
 *  prevents the two overlapping. */
object AlarmNotifier {

    const val CHANNEL_ID = "xyznotes_alarm_channel_v1"

    fun createChannel(context: Context) {
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Alarms",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Note alarms and reminders"
            setBypassDnd(true)
            enableVibration(true)
            setSound(
                sound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun notificationId(alarmId: String) = alarmId.hashCode() and 0x7FFFFFFF

    fun fire(context: Context, alarm: Alarm) {
        val alertIntent = Intent(context, AlarmAlertActivity::class.java).apply {
            putExtra("alarmId", alarm.id)
            putExtra("alarmName", alarm.name)
            putExtra("displayText", alarm.displayText)
            putExtra("repeatDays", alarm.repeatDays.toTypedArray())
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pi = PendingIntent.getActivity(
            context,
            notificationId(alarm.id),
            alertIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.outline_alarm_24)
            .setContentTitle(alarm.name.ifEmpty { "Alarm" })
            .setContentText(alarm.displayText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .setAutoCancel(true)
            .setOngoing(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(notificationId(alarm.id), notification)
    }

    fun cancel(context: Context, alarmId: String) {
        context.getSystemService(NotificationManager::class.java)
            .cancel(notificationId(alarmId))
    }
}