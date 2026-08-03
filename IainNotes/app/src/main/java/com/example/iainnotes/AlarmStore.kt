package com.example.iainnotes

import android.os.Environment
import kotlinx.serialization.json.Json
import java.io.File

/** Single source of truth for alarms.json. Lives OUTSIDE the encrypted container
 *  so BootReceiver/AlarmReceiver can read it while the app is locked. */
object AlarmStore {

	private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

	fun file(): File = File(
		Environment.getExternalStorageDirectory(),
		"IainNotes/userData/alarms.json"
	).also { it.parentFile?.mkdirs() }

	/** Returns empty list on missing file. Throws on corrupt content. */
	fun load(): List<AlarmEntry> {
		val f = file()
		if (!f.exists()) return emptyList()
		val text = f.readText()
		return if (text.trimStart().startsWith("[")) {
			// Legacy plain-array format — migrate on read.
			json.decodeFromString<List<AlarmEntry>>(text).also { save(it) }
		} else {
			json.decodeFromString<AlarmsFile>(text).alarms
		}
	}

	/** Never throws — receivers have no UI to report on. */
	fun loadOrEmpty(): List<AlarmEntry> = try { load() } catch (_: Exception) { emptyList() }

	fun save(alarms: List<AlarmEntry>) {
		file().writeText(json.encodeToString(AlarmsFile(alarms = alarms)))
		DataStore.invalidateCache()
	}
}