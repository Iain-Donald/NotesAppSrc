package com.example.iainnotes

object SortHelper {

	fun sortedSections(
		sections: List<Section>,
		sortOrder: String,
		sortAsc: Boolean
	): List<Section> {
		val pinned = sections.filter { it.pinned }
			.let { applySectionSort(it, sortOrder, sortAsc) }
		val unpinned = sections.filter { !it.pinned }
			.let { applySectionSort(it, sortOrder, sortAsc) }
		return pinned + unpinned
	}

	private fun applySectionSort(
		sections: List<Section>,
		sortOrder: String,
		sortAsc: Boolean
	): List<Section> {
		val sorted = when (sortOrder) {
			"alpha" -> sections.sortedBy { it.name.lowercase() }
			else -> sections.sortedBy { it.createdAt }
		}
		return if (sortAsc) sorted else sorted.reversed()
	}

	fun sortedNotes(
		notes: List<Note>,
		sortOrder: String,
		sortAsc: Boolean
	): List<Note> {
		val pinned = notes.filter { it.pinned }
			.let { applyNoteSort(it, sortOrder, sortAsc) }
		val unpinned = notes.filter { !it.pinned }
			.let { applyNoteSort(it, sortOrder, sortAsc) }
		return pinned + unpinned
	}

	private fun applyNoteSort(
		notes: List<Note>,
		sortOrder: String,
		sortAsc: Boolean
	): List<Note> {
		val sorted = when (sortOrder) {
			"alpha" -> notes.sortedBy { it.title.lowercase() }
			else -> notes.sortedBy { it.createdAt }
		}
		return if (sortAsc) sorted else sorted.reversed()
	}
}