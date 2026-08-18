package com.liblens.xyznotes

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.icu.util.Calendar

object AlarmScheduler {

    fun canScheduleExact(context: Context): Boolean =
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()

    /** Exact when permitted, inexact otherwise. An alarm that fires late beats
     *  one that never fires, so a missing permission degrades rather than refuses. */
    @SuppressLint("ScheduleExactAlarm")
    private fun armExact(am: AlarmManager, triggerAt: Long, pi: PendingIntent, exact: Boolean) {
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
    }

    // ── Request codes ─────────────────────────────────────────────────────
    // Centralised so schedule() and cancel() cannot drift apart. Every code
    // minted here must have a matching cancel path.

    private fun codeOneShot(alarmId: String) = alarmId.hashCode()
    private fun codeDay(alarmId: String, day: String) = "${alarmId}_$day".hashCode()
    private fun codeSnooze(alarmId: String) = "${alarmId}_snooze".hashCode()

    private fun pendingIntentFor(context: Context, code: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Returns true when the alarm was armed exactly, false when it degraded
     *  to an inexact window. Callers may surface that; they may not ignore it
     *  on the assumption it is always true. */
    fun schedule(context: Context, alarm: Alarm): Boolean {
        if (!alarm.isActive) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val exact = canScheduleExact(context)

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("alarmId", alarm.id)
            putExtra("alarmName", alarm.name)
            putExtra("displayText", alarm.displayText)
        }

        if (alarm.repeatDays.isEmpty()) {
            // One-time alarm
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, alarm.timeHour)
                set(Calendar.MINUTE, alarm.timeMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(Calendar.getInstance())) add(Calendar.DAY_OF_YEAR, 1)
            }
            armExact(am, calendar.timeInMillis, pendingIntentFor(context, codeOneShot(alarm.id), intent), exact)
        } else {
            // Repeating — schedule one PendingIntent per selected day
            alarm.repeatDays.forEach { day ->
                val dayOfWeek = dayOfWeekOf(day) ?: return@forEach
                val calendar = nextOccurrence(dayOfWeek, alarm.timeHour, alarm.timeMinute)
                armExact(am, calendar.timeInMillis, pendingIntentFor(context, codeDay(alarm.id, day), intent), exact)
            }
        }
        return exact
    }

    private fun dayOfWeekOf(day: String): Int? = when (day) {
        "MON" -> Calendar.MONDAY
        "TUE" -> Calendar.TUESDAY
        "WED" -> Calendar.WEDNESDAY
        "THU" -> Calendar.THURSDAY
        "FRI" -> Calendar.FRIDAY
        "SAT" -> Calendar.SATURDAY
        "SUN" -> Calendar.SUNDAY
        else -> null
    }

    /** Next future instant matching a weekday and wall-clock time.
     *
     *  Computed by day-offset arithmetic rather than by setting DAY_OF_WEEK.
     *  Calendar.set(DAY_OF_WEEK, ...) resolves against the locale's first day
     *  of the week, so on a Monday-first locale a SUN target can land in the
     *  previous week; the old "+1 week if in the past" correction then lands a
     *  week early or late depending on the day. Offset arithmetic has no
     *  locale dependency. */
    private fun nextOccurrence(targetDayOfWeek: Int, hour: Int, minute: Int): Calendar {
        val now = Calendar.getInstance()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        var delta = (targetDayOfWeek - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
        if (delta == 0 && !cal.after(now)) delta = 7
        cal.add(Calendar.DAY_OF_YEAR, delta)
        return cal
    }

    fun cancel(context: Context, alarm: Alarm) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)

        if (alarm.repeatDays.isEmpty()) {
            am.cancel(pendingIntentFor(context, codeOneShot(alarm.id), intent))
        } else {
            alarm.repeatDays.forEach { day ->
                am.cancel(pendingIntentFor(context, codeDay(alarm.id, day), intent))
            }
        }
        // A snooze may be armed independently of either shape above. Cancelling
        // the alarm without cancelling its snooze leaves a live wakeup for an
        // alarm the user believes is gone.
        cancelSnooze(context, alarm.id)
        AlarmNotifier.cancel(context, alarm.id)
    }

    fun cancelSnooze(context: Context, alarmId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(
            pendingIntentFor(
                context,
                codeSnooze(alarmId),
                Intent(context, AlarmReceiver::class.java)
            )
        )
    }

    /** Schedules a single firing at an exact epoch time, ignoring repeatDays.
     *  Used for snooze, where hour/minute alone loses the date. */
    fun scheduleAt(context: Context, alarm: Alarm, triggerAtMillis: Long): Boolean {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val exact = canScheduleExact(context)
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("alarmId", alarm.id)
            putExtra("alarmName", alarm.name)
            putExtra("displayText", alarm.displayText)
            putExtra("isSnooze", true)          // receiver must not re-arm or deactivate on this
        }
        armExact(am, triggerAtMillis, pendingIntentFor(context, codeSnooze(alarm.id), intent), exact)
        return exact
    }
}