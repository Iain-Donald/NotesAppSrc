package com.liblens.xyznotes

import android.content.Context
import com.liblens.xyznotes.crypto.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

object DataStore {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    // Closes Tier 0 #9 — NoteNotifyReceiver mutates from GlobalScope(IO).
    private val mutex = Mutex()

    private var dataKey: ByteArray? = null
    private var encrypted = false
    private var unlocked = false

    private var cachedAppData: AppData? = null
    private var cacheValid = false

    private const val MAP = "userData/map"

    /** First run only. Creates the key file and an empty store.
     *  Refuses to run if a key file already exists — minting a new DK over
     *  existing blobs would render them permanently undecryptable. */
    suspend fun createNew(passphrase: CharArray?): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(!KeyFile.exists()) { "Key file already exists — refusing to overwrite" }
            val dk = KeyFile.create(passphrase)
            dataKey = dk
            encrypted = passphrase != null && passphrase.isNotEmpty()
            unlocked = true
            cacheValid = false
            saveMapLocked(MapFile())
            AlarmStore.save(emptyList())
        }
    }

    fun invalidateCache() { cacheValid = false }

    fun hasContainer() = KeyFile.exists() || Migration.hasLegacyContainer()

    fun isUnlocked() = unlocked

    // ── Session ───────────────────────────────────────────────────────────

    /** Suspending — Argon2id allocates 64 MiB and blocks. Closes Tier 0 #5. */
    suspend fun unlock(passphrase: CharArray?): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (Migration.hasLegacyContainer() && !KeyFile.exists()) {
                Migration.migrateV1ToV2(passphrase) ?: return@withLock false
            }
            Transition.recover(expectedEncrypted = passphrase?.isNotEmpty() == true)

            val dk = KeyFile.unwrap(passphrase) ?: return@withLock false
            dataKey = dk
            encrypted = KeyFile.currentKdfId() == Format.KDF_ARGON2ID
            unlocked = true
            cacheValid = false
            true
        }
    }

    suspend fun unlockWithoutPassphrase(): Boolean = unlock(null)

    /*suspend fun initEmpty(): Unit = withContext(Dispatchers.IO) { iainnote old v1
        mutex.withLock {
            saveMapLocked(MapFile())
            AlarmStore.save(emptyList())
        }
    }*/

    fun lock() {
        dataKey?.let { Sodium.memzero(it) }
        dataKey = null
        unlocked = false
        cachedAppData = null
        cacheValid = false
    }

    // ── Passphrase transitions ────────────────────────────────────────────

    /** none -> passphrase. O(corpus): new DK, re-encrypt everything. */
    suspend fun setPassphrase(newPassphrase: CharArray): Unit = withContext(Dispatchers.IO) {
        mutex.withLock { retransition(newPassphrase, targetEncrypted = true) }
    }

    /** passphrase -> none. O(corpus): decrypt everything to plaintext blobs. */
    suspend fun removePassphrase(): Unit = withContext(Dispatchers.IO) {
        mutex.withLock { retransition(null, targetEncrypted = false) }
    }

    /** passphrase -> passphrase'. O(1) rewrap; payload untouched. */
    suspend fun changePassphrase(
        oldPassphrase: CharArray, newPassphrase: CharArray
    ): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock { KeyFile.rewrap(oldPassphrase, newPassphrase) }
    }

    private fun retransition(newPassphrase: CharArray?, targetEncrypted: Boolean) {
        check(unlocked) { "DataStore is locked" }
        val oldKey = dataKey
        val newKey = ByteArray(Sodium.KEY_BYTES).also { Sodium.randomBytes(it) }

        // Invariant: every replacement blob is on disk and fsynced BEFORE the
        // key file is touched. Crash before that point is a clean no-op.
        BlobStore.discardStaged()
        BlobStore.stageReseal(BlobStore.list(), oldKey, newKey, targetEncrypted)

        Transition.begin(targetEncrypted)
        KeyFile.write(newKey, newPassphrase)
        BlobStore.commitStaged()
        Transition.clear()

        oldKey?.let { Sodium.memzero(it) }
        dataKey = newKey
        encrypted = targetEncrypted
        cacheValid = false
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private fun loadMapLocked(): MapFile {
        val text = BlobStore.readText(MAP, dataKey)
        if (text.isEmpty()) return MapFile()
        return try {
            json.decodeFromString(text)
        } catch (e: Exception) {
            throw DataStoreException("map is corrupt or unreadable: ${e.message}", e)
        }
    }

    private fun saveMapLocked(map: MapFile) {
        // Currently plaintext by decision — leaks titles/section names at rest.
        // The blob header records aead=0, so flipping this to `encrypted` later
        // is a one-flag change plus a reseal, not a format migration.
        BlobStore.writeText(MAP, json.encodeToString(map), dataKey, encrypted = false)
    }

    private fun notePath(folder: String, fileName: String) =
        "userData/sections/$folder/$fileName"

    private fun noteFileName(id: String, title: String) = "$id-${sanitizeName(title)}.txt"

    /** Every mutator ends here: refresh cache and return. No container rewrite. */
    private fun reloadLocked(): AppData {
        val map = loadMapLocked()
        val sections = map.sections.map { it.toSection() }
        val notes = map.notes.map { ne ->
            val se = map.sections.find { it.id == ne.sectionId }
            ne.toNote(if (se != null) BlobStore.readText(notePath(se.folderName, ne.fileName), dataKey) else "")
        }
        return AppData(
            sections = sections, notes = notes,
            alarms = AlarmStore.load(), categories = map.categories,
            sectionSortOrder = map.sectionSortOrder, sectionSortAsc = map.sectionSortAsc
        ).also { cachedAppData = it; cacheValid = true }
    }

    suspend fun load(context: Context? = null): AppData = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (cacheValid) cachedAppData?.let { return@withLock it }
            check(unlocked) { "DataStore is locked" }
            try { reloadLocked() }
            catch (e: DataStoreException) { throw e }
            catch (e: Exception) { throw DataStoreException("Failed to load data: ${e.message}", e) }
        }
    }

    private suspend fun mutate(block: () -> Unit): AppData = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(unlocked) { "DataStore is locked" }
            try { block(); reloadLocked() }
            catch (e: DataStoreException) { throw e }
            catch (e: Exception) { throw DataStoreException("Write failed: ${e.message}", e) }
        }
    }

    // ── Sections ──────────────────────────────────────────────────────────

    suspend fun addSection(context: Context, name: String) = mutate {
        val id = generateId("s")
        val now = currentTimestamp()
        val map = loadMapLocked()
        saveMapLocked(map.copy(sections = map.sections + SectionEntry(
            id = id, name = name, folderName = "$id-${sanitizeName(name)}",
            createdAt = IdGenerator.decodeId(id.substringAfter("s")) ?: now, modifiedAt = now
        )))
    }

    suspend fun renameSection(context: Context, sectionId: String, newName: String) = mutate {
        val map = loadMapLocked()
        val old = map.sections.find { it.id == sectionId } ?: return@mutate
        val newFolder = "$sectionId-${sanitizeName(newName)}"
        if (newFolder != old.folderName) {
            val oldPrefix = "userData/sections/${old.folderName}/"
            BlobStore.list(oldPrefix).forEach { p ->
                BlobStore.move(p, "userData/sections/$newFolder/${p.removePrefix(oldPrefix)}",
                    dataKey, encrypted)
            }
        }
        saveMapLocked(map.copy(sections = map.sections.map {
            if (it.id == sectionId) it.copy(name = newName, folderName = newFolder,
                modifiedAt = currentTimestamp()) else it
        }))
    }

    suspend fun deleteSection(context: Context, sectionId: String) = mutate {
        val map = loadMapLocked()
        val entry = map.sections.find { it.id == sectionId } ?: return@mutate
        BlobStore.deletePrefix("userData/sections/${entry.folderName}/")
        val doomed = map.notes.filter { it.sectionId == sectionId }.map { it.id }
        AlarmStore.save(AlarmStore.load().filter { it.noteId !in doomed })
        saveMapLocked(map.copy(
            sections = map.sections.filter { it.id != sectionId },
            notes = map.notes.filter { it.sectionId != sectionId }
        ))
    }

    // ── Notes ─────────────────────────────────────────────────────────────

    suspend fun addNote(context: Context, note: Note) = mutate {
        val map = loadMapLocked()
        val se = map.sections.find { it.id == note.sectionId } ?: return@mutate
        val fileName = noteFileName(note.id, note.title)
        val now = currentTimestamp()
        BlobStore.writeText(notePath(se.folderName, fileName), note.content, dataKey, encrypted)
        saveMapLocked(map.copy(notes = map.notes + NoteEntry(
            id = note.id, sectionId = note.sectionId, title = note.title, fileName = fileName,
            createdAt = IdGenerator.decodeId(note.id.substringAfter("n")) ?: now,
            modifiedAt = now, notifyEnabled = note.notifyEnabled, pinned = note.pinned
        )))
    }

    /** renameNote collapsed into this — it was a strict subset. */
    suspend fun updateNote(context: Context, note: Note) = mutate {
        val map = loadMapLocked()
        val ne = map.notes.find { it.id == note.id } ?: return@mutate
        val se = map.sections.find { it.id == ne.sectionId } ?: return@mutate

        val newFileName = if (sanitizeName(note.title) != sanitizeName(ne.title)) {
            val n = noteFileName(note.id, note.title)
            BlobStore.delete(notePath(se.folderName, ne.fileName))
            n
        } else ne.fileName

        BlobStore.writeText(notePath(se.folderName, newFileName), note.content, dataKey, encrypted)
        saveMapLocked(map.copy(notes = map.notes.map {
            if (it.id == note.id) it.copy(
                title = note.title, fileName = newFileName, modifiedAt = currentTimestamp(),
                notifyEnabled = note.notifyEnabled, pinned = note.pinned
            ) else it
        }))
    }

    /*suspend fun renameNote(context: Context, noteId: String, newTitle: String): AppData {
        val data = load(context)
        val note = data.notes.find { it.id == noteId } ?: return data
        return updateNote(context, note.copy(title = newTitle))
    }*/

    suspend fun setNoteNotify(context: Context, noteId: String, enabled: Boolean) = mutate {
        val map = loadMapLocked()
        saveMapLocked(map.copy(notes = map.notes.map {
            if (it.id == noteId) it.copy(notifyEnabled = enabled, modifiedAt = currentTimestamp()) else it
        }))
    }

    suspend fun deleteNote(context: Context, noteId: String) = mutate {
        val map = loadMapLocked()
        val ne = map.notes.find { it.id == noteId } ?: return@mutate
        val se = map.sections.find { it.id == ne.sectionId }
        if (se != null) BlobStore.delete(notePath(se.folderName, ne.fileName))
        AlarmStore.save(AlarmStore.load().filter { it.noteId != noteId })
        saveMapLocked(map.copy(notes = map.notes.filter { it.id != noteId }))
    }

    suspend fun toggleNotePin(context: Context, noteId: String) = mutate {
        val map = loadMapLocked()
        saveMapLocked(map.copy(notes = map.notes.map {
            if (it.id == noteId) it.copy(pinned = !it.pinned) else it
        }))
    }

    suspend fun toggleSectionPin(context: Context, sectionId: String) = mutate {
        val map = loadMapLocked()
        saveMapLocked(map.copy(sections = map.sections.map {
            if (it.id == sectionId) it.copy(pinned = !it.pinned) else it
        }))
    }

    // ── Sort ──────────────────────────────────────────────────────────────

    suspend fun updateSectionSort(context: Context, sectionId: String, sortOrder: String, sortAsc: Boolean) = mutate {
        val map = loadMapLocked()
        saveMapLocked(map.copy(sections = map.sections.map {
            if (it.id == sectionId) it.copy(sortOrder = sortOrder, sortAsc = sortAsc) else it
        }))
    }

    suspend fun updateAppSectionSort(context: Context, sortOrder: String, sortAsc: Boolean) = mutate {
        saveMapLocked(loadMapLocked().copy(sectionSortOrder = sortOrder, sectionSortAsc = sortAsc))
    }

    // ── Alarms ────────────────────────────────────────────────────────────

    suspend fun addAlarm(context: Context, alarm: Alarm) = mutate {
        AlarmStore.save(AlarmStore.load() + alarm.copy(modifiedAt = currentTimestamp()))
        val map = loadMapLocked()
        saveMapLocked(map.copy(notes = map.notes.map {
            if (it.id == alarm.noteId) it.copy(alarmIds = it.alarmIds + alarm.id) else it
        }))
    }

    suspend fun updateAlarm(context: Context, alarm: Alarm) = mutate {
        AlarmStore.save(AlarmStore.load().map {
            if (it.id == alarm.id) it.copy(
                noteId = alarm.noteId, sectionId = alarm.sectionId, name = alarm.name,
                timeHour = alarm.timeHour, timeMinute = alarm.timeMinute,
                displayText = alarm.displayText, isActive = alarm.isActive,
                repeatDays = alarm.repeatDays, modifiedAt = currentTimestamp()
            ) else it
        })
    }

    suspend fun deleteAlarm(context: Context, alarmId: String) = mutate {
        AlarmStore.save(AlarmStore.load().filter { it.id != alarmId })
        val map = loadMapLocked()
        saveMapLocked(map.copy(notes = map.notes.map {
            it.copy(alarmIds = it.alarmIds.filter { aid -> aid != alarmId })
        }))
    }

    // ── Categories ────────────────────────────────────────────────────────

    suspend fun addCategory(context: Context, name: String, colorId: Int) = mutate {
        val map = loadMapLocked()
        saveMapLocked(map.copy(categories = map.categories + Category(generateId("c"), name, colorId)))
    }

    suspend fun updateCategory(context: Context, categoryId: String, name: String, colorId: Int) = mutate {
        val map = loadMapLocked()
        saveMapLocked(map.copy(categories = map.categories.map {
            if (it.id == categoryId) it.copy(name = name, colorId = colorId) else it
        }))
    }

    suspend fun deleteCategory(context: Context, categoryId: String) = mutate {
        val map = loadMapLocked()
        saveMapLocked(map.copy(
            categories = map.categories.filter { it.id != categoryId },
            sections = map.sections.map {
                if (it.categoryId == categoryId) it.copy(categoryId = "", modifiedAt = currentTimestamp()) else it
            }
        ))
    }

    suspend fun setSectionCategory(context: Context, sectionId: String, categoryId: String) = mutate {
        val map = loadMapLocked()
        saveMapLocked(map.copy(sections = map.sections.map {
            if (it.id == sectionId) it.copy(categoryId = categoryId, modifiedAt = currentTimestamp()) else it
        }))
    }

    // ── Export ────────────────────────────────────────────────────────────

    suspend fun export(encryptedExport: Boolean) = Exporter.export(dataKey, encrypted, encryptedExport)
}