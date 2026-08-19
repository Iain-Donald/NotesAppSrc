package com.liblens.xyznotes

import com.liblens.xyznotes.crypto.AtomicFile
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
	fun loadOrEmpty(): List<Alarm> = try { load() } catch (e: Exception) {
		Fail.warn("alarms.json unreadable; treating as empty", e)
		emptyList()
	}

	/** tmp -> fsync -> atomic rename. AlarmReceiver writes here while the
	 *  device may be dozing and may be killed mid-write; load() throws on
	 *  corrupt content, so a torn write would take out every alarm at once. */
	fun save(alarms: List<Alarm>) {
		AtomicFile.write(
			file(),
			json.encodeToString(AlarmsFile(alarms = alarms)).toByteArray(Charsets.UTF_8)
		)
		DataStore.invalidateCache()
	}

	/** Degrading load for the app's own read path.
	 *
	 *  A corrupt alarms.json must not make the app unopenable. AlarmStore sits
	 *  in the same load path as notes, so throwing here takes the whole corpus
	 *  offline for a fault in an unrelated, far less valuable file — and an app
	 *  that will not open drives the user to Clear Storage, which destroys
	 *  everything. The file is moved aside rather than overwritten so the
	 *  alarms remain recoverable by hand. */
	fun loadOrQuarantine(): List<Alarm> = try {
		load()
	} catch (e: DataStoreException) {
		quarantine()
		Fail.warn("alarms.json corrupt; quarantined, continuing with no alarms", e)
		emptyList()
	}

	private fun quarantine() {
		val f = file()
		if (!f.exists()) return
		try {
			f.renameTo(File(f.parentFile, "alarms.json.corrupt.${System.currentTimeMillis()}"))
		} catch (e: SecurityException) {
			Fail.warn("could not quarantine alarms.json", e)
		}
	}
}