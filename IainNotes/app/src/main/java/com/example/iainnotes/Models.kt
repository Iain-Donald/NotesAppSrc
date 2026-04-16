package com.example.iainnotes

import kotlinx.serialization.Serializable
import java.time.LocalDateTime

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
    val createdAt: String = IdGenerator.decodeId(
        id.substringAfter("s").substringBefore("-")
    ) ?: currentTimestamp(),
    val modifiedAt: String = createdAt
)

@Serializable
data class Note(
    val id: String = generateId("n"),
    val sectionId: String,
    val title: String,
    val content: String = "",
    val createdAt: String = IdGenerator.decodeId(
        id.substringAfter("n").substringBefore("-")
    ) ?: currentTimestamp(),
    val modifiedAt: String = createdAt
)

@Serializable
data class Alarm(
    val id: String = generateId("t"),
    val noteId: String,
    val sectionId: String,
    val name: String,
    val timeHour: Int,
    val timeMinute: Int,
    val displayText: String,
    val isActive: Boolean,
    val repeatDays: List<String>,
    val createdAt: String = IdGenerator.decodeId(
        id.substringAfter("t").substringBefore("-")
    ) ?: currentTimestamp(),
    val modifiedAt: String = createdAt
)

@Serializable
data class AppData(
    val sections: List<Section> = emptyList(),
    val notes: List<Note> = emptyList(),
    val alarms: List<Alarm> = emptyList()
)

// Map stored in map.json
@Serializable
data class SectionEntry(
    val id: String,
    val name: String,
    val folderName: String,
    val noteIds: List<String> = emptyList(),
    val createdAt: String = "",
    val modifiedAt: String = ""
)

@Serializable
data class NoteEntry(
    val id: String,
    val sectionId: String,
    val title: String,
    val fileName: String,
    val alarmIds: List<String> = emptyList(),
    val createdAt: String = "",
    val modifiedAt: String = ""
)

@Serializable
data class AlarmEntry(
    val id: String,
    val noteId: String,
    val sectionId: String,
    val name: String,
    val timeHour: Int,
    val timeMinute: Int,
    val displayText: String,
    val isActive: Boolean,
    val repeatDays: List<String>,
    val createdAt: String = "",
    val modifiedAt: String = ""
)

@Serializable
data class MapFile(
    val version: Int = 1,
    val sections: List<SectionEntry> = emptyList(),
    val notes: List<NoteEntry> = emptyList()
)

@Serializable
data class AlarmsFile(
    val version: Int = 1,
    val alarms: List<AlarmEntry> = emptyList()
)
fun generateId(prefix: String) = "$prefix${IdGenerator.makeId()}"

fun sanitizeName(name: String): String =
    name.trim()
        .replace(Regex("[^a-zA-Z0-9 _-]"), "")
        .replace(" ", "_")
        .take(64)