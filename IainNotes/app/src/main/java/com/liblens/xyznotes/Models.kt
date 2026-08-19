package com.liblens.xyznotes

import kotlinx.serialization.Serializable
import java.time.LocalDateTime

interface Sortable {
    val sortName: String
    val sortCreatedAt: String
    val pinned: Boolean
}

fun currentTimestamp(): String {
    val now = LocalDateTime.now()
    return "%04d%02d%02d-%02d%02d%02d".format(
        now.year, now.monthValue, now.dayOfMonth,
        now.hour, now.minute, now.second
    )
}

//json
@Serializable
data class Section(
    val id: String = generateId("s"),
    val name: String,
    val createdAt: String = IdGenerator.decodeId(id.substringAfter("s")) ?: currentTimestamp(),
    val modifiedAt: String = createdAt,
    val sortOrder: String = "date_created",
    val sortAsc: Boolean = true,
    override val pinned: Boolean = false,          // ← unchanged, no `override` needed
    val categoryId: String = ""
) : Sortable {
    override val sortName get() = name
    override val sortCreatedAt get() = createdAt
}

@Serializable
data class Note(
    val id: String = generateId("n"),
    val sectionId: String,
    val title: String,
    val content: String = "",
    val createdAt: String = IdGenerator.decodeId(
        id.substringAfter("n")
    ) ?: currentTimestamp(),
    val modifiedAt: String = createdAt,
    val notifyEnabled: Boolean = false,
    override val pinned: Boolean = false
) : Sortable {
    override val sortName get() = title
    override val sortCreatedAt get() = createdAt
}

@Serializable
data class Alarm(
    val vaultId: String = VaultStore.DEFAULT_VAULT_ID,
    val id: String = generateId("t"),
    val noteId: String,
    val sectionId: String,
    val name: String,
    val timeHour: Int,
    val timeMinute: Int,
    val displayText: String,
    val isActive: Boolean,
    val repeatDays: List<String>,
    val createdAt: String = IdGenerator.decodeId(id.substringAfter("t").substringBefore("-"))
        ?: currentTimestamp(),
    val modifiedAt: String = createdAt
)

data class AppData(
    val sections: List<Section> = emptyList(),
    val notes: List<Note> = emptyList(),
    val alarms: List<Alarm> = emptyList(),
    val categories: List<Category> = emptyList(),
    val sectionSortOrder: String = "date_created",
    val sectionSortAsc: Boolean = true,
    val sectionCustomOrder: List<String> = emptyList()
)

// Map stored in map.json
@Serializable
data class SectionEntry(
    val id: String,
    val name: String,
    val folderName: String,
    val noteIds: List<String> = emptyList(),
    val createdAt: String = "",
    val modifiedAt: String = "",
    val sortOrder: String = "date_created",
    val sortAsc: Boolean = true,
    val pinned: Boolean = false,
    val categoryId: String = ""
) {
    fun toSection() = Section(
        id = id, name = name,
        createdAt = createdAt.ifEmpty { IdGenerator.decodeId(id.substringAfter("s")) ?: "" },
        modifiedAt = modifiedAt, sortOrder = sortOrder, sortAsc = sortAsc,
        pinned = pinned, categoryId = categoryId
    )
}

@Serializable
data class NoteEntry(
    val id: String,
    val sectionId: String,
    val title: String,
    val fileName: String,
    val alarmIds: List<String> = emptyList(),
    val createdAt: String = "",
    val modifiedAt: String = "",
    val notifyEnabled: Boolean = false,
    val pinned: Boolean = false
){
    fun toNote(content: String) = Note(
        id = id, sectionId = sectionId, title = title, content = content,
        createdAt = createdAt.ifEmpty { IdGenerator.decodeId(id.substringAfter("n")) ?: "" },
        modifiedAt = modifiedAt, notifyEnabled = notifyEnabled, pinned = pinned
    )
}

@Serializable
data class Category(
    val id: String,
    val name: String,
    val colorId: Int = 0
)
@Serializable
data class MapFile(
    val version: Int = 1,
    val sections: List<SectionEntry> = emptyList(),
    val notes: List<NoteEntry> = emptyList(),
    val sectionSortOrder: String = "date_created",
    val sectionSortAsc: Boolean = true,
    val categories: List<Category> = emptyList()
)

@Serializable
data class AlarmsFile(
    val version: Int = 1,
    val alarms: List<Alarm> = emptyList()
)


fun generateId(prefix: String) = "$prefix${IdGenerator.makeId()}"

fun sanitizeName(name: String): String =
    name.trim()
        .replace(Regex("[^a-zA-Z0-9 _-]"), "")
        .replace(" ", "_")
        .take(64)

class DataStoreException(message: String, cause: Throwable? = null) : Exception(message, cause)