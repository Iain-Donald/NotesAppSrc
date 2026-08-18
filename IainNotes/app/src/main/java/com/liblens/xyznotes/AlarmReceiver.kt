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
        val alarm = entries.find { it.id == alarmId }
        if (alarm == null) {
            // Alarm was deleted after the PendingIntent was armed. Nothing to
            // show, but the stale snooze intent must not survive to fire again.
            AlarmScheduler.cancelSnooze(context, alarmId)
            Fail.quiet("Alarm $alarmId fired but no longer exists; dropped")
            return
        }

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
        // the full-screen intent is honoured — in Doze the device may sleep in
        // between and the alarm silently never happens. Hold one across the
        // handoff. Not released here: the Activity needs it during startup. The
        // timeout is the release mechanism, and it is short enough to be harmless.
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "xyznotes:alarm")
            .acquire(20_000L)

        // NOT context.startActivity(). Background activity starts from a
        // receiver are dropped silently on API 29+. See AlarmNotifier.
        AlarmNotifier.fire(context, alarm)
    }
}