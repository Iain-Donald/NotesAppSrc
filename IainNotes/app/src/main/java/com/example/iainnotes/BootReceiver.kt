package com.example.iainnotes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        AlarmStore.loadOrEmpty()
            .filter { it.isActive }
            .forEach { AlarmScheduler.schedule(context, it.toAlarm()) }
    }
}