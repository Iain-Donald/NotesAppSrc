package com.example.iainnotes

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

    private fun containerFile() =
        if (noPassphraseMode) containerFilePlain() else containerFileEnc()

    fun hasContainer() = containerFileEnc().exists() || containerFilePlain().exists()

    // ── Session ───────────────────────────────────────────────────────────

    suspend fun initEmpty(context: Context) {
        saveMap(MapFile())
        saveAlarmsDirect(emptyList())
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

    suspend fun setPassphrase(newPassphrase: CharArray) {
        passphrase = newPassphrase
        noPassphraseMode = false
        commit()  // writes .tar.enc
        // Clean up the old plain .tar if it exists
        containerFilePlain().delete()
    }

    suspend fun removePassphrase() {
        noPassphraseMode = true
        passphrase.fill('\u0000')
        passphrase = charArrayOf()
        commit()  // writes plain .tar
        // Clean up the old .enc file if it exists
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
            val alarmEntries = loadAlarmsDirect()

            val sections = map.sections.map {
                Section(
                    id = it.id,
                    name = it.name,
                    createdAt = it.createdAt,
                    modifiedAt = it.modifiedAt,
                    sortOrder = it.sortOrder,
                    sortAsc = it.sortAsc,
                    pinned = it.pinned
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

            AppData(sections = sections, notes = notes, alarms = alarms, sectionSortOrder = map.sectionSortOrder, sectionSortAsc = map.sectionSortAsc).also {
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
        saveAlarmsDirect(loadAlarmsDirect().filter { it.noteId !in deletedNoteIds })
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
        val fileName = "${note.id}-${sanitizeName(note.title)}.txt"
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

    suspend fun updateNote(context: Context, note: Note): AppData {
        val map = loadMap()
        val noteEntry = map.notes.find { it.id == note.id } ?: return load(context)
        val sectionEntry = map.sections.find { it.id == note.sectionId } ?: return load(context)
        val newFileName = if (sanitizeName(note.title) != sanitizeName(noteEntry.title)) {
            val newName = "${note.id}-${sanitizeName(note.title)}.txt"
            val oldPath = "userData/sections/${sectionEntry.folderName}/${noteEntry.fileName}"
            val newPath = "userData/sections/${sectionEntry.folderName}/$newName"
            files[newPath] = files.remove(oldPath) ?: byteArrayOf()
            newName
        } else noteEntry.fileName
        val updatedEntry = noteEntry.copy(
            title = note.title,
            fileName = newFileName,
            modifiedAt = currentTimestamp(),
            notifyEnabled = note.notifyEnabled,
            pinned = note.pinned
        )
        writeText(
            "userData/sections/${sectionEntry.folderName}/${updatedEntry.fileName}",
            note.content
        )
        saveMap(map.copy(
            notes = map.notes.map {
                if (it.id == note.id) updatedEntry else it
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
        saveAlarmsDirect(loadAlarmsDirect().filter { it.noteId != noteId })
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
        saveAlarmsDirect(loadAlarmsDirect() + entry)
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
        saveAlarmsDirect(loadAlarmsDirect().map {
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
        saveAlarmsDirect(loadAlarmsDirect().filter { it.id != alarmId })
        val map = loadMap()
        saveMap(map.copy(
            notes = map.notes.map {
                it.copy(alarmIds = it.alarmIds.filter { aid -> aid != alarmId })
            }
        ))
        commit()
        return load(context)
    }

    private fun alarmsJsonFile() = File(
        Environment.getExternalStorageDirectory(),
        "IainNotes/userData/alarms.json"
    ).also { it.parentFile?.mkdirs() }

    private fun loadAlarmsDirect(): List<AlarmEntry> {
        val f = alarmsJsonFile()
        if (!f.exists()) return emptyList()
        return try {
            val text = f.readText()
            // Handle legacy format — plain array from before versioning
            if (text.trimStart().startsWith("[")) {
                val alarms = json.decodeFromString<List<AlarmEntry>>(text)
                // Migrate to new format immediately
                saveAlarmsDirect(alarms)
                alarms
            } else {
                json.decodeFromString<AlarmsFile>(text).alarms
            }
        } catch (e: Exception) {
            throw DataStoreException("alarms.json is corrupt or unreadable: ${e.message}", e)
        }
    }

    private fun saveAlarmsDirect(alarms: List<AlarmEntry>) {
        alarmsJsonFile().writeText(json.encodeToString(AlarmsFile(alarms = alarms)))
    }

    suspend fun export(encrypted: Boolean): File = withContext(Dispatchers.IO) {
        val fileName = if (encrypted) "IainNotes-export.tar.enc" else "IainNotes-export.tar"
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
}