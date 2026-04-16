package com.example.iainnotes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Environment
import kotlinx.serialization.json.Json
import java.io.File

class AlarmReceiver : BroadcastReceiver() {
    private val json = Json { ignoreUnknownKeys = true }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra("alarmId") ?: return

        // Read alarms.json directly — no decryption needed
        val alarmsFile = File(
            Environment.getExternalStorageDirectory(),
            "IainNotes/userData/alarms.json"
        )
        if (!alarmsFile.exists()) return

        val alarms: List<AlarmEntry> = try {
            json.decodeFromString(alarmsFile.readText())
        } catch (e: Exception) {
            return
        }

        val alarm = alarms.find { it.id == alarmId } ?: return

        val notificationIntent = Intent(context, AlarmAlertActivity::class.java).apply {
            putExtra("alarmId", alarm.id)
            putExtra("alarmName", alarm.name)
            putExtra("displayText", alarm.displayText)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(notificationIntent)
    }
}