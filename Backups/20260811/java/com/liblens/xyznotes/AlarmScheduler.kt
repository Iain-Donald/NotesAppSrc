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
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                alarm.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            armExact(am, calendar.timeInMillis, pendingIntent, exact)
        } else {
            // Repeating — schedule one PendingIntent per selected day
            alarm.repeatDays.forEach { day ->
                val dayOfWeek = when (day) {
                    "MON" -> Calendar.MONDAY
                    "TUE" -> Calendar.TUESDAY
                    "WED" -> Calendar.WEDNESDAY
                    "THU" -> Calendar.THURSDAY
                    "FRI" -> Calendar.FRIDAY
                    "SAT" -> Calendar.SATURDAY
                    "SUN" -> Calendar.SUNDAY
                    else -> return@forEach
                }
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_WEEK, dayOfWeek)
                    set(Calendar.HOUR_OF_DAY, alarm.timeHour)
                    set(Calendar.MINUTE, alarm.timeMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (before(Calendar.getInstance())) add(Calendar.WEEK_OF_YEAR, 1)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    "${alarm.id}_$day".hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                armExact(am, calendar.timeInMillis, pendingIntent, exact)
            }
        }
        return true
    }

    fun cancel(context: Context, alarm: Alarm) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)

        if (alarm.repeatDays.isEmpty()) {
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                alarm.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        } else {
            alarm.repeatDays.forEach { day ->
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    "${alarm.id}_$day".hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(pendingIntent)
            }
        }
    }

    /** Schedules a single firing at an exact epoch time, ignoring repeatDays.
     *  Used for snooze, where hour/minute alone loses the date. */
    @SuppressLint("ScheduleExactAlarm")
    fun scheduleAt(context: Context, alarm: Alarm, triggerAtMillis: Long): Boolean {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("alarmId", alarm.id)
            putExtra("alarmName", alarm.name)
            putExtra("displayText", alarm.displayText)
            putExtra("isSnooze", true)          // receiver must not re-arm or deactivate on this
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            "${alarm.id}_snooze".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
        )
        armExact(am, triggerAtMillis, pendingIntent, canScheduleExact(context))
        return canScheduleExact(context)
    }
}