package com.liblens.xyznotes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        BlobStore.init(context)
        val alarmId = intent.getStringExtra("alarmId") ?: return
        val isSnooze = intent.getBooleanExtra("isSnooze", false)

        // onReceive's own implicit wakelock covers this file read.
        val entries = AlarmStore.loadOrEmpty()
        val alarm = entries.find { it.id == alarmId } ?: return

        // Re-arm BEFORE showing UI: setExactAndAllowWhileIdle is one-shot, so a
        // repeating alarm that isn't re-armed here never fires again.
        if (!isSnooze) {
            if (alarm.repeatDays.isNotEmpty()) {
                // schedule() recomputes each day's next occurrence; the day that
                // just fired is now in the past, so it rolls forward a week.
                AlarmScheduler.schedule(context, alarm)
            } else {
                // One-shot has been consumed — reflect that in stored state.
                AlarmStore.save(entries.map {
                    if (it.id == alarmId) it.copy(isActive = false) else it
                })
            }
        }

        // The implicit wakelock ends when onReceive returns, which can be before
        // AlarmAlertActivity is drawn — in Doze the device may sleep in between
        // and the alarm silently never happens. Hold one across the handoff.
        // Not released here: the Activity needs it during startup. The timeout
        // is the release mechanism, and it is short enough to be harmless.
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "xyznotes:alarm")
            .acquire(20_000L)

        context.startActivity(
            Intent(context, AlarmAlertActivity::class.java).apply {
                putExtra("alarmId", alarm.id)
                putExtra("alarmName", alarm.name)
                putExtra("displayText", alarm.displayText)
                putExtra("repeatDays", alarm.repeatDays.toTypedArray())
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )
    }
}