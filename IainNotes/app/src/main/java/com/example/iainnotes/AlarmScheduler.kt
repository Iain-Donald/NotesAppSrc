package com.example.iainnotes

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.icu.util.Calendar

object AlarmScheduler {

    private fun canScheduleExact(context: Context): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    @SuppressLint("ScheduleExactAlarm")
    fun schedule(context: Context, alarm: Alarm) {
        if (!alarm.isActive) return
        if (!canScheduleExact(context)) return   // silently skip if no permission

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

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
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
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
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        }
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

    /*fun rescheduleAll(context: Context) {
        if (!canScheduleExact(context)) return
        val data = DataStore.load(context)
        data.alarms.filter { it.isActive }.forEach { schedule(context, it) }
    }*/
}