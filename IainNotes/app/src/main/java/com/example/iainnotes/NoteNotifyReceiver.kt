package com.example.iainnotes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class NoteNotifyReceiver : BroadcastReceiver() {
	@OptIn(DelicateCoroutinesApi::class)
	override fun onReceive(context: Context, intent: Intent) {
		if (intent.action != NoteNotificationManager.ACTION_CANCEL) return
		val noteId = intent.getStringExtra(NoteNotificationManager.EXTRA_NOTE_ID) ?: return

		// Cancel the notification immediately — no coroutine needed
		NoteNotificationManager.cancel(context, noteId)

		// goAsync holds the BroadcastReceiver wake lock open until finish() is called,
		// preventing the OS from killing the process before the update completes.
		val pendingResult = goAsync()
		GlobalScope.launch(Dispatchers.IO) {
			try {
				val data = DataStore.load(context)
				val note = data.notes.find { it.id == noteId } ?: return@launch
				DataStore.updateNote(context, note.copy(notifyEnabled = false))
			} catch (_: Exception) {
				// Silent — receiver has no UI to show errors on
			} finally {
				pendingResult.finish()
			}
		}
	}
}