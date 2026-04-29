package com.example.iainnotes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Environment
import kotlinx.serialization.json.Json
import java.io.File

class BootReceiver : BroadcastReceiver() {
    private val json = Json { ignoreUnknownKeys = true }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val alarmsFile = File(
            Environment.getExternalStorageDirectory(),
            "IainNotes/userData/alarms.json"
        )
        if (!alarmsFile.exists()) return

        val text = try { alarmsFile.readText() } catch (_: Exception) { return }

        val alarms: List<AlarmEntry> = try {
            if (text.trimStart().startsWith("[")) {
                json.decodeFromString<List<AlarmEntry>>(text)
            } else {
                json.decodeFromString<AlarmsFile>(text).alarms
            }
        } catch (_: Exception) {
            return
        }

        alarms.filter { it.isActive }.forEach { entry ->
            AlarmScheduler.schedule(context, Alarm(
                id = entry.id,
                noteId = entry.noteId,
                sectionId = entry.sectionId,
                name = entry.name,
                timeHour = entry.timeHour,
                timeMinute = entry.timeMinute,
                displayText = entry.displayText,
                isActive = entry.isActive,
                repeatDays = entry.repeatDays
            ))
        }
    }
}