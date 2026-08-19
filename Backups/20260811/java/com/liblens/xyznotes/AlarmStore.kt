package com.liblens.xyznotes

import android.os.Environment
import kotlinx.serialization.json.Json
import java.io.File

/** Single source of truth for alarms.json. Lives OUTSIDE the encrypted container
 *  so BootReceiver/AlarmReceiver can read it while the app is locked. */
object AlarmStore {

	private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

	fun file(): File = File(BlobStore.root(), "userData/alarms.json")
		.also { it.parentFile?.mkdirs() }

	/** Returns empty list on missing file. Throws on corrupt content. */
	fun load(): List<Alarm> {
		val f = file()
		if (!f.exists()) return emptyList()
		return try {
			val text = f.readText()
			if (text.trimStart().startsWith("[")) {
				json.decodeFromString<List<Alarm>>(text).also { save(it) }
			} else {
				json.decodeFromString<AlarmsFile>(text).alarms
			}
		} catch (e: Exception) {
			throw DataStoreException("alarms.json is corrupt or unreadable: ${e.message}", e)
		}
	}

	/** Never throws — receivers have no UI to report on. */
	fun loadOrEmpty(): List<Alarm> = try { load() } catch (_: Exception) { emptyList() }

	fun save(alarms: List<Alarm>) {
		file().writeText(json.encodeToString(AlarmsFile(alarms = alarms)))
		DataStore.invalidateCache()
	}
}