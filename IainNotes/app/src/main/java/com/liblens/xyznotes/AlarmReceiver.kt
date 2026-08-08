package com.liblens.xyznotes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra("alarmId") ?: return
        val isSnooze = intent.getBooleanExtra("isSnooze", false)

        val entries = AlarmStore.loadOrEmpty()
        val entry = entries.find { it.id == alarmId } ?: return

        val alarm = AlarmStore.loadOrEmpty().find { it.id == alarmId } ?: return

        // Re-arm BEFORE showing UI: setExactAndAllowWhileIdle is one-shot, so a
        // repeating alarm that isn't re-armed here never fires again.
        if (!isSnooze) {
            if (alarm.repeatDays.isNotEmpty()) {
                // schedule() recomputes each day's next occurrence; the day that just
                // fired is now in the past, so it rolls forward a week. Correct.
                AlarmScheduler.schedule(context, alarm)
            } else {
                // One-shot has now been consumed — reflect that in stored state.
                val all = AlarmStore.loadOrEmpty()
                AlarmStore.save(all.map { if (it.id == alarmId) it.copy(isActive = false) else it })
            }
        }

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