package com.example.iainnotes

import android.content.Context
import com.example.iainnotes.crypto.*
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

    class DataStoreException(message: String, cause: Throwable? = null) : Exception(message, cause)

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
        val categories = map.categories.map { Category(it.id, it.name, it.colorId) }
        val sections = map.sections.map {
            Section(
                id = it.id, name = it.name,
                createdAt = it.createdAt.ifEmpty { IdGenerator.decodeId(it.id.substringAfter("s")) ?: "" },
                modifiedAt = it.modifiedAt, sortOrder = it.sortOrder, sortAsc = it.sortAsc,
                pinned = it.pinned, categoryId = it.categoryId
            )
        }
        val notes = map.notes.map { ne ->
            val se = map.sections.find { it.id == ne.sectionId }
            val content = if (se != null) BlobStore.readText(notePath(se.folderName, ne.fileName), dataKey) else ""
            Note(
                id = ne.id, sectionId = ne.sectionId, title = ne.title, content = content,
                createdAt = ne.createdAt.ifEmpty { IdGenerator.decodeId(ne.id.substringAfter("n")) ?: "" },
                modifiedAt = ne.modifiedAt, notifyEnabled = ne.notifyEnabled, pinned = ne.pinned
            )
        }
        val alarms = AlarmStore.load().map { it.toAlarm() }

        return AppData(
            sections = sections, notes = notes, alarms = alarms, categories = categories,
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
        val now = currentTimestamp()
        AlarmStore.save(AlarmStore.load() + AlarmEntry(
            id = alarm.id, noteId = alarm.noteId, sectionId = alarm.sectionId, name = alarm.name,
            timeHour = alarm.timeHour, timeMinute = alarm.timeMinute, displayText = alarm.displayText,
            isActive = alarm.isActive, repeatDays = alarm.repeatDays,
            createdAt = IdGenerator.decodeId(alarm.id.substringAfter("t")) ?: now, modifiedAt = now
        ))
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
        saveMapLocked(map.copy(categories = map.categories + CategoryEntry(generateId("c"), name, colorId)))
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
/*package com.example.iainnotes

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object DataStore {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private var passphrase: CharArray = charArrayOf()
    private var files: MutableMap<String, ByteArray> = mutableMapOf()
    private var cachedAppData: AppData? = null
    private var cacheValid = false

    private fun containerFileEnc() = File(
        Environment.getExternalStorageDirectory(), "IainNotes/IainNotes.tar.enc"
    )

    private fun containerFilePlain() = File(
        Environment.getExternalStorageDirectory(), "IainNotes/IainNotes.tar"
    )

    private fun containerFile() = if (noPassphraseMode) containerFilePlain() else containerFileEnc()

    fun hasContainer() = containerFileEnc().exists() || containerFilePlain().exists()

    // ── Session ───────────────────────────────────────────────────────────

    suspend fun initEmpty(context: Context) {
        saveMap(MapFile())
        AlarmStore.save(emptyList())
        commit()
    }

    private var noPassphraseMode = false

    fun unlockWithoutPassphrase(context: Context) {
        noPassphraseMode = true
        cachedAppData = null
        cacheValid = false
        val container = containerFile()
        files = if (container.exists()) {
            try {
                TarManager.unpack(container.readBytes()).toMutableMap()
            } catch (e: Exception) {
                mutableMapOf()
            }
        } else {
            mutableMapOf()
        }
    }

    fun unlock(passphrase: CharArray): Boolean {
        return try {
            val container = containerFile()
            files = if (container.exists()) {
                TarManager.unpack(CryptoManager.decrypt(container.readBytes(), passphrase))
                    .toMutableMap()
            } else {
                mutableMapOf()
            }
            this.passphrase = passphrase
            noPassphraseMode = false
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isUnlocked() = noPassphraseMode || passphrase.isNotEmpty()

    fun lock() {
        if (noPassphraseMode) return
        passphrase.fill('\u0000')
        passphrase = charArrayOf()
        files.clear()
        cachedAppData = null
        cacheValid = false
    }

    fun invalidateCache() { cacheValid = false }

    suspend fun setPassphrase(newPassphrase: CharArray) {
        passphrase = newPassphrase
        noPassphraseMode = false
        commit()  // writes .tar.enc
        // Clean up the old plain .tar if it exists
        containerFilePlain().delete()
    }

    /*suspend fun removePassphrase() {
        noPassphraseMode = true
        passphrase.fill('\u0000')
        passphrase = charArrayOf()
        commit()  // writes plain .tar
        // Clean up the old .enc file if it exists
        containerFileEnc().delete()
    }*/

    suspend fun removePassphrase() {
        val old = passphrase
        noPassphraseMode = true
        commit()
        old.fill('\u0000')
        passphrase = charArrayOf()
        containerFileEnc().delete()
    }

    suspend fun changePassphrase(
        oldPassphrase: CharArray,
        newPassphrase: CharArray
    ): Boolean = withContext(Dispatchers.IO) {
        // Verify old passphrase by attempting to decrypt the container
        return@withContext try {
            val container = containerFile()
            if (container.exists()) {
                // This will throw if the old passphrase is wrong
                CryptoManager.decrypt(container.readBytes(), oldPassphrase)
            }
            // Old passphrase verified — re-encrypt with new one
            passphrase.fill('\u0000')
            passphrase = newPassphrase
            commit()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ── Commit (encrypt and write) ────────────────────────────────────────

    private suspend fun commit() = withContext(Dispatchers.IO) {
        check(isUnlocked()) { "DataStore is locked — cannot commit" }
        val container = containerFile()
        val bak = File(container.parent, "${container.name}.bak")
        val packed = TarManager.pack(files)
        val bytes = if (noPassphraseMode) {
            packed
        } else {
            check(passphrase.isNotEmpty()) { "Passphrase is empty" }
            CryptoManager.encrypt(packed, passphrase)
        }

        bak.writeBytes(bytes)

        // Verify written bytes match what we intended to write
        val written = bak.readBytes()
        check(written.contentEquals(bytes)) {
            bak.delete()
            "Backup verification failed — disk write was corrupted, aborting"
        }

        Files.move(bak.toPath(), container.toPath(), StandardCopyOption.ATOMIC_MOVE)
        cacheValid = false
    }

    // ── Internal file helpers ─────────────────────────────────────────────

    private fun readText(path: String) =
        files[path]?.toString(Charsets.UTF_8) ?: ""

    private fun writeText(path: String, content: String) {
        files[path] = content.toByteArray(Charsets.UTF_8)
    }

    private fun deleteFile(path: String) { files.remove(path) }

    private fun deletePrefix(prefix: String) {
        files.keys.filter { it.startsWith(prefix) }.forEach { files.remove(it) }
    }

    // ── Map & Alarms ──────────────────────────────────────────────────────

    private fun loadMap(): MapFile {
        val text = readText("userData/map.json")
        if (text.isEmpty()) return MapFile()
        return try {
            json.decodeFromString(text)
        } catch (e: Exception) {
            throw DataStoreException("map.json is corrupt or unreadable: ${e.message}", e)
        }
    }

    private fun saveMap(map: MapFile) {
        writeText("userData/map.json", json.encodeToString(map))
    }

    // ── Load (assembles AppData from in-memory files) ─────────────────────

    class DataStoreException(message: String, cause: Throwable? = null) :
        Exception(message, cause)

    suspend fun toggleNotePin(context: Context, noteId: String): AppData {
        val map = loadMap()
        saveMap(map.copy(
            notes = map.notes.map {
                if (it.id == noteId) it.copy(pinned = !it.pinned) else it
            }
        ))
        commit()
        return load(context)
    }

    suspend fun toggleSectionPin(context: Context, sectionId: String): AppData {
        val map = loadMap()
        saveMap(map.copy(
            sections = map.sections.map {
                if (it.id == sectionId) it.copy(pinned = !it.pinned) else it
            }
        ))
        commit()
        return load(context)
    }

    suspend fun load(context: Context): AppData = withContext(Dispatchers.IO) {
        if (cacheValid && cachedAppData != null) {
            return@withContext cachedAppData!!
        }
        try {
            val map = loadMap()
            val alarmEntries = AlarmStore.load()

            val categories = map.categories.map {
                Category(id = it.id, name = it.name, colorId = it.colorId)
            }

            val sections = map.sections.map {
                Section(
                    id = it.id,
                    name = it.name,
                    createdAt = it.createdAt,
                    modifiedAt = it.modifiedAt,
                    sortOrder = it.sortOrder,
                    sortAsc = it.sortAsc,
                    pinned = it.pinned,
                    categoryId = it.categoryId
                )
            }

            val notes = map.notes.map { noteEntry ->
                val sectionEntry = map.sections.find { it.id == noteEntry.sectionId }
                val content = if (sectionEntry != null) {
                    readText("userData/sections/${sectionEntry.folderName}/${noteEntry.fileName}")
                } else ""
                Note(
                    id = noteEntry.id,
                    sectionId = noteEntry.sectionId,
                    title = noteEntry.title,
                    content = content,
                    createdAt = noteEntry.createdAt,
                    modifiedAt = noteEntry.modifiedAt,
                    notifyEnabled = noteEntry.notifyEnabled,
                    pinned = noteEntry.pinned
                )
            }

            val alarms = alarmEntries.map { a ->
                Alarm(
                    id = a.id,
                    noteId = a.noteId,
                    sectionId = a.sectionId,
                    name = a.name,
                    timeHour = a.timeHour,
                    timeMinute = a.timeMinute,
                    displayText = a.displayText,
                    isActive = a.isActive,
                    repeatDays = a.repeatDays,
                    createdAt = a.createdAt,
                    modifiedAt = a.modifiedAt
                )
            }

            AppData(sections = sections, notes = notes, alarms = alarms, categories = categories, sectionSortOrder = map.sectionSortOrder, sectionSortAsc = map.sectionSortAsc).also {
                cachedAppData = it
                cacheValid = true
            }
        } catch (e: Exception) {
            throw DataStoreException("Failed to load data: ${e.message}", e)
        }
    }

    // ── Sections ──────────────────────────────────────────────────────────

    suspend fun addSection(context: Context, name: String): AppData {
        val id = generateId("s")
        val folderName = "$id-${sanitizeName(name)}"
        val now = currentTimestamp()
        val entry = SectionEntry(
            id = id,
            name = name,
            folderName = folderName,
            createdAt = IdGenerator.decodeId(id.substringAfter("s")) ?: now,
            modifiedAt = now
        )
        val map = loadMap()
        saveMap(map.copy(sections = map.sections + entry))
        commit()
        return load(context)
    }

    suspend fun renameSection(context: Context, sectionId: String, newName: String): AppData {
        val map = loadMap()
        val old = map.sections.find { it.id == sectionId } ?: return load(context)
        val newFolderName = "$sectionId-${sanitizeName(newName)}"
        val oldPrefix = "userData/sections/${old.folderName}/"
        val newPrefix = "userData/sections/$newFolderName/"
        // Move all files under old prefix to new prefix
        files.keys.filter { it.startsWith(oldPrefix) }.forEach { oldPath ->
            val newPath = newPrefix + oldPath.removePrefix(oldPrefix)
            files[newPath] = files.remove(oldPath)!!
        }
        saveMap(map.copy(
            sections = map.sections.map {
                if (it.id == sectionId) it.copy(
                    name = newName,
                    folderName = newFolderName,
                    modifiedAt = currentTimestamp()
                )
                else it
            }
        ))
        commit()
        return load(context)
    }

    suspend fun deleteSection(context: Context, sectionId: String): AppData {
        val map = loadMap()
        val entry = map.sections.find { it.id == sectionId } ?: return load(context)
        deletePrefix("userData/sections/${entry.folderName}/")
        val deletedNoteIds = map.notes.filter { it.sectionId == sectionId }.map { it.id }
        AlarmStore.save(AlarmStore.load().filter { it.noteId !in deletedNoteIds })
        saveMap(map.copy(
            sections = map.sections.filter { it.id != sectionId },
            notes = map.notes.filter { it.sectionId != sectionId }
        ))
        commit()
        return load(context)
    }

    // ── Notes ─────────────────────────────────────────────────────────────

    suspend fun addNote(context: Context, note: Note): AppData {
        val map = loadMap()
        val sectionEntry = map.sections.find { it.id == note.sectionId } ?: return load(context)
        //val fileName = "${note.id}-${sanitizeName(note.title)}.txt"
        val fileName = noteFileName(note.id, note.title)
        val path = "userData/sections/${sectionEntry.folderName}/$fileName"
        val now = currentTimestamp()
        val entry = NoteEntry(
            id = note.id,
            sectionId = note.sectionId,
            title = note.title,
            fileName = fileName,
            createdAt = IdGenerator.decodeId(note.id.substringAfter("n")) ?: now,
            modifiedAt = now,
            notifyEnabled = note.notifyEnabled
        )
        writeText(path, note.content)
        saveMap(map.copy(notes = map.notes + entry))
        commit()
        return load(context)
    }

    private fun noteFileName(id: String, title: String) = "$id-${sanitizeName(title)}.txt"

    suspend fun renameNote(context: Context, noteId: String, newTitle: String): AppData {
        val map = loadMap()
        val noteEntry = map.notes.find { it.id == noteId } ?: return load(context)
        val sectionEntry = map.sections.find { it.id == noteEntry.sectionId } ?: return load(context)
        val newFileName = if (sanitizeName(newTitle) != sanitizeName(noteEntry.title)) {
            val name = noteFileName(noteId, newTitle)
            val oldPath = "userData/sections/${sectionEntry.folderName}/${noteEntry.fileName}"
            val newPath = "userData/sections/${sectionEntry.folderName}/$name"
            files[newPath] = files.remove(oldPath) ?: byteArrayOf()
            name
        } else noteEntry.fileName
        saveMap(map.copy(
            notes = map.notes.map {
                if (it.id == noteId) it.copy(
                    title = newTitle,
                    fileName = newFileName,
                    modifiedAt = currentTimestamp()
                )
                else it
            }
        ))
        commit()
        return load(context)
    }

    suspend fun updateNote(context: Context, note: Note): AppData {
        val map = loadMap()
        val noteEntry = map.notes.find { it.id == note.id } ?: return load(context)
        val sectionEntry = map.sections.find { it.id == note.sectionId } ?: return load(context)
        val newFileName = if (sanitizeName(note.title) != sanitizeName(noteEntry.title)) {
            //val newName = "${note.id}-${sanitizeName(note.title)}.txt"
            val newName = noteFileName(note.id, note.title)
            val oldPath = "userData/sections/${sectionEntry.folderName}/${noteEntry.fileName}"
            val newPath = "userData/sections/${sectionEntry.folderName}/$newName"
            files[newPath] = files.remove(oldPath) ?: byteArrayOf()
            newName
        } else noteEntry.fileName
        val newName = noteFileName(note.id, note.title)
        val updatedEntry = noteEntry.copy(
            title = note.title,
            fileName = newFileName,
            modifiedAt = currentTimestamp(),
            notifyEnabled = note.notifyEnabled,
            pinned = note.pinned
        )
        writeText("userData/sections/${sectionEntry.folderName}/${updatedEntry.fileName}", note.content)
        saveMap(map.copy(notes = map.notes.map { if (it.id == note.id) updatedEntry else it }))
        commit()
        return load(context)
    }

    suspend fun setNoteNotify(context: Context, noteId: String, enabled: Boolean): AppData {
        val map = loadMap()
        saveMap(map.copy(
            notes = map.notes.map {
                if (it.id == noteId) it.copy(notifyEnabled = enabled, modifiedAt = currentTimestamp())
                else it
            }
        ))
        commit()
        return load(context)
    }

    suspend fun deleteNote(context: Context, noteId: String): AppData {
        val map = loadMap()
        val noteEntry = map.notes.find { it.id == noteId } ?: return load(context)
        val sectionEntry = map.sections.find { it.id == noteEntry.sectionId } ?: return load(context)
        deleteFile("userData/sections/${sectionEntry.folderName}/${noteEntry.fileName}")
        AlarmStore.save(AlarmStore.load().filter { it.noteId != noteId })
        saveMap(map.copy(
            notes = map.notes.filter { it.id != noteId },
        ))
        commit()
        return load(context)
    }

    // Sort order

    suspend fun updateSectionSort(
        context: Context,
        sectionId: String,
        sortOrder: String,
        sortAsc: Boolean
    ): AppData {
        val map = loadMap()
        saveMap(map.copy(
            sections = map.sections.map {
                if (it.id == sectionId) it.copy(
                    sortOrder = sortOrder,
                    sortAsc = sortAsc
                ) else it
            }
        ))
        commit()
        return load(context)
    }

    suspend fun updateAppSectionSort(
        context: Context,
        sortOrder: String,
        sortAsc: Boolean
    ): AppData {
        val map = loadMap()
        saveMap(map.copy(
            sectionSortOrder = sortOrder,
            sectionSortAsc = sortAsc
        ))
        commit()
        return load(context)
    }

    // ── Alarms ────────────────────────────────────────────────────────────

    suspend fun addAlarm(context: Context, alarm: Alarm): AppData {
        val now = currentTimestamp()
        val entry = AlarmEntry(
            id = alarm.id,
            noteId = alarm.noteId,
            sectionId = alarm.sectionId,
            name = alarm.name,
            timeHour = alarm.timeHour,
            timeMinute = alarm.timeMinute,
            displayText = alarm.displayText,
            isActive = alarm.isActive,
            repeatDays = alarm.repeatDays,
            createdAt = IdGenerator.decodeId(alarm.id.substringAfter("t")) ?: now,
            modifiedAt = now
        )
        AlarmStore.save(AlarmStore.load() + entry)
        val map = loadMap()
        saveMap(map.copy(
            notes = map.notes.map {
                if (it.id == alarm.noteId) it.copy(alarmIds = it.alarmIds + alarm.id) else it
            }
        ))
        commit()
        return load(context)
    }

    suspend fun updateAlarm(context: Context, alarm: Alarm): AppData {
        AlarmStore.save(AlarmStore.load().map {
            if (it.id == alarm.id) AlarmEntry(
                id = alarm.id,
                noteId = alarm.noteId,
                sectionId = alarm.sectionId,
                name = alarm.name,
                timeHour = alarm.timeHour,
                timeMinute = alarm.timeMinute,
                displayText = alarm.displayText,
                isActive = alarm.isActive,
                repeatDays = alarm.repeatDays,
                createdAt = it.createdAt,        // preserve original
                modifiedAt = currentTimestamp()  // update
            ) else it
        })
        commit()
        return load(context)
    }

    suspend fun deleteAlarm(context: Context, alarmId: String): AppData {
        AlarmStore.save(AlarmStore.load().filter { it.id != alarmId })
        val map = loadMap()
        saveMap(map.copy(
            notes = map.notes.map {
                it.copy(alarmIds = it.alarmIds.filter { aid -> aid != alarmId })
            }
        ))
        commit()
        return load(context)
    }

    suspend fun export(encrypted: Boolean): File = withContext(Dispatchers.IO) {
        val fileName = if (encrypted) "XyzNotes-export.tar.enc" else "XyzNotes-export.tar"
        val output = File(Environment.getExternalStorageDirectory(), "IainNotes/$fileName")

        val packed = TarManager.pack(files)
        val bytes = if (encrypted) {
            check(passphrase.isNotEmpty()) { "Cannot export encrypted — no passphrase set" }
            CryptoManager.encrypt(packed, passphrase)
        } else {
            packed
        }

        output.writeBytes(bytes)

        // Verify written bytes
        val written = output.readBytes()
        check(written.contentEquals(bytes)) {
            output.delete()
            "Export verification failed — file may be corrupt"
        }

        output
    }

    suspend fun addCategory(context: Context, name: String, colorId: Int): AppData {
        val map = loadMap()
        val entry = CategoryEntry(id = generateId("c"), name = name, colorId = colorId)
        saveMap(map.copy(categories = map.categories + entry))
        commit()
        return load(context)
    }

    suspend fun updateCategory(context: Context, categoryId: String, name: String, colorId: Int): AppData {
        val map = loadMap()
        saveMap(map.copy(
            categories = map.categories.map {
                if (it.id == categoryId) it.copy(name = name, colorId = colorId) else it
            }
        ))
        commit()
        return load(context)
    }

    suspend fun deleteCategory(context: Context, categoryId: String): AppData {
        val map = loadMap()
        saveMap(map.copy(
            categories = map.categories.filter { it.id != categoryId },
            sections = map.sections.map {
                if (it.categoryId == categoryId) it.copy(
                    categoryId = "",
                    modifiedAt = currentTimestamp()
                ) else it
            }
        ))
        commit()
        return load(context)
    }

    suspend fun setSectionCategory(context: Context, sectionId: String, categoryId: String): AppData {
        val map = loadMap()
        saveMap(map.copy(
            sections = map.sections.map {
                if (it.id == sectionId) it.copy(
                    categoryId = categoryId,
                    modifiedAt = currentTimestamp()
                ) else it
            }
        ))
        commit()
        return load(context)
    }
}*/