package com.example.iainnotes

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

object NoteNotificationManager {

	const val CHANNEL_ID = "iain_notes_note_channel"
	const val ACTION_CANCEL = "com.example.iainnotes.ACTION_CANCEL_NOTE_NOTIFY"
	const val EXTRA_NOTE_ID = "noteId"

	fun createChannel(context: Context) {
		val channel = NotificationChannel(
			CHANNEL_ID,
			"Note reminders",
			NotificationManager.IMPORTANCE_HIGH   // ← was IMPORTANCE_LOW
		).apply {
			description = "Persistent notifications pinned to notes"
			setShowBadge(true)
			enableLights(true)
		}
		context.getSystemService(NotificationManager::class.java)
			.createNotificationChannel(channel)
	}
	// In NoteNotificationManager — single source of truth for notification ID
	fun notificationId(noteId: String) = noteId.hashCode() and 0x7FFFFFFF

	fun notify(context: Context, note: Note) {
		// Tap opens NoteDetailActivity
		val openIntent = PendingIntent.getActivity(
			context,
			note.id.hashCode(),
			Intent(context, NoteDetailActivity::class.java).apply {
				putExtra("noteId", note.id)
				addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
			},
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
		)

		// Dismiss action cancels the notification and toggles notifyEnabled off
		val cancelIntent = PendingIntent.getBroadcast(
			context,
			notificationId(note.id) + 1,
			Intent(context, NoteNotifyReceiver::class.java).apply {
				action = ACTION_CANCEL
				putExtra(EXTRA_NOTE_ID, note.id)
			},
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
		)

		val notification = NotificationCompat.Builder(context, CHANNEL_ID)
			.setContentTitle(note.title)
			.setContentText(note.content.take(80).ifBlank { "Tap to open note" })
			.setSmallIcon(R.drawable.ic_plus)
			.setContentIntent(openIntent)
			.setOngoing(true)
			.setPriority(NotificationCompat.PRIORITY_HIGH)   // ← add this
			.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
			.addAction(
				R.drawable.rounded_lock_24,
				"Dismiss",
				cancelIntent
			)
			.build()

		context.getSystemService(NotificationManager::class.java)
			.notify(notificationId(note.id), notification)
	}


	fun cancel(context: Context, noteId: String) {
		context.getSystemService(NotificationManager::class.java)
			.cancel(notificationId(noteId))
	}

	fun syncAll(context: Context, notes: List<Note>) {
		notes.forEach { note ->
			if (note.notifyEnabled) notify(context, note)
			else cancel(context, note.id)
		}
	}
}