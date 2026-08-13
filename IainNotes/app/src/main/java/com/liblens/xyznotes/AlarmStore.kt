package com.liblens.xyznotes

import android.os.Environment
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException
import java.io.File
import java.io.IOException

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
			json.decodeFromString<AlarmsFile>(f.readText()).alarms
		} catch (e: SerializationException) {
			throw DataStoreException("alarms.json is corrupt: ${e.message}", e)
		} catch (e: IOException) {
			throw DataStoreException("alarms.json is unreadable: ${e.message}", e)
		}
	}

	/** Never throws — receivers have no UI to report on. */
	fun loadOrEmpty(): List<Alarm> = try { load() } catch (_: Exception) { emptyList() }

	fun save(alarms: List<Alarm>) {
		file().writeText(json.encodeToString(AlarmsFile(alarms = alarms)))
		DataStore.invalidateCache()
	}
}